package net.tfminecraft.simplefactions.war.campaign.runtime;

import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;

/**
 * Volatile in-memory offset on {@link Instant#now()} for campaign schedule dev/QA.
 * <p>
 * Use {@link #now()} for eligibility, windows, overdue checks, GUI, and tick logic.
 * Do <strong>not</strong> use for persistence audit fields such as {@code war.startedAt},
 * {@code endedAt}, or commitment timestamps — those must use real wall-clock time.
 * <p>
 * Offset is lost on server restart (same pattern as {@code WarDevMode}).
 */
public final class CampaignClock {
	private static volatile Duration offset = Duration.ZERO;

	private CampaignClock() {}

	public static Instant now() {
		return Instant.now().plus(offset);
	}

	public static Duration getOffset() {
		return offset;
	}

	public static boolean isSpoofed() {
		return !offset.isZero();
	}

	public static void add(Duration delta) {
		if (delta == null) {
			throw new IllegalArgumentException("Duration must not be null");
		}
		try {
			Duration candidate = offset.plus(delta);
			// Validate the schedule's time zone before publishing an offset used by every tick.
			Instant.now().plus(candidate).atZone(BattleWindowService.SCHEDULE_ZONE);
			offset = candidate;
		} catch (ArithmeticException | DateTimeException error) {
			throw new IllegalArgumentException("Campaign time is outside the supported range", error);
		}
	}

	public static void reset() {
		offset = Duration.ZERO;
	}

	static void resetForTests() {
		reset();
	}
}
