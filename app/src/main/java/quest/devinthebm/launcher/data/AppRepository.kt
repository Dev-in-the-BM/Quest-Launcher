package quest.devinthebm.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Owns the shared launchable-app catalog, package-scoped activity lists, and
 * rendered icon cache. Package events reconcile the affected records; callers
 * consume immutable catalog snapshots rather than starting independent scans.
 */
object AppRepository {
    private val preloadExecutor = Executors.newSingleThreadExecutor()
    private val iconExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(11, Comparator { first, second ->
            val a = first as IconLoadTask
            val b = second as IconLoadTask
            if (a.priority != b.priority) a.priority.compareTo(b.priority) else a.sequence.compareTo(b.sequence)
        }),
    )
    @Volatile private var preloadedApps: List<AppInfo>? = null
    @Volatile private var preloadedByKey: Map<String, AppInfo> = emptyMap()
    @Volatile private var preloadedResolveInfoByKey: Map<String, ResolveInfo> = emptyMap()
    @Volatile private var cacheGeneration = 0
    @Volatile private var preloadFuture: CompletableFuture<AppCatalog>? = null
    private val resolvedIconByKey = HashMap<String, Drawable>()
    private val iconTasks = HashMap<String, IconLoadTask>()
    private val iconCallbacks = HashMap<String, MutableList<(AppInfo) -> Unit>>()
    private var nextIconTaskSequence = 0L
    private val activitiesByPackage = HashMap<String, List<AppInfo>>()
    private val catalogListeners = CopyOnWriteArrayList<(AppCatalogSnapshot) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingPackageNames = LinkedHashSet<String>()
    private var packageReconcileActive = false

    data class AppCatalogSnapshot(
        val generation: Int,
        val apps: List<AppInfo>,
    )

    /** A stable view of the latest catalog, or null until startup hydration finishes. */
    fun getSnapshot(): AppCatalogSnapshot? {
        val generation = cacheGeneration
        val apps = preloadedApps ?: return null
        if (generation != cacheGeneration) return null
        return AppCatalogSnapshot(generation, apps)
    }

    /** Observe catalog generations; callbacks are delivered on the main thread. */
    fun observeCatalog(listener: (AppCatalogSnapshot) -> Unit): Closeable {
        catalogListeners += listener
        getSnapshot()?.let { snapshot -> mainHandler.post { if (listener in catalogListeners) listener(snapshot) } }
        return Closeable { catalogListeners -= listener }
    }

    private data class AppCatalog(
        val apps: List<AppInfo>,
        val resolveInfoByKey: Map<String, ResolveInfo>,
    )

    /** Resolve and sort launcher metadata while Home is visible, before the drawer opens. */
    @Synchronized fun preload(context: Context) {
        if (preloadedApps != null) return
        if (preloadFuture != null) return
        val appContext = context.applicationContext
        val generation = cacheGeneration
        val future = CompletableFuture<AppCatalog>()
        preloadFuture = future
        preloadExecutor.execute {
            try {
                val catalog = loadAppCatalog(appContext)
                synchronized(this) {
                    if (generation == cacheGeneration && preloadedApps == null) cacheCatalog(catalog)
                    if (preloadFuture === future) preloadFuture = null
                }
                future.complete(catalog)
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
        preloadedResolveInfoByKey = emptyMap()
        resolvedIconByKey.clear()
        activitiesByPackage.clear()
        iconCallbacks.clear()
        iconTasks.values.toList().forEach { task ->
            task.cancelled = true
            iconExecutor.queue.remove(task)
        }
        iconTasks.clear()
        preloadFuture = null
        pendingPackageNames.clear()
        packageReconcileActive = false
    }

    /** Reconcile only changed packages; event bursts are merged and stale passes are retried. */
    fun reconcilePackages(context: Context, packageNames: Set<String>) {
        if (packageNames.isEmpty() || getSnapshot() == null) {
            synchronized(this) { invalidateCache() }
            preload(context)
            return
        }
        val appContext = context.applicationContext
        synchronized(this) {
            cacheGeneration++
            pendingPackageNames += packageNames
            activitiesByPackage.keys.removeAll(packageNames)
            // In-flight icon tasks carry the old catalog generation. Cancel and
            // requeue unresolved work after the changed package rows publish.
            iconTasks.values.forEach { it.cancelled = true; iconExecutor.queue.remove(it) }
            iconTasks.clear()
            iconCallbacks.clear()
            if (packageReconcileActive) return
            packageReconcileActive = true
        }
        preloadExecutor.execute { reconcilePendingPackages(appContext) }
    }

    @Suppress("DEPRECATION")
    private fun queryPackageCatalog(context: Context, packageName: String): AppCatalog {
        val pm = context.packageManager
        val results = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
            0,
        )
        val apps = ArrayList<AppInfo>(results.size)
        val resolveInfos = LinkedHashMap<String, ResolveInfo>(results.size)
        val placeholder = pm.defaultActivityIcon.constantState
        results.forEach { resolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@forEach
            if (activity.packageName == context.packageName) return@forEach
            val key = ComponentName(activity.packageName, activity.name).flattenToString()
            apps += AppInfo(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = activity.packageName,
                activityName = activity.name,
                icon = placeholder?.newDrawable(context.resources)?.mutate()
                    ?: ColorDrawable(android.graphics.Color.TRANSPARENT),
                iconResolved = false,
            )
            resolveInfos[key] = resolveInfo
        }
        val sortedApps = apps.sortedBy { it.label.lowercase() }
        return AppCatalog(sortedApps, sortedApps.mapNotNull { app ->
            resolveInfos[app.key]?.let { app.key to it }
        }.toMap())
    }

    private fun reconcilePendingPackages(context: Context) {
        while (true) {
            val packages: Set<String>
            val generation: Int
            val baseApps: List<AppInfo>
            val baseResolveInfo: Map<String, ResolveInfo>
            synchronized(this) {
                if (pendingPackageNames.isEmpty()) {
                    packageReconcileActive = false
                    return
                }
                packages = pendingPackageNames.toSet()
                pendingPackageNames.clear()
                generation = cacheGeneration
                baseApps = preloadedApps.orEmpty()
                baseResolveInfo = preloadedResolveInfoByKey
            }
            val replacements = try {
                packages.associateWith { queryPackageCatalog(context, it) }
            } catch (error: RuntimeException) {
                synchronized(this) { packageReconcileActive = false }
                Log.w(PERF_TAG, "package-scoped reconciliation failed; falling back to full catalog scan", error)
                invalidateCache()
                preload(context)
                return
            }
            var stale = false
            synchronized(this) {
                if (generation != cacheGeneration) {
                    pendingPackageNames += packages
                    stale = true
                } else {
                    val retainedApps = baseApps.filterNot { it.packageName in packages }
                    val retainedResolveInfo = baseResolveInfo.filterKeys { key ->
                        ComponentName.unflattenFromString(key)?.packageName !in packages
                    }
                    val changedApps = replacements.values.flatMap { it.apps }
                    val changedResolveInfo = replacements.values.flatMap { it.resolveInfoByKey.entries }
                        .associate { it.key to it.value }
                    val merged = (retainedApps + changedApps).sortedBy { it.label.lowercase() }
                    val mergedResolveInfo = retainedResolveInfo + changedResolveInfo
                    packages.forEach { packageName ->
                        resolvedIconByKey.keys.removeAll { key ->
                            ComponentName.unflattenFromString(key)?.packageName == packageName
                        }
                    }
                    cacheCatalog(AppCatalog(merged, mergedResolveInfo))
                    if (pendingPackageNames.isEmpty()) packageReconcileActive = false
                }
            }
            if (stale) continue
            synchronized(this) {
                if (!packageReconcileActive) return
            }
        }
    }

    /** Re-render icons after launcher icon preferences change without rescanning app metadata. */
    fun invalidateIcons(context: Context) {
        var shouldPreload = false
        synchronized(this) {
            cacheGeneration++
            resolvedIconByKey.clear()
            iconCallbacks.clear()
            iconTasks.values.toList().forEach { task ->
                task.cancelled = true
                iconExecutor.queue.remove(task)
            }
            iconTasks.clear()
            activitiesByPackage.clear()
            val snapshotApps = preloadedApps?.map { app ->
                app.copy(
                    icon = ColorDrawable(android.graphics.Color.TRANSPARENT),
                    iconResolved = false,
                )
            }
            if (snapshotApps != null) {
                preloadedApps = snapshotApps
                preloadedByKey = snapshotApps.associateBy { it.key }
                publishCatalog()
            } else {
                preloadFuture = null
                shouldPreload = true
            }
        }
        // Consumers request icons for visible rows after receiving the new
        // generation. Hydrating the entire catalog here competes with drawer entry.
        if (shouldPreload) preload(context)
    }

    private fun cacheCatalog(catalog: AppCatalog) {
        preloadedByKey = catalog.apps.associateBy { it.key }
        preloadedResolveInfoByKey = catalog.resolveInfoByKey
        preloadedApps = catalog.apps
        publishCatalog()
    }

    private fun publishCatalog() {
        val snapshot = getSnapshot() ?: return
        mainHandler.post {
            catalogListeners.forEach { listener ->
                if (listener in catalogListeners) listener(snapshot)
            }
        }
    }

    /**
     * Returns sorted app labels immediately, with a generic icon until requested.
     * ResolveInfo objects are retained so hydration does not repeat the launcher
     * query or an ActivityInfo lookup for each icon.
     */
    @Suppress("DEPRECATION")
    private fun loadAppCatalog(context: Context): AppCatalog {
        val debugMetrics = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val startedAt = if (debugMetrics) SystemClock.elapsedRealtimeNanos() else 0L
        val pm = context.packageManager
        val queryStartedAt = if (debugMetrics) SystemClock.elapsedRealtimeNanos() else 0L
        val queryResults = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )
        val queryNanos = if (debugMetrics) SystemClock.elapsedRealtimeNanos() - queryStartedAt else 0L
        var labelNanos = 0L
        val placeholderState = pm.defaultActivityIcon.constantState
        val apps = ArrayList<AppInfo>(queryResults.size)
        val resolveInfoByKey = LinkedHashMap<String, ResolveInfo>(queryResults.size)
        queryResults.forEach { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@forEach
            if (activityInfo.packageName == context.packageName) return@forEach
            val key = ComponentName(activityInfo.packageName, activityInfo.name).flattenToString()
            val labelStartedAt = if (debugMetrics) SystemClock.elapsedRealtimeNanos() else 0L
            val label = resolveInfo.loadLabel(pm).toString()
            if (debugMetrics) labelNanos += SystemClock.elapsedRealtimeNanos() - labelStartedAt
            val placeholder = placeholderState?.newDrawable(context.resources)?.mutate()
                ?: ColorDrawable(android.graphics.Color.TRANSPARENT)
            apps += AppInfo(
                label = label,
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                icon = placeholder,
                iconResolved = false,
            )
            resolveInfoByKey[key] = resolveInfo
        }
        val sortStartedAt = if (debugMetrics) SystemClock.elapsedRealtimeNanos() else 0L
        val sortedApps = apps.sortedBy { it.label.lowercase() }
        val sortedResolveInfo = sortedApps.mapNotNull { app ->
            resolveInfoByKey[app.key]?.let { app.key to it }
        }.toMap()
        val sortNanos = if (debugMetrics) SystemClock.elapsedRealtimeNanos() - sortStartedAt else 0L
        if (debugMetrics) {
            fun ms(nanos: Long) = nanos / 1_000_000.0
            Log.i(
                PERF_TAG,
                "app catalog ${sortedApps.size}/${queryResults.size} entries total=${ms(SystemClock.elapsedRealtimeNanos() - startedAt)}ms " +
                    "query=${ms(queryNanos)}ms labels=${ms(labelNanos)}ms sort=${ms(sortNanos)}ms",
            )
        }
        return AppCatalog(sortedApps, sortedResolveInfo)
    }

    /** Every launchable app except this launcher itself, sorted by label. */
    @Suppress("DEPRECATION") // int-flags overload kept for minSdk 21 compatibility
    fun getAllApps(context: Context): List<AppInfo> {
        while (true) {
            getSnapshot()?.let { return it.apps }
            val generation = cacheGeneration
            val catalog = try {
                preloadFuture?.join() ?: loadAppCatalog(context)
            } catch (_: RuntimeException) {
                loadAppCatalog(context)
            }
            val result = synchronized(this) {
                if (generation != cacheGeneration) null
                else {
                    if (preloadedApps == null) cacheCatalog(catalog)
                    preloadedApps ?: catalog.apps
                }
            }
            if (result != null) return result
        }
    }

    /** Apps shown to the user (hidden ones removed). */
    fun getCachedVisibleApps(prefs: LauncherPrefs): List<AppInfo>? {
        val generation = cacheGeneration
        val apps = preloadedApps ?: return null
        val hidden = prefs.getHiddenKeys()
        if (generation != cacheGeneration) return null
        return apps.filter { it.key !in hidden }
    }

    /** Apps shown to the user (hidden ones removed). */
    fun getVisibleApps(context: Context, prefs: LauncherPrefs): List<AppInfo> {
        while (true) {
            val generation = cacheGeneration
            val apps = preloadedApps ?: run {
                val pending = preloadFuture
                val catalog = try {
                    pending?.join() ?: loadAppCatalog(context)
                } catch (_: RuntimeException) {
                    loadAppCatalog(context)
                }
                synchronized(this) {
                    if (generation == cacheGeneration && preloadedApps == null) cacheCatalog(catalog)
                    preloadedApps ?: catalog.apps
                }
            }
            if (generation != cacheGeneration) continue
            val hidden = prefs.getHiddenKeys()
            if (generation != cacheGeneration) continue
            return apps.filter { it.key !in hidden }
        }
    }

    /**
     * Schedules icon loads away from the UI thread. Lower numeric priorities run
     * first; a later visible-page request can promote an icon still in the queue.
     */
    fun requestIcons(
        context: Context,
        apps: List<AppInfo>,
        priority: Int = BACKGROUND_ICON_PRIORITY,
        onIconReady: (AppInfo) -> Unit,
    ) {
        val alreadyResolved = ArrayList<AppInfo>()
        synchronized(this) {
            val generation = cacheGeneration
            apps.filterNot { it.iconResolved }.forEach { app ->
                val cachedIcon = resolvedIconByKey[app.key]
                if (cachedIcon != null) {
                    alreadyResolved += app.copy(icon = cachedIcon, iconResolved = true)
                    return@forEach
                }
                val callbacks = iconCallbacks.getOrPut(app.key) { ArrayList() }
                if (callbacks.none { it === onIconReady }) callbacks += onIconReady
                val pending = iconTasks[app.key]
                if (pending != null) {
                    if (priority < pending.priority && iconExecutor.queue.remove(pending)) {
                        pending.priority = priority
                        iconExecutor.execute(pending)
                    } else {
                        pending.priority = minOf(pending.priority, priority)
                    }
                } else {
                    val task = IconLoadTask(
                        context = context.applicationContext,
                        app = app,
                        resolveInfo = preloadedResolveInfoByKey[app.key],
                        generation = generation,
                        priority = priority,
                        sequence = nextIconTaskSequence++,
                    )
                    iconTasks[app.key] = task
                    iconExecutor.execute(task)
                }
            }
        }
        alreadyResolved.forEach(onIconReady)
    }

    /** Detach a screen callback while allowing already-started work to warm the cache. */
    @Synchronized fun cancelIconRequests(onIconReady: (AppInfo) -> Unit) {
        iconCallbacks.values.forEach { callbacks -> callbacks.removeAll { it === onIconReady } }
    }

    private class IconLoadTask(
        private val context: Context,
        private val app: AppInfo,
        private val resolveInfo: ResolveInfo?,
        private val generation: Int,
        @Volatile var priority: Int,
        val sequence: Long,
    ) : Runnable {
        @Volatile var cancelled = false

        override fun run() {
            if (cancelled) return
            val debugMetrics = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            val startedAt = if (debugMetrics) SystemClock.elapsedRealtimeNanos() else 0L
            var paletteNanos = 0L
            val hydrated = try {
                val pm = context.packageManager
                val sourceIcon = resolveInfo?.loadIcon(pm) ?: run {
                    val component = ComponentName(app.packageName, app.activityName)
                    pm.getActivityInfo(component, 0).loadIcon(pm)
                }
                val icon = resolveIcon(
                    context,
                    app.key,
                    sourceIcon,
                    includePackageOverride = false,
                    onLegacyPaletteMeasured = if (debugMetrics) ({ nanos -> paletteNanos += nanos }) else null,
                )
                // The drawer may request this color during a bind/focus callback.
                // Warm its Palette cache here so icon completion never moves that
                // sampling work onto the main thread.
                NotificationDotColor.forIcon(app.key, icon)
                app.copy(icon = icon, iconResolved = true)
            } catch (error: Exception) {
                if (debugMetrics) Log.w(PERF_TAG, "icon hydration failed for ${app.key}", error)
                app.copy(iconResolved = true)
            }
            var callbacks: List<(AppInfo) -> Unit> = emptyList()
            synchronized(AppRepository) {
                if (generation != cacheGeneration || cancelled) {
                    if (iconTasks[app.key] === this) iconTasks.remove(app.key)
                    return
                }
                resolvedIconByKey[app.key] = hydrated.icon
                preloadedApps = preloadedApps?.map { cached ->
                    if (cached.key == app.key) hydrated else cached
                }
                preloadedByKey = preloadedByKey + (app.key to hydrated)
                activitiesByPackage[app.packageName]?.let { activities ->
                    activitiesByPackage[app.packageName] = activities.map { cached ->
                        if (cached.key == app.key) hydrated else cached
                    }
                }
                callbacks = iconCallbacks.remove(app.key).orEmpty()
                if (iconTasks[app.key] === this) iconTasks.remove(app.key)
            }
            if (debugMetrics) {
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                val paletteMs = paletteNanos / 1_000_000.0
                Log.i(
                    PERF_TAG,
                    "icon hydration ${app.key} total=${elapsedMs}ms palette=${paletteMs}ms",
                )
            }
            callbacks.forEach { callback ->
                try {
                    callback(hydrated)
                } catch (error: RuntimeException) {
                    if (debugMetrics) Log.w(PERF_TAG, "icon callback failed for ${app.key}", error)
                }
            }
        }
    }

    /**
     * Every launchable, exported activity declared by [packageName] — the source
     * for the activity picker, so a shortcut can target a deep screen rather than
     * only an app's main entry point.
     */
    fun getActivities(context: Context, packageName: String): List<AppInfo> {
        synchronized(this) { activitiesByPackage[packageName]?.let { return it } }
        val pm = context.packageManager
        val placeholderState = pm.defaultActivityIcon.constantState
        val result = try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            (info.activities ?: emptyArray()).asSequence()
                .filter { it.exported && it.enabled }
                .map { ai ->
                    val key = ComponentName(packageName, ai.name).flattenToString()
                    val cachedIcon = synchronized(this) { resolvedIconByKey[key] }
                    AppInfo(
                        label = ai.loadLabel(pm).toString(),
                        packageName = packageName,
                        activityName = ai.name,
                        icon = cachedIcon
                            ?: placeholderState?.newDrawable(context.resources)?.mutate()
                            ?: ColorDrawable(android.graphics.Color.TRANSPARENT),
                        iconResolved = cachedIcon != null,
                    )
                }
                .sortedBy { it.activityName }
                .toList()
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }
        synchronized(this) { activitiesByPackage[packageName] = result }
        return result
    }

    /**
     * Resolve a stored shortcut [key] (any activity component) to a displayable
     * [AppInfo], or null if it no longer exists.
     */
    fun resolveComponent(context: Context, key: String): AppInfo? {
        preloadedByKey[key]?.takeIf { it.iconResolved }?.let { return it }
        val component = ComponentName.unflattenFromString(key) ?: return null
        val pm = context.packageManager
        val generation = cacheGeneration
        return try {
            @Suppress("DEPRECATION")
            val ai = pm.getActivityInfo(component, 0)
            val label = preloadedByKey[key]?.label ?: ai.loadLabel(pm).toString()
            val cachedIcon = synchronized(this) { resolvedIconByKey[key] }
            val icon = cachedIcon ?: resolveIcon(context, key, ai.loadIcon(pm))
            val resolved = AppInfo(
                label = label,
                packageName = component.packageName,
                activityName = component.className,
                icon = icon,
            )
            synchronized(this) {
                if (generation == cacheGeneration) {
                    resolvedIconByKey[key] = icon
                    preloadedApps = preloadedApps?.map { if (it.key == key) resolved else it }
                    preloadedByKey = preloadedByKey + (key to resolved)
                }
            }
            resolved
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Resolve a component label without forcing icon loading or shape rendering. */
    fun resolveLabel(context: Context, key: String): String? {
        preloadedByKey[key]?.label?.let { return it }
        val component = ComponentName.unflattenFromString(key) ?: return null
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(component, 0).loadLabel(context.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
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
        onLegacyPaletteMeasured: ((Long) -> Unit)? = null,
    ): Drawable {
        val prefs = LauncherPrefs(context)
        val raw = rawIcon(context, prefs, componentKey, fallback, includePackageOverride)
        return IconShapeRenderer.render(
            context = context,
            source = raw,
            shape = prefs.getIconShape(),
            wrapEnabled = prefs.isIconWrapEnabled(componentKey),
            legacyBackgroundEnabled = prefs.isLegacyIconBackgroundEnabled(),
            onLegacyPaletteMeasured = onLegacyPaletteMeasured,
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
    const val VISIBLE_ICON_PRIORITY = 0
    const val BACKGROUND_ICON_PRIORITY = 10
}
