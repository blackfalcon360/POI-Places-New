package com.blackfalcon.poiplaces;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure helper functions (no Android classes) so they can be tested separately. */
public class Geo {

    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Initial bearing from point 1 to point 2, 0..360 degrees. */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    /** 0=N 1=NE 2=E 3=SE 4=S 5=SW 6=W 7=NW */
    public static int sector(double bearing) {
        double b = ((bearing % 360.0) + 360.0) % 360.0;
        return (int) Math.floor(((b + 22.5) % 360.0) / 45.0);
    }

    /** Degrees of latitude that cover the given distance. */
    public static double dLat(double km) {
        return km / 110.574;
    }

    /** Degrees of longitude that cover the given distance at a latitude (capped near the poles). */
    public static double dLon(double km, double lat) {
        double c = Math.cos(Math.toRadians(Math.min(Math.abs(lat), 89.0)));
        return km / (111.320 * c);
    }

    public static String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    /** True when the distance is shown in meters (under 1 km). */
    public static boolean distIsMeters(double km) {
        return km < 0.95;
    }

    /** Number part of a distance: meters rounded to 10, then 1 decimal under 10 km, then whole km. */
    public static String distValue(double km) {
        if (distIsMeters(km)) {
            long m = Math.round(km * 1000.0 / 10.0) * 10L;
            if (m < 10) m = 10;
            return String.valueOf(m);
        }
        if (km < 10) return String.format(Locale.US, "%.1f", km);
        return String.format(Locale.US, "%.0f", km);
    }

    /** 34.01234 / 71.98765 becomes "34.01234\u00B0N 71.98765\u00B0E". */
    public static String fmtCoord(double lat, double lon) {
        return String.format(Locale.US, "%.5f\u00B0%s %.5f\u00B0%s",
                Math.abs(lat), lat >= 0 ? "N" : "S", Math.abs(lon), lon >= 0 ? "E" : "W");
    }

    /** Plain decimal pair, easy to paste anywhere. */
    public static String fmtPlain(double lat, double lon) {
        return String.format(Locale.US, "%.6f, %.6f", lat, lon);
    }

    public static String isoUtc(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    /** Parses "2026-10-27T08:14:03.5Z" (also +00:00) to epoch millis; 0 on failure. */
    public static long parseIso(String s) {
        try {
            String t = s.trim();
            if (t.endsWith("Z")) t = t.substring(0, t.length() - 1);
            if (t.endsWith("+00:00")) t = t.substring(0, t.length() - 6);
            long ms = 0;
            int dot = t.indexOf('.');
            if (dot >= 0) {
                String frac = t.substring(dot + 1);
                t = t.substring(0, dot);
                while (frac.length() < 3) frac = frac + "0";
                ms = Long.parseLong(frac.substring(0, 3));
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(t).getTime() + ms;
        } catch (Exception e) {
            return 0;
        }
    }

    private static final Pattern DMS = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*[\u00B0d]\\s*(?:(\\d+(?:\\.\\d+)?)\\s*['\u2032m]\\s*)?(?:(\\d+(?:\\.\\d+)?)\\s*(?:\"|\u2033|'')\\s*)?([NSEWnsew])");
    /**
     * Parses a typed coordinate pair: "34.0123, 71.9876", "34.0123N 71.9876E", "N 34.0123 E 71.9876",
     * or degrees/minutes/seconds with symbols such as 34\u00B003'44"N 71\u00B059'45"E.
     * Returns {lat, lon} or null when it is not a valid pair.
     */
    public static double[] parseCoords(String in) {
        if (in == null) return null;
        String s = in.trim();
        if (s.regionMatches(true, 0, "geo:", 0, 4)) s = s.substring(4);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        if (s.length() == 0) return null;

        Matcher m = DMS.matcher(s);
        double[] vals = new double[2];
        boolean[] seen = new boolean[2];
        int found = 0;
        while (m.find()) {
            double deg = Double.parseDouble(m.group(1));
            double min = m.group(2) != null ? Double.parseDouble(m.group(2)) : 0;
            double sec = m.group(3) != null ? Double.parseDouble(m.group(3)) : 0;
            if (min >= 60 || sec >= 60) return null;
            double v = deg + min / 60.0 + sec / 3600.0;
            char h = Character.toUpperCase(m.group(4).charAt(0));
            boolean isLat = (h == 'N' || h == 'S');
            int idx = isLat ? 0 : 1;
            if (seen[idx]) return null;
            seen[idx] = true;
            vals[idx] = (h == 'S' || h == 'W') ? -v : v;
            found++;
        }
        if (found == 2) return valid(vals[0], vals[1]);

        return parseDecimal(s);
    }

    /** Decimal pair with optional N/S/E/W letters before (N 34.1 E 71.2) or after (34.1N 71.2E) each number. */
    private static double[] parseDecimal(String s) {
        java.util.List<String> toks = new java.util.ArrayList<String>();
        int k = 0;
        while (k < s.length()) {
            char c = s.charAt(k);
            if (c == ' ' || c == ',' || c == ';' || c == '\t' || c == '\u00B0') {
                k++;
            } else if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+') {
                int st = k;
                while (k < s.length()) {
                    char d = s.charAt(k);
                    if ((d >= '0' && d <= '9') || d == '.') k++;
                    else if ((d == '-' || d == '+') && k == st) k++;
                    else break;
                }
                toks.add(s.substring(st, k));
            } else if ("NSEWnsew".indexOf(c) >= 0) {
                toks.add(String.valueOf(Character.toUpperCase(c)));
                k++;
            } else {
                return null;
            }
        }
        if (toks.isEmpty()) return null;
        boolean prefix = Character.isLetter(toks.get(0).charAt(0));
        double[] nums = new double[2];
        char[] hs = new char[2];
        int n = 0;
        char pending = 0;
        for (String t : toks) {
            char c0 = t.charAt(0);
            if (Character.isLetter(c0)) {
                if (prefix) {
                    if (pending != 0) return null;
                    pending = c0;
                } else {
                    if (n == 0 || hs[n - 1] != 0) return null;
                    hs[n - 1] = c0;
                }
            } else {
                if (n >= 2) return null;
                try {
                    nums[n] = Double.parseDouble(t);
                } catch (Exception e) {
                    return null;
                }
                if (prefix) {
                    hs[n] = pending;
                    pending = 0;
                }
                n++;
            }
        }
        if (n != 2 || pending != 0) return null;
        boolean a0 = hs[0] == 'N' || hs[0] == 'S';
        boolean a1 = hs[0] == 'E' || hs[0] == 'W';
        boolean b0 = hs[1] == 'N' || hs[1] == 'S';
        boolean b1 = hs[1] == 'E' || hs[1] == 'W';
        int latIdx;
        if (a0 && b0) return null;
        if (a1 && b1) return null;
        if (a0 || b1) latIdx = 0;
        else if (a1 || b0) latIdx = 1;
        else latIdx = 0;
        int lonIdx = 1 - latIdx;
        double lat = nums[latIdx];
        double lon = nums[lonIdx];
        if (hs[latIdx] != 0) lat = hs[latIdx] == 'S' ? -Math.abs(lat) : Math.abs(lat);
        if (hs[lonIdx] != 0) lon = hs[lonIdx] == 'W' ? -Math.abs(lon) : Math.abs(lon);
        return valid(lat, lon);
    }

    private static double[] valid(double lat, double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
        return new double[]{lat, lon};
    }
}
