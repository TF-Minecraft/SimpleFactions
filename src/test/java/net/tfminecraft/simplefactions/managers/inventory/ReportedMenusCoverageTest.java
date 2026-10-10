package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.*;
import net.tfminecraft.simplefactions.espionage.*;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.managers.*;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/**
 * Actual report snapshots and faction state; only Paper and external item creation are isolated.
 */
class ReportedMenusCoverageTest {
  private FactionDomainFixture fixture;
  private Faction target, observer;
  private Guild guild;
  private Player viewer;
  private InventoryManager manager;
  private IntelligenceReport report;
  private MockedStatic<TLibs> items;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    fixture.lawGroup(
        "government",
        Map.of(
            "effects.faction.rules",
            List.of(
                "LEADER_ELECTIONS true",
                "CITIZEN_TAX true",
                "GUILD_TAX true",
                "VASSAL_TAX true",
                "DIVIDEND_TAX true",
                "TARIFFS true")));
    fixture.law("government", "other", Map.of("name", "Other policy"));
    fixture.regiment("levies", true, 4, 0);
    fixture.regiment("guards", false, 8, 20);
    for (int i = 0; i < 11; i++) {
      YamlConfiguration y = new YamlConfiguration();
      y.set("u.name", "Upgrade " + i);
      y.set("u.allowed-types", List.of("guild"));
      y.set("u.description", List.of("A public upgrade description"));
      UpgradeLoader.map.put(
          "upgrade" + i, new Upgrade("upgrade" + i, y.getConfigurationSection("u")));
    }
    target = fixture.saved("target", "ForeignLeader");
    target.getOrCreateMainGuild();
    guild = fixture.guild(target, "merchants", "GuildLeader");
    observer = fixture.saved("observer", "Viewer");
    viewer = fixture.player("Viewer");
    appoint(target, fixture.player("ForeignLeader"));
    appoint(observer, viewer);
    report = new IntelligenceReport();
    report.quality = "detailed";
    observer
        .getEspionage()
        .report(
            target.getId(),
            target.getFoundedAt(),
            LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            () -> report);
    manager = spy(new InventoryManager());
    doNothing().when(manager).installationDetailView(any(), any(), anyString());
    items = mockStatic(TLibs.class);
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator creator = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(creator);
    items.when(TLibs::getItemAPI).thenReturn(api);
    when(creator.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    net.tfminecraft.simplefactions.testsupport.EspionageModes.guardEverything();
    assertSame(report, EspionageService.report(viewer, target));
  }

  @AfterEach
  void close() {
    items.close();
    EspionageConfig.load(new YamlConfiguration());
    fixture.close();
  }

  private void appoint(Faction faction, Player player) {
    SpecialPositionAssignment spy = new SpecialPositionAssignment();
    spy.playerName = player.getName();
    spy.playerId = player.getUniqueId();
    spy.characterId = "character-" + player.getName();
    faction.getEspionage().appoint(spy, 80);
  }

  private Inventory menu(SFGUI type, String id, int size) {
    return ReportedMenus.open(viewer, id, type, size, "Foreign records");
  }

  private Inventory menu(SFGUI type) {
    return menu(type, target.getId(), 54);
  }

  private String text(ItemStack item) {
    if (item == null) return "";
    var meta = item.getItemMeta();
    return ChatColor.stripColor(
        meta.getDisplayName()
            + "\n"
            + String.join("\n", meta.getLore() == null ? List.of() : meta.getLore()));
  }

  private void estimate(String key, long lower, long upper) {
    report.estimates.put(key, new EspionageMath.Estimate(lower, upper));
  }

  private void click(Inventory inventory, int slot) {
    viewer.openInventory(inventory);
    InventoryClickEvent event = fixture.ui.click(viewer, slot);
    ReportedMenus.navigate(event, viewer, (SFInventoryHolder) inventory.getHolder(), manager);
  }

  private Inventory opened() {
    return viewer.getOpenInventory().getTopInventory();
  }

