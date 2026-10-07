package net.tfminecraft.simplefactions.war.declare;

import java.util.List;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

public final class OpenMarketEligibility {
	private OpenMarketEligibility() {}

	public record ResolvedLaw(Law law, LawGroup group) {}

	public static boolean hasAnyCurrentLaw(Faction faction, List<String> ids) {
		if (faction == null || ids == null || ids.isEmpty()) {
			return false;
		}
		LawHandler handler = faction.getLawHandler();
		List<Law> current = handler.getCurrentLaws();
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
		List<LawGroup> groups = handler.getGroupList();
		for (LawGroup group : groups) {
			for (Law law : group.getLaws().values()) {
				if (law != null && law.getId() != null && law.getId().equalsIgnoreCase(lawId.trim())) {
					return new ResolvedLaw(law, group);
				}
			}
		}
		return null;
	}
}
