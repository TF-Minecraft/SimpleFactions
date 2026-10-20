package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.identity.Members;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
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
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.DeclareWarHolder;
import net.tfminecraft.simplefactions.managers.holder.WarDemandDraft;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDeclareHelper;
import net.tfminecraft.simplefactions.war.core.WarDemand;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.declare.ChangeGovernmentEligibility;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility.DeJureTitleOption;
import net.tfminecraft.simplefactions.war.declare.PillageEligibility;
import net.tfminecraft.simplefactions.war.declare.WarDeclareCodeService;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarDemandRules;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.declare.WarValidationResult;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

public class DeclareWarView {
	public InventoryManager inv;
	public DeclareWarCreator creator = new DeclareWarCreator();

	public DeclareWarView(InventoryManager inv) {
		this.inv = inv;
	}

	public void openGoalPicker(Player player, Faction attacker, Faction defender) {
		openGoalPicker(player, attacker, defender, null);
	}

	public void openGoalPicker(Player player, Faction attacker, Faction defender, Inventory inventory) {
		openGoalPicker(player, attacker, defender, inventory, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void openGoalPicker(
			Player player, Faction attacker, Faction defender, Inventory inventory, WarDemandDraft demand) {
		if (!Cache.requireProvinces(player)) {
			return;
		}
		boolean open = inventory == null;
		if(open) {
			DeclareWarHolder holder = new DeclareWarHolder(attacker.getId(), defender.getId(), SFGUI.WAR_DECLARE_GOAL).withDemand(demand);
			inventory = SimpleFactions.plugin.getServer().createInventory(
					holder, 27, title(demand, "Goal"));
		}
		inventory.clear();
		int slot = 10;
		inventory.setItem(slot++, creator.createTributaryGoalItem());
		if (attacker.canHaveVassals()) {
			inventory.setItem(slot++, creator.createSubjugateGoalItem());
		}
		inventory.setItem(slot++, creator.createOpenMarketGoalItem());
		inventory.setItem(slot++, creator.createChangeGovernmentGoalItem());
		// Pillage is fought over its own settlement, so it can only be a war's first goal.
		if (demand == null) {
			inventory.setItem(slot++, creator.createPillageGoalItem());
		}
		boolean withinRealm = withinRealm(demand);
		if (!withinRealm && WarDeclareHelper.canDeclareUsurp(attacker, defender)) {
			inventory.setItem(slot++, creator.createUsurpGoalItem());
		}
		if (!titleOptions(attacker, defender, demand).isEmpty()) {
			inventory.setItem(slot++, creator.createDeJureGoalItem());
		}
		if (attacker.canHaveVassals() && !subjects(defender, demand).isEmpty()) {
			inventory.setItem(slot, creator.createTransferSubjectGoalItem());
		}
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DECLARE_GOAL));
		relabelForDemand(inventory, demand);
		if(open) player.openInventory(inventory);
	}

	public void openRelationTypePicker(Player player, Faction attacker, Faction defender) {
		openRelationTypePicker(player, attacker, defender, null);
	}

	public void openRelationTypePicker(Player player, Faction attacker, Faction defender, Inventory inventory) {
		openRelationTypePicker(player, attacker, defender, inventory, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void openRelationTypePicker(
			Player player, Faction attacker, Faction defender, Inventory inventory, WarDemandDraft demand) {
		boolean open = inventory == null;
		if(open) {
			DeclareWarHolder holder = new DeclareWarHolder(attacker.getId(), defender.getId(), SFGUI.WAR_DECLARE_RELATION_TYPE).withDemand(demand);
			inventory = SimpleFactions.plugin.getServer().createInventory(holder, 27, title(demand, "Type"));
		}
		inventory.clear();
		int slot = 0;
		for (RelationType type : RelationLoader.getWarPickableVassalTypes()) {
			if (slot >= 26) break;
			inventory.setItem(slot++, creator.createRelationTypeItem(type, attacker));
		}
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DECLARE_RELATION_TYPE));
		relabelForDemand(inventory, demand);
		if(open) player.openInventory(inventory);
	}

	public void openTitlePicker(Player player, Faction attacker, Faction defender) {
		openTitlePicker(player, attacker, defender, null);
	}

