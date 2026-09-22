package me.Plugins.SimpleFactions.War.campaign.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CampaignUiTimeFormatterTest {

	@Test
	void formatUtcHour_convertsToCetAndEst() {
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		assertEquals("20:00 CET / 14:00 EST", CampaignUiTimeFormatter.formatUtcHour(battleDay, 20));
	}

	@Test
	void formatUtcHour_handlesWinterDate() {
		LocalDate winterDay = LocalDate.of(2026, 1, 15);
		assertEquals("16:00 CET / 10:00 EST", CampaignUiTimeFormatter.formatUtcHour(winterDay, 16));
	}

	@Test
	void formatUtcHour_hour24UsesNextDayMidnightInScheduleZone() {
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		assertEquals(
				"00:00 CET / 18:00 EST#6b6b6b (21/08/2026)",
				CampaignUiTimeFormatter.formatUtcHour(battleDay, 24));
	}

	@Test
	void formatInstant_convertsToCetAndEst() {
		Instant instant = Instant.parse("2026-08-21T19:00:00Z");
		assertEquals("21:00 CET / 15:00 EST", CampaignUiTimeFormatter.formatInstant(instant));
	}

	@Test
	void formatInstant_returnsDashWhenNull() {
		assertEquals("-", CampaignUiTimeFormatter.formatInstant(null));
	}
}
