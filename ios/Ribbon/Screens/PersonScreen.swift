import SwiftUI
import RibbonCore

// S12 — a person: someone in this room. Not a profile — there is nothing to
// follow, nothing to score. No join date, no activity, no counts of
// anything they've done.

struct PersonScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let personID: UUID
    let room: Room
    /// The verse and the reading it lives in — a finished book's note
    /// opens that book (S12), not the open one.
    var onOpenVerse: (VerseAddress, UUID) -> Void

    @State private var confirmLeave = false
    @State private var showInkPicker = false

    private var person: Person? { model.person(personID) }
    private var isMe: Bool { personID == model.me?.id }
    private var membership: Membership? { model.membership(of: personID, in: room.id) }

    /// What they've left in this room — the whole room, not only the open
    /// reading — in verse order (S12).
    private var theirNotes: [Note] {
        let readingIDs = Set(model.state.readings.filter { $0.roomID == room.id }.map(\.id))
        return model.state.notes
            .filter { readingIDs.contains($0.readingID) && $0.authorID == personID }
            .sorted { $0.verse < $1.verse }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                HStack {
                    BackControl { dismiss() }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                PortraitView(
                    person: person, ink: model.inkForDisplay(personID, in: room.id), size: 108,
                    image: model.portrait(personID))
                    .padding(.top, 12)
                Text(person?.name ?? "")
                    .font(RibbonType.display(26))
                    .foregroundStyle(Palette.text)
                    .accessibilityAddTraits(.isHeader)
                if let ink = membership?.ink {
                    HStack(spacing: 8) {
                        InkDot(ink: ink)
                        SmallCaps(ink.displayName, size: 12)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(Copy.inkSwatch(ink.displayName))
                }

                // What they've left in this room, in verse order.
                if !theirNotes.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(theirNotes) { note in
                            Button {
                                onOpenVerse(note.verse, note.readingID)
                            } label: {
                                HStack(spacing: 10) {
                                    NoteMark(
                                        kind: note.kind,
                                        ink: model.inkForDisplay(personID, in: room.id),
                                        found: true, mine: isMe, pending: false)
                                    SmallCaps(note.verse.formatted, size: 12, color: Palette.text.opacity(0.8))
                                    Spacer()
                                }
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(
                                "\(note.kind == .voice ? Copy.voiceNoteKind : Copy.writtenNoteKind), \(note.verse.formatted)")
                        }
                    }
                    .padding(.horizontal, 30)
                    .padding(.top, 20)
                }

                if isMe, !room.isDeparted {
                    VStack(spacing: 18) {
                        if model.inkIsIdentity(in: room) {
                            QuietControl(title: Copy.changeYourInk) { showInkPicker = true }
                        }
                        QuietControl(title: Copy.leaveThisRoom) { confirmLeave = true }
                    }
                    .padding(.top, 36)
                }
                Spacer(minLength: 60)
            }
            .readableColumn()
        }
        .scrollIndicators(.hidden)
        .room()
        .leaveRoomDialog(room: room, isPresented: $confirmLeave) { dismiss() }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
    }
}

/// Leaving (§6.8): one confirmation, plainly worded, no guilt — and the
/// notes question inside it, defaulting to leaving them. One dialog, not
/// two chained ones (a second dialog set from inside the first's action
/// doesn't reliably present). Shared by S12 and You.
private struct LeaveRoomDialog: ViewModifier {
    @Environment(AppModel.self) private var model
    let room: Room
    @Binding var isPresented: Bool
    var onLeft: () -> Void

    func body(content: Content) -> some View {
        content.confirmationDialog(
            Copy.leaveRoomConfirm, isPresented: $isPresented, titleVisibility: .visible
        ) {
            if model.hasLeftNotes {
                Button(Copy.leaveAndLeaveNotes, role: .destructive) {
                    model.leaveRoom(room, keepNotesBehind: true)
                    onLeft()
                }
                Button(Copy.leaveAndTakeNotes, role: .destructive) {
                    model.leaveRoom(room, keepNotesBehind: false)
                    onLeft()
                }
            } else {
                Button(Copy.leaveThisRoom, role: .destructive) {
                    model.leaveRoom(room, keepNotesBehind: true)
                    onLeft()
                }
            }
        }
    }
}

extension View {
    func leaveRoomDialog(room: Room, isPresented: Binding<Bool>, onLeft: @escaping () -> Void) -> some View {
        modifier(LeaveRoomDialog(room: room, isPresented: isPresented, onLeft: onLeft))
    }
}

/// Picking an ink when color is identity (§4.5, §6.7) — an invitation, not
/// an interruption. The row of eight, with the taken ones dimmed.
struct InkPickerRow: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onPicked: () -> Void = {}

    var body: some View {
        let taken = model.members(of: room).compactMap(\.ink)
        let mine = model.myMembership(in: room)?.ink
        HStack(spacing: 16) {
            ForEach(Ink.allCases, id: \.self) { ink in
                let isTaken = taken.contains(ink) && ink != mine
                Button {
                    guard !isTaken else { return }
                    model.pickInk(ink, in: room)
                    onPicked()
                } label: {
                    // 30 pt drawn, ~44 pt tappable — widening the
                    // frames instead would overflow a 375 pt phone
                    // (8 × 44 + gaps).
                    Circle()
                        .fill(ink.color.opacity(isTaken ? 0.2 : 1))
                        .frame(width: 30, height: 30)
                        .contentShape(Rectangle().inset(by: -7))
                        .overlay {
                            if ink == mine {
                                Circle().strokeBorder(Palette.text.opacity(0.8), lineWidth: 1.6)
                                    .padding(-4)
                            }
                        }
                }
                .buttonStyle(.plain)
                .disabled(isTaken)
                .accessibilityLabel(
                    "\(ink.displayName)\(ink == mine ? ", yours" : "")\(isTaken ? ", taken" : "")")
                .accessibilityAddTraits(ink == mine ? [.isSelected] : [])
            }
        }
    }
}

struct InkPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let room: Room

    var body: some View {
        VStack(spacing: 26) {
            SmallCaps(Copy.yourInk, size: 13)
                .padding(.top, 30)
            InkPickerRow(room: room) { dismiss() }
                .padding(.bottom, 34)
        }
        .frame(maxWidth: .infinity)
        .ribbonSheet(fitted: true)
        .presentationDetents([.height(220)])
    }
}
