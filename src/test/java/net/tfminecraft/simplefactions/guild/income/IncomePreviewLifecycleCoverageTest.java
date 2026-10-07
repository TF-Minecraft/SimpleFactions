package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.income.entry.PlayerEntry;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IncomePreviewLifecycleCoverageTest {
  @AfterEach
  void clearPreviewState() {
    IncomePreviewContext.clear();
    GuildModifierOverride.clear();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aTradePreviewChangesTheCounterpartyExactlyWhenTheRealTreatyWould(boolean mutual) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var other = fixture.saved("other", "OtherLeader");
      var previous = fixture.relationType("old", Map.of("name", "Old treaty"));
      var counterpart = fixture.relationType("counterpart", Map.of("name", "Counterpart"));
      var proposed =
          fixture.relationType(
              "proposed",
              mutual
                  ? Map.of("mutual", true)
                  : Map.of("mutual", false, "link", counterpart.getId()));
      other.getDiplomacyHandler().setTradeRelation(home, previous);
      IncomePreviewContext.open(IncomePreviewContext.trade(home, other, proposed));
      try {
        assertSame(proposed, home.getDiplomacyHandler().getTradeRelation(other.getId()));
        assertSame(
            mutual ? proposed : previous,
            other.getDiplomacyHandler().getTradeRelation(home.getId()));
      } finally {
        IncomePreviewContext.clear();
      }
      assertNull(home.getDiplomacyHandler().getTradeRelation(other.getId()));
      assertSame(previous, other.getDiplomacyHandler().getTradeRelation(home.getId()));
    }
  }

  @Test
  void frozenPreviewModifierWeightsDoNotFollowLaterCallerMapEdits() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var faction = fixture.saved("home", "Leader");
      var guild = fixture.guild(faction, "merchants", "Merchant");
      double live = guild.getModifier(GuildModifier.PRODUCTION);
      EnumMap<GuildModifier, Double> captured = new EnumMap<>(GuildModifier.class);
      captured.put(GuildModifier.PRODUCTION, 12.5);
      GuildModifierOverride.use(guild, captured);
      try {
        captured.put(GuildModifier.PRODUCTION, 999.0);
        assertEquals(12.5, GuildModifierOverride.resolve(guild, GuildModifier.PRODUCTION));
        assertEquals(live, guild.getModifier(GuildModifier.PRODUCTION));
      } finally {
        GuildModifierOverride.clear();
      }
      assertEquals(live, GuildModifierOverride.resolve(guild, GuildModifier.PRODUCTION));
    }
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,true,true,false",
    "true,false,true,false,false",
    "false,true,true,false,true",
    "false,false,false,false,true",
    "false,true,false,false,false",
    "true,false,false,true,false"
  })
  void favourPreviewsRespectMutualExclusionAndNeverToggleTheLiveGuild(
      boolean favoured,
      boolean repressed,
      boolean favourMode,
      boolean expectedFavour,
      boolean expectedRepress) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var guild = fixture.guild(home, "merchant", "Merchant");
      var other = fixture.guild(home, "other", "Other");
      guild.setFavoured(favoured);
      guild.setRepressed(repressed);
      IncomePreviewContext.open(IncomePreviewContext.favour(guild, favourMode));
      assertEquals(expectedFavour, guild.isFavoured());
      assertEquals(expectedRepress, guild.isRepressed());
      assertNull(IncomePreviewContext.overlayFavoured(other));
      assertNull(IncomePreviewContext.overlayRepressed(other));
      assertFalse(other.isFavoured());
      assertFalse(other.isRepressed());
      IncomePreviewContext.clear();
      assertEquals(favoured, guild.isFavoured());
      assertEquals(repressed, guild.isRepressed());
    }
  }

  @Test
  void scratchTradeBooksArePerGuildAndDiscardedWithoutTouchingLiveIncome() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var first = fixture.guild(home, "first", "First");
      var second = fixture.guild(home, "second", "Second");
      TradeBreakdown original = first.getTradeBreakdown();
      original.setIncome(100);
      original.registerIncome(home, 100);
      IncomePreviewContext.open(IncomePreviewContext.scratch());
      assertNull(IncomePreviewContext.scratchBreakdown(null));
      TradeBreakdown scratch = first.getTradeBreakdown();
      assertNotSame(original, scratch);
      scratch.setIncome(25);
      scratch.registerIncome(home, 25);
      assertSame(scratch, first.getTradeBreakdown());
      assertEquals(0.0, second.getTradeBreakdown().getIncome());
      assertEquals(100.0, original.getIncome());
      IncomePreviewContext.clear();
      assertSame(original, first.getTradeBreakdown());
      assertEquals(100.0, first.getTradeBreakdown().getIncomeByFaction(home));
      IncomePreviewContext.open(IncomePreviewContext.tax(home, TaxTarget.GUILDS, null, 20));
      assertSame(
          original,
          first.getTradeBreakdown(),
          "Tax-only previews must retain the actual trade book");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void endingATradeAgreementPreviewsOnlyTheSidesThatWouldActuallyBeRemoved(boolean mutual) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var other = fixture.saved("other", "Other");
      var treaty = fixture.relationType("trade", Map.of("mutual", mutual));
      var retained = fixture.relationType("independent", Map.of("name", "Independent"));
      home.getDiplomacyHandler().setTradeRelation(other, treaty);
      other.getDiplomacyHandler().setTradeRelation(home, retained);
      IncomePreviewContext.open(IncomePreviewContext.trade(home, other, null));
      assertFalse(home.getDiplomacyHandler().hasTradeRelation(other.getId()));
      assertEquals(!mutual, other.getDiplomacyHandler().hasTradeRelation(home.getId()));
      assertNull(IncomePreviewContext.overlayTrade(home.getDiplomacyHandler(), "unrelated"));
      assertFalse(IncomePreviewContext.overridesTrade(null, "other"));
      assertFalse(IncomePreviewContext.overridesTrade(home.getDiplomacyHandler(), null));
      IncomePreviewContext.clear();
      assertSame(treaty, home.getDiplomacyHandler().getTradeRelation(other.getId()));
      assertSame(retained, other.getDiplomacyHandler().getTradeRelation(home.getId()));
      assertNull(IncomePreviewContext.overlayTrade(home.getDiplomacyHandler(), other.getId()));
    }
  }

  @Test
  void linkedMutualTradePreviewsTheConfiguredCounterpartWithoutChangingStoredRelations() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var other = fixture.saved("other", "Other");
      var counterpart =
          fixture.relationType("receiving", Map.of("mutual", true, "link", "granting"));
      var agreement = fixture.relationType("granting", Map.of("mutual", true, "link", "receiving"));
      IncomePreviewContext.open(IncomePreviewContext.trade(home, other, agreement));
      assertSame(agreement, home.getDiplomacyHandler().getTradeRelation("other"));
      assertSame(counterpart, other.getDiplomacyHandler().getTradeRelation("home"));
      assertTrue(home.getDiplomacyHandler().getTradeRelations().isEmpty());
      assertTrue(other.getDiplomacyHandler().getTradeRelations().isEmpty());
    }
  }

  @ParameterizedTest
  @CsvSource({"GUILDS,GUILD_ID", "VASSALS,VASSAL_ID", "TARIFFS,TARIFF_ID"})
  void genericAndSpecificTaxPreviewsRespectOverridesAndOnlyAffectTheirFaction(
      TaxTarget category, TaxTarget specific) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var other = fixture.saved("other", "Other");
      var taxes = home.getTaxHandler();
      taxes.setTaxRate(category, null, 20);
      taxes.setTaxRate(specific, "vip", 5);
      IncomePreviewContext context = IncomePreviewContext.tax(home, category, null, 35);
      IncomePreviewContext.open(context);
      assertTrue(context.affects(home));
      assertFalse(context.affects(other));
      assertFalse(context.affects(null));
      assertFalse(context.previewsLaw(home));
      assertEquals(35.0, taxes.getTaxRate(category, "ordinary", false));
      assertEquals(35.0, taxes.getTaxRate(specific, "ordinary", false));
      assertEquals(5.0, taxes.getTaxRate(category, "vip", false));
      assertEquals(5.0, taxes.getTaxRate(specific, "vip", false));
      assertEquals(
          other.getTaxHandler().getTaxRate(category, null, false),
          other.getTaxHandler().getTaxRate(category, "ordinary", false));
      assertEquals(5.0, taxes.getTaxRate(TaxTarget.CITIZENS, null, false));
      IncomePreviewContext.open(IncomePreviewContext.tax(home, specific, "vip", 11));
      assertEquals(11.0, taxes.getTaxRate(category, "vip", false));
      assertEquals(11.0, taxes.getTaxRate(specific, "VIP", false));
      assertEquals(20.0, taxes.getTaxRate(category, "ordinary", false));
      assertEquals(20.0, taxes.getTaxRate(category, null, false));
      IncomePreviewContext.clear();
      assertEquals(20.0, taxes.getTaxRate(category, "ordinary", false));
      assertEquals(5.0, taxes.getTaxRate(category, "vip", false));
    }
  }

  @ParameterizedTest
  @CsvSource({
    "CITIZENS,citizen_tax",
    "GUILDS,guild_tax",
    "GUILD_ID,guild_tax",
    "VASSALS,vassal_tax",
    "VASSAL_ID,vassal_tax",
    "DIVIDENDS,dividend_tax",
    "TARIFFS,tariffs",
    "TARIFF_ID,tariffs"
  })
  void proposedLawLimitsAndVetoesAreTemporaryAndApplyToEveryTaxAlias(TaxTarget target, String key) {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      fixture.lawGroup("taxes", Map.of());
      var home = fixture.saved("home", "Leader");
      var other = fixture.saved("other", "Other");
      var group = home.getLawHandler().getGroup("taxes");
      var original = group.getCurrent();
      var candidate =
          fixture.law("taxes", "capped", Map.of("effects.faction.brackets." + key, "10-20"));
      var context = IncomePreviewContext.law(home, group, candidate);
      IncomePreviewContext.open(context);
      assertTrue(context.affects(home));
      assertTrue(context.previewsLaw(home));
      assertFalse(context.previewsLaw(other));
      assertSame(candidate, group.getCurrent());
      assertNull(IncomePreviewContext.overlayLaw(other.getLawHandler().getGroup("taxes")));
      assertEquals(10.0, context.adjustTax(home, home.getTaxHandler(), target, "specific", 5));
      assertEquals(20.0, context.adjustTax(home, home.getTaxHandler(), target, "specific", 30));
      assertEquals(15.0, context.adjustTax(home, home.getTaxHandler(), target, "specific", 15));
      assertEquals(30.0, context.adjustTax(other, other.getTaxHandler(), target, "specific", 30));
      assertEquals(30.0, context.adjustTax(null, null, target, null, 30));
      IncomePreviewContext.clear();
      assertSame(original, group.getCurrent());
      var forbidden =
          fixture.law(
              "taxes", "forbidden", Map.of("effects.faction.rules", List.of(key + " false")));
      IncomePreviewContext.open(IncomePreviewContext.law(home, group, forbidden));
      assertEquals(0.0, home.getTaxHandler().getTaxRate(target, "specific", false));
      IncomePreviewContext.clear();
      assertTrue(home.getTaxHandler().getTaxRate(target, "specific", false) > 0);
      var irrelevant =
          fixture.law(
              "taxes", "foreign_only", Map.of("effects.vassals.rules", List.of(key + " false")));
      IncomePreviewContext.open(IncomePreviewContext.law(home, group, irrelevant));
      assertEquals(
          30.0,
          IncomePreviewContext.current().adjustTax(home, home.getTaxHandler(), target, null, 30));
    }
  }

  @Test
  void independentThreadsCannotSeeOrClearAnotherThreadsTaxPreview() throws Exception {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var original = IncomePreviewContext.tax(home, TaxTarget.GUILDS, null, 35);
      IncomePreviewContext.open(original);
      var worker = Executors.newSingleThreadExecutor();
      try {
        assertEquals(
            10.0,
            worker
                .submit(
                    () -> {
                      assertNull(IncomePreviewContext.current());
                      IncomePreviewContext.open(
                          IncomePreviewContext.tax(home, TaxTarget.GUILDS, null, 60));
                      try {
                        assertEquals(
                            60.0, home.getTaxHandler().getTaxRate(TaxTarget.GUILDS, null, false));
                      } finally {
                        IncomePreviewContext.clear();
                      }
                      return home.getTaxHandler().getTaxRate(TaxTarget.GUILDS, null, false);
                    })
                .get(5, TimeUnit.SECONDS));
        assertSame(original, IncomePreviewContext.current());
        assertEquals(35.0, home.getTaxHandler().getTaxRate(TaxTarget.GUILDS, null, false));
      } finally {
        worker.shutdownNow();
      }
      IncomePreviewContext.clear();
      assertEquals(10.0, home.getTaxHandler().getTaxRate(TaxTarget.GUILDS, null, false));
    }
  }

  @Test
  void tradeBreakdownsAccumulateByCounterpartyAndClearAllTotalsTogether() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var first = fixture.saved("first", "First");
      var second = fixture.saved("second", "Second");
      TradeBreakdown book = new TradeBreakdown();
      book.setIncome(123.456);
      book.setUpkeep(12.345);
      book.setTariffs(1.234);
      book.setTradePower(9.876);
      book.registerIncome(first, 20.125);
      book.registerIncome(first, 10.0);
      book.registerIncome(second, 50.0);
      book.registerTariffs(first, 2.125);
      book.registerTariffs(first, 1.0);
      assertEquals(30.13, book.getIncomeByFaction(first));
      assertEquals(3.13, book.getTariffsByFaction(first));
      assertEquals(List.of(second, first), book.getFactionsByIncomeDesc());
      assertEquals(109.88, book.getNetTradeIncome());
      assertEquals(9.88, book.getTradePower());
      assertEquals(12.35, book.getUpkeep());
      assertEquals(1.23, book.getTariffs());
      assertEquals(2, book.getIncomes().size());
      assertEquals(1, book.getTariffsByFactionMap().size());
      book.clear();
      assertEquals(0.0, book.getIncome());
      assertEquals(0.0, book.getNetTradeIncome());
      assertEquals(0.0, book.getTradePower());
      assertTrue(book.getIncomes().isEmpty());
      assertTrue(book.getTariffsByFactionMap().isEmpty());
    }
  }

  @Test
  void playerTaxEntriesCanBeAdjustedWithoutChangingTheirOrigin() {
    PlayerEntry entry = new PlayerEntry("Alice", 12.5);
    assertEquals("Alice", entry.getOrigin());
    assertEquals(12.5, entry.getAmount());
    entry.setAmount(8.25);
    assertEquals("Alice", entry.getOrigin());
    assertEquals(8.25, entry.getAmount());
  }

  @Test
  void anAbsentTaxTargetLeavesAllStoredRatesAndLedgersUnchanged() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var guild = fixture.guild(home, "merchant", "Merchant");
      guild.getLedger().setCitizenTaxes(Map.of("Merchant", 100.0));
      double original = guild.getLedger().getNetIncome();
      Map<?, Double> changes = EconomicPreview.tax(home, null, null, 75);
      assertEquals(0.0, changes.get(guild));
      assertEquals(original, guild.getLedger().getNetIncome());
      assertNull(IncomePreviewContext.current());
      assertEquals(10.0, home.getTaxHandler().getTaxRate(TaxTarget.GUILDS, null, false));
    }
  }

  @Test
  void aSavedGuildWithoutAnAttachedRealmHasNoTaxUntilItsHostIsRestored() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      GuildData stored =
          new com.google.gson.Gson()
              .fromJson(
                  """
                  {"id":"merchants","name":"Merchants","leader":"Merchant",
                   "type":"guild","rgb":"4,5,6","capital":-1,"banner":["white"]}
                  """,
                  GuildData.class);
      Guild detached = new Guild(stored, null);
      assertNull(detached.getFaction());
      assertEquals(0.0, BranchIncomePreview.taxFraction(detached));
      var home = fixture.saved("home", "Leader");
      detached.setHost(home);
      assertEquals(0.1, BranchIncomePreview.taxFraction(detached));
      home.getTaxHandler().setTaxRate(TaxTarget.GUILD_ID, "merchants", 25);
      assertEquals(0.25, BranchIncomePreview.taxFraction(detached));
    }
  }

  @Test
  void aPreparedBranchPreviewUsesFrozenProvinceDataAndNeverMutatesItsGuildOrBank() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      Double previousCarry = Cache.tradeCarry.get(Terrain.PLAINS);
      try {
        fixture.provincesEnabled(true);
        Cache.tradeCarry.put(Terrain.PLAINS, 0.8);
        Province capital = new Province(91001, "PLAINS", 40, 0, 0);
        Province neighbor = new Province(91002, "PLAINS", 40, 16, 0);
        capital.addNeighbour(neighbor.getId());
        neighbor.addNeighbour(capital.getId());
        capital.setProsperity(12.5);
        fixture.provinceData.put(capital.getId(), capital);
        fixture.provinceData.put(neighbor.getId(), neighbor);
        var data = fixture.data("home", "Leader");
        data.provinces.add(capital.getId());
        data.provinces.add(neighbor.getId());
        var home = fixture.saved(data);
        // A real guild's trade is suppressed if its realm has no capacity to support it.
        var realm = home.getOrCreateMainGuild();
        realm.getBranches().put(0, new Branch(realm.getBranches().get(0), 10));
        var guild = fixture.guild(home, "fields", "Farmer");
        guild.setCapital(capital.getId());
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", "Fields");
        config.set("group", 1);
        config.set(
            "modifiers",
            List.of(
                "PRODUCTION 2 2", "TRADE_POWER 8 0", "TRADE_CARRY 1.2 0", "TRADE_UPKEEP 0.05 0"));
        Branch branch = new Branch(new Branch("fields", config), 2);
        guild.getBranches().clear();
        guild.getBranches().put(1, branch);
        guild.getBank().deposit(250.0);
        TradeBreakdown original = guild.getTradeBreakdown();
        original.setIncome(80);
        ProvinceManager live = new ProvinceManager();
        live.start(Map.of(capital.getId(), capital, neighbor.getId(), neighbor));
        BranchIncomePreview.Prepared prepared = BranchIncomePreview.prepare(live);
        Map<GuildModifier, Double> current = BranchIncomePreview.modifiers(guild);
        Map<GuildModifier, Double> raised = BranchIncomePreview.adjust(current, branch, 2, 1);
        Map<GuildModifier, Double> lowered = BranchIncomePreview.adjust(current, branch, 2, -10);
        assertEquals(8.0, raised.get(GuildModifier.PRODUCTION));
        assertEquals(2.0, lowered.get(GuildModifier.PRODUCTION));
        BranchIncomePreview.Estimate raisedEstimate =
            BranchIncomePreview.estimate(prepared, guild, current, raised);
        double afterTax = BranchIncomePreview.estimate(prepared, guild, branch, 1);
        assertTrue(afterTax > 0);
        assertEquals(raisedEstimate.own(), afterTax);
        double effectiveTax = home.getTaxRate(TaxTarget.GUILDS, guild.getId(), true) / 100.0;
        assertTrue(effectiveTax > 0 && effectiveTax < 1);
        assertTrue(raisedEstimate.realm() > afterTax, "the treasury keeps the guild tax");
        assertTrue(BranchIncomePreview.estimate(live, guild, branch, -1) < 0);
        neighbor.setProsperity(1000);
        live.start(Map.of());
        assertEquals(0.0, BranchIncomePreview.estimate(live, guild, branch, 1));
        assertEquals(afterTax, BranchIncomePreview.estimate(prepared, guild, branch, 1));
        assertEquals(2, branch.getLevel());
        assertEquals(250.0, guild.getBank().getWealth());
        assertSame(original, guild.getTradeBreakdown());
        assertEquals(80.0, original.getIncome());
        assertTrue(capital.getAllData().isEmpty());
        assertTrue(neighbor.getAllData().isEmpty());
        assertEquals(6.0, GuildModifierOverride.resolve(guild, GuildModifier.PRODUCTION));
      } finally {
        if (previousCarry == null) Cache.tradeCarry.remove(Terrain.PLAINS);
        else Cache.tradeCarry.put(Terrain.PLAINS, previousCarry);
      }
    }
  }

  @Test
  void cancelledDonationsNotifyOnlyTheCurrentOnlineLeader() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var home = fixture.saved("home", "Leader");
      var leader = fixture.player("Merchant");
      var guild = fixture.guild(home, "merchant", "Merchant");
      assertEquals(10.05, GuildDonation.fee(100.5));
      assertEquals(110.55, GuildDonation.cost(100.5));
      GuildDonation.notifyCancelled(guild, 100.5);
      verify(leader)
          .sendMessage(
              argThat(
                  (String message) ->
                      message.contains("Daily donation") && message.contains("cancelled")));
      clearInvocations(leader);
      GuildDonation.notifyCancelled(null, 100);
      GuildDonation.notifyCancelled(guild, 0);
      fixture.online.remove("Merchant");
      GuildDonation.notifyCancelled(guild, 100);
      guild.setLeader(null);
      GuildDonation.notifyCancelled(guild, 100);
      guild.setLeader(" ");
      GuildDonation.notifyCancelled(guild, 100);
      guild.setLeader("Merchant");
      when(Bukkit.getServer()).thenReturn(null);
      GuildDonation.notifyCancelled(guild, 100);
      verify(leader, never()).sendMessage(anyString());
      for (double invalid : List.of(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
        assertEquals(0.0, GuildDonation.fee(invalid));
        assertEquals(0.0, GuildDonation.cost(invalid));
      }
    }
  }
}
