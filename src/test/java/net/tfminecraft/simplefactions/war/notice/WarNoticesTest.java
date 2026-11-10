package net.tfminecraft.simplefactions.war.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.JsonUtil;

class WarNoticesTest {
	private static final String OFFLINE = "0a1b2c3d-offline";
	private static final String ONLINE = "0a1b2c3d-online";

	@TempDir
	Path data;

	private SimpleFactions previousPlugin;
	private int previousKeepDays;
	private Logger logger;
	private Player player;
	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		previousPlugin = SimpleFactions.plugin;
		previousKeepDays = Cache.warNoticeKeepDays;
		Cache.warNoticeKeepDays = 14;
		SimpleFactions plugin = mock(SimpleFactions.class);
		logger = mock(Logger.class);
		when(plugin.getDataFolder()).thenReturn(data.toFile());
		when(plugin.getLogger()).thenReturn(logger);
		SimpleFactions.plugin = plugin;
		player = mock(Player.class);
		when(player.getName()).thenReturn(OFFLINE);
		bukkit = mockStatic(Bukkit.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		WarNotices.remover = File::delete;
		SimpleFactions.plugin = previousPlugin;
		Cache.warNoticeKeepDays = previousKeepDays;
	}

	private File inbox(String key) {
		return data.resolve("Notices").resolve(key + ".json").toFile();
	}

	@Test
	void anOnlineCharacterIsToldAtOnceAndNothingIsStored() {
		Player online = mock(Player.class);
		bukkit.when(() -> Bukkit.getPlayerExact(ONLINE)).thenReturn(online);

		WarNotices.send(ONLINE, "§cWar!");

		verify(online).sendMessage("§cWar!");
		assertFalse(inbox(ONLINE).exists());
	}

	@Test
	void anOfflineCharacterReadsEveryNoticeAtItsNextSessionOnce() {
		WarNotices.send(OFFLINE, "first");
		WarNotices.send(OFFLINE, "second");
		assertTrue(inbox(OFFLINE).isFile());

		WarNotices.deliver(player);

		InOrder order = inOrder(player);
		order.verify(player).sendMessage("§6§lWar news while you were away:");
		order.verify(player).sendMessage("first");
		order.verify(player).sendMessage("second");
		assertFalse(inbox(OFFLINE).exists());

		WarNotices.deliver(player);
		verify(player).sendMessage("first");
	}

	@Test
	void staleAndBrokenNoticesAreDroppedUnread() throws Exception {
		WarNotices.Inbox stored = new WarNotices.Inbox();
		WarNotices.Notice old = new WarNotices.Notice();
		old.at = System.currentTimeMillis() - 15 * 86_400_000L;
		old.text = "old";
		WarNotices.Notice blank = new WarNotices.Notice();
		blank.at = System.currentTimeMillis();
		stored.notices.add(old);
		stored.notices.add(null);
		stored.notices.add(blank);
		Files.createDirectories(inbox(OFFLINE).toPath().getParent());
		JsonUtil.writeJson(inbox(OFFLINE), stored);

		WarNotices.deliver(player);

		verify(player, never()).sendMessage(anyString());
		assertFalse(inbox(OFFLINE).exists());
	}

	@Test
	void sendingDropsNoticesTooOldToBeShown() throws Exception {
		WarNotices.Inbox stored = new WarNotices.Inbox();
		WarNotices.Notice old = new WarNotices.Notice();
		old.at = System.currentTimeMillis() - 15 * 86_400_000L;
		old.text = "old";
		stored.notices.add(old);
		Files.createDirectories(inbox(OFFLINE).toPath().getParent());
		JsonUtil.writeJson(inbox(OFFLINE), stored);

		WarNotices.send(OFFLINE, "fresh");

		WarNotices.Inbox kept = JsonUtil.readJson(inbox(OFFLINE), WarNotices.Inbox.class);
		assertEquals(1, kept.notices.size());
		assertEquals("fresh", kept.notices.get(0).text);
	}

	@Test
	void anUnreadableInboxIsLoggedAndReplaced() throws Exception {
		Files.createDirectories(inbox(OFFLINE).toPath().getParent());
		Files.writeString(inbox(OFFLINE).toPath(), "{ not json");

		WarNotices.send(OFFLINE, "fresh");

		verify(logger).log(eq(Level.WARNING), startsWith("Could not read war notices"), any(Exception.class));
		WarNotices.deliver(player);
		verify(player).sendMessage("fresh");
	}

	@Test
	void anEmptyInboxFileReadsAsNoNotices() throws Exception {
		Files.createDirectories(inbox(OFFLINE).toPath().getParent());
		Files.writeString(inbox(OFFLINE).toPath(), "");

		WarNotices.deliver(player);

		verify(player, never()).sendMessage(anyString());
		Files.writeString(inbox(OFFLINE).toPath(), "{\"notices\": null}");
		WarNotices.send(OFFLINE, "kept");
		WarNotices.deliver(player);
		verify(player).sendMessage("kept");
	}

	@Test
	void aNoticeThatCannotBeSavedIsLogged() throws Exception {
		Files.writeString(data.resolve("Notices"), "a file where the folder should be");

		WarNotices.send(OFFLINE, "lost");

		verify(logger).log(eq(Level.WARNING), startsWith("Could not save war notices"), any(Exception.class));
	}

	@Test
	void anInboxThatCannotBeRemovedIsLoggedButStillShown() {
		WarNotices.send(OFFLINE, "shown");
		WarNotices.remover = file -> false;

		WarNotices.deliver(player);

		verify(logger).log(Level.WARNING, "Could not clear war notices " + OFFLINE + ".json", (Throwable) null);
		verify(player).sendMessage("shown");
	}

	@Test
	void unsafeKeysMissingInputsAndAMissingPluginStoreNothing() {
		WarNotices.send(null, "text");
		WarNotices.send(OFFLINE, null);
		WarNotices.send("../escape", "text");
		WarNotices.send("Upper", "text");
		WarNotices.deliver(null);
		assertFalse(data.resolve("Notices").toFile().exists());
		assertNull(WarNotices.file(null));
		assertNull(WarNotices.file("x".repeat(65)));
		assertEquals(inbox(OFFLINE), WarNotices.file(OFFLINE));

		SimpleFactions.plugin = null;
		assertNull(WarNotices.file(OFFLINE));
		WarNotices.send(OFFLINE, "text");
		WarNotices.deliver(player);
		verifyNoInteractions(logger);

		SimpleFactions.plugin = mock(SimpleFactions.class);
		assertNull(WarNotices.file(OFFLINE), "A plugin without a data folder stores nothing");
	}
}
