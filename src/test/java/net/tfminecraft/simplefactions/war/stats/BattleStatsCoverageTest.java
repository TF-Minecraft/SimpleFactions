package net.tfminecraft.simplefactions.war.stats;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.core.War;

class BattleStatsCoverageTest {
	@TempDir
	Path dir;
	private FactionDomainFixture fixture;
	private BattleStatsListener listener;
	private Player alice;
	private Player carol;
	private Player bob;
	private Player outsider;
	private Battle battle;

	@BeforeEach
	void setup() {
		fixture = new FactionDomainFixture();
		BattleManager.resetForTests();
		BattleStatsArchive.resetForTests();
		when(fixture.ui.plugin.getDataFolder()).thenReturn(dir.toFile());
		when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenAnswer(call -> mock(BossBar.class));
		alice = fighter("Alice");
		carol = fighter("Carol");
		bob = fighter("Bob");
		outsider = fighter("Outsider");
		listener = new BattleStatsListener();
		battle = BattleFactory.createBlank(BattleType.RAID, "Test Battle");
		Warband attackers = new Warband("atk", alice);
		attackers.addPlayer(carol);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(attackers);
		battle.getSideById(BattleTemplate.DEFENDER_SIDE).addBand(new Warband("def", bob));
		BattleManager.addBattle(battle);
		assertNull(battle.start());
	}

	@AfterEach
	void close() {
		BattleManager.resetForTests();
		BattleStatsArchive.resetForTests();
		fixture.close();
	}

	private Player fighter(String name) {
		Player player = fixture.player(name);
		when(player.getHealth()).thenReturn(20.0);
		when(player.getMaximumNoDamageTicks()).thenReturn(20);
		return player;
	}

