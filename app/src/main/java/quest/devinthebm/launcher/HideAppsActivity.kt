package quest.devinthebm.launcher

import android.os.Bundle
import java.io.Closeable
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
    private var catalogSubscription: Closeable? = null
    private var catalogGeneration = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        titleView.text = getString(R.string.title_hide_apps)

        adapter = ListRowAdapter(onClick = { toggle(it) })
        listView.adapter = adapter

        softKeys.set {
            left(getString(R.string.softkey_back)) { finish() }
        }
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
                AppRepository.getSnapshot()?.let { catalogGeneration = maxOf(catalogGeneration, it.generation) }
                displayApps(loaded)
            }
        }.start()
    }

    private fun displayApps(loaded: List<AppInfo>) {
        apps = loaded
        adapter.submit(loaded.map { rowFor(it) })
        focusFirst()
        AppRepository.requestIcons(this, loaded, AppRepository.VISIBLE_ICON_PRIORITY) { hydrated ->
            runOnUiThread {
                val index = apps.indexOfFirst { it.key == hydrated.key }
                if (!isDestroyed && index >= 0) {
                    apps = apps.toMutableList().also { it[index] = hydrated }
                    adapter.updateRow(index, rowFor(hydrated))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        catalogSubscription = AppRepository.observeCatalog { snapshot ->
            if (snapshot.generation > catalogGeneration && !isDestroyed) {
                catalogGeneration = snapshot.generation
                displayApps(snapshot.apps)
            }
        }
    }

    override fun onStop() {
        catalogSubscription?.close()
        catalogSubscription = null
        super.onStop()
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

}
