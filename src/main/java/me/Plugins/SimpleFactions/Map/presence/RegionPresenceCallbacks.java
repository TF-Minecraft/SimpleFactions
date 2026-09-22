package me.Plugins.SimpleFactions.Map.presence;

import java.util.UUID;

public interface RegionPresenceCallbacks {
	void onEnter(UUID playerId, String regionId, String regionName, String previousRegionId);

	default void onLeave(UUID playerId, String regionId, String nextRegionId) {}
}
