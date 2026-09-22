package me.Plugins.SimpleFactions.War.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class CampaignDurationParserTest {
	@Test
	void spacedAndConcatenatedTokensAreEquivalent() {
		Duration expected = Duration.ofHours(1).plusMinutes(31);

		assertEquals(expected, CampaignDurationParser.parse("1h", "31m"));
		assertEquals(expected, CampaignDurationParser.parse("1h31m"));
	}

	@Test
	void parsesSingleUnitTokens() {
		assertEquals(Duration.ofDays(1), CampaignDurationParser.parse("1d"));
		assertEquals(Duration.ofMinutes(45), CampaignDurationParser.parse("45m"));
		assertEquals(Duration.ofSeconds(90), CampaignDurationParser.parse("90s"));
		assertEquals(Duration.ofHours(4), CampaignDurationParser.parse("4h"));
	}

	@Test
	void rejectsEmptyInput() {
		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> CampaignDurationParser.parse());
		assertTrue(ex.getMessage().contains("Invalid duration"));
	}

	@Test
	void rejectsBlankToken() {
		assertThrows(IllegalArgumentException.class, () -> CampaignDurationParser.parse(""));
		assertThrows(IllegalArgumentException.class, () -> CampaignDurationParser.parse("1h", " "));
	}

	@Test
	void rejectsUnknownUnit() {
		assertThrows(IllegalArgumentException.class, () -> CampaignDurationParser.parse("1x"));
		assertThrows(IllegalArgumentException.class, () -> CampaignDurationParser.parse("1h", "bad"));
	}

	@Test
	void rejectsTrailingJunk() {
		assertThrows(IllegalArgumentException.class, () -> CampaignDurationParser.parse("1h1"));
	}
}
