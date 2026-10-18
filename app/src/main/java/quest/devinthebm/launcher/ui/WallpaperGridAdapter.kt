package quest.devinthebm.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import quest.devinthebm.launcher.R
import quest.devinthebm.launcher.data.BuiltInWallpapers

/** Grid of bundled wallpaper thumbnails for the Wallpaper Picker. */
class WallpaperGridAdapter(
    private val onClick: (name: String) -> Unit,
) : RecyclerView.Adapter<WallpaperGridAdapter.VH>() {

    private val names = ArrayList<String>()

    fun submit(list: List<String>) {
        names.clear()
        names.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallpaper_grid, parent, false)
        return VH(view)
    }

    override fun getItemCount() = names.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(names[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumb: ImageView = itemView.findViewById(R.id.thumb)

        fun bind(name: String) {
            thumb.setImageDrawable(BuiltInWallpapers.loadThumbnail(itemView.context, name))
            itemView.setOnClickListener { onClick(name) }
        }
    }
}
