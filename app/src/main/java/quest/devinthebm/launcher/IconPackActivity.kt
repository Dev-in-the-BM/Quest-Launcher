package quest.devinthebm.launcher

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import quest.devinthebm.launcher.data.IconPackRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

/**
 * Lists icon pack apps detected on the device (anything declaring the standard
 * "org.adw.launcher.THEMES" intent most icon packs support, Nova/ADW/Apex
 * included) and applies one launcher-wide. Apps with their own per-app icon
 * override (set via "Change Icon" in the App Drawer) keep that icon regardless.
 */
class IconPackActivity : BaseListActivity() {

    private data class Entry(val title: String, val packageName: String?, val icon: Drawable?)

    private lateinit var prefs: LauncherPrefs
    private lateinit var adapter: ListRowAdapter
    private var entries: List<Entry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        titleView.text = getString(R.string.title_icon_packs)

        adapter = ListRowAdapter(onClick = { pick(it) })
        listView.adapter = adapter

        softKeys.setLabels(getString(R.string.softkey_back), getString(R.string.softkey_select), null)
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnCenterClick { pick(focusedPosition()) }

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        Thread {
            val packs = IconPackRepository.getInstalledIconPacks(this)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val active = prefs.getActiveIconPack()
                entries = listOf(Entry(getString(R.string.icon_pack_default), null, null)) +
                    packs.map { Entry(it.label, it.packageName, it.icon) }
                adapter.submit(
                    entries.map { entry ->
                        Row(
                            title = entry.title,
                            trailing = if (entry.packageName == active) getString(R.string.settings_toggle_on) else null,
                            icon = entry.icon,
                        )
                    },
                )
                focusFirst()
            }
        }.start()
    }

    private fun pick(position: Int) {
        val entry = entries.getOrNull(position) ?: return
        prefs.setActiveIconPack(entry.packageName)
        Toast.makeText(
            this,
            if (entry.packageName == null) getString(R.string.icon_pack_reset)
            else getString(R.string.icon_pack_applied, entry.title),
            Toast.LENGTH_SHORT,
        ).show()
        load()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
