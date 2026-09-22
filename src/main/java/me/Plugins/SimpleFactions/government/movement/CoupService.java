package me.Plugins.SimpleFactions.government.movement;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.government.Council;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public final class CoupService {
	static final String GOVERNMENT_GROUP = "government";
	static final String OLIGARCHY = "oligarchy";

	private CoupService() {}

	public static void apply(Faction faction, String wantedLeader) {
		if (faction == null || wantedLeader == null || wantedLeader.isBlank()) {
			return;
		}
		if (!faction.canBecomeLeader(wantedLeader)) {
			return;
		}
		faction.promoteToLeader(wantedLeader);
		adjustCouncil(faction);
	}

	private static void adjustCouncil(Faction faction) {
		LawHandler laws = faction.getLawHandler();
		if (laws == null) {
			return;
		}
		LawGroup group = laws.getGroup(GOVERNMENT_GROUP);
		if (group == null || group.getCurrent() == null || group.getCurrent().getId() == null) {
			return;
		}
		String id = group.getCurrent().getId();
		if ("autocracy".equalsIgnoreCase(id) || "community".equalsIgnoreCase(id)) {
			return;
		}
		if ("oligarchy".equalsIgnoreCase(id)) {
			clearCouncil(faction);
			return;
		}
		if ("plutocracy".equalsIgnoreCase(id) || "democracy".equalsIgnoreCase(id)) {
			Law oligarchy = group.getLaw(OLIGARCHY);
			if (oligarchy != null) {
				faction.applyLaw(oligarchy, group);
			}
			clearCouncil(faction);
		}
	}

	private static void clearCouncil(Faction faction) {
		Government government = faction.getGovernment();
		if (government == null || government.getCouncil() == null) {
			return;
		}
		Council council = government.getCouncil();
		council.clearMembers();
	}
}
