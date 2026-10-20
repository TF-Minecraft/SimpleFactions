package net.tfminecraft.simplefactions.vehicles.berth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.objects.request.VehicleGiveConsentRequest;
import net.tfminecraft.simplefactions.objects.request.VehicleHandoverRequest;
import net.tfminecraft.simplefactions.objects.request.VehicleTransferConsentRequest;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.vehicles.VehicleIntegrationListener;
import net.tfminecraft.simplefactions.vehicles.VehicleSpawnListener;
import net.tfminecraft.simplefactions.vehicles.battle.BattleVehicleEligibilityService;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager.VehicleTransferSession;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeConfirmations;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleReclaimFeeListener;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleRegistrationFeeListener;
import net.tfminecraft.simplefactions.vehicles.fees.VfBuildersCatalog;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverListener;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverMessages;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverSessionManager;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerFields;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerMeta;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimListener;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.data.StoredVehicleMeta;
import net.tfminecraft.vehicleframework.data.VehicleRemovePayload;
import net.tfminecraft.vehicleframework.database.VehiclePersistence;
import net.tfminecraft.vehicleframework.database.VehicleRepository;
import net.tfminecraft.vehicleframework.database.VehicleSnapshot;
import net.tfminecraft.vehicleframework.enums.VehicleDeath;
import net.tfminecraft.vehicleframework.enums.VehicleRemoveReason;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.events.VehicleRemoveEvent;
import net.tfminecraft.vehicleframework.events.VehicleSpawnEvent;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vfbuilders.core.BlueprintCategory;
import net.tfminecraft.vfbuilders.events.BeginVehicleConstructionEvent;
import net.tfminecraft.vfbuilders.events.VehicleConstructEvent;
import net.tfminecraft.vfbuilders.events.VehicleConstructionCancelEvent;
import net.tfminecraft.vfbuilders.loaders.CategoryLoader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Public interaction events and request acceptance with real ownership and transfer services. */
class VehicleInteractionsCoverageTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private Faction faction;
  private Player leader;
  private Player owner;
  private Installation port;
  private PlayerVehicleRegistry registry;
  private VehicleTransferSessionManager sessions;
  private InstallationVehicleService berths;
  private FactionVehiclePoolService pools;
  private VehicleTransferConsentService consent;
  private VehicleTransferListener listener;
  private VehicleReleaseSessionManager releases;
  private FactionVehicleReleaseService releaseService;
  private FactionVehicleGiveService giveService;
  private FactionVehicleReleaseListener releaseListener;
  private VehicleHandoverSessionManager handovers;
  private VehicleHandoverService handoverService;
  private VehicleHandoverListener handoverListener;
  private VehicleFeeStore feeStore;
  private org.mockito.MockedConstruction<net.tfminecraft.simplefactions.database.Database> feeDatabase;
  private PlayerEconomyManager economy;
  private final Map<UUID, Double> balances = new HashMap<>();
  private final Map<String, OwnerFields> stored = new HashMap<>();
  private final Map<String, String> storedTypes = new HashMap<>();
  private Framework framework;
  private OwnerData ownerData;
  private ActiveVehicle vehicle;
  private PlayerVehicleRecord originalRecord;
  private final Map<String, ActiveVehicle> loaded = new HashMap<>();
  private final Map<Field, Object> globals = new LinkedHashMap<>();
  private MockedStatic<VehicleFramework> vehicleFramework;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    fixture = new FactionDomainFixture();
    for (String field :
        new String[] {
          "personalSlotLimit",
          "defaultPerPerson",
          "defaultUnpaidEffects",
          "unpaidEffectsByType",
          "maintenanceIntervalTicks",
          "categoryIds",
          "typesByCategory",
          "categoryByVehicleTypeId",
          "categoryDisplayNames",
          "feeExcludedCategories"
        }) snapshot(VehiclesConfigLoader.class, field);
    for (String field :
        new String[] {"byKind", "consentProximityBlocks", "transferRequestTimeoutSeconds"})
      snapshot(InstallationConfigLoader.class, field);
    Field requests = snapshot(RequestManager.class, "requests");
    requests.set(null, new HashMap<>());
    snapshot(VehicleFeeService.class, "factionLookup");
    snapshot(VehicleFeeService.class, "playerBank");
    Field battles = snapshot(BattleManager.class, "battles");
    battles.set(null, new ArrayList<>());
    Field warbands = snapshot(WarbandManager.class, "bands");
    warbands.set(null, new ArrayList<>());
    VehiclesConfigLoader.load(Path.of("src/main/resources/vehicles.yml").toFile());
    InstallationConfigLoader.load(Path.of("src/main/resources/installations.yml").toFile());
    faction = fixture.saved("home", "Leader");
    faction.getOrCreateMainGuild();
    leader = fixture.player("Leader");
    owner = fixture.player("Owner");
    when(Bukkit.getPlayer(any(UUID.class)))
        .thenAnswer(
            call ->
                fixture.online.values().stream()
                    .filter(p -> p.getUniqueId().equals(call.getArgument(0)))
                    .findFirst()
                    .orElse(null));
    when(owner.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, 0, 64, 0));
    port = new Installation("harbor", "Harbor", InstallationKind.PORT, 42, 0, 0, 0);
    faction.getInstallationHandler().acceptTransferred(port);
    ByteBuffer grid = ByteBuffer.allocate(8 + 16 * 16 * 2).order(ByteOrder.LITTLE_ENDIAN);
    grid.putInt(16).putInt(16);
    for (int i = 0; i < 256; i++) grid.putShort((short) 42);
    Path gridFile = files.root.resolve("province.bin.gz");
    try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gridFile))) {
      out.write(grid.array());
    }
    ProvinceGrid provinceGrid = ProvinceGrid.load(gridFile.toFile());
    when(fixture.ui.plugin.getProvinceGrid()).thenReturn(provinceGrid);
    registry = new PlayerVehicleRegistry();
    // Plugin wiring is the boundary; keep the same real registry for service and message lookups.
    Field registryField = SimpleFactions.class.getDeclaredField("vehicleRegistry");
    registryField.setAccessible(true);
    registryField.set(fixture.ui.plugin, registry);
    economy = new PlayerEconomyManager();
    Field economyField = SimpleFactions.class.getDeclaredField("playerEconomyManager");
    economyField.setAccessible(true);
    economyField.set(fixture.ui.plugin, economy);
    when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(true);
    sessions = new VehicleTransferSessionManager();
    InstallationVehicleOwnerSync sync = new InstallationVehicleOwnerSync(registry);
    berths = new InstallationVehicleService(registry, sync);
    pools = new FactionVehiclePoolService(registry, sync);
    consent = new VehicleTransferConsentService(berths, sessions, pools);
    listener = new VehicleTransferListener(sessions, registry, berths, consent, pools);
    when(fixture.ui.plugin.getVehicleTransferConsentService()).thenReturn(consent);
    VehicleManager manager = mock(VehicleManager.class);
    when(manager.get(anyString())).thenAnswer(call -> loaded.get(call.getArgument(0)));
    vehicleFramework = mockStatic(VehicleFramework.class);
    vehicleFramework.when(VehicleFramework::getVehicleManager).thenReturn(manager);
    framework = new Framework();
    VehicleOwnerWrites.use(framework);
    // The vehicle stage has finished, so builds may start.
    net.tfminecraft.simplefactions.vehicles.owners.LeaderStamps.enable(true);
    ownerData = new OwnerData();
    ownerData.setOwner(tag("Owner"));
    vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn("vehicle-1");
    when(vehicle.getId()).thenReturn("ironclad");
    when(vehicle.getOwnerData()).thenReturn(ownerData);
    when(vehicle.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, 0, 64, 0));
    loaded.put("vehicle-1", vehicle);
    originalRecord =
        new PlayerVehicleRecord(
            owner.getUniqueId(), "vehicle-1", "ironclad", OwnershipMode.PERSONAL, null, null,
            owner.getUniqueId());
    registry.register(originalRecord);
    releases = new VehicleReleaseSessionManager();
    releaseService = new FactionVehicleReleaseService(registry);
    giveService = new FactionVehicleGiveService(releaseService);
    releaseListener = new FactionVehicleReleaseListener(releases, releaseService, giveService);
    when(fixture.ui.plugin.getFactionVehicleGiveService()).thenReturn(giveService);
    handovers = new VehicleHandoverSessionManager();
    feeStore = new VehicleFeeStore();
    feeStore.bind(files.root.toFile());
    feeDatabase = org.mockito.Mockito.mockConstruction(net.tfminecraft.simplefactions.database.Database.class,
        (value, context) -> when(value.saveFactionDurably(any())).thenReturn(true));
    net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.startup(files.root, feeStore, new net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore());
    handoverService = new VehicleHandoverService(registry, feeStore, feeStore::save);
    handoverListener =
        new VehicleHandoverListener(handovers, handoverService, new VehicleFeeConfirmations());
    when(fixture.ui.plugin.getVehicleHandoverService()).thenReturn(handoverService);
    VehicleFeeService.setForTests(
        null,
        new PlayerBank() {
          @Override
          public double getBankBalance(UUID id) {
            return balances.getOrDefault(id, 0.0);
          }
          public boolean withdrawFromBank(UUID id, double amount) {
            if (amount <= 0 || getBankBalance(id) < amount) return false;
            balances.put(id, getBankBalance(id) - amount);
            return true;
          }
          public boolean depositToBank(UUID id, double amount) {
            balances.put(id, getBankBalance(id) + amount);
            return true;
          }

          @Override
          public UUID resolve(String name) {
            Player player = fixture.online.get(name);
            return player == null ? null : player.getUniqueId();
          }
        });
  }

  private static void fund(Player player, double amount) {
    var ref = net.tfminecraft.simplefactions.identity.Members.session(player).orElseThrow().ref();
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(net.tfminecraft.denareconomy.api.Denars.bank(ref), Math.round(amount * 100));
  }
  private static double balance(Player player) {
    var ref = net.tfminecraft.simplefactions.identity.Members.session(player).orElseThrow().ref();
    return net.tfminecraft.denareconomy.api.Denars.balance(net.tfminecraft.denareconomy.api.Denars.bank(ref)) / 100.0;
  }

  @AfterEach
  @SuppressWarnings({"unchecked", "rawtypes"})
  void close() throws Exception {
    net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.stop();
    if (feeDatabase != null) feeDatabase.close();
    try {
      if (vehicleFramework != null) vehicleFramework.close();
      for (var entry : globals.entrySet()) {
        if (Modifier.isFinal(entry.getKey().getModifiers())) {
          Map target = (Map) entry.getKey().get(null);
          target.clear();
          target.putAll((Map) entry.getValue());
        } else entry.getKey().set(null, entry.getValue());
      }
      fixture.close();
    } finally {
      files.close();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ownersAtTheSameCoordinatesInAnotherWorldCannotAuthorizeTransfers(boolean pool) {
    arm(pool);
    World elsewhere = mock(World.class);
    when(owner.getLocation()).thenAnswer(call -> new Location(elsewhere, 0, 64, 0));
    VehiclePreInteractEvent event = click();
    assertTrue(event.isCancelled());
    assertFalse(RequestManager.hasRequest(owner), "Another world is not within consent range");
    assertNotNull(sessions.get(leader.getUniqueId()));
    assertPersonalOwnershipUnchanged();
    verify(leader).sendMessage(VehicleTransferMessages.ownerTooFar(20));
  }

  @Test
  void aVehicleInAnotherProvinceWithinRadiusCanBeBerthed() {
    arm(false);
    when(vehicle.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, -1, 64, 0));
    assertTrue(click().isCancelled());
    assertNull(sessions.get(leader.getUniqueId()));
    var request =
        assertInstanceOf(VehicleTransferConsentRequest.class, RequestManager.getRequest(owner));
    assertEquals("Harbor", request.getInstallationName());
    assertPersonalOwnershipUnchanged();
    RequestManager.accept(owner);
    PlayerVehicleRecord updated = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    assertEquals(OwnershipMode.INSTALLATION, updated.getMode());
    assertEquals("harbor", updated.getInstallationId());
    verify(fixture.ui.plugin).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nearbyOwnersAcceptIntoTheRequestedFactionStoreExactlyOnce(boolean pool) {
    arm(pool);
    assertTrue(click().isCancelled());
    assertNull(sessions.get(leader.getUniqueId()));
    var request =
        assertInstanceOf(VehicleTransferConsentRequest.class, RequestManager.getRequest(owner));
    assertEquals(pool, request.isPool());
    assertEquals(pool ? "faction vehicle pool" : "Harbor", request.getInstallationName());
    assertEquals(owner.getUniqueId(), request.getOwnerUuid());
    assertPersonalOwnershipUnchanged();
    RequestManager.accept(owner);
    PlayerVehicleRecord updated = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    assertEquals(pool ? OwnershipMode.POOL : OwnershipMode.INSTALLATION, updated.getMode());
    assertEquals(pool ? null : "harbor", updated.getInstallationId());
    assertEquals("home", updated.getFactionId());
    assertEquals(owner.getUniqueId(), updated.getPlayerUuid());
    assertEquals("char_none", ownerData.getOwner(), "Name-keyed fixture leaders have no character");
    assertFalse(RequestManager.hasRequest(owner));
    RequestManager.accept(owner);
    assertSame(updated, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    verify(fixture.ui.plugin).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void blockedConsentRequestsDoNotReportSuccessOrConsumeTheArmedTransfer(boolean pool) {
    Request prior = new Request(faction.getOrCreateMainGuild());
    RequestManager.addRequest(owner, owner, prior);
    arm(pool);
    assertTrue(click().isCancelled());
    assertSame(prior, RequestManager.getRequest(owner));
    assertNotNull(sessions.get(leader.getUniqueId()));
    verify(leader).sendMessage(contains("already considering another request"));
    verify(leader, never()).sendMessage(VehicleTransferMessages.consentSent("Owner"));
    verify(owner, never()).sendMessage(contains("It will become a faction vehicle"));
    assertPersonalOwnershipUnchanged();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pendingConsentCannotBeRedirectedWhenItsProposerLeadsADifferentFaction(boolean pool) {
    arm(pool);
    click();
    assertTrue(RequestManager.hasRequest(owner));
    faction.setLeader("Successor");
    Faction next = fixture.saved("next", "Leader");
    next.getOrCreateMainGuild();
    next.getInstallationHandler()
        .acceptTransferred(
            new Installation("harbor", "Other Harbor", InstallationKind.PORT, 42, 0, 0, 0));
    RequestManager.accept(owner);
    assertFalse(RequestManager.hasRequest(owner));
    assertPersonalOwnershipUnchanged();
    verify(owner).sendMessage(VehicleTransferMessages.consentExpired());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void theCurrentLeaderCanTransferTheirOwnVehicleWithoutAConsentRequest(boolean pool) {
    arm(pool);
    ownerData.setOwner(tag("Leader"));
    assertTrue(click().isCancelled());
    assertNull(sessions.get(leader.getUniqueId()));
    assertFalse(RequestManager.hasRequest(owner));
    var record = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    assertEquals(pool ? OwnershipMode.POOL : OwnershipMode.INSTALLATION, record.getMode());
    assertEquals(leader.getUniqueId(), record.getPlayerUuid());
    assertEquals("home", record.getFactionId());
    assertEquals("char_none", ownerData.getOwner(), "Name-keyed fixture leaders have no character");
    verify(fixture.ui.plugin).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no_session",
        "expired",
        "null_player",
        "former_leader",
        "missing_installation",
        "null_vehicle",
        "null_uuid",
        "pool_null_vehicle",
        "pool_null_uuid"
      })
  void staleOrIncompleteInteractionContextsNeverTransferOwnership(String state) {
    arm(state.startsWith("pool_"));
    if (state.equals("no_session")) sessions.clear(leader.getUniqueId());
    if (state.equals("expired"))
      sessions.put(
          leader.getUniqueId(),
          new VehicleTransferSession("harbor", System.currentTimeMillis() - 1));
    if (state.equals("former_leader")) faction.setLeader("Successor");
    if (state.equals("missing_installation")) faction.getInstallationHandler().detachOnProvince(42);
    if (state.endsWith("null_uuid")) when(vehicle.getUUID()).thenReturn(null);
    var event =
        new VehiclePreInteractEvent(
            state.equals("null_player") ? null : leader,
            state.endsWith("null_vehicle") ? null : vehicle);
    listener.onVehiclePreInteract(event);
    assertEquals(
        state.equals("former_leader") || state.equals("missing_installation"), event.isCancelled());
    if (state.equals("former_leader")
        || state.equals("missing_installation")
        || state.equals("expired")) assertNull(sessions.get(leader.getUniqueId()));
    assertFalse(RequestManager.hasRequest(owner));
    assertPersonalOwnershipUnchanged();
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "berth_missing",
        "pool_missing",
        "berth_offline",
        "pool_offline",
        "berth_far",
        "pool_far",
        "berth_unknown",
        "pool_unknown",
        "berth_radius",
        "berth_capacity",
        "pool_must_berth",
        "berth_unowned",
        "pool_unowned"
      })
  void failedOwnerAndStoreValidationKeepsTheSelectionArmedAndTheVehiclePersonal(String state) {
    boolean pool = state.startsWith("pool_");
    arm(pool);
    if (state.endsWith("missing")) fixture.online.remove("Owner");
    if (state.endsWith("offline")) when(owner.isOnline()).thenReturn(false);
    if (state.endsWith("far"))
      when(owner.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, 21, 64, 0));
    if (state.endsWith("unknown")) when(vehicle.getId()).thenReturn("unknown");
    if (state.endsWith("must_berth")) when(vehicle.getId()).thenReturn("ironclad");
    if (state.endsWith("radius")) {
      when(vehicle.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, 90, 64, 0));
      when(owner.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, 90, 64, 0));
    }
    if (state.endsWith("capacity")) fillPort();
    if (state.endsWith("unowned")) ownerData.setOwner("none");
    int count = registry.getAll().size();
    String previousOwner = ownerData.getOwner();
    assertTrue(click().isCancelled());
    assertNotNull(sessions.get(leader.getUniqueId()));
    assertFalse(RequestManager.hasRequest(owner));
    assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(previousOwner, ownerData.getOwner());
    assertEquals(count, registry.getAll().size());
    verify(leader).sendMessage(startsWith("§c"));
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aLeadersOwnInvalidVehicleIsRefusedByTheSameStoreValidation(boolean pool) {
    arm(pool);
    ownerData.setOwner(tag("Leader"));
    when(vehicle.getId()).thenReturn("unknown");
    assertTrue(click().isCancelled());
    assertNotNull(sessions.get(leader.getUniqueId()));
    assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Leader"), ownerData.getOwner());
    verify(leader).sendMessage(contains("not registered for faction upkeep"));
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "berth_unloaded",
        "pool_unloaded",
        "berth_changed_owner",
        "pool_changed_owner",
        "berth_unknown_type",
        "pool_unknown_type",
        "berth_removed_installation",
        "berth_former_leader",
        "pool_former_leader",
        "berth_removed_faction",
        "pool_removed_faction"
      })
  void consentAcceptanceRechecksTheCurrentVehicleAndFactionBeforeWritingOwnership(String state) {
    arm(state.startsWith("pool_"));
    click();
    assertTrue(RequestManager.hasRequest(owner));
    if (state.endsWith("unloaded")) loaded.clear();
    if (state.endsWith("changed_owner")) ownerData.setOwner(tag("Buyer"));
    if (state.endsWith("unknown_type")) when(vehicle.getId()).thenReturn("unknown");
    if (state.endsWith("removed_installation"))
      faction.getInstallationHandler().detachOnProvince(42);
    if (state.endsWith("former_leader")) faction.setLeader("Successor");
    if (state.endsWith("removed_faction"))
      net.tfminecraft.simplefactions.managers.FactionManager.factions.remove(faction);
    String previousOwner = ownerData.getOwner();
    RequestManager.accept(owner);
    assertFalse(RequestManager.hasRequest(owner));
    assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(previousOwner, ownerData.getOwner());
    verify(owner, atLeastOnce()).sendMessage(startsWith("§c"));
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @Test
  void requestUuidGuardsAndMissingFrameworkObjectsDoNotWriteOwnership() {
    consent.acceptRequest(owner);
    consent.sendConsentRequest(null, owner, faction, port, vehicle);
    consent.sendPoolConsentRequest(leader, null, faction, vehicle);
    assertFalse(RequestManager.hasRequest(owner));
    var request =
        new VehicleTransferConsentRequest(
            faction.getOrCreateMainGuild(),
            "harbor",
            "Harbor",
            "vehicle-1",
            "ironclad",
            new net.tfminecraft.tlibs.character.CharacterRef(UUID.randomUUID(), UUID.randomUUID()),
            ref(leader),
            false);
    RequestManager.addRequest(leader, owner, request);
    consent.acceptRequest(owner);
    verify(owner).sendMessage("§cYou cannot accept this request.");
    assertSame(request, RequestManager.getRequest(owner));
    assertNull(consent.resolveVehicle(null));
    assertNull(consent.resolveBerthTarget("missing"));
    assertNull(consent.resolvePoolTarget("missing"));
    assertNull(consent.currentOwnerEntry("missing"));
    when(vehicle.getOwnerData()).thenReturn(null);
    assertNull(consent.currentOwnerEntry("vehicle-1"));
    assertPersonalOwnershipUnchanged();
  }

  @Test
  void expiredRequestNotificationsOnlyReachOnlineParticipants() {
    var request =
        new VehicleTransferConsentRequest(
            faction.getOrCreateMainGuild(),
            "harbor",
            "Harbor",
            "vehicle-1",
            "ironclad",
            ref(owner),
            ref(leader),
            false);
    consent.notifyExpired(null, owner);
    verify(owner, never()).sendMessage(anyString());
    consent.notifyExpired(request, owner);
    verify(owner).sendMessage(VehicleTransferMessages.consentExpired());
    verify(leader).sendMessage(VehicleTransferMessages.consentExpired());
    when(owner.isOnline()).thenReturn(false);
    when(leader.isOnline()).thenReturn(false);
    consent.notifyExpired(request, owner);
    verify(owner, times(1)).sendMessage(anyString());
    verify(leader, times(1)).sendMessage(anyString());
    assertPersonalOwnershipUnchanged();
  }

  @ParameterizedTest
  @ValueSource(strings = {"berth_leader", "pool_leader", "berth_consent", "pool_consent"})
  void aFailedRegistrySavePreservesBothTheOriginalRecordAndTheFrameworkOwner(String route) {
    arm(route.startsWith("pool_"));
    boolean awaitingConsent = route.endsWith("consent");
    if (awaitingConsent) {
      click();
      assertTrue(RequestManager.hasRequest(owner));
    } else {
      ownerData.setOwner(tag("Leader"));
    }
    String priorOwner = ownerData.getOwner();
    when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(false);
    if (awaitingConsent) RequestManager.accept(owner);
    else click();
    assertSame(
        originalRecord,
        registry.getByVehicleUuid("vehicle-1").orElseThrow(),
        "A failed save must preserve the original ownership record");
    assertEquals(priorOwner, ownerData.getOwner());
    assertEquals(1, registry.getAll().size());
    verify(leader, never()).sendMessage(startsWith("§aVehicle"));
    verify(owner, never()).sendMessage(startsWith("§aVehicle"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void consentThatHasTimedOutCannotTransferWhileThePeriodicExpiryTaskIsWaiting(boolean pool)
      throws Exception {
    Path config =
        files.write(
            "short-consent.yml",
            Files.readString(Path.of("src/main/resources/installations.yml"))
                .replace(
                    "transfer-request-timeout-seconds: 60", "transfer-request-timeout-seconds: 1"));
    InstallationConfigLoader.load(config.toFile());
    arm(pool);
    click();
    Request request = RequestManager.getRequest(owner);
    assertNotNull(request);
    Thread.sleep(1100);
    assertTrue(request.timedOut());
    RequestManager.accept(owner);
    assertFalse(RequestManager.hasRequest(owner));
    assertPersonalOwnershipUnchanged();
    verify(owner).sendMessage(VehicleTransferMessages.consentExpired());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void originalVoidConsentApisStillCreateTheExpectedRequest(boolean pool) {
    arm(pool);
    if (pool) consent.sendPoolConsentRequest(leader, owner, faction, vehicle);
    else consent.sendConsentRequest(leader, owner, faction, port, vehicle);
    var request =
        assertInstanceOf(VehicleTransferConsentRequest.class, RequestManager.getRequest(owner));
    assertEquals(pool, request.isPool());
    assertEquals(pool ? "faction vehicle pool" : "Harbor", request.getInstallationName());
    assertEquals("home", request.getDestinationFactionId());
    assertEquals("vehicle-1", request.getVehicleUuid());
    assertEquals(owner.getUniqueId(), request.getOwnerUuid());
    verify(leader).sendMessage(VehicleTransferMessages.consentSent("Owner"));
    assertPersonalOwnershipUnchanged();
  }

  @Test
  void requestDestinationDoesNotFollowItsSenderGuildWhenTheGuildMoves() {
    var guild = fixture.guild(faction, "company", "Guild Leader");
    var request =
        new VehicleTransferConsentRequest(
            guild,
            "harbor",
            "Harbor",
            "vehicle-1",
            "ironclad",
            ref(owner),
            ref(leader),
            false);
    Faction destination = fixture.saved("next", "Next Leader");
    guild.relocate(destination, -1);
    assertSame(destination, guild.getFaction());
    assertEquals("home", request.getDestinationFactionId());
    assertEquals("harbor", request.getInstallationId());
    assertPersonalOwnershipUnchanged();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void factionTakeRecordsTheNewPersonalPayer(boolean pool) {
    factionVehicle(pool);
    releases.put(
        leader.getUniqueId(),
        new VehicleReleaseSessionManager.VehicleReleaseSession(
            VehicleReleaseSessionManager.Kind.TAKE, System.currentTimeMillis() + 60000));
    var event = new VehiclePreInteractEvent(leader, vehicle);
    releaseListener.onVehiclePreInteract(event);
    assertTrue(event.isCancelled());
    assertEquals(OwnershipMode.PERSONAL, registry.getByVehicleUuid("vehicle-1").orElseThrow().getMode());
    assertEquals(tag("Leader"), ownerData.getOwner());
    assertNull(releases.get(leader.getUniqueId()));
    verify(fixture.ui.plugin).saveVehicleRegistry();
    verify(fixture.ui.plugin).recordVehicleOwner("vehicle-1", "Leader");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void factionGiveWaitsForTheExactRecipientAndTransfersOnlyOnAcceptance(boolean pool) {
    factionVehicle(pool);
    armGive(owner);
    var event = new VehiclePreInteractEvent(leader, vehicle);
    releaseListener.onVehiclePreInteract(event);
    assertTrue(event.isCancelled());
    assertTrue(RequestManager.hasRequest(owner));
    assertEquals(
        OwnershipMode.valueOf(pool ? "POOL" : "INSTALLATION"),
        registry.getByVehicleUuid("vehicle-1").orElseThrow().getMode());
    assertEquals(tag("Leader"), ownerData.getOwner());
    assertNull(releases.get(leader.getUniqueId()));
    RequestManager.accept(owner);
    assertFalse(RequestManager.hasRequest(owner));
    assertEquals(OwnershipMode.PERSONAL, registry.getByVehicleUuid("vehicle-1").orElseThrow().getMode());
    assertEquals(tag("Owner"), ownerData.getOwner());
    verify(fixture.ui.plugin).recordVehicleOwner("vehicle-1", "Owner");
    verify(fixture.ui.plugin).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no_session",
        "no_player",
        "former_leader",
        "no_vehicle",
        "no_uuid",
        "missing_recipient",
        "offline_recipient",
        "replaced_recipient",
        "not_faction_vehicle",
        "unknown_type",
        "recipient_full",
        "battle"
      })
  void releaseAndGiveRefusalsPreserveTheVehicleAndClearOnlyInvalidSelections(String state) {
    factionVehicle(false);
    armGive(owner);
    if (state.equals("no_session")) releases.clear(leader.getUniqueId());
    if (state.equals("former_leader")) faction.setLeader("Successor");
    if (state.equals("no_uuid")) when(vehicle.getUUID()).thenReturn(null);
    if (state.equals("missing_recipient")) fixture.online.remove("Owner");
    if (state.equals("offline_recipient")) when(owner.isOnline()).thenReturn(false);
    if (state.equals("replaced_recipient")) {
      Player replacement = fixture.player("Owner");
      when(replacement.getUniqueId())
          .thenReturn(UUID.nameUUIDFromBytes("replacement-owner".getBytes()));
    }
    if (state.equals("not_faction_vehicle")) registry.register(originalRecord);
    if (state.equals("unknown_type"))
      registry.register(
          new PlayerVehicleRecord(
              owner.getUniqueId(),
              "vehicle-1",
              "missing",
              OwnershipMode.INSTALLATION,
              "harbor",
              "home"));
    if (state.equals("recipient_full"))
      storeOwned("other", "ironclad", "Owner");
    if (state.equals("battle")) startBattle();
    var before = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    var event =
        new VehiclePreInteractEvent(
            state.equals("no_player") ? null : leader, state.equals("no_vehicle") ? null : vehicle);
    releaseListener.onVehiclePreInteract(event);
    assertEquals(!state.equals("no_session") && !state.equals("no_player"), event.isCancelled());
    assertSame(before, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Leader"), ownerData.getOwner());
    assertFalse(RequestManager.hasRequest(owner));
    assertEquals(
        state.equals("no_session") || state.equals("former_leader") || state.equals("battle"),
        releases.get(leader.getUniqueId()) == null);
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(strings = {"personal", "battle", "save_failed"})
  void takeFailuresPreserveOwnershipAndPermitRetryWhenAppropriate(String state) {
    factionVehicle(false);
    if (state.equals("personal")) registry.register(originalRecord);
    if (state.equals("battle")) startBattle();
    if (state.equals("save_failed"))
      when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(false);
    var before = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    releases.put(
        leader.getUniqueId(),
        new VehicleReleaseSessionManager.VehicleReleaseSession(
            VehicleReleaseSessionManager.Kind.TAKE, System.currentTimeMillis() + 60000));
    var event = new VehiclePreInteractEvent(leader, vehicle);
    releaseListener.onVehiclePreInteract(event);
    assertTrue(event.isCancelled());
    assertSame(before, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Leader"), ownerData.getOwner());
    assertEquals(state.equals("battle"), releases.get(leader.getUniqueId()) == null);
    verify(leader).sendMessage(startsWith("§c"));
    verify(fixture.ui.plugin, never()).recordVehicleOwner(anyString(), anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"success", "unaffordable", "ownership_failure"})
  void personalHandoverCollectsOrRefundsTheConfirmedFeeAndMovesOwnershipExactlyOnce(String state) {
    Player recipient = fixture.player("Recipient");
    enableTransferFee();
    fund(owner, state.equals("unaffordable") ? 10.0 : 100.0);
    handovers.put(
        owner.getUniqueId(),
        new VehicleHandoverSessionManager.Session(
            "Recipient", recipient.getUniqueId(), System.currentTimeMillis() + 60000));
    var event = new VehiclePreInteractEvent(owner, vehicle);
    handoverListener.onVehiclePreInteract(event);
    assertTrue(event.isCancelled());
    assertFalse(RequestManager.hasRequest(recipient));
    assertNotNull(handovers.get(owner.getUniqueId()));
    assertEquals(state.equals("unaffordable") ? 10.0 : 100.0, balance(owner));
    handoverListener.onVehiclePreInteract(new VehiclePreInteractEvent(owner, vehicle));
    assertTrue(RequestManager.hasRequest(recipient));
    assertNull(handovers.get(owner.getUniqueId()));
    if (state.equals("ownership_failure")) framework.forced = Result.SAVE_FAILED;
    RequestManager.accept(recipient);
    boolean successful = state.equals("success");
    if (state.equals("ownership_failure")) {
      // The recipient's payer row went first and was taken back.
      assertEquals(originalRecord.getPlayerUuid(), registry.getByVehicleUuid("vehicle-1").orElseThrow().getPlayerUuid());
      assertEquals(List.of("vehicle-1 " + tag("Recipient") + " HANDOVER"), framework.writes);
    }
    assertEquals(successful ? tag("Recipient") : tag("Owner"), ownerData.getOwner());
    assertEquals(
        successful ? 60.0 : state.equals("unaffordable") ? 10.0 : 100.0,
        balance(owner));
    assertEquals(successful ? 1040.0 : 1000.0, faction.getBank().getWealth());
    assertEquals(
        successful ? -40.0 : 0.0,
        economy.getLedger(net.tfminecraft.simplefactions.identity.Members.session(owner).orElseThrow().ref().characterId()).getAmount(PlayerCashflow.VEHICLE_FEES));
    assertEquals(successful ? "Recipient" : null, feeStore.getLastOwner("vehicle-1"));
    assertFalse(RequestManager.hasRequest(recipient));
    RequestManager.accept(recipient);
    assertEquals(
        successful ? 60.0 : state.equals("unaffordable") ? 10.0 : 100.0,
        balance(owner));
    if (successful) {
      VehicleFeeStore persisted = new VehicleFeeStore();
      persisted.bind(files.root.toFile());
      persisted.load();
      assertEquals("Recipient", persisted.getLastOwner("vehicle-1"));
    }
  }

  @Test
  void pendingHandoverCannotChargeMoreThanTheFeeConfirmedByTheOwner() {
    Player recipient = fixture.player("Recipient");
    enableTransferFee();
    fund(owner, 100.0);
    handovers.put(
        owner.getUniqueId(),
        new VehicleHandoverSessionManager.Session(
            "Recipient", recipient.getUniqueId(), System.currentTimeMillis() + 60000));
    handoverListener.onVehiclePreInteract(new VehiclePreInteractEvent(owner, vehicle));
    assertFalse(RequestManager.hasRequest(recipient));
    handoverListener.onVehiclePreInteract(new VehiclePreInteractEvent(owner, vehicle));
    assertTrue(RequestManager.hasRequest(recipient));
    assertEquals(100.0, balance(owner));
    faction.getVehicleFeeHandler().setRate(FeeKind.TRANSFER_FEE, null, 3.0);

    RequestManager.accept(recipient);

    assertAll(
        () -> assertEquals(tag("Owner"), ownerData.getOwner()),
        () -> assertEquals(100.0, balance(owner)),
        () -> assertEquals(1000.0, faction.getBank().getWealth()));
    assertEquals(1000.0, faction.getBank().getWealth());
    assertEquals(
        0.0, economy.getLedger(net.tfminecraft.simplefactions.identity.Members.session(owner).orElseThrow().ref().characterId()).getAmount(PlayerCashflow.VEHICLE_FEES));
    assertNull(feeStore.getLastOwner("vehicle-1"));
    assertFalse(RequestManager.hasRequest(recipient));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no_session",
        "no_player",
        "no_vehicle",
        "no_uuid",
        "missing_recipient",
        "offline_recipient",
        "replaced_recipient",
        "not_owner",
        "unknown_type",
        "recipient_full",
        "battle"
      })
  void personalHandoverSelectionRejectsChangedParticipantsAndUnavailableVehicles(String state) {
    Player recipient = fixture.player("Recipient");
    handovers.put(
        owner.getUniqueId(),
        new VehicleHandoverSessionManager.Session(
            "Recipient", recipient.getUniqueId(), System.currentTimeMillis() + 60000));
    if (state.equals("no_session")) handovers.clear(owner.getUniqueId());
    if (state.equals("no_uuid")) when(vehicle.getUUID()).thenReturn(null);
    if (state.equals("missing_recipient")) fixture.online.remove("Recipient");
    if (state.equals("offline_recipient")) when(recipient.isOnline()).thenReturn(false);
    if (state.equals("replaced_recipient")) {
      Player replacement = fixture.player("Recipient");
      when(replacement.getUniqueId()).thenReturn(UUID.randomUUID());
    }
    if (state.equals("not_owner")) ownerData.setOwner(tag("Another"));
    if (state.equals("unknown_type")) when(vehicle.getId()).thenReturn("unknown");
    if (state.equals("recipient_full"))
      storeOwned("other", "ironclad", "Recipient");
    if (state.equals("battle")) {
      faction.addMember("Owner");
      startBattle();
    }
    String beforeOwner = ownerData.getOwner();
    var event =
        new VehiclePreInteractEvent(
            state.equals("no_player") ? null : owner, state.equals("no_vehicle") ? null : vehicle);
    handoverListener.onVehiclePreInteract(event);
    assertEquals(!state.equals("no_session") && !state.equals("no_player"), event.isCancelled());
    assertFalse(RequestManager.hasRequest(recipient));
    assertEquals(beforeOwner, ownerData.getOwner());
    assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    boolean retained = List.of("no_player", "no_vehicle", "no_uuid", "not_owner").contains(state);
    assertEquals(retained, handovers.get(owner.getUniqueId()) != null);
    assertNull(feeStore.getLastOwner("vehicle-1"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"wrong_uuid", "changed_owner", "unknown_type", "recipient_full", "battle"})
  void personalHandoverAcceptanceRechecksCurrentOwnershipRecipientCapacityAndBattle(String state) {
    Player recipient = fixture.player("Recipient");
    handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
    var request =
        assertInstanceOf(VehicleHandoverRequest.class, RequestManager.getRequest(recipient));
    assertEquals("ironclad", request.getVehicleTypeId());
    if (state.equals("wrong_uuid")) when(recipient.getUniqueId()).thenReturn(UUID.randomUUID());
    if (state.equals("changed_owner")) ownerData.setOwner(tag("Another"));
    if (state.equals("unknown_type")) when(vehicle.getId()).thenReturn("unknown");
    if (state.equals("recipient_full"))
      storeOwned("other", "ironclad", "Recipient");
    if (state.equals("battle")) {
      faction.addMember("Owner");
      startBattle();
    }
    String beforeOwner = ownerData.getOwner();
    RequestManager.accept(recipient);
    assertEquals(beforeOwner, ownerData.getOwner());
    assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertNull(feeStore.getLastOwner("vehicle-1"));
    assertFalse(RequestManager.hasRequest(recipient));
    verify(recipient).sendMessage(startsWith("§c"));
  }

  @Test
  void publicHandoverWithNullTypeRejectsWithoutChargingChangingOwnershipOrSaving()
      throws Exception {
    Player recipient = fixture.player("Recipient");
    enableTransferFee();
    fund(owner, 100.0);
    assertEquals(40.0, VehicleFeeService.quote(FeeKind.TRANSFER_FEE, "Owner", "ironclad").amount());
    AtomicInteger saves = new AtomicInteger();
    handoverService =
        new VehicleHandoverService(
            registry,
            feeStore,
            () -> {
              saves.incrementAndGet();
              feeStore.save();
            });
    when(fixture.ui.plugin.getVehicleHandoverService()).thenReturn(handoverService);
    feeStore.setLastOwner("vehicle-1", "Owner");
    feeStore.save();
    Path feesFile = files.root.resolve("vehicle_fees.json");
    byte[] savedFees = Files.readAllBytes(feesFile);

    handoverService.offer(owner, recipient, "vehicle-1", null);
    VehicleHandoverRequest request =
        assertInstanceOf(VehicleHandoverRequest.class, RequestManager.getRequest(recipient));
    assertNull(request.getVehicleTypeId());
    try (MockedStatic<VehiclePersistence> persistence = mockStatic(VehiclePersistence.class)) {
      assertDoesNotThrow(() -> RequestManager.accept(recipient));
      persistence.verifyNoInteractions();
    }

    assertFalse(RequestManager.hasRequest(recipient));
    assertEquals(tag("Owner"), ownerData.getOwner());
    assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(100.0, balance(owner));
    assertEquals(1000.0, faction.getBank().getWealth());
    assertEquals(
        0.0, economy.getLedger(net.tfminecraft.simplefactions.identity.Members.session(owner).orElseThrow().ref().characterId()).getAmount(PlayerCashflow.VEHICLE_FEES));
    assertEquals("Owner", feeStore.getLastOwner("vehicle-1"));
    assertEquals(0, saves.get());
    assertArrayEquals(savedFees, Files.readAllBytes(feesFile));
    verify(recipient).sendMessage(VehicleHandoverMessages.feeChanged());
    verify(owner).sendMessage(VehicleHandoverMessages.feeChanged());
  }

  @Test
  void handoverPreservesAnExistingRequestAndIgnoresUnrelatedAcceptance() {
    Player recipient = fixture.player("Recipient");
    Request prior = new Request(faction.getOrCreateMainGuild());
    RequestManager.addRequest(leader, recipient, prior);
    handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
    assertSame(prior, RequestManager.getRequest(recipient));
    handoverService.acceptRequest(recipient);
    handoverService.acceptRequest(leader);
    assertPersonalOwnershipUnchanged();
    verify(recipient, never()).sendMessage(anyString());
    verify(owner).sendMessage("§cThe target is already considering another request.");
    assertEquals(
        VehicleHandoverService.Status.NOT_OWNER,
        handoverService.evaluate(null, recipient.getUniqueId(), "vehicle-1").status());
    assertEquals(
        VehicleHandoverService.Status.NOT_OWNER,
        handoverService.evaluate(ref(owner), recipient.getUniqueId(), null).status());
    factionVehicle(false);
    assertEquals(
        VehicleHandoverService.Status.NOT_OWNER,
        handoverService.evaluate(ref(leader), recipient.getUniqueId(), "vehicle-1").status());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void giftAndHandoverExpireBeforeThePeriodicRequestSweep(boolean gift) throws Exception {
    InstallationConfigLoader.load(
        files
            .write(
                "short-gift.yml",
                Files.readString(Path.of("src/main/resources/installations.yml"))
                    .replace(
                        "transfer-request-timeout-seconds: 60",
                        "transfer-request-timeout-seconds: 1"))
            .toFile());
    Player recipient = fixture.player("Recipient");
    if (gift) {
      factionVehicle(false);
      giveService.offer(leader, recipient, faction, "vehicle-1", "ironclad");
    } else handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
    Request request = RequestManager.getRequest(recipient);
    assertNotNull(request);
    String priorOwner = ownerData.getOwner();
    var priorRecord = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    Thread.sleep(1100);
    assertTrue(request.timedOut());
    RequestManager.accept(recipient);
    assertFalse(RequestManager.hasRequest(recipient));
    assertSame(priorRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(priorOwner, ownerData.getOwner());
    verify(recipient)
        .sendMessage(
            gift ? FactionVehicleReleaseMessages.giveExpired() : VehicleHandoverMessages.expired());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong_uuid", "missing_faction", "changed_leader", "absent_leader"})
  void factionGiftAcceptanceRechecksTheCapturedFactionAndRecipient(String state) {
    factionVehicle(false);
    giveService.offer(leader, owner, faction, "vehicle-1", "ironclad");
    var request =
        assertInstanceOf(VehicleGiveConsentRequest.class, RequestManager.getRequest(owner));
    assertEquals("ironclad", request.getVehicleTypeId());
    if (state.equals("wrong_uuid")) when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
    if (state.equals("missing_faction")) FactionManager.factions.remove(faction);
    if (state.equals("changed_leader")) faction.setLeader("Successor");
    if (state.equals("absent_leader")) faction.setLeader(null);
    var priorRecord = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    RequestManager.accept(owner);
    assertFalse(RequestManager.hasRequest(owner));
    assertSame(priorRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Leader"), ownerData.getOwner());
    verify(owner).sendMessage(startsWith("§c"));
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @Test
  void missingGiftInputsAndUnrelatedRequestsDoNotIssueOrAcceptOwnershipChanges() {
    giveService.offer(null, owner, faction, "vehicle-1", "ironclad");
    giveService.offer(leader, null, faction, "vehicle-1", "ironclad");
    giveService.offer(leader, owner, null, "vehicle-1", "ironclad");
    giveService.offer(leader, owner, faction, null, "ironclad");
    assertFalse(RequestManager.hasRequest(owner));
    giveService.acceptRequest(owner);
    giveService.notifyExpired(null, owner);
    assertPersonalOwnershipUnchanged();
    verify(owner, never()).sendMessage(anyString());
    var orphaned =
        new VehicleGiveConsentRequest(
            faction.getOrCreateMainGuild(),
            "home",
            "vehicle-1",
            "ironclad",
            owner.getUniqueId(),
            null,
            ref(leader),
            ref(owner));
    giveService.notifyExpired(orphaned, owner);
    verify(owner).sendMessage(FactionVehicleReleaseMessages.giveExpired());
    verify(leader, never()).sendMessage(anyString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no_faction",
        "no_leader",
        "blank_leader",
        "no_vehicle",
        "blank_vehicle",
        "no_recipient",
        "not_registered",
        "different_installation"
      })
  void releaseRejectsIncompleteOrWrongOwnershipWithoutWriting(String state) {
    factionVehicle(false);
    var record = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    if (state.equals("not_registered")) registry.unregister("vehicle-1");
    var result =
        state.equals("different_installation")
            ? releaseService.takeFromInstallation(faction, ref(leader), "elsewhere", "vehicle-1")
            : releaseService.give(
                state.equals("no_faction") ? null : faction,
                state.equals("no_leader") ? null : state.equals("blank_leader") ? " " : "Leader",
                state.equals("no_recipient") ? null : ref(owner),
                state.equals("no_vehicle")
                    ? null
                    : state.equals("blank_vehicle") ? " " : "vehicle-1");
    assertEquals(
        state.equals("no_recipient")
            ? FactionVehicleReleaseService.Status.OWNERSHIP_UNAVAILABLE
            : FactionVehicleReleaseService.Status.NOT_FACTION_VEHICLE,
        result.status());
    assertEquals(tag("Leader"), ownerData.getOwner());
    if (state.equals("not_registered")) assertTrue(registry.getAll().isEmpty());
    else assertSame(record, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedFrameworkOwnerWritesRestoreTheFactionRecordAndReportRollbackSaveFailure(
      boolean rollbackFails) {
    factionVehicle(false);
    var record = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(true, !rollbackFails);
    framework.forced = Result.SAVE_FAILED;
    var outcome = releaseService.give(faction, "Leader", ref(owner), "vehicle-1");
    assertEquals(
        rollbackFails
            ? FactionVehicleReleaseService.Status.SAVE_FAILED
            : FactionVehicleReleaseService.Status.OWNERSHIP_UNAVAILABLE,
        outcome.status());
    assertSame(record, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Leader"), ownerData.getOwner());
    verify(fixture.ui.plugin, times(2)).saveVehicleRegistry();
    assertEquals(List.of("vehicle-1 " + tag("Owner") + " RELEASE"), framework.writes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OK", "SAVE_FAILED", "NOT_FOUND", "REFUSED_MODE", "UNAVAILABLE"})
  void releasedOwnersAreWrittenThroughVehicleFrameworksSetOwnership(String result) {
    framework.forced = Result.valueOf(result);
    if (framework.forced == Result.OK) framework.forced = null;
    UUID recipient = net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf("Recipient");
    ownerData.setWhiteList(new ArrayList<>(List.of(tag("Crew") + ";x;Crew")));

    boolean changed = FactionVehicleReleaseService.assignFrameworkOwner("vehicle-1", recipient);

    assertEquals(result.equals("OK"), changed);
    assertEquals(changed ? tag("Recipient") : tag("Owner"), ownerData.getOwner());
    assertEquals(changed, ownerData.getWhiteList().isEmpty(), "A release never keeps the old crew");
    assertEquals(List.of("vehicle-1 " + tag("Recipient") + " RELEASE"), framework.writes);
  }

  @Test
  void invalidFrameworkOwnershipIdentifiersCannotModifyEitherLiveOrStoredState() {
    UUID recipient = net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf("Recipient");
    for (String vehicleId : new String[] {null, "", " "})
      assertFalse(FactionVehicleReleaseService.assignFrameworkOwner(vehicleId, recipient));
    assertFalse(FactionVehicleReleaseService.assignFrameworkOwner("vehicle-1", null));
    assertPersonalOwnershipUnchanged();
  }

  @ParameterizedTest
  @ValueSource(strings = {"free_becomes_paid", "faction_changes", "vehicle_type_changes"})
  void pendingHandoverBindsTheFeeRecipientAndFreeStatusAsWellAsItsAmount(String state) {
    Player recipient = fixture.player("Recipient");
    enableTransferFee();
    fund(owner, 100.0);
    if (state.equals("free_becomes_paid"))
      faction.getVehicleFeeHandler().setRate(FeeKind.TRANSFER_FEE, null, 0.0);
    handovers.put(
        owner.getUniqueId(),
        new VehicleHandoverSessionManager.Session(
            "Recipient", recipient.getUniqueId(), System.currentTimeMillis() + 60000));
    handoverListener.onVehiclePreInteract(new VehiclePreInteractEvent(owner, vehicle));
    if (!state.equals("free_becomes_paid"))
      handoverListener.onVehiclePreInteract(new VehiclePreInteractEvent(owner, vehicle));
    assertTrue(RequestManager.hasRequest(recipient));
    Faction second = null;
    if (state.equals("free_becomes_paid"))
      faction.getVehicleFeeHandler().setRate(FeeKind.TRANSFER_FEE, null, 2.0);
    if (state.equals("faction_changes")) {
      second = fixture.saved("next", "NextLeader");
      faction.forceRemoveMember("Owner");
      second.addMember("Owner");
      second.getVehicleFeeHandler().applyBracket(FeeKind.TRANSFER_FEE, new Bracket(0, 10));
      second.getVehicleFeeHandler().setRate(FeeKind.TRANSFER_FEE, null, 2.0);
      net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(second.getBank(), 500.0);
      assertSame(second, FactionManager.getByMember("Owner"));
    }
    if (state.equals("vehicle_type_changes")) when(vehicle.getId()).thenReturn("simple_locomotive");
    RequestManager.accept(recipient);
    assertEquals(tag("Owner"), ownerData.getOwner());
    assertEquals(100.0, balance(owner));
    assertEquals(1000.0, faction.getBank().getWealth());
    if (second != null) assertEquals(500.0, second.getBank().getWealth());
    assertNull(feeStore.getLastOwner("vehicle-1"));
    verify(recipient).sendMessage(VehicleHandoverMessages.feeChanged());
  }

  @Test
  void anOfflineOwnerStillPaysThePreviouslyConfirmedHandoverFeeFromTheirCapturedUuid() {
    Player recipient = fixture.player("Recipient");
    enableTransferFee();
    fund(owner, 100.0);
    handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
    fixture.online.remove("Owner");
    when(owner.isOnline()).thenReturn(false);
    RequestManager.accept(recipient);
    assertEquals(tag("Recipient"), ownerData.getOwner());
    assertEquals(60.0, balance(owner));
    assertEquals(1040.0, faction.getBank().getWealth());
    assertEquals("Recipient", feeStore.getLastOwner("vehicle-1"));
    assertEquals(
        VehicleHandoverService.Status.NOT_OWNER,
        handoverService.evaluate(ref(recipient), leader.getUniqueId(), "other-vehicle").status());
  }

  @Test
  void releaseServiceItselfRejectsAnObsoleteLeaderWithoutRelyingOnTheClickListener() {
    factionVehicle(false);
    var record = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    faction.setLeader("Successor");
    assertEquals(
        FactionVehicleReleaseService.Status.NOT_LEADER,
        releaseService.take(faction, ref(leader), "vehicle-1").status());
    assertSame(record, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Leader"), ownerData.getOwner());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyRegistrationWrappersUseTheRealSaveAndOwnerUpdate(boolean pool) {
    arm(pool);
    if (pool) pools.register(faction, vehicle, owner.getUniqueId());
    else berths.register(port, vehicle, faction, owner.getUniqueId());
    var record = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    assertEquals(pool ? OwnershipMode.POOL : OwnershipMode.INSTALLATION, record.getMode());
    assertEquals("home", record.getFactionId());
    assertEquals("char_none", ownerData.getOwner(), "Name-keyed fixture leaders have no character");
    verify(fixture.ui.plugin).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedFirstRegistrationRestoresTheAbsenceOfAnOwnershipRecord(boolean pool) {
    arm(pool);
    registry.unregister("vehicle-1");
    when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(false);
    boolean result =
        pool
            ? pools.tryRegister(faction, vehicle, owner.getUniqueId())
            : berths.tryRegister(port, vehicle, faction, owner.getUniqueId());
    assertFalse(result);
    assertTrue(registry.getAll().isEmpty());
    assertEquals(tag("Owner"), ownerData.getOwner());
    assertEquals(
        FactionVehiclePoolService.CanAddResult.NOT_OWNED,
        pools.canAdd(faction, (ActiveVehicle) null));
    assertEquals(
        InstallationVehicleService.CanRegisterResult.NOT_IN_REGISTRY,
        berths.canRegister(port, (ActiveVehicle) null));
    assertNull(FactionVehiclePoolService.payingFaction(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"complete", "cancel", "partial_refund", "unaffordable", "offline_refund"})
  void registrationFeesAreConfirmedThenChargedOnceAndEitherCompletedOrRefunded(String state) {
    faction.addMember("Owner");
    faction.getVehicleFeeHandler().applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(0, 10));
    faction.getVehicleFeeHandler().setRate(FeeKind.REGISTRATION_FEE, null, 2.0);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(faction.getBank(), 1000.0);
    fund(owner, state.equals("unaffordable") ? 10.0 : 100.0);
    Blueprint blueprint = blueprint("ironclad");
    ActiveStation station = station();
    VehicleRegistrationFeeListener fees =
        new VehicleRegistrationFeeListener(feeStore, new VehicleFeeConfirmations(), feeStore::save);
    var first = new BeginVehicleConstructionEvent(owner, blueprint, station, station.getLocation());
    fees.onBeginVehicleConstruction(first);
    assertTrue(first.isCancelled());
    assertTrue(first.isKeepPlacement());
    assertEquals(1000.0, faction.getBank().getWealth());
    var confirmed =
        new BeginVehicleConstructionEvent(owner, blueprint, station, station.getLocation());
    fees.onBeginVehicleConstruction(confirmed);
    if (state.equals("unaffordable")) {
      assertTrue(confirmed.isCancelled());
      assertFalse(confirmed.isKeepPlacement());
      assertEquals(10.0, balance(owner));
      assertEquals(1000.0, faction.getBank().getWealth());
      assertNull(feeStore.takePaidBuild("world:6:64:7"));
      return;
    }
    assertFalse(confirmed.isCancelled());
    assertEquals(60.0, balance(owner));
    assertEquals(1040.0, faction.getBank().getWealth());
    VehicleFeeStore persisted = new VehicleFeeStore();
    persisted.bind(files.root.toFile());
    persisted.load();
    assertEquals(net.tfminecraft.simplefactions.identity.Members.session(owner).orElseThrow().ref(), persisted.receipt("build:world:6:64:7").payer());
    assertEquals(4000, persisted.receipt("build:world:6:64:7").cents());
    var cancelled = new VehicleConstructionCancelEvent(owner.getUniqueId(), blueprint, station);
    if (state.equals("complete")) {
      fees.onVehicleConstruct(
          new VehicleConstructEvent(
              owner.getUniqueId(), vehicle, blueprint, station.getLocation(), station));
      assertEquals("Owner", feeStore.getLastOwner("vehicle-1"));
      fees.onConstructionCancel(cancelled);
      assertEquals(60.0, balance(owner));
      assertEquals(1040.0, faction.getBank().getWealth());
    } else {
      if (state.equals("partial_refund")) net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(faction.getBank(), 10.0);
      if (state.equals("offline_refund")) {
        fixture.online.remove("Owner");
        when(owner.isOnline()).thenReturn(false);
      }
      try (var members = mockStatic(net.tfminecraft.simplefactions.identity.Members.class, CALLS_REAL_METHODS)) {
        String payer = net.tfminecraft.simplefactions.identity.Members.session(owner).orElseThrow().ref().characterId().toString();
        members.when(() -> net.tfminecraft.simplefactions.identity.Members.online(payer))
            .thenReturn(state.equals("offline_refund") ? Optional.empty() : Optional.of(owner));
        fees.onConstructionCancel(cancelled);
        fees.onConstructionCancel(cancelled);
      }
      assertEquals(
          state.equals("partial_refund") ? 70.0 : 100.0, balance(owner));
      assertEquals(state.equals("partial_refund") ? 0.0 : 1000.0, faction.getBank().getWealth());
      assertEquals(
          state.equals("partial_refund") ? -30.0 : 0.0,
          economy.getLedger(net.tfminecraft.simplefactions.identity.Members.session(owner).orElseThrow().ref().characterId()).getAmount(PlayerCashflow.VEHICLE_FEES));
      assertNull(feeStore.getLastOwner("vehicle-1"));
    }
    assertNull(feeStore.takePaidBuild("world:6:64:7"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing_player",
        "missing_blueprint",
        "exempt",
        "offline_constructor",
        "unknown_constructor",
        "missing_finished_vehicle",
        "missing_finished_uuid"
      })
  void constructionFeeCallbacksIgnoreIncompleteEventsAndRememberResolvedOwners(String state) {
    Blueprint blueprint = blueprint("ironclad");
    ActiveStation station = station();
    Runnable save = mock(Runnable.class);
    VehicleRegistrationFeeListener fees =
        new VehicleRegistrationFeeListener(feeStore, new VehicleFeeConfirmations(), save);
    if (state.equals("missing_player")
        || state.equals("missing_blueprint")
        || state.equals("exempt")) {
      var event =
          new BeginVehicleConstructionEvent(
              state.equals("missing_player") ? null : owner,
              state.equals("missing_blueprint") ? null : blueprint,
              station,
              station.getLocation());
      fees.onBeginVehicleConstruction(event);
      assertFalse(event.isCancelled());
      assertFalse(event.isKeepPlacement());
      verifyNoInteractions(save);
      return;
    }
    UUID paidBy = UUID.randomUUID();
    if (state.equals("offline_constructor") || state.equals("unknown_constructor")) {
      fixture.online.remove("Owner");
      // An offline builder is known only by the character that paid the fee.
      if (state.equals("offline_constructor")) {
        feeStore.putReceipt("build:world:6:64:7", new net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.Receipt(
            "old", new net.tfminecraft.tlibs.character.CharacterRef(owner.getUniqueId(), paidBy), "home",
            faction.getOrCreateMainGuild().getIncarnationId(), FeeKind.REGISTRATION_FEE, 500));
      }
    }
    fees.onVehicleConstruct(
        new VehicleConstructEvent(
            state.equals("missing_finished_uuid") ? null : owner.getUniqueId(),
            state.equals("missing_finished_vehicle") ? null : vehicle,
            blueprint,
            station.getLocation(),
            station));
    assertEquals(
        state.equals("offline_constructor") ? paidBy.toString() : null,
        feeStore.getLastOwner("vehicle-1"));
    verifyNoInteractions(save);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"known", "unknown", "missing_blueprint", "missing_player", "per_type_full"})
  void constructionIntegrationChecksRealVehicleLimitsBeforeAllowingTheBuild(String state) {
    VehicleIntegrationListener integration = new VehicleIntegrationListener();
    Blueprint blueprint =
        state.equals("missing_blueprint")
            ? null
            : blueprint(state.equals("unknown") ? "unknown" : "ironclad");
    // Only the full case keeps the owner's personal ironclad row.
    if (!state.equals("per_type_full")) registry.unregister("vehicle-1");
    var event =
        new BeginVehicleConstructionEvent(
            state.equals("missing_player") ? null : owner,
            blueprint,
            station(),
            new Location(fixture.ui.world, 6, 64, 7));
    integration.onBeginVehicleConstruction(event);
    boolean denied = List.of("unknown", "missing_blueprint", "per_type_full").contains(state);
    assertEquals(denied, event.isCancelled());
    if (denied) verify(owner).sendMessage(startsWith("§c"));
    if (state.equals("per_type_full")) assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Owner"), ownerData.getOwner());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"unload", "death", "remove", "missing_vehicle", "missing_uuid", "missing_record"})
  void destructionRemovesFactionAndFeeRecordsButUnloadingPreservesThem(String state) {
    factionVehicle(false);
    feeStore.setLastOwner("vehicle-1", "Owner");
    if (state.equals("missing_uuid")) when(vehicle.getUUID()).thenReturn(null);
    if (state.equals("missing_record")) {
      registry.unregister("vehicle-1");
      feeStore.forgetVehicle("vehicle-1");
    }
    VehicleRemovePayload payload =
        state.equals("death")
            ? VehicleRemovePayload.death(VehicleDeath.SINK)
            : VehicleRemovePayload.remove(
                state.equals("unload")
                    ? VehicleRemoveReason.UNLOAD
                    : VehicleRemoveReason.PLAYER_DESTROY);
    var event = new VehicleRemoveEvent(state.equals("missing_vehicle") ? null : vehicle, payload);
    new VehicleIntegrationListener().onVehicleRemove(event);
    new VehicleReclaimFeeListener(feeStore, feeStore::save).onVehicleRemove(event);
    boolean removed = List.of("death", "remove", "missing_record").contains(state);
    assertEquals(removed, registry.getByVehicleUuid("vehicle-1").isEmpty());
    assertEquals(removed ? null : "Owner", feeStore.getLastOwner("vehicle-1"));
    verify(fixture.ui.plugin, times(state.equals("death") || state.equals("remove") ? 1 : 0))
        .saveVehicleRegistry();
  }

  @Test
  void enemyInstallationWithTheSameLocalIdCannotBorrowOurBattleCommitment() {
    Faction enemy = fixture.saved("enemy", "Enemy");
    enemy
        .getInstallationHandler()
        .acceptTransferred(
            new Installation("harbor", "Enemy Harbor", InstallationKind.PORT, 43, 2, 2, 0));
    War war = new War(901, faction, enemy);
    WarManager.get().add(war);
    war.setOccupiedByAttacker(new ArrayList<>(List.of(42)));
    war.setOccupiedByDefender(new ArrayList<>());
    war.setBattleInstallationPicks(Map.of("home", new LinkedHashSet<>(List.of("harbor"))));
    PlayerVehicleRecord ours =
        new PlayerVehicleRecord(
            owner.getUniqueId(), "ours", "ironclad", OwnershipMode.INSTALLATION, "harbor", "home");
    PlayerVehicleRecord theirs =
        new PlayerVehicleRecord(
            owner.getUniqueId(),
            "theirs",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            "enemy");
    assertTrue(BattleVehicleEligibilityService.isEligible(war, "home", ours));
    assertFalse(BattleVehicleEligibilityService.isEligible(war, "home", theirs));
    assertEquals(
        new LinkedHashSet<>(List.of("harbor")), war.getBattleInstallationPicks().get("home"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "personal",
        "pool_home",
        "pool_enemy",
        "pool_no_faction",
        "uncommitted",
        "committed",
        "missing_berth",
        "prebattle",
        "no_battle",
        "manual_battle",
        "missing_war",
        "ended_war",
        "no_faction",
        "nonparticipant"
      })
  void battleEntryAndSpawnUseTheSameLiveRosterAndFactionOwnershipRules(String state) {
    Faction enemy = fixture.saved("enemy", "Enemy");
    War war = new War(902, faction, enemy);
    WarManager.get().add(war);
    war.setOccupiedByAttacker(new ArrayList<>(List.of(42)));
    war.setOccupiedByDefender(new ArrayList<>());
    if (state.equals("committed"))
      war.setBattleInstallationPicks(Map.of("home", new LinkedHashSet<>(List.of("harbor"))));
    Player actor = state.equals("no_faction") || state.equals("nonparticipant") ? owner : leader;
    if (state.equals("nonparticipant")) fixture.saved("neutral", "Neutral").addMember("Owner");
    ownerData.setOwner(tag(actor.getName()));
    Battle battle = new Battle("vehicle-entry");
    battle.setWarId(war.getId());
    battle.setStarted(!state.equals("prebattle"));
    BattleSide side = new BattleSide("attackers", LifeType.COLLECTIVE, 5);
    Warband band =
        state.equals("prebattle")
            ? Warband.createCampaignSideShell(
                "campaign-vehicle", war, war.getAttackers(), "attackers")
            : Warband.createWithMemberIds("vehicle-band", actor.getUniqueId(), true);
    band.addPlayer(actor);
    side.addBand(band);
    battle.addSide(side);
    BattleManager.get().add(battle);
    WarbandManager.addWarband(band);
    if (state.equals("no_battle")) BattleManager.get().clear();
    if (state.equals("manual_battle")) battle.setWarId(null);
    if (state.equals("missing_war")) WarManager.get().remove(war);
    if (state.equals("ended_war")) war.end(WarEndReason.WHITE_PEACE);
    if (state.startsWith("pool")) {
      when(vehicle.getId()).thenReturn("simple_locomotive");
      registry.register(
          new PlayerVehicleRecord(
              owner.getUniqueId(),
              "vehicle-1",
              "simple_locomotive",
              OwnershipMode.POOL,
              null,
              state.equals("pool_no_faction")
                  ? null
                  : state.equals("pool_enemy") ? "enemy" : "home"));
    }
    if (List.of("uncommitted", "committed", "missing_berth").contains(state))
      registry.register(
          new PlayerVehicleRecord(
              owner.getUniqueId(),
              "vehicle-1",
              "ironclad",
              OwnershipMode.INSTALLATION,
              state.equals("missing_berth") ? null : "harbor",
              "home"));
    var expected =
        switch (state) {
          case "pool_home", "committed" ->
              BattleVehicleEligibilityService.BattleVehicleEligibilityResult.ALLOWED;
          case "pool_enemy", "pool_no_faction" ->
              BattleVehicleEligibilityService.BattleVehicleEligibilityResult.DENIED_POOL_SIDE;
          case "uncommitted" ->
              BattleVehicleEligibilityService.BattleVehicleEligibilityResult.DENIED_NOT_COMMITTED;
          case "missing_berth" ->
              BattleVehicleEligibilityService.BattleVehicleEligibilityResult.DENIED_NOT_BERTHED;
          case "prebattle" ->
              BattleVehicleEligibilityService.BattleVehicleEligibilityResult
                  .DENIED_PRE_BATTLE_WARBAND;
          case "no_battle",
                  "manual_battle",
                  "missing_war",
                  "ended_war",
                  "no_faction",
                  "nonparticipant" ->
              BattleVehicleEligibilityService.BattleVehicleEligibilityResult.NOT_CAMPAIGN_BATTLE;
          default ->
              BattleVehicleEligibilityService.BattleVehicleEligibilityResult
                  .DENIED_NOT_FACTION_VEHICLE;
        };
    assertEquals(expected, BattleVehicleEligibilityService.check(actor, vehicle, registry));
    var eligibility = new BattleVehicleEligibilityService.Listener(registry);
    var clicked = new VehiclePreInteractEvent(actor, vehicle);
    eligibility.onVehiclePreInteract(clicked);
    assertEquals(expected.isDenied(), clicked.isCancelled());
    eligibility.onVehicleSpawn(new VehicleSpawnEvent(vehicle));
    verify(vehicle, times(expected.isDenied() ? 1 : 0)).remove(VehicleRemoveReason.ADMIN_KILL);
    if (expected.isDenied())
      verify(actor, times(2))
          .sendMessage(BattleVehicleEligibilityService.Messages.forResult(expected));
    else verify(actor, never()).sendMessage(anyString());
  }

  @Test
  void battleNotificationsPreferCurrentOwnerThenRegistryIdentityAndIgnoreIncompleteEvents() {
    assertSame(owner, BattleVehicleEligibilityService.resolveNotifyPlayer(vehicle, registry));
    ownerData.setOwner(tag("NotOnline"));
    assertSame(owner, BattleVehicleEligibilityService.resolveNotifyPlayer(vehicle, registry));
    when(vehicle.getOwnerData()).thenReturn(null);
    assertSame(owner, BattleVehicleEligibilityService.resolveNotifyPlayer(vehicle, registry));
    assertNull(BattleVehicleEligibilityService.resolveNotifyPlayer(null, registry));
    assertNull(BattleVehicleEligibilityService.resolveNotifyPlayer(vehicle, null));
    var eligibility = new BattleVehicleEligibilityService.Listener(registry);
    var noPlayer = new VehiclePreInteractEvent(null, vehicle);
    var noVehicle = new VehiclePreInteractEvent(owner, null);
    eligibility.onVehiclePreInteract(noPlayer);
    eligibility.onVehiclePreInteract(noVehicle);
    eligibility.onVehicleSpawn(new VehicleSpawnEvent(null));
    when(vehicle.getUUID()).thenReturn(null);
    assertNull(BattleVehicleEligibilityService.resolveNotifyPlayer(vehicle, registry));
    eligibility.onVehicleSpawn(new VehicleSpawnEvent(vehicle));
    when(vehicle.getUUID()).thenReturn("vehicle-1");
    registry.unregister("vehicle-1");
    eligibility.onVehicleSpawn(new VehicleSpawnEvent(vehicle));
    assertFalse(noPlayer.isCancelled());
    assertFalse(noVehicle.isCancelled());
    verify(vehicle, never()).remove(any(VehicleRemoveReason.class));
    assertEquals(
        BattleVehicleEligibilityService.BattleVehicleEligibilityResult.ALLOWED,
        BattleVehicleEligibilityService.check(null, vehicle, registry));
    assertEquals(
        BattleVehicleEligibilityService.BattleVehicleEligibilityResult.ALLOWED,
        BattleVehicleEligibilityService.check(owner, null, registry));
    assertEquals(
        BattleVehicleEligibilityService.BattleVehicleEligibilityResult.ALLOWED,
        BattleVehicleEligibilityService.check(owner, vehicle, null));
    Faction enemy = fixture.saved("enemy", "Enemy");
    War war = new War(903, faction, enemy);
    assertTrue(BattleVehicleEligibilityService.isEligible(null, "home", originalRecord));
    assertTrue(BattleVehicleEligibilityService.isEligible(war, " ", originalRecord));
    assertTrue(BattleVehicleEligibilityService.isEligible(war, null, "ironclad", originalRecord));
    assertFalse(
        BattleVehicleEligibilityService.isEligible(war, "home", (PlayerVehicleRecord) null));
    assertNull(BattleVehicleEligibilityService.Messages.forResult(null));
    assertNull(
        BattleVehicleEligibilityService.Messages.forResult(
            BattleVehicleEligibilityService.BattleVehicleEligibilityResult.ALLOWED));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "allowed",
        "already_owned",
        "missing_player",
        "missing_vehicle",
        "missing_uuid",
        "no_owner_data",
        "unknown",
        "per_type_limit",
        "total_limit",
        "faction_owned"
      })
  void claimListenersEnforceRealPersonalLimitsWithoutCreatingPrematureOwnership(String state) {
    // A stale personal row from another account; the claimer's own vehicles decide.
    originalRecord = new PlayerVehicleRecord(owner.getUniqueId(), "vehicle-1", "ironclad", OwnershipMode.PERSONAL,
        null, null, UUID.randomUUID());
    registry.register(originalRecord);
    var claims = new VehicleRegistryClaimService(registry);
    var listener = new VehicleRegistryClaimListener(claims);
    ownerData.setOwner(state.equals("already_owned") ? tag("Owner") : "none");
    if (state.equals("missing_uuid")) when(vehicle.getUUID()).thenReturn(null);
    if (state.equals("no_owner_data")) when(vehicle.getOwnerData()).thenReturn(null);
    if (state.equals("unknown")) when(vehicle.getId()).thenReturn("unknown");
    if (state.equals("per_type_limit"))
      storeOwned("owned", "ironclad", "Owner");
    if (state.equals("total_limit"))
      for (int i = 0; i < VehiclesConfigLoader.getPersonalSlotLimit(); i++)
        storeOwned("owned-" + i, "horse_cart", "Owner");
    if (state.equals("faction_owned"))
      registry.register(
          new PlayerVehicleRecord(
              owner.getUniqueId(),
              "vehicle-1",
              "ironclad",
              OwnershipMode.INSTALLATION,
              "harbor",
              "home"));
    var before = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    Player player = state.equals("missing_player") ? null : owner;
    ActiveVehicle active = state.equals("missing_vehicle") ? null : vehicle;
    var clicked = new VehiclePreInteractEvent(player, active);
    listener.onVehiclePreInteract(clicked);
    boolean denied =
        List.of("unknown", "per_type_limit", "total_limit", "faction_owned").contains(state);
    assertEquals(denied, clicked.isCancelled());
    assertSame(before, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(state.equals("already_owned") ? tag("Owner") : "none", ownerData.getOwner());
    if (denied) verify(owner).sendMessage(startsWith("§c"));
    if (state.equals("faction_owned")) verify(owner).sendMessage(VehicleRegistryClaimListener.FACTION_VEHICLE);
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
    assertEquals(
        InstallationVehicleService.TryRegisterResult.SKIP,
        claims.tryRegisterOnClaim(owner, (ActiveVehicle) null).status());
    assertEquals(
        VehicleSlotGuard.CanBuildResult.UNKNOWN_TYPE,
        VehicleSlotGuard.checkCanBuild((Player) null, "ironclad"));
  }

  @Test
  void vehicleSearchShowsOnlyThisInstallationsRecordsAndUsesLiveStoredThenIdNames() {
    VehicleManager manager = VehicleFramework.getVehicleManager();
    when(vehicle.getName()).thenReturn("Named ship");
    when(fixture.ui.world.getName()).thenReturn("world");
    VehicleFindMessages.sendInstallationVehicles(leader, port);
    verify(leader).sendMessage("§7No vehicles berthed at Harbor.");
    factionVehicle(false);
    when(manager.getOfflineLocation("vehicle-1"))
        .thenReturn(Optional.of(new Location(fixture.ui.world, 4, 64, 8)));
    registry.register(
        new PlayerVehicleRecord(
            owner.getUniqueId(),
            "foreign",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            "other-faction"));
    VehicleFindMessages.sendInstallationVehicles(leader, port);
    verify(leader).sendMessage("§eNamed ship §7- §fworld 4, 64, 8");
    verify(manager, never()).get("foreign");
    loaded.clear();
    when(manager.readStoredVehicle("vehicle-1"))
        .thenReturn(
            Optional.of(
                new StoredVehicleMeta("vehicle-1", "Stored name", "ironclad", tag("Leader"))));
    assertEquals("Stored name", VehicleFindMessages.resolveVehicleName("vehicle-1"));
    when(manager.readStoredVehicle("vehicle-1"))
        .thenReturn(
            Optional.of(new StoredVehicleMeta("vehicle-1", " ", "ironclad", tag("Leader"))));
    assertEquals("ironclad", VehicleFindMessages.resolveVehicleName("vehicle-1"));
    when(manager.readStoredVehicle("vehicle-1")).thenReturn(Optional.empty());
    assertEquals("vehicle-1", VehicleFindMessages.resolveVehicleName("vehicle-1"));
    assertSame(
        port, VehicleFindMessages.resolveInstallation(faction.getInstallationHandler(), "Harbor"));
    assertNull(
        VehicleFindMessages.resolveInstallation(faction.getInstallationHandler(), "Missing"));
    assertNull(VehicleFindMessages.resolveInstallation(null, "Harbor"));
    assertEquals(
        "§7location unknown (stored)",
        VehicleFindMessages.formatLocation(Optional.of(new Location(null, 1, 2, 3))));
    assertNull(FactionVehiclePoolService.payingFaction(originalRecord));
  }

  @Test
  void blueprintAliasesResolveToTheirVehicleDefinitionAndUnknownCancellationKeepsOtherPaidBuilds() {
    Blueprint blueprint = blueprint("alias");
    net.tfminecraft.vehicleframework.vehicles.Vehicle definition =
        mock(net.tfminecraft.vehicleframework.vehicles.Vehicle.class);
    when(definition.getId()).thenReturn("ironclad");
    when(blueprint.getVehicle()).thenReturn(definition);
    assertEquals("ironclad", VehicleIntegrationListener.resolveVehicleTypeId(blueprint));
    when(definition.getId()).thenReturn("");
    assertEquals("alias", VehicleIntegrationListener.resolveVehicleTypeId(blueprint));
    var payment = new VehicleFeeStore.PaidBuild(owner.getUniqueId(), "home", 40);
    feeStore.putPaidBuild("world:6:64:7", payment);
    var registration =
        new VehicleRegistrationFeeListener(feeStore, new VehicleFeeConfirmations(), feeStore::save);
    registration.onConstructionCancel(
        new VehicleConstructionCancelEvent(owner.getUniqueId(), blueprint, null));
    assertEquals(payment, feeStore.takePaidBuild("world:6:64:7"));
    assertFalse(balances.containsKey(owner.getUniqueId()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "allied_owner_pick",
        "player_pick_only",
        "legacy_ambiguous",
        "legacy_unique",
        "removed_installation",
        "deleted_faction"
      })
  void battleInstallationEligibilityUsesTheOwningAlliesCommitmentAndRejectsAmbiguousLegacyRecords(
      String state) {
    Faction enemy = fixture.saved("enemy", "Enemy");
    Faction ally = fixture.saved("ally", "Ally");
    War war = new War(904, faction, enemy);
    war.getAttackers().getMainParticipants().add(new Participant(ally));
    war.setOccupiedByAttacker(new ArrayList<>(List.of(42, 44)));
    war.setOccupiedByDefender(new ArrayList<>());
    if (List.of("allied_owner_pick", "player_pick_only", "legacy_ambiguous").contains(state))
      ally.getInstallationHandler()
          .acceptTransferred(
              new Installation("harbor", "Allied Harbor", InstallationKind.PORT, 44, 4, 4, 0));
    String pickedFaction = state.equals("allied_owner_pick") ? "ally" : "home";
    war.setBattleInstallationPicks(Map.of(pickedFaction, new LinkedHashSet<>(List.of("harbor"))));
    String recordFaction =
        state.startsWith("legacy")
            ? null
            : List.of("allied_owner_pick", "player_pick_only").contains(state) ? "ally" : "home";
    var record =
        new PlayerVehicleRecord(
            owner.getUniqueId(),
            "vehicle-1",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            recordFaction);
    if (state.equals("removed_installation")) faction.getInstallationHandler().load(List.of());
    if (state.equals("deleted_faction")) FactionManager.factions.remove(faction);
    boolean eligible = BattleVehicleEligibilityService.isEligible(war, "home", record);
    assertEquals(state.equals("allied_owner_pick") || state.equals("legacy_unique"), eligible);
    var pool =
        new PlayerVehicleRecord(
            owner.getUniqueId(),
            "pool",
            "simple_locomotive",
            OwnershipMode.POOL,
            null,
            "missing-faction");
    assertFalse(BattleVehicleEligibilityService.isEligible(war, "home", pool));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "pool",
        "installation",
        "legacy_owner",
        "already_current",
        "personal",
        "missing_record",
        "missing_uuid",
        "missing_owner_data",
        "missing_installation_id",
        "missing_faction",
        "removed_installation"
      })
  void spawningFactionVehiclesSynchronizesOnlyResolvableOwnersToTheCurrentLeader(String state) {
    if (!state.equals("personal"))
      registry.register(
          new PlayerVehicleRecord(
              owner.getUniqueId(),
              "vehicle-1",
              "ironclad",
              state.equals("pool") ? OwnershipMode.POOL : OwnershipMode.INSTALLATION,
              state.equals("missing_installation_id") ? null : "harbor",
              state.equals("missing_faction") ? "missing" : "home"));
    if (state.equals("missing_record")) registry.unregister("vehicle-1");
    if (state.equals("missing_uuid")) when(vehicle.getUUID()).thenReturn(null);
    if (state.equals("missing_owner_data")) when(vehicle.getOwnerData()).thenReturn(null);
    if (state.equals("removed_installation")) faction.getInstallationHandler().load(List.of());
    if (state.equals("legacy_owner")) ownerData.setOwner("faction_home");
    // The fixture keys members by name, so this faction's leader has no character: char_none.
    if (state.equals("already_current")) ownerData.setOwner("char_none");
    String previous = ownerData.getOwner();
    var synchronizer = new InstallationVehicleOwnerSync(registry);
    var spawn = new VehicleSpawnListener(synchronizer);
    net.tfminecraft.simplefactions.vehicles.owners.LeaderStamps.enable(true);
    spawn.onVehicleSpawn(new VehicleSpawnEvent(vehicle));
    boolean change =
        !List.of("personal", "missing_record", "missing_uuid", "missing_owner_data", "already_current")
            .contains(state);
    assertEquals(change ? "char_none" : previous, ownerData.getOwner());
    assertEquals(change ? List.of("vehicle-1 char_none RESTAMP") : state.equals("missing_owner_data")
        ? List.of("vehicle-1 char_none RESTAMP") : List.of(), framework.writes);
    spawn.onVehicleSpawn(new VehicleSpawnEvent(null));
    synchronizer.syncIfBerthed((ActiveVehicle) null);
    assertEquals(change ? "char_none" : previous, ownerData.getOwner());
  }

  @Test
  void explicitOwnerSynchronizationHandlesAbsentFactionOrOwnerDataWithoutInventingOwnership() {
    var synchronizer = new InstallationVehicleOwnerSync(registry);
    InstallationVehicleOwnerSync.stampForFaction("vehicle-1", faction);
    assertEquals("char_none", ownerData.getOwner());
    ownerData.setOwner(tag("Leader"));
    net.tfminecraft.simplefactions.vehicles.owners.LeaderStamps.enable(true);
    registry.register(
        new PlayerVehicleRecord(
            owner.getUniqueId(),
            "vehicle-1",
            "simple_locomotive",
            OwnershipMode.POOL,
            null,
            "missing"));
    synchronizer.syncIfBerthed(vehicle);
    assertEquals("char_none", ownerData.getOwner(), "A pool whose faction is gone has no leader");
    assertEquals(
        InstallationVehicleService.TryRegisterResult.SKIP,
        new VehicleRegistryClaimService(registry).tryRegisterOnClaim(null, vehicle).status());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "success",
        "not_leader",
        "not_berthed",
        "embargo",
        "battle",
        "full",
        "unknown",
        "owner_failure",
        "save_failure"
      })
  void unberthingReportsRealReleaseOutcomesAndConservesTheFactionRecordOnFailure(String state) {
    factionVehicle(false);
    var before = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    if (state.equals("not_leader")) faction.setLeader("Successor");
    if (state.equals("not_berthed")) registry.register(originalRecord);
    if (state.equals("embargo")) {
      Faction enemy = fixture.saved("enemy", "Enemy");
      War war = new War(905, faction, enemy);
      WarManager.get().add(war);
      CampaignRaidService.setRepairLockUntil(
          war, "harbor", java.time.Instant.now().plusSeconds(3600));
    }
    if (state.equals("battle")) startBattle();
    if (state.equals("full"))
      storeOwned("other", "ironclad", "Leader");
    if (state.equals("unknown")) {
      registry.register(
          new PlayerVehicleRecord(
              owner.getUniqueId(),
              "vehicle-1",
              "unknown",
              OwnershipMode.INSTALLATION,
              "harbor",
              "home"));
      before = registry.getByVehicleUuid("vehicle-1").orElseThrow();
    }
    if (state.equals("save_failure"))
      when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(false);
    var service = new InstallationVehicleUnberthService(registry);
    InstallationVehicleUnberthService.UnberthOutcome outcome;
    if (state.equals("owner_failure")) framework.forced = Result.SAVE_FAILED;
    outcome = service.unberth(faction, ref(leader), port, "vehicle-1");
    var expected =
        switch (state) {
          case "success" -> InstallationVehicleUnberthService.UnberthResult.OK;
          case "not_leader" -> InstallationVehicleUnberthService.UnberthResult.NOT_LEADER;
          case "not_berthed" -> InstallationVehicleUnberthService.UnberthResult.NOT_BERTHED;
          case "embargo" -> InstallationVehicleUnberthService.UnberthResult.EMBARGO;
          case "battle" -> InstallationVehicleUnberthService.UnberthResult.IN_BATTLE;
          case "full" -> InstallationVehicleUnberthService.UnberthResult.NO_PERSONAL_ROOM;
          case "unknown" -> InstallationVehicleUnberthService.UnberthResult.UNKNOWN_TYPE;
          case "owner_failure" ->
              InstallationVehicleUnberthService.UnberthResult.OWNERSHIP_UNAVAILABLE;
          default -> InstallationVehicleUnberthService.UnberthResult.SAVE_FAILED;
        };
    assertEquals(expected, outcome.result());
    String message = InstallationVehicleUnberthService.messageFor(outcome);
    assertTrue(message.startsWith(state.equals("success") ? "§a" : "§c"));
    if (state.equals("success")) assertEquals(OwnershipMode.PERSONAL, registry.getByVehicleUuid("vehicle-1").orElseThrow().getMode());
    else
      assertSame(
          state.equals("not_berthed") ? originalRecord : before,
          registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Leader"), ownerData.getOwner());
    assertEquals(
        "§cThat vehicle is not berthed at this installation.",
        InstallationVehicleUnberthService.messageFor(null));
  }

  @Test
  void feeCatalogUsesEnabledBuildCategoriesFiltersUnknownTypesAndReturnsIndependentIcons()
      throws Exception {
    org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
    when(Bukkit.getPluginManager()).thenReturn(plugins);
    assertTrue(VfBuildersCatalog.categories().isEmpty());
    when(plugins.isPluginEnabled("VFBuilders")).thenReturn(true);
    VehiclesConfigLoader.load(
        files
            .write(
                "catalog-vehicles.yml",
                Files.readString(Path.of("src/main/resources/vehicles.yml"))
                    .replace("fee-excluded-categories: []", "fee-excluded-categories: [staff]"))
            .toFile());
    Blueprint first = blueprint("ironclad");
    org.bukkit.inventory.ItemStack item =
        new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_INGOT, 1);
    when(first.getItem()).thenReturn(item);
    Blueprint duplicate = blueprint("IRONCLAD");
    Blueprint unknown = blueprint("missing");
    Blueprint invalid = blueprint(null);
    BlueprintCategory ships = mock(BlueprintCategory.class);
    when(ships.getId()).thenReturn("ships");
    when(ships.getBlueprints()).thenReturn(List.of(first, duplicate, unknown, invalid));
    when(ships.getItem()).thenReturn(item);
    BlueprintCategory trains = mock(BlueprintCategory.class);
    when(trains.getId()).thenReturn("trains");
    Blueprint train = blueprint("simple_locomotive");
    when(trains.getBlueprints()).thenReturn(List.of(train));
    BlueprintCategory empty = mock(BlueprintCategory.class);
    when(empty.getId()).thenReturn("empty");
    when(empty.getBlueprints()).thenReturn(List.of(unknown));
    BlueprintCategory staff = mock(BlueprintCategory.class);
    when(staff.getId()).thenReturn("staff");
    HashMap<String, BlueprintCategory> categories = new LinkedHashMap<>();
    categories.put("ships", ships);
    categories.put("staff", staff);
    categories.put("absent", null);
    categories.put("empty", empty);
    categories.put("trains", trains);
    try (MockedStatic<CategoryLoader> loader = mockStatic(CategoryLoader.class)) {
      loader.when(CategoryLoader::get).thenReturn(categories);
      var actual = VfBuildersCatalog.categories();
      assertEquals(
          List.of("ships", "trains"), actual.stream().map(VfBuildersCatalog.Category::id).toList());
      assertEquals(
          List.of("ironclad"),
          actual.get(0).vehicles().stream().map(VfBuildersCatalog.Entry::vehicleTypeId).toList());
      assertNotSame(item, actual.get(0).icon());
      assertNotSame(item, actual.get(0).vehicles().get(0).icon());
      actual.get(0).icon().setAmount(4);
      assertEquals(1, item.getAmount());
      assertNull(actual.get(1).icon());
      assertNull(actual.get(1).vehicles().get(0).icon());
      assertEquals("ships", VfBuildersCatalog.category("SHIPS").id());
      assertNull(VfBuildersCatalog.category("missing"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NaN", "Infinity"})
  void persistedRegistrationPaymentsRejectNonFiniteRefundAmounts(String amount) throws Exception {
    files.write(
        "vehicle_fees.json",
        "{\"paidBuilds\":{\"station\":{\"payerUuid\":\""
            + owner.getUniqueId()
            + "\",\"factionId\":\"home\",\"amount\":\""
            + amount
            + "\"}}}");
    assertThrows(IllegalStateException.class, feeStore::load);
    assertNull(feeStore.takePaidBuild("station"));
    assertEquals(tag("Owner"), ownerData.getOwner());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"unbound", "absent", "null_document", "malformed", "directory", "empty_document"})
  void feeStoreLoadsPreservePriorStateOnReadFailureAndReplaceItForAValidEmptyDocument(String state)
      throws Exception {
    VehicleFeeStore store = state.equals("unbound") ? new VehicleFeeStore() : feeStore;
    var payment = new VehicleFeeStore.PaidBuild(owner.getUniqueId(), "home", 40);
    store.setLastOwner("vehicle-1", "Owner");
    store.putPaidBuild("station", payment);
    if (state.equals("null_document")) files.write("vehicle_fees.json", "null");
    if (state.equals("malformed")) files.write("vehicle_fees.json", "{broken");
    if (state.equals("absent") || state.equals("directory")) Files.delete(files.root.resolve("vehicle_fees.json"));
    if (state.equals("directory")) Files.createDirectory(files.root.resolve("vehicle_fees.json"));
    if (state.equals("empty_document")) files.write("vehicle_fees.json", "{}");
    if (List.of("null_document", "malformed", "directory").contains(state)) assertThrows(IllegalStateException.class, store::load);
    else store.load();
    if (state.equals("unbound")) store.save();
    assertEquals(state.equals("empty_document") ? null : "Owner", store.getLastOwner("vehicle-1"));
    assertEquals(state.equals("empty_document") ? null : payment, store.takePaidBuild("station"));
  }

  @Test
  void feeStoreRejectsMalformedPaymentRecordsAndPersistsRepairedRoundTrips() throws Exception {
    files.write(
        "vehicle_fees.json",
        """
        {"lastOwners":{"vehicle-1":"Owner"},"paidBuilds":{
          "null":null,
          "missingPayer":{"factionId":"home","amount":40},
          "invalidPayer":{"payerUuid":"invalid","amount":40},
          "zero":{"payerUuid":"%s","amount":0},
          "negative":{"payerUuid":"%s","amount":-1},
          "valid":{"payerUuid":"%s","factionId":"home","amount":40}}}
        """
            .formatted(owner.getUniqueId(), owner.getUniqueId(), owner.getUniqueId()));
    assertThrows(IllegalStateException.class, feeStore::load);
    var repaired = new com.google.gson.JsonParser().parse(Files.readString(files.root.resolve("vehicle_fees.json"))).getAsJsonObject();
    for (String key : List.of("null", "missingPayer", "invalidPayer", "zero", "negative")) repaired.getAsJsonObject("paidBuilds").remove(key);
    files.write("vehicle_fees.json", repaired.toString());
    feeStore.load(); feeStore.save();
    VehicleFeeStore loaded = new VehicleFeeStore();
    loaded.bind(files.root.toFile());
    loaded.load();
    assertEquals(
        new VehicleFeeStore.PaidBuild(owner.getUniqueId(), "home", 40),
        loaded.takePaidBuild("valid"));
    assertNull(loaded.takePaidBuild("valid"));
    assertEquals("Owner", loaded.getLastOwner("vehicle-1"));
    loaded.setLastOwner(null, "Other");
    loaded.setLastOwner("vehicle-1", null);
    loaded.setLastOwner("vehicle-1", " ");
    loaded.putPaidBuild(null, new VehicleFeeStore.PaidBuild(owner.getUniqueId(), "home", 1));
    loaded.putPaidBuild("invalid", null);
    loaded.forgetVehicle(null);
    assertNull(loaded.getLastOwner(null));
    assertNull(loaded.takePaidBuild(null));
    assertEquals("Owner", loaded.getLastOwner("vehicle-1"));
    loaded.forgetVehicle("vehicle-1");
    loaded.save();
    feeStore.load();
    assertNull(feeStore.getLastOwner("vehicle-1"));
    assertNull(feeStore.takePaidBuild("valid"));
  }

  @Test
  void feeStoreRefusesNonAtomicRenameWithoutReplacingThePreviousFile() throws Exception {
    feeStore.setLastOwner("vehicle-1", "Owner");
    feeStore.save();
    feeStore.setLastOwner("vehicle-1", "Recipient");
    java.util.concurrent.atomic.AtomicInteger atomicAttempts =
        new java.util.concurrent.atomic.AtomicInteger();
    try (MockedStatic<Files> filesystem =
        mockStatic(
            Files.class,
            call -> {
              if (call.getMethod().getName().equals("move")) {
                for (Object option : call.getArguments()) {
                  if (option == java.nio.file.StandardCopyOption.ATOMIC_MOVE) {
                    atomicAttempts.incrementAndGet();
                    throw new java.nio.file.AtomicMoveNotSupportedException(
                        "temporary", "destination", "unsupported filesystem");
                  }
                }
              }
              return call.callRealMethod();
            })) {
      assertThrows(java.io.UncheckedIOException.class, feeStore::save);
    }
    assertEquals(1, atomicAttempts.get());
    assertFalse(Files.exists(files.root.resolve("vehicle_fees.json.tmp")));
    VehicleFeeStore persisted = new VehicleFeeStore();
    persisted.bind(files.root.toFile());
    persisted.load();
    assertEquals("Owner", persisted.getLastOwner("vehicle-1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing_parent", "blocked_target"})
  void feeStoreWriteFailuresLeaveTheExistingDestinationAndInMemoryOwnershipIntact(String state)
      throws Exception {
    if (state.equals("missing_parent")) feeStore.bind(files.root.resolve("absent").toFile());
    else {
      Files.delete(files.root.resolve("vehicle_fees.json"));
      Files.createDirectory(files.root.resolve("vehicle_fees.json"));
      Files.writeString(files.root.resolve("vehicle_fees.json/sentinel"), "preserved");
    }
    feeStore.setLastOwner("vehicle-1", "Owner");
    if (state.equals("blocked_target")) assertThrows(java.io.UncheckedIOException.class, feeStore::save);
    else feeStore.save();
    assertEquals("Owner", feeStore.getLastOwner("vehicle-1"));
    if (state.equals("missing_parent")) assertTrue(Files.exists(files.root.resolve("absent/vehicle_fees.json")));
    else
      assertEquals("preserved", Files.readString(files.root.resolve("vehicle_fees.json/sentinel")));
  }

  @Test
  void unfinishedBuildAndClaimReceiptsRefuseNewPhysicalActions() {
    var payment = net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.current();
    var ref = net.tfminecraft.simplefactions.identity.Members.session(owner).orElseThrow().ref();
    feeStore.putReceipt("build:world:6:64:7", new net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.Receipt(
        "interrupted", ref, faction.getId(), faction.getOrCreateMainGuild().getIncarnationId(), FeeKind.REGISTRATION_FEE, 1));
    var build = new BeginVehicleConstructionEvent(owner, blueprint("ironclad"), station(), station().getLocation());
    new VehicleRegistrationFeeListener(feeStore, new VehicleFeeConfirmations(), feeStore::save).onBeginVehicleConstruction(build);
    assertTrue(build.isCancelled());
    feeStore.putReceipt("claim:vehicle-1", payment.receipt("build:world:6:64:7"));
    ownerData.setOwner("none");
    registry.unregister("vehicle-1");
    var claim = new net.tfminecraft.vehicleframework.events.VehicleOwnershipChangeEvent(owner, vehicle,
        net.tfminecraft.vehicleframework.api.OwnershipReason.CLAIM, UUID.randomUUID(), ref(owner), false, ref(owner),
        null, "none", tag("Owner"));
    new net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnershipGate(registry,
        new VehicleRegistryClaimService(registry), feeStore, new VehicleFeeConfirmations(), feeStore::save,
        java.util.logging.Logger.getAnonymousLogger()).onChange(claim);
    assertTrue(claim.isCancelled());
    verify(owner).sendMessage(net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnershipGate.PAYMENT_UNAVAILABLE);
    ownerData.setOwner(tag("Owner"));
    assertEquals(tag("Owner"), ownerData.getOwner());
  }

  @Test
  void handoverWithoutAFeeStillPersistsTheCapturedRecipient() {
    Player recipient = fixture.player("Recipient");
    handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
    handoverService.acceptRequest(recipient);
    assertEquals(tag("Recipient"), ownerData.getOwner());
    assertEquals("Recipient", feeStore.getLastOwner("vehicle-1"));
    assertEquals(net.tfminecraft.simplefactions.identity.Members.session(recipient).orElseThrow().ref().characterId(),
        registry.getByVehicleUuid("vehicle-1").orElseThrow().getPlayerUuid());
  }

  @Test
  void changedCharacterAndUnavailablePaymentsRefusePendingHandovers() {
    Player recipient = fixture.player("Recipient");
    handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
    try (var members = mockStatic(net.tfminecraft.simplefactions.identity.Members.class, CALLS_REAL_METHODS)) {
      members.when(() -> net.tfminecraft.simplefactions.identity.Members.session(recipient)).thenReturn(Optional.empty());
      handoverService.acceptRequest(recipient);
      verify(recipient).sendMessage("§cThis request belongs to another character.");
      handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
      verify(owner).sendMessage("§cBoth players must select a living character.");
    }
    net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.stop();
    handoverService.acceptRequest(recipient);
    verify(recipient).sendMessage("§cVehicle payment needs staff review or recovery.");
    assertEquals(tag("Owner"), ownerData.getOwner());
  }

  @Test
  void publicVehicleQueriesReportFactionUpkeepAndTheAbsenceOfMilitaryOrPluginState() {
    assertEquals(0, FactionVehiclePoolService.artillerySlots(null));
    assertEquals(0, FactionVehiclePoolService.artillerySlots(faction));
    assertEquals(0.0, FactionVehiclePoolService.dailyUpkeep(null, "home"));
    assertEquals(0.0, FactionVehiclePoolService.dailyUpkeep(registry, null));
    assertEquals(0.0, FactionVehiclePoolService.dailyUpkeep(registry, " "));
    assertEquals(0.0, FactionVehiclePoolService.dailyUpkeepOf("home"));
    factionVehicle(true);
    assertSame(faction, FactionVehiclePoolService.payingFaction(registry.getByVehicleUuid("vehicle-1").orElseThrow()));
    assertEquals(
        VehiclesConfigLoader.getUpkeep("simple_locomotive"),
        FactionVehiclePoolService.dailyUpkeepOf("home"));
    SimpleFactions previous = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      assertEquals(0.0, FactionVehiclePoolService.dailyUpkeepOf("home"));
    } finally {
      SimpleFactions.plugin = previous;
    }
    assertEquals(tag("Leader"), ownerData.getOwner());
  }

  @Test
  void publicVehicleMessagesRenderAbsentOptionalDetailsAndDoNotReportSuccessfulHandoversAsErrors() {
    assertEquals("§cUsage: §e/faction findvehicles <installation id>", VehicleFindMessages.usage());
    assertEquals("§cUnknown installation id.", VehicleFindMessages.unknownInstallation());
    assertEquals(
        "§cYou need to be a faction leader to transfer vehicles.", VehicleFindMessages.notLeader());
    assertNull(VehicleHandoverMessages.forOutcome(null, "Leader"));
    var permitted = handoverService.evaluate(ref(owner), leader.getUniqueId(), "vehicle-1");
    assertEquals(VehicleHandoverService.Status.OK, permitted.status());
    assertNull(VehicleHandoverMessages.forOutcome(permitted, "Leader"));
    var unknown =
        new InstallationVehicleUnberthService.UnberthOutcome(
            InstallationVehicleUnberthService.UnberthResult.UNKNOWN_TYPE, null, "missing");
    var noRoom =
        new InstallationVehicleUnberthService.UnberthOutcome(
            InstallationVehicleUnberthService.UnberthResult.NO_PERSONAL_ROOM, null, "ironclad");
    assertEquals(
        "§cThis vehicle type is not registered for faction upkeep.",
        InstallationVehicleUnberthService.messageFor(unknown));
    assertEquals(
        "§cYou have reached your personal vehicle limit (3).",
        InstallationVehicleUnberthService.messageFor(noRoom));
    assertPersonalOwnershipUnchanged();
  }

  private Blueprint blueprint(String id) {
    Blueprint blueprint = mock(Blueprint.class);
    when(blueprint.getId()).thenReturn(id);
    return blueprint;
  }

  private ActiveStation station() {
    when(fixture.ui.world.getName()).thenReturn("world");
    ActiveStation station = mock(ActiveStation.class);
    when(station.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, 6, 64, 7));
    return station;
  }

  private void factionVehicle(boolean pool) {
    arm(pool);
    sessions.clear(leader.getUniqueId());
    ownerData.setOwner(tag("Leader"));
    registry.register(
        new PlayerVehicleRecord(
            owner.getUniqueId(),
            "vehicle-1",
            vehicle.getId(),
            pool ? OwnershipMode.POOL : OwnershipMode.INSTALLATION,
            pool ? null : "harbor",
            "home"));
  }

  private void armGive(Player recipient) {
    releases.put(
        leader.getUniqueId(),
        new VehicleReleaseSessionManager.VehicleReleaseSession(
            VehicleReleaseSessionManager.Kind.GIVE,
            recipient.getName(),
            recipient.getUniqueId(),
            System.currentTimeMillis() + 60000));
  }

  private void startBattle() {
    Faction enemy = fixture.saved("enemy", "Enemy");
    War war = new War(faction, enemy);
    WarManager.get().add(war);
    Battle battle = new Battle("vehicle-battle");
    battle.setWarId(war.getId());
    battle.setStarted(true);
    BattleManager.get().add(battle);
  }

  private void enableTransferFee() {
    faction.addMember("Owner");
    faction.getVehicleFeeHandler().applyBracket(FeeKind.TRANSFER_FEE, new Bracket(0, 10));
    faction.getVehicleFeeHandler().setRate(FeeKind.TRANSFER_FEE, null, 2.0);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(faction.getBank(), 1000.0);
  }

  private static net.tfminecraft.tlibs.character.CharacterRef ref(Player player) {
    return net.tfminecraft.simplefactions.identity.Members.session(player).orElseThrow().ref();
  }

  /** The owner value of the character a fixture player of that name plays. */
  private static String tag(String name) {
    return "char_" + net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf(name);
  }

  /** An unloaded personal vehicle of the named player's character, with its payer row. */
  private void storeOwned(String id, String type, String ownerName) {
    stored.put(id, new OwnerFields(tag(ownerName), List.of(), false, false, null));
    storedTypes.put(id, type);
    Player player = fixture.online.get(ownerName);
    UUID account = player != null ? player.getUniqueId() : UUID.nameUUIDFromBytes(ownerName.getBytes());
    registry.register(new PlayerVehicleRecord(
        net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf(ownerName), id, type,
        OwnershipMode.PERSONAL, null, null, account));
  }

  /** VehicleFramework's setOwnership over the loaded mocks and the stored map. */
  private final class Framework implements VehicleOwnerApi {
    final List<String> writes = new ArrayList<>();
    Result forced;

    @Override
    public boolean characters() {
      return true;
    }

    @Override
    public List<OwnerMeta> list() {
      List<OwnerMeta> all = new ArrayList<>();
      for (String id : loaded.keySet()) read(id).ifPresent(all::add);
      for (String id : stored.keySet()) if (!loaded.containsKey(id)) read(id).ifPresent(all::add);
      return all;
    }

    @Override
    public Optional<OwnerMeta> read(String id) {
      ActiveVehicle live = loaded.get(id);
      if (live != null && live.getOwnerData() != null) {
        OwnerData data = live.getOwnerData();
        OwnerFields fields = new OwnerFields(data.getOwner(), data.getWhiteList(), data.isWhiteListed(),
            data.isTicketsEnabled(), data.getTicketId());
        return Optional.of(new OwnerMeta(id, live.getId(), fields.owner(), Optional.of(fields), Optional.of(fields)));
      }
      OwnerFields fields = stored.get(id);
      return fields == null ? Optional.empty()
          : Optional.of(new OwnerMeta(id, storedTypes.get(id), fields.owner(), Optional.of(fields), Optional.empty()));
    }

    @Override
    public Result write(String id, String owner, List<String> whitelist, Reason reason) {
      writes.add(id + " " + owner + " " + reason);
      if (forced != null) return forced;
      ActiveVehicle live = loaded.get(id);
      if (live != null && live.getOwnerData() != null) {
        live.getOwnerData().setOwner(owner);
        if (reason != Reason.RESTAMP && reason != Reason.MIGRATE) live.getOwnerData().setWhiteList(new ArrayList<>());
        return Result.OK;
      }
      if (stored.containsKey(id)) {
        stored.put(id, stored.get(id).withOwnership(owner, List.of()));
        return Result.OK;
      }
      return Result.NOT_FOUND;
    }
  }

  private void fillPort() {
    for (int i = 0; i < 8; i++)
      registry.register(
          new PlayerVehicleRecord(
              owner.getUniqueId(),
              "berthed-" + i,
              "ironclad",
              OwnershipMode.INSTALLATION,
              "harbor",
              "home"));
  }

  private void arm(boolean pool) {
    String type = pool ? "simple_locomotive" : "ironclad";
    when(vehicle.getId()).thenReturn(type);
    originalRecord =
        new PlayerVehicleRecord(
            owner.getUniqueId(), "vehicle-1", type, OwnershipMode.PERSONAL, null, null,
            owner.getUniqueId());
    registry.register(originalRecord);
    sessions.put(
        leader.getUniqueId(),
        new VehicleTransferSession(
            pool ? null : "harbor", System.currentTimeMillis() + 60000, pool));
  }

  private VehiclePreInteractEvent click() {
    var event = new VehiclePreInteractEvent(leader, vehicle);
    listener.onVehiclePreInteract(event);
    return event;
  }

  private void assertPersonalOwnershipUnchanged() {
    assertSame(originalRecord, registry.getByVehicleUuid("vehicle-1").orElseThrow());
    assertEquals(tag("Owner"), ownerData.getOwner());
    assertEquals(1, registry.getAll().size());
  }

  private Field snapshot(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    Object value = field.get(null);
    globals.put(
        field,
        Modifier.isFinal(field.getModifiers()) && value instanceof Map<?, ?> map
            ? new LinkedHashMap<>(map)
            : value);
    return field;
  }

  @Test
  void aHandoverWhosePayerRowCannotBeWrittenNeverChangesTheOwner() throws Exception {
    Player recipient = fixture.player("Recipient");
    handoverService.offer(owner, recipient, "vehicle-1", "ironclad");
    Files.writeString(files.root.resolve("Cache"), "a file where the registry folder should be");

    RequestManager.accept(recipient);

    assertEquals(tag("Owner"), ownerData.getOwner());
    assertEquals(List.of(), framework.writes);
    verify(recipient).sendMessage(VehicleHandoverMessages.ownershipUnavailable());
    verify(owner).sendMessage(VehicleHandoverMessages.ownershipUnavailable());
  }

  @Test
  void consentNeedsBothCharactersWhenOfferedAndTheSameOwnerCharacterWhenAccepted() {
    arm(false);
    try (var members =
        mockStatic(net.tfminecraft.simplefactions.identity.Members.class, CALLS_REAL_METHODS)) {
      members
          .when(() -> net.tfminecraft.simplefactions.identity.Members.session(owner))
          .thenReturn(Optional.empty());
      assertFalse(consent.trySendConsentRequest(leader, owner, faction, port, vehicle));
      verify(leader).sendMessage(VehicleTransferMessages.ownerOffline());
    }
    assertTrue(click().isCancelled());
    assertInstanceOf(VehicleTransferConsentRequest.class, RequestManager.getRequest(owner));
    var otherCharacter =
        Optional.of(
            new net.tfminecraft.tlibs.character.CharacterSession(
                new net.tfminecraft.tlibs.character.CharacterRef(owner.getUniqueId(), UUID.randomUUID()),
                2L));
    try (var members =
        mockStatic(net.tfminecraft.simplefactions.identity.Members.class, CALLS_REAL_METHODS)) {
      members
          .when(() -> net.tfminecraft.simplefactions.identity.Members.session(owner))
          .thenReturn(otherCharacter);
      consent.acceptRequest(owner);
    }
    verify(owner).sendMessage("§cThis request belongs to another character.");
    assertPersonalOwnershipUnchanged();
    assertEquals(List.of(), framework.writes);
  }
}
