package net.tfminecraft.simplefactions.installation;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class InstallationTransferService {
	private InstallationTransferService() {}

	public static void transfer(Faction from, Faction to, int province) {
		if (from == null || to == null || province <= 0) {
			return;
		}
		if (from.getId() != null && from.getId().equalsIgnoreCase(to.getId())) {
			return;
		}
		InstallationHandler fromHandler = from.getInstallationHandler();
		InstallationHandler toHandler = to.getInstallationHandler();
		if (fromHandler == null || toHandler == null) {
			return;
		}

		fromHandler.cancelPendingConstructionOnProvince(province);
		for (Installation installation : fromHandler.detachOnProvince(province)) {
			toHandler.acceptTransferred(installation);
			syncBerthedOwners(from, to, installation);
		}
	}

	private static void syncBerthedOwners(Faction from, Faction to, Installation installation) {
		if (to == null || installation == null || installation.getId() == null) {
			return;
		}
		try {
			if (SimpleFactions.plugin == null) {
				return;
			}
			PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
			if (registry == null) {
				return;
			}
			InstallationVehicleOwnerSync sync = new InstallationVehicleOwnerSync(registry);
			for (PlayerVehicleRecord record : registry.getByInstallation(from.getId(), installation.getId())) {
				if (record == null || record.getVehicleUuid() == null) {
					continue;
				}
				// An older record has no faction id. Move it only when the new holder is the
				// only faction with this installation id, so another faction's vehicle stays put.
				if (record.getFactionId() == null
						&& FactionVehiclePoolService.payingFaction(record) != to) {
					continue;
				}
				// The vehicles move with the installation, so the new holder pays their upkeep.
				registry.register(new PlayerVehicleRecord(
						record.getPlayerUuid(),
						record.getVehicleUuid(),
						record.getVehicleTypeId(),
						record.getMode(),
						record.getInstallationId(),
						to.getId()));
				try {
					ActiveVehicle vehicle = VehicleFramework.getVehicleManager().get(record.getVehicleUuid());
					if (vehicle != null) {
						sync.applyLeaderOwner(vehicle, to);
					}
				} catch (RuntimeException e) {
					// Kept per vehicle so the rest still transfer. The owner is corrected on its next spawn.
					SimpleFactions.getInstance().getLogger().warning(
							"Could not move vehicle " + record.getVehicleUuid() + " to faction " + to.getId()
									+ " leader after installation transfer; it updates on next spawn: " + e);
				}
			}
			SimpleFactions.getInstance().saveVehicleRegistry();
		} catch (Exception ignored) {
		}
	}
}
