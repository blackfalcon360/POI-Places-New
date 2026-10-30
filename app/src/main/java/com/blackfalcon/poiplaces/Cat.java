package com.blackfalcon.poiplaces;

import java.util.Locale;

/** Category codes and emoji (pure). Translated labels live in L. */
public class Cat {

    /** Categories the user can pick for a saved POI. */
    public static final String[] POI_CODES = {
            "general", "home", "mosque", "hospital", "pharmacy", "fuel", "food", "bank",
            "school", "market", "hotel", "attraction", "police", "worship"};

    public static String emoji(String code) {
        if (code == null) return "\uD83D\uDCCD";
        switch (code) {
            case "home": return "\uD83C\uDFE0";
            case "mosque": return "\uD83D\uDD4C";
            case "worship": return "\uD83D\uDED0";
            case "hospital": return "\uD83C\uDFE5";
            case "pharmacy": return "\uD83D\uDC8A";
            case "fuel": return "\u26FD";
            case "food": return "\uD83C\uDF7D\uFE0F";
            case "bank": return "\uD83C\uDFE6";
            case "school": return "\uD83C\uDFEB";
            case "police": return "\uD83D\uDC6E";
            case "market": return "\uD83D\uDED2";
            case "hotel": return "\uD83C\uDFE8";
            case "attraction": return "\uD83C\uDFDE\uFE0F";
            case "city": return "\uD83C\uDFD9\uFE0F";
            case "town": return "\uD83C\uDFD8\uFE0F";
            case "village": return "\uD83C\uDFE1";
            case "hamlet": return "\uD83C\uDFE1";
            case "suburb": return "\uD83C\uDFD8\uFE0F";
            case "neighbourhood": return "\uD83C\uDFD8\uFE0F";
            case "locality": return "\uD83D\uDCCD";
            default: return "\uD83D\uDCCD";
        }
    }

    public static boolean isPoiCode(String c) {
        for (String s : POI_CODES) if (s.equals(c)) return true;
        return false;
    }

    /** Maps free text from an imported file to a known category code ("general" when unknown). */
    public static String normalize(String text) {
        if (text == null) return "general";
        String t = text.trim().toLowerCase(Locale.US);
        if (t.length() == 0) return "general";
        if (isPoiCode(t)) return t;
        if (t.contains("mosque") || t.contains("masjid") || t.contains("muslim")) return "mosque";
        if (t.contains("hospital") || t.contains("clinic")) return "hospital";
        if (t.contains("pharmacy") || t.contains("chemist")) return "pharmacy";
        if (t.contains("fuel") || t.contains("petrol") || t.contains("gas")) return "fuel";
        if (t.contains("restaurant") || t.contains("cafe") || t.contains("food")) return "food";
        if (t.contains("bank") || t.contains("atm")) return "bank";
        if (t.contains("school") || t.contains("college") || t.contains("university")) return "school";
        if (t.contains("market") || t.contains("shop") || t.contains("store")) return "market";
        if (t.contains("hotel") || t.contains("hostel") || t.contains("lodging")) return "hotel";
        if (t.contains("police")) return "police";
        if (t.contains("home") || t.contains("house")) return "home";
        if (t.contains("attraction") || t.contains("viewpoint") || t.contains("park")) return "attraction";
        if (t.contains("church") || t.contains("temple") || t.contains("worship")) return "worship";
        return "general";
    }
}
