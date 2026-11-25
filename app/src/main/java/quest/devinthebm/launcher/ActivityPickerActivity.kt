package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import java.io.Closeable
import quest.devinthebm.launcher.data.AppInfo
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

/**
 * Lists the launchable, exported activities of a single app so the user can pin
 * a deep screen (not just the app's main entry) to a Home shortcut. Returns the
 * chosen activity component via [AppPickerActivity.EXTRA_APP_KEY].
 */
class ActivityPickerActivity : BaseListActivity() {

    private lateinit var adapter: ListRowAdapter
    private var activities: List<AppInfo> = emptyList()
    private lateinit var targetPackage: String
    private var catalogSubscription: Closeable? = null
    private var catalogGeneration = -1
    private var loadRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        titleView.text = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.title_pick_activity)

        adapter = ListRowAdapter(onClick = { pick(it) })
        listView.adapter = adapter

        softKeys.set {
            left(getString(R.string.softkey_back)) { finish() }
        }

        loadActivities()
    }

    override fun onStart() {
        super.onStart()
        catalogGeneration = AppRepository.getSnapshot()?.generation ?: catalogGeneration
        catalogSubscription = AppRepository.observeCatalog { snapshot ->
            if (snapshot.generation > catalogGeneration && !isDestroyed) {
                catalogGeneration = snapshot.generation
                loadActivities()
            }
        }
    }

    override fun onStop() {
        catalogSubscription?.close()
        catalogSubscription = null
        super.onStop()
    }

    private fun loadActivities() {
        val request = ++loadRequest
        Thread {
            val loaded = AppRepository.getActivities(this, targetPackage)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed || request != loadRequest) return@runOnUiThread
                displayActivities(loaded)
            }
        }.start()
    }

    private fun displayActivities(loaded: List<AppInfo>) {
        activities = loaded
        adapter.submit(
            loaded.map { Row(title = it.label, trailing = shortClassName(it.activityName), icon = it.icon) },
        )
        focusFirst()
        AppRepository.requestIcons(this, loaded, AppRepository.VISIBLE_ICON_PRIORITY) { hydrated ->
            runOnUiThread {
                val index = activities.indexOfFirst { it.key == hydrated.key }
                if (!isDestroyed && index >= 0) {
                    activities = activities.toMutableList().also { it[index] = hydrated }
                    adapter.updateRow(
                        index,
                        Row(
                            title = hydrated.label,
                            trailing = shortClassName(hydrated.activityName),
                            icon = hydrated.icon,
                        ),
                    )
                }
            }
        }
    }

    private fun pick(position: Int) {
        val activity = activities.getOrNull(position) ?: return
        setResult(RESULT_OK, Intent().putExtra(AppPickerActivity.EXTRA_APP_KEY, activity.key))
        finish()
    }

    /** "com.app.ui.SettingsActivity" -> "SettingsActivity" to disambiguate rows. */
    private fun shortClassName(name: String) = name.substringAfterLast('.')

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_TITLE = "title"
    }
}
