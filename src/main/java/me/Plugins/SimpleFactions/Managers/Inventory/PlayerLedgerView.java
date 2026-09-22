package me.Plugins.SimpleFactions.Managers.Inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.SFGUI;

public final class PlayerLedgerView {
    private final InventoryManager inventoryManager;
    private final PlayerLedgerCreator creator = new PlayerLedgerCreator();

    public PlayerLedgerView(InventoryManager inventoryManager) {
        this.inventoryManager = inventoryManager;
    }

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, Inventory inventory) {
        boolean openInv = inventory == null;
        if(openInv) {
            inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(player.getUniqueId().toString(), SFGUI.PLAYER_LEDGER_VIEW),
                27,
                "§7Your Ledger"
            );
        }
        inventory.clear();
        for (int slot = 0; slot < 27; slot++) {
            if (slot != 13) {
                inventory.setItem(slot, inventoryManager.getFiller(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        inventory.setItem(13, creator.createLedgerBook(player));
        if(openInv) player.openInventory(inventory);
    }
}
