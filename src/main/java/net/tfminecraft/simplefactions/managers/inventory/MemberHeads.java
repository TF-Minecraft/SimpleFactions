package net.tfminecraft.simplefactions.managers.inventory;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import net.tfminecraft.simplefactions.identity.Members;

/** Skull faces for members: the head of the account that plays the character. */
public final class MemberHeads {

    private MemberHeads() {}

    public static void apply(ItemMeta meta, String key) {
        if (!(meta instanceof SkullMeta skull)) return;
        Members.accountId(key).ifPresent(id -> skull.setOwningPlayer(Bukkit.getOfflinePlayer(id)));
    }
}
