package net.tfminecraft.simplefactions.managers.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.tfminecraft.simplefactions.enums.SFGUI;

public class SFCombinedInventoryHolder implements InventoryHolder {
	private final int warId;
    private final String factionId;
    private final SFGUI type;

    public SFCombinedInventoryHolder(int wid, String fid, SFGUI type) {
    	this.warId = wid;
        this.factionId = fid;
        this.type = type;
    }
    
    public int getWarId() {
    	return warId;
    }

    public String getFactionId() {
        return factionId;
    }
    
    public SFGUI getType() {
    	return type;
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used in this case
    }

}
