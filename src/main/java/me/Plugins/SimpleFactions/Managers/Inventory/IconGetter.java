package me.Plugins.SimpleFactions.Managers.Inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.Plugins.SimpleFactions.Cache;

public class IconGetter {
	public static boolean hasIcon(String s) {
		if(Cache.icons.containsKey(s.toLowerCase())) return true;
		return false;
	}
	
	public static ItemStack getIcon(String s) {
		if(!hasIcon(s)) return null;
		String data = Cache.icons.get(s.toLowerCase());
		String material = data.split("\\.")[0];
		int model = Integer.parseInt(data.split("\\.")[1]);
		ItemStack i = new ItemStack(Material.valueOf(material.toUpperCase()), 1);
		ItemMeta m = i.getItemMeta();
		m.setCustomModelData(model);
		i.setItemMeta(m);
		return i;
	}

	public static ItemStack getIconOrDefault(String id, Material fallback) {
		ItemStack icon = getIcon(id);
		return icon != null ? icon.clone() : new ItemStack(fallback, 1);
	}
}
