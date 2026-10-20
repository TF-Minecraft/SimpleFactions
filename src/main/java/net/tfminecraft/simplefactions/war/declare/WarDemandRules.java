package net.tfminecraft.simplefactions.war.declare;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDemand;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;

/**
 * Who may make a side's second demand, for whom, and of whom.
 * <p>
 * The first demand is the side leader's: the declared goal for the attackers, the counter
 * goal for the defenders. Once it is set, the same leader may add a second demand, either
 * for themselves or for an ally fighting with them. Attackers may only make demands of the
 * defending nation; defenders may also make them of the attacker's allies, who joined the
 * war by choice.
 */
public final class WarDemandRules {
	/** Goals that change who answers to whom. Only one of them can fall on one faction. */
	static final Set<WarGoalType> RELATIONAL = EnumSet.of(
			WarGoalType.TRIBUTARY, WarGoalType.SUBJUGATE, WarGoalType.USURP);

	private static final Set<WarGoalType> DEMANDABLE = EnumSet.of(
			WarGoalType.TRIBUTARY,
			WarGoalType.SUBJUGATE,
			WarGoalType.TRANSFER_SUBJECT,
			WarGoalType.USURP,
			WarGoalType.OPEN_MARKET,
			WarGoalType.CHANGE_GOVERNMENT,
			WarGoalType.DE_JURE_ANNEX);

	public static final String LOCKED = "§cWar demands are locked once the first battle begins.";
	public static final String NOT_LEADER = "§cOnly the side's leading nation can set its second demand.";
	public static final String NO_FIRST_DEMAND = "§cSet a counter goal before adding a second demand.";
	public static final String NOT_AVAILABLE = "§cThis war cannot carry a second demand.";

	private WarDemandRules() {}

	public static boolean isDemandable(WarGoalType goal) {
		return goal != null && DEMANDABLE.contains(goal);
	}

	public static boolean isRelational(WarGoalType goal) {
		return goal != null && RELATIONAL.contains(goal);
	}

	/** The side leader, then each faction that joined the war with them. */
	public static List<Faction> beneficiaries(War war, boolean attackerSide) {
		Side side = attackerSide ? war.getAttackers() : war.getDefenders();
		List<Faction> result = new ArrayList<>();
		for (Participant participant : side.getMainParticipants()) {
			add(result, participant.getLeader());
		}
		for (Participant participant : side.getMainParticipants()) {
			for (Faction joined : participant.getJoinedSecondaries()) {
				add(result, joined);
			}
		}
		return result;
	}

	/**
	 * The enemy factions a side may make demands of. The defenders' allies are never
	 * targets: they came to defend, and must not be punished for it.
	 */
	public static List<Faction> targets(War war, boolean attackerSide) {
		Side enemy = attackerSide ? war.getDefenders() : war.getAttackers();
		List<Faction> result = new ArrayList<>();
		for (Participant participant : enemy.getMainParticipants()) {
			add(result, participant.getLeader());
		}
		if (!attackerSide) {
			for (Participant participant : enemy.getMainParticipants()) {
				for (Faction joined : participant.getJoinedSecondaries()) {
					add(result, joined);
				}
			}
		}
		return result;
	}

	/** The side's first demand as a demand, or null while the defenders have no counter goal. */
	public static WarDemand firstDemand(War war, boolean attackerSide) {
		if (attackerSide) {
			if (war.getGoal() == null) {
				return null;
			}
			return new WarDemand(
					war.getGoal(),
					war.getAttackerLeaderId(),
					war.getDefenderLeaderId(),
					war.getRelationTypeId(),
					war.getTargetTitleId(),
					war.getSubjectFactionId(),
					war.getGovernmentLawId(),
					war.getLeadershipLawId());
		}
		if (war.getDefenderCounterGoal() == null) {
			return null;
		}
		return new WarDemand(
				war.getDefenderCounterGoal(),
				war.getDefenderLeaderId(),
				war.getAttackerLeaderId(),
				war.getDefenderCounterRelationTypeId(),
				null,
				null,
				null,
				null);
	}

	/**
	 * Two demands clash when they fall on the same faction and either repeat the same goal
	 * (annexing two different titles, or taking two different subjects, is fine) or both
	 * change who that faction answers to.
	 */
	public static boolean conflicts(WarDemand first, WarDemand second) {
		if (first == null || second == null || first.getTargetId() == null
				|| !first.getTargetId().equalsIgnoreCase(second.getTargetId())) {
			return false;
		}
		if (isRelational(first.getGoal()) && isRelational(second.getGoal())) {
			return true;
		}
		if (first.getGoal() != second.getGoal()) {
			return false;
		}
		return switch (first.getGoal()) {
			case DE_JURE_ANNEX -> sameId(first.getTargetTitleId(), second.getTargetTitleId());
			case TRANSFER_SUBJECT -> sameId(first.getSubjectFactionId(), second.getSubjectFactionId());
			default -> true;
		};
	}

	/** Null when the editor may change this side's second demand now, else the reason. */
	public static String editBlock(War war, Faction editor, boolean attackerSide) {
		if (war == null || !war.isActive() || war.getWarType() == WarType.RAID
				|| CivilWarBorderLock.isCivilWar(war)) {
			return NOT_AVAILABLE;
		}
		String leaderId = attackerSide ? war.getAttackerLeaderId() : war.getDefenderLeaderId();
		if (editor == null || leaderId == null || !leaderId.equalsIgnoreCase(editor.getId())) {
			return NOT_LEADER;
		}
		if (war.hasFirstBattleStarted()) {
			return LOCKED;
		}
		if (firstDemand(war, attackerSide) == null) {
			return NO_FIRST_DEMAND;
		}
		return null;
	}

	/** Checks a second demand against the war; then the goal's own rules, as for a declare. */
	public static WarValidationResult validate(War war, boolean attackerSide, WarDeclareRequest request) {
		if (war == null || request == null) {
			return WarValidationResult.fail(NOT_AVAILABLE);
		}
		if (!isDemandable(request.getGoal())) {
			return WarValidationResult.fail("§cThat goal cannot be a second demand.");
		}
		if (!contains(beneficiaries(war, attackerSide), request.getAttacker())) {
			return WarValidationResult.fail("§c" + request.getAttacker().getName()
					+ " §cis not fighting on your side.");
		}
		if (!contains(targets(war, attackerSide), request.getDefender())) {
			return WarValidationResult.fail(attackerSide
					? "§cDemands can only be made of the defending nation."
					: "§cDemands can only be made of the attackers.");
		}
		if (conflicts(firstDemand(war, attackerSide), WarDemand.fromRequest(request))) {
			return WarValidationResult.fail("§cThat clashes with your first demand on "
					+ request.getDefender().getName() + "§c.");
		}
		return new WarGoalValidator().validate(request.isDemand() ? request : request.asDemand());
	}

	private static boolean sameId(String a, String b) {
		return a == null ? b == null : a.equalsIgnoreCase(b);
	}

	private static boolean contains(List<Faction> factions, Faction faction) {
		if (faction == null || faction.getId() == null) {
			return false;
		}
		for (Faction candidate : factions) {
			if (faction.getId().equalsIgnoreCase(candidate.getId())) {
				return true;
			}
		}
		return false;
	}

	private static void add(List<Faction> factions, Faction faction) {
		if (faction == null || faction.getId() == null || contains(factions, faction)) {
			return;
		}
		factions.add(faction);
	}
}
