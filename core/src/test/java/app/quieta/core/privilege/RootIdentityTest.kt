package app.quieta.core.privilege

import app.quieta.core.privilege.root.RootIdentity
import org.junit.Assert.*
import org.junit.Test

class RootIdentityTest {
    @Test fun recognizesRuntimeWithoutManagerApk() {
        mapOf("28.1:MAGISK" to "Magisk", "12000:KernelSU" to "KernelSU",
            "1.0:APatch" to "APatch", "1:ReSukiSU" to "ReSukiSU",
            "1:SukiSU" to "SukiSU", "1:KernelSU-Next" to "KernelSU Next").forEach { (version, name) ->
            assertEquals(RootIdentity(true, name), RootIdentity.fromProbe(0,
                "QUIETA_ROOT_OK\nQUIETA_SU_VERSION=$version\n"))
        }
    }

    @Test fun rejectsDeniedAndNonRootShells() {
        assertFalse(RootIdentity.fromProbe(1, "QUIETA_ROOT_OK\nQUIETA_SU_VERSION=Magisk").available)
        assertFalse(RootIdentity.fromProbe(0, "QUIETA_SU_VERSION=KernelSU").available)
    }

    @Test fun unknownRootIsStillUsable() {
        assertEquals(RootIdentity(true), RootIdentity.fromProbe(0, "QUIETA_ROOT_OK\nQUIETA_SU_VERSION=custom"))
    }

    @Test fun ignoresDormantBinariesAndUnstructuredOutput() {
        assertEquals("Magisk", RootIdentity.fromProbe(0,
            "QUIETA_ROOT_OK\nQUIETA_SU_VERSION=28:MAGISK\nQUIETA_ROOT_KSU\nReSukiSU").manager)
    }
}
