package app.quieta.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.quieta.core.model.PrivilegeId
import app.quieta.core.model.PrivilegeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "quieta_settings")

/** User-chosen privilege authorizer (InstallerX PrivPage). AUTO picks best available. */
enum class PreferredAuthorizer {
    AUTO,
    NONE,
    ROOT,
    SHIZUKU,
    DHIZUKU,
    ;

    companion object {
        fun from(raw: String?): PreferredAuthorizer =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

class AppSettings(private val context: Context) {

    val autoMuteNewChannels: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_AUTO_MUTE] ?: false
    }

    suspend fun setAutoMuteNewChannels(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_AUTO_MUTE] = enabled
        }
    }

    val preferredAuthorizer: Flow<PreferredAuthorizer> = context.settingsStore.data.map { prefs ->
        PreferredAuthorizer.from(prefs[KEY_AUTHORIZER])
    }

    suspend fun setPreferredAuthorizer(authorizer: PreferredAuthorizer) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_AUTHORIZER] = authorizer.name
        }
    }

    /** Last successful capability result used only to avoid a misleading gray first frame. */
    val lastKnownPrivilege: Flow<PrivilegeStatus?> = context.settingsStore.data.map { prefs ->
        val id = prefs[KEY_LAST_PRIVILEGE_ID]?.let { raw ->
            runCatching { PrivilegeId.valueOf(raw) }.getOrNull()
        } ?: return@map null
        PrivilegeStatus(
            id = id,
            available = true,
            label = prefs[KEY_LAST_PRIVILEGE_LABEL].orEmpty().ifEmpty { id.name },
        )
    }

    suspend fun setLastKnownPrivilege(status: PrivilegeStatus) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_LAST_PRIVILEGE_ID] = status.id.name
            prefs[KEY_LAST_PRIVILEGE_LABEL] = status.label
        }
    }

    companion object {
        private val KEY_AUTO_MUTE = booleanPreferencesKey("auto_mute_new_channels")
        private val KEY_AUTHORIZER = stringPreferencesKey("preferred_authorizer")
        private val KEY_LAST_PRIVILEGE_ID = stringPreferencesKey("last_known_privilege_id")
        private val KEY_LAST_PRIVILEGE_LABEL = stringPreferencesKey("last_known_privilege_label")
    }
}
