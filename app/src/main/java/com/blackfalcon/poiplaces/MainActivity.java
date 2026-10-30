package com.blackfalcon.poiplaces;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements LocClient.Cb {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int ACC = 0xFF00BFA5;
    private static final int ON_ACC = 0xFF000000;
    private static final int RC_PERM = 100;
    private static final int RC_BG = 101;
    private static final int RC_EXPORT = 102;
    private static final int RC_IMPORT = 103;

    private interface Pick {
        void on(int k);
    }

    /** One line in the Nearby or Saved list. */
    private static class Row {
        String icon = "";
        String title = "";
        String sub = "";
        double lat;
        double lon;
        double dist;
        Poi poi;
        Place place;
    }

    private static class Holder {
        TextView icon;
        TextView title;
        TextView sub;
        TextView dist;
        TextView act;
    }

    // screen parts
    private LinearLayout root;
    private FrameLayout content;
    private TextView placeTv;
    private TextView coordTv;
    private TextView cityTv;
    private CompassView compass;
    private final TextView[] tabViews = new TextView[4];
    private int cur = 0;
    private int bg, card, chipBg, txt, sub;

    // location, place name, compass
    private LocClient locClient;
    private Namer.Info info = null;
    private boolean namerBusy = false;
    private double namerLat = 0;
    private double namerLon = 0;
    private long namerTime = 0;
    private SensorManager sm;
    private Sensor rotSensor;
    private Sensor accSensor;
    private Sensor magSensor;
    private float[] gravity;
    private float[] geo;
    private float sinSm = 0;
    private float cosSm = 1;
    private boolean smInit = false;
    private float declination = 0;
    private long lastSensorUi = 0;
    private long lastListUi = 0;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    // Nearby tab
    private String nearFilter = "all";
    private int nearRadius = 25;
    private RowAdapter nearAd;
    private TextView nearInfo;
    private int nearReq = 0;
    private double nearLat = 0;
    private double nearLon = 0;

    // Saved tab
    private String savedSort = "near";
    private RowAdapter savedAd;
    private TextView savedInfo;
    private int savedReq = 0;
    private String pendingExport = null;

    // Offline tab
    private String dlScope = "settlements";
    private int dlRadius = 25;
    private boolean downloading = false;
    private TextView dlStatus;
    private LinearLayout packsBox;

    // Settings (values chosen but not saved yet)
    private String pLang, pTheme;
    private boolean pBg, pBoot, pOnline;
    private int pInterval;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocState.load(this);
        locClient = new LocClient(this, this);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            accSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        build();
        if (!Store.permAsked(this)) {
            Store.setPermAsked(this);
            askPerms();
        } else {
            afterPerms();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        locClient.start(2000, Looper.getMainLooper());
        registerSensors();
        updateDeclination();
        updateHeader();
        requestNamer(true);
        reloadTab();
    }

    @Override
    protected void onPause() {
        locClient.stop();
        if (sm != null) sm.unregisterListener(sensorListener);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (cur != 0) {
            showTab(0);
            return;
        }
        confirmExit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_PERM) afterPerms();
    }

    // ------------------------------------------------------------------ small helpers

    private String tr(String en, String ur, String ar) {
        return L.t(this, en, ur, ar);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView button(String label, int bgColor, int fg, View.OnClickListener l) {
        TextView t = tv(label, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(11), dp(10), dp(11));
        t.setBackground(rounded(bgColor, 10));
        t.setOnClickListener(l);
        return t;
    }

    /** Adds an equal-width button to a horizontal row. The row itself must be added with MATCH_PARENT width. */
    private void addBtn(LinearLayout row, String label, int bgColor, int fg, View.OnClickListener l) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button(label, bgColor, fg, l), p);
    }

    /** Full-width button added to a vertical box. */
    private void addWide(LinearLayout box, String label, int bgColor, int fg, View.OnClickListener l) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.setMargins(0, dp(8), 0, 0);
        box.addView(button(label, bgColor, fg, l), p);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    private int indexOfInt(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private void applyTheme() {
        boolean dark = !"light".equals(Store.theme(this));
        if (dark) {
            bg = 0xFF000000;
            card = 0xFF161616;
            chipBg = 0xFF2A2A2A;
            txt = 0xFFFFFFFF;
            sub = 0xFF9E9E9E;
        } else {
            bg = 0xFFF2F2F2;
            card = 0xFFFFFFFF;
            chipBg = 0xFFDEDEDE;
            txt = 0xFF111111;
            sub = 0xFF5F5F5F;
        }
    }

    private void styleChip(TextView t, boolean sel) {
        t.setBackground(rounded(sel ? ACC : chipBg, 10));
        t.setTextColor(sel ? ON_ACC : txt);
    }

    private LinearLayout chips(String[] labels, int sel, final Pick cb) {
        final LinearLayout row = hbox();
        final int n = labels.length;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(12);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(4), dp(9), dp(4), dp(9));
            styleChip(t, i == sel);
            t.setOnClickListener(v -> {
                for (int j = 0; j < n; j++) styleChip((TextView) row.getChildAt(j), j == idx);
                cb.on(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(t, lp);
        }
        return row;
    }

    private void addChips(LinearLayout parent, String[] labels, int sel, Pick cb) {
        parent.addView(chips(labels, sel, cb), new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private LinearLayout section(LinearLayout box, String title) {
        LinearLayout c = vbox();
        c.setBackground(rounded(card, 12));
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.addView(tv(title, 14, ACC, true));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.setMargins(0, 0, 0, dp(10));
        box.addView(c, p);
        return c;
    }

    private TextView note(LinearLayout parent, String text) {
        TextView t = tv(text, 12, sub, false);
        t.setPadding(0, dp(6), 0, 0);
        parent.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        return t;
    }

    private ListView newList() {
        ListView lv = new ListView(this);
        lv.setBackgroundColor(bg);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setCacheColorHint(Color.TRANSPARENT);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        return lv;
    }

    /** Text box: typed text is black on white in both themes. */
    private EditText input(String hint, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.BLACK);
        e.setHintTextColor(0xFF777777);
        e.setTextSize(15);
        e.setBackground(rounded(Color.WHITE, 8));
        e.setPadding(dp(10), dp(9), dp(10), dp(9));
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(2);
            e.setMaxLines(4);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    private void runUi(Runnable r) {
        if (!isFinishing()) runOnUiThread(r);
    }

    private void runBg(Runnable r) {
        try {
            pool.execute(r);
        } catch (Exception ignore) {
            // the screen is closing
        }
    }

    // ------------------------------------------------------------------ permissions and service

    @TargetApi(33)
    private boolean notifPermMissing() {
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private void askPerms() {
        ArrayList<String> need = new ArrayList<String>();
        if (!LocClient.hasPerm(this)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && Store.bg(this) && notifPermMissing()) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (need.isEmpty()) {
            afterPerms();
        } else {
            requestPermissions(need.toArray(new String[0]), RC_PERM);
        }
    }

    private void afterPerms() {
        locClient.start(2000, Looper.getMainLooper());
        startTrackService();
        updateHeader();
        requestNamer(true);
        reloadTab();
    }

    private void startTrackService() {
        if (!Store.bg(this) || !LocClient.hasPerm(this)) return;
        try {
            Intent i = new Intent(this, TrackService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startFg(i);
            } else {
                startService(i);
            }
        } catch (Exception ignore) {
            // Android did not allow the service to start now
        }
    }

    @TargetApi(26)
    private void startFg(Intent i) {
        startForegroundService(i);
    }

    private void stopTrackService() {
        try {
            stopService(new Intent(this, TrackService.class));
        } catch (Exception ignore) {
            // not running
        }
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < 29) {
            toast(tr("Not needed on this Android version.", "اس اینڈرائیڈ ورژن پر ضرورت نہیں۔", "غير مطلوب في هذا الإصدار."));
            return;
        }
        if (!LocClient.hasPerm(this)) {
            toast(tr("Allow location first.", "پہلے لوکیشن کی اجازت دیں۔", "اسمح بالموقع أولًا."));
            askPerms();
            return;
        }
        reqBg29();
    }

    @TargetApi(29)
    private void reqBg29() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, RC_BG);
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(i);
        } catch (Exception e) {
            toast(tr("Could not open settings.", "سیٹنگز نہیں کھل سکیں۔", "تعذّر فتح الإعدادات."));
        }
    }

    // ------------------------------------------------------------------ location callbacks, place name, compass

    /** New position (main thread). */
    @Override
    public void onFix() {
        updateHeader();
        requestNamer(false);
        updateDeclination();
        long now = System.currentTimeMillis();
        if (cur == 0 && nearAd != null && Geo.km(LocState.lat, LocState.lon, nearLat, nearLon) > 0.1) {
            loadNearby();
        } else if (cur == 1 && savedAd != null && now - lastListUi > 3000) {
            lastListUi = now;
            savedAd.notifyDataSetChanged();
        } else if (cur == 0 && nearAd != null && now - lastListUi > 3000) {
            lastListUi = now;
            nearAd.notifyDataSetChanged();
        }
    }

    private void requestNamer(boolean force) {
        if (!LocState.has || namerBusy) return;
        final double lat = LocState.lat;
        final double lon = LocState.lon;
        long now = System.currentTimeMillis();
        if (!force && Geo.km(lat, lon, namerLat, namerLon) < 0.05 && now - namerTime < 60000L) return;
        namerBusy = true;
        namerLat = lat;
        namerLon = lon;
        namerTime = now;
        runBg(() -> {
            Namer.Info i = null;
            try {
                i = Where.describe(MainActivity.this, lat, lon);
            } catch (Exception ignore) {
                // keep the old name
            }
            final Namer.Info fi = i;
            runUi(() -> {
                namerBusy = false;
                if (fi != null) info = fi;
                updateHeader();
            });
        });
    }

    private void updateHeader() {
        if (placeTv == null) return;
        if (!LocState.has) {
            placeTv.setText(tr("Waiting for location\u2026", "مقام کا انتظار\u2026", "بانتظار الموقع\u2026"));
            if (!LocClient.hasPerm(this)) {
                coordTv.setText(tr("Location permission is needed. Tap here.",
                        "لوکیشن کی اجازت چاہیے۔ یہاں دبائیں۔", "يلزم إذن الموقع. اضغط هنا."));
            } else {
                coordTv.setText(tr("Turn on location (GPS) on your phone.",
                        "اپنے فون میں لوکیشن (GPS) آن کریں۔", "فعّل الموقع (GPS) في هاتفك."));
            }
            cityTv.setVisibility(View.GONE);
            return;
        }
        placeTv.setText(info == null ? "\u2026" : Where.title(this, info));
        StringBuilder s = new StringBuilder(Geo.fmtCoord(LocState.lat, LocState.lon));
        String m = tr("m", "میٹر", "م");
        if (!LocState.stale && LocState.acc >= 0) s.append("  \u00B1").append(Math.round(LocState.acc)).append(" ").append(m);
        if (!Double.isNaN(LocState.alt)) s.append("  \u25B2").append(Math.round(LocState.alt)).append(" ").append(m);
        if (LocState.stale) s.append("  \u00B7 ").append(tr("last known", "آخری معلوم", "آخر موقع معروف"));
        coordTv.setText(s.toString());
        String c = info == null ? "" : Where.cityLine(this, info);
        if (c.length() > 0) {
            cityTv.setText(c);
            cityTv.setVisibility(View.VISIBLE);
        } else {
            cityTv.setVisibility(View.GONE);
        }
    }

    private void updateDeclination() {
        if (!LocState.has) return;
        try {
            float alt = Double.isNaN(LocState.alt) ? 0f : (float) LocState.alt;
            declination = new GeomagneticField((float) LocState.lat, (float) LocState.lon, alt,
                    System.currentTimeMillis()).getDeclination();
        } catch (Exception ignore) {
            declination = 0;
        }
    }

    private void registerSensors() {
        if (sm == null) return;
        gravity = null;
        geo = null;
        smInit = false;
        if (rotSensor != null) {
            sm.registerListener(sensorListener, rotSensor, SensorManager.SENSOR_DELAY_UI);
        } else if (accSensor != null && magSensor != null) {
            sm.registerListener(sensorListener, accSensor, SensorManager.SENSOR_DELAY_UI);
            sm.registerListener(sensorListener, magSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            float[] r = new float[9];
            int type = e.sensor.getType();
            if (type == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(r, e.values);
            } else if (type == Sensor.TYPE_ACCELEROMETER) {
                gravity = lowPass(gravity, e.values);
                if (geo == null) return;
                if (!SensorManager.getRotationMatrix(r, null, gravity, geo)) return;
            } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
                geo = lowPass(geo, e.values);
                if (gravity == null) return;
                if (!SensorManager.getRotationMatrix(r, null, gravity, geo)) return;
            } else {
                return;
            }
            // flat phone: the top edge points to the heading; upright phone: the back points to it
            double az;
            if (Math.abs(r[8]) > 0.7) az = Math.atan2(r[1], r[4]);
            else az = Math.atan2(-r[2], -r[5]);
            double deg = (Math.toDegrees(az) + declination + 360.0) % 360.0;
            double rad = Math.toRadians(deg);
            if (!smInit) {
                sinSm = (float) Math.sin(rad);
                cosSm = (float) Math.cos(rad);
                smInit = true;
            } else {
                sinSm += ((float) Math.sin(rad) - sinSm) * 0.25f;
                cosSm += ((float) Math.cos(rad) - cosSm) * 0.25f;
            }
            long now = System.currentTimeMillis();
            if (now - lastSensorUi < 60) return;
            lastSensorUi = now;
            float h = (float) ((Math.toDegrees(Math.atan2(sinSm, cosSm)) + 360.0) % 360.0);
            if (compass != null) compass.setHeading(h);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private float[] lowPass(float[] old, float[] cur) {
        if (old == null) return cur.clone();
        for (int i = 0; i < 3 && i < cur.length; i++) old[i] += 0.2f * (cur[i] - old[i]);
        return old;
    }

    /** Small compass: the dial turns so that N points to true north; the pointer on top is where the phone faces. */
    private class CompassView extends View {
        private float heading = 0;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tri = new Path();

        CompassView() {
            super(MainActivity.this);
        }

        void setHeading(float h) {
            heading = h;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f;
            float cy = dp(31);
            float rad = dp(27);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(card);
            cv.drawCircle(cx, cy, rad, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f > 1 ? 2 : 1));
            paint.setColor(sub);
            cv.drawCircle(cx, cy, rad, paint);
            cv.save();
            cv.rotate(-heading, cx, cy);
            paint.setStrokeWidth(dp(1));
            for (int i = 0; i < 12; i++) {
                cv.save();
                cv.rotate(i * 30, cx, cy);
                float len = (i % 3 == 0) ? dp(5) : dp(3);
                cv.drawLine(cx, cy - rad, cx, cy - rad + len, paint);
                cv.restore();
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(11));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            String[] letters = {"N", "E", "S", "W"};
            for (int i = 0; i < 4; i++) {
                cv.save();
                cv.rotate(i * 90, cx, cy);
                paint.setColor(i == 0 ? 0xFFE53935 : txt);
                cv.drawText(letters[i], cx, cy - rad + dp(16), paint);
                cv.restore();
            }
            cv.restore();
            tri.reset();
            tri.moveTo(cx - dp(5), cy - rad - dp(3));
            tri.lineTo(cx + dp(5), cy - rad - dp(3));
            tri.lineTo(cx, cy - rad + dp(5));
            tri.close();
            paint.setColor(ACC);
            cv.drawPath(tri, paint);
            paint.setColor(txt);
            paint.setTextSize(dp(11));
            int sec = Geo.sector(heading);
            String[] d = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            cv.drawText(Math.round(heading) % 360 + "\u00B0 " + d[sec], cx, cy + rad + dp(15), paint);
        }
    }

    // ------------------------------------------------------------------ main layout

    private void build() {
        applyTheme();
        boolean rtl = L.rtl(this);
        root = vbox();
        root.setBackgroundColor(bg);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        // top: current place name + small compass
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(8), dp(10), dp(4));
        head.setBackgroundColor(card);
        LinearLayout hv = vbox();
        placeTv = tv("", 17, txt, true);
        coordTv = tv("", 12, sub, false);
        cityTv = tv("", 12, ACC, false);
        hv.addView(placeTv);
        hv.addView(coordTv);
        hv.addView(cityTv);
        hv.setOnClickListener(v -> {
            if (!LocClient.hasPerm(this)) askPerms();
        });
        head.addView(hv, new LinearLayout.LayoutParams(0, WRAP, 1f));
        compass = new CompassView();
        head.addView(compass, new LinearLayout.LayoutParams(dp(72), dp(78)));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView searchMain = button(tr("Search places  \uD83D\uDD0D", "مقامات تلاش کریں  \uD83D\uDD0D", "بحث عن الأماكن  \uD83D\uDD0D"), ACC, ON_ACC, v -> showPlaceSearch());
        LinearLayout.LayoutParams searchMainLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        searchMainLp.setMargins(dp(12), dp(7), dp(12), dp(7));
        root.addView(searchMain, searchMainLp);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bar = hbox();
        bar.setBackgroundColor(card);
        String[] names = {
                tr("Nearby", "قریبی", "القريبة"),
                tr("Saved", "محفوظ", "المحفوظة"),
                tr("Offline", "آف لائن", "دون اتصال"),
                tr("Settings", "سیٹنگز", "الإعدادات")};
        String[] icons = {"\uD83D\uDCCD", "\u2B50", "\uD83D\uDCE5", "\u2699\uFE0F"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = tv(icons[i] + "\n" + names[i], 12, sub, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(7), 0, dp(7));
            t.setOnClickListener(v -> showTab(idx));
            tabViews[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        root.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView by = tv("By: Black Falcon \uD83E\uDD85", 12, sub, false);
        by.setGravity(Gravity.CENTER);
        by.setBackgroundColor(card);
        by.setPadding(0, dp(4), 0, dp(8));
        root.addView(by, new LinearLayout.LayoutParams(MATCH, WRAP));

        setContentView(root);
        updateHeader();
        showTab(cur);
    }

    private void showTab(int i) {
        cur = i;
        for (int k = 0; k < 4; k++) {
            tabViews[k].setTextColor(k == i ? ACC : sub);
            tabViews[k].setTypeface(null, k == i ? Typeface.BOLD : Typeface.NORMAL);
        }
        content.removeAllViews();
        View v;
        if (i == 0) v = buildNearby();
        else if (i == 1) v = buildSaved();
        else if (i == 2) v = buildOffline();
        else v = buildSettings();
        content.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        reloadTab();
    }

    private void reloadTab() {
        if (content == null) return;
        if (cur == 0) loadNearby();
        else if (cur == 1) loadSaved();
        else if (cur == 2) refreshPacks();
    }

    // ------------------------------------------------------------------ list rows

    private String labelOf(Place p) {
        return p.label(Store.lang(this));
    }

    private void openMap(double lat, double lon, String label) {
        try {
            String pos = String.format(Locale.US, "%.6f,%.6f", lat, lon);
            String uri = "geo:" + pos + "?q=" + pos + "(" + Uri.encode(label) + ")";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (Exception e) {
            toast(tr("No map app found.", "کوئی نقشہ ایپ نہیں ملی۔", "لا يوجد تطبيق خرائط."));
        }
    }

    private class RowAdapter extends BaseAdapter {
        private List<Row> items = new ArrayList<Row>();

        void set(List<Row> l) {
            items = l;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int i) {
            return items.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Holder h;
            if (cv == null) {
                h = new Holder();
                LinearLayout outer = new LinearLayout(MainActivity.this);
                outer.setLayoutParams(new AbsListView.LayoutParams(MATCH, WRAP));
                outer.setPadding(dp(12), dp(4), dp(12), dp(4));
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(9), dp(4), dp(9));
                row.setBackground(rounded(card, 12));
                outer.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
                h.icon = tv("", 24, txt, false);
                h.icon.setGravity(Gravity.CENTER);
                row.addView(h.icon, new LinearLayout.LayoutParams(dp(40), WRAP));
                LinearLayout col = vbox();
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                cp.setMarginStart(dp(6));
                row.addView(col, cp);
                h.title = tv("", 15, txt, true);
                h.sub = tv("", 12, sub, false);
                h.dist = tv("", 12, ACC, false);
                col.addView(h.title);
                col.addView(h.sub);
                col.addView(h.dist);
                h.act = tv("", 22, ACC, true);
                h.act.setGravity(Gravity.CENTER);
                h.act.setPadding(dp(12), dp(8), dp(12), dp(8));
                h.act.setOnClickListener(v -> {
                    Object t = v.getTag();
                    if (t instanceof Row) onRowAction((Row) t);
                });
                row.addView(h.act, new LinearLayout.LayoutParams(WRAP, WRAP));
                outer.setTag(h);
                cv = outer;
            } else {
                h = (Holder) cv.getTag();
            }
            Row r = items.get(pos);
            h.icon.setText(r.icon);
            h.title.setText(r.title);
            h.sub.setText(r.sub);
            h.sub.setVisibility(r.sub.length() > 0 ? View.VISIBLE : View.GONE);
            if (LocState.has) {
                double d = Geo.km(LocState.lat, LocState.lon, r.lat, r.lon);
                int s = Geo.sector(Geo.bearing(LocState.lat, LocState.lon, r.lat, r.lon));
                h.dist.setText(L.dist(MainActivity.this, d) + "  \u00B7  " + L.dir(MainActivity.this, s));
                h.dist.setVisibility(View.VISIBLE);
            } else {
                h.dist.setVisibility(View.GONE);
            }
            h.act.setText(r.poi != null ? "\u22EE" : "+");
            h.act.setTag(r);
            return cv;
        }
    }

    private void onRowAction(Row r) {
        if (r.poi != null) showPoiOptions(r.poi);
        else if (r.place != null) savePlaceAsPoi(r.place);
    }

    private void savePlaceAsPoi(Place p) {
        Poi poi = new Poi(labelOf(p), p.lat, p.lon);
        poi.cat = "service".equals(p.kind) && Cat.isPoiCode(p.sub) ? p.sub : "general";
        poi.time = System.currentTimeMillis();
        Db db = Db.get(this);
        if (db.poiExists(poi.name, poi.lat, poi.lon)) {
            toast(tr("Already saved.", "پہلے سے محفوظ ہے۔", "محفوظ مسبقًا."));
            return;
        }
        db.addPoi(poi);
        toast(tr("Saved \u2713", "محفوظ ہو گیا \u2713", "تم الحفظ \u2713"));
    }

    // ------------------------------------------------------------------ Nearby tab

    private View buildNearby() {
        ListView lv = newList();
        LinearLayout h = vbox();
        h.setPadding(dp(12), dp(6), dp(12), dp(8));

        final String[] fv = {"all", "settlement", "service"};
        addChips(h, new String[]{tr("All", "سب", "الكل"), tr("Cities & villages", "شہر اور گاؤں", "مدن وقرى"),
                tr("Services", "سہولیات", "خدمات")}, indexOf(fv, nearFilter), k -> {
            nearFilter = fv[k];
            loadNearby();
        });

        final int[] rv = {5, 25, 100, 500};
        String km = tr("km", "کلومیٹر", "كم");
        String[] rl = new String[rv.length];
        for (int i = 0; i < rv.length; i++) rl[i] = rv[i] + " " + km;
        addChips(h, rl, indexOfInt(rv, nearRadius), k -> {
            nearRadius = rv[k];
            loadNearby();
        });

        TextView upd = button(tr("Update nearby places (online)", "قریبی مقامات اپ ڈیٹ کریں (آن لائن)",
                "تحديث الأماكن القريبة (عبر الإنترنت)"), ACC, ON_ACC, v -> startDownload("both", 5));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(MATCH, WRAP);
        up.setMargins(dp(3), dp(6), dp(3), 0);
        h.addView(upd, up);

        nearInfo = tv("", 13, sub, false);
        nearInfo.setPadding(dp(4), dp(8), dp(4), dp(2));
        h.addView(nearInfo, new LinearLayout.LayoutParams(MATCH, WRAP));

        lv.addHeaderView(h, null, false);
        nearAd = new RowAdapter();
        lv.setAdapter(nearAd);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = parent.getItemAtPosition(pos);
            if (o instanceof Row) openMap(((Row) o).lat, ((Row) o).lon, ((Row) o).title);
        });
        return lv;
    }

    private void loadNearby() {
        if (nearAd == null || cur != 0) return;
        if (!LocState.has) {
            nearAd.set(new ArrayList<Row>());
            nearInfo.setText(tr("Waiting for your position\u2026", "آپ کے مقام کا انتظار\u2026", "بانتظار موقعك\u2026"));
            return;
        }
        final double lat = LocState.lat;
        final double lon = LocState.lon;
        final int rad = nearRadius;
        final String filter = nearFilter;
        final int req = ++nearReq;
        nearLat = lat;
        nearLon = lon;
        runBg(() -> {
            List<Place> cand = new ArrayList<Place>();
            if (!"service".equals(filter)) {
                for (Place p : Cities.all()) {
                    if (Geo.km(lat, lon, p.lat, p.lon) <= rad) cand.add(p);
                }
            }
            cand.addAll(Db.get(MainActivity.this).placesIn(lat, lon, rad, "all".equals(filter) ? null : filter, null, 3000));
            final ArrayList<Row> out = new ArrayList<Row>();
            for (Place p : cand) {
                double d = Geo.km(lat, lon, p.lat, p.lon);
                if (d > rad) continue;
                Row r = new Row();
                r.place = p;
                r.lat = p.lat;
                r.lon = p.lon;
                r.dist = d;
                r.icon = Cat.emoji(p.sub);
                r.title = labelOf(p);
                r.sub = L.cat(MainActivity.this, p.sub) + (p.country.length() > 0 ? ", " + p.country : "");
                out.add(r);
            }
            Collections.sort(out, (a, b) -> Double.compare(a.dist, b.dist));
            // drop duplicates (the same place from the built-in list and a download)
            Set<String> seen = new HashSet<String>();
            final ArrayList<Row> shown = new ArrayList<Row>();
            for (Row r : out) {
                String key = r.title.toLowerCase(Locale.US) + "|" + Math.round(r.lat * 50) + "|" + Math.round(r.lon * 50);
                if (!seen.add(key)) continue;
                shown.add(r);
                if (shown.size() >= 300) break;
            }
            runUi(() -> {
                if (req != nearReq || nearAd == null) return;
                nearAd.set(shown);
                if (shown.isEmpty()) {
                    nearInfo.setText(tr("No places found here. Download your area in the Offline tab, or press Update (online).",
                            "یہاں کوئی مقام نہیں ملا۔ آف لائن ٹیب میں اپنا علاقہ ڈاؤن لوڈ کریں، یا اپ ڈیٹ (آن لائن) دبائیں۔",
                            "لم يتم العثور على أماكن هنا. نزّل منطقتك من تبويب دون اتصال، أو اضغط تحديث (عبر الإنترنت)."));
                } else {
                    nearInfo.setText(String.format(Locale.US,
                            tr("%1$d places within %2$s \u00B7 tap one to open it in a maps app",
                                    "%2$s کے اندر %1$d مقامات \u00B7 نقشے میں کھولنے کے لیے دبائیں",
                                    "%1$d مكانًا ضمن %2$s \u00B7 اضغط لفتحه في الخرائط"),
                            shown.size(), L.dist(MainActivity.this, rad)));
                }
            });
        });
    }

    // ------------------------------------------------------------------ Saved tab

    private View buildSaved() {
        ListView lv = newList();
        LinearLayout h = vbox();
        h.setPadding(dp(12), dp(6), dp(12), dp(8));

        LinearLayout r1 = hbox();
        addBtn(r1, tr("Save here", "یہاں محفوظ کریں", "حفظ هنا"), ACC, ON_ACC, v -> saveHere());
        addBtn(r1, tr("Add coordinates", "کوآرڈینیٹ سے شامل کریں", "إضافة بالإحداثيات"), chipBg, txt,
                v -> showPoiDialog(null, 0, 0, "", "general", true));
        h.addView(r1, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout r2 = hbox();
        addBtn(r2, tr("Import", "امپورٹ", "استيراد"), chipBg, txt, v -> startImport());
        addBtn(r2, tr("Export", "ایکسپورٹ", "تصدير"), chipBg, txt, v -> chooseExportFormat());
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(MATCH, WRAP);
        r2p.setMargins(0, dp(6), 0, dp(4));
        h.addView(r2, r2p);

        final String[] sv = {"near", "new", "az"};
        addChips(h, new String[]{tr("Nearest", "قریب ترین", "الأقرب"), tr("Newest", "تازہ ترین", "الأحدث"), "A\u2013Z"},
                indexOf(sv, savedSort), k -> {
            savedSort = sv[k];
            loadSaved();
        });

        savedInfo = tv("", 13, sub, false);
        savedInfo.setPadding(dp(4), dp(8), dp(4), dp(2));
        h.addView(savedInfo, new LinearLayout.LayoutParams(MATCH, WRAP));

        lv.addHeaderView(h, null, false);
        savedAd = new RowAdapter();
        lv.setAdapter(savedAd);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = parent.getItemAtPosition(pos);
            if (o instanceof Row) openMap(((Row) o).lat, ((Row) o).lon, ((Row) o).title);
        });
        lv.setOnItemLongClickListener((parent, view, pos, id) -> {
            Object o = parent.getItemAtPosition(pos);
            if (o instanceof Row && ((Row) o).poi != null) {
                showPoiOptions(((Row) o).poi);
                return true;
            }
            return false;
        });
        return lv;
    }

    private void loadSaved() {
        if (savedAd == null || cur != 1) return;
        final int req = ++savedReq;
        final String sort = savedSort;
        runBg(() -> {
            List<Poi> list = Db.get(MainActivity.this).pois();
            final ArrayList<Row> rows = new ArrayList<Row>();
            for (Poi p : list) {
                Row r = new Row();
                r.poi = p;
                r.lat = p.lat;
                r.lon = p.lon;
                r.icon = Cat.emoji(p.cat);
                r.title = p.name;
                String note = p.note == null ? "" : p.note.trim();
                int nl = note.indexOf('\n');
                if (nl >= 0) note = note.substring(0, nl);
                r.sub = L.cat(MainActivity.this, p.cat) + (note.length() > 0 ? "  \u00B7  " + note : "");
                r.dist = LocState.has ? Geo.km(LocState.lat, LocState.lon, p.lat, p.lon) : 0;
                rows.add(r);
            }
            if ("near".equals(sort) && LocState.has) {
                Collections.sort(rows, (a, b) -> Double.compare(a.dist, b.dist));
            } else if ("az".equals(sort)) {
                Collections.sort(rows, (a, b) -> a.title.compareToIgnoreCase(b.title));
            }
            runUi(() -> {
                if (req != savedReq || savedAd == null) return;
                savedAd.set(rows);
                if (rows.isEmpty()) {
                    savedInfo.setText(tr("No saved places yet. Tap \"Save here\" to save your current position.",
                            "ابھی کوئی محفوظ مقام نہیں۔ اپنا موجودہ مقام محفوظ کرنے کے لیے \"یہاں محفوظ کریں\" دبائیں۔",
                            "لا توجد أماكن محفوظة بعد. اضغط «حفظ هنا» لحفظ موقعك الحالي."));
                } else {
                    savedInfo.setText(String.format(Locale.US,
                            tr("%d saved places \u00B7 tap one to open it in a maps app, tap \u22EE for more",
                                    "%d محفوظ مقامات \u00B7 نقشے میں کھولنے کے لیے دبائیں، مزید کے لیے \u22EE",
                                    "%d مكانًا محفوظًا \u00B7 اضغط لفتحه في الخرائط، و\u22EE للمزيد"),
                            rows.size()));
                }
            });
        });
    }

    private void saveHere() {
        if (!LocState.has) {
            toast(tr("Waiting for your position\u2026", "آپ کے مقام کا انتظار\u2026", "بانتظار موقعك\u2026"));
            return;
        }
        String name = info == null || info.place == null ? "" : Where.title(this, info);
        showPoiDialog(null, LocState.lat, LocState.lon, name, "general", false);
    }

    // ------------------------------------------------------------------ POI dialogs

    private Dialog makeDialog(View contentView) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        contentView.setLayoutDirection(L.rtl(this) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        d.setContentView(contentView);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    private void showWide(Dialog d) {
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f), WRAP);
        }
    }

    private void confirm(String title, String msg, String yes, final Runnable onYes) {
        LinearLayout box = vbox();
        box.setBackground(rounded(card, 16));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        box.addView(tv(title, 18, txt, true));
        TextView m = tv(msg, 14, sub, false);
        m.setPadding(0, dp(8), 0, dp(14));
        box.addView(m);
        LinearLayout row = hbox();
        final Dialog d = makeDialog(box);
        addBtn(row, tr("Cancel", "منسوخ", "إلغاء"), chipBg, txt, v -> d.dismiss());
        addBtn(row, yes, ACC, ON_ACC, v -> {
            d.dismiss();
            onYes.run();
        });
        box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        showWide(d);
    }

    private void showPoiDialog(final Poi existing, final double lat, final double lon, final String suggestion,
                               final String presetCat, final boolean askCoords) {
        final boolean editing = existing != null && existing.id != 0;
        ScrollView sv = new ScrollView(this);
        sv.setBackground(rounded(card, 16));
        LinearLayout box = vbox();
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        sv.addView(box);
        box.addView(tv(editing ? tr("Edit place", "مقام میں ترمیم", "تعديل المكان")
                : tr("Save place", "مقام محفوظ کریں", "حفظ المكان"), 18, txt, true));

        TextView l1 = tv(tr("Name", "نام", "الاسم"), 12, sub, false);
        l1.setPadding(0, dp(10), 0, dp(3));
        box.addView(l1);
        final EditText nameEt = input(tr("Name", "نام", "الاسم"), false);
        nameEt.setText(editing ? existing.name : suggestion);
        box.addView(nameEt, new LinearLayout.LayoutParams(MATCH, WRAP));

        final EditText coordEt;
        if (askCoords) {
            TextView lc = tv(tr("Coordinates", "کوآرڈینیٹ", "الإحداثيات"), 12, sub, false);
            lc.setPadding(0, dp(10), 0, dp(3));
            box.addView(lc);
            coordEt = input("34.0123, 71.9876", false);
            box.addView(coordEt, new LinearLayout.LayoutParams(MATCH, WRAP));
        } else {
            coordEt = null;
            double sl = editing ? existing.lat : lat;
            double so = editing ? existing.lon : lon;
            TextView lc = tv(Geo.fmtCoord(sl, so), 12, sub, false);
            lc.setPadding(0, dp(6), 0, 0);
            box.addView(lc);
        }

        TextView l2 = tv(tr("Category", "زمرہ", "الفئة"), 12, sub, false);
        l2.setPadding(0, dp(10), 0, dp(3));
        box.addView(l2);
        final String[] sel = {editing ? existing.cat : presetCat};
        final LinearLayout catRow = hbox();
        for (int i = 0; i < Cat.POI_CODES.length; i++) {
            final String code = Cat.POI_CODES[i];
            TextView t = new TextView(this);
            t.setText(Cat.emoji(code) + " " + L.cat(this, code));
            t.setTextSize(13);
            t.setPadding(dp(10), dp(8), dp(10), dp(8));
            styleChip(t, code.equals(sel[0]));
            t.setOnClickListener(v -> {
                sel[0] = code;
                for (int j = 0; j < catRow.getChildCount(); j++) {
                    styleChip((TextView) catRow.getChildAt(j), Cat.POI_CODES[j].equals(code));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.setMargins(dp(2), 0, dp(2), 0);
            catRow.addView(t, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(catRow);
        box.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView l3 = tv(tr("Note", "نوٹ", "ملاحظة"), 12, sub, false);
        l3.setPadding(0, dp(10), 0, dp(3));
        box.addView(l3);
        final EditText noteEt = input(tr("Note (optional)", "نوٹ (اختیاری)", "ملاحظة (اختياري)"), true);
        if (editing) noteEt.setText(existing.note);
        box.addView(noteEt, new LinearLayout.LayoutParams(MATCH, WRAP));

        final Dialog d = makeDialog(sv);
        LinearLayout row = hbox();
        addBtn(row, tr("Cancel", "منسوخ", "إلغاء"), chipBg, txt, v -> d.dismiss());
        addBtn(row, tr("Save", "محفوظ کریں", "حفظ"), ACC, ON_ACC, v -> {
            double pl = editing ? existing.lat : lat;
            double po = editing ? existing.lon : lon;
            if (coordEt != null) {
                double[] c = Geo.parseCoords(coordEt.getText().toString());
                if (c == null) {
                    toast(tr("Invalid coordinates. Example: 34.0123, 71.9876",
                            "غلط کوآرڈینیٹ۔ مثال: 34.0123, 71.9876",
                            "إحداثيات غير صالحة. مثال: 34.0123, 71.9876"));
                    return;
                }
                pl = c[0];
                po = c[1];
            }
            String name = nameEt.getText().toString().trim();
            if (name.length() == 0) name = suggestion.length() > 0 ? suggestion : tr("Saved place", "محفوظ مقام", "مكان محفوظ");
            Poi p = editing ? existing : new Poi();
            p.name = name;
            p.lat = pl;
            p.lon = po;
            p.cat = sel[0];
            p.note = noteEt.getText().toString().trim();
            Db db = Db.get(MainActivity.this);
            if (editing) {
                db.updatePoi(p);
            } else {
                p.alt = (coordEt != null || !LocState.has) ? Double.NaN : LocState.alt;
                p.time = System.currentTimeMillis();
                db.addPoi(p);
            }
            d.dismiss();
            toast(tr("Saved \u2713", "محفوظ ہو گیا \u2713", "تم الحفظ \u2713"));
            loadSaved();
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rp.setMargins(0, dp(14), 0, 0);
        box.addView(row, rp);
        showWide(d);
        Window w = d.getWindow();
        if (w != null) w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showPoiOptions(final Poi p) {
        LinearLayout box = vbox();
        box.setBackground(rounded(card, 16));
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.addView(tv(Cat.emoji(p.cat) + " " + p.name, 17, txt, true));
        box.addView(tv(Geo.fmtCoord(p.lat, p.lon), 12, sub, false));
        final Dialog d = makeDialog(box);
        addWide(box, tr("Open in maps", "نقشے میں کھولیں", "فتح في الخرائط"), ACC, ON_ACC, v -> {
            d.dismiss();
            openMap(p.lat, p.lon, p.name);
        });
        addWide(box, tr("Edit", "ترمیم", "تعديل"), chipBg, txt, v -> {
            d.dismiss();
            showPoiDialog(p, p.lat, p.lon, p.name, p.cat, false);
        });
        addWide(box, tr("Copy coordinates", "کوآرڈینیٹ کاپی کریں", "نسخ الإحداثيات"), chipBg, txt, v -> {
            d.dismiss();
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("coordinates", Geo.fmtPlain(p.lat, p.lon)));
                toast(tr("Copied.", "کاپی ہو گیا۔", "تم النسخ."));
            } catch (Exception e) {
                toast(tr("Could not copy.", "کاپی نہیں ہو سکا۔", "تعذّر النسخ."));
            }
        });
        addWide(box, tr("Share", "شیئر", "مشاركة"), chipBg, txt, v -> {
            d.dismiss();
            try {
                String text = p.name + "\n" + Geo.fmtPlain(p.lat, p.lon) + "\nhttps://www.google.com/maps?q="
                        + String.format(Locale.US, "%.6f,%.6f", p.lat, p.lon);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, tr("Share", "شیئر", "مشاركة")));
            } catch (Exception e) {
                toast(tr("Could not share.", "شیئر نہیں ہو سکا۔", "تعذّرت المشاركة."));
            }
        });
        addWide(box, tr("Delete", "حذف کریں", "حذف"), 0xFFB71C1C, Color.WHITE, v -> {
            d.dismiss();
            confirm(tr("Delete place?", "مقام حذف کریں؟", "حذف المكان؟"), p.name,
                    tr("Delete", "حذف کریں", "حذف"), () -> {
                        Db.get(MainActivity.this).deletePoi(p.id);
                        loadSaved();
                    });
        });
        showWide(d);
    }

    // ------------------------------------------------------------------ import and export

    private void chooseExportFormat() {
        LinearLayout box = vbox();
        box.setBackground(rounded(card, 16));
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.addView(tv(tr("Export format", "ایکسپورٹ فارمیٹ", "صيغة التصدير"), 18, txt, true));
        final Dialog d = makeDialog(box);
        String[] desc = {
                tr("GPX \u2013 waypoints for GPS apps (OsmAnd, Garmin, Locus\u2026)",
                        "GPX \u2013 GPS ایپس کے لیے (OsmAnd، Garmin، Locus\u2026)",
                        "GPX \u2013 لتطبيقات GPS (OsmAnd وGarmin وLocus\u2026)"),
                tr("KML \u2013 Google Earth / Google Maps", "KML \u2013 گوگل ارتھ / گوگل میپس", "KML \u2013 Google Earth / خرائط Google"),
                tr("GeoJSON \u2013 GIS tools and web maps", "GeoJSON \u2013 GIS ٹولز اور ویب نقشے", "GeoJSON \u2013 أدوات GIS وخرائط الويب"),
                tr("CSV \u2013 spreadsheet (Excel, Sheets)", "CSV \u2013 اسپریڈشیٹ (Excel، Sheets)", "CSV \u2013 جداول بيانات (Excel وSheets)")};
        for (int i = 0; i < Formats.IDS.length; i++) {
            final String id = Formats.IDS[i];
            addWide(box, desc[i], i == 0 ? ACC : chipBg, i == 0 ? ON_ACC : txt, v -> {
                d.dismiss();
                doExport(id);
            });
        }
        addWide(box, tr("Cancel", "منسوخ", "إلغاء"), chipBg, sub, v -> d.dismiss());
        showWide(d);
    }

    private void doExport(final String id) {
        runBg(() -> {
            List<Poi> list = Db.get(MainActivity.this).pois();
            if (list.isEmpty()) {
                runUi(() -> toast(tr("There are no saved places to export.", "ایکسپورٹ کرنے کے لیے کوئی محفوظ مقام نہیں۔",
                        "لا توجد أماكن محفوظة للتصدير.")));
                return;
            }
            final long now = System.currentTimeMillis();
            final String text = Formats.export(id, list, now);
            runUi(() -> {
                pendingExport = text;
                try {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(Formats.mime(id));
                    i.putExtra(Intent.EXTRA_TITLE, Formats.fileName(id, now));
                    startActivityForResult(i, RC_EXPORT);
                } catch (Exception e) {
                    pendingExport = null;
                    toast(tr("No file manager found to save the file.", "فائل محفوظ کرنے کے لیے کوئی فائل مینیجر نہیں ملا۔",
                            "لا يوجد مدير ملفات لحفظ الملف."));
                }
            });
        });
    }

    private void startImport() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, RC_IMPORT);
            toast(tr("Choose a GPX, KML, GeoJSON or CSV file. The format is detected automatically.",
                    "GPX، KML، GeoJSON یا CSV فائل چنیں۔ فارمیٹ خود پہچانا جاتا ہے۔",
                    "اختر ملف GPX أو KML أو GeoJSON أو CSV. يتم اكتشاف الصيغة تلقائيًا."));
        } catch (Exception e) {
            toast(tr("No file manager found.", "کوئی فائل مینیجر نہیں ملا۔", "لا يوجد مدير ملفات."));
        }
    }

    private byte[] readAll(InputStream in, int max) throws IOException {
        if (in == null) throw new IOException("cannot open file");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("file is too large");
            bo.write(buf, 0, n);
        }
        in.close();
        return bo.toByteArray();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_EXPORT && resultCode != RESULT_OK) pendingExport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == RC_EXPORT) {
            final String text = pendingExport;
            pendingExport = null;
            if (text == null) return;
            runBg(() -> {
                try {
                    OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                    if (os == null) throw new IOException("cannot open file");
                    os.write(text.getBytes("UTF-8"));
                    os.close();
                    runUi(() -> toast(tr("File saved.", "فائل محفوظ ہو گئی۔", "تم حفظ الملف.")));
                } catch (Exception e) {
                    final String msg = String.valueOf(e.getMessage());
                    runUi(() -> toast(tr("Could not save the file: ", "فائل محفوظ نہیں ہو سکی: ", "تعذّر حفظ الملف: ") + msg));
                }
            });
        } else if (requestCode == RC_IMPORT) {
            runBg(() -> {
                try {
                    byte[] bytes = readAll(getContentResolver().openInputStream(uri), 30 * 1024 * 1024);
                    Formats.ImportResult r = Formats.parse(new String(bytes, "UTF-8"));
                    int added = Db.get(MainActivity.this).importPois(r.pois);
                    final String msg = String.format(Locale.US, tr(
                            "Imported %1$d points from %2$s. Already saved: %3$d. Not usable: %4$d.",
                            "%2$s سے %1$d مقامات امپورٹ ہوئے۔ پہلے سے محفوظ: %3$d۔ ناقابلِ استعمال: %4$d۔",
                            "تم استيراد %1$d نقطة من %2$s. محفوظة مسبقًا: %3$d. غير صالحة: %4$d."),
                            added, r.format.toUpperCase(Locale.US), r.pois.size() - added, r.skipped);
                    runUi(() -> {
                        toast(msg);
                        loadSaved();
                    });
                } catch (Exception e) {
                    final String m = String.valueOf(e.getMessage());
                    runUi(() -> toast(tr("Could not read this file: ", "یہ فائل نہیں پڑھی جا سکی: ", "تعذّرت قراءة الملف: ") + m));
                }
            });
        }
    }

    // ------------------------------------------------------------------ Main-screen place search (offline database + online OpenStreetMap)

    private void showPlaceSearch() {
        final Dialog dialog = new Dialog(this);
        LinearLayout panel = vbox();
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackgroundColor(card);
        TextView title = tv(tr("Search places", "مقامات تلاش کریں", "البحث عن الأماكن"), 18, ACC, true);
        panel.addView(title);
        final EditText query = input(tr("Enter place name or type", "مقام یا قسم کا نام لکھیں", "اكتب اسم المكان أو نوعه"), false);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        qlp.setMargins(0, dp(10), 0, dp(8));
        panel.addView(query, qlp);
        TextView status = tv(tr("Searches downloaded offline places and OpenStreetMap online.",
                "ڈاؤن لوڈ شدہ آف لائن مقامات اور OpenStreetMap دونوں میں تلاش کرے گا۔",
                "يبحث في الأماكن المحمّلة دون اتصال وفي OpenStreetMap عبر الإنترنت."), 12, sub, false);
        panel.addView(status);
        final ListView results = newList();
        final ArrayList<Place> found = new ArrayList<Place>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        results.setAdapter(adapter);
        results.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < found.size()) {
                Place p = found.get(position);
                dialog.dismiss();
                openMap(p.lat, p.lon, labelOf(p));
            }
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        rlp.setMargins(0, dp(8), 0, dp(8));
        panel.addView(results, rlp);
        TextView go = button(tr("Search both sources", "دونوں جگہ تلاش کریں", "البحث في المصدرين"), ACC, ON_ACC, v -> {
            String term = query.getText().toString().trim();
            if (term.length() < 2) {
                toast(tr("Enter at least 2 characters.", "کم از کم 2 حروف لکھیں۔", "اكتب حرفين على الأقل."));
                return;
            }
            goSearchPlaces(term, status, adapter, found);
        });
        panel.addView(go, new LinearLayout.LayoutParams(MATCH, WRAP));
        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        w = dialog.getWindow();
        if (w != null) w.setLayout(MATCH, dp(520));
    }

    private void goSearchPlaces(final String term, TextView status, ArrayAdapter<String> adapter, ArrayList<Place> found) {
        status.setText(tr("Searching offline and online…", "آف لائن اور آن لائن تلاش جاری ہے…", "جارٍ البحث دون اتصال وعبر الإنترنت…"));
        adapter.clear();
        found.clear();
        runBg(() -> {
            ArrayList<Place> all = new ArrayList<Place>();
            try {
                all.addAll(Db.get(MainActivity.this).searchPlaces(term, 100));
            } catch (Exception ignored) { }
            String needle = term.toLowerCase(Locale.US);
            for (Place city : Cities.all()) {
                String n = (city.name + " " + city.en).toLowerCase(Locale.US);
                if (n.contains(needle)) all.add(city);
            }
            String onlineMessage = "";
            if (Store.online(MainActivity.this) && LocState.has) {
                try {
                    String json = Overpass.fetch(Overpass.searchQuery(term, LocState.lat, LocState.lon, 30000));
                    all.addAll(Overpass.parse(json));
                } catch (Exception e) {
                    onlineMessage = tr(" Online search unavailable: ", " آن لائن تلاش دستیاب نہیں: ", " تعذّر البحث عبر الإنترنت: ") + e.getMessage();
                }
            } else if (!Store.online(MainActivity.this)) {
                onlineMessage = tr(" Online search is disabled in Settings.", " سیٹنگز میں آن لائن تلاش بند ہے۔", " البحث عبر الإنترنت معطّل في الإعدادات.");
            } else {
                onlineMessage = tr(" Location is not available for online search.", " آن لائن تلاش کے لیے مقام دستیاب نہیں۔", " الموقع غير متاح للبحث عبر الإنترنت.");
            }
            final ArrayList<Place> unique = new ArrayList<Place>();
            Set<String> seen = new HashSet<String>();
            for (Place p : all) {
                String key = (p.osm != null && p.osm.length() > 0) ? p.osm :
                        p.name.toLowerCase(Locale.US) + "|" + Math.round(p.lat * 10000) + "|" + Math.round(p.lon * 10000);
                if (seen.add(key)) unique.add(p);
                if (unique.size() >= 150) break;
            }
            runUi(() -> {
                found.clear();
                found.addAll(unique);
                adapter.clear();
                for (Place p : unique) {
                    double d = LocState.has ? Geo.km(LocState.lat, LocState.lon, p.lat, p.lon) : -1;
                    String line = labelOf(p) + "  ·  " + L.cat(MainActivity.this, p.sub);
                    if (d >= 0) line += "  ·  " + L.dist(MainActivity.this, d);
                    adapter.add(line);
                }
                String summary = String.format(Locale.US, tr("Found %d places. Tap a result to open it in maps.",
                        "%d مقامات ملے۔ نقشے میں کھولنے کے لیے نتیجہ دبائیں۔", "عُثر على %d مكانًا. اضغط لفتحه في الخرائط."), unique.size());
                status.setText(summary + onlineMessage);
                adapter.notifyDataSetChanged();
            });
        });
    }

    // ------------------------------------------------------------------ Offline tab

    private View buildOffline() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        LinearLayout box = vbox();
        box.setPadding(dp(12), dp(10), dp(12), dp(16));
        sv.addView(box);

        LinearLayout s1 = section(box, tr("Built-in cities", "بلٹ اِن شہر", "مدن مضمّنة"));
        note(s1, String.format(Locale.US, tr(
                "%d major cities are included (approximate positions). They give a rough place name anywhere, with no download.",
                "%d بڑے شہر شامل ہیں (تقریبی مقام)۔ یہ ڈاؤن لوڈ کے بغیر کہیں بھی موٹا اندازہ دیتے ہیں۔",
                "تتضمن %d مدينة كبرى (مواقع تقريبية). تعطي اسمًا تقريبيًا للمكان في أي مكان دون تنزيل."),
                Cities.all().size()));

        LinearLayout s2 = section(box, tr("Downloaded areas", "ڈاؤن لوڈ شدہ علاقے", "المناطق المنزَّلة"));
        packsBox = vbox();
        s2.addView(packsBox, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout s3 = section(box, tr("Download around your position", "اپنے مقام کے آس پاس ڈاؤن لوڈ کریں",
                "تنزيل ما حول موقعك"));
        final String[] scopeV = {"settlements", "services", "both"};
        addChips(s3, new String[]{tr("Cities & villages", "شہر اور گاؤں", "مدن وقرى"), tr("Services", "سہولیات", "خدمات"),
                tr("Both", "دونوں", "الاثنان")}, indexOf(scopeV, dlScope), k -> dlScope = scopeV[k]);
        final int[] radV = {10, 25, 50, 100, 300};
        String km = tr("km", "کلومیٹر", "كم");
        String[] radL = new String[radV.length];
        for (int i = 0; i < radV.length; i++) radL[i] = radV[i] + " " + km;
        addChips(s3, radL, indexOfInt(radV, dlRadius), k -> dlRadius = radV[k]);
        addWide(s3, tr("Download", "ڈاؤن لوڈ", "تنزيل"), ACC, ON_ACC, v -> startDownload(dlScope, dlRadius));
        dlStatus = note(s3, tr("Needs internet. Data comes from OpenStreetMap. The server receives the center of the area (your approximate position). For large areas, downloads may take longer; maximum radius is 300 km.",
                "انٹرنیٹ ضروری ہے۔ ڈیٹا OpenStreetMap سے آتا ہے۔ سرور کو علاقے کا مرکز (آپ کا تقریبی مقام) ملتا ہے۔ بڑے علاقے ٹائم آؤٹ ہو سکتے ہیں؛ 10\u201325 کلومیٹر آزمائیں۔",
                "يلزم الإنترنت. البيانات من OpenStreetMap. يستقبل الخادم مركز المنطقة (موقعك التقريبي). قد تنتهي مهلة المناطق الكبيرة؛ جرّب 10\u201325 كم."));
        return sv;
    }

    private void setDlStatus(String s) {
        if (dlStatus != null && cur == 2) dlStatus.setText(s);
        else toast(s);
    }

    private void refreshPacks() {
        if (packsBox == null || cur != 2) return;
        runBg(() -> {
            final List<Db.Pack> packs = Db.get(MainActivity.this).packs();
            final int total = Db.get(MainActivity.this).placeCount();
            runUi(() -> {
                if (packsBox == null || cur != 2) return;
                packsBox.removeAllViews();
                TextView sum = tv(String.format(Locale.US, tr("Places stored from downloads: %d",
                        "ڈاؤن لوڈ سے محفوظ مقامات: %d", "الأماكن المخزنة من التنزيلات: %d"), total), 13, txt, false);
                sum.setPadding(0, dp(6), 0, dp(4));
                packsBox.addView(sum);
                if (packs.isEmpty()) {
                    packsBox.addView(tv(tr("Nothing downloaded yet.", "ابھی کچھ ڈاؤن لوڈ نہیں ہوا۔", "لم يتم تنزيل شيء بعد."),
                            12, sub, false));
                }
                for (final Db.Pack p : packs) {
                    TextView t = tv(String.format(Locale.US, tr("%1$s \u00B7 %2$s \u00B7 %3$d places \u00B7 %4$s",
                            "%1$s \u00B7 %2$s \u00B7 %3$d مقامات \u00B7 %4$s", "%1$s \u00B7 %2$s \u00B7 %3$d مكانًا \u00B7 %4$s"),
                            p.label, L.dist(MainActivity.this, p.radius),
                            p.count, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(p.time))), 13, txt, false);
                    t.setPadding(0, dp(10), 0, 0);
                    packsBox.addView(t);
                    addWide(packsBox, tr("Delete this area", "یہ علاقہ حذف کریں", "حذف هذه المنطقة"), chipBg, txt, v ->
                            confirm(tr("Delete downloaded area?", "ڈاؤن لوڈ شدہ علاقہ حذف کریں؟", "حذف المنطقة المنزَّلة؟"),
                                    tr("Its places will be removed from the offline data.", "اس کے مقامات آف لائن ڈیٹا سے ہٹا دیے جائیں گے۔",
                                            "ستتم إزالة أماكنها من البيانات دون اتصال."),
                                    tr("Delete", "حذف کریں", "حذف"), () -> runBg(() -> {
                                        Db.get(MainActivity.this).deletePack(p.id);
                                        runUi(() -> {
                                            requestNamer(true);
                                            refreshPacks();
                                        });
                                    })));
                }
            });
        });
    }

    private void startDownload(final String scope, final int radiusKm) {
        if (!Store.online(this)) {
            toast(tr("Online features are turned off in Settings.", "آن لائن فیچرز سیٹنگز میں بند ہیں۔",
                    "الميزات عبر الإنترنت متوقفة في الإعدادات."));
            return;
        }
        if (!LocState.has) {
            toast(tr("Waiting for your position\u2026", "آپ کے مقام کا انتظار\u2026", "بانتظار موقعك\u2026"));
            return;
        }
        if (downloading) return;
        downloading = true;
        final double lat = LocState.lat;
        final double lon = LocState.lon;
        final Namer.Info at = info;
        setDlStatus(tr("Downloading\u2026 this can take up to a minute.", "ڈاؤن لوڈ ہو رہا ہے\u2026 ایک منٹ تک لگ سکتا ہے۔",
                "جارٍ التنزيل\u2026 قد يستغرق حتى دقيقة."));
        new Thread(() -> {
            try {
                String json = Overpass.fetch(Overpass.query(lat, lon, radiusKm * 1000, scope));
                List<Place> list = Overpass.parse(json);
                Db db = Db.get(MainActivity.this);
                String label = (at != null && at.place != null) ? labelOf(at.place) : Geo.fmtPlain(lat, lon);
                long pid = db.addPack(label, lat, lon, radiusKm, scope, System.currentTimeMillis());
                int added = db.addPlaces(pid, list);
                db.setPackCount(pid, added);
                final String msg = String.format(Locale.US, tr("Done: %1$d places saved (%2$d were already stored).",
                        "مکمل: %1$d مقامات محفوظ ہوئے (%2$d پہلے سے موجود تھے)۔",
                        "تم: حُفظ %1$d مكانًا (%2$d موجودة مسبقًا)."), added, list.size() - added);
                runUi(() -> {
                    downloading = false;
                    setDlStatus(msg);
                    requestNamer(true);
                    if (cur == 0) loadNearby();
                    if (cur == 2) refreshPacks();
                });
            } catch (Exception e) {
                final String m = String.valueOf(e.getMessage());
                runUi(() -> {
                    downloading = false;
                    setDlStatus(tr("Download failed: ", "ڈاؤن لوڈ ناکام: ", "فشل التنزيل: ") + m);
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ Settings tab

    private View buildSettings() {
        pLang = Store.lang(this);
        pTheme = Store.theme(this);
        pBg = Store.bg(this);
        pBoot = Store.onBoot(this);
        pOnline = Store.online(this);
        pInterval = Store.interval(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        LinearLayout box = vbox();
        box.setPadding(dp(12), dp(10), dp(12), dp(16));
        sv.addView(box);

        final String[] langV = {"en", "ur", "ar"};
        LinearLayout s1 = section(box, tr("Language", "زبان", "اللغة"));
        addChips(s1, new String[]{"English", "اردو", "العربية"}, indexOf(langV, pLang), k -> pLang = langV[k]);

        final String[] themeV = {"dark", "light"};
        LinearLayout s2 = section(box, tr("Theme", "تھیم", "المظهر"));
        addChips(s2, new String[]{tr("Dark", "ڈارک", "داكن"), tr("Light", "لائٹ", "فاتح")},
                indexOf(themeV, pTheme), k -> pTheme = themeV[k]);

        String on = tr("On", "آن", "تشغيل");
        String off = tr("Off", "آف", "إيقاف");
        LinearLayout s3 = section(box, tr("Run in background (notification)", "پس منظر میں چلائیں (نوٹیفیکیشن)",
                "التشغيل في الخلفية (إشعار)"));
        addChips(s3, new String[]{on, off}, pBg ? 0 : 1, k -> pBg = (k == 0));
        note(s3, tr("Keeps following your position after you press Home and shows the place name in a notification.",
                "ہوم دبانے کے بعد بھی آپ کا مقام فالو کرتی رہتی ہے اور نوٹیفیکیشن میں جگہ کا نام دکھاتی ہے۔",
                "تستمر في تتبع موقعك بعد الضغط على الرئيسية وتعرض اسم المكان في إشعار."));

        final int[] iv = {2, 5, 10, 30, 60};
        String sec = tr("s", "سیکنڈ", "ث");
        String[] il = new String[iv.length];
        for (int i = 0; i < iv.length; i++) il[i] = iv[i] + " " + sec;
        LinearLayout s4 = section(box, tr("Position update interval", "مقام اپ ڈیٹ کا وقفہ", "فترة تحديث الموقع"));
        addChips(s4, il, indexOfInt(iv, pInterval), k -> pInterval = iv[k]);
        note(s4, tr("Longer intervals save battery.", "لمبا وقفہ بیٹری بچاتا ہے۔", "الفترات الأطول توفر البطارية."));

        LinearLayout s5 = section(box, tr("Start when the phone boots", "فون چلنے پر شروع کریں", "البدء عند تشغيل الهاتف"));
        addChips(s5, new String[]{on, off}, pBoot ? 0 : 1, k -> pBoot = (k == 0));

        LinearLayout s6 = section(box, tr("Online features", "آن لائن فیچرز", "الميزات عبر الإنترنت"));
        addChips(s6, new String[]{on, off}, pOnline ? 0 : 1, k -> pOnline = (k == 0));
        note(s6, tr("Only used when you press Download or Update. Turn off to never contact the internet.",
                "صرف تب استعمال ہوتے ہیں جب آپ ڈاؤن لوڈ یا اپ ڈیٹ دبائیں۔ انٹرنیٹ سے کبھی رابطہ نہ کرنے کے لیے بند کریں۔",
                "تُستخدم فقط عند الضغط على تنزيل أو تحديث. أوقفها كي لا يتصل التطبيق بالإنترنت أبدًا."));

        TextView save = button(tr("Save", "محفوظ کریں", "حفظ"), ACC, ON_ACC, v -> saveSettings());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.setMargins(0, dp(2), 0, dp(12));
        box.addView(save, sp);

        LinearLayout s7 = section(box, tr("Background tips", "پس منظر کے لیے ہدایات", "نصائح للعمل في الخلفية"));
        note(s7, tr("For reliable background tracking: allow notifications, choose \"Allow all the time\" for location, and remove battery restrictions. On Xiaomi/Oppo/Vivo/Huawei phones also enable Autostart.",
                "پس منظر میں بھروسے مند ٹریکنگ کے لیے: نوٹیفیکیشن کی اجازت دیں، لوکیشن میں \"ہر وقت اجازت\" چنیں، اور بیٹری کی پابندی ہٹائیں۔ Xiaomi/Oppo/Vivo/Huawei فونز میں Autostart بھی آن کریں۔",
                "لتتبع موثوق في الخلفية: اسمح بالإشعارات، واختر «السماح طوال الوقت» للموقع، وأزل قيود البطارية. وفي هواتف Xiaomi/Oppo/Vivo/Huawei فعّل التشغيل التلقائي (Autostart) أيضًا."));
        addWide(s7, tr("Allow location all the time", "لوکیشن ہر وقت کی اجازت", "السماح بالموقع طوال الوقت"), chipBg, txt,
                v -> requestBackgroundLocation());
        addWide(s7, tr("Battery settings", "بیٹری سیٹنگز", "إعدادات البطارية"), chipBg, txt, v -> openBatterySettings());
        addWide(s7, tr("App permissions", "ایپ کی اجازتیں", "أذونات التطبيق"), chipBg, txt, v -> openAppSettings());

        LinearLayout s8 = section(box, tr("Privacy", "رازداری", "الخصوصية"));
        note(s8, tr("Your position stays on this phone. Downloads send the center of the area you choose (your approximate position) to the OpenStreetMap Overpass servers, only when you press Download or Update. Map apps opened from the list receive that place's coordinates. Nothing else is sent. Place names mostly come from OpenStreetMap and may be in English or the local language.",
                "آپ کا مقام اسی فون پر رہتا ہے۔ ڈاؤن لوڈ تب ہی OpenStreetMap Overpass سرورز کو آپ کے چنے ہوئے علاقے کا مرکز (آپ کا تقریبی مقام) بھیجتا ہے جب آپ ڈاؤن لوڈ یا اپ ڈیٹ دبائیں۔ فہرست سے کھلنے والی نقشہ ایپ کو اس مقام کے کوآرڈینیٹ ملتے ہیں۔ اس کے علاوہ کچھ نہیں بھیجا جاتا۔ جگہوں کے نام زیادہ تر OpenStreetMap سے آتے ہیں اور انگریزی یا مقامی زبان میں ہو سکتے ہیں۔",
                "يبقى موقعك على هذا الهاتف. ترسل التنزيلات مركز المنطقة التي تختارها (موقعك التقريبي) إلى خوادم OpenStreetMap Overpass فقط عند الضغط على تنزيل أو تحديث. تستقبل تطبيقات الخرائط المفتوحة من القائمة إحداثيات ذلك المكان. لا يُرسل شيء آخر. أسماء الأماكن تأتي غالبًا من OpenStreetMap وقد تكون بالإنجليزية أو باللغة المحلية."));
        return sv;
    }

    private void saveSettings() {
        Store.setLang(this, pLang);
        Store.setTheme(this, pTheme);
        Store.setBg(this, pBg);
        Store.setInterval(this, pInterval);
        Store.setOnBoot(this, pBoot);
        Store.setOnline(this, pOnline);
        Notifier.ensureChannel(this);
        cur = 3;
        build();
        toast(tr("Saved \u2713", "محفوظ ہو گیا \u2713", "تم الحفظ \u2713"));
        if (pBg) {
            if (Build.VERSION.SDK_INT >= 33 && notifPermMissing()) askPerms();
            startTrackService();
        } else {
            stopTrackService();
        }
    }

    // ------------------------------------------------------------------ exit

    private void confirmExit() {
        LinearLayout box = vbox();
        box.setBackground(rounded(card, 16));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        box.addView(tv(tr("Exit?", "باہر نکلیں؟", "الخروج؟"), 18, txt, true));
        final boolean running = Store.bg(this) && LocClient.hasPerm(this);
        String msg = tr("Close PoiPlaces?", "PoiPlaces بند کریں؟", "إغلاق PoiPlaces؟");
        if (running) {
            msg += "\n" + tr("Background tracking and the notification can keep running.",
                    "پس منظر کی ٹریکنگ اور نوٹیفیکیشن چلتے رہ سکتے ہیں۔",
                    "يمكن أن يستمر التتبع في الخلفية والإشعار.");
        }
        TextView m = tv(msg, 14, sub, false);
        m.setPadding(0, dp(8), 0, dp(6));
        box.addView(m);
        final Dialog d = makeDialog(box);
        if (running) {
            addWide(box, tr("Exit (keep running)", "باہر نکلیں (چلتا رہے)", "خروج (استمرار العمل)"), ACC, ON_ACC, v -> {
                d.dismiss();
                finish();
            });
            addWide(box, tr("Exit and stop background", "باہر نکلیں اور پس منظر بند کریں", "خروج وإيقاف الخلفية"), chipBg, txt, v -> {
                d.dismiss();
                stopTrackService();
                finish();
            });
        } else {
            addWide(box, tr("Yes, exit", "ہاں، باہر نکلیں", "نعم، خروج"), ACC, ON_ACC, v -> {
                d.dismiss();
                finish();
            });
        }
        addWide(box, tr("Cancel", "منسوخ", "إلغاء"), chipBg, sub, v -> d.dismiss());
        showWide(d);
    }
}
