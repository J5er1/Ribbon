import Foundation
import UIKit

// Every user-facing string, in one place, so the voice rules (§10) can be
// audited: short sentences, second person, no exclamation points, never
// name the person who hasn't done a thing, say the true small thing. A
// control says exactly what happens. Small-caps strings are written in
// sentence case or lower case — the face does the capitalization work.
//
// Never used anywhere: streak · don't break the chain · accountability
// partner · engagement · daily challenge · crush it · level up · unlock ·
// rewards · devotional journey · spiritual walk · community · you missed
// yesterday · get back on track.

enum Copy {
    // The thread (S17 / §6.1)
    static let tagline = "Read it together."
    static let whoIsReading = "Who's reading with you?"
    static let startARoom = "Start a room"
    static let haveAnInvite = "I have an invite"
    static let yourName = "Your name"
    static let thatsMe = "That's me"
    static let portraitReason = "They'll see your face when you're reading."
    static let addAPortrait = "Add a portrait"
    static let changePortrait = "Your portrait. Tap to change it."
    static let back = "back"
    static let inviteSend = "Send this to the person you're reading with."
    static let sendTheInvite = "Send the invite"
    static let sendItAgain = "Send it again"
    static let inviteLater = "Invite later"
    static let inviteSomeone = "Invite the person you're reading with"
    static let inviteSomeoneElse = "Invite someone"
    static let pickABook = "Pick a book"
    static let pickSomethingToRead = "Pick something to read together"
    static let readOnYourOwnForNow = "Read on your own for now"
    static let openTheLinkTheySent = "Open the link they sent you. It brings you straight into their room."
    static let orPasteItHere = "Or paste it here"
    static let thatLinkIsntAnInvite = "That doesn't look like an invite link."
    static let startARoomInstead = "Start a room instead"
    static let firstRunHint = "Notes go in the margin. Hold a verse to leave one."

    // The account (§6.10) — an emailed code, no passwords. It exists for
    // one reason each time, said plainly. Never "sign in first".
    static let emailReasonStarter = "Your email first, so the link works on their phone and the room stays yours."
    static let emailReasonJoiner = "Your email, so the room stays yours on any phone."
    static let emailReasonSettings = "An account carries your rooms between phones."
    static let yourEmail = "Your email"
    static let sendTheCode = "Send the code"
    static func codeOnItsWay(_ address: String) -> String { "A code is on its way to \(address)." }
    static let theCode = "The code"
    static let continueControl = "Continue"
    static let sendANewCode = "Send a new code"
    static let useADifferentEmail = "Use a different email"
    static let neverMind = "Never mind"
    static let signIn = "Sign in"
    static let signOut = "Sign out"
    @MainActor static var signOutNote: String { "Your rooms and notes stay on this \(deviceNoun)." }
    @MainActor static var differentAccountHere: String {
        "This \(deviceNoun) already belongs to another account. Sign out first."
    }

    // Joining (S16)
    static func wantsToReadWithYou(_ name: String) -> String { "\(name) wants to read with you." }
    static let someoneWantsToReadWithYou = "Someone wants to read with you."
    static let join = "Join"
    static func joiningAs(_ name: String) -> String { "joining as \(name)" }
    static let notYou = "Not you?"
    static let yourInk = "Your ink"
    static let pickYourInk = "Pick your ink"
    static let inkReason = "With three of you, each person's marks are one color."
    static let tryAgain = "Try again"
    static let close = "Close"
    static let inviteExpired = "This invite has expired. Ask for a new one."
    static let roomFullForJoiner = "This room is full. Ask them to start another."
    static let inviteNotRegisteredYet = "Can't reach Ribbon right now. The link will work once it can."

