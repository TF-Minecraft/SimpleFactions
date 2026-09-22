package me.Plugins.SimpleFactions.Managers.Inventory;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults;
import java.time.Instant;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.Holder.CampaignRaidLaunchHolder;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidEligibilityService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidMessages;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.LaunchResult;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.ValidateLaunchOutcome;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.ValidateLaunchResult;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidTargetCandidate;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.Installation;

public class CampaignRaidLaunchView {
	private static final int SUMMARY_SLOT = 10;
	private static final int LIST_START_SLOT = 12;
	private static final int LIST_END_SLOT = 44;
	private static final int BACK_SLOT = 53;

	public InventoryManager inv;
	public CampaignCreator creator = new CampaignCreator();

	public CampaignRaidLaunchView(InventoryManager inv) {
		this.inv = inv;
	}

	public void openSourcePage(Player player, War war, Faction viewerFaction) {
		openSourcePage(player, war, viewerFaction, true);
	}

	public void openSourcePage(Player player, War war, Faction viewerFaction, boolean openInventory) {
		openSourcePage(player, war, viewerFaction, openInventory, null);
	}

	public void openSourcePage(Player player, War war, Faction viewerFaction, boolean openInventory, Inventory existingInventory) {
		if (!validateParticipation(player, war, viewerFaction)) {
			return;
		}
		Instant now = CampaignClock.now();
		List<Installation> sources = CampaignRaidEligibilityService.listValidSources(
				war, viewerFaction.getId(), now);

		Inventory inventory = existingInventory != null ? existingInventory
				: SimpleFactions.plugin.getServer().createInventory(
						new CampaignRaidLaunchHolder(war.getId(), null),
						54,
						war.getName() + " §7Raid - Source");
		inventory.clear();

		inventory.setItem(SUMMARY_SLOT, creator.createRaidLaunchSummaryItem(
				war, viewerFaction, null, sources.isEmpty(), now));

		for (int index = 0; index < sources.size(); index++) {
			int slot = LIST_START_SLOT + index;
			if (slot > LIST_END_SLOT) {
				break;
			}
			Installation installation = sources.get(index);
			inventory.setItem(
					slot,
					creator.createRaidLaunchInstallationItem(war, installation, null, true));
		}
		clearUnusedListSlots(inventory, sources.size());
		inventory.setItem(BACK_SLOT, inv.createBackButton(SFGUI.CAMPAIGN_VIEW));
		if (openInventory) {
			player.openInventory(inventory);
		}
	}

	public void openTargetPage(Player player, War war, Faction viewerFaction, String sourceInstallationId) {
		openTargetPage(player, war, viewerFaction, sourceInstallationId, true);
	}

	public void openTargetPage(
			Player player,
			War war,
			Faction viewerFaction,
			String sourceInstallationId,
			boolean openInventory) {
		openTargetPage(player, war, viewerFaction, sourceInstallationId, openInventory, null);
	}

	public void openTargetPage(
			Player player,
			War war,
			Faction viewerFaction,
			String sourceInstallationId,
			boolean openInventory,
			Inventory existingInventory) {
		if (!validateParticipation(player, war, viewerFaction)) {
			return;
		}
		Instant now = CampaignClock.now();
		Installation source = viewerFaction.getInstallationHandler().getById(sourceInstallationId);
		List<RaidTargetCandidate> targets = CampaignRaidEligibilityService.listValidTargets(
				war, viewerFaction.getId(), sourceInstallationId, now);

		Inventory inventory = existingInventory != null ? existingInventory
				: SimpleFactions.plugin.getServer().createInventory(
						new CampaignRaidLaunchHolder(war.getId(), sourceInstallationId),
						54,
						war.getName() + " §7Raid - Target");
		inventory.clear();

		inventory.setItem(SUMMARY_SLOT, creator.createRaidLaunchSummaryItem(
				war, viewerFaction, source, targets.isEmpty(), now));

		for (int index = 0; index < targets.size(); index++) {
			int slot = LIST_START_SLOT + index;
			if (slot > LIST_END_SLOT) {
				break;
			}
			RaidTargetCandidate candidate = targets.get(index);
			Faction owner = FactionManager.getByString(candidate.ownerFactionId());
			String ownerName = owner != null ? owner.getName() : candidate.ownerFactionId();
			inventory.setItem(
					slot,
					creator.createRaidLaunchInstallationItem(
							war, candidate.installation(), ownerName, false));
		}
		clearUnusedListSlots(inventory, targets.size());
		inventory.setItem(BACK_SLOT, inv.createBackButton(SFGUI.CAMPAIGN_RAID_LAUNCH_VIEW));
		if (openInventory) {
			player.openInventory(inventory);
		}
	}

