package com.flipos.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flipos.launcher.data.AppInfo
import com.flipos.launcher.data.AppRepository
import com.flipos.launcher.data.LauncherPrefs
import com.flipos.launcher.data.NotificationCounts
import com.flipos.launcher.data.NotificationDotColor
import com.flipos.launcher.ui.AppGridAdapter
import com.flipos.launcher.ui.ListRowAdapter
import com.flipos.launcher.ui.PageIndicatorView
import com.flipos.launcher.ui.Row
import com.flipos.launcher.ui.SoftKeyBar
import com.flipos.launcher.ui.SquircleDrawable
import com.flipos.launcher.util.launchAppByKey
import kotlin.math.ceil
import kotlin.math.min

/**
 * The "Menu": every non-hidden app, shown as either a 3x3 icon grid or a
 * single-column list (toggled in Launcher Settings).
 *
 * The grid pages nine apps at a time: D-pad navigates within a page, row by
 * row, and pressing down off the bottom row (or up off the top row) flips to
 * the next/previous page, tracked by a column of dots to the right. Number
 * keys 1-9 launch the matching position within the current page, echoing the
 * classic feature-phone menu. The list, by contrast, is one continuous,
 * ordinary scroll - no pages, no dots, no number-key shortcuts - since
 * chopping a list into same-sized chunks doesn't carry the same meaning a
 * grid page does and only made scrolling feel choppy.
 *
 * Center/OK opens the focused app. The Options soft key (or long-press) lets
 * the user pin an app to Home, hide it, or uninstall it.
 */
