package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationConstruction;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleUnberthService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.freeze.PreparationFreeze;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class InstallationMenusCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Player leader;
  private InventoryManager navigation;
  private InstallationView view;
  private PlayerVehicleRegistry registry;
  private VehicleManager vehicles;
  private InstallationVehicleUnberthService unberth;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    faction = fixture.saved("realm", "Leader");
    faction.getOrCreateMainGuild();
    faction.addProvince(10);
    leader = fixture.player("Leader");
    navigation = mock(InventoryManager.class);
    navigation.confirming = new HashMap<>();
    navigation.installationConfirmFromCommand = new HashMap<>();
    when(navigation.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.BARRIER));
    view = new InstallationView(navigation);
    registry = new PlayerVehicleRegistry();
    MockedStatic<SimpleFactions> plugin = mockStatic(SimpleFactions.class, CALLS_REAL_METHODS);
    scopes.add(plugin);
    plugin.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
    vehicles = mock(VehicleManager.class);
    when(vehicles.getOfflineLocation(anyString())).thenReturn(Optional.empty());
    when(vehicles.readStoredVehicle(anyString())).thenReturn(Optional.empty());
    scope(VehicleFramework.class).when(VehicleFramework::getVehicleManager).thenReturn(vehicles);
    unberth = mock(InstallationVehicleUnberthService.class);
    when(fixture.ui.plugin.getInstallationVehicleUnberthService()).thenReturn(unberth);
    when(unberth.unberth(any(), anyString(), any(), anyString()))
        .thenReturn(
            new InstallationVehicleUnberthService.UnberthOutcome(
                InstallationVehicleUnberthService.UnberthResult.NOT_BERTHED, null, null));
    MockedStatic<InstallationConfigLoader> config = scope(InstallationConfigLoader.class);
    config.when(() -> InstallationConfigLoader.getMaximumLevel(any())).thenReturn(3);
    config
        .when(() -> InstallationConfigLoader.getDailyUpkeep(any(), anyInt()))
        .thenAnswer(call -> 5.0 * (Integer) call.getArgument(1));
    config
        .when(() -> InstallationConfigLoader.getConstructionTimeSeconds(any(), anyInt()))
        .thenReturn(120);
    config
        .when(() -> InstallationConfigLoader.getCategorySlots(any(), anyInt()))
        .thenReturn(Map.of("heavy_vehicles", 2));
  }

  @AfterEach
  void close() {
    try {
      for (int i = scopes.size() - 1; i >= 0; i--) scopes.get(i).close();
    } finally {
      fixture.close();
    }
  }

  @Test
  void deconstructButtonOpensTheConfirmationInsteadOfTreatingItsIdAsAVehicle() {
    Installation fort = installation("fort", InstallationKind.FORT);
    view.installationDetailView(leader, faction, fort.getId());
    Inventory menu = top();
    view.click(fixture.ui.click(leader, 11), menu, leader);
    verify(navigation).confirmView(leader, faction, "installation", fort.getId());
    assertSame(faction, navigation.confirming.get(leader));
    assertEquals(false, navigation.installationConfirmFromCommand.get(leader));
    verifyNoInteractions(unberth);
    assertSame(fort, faction.getInstallationHandler().getById(fort.getId()));
  }

  @Test
  void theReservedQueueSlotDoesNotHideAnOtherwiseVisibleInstallation() {
    Set<String> expected = new HashSet<>();
    for (int i = 0; i < 29; i++) {
      String id = String.format("fort_%02d", i);
      faction.addProvince(10 + i);
      Installation installation =
          new Installation(id, id, InstallationKind.FORT, 10 + i, i * 100, 0, 1L);
      faction.getInstallationHandler().acceptTransferred(installation);
      expected.add(id);
    }
    view.installationsView(null, leader, faction, true);
    Set<String> visible = new HashSet<>();
    for (ItemStack item : top().getContents()) {
      if (item == null || !item.hasItemMeta()) continue;
      String id =
          item.getItemMeta()
              .getPersistentDataContainer()
              .get(Keys.STRING_KEY, PersistentDataType.STRING);
      if (id != null) visible.add(id);
    }
    assertEquals(expected, visible);
    assertEquals(Material.AIR, top().getItem(39).getType());
  }

  @Test
  void aFormerLeaderCannotRequestDeconstructionFromAnOpenDetailMenu() {
    Installation fort = installation("fort", InstallationKind.FORT);
    view.installationDetailView(leader, faction, fort.getId());
    Inventory menu = top();
    faction.addMember("Successor");
    faction.setLeader("Successor");
    view.click(fixture.ui.click(leader, 11), menu, leader);
    verify(navigation, never()).confirmView(any(), any(), anyString(), anyString());
    verifyNoInteractions(unberth);
    assertSame(fort, faction.getInstallationHandler().getById(fort.getId()));
  }

  @Test
  void installationListSummarizesAllKindsAndSortsOperationalRecords() {
    for (InstallationKind kind : InstallationKind.values()) installation(kind.name(), kind);
    view.installationsView(null, leader, faction, true);
    Inventory menu = top();
    assertEquals(SFGUI.INSTALLATIONS_VIEW, ((SFInventoryHolder) menu.getHolder()).getType());
    String summary = lore(menu.getItem(10));
    assertTrue(summary.contains("Total: 4"));
    assertTrue(summary.contains("Forts: 1 Ports: 1 Airports: 1 Train Stations: 1"));
    assertTrue(summary.contains("Total Upkeep: 20.0d/day"));
    assertEquals("AIRPORT", data(menu.getItem(12), Keys.STRING_KEY));
    assertEquals("FORT", data(menu.getItem(13), Keys.STRING_KEY));
    assertEquals("PORT", data(menu.getItem(14), Keys.STRING_KEY));
    assertEquals("TRAIN_STATION", data(menu.getItem(15), Keys.STRING_KEY));
    assertEquals(Material.MINECART, menu.getItem(15).getType());
    assertEquals(Material.BARRIER, menu.getItem(53).getType());
    view.click(fixture.ui.click(leader, 12), menu, leader);
    assertEquals("AIRPORT", ((SFInventoryHolder) top().getHolder()).getSecondaryId());
  }

  @Test
  void listCapacityAndRefreshKeepControlsAndRemoveVanishedInstallations() {
    for (int n = 0; n < 35; n++) {
      faction.addProvince(10 + n);
      faction
          .getInstallationHandler()
          .acceptTransferred(
              new Installation(
                  String.format("fort_%02d", n),
                  "Fort " + n,
                  InstallationKind.FORT,
                  10 + n,
                  100 * n,
                  0,
                  1L));
    }
    pending("port", InstallationKind.PORT, false);
    view.installationsView(null, leader, faction, true);
    Inventory menu = top();
    int visible = 0;
    for (int slot = 12; slot <= 44; slot++) {
      if (slot != 39 && data(menu.getItem(slot), Keys.STRING_KEY) != null) visible++;
    }
    assertEquals(32, visible);
    assertEquals("port", data(menu.getItem(39), Keys.STRING_KEY));
    assertNotNull(data(menu.getItem(39), Keys.QUEUE_CANCEL));
    for (int n = 1; n < 35; n++) faction.getInstallationHandler().detachOnProvince(10 + n);
    faction.getInstallationHandler().cancelPending("port");
    view.installationsView(menu, leader, faction, false);
    assertEquals("fort_00", data(menu.getItem(12), Keys.STRING_KEY));
    for (int slot = 13; slot <= 44; slot++)
      assertEquals(Material.AIR, menu.getItem(slot).getType());
    assertSame(menu, top());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void constructionQueueConfirmsCancellationOnlyForTheCurrentLeader(boolean stillLeader) {
    InstallationConstruction queued = pending("port", InstallationKind.PORT, false);
    view.installationsView(null, leader, faction, true);
    Inventory menu = top();
    String payload = data(menu.getItem(39), Keys.QUEUE_CANCEL);
    assertEquals(QueueCancelPayload.installation(faction.getId(), queued.getId()), payload);
    assertTrue(name(menu.getItem(39)).contains("Building"));
    if (!stillLeader) faction.setLeader("Successor");
    InventoryClickEvent click = fixture.ui.click(leader, 39);
    view.click(click, menu, leader);
    assertTrue(click.isCancelled());
    if (stillLeader)
      verify(navigation).openQueueCancelConfirm(leader, faction, payload, "§eCancel construction?");
    else verify(navigation, never()).openQueueCancelConfirm(any(), any(), anyString(), anyString());
    assertSame(queued, faction.getInstallationHandler().getPendingConstruction());
  }

  @ParameterizedTest
  @ValueSource(strings = {"pending", "upgrade", "maximum", "ordinary"})
  void detailsDisplayBuildStateAndApplicableLeaderActions(String state) {
    Installation fort = null;
    if (!state.equals("pending")) {
      fort =
          new Installation(
              "fort",
              "Fort",
              InstallationKind.FORT,
              10,
              12,
              34,
              1L,
              state.equals("maximum") ? 3 : 1);
      faction.getInstallationHandler().acceptTransferred(fort);
    }
    if (state.equals("pending") || state.equals("upgrade"))
      pending("fort", InstallationKind.FORT, state.equals("upgrade"));
    view.installationDetailView(leader, faction, "fort");
    Inventory menu = top();
    assertEquals("fort", data(menu.getItem(49), Keys.STRING_KEY));
    assertTrue(lore(menu.getItem(49)).contains("Province: 10"));
    assertTrue(lore(menu.getItem(49)).contains("Coords: 12, 34"));
    assertEquals(Material.BARRIER, menu.getItem(53).getType());
    if (state.equals("pending")) {
      assertEquals("Cancel Construction", name(menu.getItem(11)));
      assertEquals(Material.AIR, menu.getItem(13).getType());
      assertTrue(lore(menu.getItem(49)).contains("Under construction"));
    } else {
      assertEquals("Deconstruct", name(menu.getItem(11)));
      assertTrue(
          lore(menu.getItem(49))
              .contains("Upkeep: " + (state.equals("maximum") ? "15.0" : "5.0") + "d/day"));
      if (state.equals("maximum")) assertEquals(Material.AIR, menu.getItem(13).getType());
      else if (state.equals("upgrade")) {
        assertEquals("Cancel Upgrade", name(menu.getItem(13)));
        assertTrue(lore(menu.getItem(49)).contains("Upgrading to level 2"));
        assertTrue(lore(menu.getItem(13)).contains("Target level: 2"));
      } else {
        assertEquals("Upgrade", name(menu.getItem(13)));
        assertTrue(lore(menu.getItem(13)).contains("Next level: 2"));
        assertTrue(lore(menu.getItem(13)).contains("Upkeep: 10.0d/day"));
      }
    }
    faction.setLeader("Successor");
    view.installationDetailView(leader, faction, "fort", menu);
    assertSame(menu, top());
    assertEquals(Material.AIR, menu.getItem(11).getType());
    assertEquals(Material.AIR, menu.getItem(13).getType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"upgrade", "cancel_upgrade", "stale_leader"})
  void upgradeClickSelectsTheCurrentConstructionActionWithoutChangingItsQueue(String action) {
    Installation fort = installation("fort", InstallationKind.FORT);
    InstallationConstruction queued =
        action.equals("cancel_upgrade") ? pending("fort", InstallationKind.FORT, true) : null;
    view.installationDetailView(leader, faction, "fort");
    Inventory menu = top();
    if (action.equals("stale_leader")) faction.setLeader("Successor");
    view.click(fixture.ui.click(leader, 13), menu, leader);
    if (action.equals("stale_leader")) {
      verify(navigation, never()).confirmView(any(), any(), anyString(), anyString());
      assertFalse(navigation.confirming.containsKey(leader));
    } else {
      verify(navigation).confirmView(leader, faction, "installation_" + action, "fort");
      assertSame(faction, navigation.confirming.get(leader));
    }
    assertEquals(1, fort.getLevel());
    assertSame(queued, faction.getInstallationHandler().getPendingConstruction());
  }

  @Test
  void missingDetailsReturnToListAndOnlyNewRequestsSendAnError() {
    view.installationDetailView(leader, faction, "deleted");
    assertEquals(SFGUI.INSTALLATIONS_VIEW, ((SFInventoryHolder) top().getHolder()).getType());
    verify(leader).sendMessage("§cNo installation with id §fdeleted");
    clearInvocations(leader);
    view.installationDetailView(leader, faction, "deleted", top());
    verify(leader, never()).sendMessage(anyString());
    assertEquals(SFGUI.INSTALLATIONS_VIEW, ((SFInventoryHolder) top().getHolder()).getType());
  }

  @Test
  void constructionAndUpgradeItemsExplainTargetsFrozenTimersAndConfiguredCategoryNames() {
    Installation fort = installation("fort", InstallationKind.FORT);
    InstallationConstruction upgrade = pending("fort", InstallationKind.FORT, true);
    MockedStatic<PreparationFreeze> freeze =
        mockStatic(PreparationFreeze.class, CALLS_REAL_METHODS);
    scopes.add(freeze);
    freeze
        .when(() -> PreparationFreeze.frozenUntil(eq(faction), any(Instant.class)))
        .thenReturn(Instant.now().plusSeconds(3600));
    scope(VehiclesConfigLoader.class)
        .when(() -> VehiclesConfigLoader.getCategoryDisplayName("heavy_vehicles"))
        .thenReturn("Heavy Armour");
    ItemStack queued = view.creator.createConstructionIcon(upgrade, faction);
    assertTrue(lore(queued).contains("Upgrade to level: 2"));
    assertTrue(lore(queued).contains("Frozen: battle postponed"));
    assertEquals(
        QueueCancelPayload.installation(faction.getId(), "fort"), data(queued, Keys.QUEUE_CANCEL));
    assertTrue(
        lore(view.creator.createConstructionDetailItem(upgrade, fort))
            .contains("Upgrade to level: 2"));
    assertTrue(lore(view.creator.createDetailItem(fort)).contains("Heavy Armour slots: 2"));
    assertTrue(lore(view.creator.createUpgradeButton(fort)).contains("Heavy Armour slots: 2"));
    assertEquals("Cancel Upgrade", name(view.creator.createDeconstructButton("fort", true, true)));
    assertTrue(
        lore(view.creator.createConstructionDetailItem(upgrade)).contains("Status: Upgrading"));
    faction.getInstallationHandler().detachOnProvince(10);
    ItemStack orphanedQueue = view.creator.createConstructionIcon(upgrade, faction);
    assertFalse(lore(orphanedQueue).contains("Upgrade to level:"));
    assertEquals("fort", data(orphanedQueue, Keys.STRING_KEY));
  }

  @Test
  void detailVehicleCapacityPreservesEveryControlAndRefreshDropsRemovedRecords() {
    Installation fort = installation("fort", InstallationKind.FORT);
    for (int n = 0; n < 43; n++) berth("vehicle-" + n, String.format("type_%02d", n), fort);
    view.installationDetailView(leader, faction, "fort");
    Inventory menu = top();
    for (int n = 0; n < 42; n++) {
      ItemStack item = menu.getItem(InstallationView.berthedVehicleSlot(n));
      assertEquals("vehicle-" + n, data(item, Keys.STRING_KEY));
      assertTrue(lore(item).contains("Click to take as your vehicle"));
    }
    assertNull(menu.getItem(15));
    assertEquals("Deconstruct", name(menu.getItem(11)));
    assertEquals("Upgrade", name(menu.getItem(13)));
    registry.getAll().forEach(record -> registry.unregister(record.getVehicleUuid()));
    view.installationDetailView(leader, faction, "fort", menu);
    assertNull(menu.getItem(0));
    assertNull(menu.getItem(44));
    assertEquals("fort", data(menu.getItem(49), Keys.STRING_KEY));
  }

  @Test
  void vehicleItemsShowStoredLocationAndHideTakeHintFromOtherMembers() {
    Installation fort = installation("fort", InstallationKind.FORT);
    berth("tank-1", "heavy_tank", fort);
    when(vehicles.getOfflineLocation("tank-1"))
        .thenReturn(Optional.of(new Location(fixture.ui.world, 12, 65, 34)));
    view.installationDetailView(leader, faction, "fort");
    assertEquals("tank-1", name(top().getItem(0)));
    assertTrue(lore(top().getItem(0)).contains("12, 65, 34"));
    faction.setLeader("Successor");
    view.installationDetailView(leader, faction, "fort", top());
    assertFalse(lore(top().getItem(0)).contains("Click to take"));
    view.click(fixture.ui.click(leader, 0), top(), leader);
    verifyNoInteractions(unberth);
    assertTrue(registry.isBerthed("tank-1"));
  }

  @ParameterizedTest
  @EnumSource(
      value = InstallationVehicleUnberthService.UnberthResult.class,
      names = {"OK", "NOT_BERTHED", "SAVE_FAILED"})
  void takingABerthedVehicleReflectsTheTransferResultAndConservesTheRegistry(
      InstallationVehicleUnberthService.UnberthResult result) {
    Installation fort = installation("fort", InstallationKind.FORT);
    PlayerVehicleRecord original = berth("tank-1", "heavy_tank", fort);
    InstallationVehicleUnberthService.UnberthOutcome outcome =
        new InstallationVehicleUnberthService.UnberthOutcome(result, null, "heavy_tank");
    when(unberth.unberth(faction, "Leader", fort, "tank-1"))
        .thenAnswer(
            call -> {
              if (result == InstallationVehicleUnberthService.UnberthResult.OK)
                registry.register(
                    new PlayerVehicleRecord(
                        leader.getUniqueId(),
                        "tank-1",
                        "heavy_tank",
                        OwnershipMode.PERSONAL,
                        null));
              return outcome;
            });
    view.installationDetailView(leader, faction, "fort");
    Inventory old = top();
    view.click(fixture.ui.click(leader, 0), old, leader);
    verify(unberth).unberth(faction, "Leader", fort, "tank-1");
    verify(leader).sendMessage(InstallationVehicleUnberthService.messageFor(outcome));
    assertEquals(1, registry.getAll().size());
    if (result == InstallationVehicleUnberthService.UnberthResult.OK) {
      assertEquals(
          OwnershipMode.PERSONAL, registry.getByVehicleUuid("tank-1").orElseThrow().getMode());
      assertNull(top().getItem(0));
      assertNotSame(old, top());
    } else {
      assertSame(original, registry.getByVehicleUuid("tank-1").orElseThrow());
      assertSame(old, top());
    }
    assertSame(fort, faction.getInstallationHandler().getById("fort"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "empty",
        "plain",
        "no_key",
        "missing_detail",
        "plain_detail",
        "detail_no_key",
        "removed_installation"
      })
  void staleOrNonActionVehicleSlotsNeverCallTheTransferService(String state) {
    Installation fort = installation("fort", InstallationKind.FORT);
    berth("tank-1", "heavy_tank", fort);
    view.installationDetailView(leader, faction, "fort");
    Inventory menu = top();
    switch (state) {
      case "empty" -> menu.setItem(0, null);
      case "plain" -> menu.setItem(0, new ItemStack(Material.STONE));
      case "no_key" -> menu.setItem(0, named("Decoration"));
      case "missing_detail" -> menu.setItem(49, null);
      case "plain_detail" -> menu.setItem(49, new ItemStack(Material.STONE));
      case "detail_no_key" -> menu.setItem(49, named("Details"));
      case "removed_installation" -> faction.getInstallationHandler().detachOnProvince(10);
    }
    view.click(fixture.ui.click(leader, 0), menu, leader);
    verifyNoInteractions(unberth);
    assertTrue(registry.isBerthed("tank-1"));
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 13})
  void clearedActionButtonsCannotStartAConfirmation(int slot) {
    installation("fort", InstallationKind.FORT);
    view.installationDetailView(leader, faction, "fort");
    Inventory menu = top();
    for (ItemStack item :
        new ItemStack[] {null, new ItemStack(Material.STONE), named("Decoration")}) {
      menu.setItem(slot, item);
      view.click(fixture.ui.click(leader, slot), menu, leader);
    }
    verify(navigation, never()).confirmView(any(), any(), anyString(), anyString());
    assertTrue(navigation.confirming.isEmpty());
    verifyNoInteractions(unberth);
  }

  @Test
  void unrelatedMenusAndRemovedFactionsDoNotNavigateOrChangeInstallations() {
    installation("fort", InstallationKind.FORT);
    Inventory ordinary = fixture.ui.inventory(null, 9, "Other plugin");
    leader.openInventory(ordinary);
    view.click(fixture.ui.click(leader, 0), ordinary, leader);
    view.installationsView(null, leader, faction, true);
    Inventory list = top();
    view.click(fixture.ui.click(leader, 0), list, leader);
    list.setItem(0, new ItemStack(Material.STONE));
    view.click(fixture.ui.click(leader, 0), list, leader);
    view.click(fixture.ui.click(leader, 10), list, leader);
    assertSame(list, top());
    FactionManager.factions.remove(faction);
    view.click(fixture.ui.click(leader, 12), list, leader);
    assertSame(list, top());
    assertEquals(1, faction.getInstallationHandler().getAll().size());
    verifyNoInteractions(unberth);
  }

  @Test
  void reportedInstallationMenusMaskExactDataOnInitialOpenAndRefresh() {
    installation("secret", InstallationKind.FORT);
    scope(EspionageService.class)
        .when(() -> EspionageService.canViewExact(any(), any()))
        .thenReturn(false);
    view.installationsView(null, leader, faction, true);
    Inventory list = top();
    assertTrue(((SFInventoryHolder) list.getHolder()).isReported());
    assertTrue(lore(list.getItem(12)).contains("Unknown"));
    assertNull(data(list.getItem(39), Keys.QUEUE_CANCEL));
    view.installationsView(list, leader, faction, false);
    assertSame(list, top());
    view.installationsView(null, leader, faction, false);
    assertSame(list, top());
    view.installationDetailView(leader, faction, "secret");
    Inventory detail = top();
    assertTrue(((SFInventoryHolder) detail.getHolder()).isReported());
    assertTrue(lore(detail.getItem(49)).contains("Coordinates: Unknown"));
    assertNull(detail.getItem(11));
    assertNull(detail.getItem(13));
    view.installationDetailView(leader, faction, "secret", detail);
    assertSame(detail, top());
    assertNull(data(detail.getItem(49), Keys.STRING_KEY));
  }

  private InstallationConstruction pending(String id, InstallationKind kind, boolean upgrade) {
    InstallationConstruction construction =
        new InstallationConstruction(id, id, kind, 10, 12, 34, 120, 1L, upgrade);
    faction.getInstallationHandler().loadConstruction(construction.toData());
    return faction.getInstallationHandler().getPendingConstruction();
  }

  private PlayerVehicleRecord berth(String id, String type, Installation installation) {
    PlayerVehicleRecord record =
        new PlayerVehicleRecord(
            UUID.randomUUID(),
            id,
            type,
            OwnershipMode.INSTALLATION,
            installation.getId(),
            faction.getId());
    registry.register(record);
    return record;
  }

  private ItemStack named(String text) {
    ItemStack item = new ItemStack(Material.PAPER);
    var meta = item.getItemMeta();
    meta.setDisplayName(text);
    item.setItemMeta(meta);
    return item;
  }

  private String name(ItemStack item) {
    return ChatColor.stripColor(item.getItemMeta().getDisplayName());
  }

  private String lore(ItemStack item) {
    return ChatColor.stripColor(String.join("\n", item.getItemMeta().getLore()));
  }

  private String data(ItemStack item, org.bukkit.NamespacedKey key) {
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  private Installation installation(String id, InstallationKind kind) {
    Installation installation = new Installation(id, id, kind, 10, 12, 34, 1L);
    faction.getInstallationHandler().acceptTransferred(installation);
    return installation;
  }

  private Inventory top() {
    return leader.getOpenInventory().getTopInventory();
  }

  private <T> MockedStatic<T> scope(Class<T> type) {
    MockedStatic<T> scope = mockStatic(type);
    scopes.add(scope);
    return scope;
  }
}
