package net.tfminecraft.simplefactions.war.battle.engine.capture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.raid.BattleRaidSetup;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.DefenderRespawnMode;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.CapturePointDefinition;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleCaptureBoundaryCoverageTest {
  private Fixture rig;
  private Battle battle;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(Cache.class, "battleCaptureMinPlayers");
    Cache.battleCaptureMinPlayers = 1;
    battle = BattleFactory.createBlank(BattleType.FIELD, "capture_boundary");
    BattleManager.addBattle(battle);
    battle.getSideById("attacker").addBand(new Warband("capture_attackers", rig.alice));
    battle.getSideById("defender").addBand(new Warband("capture_defenders", rig.bob));
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void aMajorityCapturesAndReinforcesThePointWithoutCountingOtherEntities() {
    var charlie = rig.player("Charlie");
    BattleSide attacker = battle.getSideById("attacker");
    BattleSide defender = battle.getSideById("defender");
    attacker.getBands().getFirst().addPlayer(charlie);
    CapturePoint point = point("bridge", 0, defender, 0, 0);
    when(rig.domain.ui.world.getNearbyEntities(
            any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(rig.alice, charlie, rig.bob, mock(Entity.class)));
    point.updateNearbyEntities();
    assertEquals(4, point.getNearbyEntities().size());
    point.updateSides(battle.getSides());
    assertSame(attacker, point.getController());
    assertEquals(0, point.getCaptureProgress());
    for (int tick = 0; tick < 101; tick++) point.updateSides(battle.getSides());
    assertEquals(100, point.getCaptureProgress());
    assertTrue(point.isFullyControlledBy("ATTACKER"));
    assertFalse(point.isFullyControlledBy(null));
    assertFalse(point.isFullyControlledBy("defender"));
    assertEquals(3, battle.getAllParticipants().size());
  }

  @Test
  void tiedTeamsAndEmptyPointsPreserveProgressWhileReturningOwnersCanReinforce() {
    CapturePoint point = point("hill", 0, battle.getSideById("defender"), 40, 0);
    when(rig.domain.ui.world.getNearbyEntities(
            any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(rig.alice, rig.bob));
    point.updateNearbyEntities();
    point.updateSides(battle.getSides());
    assertTrue(point.isContested());
    assertEquals(40, point.getCaptureProgress());
    when(rig.domain.ui.world.getNearbyEntities(
            any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    point.updateNearbyEntities();
    point.updateSides(battle.getSides());
    assertFalse(point.isContested());
    assertEquals(40, point.getCaptureProgress());
    when(rig.domain.ui.world.getNearbyEntities(
            any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(rig.bob));
    point.updateNearbyEntities();
    point.updateSides(battle.getSides());
    assertEquals(41, point.getCaptureProgress());
    assertTrue(point.isContested());
    point.setCaptureProgress(500);
    assertEquals(100, point.getCaptureProgress());
    point.setCaptureProgress(-5);
    assertEquals(0, point.getCaptureProgress());
  }

  @Test
  void publicFrontlineModesRespectLaneOwnershipAndIncompleteFronts() {
    BattleSide attacker = battle.getSideById("attacker"), defender = battle.getSideById("defender");
    CapturePoint first = point("first", 0, defender, 100, 0);
    first.setAdvanceSideId("attacker");
    CapturePoint second = point("second", 10, defender, 100, 1);
    second.setAdvanceSideId("ATTACKER");
    CapturePoint alternate = point("alternate", 20, attacker, 100, 2);
    alternate.setAdvanceSideId("defender");
    CapturePoint neutralLane = point("free", 30, defender, 100, 3);
    List<CapturePoint> chain = List.of(first, second, alternate, neutralLane);
    assertTrue(CapturePoint.isFrontPoint(first, chain));
    assertFalse(CapturePoint.isFrontPoint(second, chain));
    assertTrue(CapturePoint.isFrontPoint(neutralLane, chain));
    first.setController(attacker);
    assertFalse(CapturePoint.isFrontPoint(first, chain));
    assertTrue(CapturePoint.isFrontPoint(second, chain));
    second.setController(attacker);
    assertTrue(CapturePoint.isFrontPoint(first, chain));
    assertTrue(CapturePoint.isFrontPoint(second, chain));
    assertTrue(CapturePoint.isFrontPoint(first, chain, (Battle) null));
    assertTrue(CapturePoint.isFrontPoint(first, chain, battle));
    first.setController(defender);
    second.setController(defender);
    neutralLane.setController(attacker);
    battle.setSequentialCapture(true);
    assertFalse(CapturePoint.isFrontPoint(first, chain, battle));
    assertTrue(CapturePoint.isFrontPoint(null, chain));
    assertTrue(CapturePoint.isFrontPoint(null, chain, true));
    assertTrue(CapturePoint.isFrontPoint(first, List.of(), battle));
    assertTrue(CapturePoint.isFrontPoint(first, List.of(), true));
  }

  @Test
  void coincidentSpawnsOrderTheLinearChainByDistanceWithoutMovingLocations() {
    Location anchor = new Location(rig.domain.ui.world, 0, 64, 0);
    battle.getSideById("defender").setSpawn(anchor);
    battle.getSideById("attacker").setSpawn(anchor.clone());
    CapturePoint far = point("far", 40, battle.getSideById("attacker"), 100, 7);
    CapturePoint near = point("near", 3, battle.getSideById("defender"), 100, 9);
    CapturePoint middle = point("middle", 20, battle.getSideById("attacker"), 100, 8);
    battle.addPoint(far);
    battle.addPoint(near);
    battle.addPoint(middle);
    BattleCapturePoints.syncLinearChain(battle);
    assertEquals(List.of("A", "B", "C"), List.of(near.getId(), middle.getId(), far.getId()));
    assertEquals(
        List.of(0, 1, 2),
        List.of(near.getSequenceIndex(), middle.getSequenceIndex(), far.getSequenceIndex()));
    assertEquals(40, far.getLoc().getX());
    assertEquals(3, near.getLoc().getX());
    assertSame(battle.getSideById("defender"), near.getController());
  }

  @Test
  void removingAPointRenumbersOnlyRemainingPointsAndRejectsStaleSelections() {
    CapturePoint first = point("old_first", 0, battle.getSideById("defender"), 100, 10);
    CapturePoint second = point("old_second", 10, battle.getSideById("attacker"), 70, 20);
    battle.addPoint(second);
    battle.addPoint(first);
    BattleCapturePoints.afterPointListChanged(battle);
    assertEquals("A", first.getId());
    assertEquals("B", second.getId());
    assertFalse(BattleCapturePoints.removePoint(null, first));
    assertFalse(BattleCapturePoints.removePoint(battle, null));
    assertFalse(
        BattleCapturePoints.removePoint(
            battle, point("unknown", 99, battle.getSideById("attacker"), 100, 30)));
    assertTrue(BattleCapturePoints.removePoint(battle, first));
    assertEquals(List.of(second), battle.getPoints());
    assertEquals("A", second.getId());
    assertEquals(70, second.getCaptureProgress());
    assertEquals(0, second.getSequenceIndex());
    BattleCapturePoints.afterPointListChanged(null);
    BattleCapturePoints.syncLinearChain(null);
    BattleCapturePoints.compressGlobalLetters(null);
    assertNull(BattleCapturePoints.resolveDefenderSpawn(null));
    assertEquals(List.of(second), battle.getPoints());
    assertEquals("A", BattleCapturePoints.letterForIndex(-1));
    assertEquals("Z", BattleCapturePoints.letterForIndex(25));
    assertEquals("AA", BattleCapturePoints.letterForIndex(26));
    assertEquals("ZZ", BattleCapturePoints.letterForIndex(701));
    assertEquals("AAA", BattleCapturePoints.letterForIndex(702));
  }

  @Test
  void aRaidTargetEditRetainsItsIdAndRoundTripsTheExactLocation() {
    Battle raid = BattleFactory.createBlank(BattleType.RAID, "target_edit");
    BattleManager.addBattle(raid);
    Location first = new Location(rig.domain.ui.world, 12, 65, 14, 30, 10);
    raid.setRaidTarget(
        new CapturePointDefinition("harbor_gate", BattleLocation.fromBukkitLocation(first)));
    Location moved = new Location(rig.domain.ui.world, 23, 70, 25, 60, 5);
    BattleRaidSetup.setRaidTarget(raid, moved);
    assertEquals("harbor_gate", raid.getRaidTarget().getId());
    assertEquals(moved, raid.getRaidTarget().getLocation().toBukkitLocation());
    BattleRaidSetup.setRaidTarget(raid, null);
    BattleRaidSetup.setRaidTarget(null, moved);
    assertEquals(moved, raid.getRaidTarget().getLocation().toBukkitLocation());
    raid.setRaidTarget(new CapturePointDefinition(" ", BattleLocation.fromBukkitLocation(first)));
    BattleRaidSetup.setRaidTarget(raid, moved);
    assertEquals("target", raid.getRaidTarget().getId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing_location", "unloaded_world", "missing_defender"})
  void incompleteRaidTargetsDoNotCreateAnInvalidRuntimeCapturePoint(String incomplete) {
    Battle raid = BattleFactory.createBlank(BattleType.RAID, "incomplete_raid");
    BattleManager.addBattle(raid);
    BattleLocation location = new BattleLocation("world", 5, 64, 5, 0, 0);
    if (incomplete.equals("missing_location")) location = null;
    if (incomplete.equals("unloaded_world")) when(Bukkit.getWorld("world")).thenReturn(null);
    if (incomplete.equals("missing_defender")) raid.getSides().remove(raid.getSideById("defender"));
    raid.setRaidTarget(new CapturePointDefinition("target", location));
    BattleRaidSetup.onStart(raid);
    assertTrue(raid.getPoints().isEmpty());
    assertEquals(0, raid.getSideById("attacker").getLives());
    assertFalse(raid.hasStarted());
  }

  @Test
  void raidDefaultsHaveAnExplicitLifeFallbackAndNeverChangeOrdinaryFieldPoints() throws Exception {
    rig.remember(Cache.class, "battleRaidDefenderRespawnModeDefault");
    Cache.battleRaidDefenderRespawnModeDefault = null;
    Battle raid = BattleFactory.createBlank(BattleType.RAID, "raid_fallbacks");
    BattleManager.addBattle(raid);
    raid.setDefenderRespawnMode(null);
    raid.setDefenderLives(-1);
    raid.setLives(0);
    assertEquals(25, BattleRaidSetup.getEffectiveDefenderLives(raid));
    raid.setLives(7);
    assertEquals(7, BattleRaidSetup.getEffectiveDefenderLives(raid));
    raid.setLives(0);
    assertEquals(
        DefenderRespawnMode.INFINITE, BattleRaidSetup.getEffectiveDefenderRespawnMode(raid));
    Cache.battleRaidDefenderRespawnModeDefault = DefenderRespawnMode.LIVES;
    assertEquals(DefenderRespawnMode.LIVES, BattleRaidSetup.getEffectiveDefenderRespawnMode(raid));
    raid.setRaidTarget(
        new CapturePointDefinition(" ", new BattleLocation("world", 5, 64, 5, 0, 0)));
    BattleRaidSetup.onStart(raid);
    assertEquals(25, raid.getSideById("defender").getLives());
    assertEquals("target", raid.getPoints().getFirst().getId());
    CapturePoint fieldPoint = point("field", 0, battle.getSideById("attacker"), 60, 0);
    battle.addPoint(fieldPoint);
    BattleRaidSetup.onStart(null);
    BattleRaidSetup.onStart(battle);
    assertEquals(List.of(fieldPoint), battle.getPoints());
    assertEquals(60, fieldPoint.getCaptureProgress());
  }

  private CapturePoint point(String id, double x, BattleSide controller, int progress, int index) {
    CapturePoint point =
        new CapturePoint(id, new Location(rig.domain.ui.world, x, 64, 0), controller, progress);
    point.setSequenceIndex(index);
    return point;
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "x,NaN",
    "x,Infinity",
    "x,-Infinity",
    "y,NaN",
    "z,NaN",
    "yaw,NaN",
    "pitch,NaN"
  })
  void loadingCapturePointsRejectsNonfiniteCoordinatesWithoutDroppingAValidSibling(
      String field, String coordinate) {
    String json =
        """
        {"id":"saved_capture_boundary","battleType":"field",
         "sides":[{"id":"attacker"},{"id":"defender"}],
         "points":[
           {"id":"invalid","location":{"world":"world","%s":"%s"},
            "controllerSideId":"defender","captureProgress":50},
           {"id":"valid","location":{"world":"world","x":10,"y":64,"z":0},
            "controllerSideId":"attacker","captureProgress":70}]}
        """
            .formatted(field, coordinate);
    var data =
        net.tfminecraft.simplefactions.database.JsonUtil.GSON.fromJson(
            json, net.tfminecraft.simplefactions.database.BattleData.class);
    Battle restored =
        net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper.fromData(data);
    assertEquals(1, restored.getPoints().size());
    CapturePoint valid = restored.getPoints().getFirst();
    assertEquals("valid", valid.getId());
    assertEquals(70, valid.getCaptureProgress());
    assertEquals(10, valid.getLoc().getX());
    assertSame(restored.getSideById("attacker"), valid.getController());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({"-30,0", "140,100"})
  void persistedCaptureProgressUsesTheSameBoundsAsRuntimeUpdates(int stored, int expected) {
    String json =
        """
        {"id":"saved_capture_progress","battleType":"field",
         "sides":[{"id":"attacker"},{"id":"defender"}],
         "points":[{"id":"keep","location":{"world":"world","x":10,"y":64,"z":0},
                    "controllerSideId":"defender","captureProgress":%d}]}
        """
            .formatted(stored);
    var data =
        net.tfminecraft.simplefactions.database.JsonUtil.GSON.fromJson(
            json, net.tfminecraft.simplefactions.database.BattleData.class);
    Battle restored =
        net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper.fromData(data);
    CapturePoint point = restored.getPoints().getFirst();
    assertEquals(expected, point.getCaptureProgress());
    assertSame(restored.getSideById("defender"), point.getController());
    assertEquals("keep", point.getId());
  }

  @Test
  void anUnplacedPublicPointRetainsItsEditableIdentityWhenNoChainAnchorCanBeResolved() {
    battle.getSideById("defender").setSpawn(new Location(rig.domain.ui.world, 0, 64, 0));
    CapturePoint unplaced = new CapturePoint("draft", null, battle.getSideById("defender"), 30);
    unplaced.setSequenceIndex(7);
    battle.addPoint(unplaced);
    BattleCapturePoints.syncLinearChain(battle);
    assertEquals(List.of(unplaced), battle.getPoints());
    assertEquals("draft", unplaced.getId());
    assertEquals(7, unplaced.getSequenceIndex());
    assertEquals(30, unplaced.getCaptureProgress());
    assertNull(unplaced.getLoc());
  }

  @Test
  void anInvalidAttackerAnchorFallsBackToTheUsableDefenderChain() {
    battle.getSideById("defender").setSpawn(new Location(rig.domain.ui.world, 0, 64, 0));
    battle.getSideById("attacker").setSpawn(new Location(rig.domain.ui.world, Double.NaN, 64, 0));
    CapturePoint far = point("far", 40, battle.getSideById("attacker"), 100, 10);
    CapturePoint near = point("near", 4, battle.getSideById("defender"), 60, 20);
    battle.addPoint(far);
    battle.addPoint(near);
    BattleCapturePoints.syncLinearChain(battle);
    assertEquals("A", near.getId());
    assertEquals("B", far.getId());
    assertEquals(0, near.getSequenceIndex());
    assertEquals(1, far.getSequenceIndex());
    assertEquals(60, near.getCaptureProgress());
    assertSame(battle.getSideById("attacker"), far.getController());
  }

  @Test
  void aNonfinitePublicDraftPointDoesNotLoseTheOtherPointsOrCorruptItsLocation() {
    battle.getSideById("defender").setSpawn(new Location(rig.domain.ui.world, 0, 64, 0));
    CapturePoint placed = point("placed", 3, battle.getSideById("defender"), 70, 5);
    CapturePoint draft =
        new CapturePoint(
            "draft",
            new Location(rig.domain.ui.world, Double.NaN, 64, 0),
            battle.getSideById("attacker"),
            20);
    draft.setSequenceIndex(99);
    battle.addPoint(draft);
    battle.addPoint(placed);
    BattleCapturePoints.syncLinearChain(battle);
    assertEquals(List.of(draft, placed), battle.getPoints());
    assertEquals("A", placed.getId());
    assertEquals(0, placed.getSequenceIndex());
    assertEquals("draft", draft.getId());
    assertEquals(99, draft.getSequenceIndex());
    assertTrue(Double.isNaN(draft.getLoc().getX()));
    assertEquals(20, draft.getCaptureProgress());
    assertEquals(70, placed.getCaptureProgress());
  }

  @Test
  void aTemporarilyUnplacedMiddlePointKeepsUsableChainEndpointsAndAllDraftObjects() {
    battle.getSideById("defender").setSpawn(new Location(rig.domain.ui.world, 0, 64, 0));
    battle.getSideById("attacker").setSpawn(new Location(rig.domain.ui.world, 100, 64, 0));
    CapturePoint first = point("first", 1, battle.getSideById("defender"), 100, 0);
    CapturePoint last = point("last", 99, battle.getSideById("attacker"), 100, 3);
    CapturePoint draft = new CapturePoint("draft", null, battle.getSideById("defender"), 40);
    CapturePoint middle = point("middle", 50, battle.getSideById("attacker"), 60, 2);
    battle.addPoint(last);
    battle.addPoint(draft);
    battle.addPoint(middle);
    battle.addPoint(first);
    BattleCapturePoints.syncLinearChain(battle);
    assertEquals(4, battle.getPoints().size());
    assertTrue(battle.getPoints().containsAll(List.of(first, draft, middle, last)));
    assertEquals("A", first.getId());
    assertEquals("D", last.getId());
    assertEquals(0, first.getSequenceIndex());
    assertEquals(3, last.getSequenceIndex());
    assertNull(draft.getLoc());
    assertEquals(40, draft.getCaptureProgress());
    assertEquals(60, middle.getCaptureProgress());
  }
}
