package me.Plugins.SimpleFactions.Map.presence;

import java.util.UUID;

public interface TitlePresenceCallbacks {
	void onEnter(
			UUID playerId,
			String tierId,
			String titleId,
			String titleName,
			String previousTitleId);

	default void onLeave(UUID playerId, String tierId, String titleId, String nextTitleId) {}
}
