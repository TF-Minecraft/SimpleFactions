package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.AvailabilityHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.history.WarHistoryJson;

class AvailabilityViewTest {
	/** Wednesday 18 November: Friday, the first date shown, is decided at 21:00 tonight. */
	private static final Instant NOW = Instant.parse("2026-11-18T09:00:00Z");
	private static final LocalDate FRI = LocalDate.of(2026, 11, 20);

	private FactionDomainFixture fixture;
	private final List<Object> saved = new ArrayList<>();
	private MockedStatic<CampaignClock> clock;
	private MockedStatic<WarManager> wars;
	private MockedStatic<WarHistoryEvents> history;
	private InventoryManager navigation;
	private AvailabilityView view;
	private War war;
	private Player player;

	@BeforeEach
	void setUp() {
		fixture = new FactionDomainFixture();
		saved.addAll(List.of(Cache.warBattleWindowStartHour, Cache.warBattleWindowEndHour, Cache.warAvailabilityDaysShown,
				Cache.warAvailabilityDayLockHours, Cache.warAvailabilityTimeLockHours, Cache.warAvailabilityMaxDaysWithoutBattle));
		saved.add(Cache.warAvailabilityVoteUrl);
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warAvailabilityDaysShown = 7;
		Cache.warAvailabilityDayLockHours = 48;
		Cache.warAvailabilityTimeLockHours = 24;
		Cache.warAvailabilityMaxDaysWithoutBattle = 3;
		Cache.warAvailabilityVoteUrl = "https://www.example.test/wars/{war_key}#vote";
		Faction attacker = fixture.saved("attacker", "Leader");
		Faction defender = fixture.saved("defender", "Defender");
		player = fixture.player("Leader");
		war = new War(7, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.getAvailability().setEarliestBattleDay(FRI);
		clock = mockStatic(CampaignClock.class);
		clock.when(CampaignClock::now).thenReturn(NOW);
		wars = mockStatic(WarManager.class);
		wars.when(() -> WarManager.getById(7)).thenReturn(war);
		history = mockStatic(WarHistoryEvents.class);
		navigation = mock(InventoryManager.class);
		when(navigation.getFiller(any())).thenAnswer(call -> new ItemStack(call.<Material>getArgument(0)));
		view = new AvailabilityView(navigation);
	}

	@AfterEach
	void tearDown() {
		try {
			history.close();
			wars.close();
			clock.close();
		} finally {
			Cache.warBattleWindowStartHour = (Integer) saved.get(0);
			Cache.warBattleWindowEndHour = (Integer) saved.get(1);
			Cache.warAvailabilityDaysShown = (Integer) saved.get(2);
			Cache.warAvailabilityDayLockHours = (Integer) saved.get(3);
			Cache.warAvailabilityTimeLockHours = (Integer) saved.get(4);
			Cache.warAvailabilityMaxDaysWithoutBattle = (Integer) saved.get(5);
			Cache.warAvailabilityVoteUrl = (String) saved.get(6);
			fixture.close();
		}
	}

	private Inventory top() {
		return player.getOpenInventory().getTopInventory();
	}

	private InventoryClickEvent click(int slot) {
		InventoryClickEvent event = fixture.ui.click(player, slot);
		view.click(event, top(), player);
		return event;
	}

	private static String name(ItemStack item) {
		return ChatColor.stripColor(item.getItemMeta().getDisplayName());
	}

	private static String lore(ItemStack item) {
		return String.join("\n", item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList());
	}

	private Set<Integer> marks(LocalDate date) {
		return war.getAvailability().marksOn(player.getUniqueId(), date);
	}

	@Test
	void theGridShowsDatesAcrossAndStartTimesDown() {
		view.open(player, war);
		Inventory grid = top();

		assertInstanceOf(AvailabilityHolder.class, grid.getHolder(false));
		assertEquals(List.of(FRI, FRI.plusDays(1), FRI.plusDays(2), FRI.plusDays(3), FRI.plusDays(4), FRI.plusDays(5),
				FRI.plusDays(6)), ((AvailabilityHolder) grid.getHolder(false)).getDates());
		assertEquals("Start times", name(grid.getItem(0)));
		assertEquals("21:00 CET / 15:00 EST", name(grid.getItem(9)));
		assertEquals("01:00 CET / 19:00 EST (20/11/2026)", name(grid.getItem(45)));
		assertEquals("Fri 20/11", name(grid.getItem(1)));
		assertEquals(Material.PAPER, grid.getItem(1).getType());
		assertEquals("Battle or not is decided 18/11/2026 21:00 CET / 15:00 EST\nClick: mark every time", lore(grid.getItem(1)));
		assertEquals(Material.WHITE_STAINED_GLASS_PANE, grid.getItem(10).getType());
		assertEquals("Fri 20/11 21:00 CET", name(grid.getItem(10)));
		assertEquals("21:00 CET / 15:00 EST\nAttackers 0 · Defenders 0\nClick: I can fight then", lore(grid.getItem(10)));
		assertEquals("Thu 26/11", name(grid.getItem(7)));
		assertEquals("✘ You haven't marked anything", name(grid.getItem(8)));
		assertEquals("No battle day yet.\nNext decision: 18/11/2026 21:00 CET / 15:00 EST", lore(grid.getItem(8)));
		assertEquals("How it works", name(grid.getItem(17)));
		assertEquals("Mark every day and time you could fight.\n"
				+ "A day becomes a battle when 1 of each side can come at the same time.\n"
				+ "It's decided 2 days before. The time is decided 1 day before.\n"
				+ "After 3 days without a battle, the last one is a battle anyway.\n"
				+ "Green: you can fight then. Click a day at the top to mark all of it.", lore(grid.getItem(17)));
		assertEquals("None of these days work for me", name(grid.getItem(26)));
		assertEquals(Material.COMPASS, grid.getItem(35).getType());
		assertEquals(Material.ARROW, grid.getItem(53).getType());
	}

	@Test
	void clickingACellOrADayMarksItAndRedrawsTheGrid() {
		view.open(player, war);

		InventoryClickEvent first = click(10);
		assertTrue(first.isCancelled());
		assertEquals(Set.of(21), marks(FRI));
		assertEquals(Material.LIME_STAINED_GLASS_PANE, top().getItem(10).getType());
		assertEquals("✔ Fri 20/11 21:00 CET", name(top().getItem(10)));
		assertEquals("✔ You marked when you can fight", name(top().getItem(8)));
		wars.verify(() -> WarManager.persist(war));
		history.verify(() -> WarHistoryEvents.votesChanged(war));
		verify(player).playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);

		click(46);
		assertEquals(Set.of(21, 25), marks(FRI), "The bottom row is 01:00");
		click(1);
		assertEquals(Set.of(21, 22, 23, 24, 25), marks(FRI), "The header marks every time");
		assertTrue(lore(top().getItem(1)).contains("Click: clear this day"));
		click(1);
		assertTrue(marks(FRI).isEmpty(), "And clears a fully marked day");
		click(12);
		assertEquals(Set.of(21), marks(FRI.plusDays(2)));
	}

