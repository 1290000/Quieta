package app.quieta.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "quieta_settings")

class AppSettings(private val context: Context) {

    val autoMuteNewChannels: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_AUTO_MUTE] ?: false
    }

    suspend fun setAutoMuteNewChannels(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_AUTO_MUTE] = enabled
        }
    }

    companion object {
        private val KEY_AUTO_MUTE = booleanPreferencesKey("auto_mute_new_channels")
    }
}
