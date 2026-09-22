package me.Plugins.SimpleFactions.War.battle.campaign.warband;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CampaignWarbandLeaveBlock {
	private static final Set<String> blockedKeys = new HashSet<>();

	private CampaignWarbandLeaveBlock() {
	}

	public static void resetForTests() {
		blockedKeys.clear();
	}

	public static String blockKey(String battleId, String warbandId, UUID playerId) {
		return battleId + ":" + warbandId + ":" + playerId;
	}

	public static void block(String battleId, String warbandId, UUID playerId) {
		if (battleId == null || warbandId == null || playerId == null) {
			return;
		}
		blockedKeys.add(blockKey(battleId, warbandId, playerId));
	}

	public static boolean isBlocked(String battleId, String warbandId, UUID playerId) {
		if (battleId == null || warbandId == null || playerId == null) {
			return false;
		}
		return blockedKeys.contains(blockKey(battleId, warbandId, playerId));
	}
}
