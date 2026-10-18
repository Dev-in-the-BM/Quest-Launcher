package quest.devinthebm.launcher.data

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * Wallpapers bundled with the launcher (res/drawable-nodpi), offered in the
 * Wallpaper Picker so there's always a curated set to choose from without
 * leaving the launcher.
 */
object BuiltInWallpapers {

    private val NAMES = listOf(
        "wallpaper_default",
        "img_wallpaper_01",
        "img_wallpaper_02",
        "img_wallpaper_03",
        "img_wallpaper_04",
        "img_wallpaper_05",
        "img_wallpaper_06",
        "img_wallpaper_07",
        "img_wallpaper_08",
        "img_wallpaper_09",
        "wallpaper_25",
        "wallpaper_26",
        "wallpaper_27",
    )

    fun names(): List<String> = NAMES

    fun loadThumbnail(context: Context, name: String): Drawable? {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId == 0) null else ContextCompat.getDrawable(context, resId)
    }

    fun resId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)
}
