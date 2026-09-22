package net.tfminecraft.simplefactions.managers.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;

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
