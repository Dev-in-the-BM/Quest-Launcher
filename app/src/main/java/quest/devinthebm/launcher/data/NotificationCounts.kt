package quest.devinthebm.launcher.data

/**
 * Live counts of active notifications by category, fed by
 * [quest.devinthebm.launcher.service.NotificationCountService]. A simple in-memory
 * singleton (not persisted) since the listener service recomputes on every
 * change and re-syncs as soon as it reconnects.
 */
object NotificationCounts {

    var calls: Int = 0
        private set
    var messages: Int = 0
        private set
    var other: Int = 0
        private set

    /** Packages with at least one active notification, backing the per-app icon dot. */
    var packagesWithNotifications: Set<String> = emptySet()
        private set

    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(calls: Int, messages: Int, other: Int, packagesWithNotifications: Set<String>) {
        this.calls = calls
        this.messages = messages
        this.other = other
        this.packagesWithNotifications = packagesWithNotifications
        listeners.toList().forEach { it() }
    }
}
