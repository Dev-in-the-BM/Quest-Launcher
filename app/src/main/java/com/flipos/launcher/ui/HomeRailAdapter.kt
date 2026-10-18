package com.flipos.launcher.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.flipos.launcher.R
import com.flipos.launcher.data.AppInfo
import com.flipos.launcher.data.NotificationDotColor

/** A rail entry: an app shortcut, or the trailing "add" tile when [app] is null. */
data class RailItem(val app: AppInfo?)

/**
 * The left home rail: stacked white app discs, plus a trailing dashed "+" tile
 * for adding another shortcut. Icons only — the position (top to bottom) is the
 * 1-9 number-key mapping.
 */
class HomeRailAdapter(
    private val onClick: (position: Int, item: RailItem) -> Unit,
    private val onLongClick: (position: Int, item: RailItem) -> Unit,
    private val hasNotification: (AppInfo) -> Boolean = { false },
    private val onNavigate: (position: Int, direction: Int) -> Boolean = { _, _ -> false },
) : RecyclerView.Adapter<HomeRailAdapter.VH>() {

    private val items = ArrayList<RailItem>()

    /** Slot height in px so exactly [SLOTS_VISIBLE] rows fit the rail without scrolling. */
    private var itemHeightPx = -1
    private var expanded = false

    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
    }

    fun submit(list: List<RailItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** Pins every row to this height; pass -1 to fall back to wrap_content. */
    fun setItemHeightPx(px: Int) {
        if (itemHeightPx == px) return
        itemHeightPx = px
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_rail, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val circle: FrameLayout = itemView.findViewById(R.id.circle)
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val notifDot: View = itemView.findViewById(R.id.notif_dot)

        fun bind(item: RailItem) {
            if (itemHeightPx > 0) itemView.layoutParams = itemView.layoutParams.apply { height = itemHeightPx }
            val slot = bindingAdapterPosition.coerceAtLeast(0)
            // Keep the crescent in both states; in the closed state the ends
            // sit a little farther off-screen than the center shortcuts.
            val crescent = if (expanded) {
                intArrayOf(0, 10, 16, 10, 0)[slot.coerceAtMost(4)]
            } else {
                intArrayOf(-9, 2, 9, 2, -9)[slot.coerceAtMost(4)]
            }
            circle.translationX = itemView.resources.displayMetrics.density *
                crescent.toFloat()
            val app = item.app
            if (app != null) {
                // The icon is already shaped (and given a background, if any)
                // by IconShapeRenderer when it was resolved, so it's shown as-is.
                circle.background = null
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                icon.clearColorFilter()
                icon.setImageDrawable(app.icon)
                if (hasNotification(app)) {
                    notifDot.visibility = View.VISIBLE
                    notifDot.backgroundTintList = ColorStateList.valueOf(NotificationDotColor.forIcon(app.key, app.icon))
                } else {
                    notifDot.visibility = View.GONE
                }
            } else {
                circle.setBackgroundResource(R.drawable.bg_circle_add)
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                icon.setImageResource(R.drawable.ic_add)
                icon.setColorFilter(Color.WHITE)
                notifDot.visibility = View.GONE
            }
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(pos, item)
            }
            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onLongClick(pos, item)
                true
            }
            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val direction = when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> -1
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 1
                    else -> return@setOnKeyListener false
                }
                val pos = bindingAdapterPosition
                pos != RecyclerView.NO_POSITION && onNavigate(pos, direction)
            }
        }
    }

    companion object {
        /** Rows the rail's height is divided into, so this many are always visible without scrolling. */
        const val SLOTS_VISIBLE = 5
    }
}
