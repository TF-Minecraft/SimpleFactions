package me.Plugins.SimpleFactions.laws;

import java.util.List;

import me.Plugins.SimpleFactions.Objects.Faction;

public final class CanHaveLaw {
	private CanHaveLaw() {}

	public static boolean canHave(Faction faction, Law law) {
		return blockReason(faction, law) == null;
	}

	public static String blockReason(Faction faction, Law law) {
		if (law == null) {
			return "§cThat law does not exist.";
		}
		if (isCurrentInGroup(faction, law)) {
			return null;
		}
		String requirementReason = requirementsReason(faction, law);
		if (requirementReason != null) {
			return requirementReason;
		}
		return compatibilityReason(faction, law);
	}

	private static boolean isCurrentInGroup(Faction faction, Law law) {
		Law current = currentInGroup(faction, law);
		return current != null && current.getId() != null && current.getId().equalsIgnoreCase(law.getId());
	}

	private static Law currentInGroup(Faction faction, Law law) {
		if (faction == null || faction.getLawHandler() == null || law.getGroup() == null) {
			return null;
		}
		LawGroup group = faction.getLawHandler().getGroup(law.getGroup());
		if (group == null) {
			return null;
		}
		return group.getCurrent();
	}

	private static String requirementsReason(Faction faction, Law law) {
		List<String> requirements = law.getRequirements();
		if (requirements == null || requirements.isEmpty()) {
			return null;
		}
		for (String line : requirements) {
			if (line == null || line.isBlank()) {
				continue;
			}
			String reason = requirementReason(faction, line.trim());
			if (reason != null) {
				return reason;
			}
		}
		return null;
	}

	private static String requirementReason(Faction faction, String line) {
		String[] parts = line.split("\\s+");
		if (parts.length == 0 || parts[0].isBlank()) {
			return null;
		}
		String verb = parts[0].toLowerCase();
		if ("has_law".equals(verb)) {
			if (parts.length < 2) {
				return "§cInvalid law requirement.";
			}
			String id = parts[1];
			if (!hasCurrentLaw(faction, id)) {
				return "§cRequires law: " + id + ".";
			}
			return null;
		}
		if ("not_law".equals(verb)) {
			if (parts.length < 2) {
				return "§cInvalid law requirement.";
			}
			String id = parts[1];
			if (hasCurrentLaw(faction, id)) {
				return "§cCannot have law: " + id + ".";
			}
			return null;
		}
		return "§cInvalid law requirement.";
	}

	private static boolean hasCurrentLaw(Faction faction, String lawId) {
		if (faction == null || faction.getLawHandler() == null || lawId == null) {
			return false;
		}
		List<Law> current = faction.getLawHandler().getCurrentLaws();
		if (current == null) {
			return false;
		}
		for (Law law : current) {
			if (law != null && law.getId() != null && law.getId().equalsIgnoreCase(lawId)) {
				return true;
			}
		}
		return false;
	}

	private static String compatibilityReason(Faction faction, Law law) {
		Law current = currentInGroup(faction, law);
		if (current == null || current.getId() == null) {
			return null;
		}
		if (law.getCompatibility(current.getId()) <= 0) {
			return "§cIncompatible with the current law in this group.";
		}
		return null;
	}
}