  @Test
  void maskingCopiesAppearanceAndStripsEveryMutationPayload() {
    ItemStack template = new ItemStack(Material.DIAMOND);
    var meta = template.getItemMeta();
    NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "secret-action");
    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "withdraw");
    meta.setDisplayName("Old label");
    template.setItemMeta(meta);
    ItemStack result =
        ReportedMenus.mask(template, "Read-only report", List.of("Estimated figures"));
    assertNotSame(template, result);
    assertEquals(Material.DIAMOND, result.getType());
    assertTrue(result.getItemMeta().getPersistentDataContainer().getKeys().isEmpty());
    assertEquals(
        "withdraw",
        template.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    assertEquals("Old label", template.getItemMeta().getDisplayName());
    assertTrue(text(result).contains("Estimated figures"));
  }

  @Test
  void reportedGuildLeaderUsesTheCachedNameWhileThePublicRealmLeaderRemainsVisible() {
    assertTrue(ReportedMenus.guildLeader(viewer, guild).contains("Unknown"));
    report.details.put("guild-leader:" + guild.getId(), List.of("Recorded Baron"));
    assertEquals("Recorded Baron", ReportedMenus.guildLeader(viewer, guild));
    assertEquals("ForeignLeader", ReportedMenus.guildLeader(viewer, target.getOrCreateMainGuild()));
    assertEquals("GuildLeader", ReportedMenus.guildLeader(fixture.player("GuildLeader"), guild));
  }

  @Test
  void reportedBranchUsesEstimatedLevelsAndNeverTheCurrentLiveLevel() {
    Branch branch = new Branch(guild.getBranch(0), 9876);
    assertTrue(text(ReportedMenus.branch(viewer, guild, branch)).contains("Unknown"));
    estimate(IntelligenceLedger.key(guild, "Branch:" + branch.getId()), 2, 4);
    String rendered = text(ReportedMenus.branch(viewer, guild, branch));
    assertFalse(rendered.contains("9876"));
    assertTrue(rendered.contains("2 to 4"), rendered);
    assertTrue(rendered.contains("Effects:"));
    assertTrue(
        ReportedMenus.branch(viewer, guild, branch)
            .getItemMeta()
            .getPersistentDataContainer()
            .getKeys()
            .isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void militaryAndUpgradeQueuesDiscloseOnlyRecordedEntries(boolean known) {
    if (known) {
      report.details.put("training", List.of("Recorded guards", "Recorded levies"));
      estimate("Training:0", 50, 60);
      report.details.put(
          "upgrading:" + guild.getId(), List.of("Recorded fortification", "Recorded storehouse"));
      estimate("Regiment:levies:Levies", 20, 30);
      estimate("Regiment:guards:Soldiers", 5, 8);
    } else observer.getEspionage().resetReportsAndRolls();
    Inventory military = menu(SFGUI.MILITARY_VIEW);
    ReportedMenus.military(military, viewer, target, manager);
    assertTrue(((SFInventoryHolder) military.getHolder()).isReported());
    assertEquals(Material.BARRIER, military.getItem(53).getType());
    assertTrue(text(military.getItem(39)).contains(known ? "Recorded guards" : "Unknown"));
    assertEquals(
        known ? null : Material.CLOCK,
        military.getItem(41) == null ? null : military.getItem(41).getType());
    Inventory upgrades = menu(SFGUI.UPGRADE_VIEW, guild.getId(), 54);
    ReportedMenus.upgrades(upgrades, viewer, guild, manager);
    assertNotNull(upgrades.getItem(9));
    assertNotNull(upgrades.getItem(17));
    assertNull(upgrades.getItem(18));
    assertTrue(text(upgrades.getItem(39)).contains(known ? "Recorded fortification" : "Unknown"));
    assertEquals(
        known ? null : Material.CLOCK,
        upgrades.getItem(41) == null ? null : upgrades.getItem(41).getType());
  }

  @Test
  void largeArmyPresentationKeepsQueueAndNavigationSlotsAvailable() {
    observer.getEspionage().resetReportsAndRolls();
    for (int i = 0; i < 35; i++)
      target.getMilitary().getRegiments().add(fixture.regiment("extra" + i, false, 1, 0));
    Inventory military = menu(SFGUI.MILITARY_VIEW);
    ReportedMenus.military(military, viewer, target, manager);
    assertNotNull(military.getItem(38));
    assertEquals(Material.CLOCK, military.getItem(39).getType());
    assertEquals(Material.MINECART, military.getItem(49).getType());
    assertEquals(Material.BARRIER, military.getItem(53).getType());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void governmentAndPolicyViewsRespectMissingAndPresentSnapshots(boolean known) {
    if (known) {
      estimate("Administrative power", 12, 15);
      estimate("Legitimacy", 70, 75);
      estimate("Stability", 80, 85);
      estimate("Council size", 3, 4);
      report.details.put("law:government", List.of("Other policy"));
    } else observer.getEspionage().resetReportsAndRolls();
    Inventory government = menu(SFGUI.GOVERNMENT_VIEW);
    ReportedMenus.government(government, viewer, target, manager);
    assertTrue(text(government.getItem(10)).contains("ForeignLeader"));
    assertTrue(text(government.getItem(13)).contains("Council members: Unknown"));
    assertTrue(text(government.getItem(11)).contains(known ? "80" : "Unknown"));
    Inventory laws = menu(SFGUI.LAW_VIEW);
    ReportedMenus.laws(laws, viewer, target, manager);
    assertTrue(text(laws.getItem(10)).contains(known ? "Other policy" : "Unknown"));
    click(laws, 10);
    Inventory selection = opened();
    assertEquals(SFGUI.LAW_SELECT, ((SFInventoryHolder) selection.getHolder()).getType());
    assertTrue(text(selection.getItem(0)).contains(known ? "No" : "Unknown"));
    assertTrue(text(selection.getItem(1)).contains(known ? "Yes" : "Unknown"));
  }

  @Test
  void installationDetailsStayInTheReportAndOmitCoordinatesAndVehicles() {
    report.details.put(
        "installations",
        List.of(
            "station\nTRAIN_STATION\nRecorded Station", "fort\nFORT\nRecorded Fort", "malformed"));
    report.details.put("construction", List.of("Recorded Project"));
    estimate("Installation:station:Level", 2, 3);
    Inventory list = menu(SFGUI.INSTALLATIONS_VIEW);
    ReportedMenus.installations(list, viewer, target, manager);
    assertEquals(Material.MINECART, list.getItem(12).getType());
    assertTrue(text(list.getItem(13)).contains("Recorded Fort"));
    assertTrue(text(list.getItem(39)).contains("Recorded Project"));
    click(list, 12);
    verify(manager).installationDetailView(viewer, target, "station");
    click(list, 13);
    verify(manager).installationDetailView(viewer, target, "fort");
    Inventory details = menu(SFGUI.INSTALLATION_DETAIL_VIEW);
    ReportedMenus.installationDetail(details, viewer, target, "station", manager);
    assertTrue(text(details.getItem(49)).contains("Recorded Station"));
    assertTrue(text(details.getItem(49)).contains("Coordinates: Unknown"));
    ReportedMenus.installationDetail(details, viewer, target, "fort", manager);
    assertEquals(Material.GREEN_CONCRETE, details.getItem(49).getType());
    ReportedMenus.installationDetail(details, viewer, target, "missing", manager);
    assertTrue(text(details.getItem(49)).contains("Installation Details"));
    observer.getEspionage().resetReportsAndRolls();
    ReportedMenus.installations(list, viewer, target, manager);
    assertEquals(Material.GRAY_CONCRETE, list.getItem(12).getType());
    assertTrue(text(list.getItem(39)).contains("Construction"));
    ReportedMenus.installationDetail(details, viewer, target, "station", manager);
    assertTrue(text(details.getItem(49)).contains("Level: Unknown"));
  }

  @Test
  void malformedReportedInstallationBeforeAValidOneDoesNotShiftItsClickTarget() {
    report.details.put(
        "installations", List.of("malformed", "station\nTRAIN_STATION\nRecorded Station"));
    Inventory list = menu(SFGUI.INSTALLATIONS_VIEW);
    ReportedMenus.installations(list, viewer, target, manager);
    assertTrue(text(list.getItem(12)).contains("Recorded Station"));
    click(list, 12);
    verify(manager).installationDetailView(viewer, target, "station");
  }

  @Test
  void installationConstructionCardDoesNotNavigateToAnUndisplayedInstallation() {
    List<String> entries = new ArrayList<>();
    for (int index = 0; index < 40; index++) entries.add("fort" + index + "\nFORT\nFort " + index);
    report.details.put("installations", entries);
    report.details.put("construction", List.of("Current project"));
    Inventory menu = menu(SFGUI.INSTALLATIONS_VIEW);
    ReportedMenus.installations(menu, viewer, target, manager);
    assertEquals(Material.YELLOW_CONCRETE, menu.getItem(39).getType());
    click(menu, 39);
    verify(manager, never()).installationDetailView(any(), any(), anyString());
  }

  @Test
  void taxReportsOpenReadOnlySpecificRatesAndKeepTheTreasuryUnchanged() {
    for (TaxTarget target : TaxTarget.values()) estimate("Tax:" + target.name(), 15, 20);
    Inventory taxes = menu(SFGUI.TAX_VIEW);
    ReportedMenus.taxes(taxes, viewer, target, manager);
    List<TaxTarget> types =
        Arrays.stream(TaxTarget.values()).filter(target.getTaxHandler()::canCollectTax).toList();
    double balance = target.getBank().getWealth();
    for (int i = 0; i < types.size(); i++) {
      assertTrue(text(taxes.getItem(i)).contains("Tax rate:"));
      click(taxes, i);
      if (types.get(i).name().endsWith("_ID")) {
        Inventory specific = opened();
        assertEquals(SFGUI.TAX_VIEW_SPECIFIC, ((SFInventoryHolder) specific.getHolder()).getType());
        assertTrue(((SFInventoryHolder) specific.getHolder()).isReported());
        assertTrue(text(specific.getItem(0)).contains("Unknown"));
      }
    }
    assertEquals(balance, target.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(ints = {13, 24, 33})
  void governmentNavigationOpensMaskedCouncilProposalsAndMovements(int slot) {
    Inventory menu = menu(SFGUI.GOVERNMENT_VIEW);
    ReportedMenus.government(menu, viewer, target, manager);
    click(menu, slot);
    Inventory next = opened();
    SFGUI expected =
        slot == 13 ? SFGUI.COUNCIL_VIEW : slot == 24 ? SFGUI.PROPOSALS : SFGUI.MOVEMENT_LIST;
    assertEquals(expected, ((SFInventoryHolder) next.getHolder()).getType());
    assertTrue(((SFInventoryHolder) next.getHolder()).isReported());
    assertTrue(text(next.getItem(slot == 24 ? 13 : 10)).contains("Unknown"));
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 4, 11, 13, 15, 22})
  void guildLoanAndCompanyNavigationNeverExposesLivePrivateRecords(int slot) {
    boolean loans = slot == 2 || slot == 4;
    Inventory menu =
        menu(loans ? SFGUI.LOAN_MAIN_VIEW : SFGUI.COMPANY_VIEW, guild.getId(), loans ? 9 : 27);
    if (loans) ReportedMenus.loans(menu, manager);
    else ReportedMenus.company(menu, manager);
    click(menu, slot);
    Inventory next = opened();
    assertTrue(((SFInventoryHolder) next.getHolder()).isReported());
    assertTrue(text(next.getItem(loans || slot == 22 ? 0 : 9)).contains("Unknown"));
    assertEquals(Material.BARRIER, next.getItem(53).getType());
  }

  @Test
  void unknownStaleEmptyAndBackClicksLeaveTheCurrentMenuInPlace() {
    Inventory menu = menu(SFGUI.STABILITY_VIEW);
    menu.setItem(3, new ItemStack(Material.PAPER));
    click(menu, 3);
    assertSame(menu, opened());
    click(menu, 2);
    assertSame(menu, opened());
    menu.setItem(3, new ItemStack(Material.BARRIER));
    click(menu, 3);
    assertSame(menu, opened());
    Inventory missing = menu(SFGUI.LAW_VIEW, "deleted", 54);
    missing.setItem(10, new ItemStack(Material.PAPER));
    click(missing, 10);
    assertSame(missing, opened());
  }
}
