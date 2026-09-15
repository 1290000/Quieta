package app.quieta.core.privilege.root

/** Identity established by executing commands in the authorized root shell. */
data class RootIdentity(val available: Boolean, val manager: String? = null) {
    companion object {
        fun fromProbe(exitCode: Int, output: String): RootIdentity {
            val lines = output.lineSequence().map(String::trim).toSet()
            if (exitCode != 0 || "QUIETA_ROOT_OK" !in lines) return RootIdentity(false)
            val version = lines.firstOrNull { it.startsWith("QUIETA_SU_VERSION=") }
                ?.substringAfter('=')?.lowercase().orEmpty()
            val manager = when {
                "resukisu" in version -> "ReSukiSU"
                "sukisu" in version -> "SukiSU"
                "kernelsu next" in version || "kernelsu-next" in version -> "KernelSU Next"
                "magisk" in version -> "Magisk"
                "kernelsu" in version -> "KernelSU"
                "apatch" in version -> "APatch"
                else -> null
            }
            return RootIdentity(true, manager)
        }
    }
}
