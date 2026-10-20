package net.tfminecraft.simplefactions.war.declare;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;

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
	private final boolean demand;
	private final boolean withinRealm;

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
		this(attacker, defender, goal, targetTitleId, subjectFactionId, relationTypeId,
				governmentLawId, leadershipLawId, targetSettlementId, false, false);
	}

	private WarDeclareRequest(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId,
			String governmentLawId,
			String leadershipLawId,
			String targetSettlementId,
			boolean demand,
			boolean withinRealm) {
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
		this.demand = demand;
		this.withinRealm = withinRealm;
	}

	/**
	 * The same request as a second demand inside a war that is already running, where the
	 * attacker is the faction that benefits and the defender is the faction it is made of.
	 */
	public WarDeclareRequest asDemand() {
		return new WarDeclareRequest(attacker, defender, goal, targetTitleId, subjectFactionId,
				relationTypeId, governmentLawId, leadershipLawId, targetSettlementId, true, withinRealm);
	}

	/**
	 * The ruler's war goal in a civil war, made of a vassal that backed the rebels. Its
	 * target is in the ruler's own realm, so the same-realm rule does not apply.
	 */
	public WarDeclareRequest asCivilWarDemand() {
		return new WarDeclareRequest(attacker, defender, goal, targetTitleId, subjectFactionId,
				relationTypeId, governmentLawId, leadershipLawId, targetSettlementId, true, true);
	}

	public boolean isDemand() {
		return demand;
	}

	public boolean isWithinRealm() {
		return withinRealm;
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
