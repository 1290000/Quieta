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

enum class PaletteStyle { TonalSpot, Vibrant, Expressive, Spritz, FruitSalad, Rainbow, Monochrome }

enum class ThemeColorSpec { SPEC_2021, SPEC_2025 }

enum class PredictiveBackAnimation { NONE, AOSP, MIUIX, SCALE, CLASSIC }

enum class PredictiveBackExitDirection { FOLLOW_GESTURE, ALWAYS_RIGHT, ALWAYS_LEFT }

/** Persisted capability snapshot for cold-start UI seed only. */
data class CachedCapabilities(
    val shizukuAvailable: Boolean,
    val shizukuAuthorized: Boolean,
    val dhizukuAvailable: Boolean,
    val rootAvailable: Boolean,
    val rootLabel: String,
    val rootDescription: String,
    val rootWriteSupported: Boolean,
)

class AppSettings(private val context: Context) {

    private val bootPrefs = context.getSharedPreferences("quieta_boot", Context.MODE_PRIVATE)

    val themeMode: Flow<ThemeMode> = context.settingsStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val blurEnabled: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_BLUR_ENABLED] ?: true
    }

    suspend fun setBlurEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_BLUR_ENABLED] = enabled }
    }

    val customColors: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_CUSTOM_COLORS] ?: false
    }

    suspend fun setCustomColors(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_CUSTOM_COLORS] = enabled }
    }

    val dynamicColor: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    val paletteStyle: Flow<PaletteStyle> = context.settingsStore.data.map { prefs ->
        prefs[KEY_PALETTE_STYLE]?.let { runCatching { PaletteStyle.valueOf(it) }.getOrNull() }
            ?: PaletteStyle.TonalSpot
    }

    suspend fun setPaletteStyle(style: PaletteStyle) {
        context.settingsStore.edit { it[KEY_PALETTE_STYLE] = style.name }
    }

    val predictiveBackAnimation: Flow<PredictiveBackAnimation> = context.settingsStore.data.map { prefs ->
        prefs[KEY_PB_ANIMATION]?.let { runCatching { PredictiveBackAnimation.valueOf(it) }.getOrNull() }
            ?: PredictiveBackAnimation.MIUIX
    }

    suspend fun setPredictiveBackAnimation(value: PredictiveBackAnimation) {
        context.settingsStore.edit { it[KEY_PB_ANIMATION] = value.name }
    }

    val predictiveBackExitDirection: Flow<PredictiveBackExitDirection> = context.settingsStore.data.map { prefs ->
        prefs[KEY_PB_EXIT]?.let { runCatching { PredictiveBackExitDirection.valueOf(it) }.getOrNull() }
            ?: PredictiveBackExitDirection.ALWAYS_RIGHT
    }

    suspend fun setPredictiveBackExitDirection(value: PredictiveBackExitDirection) {
        context.settingsStore.edit { it[KEY_PB_EXIT] = value.name }
    }

    /** Material color spec version for seed schemes (InstallerX ThemeColorSpec). */
    val themeColorSpec: Flow<ThemeColorSpec> = context.settingsStore.data.map { prefs ->
        prefs[KEY_COLOR_SPEC]?.let { runCatching { ThemeColorSpec.valueOf(it) }.getOrNull() }
            ?: ThemeColorSpec.SPEC_2025
    }

    suspend fun setThemeColorSpec(spec: ThemeColorSpec) {
        context.settingsStore.edit { it[KEY_COLOR_SPEC] = spec.name }
    }

    /** ARGB seed for custom colors; defaults to purple Material seed. */
    val seedColorInt: Flow<Int> = context.settingsStore.data.map { prefs ->
        prefs[KEY_SEED_COLOR] ?: 0xFF6750A4.toInt()
    }

    suspend fun setSeedColorInt(argb: Int) {
        context.settingsStore.edit { it[KEY_SEED_COLOR] = argb }
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

    val enableFileLogging: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_FILE_LOGGING] ?: false
    }

    suspend fun setEnableFileLogging(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_FILE_LOGGING] = enabled
        }
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

    /**
     * Last privilege capability table (InstallerX Flow-style cache). Used only to seed
     * authorizer stats on cold start — never treated as live authorization proof.
     */
    fun capabilitiesSync(): CachedCapabilities? {
        if (!bootPrefs.contains(BOOT_CAP_SHIZUKU_AVAILABLE) &&
            !bootPrefs.contains(BOOT_CAP_ROOT_AVAILABLE)
        ) {
            return null
        }
        return CachedCapabilities(
            shizukuAvailable = bootPrefs.getBoolean(BOOT_CAP_SHIZUKU_AVAILABLE, false),
            shizukuAuthorized = bootPrefs.getBoolean(BOOT_CAP_SHIZUKU_AUTHORIZED, false),
            dhizukuAvailable = bootPrefs.getBoolean(BOOT_CAP_DHIZUKU_AVAILABLE, false),
            rootAvailable = bootPrefs.getBoolean(BOOT_CAP_ROOT_AVAILABLE, false),
            rootLabel = bootPrefs.getString(BOOT_CAP_ROOT_LABEL, null).orEmpty(),
            rootDescription = bootPrefs.getString(BOOT_CAP_ROOT_DESC, null).orEmpty(),
            rootWriteSupported = bootPrefs.getBoolean(BOOT_CAP_ROOT_WRITE, false),
        )
    }

    fun saveCapabilities(caps: CachedCapabilities) {
        bootPrefs.edit()
            .putBoolean(BOOT_CAP_SHIZUKU_AVAILABLE, caps.shizukuAvailable)
            .putBoolean(BOOT_CAP_SHIZUKU_AUTHORIZED, caps.shizukuAuthorized)
            .putBoolean(BOOT_CAP_DHIZUKU_AVAILABLE, caps.dhizukuAvailable)
            .putBoolean(BOOT_CAP_ROOT_AVAILABLE, caps.rootAvailable)
            .putString(BOOT_CAP_ROOT_LABEL, caps.rootLabel)
            .putString(BOOT_CAP_ROOT_DESC, caps.rootDescription)
            .putBoolean(BOOT_CAP_ROOT_WRITE, caps.rootWriteSupported)
            .apply()
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
        private val KEY_FILE_LOGGING = booleanPreferencesKey("enable_file_logging")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        private val KEY_CUSTOM_COLORS = booleanPreferencesKey("theme_custom_colors")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("theme_dynamic_color")
        private val KEY_PALETTE_STYLE = stringPreferencesKey("theme_palette_style")
        private val KEY_PB_ANIMATION = stringPreferencesKey("predictive_back_animation")
        private val KEY_PB_EXIT = stringPreferencesKey("predictive_back_exit")
        private val KEY_COLOR_SPEC = stringPreferencesKey("theme_color_spec")
        private val KEY_SEED_COLOR = androidx.datastore.preferences.core.intPreferencesKey("theme_seed_color")
        private val KEY_AUTHORIZER = stringPreferencesKey("preferred_authorizer")
        private val KEY_LAST_PRIVILEGE_ID = stringPreferencesKey("last_known_privilege_id")
        private val KEY_LAST_PRIVILEGE_LABEL = stringPreferencesKey("last_known_privilege_label")
        private const val BOOT_PRIVILEGE_ID = "last_known_privilege_id"
        private const val BOOT_PRIVILEGE_LABEL = "last_known_privilege_label"
        private const val BOOT_INVENTORY_AT = "inventory_scanned_at"
        private const val BOOT_CAP_SHIZUKU_AVAILABLE = "cap_shizuku_available"
        private const val BOOT_CAP_SHIZUKU_AUTHORIZED = "cap_shizuku_authorized"
        private const val BOOT_CAP_DHIZUKU_AVAILABLE = "cap_dhizuku_available"
        private const val BOOT_CAP_ROOT_AVAILABLE = "cap_root_available"
        private const val BOOT_CAP_ROOT_LABEL = "cap_root_label"
        private const val BOOT_CAP_ROOT_DESC = "cap_root_description"
        private const val BOOT_CAP_ROOT_WRITE = "cap_root_write_supported"
    }
}
