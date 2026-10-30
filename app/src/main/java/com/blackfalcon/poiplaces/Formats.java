package com.blackfalcon.poiplaces;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Import and export of POIs as GPX, KML, GeoJSON and CSV (pure Java). */
public class Formats {

    public static final String[] IDS = {"gpx", "kml", "geojson", "csv"};

    public static class ImportResult {
        public List<Poi> pois = new ArrayList<Poi>();
        public int skipped = 0;
        public String format = "";
    }

    public static String mime(String id) {
        if ("gpx".equals(id)) return "application/gpx+xml";
        if ("kml".equals(id)) return "application/vnd.google-earth.kml+xml";
        if ("geojson".equals(id)) return "application/geo+json";
        return "text/csv";
    }

    public static String fileName(String id, long nowMs) {
        return "pois_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date(nowMs)) + "." + id;
    }

    // ------------------------------------------------------------------ export

    public static String export(String id, List<Poi> list, long nowMs) {
        if ("gpx".equals(id)) return toGpx(list, nowMs);
        if ("kml".equals(id)) return toKml(list);
        if ("geojson".equals(id)) return toGeoJson(list);
        return toCsv(list);
    }

    static String c6(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    /** Escapes text for XML and drops characters that XML cannot hold. */
    static String xml(String t) {
        if (t == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '&') b.append("&amp;");
            else if (c == '<') b.append("&lt;");
            else if (c == '>') b.append("&gt;");
            else if (c == '"') b.append("&quot;");
            else if (c == '\'') b.append("&apos;");
            else if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') continue;
            else b.append(c);
        }
        return b.toString();
    }

