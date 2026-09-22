package me.Plugins.SimpleFactions.Managers.Inventory;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.keys.Keys;

public class TaxView {
    private InventoryManager inv;
    private GovernmentCreator creator = new GovernmentCreator();

    public TaxView(InventoryManager inv) {
        this.inv = inv;
    }

    public void taxView(Player player, Faction f, Inventory i) {
        if(f == null) return;
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.TAX_VIEW), 9, "§7Tax View");
		i.clear();
		int slot = 0;
		for(TaxTarget target : TaxTarget.values()) {
			if(!f.getTaxHandler().canCollectTax(target)) continue;
			i.setItem(slot, creator.createTaxTypeItem(player, f, target, false));
			slot++;
		}
		i.setItem(8, inv.createBackButton(SFGUI.TAX_VIEW));
		if(open) player.openInventory(i);
	}

	public void taxView(Player player, Faction f) {
		taxView(player, f, null);
	}

	public void specificTaxView(Player player, Faction f, TaxTarget target, Inventory i) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.TAX_VIEW_SPECIFIC, target.name()), 54, "§7"+target.getDisplayName());
		i.clear();
		int x = 0;
		if(target == TaxTarget.GUILD_ID) {
			for(Guild g : f.getGuildHandler().getGuilds()) {
				if(g.isBase()) continue;
				i.setItem(x, creator.createSpecificTaxItem(player, f, g.getId(), target));
				if(x >= 53) break;
				x++;
			}
		} else if(target == TaxTarget.VASSAL_ID) {
			for(Faction s : RelationManager.getSubjects(f)) {
				i.setItem(x, creator.createSpecificTaxItem(player, f, s.getId(), target));
				if(x >= 53) break;
				x++;
			}
		} else if(target == TaxTarget.TARIFF_ID) {
			for(Faction fac : FactionManager.factions) {
				if(fac.getId().equalsIgnoreCase(f.getId())) continue;
				if(RelationManager.sameRealm(fac, f)) continue;
				if(x >= 53) break;
				i.setItem(x, creator.createSpecificTaxItem(player, f, fac.getId(), target));
				x++;
			}
		}
		i.setItem(53, inv.createBackButton(SFGUI.TAX_VIEW_SPECIFIC));
		if(open) player.openInventory(i);
	}

    public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		e.setCancelled(true);
        Faction f = FactionManager.getByLeader(p.getName());
        if(f == null) {
            p.closeInventory();
            return;
        }
		SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
		if(holder.getType() == SFGUI.TAX_VIEW) {
			ItemStack item = e.getCurrentItem();
			if(item == null || item.getItemMeta() == null) return;
			ItemMeta meta = item.getItemMeta();
			String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(id == null) return;
			try {
				TaxTarget target = TaxTarget.valueOf(id);
				if(target == TaxTarget.GUILD_ID) {
					specificTaxView(p, f, TaxTarget.GUILD_ID, null);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				} else if(target == TaxTarget.VASSAL_ID) {
					specificTaxView(p, f, TaxTarget.VASSAL_ID, null);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				} else if(target == TaxTarget.TARIFF_ID) {
					specificTaxView(p, f, TaxTarget.TARIFF_ID, null);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
			} catch (Exception ex) {
				// Non-specific tax type, do nothing
			}
		}
	}
}
