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
) : RecyclerView.Adapter<HomeRailAdapter.VH>() {

    private val items = ArrayList<RailItem>()

    /** Slot height in px so exactly [SLOTS_VISIBLE] rows fit the rail without scrolling. */
    var itemHeightPx = -1
        private set

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
        private val selectionHighlight: View = itemView.findViewById(R.id.selection_highlight)
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val notifDot: View = itemView.findViewById(R.id.notif_dot)

        fun bind(item: RailItem) {
            if (itemHeightPx > 0) itemView.layoutParams = itemView.layoutParams.apply { height = itemHeightPx }
            val app = item.app
            if (app != null) {
                // The icon is already shaped (and given a background, if any)
                // by IconShapeRenderer when it was resolved, so it's shown as-is.
                circle.background = null
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                icon.clearColorFilter()
                icon.setImageDrawable(app.icon)
                // Match the app drawer: the focus shape takes its color from
                // the icon it sits behind, rather than using the global accent.
                selectionHighlight.background = SquircleDrawable(
                    NotificationDotColor.forIcon(app.key, app.icon),
                )
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
                // The add-shortcut control is not an app, so retain a neutral
                // focus surface instead of inventing an app color for it.
                selectionHighlight.setBackgroundResource(R.drawable.bg_rail_focus)
                notifDot.visibility = View.GONE
            }
            // A recycled row may have been the outgoing side of a wheel
            // cross-fade; restore its neutral state before it represents a
            // different shortcut.
            selectionHighlight.animate().cancel()
            selectionHighlight.alpha = 1f
            selectionHighlight.isSelected = false
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(pos, item)
            }
            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onLongClick(pos, item)
                true
            }
        }
    }

    companion object {
        /** Rows the rail's height is divided into, so this many are always visible without scrolling. */
        const val SLOTS_VISIBLE = 5
    }
}
