package net.tfminecraft.simplefactions.war.campaign.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.MovementOutcomeService;
import net.tfminecraft.simplefactions.government.movement.MovementOutcomeSource;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleRosterService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.campaign.WarCampaignService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleScheduleCloseResult;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.freeze.PreparationFreeze;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CampaignAdministrationBoundaryCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(Cache.class, "warBattleWindowStartHour");
    rig.remember(Cache.class, "warBattleWindowEndHour");
    Cache.warBattleWindowStartHour = 20;
    Cache.warBattleWindowEndHour = 24;
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 10));
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void administratorCanAddOnlyTheDefendingSideToTheAvailabilityVote() {
    assertFalse(WarScheduleAdminService.castVote(rig.war, 21, "invalid").success());
    assertTrue(rig.war.getBattleVotes().isEmpty());
    WarScheduleAdminResult result = WarScheduleAdminService.castVote(rig.war, 21, "defender");
    assertTrue(result.success());
    assertEquals(1, BattleQuorumService.countDistinctVoters(rig.war));
    assertTrue(result.message().contains("1 voters total"));
    assertEquals(1, rig.war.getBattleVotes().size());
    assertEquals(List.of(java.util.Set.of(21)), List.copyOf(rig.war.getBattleVotes().values()));
    assertTrue(
        rig.war
            .getBattleVotes()
            .containsKey(
                net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups
                    .spoofMemberNameToUuid()
                    .apply("Bob")));
  }

  @ParameterizedTest
  @EnumSource(WarEndReason.class)
  void administrativeEndSummariesDistinguishTheRecordedOutcome(WarEndReason reason) {
    Map<WarEndReason, String> expected =
        Map.of(
            WarEndReason.WHITE_PEACE, "War ended (white peace).",
            WarEndReason.ATTACKER_VICTORY, "War ended (attacker victory).",
            WarEndReason.DEFENDER_VICTORY, "War ended (defender victory).",
            WarEndReason.ADMIN_END, "War ended (admin).");
    assertEquals(expected.get(reason), WarScheduleAdminService.formatWarEndSummary(reason));
  }

  @Test
  void missingAdministrativeWarAndEndReasonCannotMutateAnUnrelatedLiveVote() {
    assertEquals("War ended.", WarScheduleAdminService.formatWarEndSummary(null));
    assertFalse(WarScheduleAdminService.openVote(null).success());
    assertFalse(WarScheduleAdminService.closeVote(null, rig.now()).success());
    assertFalse(WarScheduleAdminService.battleCreate(null).success());
    assertFalse(WarScheduleAdminService.battleDelete(null).success());
    assertFalse(WarScheduleAdminService.battleStart(null).success());
    assertTrue(rig.war.isActive());
    assertTrue(rig.war.getBattleVotes().isEmpty());
    assertSame(rig.war, WarManager.getById(rig.war.getId()));
  }

  @Test
  void playerPreparationFreezeUsesTheLatestActualWarAndExpiresWithoutHoldingFutureWork() {
    Instant now = rig.now();
    rig.war.setPreparationFrozenUntil(now.plusSeconds(600));
    War laterWar = new War(918733, rig.attacker, rig.defender);
    laterWar.setPreparationFrozenUntil(now.plusSeconds(1200));
    WarManager.get().add(laterWar);
    assertEquals(now.plusSeconds(1200), PreparationFreeze.frozenUntil("Alice", now));
    assertEquals(now.plusSeconds(1200), PreparationFreeze.frozenUntil("Bob", now));
    assertNull(PreparationFreeze.frozenUntil("Unknown", now));
    assertNull(PreparationFreeze.frozenUntil((String) null, now));
    assertTrue(PreparationFreeze.hiringBlockedMessage(rig.attacker, now).contains("20m"));
    assertEquals("1m", PreparationFreeze.formatRemaining(now.plusSeconds(30), now));
    laterWar.end(WarEndReason.ADMIN_END);
    assertEquals(now.plusSeconds(600), PreparationFreeze.frozenUntil("Alice", now));
    assertNull(PreparationFreeze.frozenUntil("Alice", now.plusSeconds(600)));
    assertNull(PreparationFreeze.hiringBlockedMessage(rig.attacker, now.plusSeconds(600)));
  }

  @Test
  void importedMovementWithNoCurrentHostCannotApplyAnOutcome() {
    var stabilityBefore = stabilityValues();
    Movement imported = new Movement(null, new MovementData());
    assertDoesNotThrow(() -> MovementOutcomeService.apply(imported, MovementOutcomeSource.WAR));
    assertTrue(rig.attacker.getGovernment().getMovements().isEmpty());
    assertEquals(stabilityBefore, stabilityValues());
  }

  @Test
  void movementOutcomeUsesEditedProposalRatherThanItsOriginallyCachedAction() {
    rig.attacker.addMember("Cara");
    Proposal proposal = new Proposal("Cara", rig.attacker.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.NONE));
    rig.attacker.getGovernment().startMovement("Cara", proposal);
    Movement movement = rig.attacker.getGovernment().getMovementByLeader("Cara");
    assertNotNull(movement);
    assertEquals(Action.NONE, movement.getCauses().getFirst().getAction());
    proposal.setPoliticalActionProposal(null);
    proposal.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 17));
    MovementOutcomeService.apply(movement, MovementOutcomeSource.WAR);
    assertEquals(17, rig.attacker.getTaxRate(TaxTarget.CITIZENS, null, false));
    assertTrue(rig.attacker.getGovernment().getMovements().isEmpty());
    assertTrue(
        rig.attacker.getGovernment().getStabilityModifiers().stream()
            .anyMatch(
                modifier ->
                    modifier.getName().equals("Civil War") && modifier.getModifier() == -75));
  }

  @Test
  void withdrawnMovementProposalDoesNotApplyAStaleActionOrInventAWarPenalty() {
    var stabilityBefore = stabilityValues();
    rig.attacker.addMember("Cara");
    Proposal proposal = new Proposal("Cara", rig.attacker.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.NONE));
    rig.attacker.getGovernment().startMovement("Cara", proposal);
    Movement movement = rig.attacker.getGovernment().getMovementByLeader("Cara");
    proposal.setPoliticalActionProposal(null);
    MovementOutcomeService.apply(movement, MovementOutcomeSource.WAR);
    assertEquals("Alice", rig.attacker.getLeader());
    assertTrue(rig.attacker.getGovernment().getMovements().isEmpty());
    assertEquals(stabilityBefore, stabilityValues());
  }

  @Test
  void exhaustedFieldSideIsEliminatedOnlyOnceEveryOnlineParticipantHasReachedItsJail() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "boundary_field");
    BattleSide side = battle.getSideById("attacker");
    Warband band =
        Warband.createCampaignSideShell(
            "boundary_band", rig.war, rig.war.getAttackers(), "attacker");
    band.addPlayer(rig.alice);
    side.addBand(band);
    side.setLives(0);
    assertFalse(FieldWinService.isAtJail(rig.alice, side));
    side.setJail(new Location(rig.domain.ui.world, 100, 64, 100));
    when(rig.alice.getWorld()).thenReturn(rig.domain.ui.world);
    when(rig.alice.getLocation()).thenReturn(new Location(rig.domain.ui.world, 106, 64, 100));
    assertFalse(FieldWinService.isSideEliminated(side));
    when(rig.alice.getLocation()).thenReturn(new Location(rig.domain.ui.world, 105, 64, 100));
    assertTrue(FieldWinService.isSideEliminated(side));
    when(rig.alice.getWorld()).thenReturn(null);
    assertFalse(FieldWinService.isSideEliminated(side));
    when(rig.alice.getWorld()).thenReturn(rig.domain.ui.world);
    when(rig.alice.getLocation()).thenReturn(null);
    assertFalse(FieldWinService.isAtJail(rig.alice, side));
    assertFalse(FieldWinService.isAtJail(null, side));
    assertFalse(FieldWinService.isSideEliminated((BattleSide) null));
    FieldWinService.checkFieldWin(battle);
    FieldWinService.checkFieldWin(null);
    FieldWinService.clearEmptySideTracking(null);
    assertFalse(battle.hasStarted());
    assertEquals(0, side.getLives());
    assertEquals(List.of(band), side.getBands());
    FieldWinService.clearEmptySideTracking(battle);
  }

  @Test
  void reopeningAnEditableCampaignBattleWithARemovedSidePreservesItsLayout() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "custom_one_side");
    battle.setWarId(rig.war.getId());
    battle.removeSide("defender");
    BattleManager.addBattle(battle);
    WarScheduleAdminResult result = WarScheduleAdminService.battleCreate(rig.war);
    assertTrue(result.success());
    assertSame(battle, BattleManager.getByWarId(rig.war.getId()));
    assertEquals(1, battle.getSides().size());
    assertNull(battle.getSideById("defender"));
    assertNotNull(CampaignBattleRosterService.getCampaignWarband(battle, "attacker"));
  }

  @Test
  void campaignCannotBePopulatedFromAnUnmappedDefendingProvince() {
    ProvinceManager manager = new ProvinceManager();
    manager.start(Map.of(10, new Province(10, "PLAINS", 50, 0, 0)));
    when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(manager);
    FactionData left = rig.domain.data("mapped_attacker", "Cara");
    left.capital = 10;
    left.provinces.add(10);
    Faction attacker = rig.domain.saved(left);
    FactionData right = rig.domain.data("unmapped_defender", "Drew");
    right.capital = 999;
    right.provinces.add(999);
    Faction defender = rig.domain.saved(right);
    War war = new War(918734, attacker, defender);
    war.setGoal(WarGoalType.SUBJUGATE);
    war.setWarType(WarType.SUBJUGATE);
    war.setCampaignProvinces(List.of(10, 999));
    WarCampaignService service = new WarCampaignService(manager);
    assertFalse(service.populateCampaign(war));
    assertEquals(List.of(10, 999), war.getCampaignProvinces());
    assertTrue(war.getCampaignBattleSchedule().isEmpty());
    assertFalse(service.populateCampaign(null));
    war.end(WarEndReason.ADMIN_END);
    assertFalse(service.populateCampaign(war));
    assertEquals(List.of(10, 999), war.getCampaignProvinces());
  }

  @Test
  void deJureCampaignKeepsReachableRegionalObjectiveWhenTheDefenderCapitalIsDisconnected() {
    Province capital = new Province(10, "PLAINS", 50, 0, 0);
    Province objective = new Province(20, "PLAINS", 50, 10, 0);
    Province remoteCapital = new Province(30, "PLAINS", 50, 100, 0);
    capital.addNeighbour(20);
    objective.addNeighbour(10);
    ProvinceManager manager = new ProvinceManager();
    manager.start(Map.of(10, capital, 20, objective, 30, remoteCapital));
    when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(manager);
    FactionData left = rig.domain.data("de_jure_attacker", "Cara");
    left.capital = 10;
    left.provinces.add(10);
    Faction attacker = rig.domain.saved(left);
    var title = rig.domain.title("regional_claim", "county", 20);
    FactionData right = rig.domain.data("de_jure_defender", "Drew");
    right.capital = 30;
    right.provinces.addAll(List.of(20, 30));
    right.titles.add(title.getId());
    Faction defender = rig.domain.saved(right);
    War war = new War(918735, attacker, defender);
    war.setGoal(WarGoalType.DE_JURE_ANNEX);
    war.setWarType(WarType.DE_JURE);
    war.setTargetTitleId(title.getId());
    assertTrue(new WarCampaignService(manager).populateCampaign(war));
    assertEquals(20, war.getObjectiveProvinceId());
    assertEquals(List.of(10, 20), war.getCampaignProvinces());
    assertTrue(war.getCampaignBattleSchedule().stream().anyMatch(slot -> slot.provinceId() == 20));
  }

  @ParameterizedTest
  @EnumSource(BattleScheduleCloseResult.class)
  void administrativeVoteResultMessagesPreserveEverySupportedOutcome(
      BattleScheduleCloseResult result) {
    Instant scheduled = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setScheduledBattleAt(scheduled);
    rig.war.setScheduledBattleProvinceId(20);
    Map<BattleScheduleCloseResult, String> messages =
        Map.of(
            BattleScheduleCloseResult.SCHEDULED,
                "Vote close scheduled battle at " + scheduled + " (province 20).",
            BattleScheduleCloseResult.POSTPONED,
                "Vote close postponed to battle day " + Fixture.DAY + ".",
            BattleScheduleCloseResult.AUTORESOLVE_PENDING, "Vote close set AUTORESOLVE_PENDING.",
            BattleScheduleCloseResult.AUTORESOLVED, "Vote close autoresolved the battle.",
            BattleScheduleCloseResult.BLOCKED_DEFENDER_CHOICE,
                "Vote close blocked: defender choice unresolved.",
            BattleScheduleCloseResult.SKIPPED, "Vote close skipped.");
    WarScheduleAdminResult rendered =
        WarScheduleAdminService.formatVoteCloseResult(rig.war, result);
    assertEquals(messages.get(result), rendered.message());
    assertEquals(
        result != BattleScheduleCloseResult.BLOCKED_DEFENDER_CHOICE
            && result != BattleScheduleCloseResult.SKIPPED,
        rendered.success());
    assertEquals(scheduled, rig.war.getScheduledBattleAt());
    assertEquals(20, rig.war.getScheduledBattleProvinceId());
  }

  @Test
  void objectiveOnTheCapitalRouteIsReachedBeforeAnAttackerEnclaveBeyondIt() {
    Province capital = new Province(10, "PLAINS", 50, 0, 0);
    Province objective = new Province(20, "PLAINS", 50, 10, 0);
    Province enclave = new Province(40, "PLAINS", 50, 20, 0);
    Province remoteBorder = new Province(30, "PLAINS", 50, 30, 0);
    capital.addNeighbour(20);
    objective.addNeighbour(10);
    objective.addNeighbour(40);
    enclave.addNeighbour(20);
    enclave.addNeighbour(30);
    remoteBorder.addNeighbour(40);
    ProvinceManager manager = new ProvinceManager();
    manager.start(Map.of(10, capital, 20, objective, 40, enclave, 30, remoteBorder));
    when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(manager);
    FactionData left = rig.domain.data("enclave_attacker", "Cara");
    left.capital = 10;
    left.provinces.addAll(List.of(10, 40));
    Faction attacker = rig.domain.saved(left);
    FactionData right = rig.domain.data("enclave_defender", "Drew");
    right.capital = 20;
    right.provinces.addAll(List.of(20, 30));
    Faction defender = rig.domain.saved(right);
    War war = new War(918736, attacker, defender);
    war.setGoal(WarGoalType.SUBJUGATE);
    war.setWarType(WarType.SUBJUGATE);

    assertTrue(new WarCampaignService(manager).populateCampaign(war));

    assertEquals(
        List.of(10, 20),
        war.getCampaignProvinces(),
        "The campaign must not pass its objective and double back through an enclave");
    assertFalse(war.getCampaignBattleSchedule().isEmpty());
    assertEquals(20, war.getCampaignBattleSchedule().getLast().provinceId());
    assertTrue(war.getCampaignBattleSchedule().getLast().required());
  }

  @Test
  void currentPreparationFreezeRendersTimerLoreAndReleasesHiringAfterTheWarEnds() {
    Instant until = Instant.now().plusSeconds(3600);
    rig.war.setPreparationFrozenUntil(until);
    assertTrue(PreparationFreeze.isFrozen(rig.attacker));
    String lore = PreparationFreeze.frozenLore(until);
    assertTrue(lore.contains("Frozen: battle postponed"));
    assertTrue(lore.endsWith("left)"));
    assertNull(PreparationFreeze.frozenLore(null));
    rig.war.end(WarEndReason.ADMIN_END);
    assertFalse(PreparationFreeze.isFrozen(rig.attacker));
  }

  @Test
  void administratorCanVoteOnlyForAttackersAndRejectInvalidSidesWithoutMutation() {
    assertTrue(WarScheduleAdminService.castVote(rig.war, 21, "attacker").success());
    assertEquals(1, rig.war.getBattleVotes().size());
    assertTrue(
        rig.war
            .getBattleVotes()
            .containsKey(
                net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups
                    .spoofMemberNameToUuid()
                    .apply("Alice")));
    var before = Map.copyOf(rig.war.getBattleVotes());
    assertFalse(WarScheduleAdminService.castVote(rig.war, 22, "both-ish").success());
    assertEquals(before, rig.war.getBattleVotes());
  }

  @Test
  void campaignWithNoDefendingLandDoesNotInventAnObjective() {
    WarCampaignService service = new WarCampaignService(new ProvinceManager());
    assertFalse(service.populateCampaign(rig.war));
    assertNull(rig.war.getObjectiveProvinceId());
    assertTrue(rig.war.getCampaignBattleSchedule().isEmpty());
  }

  private List<String> stabilityValues() {
    return rig.attacker.getGovernment().getStabilityModifiers().stream()
        .map(value -> value.getName() + ":" + value.getModifier() + ":" + value.getDecay())
        .toList();
  }
}
