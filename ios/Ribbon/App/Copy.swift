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
//
// One file, both platforms: Android's `Copy.kt` carries the same strings in
// the same order, and a line added to one is added to the other in the same
// change. Where the two differ it is the platform's noun (App Store / Google
// Play; phone / tablet) and nothing else.

enum Copy {
    // MARK: Onboarding (S17 / §6.1)
    static let tagline = "Read it together."
    static let whoIsReading = "Who's reading with you?"
    static let startARoom = "Start a room"
    static let haveAnInvite = "I have an invite"
    static let yourName = "Your name"
    /// The name field's own control, in the joiner's voice (S16/S17).
    static let thatsMe = "That's me"
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
    /// The same act as a control's name: what the unlit hearth does when it
    /// is tapped rather than pulled.
    static let pickABookControl = "Pick a book"
    static let firstRunHint = "Notes go in the margin. Hold a verse to leave one."

    /// The one time Ribbon asks about notifications, and it asks in context
    /// — the first time there is somebody whose note there could be (S19,
    /// ledger A34). Never on first launch, never with a system prompt first.
    static func tellYouWhen(_ name: String) -> String { "Tell you when \(firstName(name)) leaves a note?" }
    static let tellMe = "Tell me"
    static let dontTellMe = "Don't"

    // The tour (S17). Four cards, each the true small thing about one part
    // of the product, in the product's own words — no "gamified", no
    // "engagement", no durations (Law 2 holds on the tour too).
    static let walkthroughVisionTitle = "Read with someone."
    static let walkthroughVisionBody = "Read with one person, or a few. On the same couch, or four time zones away."
    static let walkthroughPresenceTitle = "See each other on the page."
    static let walkthroughPresenceBody = "When someone else is reading, you see them beside you on the page."
    static let walkthroughPresenceSample = "Ruth is reading right now"
    static let walkthroughNotesTitle = "Notes left behind."
    static let walkthroughNotesBody = "Leave a note at a verse, written or spoken. They find it when they get there."
    static let walkthroughNoteSample = "Left this for you"
    static let walkthroughFireTitle = "A fire you keep together."
    static let walkthroughFireBody = "One book is one fire. It catches, burns, goes steady, and is banked on a quiet day."
    static let walkthroughIntentTitle = "Who will you read with?"
    static let walkthroughIntentSpouse = "My spouse or partner"
    static let walkthroughIntentFriend = "A close friend"
    static let walkthroughIntentGroup = "A small study or family"
    static let walkthroughIntentSolo = "Starting on my own first"
    static let alreadyHaveAccount = "Already have an account? Sign in"
    static let continueTour = "Continue"
    static let skipTour = "Skip"
    static let getStarted = "Get Started"

    // MARK: The room (S01)

    /// The hearth's greeting, by the hour on this phone (ledger A48). The
    /// first name only: a greeting that says your whole name is a form
    /// letter.
    static func greeting(_ name: String?, hour: Int) -> String {
        let part: String
        switch hour {
        case 5...11: part = "Good morning"
        case 12...16: part = "Good afternoon"
        default: part = "Good evening"
        }
        guard let name, !name.trimmingCharacters(in: .whitespaces).isEmpty else { return "\(part)." }
        return "\(part), \(firstName(name))."
    }
    static let leftForYou = "Left for you"
    static let theShelf = "The shelf"
    /// The one gesture the front door teaches, said once until it has been
    /// done (ledger A48).
    static let pullTheFireUp = "Pull the fire up to open the book"
    static let firstFireHint = "Whatever you pick becomes a fire. A short book makes a small one."
    static let anOpenSeat = "An open seat. Invite someone."
    static func continueIn(_ book: String) -> String { "Continue in \(book)" }
    static func begin(_ book: String) -> String { "Begin \(book)" }
    static let inviteStillOut = "The invite is still out."
    static let sendItAgain = "Send it again"
    static let markAQuietDay = "Mark a quiet day"
    static let roomPaused = "The room is paused. It can be started again any time."
    static func readRecently(_ name: String, _ phrase: String) -> String { "\(name) read \(phrase)" }
    static func leftYouANote(_ name: String, _ verse: String) -> String { "\(name) left you a note at \(verse)" }
    static func leftYouAVoiceNote(_ name: String, _ verse: String) -> String { "\(name) left you a voice note at \(verse)" }
    static func bankedTheFire(_ name: String) -> String { "\(name) banked the fire" }

