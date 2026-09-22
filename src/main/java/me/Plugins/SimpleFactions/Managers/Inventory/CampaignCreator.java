package me.Plugins.SimpleFactions.Managers.Inventory;


import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.pathfinder.TitleManagerProvinceOwnerLookup;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignNavyGate;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignRouteEntry;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignRouteRenderer;
import me.Plugins.SimpleFactions.War.campaign.vote.VoteResults.BattleHourTally;
import me.Plugins.SimpleFactions.War.campaign.vote.BattleQuorumService;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleLookups;
import me.Plugins.SimpleFactions.War.campaign.vote.BattleVoteService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignScheduleCountdown;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiCopy;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiTimeFormatter;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidLaunchAvailability;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidLaunchAvailability.LaunchAvailability;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.Managers.Inventory.IconGetter;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class CampaignCreator {
	private final TitleManagerProvinceOwnerLookup owners = new TitleManagerProvinceOwnerLookup();
	private final InstallationCreator installationCreator = new InstallationCreator();

	public static NamespacedKey installationsEntryWarKey() {
		return new NamespacedKey(SimpleFactions.plugin, "campaign_installations_entry_war");
	}

	public static NamespacedKey installationPickWarKey() {
		return new NamespacedKey(SimpleFactions.plugin, "campaign_installation_pick_war");
	}

	public static NamespacedKey installationPickIdKey() {
		return new NamespacedKey(SimpleFactions.plugin, "campaign_installation_pick_id");
	}

	public static NamespacedKey raidEntryWarKey() {
		return new NamespacedKey(SimpleFactions.plugin, "campaign_raid_entry_war");
	}

	public static NamespacedKey raidLaunchWarKey() {
		return new NamespacedKey(SimpleFactions.plugin, "campaign_raid_launch_war");
	}

	public static NamespacedKey raidLaunchInstallationIdKey() {
		return new NamespacedKey(SimpleFactions.plugin, "campaign_raid_launch_installation_id");
	}

	public ItemStack createRouteEntryItem(War war, Faction viewer, CampaignRouteEntry entry) {
		Optional<ScheduledCampaignBattle> slot = CampaignScheduleService.slotAt(
				war,
				entry.scheduleIndex(),
				entry.scheduleLeg());
		if (slot.isEmpty()) {
			ItemStack fallback = new ItemStack(Material.BARRIER, 1);
			ItemMeta fallbackMeta = fallback.getItemMeta();
			fallbackMeta.setDisplayName(StringFormatter.formatHex(
					CampaignUiCopy.VALUE + "Province " + entry.provinceId()));
			fallback.setItemMeta(fallbackMeta);
			return fallback;
		}
		ScheduledCampaignBattle battle = slot.get();
		Material material = CampaignRouteRenderer.resolveRouteEntryMaterial(war, viewer, entry, battle, owners);
		ItemStack item = new ItemStack(material, 1);
		ItemMeta meta = item.getItemMeta();
		String displayName = resolveRouteDisplayName(war, entry, battle);
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + displayName));
		meta.setLore(CampaignRouteRenderer.buildRouteLore(war, entry, owners));
		NamespacedKey provinceKey = new NamespacedKey(SimpleFactions.plugin, "campaign_province");
		meta.getPersistentDataContainer().set(provinceKey, PersistentDataType.INTEGER, entry.provinceId());
		if (entry.hasBattleSlot()) {
			NamespacedKey scheduleKey = new NamespacedKey(SimpleFactions.plugin, "campaign_schedule_index");
			meta.getPersistentDataContainer().set(scheduleKey, PersistentDataType.INTEGER, entry.scheduleIndex());
			NamespacedKey legKey = new NamespacedKey(SimpleFactions.plugin, "campaign_schedule_leg");
			meta.getPersistentDataContainer().set(legKey, PersistentDataType.STRING, entry.scheduleLeg().name());
		}
		item.setItemMeta(meta);
		return item;
	}

	private String resolveRouteDisplayName(War war, CampaignRouteEntry entry, ScheduledCampaignBattle slot) {
		return BattleNamingService.resolveScheduledDisplayName(
				war,
				entry.scheduleLeg(),
				entry.scheduleIndex(),
				slot,
				entry.provinceId());
	}

	public ItemStack createFirstBattleMarkerItem() {
		ItemStack item = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_up_gray");
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.STATUS_HIGHLIGHT + "First Battle"));
		meta.setLore(null);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createInfoItem(War war, Faction viewerFaction, UUID viewerUuid) {
		return createInfoItem(war, viewerFaction, viewerUuid, null);
	}

	public ItemStack createInfoItem(
			War war,
			Faction viewerFaction,
			UUID viewerUuid,
			Function<UUID, Faction> uuidToFaction) {
		ItemStack item = new ItemStack(Material.BOOK, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + "Campaign status"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Attacker Initiative: " + CampaignUiCopy.VALUE + war.getInitiativeAttacker()));
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Defender Initiative: " + CampaignUiCopy.VALUE + war.getInitiativeDefender()));
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Initiative Holder: "
						+ CampaignUiCopy.VALUE + CampaignUiCopy.formatInitiativeHolder(war.getInitiativeHolder())));
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Phase: "
						+ CampaignUiCopy.VALUE + CampaignUiCopy.titleCasePhase(war.getCampaignPhase())));
		if (war.isWhitePeaceProposedByAttacker()) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.WARNING + "Attacker proposed white peace"));
		}
		if (war.isWhitePeaceProposedByDefender()) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.WARNING + "Defender proposed white peace"));
		}
		lore.addAll(buildScheduleInfoLines(war, viewerUuid, uuidToFaction));
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	public static List<String> buildScheduleInfoLines(
			War war,
			UUID viewerUuid,
			Function<UUID, Faction> uuidToFaction) {
		List<String> lines = new ArrayList<>();
		if (war == null) {
			return lines;
		}

		LocalDate battleDay = war.getBattleDay();
		lines.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Battle Day: " + CampaignUiCopy.VALUE + CampaignUiCopy.formatBattleDay(battleDay)));
		lines.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Vote Closes: "
						+ CampaignUiCopy.VALUE + CampaignUiTimeFormatter.formatUtcHour(battleDay, Cache.warVoteCloseHour)));
		lines.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Status: "
						+ CampaignUiCopy.STATUS_HIGHLIGHT + CampaignUiCopy.resolveActivityStatus(war)));

		Function<UUID, Faction> factionLookup = uuidToFaction != null
				? uuidToFaction
				: BattleScheduleLookups.uuidToFactionForWar(war);

		BattleSchedulePhase schedulePhase = war.getBattleSchedulePhase();
		if (schedulePhase == BattleSchedulePhase.VOTING) {
			String attackerName = war.getAttackers().getLeader().getName();
			String defenderName = war.getDefenders().getLeader().getName();
			lines.add(StringFormatter.formatHex(CampaignUiCopy.STATUS_HIGHLIGHT + "Hour Votes:"));
			lines.add(StringFormatter.formatHex(
					CampaignUiCopy.MUTED + "(" + attackerName + "/" + defenderName + ")"));
			Map<Integer, BattleHourTally> tally = BattleVoteService.buildHourTally(war, factionLookup);
			for (int hour : BattleWindowService.listValidHours()) {
				BattleHourTally counts = tally.getOrDefault(hour, new BattleHourTally(0, 0));
				String timeLabel = CampaignUiTimeFormatter.formatUtcHour(battleDay, hour);
				lines.add(StringFormatter.formatHex(
						CampaignUiCopy.LABEL + timeLabel
								+ CampaignUiCopy.VALUE + " · " + counts.attackerCount() + "/" + counts.defenderCount()));
			}
			lines.add(StringFormatter.formatHex(
					CampaignUiCopy.LABEL + "Total voters: " + CampaignUiCopy.VALUE
							+ BattleQuorumService.countDistinctVoters(war)));
		}

		Set<Integer> selections = viewerUuid != null
				? new TreeSet<>(BattleVoteService.getPlayerSelections(war, viewerUuid))
				: Set.of();
		String yourHours = selections.isEmpty()
				? "-"
				: selections.stream()
						.map(h -> CampaignUiTimeFormatter.formatUtcHour(battleDay, h))
						.collect(Collectors.joining(", "));
		lines.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Your Hours: " + CampaignUiCopy.VALUE + yourHours));

		if (schedulePhase == BattleSchedulePhase.SCHEDULED) {
			Instant fightAt = war.getScheduledBattleAt();
			if (fightAt != null) {
				lines.add(StringFormatter.formatHex(
						CampaignUiCopy.LABEL + "Fight At: "
								+ CampaignUiCopy.VALUE + CampaignUiTimeFormatter.formatInstant(fightAt)));
				CampaignScheduleCountdown.formatNextMilestone(war, CampaignClock.now())
						.ifPresent(text -> lines.add(StringFormatter.formatHex(CampaignUiCopy.MUTED + text)));
			}
			if (war.getScheduledBattleProvinceId() != null) {
				lines.add(StringFormatter.formatHex(
						CampaignUiCopy.LABEL + "Battle province: "
								+ CampaignUiCopy.VALUE + war.getScheduledBattleProvinceId()));
			}
		}

		return lines;
	}

	public ItemStack createHourToggleItem(
			War war,
			int hour,
			boolean selected,
			boolean clickable,
			BattleHourTally tally) {
		String iconId = selected ? "mcicons:icon_confirm" : "mcicons:icon_cancel";
		ItemStack item = TLibs.getItemAPI().getCreator().getItemsAdderItem(iconId);
		ItemMeta meta = item.getItemMeta();
		LocalDate battleDay = war != null ? war.getBattleDay() : null;
		String timeLabel = CampaignUiTimeFormatter.formatUtcHour(battleDay, hour);
		meta.setDisplayName(StringFormatter.formatHex(
				(selected ? CampaignUiCopy.SELECT : CampaignUiCopy.REMOVE) + timeLabel));
		int attackerVotes = tally != null ? tally.attackerCount() : 0;
		int defenderVotes = tally != null ? tally.defenderCount() : 0;
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.VALUE + attackerVotes + "/" + defenderVotes));
		if (war != null) {
			lore.add(StringFormatter.formatHex(
					CampaignUiCopy.MUTED + "("
							+ war.getAttackers().getLeader().getName()
							+ "/"
							+ war.getDefenders().getLeader().getName()
							+ ")"));
		}
		if (clickable) {
			lore.add(StringFormatter.formatHex(
					selected
							? CampaignUiCopy.REMOVE + "Click to remove this hour"
							: CampaignUiCopy.SELECT + "Click to select this hour"));
		} else {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Voting closed or you are not eligible"));
		}
		meta.setLore(lore);
		if (clickable && war != null) {
			NamespacedKey hourKey = new NamespacedKey(SimpleFactions.plugin, "campaign_vote_hour");
			NamespacedKey warKey = new NamespacedKey(SimpleFactions.plugin, "campaign_vote_war");
			meta.getPersistentDataContainer().set(hourKey, PersistentDataType.INTEGER, hour);
			meta.getPersistentDataContainer().set(warKey, PersistentDataType.INTEGER, war.getId());
		}
		item.setItemMeta(meta);
		return item;
	}

	public static List<String> buildEnemyInstallationIntelLines(
			War war,
			Faction viewerFaction,
			Instant now) {
		List<String> lines = new ArrayList<>();
		if (war == null || viewerFaction == null || !war.isParticipating(viewerFaction)) {
			return lines;
		}
		if (!BattleInstallationPickService.isLocked(war, now)) {
			lines.add(StringFormatter.formatHex(
					CampaignUiCopy.LABEL + "Enemy installation commits are hidden until vote close."));
			return lines;
		}
		lines.add(StringFormatter.formatHex(CampaignUiCopy.STATUS_HIGHLIGHT + "Enemy committed installations:"));
		Map<String, Set<String>> enemyPicks = BattleInstallationPickService.getVisibleEnemyPicks(
				war, viewerFaction.getId(), now);
		for (Map.Entry<String, Set<String>> entry : enemyPicks.entrySet()) {
			Faction enemy = FactionManager.getByString(entry.getKey());
			String factionName = enemy != null ? enemy.getName() : entry.getKey();
			Set<String> picks = entry.getValue();
			if (picks == null || picks.isEmpty()) {
				lines.add(StringFormatter.formatHex(
						CampaignUiCopy.LABEL + factionName + ": " + CampaignUiCopy.MUTED + "(none)"));
				continue;
			}
			List<String> names = new ArrayList<>();
			if (enemy != null) {
				for (String installationId : picks) {
					Installation installation = enemy.getInstallationHandler().getById(installationId);
					names.add(installation != null ? installation.getName() : installationId);
				}
			} else {
				names.addAll(picks);
			}
			lines.add(StringFormatter.formatHex(
					CampaignUiCopy.LABEL + factionName + ": " + CampaignUiCopy.VALUE + String.join(", ", names)));
		}
		return lines;
	}

	public ItemStack createEnemyInstallationIntelItem(War war, Faction viewerFaction) {
		ItemStack item = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + "Enemy installation intel"));
		meta.setLore(buildEnemyInstallationIntelLines(war, viewerFaction, CampaignClock.now()));
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createVotingHelpItem() {
		ItemStack item = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + "Battle hour voting"));
		meta.setLore(List.of(
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Pick any hours you can fight"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "One vote per eligible player"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Most-voted hour wins after vote close"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Coalition totals shown as Attacker/Defender counts")));
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createUnusedHourSlotItem() {
		ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Battle hour slot"));
		meta.setLore(List.of(StringFormatter.formatHex(CampaignUiCopy.LABEL + "No vote hour on this slot")));
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createAutoresolveProposeButton(War war, BelligerentRole side) {
		ItemStack item = new ItemStack(Material.GRAY_BANNER, 1);
		ItemMeta meta = item.getItemMeta();
		String sideLabel = side == BelligerentRole.ATTACKER ? "Attacker" : "Defender";
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + sideLabel + " autoresolve"));
		meta.setLore(List.of(
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Propose skipping the battle vote"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Opposing war leader must accept"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "(60s request, before vote close)")));
		NamespacedKey warKey = new NamespacedKey(SimpleFactions.plugin, "campaign_autoresolve_war");
		NamespacedKey sideKey = new NamespacedKey(SimpleFactions.plugin, "campaign_autoresolve_side");
		meta.getPersistentDataContainer().set(warKey, PersistentDataType.INTEGER, war.getId());
		meta.getPersistentDataContainer().set(sideKey, PersistentDataType.STRING, side.name());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createRetreatButton(War war) {
		ItemStack item = new ItemStack(Material.ORANGE_BANNER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.WARNING + "Retreat"));
		meta.setLore(List.of(
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Concede the active battle slot"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Enemy advances without a fight"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "No initiative cost")));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_retreat");
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createSurrenderButton(War war) {
		ItemStack item = new ItemStack(Material.RED_BANNER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.REMOVE + "Surrender"));
		meta.setLore(List.of(
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Your coalition loses the war"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Opponent wins")));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_surrender");
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createAcceptPeaceButton(War war) {
		ItemStack item = new ItemStack(Material.WHITE_BANNER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.SELECT + "Accept white peace"));
		meta.setLore(List.of(
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "End the war with no goal"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "No reparations")));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_accept_peace");
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createPushButton(War war) {
		boolean navyBlocked = !CampaignNavyGate.winnerCanContestNextNaval(
				war,
				war != null ? war.getPostBattleWinnerCoalition() : null);
		if (navyBlocked) {
			ItemStack item = new ItemStack(Material.GRAY_CONCRETE, 1);
			ItemMeta meta = item.getItemMeta();
			meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.MUTED + "Push"));
			meta.setLore(List.of(StringFormatter.formatHex(CampaignUiCopy.LABEL + CampaignUiCopy.NAVY_BLOCKADE)));
			item.setItemMeta(meta);
			return item;
		}
		ItemStack item = new ItemStack(Material.LIME_CONCRETE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.SELECT + "Push"));
		meta.setLore(List.of(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Continue the offensive")));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_push");
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createHoldButton(War war) {
		ItemStack item = new ItemStack(Material.YELLOW_CONCRETE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.STATUS_HIGHLIGHT + "Hold"));
		meta.setLore(List.of(
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Hold the front"),
				StringFormatter.formatHex(CampaignUiCopy.LABEL + "Auto-propose white peace")));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_hold");
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createLoserAttackButton(War war) {
		ItemStack item = new ItemStack(Material.RED_CONCRETE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.REMOVE + "Attack"));
		meta.setLore(List.of(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Schedule battle at the held front")));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_attack");
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createLoserAcceptPeaceButton(War war) {
		ItemStack item = new ItemStack(Material.WHITE_CONCRETE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.SELECT + "Accept white peace"));
		meta.setLore(List.of(StringFormatter.formatHex(CampaignUiCopy.LABEL + "End the war with no goal")));
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "campaign_loser_peace");
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createInstallationsEntryButton(War war, Faction viewerFaction) {
		ItemStack item = IconGetter.getIconOrDefault("march", Material.GREEN_CONCRETE);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#706964Battle installations"));
		int committed = viewerFaction != null
				? BattleInstallationPickService.getPicks(war, viewerFaction.getId()).size()
				: 0;
		boolean locked = BattleInstallationPickService.isLocked(war, CampaignClock.now());
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Click to commit installations"));
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Committed: " + CampaignUiCopy.VALUE + committed));
		if (locked) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Locked at vote close"));
		} else {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Leader only until vote close"));
		}
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(installationsEntryWarKey(), PersistentDataType.INTEGER, war.getId());
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createInstallationPickSummaryItem(War war, Faction faction, boolean locked) {
		ItemStack item = new ItemStack(Material.GREEN_CONCRETE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + "Battle installation picks"));
		int committed = faction != null
				? BattleInstallationPickService.getPicks(war, faction.getId()).size()
				: 0;
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Committed: " + CampaignUiCopy.VALUE + committed));
		if (locked) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Locked at vote close"));
		} else {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Click installations below to toggle"));
		}
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createInstallationPickToggleItem(
			War war,
			Installation installation,
			boolean selected,
			boolean locked,
			boolean zocLocked) {
		ItemStack item = installationCreator.createInstallationIcon(installation).clone();
		ItemMeta meta = item.getItemMeta();
		List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
		if (selected) {
			lore.add("§aCommitted for this battle");
			meta.addEnchant(Enchantment.UNBREAKING, 1, true);
			meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}
		if (zocLocked) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + CampaignUiCopy.REQUIRED_ZOC_PORT));
		} else if (locked) {
			lore.add("§7Locked at vote close");
		} else if (!selected) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.SELECT + "Click to commit"));
		} else {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.REMOVE + "Click to uncommit"));
		}
		meta.setLore(lore);
		if (war != null && (!locked || zocLocked)) {
			meta.getPersistentDataContainer().set(
					installationPickWarKey(), PersistentDataType.INTEGER, war.getId());
			meta.getPersistentDataContainer().set(
					installationPickIdKey(), PersistentDataType.STRING, installation.getId());
		}
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createStartRaidEntryButton(War war, Faction viewerFaction, Instant now) {
		LaunchAvailability availability = CampaignRaidLaunchAvailability.describe(war, viewerFaction, now);
		ItemStack item = availability.enabled()
				? new ItemStack(Material.CROSSBOW, 1)
				: new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(
				availability.enabled()
						? CampaignUiCopy.REMOVE + "Start raid"
						: CampaignUiCopy.MUTED + "Start raid"));
		meta.setLore(availability.loreLines());
		if (availability.enabled()) {
			meta.getPersistentDataContainer().set(raidEntryWarKey(), PersistentDataType.INTEGER, war.getId());
		}
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createRaidLaunchSummaryItem(
			War war,
			Faction faction,
			Installation selectedSource,
			boolean emptyList,
			Instant now) {
		ItemStack item = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta meta = item.getItemMeta();
		if (selectedSource == null) {
			meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + "Pick raid source"));
			List<String> lore = new ArrayList<>();
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Choose your port or airport"));
			if (emptyList) {
				lore.add(StringFormatter.formatHex(CampaignUiCopy.WARNING + "No valid sources right now"));
			}
			meta.setLore(lore);
		} else {
			meta.setDisplayName(StringFormatter.formatHex(CampaignUiCopy.VALUE + "Pick raid target"));
			List<String> lore = new ArrayList<>();
			lore.add(StringFormatter.formatHex(
					CampaignUiCopy.LABEL + "Source: " + CampaignUiCopy.VALUE + selectedSource.getName()));
			if (emptyList) {
				lore.add(StringFormatter.formatHex(CampaignUiCopy.WARNING + "No valid targets for this source"));
			} else {
				lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + "Click an enemy installation below"));
			}
			meta.setLore(lore);
		}
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createRaidLaunchInstallationItem(
			War war,
			Installation installation,
			String ownerFactionName,
			boolean sourcePage) {
		ItemStack item = installationCreator.createInstallationIcon(installation).clone();
		ItemMeta meta = item.getItemMeta();
		List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
		if (ownerFactionName != null && !ownerFactionName.isBlank()) {
			lore.add(StringFormatter.formatHex(
					CampaignUiCopy.LABEL + "Owner: " + CampaignUiCopy.VALUE + ownerFactionName));
		}
		if (sourcePage) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.SELECT + "Click to pick targets"));
		} else {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.REMOVE + "Click to launch raid"));
		}
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(raidLaunchWarKey(), PersistentDataType.INTEGER, war.getId());
		meta.getPersistentDataContainer().set(
				raidLaunchInstallationIdKey(), PersistentDataType.STRING, installation.getId());
		item.setItemMeta(meta);
		return item;
	}
}
