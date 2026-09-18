package app.quieta.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelEffectiveSoundTest {

    private fun channel(
        importance: ChannelImportance,
        soundEnabled: Boolean,
    ) = Channel(
        packageName = "app.quieta.notiflab.debug",
        id = "lab.test",
        name = "测试",
        importance = importance,
        soundEnabled = soundEnabled,
    )

    @Test fun defaultAndHighWithUriAreAudible() {
        assertTrue(channel(ChannelImportance.DEFAULT, true).effectiveSoundEnabled)
        assertTrue(channel(ChannelImportance.HIGH, true).effectiveSoundEnabled)
    }

    @Test fun lowMinNoneWithUriAreSilent() {
        assertFalse(channel(ChannelImportance.LOW, true).effectiveSoundEnabled)
        assertFalse(channel(ChannelImportance.MIN, true).effectiveSoundEnabled)
        assertFalse(channel(ChannelImportance.NONE, true).effectiveSoundEnabled)
    }

    @Test fun missingUriIsSilentEvenWhenImportanceHigh() {
        assertFalse(channel(ChannelImportance.HIGH, false).effectiveSoundEnabled)
        assertFalse(channel(ChannelImportance.DEFAULT, false).effectiveSoundEnabled)
    }
}
