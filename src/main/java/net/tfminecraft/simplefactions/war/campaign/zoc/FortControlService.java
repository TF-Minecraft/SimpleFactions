package net.tfminecraft.simplefactions.war.campaign.zoc;

import java.util.HashMap;
import java.util.Optional;

import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;

public final class FortControlService {
	private FortControlService() {
	}

	public static void initializeAtDeclare(War war) {
		if (war == null) {
			return;
		}
		war.setFortControllers(new HashMap<>());
		for (FortZocIndex.OperationalFort fort : FortZocIndex.listOperationalForts()) {
			if (fort == null || fort.owner() == null || fort.id() == null) {
				continue;
			}
			Side ownerSide = war.getSide(fort.owner());
			if (ownerSide == null) {
				continue;
			}
			CampaignCoalition coalition = CampaignCoalitionService.coalitionOf(war, ownerSide);
			if (coalition == null) {
				continue;
			}
			war.putFortController(fort.id(), coalition);
		}
	}

	public static Optional<CampaignCoalition> controller(War war, String fortInstallationId) {
		if (war == null || fortInstallationId == null || fortInstallationId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(war.getFortControllers().get(fortInstallationId));
	}

	public static void setController(War war, String fortInstallationId, CampaignCoalition coalition) {
		if (war == null || fortInstallationId == null || fortInstallationId.isBlank() || coalition == null) {
			return;
		}
		war.putFortController(fortInstallationId, coalition);
	}

	public static boolean isEnemyControlled(War war, String fortInstallationId, CampaignCoalition advancing) {
		if (advancing == null) {
			return false;
		}
		Optional<CampaignCoalition> controller = controller(war, fortInstallationId);
		return controller.isPresent() && controller.get() != advancing;
	}
}
