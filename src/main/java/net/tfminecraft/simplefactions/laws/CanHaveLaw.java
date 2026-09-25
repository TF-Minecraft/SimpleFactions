package net.tfminecraft.simplefactions.laws;

import java.util.List;

import net.tfminecraft.simplefactions.objects.Faction;

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
		String lockReason = lockReason(faction, law, System.currentTimeMillis());
		if (lockReason != null) {
			return lockReason;
		}
		String requirementReason = requirementsReason(faction, law);
		if (requirementReason != null) {
			return requirementReason;
		}
		return compatibilityReason(faction, law);
	}

	/** Why the law's group cannot be switched yet, or null if it can. */
	public static String lockReason(Faction faction, Law law, long now) {
		if (faction == null || faction.getLawHandler() == null || law == null || law.getGroup() == null) {
			return null;
		}
		LawGroup group = faction.getLawHandler().getGroup(law.getGroup());
		if (group == null) {
			return null;
		}
		long remaining = group.lockRemaining(now);
		if (remaining <= 0) {
			return null;
		}
		return "§cThis law was changed recently. It can change again in " + formatRemaining(remaining) + ".";
	}

	static String formatRemaining(long millis) {
		long minutes = Math.max(1, (millis + 59_999) / 60_000);
		long days = minutes / 1440;
		long hours = minutes % 1440 / 60;
		long mins = minutes % 60;
		if (days > 0) return days + "d " + hours + "h";
		if (hours > 0) return hours + "h " + mins + "m";
		return mins + "m";
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
