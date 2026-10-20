package net.tfminecraft.simplefactions.government.movement;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;

/**
 * A vassal takes its overlord's highest title through a movement in the overlord's
 * government, not a war declared from outside. The cause leader's faction usurps.
 */
public final class UsurpCause {
	private UsurpCause() {}

	/**
	 * True when {@code vassal} is a direct subject of {@code overlord} and may take its title.
	 * The usurper must be allowed vassals, since the old overlord becomes its subject.
	 */
	public static boolean canUsurp(Faction vassal, Faction overlord) {
		if (vassal == null || overlord == null || overlord.getHighestTitle() == null || !vassal.canHaveVassals()) {
			return false;
		}
		if (!RelationManager.isOverlord(vassal, overlord)) {
			return false;
		}
		return WarGoalValidator.canUsurpByRank(vassal.getTier().getTier(), overlord.getTier().getTier());
	}

	/** The faction that takes the title: the cause leader's, else the first eligible faction in the cause. */
	public static Faction usurper(Cause cause, Faction overlord) {
		if (cause == null) {
			return null;
		}
		Faction led = cause.hasLeader() ? FactionManager.getByMember(cause.getLeader()) : null;
		if (canUsurp(led, overlord)) {
			return led;
		}
		for (Faction faction : cause.getPool().getFactions()) {
			if (canUsurp(faction, overlord)) {
				return faction;
			}
		}
		return null;
	}

	public static void apply(Cause cause, Faction overlord) {
		Faction usurper = usurper(cause, overlord);
		if (usurper != null) {
			FactionManager.usurp(null, usurper, overlord);
		}
	}
}
