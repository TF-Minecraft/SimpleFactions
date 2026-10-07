package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.MenuItemType;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.CharacterNames;
import net.tfminecraft.simplefactions.espionage.EspionageAccess;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.espionage.IntelligenceReport;
import net.tfminecraft.simplefactions.espionage.RosterLore;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.objects.PrestigeRank;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.util.LegacyModelData;
import net.tfminecraft.simplefactions.utils.FactionRanker;
import net.tfminecraft.simplefactions.utils.HomeSettlementNames;
import net.tfminecraft.simplefactions.utils.Represents;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
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
import org.mockito.MockedStatic;

class FactionMenusCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Player player;
  private InventoryManager manager;
  private FactionView view;
  private FactionCreator creator;
  private MockedStatic<EspionageService> intelligence;
  private FactionRanker ranker;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();
  private HashMap<Player, RankType> oldRanking;
  private HashMap<Player, Integer> oldPage;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> s = mockStatic(type);
    scopes.add(s);
    return s;
  }

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    player = fixture.player("Leader");
    faction = fixture.saved("realm", "Leader");
    faction.getOrCreateMainGuild();
    manager = new InventoryManager();
    view = manager.factionView;
    creator = view.creator;
    intelligence = scoped(EspionageService.class);
    intelligence.when(() -> EspionageService.canViewExact(any(), any())).thenReturn(true);
    intelligence.when(() -> EspionageService.isOwn(player, faction)).thenReturn(true);
    scoped(CharacterNames.class)
        .when(() -> CharacterNames.display(any(), anyString(), any()))
        .thenAnswer(call -> call.getArgument(1));
    scoped(RosterLore.class)
        .when(() -> RosterLore.faction(any(), any()))
        .thenReturn(List.of("Leader", "Alice"));
    scoped(HomeSettlementNames.class)
        .when(() -> HomeSettlementNames.of(any(Faction.class)))
        .thenReturn("River Town");
    ranker = mock(FactionRanker.class);
    creator.r = ranker;
    view.r = ranker;
    scoped(EspionageAccess.class);
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator items = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(items);
    when(items.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(api);
    scoped(IconGetter.class);
    oldRanking = FactionView.currentRanking;
    oldPage = FactionView.currentPage;
    FactionView.currentRanking = new HashMap<>();
    FactionView.currentPage = new HashMap<>();
  }

  @AfterEach
  void close() {
    try {
      for (int n = scopes.size() - 1; n >= 0; n--) scopes.get(n).close();
    } finally {
      FactionView.currentRanking = oldRanking;
      FactionView.currentPage = oldPage;
      fixture.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"leadership", "deleted"})
  void aPendingBannerCannotApplyAfterTheFactionOrItsLeadershipChanges(String change) {
    Inventory inventory = open();
    inventory.setItem(19, new ItemStack(Material.PAPER));
    manager.clickButton(fixture.ui.click(player, 19));
    assertEquals(1, fixture.bannerCallbacks.size());
    if (change.equals("leadership")) {
      faction.addMember("Successor");
      faction.setLeader("Successor");
    } else net.tfminecraft.simplefactions.managers.FactionManager.factions.clear();
    fixture.bannerCallbacks.getFirst().accept(new ArrayList<>(List.of("red")));
    assertEquals(List.of("white"), faction.getBannerPatterns());
  }

  @Test
  void aBannerButtonDoesNotGrantItemsAfterFactionMembershipIsLost() {
    Inventory inventory = open();
    inventory.setItem(1, new ItemStack(Material.CHEST));
    manager.clickButton(fixture.ui.click(player, 1));
    assertEquals(Material.WHITE_BANNER, player.getInventory().getItem(0).getType());
    player.getInventory().clear();
    faction.getOrCreateMainGuild().kick("Leader");
    manager.clickButton(fixture.ui.click(player, 1));
    assertNull(player.getInventory().getItem(0));
  }

  @Test
  void ownedAndEstimatedListLoreKeepsPublicIdentityButLabelsPrivateValues() {
    Faction display = spy(faction);
    doReturn(25.0).when(display).getProsperity();
    display.setWealth(200.0);
    display.setPrestige(150.0);
    doReturn(List.of("Leader", "Alice", "Bob")).when(display).getCompleteMemberList();
    when(ranker.getVisibleRank(player, display, RankType.WEALTH)).thenReturn(2);
    when(ranker.getPrestigeRank(display)).thenReturn(1);
    Title title = fixture.title("North County", "county", 1);
    doReturn(List.of(title)).when(display).getTitles();
    doReturn(title).when(display).getHighestTitle();
    try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
      titles.when(() -> TitleManager.getRealmSize(display)).thenReturn(3);
      ItemStack item = creator.createListItem(player, display);
      assertEquals("realm", id(item));
      assertLore(item, "Primary Title", true);
      assertLore(item, "from subjects", true);
      assertLore(item, "200.0d", true);
      assertLore(item, "River Town", true);
      assertLore(item, "25.0", true);
      intelligence.when(() -> EspionageService.canViewExact(player, display)).thenReturn(false);
      IntelligenceReport report = mock(IntelligenceReport.class);
      when(report.display(anyString())).thenReturn("Unknown");
      when(report.display("Wealth")).thenReturn("100–300");
      intelligence.when(() -> EspionageService.report(player, display)).thenReturn(report);
      item = creator.createListItem(player, display);
      assertLore(item, "100–300d", true);
      assertLore(item, "estimated 2", true);
      assertLore(item, "Unknown", true);
      intelligence.when(() -> EspionageService.report(player, display)).thenReturn(null);
      when(ranker.getVisibleRank(player, display, RankType.WEALTH)).thenReturn(null);
      assertLore(creator.createListItem(player, display), "Unknown", true);
      doReturn(List.of(1, 2, 3)).when(display).getProvinces();
      assertLore(creator.createListItem(player, display), "Realm Size: 3", true);
    }
  }

  @Test
  void listLoreDescribesMutualAndAsymmetricRelationsGuildsSubjectsAndAllies() {
    Faction other = fixture.saved("other", "Other");
    other.getOrCreateMainGuild();
    fixture.guild(other, "Merchants", "Trader");
    fixture.subject(other, faction);
    Faction ally = fixture.saved("ally", "Ally");
    other.setRelation(
        ally,
        new Relation(
            fixture.relationType("ally", Map.of("name", "Allied")),
            RelationLoader.getDefaultAttitude()));
    ItemStack item = creator.createListItem(player, other);
    assertLore(item, "outgoing", true);
    assertLore(item, "incoming", true);
    assertLore(item, "Merchants", true);
    assertLore(item, "Subjects", true);
    assertLore(item, "Allies", true);
    assertLore(item, "ally", true);
    faction.setRelation(
        other, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
    other.setRelation(
        faction,
        new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
    assertLore(creator.createListItem(player, other), "mutual", true);
    assertFalse(String.join("\n", creator.listLore(null, other)).contains("Diplomacy"));
  }

  @Test
  void dissolvePreviewExplainsGuildTransfersIndependentSubjectsAndAnEmptyRealm() {
    assertLore(creator.createDissolveItem(faction), "No other factions or guilds", true);
    Guild guild = fixture.guild(faction, "Merchants", "Trader");
    Faction subject = fixture.saved("subject", "Subject");
    subject.getOrCreateMainGuild();
    fixture.subject(faction, subject);
    ItemStack independent = creator.createDissolveItem(faction);
    assertLore(independent, "Merchants", true);
    assertLore(independent, "independent faction", true);
    assertLore(independent, "subject", true);
    assertEquals("realm", id(independent));
    Faction overlord = fixture.saved("overlord", "Overlord");
    overlord.getOrCreateMainGuild();
    fixture.subject(overlord, faction);
    try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
      assertLore(creator.createDissolveItem(faction), "transferred to overlord", true);
      wars.when(() -> WarManager.isAtWar(subject)).thenReturn(true);
      assertLore(creator.createDissolveItem(faction), "at war", true);
    }
    assertEquals("Trader", guild.getLeader());
    assertSame(faction, guild.getFaction());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void prestigeLoreDisplaysCurrentAndAdjacentRankEffectsWithCorrectSigns(boolean an) {
    PrestigeRank low = RankLoader.getByLevel(1),
        middle = RankLoader.getByLevel(2),
        high = fixture.rank("illustrious", 3, 20000);
    low.setAn(an);
    high.setAn(an);
    low.getModifiers().add(new FactionModifier(FactionModifiers.MILITARY_UPKEEP, 5));
    middle.getModifiers().add(new FactionModifier(FactionModifiers.MILITARY_UPKEEP, 0));
    middle
        .getModifiers()
        .add(new FactionModifier(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER, 2));
    high.getModifiers()
        .add(new FactionModifier(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER, 3));
    faction.setRank(middle);
    faction.setPrestigeModifiers(
        new ArrayList<>(
            List.of(
                new Modifier("Achievements", 10.0, false),
                new Modifier("Losses", -2.0, false),
                new Modifier("20% Malus", -3.0, false),
                new Modifier("10% Inactivity", -4.0, false))));
    ItemStack item = creator.createMenuItem(player, faction, MenuItemType.PRESTIGE);
    assertLore(item, "+10.0 from Achievements", true);
    assertLore(item, "-2.0 from Losses", true);
    assertLore(item, "20% Malus: -3.0", true);
    assertLore(item, "10% Inactivity: -4.0", true);
    assertLore(item, an ? "become an illustrious" : "become a illustrious", true);
    assertLore(item, an ? "become an common" : "become a common", true);
    assertLore(item, "Diplomatic", true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void governmentLoreIncludesCouncilRepresentationAndPowerGain(boolean council) {
    Faction display = spy(faction);
    Government gov = mock(Government.class, RETURNS_DEEP_STUBS);
    doReturn(gov).when(display).getGovernment();
    when(gov.getPower()).thenReturn(council ? -4.0 : 5.0);
    when(gov.getMaxPower()).thenReturn(council ? -2.0 : 10.0);
    when(gov.getPowerGain()).thenReturn(council ? -1.0 : 2.0);
    when(gov.hasCouncil()).thenReturn(council);
    when(gov.hasLeaderElections()).thenReturn(council);
    when(gov.hasCouncilElections()).thenReturn(council);
    when(gov.getCouncil().getType()).thenReturn(Rules.ELECTED_COUNCIL);
    when(gov.getCouncil().getCurrentSize()).thenReturn(1);
    when(gov.getCouncil().getMaxSize()).thenReturn(3);
    when(gov.getCouncilMembers()).thenReturn(List.of("Alice"));
    try (MockedStatic<Represents> represents = mockStatic(Represents.class)) {
      represents.when(() -> Represents.represents(display, "Alice")).thenReturn("Merchants");
      ItemStack item = creator.createMenuItem(player, display, MenuItemType.GOVERNMENT);
      assertEquals("realm", id(item));
      assertLore(item, "Administrative Power", true);
      assertLore(item, council ? "-4.0/-2.0" : "5.0/10.0", true);
      assertLore(item, council ? "Alice" : "Has Council", true);
      if (council) assertLore(item, "Merchants", true);
    }
  }

  @Test
  void menuItemsContainTheirActualDomainSummariesAndActionIds() {
    faction.addMember("Alice");
    faction.setWealth(75.0);
    faction.getOrCreateMainGuild().addWealthModifier(new Modifier("Treasury", 75.0, false));
    assertLore(creator.createMenuItem(player, faction, MenuItemType.BANNER), "Riverfolk", true);
    assertLore(
        creator.createMenuItem(player, faction, MenuItemType.BANNER_GET), "Click to get", true);
    assertLore(
        creator.createMenuItem(player, faction, MenuItemType.BANNER_RANDOM), "randomise", true);
    assertLore(creator.createMenuItem(player, faction, MenuItemType.LEADER), "Ruling System", true);
    assertLore(
        creator.createMenuItem(player, faction, MenuItemType.WEALTH), "75.0d from Treasury", true);
    assertTrue(name(creator.createMenuItem(player, faction, MenuItemType.MEMBERS)).contains("2"));
    assertLore(creator.createMenuItem(player, faction, MenuItemType.MEMBERS), "Alice", true);
    assertLore(creator.createMenuItem(player, faction, MenuItemType.GUILDS), "1 guild", true);
    fixture.guild(faction, "Merchants", "Trader");
    assertLore(creator.createMenuItem(player, faction, MenuItemType.GUILDS), "2 guilds", true);
    for (MenuItemType type :
        List.of(
            MenuItemType.MILITARY,
            MenuItemType.TIER,
            MenuItemType.TITLES,
            MenuItemType.MODIFIERS,
            MenuItemType.TAX,
            MenuItemType.LAWS))
      assertEquals("realm", id(creator.createMenuItem(player, faction, type)));
    assertEquals(
        Material.DIRT,
        creator.createMenuItem(player, faction, MenuItemType.TRADE_BREAKDOWN).getType());
    ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
    ItemMeta meta = icon.getItemMeta();
    LegacyModelData.set(meta, 17);
    icon.setItemMeta(meta);
    when(IconGetter.hasIcon("WEALTH")).thenReturn(true);
    when(IconGetter.getIcon("WEALTH")).thenReturn(icon);
    ItemStack overridden = creator.createMenuItem(player, faction, MenuItemType.WEALTH);
    assertEquals(Material.PLAYER_HEAD, overridden.getType());
    assertEquals(17, LegacyModelData.get(overridden.getItemMeta()));
    assertLore(overridden, "Treasury", true);
  }

  @Test
  void installationsDiplomacyModifiersTaxesAndLawsShowOnlyApplicableValues() {
    Faction display = spy(faction);
    List<Installation> installations = new ArrayList<>();
    for (InstallationKind kind : InstallationKind.values())
      installations.add(new Installation(kind.name(), kind.name(), kind, 7, 0, 0, 0));
    var handler =
        mock(net.tfminecraft.simplefactions.installation.handler.InstallationHandler.class);
    when(handler.getAll()).thenReturn(installations);
    doReturn(handler).when(display).getInstallationHandler();
    try (MockedStatic<InstallationConfigLoader> config =
        mockStatic(InstallationConfigLoader.class)) {
      config.when(() -> InstallationConfigLoader.getDailyUpkeep(any(), anyInt())).thenReturn(2.0);
      ItemStack item = creator.createMenuItem(player, display, MenuItemType.INSTALLATIONS);
      assertLore(item, "Forts: 1", true);
      assertLore(item, "Train Stations: 1", true);
      assertLore(item, "8.0d/day", true);
    }
    var diplomacy = mock(net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler.class);
    doReturn(diplomacy).when(display).getDiplomacyHandler();
    when(diplomacy.getUsedDiplomaticCapacity()).thenReturn(4.0);
    when(diplomacy.getDiplomaticCapacity()).thenReturn(3.0);
    assertLore(creator.createMenuItem(player, display, MenuItemType.DIPLOMACY), "4.0/3.0", true);
    when(diplomacy.getDiplomaticCapacity()).thenReturn(5.0);
    assertLore(creator.createMenuItem(player, display, MenuItemType.DIPLOMACY), "4.0/5.0", true);
    doReturn(
            List.of(
                new FactionModifier(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER, 2),
                new FactionModifier(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER, 0)))
        .when(display)
        .getCombinedModifiers();
    assertLore(creator.createMenuItem(player, display, MenuItemType.MODIFIERS), "Diplomatic", true);
    var taxes = mock(net.tfminecraft.simplefactions.objects.handler.TaxHandler.class);
    doReturn(taxes).when(display).getTaxHandler();
    when(taxes.canCollectTax(TaxTarget.CITIZENS)).thenReturn(true);
    doReturn(5.0).when(display).getTaxRate(TaxTarget.CITIZENS, null, false);
    doReturn(4.0).when(display).getTaxRate(TaxTarget.CITIZENS, null, true);
    assertLore(creator.createMenuItem(player, display, MenuItemType.TAX), "5.0%", true);
    assertLore(creator.createMenuItem(player, display, MenuItemType.TAX), "4.0% effective", true);
    var law = fixture.lawGroup("trade", Map.of());
    law.setCurrent(law.getLaw("current"));
    var laws = mock(net.tfminecraft.simplefactions.objects.handler.LawHandler.class);
    when(laws.getGroupList()).thenReturn(List.of(law));
    doReturn(laws).when(display).getLawHandler();
    Government gov = mock(Government.class);
    when(gov.getTotalUpkeep()).thenReturn(3.0);
    doReturn(gov).when(display).getGovernment();
    assertLore(
        creator.createMenuItem(player, display, MenuItemType.LAWS),
        "3.0 Administrative Power",
        true);
  }

  @Test
  void foreignMenuItemsDelegateToTheIntelligenceRendererAndKeepIcons() {
    intelligence.when(() -> EspionageService.canViewExact(player, faction)).thenReturn(false);
    try (MockedStatic<EspionageView> foreign = mockStatic(EspionageView.class)) {
      ItemStack redacted = new ItemStack(Material.WRITABLE_BOOK);
      foreign
          .when(() -> EspionageView.factionItem(player, faction, MenuItemType.WEALTH))
          .thenReturn(redacted);
      assertSame(redacted, creator.createMenuItem(player, faction, MenuItemType.WEALTH));
      foreign.verify(() -> EspionageView.factionItem(player, faction, MenuItemType.WEALTH));
    }
  }

  @Test
  void titlesDistinguishOwnersOverlordsAndOtherViewers() {
    Title title = fixture.title("North County", "county", 7);
    Faction target = spy(fixture.saved("subject", "Subject"));
    target.getOrCreateMainGuild();
    doReturn(List.of(title)).when(target).getRankedTitles();
    assertLore(creator.createMenuItem(player, target, MenuItemType.TITLES), "North County", true);
    assertLore(creator.createMenuItem(player, target, MenuItemType.TIER), "Click to edit", false);
    fixture.subject(faction, target);
    assertLore(
        creator.createMenuItem(player, target, MenuItemType.TITLES), "grant to subject", true);
    Player outsider = fixture.player("Outsider");
    assertLore(creator.createMenuItem(outsider, target, MenuItemType.TITLES), "North County", true);
    when(IconGetter.hasIcon("TITLES")).thenReturn(true);
    ItemStack bookIcon = new ItemStack(Material.BOOK);
    ItemMeta bookMeta = bookIcon.getItemMeta();
    LegacyModelData.set(bookMeta, 0);
    bookIcon.setItemMeta(bookMeta);
    when(IconGetter.getIcon("TITLES")).thenReturn(bookIcon);
    assertEquals(
        Material.BOOK, creator.createMenuItem(player, target, MenuItemType.TITLES).getType());
  }

  @Test
  void factionListPagesCarryStableIdsAndChangingRankReturnsToFirstPage() {
    List<Faction> rows = new ArrayList<>();
    for (int n = 0; n < 49; n++) {
      Faction row = mock(Faction.class);
      when(row.getId()).thenReturn("f" + n);
      rows.add(row);
    }
    view.creator = mock(FactionCreator.class);
    when(view.creator.createListItem(eq(player), any()))
        .thenAnswer(c -> tag(c.<Faction>getArgument(1).getId()));
    when(ranker.getVisibleRankedList(eq(player), any())).thenReturn(rows);
    view.factionList(player);
    Inventory first = top();
    assertEquals("f0", id(first.getItem(0)));
    assertNotNull(first.getItem(53));
    assertNull(first.getItem(45));
    click(53);
    assertEquals(1, FactionView.currentPage.get(player));
    assertEquals("f47", id(top().getItem(0)));
    assertNotNull(top().getItem(45));
    assertNull(top().getItem(53));
    click(45);
    assertEquals(0, FactionView.currentPage.get(player));
    assertEquals("f0", id(top().getItem(0)));
    for (RankType rank : List.of(RankType.WEALTH, RankType.MEMBERS, RankType.PRESTIGE)) {
      click(8);
      assertEquals(rank, FactionView.currentRanking.get(player));
      assertEquals(0, FactionView.currentPage.get(player));
    }
    Inventory existing = top();
    view.factionList(player, existing);
    assertSame(existing, top());
    assertEquals("f0", id(top().getItem(0)));
  }

  @Test
  void factionListOnlyOpensRegisteredFactionIds() {
    when(ranker.getVisibleRankedList(eq(player), any())).thenReturn(List.of());
    view.factionList(player);
    Inventory list = top();
    FactionView dispatch = spy(view);
    doNothing().when(dispatch).factionView(player, faction);
    dispatch.click(fixture.ui.click(player, 0), list, player);
    verify(dispatch, never()).factionView(eq(player), any(Faction.class));
    list.setItem(0, new ItemStack(Material.PAPER));
    dispatch.click(fixture.ui.click(player, 0), list, player);
    list.setItem(0, tag("deleted"));
    dispatch.click(fixture.ui.click(player, 0), list, player);
    verify(dispatch, never()).factionView(eq(player), any(Faction.class));
    list.setItem(0, tag("realm"));
    dispatch.click(fixture.ui.click(player, 0), list, player);
    verify(dispatch).factionView(player, faction);
  }

  @Test
  void factionPageShowsOwnerActionsAndRefreshesInPlace() {
    view.guildCreator = mock(GuildCreator.class);
    when(view.guildCreator.createLedgerItem(player, faction.getOrCreateMainGuild()))
        .thenAnswer(call -> new ItemStack(Material.BOOK));
    view.factionView(player, faction);
    Inventory inventory = top();
    assertEquals(SFGUI.FACTION_VIEW, ((SFInventoryHolder) inventory.getHolder()).getType());
    assertEquals("realm", id(inventory.getItem(23)));
    assertEquals(Material.CHEST, inventory.getItem(1).getType());
    assertNotNull(inventory.getItem(19));
    assertNotNull(inventory.getItem(20));
    assertNotNull(inventory.getItem(53));
    view.factionView(player, faction, inventory);
    assertSame(inventory, top());
    Player visitor = fixture.player("Visitor");
    view.factionView(visitor, faction);
    assertNull(visitor.getOpenInventory().getTopInventory().getItem(1));
    assertNull(visitor.getOpenInventory().getTopInventory().getItem(19));
  }

  @Test
  void privateFactionPageUsesForeignRendererForOpenAndRefresh() {
    intelligence.when(() -> EspionageService.canViewExact(player, faction)).thenReturn(false);
    try (MockedStatic<EspionageView> foreign = mockStatic(EspionageView.class)) {
      foreign
          .when(() -> EspionageView.foreign(any(), eq(player), eq(faction), eq(manager)))
          .thenAnswer(
              c -> {
                c.<Inventory>getArgument(0).setItem(4, new ItemStack(Material.SPYGLASS));
                return null;
              });
      view.factionView(player, faction);
      Inventory inventory = top();
      assertEquals(Material.SPYGLASS, inventory.getItem(4).getType());
      view.factionView(player, faction, inventory);
      assertSame(inventory, top());
      foreign.verify(() -> EspionageView.foreign(inventory, player, faction, manager), times(2));
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 15, 25, 28, 29, 30, 31, 32, 33, 34})
  void ownerMenuClicksRouteToTheCurrentFactionAndConfirmation(int slot) {
    InventoryManager navigation = navigation();
    Inventory inventory = open();
    inventory.setItem(slot, tag("realm"));
    click(slot);
    switch (slot) {
      case 11 -> verify(navigation).governmentView(player, faction, null);
      case 15 -> verify(navigation).ledgerView(player, faction.getOrCreateMainGuild(), null);
      case 25 -> verify(navigation).taxView(player, faction);
      case 28 -> verify(navigation).lawView(player, faction, null);
      case 29 -> verify(navigation).militaryView(null, player, faction, true);
      case 30 -> {
        assertSame(faction, navigation.confirming.get(player));
        verify(navigation).confirmView(player, faction, "dissolve", "true");
      }
      case 31 -> verify(navigation).diplomacyListView(null, player, faction, true);
      case 32 -> verify(navigation).installationsView(null, player, faction, true);
      case 33 -> verify(navigation).tierView(null, player, faction, true);
      case 34 -> verify(navigation).titleView(null, player, faction, true);
      default -> fail("Unhandled navigation case");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 25, 28, 29, 32, 19})
  void restrictedViewsRouteOnlyToTheirReadOnlyPage(int slot) {
    InventoryManager navigation = navigation();
    Inventory inventory = open();
    inventory.setItem(slot, tag("realm"));
    intelligence.when(() -> EspionageService.canViewExact(player, faction)).thenReturn(false);
    click(slot);
    switch (slot) {
      case 11 -> verify(navigation).governmentView(player, faction, null);
      case 25 -> verify(navigation).taxView(player, faction);
      case 28 -> verify(navigation).lawView(player, faction, null);
      case 29 -> verify(navigation).militaryView(null, player, faction, true);
      case 32 -> verify(navigation).installationsView(null, player, faction, true);
      case 19 -> {
        verifyNoInteractions(navigation);
        assertTrue(fixture.bannerCallbacks.isEmpty());
      }
      default -> fail();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 28, 29, 30, 31, 32, 33, 34, 23})
  void taggedActionsIgnoreMissingOrDeletedTargets(int slot) {
    InventoryManager navigation = navigation();
    Inventory inventory = open();
    inventory.setItem(slot, new ItemStack(Material.PAPER));
    click(slot);
    inventory.setItem(slot, tag("deleted"));
    click(slot);
    verifyNoInteractions(navigation);
    assertTrue(navigation.confirming.isEmpty());
  }

  @Test
  void titleAndTierEditingRequiresCurrentLeadershipOrTheOverlord() {
    InventoryManager navigation = navigation();
    faction.addMember("Successor");
    faction.setLeader("Successor");
    Inventory inventory = open();
    for (int slot : List.of(33, 34, 19)) {
      inventory.setItem(slot, tag("realm"));
      click(slot);
    }
    verifyNoInteractions(navigation);
    assertTrue(fixture.bannerCallbacks.isEmpty());
    Faction own = fixture.saved("own", "Leader");
    own.getOrCreateMainGuild();
    inventory.setItem(34, tag("realm"));
    click(34);
    verifyNoInteractions(navigation);
    fixture.subject(own, faction);
    click(34);
    verify(navigation).titleView(null, player, faction, true);
  }

  @Test
  void positionsOpensTheFactionPositionPage() {
    InventoryManager navigation = navigation();
    Inventory inventory = open();
    inventory.setItem(20, new ItemStack(Material.PAPER));
    try (MockedStatic<EspionageView> positions = mockStatic(EspionageView.class)) {
      click(20);
      positions.verify(() -> EspionageView.positions(player, faction, navigation));
    }
  }

  @Test
  void currentLeaderBannerRequestsApplyOnceAndReportUnavailablePatterns() {
    fixture.useRealBannerRefresh();
    open().setItem(19, new ItemStack(Material.PAPER));
    click(19);
    fixture.bannerCallbacks.getFirst().accept(new ArrayList<>(List.of("red")));
    assertEquals(List.of("red"), faction.getBannerPatterns());
    assertEquals("Banner of realm", name(top().getItem(10)));
    assertEquals(faction.getBanner().getType(), top().getItem(10).getType());
    assertLore(top().getItem(10), "Riverfolk", true);
    click(19);
    fixture.bannerCallbacks.get(1).accept(null);
    verify(player).sendMessage(contains("Could not generate"));
    assertEquals(List.of("red"), faction.getBannerPatterns());
    when(player.isOnline()).thenReturn(false);
    click(19);
    clearInvocations(player);
    fixture.bannerCallbacks.get(2).accept(null);
    verify(player, never()).sendMessage(anyString());
    click(19);
    clearInvocations(player);
    fixture.bannerCallbacks.get(3).accept(new ArrayList<>(List.of("blue")));
    assertEquals(List.of("blue"), faction.getBannerPatterns());
    verify(player, never()).playSound(eq(player), any(Sound.class), anyFloat(), anyFloat());
  }

  @Test
  void guildListPagesPreserveHolderPageAndOpenOnlyExistingGuilds() {
    InventoryManager navigation = navigation();
    view.guildCreator = mock(GuildCreator.class);
    when(view.guildCreator.createListItem(eq(player), any()))
        .thenAnswer(c -> tag(c.<Guild>getArgument(1).getId()));
    for (int n = 0; n < 49; n++) fixture.guild(faction, "g" + n, "Member" + n);
    List<Guild> ordered = faction.getGuildHandler().getGuilds();
    view.factionGuildsView(player, faction, (Inventory) null);
    assertEquals(ordered.get(0).getId(), id(top().getItem(0)));
    assertNotNull(top().getItem(53));
    click(53);
    assertEquals(1, ((SFInventoryHolder) top().getHolder()).getPage());
    Inventory page = top();
    String firstId = id(page.getItem(0));
    assertEquals(ordered.get(47).getId(), firstId);
    view.factionGuildsView(player, faction, page);
    assertSame(page, top());
    assertEquals(firstId, id(page.getItem(0)));
    click(45);
    assertEquals(0, ((SFInventoryHolder) top().getHolder()).getPage());
    click(0);
    verify(navigation.guildView).guildView(player, ordered.get(0), true);
    Inventory inventory = top();
    inventory.setItem(0, null);
    click(0);
    inventory.setItem(0, new ItemStack(Material.PAPER));
    click(0);
    ItemStack heading = new ItemStack(Material.PAPER);
    ItemMeta headingMeta = heading.getItemMeta();
    headingMeta.setDisplayName("Guild directory");
    heading.setItemMeta(headingMeta);
    inventory.setItem(0, heading);
    click(0);
    inventory.setItem(0, tag("deleted"));
    click(0);
    verifyNoMoreInteractions(navigation.guildView);
    inventory.setItem(0, tag("realm"));
    FactionManager.factions.clear();
    click(0);
    verifyNoMoreInteractions(navigation.guildView);
  }

  @Test
  void guildButtonOpensTheActualFactionGuildListAndIgnoresUnmarkedItems() {
    Inventory inventory = open();
    inventory.setItem(23, null);
    click(23);
    assertSame(inventory, top());
    inventory.setItem(23, new ItemStack(Material.PAPER));
    click(23);
    assertSame(inventory, top());
    view.guildCreator = mock(GuildCreator.class);
    when(view.guildCreator.createListItem(eq(player), any()))
        .thenAnswer(c -> tag(c.<Guild>getArgument(1).getId()));
    inventory.setItem(23, creator.createMenuItem(player, faction, MenuItemType.GUILDS));
    click(23);
    assertEquals(SFGUI.FACTION_GUILDS, ((SFInventoryHolder) top().getHolder()).getType());
    assertEquals("realm", id(top().getItem(0)));
  }

  @Test
  void menuClickProtectionBlocksTopAndTransferActionsButAllowsPlayerInventory() {
    Inventory inventory = open();
    InventoryClickEvent top = fixture.ui.click(player, 0);
    view.clickPreventions(top, inventory, player);
    assertTrue(top.isCancelled());
    InventoryClickEvent bottom = fixture.ui.click(player, 54);
    view.clickPreventions(bottom, inventory, player);
    assertFalse(bottom.isCancelled());
    for (InventoryAction action :
        List.of(InventoryAction.HOTBAR_SWAP, InventoryAction.HOTBAR_MOVE_AND_READD)) {
      InventoryClickEvent hotbar =
          new InventoryClickEvent(
              player.getOpenInventory(),
              org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
              54,
              ClickType.NUMBER_KEY,
              action,
              0);
      view.clickPreventions(hotbar, inventory, player);
      assertTrue(hotbar.isCancelled());
    }
    InventoryClickEvent shift =
        new InventoryClickEvent(
            player.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            54,
            ClickType.SHIFT_LEFT,
            InventoryAction.MOVE_TO_OTHER_INVENTORY);
    view.clickPreventions(shift, inventory, player);
    assertTrue(shift.isCancelled());
    InventoryClickEvent outside = fixture.ui.click(player, -999);
    view.clickPreventions(outside, inventory, player);
    assertFalse(outside.isCancelled());
    view.clickPreventions(bottom, null, player);
    view.clickPreventions(bottom, fixture.ui.inventory(null, 9, "Other"), player);
    assertFalse(bottom.isCancelled());
  }

  @Test
  void staleOrUnrelatedFactionHoldersCannotRouteActions() {
    InventoryManager navigation = navigation();
    Inventory inventory = open();
    FactionManager.factions.clear();
    inventory.setItem(11, tag("realm"));
    click(11);
    verifyNoInteractions(navigation);
    inventory = fixture.ui.inventory(null, 54, "§7Faction View");
    player.openInventory(inventory);
    click(11);
    verifyNoInteractions(navigation);
    inventory = fixture.ui.inventory(null, 9, "Other");
    player.openInventory(inventory);
    InventoryClickEvent event = fixture.ui.click(player, 0);
    view.click(event, inventory, player);
    assertFalse(event.isCancelled());
  }

  private InventoryManager navigation() {
    InventoryManager nav = mock(InventoryManager.class);
    nav.confirming = new HashMap<>();
    nav.guildView = mock(GuildView.class);
    when(nav.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.PAPER));
    clearInvocations(nav);
    view.inv = nav;
    return nav;
  }

  private Inventory top() {
    return player.getOpenInventory().getTopInventory();
  }

  private void click(int slot) {
    Inventory inventory = top();
    InventoryClickEvent event = fixture.ui.click(player, slot);
    view.click(event, inventory, player);
    assertTrue(event.isCancelled());
  }

  private ItemStack tag(String id) {
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(fixture.ui.plugin, "id"), PersistentDataType.STRING, id);
    item.setItemMeta(meta);
    return item;
  }

  private static String name(ItemStack item) {
    return ChatColor.stripColor(item.getItemMeta().getDisplayName());
  }

  private static String lore(ItemStack item) {
    return item.getItemMeta().getLore() == null
        ? ""
        : String.join(
            "\n", item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList());
  }

  private static void assertLore(ItemStack item, String text, boolean present) {
    assertEquals(
        present,
        lore(item).contains(text),
        () -> "Expected " + text + " present=" + present + " in " + lore(item));
  }

  private String id(ItemStack item) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(new NamespacedKey(fixture.ui.plugin, "id"), PersistentDataType.STRING);
  }

  private Inventory open() {
    Inventory inventory =
        fixture.ui.inventory(
            new SFInventoryHolder("realm", SFGUI.FACTION_VIEW), 54, "§7Faction View");
    player.openInventory(inventory);
    return inventory;
  }
}
