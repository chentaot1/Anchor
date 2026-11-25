# Android 1.1.0-preview.6

Windows and Android now share one Harbor identity. Morning Harbor is the warm paper light theme and Night Harbor is the lifted slate dark theme. Settings includes Appearance: System, Morning Harbor, or Night Harbor. Status and navigation bars follow the active theme.

- Display type is Newsreader. Body and controls stay Atkinson Hyperlegible. Labels are sentence case.
- The assistant is Captain. The tab, tools, and chat use the captain teal only for AI.
- Home opens on the next action, today's tide, and the plan. The drawn harbor scene is removed. Focus history, weekly milestones, and JSON backups are unchanged.
- Focus keeps the rising-tide timer and freezes that motion when the system animator scale is off.
- Plan, syllabus assignments, blocker rules, rescue, Airlock, and Settings use the same paper, hairline, and well surfaces.

This debug-signed APK does not bundle model weights. An installed model is kept. Onboarding and Settings can download one later. versionCode 7 updates a preview 5 install that uses the same persistent signing key.

241 Android unit tests, 345 desktop tests (9 Windows-only checks skipped on Linux), and 24 shared tests passed. Android lint is clean. Physical-device Accessibility blocking, Health Connect, inference, and screen-reader passes were not repeated for this build.
