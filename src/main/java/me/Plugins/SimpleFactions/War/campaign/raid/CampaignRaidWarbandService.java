package me.Plugins.SimpleFactions.War.campaign.raid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandBattleService;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;

public final class CampaignRaidWarbandService {
	private CampaignRaidWarbandService() {}

	public static String attackerWarbandId(CampaignRaid raid) {
		if (raid == null || raid.getDisplayName() == null || raid.getDisplayName().isBlank()) {
			return null;
		}
		return BattleNamingService.campaignWarbandId(raid.getDisplayName(), BattleTemplate.ATTACKER_SIDE);
	}

	public static String defenderWarbandId(CampaignRaid raid) {
		if (raid == null || raid.getDisplayName() == null || raid.getDisplayName().isBlank()) {
			return null;
		}
		return BattleNamingService.campaignWarbandId(raid.getDisplayName(), BattleTemplate.DEFENDER_SIDE);
	}

	public static boolean isRaidWarbandHiddenFromPlayer(Warband warband, org.bukkit.entity.Player player) {
		if (warband == null || !isRaidWarband(warband) || player == null) {
			return false;
		}
		me.Plugins.SimpleFactions.Objects.Faction faction =
				me.Plugins.SimpleFactions.Managers.FactionManager.getByMember(player.getName());
		if (faction == null) {
			faction = me.Plugins.SimpleFactions.Managers.FactionManager.getByLeader(player.getName());
		}
		for (War war : WarManager.getActive()) {
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid == null || raid.getState() != CampaignRaidState.MUSTER) {
				continue;
			}
			String attackerId = attackerWarbandId(raid);
			String defenderId = defenderWarbandId(raid);
			if (!warband.getId().equalsIgnoreCase(attackerId)
					&& !warband.getId().equalsIgnoreCase(defenderId)) {
				continue;
			}
			if (CampaignRaidService.isMusterHiddenFromFaction(war, faction)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isRaidWarband(Warband warband) {
		if (warband == null || warband.getId() == null) {
			return false;
		}
		for (War war : WarManager.getActive()) {
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid == null) {
				continue;
			}
			String attackerId = attackerWarbandId(raid);
			String defenderId = defenderWarbandId(raid);
			if (warband.getId().equalsIgnoreCase(attackerId) || warband.getId().equalsIgnoreCase(defenderId)) {
				return true;
			}
		}
		return false;
	}

	public static void createAttackerWarband(War war, CampaignRaid raid) {
		if (war == null || raid == null) {
			return;
		}
		ensureWarband(war, raid, true);
	}

	public static void createRaidWarbands(War war, CampaignRaid raid) {
		if (war == null || raid == null) {
			return;
		}
		ensureWarband(war, raid, true);
		ensureWarband(war, raid, false);
	}

	public static Warband getAttackerWarband(CampaignRaid raid) {
		String id = attackerWarbandId(raid);
		return id != null ? WarbandManager.getByString(id) : null;
	}

	public static Warband getDefenderWarband(CampaignRaid raid) {
		String id = defenderWarbandId(raid);
		return id != null ? WarbandManager.getByString(id) : null;
	}

	public static void signupAttacker(War war, CampaignRaid raid, UUID playerId, String playerName) {
		if (war == null || raid == null || playerId == null || playerName == null) {
			return;
		}
		createRaidWarbands(war, raid);
		Warband warband = getAttackerWarband(raid);
		if (warband == null || warband.hasMember(playerId)) {
			return;
		}
		applyRaidLeaderRules(war, warband, playerId, playerName);
		warband.addMember(playerId);
	}

	public static void signupDefender(War war, CampaignRaid raid, UUID playerId, String playerName) {
		if (war == null || raid == null || playerId == null || playerName == null) {
			return;
		}
		createRaidWarbands(war, raid);
		Warband warband = getDefenderWarband(raid);
		if (warband == null || warband.hasMember(playerId)) {
			return;
		}
		applyRaidLeaderRules(war, warband, playerId, playerName);
		warband.addMember(playerId);
	}

	public static void enrollOnlineDefenders(War war, CampaignRaid raid) {
		if (war == null || raid == null) {
			return;
		}
		createRaidWarbands(war, raid);
		Side defenderSide = coalitionSide(war, raid.getAttackerCoalition() != null
				? raid.getAttackerCoalition().opposing()
				: null);
		if (defenderSide == null) {
			return;
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(defenderSide)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player == null || !player.isOnline()) {
				continue;
			}
			if (WarbandManager.getByMemberId(player.getUniqueId()) != null) {
				continue;
			}
			signupDefender(war, raid, player.getUniqueId(), player.getName());
		}
	}

