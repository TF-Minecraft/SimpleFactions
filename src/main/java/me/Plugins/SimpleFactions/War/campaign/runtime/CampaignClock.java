package me.Plugins.SimpleFactions.War.campaign.runtime;

import java.time.Duration;
import java.time.Instant;

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
		offset = offset.plus(delta);
	}

	public static void reset() {
		offset = Duration.ZERO;
	}

	static void resetForTests() {
		reset();
	}
}
