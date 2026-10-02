# Android app protection

Open **More → App Blocker** in the updated Android app. Enable Anchor in Android Accessibility settings to enforce the rules, and set Anchor's battery setting to **Unrestricted** on Samsung.

Select apps independently of the older focus rules in Settings. Existing focus, whitelist and scheduled rules still apply; earned leisure does not override those older rules.

## App rules

- **During focus:** blocks the selected app during a locked focus session.
- **Always protected:** blocks outside focus too; earned leisure or the held emergency pass can temporarily open it.
- **Daily allowance:** tracks foreground time and blocks once the allowance is exhausted. A zero-minute allowance blocks immediately. Apps with the same group name share a usage ledger; use the same allowance for each group member.
- **Track only:** records foreground time without adding a blocking rule.
- **Freeze rule for 24 hours:** prevents changing or removing that rule inside the new blocker screen until its lock expires.

Foreground usage is sampled every second while the display is on and unlocked. Time spent behind the rescue overlay is not counted. Totals persist across service/app restarts. Android settings, launchers and dialers in the essential-package list remain available.

## Global protection

- **Standing shield:** protects selected apps outside focus.
- **Night curfew:** protects selected apps in a configurable time window, including overnight windows.
- **Weekday study window:** protects selected apps Monday through Friday during a configurable time window.
- **Lock until 4 AM:** blocks protected apps and freezes configuration until the next local 4 AM. Activation requires confirmation.

Track-only rules are excluded from these global shields. Equal start/end times mean a full-day window. Rule changes in the new blocker screen are frozen during work sessions.

## Earned leisure

Completed focus sessions earn rewards using actual minutes capped at the planned work duration. The first reward requires 60 minutes; the next rewards require 75, 90 and then 120 additional minutes each. Each reward earns 30 leisure minutes. The reservoir caps at 60 minutes and discards excess rewards rather than restoring them after spending.

Spend 15 or 30 minutes from the blocker screen, or 15 minutes from an eligible rescue overlay. One leisure window can run at a time. Leisure temporarily opens standing/always protection. It cannot bypass focus, curfew, study windows, lockdown or exhausted daily allowances. Those protections also disable emergency passes.

Usage totals, focus progress and the bank reset at **4 AM in the phone's timezone**, including daylight-saving changes. Leisure expires no later than that reset. Settings and 24-hour rule locks survive the daily reset. An explicit factory reset clears this protection state along with other app data.

## Desktop features still separate

This implementation protects Android **apps**. Desktop website/title matching, AI-assisted Scope Sentinel classification, distraction taxes and per-class schedule management are not ported. Blocking a browser here protects the entire browser. Desktop and Android balances/settings are separate; there is no cross-device sync.

Validation uses the Android unit suite and debug APK build. Service regression coverage checks advanced/legacy rules, essential apps and cancellation of stale app-switch checks when returning to Anchor. Accessibility overlay behavior still needs verification on an S24 Ultra; a compiled APK alone does not verify device enforcement or Samsung background-service behavior.