	@Test
	void noneOfTheseDaysIsAnAnswerThatCanBeTakenBack() {
		view.open(player, war);
		click(10);

		click(26);
		assertTrue(marks(FRI).isEmpty());
		assertEquals("You said none of these days work", name(top().getItem(26)));
		assertEquals("You said none of these days work", name(top().getItem(8)));

		click(26);
		assertNull(war.getAvailability().noneUntil(player.getUniqueId()));
		assertEquals("None of these days work for me", name(top().getItem(26)));
	}

	@Test
	void theWebsiteAndBackButtonsAndEmptySlotsDoWhatTheySay() {
		view.open(player, war);
		click(35);
		ArgumentCaptor<Component> link = ArgumentCaptor.forClass(Component.class);
		verify(player).sendMessage(link.capture());
		assertEquals(ClickEvent.openUrl("https://www.example.test/wars/" + WarHistoryJson.warKey(war) + "#vote"),
				link.getValue().clickEvent());

		click(53);
		verify(navigation).openCampaignView(player, war);

		view.open(player, war);
		for (int slot : List.of(0, 9, 44, 17)) {
			click(slot);
		}
		assertTrue(war.getAvailability().marks().isEmpty());
		wars.verify(() -> WarManager.persist(any()), never());

		Cache.warAvailabilityVoteUrl = "";
		view.open(player, war);
		assertEquals(Material.GRAY_STAINED_GLASS_PANE, top().getItem(35).getType());
		click(35);
		verify(player, times(1)).sendMessage(any(Component.class));
	}

