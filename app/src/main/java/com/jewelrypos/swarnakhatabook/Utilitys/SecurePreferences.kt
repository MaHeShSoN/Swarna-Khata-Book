package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferences {
    private var instance: SharedPreferences? = null

    fun getInstance(context: Context): SharedPreferences {
        if (instance == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            instance = EncryptedSharedPreferences.create(
                context,
                "secure_jewelry_pos_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return instance!!
    }
}