package me.Plugins.SimpleFactions.War.declare;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;

public final class WarDeclareRequest {
	private final Faction attacker;
	private final Faction defender;
	private final WarGoalType goal;
	private final String targetTitleId;
	private final String subjectFactionId;
	private final String relationTypeId;
	private final String governmentLawId;
	private final String leadershipLawId;
	private final String targetSettlementId;

	public WarDeclareRequest(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId) {
		this(attacker, defender, goal, targetTitleId, subjectFactionId, null);
	}

	public WarDeclareRequest(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId) {
		this(attacker, defender, goal, targetTitleId, subjectFactionId, relationTypeId, null, null);
	}

	public WarDeclareRequest(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId,
			String governmentLawId,
			String leadershipLawId) {
		this(attacker, defender, goal, targetTitleId, subjectFactionId, relationTypeId,
				governmentLawId, leadershipLawId, null);
	}

	public WarDeclareRequest(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId,
			String governmentLawId,
			String leadershipLawId,
			String targetSettlementId) {
		if (attacker == null) {
			throw new IllegalArgumentException("attacker is required");
		}
		if (defender == null) {
			throw new IllegalArgumentException("defender is required");
		}
		if (goal == null) {
			throw new IllegalArgumentException("goal is required");
		}
		this.attacker = attacker;
		this.defender = defender;
		this.goal = goal;
		this.targetTitleId = targetTitleId;
		this.subjectFactionId = subjectFactionId;
		this.relationTypeId = relationTypeId;
		this.governmentLawId = governmentLawId;
		this.leadershipLawId = leadershipLawId;
		this.targetSettlementId = targetSettlementId;
	}

	public static WarDeclareRequest of(Faction attacker, Faction defender, WarGoalType goal) {
		return new WarDeclareRequest(attacker, defender, goal, null, null, null);
	}

	public Faction getAttacker() {
		return attacker;
	}

	public Faction getDefender() {
		return defender;
	}

	public WarGoalType getGoal() {
		return goal;
	}

	public String getTargetTitleId() {
		return targetTitleId;
	}

	public String getSubjectFactionId() {
		return subjectFactionId;
	}

	public String getRelationTypeId() {
		return relationTypeId;
	}

	public String getGovernmentLawId() {
		return governmentLawId;
	}

	public String getLeadershipLawId() {
		return leadershipLawId;
	}

	public String getTargetSettlementId() {
		return targetSettlementId;
	}
}
