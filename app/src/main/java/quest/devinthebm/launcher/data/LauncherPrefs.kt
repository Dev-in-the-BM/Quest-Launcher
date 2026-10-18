package quest.devinthebm.launcher.data

import android.content.Context
import quest.devinthebm.launcher.R

/**
 * Persists the two pieces of user customization this launcher supports:
 *  - the set of hidden app keys, and
 *  - an ordered list of home-screen shortcut app keys (max 9, mapped to keys 1-9).
 *
 * Shortcuts are stored as a compact ordered list (newline-joined keys) so the
 * left rail shows them gap-free, top to bottom.
 */
class LauncherPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- Hidden

    fun getHiddenKeys(): MutableSet<String> =
        // Copy: the Set returned by getStringSet must not be mutated in place.
        HashSet(prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())

    fun isHidden(key: String): Boolean = getHiddenKeys().contains(key)

    fun setHidden(key: String, hidden: Boolean) {
        val set = getHiddenKeys()
        if (hidden) set.add(key) else set.remove(key)
        prefs.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    // ------------------------------------------------------------- Shortcuts

    /** Ordered list of shortcut app keys (never longer than [MAX_SHORTCUTS]). */
    fun getShortcuts(): MutableList<String> {
        val raw = prefs.getString(KEY_SHORTCUTS, "").orEmpty()
        if (raw.isEmpty()) return mutableListOf()
        return raw.split('\n').filter { it.isNotEmpty() }.toMutableList()
    }

    fun setShortcuts(list: List<String>) {
        prefs.edit().putString(KEY_SHORTCUTS, list.joinToString("\n")).apply()
    }

    /** Append a shortcut. Returns false if Home is full or the app is already pinned. */
    fun addShortcut(key: String): Boolean {
        val list = getShortcuts()
        if (list.size >= MAX_SHORTCUTS || list.contains(key)) return false
        list.add(key)
        setShortcuts(list)
        return true
    }

    /** Replace the shortcut at [index], or append when [index] == size. */
    fun setShortcutAt(index: Int, key: String) {
        val list = getShortcuts()
        when {
            index in list.indices -> list[index] = key
            index == list.size && list.size < MAX_SHORTCUTS -> list.add(key)
            else -> return
        }
        setShortcuts(list)
    }

    fun removeShortcutAt(index: Int) {
        val list = getShortcuts()
        if (index in list.indices) {
            list.removeAt(index)
            setShortcuts(list)
        }
    }

    // ------------------------------------------------------- Back long-press

    /** App key launched on long-pressing Back, or null if unconfigured. */
    fun getBackLongPressApp(): String? = prefs.getString(KEY_BACK_LONGPRESS_APP, null)

    fun setBackLongPressApp(key: String?) {
        prefs.edit().putString(KEY_BACK_LONGPRESS_APP, key).apply()
    }

    // ---------------------------------------------------------- Icon size

    /** App drawer icon size as a percentage of the size that exactly fills a
     * 3x3 grid with no scrolling on the current screen - not an absolute dp
     * value, so "Large" means the same relative thing on a tiny feature-phone
     * screen and a large tablet screen. Default is 100 (exactly fills 3x3). */
    fun getIconSizePercent(): Int = prefs.getInt(KEY_ICON_SIZE_PERCENT, DEFAULT_ICON_SIZE_PERCENT)

    fun setIconSizePercent(percent: Int) {
        prefs.edit().putInt(KEY_ICON_SIZE_PERCENT, percent).apply()
    }

    // ----------------------------------------------------- Home right key

    /** App key launched by the right soft key on Home, or null for Contacts. */
    fun getRightKeyApp(): String? = prefs.getString(KEY_RIGHT_KEY_APP, null)

    fun setRightKeyApp(key: String?) {
        prefs.edit().putString(KEY_RIGHT_KEY_APP, key).apply()
    }

    // ------------------------------------------------------ Home left key

    /** App key launched by the left soft key on Home, or null for Notices. */
    fun getLeftKeyApp(): String? = prefs.getString(KEY_LEFT_KEY_APP, null)

    fun setLeftKeyApp(key: String?) {
        prefs.edit().putString(KEY_LEFT_KEY_APP, key).apply()
    }

    // ----------------------------------------------------- App drawer layout

    /** Whether the app drawer shows a single-column list instead of an icon grid. */
    fun isDrawerListViewEnabled(): Boolean = prefs.getBoolean(KEY_DRAWER_LIST_VIEW, false)

    fun setDrawerListViewEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_DRAWER_LIST_VIEW, enabled).apply()

    // --------------------------------------------------- Notification badges

    fun isCallBadgeEnabled(): Boolean = prefs.getBoolean(KEY_BADGE_CALLS, true)
    fun setCallBadgeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BADGE_CALLS, enabled).apply()

    fun isMessageBadgeEnabled(): Boolean = prefs.getBoolean(KEY_BADGE_MESSAGES, true)
    fun setMessageBadgeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BADGE_MESSAGES, enabled).apply()

    fun isOtherBadgeEnabled(): Boolean = prefs.getBoolean(KEY_BADGE_OTHER, true)
    fun setOtherBadgeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BADGE_OTHER, enabled).apply()

    /** Whether app icons in the drawer/Home show a small notification dot. */
    fun isIconNotificationDotEnabled(): Boolean = prefs.getBoolean(KEY_BADGE_ICON_DOT, true)
    fun setIconNotificationDotEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BADGE_ICON_DOT, enabled).apply()

    // ------------------------------------------------------------- Icon packs

    /** Icon pack package applied launcher-wide, or null for default icons. */
    fun getActiveIconPack(): String? = prefs.getString(KEY_ACTIVE_ICON_PACK, null)

    fun setActiveIconPack(packageName: String?) {
        prefs.edit().putString(KEY_ACTIVE_ICON_PACK, packageName).apply()
    }

    /** Per-app icon override as (icon pack package, drawable name), or null if unset. */
    fun getIconOverride(appKey: String): Pair<String, String>? {
        val raw = prefs.getString(iconOverrideKey(appKey), null) ?: return null
        val parts = raw.split(ICON_OVERRIDE_SEPARATOR, limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    fun setIconOverride(appKey: String, packageName: String, drawableName: String) {
        prefs.edit()
            .putString(iconOverrideKey(appKey), "$packageName$ICON_OVERRIDE_SEPARATOR$drawableName")
            .apply()
    }

    fun clearIconOverride(appKey: String) {
        prefs.edit().remove(iconOverrideKey(appKey)).apply()
    }

    private fun iconOverrideKey(appKey: String) = "$KEY_ICON_OVERRIDE_PREFIX$appKey"

    // -------------------------------------------------------- Accent color

    /** The user's chosen accent color, or [AccentColor.DEFAULT] (the original cyan) if unset. */
    fun getAccentColor(): AccentColor {
        val stored = prefs.getString(KEY_ACCENT_COLOR, null) ?: return AccentColor.DEFAULT
        return AccentColor.entries.find { it.key == stored } ?: AccentColor.DEFAULT
    }

    fun setAccentColor(color: AccentColor) {
        prefs.edit().putString(KEY_ACCENT_COLOR, color.key).apply()
    }

    // --------------------------------------------------------- Icon wrapping

    /** The shape every wrapped icon is masked into, launcher-wide. */
    fun getIconShape(): IconShape {
        val stored = prefs.getString(KEY_ICON_SHAPE, null) ?: return IconShape.CIRCLE
        return IconShape.entries.find { it.key == stored } ?: IconShape.CIRCLE
    }

    fun setIconShape(shape: IconShape) {
        prefs.edit().putString(KEY_ICON_SHAPE, shape.key).apply()
    }

    /** Whether non-adaptive icons get a pale color-matched background, or sit on a transparent one. */
    fun isLegacyIconBackgroundEnabled(): Boolean = prefs.getBoolean(KEY_LEGACY_ICON_BG, true)

    fun setLegacyIconBackgroundEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_LEGACY_ICON_BG, enabled).apply()

    /** Per-app opt-out: whether [appKey]'s icon gets shape-masked at all. Defaults to on. */
    fun isIconWrapEnabled(appKey: String): Boolean = !getWrapDisabledKeys().contains(appKey)

    fun setIconWrapEnabled(appKey: String, enabled: Boolean) {
        val set = getWrapDisabledKeys()
        if (enabled) set.remove(appKey) else set.add(appKey)
        prefs.edit().putStringSet(KEY_WRAP_DISABLED, set).apply()
    }

    private fun getWrapDisabledKeys(): MutableSet<String> =
        HashSet(prefs.getStringSet(KEY_WRAP_DISABLED, emptySet()) ?: emptySet())

    /** The mask shape applied to every wrapped icon. */
    enum class IconShape(val key: String, val labelRes: Int) {
        SQUIRCLE("squircle", R.string.icon_shape_squircle),
        SQUARE("square", R.string.icon_shape_square),
        CIRCLE("circle", R.string.icon_shape_circle),
        ROUNDED_SQUARE("rounded_square", R.string.icon_shape_rounded_square),
        /** No masking at all — every icon shows exactly as its app (or adaptive layers) draws it. */
        NONE("none", R.string.icon_shape_none),
    }

    /** A rainbow of accent color presets, plus the launcher's original cyan as the default. */
    enum class AccentColor(val key: String, val labelRes: Int, val themeOverlayRes: Int) {
        DEFAULT("default", R.string.accent_color_default, 0),
        RED("red", R.string.accent_color_red, R.style.ThemeOverlay_QuestLauncher_Accent_Red),
        ORANGE("orange", R.string.accent_color_orange, R.style.ThemeOverlay_QuestLauncher_Accent_Orange),
        YELLOW("yellow", R.string.accent_color_yellow, R.style.ThemeOverlay_QuestLauncher_Accent_Yellow),
        GREEN("green", R.string.accent_color_green, R.style.ThemeOverlay_QuestLauncher_Accent_Green),
        BLUE("blue", R.string.accent_color_blue, R.style.ThemeOverlay_QuestLauncher_Accent_Blue),
        INDIGO("indigo", R.string.accent_color_indigo, R.style.ThemeOverlay_QuestLauncher_Accent_Indigo),
        VIOLET("violet", R.string.accent_color_violet, R.style.ThemeOverlay_QuestLauncher_Accent_Violet),
    }

    companion object {
        /** Maximum number of home shortcuts (mapped to keys 1..9). */
        const val MAX_SHORTCUTS = 9

        /** Default icon size: exactly fills a 3x3 grid with no scrolling. */
        const val DEFAULT_ICON_SIZE_PERCENT = 100

        private const val PREFS_NAME = "flip_launcher_prefs"
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_SHORTCUTS = "home_shortcuts"
        private const val KEY_BACK_LONGPRESS_APP = "back_longpress_app"
        private const val KEY_ICON_SIZE_PERCENT = "icon_size_percent"
        private const val KEY_RIGHT_KEY_APP = "right_key_app"
        private const val KEY_LEFT_KEY_APP = "left_key_app"
        private const val KEY_BADGE_CALLS = "badge_calls"
        private const val KEY_BADGE_MESSAGES = "badge_messages"
        private const val KEY_BADGE_OTHER = "badge_other"
        private const val KEY_BADGE_ICON_DOT = "badge_icon_dot"
        private const val KEY_ACTIVE_ICON_PACK = "active_icon_pack"
        private const val KEY_DRAWER_LIST_VIEW = "drawer_list_view"
        private const val KEY_ICON_OVERRIDE_PREFIX = "icon_override_"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val ICON_OVERRIDE_SEPARATOR = "::"
        private const val KEY_ICON_SHAPE = "icon_shape"
        private const val KEY_LEGACY_ICON_BG = "legacy_icon_background"
        private const val KEY_WRAP_DISABLED = "wrap_disabled_apps"
    }
}
