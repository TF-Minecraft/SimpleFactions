package me.Plugins.SimpleFactions.War.campaign.runtime.pick;

import java.util.Optional;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

public final class BattleSiegeFortService {
	private BattleSiegeFortService() {}

	public static Optional<String> currentSiegeFortInstallationId(War war) {
		if (war == null) {
			return Optional.empty();
		}
		return CampaignScheduleService.slotAtActiveIndex(war)
				.filter(slot -> slot.kind() == CampaignBattleKind.SIEGE)
				.map(ScheduledCampaignBattle::fortInstallationId)
				.filter(id -> id != null && !id.isBlank());
	}

	public static boolean isSiegeFortInPlay(War war, String installationId) {
		if (installationId == null || installationId.isBlank()) {
			return false;
		}
		return currentSiegeFortInstallationId(war)
				.map(installationId::equals)
				.orElse(false);
	}

	public static boolean isSiegeFortInPlayForFaction(War war, String factionId, String installationId) {
		if (factionId == null || factionId.isBlank() || !isSiegeFortInPlay(war, installationId)) {
			return false;
		}
		Faction owner = findOwnerFaction(installationId);
		return owner != null && factionId.equalsIgnoreCase(owner.getId());
	}

	private static Faction findOwnerFaction(String installationId) {
		for (Faction faction : FactionManager.factions) {
			InstallationHandler handler = faction.getInstallationHandler();
			if (handler == null) {
				continue;
			}
			if (handler.getById(installationId) != null) {
				return faction;
			}
		}
		return null;
	}
}
