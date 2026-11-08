package net.tfminecraft.simplefactions.war.battle.campaign.warband;

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

	/** Lets everyone who left a battle back in, e.g. after staff restart or stop it. */
	public static void clearBattle(String battleId) {
		if (battleId != null) {
			blockedKeys.removeIf(key -> key.startsWith(battleId + ":"));
		}
	}

	public static boolean isBlocked(String battleId, String warbandId, UUID playerId) {
		if (battleId == null || warbandId == null || playerId == null) {
			return false;
		}
		return blockedKeys.contains(blockKey(battleId, warbandId, playerId));
	}
}
