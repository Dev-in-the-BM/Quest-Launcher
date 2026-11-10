package quest.devinthebm.launcher

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import quest.devinthebm.launcher.theme.ThemedActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import quest.devinthebm.launcher.data.AppInfo
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.data.NotificationCounts
import quest.devinthebm.launcher.data.NotificationDotColor
import quest.devinthebm.launcher.ui.AppGridAdapter
import quest.devinthebm.launcher.ui.ListRowAdapter
import quest.devinthebm.launcher.ui.PageIndicatorView
import quest.devinthebm.launcher.ui.Row
import com.theonionsarewatching.yapchik.Softkeys
import quest.devinthebm.launcher.ui.SquircleDrawable
import quest.devinthebm.launcher.util.launchAppByKey
import kotlin.math.ceil
import kotlin.math.min

/**
 * The "Menu": every non-hidden app, shown as either a 3x3 icon grid or a
 * single-column list (toggled in Launcher Settings).
 *
 * The grid pages nine apps at a time: D-pad navigates within a page, row by
 * row, and pressing down off the bottom row (or up off the top row) flips to
 * the next/previous page, tracked by a column of dots to the right. The
 * configurable keypad can search, make a T9 initial jump, or launch a grid
 * position; * and # always move a page backward or forward. The list remains
 * one continuous scroll, while * and # move it by one visible screenful.
 *
 * Center/OK opens the focused app. The Options soft key (or long-press) lets
 * the user pin an app to Home, hide it, or uninstall it.
 */
