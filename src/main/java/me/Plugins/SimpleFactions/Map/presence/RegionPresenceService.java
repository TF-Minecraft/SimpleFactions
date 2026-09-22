package me.Plugins.SimpleFactions.Map.presence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Events.PlayerEnterRegionEvent;
import me.Plugins.SimpleFactions.Events.PlayerLeaveRegionEvent;
import me.Plugins.SimpleFactions.Loaders.RegionLoader;
import me.Plugins.SimpleFactions.Map.MapRegion;

public final class RegionPresenceService {

	private static RegionPresenceService instance = createDefault();

	private final Function<Integer, MapRegion> resolver;
	private final RegionPresenceCallbacks callbacks;
	private final Map<UUID, MapRegion> current = new HashMap<>();

	RegionPresenceService(Function<Integer, MapRegion> resolver, RegionPresenceCallbacks callbacks) {
		this.resolver = resolver;
		this.callbacks = callbacks;
	}

	public static RegionPresenceService getInstance() {
		return instance;
	}

	static RegionPresenceService createDefault() {
		return new RegionPresenceService(
				provinceId -> {
					if (provinceId == ProvincePresenceService.UNKNOWN_PROVINCE) {
						return null;
					}
					return RegionLoader.getByProvince(provinceId);
				},
				new RegionPresenceCallbacks() {
					@Override
					public void onEnter(
							UUID playerId,
							String regionId,
							String regionName,
							String previousRegionId) {
						Player player = Bukkit.getPlayer(playerId);
						if (player != null) {
							Bukkit.getPluginManager().callEvent(
									new PlayerEnterRegionEvent(
											player, regionId, regionName, previousRegionId));
						}
					}

					@Override
					public void onLeave(UUID playerId, String regionId, String nextRegionId) {
						Player player = Bukkit.getPlayer(playerId);
						if (player != null) {
							Bukkit.getPluginManager().callEvent(
									new PlayerLeaveRegionEvent(player, regionId, nextRegionId));
						}
					}
				});
	}

	static void setInstance(RegionPresenceService service) {
		instance = service != null ? service : createDefault();
	}

	public static void resetForTests() {
		instance = createDefault();
		instance.current.clear();
	}

	public void applyProvince(Player player, int provinceId) {
		if (player == null) {
			return;
		}
		applyProvince(player.getUniqueId(), provinceId);
	}

	void applyProvince(UUID playerId, int provinceId) {
		if (playerId == null) {
			return;
		}
		MapRegion next = resolver.apply(provinceId);
		MapRegion previous = current.get(playerId);
		String prevId = previous != null ? previous.getId() : null;
		String nextId = next != null ? next.getId() : null;
		if (Objects.equals(prevId, nextId)) {
			if (next != null) {
				current.put(playerId, next);
			}
			return;
		}
		if (previous != null) {
			callbacks.onLeave(playerId, previous.getId(), nextId);
		}
		if (next != null) {
			callbacks.onEnter(playerId, next.getId(), next.getName(), prevId);
			current.put(playerId, next);
		} else {
			current.remove(playerId);
		}
	}

	public void handleQuit(Player player) {
		if (player == null) {
			return;
		}
		handleQuit(player.getUniqueId());
	}

	void handleQuit(UUID playerId) {
		if (playerId == null) {
			return;
		}
		MapRegion previous = current.remove(playerId);
		if (previous != null) {
			callbacks.onLeave(playerId, previous.getId(), null);
		}
	}
}
