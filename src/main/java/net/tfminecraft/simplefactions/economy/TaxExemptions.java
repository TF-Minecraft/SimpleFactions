package net.tfminecraft.simplefactions.economy;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Helpers and staff on duty owe no citizen tax, so the NPC and lore characters they play keep the
 * denars players hand them. The exemption follows {@link #PERMISSION} on the earning character's
 * account while it is online, on whichever character it plays, so it comes and goes with the duty
 * rank and nobody has to remember a command. The first untaxed earning of each character in a
 * server run is logged, so staff can see whose income goes untaxed.
 */
public final class TaxExemptions {
    public static final String PERMISSION = "simplefactions.taxexempt";

    /** Characters whose untaxed earning has been logged since the server started. */
    private static final Set<UUID> logged = ConcurrentHashMap.newKeySet();
    private static volatile Predicate<UUID> permitted = TaxExemptions::onlineWithPermission;

    private TaxExemptions() {}

    /** Whether this earning owes no citizen tax. */
    public static boolean exempt(CharacterRef earner) {
        if (earner == null || !permitted.test(earner.ownerId())) return false;
        if (logged.add(earner.characterId())) log(earner);
        return true;
    }

    /** Test hook: who counts as holding the permission; null restores the online check. */
    static void setPermissionCheck(Predicate<UUID> check) {
        permitted = check == null ? TaxExemptions::onlineWithPermission : check;
    }

    /** Test hook: forget which characters were logged and restore the online check. */
    static void reset() {
        logged.clear();
        permitted = TaxExemptions::onlineWithPermission;
    }

    private static boolean onlineWithPermission(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        return owner != null && owner.hasPermission(PERMISSION);
    }

    private static void log(CharacterRef earner) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null) return;
        String key = Members.key(earner.characterId());
        String account = Members.accountName(key);
        plugin.getLogger().info("[TaxExempt] " + (account == null ? earner.ownerId() : account) + " earned untaxed as "
                + Members.display(key) + " (" + key + ") through " + PERMISSION + "; logged once per character until restart");
    }
}
