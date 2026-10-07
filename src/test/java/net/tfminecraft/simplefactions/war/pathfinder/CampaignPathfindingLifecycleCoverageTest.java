package net.tfminecraft.simplefactions.war.pathfinder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.campaign.ObjectiveProvincePicker;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignPathfindingLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private final ProvinceManager provinces = new ProvinceManager();
  private final Map<Integer, Province> graph = new LinkedHashMap<>();
  private Map<Terrain, Double> oldCarry;
  private double oldWater, oldPenalty;
  private boolean oldSeaPass;
  private Faction attacker, defender, neutral;
  private ProvinceOwnerLookup owners;
  private ProvincePathfinder pathfinder;
  private War war;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    oldCarry = Cache.tradeCarry;
    oldWater = Cache.warPathfinderWaterCost;
    oldPenalty = Cache.warPathfinderNeutralPenalty;
    oldSeaPass = Cache.warPathfinderSeaPassEnabled;
    Cache.tradeCarry = new HashMap<>();
    Cache.tradeCarry.put(Terrain.PLAINS, 1.0);
    Cache.tradeCarry.put(Terrain.MOUNTAIN, 0.25);
    Cache.tradeCarry.put(Terrain.SEA, 0.5);
    Cache.tradeCarry.put(Terrain.WATER, 0.5);
    Cache.warPathfinderWaterCost = 0;
    Cache.warPathfinderNeutralPenalty = 8;
    Cache.warPathfinderSeaPassEnabled = true;
    provinces.start(graph);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(provinces);
    attacker = fixture.saved("attacker", "Alice");
    defender = fixture.saved("defender", "Bob");
    neutral = fixture.saved("neutral", "Carol");
    war = new War(918735, attacker, defender);
    owners =
        id -> {
          Faction owner = TitleManager.getByProvince(id);
          return owner == null ? null : owner.getId();
        };
    pathfinder = new ProvincePathfinder(provinces, owners);
  }

  @AfterEach
  void close() throws Exception {
    Cache.tradeCarry = oldCarry;
    Cache.warPathfinderWaterCost = oldWater;
    Cache.warPathfinderNeutralPenalty = oldPenalty;
    Cache.warPathfinderSeaPassEnabled = oldSeaPass;
    if (fixture != null) fixture.close();
  }

  @Test
  void explicitNeutralPenaltyPassPricesForeignTransitBeforeChoosingTheRoute() {
    province(1, Terrain.PLAINS, attacker);
    province(2, Terrain.PLAINS, neutral);
    province(3, Terrain.MOUNTAIN, attacker);
    province(4, Terrain.PLAINS, defender);
    link(1, 2);
    link(2, 4);
    link(1, 3);
    link(3, 4);
    BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners);

    PathfinderResult result =
        pathfinder.findRoute(1, 4, PathfinderPass.LAND_NEUTRAL_PENALTY, territory);

    assertTrue(result.isFound());
    assertEquals(
        List.of(1, 3, 4),
        result.getPath(),
        "The configured foreign transit penalty outweighs the mountain detour");
    assertEquals(5.0, result.getTotalCost());
  }

  @Test
  void missingBlockedAndDanglingRouteNodesFailCleanlyWithoutChangingTheGraph() {
    province(1, Terrain.PLAINS, attacker);
    province(2, Terrain.SEA, null);
    province(3, Terrain.PLAINS, defender);
    link(1, 2);
    link(2, 3);
    graph.get(1).addNeighbour(999);
    BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners);
    assertFalse(pathfinder.findRoute(0, 3, PathfinderPass.LAND_NO_NEUTRAL, territory).isFound());
    assertFalse(pathfinder.findRoute(1, 999, PathfinderPass.LAND_NO_NEUTRAL, territory).isFound());
    assertFalse(pathfinder.findRoute(2, 3, PathfinderPass.LAND_NO_NEUTRAL, territory).isFound());
    Cache.warPathfinderSeaPassEnabled = false;
    assertFalse(pathfinder.findRouteWithFallback(1, 3, territory).isFound());
    Cache.warPathfinderSeaPassEnabled = true;
    var route = pathfinder.findRouteWithFallback(1, 3, territory);
    assertEquals(List.of(1, 2, 3), route.getPath());
    assertEquals(PathfinderPass.SEA_NO_NEUTRAL, route.getPassUsed());
    assertEquals(3.0, route.getTotalCost());
    assertEquals(Set.of(2, 999), graph.get(1).getNeighbours());
    var stationary = pathfinder.findRoute(1, 1, PathfinderPass.LAND_NO_NEUTRAL, territory);
    assertEquals(List.of(1), stationary.getPath());
    assertEquals(0, stationary.getTotalCost());
  }

  @Test
  void impassableCarryAndConfiguredWaterCostAffectTheRealShortestRoute() {
    province(1, Terrain.PLAINS, attacker);
    province(2, Terrain.WATER, null);
    province(3, Terrain.MOUNTAIN, attacker);
    province(4, Terrain.PLAINS, defender);
    link(1, 2);
    link(2, 4);
    link(1, 3);
    link(3, 4);
    var territory = BelligerentTerritory.fromWar(war, owners);
    Cache.warPathfinderWaterCost = 10;
    assertEquals(
        List.of(1, 3, 4),
        pathfinder.findRoute(1, 4, PathfinderPass.LAND_NO_NEUTRAL, territory).getPath());
    Cache.tradeCarry.put(Terrain.MOUNTAIN, 0.0);
    var route = pathfinder.findRoute(1, 4, PathfinderPass.LAND_NO_NEUTRAL, territory);
    assertEquals(List.of(1, 2, 4), route.getPath());
    assertEquals(11, route.getTotalCost());
    Cache.warPathfinderWaterCost = 0;
    Cache.tradeCarry.put(Terrain.WATER, 0.0);
    assertFalse(pathfinder.findRoute(1, 4, PathfinderPass.LAND_NO_NEUTRAL, territory).isFound());
  }

  @Test
  void neutralPenaltyDoesNotChargeWildernessOrBelligerentsAndCannotBecomeNegative() {
    province(1, Terrain.PLAINS, attacker);
    province(2, Terrain.PLAINS, null);
    province(3, Terrain.PLAINS, neutral);
    province(4, Terrain.PLAINS, defender);
    link(1, 2);
    link(2, 3);
    link(3, 4);
    var territory = BelligerentTerritory.fromWar(war, owners);
    assertEquals(
        11,
        pathfinder.findRoute(1, 4, PathfinderPass.LAND_NEUTRAL_PENALTY, territory).getTotalCost());
    Cache.warPathfinderNeutralPenalty = -5;
    assertEquals(
        3,
        pathfinder.findRoute(1, 4, PathfinderPass.LAND_NEUTRAL_PENALTY, territory).getTotalCost());
    assertFalse(pathfinder.findRoute(1, 4, PathfinderPass.SEA_NO_NEUTRAL, territory).isFound());
  }

  @Test
  void nonFiniteNeutralPenaltyCannotPoisonAnOtherwiseReachableRoute() {
    province(1, Terrain.PLAINS, attacker);
    province(2, Terrain.PLAINS, neutral);
    link(1, 2);
    Cache.warPathfinderNeutralPenalty = Double.NaN;

    var result =
        pathfinder.findRoute(
            1, 2, PathfinderPass.LAND_NEUTRAL_PENALTY, BelligerentTerritory.fromWar(war, owners));

    assertTrue(result.isFound());
    assertEquals(List.of(1, 2), result.getPath());
    assertEquals(
        1.0,
        result.getTotalCost(),
        "A malformed configured penalty must retain the finite terrain cost");
  }

  @Test
  void campaignLineSkipsADisconnectedBorderEntryAndUsesTheReachableCapitalFront() {
    province(1, Terrain.PLAINS, attacker);
    province(8, Terrain.PLAINS, attacker);
    province(2, Terrain.PLAINS, defender);
    province(3, Terrain.PLAINS, defender);
    province(4, Terrain.PLAINS, defender);
    link(8, 2);
    link(1, 3);
    link(3, 4);
    attacker.setCapital(1, true, false);
    var route = pathfinder.computeCampaignLine(war, 4);
    assertEquals(List.of(3, 4), route.getPath());
    assertEquals(3, route.getStartProvinceId());
    assertFalse(pathfinder.computeCampaignLine(war, 999).isFound());
  }

  @Test
  void campaignLineUsesSeaContactThenFallsBackToDefenderLandWhenNoBorderIsKnown() {
    province(1, Terrain.PLAINS, attacker);
    province(2, Terrain.SEA, null);
    province(3, Terrain.PLAINS, defender);
    province(4, Terrain.PLAINS, defender);
    link(1, 2);
    link(2, 3);
    link(3, 4);
    attacker.setCapital(1, true, false);
    assertEquals(List.of(3, 4), pathfinder.computeCampaignLine(war, 4).getPath());
    graph.remove(2);
    attacker.setCapital(-1, true, false);
    assertEquals(List.of(3, 4), pathfinder.computeCampaignLine(war, 4).getPath());
    defender.removeProvince(3, false);
    assertEquals(List.of(4), pathfinder.computeCampaignLine(war, 4).getPath());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void equallyReachableBordersPreferTheLongerDefenderMarchThenStableProvinceOrder(boolean longer) {
    province(1, Terrain.PLAINS, attacker);
    province(6, Terrain.PLAINS, defender);
    province(4, Terrain.PLAINS, defender);
    province(5, Terrain.PLAINS, defender);
    province(8, Terrain.PLAINS, defender);
    link(1, 6);
    link(1, 4);
    link(6, 8);
    link(4, 5);
    link(5, 8);
    if (!longer) link(4, 8);
    attacker.setCapital(1, true, false);
    var route = pathfinder.computeCampaignLine(war, 8);
    assertEquals(4, route.getStartProvinceId());
    assertEquals(longer ? List.of(4, 5, 8) : List.of(4, 8), route.getPath());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void withoutACapitalBorderChoiceUsesMinimumCostWithDeterministicTies(boolean tie) {
    province(1, Terrain.PLAINS, attacker);
    province(6, Terrain.PLAINS, defender);
    province(4, Terrain.PLAINS, defender);
    province(5, Terrain.PLAINS, defender);
    province(8, Terrain.PLAINS, defender);
    link(1, 6);
    link(1, 4);
    link(6, 5);
    link(5, 8);
    link(4, 8);
    if (tie) link(6, 8);
    var route = pathfinder.computeCampaignLine(war, 8);
    assertEquals(List.of(4, 8), route.getPath());
  }

  @Test
  void objectiveSelectionValidatesWarSpecificTargetsAndUsesTheActualTitleOwner() {
    province(10, Terrain.PLAINS, defender);
    province(20, Terrain.PLAINS, defender);
    province(30, Terrain.PLAINS, defender);
    defender.setCapital(20, true, false);
    var picker = new ObjectiveProvincePicker(provinces);
    war.setGoal(WarGoalType.WAR);
    assertEquals(20, picker.pickObjective(war, defender).orElseThrow());
    assertTrue(picker.pickObjective(null, defender).isEmpty());
    assertTrue(picker.pickObjective(war, null).isEmpty());
    war.setGoal(WarGoalType.DE_JURE_ANNEX);
    assertTrue(picker.pickObjective(war, defender).isEmpty());
    war.setTargetTitleId("missing");
    assertTrue(picker.pickObjective(war, defender).isEmpty());
    var title = fixture.title("claimed", "county", 10, 30);
    war.setTargetTitleId(title.getId());
    assertEquals(
        10,
        picker.pickObjective(war, defender).orElseThrow(),
        "An unheld title uses defender settlement/geometry");
    defender.addTitle(title);
    assertEquals(10, picker.pickObjective(war, defender).orElseThrow());
  }

  @Test
  void transferObjectiveResolvesTheCurrentSubjectRatherThanTheDefendersCapital() {
    var subject = fixture.saved("subject", "SubLeader");
    province(40, Terrain.PLAINS, subject);
    subject.setCapital(40, true, false);
    fixture.subject(defender, subject);
    var picker = new ObjectiveProvincePicker(provinces);
    war.setGoal(WarGoalType.TRANSFER_SUBJECT);
    assertTrue(picker.pickObjective(war, defender).isEmpty());
    war.setSubjectFactionId("missing");
    assertTrue(picker.pickObjective(war, defender).isEmpty());
    war.setSubjectFactionId(subject.getId());
    assertEquals(40, picker.pickObjective(war, defender).orElseThrow());
  }

  @Test
  void settlementPopulationAndGeometryProvideStableObjectivesWithoutACapital() {
    province(10, Terrain.PLAINS, defender);
    province(20, Terrain.PLAINS, defender);
    province(30, Terrain.PLAINS, defender);
    var picker = new ObjectiveProvincePicker(provinces);
    war.setGoal(WarGoalType.SUBJUGATE);
    assertEquals(20, picker.pickObjective(war, defender).orElseThrow());
    var smaller = new Settlement("small", "Small", 10, 100, 0);
    var larger = new Settlement("large", "Large", 30, 300, 0);
    var outside = new Settlement("outside", "Outside", 99, 990, 0);
    defender.getSettlementHandler().acceptTransferred(smaller);
    defender.getSettlementHandler().acceptTransferred(larger);
    defender.getSettlementHandler().acceptTransferred(outside);
    fixture.guild(defender, "g1", "One").setCapital(10, false);
    fixture.guild(defender, "g2", "Two").setCapital(30, false);
    fixture.guild(defender, "g3", "Three").setCapital(30, false);
    assertEquals(30, picker.pickObjective(war, defender).orElseThrow());
    fixture.guild(defender, "g4", "Four").setCapital(10, false);
    assertEquals(10, picker.pickObjective(war, defender).orElseThrow());
    defender.getSettlementHandler().load(List.of());
    graph.remove(20);
    assertEquals(10, picker.pickObjective(war, defender).orElseThrow());
    graph.clear();
    assertEquals(10, picker.pickObjective(war, defender).orElseThrow());
  }

  @Test
  void pillageSelectionStaysWithinTheNamedDefenderAndRejectsMissingSettlements() {
    province(10, Terrain.PLAINS, neutral);
    province(20, Terrain.PLAINS, defender);
    neutral
        .getSettlementHandler()
        .acceptTransferred(new Settlement("town", "Neutral town", 10, 100, 0));
    defender
        .getSettlementHandler()
        .acceptTransferred(new Settlement("town", "Target town", 20, 200, 0));
    war.setGoal(WarGoalType.PILLAGE);
    war.setTargetSettlementId("town");
    var picker = new ObjectiveProvincePicker(provinces);
    assertEquals(20, picker.pickObjective(war, defender).orElseThrow());
    war.setTargetSettlementId("missing");
    assertTrue(picker.pickObjective(war, defender).isEmpty());
    war.setTargetSettlementId(null);
    assertTrue(picker.pickObjective(war, defender).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void capitalReachTakesPriorityOverTheRemainingDistanceRegardlessOfIterationOrder(
      boolean fartherFirst) {
    province(1, Terrain.PLAINS, attacker);
    province(2, Terrain.PLAINS, attacker);
    province(3, Terrain.PLAINS, attacker);
    province(fartherFirst ? 4 : 6, Terrain.PLAINS, defender);
    province(fartherFirst ? 6 : 4, Terrain.PLAINS, defender);
    province(7, Terrain.PLAINS, defender);
    province(8, Terrain.PLAINS, defender);
    link(1, 2);
    link(2, 3);
    link(3, 4);
    link(1, 6);
    link(6, 7);
    link(7, 8);
    link(4, 8);
    attacker.setCapital(1, true, false);
    assertEquals(List.of(6, 7, 8), pathfinder.computeCampaignLine(war, 8).getPath());
  }

  @Test
  void aFactionWithNoLandHasNoGeographicalWarObjective() {
    war.setGoal(WarGoalType.SUBJUGATE);
    assertTrue(new ObjectiveProvincePicker(provinces).pickObjective(war, defender).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void onlyNonpositiveImportedProvinceIdsCannotBecomeAWarObjective(boolean titleGoal) {
    province(-10, Terrain.PLAINS, defender);
    province(0, Terrain.PLAINS, defender);
    if (titleGoal) {
      var title = fixture.title("invalid_target", "county", -10, 0);
      defender.addTitle(title);
      war.setGoal(WarGoalType.DE_JURE_ANNEX);
      war.setTargetTitleId(title.getId());
    } else {
      war.setGoal(WarGoalType.SUBJUGATE);
    }

    assertTrue(
        new ObjectiveProvincePicker(provinces).pickObjective(war, defender).isEmpty(),
        "Missing or corrupt province identifiers must not create a fightable objective");
    assertEquals(
        List.of(-10, 0),
        defender.getProvinces(),
        "Objective selection must not rewrite imported territory");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mixedImportedProvinceIdsSelectTheValidPositiveObjective(boolean titleGoal) {
    province(-10, Terrain.PLAINS, defender);
    province(0, Terrain.PLAINS, defender);
    province(10, Terrain.PLAINS, defender);
    if (titleGoal) {
      var title = fixture.title("mixed_target", "county", -10, 0, 10);
      defender.addTitle(title);
      war.setGoal(WarGoalType.DE_JURE_ANNEX);
      war.setTargetTitleId(title.getId());
    } else {
      war.setGoal(WarGoalType.SUBJUGATE);
    }
    var picker = new ObjectiveProvincePicker(provinces);

    assertEquals(10, picker.pickObjective(war, defender).orElseThrow());
    graph.clear();
    assertEquals(
        10,
        picker.pickObjective(war, defender).orElseThrow(),
        "A positive stored province keeps the deterministic fallback when map data is absent");
    assertEquals(List.of(-10, 0, 10), defender.getProvinces());
  }

  private Province province(int id, Terrain terrain, Faction owner) {
    Province province = new Province(id, terrain.name(), 50, id * 20, 0);
    graph.put(id, province);
    if (owner != null) owner.addProvince(id);
    return province;
  }

  private void link(int a, int b) {
    graph.get(a).addNeighbour(b);
    graph.get(b).addNeighbour(a);
  }
}
