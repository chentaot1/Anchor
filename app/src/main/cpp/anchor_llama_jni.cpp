#include <android/log.h>
#include <jni.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <functional>
#include <mutex>
#include <memory>
#include <algorithm>
#include <stdexcept>
#include <string>
#include <vector>

#include "llama.h"
#include "utf8_jni.h"

#define LOG_TAG "anchor_llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mutex;
std::atomic<bool> g_abort{false};
llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;
bool g_backend_init = false;
int g_loaded_gpu_layers = 0;

void ensure_backend() {
    if (!g_backend_init) {
        llama_backend_init();
        g_backend_init = true;
    }
}

void free_all() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_loaded_gpu_layers = 0;
}

bool load_model(const char * path, int n_ctx, int n_gpu_layers) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_abort.store(false);
    free_all();
    ensure_backend();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;
    mparams.use_mmap = true;
    mparams.use_mlock = false;

    g_model = llama_model_load_from_file(path, mparams);
    if (!g_model) {
        LOGE("Failed to load model: %s", path);
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.type_k = GGML_TYPE_Q8_0;
    cparams.type_v = GGML_TYPE_Q8_0;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    cparams.n_batch = 2048;
    cparams.n_ubatch = 512;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create llama context (ctx=%d)", n_ctx);
        llama_model_free(g_model);
        g_model = nullptr;
        return false;
    }

    g_loaded_gpu_layers = n_gpu_layers;
    LOGI("Model loaded (ctx=%d, gpu_layers=%d)", n_ctx, n_gpu_layers);
    return true;
}

llama_sampler * build_sampler(const llama_vocab * vocab, const std::string & grammar, float temperature, float repeat_penalty,
                             const std::vector<llama_token> & prompt_tokens) {
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sparams);
    if (!grammar.empty()) {
        llama_sampler * grammar_sampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (grammar_sampler) {
            llama_sampler_chain_add(sampler, grammar_sampler);
        } else {
            llama_sampler_free(sampler);
            throw std::runtime_error("Invalid grammar; refusing unconstrained JSON generation");
        }
    }
    auto penalties = llama_sampler_init_penalties(64, repeat_penalty, 0.0f, 0.0f);
    // Desktop includes the prompt in penalty history, but not in grammar state.
    for (size_t i = prompt_tokens.size() > 64 ? prompt_tokens.size() - 64 : 0; i < prompt_tokens.size(); ++i) {
        llama_sampler_accept(penalties, prompt_tokens[i]);
    }
    llama_sampler_chain_add(sampler, penalties);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return sampler;
}

/** Longest prefix of [s] that is complete, valid UTF-8 with no NULs. */
size_t utf8_complete_prefix(const std::string & s) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        if (c == 0) {
            break;
        }
        size_t need = 0;
        if ((c & 0x80) == 0) {
            need = 1;
        } else if ((c & 0xE0) == 0xC0) {
            need = 2;
        } else if ((c & 0xF0) == 0xE0) {
            need = 3;
        } else if ((c & 0xF8) == 0xF0) {
            need = 4;
        } else {
            break;
        }
        if (i + need > n) {
            break;
        }
        bool ok = true;
        for (size_t k = 1; k < need; ++k) {
            if ((static_cast<unsigned char>(s[i + k]) & 0xC0) != 0x80) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            break;
        }
        i += need;
    }
    return i;
}

