package app.quieta.core.model

enum class RuleAction {
    /** Leave channel unchanged. */
    KEEP,
    /** Set importance to NONE (blocked). */
    MUTE,
    /** Downgrade to LOW without fully blocking. */
    DOWNGRADE,
}

data class Rule(
    val id: String,
    val enabled: Boolean = true,
    /** Optional package filter; null = any package. */
    val packageName: String? = null,
    /** Case-insensitive substring match on channel name or id; null = any. */
    val nameContains: String? = null,
    val action: RuleAction,
)
