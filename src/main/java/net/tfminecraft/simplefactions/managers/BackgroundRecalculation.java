package net.tfminecraft.simplefactions.managers;

import java.util.logging.Level;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;

/**
 * Full province recalculations asked for by a live change that is not a menu click, such as an
 * inactivity output step. Like the guild menu's, the flood runs on a copy off the server thread
 * and is installed on the server thread, so the step no longer freezes the server for the whole
 * recalculation (250-850 ms on Main).
 *
 * <p>The caller marks the change first ({@link ProvinceManager#markDirty()}). That makes any flood
 * prepared before the change refuse to install, and makes {@link ProvinceManager#recalculateIfNeeded()}
 * see the change. A newer request supersedes one still running. If the copy cannot be made, the
 * run fails, or live data changed while it ran (the install is refused), the server thread
 * recalculates if the live data still needs it, which is what used to happen right away. Settlement
 * recalculates on the server thread itself, so it never works from values waiting for an install.
 */
public final class BackgroundRecalculation {
	private static long runs;
	private static ProvinceManager.BackgroundFlood flooding;

	private BackgroundRecalculation() {}

	/** Server thread. Floods {@code live} in the background and installs the result. */
	public static void request(SimpleFactions plugin, ProvinceManager live) {
		if (flooding != null) flooding.cancel();
		flooding = null;
		long run = ++runs;
		ProvinceManager.BackgroundFlood flood;
		try {
			flood = live.prepareBackgroundFlood();
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.WARNING,
					"Could not copy trade for a background recalculation; recalculating on the server thread", e);
			live.recalculateIfNeeded();
			return;
		}
		flooding = flood;
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			boolean ran;
			try {
				flood.run();
				ran = true;
			} catch (RuntimeException | Error e) {
				// Errors too: the step must still land through the server-thread fallback.
				plugin.getLogger().log(Level.WARNING,
						"Background trade recalculation failed; recalculating on the server thread", e);
				ran = false;
			}
			boolean finished = ran;
			if (!plugin.isEnabled()) return;
			Bukkit.getScheduler().runTask(plugin, () -> {
				// A newer request has its own flood coming, from newer data.
				if (run != runs) return;
				flooding = null;
				if (plugin.getProvinceManager() != live) return;
				if (!finished || !live.adopt(flood)) live.recalculateIfNeeded();
			});
		});
	}
}
