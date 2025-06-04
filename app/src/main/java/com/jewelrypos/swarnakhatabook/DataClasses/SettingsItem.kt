package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

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
    @PropertyName("id")
    val id: String,
    @PropertyName("titleResId")
    val titleResId: Int,
    @PropertyName("subtitleResId")
    val subtitleResId: Int,
    @PropertyName("iconResId")
    val iconResId: Int,
    @PropertyName("badgeTextResId")
    val badgeTextResId: Int? = null,
    @PropertyName("badgeType")
    val badgeType: BadgeType = BadgeType.NONE,
    @PropertyName("subtitleResIdArgs")
    val subtitleResIdArgs: List<Any>? = null,
    @PropertyName("badgeTextResIdArgs")
    val badgeTextResIdArgs: List<Any>? = null
)