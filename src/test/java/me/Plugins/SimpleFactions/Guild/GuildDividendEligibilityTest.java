package me.Plugins.SimpleFactions.Guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Cache;

class GuildDividendEligibilityTest {

	@Test
	void percentIsClamped() throws Exception {
		Guild guild = emptyGuild();
		assertEquals(100.0, guild.setDividendPercent(150.0), 1e-9);
		assertEquals(0.0, guild.setDividendPercent(-4.0), 1e-9);
		assertEquals(12.5, guild.setDividendPercent(12.5), 1e-9);
	}

	@Test
	void previousTickMembershipExcludesJoiners() throws Exception {
		Cache.dividendRequirePreviousTickMembership = true;
		Guild guild = emptyGuild();
		guild.getMembers().add("Ann");
		guild.getMembers().add("Bob");
		guild.refreshDividendEligibility();
		guild.getMembers().add("Cara");
		List<String> eligible = guild.getDividendEligibleMembers();
		assertEquals(List.of("Ann", "Bob"), eligible);
		assertFalse(eligible.contains("Cara"));
	}

	@Test
	void tenureToggleIncludesEveryone() throws Exception {
		Cache.dividendRequirePreviousTickMembership = false;
		try {
			Guild guild = emptyGuild();
			guild.getMembers().add("Ann");
			guild.refreshDividendEligibility();
			guild.getMembers().add("Cara");
			assertEquals(List.of("Ann", "Cara"), guild.getDividendEligibleMembers());
		} finally {
			Cache.dividendRequirePreviousTickMembership = true;
		}
	}

	private static Guild emptyGuild() throws Exception {
		Guild guild = mock(Guild.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		set(guild, "members", new ArrayList<String>());
		set(guild, "dividendEligible", new ArrayList<String>());
		set(guild, "dividendPercent", 0.0);
		return guild;
	}

	private static void set(Guild guild, String fieldName, Object value) throws Exception {
		Field field = Guild.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(guild, value);
	}
}
