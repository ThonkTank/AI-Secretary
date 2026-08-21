package de.thonktank.autosecretary.presentation.today;

import java.text.NumberFormat;
import java.util.Locale;

/** Locale-aware reward labels prepared outside Android views. */
public final class RewardTextFormatter {
    private final NumberFormat multiplierFormat;

    public RewardTextFormatter(Locale locale) {
        multiplierFormat = NumberFormat.getNumberInstance(locale == null ? Locale.GERMANY : locale);
        multiplierFormat.setMinimumFractionDigits(0);
        multiplierFormat.setMaximumFractionDigits(1);
        multiplierFormat.setGroupingUsed(false);
    }

    public String multiplier(double value) {
        return multiplierFormat.format(Math.max(1d, value));
    }

    public String breakdown(int baseXp, double multiplier) {
        return Math.max(0, baseXp) + " × " + multiplier(multiplier);
    }
}
