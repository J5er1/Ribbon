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
    @State private var askAboutNotes = false
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
                PortraitView(
                    person: person, ink: membership?.ink, size: 108,
                    image: model.portrait(personID))
                    .padding(.top, 40)
                Text(person?.name ?? "")
                    .font(RibbonType.display(26))
                    .foregroundStyle(Palette.text)
                if let ink = membership?.ink {
                    HStack(spacing: 8) {
                        InkDot(ink: ink)
                        SmallCaps(ink.displayName, size: 12)
                    }
                }

                // What they've left in this room, in verse order.
                if !theirNotes.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(theirNotes) { note in
                            Button {
                                onOpenVerse(note.verse, note.readingID)
                            } label: {
                                HStack(spacing: 10) {
                                    NoteMark(
                                        kind: note.kind,
                                        ink: membership?.ink ?? .clay,
                                        found: true, mine: isMe, pending: false)
                                    SmallCaps(note.verse.formatted, size: 12, color: Palette.text.opacity(0.8))
                                    Spacer()
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 30)
                    .padding(.top, 20)
                }

                if isMe {
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
        .confirmationDialog(Copy.leaveRoomConfirm, isPresented: $confirmLeave, titleVisibility: .visible) {
            Button(Copy.leaveThisRoom, role: .destructive) { askAboutNotes = true }
        }
        .confirmationDialog(Copy.leaveNotesQuestion, isPresented: $askAboutNotes, titleVisibility: .visible) {
            // Leaving them is the default; taking them back is possible and
            // never the default (§6.8).
            Button(Copy.leaveThem) { leave(keepNotes: true) }
            Button(Copy.takeThemBack) { leave(keepNotes: false) }
        }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
    }

    private func leave(keepNotes: Bool) {
        model.leaveRoom(room, keepNotesBehind: keepNotes)
        dismiss()
    }
}

/// Picking an ink when color is identity (§4.5, §6.7) — an invitation, not
/// an interruption.
struct InkPickerSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let room: Room

    var body: some View {
        VStack(spacing: 26) {
            SmallCaps("your ink", size: 13)
                .padding(.top, 30)
            let taken = model.members(of: room).compactMap(\.ink)
            let mine = model.myMembership(in: room)?.ink
            HStack(spacing: 16) {
                ForEach(Ink.allCases, id: \.self) { ink in
                    let isTaken = taken.contains(ink) && ink != mine
                    Button {
                        guard !isTaken else { return }
                        model.pickInk(ink, in: room)
                        dismiss()
                    } label: {
                        Circle()
                            .fill(ink.color.opacity(isTaken ? 0.2 : 1))
                            .frame(width: 30, height: 30)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                            .overlay {
                                if ink == mine {
                                    Circle().strokeBorder(Palette.text.opacity(0.8), lineWidth: 1.6)
                                        .padding(3)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                    .disabled(isTaken)
                    .accessibilityLabel("\(ink.displayName)\(isTaken ? ", taken" : "")")
                }
            }
            .padding(.bottom, 34)
        }
        .frame(maxWidth: .infinity)
        .room()
        .presentationBackground(Palette.ground)
        .presentationDetents([.height(220)])
        // iPad ignores detents; without this the eight swatches sit at
        // the top of a vast empty form sheet.
        .presentationSizing(.fitted)
    }
}
