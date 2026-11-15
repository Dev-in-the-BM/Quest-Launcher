package quest.devinthebm.launcher

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import quest.devinthebm.launcher.theme.ThemedActivity
import quest.devinthebm.launcher.theme.ThemeDescriptor
import quest.devinthebm.launcher.theme.LauncherComponentSelection
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theonionsarewatching.yapchik.SoftkeyBar
import com.theonionsarewatching.yapchik.SoftkeyController
import com.theonionsarewatching.yapchik.Softkeys
import com.theonionsarewatching.yapchik.Yapchik
import quest.devinthebm.launcher.data.AppRepository
import quest.devinthebm.launcher.data.LauncherPrefs
import quest.devinthebm.launcher.data.NotificationCounts
import quest.devinthebm.launcher.ui.HomeRailAdapter
import quest.devinthebm.launcher.ui.RailItem
import quest.devinthebm.launcher.ui.SpotlightDrawable
import quest.devinthebm.launcher.util.launchAppByKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The KaiOS-style home screen:
 *  - a vertical rail of shortcut icons on the left (keys 1-9, top to bottom),
 *  - a large clock + date on the right, and
 *  - "Notifications · apps · Contacts" soft keys along the bottom.
 *
 * Tap an icon (or press its number key, or D-pad to it and press OK) to launch.
 * The center dots open All Apps; long-pressing them opens launcher Options.
 */
class MainActivity : ThemedActivity() {

    override fun activityThemeStyle(descriptor: ThemeDescriptor): Int =
        when (descriptor.components) {
            LauncherComponentSelection.KaiOS -> R.style.Theme_QuestLauncher_Home
            LauncherComponentSelection.Material2 -> R.style.Theme_QuestLauncher_Home_Material2
            LauncherComponentSelection.Aeris -> R.style.Theme_QuestLauncher_Home_Aeris
        }

    private lateinit var prefs: LauncherPrefs
    private lateinit var rail: RecyclerView
    private lateinit var appMenuButton: ImageView
    private lateinit var softKeys: SoftkeyController
    private lateinit var adapter: HomeRailAdapter
    private var railExpanded = false
    private var activeRailKey: LauncherPrefs.HomeKey? = null
    private var railHorizontal = false
    private var visibleRailSlots = HomeRailAdapter.SLOTS_VISIBLE
    private var focusedRailIndex = 0
    private var displayedRailHighlightIndex = RecyclerView.NO_POSITION
    private var wheelAnimator: ValueAnimator? = null
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeEligible = false
    private var swipeCaptured = false
    private lateinit var clock: TextView
    private lateinit var ampm: TextView
    private lateinit var weekday: TextView
    private lateinit var dateLine: TextView
    private lateinit var notifCallsGroup: View
    private lateinit var notifCallsCount: TextView
    private lateinit var notifMessagesGroup: View
    private lateinit var notifMessagesCount: TextView
    private lateinit var notifOtherGroup: View
    private lateinit var notifOtherCount: TextView

