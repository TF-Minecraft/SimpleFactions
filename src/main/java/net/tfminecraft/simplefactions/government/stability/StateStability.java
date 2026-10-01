package net.tfminecraft.simplefactions.government.stability;

import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.stability.StabilityFacts.Body;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;

/** Reads a live faction into {@link StabilityMath}. */
public final class StateStability {

	private StateStability() {}

	public static StabilityReport of(Faction faction) {
		return StabilityMath.assess(facts(faction));
	}

	public static StabilityFacts facts(Faction faction) {
		StabilityFacts facts = new StabilityFacts();
		if (faction == null) {
			return facts;
		}
		facts.government = law(faction, "government", "autocracy");
		facts.electedLeadership = "elected".equals(law(faction, "leadership", "fixed"));
		facts.provinces = faction.getProvinces() == null ? 0 : faction.getProvinces().size();
		facts.bankrupt = faction.getOrCreateMainGuild() != null && faction.getOrCreateMainGuild().isBankrupt();
		if (faction.getGovernment() != null && faction.getGovernment().getStabilityModifiers() != null) {
			faction.getGovernment().getStabilityModifiers().forEach(modifier -> {
				if (modifier != null) {
					facts.temporary += modifier.getModifier();
				}
			});
		}
		Guild richest = null;
		if (faction.getGuildHandler() != null && faction.getGuildHandler().getGuilds() != null) {
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				if (guild == null) continue;
				facts.guilds.add(body(guild, faction));
				if (!guild.isBase() && richer(guild, richest)) {
					richest = guild;
				}
			}
		}
		facts.plutocracySeated = seated(faction, richest);
		for (Faction vassal : RelationManager.getSubjects(faction)) {
			if (vassal == null) continue;
			Guild main = vassal.getOrCreateMainGuild();
			Body body = new Body();
			body.name = vassal.getName() == null ? vassal.getId() : vassal.getName();
			body.members = vassal.getMembers() == null ? 0 : vassal.getMembers().size();
			body.stance = main == null || main.getStance(faction) == null
					? "SUPPORT"
					: main.getStance(faction).name();
			facts.vassals.add(body);
		}
		return facts;
	}

	private static Body body(Guild guild, Faction faction) {
		Body body = new Body();
		body.name = guild.getName() == null ? guild.getId() : strip(guild.getName());
		body.realm = guild.isBase();
		body.members = guild.getMembers() == null ? 0 : guild.getMembers().size();
		body.branchLevels = guild.getSize();
		Stance stance = guild.getStance(faction);
		body.stance = stance == null ? "SUPPORT" : stance.name();
		return body;
	}

	private static boolean richer(Guild guild, Guild current) {
		if (current == null) return true;
		double wealth = guild.getWealth() == null ? 0 : guild.getWealth();
		double other = current.getWealth() == null ? 0 : current.getWealth();
		if (wealth != other) return wealth > other;
		return guild.getSize() > current.getSize();
	}

	private static boolean seated(Faction faction, Guild richest) {
		if (richest == null || faction.getGovernment() == null) return false;
		if (faction.getGovernment().getCouncilMembers() == null || richest.getMembers() == null) return false;
		for (String member : richest.getMembers()) {
			for (String seat : faction.getGovernment().getCouncilMembers()) {
				if (member != null && member.equalsIgnoreCase(seat)) return true;
			}
		}
		return false;
	}

	private static String law(Faction faction, String groupId, String fallback) {
		LawHandler laws = faction.getLawHandler();
		if (laws == null) return fallback;
		LawGroup group = laws.getGroup(groupId);
		if (group == null || group.getCurrent() == null || group.getCurrent().getId() == null) {
			return fallback;
		}
		Law current = group.getCurrent();
		return current.getId();
	}

	private static String strip(String name) {
		return name.replaceAll("§x(?:§[0-9a-fA-F]){6}", "").replaceAll("§.", "");
	}
}
