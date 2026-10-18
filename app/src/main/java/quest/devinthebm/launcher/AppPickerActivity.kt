package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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

        softKeys.setLabels(
            getString(R.string.softkey_back),
            getString(R.string.softkey_select),
            getString(R.string.softkey_activities),
        )
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnCenterClick { pick(focusedPosition()) }
        softKeys.setOnRightClick { openActivities(focusedPosition()) }

        loadApps()
    }

    private fun loadApps() {
        Thread {
            val loaded = AppRepository.getAllApps(this)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                apps = loaded
                adapter.submit(loaded.map { Row(title = it.label, icon = it.icon) })
                focusFirst()
            }
        }.start()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SOFT_LEFT -> { finish(); return true }
            KeyEvent.KEYCODE_SOFT_RIGHT -> { openActivities(focusedPosition()); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_APP_KEY = "app_key"
    }
}
