package us.i3u.hermesstudio

import android.Manifest
import android.app.Activity
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
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.res.stringResource
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

    /** Applies the language chosen in Settings before any screen is built. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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
        Screen.Onboarding -> OnboardingScreen(
            languageAction = { LanguageAction(state, viewModel) },
            onDone = { viewModel.finishOnboarding() },
        )
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

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.app_name),
                actions = { LanguageAction(state, viewModel) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.login_server_label)) },
                placeholder = { Text(stringResource(R.string.login_server_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
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
                Text(stringResource(if (state.busy) R.string.login_submitting else R.string.login_submit))
            }
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            Text(
                stringResource(R.string.login_note),
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
                title = stringResource(R.string.chats_title),
                leading = { AppMark(size = 30.dp, corner = 9.dp) },
                actions = {
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_chat))
                    }
                    IconButton(onClick = { viewModel.refreshSessions() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { viewModel.show(Screen.Profiles) }) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.action_profiles))
                    }
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
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
                EmptyNote(stringResource(R.string.chats_empty))
            } else {
                SectionHeader(stringResource(R.string.chats_section), state.sessions.size)
                LazyColumn {
                    items(state.sessions) { session ->
                        SessionRow(session, state.avatarOf(session.profile)) {
                            viewModel.openSession(session)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, avatar: AvatarSpec?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            name = session.profile.orEmpty().ifBlank { "default" },
            spec = avatar,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
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

/** The avatar Studio shows for a profile, or null when it is not loaded yet. */
private fun UiState.avatarOf(profile: String?): AvatarSpec? {
    val name = profile?.ifBlank { null } ?: activeProfile
    return profiles.firstOrNull { it.name == name }?.avatar
}

@Composable
private fun ProfileFilterRow(state: UiState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val label = state.profileFilter.ifBlank { stringResource(R.string.chats_all_profiles) }

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
                text = { Text(stringResource(R.string.chats_all_profiles)) },
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
                title = stringResource(R.string.groups_title),
                actions = {
                    IconButton(onClick = { viewModel.refreshRooms() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
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
                EmptyNote(stringResource(R.string.groups_empty))
            } else {
                SectionHeader(stringResource(R.string.groups_section), state.rooms.size)
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
                                stringResource(R.string.groups_counts, room.agentCount, room.memberCount),
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
                title = room?.name ?: stringResource(R.string.room_title),
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
                EmptyNote(stringResource(R.string.room_empty))
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
    val avatar = state.avatarOf(profile)
    Scaffold(
        topBar = {
            StudioTopBar(
                title = state.openSession?.title ?: stringResource(R.string.action_new_chat),
                subtitle = listOfNotNull(profile.ifBlank { null }, state.openSession?.model)
                    .joinToString(" · ")
                    .ifBlank { null },
                leading = {
                    ProfileAvatar(profile.ifBlank { "default" }, avatar, size = 32.dp)
                },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_chat))
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
                        stringResource(
                            R.string.conversation_empty,
                            profile.ifBlank { stringResource(R.string.conversation_your_agent) },
                        ),
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
                    items(state.lines) { line ->
                        MessageBubble(
                            line = line,
                            profile = profile.ifBlank { "default" },
                            avatar = avatar,
                        )
                    }
                }
            }

            if (state.sending) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.conversation_thinking), style = MaterialTheme.typography.bodySmall)
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
private fun MessageBubble(line: ChatLine, profile: String? = null, avatar: AvatarSpec? = null) {
    val alignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val container = when {
        line.isError -> MaterialTheme.colorScheme.errorContainer
        line.fromUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Row(verticalAlignment = Alignment.Bottom) {
            // The agent's picture rides with its own replies, the way Studio
            // shows it in the transcript.
            if (!line.fromUser && !profile.isNullOrBlank()) {
                ProfileAvatar(profile, avatar, size = 26.dp)
                Spacer(Modifier.width(8.dp))
            }
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
}

// ── profiles ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesScreen(state: UiState, viewModel: AppViewModel) {
    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.profiles_title),
                subtitle = state.account?.let { stringResource(R.string.profiles_signed_in, it) },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { viewModel.refreshProfiles() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.action_sign_out))
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
                        ProfileAvatar(profile.name, profile.avatar, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                profile.model ?: stringResource(R.string.profiles_no_model),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (profile.name == state.activeProfile) {
                            Text(
                                stringResource(R.string.profiles_active),
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
                title = stringResource(R.string.sheet_model),
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
                title = stringResource(R.string.sheet_reasoning),
                loading = false,
                rows = REASONING_LEVELS.map { (value, label) ->
                    PickerRow(
                        label = stringResource(label),
                        detail = if (value.isBlank()) stringResource(R.string.reasoning_use_profile) else null,
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
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove)) },
                    )
                }
                if (state.attaching) AssistChip(onClick = {}, label = { Text(stringResource(R.string.composer_uploading)) })
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
                    stringResource(if (state.recording) R.string.composer_recording else R.string.composer_transcribing),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.recording) {
                    TextButton(onClick = { viewModel.stopRecordingAndAttach() }) { Text(stringResource(R.string.composer_send_audio)) }
                    TextButton(onClick = { viewModel.cancelRecording() }) { Text(stringResource(R.string.action_cancel)) }
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
                placeholder = { Text(stringResource(R.string.composer_hint)) },
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
                    contentDescription = stringResource(R.string.composer_more),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            ToolbarChip(
                icon = Icons.Filled.Psychology,
                label = reasoningLabel(state.reasoningEffort),
            ) {
                sheet = ComposerSheet.Reasoning
            }
            ToolbarChip(
                icon = Icons.Filled.WbSunny,
                label = state.sessionModel ?: stringResource(R.string.sheet_model),
            ) {
                viewModel.loadModels()
                sheet = ComposerSheet.Model
            }
        }
    }
}

