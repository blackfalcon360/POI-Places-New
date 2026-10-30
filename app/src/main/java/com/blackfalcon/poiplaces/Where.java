package com.blackfalcon.poiplaces;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Works out "where am I" from the offline database and the built-in cities, and words it in the chosen language. */
public class Where {

    public static Namer.Info describe(Context c, double lat, double lon) {
        List<Place> cand = new ArrayList<Place>(Cities.all());
        Db db = Db.get(c);
        cand.addAll(db.placesIn(lat, lon, 60, "settlement", null, 3000));
        cand.addAll(db.placesIn(lat, lon, 250, "settlement", "city", 300));
        return Namer.pick(lat, lon, cand);
    }

    public static String title(Context c, Namer.Info i) {
        if (i == null || i.place == null) {
            return L.t(c, "Unknown area", "نامعلوم علاقہ", "منطقة غير معروفة");
        }
        String name = i.place.label(Store.lang(c));
        if (i.km < 1.5) return name;
        return String.format(Locale.US, L.t(c, "%1$s %2$s of %3$s", "%3$s سے %1$s %2$s", "%1$s %2$s من %3$s"),
                L.dist(c, i.km), L.dir(c, i.sector), name);
    }

    /** Second line: nearest city when it adds information, or a hint when no places are known. */
    public static String cityLine(Context c, Namer.Info i) {
        if (i == null || i.place == null) {
            return L.t(c, "No offline places yet. Open the Offline tab to download your area.",
                    "ابھی کوئی آف لائن مقام نہیں۔ اپنا علاقہ ڈاؤن لوڈ کرنے کے لیے آف لائن ٹیب کھولیں۔",
                    "لا توجد أماكن دون اتصال بعد. افتح تبويب دون اتصال لتنزيل منطقتك.");
        }
        if (i.city == null) return "";
        return String.format(Locale.US, L.t(c, "Nearest city: %1$s, %2$s %3$s", "قریب ترین شہر: %1$s، %2$s %3$s",
                "أقرب مدينة: %1$s، %2$s %3$s"),
                i.city.label(Store.lang(c)), L.dist(c, i.cityKm), L.dir(c, i.citySector));
    }
}
