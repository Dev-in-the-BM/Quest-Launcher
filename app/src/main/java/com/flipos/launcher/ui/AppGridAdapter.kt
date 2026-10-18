package com.flipos.launcher.ui

import android.content.res.ColorStateList
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.flipos.launcher.R
import com.flipos.launcher.data.AppInfo
import com.flipos.launcher.data.NotificationDotColor

/** Grid of apps used by the app drawer. Icons only — [onFocusChanged] lets the
 * screen mirror the focused app's name in its title bar in place of labels. */
class AppGridAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit,
    private val onFocusChanged: (AppInfo, View) -> Unit = { _, _ -> },
    private val iconSizePercent: Int = 100,
    private val hasNotification: (AppInfo) -> Boolean = { false },
) : RecyclerView.Adapter<AppGridAdapter.VH>() {

    private val items = ArrayList<AppInfo>()

    // The RecyclerView's own width/height aren't readable from a ViewHolder at
    // bind time - RecyclerView binds a child's content before attaching it to
    // the parent, so itemView.parent is null inside onBindViewHolder. The grid
    // owner (AppDrawerActivity) measures its own width/height/columns/rows and
    // pushes them in here instead.
    private var gridWidthPx = 0
    private var gridHeightPx = 0
    private var columns = 1
    private var rows = 1

    fun submit(list: List<AppInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** Tells the icon-size clamp the grid's current content area and shape,
     * so a large icon-size preference can't overflow a column's width or push
     * the last of [rows] rows past the bottom of the grid on a small screen. */
    fun setGridMetrics(widthPx: Int, heightPx: Int, columns: Int, rows: Int) {
        val coercedColumns = columns.coerceAtLeast(1)
        val coercedRows = rows.coerceAtLeast(1)
        if (widthPx == gridWidthPx && heightPx == gridHeightPx && coercedColumns == this.columns && coercedRows == this.rows) return
        gridWidthPx = widthPx
        gridHeightPx = heightPx
        this.columns = coercedColumns
        this.rows = coercedRows
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): AppInfo? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val notifDot: View = itemView.findViewById(R.id.notif_dot)
        fun bind(app: AppInfo) {
            val density = Resources.getSystem().displayMetrics.density
            // Before the grid's first layout pass, gridWidthPx/gridHeightPx are
            // still 0 - fall back to a fixed size so icons aren't briefly
            // invisible; setGridMetrics rebinds with the real fit moments later.
            val fitPx = baseFitIconPx(gridWidthPx, gridHeightPx, columns, density)
                .takeIf { it > 0 } ?: (PRE_LAYOUT_FALLBACK_DP * density).toInt()
            val desiredPx = (fitPx * iconSizePercent / 100f).toInt()
            val px = desiredPx.coerceAtMost(maxIconPx(density)).coerceAtLeast(0)
            icon.layoutParams = icon.layoutParams.apply { width = px; height = px }
            icon.setImageDrawable(app.icon)
            icon.contentDescription = app.label
            if (hasNotification(app)) {
                notifDot.visibility = View.VISIBLE
                notifDot.backgroundTintList = ColorStateList.valueOf(NotificationDotColor.forIcon(app.key, app.icon))
            } else {
                notifDot.visibility = View.GONE
            }
            itemView.setOnClickListener { onClick(app) }
            itemView.setOnLongClickListener {
                onLongClick(app)
                true
            }
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onFocusChanged(app, itemView)
            }
        }

        /** Shrinks the icon to fit the grid's current column width and row
         * height, so a large icon-size preference can't overflow past
         * item_app_grid's overhead of margin+padding and get clipped by the
         * FrameLayout - or push the last row past the bottom of the grid - on
         * a small screen. */
        private fun maxIconPx(density: Float): Int {
            if (gridWidthPx <= 0 || gridHeightPx <= 0) return Int.MAX_VALUE
            val cellWidth = gridWidthPx / columns
            val cellHeight = gridHeightPx / rows
            val overheadPx = (ITEM_OVERHEAD_DP * density).toInt()
            return (minOf(cellWidth, cellHeight) - overheadPx).coerceAtLeast(0)
        }
    }

    companion object {
        /** item_app_grid's 4dp margin + 8dp padding + icon_frame's 6dp padding,
         * each on every side: (4 + 8 + 6) * 2. */
        const val ITEM_OVERHEAD_DP = 36

        /** The grid always guarantees at least this many rows fit with no
         * scrolling - the 100% icon-size baseline is pinned to this, not to
         * however many rows the current page actually shows (which can be
         * more, on a screen tall enough to fit extra rows at that size). */
        private const val GUARANTEED_ROWS = 3

        /** Used only before the grid's first layout pass, when there's no
         * real fit measurement yet to scale a percentage off of. */
        private const val PRE_LAYOUT_FALLBACK_DP = 61

        /** The icon pixel size that exactly fills a [GUARANTEED_ROWS]x[columns]
         * grid in [contentWidthPx]x[contentHeightPx] with no scrolling - the
         * 100% baseline that icon-size percentages scale from. Shared with
         * [AppDrawerActivity] so its page-size math stays in sync with this
         * adapter's icon clamp. */
        fun baseFitIconPx(contentWidthPx: Int, contentHeightPx: Int, columns: Int, density: Float): Int {
            if (contentWidthPx <= 0 || contentHeightPx <= 0 || columns <= 0) return 0
            val cellWidth = contentWidthPx / columns
            val cellHeight = contentHeightPx / GUARANTEED_ROWS
            val overheadPx = (ITEM_OVERHEAD_DP * density).toInt()
            return (minOf(cellWidth, cellHeight) - overheadPx).coerceAtLeast(0)
        }
    }
}
