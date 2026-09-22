package net.tfminecraft.simplefactions.vehicles.berth;

import java.time.Instant;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationInPlayService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.InstallationVulnerabilityService;

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
