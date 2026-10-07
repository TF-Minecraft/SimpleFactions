package net.tfminecraft.simplefactions.war.campaign.runtime.pick;

import java.util.Optional;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

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
		Faction owner = currentSiegeFortOwner(war).orElse(null);
		return owner != null && factionId.equalsIgnoreCase(owner.getId());
	}

	public static Optional<Faction> currentSiegeFortOwner(War war) {
		if (war == null) return Optional.empty();
		Optional<ScheduledCampaignBattle> slot = CampaignScheduleService.slotAtActiveIndex(war)
				.filter(value -> value.kind() == CampaignBattleKind.SIEGE)
				.filter(value -> value.fortInstallationId() != null);
		if (slot.isEmpty()) return Optional.empty();
		for (Faction faction : FactionManager.factions) {
			InstallationHandler handler = faction.getInstallationHandler();
			Installation fort = handler.getById(slot.get().fortInstallationId());
			if (fort != null && fort.getKind() == InstallationKind.FORT
					&& fort.getProvince() == slot.get().provinceId()) {
				return Optional.of(faction);
			}
		}
		return Optional.empty();
	}
}
