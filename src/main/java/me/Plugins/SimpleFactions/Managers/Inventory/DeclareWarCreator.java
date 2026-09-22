package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.Inventory.IconGetter;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.declare.DeJureAnnexEligibility.DeJureTitleOption;
import me.Plugins.SimpleFactions.War.declare.OpenMarketEligibility;
import me.Plugins.SimpleFactions.War.declare.PillageEligibility;
import me.Plugins.SimpleFactions.War.declare.WarDeclareRequest;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class DeclareWarCreator {

	public ItemStack createWarGoalItem() {
		return createGoalItem(WarGoalType.WAR, "§lWar", List.of(
				"§7Fight a campaign with no",
				"§7automatic political outcome."));
	}

	public ItemStack createTributaryGoalItem() {
		return createGoalItem(WarGoalType.TRIBUTARY, "§lTributary", List.of(
				"§7Make the defender your tributary."));
	}

	public ItemStack createSubjugateGoalItem() {
		return createGoalItem(WarGoalType.SUBJUGATE, "§lSubjugate", List.of(
				"§7Force the defender to become",
				"§7your subject."));
	}

	public ItemStack createUsurpGoalItem() {
		return createGoalItem(WarGoalType.USURP, "§lUsurp", List.of(
				"§7Take the defender's primary title",
				"§7and become their liege."));
	}

	public ItemStack createOpenMarketGoalItem() {
		return createGoalItem(WarGoalType.OPEN_MARKET, "§lOpen Market", List.of(
				"§7Force the defender to adopt",
				"§7the configured free-trade law."));
	}

	public ItemStack createChangeGovernmentGoalItem() {
		return createGoalItem(WarGoalType.CHANGE_GOVERNMENT, "§lChange Government", List.of(
				"§7Force the defender to adopt a",
				"§7government and leadership combo."));
	}

	public ItemStack createPillageGoalItem() {
		return createGoalItem(WarGoalType.PILLAGE, "§lPillage", List.of(
				"§7Raid a nearby settlement for",
				"§7trade income and loot."));
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

	public ItemStack createRelationTypeItem(RelationType type, Faction attacker) {
		ItemStack item = IconGetter.getIcon("war");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(type.getName());
		List<String> lore = new ArrayList<>();
		lore.add("§7The defender becomes this");
		lore.add("§7kind of subject.");
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

	public ItemStack createTitleItem(DeJureTitleOption option) {
		Title title = option.title();
		ItemStack item = new ItemStack(Material.PAPER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§7" + title.getName());
		List<String> lore = new ArrayList<>();
		lore.add("§7Tier: §f" + title.getTier().getName());
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

	public ItemStack createSettlementItem(PillageEligibility.PillageSettlementOption option) {
		Settlement settlement = option.settlement();
		ItemStack item = new ItemStack(Material.PAPER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§7" + settlement.getName());
		List<String> lore = new ArrayList<>();
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

	public ItemStack createConfirmSummaryItem(WarDeclareRequest request) {
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
			Settlement settlement = PillageEligibility.findSettlement(request.getTargetSettlementId());
			if (settlement != null && settlement.getName() != null) {
				lore.add(StringFormatter.formatHex("#a89977Settlement: "+settlement.getName()));
			}
		}
		if (request.getGoal() == WarGoalType.USURP) {
			Title title = request.getDefender().getHighestTitle();
			if (title != null) {
				lore.add(StringFormatter.formatHex("#a89977Primary title: "+title.getName()));
			}
		}
		lore.add(" ");
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
