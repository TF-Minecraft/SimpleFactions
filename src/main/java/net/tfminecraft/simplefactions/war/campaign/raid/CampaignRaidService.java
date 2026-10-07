package net.tfminecraft.simplefactions.war.campaign.raid;


import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidMusterScheduler;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidFightScheduler;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationLookup;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.LaunchResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.ValidateLaunchOutcome;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.ValidateLaunchResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.TransitionResult;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDevMode;

public final class CampaignRaidService {
	private CampaignRaidService() {}

	public static void syncBattleDay(War war) {
		if (war == null) {
			return;
		}
		LocalDate battleDay = war.getBattleDay();
		CampaignRaid active = war.getActiveCampaignRaid();
		if (active != null && active.getBattleDay() != null
				&& !active.getBattleDay().equals(battleDay)) {
			clearActiveRaid(war);
		}
		if (battleDay == null) {
			war.getCampaignRaidsUsed().clear();
			return;
		}
		String battleDayKey = battleDay.toString();
		war.getCampaignRaidsUsed().entrySet().removeIf(entry ->
				entry.getValue() == null || !battleDayKey.equals(entry.getValue()));
	}

	public static LaunchResult canLaunch(War war, Faction faction, Instant now) {
		if (war == null || !war.isActive() || faction == null || now == null) {
			return LaunchResult.REJECTED_WAR_INACTIVE;
		}
		syncBattleDay(war);
		if (!WarDevMode.isEnabled() && war.getBattleDay() == null) {
			return LaunchResult.REJECTED_WAR_INACTIVE;
		}
		if (war.getSide(faction) == null) {
			return LaunchResult.REJECTED_NOT_PARTICIPANT;
		}
		if (!BattleScheduleService.isRaidWindowOpen(war, now)) {
			return LaunchResult.REJECTED_OUTSIDE_WINDOW;
		}
		if (war.getActiveCampaignRaid() != null) {
			return LaunchResult.REJECTED_RAID_IN_PROGRESS;
		}
		if (!WarDevMode.isEnabled()) {
			CampaignCoalition coalition = coalitionForFaction(war, faction);
			if (coalition == null || isSideQuotaUsed(war, coalition)) {
				return LaunchResult.REJECTED_QUOTA_SPENT;
			}
		}
		return LaunchResult.STARTED;
	}

	public static LaunchResult beginMuster(
			War war,
			Faction faction,
			String sourceInstallationId,
			String targetInstallationId,
			Instant now) {
		if (faction == null || faction.getId() == null) {
			return LaunchResult.REJECTED_NOT_PARTICIPANT;
		}
		ValidateLaunchOutcome outcome = CampaignRaidEligibilityService.validateLaunch(
				war, faction.getId(), sourceInstallationId, targetInstallationId, now);
		LaunchResult gate = mapValidateLaunchResult(outcome.result());
		if (gate != LaunchResult.STARTED) {
			return gate;
		}

		CampaignCoalition coalition = coalitionForFaction(war, faction);

		LocalDate battleDay = war.getBattleDay();
		Installation target = CampaignRaidEligibilityService.resolveTargetInstallation(war, faction.getId(), targetInstallationId);
		String displayName = BattleNamingService.buildRaidDisplayName(war, target);
		String raidId = resolveUniqueRaidId(displayName, war.getId());

		CampaignRaid raid = new CampaignRaid();
		raid.setDisplayName(displayName);
		raid.setId(raidId);
		raid.setWarId(war.getId());
		raid.setBattleDay(battleDay);
		raid.setAttackerCoalition(coalition);
		raid.setLauncherFactionId(faction.getId());
		raid.setSourceInstallationId(sourceInstallationId);
		raid.setTargetInstallationId(targetInstallationId);
		raid.setRaidKind(outcome.raidKind());
		raid.setState(CampaignRaidState.MUSTER);
		raid.setMusterEndsAt(now.plusSeconds(Cache.campaignRaidMusterSeconds));
		raid.clearMusterRemindersSent();
		war.setActiveCampaignRaid(raid);
		CampaignRaidWarbandService.createAttackerWarband(war, raid);
		CampaignRaidMusterScheduler.onMusterStarted(war, now);
		return LaunchResult.STARTED;
	}

