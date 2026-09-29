package net.tfminecraft.simplefactions.government.stability;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;

/**
 * Trade-power cut on a guild the state is too small to bear.
 * Size is branch levels. The cut is applied after that comparison.
 */
public final class GovernmentIncompatibility {

	private GovernmentIncompatibility() {}

	public static double factor(Guild guild) {
		if (guild == null || guild.isBase()) return 1;
		Faction host = guild.getFaction();
		if (host == null) return 1;
		Guild state = realm(host);
		if (state == null) return 1;
		return factor(guild.getSize(), state.getSize(), ratio(host));
	}

	public static double factor(int guildSize, int stateSize, double ratio) {
		if (ratio <= 0 || guildSize <= 0) return 1;
		double required = guildSize * ratio;
		if (required <= 0 || stateSize >= required) return 1;
		return stateSize / required;
	}

	private static double ratio(Faction host) {
		StabilityTuning tuning = StabilityTuning.get();
		String government = law(host, "government", "autocracy");
		double ratio = StabilityMath.governmentExpectation(government, tuning);
		if ("elected".equals(law(host, "leadership", "fixed")) && !"democracy".equals(government)) {
			ratio *= tuning.electedWeakStateFactor;
		}
		return ratio;
	}

	private static Guild realm(Faction host) {
		if (host.getGuildHandler() == null || host.getGuildHandler().getGuilds() == null) return null;
		for (Guild guild : host.getGuildHandler().getGuilds()) {
			if (guild != null && guild.isBase()) return guild;
		}
		return null;
	}

	private static String law(Faction faction, String groupId, String fallback) {
		LawHandler laws = faction.getLawHandler();
		if (laws == null) return fallback;
		LawGroup group = laws.getGroup(groupId);
		if (group == null || group.getCurrent() == null || group.getCurrent().getId() == null) return fallback;
		Law current = group.getCurrent();
		return current.getId().toLowerCase();
	}
}
