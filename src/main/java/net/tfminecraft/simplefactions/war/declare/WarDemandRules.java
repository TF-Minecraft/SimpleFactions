package net.tfminecraft.simplefactions.war.declare;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.managers.RelationManager;
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
 * war by choice. In a usurp war on one's own overlord neither side is the victim, so both
 * sides may make demands of the other's allies.
 * <p>
 * A civil war carries one goal per side. The rebels' is the movement's demands; the ruler's
 * is a single war goal on a vassal that backed the rebellion, kept in the defenders' second
 * demand slot.
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
	public static final String REBEL_DEMANDS = "§cIn a civil war the rebels' demands are the movement's causes.";
	public static final String NO_REBEL_VASSALS = "§cNo vassal backed the rebellion, so there is no one to make demands of.";

	private WarDemandRules() {}

	public static boolean isDemandable(WarGoalType goal) {
		return goal != null && DEMANDABLE.contains(goal);
	}

	public static boolean isRelational(WarGoalType goal) {
		return goal != null && RELATIONAL.contains(goal);
	}

	/** The side leader, then its subjects, then each faction that joined the war with them. */
	public static List<Faction> beneficiaries(War war, boolean attackerSide) {
		Side side = attackerSide ? war.getAttackers() : war.getDefenders();
		List<Faction> result = new ArrayList<>();
		for (Participant participant : side.getMainParticipants()) {
			add(result, participant.getLeader());
		}
		for (Participant participant : side.getMainParticipants()) {
			for (Faction subject : participant.getSubjects()) {
				add(result, subject);
			}
			for (Faction joined : participant.getJoinedSecondaries()) {
				add(result, joined);
			}
		}
		return result;
	}

	/** A civil war, where the ruler's single goal falls on the vassals that backed the rebels. */
	public static boolean isCivilWar(War war) {
		return CivilWarBorderLock.isCivilWar(war);
	}

	/** A usurp war a vassal declared on its own overlord: inside one realm, with no clear victim. */
	public static boolean isUsurpOnOverlord(War war) {
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		return war.getGoal() == WarGoalType.USURP && attacker != null && defender != null
				&& RelationManager.isOverlord(attacker, defender);
	}

	/**
	 * The enemy factions a side may make demands of. The defenders' allies are never
	 * targets: they came to defend, and must not be punished for it.
	 */
	public static List<Faction> targets(War war, boolean attackerSide) {
		if (isCivilWar(war)) {
			return attackerSide ? List.of() : rebelVassals(war);
		}
		Side enemy = attackerSide ? war.getDefenders() : war.getAttackers();
		List<Faction> result = new ArrayList<>();
		for (Participant participant : enemy.getMainParticipants()) {
			add(result, participant.getLeader());
		}
		if (!attackerSide || isUsurpOnOverlord(war)) {
			for (Participant participant : enemy.getMainParticipants()) {
				for (Faction joined : participant.getJoinedSecondaries()) {
					add(result, joined);
				}
			}
		}
		return result;
	}

	/** The real factions on the rebel side: every main participant but the temporary rebel realm. */
	static List<Faction> rebelVassals(War war) {
		String rebelsId = war.getCivilWarSnapshot() == null ? null : war.getCivilWarSnapshot().getTempRebelFactionId();
		List<Faction> result = new ArrayList<>();
		for (Participant participant : war.getAttackers().getMainParticipants()) {
			Faction leader = participant.getLeader();
			if (leader != null && !leader.getId().equalsIgnoreCase(rebelsId)) {
				add(result, leader);
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
		if (war == null || !war.isActive() || war.getWarType() == WarType.RAID) {
			return NOT_AVAILABLE;
		}
		boolean civilWar = isCivilWar(war);
		if (civilWar && attackerSide) {
			return REBEL_DEMANDS;
		}
		String leaderId = attackerSide ? war.getAttackerLeaderId() : war.getDefenderLeaderId();
		if (editor == null || leaderId == null || !leaderId.equalsIgnoreCase(editor.getId())) {
			return NOT_LEADER;
		}
		if (war.hasFirstBattleStarted()) {
			return LOCKED;
		}
		if (civilWar) {
			// The ruler's only goal: it needs no counter goal first, only a rebel vassal to fall on.
			return rebelVassals(war).isEmpty() ? NO_REBEL_VASSALS : null;
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
		boolean civilWar = isCivilWar(war);
		if (!contains(targets(war, attackerSide), request.getDefender())) {
			return WarValidationResult.fail(civilWar
					? "§cDemands can only be made of a vassal that backed the rebellion."
					: attackerSide
							? "§cDemands can only be made of the defending side."
							: "§cDemands can only be made of the attackers.");
		}
		if (civilWar && request.getGoal() == WarGoalType.USURP) {
			return WarValidationResult.fail("§cYou cannot usurp your own vassal.");
		}
		if (conflicts(firstDemand(war, attackerSide), WarDemand.fromRequest(request))) {
			return WarValidationResult.fail("§cThat clashes with your first demand on "
					+ request.getDefender().getName() + "§c.");
		}
		// The ruler's goal falls inside the ruler's own realm.
		return new WarGoalValidator().validate(civilWar ? request.asCivilWarDemand() : request.asDemand());
	}

	private static boolean sameId(String a, String b) {
		return a == null ? b == null : a.equalsIgnoreCase(b);
	}

	private static boolean contains(List<Faction> factions, Faction faction) {
		return faction != null && faction.getId() != null
				&& factions.stream().anyMatch(candidate -> faction.getId().equalsIgnoreCase(candidate.getId()));
	}

	private static void add(List<Faction> factions, Faction faction) {
		if (faction != null && faction.getId() != null && !contains(factions, faction)) factions.add(faction);
	}
}
