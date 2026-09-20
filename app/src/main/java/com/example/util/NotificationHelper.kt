package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    private const val CHANNEL_ID = "expense_notifications_channel"
    private const val CHANNEL_NAME = "Expense & Split Alerts"
    private const val CHANNEL_DESC = "Notifications for added expenses and deletion warnings"
    private const val PREFS_NAME = "shown_notifications_prefs"
    private const val KEY_SHOWN_IDS = "shown_notif_ids"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun isNotificationAlreadyShown(context: Context, notifId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SHOWN_IDS, emptySet()) ?: emptySet()
        return set.contains(notifId)
    }

    fun markNotificationShown(context: Context, notifId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SHOWN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(notifId)
        // Keep set size reasonable (last 100 entries)
        if (set.size > 150) {
            val trimmed = set.toList().takeLast(100).toSet()
            prefs.edit().putStringSet(KEY_SHOWN_IDS, trimmed).apply()
        } else {
            prefs.edit().putStringSet(KEY_SHOWN_IDS, set).apply()
        }
    }

    fun showDeviceNotification(
        context: Context,
        notificationUniqueId: String = "",
        id: Int = (System.currentTimeMillis() % 100000).toInt(),
        title: String,
        message: String,
        isWarning: Boolean = false
    ) {
        // Prevent repeated/spam notifications
        if (notificationUniqueId.isNotBlank()) {
            if (isNotificationAlreadyShown(context, notificationUniqueId)) {
                return
            }
            markNotificationShown(context, notificationUniqueId)
        }

        initNotificationChannel(context)

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "notifications")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (isWarning) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (isWarning) {
            builder.setColor(Color.RED)
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
        } else {
            builder.setColor(Color.BLUE)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            val notifyId = if (notificationUniqueId.isNotBlank()) notificationUniqueId.hashCode() else id
            notificationManager?.notify(notifyId, builder.build())
        } catch (e: Exception) {
            // Security or other exception
        }
    }
}

