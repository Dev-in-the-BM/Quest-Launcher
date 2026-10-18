package quest.devinthebm.launcher.ui

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import quest.devinthebm.launcher.R

/** A single text row, optionally with a leading icon and a trailing badge. */
data class Row(
    val title: String,
    val trailing: String? = null,
    val icon: Drawable? = null,
    val iconRes: Int? = null,
    // The default title color selector darkens on focus to stay legible
    // against the accent highlight (Options, Settings, etc). The app drawer's
    // list view wants its title to read the same focused or not, like the
    // grid's labels do, so it opts out of that swap.
    val keepWhiteTitle: Boolean = false,
    // Notification dot color shown over the leading icon, or null for none.
    // Only set by the app drawer's list view; every other screen leaves it null.
    val badgeColor: Int? = null,
)

/**
 * Generic vertical-list adapter shared by the Options, Hide Apps, Shortcuts and
 * App Picker screens. Clicks are reported by position so each screen owns its
 * own behavior.
 */
class ListRowAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: ((Int) -> Unit)? = null,
    private val onFocusChanged: ((Int) -> Unit)? = null,
) : RecyclerView.Adapter<ListRowAdapter.VH>() {

    private val rows = ArrayList<Row>()

    fun submit(list: List<Row>) {
        rows.clear()
        rows.addAll(list)
        notifyDataSetChanged()
    }

    fun updateRow(position: Int, row: Row) {
        if (position in rows.indices) {
            rows[position] = row
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_row, parent, false)
        return VH(view)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.row_icon)
        private val notifDot: View = itemView.findViewById(R.id.row_notif_dot)
        private val title: TextView = itemView.findViewById(R.id.row_title)
        private val trailing: TextView = itemView.findViewById(R.id.row_trailing)

        fun bind(row: Row) {
            when {
                row.icon != null -> {
                    icon.setImageDrawable(row.icon)
                    icon.visibility = View.VISIBLE
                }
                row.iconRes != null -> {
                    icon.setImageResource(row.iconRes)
                    icon.visibility = View.VISIBLE
                }
                else -> icon.visibility = View.GONE
            }
            if (row.badgeColor != null) {
                notifDot.visibility = View.VISIBLE
                notifDot.backgroundTintList = ColorStateList.valueOf(row.badgeColor)
            } else {
                notifDot.visibility = View.GONE
            }
            title.text = row.title
            if (row.keepWhiteTitle) {
                title.setTextColor(ContextCompat.getColor(itemView.context, R.color.kai_text))
            } else {
                title.setTextColor(ContextCompat.getColorStateList(itemView.context, R.color.text_on_item))
            }
            trailing.text = row.trailing ?: ""
            trailing.visibility = if (row.trailing.isNullOrEmpty()) View.GONE else View.VISIBLE

            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(pos)
            }
            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onLongClick?.invoke(pos)
                onLongClick != null
            }
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onFocusChanged?.invoke(pos)
                }
            }
        }
    }
}
