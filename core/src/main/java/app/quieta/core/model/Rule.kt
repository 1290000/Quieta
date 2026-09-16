package app.quieta.core.model

enum class RuleAction {
    /** Leave channel unchanged. Higher priority than MUTE/DOWNGRADE when it matches. */
    KEEP,
    /** Set importance to NONE (blocked). */
    MUTE,
    /** Downgrade to LOW without fully blocking. */
    DOWNGRADE,
}

/**
 * Match conditions are AND-combined. Empty rule (no filters) never matches.
 * Whitelist KEEP beats MUTE/DOWNGRADE; among the rest, higher [specificity] wins.
 */
data class Rule(
    val id: String,
    val enabled: Boolean = true,
    /** Exact package name (case-insensitive). */
    val packageName: String? = null,
    /** Package name prefix (e.g. `com.tencent.`). */
    val packagePrefix: String? = null,
    /** Case-insensitive substring on name and/or id, controlled by [matchName]/[matchId]. */
    val nameContains: String? = null,
    val channelIdExact: String? = null,
    val channelIdPrefix: String? = null,
    val matchName: Boolean = true,
    val matchId: Boolean = true,
    val action: RuleAction,
) {
    /** Higher = more specific. Used to pick among conflicting non-KEEP matches. */
    val specificity: Int
        get() {
            var score = 0
            if (channelIdExact != null) score += 100
            if (packageName != null) score += 40
            if (channelIdPrefix != null) score += 30
            if (packagePrefix != null) score += 20
            if (nameContains != null) score += 10
            if (matchName && matchId && nameContains != null) score += 1
            return score
        }

    val hasAnyFilter: Boolean
        get() = packageName != null ||
            packagePrefix != null ||
            !nameContains.isNullOrBlank() ||
            channelIdExact != null ||
            !channelIdPrefix.isNullOrBlank()
}
