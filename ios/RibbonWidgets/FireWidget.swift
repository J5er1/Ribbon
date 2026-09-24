import SwiftUI
import WidgetKit
import RibbonCore

// The fire (S24). Small: the fire at its state, on the unlit ground, and the
// book's name in small caps — nothing else. Lock screen: the fire alone in
// the circle; the book and its state in small caps inline.

struct FireEntry: TimelineEntry {
    var date: Date
    /// Nil when the room has no book open: the unlit ground, and nothing on it.
    var book: String?
    var state: FireState?
    var scale: FireScale
    var coalDepth: Double
}

struct FireTimeline: TimelineProvider {
    func placeholder(in context: Context) -> FireEntry {
        FireEntry(date: Date(), book: "Mark", state: .burning, scale: .medium, coalDepth: 0.4)
    }

    func getSnapshot(in context: Context, completion: @escaping (FireEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
        } else {
            completion(entries(from: Date())[0])
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FireEntry>) -> Void) {
        completion(Timeline(entries: entries(from: Date()), policy: .atEnd))
    }

    /// A fire cools on its own. The timeline carries it an hour at a time,
    /// asking the room's own engine what the state is at each hour, so the
    /// widget reports what the room would — and the app reloads it the moment
    /// anybody feeds it.
    private func entries(from now: Date) -> [FireEntry] {
        guard let snapshot = RoomSnapshot.read(),
              let book = snapshot.book, let handiwork = snapshot.handiwork
        else {
            return [FireEntry(date: now, book: nil, state: nil, scale: .medium, coalDepth: 0)]
        }
        return (0..<12).map { hour in
            let date = now.addingTimeInterval(Double(hour) * 3600)
            return FireEntry(
                date: date, book: book,
                state: handiwork.state(at: date, bankedIntervals: snapshot.banked),
                scale: handiwork.scale, coalDepth: handiwork.coalDepth)
        }
    }
}

struct FireWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "bible.ribbon.fire", provider: FireTimeline()) { entry in
            FireWidgetView(entry: entry)
        }
        .configurationDisplayName(WidgetCopy.fireName)
        .description(WidgetCopy.fireDescription)
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryInline])
        .contentMarginsDisabled()
    }
}

struct FireWidgetView: View {
    @Environment(\.widgetFamily) private var family
    var entry: FireEntry

    var body: some View {
        Group {
            switch family {
            case .accessoryCircular: circle
            case .accessoryInline: inline
            default: small
            }
        }
        .containerBackground(for: .widget) {
            // The unlit ground (§00) on the home screen; the lock screen
            // draws its own.
            if family == .systemSmall { Palette.ground }
        }
    }

    private var small: some View {
        VStack(spacing: 6) {
            if let state = entry.state, let book = entry.book {
                fire(state)
                    .padding(.horizontal, 14)
                Text(book)
                    .font(WidgetType.smallCaps(13))
                    .kerning(1)
                    .foregroundStyle(Palette.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .padding(.bottom, 16)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }

    private var circle: some View {
        Group {
            if let state = entry.state {
                fire(state).padding(4)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }

    private var inline: some View {
        // The inline slot sets its own face; the words are what is ours.
        Group {
            if let state = entry.state, let book = entry.book {
                Text("\(book) · \(state.displayName)")
                    .font(.body.smallCaps())
            } else {
                Text("")
            }
        }
    }

    /// The room's own fire, held at one instant: the painter is the one the
    /// room uses, and a widget is a still.
    private func fire(_ state: FireState) -> some View {
        Canvas { context, size in
            FirePainter.draw(
                in: &context, size: size, time: 402.7,
                state: state, scale: entry.scale, coalDepth: entry.coalDepth)
        }
    }

    private var label: String {
        guard let state = entry.state, let book = entry.book else { return "" }
        return "\(WidgetCopy.fireIs(state.displayName)) \(book)."
    }
}
