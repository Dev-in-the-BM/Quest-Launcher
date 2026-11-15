package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

/** The launcher's own settings menu, reached from the Home "Options" soft key. */
class OptionsActivity : BaseListActivity() {

    private data class Option(val title: String, val action: () -> Unit)

    private lateinit var adapter: ListRowAdapter
    private lateinit var options: List<Option>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titleView.text = getString(R.string.title_options)

        options = listOf(
            Option(getString(R.string.settings_category_home_navigation)) { openSettings(SettingsCategoryActivity.CATEGORY_HOME_NAVIGATION) },
            Option(getString(R.string.settings_category_app_drawer)) { openSettings(SettingsCategoryActivity.CATEGORY_APP_DRAWER) },
            Option(getString(R.string.settings_category_appearance)) { openSettings(SettingsCategoryActivity.CATEGORY_APPEARANCE) },
            Option(getString(R.string.settings_category_notifications)) { openSettings(SettingsCategoryActivity.CATEGORY_NOTIFICATIONS) },
            Option(getString(R.string.opt_all_apps)) { open(AppDrawerActivity::class.java) },
            Option(getString(R.string.opt_favorites)) { open(FavoritesActivity::class.java) },
            Option(getString(R.string.opt_customize_shortcuts)) { open(ShortcutConfigActivity::class.java) },
            Option(getString(R.string.opt_hide_apps)) { open(HideAppsActivity::class.java) },
            Option(getString(R.string.opt_set_wallpaper)) { open(WallpaperPickerActivity::class.java) },
            Option(getString(R.string.opt_default_launcher)) { openDefaultLauncherSettings() },
        )

        adapter = ListRowAdapter(onClick = { options[it].action() })
        listView.adapter = adapter
        adapter.submit(options.map { Row(title = it.title) })

        softKeys.set {
            left(getString(R.string.softkey_back)) { finish() }
        }
        focusFirst()
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun openSettings(category: String) {
        startActivity(Intent(this, SettingsCategoryActivity::class.java).putExtra(SettingsCategoryActivity.EXTRA_CATEGORY, category))
    }

    private fun openDefaultLauncherSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

}