	public void openTitlePicker(Player player, Faction attacker, Faction defender, Inventory inventory) {
		openTitlePicker(player, attacker, defender, inventory, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void openTitlePicker(
			Player player, Faction attacker, Faction defender, Inventory inventory, WarDemandDraft demand) {
		boolean open = inventory == null;
		if(open) {
			DeclareWarHolder holder = new DeclareWarHolder(attacker.getId(), defender.getId(), SFGUI.WAR_DECLARE_TITLE).withDemand(demand);
			inventory = SimpleFactions.plugin.getServer().createInventory(holder, 27, title(demand, "Title"));
		}
		inventory.clear();
		int slot = 0;
		for (DeJureTitleOption option : titleOptions(attacker, defender, demand)) {
			if (slot >= 26) break;
			inventory.setItem(slot++, creator.createTitleItem(option));
		}
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DECLARE_TITLE));
		relabelForDemand(inventory, demand);
		if(open) player.openInventory(inventory);
	}

	public void openSubjectPicker(Player player, Faction attacker, Faction defender) {
		openSubjectPicker(player, attacker, defender, null);
	}

	public void openSubjectPicker(Player player, Faction attacker, Faction defender, Inventory inventory) {
		openSubjectPicker(player, attacker, defender, inventory, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void openSubjectPicker(
			Player player, Faction attacker, Faction defender, Inventory inventory, WarDemandDraft demand) {
		boolean open = inventory == null;
		if(open) {
			DeclareWarHolder holder = new DeclareWarHolder(attacker.getId(), defender.getId(), SFGUI.WAR_DECLARE_SUBJECT).withDemand(demand);
			inventory = SimpleFactions.plugin.getServer().createInventory(holder, 27, title(demand, "Subject"));
		}
		inventory.clear();
		int slot = 0;
		for (Faction subject : subjects(defender, demand)) {
			if (slot >= 26) break;
			inventory.setItem(slot++, creator.createSubjectItem(subject));
		}
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DECLARE_SUBJECT));
		relabelForDemand(inventory, demand);
		if(open) player.openInventory(inventory);
	}

	public void openSettlementPicker(Player player, Faction attacker, Faction defender) {
		openSettlementPicker(player, attacker, defender, null);
	}

	public void openSettlementPicker(Player player, Faction attacker, Faction defender, Inventory inventory) {
		openSettlementPicker(player, attacker, defender, inventory, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void openSettlementPicker(
			Player player, Faction attacker, Faction defender, Inventory inventory, WarDemandDraft demand) {
		boolean open = inventory == null;
		if(open) {
			DeclareWarHolder holder = new DeclareWarHolder(attacker.getId(), defender.getId(), SFGUI.WAR_DECLARE_SETTLEMENT).withDemand(demand);
			inventory = SimpleFactions.plugin.getServer().createInventory(holder, 27, title(demand, "Settlement"));
		}
		inventory.clear();
		int slot = 0;
		for (PillageEligibility.PillageSettlementOption option : PillageEligibility.options(attacker, defender)) {
			if (slot >= 26) break;
			inventory.setItem(slot++, creator.createSettlementItem(option));
		}
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DECLARE_SETTLEMENT));
		relabelForDemand(inventory, demand);
		if(open) player.openInventory(inventory);
	}

	public void openGovernmentPicker(
			Player player,
			Faction attacker,
			Faction defender,
			String governmentLawId,
			String leadershipLawId) {
		openGovernmentPicker(player, attacker, defender, governmentLawId, leadershipLawId, null);
	}

	public void openGovernmentPicker(
			Player player,
			Faction attacker,
			Faction defender,
			String governmentLawId,
			String leadershipLawId,
			Inventory inventory) {
		openGovernmentPicker(player, attacker, defender, governmentLawId, leadershipLawId, inventory, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void openGovernmentPicker(
			Player player,
			Faction attacker,
			Faction defender,
			String governmentLawId,
			String leadershipLawId,
			Inventory inventory,
			WarDemandDraft demand) {
		String govId = governmentLawId != null
				? governmentLawId
				: ChangeGovernmentEligibility.currentLawId(defender, ChangeGovernmentEligibility.GOVERNMENT_GROUP);
		String leadId = leadershipLawId != null
				? leadershipLawId
				: ChangeGovernmentEligibility.currentLawId(defender, ChangeGovernmentEligibility.LEADERSHIP_GROUP);
		boolean open = inventory == null;
		if(open) {
			DeclareWarHolder holder = new DeclareWarHolder(
					attacker.getId(), defender.getId(), SFGUI.WAR_DECLARE_GOVERNMENT, govId, leadId).withDemand(demand);
			inventory = SimpleFactions.plugin.getServer().createInventory(holder, 27, title(demand, "Government"));
		}
		inventory.clear();
		LawGroup government = ChangeGovernmentEligibility.group(defender, ChangeGovernmentEligibility.GOVERNMENT_GROUP);
		if (government != null) {
			inventory.setItem(11, creator.createGovernmentAxisItem(government, lawInGroup(government, govId)));
		}
		LawGroup leadership = ChangeGovernmentEligibility.group(defender, ChangeGovernmentEligibility.LEADERSHIP_GROUP);
		if (leadership != null) {
			inventory.setItem(15, creator.createGovernmentAxisItem(leadership, lawInGroup(leadership, leadId)));
		}
		if (!ChangeGovernmentEligibility.combinationEqualsCurrent(defender, govId, leadId)) {
			inventory.setItem(22, creator.createGovernmentConfirmItem());
		}
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DECLARE_GOVERNMENT));
		relabelForDemand(inventory, demand);
		if(open) player.openInventory(inventory);
	}

