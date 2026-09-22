package me.Plugins.SimpleFactions.War.campaign.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleTickService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class CampaignTimeCommandServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	@BeforeEach
	void setUp() {
		CampaignClock.reset();
		BattleScheduleTickService.onClockOffsetChanged();
		WarManager.get().clear();
	}

	@AfterEach
	void tearDown() {
		CampaignClock.reset();
		BattleScheduleTickService.onClockOffsetChanged();
		WarManager.get().clear();
	}

	@Test
	void add_parsesAndAdvancesOffset() {
		CampaignTimeResult result = CampaignTimeCommandService.add("1h");

		assertTrue(result.success());
		assertTrue(CampaignClock.isSpoofed());
		assertEquals(Duration.ofHours(1), CampaignClock.getOffset());
	}

	@Test
	void add_invalidDuration_returnsError() {
		CampaignTimeResult result = CampaignTimeCommandService.add("bad");

		assertFalse(result.success());
		assertTrue(result.message().contains("Invalid duration"));
	}

	@Test
	void reset_clearsOffset() {
		CampaignClock.add(Duration.ofHours(2));

		CampaignTimeResult result = CampaignTimeCommandService.reset();

		assertTrue(result.success());
		assertFalse(CampaignClock.isSpoofed());
		assertEquals(Duration.ZERO, CampaignClock.getOffset());
	}

	@Test
	void skipToBattleDay_setsParisMidnightOnWarBattleDay() {
		War war = warWithBattleDay();

		CampaignTimeResult result = CampaignTimeCommandService.skipToBattleDay(war);

		assertTrue(result.success());
		assertEquals(BATTLE_DAY, BattleScheduleService.battleDayDate(CampaignClock.now()));
		assertEquals(0, BattleScheduleService.battleDayHour(CampaignClock.now()));
	}

	@Test
	void skipToBattleDay_unknownWar() {
		CampaignTimeResult result = CampaignTimeCommandService.skipToBattleDay(null);

		assertFalse(result.success());
		assertEquals("§cUnknown war id.", result.message());
	}

	@Test
	void statusLines_includesParisHour() {
		CampaignClock.add(Duration.ofHours(1));

		boolean hasParisHour = CampaignTimeCommandService.statusLines().stream()
				.anyMatch(line -> line.contains("Paris battle-day hour"));

		assertTrue(hasParisHour);
	}

	private War warWithBattleDay() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(java.util.List.of());
		when(defender.getMembers()).thenReturn(java.util.List.of());
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		WarManager.get().add(war);
		return war;
	}
}
