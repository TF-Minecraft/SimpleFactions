package me.Plugins.SimpleFactions.Managers.Inventory;


import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.Holder.CampaignInventoryHolder;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickEligibility;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults.InstallationPickToggleResult;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.installation.Installation;

public class CampaignInstallationPickView {
	private static final int SUMMARY_SLOT = 10;
	private static final int LIST_START_SLOT = 12;
	private static final int LIST_END_SLOT = 44;
	private static final int BACK_SLOT = 53;

	public InventoryManager inv;
	public CampaignCreator creator = new CampaignCreator();

	public CampaignInstallationPickView(InventoryManager inv) {
		this.inv = inv;
	}

	public void open(Player player, War war, Faction viewerFaction) {
		open(player, war, viewerFaction, true);
	}

	public void open(Player player, War war, Faction viewerFaction, boolean openInventory) {
		open(player, war, viewerFaction, openInventory, null);
	}

	public void open(Player player, War war, Faction viewerFaction, boolean openInventory, Inventory existingInventory) {
		if (war == null || !war.isActive() || viewerFaction == null) {
			player.sendMessage("§cWar not found.");
			return;
		}
		if (!war.isParticipating(viewerFaction)) {
			player.sendMessage("§cYou are not a belligerent in this war.");
			return;
		}

		boolean locked = BattleInstallationPickService.isLocked(war, CampaignClock.now());
		Set<String> picks = BattleInstallationPickService.getPicks(war, viewerFaction.getId());

		Inventory inventory = existingInventory != null ? existingInventory
				: SimpleFactions.plugin.getServer().createInventory(
						new CampaignInventoryHolder(war.getId(), SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW),
						54,
						war.getName() + " §7Installations");
		inventory.clear();

		inventory.setItem(SUMMARY_SLOT, creator.createInstallationPickSummaryItem(war, viewerFaction, locked));

		List<Installation> installations = new ArrayList<>(
				BattleInstallationPickEligibility.listPickableInstallations(war, viewerFaction));

		for (int index = 0; index < installations.size(); index++) {
			int slot = LIST_START_SLOT + index;
			if (slot > LIST_END_SLOT) {
				break;
			}
			Installation installation = installations.get(index);
			boolean selected = picks.contains(installation.getId());
			boolean zocLocked = BattleInstallationPickService.isDefenderZocPort(
					war, viewerFaction, installation.getId());
			inventory.setItem(
					slot,
					creator.createInstallationPickToggleItem(war, installation, selected, locked, zocLocked));
		}
		for (int slot = LIST_START_SLOT + installations.size(); slot <= LIST_END_SLOT; slot++) {
			inventory.setItem(slot, new ItemStack(Material.AIR, 1));
		}

		inventory.setItem(BACK_SLOT, inv.createBackButton(SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW));
		if (openInventory) {
			player.openInventory(inventory);
		}
	}

	public void click(InventoryClickEvent e, Inventory inventory, Player player) {
		if (!(inventory.getHolder() instanceof CampaignInventoryHolder holder)) {
			return;
		}
		if (holder.getType() != SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW) {
			return;
		}
		e.setCancelled(true);

		War war = WarManager.getById(holder.getWarId());
		if (war == null || !war.isActive()) {
			player.sendMessage("§cWar not found.");
			return;
		}

		int slot = e.getSlot();
		if (slot == BACK_SLOT) {
			inv.campaignView.campaignView(player, war, true);
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			return;
		}

		ItemStack clicked = e.getCurrentItem();
		if (clicked == null || clicked.getItemMeta() == null) {
			return;
		}
		ItemMeta meta = clicked.getItemMeta();

		Integer pickWarId = meta.getPersistentDataContainer().get(
				CampaignCreator.installationPickWarKey(),
				PersistentDataType.INTEGER);
		String installationId = meta.getPersistentDataContainer().get(
				CampaignCreator.installationPickIdKey(),
				PersistentDataType.STRING);
		if (pickWarId == null || installationId == null || pickWarId != war.getId()) {
			return;
		}

		Faction viewerFaction = resolveViewerFaction(player, war);
		if (viewerFaction == null) {
			player.sendMessage("§cYou are not a belligerent in this war.");
			return;
		}

		if (BattleInstallationPickService.isLocked(war, CampaignClock.now())) {
			player.sendMessage("§cInstallation choices are locked until the next battle day.");
			return;
		}

		if (!viewerFaction.isLeader(player.getName())) {
			player.sendMessage("§cOnly your faction leader can select installations for this battle.");
			return;
		}

		Installation installation = viewerFaction.getInstallationHandler().getById(installationId);
		String installationName = installation != null ? installation.getName() : installationId;

		InstallationPickToggleResult result = BattleInstallationPickService.togglePick(
				war, viewerFaction, player.getName(), installationId);

		switch (result) {
			case ADDED -> player.sendMessage("§aCommitted " + installationName + " for this battle.");
			case REMOVED -> player.sendMessage("§7Uncommitted " + installationName + ".");
			case REJECTED_LOCKED -> player.sendMessage("§cInstallation choices are locked until the next battle day.");
			case REJECTED_ZOC_PORT -> player.sendMessage("§cThe ZOC port is required for this naval battle.");
			case REJECTED_NOT_LEADER -> player.sendMessage("§cOnly your faction leader can select installations for this battle.");
			case REJECTED_NOT_PARTICIPANT -> player.sendMessage("§cYou are not a belligerent in this war.");
			case REJECTED_INVALID_INSTALLATION -> player.sendMessage("§cThat installation is not available.");
			case REJECTED_WAR_INACTIVE -> player.sendMessage("§cWar not found.");
			default -> {
				return;
			}
		}

		if (result == InstallationPickToggleResult.ADDED || result == InstallationPickToggleResult.REMOVED) {
			WarManager.persist(war);
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			open(player, war, viewerFaction, true);
		}
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
}
