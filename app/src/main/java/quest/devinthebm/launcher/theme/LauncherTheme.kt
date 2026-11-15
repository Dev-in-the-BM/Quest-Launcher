package quest.devinthebm.launcher.theme

import android.app.Activity
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import quest.devinthebm.launcher.R
import quest.devinthebm.launcher.data.LauncherPrefs
import com.theonionsarewatching.yapchik.Yapchik
import androidx.core.content.ContextCompat

@JvmInline
value class ThemeId(val value: String)

/** Closed, app-owned component choices. These describe presentation seams, not business behavior. */
sealed interface LauncherComponentSelection {
    data object KaiOS : LauncherComponentSelection
    data object Material2 : LauncherComponentSelection
}

private class LauncherPresentation(private val material2: Boolean) : ThemePresentationExtension {
    override fun configureActivity(activity: Activity) {
        val style = Yapchik.style
        style.backgroundColor = ContextCompat.getColor(activity, if (material2) R.color.m2_background else R.color.kai_softkey_bg)
        style.textColor = ContextCompat.getColor(activity, if (material2) R.color.m2_control_ink else R.color.kai_text)
        style.pressedTextColor = ContextCompat.getColor(activity, if (material2) R.color.m2_action else R.color.kai_accent)
        style.dividerColor = ContextCompat.getColor(activity, if (material2) R.color.m2_divider else R.color.kai_divider)
        style.heightDp = 48
        style.textSizeSp = 15f
        style.horizontalPaddingDp = 18
    }
}

interface ThemePresentationExtension {
    fun configureActivity(activity: Activity) {}
    fun configureHost(host: View, role: String) {}
}

data class ThemeDescriptor(
    val id: ThemeId,
    @StringRes val displayNameRes: Int,
    @StyleRes val styleRes: Int,
    val components: LauncherComponentSelection,
    val presentation: ThemePresentationExtension? = null,
)

object LauncherThemeCatalog {
    val descriptors = listOf(
        ThemeDescriptor(ThemeId("kaios"), R.string.theme_kaios, R.style.Theme_QuestLauncher, LauncherComponentSelection.KaiOS, LauncherPresentation(false)),
        ThemeDescriptor(ThemeId("material2"), R.string.theme_material2, R.style.Theme_QuestLauncher_Material2, LauncherComponentSelection.Material2, LauncherPresentation(true)),
    )
    val defaultId = ThemeId("kaios")
    init {
        require(descriptors.map { it.id }.distinct().size == descriptors.size) { "Theme IDs must be unique" }
        require(descriptors.count { it.id == defaultId } == 1) { "Catalog must have exactly one registered default" }
        require(descriptors.all { it.styleRes != 0 && it.displayNameRes != 0 }) { "Theme descriptors need local style and name resources" }
    }
    fun resolve(id: ThemeId?) = descriptors.firstOrNull { it.id == id } ?: descriptors.first { it.id == defaultId }
}

interface ThemeSelectionStore {
    fun read(): ThemeId?
    fun write(id: ThemeId)
}

class LauncherThemeStore(private val prefs: LauncherPrefs) : ThemeSelectionStore {
    override fun read() = prefs.getThemeId()?.let(::ThemeId)
    override fun write(id: ThemeId) {
        require(LauncherThemeCatalog.descriptors.any { it.id == id }) { "Unregistered launcher theme: ${id.value}" }
        prefs.setThemeId(id.value)
    }
}

object ThemeSelection {
    fun current(prefs: LauncherPrefs) = LauncherThemeCatalog.resolve(LauncherThemeStore(prefs).read())
}

/** Apply before super.onCreate; refresh an older back-stack Activity after selection changes. */
abstract class ThemedActivity : androidx.appcompat.app.AppCompatActivity() {
    private var appliedThemeId: ThemeId? = null
    protected val launcherTheme get() = LauncherThemeCatalog.resolve(ThemeSelection.current(quest.devinthebm.launcher.data.LauncherPrefs(this)).id)

    protected open fun activityThemeStyle(descriptor: ThemeDescriptor) = descriptor.styleRes

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        val descriptor = ThemeSelection.current(quest.devinthebm.launcher.data.LauncherPrefs(this))
        appliedThemeId = descriptor.id
        setTheme(activityThemeStyle(descriptor))
        super.onCreate(savedInstanceState)
        descriptor.presentation?.configureActivity(this)
    }

    override fun onResume() {
        super.onResume()
        if (appliedThemeId != null && ThemeSelection.current(quest.devinthebm.launcher.data.LauncherPrefs(this)).id != appliedThemeId) recreate()
    }
}
