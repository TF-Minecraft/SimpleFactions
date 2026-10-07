package net.tfminecraft.simplefactions.installation.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.InstallationConstructionData;
import net.tfminecraft.simplefactions.database.InstallationData;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationConstruction;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.InstallationKindConfig;
import net.tfminecraft.simplefactions.installation.InstallationSpawnService;
import net.tfminecraft.simplefactions.installation.InstallationTransferService;
import net.tfminecraft.simplefactions.installation.WartimeInstallationService;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Installation lifecycle with real factions, handlers, banks, and registry state. */
class InstallationLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Faction home;
  private PlayerVehicleRegistry registry;
  private Map<InstallationKind, InstallationKindConfig> configurations;
  private Map<InstallationKind, InstallationKindConfig> previousConfigurations;
  private final Map<Field, Object> vehicleConfigurations = new LinkedHashMap<>();
  private int previousProximity;
  private int previousTimeout;
  private MockedStatic<VehicleFramework> vehicleFramework;
  private VehicleManager vehicles;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    Field config = InstallationConfigLoader.class.getDeclaredField("byKind");
    config.setAccessible(true);
    configurations = (Map<InstallationKind, InstallationKindConfig>) config.get(null);
    previousConfigurations = new EnumMap<>(InstallationKind.class);
    previousConfigurations.putAll(configurations);
    previousProximity = InstallationConfigLoader.getConsentProximityBlocks();
    previousTimeout = InstallationConfigLoader.getTransferRequestTimeoutSeconds();
    for (String name :
        new String[] {
          "personalSlotLimit",
          "defaultPerPerson",
          "maintenanceHourlyDamagePercent",
          "maintenanceMinHealthPercent",
          "maintenanceIntervalTicks",
          "categoryIds",
          "typesByCategory",
          "categoryByVehicleTypeId",
          "categoryDisplayNames",
          "feeExcludedCategories"
        }) {
      Field field = VehiclesConfigLoader.class.getDeclaredField(name);
      field.setAccessible(true);
      vehicleConfigurations.put(field, field.get(null));
    }
    VehiclesConfigLoader.load(Path.of("src/main/resources/vehicles.yml").toFile());
    InstallationConfigLoader.load(Path.of("src/main/resources/installations.yml").toFile());
    home = faction("home", "Leader", 42, 44);
    fixture.player("Leader");
    registry = new PlayerVehicleRegistry();
    Field wiring = SimpleFactions.class.getDeclaredField("vehicleRegistry");
    wiring.setAccessible(true);
    wiring.set(fixture.ui.plugin, registry);
    when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(true);
    vehicles = mock(VehicleManager.class);
    vehicleFramework = mockStatic(VehicleFramework.class);
    vehicleFramework.when(VehicleFramework::getVehicleManager).thenReturn(vehicles);
  }

  @AfterEach
  void close() throws Exception {
    try {
      vehicleFramework.close();
      configurations.clear();
      configurations.putAll(previousConfigurations);
      for (Map.Entry<Field, Object> entry : vehicleConfigurations.entrySet())
        entry.getKey().set(null, entry.getValue());
      restoreConfig("consentProximityBlocks", previousProximity);
      restoreConfig("transferRequestTimeoutSeconds", previousTimeout);
    } finally {
      fixture.close();
    }
  }

  @Test
  void transferPreservesBothSameNamedInstallationsAndTheirOwnVehicles() {
    Faction destination = faction("destination", "Recipient", 43);
    Installation incoming = install(home, "harbor", InstallationKind.PORT, 42, 2, 3, 101L, 1);
    Installation existing =
        install(destination, "harbor", InstallationKind.PORT, 43, 10, 4, 202L, 1);
    UUID captain = UUID.randomUUID();
    registry.register(
        new PlayerVehicleRecord(
            captain, "incoming", "ironclad", OwnershipMode.INSTALLATION, "harbor", home.getId()));
    registry.register(
        new PlayerVehicleRecord(
            captain,
            "existing",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            destination.getId()));
    OwnerData incomingOwner = new OwnerData();
    incomingOwner.setOwner("player_Leader");
    ActiveVehicle incomingVehicle = mock(ActiveVehicle.class);
    when(incomingVehicle.getOwnerData()).thenReturn(incomingOwner);
    when(vehicles.get("incoming")).thenReturn(incomingVehicle);

    InstallationTransferService.transfer(home, destination, 42);

    assertTrue(home.getInstallationHandler().getAll().isEmpty());
    assertEquals(2, destination.getInstallationHandler().getAll().size());
    assertSame(existing, destination.getInstallationHandler().getById("harbor"));
    assertSame(
        existing, destination.getInstallationHandler().getByProvince(InstallationKind.PORT, 43));
    Installation transferred =
        destination.getInstallationHandler().getByProvince(InstallationKind.PORT, 42);
    assertNotNull(transferred);
    assertNotEquals(existing.getId(), transferred.getId());
    assertSame(transferred, destination.getInstallationHandler().getById(transferred.getId()));
    assertEquals(incoming.getName(), transferred.getName());
    assertEquals(101L, transferred.getCompletedAt());
    assertEquals(2, transferred.getCenterX());
    assertEquals(3, transferred.getCenterZ());
    assertEquals(1, transferred.getLevel());
    assertEquals(
        transferred.getId(),
        registry.getByVehicleUuid("incoming").orElseThrow().getInstallationId());
    assertEquals("destination", registry.getByVehicleUuid("incoming").orElseThrow().getFactionId());
    assertEquals("harbor", registry.getByVehicleUuid("existing").orElseThrow().getInstallationId());
    assertEquals("player_Recipient", incomingOwner.getOwner());
  }

  @Test
  void siegeOccupationUsesTheScheduledProvinceWhenAnotherFactionHasTheSameFortId() {
    Installation unrelated = install(home, "fort", InstallationKind.FORT, 42, 0, 0, 1, 1);
    Faction defender = faction("defender", "Defender", 43);
    Installation target = install(defender, "fort", InstallationKind.FORT, 43, 20, 0, 2, 1);
    Faction attacker = faction("attacker", "Attacker", 45);
    War war = new War(2, attacker, defender);
    ScheduledCampaignBattle slot =
        new ScheduledCampaignBattle(43, CampaignBattleKind.SIEGE, false, "fort");

    WartimeInstallationService.occupySiegeFort(war, BelligerentRole.ATTACKER, slot);

    assertSame(unrelated, home.getInstallationHandler().getById("fort"));
    assertSame(target, attacker.getInstallationHandler().getByProvince(InstallationKind.FORT, 43));
    assertTrue(defender.getInstallationHandler().getAll().isEmpty());
  }

  @Test
  void wartimeCollisionAndRevertPreserveEachOriginalOwnersInstallation() {
    Faction attacker = faction("attacker", "Attacker", 43);
    Installation defending = install(home, "harbor", InstallationKind.PORT, 42, 2, 3, 101, 1);
    Installation originalAttacking =
        install(attacker, "harbor", InstallationKind.PORT, 43, 4, 5, 202, 1);
    War war = new War(3, attacker, home);
    UUID captain = UUID.randomUUID();
    registry.register(
        new PlayerVehicleRecord(
            captain,
            "defending-ship",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            home.getId()));
    registry.register(
        new PlayerVehicleRecord(
            captain,
            "attacking-ship",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            attacker.getId()));

    WartimeInstallationService.occupy(war, attacker, 42);
    assertEquals(2, attacker.getInstallationHandler().getAll().size());
    assertSame(
        originalAttacking,
        attacker.getInstallationHandler().getByProvince(InstallationKind.PORT, 43));
    Installation occupied =
        attacker.getInstallationHandler().getByProvince(InstallationKind.PORT, 42);
    assertEquals(
        occupied.getId(),
        registry.getByVehicleUuid("defending-ship").orElseThrow().getInstallationId());
    assertEquals(
        attacker.getId(), registry.getByVehicleUuid("defending-ship").orElseThrow().getFactionId());
    WartimeInstallationService.revert(war);

    assertEquals(1, home.getInstallationHandler().getAll().size());
    Installation restored = home.getInstallationHandler().getByProvince(InstallationKind.PORT, 42);
    assertNotNull(restored);
    assertEquals(defending.getName(), restored.getName());
    assertEquals(101L, restored.getCompletedAt());
    assertEquals(
        restored.getId(),
        registry.getByVehicleUuid("defending-ship").orElseThrow().getInstallationId());
    assertEquals(
        home.getId(), registry.getByVehicleUuid("defending-ship").orElseThrow().getFactionId());
    assertEquals(
        "harbor", registry.getByVehicleUuid("attacking-ship").orElseThrow().getInstallationId());
    assertEquals(
        attacker.getId(), registry.getByVehicleUuid("attacking-ship").orElseThrow().getFactionId());
    assertSame(originalAttacking, attacker.getInstallationHandler().getById("harbor"));
    assertEquals(1, attacker.getInstallationHandler().getAll().size());
    assertTrue(war.getWartimeInstallationOwners().isEmpty());
  }

  @Test
  void loadReplacesOldIndexesSkipsInvalidRecordsAndRoundTripsAllFields() {
    install(home, "obsolete", InstallationKind.FORT, 42, 0, 0, 1, 1);
    InstallationData valid =
        new Installation("station", "Central", InstallationKind.TRAIN_STATION, 44, 8, 9, 123L, 2)
            .toData();
    InstallationData unknown = new InstallationData();
    unknown.id = "bad";
    unknown.name = "Bad";
    unknown.kind = "not_a_kind";
    unknown.province = 42;
    home.getInstallationHandler().load(List.of(new InstallationData(), unknown, valid));
    Installation restored = home.getInstallationHandler().getById("station");
    assertNotNull(restored);
    assertNull(home.getInstallationHandler().getById("obsolete"));
    assertNull(home.getInstallationHandler().getByProvince(InstallationKind.FORT, 42));
    assertSame(
        restored, home.getInstallationHandler().getByProvince(InstallationKind.TRAIN_STATION, 44));
    InstallationData saved = home.getInstallationHandler().serialize().getFirst();
    assertEquals("Central", saved.name);
    assertEquals(2, saved.level);
    assertEquals(123L, saved.completedAt);
    assertEquals(8, saved.centerX);
    assertEquals(9, saved.centerZ);
    assertEquals(44, saved.province);
    home.getInstallationHandler().load(null);
    assertTrue(home.getInstallationHandler().getAll().isEmpty());
    assertNull(home.getInstallationHandler().getById(null));
    assertTrue(home.getInstallationHandler().serialize().isEmpty());
  }

  @Test
  void queuedConstructionPersistsTicksOnceAndNotifiesItsLeaderOnCompletion() {
    shortConstruction(InstallationKind.FORT, 2);
    long started = System.currentTimeMillis();
    ConstructResult result =
        home.getInstallationHandler().construct(InstallationKind.FORT, "Green Fort", 42, 2, 3);
    assertTrue(result.isSuccess());
    InstallationConstruction pending = home.getInstallationHandler().getPendingConstruction();
    assertEquals("Green_Fort", pending.getId());
    assertFalse(pending.isUpgrade());
    assertTrue(pending.getStartedAt() >= started);
    InstallationConstructionData saved = home.getInstallationHandler().serializeConstruction();
    assertEquals(2, saved.timeLeft);
    home.getInstallationHandler().loadConstruction(saved);
    home.getInstallationHandler().tick();
    assertNull(home.getInstallationHandler().getById("Green_Fort"));
    assertEquals(1, home.getInstallationHandler().getPendingConstruction().getTimeLeft());
    home.getInstallationHandler().tick();
    Installation completed = home.getInstallationHandler().getById("Green_Fort");
    assertNotNull(completed);
    assertEquals(2, completed.getCenterX());
    assertEquals(3, completed.getCenterZ());
    assertNull(home.getInstallationHandler().serializeConstruction());
    home.getInstallationHandler().tick();
    assertEquals(1, home.getInstallationHandler().getAll().size());
    verify(fixture.online.get("Leader"), times(1))
        .sendMessage(argThat((String message) -> message.contains("finished construction")));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -3})
  void persistedCompletedConstructionIsFinishedImmediatelyEvenWithOfflineLeader(int timeLeft) {
    fixture.online.remove("Leader");
    InstallationConstructionData data = construction("ready", 42, timeLeft, false);
    home.getInstallationHandler().loadConstruction(data);
    assertNotNull(home.getInstallationHandler().getById("ready"));
    assertNull(home.getInstallationHandler().getPendingConstruction());
    assertNull(home.getInstallationHandler().serializeConstruction());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "unknown", "clear"})
  void invalidOrAbsentPersistedConstructionClearsThePreviousQueue(String kind) {
    home.getInstallationHandler().loadConstruction(construction("pending", 42, 10, false));
    InstallationConstructionData invalid = construction("bad", 44, 20, false);
    if (kind.equals("missing")) invalid.id = null;
    if (kind.equals("unknown")) invalid.kind = "unknown";
    home.getInstallationHandler().loadConstruction(kind.equals("clear") ? null : invalid);
    assertNull(home.getInstallationHandler().getPendingConstruction());
    assertTrue(home.getInstallationHandler().getAll().isEmpty());
    assertTrue(
        home.getInstallationHandler()
            .constructInstant(InstallationKind.FORT, "replacement", 42, 0, 0)
            .isSuccess());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void completedUpgradeIncrementsOnlyAnExistingInstallationAndClearsTheQueue(boolean existing) {
    if (existing) install(home, "station", InstallationKind.TRAIN_STATION, 42, 0, 0, 3, 1);
    InstallationConstructionData data = construction("station", 42, 0, true);
    data.kind = "train_station";
    home.getInstallationHandler().loadConstruction(data);
    assertNull(home.getInstallationHandler().getPendingConstruction());
    if (existing) {
      assertEquals(2, home.getInstallationHandler().getById("station").getLevel());
      verify(fixture.online.get("Leader"))
          .sendMessage(
              argThat((String message) -> message.contains("finished upgrading to level 2")));
    } else assertTrue(home.getInstallationHandler().getAll().isEmpty());
  }

  @Test
  void upkeepPaysCheapestFirstAndPreservesUnspentMoneyWithoutChargingDestroyedBuildings() {
    Installation cheap = install(home, "station", InstallationKind.TRAIN_STATION, 42, 0, 0, 5, 1);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 3, 1);
    install(home, "fort", InstallationKind.FORT, 42, 0, 0, 1, 1);
    home.getBank().setWealth(45.0);
    home.getInstallationHandler().payDailyUpkeep();
    assertEquals(25.0, home.getBank().getWealth());
    assertSame(cheap, home.getInstallationHandler().getById("station"));
    assertSame(port, home.getInstallationHandler().getById("port"));
    assertNull(home.getInstallationHandler().getById("fort"));
    verify(fixture.online.get("Leader"))
        .sendMessage(
            argThat(
                (String message) -> message.contains("destroyed") && message.contains("upkeep")));
  }

  @Test
  void equalUpkeepIsPaidInCompletionOrder() {
    Installation older = install(home, "older", InstallationKind.PORT, 42, 0, 0, 1, 1);
    install(home, "newer", InstallationKind.PORT, 44, 8, 0, 2, 1);
    home.getBank().setWealth(15.0);
    home.getInstallationHandler().payDailyUpkeep();
    assertEquals(0.0, home.getBank().getWealth());
    assertSame(older, home.getInstallationHandler().getById("older"));
    assertNull(home.getInstallationHandler().getById("newer"));
  }

  @Test
  void freeInstallationDoesNotRequireBankFunds() {
    configurations.put(
        InstallationKind.FORT,
        new InstallationKindConfig(
            80, Map.of(1, new InstallationKindConfig.Level(0, 2, Map.of()))));
    Installation free = install(home, "free", InstallationKind.FORT, 42, 0, 0, 1, 1);
    home.setBank(null);
    home.getInstallationHandler().payDailyUpkeep();
    assertSame(free, home.getInstallationHandler().getById("free"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void unpaidUpgradeDowngradesThenDissolvesWithoutSpendingMoney(boolean online) {
    if (!online) fixture.online.remove("Leader");
    Installation station = install(home, "station", InstallationKind.TRAIN_STATION, 42, 0, 0, 1, 2);
    assertTrue(home.getInstallationHandler().upgrade("station").isSuccess());
    home.getBank().setWealth(0.0);
    home.getInstallationHandler().payDailyUpkeep();
    assertEquals(1, station.getLevel());
    assertNull(home.getInstallationHandler().getPendingConstruction());
    if (online)
      verify(fixture.online.get("Leader"))
          .sendMessage(argThat((String message) -> message.contains("dropped to level 1")));
    home.getInstallationHandler().payDailyUpkeep();
    assertTrue(home.getInstallationHandler().getAll().isEmpty());
    assertEquals(0.0, home.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "maximum", "pending"})
  @NullSource
  @EmptySource
  void upgradeDenialsPreserveInstallationsAndCurrentQueue(String state) {
    Installation station =
        install(
            home,
            "station",
            InstallationKind.TRAIN_STATION,
            42,
            0,
            0,
            1,
            "maximum".equals(state) ? 3 : 1);
    if ("pending".equals(state))
      home.getInstallationHandler().loadConstruction(construction("pending", 44, 10, false));
    InstallationConstruction pending = home.getInstallationHandler().getPendingConstruction();
    String id = state == null || state.isEmpty() || state.equals("missing") ? state : "station";
    assertFalse(home.getInstallationHandler().upgrade(id).isSuccess());
    assertFalse(home.getInstallationHandler().upgradeInstant(id).isSuccess());
    assertSame(pending, home.getInstallationHandler().getPendingConstruction());
    assertSame(station, home.getInstallationHandler().getById("station"));
    assertEquals("maximum".equals(state) ? 3 : 1, station.getLevel());
  }

  @Test
  void instantUpgradeReturnsTheSameInstallationAndQueuesItsMapUpdate() {
    Installation station = install(home, "station", InstallationKind.TRAIN_STATION, 42, 0, 0, 1, 1);
    clearInvocations(fixture.map);
    ConstructResult result = home.getInstallationHandler().upgradeInstant("station");
    assertTrue(result.isSuccess());
    assertSame(station, result.getInstallation());
    assertEquals(2, station.getLevel());
    assertNull(home.getInstallationHandler().getPendingConstruction());
    verify(fixture.map).enqueue("nation", home.getRGB());
  }

  @Test
  void cancellingUpgradeRequiresItsCurrentIdAndLeavesTheCompletedInstallation() {
    Installation station = install(home, "station", InstallationKind.TRAIN_STATION, 42, 0, 0, 1, 1);
    assertTrue(home.getInstallationHandler().upgrade("station").isSuccess());
    assertFalse(home.getInstallationHandler().cancelPendingUpgrade(null));
    assertFalse(home.getInstallationHandler().cancelPendingUpgrade("other"));
    assertFalse(home.getInstallationHandler().cancelPending("other"));
    assertFalse(home.getInstallationHandler().cancelPending(null));
    assertTrue(home.getInstallationHandler().cancelPendingUpgrade("STATION"));
    assertNull(home.getInstallationHandler().getPendingConstruction());
    assertSame(station, home.getInstallationHandler().getById("station"));
    assertEquals(1, station.getLevel());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  ", "missing"})
  void invalidDeconstructionLeavesBothCompletedAndQueuedInstallations(String id) {
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    home.getInstallationHandler().loadConstruction(construction("pending", 44, 10, false));
    assertFalse(home.getInstallationHandler().deconstruct(id).isSuccess());
    assertSame(port, home.getInstallationHandler().getById("port"));
    assertEquals("pending", home.getInstallationHandler().getPendingConstruction().getId());
  }

  @Test
  void deconstructionOfQueuedBuildCancelsWithoutDestroyingACompletedNeighbour() {
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    home.getInstallationHandler().loadConstruction(construction("pending", 44, 10, false));
    ConstructResult result = home.getInstallationHandler().deconstruct("PENDING");
    assertTrue(result.isSuccess());
    assertTrue(result.getMessage().contains("Cancelled"));
    assertNull(home.getInstallationHandler().getPendingConstruction());
    assertSame(port, home.getInstallationHandler().getById("port"));
  }

  @Test
  void losingAProvinceDestroysOnlyItsInstallationsAndCancelsItsQueue() {
    install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    Installation retained =
        install(home, "station", InstallationKind.TRAIN_STATION, 44, 0, 0, 1, 1);
    home.getInstallationHandler().loadConstruction(construction("pending", 42, 10, false));
    home.removeProvince(42, false);
    assertNull(home.getInstallationHandler().getById("port"));
    assertNull(home.getInstallationHandler().getByProvince(InstallationKind.PORT, 42));
    assertNull(home.getInstallationHandler().getPendingConstruction());
    assertSame(retained, home.getInstallationHandler().getById("station"));
    verify(fixture.online.get("Leader"))
        .sendMessage(
            argThat((String message) -> message.contains("destroyed") && message.contains("port")));
  }

  @Test
  void validationPrunesPersistedInstallationsAndQueueOutsideCurrentTerritory() {
    Installation retained = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    install(home, "orphan", InstallationKind.FORT, 99, 0, 0, 1, 1);
    home.getInstallationHandler().loadConstruction(construction("pending", 99, 10, false));
    home.getInstallationHandler().validate();
    assertEquals(List.of(retained), List.copyOf(home.getInstallationHandler().getAll()));
    assertNull(home.getInstallationHandler().getPendingConstruction());
    home.getInstallationHandler().loadConstruction(construction("valid", 44, 10, false));
    home.getInstallationHandler().validate();
    assertEquals("valid", home.getInstallationHandler().getPendingConstruction().getId());
    home.getInstallationHandler().acceptTransferred(null);
    assertEquals(1, home.getInstallationHandler().getAll().size());
  }

  @Test
  void symbolsOnlyConstructionNameIsRejectedWithoutReservingTheProvince() {
    ConstructResult result =
        home.getInstallationHandler().construct(InstallationKind.FORT, "!!!", 42, 0, 0);
    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("Invalid installation name"));
    assertTrue(home.getInstallationHandler().getAll().isEmpty());
    assertNull(home.getInstallationHandler().getPendingConstruction());
  }

  @Test
  void publicConstructionSnapshotPreservesDefaultsAndCompletedTickIsIdempotent() {
    InstallationConstructionData data = construction("legacy", 42, 0, false);
    data.centerX = null;
    data.centerZ = null;
    data.startedAt = null;
    data.upgrade = null;
    InstallationConstruction construction = new InstallationConstruction(data);
    construction.tick();
    assertEquals(0, construction.getTimeLeft());
    assertFalse(construction.isUpgrade());
    assertEquals(0, construction.getCenterX());
    assertEquals(0, construction.getCenterZ());
    assertNotNull(construction.toData().startedAt);
  }

  @Test
  void levelConfigurationDefaultsAndViewsRemainImmutable() {
    InstallationKindConfig config = configurations.get(InstallationKind.TRAIN_STATION);
    assertEquals(5.0, config.getDailyUpkeep());
    assertEquals(259200, config.getConstructionTimeSeconds());
    assertEquals(Map.of("static_emplacements", 2), config.getCategorySlots());
    assertEquals(3, config.getLevels().size());
    assertThrows(UnsupportedOperationException.class, () -> config.getLevels().clear());
    assertThrows(UnsupportedOperationException.class, () -> config.getCategorySlots().clear());
    assertEquals(config.getLevel(1), config.getLevel(-1));
    assertEquals(config.getLevel(3), config.getLevel(99));
  }

  @Test
  void spawnedCenterUsesConfiguredWorldAndSurfaceHeight() {
    Installation port = install(home, "port", InstallationKind.PORT, 42, 2, 3, 1, 1);
    when(fixture.ui.world.getHighestBlockYAt(2, 3)).thenReturn(70);
    Location center = InstallationSpawnService.resolveCenter(port);
    assertEquals(new Location(fixture.ui.world, 2.5, 71, 3.5), center);
    assertNull(InstallationSpawnService.resolveCenter(null));
    String world = Cache.worldName;
    try {
      Cache.worldName = "";
      assertNull(InstallationSpawnService.resolveCenter(port));
      Cache.worldName = null;
      assertNull(InstallationSpawnService.resolveCenter(port));
    } finally {
      Cache.worldName = world;
    }
    when(Bukkit.getWorld(world)).thenReturn(null);
    assertNull(InstallationSpawnService.resolveCenter(port));
  }

  @ParameterizedTest
  @ValueSource(strings = {"from", "to", "province", "same"})
  void invalidOrSameFactionTransferPreservesAllOwnership(String reason) {
    Faction destination = faction("destination", "Recipient", 43);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    InstallationTransferService.transfer(
        reason.equals("from") ? null : home,
        reason.equals("to") ? null : reason.equals("same") ? home : destination,
        reason.equals("province") ? 0 : 42);
    assertSame(port, home.getInstallationHandler().getById("port"));
    assertTrue(destination.getInstallationHandler().getAll().isEmpty());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyVehicleMovesOnlyWhenTheTransferredInstallationRemainsUnambiguous(boolean ambiguous) {
    Faction destination = faction("destination", "Recipient", 43);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    if (ambiguous)
      install(faction("unrelated", "Other", 45), "port", InstallationKind.PORT, 45, 8, 0, 1, 1);
    PlayerVehicleRecord legacy =
        new PlayerVehicleRecord(
            UUID.randomUUID(), "legacy", "ironclad", OwnershipMode.INSTALLATION, "port");
    registry.register(legacy);
    InstallationTransferService.transfer(home, destination, 42);
    assertSame(port, destination.getInstallationHandler().getById("port"));
    PlayerVehicleRecord updated = registry.getByVehicleUuid("legacy").orElseThrow();
    if (ambiguous) assertSame(legacy, updated);
    else assertEquals(destination.getId(), updated.getFactionId());
    assertTrue(home.getInstallationHandler().getAll().isEmpty());
  }

  @Test
  void transferChoosesAnUnusedIdBeyondBothCompletedAndPendingNames() {
    Faction destination = faction("destination", "Recipient", 43, 45);
    install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    Installation original = install(destination, "port", InstallationKind.PORT, 43, 4, 0, 2, 1);
    Installation suffix = install(destination, "port_42_1", InstallationKind.FORT, 43, 5, 0, 3, 1);
    destination.getInstallationHandler().loadConstruction(construction("port_42_2", 45, 20, false));
    InstallationTransferService.transfer(home, destination, 42);
    Installation moved =
        destination.getInstallationHandler().getByProvince(InstallationKind.PORT, 42);
    assertNotNull(moved);
    assertNotEquals("port_42_2", moved.getId());
    assertNotEquals("port_42_1", moved.getId());
    assertNotEquals("port", moved.getId());
    assertSame(original, destination.getInstallationHandler().getById("port"));
    assertSame(suffix, destination.getInstallationHandler().getById("port_42_1"));
    assertEquals(
        "port_42_2", destination.getInstallationHandler().getPendingConstruction().getId());
    assertEquals(3, destination.getInstallationHandler().getAll().size());
  }

  @Test
  void externalVehicleFailureDefersOnlyThatOwnerSyncWhileAllRegistryEntriesTransfer() {
    Faction destination = faction("destination", "Recipient", 43);
    install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    for (String id : List.of("unavailable", "loaded"))
      registry.register(
          new PlayerVehicleRecord(
              UUID.randomUUID(), id, "ironclad", OwnershipMode.INSTALLATION, "port", home.getId()));
    when(vehicles.get("unavailable")).thenThrow(new IllegalStateException("external failure"));
    OwnerData owner = new OwnerData();
    owner.setOwner("player_Leader");
    ActiveVehicle loaded = mock(ActiveVehicle.class);
    when(loaded.getOwnerData()).thenReturn(owner);
    when(vehicles.get("loaded")).thenReturn(loaded);
    Logger logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);
    InstallationTransferService.transfer(home, destination, 42);
    for (String id : List.of("unavailable", "loaded"))
      assertEquals(destination.getId(), registry.getByVehicleUuid(id).orElseThrow().getFactionId());
    assertEquals("player_Recipient", owner.getOwner());
    verify(logger).warning(contains("unavailable"));
    verify(fixture.ui.plugin).saveVehicleRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedRegistrySaveReportsTheTransferredInstallationWithoutLosingLiveOwnership(
      boolean exceptional) {
    Faction destination = faction("destination", "Recipient", 43);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    registry.register(
        new PlayerVehicleRecord(
            UUID.randomUUID(),
            "ship",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "port",
            home.getId()));
    OwnerData owner = new OwnerData();
    owner.setOwner("player_Leader");
    ActiveVehicle loaded = mock(ActiveVehicle.class);
    when(loaded.getOwnerData()).thenReturn(owner);
    when(vehicles.get("ship")).thenReturn(loaded);
    Logger logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);
    if (exceptional)
      when(fixture.ui.plugin.saveVehicleRegistry())
          .thenThrow(new IllegalStateException("disk unavailable"));
    else when(fixture.ui.plugin.saveVehicleRegistry()).thenReturn(false);

    InstallationTransferService.transfer(home, destination, 42);

    assertSame(port, destination.getInstallationHandler().getById("port"));
    assertTrue(home.getInstallationHandler().getAll().isEmpty());
    assertEquals(
        destination.getId(), registry.getByVehicleUuid("ship").orElseThrow().getFactionId());
    assertEquals("player_Recipient", owner.getOwner());
    verify(fixture.ui.plugin).saveVehicleRegistry();
    verify(logger)
        .warning(
            argThat(
                (String message) -> message.contains("port") && message.contains("destination")));
  }

  @Test
  void transferWithoutEnabledPluginPreservesTheInstallationForLaterVehicleReconciliation() {
    Faction destination = faction("destination", "Recipient", 43);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    SimpleFactions plugin = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      InstallationTransferService.transfer(home, destination, 42);
    } finally {
      SimpleFactions.plugin = plugin;
    }
    assertSame(port, destination.getInstallationHandler().getById("port"));
    assertTrue(home.getInstallationHandler().getAll().isEmpty());
  }

  @Test
  void occupationAndRecaptureReturnASubjectsInstallationToItsOriginalHolder() {
    Faction defender = faction("defender", "Defender", 43);
    Faction attacker = faction("attacker", "Attacker", 45);
    fixture.subject(defender, home);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    War war = new War(5, attacker, defender);
    war.setLastBattleOccupied(List.of(42));
    WartimeInstallationService.occupyLastBattle(war, BelligerentRole.ATTACKER);
    assertSame(port, attacker.getInstallationHandler().getById("port"));
    WartimeInstallationService.occupy(war, defender, 42);
    assertSame(port, home.getInstallationHandler().getById("port"));
    assertTrue(attacker.getInstallationHandler().getAll().isEmpty());
    assertTrue(defender.getInstallationHandler().getAll().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null_war",
        "null_winner",
        "empty",
        "null_slot",
        "field_slot",
        "missing_fort",
        "already_occupied",
        "invalid_province",
        "missing_holder"
      })
  void invalidWartimeTargetsLeaveInstallationsWhereTheyAre(String state) {
    Faction attacker = faction("attacker", "Attacker", 43);
    Installation fort = install(home, "fort", InstallationKind.FORT, 42, 0, 0, 1, 1);
    War war = new War(6, attacker, home);
    ScheduledCampaignBattle siege =
        new ScheduledCampaignBattle(42, CampaignBattleKind.SIEGE, false, "fort");
    switch (state) {
      case "null_war" -> {
        WartimeInstallationService.occupyLastBattle(null, BelligerentRole.ATTACKER);
        WartimeInstallationService.occupySiegeFort(null, BelligerentRole.ATTACKER, siege);
        WartimeInstallationService.occupy(null, attacker, 42);
        WartimeInstallationService.revert(null);
      }
      case "null_winner" -> {
        WartimeInstallationService.occupyLastBattle(war, null);
        WartimeInstallationService.occupySiegeFort(war, null, siege);
        WartimeInstallationService.occupy(war, null, 42);
      }
      case "empty" -> {
        WartimeInstallationService.occupyLastBattle(war, BelligerentRole.ATTACKER);
        WartimeInstallationService.revert(war);
      }
      case "null_slot" ->
          WartimeInstallationService.occupySiegeFort(war, BelligerentRole.ATTACKER, null);
      case "field_slot" ->
          WartimeInstallationService.occupySiegeFort(
              war,
              BelligerentRole.ATTACKER,
              new ScheduledCampaignBattle(42, CampaignBattleKind.FIELD, false, null));
      case "missing_fort" ->
          WartimeInstallationService.occupySiegeFort(
              war,
              BelligerentRole.ATTACKER,
              new ScheduledCampaignBattle(99, CampaignBattleKind.SIEGE, false, "missing"));
      case "already_occupied" -> {
        war.setLastBattleOccupied(List.of(42));
        WartimeInstallationService.occupySiegeFort(war, BelligerentRole.ATTACKER, siege);
      }
      case "invalid_province" -> WartimeInstallationService.occupy(war, attacker, -1);
      case "missing_holder" -> WartimeInstallationService.occupy(war, attacker, 99);
    }
    assertSame(fort, home.getInstallationHandler().getById("fort"));
    assertTrue(attacker.getInstallationHandler().getAll().isEmpty());
    assertTrue(war.getWartimeInstallationOwners().isEmpty());
  }

  @Test
  void migratedLegacyWartimeSnapshotReturnsAnUnambiguousInstallation() {
    Faction attacker = faction("attacker", "Attacker", 43);
    Installation port = install(attacker, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    War war = new War(7, attacker, home);
    Map<String, String> legacy = new LinkedHashMap<>();
    legacy.put(null, home.getId());
    legacy.put("missing-owner", null);
    legacy.put("missing-port", "deleted-faction");
    legacy.put("port", home.getId());
    war.setWartimeInstallationOwners(legacy);
    net.tfminecraft.simplefactions.installation.WarInstallationMigration.migrate(war);
    WartimeInstallationService.revert(war);
    assertSame(port, home.getInstallationHandler().getById("port"));
    assertTrue(attacker.getInstallationHandler().getAll().isEmpty());
    assertTrue(war.getWartimeInstallationOwners().isEmpty());
  }

  @Test
  void malformedSnapshotEntriesDoNotPreventReturningTheValidPhysicalInstallation() {
    Faction attacker = faction("attacker", "Attacker", 43);
    Installation port = install(attacker, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    War war = new War(11, attacker, home);
    Map<String, String> snapshot = new LinkedHashMap<>();
    snapshot.put(null, home.getId());
    snapshot.put("missing-owner", null);
    snapshot.put(port.getStableKey(), home.getId());
    war.setWartimeInstallationOwners(snapshot);

    WartimeInstallationService.revert(war);

    assertSame(port, home.getInstallationHandler().getById("port"));
    assertTrue(attacker.getInstallationHandler().getAll().isEmpty());
    assertTrue(war.getWartimeInstallationOwners().isEmpty());
  }

  @Test
  void invalidPersistedLevelCannotCreateLevelZeroInstallation() {
    Installation installation =
        new Installation("old", "Old", InstallationKind.TRAIN_STATION, 42, 0, 0, 1, -9);
    assertEquals(1, installation.getLevel());
    installation.setLevel(0);
    assertEquals(1, installation.getLevel());
    assertNull(InstallationKind.fromCommand(null));
  }

  @Test
  void defendingWinnerRecoversItsInstallationWithoutReplacingIt() {
    Faction attacker = faction("attacker", "Attacker", 43);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    War war = new War(8, attacker, home);
    WartimeInstallationService.occupy(war, attacker, 42);
    assertSame(port, attacker.getInstallationHandler().getById("port"));
    war.setLastBattleOccupied(List.of(42));
    WartimeInstallationService.occupyLastBattle(war, BelligerentRole.DEFENDER);
    assertSame(port, home.getInstallationHandler().getById("port"));
    assertTrue(attacker.getInstallationHandler().getAll().isEmpty());
    WartimeInstallationService.revert(war);
    assertSame(port, home.getInstallationHandler().getById("port"));
    assertTrue(war.getWartimeInstallationOwners().isEmpty());
  }

  @Test
  void deletedOriginalHolderDoesNotLoseTheCurrentInstallationDuringSnapshotReconciliation() {
    Faction attacker = faction("attacker", "Attacker", 43);
    Installation port = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    War war = new War(9, attacker, home);
    WartimeInstallationService.occupy(war, attacker, 42);
    FactionManager.factions.remove(home);
    WartimeInstallationService.occupy(war, attacker, 42);
    assertSame(port, attacker.getInstallationHandler().getById("port"));
    WartimeInstallationService.revert(war);
    assertSame(port, attacker.getInstallationHandler().getById("port"));
    assertTrue(war.getWartimeInstallationOwners().isEmpty());
  }

  @Test
  void ambiguousLegacySnapshotDoesNotMoveAnUnrelatedSameNamedInstallation() {
    Faction attacker = faction("attacker", "Attacker", 43);
    Installation mine = install(home, "port", InstallationKind.PORT, 42, 0, 0, 1, 1);
    Installation theirs = install(attacker, "port", InstallationKind.PORT, 43, 8, 0, 2, 1);
    War war = new War(10, attacker, home);
    war.setWartimeInstallationOwners(Map.of("port", home.getId()));
    WartimeInstallationService.revert(war);
    assertSame(mine, home.getInstallationHandler().getById("port"));
    assertSame(theirs, attacker.getInstallationHandler().getById("port"));
    assertTrue(war.getWartimeInstallationOwners().isEmpty());
  }

  private void shortConstruction(InstallationKind kind, int seconds) {
    InstallationKindConfig previous = configurations.get(kind);
    Map<Integer, InstallationKindConfig.Level> levels = new LinkedHashMap<>();
    previous
        .getLevels()
        .forEach(
            (level, value) ->
                levels.put(
                    level,
                    new InstallationKindConfig.Level(
                        value.dailyUpkeep(), seconds, value.categorySlots())));
    configurations.put(kind, new InstallationKindConfig(previous.getRadius(), levels));
  }

  private InstallationConstructionData construction(
      String id, int province, int ticks, boolean upgrade) {
    return new InstallationConstruction(
            id, id, InstallationKind.FORT, province, 0, 0, ticks, 1, upgrade)
        .toData();
  }

  private Faction faction(String id, String leader, int... provinces) {
    FactionData data = fixture.data(id, leader);
    for (int province : provinces) {
      data.provinces.add(province);
      fixture.provinceData.put(province, new Province(province, "PLAINS", 1));
    }
    Faction result = fixture.saved(data);
    result.getOrCreateMainGuild();
    return result;
  }

  private Installation install(
      Faction faction,
      String id,
      InstallationKind kind,
      int province,
      int x,
      int z,
      long completed,
      int level) {
    Installation installation = new Installation(id, id, kind, province, x, z, completed, level);
    faction.getInstallationHandler().acceptTransferred(installation);
    return installation;
  }

  private static void restoreConfig(String name, int value) throws Exception {
    Field field = InstallationConfigLoader.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(null, value);
  }
}
