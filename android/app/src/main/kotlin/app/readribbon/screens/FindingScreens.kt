@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Bible
import app.readribbon.core.Knock
import app.readribbon.core.OpenRoom
import app.readribbon.core.OwnLine
import app.readribbon.core.Person
import app.readribbon.core.PublicFace
import app.readribbon.design.Air
import app.readribbon.design.Chevron
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonScreen
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.SectionLabel
import app.readribbon.design.Setting
import app.readribbon.design.SettingNote
import app.readribbon.design.SettingSwitch
import app.readribbon.design.SettingsGroup
import app.readribbon.design.SmallCaps
import app.readribbon.design.TextInset
import app.readribbon.design.WayInButton
import app.readribbon.design.pressablePaper
import app.readribbon.fire.CampfireGlyph
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Being found (A72): finding an open room, asking into it, a found person,
// and your own being found. Pushed inside the menu, as pages.
// FindingScreens.swift, screen for screen.
//
// Nothing here is a number. A found room says its name or its people, its
// line, the book it is reading and its fire — never how many are in it — and
// an ask is answered by somebody letting you in, or by nothing at all.

/** The finding screens the menu pushes, as navigation routes. */
internal object FindRoute {
    const val FIND = "find"
    const val ROOM_PATTERN = "found-room/{roomID}"
    const val PERSON_PATTERN = "found-person/{personID}"
    const val BEING_FOUND = "being-found"
    const val ROOM_ID = "roomID"
    const val PERSON_ID = "personID"

    fun room(id: Uuid): String = "found-room/$id"
    fun person(id: Uuid): String = "found-person/$id"
}

/** What a found room is called: its own name, or the people in it who chose to be found. */
internal fun foundRoomName(room: OpenRoom): String = room.shownName ?: Copy.roomOf(room.firstNames)

/** Between the groups on these pages, as the menu spaces its own. */
private val Gap = 28.dp

/** `.padding(.horizontal, 60)` around the asking button. */
private val ButtonInset = 60.dp

/** [QuietControl] pads itself by 8 dp; pulled back so its words sit on the text's edge. */
private val QuietInset = (-8).dp

// MARK: - Find a room

@Composable
internal fun FindRoomScreen(
    model: AppModel,
    onBack: () -> Unit,
    onRoom: (OpenRoom) -> Unit,
    onPerson: (PublicFace) -> Unit,
    onBeingFound: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf(emptyList<OpenRoom>()) }
    var people by remember { mutableStateOf(emptyList<PublicFace>()) }
    // An answer has come back at least once, so an empty one means empty.
    var looked by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // Looks once the typing stops, so each letter is not its own search; a
    // newer question cancels the older one, and so its slower answer.
    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(350)
        val asked = query
        try {
            val (r, p) = coroutineScope {
                val foundRooms = async { model.findRooms(asked) }
                val foundPeople = async { model.findPeople(asked) }
                foundRooms.await() to foundPeople.await()
            }
            rooms = r
            people = p
            failed = false
            (r.flatMap { it.faces } + p).forEach(model::lookAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            failed = true
        }
        looked = true
    }

    RibbonScreen(title = Copy.FIND_A_ROOM, lede = Copy.FIND_A_ROOM_LEDE, onBack = onBack) {
        LineField(
            value = query,
            onValueChange = { query = it },
            placeholder = Copy.FIND_PROMPT,
            imeAction = ImeAction.Search,
            onDone = {},
        )

        if (!model.beingFound.findable) {
            Air(Gap)
            SettingsGroup(count = 1, footnote = Copy.ASK_NEEDS_BEING_FOUND) {
                Setting(title = Copy.BEING_FOUND, subtitle = Copy.BEING_FOUND_SUB, onClick = onBeingFound)
            }
        }

        if (failed) {
            Air(Gap)
            SettingsGroup(count = 1) { SettingNote(Copy.SERVER_UNREACHABLE) }
        } else if (looked && rooms.isEmpty() && people.isEmpty()) {
            Air(Gap)
            SettingsGroup(count = 1) {
                SettingNote(if (OwnLine.clean(query) == null) Copy.NO_ROOM_IS_OPEN_YET else Copy.NOTHING_OPEN_MATCHES)
            }
        }

        if (rooms.isNotEmpty()) {
            Air(Gap)
            SectionLabel(Copy.OPEN_ROOMS)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rooms.forEach { room ->
                    key(room.id) { FoundRoomTile(model = model, room = room) { onRoom(room) } }
                }
            }
        }

        if (people.isNotEmpty()) {
            Air(Gap)
            SectionLabel(Copy.PEOPLE)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                people.forEach { face ->
                    key(face.id) { FoundPersonTile(model = model, face = face) { onPerson(face) } }
                }
            }
        }
    }
}

