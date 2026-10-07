package net.tfminecraft.simplefactions.war.campaign.raid.fight;


import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import java.time.Instant;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;

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
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (!CampaignRaidBattleService.isCampaignRaidBattle(war, battle)
				|| !CampaignRaidBattleService.matchesRaidBattle(raid, battle.getId())) {
			return;
		}
		CampaignRaidBattleService.markAsCampaignRaidIfActive(war, battle);
		restoreFightStartedAt(battle, raid);
	}

	public static void resumeAll() {
		Instant now = CampaignClock.now();
		for (War war : WarManager.getActive()) {
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
		CampaignRaidWarbandService.createRaidWarbands(war, raid);
		if (now.isBefore(raid.getMusterEndsAt())) {
			CampaignRaidMusterScheduler.onMusterStarted(war, now);
		} else {
			CampaignRaidMusterScheduler.processOverdue(war, now);
		}
	}

	private static void resumeFight(War war, CampaignRaid raid, Instant now) {
		if (raid.getFightEndsAt() == null) {
			String savedId = raid.getBattleId() != null ? raid.getBattleId() : raid.getId();
			Battle saved = BattleManager.getByString(savedId);
			if (saved != null && CampaignRaidBattleService.isCampaignRaidBattle(war, saved)
					&& CampaignRaidBattleService.matchesRaidBattle(raid, saved.getId())) {
				saved.end();
				net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.deleteRaidBattle(saved);
			}
			CampaignRaidService.endRaid(war, now);
			WarManager.persist(war);
			return;
		}
		Battle battle = resolveBattle(war, raid, now);
		if (battle == null) {
			CampaignRaidService.endRaid(war, now);
			WarManager.persist(war);
			return;
		}
		if (battle.hasStarted()) {
			battle.setCampaignRaid(true);
			battle.setLootEnabled(false);
			restoreFightStartedAt(battle, raid);
			CampaignRaidWarbandService.createRaidWarbands(war, raid);
			Warband attacker = CampaignRaidWarbandService.getAttackerWarband(raid);
			Warband defender = CampaignRaidWarbandService.getDefenderWarband(raid);
			CampaignRaidBattleService.enrollRaidWarbands(raid, battle, attacker, defender);
			CampaignRaidBossBarService.onFightStarted(battle, raid);
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
			return CampaignRaidBattleService.isCampaignRaidBattle(war, battle)
					&& CampaignRaidBattleService.matchesRaidBattle(raid, battle.getId()) ? battle : null;
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
