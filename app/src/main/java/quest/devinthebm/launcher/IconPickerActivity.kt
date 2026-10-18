package quest.devinthebm.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import quest.devinthebm.launcher.data.BuiltInIcons
import quest.devinthebm.launcher.data.IconPackRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.ui.IconGridAdapter
import quest.devinthebm.launcher.ui.SoftKeyBar

/**
 * Lets the user replace one app's icon with any icon from an installed icon
 * pack, or from the launcher's own bundled icon set (always available, even
 * with no icon pack installed).
 */
class IconPickerActivity : AppCompatActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var appKey: String
    private lateinit var titleView: TextView
    private lateinit var grid: RecyclerView
    private lateinit var adapter: IconGridAdapter
    private var currentPack: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(EXTRA_APP_KEY)
        if (key == null) {
            finish()
            return
        }
        appKey = key
        prefs = LauncherPrefs(this)
        prefs.getAccentColor().let { if (it.themeOverlayRes != 0) theme.applyStyle(it.themeOverlayRes, true) }

        // Reuses the App Drawer's title/grid/soft-key layout; its page indicator
        // isn't relevant here so it's hidden.
        setContentView(R.layout.activity_app_drawer)
        titleView = findViewById(R.id.title)
        grid = findViewById(R.id.apps_grid)
        findViewById<View>(R.id.page_indicator).visibility = View.GONE
        val softKeys = findViewById<SoftKeyBar>(R.id.soft_keys)

        adapter = IconGridAdapter(
            onClick = { name -> applyIcon(name) },
            iconLoader = { name ->
                when (currentPack) {
                    null -> null
                    BuiltInIcons.PACK_ID -> BuiltInIcons.loadIcon(this, name)
                    else -> IconPackRepository.loadIcon(this, currentPack!!, name)
                }
            },
        )
        grid.layoutManager = GridLayoutManager(this, COLUMNS)
        grid.adapter = adapter

        softKeys.setLabels(getString(R.string.softkey_back), null, null)
        softKeys.setOnLeftClick { finish() }

        titleView.text = getString(R.string.icon_picker_choose_pack)
        choosePack()
    }

    private fun choosePack() {
        Thread {
            val packs = IconPackRepository.getInstalledIconPacks(this)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                // The bundled set is always offered first, so there's something
                // to pick from even with no icon pack installed.
                val labels = listOf(getString(R.string.icon_picker_built_in)) + packs.map { it.label }
                val packageNames = listOf(BuiltInIcons.PACK_ID) + packs.map { it.packageName }
                if (labels.size == 1) {
                    openSource(packageNames[0], labels[0])
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.icon_picker_choose_pack)
                        .setItems(labels.toTypedArray()) { _, which -> openSource(packageNames[which], labels[which]) }
                        .setOnCancelListener { finish() }
                        .show()
                }
            }
        }.start()
    }

    private fun openSource(packageName: String, label: String) {
        currentPack = packageName
        titleView.text = label
        if (packageName == BuiltInIcons.PACK_ID) {
            adapter.submit(BuiltInIcons.names())
            return
        }
        Thread {
            val names = IconPackRepository.getDrawableNames(this, packageName)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.submit(names)
            }
        }.start()
    }

    private fun applyIcon(name: String) {
        val pack = currentPack ?: return
        prefs.setIconOverride(appKey, pack, name)
        Toast.makeText(this, R.string.icon_picker_applied, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_APP_KEY = "app_key"
        private const val COLUMNS = 4
    }
}
