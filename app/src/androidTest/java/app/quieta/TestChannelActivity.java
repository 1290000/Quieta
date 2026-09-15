package app.quieta;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Bundle;

/** Standalone test fixture requiring no target APK classes or Kotlin runtime. */
public final class TestChannelActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("channel");
        if (id != null && id.startsWith("quieta.root.test.")) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (getIntent().getBooleanExtra("delete", false)) manager.deleteNotificationChannel(id);
            else manager.createNotificationChannel(new NotificationChannel(id, "Root verification", 3));
        }
        finish();
    }
}
