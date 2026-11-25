package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import java.io.Closeable
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import quest.devinthebm.launcher.data.AppInfo
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

/**
 * Pick-an-app dialog used when assigning a Home shortcut. Returns the chosen
 * app's [key] via [EXTRA_APP_KEY].
 *
 * Select an app to pin its main entry, or press the "Activities" soft key to
 * drill into that app and pin a specific activity instead. Lists all installed
 * apps (including hidden ones, so a hidden app can still be pinned to Home).
 */
class AppPickerActivity : BaseListActivity() {

    private lateinit var adapter: ListRowAdapter
    private var apps: List<AppInfo> = emptyList()
    private var catalogSubscription: Closeable? = null
    private var catalogGeneration = -1

    private val activityPicker = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(EXTRA_APP_KEY)?.let { key ->
                // Forward the chosen activity back to whoever opened the app picker.
                setResult(RESULT_OK, Intent().putExtra(EXTRA_APP_KEY, key))
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titleView.text = getString(R.string.title_pick_app)

        adapter = ListRowAdapter(onClick = { pick(it) })
        listView.adapter = adapter

        softKeys.set {
            left(getString(R.string.softkey_back)) { finish() }
            right(getString(R.string.softkey_activities)) { openActivities(focusedPosition()) }
        }

        loadApps()
        catalogSubscription = AppRepository.observeCatalog { snapshot ->
            if (snapshot.generation > catalogGeneration && !isDestroyed) {
                catalogGeneration = snapshot.generation
                displayApps(snapshot.apps)
            }
        }
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
        adapter.submit(loaded.map { Row(title = it.label, icon = it.icon) })
        focusFirst()
        AppRepository.requestIcons(this, loaded, AppRepository.VISIBLE_ICON_PRIORITY) { hydrated ->
            runOnUiThread {
                val index = apps.indexOfFirst { it.key == hydrated.key }
                if (!isDestroyed && index >= 0) {
                    apps = apps.toMutableList().also { it[index] = hydrated }
                    adapter.updateRow(index, Row(title = hydrated.label, icon = hydrated.icon))
                }
            }
        }
    }

    override fun onDestroy() {
        catalogSubscription?.close()
        super.onDestroy()
    }

    private fun pick(position: Int) {
        val app = apps.getOrNull(position) ?: return
        setResult(RESULT_OK, Intent().putExtra(EXTRA_APP_KEY, app.key))
        finish()
    }

    private fun openActivities(position: Int) {
        val app = apps.getOrNull(position) ?: return
        activityPicker.launch(
            Intent(this, ActivityPickerActivity::class.java)
                .putExtra(ActivityPickerActivity.EXTRA_PACKAGE, app.packageName)
                .putExtra(ActivityPickerActivity.EXTRA_TITLE, app.label),
        )
    }

    companion object {
        const val EXTRA_APP_KEY = "app_key"
    }
}
