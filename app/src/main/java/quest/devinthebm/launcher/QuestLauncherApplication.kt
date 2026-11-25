package quest.devinthebm.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.theonionsarewatching.yapchik.SoftkeyMode
import com.theonionsarewatching.yapchik.Yapchik
import quest.devinthebm.launcher.data.AppRepository

class QuestLauncherApplication : Application() {
    private val packageRefreshHandler = Handler(Looper.getMainLooper())
    private val pendingChangedPackages = LinkedHashSet<String>()
    private val reconcilePackages = Runnable {
        val packages = pendingChangedPackages.toSet()
        pendingChangedPackages.clear()
        AppRepository.reconcilePackages(this, packages)
    }
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // PACKAGE_REMOVED + ADDED can be a single app update. Debouncing
            // treats the burst as one reconciliation and avoids publishing the
            // transient "removed" state for a replacing package.
            val packageName = intent?.data?.schemeSpecificPart
            if (packageName != null) pendingChangedPackages += packageName
            packageRefreshHandler.removeCallbacks(reconcilePackages)
            packageRefreshHandler.postDelayed(reconcilePackages, PACKAGE_EVENT_DEBOUNCE_MS)
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
        // Warm the lightweight app catalog while Home is visible so the drawer
        // can bind from memory on entry.
        AppRepository.preload(this)
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private companion object {
        const val PACKAGE_EVENT_DEBOUNCE_MS = 400L
    }
}
