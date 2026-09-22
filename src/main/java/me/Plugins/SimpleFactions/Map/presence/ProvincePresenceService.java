package me.Plugins.SimpleFactions.Map.presence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Events.PlayerProvinceEnterEvent;
import me.Plugins.SimpleFactions.Events.PlayerProvinceLeaveEvent;
import me.Plugins.SimpleFactions.REST.RestServer;
import me.Plugins.SimpleFactions.SimpleFactions;

public final class ProvincePresenceService {
	public static final int UNKNOWN_PROVINCE = -2;

	private static ProvincePresenceService instance = createDefault();

	private final Function<Player, Integer> provinceResolver;
	private final ProvincePresenceCallbacks callbacks;
	private final Map<UUID, Integer> currentProvince = new HashMap<>();

	ProvincePresenceService(
			Function<Player, Integer> provinceResolver,
			ProvincePresenceCallbacks callbacks) {
		this.provinceResolver = provinceResolver;
		this.callbacks = callbacks;
	}

	public static ProvincePresenceService getInstance() {
		return instance;
	}

	static ProvincePresenceService createDefault() {
		return new ProvincePresenceService(
				RestServer::getProvince,
				new ProvincePresenceCallbacks() {
					@Override
					public void onEnter(UUID playerId, int provinceId, Integer previousProvinceId) {
						Player player = Bukkit.getPlayer(playerId);
						if (player != null) {
							Bukkit.getPluginManager().callEvent(
									new PlayerProvinceEnterEvent(player, provinceId, previousProvinceId));
							TitlePresenceService.getInstance().applyProvince(player, provinceId);
							RegionPresenceService.getInstance().applyProvince(player, provinceId);
						}
					}

					@Override
					public void onLeave(UUID playerId, int provinceId, Integer nextProvinceId) {
						Player player = Bukkit.getPlayer(playerId);
						if (player != null) {
							Bukkit.getPluginManager().callEvent(
									new PlayerProvinceLeaveEvent(player, provinceId, nextProvinceId));
						}
					}
				});
	}

	static void setInstance(ProvincePresenceService service) {
		instance = service != null ? service : createDefault();
	}

	public static void resetForTests() {
		instance = createDefault();
		instance.currentProvince.clear();
	}

	public int getCurrentProvince(Player player) {
		if (player == null) {
			return UNKNOWN_PROVINCE;
		}
		return getCurrentProvince(player.getUniqueId());
	}

	public int getCurrentProvince(UUID playerId) {
		if (playerId == null) {
			return UNKNOWN_PROVINCE;
		}
		Integer province = currentProvince.get(playerId);
		return province != null ? province : UNKNOWN_PROVINCE;
	}

	public boolean isInProvince(Player player, int provinceId) {
		return getCurrentProvince(player) == provinceId;
	}

	public void tick() {
		if (SimpleFactions.plugin == null || Bukkit.getServer() == null) {
			return;
		}
		tick(Bukkit.getOnlinePlayers());
	}

	public void tick(Collection<? extends Player> players) {
		if (players == null) {
			return;
		}
		for (Player player : players) {
			if (player != null && player.isOnline()) {
				updatePlayer(player);
			}
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
		Integer previous = currentProvince.remove(playerId);
		if (previous != null) {
			callbacks.onLeave(playerId, previous, null);
		}
	}

	void updatePlayer(Player player) {
		updatePlayer(player.getUniqueId(), provinceResolver.apply(player));
	}

	void updatePlayer(UUID playerId, int next) {
		Integer previous = currentProvince.get(playerId);
		if (previous == null) {
			currentProvince.put(playerId, next);
			callbacks.onEnter(playerId, next, null);
			return;
		}
		if (previous == next) {
			return;
		}
		callbacks.onLeave(playerId, previous, next);
		currentProvince.put(playerId, next);
		callbacks.onEnter(playerId, next, previous);
	}
}
