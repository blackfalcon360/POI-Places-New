package com.blackfalcon.poiplaces;

/** A named place from the built-in city list or a downloaded offline area. Pure data. */
public class Place {
    public long id = 0;
    /** Unique key such as n123456 (OpenStreetMap node) or empty for built-in cities. */
    public String osm = "";
    public String name = "";
    /** English name when known, otherwise empty. */
    public String en = "";
    public double lat;
    public double lon;
    /** "settlement" or "service" */
    public String kind = "settlement";
    /** settlement: city town village hamlet suburb neighbourhood locality; service: a category code */
    public String sub = "";
    public String country = "";

    public Place() {
    }

    public Place(String name, double lat, double lon, String kind, String sub) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.kind = kind;
        this.sub = sub;
    }

    /** Name to show for a language code: English prefers the English name, others prefer the local name. */
    public String label(String lang) {
        if ("en".equals(lang)) return en.length() > 0 ? en : name;
        return name.length() > 0 ? name : en;
    }
}