    /// What the room header's two controls do, for a screen reader. iOS
    /// states the consequence (a hint); Android's click label states the
    /// action. Same fact, each platform's grammar.
    static let opensYourRooms = "Opens your rooms"
    static let opensYourAccount = "Opens your account and settings"

    // MARK: Reading (S02–S06)

    /// The fire's state as a sentence, for a screen reader — never a
    /// percentage, never a count (§11, Law 2). The small caps under the fire
    /// say the bare word; anything that has to *speak* it says this.
    static func fireIs(_ stateName: String) -> String { "The fire is \(stateName)." }
    /// A verse, read out: its number first, so a reader can find it again.
    static func verseSpoken(_ verse: Int, _ body: String) -> String { "Verse \(verse). \(body)" }
    /// The margin's mark, read out: whose, at which verse, and whether it
    /// has been found — never how many (ledger A38).
    static func marginNotes(authors: [String], verse: Int, several: Bool, unfound: Bool) -> String {
        let who = authors.isEmpty ? "you" : authors.joined(separator: " and ")
        let noun = several ? "Notes" : "Note"
        return "\(noun) from \(who), verse \(verse)" + (unfound ? ", not yet found" : "")
    }
    /// A verse's two tap equivalents (§11): what is at it, and leaving
    /// something there yourself.
    static let openWhatsHere = "open what's here"
    static let leaveSomethingHere = "leave something here"
    static let closeTheBook = "Close the book"
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
    static let readingQuietlySpoken = "Reading quietly. Only you can see you."
    static let whosHere = "who's here"
    static let recordingAVoiceNote = "Recording. Leave it, or take it back."
    /// The presence line as a sentence (ledger A48): one name, then the
    /// rest, never a list of pills.
    static func personIsReading(_ name: String) -> String { "\(name) is reading" }
    static func personIsHereButStill(_ name: String) -> String { "\(name) is here, but still" }
    static func alsoHere(_ base: String, _ others: [String]) -> String {
        base + ", with " + others.joined(separator: " and ")
    }
    static let follow = "Follow"
    /// A portrait in the presence form, for a screen reader: what the tap
    /// does, as a consequence (Android's click label says "Follow"). The
    /// hold is not described — it is an action of its own, "Thinking of
    /// you", as it is on Android (§11: every gesture has an equivalent that
    /// is not a gesture).
    static let followsThem = "Follows them"
    static let dismiss = "dismiss"

    /// The two ends of a highlight, as controls (§11, ledger A41g). Each has
    /// a verse and a word step either way, so a mark can be made and moved
    /// without a drag.
    static let whereTheMarkStarts = "Where the mark starts"
    static let whereTheMarkEnds = "Where the mark ends"
    static let aVerseFurtherOn = "A verse further on"
    static let aVerseBack = "A verse back"
    static let aWordFurtherOn = "A word further on"
    static let aWordBack = "A word back"

    static let transcriptComing = "Transcript coming"
    static let noTranscript = "No transcript for this one."
    static let tryAgain = "Try again"
    static let showsTheTranscript = "show the transcript"
    static let hidesTheTranscript = "hide the transcript"
    static let playTheVoiceNote = "Play the voice note"
    static func noteFrom(_ name: String, _ body: String) -> String { "Note from \(name). \(body)" }
    static func voiceNoteFrom(_ name: String, _ transcript: String) -> String { "Voice note from \(name). \(transcript)" }
    static let sendItNow = "send it now"
    static let write = "write"
    static let speak = "speak"
    static let takeBack = "take back"
    /// The composer's prompt. Not "Type a note": what you want to say.
    static let whatYouWantToSay = "What you want to say"
    static let releaseToLeaveIt = "release to leave it"
    static let letGoToDiscard = "let go to discard"
    static func inkNamed(_ name: String) -> String { "\(name) ink" }
    static let edit = "edit"
    static let remove = "remove"
    static let newNotesNeedTheRoom = "New notes need the room started again."
    /// The third person arriving turns colour into identity (§4.5). Said
    /// once, in the room, as a row you can act on.
    static let pickAnInk = "Colour is a person now. Pick your ink."

