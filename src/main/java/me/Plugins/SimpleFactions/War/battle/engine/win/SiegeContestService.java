package me.Plugins.SimpleFactions.War.battle.engine.win;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleContestSetup;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.template.ContestArea;
import net.tfminecraft.VehicleFramework.VehicleFramework;

public final class SiegeContestService {
	static final int PRESENCE_THRESHOLD = 3;
	static final int TICKS_PER_SECOND = 5;

	enum ControlState {
		ATTACKER,
		DEFENDER,
		CONTESTED
	}

	private static final Map<String, Integer> secondTickCounters = new HashMap<>();

	private SiegeContestService() {
	}

	public static void resetForTests() {
		secondTickCounters.clear();
	}

	public static void tick(Battle battle) {
		if (battle == null || !battle.hasStarted() || battle.getBattleType() != BattleType.SIEGE) {
			return;
		}
		ContestArea area = battle.getContestArea();
		if (area == null || !area.isConfigured()) {
			return;
		}

		int[] presence = countPresence(battle, area);
		ControlState state = resolveControlState(presence[0], presence[1]);
		updateFeedback(battle, state);

		int ticks = secondTickCounters.getOrDefault(battle.getId(), 0) + 1;
		if (ticks < TICKS_PER_SECOND) {
			secondTickCounters.put(battle.getId(), ticks);
			return;
		}
		secondTickCounters.put(battle.getId(), 0);
		tickHoldSeconds(battle, state);
	}

	static int[] countPresence(Battle battle, ContestArea area) {
		int attackers = 0;
		int defenders = 0;
		for (Player player : battle.getAllParticipants()) {
			if (player == null || !player.isOnline()) {
				continue;
			}
			if (VehicleFramework.getVehicleManager().get(player) != null) {
				continue;
			}
			if (!area.contains(player.getLocation())) {
				continue;
			}
			BattleSide side = battle.getSideByPlayer(player);
			if (side == null) {
				continue;
			}
			if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(side.getId())) {
				attackers++;
			} else if (BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(side.getId())) {
				defenders++;
			}
		}
		return new int[] { attackers, defenders };
	}

	static ControlState resolveControlState(int attackers, int defenders) {
		if (attackers >= PRESENCE_THRESHOLD && attackers > defenders) {
			return ControlState.ATTACKER;
		}
		if (defenders >= PRESENCE_THRESHOLD && defenders >= attackers) {
			return ControlState.DEFENDER;
		}
		return ControlState.CONTESTED;
	}

	static void tickHoldSeconds(Battle battle, ControlState state) {
		int maxDuration = BattleContestSetup.getEffectiveDurationSeconds(battle);
		int remaining = battle.getContestHoldRemainingSeconds();
		if (state == ControlState.ATTACKER) {
			remaining = Math.max(0, remaining - 1);
		} else if (state == ControlState.DEFENDER) {
			remaining = Math.min(maxDuration, remaining + 1);
		}
		battle.setContestHoldRemainingSeconds(remaining);
	}

	private static void updateFeedback(Battle battle, ControlState state) {
		int remaining = battle.getContestHoldRemainingSeconds();
		String stateLabel = switch (state) {
			case ATTACKER -> "§aATTACKERS HOLDING";
			case DEFENDER -> "§cDEFENDERS HOLDING";
			case CONTESTED -> "§eCONTESTED";
		};
		String actionBar = "§6Siege: §f" + remaining + "s §7- " + stateLabel;
		for (Player player : battle.getAllParticipants()) {
			if (isInContestArea(battle, player)) {
				player.sendTitle(" ", actionBar, 0, 10, 0);
			}
		}
	}

	private static boolean isInContestArea(Battle battle, Player player) {
		ContestArea area = battle.getContestArea();
		return area != null && area.contains(player.getLocation());
	}

	public static void clearBattleState(Battle battle) {
		if (battle != null) {
			secondTickCounters.remove(battle.getId());
		}
	}
}
