package me.Plugins.SimpleFactions.War.campaign.raid.fight;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidAttackerEliminationService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.TLibs.Utils.TimeFormatter;

public final class CampaignRaidBossBarService {
	private static final Map<String, RaidBossBars> BARS = new ConcurrentHashMap<>();

	private CampaignRaidBossBarService() {}

	public static void resetForTests() {
		for (RaidBossBars bars : BARS.values()) {
			bars.removeAll();
		}
		BARS.clear();
	}

	public static void onFightStarted(Battle battle, CampaignRaid raid) {
		if (battle == null || raid == null || !battle.isCampaignRaid() || !battle.hasStarted()) {
			return;
		}
		clear(battle);
		for (var side : battle.getSides()) {
			side.removeBossBar();
		}

		int rosterSize = Math.max(1, RaidAttackerEliminationService.countAttackerRoster(battle));
		long totalSeconds = Math.max(1L, Cache.campaignRaidDurationSeconds);
		if (raid.getFightEndsAt() != null && battle.getStartedAt() != null) {
			long computed = raid.getFightEndsAt().getEpochSecond() - battle.getStartedAt().getEpochSecond();
			if (computed > 0L) {
				totalSeconds = computed;
			}
		}

		String label = resolveRaidLabel(battle);
		BossBar timeBar = Bukkit.createBossBar(label, BarColor.BLUE, BarStyle.SOLID);
		BossBar raidersBar = Bukkit.createBossBar("Raiders remaining: " + rosterSize, BarColor.RED, BarStyle.SOLID);
		BARS.put(battle.getId(), new RaidBossBars(timeBar, raidersBar, rosterSize, totalSeconds));
		update(battle, raid);
	}

	public static void update(Battle battle, CampaignRaid raid) {
		if (battle == null || raid == null || !battle.isCampaignRaid() || !battle.hasStarted()) {
			return;
		}
		RaidBossBars bars = BARS.get(battle.getId());
		if (bars == null) {
			onFightStarted(battle, raid);
			bars = BARS.get(battle.getId());
			if (bars == null) {
				return;
			}
		}

		List<Player> viewers = battle.getAllParticipants();
		bars.syncViewers(viewers);

		Instant now = CampaignClock.now();
		long remainingSeconds = 0L;
		if (raid.getFightEndsAt() != null) {
			remainingSeconds = Math.max(0L, raid.getFightEndsAt().getEpochSecond() - now.getEpochSecond());
		}
		double timeProgress = Math.min(1.0, Math.max(0.0, (double) remainingSeconds / (double) bars.totalSeconds()));
		bars.timeBar().setProgress(timeProgress);
		bars.timeBar().setTitle(resolveRaidLabel(battle) + " - "
				+ formatRemaining(remainingSeconds));

		int remainingRaiders = RaidAttackerEliminationService.countActiveAttackers(battle);
		double raiderProgress = Math.min(1.0, Math.max(0.0,
				(double) remainingRaiders / (double) bars.initialRaiders()));
		bars.raidersBar().setProgress(raiderProgress);
		bars.raidersBar().setTitle("Raiders remaining: " + remainingRaiders);
	}

	public static void clear(Battle battle) {
		if (battle == null) {
			return;
		}
		RaidBossBars bars = BARS.remove(battle.getId());
		if (bars != null) {
			bars.removeAll();
		}
	}

	public static void tickCampaignRaid(Battle battle) {
		if (battle == null || !battle.isCampaignRaid() || !battle.hasStarted()) {
			return;
		}
		Integer warId = battle.getWarId();
		if (warId == null) {
			clear(battle);
			return;
		}
		War war = WarManager.getById(warId);
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (war == null || raid == null || raid.getState() != CampaignRaidState.FIGHTING) {
			clear(battle);
			for (var side : battle.getSides()) {
				side.removeBossBar();
			}
			return;
		}
		update(battle, raid);
	}

	private static String resolveRaidLabel(Battle battle) {
		if (battle == null) {
			return "Campaign raid";
		}
		String displayName = battle.getDisplayName();
		if (displayName != null && !displayName.isBlank()) {
			return displayName;
		}
		return "Campaign raid";
	}

	private static String formatRemaining(long remainingSeconds) {
		if (remainingSeconds <= 0L) {
			return "0s";
		}
		return TimeFormatter.formatTime((int) remainingSeconds);
	}

	private record RaidBossBars(
			BossBar timeBar,
			BossBar raidersBar,
			int initialRaiders,
			long totalSeconds) {

		void syncViewers(List<Player> viewers) {
			if (timeBar == null || raidersBar == null) {
				return;
			}
			timeBar.setVisible(true);
			raidersBar.setVisible(true);
			for (Player player : viewers) {
				if (player == null) {
					continue;
				}
				if (!timeBar.getPlayers().contains(player)) {
					timeBar.addPlayer(player);
				}
				if (!raidersBar.getPlayers().contains(player)) {
					raidersBar.addPlayer(player);
				}
			}
			for (Player player : List.copyOf(timeBar.getPlayers())) {
				if (!viewers.contains(player)) {
					timeBar.removePlayer(player);
				}
			}
			for (Player player : List.copyOf(raidersBar.getPlayers())) {
				if (!viewers.contains(player)) {
					raidersBar.removePlayer(player);
				}
			}
		}

		void removeAll() {
			if (timeBar != null) {
				timeBar.removeAll();
				timeBar.setVisible(false);
			}
			if (raidersBar != null) {
				raidersBar.removeAll();
				raidersBar.setVisible(false);
			}
		}
	}
}
