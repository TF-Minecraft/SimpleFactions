package net.tfminecraft.simplefactions.war.battle.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.StaffJoins;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleStaffService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;

class BattleStaffUiTest {
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<BattleStaffService> staff;
	private MockedStatic<BattlePersistenceService> persistence;
	private final List<Player> online = new ArrayList<>();
	private Battle battle;
	private BattleSide attacker;
	private BattleSide defender;
	private Player admin;
	private Player soldier;
	private boolean capEnabled;
	private int capMinutes;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.get().clear();
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
				.thenAnswer(call -> mock(BossBar.class));
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> online);
		staff = mockStatic(BattleStaffService.class);
		persistence = mockStatic(BattlePersistenceService.class);
		battle = new Battle("b1");
		battle.setBattleType(BattleType.FIELD);
		attacker = new BattleSide("attacker", LifeType.COLLECTIVE, 10);
		defender = new BattleSide("defender", LifeType.COLLECTIVE, 8);
		battle.addSide(attacker);
		battle.addSide(defender);
		soldier = player("Soldier", false, false);
		Warband band = mock(Warband.class);
		when(band.getPlayers()).thenReturn(new ArrayList<>(List.of(soldier)));
		attacker.addBand(band);
		BattleManager.addBattle(battle);
		admin = player("Admin", true, false);
		capEnabled = Cache.battleTimeCapEnabled;
		capMinutes = Cache.battleTimeCapMinutes;
	}

	@AfterEach
	void tearDown() {
		Cache.battleTimeCapEnabled = capEnabled;
		Cache.battleTimeCapMinutes = capMinutes;
		BattleWatchService.resetForTests();
		BattleManager.resetForTests();
		WarbandManager.get().clear();
		persistence.close();
		staff.close();
		bukkit.close();
	}

	private Player player(String name, boolean simplefactionsAdmin, boolean warbandsAdmin) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
		when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(simplefactionsAdmin);
		when(player.hasPermission(BattlePermissions.Permission_Admin)).thenReturn(warbandsAdmin);
		online.add(player);
		return player;
	}

	// --- watch ---

	@Test
	void watchingAddsTheStaffMemberToEverySideBarAndAgainStops() {
		battle.setStarted(true);

		assertTrue(BattleWatchService.toggle(admin, battle).startsWith("§aWatching b1"));
		assertEquals("b1", BattleWatchService.watching(admin.getUniqueId()));
		assertTrue(BattleWatchService.toggle(admin, battle).startsWith("§7Stopped watching b1"));
		assertNull(BattleWatchService.watching(admin.getUniqueId()));
		assertEquals("§7You are not watching a battle.", BattleWatchService.stop(admin));
	}

	@Test
	void watchingNeedsARunningNonRaidBattle() {
		assertTrue(BattleWatchService.toggle(admin, battle).contains("is not running"));
		battle.setStarted(true);
		battle.setBattleType(BattleType.RAID);
		assertTrue(BattleWatchService.toggle(admin, battle).contains("/battle info b1"));
	}

	@Test
	void stoppingKeepsAFightersBarAndCopesWithABattleThatIsGone() {
		battle.setStarted(true);
		BattleWatchService.toggle(soldier, battle);
		BattleWatchService.stop(soldier);
		BattleWatchService.toggle(admin, battle);
		BattleManager.resetForTests();
		assertEquals("§7Stopped watching b1.", BattleWatchService.stop(admin));
	}

	@Test
	void watchingAnotherBattleSwitchesAndEndingABattleForgetsItsWatchers() {
		battle.setStarted(true);
		Battle other = new Battle("b2");
		other.setStarted(true);
		BattleManager.addBattle(other);
		BattleWatchService.toggle(admin, battle);
		BattleWatchService.toggle(admin, other);
		assertEquals("b2", BattleWatchService.watching(admin.getUniqueId()));
		BattleWatchService.clearBattle("B2");
		assertNull(BattleWatchService.watching(admin.getUniqueId()));
	}

	// --- notices ---

	@Test
	void staffNoticesSkipTheActorAndNonStaff() {
		Player other = player("Other", false, true);
		BattleStaffNotices.toStaff("hi", "admin");
		verify(admin, never()).sendMessage("hi");
		verify(other).sendMessage("hi");
		verify(soldier, never()).sendMessage("hi");
	}

	@Test
	void startAndEndAreAnnouncedToStaff() {
		BattleStaffNotices notices = new BattleStaffNotices();
		notices.onStarted(new BattleStartedEvent("b1", BattleType.SIEGE, 3, Set.of(), Set.of()));
		verify(admin).sendMessage("§6[Battle] §eb1 §6started §7(siege)§6. §7Watch: §e/battle watch b1§7, details: §e/battle info b1");
		notices.onStarted(new BattleStartedEvent("gone", null, null, Set.of(), Set.of()));
		verify(admin).sendMessage(org.mockito.ArgumentMatchers.contains("§egone §6started §7(battle)"));

		Map<String, Integer> casualties = new LinkedHashMap<>();
		casualties.put("attacker", 3);
		casualties.put("defender", 5);
		notices.onEnded(ended("b1", "attacker", casualties));
		verify(admin).sendMessage("§6[Battle] §eb1 §6is over: §eattacker §6won§6, casualties §7attacker 3, defender 5§6.");
		notices.onEnded(ended("b1", null, Map.of()));
		verify(admin).sendMessage("§6[Battle] §eb1 §6is over: §6ended with no winner§6.");
		notices.onEnded(ended("b1", null, null));
	}

	private static BattleEndedEvent ended(String id, String winner, Map<String, Integer> casualties) {
		BattleEndedEvent event = mock(BattleEndedEvent.class);
		when(event.getBattleId()).thenReturn(id);
		when(event.hasWinner()).thenReturn(winner != null);
		when(event.getWinningSideId()).thenReturn(winner);
		when(event.getSideCasualties()).thenReturn(casualties);
		when(event.getEndReason()).thenReturn(BattleEndReason.TIMER);
		return event;
	}

	@Test
	void quittingStopsWatching() {
		battle.setStarted(true);
		BattleWatchService.toggle(admin, battle);
		BattleStaffNotices notices = new BattleStaffNotices();
		notices.onQuit(new PlayerQuitEvent(admin, (String) null));
		assertNull(BattleWatchService.watching(admin.getUniqueId()));
		notices.onQuit(new PlayerQuitEvent(admin, (String) null));
	}

	// --- info ---

	@Test
	void infoShowsARunningCampaignSiege() {
		Cache.battleTimeCapEnabled = true;
		Cache.battleTimeCapMinutes = 60;
		battle.setBattleType(BattleType.SIEGE);
		battle.setWarId(2);
		battle.setProvinceId(199);
		battle.setStarted(true);
		Instant now = Instant.parse("2026-11-08T10:00:00Z");
		battle.setStartedAt(now.minusSeconds(65));
		battle.setContestHoldRemainingSeconds(30);
		battle.setRecordedSideCasualty("attacker", 4);
		attacker.restoreLives(10, 10);
		battle.addPoint(new CapturePoint("A", null, attacker, 60));
		battle.addPoint(new CapturePoint("B", null, null, 0));

		List<String> lines = BattleInfoFormatter.lines(battle, now);

		assertEquals(List.of(
				"§6Battle §eb1 §7(b1, siege, war 2, province 199)",
				"§7State: §arunning for 1:05",
				"§7Time cap in: §e58:55",
				"§7Siege hold timer: §e30s",
				"§fattacker§7: §e10§7/§e10 §7lives, §e1§7/§e0 §7online, §e4 §7casualties",
				"§fdefender§7: §e8 §7lives, §e0§7/§e0 §7online, §e0 §7casualties",
				"§7Point §fA§7: §eattacker §760%",
				"§7Point §fB§7: §8nobody"), lines);
	}

	@Test
	void infoCoversBattlesWithoutTimers() {
		Instant now = Instant.now();
		assertEquals("§7State: §enot started", BattleInfoFormatter.lines(battle, now).get(1));
		battle.setStaffHold(true);
		assertEquals("§7State: §con hold by staff", BattleInfoFormatter.lines(battle, now).get(1));
		battle.setStarted(true);
		assertEquals("§7State: §arunning", BattleInfoFormatter.lines(battle, now).get(1));
		battle.setBattleType(null);
		assertTrue(BattleInfoFormatter.lines(battle, now).get(0).contains("(b1, battle)"));

		battle.setWarId(1);
		battle.setStartedAt(now);
		Cache.battleTimeCapEnabled = false;
		assertFalse(String.join("", BattleInfoFormatter.lines(battle, now)).contains("Time cap"));
		Cache.battleTimeCapEnabled = true;
		Cache.battleTimeCapMinutes = 0;
		assertFalse(String.join("", BattleInfoFormatter.lines(battle, now)).contains("Time cap"));
		Cache.battleTimeCapMinutes = 10;
		battle.setBattleType(BattleType.RAID);
		assertFalse(String.join("", BattleInfoFormatter.lines(battle, now)).contains("Time cap"));
		battle.setBattleType(BattleType.FIELD);
		assertTrue(String.join("", BattleInfoFormatter.lines(battle, now)).contains("Time cap in: §e10:00"));
		battle.setWarId(null);
		assertFalse(String.join("", BattleInfoFormatter.lines(battle, now)).contains("Time cap"));
		battle.setWarId(1);
		battle.setStartedAt(null);
		assertFalse(String.join("", BattleInfoFormatter.lines(battle, now)).contains("Time cap"));
		battle.setStarted(false);
		battle.setStartedAt(now);
		assertFalse(String.join("", BattleInfoFormatter.lines(battle, now)).contains("Time cap"));
	}

	// --- commands ---

	private boolean battleCommand(String... args) {
		return BattleStaffCommands.battle(admin, args);
	}

	@Test
	void unknownSubcommandsAreLeftToTheOldHandler() {
		assertFalse(battleCommand("edit", "b1"));
		assertFalse(BattleStaffCommands.warband(admin, new String[] {"forcekick", "w"}));
		assertFalse(BattleStaffCommands.warband(admin, new String[] {"toggleopen"}));
		assertFalse(BattleStaffCommands.warband(admin, new String[] {"list"}));
	}

	@Test
	void battleCommandsNeedAKnownBattle() {
		assertTrue(battleCommand("info"));
		verify(admin).sendMessage("§cUsage: /battle info <battleId>");
		assertTrue(battleCommand("info", "nope"));
		verify(admin).sendMessage("§cThere is no battle with the id nope");
	}

	@Test
	void watchAndInfoRunStraightAway() {
		battle.setStarted(true);
		battleCommand("watch", "b1");
		assertEquals("b1", BattleWatchService.watching(admin.getUniqueId()));
		battleCommand("watch", "off");
		assertNull(BattleWatchService.watching(admin.getUniqueId()));
		battleCommand("info", "b1");
		verify(admin).sendMessage("§6Battle §eb1 §7(b1, field)");
	}

	@Test
	void restartAbortAndEndAskFirst() {
		battleCommand("restart", "b1");
		verify(admin).sendMessage("§6This cannot be undone: §e" + BattleStaffCommands.RESTART_RISK);
		staff.verifyNoInteractions();
	}

	@Test
	void confirmedCommandsReachTheService() {
		staff.when(() -> BattleStaffService.restart(battle, "Admin")).thenReturn(new BattleStaffService.Result(true, "§aR"));
		staff.when(() -> BattleStaffService.abort(battle, "Admin")).thenReturn(new BattleStaffService.Result(true, "§aA"));
		staff.when(() -> BattleStaffService.end(battle, null, "Admin")).thenReturn(new BattleStaffService.Result(true, "§aN"));
		staff.when(() -> BattleStaffService.end(battle, "attacker", "Admin"))
				.thenReturn(new BattleStaffService.Result(false, "W"));
		staff.when(() -> BattleStaffService.hold(battle, true, "Admin")).thenReturn(new BattleStaffService.Result(true, "§aH"));
		staff.when(() -> BattleStaffService.hold(battle, false, "Admin")).thenReturn(new BattleStaffService.Result(true, "§aO"));
		staff.when(() -> BattleStaffService.adjustLives(battle, "attacker", "add", 2, "Admin"))
				.thenReturn(new BattleStaffService.Result(true, "§aL"));

		battleCommand("restart", "b1", "confirm");
		battleCommand("abort", "b1", "confirm");
		battleCommand("end", "b1", "confirm");
		battleCommand("end", "b1", "nowinner", "confirm");
		battleCommand("end", "b1", "attacker", "confirm");
		battleCommand("hold", "b1", "on");
		battleCommand("lives", "b1", "attacker", "add", "2");

		verify(admin).sendMessage("§aR");
		verify(admin).sendMessage("§aA");
		verify(admin, org.mockito.Mockito.times(2)).sendMessage("§aN");
		verify(admin).sendMessage("§cW");
		verify(admin).sendMessage("§aH");
		verify(admin).sendMessage("§aL");
	}

	@Test
	void holdAndLivesCheckTheirArguments() {
		staff.when(() -> BattleStaffService.hold(battle, false, "Admin")).thenReturn(new BattleStaffService.Result(true, "§aO"));
		battleCommand("hold", "b1");
		battleCommand("hold", "b1", "maybe");
		verify(admin, org.mockito.Mockito.times(2)).sendMessage("§cUsage: /battle hold <battleId> on|off");
		battleCommand("lives", "b1", "attacker");
		verify(admin).sendMessage("§cUsage: /battle lives <battleId> <sideId> add|remove|set <amount>");
		battleCommand("lives", "b1", "attacker", "add", "lots");
		verify(admin).sendMessage("§cThe amount must be a whole number.");
		battleCommand("hold", "b1", "off");
		staff.verify(() -> BattleStaffService.hold(battle, false, "Admin"));
	}

	@Test
	void takingASideToZeroLivesAsksFirst() {
		assertNull(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "attacker"}));
		assertNull(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "nobody", "set", "0"}));
		assertNull(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "attacker", "set", "x"}));
		assertNull(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "attacker", "set", "1"}));
		assertNull(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "attacker", "remove", "9"}));
		assertNull(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "attacker", "add", "0"}));
		assertTrue(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "attacker", "set", "0"})
				.startsWith("leaves attacker with no lives"));
		assertTrue(BattleStaffCommands.livesRisk(battle, new String[] {"lives", "b1", "attacker", "REMOVE", "10"}) != null);

		battleCommand("lives", "b1", "attacker", "set", "0");
		staff.verifyNoInteractions();
		staff.when(() -> BattleStaffService.adjustLives(battle, "attacker", "set", 0, "Admin"))
				.thenReturn(new BattleStaffService.Result(true, "§aZ"));
		battleCommand("lives", "b1", "attacker", "set", "0", "confirm");
		verify(admin).sendMessage("§aZ");
	}

	@Test
	void forcekickDuringARunningBattleAsksFirst() {
		Warband band = Warband.createWithMemberIds("w1", UUID.randomUUID(), false);
		WarbandManager.addWarband(band);
		Player lead = player("Lead", false, true);
		assertFalse(BattleStaffCommands.warband(lead, new String[] {"confirm"}));
		battle.setStarted(true);
		var context = new net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.CampaignBattleContext(
				battle, "attacker", mock(net.tfminecraft.simplefactions.war.core.War.class));
		try (MockedStatic<StaffJoins> joins = mockStatic(StaffJoins.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService> campaigns =
						mockStatic(net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.class)) {
			campaigns.when(() -> net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService
					.findCampaignBattleForWarband(band)).thenReturn(context);
			joins.when(() -> StaffJoins.warbandKick(band, "Bob")).thenReturn("kicked");
			BattleStaffCommands.warband(lead, new String[] {"forcekick", "w1", "Bob"});
			verify(lead).sendMessage(org.mockito.ArgumentMatchers.startsWith("§6This cannot be undone: §eremoves them"));
			joins.verifyNoInteractions();
			BattleStaffCommands.warband(lead, new String[] {"forcekick", "w1", "Bob", "confirm"});
			verify(lead).sendMessage("kicked");
			battle.setStarted(false);
			BattleStaffCommands.warband(lead, new String[] {"forcekick", "w1", "Bob"});
			verify(lead, org.mockito.Mockito.times(2)).sendMessage("kicked");
		}
	}

	@Test
	void usageCoversEveryTool() {
		assertEquals("§cUsage: /battle end <battleId> [nowinner|<sideId>]", BattleStaffCommands.usage("end"));
		assertEquals("§cUsage: /battle watch <battleId>|off", BattleStaffCommands.usage("watch"));
		assertEquals("§cUsage: /battle restart <battleId>", BattleStaffCommands.usage("restart"));
	}

	@Test
	void warbandToolsNeedStaffAndAWarband() {
		Warband band = mock(Warband.class);
		when(band.getId()).thenReturn("w1");
		WarbandManager.get().add(band);

		assertTrue(BattleStaffCommands.warband(soldier, new String[] {"toggleopen", "w1"}));
		verify(soldier).sendMessage("§a[Battle]§4You do not have access to this command");

		Player warbandStaff = player("Lead", false, true);
		assertTrue(BattleStaffCommands.warband(warbandStaff, new String[] {"toggleopen", "nope"}));
		verify(warbandStaff).sendMessage("§cWarband not found");
	}

	@Test
	void staffOpenOrCloseAnyWarbandAndKickFromIt() {
		Warband band = Warband.createWithMemberIds("w1", UUID.randomUUID(), false);
		WarbandManager.addWarband(band);
		Player lead = player("Lead", false, true);
		boolean locked = band.isLocked();

		BattleStaffCommands.warband(lead, new String[] {"toggleopen", "w1"});
		assertEquals(!locked, band.isLocked());
		BattleStaffCommands.warband(lead, new String[] {"toggleopen", "w1"});
		assertEquals(locked, band.isLocked());
		verify(lead).sendMessage("§cWarband w1 §cis now invite only");
		verify(lead).sendMessage("§aWarband w1 §ais now open");

		try (MockedStatic<StaffJoins> joins = mockStatic(StaffJoins.class)) {
			joins.when(() -> StaffJoins.warbandKick(band, "Sir Bob")).thenReturn("kicked");
			BattleStaffCommands.warband(lead, new String[] {"forcekick", "w1", "Sir", "Bob"});
			verify(lead).sendMessage("kicked");
		}
	}

	// --- tab completion ---

	@Test
	void staffCompletionsCoverTheNewTools() {
		Player admin = player("Lead", false, true);
		CommandSender nobody = soldier;
		assertNull(BattleTabCompletion.staffCompletions(nobody, "battle", new String[] {"info", ""}));
		assertNull(BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"info"}));
		assertNull(BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"edit", ""}));
		assertNull(BattleTabCompletion.staffCompletions(admin, "warband", new String[] {"join", ""}));
		assertNull(BattleTabCompletion.staffCompletions(admin, "war", new String[] {"info", ""}));
		assertEquals(List.of("b1"), BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"info", ""}));
		assertEquals(List.of("b1", "off"), BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"watch", ""}));
		assertEquals(List.of("attacker", "defender", "nowinner"),
				BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"end", "b1", ""}));
		assertEquals(List.of("nowinner"), BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"end", "x", ""}));
		assertEquals(List.of("on", "off"), BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"hold", "b1", ""}));
		assertEquals(List.of("attacker", "defender"),
				BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"lives", "b1", ""}));
		assertEquals(List.of("add", "remove", "set"),
				BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"lives", "b1", "attacker", ""}));
		assertEquals(List.of(), BattleTabCompletion.staffCompletions(admin, "battle", new String[] {"restart", "b1", ""}));
	}

	@Test
	void warbandStaffCompletionsListBandsAndMembers() {
		Player admin = player("Lead", false, true);
		Warband band = mock(Warband.class);
		when(band.getId()).thenReturn("w1");
		when(band.getPlayers()).thenReturn(new ArrayList<>(List.of(soldier)));
		WarbandManager.get().add(band);
		try (MockedStatic<Members> members = mockStatic(Members.class)) {
			members.when(() -> Members.name(soldier)).thenReturn("Soldier");
			members.when(() -> Members.completions(eq(List.of("Soldier")),
					org.mockito.AdditionalMatchers.aryEq(new String[] {"w1", ""}))).thenReturn(List.of("Soldier"));
			assertEquals(List.of("w1"), BattleTabCompletion.staffCompletions(admin, "warband", new String[] {"toggleopen", ""}));
			assertEquals(List.of("Soldier"),
					BattleTabCompletion.staffCompletions(admin, "warband", new String[] {"forcekick", "w1", ""}));
			assertEquals(List.of(), BattleTabCompletion.staffCompletions(admin, "warband", new String[] {"forcekick", "x", ""}));
			assertEquals(List.of(), BattleTabCompletion.staffCompletions(admin, "warband", new String[] {"toggleopen", "w1", ""}));
		}
	}
}
