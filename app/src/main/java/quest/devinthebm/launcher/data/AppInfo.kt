package quest.devinthebm.launcher.data

import android.content.ComponentName
import android.graphics.drawable.Drawable

/**
 * A single launchable application entry.
 *
 * [key] is the stable identifier persisted in preferences (hidden set, home
 * shortcuts). It is the flattened component name, e.g.
 * "com.android.chrome/com.google.android.apps.chrome.Main".
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)

    val key: String
        get() = componentName.flattenToString()
}
