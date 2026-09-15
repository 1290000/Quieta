package app.quieta.core.privilege

import app.quieta.core.model.PrivilegeId
import app.quieta.core.settings.PreferredAuthorizer
import app.quieta.core.privilege.root.verifyImportance
import org.junit.Assert.*
import org.junit.Test

class AuthorizerSelectionTest {
    @Test fun autoUsesRootThenShizukuThenDhizuku() {
        for (root in listOf(false, true)) for (shizuku in listOf(false, true)) for (dhizuku in listOf(false, true)) {
            val expected = when { root -> PrivilegeId.ROOT; shizuku -> PrivilegeId.SHIZUKU
                dhizuku -> PrivilegeId.DHIZUKU; else -> PrivilegeId.NONE }
            assertEquals(expected, selectAuthorizer(PreferredAuthorizer.AUTO, root, shizuku, dhizuku))
        }
    }

    @Test fun explicitChoiceNeverFallsBack() {
        assertEquals(PrivilegeId.NONE, selectAuthorizer(PreferredAuthorizer.ROOT, false, true, true))
        assertEquals(PrivilegeId.NONE, selectAuthorizer(PreferredAuthorizer.NONE, true, true, true))
        assertEquals(PrivilegeId.SHIZUKU, selectAuthorizer(PreferredAuthorizer.SHIZUKU, true, true, true))
        assertEquals(PrivilegeId.DHIZUKU, selectAuthorizer(PreferredAuthorizer.DHIZUKU, true, true, true))
    }

    @Test fun writesRequireMatchingReadBack() {
        verifyImportance(0, 0)
        assertThrows(IllegalStateException::class.java) { verifyImportance(0, 3) }
        assertThrows(IllegalStateException::class.java) { verifyImportance(0, null) }
    }
}