    // The room (S01)
    static func continueIn(_ book: String) -> String { "Continue in \(book)" }
    static func begin(_ book: String) -> String { "Begin \(book)" }
    static func willFinishDownloading(_ book: String) -> String { "\(book) will finish downloading on Wi-Fi" }
    static let inviteStillOut = "The invite is still out."
    static let markAQuietDay = "Mark a quiet day"
    static let quietDayMarked = "banked for today"
    static let roomPaused = "The room is paused. It can be started again any time."
    static let youLeftThisRoom = "You left this room. The shelf stays."
    static func readRecently(_ name: String, _ phrase: String) -> String { "\(name) read \(phrase)" }
    static func leftYouANote(_ name: String, _ verse: String) -> String { "\(name) left you a note at \(verse)" }
    static func bankedTheFire(_ name: String, _ phrase: String) -> String { "\(name) banked the fire \(phrase)" }
    static func youBankedTheFire(_ phrase: String) -> String { "You banked the fire \(phrase)" }
    static let anInkToPick = "An ink to pick"
    static let cardsAreOpen = "The cards are open"
    static let onTheShelf = "on the shelf"
    static let startAnother = "Start another"
    static let yourRoom = "Your room"

    // Reading (S02–S06)
    static let closeTheBook = "Close the book"
    static func isWithYou(_ name: String) -> String { "\(name) is with you" }
    static let backToWhereYouWere = "back to where you were"
    static let readQuietly = "read quietly"
    static let readingQuietly = "reading quietly"
    static let beSeenAgain = "be seen again"
    static let onlyYouCanSeeYou = "only you can see you"
    static let hereButStill = "here, but still"
    static let transcriptComing = "Transcript coming"
    static let transcript = "transcript"
    static let noTranscript = "No transcript for this one."
    static let write = "write"
    static let speak = "speak"
    static let leaveIt = "leave it"
    static let discard = "discard"
    static let takeBack = "take back"
    static let edit = "edit"
    static let remove = "remove"
    static let releaseToLeaveIt = "release to leave it"
    static let letGoToDiscard = "let go to discard"
    static let holdToSpeak = "hold to speak"
    static let recordAVoiceNote = "Record a voice note"
    static let stopRecording = "Stop and leave the note"
    static let leaveANote = "Leave a note"
    static let highlight = "Highlight"
    static let openSettings = "open settings"
    static let newNotesNeedTheRoom = "New notes need the room started again."
    static let inksFromWhenTheRoomWasTwo = "These keep their colors. They're from when the room was two."
    static let bookIsFinished = "This book is finished. Its record is on the shelf."
    static func bereanForNow(_ translation: String) -> String { "Berean Standard for now — \(translation) can't load" }
    static let fireSettles = "The fire settles into an ember"

    // Cards (S08/S09)
    static let cardOpensWhenEveryoneHasAnswered = "This opens when everyone has answered."
    static let setItDown = "set it down"
    static let yourAnswer = "Your answer"
    static let saveAnswer = "keep it"
    static let editAnswer = "edit"

    // The shelf and embers (S10/S11)
    static let oneDayThisIsABook = "One day this is a book."
    static let readItAgain = "Read it again"
    static let straightThrough = "You read this one straight through."
    static let putItOnTheShelf = "Put it on the shelf"
    static func finishedTogether(_ book: String) -> String { "You finished \(book) together" }
    static let exportTheShelf = "Export the shelf"
    static let share = "share"
    static let shareTheVerse = "share the verse"
    static let stillGoing = "still going"
    static let setAside = "set aside"
    static func setAsideLine(_ book: String) -> String { "\(book) is still going. Picking another sets it aside — it keeps its notes, and it's here when you come back." }

    // The chooser (S13, S23)
    static let goodPlacesToStart = "Good places to start together"
    static let nothingMatches = "Nothing matches that."
    static let search = "Search"
    static let clear = "clear"

