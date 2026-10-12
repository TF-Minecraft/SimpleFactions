package net.tfminecraft.simplefactions.war.battle.warband;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;

public class WarbandMembershipListener implements Listener {
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		WarbandMembershipService.getInstance().handleQuit(e.getPlayer());
	}

	/** Joining alone restores nothing; the place returns with the same character. */
	@EventHandler
	public void onSessionStart(CharacterSessionStartEvent e) {
		WarbandMembershipService.getInstance().handleSessionStart(e.getPlayer(), e.getSession());
	}
}
