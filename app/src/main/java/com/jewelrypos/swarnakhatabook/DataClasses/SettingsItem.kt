package com.jewelrypos.swarnakhatabook.DataClasses

data class SettingsItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
    val badgeText: String? = null
)