package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.export.ChapterIdentity;
import me.Plugins.SimpleFactions.War.battle.enums.BattleLootMode;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.laws.LawEffect;

public class ConfigLoader {
	public void loadConfig(File configFile) {
		FileConfiguration config = loadYaml(configFile);
		Cache.mapRef = config.getString("map-reference", "main");
		Cache.chapterId = ChapterIdentity.normalizeId(config.getString("map-id"));
		Cache.chapterName = ChapterIdentity.normalizeName(config.getString("map-name"));
		Cache.worldName = config.getString("world-name", "TFMC_Map");

		Cache.maxMembers = config.getInt("max-members", 64);
		Cache.maxWealthPrestige = config.getInt("max-prestige-from-wealth", 1000);
		Cache.prestigePerTradePower = config.getDouble("prestige-per-trade-power", 0.1);
		Cache.prestigeFromTradeSoftCap = config.getDouble("prestige-from-trade-soft-cap", 2000);
		Cache.prestigeFromTradeFalloff = config.getDouble("prestige-from-trade-falloff", 0.1);
		Cache.maxPlaytimePrestigeExponent = config.getDouble("max-prestige-playtime-exponent", 5);
		Cache.bankBlock = config.getString("bank-block", "v.lodestone");
		Cache.maxExtraNodeCapacity = config.getInt("max-extra-node-capacity", 0);
		
		Cache.deJureRequirement = config.getDouble("de-jure-requirement", 100.0);
		Cache.maxUntitledProvinces = config.getInt("max-untitled-provinces", 5);
		Cache.maxFreeTitles = config.getInt("max-free-titles", 3);
		Cache.mapEnabled = config.getBoolean("enable-map", false);
		Cache.provincesEnabled = config.getBoolean("enable-provinces", true);
		Cache.chronicleEnabled = config.getBoolean("enable-chronicle", true);
		if (!Cache.provincesEnabled) {
			Cache.mapEnabled = false;
		}

		Cache.provinceCost = config.getInt("province-cost", 50);
		Cache.dividendRequirePreviousTickMembership =
				config.getBoolean("dividend-require-previous-tick-membership", true);

		Cache.mercenaryFormationCost = config.getDouble("mercenary-formation-cost", 100.0);
		Cache.mercenaryFormationSeconds = config.getInt("mercenary-formation-seconds", 86400);
		Cache.mercenarySlotUpkeep = config.getDouble("mercenary-slot-upkeep", 8.0);
		Cache.mercenaryMinPricePerBattle = config.getDouble("mercenary-min-price-per-battle", 50.0);
		Cache.mercenaryMinPricePerDay = config.getDouble("mercenary-min-price-per-day", 10.0);
		Cache.mercenaryMaxContractDays = config.getInt("mercenary-max-contract-days", 14);
		Cache.mercenaryDefaultBreachRefund = config.getDouble("mercenary-default-breach-refund", 500.0);

		Cache.settlementLargePopulationThreshold = config.getInt("settlement-large-population-threshold", 8);
		Cache.portSeaProximityBlocks = config.getInt("port-sea-proximity-blocks", 20);

		Cache.loggingEnabled = config.getBoolean("logging", true);
		Cache.wipeLog = config.getBoolean("wipe-log", true);

		Cache.battleProvincePollIntervalTicks = config.getInt("battle.province_poll_interval_ticks", 20);
		Cache.battleProvinceLeaveCountdownSeconds = config.getInt("battle.province_leave_countdown_seconds", 10);
		Cache.battleProvinceBlockProtectionEnabled =
				config.getBoolean("battle.province_block_protection_enabled", false);
		Cache.battleCaptureMinPlayers = config.getInt("battle.capture_min_players", 1);
		Cache.battleRetreatMinElapsedSeconds = config.getInt("battle.retreat_min_elapsed_seconds", 1200);
		Cache.battleSignupReminderSecondsBefore = loadReminderOffsets(
				config,
				"battle.signup_reminder_seconds_before",
				List.of(1800, 600, 300, 60));
		Cache.battleSiegeContestDurationSeconds = config.getInt("battle.siege.contest_duration_seconds", 180);
		Cache.battleRaidDefenderRespawnModeDefault = DefenderRespawnMode.fromJson(
				config.getString("battle.raid.defender_respawn_mode_default", "INFINITE"));
		if (Cache.battleRaidDefenderRespawnModeDefault == null) {
			Cache.battleRaidDefenderRespawnModeDefault = DefenderRespawnMode.INFINITE;
		}
		Cache.battleCampaignTemplateField = config.getString("battle.campaign_template.field", "field_default");
		Cache.battleCampaignTemplateSiege = config.getString("battle.campaign_template.siege", "siege_default");
		Cache.battleCampaignTemplateRaid = config.getString("battle.campaign_template.raid", "raid_template");
		Cache.battleItemDurabilityMultiplier = clampUnit(
				config.getDouble("battle.item_durability_multiplier", 0.2));
		validateBattlePresenceConfig();
		validateBattleTemplateDefaultsConfig();

		Cache.branchUpgradeCost = config.getDouble("branch-upgrade-cost", 100.0);
		Cache.branchUpgradeExponent = config.getDouble("branch-upgrade-exponent", 1.1);
		Cache.votingBlock = config.getString("voting-block", "v(chiseled_bookshelf)");
		Cache.baseYear = config.getString("starting-year", "372 AE");

		if(config.contains("terrain-modifiers")) {
			for(String s : config.getStringList("terrain-modifiers")) {
				String[] args = s.split("\\s+");
				if(args.length != 2) continue;
				try {
					Cache.tradeCarry.put(Terrain.valueOf(args[0].toUpperCase()), Double.parseDouble(args[1]));
				} catch (Exception e) {
					// TODO: handle exception
					Bukkit.getLogger().info("Could not parse "+s);
				}
			}
		}
		
		if(config.contains("icons")) {
			for(String s : config.getStringList("icons")) {
				String id = s.split("\\(")[0];
				String path = s.split("\\(")[1].replace(")", "");
				Cache.icons.put(id, path);
			}
		}

		if(config.contains("base-effects")) {
            for(String s : config.getConfigurationSection("base-effects").getKeys(false)) {
                try {
                    Scope scope = Scope.valueOf(s.toUpperCase());
                    Cache.baseEffects.put(scope, new LawEffect(scope, config.getConfigurationSection("base-effects."+s)));
                } catch (Exception e) {
                    Bukkit.getLogger().info("[SimpleFactions] could not parse modifier for scope "+s);
                    // TODO: handle exception
                }
            }
        }
	}

