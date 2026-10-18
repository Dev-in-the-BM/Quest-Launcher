package quest.devinthebm.launcher.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import quest.devinthebm.launcher.R
import quest.devinthebm.launcher.data.NoticeItem

/** Rows for the custom Notices screen: icon, title, body text and a relative timestamp. */
class NoticeRowAdapter(
    private val onClick: (NoticeItem) -> Unit,
    private val onFocusChanged: (NoticeItem) -> Unit = {},
) : RecyclerView.Adapter<NoticeRowAdapter.VH>() {

    private val items = ArrayList<NoticeItem>()

    fun submit(list: List<NoticeItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): NoticeItem? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notice_row, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.notice_icon)
        private val title: TextView = itemView.findViewById(R.id.notice_title)
        private val text: TextView = itemView.findViewById(R.id.notice_text)
        private val time: TextView = itemView.findViewById(R.id.notice_time)

        fun bind(item: NoticeItem) {
            icon.setImageDrawable(item.icon)
            title.text = item.title
            text.text = item.text
            text.visibility = if (item.text.isEmpty()) View.GONE else View.VISIBLE
            time.text = DateUtils.getRelativeTimeSpanString(
                item.postTime,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onFocusChanged(item) }
        }
    }
}