class AppDrawerActivity : AppCompatActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var grid: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var gridAdapter: AppGridAdapter
    private lateinit var listAdapter: ListRowAdapter
    private lateinit var softKeys: SoftKeyBar
    private lateinit var pageIndicator: PageIndicatorView
    private lateinit var gridFocusHighlight: View
    private val gridFocusDrawable = SquircleDrawable()
    private var gridFocusVisible = false

    private var allApps: List<AppInfo> = emptyList()
    private var currentPageItems: List<AppInfo> = emptyList()
    private var currentPage = 0
    private var listMode = false
    private var gridColumns = GRID_COLUMNS
    private var pageSize = PAGE_SIZE

    /** The accent color applied this onCreate, so [onResume] can detect a change and [recreate]. */
    private var appliedAccentColor: LauncherPrefs.AccentColor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        val accent = prefs.getAccentColor()
        appliedAccentColor = accent
        if (accent.themeOverlayRes != 0) theme.applyStyle(accent.themeOverlayRes, true)
        setContentView(R.layout.activity_app_drawer)

        grid = findViewById(R.id.apps_grid)
        titleView = findViewById(R.id.title)
        softKeys = findViewById(R.id.soft_keys)
        pageIndicator = findViewById(R.id.page_indicator)
        gridFocusHighlight = findViewById<View>(R.id.grid_focus_highlight).apply {
            background = gridFocusDrawable
            isSelected = true
        }

        // The window shows the wallpaper through a translucent overlay (see
        // Theme.FlipLauncher.Drawer); these two would otherwise paint over it
        // with their own solid bar, breaking the edge-to-edge KaiOS look.
        titleView.setBackgroundColor(Color.TRANSPARENT)
        softKeys.setBackgroundColor(Color.TRANSPARENT)

        // RecyclerView defaults to focusable so it has somewhere to park focus
        // when it's empty. We always have items and manage focus ourselves, and
        // leaving this on means the view itself can get "rescued" into holding
        // focus for a frame whenever a page swap detaches the previously
        // focused child - visible as the whole row/grid highlighting.
        grid.isFocusable = false

        gridAdapter = AppGridAdapter(
            onClick = { launchAppByKey(it.key) },
            onLongClick = { showContextMenu(it) },
            // Labels are gone from the grid; mirror the focused app's name
            // where "All Apps" normally sits so it's still identifiable.
            onFocusChanged = { app, itemView ->
                titleView.text = app.label
                showGridFocusHighlight(app, itemView)
            },
            iconSizePercent = prefs.getIconSizePercent(),
            hasNotification = ::hasNotification,
        )
        listAdapter = ListRowAdapter(
            onClick = { pos -> currentPageItems.getOrNull(pos)?.let { launchAppByKey(it.key) } },
            onLongClick = { pos -> currentPageItems.getOrNull(pos)?.let { showContextMenu(it) } },
            onFocusChanged = { pos -> currentPageItems.getOrNull(pos)?.let { titleView.text = it.label } },
        )
        grid.itemAnimator = null
        grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val oldPageSize = pageSize
            updatePageSize()
            if (oldPageSize != pageSize) refresh()
        }

        softKeys.setLabels(null, null, getString(R.string.softkey_options))
        softKeys.setCenterPlainLabel(getString(R.string.softkey_select).uppercase())
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnCenterClick { openFocused() }
        softKeys.setOnRightClick { optionsForFocused() }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getAccentColor() != appliedAccentColor) {
            recreate()
            return
        }
        applyViewMode()
        refresh()
    }

    /** Swap layout manager/adapter to match the current Launcher Settings choice. */
    private fun applyViewMode() {
        val wantList = prefs.isDrawerListViewEnabled()
        if (wantList == listMode && grid.adapter != null) return
        listMode = wantList
        if (listMode) hideGridFocusHighlight()
        updatePageSize()
        grid.layoutManager = if (listMode) LinearLayoutManager(this) else GridLayoutManager(this, gridColumns)
        grid.adapter = if (listMode) listAdapter else gridAdapter
    }

    private fun updatePageSize() {
        if (listMode) {
            pageSize = PAGE_SIZE
            return
        }
        val gridWidth = grid.width
        val gridHeight = grid.height
        if (gridWidth <= 0 || gridHeight <= 0) return

        gridColumns = GRID_COLUMNS
        val contentWidth = gridWidth - grid.paddingStart - grid.paddingEnd
        val contentHeight = gridHeight - grid.paddingTop - grid.paddingBottom
        val density = resources.displayMetrics.density
        val fitIconPx = AppGridAdapter.baseFitIconPx(contentWidth, contentHeight, gridColumns, density)
        val desiredIconPx = fitIconPx * prefs.getIconSizePercent() / 100
        val itemHeightPx = desiredIconPx + (AppGridAdapter.ITEM_OVERHEAD_DP * density).toInt()
        val rows = (contentHeight / itemHeightPx.coerceAtLeast(1)).coerceAtLeast(3)
        pageSize = gridColumns * rows
        // Below the floor of 3 rows, the icon-size clamp (not this page-size
        // math) is what actually keeps the grid from overflowing - it shrinks
        // icons to fit instead of forcing fewer, undersized rows.
        gridAdapter.setGridMetrics(contentWidth, contentHeight, gridColumns, rows)
    }

    private fun refresh() {
        Thread {
            val apps = AppRepository.getVisibleApps(this, prefs)
            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                allApps = apps
                if (listMode) {
                    bindList()
                } else {
                    val maxPage = (totalPages() - 1).coerceAtLeast(0)
                    bindPage(currentPage.coerceIn(0, maxPage))
                }
            }
        }.start()
    }

    private fun hasNotification(app: AppInfo): Boolean =
        prefs.isIconNotificationDotEnabled() && NotificationCounts.packagesWithNotifications.contains(app.packageName)

    private fun totalPages(): Int =
        if (allApps.isEmpty()) 1 else ceil(allApps.size / pageSize.toDouble()).toInt()

    /** Bind the full app list in one go - no pages, no dots, just a normal scroll. */
    private fun bindList(focusPosition: Int? = null) {
        currentPageItems = allApps
        listAdapter.submit(
            currentPageItems.map {
                Row(
                    title = it.label,
                    icon = it.icon,
                    keepWhiteTitle = true,
                    badgeColor = if (hasNotification(it)) NotificationDotColor.forIcon(it.key, it.icon) else null,
                )
            },
        )
        pageIndicator.visibility = View.GONE
        grid.post {
            if (currentPageItems.isEmpty()) return@post
            focusItemAt(focusPosition?.coerceIn(0, currentPageItems.size - 1) ?: 0)
        }
    }

    /** Swap the grid's contents to [page] and re-sync the dot indicator. */
    private fun bindPage(page: Int, focusPosition: Int? = null) {
        hideGridFocusHighlight()
        currentPage = page
        val start = page * pageSize
        val end = min(start + pageSize, allApps.size)
        currentPageItems = if (start < end) allApps.subList(start, end) else emptyList()
        gridAdapter.submit(currentPageItems)

        val pages = totalPages()
        pageIndicator.visibility = if (pages > 1) View.VISIBLE else View.GONE
        pageIndicator.setPageCount(pages)
        pageIndicator.setCurrentPage(page)

        grid.post {
            if (currentPageItems.isEmpty()) return@post
            val target = focusPosition?.coerceAtMost(currentPageItems.size - 1) ?: 0
            focusItemAt(target)
        }
    }

    /**
     * Focuses the row/icon at [target], waiting for it to attach first if
     * needed. A page swap can land on a row far enough down that the layout
     * pass hasn't created its view yet - findViewByPosition would silently
     * return null right after [bindPage], the requestFocus() would no-op, and
     * Android would "rescue" focus onto whatever view happens to be visible
     * (the top row) instead of leaving the request pending.
     */
    private fun focusItemAt(target: Int) {
        val lm = grid.layoutManager ?: return
        lm.findViewByPosition(target)?.let {
            it.requestFocus()
            return
        }
        grid.scrollToPosition(target)
        grid.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (grid.getChildAdapterPosition(view) != target) return
                grid.removeOnChildAttachStateChangeListener(this)
                view.requestFocus()
            }
            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })
    }

    /** Moves one focus surface beneath the grid, instead of snapping a new
     * background into each individual cell. */
    private fun showGridFocusHighlight(app: AppInfo, itemView: View) {
        if (listMode) return
        itemView.post {
            if (isDestroyed || grid.focusedChild !== itemView) return@post
            val iconFrame = itemView.findViewById<View>(R.id.icon_frame)
            val parent = gridFocusHighlight.parent as? FrameLayout ?: return@post
            val bounds = Rect().also {
                iconFrame.getDrawingRect(it)
                parent.offsetDescendantRectToMyCoords(iconFrame, it)
            }
            gridFocusDrawable.setColor(NotificationDotColor.forIcon(app.key, app.icon))
            val params = gridFocusHighlight.layoutParams as FrameLayout.LayoutParams
            val sizeChanged = params.width != bounds.width() || params.height != bounds.height()
            params.width = bounds.width()
            params.height = bounds.height()
            gridFocusHighlight.layoutParams = params
            gridFocusHighlight.animate().cancel()
            if (!gridFocusVisible || sizeChanged) {
                gridFocusHighlight.x = bounds.left.toFloat()
                gridFocusHighlight.y = bounds.top.toFloat()
                gridFocusHighlight.visibility = View.VISIBLE
                gridFocusVisible = true
            } else {
                gridFocusHighlight.animate()
                    .x(bounds.left.toFloat())
                    .y(bounds.top.toFloat())
                    .setDuration(GRID_HIGHLIGHT_SLIDE_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun hideGridFocusHighlight() {
        if (!::gridFocusHighlight.isInitialized) return
        gridFocusHighlight.animate().cancel()
        gridFocusHighlight.visibility = View.INVISIBLE
        gridFocusVisible = false
    }

    /** Flip to [page], keeping the focused column and landing on the matching row. */
    private fun goToPage(page: Int, landOnLastRow: Boolean) {
        if (page < 0 || page >= totalPages()) return
        val column = focusedPosition() % gridColumns
        bindPage(page, focusPosition = if (landOnLastRow) lastRowStartForPage(page) + column else column)
    }

    private fun lastRowStartForPage(page: Int): Int {
        val count = min(pageSize, allApps.size - page * pageSize).coerceAtLeast(1)
        return ((count - 1) / gridColumns) * gridColumns
    }

    /**
     * Moves focus one row up/down ([rowDelta] = -1/+1) within the current grid
     * page, landing on the same column. Only flips to the next/previous page
     * once already on the bottom/top row, instead of every press.
     */
    private fun moveFocusByRow(rowDelta: Int) {
        val itemCount = currentPageItems.size
        if (itemCount == 0) return
        val position = focusedPosition()
        val row = position / gridColumns
        val column = position % gridColumns
        val lastRow = (itemCount - 1) / gridColumns
        val targetRow = row + rowDelta
        when {
            targetRow < 0 -> goToPage(currentPage - 1, landOnLastRow = true)
            targetRow > lastRow -> goToPage(currentPage + 1, landOnLastRow = false)
            else -> {
                val target = (targetRow * gridColumns + column).coerceAtMost(itemCount - 1)
                grid.layoutManager?.findViewByPosition(target)?.requestFocus()
            }
        }
    }

    /**
     * Moves focus one cell left/right ([delta] = -1/+1) within the grid,
     * treating the page as a single sequence so that pressing right off the end
     * of a row lands on the first cell of the next row (and left off the start
     * of a row lands on the last cell of the previous one). Pressing past the
     * page's first/last cell flips to the adjacent page.
     */
    private fun moveFocusByColumn(delta: Int) {
        val itemCount = currentPageItems.size
        if (itemCount == 0) return
        val target = focusedPosition() + delta
        when {
            target < 0 -> {
                val prev = currentPage - 1
                if (prev < 0) return
                val prevCount = min(pageSize, allApps.size - prev * pageSize)
                bindPage(prev, focusPosition = prevCount - 1)
            }
            target >= itemCount -> {
                if (currentPage + 1 < totalPages()) bindPage(currentPage + 1, focusPosition = 0)
            }
            else -> grid.layoutManager?.findViewByPosition(target)?.requestFocus()
        }
    }

    /** Moves focus one row up/down ([delta] = -1/+1) in the (unpaged) list view. */
    private fun moveFocusLinear(delta: Int) {
        val itemCount = currentPageItems.size
        if (itemCount == 0) return
        val target = (focusedPosition() + delta).coerceIn(0, itemCount - 1)
        focusItemAt(target)
    }

    // --------------------------------------------------------------- Actions

    private fun focusedPosition(): Int {
        val child = grid.focusedChild ?: return 0
        val pos = grid.getChildAdapterPosition(child)
        return if (pos == RecyclerView.NO_POSITION) 0 else pos
    }

    private fun openFocused() {
        currentPageItems.getOrNull(focusedPosition())?.let { launchAppByKey(it.key) }
    }

    private fun optionsForFocused() {
        currentPageItems.getOrNull(focusedPosition())?.let { showContextMenu(it) }
    }

    private data class ContextItem(val label: String, val action: () -> Unit)

    private fun showContextMenu(app: AppInfo) {
        val items = mutableListOf(
            ContextItem(getString(R.string.ctx_open)) { launchAppByKey(app.key) },
            ContextItem(getString(R.string.ctx_add_home)) { addToHome(app) },
            ContextItem(getString(R.string.ctx_hide)) { hideApp(app) },
            ContextItem(getString(R.string.ctx_change_icon)) { changeIcon(app) },
        )
        if (prefs.getIconOverride(app.key) != null) {
            items.add(ContextItem(getString(R.string.ctx_reset_icon)) { resetIcon(app) })
        }
        val wrapEnabled = prefs.isIconWrapEnabled(app.key)
        items.add(
            ContextItem(getString(if (wrapEnabled) R.string.ctx_disable_wrap else R.string.ctx_enable_wrap)) {
                toggleIconWrap(app, !wrapEnabled)
            },
        )
        items.add(ContextItem(getString(R.string.ctx_uninstall)) { uninstallApp(app) })

        val titleView = layoutInflater.inflate(R.layout.dialog_app_context_title, null).apply {
            findViewById<TextView>(R.id.dialog_title_label).text = app.label
            findViewById<TextView>(R.id.dialog_title_package).text = app.packageName
        }
        AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setItems(items.map { it.label }.toTypedArray()) { _, which -> items[which].action() }
            .show()
    }

    private fun changeIcon(app: AppInfo) {
        startActivity(Intent(this, IconPickerActivity::class.java).putExtra(IconPickerActivity.EXTRA_APP_KEY, app.key))
    }

    private fun resetIcon(app: AppInfo) {
        prefs.clearIconOverride(app.key)
        Toast.makeText(this, R.string.icon_picker_reset, Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun toggleIconWrap(app: AppInfo, enabled: Boolean) {
        prefs.setIconWrapEnabled(app.key, enabled)
        refresh()
    }

    private fun addToHome(app: AppInfo) {
        val current = prefs.getShortcuts()
        when {
            current.contains(app.key) ->
                Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show()
            current.size >= LauncherPrefs.MAX_SHORTCUTS ->
                Toast.makeText(this, R.string.home_full, Toast.LENGTH_SHORT).show()
            else -> {
                prefs.addShortcut(app.key)
                Toast.makeText(this, "Added to Home", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideApp(app: AppInfo) {
        prefs.setHidden(app.key, true)
        Toast.makeText(this, "${app.label} hidden", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun uninstallApp(app: AppInfo) {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_DELETE,
                    Uri.fromParts("package", app.packageName, null),
                ),
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Can't uninstall app", Toast.LENGTH_SHORT).show()
        }
    }

    // ----------------------------------------------------------- Key handling

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                // Only the grid has a fixed nine-per-page shape for this
                // feature-phone shortcut to map onto; the list just scrolls.
                if (!listMode) {
                    currentPageItems.getOrNull(keyCode - KeyEvent.KEYCODE_1)?.let { launchAppByKey(it.key) }
                }
                return true
            }
            // Handled explicitly (rather than left to view focus search) so a
            // page flip only happens once focus is already on the bottom/top row.
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (listMode) moveFocusLinear(1) else moveFocusByRow(1); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { if (listMode) moveFocusLinear(-1) else moveFocusByRow(-1); return true }
            // In the grid, left/right wrap across rows (and pages) instead of
            // stopping at a row edge; the list has no columns to move between.
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (!listMode) { moveFocusByColumn(1); return true } }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (!listMode) { moveFocusByColumn(-1); return true } }
            KeyEvent.KEYCODE_SOFT_LEFT -> { finish(); return true }
            KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_MENU -> { optionsForFocused(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val GRID_COLUMNS = 3
        private const val PAGE_SIZE = 9
        private const val GRID_HIGHLIGHT_SLIDE_MS = 150L
    }
}
