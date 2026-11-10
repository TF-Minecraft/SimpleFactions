package net.tfminecraft.simplefactions.war.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.actions.WarActions;

/** {@code /war admin history status|upload-battles}. */
public final class WarHistoryAdmin {
	public static final String USAGE = "§cUsage: /war admin history status|upload-battles";
	public static final String UPLOAD_RISK =
			"sends every archived war battle to the website, where it shows on the public war pages.";

	private WarHistoryAdmin() {
	}

	/** {@code args} start at {@code admin}. */
	public static void handle(Player player, String[] args) {
		String action = args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "";
		switch (action) {
			case "status" -> statusLines().forEach(player::sendMessage);
			case "upload-battles" -> upload(player);
			default -> player.sendMessage(USAGE);
		}
	}

	private static void upload(Player player) {
		if (!WarHistory.isRecording()) {
			player.sendMessage("§cWar history is off (war.history.enabled), so nothing can be queued.");
			return;
		}
		player.sendMessage("§7Queueing every archived war battle...");
		WarHistory.queueArchive(SimpleFactions.plugin, queued -> player.sendMessage("§aQueued §f" + queued
				+ " §abattle records for the website. Ones already waiting are not queued twice."));
	}

	static List<String> statusLines() {
		List<String> lines = new ArrayList<>();
		WarHistorySender.Status status = WarHistory.status();
		lines.add("§6War history: " + (Cache.warHistoryEnabled ? "§aon" : "§coff (war.history.enabled)"));
		if (status == null) {
			lines.add("§7Not started.");
		} else {
			lines.add("§7Waiting to send: §f" + status.events() + " §7events, §f" + status.battles() + " §7battle records.");
			lines.add("§7Last sent: §f" + (status.lastSuccess() == null ? "never this run" : status.lastSuccess()));
			if (status.lastError() != null) {
				lines.add("§7Last error: §c" + status.lastError() + " §7at §f" + status.lastErrorAt());
			}
		}
		actionLines(lines);
		return lines;
	}

	/** The website's votes and call to arms answers, applied here. */
	private static void actionLines(List<String> lines) {
		lines.add("§6Website actions: " + (Cache.warActionsEnabled ? "§aon" : "§coff (war.history.actions_enabled)"));
		var actions = WarActions.status();
		if (actions == null) {
			lines.add("§7Not polling the website.");
			return;
		}
		lines.add("§7Applied this run: §f" + actions.appliedThisRun() + " §7(" + actions.remembered()
				+ " results remembered). Last fetch: §f" + (actions.lastPoll() == null ? "never this run" : actions.lastPoll()));
		if (actions.lastError() != null) {
			lines.add("§7Last error: §c" + actions.lastError() + " §7at §f" + actions.lastErrorAt());
		}
	}
}
