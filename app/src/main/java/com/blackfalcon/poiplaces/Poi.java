package com.blackfalcon.poiplaces;

/** A saved point of interest. Pure data (no Android classes). */
public class Poi {
    public long id = 0;
    public String name = "";
    public double lat;
    public double lon;
    /** Altitude in meters, NaN when unknown. */
    public double alt = Double.NaN;
    public String cat = "general";
    public String note = "";
    public long time = 0;

    public Poi() {
    }

    public Poi(String name, double lat, double lon) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }
}
