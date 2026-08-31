import SwiftUI
import RibbonCore

// S14 — rooms: switching between rooms, and making a new one. One row per
// room: its name, its members' portraits, and its fire drawn small in its
// current state. "You" at the bottom is how settings is reached.

struct RoomsSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var onSwitch: (UUID) -> Void
    var onStartRoom: () -> Void
    var onYou: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(model.state.rooms) { room in
                        roomRow(room)
                    }
                }
                .padding(.top, 26)
                .padding(.horizontal, 22)
            }
            .scrollIndicators(.hidden)

            VStack(alignment: .leading, spacing: 18) {
                QuietControl(title: Copy.startARoomControl, action: onStartRoom)
                Button(action: onYou) {
                    HStack(spacing: 10) {
                        PortraitView(
                            person: model.me, ink: nil, size: 26,
                            image: model.me.flatMap { model.portrait($0.id) })
                        SmallCaps(Copy.you, size: 13, color: Palette.text.opacity(0.8))
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(22)
        }
        .room()
        .presentationBackground(Palette.ground)
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
                    .frame(width: 2, height: 34)
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.displayName(of: room))
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                    HStack(spacing: -5) {
                        ForEach(model.members(of: room)) { membership in
                            PortraitView(
                                person: model.person(membership.personID),
                                ink: membership.ink,
                                size: 18,
                                image: model.portrait(membership.personID))
                        }
                    }
                }
                Spacer()
                if room.isPaused {
                    SmallCaps(Copy.paused, size: 11)
                }
                if let reading = model.openReading(in: room) {
                    // A paused room's fire is drawn in whatever state it
                    // actually holds — never banked by a lapse (S14).
                    CampfireGlyph(
                        state: model.fireState(of: reading),
                        scale: reading.handiwork.scale,
                        height: 22)
                }
            }
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }
}
