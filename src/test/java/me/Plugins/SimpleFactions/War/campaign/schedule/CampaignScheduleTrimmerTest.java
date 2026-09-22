package me.Plugins.SimpleFactions.War.campaign.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;

class CampaignScheduleTrimmerTest {

	@BeforeEach
	void setUp() {
		Cache.warGoalMaxBattles = new EnumMap<>(WarGoalType.class);
		Cache.warGoalMaxBattles.put(WarGoalType.SUBJUGATE, 4);
	}

	@Test
	void trim_noOpWhenUnderMax() {
		List<ScheduledCampaignBattle> schedule = List.of(
				field(10, false),
				field(20, false),
				field(30, true));

		List<ScheduledCampaignBattle> trimmed = CampaignScheduleTrimmer.trim(schedule, 4);

		assertEquals(3, trimmed.size());
		assertEquals(schedule, trimmed);
	}

	@Test
	void trim_dropsFieldsBeforeSieges() {
		List<ScheduledCampaignBattle> schedule = List.of(
				field(10, false),
				field(15, false),
				siege(18, "fort_a"),
				siege(22, "fort_b"),
				field(20, false),
				field(30, true));

		List<ScheduledCampaignBattle> trimmed = CampaignScheduleTrimmer.trimInvasion(schedule, 4);

		assertEquals(4, trimmed.size());
		assertTrue(trimmed.stream().anyMatch(slot -> slot.provinceId() == 30 && slot.required()));
		assertEquals(2, trimmed.stream().filter(slot -> slot.kind() == CampaignBattleKind.SIEGE).count());
		assertEquals(1, trimmed.stream().filter(slot -> slot.kind() == CampaignBattleKind.FIELD && !slot.required()).count());
		assertTrue(trimmed.stream().anyMatch(slot -> slot.provinceId() == 10));
		assertTrue(trimmed.stream().noneMatch(slot -> slot.provinceId() == 15));
		assertTrue(trimmed.stream().noneMatch(slot -> slot.provinceId() == 20));
	}

	@Test
	void trim_neverDropsRequiredObjective() {
		List<ScheduledCampaignBattle> schedule = List.of(
				field(10, false),
				field(15, false),
				field(20, false),
				field(30, true));

		List<ScheduledCampaignBattle> trimmed = CampaignScheduleTrimmer.trimInvasion(schedule, 1);

		assertEquals(2, trimmed.size());
		assertEquals(10, trimmed.get(0).provinceId());
		assertEquals(30, trimmed.get(1).provinceId());
		assertTrue(trimmed.get(1).required());
	}

	@Test
	void trim_navalKindsDropBeforeSiege() {
		List<ScheduledCampaignBattle> schedule = List.of(
				field(10, false),
				naval(12, CampaignBattleKind.NAVAL_INVASION),
				naval(14, CampaignBattleKind.NAVAL),
				siege(18, "fort_a"),
				field(30, true));

		List<ScheduledCampaignBattle> trimmed = CampaignScheduleTrimmer.trimInvasion(schedule, 3);

		assertEquals(3, trimmed.size());
		assertTrue(trimmed.stream().anyMatch(slot -> slot.kind() == CampaignBattleKind.SIEGE));
		assertTrue(trimmed.stream().anyMatch(slot -> slot.provinceId() == 30 && slot.required()));
		assertTrue(trimmed.stream().anyMatch(slot -> slot.provinceId() == 10));
		assertTrue(trimmed.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
		assertTrue(trimmed.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL));
	}

	@Test
	void maxBattlesForGoal_readsCache() {
		assertEquals(4, CampaignScheduleTrimmer.maxBattlesForGoal(WarGoalType.SUBJUGATE));
		assertEquals(4, CampaignScheduleTrimmer.maxBattlesForGoal(null));
	}

	@Test
	void maxBattlesPerLegForGoal_readsCache() {
		assertEquals(4, CampaignScheduleTrimmer.maxBattlesPerLegForGoal(WarGoalType.SUBJUGATE));
		assertEquals(4, CampaignScheduleTrimmer.maxBattlesPerLegForGoal(null));
	}

