package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionWars;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.inventory.IconGetter;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility.DeJureAnnexPreview;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility.DeJureTitleOption;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility.KeptSettlement;
import net.tfminecraft.simplefactions.war.declare.OpenMarketEligibility;
import net.tfminecraft.simplefactions.war.declare.PillageEligibility;
import net.tfminecraft.simplefactions.war.declare.WarGoalPreview;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class DeclareWarCreator {

	public ItemStack createTributaryGoalItem(Faction attacker, Faction defender) {
		List<String> lore = new ArrayList<>(List.of("§7Make the defender your tributary."));
		lore.addAll(WarGoalPreview.subjectEffects(RelationLoader.getType("tributary"), attacker, defender));
		return createGoalItem(WarGoalType.TRIBUTARY, "§lTributary", lore);
	}

	public ItemStack createSubjugateGoalItem() {
		return createGoalItem(WarGoalType.SUBJUGATE, "§lSubjugate", List.of(
				"§7Force the defender to become",
				"§7your subject."));
	}

	public ItemStack createUsurpGoalItem(Faction defender) {
		List<String> lore = new ArrayList<>(List.of(
				"§7Take the defender's primary title",
				"§7and become their liege."));
		lore.addAll(WarGoalPreview.usurp(defender));
		return createGoalItem(WarGoalType.USURP, "§lUsurp", lore);
	}

	public ItemStack createOpenMarketGoalItem(Faction defender) {
		List<String> lore = new ArrayList<>(List.of(
				"§7Force the defender to adopt",
				"§7the configured free-trade law."));
		OpenMarketEligibility.ResolvedLaw resolved =
				OpenMarketEligibility.resolve(defender, Cache.openMarketApplyDefenderLaw);
		if (resolved != null && resolved.law() != null && resolved.law().getName() != null) {
			lore.add("§7Law: §f" + resolved.law().getName());
		}
		return createGoalItem(WarGoalType.OPEN_MARKET, "§lOpen Market", lore);
	}

	public ItemStack createChangeGovernmentGoalItem() {
		return createGoalItem(WarGoalType.CHANGE_GOVERNMENT, "§lChange Government", List.of(
				"§7Force the defender to adopt a",
				"§7government and leadership combo."));
	}

	/** {@code bestLoot}: the richest eligible settlement's line, or null when none is in range. */
	public ItemStack createPillageGoalItem(String bestLoot) {
		List<String> lore = new ArrayList<>(List.of(
				"§7Raid a nearby settlement for",
				"§7trade income and loot."));
		lore.add(bestLoot != null ? bestLoot : "§cNo settlement in range to pillage.");
		return createGoalItem(WarGoalType.PILLAGE, "§lPillage", lore);
	}

	/** Shown above the goals: what every war wins or costs, and when the staff code is asked for. */
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createOverviewItem(Faction attacker, Faction defender, boolean codeRequired) {
		ItemStack item = new ItemStack(Material.GOLD_NUGGET);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§6War with " + defender.getName());
		List<String> lore = new ArrayList<>(WarGoalPreview.reparations(attacker, defender));
		lore.add(" ");
		lore.add("§7Hover over a goal to see what it wins.");
		if (codeRequired) {
			lore.add("§7You only need a staff war code");
			lore.add("§7when you confirm the declaration.");
		}
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createDeJureGoalItem() {
		return createGoalItem(WarGoalType.DE_JURE_ANNEX, "§lDe Jure Annex", List.of(
				"§7Take land in a title you own,",
				"§7or an unowned title you already",
				"§7hold a province in."));
	}

	public ItemStack createTransferSubjectGoalItem() {
		return createGoalItem(WarGoalType.TRANSFER_SUBJECT, "§lTransfer Subject", List.of(
				"§7Take one of the defender's",
				"§7subjects as your own."));
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private ItemStack createGoalItem(WarGoalType goal, String name, List<String> description) {
		ItemStack item = IconGetter.getIcon("war");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#d42300" + name));
		List<String> lore = new ArrayList<>(description);
		lore.add(" ");
		lore.add("§eClick to select");
		meta.setLore(lore);
		NamespacedKey goalKey = new NamespacedKey(SimpleFactions.plugin, "goal");
		meta.getPersistentDataContainer().set(goalKey, PersistentDataType.STRING, goal.toJson());
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createRelationTypeItem(RelationType type, Faction attacker, Faction defender) {
		ItemStack item = IconGetter.getIcon("war");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(type.getName());
		List<String> lore = new ArrayList<>();
		lore.add("§7The defender becomes this");
		lore.add("§7kind of subject.");
		lore.addAll(WarGoalPreview.subjectEffects(type, attacker, defender));
		if (attacker != null && RelationManager.atLimit(attacker, type)) {
			lore.add("§cAt limit");
		}
		lore.add(" ");
		lore.add("§eClick to declare");
		meta.setLore(lore);
		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, type.getId());
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createTitleItem(DeJureTitleOption option) {
		Title title = option.title();
		ItemStack item = new ItemStack(Material.PAPER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§7" + title.getName());
		List<String> lore = new ArrayList<>();
		lore.add("§7Tier: §f" + title.getTier().getName());
		addAnnexPreviewLore(lore, option.preview());
		lore.add(" ");
		if (option.eligible()) {
			lore.add("§eClick to declare");
		} else if (option.blockReason() != null) {
			lore.add(option.blockReason());
		}
		meta.setLore(lore);
		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, title.getId());
		if (option.eligible()) {
			NamespacedKey eligibleKey = new NamespacedKey(SimpleFactions.plugin, "eligible");
			meta.getPersistentDataContainer().set(eligibleKey, PersistentDataType.STRING, "true");
		}
		item.setItemMeta(meta);
		return item;
	}

	private static void addAnnexPreviewLore(List<String> lore, DeJureAnnexPreview preview) {
		if (preview == null || preview.incoming() < 1) {
			return;
		}
		lore.add("§7Annex §f" + preview.taken() + " §7of §f" + preview.incoming()
				+ " §7" + provinces(preview.incoming()));
		for (KeptSettlement kept : preview.keptSettlements()) {
			lore.add("§7Kept by " + kept.holderName() + ": §f" + kept.settlementName() + " §7(settlement)");
		}
		if (preview.roadSize() > 0) {
			lore.add("§7Road to their capital: §f" + preview.roadSize() + " §7" + provinces(preview.roadSize()));
		}
		if (preview.otherKept() > 0) {
			lore.add("§7Kept so their land stays whole: §f" + preview.otherKept() + " §7" + provinces(preview.otherKept()));
		}
	}

	private static String provinces(int count) {
		return count == 1 ? "province" : "provinces";
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createSettlementItem(PillageEligibility.PillageSettlementOption option) {
		Settlement settlement = option.settlement();
		ItemStack item = new ItemStack(Material.PAPER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§7" + settlement.getName());
		List<String> lore = new ArrayList<>(WarGoalPreview.pillage(settlement));
		lore.add(" ");
		if (option.eligible()) {
			lore.add("§eClick to declare");
		} else if (option.blockReason() != null) {
			lore.add(option.blockReason());
		}
		meta.setLore(lore);
		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, settlement.getId());
		if (option.eligible()) {
			NamespacedKey eligibleKey = new NamespacedKey(SimpleFactions.plugin, "eligible");
			meta.getPersistentDataContainer().set(eligibleKey, PersistentDataType.STRING, "true");
		}
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createSubjectItem(Faction subject) {
		ItemStack item = subject.getBanner();
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(subject.getName());
		List<String> lore = new ArrayList<>();
		lore.add("§7Subject faction");
		lore.add(" ");
		lore.add("§eClick to declare");
		meta.setLore(lore);
		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, subject.getId());
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createGovernmentAxisItem(LawGroup group, Law selected) {
		ItemStack item = IconGetter.getIcon("war");
		ItemMeta meta = item.getItemMeta();
		String groupName = group.getName() != null ? group.getName() : group.getId();
		meta.setDisplayName("§7" + groupName);
		List<String> lore = new ArrayList<>();
		if (selected != null && selected.getName() != null) {
			lore.add("§7Selected: §f" + selected.getName());
		}
		lore.add(" ");
		lore.add("§eClick to change");
		meta.setLore(lore);
		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, group.getId());
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createGovernmentLawItem(Law law, boolean selected) {
		ItemStack item = new ItemStack(Material.PAPER, 1);
		ItemMeta meta = item.getItemMeta();
		String name = law.getName() != null ? law.getName() : law.getId();
		meta.setDisplayName("§7" + name);
		List<String> lore = new ArrayList<>();
		if (selected) {
			lore.add("§aCurrently selected");
		}
		lore.add(" ");
		lore.add("§eClick to select");
		meta.setLore(lore);
		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, law.getId());
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createGovernmentConfirmItem() {
		ItemStack item = IconGetter.getIcon("war");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#d42300§lConfirm"));
		meta.setLore(List.of("§eClick to declare"));
		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, "confirm");
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createConfirmSummaryItem(WarDeclareRequest request, boolean asksForCode) {
		ItemStack item = IconGetter.getIcon("war");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#d42300§lDeclare War?"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#a89977Target: "+request.getDefender().getName()));
		lore.add(StringFormatter.formatHex("#a89977Goal: "+request.getGoal().getDisplayName()));
		if (request.getGoal() == WarGoalType.DE_JURE_ANNEX) {
			Title title = TitleLoader.getById(request.getTargetTitleId());
			if (title != null) {
				lore.add(StringFormatter.formatHex("#a89977Title: "+title.getName()));
			}
		}
		if (request.getGoal() == WarGoalType.TRANSFER_SUBJECT) {
			Faction subject = FactionManager.getByString(request.getSubjectFactionId());
			if (subject != null) {
				lore.add(StringFormatter.formatHex("#a89977Subject: "+subject.getName()));
			}
		}
		if (request.getGoal() == WarGoalType.SUBJUGATE) {
			RelationType type = RelationLoader.getType(request.getRelationTypeId());
			if (type != null) {
				lore.add(StringFormatter.formatHex("#a89977Subject type: "+type.getName()));
			}
		}
		if (request.getGoal() == WarGoalType.OPEN_MARKET) {
			OpenMarketEligibility.ResolvedLaw resolved =
					OpenMarketEligibility.resolve(request.getDefender(), Cache.openMarketApplyDefenderLaw);
			if (resolved != null && resolved.law() != null && resolved.law().getName() != null) {
				lore.add(StringFormatter.formatHex("#a89977Law: §7" + resolved.law().getName()));
			}
		}
		if (request.getGoal() == WarGoalType.CHANGE_GOVERNMENT) {
			addResolvedLawLore(lore, request.getDefender(), request.getGovernmentLawId(), "Government");
			addResolvedLawLore(lore, request.getDefender(), request.getLeadershipLawId(), "Leadership");
		}
		if (request.getGoal() == WarGoalType.PILLAGE) {
			Settlement settlement = PillageEligibility.findSettlement(request.getTargetSettlementId(), request.getDefender());
			if (settlement != null && settlement.getName() != null) {
				lore.add(StringFormatter.formatHex("#a89977Settlement: "+settlement.getName()));
				lore.addAll(WarGoalPreview.pillage(settlement));
			}
		}
		if (request.getGoal() == WarGoalType.USURP) {
			Title title = request.getDefender().getHighestTitle();
			if (title != null) {
				lore.add(StringFormatter.formatHex("#a89977Primary title: "+title.getName()));
			}
		}
		List<String> coalitionWarnings = CoalitionWars.declareWarnings(request.getAttacker(), request.getDefender());
		if (!coalitionWarnings.isEmpty()) {
			lore.add(" ");
			lore.addAll(coalitionWarnings);
		}
		lore.add(" ");
		lore.addAll(WarGoalPreview.reparations(request.getAttacker(), request.getDefender()));
		lore.add(" ");
		if (asksForCode) {
			lore.add("§eConfirming asks for your staff war code.");
		}
		lore.add(StringFormatter.formatHex("#c74c3fThis cannot be undone easily."));
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private static void addResolvedLawLore(List<String> lore, Faction defender, String lawId, String label) {
		OpenMarketEligibility.ResolvedLaw resolved = OpenMarketEligibility.resolve(defender, lawId);
		if (resolved != null && resolved.law() != null && resolved.law().getName() != null) {
			lore.add(StringFormatter.formatHex("#a89977" + label + ": §7" + resolved.law().getName()));
		}
	}
}
