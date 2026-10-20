package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.identity.Members;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.managers.holder.SFCombinedInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.WarDemandDraft;
import net.tfminecraft.simplefactions.managers.holder.WarDemandHolder;
import net.tfminecraft.simplefactions.managers.holder.WarInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDemand;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.declare.WarDemandRules;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements.Engagement;

public class WarView {
	public static final int SIDE_COLUMN_SIZE = 16;
	public static final HashMap<Player, Integer> engagementPage = new HashMap<>();

	private static final int PREV_PAGE_SLOT = 45;
	private static final int NEXT_PAGE_SLOT = 53;
	private static final List<Integer> ENGAGEMENT_RESERVED = List.of(
			8, 17, 26, 35, 44, PREV_PAGE_SLOT, NEXT_PAGE_SLOT);

	public record SideColumnLayout(int factionsShown, int engagementsShown, boolean overflowOpener) {
		public static SideColumnLayout of(int factions, int engagements, int columnSize) {
			int size = Math.max(1, columnSize);
			int total = Math.max(0, factions) + Math.max(0, engagements);
			if (total <= size) {
				return new SideColumnLayout(Math.max(0, factions), Math.max(0, engagements), false);
			}
			int room = size - 1;
			int shownFactions = Math.min(Math.max(0, factions), room);
			int shownEngagements = Math.min(Math.max(0, engagements), room - shownFactions);
			return new SideColumnLayout(shownFactions, shownEngagements, true);
		}

		public int hiddenEngagements(int engagements) {
			return Math.max(0, engagements - engagementsShown);
		}
	}
	public InventoryManager inv;
	public WarCreator creator = new WarCreator();
	
	public WarView(InventoryManager inv) {
		this.inv = inv;
	}
	
	public void warList(Player player) {
		warList(player, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void warList(Player player, Inventory i) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder("", SFGUI.WAR_LIST), 54, "§7War List");
		populateWarList(i);
		if(open) player.openInventory(i);
	}

