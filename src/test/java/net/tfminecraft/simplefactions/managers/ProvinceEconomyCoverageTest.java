package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.resolution.PillageTradeHit;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProvinceEconomyCoverageTest {
  private FactionDomainFixture fixture;
  private ProvinceManager manager;
  private Map<Integer, Province> provinces;
  private Map<Terrain, Double> oldCarry;
  private double oldMaxUpkeep;
  private TradeGraph oldGraph;

  @BeforeEach
  void setup() {
    oldCarry = new HashMap<>(Cache.tradeCarry);
    oldMaxUpkeep = Cache.maxTradeUpkeep;
    oldGraph = TradeGraph.live();
    fixture = new FactionDomainFixture();
    manager = new ProvinceManager();
    provinces = new LinkedHashMap<>();
    manager.start(provinces);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
    Cache.tradeCarry.clear();
    Cache.tradeCarry.put(Terrain.PLAINS, 0.5);
    Cache.maxTradeUpkeep = 0.75;
  }

  @AfterEach
  void close() {
    try {
      if (fixture != null) fixture.close();
    } finally {
      Cache.tradeCarry.clear();
      Cache.tradeCarry.putAll(oldCarry);
      Cache.maxTradeUpkeep = oldMaxUpkeep;
      TradeGraph.setLiveForTests(oldGraph);
    }
  }

  private Province province(int id) {
    Province province = new Province(id, "PLAINS", 50);
    provinces.put(id, province);
    return province;
  }

  private void productiveBranch() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("industry.allowed-types", List.of("realm", "guild"));
    yaml.set("industry.group", 0);
    yaml.set(
        "industry.modifiers",
        List.of("TRADE_POWER 10 2", "PRODUCTION 10 5", "TRADE_CARRY 1 0", "TRADE_UPKEEP 0 0"));
    BranchLoader.map.clear();
    BranchLoader.map.put(
        "industry", new Branch("industry", yaml.getConfigurationSection("industry")));
  }

  @Test
  void tradeModifiersRedistributeAProvincesIncomeWithoutCreatingAdditionalProsperity() {
    fixture.lawGroup("economy", Map.of());
    Faction first = fixture.saved("first", "Alice");
    Faction second = fixture.saved("second", "Bob");
    var bonus =
        fixture.law(
            "economy",
            "merchant_boost",
            Map.of("effects.domestic_guilds.modifiers", List.of("TRADE_POWER(100)")));
    first.getLawHandler().getGroup("economy").setCurrent(bonus);
    Guild boosted = first.getOrCreateMainGuild();
    Guild ordinary = second.getOrCreateMainGuild();
    Province province = province(1);
    province.setData(boosted.getId(), new ProvinceDataEntry(boosted, 10, 0));
    province.setData(ordinary.getId(), new ProvinceDataEntry(ordinary, 10, 0));
    province.setProsperity(90);
    assertEquals(20, province.getGuildTrade(boosted));
    assertEquals(10, province.getGuildTrade(ordinary));
    assertEquals(60, province.getIncome(boosted), 0.000001);
    assertEquals(30, province.getIncome(ordinary), 0.000001);
    assertEquals(1, province.getTradeShare(boosted) + province.getTradeShare(ordinary), 0.000001);
    assertEquals(90, province.getIncome(boosted) + province.getIncome(ordinary), 0.000001);
  }

  @Test
  void readOnlyAndStoredIncomeBothApplyPillageWhileOnlyTheStoredReadChangesTheBreakdown() {
    productiveBranch();
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    Province province = province(1);
    province.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
    province.setProsperity(100);
    fixture.provincesEnabled(true);
    guild.getTradeBreakdown().setIncome(7);
    PillageTradeHit.attach(guild, -25, 1);
    assertEquals(75, manager.getIncome(guild, false), 0.000001);
    assertEquals(7, guild.getTradeBreakdown().getIncome());
    assertEquals(75, manager.getIncome(guild, true), 0.000001);
    assertEquals(75, guild.getTradeBreakdown().getIncome());
    assertEquals(75, manager.getGrossTradeIncome(guild), 0.000001);
  }

  @Test
  void singleGuildRecalculationStoresIncomeFromTheNewProsperity() {
    productiveBranch();
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    Province province = province(1);
    faction.addProvince(1);
    faction.setCapital(1, true);
    fixture.provincesEnabled(true);
    assertEquals(0, province.getProsperity());
    manager.recalculateForSingleGuild(guild, true);
    assertTrue(province.getProsperity() > 0);
    assertEquals(manager.getGrossTradeIncome(guild), guild.getTradeBreakdown().getIncome(), 0.01);
    assertTrue(guild.getTradeBreakdown().getIncome() > 0);
  }

  @Test
  void aStrongerForeignTradeRouteReplacesTheEarlierRouteEvenBelowItsBonusedStoredValue() {
    productiveBranch();
    Faction owner = fixture.saved("owner", "Alice");
    Faction foreign = fixture.saved("foreign", "Bob");
    Guild guild = foreign.getOrCreateMainGuild();
    Province province = province(1);
    owner.addProvince(1);
    province.calculateTrade(manager, guild, 8, 0);
    double stored = province.getStoredGuildTrade(guild);
    assertTrue(stored > 8);
    assertEquals(8, province.getRawGuildTrade(guild), 0.000001);
    double stronger = (stored + 8) / 2;
    province.calculateTrade(manager, guild, stronger, 0);
    assertEquals(stronger, province.getRawGuildTrade(guild), 0.000001);
    assertTrue(province.getStoredGuildTrade(guild) > stored);
  }

  @ParameterizedTest
  @ValueSource(ints = {-100, -200})
  void zeroOrNegativeTradeModifiersCannotStealIncomeFromAnIndependentCompetitor(int amount) {
    fixture.lawGroup("economy", Map.of());
    Faction reduced = fixture.saved("reduced", "Alice");
    Faction ordinary = fixture.saved("ordinary", "Bob");
    var penalty =
        fixture.law(
            "economy",
            "reduction",
            Map.of("effects.domestic_guilds.modifiers", List.of("TRADE_POWER(" + amount + ")")));
    reduced.getLawHandler().getGroup("economy").setCurrent(penalty);
    Guild first = reduced.getOrCreateMainGuild();
    Guild second = ordinary.getOrCreateMainGuild();
    Province province = province(1);
    province.setData(first.getId(), new ProvinceDataEntry(first, 10, 0));
    province.setProsperity(80);
    assertEquals(0, province.getTotalTrade());
    assertEquals(0, province.getIncome(first));
    assertEquals(0, province.getTradeShare(first));
    assertEquals(0, province.getTradeShare(second));
    province.setData(second.getId(), new ProvinceDataEntry(second, 10, 0));
    assertEquals(10, province.getTotalTrade());
    assertEquals(0, province.getGuildTrade(first));
    assertEquals(80, province.getIncome(second));
    assertEquals(1, province.getTradeShare(second));
  }

  @Test
  void provinceTerrainIdsUseTheSameCaseRulesInEveryServerLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals(Terrain.PLAINS, new Province(1, "plains", 50).getTerrain());
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void unknownTerrainAndUncachedGuildDataDoNotCreateTradeOrMutateTheProvince() {
    Faction home = fixture.saved("home", "Alice");
    Guild guild = home.getOrCreateMainGuild();
    Province unknown = new Province(7, "not-configured", 20, 15, -12);
    assertEquals(Terrain.UNKNOWN, unknown.getTerrain());
    assertEquals(20, unknown.getFertility());
    assertEquals(15, unknown.getCenterX());
    assertEquals(-12, unknown.getCenterZ());
    assertEquals(Terrain.UNKNOWN, new Province(8, null, 0).getTerrain());
    assertFalse(new Province().isValid());
    ProvinceDataEntry empty = unknown.getData(guild.getId());
    assertSame(guild, empty.getGuild());
    assertFalse(empty.isConsidered());
    assertTrue(unknown.getAllData().isEmpty());
    assertNull(unknown.getData("missing"));
    empty.setProduction(2);
    assertTrue(empty.isConsidered());
    assertEquals(0, unknown.getGuildProduction(guild));
    unknown.setData(guild.getId(), empty);
    assertSame(empty, unknown.getData(guild.getId()));
    assertEquals(2, unknown.getGuildProduction(guild));
  }

  @Test
  void snapshotCopiesPreserveProvinceMetadataAndOwnTheirMutableEconomicEntries() {
    Faction home = fixture.saved("home", "Alice");
    Guild guild = home.getOrCreateMainGuild();
    Province live = new Province(1, "PLAINS", 72, 5, -6);
    provinces.put(1, live);
    live.addNeighbour(2);
    ProvinceDataEntry original = new ProvinceDataEntry(guild, 10, 6, 3);
    live.setData(guild.getId(), original);
    live.setProsperity(42);
    ProvinceManager snapshot = manager.createSnapshotShell();
    Province copy = snapshot.get(1);
    assertEquals(Set.of(2), copy.getNeighbours());
    assertEquals(72, copy.getFertility());
    assertEquals(5, copy.getCenterX());
    assertEquals(-6, copy.getCenterZ());
    assertTrue(copy.getAllData().isEmpty());
    assertEquals(0, copy.getProsperity());
    assertThrows(UnsupportedOperationException.class, () -> copy.getNeighbours().add(3));
    snapshot.copyAllDataFrom(manager);
    ProvinceDataEntry copied = copy.getData(guild.getId());
    assertNotSame(original, copied);
    assertEquals(10, copied.getTrade());
    assertEquals(6, copied.getProduction());
    assertEquals(3, copied.getDistance());
    assertTrue(copied.isConsidered());
    assertEquals(42, copy.getProsperity());
    copied.setTrade(999);
    copied.setProduction(888);
    copied.setDistance(0);
    copy.setProsperity(100);
    assertEquals(10, original.getTrade());
    assertEquals(6, original.getProduction());
    assertEquals(3, original.getDistance());
    assertEquals(42, live.getProsperity());
    province(2).setProsperity(50);
    snapshot.copyAllDataFrom(manager);
    assertFalse(snapshot.contains(2));
    assertFalse(snapshot.get(2).isValid());
    List<Province> returned = snapshot.getProvinces();
    returned.clear();
    assertTrue(snapshot.contains(1));
    assertEquals(1, snapshot.getProvinces().size());
  }

  @Test
  void dirtyRecalculationReplacesEconomicsOnlyWhenTheModelChanges() {
    productiveBranch();
    Faction home = fixture.saved("home", "Alice");
    Guild guild = home.getOrCreateMainGuild();
    Province source = province(1);
    home.addProvince(1);
    home.setCapital(1, true);
    fixture.guild(home, "landless", "Bob");
    fixture.provincesEnabled(true);
    manager.recalculateIfNeeded();
    ProvinceDataEntry first = source.getData(guild.getId());
    double prosperity = source.getProsperity();
    assertTrue(prosperity > 0);
    assertEquals(prosperity, guild.getTradeBreakdown().getIncome(), 0.01);
    manager.recalculateIfNeeded();
    assertSame(first, source.getData(guild.getId()));
    manager.markDirty();
    manager.recalculateIfNeeded();
    assertNotSame(first, source.getData(guild.getId()));
    assertEquals(prosperity, source.getProsperity(), 0.01);
    assertEquals(prosperity, guild.getTradeBreakdown().getIncome(), 0.01);
  }

  @Test
  void disabledIncomeQueriesAndRecalculationRespectWhetherSavingWasRequested() {
    productiveBranch();
    Faction home = fixture.saved("home", "Alice");
    Guild guild = home.getOrCreateMainGuild();
    Province source = province(1);
    home.addProvince(1);
    home.setCapital(1, true);
    source.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 3));
    source.setProsperity(40);
    guild.getTradeBreakdown().setIncome(7);
    manager.recalculate();
    manager.recalculateQuiet(null, null);
    manager.recalculateForSingleGuild(guild, false);
    assertEquals(40, source.getProsperity());
    assertEquals(10, source.getStoredGuildTrade(guild));
    assertEquals(0, manager.getGrossTradeIncome(guild));
    assertEquals(0, manager.getIncome(guild, false));
    assertEquals(7, guild.getTradeBreakdown().getIncome());
    assertEquals(0, manager.getIncome(guild, true));
    assertEquals(0, guild.getTradeBreakdown().getIncome());
    fixture.provincesEnabled(true);
    Guild noCapital = fixture.guild(home, "landless", "Bob");
    manager.recalculateForSingleGuild(noCapital, true);
    assertEquals(40, source.getProsperity());
    manager.clearGuildData(null);
    assertEquals(10, source.getStoredGuildTrade(guild));
    manager.clearGuildData(guild.getId());
    assertTrue(source.getAllData().isEmpty());
  }

  @Test
  void quietRecalculationDropsAbsentGuildsAndKeepsLiveBreakdownsUntouched() {
    productiveBranch();
    Faction home = fixture.saved("home", "Alice");
    Guild guild = home.getOrCreateMainGuild();
    Guild idle = fixture.guild(home, "idle", "Bob");
    Province source = province(1);
    home.addProvince(1);
    home.setCapital(1, true);
    source.setData("gone", new ProvinceDataEntry(guild, 100, 100));
    source.setData(null, new ProvinceDataEntry(guild, 100, 100));
    guild.getTradeBreakdown().setIncome(31);
    fixture.provincesEnabled(true);
    manager.recalculateQuiet(List.of(guild, idle), List.of(home));
    assertEquals(Set.of(guild.getId()), source.getAllData().keySet());
    assertTrue(source.getProsperity() > 0);
    assertEquals(31, guild.getTradeBreakdown().getIncome());
    assertTrue(manager.getIncome(guild, false) > 0);
    manager.clearPreviewLists();
    manager.recalculateQuiet(null, null);
    assertTrue(source.getAllData().isEmpty());
    assertEquals(0, source.getProsperity());
    assertEquals(31, guild.getTradeBreakdown().getIncome());
    manager.clearPreviewLists();
  }

  @Test
  void savedMissingCapitalCannotSeedTradeIntoAnUnrelatedProvince() {
    productiveBranch();
    Faction home = fixture.saved("home", "Alice");
    Guild guild = fixture.guild(home, "missing_capital", "Bob");
    guild.setCapital(999);
    Province province = province(1);
    province.setData(guild.getId(), new ProvinceDataEntry(guild, 40, 12));
    fixture.provincesEnabled(true);
    manager.recalculateForSingleGuild(guild, false);
    assertTrue(province.getAllData().isEmpty());
    assertEquals(0, province.getProsperity());
  }

  @Test
  void tariffPreviewIsTargetedAndLeavesRatesBalancesAndBreakdownsUntouched() {
    productiveBranch();
    Faction owner = fixture.saved("owner", "Alice");
    Faction first = fixture.saved("first", "Bob");
    Faction second = fixture.saved("second", "Cara");
    Faction subject = fixture.saved("subject", "Dave");
    fixture.subject(owner, subject);
    Guild one = first.getOrCreateMainGuild();
    Guild two = second.getOrCreateMainGuild();
    Guild domestic = owner.getOrCreateMainGuild();
    Guild vassal = subject.getOrCreateMainGuild();
    Guild absent = fixture.guild(first, "absent", "Eve");
    Province held = province(1);
    Province foreign = province(2);
    province(3);
    owner.addProvince(1);
    second.addProvince(2);
    held.setData(one.getId(), new ProvinceDataEntry(one, 10, 0));
    held.setData(two.getId(), new ProvinceDataEntry(two, 10, 0));
    held.setData(domestic.getId(), new ProvinceDataEntry(domestic, 10, 0));
    held.setData(vassal.getId(), new ProvinceDataEntry(vassal, 10, 0));
    held.setProsperity(100);
    foreign.setData(one.getId(), new ProvinceDataEntry(one, 10, 0));
    foreign.setProsperity(1000);
    owner.getTaxHandler().setTariffs(10);
    owner.getTaxHandler().setSpecificTax(TaxTarget.TARIFFS, second.getId(), 30);
    one.getTradeBreakdown().setIncome(123);
    Map<Guild, Double> base = manager.previewTariffRateChange(owner, null, 20);
    assertEquals(-2.5, base.get(one), 0.000001);
    assertEquals(0, base.get(two));
    assertEquals(0, base.get(domestic));
    assertEquals(0, base.get(vassal));
    assertEquals(0, base.get(absent));
    Map<Guild, Double> specific = manager.previewTariffRateChange(owner, "SECOND", 15);
    assertEquals(3.75, specific.get(two), 0.000001);
    assertEquals(0, specific.get(one));
    assertEquals(10, owner.getTaxHandler().getTariffs());
    assertEquals(30, owner.getTaxHandler().getSpecificTax(TaxTarget.TARIFFS, second.getId()));
    assertEquals(123, one.getTradeBreakdown().getIncome());
    assertEquals(100, held.getProsperity());
  }

  @Test
  void storedIncomeSeparatesForeignTariffsFromDomesticAndWaterIncome() {
    productiveBranch();
    Faction home = fixture.saved("home", "Alice");
    Faction foreign = fixture.saved("foreign", "Bob");
    Guild guild = home.getOrCreateMainGuild();
    Province own = province(1);
    Province other = province(2);
    Province wild = province(3);
    Province sea = new Province(4, "SEA", 0);
    provinces.put(4, sea);
    province(5);
    home.addProvince(1);
    foreign.addProvince(2);
    for (Province province : List.of(own, other, wild, sea)) {
      province.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
      province.setProsperity(100);
    }
    foreign.getTaxHandler().setTariffs(20);
    fixture.provincesEnabled(true);
    assertEquals(300, manager.getIncome(guild));
    var breakdown = guild.getTradeBreakdown();
    assertEquals(300, breakdown.getIncome());
    assertEquals(100, breakdown.getIncomeByFaction(home));
    assertEquals(100, breakdown.getIncomeByFaction(foreign));
    assertEquals(
        100 * foreign.getTaxRate(TaxTarget.TARIFFS, home.getId(), true) / 100,
        breakdown.getTariffsByFaction(foreign),
        0.01);
    assertEquals(0, breakdown.getTariffsByFaction(home));
    assertEquals(40, breakdown.getTradePower());
    assertEquals(300, manager.getGrossTradeIncome(guild));
    double tariffs = breakdown.getTariffs();
    own.calculateTariffs();
    own.calculateTariffs();
    assertEquals(10, own.getStoredGuildTrade(guild));
    assertEquals(0, own.getGuildProduction(guild));
    assertEquals(100, own.getProsperity());
    assertEquals(300, breakdown.getIncome());
    assertEquals(tariffs, breakdown.getTariffs());
    manager.recalculateQuiet(List.of(guild), List.of(home, foreign));
    own.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
    other.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
    wild.setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
    for (Province province : List.of(own, other, wild)) province.setProsperity(100);
    assertEquals(300, manager.getIncome(guild));
    assertEquals(100, breakdown.getIncomeByFaction(foreign));
    manager.clearPreviewLists();
  }

  @Test
  void newTradeAndProductionSeedsSpreadToNeighborsAndNeverReplaceStrongerExistingData() {
    productiveBranch();
    Faction home = fixture.saved("home", "Alice");
    Guild guild = home.getOrCreateMainGuild();
    Province first = province(1);
    Province second = province(2);
    first.addNeighbour(2);
    second.addNeighbour(1);
    first.seedTrade(manager, guild, 20);
    assertEquals(20, first.getRawGuildTrade(guild));
    assertEquals(10, second.getRawGuildTrade(guild));
    assertEquals(1, second.getData(guild.getId()).getDistance());
    first.seedTrade(manager, guild, 1);
    assertEquals(20, first.getRawGuildTrade(guild));
    first.clearData();
    second.clearData();
    first.seedProduction(manager, guild, 10);
    assertEquals(10, first.getGuildProduction(guild));
    assertEquals(10 * Math.sqrt(0.5) * 0.05, second.getGuildProduction(guild), 0.000001);
    assertEquals(1, second.getData(guild.getId()).getDistance());
    first.seedProduction(manager, guild, 1);
    assertEquals(10, first.getGuildProduction(guild));
    first.clearData();
    second.clearData();
    first.calculateProduction(manager, guild, null, 0);
    assertTrue(first.getGuildProduction(guild) > 0);
    assertTrue(second.getGuildProduction(guild) > 0);
    assertEquals(0, first.getData(guild.getId()).getTrade());
  }

  @Test
  void vassalAndOverlordTradeUsesTheCorrectRegionalLawsWithoutAddingUnrelatedEffects() {
    fixture.lawGroup(
        "economy",
        Map.of(
            "effects.domestic_guilds.vassal_territory", List.of("TRADE_POWER(10)"),
            "effects.overlord_guilds.our_territory", List.of("TRADE_POWER(20)"),
            "effects.vassal_guilds.our_territory", List.of("TRADE_POWER(30)"),
            "effects.domestic_guilds.our_territory", List.of("TRADE_POWER(40)"),
            "effects.foreign_guilds.our_territory", List.of("TRADE_POWER(50)")));
    Faction overlord = fixture.saved("overlord", "Alice");
    Faction vassal = fixture.saved("vassal", "Bob");
    fixture.subject(overlord, vassal);
    Province capital = province(1);
    Province subject = province(2);
    overlord.addProvince(1);
    vassal.addProvince(2);
    Guild master = overlord.getOrCreateMainGuild();
    Guild local = vassal.getOrCreateMainGuild();
    for (Province province : List.of(capital, subject)) {
      province.setData(master.getId(), new ProvinceDataEntry(master, 10, 0));
      province.setData(local.getId(), new ProvinceDataEntry(local, 10, 0));
      province.setProsperity(100);
    }
    assertEquals(13, subject.getGuildTrade(master), 0.000001);
    assertEquals(13, capital.getGuildTrade(local), 0.000001);
    assertEquals(14, capital.getGuildTrade(master), 0.000001);
    assertEquals(14, subject.getGuildTrade(local), 0.000001);
    assertEquals(100, subject.getIncome(master) + subject.getIncome(local), 0.000001);
    var agreement =
        fixture.relationType(
            "market_access", Map.of("trade-effects-them", List.of("TRADE_POWER(15)")));
    vassal.getDiplomacyHandler().setTradeRelation(overlord, agreement);
    assertEquals(14.5, subject.getGuildTrade(master), 0.000001);
    assertEquals(100, subject.getIncome(master) + subject.getIncome(local), 0.000001);
  }

  @Test
  void previewAndLiveGraphAccessUseTheAppropriateFactionSnapshot() {
    productiveBranch();
    Faction home = fixture.saved("home", "Alice");
    Faction foreign = fixture.saved("foreign", "Bob");
    Guild guild = home.getOrCreateMainGuild();
    Province capital = province(1);
    Province destination = province(2);
    province(3);
    home.addProvince(1);
    foreign.addProvince(2);
    home.setCapital(1, true);
    var treaty = fixture.relationType("open_stations", Map.of("installation-access", 1.0));
    home.getDiplomacyHandler().setTradeRelation(foreign, treaty);
    Installation start =
        new Installation("start", "Start", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);
    Installation end = new Installation("end", "End", InstallationKind.TRAIN_STATION, 2, 0, 0, 1L);
    Installation missingOwner =
        new Installation("gone", "Gone", InstallationKind.TRAIN_STATION, 3, 0, 0, 1L);
    TradeGraph graph =
        TradeGraphBuilder.build(
            List.of(
                new TradeGraphBuilder.Site("HOME", start, true),
                new TradeGraphBuilder.Site("FOREIGN", end, true),
                new TradeGraphBuilder.Site("missing", missingOwner, true)),
            Map.of(),
            (left, right) -> Optional.of(new RailRoutes.Route(0, List.of())),
            point -> 0);
    manager.setHighwayOverride(graph);
    fixture.provincesEnabled(true);
    manager.recalculateForSingleGuild(guild, false);
    assertTrue(capital.getStoredGuildTrade(guild) > 0);
    assertTrue(destination.getStoredGuildTrade(guild) > 0);
    double available = destination.getStoredGuildTrade(guild);
    manager.recalculateQuiet(List.of(guild), List.of(home, foreign));
    assertEquals(available, destination.getStoredGuildTrade(guild), 0.000001);
    manager.recalculateQuiet(List.of(guild), List.of(home));
    assertEquals(0, destination.getStoredGuildTrade(guild));
    manager.clearPreviewLists();
    manager.recalculateForSingleGuild(guild, false);
    assertEquals(available, destination.getStoredGuildTrade(guild), 0.000001);
    ProvinceManager snapshot = manager.createSnapshotShell();
    manager.setHighwayOverride(null);
    TradeGraph.setLiveForTests(null);
    snapshot.recalculateForSingleGuild(guild, false);
    assertEquals(available, snapshot.get(2).getStoredGuildTrade(guild), 0.000001);
  }

  @Test
  void incomePreviewsArePrivateCopiesAndThePerTickCacheRefreshesAtTheNextTick() throws Exception {
    Field shared = EconomicPreview.class.getDeclaredField("shared");
    Field sharedTick = EconomicPreview.class.getDeclaredField("sharedTick");
    shared.setAccessible(true);
    sharedTick.setAccessible(true);
    Object oldPrepared = shared.get(null);
    int oldTick = sharedTick.getInt(null);
    try {
      productiveBranch();
      fixture.lawGroup("economy", Map.of());
      Faction home = fixture.saved("home", "Alice");
      Guild guild = home.getOrCreateMainGuild();
      Province province = province(1);
      home.addProvince(1);
      home.setCapital(1, true);
      fixture.guild(home, "landless", "Bob");
      fixture.provincesEnabled(true);
      manager.recalculate();
      double prosperity = province.getProsperity();
      double income = guild.getTradeBreakdown().getIncome();
      when(Bukkit.getCurrentTick()).thenReturn(100, 100, 101);
      shared.set(null, null);
      var prepared = EconomicPreview.current();
      assertSame(prepared, EconomicPreview.current());
      ProvinceManager copied = EconomicPreview.copyOf(prepared);
      copied.get(1).setProsperity(999);
      assertEquals(prosperity, province.getProsperity());
      assertNotSame(prepared, EconomicPreview.current());
      var original = home.getLawHandler().getGroup("economy").getCurrent();
      var productiveLaw =
          fixture.law(
              "economy",
              "production",
              Map.of("effects.domestic_guilds.modifiers", List.of("PRODUCTION(100)")));
      Map<Guild, Double> delta =
          manager.previewLawIncomeExact(
              home, home.getLawHandler().getGroup("economy"), productiveLaw);
      assertTrue(delta.get(guild) > 0);
      assertSame(original, home.getLawHandler().getGroup("economy").getCurrent());
      assertEquals(prosperity, province.getProsperity());
      assertEquals(income, guild.getTradeBreakdown().getIncome());
      double beforeFavour = guild.getLedger().getNetIncome();
      double predictedFavour =
          manager.previewFavourRepressIncomeExact(home, guild, true).get(guild);
      assertFalse(guild.isFavoured());
      assertEquals(income, guild.getTradeBreakdown().getIncome());
      guild.setFavoured(true);
      manager.recalculate();
      assertEquals(guild.getLedger().getNetIncome() - beforeFavour, predictedFavour, 0.02);
      guild.setFavoured(false);
      manager.recalculate();
      Branch branch = guild.getBranches().get(0);
      int level = branch.getLevel();
      assertTrue(manager.previewUpgradeIncomeExact(guild, branch) > 0);
      assertEquals(0, manager.previewDowngradeIncomeExact(guild, branch));
      assertEquals(level, branch.getLevel());
      branch.levelUp();
      manager.recalculate();
      double upgradedIncome = guild.getTradeBreakdown().getIncome();
      assertTrue(manager.previewDowngradeIncomeExact(guild, branch) < 0);
      assertEquals(level + 1, branch.getLevel());
      assertEquals(upgradedIncome, guild.getTradeBreakdown().getIncome());
      Map<Guild, Double> projected = EconomicPreview.projectNets(EconomicPreview.copyOf(prepared));
      assertEquals(FactionManager.getAllGuilds().size(), projected.size());
      assertTrue(Double.isFinite(projected.get(guild)));
      assertNull(IncomePreviewContext.current());
    } finally {
      shared.set(null, oldPrepared);
      sharedTick.setInt(null, oldTick);
      IncomePreviewContext.clear();
    }
  }
}