	public void click(InventoryClickEvent e, Inventory inventory, Player player) {
		if (!(inventory.getHolder() instanceof DeclareWarHolder holder)) return;
		e.setCancelled(true);
		WarDemandDraft demand = holder.getDemand();
		ItemStack item = e.getCurrentItem();
		if (item == null || item.getType().equals(Material.AIR)) return;

		Faction attacker = FactionManager.getByString(holder.getAttackerId());
		Faction defender = FactionManager.getByString(holder.getDefenderId());
		if (attacker == null || defender == null) return;

		if (item.getType().equals(Material.BARRIER)) {
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			if (holder.getStep() == SFGUI.WAR_DECLARE_GOAL) {
				if (demand != null) {
					returnToDemands(player, demand);
				} else {
					inv.diplomacyView(null, player, defender, true);
				}
			} else if (holder.getStep() == SFGUI.WAR_DECLARE_GOVERNMENT_LAW) {
				openGovernmentPicker(
						player, attacker, defender, holder.getGovernmentLawId(), holder.getLeadershipLawId(),
						null, demand);
			} else {
				backOutOfSubPicker(player, attacker, defender, demand);
			}
			return;
		}

		if (holder.getStep() == SFGUI.WAR_DECLARE_GOAL) {
			NamespacedKey goalKey = new NamespacedKey(SimpleFactions.plugin, "goal");
			String goalId = item.getItemMeta().getPersistentDataContainer().get(goalKey, PersistentDataType.STRING);
			WarGoalType goal = WarGoalType.fromJson(goalId);
			if (goal == null) return;
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			routeGoal(player, attacker, defender, goal, demand);
			return;
		}

		NamespacedKey idKey = new NamespacedKey(SimpleFactions.plugin, "id");
		String id = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
		if (id == null) return;
		player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		if (holder.getStep() == SFGUI.WAR_DECLARE_TITLE) {
			NamespacedKey eligibleKey = new NamespacedKey(SimpleFactions.plugin, "eligible");
			String eligible = item.getItemMeta().getPersistentDataContainer().get(eligibleKey, PersistentDataType.STRING);
			if (!"true".equals(eligible)) {
				return;
			}
			openConfirm(player, attacker, defender, WarGoalType.DE_JURE_ANNEX, id, null, null, null, null, null, demand);
		} else if (holder.getStep() == SFGUI.WAR_DECLARE_SETTLEMENT) {
			NamespacedKey eligibleKey = new NamespacedKey(SimpleFactions.plugin, "eligible");
			String eligible = item.getItemMeta().getPersistentDataContainer().get(eligibleKey, PersistentDataType.STRING);
			if (!"true".equals(eligible)) {
				return;
			}
			openConfirm(player, attacker, defender, WarGoalType.PILLAGE, null, null, null, null, null, id, demand);
		} else if (holder.getStep() == SFGUI.WAR_DECLARE_SUBJECT) {
			openConfirm(player, attacker, defender, WarGoalType.TRANSFER_SUBJECT, null, id, null, null, null, null, demand);
		} else if (holder.getStep() == SFGUI.WAR_DECLARE_RELATION_TYPE) {
			openConfirm(player, attacker, defender, WarGoalType.SUBJUGATE, null, null, id, null, null, null, demand);
		} else if (holder.getStep() == SFGUI.WAR_DECLARE_GOVERNMENT) {
			if ("confirm".equals(id)) {
				openConfirm(
						player,
						attacker,
						defender,
						WarGoalType.CHANGE_GOVERNMENT,
						null,
						null,
						null,
						holder.getGovernmentLawId(),
						holder.getLeadershipLawId(),
						null,
						demand);
				return;
			}
			openGovernmentLawPicker(
					player, attacker, defender, holder.getGovernmentLawId(), holder.getLeadershipLawId(), id, demand);
		} else if (holder.getStep() == SFGUI.WAR_DECLARE_GOVERNMENT_LAW) {
			String govId = holder.getGovernmentLawId();
			String leadId = holder.getLeadershipLawId();
			if (ChangeGovernmentEligibility.GOVERNMENT_GROUP.equalsIgnoreCase(holder.getPickingGroupId())) {
				govId = id;
			} else if (ChangeGovernmentEligibility.LEADERSHIP_GROUP.equalsIgnoreCase(holder.getPickingGroupId())) {
				leadId = id;
			}
			openGovernmentPicker(player, attacker, defender, govId, leadId, null, demand);
		}
	}

