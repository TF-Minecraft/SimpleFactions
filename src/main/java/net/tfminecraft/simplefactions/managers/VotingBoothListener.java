package net.tfminecraft.simplefactions.managers;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import dev.lone.itemsadder.api.Events.FurniturePlaceSuccessEvent;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.government.VotingBlock;

public class VotingBoothListener implements Listener {
	private final FactionManager factions;

	public VotingBoothListener(FactionManager factions) {
		this.factions = factions;
	}

	@EventHandler
	public void openVotingFurniture(FurnitureInteractEvent event) {
		Action action = event.getAction();
		if (action != null && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
		if (!VotingBlock.matches(Cache.votingBlock, event.getNamespacedID())) return;
		Entity entity = event.getBukkitEntity();
		if (entity == null) return;
		if (factions.presentBooth(event.getPlayer(), entity.getLocation().getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void placeVotingFurniture(FurniturePlaceSuccessEvent event) {
		if (!VotingBlock.matches(Cache.votingBlock, event.getNamespacedID())) return;
		Entity entity = event.getBukkitEntity();
		if (entity == null) return;
		factions.registerVotingBooth(event.getPlayer(), entity.getLocation().getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void breakVotingFurniture(FurnitureBreakEvent event) {
		if (!VotingBlock.matches(Cache.votingBlock, event.getNamespacedID())) return;
		Entity entity = event.getBukkitEntity();
		if (entity == null) return;
		Block block = entity.getLocation().getBlock();
		factions.unregisterVotingBooth(event.getPlayer(), block);
	}
}
