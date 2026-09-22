package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.Plugins.SimpleFactions.player.PlayerEconomyManager;
import me.Plugins.SimpleFactions.player.income.PlayerCashflow;
import me.Plugins.SimpleFactions.player.income.PlayerLedger;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleUpkeepProjection;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public final class PlayerLedgerCreator {

    public ItemStack createLedgerBook(Player player) {
        return createLedgerBook(
            PlayerEconomyManager.get().getLedger(player.getUniqueId()),
            player.getUniqueId());
    }

    public ItemStack createLedgerBook(PlayerLedger ledger, UUID playerUuid) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#d6cf69Ledger"));
        meta.setLore(buildLore(ledger, playerUuid));
        item.setItemMeta(meta);
        return item;
    }

    public List<String> buildLore(PlayerLedger ledger) {
        return buildLore(ledger, null);
    }

    public List<String> buildLore(PlayerLedger ledger, UUID playerUuid) {
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#4c5250§oToday's cashflow"));
        lore.add(StringFormatter.formatHex("#4c5250§oResets at the next daily tick"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#4fd945Income"));
        lore.add(StringFormatter.formatHex("#2f3b2f────────────"));
        boolean hasIncome = false;
        for (PlayerCashflow cashflow : PlayerCashflow.values()) {
            double value = ledger.getAmount(cashflow);
            if (value <= 0) {
                continue;
            }
            hasIncome = true;
            lore.add(StringFormatter.formatHex(
                "#cfc7a2• "
                + cashflow.getDisplay()
                + "#d6cf69: #7fbd73"
                + String.format("+%.2f", value)
                + "d"
            ));
        }
        if (!hasIncome) {
            lore.add(StringFormatter.formatHex("#7a706aNo income sources."));
        }
        lore.add("");
        lore.add(StringFormatter.formatHex("#cf493aExpenses"));
        lore.add(StringFormatter.formatHex("#3b2f2f────────────"));
        boolean hasExpenses = false;
        for (PlayerCashflow cashflow : PlayerCashflow.values()) {
            double value = cashflow == PlayerCashflow.VEHICLE_UPKEEP
                    ? VehicleUpkeepProjection.displayVehicleExpense(ledger, playerUuid)
                    : ledger.getAmount(cashflow);
            if (value >= 0) {
                continue;
            }
            hasExpenses = true;
            lore.add(StringFormatter.formatHex(
                "#cfc7a2• "
                + cashflow.getDisplay()
                + "#d6cf69: #cf493a"
                + String.format("%.2f", value)
                + "d"
            ));
        }
        if (!hasExpenses) {
            lore.add(StringFormatter.formatHex("#7a706aNo expenses."));
        }
        lore.add("");
        double net = VehicleUpkeepProjection.displayNetDaily(ledger, playerUuid);
        String netColor = net >= 0 ? "#4fd945" : "#cf493a";
        lore.add(StringFormatter.formatHex("#f2e5c2Net Income"));
        lore.add(StringFormatter.formatHex(
            "#d6cf69Total#7a706a: "
            + netColor
            + String.format("%+.2f", net)
            + "d"
        ));
        return lore;
    }
}
