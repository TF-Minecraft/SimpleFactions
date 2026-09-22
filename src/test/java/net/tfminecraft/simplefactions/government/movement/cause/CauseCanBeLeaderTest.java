package net.tfminecraft.simplefactions.government.movement.cause;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.enums.Member;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;

class CauseCanBeLeaderTest {

	@Test
	void guildLeader_canLeadWhenGuildsAllowedEvenIfFactionsAreNot() {
		Cause cause = guildCause(true, false, true);
		assertTrue(cause.canBeLeader("Alice"));
	}

	@Test
	void guildLeader_cannotLeadWhenGuildsDisallowed() {
		Cause cause = guildCause(false, true, true);
		assertFalse(cause.canBeLeader("Alice"));
	}

	@Test
	void citizen_canLeadWhenCitizensAllowed() {
		Faction host = mock(Faction.class);
		Movement movement = mock(Movement.class);
		when(movement.getFaction()).thenReturn(host);
		when(host.getRelationToFaction("Alice")).thenReturn(Member.MEMBER);

		Proposal proposal = proposal(false, false, true);
		Cause cause = new Cause(movement, proposal, "Alice");
		assertTrue(cause.canBeLeader("Alice"));
	}

	private static Cause guildCause(boolean guilds, boolean factions, boolean citizens) {
		Faction host = mock(Faction.class);
		Movement movement = mock(Movement.class);
		when(movement.getFaction()).thenReturn(host);
		when(host.getRelationToFaction("Alice")).thenReturn(Member.GUILD_LEADER);

		Guild guild = mock(Guild.class);
		when(guild.getMembers()).thenReturn(List.of("Alice"));

		Proposal proposal = proposal(guilds, factions, citizens);
		try (MockedStatic<FactionManager> factionsMgr = mockStatic(FactionManager.class)) {
			factionsMgr.when(() -> FactionManager.getGuildByMember("Alice")).thenReturn(guild);
			return new Cause(movement, proposal, "Alice");
		}
	}

	private static Proposal proposal(boolean guilds, boolean factions, boolean citizens) {
		PoliticalAction action = mock(PoliticalAction.class);
		when(action.getAction()).thenReturn(Action.CHANGE_LEADER);
		when(action.allowGuilds()).thenReturn(guilds);
		when(action.allowFactions()).thenReturn(factions);
		when(action.allowCitizens()).thenReturn(citizens);
		Proposal proposal = mock(Proposal.class);
		when(proposal.getPoliticalAction()).thenReturn(action);
		return proposal;
	}
}
