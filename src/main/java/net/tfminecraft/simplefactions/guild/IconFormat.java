package net.tfminecraft.simplefactions.guild;

import java.util.Locale;

/** Guild icon strings are either {@code material.customModelData} or a TLibs item path. */
public final class IconFormat {
    public enum Kind {
        MATERIAL,
        ITEM_PATH,
        MALFORMED
    }

    // Prefixes ItemCreator.getItemFromPath accepts in this plugin: m, ia, v, and modeled.
    private static final String[] ITEM_PATH_PREFIXES = {
        "m.",
        "ia.",
        "v.",
        "modeled."
    };

    private IconFormat() {}

    public static Kind classify(String icon) {
        if (icon == null || icon.isBlank()) {
            return Kind.MALFORMED;
        }
        String lower = icon.toLowerCase(Locale.ROOT);
        for (String prefix : ITEM_PATH_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return Kind.ITEM_PATH;
            }
        }
        int dot = icon.indexOf('.');
        if (dot <= 0 || dot != icon.lastIndexOf('.')) {
            return Kind.MALFORMED;
        }
        try {
            Integer.parseInt(icon.substring(dot + 1));
        } catch (NumberFormatException e) {
            return Kind.MALFORMED;
        }
        return Kind.MATERIAL;
    }
}
