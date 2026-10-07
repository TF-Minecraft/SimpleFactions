package net.tfminecraft.simplefactions.war.campaign.progression.postbattle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.*;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.enums.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignPostBattleLifecycleCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(Cache.class, "warFirstBattleAtBorder");
    rig.remember(Cache.class, "warProvincesBetweenBattles");
    rig.remember(Cache.class, "warDefenderChoiceDeadlineHour");
    Cache.warFirstBattleAtBorder = true;
    Cache.warProvincesBetweenBattles = 1;
    Cache.warDefenderChoiceDeadlineHour = 15;
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
  @ValueSource(booleans = {true, false})
  void aRunningBattleCannotBeForfeitedByARepeatedLaunchCheck(boolean offensiveDepleted) {
    army(rig.attacker, "raiders", true, 1);
    army(rig.defender, "guards", false, 2);
    var battle = BattleFactory.createBlank(BattleType.FIELD, "already_running");
    battle.setWarId(rig.war.getId());
    battle.setProvinceId(11);
    Warband attackers =
        Warband.createCampaignSideShell(
            "live_attackers", rig.war, rig.war.getAttackers(), "attacker");
    Warband defenders =
        Warband.createCampaignSideShell(
            "live_defenders", rig.war, rig.war.getDefenders(), "defender");
    attackers.addPlayer(rig.alice);
    defenders.addPlayer(rig.bob);
    WarbandManager.addWarband(attackers);
    WarbandManager.addWarband(defenders);
    battle.getSideById("attacker").addBand(attackers);
    battle.getSideById("defender").addBand(defenders);
    for (var side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 100, 65, 100));
      side.setJail(new Location(rig.domain.ui.world, 200, 65, 200));
    }
    battle
        .getPoints()
        .add(
            new CapturePoint(
                "live_point",
                new Location(rig.domain.ui.world, 120, 65, 120),
                battle.getSideById("attacker"),
                100));
    BattleManager.addBattle(battle);
    assertNull(battle.start());
    assertTrue(battle.hasStarted());
    if (offensiveDepleted) {
      assertTrue(rig.attacker.getMilitary().adminAdjustSlots("raiders", -1).allowed());
    } else {
      assertTrue(rig.defender.getMilitary().adminAdjustSlots("guards", -2).allowed());
    }
    long writes = rig.warWrites();
    assertFalse(CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(rig.war, 11));
    CampaignMilitaryWalkoverService.resolvePendingWalkovers(rig.war);
    assertEquals(0, rig.war.getCampaignBattlesFought());
    assertEquals(5, rig.war.getInitiativeAttacker());
    assertEquals(writes, rig.warWrites());
    assertSame(battle, BattleManager.getByWarId(rig.war.getId()));
  }

  @ParameterizedTest
  @EnumSource(CampaignCoalition.class)
  void winnerHoldGivesOnlyTheLosingCoalitionItsResponseAndAttackRestoresVoting(
      CampaignCoalition winner) {
    army(rig.attacker, "guards", true, 3);
    army(rig.defender, "guards", true, 3);
    CampaignBattleEndService.snapshotBattleStart(rig.war);
    CampaignBattleEndService.beginPostBattleChoice(rig.war, winner);
    assertTrue(CampaignPostBattleChoiceService.needsWinnerChoice(rig.war));
    assertEquals(winner, CampaignPostBattleChoiceService.choiceLeaderCoalition(rig.war));
    assertTrue(
        CampaignPostBattleChoiceService.isChoiceLeader(
            rig.war, winner == CampaignCoalition.AGGRESSOR ? rig.attacker : rig.defender));
    assertTrue(CampaignChoiceService.applyHold(rig.war));
    assertEquals(winner.opposing(), CampaignPostBattleChoiceService.choiceLeaderCoalition(rig.war));
    assertTrue(rig.war.isHoldPeaceProposalActive());
    assertTrue(CampaignCoalitionService.isWhitePeaceProposed(rig.war, winner));
    assertTrue(CampaignChoiceService.applyLoserAttack(rig.war));
    assertFalse(CampaignPostBattleChoiceService.needsAnyChoice(rig.war));
    assertFalse(rig.war.isHoldPeaceProposalActive());
    assertNull(rig.war.getPostBattleWinnerCoalition());
    assertEquals(winner.opposing(), rig.war.getInitiativeHolderCoalition());
    assertEquals(BattleSchedulePhase.VOTING, rig.war.getBattleSchedulePhase());
    assertTrue(rig.warWrites() > 0);
  }

  @Test
  void acceptingAHoldPeaceEndsTheActualWarAndClearsItsChoice() {
    CampaignBattleEndService.beginPostBattleChoice(rig.war, CampaignCoalition.AGGRESSOR);
    assertTrue(CampaignChoiceService.applyHold(rig.war));
    assertTrue(CampaignChoiceService.applyLoserAcceptPeace(rig.war));
    assertFalse(rig.war.isActive());
    assertEquals(WarEndReason.WHITE_PEACE, rig.war.getEndReason());
    assertFalse(CampaignPostBattleChoiceService.needsAnyChoice(rig.war));
    assertFalse(CampaignChoiceService.applyLoserAcceptPeace(rig.war));
  }

  @Test
  void noArmyForcesHoldButAnActualOffensiveArmyAllowsPush() {
    assertTrue(
        CampaignPostBattleChoiceService.resolveMandatoryHoldIfNeeded(
            rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(CampaignPostBattleChoiceService.needsLoserResponse(rig.war));
    assertEquals(
        CampaignCoalition.DEFENDER, CampaignPostBattleChoiceService.choiceLeaderCoalition(rig.war));
    CampaignBattleEndService.resolveChoicePhase(rig.war);
    CampaignBattleEndService.clearHoldPeace(rig.war);
    army(rig.attacker, "guards", true, 3);
    army(rig.defender, "guards", true, 3);
    assertFalse(
        CampaignPostBattleChoiceService.resolveMandatoryHoldIfNeeded(
            rig.war, CampaignCoalition.AGGRESSOR));
    CampaignBattleEndService.beginPostBattleChoice(rig.war, CampaignCoalition.AGGRESSOR);
    assertTrue(CampaignChoiceService.applyPush(rig.war));
    assertEquals(2, rig.war.getCursorIndex());
    assertEquals(CampaignCoalition.AGGRESSOR, rig.war.getInitiativeHolderCoalition());
    assertFalse(CampaignPostBattleChoiceService.needsAnyChoice(rig.war));
  }

  @Test
  void choiceDeadlinesRespectTheBattleDayAndResolveTheCurrentPhaseOnce() {
    army(rig.attacker, "guards", true, 3);
    army(rig.defender, "guards", true, 3);
    CampaignBattleEndService.beginPostBattleChoice(rig.war, CampaignCoalition.AGGRESSOR);
    assertFalse(CampaignPostBattleChoiceService.applyDeadlineIfDue(rig.war, null));
    assertFalse(
        CampaignPostBattleChoiceService.applyDeadlineIfDue(
            rig.war, BattleWindowService.atScheduleHour(Fixture.DAY.minusDays(1), 20)));
    assertFalse(
        CampaignPostBattleChoiceService.applyDeadlineIfDue(
            rig.war, BattleWindowService.atScheduleHour(Fixture.DAY, 14)));
    assertTrue(
        CampaignPostBattleChoiceService.applyDeadlineIfDue(
            rig.war, BattleWindowService.atScheduleHour(Fixture.DAY, 15)));
    assertEquals(2, rig.war.getCursorIndex());
    assertFalse(
        CampaignPostBattleChoiceService.applyDeadlineIfDue(
            rig.war, BattleWindowService.atScheduleHour(Fixture.DAY, 15)));
  }

  @Test
  void whitePeaceRequiresTheOpponentsProposalAndTheCurrentWarLeader() {
    var outsider = rig.domain.saved("outsider", "Outsider");
    assertFalse(WhitePeaceService.acceptWhitePeace(rig.war, outsider));
    assertFalse(WhitePeaceService.acceptWhitePeace(rig.war, rig.attacker));
    assertFalse(WhitePeaceService.acceptWhitePeace(null, rig.attacker));
    assertFalse(WhitePeaceService.acceptWhitePeace(rig.war, null));
    rig.war.setForcedWhitePeaceByDefender(true);
    assertTrue(WhitePeaceService.recalculateProposals(rig.war).isEmpty());
    assertTrue(WhitePeaceService.acceptWhitePeace(rig.war, rig.attacker));
    assertFalse(WhitePeaceService.acceptWhitePeace(rig.war, rig.defender));
    rig.war.setForcedWhitePeaceByAttacker(true);
    assertEquals(
        WarEndReason.WHITE_PEACE, WhitePeaceService.recalculateProposals(rig.war).orElseThrow());
    assertTrue(WhitePeaceService.shouldAutoEnd(rig.war));
    assertTrue(CampaignChoiceService.acceptWhitePeaceAndEnd(rig.war, rig.defender));
    assertFalse(rig.war.isActive());
    assertTrue(WhitePeaceService.recalculateProposals(rig.war).isEmpty());
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
  void absentOrUnchosenBattlesCannotSpendFuelOrPersistAChoice() {
    long writes = rig.warWrites();
    CampaignBattleEndService.beginPostBattleChoice(null, CampaignCoalition.AGGRESSOR);
    CampaignBattleEndService.beginPostBattleChoice(rig.war, null);
    CampaignBattleEndService.snapshotBattleStart(null);
    CampaignBattleEndService.snapshotBattleStart(null, CampaignCoalition.DEFENDER);
    CampaignBattleEndService.clearHoldPeace(null);
    CampaignBattleEndService.resolveChoicePhase(null);
    assertFalse(CampaignBattleEndService.applyPush(null));
    assertFalse(CampaignBattleEndService.applyPush(rig.war));
    assertFalse(CampaignBattleEndService.applyHold(null));
    assertFalse(CampaignBattleEndService.applyHold(rig.war));
    assertFalse(CampaignBattleEndService.applyLoserAttack(null));
    assertFalse(CampaignBattleEndService.applyLoserAttack(rig.war));
    assertFalse(CampaignChoiceService.applyPush(rig.war));
    assertFalse(CampaignChoiceService.applyHold(rig.war));
    assertFalse(CampaignChoiceService.applyLoserAttack(rig.war));
    assertFalse(
        CampaignPostBattleChoiceService.resolveMandatoryHoldIfNeeded(
            null, CampaignCoalition.AGGRESSOR));
    assertFalse(CampaignPostBattleChoiceService.isChoiceLeader(null, rig.attacker));
    assertNull(CampaignPostBattleChoiceService.choiceLeaderCoalition(rig.war));
    assertEquals(writes, rig.warWrites());
    assertEquals(5, rig.war.getInitiativeAttacker());
    assertEquals(1, rig.war.getCursorIndex());
  }

  @ParameterizedTest
  @CsvSource({
    "TOWARD_OBJECTIVE,AGGRESSOR,AGGRESSOR,1,2,TOWARD_OBJECTIVE,DEFENDER",
    "TOWARD_OBJECTIVE,AGGRESSOR,AGGRESSOR,3,3,RETAKE_OBJECTIVE,ATTACKER",
    "TOWARD_AGGRESSOR_CAPITAL,DEFENDER,DEFENDER,2,1,TOWARD_AGGRESSOR_CAPITAL,DEFENDER",
    "RETAKE_OBJECTIVE,DEFENDER,DEFENDER,3,3,TOWARD_OBJECTIVE,DEFENDER",
    "TOWARD_AGGRESSOR_CAPITAL,DEFENDER,AGGRESSOR,1,2,TOWARD_OBJECTIVE,DEFENDER",
    "TOWARD_OBJECTIVE,AGGRESSOR,DEFENDER,2,1,TOWARD_AGGRESSOR_CAPITAL,DEFENDER"
  })
  void applyingPushMatchesTheWinnerDirectionAndOnlySpendsTheRecordedOffensiveFuel(
      CampaignPushTarget before,
      CampaignCoalition offensive,
      CampaignCoalition winner,
      int cursor,
      int expectedCursor,
      CampaignPushTarget after,
      ObjectiveHolder holder) {
    rig.war.setPushTarget(before);
    rig.war.setCursorIndex(cursor);
    rig.war.setObjectiveHeldBy(
        before == CampaignPushTarget.RETAKE_OBJECTIVE
            ? ObjectiveHolder.ATTACKER
            : ObjectiveHolder.DEFENDER);
    CampaignCoalitionService.setInitiativeHolderCoalition(rig.war, offensive);
    CampaignBattleEndService.snapshotBattleStart(rig.war, offensive);
    CampaignBattleEndService.spendOffensiveFuel(rig.war);
    CampaignBattleEndService.beginPostBattleChoice(rig.war, winner);
    assertTrue(CampaignBattleEndService.applyPush(rig.war));
    assertEquals(expectedCursor, rig.war.getCursorIndex());
    assertEquals(after, rig.war.getPushTarget());
    assertEquals(holder, rig.war.getObjectiveHeldBy());
    assertEquals(
        CampaignCoalitionService.deriveLegacyPhaseFromPushTarget(after, holder),
        rig.war.getCampaignPhase());
    assertEquals(4, CampaignCoalitionService.getFuel(rig.war, offensive));
    assertEquals(5, CampaignCoalitionService.getFuel(rig.war, offensive.opposing()));
    assertEquals(winner, rig.war.getInitiativeHolderCoalition());
    assertTrue(rig.war.isPostBattleChoiceResolved());
    assertEquals(PostBattleChoicePhase.NONE, rig.war.getPostBattleChoicePhase());
  }

  @Test
  void legacyFuelAndMalformedPendingChoiceUseTheRecordedPublicStateSafely() {
    rig.war.setLastBattleOffensiveCoalition(null);
    CampaignBattleEndService.spendOffensiveFuel(rig.war);
    assertEquals(4, rig.war.getInitiativeAttacker());
    assertEquals(5, rig.war.getInitiativeDefender());
    rig.war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
    rig.war.setPostBattleChoiceResolved(false);
    rig.war.setPostBattleWinnerCoalition(null);
    assertFalse(CampaignPostBattleChoiceService.applyPushChoice(rig.war));
    assertFalse(CampaignPostBattleChoiceService.applyHoldChoice(rig.war));
    rig.war.setPostBattleChoicePhase(PostBattleChoicePhase.LOSER_ATTACK_PEACE);
    assertFalse(CampaignPostBattleChoiceService.applyLoserAttack(rig.war));
    assertNull(CampaignPostBattleChoiceService.choiceLeaderCoalition(rig.war));
    assertNull(PostBattleChoicePhase.fromJson("removed_phase"));
    assertEquals(PostBattleChoicePhase.NONE, PostBattleChoicePhase.fromJson(null));
    for (var phase : PostBattleChoicePhase.values())
      assertEquals(phase, PostBattleChoicePhase.fromJson(phase.toJson()));
  }

  @Test
  void deadlineHoldsForAnUnableWinnerThenAllowsTheLosingCoalitionToAttack() {
    army(rig.attacker, "guards", false, 2);
    army(rig.defender, "raiders", true, 2);
    CampaignBattleEndService.beginPostBattleChoice(rig.war, CampaignCoalition.AGGRESSOR);
    var deadline = BattleWindowService.atScheduleHour(Fixture.DAY, 15);
    assertTrue(CampaignPostBattleChoiceService.applyDeadlineIfDue(rig.war, deadline));
    assertTrue(CampaignPostBattleChoiceService.needsLoserResponse(rig.war));
    assertTrue(WhitePeaceService.recalculateProposals(rig.war).isEmpty());
    assertTrue(rig.war.isWhitePeaceProposedByAttacker());
    assertTrue(CampaignPostBattleChoiceService.applyDeadlineIfDue(rig.war, deadline));
    assertFalse(CampaignPostBattleChoiceService.needsAnyChoice(rig.war));
    assertEquals(CampaignCoalition.DEFENDER, rig.war.getInitiativeHolderCoalition());
  }

  @Test
  void aForfeitDeletesOnlyTheUnstartedBattleAndNotifiesBothCurrentOnlineSides() {
    army(rig.defender, "guards", true, 2);
    rig.war.setCursorIndex(2);
    var pending = BattleFactory.createBlank(BattleType.FIELD, "unstarted");
    pending.setWarId(rig.war.getId());
    pending.setProvinceId(12);
    BattleManager.addBattle(pending);
    assertTrue(CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(rig.war, 12));
    assertNull(BattleManager.getByString("unstarted"));
    assertEquals(1, rig.war.getCampaignBattlesFought());
    assertEquals(4, rig.war.getInitiativeAttacker());
    assertEquals(CampaignCoalition.DEFENDER, rig.war.getPostBattleWinnerCoalition());
    verify(rig.alice).sendMessage(contains("wins by forfeit"));
    verify(rig.bob).sendMessage(contains("wins by forfeit"));
    long writes = rig.warWrites();
    assertFalse(CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(rig.war, 12));
    assertFalse(CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(null, 12));
    assertFalse(CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(rig.war, 0));
    assertEquals(writes, rig.warWrites());
  }

  @Test
  void armedCoalitionsPreventAWalkoverWhileTwoEmptyArmiesEndInWhitePeace() {
    CampaignMilitaryWalkoverService.resolvePendingWalkovers(null);
    CampaignBattleEndService.beginPostBattleChoice(rig.war, CampaignCoalition.AGGRESSOR);
    CampaignMilitaryWalkoverService.resolvePendingWalkovers(rig.war);
    assertEquals(0, rig.war.getCampaignBattlesFought());
    CampaignBattleEndService.resolveChoicePhase(rig.war);
    CampaignMilitaryWalkoverService.resolvePendingWalkovers(rig.war);
    assertFalse(rig.war.isActive());
    assertEquals(WarEndReason.WHITE_PEACE, rig.war.getEndReason());
    assertEquals(0, rig.war.getCampaignBattlesFought());
  }

  @Test
  void aDefensiveOnlyArmyForfeitsOnceBeforeTheWinnerMustChoose() {
    army(rig.attacker, "guards", false, 2);
    army(rig.defender, "raiders", true, 2);
    rig.war.setCursorIndex(2);
    CampaignMilitaryWalkoverService.resolvePendingWalkovers(rig.war);
    assertEquals(1, rig.war.getCampaignBattlesFought());
    assertEquals(4, rig.war.getInitiativeAttacker());
    assertTrue(CampaignPostBattleChoiceService.needsWinnerChoice(rig.war));
    assertEquals(CampaignCoalition.DEFENDER, rig.war.getPostBattleWinnerCoalition());
  }

  @Test
  void aRetreatConcedesOneScheduledProvinceWithoutSpendingBattleFuel() {
    army(rig.attacker, "raiders", true, 2);
    army(rig.defender, "guards", true, 2);
    rig.defender.addProvince(11);
    rig.war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, false, null),
            new ScheduledCampaignBattle(12, CampaignBattleKind.FIELD, false, null)));
    var early = BattleWindowService.atScheduleHour(Fixture.DAY, 12);
    assertEquals(
        CampaignRetreatService.RetreatResult.REJECTED_NOT_LEADER,
        CampaignRetreatService.concedeActiveSlot(rig.war, rig.attacker, early).result());
    var result = CampaignRetreatService.concedeActiveSlot(rig.war, rig.defender, early);
    assertEquals(CampaignRetreatService.RetreatResult.SUCCESS, result.result());
    assertTrue(result.autoEndReason().isEmpty());
    assertEquals(1, rig.war.getCampaignScheduleIndex());
    assertTrue(
        CampaignRetreatService.isSlotConceded(
            rig.war,
            net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService.ScheduleLeg
                .INVASION,
            0));
    assertEquals(2, rig.war.getCursorIndex());
    assertEquals(5, rig.war.getInitiativeAttacker());
    assertEquals(0, rig.war.getCampaignBattlesFought());
    assertTrue(rig.war.getOccupiedByAttacker().contains(11));
  }

  @Test
  void retreatRejectsMissingClosedAndUnresolvedContextsWithoutChangingOccupation() {
    var early = BattleWindowService.atScheduleHour(Fixture.DAY, 12);
    assertFalse(CampaignRetreatService.canRetreat(null, rig.defender, early));
    assertNull(CampaignRetreatService.pushedCoalition(null));
    assertNull(CampaignRetreatService.slotKey(null, 0));
    assertFalse(CampaignRetreatService.isSlotConceded(null, null, -1));
    assertEquals(
        CampaignRetreatService.RetreatResult.REJECTED_NO_ACTIVE_SLOT,
        CampaignRetreatService.concedeActiveSlot(rig.war, rig.defender, early).result());
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, false, null)));
    rig.war.setCampaignScheduleIndex(1);
    assertEquals(
        CampaignRetreatService.RetreatResult.REJECTED_NO_ACTIVE_SLOT,
        CampaignRetreatService.concedeActiveSlot(rig.war, rig.defender, early).result());
    assertEquals(1, rig.war.getCursorIndex());
    assertEquals(0, rig.war.getCampaignBattlesFought());
  }

  @Test
  void concedingTheCapitalReturnsTheActualWarEndInsteadOfReopeningVoting() {
    army(rig.attacker, "raiders", true, 2);
    army(rig.defender, "guards", true, 2);
    rig.defender.addProvince(20);
    rig.defender.setCapital(20, true);
    rig.war.setCursorIndex(3);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null)));
    var result =
        CampaignRetreatService.concedeActiveSlot(
            rig.war, rig.defender, BattleWindowService.atScheduleHour(Fixture.DAY, 12));
    assertEquals(CampaignRetreatService.RetreatResult.SUCCESS, result.result());
    assertEquals(WarEndReason.ATTACKER_VICTORY, result.autoEndReason().orElseThrow());
    assertFalse(rig.war.isActive());
    assertEquals(5, rig.war.getInitiativeAttacker());
  }

  @Test
  void longUncontestedRoutesStopAtTheBoundedWalkoverBatchWithoutRepeatingAnySlot() {
    army(rig.attacker, "raiders", true, 2);
    List<Integer> route = java.util.stream.IntStream.rangeClosed(100, 140).boxed().toList();
    for (int province : route) {
      rig.defender.addProvince(province);
      rig.domain.provinceData.put(
          province,
          new net.tfminecraft.simplefactions.map.provinces.Province(
              province, "PLAINS", 40, province * 16, 0));
    }
    rig.war.setCampaignProvinces(route);
    rig.war.setCursorIndex(0);
    rig.war.setObjectiveProvinceId(140);
    rig.war.setInitiativeAttacker(100);
    rig.war.setInitiativeDefender(100);
    rig.war.setCampaignBattleSchedule(
        route.stream()
            .map(id -> new ScheduledCampaignBattle(id, CampaignBattleKind.FIELD, id == 140, null))
            .toList());
    CampaignMilitaryWalkoverService.resolvePendingWalkovers(rig.war);
    assertTrue(rig.war.isActive());
    assertEquals(32, rig.war.getCampaignBattlesFought());
    assertEquals(32, rig.war.getCampaignScheduleIndex());
    assertEquals(68, rig.war.getInitiativeAttacker());
    assertEquals(32, rig.war.getOccupiedByAttacker().size());
    assertTrue(rig.war.getOccupiedByAttacker().containsAll(route.subList(0, 32)));
    assertFalse(rig.war.getOccupiedByAttacker().contains(132));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aMalformedProgrammaticSlotCannotConsumeCampaignState(boolean offensiveArmy) {
    army(rig.attacker, "guards", offensiveArmy, 2);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(0, CampaignBattleKind.FIELD, false, null)));
    List<Integer> attackerOccupation = List.copyOf(rig.war.getOccupiedByAttacker());
    List<Integer> defenderOccupation = List.copyOf(rig.war.getOccupiedByDefender());
    List<Integer> lastOccupation =
        rig.war.getLastBattleOccupied() == null
            ? null
            : List.copyOf(rig.war.getLastBattleOccupied());
    long writes = rig.warWrites();

    CampaignMilitaryWalkoverService.resolvePendingWalkovers(rig.war);

    assertAll(
        () -> assertTrue(rig.war.isActive()),
        () -> assertEquals(5, rig.war.getInitiativeAttacker(), "attacker fuel"),
        () -> assertEquals(5, rig.war.getInitiativeDefender(), "defender fuel"),
        () -> assertEquals(0, rig.war.getCampaignBattlesFought(), "fought battle count"),
        () -> assertEquals(0, rig.war.getCampaignScheduleIndex(), "schedule cursor"),
        () -> assertEquals(1, rig.war.getCursorIndex(), "route cursor"),
        () -> assertEquals(attackerOccupation, rig.war.getOccupiedByAttacker()),
        () -> assertEquals(defenderOccupation, rig.war.getOccupiedByDefender()),
        () -> assertEquals(lastOccupation, rig.war.getLastBattleOccupied()),
        () -> assertFalse(rig.war.isWhitePeaceProposedByAttacker()),
        () -> assertFalse(rig.war.isWhitePeaceProposedByDefender()),
        () -> assertEquals(writes, rig.warWrites()));
  }
}
