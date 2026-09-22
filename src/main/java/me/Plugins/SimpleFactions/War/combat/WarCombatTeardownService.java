package me.Plugins.SimpleFactions.War.combat;

import java.util.ArrayList;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidAttackerEliminationService;
import me.Plugins.SimpleFactions.War.battle.engine.win.SiegeContestService;
import me.Plugins.SimpleFactions.War.battle.military.BattleCasualtyLedger;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidFightScheduler;
import me.Plugins.SimpleFactions.War.campaign.raid.intruder.CampaignRaidIntruderService;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidMusterScheduler;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.core.War;

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
