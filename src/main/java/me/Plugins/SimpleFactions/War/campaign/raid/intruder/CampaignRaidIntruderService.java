package me.Plugins.SimpleFactions.War.campaign.raid.intruder;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidMessages;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import me.Plugins.SimpleFactions.Events.PlayerProvinceEnterEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.SimpleFactions;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Map.presence.ProvincePresenceService;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidAttackerEliminationService;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationLookup;

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
