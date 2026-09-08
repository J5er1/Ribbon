package app.readribbon.app

import android.content.Context

// Every user-facing string, in one place, so the voice rules (§10) can be
// audited: short sentences, second person, no exclamation points, never name
// the person who hasn't done a thing, say the true small thing.
//
// Never used anywhere: streak · don't break the chain · accountability
// partner · engagement · daily challenge · crush it · level up · unlock ·
// rewards · devotional journey · spiritual walk · community · you missed
// yesterday · get back on track.
//
// These are Kotlin constants rather than string resources on purpose, and
// it is the same call the iOS build made: the copy IS the product here, and
// having it in one readable file — with the reasoning attached in comments —
// is worth more right now than the localisation machinery. Localisation,
// when it comes, moves this file into strings.xml wholesale.

object Copy {

    // Onboarding (S17 / §6.1)
    const val TAGLINE = "Read it together."
    const val WHO_IS_READING = "Who's reading with you?"
    const val START_A_ROOM = "Start a room"
    const val HAVE_AN_INVITE = "I have an invite"
    const val YOUR_NAME = "Your name"

    /**
     * The control that takes the typed name (S17), and the same control on
     * the joiner's side (S16). Swift writes it inline in both flows; every
     * user-facing string belongs here, so it was lifted on the way across.
     */
    const val THATS_ME = "That's me"
    const val PORTRAIT_REASON = "They'll see your face when you're reading."
    const val ADD_A_PORTRAIT = "Add a portrait"

    /** The same control, said to somebody who already has a face behind it. */
    const val CHANGE_YOUR_PORTRAIT = "Change your portrait"

    /**
     * What tapping your own name does, as a click label — the room header
     * names its two doors the same way.
     */
    const val EDITS_YOUR_NAME = "edit your name"
    const val SKIP_PORTRAIT = "Not now"
    const val INVITE_SEND = "Send this to the person you're reading with."
    const val INVITE_LATER = "Invite later"
    const val PICK_A_BOOK = "Pick something to read together"
    const val FIRST_RUN_HINT = "Notes go in the margin. Hold a verse to leave one."

    // Walkthrough Tour (Duolingo-style feature walkthrough)
    const val WALKTHROUGH_VISION_TITLE = "Read Scripture together."
    const val WALKTHROUGH_VISION_BODY = "Ribbon is made for reading with one person or a few — a partner on the same couch, or a friend four time zones away."
    const val WALKTHROUGH_PRESENCE_TITLE = "See each other on the page."
    const val WALKTHROUGH_PRESENCE_BODY = "A soft presence appears when someone is reading at the same time. Quiet companionship without noisy notifications."
    const val WALKTHROUGH_PRESENCE_SAMPLE = "Ruth is reading right now"
    const val WALKTHROUGH_NOTES_TITLE = "Notes left behind."
    const val WALKTHROUGH_NOTES_BODY = "Pin written reflections or voice memos to specific verses, waiting silently for the other person to discover later."
    const val WALKTHROUGH_NOTE_SAMPLE = "0:42 · Left this for you"
    const val WALKTHROUGH_FIRE_TITLE = "A fire kept alive together."
    const val WALKTHROUGH_FIRE_BODY = "No gamified streak counters or cold badge scores. Just a single warm ember your room keeps burning together."
    const val WALKTHROUGH_INTENT_TITLE = "Who will you read with?"
    const val WALKTHROUGH_INTENT_SPOUSE = "My spouse or partner"
    const val WALKTHROUGH_INTENT_FRIEND = "A close friend"
    const val WALKTHROUGH_INTENT_GROUP = "A small study or family"
    const val WALKTHROUGH_INTENT_SOLO = "Starting on my own first"
    const val ALREADY_HAVE_ACCOUNT = "Already have an account? Sign in"
    const val CONTINUE_TOUR = "Continue"
    const val SKIP_TOUR = "Skip"
    const val GET_STARTED = "Get Started"

