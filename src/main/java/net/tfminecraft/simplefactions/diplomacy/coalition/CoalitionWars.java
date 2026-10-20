package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.core.CallToArmsEligibility;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * When coalition members may be called into a war. A member that is a main participant
 * may call the others when the coalition's target fights on the other side, as a main
 * or a called-in participant. A defensive coalition only answers when that member defends.
 */
public final class CoalitionWars {
	/** Coalition id and war id pairs already announced, so members hear about a war once. */
	private static final Set<String> announced = new HashSet<>();

	private CoalitionWars() {}

	/**
	 * The coalition through which the caller may call the target into the war, or null.
	 * Only says whether the coalition allows it; {@code CallToArmsEligibility} still
	 * applies every ordinary call-to-arms rule on top.
	 */
	public static Coalition callingCoalition(War war, Faction caller, Faction target) {
		if (!CoalitionSettings.enabled) return null;
		if (caller == null || target == null || caller.getId().equalsIgnoreCase(target.getId())) return null;
		Coalition coalition = CoalitionService.of(caller);
		if (coalition == null || !coalition.isMember(target.getId())) return null;
		if (!isCoalitionWar(war, coalition, caller)) return null;
		// An ally is called as an ally, so it is listed once.
		Participant participant = war.getParticipant(caller);
		if (participant.getAllies().containsKey(target)) return null;
		if (war.getSide(caller) == war.getAttackers()) {
			// Joining a coalition mid-war only helps on the defending side, for caller and called alike.
			long started = war.getStartedAt().toEpochMilli();
			if (coalition.joinedAt(target.getId()) > started || coalition.joinedAt(caller.getId()) > started) return null;
		}
		return coalition;
	}

	public static boolean canCallMember(War war, Faction caller, Faction target) {
		return callingCoalition(war, caller, target) != null;
	}

	/** Whether this member, as a main participant, may call its coalition into the war. */
	public static boolean isCoalitionWar(War war, Coalition coalition, Faction member) {
		if (war == null || !war.isActive() || CivilWarBorderLock.isCivilWar(war)) return false;
		if (CoalitionService.targetLost(coalition) || war.getParticipant(member) == null) return false;
		Faction target = CoalitionService.target(coalition);
		Side memberSide = war.getSide(member);
		Side targetSide = target == null ? null : war.getSide(target);
		if (targetSide == null || targetSide == memberSide) return false;
		return coalition.isOffensive() || memberSide == war.getDefenders();
	}

	/** Coalition members the caller could call into the war right now. */
	public static List<Faction> callableMembers(War war, Faction caller) {
		List<Faction> result = new ArrayList<>();
		Coalition coalition = CoalitionService.of(caller);
		if (coalition == null) return result;
		for (Faction member : CoalitionService.members(coalition)) {
			// Every ordinary call-to-arms rule applies too, so nobody is offered who would be refused.
			if (CallToArmsEligibility.canCall(war, caller, member).allowed() && canCallMember(war, caller, member)) {
				result.add(member);
			}
		}
		return result;
	}

	/** True while a war is running with a member and the coalition's target on opposite sides. */
	public static boolean activeCoalitionWar(Coalition coalition) {
		Faction target = CoalitionService.target(coalition);
		if (target == null) return false;
		for (Faction member : CoalitionService.members(coalition)) {
			if (WarManager.existsHostile(member, target)) return true;
		}
		return false;
	}

	/** True while the faction fights in a running war it joined through a coalition call. */
	public static boolean joinedThroughCoalition(Faction faction) {
		for (War war : WarManager.getActive()) {
			for (Participant participant : mains(war)) {
				if (participant.hasCoalitionAlly(faction)) return true;
			}
		}
		return false;
	}