    // Reflection cards (§4.6, S08/S09)
    static let cardOpensWhenEveryoneHasAnswered = "This opens when everyone has answered."
    static let setItDown = "set it down"
    static let editYourAnswer = "Edit your answer"
    static let answer = "Answer"
    static let keepWhatIHad = "Keep what I had"
    static let yourAnswer = "Your answer"
    static func cardSealedSpoken(_ question: String) -> String { "\(question). \(cardOpensWhenEveryoneHasAnswered)" }
    static func cardOpenSpoken(_ question: String) -> String { "\(question). The card is open." }
    static func answerFrom(_ name: String, _ answer: String) -> String { "\(name) answered. \(answer)" }

    // MARK: The shelf and embers (S10/S11)
    static let startAnother = "Start another"
    static let oneDayThisIsABook = "One day this is a book."
    static let readItAgain = "Read it again"
    static let straightThrough = "You read this one straight through."
    static func sharedNote(_ verse: String, _ body: String) -> String { "\(verse) — \(body)" }
    static let putItOnTheShelf = "Put it on the shelf"
    static func finishedTogether(_ book: String) -> String { "You finished \(book) together" }
    static let theFireSettlesIntoAnEmber = "The fire settles into an ember."

    // MARK: A person (S12)
    static func whatTheyLeft(_ name: String) -> String { "What \(name) left" }
    static func aVoiceNoteAt(_ verse: String) -> String { "Voice note at \(verse)" }
    static func aNoteAt(_ verse: String) -> String { "Note at \(verse)" }
    static let whatYouLeft = "What you left"
    static let nothingLeftHereYet = "Nothing left in this room yet."
    static let notYetFound = "not yet found"
    static func inkSpoken(yours: Bool, _ ink: String) -> String { (yours ? "Your ink" : "Their ink") + ", " + ink }
    /// A person whose name this phone has never learned, or who has
    /// deleted their account and left their notes behind (§6.8).
    static let someone = "Someone"
    static let changeYourInk = "Change your ink"
    static let leaveThisRoom = "Leave this room"
    static let leaveRoomConfirm = "Leave this room? You'll keep the books on your shelf."
    static let leaveNotesQuestion = "Leave your notes behind? They were left for the other person."
    static let leaveThem = "Leave them"
    static let takeThemBack = "Take them back"
    static let stay = "Stay"
    /// Picking an ink when colour is identity (§4.5, §6.7).
    static let yourInk = "your ink"
    static func inkSwatchSpoken(_ name: String, yours: Bool, taken: Bool) -> String {
        name + (yours ? ", yours" : "") + (taken ? ", taken" : "")
    }

    // MARK: The chooser (S13)
    static let goodPlacesToStart = "Good places to start together"
    static let nothingMatches = "Nothing matches that."
    static let search = "Search"
    /// A book as the chooser speaks it: its name and the fire it makes,
    /// which is a scale, not a number (Law 2).
    static func bookIsAFire(_ book: String, scale: String, onShelf: Bool = false) -> String {
        "\(book), a \(scale) fire" + (onShelf ? ", on your shelf" : "")
    }

