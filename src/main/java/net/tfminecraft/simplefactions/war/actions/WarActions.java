package net.tfminecraft.simplefactions.war.actions;

import java.io.File;
import java.time.Clock;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.war.history.WarHistory;

/**
 * Votes and answers to calls to arms that players make on the website or in a Discord DM. The
 * website queues them; every five seconds an async task fetches the pending ones for this realm,
 * the main thread applies each with the game's own checks, and the results go back.
 * {@code war.history.actions_enabled} switches it off.
 */
public final class WarActions {
	static final String FILE = "actions-applied.json";
	/** Five seconds between polls. */
	static final long POLL_TICKS = 100L;

	private static WarActionPoller poller;
	private static BukkitTask task;
	private static Logger logger;

	private WarActions() {
	}

	public static synchronized void start(Plugin plugin) {
		stop();
		logger = plugin.getLogger();
		if (plugin.getDataFolder() == null) {
			return;
		}
		WarActionLedger ledger = new WarActionLedger(
				new File(new File(plugin.getDataFolder(), WarHistory.FOLDER), FILE).toPath(), WarActions::warn);
		ledger.load();
		poller = new WarActionPoller(GatewayClient::request, GatewayClient::realmId, ledger, new WarActionApplier(),
				work -> Bukkit.getScheduler().runTask(plugin, work),
				work -> Bukkit.getScheduler().runTaskAsynchronously(plugin, work),
				WarHistory::sendNow, WarActions::warn, Clock.systemUTC());
		task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, WarActions::poll, POLL_TICKS, POLL_TICKS);
	}

	public static synchronized void stop() {
		if (task != null) {
			task.cancel();
		}
		task = null;
		poller = null;
	}

	/** One poll, from the async task; nothing while {@code war.history.actions_enabled} is off. */
	static void poll() {
		WarActionPoller current = poller;
		if (Cache.warActionsEnabled && current != null) {
			current.poll();
		}
	}

	/** Null before the plugin started. */
	public static WarActionPoller.Status status() {
		WarActionPoller current = poller;
		return current == null ? null : current.status();
	}

	static void warn(String message) {
		Logger current = logger;
		if (current != null) {
			current.warning("[WarActions] " + message);
		}
	}

	static synchronized void startForTests(WarActionPoller next, Logger log) {
		stop();
		poller = next;
		logger = log;
	}

	static synchronized void resetForTests() {
		stop();
		logger = null;
	}
}
