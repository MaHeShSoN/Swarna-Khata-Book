package com.jewelrypos.swarnakhatabook.DataClasses

/**
 * Enum defining the types of badges that can be displayed on settings items
 */
enum class BadgeType {
    NONE,       // No badge
    PREMIUM,    // Premium feature badge
    TRIAL,      // Trial badge
    EXPIRED,    // Expired trial badge
    DAYS_LEFT,  // X days left badge
    NEW         // New feature badge
}

data class SettingsItem(
    val id: String,
    val titleResId: Int,
    val subtitleResId: Int,
    val iconResId: Int,
    val badgeTextResId: Int? = null,
    val badgeType: BadgeType = BadgeType.NONE,
    val subtitleResIdArgs: List<Any>? = null,
    val badgeTextResIdArgs: List<Any>? = null
)