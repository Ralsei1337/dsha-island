package io.github.dsha.island.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** Best-effort restart after boot; Android 15+ may reject background FGS starts. */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "DshaIslandLite";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            Intent service = new Intent(context, IslandService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Throwable t) {
            Log.w(TAG, "boot start rejected", t);
        }
    }
}
