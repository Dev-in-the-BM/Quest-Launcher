package quest.devinthebm.launcher

import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import quest.devinthebm.launcher.data.NoticeItem
import quest.devinthebm.launcher.data.NotificationStore
import quest.devinthebm.launcher.service.NotificationCountService
import quest.devinthebm.launcher.ui.NoticeRowAdapter

/**
 * A KaiOS-style "Notices" list standing in for the system notification shade,
 * which isn't laid out for a screen this small. Backed by
 * [NotificationCountService] via [NotificationStore].
 */
class NoticesActivity : BaseListActivity() {

    private lateinit var adapter: NoticeRowAdapter

    private val storeListener: () -> Unit = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = NoticeRowAdapter(onClick = { openNotice(it) })
        listView.adapter = adapter

        softKeys.set {
            left(getString(R.string.softkey_dismiss)) { dismissFocused() }
            right(getString(R.string.softkey_dismiss_all)) { dismissAll() }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationStore.addListener(storeListener)
        if (!isNotificationAccessGranted()) {
            Toast.makeText(this, R.string.notices_access_required, Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        NotificationStore.removeListener(storeListener)
    }

    private fun refresh() {
        val items = NotificationStore.items
        titleView.text = getString(R.string.title_notices, items.size)
        adapter.submit(items)
        focusFirst()
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun openFocused() {
        adapter.itemAt(focusedPosition())?.let { openNotice(it) }
    }

    private fun openNotice(item: NoticeItem) {
        val opened = NotificationCountService.instance?.openNotice(item.key) == true
        if (!opened) {
            Toast.makeText(this, R.string.notices_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        NotificationCountService.instance?.dismiss(item.key)
        finish()
    }

    private fun dismissFocused() {
        val item = adapter.itemAt(focusedPosition()) ?: return
        NotificationCountService.instance?.dismiss(item.key)
    }

    private fun dismissAll() {
        if (NotificationStore.items.isEmpty()) return
        NotificationCountService.instance?.dismissAll()
    }

}
