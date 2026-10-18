package quest.devinthebm.launcher.data

import android.graphics.drawable.Drawable

/** An installed icon pack app, detected via the standard icon-pack intent. */
data class IconPack(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)