	/**
	 * Sends a chosen goal to its own sub-picker, or straight to confirm when it needs
	 * no extra ids. Also the entry point for a validated declare code, which skips the
	 * goal picker entirely because the code already pinned the goal.
	 */
	public void routeGoal(Player player, Faction attacker, Faction defender, WarGoalType goal) {
		routeGoal(player, attacker, defender, goal, null);
	}

	private void routeGoal(Player player, Faction attacker, Faction defender, WarGoalType goal, WarDemandDraft demand) {
		if (goal == null) return;
		switch (goal) {
			case WAR -> player.sendMessage(WarDeclareCodeService.RETIRED_WAR_GOAL);
			case TRIBUTARY, USURP, OPEN_MARKET ->
					openConfirm(player, attacker, defender, goal, null, null, null, null, null, null, demand);
			case CHANGE_GOVERNMENT -> openGovernmentPicker(player, attacker, defender, null, null, null, demand);
			case SUBJUGATE -> openRelationTypePicker(player, attacker, defender, null, demand);
			case DE_JURE_ANNEX -> openTitlePicker(player, attacker, defender, null, demand);
			case TRANSFER_SUBJECT -> openSubjectPicker(player, attacker, defender, null, demand);
			case PILLAGE -> {
				if (demand == null) {
					openSettlementPicker(player, attacker, defender);
				}
			}
			case OVERTHROW, CHANGE_LAW, CHANGE_TAX, FORCE_PEACE, INDEPENDENCE, NATIONHOOD -> {
			}
		}
	}

	public void handleConfirm(Player player, WarDeclareRequest request, boolean confirmed) {
		Faction attacker = request.getAttacker();
		Faction defender = request.getDefender();
		if (FactionManager.getByString(attacker.getId()) != attacker
				|| FactionManager.getByString(defender.getId()) != defender
				|| !attacker.isLeader(Members.key(player))) {
			WarDeclareCodeService.clearSession(player);
			player.sendMessage("§cThis war declaration is no longer available.");
			return;
		}
		if (!confirmed) {
			returnToPreviousPicker(player, request);
			return;
		}
		WarValidationResult validation = new WarGoalValidator().validate(request);
		if (!validation.isValid()) {
			player.sendMessage(validation.getMessage());
			returnToPreviousPicker(player, request);
			return;
		}
		executeDeclare(player, request);
	}

	/**
	 * Back out of a goal's sub-picker. A code session means the goal was pinned, so the
	 * goal picker must not reappear: the pin is the point, and reopening it would offer
	 * goals the confirm step is going to refuse anyway.
	 */
	private void backOutOfSubPicker(Player player, Faction attacker, Faction defender, WarDemandDraft demand) {
		if (demand != null) {
			openGoalPicker(player, attacker, defender, null, demand);
			return;
		}
		if (WarDeclareCodeService.session(player) != null) {
			WarDeclareCodeService.clearSession(player);
			player.sendMessage("§7War declaration cancelled. Your code is still unused.");
			inv.diplomacyView(null, player, defender, true);
			return;
		}
		openGoalPicker(player, attacker, defender);
	}

