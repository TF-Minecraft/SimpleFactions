package me.Plugins.SimpleFactions.War.battle.engine.core;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BattleRespawnRouting {
	private static final Set<UUID> jailRespawns = ConcurrentHashMap.newKeySet();

	private BattleRespawnRouting() {
	}

	static void scheduleJailRespawn(UUID playerId, boolean jail) {
		if (playerId == null) {
			return;
		}
		if (jail) {
			jailRespawns.add(playerId);
		} else {
			jailRespawns.remove(playerId);
		}
	}

	static boolean consumeJailRespawn(UUID playerId) {
		if (playerId == null) {
			return false;
		}
		return jailRespawns.remove(playerId);
	}

	static void clear(UUID playerId) {
		if (playerId != null) {
			jailRespawns.remove(playerId);
		}
	}

	static void resetForTests() {
		jailRespawns.clear();
	}
}
