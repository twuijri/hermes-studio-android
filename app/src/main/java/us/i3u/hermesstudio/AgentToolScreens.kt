package us.i3u.hermesstudio

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillsScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val skills = state.skillsUi
    var query by rememberSaveable { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            var name = uri.lastPathSegment ?: "skill.zip"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0)
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected skill")
            viewModel.importSkill(bytes, name)
        }.onFailure { viewModel.showToolError(it) }
    }
    val visible = remember(skills.categories, query) {
        val clean = query.trim()
        skills.categories.map { category ->
            category.copy(skills = category.skills.filter {
                clean.isBlank() || it.name.contains(clean, true) || it.description.contains(clean, true)
            }.sortedWith(compareByDescending<SkillInfo> { it.pinned }.thenBy { it.name.lowercase() }))
        }.filter { it.skills.isNotEmpty() }
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.skills_title),
                subtitle = stringResource(R.string.skills_subtitle, state.activeProfile),
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { importer.launch("application/zip") }, enabled = skills.actionName == null) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = stringResource(R.string.skills_import))
                    }
                    IconButton(onClick = viewModel::refreshSkills, enabled = !skills.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (skills.loading) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf("hermes", "claude", "codex").forEach { target ->
                    FilterChip(
                        selected = skills.target == target,
                        onClick = { viewModel.selectSkillsTarget(target) },
                        label = { Text(target.replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.skills_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            )
            if (!skills.loading && visible.isEmpty()) {
                EmptyToolState(
                    Icons.Filled.School,
                    stringResource(R.string.skills_empty),
                    stringResource(R.string.skills_empty_note),
                    Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    visible.forEach { category ->
                        item(key = "header-${category.name}") {
                            Column(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                                Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (category.description.isNotBlank()) {
                                    Text(
                                        category.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(category.skills, key = { "${category.name}/${it.name}" }) { skill ->
                            SkillCard(
                                skill = skill,
                                busy = skills.actionName == skill.name,
                                onOpen = { viewModel.openSkill(category.name, skill) },
                                onToggle = { viewModel.toggleSkill(skill, it) },
                                onPin = { viewModel.pinSkill(skill, !skill.pinned) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillInfo,
    busy: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPin: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)),
    ) {
        Row(Modifier.padding(start = 15.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(skill.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (skill.pinned) {
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Filled.Star, null, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    skill.description.ifBlank { stringResource(R.string.skills_no_description) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${skill.source} · ${stringResource(R.string.skills_uses, skill.useCount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            IconButton(onClick = onPin, enabled = !busy) {
                Icon(if (skill.pinned) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.skills_pin))
            }
            Switch(checked = skill.enabled, onCheckedChange = onToggle, enabled = !busy)
            Spacer(Modifier.width(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillScreen(state: UiState, viewModel: AppViewModel) {
    val open = state.skillsUi.openSkill
    var content by rememberSaveable(open?.skill?.name, open?.content) { mutableStateOf(open?.content.orEmpty()) }
    var delete by remember { mutableStateOf(false) }
    if (delete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.skills_delete_title),
            message = stringResource(R.string.skills_delete_note, open?.skill?.name.orEmpty()),
            onDismiss = { delete = false },
            onDelete = { delete = false; viewModel.deleteOpenSkill() },
        )
    }
    Scaffold(
        topBar = {
            StudioTopBar(
                title = open?.skill?.name ?: stringResource(R.string.skills_skill),
                subtitle = open?.category,
                onBack = { viewModel.back() },
                actions = {
                    open?.let {
                        IconButton(onClick = { viewModel.pinSkill(it.skill, !it.skill.pinned) }) {
                            Icon(if (it.skill.pinned) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.skills_pin))
                        }
                        IconButton(onClick = { delete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            if (state.skillsUi.loading) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            open?.let {
                Text(it.skill.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("SKILL.md") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Button(
                    onClick = { viewModel.saveSkill(content) },
                    enabled = state.skillsUi.actionName == null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                ) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginsScreen(state: UiState, viewModel: AppViewModel) {
    val ui = state.pluginsUi
    var query by rememberSaveable { mutableStateOf("") }
    var onlyEnabled by rememberSaveable { mutableStateOf(false) }
    val visible = remember(ui.plugins, query, onlyEnabled) {
        ui.plugins.filter {
            (!onlyEnabled || it.enabled) && (query.isBlank() || it.name.contains(query, true) ||
                it.description.orEmpty().contains(query, true) || it.key.contains(query, true))
        }
    }
    Scaffold(
        topBar = {
            StudioTopBar(
                stringResource(R.string.plugins_title),
                stringResource(R.string.plugins_summary, ui.plugins.count { it.enabled }, ui.plugins.size),
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = viewModel::refreshPlugins, enabled = !ui.loading) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.loading) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            ui.warnings.forEach {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Text(it, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                query, { query = it }, placeholder = { Text(stringResource(R.string.plugins_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            FilterChip(
                selected = onlyEnabled,
                onClick = { onlyEnabled = !onlyEnabled },
                label = { Text(stringResource(R.string.plugins_enabled_only)) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
            if (!ui.loading && visible.isEmpty()) {
                EmptyToolState(Icons.Filled.Extension, stringResource(R.string.plugins_empty), stringResource(R.string.plugins_empty_note), Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(visible, key = { it.key }) { plugin ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
                            Column(Modifier.padding(15.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Extension, null, tint = if (plugin.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(plugin.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOfNotNull(plugin.version, plugin.source, plugin.kind).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        plugin.enabled,
                                        { viewModel.togglePlugin(plugin, it) },
                                        enabled = plugin.manageable && ui.actionKey != plugin.key,
                                    )
                                }
                                plugin.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.plugins_tools, plugin.tools.size)) })
                                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.plugins_hooks, plugin.hooks.size)) })
                                    if (plugin.requiredEnv.isNotEmpty()) {
                                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.plugins_env, plugin.requiredEnv.size)) })
                                    }
                                    if (!plugin.manageable) AssistChip(onClick = {}, label = { Text(stringResource(R.string.plugins_managed_by_studio)) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpScreen(state: UiState, viewModel: AppViewModel) {
    val ui = state.mcpUi
    var editor by remember { mutableStateOf<McpServer?>(null) }
    var adding by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<McpServer?>(null) }
    if (adding || editor != null) {
        McpEditorDialog(
            server = editor,
            busy = ui.actionName != null,
            onDismiss = { adding = false; editor = null },
            onSave = { name, config ->
                viewModel.saveMcpServer(editor?.name, name, config)
                adding = false
                editor = null
            },
        )
    }
    remove?.let { server ->
        ConfirmDeleteDialog(
            stringResource(R.string.mcp_delete_title),
            stringResource(R.string.mcp_delete_note, server.name),
            onDismiss = { remove = null },
            onDelete = { remove = null; viewModel.deleteMcpServer(server.name) },
        )
    }
    Scaffold(
        topBar = {
            StudioTopBar(
                stringResource(R.string.mcp_title),
                stringResource(R.string.mcp_summary, ui.servers.count { it.connected }, ui.servers.size),
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { viewModel.reloadMcpServer() }, enabled = ui.actionName == null) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.mcp_reload_all))
                    }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.mcp_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.loading) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            if (!ui.loading && ui.servers.isEmpty()) {
                EmptyToolState(Icons.Filled.Cable, stringResource(R.string.mcp_empty), stringResource(R.string.mcp_empty_note), Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(ui.servers, key = { it.name }) { server ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
                            Column(Modifier.padding(15.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(10.dp).background(
                                            if (server.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            RoundedCornerShape(5.dp),
                                        ),
                                    )
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(server.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${server.transport} · ${stringResource(R.string.mcp_tools, server.registeredToolCount, server.toolCount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { editor = server }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                                    IconButton(onClick = { remove = server }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                                }
                                server.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                if (server.tools.isNotEmpty()) {
                                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        server.tools.take(12).forEach { AssistChip(onClick = {}, label = { Text(it.name) }) }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { viewModel.testMcpServer(server.name) }, enabled = ui.actionName == null) {
                                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(stringResource(R.string.mcp_test))
                                    }
                                    TextButton(onClick = { viewModel.reloadMcpServer(server.name) }, enabled = ui.actionName == null) {
                                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(stringResource(R.string.mcp_reload))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpEditorDialog(
    server: McpServer?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val initial = remember(server) { runCatching { JSONObject(server?.rawConfig ?: "{}") }.getOrDefault(JSONObject()) }
    var name by rememberSaveable(server?.name) { mutableStateOf(server?.name.orEmpty()) }
    var transport by rememberSaveable(server?.name) { mutableStateOf(initial.optString("transport", server?.transport ?: "stdio")) }
    var endpoint by rememberSaveable(server?.name) {
        mutableStateOf(initial.optString(if (transport == "stdio") "command" else "url"))
    }
    var args by rememberSaveable(server?.name) {
        mutableStateOf(initial.optJSONArray("args")?.let { array ->
            (0 until array.length()).joinToString("\n") { array.optString(it) }
        }.orEmpty())
    }
    var advanced by rememberSaveable(server?.name) { mutableStateOf(false) }
    var raw by rememberSaveable(server?.name) { mutableStateOf(initial.toString(2)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (server == null) R.string.mcp_add else R.string.mcp_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.mcp_name)) }, enabled = server == null, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("stdio", "http", "sse").forEach { value ->
                        FilterChip(selected = transport == value, onClick = { transport = value }, label = { Text(value.uppercase()) })
                    }
                }
                OutlinedTextField(
                    endpoint,
                    { endpoint = it },
                    label = { Text(stringResource(if (transport == "stdio") R.string.mcp_command else R.string.mcp_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (transport == "stdio") {
                    OutlinedTextField(args, { args = it }, label = { Text(stringResource(R.string.mcp_args)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { advanced = !advanced }) {
                    Icon(Icons.Filled.Code, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mcp_advanced_json))
                }
                if (advanced) {
                    OutlinedTextField(
                        raw,
                        { raw = it },
                        label = { Text("JSON") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = if (advanced) raw else initial.apply {
                        put("transport", transport)
                        if (transport == "stdio") {
                            put("command", endpoint)
                            put("args", org.json.JSONArray(args.lines().map(String::trim).filter(String::isNotBlank)))
                            remove("url")
                        } else {
                            put("url", endpoint)
                            remove("command")
                            remove("args")
                        }
                    }.toString()
                    onSave(name, config)
                },
                enabled = name.isNotBlank() && endpoint.isNotBlank() && !busy,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PetsScreen(state: UiState, viewModel: AppViewModel) {
    val ui = state.petsUi
    var query by rememberSaveable { mutableStateOf("") }
    var scale by remember(ui.active?.slug, ui.active?.scale) { mutableFloatStateOf(ui.active?.scale?.toFloat() ?: 1f) }
    val visible = remember(ui.pets, query) {
        ui.pets.filter { query.isBlank() || it.displayName.contains(query, true) || it.kind.contains(query, true) }
    }
    Scaffold(
        topBar = {
            StudioTopBar(
                stringResource(R.string.pets_title),
                stringResource(R.string.pets_count, ui.pets.size),
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = viewModel::refreshPets, enabled = !ui.loading) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.loading) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            ui.active?.let { active ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = active.spritesheetDataUrl,
                                contentDescription = active.displayName,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(13.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                                Text(stringResource(R.string.pets_active), style = MaterialTheme.typography.labelMedium)
                                Text(active.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Switch(
                                active.enabled,
                                { viewModel.setActivePet(enabled = it) },
                                enabled = ui.actionSlug == null,
                            )
                        }
                        Text(stringResource(R.string.pets_scale, scale), style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            onValueChangeFinished = { viewModel.setActivePet(scale = scale.toDouble()) },
                            valueRange = .5f..2f,
                            enabled = ui.actionSlug == null,
                        )
                    }
                }
            }
            OutlinedTextField(
                query, { query = it }, placeholder = { Text(stringResource(R.string.pets_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            )
            if (!ui.loading && visible.isEmpty()) {
                EmptyToolState(Icons.Filled.Pets, stringResource(R.string.pets_empty), stringResource(R.string.pets_empty_note), Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(visible, key = { it.slug }) { pet ->
                        val adopted = ui.active?.slug == pet.slug
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                            Column {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1.35f).background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (pet.previewUrl != null) {
                                        AsyncImage(
                                            model = absoluteStudioUrl(state.baseUrl, pet.previewUrl),
                                            contentDescription = pet.displayName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Icon(Icons.Filled.Pets, null, Modifier.size(42.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column(Modifier.padding(11.dp)) {
                                    Text(pet.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pet.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(
                                        onClick = { viewModel.adoptPet(pet.slug) },
                                        enabled = !adopted && ui.actionSlug == null,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    ) {
                                        Icon(if (adopted) Icons.Filled.Favorite else Icons.Filled.Pets, null, Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(stringResource(if (adopted) R.string.pets_adopted_button else R.string.pets_adopt))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onDelete) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun absoluteStudioUrl(baseUrl: String, path: String): String = when {
    path.startsWith("http://") || path.startsWith("https://") || path.startsWith("data:") -> path
    else -> "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
}
