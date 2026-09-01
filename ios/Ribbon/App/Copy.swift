import Foundation

// Every user-facing string, in one place, so the voice rules (§10) can be
// audited: short sentences, second person, no exclamation points, never
// name the person who hasn't done a thing, say the true small thing.
//
// Never used anywhere: streak · don't break the chain · accountability
// partner · engagement · daily challenge · crush it · level up · unlock ·
// rewards · devotional journey · spiritual walk · community · you missed
// yesterday · get back on track.

enum Copy {
    // Onboarding (S17 / §6.1)
    static let tagline = "Read it together."
    static let whoIsReading = "Who's reading with you?"
    static let startARoom = "Start a room"
    static let haveAnInvite = "I have an invite"
    static let yourName = "Your name"
    static let portraitReason = "They'll see your face when you're reading."
    static let addAPortrait = "Add a portrait"
    static let skipPortrait = "Not now"
    static let inviteSend = "Send this to the person you're reading with."
    static let inviteLater = "Invite later"
    static let pickABook = "Pick something to read together"
    static let firstRunHint = "Notes go in the margin. Hold a verse to leave one."

    // The room (S01)
    static func continueIn(_ book: String) -> String { "Continue in \(book)" }
    static func begin(_ book: String) -> String { "Begin \(book)" }
    static let inviteStillOut = "The invite is still out."
    static let sendItAgain = "Send it again"
    static let markAQuietDay = "Mark a quiet day"
    static let roomPaused = "The room is paused. It can be started again any time."
    static func readRecently(_ name: String, _ phrase: String) -> String { "\(name) read \(phrase)" }
    static func leftYouANote(_ name: String, _ verse: String) -> String { "\(name) left you a note at \(verse)" }
    static func bankedTheFire(_ name: String) -> String { "\(name) banked the fire" }

    // Reading (S02–S06)
    static let closeTheBook = "Close the book"
    static func isWithYou(_ name: String) -> String { "\(name) is with you" }
    static let backToWhereYouWere = "back to where you were"
    static let readQuietly = "read quietly"
    static let onlyYouCanSeeYou = "only you can see you"
    static let hereButStill = "here, but still"
    static let transcriptComing = "Transcript coming"
    static let noTranscript = "No transcript for this one."
    static let tryAgain = "Try again"
    static let sendItNow = "send it now"
    static let write = "write"
    static let speak = "speak"
    static let takeBack = "take back"
    static let edit = "edit"
    static let remove = "remove"
    static let newNotesNeedTheRoom = "New notes need the room started again."
    static let inksFromWhenTheRoomWasTwo = "These keep their colors. They're from when the room was two."

    // The shelf and embers (S10/S11)
    static let startAnother = "Start another"
    static let oneDayThisIsABook = "One day this is a book."
    static let readItAgain = "Read it again"
    static let straightThrough = "You read this one straight through."
    static let putItOnTheShelf = "Put it on the shelf"
    static func finishedTogether(_ book: String) -> String { "You finished \(book) together" }

    // A person (S12)
    static let changeYourInk = "Change your ink"
    static let leaveThisRoom = "Leave this room"
    static let leaveRoomConfirm = "Leave this room? You'll keep the books on your shelf."
    static let leaveNotesQuestion = "Leave your notes behind? They were left for the other person."
    static let leaveThem = "Leave them"
    static let takeThemBack = "Take them back"

    // The chooser (S13)
    static let goodPlacesToStart = "Good places to start together"
    static let nothingMatches = "Nothing matches that."
    static let search = "Search"

    // Rooms (S14/S15/S16)
    static let startARoomControl = "Start a room"
    static let you = "You"
    static let paused = "paused"
    static let roomName = "Room name"
    static let roomHoldsSix = "A room holds six. Start another for the rest."
    static let inviteExpired = "This invite has expired. Ask for a new one."
    static func wantsToReadWithYou(_ name: String) -> String { "\(name) wants to read with you." }
    static let join = "Join"

    // Settings (S18–S22)
    static let textAndTranslation = "Text"
    static let notifications = "Notifications"
    static let downloads = "Downloads"
    static let plan = "Plan"
    static let signOut = "Sign out"
    static let deleteAccount = "Delete account"
    static let translation = "Translation"
    static let textSize = "Text size"
    static let lineSpacing = "Line spacing"
    static let redLetter = "Words of Jesus in red"
    static let notesLeftForYou = "Notes left for you"
    static let cardsOpen = "The cards open"
    static let whenTheyOpenTheBook = "When they open the book"
    static let thinkingOfYou = "Thinking of you"
    static let quietHours = "Quiet hours"
    static let onThisPhone = "On this phone"
    static let keepEverything = "Keep everything on this phone"
    static let voiceNotesPolicy = "Voice notes stay on this phone for the current reading."
    static let startTheRoomAgain = "Start the room again"
    static let firstBookFree = "The first book is free, all the way through."
    static let manageInStore = "Manage in the App Store"

    // Failure surfaces (S25) — name what happened, name what didn't, offer
    // the one action that helps. No "oops," no "sorry," no error codes.
    static let signInCodeWrong = "That code didn't work. Try again, or send a new one."
    static let cardDeclined = "The card was declined. Nothing changed in your room."
    static func bookNotDownloaded(_ book: String) -> String { "\(book) isn't downloaded yet. It'll finish on Wi-Fi." }
    static let serverUnreachable = "Can't reach Ribbon right now."
    static let micNeeded = "Ribbon needs the microphone to record a note."
    static func noRoomOnPhone(_ megabytes: Int) -> String {
        // A count about a device, not about a person — the boundary of
        // Law 2, stated so nobody over-applies the rule into unusability.
        "There's no room on this phone for the recording. It needs about \(megabytes) MB."
    }

    // Notifications (§10.3) — there are six. There will never be a seventh
    // that is about absence.
    static func notifNoteLeft(_ name: String, _ verse: String) -> String { "\(name) left you a note at \(verse)" }
    static func notifNotesLeft(_ name: String) -> String { "\(name) left you a note" }
    static let notifCardsOpen = "The cards are open"
    static func notifReading(_ name: String, _ book: String) -> String { "\(name) is reading \(book)" }
    static func notifThinkingOfYou(_ name: String) -> String { name }
    static func notifFinished(_ book: String) -> String { "You finished \(book) together" }
}
