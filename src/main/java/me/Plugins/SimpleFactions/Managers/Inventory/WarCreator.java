package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Army.LevyEntry;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.declare.OpenMarketEligibility;
import me.Plugins.SimpleFactions.War.core.WarGoal;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class WarCreator {
	public ItemStack createCampaignButton(War w) {
		ItemStack i = new ItemStack(Material.COMPASS, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#d4c9aeCampaign"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#a39ba8Attacker initiative: #d4c9ae" + w.getInitiativeAttacker()));
		lore.add(StringFormatter.formatHex("#a39ba8Defender initiative: #d4c9ae" + w.getInitiativeDefender()));
		String phase = w.getCampaignPhase() != null ? w.getCampaignPhase().toJson() : "invasion";
		lore.add(StringFormatter.formatHex("#a39ba8Phase: #d4c9ae" + phase));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#28ed70Click to view campaign route"));
		m.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_war");
		m.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, w.getId());
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createWarItem(War w, boolean button) {
		ItemStack i = IconGetter.getIconOrDefault("war", Material.BLAZE_POWDER);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(w.getName());
		List<String> lore = new ArrayList<>();
		if(button) {
			lore.add(StringFormatter.formatHex("#28ed70Click to view"));
			lore.add(" ");
			if (w.getStatus() != null) {
				String status = w.getStatus().toJson();
				lore.add(StringFormatter.formatHex("#a39ba8Status: #f5ef42" + status.substring(0, 1).toUpperCase() + status.substring(1)));
			}
			lore.add(StringFormatter.formatHex("#a39ba8Attacker: #d4c9ae" + formatLeaderName(w.getAttackerLeaderId())));
			lore.add(StringFormatter.formatHex("#a39ba8Defender: #d4c9ae" + formatLeaderName(w.getDefenderLeaderId())));
		} else {
			lore.add(StringFormatter.formatHex("#535955ID: "+w.getId()));
		}
		if (w.getGoal() != null) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#a39ba8War Goal: #f5ef42" + w.getGoal().getDisplayName()));
			if (w.getGoal() == WarGoalType.DE_JURE_ANNEX && w.getTargetTitleId() != null) {
				Title title = TitleLoader.getById(w.getTargetTitleId());
				if (title != null) {
					lore.add(StringFormatter.formatHex("#a39ba8Title: #d4c9ae" + title.getName()));
				}
			}
			if (w.getGoal() == WarGoalType.CHANGE_GOVERNMENT) {
				Faction defender = FactionManager.getByString(w.getDefenderLeaderId());
				if (defender != null) {
					addWarLawLore(lore, defender, w.getGovernmentLawId(), "Government");
					addWarLawLore(lore, defender, w.getLeadershipLawId(), "Leadership");
				}
			}
			if (w.getGoal() == WarGoalType.USURP) {
				Faction defender = FactionManager.getByString(w.getDefenderLeaderId());
				if (defender != null && defender.getHighestTitle() != null) {
					lore.add(StringFormatter.formatHex("#a39ba8Primary title: #d4c9ae" + defender.getHighestTitle().getName()));
				}
			}
		}
		m.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		m.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, w.getId());
		i.setItemMeta(m);
		return i;
	}

	private String formatLeaderName(String leaderId) {
		if (leaderId == null) return "Unknown";
		Faction faction = FactionManager.getByString(leaderId);
		return faction != null ? faction.getName() : leaderId;
	}

	private static void addWarLawLore(List<String> lore, Faction defender, String lawId, String label) {
		OpenMarketEligibility.ResolvedLaw resolved = OpenMarketEligibility.resolve(defender, lawId);
		if (resolved != null && resolved.law() != null && resolved.law().getName() != null) {
			lore.add(StringFormatter.formatHex("#a39ba8" + label + ": #d4c9ae" + resolved.law().getName()));
		}
	}
	
	public ItemStack createSecondaryItem(Player p, Participant par, War w, Faction f, boolean subject, boolean called) {
		return createSecondaryItem(p, par, w, f, subject, called, false);
	}

	public ItemStack createSecondaryItem(
			Player p,
			Participant par,
			War w,
			Faction f,
			boolean subject,
			boolean called,
			boolean backer) {
		Faction pf = FactionManager.getByLeader(p.getName());
		ItemStack i = new ItemStack(f.getBanner());
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(f.getName());
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, f.getId());
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#65e0bbSecondary Participant"));
		lore.add(" ");
		if(!subject) {
			lore.add(StringFormatter.formatHex("#a39ba8Soldiers: #28ed70"+f.getMilitary().getManpower(true)));
		} else {
			Faction overlord = FactionManager.getByString(RelationManager.getOverlord(f));
			LevyEntry e = overlord.getMilitary().getRegiment("levy").getEntry(f);
			int amount = 0;
			if(e != null) {
				amount = e.getAmount();
			}
			lore.add(StringFormatter.formatHex("#a39ba8Contributes: #28ed70"+amount+" #a39ba8Soldiers"));
		}
		if(subject) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#d4c9aeThis nation is a subject"));
			lore.add(StringFormatter.formatHex("#d4c9aeand is therefore automatically called"));
			
		}
		if (backer) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#c2bea7Backer"));
		}
		lore.add(" ");
		if(called) {
			lore.add(StringFormatter.formatHex("#2757cc§lCalled!"));
		} else {
			lore.add(StringFormatter.formatHex("#8a4152Not Called!"));
			if(pf != null && par.getLeader().getId().equalsIgnoreCase(pf.getId())) {
				lore.add(StringFormatter.formatHex("#28ed70§lClick to call!"));
			}
		}
		if (w.getGoal() == null && pf != null && !w.getSide(pf).equals(w.getSide(f)) && w.getSide(f) != null) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#8a4152§o§lClick to set a war goal!"));
		}
		
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createParticipantItem(Player p, Participant par, String type, War w, boolean full, boolean warGoal) {
		Faction pf = FactionManager.getByLeader(p.getName());
		Faction f = par.getLeader();
		ItemStack i = new ItemStack(f.getBanner());
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(f.getName());
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, par.getLeader().getId());
		List<String> lore = new ArrayList<>();
		String participant = "";
		if(type.equalsIgnoreCase("main_attacker")) {
			participant = StringFormatter.formatHex("#f5ef42Main Attacker");
		} else if(type.equalsIgnoreCase("main_defender")) {
			participant = StringFormatter.formatHex("#f5ef42Main Defender");
		} else {
			participant = StringFormatter.formatHex("#65e0bbSecondary Participant");
		}
		if(par.isCivilWar()) participant += StringFormatter.formatHex(" §7("+"#14b887Civil War"+"§7)");
		lore.add(participant);
		lore.add(" ");
		Side s = w.getSide(f);
		if(type.equalsIgnoreCase("main_defender")) {
			if(!full && !warGoal) lore.add(StringFormatter.formatHex("#a39ba8Soldiers: #28ed70"+s.getTotalManpower(false)));
			else lore.add(StringFormatter.formatHex("#a39ba8Soldiers: #28ed70"+f.getMilitary().getManpower(false)));
		} else {
			boolean offensive = true;
			if(par.isCivilWar()) offensive = false;
			if(!full && !warGoal) lore.add(StringFormatter.formatHex("#a39ba8Soldiers: #28ed70"+s.getTotalManpower(offensive)));
			else lore.add(StringFormatter.formatHex("#a39ba8Soldiers: #28ed70"+f.getMilitary().getManpower(offensive)));
		}
		if(full && !type.equalsIgnoreCase("secondary_participant")) {
			if(par.getAllies().size() > 0 || par.getSubjects().size() > 0 || !par.getBackers().isEmpty()) {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#65e0bbSecondary Participants:"));
				if(par.getAllies().size() > 0) lore.add(StringFormatter.formatHex("§7- #975bbdAllies: #bea1d1"+par.getAllies().size()));
				if(!par.getBackers().isEmpty()) lore.add(StringFormatter.formatHex("§7- #c2bea7Backers: #d4c9ae"+par.getBackers().size()));
				if(par.getSubjects().size() > 0) lore.add(StringFormatter.formatHex("§7- #768fccSubjects: #a3afcc"+par.getSubjects().size()));
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#28ed70Click to view"));
			} else {
				lore.add(" ");
				lore.add(StringFormatter.formatHex("#9e4c4fNo Secondary Participants"));
			}
		}
		if (w.getGoal() == null && (full || warGoal)) {
			if(pf != null) {
				Participant pp = w.getParticipant(pf);
				if(pp != null) {
					if(!w.getSide(par).equals(w.getSide(pp))) {
						lore.add(" ");
						lore.add(StringFormatter.formatHex("#a39ba8Your War Goal§7:"));
						if(pp.hasWarGoal(f)) {
							lore.add(pp.getWarGoal(f).getName());
						} else {
							lore.add(StringFormatter.formatHex("#8a4152§oNot Set!"));
							if(warGoal) {
								lore.add(StringFormatter.formatHex("#8a4152§o§lClick to set a war goal!"));
							}
						}
					}
				}
				HashMap<Faction, WarGoal> otherGoals = w.getWarGoalsOn(f);
				otherGoals.remove(pf);
				if(otherGoals.size() > 0) {
					lore.add(" ");
					lore.add(StringFormatter.formatHex("#a39ba8Other War Goals§7:"));
					for(Faction g : otherGoals.keySet()) {
						lore.add(g.getName()+" §f- "+otherGoals.get(g).getName());
					}
				}
			}
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public static String mercenaryHeader(String marker) {
		if ("mercenary_defender".equalsIgnoreCase(marker)) {
			return StringFormatter.formatHex("#b7aae3Mercenary (Defender)");
		}
		return StringFormatter.formatHex("#b7aae3Mercenary (Attacker)");
	}

	public List<String> buildMercenaryLore(
			me.Plugins.SimpleFactions.mercenary.contract.MercenaryEngagements.Engagement engagement,
			String marker) {
		List<String> lore = new ArrayList<>();
		lore.add(mercenaryHeader(marker));
		if (engagement == null || engagement.company() == null || engagement.contract() == null) {
			return lore;
		}
		me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany company = engagement.company();
		me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract contract = engagement.contract();
		lore.add(" ");
		if (company.getGuild() != null && company.getGuild().getName() != null) {
			lore.add(StringFormatter.formatHex("#a39ba8Host guild: #d4c9ae" + company.getGuild().getName()));
		}
		lore.add(StringFormatter.formatHex("#a39ba8Home: #d4c9ae"
				+ me.Plugins.SimpleFactions.Utils.HomeSettlementNames.of(company.getGuild())));
		lore.add(StringFormatter.formatHex("#a39ba8Promised slots: #28ed70" + contract.getSlots()));
		lore.add(StringFormatter.formatHex("#a39ba8Reputation: ") + company.getReputationString());
		lore.add(StringFormatter.formatHex("#a39ba8Days remaining: #d4c9ae" + contract.getDaysRemaining()));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#28ed70Click to view the contract"));
		return lore;
	}

	public List<String> buildOverflowOpenerLore(int hiddenCount) {
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#b7aae3More mercenaries"));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#a39ba8+" + Math.max(0, hiddenCount) + " not shown"));
		lore.add(StringFormatter.formatHex("#28ed70Click to view"));
		return lore;
	}

	public ItemStack createMercenaryItem(
			me.Plugins.SimpleFactions.mercenary.contract.MercenaryEngagements.Engagement engagement,
			String marker) {
		me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany company =
				engagement == null ? null : engagement.company();
		org.bukkit.inventory.ItemStack banner = company == null || company.getGuild() == null
				? null : company.getGuild().getBanner();
		ItemStack i = banner == null ? new ItemStack(Material.IRON_SWORD, 1) : banner.clone();
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(company == null ? "Mercenary" : company.getName());
		m.setLore(buildMercenaryLore(engagement, marker));
		if (engagement != null && engagement.contract() != null) {
			m.getPersistentDataContainer().set(
					me.Plugins.SimpleFactions.keys.Keys.CONTRACT_ID,
					PersistentDataType.STRING,
					engagement.contract().getId());
		}
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createOverflowOpener(String sideId, int hiddenCount) {
		ItemStack i = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#b7aae3More mercenaries"));
		m.setLore(buildOverflowOpenerLore(hiddenCount));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "mercenary_overflow");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, sideId);
		i.setItemMeta(m);
		return i;
	}
}