class AppDrawerActivity : ThemedActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var grid: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var searchField: EditText
    private lateinit var gridAdapter: AppGridAdapter
    private lateinit var listAdapter: ListRowAdapter
    private lateinit var pageIndicator: PageIndicatorView
    private lateinit var gridFocusHighlight: View
    private val gridFocusDrawable = SquircleDrawable()
    private var gridFocusVisible = false

    private var allApps: List<AppInfo> = emptyList()
    private var currentPageItems: List<AppInfo> = emptyList()
    private var currentPage = 0
    private var listMode = false
    private var searchVisible = false
    private var searchQuery = ""
    private var gridColumns = GRID_COLUMNS
    private var pageSize = PAGE_SIZE
    // Search/IME and transient window changes can temporarily reduce the
    // RecyclerView's measured height. Do not let that resize icons or change
    // page boundaries while the user is navigating; retain the largest height
    // measured for this activity configuration.
    private var stableGridHeight = 0
    private var pendingGridFocusPosition = RecyclerView.NO_POSITION
    private var lastT9Key = KeyEvent.KEYCODE_UNKNOWN
    private var t9LetterIndex = 0
    private var lastT9PressAt = 0L

    /** The accent color applied this onCreate, so [onResume] can detect a change and [recreate]. */
    private var appliedAccentColor: LauncherPrefs.AccentColor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        val accent = prefs.getAccentColor()
        appliedAccentColor = accent
        if (launcherTheme.components == quest.devinthebm.launcher.theme.LauncherComponentSelection.KaiOS && accent.themeOverlayRes != 0) theme.applyStyle(accent.themeOverlayRes, true)
        setContentView(R.layout.activity_app_drawer)

        grid = findViewById(R.id.apps_grid)
        titleView = findViewById(R.id.title)
        searchField = findViewById(R.id.app_search)
        if (launcherTheme.components == quest.devinthebm.launcher.theme.LauncherComponentSelection.Material2) {
            searchField.setBackgroundResource(R.drawable.m2_search_background)
            val horizontal = resources.getDimensionPixelSize(R.dimen.m2_search_padding_horizontal)
            val vertical = resources.getDimensionPixelSize(R.dimen.m2_search_padding_vertical)
            searchField.setPadding(horizontal, vertical, horizontal, vertical)
            searchField.minHeight = resources.getDimensionPixelSize(R.dimen.m2_search_min_height)
            searchField.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.m2_on_surface))
            searchField.setHintTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.m2_secondary_text))
        }
        pageIndicator = findViewById(R.id.page_indicator)
        gridFocusHighlight = findViewById<View>(R.id.grid_focus_highlight).apply {
            background = when (launcherTheme.components) {
                quest.devinthebm.launcher.theme.LauncherComponentSelection.KaiOS -> gridFocusDrawable
                quest.devinthebm.launcher.theme.LauncherComponentSelection.Material2 -> getDrawable(R.drawable.m2_grid_focus)
            }
            isSelected = true
        }

        // The window shows the wallpaper through a translucent overlay (see
        // Theme.QuestLauncher.Drawer); these two would otherwise paint over it
        // with their own solid bar, breaking the edge-to-edge KaiOS look.
        titleView.setBackgroundColor(Color.TRANSPARENT)

        // RecyclerView defaults to focusable so it has somewhere to park focus
        // when it's empty. We always have items and manage focus ourselves, and
        // leaving this on means the view itself can get "rescued" into holding
        // focus for a frame whenever a page swap detaches the previously
        // focused child - visible as the whole row/grid highlighting.
        grid.isFocusable = false
        grid.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (pendingGridFocusPosition == RecyclerView.NO_POSITION ||
                    grid.getChildAdapterPosition(view) != pendingGridFocusPosition) return
                pendingGridFocusPosition = RecyclerView.NO_POSITION
                view.requestFocus()
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })

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
        searchField.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty()
            applySearch()
        }
        grid.itemAnimator = null
        grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val oldPageSize = pageSize
            updatePageSize()
            if (oldPageSize != pageSize) refresh()
        }

        Softkeys.of(this).set {
            left("Search") { toggleSearch(showKeyboard = true) }
            right(getString(R.string.softkey_options)) { optionsForFocused() }
        }
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
        stableGridHeight = maxOf(stableGridHeight, grid.height)
        val gridHeight = stableGridHeight
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
                applySearch()
                if (searchQuery.isNotEmpty()) return@runOnUiThread
                if (listMode) {
                    bindList()
                } else {
                    val maxPage = (totalPages() - 1).coerceAtLeast(0)
                    bindPage(currentPage.coerceIn(0, maxPage))
                }
            }
        }.start()
    }

    private fun toggleSearch(showKeyboard: Boolean = true, initialQuery: String? = null) {
        searchVisible = !searchVisible
        searchField.visibility = if (searchVisible) View.VISIBLE else View.GONE
        if (searchVisible) {
            Softkeys.of(this).set { left("Close search") { toggleSearch() }; right(getString(R.string.softkey_options)) { optionsForFocused() } }
            if (initialQuery != null) searchField.setText(initialQuery)
            searchField.showSoftInputOnFocus = showKeyboard
            searchField.requestFocus()
            if (!showKeyboard) {
                searchField.setSelection(searchField.length())
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchField.windowToken, 0)
            }
        } else {
            searchField.showSoftInputOnFocus = true
            searchQuery = ""
            searchField.setText("")
            Softkeys.of(this).set { left("Search") { toggleSearch() }; right(getString(R.string.softkey_options)) { optionsForFocused() } }
            grid.requestFocus()
            applySearch()
        }
    }

    private fun applySearch() {
        val source = matchingApps()
        currentPage = 0
        currentPageItems = source
        if (listMode) {
            listAdapter.submit(source.map { Row(title = it.label, icon = it.icon, keepWhiteTitle = true, badgeColor = if (hasNotification(it)) NotificationDotColor.forIcon(it.key, it.icon) else null) })
            pageIndicator.visibility = View.GONE
            if (source.isNotEmpty() && !searchField.hasFocus()) grid.post { focusItemAt(0) }
        } else {
            bindPage(0)
        }
    }

    private fun matchingApps(): List<AppInfo> {
        val query = searchQuery.trim()
        return if (query.isEmpty()) allApps else allApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    private fun hasNotification(app: AppInfo): Boolean =
        prefs.isIconNotificationDotEnabled() && NotificationCounts.packagesWithNotifications.contains(app.packageName)

    private fun totalPages(): Int =
        if (matchingApps().isEmpty()) 1 else ceil(matchingApps().size / pageSize.toDouble()).toInt()

    /** Bind the full app list in one go - no pages, no dots, just a normal scroll. */
    private fun bindList(focusPosition: Int? = null) {
        currentPageItems = matchingApps()
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
            if (searchField.hasFocus()) return@post
            if (currentPageItems.isEmpty()) return@post
            focusItemAt(focusPosition?.coerceIn(0, currentPageItems.size - 1) ?: 0)
        }
    }

    /** Swap the grid's contents to [page] and re-sync the dot indicator. */
    private fun bindPage(page: Int, focusPosition: Int? = null) {
        hideGridFocusHighlight()
        currentPage = page
        val apps = matchingApps()
        val start = page * pageSize
        val end = min(start + pageSize, apps.size)
        currentPageItems = if (start < end) apps.subList(start, end) else emptyList()
        gridAdapter.submit(currentPageItems)

        val pages = totalPages()
        pageIndicator.visibility = if (pages > 1) View.VISIBLE else View.GONE
        pageIndicator.setPageCount(pages)
        pageIndicator.setCurrentPage(page)

        grid.post {
            if (searchField.hasFocus()) return@post
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
            pendingGridFocusPosition = RecyclerView.NO_POSITION
            it.requestFocus()
            return
        }
        pendingGridFocusPosition = target
        grid.scrollToPosition(target)
    }

    /** Moves one focus surface beneath the grid, instead of snapping a new
     * background into each individual cell. */
    private fun showGridFocusHighlight(app: AppInfo, itemView: View) {
        if (listMode) return
        itemView.post {
            if (isDestroyed || grid.focusedChild !== itemView) return@post
            val iconFrame = itemView.findViewById<View>(R.id.icon_frame)
            val parent = gridFocusHighlight.parent as? FrameLayout ?: return@post
            // Work in window coordinates rather than walking the RecyclerView's
            // transient child hierarchy. During quick D-pad moves its child
            // layout can be between passes, which made the moving surface use
            // a stale, undersized rect or land offset from the icon.
            val iconLocation = IntArray(2)
            val parentLocation = IntArray(2)
            iconFrame.getLocationInWindow(iconLocation)
            parent.getLocationInWindow(parentLocation)
            val left = iconLocation[0] - parentLocation[0]
            val top = iconLocation[1] - parentLocation[1]
            if (launcherTheme.components == quest.devinthebm.launcher.theme.LauncherComponentSelection.KaiOS) {
                gridFocusDrawable.setColor(NotificationDotColor.forIcon(app.key, app.icon))
            }
            val params = gridFocusHighlight.layoutParams as FrameLayout.LayoutParams
            val sizeChanged = params.width != iconFrame.width || params.height != iconFrame.height
            params.width = iconFrame.width
            params.height = iconFrame.height
            gridFocusHighlight.layoutParams = params
            gridFocusHighlight.animate().cancel()
            if (!gridFocusVisible || sizeChanged) {
                gridFocusHighlight.x = left.toFloat()
                gridFocusHighlight.y = top.toFloat()
                gridFocusHighlight.visibility = View.VISIBLE
                gridFocusVisible = true
            } else {
                gridFocusHighlight.animate()
                    .x(left.toFloat())
                    .y(top.toFloat())
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
        val count = min(pageSize, matchingApps().size - page * pageSize).coerceAtLeast(1)
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
                focusItemAt(target)
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
                val prevCount = min(pageSize, matchingApps().size - prev * pageSize)
                bindPage(prev, focusPosition = prevCount - 1)
            }
            target >= itemCount -> {
                if (currentPage + 1 < totalPages()) bindPage(currentPage + 1, focusPosition = 0)
            }
            else -> focusItemAt(target)
        }
    }

    /** Moves focus one row up/down ([delta] = -1/+1) in the (unpaged) list view. */
    private fun moveFocusLinear(delta: Int) {
        val itemCount = currentPageItems.size
        if (itemCount == 0) return
        val target = (focusedPosition() + delta).coerceIn(0, itemCount - 1)
        focusItemAt(target)
    }

    /** Previous/next page for keypad * and #. Lists scroll by the visible page size. */
    private fun movePageBy(delta: Int) {
        if (listMode) {
            val target = (focusedPosition() + delta * pageSize).coerceIn(0, (currentPageItems.size - 1).coerceAtLeast(0))
            focusItemAt(target)
        } else {
            goToPage(currentPage + delta, landOnLastRow = delta < 0)
        }
    }

    private fun openKeypadSearch(initialQuery: String) {
        if (!searchVisible) {
            toggleSearch(showKeyboard = false, initialQuery = initialQuery)
        } else {
            searchField.append(initialQuery)
        }
    }

    /** Focus the first alphabetically sorted visible app whose label begins with [letter]. */
    private fun jumpToAppInitial(letter: Char) {
        val index = allApps.indexOfFirst { it.label.startsWith(letter.toString(), ignoreCase = true) }
        if (index < 0) return
        if (listMode) {
            bindList(index)
        } else {
            bindPage(index / pageSize, index % pageSize)
        }
    }

    private fun handleT9Jump(keyCode: Int, eventTime: Long): Boolean {
        val letters = T9_LETTERS[keyCode] ?: return false
        t9LetterIndex = if (keyCode == lastT9Key && eventTime - lastT9PressAt <= T9_MULTITAP_TIMEOUT_MS) {
            (t9LetterIndex + 1) % letters.length
        } else {
            0
        }
        lastT9Key = keyCode
        lastT9PressAt = eventTime
        jumpToAppInitial(letters[t9LetterIndex])
        return true
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
        if (searchVisible) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                if (event.repeatCount > 0) return true
                when (prefs.getDrawerKeypadAction()) {
                    LauncherPrefs.DrawerKeypadAction.SEARCH ->
                        openKeypadSearch(('0'.code + keyCode - KeyEvent.KEYCODE_0).toChar().toString())
                    LauncherPrefs.DrawerKeypadAction.T9_JUMP -> handleT9Jump(keyCode, event.eventTime)
                    LauncherPrefs.DrawerKeypadAction.GRID_POSITION -> {
                        // Only the grid has a fixed nine-per-page shape for this shortcut.
                        if (!listMode) currentPageItems.getOrNull(keyCode - KeyEvent.KEYCODE_1)?.let { launchAppByKey(it.key) }
                    }
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
        }
        val typed = event.unicodeChar.toChar()
        if (typed.isLetterOrDigit()) {
            when (prefs.getDrawerKeypadAction()) {
                LauncherPrefs.DrawerKeypadAction.SEARCH -> {
                    openKeypadSearch(typed.toString())
                    return true
                }
                LauncherPrefs.DrawerKeypadAction.T9_JUMP -> {
                    if (typed.isLetter()) jumpToAppInitial(typed)
                    return true
                }
                LauncherPrefs.DrawerKeypadAction.GRID_POSITION -> Unit
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_STAR || event.keyCode == KeyEvent.KEYCODE_POUND)) {
            movePageBy(if (event.keyCode == KeyEvent.KEYCODE_STAR) -1 else 1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val GRID_COLUMNS = 3
        private const val PAGE_SIZE = 9
        private const val GRID_HIGHLIGHT_SLIDE_MS = 150L
        private const val T9_MULTITAP_TIMEOUT_MS = 1_000L
        private val T9_LETTERS = mapOf(
            KeyEvent.KEYCODE_2 to "abc",
            KeyEvent.KEYCODE_3 to "def",
            KeyEvent.KEYCODE_4 to "ghi",
            KeyEvent.KEYCODE_5 to "jkl",
            KeyEvent.KEYCODE_6 to "mno",
            KeyEvent.KEYCODE_7 to "pqrs",
            KeyEvent.KEYCODE_8 to "tuv",
            KeyEvent.KEYCODE_9 to "wxyz",
        )
    }
}
