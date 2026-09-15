package app.quieta.core.privilege.root;

import android.os.Bundle;

interface IRootChannels {
    Bundle probe();
    Bundle listChannels(String packageName, int uid, int offset);
    Bundle setImportance(String packageName, int uid, String channelId, int importance);
}
