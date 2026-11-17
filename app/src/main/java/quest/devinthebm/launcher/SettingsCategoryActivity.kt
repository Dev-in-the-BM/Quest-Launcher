package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.IconPackRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row
import quest.devinthebm.launcher.theme.LauncherThemeCatalog
import quest.devinthebm.launcher.theme.LauncherThemeStore

class SettingsCategoryActivity : BaseListActivity() {

    private data class Setting(val title: String, val category: String, val action: () -> Unit)

    private lateinit var prefs: LauncherPrefs
    private lateinit var adapter: ListRowAdapter
    private lateinit var settings: List<Setting>

    private var iconSizeRow = -1
    private var iconPackRow = -1
    private var drawerViewRow = -1
    private var drawerKeypadRow = -1
    private var notifAccessRow = -1
    private var notifCallsRow = -1
    private var notifMessagesRow = -1
    private var notifOtherRow = -1
    private var notifIconDotsRow = -1
    private var accentColorRow = -1
    private var iconShapeRow = -1
    private var iconBackgroundRow = -1
    private var themeRow = -1
    private var backLongPressRow = -1
    private var homeKeyRows = emptyMap<Int, LauncherPrefs.HomeKey>()
    private var pendingHomeKey: LauncherPrefs.HomeKey? = null

    private val pickHomeKeyApp = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
            pendingHomeKey?.let {
                prefs.setHomeKeyApp(it, key)
                prefs.setHomeKeyAction(it, LauncherPrefs.HomeKeyAction.APP)
            }
        }
        pendingHomeKey = null
        refreshRows()
    }

    private var category: String = CATEGORY_HOME_NAVIGATION

    private val pickBackLongPressApp = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                prefs.setBackLongPressApp(key)
                val label = AppRepository.resolveComponent(this, key)?.label
                if (label != null) Toast.makeText(this, getString(R.string.back_longpress_app_set, label), Toast.LENGTH_SHORT).show()
                refreshRows()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: CATEGORY_HOME_NAVIGATION
        titleView.text = getString(categoryTitle(category))

        settings = listOf(
            *LauncherPrefs.HomeKey.entries.map { key ->
                Setting(getString(key.labelRes), CATEGORY_HOME_NAVIGATION) { configureHomeKey(key) }
            }.toTypedArray(),
            Setting(getString(R.string.opt_back_longpress), CATEGORY_HOME_NAVIGATION) { configureBackLongPress() },
            Setting(getString(R.string.settings_icon_size), CATEGORY_APP_DRAWER) { chooseIconSize() },
            Setting(getString(R.string.settings_drawer_view), CATEGORY_APP_DRAWER) {
                prefs.setDrawerListViewEnabled(!prefs.isDrawerListViewEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_drawer_keypad), CATEGORY_APP_DRAWER) { chooseDrawerKeypadAction() },
            Setting(getString(R.string.settings_icon_pack), CATEGORY_APPEARANCE) { open(IconPackActivity::class.java) },
            Setting(getString(R.string.settings_accent_color), CATEGORY_APPEARANCE) { chooseAccentColor() },
            Setting(getString(R.string.settings_theme), CATEGORY_APPEARANCE) { chooseTheme() },
            Setting(getString(R.string.settings_icon_shape), CATEGORY_APPEARANCE) { chooseIconShape() },
            Setting(getString(R.string.settings_icon_background), CATEGORY_APPEARANCE) {
                prefs.setLegacyIconBackgroundEnabled(!prefs.isLegacyIconBackgroundEnabled())
                AppRepository.invalidateCache()
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_access), CATEGORY_NOTIFICATIONS) { openNotificationAccessSettings() },
            Setting(getString(R.string.settings_notif_calls), CATEGORY_NOTIFICATIONS) {
                prefs.setCallBadgeEnabled(!prefs.isCallBadgeEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_messages), CATEGORY_NOTIFICATIONS) {
                prefs.setMessageBadgeEnabled(!prefs.isMessageBadgeEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_other), CATEGORY_NOTIFICATIONS) {
                prefs.setOtherBadgeEnabled(!prefs.isOtherBadgeEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_icon_dots), CATEGORY_NOTIFICATIONS) {
                prefs.setIconNotificationDotEnabled(!prefs.isIconNotificationDotEnabled())
                refreshRows()
            },
        ).filter { it.category == category }
        iconSizeRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_size) }
        iconPackRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_pack) }
        drawerViewRow = settings.indexOfFirst { it.title == getString(R.string.settings_drawer_view) }
        drawerKeypadRow = settings.indexOfFirst { it.title == getString(R.string.settings_drawer_keypad) }
        notifAccessRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_access) }
        notifCallsRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_calls) }
        notifMessagesRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_messages) }
        notifOtherRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_other) }
        notifIconDotsRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_icon_dots) }
        accentColorRow = settings.indexOfFirst { it.title == getString(R.string.settings_accent_color) }
        themeRow = settings.indexOfFirst { it.title == getString(R.string.settings_theme) }
        iconShapeRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_shape) }
        iconBackgroundRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_background) }
        backLongPressRow = settings.indexOfFirst { it.title == getString(R.string.opt_back_longpress) }
        homeKeyRows = LauncherPrefs.HomeKey.entries.associateBy { key -> settings.indexOfFirst { it.title == getString(key.labelRes) } }

        adapter = ListRowAdapter(onClick = { settings[it].action() })
        listView.adapter = adapter

        softKeys.set {
            left(getString(R.string.softkey_back)) { finish() }
        }
        refreshRows()
        focusFirst()
    }

    override fun onResume() {
        super.onResume()
        // Notification access is granted from a separate system screen, so
        // re-check it whenever we come back into view.
        refreshRows()
    }

    private fun refreshRows() {
        val iconPercent = prefs.getIconSizePercent()
        val iconSizeLabel = when {
            iconPercent <= 80 -> getString(R.string.settings_icon_small)
            iconPercent >= 120 -> getString(R.string.settings_icon_large)
            else -> getString(R.string.settings_icon_medium)
        }
        val iconTrailing = getString(R.string.settings_icon_current, iconSizeLabel, iconPercent)

        val activePack = prefs.getActiveIconPack()
        val iconPackTrailing = activePack?.let { pkg ->
            IconPackRepository.getInstalledIconPacks(this).find { it.packageName == pkg }?.label
        } ?: getString(R.string.icon_pack_default)

        val notifAccessTrailing = if (isNotificationAccessGranted()) {
            getString(R.string.settings_notif_access_granted)
        } else {
            getString(R.string.settings_notif_access_denied)
        }
        fun onOff(enabled: Boolean) =
            getString(if (enabled) R.string.settings_toggle_on else R.string.settings_toggle_off)

        val rows = settings.mapIndexed { index, setting ->
            val trailing = when (index) {
                iconSizeRow -> iconTrailing
                iconPackRow -> iconPackTrailing
                drawerViewRow -> getString(
                    if (prefs.isDrawerListViewEnabled()) R.string.settings_drawer_view_list else R.string.settings_drawer_view_grid,
                )
                drawerKeypadRow -> getString(prefs.getDrawerKeypadAction().labelRes)
                notifAccessRow -> notifAccessTrailing
                notifCallsRow -> onOff(prefs.isCallBadgeEnabled())
                notifMessagesRow -> onOff(prefs.isMessageBadgeEnabled())
                notifOtherRow -> onOff(prefs.isOtherBadgeEnabled())
                notifIconDotsRow -> onOff(prefs.isIconNotificationDotEnabled())
                accentColorRow -> getString(prefs.getAccentColor().labelRes)
                themeRow -> getString(LauncherThemeCatalog.resolve(LauncherThemeStore(prefs).read()).displayNameRes)
                iconShapeRow -> getString(prefs.getIconShape().labelRes)
                iconBackgroundRow -> onOff(prefs.isLegacyIconBackgroundEnabled())
                backLongPressRow -> prefs.getBackLongPressApp()?.let { AppRepository.resolveComponent(this, it)?.label }
                    ?: getString(R.string.back_longpress_not_set)
                in homeKeyRows.keys -> homeKeyRows[index]?.let { key ->
                    val action = prefs.getHomeKeyAction(key)
                    if (action == LauncherPrefs.HomeKeyAction.APP) prefs.getHomeKeyApp(key)?.let { AppRepository.resolveComponent(this, it)?.label }
                        ?: getString(action.labelRes)
                    else getString(action.labelRes)
                }
                else -> null
            }
            Row(title = setting.title, trailing = trailing)
        }
        adapter.submit(rows)
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun openNotificationAccessSettings() =
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun configureBackLongPress() {
        if (prefs.getBackLongPressApp() == null) {
            pickBackLongPressApp.launch(Intent(this, AppPickerActivity::class.java))
            return
        }
        android.app.AlertDialog.Builder(this)
            .setItems(arrayOf(getString(R.string.back_longpress_choose), getString(R.string.back_longpress_clear))) { _, which ->
                when (which) {
                    0 -> pickBackLongPressApp.launch(Intent(this, AppPickerActivity::class.java))
                    1 -> {
                        prefs.setBackLongPressApp(null)
                        Toast.makeText(this, R.string.back_longpress_app_cleared, Toast.LENGTH_SHORT).show()
                        refreshRows()
                    }
                }
            }
            .show()
    }

    private fun categoryTitle(category: String): Int = when (category) {
        CATEGORY_APP_DRAWER -> R.string.settings_category_app_drawer
        CATEGORY_APPEARANCE -> R.string.settings_category_appearance
        CATEGORY_NOTIFICATIONS -> R.string.settings_category_notifications
        else -> R.string.settings_category_home_navigation
    }

    private fun chooseIconSize() {
        val current = prefs.getIconSizePercent()
        val labels = arrayOf(
            getString(R.string.settings_icon_small),
            getString(R.string.settings_icon_medium),
            getString(R.string.settings_icon_large),
        )
        val values = listOf(80, 100, 120)
        val checked = values.indexOf(current).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_icon_size)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val percent = values[which]
                prefs.setIconSizePercent(percent)
                Toast.makeText(this, getString(R.string.settings_icon_size_set, labels[which]), Toast.LENGTH_SHORT).show()
                refreshRows()
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseDrawerKeypadAction() {
        val options = LauncherPrefs.DrawerKeypadAction.entries.toTypedArray()
        val labels = options.map { getString(it.labelRes) }.toTypedArray()
        val checked = options.indexOf(prefs.getDrawerKeypadAction()).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_drawer_keypad)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.setDrawerKeypadAction(options[which])
                Toast.makeText(this, getString(R.string.settings_drawer_keypad_set, labels[which]), Toast.LENGTH_SHORT).show()
                refreshRows()
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseAccentColor() {
        val options = LauncherPrefs.AccentColor.entries.toTypedArray()
        val labels = options.map { getString(it.labelRes) }.toTypedArray()
        val checked = options.indexOf(prefs.getAccentColor()).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_accent_color)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val color = options[which]
                prefs.setAccentColor(color)
                Toast.makeText(this, getString(R.string.settings_accent_color_set, labels[which]), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun chooseTheme() {
        val themes = LauncherThemeCatalog.descriptors
        val labels = themes.map { getString(it.displayNameRes) }.toTypedArray()
        val selected = LauncherThemeCatalog.resolve(LauncherThemeStore(prefs).read())
        val checked = themes.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                LauncherThemeStore(prefs).write(themes[which].id)
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseIconShape() {
        val options = LauncherPrefs.IconShape.entries.toTypedArray()
        val labels = options.map { getString(it.labelRes) }.toTypedArray()
        val checked = options.indexOf(prefs.getIconShape()).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_icon_shape)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val shape = options[which]
                prefs.setIconShape(shape)
                AppRepository.invalidateCache()
                Toast.makeText(this, getString(R.string.settings_icon_shape_set, labels[which]), Toast.LENGTH_SHORT).show()
                refreshRows()
                dialog.dismiss()
            }
            .show()
    }

    private fun configureHomeKey(key: LauncherPrefs.HomeKey) {
        val actions = LauncherPrefs.HomeKeyAction.entries.filter { it != LauncherPrefs.HomeKeyAction.RAIL || key !in listOf(LauncherPrefs.HomeKey.UP, LauncherPrefs.HomeKey.SOFT_LEFT, LauncherPrefs.HomeKey.SOFT_RIGHT) }
        val labels = actions.map { getString(it.labelRes) }.toMutableList()
        val current = prefs.getHomeKeyAction(key)
        val choices = actions.toMutableList()
        if (current == LauncherPrefs.HomeKeyAction.APP && prefs.getHomeKeyApp(key) != null) {
            labels.add(getString(R.string.back_longpress_clear)); choices.add(LauncherPrefs.HomeKeyAction.APP)
        }
        android.app.AlertDialog.Builder(this).setTitle(key.labelRes).setItems(labels.toTypedArray()) { _, which ->
            val action = choices[which]
            if (which == labels.lastIndex && current == LauncherPrefs.HomeKeyAction.APP && prefs.getHomeKeyApp(key) != null) {
                prefs.setHomeKeyApp(key, null)
            } else if (action == LauncherPrefs.HomeKeyAction.APP) {
                pendingHomeKey = key
                pickHomeKeyApp.launch(Intent(this, AppPickerActivity::class.java))
            } else prefs.setHomeKeyAction(key, action)
            refreshRows()
        }.show()
    }

    companion object {
        const val EXTRA_CATEGORY = "settings_category"
        const val CATEGORY_HOME_NAVIGATION = "home_navigation"
        const val CATEGORY_APP_DRAWER = "app_drawer"
        const val CATEGORY_APPEARANCE = "appearance"
        const val CATEGORY_NOTIFICATIONS = "notifications"
    }

}
