package net.tfminecraft.simplefactions.objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.IconFormat;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.ProvinceLoader;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;
import net.tfminecraft.simplefactions.prestige.TradePrestige;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.tiers.admin.TitleAdminService;
import net.tfminecraft.simplefactions.util.LegacyModelData;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainConfigurationBoundaryCoverageTest {
  @TempDir Path directory;
  private FactionDomainFixture fixture;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
  }

  @AfterEach
  void close() {
    fixture.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void guildModifierIdentifiersAreIndependentOfServerLocale(boolean upgrade) {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      var config = guildConfig("paper.1", List.of("prestige 3 2"));
      double amount =
          upgrade
              ? new Upgrade(new Upgrade("prestige", config), 2).getAmount(GuildModifier.PRESTIGE)
              : new Branch(new Branch("prestige", config), 2).getAmount(GuildModifier.PRESTIGE);
      assertEquals(
          7.0,
          amount,
          "The same guild configuration must grant the same prestige on every server locale");
    } finally {
      Locale.setDefault(previous);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void malformedGuildModifiersAreSkippedWithoutLosingValidSiblings(boolean upgrade) {
    var config =
        guildConfig(
            "paper.1",
            List.of(
                "TRADE_POWER 3 2",
                "PRESTIGE NaN",
                "ADMIN_POWER 1 Infinity",
                "MANA_REGEN 1 2 3",
                "MAX_MANA nope",
                "UNKNOWN 2",
                "incomplete"));
    var modifiers =
        upgrade
            ? new Upgrade("workshop", config).getModifiers()
            : new Branch("workshop", config).getModifiers();
    assertEquals(List.of(GuildModifier.TRADE_POWER), new ArrayList<>(modifiers.keySet()));
    assertEquals(7.0, modifiers.get(GuildModifier.TRADE_POWER).getCurrent(2));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void configuredMaterialIconsDoNotDependOnServerLocale(boolean upgrade) {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      var config = guildConfig("iron_ingot.7", List.of());
      ItemStack item =
          upgrade
              ? new Upgrade("smith", config).getIconItem()
              : new Branch("smith", config).getIconItem();
      assertEquals(Material.IRON_INGOT, item.getType());
      assertEquals(7, LegacyModelData.get(item.getItemMeta()));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void itemPathIconsAreIndependentCopiesOfTheExternalTemplate(boolean upgrade) {
    ItemStack template = new ItemStack(Material.EMERALD, 4);
    var meta = template.getItemMeta();
    meta.setDisplayName("Original template");
    template.setItemMeta(meta);
    try (var items = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class);
      ItemCreator creator = mock(ItemCreator.class);
      items.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator()).thenReturn(creator);
      when(creator.getItemFromPath("ia.guild.badge")).thenReturn(template);
      var config = guildConfig("ia.guild.badge", List.of());
      ItemStack icon =
          upgrade
              ? new Upgrade("badge", config).getIconItem()
              : new Branch("badge", config).getIconItem();
      assertNotSame(template, icon);
      assertEquals(4, icon.getAmount());
      assertEquals("Original template", icon.getItemMeta().getDisplayName());
      icon.setAmount(1);
      var changed = icon.getItemMeta();
      changed.setDisplayName("Menu copy");
      icon.setItemMeta(changed);
      assertEquals(4, template.getAmount());
      assertEquals("Original template", template.getItemMeta().getDisplayName());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingOrBrokenExternalIconUsesFreshBlackDye(boolean throwsError) {
    try (var items = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class);
      ItemCreator creator = mock(ItemCreator.class);
      items.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator()).thenReturn(creator);
      if (throwsError)
        when(creator.getItemFromPath("m.unavailable"))
            .thenThrow(new IllegalArgumentException("missing model"));
      ItemStack first = IconFormat.itemFromPath("m.unavailable");
      ItemStack second = IconFormat.itemFromPath("m.unavailable");
      assertEquals(Material.BLACK_DYE, first.getType());
      assertEquals(1, first.getAmount());
      first.setAmount(9);
      assertEquals(1, second.getAmount());
      assertNotSame(first, second);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidLegacyMaterialDoesNotPreventGuildMenuRendering(boolean upgrade) {
    var config = guildConfig("not_a_material.2", List.of());
    ItemStack icon =
        upgrade
            ? new Upgrade("invalid", config).getIconItem()
            : new Branch("invalid", config).getIconItem();
    assertEquals(Material.DIRT, icon.getType());
    assertEquals(1, icon.getAmount());
    assertFalse(LegacyModelData.has(icon.getItemMeta()));
  }

  @Test
  void rankEditsChangeLookupAndRequiredPrestigeTogether() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("name", "Eminent");
    config.set("level", 3);
    config.set("minimum-prestige", 300.0);
    config.set("an", true);
    PrestigeRank rank = new PrestigeRank("eminent", config);
    RankLoader.getRanks().add(rank);
    assertTrue(rank.getAn());
    assertEquals(300.0, FactionManager.getRankUpAmount(rank));
    rank.setId("illustrious");
    rank.setName("Illustrious");
    rank.setMin(450.0);
    assertNull(RankLoader.getByString("eminent"));
    assertSame(rank, RankLoader.getByString("ILLUSTRIOUS"));
    assertEquals("Illustrious", RankLoader.getByLevel(3).getName());
    rank.setLevel(4);
    assertNull(RankLoader.getByLevel(3));
    assertSame(rank, RankLoader.getByLevel(4));
    assertEquals(450.0, FactionManager.getRankUpAmount(rank));
  }

  @Test
  void configuredLargeRankLevelsUseNumericIdentity() {
    PrestigeRank rank = fixture.rank("legendary", 200, 500.0);
    assertSame(rank, RankLoader.getByLevel(Integer.valueOf("200")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedRankReloadPreservesTheCurrentDefinitions(boolean malformed) throws Exception {
    var previous = new ArrayList<>(RankLoader.getRanks());
    Path file = directory.resolve("ranks.yml");
    if (malformed) Files.writeString(file, "rank: [unterminated\n");
    assertThrows(IllegalStateException.class, () -> new RankLoader().loadRanks(file.toFile()));
    assertEquals(previous, RankLoader.getRanks());
    assertSame(previous.getFirst(), RankLoader.getLowest());
  }

  @Test
  void configuredTierAliasesChangeDisplayWithoutChangingItsIdentityOrCost() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("name", "County");
    config.set("tier", 2);
    config.set("form-cost", 40);
    config.set("aliases", List.of("Earldom", "Shire"));
    Tier template = new Tier("county_alias", config);
    Tier selected = new Tier(template, 1);
    assertEquals("Shire §7(County§7)", selected.getFormattedName());
    assertEquals(1, selected.getIndex());
    selected.setIndex(0);
    assertEquals("Earldom", selected.getCurrentAlias());
    assertEquals("County", template.getFormattedName());
    assertEquals("county_alias", selected.getId());
    assertEquals(40, selected.getFormCost());
    assertTrue(selected.canForm());
  }

  @Test
  void completeCompositeTitleNeedsEveryChildAndEnoughOfTheirLand() {
    Faction faction = fixture.saved("realm", "Leader");
    Title first = fixture.title("west", "county", 1, 2);
    Title second = fixture.title("east", "county", 3, 4);
    Title duchy =
        new Title(
            TierLoader.getByString("duchy"),
            "duchy_complete",
            JsonParser.parseString(
                    "{\"name\":\"Complete"
                        + " Duchy\",\"titles\":[\"west\",\"east\"],\"title-complete\":true}")
                .getAsJsonObject());
    TitleLoader.getTitles().add(duchy);
    assertFalse(duchy.canBeCreatedBy(faction, List.of(1, 2, 3, 4), List.of(first), 50));
    assertFalse(duchy.canBeCreatedBy(faction, List.of(1), List.of(first, second), 50));
    assertTrue(duchy.canBeCreatedBy(faction, List.of(1, 3), List.of(first, second), 50));
    var owned = new ArrayList<>(List.of(first, second));
    duchy.destroy(faction, List.of(), owned);
    assertEquals(
        List.of(first, second), owned, "Destroying an unowned parent cannot revoke its children");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1,blue,3", "1,2147483648,3"})
  void invalidTitleColourCannotChangeItsMapIdentity(String invalid) {
    Title title = fixture.title("west", "county", 1, 2);
    var result = TitleAdminService.setColour("west", invalid);
    assertFalse(result.ok());
    assertEquals("4,5,6", title.getRgb());
    assertTrue(result.changed().isEmpty());
    assertTrue(result.regenerate().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3,nope", "3,999", " , "})
  void invalidProvinceBatchReportsTheReasonAndLeavesTitlesUnchanged(String invalid) {
    Title title = fixture.title("west", "county", 1, 2);
    var result = TitleAdminService.addProvinces("west", List.of(invalid), id -> id < 100);
    String reason =
        invalid.contains("nope")
            ? "Province ids must be numbers: nope"
            : invalid.contains("999")
                ? "No province with the id 999"
                : "Give at least one province id";
    assertFalse(result.ok());
    assertEquals(List.of(TitleAdminService.PREFIX + reason), result.lines());
    assertEquals(List.of(1, 2), title.getProvinces());
    assertTrue(result.changed().isEmpty());
  }

  @Test
  void releasableGuildListReflectsLiveCapitalConflictsAndVassalLaw() {
    fixture.lawGroup("vassals", Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS true")));
    Faction faction = fixture.saved("realm", "Leader");
    Guild first = fixture.guild(faction, "west", "West");
    Guild second = fixture.guild(faction, "east", "East");
    first.setCapital(10);
    second.setCapital(20);
    assertEquals(2, faction.getGuildHandler().getReleasableGuilds().size());
    assertTrue(faction.getGuildHandler().getReleasableGuilds().containsAll(List.of(first, second)));
    second.setCapital(10);
    assertTrue(faction.getGuildHandler().getReleasableGuilds().isEmpty());
    second.setCapital(20);
    var denied =
        fixture.law(
            "vassals",
            "denied",
            Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS false")));
    faction.getLawHandler().getGroup("vassals").setCurrent(denied);
    assertTrue(faction.getGuildHandler().getReleasableGuilds().isEmpty());
    assertSame(first, faction.getGuildHandler().getGuild("west"));
    assertSame(second, faction.getGuildHandler().getGuild("east"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingGuildDoesNotHideUnrelatedConfiguredLawModifiers(boolean attached) {
    fixture.lawGroup(
        "economy", Map.of("effects.domestic_guilds.modifiers", List.of("PRODUCTION(7)")));
    Faction faction = fixture.saved("realm", "Leader");
    LawHandler handler = attached ? faction.getLawHandler() : new LawHandler(null);
    SimpleFactions previous = SimpleFactions.plugin;
    try {
      if (!attached) SimpleFactions.plugin = null;
      var modifiers = handler.getLawModifiers("removed_guild", Scope.DOMESTIC_GUILDS, null);
      assertEquals(1, modifiers.size());
      assertEquals(FactionModifiers.PRODUCTION, modifiers.getFirst().getType());
      assertEquals(7.0, modifiers.getFirst().getAmount());
    } finally {
      SimpleFactions.plugin = previous;
    }
  }

  @Test
  void exposedDefinitionRegistriesDriveExistingLookupAndLegacyBranchAlias() {
    var config = guildConfig("paper.1", List.of());
    config.set("group", 6);
    Branch branch = new Branch("counting_houses", config);
    BranchLoader.get().put(branch.getId(), branch);
    Upgrade upgrade = new Upgrade("guild_hall", config);
    UpgradeLoader.get().put(upgrade.getId(), upgrade);
    Faction faction = fixture.saved("realm", "Leader");
    Guild guild = fixture.guild(faction, "trade", "Trader");
    assertSame(branch, BranchLoader.getByString("freight_yards"));
    assertSame(branch, BranchLoader.getByGroup(guild, 6));
    assertSame(upgrade, UpgradeLoader.getByString("GUILD_HALL"));
    assertSame(guild.getType(), GuildLoader.get().get("guild"));
  }

  @Test
  void tinyTradeConversionUnderflowProducesZeroPrestige() {
    double previous = Cache.prestigePerTradePower;
    try {
      Cache.prestigePerTradePower = 0.5;
      assertEquals(0.0, TradePrestige.fromTradePower(Double.MIN_VALUE));
      assertEquals(0.5, TradePrestige.fromTradePower(1.0));
    } finally {
      Cache.prestigePerTradePower = previous;
    }
  }

  @Test
  void rankReloadCommitsAllDefinitionsOnlyAfterTheEntireFileIsValid() throws Exception {
    var previous = new ArrayList<>(RankLoader.getRanks());
    Path file = directory.resolve("rank-reload.yml");
    Files.writeString(
        file, "novice:\n  name: Novice\n  level: 1\n  minimum-prestige: 0\nbroken: scalar\n");
    RankLoader loader = new RankLoader();
    assertThrows(IllegalStateException.class, () -> loader.loadRanks(file.toFile()));
    assertEquals(previous, RankLoader.getRanks());
    assertNull(
        RankLoader.getByString("novice"), "An earlier valid row must not leak from a failed load");
    Files.writeString(
        file,
        "novice:\n"
            + "  name: Novice\n"
            + "  level: 1\n"
            + "  minimum-prestige: 0\n"
            + "master:\n"
            + "  name: Master\n"
            + "  level: 2\n"
            + "  minimum-prestige: 200\n");
    loader.loadRanks(file.toFile());
    assertEquals(2, RankLoader.getRanks().size());
    assertNull(RankLoader.getByString("common"));
    assertEquals("novice", RankLoader.getLowest().getId());
    assertEquals(200.0, RankLoader.getByLevel(2).getMin());
    assertSame(RankLoader.getLowest(), fixture.saved("new_realm", "NewLeader").getRank());
  }

  @Test
  void emptyRankReloadCannotPreventTheNextFactionFromLoading() throws Exception {
    var previous = new ArrayList<>(RankLoader.getRanks());
    Path file = directory.resolve("empty-ranks.yml");
    Files.writeString(file, "# accidental empty replacement\n");
    assertThrows(IllegalStateException.class, () -> new RankLoader().loadRanks(file.toFile()));
    assertAll(
        () -> assertEquals(previous, RankLoader.getRanks()),
        () -> assertSame(previous.getFirst(), fixture.saved("after_reload", "Leader").getRank()));
  }

  @Test
  void lossOfTheLastTitleNotifiesTheCurrentOnlineLeaderOnce() {
    var leader = fixture.player("Leader");
    Faction faction = fixture.saved("realm", "Leader");
    Title west = fixture.title("west", "county", 1, 2);
    faction.addTitle(west);
    var owned = new ArrayList<>(faction.getTitles());
    west.destroy(faction, List.of(), owned);
    assertFalse(faction.hasTitle(west));
    assertTrue(owned.isEmpty());
    verify(leader)
        .sendMessage("§cYou lost the title §ewest §cdue to a lack of provinces or required titles");
    west.destroy(faction, List.of(), owned);
    verify(leader, times(1))
        .sendMessage("§cYou lost the title §ewest §cdue to a lack of provinces or required titles");
  }

  @Test
  void renamingAPersistentModifierPreservesItsIdentityWhenLaterAmountsMerge() {
    Faction faction = fixture.saved("realm", "Leader");
    Modifier grant = new Modifier("legacy charter", 5.0, true);
    faction.addPersistentPrestigeModifier(grant);
    grant.setType("Royal charter");
    faction.addPersistentPrestigeModifier(new Modifier("ROYAL CHARTER", 3.0, true));
    var persistent =
        faction.getPrestigeModifiers().stream().filter(Modifier::isPersistent).toList();
    assertEquals(1, persistent.size());
    assertEquals("ROYAL CHARTER", persistent.getFirst().getType());
    assertEquals(8.0, persistent.getFirst().getAmount());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void provinceConfigurationFailuresAreReportedBeforeAnyPartialMapEscapes(boolean neighboursFail)
      throws Exception {
    Path provinces = directory.resolve("provinces.txt");
    Path neighbours = directory.resolve("neighbours.json");
    Files.writeString(
        provinces, neighboursFail ? "1 = 10,64,20;plains;30\n" : "one = 10,64,20;plains;30\n");
    Files.writeString(neighbours, neighboursFail ? "{" : "{}");
    RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () -> new ProvinceLoader().loadProvinces(provinces.toFile(), neighbours.toFile()));
    assertEquals(
        neighboursFail ? "Failed to load province neighbours" : "Failed to load provinces file",
        failure.getMessage());
    assertNotNull(failure.getCause());
    Files.writeString(provinces, "1 = 10,64,20;plains;30\n");
    Files.writeString(neighbours, "{\"1\":[]}");
    var recovered = new ProvinceLoader().loadProvinces(provinces.toFile(), neighbours.toFile());
    assertEquals(java.util.Set.of(1), recovered.keySet());
    assertEquals(1, recovered.get(1).getId());
  }

  @Test
  void guildMembershipQueriesAndForcedRemovalAgreeWithoutChangingLeaders() {
    Faction faction = fixture.saved("realm", "Alice");
    faction.addMember("Citizen");
    Guild traders = fixture.guild(faction, "traders", "Bob");
    traders.addMember("Traveler");
    var guilds = faction.getGuildHandler();
    assertSame(traders, guilds.getGuildByMember("traveler"));
    assertNull(guilds.getGuildByMember("missing"));
    assertTrue(guilds.isGuildLeader("BOB"));
    assertFalse(guilds.isGuildLeader("Traveler"));
    guilds.forceKick("traveler");
    assertNull(guilds.getGuildByMember("Traveler"));
    assertFalse(faction.isMemberIgnoreCase("Traveler"));
    assertEquals(List.of("Bob"), traders.getMembers());
    assertEquals("Bob", traders.getLeader());
    assertTrue(faction.isMemberIgnoreCase("Citizen"));
    guilds.forceKick("already gone");
    assertTrue(guilds.isGuildLeader("Alice"));
    assertTrue(guilds.isGuildLeader("Bob"));
  }

  @Test
  void currentLawLookupAndRegionalModifiersUseTheSameConfiguredPolicy() {
    fixture.lawGroup(
        "production",
        Map.of(
            "effects.domestic_guilds.modifiers", List.of("PRODUCTION(5)"),
            "effects.domestic_guilds.foreign_territory", List.of("PRODUCTION(7)")));
    var faction = fixture.saved("realm", "Alice");
    var laws = faction.getLawHandler();
    assertSame(laws.getGroup("production").getCurrent(), laws.getLaw("production", "current"));
    assertNull(laws.getLaw("removed_group", "current"));
    assertNull(laws.getLaw("production", "removed_law"));
    var domestic = laws.getLawModifiers(null, Scope.DOMESTIC_GUILDS, Region.OUR_TERRITORY);
    var foreign = laws.getLawModifiers(null, Scope.DOMESTIC_GUILDS, Region.FOREIGN_TERRITORY);
    assertEquals(1, domestic.size());
    assertEquals(1, foreign.size());
    assertEquals(FactionModifiers.PRODUCTION, foreign.getFirst().getType());
    assertEquals(5.0, domestic.getFirst().getAmount());
    assertEquals(12.0, foreign.getFirst().getAmount());
    assertTrue(
        laws.getLawModifiers(null, Scope.FOREIGN_GUILDS, Region.FOREIGN_TERRITORY).isEmpty());
  }

  private YamlConfiguration guildConfig(String icon, List<String> modifiers) {
    var config = new YamlConfiguration();
    config.set("icon", icon);
    config.set("allowed-types", List.of("realm", "guild"));
    config.set("modifiers", modifiers);
    return config;
  }
}
