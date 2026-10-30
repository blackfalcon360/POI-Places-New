package com.blackfalcon.poiplaces;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.List;

/** Local database: saved POIs, downloaded offline places and the list of downloaded areas. */
public class Db extends SQLiteOpenHelper {

    public static class Pack {
        public long id;
        public String label = "";
        public double lat;
        public double lon;
        public int radius;
        public String scope = "";
        public long time;
        public int count;
    }

    private static Db inst;

    public static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) {
        super(c, "poiplaces.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE poi (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, lat REAL, lon REAL, "
                + "alt REAL, cat TEXT, note TEXT, time INTEGER)");
        db.execSQL("CREATE TABLE pack (id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT, lat REAL, lon REAL, "
                + "radius INTEGER, scope TEXT, time INTEGER, cnt INTEGER)");
        db.execSQL("CREATE TABLE place (id INTEGER PRIMARY KEY AUTOINCREMENT, osm TEXT UNIQUE, name TEXT, en TEXT, "
                + "lat REAL, lon REAL, kind TEXT, sub TEXT, pack INTEGER)");
        db.execSQL("CREATE INDEX place_lat ON place(lat)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // first version: nothing to migrate
    }

    // ------------------------------------------------------------------ POIs

    private static ContentValues cv(Poi p) {
        ContentValues v = new ContentValues();
        v.put("name", p.name);
        v.put("lat", p.lat);
        v.put("lon", p.lon);
        if (Double.isNaN(p.alt)) v.putNull("alt");
        else v.put("alt", p.alt);
        v.put("cat", p.cat);
        v.put("note", p.note);
        v.put("time", p.time);
        return v;
    }

    public synchronized long addPoi(Poi p) {
        if (p.time == 0) p.time = System.currentTimeMillis();
        return getWritableDatabase().insert("poi", null, cv(p));
    }

    public synchronized void updatePoi(Poi p) {
        getWritableDatabase().update("poi", cv(p), "id=?", new String[]{String.valueOf(p.id)});
    }

    public synchronized void deletePoi(long id) {
        getWritableDatabase().delete("poi", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized List<Poi> pois() {
        List<Poi> out = new ArrayList<Poi>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,lat,lon,alt,cat,note,time FROM poi ORDER BY id DESC", null);
        try {
            while (c.moveToNext()) {
                Poi p = new Poi();
                p.id = c.getLong(0);
                p.name = c.isNull(1) ? "" : c.getString(1);
                p.lat = c.getDouble(2);
                p.lon = c.getDouble(3);
                p.alt = c.isNull(4) ? Double.NaN : c.getDouble(4);
                p.cat = c.isNull(5) ? "general" : c.getString(5);
                p.note = c.isNull(6) ? "" : c.getString(6);
                p.time = c.getLong(7);
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized int poiCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM poi", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** True when a POI with the same name exists within about 5 meters. */
    public synchronized boolean poiExists(String name, double lat, double lon) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM poi WHERE name=? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT 1",
                new String[]{name, String.valueOf(lat - 0.00005), String.valueOf(lat + 0.00005),
                        String.valueOf(lon - 0.00005), String.valueOf(lon + 0.00005)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** Adds the points that are not already saved; returns how many were added. */
    public synchronized int importPois(List<Poi> list) {
        SQLiteDatabase db = getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try {
            for (Poi p : list) {
                if (poiExists(p.name, p.lat, p.lon)) continue;
                if (p.time == 0) p.time = System.currentTimeMillis();
                db.insert("poi", null, cv(p));
                added++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return added;
    }

    // ------------------------------------------------------------------ offline places

    /** Inserts places that are not stored yet (same OpenStreetMap id is skipped); returns how many were added. */
    public synchronized int addPlaces(long packId, List<Place> list) {
        SQLiteDatabase db = getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try {
            SQLiteStatement st = db.compileStatement(
                    "INSERT OR IGNORE INTO place (osm,name,en,lat,lon,kind,sub,pack) VALUES (?,?,?,?,?,?,?,?)");
            for (Place p : list) {
                st.clearBindings();
                st.bindString(1, p.osm);
                st.bindString(2, p.name);
                st.bindString(3, p.en);
                st.bindDouble(4, p.lat);
                st.bindDouble(5, p.lon);
                st.bindString(6, p.kind);
                st.bindString(7, p.sub);
                st.bindLong(8, packId);
                long r = st.executeInsert();
                if (r != -1) added++;
            }
            st.close();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return added;
    }

    /** Places inside a square around the point, nearest first. kind and sub may be null. */
    public synchronized List<Place> placesIn(double lat, double lon, double radiusKm, String kind, String sub, int limit) {
        double dLat = Geo.dLat(radiusKm);
        double dLon = Geo.dLon(radiusKm, lat);
        double cos = Math.cos(Math.toRadians(Math.min(Math.abs(lat), 89.0)));
        StringBuilder sql = new StringBuilder(
                "SELECT id,osm,name,en,lat,lon,kind,sub FROM place WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?");
        ArrayList<String> args = new ArrayList<String>();
        args.add(String.valueOf(lat - dLat));
        args.add(String.valueOf(lat + dLat));
        args.add(String.valueOf(lon - dLon));
        args.add(String.valueOf(lon + dLon));
        if (kind != null) {
            sql.append(" AND kind=?");
            args.add(kind);
        }
        if (sub != null) {
            sql.append(" AND sub=?");
            args.add(sub);
        }
        sql.append(" ORDER BY ((lat-?)*(lat-?)+(lon-?)*(lon-?)*?) LIMIT ").append(limit);
        args.add(String.valueOf(lat));
        args.add(String.valueOf(lat));
        args.add(String.valueOf(lon));
        args.add(String.valueOf(lon));
        args.add(String.valueOf(cos * cos));
        List<Place> out = new ArrayList<Place>();
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            while (c.moveToNext()) {
                Place p = new Place();
                p.id = c.getLong(0);
                p.osm = c.isNull(1) ? "" : c.getString(1);
                p.name = c.isNull(2) ? "" : c.getString(2);
                p.en = c.isNull(3) ? "" : c.getString(3);
                p.lat = c.getDouble(4);
                p.lon = c.getDouble(5);
                p.kind = c.isNull(6) ? "settlement" : c.getString(6);
                p.sub = c.isNull(7) ? "" : c.getString(7);
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Searches all downloaded offline places by local name, English name or category. */
    public synchronized List<Place> searchPlaces(String term, int limit) {
        String q = "%" + term.trim() + "%";
        List<Place> out = new ArrayList<Place>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,osm,name,en,lat,lon,kind,sub FROM place "
                        + "WHERE name LIKE ? OR en LIKE ? OR sub LIKE ? "
                        + "ORDER BY name COLLATE NOCASE LIMIT " + Math.max(1, Math.min(limit, 500)),
                new String[]{q, q, q});
        try {
            while (c.moveToNext()) {
                Place p = new Place();
                p.id = c.getLong(0);
                p.osm = c.isNull(1) ? "" : c.getString(1);
                p.name = c.isNull(2) ? "" : c.getString(2);
                p.en = c.isNull(3) ? "" : c.getString(3);
                p.lat = c.getDouble(4);
                p.lon = c.getDouble(5);
                p.kind = c.isNull(6) ? "settlement" : c.getString(6);
                p.sub = c.isNull(7) ? "" : c.getString(7);
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized int placeCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM place", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    // ------------------------------------------------------------------ downloaded areas

    public synchronized long addPack(String label, double lat, double lon, int radius, String scope, long time) {
        ContentValues v = new ContentValues();
        v.put("label", label);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("radius", radius);
        v.put("scope", scope);
        v.put("time", time);
        v.put("cnt", 0);
        return getWritableDatabase().insert("pack", null, v);
    }

    public synchronized void setPackCount(long id, int count) {
        ContentValues v = new ContentValues();
        v.put("cnt", count);
        getWritableDatabase().update("pack", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized List<Pack> packs() {
        List<Pack> out = new ArrayList<Pack>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,label,lat,lon,radius,scope,time,cnt FROM pack ORDER BY id DESC", null);
        try {
            while (c.moveToNext()) {
                Pack p = new Pack();
                p.id = c.getLong(0);
                p.label = c.isNull(1) ? "" : c.getString(1);
                p.lat = c.getDouble(2);
                p.lon = c.getDouble(3);
                p.radius = c.getInt(4);
                p.scope = c.isNull(5) ? "" : c.getString(5);
                p.time = c.getLong(6);
                p.count = c.getInt(7);
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Deletes a downloaded area together with the places that came from it. */
    public synchronized void deletePack(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("place", "pack=?", new String[]{String.valueOf(id)});
            db.delete("pack", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
