package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A full or single-guild recalculation reads every guild's income with each province's total trade
 * summed once. The breakdowns must match reads that sum the total on every ask, as before.
 */
class ProvinceIncomeHoldTest {
  private FactionDomainFixture fixture;
  private ProvinceManager manager;
  private Map<Integer, Province> provinces;
  private Map<Terrain, Double> oldCarry;
  private double oldMaxUpkeep;
  private TradeGraph oldGraph;
  private final List<Guild> guilds = new java.util.ArrayList<>();

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
    Cache.tradeCarry.put(Terrain.PLAINS, 0.8);
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

  /**
   * A 4x4 plains map with three realms, two trading guilds, a trade-power law in one realm,
   * unowned land between them and the fixture's 30 % tariffs, so most provinces are contested.
   */
  private void realm() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("industry.allowed-types", List.of("realm", "guild"));
    yaml.set("industry.group", 0);
    yaml.set(
        "industry.modifiers",
        List.of("TRADE_POWER 10 2", "PRODUCTION 10 5", "TRADE_CARRY 1 0", "TRADE_UPKEEP 0 0"));
    BranchLoader.map.clear();
    BranchLoader.map.put(
        "industry", new Branch("industry", yaml.getConfigurationSection("industry")));
    for (int row = 0; row < 4; row++) {
      for (int column = 0; column < 4; column++) {
        int id = row * 4 + column + 1;
        provinces.put(id, new Province(id, "PLAINS", 40 + id));
      }
    }
    for (Province province : provinces.values()) {
      int id = province.getId();
      if ((id - 1) % 4 != 3) link(id, id + 1);
      if (id <= 12) link(id, id + 4);
    }
    fixture.lawGroup("economy", Map.of());
    var boost =
        fixture.law(
            "economy",
            "merchant_boost",
            Map.of("effects.domestic_guilds.modifiers", List.of("TRADE_POWER(40)")));
    Faction north = realm("north", "Alice", 1, 2, 5);
    Faction south = realm("south", "Bob", 16, 15, 12);
    Faction east = realm("east", "Cara", 4, 8);
    north.getLawHandler().getGroup("economy").setCurrent(boost);
    Guild traders = fixture.guild(north, "north_traders", "Dan");
    traders.setCapital(5);
    guilds.add(traders);
    Guild shipwrights = fixture.guild(south, "south_shipwrights", "Eve");
    shipwrights.setCapital(12);
    guilds.add(shipwrights);
    assertNotNull(east);
    fixture.provincesEnabled(true);
  }

  private Faction realm(String id, String leader, int capital, int... more) {
    Faction faction = fixture.saved(id, leader);
    faction.addProvince(capital);
    for (int province : more) faction.addProvince(province);
    faction.setCapital(capital, true);
    guilds.add(faction.getOrCreateMainGuild());
    return faction;
  }

  private void link(int first, int second) {
    provinces.get(first).addNeighbour(second);
    provinces.get(second).addNeighbour(first);
  }

  /** Every value a breakdown keeps, as exact text so equal means bit-for-bit equal. */
  private Map<String, String> breakdowns() {
    Map<String, String> all = new TreeMap<>();
    for (Guild guild : guilds) {
      TradeBreakdown breakdown = guild.getTradeBreakdown();
      Map<String, Double> incomes = new TreeMap<>();
      breakdown.getIncomes().forEach((owner, value) -> incomes.put(owner.getId(), value));
      Map<String, Double> tariffs = new TreeMap<>();
      breakdown.getTariffsByFactionMap().forEach((owner, value) -> tariffs.put(owner.getId(), value));
      all.put(
          guild.getId(),
          breakdown.getIncome() + " " + breakdown.getUpkeep() + " " + breakdown.getTariffs()
              + " " + breakdown.getTradePower() + " " + incomes + " " + tariffs);
    }
    return all;
  }

  /** The reads as they ran before: each province sums its total trade on every income ask. */
  private Map<String, String> breakdownsSummedOnEveryRead() {
    for (Guild guild : guilds) manager.getIncome(guild, true);
    return breakdowns();
  }

  private boolean contested() {
    return provinces.values().stream().anyMatch(province -> province.getAllData().size() >= 3);
  }

  @Test
  void aFullRecalculationStoresTheBreakdownsThatPerReadTotalsGive() {
    realm();
    manager.recalculate();
    assertTrue(contested(), "The fixture must share provinces between guilds");
    Map<String, String> held = breakdowns();
    assertTrue(guilds.stream().allMatch(guild -> guild.getTradeBreakdown().getIncome() > 0));
    assertTrue(guilds.stream().anyMatch(guild -> guild.getTradeBreakdown().getTariffs() > 0));
    assertEquals(breakdownsSummedOnEveryRead(), held);
    for (Guild guild : guilds) {
      TradeBreakdown stored = guild.getTradeBreakdown();
      assertEquals(stored.getIncome() - stored.getUpkeep(), manager.getIncome(guild, false), 0.01);
    }
  }

  @Test
  void aSingleGuildRecalculationStoresTheBreakdownsThatPerReadTotalsGive() {
    realm();
    manager.recalculate();
    Guild traders = guilds.stream().filter(g -> g.getId().equals("north_traders")).findFirst().orElseThrow();
    traders.setCapital(6);
    manager.recalculateForSingleGuild(traders, true);
    assertTrue(contested());
    Map<String, String> held = breakdowns();
    assertEquals(breakdownsSummedOnEveryRead(), held);
  }

  @Test
  void aHeldTotalAnswersUntilReleasedAndANewHoldSumsAgain() {
    realm();
    Faction other = fixture.saved("other", "Finn");
    Guild first = other.getOrCreateMainGuild();
    Guild second = fixture.guild(other, "second", "Gus");
    Province province = provinces.get(10);
    ProvinceDataEntry entry = new ProvinceDataEntry(first, 10, 0);
    province.setData(first.getId(), entry);
    province.setData(second.getId(), new ProvinceDataEntry(second, 30, 0));
    double before = province.getTotalTrade();
    province.holdTotalTrade(true);
    assertEquals(before, province.getTotalTrade());
    entry.setTrade(50);
    assertEquals(before, province.getTotalTrade(), "A held total is not summed again");
    province.holdTotalTrade(false);
    double after = province.getTotalTrade();
    assertNotEquals(before, after);
    province.holdTotalTrade(true);
    assertEquals(after, province.getTotalTrade());
    province.holdTotalTrade(false);
    entry.setTrade(Double.NaN);
    province.holdTotalTrade(true);
    assertTrue(Double.isNaN(province.getTotalTrade()));
    assertTrue(Double.isNaN(province.getTotalTrade()));
    province.holdTotalTrade(false);
  }

  @Test
  void everyProvinceIsReleasedAfterTheRecalculations() {
    realm();
    manager.recalculate();
    Guild traders = guilds.stream().filter(g -> g.getId().equals("north_traders")).findFirst().orElseThrow();
    manager.recalculateForSingleGuild(traders, true);
    for (Province province : provinces.values()) {
      if (province.getAllData().isEmpty()) continue;
      double total = province.getTotalTrade();
      ProvinceDataEntry entry = province.getAllData().values().iterator().next();
      entry.setTrade(entry.getTrade() + 100);
      assertNotEquals(total, province.getTotalTrade(), "Province " + province.getId() + " stayed held");
    }
  }
}
