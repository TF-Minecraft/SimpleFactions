package net.tfminecraft.simplefactions.inactivity;

/**
 * One guild or faction decay timer. The first step waits a full interval after
 * the group becomes inactive. Each later step adds {@link InactivityRules#DECAY_STEP_PERCENT}
 * and starts the interval again. Logging in clears the clock.
 */
public final class DecayClock {
	public enum Event {
		NONE,
		ARMED,
		STEPPED,
		CLEARED
	}

	private int percent;
	private long nextAt;

	public DecayClock() {}

	public DecayClock(int percent, long nextAt) {
		this.percent = InactivityRules.clampPercent(percent);
		this.nextAt = Math.max(0L, nextAt);
	}

	public int getPercent() {
		return percent;
	}

	public long getNextAt() {
		return nextAt;
	}

	public boolean isSet() {
		return percent != 0 || nextAt != 0;
	}

	public void clear() {
		percent = 0;
		nextAt = 0;
	}

	/** Starts the wait if this clock is not already running. Does not apply a step. */
	public boolean arm(long now) {
		if (nextAt != 0) return false;
		nextAt = now + InactivityRules.DECAY_INTERVAL_MILLIS;
		return true;
	}

	public Event tick(long now, boolean fullyInactive) {
		if (!fullyInactive) {
			if (!isSet()) return Event.NONE;
			clear();
			return Event.CLEARED;
		}
		if (nextAt == 0) {
			arm(now);
			return Event.ARMED;
		}
		if (now < nextAt) return Event.NONE;
		if (percent < InactivityRules.DECAY_CAP_PERCENT) {
			percent = InactivityRules.clampPercent(percent + InactivityRules.DECAY_STEP_PERCENT);
		}
		// One step per wake-up. Time the server spent offline does not pile on.
		nextAt = now + InactivityRules.DECAY_INTERVAL_MILLIS;
		return Event.STEPPED;
	}
}
