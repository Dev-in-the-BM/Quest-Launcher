package quest.devinthebm.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.theonionsarewatching.yapchik.SoftkeyMode
import com.theonionsarewatching.yapchik.Yapchik
import quest.devinthebm.launcher.data.AppRepository

class QuestLauncherApplication : Application() {
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidateCache()
            AppRepository.preload(this@QuestLauncherApplication)
        }
    }

    override fun onCreate() {
        super.onCreate()

        Yapchik.install(this)
        // Quest Launcher is designed around a keypad-first layout, including on
        // devices where Yapchik's automatic hardware detection is inconclusive.
        Yapchik.defaultScreenMode = SoftkeyMode.ON
        Yapchik.style.apply {
            backgroundColor = ContextCompat.getColor(this@QuestLauncherApplication, R.color.kai_softkey_bg)
            textColor = ContextCompat.getColor(this@QuestLauncherApplication, R.color.kai_text)
            pressedTextColor = ContextCompat.getColor(this@QuestLauncherApplication, R.color.kai_accent)
            dividerColor = ContextCompat.getColor(this@QuestLauncherApplication, R.color.kai_divider)
            heightDp = 48
            textSizeSp = 15f
            horizontalPaddingDp = 18
        }
        // Resolve labels/icons while Home is shown, not after the drawer opens.
        AppRepository.preload(this)
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
