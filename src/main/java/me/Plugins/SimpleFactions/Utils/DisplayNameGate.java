package me.Plugins.SimpleFactions.Utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.SimpleFactions;

/**
 * Soft gates for display names. Warns on camelCase without {@code _} for spaces,
 * and on words that do not start with a capital letter (with a small whitelist
 * for articles/prepositions after the first word). First attempt blocks;
 * repeating the same name proceeds.
 */
public final class DisplayNameGate implements Listener {

	public enum NameOperation {
		FACTION_CREATE,
		GUILD_CREATE,
		FACTION_RENAME,
		GUILD_RENAME,
		SETTLEMENT_FOUND,
		COMPANY_FOUND
	}

	public enum Result {
		OK,
		NEEDS_CONFIRM
	}

	private enum GateKind {
		MISSING_SPACES,
		CAPITALIZATION
	}

	private static final long TIMEOUT_TICKS = 20L * 60;

	private static final Set<String> LOWERCASE_WORD_WHITELIST = Set.of(
			"the", "of", "and", "in", "on", "at", "to", "for", "a", "an");

	private static final Map<UUID, Pending> pending = new HashMap<>();

	private static final class Pending {
		private final NameOperation op;
		private final String raw;
		private final GateKind kind;

		private Pending(NameOperation op, String raw, GateKind kind) {
			this.op = op;
			this.raw = raw;
			this.kind = kind;
		}
	}

	public static boolean looksLikeMissingSpaces(String raw) {
		if (raw == null || raw.isBlank()) {
			return false;
		}
		String plain = Formatter.formatId(raw).trim();
		if (plain.contains("_") || plain.contains(" ")) {
			return false;
		}
		for (int i = 1; i < plain.length(); i++) {
			if (Character.isLowerCase(plain.charAt(i - 1))
					&& Character.isUpperCase(plain.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasCapitalizationIssue(String raw) {
		return findUncapitalizedWord(raw).isPresent();
	}

	public static Optional<String> findUncapitalizedWord(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String plain = Formatter.formatId(raw).trim();
		String[] words = plain.split("[ _]+");
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			if (word.isEmpty()) {
				continue;
			}
			char first = word.charAt(0);
			if (!Character.isLetter(first)) {
				continue;
			}
			if (Character.isUpperCase(first)) {
				continue;
			}
			if (i > 0 && LOWERCASE_WORD_WHITELIST.contains(word.toLowerCase(Locale.ROOT))) {
				continue;
			}
			return Optional.of(word);
		}
		return Optional.empty();
	}

	public static String suggestUnderscores(String raw) {
		if (raw == null) {
			return "";
		}
		String plain = Formatter.formatId(raw).trim();
		StringBuilder out = new StringBuilder(plain.length() + 4);
		for (int i = 0; i < plain.length(); i++) {
			char c = plain.charAt(i);
			if (i > 0
					&& Character.isLowerCase(plain.charAt(i - 1))
					&& Character.isUpperCase(c)) {
				out.append('_');
			}
			out.append(c);
		}
		return out.toString();
	}

	public static Result check(Player player, NameOperation op, String raw) {
		return check(player, op, raw, false);
	}

	public static Result check(Player player, NameOperation op, String raw, boolean chatConfirm) {
		if (player == null || op == null) {
			return Result.OK;
		}
		if (looksLikeMissingSpaces(raw)) {
			Result spaceResult = tryConfirmOrBlock(
					player, op, raw, GateKind.MISSING_SPACES, chatConfirm, Optional.empty());
			if (spaceResult != Result.OK) {
				return spaceResult;
			}
		}
		Optional<String> uncapitalized = findUncapitalizedWord(raw);
		if (uncapitalized.isPresent()) {
			Result capResult = tryConfirmOrBlock(
					player, op, raw, GateKind.CAPITALIZATION, chatConfirm, uncapitalized);
			if (capResult != Result.OK) {
				return capResult;
			}
		}
		pending.remove(player.getUniqueId());
		return Result.OK;
	}

	private static Result tryConfirmOrBlock(
			Player player,
			NameOperation op,
			String raw,
			GateKind kind,
			boolean chatConfirm,
			Optional<String> offendingWord) {
		UUID id = player.getUniqueId();
		Pending prev = pending.get(id);
		if (prev != null && prev.op == op && prev.raw.equals(raw) && prev.kind == kind) {
			pending.remove(id);
			return Result.OK;
		}
		Pending next = new Pending(op, raw, kind);
		pending.put(id, next);
		if (kind == GateKind.MISSING_SPACES) {
			sendSpaceHint(player, raw, chatConfirm);
		} else {
			sendCapitalizationHint(player, offendingWord.orElse(""), chatConfirm);
		}
		scheduleTimeout(player, next);
		return Result.NEEDS_CONFIRM;
	}

	private static void sendSpaceHint(Player player, String raw, boolean chatConfirm) {
		String shown = Formatter.formatId(raw).trim();
		String suggestion = suggestUnderscores(raw);
		player.sendMessage("§cThat name looks like multiple words run together (it will show as "
				+ shown + ").");
		player.sendMessage("§7Use §e_ §7for spaces, e.g. §e" + suggestion + "§7.");
		sendRetryHint(player, chatConfirm);
	}

	private static void sendCapitalizationHint(Player player, String word, boolean chatConfirm) {
		player.sendMessage("§cDid you forget to make §e" + word + "§c a capital letter?");
		sendRetryHint(player, chatConfirm);
	}

	private static void sendRetryHint(Player player, boolean chatConfirm) {
		if (chatConfirm) {
			player.sendMessage("§7Type the same name again in chat to keep it anyway.");
		} else {
			player.sendMessage("§7Run the same command again to keep this name anyway.");
		}
	}

	private static void scheduleTimeout(Player player, Pending next) {
		UUID id = player.getUniqueId();
		new BukkitRunnable() {
			@Override
			public void run() {
				Pending current = pending.get(id);
				if (current != next) {
					return;
				}
				pending.remove(id);
			}
		}.runTaskLater(SimpleFactions.getInstance(), TIMEOUT_TICKS);
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		pending.remove(event.getPlayer().getUniqueId());
	}
}
