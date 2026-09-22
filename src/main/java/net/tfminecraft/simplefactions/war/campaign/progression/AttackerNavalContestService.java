package net.tfminecraft.simplefactions.war.campaign.progression;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleOutcomeService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationInPlayService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

public final class AttackerNavalContestService {
	private static final String SHIPS_CATEGORY = "ships";

	private AttackerNavalContestService() {}

	public static boolean isNavalSlotActive(War war) {
		return CampaignScheduleService.slotAtActiveIndex(war)
				.map(slot -> CampaignNavyGate.isNavalKind(slot.kind()))
				.orElse(false);
	}

	public static boolean wouldAttackerAutoLoseNaval(War war) {
		return isNavalSlotActive(war) && !hasBerthedNavalAtInPlayPort(war);
	}

	public static boolean hasBerthedNavalAtInPlayPort(War war) {
		if (war == null || war.getAttackers() == null) {
			return false;
		}
		PlayerVehicleRegistry registry = vehicleRegistryOrNull();
		if (registry == null) {
			return false;
		}
		for (Faction faction : BattleSideMembers.collectParticipatingFactions(war.getAttackers())) {
			if (hasBerthedNavalAtInPlayPort(war, faction, registry)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Applies an automatic naval-slot loss when the war attacker has no berthed navy at an in-play port.
	 *
	 * @return true if auto-loss was applied and the caller should not start a live battle
	 */
	public static boolean applyIfAttackerHasNoBerthedNavy(War war, int battleProvinceId) {
		if (war == null || !war.isActive() || battleProvinceId <= 0) {
			return false;
		}
		if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return false;
		}
		if (!wouldAttackerAutoLoseNaval(war)) {
			return false;
		}

		Battle existing = BattleManager.getByWarId(war.getId());
		if (existing != null && existing.hasStarted()) {
			return false;
		}

		purgeUnstartedBattle(war);

		CampaignBattleOutcomeService.applyCampaignBattleOutcome(
				war,
				BelligerentRole.DEFENDER,
				battleProvinceId,
				null,
				null,
				CampaignCoalition.AGGRESSOR);
		CampaignBattleOutcomeService.finalizeCampaignBattleAfterOutcome(war);
		broadcastAutoLoss(war);
		return true;
	}

	private static boolean hasBerthedNavalAtInPlayPort(
			War war,
			Faction faction,
			PlayerVehicleRegistry registry) {
		if (faction == null || faction.getId() == null) {
			return false;
		}
		InstallationHandler handler = faction.getInstallationHandler();
		if (handler == null) {
			return false;
		}
		for (Installation installation : handler.getAll()) {
			if (installation == null || installation.getKind() != InstallationKind.PORT) {
				continue;
			}
			if (!BattleInstallationInPlayService.isInPlay(war, faction.getId(), installation.getId())) {
				continue;
			}
			if (hasBerthedShip(registry, installation.getId())) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBerthedShip(PlayerVehicleRegistry registry, String installationId) {
		for (PlayerVehicleRecord record : registry.getByInstallationId(installationId)) {
			Optional<String> categoryId = VehiclesConfigLoader.getCategoryId(record.getVehicleTypeId());
			if (categoryId.isPresent() && SHIPS_CATEGORY.equalsIgnoreCase(categoryId.get())) {
				return true;
			}
		}
		return false;
	}

	private static PlayerVehicleRegistry vehicleRegistryOrNull() {
		if (SimpleFactions.plugin == null) {
			return null;
		}
		return SimpleFactions.getVehicleRegistry();
	}

	private static void purgeUnstartedBattle(War war) {
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null || battle.hasStarted()) {
			return;
		}
		BattlePersistenceService.deleteCampaignBattle(battle);
		BattleManager.clearEditorSessions(battle);
		BattleManager.deleteBattle(battle);
	}

	private static void broadcastAutoLoss(War war) {
		if (Bukkit.getServer() == null) {
			return;
		}
		String message = CampaignUiCopy.navalAutoLossBroadcast();
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getAttackers())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getDefenders())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}
}