	public void click(InventoryClickEvent e, Inventory inventory, Player player) {
		if (!(inventory.getHolder() instanceof CampaignRaidLaunchHolder holder)) {
			return;
		}
		e.setCancelled(true);

		War war = WarManager.getById(holder.getWarId());
		if (war == null || !war.isActive()) {
			player.sendMessage(CampaignRaidMessages.WAR_INACTIVE);
			return;
		}

		Faction viewerFaction = resolveViewerFaction(player, war);
		if (viewerFaction == null) {
			player.sendMessage(CampaignRaidMessages.NOT_PARTICIPANT);
			return;
		}

		int slot = e.getSlot();
		if (slot == BACK_SLOT) {
			if (holder.isSourcePage()) {
				inv.campaignView.campaignView(player, war, true);
			} else {
				openSourcePage(player, war, viewerFaction, true);
			}
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			return;
		}

		if (!viewerFaction.isLeader(player.getName())) {
			player.sendMessage(CampaignRaidMessages.NOT_LEADER);
			return;
		}

		ItemStack clicked = e.getCurrentItem();
		if (clicked == null || clicked.getItemMeta() == null) {
			return;
		}
		ItemMeta meta = clicked.getItemMeta();

		Integer launchWarId = meta.getPersistentDataContainer().get(
				CampaignCreator.raidLaunchWarKey(),
				PersistentDataType.INTEGER);
		String installationId = meta.getPersistentDataContainer().get(
				CampaignCreator.raidLaunchInstallationIdKey(),
				PersistentDataType.STRING);
		if (launchWarId == null || installationId == null || launchWarId != war.getId()) {
			return;
		}

		Instant now = CampaignClock.now();
		if (holder.isSourcePage()) {
			if (CampaignRaidService.canLaunch(war, viewerFaction, now) != LaunchResult.STARTED) {
				sendLaunchFailure(player, war, viewerFaction, now, null);
				return;
			}
			openTargetPage(player, war, viewerFaction, installationId, true);
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			return;
		}

		String sourceId = holder.getSourceInstallationId();
		ValidateLaunchOutcome outcome = CampaignRaidEligibilityService.validateLaunch(
				war, viewerFaction.getId(), sourceId, installationId, now);
		if (outcome.result() != ValidateLaunchResult.OK) {
			String message = CampaignRaidMessages.messageForValidateResult(outcome.result());
			if (message != null) {
				player.sendMessage(message);
			}
			return;
		}

		LaunchResult launch = CampaignRaidService.beginMuster(
				war, viewerFaction, sourceId, installationId, now);
		if (launch != LaunchResult.STARTED) {
			sendLaunchFailure(player, war, viewerFaction, now, outcome);
			return;
		}

		WarManager.persist(war);
		Installation target = resolveTargetInstallation(war, viewerFaction, installationId);
		broadcastRaidCalled(war, viewerFaction, target);
		player.sendMessage("§aCampaign raid muster started.");
		player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		inv.campaignView.campaignView(player, war, true);
	}

	private void sendLaunchFailure(
			Player player,
			War war,
			Faction faction,
			Instant now,
			ValidateLaunchOutcome ignored) {
		LaunchResult launch = CampaignRaidService.canLaunch(war, faction, now);
		String message = CampaignRaidMessages.messageForLaunchResult(launch);
		if (message != null) {
			player.sendMessage(message);
		}
	}

	private void broadcastRaidCalled(War war, Faction launcher, Installation target) {
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null) {
			return;
		}
		String message = CampaignRaidMessages.buildRaidCalledMessage(launcher, target, raid.getId());
		Side side = war.getSide(launcher);
		if (side == null) {
			return;
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(side)) {
			Player online = Bukkit.getPlayerExact(memberName);
			if (online != null && online.isOnline()) {
				online.sendMessage(message);
			}
		}
	}

	private Installation resolveTargetInstallation(War war, Faction launcher, String targetInstallationId) {
		if (war == null || launcher == null || targetInstallationId == null) {
			return null;
		}
		Side enemySide = war.getOppositeSide(launcher);
		if (enemySide == null) {
			return null;
		}
		for (Faction enemy : BattleSideMembers.collectParticipatingFactions(enemySide)) {
			if (enemy == null || enemy.getInstallationHandler() == null) {
				continue;
			}
			Installation installation = enemy.getInstallationHandler().getById(targetInstallationId);
			if (installation != null) {
				return installation;
			}
		}
		return null;
	}

	private boolean validateParticipation(Player player, War war, Faction viewerFaction) {
		if (war == null || !war.isActive()) {
			player.sendMessage(CampaignRaidMessages.WAR_INACTIVE);
			return false;
		}
		if (viewerFaction == null || !war.isParticipating(viewerFaction)) {
			player.sendMessage(CampaignRaidMessages.NOT_PARTICIPANT);
			return false;
		}
		return true;
	}

	private Faction resolveViewerFaction(Player player, War war) {
		Faction leaderFaction = FactionManager.getByLeader(player.getName());
		if (leaderFaction != null && war.isParticipating(leaderFaction)) {
			return leaderFaction;
		}
		Faction memberFaction = FactionManager.getByMember(player.getName());
		if (memberFaction != null && war.isParticipating(memberFaction)) {
			return memberFaction;
		}
		return null;
	}

	private void clearUnusedListSlots(Inventory inventory, int usedCount) {
		for (int slot = LIST_START_SLOT + usedCount; slot <= LIST_END_SLOT; slot++) {
			inventory.setItem(slot, new ItemStack(Material.AIR, 1));
		}
	}
}
