package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.*;
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

  @BeforeEach
  void setup() throws Exception {
    domain = new FactionDomainFixture();
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
    for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
    domain.close();
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
    assertDoesNotThrow(() -> new ConfigLoader().loadConfig(config.toFile()));
    assertEquals(9, Cache.maxMembers);
    Path war = write("war.yml", "war:\n  declare_opinion_threshold: -77\n");
    new ConfigLoader().loadWar(war.toFile());
    Files.writeString(war, "broken: [\n");
    assertDoesNotThrow(() -> new ConfigLoader().loadWar(war.toFile()));
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
}
