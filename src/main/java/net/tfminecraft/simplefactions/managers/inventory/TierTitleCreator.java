package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class TierTitleCreator {

	public ItemStack getPageItem(String s, int page) {
		ItemAPI api = TLibs.getItemAPI();
		ItemStack i = api.getCreator().getItemsAdderItem(s);
		ItemMeta m = i.getItemMeta();
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "page");
		m.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, page);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createNoTitleItem() {
		ItemStack i = new ItemStack(Material.RED_CONCRETE);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§cNo titles to give away");
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createTierViewItem(Faction f, Tier t, int amount) {
		ItemStack i = new ItemStack(Material.SHIELD, 1);
		if(IconGetter.hasIcon(t.getId())) {
			i = IconGetter.getIcon(t.getId());
		}
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(t.getName());
		List<String> lore = new ArrayList<>();	
		lore.add(StringFormatter.formatHex("#d4c9aeThere are #28ed70"+amount+" #d4c9aeTitles of this type"));
		m.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.getId());
		i.setItemMeta(m);
		return i;
	}
	
	public List<ItemStack> getSortedTitleItems(Player p, Faction f, List<Title> allTitles) {
		List<Integer> provinces = TitleManager.getProvinces(f);
		List<Title> titles = TitleManager.getTitles(f);
	    return allTitles.stream()
	        .sorted((t1, t2) -> {
	            double p1 = progressRatio(t1, f, provinces, titles);
	            double p2 = progressRatio(t2, f, provinces, titles);
	            return Double.compare(p2, p1); // Descending order
	        })
	        .map(t -> createTitleItem(p, f, t, provinces, titles))
	        .collect(Collectors.toList());
	}

	private double progressRatio(Title t, Faction f, List<Integer> provinces, List<Title> titles) {
		Faction owner = FactionManager.getTitleOwner(t);
		if(owner != null && owner != f) return -1;
	    int needed = t.getNeededAmount();
	    if (needed == 0) return 0;
	    int current = t.getCurrentAmount(f, provinces, titles);
	    return (double) current / needed;
	}
	
	public ItemStack createGrantTitleItem(Player p, Faction f, Title t) {
	    ItemStack i = new ItemStack(Material.GREEN_CONCRETE, 1);

	    ItemMeta m = i.getItemMeta();
	    m.setDisplayName(StringFormatter.formatHex("#d4bb98§l" + t.getName()));

	    List<String> lore = new ArrayList<>();

	    lore.add(StringFormatter.formatHex("#28ed70Click to grant this title to "+f.getName()));
	    m.setLore(lore);

	    NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
	    m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.getId());
	    key = new NamespacedKey(SimpleFactions.plugin, "type");
	    m.getPersistentDataContainer().set(key, PersistentDataType.STRING, "grant");
	    i.setItemMeta(m);

	    return i;
	}
	
	public ItemStack createTitleItem(Player p, Faction f, Title t, List<Integer> provinces, List<Title> titles) {
	    ItemStack i = new ItemStack(Material.RED_CONCRETE, 1);
	    if (f.hasTitle(t)) i.setType(Material.GREEN_CONCRETE);
	    else if (FactionManager.getTitleOwner(t) != null) i.setType(Material.GRAY_CONCRETE);
	    else if (t.canBeCreatedBy(f, provinces, titles, f.getModifier(FactionModifiers.DE_JURE).getAmount())) i.setType(Material.YELLOW_CONCRETE);

	    ItemMeta m = i.getItemMeta();
	    m.setDisplayName(StringFormatter.formatHex("#d4bb98§l" + t.getName()));

	    List<String> lore = new ArrayList<>();

	    if (i.getType().equals(Material.GREEN_CONCRETE)) {
	        lore.add(StringFormatter.formatHex("#3cc7b2Current"));
	    } else if (i.getType().equals(Material.YELLOW_CONCRETE)) {
	        lore.add(StringFormatter.formatHex("#28ed70Click to claim"));
	    } else {
	        lore.add(StringFormatter.formatHex("#9e4c4fUnavailable"));
	    }
	    if(t.getTier().getPrestige() > 0) {
	    	lore.add(""); // Spacer
	    	lore.add(StringFormatter.formatHex("#4793bfPrestige: #6eafba+"+t.getTier().getPrestige()));
	    }

	    // Add dynamic progress info
	    if(t.isComposite()) {
	        lore.add(""); // Spacer
	        t.getGUIString(f, provinces, titles, f.getModifier(FactionModifiers.DE_JURE).getAmount(), lore);
	    } else if(progressRatio(t, f, provinces, titles) > -1){
	        lore.add(""); // Spacer
	        t.getGUIString(f, provinces, titles, f.getModifier(FactionModifiers.DE_JURE).getAmount(), lore);
		}
		if(FactionManager.getTitleOwner(t) != null) {
	    	lore.add(""); // Spacer
	        lore.add(StringFormatter.formatHex("#cab58eOwned by: "+FactionManager.getTitleOwner(t).getName()));
	    }

	    m.setLore(lore);

	    NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
	    m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.getId());
	    key = new NamespacedKey(SimpleFactions.plugin, "type");
	    m.getPersistentDataContainer().set(key, PersistentDataType.STRING, "claim");
	    i.setItemMeta(m);

	    return i;
	}
	
	public int getNewTitleCost(Faction f, Tier t) {
		if(t == null) return 0;
		int amount = 0;
		if(t.canForm()) {
			amount = t.getFormCost();
		}
		/* //Remove the comment out part if you want DE JURE scaling cost
		double deJure = f.getModifier(FactionModifiers.DE_JURE).getAmount();
		deJure = deJure/100.0;
		amount = (int) Math.round(amount*deJure);
		if(amount < 1) amount = 1;
		*/
		return amount;
	}
	
	public ItemStack createNewTitleItem(Faction f, Tier t) {
		ItemStack i = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#c49760Create a new "+t.getName()));
		List<String> lore = new ArrayList<String>();
		int cost = getNewTitleCost(f, TierLoader.getByLevel(t.getTier()));
		int current = 0;
		if(t.getId().equalsIgnoreCase("county")) {
			current = f.getUntitledProvinces().size();
			int percent = (int) Math.round((current * 1.0) / (cost * 1.0) * 100);
			lore.add(StringFormatter.formatHex("#d4c9aeYou need at least #28ed70"+cost+" #d4c9aeuntitled provinces to form a new "+t.getName()+"#d4c9ae!"));
			lore.add(StringFormatter.formatHex("#767a77Currently: #a1aba3" + current + " #535955[" + percent + "%]"));
		} else {
			current = f.getFreeTitles(TierLoader.getByLevel(t.getTier()-1)).size();
			int percent = (int) Math.round((current * 1.0) / (cost * 1.0) * 100);
			lore.add(StringFormatter.formatHex("#d4c9aeYou need at least #28ed70"+cost+" #d4c9aefree titles of the type "+TierLoader.getByLevel(t.getTier()-1).getName()+" #d4c9aeto form a "+t.getName()+"#d4c9ae!"));
			lore.add(StringFormatter.formatHex("#767a77Currently: #a1aba3" + current + " #535955[" + percent + "%]"));
		}
		lore.add("");
		if(current >= cost) {
			lore.add(StringFormatter.formatHex("#28ed70Click to create"));
		} else {
			lore.add(StringFormatter.formatHex("#9e4c4fUnavailable"));
		}
		m.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "tier");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.getId());
		i.setItemMeta(m);
		return i;
	}

	
	public ItemStack createTierItem(Faction f, Tier t, int index) {
		ItemStack i = new ItemStack(Material.RED_CONCRETE, 1);
		if(t.getTier() == f.getTier().getTier()) {
			if(index == f.getTier().getIndex()) {
				i.setType(Material.GREEN_CONCRETE);
			} else {
				i.setType(Material.YELLOW_CONCRETE);
			}
		}
		ItemMeta m = i.getItemMeta();
		if(index == -1) {
			m.setDisplayName(t.getName());
		} else {
			m.setDisplayName(t.getAliases().get(index)+" §7("+t.getName()+"§7)");
		}
		List<String> lore = new ArrayList<>();	
		if(i.getType().equals(Material.GREEN_CONCRETE)) {
			lore.add(StringFormatter.formatHex("#3cc7b2Current"));
		} else if(i.getType().equals(Material.YELLOW_CONCRETE)) {
			lore.add(StringFormatter.formatHex("#28ed70Click to change"));
		} else {
			lore.add(StringFormatter.formatHex("#9e4c4fUnavailable"));
		}
		
		m.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "index");
		m.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, index);
		i.setItemMeta(m);
		return i;
	}
}
