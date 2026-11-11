package net.tfminecraft.simplefactions.war.wounds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class CompanyWoundsTest {
	private static final Instant T0 = Instant.parse("2026-11-11T12:00:00Z");
	private double percent;
	private double hours;
	private int deaths;

	@BeforeEach
	void setUp() {
		percent = Cache.warMercenaryHealPercent;
		hours = Cache.warMercenaryHealHours;
		deaths = Cache.warBattleDeathsPerRegimentLoss;
		Cache.warMercenaryHealPercent = 25;
		Cache.warMercenaryHealHours = 6;
		Cache.warBattleDeathsPerRegimentLoss = 5;
	}

	@AfterEach
	void tearDown() {
		Cache.warMercenaryHealPercent = percent;
		Cache.warMercenaryHealHours = hours;
		Cache.warBattleDeathsPerRegimentLoss = deaths;
	}

	@Test
	void woundsHealAPercentOfTheCompanyEveryPeriod() {
		CompanyWounds wounds = new CompanyWounds();
		wounds.add(20, T0, 4);

		assertEquals(4, wounds.woundedSlots(T0, 4));
		assertEquals(0, wounds.healthySlots(T0, 4));
		// 25% of 4 slots is 1 slot, 5 lives, every 6 hours.
		assertEquals(20, wounds.lives(T0.plus(Duration.ofHours(5)), 4));
		assertEquals(15, wounds.lives(T0.plus(Duration.ofHours(6)), 4));
		assertEquals(5, wounds.lives(T0.plus(Duration.ofHours(19)), 4));
		assertEquals(T0.plus(Duration.ofHours(18)), wounds.healFrom());
		assertEquals(0, wounds.lives(T0.plus(Duration.ofHours(24)), 4));
		assertNull(wounds.healFrom());
		assertEquals(4, wounds.healthySlots(T0.plus(Duration.ofHours(24)), 4));
	}

	@Test
	void woundsStayWithinTheCompanyAndCanBeTakenOff() {
		CompanyWounds wounds = new CompanyWounds();
		wounds.add(100, T0, 3);
		assertEquals(15, wounds.storedLives());
		assertEquals(T0, wounds.healFrom());
		wounds.add(-4, T0.plus(Duration.ofHours(1)), 3);
		assertEquals(11, wounds.storedLives());
		assertEquals(T0, wounds.healFrom(), "healing keeps counting from the first wound");
		wounds.add(-50, T0, 3);
		assertEquals(0, wounds.storedLives());
		assertNull(wounds.healFrom());
		wounds.set(7, T0, 0);
		assertEquals(0, wounds.storedLives());
		assertEquals(0, wounds.woundedSlots(T0, 3));
	}

	@Test
	void freshWoundsRestartTheHealingPeriod() {
		CompanyWounds wounds = new CompanyWounds();
		wounds.add(5, T0, 4);
		wounds.add(10, T0.plus(Duration.ofHours(5)), 4);

		assertEquals(T0.plus(Duration.ofHours(5)), wounds.healFrom());
		// Nothing heals an hour after the new wounds; a full period after them, one slot does.
		assertEquals(15, wounds.lives(T0.plus(Duration.ofHours(6)), 4));
		assertEquals(10, wounds.lives(T0.plus(Duration.ofHours(11)), 4));
	}

	@Test
	void nothingHealsWithoutAClockOrAPercent() {
		CompanyWounds wounds = new CompanyWounds(10, T0);
		assertEquals(10, wounds.lives(null, 4));
		Cache.warMercenaryHealPercent = 0;
		assertEquals(10, wounds.lives(T0.plus(Duration.ofDays(30)), 4));
		Cache.warBattleDeathsPerRegimentLoss = 0;
		assertEquals(2, wounds.woundedSlots(T0, 4), "an unset rate counts 5 lives a slot");
		assertNull(new CompanyWounds(0, T0).healFrom());
		assertEquals(0, new CompanyWounds(-3, null).storedLives());
		CompanyWounds lost = new CompanyWounds(4, null);
		lost.add(1, T0, 4);
		assertEquals(T0, lost.healFrom(), "a wound with no start time starts healing now");
	}

	@Test
	void aTinyCompanyStillHealsALifeAPeriod() {
		Cache.warMercenaryHealPercent = 1;
		CompanyWounds wounds = new CompanyWounds(3, T0);
		assertEquals(1, wounds.lives(T0.plus(Duration.ofHours(12)), 1));
	}
}
