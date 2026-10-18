package quest.devinthebm.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import quest.devinthebm.launcher.R

/**
 * The three-button bar pinned to the bottom of every screen, mimicking a
 * feature phone's left / center / right soft keys. The center key is drawn as
 * an accent pill to signal the primary action (it maps to D-pad center / OK).
 *
 * The labels are not focusable so they never steal D-pad focus from the content
 * above them; they respond to touch and to the hardware soft keys handled by
 * each Activity.
 */
class SoftKeyBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val left: TextView
    private val center: TextView
    private val right: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.kai_softkey_bg))
        minimumHeight = resources.getDimensionPixelSize(R.dimen.softkey_height)
        LayoutInflater.from(context).inflate(R.layout.view_soft_keys, this, true)
        left = findViewById(R.id.softkey_left)
        center = findViewById(R.id.softkey_center)
        right = findViewById(R.id.softkey_right)
    }

    fun setLabels(left: String?, center: String?, right: String?) {
        bind(this.left, left)
        bind(this.center, center)
        bind(this.right, right)
    }

    /**
     * Swaps the center key's default accent pill for plain bold white text
     * (e.g. the App Drawer's "SELECT", which sits over the wallpaper rather
     * than the solid soft-key bar most other screens use).
     */
    fun setCenterPlainLabel(label: String?) {
        bind(center, label)
        center.background = null
        center.setTextColor(ContextCompat.getColor(context, R.color.kai_text))
    }

    fun setOnLeftClick(action: () -> Unit) = left.setOnClickListener { action() }
    fun setOnCenterClick(action: () -> Unit) = center.setOnClickListener { action() }
    fun setOnRightClick(action: () -> Unit) = right.setOnClickListener { action() }

    private fun bind(view: TextView, label: String?) {
        view.text = label ?: ""
        view.visibility = if (label.isNullOrEmpty()) INVISIBLE else VISIBLE
    }
}
