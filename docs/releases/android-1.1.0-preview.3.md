# Anchor Android 1.1.0 preview 3

The phone's bottom navigation now opens the desktop-style Executive Function Studio in place of Plan.

## Changes

- Bottom tabs are Grove, AI, Focus and More.
- AI includes the desktop's five tools: Break Down, Brain Dump, Triage, Replan and Ask Anchor. All five selectors remain visible and flow onto additional rows at larger text sizes.
- Each tool shows its own input and results. Breakdown highlights the first physical action, with actions to add steps or start focus. Triage uses today's inbox; Replan uses the missed-task queue.
- Ask Anchor includes conversation history, suggestions, action confirmations and Clear Chat. Drafts and tool selection survive switching tools and bottom tabs.
- Inbox, timeline, routines and assignments remain available in More > Plan. Home's inbox shortcut, replan notifications and AI navigation use the new routes.
- The AI page respects the status bar and keyboard insets.

## Model and installation

The original `MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf` remains bundled with the same inference settings. Its SHA-256 is `8125095ae223278e728adb4148a8a466cec920929c1de5a5f5ebcb479ec31a2c`. No V2 IQ4_XS weights are included.

Install this APK over preview 1 or preview 2. It uses the same package and signing identity, with version code 4. The APK is about 1.24 GB and works offline after model preparation.

## Verification

Verification covers 183 passing Android unit tests, ARM64 APK packaging/model checksum and the update-compatible signing identity. Fourteen emulator checks passed for tool navigation, separate drafts, bottom-tab draft preservation, More > Plan, back navigation, Replan notifications, the legacy AI shortcut and 130% text size. Ask Anchor returned a 12-minute timer confirmation, and the final keyboard layout was visually inspected. Physical S24 Ultra checks remain pending.
