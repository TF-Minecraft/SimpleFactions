package net.tfminecraft.simplefactions.vehicles.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerPouch;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePaySessionManager.VehicleMaintenancePaySession;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.data.OwnedVehicleSummary;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.events.VehicleRepairStartEvent;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class VehicleRuntimeLifecycleCoverageTest {
  @TempDir Path temporary;
  private FactionDomainFixture fixture;
  private Faction faction;
  private Player player;
  private Player leader;
  private PlayerVehicleRegistry registry;
  private PlayerEconomyManager economy;
  private VehicleMaintenanceStore store;
  private Balances balances;
  private VehicleUpkeepService upkeep;
  private VehicleMaintenancePaySessionManager sessions;
  private VehicleMaintenancePayListener payListener;
  private VehicleManager manager;
  private MockedStatic<VehicleFramework> framework;
  private final List<OwnedVehicleSummary> owned = new ArrayList<>();
  private final List<Damage> damage = new ArrayList<>();
  private final Map<Field, Object> globals = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    for (String name :
        List.of(
            "personalSlotLimit",
            "defaultPerPerson",
            "maintenanceHourlyDamagePercent",
            "maintenanceMinHealthPercent",
            "maintenanceIntervalTicks",
            "categoryIds",
            "typesByCategory",
            "categoryByVehicleTypeId",
            "categoryDisplayNames",
            "feeExcludedCategories")) {
      snapshot(VehiclesConfigLoader.class, name);
    }
    snapshot(VehicleOwnershipQueries.class, "source");
    snapshot(VehicleFeeService.class, "factionLookup");
    snapshot(VehicleFeeService.class, "playerBank");
    VehicleOwnershipQueries.setSourceForTests(null);
    configure(20);
    faction = fixture.saved("home", "Leader");
    faction.getOrCreateMainGuild().addMember("Alice");
    player = fixture.player("Alice");
    leader = fixture.player("Leader");
    when(Bukkit.getPlayer(any(UUID.class)))
        .thenAnswer(
            call ->
                fixture.online.values().stream()
                    .filter(online -> online.getUniqueId().equals(call.getArgument(0)))
                    .findFirst()
                    .orElse(null));
    registry = new PlayerVehicleRegistry();
    economy = new PlayerEconomyManager();
    bindPlugin("vehicleRegistry", registry);
    bindPlugin("playerEconomyManager", economy);
    when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(true);
    store = new VehicleMaintenanceStore();
    balances = new Balances();
    balances.names.put("Alice", player.getUniqueId());
    balances.bank.put(player.getUniqueId(), 100.0);
    balances.pouch.put(player.getUniqueId(), 50.0);
    VehicleFeeService.setForTests(null, balances);
    upkeep =
        new VehicleUpkeepService(
            registry,
            economy,
            balances,
            store,
            (id, fraction, floor) -> {
              damage.add(new Damage(id, fraction, floor));
              return true;
            });
    when(fixture.ui.plugin.getVehicleUpkeepService()).thenReturn(upkeep);
    sessions = new VehicleMaintenancePaySessionManager();
    payListener =
        new VehicleMaintenancePayListener(
            sessions, new VehicleMaintenancePayService(store, balances, balances));
    manager = mock(VehicleManager.class);
    when(manager.listAllPlayerOwnedVehicles()).thenAnswer(call -> new ArrayList<>(owned));
    when(manager.listOwnedVehicles(anyString()))
        .thenAnswer(
            call ->
                owned.stream()
                    .filter(
                        vehicle -> call.<String>getArgument(0).equalsIgnoreCase(vehicle.getOwner()))
                    .toList());
    framework = mockStatic(VehicleFramework.class);
    framework.when(VehicleFramework::getVehicleManager).thenReturn(manager);
  }

  @AfterEach
  void close() throws Exception {
    framework.close();
    for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    fixture.close();
  }

  @Test
  void disablingUpkeepClearsOldPersonalDebtAndStopsDecayAndRepairBlocking() throws Exception {
    owned.add(summary("personal", "ironclad", "player_Alice"));
    store.markUnpaid("personal", 1);
    configure(0);

    upkeep.processDailyUpkeep();
    upkeep.tickHourlyDecay();

    assertFalse(store.isUnpaid("personal"));
    assertTrue(damage.isEmpty());
    assertEquals(100, balances.getBankBalance(player.getUniqueId()));
    assertEquals(
        0, economy.getLedger(player.getUniqueId()).getAmount(PlayerCashflow.VEHICLE_UPKEEP));
    VehicleRepairStartEvent repair =
        new VehicleRepairStartEvent(player, vehicle("personal", "ironclad"));
    new VehicleMaintenanceRepairListener(store).onRepairStart(repair);
    assertFalse(repair.isCancelled());
    verify(fixture.ui.plugin).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "success_pouch",
        "success_bank",
        "insufficient_pouch",
        "insufficient_bank",
        "not_unpaid",
        "unknown_type"
      })
  void anArmedPaymentConsumesOneInteractionAndReportsTheActualOutcome(String scenario) {
    String type = scenario.equals("unknown_type") ? "unknown" : "ironclad";
    PaymentSource source = scenario.endsWith("bank") ? PaymentSource.BANK : PaymentSource.POUCH;
    if (!scenario.equals("not_unpaid")) store.markUnpaid("vehicle", 5);
    if (scenario.equals("insufficient_pouch")) balances.pouch.put(player.getUniqueId(), 1.0);
    if (scenario.equals("insufficient_bank")) balances.bank.put(player.getUniqueId(), 1.0);
    double originalBank = balances.getBankBalance(player.getUniqueId());
    double originalPouch = balances.getPouchBalance(player.getUniqueId());
    sessions.put(player.getUniqueId(), new VehicleMaintenancePaySession(Long.MAX_VALUE, source));
    VehiclePreInteractEvent event = new VehiclePreInteractEvent(player, vehicle("vehicle", type));

    payListener.onVehiclePreInteract(event);

    assertTrue(event.isCancelled());
    assertNull(sessions.get(player.getUniqueId()));
    boolean success = scenario.startsWith("success");
    assertEquals(!success && !scenario.equals("not_unpaid"), store.isUnpaid("vehicle"));
    assertEquals(
        originalBank - (scenario.equals("success_bank") ? 20 : 0),
        balances.getBankBalance(player.getUniqueId()));
    assertEquals(
        originalPouch - (scenario.equals("success_pouch") ? 20 : 0),
        balances.getPouchBalance(player.getUniqueId()));
    String message =
        switch (scenario) {
          case "success_pouch", "success_bank" -> VehicleMaintenanceMessages.paySuccess();
          case "insufficient_pouch" -> VehicleMaintenanceMessages.insufficientPouch();
          case "insufficient_bank" -> VehicleMaintenanceMessages.insufficientBank();
          case "not_unpaid" -> VehicleMaintenanceMessages.notUnpaid();
          case "unknown_type" -> VehicleMaintenanceMessages.unknownType();
          default -> throw new AssertionError(scenario);
        };
    verify(player).sendMessage(message);
    verify(fixture.ui.plugin, times(success ? 1 : 0)).saveVehicleRegistry();
    VehiclePreInteractEvent second = new VehiclePreInteractEvent(player, event.getVehicle());
    payListener.onVehiclePreInteract(second);
    assertFalse(second.isCancelled());
  }

  @Test
  void invalidInteractionBoundariesAndExpiredSessionsNeverMoveMoney() {
    VehiclePreInteractEvent noPlayer = new VehiclePreInteractEvent(null, vehicle("x", "ironclad"));
    payListener.onVehiclePreInteract(noPlayer);
    assertFalse(noPlayer.isCancelled());
    sessions.put(null, new VehicleMaintenancePaySession(1));
    sessions.put(player.getUniqueId(), null);
    sessions.clear(null);
    assertNull(sessions.get(null));
    assertNull(sessions.get(player.getUniqueId()));
    VehicleMaintenancePaySession expired = new VehicleMaintenancePaySession(0);
    assertEquals(0, expired.getExpiresAtMillis());
    assertEquals(PaymentSource.POUCH, expired.getPaymentSource());
    assertTrue(expired.isExpired(0));
    sessions.put(player.getUniqueId(), expired);
    VehiclePreInteractEvent afterExpiry =
        new VehiclePreInteractEvent(player, vehicle("x", "ironclad"));
    payListener.onVehiclePreInteract(afterExpiry);
    assertFalse(afterExpiry.isCancelled());
    assertNull(sessions.get(player.getUniqueId()));
    VehicleMaintenancePaySession active = new VehicleMaintenancePaySession(Long.MAX_VALUE);
    assertFalse(active.isExpired(1));
    sessions.put(player.getUniqueId(), active);
    for (ActiveVehicle vehicle : Arrays.asList(null, vehicle(null, "ironclad"))) {
      VehiclePreInteractEvent invalid = new VehiclePreInteractEvent(player, vehicle);
      payListener.onVehiclePreInteract(invalid);
      assertTrue(invalid.isCancelled());
      assertSame(active, sessions.get(player.getUniqueId()));
    }
    assertEquals(100, balances.getBankBalance(player.getUniqueId()));
    assertEquals(50, balances.getPouchBalance(player.getUniqueId()));
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @Test
  void successfulPaymentStillCompletesWhenThePluginPersistenceBoundaryIsUnavailable() {
    store.markUnpaid("x", 1);
    sessions.put(player.getUniqueId(), new VehicleMaintenancePaySession(Long.MAX_VALUE));
    SimpleFactions.plugin = null;

    payListener.onVehiclePreInteract(new VehiclePreInteractEvent(player, vehicle("x", "ironclad")));

    assertFalse(store.isUnpaid("x"));
    assertEquals(30, balances.getPouchBalance(player.getUniqueId()));
    assertNull(sessions.get(player.getUniqueId()));
    verify(player).sendMessage(VehicleMaintenanceMessages.paySuccess());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @Test
  void repairEventsDenyOnlyKnownUnpaidVehiclesAndKeepDebtIntact() {
    VehicleMaintenanceRepairListener listener = new VehicleMaintenanceRepairListener(store);
    listener.onRepairStart(null);
    for (ActiveVehicle vehicle :
        Arrays.asList(null, vehicle(null, "ironclad"), vehicle("paid", "ironclad"))) {
      VehicleRepairStartEvent event = new VehicleRepairStartEvent(player, vehicle);
      listener.onRepairStart(event);
      assertFalse(event.isCancelled());
    }
    store.markUnpaid("unpaid", 9);
    VehicleRepairStartEvent blocked =
        new VehicleRepairStartEvent(player, vehicle("unpaid", "ironclad"));
    listener.onRepairStart(blocked);
    assertTrue(blocked.isCancelled());
    verify(player).sendMessage(VehicleMaintenanceMessages.repairBlocked());
    VehicleRepairStartEvent noPlayer = new VehicleRepairStartEvent(null, blocked.getVehicle());
    listener.onRepairStart(noPlayer);
    assertTrue(noPlayer.isCancelled());
    assertEquals(Map.of("unpaid", 9L), store.snapshot());
    VehicleRepairStartEvent noStore = new VehicleRepairStartEvent(player, blocked.getVehicle());
    new VehicleMaintenanceRepairListener(null).onRepairStart(noStore);
    assertFalse(noStore.isCancelled());
    assertEquals(100, balances.getBankBalance(player.getUniqueId()));
  }

  @Test
  void decayTaskReplacesItsScheduleAndRunsAgainstTheActualUnpaidStore() {
    store.markUnpaid("x", 1);
    BukkitTask first = mock(BukkitTask.class);
    BukkitTask second = mock(BukkitTask.class);
    List<Runnable> callbacks = new ArrayList<>();
    when(fixture.ui.scheduler.runTaskTimer(
            eq(fixture.ui.plugin), any(Runnable.class), eq(90L), eq(90L)))
        .thenAnswer(
            call -> {
              callbacks.add(call.getArgument(1));
              return callbacks.size() == 1 ? first : second;
            });
    VehicleMaintenanceDecayTask task = new VehicleMaintenanceDecayTask();
    task.start();
    task.start();
    verify(first).cancel();
    assertEquals(2, callbacks.size());
    assertTrue(damage.isEmpty());

    callbacks.getLast().run();

    assertEquals(List.of(new Damage("x", 0.15, 0.07)), damage);
    task.stop();
    task.stop();
    verify(second).cancel();
    SimpleFactions.plugin = null;
    task.start();
    SimpleFactions.plugin = fixture.ui.plugin;
    when(Bukkit.getScheduler()).thenReturn(null);
    task.start();
    assertEquals(2, callbacks.size());
  }

  @Test
  void liveDecayAdapterPassesFractionsToVehicleFrameworkOnlyWhenAvailable() {
    VehicleHealthDecayApi api = VehicleHealthDecayApi.Vf.INSTANCE;
    assertFalse(api.unloadedDamage(null, 0.15, 0.07));
    assertFalse(api.unloadedDamage(" ", 0.15, 0.07));
    assertFalse(api.unloadedDamage("x", 0.15, 0.07));
    PluginManager plugins = Bukkit.getPluginManager();
    when(plugins.isPluginEnabled("VehicleFramework")).thenReturn(true);
    when(manager.unloadedDamage("x", 0.15, 0.07)).thenReturn(true);
    assertTrue(api.unloadedDamage("x", 0.15, 0.07));
    assertFalse(api.unloadedDamage("unknown", 0.15, 0.07));
    verify(manager).unloadedDamage("x", 0.15, 0.07);
    when(Bukkit.getPluginManager()).thenReturn(null);
    assertFalse(api.unloadedDamage("x", 0.15, 0.07));
    when(Bukkit.getServer()).thenReturn(null);
    assertFalse(api.unloadedDamage("x", 0.15, 0.07));
    verify(manager, times(1)).unloadedDamage("x", 0.15, 0.07);
  }

  @Test
  void ownershipQueriesPreserveValidProviderRowsAndExcludeFactionRegistrations() {
    OwnedVehicleSummary personal = summary("personal", "ironclad", "player_Alice");
    OwnedVehicleSummary ignored = summary("ignored", "raft", "player_Alice");
    OwnedVehicleSummary factionOwned = summary("pool", "ironclad", "player_Alice");
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(), "pool", "ironclad", OwnershipMode.POOL, null, "home"));
    when(manager.listOwnedVehicles("player_Alice"))
        .thenReturn(
            Arrays.asList(
                null, summary(null, "ironclad", "player_Alice"), personal, ignored, factionOwned));
    when(manager.listAllPlayerOwnedVehicles())
        .thenReturn(Arrays.asList(null, personal, factionOwned));

    assertEquals(
        List.of(personal, ignored), VehicleOwnershipQueries.personalVehicles("Alice", registry));
    assertEquals(List.of(personal), VehicleOwnershipQueries.allPersonalVehicles(registry));
    assertEquals(
        List.of(personal, factionOwned), VehicleOwnershipQueries.allPersonalVehicles(null));
    assertEquals(1, VehicleOwnershipQueries.countOfType(List.of(personal, ignored), "IRONCLAD"));
    assertEquals(1, VehicleOwnershipQueries.countExcludingIgnoreLimit(List.of(personal, ignored)));
    assertEquals(
        0,
        VehicleOwnershipQueries.countOfType(
            List.of(summary("unknown", null, "player_Alice")), "ironclad"));
    assertEquals(0, VehicleOwnershipQueries.countOfType(null, "ironclad"));
    assertEquals(0, VehicleOwnershipQueries.countOfType(List.of(personal), null));
    assertEquals(0, VehicleOwnershipQueries.countOfType(List.of(personal), ""));
    assertEquals(0, VehicleOwnershipQueries.countExcludingIgnoreLimit(null));
    for (String name : Arrays.asList(null, "", " ")) {
      assertEquals("player_none", VehicleOwnershipQueries.ownerEntry(name));
      assertTrue(VehicleOwnershipQueries.personalVehicles(name, registry).isEmpty());
      assertFalse(VehicleOwnershipQueries.isPlayerOwner(name));
      assertNull(VehicleOwnershipQueries.playerNameFromOwner(name));
    }
    for (String owner : List.of("player_", "player_none", "faction_home")) {
      assertFalse(VehicleOwnershipQueries.isPlayerOwner(owner));
      assertNull(VehicleOwnershipQueries.playerNameFromOwner(owner));
    }
    assertTrue(VehicleOwnershipQueries.isPlayerOwner("PLAYER_Alice"));
    assertEquals("Alice", VehicleOwnershipQueries.playerNameFromOwner("PLAYER_Alice"));
    when(manager.listOwnedVehicles("player_Alice")).thenReturn(null);
    assertTrue(VehicleOwnershipQueries.personalVehicles("Alice", registry).isEmpty());
    framework
        .when(VehicleFramework::getVehicleManager)
        .thenThrow(new LinkageError("plugin absent"));
    assertTrue(VehicleOwnershipQueries.personalVehicles("Alice", registry).isEmpty());
    assertTrue(VehicleOwnershipQueries.allPersonalVehicles(registry).isEmpty());
  }

  @Test
  void ownershipNameResolutionUsesTheRealOnlineOrOfflineProviderContract() {
    UUID offlineId = UUID.randomUUID();
    OfflinePlayer offline = mock(OfflinePlayer.class);
    when(offline.getName()).thenReturn("OfflineAlice");
    when(Bukkit.getOfflinePlayer(offlineId)).thenReturn(offline);
    assertEquals("Alice", VehicleOwnershipQueries.resolvePlayerName(player.getUniqueId()));
    assertEquals("OfflineAlice", VehicleOwnershipQueries.resolvePlayerName(offlineId));
    assertNull(VehicleOwnershipQueries.resolvePlayerName(null));
    try (MockedStatic<OfflineModifier> identities = mockStatic(OfflineModifier.class)) {
      identities.when(() -> OfflineModifier.playerId("OfflineAlice")).thenReturn(offlineId);
      assertEquals(offlineId, VehicleOwnershipQueries.resolvePlayerUuid("OfflineAlice"));
      assertNull(VehicleOwnershipQueries.resolvePlayerUuid("Unknown"));
    }
    when(Bukkit.getServer()).thenReturn(null);
    assertNull(VehicleOwnershipQueries.resolvePlayerName(player.getUniqueId()));
  }

  @Test
  void failedPersonalPaymentsPreserveBalancesTrackFirstDebtAndWarnOnlyResolvedOwners() {
    Player ghost = fixture.player("Unknown");
    Player offline = fixture.player("Offline");
    fixture.online.remove("Offline");
    balances.names.put("Offline", offline.getUniqueId());
    balances.bank.put(player.getUniqueId(), 10.0);
    owned.addAll(
        List.of(
            summary("alice", "ironclad", "player_Alice"),
            summary("ghost", "ironclad", "player_Unknown"),
            summary("offline", "ironclad", "player_Offline")));
    store.markUnpaid("alice", 1);
    Logger logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);

    upkeep.processDailyUpkeep();

    assertEquals(Set.of("alice", "ghost", "offline"), store.unpaidUuids());
    assertEquals(1, store.snapshot().get("alice"));
    assertEquals(10, balances.getBankBalance(player.getUniqueId()));
    assertEquals(
        0, economy.getLedger(player.getUniqueId()).getAmount(PlayerCashflow.VEHICLE_UPKEEP));
    verify(player)
        .sendMessage("§cCould not pay vehicle upkeep (ironclad): insufficient bank balance.");
    verify(ghost, never()).sendMessage(anyString());
    verify(offline, never()).sendMessage(anyString());
    verify(logger, times(3)).info(anyString());
    verify(fixture.ui.plugin, times(3)).saveVehicleRegistry();
    clearInvocations(player);
    upkeep.warnBankShortfalls(List.of(player), 0);
    verify(player, never()).sendMessage(anyString());
    upkeep.warnBankShortfalls(List.of(player, ghost, leader), 59);
    verify(player).sendMessage(VehicleMaintenanceMessages.bankShortfall(10, 59));
    verify(ghost, never()).sendMessage(anyString());
    upkeep.tickHourlyDecay();
    assertEquals(
        Set.of(
            new Damage("alice", 0.15, 0.07),
            new Damage("ghost", 0.15, 0.07),
            new Damage("offline", 0.15, 0.07)),
        Set.copyOf(damage));
  }

  @Test
  void factionBanksPayPoolsWhileLostInstallationsAndFreeTypesStopAccruingDebt() {
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(), "personal", "ironclad", OwnershipMode.PERSONAL, null));
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(), "pool", "ironclad", OwnershipMode.POOL, null, "home"));
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(), "orphan-pool", "ironclad", OwnershipMode.POOL, null, "missing"));
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(),
            "orphan-install",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "lost",
            "home"));
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(), "free", "raft", OwnershipMode.POOL, null, "home"));
    store.markUnpaid("orphan-install", 1);
    store.markUnpaid("free", 1);
    Logger logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);

    upkeep.processDailyUpkeep();

    assertEquals(Set.of("pool", "orphan-pool"), store.unpaidUuids());
    verify(leader)
        .sendMessage(
            "§cCould not pay faction vehicle upkeep (ironclad): insufficient faction bank.");
    verify(logger, times(2)).info(anyString());
    assertEquals(0, faction.getBank().getWealth());
    assertEquals(100, balances.getBankBalance(player.getUniqueId()));
    registry.unregister("orphan-pool");
    faction.getBank().deposit(40.0);

    upkeep.processDailyUpkeep();

    assertFalse(store.isUnpaid("pool"));
    assertEquals(20, faction.getBank().getWealth());
    assertEquals(100, balances.getBankBalance(player.getUniqueId()));
    faction.getOrCreateMainGuild().setBank(null);
    when(leader.isOnline()).thenReturn(false);
    clearInvocations(leader);
    upkeep.processDailyUpkeep();
    assertTrue(store.isUnpaid("pool"));
    verify(leader, never()).sendMessage(anyString());
  }

  @Test
  void defaultUpkeepWiringUsesDenarBankAndRecordsTheRealPlayerLedger() {
    owned.add(summary("personal", "ironclad", "player_Alice"));
    UUID playerId = player.getUniqueId();
    try (MockedStatic<OfflineModifier> bank = mockStatic(OfflineModifier.class)) {
      bank.when(() -> OfflineModifier.playerId("Alice")).thenReturn(playerId);
      bank.when(() -> OfflineModifier.apply(playerId, Accounts.BANK, -20)).thenReturn(true);

      new VehicleUpkeepService(registry, economy).processDailyUpkeep();

      bank.verify(() -> OfflineModifier.apply(playerId, Accounts.BANK, -20));
      assertEquals(
          -20, economy.getLedger(player.getUniqueId()).getAmount(PlayerCashflow.VEHICLE_UPKEEP));
      verify(fixture.ui.plugin).saveVehicleRegistry();
    }
  }

  @Test
  void defaultPaymentWiringUsesDenarBankWithoutTouchingTheSuppliedPouch() {
    UUID id = player.getUniqueId();
    store.markUnpaid("personal", 1);
    VehicleMaintenancePayService payments = new VehicleMaintenancePayService(store, balances);
    try (MockedStatic<OfflineModifier> bank = mockStatic(OfflineModifier.class)) {
      bank.when(() -> OfflineModifier.apply(id, Accounts.BANK, -20)).thenReturn(true);

      assertEquals(
          VehicleMaintenancePayService.VehicleMaintenancePayResult.SUCCESS,
          payments.tryPay(id, "personal", "ironclad", PaymentSource.BANK));

      bank.verify(() -> OfflineModifier.apply(id, Accounts.BANK, -20));
      assertFalse(store.isUnpaid("personal"));
      assertEquals(50, balances.getPouchBalance(id));
      store.markUnpaid("free-raft", 2);
      assertEquals(
          VehicleMaintenancePayService.VehicleMaintenancePayResult.SUCCESS,
          payments.tryPay(id, "free-raft", "raft"));
      assertFalse(store.isUnpaid("free-raft"));
      assertEquals(50, balances.getPouchBalance(id));
      bank.verifyNoMoreInteractions();
    }
  }

  @Test
  void personalOnlyBillingSurvivesMissingPluginBoundariesAndEmptyProjectionInputs() {
    owned.add(summary("personal", "ironclad", "player_Alice"));
    VehicleUpkeepService personalOnly =
        new VehicleUpkeepService(null, economy, balances, store, (id, fraction, floor) -> true);
    SimpleFactions.plugin = null;
    when(Bukkit.getServer()).thenReturn(null);
    balances.bank.put(player.getUniqueId(), 0.0);

    personalOnly.processDailyUpkeep();

    assertTrue(store.isUnpaid("personal"));
    assertEquals(0, balances.getBankBalance(player.getUniqueId()));
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
    assertEquals(0, VehicleUpkeepProjection.projectedDailyUpkeep((UUID) null));
    assertEquals(0, VehicleUpkeepProjection.projectedDailyUpkeep(null, registry));
    assertEquals(0, VehicleUpkeepProjection.projectedDailyTax(" ", registry));
    assertEquals(0, VehicleUpkeepProjection.displayVehicleTax(null, player.getUniqueId()));
    assertEquals(0, VehicleUpkeepProjection.displayVehicleExpense(null, player.getUniqueId()));
    assertEquals(0, VehicleUpkeepProjection.displayNetDaily(null, player.getUniqueId()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{unfinished", "null"})
  void malformedMaintenanceHistoryPreservesLiveDebtAndTheFailedFileUntilRecovery(String damaged)
      throws Exception {
    Path file = temporary.resolve("vehicle_maintenance.json");
    Files.writeString(file, damaged);
    store.markUnpaid("existing", 25);
    VehicleMaintenancePersistence persistence =
        new VehicleMaintenancePersistence(temporary.toFile(), store);

    assertDoesNotThrow(persistence::load);

    assertEquals(Map.of("existing", 25L), store.snapshot());
    persistence.save();
    assertEquals(
        damaged, Files.readString(file), "A failed read must not be overwritten by a later save");
    Files.writeString(file, "{\"recovered\":17}");
    persistence.load();
    assertEquals(Map.of("recovered", 17L), store.snapshot());
    store.markUnpaid("new", 30);
    persistence.save();
    VehicleMaintenanceStore reloaded = new VehicleMaintenanceStore();
    new VehicleMaintenancePersistence(temporary.toFile(), reloaded).load();
    assertEquals(Map.of("recovered", 17L, "new", 30L), reloaded.snapshot());
  }

  @Test
  void unpaidStoreRetainsFirstFailureAndExposesDetachedImmutableSnapshots() {
    store.markUnpaid(null, 1);
    store.markUnpaid(" ", 1);
    store.clearUnpaid(null);
    assertTrue(store.snapshot().isEmpty());
    assertFalse(store.isUnpaid(null));
    store.markUnpaid("ship", 10);
    store.markUnpaid("ship", 30);
    Map<String, Long> snapshot = store.snapshot();
    Set<String> identifiers = store.unpaidUuids();
    assertEquals(Map.of("ship", 10L), snapshot);
    assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", 20L));
    assertThrows(UnsupportedOperationException.class, () -> identifiers.add("other"));
    store.clearUnpaid("ship");
    assertEquals(Map.of("ship", 10L), snapshot);
    assertEquals(Set.of("ship"), identifiers);
    assertTrue(store.unpaidUuids().isEmpty());

    Map<String, Long> imported = new LinkedHashMap<>();
    imported.put(null, 1L);
    imported.put(" ", 2L);
    imported.put("missing-time", null);
    imported.put("recovered", 7L);
    store.replaceAll(imported);
    assertEquals(Map.of("recovered", 7L), store.snapshot());
    store.replaceAll(null);
    assertTrue(store.snapshot().isEmpty());
  }

  @Test
  void projectionIncludesCurrentLawTaxOnceThenUsesSettledAmountsWithoutDoubleCounting() {
    fixture.lawGroup(
        "vehicles",
        Map.of(
            "effects.faction.rules",
            List.of("VEHICLE_TAX true"),
            "effects.faction.brackets.VEHICLE_TAX",
            "0-100"));
    Faction taxed = fixture.saved("taxed", "TaxLeader");
    taxed.getOrCreateMainGuild().addMember("Taxpayer");
    Player taxpayer = fixture.player("Taxpayer");
    taxed.getVehicleFeeHandler().setRate(FeeKind.VEHICLE_TAX, null, 50);
    owned.add(summary("personal-taxable", "ironclad", "player_Taxpayer"));
    owned.add(summary("pooled-exempt", "ironclad", "player_Taxpayer"));
    owned.add(summary("free-exempt", "raft", "player_Taxpayer"));
    registry.register(
        new PlayerVehicleRecord(
            taxpayer.getUniqueId(), "pooled-exempt", "ironclad", OwnershipMode.POOL, "taxed"));
    PlayerLedger ledger = new PlayerLedger();
    ledger.add(PlayerCashflow.EARNINGS, 100);

    assertEquals(20, VehicleUpkeepProjection.projectedDailyUpkeep(taxpayer.getUniqueId()));
    assertEquals(10, VehicleUpkeepProjection.projectedDailyTax("Taxpayer", registry));
    assertEquals(-10, VehicleUpkeepProjection.displayVehicleTax(ledger, taxpayer.getUniqueId()));
    assertEquals(
        -20, VehicleUpkeepProjection.displayVehicleExpense(ledger, taxpayer.getUniqueId()));
    assertEquals(70, VehicleUpkeepProjection.displayNetDaily(ledger, taxpayer.getUniqueId()));
    assertEquals(100, ledger.getNetDaily(), "A preview must not mutate the settled ledger");
    assertEquals(0, VehicleUpkeepProjection.displayVehicleTax(ledger, null));

    ledger.add(PlayerCashflow.VEHICLE_UPKEEP, -20);
    ledger.add(PlayerCashflow.VEHICLE_TAX, -10);
    taxed.getVehicleFeeHandler().setRate(FeeKind.VEHICLE_TAX, null, 100);
    assertEquals(-10, VehicleUpkeepProjection.displayVehicleTax(ledger, taxpayer.getUniqueId()));
    assertEquals(
        -20, VehicleUpkeepProjection.displayVehicleExpense(ledger, taxpayer.getUniqueId()));
    assertEquals(70, VehicleUpkeepProjection.displayNetDaily(ledger, taxpayer.getUniqueId()));
    ledger.clearDaily();
    ledger.add(PlayerCashflow.VEHICLE_UPKEEP, -20);
    assertEquals(
        0,
        VehicleUpkeepProjection.displayVehicleTax(ledger, taxpayer.getUniqueId()),
        "A day settled without tax must not suddenly show tomorrow's tax");
  }

  @Test
  void denarAdapterSeparatesBankAndPouchAndDoesNotSendNonpositiveTransfers() {
    UUID id = player.getUniqueId();
    Map<Accounts, Double> amounts = new HashMap<>();
    amounts.put(Accounts.BANK, 100.0);
    amounts.put(Accounts.POUCH, 40.0);
    AtomicInteger mutations = new AtomicInteger();
    try (MockedStatic<OfflineModifier> boundary = mockStatic(OfflineModifier.class)) {
      boundary.when(() -> OfflineModifier.playerId("Alice")).thenReturn(id);
      boundary
          .when(() -> OfflineModifier.balance(eq(id), any(Accounts.class)))
          .thenAnswer(call -> amounts.get(call.getArgument(1)));
      boundary
          .when(() -> OfflineModifier.apply(eq(id), any(Accounts.class), anyDouble()))
          .thenAnswer(
              call -> {
                mutations.incrementAndGet();
                Accounts account = call.getArgument(1);
                double next = amounts.get(account) + call.<Double>getArgument(2);
                if (next < 0) return false;
                amounts.put(account, next);
                return true;
              });
      var adapter = DenarEconomyPlayerBank.INSTANCE;
      assertEquals(id, adapter.resolve("Alice"));
      assertEquals(100, adapter.getBankBalance(id));
      assertEquals(40, adapter.getPouchBalance(id));
      assertTrue(adapter.withdrawFromBank(id, 30));
      assertTrue(adapter.depositToBank(id, 15));
      assertTrue(adapter.withdrawFromPouch(id, 10));
      assertFalse(adapter.withdrawFromBank(id, 200));
      assertFalse(adapter.withdrawFromBank(id, 0));
      assertFalse(adapter.depositToBank(id, -1));
      assertFalse(adapter.withdrawFromPouch(id, -10));
      assertEquals(4, mutations.get());
      assertEquals(85, adapter.getBankBalance(id));
      assertEquals(30, adapter.getPouchBalance(id));
    }
  }

  @Test
  void maintenanceInstructionsDescribeTheActualPayerAndSupportedCommandFlow() {
    assertTrue(VehicleMaintenanceMessages.repairBlocked().contains("maintenance pay bank"));
    assertTrue(VehicleMaintenanceMessages.payArmed().contains("from your pouch"));
    assertTrue(VehicleMaintenanceMessages.payArmed(PaymentSource.BANK).contains("from your bank"));
    assertTrue(VehicleMaintenanceMessages.notLeader().contains("from your own bank"));
    assertTrue(VehicleMaintenanceMessages.payUsage().contains("No amount needed"));
    assertTrue(VehicleMaintenanceMessages.payUsage().contains("right-click"));
    String commands = VehicleMaintenanceMessages.vehicleUsage();
    for (String command : List.of("transfer", "take", "give", "handover", "maintenance pay bank")) {
      assertTrue(commands.contains(command), command);
    }
    assertTrue(VehicleMaintenanceMessages.transferUsage().contains("<installation id|pool>"));
    assertTrue(VehicleMaintenanceMessages.bankShortfall(10, 59).contains("0h 1m"));
    assertTrue(VehicleMaintenanceMessages.bankShortfall(10, 3601).contains("1h 1m"));
  }

  @Test
  void missingMaintenanceHistoryDoesNotCreateAnEmptyFileAndValidImportsFilterDamagedRows()
      throws Exception {
    Path directory = Files.createDirectory(temporary.resolve("fresh"));
    Path file = directory.resolve("vehicle_maintenance.json");
    VehicleMaintenancePersistence persistence =
        new VehicleMaintenancePersistence(directory.toFile(), store);
    persistence.load();
    persistence.save();
    assertFalse(Files.exists(file));
    Files.writeString(file, "{\"valid\":17,\"\":99,\"missing-time\":null}");
    persistence.load();
    assertEquals(Map.of("valid", 17L), store.snapshot());
    store.clearUnpaid("valid");
    persistence.save();
    assertTrue(Files.exists(file));
    store.markUnpaid("stale", 30);
    persistence.load();
    assertTrue(store.snapshot().isEmpty(), "A valid empty map clears previous debt");
  }

  @Test
  void unreadableHistoryRetainsDebtAndExplicitMissingFileRecoveryPermitsSaving() throws Exception {
    Path file = Files.createDirectory(temporary.resolve("vehicle_maintenance.json"));
    store.markUnpaid("ship", 12);
    VehicleMaintenancePersistence persistence =
        new VehicleMaintenancePersistence(temporary.toFile(), store);

    assertDoesNotThrow(persistence::load);
    persistence.save();

    assertTrue(Files.isDirectory(file));
    assertEquals(Map.of("ship", 12L), store.snapshot());
    Files.delete(file);
    persistence.load();
    persistence.save();
    assertTrue(Files.isRegularFile(file));
    VehicleMaintenanceStore restored = new VehicleMaintenanceStore();
    new VehicleMaintenancePersistence(temporary.toFile(), restored).load();
    assertEquals(Map.of("ship", 12L), restored.snapshot());
  }

  @Test
  void failedAtomicMaintenanceSaveKeepsDestinationAndLiveDebtThenCanRetry() throws Exception {
    Path file = Files.createDirectory(temporary.resolve("vehicle_maintenance.json"));
    Path marker = file.resolve("do-not-delete");
    Files.writeString(marker, "existing directory content");
    store.markUnpaid("ship", 12);
    VehicleMaintenancePersistence persistence =
        new VehicleMaintenancePersistence(temporary.toFile(), store);

    assertDoesNotThrow(persistence::save);

    assertEquals("existing directory content", Files.readString(marker));
    assertEquals(Map.of("ship", 12L), store.snapshot());
    try (var entries = Files.list(temporary)) {
      assertTrue(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
    }
    Files.delete(marker);
    Files.delete(file);
    persistence.save();
    VehicleMaintenanceStore restored = new VehicleMaintenanceStore();
    new VehicleMaintenancePersistence(temporary.toFile(), restored).load();
    assertEquals(Map.of("ship", 12L), restored.snapshot());
  }

  private void configure(double ironcladUpkeep) throws Exception {
    YamlConfiguration config = new YamlConfiguration();
    config.set("maintenance-hourly-damage-percent", 15);
    config.set("maintenance-min-health-percent", 7);
    config.set("maintenance-interval-ticks", 90);
    config.set("categories.ships.ironclad.upkeep", ironcladUpkeep);
    config.set("categories.ships.ironclad.size", 2);
    config.set("categories.ships.raft.upkeep", 0);
    config.set("categories.ships.raft.size", 1);
    config.set("categories.ships.raft.ignore-limit", true);
    config.set("categories.aircraft.glider.upkeep", 5);
    config.set("categories.aircraft.glider.size", 1);
    Path file = temporary.resolve("vehicles.yml");
    config.save(file.toFile());
    VehiclesConfigLoader.load(file.toFile());
  }

  private ActiveVehicle vehicle(String id, String type) {
    ActiveVehicle vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn(id);
    when(vehicle.getId()).thenReturn(type);
    return vehicle;
  }

  private static OwnedVehicleSummary summary(String id, String type, String owner) {
    return new OwnedVehicleSummary(id, type, type, Optional.empty(), false, owner);
  }

  private void snapshot(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    globals.put(field, field.get(null));
  }

  private void bindPlugin(String name, Object value) throws Exception {
    Field field = SimpleFactions.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(fixture.ui.plugin, value);
  }

  private record Damage(String id, double fraction, double floor) {}

  private static final class Balances implements PlayerBank, PlayerPouch {
    final Map<String, UUID> names = new HashMap<>();
    final Map<UUID, Double> bank = new HashMap<>();
    final Map<UUID, Double> pouch = new HashMap<>();

    @Override
    public UUID resolve(String name) {
      return names.get(name);
    }

    @Override
    public double getBankBalance(UUID id) {
      return bank.getOrDefault(id, 0.0);
    }

    @Override
    public double getPouchBalance(UUID id) {
      return pouch.getOrDefault(id, 0.0);
    }

    @Override
    public boolean withdrawFromBank(UUID id, double amount) {
      return withdraw(bank, id, amount);
    }

    @Override
    public boolean withdrawFromPouch(UUID id, double amount) {
      return withdraw(pouch, id, amount);
    }

    @Override
    public boolean depositToBank(UUID id, double amount) {
      bank.put(id, getBankBalance(id) + amount);
      return true;
    }

    private boolean withdraw(Map<UUID, Double> account, UUID id, double amount) {
      double balance = account.getOrDefault(id, 0.0);
      if (balance < amount) return false;
      account.put(id, balance - amount);
      return true;
    }
  }
}
