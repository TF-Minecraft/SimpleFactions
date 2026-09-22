package me.Plugins.SimpleFactions.Managers;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.settlement.handler.CapitalResult;
import me.Plugins.SimpleFactions.Utils.DisplayNameGate;
import me.Plugins.SimpleFactions.Utils.DisplayNameGate.NameOperation;

public class RelocationPrompt implements Listener {
    private static final Map<Player, RelocationPending> pending = new HashMap<>();

    public static class RelocationPending {
        public final Guild guild;
        public final Faction target;
        public final int province;
        public final boolean crossFaction;
        public final double cost;

        public RelocationPending(
                Guild guild,
                Faction target,
                int province,
                boolean crossFaction,
                double cost) {
            this.guild = guild;
            this.target = target;
            this.province = province;
            this.crossFaction = crossFaction;
            this.cost = cost;
        }
    }

    /**
     * @return true if relocation was deferred pending a city name in chat
     */
    public static boolean begin(
            Player player,
            Guild guild,
            Faction target,
            int province,
            boolean crossFaction,
            double cost) {
        if (!target.getSettlementHandler().requiresFoundingName(province)) {
            return false;
        }
        pending.put(player, new RelocationPending(guild, target, province, crossFaction, cost));
        player.sendMessage("§eEnter a name for the new city in chat:");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pending.remove(player) != null && player.isOnline()) {
                    player.sendMessage("§cRelocation timed out");
                }
            }
        }.runTaskLater(SimpleFactions.getInstance(), 20L * 60);
        return true;
    }

    public static boolean completeIntraFactionRelocate(
            Player player,
            Guild guild,
            Faction target,
            int province,
            String settlementName,
            double cost) {
        if (!target.hasProvince(province)) {
            int old = guild.getCapital();
            guild.setCapital(-1, false);
            FactionManager.getMap().claim(player, target, province, true);
            if (!target.hasProvince(province)) {
                guild.setCapital(old);
                player.sendMessage("§cRelocation failed, cannot claim province!");
                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return false;
            }
        }

        CapitalResult result = guild.relocateWithinFaction(player, province, settlementName);
        if (!result.isSuccess()) {
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        guild.getBank().withdraw(cost);
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        return true;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        RelocationPending state = pending.get(player);
        if (state == null) {
            return;
        }

        event.setCancelled(true);
        String name = event.getMessage().trim();
        if (name.isBlank()) {
            player.sendMessage("§cA city name is required to relocate here");
            return;
        }
        if (DisplayNameGate.check(player, NameOperation.SETTLEMENT_FOUND, name, true)
                == DisplayNameGate.Result.NEEDS_CONFIRM) {
            return;
        }
        pending.remove(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (state.crossFaction) {
                    FactionManager.requestRelocation(
                            player, state.guild, state.target, state.province, name);
                } else if (completeIntraFactionRelocate(
                        player, state.guild, state.target, state.province, name, state.cost)) {
                    FactionManager.getInv().guildView(player, state.guild);
                }
            }
        }.runTask(SimpleFactions.getInstance());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer());
    }
}
