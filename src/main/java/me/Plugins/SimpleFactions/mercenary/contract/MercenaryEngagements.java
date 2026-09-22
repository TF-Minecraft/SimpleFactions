package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.PostSettlementPayouts.PlayerUuidLookup;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanies;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * Which companies are on a war side, derived from active contracts rather than
 * stored on {@code Side}. A terminated contract disappears on its own.
 */
public final class MercenaryEngagements {
    private static PlayerUuidLookup uuids = bukkitUuids();

    private MercenaryEngagements() {
    }

    public static void setUuidLookup(PlayerUuidLookup lookup) {
        uuids = lookup == null ? bukkitUuids() : lookup;
    }

    public record Engagement(MercenaryCompany company, MercenaryContract contract) {
        public int promisedSlots() {
            return contract == null ? 0 : contract.getSlots();
        }

        public Faction hirer() {
            return contract == null ? null : contract.getHirer();
        }
    }

    public static PlayerUuidLookup uuidLookup() {
        return uuids;
    }

    public static PlayerUuidLookup bukkitUuids() {
        return name -> {
            if (name == null || Bukkit.getServer() == null) return null;
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) return online.getUniqueId();
            return Bukkit.getOfflinePlayer(name).getUniqueId();
        };
    }

    public static List<Engagement> on(War war, Side side) {
        return on(war, side, FactionManager.getAllGuilds());
    }

    public static List<Engagement> on(War war, Side side, Collection<Guild> guilds) {
        List<Engagement> list = new ArrayList<>();
        if (war == null || side == null || guilds == null) return list;
        for (Guild g : guilds) {
            if (g == null) continue;
            MercenaryCompany company = g.getCompany();
            if (company == null || !company.isFormed()) continue;
            for (MercenaryContract contract : company.getContractHandler().getActive()) {
                Faction hirer = contract.getHirer();
                if (hirer == null || !war.isParticipating(hirer)) continue;
                if (war.getSide(hirer) != side) continue;
                list.add(new Engagement(company, contract));
            }
        }
        return list;
    }

    public static Engagement forPlayer(War war, String player) {
        return forPlayer(war, player, FactionManager.getAllGuilds());
    }

    public static Engagement forPlayer(War war, String player, Collection<Guild> guilds) {
        if (war == null || player == null) return null;
        MercenaryCompany company = MercenaryCompanies.findByMember(player, guilds);
        if (company == null) return null;
        for (MercenaryContract contract : company.getContractHandler().getActive()) {
            Faction hirer = contract.getHirer();
            if (hirer == null || !war.isParticipating(hirer)) continue;
            if (war.getSide(hirer) == null) continue;
            return new Engagement(company, contract);
        }
        return null;
    }

    public static Side sideFor(War war, String player) {
        return sideFor(war, player, FactionManager.getAllGuilds());
    }

    public static Side sideFor(War war, String player, Collection<Guild> guilds) {
        Engagement engagement = forPlayer(war, player, guilds);
        if (engagement == null) return null;
        Faction hirer = engagement.hirer();
        return hirer == null ? null : war.getSide(hirer);
    }

    public static int coveringMembers(Engagement engagement, BattleSide side) {
        return coveringMembers(engagement, side, uuids);
    }

    /**
     * How many of this company's enlisted players are on the battle roster, capped
     * at the slots the contract promised. One number for the join cap, lives, and
     * attendance.
     */
    public static int coveringMembers(
            Engagement engagement, BattleSide side, PlayerUuidLookup uuids) {
        if (engagement == null || engagement.company() == null || side == null) return 0;
        int promised = engagement.promisedSlots();
        if (promised <= 0) return 0;
        int found = 0;
        for (String name : engagement.company().getEnlisted()) {
            UUID id = uuids == null ? null : uuids.uuidOf(name);
            if (id == null) continue;
            if (isOnRoster(side, id)) {
                found++;
                if (found >= promised) return promised;
            }
        }
        return found;
    }

    private static boolean isOnRoster(BattleSide side, UUID id) {
        for (Warband band : side.getBands()) {
            if (band != null && band.hasMember(id)) return true;
        }
        return false;
    }
}
