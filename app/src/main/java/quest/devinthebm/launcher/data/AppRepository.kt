package quest.devinthebm.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Reads launchable apps and individual activities from [PackageManager] and
 * resolves the launch intents the launcher fires. Stateless: callers re-query so
 * the list is always fresh after installs/uninstalls.
 */
object AppRepository {
    private val preloadExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var preloadedApps: List<AppInfo>? = null
    @Volatile private var preloadedByKey: Map<String, AppInfo> = emptyMap()
    @Volatile private var cacheGeneration = 0
    @Volatile private var preloadFuture: CompletableFuture<List<AppInfo>>? = null

    /** Resolve app labels and icons while Home is visible, before the drawer opens. */
    @Synchronized fun preload(context: Context) {
        if (preloadedApps != null) return
        if (preloadFuture != null) return
        val appContext = context.applicationContext
        val generation = cacheGeneration
        val future = CompletableFuture<List<AppInfo>>()
        preloadFuture = future
        preloadExecutor.execute {
            try {
                val apps = getAllApps(appContext)
                synchronized(this) {
                    if (generation == cacheGeneration && preloadedApps == null) cacheApps(apps)
                    if (preloadFuture === future) preloadFuture = null
                }
                future.complete(apps)
            } catch (error: Throwable) {
                synchronized(this) {
                    if (preloadFuture === future) preloadFuture = null
                }
                future.completeExceptionally(error)
            }
        }
    }

    @Synchronized fun invalidateCache() {
        cacheGeneration++
        preloadedApps = null
        preloadedByKey = emptyMap()
        preloadFuture = null
    }

    private fun cacheApps(apps: List<AppInfo>) {
        preloadedByKey = apps.associateBy { it.key }
        preloadedApps = apps
    }

    /** Every launchable app except this launcher itself, sorted by label. */
    @Suppress("DEPRECATION") // int-flags overload kept for minSdk 21 compatibility
    fun getAllApps(context: Context): List<AppInfo> {
        val startedAt = SystemClock.elapsedRealtime()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val self = context.packageName
        val apps = pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == self) return@mapNotNull null
                val key = ComponentName(ai.packageName, ai.name).flattenToString()
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    activityName = ai.name,
                    // This result is already the package's launcher entry;
                    // resolving its launch intent again for the deep-activity
                    // icon fallback only adds another PackageManager lookup.
                    icon = resolveIcon(context, key, ri.loadIcon(pm), includePackageOverride = false),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.i(PERF_TAG, "app scan ${apps.size} entries in ${SystemClock.elapsedRealtime() - startedAt}ms")
        }
        return apps
    }

    /** Apps shown to the user (hidden ones removed). */
    fun getVisibleApps(context: Context, prefs: LauncherPrefs): List<AppInfo> {
        val apps = preloadedApps ?: run {
            val generation = cacheGeneration
            val pending = preloadFuture
            val loaded = try {
                pending?.join() ?: getAllApps(context)
            } catch (_: RuntimeException) {
                getAllApps(context)
            }
            synchronized(this) {
                if (generation == cacheGeneration && preloadedApps == null) cacheApps(loaded)
            }
            preloadedApps ?: loaded
        }
        val hidden = prefs.getHiddenKeys()
        return apps.filter { it.key !in hidden }
    }

    /**
     * Every launchable, exported activity declared by [packageName] — the source
     * for the activity picker, so a shortcut can target a deep screen rather than
     * only an app's main entry point.
     */
    fun getActivities(context: Context, packageName: String): List<AppInfo> {
        val pm = context.packageManager
        return try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            (info.activities ?: emptyArray()).asSequence()
                .filter { it.exported && it.enabled }
                .map { ai ->
                    val key = ComponentName(packageName, ai.name).flattenToString()
                    AppInfo(
                        label = ai.loadLabel(pm).toString(),
                        packageName = packageName,
                        activityName = ai.name,
                        icon = resolveIcon(context, key, ai.loadIcon(pm)),
                    )
                }
                .sortedBy { it.activityName }
                .toList()
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }

    /**
     * Resolve a stored shortcut [key] (any activity component) to a displayable
     * [AppInfo], or null if it no longer exists.
     */
    fun resolveComponent(context: Context, key: String): AppInfo? {
        preloadedByKey[key]?.let { return it }
        val component = ComponentName.unflattenFromString(key) ?: return null
        val pm = context.packageManager
        return try {
            @Suppress("DEPRECATION")
            val ai = pm.getActivityInfo(component, 0)
            AppInfo(
                label = ai.loadLabel(pm).toString(),
                packageName = component.packageName,
                activityName = component.className,
                icon = resolveIcon(context, key, ai.loadIcon(pm)),
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Swaps in a per-app icon override if one is set, else the active icon
     * pack's mapping for [componentKey] if it has one, else [fallback] — then
     * masks the result into the user's chosen icon shape.
     */
    private fun resolveIcon(
        context: Context,
        componentKey: String,
        fallback: Drawable,
        includePackageOverride: Boolean = true,
    ): Drawable {
        val prefs = LauncherPrefs(context)
        val raw = rawIcon(context, prefs, componentKey, fallback, includePackageOverride)
        return IconShapeRenderer.render(
            context = context,
            source = raw,
            shape = prefs.getIconShape(),
            wrapEnabled = prefs.isIconWrapEnabled(componentKey),
            legacyBackgroundEnabled = prefs.isLegacyIconBackgroundEnabled(),
        )
    }

    private fun rawIcon(
        context: Context,
        prefs: LauncherPrefs,
        componentKey: String,
        fallback: Drawable,
        includePackageOverride: Boolean,
    ): Drawable {
        val override = prefs.getIconOverride(componentKey)
            ?: if (includePackageOverride) packageIconOverride(context, prefs, componentKey) else null
        override?.let { (pack, name) ->
            val drawable = if (pack == BuiltInIcons.PACK_ID) {
                BuiltInIcons.loadIcon(context, name)
            } else {
                IconPackRepository.loadIcon(context, pack, name)
            }
            drawable?.let { return it }
        }
        val activePack = prefs.getActiveIconPack() ?: return fallback
        val name = IconPackRepository.iconNameFor(context, activePack, componentKey) ?: return fallback
        return IconPackRepository.loadIcon(context, activePack, name) ?: fallback
    }

    /**
     * Falls back to the icon override set on the app's main entry point, so an
     * activity picked via [ActivityPickerActivity] (a deep screen, not the app's
     * main icon) still picks up a custom icon when used as a Home shortcut.
     */
    private fun packageIconOverride(context: Context, prefs: LauncherPrefs, componentKey: String): Pair<String, String>? {
        val packageName = ComponentName.unflattenFromString(componentKey)?.packageName ?: return null
        val mainKey = context.packageManager.getLaunchIntentForPackage(packageName)?.component?.flattenToString()
            ?: return null
        if (mainKey == componentKey) return null
        return prefs.getIconOverride(mainKey)
    }

    /**
     * Intent that launches the activity identified by [key]. Uses a bare explicit
     * component (no MAIN/LAUNCHER category) so it works for any exported activity,
     * not just an app's home-screen entry.
     */
    fun launchIntentFor(key: String): Intent? {
        val component = ComponentName.unflattenFromString(key) ?: return null
        // RESET_TASK_IF_NEEDED used to ride along with NEW_TASK here, but combined
        // with our own same-affinity activities (e.g. Launcher Settings, which is
        // exported so it can appear in the drawer) it makes the system swallow the
        // launch instead of pushing the activity onto the current task.
        return Intent()
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private const val PERF_TAG = "QuestPerf"
}
