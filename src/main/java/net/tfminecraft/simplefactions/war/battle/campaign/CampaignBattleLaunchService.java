package net.tfminecraft.simplefactions.war.battle.campaign;

import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.campaign.progression.AttackerNavalContestService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignOffensiveForfeitService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.SimpleFactions;

public final class CampaignBattleLaunchService {
	private CampaignBattleLaunchService() {
	}

	public static Battle prepareScheduledBattle(War war) {
		if (war == null || !war.isActive()) {
			return null;
		}
		Battle existing = BattleManager.getByWarId(war.getId());
		if (existing != null) {
			CampaignBattleRosterService.ensureEnrolled(war, existing);
			return existing;
		}

		Integer provinceId = war.getScheduledBattleProvinceId();
		if (provinceId == null) {
			provinceId = BattleScheduleService.resolveScheduledProvinceId(war);
		}
		if (provinceId == null) {
			return null;
		}

		return createCampaignBattle(war, provinceId, false);
	}

	public static Battle launchAutoresolveBattle(War war) {
		if (war == null || !war.isActive()) {
			return null;
		}
		Battle existing = BattleManager.getByWarId(war.getId());
		Integer provinceId = BattleScheduleService.resolveScheduledProvinceId(war);
		if (provinceId != null
				&& AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(war, provinceId)) {
			return null;
		}

		if (existing != null) {
			String startError = startPreparedBattle(war, existing);
			if (startError != null) {
				SimpleFactions.plugin.getLogger().warning(
						"[SimpleFactions] Could not start autoresolve battle: " + startError);
			}
			return existing;
		}

		if (provinceId == null) {
			return null;
		}

		Battle battle = createCampaignBattle(war, provinceId, true);
		if (battle != null) {
			String startError = startPreparedBattle(war, battle);
			if (startError != null) {
				SimpleFactions.plugin.getLogger().warning(
						"[SimpleFactions] Could not start autoresolve battle: " + startError);
			}
		}
		return battle;
	}

	public static boolean tryStartScheduledBattle(War war, Instant now) {
		if (war == null || !war.isActive() || now == null) {
			return false;
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.SCHEDULED) {
			return false;
		}
		Instant scheduledAt = war.getScheduledBattleAt();
		if (scheduledAt == null || now.isBefore(scheduledAt)) {
			return false;
		}

		Integer provinceId = war.getScheduledBattleProvinceId();
		if (provinceId == null) {
			provinceId = BattleScheduleService.resolveScheduledProvinceId(war);
		}
		if (provinceId != null
				&& CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(war, provinceId)) {
			return true;
		}
		if (provinceId != null
				&& AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(war, provinceId)) {
			return true;
		}

		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null) {
			battle = prepareScheduledBattle(war);
		}
		String startError = startPreparedBattle(war, battle);
		if (startError != null) {
			SimpleFactions.plugin.getLogger().warning(
					"[SimpleFactions] Could not start scheduled battle: " + startError);
			broadcastStartFailure(war, battle, startError);
			return false;
		}
		return true;
	}

	/** Returns null on success, or an error message. */
	public static String startPreparedBattle(War war, Battle battle) {
		if (battle == null) {
			return "No campaign battle for this war.";
		}
		if (battle.hasStarted()) {
			return "Battle already started.";
		}
		CampaignBattleRosterService.ensureEnrolledForced(war, battle);
		return battle.start();
	}

	private static Battle createCampaignBattle(War war, int provinceId, boolean immediateStart) {
		ScheduledCampaignBattle slot = CampaignScheduleService.currentSlot(war)
				.filter(current -> current.provinceId() == provinceId)
				.orElse(null);
		BattleType type = CampaignBattleTypeResolver.resolve(war, slot);
		String battleId = campaignBattleId(war.getId(), provinceId);

		Battle battle = BattleFactory.createBlank(type, battleId);
		battle.setWarId(war.getId());
		battle.setProvinceId(provinceId);
		battle.setLocked(false);
		battle.setTeleport(true);
		BattleFactory.applyCampaignDefault(battle);
		if (slot != null
				&& (slot.kind() == CampaignBattleKind.NAVAL
						|| slot.kind() == CampaignBattleKind.NAVAL_INVASION)) {
			battle.setNavalVariant(true);
		}
		BattleNamingService.applyCampaignName(battle, war, provinceId, type, slot);
		BattleManager.addBattle(battle);
		CampaignBattleRosterService.ensureEnrolled(war, battle);
		BattlePersistenceService.persistBattle(battle);
		return battle;
	}

	static String campaignBattleId(int warId, int provinceId) {
		return "campaign_w" + warId + "_p" + provinceId;
	}

	private static void broadcastStartFailure(War war, Battle battle, String error) {
		String battleName = battle != null ? battle.getDisplayName() : "The scheduled battle";
		String message = "§c" + battleName + " could not start: §7" + error;
		broadcastToBelligerents(war, message);
	}

	private static void broadcastToBelligerents(War war, String message) {
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
