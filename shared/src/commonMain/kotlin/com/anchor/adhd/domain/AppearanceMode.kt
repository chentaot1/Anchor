package com.anchor.adhd.domain

enum class AppearanceMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Morning Harbor"),
    DARK("Night Harbor"),
    ;

    fun resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(raw: String?): AppearanceMode {
            val normalized = raw?.trim() ?: return SYSTEM
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: SYSTEM
        }
    }
}
