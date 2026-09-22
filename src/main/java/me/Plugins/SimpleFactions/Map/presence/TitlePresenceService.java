package me.Plugins.SimpleFactions.Map.presence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Events.PlayerTitleEnterEvent;
import me.Plugins.SimpleFactions.Events.PlayerTitleLeaveEvent;

public final class TitlePresenceService {

	private static TitlePresenceService instance = createDefault();

	private final Function<Integer, Map<String, TitlePresenceResolver.ResolvedTitle>> resolver;
	private final TitlePresenceCallbacks callbacks;
	private final Map<UUID, Map<String, TitlePresenceResolver.ResolvedTitle>> current =
			new HashMap<>();

	TitlePresenceService(
			Function<Integer, Map<String, TitlePresenceResolver.ResolvedTitle>> resolver,
			TitlePresenceCallbacks callbacks) {
		this.resolver = resolver;
		this.callbacks = callbacks;
	}

	public static TitlePresenceService getInstance() {
		return instance;
	}

	static TitlePresenceService createDefault() {
		return new TitlePresenceService(
				TitlePresenceResolver::resolve,
				new TitlePresenceCallbacks() {
					@Override
					public void onEnter(
							UUID playerId,
							String tierId,
							String titleId,
							String titleName,
							String previousTitleId) {
						Player player = Bukkit.getPlayer(playerId);
						if (player != null) {
							Bukkit.getPluginManager().callEvent(new PlayerTitleEnterEvent(
									player, tierId, titleId, titleName, previousTitleId));
						}
					}

					@Override
					public void onLeave(
							UUID playerId,
							String tierId,
							String titleId,
							String nextTitleId) {
						Player player = Bukkit.getPlayer(playerId);
						if (player != null) {
							Bukkit.getPluginManager().callEvent(
									new PlayerTitleLeaveEvent(player, tierId, titleId, nextTitleId));
						}
					}
				});
	}

	static void setInstance(TitlePresenceService service) {
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
		Map<String, TitlePresenceResolver.ResolvedTitle> next = resolver.apply(provinceId);
		if (next == null) {
			next = Map.of();
		}
		Map<String, TitlePresenceResolver.ResolvedTitle> previous =
				current.computeIfAbsent(playerId, id -> new HashMap<>());

		for (String tier : TitlePresenceResolver.TIERS) {
			TitlePresenceResolver.ResolvedTitle prev = previous.get(tier);
			TitlePresenceResolver.ResolvedTitle nxt = next.get(tier);
			String prevId = prev != null ? prev.id() : null;
			String nextId = nxt != null ? nxt.id() : null;
			if (Objects.equals(prevId, nextId)) {
				continue;
			}
			if (prev != null) {
				callbacks.onLeave(playerId, tier, prev.id(), nextId);
			}
			if (nxt != null) {
				callbacks.onEnter(playerId, tier, nxt.id(), nxt.name(), prevId);
			}
			if (nxt != null) {
				previous.put(tier, nxt);
			} else {
				previous.remove(tier);
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
		Map<String, TitlePresenceResolver.ResolvedTitle> previous = current.remove(playerId);
		if (previous == null) {
			return;
		}
		for (String tier : TitlePresenceResolver.TIERS) {
			TitlePresenceResolver.ResolvedTitle prev = previous.get(tier);
			if (prev != null) {
				callbacks.onLeave(playerId, tier, prev.id(), null);
			}
		}
	}
}
