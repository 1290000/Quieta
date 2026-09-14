package app.quieta.core.model

enum class PrivilegeId {
    NONE,
    SHIZUKU,
    ROOT,
    DHIZUKU,
}

data class PrivilegeStatus(
    val id: PrivilegeId,
    val available: Boolean,
    val label: String,
)