private enum class ComposerSheet { Options, Model, Reasoning }

private val REASONING_LEVELS = listOf(
    "" to R.string.reasoning_default,
    "low" to R.string.reasoning_low,
    "medium" to R.string.reasoning_medium,
    "high" to R.string.reasoning_high,
)

@Composable
private fun reasoningLabel(effort: String): String = stringResource(
    REASONING_LEVELS.firstOrNull { it.first == effort }?.second ?: R.string.reasoning_default,
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
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.composer_stop), tint = tint)
            }
            hasPayload -> IconButton(onClick = onSend, enabled = !state.sending) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.composer_send), tint = tint)
            }
            else -> IconButton(onClick = onRecord, enabled = !state.transcribing) {
                Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.composer_record), tint = tint)
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
        SheetTitle(stringResource(R.string.sheet_add))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachOption(Icons.Filled.PhotoCamera, stringResource(R.string.sheet_camera), onCamera)
            AttachOption(Icons.Filled.Image, stringResource(R.string.sheet_gallery), onGallery)
            AttachOption(Icons.Filled.InsertDriveFile, stringResource(R.string.sheet_file), onDocument)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SheetTitle(stringResource(R.string.sheet_conversation))
        SheetRow(
            icon = Icons.Filled.WbSunny,
            label = stringResource(R.string.sheet_model),
            detail = state.sessionModel ?: stringResource(R.string.sheet_profile_default),
            onClick = onModel,
        )
        SheetRow(
            icon = Icons.Filled.Psychology,
            label = stringResource(R.string.sheet_reasoning),
            detail = reasoningLabel(state.reasoningEffort),
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
                stringResource(R.string.sheet_empty),
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
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_selected))
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

