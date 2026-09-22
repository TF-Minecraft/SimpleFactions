package net.tfminecraft.simplefactions.war.combat;

import java.util.ArrayList;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.engine.win.SiegeContestService;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidFightScheduler;
import net.tfminecraft.simplefactions.war.campaign.raid.intruder.CampaignRaidIntruderService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidMusterScheduler;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.core.War;

public final class WarCombatTeardownService {
	private WarCombatTeardownService() {}

	/**
	 * Ends and removes all live combat for a war without firing {@code BattleEndedEvent},
	 * so campaign progression and raid outcomes are not applied when the war is ending.
	 */
	public static void teardownCombatForWar(War war) {
		if (war == null) {
			return;
		}
		int warId = war.getId();

		CampaignRaidMusterScheduler.cancelForWar(warId);
		CampaignRaidFightScheduler.cancelForWar(warId);

		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid != null) {
			CampaignRaidIntruderService.clearForRaid(raid);
		}

		for (Battle battle : new ArrayList<>(BattleManager.getAllByWarId(warId))) {
			purgeBattle(battle);
		}
	}

	private static void purgeBattle(Battle battle) {
		if (battle == null) {
			return;
		}
		BattleManager.clearEditorSessions(battle);
		if (battle.hasStarted()) {
			SiegeContestService.clearBattleState(battle);
			RaidAttackerEliminationService.clearBattleState(battle);
			BattleCasualtyLedger.clear(battle);
			battle.end();
		}
		if (battle.isCampaignRaid()) {
			BattlePersistenceService.deleteRaidBattle(battle);
		} else {
			BattlePersistenceService.deleteCampaignBattle(battle);
		}
	}
}
