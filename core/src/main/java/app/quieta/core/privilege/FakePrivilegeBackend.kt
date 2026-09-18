package app.quieta.core.privilege

import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId

/** In-memory backend for previews and unit tests. */
class FakePrivilegeBackend(
    private val available: Boolean = true,
) : PrivilegeBackend {

    override val id: PrivilegeId = PrivilegeId.NONE

    /** Mutable demo store so applyChannelSettings can be unit-tested. */
    private val store: MutableMap<String, MutableList<Channel>> =
        demoChannels().groupBy { it.packageName }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()

    val appliedPatches = mutableListOf<ChannelSettingsPatch>()

    override suspend fun isAvailable(): Boolean = available

    override suspend fun listChannels(packageName: String): List<Channel> =
        if (!available) emptyList() else store[packageName].orEmpty().toList()

    override suspend fun setImportance(
        packageName: String,
        channelId: String,
        importance: Int,
    ) {
        applyChannelSettings(
            ChannelSettingsPatch(packageName, channelId, importance = importance),
        )
    }

    override suspend fun applyChannelSettings(patch: ChannelSettingsPatch) {
        if (!available) error("Fake backend unavailable")
        appliedPatches += patch
        val list = store[patch.packageName] ?: error("unknown package ${patch.packageName}")
        val idx = list.indexOfFirst { it.id == patch.channelId }
        if (idx < 0) error("unknown channel ${patch.channelId}")
        val current = list[idx]
        val importance = patch.importance?.let { intToEnum(it) } ?: current.importance
        list[idx] = current.copy(
            importance = importance,
            soundEnabled = patch.soundEnabled ?: current.soundEnabled,
            vibrationEnabled = patch.vibrationEnabled ?: current.vibrationEnabled,
            lockscreenHidden = patch.lockscreenHidden ?: current.lockscreenHidden,
        )
    }

    private fun intToEnum(v: Int): ChannelImportance = when (v) {
        0 -> ChannelImportance.NONE
        1 -> ChannelImportance.MIN
        2 -> ChannelImportance.LOW
        4, 5 -> ChannelImportance.HIGH
        else -> ChannelImportance.DEFAULT
    }

    companion object {
        fun demoApps(): List<AppChannels> {
            val byPkg = demoChannels().groupBy { it.packageName }
            return listOf(
                AppChannels("com.taobao.taobao", "淘宝", byPkg.getValue("com.taobao.taobao")),
                AppChannels("com.jingdong.app.mall", "京东", byPkg.getValue("com.jingdong.app.mall")),
                AppChannels("com.tencent.mm", "微信", byPkg.getValue("com.tencent.mm")),
            )
        }

        private fun demoChannels(): List<Channel> = listOf(
            Channel("com.taobao.taobao", "promo", "促销活动", ChannelImportance.DEFAULT),
            Channel("com.taobao.taobao", "order", "订单物流", ChannelImportance.HIGH),
            Channel("com.taobao.taobao", "live", "直播提醒", ChannelImportance.LOW),
            Channel("com.jingdong.app.mall", "marketing", "营销推荐", ChannelImportance.DEFAULT),
            Channel("com.jingdong.app.mall", "order", "订单通知", ChannelImportance.HIGH),
            Channel("com.tencent.mm", "voip", "语音通话", ChannelImportance.HIGH),
            Channel("com.tencent.mm", "misc", "其它通知", ChannelImportance.DEFAULT),
        )
    }
}
