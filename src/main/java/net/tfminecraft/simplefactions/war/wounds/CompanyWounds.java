package net.tfminecraft.simplefactions.war.wounds;

import java.time.Duration;
import java.time.Instant;

import net.tfminecraft.simplefactions.Cache;

/**
 * A mercenary company's wounds, shared by all its contracts. They are counted in lives and heal
 * by {@code mercenary_heal_percent} of the company's slots every {@code mercenary_heal_hours},
 * counted from its latest new wound and worked out when read, so nothing needs a timer. A company never
 * loses slots to wounds.
 */
public final class CompanyWounds {
	private int lives;
	private Instant healFrom;

	public CompanyWounds() {
	}

	public CompanyWounds(int lives, Instant healFrom) {
		this.lives = Math.max(0, lives);
		this.healFrom = this.lives > 0 ? healFrom : null;
	}

	/** Wounded lives now, after the healing due since the last read. */
	public int lives(Instant now, int slots) {
		heal(now, slots);
		return lives;
	}

	/** Slots too wounded to fight, never more than the company has. */
	public int woundedSlots(Instant now, int slots) {
		return Math.min(Math.max(0, slots), lives(now, slots) / deathsPerSlot());
	}

	public int healthySlots(Instant now, int slots) {
		return Math.max(0, slots - woundedSlots(now, slots));
	}

	/** Adds (or with a negative amount removes) wounded lives, kept between 0 and the company's size. */
	public void add(int delta, Instant now, int slots) {
		heal(now, slots);
		set(lives + (long) delta, now, slots);
	}

	public void set(long newLives, Instant now, int slots) {
		long max = (long) Math.max(0, slots) * deathsPerSlot();
		int before = lives;
		lives = (int) Math.max(0, Math.min(max, newLives));
		if (lives == 0) {
			healFrom = null;
		} else if (lives > before || healFrom == null) {
			// Fresh wounds restart the healing period, so they cannot heal the moment they are taken.
			healFrom = now;
		}
	}

	/** When healing started counting, for saving; null while unwounded. */
	public Instant healFrom() {
		return healFrom;
	}

	/** Lives as last stored, without healing; for saving. */
	public int storedLives() {
		return lives;
	}

	private void heal(Instant now, int slots) {
		if (lives <= 0 || healFrom == null || now == null || Cache.warMercenaryHealPercent <= 0) {
			return;
		}
		long periodMillis = Math.max(1L, Math.round(Cache.warMercenaryHealHours * 3_600_000d));
		long periods = Duration.between(healFrom, now).toMillis() / periodMillis;
		if (periods <= 0) {
			return;
		}
		long perPeriod = Math.max(1L, (long) Math.ceil(
				Math.max(0, slots) * deathsPerSlot() * Cache.warMercenaryHealPercent / 100d));
		long healed = Math.min(lives, periods * perPeriod);
		lives -= (int) healed;
		healFrom = lives == 0 ? null : healFrom.plusMillis(periods * periodMillis);
	}

	private static int deathsPerSlot() {
		return Cache.warBattleDeathsPerRegimentLoss > 0 ? Cache.warBattleDeathsPerRegimentLoss : 5;
	}
}
