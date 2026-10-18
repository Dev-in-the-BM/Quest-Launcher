package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

/** The launcher's own settings menu, reached from the Home "Options" soft key. */
class OptionsActivity : BaseListActivity() {

    private data class Option(val title: String, val action: () -> Unit)

    private lateinit var prefs: LauncherPrefs
    private lateinit var adapter: ListRowAdapter
    private lateinit var options: List<Option>

    /** Index of the back-long-press row within [options], so its trailing text can be refreshed. */
    private var backLongPressRow = -1

    private val pickBackLongPressApp = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                prefs.setBackLongPressApp(key)
                refreshBackLongPressRow()
                val label = AppRepository.resolveComponent(this, key)?.label
                if (label != null) {
                    Toast.makeText(this, getString(R.string.back_longpress_app_set, label), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        titleView.text = getString(R.string.title_options)

        options = listOf(
            Option(getString(R.string.opt_all_apps)) { open(AppDrawerActivity::class.java) },
            Option(getString(R.string.opt_launcher_settings)) { open(LauncherSettingsActivity::class.java) },
            Option(getString(R.string.opt_customize_shortcuts)) { open(ShortcutConfigActivity::class.java) },
            Option(getString(R.string.opt_hide_apps)) { open(HideAppsActivity::class.java) },
            Option(getString(R.string.opt_back_longpress)) { configureBackLongPress() },
            Option(getString(R.string.opt_set_wallpaper)) { open(WallpaperPickerActivity::class.java) },
            Option(getString(R.string.opt_default_launcher)) { startSafely(Intent(Settings.ACTION_HOME_SETTINGS)) },
            Option(getString(R.string.opt_system_settings)) { startSafely(Intent(Settings.ACTION_SETTINGS)) },
        )
        backLongPressRow = options.indexOfFirst { it.title == getString(R.string.opt_back_longpress) }

        adapter = ListRowAdapter(onClick = { options[it].action() })
        listView.adapter = adapter
        adapter.submit(options.map { Row(title = it.title) })
        refreshBackLongPressRow()

        softKeys.setLabels(
            getString(R.string.softkey_back),
            getString(R.string.softkey_select),
            null,
        )
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnCenterClick { options[focusedPosition()].action() }
        focusFirst()
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun configureBackLongPress() {
        if (prefs.getBackLongPressApp() == null) {
            pickBackLongPressApp.launch(Intent(this, AppPickerActivity::class.java))
            return
        }
        showBackLongPressDialog()
    }

    private fun showBackLongPressDialog() {
        AlertDialog.Builder(this)
            .setItems(arrayOf(getString(R.string.back_longpress_choose), getString(R.string.back_longpress_clear))) { _, which ->
                when (which) {
                    0 -> pickBackLongPressApp.launch(Intent(this, AppPickerActivity::class.java))
                    1 -> {
                        prefs.setBackLongPressApp(null)
                        refreshBackLongPressRow()
                        Toast.makeText(this, R.string.back_longpress_app_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun refreshBackLongPressRow() {
        if (backLongPressRow < 0) return
        val key = prefs.getBackLongPressApp()
        val label = key?.let { AppRepository.resolveComponent(this, it)?.label }
            ?: getString(R.string.back_longpress_not_set)
        adapter.updateRow(backLongPressRow, Row(title = options[backLongPressRow].title, trailing = label))
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
