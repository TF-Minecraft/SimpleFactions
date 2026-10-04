package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lore for the realm headline and the installation confirmation. */
public final class InfrastructureMenuCopy {
    private InfrastructureMenuCopy() {}

    public static String headline(double worth) {
        return "§7Infrastructure is worth about " + signed(worth) + " a day to your realm";
    }

    public static String headlineUnknown() {
        return "§7Infrastructure worth is worked out once a day.";
    }

    public static String calculating() {
        return "§7Working out what this would earn...";
    }

    public static List<String> installationPreview(InfrastructurePreview.InstallationPreview preview) {
        if (preview == null) {
            return List.of(calculating());
        }
        List<String> lines = new ArrayList<>();
        if (preview.infrastructureHere() > 0) {
            lines.add("§a" + signed(preview.infrastructureHere()) + " infrastructure here");
        } else {
            lines.add("§7This adds no infrastructure here");
        }
        lines.add("§7about " + signed(preview.realmPerDay()) + " a day for your realm");
        if (preview.upkeep() > 0) {
            lines.add("§7upkeep " + compact(preview.upkeep()));
        }
        return lines;
    }

    static String signed(double value) {
        String number = compact(Math.abs(value));
        if (value < 0) {
            return "-" + number;
        }
        return "+" + number;
    }

    static String compact(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001) {
            return Long.toString((long) Math.rint(rounded));
        }
        return String.format(Locale.ROOT, "%.2f", rounded);
    }
}
