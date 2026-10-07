package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import net.tfminecraft.simplefactions.government.movement.*;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CatalogReloadCoverageTest {
  @TempDir Path directory;
  private FactionDomainFixture domain;
  private Map<String, Upgrade> company;
  private Map<Action, PoliticalAction> actions;

  @BeforeEach
  void setup() {
    domain = new FactionDomainFixture();
    company = new LinkedHashMap<>(CompanyUpgradeLoader.get());
    actions = new LinkedHashMap<>(PoliticalActionLoader.map);
    CompanyUpgradeLoader.get().clear();
    PoliticalActionLoader.map.clear();
    TierLoader.oList.clear();
    RegimentLoader.oList.clear();
    GuildLoader.map.clear();
    BranchLoader.map.clear();
    UpgradeLoader.map.clear();
    LawLoader.map.clear();
  }

  @AfterEach
  void close() {
    CompanyUpgradeLoader.get().clear();
    CompanyUpgradeLoader.get().putAll(company);
    PoliticalActionLoader.map.clear();
    PoliticalActionLoader.map.putAll(actions);
    domain.close();
  }

  private record Catalog(
      Consumer<File> load,
      Supplier<List<String>> ids,
      Function<String, Object> find,
      String properties) {}

  private Catalog catalog(String kind) {
    return switch (kind) {
      case "guild" ->
          new Catalog(
              new GuildLoader()::load,
              () -> GuildLoader.getList().stream().map(g -> g.getId()).toList(),
              GuildLoader::getByString,
              "  name: %s\n");
      case "branch" ->
          new Catalog(
              new BranchLoader()::load,
              () -> BranchLoader.getList().stream().map(g -> g.getId()).toList(),
              BranchLoader::getByString,
              "  name: %s\n  group: 3\n");
      case "upgrade" ->
          new Catalog(
              new UpgradeLoader()::load,
              () -> UpgradeLoader.getList().stream().map(g -> g.getId()).toList(),
              UpgradeLoader::getByString,
              "  name: %s\n  upkeep: 4\n");
      case "company" ->
          new Catalog(
              new CompanyUpgradeLoader()::load,
              () -> CompanyUpgradeLoader.getList().stream().map(g -> g.getId()).toList(),
              CompanyUpgradeLoader::getByString,
              "  name: %s\n  upkeep: 4\n");
      case "law" ->
          new Catalog(
              new LawLoader()::load,
              () -> LawLoader.getList().stream().map(g -> g.getId()).toList(),
              LawLoader::getByString,
              "  name: %s\n  laws:\n    default: {}\n");
      case "tier" ->
          new Catalog(
              new TierLoader()::load,
              () -> TierLoader.get().stream().map(g -> g.getId()).toList(),
              TierLoader::getByString,
              "  name: %s\n  tier: 1\n");
      case "regiment" ->
          new Catalog(
              new RegimentLoader()::loadRegiments,
              () -> RegimentLoader.getRegiments().stream().map(g -> g.getId()).toList(),
              RegimentLoader::getByString,
              "  name: %s\n  default-slots: 2\n  item:\n    material: PAPER\n");
      default -> throw new IllegalArgumentException(kind);
    };
  }

  static Stream<String> catalogs() {
    return Stream.of("guild", "branch", "upgrade", "company", "law", "tier", "regiment");
  }

  private String row(Catalog catalog, String id, String name) {
    return id + ":\n" + catalog.properties().formatted(name);
  }

  @ParameterizedTest
  @MethodSource("catalogs")
  void reloadReplacesRemovedDefinitionsAndDoesNotDuplicateSurvivingIds(String kind)
      throws Exception {
    Catalog catalog = catalog(kind);
    Path file =
        Files.writeString(
            directory.resolve(kind + ".yml"),
            row(catalog, "alpha", "Alpha") + row(catalog, "beta", "Beta"));
    catalog.load().accept(file.toFile());
    assertEquals(Set.of("alpha", "beta"), new HashSet<>(catalog.ids().get()));
    Object original = catalog.find().apply("BETA");
    assertNotNull(original);
    Files.writeString(file, row(catalog, "beta", "Updated") + row(catalog, "gamma", "Gamma"));
    catalog.load().accept(file.toFile());
    assertEquals(2, catalog.ids().get().size());
    assertEquals(Set.of("beta", "gamma"), new HashSet<>(catalog.ids().get()));
    assertNull(catalog.find().apply("ALPHA"));
    assertNotSame(original, catalog.find().apply("beta"));
  }

  @ParameterizedTest
  @MethodSource("catalogs")
  void malformedOrUnreadableReloadPreservesThePreviousCatalog(String kind) throws Exception {
    Catalog catalog = catalog(kind);
    Path file = Files.writeString(directory.resolve(kind + ".yml"), row(catalog, "alpha", "Alpha"));
    catalog.load().accept(file.toFile());
    Object original = catalog.find().apply("alpha");
    Files.writeString(file, "broken: [\n");
    assertThrows(IllegalStateException.class, () -> catalog.load().accept(file.toFile()));
    assertEquals(List.of("alpha"), catalog.ids().get());
    assertSame(original, catalog.find().apply("alpha"));
    Files.writeString(file, row(catalog, "replacement", "Replacement") + "invalid: 42\n");
    assertThrows(IllegalStateException.class, () -> catalog.load().accept(file.toFile()));
    assertEquals(List.of("alpha"), catalog.ids().get());
    assertSame(original, catalog.find().apply("alpha"));
    Files.delete(file);
    assertThrows(IllegalStateException.class, () -> catalog.load().accept(file.toFile()));
    assertSame(original, catalog.find().apply("alpha"));
  }

  @Test
  void politicalActionReloadReplacesOldActionsButRetainsTheNoneAction() throws Exception {
    Path file = Files.writeString(directory.resolve("actions.yml"), "CHANGE_LEADER: {}\n");
    PoliticalActionLoader loader = new PoliticalActionLoader();
    loader.load(file.toFile());
    assertNotNull(PoliticalActionLoader.getByAction(Action.CHANGE_LEADER));
    Files.writeString(file, "SURRENDER: {}\n");
    loader.load(file.toFile());
    assertEquals(Set.of(Action.NONE, Action.SURRENDER), PoliticalActionLoader.get().keySet());
    assertEquals(2, PoliticalActionLoader.getList().size());
    assertNull(PoliticalActionLoader.getByAction(Action.CHANGE_LEADER));
    Object original = PoliticalActionLoader.getByAction(Action.SURRENDER);
    Files.writeString(file, "SURRENDER: {}\ninvalid-action: {}\n");
    assertThrows(IllegalStateException.class, () -> loader.load(file.toFile()));
    assertSame(original, PoliticalActionLoader.getByAction(Action.SURRENDER));
    Files.writeString(file, "broken: [\n");
    assertThrows(IllegalStateException.class, () -> loader.load(file.toFile()));
    assertSame(original, PoliticalActionLoader.getByAction(Action.SURRENDER));
  }

  @Test
  void guildFallbackAndTierOrderingWorkForUnflaggedDefinitions() throws Exception {
    Path guilds = Files.writeString(directory.resolve("guilds.yml"), "ordinary: {}\n");
    new GuildLoader().load(guilds.toFile());
    assertSame(GuildLoader.getByString("ordinary"), GuildLoader.getBaseType());
    assertSame(GuildLoader.getByString("ordinary"), GuildLoader.getDefaultType());
    Path tiers =
        Files.writeString(directory.resolve("tiers.yml"), "high:\n  tier: 3\nlow:\n  tier: 1\n");
    new TierLoader().load(tiers.toFile());
    assertEquals("low", TierLoader.getLowest().getId());
  }
}