	public void populateWarList(Inventory i) {
		i.clear();
		List<War> activeWars = WarManager.getActive();
		for(int x = 0; x < activeWars.size(); x++) {
			i.setItem(x, creator.createWarItem(activeWars.get(x), true));
		}
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void warView(Inventory i, Player player, War w, boolean open) {
		w.update();
		if(open) {
			i = SimpleFactions.plugin.getServer().createInventory(new WarInventoryHolder(w.getId(), SFGUI.WAR_VIEW), 54, w.getName());
		}
		i.clear();
		List<Integer> gray = Arrays.asList(0, 1, 2, 6, 7, 8, 45, 46, 47, 48, 50, 51, 52);
		List<Integer> red = Arrays.asList(13, 22, 31, 40, 49);
		
		List<Integer> attackerSide = Arrays.asList(9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39);
		List<Integer> defenderSide = Arrays.asList(14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44);
		
		i.setItem(3, creator.createParticipantItem(player, w.getAttackers().getMainParticipants().get(0), "main_attacker", w, false, false));
		i.setItem(4, creator.createWarItem(w, false));
		i.setItem(5, creator.createParticipantItem(player, w.getDefenders().getMainParticipants().get(0), "main_defender", w, false, false));

		fillSideColumn(i, player, w, w.getAttackers(), attackerSide, "mercenary_attacker", BattleTemplate.ATTACKER_SIDE);
		fillSideColumn(i, player, w, w.getDefenders(), defenderSide, "mercenary_defender", BattleTemplate.DEFENDER_SIDE);
		
		for(int x : gray) {
			i.setItem(x, inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
		}
		boolean showCampaign = canShowCampaignView(w, player);
		for(int x : red) {
			if (x == 49 && showCampaign) {
				continue;
			}
			i.setItem(x, inv.getFiller(Material.RED_STAINED_GLASS_PANE));
		}
		if (showCampaign) {
			i.setItem(49, creator.createCampaignButton(w));
		}
		i.setItem(53, inv.createBackButton(SFGUI.WAR_VIEW));
		i.setItem(48, createDemandsButton(w, player));
		if(open) player.openInventory(i);
	}

	private ItemStack createCounterGoalItem(War war, Player viewer) {
		syncFirstBattleStarted(war);
		boolean canEdit = canEditCounterGoal(war, viewer);
		ItemStack item = new ItemStack(canEdit ? Material.GOLDEN_SWORD : Material.GOLD_INGOT);
		ItemMeta meta = item.getItemMeta();
		WarGoalType selected = war.getDefenderCounterGoal();
		meta.setDisplayName("§9Defenders: Counter Goal");
		List<String> lore = new ArrayList<>();
		lore.add(selected == null
				? "§7Selected: None"
				: "§7Selected: " + selected.getDisplayName());
		if (war.hasFirstBattleStarted()) {
			lore.add("§cLocked after the first battle began");
		} else if (canEdit) {
			lore.add("§aClick to set or change");
		} else {
			lore.add("§7Set by the defending nation before battle");
		}
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private ItemStack createDemandsButton(War war, Player viewer) {
		syncFirstBattleStarted(war);
		Faction editor = viewer == null ? null : FactionManager.getByLeader(Members.key(viewer));
		boolean canEdit = canEditCounterGoal(war, viewer)
				|| WarDemandRules.editBlock(war, editor, true) == null
				|| WarDemandRules.editBlock(war, editor, false) == null;
		ItemStack item = new ItemStack(canEdit ? Material.GOLDEN_SWORD : Material.GOLD_INGOT);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§eWar Demands");
		List<String> lore = new ArrayList<>();
		lore.add("§cAttackers:");
		addDemandSummary(lore, WarDemandRules.firstDemand(war, true));
		addDemandSummary(lore, war.getSecondDemand(true));
		lore.add("§9Defenders:");
		addDemandSummary(lore, WarDemandRules.firstDemand(war, false));
		addDemandSummary(lore, war.getSecondDemand(false));
		lore.add("§7The loser also pays war reparations.");
		lore.add(canEdit ? "§aClick to view or change" : "§aClick to view");
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private void addDemandSummary(List<String> lore, WarDemand demand) {
		if (demand == null) {
			return;
		}
		Faction target = demand.getTarget();
		lore.add(" §7- §f" + demand.describeGoal() + " §7of " + (target != null ? target.getName() : demand.getTargetId()));
	}

	public void demandsView(Player player, War war) {
		syncFirstBattleStarted(war);
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
				new WarInventoryHolder(war.getId(), SFGUI.WAR_DEMANDS),
				27,
				"§7War Demands");
		Faction editor = FactionManager.getByLeader(Members.key(player));
		inventory.setItem(10, createFirstAttackerDemandItem(war));
		inventory.setItem(11, createSecondDemandItem(war, editor, true));
		inventory.setItem(13, createReparationsItem());
		inventory.setItem(15, createCounterGoalItem(war, player));
		inventory.setItem(16, createSecondDemandItem(war, editor, false));
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DEMANDS));
		player.openInventory(inventory);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private ItemStack createFirstAttackerDemandItem(War war) {
		ItemStack item = new ItemStack(Material.IRON_SWORD);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§cAttackers: War Goal");
		List<String> lore = new ArrayList<>();
		addDemandLore(lore, WarDemandRules.firstDemand(war, true));
		lore.add("§7Set when the war was declared");
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private ItemStack createSecondDemandItem(War war, Faction editor, boolean attackerSide) {
		WarDemand demand = war.getSecondDemand(attackerSide);
		String block = WarDemandRules.editBlock(war, editor, attackerSide);
		ItemStack item = new ItemStack(demand != null ? Material.WRITABLE_BOOK : Material.BOOK);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(attackerSide ? "§cAttackers: Second Demand" : "§9Defenders: Second Demand");
		List<String> lore = new ArrayList<>();
		if (demand == null) {
			lore.add("§7None");
		} else {
			addDemandLore(lore, demand);
		}
		if (block == null) {
			lore.add(demand == null
					? "§aClick to add a demand, for your nation or an ally"
					: "§aClick to change");
			if (demand != null) {
				lore.add("§cRight-click to remove");
			}
		} else if (war.hasFirstBattleStarted()) {
			lore.add("§cLocked after the first battle began");
		} else if (WarDemandRules.NO_FIRST_DEMAND.equals(block) && !attackerSide) {
			lore.add("§7Needs a counter goal first");
		} else {
			lore.add(attackerSide
					? "§7Set by the attacking nation before battle"
					: "§7Set by the defending nation before battle");
		}
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private void addDemandLore(List<String> lore, WarDemand demand) {
		if (demand == null) {
			lore.add("§7None");
			return;
		}
		Faction target = demand.getTarget();
		Faction beneficiary = demand.getBeneficiary();
		lore.add("§7Demand: §f" + demand.describeGoal());
		lore.add("§7Of: §f" + (target != null ? target.getName() : demand.getTargetId()));
		lore.add("§7For: §f" + (beneficiary != null ? beneficiary.getName() : demand.getBeneficiaryId()));
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private ItemStack createReparationsItem() {
		ItemStack item = new ItemStack(Material.GOLD_NUGGET);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§6War Reparations");
		meta.setLore(List.of(
				"§7Always paid by the losing side",
				"§7" + formatPercent(Cache.warReparationsIncomePercent) + "% of each payer guild's gross",
				"§7trade income for " + Cache.warReparationsDays + " days"));
		item.setItemMeta(meta);
		return item;
	}

	private static String formatPercent(double percent) {
		return percent == Math.rint(percent) ? String.valueOf((long) percent) : String.valueOf(percent);
	}

	/** Starts the second demand: choose who it is for, then of whom, then the goal itself. */
	private void startSecondDemand(Player player, War war, boolean attackerSide) {
		List<Faction> beneficiaries = WarDemandRules.beneficiaries(war, attackerSide);
		if (beneficiaries.size() == 1) {
			chooseDemandTarget(player, war, attackerSide, beneficiaries.get(0));
			return;
		}
		openDemandFactionPicker(player, war, attackerSide, SFGUI.WAR_DEMAND_BENEFICIARY, null,
				beneficiaries, "§7Second Demand - For");
	}

	private void chooseDemandTarget(Player player, War war, boolean attackerSide, Faction beneficiary) {
		List<Faction> targets = WarDemandRules.targets(war, attackerSide);
		if (targets.size() == 1) {
			inv.declareWarView.openDemandGoalPicker(
					player, beneficiary, targets.get(0), new WarDemandDraft(war.getId(), attackerSide));
			return;
		}
		openDemandFactionPicker(player, war, attackerSide, SFGUI.WAR_DEMAND_TARGET, beneficiary.getId(),
				targets, "§7Second Demand - Of");
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void openDemandFactionPicker(
			Player player,
			War war,
			boolean attackerSide,
			SFGUI step,
			String beneficiaryId,
			List<Faction> factions,
			String title) {
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
				new WarDemandHolder(war.getId(), step, attackerSide, beneficiaryId), 27, title);
		int slot = 0;
		for (Faction faction : factions) {
			if (slot >= 26) break;
			ItemStack item = new ItemStack(Material.WHITE_BANNER);
			ItemMeta meta = item.getItemMeta();
			meta.setDisplayName(faction.getName());
			meta.setLore(List.of(step == SFGUI.WAR_DEMAND_BENEFICIARY
					? "§7Click to make the demand for this nation"
					: "§7Click to make the demand of this nation"));
			meta.getPersistentDataContainer().set(
					new NamespacedKey(SimpleFactions.plugin, "demand_faction"),
					PersistentDataType.STRING,
					faction.getId());
			item.setItemMeta(meta);
			inventory.setItem(slot++, item);
		}
		inventory.setItem(26, inv.createBackButton(step));
		player.openInventory(inventory);
	}

	private boolean canEditCounterGoal(War war, Player player) {
		syncFirstBattleStarted(war);
		if (war == null || player == null || !war.isActive() || war.hasFirstBattleStarted()
				|| war.getWarType() == WarType.RAID || CivilWarBorderLock.isCivilWar(war)) {
			return false;
		}
		Faction leader = FactionManager.getByLeader(Members.key(player));
		return leader != null && war.getDefenderLeaderId() != null
				&& war.getDefenderLeaderId().equalsIgnoreCase(leader.getId());
	}

	private void syncFirstBattleStarted(War war) {
		if (war == null || war.hasFirstBattleStarted()) return;
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			war.setFirstBattleStarted(true);
			WarManager.persist(war);
		}
	}

	private void counterGoalView(Player player, War war) {
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
				new WarInventoryHolder(war.getId(), SFGUI.WAR_COUNTER_GOAL),
				27,
				"§7Defender Counter Goal");
		int slot = 10;
		addCounterGoalOption(inventory, slot++, WarGoalType.TRIBUTARY, Material.GOLD_INGOT,
				"§7Make the attacker a tributary");
		addCounterGoalOption(inventory, slot++, WarGoalType.OPEN_MARKET, Material.BOOK,
				"§7Force the attacker's market law open");
		Faction attacker = FactionManager.getByString(war.getAttackerLeaderId());
		Faction defender = FactionManager.getByString(war.getDefenderLeaderId());
		if (attacker != null && defender != null && attacker.getHighestTitle() != null
				&& WarGoalValidator.canUsurpByRank(defender.getTier().getTier(), attacker.getTier().getTier())) {
			addCounterGoalOption(inventory, slot++, WarGoalType.USURP, Material.NETHER_STAR,
					"§7Take the attacker's highest title");
		}
		List<RelationType> vassalTypes = RelationLoader.getWarPickableVassalTypes();
		if (vassalTypes != null && !vassalTypes.isEmpty()) {
			for (RelationType type : vassalTypes) {
				if (slot >= 17) break;
				ItemStack option = goalOption(Material.CHAINMAIL_CHESTPLATE, "§eSubjugate: " + type.getName(),
						"§7Make the attacker your " + type.getName());
				ItemMeta meta = option.getItemMeta();
				meta.getPersistentDataContainer().set(
						new NamespacedKey(SimpleFactions.plugin, "counter_relation_type"),
						PersistentDataType.STRING,
						type.getId());
				option.setItemMeta(meta);
				inventory.setItem(slot++, option);
			}
		}
		ItemStack defaultOption = new ItemStack(Material.PAPER);
		ItemMeta defaultOptionMeta = defaultOption.getItemMeta();
		defaultOptionMeta.setDisplayName("§fNo Counter Goal");
		defaultOptionMeta.setLore(List.of("§7Only war reparations apply if the attacker loses"));
		defaultOption.setItemMeta(defaultOptionMeta);
		ItemMeta defaultMeta = defaultOption.getItemMeta();
		defaultMeta.getPersistentDataContainer().set(
				new NamespacedKey(SimpleFactions.plugin, "counter_default"), PersistentDataType.BYTE, (byte) 1);
		defaultOption.setItemMeta(defaultMeta);
		inventory.setItem(22, defaultOption);
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DEMANDS));
		player.openInventory(inventory);
	}

	private void addCounterGoalOption(Inventory inventory, int slot, WarGoalType goal, Material material, String description) {
		ItemStack option = goalOption(material, "§e" + goal.getDisplayName(), description);
		ItemMeta meta = option.getItemMeta();
		meta.getPersistentDataContainer().set(
				new NamespacedKey(SimpleFactions.plugin, "counter_goal"),
				PersistentDataType.STRING,
				goal.toJson());
		option.setItemMeta(meta);
		inventory.setItem(slot, option);
	}

	private ItemStack goalOption(Material material, String name, String description) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		meta.setLore(List.of(description, "§7War reparations apply as well"));
		item.setItemMeta(meta);
		return item;
	}

