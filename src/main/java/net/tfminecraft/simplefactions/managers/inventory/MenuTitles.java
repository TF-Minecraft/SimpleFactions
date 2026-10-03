package net.tfminecraft.simplefactions.managers.inventory;

/** Native inventory titles use plain names; item lore retains the guild's configured colours. */
@SuppressWarnings("deprecation")
public final class MenuTitles {
    private MenuTitles() {}
    public static String legacy(String title) {
        return "\u00a77" + org.bukkit.ChatColor.stripColor(title == null ? "" : title).replace("\u00a7", "");
    }
}