    // A person (S12) and rooms (S14/S15)
    static let changeYourInk = "Change your ink"
    static let leaveThisRoom = "Leave this room"
    static let leaveRoomConfirm = "Leave this room? You'll keep the books on your shelf."
    static let leaveAndLeaveNotes = "Leave, and leave my notes"
    static let leaveAndTakeNotes = "Leave, and take my notes back"
    static let closeThisRoom = "Close this room"
    static let closeRoomConfirm = "Close this room? Its shelf goes with it. Export it first if you want to keep it."
    static let closeIt = "Close it"
    static let forgetThisRoom = "Forget this room"
    static let forgetRoomConfirm = "Forget this room? Its shelf goes with it. Export it first if you want to keep it."
    static let forgetIt = "Forget it"
    static let startARoomControl = "Start a room"
    static let you = "You"
    static let paused = "paused"
    static let roomName = "Room name"
    static let nameThisRoom = "Name this room"
    static let roomHoldsSix = "A room holds six. Start another for the rest."
    static let roomsYouLeft = "rooms you've left"
    static let freePaletteAgain = "Use the whole palette again"
    static let inkYours = "yours"
    static let inkTaken = "taken"

    // Settings (S18–S22)
    static let textAndTranslation = "Text"
    static let notifications = "Notifications"
    static let downloads = "Downloads"
    static let plan = "Plan"
    static let deleteAccount = "Delete account"
    static let deleteAccountConfirm = "Delete your account? Your rooms stay for the people in them."
    static let deleteAndLeaveNotes = "Delete, and leave my notes"
    static let deleteAndTakeNotes = "Delete, and take my notes back"
    static let deleteCouldNotReach = "Can't reach Ribbon right now. Nothing was deleted."
    static let translation = "Translation"
    static let textSize = "Text size"
    static let lineSpacing = "Line spacing"
    static let spacingClose = "Close"
    static let spacingBook = "Book"
    static let spacingOpen = "Open"
    static let redLetter = "Words of Jesus in red"
    static let redLetterUnavailable = "The Berean text doesn't mark them."
    static let notesLeftForYou = "Notes left for you"
    static let cardsOpen = "The cards open"
    static let whenTheyOpenTheBook = "When they open the book"
    static let thinkingOfYou = "Thinking of you"
    static let quietHours = "Quiet hours"
    static let quietHoursTo = "to"
    static let quietHoursNote = "Thinking of you still arrives, silently, as a touch."
    static let notificationsNotYet = "Kept for when Ribbon can send them."
    /// "Phone" is the concrete noun the voice wants — but on an iPad it is
    /// simply wrong, so the noun follows the device. The one place idiom,
    /// not size class, is the right test. (MainActor because UIDevice is;
    /// every caller is a view.)
    @MainActor static var deviceNoun: String {
        UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "phone"
    }
    @MainActor static var onThisPhone: String { "On this \(deviceNoun)" }
    @MainActor static var keepEverything: String { "Keep everything on this \(deviceNoun)" }
    @MainActor static var keepEverythingNote: String { "The Berean and World English are always whole here. Licensed text keeps the book being read." }
    @MainActor static var voiceNotesPolicy: String { "Voice notes stay on this \(deviceNoun)." }
    static let streams = "streams"
    static let firstBookFree = "The first book is free, all the way through."
    static let planAskLater = "When your room's first ember is on the shelf, Ribbon will ask — there, and in Plan. Nowhere else."
    static let planNotYet = "Plans arrive with the store. Nothing here changes until then."
    static let version = "ribbon"
    // Phase two (§13, §14) — written now so the voice is settled before
    // the store and the pause exist; not yet spoken anywhere.
    static let startTheRoomAgain = "Start the room again"
    static let manageInStore = "Manage in the App Store"
    static let cardDeclined = "The card was declined. Nothing changed in your room."
    static let speechNeeded = "Ribbon needs speech recognition to write the transcript."

