package net.tfminecraft.simplefactions.managers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.settlement.handler.CapitalResult;
import net.tfminecraft.simplefactions.utils.DisplayNameGate;
import net.tfminecraft.simplefactions.utils.DisplayNameGate.NameOperation;

public class RelocationPrompt implements Listener {
    private static final Map<Player, RelocationPending> pending = new ConcurrentHashMap<>();

    public static class RelocationPending {
        public final Guild guild;
        public final Faction target;
        public final int province;
        public final boolean crossFaction;
        public final double cost;
        private final Faction origin;
        private final int fromCapital;
        private final AtomicBoolean submitted = new AtomicBoolean();

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
            this.origin = guild.getFaction();
            this.fromCapital = guild.getCapital();
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
        RelocationPending state = new RelocationPending(guild, target, province, crossFaction, cost);
        pending.put(player, state);
        player.sendMessage("§eEnter a name for the new city in chat:");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pending.remove(player, state) && player.isOnline()) {
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
        if (!guild.isLeader(player) || guild.getFaction() != target) {
            player.sendMessage("§cYour guild changed while you were deciding. Start relocation again.");
            return false;
        }
        if (!Double.isFinite(cost) || cost < 0 || guild.getBank().getWealth() < cost) {
            player.sendMessage("§cCannot afford to relocate");
            return false;
        }
        int old = guild.getCapital();
        if (!target.hasProvince(province)) {
            CapitalResult validation = target.getSettlementHandler()
                    .validateRelocationCapital(player, province, settlementName);
            if (!validation.isSuccess()) {
                player.sendMessage(validation.getMessage());
                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return false;
            }
            guild.setCapital(-1, false);
            FactionManager.getMap().claim(player, target, province, true);
            guild.setCapital(old, false);
            if (!target.hasProvince(province)) {
                player.sendMessage("§cRelocation failed, cannot claim province!");
                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return false;
            }
        }

        CapitalResult result = guild.relocateWithinFaction(player, province, settlementName);
        if (!result.isSuccess()) {
            if (guild.getCapital() != old) guild.setCapital(old, false);
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        guild.getBank().withdraw(cost);
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        return true;
    }

    // Retain Bukkit chat-event ordering and String message semantics for existing integrations.
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        RelocationPending state = pending.get(player);
        if (state == null || !state.submitted.compareAndSet(false, true)) {
            return;
        }

        event.setCancelled(true);
        String name = event.getMessage().trim();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pending.get(player) != state) return;
                if (!player.isOnline()) {
                    pending.remove(player, state);
                    return;
                }
                if (name.isBlank()) {
                    player.sendMessage("§cA city name is required to relocate here");
                    state.submitted.set(false);
                    return;
                }
                if (DisplayNameGate.check(player, NameOperation.SETTLEMENT_FOUND, name, true)
                        == DisplayNameGate.Result.NEEDS_CONFIRM) {
                    state.submitted.set(false);
                    return;
                }
                pending.remove(player, state);
                if (!state.guild.isLeader(player)
                        || state.guild.getFaction() != state.origin
                        || state.guild.getCapital() != state.fromCapital
                        || FactionManager.getGuildByString(state.guild.getId()) != state.guild
                        || FactionManager.getByString(state.target.getId()) != state.target) {
                    player.sendMessage("§cYour guild changed while you were deciding. Start relocation again.");
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
