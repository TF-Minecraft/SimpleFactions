package me.Plugins.SimpleFactions.War.core;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.declare.DeJureAnnexEligibility;
import me.Plugins.SimpleFactions.War.declare.DeJureAnnexEligibility.DeJureTitleOption;
import me.Plugins.SimpleFactions.War.declare.InterVassalQueries;
import me.Plugins.SimpleFactions.War.declare.WarGoalValidator;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

public final class WarDeclareHelper {
	private WarDeclareHelper() {}

	public static WarType warTypeForGoal(WarGoalType goal) {
		return switch (goal) {
			case DE_JURE_ANNEX -> WarType.DE_JURE;
			case SUBJUGATE -> WarType.SUBJUGATE;
			case TRANSFER_SUBJECT -> WarType.TRANSFER_SUBJECT;
			case WAR -> WarType.WAR;
			case TRIBUTARY -> WarType.TRIBUTARY;
			case USURP -> WarType.USURP;
			case OPEN_MARKET -> WarType.OPEN_MARKET;
			case CHANGE_GOVERNMENT -> WarType.CHANGE_GOVERNMENT;
			case PILLAGE -> WarType.PILLAGE;
			case OVERTHROW -> WarType.OVERTHROW;
			case CHANGE_LAW -> WarType.CHANGE_LAW;
			case CHANGE_TAX -> WarType.CHANGE_TAX;
			case FORCE_PEACE -> WarType.WAR;
		};
	}

	public static List<DeJureTitleOption> deJureTitleOptions(Faction attacker, Faction defender) {
		if (CivilWarBorderLock.isLocked(defender)) {
			return List.of();
		}
		return DeJureAnnexEligibility.options(attacker, defender);
	}

	public static List<Faction> defenderSubjects(Faction defender) {
		List<Faction> nested = new ArrayList<>();
		if (CivilWarBorderLock.isLocked(defender)) {
			return nested;
		}
		if (defender == null || defender.getId() == null) {
			return nested;
		}
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getId() == null) {
				continue;
			}
			if (faction.getId().equalsIgnoreCase(defender.getId())) {
				continue;
			}
			if (RelationManager.isOnOverlordPath(faction, defender)) {
				nested.add(faction);
			}
		}
		return nested;
	}

	public static boolean canDeclareUsurp(Faction attacker, Faction defender) {
		if (attacker == null || defender == null) {
			return false;
		}
		if (defender.getHighestTitle() == null) {
			return false;
		}
		if (!WarGoalValidator.canUsurpByRank(attacker.getTier().getTier(), defender.getTier().getTier())) {
			return false;
		}
		if (RelationManager.sameRealm(attacker, defender)
				&& !RelationManager.isOverlord(attacker, defender)) {
			return false;
		}
		if (InterVassalQueries.isInternalPeer(attacker, defender)) {
			return false;
		}
		return true;
	}

	static boolean canAnnexByRank(int attackerTierLevel, int titleTierLevel) {
		return attackerTierLevel >= titleTierLevel;
	}
}
