package net.tfminecraft.simplefactions.war.resolution;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.government.movement.Action;

public final class CouncilPeaceQueries {

	private CouncilPeaceQueries() {}

	public static boolean isWarEndAction(Action action) {
		return action == Action.WHITE_PEACE || action == Action.SURRENDER;
	}

	public static Integer parseWarId(String target) {
		if (target == null || target.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(target.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	public static List<War> warsFor(Faction faction) {
		List<War> wars = new ArrayList<>();
		if (faction == null) {
			return wars;
		}
		for (War war : WarManager.getActive()) {
			if (war != null && war.isParticipating(faction)) {
				wars.add(war);
			}
		}
		return wars;
	}

	public static boolean isParticipatingInAny(Faction faction) {
		return !warsFor(faction).isEmpty();
	}

	public static War warFromTarget(String target) {
		Integer id = parseWarId(target);
		if (id == null) {
			return null;
		}
		War war = WarManager.getById(id);
		if (war == null || !war.isActive()) {
			return null;
		}
		return war;
	}

	public static boolean isValidTarget(Faction faction, String target) {
		War war = warFromTarget(target);
		return war != null && faction != null && war.isParticipating(faction);
	}

	public static Faction sideMain(War war, Faction actor) {
		if (war == null || actor == null) {
			return null;
		}
		Side side = war.getSide(actor);
		if (side == null) {
			return null;
		}
		return side.getLeader();
	}
}
