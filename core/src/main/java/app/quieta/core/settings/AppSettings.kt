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

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppSettings(private val context: Context) {

    private val bootPrefs = context.getSharedPreferences("quieta_boot", Context.MODE_PRIVATE)

    val themeMode: Flow<ThemeMode> = context.settingsStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    val autoMuteNewChannels: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_AUTO_MUTE] ?: false
    }

    val notificationTimelineEnabled: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_TIMELINE_ENABLED] ?: true
    }

    suspend fun setNotificationTimelineEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_TIMELINE_ENABLED] = enabled
        }
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

    /**
     * Synchronous boot seed (SharedPreferences). DataStore first emission is too late for
     * the first Compose frame — InstallerX paints the status card from a sync/local value.
     */
    fun lastKnownPrivilegeSync(): PrivilegeStatus? {
        val raw = bootPrefs.getString(BOOT_PRIVILEGE_ID, null) ?: return null
        val id = runCatching { PrivilegeId.valueOf(raw) }.getOrNull() ?: return null
        val label = bootPrefs.getString(BOOT_PRIVILEGE_LABEL, null).orEmpty().ifEmpty { id.name }
        return PrivilegeStatus(id = id, available = true, label = label)
    }

    /** Wall-clock of the last completed full inventory scan; 0 when never scanned. */
    fun inventoryScannedAt(): Long = bootPrefs.getLong(BOOT_INVENTORY_AT, 0L)

    fun markInventoryScanned() {
        bootPrefs.edit().putLong(BOOT_INVENTORY_AT, System.currentTimeMillis()).apply()
    }

    suspend fun setLastKnownPrivilege(status: PrivilegeStatus) {
        bootPrefs.edit()
            .putString(BOOT_PRIVILEGE_ID, status.id.name)
            .putString(BOOT_PRIVILEGE_LABEL, status.label)
            .apply()
        context.settingsStore.edit { prefs ->
            prefs[KEY_LAST_PRIVILEGE_ID] = status.id.name
            prefs[KEY_LAST_PRIVILEGE_LABEL] = status.label
        }
    }

    companion object {
        private val KEY_AUTO_MUTE = booleanPreferencesKey("auto_mute_new_channels")
        private val KEY_TIMELINE_ENABLED = booleanPreferencesKey("notification_timeline_enabled")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_AUTHORIZER = stringPreferencesKey("preferred_authorizer")
        private val KEY_LAST_PRIVILEGE_ID = stringPreferencesKey("last_known_privilege_id")
        private val KEY_LAST_PRIVILEGE_LABEL = stringPreferencesKey("last_known_privilege_label")
        private const val BOOT_PRIVILEGE_ID = "last_known_privilege_id"
        private const val BOOT_PRIVILEGE_LABEL = "last_known_privilege_label"
        private const val BOOT_INVENTORY_AT = "inventory_scanned_at"
    }
}