	private void hit(org.bukkit.entity.Entity victim, Object damager, double damage, boolean cancelled) {
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getEntity()).thenReturn(victim);
		when(event.getDamager()).thenReturn((org.bukkit.entity.Entity) damager);
		when(event.getFinalDamage()).thenReturn(damage);
		when(event.isCancelled()).thenReturn(cancelled);
		listener.onDamage(event);
	}

	private void die(Player victim, Player killer) {
		when(victim.getKiller()).thenReturn(killer);
		PlayerDeathEvent event = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(victim);
		listener.onDeath(event);
	}

	private void quit(Player player) {
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);
		listener.onQuit(event);
	}

	private FighterStats live(String key) {
		return battle.getStats().fighters.get(key);
	}

	private List<String> said(Player player) {
		List<String> lines = new ArrayList<>();
		for (Invocation call : mockingDetails(player).getInvocations()) {
			if (!call.getMethod().getName().equals("sendMessage") || call.getArguments().length != 1) continue;
			Object line = call.getArgument(0);
			lines.add(line instanceof Component component
					? LegacyComponentSerializer.legacySection().serialize(component)
					: String.valueOf(line));
		}
		clearInvocations(player);
		return lines;
	}

	private static boolean has(List<String> lines, String part) {
		return lines.stream().anyMatch(line -> line.contains(part));
	}

	private BattleRecord end(String winner) {
		BattleEndedEvent event = new BattleEndedEvent(battle.getId(), BattleType.RAID, null, winner, Map.of(),
				Set.of(), BattleEndReason.SIDE_WIN, false, false);
		listener.onEnded(event);
		List<BattleStatsArchive.Summary> all = BattleStatsArchive.list(null);
		return all.isEmpty() ? null : BattleStatsArchive.get(all.get(0).id());
	}

	@Test
	void hitsSplitEnemyAndFriendlyDamageAndCountBlockedHits() {
		Zombie zombie = mock(Zombie.class);
		hit(alice, zombie, 5, false);
		hit(alice, alice, 5, false);
		hit(mock(Zombie.class), alice, 5, false);
		hit(bob, outsider, 5, false);
		Arrow arrow = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(alice);
		hit(bob, arrow, 4, false);
		Arrow wild = mock(Arrow.class);
		when(wild.getShooter()).thenReturn(zombie);
		hit(bob, wild, 4, false);
		hit(bob, alice, 50, false);
		hit(carol, alice, 3, false);
		hit(bob, alice, 6, true);
		hit(carol, alice, 6, true);
		when(bob.getNoDamageTicks()).thenReturn(15);
		hit(bob, alice, 6, true);
		hit(bob, alice, Double.NaN, false);
		hit(bob, alice, 0, false);
		when(bob.getHealth()).thenReturn(Double.POSITIVE_INFINITY);
		hit(bob, alice, Double.POSITIVE_INFINITY, false);

		FighterStats a = live("Alice");
		assertEquals(24, a.damage, 1e-9);
		assertEquals(3, a.friendlyDamage, 1e-9);
		assertEquals(24, live("Bob").damageTaken, 1e-9);
		assertEquals(1, live("Bob").blockedHits);
		assertNull(live("Carol"));
		assertEquals("attacker", a.side);
	}

	@Test
	void deathsCreditTheKillerOrTheLastEnemyHitAndCountTeamKills() {
		die(outsider, null);
		die(bob, alice);
		die(carol, alice);
		hit(bob, alice, 2, false);
		die(bob, null);
		die(alice, null);
		die(bob, outsider);
		hit(carol, bob, 1, false);
		battle.getStats().takeLastHitter(carol.getUniqueId(), System.currentTimeMillis() + 60_000);
		die(carol, null);
		die(bob, bob);
		Battle idle = BattleFactory.createBlank(BattleType.RAID, "idle");
		BattleManager.get().add(0, idle);
		quit(outsider);
		quit(carol);
		Player nameless = fixture.player("Nameless");
		when(nameless.getName()).thenReturn(null);
		quit(nameless);
		var session = net.tfminecraft.simplefactions.identity.Members.session(carol).orElseThrow();
		listener.onSessionEnd(new net.tfminecraft.tlibs.character.CharacterSessionEndEvent(carol, session,
				net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason.SWITCH));
		listener.onSessionEnd(new net.tfminecraft.tlibs.character.CharacterSessionEndEvent(carol, session,
				net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason.QUIT));
		var stranger = new net.tfminecraft.tlibs.character.CharacterRef(carol.getUniqueId(), java.util.UUID.randomUUID());
		listener.onSessionEnd(new net.tfminecraft.tlibs.character.CharacterSessionEndEvent(carol,
				new net.tfminecraft.tlibs.character.CharacterSession(stranger, 9L),
				net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason.SWITCH));

		assertEquals(2, live("Alice").kills);
		assertEquals(1, live("Alice").teamKills);
		assertEquals(4, live("Bob").deaths);
		assertEquals(2, live("Carol").deaths);
		assertEquals(2, live("Carol").logouts);
		assertNull(battle.getStats().fighter(alice, null));
	}

	@Test
	void finishArchivesOnceTellsFightersAndRanksThem() {
		hit(bob, alice, 9, false);
		die(bob, alice);
		hit(carol, bob, 4, false);
		quit(carol);
		listener.onEnded(new BattleEndedEvent("missing", BattleType.RAID, null, null, Map.of(), Set.of(),
				BattleEndReason.TIMER, false, false));
		BattleRecord record = end("attacker");
		assertNotNull(record);
		assertNull(battle.getStats());
		assertNull(end("attacker").fighter("Nobody"));
		assertEquals(1, BattleStatsArchive.list(null).size());
		assertTrue(record.id.startsWith("test_battle-"));
		assertEquals(3, record.fighters.size());

		List<String> lines = said(alice);
		assertTrue(has(lines, "Winner: §eattacker"), lines.toString());
		assertTrue(has(lines, "Kills §e1 §7(#1 of 3)"), lines.toString());
		assertTrue(has(lines, "Battle history"), lines.toString());
		assertTrue(has(said(bob), "Damage §e4 §7(#2 of 3)"));
		assertTrue(Files.exists(dir.resolve(BattleStatsArchive.FOLDER).resolve(record.id + ".json")));
	}

	@Test
	void liveStatsSurviveTheBattleSave() {
		hit(bob, alice, 7, false);
		BattleData data = BattleMapper.toData(battle);
		String json = JsonUtil.GSON.toJson(data);
		Battle loaded = BattleMapper.fromData(JsonUtil.GSON.fromJson(json, BattleData.class));
		assertEquals(7, loaded.getStats().fighters.get("Alice").damage, 1e-9);
		assertNull(loaded.getStats().takeLastHitter(bob.getUniqueId(), 0));
		Battle fresh = new Battle("fresh");
		assertNull(fresh.getStats());
		assertNotNull(fresh.stats());
		assertSame(fresh.stats(), fresh.takeStats());
		assertNull(BattleStatsListener.finish(fresh, null, "TIMER"));
	}

	@Test
	void recordsCarryTheWarAndFallBackWhenItsLeaderIsMissing() {
		War war = mock(War.class);
		when(war.getId()).thenReturn(4);
		when(war.getStartedAt()).thenReturn(Instant.ofEpochSecond(1000));
		when(war.getName()).thenReturn("North vs. South");
		WarManager.get().add(war);
		battle.setWarId(4);
		battle.setNavalVariant(true);
		BattleRecord record = BattleRecord.of(battle, new BattleStats(), " ", "TIMER", Instant.ofEpochMilli(5));
		assertEquals("w4-1000", record.warKey);
		assertEquals("North vs. South", record.warName);
		assertNull(record.winner);

		when(war.getStartedAt()).thenReturn(null);
		when(war.getName()).thenThrow(new NullPointerException("no leader"));
		battle.setStartedAt(null);
		battle.setBattleType(null);
		record = BattleRecord.of(battle, new BattleStats(), null, null, Instant.ofEpochMilli(5));
		assertEquals("w4-0", record.warKey);
		assertEquals("War 4", record.warName);
		assertEquals(5, record.startedAt);
		battle.setWarId(99);
		assertNull(BattleRecord.of(battle, new BattleStats(), null, null, Instant.now()).warKey);
		assertEquals("battle", BattleRecord.safeId(null));
		assertEquals("a_b-1", BattleRecord.safeId("A b-1"));
	}

	@Test
	void fighterClassComesFromMmoCoreWhenItIsEnabled() {
		assertNull(FighterClass.of(alice));
		when(Bukkit.getPluginManager().isPluginEnabled("MMOCore")).thenReturn(true);
		try (MockedStatic<PlayerData> mmo = mockStatic(PlayerData.class)) {
			PlayerData data = mock(PlayerData.class);
			PlayerClass mage = mock(PlayerClass.class);
			when(mage.getName()).thenReturn("Mage");
			when(data.getProfess()).thenReturn(mage);
			mmo.when(() -> PlayerData.get(alice)).thenReturn(data);
			mmo.when(() -> PlayerData.get(bob)).thenReturn(null);
			mmo.when(() -> PlayerData.get(carol)).thenThrow(new IllegalStateException("not loaded"));
			assertEquals("Mage", FighterClass.of(alice));
			assertNull(FighterClass.of(bob));
			assertNull(FighterClass.of(carol));
		}
	}

	@Test
	void archiveLoadsSkipsBadFilesAndReadsFromDisk() throws Exception {
		Logger logger = mock(Logger.class);
		when(fixture.ui.plugin.getLogger()).thenReturn(logger);
		BattleStatsArchive.loadNow();
		hit(bob, alice, 1, false);
		BattleRecord record = end(null);
		File folder = dir.resolve(BattleStatsArchive.FOLDER).toFile();
		Files.writeString(folder.toPath().resolve("broken.json"), "{not json");
		Files.writeString(folder.toPath().resolve("empty.json"), "null");
		Files.writeString(folder.toPath().resolve("notes.txt"), "ignored");

		BattleStatsArchive.resetForTests();
		BattleStatsArchive.loadAsync();
		fixture.ui.runAsyncTasks();
		BattleStatsArchive.loadNow();
		assertEquals(1, BattleStatsArchive.list(null).size());
		assertEquals(1, BattleStatsArchive.list("Alice").size());
		assertTrue(BattleStatsArchive.list("Nobody").isEmpty());
		assertEquals(record.id, BattleStatsArchive.get(record.id).id);
		assertSame(BattleStatsArchive.get(record.id), BattleStatsArchive.get(record.id));
		assertNull(BattleStatsArchive.get(null));
		assertNull(BattleStatsArchive.get("../escape"));
		assertNull(BattleStatsArchive.get("unknown"));

		BattleStatsArchive.resetForTests();
		BattleStatsArchive.loadNow();
		Files.writeString(folder.toPath().resolve(record.id + ".json"), "null");
		assertNull(BattleStatsArchive.get(record.id));
		Files.delete(folder.toPath().resolve(record.id + ".json"));
		assertNull(BattleStatsArchive.get(record.id));

		Files.delete(folder.toPath().resolve("broken.json"));
		Files.delete(folder.toPath().resolve("empty.json"));
		Files.delete(folder.toPath().resolve("notes.txt"));
		Files.delete(folder.toPath());
		Files.writeString(folder.toPath(), "a file where the folder should be");
		BattleStatsArchive.save(record);
		when(fixture.ui.plugin.getDataFolder()).thenReturn(null);
		BattleStatsArchive.save(record);
		BattleStatsArchive.loadNow();
		verify(logger, atLeast(3)).log(eq(java.util.logging.Level.WARNING), anyString(), any(Exception.class));
	}

	@Test
	void commandListsReportsAndWarsForPlayersAndStaff() {
		War war = mock(War.class);
		when(war.getId()).thenReturn(2);
		when(war.getName()).thenReturn("Red vs. Blue");
		WarManager.get().add(war);
		battle.setWarId(2);
		hit(bob, alice, 9, false);
		die(bob, alice);
		BattleRecord first = end("attacker");
		assertNull(battle.start());
		battle.setWarId(null);
		hit(alice, bob, 2, false);
		BattleRecord second = BattleStatsListener.finish(battle, null, "WAR_ENDED");
		said(alice);
		said(bob);

		Player nameless = fixture.player("Nameless");
		when(nameless.getName()).thenReturn(null);
		BattleStatsView.command(nameless, new String[] {"stats"});
		assertTrue(has(said(nameless), "Choose a character"));

		org.bukkit.command.Command battleCommand = mock(org.bukkit.command.Command.class);
		when(battleCommand.getName()).thenReturn("battle");
		assertTrue(new net.tfminecraft.simplefactions.war.battle.ui.BattleCommandManager()
				.onCommand(outsider, battleCommand, "battle", new String[] {"stats"}));
		assertTrue(has(said(outsider), "No battle stats yet"));
		BattleStatsView.command(outsider, new String[] {"stats", "war"});
		assertTrue(has(said(outsider), "No war battles yet"));
		BattleStatsView.command(outsider, new String[] {"stats", "war", first.warKey});
		assertTrue(has(said(outsider), "No battle stats for that war"));
		BattleStatsView.command(outsider, new String[] {"stats", first.id});
		assertTrue(has(said(outsider), "No battle stats with that ID"));
		BattleStatsView.command(alice, new String[] {"stats", "nope"});
		assertTrue(has(said(alice), "No battle stats with that ID"));

		BattleStatsView.command(alice, new String[] {"stats"});
		List<String> lines = said(alice);
		assertTrue(has(lines, "Battle history §7(page 1 of 1"), lines.toString());
		assertTrue(has(lines, "won by attacker"));
		assertTrue(has(lines, "no winner"));
		BattleStatsView.command(alice, new String[] {"stats", "5"});
		assertTrue(has(said(alice), "page 1 of 1"));

		BattleStatsView.command(alice, new String[] {"stats", second.id.toUpperCase()});
		lines = said(alice);
		assertTrue(has(lines, "No winner §8(the war ended)"), lines.toString());
		assertFalse(has(lines, "Fighters"));

		BattleStatsView.command(alice, new String[] {"stats", "war"});
		assertTrue(has(said(alice), "Red vs. Blue"));
		BattleStatsView.command(alice, new String[] {"stats", "war", first.warKey});
		lines = said(alice);
		assertTrue(has(lines, "§7Wins: §eattacker §f1"), lines.toString());
		assertTrue(has(lines, "in this war: §fKills §e1 §7(#1 of 3)"), lines.toString());

		when(outsider.hasPermission("warbands.admin")).thenReturn(true);
		BattleStatsView.command(outsider, new String[] {"stats", first.id});
		lines = said(outsider);
		assertTrue(has(lines, "Fighters"), lines.toString());
		assertTrue(has(lines, "§fAlice §7attacker §e1/0/0 §f9"), lines.toString());
		assertTrue(has(lines, "No class §73 §e100%"), lines.toString());
		assertTrue(has(lines, "War: §fRed vs. Blue"));
		BattleStatsView.command(outsider, new String[] {"stats", "war", first.warKey});
		lines = said(outsider);
		assertFalse(has(lines, "in this war"));
		assertTrue(has(lines, "Wins:"));
	}

	@Test
	void staffDetailShowsBlockedHitsLogoutsClassesAndPaging() {
		when(alice.hasPermission("warbands.admin")).thenReturn(true);
		BattleRecord record = new BattleRecord();
		record.id = "manual-1";
		record.name = "Manual";
		record.type = "FIELD";
		record.raid = true;
		record.naval = true;
		record.endReason = "RETREAT";
		record.winner = "defender";
		FighterStats one = new FighterStats();
		one.key = "Alice";
		one.name = "Alice";
		one.side = "attacker";
		one.className = "Mage";
		one.faction = "Aldmark";
		one.blockedHits = 2;
		one.logouts = 1;
		record.fighters.add(one);
		BattleRecord.SideRecord side = new BattleRecord.SideRecord();
		side.id = "attacker";
		side.maxLives = 10;
		side.lives = 4;
		record.sides.add(side);
		BattleStatsArchive.save(record);
		BattleRecord twin = new BattleRecord();
		twin.id = "manual-1";
		twin.name = "Twin";
		BattleStatsArchive.save(twin);
		assertEquals("manual-1-2", twin.id);
		BattleStatsView.command(alice, new String[] {"stats", "manual-1"});
		List<String> lines = said(alice);
		assertTrue(has(lines, "Naval field raid"), lines.toString());
		assertTrue(has(lines, "retreat"));
		assertTrue(has(lines, "blocked 2"));
		assertTrue(has(lines, "logouts 1"));
		assertTrue(has(lines, "Mage §71 §e0%"));
		assertTrue(has(lines, "lives 4/10"));
		assertTrue(has(lines, "(Aldmark"));
		BattleStatsView.command(alice, new String[] {"stats", "war", "w1-1"});
		assertTrue(has(said(alice), "No battle stats for that war"));

		for (int i = 0; i < BattleStatsView.PAGE_SIZE - 1; i++) {
			BattleRecord extra = new BattleRecord();
			extra.id = "extra-" + i;
			extra.name = "Extra " + i;
			extra.warName = "Some war";
			extra.endedAt = i;
			BattleStatsArchive.save(extra);
		}
		BattleStatsView.command(alice, new String[] {"stats", "1"});
		assertTrue(has(said(alice), "Next page"));
		BattleStatsView.command(alice, new String[] {"stats", "2"});
		assertTrue(has(said(alice), "page 2 of 2"));
	}

	@Test
	void formatsDurationsReasonsAndRanks() {
		assertEquals("0m", BattleStatsView.duration(-5));
		assertEquals("59m", BattleStatsView.duration(59 * 60_000L));
		assertEquals("1h 57m", BattleStatsView.duration(117 * 60_000L));
		BattleRecord record = new BattleRecord();
		record.id = "r";
		record.name = "R";
		for (String reason : new String[] {"SIDE_WIN", "TIMER", "OTHER"}) {
			record.endReason = reason;
			BattleStatsView.report(alice, record, null, false);
		}
		List<String> lines = said(alice);
		assertTrue(has(lines, "side won"));
		assertTrue(has(lines, "time ran out"));
		assertTrue(has(lines, "(ended)"));
		FighterStats low = new FighterStats();
		FighterStats high = new FighterStats();
		high.kills = 3;
		FighterStats tie = new FighterStats();
		tie.kills = 3;
		List<FighterStats> all = List.of(low, high, tie);
		assertEquals(3, BattleStatsView.rank(all, low, f -> f.kills));
		assertEquals(1, BattleStatsView.rank(all, tie, f -> f.kills));
	}
}
