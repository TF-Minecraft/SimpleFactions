package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.enums.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.*;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.inventory.EspionageView;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Public foreign reports and office workflows using actual faction, report and office state. */
class EspionageMenusCoverageTest {
  @TempDir Path directory;
  private FactionDomainFixture fixture;
  private Faction home, target;
  private Player leader, spy, foreignLeader, visitor;
  private Guild guild;
  private InventoryManager manager;
  private IntelligenceReport report;
  private Field aptitudeField;
  private Object oldAptitudes;
  private MockedStatic<OfficeCharacters> characters;
  private MockedStatic<TLibs> items;
  private MockedStatic<net.tfminecraft.rpcharacters.calendar.FantasyCalendar> calendar;
  private MockedConstruction<Database> databases;

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    home = fixture.saved("home", "Leader");
    home.getOrCreateMainGuild();
    home.addMember("Spy");
    target = fixture.saved("target", "ForeignLeader");
    target.getOrCreateMainGuild();
    guild = fixture.guild(target, "merchants", "Baron");
    leader = fixture.player("Leader");
    spy = fixture.player("Spy");
    foreignLeader = fixture.player("ForeignLeader");
    visitor = fixture.player("Visitor");
    characters = mockStatic(OfficeCharacters.class);
    characters
        .when(() -> OfficeCharacters.activeCharacterId(any()))
        .thenAnswer(
            call ->
                call.getArgument(0) == null
                    ? null
                    : "character-" + ((Player) call.getArgument(0)).getName());
    characters.when(() -> OfficeCharacters.attributes(any())).thenReturn(Map.of());
    databases =
        mockConstruction(
            Database.class,
            (database, context) -> when(database.saveFactionChecked(any())).thenReturn(true));
    aptitudeField = EspionageService.class.getDeclaredField("characterAptitudes");
    aptitudeField.setAccessible(true);
    oldAptitudes = aptitudeField.get(null);
    EspionageService.loadAptitudes(directory.resolve("aptitudes.json"));
    assign(home, spy, false, 0);
    assign(target, foreignLeader, false, 0);
    report = new IntelligenceReport();
    report.quality = "detailed";
    home.getEspionage()
        .report(
            target.getId(),
            target.getFoundedAt(),
            LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            () -> report);
    manager = spy(new InventoryManager());
    doNothing().when(manager).factionView(any(), any());
    doNothing().when(manager).guildView(any(), any());
    items = mockStatic(TLibs.class);
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator creator = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(creator);
    items.when(TLibs::getItemAPI).thenReturn(api);
    when(creator.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    calendar = mockStatic(net.tfminecraft.rpcharacters.calendar.FantasyCalendar.class);
    calendar
        .when(() -> net.tfminecraft.rpcharacters.calendar.FantasyCalendar.formatDate(anyLong()))
        .thenReturn("7 Harvest, 322 AE");
    when(Bukkit.getOfflinePlayer(any(UUID.class)))
        .thenAnswer(
            call -> {
              OfflinePlayer p = mock(OfflinePlayer.class);
              when(p.getUniqueId()).thenReturn(call.getArgument(0));
              return p;
            });
    net.tfminecraft.simplefactions.testsupport.EspionageModes.guardEverything();
  }

  @AfterEach
  void close() throws Exception {
    try {
      calendar.close();
      items.close();
      characters.close();
      databases.close();
      aptitudeField.set(null, oldAptitudes);
      EspionageConfig.load(new YamlConfiguration());
    } finally {
      fixture.close();
    }
  }

  private SpecialPositionAssignment assign(
      Faction faction, Player player, boolean automatic, long time) {
    faction.getEspionage().removeSpymaster();
    SpecialPositionAssignment assignment = new SpecialPositionAssignment();
    assignment.playerId = player.getUniqueId();
    assignment.playerName = player.getName();
    assignment.characterId = "character-" + player.getName();
    assignment.automatic = automatic;
    faction.getEspionage().appoint(assignment, 80, time);
    return assignment;
  }

  private Inventory top(Player player) {
    return player.getOpenInventory().getTopInventory();
  }

  private SFGUI kind(Player player) {
    return ((SFInventoryHolder) top(player).getHolder()).getType();
  }

  private String text(ItemStack item) {
    if (item == null) return "";
    var meta = item.getItemMeta();
    return ChatColor.stripColor(
        meta.getDisplayName()
            + "\n"
            + String.join("\n", meta.getLore() == null ? List.of() : meta.getLore()));
  }

  private void click(Player player, int slot) {
    InventoryClickEvent e = fixture.ui.click(player, slot);
    EspionageView.click(e, player, (SFInventoryHolder) top(player).getHolder(), manager);
    assertTrue(e.isCancelled());
  }

  private Inventory inventory(String id, SFGUI type, int size) {
    return fixture.ui.inventory(new SFInventoryHolder(id, type), size, "test");
  }

