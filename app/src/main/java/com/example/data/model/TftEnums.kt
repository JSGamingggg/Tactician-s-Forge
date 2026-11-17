package com.example.data.model

enum class TftEnvironment(val label: String, val setName: String) {
    LIVE("LIVE", "Set 18: Enchanted Wilds"),
    PBE("PBE", "Set 19: Prismatic Tour (PBE)");

    companion object {
        fun fromRaw(raw: String?): TftEnvironment =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LIVE
    }
}

enum class ThemeMode(val label: String) {
    DARK("Hextech Dark"),
    LIGHT("Alabaster Light"),
    SYSTEM("System Default");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

enum class TierRank(val label: String, val sortOrder: Int) {
    S("S", 0),
    A("A", 1),
    B("B", 2),
    C("C", 3);

    companion object {
        fun fromRaw(raw: String?): TierRank {
            val cleaned = raw?.trim()?.uppercase().orEmpty()
            return when {
                cleaned.startsWith("S") -> S
                cleaned.startsWith("A") -> A
                cleaned.startsWith("B") -> B
                cleaned.startsWith("C") || cleaned.startsWith("D") -> C
                else -> B
            }
        }
    }
}

enum class DeltaType(val label: String) {
    BUFF("BUFF"),
    NERF("NERF"),
    ADJUST("ADJUST"),
    NEW("NEW");

    companion object {
        fun fromRaw(raw: String?): DeltaType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ADJUST
    }
}

enum class ItemCategory(val label: String) {
    AP("AP & Mana"),
    AD("AD & Crit"),
    TANK("Tank & Defense"),
    ATTACK_SPEED("Attack Speed"),
    FIGHTER("Bruiser & Utility");

    companion object {
        fun fromRaw(raw: String?): ItemCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UTILITY_FALLBACK

        private val UTILITY_FALLBACK = FIGHTER
    }
}

enum class AugmentTier(val label: String) {
    SILVER("Silver"),
    GOLD("Gold"),
    PRISMATIC("Prismatic");

    companion object {
        fun fromRaw(raw: String?): AugmentTier =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GOLD
    }
}

