package quest.devinthebm.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theonionsarewatching.yapchik.SoftkeyBar
import com.theonionsarewatching.yapchik.Softkeys
import com.theonionsarewatching.yapchik.Yapchik
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
    private lateinit var grid: RecyclerView
    private var appliedAccentColor: LauncherPrefs.AccentColor? = null
    private var editing = false
    private var focusedSlot = 0
    private var slotBeingEdited = 0

    private val picker = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                val slots = prefs.getFavoriteSlots()
                slots.forEachIndexed { index, existing -> if (existing == key) slots[index] = null }
                slots[slotBeingEdited] = key
                prefs.setFavoriteSlots(slots)
                refresh(slotBeingEdited)
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
        grid = findViewById(R.id.favorites_grid)
        appliedAccentColor = accent
        grid.layoutManager = object : GridLayoutManager(this, GRID_SIZE) {
            override fun canScrollVertically(): Boolean = false
            override fun canScrollHorizontally(): Boolean = false
        }
        grid.isNestedScrollingEnabled = false
        grid.isFocusable = false
        grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        adapter = FavoritesAdapter(
            cellHeight = 0,
            components = launcherTheme.components,
            isEditing = { editing },
            isSlotSelected = { editing && it == focusedSlot },
            onTap = ::onSlotTapped,
            onLongPress = { position -> if (editing && prefs.getFavoriteSlots()[position] != null) showSlotMenu(position) },
            onFocusChanged = { selectSlot(it) },
        )
        grid.adapter = adapter
        grid.addOnLayoutChangeListener { view, _, _, _, bottom, _, _, _, _ ->
            val height = (bottom - view.top - view.paddingTop - view.paddingBottom) / GRID_SIZE
            if (height > 0) adapter.setCellHeight(height)
        }
        updateSoftkeys()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getAccentColor() != appliedAccentColor) recreate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (isNumberKey(key)) {
            val position = numberKeyPosition(key)
            if (editing && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                position in 0 until GRID_SIZE * GRID_SIZE
            ) {
                focusSlot(position)
            } else if (!editing && event.action == KeyEvent.ACTION_UP && event.repeatCount == 0 &&
                position in 0 until GRID_SIZE * GRID_SIZE
            ) {
                // Launch on key-up so the release is consumed here before the
                // newly opened app or the underlying home activity can treat
                // the same physical digit as a dialer shortcut.
                prefs.getFavoriteSlots()[position]?.let(::launchAppByKey)
            }
            return true
        }
        // Never pass telephone keypad symbols through to the system dialer.
        if (key == KeyEvent.KEYCODE_STAR || key == KeyEvent.KEYCODE_POUND) return true

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (key) {
                KeyEvent.KEYCODE_DPAD_LEFT -> return moveFocusBy(-1, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return moveFocusBy(1, 0)
                KeyEvent.KEYCODE_DPAD_UP -> return moveFocusBy(0, -1)
                KeyEvent.KEYCODE_DPAD_DOWN -> return moveFocusBy(0, 1)
            }
        }
        if (editing && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (key) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    onSlotTapped(focusedSlot)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun numberKeyPosition(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 0
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 1
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 2
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 3
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 4
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 5
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 6
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 7
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 8
        else -> RecyclerView.NO_POSITION
    }

    private fun isNumberKey(keyCode: Int): Boolean =
        keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9

    private fun openPicker(position: Int) {
        slotBeingEdited = position
        picker.launch(Intent(this, AppPickerActivity::class.java))
    }

    private fun updateSoftkeys() {
        // Like the home and app-drawer screens, this bar sits over wallpaper:
        // keep its labels high-contrast regardless of the selected surface theme.
        Yapchik.style.textColor = Color.WHITE
        Softkeys.of(this).set {
            left(if (editing) getString(R.string.favorites_done) else getString(R.string.softkey_options)) {
                if (editing) setEditing(false) else showOptionsMenu()
            }
        }
        disableSoftkeyFocus()
        window.decorView.post { disableSoftkeyFocus() }
    }

    private fun disableSoftkeyFocus() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        for (index in 0 until content.childCount) {
            val bar = content.getChildAt(index) as? SoftkeyBar ?: continue
            bar.setBackgroundColor(Color.TRANSPARENT)
            bar.isFocusable = false
            bar.isFocusableInTouchMode = false
            val labelRow = bar.getChildAt(1) as? ViewGroup
            fun styleLabels(view: View) {
                if (view is TextView) {
                    view.setTextColor(Color.WHITE)
                    view.setShadowLayer(3f * resources.displayMetrics.density, 0f, 1f, 0xCC000000.toInt())
                } else if (view is ViewGroup) {
                    for (childIndex in 0 until view.childCount) styleLabels(view.getChildAt(childIndex))
                }
            }
            labelRow?.let(::styleLabels)
            fun disable(view: View) {
                view.isFocusable = false
                view.isFocusableInTouchMode = false
                if (view is ViewGroup) {
                    for (childIndex in 0 until view.childCount) disable(view.getChildAt(childIndex))
                }
            }
            disable(bar)
            bar.post {
                (bar.getChildAt(1) as? ViewGroup)?.let(::styleLabels)
            }
        }
    }

    private fun findSoftkeyAnchor(): View {
        val content = findViewById<ViewGroup>(android.R.id.content)
        val bar = (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<SoftkeyBar>()
            .firstOrNull()
        return (bar?.getChildAt(1) as? ViewGroup)?.getChildAt(0) ?: grid
    }

    private fun showOptionsMenu() {
        val popup = createPopup(findSoftkeyAnchor())
        popup.menu.add(getString(R.string.favorites_edit))
        popup.setOnMenuItemClickListener {
            setEditing(true)
            true
        }
        popup.show()
    }

    private fun showSlotMenu(position: Int) {
        val anchor = grid.layoutManager?.findViewByPosition(position) ?: return
        val popup = createPopup(anchor)
        popup.menu.add(getString(R.string.favorites_change_app))
        popup.menu.add(getString(R.string.favorites_remove))
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                getString(R.string.favorites_change_app) -> openPicker(position)
                getString(R.string.favorites_remove) -> {
                    val slots = prefs.getFavoriteSlots()
                    slots[position] = null
                    prefs.setFavoriteSlots(slots)
                    refresh(position)
                }
            }
            true
        }
        popup.show()
    }

    private fun createPopup(anchor: View): PopupMenu {
        // Use the activity's themed context so PopupMenu inherits the selected
        // KaiOS palette and accent instead of resetting to the base theme.
        return PopupMenu(this, anchor, Gravity.TOP or Gravity.START)
    }

    private fun setEditing(enabled: Boolean) {
        editing = enabled
        if (!enabled) {
            focusedSlot = prefs.getFavoriteSlots().indexOfFirst { it != null }.takeIf { it >= 0 } ?: 0
        }
        adapter.notifyDataSetChanged()
        updateVisibleSelection()
        updateSoftkeys()
        if (enabled) focusSlot(0)
    }

    private fun moveFocusBy(columnDelta: Int, rowDelta: Int): Boolean {
        val column = focusedSlot % GRID_SIZE
        val row = focusedSlot / GRID_SIZE
        var targetColumn = column
        var targetRow = row
        val slots = prefs.getFavoriteSlots()
        repeat(GRID_SIZE - 1) {
            targetColumn = (targetColumn + columnDelta).coerceIn(0, GRID_SIZE - 1)
            targetRow = (targetRow + rowDelta).coerceIn(0, GRID_SIZE - 1)
            val candidate = targetRow * GRID_SIZE + targetColumn
            if (candidate == focusedSlot) return@repeat
            if (editing || slots[candidate] != null) {
                focusSlot(candidate)
                return true
            }
        }
        return true
    }

    private fun onSlotTapped(position: Int) {
        selectSlot(position)
        val key = prefs.getFavoriteSlots()[position]
        if (editing) {
            if (key == null) openPicker(position) else showSlotMenu(position)
        } else {
            key?.let(::launchAppByKey)
        }
    }

    private fun focusSlot(position: Int) {
        selectSlot(position)
        grid.post {
            grid.layoutManager?.findViewByPosition(focusedSlot)?.let { view ->
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.requestFocus()
            }
        }
    }

    private fun selectSlot(position: Int) {
        val previous = focusedSlot
        focusedSlot = position.coerceIn(0, GRID_SIZE * GRID_SIZE - 1)
        grid.layoutManager?.findViewByPosition(previous)?.isSelected = false
        grid.layoutManager?.findViewByPosition(focusedSlot)?.isSelected = editing
    }

    private fun updateVisibleSelection() {
        for (position in 0 until GRID_SIZE * GRID_SIZE) {
            grid.layoutManager?.findViewByPosition(position)?.isSelected =
                editing && position == focusedSlot
        }
    }

    private fun refresh(focusPosition: Int = 0) {
        Thread {
            val slots = prefs.getFavoriteSlots()
            val loaded = slots.map { key -> key?.let { AppRepository.resolveComponent(this, it) } }
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.submit(loaded)
                val target = if (editing) focusPosition else slots.indexOfFirst { it != null }
                if (target >= 0) focusSlot(target) else grid.clearFocus()
            }
        }.start()
    }

    private class FavoritesAdapter(
        private var cellHeight: Int,
        private val components: LauncherComponentSelection,
        private val isEditing: () -> Boolean,
        private val isSlotSelected: (Int) -> Boolean,
        private val onTap: (Int) -> Unit,
        private val onLongPress: (Int) -> Unit,
        private val onFocusChanged: (Int) -> Unit,
    ) : RecyclerView.Adapter<FavoritesAdapter.Holder>() {
        private var items = emptyList<AppInfo?>()
        fun setCellHeight(height: Int) {
            if (cellHeight == height) return
            cellHeight = height
            notifyDataSetChanged()
        }
        fun submit(apps: List<AppInfo?>) { items = apps; notifyDataSetChanged() }
        override fun getItemCount() = GRID_SIZE * GRID_SIZE
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_slot, parent, false)
            return Holder(view)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
                height = cellHeight.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT
            }
            holder.bind(items.getOrNull(position), position)
        }
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon = view.findViewById<ImageView>(R.id.icon)
            private val mark = view.findViewById<TextView>(R.id.empty_mark)
            private val keyNumber = view.findViewById<TextView>(R.id.key_number)
            fun bind(app: AppInfo?, position: Int) {
                itemView.isFocusable = app != null || isEditing()
                itemView.isFocusableInTouchMode = app != null || isEditing()
                itemView.isClickable = true
                itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onFocusChanged(position)
                }
                itemView.isSelected = isSlotSelected(position)
                icon.setImageDrawable(app?.icon)
                keyNumber.text = (position + 1).toString()
                keyNumber.visibility = if (app != null || isEditing()) View.VISIBLE else View.GONE
                itemView.background = itemView.context.getDrawable(
                    when (components) {
                        LauncherComponentSelection.KaiOS -> R.drawable.bg_item_focus
                        LauncherComponentSelection.Material2 -> R.drawable.m2_grid_focus
                        LauncherComponentSelection.Aeris -> R.drawable.aeris_grid_focus
                    },
                )
                itemView.contentDescription = itemView.context.getString(
                    R.string.favorites_slot_description,
                    position + 1,
                    app?.label ?: itemView.context.getString(R.string.favorites_add),
                )
                mark.visibility = if (app == null && isEditing()) View.VISIBLE else View.GONE
                itemView.setOnClickListener { onTap(position) }
                itemView.setOnLongClickListener { if (app != null) onLongPress(position); app != null }
            }
        }
    }

    companion object {
        private const val GRID_SIZE = 3
    }
}
