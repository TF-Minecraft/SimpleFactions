package me.Plugins.SimpleFactions.Managers.Holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import me.Plugins.SimpleFactions.enums.SFGUI;

public class SFInventoryHolder implements InventoryHolder {
    private final String id;
    private final SFGUI type;
    private int page;
    private boolean flag;
    private final String secondaryId;

    public SFInventoryHolder(String id, SFGUI type) {
        this.id = id;
        this.type = type;
        this.page = 0;
        this.flag = false;
        this.secondaryId = null;
    }

    public SFInventoryHolder(String id, SFGUI type, int page) {
        this.id = id;
        this.type = type;
        this.page = page;
        this.flag = false;
        this.secondaryId = null;
    }

    public SFInventoryHolder(String id, SFGUI type, int page, boolean flag) {
        this.id = id;
        this.type = type;
        this.page = page;
        this.flag = flag;
        this.secondaryId = null;
    }

    public SFInventoryHolder(String id, SFGUI type, String secondaryId) {
        this.id = id;
        this.type = type;
        this.page = 0;
        this.flag = false;
        this.secondaryId = secondaryId;
    }

    public SFInventoryHolder(String id, SFGUI type, int page, boolean flag, String secondaryId) {
        this.id = id;
        this.type = type;
        this.page = page;
        this.flag = flag;
        this.secondaryId = secondaryId;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public boolean getFlag() {
        return flag;
    }

    public String getId() {
        return id;
    }

    public SFGUI getType() {
        return type;
    }

    public String getSecondaryId() {
        return secondaryId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