    // MARK: Rooms (S14/S15/S16)
    static let startARoomControl = "Start a room"
    static let you = "You"
    static let paused = "paused"
    static let roomName = "Room name"
    /// The room-name field's placeholder: naming a room is never required.
    static let optional = "Optional"
    /// The link, handed to whatever the two of them already use to talk
    /// (S15). The control says exactly what happens.
    static let sendTheInvite = "Send the invite"
    static let nameThisRoom = "Name this room"
    static let roomHoldsSix = "A room holds six. Start another for the rest."
    /// The same fact, said to the person arriving — "the rest" are the
    /// inviter's people, not theirs.
    static let roomFullForJoiner = "This room is full. Ask them to start another."
    static let inviteExpired = "This invite has expired. Ask for a new one."
    /// "We couldn't find" was the app talking about itself. The invite is
    /// what is gone.
    static let inviteNotFound = "That invite isn't there any more. Ask for a new one."
    static func wantsToReadWithYou(_ name: String) -> String { "\(name) wants to read with you." }
    static let join = "Join"
    static let someoneWantsToReadWithYou = "Someone wants to read with you."
    /// Joining a second room as the person this phone already is (S16,
    /// ledger A37): the name is stated and can be declined, never re-asked.
    static func joiningAs(_ name: String) -> String { "You'll join as \(name)." }
    static let joinAsSomeoneElse = "Join as someone else"
    /// The held beat while the invite is fetched (S16) — the wordmark, in
    /// the quietest voice there is, because nothing in Ribbon is visibly
    /// loading (§8).
    static let wordmark = "ribbon"
    /// The join in flight, said once and quietly.
    static let joining = "joining"
    /// A dead end, and the menu, still need their own way out — not only the
    /// swipe. (The line-spacing picker's "Close" is a measure, not a way
    /// out, and stays its own word.)
    static let close = "Close"
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

    // MARK: The chapter list and the ribbon (ledger A30/A31)
    static let chapters = "Chapters"
    static let goThere = "Go there"
    static let theRibbon = "The ribbon"
    /// The ribbon offered, never applied: who left it and where. Nobody is
    /// behind — the sentence holds one address and never the reader's own.
    static func ribbonIsAt(_ who: String?, _ reference: String) -> String {
        guard let who else { return "The ribbon is at \(reference)." }
        return "\(who) left the ribbon at \(reference)."
    }
    static func youLeftTheRibbonAt(_ reference: String) -> String { "You left the ribbon at \(reference)." }
    static func chapterYouAreHere(_ heading: String) -> String { "\(heading), where you are" }
    static func chapterHasRibbon(_ heading: String) -> String { "\(heading), where the ribbon is" }
    static func chapterYouAreHereWithRibbon(_ heading: String) -> String { "\(heading), where you are and where the ribbon is" }

    // MARK: The menu (S14 + S18 — two root screens, ledger A29)
    static let rooms = "Rooms"
    static let thisRoom = "This room"
    static let account = "Account"
    static let roomLede = "Who is in it, what it tells you, and the rooms you are in."
    static let yourRooms = "Your rooms"
    static let howYouRead = "How you read"
    @MainActor static var thisPhone: String { "This \(deviceNoun)" }
    static let yourAccount = "Your account"
    static let yourFaceReason = "Your name and face are what the room sees."
    static let addAPortraitReason = "Your name and face are what the room sees. Tap to add one."
    static let tapToChange = "Tap to change"
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
    /// The way back out of a pushed screen: the name the drawn chevron
    /// answers to, and the word on the menu's own way out of a dead invite.
    static let back = "Back"

    // MARK: Sign-in (§6.10) — an emailed code, no passwords.
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
    static let passkeyWasntAdded = "Adding the passkey didn't finish. You can try again."
    /// What the button does, not whose service it is. Nobody signing in
    /// knows what an Auth0 is (ledger A46).
    static let signInInABrowser = "Continue in a browser"
    static let auth0DidntWork = "Signing in didn't finish. The emailed code still does."
    /// The code didn't arrive, or arrived too late to use (S16/§6.10).
    static let sendANewCode = "Send a new code"
    /// The quiet way out of the sign-in thread, wherever it is offered —
    /// never a wall (§6.1, §6.10).
    static let neverMind = "Never mind"

    // MARK: Settings (S18–S22)

