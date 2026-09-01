import SwiftUI
import RibbonCore

// S15 — making a room, and inviting. The link is the whole mechanism: no
// contact-list permission, no email field, no invite-by-username. Copy
// assumes one person.

struct InviteSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let room: Room

    @State private var invite: Invite?

    var body: some View {
        VStack(spacing: 22) {
            Spacer()
            if model.isFull(room) {
                Text(Copy.roomHoldsSix)
                    .font(RibbonType.ui(16))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
            } else {
                Text(Copy.inviteSend)
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)

                if let invite {
                    ShareLink(item: invite.url()) {
                        Text("Send the invite")
                            .font(RibbonType.uiMedium(17))
                            .foregroundStyle(Palette.ground)
                            .padding(.horizontal, 28)
                            .padding(.vertical, 13)
                            .background(Palette.chartreuse, in: Capsule())
                    }
                }
            }
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .room()
        .presentationBackground(Palette.ground)
        .onAppear {
            if !model.isFull(room) {
                invite = model.createInvite(for: room)
            }
        }
    }
}

// S15's naming half — used when starting an additional room from S14.
struct NewRoomSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var onCreated: (Room) -> Void

    @State private var name = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            SmallCaps(Copy.roomName, size: 12)
            TextField("", text: $name, prompt: Text("Optional").foregroundStyle(Palette.muted))
                .font(RibbonType.ui(18))
                .foregroundStyle(Palette.text)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Palette.rule, lineWidth: 1))

            WayInButton(title: Copy.startARoomControl) {
                let trimmed = name.trimmingCharacters(in: .whitespaces)
                let room = model.createRoom(named: trimmed.isEmpty ? nil : trimmed)
                dismiss()
                onCreated(room)
            }
        }
        .padding(24)
        .padding(.top, 20)
        .padding(.bottom, 12)
        .room()
        .presentationBackground(Palette.ground)
        .presentationDetents([.medium])
        // iPad ignores detents; fitted keeps this from becoming a mostly
        // empty form sheet around one field and one button.
        .presentationSizing(.fitted)
    }
}
