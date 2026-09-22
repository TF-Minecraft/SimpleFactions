package me.Plugins.SimpleFactions.War.battle.warband;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class WarbandMembershipListener implements Listener {
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		WarbandMembershipService.getInstance().handleQuit(e.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		WarbandMembershipService.getInstance().handleJoin(e.getPlayer());
	}
}
