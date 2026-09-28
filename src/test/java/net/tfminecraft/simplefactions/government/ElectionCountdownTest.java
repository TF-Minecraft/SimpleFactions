package net.tfminecraft.simplefactions.government;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ElectionCountdownTest {

	@Test
	void formatTimeUntil_countsWholeDaysAndHours() {
		Instant now = Instant.parse("2026-09-21T08:15:00Z");
		Instant next = Instant.parse("2026-09-28T00:00:00Z");
		assertEquals("6d 15h", Government.formatTimeUntil(now, next));
	}

	@Test
	void formatTimeUntil_pastTargetIsZero() {
		Instant now = Instant.parse("2026-09-28T08:15:00Z");
		Instant next = Instant.parse("2026-09-28T00:00:00Z");
		assertEquals("0d 0h", Government.formatTimeUntil(now, next));
		assertEquals("0d 0h", Government.formatTimeUntil(now, null));
	}
}
