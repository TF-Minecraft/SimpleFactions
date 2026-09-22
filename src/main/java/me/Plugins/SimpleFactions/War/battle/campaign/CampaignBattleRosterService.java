package me.Plugins.SimpleFactions.War.battle.campaign;


import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandSignupService;
import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleJoinService;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBattleService;
import me.Plugins.SimpleFactions.SimpleFactions;

public final class CampaignBattleRosterService {
	private CampaignBattleRosterService() {
	}

	public static void ensureEnrolled(War war, Battle battle) {
		ensureEnrolledAt(war, battle, CampaignClock.now(), false, false);
	}

	public static void ensureEnrolledForced(War war, Battle battle) {
		ensureEnrolledAt(war, battle, CampaignClock.now(), true, false);
	}

	public static void tryEnrollWhenSignupOpens(War war, Instant now) {
		if (war == null || !war.isActive() || now == null) {
			return;
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null || battle.hasStarted()) {
			return;
		}
		ensureEnrolledAt(war, battle, now, false, true);
	}

	static void ensureEnrolledAt(
			War war,
			Battle battle,
			Instant now,
			boolean force,
			boolean broadcastIfNew) {
		if (war == null || battle == null || now == null) {
			return;
		}
		if (CampaignRaidBattleService.isCampaignRaidBattle(war, battle)) {
			return;
		}
		if (!force && !canEnrollCampaignWarbands(war, battle, now)) {
			return;
		}
		boolean hadShells = hasCampaignFactionShells(battle);
		enrollWarbands(war, battle);
		if (broadcastIfNew && !hadShells && hasCampaignFactionShells(battle) && !battle.hasStarted()) {
			broadcastJoinReady(war, battle);
		}
	}

	static boolean canEnrollCampaignWarbands(War war, Battle battle, Instant now) {
		if (WarDevMode.isEnabled()) {
			return true;
		}
		if (battle.hasStarted()) {
			return true;
		}
		return CampaignWarbandSignupService.isSignupOpen(war, now);
	}

	public static void enrollWarbands(War war, Battle battle) {
		if (war == null || battle == null) {
			return;
		}
		enrollSide(war, battle, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		enrollSide(war, battle, war.getDefenders(), BattleTemplate.DEFENDER_SIDE);
		BattlePersistenceService.persistBattle(battle);
		for (BattleSide side : battle.getSides()) {
			for (Warband warband : side.getBands()) {
				BattlePersistenceService.persistWarband(warband);
			}
		}
	}

	private static boolean hasCampaignFactionShells(Battle battle) {
		if (battle == null) {
			return false;
		}
		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
		return hasFactionShell(attacker) && hasFactionShell(defender);
	}

	private static boolean hasFactionShell(BattleSide side) {
		if (side == null) {
			return false;
		}
		for (Warband warband : side.getBands()) {
			if (warband != null && warband.isFaction() && warband.getCampaignSideId() != null) {
				return true;
			}
		}
		return false;
	}

	private static void enrollSide(War war, Battle battle, Side side, String battleSideId) {
		if (side == null || side.getLeader() == null) {
			if (side == null) {
				SimpleFactions.plugin.getLogger().warning(
						"[SimpleFactions] Skipping campaign warband enroll for missing side " + battleSideId);
			} else {
				SimpleFactions.plugin.getLogger().warning(
						"[SimpleFactions] Skipping campaign warband enroll for side "
								+ battleSideId
								+ " (no war side leader)");
			}
			return;
		}
		String warbandId = BattleNamingService.campaignWarbandId(battle.getDisplayName(), battleSideId);
		Warband warband = WarbandManager.getByString(warbandId);
		if (warband == null) {
			warband = Warband.createCampaignSideShell(warbandId, war, side, battleSideId);
			WarbandManager.addWarband(warband);
		}
		if (isWarbandOnBattleSide(warband, battle, battleSideId)) {
			return;
		}
		String joinError = BattleJoinService.join(warband, battle, battleSideId);
		if (joinError != null && !isWarbandOnBattleSide(warband, battle, battleSideId)) {
			if (SimpleFactions.plugin != null) {
				SimpleFactions.plugin.getLogger().warning(
						"[SimpleFactions] Campaign warband join failed for "
								+ warbandId
								+ ": "
								+ joinError
								+ "; attaching directly.");
			}
			BattleSide battleSide = battle.getSideById(battleSideId);
			if (battleSide != null) {
				battleSide.addBand(warband);
			}
		}
	}

	private static boolean isWarbandOnBattleSide(Warband warband, Battle battle, String battleSideId) {
		if (warband == null || battle == null || battleSideId == null) {
			return false;
		}
		BattleSide side = battle.getSideById(battleSideId);
		if (side == null) {
			return false;
		}
		for (Warband band : side.getBands()) {
			if (band != null && band.getId().equalsIgnoreCase(warband.getId())) {
				return true;
			}
		}
		return false;
	}

	private static void broadcastJoinReady(War war, Battle battle) {
		String message = "§a" + battle.getDisplayName()
				+ " ready. Sign up with §e/warband list §7and join your faction warband.";
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