int generate_tokens(
    const std::string & prompt,
    int max_tokens,
    const std::string & grammar,
    float temperature,
    float repeat_penalty,
    const std::function<bool(const std::string &)> & on_token
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx) {
        throw std::runtime_error("Model not loaded");
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    // Each request is a complete prompt, matching desktop cache_prompt=false.
    // Reusing a resident model must not append the previous request's KV cache.
    llama_memory_clear(llama_get_memory(g_ctx), true);

    const int n_prompt = -llama_tokenize(vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        throw std::runtime_error("Tokenization failed");
    }
    if (n_prompt >= static_cast<int>(llama_n_ctx(g_ctx))) {
        throw std::runtime_error("Prompt exceeds the desktop profile's 2048-token context");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(vocab, prompt.c_str(), prompt.size(), prompt_tokens.data(), n_prompt, true, true) < 0) {
        throw std::runtime_error("Tokenization failed");
    }

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
    if (llama_decode(g_ctx, batch) != 0) {
        throw std::runtime_error("Prompt decode failed");
    }

    auto sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>(
        build_sampler(vocab, grammar, temperature, repeat_penalty, prompt_tokens), llama_sampler_free);

    std::string piece;
    piece.resize(256);
    std::string utf8_buf;
    int generated = 0;
    const std::vector<std::string> stops = {"<|role_end|>", "<role>", "<|im_end|>", "<|endoftext|>", "</s>"};
    bool output_stopped = false;
    auto emit_pending = [&](bool final) {
        size_t safe = utf8_buf.size();
        bool stop_found = false;
        for (const auto & stop : stops) {
            const auto position = utf8_buf.find(stop);
            if (position != std::string::npos) {
                safe = std::min(safe, position);
                stop_found = true;
            }
        }
        if (!final && !stop_found) {
            // Hold partial stop strings even when their tokens arrive separately.
            for (const auto & stop : stops) {
                for (size_t count = 1; count < stop.size() && count <= utf8_buf.size(); ++count) {
                    if (utf8_buf.compare(utf8_buf.size() - count, count, stop, 0, count) == 0) {
                        safe = std::min(safe, utf8_buf.size() - count);
                    }
                }
            }
        }
        safe = utf8_complete_prefix(utf8_buf.substr(0, safe));
        if (safe > 0 && !on_token(utf8_buf.substr(0, safe))) output_stopped = true;
        utf8_buf.erase(0, safe);
        if (stop_found) output_stopped = true;
    };

    const int budget = std::min(max_tokens, static_cast<int>(llama_n_ctx(g_ctx)) - n_prompt);
    for (int i = 0; i < budget; ++i) {
        if (g_abort.load()) {
            break;
        }
        llama_token token = llama_sampler_sample(sampler.get(), g_ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        int32_t n = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        if (n < 0) {
            piece.resize(static_cast<size_t>(-n));
            llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        } else {
            piece.resize(static_cast<size_t>(n));
        }

        utf8_buf.append(piece);
        emit_pending(false);
        if (output_stopped) break;

        generated++;

        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
    }
    if (!output_stopped && !g_abort.load()) emit_pending(true);

    return generated;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_anchor_adhd_ai_NativeLlamaBridge_nativeLoad(
    JNIEnv * env,
    jobject /*thiz*/,
    jstring path,
    jint contextSize,
    jint gpuLayers
) {
    const char * path_chars = env->GetStringUTFChars(path, nullptr);
    const bool ok = load_model(path_chars, contextSize, gpuLayers);
    env->ReleaseStringUTFChars(path, path_chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_anchor_adhd_ai_NativeLlamaBridge_nativeUnload(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_abort.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    free_all();
    g_abort.store(false);
}

JNIEXPORT void JNICALL
Java_com_anchor_adhd_ai_NativeLlamaBridge_nativeAbort(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_abort.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_anchor_adhd_ai_NativeLlamaBridge_nativeIsLoaded(JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_anchor_adhd_ai_NativeLlamaBridge_nativeLoadedGpuLayers(JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_loaded_gpu_layers;
}

JNIEXPORT void JNICALL
Java_com_anchor_adhd_ai_NativeLlamaBridge_nativeGenerate(
    JNIEnv * env,
    jobject /*thiz*/,
    jstring prompt,
    jint maxTokens,
    jstring grammar,
    jfloat temperature,
    jfloat repeatPenalty,
    jobject callback
) {
    g_abort.store(false);
    const std::string prompt_str = java_utf8(env, prompt);

    std::string grammar_str;
    if (grammar != nullptr) {
        grammar_str = java_utf8(env, grammar);
    }

    jclass callback_class = env->GetObjectClass(callback);
    const jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");

    try {
        generate_tokens(prompt_str, maxTokens, grammar_str, temperature, repeatPenalty, [&](const std::string & token) {
            jstring jtoken = utf8_java(env, token);
            if (jtoken == nullptr) {
                env->ExceptionClear();
                return false;
            }
            env->CallVoidMethod(callback, on_token, jtoken);
            env->DeleteLocalRef(jtoken);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return false;
            }
            return !g_abort.load();
        });
    } catch (const std::exception & e) {
        LOGE("generate failed: %s", e.what());
        jclass ex_class = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(ex_class, e.what());
    }
}

JNIEXPORT jlongArray JNICALL
Java_com_anchor_adhd_ai_NativeLlamaBridge_nativeBenchmark(
    JNIEnv * env,
    jobject /*thiz*/,
    jstring prompt,
    jint maxTokens
) {
    g_abort.store(false);
    const std::string prompt_str = java_utf8(env, prompt);

    jlong results[3] = {0, 0, 0};
    try {
        const auto start = std::chrono::steady_clock::now();
        const int tokens = generate_tokens(prompt_str, maxTokens, "", 0.2f, 1.15f, [](const std::string &) { return true; });
        const auto end = std::chrono::steady_clock::now();
        const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
        results[0] = tokens;
        results[1] = ms;
        results[2] = g_loaded_gpu_layers;
    } catch (const std::exception & e) {
        LOGE("benchmark failed: %s", e.what());
        results[0] = -1;
    }

    jlongArray arr = env->NewLongArray(3);
    env->SetLongArrayRegion(arr, 0, 3, results);
    return arr;
}

} // extern "C"
