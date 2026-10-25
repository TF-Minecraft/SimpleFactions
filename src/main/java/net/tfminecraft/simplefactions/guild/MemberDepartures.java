package net.tfminecraft.simplefactions.guild;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.session.Session;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;

/**
 * A living character leaving a guild or a realm, by choice or by a kick. Like a
 * death, it takes away what only members may hold, so nothing stale is left behind.
 */
public final class MemberDepartures {
	private MemberDepartures() {}

	/** From a guild back to the realm's main guild: {@code /guild leave} and {@code /guild kick}. */
	public static void leaveGuild(Guild guild, String key) {
		guild.kick(key);
		guild.forgetDeparted(key);
		guild.getFaction().addMember(key);
	}

	/** Out of the realm and every guild in it: {@code /faction leave} ({@code voluntary}) and {@code /faction kick}. */
	public static void leaveRealm(Faction faction, String key, boolean voluntary) {
		faction.forceRemoveMember(key);
		for (Guild guild : faction.getGuildHandler().getGuilds()) guild.forgetDeparted(key);
		// Council seat, proposals, election ballots and candidacy, movement roles.
		faction.getGovernment().removeRights(key);
		var plugin = SimpleFactions.getInstance();
		if (plugin != null && plugin.getSessionManager() != null) {
			Session session = plugin.getSessionManager().getSession(faction.getGovernment().getCouncil());
			// As for a death: their vote and place in a sitting council session go.
			if (session != null) session.removeDeadCharacter(key);
		}
		faction.getEspionage().vacateFor(key);
		Members.ref(key).ifPresent(ref -> WarbandMembershipService.getInstance().removeFromFactionWarband(ref, voluntary));
		faction.updatePrestige();
	}
}