    // What each settings door leads to, under its title (ledger A23).
    static let textSub = "Translation, size, spacing, red letter"
    static let notificationsSub = "Per room, and your quiet hours"
    @MainActor static var downloadsSub: String { "What Scripture is held on this \(deviceNoun)" }
    static let planSub = "What your room has"
    // The lede under each screen's title: what the screen is, in a sentence.
    static let textLede = "How Scripture sets on the page."
    static let translationIsTheRooms = "Everyone in this room reads this one."
    static let thePageIsYours = "Yours alone. Nobody else's page moves."
    static let notificationsLede = "Every room asks for something different. These are per room."
    static let planLede = "What your room has, and when Ribbon asks."
    @MainActor static var downloadsLede: String { "What is on this \(deviceNoun), and what isn't." }
    static let thePage = "The page"
    static let notesLeftForYouSub = "When they leave one at a verse."
    static let cardsOpenSub = "When everyone has answered."
    static let whenTheyOpenTheBookSub = "So you can read at the same time."
    static let thinkingOfYouSub = "A touch on the shoulder. No words."
    static let textSizeSub = "Scripture only. Everything else stays where it is."
    static let lineSpacingSub = "How much air between the lines."
    static let redLetterSub = "Where the text marks them."
    @MainActor static var bundledSub: String { "On this \(deviceNoun) already, whole." }
    @MainActor static var streamsSub: String { "Streams. The book you are in stays on the \(deviceNoun)." }
    static let quietHoursFrom = "From"
    static let quietHoursUntil = "Until"
    /// iOS isn't passing these on (S19): the switch lives in Settings, and
    /// the app can only point at it.
    static let iOSIsNotPassingTheseOn = "iOS isn't passing these on."
    static let openIOSSettings = "open Settings"

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
    static let startTheRoomAgain = "Start the room again"
    static let firstBookFree = "The first book is free, all the way through."
    static let manageInStore = "Manage in the App Store"

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
    /// The wordmark and the build, in the quietest voice there is (S18) —
    /// the one line on that screen that is for us rather than for the
    /// reader.
    static func versionLine(_ version: String) -> String { "\(wordmark) \(version)" }
    /// Megabytes on this device (S21) — a count about a device, not about a
    /// person, which is the one honest exception to Law 2 and the same
    /// boundary `noRoomOnPhone` states.
    static func megabytes(_ count: Int) -> String { "\(count) MB" }

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

    // MARK: Failure surfaces (S25) — name what happened, name what didn't,
    // offer the one action that helps. No "oops," no "sorry," no error codes.
    static let signInCodeWrong = "That code didn't work. Try again, or send a new one."
    static let cardDeclined = "The card was declined. Nothing changed in your room."
    static func bookNotDownloaded(_ book: String) -> String { "\(book) isn't downloaded yet. It'll finish on Wi-Fi." }
    /// A licensed chapter that would not stream (ledger A45): the page says
    /// so where the words would be, and offers the one thing that helps.
    @MainActor static func chapterWouldntCome(_ book: String) -> String { "\(book) isn't on this \(deviceNoun) yet." }
    static let serverUnreachable = "Can't reach Ribbon right now."
    static let micNeeded = "Ribbon needs the microphone to record a note."
    @MainActor static func noRoomOnPhone(_ megabytes: Int) -> String {
        // A count about a device, not about a person — the boundary of
        // Law 2, stated so nobody over-applies the rule into unusability.
        "There's no room on this \(deviceNoun) for the recording. It needs about \(megabytes) MB."
    }

    // MARK: Notifications (§10.3) — there are six. There will never be a
    // seventh that is about absence.
    static func notifNoteLeft(_ name: String, _ verse: String) -> String { "\(name) left you a note at \(verse)" }
    static func notifNotesLeft(_ name: String) -> String { "\(name) left you a note" }
    static let notifCardsOpen = "The cards are open"
    static func notifReading(_ name: String, _ book: String) -> String { "\(name) is reading \(book)" }
    static func notifThinkingOfYou(_ name: String) -> String { name }
    static func notifFinished(_ book: String) -> String { "You finished \(book) together" }
    /// The title over `notifFinished` — the only notification with one,
    /// because the body is a sentence about the room and the title says
    /// what kind of thing arrived.
    static let aBookFinished = "A book finished"
}

/// The first word of a name, for the places that say it in passing. Kept
/// out of `Copy` because it is not a string: it is the rule for one.
func firstName(_ name: String) -> String {
    let trimmed = name.trimmingCharacters(in: .whitespaces)
    let first = trimmed.split(separator: " ", maxSplits: 1).first.map(String.init) ?? ""
    return first.isEmpty ? name : first
}
