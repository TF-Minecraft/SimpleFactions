package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.MenuItemType;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.EspionageAccess;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.espionage.IntelligenceReport;
import net.tfminecraft.simplefactions.espionage.RosterLore;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.government.stability.GovernmentIncompatibility;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildCoverageTest.GuildFixture;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.branch.BranchModifier;
import net.tfminecraft.simplefactions.guild.income.BranchIncomePreview;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.guild.income.DividendBreakdown;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.guild.income.LedgerHistory;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.guild.upgrade.UpgradeExpansion;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.RelocationPrompt;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.rest.BannerFetcher;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.util.LegacyModelData;
import net.tfminecraft.simplefactions.utils.FactionRanker;
import net.tfminecraft.simplefactions.utils.HomeSettlementNames;
import net.tfminecraft.simplefactions.war.freeze.PreparationFreeze;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class GuildMenusCoverageTest {
  private GuildFixture fixture;
  private Guild guild;
  private InventoryManager manager;
  private GuildView view;
  private MockedStatic<EspionageService> intelligence;
  private MockedStatic<EspionageAccess> access;
  private MockedStatic<ReportedMenus> reported;
  private MockedStatic<BranchIncomePreviewService> previewTasks;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();
  private HashMap<Player, RankType> oldRanking;
  private HashMap<Player, Integer> oldPages;
  private GuildCreator creator;
  private MapSystem map;
  private FactionRanker ranker;
  private boolean oldProvinces;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> scope = mockStatic(type);
    scopes.add(scope);
    return scope;
  }

  @BeforeEach
  void setUp() {
    fixture = new GuildFixture();
    guild = fixture.guild("guild", 1);
    fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    manager = new InventoryManager();
    view = new GuildView(manager);
    manager.guildView = view;
    view.setProvinceManager(fixture.provinces);
    intelligence = mockStatic(EspionageService.class);
    intelligence
        .when(() -> EspionageService.canViewExact(fixture.player, fixture.host))
        .thenReturn(true);
    intelligence
        .when(() -> EspionageService.canViewCovert(fixture.player, fixture.host))
        .thenReturn(true);
    access = mockStatic(EspionageAccess.class);
    oldRanking = GuildView.currentRanking;
    oldPages = GuildView.currentPage;
    GuildView.currentRanking = new HashMap<>();
    GuildView.currentPage = new HashMap<>();
    oldProvinces = Cache.provincesEnabled;
    Cache.provincesEnabled = true;
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator items = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(items);
    when(items.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    when(items.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(api);
    scoped(IconGetter.class)
        .when(() -> IconGetter.getIconOrDefault(anyString(), any()))
        .thenAnswer(call -> new ItemStack((Material) call.getArgument(1)));
    reported = scoped(ReportedMenus.class);
    reported
        .when(() -> ReportedMenus.guildLeader(any(), any()))
        .thenAnswer(call -> ((Guild) call.getArgument(1)).getLeader());
    scoped(HomeSettlementNames.class)
        .when(() -> HomeSettlementNames.of(any(Guild.class)))
        .thenReturn("Market Town");
    scoped(InactivityService.class);
    scoped(RosterLore.class)
        .when(() -> RosterLore.guild(any(), any()))
        .thenReturn(List.of("Leader", "Alice"));
    scoped(GovernmentIncompatibility.class)
        .when(() -> GovernmentIncompatibility.factor(any()))
        .thenReturn(1.0);
    map = mock(MapSystem.class);
    fixture.factions.when(FactionManager::getMap).thenReturn(map);
    creator = view.creator;
    ranker = mock(FactionRanker.class);
    creator.r = ranker;
    scoped(BranchIncomePreview.class);
    previewTasks = scoped(BranchIncomePreviewService.class);
  }

  @AfterEach
  void close() {
    try {
      for (int i = scopes.size() - 1; i >= 0; i--) scopes.get(i).close();
      access.close();
      intelligence.close();
    } finally {
      GuildView.currentRanking = oldRanking;
      GuildView.currentPage = oldPages;
      Cache.provincesEnabled = oldProvinces;
      fixture.close();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aBranchRemovedSinceTheMenuOpenedDoesNotMutateTheBank(boolean upgrade) {
    Inventory inventory = open();
    ItemStack button = new ItemStack(Material.ARROW);
    ItemMeta meta = button.getItemMeta();
    meta.getPersistentDataContainer().set(Keys.BRANCH_ID, PersistentDataType.STRING, "removed");
    meta.getPersistentDataContainer().set(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN, upgrade);
    button.setItemMeta(meta);
    inventory.setItem(20, button);
    Bank bank = mock(Bank.class);
    when(bank.getWealth()).thenReturn(1000.0);
    guild.setBank(bank);
    InventoryClickEvent click = fixture.ui.click(fixture.player, 20);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    verify(bank, never()).withdraw(anyDouble());
    verify(bank, never()).deposit(anyDouble());
  }

  @Test
  void aBannerRequestCannotApplyAfterTheRequesterLosesLeadership() {
    Inventory inventory = open();
    inventory.setItem(GuildView.BANNER_RANDOM_SLOT, new ItemStack(Material.WHITE_BANNER));
    List<Consumer<List<String>>> pending = new ArrayList<>();
    try (MockedStatic<BannerFetcher> banners = mockStatic(BannerFetcher.class)) {
      banners
          .when(() -> BannerFetcher.fetch(eq("guild:merchants"), any()))
          .thenAnswer(
              call -> {
                pending.add(call.getArgument(1));
                return null;
              });
      manager.clickButton(fixture.ui.click(fixture.player, GuildView.BANNER_RANDOM_SLOT));
      assertEquals(1, pending.size());
      guild.setLeader("Successor");
      pending.getFirst().accept(List.of("red"));
      assertEquals(List.of("white"), guild.getBannerPatterns());
    }
  }

  @Test
  void evictionChargesTheOriginalHostAfterTheGuildBecomesLandless() {
    guild.setLeader("GuildLeader");
    when(fixture.host.isLeader("Leader")).thenReturn(true);
    when(fixture.host.getGovernment().getMovementByMember("GuildLeader")).thenReturn(null);
    when(fixture.host.getGovernment().getPower()).thenReturn(1000.0);
    Inventory inventory = open();
    inventory.setItem(34, new ItemStack(Material.PAPER));
    double cost = guild.getEvictionCost();
    try (MockedConstruction<Faction> created =
        mockConstruction(
            Faction.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS), (f, context) -> {})) {
      manager.clickButton(fixture.ui.click(fixture.player, 34));
      assertEquals(1, created.constructed().size());
      assertSame(created.constructed().getFirst(), guild.getFaction());
      verify(fixture.host.getGovernment()).spendPower(cost);
      verify(created.constructed().getFirst().getGovernment(), never()).spendPower(anyDouble());
      verify(fixture.player).closeInventory();
    }
  }

  @Test
  void branchDowngradeImmediatelyRemovesTheOldExpansionValueFromWealth() {
    guild.getBank().setWealth(1000.0);
    guild.updateWealth();
    assertEquals(1100, guild.getWealth());
    view = spy(view);
    manager.guildView = view;
    doNothing().when(view).guildView(any(), any(), any(Inventory.class));
    Inventory inventory = open();
    inventory.setItem(
        38, creator.createBranchDowngradeItem(fixture.player, guild, guild.getBranch(0)));
    manager.clickButton(fixture.ui.click(fixture.player, 38));
    assertEquals(0, guild.getSize());
    assertEquals(1080, guild.getBank().getWealth());
    assertEquals(1080, guild.getWealth());
    verify(fixture.host).updatePrestige();
    verify(fixture.provinces).recalculateForSingleGuild(guild, true);
  }

  @Test
  void branchItemsDescribeRealDefinitionsCostsRefundsAndDeferredIncomeEstimates() {
    Branch branch = guild.getBranch(0);
    branch.getModifiers().put(GuildModifier.TRADE_UPKEEP, new BranchModifier(3, 1));
    ItemStack item = creator.createBranchItem(fixture.player, guild, branch);
    assertEquals(Material.EMERALD, item.getType());
    assertEquals("commerce", payload(item, Keys.BRANCH_ID));
    assertLore(item, "Commerce", false);
    assertLore(item, "Trade offices", true);
    assertLore(item, "Trade Power", true);
    assertLore(item, "Upkeep", true);
    guild.getTradeBreakdown().setIncome(50);
    guild.getTradeBreakdown().setUpkeep(4);
    guild.getTradeBreakdown().setTariffs(6);
    ItemStack up = creator.createBranchUpgradeItem(fixture.player, guild, branch);
    ItemStack down = creator.createBranchDowngradeItem(fixture.player, guild, branch);
    assertEquals(true, flag(up));
    assertEquals(false, flag(down));
    assertEquals("commerce", payload(up, Keys.BRANCH_ID));
    assertLore(up, "200.0d", true);
    assertLore(up, "40.0", true);
    assertLore(up, "Calculating", true);
    assertLore(down, "80.0d", true);
    assertLore(down, "Stats will decrease", true);
    creator.writeUpgradeEstimate(up, guild, branch, new BranchIncomePreview.Estimate(7.125, 7.125));
    assertLore(up, "+7.13d/day", true);
    assertLore(up, "Realm-wide", false);
    assertEquals("commerce", payload(up, Keys.BRANCH_ID));
    creator.writeUpgradeEstimate(up, guild, branch, new BranchIncomePreview.Estimate(7.125, 9.5));
    assertLore(up, "+7.13d/day", true);
    assertLore(up, "Realm-wide Change", true);
    assertLore(up, "+9.50d/day", true);
    creator.writeDowngradeEstimate(down, guild, branch, new BranchIncomePreview.Estimate(-6.0, -4.0));
    assertLore(down, "-6.00d/day", true);
    assertLore(down, "-4.00d/day", true);
    assertEquals(false, flag(down));
    creator.writeUpgradeEstimate(up, guild, branch, BranchIncomePreview.Estimate.UNAVAILABLE);
    assertLore(up, "unavailable", true);
    guild.setCapital(-1);
    assertLore(creator.createBranchItem(fixture.player, guild, branch), "No capital!", true);
    assertLore(creator.createBranchUpgradeItem(fixture.player, guild, branch), "No capital!", true);
    assertLore(
        creator.createBranchDowngradeItem(fixture.player, guild, branch), "No capital!", true);
    branch.levelDown();
    assertLore(
        creator.createBranchDowngradeItem(fixture.player, guild, branch),
        "cannot be downgraded",
        true);
  }

  @Test
  void upgradeItemsPreserveActionPayloadsAndExposeQueueAndUpkeepDetails() {
    Upgrade upgrade = new Upgrade(fixture.upgrade("roads", true, 60), 2);
    upgrade.getModifiers().put(GuildModifier.TRADE_POWER, new BranchModifier(2, 3));
    upgrade.getModifiers().put(GuildModifier.TRADE_UPKEEP, new BranchModifier(1, 2));
    ItemStack item = creator.createUpgradeItem(fixture.player, guild, upgrade);
    assertEquals("roads", payload(item, Keys.STRING_KEY));
    assertLore(item, "6.00d/day", true);
    assertLore(item, "Trade Power", true);
    ItemStack up = creator.createUpgradeUpgradeItem(fixture.player, guild, upgrade);
    ItemStack down = creator.createUpgradeDowngradeItem(fixture.player, guild, upgrade);
    assertEquals("roads", payload(up, Keys.STRING_KEY));
    assertEquals(true, flag(up));
    assertEquals(false, flag(down));
    assertLore(up, "Time:", true);
    assertLore(up, "+3.00d/day", true);
    assertLore(down, "-3.00d/day", true);
    upgrade.setLevel(0);
    assertLore(
        creator.createUpgradeDowngradeItem(fixture.player, guild, upgrade),
        "cannot be downgraded",
        true);
    try (MockedStatic<PreparationFreeze> freeze = mockStatic(PreparationFreeze.class)) {
      freeze.when(() -> PreparationFreeze.frozenLore(any())).thenReturn("§eFrozen until tomorrow");
      ItemStack running =
          creator.createUpgradeQueueItem(new UpgradeExpansion(upgrade, 30), 0, guild);
      assertLore(running, "Time Left", true);
      assertLore(running, "Frozen until tomorrow", true);
      assertEquals(
          QueueCancelPayload.guildUpgrade("merchants", 0), payload(running, Keys.QUEUE_CANCEL));
      ItemStack queued =
          creator.createUpgradeQueueItem(new UpgradeExpansion(upgrade, 30), 1, guild);
      assertLore(queued, "Queued", true);
      assertEquals(
          QueueCancelPayload.guildUpgrade("merchants", 1), payload(queued, Keys.QUEUE_CANCEL));
    }
    Guild display = displayGuild(guild);
    when(display.getUpgrades()).thenReturn(List.of(upgrade));
    assertLore(creator.createUpgradesItem(fixture.player, display), "Roads", true);
    guild.setLeader("Other");
    assertLore(creator.createUpgradesItem(fixture.player, guild), "Click to view", false);
  }

  @Test
  void listItemsShowExactOwnedValuesAndLabelForeignEstimatesAndUnknowns() {
    Guild display = displayGuild(guild);
    display.setWealth(300.0);
    display.getTradeBreakdown().setTradePower(12);
    when(display.getLedger().getNetIncome()).thenReturn(-5.0);
    when(ranker.visibleGuildValue(fixture.player, display, RankType.INCOME)).thenReturn(-5.0);
    when(ranker.getVisibleGuildRank(fixture.player, display, RankType.WEALTH)).thenReturn(2);
    ItemStack own = creator.createPublicListItem(fixture.player, display);
    assertEquals("merchants", payload(own, new NamespacedKey(fixture.ui.plugin, "id")));
    assertLore(own, "300.0d", true);
    assertLore(own, "Market Town", true);
    assertLore(own, "-5.0d/day", true);
    assertLore(own, "estimated", false);
    intelligence
        .when(() -> EspionageService.canViewExact(fixture.player, fixture.host))
        .thenReturn(false);
    IntelligenceReport report = mock(IntelligenceReport.class);
    intelligence
        .when(() -> EspionageService.report(fixture.player, fixture.host))
        .thenReturn(report);
    when(report.display("Guild:merchants:Members")).thenReturn("3–5");
    when(report.display("Guild:merchants:Trade power")).thenReturn("Unknown");
    when(report.display("Guild:merchants:Income")).thenReturn("10–20");
    when(report.display("Guild:merchants:Wealth")).thenReturn("100–500");
    ItemStack foreign = creator.createListItem(fixture.player, display);
    assertLore(foreign, "3–5", true);
    assertLore(foreign, "Unknown", true);
    assertLore(foreign, "10–20d/day", true);
    assertLore(foreign, "estimated 2", true);
    intelligence.when(() -> EspionageService.report(fixture.player, fixture.host)).thenReturn(null);
    when(ranker.getVisibleGuildRank(fixture.player, display, RankType.WEALTH)).thenReturn(null);
    display.setCapital(-1);
    ItemStack unknown = creator.createListItem(fixture.player, display);
    assertLore(unknown, "Unknown", true);
    assertLore(unknown, "Trade Power", false);
    ItemStack host = creator.createHostFactionItem(guild);
    assertEquals("realm", payload(host, new NamespacedKey(fixture.ui.plugin, "id")));
    assertEquals("The Realm", ChatColor.stripColor(host.getItemMeta().getDisplayName()));
  }

  @Test
  void menuIdentityWealthAndBannerActionsUseConcreteItemsAndKeepSourceBannersUnchanged() {
    guild.addWealthModifier(new Modifier("Treasure", 20.0, true));
    guild.setWealth(20.0);
    assertEquals(
        Material.WHITE_BANNER,
        creator.createMenuItem(fixture.player, guild, MenuItemType.BANNER).getType());
    assertLore(
        creator.createMenuItem(fixture.player, guild, MenuItemType.BANNER),
        "Part of: The Realm",
        true);
    assertLore(
        creator.createMenuItem(fixture.player, fixture.guild("realm", 0), MenuItemType.BANNER),
        "Guild of The Realm",
        true);
    ItemStack get = creator.createMenuItem(fixture.player, guild, MenuItemType.BANNER_GET);
    assertEquals(Material.CHEST, get.getType());
    assertLore(get, "Click to get a banner", true);
    assertLore(
        creator.createMenuItem(fixture.player, guild, MenuItemType.BANNER_RANDOM),
        "randomise",
        true);
    ItemStack head = creator.createMenuItem(fixture.player, guild, MenuItemType.LEADER);
    assertEquals(Material.PLAYER_HEAD, head.getType());
    assertTrue(ChatColor.stripColor(head.getItemMeta().getDisplayName()).contains("Leader"));
    assertLore(
        creator.createMenuItem(fixture.player, guild, MenuItemType.WEALTH),
        "20.0d from Treasure",
        true);
    ItemStack members = creator.createMenuItem(fixture.player, guild, MenuItemType.MEMBERS);
    assertTrue(ChatColor.stripColor(members.getItemMeta().getDisplayName()).contains("2/"));
    assertEquals("", guild.getBanner().getItemMeta().getDisplayName());
  }

  @Test
  void publicLedgersKeepMilitaryAndVehicleLinesCovert() {
    Guild display = displayGuild(guild);
    Ledger ledger = display.getLedger();
    when(ledger.getIncome(Cashflow.TRADE)).thenReturn(40.0);
    when(ledger.getIncome(Cashflow.VEHICLE_FEES)).thenReturn(12.0);
    when(ledger.getIncome(Cashflow.MILITARY_UPKEEP)).thenReturn(-30.0);
    when(ledger.getIncome(Cashflow.MERCENARY_PAYMENTS)).thenReturn(-7.0);
    intelligence
        .when(() -> EspionageService.canViewCovert(fixture.player, fixture.host))
        .thenReturn(false);
    access.when(() -> EspionageAccess.covert(any())).thenCallRealMethod();
    ItemStack summary = creator.createLedgerItem(fixture.player, display);
    assertLore(summary, "+40.00d", true);
    assertLore(summary, "+12.00d", false);
    assertLore(summary, "-30.00d", false);
    assertLore(summary, "-7.00d", false);
    assertLore(summary, "Vehicle Taxes & Fees: Unknown", true);
    assertLore(summary, "Military, Vehicles & Mercenaries: Unknown", true);
    assertLore(summary, "No expenses", false);
  }

  @Test
  void dividendDonationAndLedgerSummariesSeparateIncomeExpensesAndAffordability() {
    Guild display = displayGuild(guild);
    Ledger ledger = display.getLedger();
    assertLore(creator.createLedgerItem(fixture.player, display), "No income sources", true);
    assertLore(creator.createLedgerItem(fixture.player, display), "No expenses", true);
    when(ledger.getIncome(Cashflow.TRADE)).thenReturn(40.0);
    when(ledger.getIncome(Cashflow.GUILD_PAYMENTS)).thenReturn(-5.0);
    when(ledger.getNetIncome()).thenReturn(35.0);
    ItemStack summary = creator.createLedgerItem(fixture.player, display);
    assertLore(summary, "+40.00d", true);
    assertLore(summary, "-5.00d", true);
    assertLore(summary, "+35.00d", true);
    when(ledger.getIncome(Cashflow.TRADE)).thenReturn(0.0);
    display.getPillageHits().add(new StabilityModifier("Pillage", -100, 1));
    when(ledger.getNetIncome()).thenReturn(-5.0);
    assertLore(creator.createLedgerItem(fixture.player, display), "Pillaged", true);
    when(ledger.getDividendBreakdown()).thenReturn(new DividendBreakdown(100, 20, 2, 18, 2, 9));
    display.setDividendPercent(20);
    ItemStack dividends = creator.createDividendItem(fixture.player, display);
    assertLore(dividends, "20.00%", true);
    assertLore(dividends, "9.00d", true);
    assertLore(dividends, "limited by the guild bank", true);
    display.getBank().setWealth(100.0);
    display.setLeader("Other");
    assertLore(creator.createDividendItem(fixture.player, display), "Only the guild leader", true);
    assertLore(
        creator.createDividendItem(fixture.player, fixture.guild("realm", 0)),
        "cannot pay dividends",
        true);
    assertLore(creator.createDonationItem(fixture.player, display), "Nothing is sent", true);
    display.setDonationAmount(100);
    ItemStack donation = creator.createDonationItem(fixture.player, display);
    assertLore(donation, "100.00d", true);
    assertLore(donation, "10.00d", true);
    assertLore(donation, "110.00d", true);
    assertLore(donation, "Only the guild leader", true);
    display.setLeader("Leader");
    assertLore(creator.createDonationItem(fixture.player, display), "Click to set", true);
    assertLore(creator.createLoansItem(fixture.player, display), "Loans Given: 0", true);
    assertLore(creator.createLoansItem(fixture.player, display), "Click to View Details", true);
  }

  @Test
  void tradeBreakdownShowsPenaltiesTopContributorsAndOnlyFiveKnownGuilds() {
    Guild display = displayGuild(guild);
    TradeBreakdown trade = display.getTradeBreakdown();
    trade.setIncome(100);
    trade.setUpkeep(7);
    trade.setTariffs(3);
    trade.setTradePower(20);
    display.getPillageHits().add(new StabilityModifier("Pillage", -25, 1));
    when(GovernmentIncompatibility.factor(display)).thenReturn(.75);
    for (int n = 0; n < 12; n++) {
      Faction f = mock(Faction.class);
      when(f.getName()).thenReturn("Contributor " + n);
      trade.registerIncome(f, 12 - n);
      trade.registerTariffs(f, n == 0 ? 2 : 0);
    }
    List<Guild> ranked = new ArrayList<>();
    Guild hidden = mock(Guild.class);
    when(ranker.visibleGuildValue(fixture.player, hidden, RankType.INCOME)).thenReturn(null);
    ranked.add(hidden);
    for (int n = 0; n < 6; n++) {
      Guild entry = mock(Guild.class, RETURNS_DEEP_STUBS);
      when(entry.getName()).thenReturn("Visible " + n);
      when(entry.getId()).thenReturn("visible" + n);
      when(entry.getFaction()).thenReturn(fixture.host);
      when(entry.getLedger().getNetIncome()).thenReturn(10.0 + n);
      when(ranker.visibleGuildValue(fixture.player, entry, RankType.INCOME)).thenReturn(10.0 + n);
      ranked.add(entry);
    }
    when(ranker.getVisibleRankedGuildList(fixture.player, RankType.INCOME)).thenReturn(ranked);
    ItemStack item = creator.createMenuItem(fixture.player, display, MenuItemType.TRADE_BREAKDOWN);
    assertLore(item, "Strained Government", true);
    assertLore(item, "75.0%", true);
    assertLore(item, "Pillage", true);
    assertLore(item, "Contributor 9", true);
    assertLore(item, "Contributor 10", false);
    assertLore(item, "2.0", true);
    assertLore(item, "Visible 4", true);
    assertLore(item, "Visible 5", false);
    Faction foreign = mock(Faction.class);
    when(ranked.get(1).getFaction()).thenReturn(foreign);
    IntelligenceReport report = mock(IntelligenceReport.class);
    when(report.display("Guild:visible0:Income")).thenReturn("8–12");
    intelligence.when(() -> EspionageService.report(fixture.player, foreign)).thenReturn(report);
    assertLore(
        creator.createMenuItem(fixture.player, display, MenuItemType.TRADE_BREAKDOWN),
        "8–12d/day",
        true);
    Guild base = fixture.guild("realm", 0);
    assertLore(
        creator.createMenuItem(fixture.player, base, MenuItemType.TRADE_BREAKDOWN),
        "State Output",
        true);
    display.setHost(null);
    assertLore(
        creator.createMenuItem(fixture.player, display, MenuItemType.TRADE_BREAKDOWN),
        "Strained Government",
        false);
  }

  @Test
  void iconOverridesRetainMenuContentAndInactivityDoesNotHideUpgradeLevels() {
    ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
    ItemMeta meta = icon.getItemMeta();
    LegacyModelData.set(meta, 123);
    icon.setItemMeta(meta);
    when(IconGetter.hasIcon("LEADER")).thenReturn(true);
    when(IconGetter.getIcon("LEADER")).thenReturn(icon);
    ItemStack leader = creator.createMenuItem(fixture.player, guild, MenuItemType.LEADER);
    assertEquals(123, LegacyModelData.get(leader.getItemMeta()));
    assertTrue(ChatColor.stripColor(leader.getItemMeta().getDisplayName()).contains("Leader"));
    when(ReportedMenus.guildLeader(fixture.player, guild)).thenReturn("§7Unknown");
    assertNull(
        ((org.bukkit.inventory.meta.SkullMeta)
                creator.createMenuItem(fixture.player, guild, MenuItemType.LEADER).getItemMeta())
            .getOwningPlayer());
    assertEquals(
        Material.DIRT,
        creator.createMenuItem(fixture.player, guild, MenuItemType.PRESTIGE).getType());
    when(InactivityService.guildPercent(guild)).thenReturn(40);
    assertLore(creator.createUpgradesItem(fixture.player, guild), "Inactive output -40%", true);
  }

  @Test
  void ledgerSourcesSortCapAndSeparateTodayFromHistoricalTotals() {
    Guild display = displayGuild(guild);
    Ledger ledger = display.getLedger();
    LedgerHistory history = ledger.getHistory();
    Map<String, Double> taxes = new LinkedHashMap<>();
    for (int n = 0; n < 7; n++) taxes.put("Citizen" + n, (double) n);
    taxes.put("Negative", -2.0);
    when(ledger.getCitizenTaxesCopy()).thenReturn(taxes);
    history.closeDay(Map.of(LedgerHistory.Source.CITIZENS, Map.of("Yesterday", 15.0)));
    history.addDeposit("Leader", 25);
    history.addDeposit("Alice", 10);
    ItemStack citizens = creator.createLedgerCitizensItem(display);
    assertEquals(Material.PLAYER_HEAD, citizens.getType());
    assertLore(citizens, "Citizen6", true);
    assertLore(citizens, "Citizen1", false);
    assertLore(citizens, "Yesterday", true);
    assertLore(citizens, "Negative", false);
    when(ledger.getCitizenTaxesCopy()).thenReturn(Map.of("Zero", 0.0));
    assertLore(creator.createLedgerCitizensItem(display), "No citizen taxes", true);
    Guild paying = displayGuild(fixture.guild("guild", 0));
    when(paying.getLedger().getIncome(Cashflow.GUILD_PAYMENTS)).thenReturn(-12.0);
    Guild realm = fixture.guild("realm", 0);
    when(fixture.host.getGuildHandler().getGuilds()).thenReturn(List.of(realm, paying, display));
    assertLore(creator.createLedgerGuildsItem(display), "Merchants", true);
    assertLore(creator.createLedgerGuildsItem(display), "+12.00d", true);
    Faction subject = mock(Faction.class);
    when(subject.getName()).thenReturn("Northern Realm");
    when(subject.getOrCreateMainGuild()).thenReturn(paying);
    try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
      relations.when(() -> RelationManager.getSubjects(fixture.host)).thenReturn(List.of(subject));
      assertLore(creator.createLedgerVassalsItem(display), "No vassal income", true);
      when(paying.getLedger().getIncome(Cashflow.OVERLORD_TAX)).thenReturn(-9.0);
      assertLore(creator.createLedgerVassalsItem(display), "Northern Realm", true);
      assertLore(creator.createLedgerVassalsItem(display), "+9.00d", true);
    }
    ItemStack deposits = creator.createLedgerDepositsItem(display);
    assertLore(deposits, "not part of daily income", true);
    assertLore(deposits, "+35.00d", true);
    assertLore(deposits, "Leader", true);
    Faction payer = mock(Faction.class);
    when(payer.getId()).thenReturn("payer");
    when(payer.getName()).thenReturn("Tributary");
    when(payer.getOrCreateMainGuild()).thenReturn(paying);
    when(paying.getLedger().getInternalTaxableIncome()).thenReturn(100.0);
    Faction stranger = mock(Faction.class);
    when(stranger.getId()).thenReturn("stranger");
    when(payer.getModifiers())
        .thenReturn(
            List.of(
                new FactionModifier(FactionModifiers.TRIBUTE, 10),
                new FactionModifier(fixture.host, FactionModifiers.STABILITY_INFLUENCE, 10),
                new FactionModifier(stranger, FactionModifiers.TRIBUTE, 10),
                new FactionModifier(fixture.host, FactionModifiers.TRIBUTE, 15)));
    Faction noGuild = mock(Faction.class);
    when(noGuild.getId()).thenReturn("empty");
    fixture
        .factions
        .when(FactionManager::getCopy)
        .thenReturn(Arrays.asList(null, fixture.host, noGuild, payer));
    assertLore(creator.createLedgerTributesItem(display), "Tributary", true);
    assertLore(creator.createLedgerTributesItem(display), "+15.00d", true);
    when(paying.getLedger().getInternalTaxableIncome()).thenReturn(0.0);
    assertLore(creator.createLedgerTributesItem(display), "No tributes", true);
    Guild hostless = mock(Guild.class);
    fixture
        .factions
        .when(FactionManager::getAllGuilds)
        .thenReturn(Arrays.asList(null, hostless, paying, display));
    paying.getTradeBreakdown().registerTariffs(fixture.host, 8);
    assertLore(creator.createLedgerTariffsItem(display), "+8.00d", true);
    assertLore(creator.createLedgerTariffsItem(display), "The Realm", true);
  }

  @Test
  void relocationElevationAndEvictionItemsExplainTheirAvailableAndUnavailableActions() {
    Province province = mock(Province.class);
    when(fixture.provinces.get(9)).thenReturn(province);
    Faction foreign = mock(Faction.class);
    when(foreign.getId()).thenReturn("foreign");
    when(foreign.getName()).thenReturn("Other Realm");
    RelationType relation = mock(RelationType.class);
    when(relation.getName()).thenReturn("vassal");
    try (MockedStatic<RestServer> rest = mockStatic(RestServer.class);
        MockedStatic<RelationLoader> relations = mockStatic(RelationLoader.class)) {
      rest.when(() -> RestServer.getProvince(fixture.player)).thenReturn(9);
      relations.when(RelationLoader::getElevationTarget).thenReturn(relation);
      ItemStack cross = creator.createRelocateItem(fixture.player, foreign, guild);
      assertLore(cross, "changes the faction", true);
      assertLore(cross, "100.0d", true);
      assertLore(
          creator.createRelocateItem(fixture.player, fixture.host, guild),
          "outside The Realm",
          true);
      when(fixture.host.hasProvince(9)).thenReturn(true);
      assertLore(
          creator.createRelocateItem(fixture.player, fixture.host, guild),
          "within The Realm",
          true);
      assertLore(creator.createElevationItem(fixture.player, guild), "Unavailable", true);
      when(fixture.host.hasFactionRule(Rules.CAN_HAVE_VASSALS)).thenReturn(true);
      assertLore(creator.createElevationItem(fixture.player, guild), "Click to Elevate", true);
      when(fixture.host.getGovernment().getMovementByMember("Leader")).thenReturn(null);
      assertLore(creator.createEvictionItem(fixture.player, guild), "Click to Evict", true);
      assertLore(
          creator.createEvictionItem(fixture.player, fixture.guild("realm", 0)),
          "Unavailable",
          true);
    }
  }

  @Test
  void guildListPagesReserveControlsCycleRankingAndOpenTheSelectedGuild() {
    List<Guild> listed = new ArrayList<>();
    for (int n = 0; n < 50; n++) {
      Guild entry = mock(Guild.class);
      when(entry.getId()).thenReturn("guild" + n);
      listed.add(entry);
    }
    GuildCreator listing = mock(GuildCreator.class);
    when(listing.createListItem(eq(fixture.player), any()))
        .thenAnswer(
            call -> {
              ItemStack item = new ItemStack(Material.PAPER);
              ItemMeta meta = item.getItemMeta();
              meta.setDisplayName(((Guild) call.getArgument(1)).getId());
              meta.getPersistentDataContainer()
                  .set(
                      new NamespacedKey(fixture.ui.plugin, "id"),
                      PersistentDataType.STRING,
                      ((Guild) call.getArgument(1)).getId());
              item.setItemMeta(meta);
              return item;
            });
    view.creator = listing;
    try (MockedConstruction<FactionRanker> rankings =
        mockConstruction(
            FactionRanker.class,
            (mock, context) ->
                when(mock.getVisibleRankedGuildList(eq(fixture.player), any()))
                    .thenReturn(listed))) {
      view.guildList(fixture.player);
      Inventory first = fixture.player.getOpenInventory().getTopInventory();
      assertEquals("guild0", first.getItem(0).getItemMeta().getDisplayName());
      assertEquals("guild46", first.getItem(52).getItemMeta().getDisplayName());
      assertNull(first.getItem(17));
      assertNotNull(first.getItem(53));
      assertNull(first.getItem(45));
      manager.clickButton(fixture.ui.click(fixture.player, 53));
      assertEquals(1, GuildView.currentPage.get(fixture.player));
      Inventory second = fixture.player.getOpenInventory().getTopInventory();
      assertEquals("guild47", second.getItem(0).getItemMeta().getDisplayName());
      assertNull(second.getItem(53));
      assertNotNull(second.getItem(45));
      manager.clickButton(fixture.ui.click(fixture.player, 45));
      assertEquals(0, GuildView.currentPage.get(fixture.player));
      for (RankType expected :
          List.of(RankType.MEMBERS, RankType.TRADE_POWER, RankType.INCOME, RankType.WEALTH)) {
        manager.clickButton(fixture.ui.click(fixture.player, 8));
        assertEquals(expected, GuildView.currentRanking.get(fixture.player));
        assertEquals(0, GuildView.currentPage.get(fixture.player));
      }
      Guild selected = listed.getFirst();
      fixture.factions.when(() -> FactionManager.getGuildByString("guild0")).thenReturn(selected);
      view = spy(view);
      manager.guildView = view;
      doNothing().when(view).guildView(fixture.player, selected);
      manager.clickButton(fixture.ui.click(fixture.player, 0));
      verify(view).guildView(fixture.player, selected);
      Inventory existing = fixture.player.getOpenInventory().getTopInventory();
      clearInvocations(fixture.player);
      view.guildList(fixture.player, existing);
      verify(fixture.player, never()).openInventory(any(Inventory.class));
    }
  }

  @Test
  void exactGuildRenderingUsesRealBranchControlsAndCurrentMembership() {
    Guild display = displayGuild(guild);
    fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(display);
    Upgrade upgrade = new Upgrade(fixture.upgrade("roads", false, 1), 1);
    when(display.getUpgrades()).thenReturn(List.of(upgrade));
    BranchIncomePreview.Prepared prepared = mock(BranchIncomePreview.Prepared.class);
    when(BranchIncomePreview.prepare(fixture.provinces)).thenReturn(prepared);
    view.setProvinceManager(null);
    view.guildView(fixture.player, display, true);
    Inventory inventory = fixture.player.getOpenInventory().getTopInventory();
    SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
    assertEquals("merchants", holder.getId());
    assertTrue(holder.getFlag());
    assertEquals("commerce", payload(inventory.getItem(29), Keys.BRANCH_ID));
    assertEquals(true, flag(inventory.getItem(20)));
    assertEquals(false, flag(inventory.getItem(38)));
    assertNotNull(inventory.getItem(1));
    assertNotNull(inventory.getItem(28));
    assertNotNull(inventory.getItem(17));
    assertNotNull(inventory.getItem(8));
    assertNotNull(inventory.getItem(19));
    verify(fixture.provinces).recalculate();
    verify(fixture.provinces).getIncome(display);
    verify(fixture.provinces).recalculateIfNeeded();
    previewTasks.verify(
        () ->
            BranchIncomePreviewService.schedule(
                fixture.player, inventory, 20, prepared, display, display.getBranch(0), 1));
    previewTasks.verify(
        () ->
            BranchIncomePreviewService.schedule(
                fixture.player, inventory, 38, prepared, display, display.getBranch(0), -1));
    display.setLeader("Other");
    display.kick("Leader");
    display.setCapital(-1);
    view.guildView(fixture.player, display, inventory);
    assertNull(inventory.getItem(1));
    assertNull(inventory.getItem(28));
    assertNull(inventory.getItem(20));
    assertNull(inventory.getItem(38));
    assertNotNull(inventory.getItem(29));
  }

  @Test
  void ledgerRenderingCentresRealmSourcesAndKeepsTheBackDestination() {
    Guild display = displayGuild(fixture.guild("realm", 0));
    try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
      view.ledgerView(fixture.player, display, null, true);
      Inventory inventory = fixture.player.getOpenInventory().getTopInventory();
      SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
      assertTrue(holder.getFlag());
      assertEquals(SFGUI.LEDGER_VIEW, holder.getType());
      for (int slot = 10; slot <= 15; slot++) assertNotNull(inventory.getItem(slot));
      assertNull(inventory.getItem(9));
      assertNull(inventory.getItem(16));
      assertNotNull(inventory.getItem(31));
      clearInvocations(fixture.player);
      view.ledgerView(fixture.player, display, inventory);
      verify(fixture.player, never()).openInventory(any(Inventory.class));
      Guild ordinary = displayGuild(guild);
      view.ledgerView(fixture.player, ordinary, null);
      assertNull(fixture.player.getOpenInventory().getTopInventory().getItem(10));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void upgradeRenderingShowsNineDefinitionsAndThreeQueueEntriesWithLeaderOnlyControls(
      boolean leader) {
    for (int n = 0; n < 10; n++) fixture.upgrade("upgrade" + n, false, 2);
    guild = fixture.guild("guild", 0);
    if (!leader) guild.setLeader("Other");
    fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    List<UpgradeExpansion> queue = new ArrayList<>();
    for (int n = 0; n < 4; n++) queue.add(new UpgradeExpansion(guild.getUpgrade("upgrade" + n), 2));
    guild.setUpgradeQueue(queue);
    view.upgradeView(fixture.player, guild);
    Inventory inventory = fixture.player.getOpenInventory().getTopInventory();
    assertEquals("upgrade0", payload(inventory.getItem(9), Keys.STRING_KEY));
    assertEquals("upgrade8", payload(inventory.getItem(17), Keys.STRING_KEY));
    assertEquals(leader, inventory.getItem(0) != null);
    assertEquals(leader, inventory.getItem(18) != null);
    assertNotNull(inventory.getItem(39));
    assertNotNull(inventory.getItem(41));
    assertNull(inventory.getItem(42));
    assertEquals(
        QueueCancelPayload.guildUpgrade("merchants", 2),
        payload(inventory.getItem(41), Keys.QUEUE_CANCEL));
    assertNotNull(inventory.getItem(53));
  }

  @ParameterizedTest
  @ValueSource(strings = {"success", "failed", "offline_success", "offline_failed", "deleted"})
  void bannerCallbackHandlesSuccessFailureOfflinePlayersAndDeletedGuilds(String outcome) {
    Inventory inventory = open();
    inventory.setItem(28, new ItemStack(Material.PAPER));
    List<Consumer<List<String>>> pending = new ArrayList<>();
    List<ItemStack> refreshed = new ArrayList<>();
    try (MockedStatic<BannerFetcher> banners = mockStatic(BannerFetcher.class)) {
      banners
          .when(() -> BannerFetcher.fetch(eq("guild:merchants"), any()))
          .thenAnswer(
              call -> {
                pending.add(call.getArgument(1));
                return null;
              });
      banners
          .when(
              () ->
                  BannerFetcher.refreshOpenView(
                      eq(fixture.player), eq(SFGUI.GUILD_VIEW), eq("merchants"), any()))
          .thenAnswer(
              call -> {
                refreshed.add(((java.util.function.Supplier<ItemStack>) call.getArgument(3)).get());
                return null;
              });
      manager.clickButton(fixture.ui.click(fixture.player, 28));
      assertEquals(1, pending.size());
      if (outcome.startsWith("offline")) when(fixture.player.isOnline()).thenReturn(false);
      if (outcome.equals("deleted"))
        fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(null);
      boolean success = outcome.endsWith("success");
      pending.getFirst().accept(success ? new ArrayList<>(List.of("red")) : null);
      assertEquals(success ? List.of("red") : List.of("white"), guild.getBannerPatterns());
      assertEquals(success ? 1 : 0, refreshed.size());
      if (success) assertEquals(Material.RED_BANNER, refreshed.getFirst().getType());
      verify(fixture.player, times(outcome.equals("failed") ? 1 : 0))
          .sendMessage("§cCould not generate a banner right now. Try again later.");
      verify(fixture.player, times(outcome.equals("success") ? 1 : 0))
          .playSound(fixture.player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"not_leader", "base"})
  void onlyAnOrdinaryGuildLeaderCanRequestNewBannerPatterns(String denied) {
    if (denied.equals("base")) {
      guild = fixture.guild("realm", 0);
      fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    } else guild.setLeader("Other");
    Inventory inventory = open();
    inventory.setItem(28, new ItemStack(Material.PAPER));
    try (MockedStatic<BannerFetcher> banners = mockStatic(BannerFetcher.class)) {
      manager.clickButton(fixture.ui.click(fixture.player, 28));
      banners.verifyNoInteractions();
      verify(fixture.player)
          .sendMessage("§cYou must be the leader of a guild to change the banner!");
    }
  }

  @Test
  void bannerTakeCopiesTheGuildBannerAndRejectsStaleMembership() {
    Inventory inventory = open();
    inventory.setItem(1, creator.createMenuItem(fixture.player, guild, MenuItemType.BANNER_GET));
    manager.clickButton(fixture.ui.click(fixture.player, 1));
    assertEquals(Material.WHITE_BANNER, fixture.player.getInventory().getItem(0).getType());
    assertNotSame(guild.getBanner(), fixture.player.getInventory().getItem(0));
    fixture.player.getInventory().clear();
    guild.kick("Leader");
    manager.clickButton(fixture.ui.click(fixture.player, 1));
    assertNull(fixture.player.getInventory().getItem(0));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "upgrade",
        "insufficient",
        "downgrade_zero",
        "no_flag",
        "not_leader",
        "no_branch_id"
      })
  void branchClicksApplyOnlyAuthorizedAffordableWellFormedActions(String scenario) {
    quietRendering();
    guild.getBank().setWealth(scenario.equals("insufficient") ? 0.0 : 1000.0);
    guild.updateWealth();
    Branch branch = guild.getBranch(0);
    if (scenario.equals("downgrade_zero")) branch.levelDown();
    if (scenario.equals("not_leader")) guild.setLeader("Other");
    Inventory inventory = open();
    ItemStack item =
        scenario.equals("downgrade_zero")
            ? creator.createBranchDowngradeItem(fixture.player, guild, branch)
            : creator.createBranchUpgradeItem(fixture.player, guild, branch);
    ItemMeta meta = item.getItemMeta();
    if (scenario.equals("no_flag")) meta.getPersistentDataContainer().remove(Keys.BOOLEAN_FLAG);
    if (scenario.equals("no_branch_id")) meta.getPersistentDataContainer().remove(Keys.BRANCH_ID);
    item.setItemMeta(meta);
    inventory.setItem(20, item);
    int before = branch.getLevel();
    double balance = guild.getBank().getWealth();
    InventoryClickEvent click = fixture.ui.click(fixture.player, 20);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    assertEquals(before + (scenario.equals("upgrade") ? 1 : 0), branch.getLevel());
    assertEquals(balance - (scenario.equals("upgrade") ? 200 : 0), guild.getBank().getWealth());
    if (scenario.equals("upgrade")) {
      verify(fixture.provinces).recalculateForSingleGuild(guild, true);
      verify(view).guildView(fixture.player, guild, inventory);
    }
    if (scenario.equals("insufficient"))
      verify(fixture.player).sendMessage("§cCannot afford to upgrade");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "upgrade",
        "full",
        "downgrade",
        "zero",
        "missing",
        "no_flag",
        "not_leader",
        "queue",
        "queue_denied",
        "no_key"
      })
  void upgradeClicksRespectOwnershipLimitsAndQueueCancellation(String scenario) {
    fixture.upgrade("roads", false, 2);
    guild = fixture.guild("guild", 0);
    fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    quietRendering();
    InventoryManager callbacks = routingBoundary();
    Upgrade upgrade = guild.getUpgrade("roads");
    if (scenario.equals("downgrade")) upgrade.setLevel(2);
    if (scenario.equals("full")) for (int n = 0; n < 3; n++) guild.enqueueUpgrade(upgrade);
    if (scenario.equals("not_leader") || scenario.equals("queue_denied")) guild.setLeader("Other");
    Inventory inventory = open(SFGUI.UPGRADE_VIEW, "§7Upgrade View");
    ItemStack item;
    if (scenario.startsWith("queue"))
      item = creator.createUpgradeQueueItem(new UpgradeExpansion(upgrade, 2), 0, guild);
    else if (scenario.equals("downgrade") || scenario.equals("zero"))
      item = creator.createUpgradeDowngradeItem(fixture.player, guild, upgrade);
    else item = creator.createUpgradeUpgradeItem(fixture.player, guild, upgrade);
    ItemMeta meta = item.getItemMeta();
    if (scenario.equals("missing"))
      meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, "removed");
    if (scenario.equals("no_flag")) meta.getPersistentDataContainer().remove(Keys.BOOLEAN_FLAG);
    if (scenario.equals("no_key")) meta.getPersistentDataContainer().remove(Keys.STRING_KEY);
    item.setItemMeta(meta);
    inventory.setItem(0, item);
    manager.clickButton(fixture.ui.click(fixture.player, 0));
    assertEquals(
        scenario.equals("upgrade") ? 1 : scenario.equals("full") ? 3 : 0,
        guild.getUpgradeQueue().size());
    assertEquals(scenario.equals("downgrade") ? 1 : 0, upgrade.getLevel());
    if (scenario.equals("full")) verify(fixture.player).sendMessage("§cUpgrade queue is full");
    verify(callbacks, times(scenario.equals("queue") ? 1 : 0))
        .openQueueCancelConfirm(
            fixture.player,
            fixture.host,
            QueueCancelPayload.guildUpgrade("merchants", 0),
            "§eCancel queued upgrade?");
  }

  @ParameterizedTest
  @ValueSource(ints = {14, 16, 19, 25, 17, 8, 37})
  void authorizedGuildNavigationUsesTheRegisteredManagers(int slot) {
    quietRendering();
    InventoryManager callbacks = routingBoundary();
    if (slot == 19) guild.setCompany(mock(MercenaryCompany.class));
    Inventory inventory = open();
    inventory.setItem(slot, new ItemStack(Material.PAPER));
    manager.clickButton(fixture.ui.click(fixture.player, slot));
    switch (slot) {
      case 14 -> verify(view).ledgerView(fixture.player, guild, null, true);
      case 16 -> verify(view).upgradeView(fixture.player, guild);
      case 19 -> verify(callbacks).companyView(fixture.player, guild);
      case 25 -> verify(callbacks).loanMainView(fixture.player, guild);
      case 17 -> verify(callbacks).setChangingDividend(fixture.player, guild);
      case 8 -> verify(callbacks).setChangingDonation(fixture.player, guild);
      case 37 -> verify(callbacks).factionView(fixture.player, fixture.host);
      default -> fail();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {16, 17, 8, 19})
  void unauthorizedAndMissingCompanyNavigationDoesNotStartAChange(int slot) {
    quietRendering();
    InventoryManager callbacks = routingBoundary();
    if (slot != 19) guild.setLeader("Other");
    Inventory inventory = open();
    inventory.setItem(slot, new ItemStack(Material.PAPER));
    manager.clickButton(fixture.ui.click(fixture.player, slot));
    verify(callbacks, never()).setChangingDividend(any(), any());
    verify(callbacks, never()).setChangingDonation(any(), any());
    verify(callbacks, never()).companyView(any(), any());
    if (slot == 16) verify(view, never()).upgradeView(any(), any());
    else if (slot == 19)
      verify(fixture.player)
          .sendMessage("§7Use §e/company found <name> §7to found a mercenary company.");
  }

  @ParameterizedTest
  @ValueSource(ints = {16, 19, 25, 20})
  void foreignGuildClicksRouteOnlyToReportedReadOnlySubmenus(int slot) {
    quietRendering();
    InventoryManager callbacks = routingBoundary();
    intelligence
        .when(() -> EspionageService.canViewExact(fixture.player, fixture.host))
        .thenReturn(false);
    Inventory inventory = open();
    inventory.setItem(slot, new ItemStack(Material.PAPER));
    manager.clickButton(fixture.ui.click(fixture.player, slot));
    if (slot == 16) verify(view).upgradeView(fixture.player, guild);
    if (slot == 19) verify(callbacks.companyView).companyView(fixture.player, guild);
    if (slot == 25) verify(callbacks.loanView).loanMainView(fixture.player, guild);
    assertEquals(1, guild.getSize());
    assertEquals(0, guild.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "base",
        "disabled",
        "no_target",
        "missing_province",
        "invalid",
        "sea",
        "poor",
        "cross_prompt",
        "cross_request",
        "intra_prompt",
        "intra_failed",
        "intra_success"
      })
  void relocationClicksValidateTheCurrentDestinationFundsAndNamingPrompt(String scenario) {
    if (scenario.equals("base")) {
      guild = fixture.guild("realm", 0);
      fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    }
    quietRendering();
    guild.getBank().setWealth(scenario.equals("poor") ? 0.0 : 1000.0);
    if (scenario.equals("disabled")) Cache.provincesEnabled = false;
    Faction target = scenario.startsWith("cross") ? mock(Faction.class) : fixture.host;
    if (target != fixture.host) when(target.getId()).thenReturn("other");
    if (!scenario.equals("no_target"))
      when(map.getRelocationTarget(fixture.player)).thenReturn(target);
    Province province = mock(Province.class);
    when(province.isValid()).thenReturn(!scenario.equals("invalid"));
    when(province.isSea()).thenReturn(scenario.equals("sea"));
    if (!scenario.equals("missing_province")) when(fixture.provinces.get(9)).thenReturn(province);
    try (MockedStatic<RestServer> rest = mockStatic(RestServer.class);
        MockedStatic<RelocationPrompt> prompts = mockStatic(RelocationPrompt.class)) {
      rest.when(() -> RestServer.getProvince(fixture.player)).thenReturn(9);
      boolean cross = scenario.startsWith("cross");
      boolean prompted = scenario.endsWith("prompt");
      prompts
          .when(() -> RelocationPrompt.begin(fixture.player, guild, target, 9, cross, 100.0))
          .thenReturn(prompted);
      prompts
          .when(
              () ->
                  RelocationPrompt.completeIntraFactionRelocate(
                      fixture.player, guild, target, 9, null, 100.0))
          .thenReturn(scenario.equals("intra_success"));
      Inventory inventory = open();
      inventory.setItem(34, new ItemStack(Material.PAPER));
      InventoryClickEvent click = fixture.ui.click(fixture.player, 34);
      manager.clickButton(click);
      assertTrue(click.isCancelled());
      fixture.factions.verify(
          () -> FactionManager.requestRelocation(fixture.player, guild, target, 9, null),
          times(scenario.equals("cross_request") ? 1 : 0));
      prompts.verify(
          () ->
              RelocationPrompt.completeIntraFactionRelocate(
                  fixture.player, guild, target, 9, null, 100.0),
          times(scenario.equals("intra_failed") || scenario.equals("intra_success") ? 1 : 0));
      verify(
              view,
              times(scenario.equals("intra_success") || scenario.equals("cross_request") ? 1 : 0))
          .guildView(fixture.player, guild, inventory);
      if (scenario.equals("poor"))
        verify(fixture.player).sendMessage("§cCannot afford to relocate");
      assertEquals(scenario.equals("poor") ? 0.0 : 1000.0, guild.getBank().getWealth());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"not_leader", "ineligible", "poor", "success"})
  void elevationClicksRequireHostLeadershipEligibilityAndAdministrativePower(String scenario) {
    when(fixture.host.isLeader("Leader")).thenReturn(!scenario.equals("not_leader"));
    when(fixture.host.hasFactionRule(Rules.CAN_HAVE_VASSALS))
        .thenReturn(!scenario.equals("ineligible"));
    when(fixture.host.getGovernment().getPower())
        .thenReturn(scenario.equals("poor") ? 0.0 : 1000.0);
    Inventory inventory = open();
    inventory.setItem(43, new ItemStack(Material.PAPER));
    double cost = guild.getElevationCost();
    manager.clickButton(fixture.ui.click(fixture.player, 43));
    verify(fixture.host.getGovernment(), times(scenario.equals("success") ? 1 : 0))
        .spendPower(cost);
    fixture.factions.verify(
        () -> FactionManager.requestElevation(fixture.player, guild),
        times(scenario.equals("success") ? 1 : 0));
    verify(fixture.player, times(scenario.equals("success") ? 1 : 0)).closeInventory();
    if (scenario.equals("poor")) verify(fixture.player).sendMessage("§cCannot afford to elevate");
  }

  @ParameterizedTest
  @ValueSource(strings = {"not_host_leader", "involved_in_movement", "poor"})
  void evictionDenialsDoNotChangeGuildOwnershipOrGovernmentPower(String scenario) {
    guild.setLeader("Other");
    when(fixture.host.isLeader("Leader")).thenReturn(!scenario.equals("not_host_leader"));
    if (!scenario.equals("involved_in_movement"))
      when(fixture.host.getGovernment().getMovementByMember("Other")).thenReturn(null);
    when(fixture.host.getGovernment().getPower())
        .thenReturn(scenario.equals("poor") ? 0.0 : 1000.0);
    Inventory inventory = open();
    inventory.setItem(34, new ItemStack(Material.PAPER));
    manager.clickButton(fixture.ui.click(fixture.player, 34));
    assertSame(fixture.host, guild.getFaction());
    verify(fixture.host.getGovernment(), never()).spendPower(anyDouble());
    verify(fixture.host.getGuildHandler(), never()).removeGuild(anyString());
    if (scenario.equals("poor")) verify(fixture.player).sendMessage("§cCannot afford to evict");
  }

  @Test
  void foreignRenderingDelegatesToIntelligenceViewsWithoutExposingExactLedgerData() {
    intelligence
        .when(() -> EspionageService.canViewExact(fixture.player, fixture.host))
        .thenReturn(false);
    try (MockedStatic<EspionageView> foreign = mockStatic(EspionageView.class)) {
      Inventory inventory = open();
      view.guildView(fixture.player, guild, inventory);
      foreign.verify(() -> EspionageView.guild(inventory, fixture.player, guild, manager));
      view.ledgerView(fixture.player, guild, null);
      foreign.verify(() -> EspionageView.foreignLedger(fixture.player, guild, manager));
      ItemStack redacted = new ItemStack(Material.WRITABLE_BOOK);
      foreign.when(() -> EspionageView.ledgerItem(null, guild)).thenReturn(redacted);
      assertSame(redacted, creator.createLedgerItem(fixture.player, guild));
    }
    view.upgradeView(fixture.player, guild);
    Inventory inventory = fixture.player.getOpenInventory().getTopInventory();
    // The reported renderer owns the contents. Its delegate receives the same guild and viewer.
    reported.verify(() -> ReportedMenus.upgrades(inventory, fixture.player, guild, manager));
    org.mockito.Mockito.verifyNoInteractions(fixture.provinces);
    assertEquals(SFGUI.UPGRADE_VIEW, ((SFInventoryHolder) inventory.getHolder()).getType());
  }

  @Test
  void renderingAdministrativeActionsAndRelocationUsesTheCurrentHostAndDestination() {
    Guild display = displayGuild(guild);
    when(map.getRelocationTarget(fixture.player)).thenReturn(fixture.host);
    RelationType relation = mock(RelationType.class);
    when(relation.getName()).thenReturn("vassal");
    try (MockedStatic<RelationLoader> definitions = mockStatic(RelationLoader.class);
        MockedStatic<RestServer> rest = mockStatic(RestServer.class)) {
      definitions.when(RelationLoader::getElevationTarget).thenReturn(relation);
      view.guildView(fixture.player, display);
      Inventory inventory = fixture.player.getOpenInventory().getTopInventory();
      assertFalse(((SFInventoryHolder) inventory.getHolder()).getFlag());
      assertEquals(Material.FILLED_MAP, inventory.getItem(34).getType());
      assertNull(inventory.getItem(43));
      when(fixture.host.isLeader("Leader")).thenReturn(true);
      view.guildView(fixture.player, display, inventory);
      assertTrue(
          ChatColor.stripColor(inventory.getItem(34).getItemMeta().getDisplayName())
              .contains("Evict"));
      assertNotNull(inventory.getItem(43));
      Guild base = displayGuild(fixture.guild("realm", 0));
      view.guildView(fixture.player, base, inventory);
      assertNull(inventory.getItem(8));
      assertNull(inventory.getItem(17));
      assertNull(inventory.getItem(28));
    }
  }

  @Test
  void absentGuildAndUnrelatedInventoriesLeaveActionsUntouched() {
    Inventory inventory = open();
    inventory.setItem(20, new ItemStack(Material.PAPER));
    fixture.factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(null);
    manager.clickButton(fixture.ui.click(fixture.player, 20));
    assertEquals(1, guild.getSize());
    Inventory upgrades = open(SFGUI.UPGRADE_VIEW, "§7Upgrade View");
    upgrades.setItem(0, new ItemStack(Material.PAPER));
    manager.clickButton(fixture.ui.click(fixture.player, 0));
    assertTrue(guild.getUpgradeQueue().isEmpty());
    Inventory foreign = fixture.ui.inventory(null, 9, "Foreign plugin");
    fixture.player.openInventory(foreign);
    InventoryClickEvent click = fixture.ui.click(fixture.player, 0);
    view.click(click, foreign, fixture.player);
    assertFalse(click.isCancelled());
  }

  @Test
  void evictionAppliesTheAdvertisedStabilityPenaltyToTheOriginalHost() {
    guild.setLeader("GuildLeader");
    when(fixture.host.isLeader("Leader")).thenReturn(true);
    when(fixture.host.getGovernment().getMovementByMember("GuildLeader")).thenReturn(null);
    when(fixture.host.getGovernment().getPower()).thenReturn(1000.0);
    double impact = guild.getStabilityEffect();
    Inventory inventory = open();
    inventory.setItem(34, new ItemStack(Material.PAPER));
    try (MockedConstruction<Faction> created =
        mockConstruction(
            Faction.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS), (f, context) -> {})) {
      manager.clickButton(fixture.ui.click(fixture.player, 34));
      ArgumentCaptor<StabilityModifier> applied = ArgumentCaptor.forClass(StabilityModifier.class);
      verify(fixture.host.getGovernment()).addStabilityModifier(applied.capture());
      assertEquals("Evicted Guild", applied.getValue().getName());
      assertEquals(-impact, applied.getValue().getModifier());
    }
  }

  @Test
  void configuredBranchGroupGapsDoNotHideLaterBranchesOrTheirControls() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("trade.group", 4);
    config.set("trade.allowed-types", List.of("guild"));
    config.set("trade.icon", "emerald.0");
    BranchLoader.map.clear();
    BranchLoader.map.put("trade", new Branch("trade", config.getConfigurationSection("trade")));
    GuildData data = fixture.data("guild", 2);
    data.branches.getFirst().id = "trade";
    Guild display = displayGuild(new Guild(data, fixture.host));
    Inventory inventory = open();
    view.guildView(fixture.player, display, inventory);
    assertNotNull(inventory.getItem(33));
    assertEquals("trade", payload(inventory.getItem(33), Keys.BRANCH_ID));
    assertEquals(true, flag(inventory.getItem(24)));
    assertEquals(false, flag(inventory.getItem(42)));
  }

  private void quietRendering() {
    view = spy(view);
    manager.guildView = view;
    doNothing().when(view).guildView(any(), any(), any(Inventory.class));
    doNothing().when(view).upgradeView(any(), any(), any(Inventory.class));
    doNothing().when(view).upgradeView(any(), any());
    doNothing().when(view).ledgerView(any(), any(), any(), anyBoolean());
  }

  private InventoryManager routingBoundary() {
    InventoryManager callbacks = mock(InventoryManager.class);
    callbacks.companyView = mock(CompanyView.class);
    callbacks.loanView = mock(LoanView.class);
    view.inv = callbacks;
    return callbacks;
  }

  private Inventory open(SFGUI type, String title) {
    Inventory inventory = fixture.ui.inventory(new SFInventoryHolder("merchants", type), 54, title);
    fixture.player.openInventory(inventory);
    return inventory;
  }

  private Guild displayGuild(Guild original) {
    Guild display = spy(original);
    Ledger ledger = mock(Ledger.class);
    when(ledger.getHistory()).thenReturn(new LedgerHistory());
    when(ledger.getDividendBreakdown()).thenReturn(DividendBreakdown.none());
    doReturn(ledger).when(display).getLedger();
    return display;
  }

  private static String payload(ItemStack item, NamespacedKey key) {
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  private static Boolean flag(ItemStack item) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN);
  }

  private static String lore(ItemStack item) {
    return String.join(
        "\n", item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList());
  }

  private static void assertLore(ItemStack item, String text, boolean present) {
    assertEquals(
        present,
        lore(item).contains(text),
        () -> "Expected " + text + " present=" + present + " in " + lore(item));
  }

  private Inventory open() {
    Inventory inventory =
        fixture.ui.inventory(
            new SFInventoryHolder("merchants", SFGUI.GUILD_VIEW), 54, "§7Guild View");
    fixture.player.openInventory(inventory);
    return inventory;
  }
}
