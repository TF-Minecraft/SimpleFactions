package me.Plugins.SimpleFactions.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class Formatter {
    // Removes all color codes including hex for ID use, then keeps a-z, 0-9, _
    public static String formatId(String i) {
        if (i == null || i.isEmpty()) {
            return "";
        }
        String s = i;

        s = s.replaceAll("(?i)[&§]?#[0-9a-f]{6}", "");
        s = s.replaceAll("§x(§[0-9a-fA-F]){6}", "");
        s = s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
        s = s.replaceAll("\\s+", "_");
        s = s.replaceAll("[^a-zA-Z0-9_]", "");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_|_$", "");

        return s;
    }


    // Formats name for Minecraft display
    public static String formatName(String i) {
        String s = i;

        // If it doesn't start with a color code, add default color
        if (!s.matches("(?i)^(&[0-9a-frk-or]|#[a-fA-F0-9]{6}).*")) {
            s = "#a3a184" + s;
        }

        // Replace legacy color codes (&x) with §
        s = s.replace("&", "§");

        // Replace _ with space
        s = s.replace("_", " ");

        return s;
    }
    public static Double formatDouble(Double d) {
        if (d == null || d.isNaN() || d.isInfinite()) {
            return 0.0; // or any default value you'd prefer
        }
        return round(d, 2);
    }

    public static String formatMoney(double d) {
        return String.format(Locale.US, "%.2f", formatDouble(d));
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
