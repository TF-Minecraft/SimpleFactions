package net.tfminecraft.simplefactions.war.campaign.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickEligibility;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.vote.InstallationPickReminders.Due;
import net.tfminecraft.simplefactions.war.campaign.vote.InstallationPickReminders.Recipient;
import net.tfminecraft.simplefactions.war.campaign.vote.InstallationPickWarnings.Warning;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

class InstallationPickRemindersTest {
	/** A Friday: its start time is decided Thursday 21:00 CET, installation choices lock Friday 16:00 CET. */
	private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

	private Faction attacker;
	private Faction defender;
	private Faction ally;
	private War war;
	private MockedStatic<Members> members;
	private MockedStatic<BattleSideMembers> sides;
	private MockedStatic<BattleInstallationPickEligibility> eligibility;
	private MockedStatic<BattleInstallationPickService> picks;
	private InstallationPickWarnings previousWarnings;
	private int previousVoteClose;
	private int previousWindowStart;
	private int previousWindowEnd;
	private int previousDayLock;
	private int previousTimeLock;
	private final List<Player> online = new ArrayList<>();

	@BeforeEach
	void setUp() {
		previousWarnings = Cache.warInstallationWarnings;
		previousVoteClose = Cache.warVoteCloseHour;
		previousWindowStart = Cache.warBattleWindowStartHour;
		previousWindowEnd = Cache.warBattleWindowEndHour;
		previousDayLock = Cache.warAvailabilityDayLockHours;
		previousTimeLock = Cache.warAvailabilityTimeLockHours;
		Cache.warInstallationWarnings = InstallationPickWarnings.defaults();
		Cache.warVoteCloseHour = 16;
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warAvailabilityDayLockHours = 48;
		Cache.warAvailabilityTimeLockHours = 24;
		attacker = faction("atk", "Brume", "alice");
		defender = faction("def", "Varn", "dora");
		ally = faction("ally", "Orl", "olaf");
		FactionManager.factions.clear();
		war = new War(7, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setBattleDay(DAY);
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false).withHour(22, false));

