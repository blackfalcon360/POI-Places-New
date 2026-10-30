package com.blackfalcon.poiplaces;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

/** Registers for GPS and network location updates and feeds LocState. Used by the screen and the service. */
public class LocClient implements LocationListener {

    public interface Cb {
        void onFix();
    }

    private final Context ctx;
    private final Cb cb;
    private LocationManager lm;
    private boolean running = false;
    private long lastSave = 0;

    public LocClient(Context c, Cb cb) {
        this.ctx = c.getApplicationContext();
        this.cb = cb;
    }

    public static boolean hasPerm(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns true when at least one provider accepted the request. */
    public synchronized boolean start(long minTimeMs, Looper looper) {
        if (running) return true;
        if (!hasPerm(ctx)) return false;
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        boolean any = false;
        String[] provs = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
        for (String p : provs) {
            try {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, minTimeMs, 0f, this, looper);
                    any = true;
                }
            } catch (Exception ignore) {
                // provider not usable right now
            }
        }
        String[] last = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
        Location best = null;
        for (String p : last) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignore) {
                // no last location from this provider
            }
        }
        if (best != null && (!LocState.has || LocState.stale)) {
            LocState.offer(best);
            LocState.stale = System.currentTimeMillis() - best.getTime() > 120000L;
        }
        running = any;
        return any;
    }

    public synchronized void stop() {
        if (lm != null) {
            try {
                lm.removeUpdates(this);
            } catch (Exception ignore) {
                // already removed
            }
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void onLocationChanged(Location l) {
        if (l == null) return;
        if (LocState.offer(l)) {
            long now = System.currentTimeMillis();
            if (now - lastSave > 30000L) {
                lastSave = now;
                Store.saveLast(ctx, LocState.lat, LocState.lon, now);
            }
            if (cb != null) cb.onFix();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
