package net.tfminecraft.simplefactions.war.battle.engine.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.rules.BattleItemDurability;
import net.tfminecraft.simplefactions.war.battle.engine.rules.BattleProvinceBlockProtectionService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleExecutionBoundaryCoverageTest {
  private Fixture rig;
  private ProvinceGrid grid;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(Cache.class, "mapEnabled");
    rig.remember(Cache.class, "battleProvinceBlockProtectionEnabled");
    rig.remember(Cache.class, "battleItemDurabilityMultiplier");
    Cache.mapEnabled = true;
    Cache.battleProvinceBlockProtectionEnabled = true;
    grid = mock(ProvinceGrid.class);
    when(rig.domain.ui.plugin.getProvinceGrid()).thenReturn(grid);
    when(grid.getAt(anyInt(), anyInt())).thenReturn(11);
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void aForeignWorldSpawnCannotInventAMapProvinceForAManualBattle() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "other_world_spawn");
    World foreign = mock(World.class);
    when(foreign.getName()).thenReturn("minigames");
    battle.getSideById("defender").setSpawn(new Location(foreign, 100, 64, 100));

    BattleBoundsService.resolveAllowedProvinces(battle);

    assertNull(battle.getProvinceId());
    assertTrue(battle.getAllowedProvinceIds().isEmpty());
  }

  @Test
  void anActiveMapBattleDoesNotBlockBuildingAtTheSameCoordinatesInAnotherWorld() {
    Battle battle = manualBattle(true);
    World foreign = mock(World.class);
    when(foreign.getName()).thenReturn("minigames");
    assertTrue(
        BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(
            new Location(rig.domain.ui.world, 100, 64, 100)));
    assertFalse(
        BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(
            new Location(foreign, 100, 64, 100)));
    assertTrue(battle.hasStarted());
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 5})
  void probabilisticSubUnitWearCostsOneDurabilityWhenTheRollSucceeds(int originalDamage) {
    assertEquals(1, BattleItemDurability.apply(originalDamage, 0.1, () -> 0.0));
    assertEquals(0, BattleItemDurability.apply(originalDamage, 0.1, () -> 0.9));
  }

  private Battle manualBattle(boolean start) {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "manual_execution");
    battle.setProvinceId(11);
    Warband attackers = new Warband("manual_attackers", rig.alice);
    Warband defenders = new Warband("manual_defenders", rig.bob);
    WarbandManager.addWarband(attackers);
    WarbandManager.addWarband(defenders);
    battle.getSideById("attacker").addBand(attackers);
    battle.getSideById("defender").addBand(defenders);
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 100, 65, 100));
      side.setJail(new Location(rig.domain.ui.world, 200, 65, 200));
    }
    battle.addPoint(
        new CapturePoint(
            "manual_point",
            new Location(rig.domain.ui.world, 120, 65, 120),
            battle.getSideById("attacker"),
            100));
    BattleManager.addBattle(battle);
    if (start) {
      assertNull(battle.start());
      assertTrue(battle.hasStarted());
    }
    return battle;
  }

  @Test
  void publicSetupRejectsMissingArgumentsWithoutChangingTheExistingLayout() {
    Battle battle = manualBattle(false);
    BattleSide side = battle.getSideById("attacker");
    Location initialSpawn = side.getSpawn();
    Location initialJail = side.getJail();
    int initialLives = side.getLives();
    Location location = new Location(rig.domain.ui.world, 5, 64, 5);
    assertThrows(
        IllegalArgumentException.class,
        () -> BattleSideSetupService.setSpawn(battle, null, location));
    assertThrows(
        IllegalArgumentException.class, () -> BattleSideSetupService.setSpawn(battle, side, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> BattleSideSetupService.setJail(battle, null, location));
    assertThrows(
        IllegalArgumentException.class, () -> BattleSideSetupService.setJail(battle, side, null));
    assertThrows(
        IllegalArgumentException.class, () -> BattleSideSetupService.setSideLives(battle, null, 1));
    assertThrows(
        IllegalArgumentException.class, () -> BattleSideSetupService.setSideLives(battle, side, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> BattleSideSetupService.addCapturePoint(null, side, location));
    assertThrows(
        IllegalArgumentException.class,
        () -> BattleSideSetupService.addCapturePoint(battle, null, location));
    assertThrows(
        IllegalArgumentException.class,
        () -> BattleSideSetupService.addCapturePoint(battle, side, null));
    battle.setCapturePointsEnabled(false);
    assertThrows(
        IllegalStateException.class,
        () -> BattleSideSetupService.addCapturePoint(battle, side, location));
    battle.setWarId(rig.war.getId());
    assertThrows(
        IllegalStateException.class, () -> BattleSideSetupService.setSideLives(battle, side, 7));
    assertSame(initialSpawn, side.getSpawn());
    assertSame(initialJail, side.getJail());
    assertEquals(initialLives, side.getLives());
    assertEquals(1, battle.getPoints().size());
  }

  @Test
  void settingBothSpawnsReordersCapturePointsAlongTheActualBattleAxis() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "setup_axis");
    battle.setSequentialCapture(true);
    BattleSide attacker = battle.getSideById("attacker");
    BattleSide defender = battle.getSideById("defender");
    CapturePoint east =
        BattleSideSetupService.addCapturePoint(
            battle, attacker, new Location(rig.domain.ui.world, 90, 64, 0));
    CapturePoint west =
        BattleSideSetupService.addCapturePoint(
            battle, defender, new Location(rig.domain.ui.world, 10, 64, 0));
    BattleSideSetupService.setSpawn(
        battle, attacker, new Location(rig.domain.ui.world, 100, 64, 0));
    BattleSideSetupService.setSpawn(battle, defender, new Location(rig.domain.ui.world, 0, 64, 0));
    BattleSideSetupService.setJail(battle, defender, new Location(rig.domain.ui.world, -10, 64, 0));
    BattleSideSetupService.setSideLives(battle, defender, 9);
    assertEquals("A", west.getId());
    assertEquals("B", east.getId());
    assertEquals(0, west.getSequenceIndex());
    assertEquals(1, east.getSequenceIndex());
    assertEquals(9, defender.getLives());
    assertEquals(-10, defender.getJail().getBlockX());
    assertSame(west, battle.getPointById("a"));
    assertNull(battle.getPointById("missing"));
    assertFalse(battle.removePointById("missing"));
    assertFalse(battle.removePointById(null));
    battle.removePoint(null);
    battle.removePoint(west);
    assertEquals(java.util.List.of(east), battle.getPoints());
    battle.setPoints(new java.util.ArrayList<>(java.util.List.of(west)));
    assertSame(west, battle.getPoints().getFirst());
  }

  @Test
  void malformedSavedCasualtyEntriesAreIgnoredAndRestoringReplacesPriorCounts() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "casualty_snapshot");
    battle.setRecordedSideCasualty(null, 4);
    battle.setRecordedSideCasualty("ATTACKER", 7);
    battle.setRecordedSideCasualty("attacker", 0);
    assertTrue(battle.getRecordedSideCasualties().isEmpty());
    java.util.Map<String, Integer> saved = new java.util.HashMap<>();
    saved.put(null, 5);
    saved.put("invalid_null", null);
    saved.put("invalid_zero", 0);
    saved.put("invalid_negative", -2);
    saved.put("DEFENDER", 3);
    battle.restoreSideCasualties(saved);
    assertEquals(java.util.Map.of("defender", 3), battle.getRecordedSideCasualties());
    saved.put("DEFENDER", 99);
    assertEquals(3, battle.getRecordedSideCasualties().get("defender"));
    battle.restoreSideCasualties(null);
    assertTrue(battle.getRecordedSideCasualties().isEmpty());
  }

  @Test
  void provinceInferenceUsesAttackerSpawnWhenTheDefenderHasNoneAndClearsObsoleteBounds() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "fallback_spawn");
    battle.setAllowedProvinceIds(java.util.Set.of(99));
    assertFalse(BattleBoundsService.applies(battle));
    assertFalse(BattleBoundsService.applies(null));
    BattleBoundsService.resolveAllowedProvinces(null);
    battle.removeSide("DEFENDER");
    battle.getSideById("attacker").setSpawn(new Location(rig.domain.ui.world, 5, 64, 6));
    BattleBoundsService.resolveAllowedProvinces(battle);
    assertEquals(11, battle.getProvinceId());
    assertTrue(battle.getAllowedProvinceIds().isEmpty());
    assertTrue(BattleBoundsService.isInBounds(battle, rig.alice));
    assertTrue(BattleBoundsService.isProvinceAllowed(battle, 999));
    assertFalse(BattlePlacementValidator.canResolveBounds(battle));
    assertTrue(BattlePlacementValidator.resolveAllowedProvinces(battle).isEmpty());
    assertTrue(
        BattlePlacementValidator.resolveAllowedProvinces(battle, rig.domain.ui.plugin).isEmpty());
    assertTrue(
        BattlePlacementValidator.isLocationAllowed(
            battle, new Location(rig.domain.ui.world, -500, 64, -500)));
  }

  @Test
  void absentMapServicesOrSpawnLeaveTheProvinceUnresolved() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "missing_map");
    BattleBoundsService.resolveAllowedProvinces(battle);
    assertNull(battle.getProvinceId());
    battle.getSideById("attacker").setSpawn(new Location(rig.domain.ui.world, 5, 64, 5));
    when(grid.getAt(anyInt(), anyInt())).thenReturn(-1);
    BattleBoundsService.resolveAllowedProvinces(battle);
    assertNull(battle.getProvinceId());
    when(rig.domain.ui.plugin.getProvinceGrid()).thenReturn(null);
    BattleBoundsService.resolveAllowedProvinces(battle);
    assertNull(battle.getProvinceId());
    assertEquals(
        -1, BattlePlacementValidator.provinceAt(battle.getSideById("attacker").getSpawn()));
    net.tfminecraft.simplefactions.SimpleFactions plugin =
        net.tfminecraft.simplefactions.SimpleFactions.plugin;
    try {
      net.tfminecraft.simplefactions.SimpleFactions.plugin = null;
      BattleBoundsService.resolveAllowedProvinces(battle);
      assertNull(battle.getProvinceId());
      assertEquals(
          -1, BattlePlacementValidator.provinceAt(battle.getSideById("attacker").getSpawn()));
    } finally {
      net.tfminecraft.simplefactions.SimpleFactions.plugin = plugin;
    }
    assertTrue(BattlePlacementValidator.validate(null).isEmpty());
  }

  @Test
  void templateArgumentFailuresPreserveLayoutAndAnUntypedManualBattleCanResetSafely() {
    Battle battle = manualBattle(false);
    BattleSide original = battle.getSideById("attacker");
    assertThrows(IllegalArgumentException.class, () -> BattleFactory.createBlank(null, "invalid"));
    assertThrows(
        IllegalArgumentException.class, () -> BattleFactory.createBlank(BattleType.FIELD, " "));
    assertThrows(
        IllegalArgumentException.class, () -> BattleFactory.applyTemplate(null, "missing"));
    assertThrows(IllegalArgumentException.class, () -> BattleFactory.applyTemplate(battle, " "));
    Battle untyped = new Battle("untyped_manual");
    assertThrows(
        IllegalStateException.class, () -> BattleFactory.applyTemplate(untyped, "missing"));
    BattleFactory.applyCampaignDefault(null);
    BattleFactory.applyCampaignDefault(untyped);
    BattleFactory.resetToBase(untyped);
    assertNull(untyped.getBattleType());
    assertEquals(2, untyped.getSides().size());
    assertFalse(untyped.isCapturePointsEnabled());
    assertSame(original, battle.getSideById("attacker"));
    assertEquals(1, battle.getPoints().size());
  }

  @Test
  void manualJoinDenialsPreserveRostersAndASuccessEnrolsOnlyTheChosenSide() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "manual_join");
    org.bukkit.entity.Player leader = rig.player("Charlie");
    Warband band = new Warband("charlie_band", leader);
    WarbandManager.addWarband(band);
    BattleManager.addBattle(battle);
    assertEquals(
        "Only players can join battles",
        BattleJoinService.join((org.bukkit.entity.Player) null, battle, "attacker"));
    assertEquals("Battle not found", BattleJoinService.join(band, null, "attacker"));
    assertEquals(
        "Side is required (attacker or defender)", BattleJoinService.join(band, battle, " "));
    assertEquals(
        "You need to lead a warband to join a battle",
        BattleJoinService.join((Warband) null, battle, "attacker"));
    assertEquals("Battle is locked", BattleJoinService.join(leader, battle, "attacker"));
    battle.setLocked(false);
    assertEquals("No side with id observers", BattleJoinService.join(band, battle, "observers"));
    assertTrue(battle.getSides().stream().allMatch(s -> s.getBands().isEmpty()));
    assertNull(BattleJoinService.join(leader, battle, "attacker"));
    assertSame(band, battle.getSideById("attacker").getBands().getFirst());
    assertTrue(battle.getSideById("defender").getBands().isEmpty());
    assertEquals(
        "Already signed up for a battle", BattleJoinService.join(band, battle, "defender"));
    assertEquals(1, battle.getSideById("attacker").getBands().size());
  }

  @Test
  void pendingShellDuplicatesAndMissingCampaignsAreRejectedWithoutAppendingBands() {
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "pending_manual");
    manual.setLocked(false);
    Warband pending =
        Warband.createCampaignSideShell(
            "pending_shell", rig.war, rig.war.getAttackers(), "attacker");
    assertNull(BattleJoinService.join(pending, manual, "attacker"));
    assertEquals(
        "Already signed up for this battle", BattleJoinService.join(pending, manual, "attacker"));
    assertEquals(1, manual.getSideById("attacker").getBands().size());
    Battle campaign = BattleFactory.createBlank(BattleType.FIELD, "orphan_join");
    campaign.setLocked(false);
    campaign.setWarId(991923);
    assertEquals("War not found", BattleJoinService.join(pending, campaign, "attacker"));
    campaign.setWarId(rig.war.getId());
    assertEquals(
        "Warband is not on this battle side",
        BattleJoinService.join(pending, campaign, "defender"));
    rig.war.end(net.tfminecraft.simplefactions.war.enums.WarEndReason.ADMIN_END);
    assertEquals("War not found", BattleJoinService.join(pending, campaign, "attacker"));
    assertTrue(campaign.getSides().stream().allMatch(s -> s.getBands().isEmpty()));
  }

  @Test
  void blockEventsRespectBothStaffPermissionsAndTheGlobalToggle() {
    manualBattle(true);
    org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
    when(block.getLocation()).thenReturn(new Location(rig.domain.ui.world, 100, 65, 100));
    var listener = new BattleProvinceBlockProtectionService.Listener();
    var denied = new org.bukkit.event.block.BlockBreakEvent(block, rig.alice);
    listener.onBlockBreak(denied);
    assertTrue(denied.isCancelled());
    verify(rig.alice).sendMessage(BattleProvinceBlockProtectionService.BLOCKED);
    for (String permission : java.util.List.of("simplefactions.admin", "warbands.admin")) {
      when(rig.alice.hasPermission(anyString()))
          .thenAnswer(call -> permission.equals(call.getArgument(0)));
      var allowed = new org.bukkit.event.block.BlockBreakEvent(block, rig.alice);
      listener.onBlockBreak(allowed);
      assertFalse(allowed.isCancelled());
    }
    when(rig.alice.hasPermission(anyString())).thenReturn(false);
    var placement =
        new org.bukkit.event.block.BlockPlaceEvent(
            block,
            mock(org.bukkit.block.BlockState.class),
            block,
            new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE),
            rig.alice,
            true,
            org.bukkit.inventory.EquipmentSlot.HAND);
    listener.onBlockPlace(placement);
    assertTrue(placement.isCancelled());
    Cache.battleProvinceBlockProtectionEnabled = false;
    var disabled = new org.bukkit.event.block.BlockBreakEvent(block, rig.alice);
    listener.onBlockBreak(disabled);
    assertFalse(disabled.isCancelled());
  }

  @Test
  void durabilityEventsScaleOnlyParticipantsInStartedBattles() {
    var listener = new BattleItemDurability.Listener();
    org.bukkit.inventory.ItemStack sword =
        new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD);
    var outside = new org.bukkit.event.player.PlayerItemDamageEvent(rig.alice, sword, 10);
    listener.onItemDamage(outside);
    assertEquals(10, outside.getDamage());
    Battle battle = manualBattle(false);
    Cache.battleItemDurabilityMultiplier = 0.2;
    var pending = new org.bukkit.event.player.PlayerItemDamageEvent(rig.alice, sword, 10);
    listener.onItemDamage(pending);
    assertEquals(10, pending.getDamage());
    assertNull(battle.start());
    var reduced = new org.bukkit.event.player.PlayerItemDamageEvent(rig.alice, sword, 10);
    listener.onItemDamage(reduced);
    assertEquals(2, reduced.getDamage());
    assertFalse(reduced.isCancelled());
    Cache.battleItemDurabilityMultiplier = 0;
    var free = new org.bukkit.event.player.PlayerItemDamageEvent(rig.alice, sword, 10);
    listener.onItemDamage(free);
    assertTrue(free.isCancelled());
    assertEquals(0, BattleItemDurability.apply(0, 0.5, null));
    assertEquals(0, BattleItemDurability.apply(1, 0.5, null));
  }

  @Test
  void fieldTickPreservesCaptureOwnershipWhenNoPlayersContestEitherPoint() {
    Battle battle = manualBattle(false);
    battle.setSequentialCapture(true);
    battle.addPoint(
        new CapturePoint(
            "defender_point",
            new Location(rig.domain.ui.world, 140, 65, 140),
            battle.getSideById("defender"),
            100));
    when(rig.domain.ui.world.getNearbyEntities(
            any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(java.util.List.of());
    try (org.mockito.MockedStatic<net.tfminecraft.vehicleframework.VehicleFramework> framework =
        mockStatic(net.tfminecraft.vehicleframework.VehicleFramework.class)) {
      net.tfminecraft.vehicleframework.managers.VehicleManager vehicles =
          mock(net.tfminecraft.vehicleframework.managers.VehicleManager.class);
      framework
          .when(net.tfminecraft.vehicleframework.VehicleFramework::getVehicleManager)
          .thenReturn(vehicles);
      when(vehicles.get(any(org.bukkit.entity.Player.class)))
          .thenReturn(mock(net.tfminecraft.vehicleframework.vehicles.ActiveVehicle.class));
      assertNull(battle.start());
      battle.tick();
      assertTrue(battle.hasStarted());
      assertEquals(
          java.util.List.of(100, 100),
          battle.getPointManager().getPoints().stream()
              .map(CapturePoint::getCaptureProgress)
              .toList());
      assertSame(
          battle.getSideById("defender"),
          battle.getPointManager().getPoints().get(1).getController());
      assertEquals(2, battle.getAllParticipants().size());
      battle.end();
    }
  }

  @Test
  void siegeTickCannotCountUncontestedTimeUntilEnoughAttackersHoldTheArea() {
    Battle battle = manualBattle(false);
    battle.setBattleType(BattleType.SIEGE);
    battle.setCapturePointsEnabled(false);
    battle.clearPoints();
    BattleContestSetup.setContestMin(battle, new Location(rig.domain.ui.world, 100, 60, 100));
    BattleContestSetup.setContestMax(battle, new Location(rig.domain.ui.world, 150, 90, 150));
    battle.setContestDurationSeconds(60);
    try (org.mockito.MockedStatic<net.tfminecraft.vehicleframework.VehicleFramework> framework =
        mockStatic(net.tfminecraft.vehicleframework.VehicleFramework.class)) {
      framework
          .when(net.tfminecraft.vehicleframework.VehicleFramework::getVehicleManager)
          .thenReturn(mock(net.tfminecraft.vehicleframework.managers.VehicleManager.class));
      assertNull(battle.start());
      for (int tick = 0; tick < 5; tick++) battle.tick();
      assertTrue(battle.hasStarted());
      assertEquals(60, battle.getContestHoldRemainingSeconds());
      assertEquals(25, battle.getSideById("attacker").getLives());
      battle.end();
      battle.tick();
      assertFalse(battle.hasStarted());
    }
  }

  @Test
  void pendingCampaignPlacementReportsEachMissingSpawnJailAndSiegeArea() {
    Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "missing_placements");
    battle.setWarId(rig.war.getId());
    assertEquals(
        java.util.List.of(
            "Attacker spawn is not set.",
            "Attacker jail is not set.",
            "Defender spawn is not set.",
            "Defender jail is not set.",
            "Siege contest area is not configured (setcontestmin / setcontestmax)."),
        BattlePlacementValidator.validate(battle));
    assertEquals("Attacker spawn is not set.", battle.start());
    assertFalse(battle.hasStarted());
    for (BattleSide side : battle.getSides()) {
      BattleSideSetupService.setSpawn(battle, side, new Location(rig.domain.ui.world, 0, 64, 0));
      BattleSideSetupService.setJail(battle, side, new Location(rig.domain.ui.world, 5, 64, 5));
    }
    BattleContestSetup.setContestMin(battle, new Location(rig.domain.ui.world, 0, 60, 0));
    BattleContestSetup.setContestMax(battle, new Location(rig.domain.ui.world, 5, 80, 5));
    assertTrue(BattlePlacementValidator.validate(battle).isEmpty());
  }

  @Test
  void missingProvinceServicesAndDisabledMapsNeverBlockPlayerBuilding() {
    Location place = new Location(rig.domain.ui.world, 100, 64, 100);
    Battle battle = manualBattle(true);
    Cache.mapEnabled = false;
    assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(place));
    Cache.mapEnabled = true;
    when(rig.domain.ui.plugin.getProvinceGrid()).thenReturn(null);
    assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(place));
    net.tfminecraft.simplefactions.SimpleFactions before =
        net.tfminecraft.simplefactions.SimpleFactions.plugin;
    try {
      net.tfminecraft.simplefactions.SimpleFactions.plugin = null;
      assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(place));
    } finally {
      net.tfminecraft.simplefactions.SimpleFactions.plugin = before;
    }
    assertFalse(BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(null));
    assertTrue(battle.hasStarted());
    assertEquals(11, battle.getProvinceId());
  }

  @Test
  void malformedSavedSideLabelsStayReadableWhileNullSideRecordsAreIgnored() {
    var saved =
        net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper.toData(
            BattleFactory.createBlank(BattleType.FIELD, "legacy_side_labels"));
    saved.warId = rig.war.getId();
    saved.sides.getFirst().id = "";
    saved.sides.add(null);
    Battle restored =
        net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper.fromData(saved);
    assertNotNull(restored);
    assertEquals(2, restored.getSides().size());
    restored.getSides().add(null);
    assertEquals(
        java.util.List.of(
            "Side spawn is not set.",
            "Side jail is not set.",
            "Defender spawn is not set.",
            "Defender jail is not set."),
        BattlePlacementValidator.validate(restored));
    assertFalse(restored.hasStarted());
  }

  @Test
  void provinceLookupUsesTheMapWorldAndNeverQueriesTheGridForAnotherWorld() {
    World foreign = mock(World.class);
    when(foreign.getName()).thenReturn("lobby");
    clearInvocations(grid);
    assertEquals(
        11, BattlePlacementValidator.provinceAt(new Location(rig.domain.ui.world, 8, 64, 9)));
    assertEquals(-1, BattlePlacementValidator.provinceAt(new Location(foreign, 8, 64, 9)));
    assertEquals(-1, BattlePlacementValidator.provinceAt(new Location(null, 8, 64, 9)));
    verify(grid).getAt(8, 9);
    verifyNoMoreInteractions(grid);
  }

  @Test
  void raidTemplateCarriesItsConfiguredFiniteDefenderLivesIntoTheBattle() {
    org.bukkit.configuration.file.YamlConfiguration yaml =
        new org.bukkit.configuration.file.YamlConfiguration();
    yaml.set("type", "raid");
    yaml.set("defender_respawn_mode", "lives");
    yaml.set("defender_lives", 7);
    net.tfminecraft.simplefactions.loaders.BattleTemplateLoader.putForTests(
        new net.tfminecraft.simplefactions.war.battle.template.BattleTemplate("finite_raid", yaml));
    Battle battle = BattleFactory.createBlank(BattleType.RAID, "finite_raid_battle");
    BattleFactory.applyTemplate(battle, "finite_raid");
    assertEquals(7, battle.getDefenderLives());
    assertEquals(
        net.tfminecraft.simplefactions.war.battle.enums.DefenderRespawnMode.LIVES,
        battle.getDefenderRespawnMode());
    assertEquals(2, battle.getSides().size());
    assertFalse(battle.isCampaignRaid());
    assertTrue(battle.getPoints().isEmpty());
  }

  @Test
  void aRealCampaignRaidBattleTickUpdatesRaidBarsWithoutGivingItOrdinarySideBars() {
    assertEquals(
        net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.LaunchResult.STARTED,
        net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    var raid =
        net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService.getActive(rig.war);
    net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService.signupAttacker(
        rig.war, raid, rig.alice.getUniqueId(), rig.alice.getName());
    rig.time(raid.getMusterEndsAt());
    net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidLaunchService.startFight(
        rig.war, raid.getMusterEndsAt());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertNotNull(battle);
    assertTrue(battle.hasStarted());
    net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidBossBarService.clear(battle);
    rig.bars.clear();
    rig.time(raid.getFightEndsAt().minusSeconds(300));
    battle.tick();
    assertTrue(battle.hasStarted());
    assertTrue(battle.isCampaignRaid());
    assertEquals(2, rig.bars.size());
    assertTrue(rig.bars.stream().allMatch(org.bukkit.boss.BossBar::isVisible));
    assertTrue(rig.bars.stream().anyMatch(bar -> "Raiders remaining: 1".equals(bar.getTitle())));
    assertTrue(rig.bars.stream().allMatch(bar -> bar.getPlayers().contains(rig.alice)));
  }
}