/**
 * Language is reachable before sign-in on purpose: someone who cannot read the
 * sign-in form cannot get to Settings to fix that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(state: UiState, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        PickerSheet(
            title = stringResource(R.string.settings_language),
            loading = false,
            rows = APP_LANGUAGES.map { option ->
                PickerRow(
                    label = AppLocale.labelFor(context, option),
                    detail = null,
                    selected = option.tag == state.language,
                ) {
                    onDismiss()
                    viewModel.setLanguage(option.tag)
                    // Resources are resolved when the activity is built, so rebuild it.
                    activity?.recreate()
                }
            },
        )
    }
}

@Composable
private fun LanguageAction(state: UiState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (open) LanguageSheet(state, viewModel) { open = false }

    TextButton(onClick = { open = true }) {
        Icon(
            Icons.Filled.Language,
            contentDescription = stringResource(R.string.settings_language),
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            AppLocale.currentEndonym(context, state.language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: UiState, viewModel: AppViewModel) {
    var modelSheet by remember { mutableStateOf(false) }
    var languageSheet by remember { mutableStateOf(false) }
    val profile = state.activeProfile.ifBlank { "default" }
    val context = LocalContext.current
    val language = APP_LANGUAGES.firstOrNull { it.tag == state.language } ?: APP_LANGUAGES.first()

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) viewModel.setAppLogo(bytes)
    }

    if (modelSheet) {
        ModalBottomSheet(
            onDismissRequest = { modelSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = stringResource(R.string.settings_default_model_title, profile),
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

    if (languageSheet) LanguageSheet(state, viewModel) { languageSheet = false }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = state.account?.let { stringResource(R.string.profiles_signed_in, it) },
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

            SettingsSection(stringResource(R.string.settings_section_server))
            SettingsRow(
                icon = Icons.Filled.Dns,
                label = stringResource(R.string.settings_address),
                value = state.baseUrl.ifBlank { stringResource(R.string.settings_address_missing) },
            )
            SettingsRow(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.settings_account),
                value = state.account ?: stringResource(R.string.settings_account_unknown),
            )

            SettingsSection(stringResource(R.string.settings_section_profile))
            SettingsRow(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.settings_profile),
                value = profile,
                onClick = { viewModel.show(Screen.Profiles) },
            )
            SettingsRow(
                icon = Icons.Filled.WbSunny,
                label = stringResource(R.string.settings_default_model),
                value = state.defaultModel ?: stringResource(R.string.settings_default_model_server),
                onClick = {
                    viewModel.loadModels()
                    modelSheet = true
                },
            )
            SettingsRow(
                icon = Icons.Filled.RestartAlt,
                label = stringResource(R.string.settings_restart_gateway),
                value = stringResource(R.string.settings_restart_gateway_note),
                onClick = { viewModel.restartGateway() },
            )

            SettingsSection(stringResource(R.string.settings_section_appearance))
            LogoRow(
                value = stringResource(
                    when {
                        AppLogo.isCustom -> R.string.settings_logo_custom
                        AppLogo.image != null -> R.string.settings_logo_server
                        else -> R.string.settings_logo_missing
                    },
                ),
                onClick = { pickLogo.launch("image/*") },
            )
            if (AppLogo.isCustom) {
                SettingsRow(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(R.string.settings_logo_reset),
                    value = stringResource(R.string.settings_logo_reset_note),
                    onClick = { viewModel.resetAppLogo() },
                )
            }
            Text(
                stringResource(R.string.settings_logo_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SettingsRow(
                icon = Icons.Filled.Language,
                label = stringResource(R.string.settings_language),
                value = AppLocale.labelFor(context, language),
                onClick = { languageSheet = true },
            )

            SettingsSection(stringResource(R.string.settings_section_device))
            SettingsRow(
                icon = Icons.Filled.Psychology,
                label = stringResource(R.string.settings_reasoning),
                value = reasoningLabel(state.reasoningEffort),
            )
            Text(
                stringResource(R.string.settings_reasoning_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            SettingsSection(stringResource(R.string.settings_section_account))
            SettingsRow(
                icon = Icons.Filled.Logout,
                label = stringResource(R.string.action_sign_out),
                value = stringResource(R.string.settings_sign_out_note),
                onClick = { viewModel.signOut() },
            )

            SettingsSection(stringResource(R.string.settings_section_about))
            SettingsRow(
                icon = Icons.Filled.Chat,
                label = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME,
            )
            Text(
                stringResource(R.string.settings_about_note),
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

/** Settings row that previews the current app mark instead of an icon. */
@Composable
private fun LogoRow(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppMark(size = 34.dp, corner = 10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_logo), style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
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
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
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
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
            label = { Text(stringResource(R.string.chats_tab)) },
        )
        NavigationBarItem(
            selected = state.tab == Tab.Groups,
            onClick = { viewModel.showTab(Tab.Groups) },
            icon = { Icon(Icons.Filled.Group, contentDescription = null) },
            label = { Text(stringResource(R.string.groups_tab)) },
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}
