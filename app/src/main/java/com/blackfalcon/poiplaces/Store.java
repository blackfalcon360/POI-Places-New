package com.blackfalcon.poiplaces;

import android.content.Context;
import android.content.SharedPreferences;

public class Store {

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("poiplaces", Context.MODE_PRIVATE);
    }

    private static String gs(Context c, String k, String d) { return p(c).getString(k, d); }
    private static void ps(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
    private static int gi(Context c, String k, int d) { return p(c).getInt(k, d); }
    private static void pin(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
    private static boolean gb(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }
    private static void pb(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }
    private static long gl(Context c, String k, long d) { return p(c).getLong(k, d); }
    private static void pl(Context c, String k, long v) { p(c).edit().putLong(k, v).apply(); }

    private static double gd(Context c, String k, double d) {
        try {
            return Double.parseDouble(p(c).getString(k, Double.toString(d)));
        } catch (Exception e) {
            return d;
        }
    }

    public static String lang(Context c) { return gs(c, "lang", "en"); }
    public static void setLang(Context c, String v) { ps(c, "lang", v); }

    public static String theme(Context c) { return gs(c, "theme", "dark"); }
    public static void setTheme(Context c, String v) { ps(c, "theme", v); }

    /** Keep running in the background with a notification (default on). */
    public static boolean bg(Context c) { return gb(c, "bg", true); }
    public static void setBg(Context c, boolean v) { pb(c, "bg", v); }

    /** Seconds between location updates. */
    public static int interval(Context c) { return gi(c, "interval", 10); }
    public static void setInterval(Context c, int v) { pin(c, "interval", v); }

    public static boolean onBoot(Context c) { return gb(c, "onBoot", false); }
    public static void setOnBoot(Context c, boolean v) { pb(c, "onBoot", v); }

    /** Allow downloads from the internet (default on; only used when a button is pressed). */
    public static boolean online(Context c) { return gb(c, "online", true); }
    public static void setOnline(Context c, boolean v) { pb(c, "online", v); }

    public static boolean permAsked(Context c) { return gb(c, "permAsked", false); }
    public static void setPermAsked(Context c) { pb(c, "permAsked", true); }

    // last known position, so the header can show a place right after start
    public static boolean hasLast(Context c) { return gb(c, "hasLast", false); }
    public static double lastLat(Context c) { return gd(c, "lastLat", 0); }
    public static double lastLon(Context c) { return gd(c, "lastLon", 0); }
    public static long lastTime(Context c) { return gl(c, "lastTime", 0); }

    public static void saveLast(Context c, double lat, double lon, long t) {
        p(c).edit().putString("lastLat", Double.toString(lat)).putString("lastLon", Double.toString(lon))
                .putLong("lastTime", t).putBoolean("hasLast", true).apply();
    }
}
