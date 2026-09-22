package me.Plugins.SimpleFactions.War.battle.engine.win;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleEndSupport;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

public final class SiegeWinService {
	private SiegeWinService() {
	}

	public static void checkSiegeWin(Battle battle) {
		if (battle == null || !battle.hasStarted() || battle.getBattleType() != BattleType.SIEGE) {
			return;
		}

		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
		boolean attackerEliminated = attacker != null && FieldWinService.isSideEliminated(attacker);
		boolean defenderEliminated = defender != null && FieldWinService.isSideEliminated(defender);
		boolean holdComplete = battle.getContestHoldRemainingSeconds() <= 0;

		if (attackerEliminated && defenderEliminated) {
			BattleEndSupport.endBattle(battle, null);
			return;
		}
		if (holdComplete || defenderEliminated) {
			BattleEndSupport.endBattle(battle, BattleTemplate.ATTACKER_SIDE);
			return;
		}
		if (attackerEliminated) {
			BattleEndSupport.endBattle(battle, BattleTemplate.DEFENDER_SIDE);
		}
	}
}
