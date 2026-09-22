package me.Plugins.SimpleFactions;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.War.enums.WarGoalType;

import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.laws.LawEffect;
import me.Plugins.SimpleFactions.War.battle.enums.BattleLootMode;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;

public class Cache {
	public static String mapRef;
	/** Chapter slug for a future Archive prefill. Not the live upload folder. */
	public static String chapterId = "unknown";
	public static String chapterName = "Unknown";
	public static String worldName;

	public static int maxMembers;
	public static int maxWealthPrestige;
	public static double prestigePerTradePower = 0.1;
	public static double prestigeFromTradeSoftCap = 2000;
	public static double prestigeFromTradeFalloff = 0.1;
	public static double maxPlaytimePrestigeExponent = 5;
	public static String baseYear;
	public static int baseIrlYear = java.time.Year.now().getValue();
	public static String bankBlock;
	public static String votingBlock;

	public static int maxExtraNodeCapacity;
	
	public static int maxUntitledProvinces;
	public static int maxFreeTitles;
	public static double deJureRequirement;
	public static boolean mapEnabled;
	public static boolean provincesEnabled = true;
	public static boolean chronicleEnabled = true;
	public static final String PROVINCES_DISABLED_MESSAGE = "§cProvinces are disabled on this server";

	public static boolean requireProvinces(org.bukkit.command.CommandSender sender) {
		if (provincesEnabled) {
			return true;
		}
		sender.sendMessage(PROVINCES_DISABLED_MESSAGE);
		return false;
	}

	public static boolean dividendRequirePreviousTickMembership = true;

	public static int provinceCost;

	public static int settlementLargePopulationThreshold;

	public static int portSeaProximityBlocks;

	public static boolean warRequireDeclareCode;
	public static int warDeclareCodeTimeoutSeconds = 10;
	public static int warDeclareOpinionThreshold;
	public static double warInitiativeFactor = 1.5;
	public static int warPortSeaZocRadius = 2;
	public static double warReparationsIncomePercent = 25;
	public static int warReparationsDays = 10;
	public static Map<WarGoalType, Integer> warGoalMaxBattles = new EnumMap<>(WarGoalType.class);
	public static final int MAX_BATTLES_PER_LEG = 4;
	public static List<String> openMarketDefenderMustNotHave = List.of();
	public static List<String> openMarketAttackerMustNotHave = List.of();
	public static String openMarketApplyDefenderLaw = "";
	public static String civilWarVassalageGroup = "vassalage";
	public static String civilWarVassalageLaw = "inclusive";
	public static int pillageRangeProvinces = 3;
	public static int pillageLootDays = 10;
	public static double pillageTradeHitPercent = -100;
	public static int pillageTradeHitDays = 10;
	public static int warDeclinedAllyStabilityPenalty;
	public static boolean warFirstBattleAtBorder;
	public static int warProvincesBetweenBattles;
	public static boolean warOccupationIncludeEnemyNeighbors;
	public static double warPathfinderNeutralPenalty;
	public static boolean warPathfinderSeaPassEnabled;
	public static double warPathfinderWaterCost;

	public static boolean loggingEnabled;
	public static boolean wipeLog;

	public static int warBattleWindowStartHour;
	public static int warBattleWindowEndHour;
	public static int warRaidWindowStartHour;
	public static int warRaidWindowEndHour;
	public static int warVoteCloseHour;
	public static int warDefenderChoiceDeadlineHour;
	public static boolean warOneBattlePerDay;
	public static boolean warFirstBattleDayAfterDeclare;
	public static int warBattleVotingMinPlayers;
	public static boolean warBattleVotingRequireSmallestSideFull;
	public static boolean warBattleVotingPassIfEither;
	public static int warBattleVotingDevMinPlayers;
	public static boolean warBattleVotingDevMinPlayersEnabled;
	public static int warBattleLivesPerRegiment;
	public static int warBattleMinSideLives;
	public static BattleLootMode battleLootMode;
	public static List<String> battleLootCommands = new ArrayList<>();
	public static String battleLootItemPath;
	public static int battleLootItemAmount;

	public static int campaignRaidMusterSeconds;
	public static List<Integer> campaignRaidMusterReminderSecondsBefore = new ArrayList<>();
	public static int campaignRaidDurationSeconds;
	public static int campaignRaidRepairLockHours;
	public static int campaignRaidIntruderDamageIntervalTicks;
	public static int campaignRaidIntruderDamageAmount;

	public static int battleProvincePollIntervalTicks;
	public static int battleProvinceLeaveCountdownSeconds;
	public static boolean battleProvinceBlockProtectionEnabled;
	public static int battleCaptureMinPlayers;
	public static int battleRetreatMinElapsedSeconds;
	public static List<Integer> battleSignupReminderSecondsBefore = new ArrayList<>();
	public static int warDevmodePhantomCount;
	public static int battleSiegeContestDurationSeconds;
	public static DefenderRespawnMode battleRaidDefenderRespawnModeDefault;
	public static String battleCampaignTemplateField;
	public static String battleCampaignTemplateSiege;
	public static String battleCampaignTemplateRaid;

	public static double battleItemDurabilityMultiplier;

	public static HashMap<String, String> icons = new HashMap<>();

	public static double branchUpgradeCost;
	public static double branchUpgradeExponent;

	public static double mercenaryFormationCost = 100.0;
	public static int mercenaryFormationSeconds = 86400;
	public static double mercenarySlotUpkeep = 8.0;
	public static double mercenaryMinPricePerBattle = 50.0;
	public static double mercenaryMinPricePerDay = 10.0;
	public static int mercenaryMaxContractDays = 14;
	public static double mercenaryDefaultBreachRefund = 500.0;

	public static Map<Scope, LawEffect> baseEffects = new HashMap<>();

	public static Map<Terrain, Double> tradeCarry = new HashMap<>();
	public static double getTradeCarry(Terrain t) {
		return tradeCarry.getOrDefault(t, 0.5);
	}

	public static String getFantasyYear(long timestamp) {
		// Current IRL year of the timestamp
		int irlYear = Instant.ofEpochMilli(timestamp)
				.atZone(ZoneId.systemDefault())
				.getYear();

		// Parse configured fantasy year (e.g. "322 AE")
		String[] parts = baseYear.split(" ", 2);
		int baseFantasyYear = Integer.parseInt(parts[0]);
		String era = parts.length > 1 ? parts[1] : "";

		// 1-to-1 year mapping
		int yearDelta = irlYear - baseIrlYear;
		int fantasyYear = baseFantasyYear + yearDelta;

		return fantasyYear + (era.isEmpty() ? "" : " " + era);
	}

	public static String getFantasyDate(long timestamp) {

		var zonedDateTime = Instant.ofEpochMilli(timestamp)
				.atZone(ZoneId.systemDefault());

		int day = zonedDateTime.getDayOfMonth();
		int month = zonedDateTime.getMonthValue();

		String fantasyYear = getFantasyYear(timestamp);

		return String.format("%02d/%02d/%s", day, month, fantasyYear);
	}
}
