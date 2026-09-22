package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Diplomacy.Threshold;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.Utils.EconomicImpact;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.OpinionColourMapper;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsObligation;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsService;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class RelationCreator {
	public void addThreshold(List<String> lore, Threshold h) {
		lore.add(" ");
		String plus = "";
		if(h.getOpinion() > 0) plus = "+";
		if(h.isMutual()) {
			lore.add(StringFormatter.formatHex("#a39ba8Requires opinion "+h.getFormattedShort()+OpinionColourMapper.getOpinionColor(h.getOpinion())+" "+plus+h.getOpinion()+"#a39ba8 (mutual)"));
		} else {
			lore.add(StringFormatter.formatHex("#a39ba8Requires opinion "+h.getFormattedShort()+OpinionColourMapper.getOpinionColor(h.getOpinion())+" "+plus+h.getOpinion()));
		}
	}
	
	public ItemStack createWarButton(Faction target, Faction origin) {
		RelationType r = RelationLoader.getType("war");
		if(r == null) return new ItemStack(Material.AIR, 1);
		ItemStack i = IconGetter.getIcon("war");
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#d42300§lDeclare War"));
		List<String> lore = new ArrayList<>();
		if(r.hasThreshold()) {
			addThreshold(lore, r.getThreshold());
			lore.add("");
			if (Cache.warRequireDeclareCode) {
				lore.add("§4Only click this if you have");
				lore.add("§4an approved War Ticket in the discord!");
				lore.add("§7You will be asked for the code in chat.");
			} else {
				lore.add("§7Select a war goal.");
			}
		}
		lore.add(" ");
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createRelationItem(Faction target, Faction origin) {
		ItemStack i = target.getBanner();
		Relation r = origin.getRelation(target.getId());
		Relation ofR = target.getRelation(origin.getId());
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(target.getName());
		List<String> lore = new ArrayList<String>();
		if(r.getType().isVisible()) lore.add(r.getType().getFull());
		lore.add(" ");
		if(r.getType().equals(ofR.getType())) {
			lore.add(StringFormatter.formatHex("#d4bb98§lRelation: "+r.getType().getName()+" #a39ba8(mutual)"));
		} else {
			lore.add(StringFormatter.formatHex("#d4bb98§lRelation: "+r.getType().getName()+" #a39ba8(outgoing)"));
			lore.add(StringFormatter.formatHex("#d4bb98§lRelation: "+ofR.getType().getName()+" #a39ba8(incoming)"));
		}
		if(target.getDiplomacyHandler().hasTradeRelation(origin.getId())) {
			lore.add(StringFormatter.formatHex("#a89977Trade: "+target.getDiplomacyHandler().getTradeRelation(origin.getId()).getName()));
		} else {
			lore.add(StringFormatter.formatHex("#a89977Trade: #59795fNone"));
		}
		if(target.getDiplomacyHandler().hasTreatyRelation(origin.getId())) {
			lore.add(StringFormatter.formatHex("#a89977Treaty: "+target.getDiplomacyHandler().getTreatyRelation(origin.getId()).getName()));
		} else {
			lore.add(StringFormatter.formatHex("#a89977Treaty: #59795fNone"));
		}
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#a39ba8Our opinion of them: "+OpinionColourMapper.getOpinionColor(r.getOpinion())+r.getOpinion()));
		lore.add(StringFormatter.formatHex("#a39ba8Our attitude towards them: "+r.getAttitude().getName()));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#a39ba8Their opinion of us: "+OpinionColourMapper.getOpinionColor(ofR.getOpinion())+ofR.getOpinion()));
		lore.add(StringFormatter.formatHex("#a39ba8Their attitude towards us: "+ofR.getAttitude().getName()));
		lore.add(" ");
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createDiplomacyListFactionItem(Faction viewed, Faction other, boolean clickable) {
		ItemStack i = other.getBanner();
		if (i == null || i.getType() == Material.AIR) {
			i = new ItemStack(Material.WHITE_BANNER, 1);
		} else {
			i = i.clone();
		}
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(other.getName());
		List<String> lore = new ArrayList<>();
		Relation r = viewed.getRelation(other.getId());
		if (r != null && r.getType() != null) {
			lore.add(StringFormatter.formatHex("#d4bb98Relation: " + r.getType().getName()));
		}
		if (viewed.getDiplomacyHandler().hasTradeRelation(other.getId())) {
			lore.add(StringFormatter.formatHex("#a89977Trade: " + viewed.getDiplomacyHandler().getTradeRelation(other.getId()).getName()));
		}
		if (viewed.getDiplomacyHandler().hasTreatyRelation(other.getId())) {
			lore.add(StringFormatter.formatHex("#a89977Treaty: " + viewed.getDiplomacyHandler().getTreatyRelation(other.getId()).getName()));
		}
		if (clickable) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#50e846Click to open diplomacy"));
		}
		m.setLore(lore);
		NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
		m.getPersistentDataContainer().set(id, PersistentDataType.STRING, other.getId());
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createDiplomacySeparatorItem() {
		ItemStack i = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(" ");
		m.setLore(new ArrayList<>());
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createNoOfficialRelationsItem() {
		ItemStack i = new ItemStack(Material.MAP, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#a89977No official relations"));
		m.setLore(new ArrayList<>());
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createWarReparationsItem(Faction origin, Faction target) {
		ItemStack i = new ItemStack(Material.GOLD_INGOT, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#d4bb98§lWar Reparations"));
		List<String> lore = new ArrayList<>();
		WarReparationsObligation paying = WarReparationsService.findObligation(origin, target);
		WarReparationsObligation receiving = WarReparationsService.findObligation(target, origin);
		if (paying == null && receiving == null) {
			lore.add(StringFormatter.formatHex("#a89977No active war reparations"));
		} else {
			if (paying != null) {
				lore.add(StringFormatter.formatHex("#c74c3fPaying "+target.getName()));
				lore.add(StringFormatter.formatHex("#a89977"+Formatter.formatDouble(paying.getIncomePercent())+"% of main guild income"));
				lore.add(StringFormatter.formatHex("#a89977"+paying.getDaysRemaining()+" day(s) remaining"));
			}
			if (receiving != null) {
				if (paying != null) {
					lore.add(" ");
				}
				lore.add(StringFormatter.formatHex("#87d65cReceiving from "+target.getName()));
				lore.add(StringFormatter.formatHex("#a89977"+Formatter.formatDouble(receiving.getIncomePercent())+"% of their main guild income"));
				lore.add(StringFormatter.formatHex("#a89977"+receiving.getDaysRemaining()+" day(s) remaining"));
			}
		}
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#7a7a7aBased on internal taxable income"));
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createAttitudeItem(Attitude a) {
		return createAttitudeItem(a, null, null, false);
	}

	public ItemStack createAttitudeItem(Attitude a, Faction origin, Faction target, boolean picker) {
		ItemStack i = new ItemStack(Material.EMERALD, 1);
		if(IconGetter.hasIcon(a.getId())) {
			i = IconGetter.getIcon(a.getId());
		}
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#d4bb98§lAttitude: "+a.getName()));
		List<String> lore = new ArrayList<String>();
		lore.add(" ");
		if(a.getTarget() > 0) {
			lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(a.getTarget())+"+"+a.getTarget()));
		} else {
			lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(a.getTarget())+a.getTarget()));
		}
		if (origin != null && target != null) {
			double cost = RelationManager.getDiplomaticCost(origin, target, a);
			if (cost > 0) {
				lore.add(StringFormatter.formatHex("#a89977Diplomatic Cost: #56ccf2"+Formatter.formatDouble(cost)+" Diplomatic Capacity"));
			}
			if (a.hasRecieveModifiers()) {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#a39ba8We recieve modifiers§e:"));
				for (FactionModifier template : a.getRecieveModifiers()) {
					FactionModifier tagged = new FactionModifier(target, template);
					lore.add("§7- "+tagged.getString(origin));
				}
			}
		}
		lore.add(" ");
		if (picker && origin != null && target != null) {
			Attitude current = origin.getRelation(target.getId()).getAttitude();
			boolean same = current != null && current.getId().equalsIgnoreCase(a.getId());
			double oldCost = RelationManager.getDiplomaticCost(origin, target, current);
			double newCost = RelationManager.getDiplomaticCost(origin, target, a);
			if (!same && origin.getDiplomacyHandler().getAvailableCapacity() < newCost - oldCost) {
				lore.add(StringFormatter.formatHex("#d4bb98You lack diplomatic capacity for this attitude!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			} else if (same) {
				lore.add(StringFormatter.formatHex("#28ed70Current"));
				m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
				m.addEnchant(Enchantment.UNBREAKING, 1, true);
			} else {
				lore.add(StringFormatter.formatHex("#28ed70Click to change"));
			}
		} else {
			lore.add(StringFormatter.formatHex("#28ed70Click to change"));
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createRelationTypeItem(RelationType t, Faction target, Faction origin, boolean full) {
		ItemStack i = new ItemStack(Material.PAPER, 1);
		if(IconGetter.hasIcon(t.getId())) {
			i = IconGetter.getIcon(t.getId());
		}
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#d4bb98§lRelation: "+t.getName()));
		List<String> lore = new ArrayList<String>();
		if(t.hasLimit()) {
			lore.add(StringFormatter.formatHex("#a89977Current: #59795f"+RelationManager.getRelationCount(origin, t)+"/"+t.getLimit()));
		}
		double ourCost = RelationManager.getDiplomaticCost(origin, target, t);
		double theirCost = t.isMutual() ? RelationManager.getDiplomaticCost(target, origin, t.getLink()) : 0;
		if(ourCost > 0) {
			lore.add(StringFormatter.formatHex("#a89977Diplomatic Cost "+(theirCost > 0 ? "§7(us)#a89977" : "")+": #56ccf2"+Formatter.formatDouble(ourCost)+" Diplomatic Capacity"));
		}
		if(theirCost > 0) {
			lore.add(StringFormatter.formatHex("#a89977Diplomatic Cost §7(them)#a89977: #56ccf2"+Formatter.formatDouble(theirCost)+" Diplomatic Capacity"));
		}
		if(t.isVisible()) {
			lore.add(" ");
			if(t.getTarget() > 0) {
				lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(t.getTarget())+"+"+t.getTarget()));
			} else {
				lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(t.getTarget())+t.getTarget()));
			}
		}	
		if(full) {
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.getId());
			if(t.isMutual()) {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#8e50baRequires Mutual Agreement"));
				lore.add(StringFormatter.formatHex("#a39ba8(60s request)"));
			}
			if(t.isVassalage()) {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#d4bb98This is a "+t.getName()+"#d4bb98/"+t.getLink().getName()+" #d4bb98relationship"));
			}
			if(t.hasThreshold()) {
				addThreshold(lore, t.getThreshold());
			}
		}
		if(t.hasRecieveModifiers()) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#a39ba8We recieve modifiers§e:"));
			for(FactionModifier mod : t.getRecieveModifiers()) {
				lore.add("§7- "+new FactionModifier(target, mod).getString(origin));
			}
		}
		if(t.hasGiveModifiers()) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#a39ba8They recieve modifiers§e:"));
			for(FactionModifier mod : t.getGiveModifiers()) {
				lore.add("§7- "+new FactionModifier(origin, mod).getString(target));
			}
		}
		lore.add(" ");
		if(full) {
			RelationType current = origin.getRelation(target.getId()).getType();
			if(current.hasLock()) {
				lore.add(StringFormatter.formatHex("#d4bb98You have the relation "+current.getName()+" #d4bb98which you cannot change freely!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			} else if(origin.getDiplomacyHandler().getAvailableCapacity() < ourCost && !current.equals(t) || target.getDiplomacyHandler().getAvailableCapacity() < theirCost && !current.equals(t.getLink())) {
				if(origin.getDiplomacyHandler().getAvailableCapacity() < ourCost && !current.equals(t)) 
					lore.add(StringFormatter.formatHex("#d4bb98You lack diplomatic capacity for this relation!"));
				if(target.getDiplomacyHandler().getAvailableCapacity() < theirCost && !current.equals(t.getLink())) 
					lore.add(StringFormatter.formatHex("#d4bb98They lack diplomatic capacity for this relation!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			} else {
				RelationType linked = t.getLink() != null ? t.getLink() : RelationLoader.getDefaultType();
				current = target.getRelation(origin.getId()).getType();
				if(RelationManager.reverseChange(target, origin, t)) {
					lore.add(StringFormatter.formatHex("#ba3439Notice!"));
					lore.add(StringFormatter.formatHex("#d4bb98Since we have a "+target.getRelation(origin.getId()).getType().getName()+"#d4bb98/"+origin.getRelation(target.getId()).getType().getName()+" #d4bb98relationship"));
					RelationType link = RelationLoader.getDefaultType();
					if(t.isMutual() || t.hasLink()) {
						link = linked;
					}
					if(link.isDefault()) {
						lore.add(StringFormatter.formatHex("#d4bb98Changing would reset their relationship with us to "+link.getName()));
					} else {
						lore.add(StringFormatter.formatHex("#d4bb98Changing would set their relationship with us to "+link.getName()));
					}	
					lore.add(" ");
				}
				if(origin.getRelation(target.getId()).getType().equals(t)) {
					lore.add(StringFormatter.formatHex("#28ed70Current"));
					m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					m.addEnchant(Enchantment.UNBREAKING, 1, true);
				} else if(t.isMutual()) {
					lore.add(StringFormatter.formatHex("#28ed70Click to request"));
				} else {
					lore.add(StringFormatter.formatHex("#28ed70Click to change"));
				}
			}
		} else {
			lore.add(StringFormatter.formatHex("#28ed70Click for more information"));
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createNoTradeAgreementItem() {
		ItemStack i = new ItemStack(Material.PAPER, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#b6aa90No Trade Agreement"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#a89977This faction has no trade agreement with us"));
		lore.add("");
		lore.add(StringFormatter.formatHex("#28ed70Click to view options"));
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createTradeAgreementTypeItem(Player p, RelationType t, Faction target, Faction origin, boolean full) {
		ItemStack i = new ItemStack(Material.PAPER, 1);
		if(IconGetter.hasIcon(t.getId())) {
			i = IconGetter.getIcon(t.getId());
		}
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex(t.getName()));
		List<String> lore = new ArrayList<String>();
		double ourCost = RelationManager.getDiplomaticCost(origin, target, t);
		double theirCost = t.isMutual() ? RelationManager.getDiplomaticCost(target, origin, t.getLink()) : 0;
		if(ourCost > 0) {
			lore.add(StringFormatter.formatHex("#a89977Diplomatic Cost "+(theirCost > 0 ? "§7(us)#a89977" : "")+": #56ccf2"+Formatter.formatDouble(ourCost)+" Diplomatic Capacity"));
		}
		if(theirCost > 0) {
			lore.add(StringFormatter.formatHex("#a89977Diplomatic Cost §7(them)#a89977: #56ccf2"+Formatter.formatDouble(theirCost)+" Diplomatic Capacity"));
		}
		if(t.isVisible()) {
			lore.add(" ");
			if(t.getTarget() > 0) {
				lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(t.getTarget())+"+"+t.getTarget()));
			} else {
				lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(t.getTarget())+t.getTarget()));
			}
		}	
		if(full) {
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.getId());
			if(t.isMutual()) {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#8e50baRequires Mutual Agreement"));
				lore.add(StringFormatter.formatHex("#a39ba8(60s request)"));
			}
			if(t.hasThreshold()) {
				addThreshold(lore, t.getThreshold());
			}
		}
		if(t.hasTradeEffectsUs()) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#a39ba8Our Guilds in Their Territory§e:"));
			for(FactionModifier mod : t.getTradeEffectsUs()) {
				lore.add("§7- "+mod.getString());
			}
		}
		if(t.hasTradeEffectsThem()) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#a39ba8Their Guilds in Our Territory§e:"));
			for(FactionModifier mod : t.getTradeEffectsThem()) {
				lore.add("§7- "+mod.getString());
			}
		}
		lore.add(" ");
		if(full) {
			RelationType current = origin.getDiplomacyHandler().getTradeRelation(target.getId());
			if(current != null && current.hasLock()) {
				lore.add(StringFormatter.formatHex("#d4bb98You have the agreement "+current.getName()+" #d4bb98which you cannot change freely!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			} else if(current != null && (origin.getDiplomacyHandler().getAvailableCapacity() < ourCost && !current.equals(t) || target.getDiplomacyHandler().getAvailableCapacity() < theirCost && !current.equals(t.getLink()))) {
				if(origin.getDiplomacyHandler().getAvailableCapacity() < ourCost && !current.equals(t)) 
					lore.add(StringFormatter.formatHex("#d4bb98You lack diplomatic capacity for this relation!"));
				if(target.getDiplomacyHandler().getAvailableCapacity() < theirCost && !current.equals(t.getLink())) 
					lore.add(StringFormatter.formatHex("#d4bb98They lack diplomatic capacity for this relation!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			} else {
				if(current != null &&  current.equals(t)) {
					lore.add(StringFormatter.formatHex("#28ed70Current"));
					lore.add(StringFormatter.formatHex("#28ed70Click to end agreement"));
					m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					m.addEnchant(Enchantment.UNBREAKING, 1, true);
					if(full) EconomicImpact.applyTradeAgreementChange(lore, p, origin, target, null);
				} else if(origin.getDiplomacyHandler().getAvailableCapacity() > ourCost && target.getDiplomacyHandler().getAvailableCapacity() > theirCost) {
					if(t.isMutual()) {
						lore.add(StringFormatter.formatHex("#28ed70Click to request agreement"));
						if(full) EconomicImpact.applyTradeAgreementChange(lore, p, origin, target, t);
					} else {
						lore.add(StringFormatter.formatHex("#28ed70Click to set"));
						if(full) EconomicImpact.applyTradeAgreementChange(lore, p, origin, target, t);
					}
				} else {
					if(origin.getDiplomacyHandler().getAvailableCapacity() < ourCost) 
						lore.add(StringFormatter.formatHex("#d4bb98You lack diplomatic capacity for this relation!"));
					if(target.getDiplomacyHandler().getAvailableCapacity() < theirCost) 
						lore.add(StringFormatter.formatHex("#d4bb98They lack diplomatic capacity for this relation!"));
					lore.add(" ");
					lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
				}
			}
		} else {
			lore.add(StringFormatter.formatHex("#28ed70Click for more information"));
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createNoTreatyItem() {
		ItemStack i = new ItemStack(Material.PAPER, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#b6aa90No Treaty"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#a89977This faction has no treaty with us"));
		lore.add("");
		lore.add(StringFormatter.formatHex("#28ed70Click to view options"));
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createTreatyTypeItem(Player p, RelationType t, Faction target, Faction origin, boolean full) {
		ItemStack i = new ItemStack(Material.PAPER, 1);
		if(IconGetter.hasIcon(t.getId())) {
			i = IconGetter.getIcon(t.getId());
		}
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex(t.getName()));
		List<String> lore = new ArrayList<String>();
		double ourCost = RelationManager.getDiplomaticCost(origin, target, t);
		double theirCost = t.isMutual() ? RelationManager.getDiplomaticCost(target, origin, t.getLink()) : 0;
		if(ourCost > 0) {
			lore.add(StringFormatter.formatHex("#a89977Diplomatic Cost "+(theirCost > 0 ? "§7(us)#a89977" : "")+": #56ccf2"+Formatter.formatDouble(ourCost)+" Diplomatic Capacity"));
		}
		if(theirCost > 0) {
			lore.add(StringFormatter.formatHex("#a89977Diplomatic Cost §7(them)#a89977: #56ccf2"+Formatter.formatDouble(theirCost)+" Diplomatic Capacity"));
		}
		if(t.isVisible()) {
			lore.add(" ");
			if(t.getTarget() > 0) {
				lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(t.getTarget())+"+"+t.getTarget()));
			} else if(t.getTarget() != 0) {
				lore.add(StringFormatter.formatHex("#a89977This modifies the opinion target by: "+OpinionColourMapper.getOpinionColor(t.getTarget())+t.getTarget()));
			}
		}
		if(t.blocksWar()) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#d4bb98Blocks declaring war while in effect"));
		}
		if(full) {
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.getId());
			if(t.isMutual()) {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#8e50baRequires Mutual Agreement"));
				lore.add(StringFormatter.formatHex("#a39ba8(60s request)"));
			}
			if(t.hasThreshold()) {
				addThreshold(lore, t.getThreshold());
			}
		}
		lore.add(" ");
		if(full) {
			RelationType current = origin.getDiplomacyHandler().getTreatyRelation(target.getId());
			if(current != null && current.hasLock()) {
				lore.add(StringFormatter.formatHex("#d4bb98You have the treaty "+current.getName()+" #d4bb98which you cannot change freely!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			} else if(!t.isClearTreaty() && current != null && (origin.getDiplomacyHandler().getAvailableCapacity() < ourCost && !current.equals(t) || target.getDiplomacyHandler().getAvailableCapacity() < theirCost && !current.equals(t.getLink()))) {
				if(origin.getDiplomacyHandler().getAvailableCapacity() < ourCost && !current.equals(t)) 
					lore.add(StringFormatter.formatHex("#d4bb98You lack diplomatic capacity for this relation!"));
				if(target.getDiplomacyHandler().getAvailableCapacity() < theirCost && !current.equals(t.getLink())) 
					lore.add(StringFormatter.formatHex("#d4bb98They lack diplomatic capacity for this relation!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			} else if(t.isClearTreaty()) {
				if(current == null) {
					lore.add(StringFormatter.formatHex("#28ed70Current"));
				} else {
					lore.add(StringFormatter.formatHex("#28ed70Click to clear treaty"));
				}
			} else if(current != null && current.equals(t)) {
				lore.add(StringFormatter.formatHex("#28ed70Current"));
				m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
				m.addEnchant(Enchantment.UNBREAKING, 1, true);
			} else if(origin.getDiplomacyHandler().getAvailableCapacity() >= ourCost && target.getDiplomacyHandler().getAvailableCapacity() >= theirCost) {
				if(t.isMutual()) {
					lore.add(StringFormatter.formatHex("#28ed70Click to request"));
				} else {
					lore.add(StringFormatter.formatHex("#28ed70Click to set"));
				}
			} else {
				if(origin.getDiplomacyHandler().getAvailableCapacity() < ourCost) 
					lore.add(StringFormatter.formatHex("#d4bb98You lack diplomatic capacity for this relation!"));
				if(target.getDiplomacyHandler().getAvailableCapacity() < theirCost) 
					lore.add(StringFormatter.formatHex("#d4bb98They lack diplomatic capacity for this relation!"));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#ba3439Unavailable"));
			}
		} else {
			lore.add(StringFormatter.formatHex("#28ed70Click for more information"));
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
}
