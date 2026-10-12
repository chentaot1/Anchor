# Desktop model on Android

Android uses `MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf`, matching the desktop profile. New APKs exclude its weights. First-run onboarding offers an optional download from Hugging Face and an equally accessible Skip choice; Settings → AI offers the download later. The retained GPL preview 5 release still bundles its model.

- Weight size: 1,153,529,792 bytes.
- SHA-256: `8125095ae223278e728adb4148a8a466cec920929c1de5a5f5ebcb479ec31a2c`.
- Source: [GGUF repository](https://huggingface.co/GnLOLot/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-GGUF). The public artifact's size and LFS hash match the local desktop file. The model notice is packaged with the app.

## Matching inference settings

The verified desktop launch settings are context 2048, one slot, four CPU inference threads and four batch threads, no GPU layers, Q8_0 key and value caches, and flash attention enabled. Android uses those settings with a single serialized JNI context. Batch size is 2048 and micro-batch size 512.

Both use the desktop's MiniCPM chat delimiters and empty `<think>` prefill, disabling reasoning. Shared task and conversational system prompts, for breakdown, brain dump, triage, replan and chat match desktop source. All phone breakdown entry points now use the desktop's default prompt and grammar. Structured tasks use temperature 0.2 and repeat penalty 1.15; conversation uses temperature 0.4 and repeat penalty 1.0. Both use top-k 40, top-p 0.95, min-p 0.05, repetition history 64, zero presence/frequency penalties, and a random seed. Typical probability 1.0 and disabled DRY/Mirostat add no filtering.

Common task/chat responses have a 256-token limit. Natural timer/blocker/reset/overwhelm commands use the desktop deterministic router before mobile planning commands. Normal chat goes directly to conversation inference, without an extra model classifier pass. Recent conversation turns are included with user/assistant roles; older phone history is bounded for its 2048-token context. The phone-specific classifier remains available to other flows. Phone-only weekly and routine tools keep their own system prompts and JSON schemas.

The desktop stop strings are applied in JNI, including stops split across token pieces. JSON generation uses a grammar. JNI converts standard UTF-8 to/from Java UTF-16, preserving emoji and split multibyte output. Token delivery cannot drop pieces under callback backpressure. Prompt tokens seed repetition history without advancing the JSON grammar. Each complete prompt starts with a cleared KV cache, matching desktop's `cache_prompt=false` requests.

Server settings such as HTTP host/port, continuous batching, slot prompt similarity and server log suppression have no direct JNI setting; Android serializes requests instead. Matching model/settings does not promise identical text or performance across Windows and ARM64.

## Runtime and installation

The former Android llama.cpp b5046 runtime cannot recognize this GGUF's `minicpm5` tokenizer. The build is now pinned to official [llama.cpp b9371](https://github.com/ggml-org/llama.cpp/releases/tag/b9371), with a verified source archive hash and MiniCPM5 tokenizer support. OpenCL is disabled to match the active desktop CPU profile.

The single C++ JNI library links the C++ runtime statically and uses 16 KB LOAD alignment and RELRO padding. The APK gate checks all ARM64 libraries for aligned LOAD segments and rejects RELRO page protection overlapping writable data. See [Android page-size guidance](https://developer.android.com/guide/practices/page-sizes).

Startup and skipping onboarding never download weights. An explicit download writes a private `.part` file, verifies GGUF magic, length and SHA-256, then replaces the target file and selects it. Interrupted or corrupt downloads cannot become active. Leaving onboarding does not cancel a download; pausing or process termination preserves partial bytes for an explicit retry. Android may stop the process in the background. Existing installed weights are verified and retained on upgrade. Existing Qwen selection and GPU preferences migrate to this CPU profile; old weight files remain on disk.

Run `python tools/verify_android_apk.py` after building to reject any packaged GGUF weights and verify the ARM64 JNI library. Tagged release CI runs the same verifier without fetching weights.

## Validation on the S24 Ultra

Earlier bundled-model validation included the Android unit suite, native ARM64 compilation, APK packaging and SHA-256 verification of embedded weights. Android x86_64 emulator verification ran the actual model through JNI, checked valid structured task output and forced emoji output. UI checks used 1440 × 3120 at normal and 130% text size, and verified a natural 10-minute command started the correct timer. These historical checks do not verify the new onboarding or ARM64 inference and Android Accessibility on a physical phone.

On the phone, check the release's installation notes first: the former v1.0.3 GitHub APK has a different signing identity and cannot be updated in place with the preview. Preserve needed data before considering an uninstall, which clears app data. The existing JSON backup only includes tasks, assignments and calendar events. For a new build, download the optional model during onboarding or from Settings and run **Benchmark active model**. Confirm the desktop model name and successful CPU inference. Then try task breakdown and two consecutive chat messages, exercising a reused model context.

For protection, enable Accessibility and Samsung's Unrestricted battery setting. Check an always-protected app, an exhausted daily allowance, a curfew boundary while the app stays open, and a rule frozen for 24 hours. Return to Anchor during rapid app switches to ensure no stale overlay follows. Check that earned leisure opens eligible protection and cannot override focus, curfew, study windows, quotas or lockdown. Avoid enabling lockdown until you want it to last until the next 4 AM.

Record device results separately from host checks; they remain pending until a phone is available.
