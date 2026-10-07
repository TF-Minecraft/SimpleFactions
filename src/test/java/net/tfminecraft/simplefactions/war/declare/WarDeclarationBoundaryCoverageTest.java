package net.tfminecraft.simplefactions.war.declare;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.*;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.inventory.WarCreator;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.core.CallToArmsEligibility;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommandHelper;
import net.tfminecraft.simplefactions.war.core.WarDeclareHelper;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.pathfinder.BelligerentTerritory;
import net.tfminecraft.simplefactions.war.pathfinder.PathfinderPass;
import net.tfminecraft.simplefactions.war.pathfinder.ProvincePathfinder;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class WarDeclarationBoundaryCoverageTest {
  @Test
  void governmentSelectionsCompareRealCurrentLawsAndOptionalLeadership() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction legacy = domain.saved("legacy", "LegacyLeader");
      assertNull(ChangeGovernmentEligibility.group(null, "government"));
      assertNull(ChangeGovernmentEligibility.group(legacy, null));
      assertNull(ChangeGovernmentEligibility.currentLawId(legacy, "government"));
      assertFalse(ChangeGovernmentEligibility.combinationEqualsCurrent(null, null, null));
      assertTrue(ChangeGovernmentEligibility.combinationEqualsCurrent(legacy, " ", null));
      assertFalse(ChangeGovernmentEligibility.combinationEqualsCurrent(legacy, "monarchy", null));
      assertFalse(ChangeGovernmentEligibility.hasLeadershipGroup(legacy));
      domain.lawGroup("government", Map.of());
      Faction oldGovernment = domain.saved("no_leadership", "OldLeader");
      assertTrue(
          ChangeGovernmentEligibility.combinationEqualsCurrent(oldGovernment, "current", null));
      domain.lawGroup("leadership", Map.of());
      Faction modern = domain.saved("modern", "ModernLeader");
      Law elected = domain.law("leadership", "elected", Map.of());
      Law parliament = domain.law("government", "parliament", Map.of());
      assertTrue(ChangeGovernmentEligibility.hasLeadershipGroup(modern));
      assertTrue(ChangeGovernmentEligibility.lawInGroup(modern, "government", " PARLIAMENT "));
      assertFalse(ChangeGovernmentEligibility.lawInGroup(modern, "government", " "));
      assertFalse(ChangeGovernmentEligibility.lawInGroup(modern, "missing", "parliament"));
      assertFalse(ChangeGovernmentEligibility.lawInGroup(modern, "government", null));
      assertFalse(ChangeGovernmentEligibility.lawInGroup(modern, "government", "removed_law"));
      assertTrue(ChangeGovernmentEligibility.combinationEqualsCurrent(modern, "CURRENT", " "));
      assertFalse(
          ChangeGovernmentEligibility.combinationEqualsCurrent(modern, "current", "elected"));
      modern.getLawHandler().getGroup("leadership").setCurrent(elected);
      modern.getLawHandler().getGroup("government").setCurrent(parliament);
      assertTrue(
          ChangeGovernmentEligibility.combinationEqualsCurrent(
              modern, " parliament ", " ELECTED "));
      assertEquals("parliament", ChangeGovernmentEligibility.currentLawId(modern, "government"));
    }
  }

  @Test
  void openMarketQueriesHandleRemovedAndUnidentifiedLawsWithoutSelectingAnotherLaw() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      domain.lawGroup("economy", Map.of());
      Faction faction = domain.saved("market", "MarketLeader");
      Law trade = domain.law("economy", "free_trade", Map.of("name", "Free Trade"));
      var group = faction.getLawHandler().getGroup("economy");
      assertFalse(OpenMarketEligibility.hasAnyCurrentLaw(null, List.of("current")));
      assertFalse(OpenMarketEligibility.hasAnyCurrentLaw(faction, null));
      assertFalse(OpenMarketEligibility.hasAnyCurrentLaw(faction, List.of()));
      assertTrue(OpenMarketEligibility.hasAnyCurrentLaw(faction, Arrays.asList(null, "CURRENT")));
      assertFalse(OpenMarketEligibility.hasAnyCurrentLaw(faction, List.of("free_trade")));
      assertSame(trade, OpenMarketEligibility.resolve(faction, " FREE_TRADE ").law());
      assertSame(group, OpenMarketEligibility.resolve(faction, "free_trade").group());
      assertNull(OpenMarketEligibility.resolve(null, "free_trade"));
      assertNull(OpenMarketEligibility.resolve(faction, null));
      assertNull(OpenMarketEligibility.resolve(faction, " "));
      group.getLaws().remove("free_trade");
      group.getLaws().put("removed", null);
      assertNull(OpenMarketEligibility.resolve(faction, "free_trade"));
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("name", "Unidentified imported law");
      Law unidentified = new Law("economy", null, yaml);
      group.getLaws().put("unidentified", unidentified);
      group.setCurrent(unidentified);
      assertFalse(OpenMarketEligibility.hasAnyCurrentLaw(faction, List.of("free_trade")));
      assertNull(OpenMarketEligibility.resolve(faction, "free_trade"));
      assertSame(unidentified, group.getCurrent());
    }
  }

  @Test
  void invalidDeclarationInputsNeverBecomePendingWarRequests() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("attacker", "AttackerLeader");
      Faction defender = domain.saved("defender", "DefenderLeader");
      assertTrue(
          assertThrows(
                  IllegalArgumentException.class,
                  () -> WarDeclareRequest.of(null, defender, WarGoalType.WAR))
              .getMessage()
              .contains("attacker"));
      assertTrue(
          assertThrows(
                  IllegalArgumentException.class,
                  () -> WarDeclareRequest.of(attacker, null, WarGoalType.WAR))
              .getMessage()
              .contains("defender"));
      assertTrue(
          assertThrows(
                  IllegalArgumentException.class,
                  () -> WarDeclareRequest.of(attacker, defender, null))
              .getMessage()
              .contains("goal"));
      assertTrue(WarCommandHelper.parseWarId(null).isEmpty());
      assertTrue(WarCommandHelper.parseWarId(" ").isEmpty());
      assertTrue(WarCommandHelper.parseWarId("2147483648").isEmpty());
      assertNull(WarCommandHelper.parseBelligerentRoleArg(null));
      assertNull(WarCommandHelper.parseBelligerentRoleArg(" "));
      assertTrue(WarManager.get().isEmpty());
    }
  }

  @Test
  void malformedGatewayRepliesAndFailedRedemptionPreserveTheValidatedSession() throws Exception {
    try (FactionDomainFixture domain = new FactionDomainFixture();
        GatewayScope scope = new GatewayScope()) {
      var player = domain.player("CodeLeader");
      Faction attacker = domain.saved("attacker", "CodeLeader");
      Faction defender = domain.saved("defender", "DefenderLeader");
      var session =
          new WarDeclareCodeService.Session(
              "code", attacker.getId(), defender.getId(), WarGoalType.WAR);
      WarDeclareCodeService.openSession(player, session);
      try {
        WarDeclareCodeService.openSession(null, session);
        WarDeclareCodeService.openSession(player, null);
        assertSame(session, WarDeclareCodeService.session(player));
        assertNull(WarDeclareCodeService.session(null));
        assertNull(WarDeclareCodeService.clearSession(null));
        for (String body : List.of("{", "[]", "null", "{\"goal\":null}", "{\"goal\":{}}")) {
          WarDeclareCodeService.setGateway((path, json) -> GatewayClient.Result.success(body));
          assertFalse(
              WarDeclareCodeService.validate("code", attacker.getId(), defender.getId()).ok, body);
        }
        List<String> calls = new ArrayList<>();
        WarDeclareCodeService.setGateway(
            (path, json) -> {
              calls.add(path);
              return null;
            });
        assertFalse(WarDeclareCodeService.redeem(" ", attacker.getId(), defender.getId(), 3).ok);
        assertTrue(calls.isEmpty());
        assertFalse(WarDeclareCodeService.redeem("code", attacker.getId(), defender.getId(), 3).ok);
        assertEquals(List.of("/wars/declare-codes/redeem"), calls);
        WarDeclareCodeService.setGateway((path, json) -> GatewayClient.Result.fail(" "));
        assertTrue(
            WarDeclareCodeService.validate("code", attacker.getId(), defender.getId())
                .error
                .contains("Could not reach"));
        assertSame(session, WarDeclareCodeService.session(player));
        assertFalse(
            WarDeclareCodeService.covers(
                new WarDeclareCodeService.Session("code", null, defender.getId(), WarGoalType.WAR),
                WarDeclareRequest.of(attacker, defender, WarGoalType.WAR)));
        assertTrue(WarManager.get().isEmpty());
      } finally {
        WarDeclareCodeService.clearSession(player);
      }
    }
  }

  @Test
  void defaultGatewayDispatchCarriesTheRealRequestAndStopsAtTheExternalBoundary() throws Exception {
    try (GatewayScope scope = new GatewayScope();
        MockedStatic<GatewayClient> gateway = mockStatic(GatewayClient.class)) {
      List<String> bodies = new ArrayList<>();
      GatewayClient.Result response = scope.success("{\"goal\":\"war\"}");
      gateway
          .when(
              () ->
                  GatewayClient.request(
                      eq("POST"), eq("/wars/declare-codes/validate"), anyString()))
          .thenAnswer(
              call -> {
                bodies.add(call.getArgument(2));
                return response;
              });
      WarDeclareCodeService.setGateway(null);
      assertTrue(WarDeclareCodeService.validate(" ticket ", "alpha", "beta").ok);
      var request = JsonParser.parseString(bodies.getFirst()).getAsJsonObject();
      assertEquals("ticket", request.get("code").getAsString());
      assertEquals("alpha", request.get("attacker_faction_id").getAsString());
      assertEquals("beta", request.get("defender_faction_id").getAsString());
      assertFalse(request.has("realm_id"));
    }
  }

  @Test
  void callsToArmsRequireAnUnjoinedRealAllyAndIgnoreIncompleteAllyReferences() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction caller = domain.saved("caller", "CallerLeader");
      Faction enemy = domain.saved("enemy", "EnemyLeader");
      Faction ally = domain.saved("ally", "AllyLeader");
      Faction outsider = domain.saved("outsider", "OutsiderLeader");
      var alliance = domain.relationType("ally", Map.of("name", "Ally"));
      caller.setRelation(ally, new Relation(alliance, RelationLoader.getDefaultAttitude()));
      War war = new War(881001, caller, enemy);
      var participant = war.getParticipant(caller);
      assertEquals(
          CallToArmsEligibility.CANNOT_CALL,
          CallToArmsEligibility.canCall(war, caller, outsider).message());
      assertTrue(CallToArmsEligibility.canCall(war, caller, ally).allowed());
      participant.getAllies().put(null, false);
      Faction unidentified = domain.saved("unidentified", "UnknownLeader");
      FactionManager.factions.remove(unidentified);
      unidentified.setId(null);
      participant.getAllies().put(unidentified, false);
      assertFalse(CallToArmsEligibility.canCall(war, caller, outsider).allowed());
      assertFalse(CallToArmsEligibility.canCall(war, caller, unidentified).allowed());
      assertTrue(CallToArmsEligibility.canCall(war, caller, ally).allowed());
      participant.getAllies().put(ally, true);
      assertEquals(
          CallToArmsEligibility.ALREADY_IN_WAR,
          CallToArmsEligibility.canCall(war, caller, ally).message());
      assertTrue(war.isParticipating(ally));
    }
  }

  @Test
  void nestedDefenderSubjectsAndPeerUsurpEligibilityRetainRealmOwnership() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      var dukeTitle = domain.title("duke_title", "duchy", 20);
      var countTitle = domain.title("count_title", "county", 10);
      var attackerData = domain.data("count", "CountLeader");
      attackerData.titles.add(countTitle.getId());
      var defenderData = domain.data("duke", "DukeLeader");
      defenderData.titles.add(dukeTitle.getId());
      Faction attacker = domain.saved(attackerData), defender = domain.saved(defenderData);
      Faction king = domain.saved("king", "KingLeader");
      Faction nested = domain.saved("nested", "NestedLeader");
      domain.subject(king, attacker);
      domain.subject(king, defender);
      domain.subject(defender, nested);
      assertFalse(WarDeclareHelper.canDeclareUsurp(attacker, defender));
      assertTrue(InterVassalQueries.isInternalPeer(attacker, defender));
      assertEquals(List.of(nested), WarDeclareHelper.defenderSubjects(defender));
      assertTrue(WarDeclareHelper.defenderSubjects(null).isEmpty());
      assertFalse(InterVassalQueries.isInternalPeerWar(null));
      Faction unidentified = domain.saved("unidentified", "UnknownLeader");
      unidentified.setId(null);
      FactionManager.factions.add(null);
      assertTrue(WarDeclareHelper.defenderSubjects(unidentified).isEmpty());
      assertEquals(List.of(nested), WarDeclareHelper.defenderSubjects(defender));
      FactionManager.factions.remove(null);
      FactionManager.factions.remove(unidentified);
      War war = new War(881002, attacker, defender);
      war.getAttackers().getMainParticipants().add(null);
      war.getAttackers()
          .getMainParticipants()
          .add(new Participant(null, List.of(), Map.of(), Map.of(), false));
      assertFalse(InterVassalQueries.isOverlordOfMain(nested, war));
      Faction grandchild = domain.saved("grandchild", "GrandchildLeader");
      domain.subject(nested, grandchild);
      assertFalse(
          WarDeclareHelper.canDeclareUsurp(grandchild, defender),
          "A nested subject cannot bypass its immediate liege to usurp the higher ruler");
      assertSame(dukeTitle, defender.getHighestTitle());
    }
  }

  @Test
  void pillageDistancesUseActualLandAndRefuseUnpositionedSettlements() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("attacker", "AttackerLeader"),
          defender = domain.saved("defender", "DefenderLeader");
      attacker.addProvince(1);
      defender.addProvince(3);
      Province a = new Province(1, "PLAINS", 50),
          b = new Province(2, "PLAINS", 50),
          c = new Province(3, "PLAINS", 50);
      a.addNeighbour(2);
      b.addNeighbour(1);
      b.addNeighbour(3);
      c.addNeighbour(2);
      ProvinceManager provinces = new ProvinceManager();
      provinces.start(Map.of(1, a, 2, b, 3, c));
      assertEquals(
          OptionalInt.of(2), PillageRangeQueries.landDistanceFromAttacker(provinces, attacker, 3));
      assertTrue(PillageRangeQueries.landDistanceFromAttacker(null, attacker, 3).isEmpty());
      assertTrue(PillageRangeQueries.landDistanceFromAttacker(provinces, null, 3).isEmpty());
      assertTrue(PillageRangeQueries.landDistanceFromAttacker(provinces, attacker, 0).isEmpty());
      assertTrue(PillageRangeQueries.distanceToCoast(null, 3).isEmpty());
      assertTrue(PillageRangeQueries.distanceToCoast(provinces, -1).isEmpty());
      assertFalse(
          PillageRangeQueries.canPillageSettlement(
              provinces,
              attacker,
              new Settlement("unpositioned", "Unpositioned", -1, 0, 0),
              defender,
              List.of(3),
              3));
      assertTrue(
          PillageRangeQueries.canPillageSettlement(
              provinces,
              attacker,
              new Settlement("town", "Town", 3, 0, 0),
              defender,
              List.of(3),
              2));
      assertEquals(List.of(1), attacker.getProvinces());
      assertEquals(List.of(3), defender.getProvinces());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "OVERTHROW,Overthrow",
    "CHANGE_LAW,Change Law",
    "CHANGE_TAX,Change Tax",
    "FORCE_PEACE,Force Peace"
  })
  void civilWarGoalDescriptionsAreVisibleOnTheActualWarItem(WarGoalType goal, String label) {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("attacker", "AttackerLeader"),
          defender = domain.saved("defender", "DefenderLeader");
      War war = new War(881003, attacker, defender);
      war.setGoal(goal);
      var item = new WarCreator().createWarItem(war, false);
      List<String> lore = item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList();
      assertTrue(lore.contains("War Goal: " + label), lore.toString());
      assertTrue(goal.isMovementOrigin());
    }
  }

  @Test
  void aDeclarationPreviewCannotMutateTheCalculatedCampaignRoute() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("attacker", "AttackerLeader");
      Faction defender = domain.saved("defender", "DefenderLeader");
      attacker.addProvince(1);
      attacker.addProvince(2);
      defender.addProvince(3);
      Province first = new Province(1, "PLAINS", 50);
      Province middle = new Province(2, "PLAINS", 50);
      Province last = new Province(3, "PLAINS", 50);
      first.addNeighbour(2);
      middle.addNeighbour(1);
      middle.addNeighbour(3);
      last.addNeighbour(2);
      ProvinceManager provinces = new ProvinceManager();
      provinces.start(Map.of(1, first, 2, middle, 3, last));
      Map<Integer, String> owners =
          Map.of(1, attacker.getId(), 2, attacker.getId(), 3, defender.getId());
      War war = new War(881004, attacker, defender);
      var route =
          new ProvincePathfinder(provinces, owners::get)
              .findRoute(
                  1,
                  3,
                  PathfinderPass.LAND_NO_NEUTRAL,
                  BelligerentTerritory.fromWar(war, owners::get));
      assertTrue(route.isFound());
      assertEquals(List.of(1, 2, 3), route.getPathView());
      assertThrows(UnsupportedOperationException.class, () -> route.getPathView().clear());
      assertEquals(List.of(1, 2, 3), route.getPath());
      assertEquals(List.of(1, 2), attacker.getProvinces());
      assertEquals(List.of(3), defender.getProvinces());
    }
  }

  private static final class GatewayScope implements AutoCloseable {
    private final WarDeclareCodeService.Gateway previous;

    GatewayScope() throws Exception {
      Field field = WarDeclareCodeService.class.getDeclaredField("gateway");
      field.setAccessible(true);
      previous = (WarDeclareCodeService.Gateway) field.get(null);
    }

    GatewayClient.Result success(String body) {
      return GatewayClient.Result.success(body);
    }

    public void close() {
      WarDeclareCodeService.setGateway(previous);
    }
  }
}
