package net.tfminecraft.simplefactions.war.core;

import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;

/**
 * One side's demand on a single enemy faction. The beneficiary is who gains if that side
 * wins: the side leader, or an ally fighting with them.
 */
public final class WarDemand {
	private final WarGoalType goal;
	private final String beneficiaryId;
	private final String targetId;
	private final String relationTypeId;
	private final String targetTitleId;
	private final String subjectFactionId;
	private final String governmentLawId;
	private final String leadershipLawId;

	public WarDemand(
			WarGoalType goal,
			String beneficiaryId,
			String targetId,
			String relationTypeId,
			String targetTitleId,
			String subjectFactionId,
			String governmentLawId,
			String leadershipLawId) {
		this.goal = goal;
		this.beneficiaryId = beneficiaryId;
		this.targetId = targetId;
		this.relationTypeId = goal == WarGoalType.SUBJUGATE ? relationTypeId : null;
		this.targetTitleId = goal == WarGoalType.DE_JURE_ANNEX ? targetTitleId : null;
		this.subjectFactionId = goal == WarGoalType.TRANSFER_SUBJECT ? subjectFactionId : null;
		this.governmentLawId = goal == WarGoalType.CHANGE_GOVERNMENT ? governmentLawId : null;
		this.leadershipLawId = goal == WarGoalType.CHANGE_GOVERNMENT ? leadershipLawId : null;
	}

	public static WarDemand fromRequest(WarDeclareRequest request) {
		return new WarDemand(
				request.getGoal(),
				request.getAttacker().getId(),
				request.getDefender().getId(),
				request.getRelationTypeId(),
				request.getTargetTitleId(),
				request.getSubjectFactionId(),
				request.getGovernmentLawId(),
				request.getLeadershipLawId());
	}

	public WarGoalType getGoal() {
		return goal;
	}

	public String getBeneficiaryId() {
		return beneficiaryId;
	}

	public String getTargetId() {
		return targetId;
	}

	public String getRelationTypeId() {
		return relationTypeId;
	}

	public String getTargetTitleId() {
		return targetTitleId;
	}

	public String getSubjectFactionId() {
		return subjectFactionId;
	}

	public String getGovernmentLawId() {
		return governmentLawId;
	}

	public String getLeadershipLawId() {
		return leadershipLawId;
	}

	public Faction getBeneficiary() {
		return FactionManager.getByString(beneficiaryId);
	}

	public Faction getTarget() {
		return FactionManager.getByString(targetId);
	}

	/** The demand as a declaration by the beneficiary on the target, for validation. */
	public WarDeclareRequest toRequest() {
		Faction beneficiary = getBeneficiary();
		Faction target = getTarget();
		if (beneficiary == null || target == null || goal == null) {
			return null;
		}
		return new WarDeclareRequest(
				beneficiary,
				target,
				goal,
				targetTitleId,
				subjectFactionId,
				relationTypeId,
				governmentLawId,
				leadershipLawId,
				null).asDemand();
	}

	/** Goal name plus the detail it carries, e.g. "Subjugate (Vassal)". */
	public String describeGoal() {
		if (goal == null) {
			return "";
		}
		String detail = null;
		if (goal == WarGoalType.SUBJUGATE && relationTypeId != null) {
			RelationType type = RelationLoader.getType(relationTypeId);
			detail = type != null ? type.getName() : relationTypeId;
		} else if (goal == WarGoalType.DE_JURE_ANNEX && targetTitleId != null) {
			Title title = TitleLoader.getById(targetTitleId);
			detail = title != null ? title.getName() : targetTitleId;
		} else if (goal == WarGoalType.TRANSFER_SUBJECT && subjectFactionId != null) {
			Faction subject = FactionManager.getByString(subjectFactionId);
			detail = subject != null ? subject.getName() : subjectFactionId;
		}
		return detail == null ? goal.getDisplayName() : goal.getDisplayName() + " §7(" + detail + "§7)";
	}

	public boolean isFor(String beneficiary, String target) {
		return beneficiary != null && target != null
				&& beneficiary.equalsIgnoreCase(beneficiaryId)
				&& target.equalsIgnoreCase(targetId);
	}
}
