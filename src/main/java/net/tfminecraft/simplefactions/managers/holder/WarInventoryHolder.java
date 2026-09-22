package net.tfminecraft.simplefactions.managers.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.tfminecraft.simplefactions.enums.SFGUI;

public class WarInventoryHolder implements InventoryHolder {
    private final int id;
    private final SFGUI type;

    public WarInventoryHolder(int id, SFGUI type) {
        this.id = id;
        this.type = type;
    }

    public int getId() {
        return id;
    }
    
    public SFGUI getType() {
    	return type;
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used in this case
    }

}
