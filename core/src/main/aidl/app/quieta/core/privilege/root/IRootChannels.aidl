package app.quieta.core.privilege.root;

import android.os.Bundle;

interface IRootChannels {
    Bundle probe();
    Bundle listChannels(String packageName, int uid, int offset);
    Bundle setImportance(String packageName, int uid, String channelId, int importance);
    /**
     * Full channel settings write for snapshot import.
     * Bundle keys: hasImportance/int importance, hasSound/soundEnabled,
     * hasVibration/vibrationEnabled, hasLockscreen/lockscreenHidden.
     */
    Bundle applyChannelSettings(String packageName, int uid, String channelId, in Bundle settings);
}
