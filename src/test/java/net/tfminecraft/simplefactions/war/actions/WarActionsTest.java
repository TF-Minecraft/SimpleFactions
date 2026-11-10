package net.tfminecraft.simplefactions.war.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.history.WarHistory;
import net.tfminecraft.simplefactions.war.history.WarHistoryAdmin;

/** Starting and stopping the website action poller, its switch and its status lines. */
class WarActionsTest {
	@TempDir
	Path dir;
	private FactionDomainFixture fixture;
	private Logger logger;
	private boolean previousEnabled;

	@BeforeEach
	void setup() {
		fixture = new FactionDomainFixture();
		logger = mock(Logger.class);
		previousEnabled = Cache.warActionsEnabled;
	}

	@AfterEach
	void close() {
		WarActions.resetForTests();
		WarHistory.resetForTests();
		Cache.warActionsEnabled = previousEnabled;
		fixture.close();
	}

	private Plugin plugin(Path folder) {
		Plugin plugin = mock(Plugin.class);
		when(plugin.getLogger()).thenReturn(logger);
		when(plugin.getDataFolder()).thenReturn(folder == null ? null : folder.toFile());
		return plugin;
	}

	@Test
	void startingLoadsTheLedgerAndPollsEveryFiveSecondsUntilStopped() throws Exception {
		Path folder = dir.resolve(WarHistory.FOLDER);
		Files.createDirectories(folder);
		Files.writeString(folder.resolve(WarActions.FILE), "{\"results\":[{\"id\":7,\"status\":\"applied\",\"reason\":null}]}");
		Plugin plugin = plugin(dir);
		BukkitTask task = mock(BukkitTask.class);
		when(fixture.ui.scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class),
				eq(WarActions.POLL_TICKS), eq(WarActions.POLL_TICKS))).thenReturn(task);

		WarActions.start(plugin);

		assertEquals(1, WarActions.status().remembered());
		assertEquals(0, WarActions.status().appliedThisRun());
		WarActions.stop();
		verify(task).cancel();
		assertNull(WarActions.status());
		WarActions.stop();
	}

	@Test
	void theTimerPollsTheWebsiteThroughTfmcWebOnlyWhileSwitchedOn() {
		Plugin plugin = plugin(dir);
		WarActions.start(plugin);
		var captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.ui.scheduler).runTaskTimerAsynchronously(eq(plugin), captor.capture(), anyLong(), anyLong());

		Cache.warActionsEnabled = false;
		captor.getValue().run();
		assertNull(WarActions.status().lastErrorAt(), "Off: nothing was asked");

		Cache.warActionsEnabled = true;
		captor.getValue().run();
		// No TFMCWeb in the tests, so there is no realm to ask for.
		assertTrue(WarActions.status().lastError().contains("no realm"));
		verify(logger).warning(startsWith("[WarActions] Could not fetch or answer website actions."));

		WarActions.resetForTests();
		assertDoesNotThrow(WarActions::poll);
		assertDoesNotThrow(() -> WarActions.warn("nobody listens"));
	}

	@Test
	void theProductionPollerReportsOnTheSchedulersAsyncThread() {
		Plugin plugin = plugin(dir);
		WarActions.start(plugin);
		WarActionPoller poller = pollerOf();
		poller.applyAll(List.of(new WarAction(1, "surrender", "w1-0", null, null)));
		verify(fixture.ui.scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
		fixture.ui.asyncTasks.remove(0).run();
		assertTrue(WarActions.status().lastError().startsWith(WarActionPoller.APPLIED_PATH + ": "),
				"Without TFMCWeb the report fails and is retried by the website");
		java.util.concurrent.Executor mainThread = fieldOf(poller, "mainThread");
		Runnable work = () -> {};
		mainThread.execute(work);
		verify(fixture.ui.scheduler).runTask(plugin, work);
	}

	@SuppressWarnings("unchecked")
	private static <T> T fieldOf(Object owner, String name) {
		try {
			var field = owner.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return (T) field.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static WarActionPoller pollerOf() {
		try {
			var field = WarActions.class.getDeclaredField("poller");
			field.setAccessible(true);
			return (WarActionPoller) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void withoutADataFolderNothingStarts() {
		WarActions.start(plugin(null));
		assertNull(WarActions.status());
		verify(fixture.ui.scheduler, never()).runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class),
				anyLong(), anyLong());
	}

	@Test
	void theHistoryStatusShowsTheWebsiteActions() {
		Player staff = mock(Player.class);
		Cache.warActionsEnabled = false;
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "status"});
		verify(staff).sendMessage("§6Website actions: §coff (war.history.actions_enabled)");

		Cache.warActionsEnabled = true;
		WarActions.startForTests(new WarActionPoller((method, path, json) -> null, () -> null,
				new WarActionLedger(dir.resolve("ledger.json"), message -> {}), new WarActionApplier(),
				Runnable::run, Runnable::run, () -> {}, message -> {}, java.time.Clock.fixed(java.time.Instant.EPOCH,
						java.time.ZoneOffset.UTC)), logger);
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "status"});
		verify(staff).sendMessage("§7Applied this run: §f0 §7(0 results remembered). Last fetch: §fnever this run");

		WarActions.poll();
		WarHistoryAdmin.handle(staff, new String[] {"admin", "history", "status"});
		verify(staff).sendMessage(startsWith("§7Last error: §cTFMCWeb reports no realm"));
	}
}
