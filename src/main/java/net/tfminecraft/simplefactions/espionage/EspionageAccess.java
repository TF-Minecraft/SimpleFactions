package net.tfminecraft.simplefactions.espionage;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;

/** Recheck membership and viewing permission while a private menu is open. */
public final class EspionageAccess {
    private static final Set<SFGUI> PRIVATE_MENUS = EnumSet.of(
            SFGUI.UPGRADE_VIEW, SFGUI.LEDGER_VIEW,
            SFGUI.MILITARY_VIEW, SFGUI.INSTALLATIONS_VIEW, SFGUI.INSTALLATION_DETAIL_VIEW,
            SFGUI.GOVERNMENT_VIEW, SFGUI.STABILITY_VIEW, SFGUI.COUNCIL_VIEW, SFGUI.COUNCIL_SELECT,
            SFGUI.LAW_VIEW, SFGUI.LAW_SELECT, SFGUI.TAX_VIEW, SFGUI.TAX_VIEW_SPECIFIC,
            SFGUI.SPECIAL_POSITIONS, SFGUI.SPYMASTER_VIEW, SFGUI.SPYMASTER_SETTINGS, SFGUI.SPYMASTER_SELECT);

    // Regiments, training and the vehicle pool. Installation berths are hidden inside their menu.
    private static final Set<SFGUI> COVERT_MENUS = EnumSet.of(SFGUI.MILITARY_VIEW);

    private EspionageAccess() {}

    public static boolean requiresOwn(SFGUI type) {
        return type != null && PRIVATE_MENUS.contains(type);
    }

    /** Covert menus stay guarded even when the rest of a faction is public. */
    public static boolean canView(Player player, Faction faction, SFGUI type) {
        return COVERT_MENUS.contains(type) ? EspionageService.canViewCovert(player, faction)
                : EspionageService.canViewExact(player, faction);
    }

    public static Faction owner(SFInventoryHolder holder) {
        var guild = FactionManager.getGuildByString(holder.getId());
        return guild != null ? guild.getFaction() : FactionManager.getByString(holder.getId());
    }

    public static boolean denied(Player player, SFInventoryHolder holder) {
        if (!requiresOwn(holder.getType())) return false;
        Faction faction = owner(holder);
        if (faction != null && holder.isReported()
                && net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.READABLE.contains(holder.getType())) return false;
        boolean office = holder.getType() == SFGUI.SPECIAL_POSITIONS
                || holder.getType() == SFGUI.SPYMASTER_VIEW
                || holder.getType() == SFGUI.SPYMASTER_SETTINGS || holder.getType() == SFGUI.SPYMASTER_SELECT;
        if (office ? !EspionageService.isOwn(player, faction) : !canView(player, faction, holder.getType())) return true;
        if (holder.getType() == SFGUI.SPYMASTER_SETTINGS) {
            var spymaster = EspionageService.spymaster(faction);
            return spymaster == null || !spymaster.isHolder(player.getUniqueId());
        }
        return holder.getType() == SFGUI.SPYMASTER_SELECT && !faction.isLeader(player.getName());
    }
}
