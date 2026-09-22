package me.Plugins.SimpleFactions.War.campaign.raid;


import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidMusterScheduler;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationLookup;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.LaunchResult;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.ValidateLaunchOutcome;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.ValidateLaunchResult;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.TransitionResult;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarDevMode;

public final class CampaignRaidService {
	private CampaignRaidService() {}

	public static void syncBattleDay(War war) {
		if (war == null) {
			return;
		}
		LocalDate battleDay = war.getBattleDay();
		CampaignRaid active = war.getActiveCampaignRaid();
		if (active != null && battleDay != null && active.getBattleDay() != null
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
		if (coalition == null) {
			return LaunchResult.REJECTED_NOT_PARTICIPANT;
		}

		LocalDate battleDay = war.getBattleDay();
		Installation target = InstallationLookup.findById(targetInstallationId);
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
		CampaignRaid raid = war.getActiveCampaignRaid();
		if (raid != null) {
			CampaignRaidWarbandService.destroyRaidWarbands(war, raid);
			raid.setState(CampaignRaidState.ENDED);
		}
		clearActiveRaid(war);
	}

	public static void clearForNewBattleDay(War war) {
		if (war == null) {
			return;
		}
		CampaignRaid raid = war.getActiveCampaignRaid();
		if (raid != null) {
			CampaignRaidWarbandService.destroyRaidWarbands(war, raid);
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
		return until != null && now.isBefore(until);
	}

	public static Instant repairLockUntilFromStart(Instant fightStart) {
		if (fightStart == null) {
			return null;
		}
		return fightStart.plus(Cache.campaignRaidRepairLockHours, ChronoUnit.HOURS);
	}

	private static void clearActiveRaid(War war) {
		war.setActiveCampaignRaid(null);
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
		if (raidId == null || raidId.isBlank()) {
			return false;
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
		Side side = war.getSide(faction);
		if (side == null) {
			return null;
		}
		if (side == war.getAttackers()) {
			return CampaignCoalition.AGGRESSOR;
		}
		if (side == war.getDefenders()) {
			return CampaignCoalition.DEFENDER;
		}
		return null;
	}

	private static LaunchResult mapValidateLaunchResult(ValidateLaunchResult result) {
		if (result == null) {
			return LaunchResult.REJECTED_WAR_INACTIVE;
		}
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
