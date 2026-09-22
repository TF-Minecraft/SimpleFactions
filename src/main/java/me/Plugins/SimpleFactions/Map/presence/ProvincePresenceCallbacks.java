package me.Plugins.SimpleFactions.Map.presence;

import java.util.UUID;

@FunctionalInterface
public interface ProvincePresenceCallbacks {
	void onEnter(UUID playerId, int provinceId, Integer previousProvinceId);

	default void onLeave(UUID playerId, int provinceId, Integer nextProvinceId) {}
}
