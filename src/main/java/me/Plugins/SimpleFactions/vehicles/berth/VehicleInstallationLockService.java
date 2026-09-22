package me.Plugins.SimpleFactions.vehicles.berth;

import java.time.Instant;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationInPlayService;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.InstallationVulnerabilityService;

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
		if (InstallationVulnerabilityService.isVulnerable(installationId, now)) {
			return true;
		}
		for (War war : WarManager.getActive()) {
			if (CampaignRaidService.isRepairLocked(war, installationId, now)) {
				return true;
			}
			if (isPickLockFrozen(war, installationId, now)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPickLockFrozen(War war, String installationId, Instant now) {
		if (war == null || !war.isActive() || !BattleInstallationPickService.isLocked(war, now)) {
			return false;
		}
		for (Faction faction : BattleSideMembers.collectParticipatingFactions(war.getAttackers())) {
			if (BattleInstallationInPlayService.isInPlay(war, faction.getId(), installationId)) {
				return true;
			}
		}
		for (Faction faction : BattleSideMembers.collectParticipatingFactions(war.getDefenders())) {
			if (BattleInstallationInPlayService.isInPlay(war, faction.getId(), installationId)) {
				return true;
			}
		}
		return false;
	}
}
