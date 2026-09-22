package me.Plugins.SimpleFactions.Map.presence;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ProvincePresenceListener implements Listener {
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		ProvincePresenceService.getInstance().handleQuit(event.getPlayer());
		TitlePresenceService.getInstance().handleQuit(event.getPlayer());
		RegionPresenceService.getInstance().handleQuit(event.getPlayer());
	}
}
