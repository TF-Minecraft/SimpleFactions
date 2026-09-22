package me.Plugins.SimpleFactions.mercenary.stat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanies;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * Company upgrades only touch a player while they fight a battle as a hired
 * mercenary, so every buff is applied on demand and stripped the moment that
 * stops being true.
 */
public final class MercenaryStatService {
    private static final Map<UUID, MercenaryStatPlan> applied = new HashMap<>();

    private static MercenaryBattleGate gate = MercenaryBattleGate.CLOSED;
    private static MercenaryStatApplier applier = new MythicLibStatApplier();
    private static boolean missingLogged;

    private MercenaryStatService() {
    }

    public static void setGate(MercenaryBattleGate newGate) {
        gate = newGate == null ? MercenaryBattleGate.CLOSED : newGate;
    }

    public static void setApplier(MercenaryStatApplier newApplier) {
        applier = newApplier == null ? new MythicLibStatApplier() : newApplier;
    }

    public static void reset() {
        applied.clear();
        gate = MercenaryBattleGate.CLOSED;
        applier = new MythicLibStatApplier();
        missingLogged = false;
    }

    public static MercenaryStatPlan planFor(String player) {
        return planFor(player, FactionManager.getAllGuilds());
    }

    public static MercenaryStatPlan planFor(String player, Collection<Guild> guilds) {
        if (player == null || !gate.isHiredInBattle(player)) {
            return MercenaryStatPlan.EMPTY;
        }
        MercenaryCompany company = MercenaryCompanies.findByMember(player, guilds);
        return MercenaryStatPlan.of(company);
    }

    /** True when the player is carrying company buffs after this call. */
    public static boolean apply(Player player) {
        if (player == null) return false;
        MercenaryStatPlan plan = planFor(player.getName());
        if (plan.isEmpty()) {
            clear(player);
            return false;
        }
        if (!applier.isAvailable()) {
            logMissingOnce();
            return false;
        }
        MercenaryStatPlan current = applied.get(player.getUniqueId());
        if (plan.equals(current)) {
            return true;
        }
        if (current != null) {
            applier.strip(player);
        }
        applier.apply(player, plan);
        applied.put(player.getUniqueId(), plan);
        return true;
    }

    public static void clear(Player player) {
        if (player == null) return;
        applied.remove(player.getUniqueId());
        if (!applier.isAvailable()) return;
        applier.strip(player);
    }

    /** Used when a battle ends: offline participants only need the record dropped. */
    public static void clearParticipants(Collection<UUID> ids) {
        if (ids == null) return;
        for (UUID id : ids) {
            applied.remove(id);
            Player player = playerOrNull(id);
            if (player != null) clear(player);
        }
    }

    public static void clearAll() {
        if (Bukkit.getServer() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clear(player);
            }
        }
        applied.clear();
    }

    public static MercenaryStatPlan appliedTo(Player player) {
        if (player == null) return MercenaryStatPlan.EMPTY;
        return applied.getOrDefault(player.getUniqueId(), MercenaryStatPlan.EMPTY);
    }

    public static boolean isApplied(Player player) {
        return player != null && applied.containsKey(player.getUniqueId());
    }

    private static Player playerOrNull(UUID id) {
        return Bukkit.getServer() == null ? null : Bukkit.getPlayer(id);
    }

    private static void logMissingOnce() {
        if (missingLogged || SimpleFactions.plugin == null) return;
        missingLogged = true;
        SimpleFactions.plugin.getLogger().info(
                "[SimpleFactions] MythicLib or MMOCore missing; mercenary company buffs not applied.");
    }

    public static final class Listener implements org.bukkit.event.Listener {
        @EventHandler
        public void onBattleEnded(BattleEndedEvent event) {
            clearParticipants(event.getParticipantIds());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            clear(event.getPlayer());
        }

        @EventHandler
        public void onDeath(PlayerDeathEvent event) {
            clear(event.getEntity());
        }

        /** A crash mid-battle can leave a stale health modifier behind. */
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            clear(event.getPlayer());
        }
    }
}
