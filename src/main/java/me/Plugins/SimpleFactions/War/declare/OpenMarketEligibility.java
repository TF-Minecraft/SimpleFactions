package me.Plugins.SimpleFactions.War.declare;

import java.util.List;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public final class OpenMarketEligibility {
	private OpenMarketEligibility() {}

	public record ResolvedLaw(Law law, LawGroup group) {}

	public static boolean hasAnyCurrentLaw(Faction faction, List<String> ids) {
		if (faction == null || ids == null || ids.isEmpty()) {
			return false;
		}
		LawHandler handler = faction.getLawHandler();
		if (handler == null) {
			return false;
		}
		List<Law> current = handler.getCurrentLaws();
		if (current == null) {
			return false;
		}
		for (Law law : current) {
			if (law == null || law.getId() == null) {
				continue;
			}
			for (String id : ids) {
				if (id != null && id.equalsIgnoreCase(law.getId())) {
					return true;
				}
			}
		}
		return false;
	}

	public static ResolvedLaw resolve(Faction faction, String lawId) {
		if (faction == null || lawId == null || lawId.isBlank()) {
			return null;
		}
		LawHandler handler = faction.getLawHandler();
		if (handler == null) {
			return null;
		}
		List<LawGroup> groups = handler.getGroupList();
		if (groups == null) {
			return null;
		}
		for (LawGroup group : groups) {
			if (group == null || group.getLaws() == null) {
				continue;
			}
			for (Law law : group.getLaws().values()) {
				if (law != null && law.getId() != null && law.getId().equalsIgnoreCase(lawId.trim())) {
					return new ResolvedLaw(law, group);
				}
			}
		}
		return null;
	}
}
