package app.quieta.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    companion object {
        private val KEY_AUTO_MUTE = booleanPreferencesKey("auto_mute_new_channels")
        private val KEY_AUTHORIZER = stringPreferencesKey("preferred_authorizer")
    }
}
