# Anchor Android 1.1.0 preview 1

Adds desktop-inspired app protection to the S24 Ultra app and bundles the exact active desktop MiniCPM5 Q8_0 model for offline installation.

## Changes

- More → App Blocker: always protection, standing shield, curfew, weekday study window, individual/shared daily allowances, 24-hour rule freeze and lockdown until 4 AM.
- Earned leisure bank with tiered focus rewards and a 4 AM daily reset. Hard protections cannot be bypassed with leisure or emergency passes.
- Foreground usage tracking and continuous checks of schedules, allowance limits and pass expiry. Fixed stale delayed overlays when returning to Anchor.
- Bundled `MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf`, 1,153,529,792 bytes. SHA-256: `8125095ae223278e728adb4148a8a466cec920929c1de5a5f5ebcb479ec31a2c`.
- Desktop inference profile: 2048-token context, four CPU threads, Q8 key/value cache, flash attention, reasoning disabled, matching shared task/chat prompts and sampling settings.
- Updated ARM64 llama.cpp runtime for MiniCPM5 tokenizer support; atomic, checksum-verified installation from the APK.

## Install and update compatibility

Download the APK asset on the phone (about 1.24 GB). The model prepares automatically after launch without a network download. Allow about 2.4 GB for the APK and extracted model, plus existing files. Enable Anchor Accessibility and set Samsung battery handling to Unrestricted for blocking.

**The previous v1.0.3 GitHub APK was signed with a different key. Android cannot install this preview over that APK. Do not uninstall until you have preserved everything you need: uninstalling clears app data.** Settings → Export backup JSON saves tasks, assignments and calendar events, but does not preserve every setting, history record or protection state. The old APK remains available in its original release.

This preview uses the existing persistent local signing key (certificate SHA-256 `f1ddd4dfb4323ea739c78053e87f309154d9dc1b32ba5e0f8c2300b690a7ac30`), so subsequent updates using that key can install in place. With owner approval, the key has also been stored as an encrypted GitHub Actions secret. Future automated CI publication is prepared to require the same key and remains a separate opt-in setting. Package ID remains `com.anchor.adhd`; Android version code is now 2. This is a debug-signed personal preview build.

## Verification and scope

177 Android unit tests passed, including blocker policy/service regression and model preference migration checks. ARM64 native compilation, APK assembly, model checksum inside the APK and APK signing were verified locally. Physical S24 Ultra inference, Accessibility overlays and Samsung background behavior still need device validation.

Protection applies to whole Android apps. Desktop website/window-title filters, Scope Sentinel classification, distraction taxes, individual class schedule management and cross-device balance/settings sync are not included.
