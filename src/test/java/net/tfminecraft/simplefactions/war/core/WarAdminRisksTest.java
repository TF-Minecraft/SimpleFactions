package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;

class WarAdminRisksTest {
	private MockedStatic<WarManager> wars;

	@BeforeEach
	void setUp() {
		CampaignClock.reset();
		wars = mockStatic(WarManager.class);
		wars.when(WarManager::getActive).thenReturn(List.of(mock(War.class)));
		wars.when(() -> WarManager.getById(1)).thenReturn(mock(War.class));
	}

	@AfterEach
	void tearDown() {
		wars.close();
		CampaignClock.reset();
	}

	private static String risk(String... args) {
		return WarAdminRisks.describe(args);
	}

	@Test
	void shortOrSafeCommandsRunWithoutAsking() {
		assertNull(WarAdminRisks.describe(null));
		assertNull(risk("admin", "end"));
		assertNull(risk("admin", "status", "1"));
		assertNull(risk("admin", "win", "1"));
		assertNull(risk("admin", "devmode", "status"));
		assertNull(risk("admin", "time", "status"));
		assertNull(risk("admin", "time", "remove", "1h"));
		assertNull(risk("admin", "time", "reset"));
		assertNull(risk("admin", "schedule", "1"));
		assertNull(risk("admin", "schedule", "1", "sethour", "20"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"setday", "skipday", "closevote", "forcequorum", "battledelete", "battlestart",
			"winbattle", "choice", "battlechoice", "defenderchoice", "pushchoice", "holdchoice"})
	void irreversibleScheduleCommandsAsk(String action) {
		assertNotNull(risk("admin", "schedule", "1", action.toUpperCase()));
	}

	@Test
	void battleToolsAskOnlyForARunningBattleThatTheyCanAffect() {
		net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.resetForTests();
		try {
			assertNull(risk("admin", "schedule", "1", "battlerestart"));
			var battle = new net.tfminecraft.simplefactions.war.battle.engine.core.Battle("campaign_w1");
			battle.setWarId(1);
			battle.setBattleType(net.tfminecraft.simplefactions.war.battle.enums.BattleType.FIELD);
			net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.addBattle(battle);
			assertNull(risk("admin", "schedule", "1", "battleabort"));
			battle.setStarted(true);
			assertNotNull(risk("admin", "schedule", "1", "battlerestart"));
			assertNotNull(risk("admin", "schedule", "1", "battleabort"));
			assertNotNull(risk("admin", "schedule", "1", "battleend"));
			battle.setBattleType(net.tfminecraft.simplefactions.war.battle.enums.BattleType.RAID);
			assertNull(risk("admin", "schedule", "1", "battlerestart"));
			assertNull(risk("admin", "schedule", "1", "battleabort"));
			assertNotNull(risk("admin", "schedule", "1", "battleend"));
		} finally {
			net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.resetForTests();
		}
	}

	@Test
	void startEndAndReleaseAskOnlyWhenTheyWouldActOnTheBattle() {
		net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.resetForTests();
		try {
			var battle = new net.tfminecraft.simplefactions.war.battle.engine.core.Battle("campaign_w1");
			battle.setWarId(1);
			battle.setBattleType(net.tfminecraft.simplefactions.war.battle.enums.BattleType.FIELD);
			var side = mock(net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide.class);
			org.mockito.Mockito.when(side.getId()).thenReturn("attacker");
			battle.addSide(side);
			assertNull(risk("admin", "schedule", "1", "battlehold", "off"));
			net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.addBattle(battle);
			assertNotNull(risk("admin", "schedule", "1", "battlestart"));
			battle.setStarted(true);
			assertNull(risk("admin", "schedule", "1", "battlestart"));
			assertNotNull(risk("admin", "schedule", "1", "battleend"));
			assertNotNull(risk("admin", "schedule", "1", "battleend", "NOWINNER"));
			assertNotNull(risk("admin", "schedule", "1", "battleend", "attacker"));
			assertNull(risk("admin", "schedule", "1", "battleend", "nobody"));

			battle.setStarted(false);
			War war = mock(War.class);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			org.mockito.Mockito.when(war.getBattleSchedulePhase())
					.thenReturn(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.SCHEDULED);
			org.mockito.Mockito.when(war.getScheduledBattleAt()).thenReturn(java.time.Instant.EPOCH);
			assertNull(risk("admin", "schedule", "1", "battlehold", "off"));
			battle.setStaffHold(true);
			assertNotNull(risk("admin", "schedule", "1", "battlehold", "off"));
			assertNotNull(risk("admin", "schedule", "1", "battlehold", "off", "x"));
			assertNull(risk("admin", "schedule", "1", "battlehold", "on"));
			assertNull(risk("admin", "schedule", "1", "battlehold"));
			org.mockito.Mockito.when(war.getScheduledBattleAt()).thenReturn(null);
			assertNull(risk("admin", "schedule", "1", "battlehold", "off"));
		} finally {
			net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.resetForTests();
		}
	}

	@Test
	void schedulingAStartTimeThatHasPassedAsks() {
		assertNotNull(risk("admin", "schedule", "1", "setscheduled", "2020-01-01T20:00:00Z"));
		assertNull(risk("admin", "schedule", "1", "setscheduled", "2999-01-01T20:00:00Z"));
		assertNull(risk("admin", "schedule", "1", "setscheduled", "tonight"));
		assertNull(risk("admin", "schedule", "1", "setscheduled"));
	}

	@Test
	void clockCommandsThatWouldFailAnywayDoNotAsk() {
		assertNull(risk("admin", "time", "add", "soon"));
		assertNull(risk("admin", "time", "skip-to-battle-day", "7"));
		assertNull(risk("admin", "time", "skip-to-battle-day"));
		assertNotNull(risk("admin", "time", "skip-to-battle-day", "1"));
	}

	@Test
	void unknownOrMalformedWarIdsRunSoTheCommandCanSayWhatIsWrong() {
		assertNull(risk("admin", "end", "7"));
		assertNull(risk("admin", "win", "x", "attacker"));
		assertNull(risk("admin", "path", "7"));
		assertNull(risk("admin", "schedule", "7", "battlestart"));
	}

	@Test
	void reparationsAndTruceRemovalsAsk() {
		assertNull(risk("admin", "reparations", "a"));
		try (MockedStatic<net.tfminecraft.simplefactions.managers.FactionManager> factions =
				mockStatic(net.tfminecraft.simplefactions.managers.FactionManager.class)) {
			assertNull(risk("admin", "reparations", "a", "b"));
			factions.when(() -> net.tfminecraft.simplefactions.managers.FactionManager.getByString("a"))
					.thenReturn(mock(net.tfminecraft.simplefactions.objects.Faction.class));
			assertNull(risk("admin", "reparations", "a", "b"));
			factions.when(() -> net.tfminecraft.simplefactions.managers.FactionManager.getByString("b"))
					.thenReturn(mock(net.tfminecraft.simplefactions.objects.Faction.class));
			assertNotNull(risk("admin", "reparations", "a", "b"));
		}
		assertNotNull(risk("admin", "truce", "clear", "a", "b"));
		assertNotNull(risk("admin", "truce", "clearcooldown", "a"));
		assertNull(risk("admin", "truce", "list"));
	}

	@Test
	void warEndingAndRouteCommandsAsk() {
		assertTrue(risk("admin", "end", "1").contains("no winner"));
		assertTrue(risk("admin", "win", "1", "Attacker").contains("attacker victory"));
		assertTrue(risk("admin", "path", "1").contains("route"));
		assertTrue(risk("admin", "devmode", "on").contains("dummy"));
		assertTrue(risk("admin", "devmode", "off").contains("dummy"));
	}

	@Test
	void movingTheClockForwardAsksAndNamesHowManyWars() {
		assertTrue(risk("admin", "time", "add", "1h").contains("the 1 active war"));
		assertNotNull(risk("admin", "time", "skip-to-battle-day", "1"));
		wars.when(WarManager::getActive).thenReturn(List.of(mock(War.class), mock(War.class)));
		assertTrue(risk("admin", "time", "add", "1h").contains("all 2 active wars"));
	}

	@Test
	void resetAsksOnlyWhenItMovesTheClockForward() {
		CampaignClock.add(Duration.ofHours(-1));
		assertNotNull(risk("admin", "time", "reset"));
	}
}
