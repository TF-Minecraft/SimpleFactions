package net.tfminecraft.simplefactions.war.battle.engine.win;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;

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