	@Test
	void battleDaysAndDecidedDatesShowWhatCanStillChange() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false));
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI.plusDays(3), true).withHour(22, false));
		war.getAvailability().setLastDayLockProcessed(FRI.plusDays(1));
		war.getAvailability().setMarks(player.getUniqueId(), FRI.plusDays(1), Set.of(22));
		war.setBattleDay(FRI);
		view.open(player, war);
		Inventory grid = top();

		assertEquals(Material.GOLD_INGOT, grid.getItem(1).getType());
		assertEquals("⚔ Fri 20/11: battle day", name(grid.getItem(1)));
		assertTrue(lore(grid.getItem(1)).startsWith("Start time decided 19/11/2026 21:00 CET"));
		assertEquals(Material.GRAY_DYE, grid.getItem(2).getType());
		assertEquals("Sat 21/11: no battle", name(grid.getItem(2)));
		assertEquals("Not enough people could come at the same time.\nDecided. It can't change.", lore(grid.getItem(2)));
		assertEquals(Material.GREEN_STAINED_GLASS_PANE, grid.getItem(20).getType(), "Marked, but decided");
		assertEquals(Material.GRAY_STAINED_GLASS_PANE, grid.getItem(11).getType());
		assertEquals("Next battle: Fri 20/11, start time decided 19/11/2026 21:00 CET / 15:00 EST",
				lore(grid.getItem(8)).lines().findFirst().orElseThrow().replace("⚔ ", ""));

		click(11);
		verify(player).sendMessage("§cThat day is decided and can no longer change.");
		wars.verify(() -> WarManager.persist(any()), never());

		war.getAvailability().removeBattleDay(FRI);
		view.open(player, war);
		assertTrue(lore(top().getItem(8)).startsWith("⚔ Next battle: Mon 23/11, 22:00 CET / 16:00 EST"));
	}

	@Test
	void onlyThoseWhoFightCanOpenAndAnEndedWarClosesTheGrid() {
		Player outsider = fixture.player("Outsider");
		view.open(outsider, war);
		verify(outsider).sendMessage("§cOnly members of the warring factions and their hired mercenaries mark when they can fight.");
		view.open(player, null);
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		view.open(player, war);
		verify(player, times(2)).sendMessage("§cThis war has no battles to plan.");

		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		view.open(player, war);
		InventoryClickEvent below = fixture.ui.click(player, 60);
		view.click(below, top(), player);
		assertFalse(below.isCancelled(), "The player's own inventory is theirs");
		view.click(fixture.ui.click(player, 0), fixture.ui.inventory(null, 9, "Other"), player);

		wars.when(() -> WarManager.getById(7)).thenReturn(null);
		click(10);
		verify(player).sendMessage("§cWar not found.");
		verify(player).closeInventory();
		assertTrue(war.getAvailability().marks().isEmpty());
	}

	@Test
	void aShortWindowAndFewerDatesLeaveTheRestOfTheGridEmpty() {
		Cache.warBattleWindowEndHour = 24;
		Cache.warAvailabilityDaysShown = 3;
		Cache.warAvailabilityMaxDaysWithoutBattle = 0;
		Cache.warAvailabilityDayLockHours = 36;
		view.open(player, war);
		Inventory grid = top();

		assertEquals(Material.GRAY_STAINED_GLASS_PANE, grid.getItem(36).getType(), "Only three start times");
		assertEquals(Material.GRAY_STAINED_GLASS_PANE, grid.getItem(37).getType());
		assertEquals(Material.GRAY_STAINED_GLASS_PANE, grid.getItem(4).getType(), "Only three dates");
		assertFalse(lore(grid.getItem(17)).contains("without a battle"));
		assertTrue(lore(grid.getItem(17)).contains("It's decided 36 hours before."));
		click(37);
		click(4);
		assertTrue(war.getAvailability().marks().isEmpty());
	}

	@Test
	void theNeedReadsPlainly() {
		assertEquals("3 of each side", AvailabilityView.needText(3, 3));
		assertEquals("3 attackers and 1 defender", AvailabilityView.needText(3, 1));
		assertEquals("1 attacker and 2 defenders", AvailabilityView.needText(1, 2));
	}

	@Test
	void hoursReadAsDaysWhenTheyAreWholeDays() {
		assertEquals("1 day", AvailabilityView.hours(24));
		assertEquals("2 days", AvailabilityView.hours(48));
		assertEquals("30 hours", AvailabilityView.hours(30));
	}
}
