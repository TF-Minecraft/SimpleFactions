package net.tfminecraft.simplefactions.war.campaign.raid.fight;


import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import java.time.Instant;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleJoinService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidEligibilityService;
import net.tfminecraft.simplefactions.installation.InstallationSpawnService;

public final class CampaignRaidBattleService {
	private CampaignRaidBattleService() {}

	public static String raidBattleId(CampaignRaid raid) {
		if (raid == null || raid.getId() == null || raid.getId().isBlank()) {
			return null;
		}
		return raid.getId();
	}

	public static boolean isCampaignRaidBattle(War war, Battle battle) {
		if (battle == null || battle.getBattleType() != BattleType.RAID) {
			return false;
		}
		if (war == null) {
			return battle.isCampaignRaid();
		}
		if (!Objects.equals(battle.getWarId(), war.getId())) {
			return false;
		}
		if (battle.isCampaignRaid()) {
			return true;
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
		if (matchesRaidBattle(raid, battle.getId())
				&& (raid.getBattleId() == null || raid.getBattleId().isBlank())) {
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
		Installation source = CampaignRaidEligibilityService.resolveSourceInstallation(raid);
		Installation target = CampaignRaidEligibilityService.resolveTargetInstallation(war, raid);
		if (source == null || target == null) {
			return null;
		}
		Location sourceCenter = InstallationSpawnService.resolveCenter(source);
		Location targetCenter = InstallationSpawnService.resolveCenter(target);
		if (sourceCenter == null || targetCenter == null) {
			return null;
		}

		String battleId = raidBattleId(raid);
		if (battleId == null) return null;
		Battle existing = BattleManager.getByString(battleId);
		if (existing != null && (!existing.isCampaignRaid()
				|| existing.getBattleType() != BattleType.RAID
				|| !Objects.equals(existing.getWarId(), war.getId()))) {
			return null;
		}
		if (existing != null && existing.hasStarted()) {
			CampaignRaidBossBarService.onFightStarted(existing, raid);
			return existing;
		}

		Battle battle = existing != null ? existing : BattleFactory.createBlank(BattleType.RAID, battleId);
		battle.setCampaignRaid(true);
		battle.setWarId(war.getId());
		battle.setOffensiveCoalition(raid.getAttackerCoalition());
		battle.setProvinceId(target.getProvince());
		battle.setLocked(false);
		battle.setTeleport(false);
		battle.setDisplayName(raid.getDisplayName() != null && !raid.getDisplayName().isBlank()
				? raid.getDisplayName()
				: "Campaign raid at " + target.getName());
		if (existing == null) {
			try {
				BattleFactory.applyTemplate(battle, Cache.battleCampaignTemplateRaid);
			} catch (IllegalArgumentException e) {
				net.tfminecraft.simplefactions.SimpleFactions.getInstance().getLogger().warning(
						"Cannot start campaign raid with template '" + Cache.battleCampaignTemplateRaid
						+ "': " + e.getMessage());
				return null;
			}
		}
		// applyTemplate resets layout first, which clears campaignRaid before reading it
		// back off the template. Restate both flags so a hand-edited template cannot
		// turn a campaign raid into a loot payout.
		battle.setCampaignRaid(true);
		battle.setLootEnabled(false);

		if (battle.getSideById(BattleTemplate.ATTACKER_SIDE) == null
				|| battle.getSideById(BattleTemplate.DEFENDER_SIDE) == null) {
			return null;
		}

		CampaignRaidWarbandService.createRaidWarbands(war, raid);
		Warband attackerWarband = CampaignRaidWarbandService.getAttackerWarband(raid);
		Warband defenderWarband = CampaignRaidWarbandService.getDefenderWarband(raid);
		if (attackerWarband == null || defenderWarband == null) {
			return null;
		}
		if (existing == null) {
			BattleManager.addBattle(battle);
		}

		applySpawn(battle, BattleTemplate.ATTACKER_SIDE, sourceCenter, true);
		applySpawn(battle, BattleTemplate.DEFENDER_SIDE, targetCenter, false);
		enrollRaidWarbands(raid, battle, attackerWarband, defenderWarband);

		battle.start();
		// A BattleStartedEvent listener can end this raid or replace it with a new muster.
		if (!war.isActive() || CampaignRaidService.getActive(war) != raid
				|| !battle.hasStarted() || BattleManager.getByString(battleId) != battle) {
			return null;
		}
		battle.setStartedAt(now);
		war.setFirstBattleStarted(true);
		WarManager.persist(war);

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
		if (attackerWarband != null) {
			joinWarband(attackerWarband, battle, BattleTemplate.ATTACKER_SIDE);
		}
		if (defenderWarband != null) {
			joinWarband(defenderWarband, battle, BattleTemplate.DEFENDER_SIDE);
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
		side.setSpawn(location);
		if (setJail) {
			side.setJail(location);
		}
	}

	private static void teleportAttackerWarband(Warband warband, Location destination) {
		for (Player player : warband.getPlayers()) {
			if (player != null && player.isOnline()) {
				player.teleport(destination);
			}
		}
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private static void alertDefenders(War war, CampaignRaid raid, Installation target) {
		CampaignCoalition defendingCoalition = raid.getAttackerCoalition().opposing();
		Side defendingSide = defendingCoalition == CampaignCoalition.AGGRESSOR
				? war.getAttackers()
				: war.getDefenders();
		String targetName = target.getName();
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(defendingSide)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendTitle("§cRAID INCOMING", "§eDefend " + targetName, 10, 120, 10);
				player.playSound(player, Sound.ITEM_GOAT_HORN_SOUND_2, SoundCategory.MASTER, 10f, 0.6f);
			}
		}
	}
}
