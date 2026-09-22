package net.tfminecraft.simplefactions.war.campaign.raid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.JoinResult;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandVehicleRules;
import net.tfminecraft.simplefactions.war.core.War;

public final class CampaignRaidJoinService {
	private CampaignRaidJoinService() {}

	public static War findWarByRaidId(String raidId) {
		if (raidId == null || raidId.isBlank()) {
			return null;
		}
		for (War war : WarManager.getActive()) {
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid != null && matchesRaidJoinId(raid, raidId)) {
				return war;
			}
		}
		return null;
	}

	public static boolean matchesRaidJoinId(CampaignRaid raid, String raidId) {
		if (raid == null || raidId == null || raidId.isBlank()) {
			return false;
		}
		if (raidId.equalsIgnoreCase(raid.getId())) {
			return true;
		}
		if (raid.getDisplayName() != null
				&& raidId.equalsIgnoreCase(BattleNamingService.slugifyDisplayName(raid.getDisplayName()))) {
			return true;
		}
		return false;
	}

	public static List<String> listJoinableRaidIds(Faction faction) {
		if (faction == null) {
			return List.of();
		}
		List<String> ids = new ArrayList<>();
		for (War war : WarManager.getActive()) {
			if (war.getSide(faction) == null) {
				continue;
			}
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid == null || raid.getState() != CampaignRaidState.MUSTER) {
				continue;
			}
			CampaignCoalition coalition = CampaignRaidService.coalitionForFaction(war, faction);
			if (coalition != raid.getAttackerCoalition()) {
				continue;
			}
			ids.add(raid.getId());
		}
		return List.copyOf(ids);
	}

	public static JoinResult join(
			War war,
			UUID playerId,
			String playerName,
			Faction faction,
			String raidId,
			Instant now) {
		if (war == null || !war.isActive() || playerId == null || faction == null || raidId == null) {
			return JoinResult.REJECTED_RAID_NOT_FOUND;
		}
		if (war.getSide(faction) == null) {
			return JoinResult.REJECTED_NOT_PARTICIPANT;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || !matchesRaidJoinId(raid, raidId)) {
			return JoinResult.REJECTED_RAID_NOT_FOUND;
		}
		if (raid.getState() != CampaignRaidState.MUSTER) {
			return JoinResult.REJECTED_NOT_MUSTER;
		}
		CampaignCoalition coalition = CampaignRaidService.coalitionForFaction(war, faction);
		if (coalition != raid.getAttackerCoalition()) {
			return JoinResult.REJECTED_NOT_ATTACKER_COALITION;
		}
		if (WarbandManager.getByMemberId(playerId) != null) {
			return JoinResult.REJECTED_IN_WARBAND;
		}
		Player player = Bukkit.getPlayer(playerId);
		if (player != null && player.isOnline() && WarbandVehicleRules.joinBlockedReason(player) != null) {
			return JoinResult.REJECTED_MOUNTED_ON_VEHICLE;
		}
		String playerKey = playerId.toString();
		if (raid.getMusterParticipantIds().contains(playerKey)) {
			return JoinResult.REJECTED_ALREADY_JOINED;
		}
		raid.getMusterParticipantIds().add(playerKey);
		CampaignRaidWarbandService.signupAttacker(war, raid, playerId, playerName);
		return JoinResult.OK;
	}
}
