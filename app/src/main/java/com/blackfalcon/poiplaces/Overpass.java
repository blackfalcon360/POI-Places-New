package com.blackfalcon.poiplaces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Downloads places around a point from OpenStreetMap through the public Overpass API (pure Java). */
public class Overpass {

    public static final String[] SERVERS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"};

    /** scope: "settlements", "services" or "both". */
    public static String query(double lat, double lon, int radiusM, String scope) {
        String around = "(around:" + radiusM + "," + String.format(Locale.US, "%.5f", lat) + ","
                + String.format(Locale.US, "%.5f", lon) + ")";
        StringBuilder b = new StringBuilder("[out:json][timeout:90];(");
        if (!"services".equals(scope)) {
            b.append("node[\"place\"~\"^(city|town|village|hamlet|suburb|neighbourhood|locality)$\"][\"name\"]")
                    .append(around).append(";");
        }
        if (!"settlements".equals(scope)) {
            b.append("nwr[\"amenity\"~\"^(place_of_worship|hospital|clinic|pharmacy|fuel|restaurant|cafe|fast_food|bank|atm|school|college|university|police|marketplace)$\"][\"name\"]")
                    .append(around).append(";");
            b.append("nwr[\"tourism\"~\"^(hotel|guest_house|hostel|attraction|viewpoint)$\"][\"name\"]")
                    .append(around).append(";");
        }
        b.append(");out tags center 20000;");
        return b.toString();
    }

    /** Name/category search near the current position; results are parsed by the normal OSM parser. */
    public static String searchQuery(String term, double lat, double lon, int radiusM) {
        String safe = term.trim().replace("\\", "\\\\").replace("\"", "\\\"");
        return "[out:json][timeout:35];(nwr[\"name\"~\"" + safe + "\",i](around:" + radiusM + ","
                + String.format(Locale.US, "%.5f", lat) + "," + String.format(Locale.US, "%.5f", lon) + "););out tags center 150;";
    }

    /** Sends the query to the first server that answers. */
    public static String fetch(String query) throws Exception {
        Exception last = null;
        for (String server : SERVERS) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(server).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(100000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("User-Agent", "PoiPlaces/1.0 (Android)");
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                byte[] body = ("data=" + URLEncoder.encode(query, "UTF-8")).getBytes("UTF-8");
                OutputStream os = c.getOutputStream();
                os.write(body);
                os.close();
                int code = c.getResponseCode();
                if (code != 200) {
                    c.disconnect();
                    throw new IOException("HTTP " + code);
                }
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                c.disconnect();
                return bo.toString("UTF-8");
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no server");
    }

    private static String tag(Map<String, Object> tags, String k) {
        return MiniJson.str(tags, k).trim();
    }

    /** Service category code for OpenStreetMap tags, or "" when the element is not wanted. */
    static String serviceCode(Map<String, Object> tags) {
        String amenity = tag(tags, "amenity");
        switch (amenity) {
            case "place_of_worship":
                return "muslim".equalsIgnoreCase(tag(tags, "religion")) ? "mosque" : "worship";
            case "hospital":
            case "clinic":
                return "hospital";
            case "pharmacy":
                return "pharmacy";
            case "fuel":
                return "fuel";
            case "restaurant":
            case "cafe":
            case "fast_food":
                return "food";
            case "bank":
            case "atm":
                return "bank";
            case "school":
            case "college":
            case "university":
                return "school";
            case "police":
                return "police";
            case "marketplace":
                return "market";
            default:
                break;
        }
        String tourism = tag(tags, "tourism");
        switch (tourism) {
            case "hotel":
            case "guest_house":
            case "hostel":
                return "hotel";
            case "attraction":
            case "viewpoint":
                return "attraction";
            default:
                return "";
        }
    }

    private static boolean settlementKind(String p) {
        return p.equals("city") || p.equals("town") || p.equals("village") || p.equals("hamlet")
                || p.equals("suburb") || p.equals("neighbourhood") || p.equals("locality");
    }

    /** Reads an Overpass JSON answer into places. Elements without a name are skipped. */
    public static List<Place> parse(String json) throws Exception {
        Map<String, Object> root = MiniJson.obj(MiniJson.parse(json));
        if (root == null) throw new Exception("unexpected answer");
        List<Object> els = MiniJson.arr(root.get("elements"));
        if (els == null) els = new ArrayList<Object>();
        String remark = MiniJson.str(root, "remark");
        if (els.isEmpty() && remark.toLowerCase(Locale.US).contains("error")) throw new Exception(remark);
        List<Place> out = new ArrayList<Place>();
        for (Object o : els) {
            Map<String, Object> e = MiniJson.obj(o);
            if (e == null) continue;
            Map<String, Object> tags = MiniJson.obj(e.get("tags"));
            if (tags == null) continue;
            String name = tag(tags, "name");
            if (name.length() == 0) continue;
            double lat = MiniJson.num(e.get("lat"), Double.NaN);
            double lon = MiniJson.num(e.get("lon"), Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                Map<String, Object> c = MiniJson.obj(e.get("center"));
                if (c != null) {
                    lat = MiniJson.num(c.get("lat"), Double.NaN);
                    lon = MiniJson.num(c.get("lon"), Double.NaN);
                }
            }
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            Place p = new Place();
            String place = tag(tags, "place");
            if (place.length() > 0) {
                if (!settlementKind(place)) continue;
                p.kind = "settlement";
                p.sub = place;
            } else {
                String code = serviceCode(tags);
                if (code.length() == 0) continue;
                p.kind = "service";
                p.sub = code;
            }
            String type = MiniJson.str(e, "type");
            p.osm = (type.length() > 0 ? type.substring(0, 1) : "x") + MiniJson.str(e, "id");
            p.name = name;
            p.en = tag(tags, "name:en");
            p.lat = lat;
            p.lon = lon;
            out.add(p);
        }
        return out;
    }
}