	private void fillSideColumn(
			Inventory i,
			Player player,
			War w,
			Side side,
			List<Integer> slots,
			String mercMarker,
			String sideId) {
		List<Participant> mains = side.getMainParticipants();
		List<Engagement> engagements = MercenaryEngagements.on(w, side);
		SideColumnLayout layout = SideColumnLayout.of(mains.size(), engagements.size(), slots.size());
		int index = 0;
		String factionMarker = mercMarker.endsWith("defender") ? "main_defender" : "main_attacker";
		for (int x = 0; x < layout.factionsShown(); x++) {
			i.setItem(slots.get(index++), creator.createParticipantItem(
					player, mains.get(x), factionMarker, w, true, false));
		}
		for (int x = 0; x < layout.engagementsShown(); x++) {
			i.setItem(slots.get(index++), creator.createMercenaryItem(engagements.get(x), mercMarker));
		}
		if (layout.overflowOpener()) {
			i.setItem(slots.get(slots.size() - 1), creator.createOverflowOpener(
					sideId, layout.hiddenEngagements(engagements.size())));
		}
	}

	public void engagementList(Player player, War w, String sideId) {
		engagementList(player, w, sideId, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void engagementList(Player player, War w, String sideId, Inventory inventory) {
		engagementPage.putIfAbsent(player, 0);
		boolean open = inventory == null;
		if (open) {
			inventory = SimpleFactions.plugin.getServer().createInventory(
					new SFCombinedInventoryHolder(w.getId(), sideId, SFGUI.MERCENARY_ENGAGEMENT_LIST),
					54,
					"§7Hired companies");
		}
		populateEngagementList(inventory, player, w, sideId);
		if (open) player.openInventory(inventory);
	}

	public void populateEngagementList(Inventory inventory, Player player, War w, String sideId) {
		engagementPage.putIfAbsent(player, 0);
		int page = engagementPage.get(player);
		Side side = BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(sideId)
				? w.getDefenders() : w.getAttackers();
		List<Engagement> engagements = MercenaryEngagements.on(w, side);
		String marker = BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(sideId)
				? "mercenary_defender" : "mercenary_attacker";

		List<Integer> usable = new ArrayList<>();
		for (int s = 0; s < 54; s++) {
			if (!ENGAGEMENT_RESERVED.contains(s)) usable.add(s);
		}
		inventory.clear();
		int perPage = usable.size();
		int start = page * perPage;
		int end = Math.min(start + perPage, engagements.size());
		for (int x = start; x < end; x++) {
			inventory.setItem(usable.get(x - start), creator.createMercenaryItem(engagements.get(x), marker));
		}
		inventory.setItem(8, inv.createBackButton(SFGUI.MERCENARY_ENGAGEMENT_LIST));
		if (page > 0) inventory.setItem(PREV_PAGE_SLOT, DefaultCreator.createPreviousPageButton());
		if (end < engagements.size()) {
			inventory.setItem(NEXT_PAGE_SLOT, DefaultCreator.createNextPageButton());
		}
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void participantView(Inventory i, Player player, War w, Participant p, boolean open) {
		w.update();
		if(open) {
			i = SimpleFactions.plugin.getServer().createInventory(new SFCombinedInventoryHolder(w.getId(), p.getLeader().getId(), SFGUI.PARTICIPANT_VIEW), 54, w.getName());
		}
		i.clear();
		List<Integer> gray = Arrays.asList(0, 1, 2, 3, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52);
		
		i.setItem(4, creator.createParticipantItem(player, p, w.getType(p.getLeader()), w, false, true));
		int slots = 0;
		int offset = 9;
		for(int x = 0; x < p.getSubjects().size(); x++) {
			int slot = x+offset;
			i.setItem(slot, creator.createSecondaryItem(player, p, w, p.getSubjects().get(x), true, true));
			slots++;
		}
		offset += slots;
		List<Faction> allies = new ArrayList<>(p.getAllies().keySet());
		for(int x = 0; x < allies.size(); x++) {
			int slot = x+offset;
			i.setItem(slot, creator.createSecondaryItem(player, p, w, allies.get(x), false, p.getAllies().get(allies.get(x))));
			slots++;
		}
		offset += allies.size();
		List<Faction> backers = p.getBackers();
		for(int x = 0; x < backers.size(); x++) {
			int slot = x+offset;
			i.setItem(slot, creator.createSecondaryItem(player, p, w, backers.get(x), false, true, true));
			slots++;
		}
		for(int x : gray) {
			i.setItem(x, inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
		}
		i.setItem(53, inv.createBackButton(SFGUI.PARTICIPANT_VIEW));
		if(open) player.openInventory(i);
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		if(e.getView().getTitle().equalsIgnoreCase("§7War List")) {
			e.setCancelled(true);
			if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			Integer id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
			if(id == null) return;
			War w = WarManager.getById(id);
			if (w == null) return;
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			warView(null, p, w, true);
		} else if (inventory.getHolder() instanceof WarDemandHolder holder) {
			e.setCancelled(true);
			War war = WarManager.getById(holder.getId());
			if (war == null) return;
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			if (e.getSlot() == 26) {
				if (holder.getType() == SFGUI.WAR_DEMAND_TARGET
						&& WarDemandRules.beneficiaries(war, holder.isAttackerSide()).size() > 1) {
					startSecondDemand(p, war, holder.isAttackerSide());
				} else {
					demandsView(p, war);
				}
				return;
			}
			Faction editor = FactionManager.getByLeader(Members.key(p));
			String block = WarDemandRules.editBlock(war, editor, holder.isAttackerSide());
			if (block != null) {
				p.sendMessage(block);
				demandsView(p, war);
				return;
			}
			ItemStack picked = e.getCurrentItem();
			if (picked == null || picked.getItemMeta() == null) return;
			String factionId = picked.getItemMeta().getPersistentDataContainer().get(
					new NamespacedKey(SimpleFactions.plugin, "demand_faction"), PersistentDataType.STRING);
			Faction faction = FactionManager.getByString(factionId);
			if (faction == null) return;
			if (holder.getType() == SFGUI.WAR_DEMAND_BENEFICIARY) {
				chooseDemandTarget(p, war, holder.isAttackerSide(), faction);
				return;
			}
			Faction beneficiary = FactionManager.getByString(holder.getBeneficiaryId());
			if (beneficiary == null) return;
			inv.declareWarView.openDemandGoalPicker(
					p, beneficiary, faction, new WarDemandDraft(war.getId(), holder.isAttackerSide()));
		} else if (inventory.getHolder() instanceof WarInventoryHolder holder
				&& holder.getType() == SFGUI.WAR_DEMANDS) {
			e.setCancelled(true);
			War war = WarManager.getById(holder.getId());
			if (war == null) return;
			int slot = e.getSlot();
			if (slot == 26) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				warView(null, p, war, true);
				return;
			}
			if (slot == 15) {
				if (!canEditCounterGoal(war, p)) {
					p.sendMessage(war.hasFirstBattleStarted()
							? WarDemandRules.LOCKED
							: "§cOnly the defending nation's leader can set a counter goal before the first battle begins.");
					return;
				}
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				counterGoalView(p, war);
				return;
			}
			if (slot != 11 && slot != 16) {
				return;
			}
			boolean attackerSide = slot == 11;
			syncFirstBattleStarted(war);
			Faction editor = FactionManager.getByLeader(Members.key(p));
			String block = WarDemandRules.editBlock(war, editor, attackerSide);
			if (block != null) {
				p.sendMessage(block);
				return;
			}
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			if (e.isRightClick() && war.getSecondDemand(attackerSide) != null) {
				war.setSecondDemand(attackerSide, null);
				WarManager.persist(war);
				p.sendMessage("§aSecond demand removed.");
				demandsView(p, war);
				return;
			}
			startSecondDemand(p, war, attackerSide);
		} else if (inventory.getHolder() instanceof WarInventoryHolder holder
				&& holder.getType() == SFGUI.WAR_COUNTER_GOAL) {
			e.setCancelled(true);
			War war = WarManager.getById(holder.getId());
			if (war == null) return;
			if (e.getSlot() == 26) {
				demandsView(p, war);
				return;
			}
			if (!canEditCounterGoal(war, p)) {
				p.sendMessage("§cCounter goals can only be changed by the defending nation's leader before the first battle begins.");
				demandsView(p, war);
				return;
			}
			ItemStack selected = e.getCurrentItem();
			if (selected == null || selected.getItemMeta() == null) return;
			var data = selected.getItemMeta().getPersistentDataContainer();
			String relationTypeId = data.get(
					new NamespacedKey(SimpleFactions.plugin, "counter_relation_type"),
					PersistentDataType.STRING);
			String goalId = data.get(
					new NamespacedKey(SimpleFactions.plugin, "counter_goal"),
					PersistentDataType.STRING);
			boolean useDefault = data.has(
					new NamespacedKey(SimpleFactions.plugin, "counter_default"),
					PersistentDataType.BYTE);
			boolean hadSecondDemand = war.getSecondDemand(false) != null;
			if (useDefault || goalId != null || relationTypeId != null) {
				WarGoalType goal = useDefault ? null : relationTypeId != null
						? WarGoalType.SUBJUGATE
						: WarGoalType.fromJson(goalId);
				war.setDefenderCounterGoal(goal, relationTypeId);
				WarManager.persist(war);
				p.sendMessage(goal == null
						? "§aDefender counter goal cleared. War reparations still apply."
						: "§aDefender counter goal set to " + goal.getDisplayName() + ".");
				if (goal == null && hadSecondDemand) {
					p.sendMessage("§7Your second demand was removed with it.");
				} else if (WarDemandRules.conflicts(
						WarDemandRules.firstDemand(war, false), war.getSecondDemand(false))) {
					war.setSecondDemand(false, null);
					WarManager.persist(war);
					p.sendMessage("§7Your second demand clashed with the new counter goal and was removed.");
				}
			}
			demandsView(p, war);
		} else if(inventory.getHolder() instanceof WarInventoryHolder && ((WarInventoryHolder) inventory.getHolder()).getType().equals(SFGUI.WAR_VIEW)) {
			e.setCancelled(true);
			WarInventoryHolder h = (WarInventoryHolder) inventory.getHolder();
			War w = WarManager.getById(h.getId());
			if (w == null) return;
			if(e.getSlot() == 53) {
				warList(p);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			} else if(e.getSlot() == 49) {
				if (!canShowCampaignView(w, p)) {
					return;
				}
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.openCampaignView(p, w);
				return;
			} else if (e.getSlot() == 48) {
				demandsView(p, w);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
			NamespacedKey overflow = new NamespacedKey(SimpleFactions.plugin, "mercenary_overflow");
			String overflowSide = e.getCurrentItem().getItemMeta().getPersistentDataContainer()
					.get(overflow, PersistentDataType.STRING);
			if (overflowSide != null) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				engagementList(p, w, overflowSide);
				return;
			}
			if (openContractFromItem(e.getCurrentItem(), p, w)) return;
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			String id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if(id == null) return;
			Faction f = FactionManager.getByString(id);
			if(f == null) return;
			Participant par = w.getParticipant(f);
			if(par == null) return;
			p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			participantView(null, p, w, par, true);
		} else if(inventory.getHolder() instanceof SFCombinedInventoryHolder
				&& ((SFCombinedInventoryHolder) inventory.getHolder()).getType().equals(SFGUI.MERCENARY_ENGAGEMENT_LIST)) {
			e.setCancelled(true);
			SFCombinedInventoryHolder h = (SFCombinedInventoryHolder) inventory.getHolder();
			War w = WarManager.getById(h.getWarId());
			if (w == null) return;
			if (e.getSlot() == 8) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				warView(null, p, w, true);
				return;
			}
			if (e.getSlot() == PREV_PAGE_SLOT) {
				engagementPage.put(p, Math.max(0, engagementPage.getOrDefault(p, 0) - 1));
				engagementList(p, w, h.getFactionId(), inventory);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			if (e.getSlot() == NEXT_PAGE_SLOT) {
				engagementPage.put(p, engagementPage.getOrDefault(p, 0) + 1);
				engagementList(p, w, h.getFactionId(), inventory);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			openContractFromItem(e.getCurrentItem(), p, w);
		} else if(inventory.getHolder() instanceof SFCombinedInventoryHolder && ((SFCombinedInventoryHolder) inventory.getHolder()).getType().equals(SFGUI.PARTICIPANT_VIEW)) {
			e.setCancelled(true);
			SFCombinedInventoryHolder h = (SFCombinedInventoryHolder) inventory.getHolder();
			War w = WarManager.getById(h.getWarId());
			if (w == null) return;
			if(e.getSlot() == 53) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				warView(null, p, w, true);
				return;
			}
			Faction pf = FactionManager.getByLeader(Members.key(p));
			if(pf == null) return;
			Participant par = w.getParticipant(pf);
			if(par == null) return;
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
			String id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if(id == null) return;
			Faction f = FactionManager.getByString(id);
			if(f == null) return;
			if(par.getAllies().containsKey(f)) {
				if(par.getAllies().get(f)) return;
				if(!w.canBeCalled(pf, f)) return;
				WarManager.sendRequest(p, pf, f, w);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		}
	}

	private boolean canShowCampaignView(War w, Player player) {
		if (w == null || !w.isActive() || w.getWarType() == WarType.RAID) {
			return false;
		}
		List<Integer> axis = w.getCampaignProvinces();
		if (axis == null || axis.isEmpty()) {
			return false;
		}
		Faction faction = FactionManager.getByLeader(Members.key(player));
		if (faction == null) {
			faction = FactionManager.getByMember(Members.key(player));
		}
		return faction != null && w.getParticipant(faction) != null;
	}

	private boolean openContractFromItem(ItemStack item, Player p, War w) {
		if (item == null || !item.hasItemMeta() || w == null) return false;
		String contractId = item.getItemMeta().getPersistentDataContainer()
				.get(Keys.CONTRACT_ID, PersistentDataType.STRING);
		if (contractId == null) return false;
		for (Engagement engagement : MercenaryEngagements.on(w, w.getAttackers())) {
			if (openContract(p, engagement, contractId)) return true;
		}
		for (Engagement engagement : MercenaryEngagements.on(w, w.getDefenders())) {
			if (openContract(p, engagement, contractId)) return true;
		}
		return false;
	}

	private boolean openContract(Player p, Engagement engagement, String contractId) {
		if (engagement == null || engagement.contract() == null) return false;
		if (!engagement.contract().getId().equalsIgnoreCase(contractId)) return false;
		if (engagement.company() == null || engagement.company().getGuild() == null) return false;
		p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		inv.contractDetailView(p, engagement.company().getGuild(), contractId);
		return true;
	}
}