	public static CampaignRaid getActive(War war) {
		if (war == null) {
			return null;
		}
		syncBattleDay(war);
		return war.getActiveCampaignRaid();
	}

	public static TransitionResult transitionToFighting(War war, Instant now) {
		if (war == null || now == null) {
			return TransitionResult.REJECTED_NO_ACTIVE_RAID;
		}
		syncBattleDay(war);
		CampaignRaid raid = war.getActiveCampaignRaid();
		if (raid == null) {
			return TransitionResult.REJECTED_NO_ACTIVE_RAID;
		}
		if (raid.getState() != CampaignRaidState.MUSTER) {
			return TransitionResult.REJECTED_WRONG_STATE;
		}
		raid.setState(CampaignRaidState.FIGHTING);
		raid.setFightEndsAt(now.plusSeconds(Cache.campaignRaidDurationSeconds));
		if (raid.getAttackerCoalition() != null && raid.getBattleDay() != null) {
			war.getCampaignRaidsUsed().put(
					raid.getAttackerCoalition().toJson(),
					raid.getBattleDay().toString());
		}
		return TransitionResult.OK;
	}

	public static void endRaid(War war, Instant now) {
		if (war == null) {
			return;
		}
		syncBattleDay(war);
		clearActiveRaid(war);
	}

	public static void clearForNewBattleDay(War war) {
		if (war == null) {
			return;
		}
		clearActiveRaid(war);
		war.getCampaignRaidsUsed().clear();
	}

	public static boolean isSideQuotaUsed(War war, CampaignCoalition coalition) {
		if (war == null || coalition == null || war.getBattleDay() == null) {
			return false;
		}
		syncBattleDay(war);
		String usedDay = war.getCampaignRaidsUsed().get(coalition.toJson());
		return usedDay != null && usedDay.equals(war.getBattleDay().toString());
	}

	/**
	 * Clears raid quota for the war's current battle day.
	 *
	 * @param coalition {@code null} clears both sides; otherwise one coalition
	 * @return number of coalition quota entries removed
	 */
	public static int resetRaidQuota(War war, CampaignCoalition coalition) {
		if (war == null) {
			return 0;
		}
		syncBattleDay(war);
		int cleared = 0;
		if (coalition == null) {
			for (CampaignCoalition side : CampaignCoalition.values()) {
				if (war.getCampaignRaidsUsed().remove(side.toJson()) != null) {
					cleared++;
				}
			}
		} else if (war.getCampaignRaidsUsed().remove(coalition.toJson()) != null) {
			cleared = 1;
		}
		WarManager.persist(war);
		return cleared;
	}

	public static void setRepairLockUntil(War war, String installationId, Instant until) {
		if (war == null || installationId == null || installationId.isBlank() || until == null) {
			return;
		}
		war.getRaidRepairLockUntil().put(installationId, until);
	}

	public static boolean isRepairLocked(War war, String installationId, Instant now) {
		if (war == null || installationId == null || installationId.isBlank() || now == null) {
			return false;
		}
		Instant until = war.getRaidRepairLockUntil().get(installationId);
		if (until != null && now.isBefore(until)) return true;
		for (Installation installation : InstallationLookup.all()) {
			if (installationId.equals(installation.getId()) && isInstallationRepairLocked(war, installation, now)) return true;
		}
		return false;
	}

	public static void setInstallationRepairLockUntil(War war, Installation installation, Instant until) {
		if (installation != null) setRepairLockUntil(war, installation.getStableKey(), until);
	}

