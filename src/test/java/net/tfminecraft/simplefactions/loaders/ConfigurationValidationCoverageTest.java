package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.*;
import net.tfminecraft.simplefactions.government.stability.StabilityTuning;
import net.tfminecraft.simplefactions.guild.hub.HubTransport;
import net.tfminecraft.simplefactions.guild.hub.OpenTrackSettings;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.enums.DefenderRespawnMode;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationValidationCoverageTest {
  @TempDir Path directory;
  private FactionDomainFixture domain;
  private final Map<Field, Object> saved = new HashMap<>();
  private final Map<Field, Object> otherSettings = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    domain = new FactionDomainFixture();
    remember(StabilityTuning.class, "active");
    remember(HubTransport.class, "rates");
    for (String name : List.of("enabled", "share", "keptPer1000", "rangeBlocks"))
      remember(OpenTrackSettings.class, name);
    for (Field field : Cache.class.getFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
        continue;
      Object value = field.get(null);
      if (value instanceof Map<?, ?> map) value = new HashMap<>(map);
      else if (value instanceof List<?> list) value = new ArrayList<>(list);
      saved.put(field, value);
    }
  }

  @AfterEach
  void close() throws Exception {
    try {
      for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
      for (var entry : otherSettings.entrySet()) entry.getKey().set(null, entry.getValue());
    } finally {
      domain.close();
    }
  }

  private void remember(Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    otherSettings.put(field, field.get(null));
  }

  private Path write(String name, String yaml) throws Exception {
    return Files.writeString(directory.resolve(name), yaml);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "defender_choice_deadline_hour=16",
        "vote_close_hour=19",
        "raid_window_start_hour=21",
        "raid_window_end_hour=21",
        "window_end_hour=20",
        "window_end_hour=25"
      })
  void invalidScheduleOrderingIsRejectedWithTheSettingName(String input) throws Exception {
    String[] parts = input.split("=");
    YamlConfiguration config = new YamlConfiguration();
    config.set("war.battle_schedule." + parts[0], Integer.parseInt(parts[1]));
    Path path = write("war.yml", config.saveToString());
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(path.toFile()));
    assertTrue(error.getMessage().contains(parts[0]));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "battle_voting.min_players",
        "battle_voting.dev_min_players",
        "campaign_raid.muster_seconds",
        "campaign_raid.duration_seconds",
        "campaign_raid.repair_lock_hours",
        "campaign_raid.intruder_damage_interval_ticks",
        "campaign_raid.intruder_damage_amount",
        "devmode.phantom_count"
      })
  void invalidWarMinimumsAreRejected(String key) throws Exception {
    YamlConfiguration config = new YamlConfiguration();
    config.set("war." + key, -1);
    Path path = write("war.yml", config.saveToString());
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(path.toFile()));
    assertTrue(error.getMessage().contains(key));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "province_poll_interval_ticks",
        "capture_min_players",
        "province_leave_countdown_seconds",
        "siege.contest_duration_seconds"
      })
  void invalidBattleMinimumsAreRejected(String key) throws Exception {
    YamlConfiguration config = new YamlConfiguration();
    config.set("battle." + key, 0);
    Path path = write("config.yml", config.saveToString());
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> new ConfigLoader().loadConfig(path.toFile()));
    assertTrue(error.getMessage().contains(key));
  }

  @Test
  void reminderListsFilterNonNumbersSortOffsetsAndRejectNonPositiveNumbers() throws Exception {
    Path config =
        write(
            "config.yml",
            "battle:\n"
                + "  signup_reminder_seconds_before: [10, junk, 30, 20]\n"
                + "  raid:\n"
                + "    defender_respawn_mode_default: invalid\n");
    new ConfigLoader().loadConfig(config.toFile());
    assertEquals(List.of(30, 20, 10), Cache.battleSignupReminderSecondsBefore);
    assertEquals(DefenderRespawnMode.INFINITE, Cache.battleRaidDefenderRespawnModeDefault);
    Files.writeString(config, "battle:\n  signup_reminder_seconds_before: [0]\n");
    assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadConfig(config.toFile()));
    Path war =
        write("war.yml", "war:\n  campaign_raid:\n    muster_reminder_seconds_before: [-1]\n");
    assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(war.toFile()));
  }

  @Test
  void legacyWarFallbackReadsConfigAndClampsBattleLimits() throws Exception {
    write(
        "config.yml",
        "war:\n"
            + "  declare_opinion_threshold: -77\n"
            + "  autoresolve:\n"
            + "    luck: -2\n"
            + "  goals:\n"
            + "    SUBJUGATE:\n"
            + "      max_battles_per_leg: 999\n");
    new ConfigLoader().loadWar(directory.resolve("war.yml").toFile());
    assertEquals(-77, Cache.warDeclareOpinionThreshold);
    assertEquals(0, Cache.warAutoresolveLuck);
    assertEquals(Cache.MAX_BATTLES_PER_LEG, Cache.warGoalMaxBattles.get(WarGoalType.SUBJUGATE));
    Path war = write("war.yml", "war:\n  autoresolve:\n    luck: 2\n");
    new ConfigLoader().loadWar(war.toFile());
    assertEquals(1, Cache.warAutoresolveLuck);
  }

  @Test
  void legacyTransportAndValidEffectListsAreParsed() throws Exception {
    Path path =
        write(
            "config.yml",
            "supply-hubs:\n"
                + "  corridor-share: 0.3\n"
                + "max-trade-upkeep: -4\n"
                + "terrain-modifiers:\n"
                + "  - 'PLAINS 0.7'\n"
                + "  - 'wrong-token-count'\n"
                + "  - 'missing 0.8'\n"
                + "icons:\n"
                + "  - 'example(tfmc:example)'\n"
                + "base-effects:\n"
                + "  DOMESTIC_GUILDS:\n"
                + "    modifiers: ['TRADE_POWER(2)']\n"
                + "  INVALID: {}\n");
    new ConfigLoader().loadConfig(path.toFile());
    assertEquals(0.3, Cache.supplyHubCorridorShare);
    assertEquals(
        net.tfminecraft.simplefactions.guild.income.TradeUpkeep.DEFAULT_MAX, Cache.maxTradeUpkeep);
    assertEquals(0.7, Cache.tradeCarry.get(Terrain.PLAINS));
    assertEquals("tfmc:example", Cache.icons.get("example"));
    assertTrue(Cache.baseEffects.containsKey(Scope.DOMESTIC_GUILDS));
  }

  @Test
  void malformedIconAndScalarEffectsDoNotAbortTheConfigurationLoad() throws Exception {
    Path path =
        write("config.yml", "icons:\n  - 'broken'\n  - 'good(tfmc:good)'\nbase-effects: bad\n");
    assertDoesNotThrow(() -> new ConfigLoader().loadConfig(path.toFile()));
    assertEquals("tfmc:good", Cache.icons.get("good"));
    assertFalse(Cache.icons.containsKey("broken"));
  }

  @Test
  void invalidEconomySettingsUseTheirDocumentedDefaults() throws Exception {
    Path config =
        write(
            "config.yml",
            "elevation-base: -1\n"
                + "elevation-size-multiplier: .nan\n"
                + "elevation-exponent: .inf\n"
                + "eviction-multiplier: -2\n");
    new ConfigLoader().loadConfig(config.toFile());
    assertEquals(25.0, Cache.elevationBase);
    assertEquals(1.0, Cache.elevationSizeMultiplier);
    assertEquals(1.1, Cache.elevationExponent);
    assertEquals(2.0, Cache.evictionMultiplier);
  }

  @Test
  void malformedYamlKeepsTheLastLoadedConfiguration() throws Exception {
    Path config = write("config.yml", "max-members: 9\n");
    new ConfigLoader().loadConfig(config.toFile());
    Files.writeString(config, "broken: [\n");
    assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadConfig(config.toFile()));
    assertEquals(9, Cache.maxMembers);
    Path war = write("war.yml", "war:\n  declare_opinion_threshold: -77\n");
    new ConfigLoader().loadWar(war.toFile());
    Files.writeString(war, "broken: [\n");
    assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(war.toFile()));
    assertEquals(-77, Cache.warDeclareOpinionThreshold);
  }

  @Test
  void terrainNamesAreIndependentOfTheHostLocale() throws Exception {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      Cache.tradeCarry.remove(Terrain.PLAINS);
      Path path = write("config.yml", "terrain-modifiers: ['plains 0.25']\n");
      new ConfigLoader().loadConfig(path.toFile());
      assertEquals(0.25, Cache.tradeCarry.get(Terrain.PLAINS));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "province_poll_interval_ticks",
        "capture_min_players",
        "province_leave_countdown_seconds",
        "siege.contest_duration_seconds",
        "signup_reminder_seconds_before"
      })
  void invalidBattleReloadRetainsEveryPublishedSetting(String key) throws Exception {
    ConfigLoader loader = new ConfigLoader();
    Path path = write("config.yml", mainSettings(true).saveToString());
    loader.loadConfig(path.toFile());
    PublishedSettings prior = PublishedSettings.capture();
    YamlConfiguration rejected = mainSettings(false);
    rejected.set("battle." + key, key.endsWith("seconds_before") ? List.of(40, 0) : 0);
    Files.writeString(path, rejected.saveToString());

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> loader.loadConfig(path.toFile()));
    assertTrue(failure.getMessage().contains(key));
    prior.assertUnchanged();

    Files.writeString(path, mainSettings(false).saveToString());
    loader.loadConfig(path.toFile());
    assertEquals(24, Cache.maxMembers);
    assertEquals("candidate-world", Cache.worldName);
    assertEquals(66, StabilityTuning.get().legitimacyCommunity);
    assertEquals(0.7, HubTransport.rates(HubTransport.Mode.RAIL).trade());
    assertTrue(OpenTrackSettings.enabled());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "battle_schedule.defender_choice_deadline_hour=-1",
        "battle_schedule.defender_choice_deadline_hour=16",
        "battle_schedule.vote_close_hour=19",
        "battle_schedule.raid_window_start_hour=21",
        "battle_schedule.raid_window_end_hour=21",
        "battle_schedule.window_start_hour=25",
        "battle_schedule.window_end_hour=20",
        "battle_schedule.window_end_hour=25",
        "battle_voting.min_players=0",
        "battle_voting.dev_min_players=0",
        "campaign_raid.muster_seconds=0",
        "campaign_raid.duration_seconds=0",
        "campaign_raid.repair_lock_hours=0",
        "campaign_raid.intruder_damage_interval_ticks=0",
        "campaign_raid.intruder_damage_amount=0",
        "campaign_raid.muster_reminder_seconds_before=0",
        "devmode.phantom_count=-1"
      })
  void invalidWarReloadRetainsEveryPublishedSetting(String input) throws Exception {
    ConfigLoader loader = new ConfigLoader();
    Path path = write("war.yml", warSettings(true).saveToString());
    loader.loadWar(path.toFile());
    PublishedSettings prior = PublishedSettings.capture();
    String[] setting = input.split("=");
    int invalid = Integer.parseInt(setting[1]);
    YamlConfiguration rejected = warSettings(false);
    rejected.set(
        "war." + setting[0],
        setting[0].endsWith("seconds_before") ? List.of(45, invalid) : invalid);
    Files.writeString(path, rejected.saveToString());

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> loader.loadWar(path.toFile()));
    assertTrue(
        failure.getMessage().contains(setting[0].substring(setting[0].lastIndexOf('.') + 1)));
    prior.assertUnchanged();

    Files.writeString(path, warSettings(false).saveToString());
    loader.loadWar(path.toFile());
    assertEquals(-22, Cache.warDeclareOpinionThreshold);
    assertEquals(16, Cache.warVoteCloseHour);
    assertEquals(Cache.MAX_BATTLES_PER_LEG, Cache.warGoalMaxBattles.get(WarGoalType.SUBJUGATE));
    assertEquals(List.of("say candidate"), Cache.battleLootCommands);
  }

  @Test
  void invalidLegacyWarReloadRetainsEveryPublishedSetting() throws Exception {
    ConfigLoader loader = new ConfigLoader();
    Path war = write("war.yml", warSettings(true).saveToString());
    loader.loadWar(war.toFile());
    PublishedSettings prior = PublishedSettings.capture();
    YamlConfiguration rejected = warSettings(false);
    rejected.set("war.campaign_raid.muster_seconds", 0);
    Path legacy = write("config.yml", rejected.saveToString());
    Files.writeString(war, "{}\n");

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> loader.loadWar(war.toFile()));
    assertTrue(failure.getMessage().contains("war.campaign_raid.muster_seconds"));
    prior.assertUnchanged();

    Files.writeString(legacy, warSettings(false).saveToString());
    loader.loadWar(war.toFile());
    assertEquals(-22, Cache.warDeclareOpinionThreshold);
    assertEquals(60, Cache.campaignRaidMusterSeconds);
  }

  @Test
  void minimumValidNumbersAndDocumentedClampsStillLoad() throws Exception {
    YamlConfiguration config = mainSettings(false);
    config.set("battle.province_poll_interval_ticks", 1);
    config.set("battle.capture_min_players", 1);
    config.set("battle.province_leave_countdown_seconds", 1);
    config.set("battle.siege.contest_duration_seconds", 1);
    config.set("battle.signup_reminder_seconds_before", List.of(1));
    config.set("battle.raid.defender_respawn_mode_default", "unknown");
    config.set("battle.item_durability_multiplier", 2);
    config.set("battle.empty_side_grace_seconds", -3);
    new ConfigLoader().loadConfig(write("config.yml", config.saveToString()).toFile());
    assertEquals(1, Cache.battleProvincePollIntervalTicks);
    assertEquals(1, Cache.battleCaptureMinPlayers);
    assertEquals(1, Cache.battleProvinceLeaveCountdownSeconds);
    assertEquals(1, Cache.battleSiegeContestDurationSeconds);
    assertEquals(List.of(1), Cache.battleSignupReminderSecondsBefore);
    assertEquals(DefenderRespawnMode.INFINITE, Cache.battleRaidDefenderRespawnModeDefault);
    assertEquals(1.0, Cache.battleItemDurabilityMultiplier);
    assertEquals(0, Cache.battleEmptySideGraceSeconds);

    YamlConfiguration war = warSettings(false);
    war.set("war.battle_schedule.defender_choice_deadline_hour", 0);
    war.set("war.battle_schedule.vote_close_hour", 1);
    war.set("war.battle_schedule.raid_window_start_hour", 2);
    war.set("war.battle_schedule.raid_window_end_hour", 2);
    war.set("war.battle_schedule.window_start_hour", 3);
    war.set("war.battle_schedule.window_end_hour", 24);
    war.set("war.battle_voting.min_players", 1);
    war.set("war.battle_voting.dev_min_players", 1);
    war.set("war.devmode.phantom_count", 0);
    for (String key :
        List.of(
            "muster_seconds",
            "duration_seconds",
            "repair_lock_hours",
            "intruder_damage_interval_ticks",
            "intruder_damage_amount")) war.set("war.campaign_raid." + key, 1);
    war.set("war.campaign_raid.muster_reminder_seconds_before", List.of(1));
    war.set("war.goals.SUBJUGATE.max_battles_per_leg", 999);
    war.set("war.declare_code_timeout_seconds", -5);
    new ConfigLoader().loadWar(write("war.yml", war.saveToString()).toFile());
    assertEquals(0, Cache.warDefenderChoiceDeadlineHour);
    assertEquals(24, Cache.warBattleWindowEndHour);
    assertEquals(1, Cache.warBattleVotingMinPlayers);
    assertEquals(1, Cache.warBattleVotingDevMinPlayers);
    assertEquals(0, Cache.warDevmodePhantomCount);
    assertEquals(1, Cache.campaignRaidMusterSeconds);
    assertEquals(1, Cache.campaignRaidDurationSeconds);
    assertEquals(1, Cache.campaignRaidRepairLockHours);
    assertEquals(1, Cache.campaignRaidIntruderDamageIntervalTicks);
    assertEquals(1, Cache.campaignRaidIntruderDamageAmount);
    assertEquals(List.of(1), Cache.campaignRaidMusterReminderSecondsBefore);
    assertEquals(Cache.MAX_BATTLES_PER_LEG, Cache.warGoalMaxBattles.get(WarGoalType.SUBJUGATE));
    assertEquals(1, Cache.warDeclareCodeTimeoutSeconds);
  }

  private static YamlConfiguration mainSettings(boolean prior) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("map-reference", prior ? "loaded-map" : "candidate-map");
    yaml.set("world-name", prior ? "loaded-world" : "candidate-world");
    yaml.set("max-members", prior ? 12 : 24);
    yaml.set("capital-move-cost", prior ? 50 : 150);
    yaml.set("stability.legitimacy-community", prior ? 77 : 66);
    yaml.set("installation-trade.corridor-share", prior ? 0.2 : 0.8);
    yaml.set("installation-trade.transport.rail.trade", prior ? 0.3 : 0.7);
    yaml.set("installation-trade.transport.sea.production", prior ? 0.25 : 0.65);
    yaml.set("installation-trade.open-track.enabled", !prior);
    yaml.set("installation-trade.open-track.share", prior ? 0.4 : 0.9);
    yaml.set("installation-trade.open-track.kept-per-1000-blocks", prior ? 0.6 : 0.8);
    yaml.set("installation-trade.open-track.range-blocks", prior ? 1200 : 2400);
    yaml.set("battle.province_poll_interval_ticks", prior ? 30 : 40);
    yaml.set("battle.capture_min_players", prior ? 2 : 3);
    yaml.set("battle.province_leave_countdown_seconds", prior ? 10 : 20);
    yaml.set("battle.siege.contest_duration_seconds", prior ? 120 : 240);
    yaml.set("battle.signup_reminder_seconds_before", prior ? List.of(60, 30) : List.of(40, 20));
    yaml.set("branch-upgrade-cost", prior ? 300 : 600);
    yaml.set("terrain-modifiers", List.of(prior ? "PLAINS 0.25" : "PLAINS 0.75"));
    yaml.set("icons", List.of(prior ? "prior(v.STONE)" : "candidate(v.DIRT)"));
    yaml.set(
        "base-effects.DOMESTIC_GUILDS.modifiers",
        List.of(prior ? "TRADE_POWER(2)" : "TRADE_POWER(3)"));
    return yaml;
  }

  private static YamlConfiguration warSettings(boolean prior) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("war.declare_opinion_threshold", prior ? -77 : -22);
    yaml.set("war.initiative_factor", prior ? 2.5 : 4.5);
    yaml.set("war.goals.SUBJUGATE.max_battles_per_leg", prior ? 3 : 5);
    yaml.set(
        "war.goals.OPEN_MARKET.defender_must_not_have",
        List.of(prior ? "prior-law" : "candidate-law"));
    yaml.set("war.battle_schedule.defender_choice_deadline_hour", prior ? 2 : 12);
    yaml.set("war.battle_schedule.vote_close_hour", prior ? 4 : 16);
    yaml.set("war.battle_schedule.raid_window_start_hour", prior ? 6 : 19);
    yaml.set("war.battle_schedule.raid_window_end_hour", prior ? 8 : 20);
    yaml.set("war.battle_schedule.window_start_hour", prior ? 10 : 21);
    yaml.set("war.battle_schedule.window_end_hour", prior ? 12 : 24);
    yaml.set("war.battle_voting.min_players", prior ? 7 : 4);
    yaml.set("war.battle_voting.dev_min_players", prior ? 8 : 2);
    yaml.set("war.battle_military.lives_per_regiment", prior ? 7 : 8);
    yaml.set("war.battle_loot.commands", List.of(prior ? "say prior" : "say candidate"));
    yaml.set("war.devmode.phantom_count", prior ? 9 : 10);
    yaml.set("war.campaign_raid.muster_seconds", prior ? 70 : 60);
    yaml.set("war.campaign_raid.duration_seconds", prior ? 800 : 600);
    yaml.set("war.campaign_raid.repair_lock_hours", prior ? 60 : 48);
    yaml.set("war.campaign_raid.intruder_damage_interval_ticks", prior ? 15 : 10);
    yaml.set("war.campaign_raid.intruder_damage_amount", prior ? 7 : 4);
    yaml.set(
        "war.campaign_raid.muster_reminder_seconds_before",
        prior ? List.of(50, 20) : List.of(45, 30, 15, 10));
    return yaml;
  }

  /** Publicly published settings must be unchanged, including lists retained by running tasks. */
  private record PublishedSettings(
      Map<Field, Object> references,
      Map<Field, Object> contents,
      StabilityTuning stability,
      Map<HubTransport.Mode, HubTransport.Rates> transport,
      boolean openTrackEnabled,
      double openTrackShare,
      double openTrackKept,
      double openTrackRange) {
    static PublishedSettings capture() throws IllegalAccessException {
      Map<Field, Object> references = new LinkedHashMap<>();
      Map<Field, Object> contents = new LinkedHashMap<>();
      for (Field field : Cache.class.getFields()) {
        if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
          continue;
        Object value = field.get(null);
        references.put(field, value);
        if (value instanceof Map<?, ?> map) value = new HashMap<>(map);
        else if (value instanceof List<?> list) value = new ArrayList<>(list);
        contents.put(field, value);
      }
      Map<HubTransport.Mode, HubTransport.Rates> transport = new EnumMap<>(HubTransport.Mode.class);
      for (HubTransport.Mode mode : HubTransport.Mode.values())
        transport.put(mode, HubTransport.rates(mode));
      return new PublishedSettings(
          references,
          contents,
          StabilityTuning.get(),
          transport,
          OpenTrackSettings.enabled(),
          OpenTrackSettings.share(),
          OpenTrackSettings.keptPer1000(),
          OpenTrackSettings.rangeBlocks());
    }

    void assertUnchanged() {
      List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
      for (var entry : references.entrySet()) {
        Field field = entry.getKey();
        checks.add(() -> assertEquals(contents.get(field), field.get(null), field.getName()));
        if (entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof List<?>)
          checks.add(
              () -> assertSame(entry.getValue(), field.get(null), field.getName() + " reference"));
      }
      checks.add(() -> assertSame(stability, StabilityTuning.get()));
      for (var entry : transport.entrySet())
        checks.add(() -> assertSame(entry.getValue(), HubTransport.rates(entry.getKey())));
      checks.add(() -> assertEquals(openTrackEnabled, OpenTrackSettings.enabled()));
      checks.add(() -> assertEquals(openTrackShare, OpenTrackSettings.share()));
      checks.add(() -> assertEquals(openTrackKept, OpenTrackSettings.keptPer1000()));
      checks.add(() -> assertEquals(openTrackRange, OpenTrackSettings.rangeBlocks()));
      assertAll(checks);
    }
  }
}
