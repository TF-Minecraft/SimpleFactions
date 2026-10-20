package net.tfminecraft.simplefactions.war.core;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.declare.InterVassalQueries;
import net.tfminecraft.simplefactions.war.truce.TruceService;

public final class CallToArmsEligibility {
	public static final String ALREADY_IN_WAR = "§cTarget faction is already part of the war";
	public static final String CANNOT_CALL = "§cYou cannot call that faction to arms.";
	public static final String OVERLORD = "§cYou cannot call the overlord into this war.";
	public static final String BOUND_TO_ENEMY = "§cThat faction is bound to the enemy.";
	public static final String LIEGE_ALREADY_MAIN = "§cYou cannot call a vassal of a nation already in this war.";

	private CallToArmsEligibility() {}

	public record Result(boolean allowed, String message) {
		public static Result allow() {
			return new Result(true, null);
		}

		public static Result deny(String message) {
			return new Result(false, message);
		}
	}

	public static Result canCall(War war, Faction caller, Faction target) {
		if (war == null || caller == null || target == null) {
			return Result.deny(CANNOT_CALL);
		}
		Participant participant = war.getParticipant(caller);
		if (participant == null) {
			return Result.deny(CANNOT_CALL);
		}
		if (war.isParticipating(target)) {
			return Result.deny(ALREADY_IN_WAR);
		}
		if (InterVassalQueries.isOverlordOfMain(target, war)) {
			return Result.deny(OVERLORD);
		}
		Side callerSide = war.getSide(caller);
		Side enemySide = callerSide == war.getAttackers() ? war.getDefenders() : war.getAttackers();
		for (Faction enemy : BattleSideMembers.collectParticipatingFactions(enemySide)) {
			if (InterVassalQueries.isNestedUnder(target, enemy)) {
				return Result.deny(BOUND_TO_ENEMY);
			}
		}
		if (topLiegeIsMain(target, war)) {
			return Result.deny(LIEGE_ALREADY_MAIN);
		}
		if (!isUnjoinedAlly(participant, target)) {
			return Result.deny(CANNOT_CALL);
		}
		// Joining an attack would break a truce; joining the defence never does.
		if (callerSide == war.getAttackers()) {
			for (Faction defender : BattleSideMembers.collectParticipatingFactions(war.getDefenders())) {
				java.time.Duration truce = TruceService.remaining(target, defender);
				if (!truce.isZero()) {
					return Result.deny("§c" + target.getName() + " §chas a truce with " + defender.getName()
							+ " §cfor another " + TruceService.formatRemaining(truce) + ".");
				}
			}
		}
		return Result.allow();
	}

	private static boolean isUnjoinedAlly(Participant participant, Faction target) {
		if (participant.getAllies() == null || target.getId() == null) {
			return false;
		}
		for (var entry : participant.getAllies().entrySet()) {
			Faction ally = entry.getKey();
			if (ally == null || ally.getId() == null) {
				continue;
			}
			if (!ally.getId().equalsIgnoreCase(target.getId())) {
				continue;
			}
			return !Boolean.TRUE.equals(entry.getValue());
		}
		return false;
	}

	private static boolean topLiegeIsMain(Faction target, War war) {
		String liegeId = InterVassalQueries.topLiegeId(target);
		if (liegeId == null) {
			return false;
		}
		return isMainLeaderId(war.getAttackers(), liegeId) || isMainLeaderId(war.getDefenders(), liegeId);
	}

	private static boolean isMainLeaderId(Side side, String factionId) {
		for (Participant participant : side.getMainParticipants()) {
			if (participant.getLeader().getId().equalsIgnoreCase(factionId)) {
				return true;
			}
		}
		return false;
	}
}
