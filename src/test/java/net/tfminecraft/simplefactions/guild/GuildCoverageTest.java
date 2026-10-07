package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.GuildBranchData;
import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.MercenaryCompanyData;
import net.tfminecraft.simplefactions.database.StabilityModifierData;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.installation.InstallationTransferService;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.rest.BannerFetcher;
import net.tfminecraft.simplefactions.settlement.handler.CapitalResult;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.testsupport.TestRegistryAccess;
import net.tfminecraft.simplefactions.utils.RandomRGB;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class GuildCoverageTest {
  private GuildFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new GuildFixture();
  }

  @AfterEach
  void close() {
    fixture.close();
  }

  @Test
  void realmGuildLeadershipTracksTheCurrentFactionLeader() {
    Guild guild = fixture.guild("realm", 0);
    assertTrue(guild.isLeader("Leader"));
    when(fixture.host.getLeader()).thenReturn("Successor");
    assertEquals("Successor", guild.getLeader());
    assertFalse(guild.isLeader("Leader"));
    assertTrue(guild.isLeader("successor"));
    assertTrue(guild.isLeader(fixture.ui.player("Successor")));
  }

  @Test
  void anExpansionExponentOfOneMeansConstantCostRatherThanZeroInvestedWealth() {
    Guild guild = fixture.guild("guild", 3);
    Cache.branchUpgradeExponent = 1.0;
    assertEquals(100.0, guild.getExpansionCost());
    assertEquals(300.0, guild.getTotalExpansionSpent());
    assertEquals(240.0, guild.getTotalPossibleRefund());
  }

  @Test
  void liquidationRefundsTheLevelBeingRemovedAndRecalculatesWealthAfterTheLevelDrops() {
    Guild guild = fixture.guild("guild", 2);
    assertEquals(160.0, guild.getRefund());
    guild.liquidateRandom();
    assertEquals(1, guild.getSize());
    assertEquals(160.0, guild.getBank().getWealth());
    assertEquals(260.0, guild.getWealth());
  }

  @Test
  void liquidationWorksWhenConfiguredBranchGroupsHaveGaps() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("trade.group", 4);
    config.set("trade.allowed-types", List.of("guild"));
    Branch template = new Branch("trade", config.getConfigurationSection("trade"));
    BranchLoader.map.clear();
    BranchLoader.map.put("trade", template);
    GuildData data = fixture.data("guild", 2);
    data.branches.getFirst().id = "trade";
    Guild guild = new Guild(data, fixture.host);
    guild.liquidateRandom();
    assertEquals(1, guild.getSize());
    assertEquals(160.0, guild.getBank().getWealth());
  }

  @Test
  void savedGuildDataSurvivesJsonRoundTripAndRestoresFinancialSettings() {
    GuildData data = fixture.data("guild", 2);
    data.members = null;
    data.dividendEligible = null;
    data.dividendPercent = 12.5;
    data.donation = 25.0;
    data.favoured = true;
    data.repressed = true;
    data.stance = "OPPOSE";
    data.creditScore = 70;
    data.vehicleFeeIncome = 3.0;
    data.casinoProfit = 5.0;
    data.citizenTaxes = Map.of("Alice", 4.0);
    data.usedLoanOffers = Map.of("offer", System.currentTimeMillis() + 60_000);
    data.wealthModifiers.add("Inheritance(7.5)");
    data.depositsToday = Map.of("Alice", 8.0);
    data.pillageHits.add(null);
    StabilityModifierData hit = new StabilityModifierData();
    hit.name = "Raid";
    hit.modifier = 10;
    hit.decay = 2;
    data.pillageHits.add(hit);
    StabilityModifierData invalid = new StabilityModifierData();
    data.pillageHits.add(invalid);
    Guild loaded =
        new Guild(
            JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(data), GuildData.class), fixture.host);
    assertEquals("merchants", loaded.getId());
    assertEquals("Merchants", loaded.getName());
    assertEquals("Leader", loaded.getLeader());
    assertEquals(List.of("Leader"), loaded.getMembers());
    assertTrue(loaded.getDividendEligibleSnapshot().isEmpty());
    assertEquals(12.5, loaded.getDividendPercent());
    assertEquals(25, loaded.getDonationAmount());
    assertTrue(loaded.isFavoured());
    assertTrue(loaded.isRepressed());
    assertEquals(Stance.OPPOSE, loaded.getStance(fixture.host));
    assertEquals(70, loaded.getLoanHandler().getCreditScore());
    assertTrue(loaded.getLoanHandler().getUsedOffers().containsKey("offer"));
    assertEquals(3, loaded.getLedger().getVehicleFeeIncome());
    assertEquals(5, loaded.getLedger().getCasinoProfit());
    assertEquals(Map.of("Alice", 4.0), loaded.getLedger().getCitizenTaxesCopy());
    assertEquals(Map.of("Alice", 8.0), loaded.getLedger().getHistory().getDepositsToday());
    assertEquals(1, loaded.getPillageHits().size());
    assertEquals("Raid", loaded.getPillageHits().getFirst().getName());
    assertEquals(2, loaded.getBranch(0).getLevel());
    assertEquals(7.5, loaded.getWealthModifiers().getFirst().getAmount());
  }

  @Test
  void membershipsAndInvitationsAreCaseInsensitiveAndLeaveTheMainGuildOnce() {
    Guild guild = fixture.guild("guild", 0);
    guild.invite(null);
    guild.invite("BOB");
    guild.invite("bob");
    assertEquals(List.of("BOB"), guild.getInvites());
    assertTrue(guild.isInvited("Bob"));
    assertFalse(guild.consumeInvite("missing"));
    when(fixture.main.isMember("Bob")).thenReturn(true);
    guild.addMember("Bob");
    verify(fixture.main).kick("Bob");
    assertTrue(guild.getInvites().isEmpty());
    guild.addMember("BOB");
    assertEquals(List.of("Leader", "Alice", "Bob"), guild.getMembers());
    assertTrue(guild.isMember(fixture.ui.player("bOb")));
    guild.kick("bOB");
    assertFalse(guild.isMember("Bob"));
    guild.kick("missing");
    assertEquals(2, guild.getMembers().size());
    assertNull(Guild.findIgnoreCase(null, "Bob"));
    assertNull(Guild.findIgnoreCase(List.of("Bob"), null));
    assertEquals("Alice", Guild.findIgnoreCase(java.util.Arrays.asList(null, "Alice"), "ALICE"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void creatingGuildsInitializesDefinitionsAndWaitsForBannerFetch(boolean base) {
    fixture.upgrade("roads", false, 1);
    List<java.util.function.Consumer<List<String>>> callbacks = new ArrayList<>();
    try (MockedStatic<RandomRGB> rgb = mockStatic(RandomRGB.class);
        MockedStatic<BannerFetcher> banners = mockStatic(BannerFetcher.class)) {
      rgb.when(() -> RandomRGB.similarButDistinct("1,2,3")).thenReturn("1,2,4", "1,2,5");
      rgb.when(RandomRGB::random).thenReturn("1,2,4", "1,2,5");
      rgb.when(() -> RandomRGB.isFree("1,2,5")).thenReturn(true);
      List<String> placeholder = new ArrayList<>(List.of("white"));
      banners.when(BannerFetcher::placeholder).thenReturn(placeholder);
      banners.when(() -> BannerFetcher.isPlaceholder(placeholder)).thenReturn(true);
      banners
          .when(() -> BannerFetcher.fetch(anyString(), any()))
          .thenAnswer(
              call -> {
                callbacks.add(call.getArgument(1));
                return null;
              });
      Guild guild =
          base ? new Guild(fixture.host) : new Guild("New_Guild", fixture.player, fixture.host, 7);
      assertEquals(base ? "realm" : "New_Guild", guild.getId());
      assertEquals(List.of("Leader"), guild.getMembers());
      assertEquals(List.of("Leader"), guild.getDividendEligibleSnapshot());
      assertNotNull(guild.getBank());
      assertNotNull(guild.getBranch(0));
      assertNotNull(guild.getUpgrade("roads"));
      assertEquals(0.0, guild.getWealth());
      if (base) {
        assertTrue(guild.isBase());
        assertTrue(callbacks.isEmpty());
      } else {
        assertFalse(guild.isBase());
        verify(fixture.main).kick("Leader");
        assertEquals("1,2,5", guild.getRGB());
        callbacks.getFirst().accept(null);
        assertEquals(List.of("white"), guild.getBannerPatterns());
        callbacks.getFirst().accept(new ArrayList<>(List.of("red")));
        assertEquals(List.of("red"), guild.getBannerPatterns());
        callbacks.getFirst().accept(List.of("blue"));
        assertEquals(List.of("red"), guild.getBannerPatterns());
      }
    }
  }

  @Test
  void wealthModifiersReplaceAccumulateAndRemoveWithoutDuplicatingSources() {
    Guild guild = fixture.guild("guild", 2);
    guild.getBank().setWealth(50.0);
    guild.addWealthModifier(new Modifier("Gift", 3.0, false));
    guild.addWealthModifier(new Modifier("GIFT", 4.0, false));
    guild.addPersistentWealthModifier(new Modifier("Treasure", 10.0, true));
    guild.addPersistentWealthModifier(new Modifier("treasure", 5.0, true));
    guild.addPersistentWealthModifier(new Modifier("zero", 0.0, true));
    guild.updateWealth();
    assertEquals(369.0, guild.getWealth());
    assertEquals(4, guild.getWealthModifiers().size());
    guild.addPersistentWealthModifier(new Modifier("TREASURE", -15.0, true));
    guild.updateWealth();
    assertEquals(354.0, guild.getWealth());
    assertEquals(3, guild.getWealthModifiers().size());
    guild.setWealthModifiers(new ArrayList<>());
    guild.setWealth(12.0);
    guild.setBank(null);
    guild.updateWealth();
    assertEquals(300.0, guild.getWealth(), "Removing the bank preserves the value of guild expansions");
    assertEquals(2, guild.getWealthModifiers().size());
    assertEquals(0.0, guild.getWealthModifiers().stream().filter(m -> m.getType().equals("Bank")).findFirst().orElseThrow().getAmount());
  }

  @Test
  void expansionAndRelocationCostsFollowLevelAndProvinceOwnership() {
    Guild guild = fixture.guild("guild", 0);
    assertEquals(0, guild.getTotalExpansionSpent());
    assertEquals(0, guild.getTotalPossibleRefund());
    assertFalse(guild.canLiquidate());
    guild.liquidateRandom();
    assertEquals(0, guild.getSize());
    guild.getBranch(0).levelUp();
    guild.getBranch(0).levelUp();
    assertEquals(300, guild.getTotalExpansionSpent());
    assertEquals(240, guild.getTotalPossibleRefund());
    assertEquals(400, guild.getExpansionCost());
    assertEquals(-1, guild.getRelocationCost(99));
    Province province = mock(Province.class);
    when(fixture.provinces.get(7)).thenReturn(province);
    assertEquals(100, guild.getRelocationCost(7));
    for (int n = 0; n < 4; n++) guild.getBranch(0).levelUp();
    assertEquals(945, guild.getRelocationCost(7));
    when(province.getOwner()).thenReturn(fixture.host);
    assertEquals(315, guild.getRelocationCost(7));
    Faction other = mock(Faction.class);
    when(other.getId()).thenReturn("other");
    when(province.getOwner()).thenReturn(other);
    assertEquals(945, guild.getRelocationCost(7));
    guild.setBank(null);
    guild.liquidateRandom();
    assertEquals(6, guild.getSize());
  }

  @Test
  void upgradesRestoreFilterQueueFreezeAndCompleteWithAnInventoryNotification() {
    Upgrade template = fixture.upgrade("roads", true, 1);
    GuildData data = fixture.data("guild", 0);
    GuildBranchData saved = new GuildBranchData();
    saved.id = "roads";
    saved.level = 2;
    data.upgrades.add(saved);
    Guild guild = new Guild(data, fixture.host);
    Upgrade upgrade = guild.getUpgrade("roads");
    assertEquals(2, upgrade.getLevel());
    assertTrue(guild.hasUpgrades());
    assertEquals(List.of(upgrade), guild.getUpgrades());
    assertEquals(6, guild.getUpgradesUpkeep());
    guild.enqueueUpgrade(upgrade);
    guild.tick(true);
    assertEquals(1, guild.getUpgradeQueue().getFirst().getTimeLeft());
    guild.tick();
    assertEquals(3, upgrade.getLevel());
    assertTrue(guild.getUpgradeQueue().isEmpty());
    verify(fixture.inventory.getUpdater())
        .inventorySound(
            "minecraft:block.note_block.chime",
            net.tfminecraft.simplefactions.enums.SFGUI.UPGRADE_VIEW);
    guild.addQueuedUpgrade(upgrade, 2);
    guild.tick(false);
    assertEquals(1, guild.getUpgradeQueue().getFirst().getTimeLeft());
    guild.enqueueUpgrade(upgrade);
    guild.enqueueUpgrade(upgrade);
    guild.enqueueUpgrade(upgrade);
    guild.addQueuedUpgrade(upgrade, 4);
    assertEquals(3, guild.getUpgradeQueue().size());
    assertFalse(guild.cancelUpgradeQueue(-1));
    assertFalse(guild.cancelUpgradeQueue(3));
    assertTrue(guild.cancelUpgradeQueue(1));
    assertEquals(2, guild.getUpgradeQueue().size());
    guild.setUpgradeQueue(new ArrayList<>());
    guild.tick();
    assertTrue(guild.getUpgradeQueue().isEmpty());
    MercenaryCompany company = mock(MercenaryCompany.class);
    guild.setCompany(company);
    guild.tick();
    verify(company).tick();
    assertSame(company, guild.getCompany());
    assertFalse(guild.hasCompany());
    assertFalse(guild.isFoundingCompany());
    when(company.isFormed()).thenReturn(true);
    when(company.isForming()).thenReturn(true);
    assertTrue(guild.hasCompany());
    assertTrue(guild.isFoundingCompany());
    guild.setCompany(null);
    assertFalse(guild.hasCompany());
    assertFalse(guild.isFoundingCompany());
  }

  @Test
  void capitalChangesNotifyOnlyDeparturesAndTheBaseDelegatesToItsFaction() {
    Guild guild = fixture.guild("guild", 0);
    guild.setCapital(8);
    assertEquals(8, guild.getCapital());
    assertTrue(guild.hasCapital());
    verify(fixture.host.getSettlementHandler()).onGuildDepartedCapital(7);
    guild.setCapital(8);
    verify(fixture.host.getSettlementHandler(), times(1)).onGuildDepartedCapital(anyInt());
    guild.setCapital(-1, false);
    assertFalse(guild.hasCapital());
    verify(fixture.provinces, times(3)).recalculateForSingleGuild(guild, true);
    Guild base = fixture.guild("realm", 0);
    base.setCapital(-1, false);
    verify(fixture.host).setCapital(-1, true, false);
    when(fixture.host.getCapital()).thenReturn(3);
    when(fixture.host.hasCapital()).thenReturn(true);
    assertEquals(3, base.getCapital());
    assertTrue(base.hasCapital());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void relocationChangesMembershipAndPreservesSettlementsOnlyWhenRequested(boolean keep) {
    Guild guild = fixture.guild("guild", 0);
    Faction other = mock(Faction.class, RETURNS_DEEP_STUBS);
    when(other.getId()).thenReturn("other");
    if (keep) guild.relocateKeepingSettlements(other, 8);
    else guild.relocate(other, 8);
    if (keep) verify(fixture.host.getGuildHandler()).removeGuild("merchants", false, false);
    else verify(fixture.host.getGuildHandler()).removeGuild("merchants");
    verify(other.getGuildHandler()).addGuild(guild);
    assertSame(other, guild.getFaction());
    assertEquals(8, guild.getCapital());
    if (keep) verify(other.getSettlementHandler(), never()).onGuildDepartedCapital(anyInt());
    else verify(other.getSettlementHandler()).onGuildDepartedCapital(7);
  }

  @Test
  void relocationWithAnActorReportsTheSettlementResultAndBaseGuildsStayPut() {
    Faction other = mock(Faction.class, RETURNS_DEEP_STUBS);
    when(other.getId()).thenReturn("other");
    Guild guild = fixture.guild("guild", 0);
    when(other.getSettlementHandler().onGuildRelocateTo(fixture.player, guild, 8, "New City"))
        .thenReturn(CapitalResult.ok("Relocated"));
    guild.relocate(other, 8, fixture.player, "New City");
    verify(fixture.player).sendMessage("Relocated");
    guild.relocate(other, 9);
    assertEquals(8, guild.getCapital());
    guild.relocate(fixture.host, -1);
    assertEquals(-1, guild.getCapital());
    Guild base = fixture.guild("realm", 0);
    base.relocate(other, 8);
    assertSame(fixture.host, base.getFaction());
    assertFalse(base.relocateWithinFaction(fixture.player, 8, null).isSuccess());
    when(fixture.host.getSettlementHandler().onGuildRelocateTo(fixture.player, guild, 9, null))
        .thenReturn(CapitalResult.ok("Within realm"));
    assertTrue(guild.relocateWithinFaction(fixture.player, 9, null).isSuccess());
    verify(fixture.player).sendMessage("Within realm");
  }

  @Test
  void nonBaseGuildStabilityCombinesSharesAndHonoursStanceAndInfluence() {
    Guild guild = fixture.guild("guild", 0);
    guild.setWealth(50.0);
    when(fixture.host.getWealth()).thenReturn(100.0);
    when(fixture.host.getVassalWealth()).thenReturn(0.0);
    when(fixture.host.getGuildHandler().getTotalTradePower()).thenReturn(100.0);
    guild.getTradeBreakdown().setTradePower(50);
    assertEquals(60, guild.getStabilityEffect());
    assertEquals(30, guild.getRepressFavourCost());
    when(fixture.host.getModifier(
            FactionModifiers.STABILITY_INFLUENCE, "merchants", Scope.DOMESTIC_GUILDS, null))
        .thenReturn(0.5);
    assertEquals(10.5, guild.getStabilityModifier(fixture.host));
    guild.switchStance();
    assertEquals(Stance.SUPPORT, guild.getStance(fixture.host));
    assertEquals(30, guild.getStabilityModifier(fixture.host));
    guild.switchStance();
    assertEquals(-30, guild.getStabilityModifier(fixture.host));
    guild.switchStance();
    guild.setStance(null);
    assertEquals(Stance.NEUTRAL, guild.getStance(fixture.host));
    when(fixture.host.getMembers()).thenReturn(List.of());
    assertEquals(0, guild.getMemberPercentage());
    when(fixture.host.getWealth()).thenReturn(0.0);
    when(fixture.host.getGuildHandler().getTotalTradePower()).thenReturn(0.0);
    assertEquals(0, guild.getStabilityEffect());
  }

  @Test
  void baseGuildStabilitySupportsItsHostAndScalesItsInfluenceAbroad() {
    Guild guild = fixture.guild("realm", 0);
    assertEquals(Stance.SUPPORT, guild.getStance(fixture.host));
    assertEquals(30, guild.getStabilityModifier(fixture.host));
    Faction other = mock(Faction.class);
    when(other.getId()).thenReturn("other");
    when(other.getModifier(FactionModifiers.STABILITY_INFLUENCE, "merchants", Scope.VASSALS, null))
        .thenReturn(2.0);
    when(fixture.host.getGovernment().getStability()).thenReturn(50.0);
    guild.setStance(Stance.SUPPORT);
    assertEquals(30, guild.getStabilityModifier(other));
  }

  @Test
  void incomePreviewChangesNeitherLiveTradeNorFavoursAndRepression() {
    Guild guild = fixture.guild("guild", 0);
    TradeBreakdown live = new TradeBreakdown();
    live.setIncome(25);
    guild.setTradeBreakdown(live);
    IncomePreviewContext previous = IncomePreviewContext.current();
    try {
      IncomePreviewContext.open(IncomePreviewContext.favour(guild, true));
      assertTrue(guild.isFavoured());
      assertFalse(guild.isRepressed());
      assertNotSame(live, guild.getTradeBreakdown());
      guild.getTradeBreakdown().setIncome(100);
    } finally {
      if (previous == null) IncomePreviewContext.clear();
      else IncomePreviewContext.open(previous);
    }
    assertSame(live, guild.getTradeBreakdown());
    assertEquals(25, live.getIncome());
    assertFalse(guild.isFavoured());
    guild.setRepressed(true);
    guild.setFavoured(true);
    assertTrue(guild.isFavoured());
    assertTrue(guild.isRepressed());
    try (MockedStatic<InactivityService> inactivity = mockStatic(InactivityService.class)) {
      inactivity.when(() -> InactivityService.outputFactor(guild)).thenReturn(0.5);
      assertEquals(2.5, guild.getModifier(GuildModifier.TRADE_POWER));
      assertEquals(0, guild.getModifier(GuildModifier.TRADE_UPKEEP));
    }
  }

  /**
   * Real saved guilds and definitions; only server and faction integration boundaries are mocked.
   */
  @Test
  void removingTheLastExpansionDropsItsHistoricalWealthContribution() {
    Guild guild = fixture.guild("guild", 1);
    guild.updateWealth();
    assertEquals(100, guild.getWealth());
    guild.liquidateRandom();
    assertEquals(0, guild.getSize());
    assertEquals(80, guild.getBank().getWealth());
    assertEquals(80, guild.getWealth());
  }

  @Test
  void financialLimitsAndDividendEligibilityUseFiniteValuesAndCopies() {
    GuildData data = fixture.data("guild", 0);
    data.dividendEligible = null;
    Guild guild = new Guild(data, fixture.host);
    assertEquals(List.of("Leader", "Alice"), guild.getDividendEligibleSnapshot());
    for (double value :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0}) {
      assertEquals(0, guild.setDividendPercent(value));
      assertEquals(0, guild.setDonationAmount(value));
    }
    assertEquals(100, guild.setDividendPercent(120));
    assertEquals(33.3, guild.setDividendPercent(33.3));
    assertEquals(12.35, guild.setDonationAmount(12.345));
    assertEquals(0, fixture.guild("realm", 0).setDonationAmount(20));
    boolean previous = Cache.dividendRequirePreviousTickMembership;
    try {
      Cache.dividendRequirePreviousTickMembership = true;
      guild.addMember("Bob");
      guild.kick("Alice");
      guild.getMembers().add(null);
      assertEquals(List.of("Leader"), guild.getDividendEligibleMembers());
      guild.getDividendEligibleSnapshot().clear();
      assertEquals(2, guild.getDividendEligibleSnapshot().size());
      guild.refreshDividendEligibility();
      assertEquals(List.of("Leader", "Bob"), guild.getDividendEligibleMembers());
      Cache.dividendRequirePreviousTickMembership = false;
      List<String> eligible = guild.getDividendEligibleMembers();
      assertEquals(Arrays.asList("Leader", "Bob", null), eligible);
      eligible.clear();
      assertEquals(3, guild.getMembers().size());
    } finally {
      Cache.dividendRequirePreviousTickMembership = previous;
    }
    assertFalse(guild.isBankrupt());
    Bank bank = mock(Bank.class);
    guild.setBank(bank);
    when(bank.getWealth()).thenReturn(null);
    assertFalse(guild.isBankrupt());
    when(bank.getWealth()).thenReturn(-1.0);
    assertTrue(guild.isBankrupt());
    guild.setBank(null);
    assertFalse(guild.isBankrupt());
  }

  @Test
  void bannerPatternsRoundTripAndIgnoreUnknownOrMalformedPatternEntries() {
    assertNotNull(PatternType.CROSS);
    PatternType custom =
        TestRegistryAccess.registerPattern(new NamespacedKey("tfmc", "guild_crest"));
    Guild guild = fixture.guild("guild", 0);
    guild.setBannerPatterns(
        new ArrayList<>(
            List.of(
                "red",
                "blue.cross",
                "white.guild_crest",
                "bad",
                "invalid.cross",
                "blue.not_known")));
    assertEquals(Material.RED_BANNER, guild.getBanner().getType());
    BannerMeta loaded = (BannerMeta) guild.getBanner().getItemMeta();
    assertEquals(2, loaded.getPatterns().size());
    assertEquals(new Pattern(DyeColor.BLUE, PatternType.CROSS), loaded.getPatterns().getFirst());
    ItemStack picked = new ItemStack(Material.BLUE_BANNER);
    BannerMeta selected = (BannerMeta) picked.getItemMeta();
    selected.addPattern(new Pattern(DyeColor.RED, custom));
    selected.addPattern(new Pattern(DyeColor.WHITE, mock(PatternType.class)));
    picked.setItemMeta(selected);
    guild.setBanner(picked);
    assertEquals(List.of("BLUE.BASE", "RED.GUILD_CREST"), guild.getBannerPatterns());
    assertEquals(1, ((BannerMeta) guild.getBanner().getItemMeta()).getPatterns().size());
    Guild base = fixture.guild("realm", 0);
    base.setBanner(picked);
    assertSame(fixture.host.getBanner(), base.getBanner());
    assertSame(fixture.host.getBannerPatterns(), base.getBannerPatterns());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void dummyMembershipCommandsChooseAvailableNamesAndMoveLeadership(boolean base) {
    Guild guild = fixture.guild(base ? "realm" : "guild", 0);
    guild.getMembers().clear();
    guild.getMembers().addAll(List.of("Leader", "existing_dummy"));
    fixture.factions.when(() -> FactionManager.getGuildByMember("dummy_1")).thenReturn(guild);
    guild.dummify(fixture.player);
    assertEquals(List.of("dummy_2", "existing_dummy"), guild.getMembers());
    verify(fixture.player).sendMessage("§adummy_2 replaced Leader");
    verify(fixture.player).sendMessage("§adummy_2 became leader");
    if (base) verify(fixture.host).setLeader("dummy_2");
    else assertEquals("dummy_2", guild.getLeader());
    fixture.factions.when(() -> FactionManager.getGuildByMember("dummy_2")).thenReturn(guild);
    guild.dummyLeader(fixture.player);
    assertTrue(guild.getMembers().contains("dummy_3"));
    if (base) verify(fixture.host).setLeader("dummy_3");
    else assertEquals("dummy_3", guild.getLeader());
    verify(fixture.player).sendMessage("§adummy_3 became leader");
    guild.setLeader(null);
    if (!base) assertFalse(guild.isLeader("Leader"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Original Guild"})
  void convertingTheRealmGuildKeepsItsOwnIdentityAndReplacesIncompatibleDefinitions(String name) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("realm_trade.allowed-types", List.of("realm"));
    config.set("realm_trade.group", 1);
    config.set("guild_trade.allowed-types", List.of("guild"));
    config.set("guild_trade.group", 1);
    for (String id : List.of("realm_trade", "guild_trade"))
      BranchLoader.map.put(id, new Branch(id, config.getConfigurationSection(id)));
    config.set("realm_road.allowed-types", List.of("realm"));
    UpgradeLoader.map.put(
        "realm_road", new Upgrade("realm_road", config.getConfigurationSection("realm_road")));
    Guild guild = fixture.guild("realm", 1);
    guild.setName(name);
    guild.getBranch(1).levelUp();
    guild.getUpgrade("realm_road").setLevel(3);
    when(fixture.host.getCapital()).thenReturn(42);
    when(fixture.host.getLeaderCharacter()).thenReturn("Duke Rowan");
    when(fixture.host.getLeaderCharacterOf()).thenReturn("Leader");
    guild.convert(GuildLoader.getDefaultType());
    assertFalse(guild.isBase());
    assertEquals(name.isEmpty() ? "The Realm" : name, guild.getOwnName());
    assertEquals(42, guild.getCapital());
    assertEquals("Leader", guild.getLeader());
    assertEquals("1,2,3", guild.getRGB());
    assertEquals("Duke Rowan", guild.getLeaderCharacter());
    assertEquals("Leader", guild.getLeaderCharacterOf());
    assertEquals("guild_trade", guild.getBranch(1).getId());
    assertEquals(1, guild.getBranch(1).getLevel());
    assertEquals(0, guild.getUpgrade("realm_road").getLevel());
    assertTrue(guild.getUpgrades().isEmpty());
    guild.convert(GuildLoader.getDefaultType());
    assertEquals(42, guild.getCapital());
    guild.setRGB("5,6,7");
    guild.rememberLeaderCharacter("Captain", "Successor");
    assertEquals("5,6,7", guild.getRGB());
    assertEquals("Captain", guild.getLeaderCharacter());
    assertEquals("Successor", guild.getLeaderCharacterOf());
    assertNull(guild.getBranch("missing"));
  }

  @Test
  void elevationAndEvictionValidateGovernmentMovementAndCapitalOwnership() {
    Guild guild = fixture.guild("guild", 2);
    Guild base = fixture.guild("realm", 0);
    assertFalse(base.canBeEvicted(fixture.player));
    assertFalse(base.canBeElevated(fixture.player));
    assertFalse(guild.canBeElevated(fixture.player));
    assertNull(guild.elevate(false));
    verify(fixture.player).sendMessage("§cHost faction cannot have vassals");
    when(fixture.host.hasFactionRule(Rules.CAN_HAVE_VASSALS)).thenReturn(true);
    when(fixture.host.getCapital()).thenReturn(7);
    assertFalse(guild.canBeElevated(fixture.player));
    when(fixture.host.getCapital()).thenReturn(8);
    Guild sibling = fixture.guild("guild", 0);
    GuildData otherData = fixture.data("guild", 0);
    otherData.id = "other";
    sibling = new Guild(otherData, fixture.host);
    when(fixture.host.getGuildHandler().getGuilds()).thenReturn(List.of(base, guild, sibling));
    assertFalse(guild.canBeElevated(fixture.player));
    sibling.setCapital(9);
    assertTrue(guild.canBeElevated(fixture.player));
    when(fixture.host.getGovernment().getMovementByMember("Leader"))
        .thenReturn(mock(Movement.class));
    assertFalse(guild.canBeEvicted(fixture.player));
    verify(fixture.player).sendMessage("§cGuild is currently involved in a movement");
    when(fixture.host.getGovernment().getMovementByMember("Leader")).thenReturn(null);
    assertTrue(guild.canBeEvicted(fixture.player));
    assertEquals(
        Cache.elevationBase + Math.pow(2 * Cache.elevationSizeMultiplier, Cache.elevationExponent),
        guild.getElevationCost());
    assertEquals(guild.getElevationCost() * Cache.evictionMultiplier, guild.getEvictionCost());
    assertEquals(Cache.elevationBase, fixture.guild("guild", 0).getElevationCost());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void landlessConversionClearsPoliticalStatusAndOptionallyCreatesTheSubjectRelation(
      boolean subjugate) {
    Guild guild = fixture.guild("guild", 1);
    guild.setFavoured(true);
    guild.setRepressed(true);
    RelationType relation = mock(RelationType.class);
    try (MockedConstruction<Faction> created =
            mockConstruction(
                Faction.class,
                (f, context) -> {
                  // Faction(Guild) rebinds the guild before returning; preserve that constructor
                  // effect.
                  assertSame(guild, context.arguments().getFirst());
                  guild.setHost(f);
                });
        MockedStatic<RelationLoader> definitions = mockStatic(RelationLoader.class);
        MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
      definitions.when(RelationLoader::getElevationTarget).thenReturn(relation);
      Faction result = guild.toLandless(subjugate);
      assertSame(created.constructed().getFirst(), result);
      assertSame(result, guild.getFaction());
      assertFalse(guild.hasCapital());
      assertFalse(guild.isFavoured());
      assertFalse(guild.isRepressed());
      verify(fixture.host.getGuildHandler()).removeGuild("merchants");
      fixture.factions.verify(() -> FactionManager.addFaction(result));
      relations.verify(
          () -> RelationManager.setRelation(null, relation, result, fixture.host, false),
          times(subjugate ? 1 : 0));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void elevationTransfersTheCapitalAndItsInstallationsWithOptionalSubjugation(boolean subjugate) {
    Guild guild = fixture.guild("guild", 1);
    guild.setFavoured(true);
    guild.setRepressed(true);
    when(fixture.host.hasFactionRule(Rules.CAN_HAVE_VASSALS)).thenReturn(true);
    RelationType relation = mock(RelationType.class);
    try (MockedConstruction<Faction> created =
            mockConstruction(
                Faction.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (f, context) -> {
                  // Faction(Guild) rebinds the guild before returning; preserve that constructor
                  // effect.
                  assertSame(guild, context.arguments().getFirst());
                  guild.setHost(f);
                });
        MockedStatic<RelationLoader> definitions = mockStatic(RelationLoader.class);
        MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
        MockedStatic<InstallationTransferService> installations =
            mockStatic(InstallationTransferService.class)) {
      definitions.when(RelationLoader::getElevationTarget).thenReturn(relation);
      Faction result = guild.elevate(subjugate);
      assertSame(created.constructed().getFirst(), result);
      assertSame(result, guild.getFaction());
      assertFalse(guild.isFavoured());
      assertFalse(guild.isRepressed());
      verify(result.getProvinceHandler()).addProvince(7);
      verify(fixture.host.getProvinceHandler()).removeProvince(7, false);
      installations.verify(() -> InstallationTransferService.transfer(fixture.host, result, 7));
      fixture.factions.verify(() -> FactionManager.addFaction(result));
      relations.verify(
          () -> RelationManager.setRelation(null, relation, result, fixture.host, false),
          times(subjugate ? 1 : 0));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Merchant Republic"})
  void rebellionPreservesSettlementsAndReturnsTheOriginalGuildName(String name) {
    Guild guild = fixture.guild("guild", 1);
    guild.setName(name);
    guild.setFavoured(true);
    guild.setRepressed(true);
    try (MockedConstruction<Faction> created = mockConstruction(Faction.class)) {
      Guild.RebelNation rebels = guild.rebel();
      assertSame(created.constructed().getFirst(), rebels.faction());
      assertEquals(name.isEmpty() ? "#a3a184merchants" : name, rebels.ownName());
      assertFalse(guild.isFavoured());
      assertFalse(guild.isRepressed());
      verify(fixture.host.getGuildHandler()).removeGuild("merchants", false, false);
      fixture.factions.verify(() -> FactionManager.addFaction(rebels.faction()));
    }
    guild.setHost(null);
    assertNull(guild.rebel());
    guild.setHost(mock(Faction.class));
    assertNull(guild.rebel());
  }

  @Test
  void savedGuildRestoresCompanyDefaultsAndTicksPillageDecay() {
    GuildData data = fixture.data("guild", 0);
    data.company = new MercenaryCompanyData();
    data.dividendEligible = null;
    data.pillageHits = null;
    data.upgrades = null;
    data.favoured = null;
    data.repressed = null;
    try (MockedStatic<MercenaryCompany> templates = mockStatic(MercenaryCompany.class);
        MockedConstruction<MercenaryCompany> companies = mockConstruction(MercenaryCompany.class)) {
      Guild guild = new Guild(data, fixture.host);
      assertSame(companies.constructed().getFirst(), guild.getCompany());
      assertFalse(guild.isFavoured());
      assertFalse(guild.isRepressed());
      assertEquals(50, guild.getLoanHandler().getCreditScore());
      guild.getPillageHits().add(new StabilityModifier("Pillage", -3, 2));
      guild.tickPillageHits();
      assertEquals(-1, guild.getPillageHits().getFirst().getModifier());
      guild.tickPillageHits();
      assertTrue(guild.getPillageHits().isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void newlyCreatedGuildsRetainConfiguredBranchesAfterAGroupGap(boolean base) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("trade.group", 4);
    config.set("trade.allowed-types", List.of("guild", "realm"));
    BranchLoader.map.clear();
    BranchLoader.map.put("trade", new Branch("trade", config.getConfigurationSection("trade")));
    try (MockedStatic<RandomRGB> rgb = mockStatic(RandomRGB.class);
        MockedStatic<BannerFetcher> banners = mockStatic(BannerFetcher.class)) {
      rgb.when(RandomRGB::random).thenReturn("1,2,4");
      rgb.when(() -> RandomRGB.similarButDistinct("1,2,3")).thenReturn("1,2,4");
      rgb.when(() -> RandomRGB.isFree("1,2,4")).thenReturn(true);
      banners.when(BannerFetcher::placeholder).thenReturn(new ArrayList<>(List.of("white")));
      Guild created =
          base ? new Guild(fixture.host) : new Guild("New_Guild", fixture.player, fixture.host, 7);
      assertNull(created.getBranch(0));
      assertNotNull(created.getBranch(4));
      assertEquals("trade", created.getBranch(4).getId());
      assertEquals(0, created.getSize());
    }
  }

  @Test
  void configuredUpgradeEffectsCombineWithBranchLevelsAndOnlyBenefitsScaleWithInactivity() {
    Upgrade template = fixture.upgrade("roads", false, 2);
    template
        .getModifiers()
        .put(
            GuildModifier.TRADE_POWER,
            new net.tfminecraft.simplefactions.guild.branch.BranchModifier(2, 3));
    template
        .getModifiers()
        .put(
            GuildModifier.TRADE_UPKEEP,
            new net.tfminecraft.simplefactions.guild.branch.BranchModifier(2, 1));
    Guild guild = fixture.guild("guild", 1);
    guild.getUpgrade("roads").setLevel(2);
    assertEquals(Map.of(0, guild.getBranch(0)), guild.getBranches());
    try (MockedStatic<InactivityService> inactivity = mockStatic(InactivityService.class)) {
      inactivity.when(() -> InactivityService.outputFactor(guild)).thenReturn(.5);
      assertEquals(7.5, guild.getModifier(GuildModifier.TRADE_POWER));
      assertEquals(4, guild.getModifier(GuildModifier.TRADE_UPKEEP));
    }
  }

  public static final class GuildFixture implements AutoCloseable {
    public final GuiTestFixture ui;
    public final Player player;
    public final Faction host;
    public final Guild main;
    public final ProvinceManager provinces;
    public final InventoryManager inventory;
    public final MockedStatic<FactionManager> factions;
    private final Map<String, GuildType> oldTypes = GuildLoader.map;
    private final Map<String, Branch> oldBranches = BranchLoader.map;
    private final Map<String, Upgrade> oldUpgrades = UpgradeLoader.map;
    private final String oldWorld = Cache.worldName;
    private final double oldCost = Cache.branchUpgradeCost;
    private final double oldExponent = Cache.branchUpgradeExponent;

    public GuildFixture() {
      Cache.worldName = "world";
      Cache.branchUpgradeCost = 100;
      Cache.branchUpgradeExponent = 2;
      ui = new GuiTestFixture();
      player = ui.player("Leader");
      when(Bukkit.getLogger()).thenReturn(Logger.getLogger("GuildCoverageTest"));
      when(ui.world.getChunkAt(0, 0)).thenReturn(mock(Chunk.class));
      provinces = mock(ProvinceManager.class);
      when(ui.plugin.getProvinceManager()).thenReturn(provinces);
      host = mock(Faction.class, RETURNS_DEEP_STUBS);
      when(host.getId()).thenReturn("realm");
      when(host.getName()).thenReturn("The Realm");
      when(host.getLeader()).thenReturn("Leader");
      when(host.getRGB()).thenReturn("1,2,3");
      when(host.getMembers()).thenReturn(new ArrayList<>(List.of("Leader", "Alice")));
      when(host.getBannerPatterns()).thenReturn(new ArrayList<>(List.of("white")));
      ItemStack banner = new ItemStack(Material.WHITE_BANNER);
      when(host.getBanner()).thenReturn(banner);
      main = mock(Guild.class);
      when(host.getOrCreateMainGuild()).thenReturn(main);
      inventory = mock(InventoryManager.class, RETURNS_DEEP_STUBS);
      factions = mockStatic(FactionManager.class);
      factions.when(FactionManager::getInv).thenReturn(inventory);
      GuildLoader.map = new LinkedHashMap<>();
      BranchLoader.map = new LinkedHashMap<>();
      UpgradeLoader.map = new LinkedHashMap<>();
      YamlConfiguration types = new YamlConfiguration();
      types.set("realm.name", "Realm");
      types.set("realm.base", true);
      types.set("guild.name", "Guild");
      types.set("guild.default", true);
      for (String id : List.of("realm", "guild"))
        GuildLoader.map.put(id, new GuildType(id, types.getConfigurationSection(id)));
      YamlConfiguration branch = new YamlConfiguration();
      branch.set("commerce.name", "Commerce");
      branch.set("commerce.allowed-types", List.of("guild", "realm"));
      branch.set("commerce.group", 0);
      branch.set("commerce.icon", "emerald.0");
      branch.set("commerce.description", List.of("Trade offices"));
      branch.set("commerce.modifiers", List.of("TRADE_POWER 5 2"));
      BranchLoader.map.put(
          "commerce", new Branch("commerce", branch.getConfigurationSection("commerce")));
    }

    public GuildData data(String type, int branchLevel) {
      GuildData data = new GuildData();
      data.id = "merchants";
      data.name = "Merchants";
      data.leader = "Leader";
      data.type = type;
      data.capital = 7;
      data.rgb = "3,4,5";
      data.banner = new ArrayList<>(List.of("white"));
      data.members = new ArrayList<>(List.of("Leader", "Alice"));
      GuildBranchData branch = new GuildBranchData();
      branch.id = "commerce";
      branch.level = (double) branchLevel;
      data.branches.add(branch);
      return data;
    }

    public Guild guild(String type, int branchLevel) {
      return new Guild(data(type, branchLevel), host);
    }

    public Upgrade upgrade(String id, boolean war, int time) {
      YamlConfiguration config = new YamlConfiguration();
      config.set(id + ".name", "Roads");
      config.set(id + ".allowed-types", List.of("guild", "realm"));
      config.set(id + ".icon", "stone.0");
      config.set(id + ".upkeep", 3);
      config.set(id + ".war-related", war);
      config.set(id + ".expansion-time", time);
      Upgrade upgrade = new Upgrade(id, config.getConfigurationSection(id));
      UpgradeLoader.map.put(id, upgrade);
      return upgrade;
    }

    @Override
    public void close() {
      try {
        factions.close();
      } finally {
        try {
          ui.close();
        } finally {
          GuildLoader.map = oldTypes;
          BranchLoader.map = oldBranches;
          UpgradeLoader.map = oldUpgrades;
          Cache.worldName = oldWorld;
          Cache.branchUpgradeCost = oldCost;
          Cache.branchUpgradeExponent = oldExponent;
        }
      }
    }
  }
}