    // The room (S01)
    fun continueIn(book: String) = "Continue in $book"
    fun begin(book: String) = "Begin $book"
    const val INVITE_STILL_OUT = "The invite is still out."
    const val SEND_IT_AGAIN = "Send it again"
    const val MARK_A_QUIET_DAY = "Mark a quiet day"
    const val ROOM_PAUSED = "The room is paused. It can be started again any time."
    fun readRecently(name: String, phrase: String) = "$name read $phrase"
    fun leftYouANote(name: String, verse: String) = "$name left you a note at $verse"
    fun bankedTheFire(name: String) = "$name banked the fire"

    /**
     * The two pieces of chrome at the top of the room, said aloud (§11).
     *
     * Swift writes these inline as `.accessibilityHint("Opens your rooms")`
     * and `.accessibilityHint("Your account and settings")`. Compose has no
     * hint field; the nearest honest thing is the click action's own label,
     * which TalkBack reads as "double tap to open your rooms" — so the same
     * fact is phrased as the action rather than as its consequence, and it
     * lives here because a screen reader's sentence is copy like any other.
     */
    const val OPEN_YOUR_ROOMS = "open your rooms"
    const val OPEN_YOUR_ACCOUNT = "open your account and settings"

    /**
     * The fire's screen-reader label (§11). Law 2 in one line: a state, a
     * full stop, and nothing else — never a percentage, never a count,
     * never "how long since". [stateName] is [app.readribbon.core.FireState.displayName].
     */
    fun fireIs(stateName: String) = "The fire is $stateName."

    // Reading (S02–S06)

    /**
     * How one verse announces itself to a screen reader (§11): the verse,
     * a full stop, then the words. Law 2 — it says what it is and never
     * where you are in it, so there is no "verse 9 of 41".
     */
    fun verseSpoken(verse: Int, body: String) = "Verse $verse. $body"

    /**
     * A mark in the gutter, read aloud (§11), exactly: "Note from Ruth,
     * verse 9, not yet found." A stack announces by author and never by
     * count — [authors] names who left them, [several] only chooses the
     * noun, and nothing anywhere says how many. Your own notes are "you".
     */
    fun marginNotes(
        authors: List<String>,
        verse: Int,
        several: Boolean,
        unfound: Boolean,
    ): String {
        val who = if (authors.isEmpty()) "you" else authors.joinToString(" and ")
        val noun = if (several) "Notes" else "Note"
        return "$noun from $who, verse $verse" + if (unfound) ", not yet found" else ""
    }

    const val CLOSE_THE_BOOK = "Close the book"
    fun isWithYou(name: String) = "$name is with you"
    const val BACK_TO_WHERE_YOU_WERE = "back to where you were"
    const val READ_QUIETLY = "read quietly"
    const val ONLY_YOU_CAN_SEE_YOU = "only you can see you"
    const val HERE_BUT_STILL = "here, but still"

    /** The small closed shape at the edge, read aloud (§11): the state, and
     *  what it means, in the two short sentences the form itself would say. */
    const val READING_QUIETLY_SPOKEN = "Reading quietly. Only you can see you."

    // Presence, read aloud (§11). Law 2 holds here as it does for the fire:
    // a state and a name, never a count. Several people announce by author.
    fun personIsReading(name: String) = "$name is reading"

    fun personIsHereButStill(name: String) = "$name is here, but still"

    /** "Ruth is reading, with Ann and Mara" — who else is here, named. */
    fun alsoHere(base: String, others: List<String>) =
        base + ", with " + others.joinToString(" and ")

    /** Tap a portrait to follow — the action, spoken. */
    const val FOLLOW = "Follow"

    const val TRANSCRIPT_COMING = "Transcript coming"
    const val NO_TRANSCRIPT = "No transcript for this one."
    const val TRY_AGAIN = "Try again"

    /** The disclosure under a voice note: the transcript is there, folded. */
    const val TRANSCRIPT = "transcript"

    /** The waveform's screen-reader label (§11) — what happens, never how
     *  long it is. A duration is a count (S04). */
    const val PLAY_THE_VOICE_NOTE = "Play the voice note"

