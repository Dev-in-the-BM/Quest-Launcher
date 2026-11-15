package quest.devinthebm.launcher

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import quest.devinthebm.launcher.data.AppInfo
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.theme.ThemedActivity
import quest.devinthebm.launcher.theme.ThemeDescriptor
import quest.devinthebm.launcher.theme.LauncherComponentSelection
import quest.devinthebm.launcher.util.launchAppByKey

/** Fixed, non-scrolling 3x3 grid of user-selected favorite apps. */
class FavoritesActivity : ThemedActivity() {
    override fun activityThemeStyle(descriptor: ThemeDescriptor): Int =
        when (descriptor.components) {
            LauncherComponentSelection.KaiOS -> R.style.Theme_QuestLauncher_Drawer
            LauncherComponentSelection.Material2 -> R.style.Theme_QuestLauncher_Drawer_Material2
            LauncherComponentSelection.Aeris -> R.style.Theme_QuestLauncher_Drawer_Aeris
        }

    private lateinit var prefs: LauncherPrefs
    private lateinit var adapter: FavoritesAdapter
    private var apps = emptyList<AppInfo>()
    private var slotToAdd = 0

    private val picker = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                val selected = prefs.getFavoriteKeys()
                if (key !in selected) {
                    if (slotToAdd in selected.indices) selected[slotToAdd] = key
                    else if (selected.size < LauncherPrefs.MAX_FAVORITES) selected.add(key)
                    prefs.setFavoriteKeys(selected)
                    refresh()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        val accent = prefs.getAccentColor()
        if (launcherTheme.components == LauncherComponentSelection.KaiOS && accent.themeOverlayRes != 0) {
            theme.applyStyle(accent.themeOverlayRes, true)
        }
        setContentView(R.layout.activity_favorites)
        val grid = findViewById<RecyclerView>(R.id.favorites_grid)
        grid.layoutManager = GridLayoutManager(this, 3)
        grid.isNestedScrollingEnabled = false
        grid.isFocusable = false
        adapter = FavoritesAdapter(
            onTap = { position ->
                val key = prefs.getFavoriteKeys().getOrNull(position)
                if (key == null) openPicker(position) else launchAppByKey(key)
            },
            onLongPress = { position ->
                val keys = prefs.getFavoriteKeys()
                if (position in keys.indices) {
                    AlertDialog.Builder(this)
                        .setItems(arrayOf(getString(R.string.favorites_remove))) { _, _ ->
                            keys.removeAt(position)
                            prefs.setFavoriteKeys(keys)
                            refresh()
                        }.show()
                }
            },
        )
        grid.adapter = adapter
        refresh()
    }

    private fun openPicker(position: Int) {
        slotToAdd = position
        picker.launch(Intent(this, AppPickerActivity::class.java))
    }

    private fun refresh() {
        Thread {
            val loaded = prefs.getFavoriteKeys().mapNotNull { AppRepository.resolveComponent(this, it) }
            if (isDestroyed) return@Thread
            runOnUiThread { if (!isDestroyed) { apps = loaded; adapter.submit(prefs.getFavoriteKeys().mapNotNull { key -> loaded.find { it.key == key } }) } }
        }.start()
    }

    private class FavoritesAdapter(
        private val onTap: (Int) -> Unit,
        private val onLongPress: (Int) -> Unit,
    ) : RecyclerView.Adapter<FavoritesAdapter.Holder>() {
        private var items = emptyList<AppInfo>()
        fun submit(apps: List<AppInfo>) { items = apps; notifyDataSetChanged() }
        override fun getItemCount() = 9
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_slot, parent, false)
            return Holder(view)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items.getOrNull(position), position)
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon = view.findViewById<ImageView>(R.id.icon)
            private val mark = view.findViewById<TextView>(R.id.empty_mark)
            fun bind(app: AppInfo?, position: Int) {
                icon.setImageDrawable(app?.icon)
                icon.contentDescription = app?.label ?: itemView.context.getString(R.string.favorites_add)
                mark.visibility = if (app == null) View.VISIBLE else View.GONE
                itemView.setOnClickListener { onTap(position) }
                itemView.setOnLongClickListener { if (app != null) onLongPress(position); app != null }
            }
        }
    }
}
