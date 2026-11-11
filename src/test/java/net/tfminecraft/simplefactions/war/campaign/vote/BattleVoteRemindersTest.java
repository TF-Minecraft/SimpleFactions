package net.tfminecraft.simplefactions.war.campaign.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
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
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteReminders.Reason;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteReminders.Reminder;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.history.WarHistoryJson;

class BattleVoteRemindersTest {
	/** A Friday; its day lock is Wednesday 21:00 CET, its time lock Thursday 21:00 CET. */
	private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

	private Faction attacker;
	private War war;
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<Members> members;
	private MockedStatic<WarHistoryEvents> history;
	private String previousUrl;

	@BeforeEach
	void setUp() {
		previousUrl = Cache.warAvailabilityVoteUrl;
		Cache.warAvailabilityVoteUrl = "https://www.example.test/wars/{war_key}#vote";
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warAvailabilityDayLockHours = 48;
		Cache.warAvailabilityTimeLockHours = 24;
		Cache.warAvailabilityDaysShown = 7;
		attacker = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(attacker.getName()).thenReturn("Brume");
		when(attacker.getMembers()).thenReturn(List.of("alice", "bob"));
		when(attacker.isMember("alice")).thenReturn(true);
		when(attacker.isMember("bob")).thenReturn(true);
		Faction defender = mock(Faction.class);
		when(defender.getId()).thenReturn("def");
		when(defender.getName()).thenReturn("Varn");
		when(defender.getMembers()).thenReturn(List.of());
		FactionManager.factions.clear();
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(7, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.getAvailability().setEarliestBattleDay(DAY);

		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
		members = mockStatic(Members.class, CALLS_REAL_METHODS);
		members.when(() -> Members.accountId("alice")).thenReturn(Optional.of(account("alice")));
		members.when(() -> Members.accountId("bob")).thenReturn(Optional.of(account("bob")));
		history = mockStatic(WarHistoryEvents.class);
	}

	@AfterEach
	void tearDown() {
		history.close();
		members.close();
		bukkit.close();
		FactionManager.factions.clear();
		Cache.warAvailabilityVoteUrl = previousUrl;
	}

	private static UUID account(String key) {
		return UUID.nameUUIDFromBytes(key.getBytes());
	}

	private Player player(String key) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(account(key));
		members.when(() -> Members.key(player)).thenReturn(key);
		return player;
	}

	private void online(Player... players) {
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of(players));
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

	private Component sentTo(Player player) {
		ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
		verify(player).sendMessage(sent.capture());
		return sent.getValue();
	}

	@Test
	void markYourAvailabilityGoesOnce24HoursBeforeADayLockToThoseWhoMarkedNothing() {
		Player alice = player("alice");
		Player bob = player("bob");
		Player stranger = player("stranger");
		online(alice, bob, stranger);
		war.getAvailability().setMarks(account("bob"), DAY.plusDays(3), Set.of(22));
		Instant due = AvailabilityCalendar.dayLockAt(DAY).minus(Duration.ofHours(24)).plusSeconds(30);

		assertFalse(BattleVoteReminders.process(war, due.minusSeconds(60)));
		assertTrue(BattleVoteReminders.process(war, due));
		assertFalse(BattleVoteReminders.process(war, due.plusSeconds(60)), "Each reminder goes once");

		Component line = sentTo(alice);
		verify(bob, never()).sendMessage(any(Component.class));
		verify(stranger, never()).sendMessage(any(Component.class));
		String text = text(line);
		assertTrue(text.startsWith("⚔ Brume vs. Varn: mark the days and times you can fight."), text);
		assertTrue(text.contains("The next day is decided in 1d 0h (18/11/2026 21:00 CET / 15:00 EST)."), text);
		assertTrue(text.endsWith("[Vote in game] [Vote on the website]"), text);
		assertEquals(List.of(ClickEvent.runCommand("/war vote 7"),
				ClickEvent.openUrl("https://www.example.test/wars/" + WarHistoryJson.warKey(war) + "#vote")), clicks(line));
		history.verify(() -> WarHistoryEvents.voteReminder(war, "mark_availability", null,
				AvailabilityCalendar.dayLockAt(DAY), List.of(account("alice"))));
		assertTrue(war.getAvailability().remindersSent().contains("mark_availability|" + DAY));
	}

