package net.tfminecraft.simplefactions.war.campaign.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.HashSet;
import java.util.List;
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
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarVoting;

class BattleVoteRemindersTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 11, 20);

	private Faction attacker;
	private War war;
	private Instant closes;
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<Members> members;

	@BeforeEach
	void setUp() {
		BattleVoteReminders.clearForTests();
		Cache.warVoteCloseHour = 16;
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		attacker = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(attacker.getName()).thenReturn("Brume");
		when(attacker.isMember("alice")).thenReturn(true);
		when(attacker.isMember("bob")).thenReturn(true);
		Faction defender = mock(Faction.class);
		when(defender.getId()).thenReturn("def");
		when(defender.getName()).thenReturn("Varn");
		FactionManager.factions.clear();
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(7, attacker, defender);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		closes = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);

		bukkit = mockStatic(Bukkit.class);
		members = mockStatic(Members.class, CALLS_REAL_METHODS);
	}

	@AfterEach
	void tearDown() {
		members.close();
		bukkit.close();
		FactionManager.factions.clear();
		BattleVoteReminders.clearForTests();
	}

	private Player player(String key) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(key.getBytes()));
		members.when(() -> Members.key(player)).thenReturn(key);
		return player;
	}

	private void voted(Player player) {
		war.getBattleVotes().put(player.getUniqueId(), new HashSet<>(Set.of(22)));
	}

	@Test
	void theOpenVoteAndItsCloseTime() {
		BattleVoteReminders.OpenVote vote = BattleVoteReminders.openVote(war, closes.minusSeconds(60));
		assertEquals(WarVoting.Kind.HOUR, vote.kind());
		assertEquals(closes, vote.closesAt());

		assertNull(BattleVoteReminders.openVote(war, closes));
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		assertNull(BattleVoteReminders.openVote(war, closes.minusSeconds(60)));

		// A day vote without a close time has nothing to remind about.
		war.setBattleSchedulePhase(BattleSchedulePhase.BUILDUP);
		war.setDayVoteFirstDay(BATTLE_DAY);
		assertNull(BattleVoteReminders.openVote(war, closes.minusSeconds(60)));
		war.setDayVoteClosesAt(closes);
		assertEquals(WarVoting.Kind.DAY, BattleVoteReminders.openVote(war, closes.minusSeconds(60)).kind());
	}

	@Test
	void anyPickCountsAsVoted() {
		UUID account = UUID.randomUUID();
		assertFalse(BattleVoteReminders.hasVoted(war, account, WarVoting.Kind.HOUR));
		war.getBattleVotes().put(account, new HashSet<>());
		assertFalse(BattleVoteReminders.hasVoted(war, account, WarVoting.Kind.HOUR));
		war.getBattleVotes().put(account, new HashSet<>(Set.of(21)));
		assertTrue(BattleVoteReminders.hasVoted(war, account, WarVoting.Kind.HOUR));
		war.getDayVotes().put(account, new HashSet<>(Set.of(0)));
		assertTrue(BattleVoteReminders.hasVoted(war, account, WarVoting.Kind.DAY));
	}

	@Test
	void eachReminderFallsDueOnceAndOnlyNearItsTime() {
		BattleVoteReminders.OpenVote vote = new BattleVoteReminders.OpenVote(WarVoting.Kind.HOUR, closes);
		Instant threeHoursBefore = closes.minus(Duration.ofHours(3));

		assertTrue(BattleVoteReminders.dueReminders(war, vote, threeHoursBefore.minusSeconds(1)).isEmpty());
		assertEquals(List.of(3), BattleVoteReminders.dueReminders(war, vote, threeHoursBefore.plusSeconds(30)));
		assertTrue(BattleVoteReminders.dueReminders(war, vote, threeHoursBefore.plusSeconds(90)).isEmpty());

		Instant oneHourBefore = closes.minus(Duration.ofHours(1));
		assertTrue(BattleVoteReminders.dueReminders(war, vote, oneHourBefore.plus(Duration.ofMinutes(5))).isEmpty());
		assertEquals(List.of(1), BattleVoteReminders.dueReminders(war, vote, oneHourBefore.plus(Duration.ofMinutes(4))));
	}

	@Test
	void onlyOnlineVotersWhoHaveNotVotedAreReminded() {
		Player alice = player("alice");
		Player bob = player("bob");
		voted(bob);
		Player stranger = player("stranger");
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of(alice, bob, stranger));

		BattleVoteReminders.process(war, closes.minus(Duration.ofHours(1)));
		BattleVoteReminders.process(war, closes.minus(Duration.ofHours(1)).plusSeconds(60));

		ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
		verify(alice, times(1)).sendMessage(sent.capture());
		verify(bob, never()).sendMessage(any(Component.class));
		verify(stranger, never()).sendMessage(any(Component.class));
		String text = PlainTextComponentSerializer.plainText().serialize(sent.getValue());
		assertTrue(text.contains("Brume vs. Varn"), text);
		assertTrue(text.contains("the battle hour"), text);
		assertTrue(text.contains("closes in 1h"), text);
		assertEquals(ClickEvent.runCommand("/war vote 7"), sent.getValue().clickEvent());
	}

	@Test
	void nothingIsSentBetweenReminderTimesOrWithoutAVote() {
		Player alice = player("alice");
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of(alice));

		BattleVoteReminders.process(war, closes.minus(Duration.ofHours(2)));
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		BattleVoteReminders.process(war, closes.minus(Duration.ofHours(1)));

		verify(alice, never()).sendMessage(any(Component.class));
	}

	@Test
	void playingACharacterBringsTheRemindersItMissed() {
		Player alice = player("alice");
		Faction third = mock(Faction.class);
		when(third.getId()).thenReturn("third");
		War quiet = new War(8, attacker, third);
		quiet.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setBattleSchedulePhase(BattleSchedulePhase.BUILDUP);
		war.setDayVoteFirstDay(BATTLE_DAY);
		war.setDayVoteClosesAt(closes);
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(war, quiet));

			BattleVoteReminders.onCharacterStart(alice, closes.minus(Duration.ofDays(2)));
		}

		ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
		verify(alice).sendMessage(sent.capture());
		String text = PlainTextComponentSerializer.plainText().serialize(sent.getValue());
		assertTrue(text.contains("the day of the first battle"), text);
		assertTrue(text.contains("closes in 2d 0h"), text);
	}

	@Test
	void noPlayerNoReminder() {
		BattleVoteReminders.OpenVote vote = new BattleVoteReminders.OpenVote(WarVoting.Kind.HOUR, closes);
		assertFalse(BattleVoteReminders.remindIfNotVoted(war, null, vote, closes.minusSeconds(60)));
		assertNotNull(BattleVoteReminders.message(war, vote, closes.minusSeconds(60)));
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
