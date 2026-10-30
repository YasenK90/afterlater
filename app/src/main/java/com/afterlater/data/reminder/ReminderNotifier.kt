package com.afterlater.data.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.afterlater.MainActivity
import com.afterlater.R

class ReminderNotifier(private val context: Context) {

    init {
        ensureChannel()
    }

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = CHANNEL_DESCRIPTION
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun notificationsAllowed(): Boolean = areReminderNotificationsAllowed(context)

    @SuppressLint("MissingPermission")
    fun notify(eligibleCount: Int): Boolean {
        ensureChannel()
        if (!notificationsAllowed()) return false

        val openToday = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            MainActivity.intentForToday(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = if (eligibleCount == 1) {
            "1 saved link is ready when you are."
        } else {
            "$eligibleCount saved links are ready when you are."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.acid))
            .setContentTitle("AFTERLATER / TODAY")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setContentIntent(openToday)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "afterlater-reminders"
        const val NOTIFICATION_ID = 4102
        private const val REQUEST_CODE = 4102
        private const val CHANNEL_NAME = "Daily reminders"
        private const val CHANNEL_DESCRIPTION = "One quiet note a day when saved links are ready."
    }
}

fun areReminderNotificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    val channel = manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}