	@Test
	void aNoneAnswerCoveringTheDateStopsTheMarkReminder() {
		Player alice = player("alice");
		online(alice);
		war.getAvailability().setNone(account("alice"), DAY.plusDays(6));
		war.getAvailability().setNone(account("bob"), DAY.minusDays(1));

		assertTrue(BattleVoteReminders.process(war, AvailabilityCalendar.dayLockAt(DAY).minus(Duration.ofHours(1))));

		verify(alice, never()).sendMessage(any(Component.class));
		history.verify(() -> WarHistoryEvents.voteReminder(war, "mark_availability", null,
				AvailabilityCalendar.dayLockAt(DAY), List.of(account("bob"))));
	}

	@Test
	void aBattleDayLockRemindsThoseWithNoMarksOnThatDate() {
		Player alice = player("alice");
		Player bob = player("bob");
		online(alice, bob);
		war.getAvailability().setMarks(account("bob"), DAY, Set.of(21));
		war.getAvailability().setMarks(account("alice"), DAY.plusDays(1), Set.of(21));
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		Instant dayLock = AvailabilityCalendar.dayLockAt(DAY);

		BattleVoteReminders.battleDayLocked(war, DAY, dayLock);
		BattleVoteReminders.battleDayLocked(war, DAY, dayLock);

		verify(bob, never()).sendMessage(any(Component.class));
		String text = text(sentTo(alice));
		assertTrue(text.contains("Fri 20/11 is a battle day. Mark the times you can make. The time is decided in 1d 0h"),
				text);
		history.verify(() -> WarHistoryEvents.voteReminder(war, "battle_day_locked", DAY,
				AvailabilityCalendar.timeLockAt(DAY), List.of(account("alice"))), times(1));
	}

	@Test
	void theTimeLockReminderGoesThreeHoursBeforeOnlyWhileTheTimeIsOpen() {
		Player alice = player("alice");
		online(alice);
		Cache.warAvailabilityVoteUrl = " ";
		war.getAvailability().setMarks(account("alice"), DAY.plusDays(3), Set.of(21));
		war.getAvailability().setMarks(account("bob"), DAY, Set.of(23));
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		war.getAvailability().setLastDayLockProcessed(DAY);
		// The next date's own reminder is due at the same time; it went already.
		war.getAvailability().markReminderSent("mark_availability|" + DAY.plusDays(1));
		Instant timeLock = AvailabilityCalendar.timeLockAt(DAY);

		assertFalse(BattleVoteReminders.process(war, timeLock.minus(Duration.ofHours(4))));
		assertTrue(BattleVoteReminders.process(war, timeLock.minus(Duration.ofHours(3)).plusSeconds(10)));

		Component line = sentTo(alice);
		assertEquals("⚔ Brume vs. Varn: the battle time for Fri 20/11 is decided in 3h (19/11/2026 21:00 CET / 15:00 EST)."
				+ " Mark the times you can make. [Vote in game]", text(line));
		assertEquals(List.of(ClickEvent.runCommand("/war vote 7")), clicks(line), "No website link configured");
		history.verify(() -> WarHistoryEvents.voteReminder(war, "time_lock_soon", DAY, timeLock, List.of(account("alice"))));
	}

