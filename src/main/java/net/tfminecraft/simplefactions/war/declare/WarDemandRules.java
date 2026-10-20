package net.tfminecraft.simplefactions.war.declare;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarWartimeVassalEnd;
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

	/** What a ruler may take from a vassal that backed the rebels: never their freedom, nor its own title. */
	private static final Set<WarGoalType> CIVIL_WAR_GOALS = EnumSet.of(
			WarGoalType.SUBJUGATE,
			WarGoalType.TRANSFER_SUBJECT,
			WarGoalType.DE_JURE_ANNEX,
			WarGoalType.OPEN_MARKET,
			WarGoalType.CHANGE_GOVERNMENT);

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

	/**
	 * The side leader, then its subjects, then each faction that joined the war with them.
	 * In a civil war the ruler's goal is the ruler's alone.
	 */
	public static List<Faction> beneficiaries(War war, boolean attackerSide) {
		Side side = attackerSide ? war.getAttackers() : war.getDefenders();
		List<Faction> result = new ArrayList<>();
		if (isCivilWar(war)) {
			add(result, side.getLeader());
			return result;
		}
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

	public static boolean isCivilWarGoal(WarGoalType goal) {
		return goal != null && CIVIL_WAR_GOALS.contains(goal);
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
		if (civilWar) {
			return validateCivilWarGoal(war, request);
		}
		if (conflicts(firstDemand(war, attackerSide), WarDemand.fromRequest(request))) {
			return WarValidationResult.fail("§cThat clashes with your first demand on "
					+ request.getDefender().getName() + "§c.");
		}
		return new WarGoalValidator().validate(request.asDemand());
	}

	/**
	 * The ruler's goal on a rebel vassal. It is applied once the war has restored the realm,
	 * so it is checked against the realm as it will be then, not as the rebellion left it:
	 * the backing vassals answer to the ruler again and count toward its relation limits.
	 */
	static WarValidationResult validateCivilWarGoal(War war, WarDeclareRequest request) {
		Faction ruler = request.getAttacker();
		Faction vassal = request.getDefender();
		if (!isCivilWarGoal(request.getGoal())) {
			return WarValidationResult.fail("§cThat goal cannot be made of your own vassal.");
		}
		if (inAnotherCivilWar(war, vassal)) {
			return WarValidationResult.fail(CivilWarCopy.DECLARE_VS_CIVIL_WAR);
		}
		return switch (request.getGoal()) {
			case SUBJUGATE -> validateNewSubjectType(war, ruler, vassal, request.getRelationTypeId());
			case TRANSFER_SUBJECT -> validateTakenSubject(war, ruler, vassal, request.getSubjectFactionId());
			default -> new WarGoalValidator().validate(request.asCivilWarDemand());
		};
	}

	private static WarValidationResult validateNewSubjectType(War war, Faction ruler, Faction vassal, String typeId) {
		RelationType type = typeId == null ? null : RelationLoader.getType(typeId);
		if (type == null || !type.isVassalage()) {
			return WarValidationResult.fail("§cSpecify a subject type.");
		}
		CivilWarWartimeVassalEnd end = vassalEnd(war, vassal, ruler);
		if (end == null) {
			return WarValidationResult.fail("§cOnly your direct vassals can be given a new subject type.");
		}
		if (type.getId().equalsIgnoreCase(end.relationTypeId())) {
			return WarValidationResult.fail("§cThey are already your " + type.getName() + "§c.");
		}
		if (overLimitAfterWar(war, ruler, type, vassal.getId())) {
			return WarValidationResult.fail("§cYou have reached the limit for this relation type.");
		}
		return WarValidationResult.ok();
	}

	private static WarValidationResult validateTakenSubject(War war, Faction ruler, Faction vassal, String subjectId) {
		Faction subject = subjectId == null ? null : FactionManager.getByString(subjectId);
		Faction liege = subject == null ? null : FactionManager.getByString(RelationManager.getOverlord(subject));
		if (liege == null || !RelationManager.isOnOverlordPath(subject, vassal)) {
			return WarValidationResult.fail("§cThat faction is not a subject of " + vassal.getName() + "§c.");
		}
		if (inAnotherCivilWar(war, subject)) {
			return WarValidationResult.fail(CivilWarCopy.DECLARE_VS_CIVIL_WAR);
		}
		RelationType type = liege.getRelation(subject.getId()).getType();
		if (overLimitAfterWar(war, ruler, type, null)) {
			return WarValidationResult.fail("§cYou have reached the limit for this relation type.");
		}
		return WarValidationResult.ok();
	}

	/** Whether a civil war other than this one holds the faction. */
	private static boolean inAnotherCivilWar(War war, Faction faction) {
		War other = CivilWarBorderLock.findActiveCivilWarInvolving(faction);
		return other != null && other.getId() != war.getId();
	}

	/** The backing vassal's tie to the ruler, suspended for the war and restored when it ends. */
	private static CivilWarWartimeVassalEnd vassalEnd(War war, Faction vassal, Faction ruler) {
		if (war.getCivilWarSnapshot() == null) {
			return null;
		}
		for (CivilWarWartimeVassalEnd end : war.getCivilWarSnapshot().getWartimeVassalEnds()) {
			if (end != null && vassal.getId().equalsIgnoreCase(end.factionId())
					&& ruler.getId().equalsIgnoreCase(end.formerOverlordId())) {
				return end;
			}
		}
		return null;
	}

	/** The ruler's count of this relation type once the war returns its rebel vassals, leaving out one of them. */
	private static boolean overLimitAfterWar(War war, Faction ruler, RelationType type, String excludeId) {
		if (type == null || !type.hasLimit()) {
			return false;
		}
		int count = RelationManager.getRelationCount(ruler, type);
		if (war.getCivilWarSnapshot() != null) {
			for (CivilWarWartimeVassalEnd end : war.getCivilWarSnapshot().getWartimeVassalEnds()) {
				if (end != null && ruler.getId().equalsIgnoreCase(end.formerOverlordId())
						&& type.getId().equalsIgnoreCase(end.relationTypeId())
						&& !end.factionId().equalsIgnoreCase(excludeId)) {
					count++;
				}
			}
		}
		return count >= type.getLimit();
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
