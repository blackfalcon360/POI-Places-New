package com.blackfalcon.poiplaces;

import android.content.Context;
import android.location.Location;

/** The newest known position, shared by the screen and the background service (same process). */
public class LocState {
    public static volatile boolean has = false;
    /** True while the position is only the one saved from an earlier session. */
    public static volatile boolean stale = true;
    public static volatile double lat = 0;
    public static volatile double lon = 0;
    public static volatile double alt = Double.NaN;
    public static volatile float acc = -1;
    public static volatile long time = 0;
    private static String provider = "";
    private static long recv = 0;

    /** Takes a new fix unless a clearly worse one arrives from another provider right after a good one. */
    public static synchronized boolean offer(Location l) {
        long now = System.currentTimeMillis();
        float newAcc = l.hasAccuracy() ? l.getAccuracy() : 9999f;
        float oldAcc = acc >= 0 ? acc : 9999f;
        boolean take = !has || stale || provider.equals(l.getProvider()) || newAcc <= oldAcc || now - recv > 20000;
        if (!take) return false;
        has = true;
        stale = false;
        lat = l.getLatitude();
        lon = l.getLongitude();
        alt = l.hasAltitude() ? l.getAltitude() : Double.NaN;
        acc = l.hasAccuracy() ? l.getAccuracy() : -1;
        provider = l.getProvider() == null ? "" : l.getProvider();
        time = now;
        recv = now;
        return true;
    }

    /** Loads the saved position from the last session when nothing newer is known. */
    public static synchronized void load(Context c) {
        if (has || !Store.hasLast(c)) return;
        lat = Store.lastLat(c);
        lon = Store.lastLon(c);
        alt = Double.NaN;
        acc = -1;
        time = Store.lastTime(c);
        has = true;
        stale = true;
    }
}