    private val ampmFmt = SimpleDateFormat("a", Locale.getDefault())
    private val time12 = SimpleDateFormat("h:mm", Locale.getDefault())
    private val time24 = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekdayFmt = SimpleDateFormat("EEEE", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    /** Rail index awaiting an app from the picker (-1 = none). */
    private var pendingIndex = -1

    /** Set once a Back long-press has fired, so the matching key-up doesn't also open the drawer. */
    private var backLongPressHandled = false

    /** The accent color applied this onCreate, so [onResume] can detect a change and [recreate]. */
    private var appliedAccentColor: LauncherPrefs.AccentColor? = null

    private val pickLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && pendingIndex >= 0) {
            result.data?.getStringExtra(AppPickerActivity.EXTRA_APP_KEY)?.let { key ->
                prefs.setShortcutAt(pendingIndex, key)
            }
        }
        pendingIndex = -1
        refreshShortcuts()
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateClock()
    }

    private val notifListener: () -> Unit = {
        runOnUiThread {
            updateNotifSummary()
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        val accent = prefs.getAccentColor()
        appliedAccentColor = accent
        if (launcherTheme.components == quest.devinthebm.launcher.theme.LauncherComponentSelection.KaiOS && accent.themeOverlayRes != 0) theme.applyStyle(accent.themeOverlayRes, true)
        setContentView(R.layout.activity_main)

        clock = findViewById(R.id.clock)
        ampm = findViewById(R.id.ampm)
        weekday = findViewById(R.id.weekday)
        dateLine = findViewById(R.id.date_line)
        notifCallsGroup = findViewById(R.id.notif_calls_group)
        notifCallsCount = findViewById(R.id.notif_calls_count)
        notifMessagesGroup = findViewById(R.id.notif_messages_group)
        notifMessagesCount = findViewById(R.id.notif_messages_count)
        notifOtherGroup = findViewById(R.id.notif_other_group)
        notifOtherCount = findViewById(R.id.notif_other_count)
        rail = findViewById(R.id.shortcuts_rail)
        updateConfiguredRail()

        adapter = HomeRailAdapter(
            onClick = { _, item -> onRailClick(item) },
            onLongClick = { position, item -> onRailLongClick(position, item) },
            hasNotification = { app ->
                prefs.isIconNotificationDotEnabled() && NotificationCounts.packagesWithNotifications.contains(app.packageName)
            },
        )
        rail.layoutManager = LinearLayoutManager(this)
        rail.adapter = adapter
        rail.itemAnimator = null
        rail.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val extent = if (railHorizontal) view.width else view.height
            val density = resources.displayMetrics.density
            val edgeSpace = (24 * density).toInt()
            val available = (extent - edgeSpace).coerceAtLeast(1)
            // Keep the existing 54dp icon dimensions. On short screens show
            // fewer slots rather than squeezing five icons into overlapping rows.
            visibleRailSlots = (available / (MIN_RAIL_SLOT_DP * density).toInt())
                .coerceIn(1, HomeRailAdapter.SLOTS_VISIBLE)
            val slot = maxOf(
                available / HomeRailAdapter.SLOTS_VISIBLE,
                (MIN_RAIL_SLOT_DP * density).toInt(),
            )
            if (slot > 0 && adapter.itemHeightPx != slot) {
                val edge = (extent - slot) / 2
                if (railHorizontal) view.setPadding(edge, 0, edge, 0) else view.setPadding(0, edge, 0, edge)
                adapter.setItemHeightPx(slot)
                view.post {
                    centerRailItem(railCenterPosition())
                    if (!railExpanded) focusAppMenu()
                }
            }
        }
        rail.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateWheelCurve()
        })

        // Yapchik owns the left and right softkey labels; the app grid is the
        // middle child of their row, not a separate overlay over the bar.
        appMenuButton = ImageView(this).apply {
            id = View.generateViewId()
            tag = APP_GRID_BUTTON_TAG
            background = SpotlightDrawable()
            setImageResource(R.drawable.ic_apps_dots)
            setOnClickListener { openAppDrawer() }
            setOnLongClickListener { openOptions(); true }
            contentDescription = getString(R.string.content_apps)
            isClickable = true
            isFocusable = true
            // Yapchik's bar is attached after the activity content. On a
            // touchscreen/keypad hybrid the window can still be in touch mode
            // at that point; this lets Home explicitly restore grid focus.
            isFocusableInTouchMode = true
            // Match the original XML button: Android's rectangular focus
            // highlight would cover the softer SpotlightDrawable glow.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                defaultFocusHighlightEnabled = false
            }
            scaleType = ImageView.ScaleType.CENTER
            setPadding(
                (18 * resources.displayMetrics.density).toInt(),
                0,
                (18 * resources.displayMetrics.density).toInt(),
                0,
            )
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setRailExpanded(false) }
        }
        softKeys = Softkeys.of(this).apply {
            set {
                left(label = { keyLabel(LauncherPrefs.HomeKey.SOFT_LEFT) }) { performHomeKey(LauncherPrefs.HomeKey.SOFT_LEFT) }
                right(label = { keyLabel(LauncherPrefs.HomeKey.SOFT_RIGHT) }) { performHomeKey(LauncherPrefs.HomeKey.SOFT_RIGHT) }
            }
        }
        styleHomeSoftkeyLabels()
        addAppMenuButtonToSoftkeyBar()

        // Back opens the app drawer; long-pressing it launches the configured app instead
        // (see onKeyDown/onKeyUp, which suppress this callback when a long-press fires).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = openAppDrawer()
        })

        updateClock()
    }

    private fun setRailExpanded(expanded: Boolean) {
        if (railExpanded == expanded) return
        railExpanded = expanded
        if (!expanded) clearRailHighlight()
        val density = resources.displayMetrics.density
        rail.animate()
            .translationX(if (!railHorizontal && !expanded) (if (activeRailKey == LauncherPrefs.HomeKey.RIGHT) CLOSED_RAIL_OFFSET_DP else -CLOSED_RAIL_OFFSET_DP) * density else 0f)
            .translationY(if (railHorizontal && !expanded) CLOSED_RAIL_OFFSET_DP * density else 0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
        findViewById<View>(R.id.home_clock_group).let { clockGroup ->
            val params = clockGroup.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.marginStart = if (!railHorizontal && expanded && activeRailKey == LauncherPrefs.HomeKey.LEFT) (8 * density).toInt() else (if (activeRailKey == LauncherPrefs.HomeKey.LEFT) 8 * density else 0f).toInt()
            params.marginEnd = if (!railHorizontal && expanded && activeRailKey == LauncherPrefs.HomeKey.RIGHT) (8 * density).toInt() else (if (activeRailKey == LauncherPrefs.HomeKey.RIGHT) 18 * density else 0f).toInt()
            clockGroup.layoutParams = params
        }
        updateWheelCurve()
    }

    /** Place the grid action between Yapchik's equally weighted left/right labels. */
    private fun addAppMenuButtonToSoftkeyBar() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        val bar = (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<SoftkeyBar>()
            .firstOrNull() ?: return
        for (i in 0 until content.childCount) {
            val otherBar = content.getChildAt(i) as? SoftkeyBar ?: continue
            val otherRow = otherBar.getChildAt(1) as? ViewGroup ?: continue
            for (childIndex in otherRow.childCount - 1 downTo 0) {
                val child = otherRow.getChildAt(childIndex)
                if (child !== appMenuButton && child.tag == APP_GRID_BUTTON_TAG) otherRow.removeViewAt(childIndex)
            }
        }
        val labelRow = bar.getChildAt(1) as? LinearLayout ?: return
        // Yapchik's labels represent hardware softkeys, not D-pad focus stops.
        // Keep the inserted center action focusable, but remove the bar and its
        // label widgets from Android's ordinary focus traversal.
        bar.isFocusable = false
        bar.isFocusableInTouchMode = false
        labelRow.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        fun disableFocus(view: View) {
            if (view === appMenuButton) return
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) disableFocus(view.getChildAt(index))
            }
        }
        disableFocus(labelRow)
        // The spotlight draws well beyond the button and above the bar.
        // Yapchik clips its children by default, so let the light bloom out.
        bar.clipChildren = false
        bar.clipToPadding = false
        labelRow.clipChildren = false
        labelRow.clipToPadding = false
        if (appMenuButton.parent === labelRow) {
            bar.doOnLayout { fitRailAboveSoftkeyBar(bar) }
            return
        }
        (appMenuButton.parent as? ViewGroup)?.removeView(appMenuButton)
        labelRow.addView(
            appMenuButton, 1,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        bar.doOnLayout { fitRailAboveSoftkeyBar(bar) }
    }

    private fun fitRailAboveSoftkeyBar(bar: SoftkeyBar) {
        if (!::rail.isInitialized || bar.height <= 0) return
        val params = rail.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ?: return
        if (params.bottomMargin != bar.height) {
            params.bottomMargin = bar.height
            rail.layoutParams = params
        }
    }

    private fun findSoftkeyBar(): SoftkeyBar? {
        val content = findViewById<ViewGroup>(android.R.id.content)
        return (0 until content.childCount).map { content.getChildAt(it) }
            .filterIsInstance<SoftkeyBar>().firstOrNull()
    }

    private fun styleHomeSoftkeyLabels() {
        Yapchik.style.textColor = android.graphics.Color.WHITE
        val content = findViewById<ViewGroup>(android.R.id.content)
        val bar = (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<SoftkeyBar>()
            .firstOrNull() ?: return
        bar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val labelRow = bar.getChildAt(1) as? ViewGroup ?: return
        fun styleLabels(view: View) {
            if (view is TextView) {
                view.setTextColor(android.graphics.Color.WHITE)
                view.setShadowLayer(
                    3f * resources.displayMetrics.density,
                    0f,
                    1f,
                    0xCC000000.toInt(),
                )
            } else if (view is ViewGroup) {
                for (index in 0 until view.childCount) styleLabels(view.getChildAt(index))
            }
        }
        styleLabels(labelRow)
        bar.post {
            (bar.getChildAt(1) as? ViewGroup)?.let(::styleLabels)
        }
    }

    /** Detect rail swipes before allowing an icon to interpret the same gesture as a tap. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::rail.isInitialized && rail.visibility == View.VISIBLE) {
            val density = resources.displayMetrics.density
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    val location = IntArray(2)
                    rail.getLocationOnScreen(location)
                    val edge = when {
                        railHorizontal -> event.rawY >= location[1] - (if (railExpanded) 120 else 72) * density
                        activeRailKey == LauncherPrefs.HomeKey.RIGHT -> event.rawX >= rail.width + location[0] - (if (railExpanded) 120 else 72) * density
                        else -> event.rawX <= location[0] + (if (railExpanded) 120 else 72) * density
                    }
                    swipeEligible = edge &&
                        event.rawX >= location[0] - (if (railExpanded) 140 else 60) * density &&
                        event.rawX <= location[0] + rail.width + (if (railExpanded) 140 else 60) * density &&
                        event.rawY >= location[1] - (if (railExpanded) 140 else 60) * density &&
                        event.rawY <= location[1] + rail.height + (if (railExpanded) 140 else 60) * density
                    swipeCaptured = false
                }
                MotionEvent.ACTION_MOVE -> if (swipeEligible && !swipeCaptured) {
                    val dx = event.rawX - swipeStartX
                    val dy = event.rawY - swipeStartY
                    val delta = if (railHorizontal) dy else dx
                    val horizontalAxis = !railHorizontal
                    if (kotlin.math.abs(delta) > 6f * density &&
                        (if (horizontalAxis) kotlin.math.abs(dx) > kotlin.math.abs(dy) else kotlin.math.abs(dy) > kotlin.math.abs(dx))) {
                        MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(this)
                            recycle()
                        }
                        swipeCaptured = true
                        val opensToward = if (railHorizontal || activeRailKey == LauncherPrefs.HomeKey.RIGHT) -1f else 1f
                        if (!railExpanded && delta * opensToward > 0) {
                            if (!railHorizontal) activeRailKey = if (activeRailKey == LauncherPrefs.HomeKey.RIGHT) LauncherPrefs.HomeKey.RIGHT else LauncherPrefs.HomeKey.LEFT
                            updateConfiguredRail(activeRailKey)
                            openWheel()
                        } else if (railExpanded && delta * opensToward < 0) {
                            setRailExpanded(false)
                            appMenuButton.requestFocus()
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (swipeCaptured) {
                        swipeCaptured = false
                        swipeEligible = false
                        return true
                    }
                    swipeEligible = false
                }
            }
            if (swipeCaptured) return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun closeRailAndFocusAppMenu() {
        setRailExpanded(false)
        appMenuButton.requestFocus()
    }

    /** Opens the carousel with its third (visually middle) shortcut selected. */
    private fun openWheel() {
        setRailExpanded(true)
        if (adapter.itemCount == 0) return
        focusedRailIndex = minOf(visibleRailSlots / 2, adapter.itemCount - 1)
        wheelAnimator?.cancel()
        centerRailItem(focusedRailIndex)
        setRailHighlight(focusedRailIndex, animate = false)
        rail.requestFocus()
    }

    /** Used only after layout/data changes, never during a wheel turn. */
    private fun centerRailItem(position: Int) {
        if (adapter.itemCount == 0 || adapter.itemHeightPx <= 0) return
        (rail.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
            position.coerceIn(0, adapter.itemCount - 1), 0,
        )
        rail.post { updateWheelCurve() }
    }

    /** The closed rail is a five-icon peek, not a list parked at item zero. */
    private fun railCenterPosition(): Int = if (railExpanded) {
        focusedRailIndex
    } else {
        minOf(visibleRailSlots / 2, (adapter.itemCount - 1).coerceAtLeast(0))
    }

    private fun updateWheelCurve() {
        val slot = adapter.itemHeightPx
        if (slot <= 0) return
        val density = resources.displayMetrics.density
        val center = (if (railHorizontal) rail.width else rail.height) / 2f
        // Reuse the original crescent track from before the wheel animation:
        // center +9dp, outer -5dp when closed (a slightly flatter crescent);
        // center +16dp, adjacent +12dp, outer 0dp when open. Derive it from
        // each row's visible position so it follows the wheel as it scrolls.
        for (i in 0 until rail.childCount) {
            val row = rail.getChildAt(i)
            val position = if (railHorizontal) (row.left + row.right) / 2f else (row.top + row.bottom) / 2f
            val relativeY = position - center
            val distance = kotlin.math.abs(relativeY) / slot
            val halfSpan = (distance / 2f).coerceIn(0f, 1f)
            val curve = 1f - halfSpan * halfSpan
            val circle = row.findViewById<View>(R.id.circle)
            val curveOffset = (if (railExpanded) 16f * curve else -5f + 14f * curve) * density
            if (railHorizontal) circle.translationY = -curveOffset
            else circle.translationX = curveOffset * if (activeRailKey == LauncherPrefs.HomeKey.RIGHT) -1f else 1f
            // Keep every shortcut at the same visual size and opacity. The
            // crescent translation provides the wheel depth without making
            // off-center apps look smaller or faded.
            circle.scaleX = 1f
            circle.scaleY = 1f
            circle.alpha = 1f
            row.findViewById<View>(R.id.selection_highlight).apply {
                // A multiplier, not a fixed size: it remains 18% larger than
                // whichever icon shape/size is rendered in this slot.
                scaleX = 1.18f
                scaleY = 1.18f
                // A newly attached target (after a scroll) needs the current
                // selection state, but normal focus moves are handled by
                // setRailHighlight so their old/new halos can cross-fade.
                if (railExpanded && rail.getChildAdapterPosition(row) == displayedRailHighlightIndex && !isSelected) {
                    alpha = 1f
                    isSelected = true
                }
            }
        }
    }

    private fun setRailHighlight(index: Int, animate: Boolean) {
        val previous = displayedRailHighlightIndex
        displayedRailHighlightIndex = index
        if (!animate || previous == RecyclerView.NO_POSITION || previous == index) {
            for (i in 0 until rail.childCount) {
                val row = rail.getChildAt(i)
                row.findViewById<View>(R.id.selection_highlight).apply {
                    animate().cancel()
                    alpha = 1f
                    isSelected = rail.getChildAdapterPosition(row) == index
                }
            }
            return
        }

        rail.layoutManager?.findViewByPosition(previous)
            ?.findViewById<View>(R.id.selection_highlight)
            ?.apply {
                animate().cancel()
                isSelected = true
                alpha = 1f
                animate().alpha(0f).setDuration(RAIL_HIGHLIGHT_FADE_MS).withEndAction {
                    if (displayedRailHighlightIndex != previous) {
                        isSelected = false
                        alpha = 1f
                    }
                }.start()
            }
        rail.layoutManager?.findViewByPosition(index)
            ?.findViewById<View>(R.id.selection_highlight)
            ?.apply {
                animate().cancel()
                isSelected = true
                alpha = 0f
                animate().alpha(1f).setDuration(RAIL_HIGHLIGHT_FADE_MS).start()
            }
    }

    private fun clearRailHighlight() {
        displayedRailHighlightIndex = RecyclerView.NO_POSITION
        for (i in 0 until rail.childCount) {
            rail.getChildAt(i).findViewById<View>(R.id.selection_highlight).apply {
                animate().cancel()
                alpha = 1f
                isSelected = false
            }
        }
    }

    private fun moveRailFocus(direction: Int): Boolean {
        if (adapter.itemCount == 0) return true
        val target = (focusedRailIndex + direction).coerceIn(0, adapter.itemCount - 1)
        if (target == focusedRailIndex) return true
        focusedRailIndex = target
        // Start the cross-fade before the wheel's first animation frame.
        setRailHighlight(target, animate = true)
        val layout = rail.layoutManager as? LinearLayoutManager ?: return true
        val anchorIndex = layout.findFirstVisibleItemPosition()
        val anchor = layout.findViewByPosition(anchorIndex) ?: return true
        val extent = if (railHorizontal) rail.width else rail.height
        val anchorStart = if (railHorizontal) anchor.left else anchor.top
        val centerStart = (extent - adapter.itemHeightPx) / 2
        val distance = anchorStart - centerStart + (target - anchorIndex) * adapter.itemHeightPx
        wheelAnimator?.cancel()
        var previous = 0
        wheelAnimator = ValueAnimator.ofInt(0, distance).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                if (railHorizontal) rail.scrollBy(value - previous, 0) else rail.scrollBy(0, value - previous)
                previous = value
            }
            start()
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && appMenuButton.hasFocus()) {
            val key = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> LauncherPrefs.HomeKey.UP
                KeyEvent.KEYCODE_DPAD_LEFT -> LauncherPrefs.HomeKey.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> LauncherPrefs.HomeKey.RIGHT
                KeyEvent.KEYCODE_DPAD_DOWN -> LauncherPrefs.HomeKey.DOWN
                else -> null
            }
            if (key != null) { performHomeKey(key); return true }
        }
        if (::rail.isInitialized && railExpanded && rail.hasFocus()) {
            val key = event.keyCode
            val mapped = when (key) {
                KeyEvent.KEYCODE_DPAD_UP -> LauncherPrefs.HomeKey.UP
                KeyEvent.KEYCODE_DPAD_LEFT -> LauncherPrefs.HomeKey.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> LauncherPrefs.HomeKey.RIGHT
                KeyEvent.KEYCODE_DPAD_DOWN -> LauncherPrefs.HomeKey.DOWN
                else -> null
            }
            if (event.action == KeyEvent.ACTION_DOWN && mapped == activeRailKey) {
                performHomeKey(mapped!!)
                return true
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
                key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (event.action == KeyEvent.ACTION_DOWN) when (key) {
                    KeyEvent.KEYCODE_DPAD_UP -> { if (!railHorizontal) moveRailFocus(-1) }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { if (!railHorizontal) moveRailFocus(1) }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                        (rail.layoutManager as? LinearLayoutManager)
                            ?.findViewByPosition(focusedRailIndex)?.performClick()
                    KeyEvent.KEYCODE_DPAD_LEFT -> { if (railHorizontal) moveRailFocus(-1) }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { if (railHorizontal) moveRailFocus(1) }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            timeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
        )
        updateClock()
        updateConfiguredRail()
        refreshShortcuts()
        softKeys.invalidate()
        findViewById<ViewGroup>(android.R.id.content).post {
            addAppMenuButtonToSoftkeyBar()
            findSoftkeyBar()?.let(::fitRailAboveSoftkeyBar)
            focusAppMenu()
        }
        resetHomeFocus()
        NotificationCounts.addListener(notifListener)
        updateNotifSummary()
        maybePromptDefaultLauncher()
        // The accent color may have changed in Settings while Home was backgrounded;
        // theme overlays only apply at onCreate, so recreate to pick it up. Done last
        // (after registering the receiver/listener above) so onPause's matching
        // unregister calls below still have something to unregister.
        if (prefs.getAccentColor() != appliedAccentColor) recreate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A HOME intent can bring this existing singleTask activity forward from
        // another app. Always return to the home state rather than preserving a
        // previously opened shortcut wheel.
        resetHomeFocus()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(timeReceiver)
        NotificationCounts.removeListener(notifListener)
    }

    // ----------------------------------------------------------- Data / clock

    private fun updateClock() {
        val now = Date()
        if (android.text.format.DateFormat.is24HourFormat(this)) {
            ampm.visibility = TextView.GONE
            clock.text = time24.format(now)
        } else {
            ampm.visibility = TextView.VISIBLE
            ampm.text = ampmFmt.format(now)
            clock.text = time12.format(now)
        }
        weekday.text = weekdayFmt.format(now)
        dateLine.text = dateFmt.format(now)
    }

    private fun refreshShortcuts() {
        Thread {
            // Resolve each shortcut (any activity component), dropping ones whose
            // app was uninstalled so the rail stays gap-free.
            val keys = prefs.getShortcuts()
            val resolved = keys.mapNotNull { key ->
                AppRepository.resolveComponent(this, key)?.let { key to it }
            }
            if (resolved.size != keys.size) prefs.setShortcuts(resolved.map { it.first })

            val entries = resolved.mapTo(ArrayList()) { RailItem(it.second) }
            if (resolved.size < LauncherPrefs.MAX_SHORTCUTS) entries.add(RailItem(null))

            if (isDestroyed) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                wheelAnimator?.cancel()
                adapter.submit(entries)
                if (railExpanded && adapter.itemCount > 0) {
                    focusedRailIndex = minOf(visibleRailSlots / 2, adapter.itemCount - 1)
                }
                rail.post {
                    centerRailItem(railCenterPosition())
                    if (railExpanded) setRailHighlight(focusedRailIndex, animate = false)
                    else focusAppMenu()
                }
            }
        }.start()
    }

    // --------------------------------------------------------------- Actions

    private fun onRailClick(item: RailItem) {
        val app = item.app
        if (app != null) launchAppByKey(app.key) else pickForIndex(prefs.getShortcuts().size)
    }

    private fun onRailLongClick(position: Int, item: RailItem) {
        if (item.app == null) {
            pickForIndex(prefs.getShortcuts().size)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(item.app.label)
            .setItems(arrayOf("Change app", "Remove from Home")) { _, which ->
                when (which) {
                    0 -> pickForIndex(position)
                    1 -> {
                        prefs.removeShortcutAt(position)
                        refreshShortcuts()
                    }
                }
            }
            .show()
    }

    /** Open the phone dialer, prefilled with the pressed digit (or * / #). */
    private fun startDial(digit: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", digit, null)))
        } catch (e: Exception) {
            Toast.makeText(this, "No dialer available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickForIndex(index: Int) {
        pendingIndex = index
        pickLauncher.launch(Intent(this, AppPickerActivity::class.java))
    }

    private fun openAppDrawer() = startActivity(Intent(this, AppDrawerActivity::class.java))

    private fun updateConfiguredRail(preferredKey: LauncherPrefs.HomeKey? = null) {
        if (!::rail.isInitialized) return
        val railKeys = listOf(LauncherPrefs.HomeKey.LEFT, LauncherPrefs.HomeKey.RIGHT, LauncherPrefs.HomeKey.DOWN)
            .filter { prefs.getHomeKeyAction(it) == LauncherPrefs.HomeKeyAction.RAIL }
        val railKey = preferredKey?.takeIf { it in railKeys }
            ?: activeRailKey?.takeIf { it in railKeys }
            ?: railKeys.firstOrNull()
        if (!railExpanded && railKey != null) activeRailKey = railKey
        rail.visibility = if (railKey == null) View.GONE else View.VISIBLE
        railHorizontal = railKey == LauncherPrefs.HomeKey.DOWN
        val params = rail.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.width = if (railHorizontal) ViewGroup.LayoutParams.MATCH_PARENT else (84 * resources.displayMetrics.density).toInt()
        params.height = if (railHorizontal) (84 * resources.displayMetrics.density).toInt() else 0
        params.startToStart = if (railKey == LauncherPrefs.HomeKey.LEFT || railHorizontal || railKey == null) androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID else -1
        params.endToEnd = if (railKey == LauncherPrefs.HomeKey.RIGHT || railHorizontal) androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID else -1
        params.topToTop = if (railHorizontal) -1 else androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        if (railHorizontal) {
            params.bottomMargin = findSoftkeyBar()?.height ?: params.bottomMargin
            params.topMargin = 0
        } else {
            params.bottomMargin = findSoftkeyBar()?.height ?: 0
            params.topMargin = 0
        }
        rail.layoutParams = params
        findViewById<View>(R.id.home_clock_group).let { clockGroup ->
            val clockParams = clockGroup.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            val rightSide = railKey == LauncherPrefs.HomeKey.RIGHT
            if (rightSide) {
                clockParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                clockParams.startToEnd = -1
                clockParams.endToStart = R.id.shortcuts_rail
                clockParams.endToEnd = -1
            } else {
                clockParams.startToEnd = if (railKey == LauncherPrefs.HomeKey.LEFT) R.id.shortcuts_rail else -1
                clockParams.startToStart = if (railKey == LauncherPrefs.HomeKey.LEFT) -1 else androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                clockParams.endToStart = -1
                clockParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
            clockGroup.layoutParams = clockParams
            (clockGroup as? android.widget.LinearLayout)?.gravity =
                if (rightSide) Gravity.START else Gravity.END
            listOf(R.id.ampm, R.id.clock, R.id.weekday, R.id.date_line).forEach { id ->
                findViewById<TextView>(id).gravity = if (rightSide) Gravity.START else Gravity.END
            }
            findViewById<View>(R.id.notif_row).let { row ->
                (row as? android.widget.LinearLayout)?.gravity =
                    (if (rightSide) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            }
        }
        rail.layoutManager = LinearLayoutManager(this, if (railHorizontal) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false)
        rail.translationX = if (!railHorizontal && railKey != null) (if (railKey == LauncherPrefs.HomeKey.RIGHT) CLOSED_RAIL_OFFSET_DP else -CLOSED_RAIL_OFFSET_DP) * resources.displayMetrics.density else 0f
        rail.translationY = if (railHorizontal) CLOSED_RAIL_OFFSET_DP * resources.displayMetrics.density else 0f
        if (::adapter.isInitialized) adapter.setOrientation(railHorizontal)
    }

    private fun performHomeKey(key: LauncherPrefs.HomeKey) {
        when (prefs.getHomeKeyAction(key)) {
            LauncherPrefs.HomeKeyAction.APP -> prefs.getHomeKeyApp(key)?.let(::launchAppByKey)
            LauncherPrefs.HomeKeyAction.RAIL -> {
                if (railExpanded && activeRailKey == key) {
                    closeRailAndFocusAppMenu()
                } else {
                    activeRailKey = key
                    updateConfiguredRail(key)
                    openWheel()
                }
            }
            LauncherPrefs.HomeKeyAction.FAVORITES -> startActivity(Intent(this, FavoritesActivity::class.java))
            LauncherPrefs.HomeKeyAction.NOTIFICATIONS -> openNotifications()
        }
    }

    private fun keyLabel(key: LauncherPrefs.HomeKey): String =
        if (prefs.getHomeKeyAction(key) == LauncherPrefs.HomeKeyAction.APP)
            prefs.getHomeKeyApp(key)?.let { AppRepository.resolveComponent(this, it)?.label }
                ?: getString(prefs.getHomeKeyAction(key).labelRes)
        else getString(prefs.getHomeKeyAction(key).labelRes)

    /** Return to the default Home state, with the app-menu action ready for OK. */
    private fun resetHomeFocus() {
        wheelAnimator?.cancel()
        setRailExpanded(false)
        // RecyclerView may reclaim focus during its next data/layout pass;
        // queue Home's default focus behind that pass.
        rail.post { focusAppMenu() }
    }

    private fun focusAppMenu() = appMenuButton.post { appMenuButton.requestFocus() }

    private fun openLeftKeyApp() {
        val key = prefs.getLeftKeyApp()
        if (key != null) launchAppByKey(key) else openNotifications()
    }

    private fun openOptions() = startActivity(Intent(this, OptionsActivity::class.java))

    private fun openRightKeyApp() {
        val key = prefs.getRightKeyApp()
        if (key != null) {
            launchAppByKey(key)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL))
            } catch (ignored: Exception) {
                Toast.makeText(this, "No contacts app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateNotifSummary() {
        bindNotifBadge(notifCallsGroup, notifCallsCount, prefs.isCallBadgeEnabled(), NotificationCounts.calls)
        bindNotifBadge(notifMessagesGroup, notifMessagesCount, prefs.isMessageBadgeEnabled(), NotificationCounts.messages)
        bindNotifBadge(notifOtherGroup, notifOtherCount, prefs.isOtherBadgeEnabled(), NotificationCounts.other)
    }

    private fun bindNotifBadge(group: View, countView: TextView, enabled: Boolean, count: Int) {
        if (enabled && count > 0) {
            countView.text = if (count > 99) "99+" else count.toString()
            group.visibility = View.VISIBLE
        } else {
            group.visibility = View.GONE
        }
    }

    private fun leftKeyLabel(): String {
        val key = prefs.getLeftKeyApp()
        return key?.let { AppRepository.resolveComponent(this, it)?.label }
            ?: getString(R.string.softkey_notifications)
    }

    private fun rightKeyLabel(): String {
        val key = prefs.getRightKeyApp()
        return key?.let { AppRepository.resolveComponent(this, it)?.label }
            ?: getString(R.string.softkey_contacts)
    }

    private fun openDialer() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL))
        } catch (e: Exception) {
            Toast.makeText(this, "No dialer available", Toast.LENGTH_SHORT).show()
        }
    }

    /** The KaiOS "Notices" action: our own list screen, not the system shade. */
    private fun openNotifications() = startActivity(Intent(this, NoticesActivity::class.java))

    // ----------------------------------------------------------- Key handling

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            // Start typing a number anywhere on Home -> jump into the dialer.
            // The dialer is actually launched on key-up (see onKeyUp): launching
            // it here on key-down leaves the matching key-up to be delivered to
            // the now-focused dialer, which on some devices re-enters the same
            // digit, so the first digit appears twice. Consuming the key-down
            // here keeps the gesture entirely within Home until it completes.
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_STAR,
            KeyEvent.KEYCODE_POUND -> return true
            KeyEvent.KEYCODE_MENU -> { openAppDrawer(); return true }
            KeyEvent.KEYCODE_CALL -> { openDialer(); return true }
            KeyEvent.KEYCODE_BACK -> {
                if (event.isLongPress) {
                    backLongPressHandled = true
                    launchBackLongPressApp()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            // Launch the dialer on key-up (not key-down) so the physical key
            // event is fully consumed by Home before the dialer is focused,
            // preventing the leading digit from being entered twice.
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                startDial(('0' + (keyCode - KeyEvent.KEYCODE_0)).toString())
                return true
            }
            KeyEvent.KEYCODE_STAR -> { startDial("*"); return true }
            KeyEvent.KEYCODE_POUND -> { startDial("#"); return true }
        }
        // Swallow the key-up that follows a handled long-press so the
        // OnBackPressedCallback above doesn't also open the app drawer.
        if (keyCode == KeyEvent.KEYCODE_BACK && backLongPressHandled) {
            backLongPressHandled = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun launchBackLongPressApp() {
        val key = prefs.getBackLongPressApp()
        if (key == null) {
            Toast.makeText(this, R.string.back_longpress_unset_toast, Toast.LENGTH_SHORT).show()
        } else {
            launchAppByKey(key)
        }
    }

    // ------------------------------------------------------ Default launcher

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun maybePromptDefaultLauncher() {
        // Gently hint, but never yank the user away — they opt in deliberately via
        // Options → "Set as Default Launcher".
        if (defaultPromptShown || isDefaultLauncher()) return
        defaultPromptShown = true
        Toast.makeText(this, R.string.choose_home_app, Toast.LENGTH_LONG).show()
    }

    companion object {
        // Shown at most once per process so we don't nag on every resume.
        private var defaultPromptShown = false
        const val RAIL_HIGHLIGHT_FADE_MS = 180L
        // Keep most of the 54dp icon face visible in the closed peek while
        // leaving enough rail motion to signal that it can be opened.
        private const val CLOSED_RAIL_OFFSET_DP = 34f
        private const val MIN_RAIL_SLOT_DP = 64f
        private const val APP_GRID_BUTTON_TAG = "quest_home_app_grid_button"
    }
}
