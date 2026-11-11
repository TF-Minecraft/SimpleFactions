package net.tfminecraft.simplefactions.war.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

class PreparationFreezeTest {
	private static final Instant NOW = Instant.parse("2026-08-21T14:00:00Z");

	private Faction attacker;
	private Faction defender;
	private Faction outsider;
	private final List<War> wars = new ArrayList<>();

	@BeforeEach
	void setUp() {
		attacker = faction("atk");
		defender = faction("def");
		outsider = faction("other");
		PreparationFreeze.setActiveWars(() -> wars);
	}

	@AfterEach
	void tearDown() {
		PreparationFreeze.setActiveWars(null);
		wars.clear();
	}

	@Test
	void aSavedPostponementFreezeStillHoldsBothSidesUntilItEnds() {
		War war = war();

		postpone(war, NOW);

		Instant until = NOW.plus(24, ChronoUnit.HOURS);
		assertEquals(until, war.getPreparationFrozenUntil());
		assertEquals(new PreparationFreeze.Freeze(PreparationFreeze.Reason.POSTPONED, until),
				PreparationFreeze.freeze(attacker, NOW));
		assertEquals(until, PreparationFreeze.freeze(defender, NOW).until());
		assertNull(PreparationFreeze.freeze(outsider, NOW));
	}

	@Test
	void freeze_endsAfterItsWindow() {
		War war = war();
		postpone(war, NOW);

		Instant later = NOW.plus(24, ChronoUnit.HOURS);
		assertNotNull(PreparationFreeze.freeze(attacker, later.minusSeconds(1)));
		assertNull(PreparationFreeze.freeze(attacker, later));
	}

	@Test
	void endedWar_doesNotFreeze() {
		War war = war();
		postpone(war, NOW);
		war.end(net.tfminecraft.simplefactions.war.enums.WarEndReason.WHITE_PEACE);

		assertNull(PreparationFreeze.freeze(attacker, NOW));
	}

	@Test
	void refusal_onlyForFrozenFactions() {
		War war = war();
		postpone(war, NOW);

		String message = PreparationFreeze.refusal(PreparationFreeze.freeze(attacker, NOW), "regiments", NOW);
		assertEquals("A battle of yours was postponed. No new regiments can be started for 24h 0m.", message);
		assertNull(PreparationFreeze.refusal(PreparationFreeze.freeze(outsider, NOW), "regiments", NOW));
	}

	@Test
	void buildup_freezesBothSidesUntilTheFirstBattle() {
		War war = war();
		war.setBuildup(true);

		PreparationFreeze.Freeze freeze = PreparationFreeze.freeze(attacker, NOW);
		assertEquals(new PreparationFreeze.Freeze(PreparationFreeze.Reason.BUILDUP, null), freeze);
		assertEquals(PreparationFreeze.Reason.BUILDUP, PreparationFreeze.freeze(defender, NOW).reason());
		assertNull(PreparationFreeze.freeze(outsider, NOW));
		assertEquals("Your war is in its buildup. No new installations can be started until the first battle.",
				PreparationFreeze.refusal(freeze, "installations", NOW));
		assertEquals("war buildup, until the first battle", PreparationFreeze.reasonText(freeze, NOW));
		assertEquals("§cFrozen: war buildup, until the first battle", PreparationFreeze.frozenLore(freeze));

		war.setFirstBattleStarted(true);
		assertNull(PreparationFreeze.freeze(attacker, NOW));
	}

	@Test
	void buildup_outranksAPostponementOnEitherSide() {
		War postponed = war();
		postpone(postponed, NOW);
		War built = war();
		built.setBuildup(true);

		assertEquals(PreparationFreeze.Reason.BUILDUP, PreparationFreeze.freeze(attacker, NOW).reason());

		wars.clear();
		wars.add(built);
		wars.add(postponed);
		assertEquals(PreparationFreeze.Reason.BUILDUP, PreparationFreeze.freeze(attacker, NOW).reason());
	}

	@Test
	void postponedWarsKeepTheLatestEnd() {
		War first = war();
		postpone(first, NOW);
		War second = war();
		second.setPreparationFrozenUntil(NOW.plus(48, ChronoUnit.HOURS));

		assertEquals(NOW.plus(48, ChronoUnit.HOURS), PreparationFreeze.freeze(attacker, NOW).until());
		second.setPreparationFrozenUntil(NOW.plus(1, ChronoUnit.HOURS));
		assertEquals(NOW.plus(24, ChronoUnit.HOURS), PreparationFreeze.freeze(attacker, NOW).until());
	}

	@Test
	void postponedLoreAndReasonShowTheTimeLeft() {
		PreparationFreeze.Freeze freeze = new PreparationFreeze.Freeze(
				PreparationFreeze.Reason.POSTPONED, NOW.plus(3, ChronoUnit.HOURS));

		assertEquals("battle postponed (3h 0m left)", PreparationFreeze.reasonText(freeze, NOW));
		assertTrue(PreparationFreeze.frozenLore(freeze).startsWith("§cFrozen: battle postponed ("));
		assertNull(PreparationFreeze.frozenLore(null));
	}

	@Test
	void currentClockRefusalsAndChecks() {
		War war = war();
		war.setBuildup(true);

		assertTrue(PreparationFreeze.isFrozen(attacker));
		assertFalse(PreparationFreeze.isFrozen(outsider));
		assertTrue(PreparationFreeze.refusal(attacker, "regiments").contains("No new regiments"));
		assertNull(PreparationFreeze.refusal(outsider, "regiments"));
		assertNull(PreparationFreeze.freeze((Faction) null, NOW));
	}

	private War war() {
		War war = new War(1, attacker, defender);
		wars.add(war);
		return war;
	}

	private static Faction faction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of());
		return faction;
	}

	/** A postponement freeze saved before postponements went: 24 hours from {@code at}. */
	private static void postpone(War war, Instant at) {
		war.setPreparationFrozenUntil(at.plus(24, ChronoUnit.HOURS));
	}
}
