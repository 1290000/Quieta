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

    override suspend fun isAvailable(): Boolean = available

    override suspend fun listChannels(packageName: String): List<Channel> =
        if (!available) emptyList() else demoChannels().filter { it.packageName == packageName }

    override suspend fun setImportance(
        packageName: String,
        channelId: String,
        importance: Int,
    ) = Unit

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
