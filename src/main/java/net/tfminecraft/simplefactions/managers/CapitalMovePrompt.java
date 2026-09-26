package net.tfminecraft.simplefactions.managers;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.handler.CapitalResult;
import net.tfminecraft.simplefactions.utils.Formatter;

public class CapitalMovePrompt implements Listener {
    private static final Map<Player, CapitalMovePending> pending = new HashMap<>();

    public record CapitalMovePending(
            Faction faction,
            int fromCapital,
            int province,
            String settlementName,
            boolean rename,
            double cost) {}

    /** A faction's first capital move or rename is free; later ones cost {@link Cache#capitalMoveCost}. */
    public static double costFor(Faction faction) {
        return faction.getCapitalMoves() == 0 ? 0 : Cache.capitalMoveCost;
    }

    /** Null when the faction bank covers {@code cost}, otherwise why it cannot. */
    public static String checkFunds(Faction faction, double cost) {
        if (cost <= 0) {
            return null;
        }
        Bank bank = faction.getBank();
        if (bank == null) {
            return "§cYour faction needs a bank to pay the " + Formatter.formatMoney(cost)
                    + "d capital move cost";
        }
        if (bank.getWealth() == null || bank.getWealth() < cost) {
            return "§cYour faction bank needs " + Formatter.formatMoney(cost) + "d to move the capital";
        }
        return null;
    }

    /** Opens the confirmation for moving or renaming an existing capital; nothing is charged until Confirm. */
    public static void begin(
            Player player,
            Faction faction,
            int province,
            String settlementName,
            boolean rename) {
        double cost = costFor(faction);
        String funds = checkFunds(faction, cost);
        if (funds != null) {
            player.sendMessage(funds);
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int provincesLost = rename
                ? 0 : faction.getProvinceHandler().previewProvincesLostIfCapitalMoved(province).size();
        pending.put(player, new CapitalMovePending(
                faction, faction.getCapital(), province, settlementName, rename, cost));
        FactionManager.getInv().confirming.put(player, faction);
        FactionManager.getInv().confirmCapitalMoveView(player, faction, rename, cost, provincesLost);
    }

    enum Outcome { CANCELLED, REFUSED, CHANGED }

    public static void handleConfirm(Player player, boolean confirmed) {
        CapitalMovePending state = pending.remove(player);
        FactionManager.getInv().confirming.remove(player);
        player.closeInventory();
        if (state == null) {
            return;
        }
        switch (confirm(player, state, confirmed)) {
            case CANCELLED -> player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            case REFUSED -> player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            case CHANGED -> player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    /** Applies a confirmed change and charges for it. Plays no sounds, so tests can run it without a server. */
    static Outcome confirm(Player player, CapitalMovePending state, boolean confirmed) {
        if (!confirmed) {
            player.sendMessage("§7Capital change cancelled. Nothing was charged.");
            return Outcome.CANCELLED;
        }
        Faction faction = state.faction();
        // The leader or the capital may have changed while the menu was open.
        if (FactionManager.getByLeader(player.getName()) != faction
                || faction.getCapital() != state.fromCapital()) {
            player.sendMessage("§cThe capital changed while you were deciding. Nothing was charged.");
            return Outcome.REFUSED;
        }
        // Charge the price the menu showed, never a different one.
        double cost = state.cost();
        if (costFor(faction) != cost) {
            player.sendMessage("§cThe capital change price changed. Run §e/faction setcapital §cagain.");
            return Outcome.REFUSED;
        }
        String funds = checkFunds(faction, cost);
        if (funds != null) {
            player.sendMessage(funds);
            return Outcome.REFUSED;
        }
        boolean changed = state.rename()
                ? applyRename(player, faction, state.province(), state.settlementName())
                : applyMove(player, faction, state.province(), state.settlementName());
        if (!changed) {
            return Outcome.REFUSED;
        }
        faction.setCapitalMoves(faction.getCapitalMoves() + 1);
        if (cost > 0) {
            faction.getBank().withdraw(cost);
            player.sendMessage("§7Paid §e" + Formatter.formatMoney(cost) + "d §7from the faction bank.");
        } else {
            player.sendMessage("§7That was your free capital change. Later moves and renames cost §e"
                    + Formatter.formatMoney(Cache.capitalMoveCost) + "d§7.");
        }
        return Outcome.CHANGED;
    }

    /** Sets the first capital. Free, and not counted as a move. */
    public static void applyFactionCapitalMove(Player player, Faction faction, int claim, String name) {
        boolean changed = applyMove(player, faction, claim, name);
        player.playSound(player, changed ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    private static boolean applyMove(Player player, Faction faction, int claim, String name) {
        CapitalResult result = faction.getSettlementHandler().applyFactionCapital(player, claim, name);
        player.sendMessage(result.getMessage());
        if (!result.isSuccess()) {
            return false;
        }
        faction.setCapital(claim);
        faction.getProvinceHandler().revalidateClaims();
        return true;
    }

    private static boolean applyRename(Player player, Faction faction, int province, String name) {
        CapitalResult result = faction.getSettlementHandler().rename(province, name, false);
        player.sendMessage(result.getMessage());
        return result.isSuccess();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (pending.remove(event.getPlayer()) != null) {
            FactionManager.getInv().confirming.remove(event.getPlayer());
        }
    }
}
