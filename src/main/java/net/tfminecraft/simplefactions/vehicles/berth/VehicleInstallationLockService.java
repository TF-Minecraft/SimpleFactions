package net.tfminecraft.simplefactions.vehicles.berth;

import java.time.Instant;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationInPlayService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.InstallationVulnerabilityService;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationLookup;
import net.tfminecraft.simplefactions.installation.InstallationOwners;

public final class VehicleInstallationLockService {
	public static final String BERTH_BLOCKED =
			"§cCannot berth vehicles at this installation during battle or raid embargo.";
	public static final String UNBERTH_BLOCKED =
			"§cCannot unberth vehicles at this installation during battle or raid embargo.";

	private VehicleInstallationLockService() {}

	public static boolean isVehicleLocked(String installationId, Instant now) {
		if (installationId == null || installationId.isBlank() || now == null) {
			return false;
		}
		for (Installation installation : InstallationLookup.all()) {
			if (installationId.equals(installation.getId()) && isInstallationLocked(installation, now)) return true;
		}
		return false;
	}

	public static boolean isInstallationLocked(Installation installation, Instant now) {
		if (installation == null || now == null) return false;
		if (InstallationVulnerabilityService.isInstallationVulnerable(installation, now)) return true;
		Faction owner = InstallationOwners.ownerOf(installation);
		if (owner == null) return false;
		for (War war : WarManager.getActive()) {
			if (CampaignRaidService.isInstallationRepairLocked(war, installation, now)) return true;
			if (war.getSide(owner) == null) continue;
			if (BattleInstallationPickService.isLocked(war, now)
					&& BattleInstallationInPlayService.isInPlay(war, owner.getId(), installation.getId())) return true;
		}
		return false;
	}

}
