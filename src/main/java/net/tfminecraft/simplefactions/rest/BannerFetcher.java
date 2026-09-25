package net.tfminecraft.simplefactions.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;

/**
 * Generates banners off the server thread. The banner API can hang for the whole gateway
 * timeout, and one click on the server thread used to freeze the server for that long.
 */
public final class BannerFetcher {

	/** Where the faction and guild views show the banner. */
	public static final int BANNER_SLOT = 10;

	private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

	private BannerFetcher() {}

	/** Plain white banner, shown until a generated one arrives. */
	public static List<String> placeholder() {
		List<String> patterns = new ArrayList<>();
		patterns.add("WHITE.BASE");
		return patterns;
	}

	/** True while {@code patterns} still holds the untouched placeholder. */
	public static boolean isPlaceholder(List<String> patterns) {
		return placeholder().equals(patterns);
	}

	/**
	 * Puts {@code item} in the banner slot of the view the player has open now, if it is the
	 * {@code type} view for {@code id}. The player may have reopened it since clicking.
	 */
	public static void refreshOpenView(Player player, SFGUI type, String id, Supplier<ItemStack> item) {
		if (player == null || !player.isOnline()) return;
		Inventory top = player.getOpenInventory().getTopInventory();
		if (!(top.getHolder() instanceof SFInventoryHolder holder)) return;
		if (holder.getType() != type || id == null || !id.equals(holder.getId())) return;
		top.setItem(BANNER_SLOT, item.get());
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
				// Held until the callback has run, so a click just before it cannot start a second fetch.
				try {
					List<String> patterns = source.get();
					List<String> result = patterns == null || patterns.isEmpty() ? null : patterns;
					main.execute(() -> {
						try {
							onMainThread.accept(result);
						} finally {
							inFlight.remove(key);
						}
					});
				} catch (RuntimeException e) {
					inFlight.remove(key);
					throw e;
				}
			});
		} catch (RuntimeException e) {
			inFlight.remove(key);
			throw e;
		}
		return true;
	}
}
