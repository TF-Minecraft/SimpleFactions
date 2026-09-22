package me.Plugins.SimpleFactions.installation;

import java.time.Instant;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.War.battle.ui.BattlePermissions;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.VehicleFramework.Events.VFEntityDamageEvent;
import net.tfminecraft.VehicleFramework.Events.VFExplosionEvent;
import net.tfminecraft.VehicleFramework.VehicleFramework;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class InstallationProtectionListener implements Listener {

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		if (isStaffBypass(player)) {
			return;
		}
		Installation installation = InstallationLookup.findCovering(event.getBlock().getLocation());
		if (installation == null) {
			return;
		}
		if (!InstallationVulnerabilityService.isVulnerable(installation.getId(), Instant.now())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();
		if (isStaffBypass(player)) {
			return;
		}
		Installation installation = InstallationLookup.findCovering(event.getBlock().getLocation());
		if (installation == null) {
			return;
		}
		if (!InstallationVulnerabilityService.isVulnerable(installation.getId(), Instant.now())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onExplosion(VFExplosionEvent event) {
		Location location = event.getLocation();
		if (location == null) {
			return;
		}
		Instant now = Instant.now();
		for (Installation installation : InstallationLookup.all()) {
			if (!InstallationBounds.isWithinRadius(installation, location)
					|| !InstallationBounds.isCorrectProvince(installation, location)) {
				continue;
			}
			if (!InstallationVulnerabilityService.isVulnerable(installation.getId(), now)) {
				event.setBlockDamage(false);
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onVehicleDamage(VFEntityDamageEvent event) {
		if (isStaffBypass(event.getEntity())) {
			return;
		}
		ActiveVehicle vehicle = resolveVehicle(event.getEntity());
		if (vehicle == null) {
			return;
		}
		Instant now = Instant.now();
		String installationId = resolveInstallationId(vehicle);
		if (installationId != null && !InstallationVulnerabilityService.isVulnerable(installationId, now)) {
			event.setCancelled(true);
			return;
		}
		Installation covering = InstallationLookup.findCovering(vehicle.getLocation());
		if (covering != null && !InstallationVulnerabilityService.isVulnerable(covering.getId(), now)) {
			event.setCancelled(true);
		}
	}

	private static String resolveInstallationId(ActiveVehicle vehicle) {
		if (vehicle == null) {
			return null;
		}
		PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
		if (registry == null) {
			return null;
		}
		return registry.getByVehicleUuid(vehicle.getUUID())
				.filter(record -> record.getMode() == OwnershipMode.INSTALLATION)
				.map(PlayerVehicleRecord::getInstallationId)
				.orElse(null);
	}

	private static ActiveVehicle resolveVehicle(Entity entity) {
		if (entity == null) {
			return null;
		}
		try {
			var manager = VehicleFramework.getVehicleManager();
			ActiveVehicle vehicle = manager.get(entity);
			if (vehicle != null) {
				return vehicle;
			}
			return manager.getByPassenger(entity);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static boolean isStaffBypass(Player player) {
		return player != null && (Permissions.isAdmin(player) || BattlePermissions.isAdmin(player));
	}

	private static boolean isStaffBypass(Entity entity) {
		if (entity instanceof Player player) {
			return isStaffBypass(player);
		}
		return false;
	}
}
