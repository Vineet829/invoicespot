package com.invoicespot.tax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The states and territories of India as GST codes them, and "96" for a
// place of supply outside India. Codes 25 and 28 are the old Daman and Diu
// and Andhra Pradesh, which older GSTINs still carry.
public final class GstStates {

    public static final String OUTSIDE_INDIA = "96";

    // A union territory without a legislature charges UTGST in place of SGST.
    public record State(String code, String name, boolean unionTerritory) {}

    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        String[][] states = {
            {"01", "Jammu and Kashmir"}, {"02", "Himachal Pradesh"}, {"03", "Punjab"}, {"04", "Chandigarh"},
            {"05", "Uttarakhand"}, {"06", "Haryana"}, {"07", "Delhi"}, {"08", "Rajasthan"},
            {"09", "Uttar Pradesh"}, {"10", "Bihar"}, {"11", "Sikkim"}, {"12", "Arunachal Pradesh"},
            {"13", "Nagaland"}, {"14", "Manipur"}, {"15", "Mizoram"}, {"16", "Tripura"}, {"17", "Meghalaya"},
            {"18", "Assam"}, {"19", "West Bengal"}, {"20", "Jharkhand"}, {"21", "Odisha"},
            {"22", "Chhattisgarh"}, {"23", "Madhya Pradesh"}, {"24", "Gujarat"}, {"25", "Daman and Diu (old)"},
            {"26", "Dadra and Nagar Haveli and Daman and Diu"}, {"27", "Maharashtra"},
            {"28", "Andhra Pradesh (old)"}, {"29", "Karnataka"}, {"30", "Goa"}, {"31", "Lakshadweep"},
            {"32", "Kerala"}, {"33", "Tamil Nadu"}, {"34", "Puducherry"}, {"35", "Andaman and Nicobar Islands"},
            {"36", "Telangana"}, {"37", "Andhra Pradesh"}, {"38", "Ladakh"}, {"97", "Other Territory"},
        };
        for (String[] state : states) {
            NAMES.put(state[0], state[1]);
        }
    }

    // Union territories without a legislature charge UTGST in place of SGST.
    private static final Set<String> UNION_TERRITORIES = Set.of("04", "25", "26", "31", "35", "38");

    private GstStates() {}

    public static boolean isState(String code) {
        return NAMES.containsKey(code);
    }

    // A state, or outside India.
    public static boolean isPlace(String code) {
        return isState(code) || OUTSIDE_INDIA.equals(code);
    }

    public static String name(String code) {
        return OUTSIDE_INDIA.equals(code) ? "Outside India" : NAMES.get(code);
    }

    public static boolean isUnionTerritoryWithoutLegislature(String code) {
        return UNION_TERRITORIES.contains(code);
    }

    // Every state and territory in code order, then outside India.
    public static List<State> places() {
        List<State> places = new ArrayList<>(NAMES.entrySet().stream()
                .map(entry -> new State(
                        entry.getKey(), entry.getValue(), isUnionTerritoryWithoutLegislature(entry.getKey())))
                .toList());
        places.add(new State(OUTSIDE_INDIA, "Outside India", false));
        return places;
    }
}