/**
 * One line to type on, in the paper the paste field wears: the search, an
 * ask, your line. Onboarding's own field, so every place the app asks for a
 * few words is one thing drawn again.
 */
@Composable
private fun LineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onDone: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    CentredTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        size = 16f,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() },
            onSearch = { onDone() },
            onSend = { onDone() },
        ),
        focusRequester = focusRequester,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.surface)
            .border(1.dp, Palette.rule, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * A found room as a tile: its name or its people, its line, faces, the book,
 * and the fire. The same paper as a room of your own.
 */
@Composable
internal fun FoundRoomTile(model: AppModel, room: OpenRoom, onClick: () -> Unit) {
    val state = room.fireState(Clock.System.now())
    val scale = room.fire?.scale
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 64.dp)
            .pressablePaper(RibbonShape.rowShape, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = TextInset, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = foundRoomName(room), style = RibbonType.ui(16f), color = Palette.text)
            room.line?.let { line ->
                Text(text = line, style = RibbonType.ui(13f), color = Palette.muted, maxLines = 2)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoundFaces(model = model, faces = room.faces, size = 18.dp)
                room.bookID?.let { id -> Bible.book(id)?.let { SmallCaps(it.name, size = 11f) } }
            }
        }
        if (room.asked) SmallCaps(Copy.ASKED, size = 11f)
        if (state != null && scale != null) {
            Row(Modifier.semantics { contentDescription = Copy.fireIs(state.displayName) }) {
                CampfireGlyph(state = state, scale = scale, height = 22.dp)
            }
        }
    }
}

/** A found person as a tile: face, name, and their line. */
@Composable
internal fun FoundPersonTile(model: AppModel, face: PublicFace, onClick: () -> Unit) {
    LaunchedEffect(face.id) { model.lookAt(face) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 58.dp)
            .pressablePaper(RibbonShape.rowShape, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = TextInset, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortraitView(
            person = Person(id = face.id, name = face.name),
            size = 36.dp,
            image = model.face(face),
            modifier = Modifier.clearAndSetSemantics {},
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = face.name, style = RibbonType.ui(17f), color = Palette.text)
            face.line?.let { Text(text = it, style = RibbonType.ui(13f), color = Palette.muted) }
        }
        Chevron()
    }
}

/** The faces of a found room's findable people, overlapped as a room's are. */
@Composable
private fun FoundFaces(model: AppModel, faces: List<PublicFace>, size: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        faces.forEach { face ->
            PortraitView(person = Person(id = face.id, name = face.name), size = size, image = model.face(face))
        }
    }
}

// MARK: - A found room