    static String toGpx(List<Poi> list, long nowMs) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<gpx version=\"1.1\" creator=\"PoiPlaces by Black Falcon\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        b.append("  <metadata><name>POI export</name><time>").append(Geo.isoUtc(nowMs)).append("</time></metadata>\n");
        for (Poi p : list) {
            b.append("  <wpt lat=\"").append(c6(p.lat)).append("\" lon=\"").append(c6(p.lon)).append("\">\n");
            if (!Double.isNaN(p.alt)) b.append("    <ele>").append(String.format(Locale.US, "%.1f", p.alt)).append("</ele>\n");
            if (p.time > 0) b.append("    <time>").append(Geo.isoUtc(p.time)).append("</time>\n");
            b.append("    <name>").append(xml(p.name)).append("</name>\n");
            if (p.note != null && p.note.length() > 0) b.append("    <desc>").append(xml(p.note)).append("</desc>\n");
            b.append("    <type>").append(xml(p.cat)).append("</type>\n");
            b.append("  </wpt>\n");
        }
        b.append("</gpx>\n");
        return b.toString();
    }

    static String toKml(List<Poi> list) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n<name>POI export</name>\n");
        for (Poi p : list) {
            b.append("<Placemark>\n");
            b.append("  <name>").append(xml(p.name)).append("</name>\n");
            if (p.note != null && p.note.length() > 0) {
                b.append("  <description>").append(xml(p.note)).append("</description>\n");
            }
            b.append("  <ExtendedData><Data name=\"category\"><value>").append(xml(p.cat)).append("</value></Data>");
            if (p.time > 0) b.append("<Data name=\"time\"><value>").append(Geo.isoUtc(p.time)).append("</value></Data>");
            b.append("</ExtendedData>\n");
            b.append("  <Point><coordinates>").append(c6(p.lon)).append(",").append(c6(p.lat));
            if (!Double.isNaN(p.alt)) b.append(",").append(String.format(Locale.US, "%.1f", p.alt));
            b.append("</coordinates></Point>\n");
            b.append("</Placemark>\n");
        }
        b.append("</Document>\n</kml>\n");
        return b.toString();
    }

    static String toGeoJson(List<Poi> list) {
        StringBuilder b = new StringBuilder();
        b.append("{\"type\":\"FeatureCollection\",\"features\":[");
        boolean first = true;
        for (Poi p : list) {
            if (!first) b.append(",");
            first = false;
            b.append("\n{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                    .append(c6(p.lon)).append(",").append(c6(p.lat));
            if (!Double.isNaN(p.alt)) b.append(",").append(String.format(Locale.US, "%.1f", p.alt));
            b.append("]},\"properties\":{\"name\":").append(MiniJson.quote(p.name));
            b.append(",\"category\":").append(MiniJson.quote(p.cat));
            if (p.note != null && p.note.length() > 0) b.append(",\"description\":").append(MiniJson.quote(p.note));
            if (p.time > 0) b.append(",\"time\":").append(MiniJson.quote(Geo.isoUtc(p.time)));
            b.append("}}");
        }
        b.append("\n]}\n");
        return b.toString();
    }

    static String csvq(String t) {
        return "\"" + (t == null ? "" : t.replace("\"", "\"\"")) + "\"";
    }

    static String toCsv(List<Poi> list) {
        StringBuilder b = new StringBuilder();
        b.append("name,latitude,longitude,altitude,category,note,time\r\n");
        for (Poi p : list) {
            b.append(csvq(p.name)).append(",").append(c6(p.lat)).append(",").append(c6(p.lon)).append(",");
            if (!Double.isNaN(p.alt)) b.append(String.format(Locale.US, "%.1f", p.alt));
            b.append(",").append(csvq(p.cat)).append(",").append(csvq(p.note)).append(",");
            if (p.time > 0) b.append(Geo.isoUtc(p.time));
            b.append("\r\n");
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ import

    /** Detects the format from the content (not the file name) and reads the points. */
    public static ImportResult parse(String text) throws Exception {
        if (text == null) throw new Exception("empty file");
        String t = text;
        if (t.length() > 0 && t.charAt(0) == '\uFEFF') t = t.substring(1);
        String head = t.trim();
        if (head.length() == 0) throw new Exception("empty file");
        if (head.startsWith("<")) {
            String low = head.substring(0, Math.min(head.length(), 4000)).toLowerCase(Locale.US);
            if (low.contains("<gpx")) return parseGpx(t);
            if (low.contains("<kml")) return parseKml(t);
            throw new Exception("unknown XML file");
        }
        if (head.startsWith("{") || head.startsWith("[")) return parseGeoJson(t);
        return parseCsv(t);
    }

    private static Document dom(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignore) {
            // not supported by this parser
        }
        f.setNamespaceAware(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new InputSource(new StringReader(xml)));
    }

    private static String childText(Element e, String tag) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String nn = n.getNodeName();
                if (nn.equals(tag) || nn.endsWith(":" + tag)) {
                    String v = n.getTextContent();
                    return v == null ? "" : v.trim();
                }
            }
        }
        return "";
    }

    private static double dbl(String s, double def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.length() == 0) return def;
        if (t.indexOf(',') >= 0 && t.indexOf('.') < 0) t = t.replace(',', '.');
        try {
            return Double.parseDouble(t);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean okLatLon(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static final java.util.regex.Pattern HTML_TAG = java.util.regex.Pattern.compile(
            "(?i)<\\s*(br|p|b|i|u|a|div|span|table|tr|td|th|ul|ol|li|img|font|strong|em|h[1-6])\\b[^>]*>|</\\s*[a-z0-9]+\\s*>");

    /** Removes HTML tags only when the text really looks like HTML (Google Earth descriptions); plain notes stay as they are. */
    private static String stripHtml(String s) {
        if (!HTML_TAG.matcher(s).find()) return s;
        return s.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    static ImportResult parseGpx(String xml) throws Exception {
        ImportResult r = new ImportResult();
        r.format = "gpx";
        Document doc = dom(xml);
        NodeList l = doc.getElementsByTagName("wpt");
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            double lat = dbl(e.getAttribute("lat"), Double.NaN);
            double lon = dbl(e.getAttribute("lon"), Double.NaN);
            if (!okLatLon(lat, lon)) {
                r.skipped++;
                continue;
            }
            Poi p = new Poi();
            p.lat = lat;
            p.lon = lon;
            p.name = childText(e, "name");
            if (p.name.length() == 0) p.name = "Imported point";
            String desc = childText(e, "desc");
            if (desc.length() == 0) desc = childText(e, "cmt");
            p.note = desc;
            p.alt = dbl(childText(e, "ele"), Double.NaN);
            String time = childText(e, "time");
            if (time.length() > 0) p.time = Geo.parseIso(time);
            String type = childText(e, "type");
            if (type.length() == 0) type = childText(e, "sym");
            p.cat = Cat.normalize(type);
            r.pois.add(p);
        }
        return r;
    }

    static ImportResult parseKml(String xml) throws Exception {
        ImportResult r = new ImportResult();
        r.format = "kml";
        Document doc = dom(xml);
        NodeList l = doc.getElementsByTagName("Placemark");
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            NodeList pts = e.getElementsByTagName("Point");
            if (pts.getLength() == 0) {
                r.skipped++;
                continue;
            }
            Element pt = (Element) pts.item(0);
            String coords = childText(pt, "coordinates");
            String[] tup = coords.trim().split("[\\s]+")[0].split(",");
            double lon = tup.length > 0 ? dbl(tup[0], Double.NaN) : Double.NaN;
            double lat = tup.length > 1 ? dbl(tup[1], Double.NaN) : Double.NaN;
            if (!okLatLon(lat, lon)) {
                r.skipped++;
                continue;
            }
            Poi p = new Poi();
            p.lat = lat;
            p.lon = lon;
            p.alt = tup.length > 2 ? dbl(tup[2], Double.NaN) : Double.NaN;
            if (!Double.isNaN(p.alt) && p.alt == 0.0 && tup.length > 2 && tup[2].trim().equals("0")) p.alt = Double.NaN;
            p.name = childText(e, "name");
            if (p.name.length() == 0) p.name = "Imported point";
            p.note = stripHtml(childText(e, "description"));
            String cat = "";
            String time = "";
            NodeList data = e.getElementsByTagName("Data");
            for (int k = 0; k < data.getLength(); k++) {
                Element d = (Element) data.item(k);
                String n = d.getAttribute("name").toLowerCase(Locale.US);
                String v = childText(d, "value");
                if (n.equals("category") || n.equals("type")) cat = v;
                else if (n.equals("time")) time = v;
            }
            if (time.length() == 0) {
                NodeList wh = e.getElementsByTagName("when");
                if (wh.getLength() > 0) time = wh.item(0).getTextContent().trim();
            }
            if (time.length() > 0) p.time = Geo.parseIso(time);
            p.cat = Cat.normalize(cat);
            r.pois.add(p);
        }
        return r;
    }

    static ImportResult parseGeoJson(String json) throws Exception {
        ImportResult r = new ImportResult();
        r.format = "geojson";
        Object root = MiniJson.parse(json);
        List<Object> feats = new ArrayList<Object>();
        Map<String, Object> m = MiniJson.obj(root);
        if (m != null) {
            String type = MiniJson.str(m, "type");
            if ("FeatureCollection".equals(type)) {
                List<Object> f = MiniJson.arr(m.get("features"));
                if (f != null) feats.addAll(f);
            } else {
                feats.add(m);
            }
        } else {
            List<Object> a = MiniJson.arr(root);
            if (a != null) feats.addAll(a);
        }
        for (Object o : feats) {
            Map<String, Object> f = MiniJson.obj(o);
            if (f == null) {
                r.skipped++;
                continue;
            }
            Map<String, Object> geom;
            Map<String, Object> props;
            if ("Feature".equals(MiniJson.str(f, "type"))) {
                geom = MiniJson.obj(f.get("geometry"));
                props = MiniJson.obj(f.get("properties"));
            } else {
                geom = f;
                props = null;
            }
            if (geom == null) {
                r.skipped++;
                continue;
            }
            String gt = MiniJson.str(geom, "type");
            List<List<Object>> points = new ArrayList<List<Object>>();
            if ("Point".equals(gt)) {
                List<Object> c = MiniJson.arr(geom.get("coordinates"));
                if (c != null) points.add(c);
            } else if ("MultiPoint".equals(gt)) {
                List<Object> cs = MiniJson.arr(geom.get("coordinates"));
                if (cs != null) {
                    for (Object co : cs) {
                        List<Object> c = MiniJson.arr(co);
                        if (c != null) points.add(c);
                    }
                }
            }
            if (points.isEmpty()) {
                r.skipped++;
                continue;
            }
            String name = firstOf(props, "name", "Name", "title", "label");
            String note = firstOf(props, "description", "desc", "note", "notes", "comment");
            String cat = firstOf(props, "category", "type", "amenity");
            String time = firstOf(props, "time", "timestamp", "date");
            for (List<Object> c : points) {
                if (c.size() < 2) {
                    r.skipped++;
                    continue;
                }
                double lon = MiniJson.num(c.get(0), Double.NaN);
                double lat = MiniJson.num(c.get(1), Double.NaN);
                if (!okLatLon(lat, lon)) {
                    r.skipped++;
                    continue;
                }
                Poi p = new Poi();
                p.lat = lat;
                p.lon = lon;
                if (c.size() > 2) p.alt = MiniJson.num(c.get(2), Double.NaN);
                p.name = name.length() > 0 ? name : "Imported point";
                p.note = note;
                p.cat = Cat.normalize(cat);
                if (time.length() > 0) p.time = Geo.parseIso(time);
                r.pois.add(p);
            }
        }
        return r;
    }

    private static String firstOf(Map<String, Object> m, String... keys) {
        if (m == null) return "";
        for (String k : keys) {
            String v = MiniJson.str(m, k);
            if (v.length() > 0) return v.trim();
        }
        return "";
    }

    // ---- CSV ----

    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String line = end >= 0 ? text.substring(0, end) : text;
        int commas = 0, semis = 0, tabs = 0;
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') inQ = !inQ;
            else if (!inQ) {
                if (c == ',') commas++;
                else if (c == ';') semis++;
                else if (c == '\t') tabs++;
            }
        }
        if (semis > commas && semis >= tabs) return ';';
        if (tabs > commas && tabs > semis) return '\t';
        return ',';
    }

    static List<List<String>> csvRows(String text, char delim) {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean inQ = false;
        boolean any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQ = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQ = true;
                any = true;
            } else if (c == delim) {
                row.add(cell.toString());
                cell.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                if (any || cell.length() > 0) {
                    row.add(cell.toString());
                    rows.add(row);
                }
                row = new ArrayList<String>();
                cell.setLength(0);
                any = false;
            } else {
                cell.append(c);
                any = true;
            }
        }
        if (any || cell.length() > 0) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static int col(List<String> header, String... names) {
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).trim().toLowerCase(Locale.US);
            for (String n : names) if (h.equals(n)) return i;
        }
        return -1;
    }

    private static String cell(List<String> row, int i) {
        return (i >= 0 && i < row.size()) ? row.get(i).trim() : "";
    }

    static ImportResult parseCsv(String text) throws Exception {
        ImportResult r = new ImportResult();
        r.format = "csv";
        char delim = detectDelimiter(text);
        List<List<String>> rows = csvRows(text, delim);
        if (rows.isEmpty()) return r;
        List<String> first = rows.get(0);
        boolean firstIsData = first.size() >= 2
                && ((!Double.isNaN(dbl(first.get(0), Double.NaN)) && !Double.isNaN(dbl(first.get(1), Double.NaN)))
                || (first.size() >= 3 && !Double.isNaN(dbl(first.get(1), Double.NaN)) && !Double.isNaN(dbl(first.get(2), Double.NaN))));
        int cName, cLat, cLon, cAlt, cCat, cNote, cTime, cLoc;
        int start;
        if (firstIsData) {
            boolean latFirst = !Double.isNaN(dbl(first.get(0), Double.NaN));
            if (latFirst) {
                cLat = 0; cLon = 1; cName = 2; cAlt = -1; cCat = 3; cNote = 4; cTime = 5;
            } else {
                cName = 0; cLat = 1; cLon = 2; cAlt = 3; cCat = 4; cNote = 5; cTime = 6;
            }
            cLoc = -1;
            start = 0;
        } else {
            cName = col(first, "name", "title", "label", "poi", "place");
            cLat = col(first, "lat", "latitude", "y");
            cLon = col(first, "lon", "lng", "long", "longitude", "x");
            cAlt = col(first, "alt", "altitude", "ele", "elevation");
            cCat = col(first, "cat", "category", "type", "group");
            cNote = col(first, "note", "notes", "description", "desc", "comment", "cmt");
            cTime = col(first, "time", "date", "timestamp");
            cLoc = col(first, "location", "coordinates", "coords", "position");
            if ((cLat < 0 || cLon < 0) && cLoc < 0) throw new Exception("no latitude/longitude columns");
            start = 1;
        }
        for (int i = start; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            double lat = Double.NaN;
            double lon = Double.NaN;
            if (cLat >= 0 && cLon >= 0) {
                lat = dbl(cell(row, cLat), Double.NaN);
                lon = dbl(cell(row, cLon), Double.NaN);
            }
            if (!okLatLon(lat, lon) && cLoc >= 0) {
                double[] c = Geo.parseCoords(cell(row, cLoc));
                if (c != null) {
                    lat = c[0];
                    lon = c[1];
                }
            }
            if (!okLatLon(lat, lon)) {
                r.skipped++;
                continue;
            }
            Poi p = new Poi();
            p.lat = lat;
            p.lon = lon;
            p.name = cell(row, cName);
            if (p.name.length() == 0) p.name = "Imported point";
            p.alt = dbl(cell(row, cAlt), Double.NaN);
            p.cat = Cat.normalize(cell(row, cCat));
            p.note = cell(row, cNote);
            String time = cell(row, cTime);
            if (time.length() > 0) p.time = Geo.parseIso(time);
            r.pois.add(p);
        }
        return r;
    }
}
