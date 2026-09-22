package me.Plugins.SimpleFactions.War.declare;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public final class ChangeGovernmentEligibility {
	public static final String GOVERNMENT_GROUP = "government";
	public static final String LEADERSHIP_GROUP = "leadership";

	private ChangeGovernmentEligibility() {}

	public static LawGroup group(Faction faction, String groupId) {
		if (faction == null || groupId == null || faction.getLawHandler() == null) {
			return null;
		}
		return faction.getLawHandler().getGroup(groupId);
	}

	public static String currentLawId(Faction faction, String groupId) {
		LawGroup group = group(faction, groupId);
		if (group == null || group.getCurrent() == null) {
			return null;
		}
		return group.getCurrent().getId();
	}

	public static boolean lawInGroup(Faction faction, String groupId, String lawId) {
		if (lawId == null || lawId.isBlank()) {
			return false;
		}
		LawGroup group = group(faction, groupId);
		if (group == null || group.getLaws() == null) {
			return false;
		}
		for (Law law : group.getLaws().values()) {
			if (law != null && law.getId() != null && law.getId().equalsIgnoreCase(lawId.trim())) {
				return true;
			}
		}
		return false;
	}

	public static boolean combinationEqualsCurrent(Faction faction, String governmentLawId, String leadershipLawId) {
		if (faction == null) {
			return false;
		}
		String currentGov = currentLawId(faction, GOVERNMENT_GROUP);
		if (!idsEqual(currentGov, governmentLawId)) {
			return false;
		}
		LawGroup leadership = group(faction, LEADERSHIP_GROUP);
		if (leadership == null) {
			return true;
		}
		String currentLead = currentLawId(faction, LEADERSHIP_GROUP);
		String selectedLead = blankToNull(leadershipLawId);
		if (selectedLead == null) {
			return true;
		}
		return idsEqual(currentLead, selectedLead);
	}

	public static boolean hasLeadershipGroup(Faction faction) {
		return group(faction, LEADERSHIP_GROUP) != null;
	}

	private static boolean idsEqual(String a, String b) {
		if (a == null || a.isBlank()) {
			return b == null || b.isBlank();
		}
		return b != null && a.equalsIgnoreCase(b.trim());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