	private void returnToPreviousPicker(Player player, WarDeclareRequest request) {
		Faction attacker = request.getAttacker();
		Faction defender = request.getDefender();
		switch (request.getGoal()) {
			case WAR, TRIBUTARY, USURP, OPEN_MARKET, OVERTHROW, CHANGE_LAW, CHANGE_TAX, FORCE_PEACE, INDEPENDENCE, NATIONHOOD ->
					backOutOfSubPicker(player, attacker, defender, null);
			case CHANGE_GOVERNMENT -> openGovernmentPicker(
					player, attacker, defender, request.getGovernmentLawId(), request.getLeadershipLawId());
			case SUBJUGATE -> openRelationTypePicker(player, attacker, defender);
			case DE_JURE_ANNEX -> openTitlePicker(player, attacker, defender);
			case TRANSFER_SUBJECT -> openSubjectPicker(player, attacker, defender);
			case PILLAGE -> openSettlementPicker(player, attacker, defender);
		}
		player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
	}

	private void openConfirm(
			Player player,
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId,
			String governmentLawId,
			String leadershipLawId,
			String targetSettlementId,
			WarDemandDraft demand) {
		WarDeclareRequest request = new WarDeclareRequest(
				attacker,
				defender,
				goal,
				targetTitleId,
				subjectFactionId,
				relationTypeId,
				governmentLawId,
				leadershipLawId,
				targetSettlementId);
		if (demand != null) {
			finishDemand(player, request.asDemand(), demand);
			return;
		}
		WarValidationResult validation = new WarGoalValidator().validate(request);
		if (!validation.isValid()) {
			player.sendMessage(validation.getMessage());
			return;
		}
		inv.confirmWarDeclareView(player, request);
	}

	/** Opens the goal picker for a war's second demand: the beneficiary demands it of the target. */
	public void openDemandGoalPicker(Player player, Faction beneficiary, Faction target, WarDemandDraft demand) {
		openGoalPicker(player, beneficiary, target, null, demand);
	}

	private void finishDemand(Player player, WarDeclareRequest request, WarDemandDraft demand) {
		War war = WarManager.getById(demand.warId());
		if (war == null || !war.isActive()) {
			player.sendMessage("§cThat war has ended.");
			player.closeInventory();
			return;
		}
		Faction editor = FactionManager.getByLeader(Members.key(player));
		String block = WarDemandRules.editBlock(war, editor, demand.attackerSide());
		if (block != null) {
			player.sendMessage(block);
			returnToDemands(player, demand);
			return;
		}
		WarValidationResult validation = WarDemandRules.validate(war, demand.attackerSide(), request);
		if (!validation.isValid()) {
			player.sendMessage(validation.getMessage());
			return;
		}
		WarDemand set = WarDemand.fromRequest(request);
		war.setSecondDemand(demand.attackerSide(), set);
		WarManager.persist(war);
		Faction beneficiary = request.getAttacker();
		Faction target = request.getDefender();
		player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		player.sendMessage("§aSecond demand set: §f" + set.describeGoal() + " §aof " + target.getName()
				+ " §afor " + beneficiary.getName() + "§a.");
		if (editor != null && !editor.getId().equalsIgnoreCase(beneficiary.getId())) {
			Player ally = Members.player(beneficiary.getLeader());
			if (ally != null && ally.isOnline()) {
				ally.sendMessage(editor.getName() + " §7will demand §f" + set.describeGoal() + " §7of "
						+ target.getName() + " §7for you if your side wins.");
			}
		}
		returnToDemands(player, demand);
	}

	private void returnToDemands(Player player, WarDemandDraft demand) {
		War war = WarManager.getById(demand.warId());
		if (war == null) {
			player.closeInventory();
			return;
		}
		inv.warDemandsView(player, war);
	}

	private void executeDeclare(Player player, WarDeclareRequest request) {
		// The war button gates the entry, but pendingWarDeclares is a separate map, so
		// the session is re-checked here against the pairing and goal actually confirmed.
		WarDeclareCodeService.Session session = null;
		if (WarDeclareCodeService.isRequired(player)) {
			session = WarDeclareCodeService.session(player);
			if (!WarDeclareCodeService.covers(session, request)) {
				WarDeclareCodeService.clearSession(player);
				player.sendMessage("§cYour war code does not cover this declaration.");
				return;
			}
		}
		War war = WarManager.declareWar(
				request.getAttacker(),
				request.getDefender(),
				request.getGoal(),
				request.getTargetTitleId(),
				request.getSubjectFactionId(),
				request.getRelationTypeId(),
				request.getGovernmentLawId(),
				request.getLeadershipLawId(),
				request.getTargetSettlementId());
		if (war == null) {
			// The code stays unspent: a goal-validator or navy-gate refusal must not burn
			// a staff-approved ticket, so the leader can fix the problem and try again.
			String error = WarManager.getLastDeclareError();
			player.sendMessage(error != null ? error : "§cCould not declare war.");
			return;
		}
		player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		if (session != null) {
			redeemCode(player, session, war);
		}
		inv.warList(player);
	}

