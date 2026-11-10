package net.tfminecraft.simplefactions.war.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Kind;
import net.tfminecraft.simplefactions.war.stats.BattleRecord;
import net.tfminecraft.simplefactions.war.stats.BattleStatsArchive;
import net.tfminecraft.simplefactions.war.stats.FighterStats;

/** Starting and stopping war history, queueing battle records, and the staff commands. */
class WarHistoryTest {
	@TempDir
	Path dir;
	private FactionDomainFixture fixture;
	private Logger logger;
	private boolean previousEnabled;
	private final List<String> posted = new ArrayList<>();

	@BeforeEach
	void setup() {
		fixture = new FactionDomainFixture();
		BattleStatsArchive.resetForTests();
		previousEnabled = Cache.warHistoryEnabled;
		logger = mock(Logger.class);
	}

	@AfterEach
	void close() {
		WarHistory.resetForTests();
		BattleStatsArchive.resetForTests();
		Cache.warHistoryEnabled = previousEnabled;
		fixture.close();
	}

	private WarHistoryOutbox startInTemp() {
		WarHistoryOutbox outbox = new WarHistoryOutbox(dir.resolve("outbox.jsonl"), message -> {});
		WarHistory.startForTests(outbox, new WarHistorySender(outbox, (path, json) -> {
			posted.add(path);
			return GatewayClient.Result.fail("website down");
		}, () -> "main", message -> {}, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)), logger);
		return outbox;
	}

	@Test
	void anEventBeforeHistoryStartsIsDropped() {
		WarHistory.event(new net.tfminecraft.simplefactions.war.core.War(1,
				fixture.saved("attacker", "Alice"), fixture.saved("defender", "Bob")), "war_declared",
				new com.google.gson.JsonObject());

		assertFalse(WarHistory.isRecording());
	}

	private static BattleRecord record(String id, String warKey) {
		BattleRecord record = new BattleRecord();
		record.id = id;
		record.battleId = "w1-field-7";
		record.warKey = warKey;
		record.provinceId = 7;
		FighterStats fighter = new FighterStats();
		fighter.key = "character-1";
		fighter.factionId = "aureate";
		fighter.factionFoundedAt = 1730000000L;
		fighter.hirerFactionId = "malestroit";
		record.fighters.add(fighter);
		return record;
	}

	@Test
	void startingLoadsTheOutboxAndSchedulesAnAsyncSenderThatStopCancels() throws Exception {
		Plugin plugin = mock(Plugin.class);
		when(plugin.getLogger()).thenReturn(logger);
		when(plugin.getDataFolder()).thenReturn(dir.toFile());
		Files.createDirectories(dir.resolve(WarHistory.FOLDER));
		Files.writeString(dir.resolve(WarHistory.FOLDER).resolve(WarHistory.FILE),
				"{\"kind\":\"event\",\"id\":\"left\",\"item\":{}}\n");
		BukkitTask task = mock(BukkitTask.class);
		when(fixture.ui.scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class),
				eq(WarHistory.SEND_TICKS), eq(WarHistory.SEND_TICKS))).thenReturn(task);

		WarHistory.start(plugin);

		assertTrue(WarHistory.isRecording());
		assertEquals(1, WarHistory.status().events(), "What the last run left is still waiting");
		WarHistory.stop();
		verify(task).cancel();
		assertFalse(WarHistory.isRecording());
		assertNull(WarHistory.status());
	}

	@Test
	void withoutADataFolderNothingIsRecorded() {
		Plugin plugin = mock(Plugin.class);
		when(plugin.getLogger()).thenReturn(logger);

		WarHistory.start(plugin);

		assertFalse(WarHistory.isRecording());
		verify(fixture.ui.scheduler, never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
				anyLong(), anyLong());
	}

	@Test
	void sendingOnlyHappensWhileHistoryIsOn() {
		WarHistoryOutbox outbox = startInTemp();
		WarHistory.battle(record("r1", "w1-1"));

		Cache.warHistoryEnabled = false;
		WarHistory.flush();
		assertTrue(posted.isEmpty());
		assertFalse(WarHistory.battle(record("r2", "w1-1")), "Off: nothing new is queued");

		Cache.warHistoryEnabled = true;
		WarHistory.flush();
		assertEquals(List.of(WarHistorySender.BATTLES_PATH), posted);
		assertEquals(1, outbox.size(), "The failed send kept the record");

		WarHistory.resetForTests();
		assertDoesNotThrow(WarHistory::flush);
	}

	@Test
	void battleRecordsAreQueuedAsSimpleFactionsSavesThemButOnlyForWars() {
		WarHistoryOutbox outbox = startInTemp();

		assertTrue(WarHistory.battle(record("r1", "w1-1")));
		assertFalse(WarHistory.battle(record("r1", "w1-1")), "Already waiting");
		assertFalse(WarHistory.battle(record("arena", null)), "A battle outside a war is not war history");
		assertFalse(WarHistory.battle(record(null, "w1-1")));
		assertFalse(WarHistory.battle(null));

		var json = JsonParser.parseString(outbox.peek(Kind.BATTLE, 1).get(0).json()).getAsJsonObject();
		assertEquals("w1-field-7", json.get("battleId").getAsString());
		assertEquals(7, json.get("provinceId").getAsInt());
		var fighter = json.getAsJsonArray("fighters").get(0).getAsJsonObject();
		assertEquals("aureate", fighter.get("factionId").getAsString());
		assertEquals(1730000000L, fighter.get("factionFoundedAt").getAsLong());
		assertEquals("malestroit", fighter.get("hirerFactionId").getAsString());
	}

	@Test
	void anEventAfterHistoryStoppedIsDropped() {
		// History can stop between the recording check and the append; that must not throw.
		assertDoesNotThrow(() -> WarHistory.event(mock(net.tfminecraft.simplefactions.war.core.War.class),
				"war_declared", new com.google.gson.JsonObject()));
		assertNull(WarHistory.status());
	}

	@Test
	void aBattleThatCannotBeQueuedIsLogged() {
		WarHistoryOutbox broken = mock(WarHistoryOutbox.class);
		when(broken.append(any(), any(), any())).thenThrow(new IllegalStateException("disk gone"));
		WarHistory.startForTests(broken, null, logger);

		assertFalse(WarHistory.battle(record("r1", "w1-1")));

		verify(logger).warning("[WarHistory] Could not queue battle record r1: java.lang.IllegalStateException: disk gone");
		WarHistory.resetForTests();
		assertDoesNotThrow(() -> WarHistory.warn("nobody listens"));
	}

	@Test
	void savingABattleQueuesItAndTheArchiveCanBeQueuedAgain() {
		when(fixture.ui.plugin.getDataFolder()).thenReturn(dir.toFile());
		WarHistoryOutbox outbox = startInTemp();
		BattleStatsArchive.save(record("r1", "w1-1"));
		BattleStatsArchive.save(record("r2", "w1-1"));
		BattleStatsArchive.save(record("arena", null));
		assertEquals(2, outbox.size(Kind.BATTLE));
		assertEquals("r2", BattleStatsArchive.latestRecordId("w1-field-7", "w1-1"));
		assertNull(BattleStatsArchive.latestRecordId("w1-field-7", "w2-1"), "Battle ids repeat across wars");

		outbox.remove(Kind.BATTLE, List.of("r1", "r2"));
		List<Integer> done = new ArrayList<>();
		WarHistory.queueArchive(fixture.ui.plugin, done::add);
		fixture.ui.asyncTasks.remove(0).run();
		fixture.ui.tasks.remove(0).run();

		assertEquals(List.of(2), done);
		assertEquals(2, outbox.size(Kind.BATTLE));
	}

	@Test
	void staffStatusShowsWhatWaitsAndTheLastError() {
		Player staff = fixture.player("Staff");
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "status"});
		verify(staff).sendMessage("§7Not started.");

		WarHistoryOutbox outbox = startInTemp();
		WarHistory.battle(record("r1", "w1-1"));
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "STATUS"});
		verify(staff, times(2)).sendMessage("§6War history: §aon");
		verify(staff).sendMessage("§7Waiting to send: §f0 §7events, §f1 §7battle records.");
		verify(staff).sendMessage("§7Last sent: §fnever this run");

		WarHistory.flush();
		Cache.warHistoryEnabled = false;
		List<String> lines = WarHistoryAdmin.statusLines();
		assertEquals("§6War history: §coff (war.history.enabled)", lines.get(0));
		assertEquals("§7Last error: §c/wars/plugin/battles: website down §7at §f" + Instant.EPOCH, lines.get(3));
		assertEquals(1, outbox.size());

		WarHistoryAdmin.handle(staff, new String[] {"admin", "history"});
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "status", "extra"});
		verify(staff, times(2)).sendMessage(WarHistoryAdmin.USAGE);
	}

	@Test
	void aSuccessfulSendShowsWhenItHappened() {
		WarHistoryOutbox outbox = new WarHistoryOutbox(dir.resolve("outbox.jsonl"), message -> {});
		WarHistory.startForTests(outbox, new WarHistorySender(outbox,
				(path, json) -> GatewayClient.Result.success("{\"acked\":[\"r1\"]}"), () -> "main", message -> {},
				Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)), logger);
		WarHistory.battle(record("r1", "w1-1"));

		WarHistory.flush();

		assertEquals("§7Last sent: §f" + Instant.EPOCH, WarHistoryAdmin.statusLines().get(2));
		List<String> lines = WarHistoryAdmin.statusLines();
		assertEquals("§6Website actions: §aon", lines.get(3), "No error line before the website actions");
	}

	@Test
	void staffUploadQueuesTheArchiveOnlyWhileRecording() {
		Player staff = fixture.player("Staff");
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "upload-battles"});
		verify(staff).sendMessage("§cWar history is off (war.history.enabled), so nothing can be queued.");

		when(fixture.ui.plugin.getDataFolder()).thenReturn(dir.toFile());
		startInTemp();
		BattleStatsArchive.save(record("r1", "w1-1"));
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "upload-battles"});
		verify(staff).sendMessage("§7Queueing every archived war battle...");
		fixture.ui.asyncTasks.remove(0).run();
		fixture.ui.tasks.remove(0).run();
		verify(staff).sendMessage("§aQueued §f0 §abattle records for the website. Ones already waiting are not queued twice.");
	}
}
