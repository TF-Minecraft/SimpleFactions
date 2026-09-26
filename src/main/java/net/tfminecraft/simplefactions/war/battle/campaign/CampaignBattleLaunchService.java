package net.tfminecraft.simplefactions.war.battle.campaign;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattlePlacementValidator;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.campaign.progression.AttackerNavalContestService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignOffensiveForfeitService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.SimpleFactions;

public final class CampaignBattleLaunchService {
	private static final DateTimeFormatter SCHEDULED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(BattleWindowService.SCHEDULE_ZONE);
	private static final Map<String, String> belligerentStartFailure = new ConcurrentHashMap<>();
	private static final Map<String, String> adminStartFailure = new ConcurrentHashMap<>();

	private CampaignBattleLaunchService() {
	}

	static void resetStartFailureAlertsForTests() {
		belligerentStartFailure.clear();
		adminStartFailure.clear();
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

		Battle battle = createCampaignBattle(war, provinceId, false);
		alertStaffOfScheduledBattle(war, battle);
		return battle;
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
				logWarning("Could not start autoresolve battle: " + startError);
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
				logWarning("Could not start autoresolve battle: " + startError);
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
			logWarning("Could not start scheduled battle: " + startError);
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
		// A replacement battle reuses the id, so its first failure must alert again.
		clearStartFailureAlerts(war, battleId);

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

	static void broadcastStartFailure(War war, Battle battle, String error) {
		String battleName = battle != null ? battle.getDisplayName() : "The scheduled battle";
		String message = "§c" + battleName + " could not start: §7" + error;
		String key = failureKey(war, battle);
		if (belligerentStartFailure.putIfAbsent(key, error) == null) {
			broadcastToBelligerents(war, message);
		}
		String previousAdmin = adminStartFailure.put(key, error);
		if (!error.equals(previousAdmin)) {
			sendToOnlineAdmins(message);
		}
	}

	private static void clearStartFailureAlerts(War war, String battleId) {
		String key = battleId.toLowerCase(Locale.ROOT);
		belligerentStartFailure.remove(key);
		adminStartFailure.remove(key);
		String warKey = failureKey(war, null);
		belligerentStartFailure.remove(warKey);
		adminStartFailure.remove(warKey);
	}

	private static String failureKey(War war, Battle battle) {
		if (battle != null && battle.getId() != null && !battle.getId().isBlank()) {
			return battle.getId().toLowerCase(Locale.ROOT);
		}
		return war != null ? "war-" + war.getId() : "unknown";
	}

	private static void alertStaffOfScheduledBattle(War war, Battle battle) {
		if (battle == null) {
			return;
		}
		List<String> missing = BattlePlacementValidator.validate(battle);
		String missingText = missing.isEmpty() ? "none" : String.join("; ", missing);
		String when = formatScheduledTime(war != null ? war.getScheduledBattleAt() : null);
		String type = scheduledBattleTypeLabel(battle);
		String plain = battle.getDisplayName()
				+ " (" + type + ") province " + battle.getProvinceId()
				+ " at " + when
				+ ". Still missing: " + missingText;
		logWarning(plain);
		sendToOnlineAdmins("§cScheduled battle §e" + battle.getDisplayName()
				+ " §7(" + type + ") §cprovince §e" + battle.getProvinceId()
				+ " §cat §e" + when
				+ "§c. Still missing: §7" + missingText);
	}

	static String scheduledBattleTypeLabel(Battle battle) {
		if (battle != null && battle.isNavalVariant()) {
			return "naval";
		}
		if (battle != null && battle.getBattleType() == BattleType.SIEGE) {
			return "siege";
		}
		return "field";
	}

	static String formatScheduledTime(Instant scheduledAt) {
		if (scheduledAt == null) {
			return "unscheduled";
		}
		return SCHEDULED_TIME.format(scheduledAt) + " CET";
	}

	private static void logWarning(String plain) {
		Logger logger = SimpleFactions.plugin != null ? SimpleFactions.plugin.getLogger() : null;
		if (logger == null) {
			logger = Bukkit.getLogger();
		}
		if (logger != null) {
			logger.warning("[SimpleFactions] " + plain);
		}
	}

	private static void sendToOnlineAdmins(String message) {
		if (Bukkit.getOnlinePlayers() == null) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player != null && player.isOnline() && Permissions.isAdmin(player)) {
				player.sendMessage(message);
			}
		}
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
