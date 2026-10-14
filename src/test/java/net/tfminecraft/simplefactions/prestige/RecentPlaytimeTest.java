package net.tfminecraft.simplefactions.prestige;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;

class RecentPlaytimeTest {

	@TempDir
	File folder;

	private long day = 20_000L;
	private int previousWindow;

	@BeforeEach
	void setUp() {
		previousWindow = Cache.memberShareWindowDays;
		Cache.memberShareWindowDays = 30;
		RecentPlaytime.reset();
		RecentPlaytime.setToday(() -> day);
	}

	@AfterEach
	void tearDown() {
		Cache.memberShareWindowDays = previousWindow;
		RecentPlaytime.reset();
		NameKeyedMembers.install();
	}

	@Test
	void countsOnlyTheLastThirtyDays() {
		RecentPlaytime.credit("ada", 100);
		day += 29;
		RecentPlaytime.credit("ada", 20);
		RecentPlaytime.credit("ada", 5);
		assertEquals(125, RecentPlaytime.seconds("ada"), "Day one is still inside on day thirty");
		day += 1;
		assertEquals(25, RecentPlaytime.seconds("ada"));
		day += 30;
		assertEquals(0, RecentPlaytime.seconds("ada"));
	}

	@Test
	void ignoresNothingToCredit() {
		RecentPlaytime.credit(null, 60);
		RecentPlaytime.credit(" ", 60);
		RecentPlaytime.credit("ada", 0);
		assertEquals(0, RecentPlaytime.seconds("ada"));
		assertEquals(0, RecentPlaytime.seconds(null));
	}

	@Test
	void aDayCannotOverflow() {
		RecentPlaytime.credit("ada", Integer.MAX_VALUE);
		RecentPlaytime.credit("ada", 60);
		assertEquals(Integer.MAX_VALUE, RecentPlaytime.seconds("ada"));
	}

	@Test
	void savesAndLoadsWithoutExpiredDays() throws Exception {
		RecentPlaytime.bind(folder);
		RecentPlaytime.load();
		RecentPlaytime.credit("old", 60);
		day += 40;
		RecentPlaytime.credit("ada", 120);
		RecentPlaytime.save();

		String saved = Files.readString(new File(folder, "recent-playtime.json").toPath());
		assertTrue(saved.contains("ada"));
		assertFalse(saved.contains("old"), "Expired characters are dropped from the file");

		RecentPlaytime.reset();
		RecentPlaytime.setToday(() -> day);
		RecentPlaytime.bind(folder);
		RecentPlaytime.load();
		assertEquals(120, RecentPlaytime.seconds("ada"));
	}

	@Test
	void skipsEmptyEntriesInTheFile() throws Exception {
		Files.writeString(new File(folder, "recent-playtime.json").toPath(),
				"{\"characters\":{\"ada\":null,\"bea\":{\"" + day + "\":30}}}");
		RecentPlaytime.bind(folder);
		RecentPlaytime.load();
		assertEquals(30, RecentPlaytime.seconds("bea"));
		assertEquals(0, RecentPlaytime.seconds("ada"));

		Files.writeString(new File(folder, "recent-playtime.json").toPath(), "{}");
		RecentPlaytime.load();
		assertEquals(0, RecentPlaytime.seconds("bea"));
	}

	@Test
	void anUnreadableFileIsKeptAndNeverOverwritten() throws Exception {
		File file = new File(folder, "recent-playtime.json");
		Files.writeString(file.toPath(), "not json");
		RecentPlaytime.bind(folder);
		RecentPlaytime.load();
		RecentPlaytime.credit("ada", 60);
		RecentPlaytime.save();
		assertEquals("not json", Files.readString(file.toPath()));

		Files.writeString(file.toPath(), "null");
		RecentPlaytime.load();
		RecentPlaytime.save();
		assertEquals("null", Files.readString(file.toPath()), "A file with no state is unreadable too");
	}

	@Test
	void doesNotSaveBeforeLoadingOrWithoutAFile() throws Exception {
		RecentPlaytime.bind(folder);
		RecentPlaytime.credit("ada", 60);
		RecentPlaytime.save();
		assertFalse(new File(folder, "recent-playtime.json").exists());

		RecentPlaytime.bind(null);
		RecentPlaytime.load();
		RecentPlaytime.save();
		assertFalse(new File(folder, "recent-playtime.json").exists());
	}

	@Test
	void aFailedWriteIsLoggedAndKeepsTheCount() throws Exception {
		File blocker = new File(folder, "blocker");
		Files.writeString(blocker.toPath(), "file, not folder");
		RecentPlaytime.bind(new File(blocker, "Cache"));
		RecentPlaytime.load();
		RecentPlaytime.credit("ada", 60);
		RecentPlaytime.save();
		assertEquals(60, RecentPlaytime.seconds("ada"));
	}

	@Test
	void creditsTheCurrentCharacterOfEachOnlinePlayer() {
		try (var identity = new SharedIdentity()) {
			Player steve = identity.connect("Steve");
			CharacterRef ada = identity.character(steve.getUniqueId(), "Ada");
			identity.play(steve, ada);
			identity.connect("Alex");

			RecentPlaytime.creditOnline();
			RecentPlaytime.creditOnline();
			assertEquals(2 * RecentPlaytime.TICK_SECONDS, RecentPlaytime.seconds(SharedIdentity.key(ada)));
		}
	}

	@Test
	void startTicksAndAutosavesUntilStopped() throws Exception {
		try (var identity = new SharedIdentity()) {
			BukkitScheduler scheduler = mock(BukkitScheduler.class);
			List<Runnable> runs = new ArrayList<>();
			List<BukkitTask> tasks = new ArrayList<>();
			when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
				runs.add(call.getArgument(1));
				BukkitTask task = mock(BukkitTask.class);
				tasks.add(task);
				return task;
			});
			identity.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			Player steve = identity.connect("Steve");
			CharacterRef ada = identity.character(steve.getUniqueId(), "Ada");
			identity.play(steve, ada);
			File file = new File(folder, "recent-playtime.json");
			RecentPlaytime.bind(folder);
			RecentPlaytime.load();

			RecentPlaytime.start(identity.plugin);
			verify(scheduler).runTaskTimer(eq(identity.plugin), any(Runnable.class), eq(1200L), eq(1200L));
			verify(scheduler).runTaskTimer(eq(identity.plugin), any(Runnable.class), eq(6000L), eq(6000L));
			runs.get(1).run();
			assertFalse(file.exists(), "Nothing counted, nothing saved");
			runs.get(0).run();
			runs.get(1).run();
			assertTrue(Files.readString(file.toPath()).contains(SharedIdentity.key(ada)));

			RecentPlaytime.start(identity.plugin);
			verify(tasks.get(0)).cancel();
			verify(tasks.get(1)).cancel();
			RecentPlaytime.credit(SharedIdentity.key(ada), 60);
			file.delete();
			RecentPlaytime.stop();
			verify(tasks.get(2), times(1)).cancel();
			verify(tasks.get(3), times(1)).cancel();
			assertTrue(file.exists(), "Stopping saves");
		}
	}

	@Test
	void theRealClockIsTheUtcDay() {
		RecentPlaytime.setToday(null);
		RecentPlaytime.credit("ada", 60);
		assertEquals(60, RecentPlaytime.seconds("ada"));
	}
}
