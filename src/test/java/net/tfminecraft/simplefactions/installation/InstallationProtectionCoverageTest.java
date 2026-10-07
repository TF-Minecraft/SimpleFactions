package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleInstallationLockService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.CapturePointDefinition;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidLaunchService;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.events.VFEntityDamageEvent;
import net.tfminecraft.vehicleframework.events.VFExplosionEvent;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Protection follows the actual installation, province, and game world. */
class InstallationProtectionCoverageTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private Faction neutral;
  private Installation harbor;
  private Player player;
  private InstallationProtectionListener listener;
  private PlayerVehicleRegistry registry;
  private VehicleManager vehicles;
  private MockedStatic<VehicleFramework> vehicleFramework;
  private Map<InstallationKind, InstallationKindConfig> configurations;
  private Map<InstallationKind, InstallationKindConfig> previousConfigurations;
  private Object previousBattles;
  private final Map<Field, Object> vehicleConfigurations = new LinkedHashMap<>();
  private int previousProximity;
  private int previousTimeout;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    fixture = new FactionDomainFixture();
    when(fixture.ui.world.getName()).thenReturn("world");
    Field config = field(InstallationConfigLoader.class, "byKind");
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
    Field battles = field(BattleManager.class, "battles");
    previousBattles = battles.get(null);
    battles.set(null, new ArrayList<Battle>());
    neutral = faction("neutral", "Neutral", 42);
    harbor = install(neutral, "harbor", 42, 0);
    player = fixture.player("Visitor");
    listener = new InstallationProtectionListener();
    ByteBuffer bytes = ByteBuffer.allocate(8 + 16 * 16 * 2).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(16).putInt(16);
    for (int z = 0; z < 16; z++)
      for (int x = 0; x < 16; x++) bytes.putShort((short) (x < 8 ? 42 : 43));
    Path path = files.root.resolve("province.bin.gz");
    try (GZIPOutputStream stream = new GZIPOutputStream(Files.newOutputStream(path))) {
      stream.write(bytes.array());
    }
    ProvinceGrid grid = ProvinceGrid.load(path.toFile());
    when(fixture.ui.plugin.getProvinceGrid()).thenReturn(grid);
    registry = new PlayerVehicleRegistry();
    field(SimpleFactions.class, "vehicleRegistry").set(fixture.ui.plugin, registry);
    vehicles = mock(VehicleManager.class);
    vehicleFramework = mockStatic(VehicleFramework.class);
    vehicleFramework.when(VehicleFramework::getVehicleManager).thenReturn(vehicles);
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
        .thenAnswer(call -> mock(BossBar.class));
  }

  @AfterEach
  void close() throws Exception {
    try {
      vehicleFramework.close();
      field(BattleManager.class, "battles").set(null, previousBattles);
      configurations.clear();
      configurations.putAll(previousConfigurations);
      for (Map.Entry<Field, Object> entry : vehicleConfigurations.entrySet())
        entry.getKey().set(null, entry.getValue());
      field(InstallationConfigLoader.class, "consentProximityBlocks")
          .setInt(null, previousProximity);
      field(InstallationConfigLoader.class, "transferRequestTimeoutSeconds")
          .setInt(null, previousTimeout);
    } finally {
      fixture.close();
      files.close();
    }
  }

  @Test
  void raidOnAnotherFactionsSameNamedPortDoesNotExposeNeutralBlocks() {
    raid();
    BlockBreakEvent neutralBreak = breakAt(new Location(fixture.ui.world, 0, 64, 0));
    BlockBreakEvent raidingBreak = breakAt(new Location(fixture.ui.world, 8, 64, 0));

    listener.onBlockBreak(neutralBreak);
    listener.onBlockBreak(raidingBreak);

    assertTrue(neutralBreak.isCancelled(), "A neutral faction's same-named port stays protected");
    assertFalse(raidingBreak.isCancelled(), "The actual fighting raid's source is vulnerable");
    assertSame(harbor, neutral.getInstallationHandler().getById("harbor"));
  }

  @Test
  void identicalCoordinatesInAnotherWorldDoNotProtectUnrelatedBlocks() {
    World other = mock(World.class);
    when(other.getName()).thenReturn("nether");
    BlockBreakEvent event = breakAt(new Location(other, 0, 64, 0));
    listener.onBlockBreak(event);
    assertFalse(event.isCancelled(), "Installation bounds belong to the configured game world");
  }

  @Test
  void staffRaidTargetInAnotherWorldDoesNotExposeThisWorldsInstallation() {
    Battle battle = BattleFactory.createBlank(BattleType.RAID, "other_world_raid");
    battle.setStarted(true);
    battle.setRaidTarget(
        new CapturePointDefinition("target", new BattleLocation("nether", 0, 64, 0, 0, 0)));
    BattleManager.addBattle(battle);
    BlockBreakEvent event = breakAt(new Location(fixture.ui.world, 0, 64, 0));
    listener.onBlockBreak(event);
    assertTrue(event.isCancelled());
  }

  @ParameterizedTest
  @ValueSource(strings = {"break", "place", "explosion", "vehicle", "passenger", "registered"})
  void peacefulCompletedInstallationProtectsBlocksAndVehicles(String eventType) {
    assertTrue(protectedAt(eventType, new Location(fixture.ui.world, 0, 64, 0), neutral));
    assertSame(harbor, InstallationLookup.findCovering(new Location(fixture.ui.world, 0, 64, 0)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"break", "place", "explosion", "vehicle", "passenger", "registered"})
  void unrelatedFightingRaidDoesNotRemoveAnyNeutralInstallationProtection(String eventType) {
    raid();
    assertTrue(protectedAt(eventType, new Location(fixture.ui.world, 0, 64, 0), neutral));
  }

  @ParameterizedTest
  @ValueSource(strings = {"break", "place", "explosion", "vehicle", "passenger", "registered"})
  void actualFightingRaidSourceIsVulnerableThroughEveryPublicEvent(String eventType) {
    War war = raid();
    assertFalse(
        protectedAt(
            eventType, new Location(fixture.ui.world, 8, 64, 0), war.getAttackers().getLeader()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"break", "place", "explosion", "vehicle", "passenger"})
  void outsideTheRadiusAndInAnotherProvinceEventsStayUntouched(String eventType) {
    assertFalse(protectedAt(eventType, new Location(fixture.ui.world, 500, 64, 500), neutral));
    assertFalse(protectedAt(eventType, new Location(fixture.ui.world, 8, 64, 0), neutral));
  }

  @ParameterizedTest
  @ValueSource(strings = {"simplefactions.admin", "warbands.admin"})
  void eitherStaffPermissionBypassesBlockBreakPlaceAndPassengerProtection(String permission) {
    when(player.hasPermission(permission)).thenReturn(true);
    assertFalse(protectedAt("break", new Location(fixture.ui.world, 0, 64, 0), neutral));
    assertFalse(protectedAt("place", new Location(fixture.ui.world, 0, 64, 0), neutral));
    VFEntityDamageEvent damage = new VFEntityDamageEvent(player, null, "test", 12);
    listener.onVehicleDamage(damage);
    assertFalse(damage.isCancelled());
    assertEquals(12, damage.getDamage());
    verifyNoInteractions(vehicles);
  }

  @Test
  void aMissingExplosionLocationDoesNotChangeItsRequestedBlockDamage() {
    VFExplosionEvent explosion = new VFExplosionEvent(null);
    explosion.setBlockDamage(true);
    listener.onExplosion(explosion);
    assertTrue(explosion.doesBlockDamage());
    assertFalse(explosion.isCancelled());
  }

  @ParameterizedTest
  @ValueSource(strings = {"none", "lookup_exception", "passenger_exception", "null_entity"})
  void unavailableExternalVehicleLookupDoesNotCancelUnrelatedDamage(String failure) {
    Entity entity = failure.equals("null_entity") ? null : mock(Entity.class);
    if (failure.equals("lookup_exception"))
      when(vehicles.get(entity)).thenThrow(new IllegalStateException("unavailable"));
    if (failure.equals("passenger_exception"))
      when(vehicles.getByPassenger(entity)).thenThrow(new IllegalStateException("unavailable"));
    VFEntityDamageEvent damage = new VFEntityDamageEvent(entity, null, "test", 12);
    listener.onVehicleDamage(damage);
    assertFalse(damage.isCancelled());
    assertEquals(12, damage.getDamage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"personal", "pool", "missing"})
  void unberthedVehicleOutsideAnyInstallationIsNotProtected(String mode) {
    Entity entity = mock(Entity.class);
    ActiveVehicle vehicle =
        externalVehicle("vehicle", new Location(fixture.ui.world, 500, 64, 500));
    when(vehicles.get(entity)).thenReturn(vehicle);
    if (!mode.equals("missing"))
      registry.register(
          new PlayerVehicleRecord(
              player.getUniqueId(),
              "vehicle",
              "ironclad",
              mode.equals("pool") ? OwnershipMode.POOL : OwnershipMode.PERSONAL,
              null,
              mode.equals("pool") ? neutral.getId() : null));
    VFEntityDamageEvent damage = new VFEntityDamageEvent(entity, null, "test", 12);
    listener.onVehicleDamage(damage);
    assertFalse(damage.isCancelled());
  }

  @Test
  void berthedVehicleRemainsProtectedAwayFromTheInstallationAtPeace() {
    assertTrue(protectedAt("registered", new Location(fixture.ui.world, 500, 64, 500), neutral));
  }

  @ParameterizedTest
  @ValueSource(strings = {"no_target", "not_started", "other_type", "far_target"})
  void inactiveOrUnrelatedManualBattleDoesNotExposeTheInstallation(String state) {
    Battle battle = new Battle("manual");
    battle.setBattleType(state.equals("other_type") ? BattleType.FIELD : BattleType.RAID);
    battle.setStarted(!state.equals("not_started"));
    if (!state.equals("no_target"))
      battle.setRaidTarget(
          new CapturePointDefinition(
              "target",
              new BattleLocation("world", state.equals("far_target") ? 500 : 0, 64, 0, 0, 0)));
    BattleManager.addBattle(battle);
    assertTrue(protectedAt("break", new Location(fixture.ui.world, 0, 64, 0), neutral));
  }

  @Test
  void activeManualRaidAtTheInstallationMakesItVulnerable() {
    Battle battle = new Battle("manual");
    battle.setBattleType(BattleType.RAID);
    battle.setStarted(true);
    battle.setRaidTarget(
        new CapturePointDefinition("target", new BattleLocation("world", 0, 64, 0, 0, 0)));
    BattleManager.addBattle(battle);
    assertFalse(protectedAt("break", new Location(fixture.ui.world, 0, 64, 0), neutral));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"started", "not_started", "missing_war", "ended_war", "raid_flag", "unpicked"})
  void campaignBattleProtectsOnlyUntilItsOwnCurrentInstallationIsInPlay(String state) {
    Faction enemy = faction("enemy", "Enemy", 44);
    War war = new War(12, neutral, enemy);
    war.setBattleDay(LocalDate.of(2026, 10, 7));
    war.setBattleInstallationPicksBattleDay(war.getBattleDay());
    war.setOccupiedByAttacker(List.of(42));
    if (!state.equals("unpicked"))
      war.setBattleInstallationPicks(
          Map.of(neutral.getId(), new LinkedHashSet<>(List.of(harbor.getId()))));
    if (!state.equals("missing_war")) WarManager.addWar(war);
    if (state.equals("ended_war")) war.end(WarEndReason.WHITE_PEACE);
    Battle battle = new Battle("campaign");
    battle.setWarId(war.getId());
    battle.setStarted(!state.equals("not_started"));
    battle.setCampaignRaid(state.equals("raid_flag"));
    BattleManager.addBattle(battle);
    assertEquals(
        !state.equals("started"),
        protectedAt("break", new Location(fixture.ui.world, 0, 64, 0), neutral));
  }

  @ParameterizedTest
  @NullSource
  @EmptySource
  @ValueSource(strings = {" ", "missing"})
  void unknownLegacyInstallationIdsAreNeverVulnerable(String id) {
    assertFalse(InstallationVulnerabilityService.isVulnerable(id, Instant.now()));
    assertFalse(VehicleInstallationLockService.isVehicleLocked(id, Instant.now()));
    assertNull(InstallationLookup.findById(id));
    assertNull(InstallationLookup.findHolder(id));
  }

  @Test
  void lookupAndOwnerQueriesPreserveActualObjectIdentityAndReturnImmutableSnapshots() {
    Faction other = faction("other", "Other", 43);
    Installation otherHarbor = install(other, "harbor", 43, 8);
    assertSame(neutral, InstallationOwners.ownerOf(harbor));
    assertSame(other, InstallationOwners.ownerOf(otherHarbor));
    assertEquals("other", InstallationOwners.ownerIdOf(otherHarbor));
    assertNull(InstallationOwners.ownerOf(null));
    Installation detached =
        new Installation("harbor", "Harbor", InstallationKind.PORT, 42, 0, 0, 0);
    assertNull(InstallationOwners.ownerOf(detached));
    assertNull(InstallationOwners.ownerIdOf(detached));
    assertSame(other, InstallationLookup.findHolderOnProvince(43));
    assertNull(InstallationLookup.findHolderOnProvince(-1));
    assertNull(InstallationLookup.findHolderOnProvince(99));
    assertNull(InstallationLookup.findCovering(null));
    List<Installation> snapshot = InstallationLookup.all();
    assertEquals(2, snapshot.size());
    assertThrows(UnsupportedOperationException.class, snapshot::clear);
    other.getInstallationHandler().deconstruct("harbor");
    assertEquals(2, snapshot.size());
    assertEquals(List.of(harbor), InstallationLookup.all());
    assertFalse(InstallationVulnerabilityService.isVulnerable("harbor", null));
    assertFalse(VehicleInstallationLockService.isVehicleLocked("harbor", null));
    assertFalse(VehicleInstallationLockService.isInstallationLocked(harbor, null));
    assertFalse(VehicleInstallationLockService.isInstallationLocked(null, Instant.now()));
  }

  @Test
  void raidOnOneDefendersPortDoesNotExposeAnAlliedsSameNamedPort() {
    War war = raid();
    Faction allied = faction("allied", "Allied", 45);
    Installation alliedPort = install(allied, "enemy_port", 45, 300);
    war.getDefenders().getMainParticipants().add(new Participant(allied));
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(),
            "allied-vehicle",
            "ironclad",
            OwnershipMode.INSTALLATION,
            alliedPort.getId(),
            allied.getId()));
    Entity entity = mock(Entity.class);
    ActiveVehicle vehicle =
        externalVehicle("allied-vehicle", new Location(fixture.ui.world, 500, 64, 500));
    when(vehicles.get(entity)).thenReturn(vehicle);
    VFEntityDamageEvent damage = new VFEntityDamageEvent(entity, null, "test", 12);
    listener.onVehicleDamage(damage);
    assertTrue(
        damage.isCancelled(),
        "A distinct allied defender's same-named installation was not the selected raid target");
  }

  @Test
  void legacyVulnerabilityLookupRefusesAmbiguousIdsWhileDirectLookupStillFindsRealObjects() {
    assertSame(harbor, InstallationLookup.findById("harbor"));
    assertSame(neutral, InstallationLookup.findHolder("harbor"));
    Faction other = faction("other", "Other", 43);
    install(other, "harbor", 43, 8);
    assertFalse(InstallationVulnerabilityService.isVulnerable("harbor", Instant.now()));
  }

  @Test
  void orphanedVehicleRegistryEntryStaysProtectedUntilItsOwnershipCanBeReconciled() {
    Entity entity = mock(Entity.class);
    ActiveVehicle orphan = externalVehicle("orphan", new Location(fixture.ui.world, 500, 64, 500));
    when(vehicles.get(entity)).thenReturn(orphan);
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(),
            "orphan",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "missing",
            "deleted"));
    VFEntityDamageEvent damage = new VFEntityDamageEvent(entity, null, "test", 12);
    listener.onVehicleDamage(damage);
    assertTrue(damage.isCancelled());
  }

  @Test
  void activeRaidStillBlocksBerthingWhenANeutralFactionReusesTheSourceId() {
    War war = raid();
    war.setBattleDay(LocalDate.now().plusDays(1));
    war.getActiveCampaignRaid().setBattleDay(war.getBattleDay());
    Installation source = war.getAttackers().getLeader().getInstallationHandler().getById("harbor");
    ActiveVehicle vehicle =
        externalVehicle("personal-ship", new Location(fixture.ui.world, 8, 64, 0));
    when(vehicle.getId()).thenReturn("ironclad");
    OwnerData owner = new OwnerData();
    owner.setOwner("player_Visitor");
    when(vehicle.getOwnerData()).thenReturn(owner);
    InstallationVehicleService service =
        new InstallationVehicleService(registry, new InstallationVehicleOwnerSync(registry));
    assertEquals(
        InstallationVehicleService.CanRegisterResult.REPAIR_LOCKED,
        service.canRegister(source, vehicle));
    when(vehicle.getLocation()).thenReturn(new Location(fixture.ui.world, 0, 64, 0));
    assertEquals(
        InstallationVehicleService.CanRegisterResult.OK, service.canRegister(harbor, vehicle));
    assertTrue(registry.getByVehicleUuid("personal-ship").isEmpty());
  }

  @Test
  void releaseValidationLocksOnlyTheActualRaidingFactionsVehicle() {
    War war = raid();
    war.setBattleDay(LocalDate.now().plusDays(1));
    war.getActiveCampaignRaid().setBattleDay(war.getBattleDay());
    Faction attacker = war.getAttackers().getLeader();
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(),
            "raiding",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            attacker.getId()));
    registry.register(
        new PlayerVehicleRecord(
            player.getUniqueId(),
            "neutral",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "harbor",
            neutral.getId()));
    FactionVehicleReleaseService releases = new FactionVehicleReleaseService(registry);
    assertEquals(
        FactionVehicleReleaseService.Status.INSTALLATION_LOCKED,
        releases
            .evaluateGive(attacker, attacker.getLeader(), player.getName(), "raiding")
            .status());
    assertEquals(
        FactionVehicleReleaseService.Status.OK,
        releases.evaluateGive(neutral, neutral.getLeader(), player.getName(), "neutral").status());
    assertEquals(
        attacker.getId(), registry.getByVehicleUuid("raiding").orElseThrow().getFactionId());
    assertEquals(
        neutral.getId(), registry.getByVehicleUuid("neutral").orElseThrow().getFactionId());
    verify(fixture.ui.plugin, never()).saveVehicleRegistry();
  }

  @Test
  void transferCommandCannotArmARaidingInstallationDespiteAnUnrelatedDuplicateId() {
    War war = raid();
    war.setBattleDay(LocalDate.now().plusDays(1));
    war.getActiveCampaignRaid().setBattleDay(war.getBattleDay());
    Player attacker = fixture.player("Attacker");
    VehicleFactionCommands.armTransfer(attacker, "harbor");
    verify(attacker).sendMessage(VehicleInstallationLockService.BERTH_BLOCKED);
    verify(fixture.ui.plugin, never()).getVehicleTransferSessionManager();
  }

  private boolean protectedAt(String eventType, Location location, Faction registeredHolder) {
    if (eventType.equals("break")) {
      BlockBreakEvent event = breakAt(location);
      listener.onBlockBreak(event);
      return event.isCancelled();
    }
    if (eventType.equals("place")) {
      Block block = mock(Block.class);
      when(block.getLocation()).thenReturn(location);
      BlockPlaceEvent event =
          new BlockPlaceEvent(
              block,
              mock(BlockState.class),
              mock(Block.class),
              new ItemStack(Material.STONE),
              player,
              true,
              EquipmentSlot.HAND);
      listener.onBlockPlace(event);
      return event.isCancelled();
    }
    if (eventType.equals("explosion")) {
      VFExplosionEvent event = new VFExplosionEvent(location);
      event.setBlockDamage(true);
      listener.onExplosion(event);
      assertFalse(event.isCancelled());
      return !event.doesBlockDamage();
    }
    Entity entity = mock(Entity.class);
    ActiveVehicle vehicle = externalVehicle("vehicle", location);
    if (eventType.equals("passenger")) when(vehicles.getByPassenger(entity)).thenReturn(vehicle);
    else when(vehicles.get(entity)).thenReturn(vehicle);
    if (eventType.equals("registered"))
      registry.register(
          new PlayerVehicleRecord(
              player.getUniqueId(),
              "vehicle",
              "ironclad",
              OwnershipMode.INSTALLATION,
              "harbor",
              registeredHolder.getId()));
    VFEntityDamageEvent event = new VFEntityDamageEvent(entity, null, "test", 12);
    listener.onVehicleDamage(event);
    assertEquals(12, event.getDamage());
    return event.isCancelled();
  }

  private ActiveVehicle externalVehicle(String id, Location location) {
    ActiveVehicle vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn(id);
    when(vehicle.getLocation()).thenReturn(location);
    return vehicle;
  }

  private War raid() {
    Faction attacker = faction("attacker", "Attacker", 43);
    Faction defender = faction("defender", "Defender", 44);
    install(attacker, "harbor", 43, 8);
    install(defender, "enemy_port", 44, 200);
    War war = new War(1, attacker, defender);
    war.setBattleDay(LocalDate.of(2026, 10, 7));
    CampaignRaid raid = new CampaignRaid();
    raid.setId("raid_1");
    raid.setWarId(1);
    raid.setBattleDay(war.getBattleDay());
    raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
    raid.setLauncherFactionId(attacker.getId());
    raid.setSourceInstallationId("harbor");
    raid.setTargetInstallationId("enemy_port");
    raid.setState(CampaignRaidState.FIGHTING);
    war.setActiveCampaignRaid(raid);
    WarManager.addWar(war);
    return war;
  }

  private Faction faction(String id, String leader, int province) {
    FactionData data = fixture.data(id, leader);
    data.provinces.add(province);
    Faction result = fixture.saved(data);
    result.getOrCreateMainGuild();
    return result;
  }

  private Installation install(Faction faction, String id, int province, int x) {
    Installation installation = new Installation(id, id, InstallationKind.PORT, province, x, 0, 0);
    faction.getInstallationHandler().acceptTransferred(installation);
    return installation;
  }

  private BlockBreakEvent breakAt(Location location) {
    Block block = mock(Block.class);
    when(block.getLocation()).thenReturn(location);
    return new BlockBreakEvent(block, player);
  }

  private static Field field(Class<?> owner, String name) throws Exception {
    Field result = owner.getDeclaredField(name);
    result.setAccessible(true);
    return result;
  }
}

