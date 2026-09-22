package me.Plugins.SimpleFactions.War.campaign.raid.fight;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService;
import java.time.Instant;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;

public final class CampaignRaidResumeService {
	private CampaignRaidResumeService() {}

	public static void applyLoadedBattle(Battle battle) {
		if (battle == null || battle.getWarId() == null) {
			return;
		}
		War war = WarManager.getById(battle.getWarId());
		if (war == null || !war.isActive()) {
			return;
		}
		CampaignRaidBattleService.markAsCampaignRaidIfActive(war, battle);
		if (!battle.isCampaignRaid()) {
			return;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null) {
			return;
		}
		if (raid.getBattleId() == null || raid.getBattleId().isBlank()) {
			raid.setBattleId(battle.getId());
		}
		restoreFightStartedAt(battle, raid);
	}

	public static void resumeAll() {
		Instant now = CampaignClock.now();
		for (War war : WarManager.getActive()) {
			if (war == null || !war.isActive()) {
				continue;
			}
			resumeWar(war, now);
		}
	}

	private static void resumeWar(War war, Instant now) {
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null) {
			return;
		}
		if (raid.getState() == CampaignRaidState.MUSTER) {
			resumeMuster(war, raid, now);
			return;
		}
		if (raid.getState() == CampaignRaidState.FIGHTING) {
			resumeFight(war, raid, now);
		}
	}

	private static void resumeMuster(War war, CampaignRaid raid, Instant now) {
		if (raid.getMusterEndsAt() == null) {
			return;
		}
		CampaignRaidWarbandService.createAttackerWarband(war, raid);
		if (now.isBefore(raid.getMusterEndsAt())) {
			CampaignRaidMusterScheduler.onMusterStarted(war, now);
		} else {
			CampaignRaidMusterScheduler.processOverdue(war, now);
		}
	}

	private static void resumeFight(War war, CampaignRaid raid, Instant now) {
		Battle battle = resolveBattle(war, raid, now);
		if (battle != null && battle.hasStarted()) {
			battle.setCampaignRaid(true);
			battle.setLootEnabled(false);
			restoreFightStartedAt(battle, raid);
			CampaignRaidWarbandService.createRaidWarbands(war, raid);
			Warband attacker = CampaignRaidWarbandService.getAttackerWarband(raid);
			Warband defender = CampaignRaidWarbandService.getDefenderWarband(raid);
			CampaignRaidBattleService.enrollRaidWarbands(raid, battle, attacker, defender);
			CampaignRaidBossBarService.onFightStarted(battle, raid);
		}
		if (raid.getFightEndsAt() == null) {
			return;
		}
		if (now.isBefore(raid.getFightEndsAt())) {
			CampaignRaidFightScheduler.onFightStarted(war, now);
		} else {
			CampaignRaidFightScheduler.processOverdue(war, now);
		}
	}

	private static Battle resolveBattle(War war, CampaignRaid raid, Instant now) {
		String battleId = raid.getBattleId();
		if (battleId == null || battleId.isBlank()) {
			battleId = CampaignRaidBattleService.raidBattleId(raid);
		}
		if (battleId == null || battleId.isBlank()) {
			return null;
		}
		Battle battle = BattleManager.getByString(battleId);
		if (battle != null) {
			return battle;
		}
		return CampaignRaidBattleService.createAndStart(war, raid, now);
	}

	private static void restoreFightStartedAt(Battle battle, CampaignRaid raid) {
		if (battle == null || battle.getStartedAt() != null || raid.getFightEndsAt() == null) {
			return;
		}
		long durationSeconds = Math.max(1L, Cache.campaignRaidDurationSeconds);
		battle.setStartedAt(raid.getFightEndsAt().minusSeconds(durationSeconds));
	}
}
