package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogStartupFailureCoverageTest {
  private FactionDomainFixture domain;
  private PersistenceFilesFixture files;
  private Map<String, Upgrade> previousCompany;
  private Map<Action, PoliticalAction> previousActions;
  private Map<String, BattleTemplate> previousTemplates;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    domain = new FactionDomainFixture();
    previousCompany = new LinkedHashMap<>(CompanyUpgradeLoader.get());
    previousActions = new LinkedHashMap<>(PoliticalActionLoader.get());
    previousTemplates = BattleTemplateLoader.getAll();
    CompanyUpgradeLoader.get().clear();
    PoliticalActionLoader.get().clear();
    BattleTemplateLoader.resetForTests();
    RankLoader.ranks.clear();
    RegimentLoader.oList.clear();
    TierLoader.oList.clear();
    BranchLoader.map.clear();
    LawLoader.map.clear();
    GuildLoader.map.clear();
    UpgradeLoader.map.clear();
    RelationLoader.types.clear();
    RelationLoader.attitudes.clear();
  }

  @AfterEach
  void restore() throws Exception {
    try {
      CompanyUpgradeLoader.get().clear();
      CompanyUpgradeLoader.get().putAll(previousCompany);
      PoliticalActionLoader.get().clear();
      PoliticalActionLoader.get().putAll(previousActions);
      BattleTemplateLoader.resetForTests();
      previousTemplates.values().forEach(BattleTemplateLoader::putForTests);
      domain.close();
    } finally {
      files.close();
    }
  }

  private void load(String kind, File file) {
    switch (kind) {
      case "rank" -> new RankLoader().loadRanks(file);
      case "regiment" -> new RegimentLoader().loadRegiments(file);
      case "tier" -> new TierLoader().load(file);
      case "branch" -> new BranchLoader().load(file);
      case "law" -> new LawLoader().load(file);
      case "guild" -> new GuildLoader().load(file);
      case "upgrade" -> new UpgradeLoader().load(file);
      case "company" -> new CompanyUpgradeLoader().load(file);
      case "political" -> new PoliticalActionLoader().load(file);
      case "relations" -> new RelationLoader().loadRelationTypes(file);
      case "attitudes" -> new RelationLoader().loadAttitudes(file);
      case "templates" -> new BattleTemplateLoader().load(file);
      default -> throw new IllegalArgumentException(kind);
    }
  }

  private List<?> entries(String kind) {
    return switch (kind) {
      case "rank" -> List.copyOf(RankLoader.ranks);
      case "regiment" -> List.copyOf(RegimentLoader.getRegiments());
      case "tier" -> List.copyOf(TierLoader.get());
      case "branch" -> BranchLoader.getList();
      case "law" -> LawLoader.getList();
      case "guild" -> GuildLoader.getList();
      case "upgrade" -> UpgradeLoader.getList();
      case "company" -> CompanyUpgradeLoader.getList();
      case "political" -> PoliticalActionLoader.getList();
      case "relations" -> List.copyOf(RelationLoader.types);
      case "attitudes" -> List.copyOf(RelationLoader.attitudes);
      case "templates" -> List.copyOf(BattleTemplateLoader.getAll().values());
      default -> throw new IllegalArgumentException(kind);
    };
  }

  private String validYaml(String kind) {
    return switch (kind) {
      case "political" -> "CHANGE_LEADER: {}\n";
      case "relations" -> "types:\n  alpha: {}\nattitudes: {}\n";
      case "attitudes" -> "types: {}\nattitudes:\n  alpha: {}\n";
      case "templates" -> "alpha:\n  type: field\n";
      default ->
          "alpha:\n"
              + "  name: Alpha\n"
              + "  tier: 1\n"
              + "  laws:\n"
              + "    default: {}\n"
              + "  item:\n"
              + "    material: PAPER\n";
    };
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "rank",
        "regiment",
        "tier",
        "branch",
        "law",
        "guild",
        "upgrade",
        "company",
        "political",
        "relations",
        "attitudes",
        "templates"
      })
  void malformedInitialCatalogFailsExplicitlyWithoutPublishingPartialDefinitions(String kind)
      throws Exception {
    Path file = files.write(kind + ".yml", "broken: [unterminated\n");
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> load(kind, file.toFile()));
    assertTrue(failure.getMessage().contains(file.getFileName().toString()), failure.getMessage());
    assertTrue(entries(kind).isEmpty(), "A rejected initial catalog has no published definitions");
    assertEquals("broken: [unterminated\n", Files.readString(file));
    Files.delete(file);
    Files.createDirectory(file);
    assertThrows(IllegalStateException.class, () -> load(kind, file.toFile()));
    assertTrue(entries(kind).isEmpty());
    assertTrue(Files.isDirectory(file), "An unreadable catalog must not be replaced with defaults");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "rank",
        "regiment",
        "tier",
        "branch",
        "law",
        "guild",
        "upgrade",
        "company",
        "political",
        "relations",
        "attitudes",
        "templates"
      })
  void failedReloadStillReportsFailureAndPreservesEveryLastKnownGoodObject(String kind)
      throws Exception {
    Path file = files.write(kind + ".yml", validYaml(kind));
    load(kind, file.toFile());
    List<?> previous = entries(kind);
    assertFalse(previous.isEmpty());
    Files.writeString(file, "broken: [unterminated\n");
    assertThrows(IllegalStateException.class, () -> load(kind, file.toFile()));
    List<?> retained = entries(kind);
    assertEquals(previous.size(), retained.size());
    for (Object entry : previous) assertTrue(retained.stream().anyMatch(value -> value == entry));
    Files.delete(file);
    assertThrows(IllegalStateException.class, () -> load(kind, file.toFile()));
    assertEquals(previous, entries(kind));
    Files.writeString(file, validYaml(kind));
    assertDoesNotThrow(() -> load(kind, file.toFile()));
    assertEquals(previous.size(), entries(kind).size());
    for (Object entry : previous)
      assertFalse(entries(kind).stream().anyMatch(value -> value == entry));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"regiment", "tier", "branch", "law", "guild", "upgrade", "company", "templates"})
  void aSuccessfullyParsedEmptyCatalogIsNotMistakenForALoadFailure(String kind) throws Exception {
    Path file = files.write(kind + ".yml", "{}\n");
    assertDoesNotThrow(() -> load(kind, file.toFile()));
    assertTrue(entries(kind).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "null", "[]", "{\"good\":{\"name\":\"Good\"},\"bad\":42}"})
  void malformedInitialTitleFilesFailExplicitlyAndRemainUnchanged(String json) throws Exception {
    domain.tier("county", 1, 0);
    Path file = files.write("Input/county.json", json);
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, new TitleLoader()::loadAll);
    assertTrue(failure.getMessage().contains("county.json"), failure.getMessage());
    assertTrue(TitleLoader.getTitles().isEmpty());
    assertEquals(json, Files.readString(file));
  }

  @Test
  void optionalAbsentAndEmptyTitleFilesRemainValidWhileUnreadablePresentFilesFail()
      throws Exception {
    domain.tier("county", 1, 0);
    TitleLoader loader = new TitleLoader();
    assertDoesNotThrow(loader::loadAll);
    assertTrue(TitleLoader.getTitles().isEmpty());
    Path file = files.write("Input/county.json", "{}");
    assertDoesNotThrow(loader::loadAll);
    assertTrue(TitleLoader.getTitles().isEmpty());
    Files.delete(file);
    Files.createDirectory(file);
    IllegalStateException failure = assertThrows(IllegalStateException.class, loader::loadAll);
    assertTrue(failure.getMessage().contains("county.json"), failure.getMessage());
    assertTrue(Files.isDirectory(file));
  }

  @ParameterizedTest
  @ValueSource(strings = {"config", "war"})
  void malformedPrimaryConfigurationFailsInsteadOfStartingWithDefaultState(String kind)
      throws Exception {
    Path file = files.write(kind + ".yml", "broken: [unterminated\n");
    ConfigLoader loader = new ConfigLoader();
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> {
              if (kind.equals("config")) loader.loadConfig(file.toFile());
              else loader.loadWar(file.toFile());
            });
    assertTrue(failure.getMessage().contains(file.getFileName().toString()), failure.getMessage());
  }
}
