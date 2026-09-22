package me.Plugins.SimpleFactions.War.campaign.raid.fight;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService;
import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleJoinService;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationLookup;
import me.Plugins.SimpleFactions.installation.InstallationSpawnService;

public final class CampaignRaidBattleService {
	private CampaignRaidBattleService() {}

	public static String raidBattleId(CampaignRaid raid) {
		if (raid == null || raid.getId() == null || raid.getId().isBlank()) {
			return null;
		}
		return raid.getId();
	}

	public static boolean isCampaignRaidBattle(War war, Battle battle) {
		if (battle == null) {
			return false;
		}
		if (battle.isCampaignRaid()) {
			return true;
		}
		if (war == null || battle.getBattleType() != BattleType.RAID) {
			return false;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		return matchesRaidBattle(raid, battle.getId());
	}

	public static boolean matchesRaidBattle(CampaignRaid raid, String battleId) {
		if (raid == null || battleId == null || battleId.isBlank()) {
			return false;
		}
		if (battleId.equalsIgnoreCase(raid.getId())) {
			return true;
		}
		return raid.getBattleId() != null && battleId.equalsIgnoreCase(raid.getBattleId());
	}

	public static void markAsCampaignRaidIfActive(War war, Battle battle) {
		if (!isCampaignRaidBattle(war, battle)) {
			return;
		}
		battle.setCampaignRaid(true);
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid != null && (raid.getBattleId() == null || raid.getBattleId().isBlank())) {
			raid.setBattleId(battle.getId());
		}
	}

	public static boolean isCampaignRaidEvent(War war, BattleEndedEvent event) {
		if (event != null && event.isCampaignRaid()) {
			return true;
		}
		if (war == null || event == null) {
			return false;
		}
		Battle battle = BattleManager.getByString(event.getBattleId());
		return isCampaignRaidBattle(war, battle);
	}

	public static Battle createAndStart(War war, CampaignRaid raid, Instant now) {
		if (war == null || raid == null || now == null) {
			return null;
		}
		Installation source = InstallationLookup.findById(raid.getSourceInstallationId());
		Installation target = InstallationLookup.findById(raid.getTargetInstallationId());
		if (source == null || target == null) {
			return null;
		}
		Location sourceCenter = InstallationSpawnService.resolveCenter(source);
		Location targetCenter = InstallationSpawnService.resolveCenter(target);
		if (sourceCenter == null || targetCenter == null) {
			return null;
		}

		String battleId = raidBattleId(raid);
		Battle existing = BattleManager.getByString(battleId);
		if (existing != null && existing.hasStarted()) {
			CampaignRaidBossBarService.onFightStarted(existing, raid);
			return existing;
		}

		Battle battle = existing != null ? existing : BattleFactory.createBlank(BattleType.RAID, battleId);
		battle.setCampaignRaid(true);
		battle.setWarId(war.getId());
		battle.setProvinceId(target.getProvince());
		battle.setLocked(false);
		battle.setTeleport(false);
		battle.setDisplayName(raid.getDisplayName() != null && !raid.getDisplayName().isBlank()
				? raid.getDisplayName()
				: "Campaign raid at " + target.getName());
		if (existing == null) {
			BattleFactory.applyTemplate(battle, Cache.battleCampaignTemplateRaid);
			BattleManager.addBattle(battle);
		}
		// applyTemplate resets layout first, which clears campaignRaid before reading it
		// back off the template. Restate both flags so a hand-edited template cannot
		// turn a campaign raid into a loot payout.
		battle.setCampaignRaid(true);
		battle.setLootEnabled(false);

		CampaignRaidWarbandService.createRaidWarbands(war, raid);
		Warband attackerWarband = CampaignRaidWarbandService.getAttackerWarband(raid);
		Warband defenderWarband = CampaignRaidWarbandService.getDefenderWarband(raid);
		if (attackerWarband == null || defenderWarband == null) {
			return null;
		}

		applySpawn(battle, attackerWarband.getCampaignSideId(), sourceCenter, true);
		applySpawn(battle, defenderWarband.getCampaignSideId(), targetCenter, false);
		enrollRaidWarbands(raid, battle, attackerWarband, defenderWarband);

		if (battle.hasStarted()) {
			return battle;
		}

		String startError = battle.start();
		if (startError != null) {
			return null;
		}

		teleportAttackerWarband(attackerWarband, sourceCenter);
		alertDefenders(war, raid, target);
		raid.setBattleId(battleId);
		CampaignRaidBossBarService.onFightStarted(battle, raid);
		BattlePersistenceService.persistBattle(battle);
		return battle;
	}

	public static void enrollRaidWarbands(
			CampaignRaid raid,
			Battle battle,
			Warband attackerWarband,
			Warband defenderWarband) {
		if (raid == null || battle == null) {
			return;
		}
		if (attackerWarband == null) {
			attackerWarband = CampaignRaidWarbandService.getAttackerWarband(raid);
		}
		if (defenderWarband == null) {
			defenderWarband = CampaignRaidWarbandService.getDefenderWarband(raid);
		}
		if (attackerWarband != null && attackerWarband.getCampaignSideId() != null) {
			joinWarband(attackerWarband, battle, attackerWarband.getCampaignSideId());
		}
		if (defenderWarband != null && defenderWarband.getCampaignSideId() != null) {
			joinWarband(defenderWarband, battle, defenderWarband.getCampaignSideId());
		}
	}

	private static void joinWarband(Warband warband, Battle battle, String sideId) {
		String error = BattleJoinService.join(warband, battle, sideId);
		if (error != null) {
			BattleSide side = battle.getSideById(sideId);
			if (side != null && !side.getBands().contains(warband)) {
				side.addBand(warband);
			}
		}
	}

	private static void applySpawn(Battle battle, String sideId, Location location, boolean setJail) {
		BattleSide side = battle.getSideById(sideId);
		if (side == null || location == null) {
			return;
		}
		side.setSpawn(location);
		if (setJail) {
			side.setJail(location);
		}
	}

	private static void teleportAttackerWarband(Warband warband, Location destination) {
		if (warband == null || destination == null) {
			return;
		}
		for (Player player : warband.getPlayers()) {
			if (player != null && player.isOnline()) {
				player.teleport(destination);
			}
		}
	}

	private static void alertDefenders(War war, CampaignRaid raid, Installation target) {
		CampaignCoalition defendingCoalition = raid.getAttackerCoalition() != null
				? raid.getAttackerCoalition().opposing()
				: null;
		if (defendingCoalition == null) {
			return;
		}
		Side defendingSide = defendingCoalition == CampaignCoalition.AGGRESSOR
				? war.getAttackers()
				: war.getDefenders();
		String targetName = target != null ? target.getName() : "the installation";
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(defendingSide)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendTitle("§cRAID INCOMING", "§eDefend " + targetName, 10, 120, 10);
				player.playSound(player, Sound.ITEM_GOAT_HORN_SOUND_2, SoundCategory.MASTER, 10f, 0.6f);
			}
		}
	}
}
