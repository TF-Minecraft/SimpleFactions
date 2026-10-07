package net.tfminecraft.simplefactions.war.campaign.runtime.pick;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.CampaignRaidData;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults.InstallationPickToggleResult;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoterEligibility;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleHourTally;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleVoteToggleResult;
import net.tfminecraft.simplefactions.war.campaign.zoc.PortSeaZocIndex;
import net.tfminecraft.simplefactions.war.campaign.zoc.PortSeaZocIndex.OperationalPort;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignPickingBoundaryCoverageTest {
  private Fixture rig;
  private ProvinceManager manager;
  private final Map<Integer, Province> provinces = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    for (String name :
        List.of("warBattleWindowStartHour", "warBattleWindowEndHour", "warPortSeaZocRadius"))
      rig.remember(Cache.class, name);
    Cache.warBattleWindowStartHour = 20;
    Cache.warBattleWindowEndHour = 24;
    Cache.warPortSeaZocRadius = 4;
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 10));
    manager = new ProvinceManager();
    manager.start(provinces);
    when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(manager);
    province(10, "PLAINS");
    province(15, "SEA");
    province(20, "PLAINS");
    province(30, "PLAINS");
    link(10, 15);
    link(15, 20);
    link(15, 30);
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void compulsoryNavalPortBelongsToItsActualOwnerOnEitherCampaignLeg(boolean counterAttack) {
    rig.attacker.getInstallationHandler().detachOnProvince(10);
    rig.defender.getInstallationHandler().detachOnProvince(20);
    Faction owner;
    if (counterAttack) {
      owner = rig.attacker;
    } else {
      owner = rig.domain.saved("coastal_subject", "Cara");
      rig.domain.subject(rig.defender, owner);
    }
    int land = counterAttack ? 10 : 30;
    owner.addProvince(land);
    Installation port = rig.install(owner, "compulsory_port", InstallationKind.PORT, land);
    War war = navalWar(counterAttack, port);
    assertSame(counterAttack ? war.getAttackers() : war.getDefenders(), war.getSide(owner));
    assertSame(owner, PortSeaZocIndex.fromGameState().portForSeaProvince(15).orElseThrow().owner());
    assertEquals(
        port.getId(),
        CampaignScheduleService.slotAtActiveIndex(war).orElseThrow().portInstallationId());

    BattleInstallationPickService.ensureDefenderZocPort(war);

    assertEquals(Set.of(port.getId()), BattleInstallationPickService.getPicks(war, owner.getId()));
    assertEquals(
        Map.of(owner.getId(), Set.of(port.getId())),
        BattleInstallationPickService.getAllPicks(war));
    assertTrue(BattleInstallationPickService.isDefenderZocPort(war, owner, port.getId()));
    assertEquals(
        InstallationPickToggleResult.REJECTED_ZOC_PORT,
        BattleInstallationPickService.togglePick(
            war, owner, owner.getLeader(), port.getId(), rig.now()));
    assertTrue(BattleInstallationInPlayService.isInPlay(war, owner.getId(), port.getId()));
    assertTrue(BattleInstallationPickService.getPicks(war, rig.defender.getId()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ended", "IDLE", "SCHEDULED", "AUTORESOLVE_PENDING"})
  void votesCannotChangeOnceTheWarOrItsVotingPhaseHasClosed(String state) {
    rig.war.getBattleVotes().put(rig.alice.getUniqueId(), new java.util.HashSet<>(Set.of(20)));
    if (state.equals("ended")) rig.war.end(WarEndReason.ADMIN_END);
    else rig.war.setBattleSchedulePhase(BattleSchedulePhase.valueOf(state));
    assertFalse(BattleVoterEligibility.canToggleVote(rig.war, rig.attacker, rig.now()));

    BattleVoteToggleResult result =
        BattleVoteService.toggleVote(rig.war, rig.alice.getUniqueId(), 21, rig.attacker, true);

    assertTrue(
        Set.of(
                BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT,
                BattleVoteToggleResult.REJECTED_VOTE_CLOSED)
            .contains(result),
        "The public vote operation must reject a closed voting session");
    assertEquals(Map.of(rig.alice.getUniqueId(), Set.of(20)), rig.war.getBattleVotes());
  }

  @Test
  void invalidPickRequestsDoNotReplaceExistingSelectionsAndSnapshotsAreIndependent() {
    assertEquals(
        InstallationPickToggleResult.ADDED,
        BattleInstallationPickService.togglePick(
            rig.war, rig.attacker, "Alice", rig.source.getId(), rig.now()));
    Map<String, Set<String>> snapshot = BattleInstallationPickService.getAllPicks(rig.war);
    assertEquals(
        InstallationPickToggleResult.REJECTED_WAR_INACTIVE,
        BattleInstallationPickService.togglePick(
            null, rig.attacker, "Alice", rig.source.getId(), rig.now()));
    assertEquals(
        InstallationPickToggleResult.REJECTED_INVALID_INSTALLATION,
        BattleInstallationPickService.togglePick(rig.war, rig.attacker, "Alice", " ", rig.now()));
    assertTrue(BattleInstallationPickService.getPicks(null, rig.attacker.getId()).isEmpty());
    assertTrue(BattleInstallationPickService.getPicks(rig.war, null).isEmpty());
    assertTrue(BattleInstallationPickService.getAllPicks(null).isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    assertThrows(
        UnsupportedOperationException.class, () -> snapshot.get(rig.attacker.getId()).clear());
    BattleInstallationPickService.clearForNewBattleDay(null);
    BattleInstallationPickService.ensureDefenderZocPort(null);
    BattleInstallationPickService.syncBattleDay(null);
    assertFalse(
        BattleInstallationPickService.isDefenderZocPort(null, rig.attacker, rig.source.getId()));
    assertNull(BattleInstallationPickService.defenderZocPortId(null));
    assertEquals(
        Set.of(rig.source.getId()),
        BattleInstallationPickService.getPicks(rig.war, rig.attacker.getId()));
    BattleInstallationPickService.clearForNewBattleDay(rig.war);
    assertTrue(BattleInstallationPickService.getPicks(rig.war, rig.attacker.getId()).isEmpty());
    assertEquals(Map.of(rig.attacker.getId(), Set.of(rig.source.getId())), snapshot);
  }

  @Test
  void portCoverageSkipsIncompleteImportedRowsWithoutLosingTheValidPort() {
    OperationalPort valid = new OperationalPort("valid", rig.attacker, 10, 1L);
    OperationalPort missingId = new OperationalPort(null, rig.defender, 20, 1L);
    PortSeaZocIndex index = PortSeaZocIndex.fromPorts(Arrays.asList(missingId, null, valid));
    assertEquals(valid, index.portForSeaProvince(15).orElseThrow());
    assertEquals(List.of(valid), index.portsCoveringSeaProvinces(List.of(15, 15)));
  }

  @Test
  void portCoverageRespectsRadiusAndMissingMapStateWithoutChangingInstallationOwnership() {
    OperationalPort valid = new OperationalPort("valid", rig.attacker, 10, 1L);
    assertTrue(PortSeaZocIndex.fromPorts(List.of()).portForSeaProvince(15).isEmpty());
    assertTrue(PortSeaZocIndex.fromPorts(null).portsCoveringSeaProvinces(null).isEmpty());
    assertTrue(
        PortSeaZocIndex.fromPorts(List.of(valid)).portsCoveringSeaProvinces(List.of()).isEmpty());
    Cache.warPortSeaZocRadius = 0;
    assertTrue(PortSeaZocIndex.fromPorts(List.of(valid)).portForSeaProvince(15).isEmpty());
    Cache.warPortSeaZocRadius = 4;
    assertTrue(
        PortSeaZocIndex.fromPorts(List.of(new OperationalPort("missing", rig.attacker, 999, 1L)))
            .portForSeaProvince(15)
            .isEmpty());
    when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(null);
    assertTrue(PortSeaZocIndex.fromPorts(List.of(valid)).portForSeaProvince(15).isEmpty());
    assertSame(rig.source, rig.attacker.getInstallationHandler().getById(rig.source.getId()));
  }

  @Test
  void enemyPicksBecomeVisibleAtTheCutoffButRemainImmutableAndHiddenFromOutsiders() {
    assertEquals(
        InstallationPickToggleResult.ADDED,
        BattleInstallationPickService.togglePick(rig.war, rig.defender, "Bob", rig.target.getId()));
    Faction outsider = rig.domain.saved("outsider", "Cara");
    assertTrue(
        BattleInstallationPickService.getVisibleEnemyPicks(rig.war, rig.attacker.getId())
            .isEmpty());
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 16));
    Map<String, Set<String>> shown =
        BattleInstallationPickService.getVisibleEnemyPicks(rig.war, rig.attacker.getId());
    assertEquals(Map.of(rig.defender.getId(), Set.of(rig.target.getId())), shown);
    assertThrows(UnsupportedOperationException.class, shown::clear);
    assertTrue(
        BattleInstallationPickService.getVisibleEnemyPicks(rig.war, outsider.getId()).isEmpty());
    assertTrue(
        BattleInstallationPickService.getVisibleEnemyPicks(null, rig.attacker.getId()).isEmpty());
    assertTrue(BattleInstallationPickService.getVisibleEnemyPicks(rig.war, " ").isEmpty());
    assertTrue(BattleInstallationPickService.getVisibleEnemyPicks(rig.war, "deleted").isEmpty());
    assertEquals(
        InstallationPickToggleResult.REJECTED_LOCKED,
        BattleInstallationPickService.togglePick(rig.war, rig.defender, "Bob", rig.target.getId()));
    assertEquals(Map.of(rig.defender.getId(), Set.of(rig.target.getId())), shown);
  }

  @Test
  void invalidAndIncompleteInstallationSelectionsDoNotHideValidSiblings() {
    assertEquals(
        InstallationPickToggleResult.ADDED,
        BattleInstallationPickService.togglePick(
            rig.war, rig.attacker, "Alice", rig.source.getId()));
    rig.war.getBattleInstallationPicks().put(null, new LinkedHashSet<>(Set.of("unowned")));
    rig.war.getBattleInstallationPicks().put("incomplete", null);
    rig.war.getBattleInstallationPicks().put("empty", new LinkedHashSet<>());
    assertEquals(
        Map.of(rig.attacker.getId(), Set.of(rig.source.getId())),
        BattleInstallationPickService.getAllPicks(rig.war));
    Faction outsider = rig.domain.saved("outsider", "Cara");
    Installation unmapped =
        new Installation("unmapped", "Unmapped", InstallationKind.PORT, 0, 0, 0, 0L);
    assertFalse(
        BattleInstallationPickEligibility.isUnderSideControl(null, rig.attacker, rig.source));
    assertFalse(
        BattleInstallationPickEligibility.isUnderSideControl(rig.war, outsider, rig.source));
    assertFalse(
        BattleInstallationPickEligibility.isUnderSideControl(rig.war, rig.attacker, unmapped));
    assertTrue(
        BattleInstallationPickEligibility.listPickableInstallations(null, rig.attacker).isEmpty());
    assertFalse(
        BattleInstallationInPlayService.isInPlay(null, rig.attacker.getId(), rig.source.getId()));
    assertFalse(BattleSiegeFortService.isSiegeFortInPlay(rig.war, " "));
    assertTrue(BattleSiegeFortService.currentSiegeFortInstallationId(null).isEmpty());
    assertTrue(BattleSiegeFortService.currentSiegeFortOwner(rig.war).isEmpty());
  }

  @Test
  void voteSnapshotsFilterOldHoursAndNullRowsWithoutMutatingCurrentValidSelections() {
    UUID alice = rig.alice.getUniqueId();
    UUID bob = rig.bob.getUniqueId();
    UUID incomplete = UUID.randomUUID();
    rig.war.getBattleVotes().put(alice, new HashSet<>(Set.of(19, 20, 21)));
    rig.war.getBattleVotes().put(bob, new HashSet<>(Set.of(21)));
    rig.war.getBattleVotes().put(incomplete, null);
    rig.war.getBattleVotes().put(UUID.randomUUID(), new HashSet<>());
    Function<UUID, Faction> lookup = id -> id.equals(bob) ? rig.defender : rig.attacker;
    Set<Integer> snapshot = BattleVoteService.getPlayerSelections(rig.war, alice);
    assertEquals(Set.of(20, 21), snapshot);
    assertThrows(UnsupportedOperationException.class, snapshot::clear);
    assertEquals(
        Map.of(20, 1, 21, 1, 22, 0, 23, 0),
        BattleVoteService.countVotesByHour(rig.war, BelligerentRole.ATTACKER, lookup));
    assertEquals(
        new BattleHourTally(1, 1), BattleVoteService.buildHourTally(rig.war, lookup).get(21));
    assertEquals(21, BattleVoteService.pickHour(rig.war, lookup).orElseThrow());
    assertEquals(2, BattleQuorumService.countDistinctVoters(rig.war));
    assertTrue(BattleVoteService.getPlayerSelections(null, alice).isEmpty());
    assertTrue(
        BattleVoteService.countVotesByHour(null, BelligerentRole.ATTACKER, lookup).isEmpty());
    assertTrue(BattleVoteService.buildHourTally(rig.war, null).isEmpty());
    assertTrue(BattleVoteService.pickHour(null, lookup).isEmpty());
    BattleVoteService.clearVotes(null);
    assertEquals(Set.of(19, 20, 21), rig.war.getBattleVotes().get(alice));
    BattleVoteService.clearVotes(rig.war);
    assertTrue(rig.war.getBattleVotes().isEmpty());
    assertEquals(Set.of(20, 21), snapshot);
  }

  @Test
  void unavailableVoterCannotChangeSelectionsAndUiCapabilitiesFollowTheCurrentVote() {
    UUID alice = rig.alice.getUniqueId();
    assertEquals(
        BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT,
        BattleVoteService.toggleVote(null, alice, 21, rig.attacker, true));
    assertEquals(
        BattleVoteToggleResult.REJECTED_OFFLINE,
        BattleVoteService.toggleVote(rig.war, alice, 21, rig.attacker, false));
    assertTrue(rig.war.getBattleVotes().isEmpty());
    assertFalse(BattleVoterEligibility.canProposeAutoresolve(null, BelligerentRole.ATTACKER));
    assertTrue(BattleVoterEligibility.canProposeAutoresolve(rig.war, BelligerentRole.ATTACKER));
    assertTrue(BattleVoterEligibility.canProposeAutoresolve(rig.war, BelligerentRole.DEFENDER));
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    assertFalse(BattleVoterEligibility.canProposeAutoresolve(rig.war, BelligerentRole.ATTACKER));
    int[] slots = BattleVoterEligibility.hourToggleSlots();
    slots[0] = -1;
    assertEquals(28, BattleVoterEligibility.hourToggleSlots()[0]);
    assertEquals(
        List.of(20, 21, 22, 23),
        BattleVoterEligibility.hourSlotLayout(null).stream()
            .map(BattleVoterEligibility.HourSlotEntry::hour)
            .filter(java.util.Objects::nonNull)
            .toList());
    assertEquals(0, BattleQuorumService.countDistinctVoters(null));
    assertFalse(BattleQuorumService.isSmallestSideFullyRepresented(null, name -> alice));
    assertFalse(BattleQuorumService.isSmallestSideFullyRepresented(rig.war, name -> null));
    assertFalse(BattleQuorumService.meetsQuorum(null, name -> alice).passed());
  }

  @Test
  void scheduleHoursRespectParisDayBoundariesAndRejectDatesOutsideTheBattleDay() {
    LocalDate dstDay = LocalDate.of(2026, 10, 25);
    Instant midnight = BattleWindowService.atScheduleHour(dstDay, 24);
    assertEquals(Instant.parse("2026-10-25T23:00:00Z"), midnight);
    assertEquals(24, BattleWindowService.resolveScheduleHour(dstDay, midnight));
    assertEquals(dstDay.plusDays(1), BattleWindowService.scheduleDate(midnight));
    assertEquals(0, BattleWindowService.scheduleHour(midnight));
    assertEquals(
        23,
        BattleWindowService.resolveScheduleHour(
            dstDay, BattleWindowService.atScheduleHour(dstDay, 23)));
    assertNull(BattleWindowService.resolveScheduleHour(dstDay, midnight.plusSeconds(60)));
    assertNull(BattleWindowService.resolveScheduleHour(dstDay, midnight.minusSeconds(172800)));
    assertNull(BattleWindowService.resolveScheduleHour(null, midnight));
    assertNull(BattleWindowService.computeScheduledBattleAt((War) null, 21));
    assertNull(BattleWindowService.computeScheduledBattleAt(rig.war, 24));
    assertNull(BattleWindowService.scheduleDate(null));
    assertEquals(0, BattleWindowService.scheduleHour(null));
    assertEquals(
        BattleWindowService.atScheduleHour(Fixture.DAY, 21),
        BattleWindowService.computeScheduledBattleAt(rig.war, 21));
  }

  @Test
  void compulsoryPortWithDuplicateLocalIdsIsAssignedUsingTheScheduledSeaTile() {
    rig.attacker.getInstallationHandler().detachOnProvince(10);
    rig.defender.getInstallationHandler().detachOnProvince(20);
    provinces.clear();
    for (int id : List.of(10, 20, 30)) province(id, "PLAINS");
    for (int id : List.of(15, 25, 35)) province(id, "SEA");
    link(30, 15);
    link(20, 25);
    link(10, 35);
    Faction subject = rig.domain.saved("port_subject", "Cara");
    rig.domain.subject(rig.defender, subject);
    Installation intended = rig.install(subject, "local_port", InstallationKind.PORT, 30);
    rig.install(rig.defender, "local_port", InstallationKind.PORT, 20);
    rig.install(rig.attacker, "local_port", InstallationKind.PORT, 10);
    War war = navalWar(false, intended);
    assertSame(
        subject, PortSeaZocIndex.fromGameState().portForSeaProvince(15).orElseThrow().owner());
    BattleInstallationPickService.ensureDefenderZocPort(war);
    assertEquals(
        Map.of(subject.getId(), Set.of("local_port")),
        BattleInstallationPickService.getAllPicks(war));
    assertFalse(BattleInstallationPickService.isDefenderZocPort(war, rig.attacker, "local_port"));
    assertFalse(BattleInstallationPickService.isDefenderZocPort(war, rig.defender, "local_port"));
    assertTrue(BattleInstallationPickService.isDefenderZocPort(war, subject, "local_port"));
    subject.getInstallationHandler().detachOnProvince(30);
    rig.defender.getInstallationHandler().detachOnProvince(20);
    BattleInstallationPickService.clearForNewBattleDay(war);
    assertTrue(BattleInstallationPickService.getAllPicks(war).isEmpty());
    assertEquals("local_port", BattleInstallationPickService.defenderZocPortId(war));
    assertFalse(BattleInstallationPickService.isDefenderZocPort(war, subject, "local_port"));
  }

  @Test
  void restoredFightingRaidOnlyExposesInstallationsOwnedByItsCurrentParticipants() {
    CampaignRaidData data = new CampaignRaidData();
    data.id = "restored_raid";
    data.warId = rig.war.getId();
    data.battleDay = Fixture.DAY.toString();
    data.state = "fighting";
    data.attackerCoalition = "aggressor";
    data.sourceInstallationId = rig.source.getId();
    data.targetInstallationId = rig.target.getId();
    rig.war.setActiveCampaignRaid(CampaignRaid.fromData(data));
    Faction outsider = rig.domain.saved("outside_raid", "Cara");
    assertFalse(
        BattleInstallationInPlayService.isInPlay(rig.war, "deleted_owner", rig.source.getId()));
    assertFalse(
        BattleInstallationInPlayService.isInPlay(rig.war, outsider.getId(), rig.source.getId()));
    assertTrue(
        BattleInstallationInPlayService.isInPlay(
            rig.war, rig.attacker.getId(), rig.source.getId()));
    assertTrue(
        BattleInstallationInPlayService.isInPlay(
            rig.war, rig.defender.getId(), rig.target.getId()));
    data.attackerCoalition = null;
    rig.war.setActiveCampaignRaid(CampaignRaid.fromData(data));
    assertFalse(
        BattleInstallationInPlayService.isInPlay(
            rig.war, rig.attacker.getId(), rig.source.getId()));
    assertFalse(
        BattleInstallationInPlayService.isInPlay(
            rig.war, rig.defender.getId(), rig.target.getId()));
    assertEquals(CampaignRaidState.FIGHTING, rig.war.getActiveCampaignRaid().getState());
  }

  @Test
  void openSeaSlotWithoutAPortCannotCreateAnInstallationPick() {
    War war = navalWar(false, rig.target);
    war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(15, CampaignBattleKind.NAVAL, false, null, " ")));
    assertNull(BattleInstallationPickService.defenderZocPortId(war));
    assertTrue(BattleInstallationPickService.getAllPicks(war).isEmpty());
    assertFalse(
        BattleInstallationPickService.isDefenderZocPort(war, rig.defender, rig.target.getId()));
  }

  @Test
  void operationalPortListingToleratesAnIncompletePublicRegistryRow() {
    FactionManager.factions.add(null);
    try {
      var ports = PortSeaZocIndex.listOperationalPorts();
      assertEquals(2, ports.size());
      assertTrue(
          ports.stream()
              .anyMatch(
                  port -> port.owner() == rig.attacker && port.id().equals(rig.source.getId())));
      assertTrue(
          ports.stream()
              .anyMatch(
                  port -> port.owner() == rig.defender && port.id().equals(rig.target.getId())));
    } finally {
      FactionManager.factions.remove(null);
    }
  }

  @Test
  void anEmptyParticipantRosterDoesNotSatisfyTheSmallestSideQuorum() {
    rig.attacker.getOrCreateMainGuild().getMembers().clear();
    assertTrue(rig.attacker.getMembers().isEmpty());
    rig.war.getBattleVotes().put(rig.bob.getUniqueId(), Set.of(21));
    assertFalse(
        BattleQuorumService.isSmallestSideFullyRepresented(rig.war, name -> rig.bob.getUniqueId()));
    assertEquals(1, BattleQuorumService.countDistinctVoters(rig.war));
  }

  @Test
  void aDeletedFactionCannotKeepItsOldInstallationInPlay() {
    assertEquals(
        InstallationPickToggleResult.ADDED,
        BattleInstallationPickService.togglePick(
            rig.war, rig.attacker, "Alice", rig.source.getId()));
    assertEquals(
        InstallationPickToggleResult.ADDED,
        BattleInstallationPickService.togglePick(rig.war, rig.defender, "Bob", rig.target.getId()));
    assertTrue(
        BattleInstallationInPlayService.isInPlay(
            rig.war, rig.attacker.getId(), rig.source.getId()));
    FactionManager.factions.remove(rig.attacker);

    assertFalse(
        BattleInstallationInPlayService.isInPlay(rig.war, rig.attacker.getId(), rig.source.getId()),
        "An installation whose faction is no longer registered cannot remain a live target");
    assertEquals(
        Map.of(rig.defender.getId(), Set.of(rig.target.getId())),
        BattleInstallationPickService.getAllPicks(rig.war));
  }

  @Test
  void siegeFortOwnershipRequiresTheCurrentInstallationAtTheScheduledProvince() {
    Installation fort = rig.install(rig.defender, "besieged", InstallationKind.FORT, 20);
    War war = navalWar(false, fort);
    war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, fort.getId())));
    assertEquals(rig.defender, BattleSiegeFortService.currentSiegeFortOwner(war).orElseThrow());
    assertTrue(
        BattleSiegeFortService.isSiegeFortInPlayForFaction(
            war, rig.defender.getId(), fort.getId()));
    assertFalse(
        BattleSiegeFortService.isSiegeFortInPlayForFaction(
            war, rig.attacker.getId(), fort.getId()));
    rig.defender.getInstallationHandler().detachOnProvince(20);
    assertTrue(BattleSiegeFortService.currentSiegeFortOwner(war).isEmpty());
    assertFalse(
        BattleSiegeFortService.isSiegeFortInPlayForFaction(
            war, rig.defender.getId(), fort.getId()));
  }

  private War navalWar(boolean counterAttack, Installation port) {
    War war = new War(918732, rig.attacker, rig.defender);
    war.setGoal(WarGoalType.SUBJUGATE);
    war.setWarType(WarType.SUBJUGATE);
    war.setBattleDay(Fixture.DAY);
    war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    war.setCampaignProvinces(List.of(10, 15, 20));
    war.setCampaignStartProvinceId(15);
    war.setObjectiveProvinceId(20);
    war.setInitiativeAttacker(4);
    war.setInitiativeDefender(4);
    war.setCampaignPhase(counterAttack ? CampaignPhase.COUNTER_PUSH : CampaignPhase.INVASION);
    war.setPushTarget(
        counterAttack
            ? CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL
            : CampaignPushTarget.TOWARD_OBJECTIVE);
    war.setInitiativeHolderCoalition(
        counterAttack ? CampaignCoalition.DEFENDER : CampaignCoalition.AGGRESSOR);
    ScheduledCampaignBattle naval =
        new ScheduledCampaignBattle(15, CampaignBattleKind.NAVAL, false, null, port.getId());
    if (counterAttack) {
      war.setCampaignCounterSchedule(List.of(naval));
      war.setOccupiedByAttacker(new ArrayList<>(List.of(port.getProvince())));
    } else {
      war.setCampaignBattleSchedule(List.of(naval));
      war.setOccupiedByDefender(new ArrayList<>(List.of(port.getProvince())));
    }
    return war;
  }

  private Province province(int id, String terrain) {
    Province province = new Province(id, terrain, 50, id * 10, 0);
    provinces.put(id, province);
    return province;
  }

  private void link(int first, int second) {
    provinces.get(first).addNeighbour(second);
    provinces.get(second).addNeighbour(first);
  }
}
