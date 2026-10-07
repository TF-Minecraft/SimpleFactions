package net.tfminecraft.simplefactions.installation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.zoc.PortSeaZocIndex;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
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
		List<War> activeWars = WarManager.getActive();
		Set<War> changedWars = new LinkedHashSet<>();
		// Capture sea coverage before removing or renaming any port.
		PortSeaZocIndex portIndex = PortSeaZocIndex.fromPorts(
				!activeWars.isEmpty() && fromHandler.getByProvince(InstallationKind.PORT, province) != null
						? PortSeaZocIndex.listOperationalPorts() : List.of());

		fromHandler.cancelPendingConstructionOnProvince(province);
		for (Installation installation : fromHandler.detachOnProvince(province)) {
			Installation transferred = availableId(toHandler, installation);
			toHandler.acceptTransferred(transferred);
			if (!installation.getId().equals(transferred.getId())) {
				for (War war : activeWars) {
					boolean invasionChanged = rebindInstallation(war.getCampaignBattleSchedule(), installation, transferred, portIndex);
					boolean counterChanged = rebindInstallation(war.getCampaignCounterSchedule(), installation, transferred, portIndex);
					if (invasionChanged || counterChanged) changedWars.add(war);
				}
			}
			syncBerthedOwners(from, to, installation.getId(), transferred);
		}
		changedWars.forEach(WarManager::persist);
	}

	private static boolean rebindInstallation(List<ScheduledCampaignBattle> schedule, Installation original,
			Installation moved, PortSeaZocIndex portIndex) {
		boolean changed = false;
		for (int index = 0; index < schedule.size(); index++) {
			ScheduledCampaignBattle slot = schedule.get(index);
			if (slot == null) continue;
			boolean fort = original.getKind() == InstallationKind.FORT && slot.kind() == CampaignBattleKind.SIEGE
					&& slot.provinceId() == original.getProvince() && original.getId().equals(slot.fortInstallationId());
			boolean port = original.getKind() == InstallationKind.PORT && original.getId().equals(slot.portInstallationId())
					&& portIndex.portForSeaProvince(slot.provinceId())
							.filter(p -> p.province() == original.getProvince() && p.completedAt() == original.getCompletedAt())
							.isPresent();
			if (fort || port) {
				schedule.set(index, new ScheduledCampaignBattle(slot.provinceId(), slot.kind(), slot.required(),
						fort ? moved.getId() : slot.fortInstallationId(),
						port ? moved.getId() : slot.portInstallationId(), slot.chronologyProvinceId()));
				changed = true;
			}
		}
		return changed;
	}

	private static Installation availableId(InstallationHandler target, Installation installation) {
		String id = installation.getId();
		int suffix = 1;
		while (target.getById(id) != null
				|| (target.getPendingConstruction() != null
						&& id.equals(target.getPendingConstruction().getId()))) {
			id = installation.getId() + "_" + installation.getProvince() + "_" + suffix++;
		}
		if (id.equals(installation.getId())) return installation;
		var data = installation.toData();
		data.id = id;
		return new Installation(data);
	}

	private static void syncBerthedOwners(Faction from, Faction to, String originalId, Installation installation) {
		try {
			if (SimpleFactions.plugin == null) {
				return;
			}
			PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
			InstallationVehicleOwnerSync sync = new InstallationVehicleOwnerSync(registry);
			for (PlayerVehicleRecord record : registry.getByInstallation(from.getId(), originalId)) {
				// An older record has no faction id. Move it only when the new holder is the
				// only faction with this installation id, so another faction's vehicle stays put.
				if (record.getFactionId() == null
						&& (!originalId.equals(installation.getId())
								|| FactionVehiclePoolService.payingFaction(record) != to)) {
					continue;
				}
				// The vehicles move with the installation, so the new holder pays their upkeep.
				registry.register(new PlayerVehicleRecord(
						record.getPlayerUuid(),
						record.getVehicleUuid(),
						record.getVehicleTypeId(),
						record.getMode(),
						installation.getId(),
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
			if (!SimpleFactions.getInstance().saveVehicleRegistry()) {
				SimpleFactions.getInstance().getLogger().warning(
						"Could not save vehicle registry after transferring installation " + installation.getId()
								+ " to faction " + to.getId());
			}
		} catch (Exception failure) {
			SimpleFactions.getInstance().getLogger().warning(
					"Could not persist vehicle ownership after transferring installation " + installation.getId()
							+ " to faction " + to.getId() + ": " + failure);
		}
	}
}
