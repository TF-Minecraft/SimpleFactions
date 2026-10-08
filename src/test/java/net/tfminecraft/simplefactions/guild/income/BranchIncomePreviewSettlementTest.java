package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The branch preview must predict what the daily ledger actually settles after the level
 * changes: guild tax on gross trade, upkeep, tariffs both ways, and the realm treasury.
 */
class BranchIncomePreviewSettlementTest {
  private FactionDomainFixture fixture;
  private Double previousCarry;
  private ProvinceManager live;
  private Faction home;
  private Faction rival;
  private Guild realm;
  private Guild fields;
  private Guild mill;
  private Guild traders;
  private Branch fieldsBranch;

  @BeforeEach
  void setUp() {
    fixture = new FactionDomainFixture();
    previousCarry = Cache.tradeCarry.get(Terrain.PLAINS);
    fixture.provincesEnabled(true);
    Cache.tradeCarry.put(Terrain.PLAINS, 0.8);

    Map<Integer, Province> provinces = new HashMap<>();
    for (int id = 1; id <= 4; id++) {
      provinces.put(id, new Province(id, "PLAINS", 40, id * 16, 0));
    }
    for (int id = 1; id < 4; id++) {
      provinces.get(id).addNeighbour(id + 1);
      provinces.get(id + 1).addNeighbour(id);
    }
    fixture.provinceData.putAll(provinces);

    home = fixture.saved("home", "Leader");
    home.addProvince(1);
    home.addProvince(2);
    rival = fixture.saved("rival", "Other");
    rival.addProvince(3);
    rival.addProvince(4);
    home.getTaxHandler().setTaxRate(TaxTarget.GUILDS, null, 25);
    home.getTaxHandler().setTaxRate(TaxTarget.TARIFFS, null, 20);
    rival.getTaxHandler().setTaxRate(TaxTarget.TARIFFS, null, 20);

    // A guild's trade is cut when its realm is too small to bear it, so the realms are large.
    realm = home.getOrCreateMainGuild();
    realm.setCapital(1);
    realm.getBranches().put(0, new Branch(realm.getBranches().get(0), 30));
    addTradeBranch(realm, "treasury", 2);
    fields = fixture.guild(home, "fields", "Farmer");
    fields.setCapital(1);
    fields.getBranches().clear();
    addTradeBranch(fields, "fields", 2);
    mill = fixture.guild(home, "mill", "Miller");
    mill.setCapital(2);
    mill.getBranches().clear();
    addTradeBranch(mill, "mill", 2);
    Guild rivalRealm = rival.getOrCreateMainGuild();
    rivalRealm.getBranches().put(0, new Branch(rivalRealm.getBranches().get(0), 30));
    traders = fixture.guild(rival, "traders", "Trader");
    traders.setCapital(3);
    traders.getBranches().clear();
    addTradeBranch(traders, "traders", 2);
    fieldsBranch = fields.getBranches().get(1);

    live = new ProvinceManager();
    live.start(provinces);
    live.recalculate();
  }

  @AfterEach
  void tearDown() {
    if (previousCarry == null) Cache.tradeCarry.remove(Terrain.PLAINS);
    else Cache.tradeCarry.put(Terrain.PLAINS, previousCarry);
    fixture.close();
  }

