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
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
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
	private final List<Player> online = new ArrayList<>();

	@BeforeEach
	void setUp() {
		previousWarnings = Cache.warInstallationWarnings;
		previousVoteClose = Cache.warVoteCloseHour;
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
		eligibility = mockStatic(BattleInstallationPickEligibility.class);
		eligibility.when(() -> BattleInstallationPickEligibility.listPickableInstallations(same(war), any()))
				.thenReturn(List.of(mock(Installation.class)));
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
		FactionManager.factions.clear();
	}

	private static Faction faction(String id, String name, String leader) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(name);
		when(faction.getLeader()).thenReturn(leader);
		when(faction.getMembers()).thenReturn(List.of(leader));
		when(faction.getInstallationHandler()).thenReturn(mock(InstallationHandler.class));
		return faction;
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
		verify(player, org.mockito.Mockito.atLeast(0)).sendMessage(sent.capture());
		return sent.getAllValues().stream().map(InstallationPickRemindersTest::text).toList();
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
		assertEquals("⚔ Brume vs. Varn: the battle on Fri 20/11 starts at 22:00 CET / 16:00 EST. Choose the installations"
				+ " you commit; your choices lock in 19h (20/11/2026 16:00 CET / 10:00 EST). You have not chosen any"
				+ " yet. [Choose installations]", toAlice.get(0));
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
	void aWarningThatWentAlreadyMarksTheEarlierOnesWithoutSendingAgain() {
		Player alice = online("alice");
		war.getAvailability().markReminderSent("picks_lock_soon|" + DAY + "|60");
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofMinutes(30))));
		verify(alice, never()).sendMessage(any(Component.class));
	}

	@Test
	void factionsWithNothingToCommitLeaderlessOnesAndOfflineLeadersAreNotWarned() {
		Player alice = online("alice");
		Player olaf = online("olaf");
		when(defender.getLeader()).thenReturn(" ");
		eligibility.when(() -> BattleInstallationPickEligibility.listPickableInstallations(same(war), same(ally)))
				.thenReturn(List.of());
		when(attacker.getLeader()).thenReturn("alice", (String) null, "alice");
		Faction unloaded = faction("raw", "Raw", "rhea");
		when(unloaded.getInstallationHandler()).thenReturn(null);
		sides.when(() -> BattleSideMembers.collectParticipatingFactions(same(war.getDefenders())))
				.thenReturn(List.of(defender, unloaded));

		assertEquals(List.of("alice"), InstallationPickReminders.recipients(war).stream()
				.map(InstallationPickReminders.Recipient::leaderKey).toList());
		assertTrue(InstallationPickReminders.recipients(war).isEmpty(), "No leader at all");
		assertEquals(1, InstallationPickReminders.recipients(war).size(), "Installations not loaded: skipped");
		verify(alice, never()).sendMessage(any(Component.class));
		verify(olaf, never()).sendMessage(any(Component.class));
		online.clear();
		when(attacker.getLeader()).thenReturn("alice");
		assertTrue(InstallationPickReminders.process(war, locks().minus(Duration.ofHours(3))), "Sent to nobody online");
		verify(alice, never()).sendMessage(any(Component.class));
	}

	@Test
	void theAutomaticDefenderPortAndStaffForcedPicksAreNotTheLeadersChoices() {
		chose(defender, "port", "forced", "own");
		picks.when(() -> BattleInstallationPickService.isDefenderZocPort(war, defender, "port")).thenReturn(true);
		picks.when(() -> BattleInstallationPickService.isForced(war, "def", "forced")).thenReturn(true);
		assertEquals(1, InstallationPickReminders.chosen(war, defender));
		chose(defender, "port", "forced");
		assertEquals(0, InstallationPickReminders.chosen(war, defender));
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
		assertEquals(DAY, InstallationPickReminders.coming(war, soon));
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
	void theTimeLockLineWaitsForTheHourAndIsDueAtOnceWhenStaffSetItEarly() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		Instant beforeTimeLock = AvailabilityCalendar.timeLockAt(DAY).minus(Duration.ofHours(2));
		assertTrue(InstallationPickReminders.dueNow(war, DAY, beforeTimeLock).isEmpty(), "No hour yet");
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false).withHour(23, false));
		List<InstallationPickReminders.Due> due = InstallationPickReminders.dueNow(war, DAY, beforeTimeLock);
		assertEquals(1, due.size());
		assertTrue(due.get(0).timeLock());
		assertEquals(beforeTimeLock, due.get(0).dueAt());
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, null, List.of(new Warning(Duration.ofHours(1), false)));
		assertTrue(InstallationPickReminders.dueNow(war, DAY, beforeTimeLock).isEmpty(), "Time-lock line turned off");
	}

	@Test
	void aWarningEarlierThanTheTimeLockStillComesFirst() {
		Cache.warInstallationWarnings = new InstallationPickWarnings(true, new Warning(Duration.ZERO, true),
				List.of(new Warning(Duration.ofHours(24), true)));
		Instant afterTimeLock = AvailabilityCalendar.timeLockAt(DAY).plusSeconds(60);
		List<InstallationPickReminders.Due> due = InstallationPickReminders.dueNow(war, DAY, afterTimeLock);
		assertEquals(List.of(false, true), due.stream().map(InstallationPickReminders.Due::timeLock).toList());
	}

	@Test
	void aTimeLockMessageWithoutAnHourFallsBackToTheLockLine() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		InstallationPickReminders.Due due = new InstallationPickReminders.Due("k", locks(), new Warning(Duration.ZERO, false), true);
		String text = text(InstallationPickReminders.message(war, DAY, due,
				new InstallationPickReminders.Recipient(attacker, "alice", 2), locks().minus(Duration.ofHours(2))));
		assertTrue(text.contains("your installation choices for the battle on Fri 20/11 lock in 2h"), text);
		assertTrue(text.contains("You have chosen 2; you can still change them."), text);
		war.getAvailability().removeBattleDay(DAY);
		assertTrue(text(InstallationPickReminders.message(war, DAY, due,
				new InstallationPickReminders.Recipient(attacker, "alice", 0), locks().minus(Duration.ofHours(2))))
				.contains("lock in 2h"));
	}

	@Test
	void aLeaderStartingACharacterInsideAWarningGetsItThen() {
		Player alice = online("alice");
		Player dora = online("dora");
		Player stranger = online("stranger");
		Player nobody = mock(Player.class);
		members.when(() -> Members.key(nobody)).thenReturn(null);
		chose(defender, "fort");
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
			InstallationPickReminders.onCharacterStart(dora, soon);
			InstallationPickReminders.onCharacterStart(stranger, soon);
			InstallationPickReminders.onCharacterStart(nobody, soon);

			assertEquals(1, received(alice).size());
			assertTrue(received(alice).get(0).contains("lock in 2h"), received(alice).get(0));
			verify(dora, never()).sendMessage(any(Component.class));
			verify(stranger, never()).sendMessage(any(Component.class));
			verify(nobody, never()).sendMessage(any(Component.class));

			InstallationPickReminders.onCharacterStart(dora, locks().minus(Duration.ofMinutes(20)));
			verify(dora, times(1)).sendMessage(any(Component.class));
		}
	}
}
