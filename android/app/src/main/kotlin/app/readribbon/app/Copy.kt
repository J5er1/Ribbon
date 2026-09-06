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
    const val PORTRAIT_REASON = "They'll see your face when you're reading."
    const val ADD_A_PORTRAIT = "Add a portrait"
    const val SKIP_PORTRAIT = "Not now"
    const val INVITE_SEND = "Send this to the person you're reading with."
    const val INVITE_LATER = "Invite later"
    const val PICK_A_BOOK = "Pick something to read together"
    const val FIRST_RUN_HINT = "Notes go in the margin. Hold a verse to leave one."

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

    // Reading (S02–S06)
    const val CLOSE_THE_BOOK = "Close the book"
    fun isWithYou(name: String) = "$name is with you"
    const val BACK_TO_WHERE_YOU_WERE = "back to where you were"
    const val READ_QUIETLY = "read quietly"
    const val ONLY_YOU_CAN_SEE_YOU = "only you can see you"
    const val HERE_BUT_STILL = "here, but still"
    const val TRANSCRIPT_COMING = "Transcript coming"
    const val NO_TRANSCRIPT = "No transcript for this one."
    const val TRY_AGAIN = "Try again"
    const val SEND_IT_NOW = "send it now"
    const val WRITE = "write"
    const val SPEAK = "speak"
    const val TAKE_BACK = "take back"
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
    const val PUT_IT_ON_THE_SHELF = "Put it on the shelf"
    fun finishedTogether(book: String) = "You finished $book together"

    // A person (S12)
    const val CHANGE_YOUR_INK = "Change your ink"
    const val LEAVE_THIS_ROOM = "Leave this room"
    const val LEAVE_ROOM_CONFIRM = "Leave this room? You'll keep the books on your shelf."
    const val LEAVE_NOTES_QUESTION =
        "Leave your notes behind? They were left for the other person."
    const val LEAVE_THEM = "Leave them"
    const val TAKE_THEM_BACK = "Take them back"

    // The chooser (S13)
    const val GOOD_PLACES_TO_START = "Good places to start together"
    const val NOTHING_MATCHES = "Nothing matches that."
    const val SEARCH = "Search"

    // Rooms (S14/S15/S16)
    const val START_A_ROOM_CONTROL = "Start a room"
    const val YOU = "You"
    const val PAUSED = "paused"
    const val ROOM_NAME = "Room name"
    const val NAME_THIS_ROOM = "Name this room"
    const val ROOM_HOLDS_SIX = "A room holds six. Start another for the rest."

    /** The same fact, said to the person arriving — "the rest" are the
     *  inviter's people, not theirs. */
    const val ROOM_FULL_FOR_JOINER = "This room is full. Ask them to start another."
    const val INVITE_EXPIRED = "This invite has expired. Ask for a new one."
    fun wantsToReadWithYou(name: String) = "$name wants to read with you."
    const val JOIN = "Join"
    const val SOMEONE_WANTS_TO_READ_WITH_YOU = "Someone wants to read with you."
    const val INVITE_NEEDS_SIGN_IN = "Sign in first, so the link can bring them to your room."
    const val PASTE_INVITE_PROMPT = "Paste the link they sent you"
    const val THAT_LINK_ISNT_AN_INVITE = "That doesn't look like an invite link."

    // Sign-in (§6.10) — an emailed code, no passwords. The account exists for
    // one reason, said plainly.
    const val ACCOUNT_REASON = "An account carries your room between phones."
    const val YOUR_EMAIL = "Your email"
    const val SEND_THE_CODE = "Send the code"
    const val CODE_ON_ITS_WAY = "A code is on its way to your email."
    const val THE_CODE = "The code"
    const val SIGN_IN = "Sign in"

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
