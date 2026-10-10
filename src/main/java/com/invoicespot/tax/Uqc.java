package com.invoicespot.tax;

import java.util.Locale;
import java.util.Set;

// The unit quantity codes GST returns count goods in. A line's unit counts
// when it is one of them; services, under a SAC code (99…), have none, and
// any other unit is "other".
public final class Uqc {

    public static final String NONE = "NA";
    public static final String OTHER = "OTH";

    private static final Set<String> CODES = Set.of(
            "BAG", "BAL", "BDL", "BKL", "BOU", "BOX", "BTL", "BUN", "CAN", "CBM", "CCM", "CMS", "CTN", "DOZ", "DRM",
            "GGK", "GMS", "GRS", "GYD", "KGS", "KLR", "KME", "LTR", "MLT", "MTR", "MTS", "NOS", "OTH", "PAC", "PCS",
            "PRS", "QTL", "ROL", "SET", "SQF", "SQM", "SQY", "TBS", "TGM", "THD", "TON", "TUB", "UGS", "UNT",
            "YDS");

    private Uqc() {}

    public static String of(String hsnSac, String unit) {
        if (hsnSac != null && hsnSac.startsWith("99")) {
            return NONE;
        }
        String code = unit == null ? "" : unit.trim().toUpperCase(Locale.ROOT);
        return CODES.contains(code) ? code : OTHER;
    }
}
