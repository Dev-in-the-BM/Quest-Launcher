package quest.devinthebm.launcher

import android.os.Bundle
import android.widget.TextView
import quest.devinthebm.launcher.theme.ThemedActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theonionsarewatching.yapchik.SoftkeyController
import com.theonionsarewatching.yapchik.Softkeys
import quest.devinthebm.launcher.data.LauncherPrefs

/**
 * Shared scaffolding for the vertical list screens (Options, Hide Apps,
 * Shortcuts, App Picker): a title bar, a focusable [RecyclerView] and the bottom
 * Yapchik softkeys. Subclasses populate the adapter and wire up the soft keys.
 */
abstract class BaseListActivity : ThemedActivity() {

    protected lateinit var titleView: TextView
    protected lateinit var listView: RecyclerView
    protected lateinit var softKeys: SoftkeyController

    /** The accent color applied this onCreate, so [onResume] can detect a change and [recreate]. */
    private var appliedAccentColor: LauncherPrefs.AccentColor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accent = LauncherPrefs(this).getAccentColor()
        appliedAccentColor = accent
        if (launcherTheme.components == quest.devinthebm.launcher.theme.LauncherComponentSelection.KaiOS && accent.themeOverlayRes != 0) {
            theme.applyStyle(accent.themeOverlayRes, true)
        }
        setContentView(R.layout.activity_list)
        titleView = findViewById(R.id.title)
        listView = findViewById(R.id.list)
        softKeys = Softkeys.of(this)
        listView.layoutManager = LinearLayoutManager(this)
        listView.itemAnimator = null
    }

    override fun onResume() {
        super.onResume()
        // The accent color may have changed in Settings while this activity was
        // backgrounded; theme overlays only apply at onCreate, so recreate to pick it up.
        if (LauncherPrefs(this).getAccentColor() != appliedAccentColor) recreate()
    }

    /** Adapter position of the focused row, or 0 when nothing is focused. */
    protected fun focusedPosition(): Int {
        val child = listView.focusedChild ?: return 0
        val pos = listView.getChildAdapterPosition(child)
        return if (pos == RecyclerView.NO_POSITION) 0 else pos
    }

    protected fun focusFirst() {
        listView.post {
            if (listView.focusedChild == null) {
                listView.layoutManager?.findViewByPosition(0)?.requestFocus()
            }
        }
    }
}
