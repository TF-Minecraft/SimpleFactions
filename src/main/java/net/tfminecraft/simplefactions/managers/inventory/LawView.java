package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.espionage.EspionageService;
import java.util.List;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

public class LawView {
	public InventoryManager inv;
	
	public LawCreator creator = new LawCreator();

	private static final List<Integer> LAW_SLOTS = List.of(
		10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25
	);
	
	public LawView(InventoryManager inv) {
        this.inv = inv;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void lawView(Player player, Faction f, Inventory i) {
		if (!EspionageService.canViewExact(player, f)) {
            boolean show = i == null;
            if (show) i = ReportedMenus.open(player, f.getId(), SFGUI.LAW_VIEW, 54, "Laws");
            ReportedMenus.laws(i, player, f, inv);
            if (show) player.openInventory(i);
            return;
        }
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.LAW_VIEW), 54, "§7Laws");
		i.clear();
        SFInventoryHolder holder = (SFInventoryHolder) i.getHolder();
        List<LawGroup> groups = f.getLawHandler().getGroupList();
        int lastPage = Math.max(0, (groups.size() - 1) / LAW_SLOTS.size());
        holder.setPage(Math.min(holder.getPage(), lastPage));
        int start = holder.getPage() * LAW_SLOTS.size();
        int end = Math.min(start + LAW_SLOTS.size(), groups.size());
        for(int index = start; index < end; index++) {
            i.setItem(LAW_SLOTS.get(index - start), creator.createLawGroupItem(player, f, groups.get(index)));
        }
        if(holder.getPage() > 0) i.setItem(45, DefaultCreator.createPreviousPageButton());
        if(holder.getPage() < lastPage) i.setItem(52, DefaultCreator.createNextPageButton());
		i.setItem(53, inv.createBackButton(SFGUI.LAW_VIEW));
		if(open) player.openInventory(i);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void lawSelect(Player player, Faction f, LawGroup group, Inventory i) {
		if (!EspionageService.canViewExact(player, f)) { inv.factionView(player, f); return; }
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.LAW_SELECT, group.getId()), 27, "§7Law View");
		i.clear();
        SFInventoryHolder holder = (SFInventoryHolder) i.getHolder();
        List<Law> laws = new java.util.ArrayList<>(group.getLaws().values());
        int lastPage = Math.max(0, (laws.size() - 1) / 24);
        holder.setPage(Math.min(holder.getPage(), lastPage));
        int start = holder.getPage() * 24;
        int end = Math.min(start + 24, laws.size());
        for(int index = start; index < end; index++) {
            i.setItem(index - start, creator.createLawItem(player, f, group, laws.get(index), false));
        }
        if(holder.getPage() > 0) i.setItem(24, DefaultCreator.createPreviousPageButton());
        if(holder.getPage() < lastPage) i.setItem(25, DefaultCreator.createNextPageButton());
		i.setItem(26, inv.createBackButton(SFGUI.LAW_SELECT));
		if(open) player.openInventory(i);
	}

	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
		SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
        if(holder.getType() == SFGUI.LAW_VIEW || holder.getType() == SFGUI.LAW_SELECT) {
            boolean select = holder.getType() == SFGUI.LAW_SELECT;
            int previous = select ? 24 : 45;
            int next = select ? 25 : 52;
            if(e.getSlot() == previous || e.getSlot() == next) {
                e.setCancelled(true);
                Faction faction = FactionManager.getByString(holder.getId());
                if(faction == null) return;
                holder.setPage(holder.getPage() + (e.getSlot() == next ? 1 : -1));
                if(select) {
                    LawGroup group = faction.getLawHandler().getGroup(holder.getSecondaryId());
                    if(group != null) lawSelect(p, faction, group, inventory);
                } else {
                    lawView(p, faction, inventory);
                }
                return;
            }
        }
		if (holder.getType() == SFGUI.LAW_VIEW) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			ItemMeta meta = item.getItemMeta();
			Faction f = FactionManager.getByString(holder.getId());
			if(f == null) return;
			String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(id == null) return;
			LawGroup group = f.getLawHandler().getGroup(id);
			if(group == null) return;
			lawSelect(p, f, group, null);
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		}
	}
}
