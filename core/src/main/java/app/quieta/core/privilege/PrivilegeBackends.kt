package app.quieta.core.privilege

import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.shizuku.ShizukuBackend

object PrivilegeBackends {

    fun shizuku(): PrivilegeBackend = ShizukuBackend()

    fun none(): PrivilegeBackend = FakePrivilegeBackend(available = true)

    fun preferred(): PrivilegeBackend {
        val shizuku = shizuku()
        // Availability is async; callers should still check isAvailable().
        return shizuku
    }

    fun label(id: PrivilegeId): String = when (id) {
        PrivilegeId.NONE -> "无特权"
        PrivilegeId.SHIZUKU -> "Shizuku"
        PrivilegeId.ROOT -> "ROOT"
        PrivilegeId.DHIZUKU -> "Dhizuku"
    }
}