	@Test
	void decidedDatesAndTimedBattleDaysHaveNoTimedReminder() {
		Instant beforeTimeLock = AvailabilityCalendar.timeLockAt(DAY).minus(Duration.ofHours(1));
		war.getAvailability().setLastDayLockProcessed(DAY);
		assertNull(BattleVoteReminders.dueOn(war, DAY, beforeTimeLock), "No battle that day");
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false).withHour(22, false));
		assertNull(BattleVoteReminders.dueOn(war, DAY, beforeTimeLock), "Its time is already set");
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		assertNull(BattleVoteReminders.dueOn(war, DAY, AvailabilityCalendar.timeLockAt(DAY)), "Locked already");
		assertNull(BattleVoteReminders.dueOn(war, DAY.plusDays(1),
				AvailabilityCalendar.dayLockAt(DAY.plusDays(1)).minus(Duration.ofHours(25))), "Too early");
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertFalse(BattleVoteReminders.process(war, beforeTimeLock));
	}

	@Test
	void withoutAServerNobodyIsOnlineToTell() {
		bukkit.when(Bukkit::getServer).thenReturn(null);
		assertTrue(BattleVoteReminders.send(war, new Reminder(Reason.MARK_AVAILABILITY, DAY,
				AvailabilityCalendar.dayLockAt(DAY)), AvailabilityCalendar.dayLockAt(DAY).minus(Duration.ofHours(2))));
		bukkit.verify(Bukkit::getOnlinePlayers, never());
	}

	@Test
	void nobodyToRemindStillUsesTheReminderButSendsNoEvent() {
		online();
		war.getAvailability().setMarks(account("alice"), DAY, Set.of(21));
		war.getAvailability().setMarks(account("bob"), DAY, Set.of(21));

		assertTrue(BattleVoteReminders.send(war, new Reminder(Reason.MARK_AVAILABILITY, DAY,
				AvailabilityCalendar.dayLockAt(DAY)), AvailabilityCalendar.dayLockAt(DAY).minus(Duration.ofHours(2))));

		history.verify(() -> WarHistoryEvents.voteReminder(any(), anyString(), any(), any(), any()), never());
	}

	@Test
	void startingACharacterRemindsOfTheMostUrgentThingNotMarked() {
		Player alice = player("alice");
		Faction third = mock(Faction.class);
		when(third.getId()).thenReturn("third");
		War quiet = new War(8, attacker, third);
		War idle = new War(9, attacker, third);
		quiet.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		quiet.getAvailability().setEarliestBattleDay(DAY);
		quiet.getAvailability().setMarks(account("alice"), DAY.plusDays(2), Set.of(21));
		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY.plusDays(1), false));
		Instant now = AvailabilityCalendar.dayLockAt(DAY).minus(Duration.ofDays(2));
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(war, quiet, idle));

			BattleVoteReminders.onCharacterStart(alice, now);
		}

		String text = text(sentTo(alice));
		assertTrue(text.contains("Sat 21/11 is a battle day. Mark the times you can make."), text);
	}

	@Test
	void whatAppliesToAnAccountNow() {
		UUID alice = account("alice");
		Instant now = AvailabilityCalendar.dayLockAt(DAY).minus(Duration.ofDays(2));
		assertEquals(new Reminder(Reason.MARK_AVAILABILITY, DAY, AvailabilityCalendar.dayLockAt(DAY)),
				BattleVoteReminders.pendingFor(war, alice, now), "Nothing marked at all");
		war.getAvailability().setMarks(alice, DAY.plusDays(4), Set.of(21));
		assertNull(BattleVoteReminders.pendingFor(war, alice, now), "Something marked");

		war.getAvailability().putBattleDay(LockedBattleDay.open(DAY, false));
		war.getAvailability().setLastDayLockProcessed(DAY);
		Instant soon = AvailabilityCalendar.timeLockAt(DAY).minus(Duration.ofHours(2));
		assertEquals(new Reminder(Reason.TIME_LOCK_SOON, DAY, AvailabilityCalendar.timeLockAt(DAY)),
				BattleVoteReminders.pendingFor(war, alice, soon));
		war.getAvailability().setMarks(alice, DAY, Set.of(22));
		assertNull(BattleVoteReminders.pendingFor(war, alice, soon), "Marked the battle day");
		war.getAvailability().setMarks(alice, DAY, Set.of());
		war.getAvailability().setMarks(alice, DAY.plusDays(4), Set.of());
		war.getAvailability().setLastDayLockProcessed(DAY.plusDays(30));
		assertNull(BattleVoteReminders.pendingFor(war, alice, AvailabilityCalendar.timeLockAt(DAY)),
				"Every date shown is decided");
	}

	@Test
	void theWebsiteLinkUsesTheWarKey() {
		assertEquals("https://www.example.test/wars/" + WarHistoryJson.warKey(war) + "#vote",
				BattleVoteReminders.voteUrl(war));
		Cache.warAvailabilityVoteUrl = null;
		assertNull(BattleVoteReminders.voteUrl(war));
		assertEquals("mark_availability", Reason.MARK_AVAILABILITY.toJson());
		assertEquals("battle_day_locked", Reason.BATTLE_DAY_LOCKED.toJson());
		assertEquals("time_lock_soon", Reason.TIME_LOCK_SOON.toJson());
	}

	@Test
	void remainingTimeReadsInTheLargestUnits() {
		assertEquals("1d 2h", BattleVoteReminders.formatRemaining(Duration.ofHours(26)));
		assertEquals("3h 20m", BattleVoteReminders.formatRemaining(Duration.ofMinutes(200)));
		assertEquals("3h", BattleVoteReminders.formatRemaining(Duration.ofHours(3)));
		assertEquals("45m", BattleVoteReminders.formatRemaining(Duration.ofMinutes(45)));
		assertEquals("1m", BattleVoteReminders.formatRemaining(Duration.ofSeconds(5)));
		assertEquals("1m", BattleVoteReminders.formatRemaining(Duration.ZERO));
	}
}
