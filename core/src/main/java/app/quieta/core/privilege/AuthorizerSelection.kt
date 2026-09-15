package app.quieta.core.privilege

import app.quieta.core.model.PrivilegeId
import app.quieta.core.settings.PreferredAuthorizer

/** Shared selection policy; an unavailable explicit choice never falls back. */
fun selectAuthorizer(
    preferred: PreferredAuthorizer,
    root: Boolean,
    shizuku: Boolean,
    dhizuku: Boolean,
): PrivilegeId = when (preferred) {
    PreferredAuthorizer.NONE -> PrivilegeId.NONE
    PreferredAuthorizer.ROOT -> if (root) PrivilegeId.ROOT else PrivilegeId.NONE
    PreferredAuthorizer.SHIZUKU -> if (shizuku) PrivilegeId.SHIZUKU else PrivilegeId.NONE
    PreferredAuthorizer.DHIZUKU -> if (dhizuku) PrivilegeId.DHIZUKU else PrivilegeId.NONE
    PreferredAuthorizer.AUTO -> when {
        root -> PrivilegeId.ROOT
        shizuku -> PrivilegeId.SHIZUKU
        dhizuku -> PrivilegeId.DHIZUKU
        else -> PrivilegeId.NONE
    }
}
