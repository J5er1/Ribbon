import Foundation
import UIKit

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
    /// The same control, said to somebody who already has a face behind it.
    static let changeYourPortrait = "Change your portrait"
    /// What tapping your own name does, for a screen reader — the room
    /// header names its two doors the same way.
    static let editsYourName = "Edits your name"
    static let skipPortrait = "Not now"
    static let inviteSend = "Send this to the person you're reading with."
    static let inviteLater = "Invite later"
    static let pickABook = "Pick something to read together"
    static let firstRunHint = "Notes go in the margin. Hold a verse to leave one."

    // Walkthrough Tour (Duolingo-style feature walkthrough)
    static let walkthroughVisionTitle = "Read Scripture together."
    static let walkthroughVisionBody = "Ribbon is made for reading with one person or a few — a partner on the same couch, or a friend four time zones away."
    static let walkthroughPresenceTitle = "See each other on the page."
    static let walkthroughPresenceBody = "A soft presence appears when someone is reading at the same time. Quiet companionship without noisy notifications."
    static let walkthroughPresenceSample = "Ruth is reading right now"
    static let walkthroughNotesTitle = "Notes left behind."
    static let walkthroughNotesBody = "Pin written reflections or voice memos to specific verses, waiting silently for the other person to discover later."
    static let walkthroughNoteSample = "0:42 · Left this for you"
    static let walkthroughFireTitle = "A fire kept alive together."
    static let walkthroughFireBody = "No gamified streak counters or cold badge scores. Just a single warm ember your room keeps burning together."
    static let walkthroughIntentTitle = "Who will you read with?"
    static let walkthroughIntentSpouse = "My spouse or partner"
    static let walkthroughIntentFriend = "A close friend"
    static let walkthroughIntentGroup = "A small study or family"
    static let walkthroughIntentSolo = "Starting on my own first"
    static let alreadyHaveAccount = "Already have an account? Sign in"
    static let continueTour = "Continue"
    static let skipTour = "Skip"
    static let getStarted = "Get Started"

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
    /// The fire's state as a sentence, for a screen reader — never a
    /// percentage, never a count (§11, Law 2). The small caps under the fire
    /// say the bare word; anything that has to *speak* it says this.
    static func fireIs(_ stateName: String) -> String { "The fire is \(stateName)." }
    /// The reading surface's own small controls, kept here for the same
    /// reason as everything else: one file, both platforms.
    static let transcript = "transcript"
    static let leaveIt = "leave it"
    static let openSettings = "open settings"
    static let share = "share"
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
    static let nameThisRoom = "Name this room"
    static let roomHoldsSix = "A room holds six. Start another for the rest."
    /// The same fact, said to the person arriving — "the rest" are the
    /// inviter's people, not theirs.
    static let roomFullForJoiner = "This room is full. Ask them to start another."
    static let inviteExpired = "This invite has expired. Ask for a new one."
    static let inviteNotFound = "We couldn't find this invite. Ask for a new one."
    static func wantsToReadWithYou(_ name: String) -> String { "\(name) wants to read with you." }
    static let join = "Join"
    static let someoneWantsToReadWithYou = "Someone wants to read with you."
    static let inviteNeedsSignIn = "Sign in first, so the link can bring them to your room."
    static let pasteInvitePrompt = "Paste the link they sent you"
    static let thatLinkIsntAnInvite = "That doesn't look like an invite link."

    /// The line over the paste field (S15/S17). The link is the whole
    /// mechanism: tapping it is the way in, and pasting is only there for
    /// when the link was sent somewhere this device can't tap it from.
    static let openTheLink = "Open the link they sent you. It brings you straight into their room."

    /// The quiet way out of the invite question, and out of every dead end
    /// in the join thread (S16/S17) — never a step without a way out.
    static let startARoomInstead = "Start a room instead"

    /// A dead end, and the menu, still need their own way out — not only the
    /// swipe. (The line-spacing picker's "Close" is a measure, not a way
    /// out, and stays its own word.)
    static let close = "Close"

    /// The name field's own control, in the joiner's voice (S16/S17).
    static let thatsMe = "That's me"

    /// The code didn't arrive, or arrived too late to use (S16/§6.10).
    static let sendANewCode = "Send a new code"

    /// The link, handed to whatever the two of them already use to talk
    /// (S15). The control says exactly what happens.
    static let sendTheInvite = "Send the invite"

    // The menu (S14 + S18, one screen — see docs/deviations.md 14). Three
    // section heads, so the rooms, the room you are in, and you are three
    // things rather than one pile.
    static let rooms = "Rooms"
    static let thisRoom = "This room"
    static let account = "Account"

    /// Inviting someone to the room you are already in (S15). The room
    /// screen's own line only appears while a room of one still has its
    /// first invite out; this is the way in from two members to six.
    static let inviteSomeone = "Invite someone"

    /// Accepting an invite to another room when you already have one (S16).
    /// The tapped link does this by itself; this is the same door, for a
    /// link that landed somewhere this phone can't tap it from.
    static let joinWithAnInvite = "Join with an invite"

    /// The line over the menu's paste field. Onboarding's `openTheLink`
    /// tells you to open the link, which is exactly what a person standing
    /// here could not do — the link landed on a laptop, or in a thread this
    /// phone can't open. So this one states the fact and lets the field's
    /// own prompt do the asking.
    static let theLinkBringsYouIn = "The link they sent you brings you into their room."

    /// The room with no name and no members but you (S15) — a room is
    /// named by whoever is in it, and until somebody is, this.
    static let yourRoom = "Your room"

    /// The room-name field's placeholder: naming a room is never required.
    static let optional = "Optional"

    /// Picking an ink when colour is identity (§4.5, §6.7).
    static let yourInk = "your ink"

    /// The held beat while the invite is fetched (S16) — the wordmark, in
    /// the quietest voice there is, because nothing in Ribbon is visibly
    /// loading (§8).
    static let wordmark = "ribbon"

    /// The join in flight, said once and quietly.
    static let joining = "joining"

    /// The way back out of a pushed screen: the name Android's drawn chevron
    /// answers to, and the word on the menu's own way out of a dead invite.
    static let back = "Back"

    /// What the room header's two controls do, for a screen reader. iOS
    /// states the consequence (a hint); Android's click label states the
    /// action. Same fact, each platform's grammar — and both of these are
    /// consequences, which the second one was not.
    static let opensYourRooms = "Opens your rooms"
    static let opensYourAccount = "Opens your account and settings"

    // Sign-in (§6.10) — an emailed code, no passwords. The account exists
    // for one reason, said plainly.
    static let accountReason = "An account carries your room between phones."
    static let yourEmail = "Your email"
    static let sendTheCode = "Send the code"
    static let codeOnItsWay = "A code is on its way to your email."
    static let theCode = "The code"
    static let signIn = "Sign in"

    // Passkeys (§6.10 — "a passkey where available, an emailed code
    // otherwise"). Always an addition to the code, never a replacement, so
    // the words offer rather than instruct.
    static let useAPasskey = "Use a passkey"
    static let addAPasskey = "Add a passkey"
    static let passkeyReason = "Then signing in is your face or your PIN, on any phone."
    /// The true small thing, said once, where the reason used to be. Nothing
    /// in Ribbon congratulates anybody.
    static let passkeyAdded = "This phone can sign you in now."
    /// S25's shape: name what happened, name what didn't, and leave the one
    /// thing that still works in front of them.
    static let passkeyDidntWork = "That passkey didn't work. The emailed code still does."
    static let signInWithAuth0 = "Continue with Auth0"
    static let auth0DidntWork = "Signing in didn't finish. The emailed code still does."

    /// The quiet way out of the sign-in thread, wherever it is offered —
    /// never a wall (§6.1, §6.10).
    static let neverMind = "Never mind"

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

    /// The three stops of the line-spacing control (S20). "Close" here is a
    /// measure of leading, not a way out — it is a different word from
    /// `close`, which happens to be spelled the same.
    static let lineSpacingClose = "Close"
    static let lineSpacingBook = "Book"
    static let lineSpacingOpen = "Open"

    /// Between the two ends of quiet hours (S19).
    static let quietHoursTo = "to"

    /// What quiet hours do not silence (S19): the one notification that is
    /// a touch rather than a sentence, said plainly so nobody is surprised
    /// by it.
    static let thinkingOfYouStillArrives = "Thinking of you still arrives, silently, as a touch."

    /// A licensed translation on the downloads list (S21). The book being
    /// read stays on the device and the rest doesn't — its license, not our
    /// design — so the row says what it does instead of a size.
    static let streams = "streams"

    /// Where the ask lives, said once (S22). A non-paying member never sees
    /// a price and never learns who pays.
    static let theAskComesOnce =
        "When your room's first ember is on the shelf, Ribbon will ask — there, and only there."

    /// Deleting the account asks §6.8's question — `leaveNotesQuestion` —
    /// and these are its two answers. Neither is the quiet one: deleting is
    /// deliberate either way, and the notes were left for the other person.
    static let deleteAndLeaveThem = "Delete, and leave them"
    static let deleteAndTakeThemBack = "Delete, and take them back"

    /// Megabytes on this device (S21) — a count about a device, not about a
    /// person, which is the one honest exception to Law 2 and the same
    /// boundary `noRoomOnPhone` states.
    static func megabytes(_ count: Int) -> String { "\(count) MB" }

    /// The wordmark and the build, in the quietest voice there is (S18) —
    /// the one line on that screen that is for us rather than for the
    /// reader.
    static func versionLine(_ version: String) -> String { "\(wordmark) \(version)" }
    static let notesLeftForYou = "Notes left for you"
    static let cardsOpen = "The cards open"
    static let whenTheyOpenTheBook = "When they open the book"
    static let thinkingOfYou = "Thinking of you"
    static let quietHours = "Quiet hours"
    /// "Phone" is the concrete noun the voice wants — but on an iPad it is
    /// simply wrong, so the noun follows the device. The one place idiom,
    /// not size class, is the right test. (MainActor because UIDevice is;
    /// every caller is a view.)
    @MainActor static var deviceNoun: String {
        UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "phone"
    }
    @MainActor static var onThisPhone: String { "On this \(deviceNoun)" }
    @MainActor static var keepEverything: String { "Keep everything on this \(deviceNoun)" }
    @MainActor static var voiceNotesPolicy: String { "Voice notes stay on this \(deviceNoun) for the current reading." }
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
}