	public void loadWar(File warFile) {
		FileConfiguration config = loadYaml(warFile);
		if (!config.contains("war") && warFile != null && warFile.getParentFile() != null) {
			FileConfiguration fromConfig = loadYaml(new File(warFile.getParentFile(), "config.yml"));
			if (fromConfig.contains("war")) {
				config = fromConfig;
			}
		}
		Cache.warRequireDeclareCode = config.getBoolean("war.require_declare_code", false);
		Cache.warDeclareCodeTimeoutSeconds =
				Math.max(1, config.getInt("war.declare_code_timeout_seconds", 10));
		Cache.warDeclareOpinionThreshold = config.getInt("war.declare_opinion_threshold", -50);
		Cache.warInitiativeFactor = config.getDouble("war.initiative_factor", 1.5);
		Cache.warPortSeaZocRadius = config.getInt("war.port_sea_zoc_radius", 2);
		Cache.warReparationsIncomePercent = config.getDouble("war.reparations.income_percent", 25);
		Cache.warReparationsDays = config.getInt("war.reparations.days", 10);
		Cache.warGoalMaxBattles = new EnumMap<>(WarGoalType.class);
		for (WarGoalType goal : WarGoalType.values()) {
			int maxBattles = config.getInt("war.goals." + goal.name() + ".max_battles_per_leg", -1);
			if (maxBattles < 0) {
				maxBattles = config.getInt("war.goals." + goal.toJson() + ".max_battles_per_leg", -1);
			}
			if (maxBattles < 0) {
				maxBattles = config.getInt("war.goals." + goal.name() + ".max_battles", -1);
			}
			if (maxBattles < 0) {
				maxBattles = config.getInt("war.goals." + goal.toJson() + ".max_battles", 4);
			}
			if (maxBattles > Cache.MAX_BATTLES_PER_LEG) {
				if (Bukkit.getServer() != null) {
					Bukkit.getLogger().warning("[SimpleFactions] war.goals." + goal.name()
							+ " max_battles_per_leg=" + maxBattles + " exceeds cap "
							+ Cache.MAX_BATTLES_PER_LEG + "; clamping.");
				}
				maxBattles = Cache.MAX_BATTLES_PER_LEG;
			}
			Cache.warGoalMaxBattles.put(goal, maxBattles);
		}
		Cache.openMarketDefenderMustNotHave = warGoalStringList(config, "defender_must_not_have");
		Cache.openMarketAttackerMustNotHave = warGoalStringList(config, "attacker_must_not_have");
		Cache.openMarketApplyDefenderLaw = warGoalString(config, "apply_defender_law");
		Cache.civilWarVassalageGroup = config.getString("war.civil_war.vassalage_group", "vassalage");
		Cache.civilWarVassalageLaw = config.getString("war.civil_war.vassalage_law", "inclusive");
		Cache.pillageRangeProvinces = pillageInt(config, "range_provinces", 3);
		Cache.pillageLootDays = pillageInt(config, "loot_days", 10);
		Cache.pillageTradeHitPercent = pillageDouble(config, "trade_hit_percent", -100);
		Cache.pillageTradeHitDays = pillageInt(config, "trade_hit_days", 10);
		Cache.warFirstBattleAtBorder = config.getBoolean("war.battle_cadence.first_battle_at_border", true);
		Cache.warProvincesBetweenBattles = config.getInt("war.battle_cadence.provinces_between_battles", 3);
		Cache.warOccupationIncludeEnemyNeighbors = config.getBoolean("war.occupation.include_enemy_neighbors", true);
		Cache.warDeclinedAllyStabilityPenalty = config.getInt("war.declined_ally_stability_penalty", -30);
		Cache.warPathfinderNeutralPenalty = config.getDouble("war.pathfinder.neutral_penalty", 8.0);
		Cache.warPathfinderSeaPassEnabled = config.getBoolean("war.pathfinder.sea_pass_enabled", true);
		Cache.warPathfinderWaterCost = config.getDouble("war.pathfinder.water_cost", 0.0);

		Cache.warBattleWindowStartHour = config.getInt("war.battle_schedule.window_start_hour", 21);
		Cache.warBattleWindowEndHour = config.getInt("war.battle_schedule.window_end_hour", 24);
		Cache.warRaidWindowStartHour = config.getInt("war.battle_schedule.raid_window_start_hour", 19);
		Cache.warRaidWindowEndHour = config.getInt("war.battle_schedule.raid_window_end_hour", 20);
		Cache.warVoteCloseHour = config.getInt("war.battle_schedule.vote_close_hour", 16);
		Cache.warDefenderChoiceDeadlineHour = config.getInt("war.battle_schedule.defender_choice_deadline_hour", 12);
		Cache.warOneBattlePerDay = config.getBoolean("war.battle_schedule.one_battle_per_day", true);
		Cache.warFirstBattleDayAfterDeclare = config.getBoolean("war.battle_schedule.first_battle_day_after_declare", true);
		Cache.warBattleVotingMinPlayers = config.getInt("war.battle_voting.min_players", 4);
		Cache.warBattleVotingRequireSmallestSideFull = config.getBoolean("war.battle_voting.require_smallest_side_full", true);
		Cache.warBattleVotingPassIfEither = config.getBoolean("war.battle_voting.pass_if_either", true);
		Cache.warBattleVotingDevMinPlayersEnabled = config.contains("war.battle_voting.dev_min_players");
		if (Cache.warBattleVotingDevMinPlayersEnabled) {
			Cache.warBattleVotingDevMinPlayers = config.getInt("war.battle_voting.dev_min_players");
		} else {
			Cache.warBattleVotingDevMinPlayers = Cache.warBattleVotingMinPlayers;
		}
		Cache.warDevmodePhantomCount = config.getInt("war.devmode.phantom_count", 10);
		validateBattleScheduleConfig();

		Cache.warBattleLivesPerRegiment = config.getInt("war.battle_military.lives_per_regiment", 5);
		Cache.warBattleMinSideLives = config.getInt("war.battle_military.min_side_lives", 1);
		Cache.battleLootMode = BattleLootMode.fromJson(config.getString("war.battle_loot.mode", "COMMAND"));
		if (Cache.battleLootMode == null) {
			Cache.battleLootMode = BattleLootMode.COMMAND;
		}
		Cache.battleLootCommands = new ArrayList<>(config.getStringList("war.battle_loot.commands"));
		Cache.battleLootItemPath = config.getString("war.battle_loot.item", "");
		Cache.battleLootItemAmount = Math.max(1, config.getInt("war.battle_loot.item_amount", 1));
		Cache.campaignRaidMusterSeconds = config.getInt("war.campaign_raid.muster_seconds", 60);
		Cache.campaignRaidMusterReminderSecondsBefore = loadReminderOffsets(
				config,
				"war.campaign_raid.muster_reminder_seconds_before",
				List.of(45, 30, 15, 10));
		Cache.campaignRaidDurationSeconds = config.getInt("war.campaign_raid.duration_seconds", 600);
		Cache.campaignRaidRepairLockHours = config.getInt("war.campaign_raid.repair_lock_hours", 48);
		Cache.campaignRaidIntruderDamageIntervalTicks =
				config.getInt("war.campaign_raid.intruder_damage_interval_ticks", 10);
		Cache.campaignRaidIntruderDamageAmount =
				config.getInt("war.campaign_raid.intruder_damage_amount", 4);
		validateCampaignRaidConfig();
		validateWarDevmodeConfig();
	}

