package quest.devinthebm.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import quest.devinthebm.launcher.data.NoticeItem
import quest.devinthebm.launcher.data.NotificationCounts
import quest.devinthebm.launcher.data.NotificationStore

/**
 * Tracks active notifications for two things Home/Notices need: per-category
 * counts for the Home summary badges, and the full list backing the custom
 * Notices screen (we skip the system shade entirely - see NoticesActivity -
 * since it isn't designed for a feature-phone-sized display).
 *
 * Requires the user to grant Notification Access in system settings; until
 * they do, both stay empty.
 */
class NotificationCountService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        recompute()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = recompute()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = recompute()

    private fun recompute() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            return
        }

        var calls = 0
        var messages = 0
        var other = 0
        val packages = HashSet<String>()
        val notices = ArrayList<NoticeItem>()
        for (sbn in active) {
            // Group summaries and our own posted-by-system rows aren't real, user-facing
            // notifications, so they're skipped to avoid inflating the "other" count.
            if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            when (sbn.notification.category) {
                Notification.CATEGORY_CALL -> calls++
                Notification.CATEGORY_MESSAGE -> messages++
                else -> other++
            }
            packages.add(sbn.packageName)
            notices.add(toNoticeItem(sbn))
        }
        NotificationCounts.update(calls, messages, other, packages)
        NotificationStore.update(notices.sortedByDescending { it.postTime })
    }

    private fun toNoticeItem(sbn: StatusBarNotification): NoticeItem {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        return NoticeItem(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title.ifEmpty { appLabel(sbn.packageName) },
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postTime = sbn.postTime,
            icon = loadSmallIcon(sbn),
        )
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun loadSmallIcon(sbn: StatusBarNotification): Drawable? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sbn.notification.smallIcon?.loadDrawable(this)
        } else {
            packageManager.getApplicationIcon(sbn.packageName)
        }
    } catch (e: Exception) {
        null
    }

    /** Opens [key]'s tap action, mirroring what tapping it in the system shade would do. */
    fun openNotice(key: String): Boolean {
        val sbn = try {
            activeNotifications.firstOrNull { it.key == key }
        } catch (e: SecurityException) {
            null
        } ?: return false
        return try {
            sbn.notification.contentIntent?.send()
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    fun dismiss(key: String) = cancelNotification(key)

    fun dismissAll() = cancelAllNotifications()

    companion object {
        /**
         * The system only ever runs one instance of a given listener service, so
         * a static reference is a simple way for [quest.devinthebm.launcher.NoticesActivity]
         * to reach it for dismiss/open actions without a bound-service round trip.
         */
        var instance: NotificationCountService? = null
            private set
    }
}
