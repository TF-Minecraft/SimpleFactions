package net.tfminecraft.simplefactions.war.campaign.raid.intruder;


import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidMessages;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.tfminecraft.simplefactions.events.PlayerProvinceEnterEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.SimpleFactions;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.presence.ProvincePresenceService;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationLookup;

public final class CampaignRaidIntruderService {
	private static final Set<UUID> intruderDeathPending = ConcurrentHashMap.newKeySet();
	private static final Set<String> enterWarningsSent = ConcurrentHashMap.newKeySet();

	private CampaignRaidIntruderService() {}

	public static void resetForTests() {
		intruderDeathPending.clear();
		enterWarningsSent.clear();
	}

	public static boolean consumeIntruderDeath(UUID playerId) {
		return playerId != null && intruderDeathPending.remove(playerId);
	}

	public static void clearForRaid(CampaignRaid raid) {
		if (raid == null || raid.getId() == null) {
			return;
		}
		String prefix = raid.getId() + ":";
		enterWarningsSent.removeIf(key -> key.startsWith(prefix));
	}

	public static void onProvinceEnter(Player player, int provinceId) {
		if (player == null) {
			return;
		}
		for (War war : WarManager.getActive()) {
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid == null) {
				continue;
			}
			if (!shouldPenalize(war, raid, player.getUniqueId(), player.getName(), provinceId)) {
				continue;
			}
			warnOnEnter(player, raid);
			return;
		}
	}

	public static void processTick() {
		for (War war : WarManager.getActive()) {
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid == null || raid.getState() != CampaignRaidState.FIGHTING) {
				continue;
			}
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player == null) {
					continue;
				}
				int provinceId = ProvincePresenceService.getInstance().getCurrentProvince(player.getUniqueId());
				if (!shouldPenalize(war, raid, player.getUniqueId(), player.getName(), provinceId)) {
					continue;
				}
				player.sendMessage(CampaignRaidMessages.INTRUDER);
				applyDamage(player);
			}
		}
	}

	static boolean shouldPenalize(
			War war,
			CampaignRaid raid,
			UUID playerId,
			String playerName,
			int playerProvinceId) {
		if (war == null || raid == null || playerId == null || playerName == null) {
			return false;
		}
		if (raid.getState() != CampaignRaidState.FIGHTING) {
			return false;
		}
		if (raid.getBattleId() == null || raid.getBattleId().isBlank()) {
			return false;
		}
		Installation target = InstallationLookup.findById(raid.getTargetInstallationId());
		if (target == null || playerProvinceId != target.getProvince()) {
			return false;
		}
		Faction faction = FactionManager.getByMember(playerName);
		CampaignCoalition coalition = CampaignRaidService.coalitionForFaction(war, faction);
		if (coalition == null || coalition != raid.getAttackerCoalition()) {
			return false;
		}
		return !isActiveAttackerParticipant(raid, playerId);
	}

	private static boolean isActiveAttackerParticipant(CampaignRaid raid, UUID playerId) {
		Warband attacker = CampaignRaidWarbandService.getAttackerWarband(raid);
		if (attacker == null || !attacker.hasMember(playerId)) {
			return false;
		}
		Battle battle = BattleManager.getByString(raid.getBattleId());
		return battle != null && !RaidAttackerEliminationService.isMarkedOut(battle, playerId);
	}

	private static void warnOnEnter(Player player, CampaignRaid raid) {
		if (player == null || raid == null || raid.getId() == null) {
			return;
		}
		String key = raid.getId() + ":" + player.getUniqueId();
		if (!enterWarningsSent.add(key)) {
			return;
		}
		player.sendMessage(CampaignRaidMessages.INTRUDER);
	}

	private static void applyDamage(Player player) {
		if (player == null || !player.isOnline()) {
			return;
		}
		double amount = Cache.campaignRaidIntruderDamageAmount;
		if (player.getHealth() - amount <= 0.0) {
			intruderDeathPending.add(player.getUniqueId());
		}
		player.damage(amount);
	}

	public static final class Listener implements org.bukkit.event.Listener {
		@EventHandler
		public void onProvinceEnter(PlayerProvinceEnterEvent event) {
			if (event == null || event.getPlayer() == null) {
				return;
			}
			CampaignRaidIntruderService.onProvinceEnter(event.getPlayer(), event.getProvinceId());
		}
	}

	public static final class Tick {
		private Tick() {}

		public static void start() {
			long interval = Math.max(1L, Cache.campaignRaidIntruderDamageIntervalTicks);
			new BukkitRunnable() {
				@Override
				public void run() {
					CampaignRaidIntruderService.processTick();
				}
			}.runTaskTimer(SimpleFactions.plugin, interval, interval);
		}
	}
}
