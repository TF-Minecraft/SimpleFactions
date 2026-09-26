package net.tfminecraft.simplefactions.war.battle.engine.win;

import java.time.Instant;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;

public final class BattleTimeCap {
	private BattleTimeCap() {
	}

	public static void check(Battle battle) {
		check(battle, Instant.now());
	}

	static void check(Battle battle, Instant now) {
		if (battle == null || !battle.hasStarted() || now == null || battle.getWarId() == null) {
			return;
		}
		BattleType type = battle.getBattleType();
		if (type != BattleType.FIELD && type != BattleType.SIEGE) {
			return;
		}
		if (!Cache.battleTimeCapEnabled || Cache.battleTimeCapMinutes <= 0 || battle.getStartedAt() == null) {
			return;
		}
		Instant deadline = battle.getStartedAt().plusSeconds(Cache.battleTimeCapMinutes * 60L);
		if (now.isBefore(deadline)) {
			return;
		}
		BattleEndSupport.endBattle(battle, winnerByLives(battle), BattleEndReason.TIMER);
	}

	private static String winnerByLives(Battle battle) {
		String winner = null;
		int best = Integer.MIN_VALUE;
		boolean tie = false;
		for (BattleSide side : battle.getSides()) {
			if (side == null) {
				continue;
			}
			int lives = side.getLives();
			if (winner == null || lives > best) {
				winner = side.getId();
				best = lives;
				tie = false;
			} else if (lives == best) {
				tie = true;
			}
		}
		return tie ? null : winner;
	}
}
