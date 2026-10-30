package com.blackfalcon.poiplaces;

import android.content.Context;

/** Language helper: every UI string is given in English, Urdu and Arabic. */
public class L {

    public static String t(Context c, String en, String ur, String ar) {
        String lang = Store.lang(c);
        if ("ur".equals(lang)) return ur;
        if ("ar".equals(lang)) return ar;
        return en;
    }

    public static boolean rtl(Context c) {
        String lang = Store.lang(c);
        return "ur".equals(lang) || "ar".equals(lang);
    }

    public static String dir(Context c, int i) {
        String[] en = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String[] ur = {"شمال", "شمال مشرق", "مشرق", "جنوب مشرق", "جنوب", "جنوب مغرب", "مغرب", "شمال مغرب"};
        String[] ar = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        String lang = Store.lang(c);
        String[] a = "ur".equals(lang) ? ur : ("ar".equals(lang) ? ar : en);
        return a[i & 7];
    }

    /** "850 m" or "2.3 km" in the chosen language. */
    public static String dist(Context c, double km) {
        String unit;
        if (Geo.distIsMeters(km)) unit = t(c, "m", "میٹر", "م");
        else unit = t(c, "km", "کلومیٹر", "كم");
        return Geo.distValue(km) + " " + unit;
    }

    public static String ago(Context c, long diffMs) {
        long s = Math.max(0, diffMs / 1000);
        if (s < 60) return t(c, "just now", "ابھی", "الآن");
        long m = s / 60;
        if (m < 60) return String.format(java.util.Locale.US, t(c, "%d min ago", "%d منٹ پہلے", "منذ %d دقيقة"), m);
        long h = m / 60;
        if (h < 48) return String.format(java.util.Locale.US, t(c, "%d h ago", "%d گھنٹے پہلے", "منذ %d ساعة"), h);
        return String.format(java.util.Locale.US, t(c, "%d d ago", "%d دن پہلے", "منذ %d يوم"), h / 24);
    }

    /** Name of a category or place type. */
    public static String cat(Context c, String code) {
        if (code == null) code = "general";
        String en, ur, ar;
        switch (code) {
            case "home": en = "Home"; ur = "گھر"; ar = "المنزل"; break;
            case "mosque": en = "Mosque"; ur = "مسجد"; ar = "مسجد"; break;
            case "hospital": en = "Hospital"; ur = "ہسپتال"; ar = "مستشفى"; break;
            case "pharmacy": en = "Pharmacy"; ur = "میڈیکل اسٹور"; ar = "صيدلية"; break;
            case "fuel": en = "Fuel"; ur = "پیٹرول پمپ"; ar = "وقود"; break;
            case "food": en = "Food"; ur = "کھانا"; ar = "طعام"; break;
            case "bank": en = "Bank / ATM"; ur = "بینک / اے ٹی ایم"; ar = "بنك / صراف"; break;
            case "school": en = "School"; ur = "اسکول"; ar = "مدرسة"; break;
            case "market": en = "Market"; ur = "بازار"; ar = "سوق"; break;
            case "hotel": en = "Hotel"; ur = "ہوٹل"; ar = "فندق"; break;
            case "attraction": en = "Attraction"; ur = "سیر گاہ"; ar = "معلم سياحي"; break;
            case "police": en = "Police"; ur = "پولیس"; ar = "شرطة"; break;
            case "worship": en = "Place of worship"; ur = "عبادت گاہ"; ar = "دار عبادة"; break;
            case "city": en = "City"; ur = "شہر"; ar = "مدينة"; break;
            case "town": en = "Town"; ur = "قصبہ"; ar = "بلدة"; break;
            case "village": en = "Village"; ur = "گاؤں"; ar = "قرية"; break;
            case "hamlet": en = "Hamlet"; ur = "چھوٹی بستی"; ar = "قرية صغيرة"; break;
            case "suburb": en = "Suburb"; ur = "علاقہ"; ar = "ضاحية"; break;
            case "neighbourhood": en = "Neighborhood"; ur = "محلہ"; ar = "حي"; break;
            case "locality": en = "Locality"; ur = "مقام"; ar = "موقع"; break;
            default: en = "General"; ur = "عمومی"; ar = "عام"; break;
        }
        return t(c, en, ur, ar);
    }
}
