package us.i3u.hermesstudio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt

private val PRIORITY_COLORS = listOf(
    Color(0xFF78909C), Color(0xFF43A047), Color(0xFFF9A825), Color(0xFFEF6C00), Color(0xFFC62828),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanScreen(state: UiState, viewModel: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    val kanban = state.kanban
    val visible = remember(kanban.tasks, query) {
        val clean = query.trim()
        if (clean.isBlank()) kanban.tasks else kanban.tasks.filter {
            it.title.contains(clean, true) || it.body.orEmpty().contains(clean, true) ||
                it.assignee.orEmpty().contains(clean, true)
        }
    }

    if (create) {
        CreateTaskDialog(
            assignees = kanban.assignees,
            busy = kanban.actionId == "new",
            onDismiss = { create = false },
            onCreate = { title, body, assignee, priority, skills, triage ->
                viewModel.createKanbanTask(title, body, assignee, priority, skills, triage)
                create = false
            },
        )
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.kanban_title),
                subtitle = stringResource(R.string.kanban_mobile_note),
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { viewModel.refreshKanban() }, enabled = !kanban.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { create = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.kanban_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (kanban.loading) {
                CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            }
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(onClick = { boardMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            kanban.boards.firstOrNull { it.slug == kanban.board }?.name
                                ?: stringResource(R.string.kanban_default_board),
                            maxLines = 1,
                        )
                    }
                    DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }) {
                        kanban.boards.forEach { board ->
                            DropdownMenuItem(
                                text = { Text("${board.name}  ·  ${board.total}") },
                                onClick = {
                                    boardMenu = false
                                    viewModel.selectKanbanBoard(board.slug)
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.kanban_drag_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )

            if (!kanban.loading && visible.isEmpty()) {
                EmptyToolState(
                    icon = Icons.Filled.FilterList,
                    title = stringResource(R.string.kanban_empty),
                    note = stringResource(R.string.kanban_empty_note),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize().padding(top = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 88.dp, bottom = 12.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(KANBAN_STATUSES, key = { it }) { status ->
                        KanbanColumn(
                            status = status,
                            tasks = visible.filter { it.status == status },
                            actionId = kanban.actionId,
                            onOpen = viewModel::openKanbanTask,
                            onMove = viewModel::moveKanbanTask,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanColumn(
    status: String,
    tasks: List<KanbanTask>,
    actionId: String?,
    onOpen: (KanbanTask) -> Unit,
    onMove: (KanbanTask, String) -> Unit,
) {
    Surface(
        modifier = Modifier.width(292.dp).fillMaxHeight(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(9.dp).background(statusColor(status), RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(statusLabel(status), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(tasks.size.toString(), Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    KanbanTaskCard(
                        task = task,
                        busy = actionId == task.id,
                        onClick = { onOpen(task) },
                        onMove = { onMove(task, it) },
                    )
                }
                item { Spacer(Modifier.height(64.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KanbanTaskCard(
    task: KanbanTask,
    busy: Boolean,
    onClick: () -> Unit,
    onMove: (String) -> Unit,
) {
    var dragX by remember(task.id, task.status) { mutableFloatStateOf(0f) }
    var moveMenu by remember { mutableStateOf(false) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val currentIndex = KANBAN_STATUSES.indexOf(task.status).coerceAtLeast(0)
    val candidate = (currentIndex + (dragX * direction / 135f).roundToInt())
        .coerceIn(0, KANBAN_STATUSES.lastIndex)
    val moving = abs(dragX) > 16f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (moving) 10.dp else 0.dp, RoundedCornerShape(14.dp))
            .graphicsLayer { translationX = dragX }
            .pointerInput(task.id, task.status) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragX = 0f },
                    onDragCancel = { dragX = 0f },
                    onDragEnd = {
                        if (candidate != currentIndex) onMove(KANBAN_STATUSES[candidate])
                        dragX = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                    },
                )
            }
            .clickable(enabled = !moving, onClick = onClick)
            .alpha(if (busy) .55f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.width(4.dp).height(34.dp).background(
                        PRIORITY_COLORS.getOrElse(task.priority.coerceIn(0, 4)) { PRIORITY_COLORS.first() },
                        RoundedCornerShape(3.dp),
                    ),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { moveMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.kanban_move), Modifier.size(19.dp))
                    }
                    DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                        KANBAN_STATUSES.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(statusLabel(status)) },
                                enabled = status != task.status,
                                onClick = { moveMenu = false; onMove(status) },
                            )
                        }
                    }
                }
            }
            task.body?.let {
                Text(
                    it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 13.dp, end = 12.dp, top = 5.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 13.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DragIndicator, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (moving) statusLabel(KANBAN_STATUSES[candidate]) else stringResource(R.string.kanban_hold_drag),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (moving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                task.assignee?.let {
                    Icon(Icons.Filled.Person, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanTaskScreen(state: UiState, viewModel: AppViewModel) {
    val detail = state.kanban.openTask
    val task = detail?.task
    var comment by rememberSaveable(task?.id) { mutableStateOf("") }
    var assigneeMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = task?.title ?: stringResource(R.string.kanban_task),
                subtitle = task?.let { statusLabel(it.status) },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { task?.let(viewModel::openKanbanTask) }, enabled = !state.kanban.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
        ) {
            if (state.kanban.loading) item { LoadingRow() }
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            if (task != null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        KANBAN_STATUSES.forEach { status ->
                            AssistChip(
                                onClick = { viewModel.moveKanbanTask(task, status) },
                                enabled = status != task.status && state.kanban.actionId == null,
                                label = { Text(statusLabel(status)) },
                                leadingIcon = {
                                    Box(Modifier.size(8.dp).background(statusColor(status), RoundedCornerShape(4.dp)))
                                },
                            )
                        }
                    }
                }
                item {
                    ToolSectionCard(title = stringResource(R.string.kanban_details)) {
                        DetailLine(stringResource(R.string.kanban_priority), (task.priority + 1).toString())
                        Box {
                            DetailLine(
                                stringResource(R.string.kanban_assignee),
                                task.assignee ?: stringResource(R.string.kanban_unassigned),
                                onClick = { assigneeMenu = true },
                            )
                            DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                                state.kanban.assignees.forEach { assignee ->
                                    DropdownMenuItem(
                                        text = { Text(assignee) },
                                        onClick = {
                                            assigneeMenu = false
                                            viewModel.assignKanbanTask(task.id, assignee)
                                        },
                                    )
                                }
                            }
                        }
                        task.skills.takeIf { it.isNotEmpty() }?.let {
                            DetailLine(stringResource(R.string.kanban_skills), it.joinToString(" · "))
                        }
                        task.body?.let { Text(it, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge) }
                        task.result?.let {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            Text(stringResource(R.string.kanban_result), fontWeight = FontWeight.SemiBold)
                            Text(it, Modifier.padding(top = 6.dp))
                        }
                    }
                }
                detail.latestSummary?.let { summary ->
                    item { ToolSectionCard(stringResource(R.string.kanban_summary)) { Text(summary) } }
                }
                item {
                    ToolSectionCard(stringResource(R.string.kanban_comments, detail.comments.size)) {
                        detail.comments.forEach { item ->
                            Text(item.author, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            Text(item.body, Modifier.padding(bottom = 12.dp))
                        }
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text(stringResource(R.string.kanban_comment_hint)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.addKanbanComment(task.id, comment); comment = "" },
                            enabled = comment.isNotBlank() && state.kanban.actionId == null,
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                        ) { Text(stringResource(R.string.kanban_comment_add)) }
                    }
                }
                if (detail.runs.isNotEmpty()) {
                    item {
                        ToolSectionCard(stringResource(R.string.kanban_runs, detail.runs.size)) {
                            detail.runs.forEach { run ->
                                DetailLine(run.status, run.summary ?: run.error ?: run.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTaskDialog(
    assignees: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Int, List<String>, Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var assignee by rememberSaveable { mutableStateOf("") }
    var skills by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableIntStateOf(1) }
    var triage by rememberSaveable { mutableStateOf(false) }
    var assigneeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kanban_new_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    title, { title = it }, label = { Text(stringResource(R.string.kanban_task_title)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    body, { body = it }, label = { Text(stringResource(R.string.kanban_description)) },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedTextField(
                        assignee,
                        { assignee = it },
                        label = { Text(stringResource(R.string.kanban_assignee)) },
                        singleLine = true,
                        trailingIcon = { IconButton(onClick = { assigneeMenu = true }) { Icon(Icons.Filled.Person, null) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                        assignees.forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { assignee = it; assigneeMenu = false })
                        }
                    }
                }
                Text(stringResource(R.string.kanban_priority_value, priority + 1), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { value ->
                        AssistChip(onClick = { priority = value }, label = { Text((value + 1).toString()) })
                    }
                }
                OutlinedTextField(
                    skills,
                    { skills = it },
                    label = { Text(stringResource(R.string.kanban_skills_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = triage, onCheckedChange = { triage = it })
                    Text(stringResource(R.string.kanban_send_triage))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title,
                        body,
                        assignee,
                        priority,
                        skills.split(',').map(String::trim).filter(String::isNotBlank),
                        triage,
                    )
                },
                enabled = title.isNotBlank() && !busy,
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun ToolSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun DetailLine(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EmptyToolState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(46.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp).widthIn(max = 340.dp),
        )
    }
}

@Composable
internal fun statusLabel(status: String): String = stringResource(
    when (status) {
        "triage" -> R.string.kanban_status_triage
        "todo" -> R.string.kanban_status_todo
        "scheduled" -> R.string.kanban_status_scheduled
        "ready" -> R.string.kanban_status_ready
        "running" -> R.string.kanban_status_running
        "blocked" -> R.string.kanban_status_blocked
        "review" -> R.string.kanban_status_review
        "done" -> R.string.kanban_status_done
        "archived" -> R.string.kanban_status_archived
        else -> R.string.kanban_status_triage
    },
)

internal fun statusColor(status: String): Color = when (status) {
    "triage" -> Color(0xFF78909C)
    "todo" -> Color(0xFF42A5F5)
    "scheduled" -> Color(0xFF7E57C2)
    "ready" -> Color(0xFF26A69A)
    "running" -> Color(0xFFFFA726)
    "blocked" -> Color(0xFFEF5350)
    "review" -> Color(0xFFAB47BC)
    "done" -> Color(0xFF66BB6A)
    else -> Color.Gray
}