    /** An open note, read aloud: who left it, then what it says. */
    fun noteFrom(name: String, body: String) = "Note from $name. $body"

    fun voiceNoteFrom(name: String, transcript: String) = "Voice note from $name. $transcript"

    const val SEND_IT_NOW = "send it now"
    const val WRITE = "write"
    const val SPEAK = "speak"
    const val TAKE_BACK = "take back"

    /** The one control that leaves a written note (S05). Save is a single
     *  control; there is no draft state to name. */
    const val LEAVE_IT = "leave it"

    /** Speak is press-and-hold (S05): the two things the finger can do,
     *  said under the live waveform. Neither of them is a duration. */
    const val RELEASE_TO_LEAVE_IT = "release to leave it"
    const val LET_GO_TO_DISCARD = "let go to discard"

    /** An ink swatch, for a screen reader (§11): the ink's own name. */
    fun inkNamed(name: String) = "$name ink"
    const val EDIT = "edit"
    const val REMOVE = "remove"
    const val NEW_NOTES_NEED_THE_ROOM = "New notes need the room started again."
    const val INKS_FROM_WHEN_THE_ROOM_WAS_TWO =
        "These keep their colors. They're from when the room was two."

    // The shelf and embers (S10/S11)
    const val START_ANOTHER = "Start another"
    const val ONE_DAY_THIS_IS_A_BOOK = "One day this is a book."
    const val READ_IT_AGAIN = "Read it again"
    const val STRAIGHT_THROUGH = "You read this one straight through."

    /**
     * The control under your own open note on an ember record (S11).
     * Sharing is plain text only, and only your own words or the verse
     * itself — never someone else's note.
     */
    const val SHARE = "share"

    /** What leaves the app when you share your own note: the address, then
     *  the words. Nothing about the room, and nobody else's name. */
    fun sharedNote(verse: String, body: String) = "$verse — $body"
    const val PUT_IT_ON_THE_SHELF = "Put it on the shelf"
    fun finishedTogether(book: String) = "You finished $book together"

    /** The finishing sequence (§6.5), for a screen reader: the become is
     *  the whole event, so it announces as one thing and says what it is
     *  — never how far along it is. */
    const val THE_FIRE_SETTLES_INTO_AN_EMBER = "The fire settles into an ember."

    // A person (S12)
    const val CHANGE_YOUR_INK = "Change your ink"
    const val LEAVE_THIS_ROOM = "Leave this room"
    const val LEAVE_ROOM_CONFIRM = "Leave this room? You'll keep the books on your shelf."
    const val LEAVE_NOTES_QUESTION =
        "Leave your notes behind? They were left for the other person."
    const val LEAVE_THEM = "Leave them"
    const val TAKE_THEM_BACK = "Take them back"

    /**
     * The way out of a confirmation. iOS supplies this button itself — every
     * `confirmationDialog` gets a Cancel, so the Swift never names one — and
     * Compose's dialog has only the choices it is handed. Leaving asks once,
     * plainly, with no guilt (§6.8); a question with no way to say no is not
     * asked plainly.
     */
    const val STAY = "Stay"

    /** The heading over the eight swatches when ink is identity (§4.5). */
    const val YOUR_INK = "your ink"

    /**
     * One ink in that picker, read aloud (§11): its name, then whether it is
     * already yours or already someone else's. Never whose — a taken ink says
     * only that it is taken.
     */
    fun inkSwatchSpoken(name: String, yours: Boolean, taken: Boolean) =
        name + (if (yours) ", yours" else "") + (if (taken) ", taken" else "")

    // The chooser (S13)
    const val GOOD_PLACES_TO_START = "Good places to start together"
    const val NOTHING_MATCHES = "Nothing matches that."
    const val SEARCH = "Search"

    /**
     * A book in the chooser, read aloud (§11). The drawn fire is the only
     * length indicator on that screen, so the label says the same thing the
     * drawing says — a size, as a word. Law 2: never a chapter count, never
     * a word count, never "about fourteen hours".
     *
     * [scale] is [app.readribbon.core.FireScale]'s own name — "small",
     * "medium", "large" — which is what the iOS build reads from its raw
     * value. Swift composes this label inline in the chooser; it lives here
     * because a screen reader's sentence is copy like any other.
     */
    fun bookIsAFire(book: String, scale: String, onShelf: Boolean = false) =
        "$book, a $scale fire" + if (onShelf) ", on your shelf" else ""

