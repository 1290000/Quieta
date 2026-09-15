package app.quieta.core.privilege

import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.dhizuku.DhizukuBackend
import app.quieta.core.privilege.root.RootBackend
import app.quieta.core.privilege.shizuku.ShizukuBackend

object PrivilegeBackends {

    fun shizuku(context: android.content.Context? = null): PrivilegeBackend = ShizukuBackend(context)

    fun dhizuku(context: android.content.Context? = null): PrivilegeBackend = DhizukuBackend(context)

    fun root(context: android.content.Context? = null): PrivilegeBackend =
        RootBackend(context, fallback = ShizukuBackend(context))

    fun none(): PrivilegeBackend = FakePrivilegeBackend(available = true)

    /**
     * Pick the best available backend for channel ops:
     * Shizuku (binder write) → Dhizuku (device-owner binder) → Root (list-only + Shizuku write if both).
     * Availability is async; callers must still check isAvailable().
     */
    fun preferred(context: android.content.Context? = null): PrivilegeBackend {
        return ShizukuBackend(context)
    }

    fun all(context: android.content.Context? = null): List<PrivilegeBackend> = listOf(
        ShizukuBackend(context),
        DhizukuBackend(context),
        RootBackend(context, fallback = ShizukuBackend(context)),
    )

    fun label(id: PrivilegeId): String = when (id) {
        PrivilegeId.NONE -> "无特权"
        PrivilegeId.SHIZUKU -> "Shizuku"
        PrivilegeId.ROOT -> "ROOT"
        PrivilegeId.DHIZUKU -> "Dhizuku"
    }
}
