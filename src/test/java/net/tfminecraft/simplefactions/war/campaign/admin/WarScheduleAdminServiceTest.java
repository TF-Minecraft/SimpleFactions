package net.tfminecraft.simplefactions.war.campaign.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleOutcomeService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;

class WarScheduleAdminServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		Cache.battleCampaignTemplateField = "";
		Cache.warBattleWindowStartHour = 20;
		Cache.warBattleWindowEndHour = 24;
		Cache.warVoteCloseHour = 16;
		Cache.warDefenderChoiceDeadlineHour = 12;
		Cache.warFirstBattleAtBorder = true;
		Cache.warBattleLivesPerRegiment = 4;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of("Alice", "Bob"));
		when(defender.getMembers()).thenReturn(List.of("Carol", "Dave"));
		mockMilitary(attacker);
		mockMilitary(defender);
	}

	@Test
	void skipDay_dropsTheNextBattleDayAndWhatBelongedToIt() {
		War war = votingWar();
		war.getAvailability().putBattleDay(LockedBattleDay.open(BATTLE_DAY.plusDays(2), false));
		war.setBattleInstallationPicks(Map.of("home", new LinkedHashSet<>(List.of("harbor"))));
		war.setBattleInstallationPicksBattleDay(war.getBattleDay());
		war.getCampaignRaidsUsed().put(CampaignSideRole.AGGRESSOR.toJson(), BATTLE_DAY.toString());
		WarScheduleAdminResult result = WarScheduleAdminService.skipDay(war);
		assertTrue(result.success());
		assertEquals("Skipped battle day " + BATTLE_DAY + ". Next battle day: " + BATTLE_DAY.plusDays(2) + ". "
				+ WarScheduleAdminService.NEW_DAY_CLEARED, result.message());
		// The message is true: commitments and the day's raid allowance are reset.
		assertTrue(war.getBattleInstallationPicks().isEmpty());
		assertTrue(war.getCampaignRaidsUsed().isEmpty());
		assertEquals(BATTLE_DAY.plusDays(2), war.getBattleDay());
		assertTrue(WarScheduleAdminService.skipDay(war).message().contains("Next battle day: none yet."));
		assertNull(war.getBattleDay());
		assertEquals("War has no battle day to skip.", WarScheduleAdminService.skipDay(war).message());
	}

	@Test
	void skipDay_callsOffAScheduledBattleButNotARunningOne() {
		War war = scheduledWar();
		Battle running = mock(Battle.class);
		when(running.hasStarted()).thenReturn(true);
		try (MockedStatic<BattleManager> battles = mockStatic(BattleManager.class)) {
			battles.when(() -> BattleManager.getByWarId(war.getId())).thenReturn(running);
			assertEquals("Cannot skip the battle day while its battle is running.",
					WarScheduleAdminService.skipDay(war).message());
		}
		assertTrue(WarScheduleAdminService.skipDay(war).success());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertNull(war.getScheduledBattleAt());
		assertNull(war.getBattleDay());
	}

	@Test
	void lockDayAndUnlockDay_turnADateIntoABattleDayAndBack() {
		War war = votingWar();
		Instant now = Instant.parse("2026-08-21T10:00:00Z");
		WarScheduleAdminResult locked = WarScheduleAdminService.lockDay(war, "2026-08-24", now);
		assertTrue(locked.success(), locked.message());
		assertEquals("Battle day 2026-08-24 locked. Its start time is decided 2026-08-23T18:00:00Z unless you set"
				+ " it with sethour.", locked.message());
		assertNotNull(war.getAvailability().battleDay(LocalDate.of(2026, 8, 24)));
		assertEquals("That day is already a battle day.", WarScheduleAdminService.lockDay(war, "2026-08-24", now).message());
		assertEquals("That day is in the past.", WarScheduleAdminService.lockDay(war, "2026-08-20", now).message());
		assertEquals(WarScheduleAdminService.DAY_FORMAT, WarScheduleAdminService.lockDay(war, "24/08", now).message());
		assertTrue(WarScheduleAdminService.unlockDay(war, "2026-08-24").success());
		assertNull(war.getAvailability().battleDay(LocalDate.of(2026, 8, 24)));
		assertEquals("That day is not a battle day.", WarScheduleAdminService.unlockDay(war, "2026-08-24").message());
		assertEquals(WarScheduleAdminService.DAY_FORMAT, WarScheduleAdminService.unlockDay(war, null).message());
		// Unlocking the next battle day calls its battle off.
		War scheduled = scheduledWar();
		assertTrue(WarScheduleAdminService.unlockDay(scheduled, BATTLE_DAY.toString()).success());
		assertEquals(BattleSchedulePhase.VOTING, scheduled.getBattleSchedulePhase());
		assertNull(scheduled.getBattleDay());
		// A day whose battle window already closed cannot become a battle day.
		Cache.warBattleWindowEndHour = 23;
		Instant late = Instant.parse("2026-08-21T21:30:00Z");
		War unlocked = votingWar();
		unlocked.getAvailability().clearBattleDays();
		assertEquals("That day's battle window has closed.",
				WarScheduleAdminService.lockDay(unlocked, "2026-08-21", late).message());
	}

	@Test
	void lockDayAndUnlockDay_refuseABattleBeingFoughtOrAutoresolved() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
		assertEquals("That day's battle is being autoresolved.",
				WarScheduleAdminService.unlockDay(war, BATTLE_DAY.toString()).message());
		Battle running = mock(Battle.class);
		when(running.hasStarted()).thenReturn(true);
		try (MockedStatic<BattleManager> battles = mockStatic(BattleManager.class)) {
			battles.when(() -> BattleManager.getByWarId(war.getId())).thenReturn(running);
			assertEquals("That day's battle is being fought.",
					WarScheduleAdminService.unlockDay(war, BATTLE_DAY.toString()).message());
			assertEquals("That day's battle is being fought.",
					WarScheduleAdminService.setHour(war, 21, null, Instant.parse("2026-08-21T10:00:00Z")).message());
		}
		assertNotNull(war.getAvailability().battleDay(BATTLE_DAY));
	}

	@Test
	void staffToolsRejectMissingEndedAndUnscheduledWars() {
		Instant now = Instant.parse("2026-08-21T10:00:00Z");
		assertEquals("War not found.", WarScheduleAdminService.lockDay(null, "2026-08-24", now).message());
		assertEquals("War not found.", WarScheduleAdminService.setHour(null, 21, null, now).message());
		War ended = votingWar();
		ended.end(WarEndReason.ADMIN_END);
		assertEquals("War is not active.", WarScheduleAdminService.unlockDay(ended, "2026-08-24").message());
		assertEquals("War is not active.", WarScheduleAdminService.castVote(ended, "2026-08-24", 21, "both").message());
		War idle = votingWar();
		idle.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertEquals("War has no battle schedule.", WarScheduleAdminService.availability(idle, now).message());
		War dayless = votingWar();
		dayless.getAvailability().clearBattleDays();
		dayless.setBattleDay(null);
		assertEquals("War has no battle day. Use lockday first.",
				WarScheduleAdminService.setHour(dayless, 21, null, now).message());
		assertEquals(WarScheduleAdminService.DAY_FORMAT,
				WarScheduleAdminService.setHour(dayless, 21, "tomorrow", now).message());
		assertEquals("That day is not a battle day.",
				WarScheduleAdminService.setHour(dayless, 21, "2026-08-24", now).message());
	}

	@Test
	void castVote_marksTheHourForEveryMemberOfTheSides() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.castVote(war, "2026-08-24", 21, "both");
		assertTrue(result.success());
		assertEquals("Marked 2026-08-24 21:00 CET for 4 test accounts.", result.message());
		assertEquals(4, war.getAvailability().marks().size());
		assertFalse(WarScheduleAdminService.castVote(war, "2026-08-24", 15, "both").success());
	}

	@Test
	void castVote_lateWindow_mapsOneAmToHour25() {
		Cache.warBattleWindowEndHour = 26;
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.castVote(war, "2026-08-24", 1, "both");
		assertTrue(result.success());
		assertTrue(war.getAvailability().marks().values().stream()
				.allMatch(dates -> dates.get(LocalDate.of(2026, 8, 24)).equals(Set.of(25))));
	}

	@Test
	void availability_listsBattleDaysAndMarksPerDate() {
		War war = votingWar();
		war.getAvailability().putBattleDay(LockedBattleDay.open(BATTLE_DAY.plusDays(1), true).withHour(21, true));
		WarScheduleAdminService.castVote(war, "2026-08-23", 21, "attacker");
		WarScheduleAdminResult result = WarScheduleAdminService.availability(war, Instant.parse("2026-08-21T10:00:00Z"));
		assertTrue(result.success());
		assertTrue(result.message().contains("Battle day 2026-08-21§7: time open"), result.message());
		assertTrue(result.message().contains("Battle day 2026-08-22§7: 21:00 CET, forced, autoresolved"), result.message());
		assertTrue(result.message().contains("2026-08-23 §7open: 20:00 0/0 21:00 2/0 22:00 0/0 23:00 0/0"),
				result.message());
		assertTrue(result.message().contains("Next lock:"), result.message());
	}

	@Test
	void setHour_lateWindow_schedulesOneAmOnNextDate() {
		Cache.warBattleWindowEndHour = 26;
		War war = votingWar();
		withMockBossBar(() -> {
			WarScheduleAdminResult result =
					WarScheduleAdminService.setHour(war, 1, null, Instant.parse("2026-08-21T14:00:00Z"));
			assertTrue(result.success(), result.message());
			// 16:00 CEST: the picks have locked, so the battle is set at once.
			assertEquals("Battle day 2026-08-21 starts at 01:00 CET (province 20).", result.message());
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertEquals(Instant.parse("2026-08-21T23:00:00Z"), war.getScheduledBattleAt());
			assertEquals(BATTLE_DAY, war.getBattleDay());
		});
	}

	@Test
	void setHour_ofALaterBattleDayOnlyFixesItsTime() {
		War war = votingWar();
		LocalDate later = BATTLE_DAY.plusDays(1);
		war.getAvailability().putBattleDay(LockedBattleDay.open(later, false));
		WarScheduleAdminResult result =
				WarScheduleAdminService.setHour(war, 22, later.toString(), Instant.parse("2026-08-21T10:00:00Z"));
		assertEquals("Battle day 2026-08-22 starts at 22:00 CET. It is set on its province from 16:00 CET that day.",
				result.message());
		assertEquals(22, war.getAvailability().battleDay(later).hour());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
	}

	@Test
	void setHour_rejectsHourAlreadyPassedOrOutsideTheWindow() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.setHour(war, 21, null, Instant.parse("2026-08-21T20:00:00Z"));
		assertEquals("That time has already passed.", result.message());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertFalse(WarScheduleAdminService.setHour(war, 1, null, Instant.parse("2026-08-21T10:00:00Z")).success());
	}

	@Test
	void setScheduled_appliesInstantAndProvince() {
		War war = votingWar();
		withMockBossBar(() -> {
			WarScheduleAdminResult result = WarScheduleAdminService.setScheduled(
					war,
					"2026-08-21T19:00:00Z");
			assertTrue(result.success());
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertEquals(
					BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21),
					war.getScheduledBattleAt());
			assertEquals(Integer.valueOf(20), war.getScheduledBattleProvinceId());
		});
	}

	@Test
	void setScheduled_rejectsInvalidIso() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.setScheduled(war, "not-an-instant");
		assertFalse(result.success());
	}

	@Test
	void battleCreate_rejectsWhenNotSingleGreenProvince() {
		War war = votingWar();
		war.setInitiativeAttacker(0);
		WarScheduleAdminResult result = WarScheduleAdminService.battleCreate(war);
		assertFalse(result.success());
		assertTrue(result.message().contains("single next battle province"));
	}

	@Test
	void battleCreate_createsCampaignBattleFromGreenProvince() {
		War war = votingWar();
		withMockBossBar(() -> {
			WarScheduleAdminResult result = WarScheduleAdminService.battleCreate(war);
			assertTrue(result.success());
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertNotNull(BattleManager.getByWarId(war.getId()));
		});
	}

	@Test
	void battleCreate_seedsPhantomsWhenDevmodeOn() {
		net.tfminecraft.simplefactions.war.core.WarDevMode.setEnabled(true);
		Cache.warDevmodePhantomCount = 10;
		War war = votingWar();
		try (MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattleForces> pool =
				mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattleForces.class)) {
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattleForces.build(
					any(), any(), any(), any(), any())).thenReturn(net.tfminecraft.simplefactions.war.wounds.TestForces.regiments(5));
			withMockBossBar(() -> {
				assertTrue(WarScheduleAdminService.battleCreate(war).success());
				Battle battle = BattleManager.getByWarId(war.getId());
				net.tfminecraft.simplefactions.war.battle.warband.Warband attackerBand =
						battle.getSideById(
								net.tfminecraft.simplefactions.war.battle.template.BattleTemplate.ATTACKER_SIDE).getBands().getFirst();
				net.tfminecraft.simplefactions.war.battle.warband.Warband defenderBand =
						battle.getSideById(
								net.tfminecraft.simplefactions.war.battle.template.BattleTemplate.DEFENDER_SIDE).getBands().getFirst();
				assertNotNull(attackerBand);
				assertNotNull(defenderBand);
				assertTrue(attackerBand.getDummyMemberCount() > 0);
				assertTrue(defenderBand.getDummyMemberCount() > 0);
				assertEquals(
						net.tfminecraft.simplefactions.war.core.WarDevMode.dummyDisplayName(
								attackerBand.getId(), 0),
						attackerBand.getLeaderDisplayName());
			});
		}
		net.tfminecraft.simplefactions.war.core.WarDevMode.resetForTests();
	}

	@Test
	void battleDelete_resetsCampaignBattleAndWarbands() {
		War war = votingWar();
		withMockBossBar(() -> {
			assertTrue(WarScheduleAdminService.battleCreate(war).success());
			Battle battle = BattleManager.getByWarId(war.getId());
			String attackerWarbandId = battle.getSideById(
					net.tfminecraft.simplefactions.war.battle.template.BattleTemplate.ATTACKER_SIDE)
					.getBands().getFirst().getId();
			assertNotNull(WarbandManager.getByString(attackerWarbandId));

			WarScheduleAdminResult result = WarScheduleAdminService.battleDelete(war);
			assertTrue(result.success());
			assertTrue(result.message().contains("Reset campaign battle"));
			battle = BattleManager.getByWarId(war.getId());
			assertNotNull(battle);
			assertNotNull(WarbandManager.getByString(attackerWarbandId));
			assertEquals(1, battle.getSideById(
					net.tfminecraft.simplefactions.war.battle.template.BattleTemplate.ATTACKER_SIDE).getBands().size());
			assertEquals(1, battle.getSideById(
					net.tfminecraft.simplefactions.war.battle.template.BattleTemplate.DEFENDER_SIDE).getBands().size());
		});
	}

	@Test
	void devModeReminderLines_disabledWhenDevmodeOff() {
		net.tfminecraft.simplefactions.war.core.WarDevMode.resetForTests();
		assertEquals(1, WarScheduleAdminService.devModeReminderLines().size());
		assertTrue(WarScheduleAdminService.devModeReminderLines().get(0).contains("disabled"));
	}

	@Test
	void devModeReminderLines_emptyWhenDevmodeOn() {
		net.tfminecraft.simplefactions.war.core.WarDevMode.setEnabled(true);
		assertTrue(WarScheduleAdminService.devModeReminderLines().isEmpty());
		net.tfminecraft.simplefactions.war.core.WarDevMode.resetForTests();
	}

	@Test
	void battleDelete_rejectsStartedBattle() {
		War war = votingWar();
		try (MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattleForces> pool =
				mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattleForces.class)) {
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattleForces.build(
					any(), any(), any(), any(), any())).thenReturn(net.tfminecraft.simplefactions.war.wounds.TestForces.regiments(5));
			withMockBossBar(() -> {
				assertTrue(WarScheduleAdminService.battleCreate(war).success());
				placeSpawnsAndJails(BattleManager.getByWarId(war.getId()));
				assertTrue(WarScheduleAdminService.battleStart(war).success());
				WarScheduleAdminResult result = WarScheduleAdminService.battleDelete(war);
				assertFalse(result.success());
				assertTrue(result.message().contains("running"));
			});
		}
	}

	@Test
	void battleStart_startsExistingCampaignBattle() {
		War war = votingWar();
		try (MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattleForces> pool =
				mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattleForces.class)) {
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattleForces.build(
					any(), any(), any(), any(), any())).thenReturn(net.tfminecraft.simplefactions.war.wounds.TestForces.regiments(5));
			withMockBossBar(() -> {
				assertTrue(WarScheduleAdminService.battleCreate(war).success());
				placeSpawnsAndJails(BattleManager.getByWarId(war.getId()));
				WarScheduleAdminResult result = WarScheduleAdminService.battleStart(war);
				assertTrue(result.success());
				assertTrue(BattleManager.getByWarId(war.getId()).hasStarted());
			});
		}
	}

	@Test
	void winBattle_reportsActualEndReasonWhenWarEnds() {
		War war = votingWar();
		try (MockedStatic<CampaignBattleOutcomeService> outcome =
				mockStatic(CampaignBattleOutcomeService.class)) {
			outcome.when(() -> CampaignBattleOutcomeService.applyCampaignBattleOutcome(
					eq(war), eq(BelligerentRole.ATTACKER), any(Integer.class)))
					.thenReturn(new CampaignBattleOutcomeService.CampaignBattleApplyResult(
							true, false, Optional.of(WarEndReason.ATTACKER_VICTORY)));
			outcome.when(() -> CampaignBattleOutcomeService.finalizeCampaignBattleAfterOutcome(war))
					.then(inv -> null);

			WarScheduleAdminResult result = WarScheduleAdminService.winBattle(war, BelligerentRole.ATTACKER);
			assertTrue(result.success());
			assertTrue(result.message().contains("attacker victory"));
			assertFalse(result.message().toLowerCase().contains("white peace"));
		}
	}

	@Test
	void winBattle_blockedWhilePostBattleChoicePending() {
		War war = votingWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleWinnerCoalition(CampaignSideRole.DEFENDER);
		war.setPostBattleChoiceResolved(false);

		WarScheduleAdminResult result = WarScheduleAdminService.winBattle(war, BelligerentRole.DEFENDER);
		assertFalse(result.success());
		assertTrue(result.message().contains("Post-battle choice pending"));
	}

	@Test
	void battleChoice_hold_proposesPeaceAndKeepsWinnerInitiative() {
		War war = votingWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleWinnerCoalition(CampaignSideRole.DEFENDER);
		war.setPostBattleChoiceResolved(false);
		try (MockedStatic<net.tfminecraft.simplefactions.managers.WarManager> warManager =
				mockStatic(net.tfminecraft.simplefactions.managers.WarManager.class)) {
			warManager.when(() -> net.tfminecraft.simplefactions.managers.WarManager.persist(any())).then(inv -> null);
			warManager.when(() -> net.tfminecraft.simplefactions.managers.WarManager.getById(war.getId())).thenReturn(war);
			warManager.when(() -> net.tfminecraft.simplefactions.managers.WarManager.endWar(any(), any())).then(inv -> null);

			WarScheduleAdminResult result = WarScheduleAdminService.battleChoice(war, "hold");
			assertTrue(result.success());
			assertEquals(BelligerentRole.DEFENDER, war.getInitiativeHolder());
			assertTrue(CampaignPostBattleChoiceService.needsLoserResponse(war));
		}
	}

	private War votingWar() {
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
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.getAvailability().putBattleDay(LockedBattleDay.open(BATTLE_DAY, false));
		return war;
	}

	private War scheduledWar() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21));
		war.setScheduledBattleHour(21);
		war.setScheduledBattleProvinceId(20);
		return war;
	}

	private void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(levy.getEntries()).thenReturn(List.of());
		when(faction.getMilitary()).thenReturn(military);
		when(faction.getLeader()).thenReturn("leader");
		when(faction.getName()).thenReturn("faction");
	}

	private static void placeSpawnsAndJails(Battle battle) {
		org.bukkit.World world = mock(org.bukkit.World.class);
		for (net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide side : battle.getSides()) {
			side.setSpawn(new org.bukkit.Location(world, 0, 64, 0));
			side.setJail(new org.bukkit.Location(world, 4, 64, 4));
		}
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			action.run();
		}
	}

	@Test
	void appliedChoiceMessagesDescribeEachAcceptedAdministrativeAction() {
		assertTrue(WarScheduleAdminService.appliedChoiceMessage("push").message().contains("Winner pushes"));
		assertTrue(WarScheduleAdminService.appliedChoiceMessage("hold").message().contains("Winner holds"));
		assertTrue(WarScheduleAdminService.appliedChoiceMessage("attack").message().contains("Loser attacks"));
		for (String choice : java.util.List.of("accept", "peace", "acceptpeace", "accept-peace")) {
			var result = WarScheduleAdminService.appliedChoiceMessage(choice);
			assertTrue(result.success());
			assertEquals("Loser accepted white peace.", result.message());
		}
	}

}