	public static boolean isInstallationRepairLocked(War war, Installation installation, Instant now) {
		if (war == null || installation == null || now == null) return false;
		Instant until = war.getRaidRepairLockUntil().get(installation.getStableKey());
		if (until != null && now.isBefore(until)) return true;
		// Old saves only stored the local id. Limit that fallback to this war's holders.
		var owner = net.tfminecraft.simplefactions.installation.InstallationOwners.ownerOf(installation);
		if (owner == null || war.getSide(owner) == null) return false;
		Instant legacy = war.getRaidRepairLockUntil().get(installation.getId());
		return legacy != null && now.isBefore(legacy);
	}

	public static Instant repairLockUntilFromStart(Instant fightStart) {
		if (fightStart == null) {
			return null;
		}
		return fightStart.plus(Cache.campaignRaidRepairLockHours, ChronoUnit.HOURS);
	}

	private static void clearActiveRaid(War war) {
		CampaignRaid raid = war.getActiveCampaignRaid();
		war.setActiveCampaignRaid(null);
		CampaignRaidMusterScheduler.cancelForWar(war.getId());
		CampaignRaidFightScheduler.cancelForWar(war.getId());
		if (raid == null) return;
		String battleId = raid.getBattleId() != null ? raid.getBattleId() : raid.getId();
		Battle battle = BattleManager.getByString(battleId);
		if (battle != null && battle.isCampaignRaid() && battle.getBattleType() == BattleType.RAID
				&& Objects.equals(battle.getWarId(), war.getId())) {
			battle.end();
			BattlePersistenceService.deleteRaidBattle(battle);
		}
		CampaignRaidWarbandService.destroyRaidWarbands(war, raid);
		raid.setState(CampaignRaidState.ENDED);
	}

	static String resolveUniqueRaidId(String displayName, int warId) {
		String slug = BattleNamingService.slugifyDisplayName(displayName);
		if (!isRaidIdInUse(slug)) {
			return slug;
		}
		String warScoped = slug + "_w" + warId;
		if (!isRaidIdInUse(warScoped)) {
			return warScoped;
		}
		return slug + "_w" + warId + "_" + System.currentTimeMillis();
	}

	private static boolean isRaidIdInUse(String raidId) {
		if (BattleManager.getByString(raidId) != null
				|| WarbandManager.getByString(raidId + "_attacker") != null
				|| WarbandManager.getByString(raidId + "_defender") != null) {
			return true;
		}
		for (War activeWar : WarManager.getActive()) {
			CampaignRaid raid = activeWar.getActiveCampaignRaid();
			if (raid != null && raidId.equalsIgnoreCase(raid.getId())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isMusterHiddenFromFaction(War war, Faction faction) {
		CampaignRaid raid = getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER) {
			return false;
		}
		CampaignCoalition coalition = coalitionForFaction(war, faction);
		return coalition == null || coalition != raid.getAttackerCoalition();
	}

	public static CampaignCoalition coalitionForFaction(War war, Faction faction) {
		if (war == null || faction == null) {
			return null;
		}
		Side side = war.getSide(faction);
		if (side == null) {
			return null;
		}
		if (side == war.getAttackers()) {
			return CampaignCoalition.AGGRESSOR;
		}
		return CampaignCoalition.DEFENDER;
	}

	private static LaunchResult mapValidateLaunchResult(ValidateLaunchResult result) {
		return switch (result) {
			case OK -> LaunchResult.STARTED;
			case REJECTED_WAR_INACTIVE -> LaunchResult.REJECTED_WAR_INACTIVE;
			case REJECTED_NOT_PARTICIPANT -> LaunchResult.REJECTED_NOT_PARTICIPANT;
			case REJECTED_OUTSIDE_WINDOW -> LaunchResult.REJECTED_OUTSIDE_WINDOW;
			case REJECTED_QUOTA_SPENT -> LaunchResult.REJECTED_QUOTA_SPENT;
			case REJECTED_RAID_IN_PROGRESS -> LaunchResult.REJECTED_RAID_IN_PROGRESS;
			case REJECTED_INVALID_SOURCE, REJECTED_INVALID_TARGET, REJECTED_KIND_MISMATCH ->
					LaunchResult.REJECTED_INVALID_INPUT;
		};
	}
}
