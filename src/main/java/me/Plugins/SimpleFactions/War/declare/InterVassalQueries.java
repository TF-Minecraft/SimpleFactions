package me.Plugins.SimpleFactions.War.declare;

import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;

public final class InterVassalQueries {
	private InterVassalQueries() {}

	public static String topLiegeId(Faction faction) {
		if (faction == null) {
			return null;
		}
		return RelationManager.getTopLiege(faction);
	}

	public static boolean sharesTopLiege(Faction a, Faction b) {
		String left = topLiegeId(a);
		String right = topLiegeId(b);
		if (left == null || right == null) {
			return false;
		}
		return left.equalsIgnoreCase(right);
	}

	public static boolean isInternalPeer(Faction a, Faction b) {
		return sharesTopLiege(a, b) && !RelationManager.sameRealm(a, b);
	}

	public static boolean isInternalPeerWar(War war) {
		if (war == null || war.getAttackers() == null || war.getDefenders() == null) {
			return false;
		}
		return isInternalPeer(war.getAttackers().getLeader(), war.getDefenders().getLeader());
	}

	public static boolean isNestedUnder(Faction faction, Faction ancestor) {
		if (faction == null || ancestor == null) {
			return false;
		}
		return RelationManager.isOnOverlordPath(faction, ancestor);
	}

	public static boolean isOverlordOfMain(Faction faction, War war) {
		if (faction == null || war == null) {
			return false;
		}
		return isOverlordOfSideMains(faction, war.getAttackers())
				|| isOverlordOfSideMains(faction, war.getDefenders());
	}

	private static boolean isOverlordOfSideMains(Faction faction, Side side) {
		if (side == null || side.getMainParticipants() == null) {
			return false;
		}
		for (Participant participant : side.getMainParticipants()) {
			if (participant == null || participant.getLeader() == null) {
				continue;
			}
			if (RelationManager.isOnOverlordPath(participant.getLeader(), faction)) {
				return true;
			}
		}
		return false;
	}
}
