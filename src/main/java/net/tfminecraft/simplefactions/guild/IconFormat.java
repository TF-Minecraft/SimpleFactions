package net.tfminecraft.simplefactions.guild;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

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

    /** A fresh copy of the TLibs item, or plain black dye when the path cannot be built. */
    public static ItemStack itemFromPath(String icon) {
        try {
            ItemStack resolved = TLibs.getItemAPI().getCreator().getItemFromPath(icon);
            if (resolved != null) {
                return resolved.clone();
            }
        } catch (Exception e) {
            // A missing or broken item path still has to produce an icon.
        }
        return new ItemStack(Material.BLACK_DYE, 1);
    }
}
