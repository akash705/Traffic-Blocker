package com.akash.apptrafficblocker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log

class BlockerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "BlockerApp initialized")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Traffic Blocker Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when AppTrafficBlocker is actively blocking traffic"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val TAG = "AppTrafficBlocker"
        const val CHANNEL_ID = "blocker_service_channel"
    }
}