    // Rooms (S14/S15/S16)
    const val START_A_ROOM_CONTROL = "Start a room"
    const val YOU = "You"
    const val PAUSED = "paused"
    const val ROOM_NAME = "Room name"

    /** The room-name field's placeholder: naming a room is never required. */
    const val OPTIONAL = "Optional"

    /**
     * The invite control (S15). The link is the whole mechanism, and the
     * control says exactly what happens when it is tapped.
     *
     * Swift writes this one inline in `InviteSheet`; every user-facing
     * string belongs here, so it was lifted on the way across.
     */
    const val SEND_THE_INVITE = "Send the invite"
    const val NAME_THIS_ROOM = "Name this room"
    const val ROOM_HOLDS_SIX = "A room holds six. Start another for the rest."

    /** The same fact, said to the person arriving — "the rest" are the
     *  inviter's people, not theirs. */
    const val ROOM_FULL_FOR_JOINER = "This room is full. Ask them to start another."
    const val INVITE_EXPIRED = "This invite has expired. Ask for a new one."
    fun wantsToReadWithYou(name: String) = "$name wants to read with you."
    const val JOIN = "Join"
    const val SOMEONE_WANTS_TO_READ_WITH_YOU = "Someone wants to read with you."

    /**
     * The held beat while the invite is fetched (S16) — the wordmark, in the
     * quietest voice there is, because nothing in Ribbon is visibly loading
     * (§8). It answers fast, or the dead line takes its place.
     */
    const val WORDMARK = "ribbon"

    /** The join in flight, said once and quietly. */
    const val JOINING = "joining"

    /**
     * A dead end presented over the room still needs its own way out, not
     * only the swipe.
     */
    const val CLOSE = "Close"
    const val INVITE_NEEDS_SIGN_IN = "Sign in first, so the link can bring them to your room."
    const val PASTE_INVITE_PROMPT = "Paste the link they sent you"
    const val THAT_LINK_ISNT_AN_INVITE = "That doesn't look like an invite link."

    /**
     * The line over the paste field (S15/S17). The link is the whole
     * mechanism: tapping it is the way in, and pasting is only there for
     * when the link was sent somewhere this device can't tap it from.
     *
     * Swift writes this one inline in `OnboardingFlow`.
     */
    const val OPEN_THE_LINK = "Open the link they sent you. It brings you straight into their room."

    /**
     * The quiet way out of the invite question, and out of every dead end in
     * the join thread (S16/S17) — never a step without a way out. Swift
     * writes it inline, in both flows that offer it.
     */
    const val START_A_ROOM_INSTEAD = "Start a room instead"

    // The menu (S14 + S18, one screen — see docs/deviations.md 14). Three
    // section heads, so the rooms, the room you are in, and you are three
    // things rather than one pile.
    const val ROOMS = "Rooms"
    const val THIS_ROOM = "This room"
    const val ACCOUNT = "Account"

    /**
     * Inviting someone to the room you are already in (S15). The room
     * screen's own line only appears while a room of one still has its first
     * invite out; this is the way in from two members to six.
     */
    const val INVITE_SOMEONE = "Invite someone"

    /**
     * Accepting an invite to another room when you already have one (S16).
     * The tapped link does this by itself; this is the same door, for a link
     * that landed somewhere this phone can't tap it from.
     */
    const val JOIN_WITH_AN_INVITE = "Join with an invite"

    /**
     * The line over the menu's paste field. [OPEN_THE_LINK] tells you to
     * open the link, which is exactly what a person standing here could not
     * do — the link landed on a laptop, or in a thread this phone can't
     * open. So this one states the fact and lets the field's own prompt do
     * the asking.
     */
    const val THE_LINK_BRINGS_YOU_IN = "The link they sent you brings you into their room."

