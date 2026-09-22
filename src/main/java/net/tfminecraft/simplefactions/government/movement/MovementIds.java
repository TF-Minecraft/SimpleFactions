package net.tfminecraft.simplefactions.government.movement;

import java.util.Locale;

import net.tfminecraft.simplefactions.managers.FactionManager;

public final class MovementIds {
	private MovementIds() {}

	public static String allocate(String founder) {
		String base = slug(founder);
		if (FactionManager.getMovementById(base) == null) {
			return base;
		}
		int suffix = 2;
		while (FactionManager.getMovementById(base + "_" + suffix) != null) {
			suffix++;
		}
		return base + "_" + suffix;
	}

	public static String slug(String founder) {
		if (founder == null || founder.isBlank()) {
			return "unknown_movement";
		}
		return founder.toLowerCase(Locale.ROOT) + "_movement";
	}
}
