package net.tfminecraft.simplefactions.war.campaign.zoc;

import java.util.HashMap;
import java.util.Optional;

import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortZocIndex.OperationalFort;

public final class FortControlService {
	private FortControlService() {}

	public static void initializeAtDeclare(War war) {
		if (war == null) return;
		war.setFortControllers(new HashMap<>());
		for (OperationalFort fort : FortZocIndex.listOperationalForts()) {
			if (fort.owner() == null || fort.id() == null) continue;
			Side ownerSide = war.getSide(fort.owner());
			if (ownerSide == null) continue;
			war.putFortController(fort.stableKey(), CampaignSideService.coalitionOf(war, ownerSide));
		}
	}

	/** Legacy ID lookup is safe only when the ID identifies at most one physical fort. */
	public static Optional<CampaignSideRole> controller(War war, String fortInstallationId) {
		if (war == null || fortInstallationId == null || fortInstallationId.isBlank()) return Optional.empty();
		var matches = matchingForts(fortInstallationId, null);
		return matches.size() == 1 ? controllerForFort(war, matches.getFirst()) : Optional.empty();
	}

	public static Optional<CampaignSideRole> controllerForInstallation(War war, Installation fort) {
		if (war == null || fort == null) return Optional.empty();
		return Optional.ofNullable(war.getFortControllers().get(fort.getStableKey()));
	}

	public static Optional<CampaignSideRole> controllerForFort(War war, OperationalFort fort) {
		if (war == null || fort == null) return Optional.empty();
		return Optional.ofNullable(war.getFortControllers().get(fort.stableKey()));
	}

	public static void setController(War war, String fortInstallationId, CampaignSideRole coalition) {
		setControllerAtProvince(war, fortInstallationId, null, coalition);
	}

	/** A saved siege slot identifies its fort by local ID and home province. */
	public static void setControllerAtProvince(War war, String fortInstallationId, Integer province,
			CampaignSideRole coalition) {
		if (war == null || fortInstallationId == null || fortInstallationId.isBlank() || coalition == null) return;
		var matches = matchingForts(fortInstallationId, province);
		if (matches.size() != 1) return;
		OperationalFort fort = matches.getFirst();
		var controllers = new HashMap<>(war.getFortControllers());
		controllers.put(fort.stableKey(), coalition);
		war.setFortControllers(controllers);
	}

	private static java.util.List<OperationalFort> matchingForts(String id, Integer province) {
		return FortZocIndex.listOperationalForts().stream()
				.filter(fort -> id != null && (id.equals(fort.id()) || id.equals(fort.stableKey())))
				.filter(fort -> province == null || fort.province() == province)
				.toList();
	}

	public static boolean isEnemyControlled(War war, String fortInstallationId, CampaignSideRole advancing) {
		return advancing != null && controller(war, fortInstallationId).filter(value -> value != advancing).isPresent();
	}

	public static boolean isEnemyControlledForFort(War war, OperationalFort fort, CampaignSideRole advancing) {
		return advancing != null && controllerForFort(war, fort).filter(value -> value != advancing).isPresent();
	}
}
