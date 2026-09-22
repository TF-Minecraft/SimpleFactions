package net.tfminecraft.simplefactions.managers.inventory;

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
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.LAW_VIEW), 54, "§7Laws");
		i.clear();
		for(int x = 0; x<f.getLawHandler().getGroupList().size(); x++) {
			LawGroup group = f.getLawHandler().getGroupList().get(x);
			i.setItem(LAW_SLOTS.get(x), creator.createLawGroupItem(player, f, group));
		}
		i.setItem(53, inv.createBackButton(SFGUI.LAW_VIEW));
		if(open) player.openInventory(i);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void lawSelect(Player player, Faction f, LawGroup group, Inventory i) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.LAW_SELECT, group.getId()), 27, "§7Law View");
		i.clear();
		int slot = 0;
		for(Law law : group.getLaws().values()) {
			i.setItem(slot, creator.createLawItem(player, f, group, law, false));
			slot++;
		}
		i.setItem(26, inv.createBackButton(SFGUI.LAW_SELECT));
		if(open) player.openInventory(i);
	}

	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
		SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
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
