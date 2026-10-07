package net.tfminecraft.simplefactions.war.resolution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.util.*;
import net.tfminecraft.simplefactions.database.ScheduledCampaignBattleData;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WarPeaceBoundaryCoverageTest {
  @Test
  void unknownSavedWarStatusIsRejectedBeforeItCanBecomeAnUnsaveableWar() throws Exception {
    try (Fixture rig = new Fixture()) {
      WarData saved = WarMapper.toData(rig.war);
      saved.status = "unknown_future_status";
      Gson gson = new Gson();
      assertNull(
          WarMapper.fromData(gson.fromJson(gson.toJson(saved), WarData.class)),
          "An unknown status must not create a war that crashes on the next save");
      assertSame(rig.war, WarManager.getById(rig.war.getId()));
      assertTrue(rig.war.isActive());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown_future_value", " ", "NaVaL"})
  void legacyOptionalCampaignValuesRetainSafeDefaultsThroughJsonReload(String value) {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction attacker = domain.saved("attacker", "AttackerLeader");
      Faction defender = domain.saved("defender", "DefenderLeader");
      War original = new War(991011, attacker, defender);
      original.setGoal(WarGoalType.WAR);
      WarData saved = WarMapper.toData(original);
      saved.status = null;
      saved.warType = value;
      saved.campaignPhase = value;
      saved.objectiveHeldBy = value;
      ScheduledCampaignBattleData slot = new ScheduledCampaignBattleData();
      slot.provinceId = 20;
      slot.kind = value;
      slot.required = true;
      saved.campaignBattleSchedule = List.of(slot);
      Gson gson = new Gson();
      War restored = WarMapper.fromData(gson.fromJson(gson.toJson(saved), WarData.class));
      assertNotNull(restored);
      assertTrue(restored.isActive(), "Omitted legacy status defaults to active");
      assertNull(restored.getWarType());
      assertEquals(CampaignPhase.INVASION, restored.getCampaignPhase());
      assertEquals(ObjectiveHolder.DEFENDER, restored.getObjectiveHeldBy());
      CampaignBattleKind expectedKind =
          value.equals("NaVaL") ? CampaignBattleKind.NAVAL : CampaignBattleKind.FIELD;
      assertEquals(expectedKind, restored.getCampaignBattleSchedule().getFirst().kind());
      WarData canonical = WarMapper.toData(restored);
      assertEquals("active", canonical.status);
      assertEquals("invasion", canonical.campaignPhase);
      assertEquals("defender", canonical.objectiveHeldBy);
      assertEquals(
          value.equals("NaVaL") ? "naval" : "field",
          canonical.campaignBattleSchedule.getFirst().kind);
    }
  }

  @Test
  void incompleteSavedLeaderRowsCannotAuthorizeCouncilSurrenderOrPeace() throws Exception {
    try (Fixture rig = new Fixture()) {
      WarData data = WarMapper.toData(rig.war);
      data.defenders.participants.removeIf(row -> row.leader.equals(rig.defender.getId()));
      Gson gson = new Gson();
      War restored = WarMapper.fromData(gson.fromJson(gson.toJson(data), WarData.class));
      WarManager.get().clear();
      WarManager.get().add(restored);
      assertTrue(restored.isParticipating(rig.defender));
      assertNull(restored.getSide(rig.defender));
      assertNull(CouncilPeaceQueries.sideMain(restored, rig.defender));
      CouncilPeaceService.apply(
          rig.defender, proposal(rig.defender, Action.SURRENDER, restored.getId()));
      CouncilPeaceService.apply(
          rig.defender, proposal(rig.defender, Action.WHITE_PEACE, restored.getId()));
      assertTrue(restored.isActive());
      assertFalse(restored.isForcedWhitePeaceByDefender());
      assertSame(restored, WarManager.getById(restored.getId()));
    }
  }

  @Test
  void staleCouncilTargetsAndUnrelatedActionsPreserveTheLiveWar() throws Exception {
    try (Fixture rig = new Fixture()) {
      Faction outsider = rig.domain.saved("outsider", "OutsiderLeader");
      Proposal none = new Proposal("Alice", rig.attacker.getGovernment());
      CouncilPeaceService.apply(null, none);
      CouncilPeaceService.apply(rig.attacker, null);
      CouncilPeaceService.apply(rig.attacker, none);
      CouncilPeaceService.apply(
          rig.attacker, proposal(rig.attacker, Action.TAX_CHANGE, rig.war.getId()));
      CouncilPeaceService.apply(outsider, proposal(outsider, Action.SURRENDER, rig.war.getId()));
      CouncilPeaceService.apply(rig.attacker, proposal(rig.attacker, Action.SURRENDER, 999999));
      Proposal invalidTarget = proposal(rig.attacker, Action.SURRENDER, rig.war.getId());
      invalidTarget.setTarget("not-a-war-id");
      CouncilPeaceService.apply(rig.attacker, invalidTarget);
      assertFalse(CouncilPeaceQueries.isValidTarget(rig.attacker, invalidTarget.getTarget()));
      assertTrue(CouncilPeaceQueries.warsFor(null).isEmpty());
      assertEquals(List.of(rig.war), CouncilPeaceQueries.warsFor(rig.attacker));
      assertNull(CouncilPeaceQueries.sideMain(null, rig.attacker));
      assertNull(CouncilPeaceQueries.sideMain(rig.war, null));
      assertNull(CouncilPeaceQueries.sideMain(rig.war, outsider));
      assertFalse(CouncilPeaceQueries.isValidTarget(outsider, String.valueOf(rig.war.getId())));
      assertTrue(rig.war.isActive());
      assertFalse(rig.war.isForcedWhitePeaceByAttacker());
      assertFalse(rig.war.isForcedWhitePeaceByDefender());
    }
  }

  @Test
  void bothRealCouncilsCanEndTheWarWithoutChangingOwnershipOrBalances() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.war.setGoal(WarGoalType.WAR);
      double attackerBalance = rig.attacker.getBank().getWealth(),
          defenderBalance = rig.defender.getBank().getWealth();
      CouncilPeaceService.apply(
          rig.defender, proposal(rig.defender, Action.WHITE_PEACE, rig.war.getId()));
      assertTrue(rig.war.isActive());
      assertTrue(rig.war.isForcedWhitePeaceByDefender());
      assertTrue(rig.war.isWhitePeaceProposedByDefender());
      CouncilPeaceService.apply(
          rig.attacker, proposal(rig.attacker, Action.WHITE_PEACE, rig.war.getId()));
      assertEquals(WarEndReason.WHITE_PEACE, rig.war.getEndReason());
      assertFalse(rig.war.isActive());
      assertNull(WarManager.getById(rig.war.getId()));
      assertSame(rig.source, rig.attacker.getInstallationHandler().getById(rig.source.getId()));
      assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
      assertEquals(attackerBalance, rig.attacker.getBank().getWealth());
      assertEquals(defenderBalance, rig.defender.getBank().getWealth());
      verify(rig.domain.map).enqueueOccupationFromWar(rig.war);
    }
  }

  @Test
  void aRealCouncilSurrenderUsesTheCurrentSideMainAndEndsThatCoalitionsWar() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.war.setGoal(WarGoalType.WAR);
      Faction subject = rig.domain.saved("subject", "SubjectLeader");
      rig.domain.subject(rig.defender, subject);
      rig.war.getDefenders().getMainParticipants().getFirst().getSubjects().add(subject);
      assertSame(rig.defender, CouncilPeaceQueries.sideMain(rig.war, subject));

      CouncilPeaceService.apply(subject, proposal(subject, Action.SURRENDER, rig.war.getId()));

      assertEquals(WarEndReason.ATTACKER_VICTORY, rig.war.getEndReason());
      assertNull(WarManager.getById(rig.war.getId()));
      assertSame(rig.target, rig.defender.getInstallationHandler().getById(rig.target.getId()));
      verify(rig.domain.map).enqueueOccupationFromWar(rig.war);
    }
  }

  @Test
  void invalidOrEndedResolutionRequestsCannotFinishALiveWar() throws Exception {
    try (Fixture rig = new Fixture()) {
      Faction outsider = rig.domain.saved("outsider", "OutsiderLeader");
      assertTrue(
          WarResolutionService.evaluateAndMaybeEnd(null, ResolutionContext.none()).isEmpty());
      assertTrue(
          WarResolutionService.evaluateAndMaybeEnd(rig.war, null).isEmpty(),
          "A fresh war without a route cannot resolve as a stalemate");
      assertTrue(
          WarResolutionService.tryEndAfterBattle(null, 20, CampaignCoalition.AGGRESSOR, null, null)
              .isEmpty());
      assertTrue(WarResolutionService.tryEndAfterBattle(rig.war, 20, null, null, null).isEmpty());
      assertFalse(WarResolutionService.surrender(null, rig.attacker));
      assertFalse(WarResolutionService.surrender(rig.war, null));
      assertFalse(WarResolutionService.surrender(rig.war, outsider));
      assertFalse(WarResolutionService.acceptWhitePeaceAndEnd(rig.war, rig.attacker));
      assertTrue(rig.war.isActive());
      War ended = new War(991010, rig.attacker, rig.defender);
      ended.end(WarEndReason.ADMIN_END);
      assertTrue(
          WarResolutionService.evaluateAndMaybeEnd(ended, ResolutionContext.none()).isEmpty());
      assertFalse(WarResolutionService.surrender(ended, rig.attacker));
      assertTrue(
          WarResolutionService.tryEndAfterBattle(ended, 20, CampaignCoalition.AGGRESSOR, null, null)
              .isEmpty());
      assertEquals(WarEndReason.ADMIN_END, ended.getEndReason());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aFailedRetakeOfANoncapitalObjectiveResolvesThroughThePublicBattleContext(boolean automatic)
      throws Exception {
    try (Fixture rig = new Fixture()) {
      campaign(rig);
      rig.war.setObjectiveHeldBy(ObjectiveHolder.ATTACKER);
      rig.war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
      ResolutionContext context =
          ResolutionContext.forBattle(rig.war, 20, CampaignCoalition.AGGRESSOR);
      assertEquals(CampaignPushTarget.RETAKE_OBJECTIVE, context.preBattlePushTarget());
      assertEquals(ObjectiveHolder.ATTACKER, context.preBattleObjectiveHeldBy());
      var result =
          automatic
              ? WarResolutionService.evaluateAndMaybeEnd(rig.war, context)
              : WarResolutionService.tryEndAfterBattle(
                  rig.war,
                  20,
                  CampaignCoalition.AGGRESSOR,
                  context.preBattlePushTarget(),
                  context.preBattleObjectiveHeldBy());
      assertEquals(Optional.of(WarEndReason.ATTACKER_VICTORY), result);
      assertEquals(WarEndReason.ATTACKER_VICTORY, rig.war.getEndReason());
      assertNull(WarManager.getById(rig.war.getId()));
      assertEquals(30, rig.defender.getCapital(), "Losing the retake is not a capital transfer");
      assertEquals(10, rig.attacker.getCapital());
    }
  }

  @Test
  void aValidOfferCanBeAcceptedOnlyOnceAndOnlyByTheOpposingLeader() throws Exception {
    try (Fixture rig = new Fixture()) {
      campaign(rig);
      assertFalse(WarResolutionService.acceptWhitePeaceAndEnd(rig.war, rig.attacker));
      rig.war.setWhitePeaceProposedByAttacker(true);
      assertFalse(WarResolutionService.acceptWhitePeaceAndEnd(rig.war, rig.attacker));
      assertTrue(WarResolutionService.acceptWhitePeaceAndEnd(rig.war, rig.defender));
      assertFalse(WarResolutionService.acceptWhitePeaceAndEnd(rig.war, rig.defender));
      assertEquals(WarEndReason.WHITE_PEACE, rig.war.getEndReason());
      verify(rig.domain.map, times(1)).enqueueOccupationFromWar(rig.war);
    }
  }

  @Test
  void exhaustedInitiativeOffersPeaceWithoutEndingAStillContestedCampaign() throws Exception {
    try (Fixture rig = new Fixture()) {
      campaign(rig);
      rig.war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
      rig.war.setCursorIndex(0);
      CampaignCoalitionService.setFuel(rig.war, CampaignCoalition.AGGRESSOR, 0);

      assertTrue(
          WarResolutionService.evaluateAndMaybeEnd(rig.war, ResolutionContext.none()).isEmpty());

      assertTrue(rig.war.isActive());
      assertTrue(rig.war.isWhitePeaceProposedByAttacker());
      assertFalse(rig.war.isWhitePeaceProposedByDefender());
      assertSame(rig.war, WarManager.getById(rig.war.getId()));
      assertEquals(3, CampaignCoalitionService.getFuel(rig.war, CampaignCoalition.DEFENDER));
    }
  }

  @Test
  void missingWarContextAndGuildPillageCleanupAreSafeAtPublicBoundaries() {
    var context = ResolutionContext.forBattle(null, 7, CampaignCoalition.DEFENDER);
    assertNull(context.preBattlePushTarget());
    assertNull(context.preBattleObjectiveHeldBy());
    assertEquals(7, context.battleProvinceId());
    assertEquals(CampaignCoalition.DEFENDER, context.battleWinnerCoalition());
    assertEquals(80.0, PillageTradeHit.applyToIncome(null, 80));
    PillageTradeHit.attach(null, -100, 100);
    PillageTradeHit.tick(null);
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction faction = domain.saved("pillage", "PillageLeader");
      var guild = domain.guild(faction, "market", "Merchant");
      PillageTradeHit.tick(guild);
      assertTrue(guild.getPillageHits().isEmpty());
      PillageTradeHit.attach(guild, -100, PillageTradeHit.decayPerPowerTick(-100, 0));
      assertEquals(0.0, PillageTradeHit.applyToIncome(guild, 80));
      assertTrue(PillageTradeHit.ledgerSuffix(guild).contains("Pillaged"));
      PillageTradeHit.tick(guild);
      assertTrue(guild.getPillageHits().isEmpty());
      assertEquals(80.0, PillageTradeHit.applyToIncome(guild, 80));
      assertEquals("", PillageTradeHit.ledgerSuffix(guild));
    }
  }

  private static Proposal proposal(Faction faction, Action action, int warId) {
    Proposal proposal = new Proposal(faction.getLeader(), faction.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(action));
    proposal.setTarget(String.valueOf(warId));
    return proposal;
  }

  private static void campaign(Fixture rig) {
    rig.war.setGoal(WarGoalType.WAR);
    rig.war.setWarType(WarType.WAR);
    rig.attacker.addProvince(10);
    rig.defender.addProvince(20);
    rig.defender.addProvince(30);
    rig.attacker.setCapital(10, true, false);
    rig.defender.setCapital(30, true, false);
    rig.war.setCampaignProvinces(List.of(10, 20, 30));
    rig.war.setObjectiveProvinceId(20);
    rig.war.setCursorIndex(1);
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
    CampaignCoalitionService.setFuel(rig.war, CampaignCoalition.AGGRESSOR, 3);
    CampaignCoalitionService.setFuel(rig.war, CampaignCoalition.DEFENDER, 3);
  }
}
