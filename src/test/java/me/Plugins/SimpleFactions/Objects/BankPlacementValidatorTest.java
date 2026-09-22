package me.Plugins.SimpleFactions.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;

class BankPlacementValidatorTest {
	@Test
	void guild_withoutCapital_allowsAnyProvince() {
		Guild guild = mock(Guild.class);
		when(guild.hasCapital()).thenReturn(false);

		assertNull(BankPlacementValidator.failureReasonForGuild(guild, 42));
		assertNull(BankPlacementValidator.failureReasonForGuild(guild, -2));
	}

	@Test
	void guild_withCapital_allowsMatchingProvince() {
		Guild guild = mock(Guild.class);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(10);

		assertNull(BankPlacementValidator.failureReasonForGuild(guild, 10));
	}

	@Test
	void guild_withCapital_deniesWrongProvince() {
		Guild guild = mock(Guild.class);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(10);

		assertEquals(
				"§cYour bank must be placed in your guild capital province.",
				BankPlacementValidator.failureReasonForGuild(guild, 11));
	}

	@Test
	void guild_withCapital_deniesUnresolvedProvince() {
		Guild guild = mock(Guild.class);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(10);

		assertEquals(
				"§a[SimpleFactions] §cError! could not resolve province",
				BankPlacementValidator.failureReasonForGuild(guild, -2));
	}

	@Test
	void guild_withCapital_deniesNoProvince() {
		Guild guild = mock(Guild.class);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(10);

		assertEquals(
				"§cThis location has no province!",
				BankPlacementValidator.failureReasonForGuild(guild, 0));
	}

	@Test
	void faction_withoutCapital_allowsAnyProvince() {
		Faction faction = mock(Faction.class);
		when(faction.hasCapital()).thenReturn(false);

		assertNull(BankPlacementValidator.failureReasonForFaction(faction, 42));
	}

	@Test
	void faction_withCapital_allowsMatchingProvince() {
		Faction faction = mock(Faction.class);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(25);

		assertNull(BankPlacementValidator.failureReasonForFaction(faction, 25));
	}

	@Test
	void faction_withCapital_deniesWrongProvince() {
		Faction faction = mock(Faction.class);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(25);

		assertEquals(
				"§cYour faction bank must be placed in your capital province.",
				BankPlacementValidator.failureReasonForFaction(faction, 30));
	}

	@Test
	void faction_withCapital_deniesUnresolvedProvince() {
		Faction faction = mock(Faction.class);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(25);

		assertEquals(
				"§a[SimpleFactions] §cError! could not resolve province",
				BankPlacementValidator.failureReasonForFaction(faction, -2));
	}

	@Test
	void faction_withCapital_deniesNoProvince() {
		Faction faction = mock(Faction.class);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(25);

		assertEquals(
				"§cThis location has no province!",
				BankPlacementValidator.failureReasonForFaction(faction, 0));
	}
}
