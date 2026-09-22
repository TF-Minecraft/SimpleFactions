package me.Plugins.SimpleFactions.War.civilwar.wartime;



import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarStartService;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.government.movement.Movement;

public final class CivilWarBorderLock {
	private CivilWarBorderLock() {}

	public static boolean isCivilWar(War war) {
		if (war == null || !war.isActive()) {
			return false;
		}
		if (war.getCivilWarSnapshot() != null) {
			return true;
		}
		String movementId = war.getMovementId();
		return movementId != null && !movementId.isBlank();
	}

	public static boolean isLocked(Faction faction) {
		return faction != null && faction.getId() != null && isLocked(faction.getId());
	}

	public static boolean isLocked(String factionId) {
		return findActiveCivilWarInvolving(factionId) != null;
	}

	public static War findActiveCivilWarInvolving(Faction faction) {
		return faction == null ? null : findActiveCivilWarInvolving(faction.getId());
	}

	public static War findActiveCivilWarInvolving(String factionId) {
		if (factionId == null || factionId.isBlank()) {
			return null;
		}
		for (War war : activeWars()) {
			if (!isCivilWar(war)) {
				continue;
			}
			if (containsIgnoreCase(involvedIds(war), factionId)) {
				return war;
			}
		}
		return null;
	}

	public static Set<String> involvedIds(War war) {
		Set<String> ids = new LinkedHashSet<>();
		if (war == null) {
			return ids;
		}
		CivilWarSnapshot snapshot = war.getCivilWarSnapshot();
		if (snapshot != null) {
			add(ids, snapshot.getHostFactionId());
			add(ids, snapshot.getTempRebelFactionId());
			if (snapshot.getWartimeVassalEnds() != null) {
				for (CivilWarWartimeVassalEnd end : snapshot.getWartimeVassalEnds()) {
					if (end == null) {
						continue;
					}
					add(ids, end.factionId());
					addNestedSubjects(ids, end.factionId());
				}
			}
			return ids;
		}
		if (war.getDefenders() != null && war.getDefenders().getLeader() != null) {
			add(ids, war.getDefenders().getLeader().getId());
		}
		if (war.getAttackers() != null) {
			addSideMains(ids, war.getAttackers());
		}
		return ids;
	}

	public static boolean hostBlockedByDeJureOrTransfer(Faction host) {
		if (host == null || host.getId() == null) {
			return false;
		}
		String hostId = host.getId();
		for (War war : activeWars()) {
			if (war == null || !war.isActive() || war.getGoal() == null) {
				continue;
			}
			if (war.getGoal() == WarGoalType.DE_JURE_ANNEX && idEquals(war.getDefenderLeaderId(), hostId)) {
				return true;
			}
			if (war.getGoal() == WarGoalType.TRANSFER_SUBJECT) {
				if (idEquals(war.getDefenderLeaderId(), hostId) || idEquals(war.getSubjectFactionId(), hostId)) {
					return true;
				}
			}
		}
		return false;
	}

	public static String refuseStart(Movement movement, Faction host) {
		if (host == null) {
			return CivilWarCopy.COULD_NOT_START;
		}
		if (isLocked(host)) {
			return CivilWarCopy.ALREADY_IN_CIVIL_WAR;
		}
		if (movement != null) {
			for (Faction vassal : CivilWarStartService.supportingVassals(movement, host)) {
				if (isLocked(vassal)) {
					return CivilWarCopy.ALREADY_IN_CIVIL_WAR;
				}
			}
			Faction leaderFaction = FactionManager.getByMember(movement.getLeader());
			if (leaderFaction != null
					&& !leaderFaction.getId().equalsIgnoreCase(host.getId())
					&& isLocked(leaderFaction)) {
				return CivilWarCopy.ALREADY_IN_CIVIL_WAR;
			}
		}
		if (hostBlockedByDeJureOrTransfer(host)) {
			return CivilWarCopy.HOST_IS_WAR_PAYLOAD;
		}
		return null;
	}

	private static void addSideMains(Set<String> ids, Side side) {
		if (side.getLeader() != null) {
			add(ids, side.getLeader().getId());
		}
		if (side.getMainParticipants() == null) {
			return;
		}
		for (Participant participant : side.getMainParticipants()) {
			if (participant != null && participant.getLeader() != null) {
				add(ids, participant.getLeader().getId());
			}
		}
	}

	private static void addNestedSubjects(Set<String> ids, String rootId) {
		Faction root = FactionManager.getByString(rootId);
		if (root == null || FactionManager.factions == null) {
			return;
		}
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getId() == null) {
				continue;
			}
			if (faction.getId().equalsIgnoreCase(rootId)) {
				continue;
			}
			if (RelationManager.isOnOverlordPath(faction, root)) {
				add(ids, faction.getId());
			}
		}
	}

	private static void add(Set<String> ids, String id) {
		if (id != null && !id.isBlank()) {
			ids.add(id);
		}
	}

	private static boolean containsIgnoreCase(Set<String> ids, String factionId) {
		for (String id : ids) {
			if (idEquals(id, factionId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean idEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		return a.equalsIgnoreCase(b);
	}

	private static List<War> activeWars() {
		List<War> active = WarManager.getActive();
		return active == null ? List.of() : active;
	}
}
