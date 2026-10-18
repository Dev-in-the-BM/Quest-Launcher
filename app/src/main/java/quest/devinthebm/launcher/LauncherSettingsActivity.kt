package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.IconPackRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

class LauncherSettingsActivity : BaseListActivity() {

    private data class Setting(val title: String, val action: () -> Unit)

    private lateinit var prefs: LauncherPrefs
    private lateinit var adapter: ListRowAdapter
    private lateinit var settings: List<Setting>

    private var iconSizeRow = -1
    private var rightKeyRow = -1
    private var leftKeyRow = -1
    private var iconPackRow = -1
    private var drawerViewRow = -1
    private var notifAccessRow = -1
    private var notifCallsRow = -1
    private var notifMessagesRow = -1
    private var notifOtherRow = -1
    private var notifIconDotsRow = -1
    private var accentColorRow = -1
    private var iconShapeRow = -1
    private var iconBackgroundRow = -1

    private val pickRightKeyApp = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                prefs.setRightKeyApp(key)
                val label = AppRepository.resolveComponent(this, key)?.label
                if (label != null) {
                    Toast.makeText(this, getString(R.string.settings_right_key_set, label), Toast.LENGTH_SHORT).show()
                }
                refreshRows()
            }
        }
    }

    private val pickLeftKeyApp = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                prefs.setLeftKeyApp(key)
                val label = AppRepository.resolveComponent(this, key)?.label
                if (label != null) {
                    Toast.makeText(this, getString(R.string.settings_left_key_set, label), Toast.LENGTH_SHORT).show()
                }
                refreshRows()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        titleView.text = getString(R.string.title_launcher_settings)

        settings = listOf(
            Setting(getString(R.string.settings_icon_size)) { chooseIconSize() },
            Setting(getString(R.string.settings_left_key)) { chooseLeftKey() },
            Setting(getString(R.string.settings_right_key)) { chooseRightKey() },
            Setting(getString(R.string.settings_icon_pack)) { open(IconPackActivity::class.java) },
            Setting(getString(R.string.settings_drawer_view)) {
                prefs.setDrawerListViewEnabled(!prefs.isDrawerListViewEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_access)) { openNotificationAccessSettings() },
            Setting(getString(R.string.settings_notif_calls)) {
                prefs.setCallBadgeEnabled(!prefs.isCallBadgeEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_messages)) {
                prefs.setMessageBadgeEnabled(!prefs.isMessageBadgeEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_other)) {
                prefs.setOtherBadgeEnabled(!prefs.isOtherBadgeEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_notif_icon_dots)) {
                prefs.setIconNotificationDotEnabled(!prefs.isIconNotificationDotEnabled())
                refreshRows()
            },
            Setting(getString(R.string.settings_accent_color)) { chooseAccentColor() },
            Setting(getString(R.string.settings_icon_shape)) { chooseIconShape() },
            Setting(getString(R.string.settings_icon_background)) {
                prefs.setLegacyIconBackgroundEnabled(!prefs.isLegacyIconBackgroundEnabled())
                refreshRows()
            },
        )
        iconSizeRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_size) }
        leftKeyRow = settings.indexOfFirst { it.title == getString(R.string.settings_left_key) }
        rightKeyRow = settings.indexOfFirst { it.title == getString(R.string.settings_right_key) }
        iconPackRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_pack) }
        drawerViewRow = settings.indexOfFirst { it.title == getString(R.string.settings_drawer_view) }
        notifAccessRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_access) }
        notifCallsRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_calls) }
        notifMessagesRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_messages) }
        notifOtherRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_other) }
        notifIconDotsRow = settings.indexOfFirst { it.title == getString(R.string.settings_notif_icon_dots) }
        accentColorRow = settings.indexOfFirst { it.title == getString(R.string.settings_accent_color) }
        iconShapeRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_shape) }
        iconBackgroundRow = settings.indexOfFirst { it.title == getString(R.string.settings_icon_background) }

        adapter = ListRowAdapter(onClick = { settings[it].action() })
        listView.adapter = adapter

        softKeys.setLabels(
            getString(R.string.softkey_back),
            getString(R.string.softkey_select),
            null,
        )
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnCenterClick { settings[focusedPosition()].action() }
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

        val rightKey = prefs.getRightKeyApp()
        val rightTrailing = rightKey?.let {
            AppRepository.resolveComponent(this, it)?.label
        } ?: getString(R.string.settings_right_key_contacts)

        val leftKey = prefs.getLeftKeyApp()
        val leftTrailing = leftKey?.let {
            AppRepository.resolveComponent(this, it)?.label
        } ?: getString(R.string.settings_left_key_notices)

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
                leftKeyRow -> leftTrailing
                rightKeyRow -> rightTrailing
                iconPackRow -> iconPackTrailing
                drawerViewRow -> getString(
                    if (prefs.isDrawerListViewEnabled()) R.string.settings_drawer_view_list else R.string.settings_drawer_view_grid,
                )
                notifAccessRow -> notifAccessTrailing
                notifCallsRow -> onOff(prefs.isCallBadgeEnabled())
                notifMessagesRow -> onOff(prefs.isMessageBadgeEnabled())
                notifOtherRow -> onOff(prefs.isOtherBadgeEnabled())
                notifIconDotsRow -> onOff(prefs.isIconNotificationDotEnabled())
                accentColorRow -> getString(prefs.getAccentColor().labelRes)
                iconShapeRow -> getString(prefs.getIconShape().labelRes)
                iconBackgroundRow -> onOff(prefs.isLegacyIconBackgroundEnabled())
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

    private fun chooseIconShape() {
        val options = LauncherPrefs.IconShape.entries.toTypedArray()
        val labels = options.map { getString(it.labelRes) }.toTypedArray()
        val checked = options.indexOf(prefs.getIconShape()).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_icon_shape)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val shape = options[which]
                prefs.setIconShape(shape)
                Toast.makeText(this, getString(R.string.settings_icon_shape_set, labels[which]), Toast.LENGTH_SHORT).show()
                refreshRows()
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseRightKey() {
        val current = prefs.getRightKeyApp()
        val items = mutableListOf(getString(R.string.back_longpress_choose))
        if (current != null) items.add(getString(R.string.settings_right_key_contacts))
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_right_key)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> pickRightKeyApp.launch(Intent(this, AppPickerActivity::class.java))
                    1 -> {
                        prefs.setRightKeyApp(null)
                        Toast.makeText(this, R.string.settings_right_key_cleared, Toast.LENGTH_SHORT).show()
                        refreshRows()
                    }
                }
            }
            .show()
    }

    private fun chooseLeftKey() {
        val current = prefs.getLeftKeyApp()
        val items = mutableListOf(getString(R.string.back_longpress_choose))
        if (current != null) items.add(getString(R.string.settings_left_key_notices))
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_left_key)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> pickLeftKeyApp.launch(Intent(this, AppPickerActivity::class.java))
                    1 -> {
                        prefs.setLeftKeyApp(null)
                        Toast.makeText(this, R.string.settings_left_key_cleared, Toast.LENGTH_SHORT).show()
                        refreshRows()
                    }
                }
            }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
