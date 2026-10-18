package quest.devinthebm.launcher.data

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * Icons bundled with the launcher itself (res/drawable-nodpi), offered in the
 * per-app icon picker alongside any installed icon packs - so there's always
 * something to choose from even with no icon pack installed.
 */
object BuiltInIcons {

    /** Sentinel "pack" id stored in [LauncherPrefs] icon overrides for these. */
    const val PACK_ID = "flip_launcher_built_in"

    private val NAMES = listOf(
        "calendar_112",
        "call_log_112",
        "camera_112",
        "clock_112",
        "contact_112",
        "email_112",
        "fm_112",
        "gallery_84",
        "music_112",
        "note_112",
        "search_112",
        "settings_112",
        "sms_112",
        "soundrecorder_112",
        "unitconverter_112",
        "video_112",
    )

    fun names(): List<String> = NAMES

    fun loadIcon(context: Context, name: String): Drawable? {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId == 0) null else ContextCompat.getDrawable(context, resId)
    }
}