	public static void tryEnrollDefenderOnLogin(Player player) {
		if (player == null) {
			return;
		}
		Faction faction = FactionManager.getByMember(player.getName());
		if (faction == null) {
			faction = FactionManager.getByLeader(player.getName());
		}
		if (faction == null || WarbandManager.getByMemberId(player.getUniqueId()) != null) {
			return;
		}
		for (War war : WarManager.getActive()) {
			if (!war.isActive() || war.getSide(faction) == null) {
				continue;
			}
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid == null || raid.getState() != CampaignRaidState.FIGHTING) {
				continue;
			}
			CampaignCoalition playerCoalition = CampaignRaidService.coalitionForFaction(war, faction);
			CampaignCoalition defendingCoalition = raid.getAttackerCoalition() != null
					? raid.getAttackerCoalition().opposing()
					: null;
			if (playerCoalition != defendingCoalition) {
				continue;
			}
			signupDefender(war, raid, player.getUniqueId(), player.getName());
			return;
		}
	}

	public static void promoteLeaderIfNeeded(Warband warband) {
		if (warband == null || !isRaidWarband(warband)) {
			return;
		}
		if (!warband.isPendingLeader() && warband.getRealMemberCount() > 0) {
			UUID currentLeader = warband.getLeaderId();
			if (currentLeader != null && warband.hasMember(currentLeader)) {
				return;
			}
		}
		UUID nextLeader = warband.getOldestRealMemberId(null);
		if (nextLeader != null) {
			warband.setLeaderId(nextLeader);
		} else {
			warband.resetToPendingLeader();
		}
	}

	public static void destroyRaidWarbands(War war, CampaignRaid raid) {
		if (raid == null) {
			return;
		}
		deleteIfPresent(attackerWarbandId(raid));
		deleteIfPresent(defenderWarbandId(raid));
	}

	private static void ensureWarband(War war, CampaignRaid raid, boolean attacker) {
		String id = attacker ? attackerWarbandId(raid) : defenderWarbandId(raid);
		if (id == null || WarbandManager.getByString(id) != null) {
			return;
		}
		CampaignCoalition coalition = attacker
				? raid.getAttackerCoalition()
				: (raid.getAttackerCoalition() != null ? raid.getAttackerCoalition().opposing() : null);
		Side side = coalitionSide(war, coalition);
		if (side == null) {
			return;
		}
		String campaignSideId = campaignSideIdForCoalition(coalition);
		Warband warband = Warband.createRaidShell(id, side, campaignSideId);
		WarbandManager.addWarband(warband);
	}

	private static void applyRaidLeaderRules(War war, Warband warband, UUID playerId, String playerName) {
		if (warband.isPendingLeader()) {
			warband.setLeaderId(playerId);
			return;
		}
		if (CampaignWarbandBattleService.isWarSideMainLeader(war, warband, playerName)) {
			warband.setLeaderId(playerId);
		}
	}

	private static String campaignSideIdForCoalition(CampaignCoalition coalition) {
		return coalition == CampaignCoalition.AGGRESSOR
				? BattleTemplate.ATTACKER_SIDE
				: BattleTemplate.DEFENDER_SIDE;
	}

	private static Side coalitionSide(War war, CampaignCoalition coalition) {
		if (war == null || coalition == null) {
			return null;
		}
		return coalition == CampaignCoalition.AGGRESSOR ? war.getAttackers() : war.getDefenders();
	}

	private static void deleteIfPresent(String warbandId) {
		if (warbandId == null) {
			return;
		}
		Warband warband = WarbandManager.getByString(warbandId);
		if (warband != null) {
			BattlePersistenceService.deleteWarband(warband);
		}
	}

	public static final class Listener implements org.bukkit.event.Listener {
		private final Map<UUID, String> pendingLeaderPromotion = new ConcurrentHashMap<>();

		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			CampaignRaidWarbandService.tryEnrollDefenderOnLogin(event.getPlayer());
		}

		@EventHandler(priority = EventPriority.LOW)
		public void onQuitLow(PlayerQuitEvent event) {
			Player player = event.getPlayer();
			Warband warband = WarbandManager.getByMemberId(player.getUniqueId());
			if (warband == null || !CampaignRaidWarbandService.isRaidWarband(warband)) {
				return;
			}
			if (player.getUniqueId().equals(warband.getLeaderId())) {
				pendingLeaderPromotion.put(player.getUniqueId(), warband.getId());
			}
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onQuitMonitor(PlayerQuitEvent event) {
			String warbandId = pendingLeaderPromotion.remove(event.getPlayer().getUniqueId());
			if (warbandId == null) {
				return;
			}
			Warband warband = WarbandManager.getByString(warbandId);
			if (warband != null) {
				CampaignRaidWarbandService.promoteLeaderIfNeeded(warband);
			}
		}

		void resetForTests() {
			pendingLeaderPromotion.clear();
		}
	}
}
