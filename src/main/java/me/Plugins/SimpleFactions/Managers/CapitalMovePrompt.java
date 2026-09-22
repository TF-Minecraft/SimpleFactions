package me.Plugins.SimpleFactions.Managers;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.settlement.handler.CapitalResult;

public class CapitalMovePrompt implements Listener {
    private static final Map<Player, CapitalMovePending> pending = new HashMap<>();

    public record CapitalMovePending(
            Faction faction,
            int province,
            String settlementName,
            int provincesLost) {}

    public static void begin(
            Player player,
            Faction faction,
            int province,
            String settlementName,
            int provincesLost) {
        pending.put(player, new CapitalMovePending(faction, province, settlementName, provincesLost));
        FactionManager.getInv().confirming.put(player, faction);
        FactionManager.getInv().confirmCapitalMoveView(player, provincesLost);
    }

    public static void handleConfirm(Player player, boolean confirmed) {
        CapitalMovePending state = pending.remove(player);
        FactionManager.getInv().confirming.remove(player);
        player.closeInventory();
        if (state == null) {
            return;
        }
        if (!confirmed) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            return;
        }
        applyFactionCapitalMove(player, state.faction(), state.province(), state.settlementName());
    }

    public static void applyFactionCapitalMove(Player player, Faction faction, int claim, String name) {
        CapitalResult result = faction.getSettlementHandler().applyFactionCapital(player, claim, name);
        player.sendMessage(result.getMessage());
        if (!result.isSuccess()) {
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        faction.setCapital(claim);
        faction.getProvinceHandler().revalidateClaims();
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer());
    }
}
