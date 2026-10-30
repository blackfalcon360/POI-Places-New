package com.blackfalcon.poiplaces;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Store.bg(context) || !Store.onBoot(context)) return;
        try {
            Intent s = new Intent(context, TrackService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startFg(context, s);
            } else {
                context.startService(s);
            }
        } catch (Exception ignore) {
            // Android did not allow the service to start now
        }
    }

    @TargetApi(26)
    private void startFg(Context c, Intent s) {
        c.startForegroundService(s);
    }
}
