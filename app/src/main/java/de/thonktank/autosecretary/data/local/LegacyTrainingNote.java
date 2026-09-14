package de.thonktank.autosecretary.data.local;

import java.math.BigDecimal;

/** Text-only boundary for retiring stored resistance values during upgrades. */
final class LegacyTrainingNote {
    private LegacyTrainingNote() { }

    static String append(String note, String mode, String unit, Long milli) {
        String text = note == null ? "" : note;
        String load;
        if ("BODYWEIGHT".equals(mode)) load = "Körpergewicht";
        else {
            if (!("EXTERNAL".equals(mode) || "BODYWEIGHT_PLUS".equals(mode)
                    || "ASSISTED_BODYWEIGHT".equals(mode)) || milli == null || milli < 0
                    || !("KG".equals(unit) || "LB".equals(unit))) return text;
            String value = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()
                    .replace('.', ',') + ("LB".equals(unit) ? " lb" : " kg");
            load = "BODYWEIGHT_PLUS".equals(mode) ? "Körpergewicht + " + value
                    : "ASSISTED_BODYWEIGHT".equals(mode) ? "Körpergewicht mit " + value + " Unterstützung"
                    : value;
        }
        String line = "Gewicht: " + load;
        for (String existing : text.split("\n", -1)) if (existing.equals(line)) return text;
        return text + (text.isEmpty() || text.endsWith("\n") ? "" : "\n") + line;
    }
}
