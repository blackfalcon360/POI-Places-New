package com.blackfalcon.poiplaces;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** The ongoing notification that shows the current place name. */
public class Notifier {

    public static final String CH = "tracking";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) channel26(c);
    }

    @TargetApi(26)
    private static void channel26(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH,
                L.t(c, "Current place", "موجودہ مقام", "المكان الحالي"), NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
    }

    @TargetApi(26)
    private static Notification.Builder builder26(Context c) {
        return new Notification.Builder(c, CH);
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder newBuilder(Context c) {
        if (Build.VERSION.SDK_INT >= 26) return builder26(c);
        return new Notification.Builder(c);
    }

    @SuppressWarnings("deprecation")
    public static Notification build(Context c, String title, String text, String big) {
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(c, TrackService.class);
        stop.setAction(TrackService.ACT_STOP);
        PendingIntent sp = PendingIntent.getService(c, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = newBuilder(c);
        b.setSmallIcon(R.drawable.ic_stat);
        b.setContentTitle(title);
        b.setContentText(text);
        b.setStyle(new Notification.BigTextStyle().bigText(big));
        b.setOngoing(true);
        b.setOnlyAlertOnce(true);
        b.setShowWhen(false);
        b.setContentIntent(pi);
        b.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                L.t(c, "Stop", "بند کریں", "إيقاف"), sp);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        return b.build();
    }
}
