package us.i3u.hermesstudio

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermesTheme {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
private fun App(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    when (state.screen) {
        Screen.Loading -> LoadingScreen(state.baseUrl)
        Screen.Onboarding -> OnboardingScreen(onDone = { viewModel.finishOnboarding() })
        Screen.Settings -> SettingsScreen(state, viewModel)
        Screen.Login -> LoginScreen(state, viewModel)
        Screen.Chats -> ChatsScreen(state, viewModel)
        Screen.Groups -> GroupsScreen(state, viewModel)
        Screen.Conversation -> ConversationScreen(state, viewModel)
        Screen.Room -> RoomScreen(state, viewModel)
        Screen.Profiles -> ProfilesScreen(state, viewModel)
    }
}

// ── login ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(state: UiState, viewModel: AppViewModel) {
    var url by rememberSaveable { mutableStateOf(state.baseUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { StudioTopBar("Hermes Studio") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connect to your server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server address") },
                placeholder = { Text("https://hermes.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.login(url, username, password) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "Connecting…" else "Sign in")
            }
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            Text(
                "Credentials are stored encrypted on this device and sent only to the address above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── conversation list ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsScreen(state: UiState, viewModel: AppViewModel) {
    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Chats",
                actions = {
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                    IconButton(onClick = { viewModel.refreshSessions() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.show(Screen.Profiles) }) {
                        Icon(Icons.Filled.Person, contentDescription = "Profiles")
                    }
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = { StudioTabs(state, viewModel) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ProfileFilterRow(state, viewModel)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (state.busy) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }

            if (!state.busy && state.sessions.isEmpty()) {
                EmptyNote("No conversations yet")
            } else {
                SectionHeader("CONVERSATIONS", state.sessions.size)
                LazyColumn {
                    items(state.sessions) { session ->
                        SessionRow(session) { viewModel.openSession(session) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatStamp(session.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            session.profile?.let { ProfileDot(it) }
            Spacer(Modifier.width(6.dp))
            Text(
                text = listOfNotNull(session.profile, session.model).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small coloured initial, standing in for Studio's generated profile avatar. */
@Composable
private fun ProfileDot(name: String) {
    val palette = listOf(0xFF18A058, 0xFF3B82F6, 0xFFB07CE8, 0xFFE8A33C, 0xFFE86C6C)
    val color = androidx.compose.ui.graphics.Color(palette[name.hashCode().mod(palette.size)])
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(color.copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun ProfileFilterRow(state: UiState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val label = state.profileFilter.ifBlank { "All profiles" }

    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = { open = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All profiles") },
                onClick = {
                    open = false
                    viewModel.setProfileFilter("")
                },
            )
            state.profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        open = false
                        viewModel.setProfileFilter(profile.name)
                    },
                )
            }
        }
    }
}

// ── group rooms ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsScreen(state: UiState, viewModel: AppViewModel) {
    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Group chat",
                actions = {
                    IconButton(onClick = { viewModel.refreshRooms() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        bottomBar = { StudioTabs(state, viewModel) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }

            if (!state.busy && state.rooms.isEmpty()) {
                EmptyNote("No rooms yet")
            } else {
                SectionHeader("ROOMS", state.rooms.size)
                LazyColumn {
                    items(state.rooms) { room ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openRoom(room) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    room.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    formatStamp(room.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${room.agentCount} agents · ${room.memberCount} members",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomScreen(state: UiState, viewModel: AppViewModel) {
    val room = state.openRoom
    Scaffold(
        topBar = {
            StudioTopBar(
                title = room?.name ?: "Room",
                subtitle = room?.agents?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                onBack = { viewModel.back() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loadingHistory) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            val messages = room?.messages.orEmpty()
            if (!state.loadingHistory && messages.isEmpty()) {
                EmptyNote("No messages in this room yet")
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message ->
                    MessageBubble(
                        ChatLine(
                            text = message.content,
                            fromUser = !message.isAgent,
                            timestamp = message.timestamp,
                            sender = message.sender,
                        ),
                    )
                }
            }
        }
    }
}

// ── conversation ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(state: UiState, viewModel: AppViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    LaunchedEffect(state.transcript) {
        state.transcript?.let { text ->
            draft = if (draft.isBlank()) text else "$draft $text"
            viewModel.consumeTranscript()
        }
    }

    val profile = state.openSession?.profile ?: state.activeProfile
    Scaffold(
        topBar = {
            StudioTopBar(
                title = state.openSession?.title ?: "New chat",
                subtitle = listOfNotNull(profile.ifBlank { null }, state.openSession?.model)
                    .joinToString(" · ")
                    .ifBlank { null },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.loadingHistory) LoadingRow()

            if (state.lines.isEmpty() && !state.loadingHistory) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Send a message to ${profile.ifBlank { "your agent" }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.lines) { line -> MessageBubble(line) }
                }
            }

            if (state.sending) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp))
                    Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }

            Composer(
                state = state,
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    viewModel.send(draft)
                    draft = ""
                },
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine) {
    val alignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val container = when {
        line.isError -> MaterialTheme.colorScheme.errorContainer
        line.fromUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(colors = CardDefaults.cardColors(containerColor = container)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                line.sender?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = line.text)
                val stamp = formatStamp(line.timestamp)
                if (stamp.isNotBlank()) {
                    Text(
                        stamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── profiles ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesScreen(state: UiState, viewModel: AppViewModel) {
    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Profiles",
                subtitle = state.account?.let { "Signed in as $it" },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { viewModel.refreshProfiles() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            LazyColumn {
                items(state.profiles) { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectProfile(profile.name) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileDot(profile.name)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                profile.model ?: "no model configured",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (profile.name == state.activeProfile) {
                            Text(
                                "active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    state: UiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    viewModel: AppViewModel,
) {
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<ComposerSheet?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readAndAttach(context, it, viewModel) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readAndAttach(context, it, viewModel) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        captureUri = null
        if (saved && uri != null) readAndAttach(context, uri, viewModel, fallbackName = "photo.jpg")
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = newCaptureUri(context)
            captureUri = uri
            takePhoto.launch(uri)
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording()
    }

    when (sheet) {
        ComposerSheet.Options -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            OptionsSheet(
                state = state,
                onCamera = {
                    sheet = null
                    askCamera.launch(Manifest.permission.CAMERA)
                },
                onGallery = {
                    sheet = null
                    pickImage.launch("image/*")
                },
                onDocument = {
                    sheet = null
                    pickFile.launch("*/*")
                },
                onModel = {
                    viewModel.loadModels()
                    sheet = ComposerSheet.Model
                },
                onReasoning = { sheet = ComposerSheet.Reasoning },
            )
        }

        ComposerSheet.Model -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = "Model",
                loading = state.loadingModels,
                rows = state.models.map { option ->
                    PickerRow(
                        label = option.id,
                        detail = option.provider,
                        selected = option.id == state.sessionModel,
                    ) {
                        viewModel.selectModel(option)
                        sheet = null
                    }
                },
            )
        }

        ComposerSheet.Reasoning -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = "Reasoning effort",
                loading = false,
                rows = REASONING_LEVELS.map { (value, label) ->
                    PickerRow(
                        label = label,
                        detail = if (value.isBlank()) "Use the profile default" else null,
                        selected = value == state.reasoningEffort,
                    ) {
                        viewModel.setReasoningEffort(value)
                        sheet = null
                    }
                },
            )
        }

        null -> Unit
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.attachments.isNotEmpty() || state.attaching) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.attachments.forEach { file ->
                    AssistChip(
                        onClick = { viewModel.removeAttachment(file) },
                        label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove") },
                    )
                }
                if (state.attaching) AssistChip(onClick = {}, label = { Text("Uploading…") })
            }
        }

        if (state.recording || state.transcribing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp))
                Text(
                    if (state.recording) "Recording…" else "Transcribing…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.recording) {
                    TextButton(onClick = { viewModel.stopRecordingAndAttach() }) { Text("Send audio") }
                    TextButton(onClick = { viewModel.cancelRecording() }) { Text("Cancel") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Type a message…") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
            )
            SendOrRecordButton(state, draft, onSend, viewModel) {
                askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Studio keeps its context controls on a row under the field.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !state.sending) { sheet = ComposerSheet.Options },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            ToolbarChip(
                icon = Icons.Filled.Psychology,
                label = REASONING_LEVELS.firstOrNull { it.first == state.reasoningEffort }?.second ?: "Default",
            ) {
                sheet = ComposerSheet.Reasoning
            }
            ToolbarChip(
                icon = Icons.Filled.WbSunny,
                label = state.sessionModel ?: "Model",
            ) {
                viewModel.loadModels()
                sheet = ComposerSheet.Model
            }
        }
    }
}

private enum class ComposerSheet { Options, Model, Reasoning }

private val REASONING_LEVELS = listOf(
    "" to "Default",
    "low" to "Low",
    "medium" to "Medium",
    "high" to "High",
)

@Composable
private fun SendOrRecordButton(
    state: UiState,
    draft: String,
    onSend: () -> Unit,
    viewModel: AppViewModel,
    onRecord: () -> Unit,
) {
    val hasPayload = draft.isNotBlank() || state.attachments.isNotEmpty()
    val background = if (hasPayload || state.recording) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val tint = if (hasPayload || state.recording) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .padding(bottom = 4.dp)
            .size(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.recording -> IconButton(onClick = { viewModel.stopRecordingAndTranscribe() }) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop and transcribe", tint = tint)
            }
            hasPayload -> IconButton(onClick = onSend, enabled = !state.sending) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = tint)
            }
            else -> IconButton(onClick = onRecord, enabled = !state.transcribing) {
                Icon(Icons.Filled.Mic, contentDescription = "Record voice", tint = tint)
            }
        }
    }
}

@Composable
private fun ToolbarChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Text("⌄", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The "+" sheet: attachments first, then the per-conversation controls. */
@Composable
private fun OptionsSheet(
    state: UiState,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDocument: () -> Unit,
    onModel: () -> Unit,
    onReasoning: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        SheetTitle("Add")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachOption(Icons.Filled.PhotoCamera, "Camera", onCamera)
            AttachOption(Icons.Filled.Image, "Gallery", onGallery)
            AttachOption(Icons.Filled.InsertDriveFile, "File", onDocument)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SheetTitle("Conversation")
        SheetRow(
            icon = Icons.Filled.WbSunny,
            label = "Model",
            detail = state.sessionModel ?: "profile default",
            onClick = onModel,
        )
        SheetRow(
            icon = Icons.Filled.Psychology,
            label = "Reasoning effort",
            detail = REASONING_LEVELS.firstOrNull { it.first == state.reasoningEffort }?.second ?: "Default",
            onClick = onReasoning,
        )
    }
}

private data class PickerRow(
    val label: String,
    val detail: String?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun PickerSheet(title: String, loading: Boolean, rows: List<PickerRow>) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        SheetTitle(title)
        if (loading) LoadingRow()
        if (!loading && rows.isEmpty()) {
            Text(
                "Nothing available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = row.onClick)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    row.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.selected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Cache-backed target for a camera capture, shared through the FileProvider. */
private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Reads a picked document through the content resolver and hands it to the upload. */
private fun readAndAttach(
    context: Context,
    uri: Uri,
    viewModel: AppViewModel,
    fallbackName: String? = null,
) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: if (fallbackName?.endsWith(".jpg") == true) "image/jpeg" else "application/octet-stream"
    var name = fallbackName ?: "attachment"
    runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index) ?: name
        }
    }
    val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    if (bytes == null || bytes.isEmpty()) return
    viewModel.attach(bytes, name, mime)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: UiState, viewModel: AppViewModel) {
    var modelSheet by remember { mutableStateOf(false) }
    val profile = state.activeProfile.ifBlank { "default" }

    if (modelSheet) {
        ModalBottomSheet(
            onDismissRequest = { modelSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = "Default model for $profile",
                loading = state.loadingModels,
                rows = state.models.map { option ->
                    PickerRow(
                        label = option.id,
                        detail = option.provider,
                        selected = option.id == state.defaultModel,
                    ) {
                        viewModel.setDefaultModel(option)
                        modelSheet = false
                    }
                },
            )
        }
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Settings",
                subtitle = state.account?.let { "Signed in as $it" },
                onBack = { viewModel.back() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.savingSetting) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }

            SettingsSection("Server")
            SettingsRow(
                icon = Icons.Filled.Dns,
                label = "Address",
                value = state.baseUrl.ifBlank { "not set" },
            )
            SettingsRow(
                icon = Icons.Filled.Person,
                label = "Account",
                value = state.account ?: "unknown",
            )

            SettingsSection("Active profile")
            SettingsRow(
                icon = Icons.Filled.Person,
                label = "Profile",
                value = profile,
                onClick = { viewModel.show(Screen.Profiles) },
            )
            SettingsRow(
                icon = Icons.Filled.WbSunny,
                label = "Default model",
                value = state.defaultModel ?: "server default",
                onClick = {
                    viewModel.loadModels()
                    modelSheet = true
                },
            )
            SettingsRow(
                icon = Icons.Filled.RestartAlt,
                label = "Restart gateway",
                value = "Applies channel and model changes",
                onClick = { viewModel.restartGateway() },
            )

            SettingsSection("This device")
            SettingsRow(
                icon = Icons.Filled.Psychology,
                label = "Reasoning effort",
                value = REASONING_LEVELS.firstOrNull { it.first == state.reasoningEffort }?.second ?: "Default",
                onClick = { viewModel.show(Screen.Settings) },
            )
            Text(
                "Reasoning effort is chosen per conversation from the + menu in the composer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            SettingsSection("Account")
            SettingsRow(
                icon = Icons.Filled.Logout,
                label = "Sign out",
                value = "Removes the stored token from this device",
                onClick = { viewModel.signOut() },
            )

            SettingsSection("About")
            SettingsRow(icon = Icons.Filled.Chat, label = "Version", value = BuildConfig.VERSION_NAME)
            Text(
                "Unofficial community client for Hermes Studio.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun NoticeNote(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

// ── shared pieces ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun StudioTabs(state: UiState, viewModel: AppViewModel) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = state.tab == Tab.Chats,
            onClick = { viewModel.showTab(Tab.Chats) },
            icon = { Icon(Icons.Filled.Chat, contentDescription = "Chats") },
            label = { Text("Chats") },
        )
        NavigationBarItem(
            selected = state.tab == Tab.Groups,
            onClick = { viewModel.showTab(Tab.Groups) },
            icon = { Icon(Icons.Filled.Group, contentDescription = "Groups") },
            label = { Text("Groups") },
        )
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyNote(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.height(22.dp).width(22.dp))
    }
}

@Composable
private fun ErrorNote(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
