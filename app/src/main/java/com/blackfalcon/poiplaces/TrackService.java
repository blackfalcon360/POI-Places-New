package com.blackfalcon.poiplaces;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.List;

/** Foreground service: keeps following your position after Home is pressed and shows the place name in a notification. */
public class TrackService extends Service implements LocClient.Cb {

    public static final String ACT_STOP = "com.blackfalcon.poiplaces.STOP";
    private static final int NID = 2001;

    private HandlerThread ht;
    private LocClient client;
    private long lastNotify = 0;
    private String lastKey = "";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACT_STOP.equals(intent.getAction())) {
            shutDown();
            return START_NOT_STICKY;
        }
        Notifier.ensureChannel(this);
        LocState.load(this);
        if (!startFg(buildNotification())) {
            shutDown();
            return START_NOT_STICKY;
        }
        if (!Store.bg(this)) {
            shutDown();
            return START_NOT_STICKY;
        }
        if (ht == null) {
            ht = new HandlerThread("poiplaces-loc");
            ht.start();
            client = new LocClient(this, this);
        }
        client.stop();
        client.start(Math.max(1, Store.interval(this)) * 1000L, ht.getLooper());
        return START_STICKY;
    }

    private boolean startFg(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    startFg34(n, true);
                } catch (Exception e) {
                    startFg34(n, false);
                }
            } else if (Build.VERSION.SDK_INT >= 29) {
                try {
                    startFg29(n);
                } catch (Exception e) {
                    startForeground(NID, n);
                }
            } else {
                startForeground(NID, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @TargetApi(34)
    private void startFg34(Notification n, boolean withLocation) {
        int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        if (withLocation) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        startForeground(NID, n, type);
    }

    @TargetApi(29)
    private void startFg29(Notification n) {
        startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    }

    private Notification buildNotification() {
        if (!LocState.has) {
            String t = L.t(this, "Waiting for location\u2026", "مقام کا انتظار\u2026", "بانتظار الموقع\u2026");
            return Notifier.build(this, t, t, t);
        }
        Namer.Info info = Where.describe(this, LocState.lat, LocState.lon);
        String title = Where.title(this, info);
        String coords = Geo.fmtCoord(LocState.lat, LocState.lon);
        StringBuilder big = new StringBuilder(coords);
        if (LocState.acc >= 0) {
            big.append("  \u00B1").append(Math.round(LocState.acc)).append(" ").append(L.t(this, "m", "میٹر", "م"));
        }
        if (!Double.isNaN(LocState.alt)) {
            big.append("\n").append(L.t(this, "Altitude: ", "بلندی: ", "الارتفاع: "))
                    .append(Math.round(LocState.alt)).append(" ").append(L.t(this, "m", "میٹر", "م"));
        }
        String city = Where.cityLine(this, info);
        if (city.length() > 0 && info.place != null) big.append("\n").append(city);
        String poi = nearestPoiLine();
        if (poi.length() > 0) big.append("\n").append(poi);
        return Notifier.build(this, title, coords, big.toString());
    }

    private String nearestPoiLine() {
        try {
            List<Poi> list = Db.get(this).pois();
            Poi best = null;
            double bd = Double.MAX_VALUE;
            for (Poi p : list) {
                double d = Geo.km(LocState.lat, LocState.lon, p.lat, p.lon);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            if (best == null) return "";
            int sec = Geo.sector(Geo.bearing(LocState.lat, LocState.lon, best.lat, best.lon));
            return String.format(java.util.Locale.US, L.t(this, "Nearest saved POI: %1$s, %2$s %3$s",
                    "قریب ترین محفوظ POI: %1$s، %2$s %3$s", "أقرب نقطة محفوظة: %1$s، %2$s %3$s"),
                    best.name, L.dist(this, bd), L.dir(this, sec));
        } catch (Exception e) {
            return "";
        }
    }

    /** Called on the service's own thread for each new position. */
    @Override
    public void onFix() {
        long now = System.currentTimeMillis();
        String key = Math.round(LocState.lat * 2000) + "," + Math.round(LocState.lon * 2000);
        if (key.equals(lastKey) && now - lastNotify < 30000L) return;
        if (now - lastNotify < 4000L) return;
        lastKey = key;
        lastNotify = now;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NID, buildNotification());
        } catch (Exception ignore) {
            // notification could not be updated this time
        }
    }

    private void shutDown() {
        if (client != null) client.stop();
        if (ht != null) {
            ht.quitSafely();
            ht = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (client != null) client.stop();
        if (ht != null) ht.quitSafely();
        super.onDestroy();
    }
}
