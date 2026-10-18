package quest.devinthebm.launcher.data

import android.graphics.drawable.Drawable

/** One active notification, as shown in the custom Notices screen. */
data class NoticeItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val icon: Drawable?,
)

/**
 * Live list of active notifications, fed by
 * [quest.devinthebm.launcher.service.NotificationCountService], backing the custom
 * Notices screen (we don't use the system shade - see NoticesActivity).
 */
object NotificationStore {

    var items: List<NoticeItem> = emptyList()
        private set

    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(items: List<NoticeItem>) {
        this.items = items
        listeners.toList().forEach { it() }
    }
}