    // Failure surfaces (S25) — name what happened, name what didn't, offer
    // the one action that helps. No "oops," no "sorry," no error codes.
    static let signInCodeWrong = "That code didn't work. Try again, or send a new one."
    static let emailDidntTake = "That email didn't take. Check it and send again."
    static let tooManyCodes = "Too many codes in a row. Give it a few minutes."
    static func bookNotDownloaded(_ book: String) -> String { "\(book) isn't downloaded yet. It'll finish on Wi-Fi." }
    static let serverUnreachable = "Can't reach Ribbon right now."
    static let micNeeded = "Ribbon needs the microphone to record a note."
    @MainActor static func noRoomOnPhone(_ megabytes: Int) -> String {
        // A count about a device, not about a person — the boundary of
        // Law 2, stated so nobody over-applies the rule into unusability.
        "There's no room on this \(deviceNoun) for the recording. It needs about \(megabytes) MB."
    }

    // Notifications (§10.3) — there are six. There will never be a seventh
    // that is about absence.
    static func notifNoteLeft(_ name: String, _ verse: String) -> String { "\(name) left you a note at \(verse)" }
    static func notifNotesLeft(_ name: String) -> String { "\(name) left you a note" }
    static let notifCardsOpen = "The cards are open"
    static func notifReading(_ name: String, _ book: String) -> String { "\(name) is reading \(book)" }
    static func notifThinkingOfYou(_ name: String) -> String { name }
    static func notifFinished(_ book: String) -> String { "You finished \(book) together" }

    // Accessibility labels (§11) — the fire obeys Law 2; marks say who and
    // what kind; stacks announce by author, never by count.
    static func fireIs(_ state: String) -> String { "The fire is \(state)." }
    static func emberOf(_ book: String) -> String { "\(book), an ember. Opens its record." }
    static func noteFrom(_ kind: String, _ who: String, _ verse: Int, unfound: Bool) -> String {
        "\(kind) from \(who), verse \(verse)\(unfound ? ", not yet found" : "")"
    }
    static let voiceNoteKind = "Voice note"
    static let writtenNoteKind = "Note"
    static let voiceNotesKind = "Voice notes"
    static let writtenNotesKind = "Notes"
    static let opensYourRooms = "Opens your rooms"
    static let yourAccountAndSettings = "Your account and settings"
    static let playTheVoiceNote = "Play the voice note"
    static let pauseTheVoiceNote = "Pause the voice note"
    static func inkSwatch(_ ink: String) -> String { "\(ink) ink" }
    static func presenceRow(_ name: String, _ where_: String) -> String { "\(name), \(where_)" }
    static func isReading(_ name: String) -> String { "\(name) is reading" }
    static let following = "following"
    /// Names, never a count: "Ruth and Jacob".
    static func names(_ names: [String]) -> String { names.joined(separator: " and ") }
    static func withOthers(_ names: [String]) -> String { "with \(Copy.names(names))" }
    static let youLower = "you"
    static func verseLabel(_ verse: Int, _ text: String) -> String { "Verse \(verse). \(text)" }
    static func noteFromAuthor(_ kind: String, _ name: String, _ body: String?) -> String {
        "\(kind) from \(name)." + (body.map { " \($0)" } ?? "")
    }
    static let audioPlaying = "playing"
    static let audioPaused = "paused"
    static let tapToFollow = "Follow"
    static let thinkingOfThem = "Thinking of you"
    static let currentRoom = "current room"
    static let backLabel = "Back"
    static let toggleOn = "On"
    static let toggleOff = "Off"

    // The export (§13) — a readable file, in the same voice.
    static func exportAsOf(_ date: String) -> String { "The shelf, as of \(date)." }
    static let exportStillGoing = "still going"
    static let exportNotesLeft = "Notes left"
    static let exportHighlights = "Highlights"
    static let exportCards = "Cards"
    static let exportVoice = "(voice)"
    static let exportVoiceNote = "(voice note)"
    static let someone = "Someone"
    static func exportFileName(_ room: String) -> String { "\(room) — shelf" }
}
