package quest.devinthebm.launcher

import android.os.Bundle
import android.view.KeyEvent
import quest.devinthebm.launcher.data.AppInfo
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

/**
 * Lists every installed app with a "Hidden" badge. Center / OK toggles whether
 * the focused app is hidden from the Home grid and the app drawer.
 */
class HideAppsActivity : BaseListActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var adapter: ListRowAdapter
    private var apps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        titleView.text = getString(R.string.title_hide_apps)

        adapter = ListRowAdapter(onClick = { toggle(it) })
        listView.adapter = adapter

        softKeys.setLabels(
            getString(R.string.softkey_back),
            getString(R.string.softkey_toggle),
            null,
        )
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnCenterClick { toggle(focusedPosition()) }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    private fun loadApps() {
        Thread {
            val loaded = AppRepository.getAllApps(this)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                apps = loaded
                adapter.submit(loaded.map { rowFor(it) })
                focusFirst()
            }
        }.start()
    }

    private fun rowFor(app: AppInfo) = Row(
        title = app.label,
        trailing = if (prefs.isHidden(app.key)) getString(R.string.hidden_badge) else null,
        icon = app.icon,
    )

    private fun toggle(position: Int) {
        val app = apps.getOrNull(position) ?: return
        prefs.setHidden(app.key, !prefs.isHidden(app.key))
        adapter.updateRow(position, rowFor(app))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
