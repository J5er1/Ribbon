import SwiftUI
import RibbonCore

// S12 — a person: someone in this room. Not a profile — there is nothing to
// follow, nothing to score. No join date, no activity, no counts of
// anything they've done. Their face on the bare ground, their ink, and what
// they left here, in verse order (ledger A22a/A35).

struct PersonScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let personID: UUID
    let room: Room
    /// The verse and the reading it lives in — a finished book's note
    /// opens that book (S12), not the open one.
    var onOpenVerse: (VerseAddress, UUID) -> Void

    private enum Leaving { case confirm, notes }
    @State private var leaving: Leaving?
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
        RibbonScreen(title: person?.name ?? Copy.someone, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center, spacing: 18) {
                    PortraitView(
                        person: person, ink: membership?.ink, size: 96,
                        image: model.portrait(personID))
                    .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 10) {
                        if let ink = membership?.ink {
                            HStack(spacing: 8) {
                                InkDot(ink: ink)
                                SmallCaps(ink.displayName, size: 12)
                            }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(Copy.inkSpoken(yours: isMe, ink.displayName))
                        }
                        if isMe, model.inkIsIdentity(in: room) {
                            QuietControl(title: Copy.changeYourInk) { showInkPicker = true }
                        }
                    }
                }
                .padding(.top, 4)

                if theirNotes.isEmpty {
                    Text(Copy.nothingLeftHereYet)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                        .padding(.top, 36)
                } else {
                    SettingsGroup(title: isMe ? Copy.whatYouLeft : Copy.whatTheyLeft(firstName(person?.name ?? Copy.someone))) {
                        ForEach(theirNotes) { note in
                            LeftNoteTile(note: note, room: room, mine: isMe, ink: membership?.ink ?? .clay) {
                                onOpenVerse(note.verse, note.readingID)
                            }
                        }
                    }
                    .padding(.top, 36)
                }

                if isMe {
                    QuietControl(title: Copy.leaveThisRoom) { leaving = .confirm }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 48)
                }
            }
        }
        .confirm(leaveDialog)
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
    }

    /// One dialog whose question changes: "Leave this room?" and then, on
    /// yes, "Leave your notes behind?" — leaving them is the default and
    /// taking them back is possible and never the default (§6.8).
    private var leaveDialog: Binding<ConfirmState?> {
        Binding(
            get: {
                switch leaving {
                case nil: return nil
                case .confirm:
                    return ConfirmState(question: Copy.leaveRoomConfirm, choices: [
                        ConfirmChoice(Copy.leaveThisRoom, destructive: true) { leaving = .notes },
                    ])
                case .notes:
                    return ConfirmState(question: Copy.leaveNotesQuestion, choices: [
                        ConfirmChoice(Copy.leaveThem) { leave(keepNotes: true) },
                        ConfirmChoice(Copy.takeThemBack, destructive: true) { leave(keepNotes: false) },
                    ])
                }
            },
            set: { if $0 == nil { leaving = nil } })
    }

    private func leave(keepNotes: Bool) {
        leaving = nil
        model.leaveRoom(room, keepNotesBehind: keepNotes)
        dismiss()
    }
}

/// One thing they left: the verse in small caps, and under it the words
/// once you have found them (or they are yours) — otherwise only that it
/// is not yet found. Finding is done on the page, not here.
private struct LeftNoteTile: View {
    @Environment(AppModel.self) private var model
    let note: Note
    let room: Room
    let mine: Bool
    let ink: Ink
    var action: () -> Void

    private var found: Bool {
        mine || (model.me.map { note.foundBy.contains($0.id) } ?? false)
    }

    private var words: String? {
        guard found else { return nil }
        return note.kind == .voice ? note.transcript : note.body
    }

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 12) {
                NoteMark(kind: note.kind, ink: ink, found: true, mine: mine, pending: note.isPending)
                    .padding(.top, 6)
                VStack(alignment: .leading, spacing: 4) {
                    SmallCaps(note.verse.formatted, size: 12, color: Palette.text.opacity(0.8))
                    if let words, !words.isEmpty {
                        Text(words)
                            .font(RibbonType.ui(15))
                            .foregroundStyle(Palette.text)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    } else if !found {
                        SmallCaps(Copy.notYetFound, size: 11)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, RibbonShape.textInset)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: RibbonShape.rowHeight, alignment: .leading)
            .contentShape(Rectangle())
            .tile()
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            (note.kind == .voice ? Copy.aVoiceNoteAt(note.verse.formatted) : Copy.aNoteAt(note.verse.formatted))
            + (words.map { ". \($0)" } ?? (found ? "" : ". \(Copy.notYetFound)")))
    }
}

/// Picking an ink when colour is identity (§4.5, §6.7) — an invitation, not
/// an interruption. Eight swatches, each drawn at 30 points inside a 44
/// point target, the chosen one ringed.
struct InkPickerSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room

    var body: some View {
        VStack(spacing: 26) {
            SmallCaps(Copy.yourInk, size: 13)
                .padding(.top, 30)
                .accessibilityAddTraits(.isHeader)
            let taken = model.members(of: room).compactMap(\.ink)
            let mine = model.myMembership(in: room)?.ink
            HStack(spacing: 0) {
                ForEach(Ink.allCases, id: \.self) { ink in
                    let isTaken = taken.contains(ink) && ink != mine
                    Button {
                        guard !isTaken else { return }
                        model.pickInk(ink, in: room)
                        dismiss()
                    } label: {
                        ZStack {
                            Circle()
                                .strokeBorder(Palette.text.opacity(0.8), lineWidth: 1.6)
                                .frame(width: 38, height: 38)
                                .opacity(ink == mine ? 1 : 0)
                                .animation(RibbonMotion.touched(still: reduceMotion), value: mine)
                            Circle()
                                .fill(ink.color.opacity(isTaken ? 0.2 : 1))
                                .frame(width: 30, height: 30)
                        }
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                    }
                    .buttonStyle(.pressable)
                    .disabled(isTaken)
                    .accessibilityLabel(Copy.inkSwatchSpoken(ink.displayName, yours: ink == mine, taken: isTaken))
                    .accessibilityAddTraits(ink == mine ? [.isSelected] : [])
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
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
