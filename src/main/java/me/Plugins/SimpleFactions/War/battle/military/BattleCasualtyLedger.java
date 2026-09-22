package me.Plugins.SimpleFactions.War.battle.military;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

public final class BattleCasualtyLedger {
	private static final Map<String, Map<String, Integer>> CASUALTIES_BY_BATTLE = new HashMap<>();

	private BattleCasualtyLedger() {}

	public static boolean tracksCasualties(Battle battle) {
		if (battle == null || !battle.hasStarted() || battle.getWarId() == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}

	public static void recordSideCasualty(Battle battle, BattleSide side) {
		if (!tracksCasualties(battle) || side == null || side.getId() == null) {
			return;
		}
		String sideKey = side.getId().toLowerCase(Locale.ROOT);
		Map<String, Integer> sideCasualties = CASUALTIES_BY_BATTLE.computeIfAbsent(
				battle.getId(),
				ignored -> new HashMap<>());
		sideCasualties.put(sideKey, sideCasualties.getOrDefault(sideKey, 0) + 1);
	}

	public static Map<String, Integer> getSideCasualties(Battle battle) {
		if (battle == null) {
			return Map.of();
		}
		Map<String, Integer> sideCasualties = CASUALTIES_BY_BATTLE.get(battle.getId());
		if (sideCasualties == null || sideCasualties.isEmpty()) {
			return Map.of();
		}
		return Collections.unmodifiableMap(new HashMap<>(sideCasualties));
	}

	public static void clear(Battle battle) {
		if (battle != null) {
			CASUALTIES_BY_BATTLE.remove(battle.getId());
		}
	}

	public static void resetForTests() {
		CASUALTIES_BY_BATTLE.clear();
	}
}
