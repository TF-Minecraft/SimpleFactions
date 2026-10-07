package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.core.CallToArmsEligibility;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class WarManagerCoverageTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private Map<Player, Request> previousRequests;
  private Map<Integer, List<WarCommitment>> previousCommitments;
  private List<Battle> previousBattles;
  private Object previousDeclareError;
  private RelationType alliance;
  private final Map<Field, Object> originalSettings = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    Files.createDirectories(files.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    previousRequests = new HashMap<>(requests());
    requests().clear();
    previousCommitments = new LinkedHashMap<>(commitments());
    commitments().clear();
    previousBattles = new ArrayList<>(BattleManager.get());
    BattleManager.get().clear();
    previousDeclareError = field(WarManager.class, "lastDeclareError").get(null);
    alliance = fixture.relationType("ally", Map.of("link", "ally"));
    fixture.regiment("guard", false, 3, 0);
    setting("warPathfinderNeutralPenalty", 8.0);
    setting("warPathfinderSeaPassEnabled", true);
    setting("warPathfinderWaterCost", 0.0);
    setting("warInitiativeFactor", 1.5);
    setting("warPortSeaZocRadius", 2);
    Map<WarGoalType, Integer> maxBattles = new EnumMap<>(WarGoalType.class);
    for (WarGoalType goal : WarGoalType.values()) maxBattles.put(goal, 4);
    setting("warGoalMaxBattles", maxBattles);
    setting("tradeCarry", new HashMap<>(Map.of(Terrain.PLAINS, 0.85)));
    setting("pillageRangeProvinces", 4);
    setting("provinceCost", 0);
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (previousRequests != null) {
        requests().clear();
        requests().putAll(previousRequests);
      }
      if (previousCommitments != null) {
        commitments().clear();
        commitments().putAll(previousCommitments);
      }
      if (previousBattles != null) {
        BattleManager.get().clear();
        BattleManager.get().addAll(previousBattles);
      }
      field(WarManager.class, "lastDeclareError").set(null, previousDeclareError);
      for (var original : originalSettings.entrySet())
        original.getKey().set(null, original.getValue());
      if (fixture != null) fixture.close();
    } finally {
      if (files != null) files.close();
    }
  }

  private static Field field(Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  @SuppressWarnings("unchecked")
  private static Map<Player, Request> requests() throws Exception {
    return (Map<Player, Request>) field(RequestManager.class, "requests").get(null);
  }

  @SuppressWarnings("unchecked")
  private static Map<Integer, List<WarCommitment>> commitments() throws Exception {
    return (Map<Integer, List<WarCommitment>>)
        field(WarCommitmentService.class, "commitmentsByWar").get(null);
  }

  private void ally(Faction first, Faction second) {
    first.setRelation(second, new Relation(alliance, RelationLoader.getDefaultAttitude()));
    second.setRelation(first, new Relation(alliance, RelationLoader.getDefaultAttitude()));
  }

  private void setting(String name, Object value) throws Exception {
    Field field = field(Cache.class, name);
    originalSettings.putIfAbsent(field, field.get(null));
    field.set(null, value);
  }

  private void offensiveArmy() {
    RegimentLoader.oList.clear();
    YamlConfiguration config = new YamlConfiguration();
    config.set("guard.item.material", "PAPER");
    config.set("guard.default-slots", 3);
    config.set("guard.offense", true);
    RegimentLoader.oList.add(new Regiment("guard", config.getConfigurationSection("guard")));
    fixture.lawGroup("military", Map.of("effects.faction.regiments", List.of("guard 3")));
  }

  private ProvinceManager graph(boolean connected, boolean sea) {
    fixture.provincesEnabled(true);
    Province first = new Province(1, "PLAINS", 50, 10, 0);
    Province second = new Province(2, sea ? "SEA" : "PLAINS", 50, 20, 0);
    Province third = new Province(3, "PLAINS", 50, 30, 0);
    if (connected) {
      first.addNeighbour(2);
      second.addNeighbour(1);
      second.addNeighbour(3);
      third.addNeighbour(2);
    }
    ProvinceManager manager = new ProvinceManager();
    manager.start(Map.of(1, first, 2, second, 3, third));
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
    return manager;
  }

  private void land(Faction faction, int province) {
    faction.addProvince(province);
    faction.setCapital(province, true, false);
  }

  @Test
  void acceptingAnInvitationAfterTheWarEndsDoesNotRecreateItsSaveOrCommitTroops() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    ally(attacker, target);
    Player sender = fixture.player("Alice");
    Player called = fixture.player("Cara");
    War war = WarManager.addWar(new War(31, attacker, defender));
    WarManager.sendRequest(sender, attacker, target, war);
    assertTrue(RequestManager.hasRequest(called));
    WarManager.endWar(war);
    assertFalse(Files.exists(files.root.resolve("Wars/war_31.json")));
    clearInvocations(called);

    RequestManager.accept(called);

    assertAll(
        () -> assertFalse(war.isParticipating(target)),
        () -> assertTrue(WarManager.getCommitmentsForWar(war.getId()).isEmpty()),
        () -> assertFalse(Files.exists(files.root.resolve("Wars/war_31.json"))),
        () -> verify(called, never()).sendMessage(startsWith("§aYour faction has joined")));
    assertFalse(RequestManager.hasRequest(called));
  }

  @Test
  void aPendingInvitationCannotBeAcceptedForAnotherFactionAfterTheLeaderMoves() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    Faction other = fixture.saved("other", "Elena");
    ally(attacker, target);
    ally(attacker, other);
    Player sender = fixture.player("Alice");
    Player called = fixture.player("Cara");
    War war = WarManager.addWar(new War(32, attacker, defender));
    WarManager.sendRequest(sender, attacker, target, war);
    target.getOrCreateMainGuild().addMember("Dave");
    target.setLeader("Dave");
    target.getOrCreateMainGuild().kick("Cara");
    other.getOrCreateMainGuild().addMember("Cara");
    other.setLeader("Cara");
    assertSame(other, FactionManager.getByLeader("Cara"));

    RequestManager.accept(called);

    assertFalse(war.isParticipating(target));
    assertFalse(war.isParticipating(other));
    assertTrue(WarManager.getCommitmentsForWar(war.getId()).isEmpty());
    assertFalse(RequestManager.hasRequest(called));
  }

  @Test
  void anInvitationIsRevalidatedWhenTheInvitedAllyBecomesBoundToTheEnemy() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    ally(attacker, target);
    Player sender = fixture.player("Alice");
    Player called = fixture.player("Cara");
    War war = WarManager.addWar(new War(33, attacker, defender));
    WarManager.sendRequest(sender, attacker, target, war);
    fixture.subject(defender, target);
    assertFalse(CallToArmsEligibility.canCall(war, attacker, target).allowed());

    RequestManager.accept(called);

    assertFalse(war.getAttackers().getMainParticipants().getFirst().getAllies().get(target));
    assertTrue(WarManager.getCommitmentsForWar(war.getId()).isEmpty());
    assertFalse(RequestManager.hasRequest(called));
  }

  @Test
  void anInvitationCannotRestoreAnOldWarObjectAfterTheManagerReloads() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    ally(attacker, target);
    Player called = fixture.player("Cara");
    War original = WarManager.addWar(new War(34, attacker, defender));
    WarManager.sendRequest(fixture.player("Alice"), attacker, target, original);
    WarManager.start();
    War current = WarManager.getById(34);
    assertNotSame(original, current);
    assertTrue(original.isActive());
    assertTrue(current.isActive());

    RequestManager.accept(called);

    assertFalse(original.isParticipating(target));
    assertFalse(current.isParticipating(target));
    assertTrue(WarManager.getCommitmentsForWar(34).isEmpty());
    assertFalse(RequestManager.hasRequest(called));
  }

  @Test
  void breakingTheAllianceInvalidatesAnInvitationBeforeTheNextWarUpdate() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    ally(attacker, target);
    Player called = fixture.player("Cara");
    War war = WarManager.addWar(new War(35, attacker, defender));
    WarManager.sendRequest(fixture.player("Alice"), attacker, target, war);
    attacker.getDiplomacyHandler().removeRelation(target.getId());
    target.getDiplomacyHandler().removeRelation(attacker.getId());
    assertFalse(RelationManager.getAllies(attacker).contains(target));

    RequestManager.accept(called);

    assertFalse(war.isParticipating(target));
    assertTrue(WarManager.getCommitmentsForWar(35).isEmpty());
    assertFalse(RequestManager.hasRequest(called));
  }

  @Test
  void acceptingACurrentInvitationCommitsItsFactionAndPersistsItsParticipation() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    ally(attacker, target);
    Player sender = fixture.player("Alice");
    Player called = fixture.player("Cara");
    War war = WarManager.addWar(new War(36, attacker, defender));
    WarManager.sendRequest(sender, attacker, target, war);

    RequestManager.accept(called);

    assertTrue(war.isParticipating(target));
    assertFalse(RequestManager.hasRequest(called));
    assertFalse(WarManager.getCommitmentsForWar(36).isEmpty());
    verify(sender).sendMessage(target.getName() + " §aaccepted your call to arms");
    verify(called).sendMessage("§aYour faction has joined the " + war.getName());
    assertTrue(new Database().loadWars().getFirst().isParticipating(target));
  }

  @Test
  void aLeaderWhoLosesTheirFactionCannotAcceptAndAnAbsentRequestIsHarmless() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    ally(attacker, target);
    Player called = fixture.player("Cara");
    WarManager.acceptRequest(called);
    War war = WarManager.addWar(new War(37, attacker, defender));
    WarManager.sendRequest(fixture.player("Alice"), attacker, target, war);
    target.getOrCreateMainGuild().addMember("Dave");
    target.setLeader("Dave");

    RequestManager.accept(called);

    assertFalse(war.isParticipating(target));
    assertTrue(WarManager.getCommitmentsForWar(37).isEmpty());
    verify(called).sendMessage("§cYou do not have a faction");
  }

  @Test
  void callsRequireAnEligibleTargetAndItsOnlineLeader() throws Exception {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction target = fixture.saved("target", "Cara");
    ally(attacker, target);
    Player sender = fixture.player("Alice");
    War war = WarManager.addWar(new War(38, attacker, defender));
    WarManager.sendRequest(sender, attacker, defender, war);
    assertTrue(requests().isEmpty());
    WarManager.sendRequest(sender, attacker, target, war);
    verify(sender).sendMessage("§cCannot send request, target faction leader is not online!");
    Player called = fixture.player("Cara");
    when(called.isOnline()).thenReturn(false);
    WarManager.sendRequest(sender, attacker, target, war);
    verify(sender, times(2))
        .sendMessage("§cCannot send request, target faction leader is not online!");
    assertFalse(RequestManager.hasRequest(called));
  }

  @Test
  void disabledProvincesAndInvalidDeclarationsLeaveWarStateUnchanged() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    assertNull(WarManager.declareWar(attacker, defender, WarGoalType.WAR, null, null));
    assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, WarManager.getLastDeclareError());
    fixture.provincesEnabled(true);
    assertNull(WarManager.declareWar(attacker, attacker, WarGoalType.WAR, null, null));
    assertTrue(WarManager.getLastDeclareError().contains("own faction"));
    assertTrue(WarManager.get().isEmpty());
    assertTrue(WarManager.getCommitmentsForWar(0).isEmpty());
  }

  @Test
  void declarationsRequireOffensiveRegimentsBeforeCreatingAnyWar() {
    graph(true, false);
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    land(attacker, 1);
    land(defender, 3);

    assertNull(WarManager.declareWar(attacker, defender, WarGoalType.WAR, null, null));

    assertTrue(WarManager.getLastDeclareError().contains("offensive regiment"));
    assertTrue(WarManager.get().isEmpty());
    assertTrue(WarManager.getCommitmentsForWar(0).isEmpty());
  }

  @Test
  void aDisconnectedGraphDeniesWarBeforeCommittingTroopsOrSaving() {
    offensiveArmy();
    graph(false, false);
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    land(attacker, 1);
    land(defender, 3);

    assertNull(WarManager.declareWar(attacker, defender, WarGoalType.WAR, null, null));

    assertTrue(WarManager.getLastDeclareError().contains("no campaign route"));
    assertTrue(WarManager.get().isEmpty());
    assertTrue(WarManager.getCommitmentsForWar(0).isEmpty());
    assertFalse(Files.exists(files.root.resolve("Wars/war_0.json")));
  }

  @ParameterizedTest
  @EnumSource(
      value = WarGoalType.class,
      names = {"WAR", "SUBJUGATE", "TRANSFER_SUBJECT", "CHANGE_GOVERNMENT", "PILLAGE"})
  void declarationsPreserveTheirGoalMetadataAndCommitTheRealParticipants(WarGoalType goal) {
    offensiveArmy();
    graph(true, false);
    fixture.lawGroup(
        "government", Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS true")));
    fixture.lawGroup("leadership", Map.of());
    fixture.law("government", "republic", Map.of());
    fixture.law("leadership", "elected", Map.of());
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    land(attacker, 1);
    land(defender, 3);
    String subjectId = null;
    String relationId = null;
    String governmentId = null;
    String leadershipId = null;
    String settlementId = null;
    if (goal == WarGoalType.TRANSFER_SUBJECT) {
      Faction subject = fixture.saved("subject", "Cara");
      fixture.subject(defender, subject);
      land(subject, 2);
      subjectId = subject.getId();
    } else if (goal == WarGoalType.SUBJUGATE) {
      relationId = "vassal";
    } else if (goal == WarGoalType.CHANGE_GOVERNMENT) {
      governmentId = "republic";
      leadershipId = "elected";
    } else if (goal == WarGoalType.PILLAGE) {
      var city = defender.getSettlementHandler().found("Defender city", 3, 30, 0);
      assertTrue(city.isSuccess(), city.getMessage());
      settlementId = city.getSettlement().getId();
    }
    War war =
        WarManager.declareWar(
            attacker,
            defender,
            goal,
            null,
            subjectId,
            relationId,
            governmentId,
            leadershipId,
            settlementId);

    assertNotNull(war, WarManager.getLastDeclareError());
    assertNull(WarManager.getLastDeclareError());
    assertSame(war, WarManager.getById(0));
    assertEquals(goal, war.getGoal());
    assertEquals(subjectId, war.getSubjectFactionId());
    assertEquals(relationId, war.getRelationTypeId());
    assertEquals(governmentId, war.getGovernmentLawId());
    assertEquals(leadershipId, war.getLeadershipLawId());
    assertEquals(settlementId, war.getTargetSettlementId());
    assertFalse(WarManager.getCommitmentsForWar(0).isEmpty());
    War stored = new Database().loadWars().getFirst();
    assertEquals(goal, stored.getGoal());
    assertEquals(war.getCampaignProvinces(), stored.getCampaignProvinces());
    assertEquals(subjectId, stored.getSubjectFactionId());
    assertEquals(relationId, stored.getRelationTypeId());
    assertEquals(governmentId, stored.getGovernmentLawId());
    assertEquals(leadershipId, stored.getLeadershipLawId());
    assertEquals(settlementId, stored.getTargetSettlementId());
  }

  @Test
  void warQueriesDistinguishAnActiveCoalitionFromAnEndedWarAndAnUnrelatedFaction() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction subject = fixture.saved("subject", "Cara");
    Faction unrelated = fixture.saved("unrelated", "Dave");
    fixture.subject(attacker, subject);
    War ended = WarManager.addWar(new War(0, attacker, defender));
    ended.end(WarEndReason.ADMIN_END);
    War current = WarManager.addWar(new War(2, attacker, defender));

    assertEquals(List.of(current), WarManager.getActive());
    assertEquals(1, WarManager.newId());
    assertSame(current, WarManager.findSharedActiveWar(attacker, defender));
    assertSame(current, WarManager.findSharedActiveWar(attacker, subject));
    assertTrue(WarManager.exists(attacker, subject));
    assertFalse(WarManager.existsHostile(attacker, subject));
    assertTrue(WarManager.existsHostile(attacker, defender));
    assertFalse(WarManager.existsHostile(attacker, unrelated));
    assertTrue(WarManager.isAtWar(subject));
    assertFalse(WarManager.isAtWar(unrelated));
    assertFalse(WarManager.isAtWar(null));
    assertNull(WarManager.findSharedActiveWar(null, defender));
    assertNull(WarManager.findSharedActiveWar(attacker, null));
    assertNull(WarManager.findSharedActiveWar(attacker, unrelated));
    assertNull(WarManager.getByFaction(unrelated));
    assertNull(WarManager.getById(99));
    WarManager.getActive().clear();
    assertEquals(2, WarManager.get().size());
    WarManager.get().remove(ended);
    assertSame(current, WarManager.getByFaction(attacker));
  }

  @ParameterizedTest
  @EnumSource(WarEndReason.class)
  void endingAWarDeletesItsSaveAndNotifiesOnlyItsOnlineMembers(WarEndReason reason) {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Player alice = fixture.player("Alice");
    Player bob = fixture.player("Bob");
    Player unrelated = fixture.player("Cara");
    when(bob.isOnline()).thenReturn(false);
    War war = WarManager.addWar(new War(39, attacker, defender));
    WarCommitmentService.commitAllParticipants(war);
    assertFalse(WarManager.getCommitmentsForWar(39).isEmpty());
    clearInvocations(alice, bob, unrelated);

    WarManager.endWar(war, reason);

    assertFalse(war.isActive());
    assertEquals(reason, war.getEndReason());
    assertNull(WarManager.getById(39));
    assertTrue(WarManager.getCommitmentsForWar(39).isEmpty());
    assertFalse(Files.exists(files.root.resolve("Wars/war_39.json")));
    String message =
        switch (reason) {
          case WHITE_PEACE -> "§7The war has ended in white peace.";
          case ATTACKER_VICTORY -> "§7The war has ended. The attacker coalition wins.";
          case DEFENDER_VICTORY -> "§7The war has ended. The defender coalition wins.";
          default -> "§7The war has ended.";
        };
    verify(alice).sendMessage(message);
    verify(bob, never()).sendMessage(anyString());
    verify(unrelated, never()).sendMessage(anyString());
  }

  @Test
  void absentWarOrEndReasonDoesNotTerminateAnExistingWar() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    War war = WarManager.addWar(new War(40, attacker, defender));
    WarManager.endWar(null, WarEndReason.ADMIN_END);
    WarManager.endWar(war, null);
    assertTrue(war.isActive());
    assertSame(war, WarManager.getById(40));
    assertTrue(Files.exists(files.root.resolve("Wars/war_40.json")));
  }

  @Test
  void aCampaignCanBeRegeneratedFromCurrentLandAndPersistedThroughThePublicManager() {
    graph(true, false);
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    land(attacker, 1);
    land(defender, 3);
    War war = WarManager.addWar(new War(41, attacker, defender));
    war.setWarType(WarType.SUBJUGATE);
    war.setGoal(WarGoalType.SUBJUGATE);

    assertTrue(WarManager.regenerateCampaign(war));

    assertEquals(List.of(1, 2, 3), war.getCampaignProvinces());
    assertEquals(3, war.getObjectiveProvinceId());
    War stored = new Database().loadWars().getFirst();
    assertEquals(war.getCampaignBattleSchedule(), stored.getCampaignBattleSchedule());
    assertFalse(war.getCampaignBattleSchedule().isEmpty());
    assertFalse(WarManager.regenerateCampaign(null));
    war.end(WarEndReason.ADMIN_END);
    assertFalse(WarManager.regenerateCampaign(war));
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(null);
    assertFalse(WarManager.regenerateCampaign(war));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void crossingAnEnemyPortBlockadeWithoutANavyCannotStartWarOrCivilWar(boolean civil) {
    offensiveArmy();
    graph(true, true);
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    land(attacker, 1);
    land(defender, 3);
    defender
        .getInstallationHandler()
        .load(
            List.of(
                new Installation("harbor", "Harbor", InstallationKind.PORT, 3, 30, 0, 1000L)
                    .toData()));

    War war =
        civil
            ? WarManager.startCivilWar(
                attacker, defender, WarGoalType.CHANGE_LAW, "movement", null, null, null)
            : WarManager.declareWar(attacker, defender, WarGoalType.WAR, null, null);

    assertNull(war);
    assertTrue(
        WarManager.getLastDeclareError().toLowerCase(java.util.Locale.ROOT).contains("port"),
        WarManager.getLastDeclareError());
    assertTrue(WarManager.get().isEmpty());
    assertTrue(WarManager.getCommitmentsForWar(0).isEmpty());
  }

  @Test
  void aCivilWarValidatesRequiredInputsAndProvinceAvailability() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    assertNull(
        WarManager.startCivilWar(
            attacker, defender, WarGoalType.CHANGE_LAW, "movement", null, null, null));
    assertEquals(Cache.PROVINCES_DISABLED_MESSAGE, WarManager.getLastDeclareError());
    fixture.provincesEnabled(true);
    assertNull(
        WarManager.startCivilWar(
            null, defender, WarGoalType.CHANGE_LAW, "movement", null, null, null));
    assertNull(
        WarManager.startCivilWar(
            attacker, null, WarGoalType.CHANGE_LAW, "movement", null, null, null));
    assertNull(WarManager.startCivilWar(attacker, defender, null, "movement", null, null, null));
    assertTrue(WarManager.getLastDeclareError().contains("Could not start"));
    assertTrue(WarManager.get().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void aCivilWarStoresUniqueMainsAndBackersAndRefreshesRebelLawSlotsAfterReload(int paidSlots) {
    offensiveArmy();
    graph(true, false);
    fixture.lawGroup("government", Map.of("effects.faction.regiments", List.of("guard 4")));
    Faction attacker = fixture.saved("rebels", "Alice");
    Faction defender = fixture.saved("host", "Bob");
    Faction extra = fixture.saved("extra", "Cara");
    Faction backer = fixture.saved("backer", "Dave");
    Faction incomplete = fixture.saved("incomplete", "Elena");
    FactionManager.factions.remove(incomplete);
    incomplete.setId(null);
    land(attacker, 1);
    land(defender, 3);
    CivilWarSnapshot snapshot = new CivilWarSnapshot();
    snapshot.setHostFactionId(defender.getId());
    snapshot.setTempRebelFactionId(attacker.getId());
    attacker.getMilitary().getRegiment("guard").setCurrentSlots(7 + paidSlots);
    assertEquals(7 + paidSlots, attacker.getMilitary().getManpower(true));
    War war =
        WarManager.startCivilWar(
            attacker,
            defender,
            WarGoalType.CHANGE_LAW,
            "movement",
            Arrays.asList(null, incomplete, attacker, extra),
            Arrays.asList(null, incomplete, attacker, extra, backer, backer),
            snapshot);

    assertNotNull(war, WarManager.getLastDeclareError());
    assertEquals(
        List.of(attacker, extra),
        war.getAttackers().getMainParticipants().stream().map(p -> p.getLeader()).toList());
    assertEquals(List.of(backer), war.getAttackers().getMainParticipants().getFirst().getBackers());
    assertTrue(war.getAttackers().getMainParticipants().stream().allMatch(p -> p.isCivilWar()));
    assertTrue(war.getDefenders().getMainParticipants().stream().allMatch(p -> p.isCivilWar()));
    assertEquals("movement", war.getMovementId());
    assertSame(snapshot, war.getCivilWarSnapshot());
    assertTrue(war.isParticipating(backer));
    int rebelSlots = attacker.getMilitary().getManpower(true);
    assertEquals(paidSlots, rebelSlots);
    assertEquals(
        rebelSlots,
        WarManager.getCommitmentsForWar(war.getId()).stream()
            .filter(row -> attacker.getId().equals(row.factionId()))
            .mapToInt(WarCommitment::count)
            .sum());
    WarManager.start();
    War restored = WarManager.getById(0);
    assertNotSame(war, restored);
    assertEquals("rebels", restored.getCivilWarSnapshot().getTempRebelFactionId());
    assertEquals(rebelSlots, attacker.getMilitary().getManpower(true));
    assertEquals(
        paidSlots,
        WarManager.getCommitmentsForWar(restored.getId()).stream()
            .filter(row -> attacker.getId().equals(row.factionId()))
            .mapToInt(WarCommitment::count)
            .sum());
    assertTrue(restored.isParticipating(extra));
    assertTrue(restored.isParticipating(backer));
  }

  @Test
  void deJureDeclarationPreservesTheClaimedTitleWithoutAnnexingLandBeforeVictory() {
    offensiveArmy();
    graph(true, false);
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    land(attacker, 1);
    land(defender, 3);
    defender.addProvince(2);
    var title = fixture.title("border", "province", 2);
    attacker.addTitle(title);

    War war = WarManager.declareWar(attacker, defender, WarGoalType.DE_JURE_ANNEX, "border", null);

    assertNotNull(war, WarManager.getLastDeclareError());
    assertEquals("border", war.getTargetTitleId());
    assertEquals("border", new Database().loadWars().getFirst().getTargetTitleId());
    assertFalse(attacker.ownsProvince(2));
    assertTrue(defender.ownsProvince(2));
    assertEquals(2, war.getObjectiveProvinceId());
  }

  @Test
  void missingProvinceServicesPreventCampaignMutationAndWarCreation() {
    offensiveArmy();
    fixture.provincesEnabled(true);
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(null);
    assertNull(WarManager.declareWar(attacker, defender, WarGoalType.WAR, null, null));
    assertTrue(WarManager.getLastDeclareError().contains("no campaign route"));
    assertNull(
        WarManager.startCivilWar(
            attacker, defender, WarGoalType.CHANGE_LAW, "movement", null, null, null));
    assertTrue(WarManager.getLastDeclareError().contains("no campaign route"));
    War candidate = new War(90, attacker, defender);
    var previousCampaign = candidate.getCampaignProvinces();
    SimpleFactions.plugin = null;
    try {
      assertFalse(WarManager.regenerateCampaign(candidate));
    } finally {
      SimpleFactions.plugin = fixture.ui.plugin;
    }
    assertSame(previousCampaign, candidate.getCampaignProvinces());
    assertTrue(WarManager.get().isEmpty());
    assertFalse(Files.exists(files.root.resolve("Wars/war_90.json")));
  }

  @Test
  void endingOneOfSeveralWarsPreservesOtherWarsAndTheirTroopCommitments() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    Faction other = fixture.saved("other", "Cara");
    War retained = WarManager.addWar(new War(91, attacker, defender));
    War ended = WarManager.addWar(new War(92, attacker, other));
    WarCommitmentService.commitAllParticipants(retained);
    WarCommitmentService.commitAllParticipants(ended);
    var retainedCommitments = new ArrayList<>(WarManager.getCommitmentsForWar(91));
    assertFalse(retainedCommitments.isEmpty());

    WarManager.endWar(ended);

    assertSame(retained, WarManager.getById(91));
    assertTrue(retained.isActive());
    assertNull(WarManager.getById(92));
    assertEquals(retainedCommitments, WarManager.getCommitmentsForWar(91));
    assertTrue(WarManager.getCommitmentsForWar(92).isEmpty());
    assertTrue(Files.exists(files.root.resolve("Wars/war_91.json")));
    assertFalse(Files.exists(files.root.resolve("Wars/war_92.json")));
  }

  @Test
  void aLegacyRaidNeedsNoCampaignRouteWhenProvinceServicesAreUnavailable() {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    War raid = new War(93, attacker, defender);
    raid.setWarType(WarType.RAID);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(null);
    var priorCampaign = raid.getCampaignProvinces();

    assertTrue(WarManager.populateCampaignIfNeeded(raid));

    assertSame(priorCampaign, raid.getCampaignProvinces());
    assertFalse(WarManager.regenerateCampaign(raid));
    assertTrue(WarManager.get().isEmpty());
    assertFalse(Files.exists(files.root.resolve("Wars/war_93.json")));
  }
}