  @Test
  void foreignFactionAndGuildMenusUseTheDatedReportWithoutRevealingCurrentBalances() {
    target.getBank().deposit(1234567.0);
    guild.getBank().deposit(7654321.0);
    report.estimates.put("Wealth", new EspionageMath.Estimate(100, 150));
    report.estimates.put(
        IntelligenceLedger.key(guild, "Wealth"), new EspionageMath.Estimate(200, 250));
    Inventory factionMenu = inventory(target.getId(), SFGUI.FACTION_VIEW, 54);
    EspionageView.foreign(factionMenu, leader, target, manager);
    assertTrue(text(factionMenu.getItem(4)).contains("7 Harvest"));
    assertTrue(text(factionMenu.getItem(4)).contains("Spy"));
    assertFalse(text(factionMenu.getItem(12)).contains("1234567"));
    assertTrue(text(factionMenu.getItem(12)).contains("100 to 150"));
    Inventory guildMenu = inventory(guild.getId(), SFGUI.GUILD_VIEW, 54);
    EspionageView.guild(guildMenu, leader, guild, manager);
    assertFalse(text(guildMenu.getItem(12)).contains("7654321"));
    assertTrue(text(guildMenu.getItem(17)).contains("Dividends"));
    assertNotNull(EspionageView.directoryItem(leader, target));
    assertEquals(Material.ENDER_EYE, EspionageView.positionButton().getType());
    assertSame(report, EspionageService.report(leader, target));
  }

  @Test
  void missingReportsAndMissingObserverSpymastersHaveDistinctExplanations() {
    home.getEspionage().resetReportsAndRolls();
    Inventory menu = inventory(target.getId(), SFGUI.FACTION_VIEW, 54);
    EspionageView.foreign(menu, leader, target, manager);
    assertTrue(text(menu.getItem(4)).contains("No dated account"));
    assertFalse(text(menu.getItem(4)).contains("Absent"));
    EspionageView.foreign(menu, visitor, target, manager);
    assertTrue(text(menu.getItem(4)).contains("Absent"));
    EspionageView.guild(menu, visitor, target.getOrCreateMainGuild(), manager);
    assertNull(menu.getItem(17));
  }

  @Test
  void allPrivateFactionCardsUseOnlyPermittedReportedMetrics() {
    for (MenuItemType type :
        List.of(
            MenuItemType.WEALTH,
            MenuItemType.PRESTIGE,
            MenuItemType.MEMBERS,
            MenuItemType.MODIFIERS,
            MenuItemType.TAX,
            MenuItemType.LAWS,
            MenuItemType.MILITARY,
            MenuItemType.INSTALLATIONS,
            MenuItemType.DIPLOMACY)) {
      ItemStack card = EspionageView.factionItem(leader, target, type);
      assertNotNull(card);
      assertFalse(text(card).isBlank());
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> EspionageView.factionItem(leader, target, MenuItemType.BANNER));
    report.estimates.put("Members", new EspionageMath.Estimate(3, 4));
    assertTrue(
        text(EspionageView.factionItem(leader, target, MenuItemType.MEMBERS)).contains("3 to 4"));
  }

  @Test
  void foreignLedgersKeepPersonalGuildHistoryHiddenAndNavigateBackToTheRightOwner() {
    EspionageView.foreignLedger(leader, guild, manager);
    assertNull(top(leader).getItem(10));
    click(leader, 26);
    verify(manager).guildView(leader, guild);
    Guild base = target.getOrCreateMainGuild();
    EspionageView.foreignLedger(leader, base, manager);
    assertNotNull(top(leader).getItem(10));
    assertTrue(text(top(leader).getItem(15)).contains("Unknown"));
    click(leader, 26);
    verify(manager).factionView(leader, target);
    leader.openInventory(inventory("deleted", SFGUI.FOREIGN_LEDGER_VIEW, 27));
    click(leader, 26);
    verify(manager, times(1)).guildView(any(), any());
  }

  @Test
  void foreignOfficeViewsUseReportsWhileOwnMembersCanSeeTheExactHolder() {
    report.officeHolders.put(SpecialPosition.SPYMASTER, "Recorded Spy");
    report.estimates.put(
        IntelligenceReport.officeAptitudeKey(SpecialPosition.SPYMASTER),
        new EspionageMath.Estimate(60, 65));
    EspionageView.positions(leader, target, manager);
    assertEquals(SFGUI.FOREIGN_POSITIONS_VIEW, kind(leader));
    assertTrue(text(top(leader).getItem(13)).contains("Recorded Spy"));
    click(leader, 13);
    assertEquals(SFGUI.FOREIGN_POSITIONS_VIEW, kind(leader));
    click(leader, 26);
    verify(manager).factionView(leader, target);
    EspionageView.foreignPositions(foreignLeader, target, manager);
    assertTrue(text(top(foreignLeader).getItem(4)).contains("Exact information"));
    assertTrue(text(top(foreignLeader).getItem(13)).contains("80/100"));
    home.getEspionage().resetReportsAndRolls();
    assertTrue(text(EspionageView.foreignPositionsItem(leader, target)).contains("Unknown"));
    target.getEspionage().removeSpymaster();
    assertTrue(text(EspionageView.foreignPositionsItem(foreignLeader, target)).contains("Vacant"));
    FactionManager.factions.remove(target);
    click(leader, 13);
    verify(leader).closeInventory();
  }

