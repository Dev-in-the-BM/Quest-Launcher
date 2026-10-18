package quest.devinthebm.launcher.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import quest.devinthebm.launcher.R

/** Grid of individual icons inside one icon pack, for picking a replacement app icon. */
class IconGridAdapter(
    private val onClick: (String) -> Unit,
    private val iconLoader: (String) -> Drawable?,
) : RecyclerView.Adapter<IconGridAdapter.VH>() {

    private val names = ArrayList<String>()

    fun submit(list: List<String>) {
        names.clear()
        names.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return VH(view)
    }

    override fun getItemCount() = names.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(names[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)

        fun bind(name: String) {
            icon.setImageDrawable(iconLoader(name))
            itemView.setOnClickListener { onClick(name) }
        }
    }
}
