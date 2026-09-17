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

    /**
     * The control under it.
     *
     * Not the same words: a control says exactly what happens, and a button
     * repeating the sentence directly above it says nothing twice.
     */
    const val PICK_A_BOOK_CONTROL = "Pick a book"
    const val FIRST_RUN_HINT = "Notes go in the margin. Hold a verse to leave one."

    /**
     * The one time the app asks about notifications (§6.1).
     *
     * "Notifications: after the first note is left or found — never at
     * launch. In context: *Tell you when Ruth leaves a note?*" — the build
     * book gives this string, and the name in it is the whole reason it is
     * allowed to be asked at all. A version with no name is a notification
     * pre-prompt, which S17 forbids by name; this one is a question about a
     * person, asked at the moment a note has just passed between two people.
     *
     * Asked once, whatever the answer. There is no "not now", because a
     * question that comes back is worse than no question.
     */
    fun tellYouWhen(name: String) = "Tell you when ${firstName(name)} leaves a note?"

    /** Its two answers. Verbs, like every other pair in the app. */
    const val TELL_ME = "Tell me"
    const val DONT_TELL_ME = "Don't"

    // The tour (deviation A25 — S17 says "four questions, no tour", and the
    // owner's call overruled it).
    //
    // The tour exists; its words are held to §10 like every other string in
    // this file, and they were not. Four separate rules were being broken on
    // four cards, on the fourth screen a person ever sees:
    //
    //   - **"streak" shipped, on screen.** It is the first entry on §10.2's
    //     Never list and on the brief's §12, and this file's own header says
    //     "Never used anywhere: streak" — which made the header false about
    //     the file it heads. Naming the competitor's mechanic puts a streak
    //     counter in the reader's head on the fourth screen of the product
    //     that exists to refuse it.
    //   - **A duration.** "0:42" on a mock voice note is a count attached to
    //     reading (Law 2), on the one surface the law guards hardest, and
    //     [PLAY_THE_VOICE_NOTE] twenty lines down says so in its own comment.
    //     The real note draws a waveform and no timecode.
    //   - **The product described by negation.** Three of the four bodies
    //     said what Ribbon is not. §10.1 asks for the true small thing, and
    //     §12 is explicit that grace is the interface being unbothered rather
    //     than the interface reassuring you.
    //   - **The wrong nouns.** A note is always *left*, never pinned, because
    //     being found later is the beat (§11); "reflections" is the cards,
    //     which are a different object; and a burning fire is not "an ember",
    //     which is what you keep when a book is finished.
    //
    // Second person, one short sentence each, and the vocabulary §11 settled.
    /**
     * Not the tagline, and deliberately not a near-miss of it.
     *
     * This read "Read Scripture together." — the settled tagline (§10.4) with
     * a word added — one screen after the mark moment has just shown the
     * tagline itself. Either the first card's heading is the tagline or it is
     * something else; a version of it with an extra word in it is the one
     * thing it cannot be.
     */
    const val WALKTHROUGH_VISION_TITLE = "Read with someone."
    const val WALKTHROUGH_VISION_BODY =
        "Read with one person, or a few. On the same couch, or four time zones away."
    const val WALKTHROUGH_PRESENCE_TITLE = "See each other on the page."
    const val WALKTHROUGH_PRESENCE_BODY =
        "When someone else is reading, you see them beside you on the page."
    const val WALKTHROUGH_PRESENCE_SAMPLE = "Ruth is reading right now"
    const val WALKTHROUGH_NOTES_TITLE = "Notes left behind."
    const val WALKTHROUGH_NOTES_BODY =
        "Leave a note at a verse, written or spoken. They find it when they get there."
    const val WALKTHROUGH_NOTE_SAMPLE = "Left this for you"
    const val WALKTHROUGH_FIRE_TITLE = "A fire you keep together."
    const val WALKTHROUGH_FIRE_BODY =
        "One book is one fire. It catches, burns, goes steady, and is banked on a quiet day."
    const val WALKTHROUGH_INTENT_TITLE = "Who will you read with?"
    const val WALKTHROUGH_INTENT_SPOUSE = "My spouse or partner"
    const val WALKTHROUGH_INTENT_FRIEND = "A close friend"
    const val WALKTHROUGH_INTENT_GROUP = "A small study or family"
    const val WALKTHROUGH_INTENT_SOLO = "Starting on my own first"
    const val CONTINUE_TOUR = "Continue"
    const val SKIP_TOUR = "Skip"
    const val GET_STARTED = "Get Started"

    // The room (S01)

    /**
     * The first words on the screen.
     *
     * The room used to open on its own name in 12 sp small caps, which is a
     * filing label, and then on a fire. Nothing on the app's front door ever
     * spoke to the person holding it. This does, once, in the plainest form
     * the voice allows: three variants, first name only, a full stop, and no
     * second sentence. §12's rules are doing real work here — an exclamation
     * point, an emoji, a verse of the day or a line about how long it has
     * been would each turn this into the church bulletin §13 refuses.
     *
     * Nameless until onboarding has a name to use, which is the one case
     * where the greeting stands alone rather than guessing.
     */
    fun greeting(name: String?, hour: Int): String {
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (name.isNullOrBlank()) "$part." else "$part, ${firstName(name)}."
    }

    // Presence is now said out loud on the room screen rather than only to a
    // screen reader: `personIsReading`, `personIsHereButStill` and `alsoHere`
    // below already existed and had no visible call site anywhere in the app.
    // The friendliest copy in the product was invisible. Alone, the line is
    // absent — never a sentence about being alone (§08).

    /** The head over what is waiting (S01's "What's waiting", in the voice). */
    const val LEFT_FOR_YOU = "Left for you"

    /** The head over the embers (S10). */
    const val THE_SHELF = "The shelf"

    /**
     * The hearth's own gesture, said once and then never again — the same
     * contract the margin hint keeps (§6.1). A hint that comes back is worse
     * than no hint.
     */
    const val PULL_THE_FIRE_UP = "Pull the fire up to open the book"

    /**
     * First run, under "Pick something to read together". Says what a fire
     * *is* without saying how long anything takes: a size, as a word.
     */
    const val FIRST_FIRE_HINT = "Whatever you pick becomes a fire. A short book makes a small one."

    /**
     * The empty place in the seats when a room can still hold somebody
     * (S15). It is a place kept, not an absence — which is why it is drawn
     * as a seat and says what tapping it does.
     */
    const val AN_OPEN_SEAT = "An open seat. Invite someone."

    fun continueIn(book: String) = "Continue in $book"
    fun begin(book: String) = "Begin $book"
    const val INVITE_STILL_OUT = "The invite is still out."
    const val SEND_IT_AGAIN = "Send it again"
    const val MARK_A_QUIET_DAY = "Mark a quiet day"
    const val ROOM_PAUSED = "The room is paused. It can be started again any time."
    fun readRecently(name: String, phrase: String) = "$name read $phrase"
    fun leftYouANote(name: String, verse: String) = "$name left you a note at $verse"

    /**
     * The same row, for a voice note.
     *
     * The gutter has told a voice note from a written one since §4.4 — a
     * solid dot against an open ring — and the room's own waiting row said
     * "left you a note" for both, so a screen reader was told the wrong
     * thing about half of them. The drawn mark now has a sentence to match.
     */
    fun leftYouAVoiceNote(name: String, verse: String) =
        "$name left you a voice note at $verse"
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

    /**
     * A verse's two actions, for somebody who cannot make the gestures they
     * sit on (§11 Motor: "every gesture has a tap equivalent").
     *
     * The whole of the reading interaction was raw pointer input on the text
     * — a long-press-drag to lift a verse, a tap to open what is at it — and
     * the per-verse accessibility nodes carried a label and nothing else. So
     * the semantics tree exposed the verses as read-only strings and exposed
     * no action at all for either gesture: leaving a highlight, a written
     * note or a voice note all begin at that long press, and every one of
     * them was closed. It also closed them to anybody who cannot hold a press
     * for the platform timeout and then drag.
     *
     * Extending a range stays drag-only, which is honest: the toolbar acts on
     * whatever is lifted, and one verse is the common case.
     */
    const val OPEN_WHATS_HERE = "open what's here"
    const val LEAVE_SOMETHING_HERE = "leave something here"

    const val CLOSE_THE_BOOK = "Close the book"
    fun isWithYou(name: String) = "$name is with you"
    const val BACK_TO_WHERE_YOU_WERE = "back to where you were"
    const val READ_QUIETLY = "read quietly"
    const val ONLY_YOU_CAN_SEE_YOU = "only you can see you"
    const val HERE_BUT_STILL = "here, but still"

    /** The small closed shape at the edge, read aloud (§11): the state, and
     *  what it means, in the two short sentences the form itself would say. */
    const val READING_QUIETLY_SPOKEN = "Reading quietly. Only you can see you."

    /**
     * What opening the presence form does, for somebody who cannot make the
     * gesture that opens it (§11 Motor).
     *
     * The lozenge carried a sentence and no action, and the sentence was on a
     * merge root that took the focus for itself — so the node with the
     * gestures on it was never landed on, and everything behind the form was
     * closed: following the one person present, the panel itself, and the
     * only route in the whole app to reading quietly.
     */
    const val WHOS_HERE = "who's here"

    /**
     * The speak control, announced as one thing.
     *
     * Its two custom actions sat on a node with no label and no merge, so —
     * by the same rule — a screen reader never landed on it and neither
     * action could be reached. The drawn line under the waveform says
     * "Release to leave it", which is an instruction for a finger that is not
     * down; this names the two things that can happen instead.
     */
    const val RECORDING_A_VOICE_NOTE = "Recording. Leave it, or take it back."

    // Presence, read aloud (§11). Law 2 holds here as it does for the fire:
    // a state and a name, never a count. Several people announce by author.
    fun personIsReading(name: String) = "$name is reading"

    fun personIsHereButStill(name: String) = "$name is here, but still"

    /** "Ruth is reading, with Ann and Mara" — who else is here, named. */
    fun alsoHere(base: String, others: List<String>) =
        base + ", with " + others.joinToString(" and ")

    /** Tap a portrait to follow — the action, spoken. */
    const val FOLLOW = "Follow"

    /** Sending away something that would have gone on its own (§11: a
     *  gesture the eye can see is a gesture a screen reader can hear). */
    const val DISMISS = "dismiss"

    // The two ends of a selection (S06), and the tap equivalents of dragging
    // them (§11). Named for the mark rather than for the selection, because a
    // "selection" is a thing a text editor has and this is a thing you are
    // about to write on Scripture with.
    const val WHERE_THE_MARK_STARTS = "Where the mark starts"
    const val WHERE_THE_MARK_ENDS = "Where the mark ends"
    const val A_VERSE_FURTHER_ON = "A verse further on"
    const val A_VERSE_BACK = "A verse back"
    const val A_WORD_FURTHER_ON = "A word further on"
    const val A_WORD_BACK = "A word back"

    const val TRANSCRIPT_COMING = "Transcript coming"
    const val NO_TRANSCRIPT = "No transcript for this one."
    const val TRY_AGAIN = "Try again"

    /** The disclosure under a voice note: the transcript is there, folded. */
    const val TRANSCRIPT = "transcript"

    /** What the disclosure does, as a click label. The word "transcript" on
     *  its own names the thing and not the act (§11). */
    const val SHOWS_THE_TRANSCRIPT = "show the transcript"
    const val HIDES_THE_TRANSCRIPT = "hide the transcript"

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

    /**
     * The composer's field, for a screen reader (§11).
     *
     * S05 draws no label and no prompt over it — the verse's own reference
     * sits above the field and the field is the rest of the card — so there
     * is nothing on screen to borrow a name from and it is said here. It was
     * the app's central writing surface and an unlabelled edit box, which is
     * the defect A25 fixed across onboarding's three fields and A35a fixed on
     * You without either of them reaching this one.
     */
    const val WHAT_YOU_WANT_TO_SAY = "What you want to say"

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
    /**
     * S01's third waiting row — "notes left for you, cards open, **an ink to
     * pick**" — which the room had never drawn.
     *
     * §6.7 is exactly this sentence: when a room becomes three, "the two
     * originals get an invitation on the room screen to pick an ink. Not a
     * blocking dialog; it waits." And the newcomer "picks from what's left",
     * which is the same invitation seen from the other side — their
     * membership arrives with no ink at all, so without this their marks fell
     * back to a colour that might already be somebody else's.
     *
     * An invitation, not an instruction, and it says why it is here: the room
     * is bigger than it was, and colour means a person now (§4.5).
     */
    const val PICK_AN_INK = "Colour is a person now. Pick your ink."


    // The cards (S08/S09, §4.6)
    //
    // Every word on a reflection card used to be a literal inside
    // `ReflectionCardView`, which is why the card is the one surface in the
    // app that had never been through the copy system. Two of them were
    // wrong the moment a room held three people, and one of them was
    // "SET IT DOWN" typed in capitals — §09 sets small caps as a real face,
    // and shouting a lowercase phrase at the type system is precisely what
    // that rule exists to prevent.

    /**
     * The line under a sealed card, exactly as §4.6 sets it.
     *
     * Never "Ruth hasn't answered yet" and never "1 of 2 answered": naming
     * the person who hasn't turns the card into an accusation, and counting
     * the ones who have is Law 2 in the one place it is most tempting to
     * break. It says only that the card is waiting, which is the feature.
     */
    const val CARD_OPENS_WHEN_EVERYONE_HAS_ANSWERED =
        "This opens when everyone has answered."

    /** Any member may retire a sealed card, for the room, at any time —
     *  §4.6's pressure valve, so a card can wait forever without becoming a
     *  debt. Lowercase, because [SmallCaps] sets the caps. */
    const val SET_IT_DOWN = "set it down"

    /** Your answer is yours until the moment the card opens (§4.6). */
    const val EDIT_YOUR_ANSWER = "Edit your answer"

    /** The control that keeps what you typed. A verb, like every other
     *  control in the app, and never "Submit" or "Done". */
    const val ANSWER = "Answer"

    /** Backing out of an edit, leaving the answer you had already given. */
    const val KEEP_WHAT_I_HAD = "Keep what I had"

    /** The field itself, for a screen reader. S08 asks for an open field
     *  with "no placeholder text beyond a single hairline", so there is no
     *  visible prompt to borrow a label from and the label is said here. */
    const val YOUR_ANSWER = "Your answer"

    /** A sealed card, announced as one thing (§11): the question, then the
     *  state it is in. Never who is missing, never how many. */
    fun cardSealedSpoken(question: String) =
        "$question. $CARD_OPENS_WHEN_EVERYONE_HAS_ANSWERED"

    /** An open card, announced as one thing: the question, then that it is
     *  open. The answers under it announce themselves, by author. */
    fun cardOpenSpoken(question: String) = "$question. The card is open."

    /** One answer on an open card, read aloud: whose it is, then what they
     *  said. First names only (§10), and no time — a card is a moment, not
     *  a thread, so there is nothing to timestamp. */
    fun answerFrom(name: String, answer: String) = "$name answered. $answer"

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

    /**
     * The head over what somebody has left in this room.
     *
     * "Left" always, never posted or sent — being found later is the
     * emotional beat (§11).
     */
    fun whatTheyLeft(name: String) = "What $name left"

    /**
     * One of their notes, read aloud (§11). The kind is drawn as a mark and
     * the mark has no words of its own, so the row says which it is before it
     * says anything else — otherwise a voice note's transcript and a written
     * note's body announce identically.
     */
    fun aVoiceNoteAt(verse: String) = "Voice note at $verse"

    fun aNoteAt(verse: String) = "Note at $verse"
    const val WHAT_YOU_LEFT = "What you left"

    /**
     * S12 with nothing in it.
     *
     * Impersonal on purpose. The head above the list reads "What Ruth left",
     * and the moment that is shown over nothing it is a head naming the
     * person who has not done the thing — which §10.1 forbids by name. So
     * when there is nothing, there is no head and no tile either: a drawn
     * container announcing an absence is §4.2's placeholder mistake. One
     * line, on the bare ground, true of your own screen as well as theirs,
     * which is why it needs no name.
     *
     * This is the first-run state of every person screen in the product
     * (§6.1), and before this it was blank ground under a name.
     */
    const val NOTHING_LEFT_HERE_YET = "Nothing left in this room yet."

    /**
     * A note nobody has found yet, on S12 — the same clause §11 specifies for
     * a gutter mark ("not yet found"), in the running-head voice.
     *
     * An unfound note shows its address and not its words, which is A22a's
     * call and right: §6.3's whole beat is being found later, and a list that
     * read every unfound note aloud would spend it before anybody opened the
     * book. But an address alone is only "an invitation to go" if the row
     * says it is one — without this, an unfound note and a voice note whose
     * transcription failed render identically, and a screen reader hears a
     * bare address either way.
     */
    const val NOT_YET_FOUND = "not yet found"

    /**
     * The ink line on S12, read aloud (§11).
     *
     * "Colour is never alone": a 6 dp dot cannot be the only signal of whose
     * ink this is, and the name of a colour on its own — "Crimson", floating
     * between a name and a list — says nothing at all. No possessive and no
     * name in it, because the screen's own heading is already the person.
     */
    fun inkSpoken(yours: Boolean, ink: String) =
        (if (yours) "Your ink" else "Their ink") + ", " + ink

    /**
     * Somebody the app has a membership for and no profile yet.
     *
     * A seat can be tapped before a profile has synced, and `person?.name ?:
     * ""` rendered an empty heading over a head reading "What  left". A
     * screen with no name on it reads as a failure rather than as a wait.
     */
    const val SOMEONE = "Someone"

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
    /**
     * The app's one first-person-plural, removed.
     *
     * "We couldn't find this invite" was the only user-visible string in the
     * tree containing we, our or us — and it stood on the dead end of the
     * join thread, which is the screen S16 says must show "a person, not a
     * product". "We" summons a support desk onto it. Every other S25 line in
     * this file names the thing that failed rather than the company that
     * failed it, and this one now matches its own sibling above.
     */
    const val INVITE_NOT_FOUND = "That invite isn't there any more. Ask for a new one."
    fun wantsToReadWithYou(name: String) = "$name wants to read with you."
    const val JOIN = "Join"
    const val SOMEONE_WANTS_TO_READ_WITH_YOU = "Someone wants to read with you."

    /**
     * Who this phone is about to join as (S16's last state: "signed in as
     * someone else — offers to switch, does not silently join").
     *
     * Said only when there is somebody to name. A tap on Join used to seat
     * whoever the phone happened to be signed in as without ever saying so,
     * and the only route to another account was the sign-out control two taps
     * deep in the menu.
     */
    fun joiningAs(name: String) = "You'll join as $name."
    const val JOIN_AS_SOMEONE_ELSE = "Join as someone else"

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

    // The menu's two doors, each now its own screen (deviation A29). The
    // room's name is the room's title — a large Material title names the
    // thing you are looking at, and "Settings" is not a thing anybody is
    // looking at. The ledes say what is behind each, in one sentence, in the
    // app's own voice.
    // The ribbon (deviation A30) and the chapter list (A31).
    //
    // Every line here names a *place*. Nothing subtracts the ribbon from
    // where you are, nothing calls anybody ahead or behind, and nothing is
    // counted — an address is not a measure, which is the whole reason a
    // shared ribbon is allowed to exist beside §03 at all.
    const val CHAPTERS = "Chapters"
    const val GO_THERE = "Go there"
    const val THE_RIBBON = "The ribbon"

    /** "Ruth left the ribbon at Mark 4:9", or the same without a name. */
    fun ribbonIsAt(who: String?, reference: String): String =
        if (who == null) "The ribbon is at $reference." else "$who left the ribbon at $reference."

    /** What you left, said back to you — never "you are at". */
    fun youLeftTheRibbonAt(reference: String): String = "You left the ribbon at $reference."

    fun chapterYouAreHere(heading: String): String = "$heading, where you are"

    fun chapterHasRibbon(heading: String): String = "$heading, where the ribbon is"

    fun chapterYouAreHereWithRibbon(heading: String): String =
        "$heading, where you are and where the ribbon is"

    const val ROOM_LEDE = "Who is in it, what it tells you, and the rooms you are in."
    const val YOUR_ROOMS = "Your rooms"

    // The You screen's own sections (deviation A29a). Each one answers "what
    // is this about" rather than naming a category: how you read, this phone,
    // your account. A heading that says "General" is a heading that gave up.
    const val HOW_YOU_READ = "How you read"
    const val THIS_PHONE = "This phone"
    const val YOUR_ACCOUNT = "Your account"

    /** Beside the face, saying where it is seen. Never "profile photo". */
    const val YOUR_FACE_REASON = "Your name and face are what the room sees."

    /**
     * The same sentence when there is no face yet, which is the one moment it
     * should also be an invitation. One line doing both jobs, because the
     * small-caps label that used to do the second one sat under a circle that
     * is now plainly a face you can touch (A48).
     */
    const val ADD_A_PORTRAIT_REASON =
        "Your name and face are what the room sees. Tap to add one."

    const val TAP_TO_CHANGE = "Tap to change"

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
     *
     * This one is for *signing in* with a passkey, where the emailed code
     * genuinely is the other way through the same door.
     */
    const val PASSKEY_DIDNT_WORK = "That passkey didn't work. The emailed code still does."

    /**
     * And this one is for *adding* a passkey on You, where the sign-in
     * sentence above is wrong twice over: nothing was being signed into, so
     * "that passkey" names a thing that was never made, and "the emailed code
     * still does" offers a way in to somebody who is already in.
     *
     * **It asserts only what is known, which took two goes.** The first
     * attempt said "Nothing changed, and you are still signed in", and both
     * halves of that can be false. The credential is made on the
     * authenticator *before* the verify call goes out, so a failure on the
     * second leg leaves a passkey on the phone — something changed. And both
     * legs now go through `withAuthRetry`, which signs the person out when a
     * refresh is refused — so the line could be read aloud to somebody it had
     * just signed out, over a screen still showing their email address.
     *
     * What is true in every case is that it did not finish, and that trying
     * again is safe.
     */
    const val PASSKEY_WASNT_ADDED = "Adding the passkey didn't finish. You can try again."
    /**
     * The hosted sign-in, named for what it does rather than for who runs it.
     *
     * "Continue with Auth0" was the one place in the product where a person
     * reading Scripture with their partner was shown the name of a vendor.
     * §12's voice is "an unbothered interface" and §10.1 has no room for an
     * infrastructure brand on the control that opens the app; the identity
     * provider is a decision this app made, not a thing the reader has an
     * account with or has heard of. It is a browser opening, so the copy says
     * that and nothing else.
     */
    const val SIGN_IN_IN_A_BROWSER = "Continue in a browser"
    const val AUTH0_DIDNT_WORK = "Signing in didn't finish. The emailed code still does."
    const val SEND_A_NEW_CODE = "Send a new code"

    /** The way past sign-in, wherever a host offers one. Never a wall. */
    const val NEVER_MIND = "Never mind"

    // Settings (S18–S22, and S26 — Appearance)

    /**
     * The subtitles under every settings row.
     *
     * New in this pass, and half the reason the settings stopped reading as
     * a list of nouns. A row that says only "Downloads" makes you open it to
     * find out what it is; a row that says what is actually on the phone has
     * answered already. Each one states a fact about *your* copy of the app,
     * never a feature description.
     */
    const val TEXT_SUB = "Translation, size, spacing, red letter"
    const val NOTIFICATIONS_SUB = "Per room, and your quiet hours"
    /**
     * A function, not a constant, for the reason deviation A8 exists: the
     * concrete noun follows the hardware. The screen this row opens says
     * "phone" three times, and the row a tap above it said "device".
     */
    fun downloadsSub(context: Context) = "What Scripture is held on this ${deviceNoun(context)}"
    /**
     * Not "what it costs". S22 puts the ask in exactly two places — the
     * shelf after the first ember, and the Plan screen — and a menu row is a
     * third. The row says what the screen is about; the screen says the rest.
     */
    const val PLAN_SUB = "What your room has"

    // S26 — Appearance. New screen (deviation A18).
    const val APPEARANCE = "Appearance"
    const val WALLPAPER_COLOUR = "Colour from your wallpaper"

    /**
     * Why the switch is there, in one sentence that says what happens rather
     * than what the feature is called. "Material You" is Google's word for
     * it and means nothing to a reader; "your wallpaper" is the thing they
     * actually chose.
     */
    const val WALLPAPER_COLOUR_WHY =
        "The room borrows the colours Android draws from your wallpaper. " +
            "Turn it off for Ribbon's own chartreuse."

    /**
     * The two states, said as the state rather than as on or off — and
     * sentence case, because on the menu's row this is the line under the
     * title rather than a small-caps value beside it.
     */
    const val FROM_YOUR_WALLPAPER = "From your wallpaper"
    const val RIBBONS_OWN = "Ribbon's own colours"

    /**
     * The one thing the wallpaper never repaints, said where somebody might
     * otherwise wonder whether it was an oversight.
     */
    /**
     * "Chrome" is a designer's word. It is used correctly all over this
     * repository's comments and means nothing at all to somebody reading the
     * app, which makes it the one string in this pass that broke §12's rule
     * about saying the true small thing plainly.
     */
    const val THE_FIRE_STAYS_WARM =
        "The fire keeps its own warmth either way, and so does everybody's ink. " +
            "Those are the two things here the wallpaper never repaints."

    /**
     * The one line under each settings screen's title.
     *
     * New, and doing more than it looks like. Every one of them states a rule
     * the app has always kept and has never said out loud — that your
     * translation is yours and not the room's (§2.6), that notifications are
     * per room because one answer to two different questions is the wrong
     * answer (S19). A setting you understand is friendlier than a setting
     * that is merely well spaced.
     */
    const val TEXT_LEDE = "How Scripture sets on the page."

    /**
     * Said under the version, because a setting that quietly changes what
     * somebody else sees has to say so before they touch it (A42).
     *
     * Not "everyone must agree" and not a warning: it is a room reading one
     * book together, which is the product, and the sentence is a statement of
     * that rather than a caution about it. It names no one, per §10.1 — a
     * line saying *Ruth is reading the Berean* would make a shared choice
     * feel like somebody else's property.
     */
    const val TRANSLATION_IS_THE_ROOMS = "Everyone in this room reads this one."

    /**
     * Said under the page settings, which stayed personal. The contrast with
     * the line above is the whole point: one of these screens' two halves is
     * shared and the other is not, and a reader should not have to find that
     * out by changing something.
     */
    const val THE_PAGE_IS_YOURS = "Yours alone. Nobody else's page moves."
    const val NOTIFICATIONS_LEDE = "Every room asks for something different. These are per room."

    /**
     * What S19 says when Android is dropping everything on it.
     *
     * The four switches are the person's answer to Ribbon's question and stay
     * exactly as they set them; this is the OS's answer to a different one,
     * and it is said plainly and once. Greying the switches out would make
     * Android's decision look like Ribbon's, which is Law 5 backwards.
     *
     * No "oops", no exclamation point, and no scolding: it names what is
     * happening and where it is decided, and then stops (S25).
     */
    const val ANDROID_IS_NOT_PASSING_THESE_ON =
        "Android isn't passing these on."
    const val OPEN_ANDROIDS_SETTINGS = "open Android's settings"
    const val APPEARANCE_LEDE = "Ribbon's colours, or your phone's."
    /**
     * Not "and what it costs". The screen cannot say what it costs — §6.11
     * and S22 are explicit that a non-paying member never sees a price — so
     * a lede promising one is the app volunteering the single fact that
     * member is meant never to learn, and then failing to deliver it.
     */
    const val PLAN_LEDE = "What your room has, and when Ribbon asks."
    fun downloadsLede(context: Context) = "What is on this ${deviceNoun(context)}, and what isn't."

    /** The two groups on the text screen. */
    const val THE_PAGE = "The page"

    /** The one group on Appearance. */
    const val COLOUR = "Colour"

    // The subtitles under the switches and the controls. Each says the true
    // small thing about what the setting does, in the app's own voice —
    // never a feature description, never a benefit.
    const val NOTES_LEFT_FOR_YOU_SUB = "When they leave one at a verse."

    /**
     * "Both" was wrong from three people up, and §2.3 says a room holds six.
     * A card opens when *everyone* has answered (§4.6), which is also the
     * word the card's own waiting line uses — so the switch and the card now
     * describe the same event in the same word.
     */
    const val CARDS_OPEN_SUB = "When everyone has answered."
    const val WHEN_THEY_OPEN_THE_BOOK_SUB = "So you can read at the same time."
    const val THINKING_OF_YOU_SUB = "A touch on the shoulder. No words."
    const val TEXT_SIZE_SUB = "Scripture only. Everything else stays where it is."
    const val LINE_SPACING_SUB = "How much air between the lines."
    const val RED_LETTER_SUB = "Where the text marks them."

    /** What a translation is, told as a fact about this phone. */
    fun bundledSub(context: Context) = "On this ${deviceNoun(context)} already, whole."
    fun streamsSub(context: Context) =
        "Streams. The book you are in stays on the ${deviceNoun(context)}."

    /** The two ends of quiet hours, as rows rather than as a sentence. */
    const val QUIET_HOURS_FROM = "From"
    const val QUIET_HOURS_UNTIL = "Until"

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

    // Updates
    fun updateAvailable(version: String) = "A new version of Ribbon is ready ($version)"
    const val UPDATE_NOW = "Update"
    fun updateDownloading(percent: Int) = "Downloading update · $percent%"
    const val UPDATE_READY_TO_INSTALL = "Ready to install · Tap to restart"
    const val UPDATE_FAILED = "Couldn't complete update. Tap to retry."
    const val CHECKING_FOR_UPDATES = "Checking for updates…"

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
    /**
     * A licensed translation whose chapter could not be fetched (S25's "book
     * won't download", §08's "name it, name what's intact, one action").
     *
     * The page used to draw the running head and 320 dp of nothing, with the
     * fetch's failure swallowed by a `runCatching` and the effect keyed so it
     * could never retry while the book was open. Offline, on NKJV, that is a
     * blank page under a heading with no line and no way forward.
     *
     * The waiting state stays wordless on purpose — §08 forbids a loading
     * indicator and a skeleton reads as fake text — so this appears only once
     * the fetch has actually missed.
     */
    fun chapterWouldntCome(book: String) = "$book isn't on this phone yet."

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

    /**
     * The channel a finished book posts on, as Android's own settings page
     * names it (S19).
     *
     * The other four channels take their names from the four switches, so
     * the OS and Ribbon say the same words about the same thing. This one has
     * no switch to borrow from — S19 is explicit that it has none, because it
     * fires a handful of times a year and is an invitation back rather than
     * an absence notification — so it is named here, in the same voice: the
     * event, not a feature.
     */
    const val A_BOOK_FINISHED = "A book finished"
}

/** First names only, everywhere a person is addressed in a line of copy. */
fun firstName(name: String): String = name.trim().substringBefore(' ').ifEmpty { name }