		members = mockStatic(Members.class, CALLS_REAL_METHODS);
		members.when(() -> Members.player(anyString())).thenAnswer(call -> online.stream()
				.filter(player -> call.getArgument(0).equals(Members.key(player))).findFirst().orElse(null));
		sides = mockStatic(BattleSideMembers.class);
		sides.when(() -> BattleSideMembers.collectParticipatingFactions(same(war.getAttackers())))
				.thenReturn(List.of(attacker, ally));
		sides.when(() -> BattleSideMembers.collectParticipatingFactions(same(war.getDefenders())))
				.thenReturn(List.of(defender));
		List<Installation> fort = List.of(installation("fort"));
		eligibility = mockStatic(BattleInstallationPickEligibility.class);
		eligibility.when(() -> BattleInstallationPickEligibility.listPickableInstallations(same(war), any()))
				.thenReturn(fort);
		picks = mockStatic(BattleInstallationPickService.class);
		picks.when(() -> BattleInstallationPickService.getPicks(same(war), anyString())).thenReturn(Set.of());
	}

	@AfterEach
	void tearDown() {
		picks.close();
		eligibility.close();
		sides.close();
		members.close();
		Cache.warInstallationWarnings = previousWarnings;
		Cache.warVoteCloseHour = previousVoteClose;
		Cache.warBattleWindowStartHour = previousWindowStart;
		Cache.warBattleWindowEndHour = previousWindowEnd;
		Cache.warAvailabilityDayLockHours = previousDayLock;
		Cache.warAvailabilityTimeLockHours = previousTimeLock;
		FactionManager.factions.clear();
	}

	private static Faction faction(String id, String name, String leader) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(name);
		when(faction.getLeader()).thenReturn(leader);
		when(faction.getMembers()).thenReturn(List.of(leader));
		return faction;
	}

	private static Installation installation(String id) {
		Installation installation = mock(Installation.class);
		when(installation.getId()).thenReturn(id);
		return installation;
	}

	private Player online(String key) {
		Player player = mock(Player.class);
		members.when(() -> Members.key(player)).thenReturn(key);
		online.add(player);
		return player;
	}

	private void chose(Faction faction, String... installations) {
		String id = faction.getId();
		picks.when(() -> BattleInstallationPickService.getPicks(same(war), eq(id)))
				.thenReturn(new LinkedHashSet<>(List.of(installations)));
	}

	private static Instant locks() {
		return BattleWindowService.atScheduleHour(DAY, 16);
	}

	private static String text(Component component) {
		return PlainTextComponentSerializer.plainText().serialize(component);
	}

	private static List<ClickEvent> clicks(Component component) {
		List<ClickEvent> found = new ArrayList<>();
		if (component.clickEvent() != null) {
			found.add(component.clickEvent());
		}
		component.children().forEach(child -> found.addAll(clicks(child)));
		return found;
	}

	private static List<String> received(Player player) {
		ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
		verify(player, atLeast(0)).sendMessage(sent.capture());
		return sent.getAllValues().stream().map(InstallationPickRemindersTest::text).toList();
	}

	private List<String> leaders() {
		return InstallationPickReminders.recipients(war).stream().map(Recipient::leaderKey).toList();
	}

	@Test
	void leadersAreWarnedAtTheTimeLockThenThreeHoursAndOneHourBeforeTheChoicesLock() {
		Player alice = online("alice");
		Player dora = online("dora");
		chose(defender, "fort");
		Instant timeLock = AvailabilityCalendar.timeLockAt(DAY);

		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		assertFalse(InstallationPickReminders.process(war, timeLock.minusSeconds(60)), "Start time not decided yet");
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false).withHour(22, false));
		assertTrue(InstallationPickReminders.process(war, timeLock.plusSeconds(30)));
		assertFalse(InstallationPickReminders.process(war, timeLock.plusSeconds(90)), "Each warning goes once");
		assertFalse(InstallationPickReminders.process(war, locks().minus(Duration.ofHours(3)).minusSeconds(1)));
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofHours(3))));
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofHours(1))));
		assertFalse(InstallationPickReminders.process(war, locks().minus(Duration.ofMinutes(1))));

		List<String> toAlice = received(alice);
		assertEquals(3, toAlice.size(), toAlice.toString());
		assertEquals("⚔ Brume vs. Varn: choose the installations you commit to the battle on Fri 20/11; your choices"
				+ " lock in 19h (20/11/2026 16:00 CET / 10:00 EST). You have not chosen any yet. [Choose installations]",
				toAlice.get(0));
		assertEquals("⚔ Brume vs. Varn: your installation choices for the battle on Fri 20/11 lock in 3h"
				+ " (20/11/2026 16:00 CET / 10:00 EST). You have not chosen any yet. [Choose installations]", toAlice.get(1));
		assertTrue(toAlice.get(2).contains("lock in 1h (20/11/2026"), toAlice.get(2));
		assertEquals(List.of("⚔ Brume vs. Varn: your installation choices for the battle on Fri 20/11 lock in 1h"
				+ " (20/11/2026 16:00 CET / 10:00 EST). You have chosen 1; you can still change them. [Choose installations]"),
				received(dora), "The last warning also goes to leaders who chose");
		assertTrue(war.getAvailability().remindersSent().containsAll(List.of("picks_time_lock|" + DAY,
				"picks_lock_soon|" + DAY + "|180", "picks_lock_soon|" + DAY + "|60")));
	}

	@Test
	void theButtonOpensTheInstallationChoices() {
		Player alice = online("alice");
		InstallationPickReminders.process(war, locks().minus(Duration.ofHours(3)));
		ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
		verify(alice).sendMessage(sent.capture());
		assertEquals(List.of(ClickEvent.runCommand("/war installations 7")), clicks(sent.getValue()));
	}

	@Test
	void afterDowntimeOnlyTheLatestWarningThatFellDueIsSent() {
		Player alice = online("alice");
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofMinutes(30))));
		assertEquals(1, received(alice).size());
		assertTrue(received(alice).get(0).contains("lock in 30m"), received(alice).get(0));
		assertEquals(3, war.getAvailability().remindersSent().size(), "The earlier ones count as sent");
		assertFalse(InstallationPickReminders.process(war, locks().minus(Duration.ofMinutes(29))));
	}

	@Test
	void aWarningThatWentAlreadyKeepsEarlierOnesQuiet() {
		Player alice = online("alice");
		war.getAvailability().markReminderSent("picks_lock_soon|" + DAY + "|60");
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofMinutes(30))));
		verify(alice, never()).sendMessage(any(Component.class));
	}

	@Test
	void anHourStaffSetEarlyIsAnnouncedAtOnceAndDoesNotUseUpTheWarningsAfterIt() {
		Player alice = online("alice");
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, new Warning(Duration.ZERO, false),
				List.of(new Warning(Duration.ofHours(30), false), new Warning(Duration.ofHours(1), false)));
		Instant timeLock = AvailabilityCalendar.timeLockAt(DAY);
		Instant thirtyBefore = locks().minus(Duration.ofHours(30));
		Instant setEarly = thirtyBefore.minus(Duration.ofHours(1));

		assertTrue(InstallationPickReminders.process(war, setEarly), "Hour already set: the time-lock line goes now");
		assertFalse(InstallationPickReminders.process(war, thirtyBefore.minusSeconds(30)));
		assertTrue(InstallationPickReminders.process(war, thirtyBefore.plusSeconds(30)));
		assertFalse(InstallationPickReminders.process(war, timeLock.plusSeconds(30)), "Nothing new at the real time lock");

		List<String> toAlice = received(alice);
		assertEquals(2, toAlice.size(), toAlice.toString());
		assertTrue(toAlice.get(0).startsWith("⚔ Brume vs. Varn: choose the installations"), toAlice.get(0));
		assertTrue(toAlice.get(1).contains("lock in 1d 6h"), toAlice.get(1));

		List<Due> due = InstallationPickReminders.dueNow(war, DAY, setEarly, true);
		assertEquals(1, due.size());
		assertTrue(due.get(0).timeLock());
		assertEquals(Instant.MIN, due.get(0).dueAt(), "Sent: ranked first at a login");
		assertTrue(InstallationPickReminders.dueNow(war, DAY, setEarly, false).isEmpty(), "Sent: left out of the tick");
		war.setBattleDay(DAY.plusDays(1));
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY.plusDays(1), false).withHour(22, false));
		List<Due> unsent = InstallationPickReminders.dueNow(war, DAY.plusDays(1), setEarly, true);
		assertEquals(setEarly, unsent.get(0).dueAt(), "Not sent yet: due now");
	}

	@Test
	void aLoginAfterAnEarlyHourRepeatsTheLatestBeforeLockWarning() {
		Player alice = online("alice");
		Player dora = online("dora");
		chose(defender, "fort");
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, new Warning(Duration.ZERO, false),
				List.of(new Warning(Duration.ofHours(30), true)));
		Instant thirtyBefore = locks().minus(Duration.ofHours(30));
		InstallationPickReminders.process(war, thirtyBefore.minus(Duration.ofHours(1)));
		InstallationPickReminders.process(war, thirtyBefore.plusSeconds(30));
		assertEquals(2, received(alice).size());
		assertEquals(1, received(dora).size(), "The 30 h warning includes leaders who chose");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(war));
			Instant later = thirtyBefore.plus(Duration.ofHours(1));
			InstallationPickReminders.onCharacterStart(alice, later);
			InstallationPickReminders.onCharacterStart(dora, later);
		}
		assertTrue(received(alice).get(2).contains("your installation choices for the battle on Fri 20/11 lock in 1d 5h"),
				received(alice).get(2));
		assertEquals(2, received(dora).size(), "The leader who chose gets the 30 h warning again");
	}

	@Test
	void aLoginAfterANormalTimeLockRepeatsTheTimeLockLineOverAnEarlierWarning() {
		Player dora = online("dora");
		chose(defender, "fort");
		// The 24 h warning (leaders who chose included) goes before the time lock; the time-lock line
		// (only leaders who chose none) goes at it. A login after it repeats the time-lock line.
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, new Warning(Duration.ZERO, false),
				List.of(new Warning(Duration.ofHours(24), true)));
		Instant timeLock = AvailabilityCalendar.timeLockAt(DAY);
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofHours(24))));
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false).withHour(22, false));
		assertTrue(InstallationPickReminders.process(war, timeLock.plusSeconds(30)));
		assertEquals(1, received(dora).size());
		List<Due> due = InstallationPickReminders.dueNow(war, DAY, timeLock.plus(Duration.ofHours(1)), true);
		assertEquals(List.of(false, true), due.stream().map(Due::timeLock).toList());
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(war));
			InstallationPickReminders.onCharacterStart(dora, timeLock.plus(Duration.ofHours(1)));
		}
		assertEquals(1, received(dora).size(), "The time-lock line is for leaders who chose none");
	}

	@Test
	void theTimeLockLineWaitsForTheHourAndCanBeTurnedOff() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		Instant afterTimeLock = AvailabilityCalendar.timeLockAt(DAY).plusSeconds(60);
		assertTrue(InstallationPickReminders.dueNow(war, DAY, afterTimeLock, false).isEmpty(), "No hour yet");
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false).withHour(23, false));
		assertEquals(1, InstallationPickReminders.dueNow(war, DAY, afterTimeLock, false).size());
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, null, List.of(new Warning(Duration.ofHours(1), false)));
		assertTrue(InstallationPickReminders.dueNow(war, DAY, afterTimeLock, false).isEmpty(), "Time-lock line turned off");
	}

	@Test
	void aWarningEarlierThanTheTimeLockStillComesFirst() {
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, new Warning(Duration.ZERO, true),
				List.of(new Warning(Duration.ofHours(24), true)));
		Instant afterTimeLock = AvailabilityCalendar.timeLockAt(DAY).plusSeconds(60);
		List<Due> due = InstallationPickReminders.dueNow(war, DAY, afterTimeLock, false);
		assertEquals(List.of(false, true), due.stream().map(Due::timeLock).toList());
	}

	@Test
	void aQueuedSecondBattleDayGetsItsTimeLockLineWhenItBecomesTheNextBattle() {
		Player alice = online("alice");
		LocalDate next = DAY.plusDays(1);
		war.getAvailability().putBattleDay(LockedBattleDay.open(next, false).withHour(21, false));
		// Friday's battle is fought; the war moves on to Saturday, whose time was fixed Friday 21:00.
		Instant afterFridayBattle = BattleWindowService.atScheduleHour(DAY, 25);
		war.setBattleDay(next);

		assertTrue(InstallationPickReminders.process(war, afterFridayBattle));
		assertEquals(List.of("⚔ Brume vs. Varn: choose the installations you commit to the battle on Sat 21/11; your"
				+ " choices lock in 15h (21/11/2026 16:00 CET / 10:00 EST). You have not chosen any yet."
				+ " [Choose installations]"), received(alice));
		assertTrue(war.getAvailability().remindersSent().contains("picks_time_lock|" + next));
	}

	@Test
	void theNewKeysArePrunedTheDayAfterTheirBattleDay() {
		war.getAvailability().markReminderSent("picks_lock_soon|" + DAY + "|180");
		war.getAvailability().markReminderSent("picks_time_lock|" + DAY);
		assertFalse(war.getAvailability().pruneBefore(DAY), "Kept on the day itself");
		assertTrue(war.getAvailability().pruneBefore(DAY.plusDays(1)));
		assertTrue(war.getAvailability().remindersSent().isEmpty());
	}

	@Test
	void factionsWithoutALeaderOrWithNothingToCommitAreNotWarned() {
		eligibility.when(() -> BattleInstallationPickEligibility.listPickableInstallations(same(war), same(ally)))
				.thenReturn(List.of());
		assertEquals(List.of("alice", "dora"), leaders());
		when(defender.getLeader()).thenReturn(" ");
		assertEquals(List.of("alice"), leaders());
		when(attacker.getLeader()).thenReturn(null);
		assertTrue(leaders().isEmpty());
	}

	@Test
	void theAutomaticDefenderPortAndStaffForcedPicksAreNotTheLeadersChoices() {
		chose(defender, "port", "forced", "own");
		picks.when(() -> BattleInstallationPickService.isDefenderZocPort(war, defender, "port")).thenReturn(true);
		picks.when(() -> BattleInstallationPickService.isForced(war, "def", "forced")).thenReturn(true);
		assertEquals(1, InstallationPickReminders.chosen(war, defender));
		chose(defender, "port", "forced");
		assertEquals(0, InstallationPickReminders.chosen(war, defender));

		// With only the port and a forced installation left, the defender's leader has nothing to choose.
		List<Installation> nothingToChoose = List.of(installation("port"), installation("forced"));
		eligibility.when(() -> BattleInstallationPickEligibility.listPickableInstallations(same(war), same(defender)))
				.thenReturn(nothingToChoose);
		assertFalse(leaders().contains("dora"));
		List<Installation> oneChoice = List.of(installation("port"), installation("forced"), installation("own"));
		eligibility.when(() -> BattleInstallationPickEligibility.listPickableInstallations(same(war), same(defender)))
				.thenReturn(oneChoice);
		assertTrue(leaders().contains("dora"));
	}

	@Test
	void offlineLeadersAreNotTold() {
		Player olaf = online("olaf");
		online.remove(olaf);
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofHours(3))), "Counts as sent");
		verify(olaf, never()).sendMessage(any(Component.class));
	}

	@Test
	void aFailureForOneFactionIsLoggedAndTheOtherLeadersAreStillTold() {
		Player alice = online("alice");
		Player dora = online("dora");
		picks.when(() -> BattleInstallationPickService.getPicks(same(war), eq("ally")))
				.thenThrow(new IllegalStateException("boom"));
		List<LogRecord> logged = captureLog(() -> assertTrue(
				InstallationPickReminders.process(war, locks().minus(Duration.ofHours(3)))));
		assertEquals(1, logged.size());
		assertEquals(Level.WARNING, logged.get(0).getLevel());
		assertTrue(logged.get(0).getMessage().contains("Could not send installation warnings for war 7"));
		assertEquals(1, received(alice).size(), "Before the failing faction");
		assertEquals(1, received(dora).size(), "After the failing faction");
		assertTrue(war.getAvailability().remindersSent().contains("picks_lock_soon|" + DAY + "|180"), "Not retried");
	}

	@Test
	void aFailureListingTheSidesOrAtLoginIsLoggedToo() {
		Player alice = online("alice");
		sides.when(() -> BattleSideMembers.collectParticipatingFactions(same(war.getAttackers())))
				.thenThrow(new IllegalStateException("boom"));
		war.getAvailability().markReminderSent("picks_lock_soon|" + DAY + "|180");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(war));
			List<LogRecord> logged = captureLog(() -> {
				assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofHours(1))));
				InstallationPickReminders.onCharacterStart(alice, locks().minus(Duration.ofHours(1)));
			});
			assertEquals(2, logged.size());
		}
		verify(alice, never()).sendMessage(any(Component.class));
	}

	@Test
	void aLockHourOutsideTheBattleDayClockMeansNoWarning() {
		Cache.warVoteCloseHour = 99;
		assertNull(InstallationPickReminders.coming(war, AvailabilityCalendar.timeLockAt(DAY)));
	}

	@Test
	void theFactionACharacterLeadsInTheWar() {
		assertEquals(defender, InstallationPickReminders.factionLedBy(war, "dora"));
		assertEquals(ally, InstallationPickReminders.factionLedBy(war, "olaf"));
		assertNull(InstallationPickReminders.factionLedBy(war, "stranger"));
		assertNull(InstallationPickReminders.factionLedBy(war, null));
		assertNull(InstallationPickReminders.factionLedBy(null, "dora"));
	}

	private static List<LogRecord> captureLog(Runnable action) {
		List<LogRecord> logged = new ArrayList<>();
		Logger logger = Logger.getLogger(InstallationPickReminders.class.getName());
		Handler handler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				logged.add(record);
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		logger.addHandler(handler);
		try {
			action.run();
		} finally {
			logger.removeHandler(handler);
		}
		return logged;
	}

	@Test
	void noWarningOnceTheChoicesLockOrWhenThereIsNoFoughtBattleDay() {
		Instant soon = locks().minus(Duration.ofHours(1));
		assertNull(InstallationPickReminders.coming(war, locks()), "Locked");
		assertNull(InstallationPickReminders.coming(null, soon));
		assertNull(InstallationPickReminders.coming(war, null));
		war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
		assertNull(InstallationPickReminders.coming(war, soon));
		war.setBattleSchedulePhase(BattleSchedulePhase.BUILDUP);
		assertEquals(DAY, InstallationPickReminders.coming(war, soon), "Staff tools can leave a locked day in buildup");
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		assertEquals(DAY, InstallationPickReminders.coming(war, soon));
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, true).withHour(22, true));
		assertNull(InstallationPickReminders.coming(war, soon), "Autoresolved, not fought");
		war.getAvailability().removeBattleDay(DAY);
		assertNull(InstallationPickReminders.coming(war, soon), "Not a battle day");
		war.setBattleDay(null);
		assertNull(InstallationPickReminders.coming(war, soon));
		assertFalse(InstallationPickReminders.process(war, soon));
		war.setBattleDay(DAY);
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		Cache.warInstallationWarnings = null;
		assertNull(InstallationPickReminders.coming(war, soon));
		Cache.warInstallationWarnings = new InstallationPickWarnings(false, null, List.of());
		assertNull(InstallationPickReminders.coming(war, soon), "Turned off");
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, null, List.of());
		assertEquals(DAY, InstallationPickReminders.coming(war, soon));
		assertFalse(InstallationPickReminders.process(war, soon), "No warnings configured");
		Cache.warInstallationWarnings = InstallationPickWarnings.defaults();
		war.end(WarEndReason.WHITE_PEACE);
		assertNull(InstallationPickReminders.coming(war, soon), "Ended");
	}

	@Test
	void noWarningWhileTheBattleIsBeingFought() {
		Battle battle = mock(Battle.class);
		try (MockedStatic<BattleManager> battles = mockStatic(BattleManager.class)) {
			battles.when(() -> BattleManager.getByWarId(7)).thenReturn(battle);
			Instant soon = locks().minus(Duration.ofHours(1));
			assertEquals(DAY, InstallationPickReminders.coming(war, soon), "Prepared, not begun");
			when(battle.hasStarted()).thenReturn(true);
			assertNull(InstallationPickReminders.coming(war, soon));
		}
	}

	@Test
	void aLeaderWhoChoseIsToldHowManyInTheTimeLockLine() {
		Due due = new Due("k", locks(), new Warning(Duration.ZERO, true), true);
		String text = text(InstallationPickReminders.message(war, DAY, due, new Recipient(attacker, "alice", 2),
				locks().minus(Duration.ofHours(2))));
		assertTrue(text.contains("choose the installations you commit to the battle on Fri 20/11; your choices lock in 2h"),
				text);
		assertTrue(text.contains("You have chosen 2; you can still change them."), text);
	}

	@Test
	void aLeaderStartingACharacterInsideAWarningGetsItThen() {
		Player alice = online("alice");
		Player dora = online("dora");
		Player olaf = online("olaf");
		Player stranger = online("stranger");
		Player nobody = mock(Player.class);
		members.when(() -> Members.key(nobody)).thenReturn(null);
		chose(defender, "fort");
		eligibility.when(() -> BattleInstallationPickEligibility.listPickableInstallations(same(war), same(ally)))
				.thenReturn(List.of());
		War quiet = new War(8, ally, defender);
		quiet.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(quiet, war));
			war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
			Instant early = locks().minus(Duration.ofHours(20));
			InstallationPickReminders.onCharacterStart(alice, early);
			verify(alice, never()).sendMessage(any(Component.class));
			war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false).withHour(22, false));

			Instant soon = locks().minus(Duration.ofHours(2));
			InstallationPickReminders.onCharacterStart(alice, soon);
			verify(alice, never()).sendMessage(any(Component.class));
			// The tick sends the 3 h warning within the minute; after that a login repeats it.
			war.getAvailability().markReminderSent("picks_lock_soon|" + DAY + "|180");
			InstallationPickReminders.onCharacterStart(alice, soon);
			InstallationPickReminders.onCharacterStart(dora, soon);
			InstallationPickReminders.onCharacterStart(olaf, soon);
			InstallationPickReminders.onCharacterStart(stranger, soon);
			InstallationPickReminders.onCharacterStart(nobody, soon);

			assertEquals(1, received(alice).size());
			assertTrue(received(alice).get(0).contains("lock in 2h"), received(alice).get(0));
			verify(dora, never()).sendMessage(any(Component.class));
			verify(olaf, never()).sendMessage(any(Component.class));
			verify(stranger, never()).sendMessage(any(Component.class));
			verify(nobody, never()).sendMessage(any(Component.class));

			war.getAvailability().markReminderSent("picks_lock_soon|" + DAY + "|60");
			InstallationPickReminders.onCharacterStart(dora, locks().minus(Duration.ofMinutes(20)));
			verify(dora, times(1)).sendMessage(any(Component.class));

			// After the time-lock line went, a leader starting before the next warning still hears of it.
			war.getAvailability().markReminderSent("picks_time_lock|" + DAY);
			InstallationPickReminders.onCharacterStart(alice, locks().minus(Duration.ofHours(10)));
			assertEquals(2, received(alice).size());
			assertTrue(received(alice).get(1).startsWith("⚔ Brume vs. Varn: choose the installations"),
					received(alice).get(1));
		}
	}
}