    /**
     * The room with no name and no members but you (S15) — a room is named
     * by whoever is in it, and until somebody is, this.
     */
    const val YOUR_ROOM = "Your room"

    // Sign-in (§6.10) — an emailed code, no passwords. The account exists for
    // one reason, said plainly.
    const val ACCOUNT_REASON = "An account carries your room between phones."
    const val YOUR_EMAIL = "Your email"
    const val SEND_THE_CODE = "Send the code"
    const val CODE_ON_ITS_WAY = "A code is on its way to your email."
    const val THE_CODE = "The code"
    const val SIGN_IN = "Sign in"

    // Passkeys (§6.10 — "a passkey where available, an emailed code
    // otherwise"). Always an addition to the code, never a replacement, so
    // the words offer rather than instruct.
    const val USE_A_PASSKEY = "Use a passkey"
    const val ADD_A_PASSKEY = "Add a passkey"
    const val PASSKEY_REASON = "Then signing in is your face or your PIN, on any phone."

    /**
     * The true small thing, said once, where the reason used to be. Nothing
     * in Ribbon congratulates anybody.
     */
    const val PASSKEY_ADDED = "This phone can sign you in now."

    /**
     * S25's shape: name what happened, name what didn't, and leave the one
     * thing that still works in front of them.
     */
    const val PASSKEY_DIDNT_WORK = "That passkey didn't work. The emailed code still does."
    const val SIGN_IN_WITH_AUTH0 = "Continue with Auth0"
    const val AUTH0_DIDNT_WORK = "Signing in didn't finish. The emailed code still does."
    const val SEND_A_NEW_CODE = "Send a new code"

    /** The way past sign-in, wherever a host offers one. Never a wall. */
    const val NEVER_MIND = "Never mind"

    // Settings (S18–S22)
    const val TEXT_AND_TRANSLATION = "Text"
    const val NOTIFICATIONS = "Notifications"
    const val DOWNLOADS = "Downloads"
    const val PLAN = "Plan"
    const val SIGN_OUT = "Sign out"
    const val DELETE_ACCOUNT = "Delete account"
    const val TRANSLATION = "Translation"
    const val TEXT_SIZE = "Text size"
    const val LINE_SPACING = "Line spacing"
    const val RED_LETTER = "Words of Jesus in red"
    const val NOTES_LEFT_FOR_YOU = "Notes left for you"
    const val CARDS_OPEN = "The cards open"
    const val WHEN_THEY_OPEN_THE_BOOK = "When they open the book"
    const val THINKING_OF_YOU = "Thinking of you"
    const val QUIET_HOURS = "Quiet hours"
    const val START_THE_ROOM_AGAIN = "Start the room again"
    const val FIRST_BOOK_FREE = "The first book is free, all the way through."
    const val MANAGE_IN_STORE = "Manage in Google Play"

    /**
     * The three steps of the line-spacing control (S20). Swift sets these
     * three words inline in its segmented picker; every user-facing string
     * belongs here, so they were lifted on the way across — and named apart
     * from [CLOSE], which is a way out of a dead end rather than a measure
     * of leading.
     */
    const val LINE_SPACING_CLOSE = "Close"
    const val LINE_SPACING_BOOK = "Book"
    const val LINE_SPACING_OPEN = "Open"

    /** Between the two ends of quiet hours (S19). Swift writes it inline. */
    const val QUIET_HOURS_TO = "to"

    /**
     * What quiet hours do not silence (S19): the one notification that is a
     * touch rather than a sentence, said plainly so nobody is surprised by
     * it. Swift writes it inline.
     */
    const val THINKING_OF_YOU_STILL_ARRIVES =
        "Thinking of you still arrives, silently, as a touch."

    /**
     * A licensed translation on the downloads list (S21). The book being
     * read stays on the device and the rest doesn't — its license, not our
     * design — so the row says what it does instead of a size. Swift writes
     * it inline.
     */
    const val STREAMS = "streams"