/** Drives the actual raid writer; the fixture scopes its own Paper and persistence boundaries. */
class InstallationRepairEmbargoCoverageTest {
  @Test
  void completedRaidRepairEmbargoDoesNotFollowAnotherAlliedsSameNamedInstallation()
      throws Exception {
    try (var rig = new CampaignRaidLifecycleCoverageTest.Fixture()) {
      Field registryWiring = SimpleFactions.class.getDeclaredField("vehicleRegistry");
      registryWiring.setAccessible(true);
      registryWiring.set(rig.domain.ui.plugin, new PlayerVehicleRegistry());
      when(rig.domain.ui.plugin.saveVehicleRegistry()).thenReturn(true);
      assertEquals(
          CampaignRaidResults.LaunchResult.STARTED,
          CampaignRaidService.beginMuster(
              rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
      CampaignRaid raid = CampaignRaidService.getActive(rig.war);
      Instant start = raid.getMusterEndsAt();
      CampaignRaidLaunchService.startFight(rig.war, start);
      assertEquals(CampaignRaidState.FIGHTING, raid.getState());
      Instant after = start.plusSeconds(1);
      CampaignRaidService.endRaid(rig.war, after);
      assertNull(CampaignRaidService.getActive(rig.war));
      Faction allied = rig.domain.saved("repair_allied", "Allied");
      rig.war.getDefenders().getMainParticipants().add(new Participant(allied));
      Installation independent = rig.install(allied, rig.target.getId(), rig.target.getKind(), 444);
      assertTrue(VehicleInstallationLockService.isInstallationLocked(rig.target, after));
      assertFalse(
          VehicleInstallationLockService.isInstallationLocked(independent, after),
          "A later same-named allied installation did not participate in the raid");
      assertSame(independent, allied.getInstallationHandler().getById(independent.getId()));
      Installation existing =
          rig.install(rig.attacker, rig.target.getId(), rig.target.getKind(), 555);
      assertFalse(VehicleInstallationLockService.isInstallationLocked(existing, after));
      InstallationTransferService.transfer(rig.defender, rig.attacker, rig.target.getProvince());
      Installation occupied =
          rig.attacker
              .getInstallationHandler()
              .getByProvince(rig.target.getKind(), rig.target.getProvince());
      assertNotNull(occupied);
      assertNotEquals(rig.target.getId(), occupied.getId());
      assertSame(existing, rig.attacker.getInstallationHandler().getById(existing.getId()));
      assertTrue(
          VehicleInstallationLockService.isInstallationLocked(occupied, after),
          "Occupation does not remove the physical installation's repair embargo");
      assertTrue(rig.attacker.getInstallationHandler().deconstruct(occupied.getId()).isSuccess());
      Installation rebuilt =
          new Installation(
              occupied.getId(),
              occupied.getName(),
              occupied.getKind(),
              occupied.getProvince(),
              occupied.getCenterX(),
              occupied.getCenterZ(),
              System.currentTimeMillis());
      rig.attacker.getInstallationHandler().acceptTransferred(rebuilt);
      assertFalse(
          VehicleInstallationLockService.isInstallationLocked(rebuilt, after),
          "A newly built installation does not inherit a destroyed installation's embargo");
    }
  }
}
