package net.tfminecraft.simplefactions.installation;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
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
			syncBerthedOwners(to, installation);
		}
	}

	private static void syncBerthedOwners(Faction to, Installation installation) {
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
			for (PlayerVehicleRecord record : registry.getByInstallationId(installation.getId())) {
				if (record == null || record.getVehicleUuid() == null) {
					continue;
				}
				ActiveVehicle vehicle = VehicleFramework.getVehicleManager().get(record.getVehicleUuid());
				if (vehicle != null) {
					sync.applyLeaderOwner(vehicle, to);
				}
			}
		} catch (Exception ignored) {
		}
	}
}