  @Test
  void officeNavigationSeparatesPublicOfficeAndPrivateConduct() {
    EspionageView.positions(leader, home, manager);
    assertEquals(SFGUI.SPECIAL_POSITIONS, kind(leader));
    click(leader, 10);
    assertEquals(SFGUI.SPYMASTER_VIEW, kind(leader));
    assertNotNull(top(leader).getItem(11));
    assertNotNull(top(leader).getItem(15));
    assertTrue(text(top(leader).getItem(13)).contains("Spymaster: Spy"));
    Inventory publicOffice = top(leader);
    click(leader, 13);
    assertSame(publicOffice, top(leader));
    EspionageView.spymasterOffice(spy, home, manager);
    assertNull(top(spy).getItem(11));
    click(spy, 13);
    assertEquals(SFGUI.SPYMASTER_SETTINGS, kind(spy));
    click(spy, 26);
    assertEquals(SFGUI.SPYMASTER_VIEW, kind(spy));
    click(spy, 26);
    assertEquals(SFGUI.SPECIAL_POSITIONS, kind(spy));
    click(spy, 26);
    verify(manager).factionView(spy, home);
    EspionageView.spymasterOffice(visitor, home, manager);
    verify(visitor, never()).openInventory(any(Inventory.class));
  }

  @Test
  void privateConductCyclesEachSettingAndPreservesReportsAndTheOtherSetting() {
    EspionageView.settings(spy, home, manager);
    SpecialPositionAssignment assignment = home.getEspionage().getSpymaster();
    for (int expected : new int[] {25, 50, 75, 100, 0}) {
      click(spy, 11);
      assertEquals(expected, assignment.offenseReduction);
      assertEquals(0, assignment.defenseReduction);
    }
    click(spy, 15);
    assertEquals(25, assignment.defenseReduction);
    assertEquals(0, assignment.offenseReduction);
    assertSame(report, EspionageService.report(leader, target));
    assertEquals(Material.RED_DYE, top(spy).getItem(15).getType());
    assertEquals(Material.LIME_DYE, top(spy).getItem(11).getType());
    assertTrue(text(top(spy).getItem(11)).contains("Faithful service"));
    assertTrue(text(top(spy).getItem(15)).contains("Loose lips"));
    assertTrue(text(top(spy).getItem(15)).contains("Guarding roll: -25"));
  }

  @Test
  void sharingCyclesEachPartnerAndReportsNameTheSharedTierWhileSharingIsAllowed() {
    EspionageView.settings(spy, home, manager);
    assertTrue(text(top(spy).getItem(21)).contains("Sharing nothing"));
    for (IntelligenceTier expected :
        new IntelligenceTier[] {
          IntelligenceTier.RUMOURS,
          IntelligenceTier.BROAD,
          IntelligenceTier.RELIABLE,
          IntelligenceTier.DETAILED,
          IntelligenceTier.UNKNOWN
        }) {
      click(spy, 21);
      assertEquals(expected, home.getEspionage().sharing(SharingPartner.OVERLORD));
    }
    assertTrue(text(top(spy).getItem(22)).contains("no allies"));
    click(spy, 22);
    assertEquals(IntelligenceTier.RUMOURS, home.getEspionage().sharing(SharingPartner.ALLIES));
    assertTrue(text(top(spy).getItem(22)).contains("Sharing up to Rumours"));
    click(spy, 23);
    assertEquals(IntelligenceTier.RUMOURS, home.getEspionage().sharing(SharingPartner.VASSALS));
    assertTrue(text(top(spy).getItem(23)).contains("Sharing up to Rumours"));
    report.shared = "broad";
    EspionageView.foreignPositions(leader, target, manager);
    assertTrue(
        text(top(leader).getItem(4)).contains("shares everything up to Broad estimates exactly"));
    YamlConfiguration config = new YamlConfiguration();
    config.set(net.tfminecraft.simplefactions.testsupport.EspionageModes.MILITARY_ONLY, false);
    config.set("espionage.vassalage.allow-sharing", false);
    EspionageConfig.load(config);
    EspionageView.foreignPositions(leader, target, manager);
    assertFalse(text(top(leader).getItem(4)).contains("shares everything"));
    EspionageView.settings(spy, home, manager);
    assertNull(top(spy).getItem(21));
  }

