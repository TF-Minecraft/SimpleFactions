package net.tfminecraft.simplefactions.war.campaign.raid;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.*;
import net.tfminecraft.simplefactions.war.campaign.raid.RaidTargetService.RaidKind;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidLaunchService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignRaidBoundaryCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
  }

  @AfterEach
  void close() throws Exception {
    if (rig != null) rig.close();
  }

  private CampaignRaid muster() {
    assertEquals(
        LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    return CampaignRaidService.getActive(rig.war);
  }

  @Test
  void changingBattleDayReleasesTheOldMusterAndItsMembers() {
    CampaignRaid raid = muster();
    assertEquals(
        JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war, rig.alice.getUniqueId(), "Alice", rig.attacker, raid.getId(), rig.now()));
    var band = CampaignRaidWarbandService.getAttackerWarband(raid);
    rig.war.setBattleDay(Fixture.DAY.plusDays(1));

    CampaignRaidService.syncBattleDay(rig.war);

    assertNull(rig.war.getActiveCampaignRaid());
    assertNull(WarbandManager.getByString(band.getId()));
    assertNull(WarbandManager.getByMemberId(rig.alice.getUniqueId()));
    assertEquals(CampaignRaidState.ENDED, raid.getState());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clearingABattleDayStopsItsRunningRaidAndReleasesPlayers(boolean explicitClear) {
    CampaignRaid raid = muster();
    assertEquals(
        JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war, rig.alice.getUniqueId(), "Alice", rig.attacker, raid.getId(), rig.now()));
    rig.time(raid.getMusterEndsAt());
    CampaignRaidLaunchService.startFight(rig.war, rig.now());
    var battle = BattleManager.getByString(raid.getBattleId());
    assertTrue(battle.hasStarted());
    assertTrue(battle.getAllParticipants().contains(rig.alice));

    if (explicitClear) CampaignRaidService.clearForNewBattleDay(rig.war);
    else {
      rig.war.setBattleDay(null);
      CampaignRaidService.syncBattleDay(rig.war);
    }

    assertNull(rig.war.getActiveCampaignRaid());
    assertFalse(battle.hasStarted());
    assertNull(BattleManager.getBattleByPlayer(rig.alice));
    assertNull(BattleManager.getByString(battle.getId()));
    assertNull(WarbandManager.getByMemberId(rig.alice.getUniqueId()));
    assertEquals(CampaignRaidState.ENDED, raid.getState());
  }

  @Test
  void theMusterDeadlineClosesEnrollmentEvenBeforeTheSchedulerCallbackRuns() {
    CampaignRaid raid = muster();
    rig.time(raid.getMusterEndsAt());

    assertEquals(
        JoinResult.REJECTED_NOT_MUSTER,
        CampaignRaidJoinService.join(
            rig.war, rig.alice.getUniqueId(), "Alice", rig.attacker, raid.getId(), rig.now()));
    assertTrue(raid.getMusterParticipantIds().isEmpty());
    assertFalse(CampaignRaidWarbandService.getAttackerWarband(raid).hasMember(rig.alice));
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
  }

  @Test
  void missingWarAndRequestInputsHaveNoSideEffects() {
    CampaignRaidService.syncBattleDay(null);
    CampaignRaidService.endRaid(null, rig.now());
    CampaignRaidService.clearForNewBattleDay(null);
    assertNull(CampaignRaidService.getActive(null));
    assertEquals(0, CampaignRaidService.resetRaidQuota(null, null));
    assertEquals(
        TransitionResult.REJECTED_NO_ACTIVE_RAID,
        CampaignRaidService.transitionToFighting(null, rig.now()));
    assertEquals(
        TransitionResult.REJECTED_NO_ACTIVE_RAID,
        CampaignRaidService.transitionToFighting(rig.war, null));
    assertEquals(
        TransitionResult.REJECTED_NO_ACTIVE_RAID,
        CampaignRaidService.transitionToFighting(rig.war, rig.now()));
    assertEquals(
        LaunchResult.REJECTED_NOT_PARTICIPANT,
        CampaignRaidService.beginMuster(rig.war, null, null, null, rig.now()));
    assertEquals(
        LaunchResult.REJECTED_WAR_INACTIVE,
        CampaignRaidService.canLaunch(rig.war, rig.attacker, null));
    assertNull(rig.war.getActiveCampaignRaid());
    assertTrue(WarbandManager.get().isEmpty());
  }

  @Test
  void launchingOutsideTheWindowOrWithoutABattleDayIsRejected() {
    assertEquals(
        LaunchResult.REJECTED_OUTSIDE_WINDOW,
        CampaignRaidService.beginMuster(
            rig.war,
            rig.attacker,
            rig.source.getId(),
            rig.target.getId(),
            rig.now().minusSeconds(7200)));
    rig.war.setBattleDay(null);
    assertEquals(
        LaunchResult.REJECTED_WAR_INACTIVE,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    assertNull(rig.war.getActiveCampaignRaid());
  }

  @Test
  void quotasAreCoalitionScopedAndResetDoesNotRemoveRepairLocks() {
    String day = Fixture.DAY.toString();
    rig.war.getCampaignRaidsUsed().put("aggressor", day);
    rig.war.getCampaignRaidsUsed().put("defender", day);
    Instant lock = rig.now().plusSeconds(60);
    CampaignRaidService.setInstallationRepairLockUntil(rig.war, rig.target, lock);
    assertEquals(
        LaunchResult.REJECTED_QUOTA_SPENT,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    assertEquals(2, CampaignRaidService.resetRaidQuota(rig.war, null));
    assertEquals(0, CampaignRaidService.resetRaidQuota(rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(CampaignRaidService.isInstallationRepairLocked(rig.war, rig.target, rig.now()));
    assertEquals(Map.of(rig.target.getStableKey(), lock), rig.war.getRaidRepairLockUntil());
    assertFalse(CampaignRaidService.isSideQuotaUsed(null, CampaignCoalition.AGGRESSOR));
  }

  @Test
  void aHiddenMusterIsVisibleOnlyToItsCoalitionAndCannotBeStartedTwice() {
    CampaignRaid raid = muster();
    assertFalse(CampaignRaidService.isMusterHiddenFromFaction(rig.war, rig.attacker));
    assertTrue(CampaignRaidService.isMusterHiddenFromFaction(rig.war, rig.defender));
    assertTrue(CampaignRaidService.isMusterHiddenFromFaction(rig.war, null));
    assertEquals(
        LaunchResult.REJECTED_RAID_IN_PROGRESS,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    assertEquals(TransitionResult.OK, CampaignRaidService.transitionToFighting(rig.war, rig.now()));
    assertEquals(
        TransitionResult.REJECTED_WRONG_STATE,
        CampaignRaidService.transitionToFighting(rig.war, rig.now()));
    assertSame(raid, rig.war.getActiveCampaignRaid());
    assertFalse(CampaignRaidService.isMusterHiddenFromFaction(rig.war, rig.defender));
  }

  @Test
  void invalidRepairLockRequestsDoNotPoisonLiveLocks() {
    CampaignRaidService.setRepairLockUntil(null, "port", rig.now());
    CampaignRaidService.setRepairLockUntil(rig.war, " ", rig.now());
    CampaignRaidService.setRepairLockUntil(rig.war, "port", null);
    CampaignRaidService.setInstallationRepairLockUntil(rig.war, null, rig.now());
    assertFalse(CampaignRaidService.isRepairLocked(null, "port", rig.now()));
    assertFalse(CampaignRaidService.isRepairLocked(rig.war, "", rig.now()));
    assertFalse(CampaignRaidService.isRepairLocked(rig.war, "port", null));
    assertFalse(CampaignRaidService.isInstallationRepairLocked(rig.war, null, rig.now()));
    assertNull(CampaignRaidService.repairLockUntilFromStart(null));
    assertTrue(rig.war.getRaidRepairLockUntil().isEmpty());
  }

  @Test
  void legacyRepairLocksApplyOnlyToInstallationsHeldByThisWarsParticipants() {
    var neutral = rig.domain.saved("legacy_lock_neutral", "Neutral");
    var foreign = rig.install(neutral, rig.target.getId(), InstallationKind.PORT, 40);
    CampaignRaidService.setRepairLockUntil(rig.war, rig.target.getId(), rig.now().plusSeconds(60));
    assertTrue(CampaignRaidService.isInstallationRepairLocked(rig.war, rig.target, rig.now()));
    assertFalse(CampaignRaidService.isInstallationRepairLocked(rig.war, foreign, rig.now()));
    assertFalse(CampaignRaidService.isRepairLocked(rig.war, "unregistered_port", rig.now()));
    assertFalse(
        CampaignRaidService.isRepairLocked(rig.war, rig.target.getId(), rig.now().plusSeconds(60)));
    CampaignRaidService.setInstallationRepairLockUntil(
        rig.war, rig.source, rig.now().plusSeconds(90));
    assertTrue(CampaignRaidService.isRepairLocked(rig.war, rig.source.getId(), rig.now()));
  }

  @Test
  void anActiveRaidReservesItsIdentityBeforeItsWarbandsHaveBeenRestored() {
    CampaignRaid raid = muster();
    CampaignRaidWarbandService.destroyRaidWarbands(rig.war, raid);
    assertTrue(WarbandManager.get().isEmpty());
    String next =
        CampaignRaidService.resolveUniqueRaidId(raid.getDisplayName(), rig.war.getId() + 1);
    assertEquals(raid.getId() + "_w" + (rig.war.getId() + 1), next);
    assertSame(raid, CampaignRaidService.getActive(rig.war));
  }

  @Test
  void staleAndNeutralFactionRequestsReturnNoSourcesOrTargets() {
    var neutral = rig.domain.saved("raid_boundary_neutral", "Neutral");
    for (String id : List.of("missing-faction", neutral.getId())) {
      assertTrue(CampaignRaidEligibilityService.listValidSources(rig.war, id, rig.now()).isEmpty());
      assertTrue(
          CampaignRaidEligibilityService.listValidTargets(rig.war, id, "port", rig.now())
              .isEmpty());
      assertFalse(
          CampaignRaidEligibilityService.isValidTarget(rig.war, id, "port", "port", rig.now()));
      assertFalse(
          CampaignRaidEligibilityService.isValidTargetForRaidKind(
              rig.war, id, "port", RaidKind.NAVAL, rig.now()));
      assertTrue(RaidTargetService.listValidTargets(rig.war, id, RaidKind.NAVAL).isEmpty());
    }
    assertNull(CampaignRaidService.coalitionForFaction(rig.war, neutral));
    assertTrue(CampaignRaidJoinService.listJoinableRaidIds(neutral).isEmpty());
    assertEquals(
        LaunchResult.REJECTED_NOT_PARTICIPANT,
        CampaignRaidService.beginMuster(rig.war, neutral, "port", "port", rig.now()));
  }

  @Test
  void targetsRequireCompatibleSourceKindsAndActualEnemyInstallations() {
    var airport = rig.install(rig.attacker, "launch_airport", InstallationKind.AIRPORT, 30);
    var station = rig.install(rig.defender, "target_station", InstallationKind.TRAIN_STATION, 31);
    assertEquals(
        ValidateLaunchResult.REJECTED_KIND_MISMATCH,
        CampaignRaidEligibilityService.validateLaunch(
                rig.war, rig.attacker.getId(), airport.getId(), rig.target.getId(), rig.now())
            .result());
    assertEquals(
        ValidateLaunchResult.REJECTED_INVALID_TARGET,
        CampaignRaidEligibilityService.validateLaunch(
                rig.war, rig.attacker.getId(), airport.getId(), station.getId(), rig.now())
            .result());
    assertEquals(
        ValidateLaunchResult.REJECTED_INVALID_SOURCE,
        CampaignRaidEligibilityService.validateLaunch(
                rig.war, rig.attacker.getId(), "", rig.target.getId(), rig.now())
            .result());
    assertTrue(
        CampaignRaidEligibilityService.listValidTargets(
                rig.war, rig.attacker.getId(), "missing", rig.now())
            .isEmpty());
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, rig.attacker.getId(), rig.target.getId(), RaidKind.AIR, rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war,
            rig.attacker.getId(),
            rig.target.getId(),
            RaidKind.NAVAL,
            rig.now().minusSeconds(7200)));
    assertNull(CampaignRaidEligibilityService.inferRaidKind(null, InstallationKind.PORT));
    assertNull(CampaignRaidEligibilityService.resolveSourceInstallation(null));
    assertNull(
        CampaignRaidEligibilityService.resolveTargetInstallation(rig.war, (CampaignRaid) null));
  }

  @Test
  void theLegacyTargetListingAgreesWithTheAuthoritativeValidator() {
    assertTrue(
        RaidTargetService.isValidTarget(
            rig.war, rig.attacker.getId(), rig.target.getId(), RaidKind.NAVAL));
    assertEquals(
        List.of(rig.target),
        RaidTargetService.listValidTargets(rig.war, rig.attacker.getId(), RaidKind.NAVAL).stream()
            .map(RaidTargetService.RaidTargetCandidate::installation)
            .toList());
    assertTrue(
        RaidTargetService.listValidTargets(null, rig.attacker.getId(), RaidKind.NAVAL).isEmpty());
    assertTrue(
        RaidTargetService.listValidTargets(
                rig.war, rig.attacker.getId(), RaidKind.NAVAL, rig.now().minusSeconds(7200))
            .isEmpty());
    assertEquals(InstallationKind.PORT, RaidKind.NAVAL.getInstallationKind());
  }

  @Test
  void joinDiscoveryHandlesMissingAndNonMusterRaids() {
    assertNull(CampaignRaidJoinService.findWarByRaidId(null));
    assertNull(CampaignRaidJoinService.findWarByRaidId("missing"));
    assertFalse(CampaignRaidJoinService.matchesRaidJoinId(null, "missing"));
    assertTrue(CampaignRaidJoinService.listJoinableRaidIds(null).isEmpty());
    assertEquals(
        JoinResult.REJECTED_RAID_NOT_FOUND,
        CampaignRaidJoinService.join(
            null, rig.alice.getUniqueId(), "Alice", rig.attacker, "missing", rig.now()));
    CampaignRaid raid = muster();
    assertSame(rig.war, CampaignRaidJoinService.findWarByRaidId(raid.getId()));
    assertTrue(
        CampaignRaidJoinService.matchesRaidJoinId(
            raid,
            net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService
                .slugifyDisplayName(raid.getDisplayName())));
    raid.setState(CampaignRaidState.FIGHTING);
    assertTrue(CampaignRaidJoinService.listJoinableRaidIds(rig.attacker).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void raidJoinLookupPrefersUniqueIdsAndRejectsAmbiguousDisplayAliases(boolean reserveAlias) {
    String alias =
        net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService.slugifyDisplayName(
            net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService
                .buildRaidDisplayName(rig.war, rig.target));
    var manual =
        net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory.createBlank(
            net.tfminecraft.simplefactions.war.battle.enums.BattleType.FIELD, alias);
    if (reserveAlias) {
      BattleManager.addBattle(manual);
      BattleManager.addBattle(
          net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory.createBlank(
              net.tfminecraft.simplefactions.war.battle.enums.BattleType.FIELD,
              alias + "_w" + rig.war.getId()));
    }
    CampaignRaid first = muster();
    var secondWar =
        new net.tfminecraft.simplefactions.war.core.War(
            rig.war.getId() + 1, rig.attacker, rig.defender);
    secondWar.setGoal(rig.war.getGoal());
    secondWar.setWarType(rig.war.getWarType());
    secondWar.setBattleDay(Fixture.DAY);
    secondWar.setBattleSchedulePhase(rig.war.getBattleSchedulePhase());
    secondWar.setOccupiedByAttacker(new java.util.ArrayList<>());
    secondWar.setOccupiedByDefender(new java.util.ArrayList<>());
    net.tfminecraft.simplefactions.managers.WarManager.addWar(secondWar);
    assertEquals(
        LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            secondWar, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid second = CampaignRaidService.getActive(secondWar);
    assertTrue(
        CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(
            CampaignRaidWarbandService.getAttackerWarband(second), rig.bob));
    assertFalse(
        CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(
            CampaignRaidWarbandService.getAttackerWarband(first), rig.alice));
    assertEquals(first.getDisplayName(), second.getDisplayName());
    assertNotEquals(first.getId(), second.getId());
    assertSame(rig.war, CampaignRaidJoinService.findWarByRaidId(first.getId()));
    assertSame(secondWar, CampaignRaidJoinService.findWarByRaidId(second.getId()));
    if (reserveAlias) {
      assertNull(CampaignRaidJoinService.findWarByRaidId(alias));
      assertSame(manual, BattleManager.getByString(alias));
      assertNull(manual.getWarId());
    } else {
      assertSame(rig.war, CampaignRaidJoinService.findWarByRaidId(alias));
    }
  }

  @Test
  void malformedInstallationIdsStayOutOfSourcesAndTargets() {
    rig.attacker
        .getInstallationHandler()
        .acceptTransferred(
            new net.tfminecraft.simplefactions.installation.Installation(
                null, "Unnamed source", InstallationKind.PORT, 30, 300, 0, 1L));
    rig.defender
        .getInstallationHandler()
        .acceptTransferred(
            new net.tfminecraft.simplefactions.installation.Installation(
                null, "Unnamed target", InstallationKind.PORT, 40, 400, 0, 1L));
    assertEquals(
        List.of(rig.source),
        CampaignRaidEligibilityService.listValidSources(rig.war, rig.attacker.getId(), rig.now()));
    assertEquals(
        List.of(rig.target),
        CampaignRaidEligibilityService.listValidTargets(
                rig.war, rig.attacker.getId(), rig.source.getId(), rig.now())
            .stream()
            .map(RaidTargetService.RaidTargetCandidate::installation)
            .toList());
    assertEquals(
        List.of(rig.target),
        RaidTargetService.listValidTargets(rig.war, rig.attacker.getId(), RaidKind.NAVAL).stream()
            .map(RaidTargetService.RaidTargetCandidate::installation)
            .toList());
  }

  @Test
  void inconsistentBelligerentMembershipNeverAllowsRaidingTheSameSide() {
    rig.war
        .getAttackers()
        .getMainParticipants()
        .add(new net.tfminecraft.simplefactions.war.core.Participant(rig.defender));
    assertFalse(
        CampaignRaidEligibilityService.isValidTarget(
            rig.war, rig.attacker.getId(), rig.source.getId(), rig.target.getId(), rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, rig.attacker.getId(), rig.target.getId(), RaidKind.NAVAL, rig.now()));
    assertEquals(
        ValidateLaunchResult.REJECTED_INVALID_TARGET,
        CampaignRaidEligibilityService.validateLaunch(
                rig.war, rig.attacker.getId(), rig.source.getId(), rig.target.getId(), rig.now())
            .result());
    assertNull(rig.war.getActiveCampaignRaid());
  }

  @Test
  void missingContextMessagesStillExplainTheCorrectPlayerAction() {
    assertNull(CampaignRaidMessages.messageForLaunchResult(LaunchResult.STARTED));
    assertNull(CampaignRaidMessages.messageForValidateResult(ValidateLaunchResult.OK));
    assertNull(CampaignRaidMessages.messageForJoinResult(JoinResult.OK));
    assertNull(CampaignRaidMessages.messageForValidateResult(null));
    assertTrue(
        CampaignRaidMessages.messageForValidateResult(ValidateLaunchResult.REJECTED_WAR_INACTIVE)
            .contains("War not found"));
    assertTrue(
        CampaignRaidMessages.messageForValidateResult(ValidateLaunchResult.REJECTED_OUTSIDE_WINDOW)
            .contains("19:00"));
    assertTrue(
        CampaignRaidMessages.messageForValidateResult(ValidateLaunchResult.REJECTED_QUOTA_SPENT)
            .contains("already launched"));
    assertTrue(
        CampaignRaidMessages.messageForValidateResult(
                ValidateLaunchResult.REJECTED_RAID_IN_PROGRESS)
            .contains("already in progress"));
    assertTrue(
        CampaignRaidMessages.messageForJoinResult(JoinResult.REJECTED_NOT_MUSTER)
            .contains("muster has ended"));
    assertTrue(
        CampaignRaidMessages.messageForJoinResult(JoinResult.REJECTED_IN_WARBAND)
            .contains("Leave your warband"));
    assertTrue(
        CampaignRaidMessages.buildRaidStartedMessage(rig.target, null)
            .contains(rig.target.getName()));
    assertTrue(CampaignRaidMessages.buildRaidStartedMessage(null, null).contains("Campaign raid"));
    assertTrue(
        CampaignRaidMessages.buildRaidEndedMessage(rig.target).contains(rig.target.getName()));
    assertTrue(CampaignRaidMessages.buildRaidEndedMessage(null).contains("has ended"));
  }

  @Test
  void legacyNullMusterCollectionsCanBeRestoredAndMutatedIndependently() {
    CampaignRaid first =
        new Gson()
            .fromJson(
                "{\"musterParticipantIds\":null,\"musterRemindersSent\":null}", CampaignRaid.class);
    CampaignRaid second =
        new Gson()
            .fromJson(
                "{\"musterParticipantIds\":null,\"musterRemindersSent\":null}", CampaignRaid.class);
    first.getMusterParticipantIds().add(rig.alice.getUniqueId().toString());
    first.getMusterRemindersSent().add(30);
    assertTrue(second.getMusterParticipantIds().isEmpty());
    assertTrue(second.getMusterRemindersSent().isEmpty());
    assertEquals(1, first.getMusterParticipantIds().size());
    assertEquals(1, first.getMusterRemindersSent().size());
  }
}