@Composable
internal fun FoundRoomScreen(
    model: AppModel,
    roomID: Uuid,
    onBack: () -> Unit,
    onPerson: (PublicFace) -> Unit,
    onBeingFound: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var room by remember { mutableStateOf<OpenRoom?>(null) }
    var composing by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    LaunchedEffect(roomID) {
        try {
            room = model.openRoom(roomID)
            room?.faces?.forEach(model::lookAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            refusal = Copy.SERVER_UNREACHABLE
        }
        loading = false
    }

    fun send(open: OpenRoom) {
        if (sending) return
        sending = true
        refusal = null
        scope.launch {
            try {
                model.askToJoin(open, saying = said)
                composing = false
                said = ""
                room = room?.copy(asked = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                refusal = AppModel.askRefusal(e)
            }
            sending = false
        }
    }

    val shown = room
    RibbonScreen(
        title = shown?.let(::foundRoomName) ?: Copy.FIND_A_ROOM,
        lede = shown?.line,
        onBack = onBack,
    ) {
        when {
            loading -> SmallCaps(
                Copy.WORDMARK, size = 12f, color = Palette.muted.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = TextInset),
            )
            shown == null -> SettingsGroup(count = 1) { SettingNote(refusal ?: Copy.ROOM_IS_NOT_OPEN) }
            else -> {
                val book = shown.bookID?.let { Bible.book(it) }
                if (book != null) {
                    val state = shown.fireState(Clock.System.now())
                    val scale = shown.fire?.scale
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 58.dp)
                            .clip(RibbonShape.rowShape)
                            .background(Palette.surface)
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = TextInset, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = Copy.readingBook(book.name), style = RibbonType.ui(17f), color = Palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (state != null && scale != null) {
                            Row(Modifier.semantics { contentDescription = Copy.fireIs(state.displayName) }) {
                                CampfireGlyph(state = state, scale = scale, height = 26.dp)
                            }
                        }
                    }
                    Air(Gap)
                }

                if (shown.faces.isNotEmpty()) {
                    SectionLabel(Copy.PEOPLE)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        shown.faces.forEach { face ->
                            key(face.id) { FoundPersonTile(model = model, face = face) { onPerson(face) } }
                        }
                    }
                    Air(Gap)
                }

                when {
                    shown.asked -> SettingsGroup(count = 1, footnote = Copy.YOU_ASKED) {
                        Setting(title = Copy.TAKE_BACK_YOUR_ASK, chevron = false, onClick = {
                            refusal = null
                            scope.launch {
                                try {
                                    model.takeBackAsk(shown)
                                    room = room?.copy(asked = false)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (e: Exception) {
                                    refusal = Copy.SERVER_UNREACHABLE
                                }
                            }
                        })
                    }
                    shown.full -> SettingsGroup(count = 1) { SettingNote(Copy.FOUND_ROOM_IS_FULL) }
                    !model.beingFound.findable -> SettingsGroup(count = 1, footnote = Copy.ASK_NEEDS_BEING_FOUND) {
                        Setting(title = Copy.BEING_FOUND, subtitle = Copy.BEING_FOUND_SUB, onClick = onBeingFound)
                    }
                    composing -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val focus = remember { FocusRequester() }
                        LineField(
                            value = said,
                            onValueChange = { said = it },
                            placeholder = Copy.ASK_LINE_PROMPT,
                            imeAction = ImeAction.Send,
                            onDone = { send(shown) },
                            focusRequester = focus,
                        )
                        WayInButton(
                            title = Copy.ASK,
                            enabled = !sending,
                            modifier = Modifier.padding(horizontal = ButtonInset),
                        ) { send(shown) }
                    }
                    else -> WayInButton(
                        title = Copy.ASK_TO_JOIN,
                        modifier = Modifier.padding(horizontal = ButtonInset),
                    ) { composing = true }
                }

                refusal?.let {
                    Text(
                        text = it, style = RibbonType.ui(14f), color = Palette.muted,
                        modifier = Modifier.padding(start = TextInset, end = TextInset, top = 12.dp),
                    )
                }

                Air(Gap)
                if (reported) {
                    Text(
                        text = Copy.REPORTED, style = RibbonType.ui(14f), color = Palette.muted,
                        modifier = Modifier.padding(horizontal = TextInset),
                    )
                } else {
                    QuietControl(
                        title = Copy.REPORT_THIS_ROOM,
                        modifier = Modifier.padding(start = TextInset).offset(x = QuietInset),
                    ) { reporting = true }
                }
            }
        }
    }

    if (reporting) {
        RibbonConfirmDialog(question = Copy.REPORT_QUESTION, onDismiss = { reporting = false }) {
            ConfirmChoice(title = Copy.REPORT, onClick = {
                reporting = false
                scope.launch {
                    try {
                        model.report(room = roomID)
                        reported = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        refusal = Copy.SERVER_UNREACHABLE
                    }
                }
            })
            ConfirmChoice(title = Copy.NEVER_MIND, onClick = { reporting = false })
        }
    }
}

// MARK: - A found person

