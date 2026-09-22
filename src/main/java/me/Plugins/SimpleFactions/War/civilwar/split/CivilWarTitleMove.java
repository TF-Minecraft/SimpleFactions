package me.Plugins.SimpleFactions.War.civilwar.split;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;

public final class CivilWarTitleMove {
	private CivilWarTitleMove() {}

	public static Title pick(Faction host, Faction rebels, int rebelCapital) {
		if (host == null || rebels == null || rebelCapital <= 0) {
			return null;
		}
		Title county = TitleLoader.getByProvince(rebelCapital);
		if (county == null) {
			return null;
		}
		List<Title> chain = new ArrayList<>();
		Title walk = county;
		int guard = 0;
		while (walk != null && guard++ < 16) {
			chain.add(walk);
			walk = TitleManager.getParent(walk);
		}
		Title primary = host.getHighestTitle();
		for (int i = chain.size() - 1; i >= 0; i--) {
			Title candidate = chain.get(i);
			if (candidate == null || !host.hasTitle(candidate)) {
				continue;
			}
			if (primary != null && candidate.equals(primary)) {
				continue;
			}
			if (!candidate.canBeHeld(rebels)) {
				continue;
			}
			return candidate;
		}
		return null;
	}

	public static void transfer(Faction from, Faction to, Title title) {
		if (from == null || to == null || title == null) {
			return;
		}
		from.stripTitle(title);
		to.addTitle(title);
		LogManager.civilwar(
				"TITLE_MOVE id=%s from=%s to=%s",
				title.getId(),
				from.getId(),
				to.getId());
	}
}
