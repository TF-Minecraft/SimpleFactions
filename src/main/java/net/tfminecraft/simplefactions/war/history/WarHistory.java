package net.tfminecraft.simplefactions.war.history;

import java.io.File;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Kind;
import net.tfminecraft.simplefactions.war.stats.BattleRecord;
import net.tfminecraft.simplefactions.war.stats.BattleStatsArchive;

/**
 * War history for the website's war pages: events and battle records go into an outbox file
 * on the main thread (one appended line each) and an async task posts them every ten seconds.
 * Nothing here may break the war code, so every entry point swallows its own failures.
 */
public final class WarHistory {
	static final String FOLDER = "WarHistory";
	static final String FILE = "outbox.jsonl";
	/** Ten seconds between sends. */
	static final long SEND_TICKS = 200L;
	/** Battle records as SimpleFactions saves them under BattleStats/, compact. */
	private static final Gson RECORDS = new Gson();

	private static WarHistoryOutbox outbox;
	private static WarHistorySender sender;
	private static BukkitTask task;
	private static Logger logger;

	private WarHistory() {
	}

	/** Loads what an earlier run left unsent and starts the sender. */
	public static synchronized void start(Plugin plugin) {
		stop();
		logger = plugin.getLogger();
		if (plugin.getDataFolder() == null) {
			// Nothing may be written to the working directory.
			return;
		}
		File folder = new File(plugin.getDataFolder(), FOLDER);
		outbox = new WarHistoryOutbox(new File(folder, FILE).toPath(), WarHistory::warn);
		outbox.load();
		sender = new WarHistorySender(outbox, (path, json) -> GatewayClient.request("POST", path, json),
				GatewayClient::realmId, WarHistory::warn, Clock.systemUTC());
		task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, WarHistory::flush, SEND_TICKS, SEND_TICKS);
		logger.info("War history " + (Cache.warHistoryEnabled ? "on" : "off") + ": " + outbox.size()
				+ " items waiting to be sent to the website");
	}

	/** Stops sending; the outbox stays on disk for the next start. */
	public static synchronized void stop() {
		if (task != null) {
			task.cancel();
		}
		task = null;
		outbox = null;
		sender = null;
	}

	/** Recording happens only once started and while {@code war.history.enabled} is on. */
	public static boolean isRecording() {
		return Cache.warHistoryEnabled && outbox != null;
	}

	/** One send, from the async task. Off: what is pending waits until history is switched back on. */
	static void flush() {
		WarHistorySender current = sender;
		if (Cache.warHistoryEnabled && current != null) {
			current.flush();
		}
	}

	/** Queues one war event with a snapshot of the war as it is now. */
	static void event(War war, String type, JsonObject payload) {
		event(war, type, payload, null);
	}

	/**
	 * As {@link #event(War, String, JsonObject)}, with a private top-level {@code notify} object
	 * for the website's notifications (null for none). The website never publishes it.
	 */
	static void event(War war, String type, JsonObject payload, JsonObject notify) {
		WarHistoryOutbox current = outbox;
		if (current == null) {
			return;
		}
		String id = UUID.randomUUID().toString();
		JsonObject event = new JsonObject();
		event.addProperty("id", id);
		event.addProperty("war_key", WarHistoryJson.warKey(war));
		event.addProperty("type", type);
		event.addProperty("at", WarHistoryJson.instant(CampaignClock.now().truncatedTo(ChronoUnit.MILLIS)));
		event.add("state", WarHistoryJson.state(war));
		event.add("payload", payload);
		if (notify != null) {
			event.add("notify", notify);
		}
		current.append(Kind.EVENT, id, event);
	}

	/**
	 * Queues a saved battle record. Battles outside a war (no war key) are not war history and
	 * stay local. Returns whether it was queued.
	 */
	public static boolean battle(BattleRecord record) {
		WarHistoryOutbox current = outbox;
		if (!isRecording() || current == null || record == null || record.id == null || record.warKey == null) {
			return false;
		}
		try {
			return current.append(Kind.BATTLE, record.id, RECORDS.toJsonTree(record));
		} catch (RuntimeException failed) {
			warn("Could not queue battle record " + record.id + ": " + failed);
			return false;
		}
	}

	/**
	 * Staff backfill: queues every archived war battle, reading the files off the main thread.
	 * {@code done} gets the number queued, back on the main thread.
	 */
	public static void queueArchive(Plugin plugin, IntConsumer done) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			int queued = queueArchiveNow();
			Bukkit.getScheduler().runTask(plugin, () -> done.accept(queued));
		});
	}

	static int queueArchiveNow() {
		int queued = 0;
		for (BattleStatsArchive.Summary summary : BattleStatsArchive.list(null)) {
			if (battle(BattleStatsArchive.get(summary.id()))) {
				queued++;
			}
		}
		return queued;
	}

	/** Null before the plugin started history. */
	static WarHistorySender.Status status() {
		WarHistorySender current = sender;
		return current == null ? null : current.status();
	}

	static void warn(String message) {
		Logger current = logger;
		if (current != null) {
			current.warning("[WarHistory] " + message);
		}
	}

	/** For tests: an outbox and sender without the plugin or its scheduler. */
	static synchronized void startForTests(WarHistoryOutbox box, WarHistorySender send, Logger log) {
		stop();
		outbox = box;
		sender = send;
		logger = log;
	}

	/** For tests in other packages: record into this outbox, with no sender. */
	public static synchronized void startForTests(WarHistoryOutbox box) {
		startForTests(box, null, null);
	}

	public static synchronized void resetForTests() {
		stop();
		logger = null;
	}
}
