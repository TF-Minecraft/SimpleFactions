package net.tfminecraft.simplefactions.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;

/**
 * Generates banners off the server thread. The banner API can hang for the whole gateway
 * timeout, and one click on the server thread used to freeze the server for that long.
 */
public final class BannerFetcher {

	private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

	private BannerFetcher() {}

	/** Plain white banner, shown until a generated one arrives. */
	public static List<String> placeholder() {
		List<String> patterns = new ArrayList<>();
		patterns.add("WHITE.BASE");
		return patterns;
	}

	/**
	 * Fetches a banner and hands it to {@code onMainThread} on the server thread, or null when
	 * the API failed. Returns false without fetching while a fetch for {@code key} is running,
	 * so repeated clicks cannot queue up requests.
	 */
	public static boolean fetch(String key, Consumer<List<String>> onMainThread) {
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin == null || !plugin.isEnabled()) return false;
		return fetch(
				key,
				onMainThread,
				RestServer::fetchBannerList,
				task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task),
				task -> {
					if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
				});
	}

	static boolean fetch(
			String key,
			Consumer<List<String>> onMainThread,
			Supplier<List<String>> source,
			Executor async,
			Executor main) {
		if (!inFlight.add(key)) return false;
		try {
			async.execute(() -> {
				List<String> patterns = null;
				try {
					patterns = source.get();
				} finally {
					inFlight.remove(key);
				}
				List<String> result = patterns == null || patterns.isEmpty() ? null : patterns;
				main.execute(() -> onMainThread.accept(result));
			});
		} catch (RuntimeException e) {
			inFlight.remove(key);
			throw e;
		}
		return true;
	}
}
