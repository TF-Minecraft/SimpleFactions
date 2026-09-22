package me.Plugins.SimpleFactions.War.campaign.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;

public final class BattleSideMembers {
	private BattleSideMembers() {}

	public static List<Faction> collectParticipatingFactions(Side side) {
		if (side == null) {
			return List.of();
		}
		List<Faction> factions = new ArrayList<>();
		Set<String> seenIds = new LinkedHashSet<>();
		for (Participant participant : side.getMainParticipants()) {
			addFaction(participant.getLeader(), factions, seenIds);
			for (Faction subject : participant.getSubjects()) {
				addFaction(subject, factions, seenIds);
			}
			for (Faction secondary : participant.getJoinedSecondaries()) {
				addFaction(secondary, factions, seenIds);
			}
		}
		return List.copyOf(factions);
	}

	public static Set<String> collectEligibleMemberNames(Side side) {
		Set<String> names = new LinkedHashSet<>();
		for (Faction faction : collectParticipatingFactions(side)) {
			for (String member : faction.getMembers()) {
				if (member != null && !member.isBlank()) {
					names.add(member);
				}
			}
		}
		return names;
	}

	public static int countEligibleMembers(Side side) {
		return collectEligibleMemberNames(side).size();
	}

	public static BelligerentRole resolveSide(War war, Faction faction) {
		if (war == null || faction == null) {
			return null;
		}
		Side side = war.getSide(faction);
		if (side == null) {
			return null;
		}
		if (side == war.getAttackers()) {
			return BelligerentRole.ATTACKER;
		}
		if (side == war.getDefenders()) {
			return BelligerentRole.DEFENDER;
		}
		return null;
	}

	private static void addFaction(Faction faction, List<Faction> factions, Set<String> seenIds) {
		if (faction == null) {
			return;
		}
		String id = faction.getId();
		if (id == null || seenIds.contains(id.toLowerCase())) {
			return;
		}
		seenIds.add(id.toLowerCase());
		factions.add(faction);
	}
}
