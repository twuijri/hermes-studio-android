package us.i3u.hermesstudio

/** Native mobile models for the agent tools exposed by Hermes Studio. */
data class KanbanBoard(
    val slug: String,
    val name: String,
    val description: String?,
    val color: String?,
    val total: Int,
    val isCurrent: Boolean,
)

data class KanbanTask(
    val id: String,
    val title: String,
    val body: String?,
    val assignee: String?,
    val status: String,
    val priority: Int,
    val createdAt: Long,
    val result: String?,
    val skills: List<String>,
)

data class KanbanComment(
    val id: String,
    val author: String,
    val body: String,
    val createdAt: Long,
)

data class KanbanRun(
    val id: String,
    val status: String,
    val summary: String?,
    val error: String?,
    val startedAt: Long,
)

data class KanbanTaskDetail(
    val task: KanbanTask,
    val latestSummary: String?,
    val comments: List<KanbanComment>,
    val runs: List<KanbanRun>,
)

data class SkillInfo(
    val name: String,
    val description: String,
    val enabled: Boolean,
    val source: String,
    val pinned: Boolean,
    val useCount: Int,
)

data class SkillCategory(
    val name: String,
    val description: String,
    val skills: List<SkillInfo>,
)

data class OpenSkill(
    val category: String,
    val skill: SkillInfo,
    val content: String,
)

data class HermesPlugin(
    val key: String,
    val name: String,
    val kind: String,
    val source: String,
    val configured: Boolean,
    val enabled: Boolean,
    val version: String?,
    val description: String?,
    val author: String?,
    val tools: List<String>,
    val hooks: List<String>,
    val requiredEnv: List<String>,
) {
    val manageable: Boolean get() = kind == "standalone" && source != "bundled"
}

data class McpTool(val name: String, val description: String?)

data class McpServer(
    val name: String,
    val transport: String,
    val connected: Boolean,
    val toolCount: Int,
    val registeredToolCount: Int,
    val tools: List<McpTool>,
    val error: String?,
    /** Kept verbatim so editing a server never discards advanced Studio keys. */
    val rawConfig: String,
)

data class PetdexPet(
    val slug: String,
    val displayName: String,
    val kind: String,
    val submittedBy: String?,
    val previewUrl: String?,
)

data class ActivePet(
    val enabled: Boolean,
    val slug: String,
    val displayName: String,
    val kind: String,
    val scale: Double,
    val spritesheetDataUrl: String?,
)

data class KanbanUiState(
    val loading: Boolean = false,
    val actionId: String? = null,
    val boards: List<KanbanBoard> = emptyList(),
    val board: String = "",
    val tasks: List<KanbanTask> = emptyList(),
    val assignees: List<String> = emptyList(),
    val openTask: KanbanTaskDetail? = null,
)

data class SkillsUiState(
    val loading: Boolean = false,
    val actionName: String? = null,
    val target: String = "hermes",
    val categories: List<SkillCategory> = emptyList(),
    val openSkill: OpenSkill? = null,
)

data class PluginsUiState(
    val loading: Boolean = false,
    val actionKey: String? = null,
    val plugins: List<HermesPlugin> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class McpUiState(
    val loading: Boolean = false,
    val actionName: String? = null,
    val servers: List<McpServer> = emptyList(),
)

data class PetsUiState(
    val loading: Boolean = false,
    val actionSlug: String? = null,
    val pets: List<PetdexPet> = emptyList(),
    val active: ActivePet? = null,
)

val KANBAN_STATUSES = listOf(
    "triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done",
)
