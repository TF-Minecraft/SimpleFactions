package net.tfminecraft.simplefactions.managers.inventory;


import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults;
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

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.CampaignInventoryHolder;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickEligibility;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults.InstallationPickToggleResult;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.installation.Installation;

public class CampaignInstallationPickView {
	private static final int SUMMARY_SLOT = 10;
	private static final int LIST_START_SLOT = 12;
	private static final int LIST_END_SLOT = 44;
	private static final int BACK_SLOT = 53;
	private static final int PREVIOUS_SLOT = 45;
	private static final int NEXT_SLOT = 52;
	private static final int PAGE_SIZE = LIST_END_SLOT - LIST_START_SLOT + 1;

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

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
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

		CampaignInventoryHolder holder = (CampaignInventoryHolder) inventory.getHolder();
		int lastPage = Math.max(0, (installations.size() - 1) / PAGE_SIZE);
		holder.setPage(Math.min(holder.getPage(), lastPage));
		int start = holder.getPage() * PAGE_SIZE;
		int end = Math.min(start + PAGE_SIZE, installations.size());
		if (holder.getPage() > 0) {
			inventory.setItem(PREVIOUS_SLOT, DefaultCreator.createPreviousPageButton());
		}
		if (holder.getPage() < lastPage) {
			inventory.setItem(NEXT_SLOT, DefaultCreator.createNextPageButton());
		}
		for (int index = start; index < end; index++) {
			int slot = LIST_START_SLOT + index - start;
			Installation installation = installations.get(index);
			boolean selected = picks.contains(installation.getId());
			boolean zocLocked = BattleInstallationPickService.isDefenderZocPort(
					war, viewerFaction, installation.getId());
			inventory.setItem(
					slot,
					creator.createInstallationPickToggleItem(war, installation, selected, locked, zocLocked));
		}
		for (int slot = LIST_START_SLOT + end - start; slot <= LIST_END_SLOT; slot++) {
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

		Faction viewerFaction = resolveViewerFaction(player, war);
		if (viewerFaction == null) {
			player.sendMessage("§cYou are not a belligerent in this war.");
			return;
		}
		if (slot == PREVIOUS_SLOT || slot == NEXT_SLOT) {
			holder.setPage(holder.getPage() + (slot == NEXT_SLOT ? 1 : -1));
			open(player, war, viewerFaction, false, inventory);
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

		player.sendMessage(toggleMessage(result, installationName));

		if (result == InstallationPickToggleResult.ADDED || result == InstallationPickToggleResult.REMOVED) {
			WarManager.persist(war);
			player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			open(player, war, viewerFaction, false, inventory);
		}
	}

	static String toggleMessage(InstallationPickToggleResult result, String installationName) {
		return switch (result) {
			case ADDED -> "§aCommitted " + installationName + " for this battle.";
			case REMOVED -> "§7Uncommitted " + installationName + ".";
			case REJECTED_LOCKED -> "§cInstallation choices are locked until the next battle day.";
			case REJECTED_ZOC_PORT -> "§cThe ZOC port is required for this naval battle.";
			case REJECTED_NOT_LEADER -> "§cOnly your faction leader can select installations for this battle.";
			case REJECTED_NOT_PARTICIPANT -> "§cYou are not a belligerent in this war.";
			case REJECTED_INVALID_INSTALLATION -> "§cThat installation is not available.";
			case REJECTED_WAR_INACTIVE -> "§cWar not found.";
		};
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
