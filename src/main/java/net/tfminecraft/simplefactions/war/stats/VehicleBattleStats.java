package net.tfminecraft.simplefactions.war.stats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.vehicleframework.events.VFEntityDamageEvent;

/**
 * Reads who is behind VehicleFramework's sourceless damage. Registered only when
 * VehicleFramework is installed, so the rest of the battle stats never depend on it.
 */
public final class VehicleBattleStats implements Listener {
	private final BattleStatsListener stats;

	public VehicleBattleStats(BattleStatsListener stats) {
		this.stats = stats;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onVehicleDamage(VFEntityDamageEvent event) {
		stats.vehicleHit(event.getEntity(), event.getDamager());
	}
}
