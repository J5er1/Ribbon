import ActivityKit
import SwiftUI
import WidgetKit

// "Ruth is reading Mark" (S24): their portrait, and the sentence. It runs
// while someone else in the room is reading and ends when they leave —
// started, kept current and ended by push, because the lock screen is where
// this is seen and the app is not running there.
//
// It is the build book's "best single argument for the product on a lock
// screen", so it says the one thing and nothing more: no time, no chapter,
// no how-long. If the reader's phone stops saying it — a phone that died
// mid-chapter never says it has left — the line goes into the past tense
// rather than go on claiming a presence nobody can vouch for (§4.2).

struct ReadingLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ReadingActivityAttributes.self) { context in
            ReadingLine(context: context, portraitSize: 36, textSize: 17)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .activityBackgroundTint(Palette.ground)
                .activitySystemActionForegroundColor(Palette.text)
                .widgetURL(roomURL(context.attributes.roomID))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Portrait(attributes: context.attributes, size: 36)
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(line(context))
                        .font(WidgetType.ui(17))
                        .foregroundStyle(Palette.text)
                        .lineLimit(2)
                }
            } compactLeading: {
                Portrait(attributes: context.attributes, size: 22)
            } compactTrailing: {
                Text(context.state.book)
                    .font(WidgetType.smallCaps(13))
                    .foregroundStyle(Palette.muted)
                    .lineLimit(1)
            } minimal: {
                Portrait(attributes: context.attributes, size: 22)
            }
            .widgetURL(roomURL(context.attributes.roomID))
        }
    }
}

private func line(_ context: ActivityViewContext<ReadingActivityAttributes>) -> String {
    let name = context.attributes.readerName
    let book = context.state.book
    return context.isStale ? WidgetCopy.wasReading(name, book) : WidgetCopy.reading(name, book)
}

private struct ReadingLine: View {
    var context: ActivityViewContext<ReadingActivityAttributes>
    var portraitSize: CGFloat
    var textSize: CGFloat

    var body: some View {
        HStack(spacing: 14) {
            Portrait(attributes: context.attributes, size: portraitSize)
            Text(line(context))
                .font(WidgetType.ui(textSize))
                .foregroundStyle(context.isStale ? Palette.muted : Palette.text)
                .lineLimit(2)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(line(context))
    }
}

/// Their face, from the copy the app keeps in the group's container; their
/// initial on a raised circle when there is none, as the room draws it.
private struct Portrait: View {
    var attributes: ReadingActivityAttributes
    var size: CGFloat

    var body: some View {
        Group {
            if let url = RoomSnapshot.portraitURL(for: attributes.readerID),
               let image = UIImage(contentsOfFile: url.path()) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Text(String(attributes.readerName.prefix(1)))
                    .font(WidgetType.ui(size * 0.46))
                    .foregroundStyle(Palette.text)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Palette.raised)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}
