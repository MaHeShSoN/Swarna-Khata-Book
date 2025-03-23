package com.jewelrypos.swarnakhatabook

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationScheduler

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeNotificationSystem()
    }

    private fun initializeNotificationSystem() {
        // Schedule periodic notification checks - this ensures they run
        // even if the user doesn't visit the Dashboard fragment
        NotificationScheduler.scheduleNotificationCheck(applicationContext)
    }
}