package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Army.ExpandResult;
import me.Plugins.SimpleFactions.Army.LevyEntry;
import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.MilitaryExpansion;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.TLibs.Utils.TimeFormatter;

public class MilitaryCreator {
	public ItemStack createMilitarySummary(Faction f) {
		Military m = f.getMilitary();
		ItemStack i = new ItemStack(Material.IRON_SWORD, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(f.getName()+" §eMilitary");
		List<String> lore = new ArrayList<String>();
		lore.add("§7Total Soldiers: §e"+m.getManpower(true)+"/"+m.getManpower(false)+" §8(Offense/Defense)");
		if(m.getRawTotalUpkeep() != m.getTotalUpkeep()) lore.add("§7Total Upkeep: §e"+Formatter.formatMoney(m.getTotalUpkeep())+"d "+"§8(From §7"+Formatter.formatMoney(m.getRawTotalUpkeep())+"§8)");
		else lore.add("§7Total Upkeep: §e"+Formatter.formatMoney(m.getTotalUpkeep())+"d");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createQueueItem(MilitaryExpansion e, int x, Faction f) {
		ItemStack i = e.getRegiment().getIcon().clone();
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§eExpanding "+e.getRegiment().getName());
		List<String> lore = new ArrayList<String>();
		if(x == 0) {
			lore.add("§7Time Left: §e"+TimeFormatter.formatTime(e.getTimeLeft()));
		} else {
			lore.add(StringFormatter.formatHex("#857e59Queued..."));
		}
		lore.add("§cClick to cancel");
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(
				Keys.QUEUE_CANCEL,
				PersistentDataType.STRING,
				QueueCancelPayload.military(f.getId(), x));
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createRegimentIcon(Faction f, Regiment r) {
		ItemStack i = r.getIcon().clone();
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(r.getName());
		List<String> lore = new ArrayList<String>();
		
		if(!r.isLevy()) {
			if(r.getFreeSlots() > 0) {
				lore.add("§7Current Slots: §e"+r.getCurrentSlots()+" §7("+r.getFreeSlots()+" free)");
			} else {
				lore.add("§7Current Slots: §e"+r.getCurrentSlots());
			}
			if(r.sentToOverlord() > 0) {
				lore.add(StringFormatter.formatHex("#877e7c("+r.sentToOverlord()+" sent as levies)"));
			}
			if(f.getModifier(FactionModifiers.MILITARY_UPKEEP) != null){
				double mod = 1.0 + f.getModifier(FactionModifiers.MILITARY_UPKEEP).getAmount()/100.0;
				lore.add("§7Current Upkeep: §e"+Formatter.formatMoney(r.getTotalUpkeep()*mod)+"d §7("+Formatter.formatMoney(r.getUpkeep()*mod)+"d per slot)");
			} else {
				lore.add("§7Current Upkeep: §e"+Formatter.formatMoney(r.getTotalUpkeep())+"d §7("+Formatter.formatMoney(r.getUpkeep())+"d per slot)");
			}
		} else {
			int total = 0;
			for(LevyEntry e : r.getEntries()) {
				total += e.getAmount();
			}
			lore.add(StringFormatter.formatHex("#d4c9aeTotal Levies: §e"+total));
			if(total > 0) {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#dbb9b2Contributors:"));
				int c = 0;
				for(LevyEntry e : r.getEntries()) {
					if(c == 10) break;
					lore.add(StringFormatter.formatHex("§7- "+e.getFrom().getName()+"§7: #b5acaa"+e.getAmount()));
					c++;
				}
				if(r.getEntries().size()-10 > 0) {
					lore.add(StringFormatter.formatHex("#877e7c§oAnd "+(r.getEntries().size()-10)+" more..."));
				}
			}
		}
		if(r.getDescription().size() > 0) {
			lore.add(" ");
			for(String s : r.getDescription()) {
				lore.add(s);
			}
		}
		ExpandResult expand = f.getMilitary().canExpand(r);
		if (!expand.allowed()) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#c74d32Blocked"));
			lore.add(StringFormatter.formatHex("#877e7c" + expand.reason()));
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createRegimentIncreaseButton(Faction f, Regiment r) {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ExpandResult expand = f.getMilitary().canExpand(r);
		ItemStack i = api.getCreator().getItemsAdderItem("mcicons:icon_up_blue");
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(
				expand.allowed() ? "#baa875Increase "+r.getName() : "#7a706aIncrease "+r.getName()));
		List<String> lore = new ArrayList<String>();
		lore.add("§7Time: §e"+TimeFormatter.formatTime(r.getExpansionTime()));
		if (!expand.allowed()) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#c74d32" + expand.reason()));
		}
		meta.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
		key = new NamespacedKey(SimpleFactions.plugin, "regiment");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, r.getId());
		key = new NamespacedKey(SimpleFactions.plugin, "type");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "increase");
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createRegimentDecreaseButton(Faction f, Regiment r) {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ItemStack i = api.getCreator().getItemsAdderItem("mcicons:icon_down_blue");
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#b83232Decrease "+r.getName()));
		List<String> lore = new ArrayList<String>();
		lore.add("§7Decreasing is instantaneous");
		meta.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
		key = new NamespacedKey(SimpleFactions.plugin, "regiment");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, r.getId());
		key = new NamespacedKey(SimpleFactions.plugin, "type");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "decrease");
		i.setItemMeta(meta);
		return i;
	}
}
