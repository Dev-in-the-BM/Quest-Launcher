package com.flipos.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flipos.launcher.data.AppRepository
import com.flipos.launcher.data.LauncherPrefs
import com.flipos.launcher.data.NotificationCounts
import com.flipos.launcher.ui.HomeRailAdapter
import com.flipos.launcher.ui.RailItem
import com.flipos.launcher.util.launchAppByKey
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
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var rail: RecyclerView
    private lateinit var appMenuButton: ImageView
    private lateinit var adapter: HomeRailAdapter
    private var railExpanded = false
    private var focusedRailIndex = 0
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
        if (accent.themeOverlayRes != 0) theme.applyStyle(accent.themeOverlayRes, true)
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

        adapter = HomeRailAdapter(
            onClick = { _, item -> onRailClick(item) },
            onLongClick = { position, item -> onRailLongClick(position, item) },
            hasNotification = { app ->
                prefs.isIconNotificationDotEnabled() && NotificationCounts.packagesWithNotifications.contains(app.packageName)
            },
            onNavigate = { position, direction -> moveRailFocus(position, direction) },
        )
        rail.layoutManager = LinearLayoutManager(this)
        rail.adapter = adapter
        rail.itemAnimator = null
        rail.clipChildren = true
        rail.clipToPadding = true
        // Slot height is computed from the rail's actual height so 5 shortcuts are
        // always visible without scrolling, regardless of screen size/aspect ratio.
        rail.doOnLayout {
            val available = it.height - it.paddingTop - it.paddingBottom
            val slotHeight = available / HomeRailAdapter.SLOTS_VISIBLE
            adapter.setItemHeightPx(slotHeight)
            (rail.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                focusedRailIndex, available / 2 - slotHeight / 2,
            )
        }

        findViewById<TextView>(R.id.softkey_left).setOnClickListener { openLeftKeyApp() }
        findViewById<TextView>(R.id.softkey_right).setOnClickListener { openRightKeyApp() }
        appMenuButton = findViewById<ImageView>(R.id.softkey_center).apply {
            setOnClickListener { openAppDrawer() }
            setOnLongClickListener { openOptions(); true }
            // Left jumps straight to the first pinned shortcut. The soft keys
            // either side of this button aren't keyboard-focusable, so without
            // this, default focus search would still wander into the rail on
            // its own (it's the only other focusable view) - landing on
            // whichever item geometric search happens to pick, not the top one.
            // Right has no focusable target and is swallowed so it's a no-op
            // instead of also leaking into the rail.
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        setRailExpanded(true)
                        rail.layoutManager?.findViewByPosition(focusedRailIndex)?.requestFocus() ?: rail.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setRailExpanded(false) }
        }

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
        val density = resources.displayMetrics.density
        rail.animate()
            .translationX(if (expanded) 0f else -42f * density)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .start()
        findViewById<View>(R.id.home_clock_group).let { clockGroup ->
            val params = clockGroup.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.marginStart = ((if (expanded) 8 else -44) * density).toInt()
            clockGroup.layoutParams = params
        }
        adapter.setExpanded(expanded)
        val openCrescent = intArrayOf(0, 10, 16, 10, 0)
        val closedCrescent = intArrayOf(-9, 2, 9, 2, -9)
        for (index in 0 until rail.childCount) {
            val row = rail.getChildAt(index)
            val position = rail.getChildAdapterPosition(row).coerceAtLeast(0)
            val offset = (if (expanded) openCrescent else closedCrescent)[position.coerceAtMost(4)].toFloat()
            row.findViewById<View>(R.id.circle).animate()
                .translationX(offset * density)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun moveRailFocus(position: Int, direction: Int): Boolean {
        val target = (position + direction).coerceIn(0, adapter.itemCount - 1)
        if (target == position) return true
        focusedRailIndex = target
        val layout = rail.layoutManager as? LinearLayoutManager ?: return true
        val available = rail.height - rail.paddingTop - rail.paddingBottom
        val slotHeight = available / HomeRailAdapter.SLOTS_VISIBLE
        layout.smoothScrollToPosition(rail, RecyclerView.State(), target)
        // Keep the active shortcut centered even at the ends of the list.
        rail.postDelayed({
            layout.scrollToPositionWithOffset(target, available / 2 - slotHeight / 2)
            rail.post {
                layout.findViewByPosition(target)?.let { row ->
                    row.requestFocus()
                    val circle = row.findViewById<View>(R.id.circle)
                    circle.animate().rotationBy(if (direction > 0) 360f else -360f)
                        .setDuration(280).setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }, 120)
        return true
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
        refreshShortcuts()
        refreshLeftKeyLabel()
        refreshRightKeyLabel()
        focusAppMenu()
        NotificationCounts.addListener(notifListener)
        updateNotifSummary()
        maybePromptDefaultLauncher()
        // The accent color may have changed in Settings while Home was backgrounded;
        // theme overlays only apply at onCreate, so recreate to pick it up. Done last
        // (after registering the receiver/listener above) so onPause's matching
        // unregister calls below still have something to unregister.
        if (prefs.getAccentColor() != appliedAccentColor) recreate()
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
                adapter.submit(entries)
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

    /** Highlight the "open app menu" button by default whenever Home is shown. */
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

    private fun refreshRightKeyLabel() {
        val key = prefs.getRightKeyApp()
        val label = key?.let { AppRepository.resolveComponent(this, it)?.label }
            ?: getString(R.string.softkey_contacts)
        findViewById<TextView>(R.id.softkey_right).text = label
    }

    private fun refreshLeftKeyLabel() {
        val key = prefs.getLeftKeyApp()
        val label = key?.let { AppRepository.resolveComponent(this, it)?.label }
            ?: getString(R.string.softkey_notifications)
        findViewById<TextView>(R.id.softkey_left).text = label
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
            KeyEvent.KEYCODE_SOFT_LEFT -> { openLeftKeyApp(); return true }
            KeyEvent.KEYCODE_SOFT_RIGHT -> { openRightKeyApp(); return true }
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
    }
}