  @Test
  void staleOfficeOwnershipAndFactionMembershipClosePrivateMenus() {
    EspionageView.settings(spy, home, manager);
    assign(home, leader, false, 0);
    click(spy, 11);
    verify(spy).closeInventory();
    assertEquals(0, home.getEspionage().getSpymaster().offenseReduction);
    EspionageView.positions(leader, home, manager);
    FactionManager.factions.remove(home);
    click(leader, 10);
    verify(leader).closeInventory();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void officeStatusExplainsNetworkBuildUpAndTheAppointmentCooldown(boolean building) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("espionage.appointments.build-up-days", building ? 2 : 0);
    EspionageConfig.load(config);
    assign(home, spy, false, building ? System.currentTimeMillis() : 0);
    EspionageView.spymasterOffice(leader, home, manager);
    assertTrue(text(top(leader).getItem(13)).contains(building ? "building" : "established"));
    assertTrue(
        text(top(leader).getItem(11))
            .contains(building ? "Next appointment" : "appointment can be made"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"stability-penalty", "penalty-days"})
  void appointmentTermsReflectTheFirstAppointmentAndDisabledUnrest(String disabledSetting) {
    home.setEspionage(new EspionageState());
    EspionageView.spymasterOffice(leader, home, manager);
    assertTrue(text(top(leader).getItem(11)).contains("first appointment brings no unrest"));
    YamlConfiguration config = new YamlConfiguration();
    config.set("espionage.appointments." + disabledSetting, 0);
    EspionageConfig.load(config);
    assign(home, spy, false, 0);
    EspionageView.spymasterOffice(leader, home, manager);
    assertTrue(text(top(leader).getItem(11)).contains("Changing Spymaster brings no unrest"));
  }

  @Test
  void candidatePagesClampAndRevalidateLeadershipWhenTheMenuIsClicked() {
    for (int i = 0; i < 50; i++) home.addMember("Member" + i);
    EspionageView.spymasterOffice(leader, home, manager);
    click(leader, 11);
    assertEquals(SFGUI.SPYMASTER_SELECT, kind(leader));
    assertNotNull(top(leader).getItem(52));
    click(leader, 52);
    assertEquals(1, ((SFInventoryHolder) top(leader).getHolder()).getPage());
    assertNotNull(top(leader).getItem(45));
    click(leader, 45);
    assertEquals(0, ((SFInventoryHolder) top(leader).getHolder()).getPage());
    click(leader, 53);
    assertEquals(SFGUI.SPYMASTER_VIEW, kind(leader));
    click(leader, 11);
    home.setLeader("Spy");
    click(leader, 0);
    verify(leader).closeInventory();
  }

  @Test
  void selectingAnOnlineCharacterAppointsAndRemovalReturnsTheOfficeToTheLeader() {
    home.addMember("NewSpy");
    Player candidate = fixture.player("NewSpy");
    EspionageView.spymasterOffice(leader, home, manager);
    click(leader, 11);
    int candidateSlot = -1;
    for (int i = 0; i < 45; i++)
      if (text(top(leader).getItem(i)).contains("NewSpy")) candidateSlot = i;
    assertTrue(candidateSlot >= 0);
    click(leader, candidateSlot);
    assertEquals("NewSpy", home.getEspionage().getSpymaster().playerName);
    assertEquals(SFGUI.SPYMASTER_VIEW, kind(leader));
    click(leader, 15);
    assertEquals("Leader", home.getEspionage().getSpymaster().playerName);
    assertTrue(home.getEspionage().getSpymaster().automatic);
    assertTrue(text(top(leader).getItem(13)).contains("Held by the faction leader"));
  }

  @Test
  void unownedOfficeAndUnknownMenuTypesDoNotExposePrivateControls() {
    characters.when(() -> OfficeCharacters.activeCharacterId(any())).thenReturn(null);
    home.getEspionage().removeSpymaster();
    EspionageView.spymasterOffice(leader, home, manager);
    assertTrue(text(top(leader).getItem(13)).contains("Vacant"));
    Inventory office = top(leader);
    click(leader, 13);
    assertSame(office, top(leader));
    click(leader, 15);
    assertSame(office, top(leader));
    for (SFGUI type : SFGUI.values())
      assertEquals(
          Set.of(
                  SFGUI.SPECIAL_POSITIONS,
                  SFGUI.SPYMASTER_VIEW,
                  SFGUI.SPYMASTER_SETTINGS,
                  SFGUI.SPYMASTER_SELECT,
                  SFGUI.FOREIGN_LEDGER_VIEW,
                  SFGUI.FOREIGN_POSITIONS_VIEW)
              .contains(type),
          EspionageView.handles(type));
  }
}
