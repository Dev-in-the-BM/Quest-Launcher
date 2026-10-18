package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import quest.devinthebm.launcher.data.AppInfo
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.Row

/**
 * Customizes the ordered list of home shortcuts. Selecting a shortcut row (or
 * pressing its number key) reassigns it; the trailing "Add shortcut" row appends
 * a new one; the Clear soft key removes the focused shortcut.
 */
class ShortcutConfigActivity : BaseListActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var adapter: ListRowAdapter
    private var appMap: Map<String, AppInfo> = emptyMap()
    private var shortcuts: List<String> = emptyList()
    private var pendingIndex = -1

    private val pickLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && pendingIndex >= 0) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                prefs.setShortcutAt(pendingIndex, key)
            }
        }
        pendingIndex = -1
        reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        titleView.text = getString(R.string.title_shortcuts)

        adapter = ListRowAdapter(onClick = { onRowClick(it) })
        listView.adapter = adapter

        softKeys.setLabels(
            getString(R.string.softkey_back),
            getString(R.string.softkey_select),
            getString(R.string.softkey_clear),
        )
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnCenterClick { onRowClick(focusedPosition()) }
        softKeys.setOnRightClick { clearAt(focusedPosition()) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        Thread {
            val keys = prefs.getShortcuts()
            val resolved = LinkedHashMap<String, AppInfo>()
            for (key in keys) AppRepository.resolveComponent(this, key)?.let { resolved[key] = it }
            val present = keys.filter { resolved.containsKey(it) }
            if (present.size != keys.size) prefs.setShortcuts(present)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                appMap = resolved
                shortcuts = present
                adapter.submit(buildRows())
                focusFirst()
            }
        }.start()
    }

    private fun buildRows(): List<Row> {
        val rows = shortcuts.mapIndexed { index, key ->
            val app = appMap[key]
            Row(
                title = "${getString(R.string.shortcut_slot_label, index + 1)}   ${app?.label.orEmpty()}",
                icon = app?.icon,
            )
        }.toMutableList()
        if (shortcuts.size < LauncherPrefs.MAX_SHORTCUTS) {
            rows.add(Row(title = getString(R.string.add_shortcut), iconRes = R.drawable.ic_add))
        }
        return rows
    }

    /** Rows 0..n-1 reassign a shortcut; the last (add) row appends one. */
    private fun onRowClick(position: Int) {
        if (position < 0) return
        pendingIndex = position.coerceAtMost(shortcuts.size)
        pickLauncher.launch(Intent(this, AppPickerActivity::class.java))
    }

    private fun clearAt(position: Int) {
        if (position in shortcuts.indices) {
            prefs.removeShortcutAt(position)
            reload()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                val index = keyCode - KeyEvent.KEYCODE_1
                if (index <= shortcuts.size && index < LauncherPrefs.MAX_SHORTCUTS) onRowClick(index)
                return true
            }
            KeyEvent.KEYCODE_SOFT_LEFT -> { finish(); return true }
            KeyEvent.KEYCODE_SOFT_RIGHT -> { clearAt(focusedPosition()); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
