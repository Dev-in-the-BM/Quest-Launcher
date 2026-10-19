package quest.devinthebm.launcher

import android.app.Application
import androidx.core.content.ContextCompat
import com.theonionsarewatching.yapchik.SoftkeyMode
import com.theonionsarewatching.yapchik.Yapchik

class QuestLauncherApplication : Application() {
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
    }
}
