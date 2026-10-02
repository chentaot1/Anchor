package com.anchor.adhd.domain

/** Packages always allowed during whitelist shielding (launcher, dialer, settings). */
object WhitelistBasics {
    val essentialPackages: Set<String> = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.sec.android.app.launcher"
    )
}
