package me.Plugins.SimpleFactions.War.battle.engine.raid;

import java.util.List;

import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleEndSupport;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.engine.win.FieldWinService;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

public final class RaidWinService {
	private RaidWinService() {
	}

	public static void checkRaidWin(Battle battle) {
		if (battle == null || !battle.hasStarted() || battle.getBattleType() != BattleType.RAID) {
			return;
		}
		if (battle.isCampaignRaid()) {
			if (RaidAttackerEliminationService.isAttackerSideEliminated(battle)) {
				BattleEndSupport.endBattle(battle, BattleTemplate.DEFENDER_SIDE);
			}
			return;
		}

		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
		boolean targetCaptured = isTargetCaptured(battle);
		boolean attackersOut = RaidAttackerEliminationService.isAttackerSideEliminated(battle);
		boolean defenderEliminated = defender != null
				&& BattleRaidSetup.getEffectiveDefenderRespawnMode(battle) == DefenderRespawnMode.LIVES
				&& FieldWinService.isSideEliminated(defender);

		if (targetCaptured && (attackersOut || defenderEliminated)) {
			BattleEndSupport.endBattle(battle, null);
			return;
		}
		if (targetCaptured || defenderEliminated) {
			BattleEndSupport.endBattle(battle, BattleTemplate.ATTACKER_SIDE);
			return;
		}
		if (attackersOut) {
			BattleEndSupport.endBattle(battle, BattleTemplate.DEFENDER_SIDE);
		}
	}

	public static boolean isTargetCaptured(Battle battle) {
		List<CapturePoint> points = battle.getPointManager().getPoints();
		if (points.isEmpty()) {
			return false;
		}
		return points.get(0).isFullyControlledBy(BattleTemplate.ATTACKER_SIDE);
	}
}
