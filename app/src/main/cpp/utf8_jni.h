#pragma once
#include <jni.h>
#include <cstdint>
#include <string>
#include <vector>

// JNI's Modified UTF-8 differs from GGUF/llama UTF-8 for emoji and embedded NUL.
inline std::string java_utf8(JNIEnv * env, jstring value) {
    const jchar * chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    const jsize length = env->GetStringLength(value);
    std::string result;
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + chars[++i] - 0xDC00;
        } else if (cp >= 0xD800 && cp <= 0xDFFF) cp = 0xFFFD;
        if (cp < 0x80) result += static_cast<char>(cp);
        else if (cp < 0x800) {
            result += static_cast<char>(0xC0 | (cp >> 6)); result += static_cast<char>(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            result += static_cast<char>(0xE0 | (cp >> 12)); result += static_cast<char>(0x80 | ((cp >> 6) & 0x3F)); result += static_cast<char>(0x80 | (cp & 0x3F));
        } else {
            result += static_cast<char>(0xF0 | (cp >> 18)); result += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
            result += static_cast<char>(0x80 | ((cp >> 6) & 0x3F)); result += static_cast<char>(0x80 | (cp & 0x3F));
        }
    }
    env->ReleaseStringChars(value, chars);
    return result;
}

inline jstring utf8_java(JNIEnv * env, const std::string & value) {
    std::vector<jchar> result;
    for (size_t i = 0; i < value.size();) {
        const auto first = static_cast<unsigned char>(value[i++]);
        uint32_t cp = first;
        int extra = 0;
        if (first >= 0xF0) { cp = first & 0x07; extra = 3; }
        else if (first >= 0xE0) { cp = first & 0x0F; extra = 2; }
        else if (first >= 0xC0) { cp = first & 0x1F; extra = 1; }
        for (int j = 0; j < extra && i < value.size(); ++j) cp = (cp << 6) | (static_cast<unsigned char>(value[i++]) & 0x3F);
        if (cp > 0xFFFF) {
            cp -= 0x10000;
            result.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            result.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        } else result.push_back(static_cast<jchar>(cp));
    }
    return env->NewString(result.data(), static_cast<jsize>(result.size()));
}