	/**
	 * Why the target may not join the caller's side because a fellow coalition member
	 * leads the other side, or null when it may.
	 */
	public static String enemyCoalitionBlock(War war, Faction caller, Faction target) {
		Side callerSide = war.getSide(caller);
		Side enemySide = callerSide == war.getAttackers() ? war.getDefenders() : war.getAttackers();
		for (Participant enemy : enemySide.getMainParticipants()) {
			if (CoalitionService.sameCoalition(target, enemy.getLeader())) {
				return "§cThat faction is in a coalition with your enemy.";
			}
		}
		return null;
	}

	/** Lines for the declare confirmation when a coalition could join the new war. */
	public static List<String> declareWarnings(Faction attacker, Faction defender) {
		List<String> lines = new ArrayList<>();
		Coalition defending = CoalitionService.of(defender);
		if (defending != null && defending.isTarget(attacker.getId()) && !CoalitionService.targetLost(defending)) {
			lines.add("§c" + defender.getName() + " §cis in the " + CoalitionService.displayName(defending)
					+ " §ccoalition, which targets you.");
			lines.add("§cIts members can be called into this war.");
		}
		Coalition attacking = CoalitionService.of(attacker);
		if (attacking != null && attacking.isOffensive() && attacking.isTarget(defender.getId())
				&& !CoalitionService.targetLost(attacking)) {
			lines.add("§aYou can call members of the " + CoalitionService.displayName(attacking)
					+ " §acoalition into this war.");
		}
		return lines;
	}

	/**
	 * Calling a coalition's target in as an ally hands that coalition a war. Returns the
	 * warning for the caller, or null when the call would not.
	 */
	public static String allyCallWarning(War war, Faction caller, Faction ally) {
		Coalition coalition = coalitionOpposing(war, caller, ally);
		if (coalition == null) return null;
		return "§cCalling " + ally.getName() + " §cwill let the " + CoalitionService.displayName(coalition)
				+ " §ccoalition (" + names(coalition) + "§c) join this war against you.";
	}

	/** The same warning, worded for the ally reading the call. */
	public static String acceptWarning(War war, Faction caller, Faction ally) {
		Coalition coalition = coalitionOpposing(war, caller, ally);
		if (coalition == null) return null;
		return "§cAccepting will let the " + CoalitionService.displayName(coalition)
				+ " §ccoalition, which targets you, join this war.";
	}

	/**
	 * A coalition aimed at the ally with a member leading the other side, which could call
	 * its members in once the ally joins the caller's side.
	 */
	private static Coalition coalitionOpposing(War war, Faction caller, Faction ally) {
		Side callerSide = war.getSide(caller);
		Side enemySide = callerSide == war.getAttackers() ? war.getDefenders() : war.getAttackers();
		for (Coalition coalition : CoalitionService.targeting(ally)) {
			if (CoalitionService.targetLost(coalition)) continue;
			for (Participant enemy : enemySide.getMainParticipants()) {
				if (!coalition.isMember(enemy.getLeader().getId())) continue;
				if (coalition.isOffensive() || enemySide == war.getDefenders()) return coalition;
			}
		}
		return null;
	}

	/** Tells each coalition the first time one of its members may call it into the war. */
	public static void announce(War war) {
		for (Participant participant : mains(war)) {
			Faction member = participant.getLeader();
			Coalition coalition = CoalitionService.of(member);
			if (coalition == null || !isCoalitionWar(war, coalition, member)) continue;
			if (!announced.add(coalition.getId() + "|" + war.getId())) continue;
			CoalitionService.notifyMembers(coalition, "§e" + CoalitionService.displayName(coalition)
					+ " §ecan now be called into " + war.getName() + " §eby " + member.getName() + "§e.");
		}
	}

	private static List<Participant> mains(War war) {
		List<Participant> mains = new ArrayList<>(war.getAttackers().getMainParticipants());
		mains.addAll(war.getDefenders().getMainParticipants());
		return mains;
	}

	private static String names(Coalition coalition) {
		List<String> names = new ArrayList<>();
		for (Faction member : CoalitionService.members(coalition)) names.add(member.getName());
		return String.join("§c, ", names);
	}

	/** Test hook: forgets which wars were announced. */
	static void reset() {
		announced.clear();
	}
}
