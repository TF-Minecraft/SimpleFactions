package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Diplomacy.DiplomacyHandler;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.RankLoader;
import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.Objects.Modifier;
import me.Plugins.SimpleFactions.Objects.PrestigeRank;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.FactionRanker;
import me.Plugins.SimpleFactions.Utils.HomeSettlementNames;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.Represents;
import me.Plugins.SimpleFactions.Utils.OpinionColourMapper;
import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.SimpleFactions.enums.MenuItemType;
import me.Plugins.SimpleFactions.enums.RankType;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class FactionCreator {
	Formatter format = new Formatter();
	FactionRanker r = new FactionRanker();
	
	public ItemStack createListItem(Player p, Faction f) {
		ItemStack i = new ItemStack(f.getBanner());
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§f"+f.getName());
		List<String> lore = new ArrayList<String>();
		lore.add(f.getRank().getName());
		if(f.getTitles().size() > 0) lore.add(StringFormatter.formatHex("#b84c44§lPrimary Title: #7a706a"+f.getHighestTitle().getName()));
		lore.add(StringFormatter.formatHex("#c45749§lTier: "+f.getTier().getFormattedName()));
		lore.add(StringFormatter.formatHex("#b8ae61Based in: #d4c9ae" + HomeSettlementNames.of(f)));
		int realmSize = TitleManager.getRealmSize(f);
		if(realmSize > 0 && realmSize-f.getProvinces().size() > 0) lore.add(StringFormatter.formatHex("#d4c9aeRealm Size: #7a706a"+realmSize+" #a39ba8("+(realmSize-f.getProvinces().size())+" from subjects)"));
		else if(realmSize > 0) lore.add(StringFormatter.formatHex("#d4c9aeRealm Size: #7a706a"+realmSize));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#9c9775"+f.getRulerTitle()+": #c2bea7"+f.getLeader()));
		lore.add(StringFormatter.formatHex("#b8ae61Ruling System: #d4c9ae"+f.getGovernmentString()));
		lore.add(StringFormatter.formatHex("#b8ae61Culture: #d4c9ae"+f.getCulture()));
		lore.add(StringFormatter.formatHex("#b8ae61Religion: #d4c9ae"+f.getReligion()));
		int subjectMembers = f.getCompleteMemberList().size()-f.getMembers().size();
		lore.add(StringFormatter.formatHex("#b8ae61Members: #7fbd73"+f.getCompleteMemberList().size()+((subjectMembers > 0) ? " #a39ba8("+subjectMembers+" from subjects)" : "")));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#4793bfPrestige: #6eafba"+f.getPrestige()+" #7a706a("+r.getPrestigeRank(f)+")"));
		lore.add(StringFormatter.formatHex("#d1b43fWealth: #ccbb76"+f.getWealth()+"d #7a706a("+r.getWealthRank(f)+")"));
		double prosperity = f.getProsperity();
		if(prosperity > 0) lore.add(StringFormatter.formatHex("#4bb244Prosperity: #4fd945"+f.getProsperity()));
		if(!f.getMembers().contains(p.getName())) {
			Faction origin = FactionManager.getByMember(p.getName());
			if(origin != null) {
				Relation r = origin.getRelation(f.getId());
				Relation ofR = f.getRelation(origin.getId());
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#7fbd73§lDiplomacy:"));
				if(r.getType().equals(ofR.getType())) {
					lore.add(StringFormatter.formatHex("#d4bb98Relation: "+r.getType().getName()+" #a39ba8(mutual)"));
				} else {
					lore.add(StringFormatter.formatHex("#d4bb98Relation: "+r.getType().getName()+" #a39ba8(outgoing)"));
					lore.add(StringFormatter.formatHex("#d4bb98Relation: "+ofR.getType().getName()+" #a39ba8(incoming)"));
				}
				lore.add(StringFormatter.formatHex("#a39ba8Our opinion of them: "+OpinionColourMapper.getOpinionColor(r.getOpinion())+r.getOpinion()));
				lore.add(StringFormatter.formatHex("#a39ba8Their opinion of us: "+OpinionColourMapper.getOpinionColor(ofR.getOpinion())+ofR.getOpinion()));
			}
		}
		List<Guild> guilds = f.getGuildHandler().getGuilds();
		if(guilds.size() > 0){
			lore.add("");
			lore.add(StringFormatter.formatHex("#d6a376Guilds:"));
			for(Guild guild : guilds) {
				lore.add(StringFormatter.formatHex("#bccbd1- "+guild.getName()+" #a39ba8("+guild.getType().getName()+"#a39ba8)"));
			}
		}
		List<Faction> subjects = RelationManager.getSubjects(f);
		if(subjects.size() > 0){
			lore.add("");
			lore.add(StringFormatter.formatHex("#5eadccSubjects:"));
			for(Faction subject : subjects) {
				lore.add(StringFormatter.formatHex("#bccbd1- "+subject.getName()));
			}
		}
		List<Faction> allies = RelationManager.getAllies(f);
		if(allies.size() > 0){
			lore.add("");
			lore.add(StringFormatter.formatHex("#975bbdAllies:"));
			for(Faction ally : allies) {
				lore.add(StringFormatter.formatHex("#bccbd1- "+ally.getName()));
			}
		}
		meta.setLore(lore);
		NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(id, PersistentDataType.STRING, f.getId());
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createDissolveItem(Faction f) {
		ItemStack item = new ItemStack(Material.PRISMARINE_CRYSTALS);
		ItemMeta meta = item.getItemMeta();

		meta.setDisplayName(StringFormatter.formatHex(
			"#c45749§lDissolve Faction"
		));

		List<String> lore = new ArrayList<>();

		lore.add(StringFormatter.formatHex(
			"#d4c9aeThis action will result in the following:"
		));
		lore.add("");

		Faction overlord = f.getOverlord();
		if (overlord != null) {

			// This faction
			lore.add(StringFormatter.formatHex(
				"#b8a58a• " + f.getName() +" §7(#4ecc5eYou§7)"+
				" #d4c9aedissolves and becomes a guild under #e0cfa6" + overlord.getName()
			));
			lore.add("");

			// Guilds
			for (Guild g : f.getGuildHandler().getGuilds()) {
				if (g.isBase()) continue;
				lore.add(StringFormatter.formatHex(
					"#b8a58a• " + g.getName() + " §7(" +g.getType().getName()+ "§7)" +
					" #d4c9aeis transferred to #e0cfa6" + overlord.getName()
				));
			}

			if (!f.getGuildHandler().getGuilds().isEmpty()) {
				lore.add("");
			}

			// Vassals
			for (Faction vassal : f.getSubjects()) {
				lore.add(StringFormatter.formatHex(
					"#b8a58a• " + vassal.getName() +" §7(#4269a8Vassal§7)"+
					" #d4c9aeis transferred to #e0cfa6" + overlord.getName()
				));
			}
		}
		else {

			// Guilds becoming factions
			for (Guild g : f.getGuildHandler().getGuilds()) {
				if (g.isBase()) continue;

				lore.add(StringFormatter.formatHex(
					"#b8a58a• " + g.getName() + " §7(" +g.getType().getName()+ "§7)" +
					" #d4c9aebecomes an independent faction" + (!g.canBeElevated(null) ? "§7(#946538Landless§7)" : "")
				));
			}

			if (f.getGuildHandler().getGuilds().stream().anyMatch(g -> !g.isBase())) {
				lore.add("");
			}

			// Vassals released
			for (Faction vassal : f.getSubjects()) {
				lore.add(StringFormatter.formatHex(
					"#b8a58a• " + vassal.getName() +" §7(#4269a8Vassal§7)"+
					" #d4c9aebecomes independent"
				));
			}

			// Nothing happens case (edge-safe)
			if (f.getGuildHandler().getGuilds().size() <= 1 && f.getSubjects().isEmpty()) {
				lore.add(StringFormatter.formatHex(
					"#9f8f78• #d4c9aeNo other factions or guilds are affected"
				));
			}
		}

		lore.add("");
		lore.add(StringFormatter.formatHex(
			"#a13030§oThis action is permanent and cannot be undone."
		));

		meta.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
		item.setItemMeta(meta);
		return item;
	}
	
	@SuppressWarnings("deprecation")
	public ItemStack createMenuItem(Player p, Faction f, MenuItemType t) {
		ItemStack i = new ItemStack(Material.DIRT, 1);
		if(t.equals(MenuItemType.BANNER)) {
			i = new ItemStack(f.getBanner());
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#d4c9ae§lBanner of "+f.getName()));
			List<String> lore = new ArrayList<String>();
			lore.add(StringFormatter.formatHex("#b8ae61Culture: #d4c9ae"+f.getCulture()));
			lore.add(StringFormatter.formatHex("#b8ae61Religion: #d4c9ae"+f.getReligion()));
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.BANNER_GET)) {
			i = new ItemStack(Material.CHEST, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#82d461Get Banner"));
			List<String> lore = new ArrayList<String>();
			lore.add("§7Click to get a banner");
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.BANNER_RANDOM)) {
			ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
			i = api.getCreator().getItemsAdderItem("mcicons:icon_refresh");
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#9161d4Randomise Banner"));
			List<String> lore = new ArrayList<String>();
			lore.add("§7Click to randomise the banner patterns");
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.LEADER)) {
			i = new ItemStack(Material.PLAYER_HEAD, 1);
			SkullMeta m = (SkullMeta) i.getItemMeta();
			m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
			m.setDisplayName(StringFormatter.formatHex("#9c9775§l"+f.getRulerTitle()+": #c2bea7"+f.getLeader()));
			m.setOwningPlayer(Bukkit.getOfflinePlayer(f.getLeader()));
			List<String> lore = new ArrayList<String>();
			lore.add(StringFormatter.formatHex("#b8ae61Ruling System: #d4c9ae"+f.getGovernmentString()));
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.GOVERNMENT)) {
			i = new ItemStack(Material.PLAYER_HEAD, 1);
			SkullMeta m = (SkullMeta) i.getItemMeta();
			m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
			m.setDisplayName(StringFormatter.formatHex("#93c9a7Government:"));
			m.setOwningPlayer(Bukkit.getOfflinePlayer(f.getLeader()));
			List<String> lore = new ArrayList<String>();
			Government gov = f.getGovernment();
			lore.add(StringFormatter.formatHex("#9c9775§l"+f.getRulerTitle()+": #c2bea7"+f.getLeader()));
			double power = Formatter.formatDouble(gov.getPower());
			double maxPower = Formatter.formatDouble(gov.getMaxPower());
			String powerString = ((power < 0) ? "§c" : "") + power+"/"+((maxPower < 0) ? "§c" : "") + maxPower;
			lore.add(StringFormatter.formatHex("#85c265Administrative Power§7: §e"+powerString+" §7("+(gov.getPowerGain() >= 0 ? "§e+" : "§c") 
					+Formatter.formatDouble(gov.getPowerGain())+"§7/hour)"));
			lore.add(StringFormatter.formatHex("#85c265Stability§7: §e"+gov.getStabilityString()+"%"));
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#b8ae61Ruling System: #d4c9ae"+f.getGovernmentString()));
			lore.add(StringFormatter.formatHex("#b8ae61Leader Elections: "+(gov.hasLeaderElections() ? "#45afc4✔" : "#c74d32✖")));
			if(gov.hasCouncil()) {
				lore.add(StringFormatter.formatHex("#b8ae61Council Size: #d4c9ae"+gov.getCouncil().getCurrentSize()+"/"+gov.getCouncil().getMaxSize()));
				lore.add(StringFormatter.formatHex("#b8ae61Council Elections: "+(gov.hasCouncilElections() ? "#45afc4✔" : "#c74d32✖")));
				lore.add(StringFormatter.formatHex("#45c46f"+gov.getCouncil().getType().getDisplay()));
				if(gov.getCouncil().getCurrentSize() > 0) {
					lore.add(StringFormatter.formatHex("#93c9a7Members:"));
					for(String member : gov.getCouncilMembers()) {
						lore.add(StringFormatter.formatHex("#d4bb98- "+member + " §7("+Represents.represents(f, member)+")"));
					}
				}
			} else {
				lore.add(StringFormatter.formatHex("#b8ae61Has Council: #c74d32✖"));
			}
			m.setLore(lore);
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.WEALTH)) {
			i = new ItemStack(Material.GOLD_NUGGET, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#d1b43fWealth: #ccbb76"+f.getWealth()+"d"));
			List<String> lore = new ArrayList<String>();
			for(Modifier mod : f.getWealthModifiers()) {
				lore.add(StringFormatter.formatHex("#93c9a7+"+mod.getAmount()+"d from "+mod.getType()));
			}
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.PRESTIGE)) {
			i = new ItemStack(Material.DIAMOND, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#4793bfPrestige: #6eafba"+f.getPrestige()));
			List<String> lore = new ArrayList<String>();
			for(Modifier mod : f.getPrestigeModifiers()) {
				lore.add(StringFormatter.formatHex("#93c9a7+"+mod.getAmount()+" from "+mod.getType()));
			}
			lore.add(StringFormatter.formatHex("#b69f5a────────────"));
			lore.add(StringFormatter.formatHex("#7fbd73Current Rank: "+f.getRank().getName()));
			if(f.getRank().hasModifiers()) {
				for(FactionModifier mod : f.getRank().getModifiers()) {
					if(mod.getAmount() != 0.0) lore.add(StringFormatter.formatHex("#d4c9ae- "+mod.getString()));
				}
			}
			lore.add(StringFormatter.formatHex("#b69f5a────────────"));
			lore.add("");
			if(f.getRank().getLevel() < RankLoader.getRanks().size()) {
				lore.add(StringFormatter.formatHex("#534f43────────────"));
				PrestigeRank rank = RankLoader.getByLevel(f.getRank().getLevel()+1);
				if(rank.getAn()) {
					lore.add(StringFormatter.formatHex("#d4c9aeFaction needs at least #7fbd73"+FactionManager.getRankUpAmount(rank)+" #4793bfPrestige"));
					lore.add(StringFormatter.formatHex("#d4c9aeto become an "+rank.getName()+(rank.hasModifiers() ? " #d4c9aewhich gives:" : "")));
				} else {
					lore.add(StringFormatter.formatHex("#d4c9aeFaction needs at least #7fbd73"+FactionManager.getRankUpAmount(rank)+" #4793bfPrestige"));
					lore.add(StringFormatter.formatHex("#d4c9aeto become a "+rank.getName()+(rank.hasModifiers() ? " #d4c9aewhich gives:" : "")));
				}
				if(rank.hasModifiers()) {
					for(FactionModifier mod : rank.getModifiers()) {
						if(mod.getAmount() != 0.0) lore.add(StringFormatter.formatHex("#d4c9ae- "+mod.getString()));
					}
				}
			}
			if(f.getRank().getLevel() != 1) {
				lore.add(StringFormatter.formatHex("#534f43────────────"));
				PrestigeRank rank = RankLoader.getByLevel(f.getRank().getLevel()-1);
				if(rank.getAn()) {
					lore.add(StringFormatter.formatHex("#d4c9aeIf the Faction falls below #7fbd73"+(FactionManager.getRankUpAmount(RankLoader.getByLevel(f.getRank().getLevel()))*0.95)+" #4793bfPrestige"));
					lore.add(StringFormatter.formatHex("#d4c9aethe faction will become an "+rank.getName()+(rank.hasModifiers() ? " #d4c9aewhich gives:" : "")));
				} else {
					lore.add(StringFormatter.formatHex("#d4c9aeIf the Faction falls below #7fbd73"+(format.formatDouble(FactionManager.getRankUpAmount(RankLoader.getByLevel(f.getRank().getLevel()))*0.95))+" #4793bfPrestige"));
					lore.add(StringFormatter.formatHex("#d4c9aethe faction will become a "+rank.getName()+(rank.hasModifiers() ? " #d4c9aewhich gives:" : "")));
				}
				if(rank.hasModifiers()) {
					for(FactionModifier mod : rank.getModifiers()) {
						if(mod.getAmount() != 0.0) lore.add(StringFormatter.formatHex("#d4c9ae- "+mod.getString()));
					}
				}
			}
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.MEMBERS)) {
				i = new ItemStack(Material.PLAYER_HEAD, 1);
				ItemMeta m = i.getItemMeta();
				m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
				List<String> memberNames = f.getCompleteMemberList();
				m.setDisplayName(StringFormatter.formatHex("#b8ae61Members: #7fbd73"+memberNames.size()));
				List<String> lore = new ArrayList<String>();
				int count = 0;
				for(String s : memberNames) {
					if(count == 24) break;
					lore.add(StringFormatter.formatHex("#d4c9ae"+s+" "+Represents.represents(f, s)));
					count++;
				}
				if(count < memberNames.size()) {
					lore.add("§7...and "+(memberNames.size()-count)+" more");
				}
				m.setLore(lore);
				i.setItemMeta(m);	
		} else if(t.equals(MenuItemType.GUILDS)) {
				i = new ItemStack(Material.SHIELD, 1);
				ItemMeta m = i.getItemMeta();
				m.setDisplayName(StringFormatter.formatHex("#b8ae61Guilds"));
				List<String> lore = new ArrayList<String>();
				int guildCount = f.getGuildHandler().getGuilds().size();
				lore.add(StringFormatter.formatHex("#7fbd73"+guildCount+" #d4c9aeguild"+(guildCount == 1 ? "" : "s")));
				lore.add(StringFormatter.formatHex("#28ed70Click to view"));
				m.setLore(lore);
				NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
				m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
				i.setItemMeta(m);
		} else if(t.equals(MenuItemType.MILITARY)) {
				i = new ItemStack(Material.IRON_SWORD, 1);
				ItemMeta m = i.getItemMeta();
				m.setDisplayName(StringFormatter.formatHex("#a6659fMilitary"));
				List<String> lore = new ArrayList<String>();
				lore.add("§7Click to view Military");
				m.setLore(lore);
				NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
				m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
				i.setItemMeta(m);
		} else if(t.equals(MenuItemType.INSTALLATIONS)) {
				i = new ItemStack(Material.GREEN_CONCRETE, 1);
				ItemMeta m = i.getItemMeta();
				m.setDisplayName(StringFormatter.formatHex("#706964Installations"));
				int forts = 0;
				int ports = 0;
				int airports = 0;
				double totalUpkeep = 0;
				for(Installation installation : f.getInstallationHandler().getAll()) {
					totalUpkeep += InstallationConfigLoader.getDailyUpkeep(installation.getKind());
					if(installation.getKind() == InstallationKind.FORT) forts++;
					else if(installation.getKind() == InstallationKind.PORT) ports++;
					else if(installation.getKind() == InstallationKind.AIRPORT) airports++;
				}
				List<String> lore = new ArrayList<String>();
				lore.add("§7Click to view Installations");
				lore.add("§7Forts: §e" + forts + " §7Ports: §e" + ports + " §7Airports: §e" + airports);
				lore.add("§7Total upkeep: §e" + Formatter.formatDouble(totalUpkeep) + "d/day");
				m.setLore(lore);
				NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
				m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
				i.setItemMeta(m);
		} else if (t.equals(MenuItemType.DIPLOMACY)) {
			i = new ItemStack(Material.WRITABLE_BOOK, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#35f2bdDiplomacy"));

			List<String> lore = new ArrayList<>();

			DiplomacyHandler dh = f.getDiplomacyHandler();
			double used = dh.getUsedDiplomaticCapacity();
			double max = dh.getDiplomaticCapacity();

			String capacityText;
			if (used > max) {
				// Everything red if over capacity
				capacityText = "§c" + Formatter.formatDouble(used) + "/" + Formatter.formatDouble(max);
			} else {
				// Used + max colored, slash gray
				capacityText =
						"#6cb9d5" + Formatter.formatDouble(used) +
						"§7/" +
						"#6cb9d5" + Formatter.formatDouble(max);
			}

			lore.add(StringFormatter.formatHex(
					"#7fbd73Diplomatic Capacity: " + capacityText+ " §8(used/max)"
			));
			lore.add("§7Click to view Diplomacy");

			m.setLore(lore);

			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());

			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.TIER)) {
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#c45749§lTier: "+f.getTier().getFormattedName()));
			if(f.getLeader().equalsIgnoreCase(p.getName())) {
				List<String> lore = new ArrayList<String>();
				lore.add("§7Click to edit Tier");
				m.setLore(lore);
			}
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.TITLES)) {
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#8acc6eTitles"));
			List<String> lore = new ArrayList<String>();
			if(f.getLeader().equalsIgnoreCase(p.getName())) {
				lore.add("§7Click to view Titles");
			} else {
				Faction pf = FactionManager.getByLeader(p.getName());
				String overlord = RelationManager.getOverlord(f);
				if(pf != null && overlord != null && overlord.equalsIgnoreCase(pf.getId())) {
					lore.add("§7Click to view Titles to grant to "+f.getName());
				} else {
					for(Title title : f.getRankedTitles()) {
						lore.add(StringFormatter.formatHex("§7- #d4bb98"+title.getName()+" §7("+title.getTier().getFormattedName()+"§7)"));
					}
				}
			}
			m.setLore(lore);
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.MODIFIERS)) {
			i = new ItemStack(Material.GOLDEN_APPLE, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#c49760Modifiers"));
			List<String> lore = new ArrayList<String>();
			for(FactionModifier mod : f.getCombinedModifiers()) {
				if(mod.getAmount() != 0.0) lore.add(StringFormatter.formatHex("#d4c9ae- "+mod.getString()));
			}
			m.setLore(lore);
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.TAX)) {
			i = new ItemStack(Material.GOLD_INGOT, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#c77c32Tax Rates"));
			List<String> lore = new ArrayList<String>();
			//lore.add(StringFormatter.formatHex("#d4c9aeDomestic Taxes§e: #a39a84"+f.getTaxRate()+"%"));
			//if(foreignTax > 0) lore.add(StringFormatter.formatHex("#d4c9aeForeign Taxes§e: #a39a84"+foreignTax+"%"));
            // Show base rates for non-ID tax targets (read-only)
            for (TaxTarget target : TaxTarget.values()) {
                if (target == TaxTarget.GUILD_ID || target == TaxTarget.VASSAL_ID || target == TaxTarget.TARIFF_ID) continue;
                if (!f.getTaxHandler().canCollectTax(target)) continue;
                lore.add(StringFormatter.formatHex("#93c9a7" + target.getDisplayName() + ": #a39a84" + f.getTaxRate(target, null, false) + "%"+ " §8(§7"+f.getTaxRate(target, null, true)+"% effective§8)"));
            }
			lore.add("");
			lore.add(StringFormatter.formatHex("§7Click to view"));
			m.setLore(lore);
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.LAWS)) {
			i = new ItemStack(Material.WRITABLE_BOOK, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#9c64b0Laws"));
			List<String> lore = new ArrayList<String>();
			for(LawGroup group : f.getLawHandler().getGroupList()) {
				lore.add(group.getName()+"§e: "+group.getCurrent().getName());
			}
			lore.add("");
			double upkeep = f.getGovernment().getTotalUpkeep();
			if(upkeep > 0) {
				lore.add(StringFormatter.formatHex("#d4c9aeTotal Law Upkeep: §e"+Formatter.formatDouble(upkeep)+" Administrative Power"));
			}
			lore.add(StringFormatter.formatHex("§7Click to view"));
			m.setLore(lore);
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
			i.setItemMeta(m);
		}
		if(IconGetter.hasIcon(t.toString())) {
			ItemStack icon = IconGetter.getIcon(t.toString());
			i.setType(icon.getType());
			ItemMeta m = i.getItemMeta();
			m.setCustomModelData(icon.getItemMeta().getCustomModelData());
			if (i.getType() == Material.PLAYER_HEAD) {
				m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
			}
			i.setItemMeta(m);
		}
		return i;
	}
}