	private static FileConfiguration loadYaml(File file) {
		FileConfiguration config = new YamlConfiguration();
		try {
			if (file != null && file.exists()) {
				config.load(file);
			}
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
		return config;
	}

	private static void validateBattleScheduleConfig() {
		// Hours in war.battle_schedule are Europe/Paris (CET/CEST), not UTC.
		int defenderDeadline = Cache.warDefenderChoiceDeadlineHour;
		int voteClose = Cache.warVoteCloseHour;
		int raidStart = Cache.warRaidWindowStartHour;
		int raidEnd = Cache.warRaidWindowEndHour;
		int windowStart = Cache.warBattleWindowStartHour;
		int windowEnd = Cache.warBattleWindowEndHour;

		if (defenderDeadline < 0 || defenderDeadline >= voteClose) {
			failBattleSchedule("war.battle_schedule.defender_choice_deadline_hour must be >= 0 and < vote_close_hour");
		}
		if (voteClose >= raidStart) {
			failBattleSchedule("war.battle_schedule.vote_close_hour must be < raid_window_start_hour");
		}
		if (raidStart > raidEnd) {
			failBattleSchedule("war.battle_schedule requires raid_window_start_hour <= raid_window_end_hour");
		}
		if (raidEnd >= windowStart) {
			failBattleSchedule("war.battle_schedule.raid_window_end_hour must be < window_start_hour");
		}
		if (windowStart > windowEnd || windowEnd > 24) {
			failBattleSchedule("war.battle_schedule requires window_start_hour <= window_end_hour <= 24");
		}
		if (Cache.warBattleVotingMinPlayers < 1) {
			failBattleSchedule("war.battle_voting.min_players must be >= 1");
		}
		if (Cache.warBattleVotingDevMinPlayersEnabled && Cache.warBattleVotingDevMinPlayers < 1) {
			failBattleSchedule("war.battle_voting.dev_min_players must be >= 1");
		}
	}

	private static void failBattleSchedule(String message) {
		if (Bukkit.getServer() != null) {
			Bukkit.getLogger().severe("[SimpleFactions] " + message);
		}
		throw new IllegalStateException(message);
	}

	private static void validateCampaignRaidConfig() {
		if (Cache.campaignRaidMusterSeconds < 1) {
			failBattleSchedule("war.campaign_raid.muster_seconds must be >= 1");
		}
		if (Cache.campaignRaidDurationSeconds < 1) {
			failBattleSchedule("war.campaign_raid.duration_seconds must be >= 1");
		}
		if (Cache.campaignRaidRepairLockHours < 1) {
			failBattleSchedule("war.campaign_raid.repair_lock_hours must be >= 1");
		}
		if (Cache.campaignRaidIntruderDamageIntervalTicks < 1) {
			failBattleSchedule("war.campaign_raid.intruder_damage_interval_ticks must be >= 1");
		}
		if (Cache.campaignRaidIntruderDamageAmount < 1) {
			failBattleSchedule("war.campaign_raid.intruder_damage_amount must be >= 1");
		}
		for (int offset : Cache.campaignRaidMusterReminderSecondsBefore) {
			if (offset < 1) {
				failBattleSchedule("war.campaign_raid.muster_reminder_seconds_before values must be >= 1");
			}
		}
	}

	private static void validateBattlePresenceConfig() {
		if (Cache.battleProvincePollIntervalTicks < 1) {
			failBattleSchedule("battle.province_poll_interval_ticks must be >= 1");
		}
		if (Cache.battleCaptureMinPlayers < 1) {
			failBattleSchedule("battle.capture_min_players must be >= 1");
		}
	}

	private static void validateWarDevmodeConfig() {
		if (Cache.warDevmodePhantomCount < 0) {
			failBattleSchedule("war.devmode.phantom_count must be >= 0");
		}
	}

	private static void validateBattleTemplateDefaultsConfig() {
		if (Cache.battleProvinceLeaveCountdownSeconds < 1) {
			failBattleSchedule("battle.province_leave_countdown_seconds must be >= 1");
		}
		if (Cache.battleSiegeContestDurationSeconds < 1) {
			failBattleSchedule("battle.siege.contest_duration_seconds must be >= 1");
		}
		if (Cache.battleRaidDefenderRespawnModeDefault == null) {
			failBattleSchedule("battle.raid.defender_respawn_mode_default must be INFINITE or LIVES");
		}
		for (int offset : Cache.battleSignupReminderSecondsBefore) {
			if (offset < 1) {
				failBattleSchedule("battle.signup_reminder_seconds_before values must be >= 1");
			}
		}
	}

	private static List<Integer> loadReminderOffsets(
			org.bukkit.configuration.file.FileConfiguration config,
			String path,
			List<Integer> defaults) {
		List<Integer> offsets = new ArrayList<>();
		List<?> raw = config.getList(path);
		if (raw == null) {
			offsets.addAll(defaults);
		} else {
			for (Object entry : raw) {
				if (entry instanceof Number number) {
					offsets.add(number.intValue());
				}
			}
		}
		offsets.sort(Collections.reverseOrder());
		return offsets;
	}

	static double clampUnit(double value) {
		if (value < 0.0) {
			return 0.0;
		}
		if (value > 1.0) {
			return 1.0;
		}
		return value;
	}

	private static int pillageInt(FileConfiguration config, String key, int defaultValue) {
		int value;
		if (config.contains("war.goals.PILLAGE." + key)) {
			value = config.getInt("war.goals.PILLAGE." + key);
		} else if (config.contains("war.goals.pillage." + key)) {
			value = config.getInt("war.goals.pillage." + key);
		} else {
			return defaultValue;
		}
		return value < 0 ? defaultValue : value;
	}

	private static double pillageDouble(FileConfiguration config, String key, double defaultValue) {
		if (config.contains("war.goals.PILLAGE." + key)) {
			return config.getDouble("war.goals.PILLAGE." + key);
		}
		if (config.contains("war.goals.pillage." + key)) {
			return config.getDouble("war.goals.pillage." + key);
		}
		return defaultValue;
	}

	private static List<String> warGoalStringList(FileConfiguration config, String key) {
		List<String> fromEnumName = trimmedLawIds(config.getStringList("war.goals.OPEN_MARKET." + key));
		if (!fromEnumName.isEmpty()) {
			return fromEnumName;
		}
		return trimmedLawIds(config.getStringList("war.goals.open_market." + key));
	}

	private static String warGoalString(FileConfiguration config, String key) {
		String fromEnumName = config.getString("war.goals.OPEN_MARKET." + key, "");
		if (fromEnumName != null && !fromEnumName.isBlank()) {
			return fromEnumName.trim();
		}
		String fromJsonId = config.getString("war.goals.open_market." + key, "");
		return fromJsonId == null ? "" : fromJsonId.trim();
	}

	private static List<String> trimmedLawIds(List<String> raw) {
		List<String> ids = new ArrayList<>();
		if (raw == null) {
			return List.of();
		}
		for (String id : raw) {
			if (id == null) {
				continue;
			}
			String trimmed = id.trim();
			if (!trimmed.isEmpty()) {
				ids.add(trimmed);
			}
		}
		return List.copyOf(ids);
	}
}
