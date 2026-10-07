package net.tfminecraft.simplefactions.war.campaign.progression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignBattleEndService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushProjection;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.enums.*;
import net.tfminecraft.simplefactions.war.pathfinder.TitleManagerProvinceOwnerLookup;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CampaignCapabilityLifecycleCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(Cache.class, "warFirstBattleAtBorder");
    rig.remember(Cache.class, "warProvincesBetweenBattles");
    Cache.warFirstBattleAtBorder = true;
    Cache.warProvincesBetweenBattles = 1;
    rig.war.setGoal(WarGoalType.WAR);
    rig.war.setWarType(WarType.WAR);
    rig.war.setCampaignProvinces(List.of(10, 11, 12, 20));
    rig.war.setObjectiveProvinceId(20);
    rig.war.setCursorIndex(1);
    rig.war.setInitiativeAttacker(5);
    rig.war.setInitiativeDefender(5);
    rig.war.setInitiativeHolder(BelligerentRole.ATTACKER);
    rig.war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
  }

  @AfterEach
  void close() throws Exception {
    if (rig != null) rig.close();
  }

  @ParameterizedTest
  @EnumSource(CampaignCoalition.class)
  void aMissingOrFinishedCampaignCannotHoldInitiative(CampaignCoalition coalition) {
    assertFalse(CampaignProgressionService.holdsInitiative(null, coalition));
    CampaignCoalitionService.setInitiativeHolderCoalition(rig.war, coalition);
    rig.war.end(WarEndReason.WHITE_PEACE);
    assertFalse(CampaignProgressionService.holdsInitiative(rig.war, coalition));
    assertFalse(
        CampaignProgressionService.holdsInitiative(
            rig.war, CampaignCoalitionService.coalitionToBelligerentRole(coalition)));
  }

  @Test
  void genuineArmySlotsDistinguishOffensiveAndDefensiveCapacity() {
    army(rig.attacker, "raiders", true, 3);
    army(rig.attacker, "guards", false, 2);
    army(rig.defender, "guards", false, 4);
    assertEquals(
        3, CampaignCapabilityService.offensiveRegiments(rig.war, 11, CampaignCoalition.AGGRESSOR));
    assertEquals(
        5, CampaignCapabilityService.defensiveRegiments(rig.war, 11, CampaignCoalition.AGGRESSOR));
    assertEquals(
        4, CampaignCapabilityService.defensiveRegiments(rig.war, 11, CampaignCoalition.DEFENDER));
    assertTrue(CampaignCapabilityService.canAttack(rig.war, CampaignCoalition.AGGRESSOR));
    assertFalse(CampaignCapabilityService.canAttack(rig.war, CampaignCoalition.DEFENDER));
    assertTrue(
        CampaignCapabilityService.canMountOffensive(rig.war, CampaignCoalition.AGGRESSOR, 11));
    assertTrue(
        CampaignCapabilityService.canMountOffensiveAtNextBattle(
            rig.war, CampaignCoalition.AGGRESSOR));
    assertFalse(
        CampaignCapabilityService.canMountOffensiveAtNextBattle(
            rig.war, CampaignCoalition.DEFENDER));
    rig.war.setInitiativeAttacker(0);
    assertFalse(CampaignCapabilityService.canAttack(rig.war, CampaignCoalition.AGGRESSOR));
    assertFalse(
        CampaignCapabilityService.hasOffensiveArmy(rig.war, CampaignCoalition.AGGRESSOR, 11));
    assertTrue(CampaignCapabilityService.canDefend(rig.war, 11, CampaignCoalition.AGGRESSOR));
    assertFalse(
        CampaignCapabilityService.hasOffensiveArmy(rig.war, CampaignCoalition.AGGRESSOR, 0));
    assertEquals(0, CampaignCapabilityService.offensiveRegiments(rig.war, 11, null));
    assertEquals(
        0, CampaignCapabilityService.defensiveRegiments(null, 11, CampaignCoalition.DEFENDER));
  }

  @Test
  void pendingChoicesAndExhaustedSchedulesBlockNewOffensivesWithoutSpendingFuel() {
    army(rig.attacker, "raiders", true, 3);
    CampaignBattleEndService.beginPostBattleChoice(rig.war, CampaignCoalition.AGGRESSOR);
    assertTrue(CampaignCapabilityService.needsPostBattleChoice(rig.war));
    assertTrue(CampaignCapabilityService.nextBattleProvince(rig.war).isEmpty());
    assertFalse(CampaignCapabilityService.canAttack(rig.war, CampaignCoalition.AGGRESSOR));
    assertFalse(
        CampaignCapabilityService.canMountOffensiveAtNextBattle(
            rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(CampaignProgressionService.resolveNextBattleNodes(rig.war).isEmpty());
    CampaignBattleEndService.resolveChoicePhase(rig.war);
    assertEquals(List.of(11), CampaignProgressionService.resolveNextBattleNodes(rig.war));
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(12, CampaignBattleKind.FIELD, false, null)));
    rig.war.setCampaignScheduleIndex(1);
    assertFalse(CampaignCapabilityService.canAttack(rig.war, CampaignCoalition.AGGRESSOR));
    assertFalse(
        CampaignCapabilityService.canMountOffensiveAtNextBattle(
            rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(CampaignProgressionService.resolveNextBattleNodes(rig.war).isEmpty());
    assertEquals(5, rig.war.getInitiativeAttacker());
  }

  @Test
  void anUnpopulatedOrInvalidAxisNeverProvidesAnAttackTarget() {
    assertEquals(
        -1, CampaignCapabilityService.capitulationTargetIndex(null, CampaignCoalition.AGGRESSOR));
    assertEquals(
        0, CampaignCapabilityService.stepsToCapitulationTarget(null, CampaignCoalition.AGGRESSOR));
    assertEquals(-1, CampaignCapabilityService.objectiveIndex(null));
    assertEquals(0, CampaignCapabilityService.aggressorCapitalIndex(null));
    assertFalse(CampaignCapabilityService.canReachTarget(null, CampaignCoalition.AGGRESSOR));
    assertFalse(CampaignCapabilityService.canAttack(null, CampaignCoalition.AGGRESSOR));
    assertFalse(
        CampaignCapabilityService.canMountOffensiveAtNextBattle(null, CampaignCoalition.AGGRESSOR));
    assertTrue(CampaignCapabilityService.nextBattleProvince(null).isEmpty());
    assertTrue(CampaignProgressionService.resolveNextBattleNodes(null).isEmpty());
    assertNull(CampaignProgressionService.getInitiativeHolder(null));
    assertFalse(CampaignProgressionService.holdsInitiative(rig.war, (BelligerentRole) null));
    rig.war.setObjectiveProvinceId(99);
    assertEquals(
        0,
        CampaignCapabilityService.stepsToCapitulationTarget(rig.war, CampaignCoalition.AGGRESSOR));
    rig.war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
    assertTrue(CampaignCapabilityService.nextBattleProvince(rig.war).isEmpty());
    rig.war.setCursorIndex(-1);
    assertFalse(CampaignCapabilityService.isValidWar(rig.war));
    rig.war.setCampaignProvinces(List.of());
    assertFalse(CampaignCapabilityService.isValidWar(rig.war));
  }

  @Test
  void axisDistanceAndLegacyPhaseFallbackRemainConsistentAcrossInitiativeChanges() {
    assertEquals(
        3, CampaignCapabilityService.capitulationTargetIndex(rig.war, CampaignCoalition.AGGRESSOR));
    assertEquals(0, CampaignCapabilityService.aggressorCapitalIndex(rig.war));
    rig.attacker.addProvince(11);
    rig.attacker.setCapital(11, true);
    assertEquals(1, CampaignCapabilityService.aggressorCapitalIndex(rig.war));
    assertEquals(
        2,
        CampaignProgressionService.stepsToCapitulationTarget(rig.war, CampaignCoalition.AGGRESSOR));
    assertEquals(
        2, CampaignProgressionService.stepsToCapitulationTarget(rig.war, BelligerentRole.ATTACKER));
    assertEquals(3, CampaignProgressionService.getObjectiveIndex(rig.war));
    assertEquals(0, CampaignProgressionService.clampCursorIndex(rig.war, -5));
    assertEquals(3, CampaignProgressionService.clampCursorIndex(rig.war, 99));
    rig.war.setPushTarget(null);
    rig.war.setCampaignPhase(CampaignPhase.COUNTER_PUSH);
    rig.war.setInitiativeHolder(BelligerentRole.DEFENDER);
    assertEquals(
        CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL,
        CampaignCapabilityService.effectivePushTarget(rig.war));
    assertEquals(
        CampaignCoalition.AGGRESSOR, CampaignCapabilityService.battleDefensiveCoalition(rig.war));
    assertEquals(BelligerentRole.DEFENDER, CampaignProgressionService.getInitiativeHolder(rig.war));
    assertTrue(CampaignProgressionService.holdsInitiative(rig.war, BelligerentRole.DEFENDER));
    assertEquals(10, CampaignCapabilityService.nextBattleProvince(rig.war).orElseThrow());
    CampaignProgressionService.applyPostponedBattle(rig.war);
    assertEquals(1, rig.war.getCursorIndex());
    assertEquals(5, rig.war.getInitiativeDefender());
    rig.war.setCursorIndex(0);
    assertTrue(CampaignCapabilityService.nextBattleProvince(rig.war).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(CampaignCoalition.class)
  void coalitionIdentityFuelAndPeaceFlagsStayIndependent(CampaignCoalition coalition) {
    var own = coalition == CampaignCoalition.AGGRESSOR ? rig.attacker : rig.defender;
    var other = coalition == CampaignCoalition.AGGRESSOR ? rig.defender : rig.attacker;
    var side = CampaignCoalitionService.toSide(rig.war, coalition);
    assertSame(own, side.getLeader());
    assertEquals(coalition, CampaignCoalitionService.coalitionOf(rig.war, side));
    assertEquals(coalition.opposing(), CampaignCoalitionService.opposing(rig.war, coalition));
    assertTrue(CampaignCoalitionService.isWarLeader(rig.war, own));
    assertTrue(CampaignCoalitionService.isCoalitionWarLeader(rig.war, own, coalition));
    assertFalse(CampaignCoalitionService.isCoalitionWarLeader(rig.war, other, coalition));
    CampaignCoalitionService.setFuel(rig.war, coalition, 1);
    CampaignCoalitionService.spendFuel(rig.war, coalition);
    CampaignCoalitionService.spendFuel(rig.war, coalition);
    assertEquals(0, CampaignCoalitionService.getFuel(rig.war, coalition));
    assertEquals(5, CampaignCoalitionService.getFuel(rig.war, coalition.opposing()));
    CampaignCoalitionService.setWhitePeaceProposed(rig.war, coalition, true);
    assertTrue(CampaignCoalitionService.isWhitePeaceProposed(rig.war, coalition));
    assertFalse(CampaignCoalitionService.isWhitePeaceProposed(rig.war, coalition.opposing()));
    assertEquals(
        coalition,
        CampaignCoalition.fromJson(coalition.toJson().toUpperCase(java.util.Locale.ROOT)));
  }

  @Test
  void optionalCoalitionLookupsDoNotChangeAValidWar() {
    assertNull(CampaignCoalitionService.toSide(null, CampaignCoalition.AGGRESSOR));
    assertNull(CampaignCoalitionService.coalitionOf(null, rig.war.getAttackers()));
    var outsider = rig.domain.saved("outsider", "Outsider");
    var unrelated = new net.tfminecraft.simplefactions.war.core.War(42, outsider, rig.defender);
    assertNull(CampaignCoalitionService.coalitionOf(rig.war, unrelated.getAttackers()));
    assertNull(CampaignCoalitionService.opposing(rig.war, null));
    assertEquals(
        CampaignCoalition.AGGRESSOR, CampaignCoalitionService.getInitiativeHolderCoalition(null));
    rig.war.setInitiativeHolder(BelligerentRole.DEFENDER);
    rig.war.setInitiativeHolderCoalition(null);
    assertEquals(
        CampaignCoalition.DEFENDER, CampaignCoalitionService.getInitiativeHolderCoalition(rig.war));
    CampaignCoalitionService.setInitiativeHolderCoalition(rig.war, null);
    CampaignCoalitionService.setFuel(rig.war, null, 9);
    CampaignCoalitionService.spendFuel(rig.war, null);
    CampaignCoalitionService.setWhitePeaceProposed(rig.war, null, true);
    assertEquals(0, CampaignCoalitionService.getFuel(rig.war, null));
    assertEquals(5, rig.war.getInitiativeAttacker());
    assertFalse(CampaignCoalitionService.isWhitePeaceProposed(rig.war, null));
    assertFalse(CampaignCoalitionService.isWarLeader(null, rig.attacker));
    assertFalse(CampaignCoalitionService.isWarLeader(rig.war, outsider));
    assertFalse(
        CampaignCoalitionService.isCoalitionWarLeader(rig.war, null, CampaignCoalition.AGGRESSOR));
    assertEquals(
        BelligerentRole.ATTACKER, CampaignCoalitionService.coalitionToBelligerentRole(null));
    assertNull(CampaignCoalition.fromJson(null));
    assertNull(CampaignCoalition.fromJson("removed"));
    assertEquals(
        CampaignPushTarget.TOWARD_OBJECTIVE,
        CampaignCoalitionService.derivePushTargetFromLegacyPhase(null, ObjectiveHolder.DEFENDER));
    assertEquals(
        CampaignPhase.INVASION,
        CampaignCoalitionService.deriveLegacyPhaseFromPushTarget(null, ObjectiveHolder.DEFENDER));
  }

  @ParameterizedTest
  @CsvSource({
    "TOWARD_OBJECTIVE,AGGRESSOR,AGGRESSOR,1",
    "TOWARD_OBJECTIVE,AGGRESSOR,AGGRESSOR,3",
    "RETAKE_OBJECTIVE,DEFENDER,DEFENDER,2",
    "TOWARD_AGGRESSOR_CAPITAL,DEFENDER,DEFENDER,2",
    "TOWARD_AGGRESSOR_CAPITAL,DEFENDER,AGGRESSOR,1",
    "TOWARD_OBJECTIVE,AGGRESSOR,DEFENDER,2"
  })
  void pushProjectionsUseFutureMovementWithoutChangingLiveCampaignState(
      CampaignPushTarget target,
      CampaignCoalition offensive,
      CampaignCoalition winner,
      int cursor) {
    army(rig.attacker, "raiders", true, 3);
    army(rig.defender, "raiders", true, 3);
    rig.war.setPushTarget(target);
    rig.war.setCursorIndex(cursor);
    CampaignCoalitionService.setInitiativeHolderCoalition(rig.war, offensive);
    rig.war.setLastBattleOffensiveCoalition(offensive);
    assertTrue(CampaignCapabilityService.canMountOffensiveAfterPush(rig.war, winner));
    assertEquals(cursor, rig.war.getCursorIndex());
    assertEquals(target, rig.war.getPushTarget());
    assertEquals(offensive, rig.war.getInitiativeHolderCoalition());
    assertEquals(5, rig.war.getInitiativeAttacker());
    assertEquals(5, rig.war.getInitiativeDefender());
  }

  @Test
  void aProjectedNavalSlotRequiresTheWinningCoalitionsOperationalPort() {
    army(rig.defender, "raiders", true, 2);
    rig.war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
    rig.war.setCursorIndex(2);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null)));
    rig.war.setCampaignCounterSchedule(
        List.of(new ScheduledCampaignBattle(10, CampaignBattleKind.NAVAL, true, null)));
    rig.defender.getInstallationHandler().load(List.of());
    assertFalse(
        CampaignPushProjection.canMountOffensiveAfterPush(rig.war, CampaignCoalition.DEFENDER));
    assertEquals(0, rig.war.getCampaignCounterScheduleIndex());
    rig.defender.getInstallationHandler().acceptTransferred(rig.target);
    assertTrue(
        CampaignPushProjection.canMountOffensiveAfterPush(rig.war, CampaignCoalition.DEFENDER));
    rig.war.setInitiativeDefender(0);
    assertFalse(
        CampaignPushProjection.canMountOffensiveAfterPush(rig.war, CampaignCoalition.DEFENDER));
    assertFalse(
        CampaignPushProjection.canMountOffensiveAfterPush(null, CampaignCoalition.DEFENDER));
  }

  private void army(Faction faction, String id, boolean offensive, int slots) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("offense", offensive);
    Regiment regiment = new Regiment(id, yaml);
    faction.getMilitary().getRegiments().add(regiment);
    assertTrue(faction.getMilitary().adminAdjustSlots(id, slots).allowed());
  }

  @Test
  void occupationCannotCaptureAnyLaterScheduledBattleThroughANeighboringProvince() {
    var battle = new Province(11, "PLAINS", 40, 0, 0);
    var next = new Province(12, "PLAINS", 40, 16, 0);
    var later = new Province(20, "PLAINS", 40, 16, 16);
    battle.addNeighbour(12);
    battle.addNeighbour(20);
    ProvinceManager map = new ProvinceManager();
    map.start(Map.of(11, battle, 12, next, 20, later));
    for (int province : List.of(11, 12, 20)) rig.defender.addProvince(province);
    rig.war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, false, null),
            new ScheduledCampaignBattle(12, CampaignBattleKind.FIELD, false, null),
            new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null)));
    OccupationService service = new OccupationService(map, new TitleManagerProvinceOwnerLookup());
    assertEquals(
        List.of(11),
        service.computeOccupationZone(rig.war, 11, BelligerentRole.ATTACKER).provinceIds());
    assertFalse(rig.war.getOccupiedByAttacker().contains(20));
    assertEquals(0, rig.war.getCampaignScheduleIndex());
  }

  @Test
  void navalRequirementsComeFromTheActualScheduleAndItsCurrentWarLeader() {
    assertFalse(CampaignNavyGate.invasionRequiresNavy(null));
    assertFalse(CampaignNavyGate.scheduleRequiresNavy(null));
    assertTrue(CampaignNavyGate.validateDeclareAfterPopulate(null).isValid());
    rig.war.setPillageNaturalNavyRequired(true);
    assertTrue(CampaignNavyGate.invasionRequiresNavy(rig.war));
    assertTrue(CampaignNavyGate.validateDeclareAfterPopulate(rig.war).isValid());
    rig.war.setPillageNaturalNavyRequired(false);
    rig.war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, false, null),
            new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL_INVASION, true, null)));
    assertTrue(CampaignNavyGate.invasionRequiresNavy(rig.war));
    assertFalse(CampaignNavyGate.nextSlotRequiresNavy(rig.war));
    assertTrue(CampaignNavyGate.winnerCanContestNextNaval(rig.war, null));
    rig.war.setCampaignScheduleIndex(1);
    assertTrue(CampaignNavyGate.nextSlotRequiresNavy(rig.war));
    assertFalse(CampaignNavyGate.winnerCanContestNextNaval(rig.war, null));
    assertTrue(CampaignNavyGate.winnerCanContestNextNaval(rig.war, CampaignCoalition.AGGRESSOR));
    rig.attacker.getInstallationHandler().load(List.of());
    assertFalse(CampaignNavyGate.winnerCanContestNextNaval(rig.war, CampaignCoalition.AGGRESSOR));
    assertFalse(CampaignNavyGate.validateDeclareAfterPopulate(rig.war).isValid());
    assertEquals(1, rig.war.getCampaignScheduleIndex());
  }

  @Test
  void impossibleFutureTargetsRefusePushWithoutChangingTheLiveCursor() {
    army(rig.attacker, "raiders", true, 2);
    army(rig.defender, "raiders", true, 2);
    rig.war.setObjectiveProvinceId(null);
    rig.war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
    rig.war.setLastBattleOffensiveCoalition(CampaignCoalition.DEFENDER);
    assertFalse(
        CampaignPushProjection.canMountOffensiveAfterPush(rig.war, CampaignCoalition.AGGRESSOR));
    rig.war.setObjectiveProvinceId(20);
    rig.war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
    rig.war.setCursorIndex(0);
    assertFalse(
        CampaignPushProjection.canMountOffensiveAfterPush(rig.war, CampaignCoalition.DEFENDER));
    assertEquals(0, rig.war.getCursorIndex());
    assertEquals(5, rig.war.getInitiativeDefender());
  }

  @Test
  void occupationRejectsInvalidBattlesAndCanAdvancePastAnAlreadyControlledFort() throws Exception {
    rig.remember(Cache.class, "warOccupationIncludeEnemyNeighbors");
    Cache.warOccupationIncludeEnemyNeighbors = true;
    var battle = new Province(11, "PLAINS", 40, 0, 0);
    var neighbor = new Province(12, "PLAINS", 40, 16, 0);
    battle.addNeighbour(11);
    battle.addNeighbour(12);
    rig.domain.provinceData.put(11, battle);
    rig.domain.provinceData.put(12, neighbor);
    rig.defender.addProvince(11);
    rig.defender.addProvince(12);
    rig.install(
        rig.defender,
        "captured_fort",
        net.tfminecraft.simplefactions.installation.InstallationKind.FORT,
        12);
    rig.war.putFortController("captured_fort", CampaignCoalition.AGGRESSOR);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, false, null)));
    var service =
        new OccupationService(rig.domain.provinces, new TitleManagerProvinceOwnerLookup());
    assertTrue(
        service.computeOccupationZone(null, 11, BelligerentRole.ATTACKER).provinceIds().isEmpty());
    assertFalse(service.applyBattleWin(rig.war, 0, BelligerentRole.ATTACKER));
    assertFalse(service.applyBattleWin(rig.war, 11, null));
    assertEquals(
        List.of(11, 12),
        service.computeOccupationZone(rig.war, 11, BelligerentRole.ATTACKER).provinceIds());
    assertTrue(service.applyBattleWin(rig.war, 11, BelligerentRole.ATTACKER));
    assertTrue(rig.war.getOccupiedByAttacker().containsAll(List.of(11, 12)));
    assertTrue(rig.war.getLastBattleOccupied().containsAll(List.of(11, 12)));
    assertEquals(0, rig.war.getCampaignScheduleIndex());
  }
}