  private static void addTradeBranch(Guild guild, String id, int level) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("name", id);
    config.set("group", 1);
    config.set(
        "modifiers",
        List.of("TRADE_POWER 6 3", "PRODUCTION 3 2", "TRADE_CARRY 1 0.2", "TRADE_UPKEEP 0.1 0.05"));
    guild.getBranches().put(1, new Branch(new Branch(id, config), level));
  }

  private Map<Guild, Double> settledNets() {
    Map<Guild, Double> nets = new HashMap<>();
    for (Guild guild : List.of(realm, fields, mill, traders)) {
      nets.put(guild, guild.getLedger().getNetIncome());
    }
    return nets;
  }

  /** Changes the level for real, settles the day's lines the way the menu does, then undoes it. */
  private Map<Guild, Double> settledChange(Guild guild, Branch branch, int levelDelta) {
    Map<Guild, Double> before = settledNets();
    if (levelDelta > 0) branch.levelUp();
    else branch.levelDown();
    live.recalculateForSingleGuild(guild, true);
    Map<Guild, Double> after = settledNets();
    if (levelDelta > 0) branch.levelDown();
    else branch.levelUp();
    live.recalculate();
    Map<Guild, Double> change = new HashMap<>();
    after.forEach((g, net) -> change.put(g, net - before.get(g)));
    return change;
  }

  private BranchIncomePreview.Estimate preview(Guild guild, Branch branch, int levelDelta) {
    var current = BranchIncomePreview.modifiers(guild);
    return BranchIncomePreview.estimate(
        BranchIncomePreview.prepare(live),
        guild,
        current,
        BranchIncomePreview.adjust(current, branch, branch.getLevel(), levelDelta));
  }

  /** The ledger rounds every line to the cent, so a guild's settled change can drift a few cents. */
  private static final double OWN_CENTS = 0.03;

  private static final double REALM_CENTS = 0.1;

  private static double homeTotal(Map<Guild, Double> change, Guild... guilds) {
    double total = 0;
    for (Guild guild : guilds) total += change.get(guild);
    return total;
  }

  @Test
  void theScenarioPaysTaxUpkeepAndTariffsBothWays() {
    assertTrue(fields.getTradeBreakdown().getIncome() > 0);
    assertTrue(fields.getTradeBreakdown().getUpkeep() > 0);
    assertTrue(fields.getTradeBreakdown().getTariffs() > 0, "fields trades into rival land");
    assertTrue(traders.getTradeBreakdown().getTariffsByFaction(home) > 0, "traders pay home");
    double guildTax = home.getTaxRate(TaxTarget.GUILDS, fields.getId(), true) / 100.0;
    assertTrue(guildTax > 0);
    assertEquals(guildTax, BranchIncomePreview.taxFraction(fields));
    assertEquals(0.0, BranchIncomePreview.taxFraction(realm), "the realm guild pays no guild tax");
  }

  @Test
  void guildUpgradeMatchesTheSettledLedgerForTheGuildAndTheRealm() {
    BranchIncomePreview.Estimate estimate = preview(fields, fieldsBranch, 1);
    Map<Guild, Double> settled = settledChange(fields, fieldsBranch, 1);

    assertEquals(settled.get(fields), estimate.own(), OWN_CENTS);
    assertEquals(homeTotal(settled, realm, fields, mill), estimate.realm(), REALM_CENTS);
    assertNotEquals(estimate.own(), estimate.realm(), "sister guilds and the treasury move too");
    assertEquals(2, fieldsBranch.getLevel());
  }

  @Test
  void guildDowngradeMatchesTheSettledLedger() {
    BranchIncomePreview.Estimate estimate = preview(fields, fieldsBranch, -1);
    Map<Guild, Double> settled = settledChange(fields, fieldsBranch, -1);

    assertTrue(estimate.own() < 0);
    assertEquals(settled.get(fields), estimate.own(), OWN_CENTS);
    assertEquals(homeTotal(settled, realm, fields, mill), estimate.realm(), REALM_CENTS);
  }

  @Test
  void withProvincesOffThereIsNoTradeAndNoChange() {
    assertTrue(live.getTradeIncome(fields).gross() > 0);
    fixture.provincesEnabled(false);

    assertEquals(TradeIncome.NONE, live.getTradeIncome(fields));
    assertEquals(new BranchIncomePreview.Estimate(0, 0), preview(fields, fieldsBranch, 1));
  }

  @Test
  void realmGuildUpgradeIsNotTaxedAndIncludesTheTreasury() {
    Branch realmBranch = realm.getBranches().get(1);
    BranchIncomePreview.Estimate estimate = preview(realm, realmBranch, 1);
    Map<Guild, Double> settled = settledChange(realm, realmBranch, 1);

    assertEquals(settled.get(realm), estimate.own(), OWN_CENTS);
    assertEquals(homeTotal(settled, realm, fields, mill), estimate.realm(), REALM_CENTS);
  }
}
