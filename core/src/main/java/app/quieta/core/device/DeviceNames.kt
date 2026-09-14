package app.quieta.core.device

import android.os.Build

object DeviceNames {
    /**
     * Official marketing name if available (Xiaomi/OPPO marketname props),
     * then Build.MODEL. Format: "Redmi K40S (22021211RC)" or just model.
     */
    fun display(): String {
        val market = marketName()
        val model = Build.MODEL.orEmpty()
        return when {
            market.isNullOrBlank() -> model.ifBlank { "—" }
            market.equals(model, ignoreCase = true) -> model
            model.isBlank() -> market
            else -> "$market ($model)"
        }
    }

    private fun marketName(): String? {
        val keys = listOf(
            "ro.product.marketname",
            "ro.product.vendor.marketname",
            "ro.product.oppo.marketname",
            "ro.product.model.display",
        )
        return runCatching {
            val clz = Class.forName("android.os.SystemProperties")
            val get = clz.getMethod("get", String::class.java, String::class.java)
            keys.asSequence()
                .map { key -> get.invoke(null, key, "") as? String }
                .firstOrNull { !it.isNullOrBlank() }
        }.getOrNull()
    }
}
