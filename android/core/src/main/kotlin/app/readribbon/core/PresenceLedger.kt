package app.readribbon.core

/**
 * Who is in a room, as the server's presence says it: one key per person
 * (the join's `presence.key`), and under it one meta per connection that
 * person has open — two phones, or a phone and the socket it is replacing.
 *
 * The rooms used to keep one meta per person and drop the person on any
 * leave. But a leave names a connection, not a person: a second device
 * closing, or a reconnect whose old socket timed out after the new one had
 * joined, took someone out of the room who was still in it. The phone they
 * were following then believed nobody followed it and stopped sending its
 * line, and nothing started it again. This keeps each connection by its
 * `phx_ref` and drops a person only when their last one goes — the rule
 * Phoenix's own client keeps. The Swift twin is `PresenceLedger.swift`.
 */
class PresenceLedger<Meta> {
    data class Connection<Meta>(
        /** The server's name for this connection's latest track; null only from a server that sends none. */
        val ref: String?,
        val meta: Meta,
    )

    private val people = LinkedHashMap<String, List<Connection<Meta>>>()

    val keys: Set<String> get() = people.keys

    /** Each person's connections, oldest first. */
    fun connections(key: String): List<Connection<Meta>> = people[key].orEmpty()

    /** The whole room, as a join's `presence_state` says it. */
    fun reset(state: Map<String, List<Connection<Meta>>>) {
        people.clear()
        for ((key, connections) in state) if (connections.isNotEmpty()) people[key] = connections
    }

    fun clear() = people.clear()

    /**
     * A `presence_diff`: joins first, then leaves, as Phoenix applies them.
     * A track that changes what a connection says arrives as a leave of its
     * old ref and a join of its new one, so either order lands the same.
     */
    fun apply(
        joins: Map<String, List<Connection<Meta>>>,
        leaves: Map<String, List<Connection<Meta>>>,
    ) {
        for ((key, joined) in joins) {
            if (joined.isEmpty()) continue
            val refs = joined.mapNotNull { it.ref }.toSet()
            val kept = people[key].orEmpty().filter { it.ref != null && it.ref !in refs }
            people[key] = kept + joined
        }
        for ((key, left) in leaves) {
            val current = people[key] ?: continue
            val refs = left.mapNotNull { it.ref }.toSet()
            // A leave that names no connection can only mean the person.
            val kept = if (refs.isEmpty()) emptyList() else current.filter { it.ref == null || it.ref !in refs }
            if (kept.isEmpty()) people.remove(key) else people[key] = kept
        }
    }

    /** What the person last said, on whichever connection said it last. */
    fun latest(key: String): Meta? = people[key]?.lastOrNull()?.meta

    /** The most recent of the person's connections whose meta passes. */
    fun latest(key: String, where: (Meta) -> Boolean): Meta? =
        people[key]?.lastOrNull { where(it.meta) }?.meta

    /** Every meta in the room, every connection of every person. */
    val allMetas: List<Meta> get() = people.values.flatMap { list -> list.map { it.meta } }
}
