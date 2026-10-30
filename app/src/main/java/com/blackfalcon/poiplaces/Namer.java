package com.blackfalcon.poiplaces;

import java.util.List;

/** Chooses the name of "where am I" from known places (pure Java). */
public class Namer {

    public static class Info {
        /** Nearest settlement, null when nothing is known. */
        public Place place;
        public double km;
        /** Direction from the place to you (0=N .. 7=NW): "2.3 km SW of Risalpur". */
        public int sector;
        /** Nearest city when the nearest settlement is not itself a city, otherwise null. */
        public Place city;
        public double cityKm;
        /** Direction from you to the city. */
        public int citySector;
        /** True when the nearest known place is very far away. */
        public boolean far;
    }

    public static Info pick(double lat, double lon, List<Place> candidates) {
        Info r = new Info();
        double best = Double.MAX_VALUE;
        double bestCity = Double.MAX_VALUE;
        Place city = null;
        for (Place p : candidates) {
            if (!"settlement".equals(p.kind)) continue;
            double d = Geo.km(lat, lon, p.lat, p.lon);
            if (d < best) {
                best = d;
                r.place = p;
            }
            if ("city".equals(p.sub) && d < bestCity) {
                bestCity = d;
                city = p;
            }
        }
        if (r.place == null) return r;
        r.km = best;
        r.sector = Geo.sector(Geo.bearing(r.place.lat, r.place.lon, lat, lon));
        r.far = best > 250.0;
        if (city != null && !"city".equals(r.place.sub)) {
            r.city = city;
            r.cityKm = bestCity;
            r.citySector = Geo.sector(Geo.bearing(lat, lon, city.lat, city.lon));
        }
        return r;
    }
}
