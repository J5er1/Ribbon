import SwiftUI
import RibbonCore

// S14 — rooms: switching between rooms, and making a new one. One row per
// room: its name, its members' portraits, and its fire drawn small in its
// current state. "You" at the bottom is how settings is reached. Rooms
// you've left stay beneath, for their shelves (§6.8).

struct RoomsSheet: View {
    @Environment(\.appModel) private var model
    @Environment(\.dismiss) private var dismiss
    var onSwitch: (UUID) -> Void
    var onStartRoom: () -> Void
    var onYou: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Spacer()
                BackControl(title: Copy.close) { dismiss() }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(model.liveRooms) { room in
                        roomRow(room)
                    }
                    if !model.departedRooms.isEmpty {
                        SectionHeader(Copy.roomsYouLeft)
                            .padding(.top, 18)
                        ForEach(model.departedRooms) { room in
                            roomRow(room)
                        }
                    }
                }
                .padding(.top, 10)
                .padding(.horizontal, 22)
            }
            .scrollIndicators(.hidden)

            VStack(alignment: .leading, spacing: 18) {
                QuietControl(title: Copy.startARoomControl, action: onStartRoom)
                Button(action: onYou) {
                    HStack(spacing: 10) {
                        PortraitView(
                            person: model.me, ink: model.me.map { Ink.stable(for: $0.id) }, size: 26,
                            image: model.me.flatMap { model.portrait($0.id) })
                        SmallCaps(Copy.you, size: 13, color: Palette.text.opacity(0.8))
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Copy.you)
                .accessibilityHint(Copy.yourAccountAndSettings)
            }
            .padding(22)
        }
        .ribbonSheet()
        .presentationDetents([.medium, .large])
    }

    private func roomRow(_ room: Room) -> some View {
        let isCurrent = room.id == model.currentRoom?.id
        return Button {
            onSwitch(room.id)
            dismiss()
        } label: {
            HStack(spacing: 12) {
                // The current room marked with a chartreuse hairline.
                Rectangle()
                    .fill(isCurrent ? Palette.chartreuse : .clear)
                    .frame(width: 1, height: 34)
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.displayName(of: room))
                        .font(RibbonType.ui(16))
                        .foregroundStyle(room.isDeparted ? Palette.muted : Palette.text)
                    HStack(spacing: -5) {
                        ForEach(model.members(of: room)) { membership in
                            PortraitView(
                                person: model.person(membership.personID),
                                ink: model.inkForDisplay(membership.personID, in: room.id),
                                size: 18,
                                image: model.portrait(membership.personID))
                        }
                    }
                }
                Spacer()
                if room.isPaused {
                    SmallCaps(Copy.paused, size: 11)
                }
                if !room.isDeparted, let reading = model.openReading(in: room) {
                    // A paused room's fire is drawn in whatever state it
                    // actually holds — never banked by a lapse (S14).
                    CampfireGlyph(
                        state: model.fireState(of: reading),
                        scale: reading.handiwork.scale,
                        height: 18)
                }
            }
            .padding(.vertical, 8)
            .frame(minHeight: 50)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(roomLabel(room, isCurrent: isCurrent))
        .accessibilityAddTraits(isCurrent ? [.isSelected] : [])
    }

    /// The row's label: the room, its fire's state, and whether it is the
    /// current one — said, not colored (§11).
    private func roomLabel(_ room: Room, isCurrent: Bool) -> String {
        var parts = [model.displayName(of: room)]
        if isCurrent { parts.append(Copy.currentRoom) }
        if room.isPaused { parts.append(Copy.paused) }
        if room.isDeparted { parts.append(Copy.roomsYouLeft) }
        if let reading = model.openReading(in: room), let book = Bible.book(id: reading.bookID) {
            parts.append(book.name)
            parts.append(Copy.fireIs(model.fireState(of: reading).displayName))
        }
        return parts.joined(separator: ", ")
    }
}
