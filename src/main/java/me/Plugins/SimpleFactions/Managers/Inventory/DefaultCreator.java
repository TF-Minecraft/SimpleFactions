package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.Plugins.SimpleFactions.enums.RankType;
import me.Plugins.TLibs.TLibs;

public class DefaultCreator {
    public static ItemStack createNextPageButton() {
		ItemStack item = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_next_orange");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§aNext Page");
		item.setItemMeta(meta);
		return item;
	}

	public static ItemStack createPreviousPageButton() {
		ItemStack item = TLibs.getItemAPI()
				.getCreator()
				.getItemsAdderItem("mcicons:icon_back_orange");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§cPrevious Page");
		item.setItemMeta(meta);

		return item;
	}
    public static ItemStack createRankButton(RankType t) {
		ItemStack i = new ItemStack(Material.BLAZE_POWDER, 1);
        switch (t) {
            case PRESTIGE:
                i.setType(Material.DIAMOND);
                break;
            case WEALTH:
                i.setType(Material.GOLD_NUGGET);
                break;
            case TRADE_POWER:
                i.setType(Material.CHEST);
                break;
            case INCOME:
                i.setType(Material.EMERALD);
                break;
            case MEMBERS:
                i.setType(Material.PLAYER_HEAD);
                break;
            default:
                break;
        }
		ItemMeta meta = i.getItemMeta();
		if (t == RankType.MEMBERS) {
			meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		}
		meta.setDisplayName("§aCurrently ranking based on §e"+t.toString());
		List<String> lore = new ArrayList<String>();
		lore.add("§7Click to change");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
}
