package app.quieta.core.privilege

import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.dhizuku.DhizukuBackend
import app.quieta.core.privilege.root.RootBackend
import app.quieta.core.privilege.shizuku.ShizukuBackend
import kotlinx.coroutines.flow.first

object PrivilegeBackends {

    fun shizuku(context: android.content.Context? = null): PrivilegeBackend = ShizukuBackend(context)

    fun dhizuku(context: android.content.Context? = null): PrivilegeBackend = DhizukuBackend(context)

    fun root(context: android.content.Context? = null): PrivilegeBackend =
        RootBackend(context)

    fun none(): PrivilegeBackend = FakePrivilegeBackend(available = true)

    /** Resolve the saved global choice using the same policy as the foreground UI. */
    suspend fun preferred(context: android.content.Context): PrivilegeBackend? {
        val preference = app.quieta.core.settings.AppSettings(context).preferredAuthorizer.first()
        val candidates = when (preference) {
            app.quieta.core.settings.PreferredAuthorizer.AUTO -> all(context)
            app.quieta.core.settings.PreferredAuthorizer.NONE -> emptyList()
            app.quieta.core.settings.PreferredAuthorizer.ROOT -> listOf(root(context))
            app.quieta.core.settings.PreferredAuthorizer.SHIZUKU -> listOf(shizuku(context))
            app.quieta.core.settings.PreferredAuthorizer.DHIZUKU -> listOf(dhizuku(context))
        }
        return candidates.firstOrNull { it.isAvailable() }
    }

    fun all(context: android.content.Context? = null): List<PrivilegeBackend> = listOf(
        root(context),
        shizuku(context),
        dhizuku(context),
    )

    fun label(id: PrivilegeId): String = when (id) {
        PrivilegeId.NONE -> "无特权"
        PrivilegeId.SHIZUKU -> "Shizuku"
        PrivilegeId.ROOT -> "ROOT"
        PrivilegeId.DHIZUKU -> "Dhizuku"
    }
}