@Composable
internal fun FoundPersonScreen(
    model: AppModel,
    face: PublicFace,
    onBack: () -> Unit,
    onRoom: (OpenRoom) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf(emptyList<OpenRoom>()) }
    var line by remember { mutableStateOf<String?>(null) }
    var reported by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<String?>(null) }
    val blocked = face.id in model.blockedPeople
    val isMe = face.id == model.me?.id

    LaunchedEffect(face.id) {
        model.lookAt(face)
        model.loadBlocked()
        rooms = runCatching { model.openRooms(face.id) }.getOrDefault(emptyList())
    }

    RibbonScreen(title = face.name, lede = face.line, onBack = onBack) {
        PortraitView(
            person = Person(id = face.id, name = face.name),
            size = 88.dp,
            image = model.face(face),
            modifier = Modifier.padding(horizontal = TextInset).clearAndSetSemantics {},
        )
        Air(Gap)

        if (rooms.isNotEmpty() && !blocked) {
            SectionLabel(Copy.ROOMS_THEYVE_OPENED)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rooms.forEach { room ->
                    key(room.id) { FoundRoomTile(model = model, room = room) { onRoom(room) } }
                }
            }
            Air(Gap)
        }

        line?.let {
            Text(
                text = it, style = RibbonType.ui(14f), color = Palette.muted,
                modifier = Modifier.padding(horizontal = TextInset),
            )
            Air(14.dp)
        }

        Column(
            modifier = Modifier.padding(start = TextInset).offset(x = QuietInset),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (blocked) {
                QuietControl(title = Copy.UNBLOCK) {
                    scope.launch {
                        try {
                            model.unblock(face.id)
                            rooms = runCatching { model.openRooms(face.id) }.getOrDefault(emptyList())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            line = Copy.SERVER_UNREACHABLE
                        }
                    }
                }
            } else if (!isMe) {
                QuietControl(title = Copy.BLOCK) { asking = Copy.blockQuestion(face.name) }
            }
            if (!reported && !isMe) {
                QuietControl(title = Copy.REPORT) { asking = Copy.REPORT_QUESTION }
            }
        }
    }

    asking?.let { question ->
        val isBlock = question != Copy.REPORT_QUESTION
        RibbonConfirmDialog(question = question, onDismiss = { asking = null }) {
            ConfirmChoice(
                title = if (isBlock) Copy.BLOCK else Copy.REPORT,
                destructive = isBlock,
                onClick = {
                    asking = null
                    scope.launch {
                        try {
                            if (isBlock) {
                                model.block(face)
                                rooms = emptyList()
                            } else {
                                model.report(person = face.id)
                                reported = true
                                line = Copy.REPORTED
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            line = Copy.SERVER_UNREACHABLE
                        }
                    }
                },
            )
            ConfirmChoice(title = Copy.NEVER_MIND, onClick = { asking = null })
        }
    }
}

// MARK: - Being found

@Composable
internal fun BeingFoundScreen(model: AppModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var line by remember { mutableStateOf(model.beingFound.line ?: "") }

    fun commit() {
        model.setOwnLine(line)
        line = model.beingFound.line ?: ""
    }

    LaunchedEffect(Unit) { model.loadBlocked() }
    // Leaving keeps what was typed, as the field losing the caret does on
    // the iPhone.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { commit() } }

    RibbonScreen(title = Copy.BEING_FOUND, lede = Copy.BEING_FOUND_LEDE, onBack = onBack) {
        if (model.beingFound.canBeFindable) {
            SettingsGroup(count = 1) {
                SettingSwitch(
                    title = Copy.LET_PEOPLE_FIND_YOU,
                    value = model.beingFound.findable,
                    onChange = { model.setFindable(it) },
                )
            }
        } else {
            SettingsGroup(count = 1, footnote = Copy.THIRTEEN_OR_OLDER_QUESTION) {
                Setting(title = Copy.I_AM_THIRTEEN_OR_OLDER, chevron = false, onClick = { model.sayThirteenOrOlder() })
            }
        }

        Air(Gap)
        SectionLabel(Copy.YOUR_LINE)
        LineField(
            value = line,
            onValueChange = { line = it },
            placeholder = Copy.YOUR_LINE_PROMPT,
            imeAction = ImeAction.Done,
            onDone = { commit() },
        )

        val blocked = model.blockedList
        if (blocked.isNotEmpty()) {
            Air(Gap)
            SettingsGroup(count = blocked.size, title = Copy.PEOPLE_YOUVE_BLOCKED) {
                blocked.forEach { (id, name) ->
                    Setting(title = name, value = Copy.UNBLOCK, chevron = false, onClick = {
                        scope.launch { runCatching { model.unblock(id) } }
                    })
                }
            }
        }
    }
}

// MARK: - Asks, in the room's menu

/**
 * Someone asking into this room: their face and what they said, and the two
 * answers. "Not now" tells them nothing; letting them in is how they hear.
 */
@Composable
internal fun KnockTile(model: AppModel, knock: Knock, onPerson: (PublicFace) -> Unit) {
    val scope = rememberCoroutineScope()
    var answering by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(knock.id) { model.lookAt(knock.person) }

    SettingsGroup(count = 3, footnote = refusal, footnoteAnnounces = true) {
        Setting(
            title = Copy.askedToJoin(knock.person.name),
            subtitle = knock.line ?: knock.person.line,
            chevron = false,
            onClick = { onPerson(knock.person) },
        )
        Setting(title = Copy.LET_THEM_IN, chevron = false, onClick = {
            if (answering) return@Setting
            answering = true
            refusal = null
            scope.launch {
                try {
                    model.letIn(knock)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    refusal = AppModel.askRefusal(e)
                }
                answering = false
            }
        })
        Setting(title = Copy.NOT_NOW, chevron = false, onClick = { model.setDown(knock) })
    }
}
