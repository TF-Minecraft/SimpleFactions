package net.tfminecraft.simplefactions.installation;

import java.time.Instant;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.war.battle.ui.BattlePermissions;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.events.VFEntityDamageEvent;
import net.tfminecraft.vehicleframework.events.VFExplosionEvent;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class InstallationProtectionListener implements Listener {

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (isBlockChangeProtected(event.getPlayer(), event.getBlock().getLocation())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (isBlockChangeProtected(event.getPlayer(), event.getBlock().getLocation())) {
			event.setCancelled(true);
		}
	}

	/**
	 * Blocks inside an installation's radius are locked to everyone but the owning faction,
	 * until a battle or raid puts the installation in play. Blocked players are told why.
	 */
	private static boolean isBlockChangeProtected(Player player, Location location) {
		if (isStaffBypass(player)) {
			return false;
		}
		Installation installation = InstallationLookup.findCovering(location);
		if (installation == null
				|| InstallationVulnerabilityService.isInstallationVulnerable(installation, Instant.now())
				|| isOwnerMember(player, installation)) {
			return false;
		}
		player.sendMessage(blockedMessage(installation));
		return true;
	}

	static boolean isOwnerMember(Player player, Installation installation) {
		Faction owner = InstallationOwners.ownerOf(installation);
		if (owner == null) {
			return false;
		}
		String name = player.getName();
		return owner.isMemberIgnoreCase(name) || name.equalsIgnoreCase(owner.getLeader());
	}

	private static String blockedMessage(Installation installation) {
		return "§cOnly its faction can build or dig near the "
				+ installation.getKind().getDisplayName() + " §f" + installation.getName() + "§c.";
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
			if (!InstallationVulnerabilityService.isInstallationVulnerable(installation, now)) {
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
		PlayerVehicleRecord record = resolveInstallationRecord(vehicle);
		if (record != null) {
			var owner = FactionVehiclePoolService.payingFaction(record);
			Installation installation = owner != null
					? owner.getInstallationHandler().getById(record.getInstallationId()) : null;
			if (!InstallationVulnerabilityService.isInstallationVulnerable(installation, now)) {
				event.setCancelled(true);
				return;
			}
		}
		Installation covering = InstallationLookup.findCovering(vehicle.getLocation());
		if (covering != null && !InstallationVulnerabilityService.isInstallationVulnerable(covering, now)) {
			event.setCancelled(true);
		}
	}

	private static PlayerVehicleRecord resolveInstallationRecord(ActiveVehicle vehicle) {
		PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
		return registry.getByVehicleUuid(vehicle.getUUID())
				.filter(record -> record.getMode() == OwnershipMode.INSTALLATION)
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
