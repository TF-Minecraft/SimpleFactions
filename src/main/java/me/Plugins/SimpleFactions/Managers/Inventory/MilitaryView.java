package me.Plugins.SimpleFactions.Managers.Inventory;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Army.ExpandResult;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.keys.Keys;

public class MilitaryView {
	public InventoryManager inv;
	
	public MilitaryCreator creator = new MilitaryCreator();
	
	public MilitaryView(InventoryManager inv) {
		this.inv = inv;
	}
	
	public void militaryView(Inventory i, Player player, Faction f, boolean open) {
		if(open) {
			i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.MILITARY_VIEW), 54, "§7Military View");
		}
		for (int slot = 3; slot <= 8; slot++) {
			i.setItem(slot, new ItemStack(Material.AIR, 1));
		}
		for (int slot = 21; slot <= 26; slot++) {
			i.setItem(slot, new ItemStack(Material.AIR, 1));
		}
		var m = f.getMilitary();
		i.setItem(10, creator.createMilitarySummary(f));
		for(int x = 0; x<m.getRegiments().size(); x++) {
			int slot = x+12;
			Regiment r = m.getRegiments().get(x);
			i.setItem(slot, creator.createRegimentIcon(f, r));
			if(!r.isLevy() && f.getLeader().equalsIgnoreCase(player.getName())) {
				int a = slot-9;
				i.setItem(a, creator.createRegimentIncreaseButton(f, r));
				if(r.getCurrentSlots() > 0) {
					a = slot+9;
					i.setItem(a, creator.createRegimentDecreaseButton(f, r));
				}
			}
		}
		for (int slot = 12 + m.getRegiments().size(); slot <= 44; slot++) {
			i.setItem(slot, new ItemStack(Material.AIR, 1));
		}
		for(int x = 0; x<3; x++) {
			int slot = x+39;
			if(x <m.getQueue().size()) {
				i.setItem(slot, creator.createQueueItem(m.getQueue().get(x), x, f));
			} else {
				i.setItem(slot, new ItemStack(Material.AIR, 1));
			}
			
		}
		i.setItem(53, inv.createBackButton(SFGUI.MILITARY_VIEW));
		if(open) player.openInventory(i);
	}
	
	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		e.setCancelled(true);
		ItemStack item = e.getCurrentItem();
		if (item == null || !item.hasItemMeta()) return;
		ItemMeta m = item.getItemMeta();

		String queuePayload = m.getPersistentDataContainer().get(Keys.QUEUE_CANCEL, PersistentDataType.STRING);
		if (queuePayload != null) {
			if (!(inventory.getHolder() instanceof SFInventoryHolder holder)) return;
			Faction f = FactionManager.getByString(holder.getId());
			if (f == null || !f.getLeader().equalsIgnoreCase(p.getName())) return;
			inv.openQueueCancelConfirm(p, f, queuePayload, "§eCancel queued regiment expansion?");
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			return;
		}

		var key = new org.bukkit.NamespacedKey(SimpleFactions.plugin, "id");
		String factionId = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if(factionId == null) return;
		Faction f = FactionManager.getByString(factionId);
		if(f == null) return;
		
		if(f.getMilitary().getQueue().size() == 3) {
			p.sendMessage("§cQueue is full");
			return;
		}
		
		key = new org.bukkit.NamespacedKey(SimpleFactions.plugin, "regiment");
		String regiment = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if(regiment == null) return;
		Regiment r = f.getMilitary().getRegiment(regiment);
		if(r == null) return;
		
		key = new org.bukkit.NamespacedKey(SimpleFactions.plugin, "type");
		String type = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if(type == null) return;
		if(type.contentEquals("increase")) {
			ExpandResult result = f.getMilitary().canExpand(r);
			if (!result.allowed()) {
				p.sendMessage("§c" + result.reason());
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
			} else {
				p.sendMessage("§eQueued "+r.getName());
				f.getMilitary().enqueue(r);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		} else {
			inv.confirmView(p, f, "regiment", r.getId());
			inv.confirming.put(p, f);
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		}
		militaryView(e.getView().getTopInventory(), p, f, false);
	}
}
