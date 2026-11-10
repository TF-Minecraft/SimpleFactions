package net.tfminecraft.simplefactions.war.notice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.identity.Members;

/**
 * War news for characters who are not online: a declaration on their faction, a call to arms, the
 * first battle day. Each character has one small JSON file under {@code Notices/}; it is shown and
 * deleted when that character is next played. Notices older than {@link Cache#warNoticeKeepDays}
 * are dropped unread.
 */
public final class WarNotices {
	static final String FOLDER = "Notices";
	private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9-]{1,64}");
	/** Removes a delivered inbox; tests swap it to see a file that cannot be removed. */
	static java.util.function.Predicate<File> remover = File::delete;

	/** Stored form of one notice. */
	public static final class Notice {
		public long at;
		public String text;
	}

	/** Stored form of a character's notices. */
	public static final class Inbox {
		public List<Notice> notices = new ArrayList<>();
	}

	private WarNotices() {
	}

	/** Tells the character now when it is being played, otherwise keeps it for its next session. */
	public static void send(String memberKey, String text) {
		if (memberKey == null || text == null) {
			return;
		}
		Player online = Members.player(memberKey);
		if (online != null) {
			online.sendMessage(text);
			return;
		}
		File file = file(memberKey);
		if (file == null) {
			return;
		}
		Inbox inbox = read(file);
		Notice notice = new Notice();
		notice.at = System.currentTimeMillis();
		notice.text = text;
		inbox.notices.add(notice);
		write(file, inbox);
	}

	/** Shows and clears what this player's current character missed. */
	public static void deliver(Player player) {
		if (player == null) {
			return;
		}
		File file = file(Members.key(player));
		if (file == null || !file.isFile()) {
			return;
		}
		Inbox inbox = read(file);
		long cutoff = System.currentTimeMillis() - Cache.warNoticeKeepDays * 86_400_000L;
		List<String> fresh = new ArrayList<>();
		for (Notice notice : inbox.notices) {
			if (notice != null && notice.text != null && notice.at >= cutoff) {
				fresh.add(notice.text);
			}
		}
		if (!remover.test(file)) {
			log("Could not clear war notices " + file.getName(), null);
		}
		if (fresh.isEmpty()) {
			return;
		}
		player.sendMessage("§6§lWar news while you were away:");
		for (String text : fresh) {
			player.sendMessage(text);
		}
	}

	private static Inbox read(File file) {
		if (!file.isFile()) {
			return new Inbox();
		}
		try {
			Inbox inbox = JsonUtil.readJson(file, Inbox.class);
			if (inbox != null && inbox.notices != null) {
				return inbox;
			}
		} catch (IOException | RuntimeException unreadable) {
			log("Could not read war notices " + file.getName(), unreadable);
		}
		return new Inbox();
	}

	private static void write(File file, Inbox inbox) {
		try {
			java.nio.file.Files.createDirectories(file.getParentFile().toPath());
			JsonUtil.writeJsonAtomic(file, inbox);
		} catch (IOException | RuntimeException failed) {
			log("Could not save war notices " + file.getName(), failed);
		}
	}

	/** Null without a plugin data folder or for a key that is not a plain character id. */
	static File file(String memberKey) {
		File data = SimpleFactions.plugin == null ? null : SimpleFactions.plugin.getDataFolder();
		if (data == null || memberKey == null || !SAFE_KEY.matcher(memberKey).matches()) {
			return null;
		}
		return new File(new File(data, FOLDER), memberKey + ".json");
	}

	private static void log(String message, Exception cause) {
		SimpleFactions.plugin.getLogger().log(Level.WARNING, message, cause);
	}
}
