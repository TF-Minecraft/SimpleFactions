package me.Plugins.SimpleFactions.War.campaign.ui;



import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignRouteRenderer;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignRouteEntry;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.pathfinder.TitleManagerProvinceOwnerLookup;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiCopy;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

class CampaignRouteRendererTest {
	private Faction attacker;
	private Faction defender;
	private TitleManagerProvinceOwnerLookup owners;

	@BeforeEach
	void setUp() {
		Cache.warFirstBattleAtBorder = true;
		Cache.warProvincesBetweenBattles = 1;
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("AttackerRealm");
		when(defender.getName()).thenReturn("DefenderRealm");
		when(attacker.getCapital()).thenReturn(5);
		when(defender.getCapital()).thenReturn(30);
		owners = new TitleManagerProvinceOwnerLookup();
	}

	@Test
	void resolveMaterial_viewerContextCases() {
		War ownershipWar = baseWar();
		ownershipWar.setCampaignBattlesFought(1);
		ownershipWar.setCursorIndex(2);

		War routeWar = baseWar();
		War choicePendingWar = baseWar();
		choicePendingWar.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		choicePendingWar.setPostBattleChoiceResolved(false);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);
			assertEquals(
					Material.BLUE_CONCRETE,
					CampaignRouteRenderer.resolveOwnershipMaterial(ownershipWar, attacker, 5, owners));
			assertEquals(
					Material.RED_CONCRETE,
					CampaignRouteRenderer.resolveMaterial(routeWar, attacker, 30, owners));
			assertEquals(
					Material.RED_CONCRETE,
					CampaignRouteRenderer.resolveMaterial(routeWar, attacker, 20, owners));
			assertEquals(
					Material.RED_CONCRETE,
					CampaignRouteRenderer.resolveMaterial(choicePendingWar, attacker, 20, owners));
		}
	}

	@Test
	void buildRouteLore_showsFieldBattleOnScheduledProvince() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(20, 2, 0), owners);
			assertTrue(lore.contains(StringFormatter.formatHex(CampaignUiCopy.BATTLE_KIND + "Field Battle")));
			assertTrue(lore.stream().anyMatch(line -> line.contains("Attackers:")));
		}
	}

	@Test
	void buildRouteLore_showsSiegeOnScheduledProvince() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort_a")));
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(18)).thenReturn(defender);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(18, 1, 0), owners);
			assertTrue(lore.contains(StringFormatter.formatHex(CampaignUiCopy.BATTLE_KIND + "Siege")));
		}
	}

	@Test
	void buildRouteLore_showsNavalBattleOnScheduledProvince() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null, "port_a")));
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(20, 2, 0), owners);
			assertTrue(lore.contains(StringFormatter.formatHex(CampaignUiCopy.BATTLE_KIND + "Naval Battle")));
		}
	}

	@Test
	void buildRouteLore_showsNavalInvasionOnScheduledProvince() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(21, CampaignBattleKind.NAVAL_INVASION, false, null)));
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(21)).thenReturn(defender);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(21, 2, 0), owners);
			assertTrue(lore.contains(StringFormatter.formatHex(CampaignUiCopy.BATTLE_KIND + "Naval Invasion")));
		}
	}

	@Test
	void buildRouteLore_omitsBattleKindWithoutSchedule() {
		War war = baseWar();
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(war, 20, owners);
			assertFalse(lore.stream().anyMatch(line ->
					line.contains("Field Battle")
							|| line.contains("Siege")
							|| line.contains("Naval Battle")
							|| line.contains("Naval Invasion")));
		}
	}

	@Test
	void buildRouteLore_includesObjectiveRealmAndNextBattle() {
		War war = baseWar();
		war.setCursorIndex(3);
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(war, 30, owners);
			assertTrue(lore.contains(StringFormatter.formatHex(CampaignUiCopy.OBJECTIVE + "Defender Capital")));
			assertTrue(lore.stream().anyMatch(line -> line.contains("Part of") && line.contains("DefenderRealm")));
			assertFalse(lore.stream().anyMatch(line -> line.contains("Cursor")));
			assertFalse(lore.stream().anyMatch(line -> line.contains("First battle")));
		}
	}

	@Test
	void buildRouteLore_showsVassalOwnerWhenNotLeader() {
		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		when(vassal.getName()).thenReturn("VassalHold");

		War war = baseWar();
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(vassal);
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(vassal);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			List<String> lore = CampaignRouteRenderer.buildRouteLore(war, 10, owners);
			assertTrue(lore.stream().anyMatch(line -> line.contains("(Owned by VassalHold)")));
		}
	}

	@Test
	void buildRouteLore_siegeOmitsDefenderCapitalObjective() {
		War war = baseWar();
		war.setObjectiveProvinceId(705);
		when(defender.getCapital()).thenReturn(705);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "Greenfort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(705)).thenReturn(defender);
			List<String> siegeLore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(705, 2, 0), owners);
			assertFalse(siegeLore.stream().anyMatch(line -> line.contains("Defender Capital")));

			List<String> fieldLore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(705, 2, 1), owners);
			assertTrue(fieldLore.stream().anyMatch(line -> line.contains("Defender Capital")));
		}
	}

	@Test
	void buildRouteEntries_brumeShaped_expandsMultipleBattlesOnSameProvince() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(452, 795, 705));
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "Greenfort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(0);

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(3, entries.size());
		assertEquals(795, entries.get(0).provinceId());
		assertEquals(CampaignBattleKind.NAVAL, war.getCampaignBattleSchedule().get(entries.get(0).scheduleIndex()).kind());
		assertEquals(705, entries.get(1).provinceId());
		assertEquals(CampaignBattleKind.SIEGE, war.getCampaignBattleSchedule().get(entries.get(1).scheduleIndex()).kind());
		assertEquals(705, entries.get(2).provinceId());
		assertEquals(CampaignBattleKind.FIELD, war.getCampaignBattleSchedule().get(entries.get(2).scheduleIndex()).kind());
		entries.forEach(entry -> assertTrue(entry.hasBattleSlot()));
	}

	@Test
	void buildRouteEntries_afterSiege_showsOnlyFieldOnCapital() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(452, 795, 705));
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "Greenfort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(2);

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(3, entries.size());
		assertEquals(795, entries.get(0).provinceId());
		assertEquals(0, entries.get(0).scheduleIndex());
		assertEquals(705, entries.get(2).provinceId());
		assertEquals(2, entries.get(2).scheduleIndex());
		entries.forEach(entry -> assertTrue(entry.hasBattleSlot()));
	}

	@Test
	void buildRouteEntries_offAxisSiegeSortsByChronologyProvince() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(452, 709, 713, 705));
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(709, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(
						704,
						CampaignBattleKind.SIEGE,
						false,
						"Lan_Airfield",
						null,
						713),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(3, entries.size());
		assertEquals(709, entries.get(0).provinceId());
		assertEquals(704, entries.get(1).provinceId());
		assertEquals(2, entries.get(1).axisIndex());
		assertEquals(705, entries.get(2).provinceId());
	}

	@Test
	void buildRouteEntries_includesCounterLegSlots() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null)));

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(4, entries.size());
		assertEquals(5, entries.get(0).provinceId());
		assertEquals(ScheduleLeg.COUNTER, entries.get(0).scheduleLeg());
		assertEquals(1, entries.get(0).scheduleIndex());
		assertEquals(10, entries.get(1).provinceId());
		assertEquals(ScheduleLeg.COUNTER, entries.get(1).scheduleLeg());
		assertEquals(0, entries.get(1).scheduleIndex());
		assertEquals(20, entries.get(2).provinceId());
		assertEquals(ScheduleLeg.INVASION, entries.get(2).scheduleLeg());
		assertEquals(30, entries.get(3).provinceId());
		assertEquals(ScheduleLeg.INVASION, entries.get(3).scheduleLeg());
	}

	@Test
	void buildRouteLore_counterSlot_doesNotShowCounterPushLabel() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null)));
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);

		List<String> lore = CampaignRouteRenderer.buildRouteLore(
				war,
				new CampaignRouteEntry(10, 1, 0, ScheduleLeg.COUNTER),
				owners);
		assertFalse(lore.stream().anyMatch(line -> line.contains("Counter-push schedule")));
		assertTrue(lore.stream().anyMatch(line -> line.contains("Next battle")));
	}

	@Test
	void buildRouteEntries_brumeAxis_geographicOrder() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(452, 782, 758, 757, 672, 709, 713, 705));
		war.setCampaignStartProvinceId(709);
		war.setObjectiveProvinceId(705);
		war.setCursorIndex(5);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(709, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(713, CampaignBattleKind.SIEGE, false, "fort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(782, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(672, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(452, CampaignBattleKind.FIELD, true, null)));

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(6, entries.size());
		assertEquals(452, entries.get(0).provinceId());
		assertEquals(ScheduleLeg.COUNTER, entries.get(0).scheduleLeg());
		assertEquals(782, entries.get(1).provinceId());
		assertEquals(672, entries.get(2).provinceId());
		assertEquals(709, entries.get(3).provinceId());
		assertEquals(ScheduleLeg.INVASION, entries.get(3).scheduleLeg());
		assertEquals(713, entries.get(4).provinceId());
		assertEquals(705, entries.get(5).provinceId());
		assertTrue(CampaignRouteRenderer.isBorderFirstBattleSlot(war, entries.get(3)));
	}

	@Test
	void isBorderFirstBattleSlot_offAxisSiegeAtBorderChronology() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(10, 704, 705));
		war.setCampaignStartProvinceId(704);
		war.setObjectiveProvinceId(705);
		war.setCursorIndex(1);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(713, CampaignBattleKind.SIEGE, false, "Greenfort", null, 704),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(2, entries.size());
		assertEquals(713, entries.get(0).provinceId());
		assertEquals(0, entries.get(0).scheduleIndex());
		assertTrue(CampaignRouteRenderer.isBorderFirstBattleSlot(war, entries.get(0)));
		assertFalse(CampaignRouteRenderer.isBorderFirstBattleSlot(war, entries.get(1)));
	}

	@Test
	void buildRouteEntries_maxEightSlots_sortsGeographically() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
		war.setCursorIndex(5);
		List<ScheduledCampaignBattle> invasion = List.of(
				new ScheduledCampaignBattle(6, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(7, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(8, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(9, CampaignBattleKind.FIELD, true, null));
		List<ScheduledCampaignBattle> counter = List.of(
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(4, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(3, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(2, CampaignBattleKind.FIELD, true, null));
		war.setCampaignBattleSchedule(invasion);
		war.setCampaignCounterSchedule(counter);

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(8, entries.size());
		assertEquals(2, entries.get(0).provinceId());
		assertEquals(3, entries.get(1).provinceId());
		assertEquals(4, entries.get(2).provinceId());
		assertEquals(5, entries.get(3).provinceId());
		assertEquals(6, entries.get(4).provinceId());
		assertEquals(9, entries.get(7).provinceId());
	}

	@Test
	void buildRouteLore_currentScheduleSlot_showsNextBattle() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "fort")));
		war.setCampaignScheduleIndex(0);

		List<String> lore = CampaignRouteRenderer.buildRouteLore(
				war,
				new CampaignRouteEntry(795, 1, 0),
				owners);
		assertTrue(lore.stream().anyMatch(line -> line.contains("Next battle")));
	}

	@Test
	void buildRouteLore_scheduledActiveSlot_showsStartsInCountdown() {
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		Instant now = BattleWindowService.atScheduleHour(battleDay, 18);
		Instant scheduledAt = now.plusSeconds(92 * 60L);

		War war = baseWar();
		war.setBattleDay(battleDay);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(scheduledAt);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignScheduleIndex(0);

		try (MockedStatic<CampaignClock> clock = mockStatic(CampaignClock.class)) {
			clock.when(CampaignClock::now).thenReturn(now);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(
					war,
					new CampaignRouteEntry(20, 2, 0),
					owners);
			assertTrue(lore.stream().anyMatch(line -> line.contains("Starts in")));
		}
	}

	@Test
	void resolveOwnershipMaterial_neutralProvince_usesGrayConcrete() {
		War war = baseWar();
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(null);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertEquals(
					Material.GRAY_CONCRETE,
					CampaignRouteRenderer.resolveOwnershipMaterial(war, attacker, 10, owners));
		}
	}

	@Test
	void resolveOwnershipMaterial_liegeTransit_usesGrayConcrete() {
		List<Faction> saved = new ArrayList<>(FactionManager.factions);
		FactionManager.factions.clear();
		try {
			Faction king = mockIndependentFaction("king");
			Faction dukeA = mockSubjectFaction("dukeA", "king");
			Faction dukeB = mockSubjectFaction("dukeB", "king");
			FactionManager.factions.add(king);
			FactionManager.factions.add(dukeA);
			FactionManager.factions.add(dukeB);
			War war = new War(1, dukeA, dukeB);
			try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
				titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(king);
				assertEquals(
						Material.GRAY_CONCRETE,
						CampaignRouteRenderer.resolveOwnershipMaterial(war, dukeA, 10, owners));
				List<String> lore = CampaignRouteRenderer.buildRouteLore(war, 10, owners);
				assertTrue(lore.stream().noneMatch(line -> line.contains("Part of")));
			}
		} finally {
			FactionManager.factions.clear();
			FactionManager.factions.addAll(saved);
		}
	}

	@Test
	void buildRouteEntries_noAxisFiller_allSlotsHaveScheduleIndex() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null)));

		List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
		assertEquals(2, entries.size());
		entries.forEach(entry -> assertTrue(entry.hasBattleSlot()));
	}

	@Test
	void buildRouteLore_retreatedSlot_showsRetreatedNotFought() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(1);
		war.addConcededScheduleSlot("invasion:0");

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(20, 2, 0), owners);
			assertTrue(lore.stream().anyMatch(line -> line.contains(CampaignUiCopy.RETREATED_LABEL)));
			assertFalse(lore.stream().anyMatch(line -> line.contains(CampaignUiCopy.FOUGHT_LABEL)));
		}
	}

	@Test
	void buildRouteLore_foughtSlot_showsFought() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(1);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);
			List<String> lore = CampaignRouteRenderer.buildRouteLore(
					war, new CampaignRouteEntry(20, 2, 0), owners);
			assertTrue(lore.stream().anyMatch(line -> line.contains("Fought")));
			assertFalse(lore.stream().anyMatch(line -> line.contains("Next battle")));
		}
	}

	@Test
	void resolveRouteEntryMaterial_brumeShapedSchedule_greensNavalBeforeCapital() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(452, 795, 705));
		war.setCampaignStartProvinceId(705);
		war.setObjectiveProvinceId(705);
		war.setCursorIndex(2);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "fort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(0);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(452)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(795)).thenReturn(null);
			titleManager.when(() -> TitleManager.getByProvince(705)).thenReturn(defender);

			ScheduledCampaignBattle naval = war.getCampaignBattleSchedule().get(0);
			assertEquals(
					Material.TRIDENT,
					CampaignRouteRenderer.resolveRouteEntryMaterial(
							war,
							attacker,
							new CampaignRouteEntry(795, 1, 0),
							naval,
							owners));
			ScheduledCampaignBattle siege = war.getCampaignBattleSchedule().get(1);
			assertEquals(
					Material.RED_CONCRETE,
					CampaignRouteRenderer.resolveRouteEntryMaterial(
							war,
							attacker,
							new CampaignRouteEntry(705, 2, 1),
							siege,
							owners));
		}
	}

	@Test
	void resolveMaterial_brumeShapedSchedule_usesOwnershipOnly() {
		War war = baseWar();
		war.setCampaignProvinces(List.of(452, 795, 705));
		war.setCampaignStartProvinceId(705);
		war.setObjectiveProvinceId(705);
		war.setCursorIndex(2);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "fort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(0);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(452)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(795)).thenReturn(null);
			titleManager.when(() -> TitleManager.getByProvince(705)).thenReturn(defender);

			assertEquals(
					Material.GRAY_CONCRETE,
					CampaignRouteRenderer.resolveMaterial(war, attacker, 795, owners));
			assertEquals(
					Material.RED_CONCRETE,
					CampaignRouteRenderer.resolveMaterial(war, attacker, 705, owners));
		}
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolder(BelligerentRole.ATTACKER);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setCampaignBattlesFought(0);
		return war;
	}

	private void stubOwnership(MockedStatic<TitleManager> titleManager) {
		titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);
	}

	private static Faction mockIndependentFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getMembers()).thenReturn(new ArrayList<>());
		return faction;
	}

	private static Faction mockSubjectFaction(String id, String overlordId) {
		Faction faction = mockIndependentFaction(id);
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.isOverlord()).thenReturn(true);
		when(type.getId()).thenReturn("subject");
		when(relation.getType()).thenReturn(type);
		HashMap<String, Relation> relations = new HashMap<>();
		relations.put(overlordId, relation);
		when(faction.getRelations()).thenReturn(relations);
		when(faction.getRelation(overlordId)).thenReturn(relation);
		return faction;
	}
}