    /**
     * Where the ask lives, said once (S22). Swift writes it inline. A
     * non-paying member never sees a price and never learns who pays.
     */
    const val THE_ASK_COMES_ONCE =
        "When your room's first ember is on the shelf, Ribbon will ask — there, and only there."

    /**
     * Deleting the account asks §6.8's question — [LEAVE_NOTES_QUESTION] —
     * and these are its two answers. Swift writes both inline in its
     * confirmation dialog. Neither is the quiet one: deleting is deliberate
     * either way, and the notes were left for the other person.
     */
    const val DELETE_AND_LEAVE_THEM = "Delete, and leave them"
    const val DELETE_AND_TAKE_THEM_BACK = "Delete, and take them back"

    /**
     * The wordmark and the build, in the quietest voice there is (S18) —
     * the one line on that screen that is for us rather than for the reader.
     * Swift composes it inline from the bundle's short version string.
     */
    fun versionLine(version: String) = "$WORDMARK $version"

    /**
     * Megabytes on this device (S21) — a count about a device, not about a
     * person, which is the one honest exception to Law 2 and the same
     * boundary [noRoomOnPhone] states. Swift interpolates it inline.
     */
    fun megabytes(count: Int) = "$count MB"

    /**
     * The way back out of a settings screen (S20–S22).
     *
     * iOS supplies this for free: a `NavigationStack` push draws a back
     * chevron and names it for VoiceOver itself. Compose draws nothing, and
     * a screen whose only way back is a gesture fails §11 — so the chevron
     * is drawn here, and this is what it says aloud.
     */
    const val BACK = "Back"

    /**
     * "Phone" is the concrete noun the voice wants — but on a tablet it is
     * simply wrong, so the noun follows the device.
     *
     * iOS tests the interface idiom, which Android has no equivalent of; the
     * closest honest test is the smallest width the device ever reports,
     * which is a property of the hardware rather than of the current window.
     * A phone in a freeform window is still a phone, and a tablet with the
     * app in a narrow split is still a tablet — which is exactly the
     * behaviour the iOS build gets from idiom.
     */
    fun deviceNoun(context: Context): String =
        if (context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"

    fun onThisPhone(context: Context) = "On this ${deviceNoun(context)}"

    fun keepEverything(context: Context) = "Keep everything on this ${deviceNoun(context)}"

    fun voiceNotesPolicy(context: Context) =
        "Voice notes stay on this ${deviceNoun(context)} for the current reading."

    // Failure surfaces (S25) — name what happened, name what didn't, offer
    // the one action that helps. No "oops," no "sorry," no error codes.
    const val SIGN_IN_CODE_WRONG = "That code didn't work. Try again, or send a new one."
    const val CARD_DECLINED = "The card was declined. Nothing changed in your room."
    fun bookNotDownloaded(book: String) = "$book isn't downloaded yet. It'll finish on Wi-Fi."
    const val SERVER_UNREACHABLE = "Can't reach Ribbon right now."
    const val MIC_NEEDED = "Ribbon needs the microphone to record a note."

    /** Refused once, the speak control offers one route and never asks
     *  again (S25). */
    const val OPEN_SETTINGS = "open settings"

    /** A count about a device, not about a person — the boundary of Law 2,
     *  stated so nobody over-applies the rule into unusability. */
    fun noRoomOnPhone(context: Context, megabytes: Int) =
        "There's no room on this ${deviceNoun(context)} for the recording. " +
            "It needs about $megabytes MB."

    // Notifications (§10.3) — there are six. There will never be a seventh
    // that is about absence.
    fun notifNoteLeft(name: String, verse: String) = "$name left you a note at $verse"
    fun notifNotesLeft(name: String) = "$name left you a note"
    const val NOTIF_CARDS_OPEN = "The cards are open"
    fun notifReading(name: String, book: String) = "$name is reading $book"
    fun notifThinkingOfYou(name: String) = name
    fun notifFinished(book: String) = "You finished $book together"
}

/** First names only, everywhere a person is addressed in a line of copy. */
fun firstName(name: String): String = name.trim().substringBefore(' ').ifEmpty { name }