	@Test
	void trim_independentLegs() {
		List<ScheduledCampaignBattle> invasion = List.of(
				field(10, false),
				field(15, false),
				field(20, false),
				field(30, false),
				field(40, true));
		List<ScheduledCampaignBattle> counter = List.of(
				field(8, false),
				field(5, false),
				field(3, false),
				field(1, true));

		List<ScheduledCampaignBattle> invasionTrimmed = CampaignScheduleTrimmer.trimInvasion(invasion, 4);
		List<ScheduledCampaignBattle> counterTrimmed = CampaignScheduleTrimmer.trimCounter(counter, 2);

		assertEquals(4, invasionTrimmed.size());
		assertEquals(2, counterTrimmed.size());
		assertTrue(invasionTrimmed.stream().anyMatch(slot -> slot.provinceId() == 40 && slot.required()));
		assertTrue(counterTrimmed.stream().anyMatch(slot -> slot.provinceId() == 1 && slot.required()));
	}

	@Test
	void trimInvasion_navalPrefix_protectsNavalAndBorder() {
		List<ScheduledCampaignBattle> schedule = List.of(
				naval(795, CampaignBattleKind.NAVAL),
				field(709, false),
				field(672, false),
				siege(713, "Greenfort"),
				field(705, true));

		List<ScheduledCampaignBattle> trimmed = CampaignScheduleTrimmer.trimInvasion(schedule, 3);

		assertEquals(3, trimmed.size());
		assertEquals(CampaignBattleKind.NAVAL, trimmed.get(0).kind());
		assertEquals(709, trimmed.get(1).provinceId());
		assertTrue(trimmed.get(2).required());
	}

	@Test
	void trimInvasion_navalPrefix_cap4_keepsNavalFbSiegeAndObjective() {
		List<ScheduledCampaignBattle> schedule = List.of(
				naval(795, CampaignBattleKind.NAVAL),
				field(709, false),
				field(672, false),
				siege(713, "Greenfort"),
				field(705, true));

		List<ScheduledCampaignBattle> trimmed = CampaignScheduleTrimmer.trimInvasion(schedule, 4);

		assertEquals(4, trimmed.size());
		assertEquals(CampaignBattleKind.NAVAL, trimmed.get(0).kind());
		assertEquals(795, trimmed.get(0).provinceId());
		assertEquals(709, trimmed.get(1).provinceId());
		assertEquals(CampaignBattleKind.FIELD, trimmed.get(1).kind());
		assertTrue(trimmed.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.SIEGE && slot.provinceId() == 713));
		assertTrue(trimmed.stream().anyMatch(slot -> slot.provinceId() == 705 && slot.required()));
		assertTrue(trimmed.stream().noneMatch(slot -> slot.provinceId() == 672));
	}

	@Test
	void trimInvasion_brumeShaped_keepsBorderFieldOverCapitalSiege() {
		List<ScheduledCampaignBattle> schedule = List.of(
				field(709, false),
				siege(713, "Greenfort"),
				field(705, true));

		List<ScheduledCampaignBattle> trimmed = CampaignScheduleTrimmer.trimInvasion(schedule, 2);

		assertEquals(2, trimmed.size());
		assertEquals(709, trimmed.get(0).provinceId());
		assertEquals(CampaignBattleKind.FIELD, trimmed.get(0).kind());
		assertEquals(705, trimmed.get(1).provinceId());
		assertTrue(trimmed.get(1).required());
	}

	private static ScheduledCampaignBattle field(int provinceId, boolean required) {
		return new ScheduledCampaignBattle(provinceId, CampaignBattleKind.FIELD, required, null);
	}

	private static ScheduledCampaignBattle siege(int provinceId, String fortId) {
		return new ScheduledCampaignBattle(provinceId, CampaignBattleKind.SIEGE, false, fortId);
	}

	private static ScheduledCampaignBattle naval(int provinceId, CampaignBattleKind kind) {
		return new ScheduledCampaignBattle(provinceId, kind, false, null);
	}
}