	private void redeemCode(Player player, WarDeclareCodeService.Session session, War war) {
		WarDeclareCodeService.clearSession(player);
		LogManager.war(
				"DECLARE_CODE warId=%d code=%s player=%s goal=%s",
				war.getId(),
				session.code,
				player.getName(),
				war.getGoal());
		SimpleFactions plugin = SimpleFactions.getInstance();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			WarDeclareCodeService.Result result = WarDeclareCodeService.redeem(
					session.code, session.attackerId, session.defenderId, war.getId());
			if (result.ok) {
				return;
			}
			// The war already exists, so this is a staff cleanup problem rather than
			// something to undo or to tell the leader about.
			Bukkit.getScheduler().runTask(plugin, () -> LogManager.war(
					"DECLARE_CODE_UNSPENT warId=%d code=%s reason=%s",
					war.getId(),
					session.code,
					result.error));
		});
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void openGovernmentLawPicker(
			Player player,
			Faction attacker,
			Faction defender,
			String governmentLawId,
			String leadershipLawId,
			String groupId,
			WarDemandDraft demand) {
		LawGroup group = ChangeGovernmentEligibility.group(defender, groupId);
		if (group == null || group.getLaws() == null) {
			return;
		}
		String selectedId = ChangeGovernmentEligibility.GOVERNMENT_GROUP.equalsIgnoreCase(groupId)
				? governmentLawId
				: leadershipLawId;
		DeclareWarHolder holder = new DeclareWarHolder(
				attacker.getId(),
				defender.getId(),
				SFGUI.WAR_DECLARE_GOVERNMENT_LAW,
				governmentLawId,
				leadershipLawId,
				groupId).withDemand(demand);
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(holder, 27, title(demand, "Law"));
		int slot = 0;
		for (Law law : group.getLaws().values()) {
			if (slot >= 26) break;
			boolean selected = law != null && law.getId() != null && law.getId().equalsIgnoreCase(selectedId);
			inventory.setItem(slot++, creator.createGovernmentLawItem(law, selected));
		}
		inventory.setItem(26, inv.createBackButton(SFGUI.WAR_DECLARE_GOVERNMENT_LAW));
		relabelForDemand(inventory, demand);
		player.openInventory(inventory);
	}

	/** The ruler's goal in a civil war falls inside their own realm, where usurp and the civil war lock do not apply. */
	private static boolean withinRealm(WarDemandDraft demand) {
		return demand != null && WarDemandRules.isCivilWar(WarManager.getById(demand.warId()));
	}

	private static List<DeJureTitleOption> titleOptions(Faction attacker, Faction defender, WarDemandDraft demand) {
		return withinRealm(demand)
				? WarDeclareHelper.deJureTitleOptions(attacker, defender, true)
				: WarDeclareHelper.deJureTitleOptions(attacker, defender);
	}

	private static List<Faction> subjects(Faction defender, WarDemandDraft demand) {
		return withinRealm(demand)
				? WarDeclareHelper.defenderSubjects(defender, true)
				: WarDeclareHelper.defenderSubjects(defender);
	}

	private static String title(WarDemandDraft demand, String step) {
		return (demand == null ? "§7Declare War - " : "§7War Demand - ") + step;
	}

	/**
	 * The pickers are written for a declaration. A second demand can fall on the attacker,
	 * so it names the target neutrally and asks the player to choose rather than declare.
	 */
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private static void relabelForDemand(Inventory inventory, WarDemandDraft demand) {
		if (demand == null) {
			return;
		}
		for (ItemStack item : inventory.getContents()) {
			ItemMeta meta = item == null ? null : item.getItemMeta();
			if (meta == null || meta.getLore() == null) {
				continue;
			}
			List<String> lore = new ArrayList<>();
			for (String line : meta.getLore()) {
				lore.add(line.replace("Click to declare", "Click to choose")
						.replace("The defender becomes", "They become")
						.replace("the defender's", "their")
						.replace("the defender", "them"));
			}
			meta.setLore(lore);
			item.setItemMeta(meta);
		}
	}

	private static Law lawInGroup(LawGroup group, String lawId) {
		for (Law law : group.getLaws().values()) {
			if (law != null && law.getId() != null && law.getId().equalsIgnoreCase(lawId)) {
				return law;
			}
		}
		return null;
	}
}
