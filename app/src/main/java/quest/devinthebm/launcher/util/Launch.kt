package quest.devinthebm.launcher.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import quest.devinthebm.launcher.data.AppRepository

/** Launches an app by its stored [key], surfacing a toast if it can't be opened. */
fun Context.launchAppByKey(key: String) {
    val intent = AppRepository.launchIntentFor(key)
    if (intent == null) {
        Toast.makeText(this, "App is no longer available", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "App is no longer available", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(this, "Can't open this app", Toast.LENGTH_SHORT).show()
    }
}
