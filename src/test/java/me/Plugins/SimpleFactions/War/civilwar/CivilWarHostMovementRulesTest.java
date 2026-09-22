package me.Plugins.SimpleFactions.War.civilwar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Member;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.proposal.Proposal;

class CivilWarHostMovementRulesTest {

	@Test
	void blocksHostGuildStartOnOneProvince() {
		Faction host = mock(Faction.class);
		when(host.getProvinces()).thenReturn(List.of(7));
		when(host.getRelationToFaction("guildLead")).thenReturn(Member.GUILD_LEADER);
		when(host.getRelationToFaction("vassalLead")).thenReturn(Member.VASSAL_LEADER);

		assertTrue(CivilWarHostMovementRules.blocksHostGuildStart(host, "guildLead"));
		assertFalse(CivilWarHostMovementRules.blocksHostGuildStart(host, "vassalLead"));
	}

	@Test
	void startMovement_skipsHostGuildOnOneProvince() {
		Faction host = mock(Faction.class);
		when(host.getProvinces()).thenReturn(List.of(7));
		when(host.getRelationToFaction("guildLead")).thenReturn(Member.GUILD_LEADER);
		Government government = new Government(host);

		government.startMovement("guildLead", mock(Proposal.class));

		assertTrue(government.getMovements().isEmpty());
	}
}