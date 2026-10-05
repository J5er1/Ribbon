import Foundation

/// Who is in a room, as the server's presence says it: one key per person
/// (the join's `presence.key`), and under it one meta per connection that
/// person has open — two phones, or a phone and the socket it is replacing.
///
/// The rooms used to keep one meta per person and drop the person on any
/// leave. But a leave names a connection, not a person: a second device
/// closing, or a reconnect whose old socket timed out after the new one had
/// joined, took someone out of the room who was still in it. The phone they
/// were following then believed nobody followed it and stopped sending its
/// line, and nothing started it again. This keeps each connection by its
/// `phx_ref` and drops a person only when their last one goes — the rule
/// Phoenix's own client keeps.
public struct PresenceLedger<Meta> {
    public struct Connection {
        /// The server's name for this connection's latest track; nil only
        /// from a server that sends none.
        public let ref: String?
        public let meta: Meta

        public init(ref: String?, meta: Meta) {
            self.ref = ref
            self.meta = meta
        }
    }

    /// Each person's connections, oldest first.
    public private(set) var people: [String: [Connection]] = [:]

    public init() {}

    public var keys: Dictionary<String, [Connection]>.Keys { people.keys }

    /// The whole room, as a join's `presence_state` says it.
    public mutating func reset(to state: [String: [Connection]]) {
        people = state.filter { !$0.value.isEmpty }
    }

    public mutating func removeAll() {
        people.removeAll()
    }

    /// A `presence_diff`: joins first, then leaves, as Phoenix applies them.
    /// A track that changes what a connection says arrives as a leave of its
    /// old ref and a join of its new one, so either order lands the same.
    public mutating func apply(
        joins: [String: [Connection]], leaves: [String: [Connection]]
    ) {
        for (key, joined) in joins where !joined.isEmpty {
            let refs = Set(joined.compactMap(\.ref))
            let kept = (people[key] ?? []).filter { $0.ref.map { !refs.contains($0) } ?? false }
            people[key] = kept + joined
        }
        for (key, left) in leaves {
            guard let current = people[key] else { continue }
            let refs = Set(left.compactMap(\.ref))
            // A leave that names no connection can only mean the person.
            let kept = refs.isEmpty ? [] : current.filter { $0.ref.map { !refs.contains($0) } ?? true }
            if kept.isEmpty {
                people.removeValue(forKey: key)
            } else {
                people[key] = kept
            }
        }
    }

    /// What the person last said, on whichever connection said it last.
    public func latest(_ key: String) -> Meta? {
        people[key]?.last?.meta
    }

    /// The most recent of the person's connections whose meta passes.
    public func latest(_ key: String, where isIt: (Meta) -> Bool) -> Meta? {
        people[key]?.last(where: { isIt($0.meta) })?.meta
    }

    /// Every meta in the room, every connection of every person.
    public var allMetas: [Meta] {
        people.values.flatMap { $0.map(\.meta) }
    }
}
