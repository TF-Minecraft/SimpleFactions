package net.tfminecraft.simplefactions.war.resolution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.stream.Collectors;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.war.campaign.admin.WarReparationsAdminService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.declare.PillageEligibility;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WarResolutionLifecycleCoverageTest {
  @Test
  void internalSubjugationUsesTheExplicitTypeWhenAStoredRelationLosesItsTypeDuringTheWar()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.domain.lawGroup(
          "authority", Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS true")));
      Faction winner = rig.domain.saved("internal_winner", "Winner");
      Faction loser = rig.domain.saved("internal_loser", "Loser");
      Faction liege = rig.domain.saved("internal_liege", "Liege");
      rig.domain.subject(liege, winner);
      rig.domain.subject(liege, loser);
      War internal = new War(919999, winner, loser);
      assertTrue(internal.isInternalWar());
      internal.setGoal(WarGoalType.SUBJUGATE);
      internal.setRelationTypeId("vassal");
      Relation stale =
          new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude());
      winner.setRelation(loser, stale);
      loser.setRelation(
          winner,
          new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
      RelationManager.endVassalage(liege, loser, false);
      assertNull(RelationManager.getOverlord(loser));
      RelationLoader.types.removeIf(type -> type.isDefault());
      stale.setType(null);
      WarOutcomeService.apply(internal, WarEndReason.ATTACKER_VICTORY);
      assertEquals("vassal", winner.getRelation(loser.getId()).getType().getId());
      assertEquals(winner.getId(), RelationManager.getOverlord(loser));
    }
  }

  @Test
  void pillageUtilitiesIgnoreAbsentInputsAndOnlySelectGuildsInsideTheSettlement() throws Exception {
    try (Fixture rig = new Fixture()) {
      var guild = rig.domain.guild(rig.defender, "utility_guild", "UtilityLeader");
      guild.setCapital(22, false);
      var settlement = new Settlement("utility_town", "Utility Town", 22, 0, 0);
      assertEquals(
          List.of(guild),
          PillageApplyService.guildsInSettlement(settlement, Arrays.asList(null, guild)));
      assertTrue(PillageApplyService.guildsInSettlement(null, List.of(guild)).isEmpty());
      assertTrue(PillageApplyService.guildsInSettlement(settlement, null).isEmpty());
      assertEquals(0, PillageApplyService.snapshotLoot(null, ignored -> 1, 3));
      assertEquals(0, PillageApplyService.snapshotLoot(List.of(guild), null, 3));
      assertEquals(0, PillageApplyService.liveTradeIncome(null));
      double before = rig.attacker.getBank().getWealth();
      PillageApplyService.depositLoot(null, 20);
      PillageApplyService.attachHits(null, -80, 1);
      assertEquals(before, rig.attacker.getBank().getWealth());
      assertTrue(guild.getPillageHits().isEmpty());
    }
  }

  @Test
  void oldSavedCivilWarWithoutAMovementDoesNotChargeReparationsOrChangeGovernment()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      var before =
          rig.defender.getGovernment().getStabilityModifiers().stream()
              .map(modifier -> modifier.getName() + ":" + modifier.getModifier())
              .toList();
      var saved = WarMapper.toData(rig.war);
      saved.civilWarHostFactionId = rig.defender.getId();
      saved.civilWarTempRebelFactionId = rig.attacker.getId();
      saved.movementId = null;
      var restored = WarMapper.fromData(saved);
      assertNotNull(restored.getCivilWarSnapshot());
      assertNull(restored.getMovementId());
      WarOutcomeService.apply(restored, WarEndReason.DEFENDER_VICTORY);
      WarOutcomeService.apply(restored, WarEndReason.WHITE_PEACE);
      assertTrue(rig.attacker.getWarReparationsObligations().isEmpty());
      assertTrue(rig.defender.getWarReparationsObligations().isEmpty());
      assertEquals(
          before,
          rig.defender.getGovernment().getStabilityModifiers().stream()
              .map(modifier -> modifier.getName() + ":" + modifier.getModifier())
              .toList());
    }
  }

  @Test
  void ambiguousPillageTargetsWithinTheDefendersRealmCannotBeSelectedOrPaid() throws Exception {
    try (Fixture rig = new Fixture()) {
      WarManager.get().clear();
      Faction subject = rig.domain.saved("pillage_subject", "SubjectLeader");
      rig.domain.subject(rig.defender, subject);
      rig.defender.addProvince(22);
      subject.addProvince(33);
      var town = rig.defender.getSettlementHandler().found("Town", 22, 0, 0).getSettlement();
      var twin = subject.getSettlementHandler().found("Town", 33, 0, 0).getSettlement();
      assertNotSame(town, twin);
      assertEquals(town.getId(), twin.getId());
      assertNull(PillageEligibility.findSettlement(town.getId()));
      assertNull(PillageEligibility.findSettlement(town.getId(), rig.defender));
      var options = PillageEligibility.options(rig.attacker, rig.defender);
      assertEquals(1, options.size());
      assertFalse(options.getFirst().eligible());
      assertTrue(options.getFirst().blockReason().contains("More than one"));
      var request =
          new WarDeclareRequest(
              rig.attacker,
              rig.defender,
              WarGoalType.PILLAGE,
              null,
              null,
              null,
              null,
              null,
              town.getId());
      assertFalse(new WarGoalValidator().validate(request).isValid());
      rig.war.setTargetSettlementId(town.getId());
      double balance = rig.attacker.getBank().getWealth();
      PillageApplyService.apply(rig.war);
      assertEquals(balance, rig.attacker.getBank().getWealth());
      assertTrue(rig.defender.getOrCreateMainGuild().getPillageHits().isEmpty());
      assertTrue(subject.getOrCreateMainGuild().getPillageHits().isEmpty());
      assertSame(twin, subject.getSettlementHandler().getById(twin.getId()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"live", "cached", "no_cache", "no_loot"})
  void pillageUsesCurrentIncomeWithCachedFallbackAndStillAppliesTheConfiguredTradeHit(String mode)
      throws Exception {
    try (Fixture rig = new Fixture()) {
      for (String field :
          List.of("pillageLootDays", "pillageTradeHitPercent", "pillageTradeHitDays"))
        rig.remember(Cache.class, field);
      Cache.pillageLootDays = mode.equals("no_loot") ? 0 : 3;
      Cache.pillageTradeHitPercent = -60;
      Cache.pillageTradeHitDays = 2;
      rig.defender.addProvince(22);
      assertTrue(rig.defender.getSettlementHandler().found("Victim", 22, 0, 0).isSuccess());
      var merchant = rig.domain.guild(rig.defender, "victim_merchant", "Trader");
      var outside = rig.domain.guild(rig.defender, "outside_merchant", "Visitor");
      merchant.setCapital(22, false);
      outside.setCapital(23, false);
      TradeBreakdown cached = new TradeBreakdown();
      cached.setIncome(10);
      merchant.setTradeBreakdown(cached);
      when(rig.domain.provinces.getIncome(merchant, false)).thenReturn(20.0);
      if (mode.equals("cached") || mode.equals("no_cache"))
        when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(null);
      if (mode.equals("no_cache")) merchant.setTradeBreakdown(null);
      rig.war.setTargetSettlementId("Victim");
      double before = rig.attacker.getBank().getWealth();
      PillageApplyService.apply(rig.war);
      double expected = mode.equals("live") ? 60 : mode.equals("cached") ? 30 : 0;
      assertEquals(before + expected, rig.attacker.getBank().getWealth());
      assertEquals(-60, PillageTradeHit.percent(merchant));
      assertEquals(0, PillageTradeHit.percent(outside));
      assertEquals(1, merchant.getPillageHits().size());
      assertEquals(60.0 / 48, merchant.getPillageHits().getFirst().getDecay());
    }
  }

  @Test
  void missingPillageTargetsNeverPayLootOrDamageGuilds() throws Exception {
    try (Fixture rig = new Fixture()) {
      double balance = rig.attacker.getBank().getWealth();
      assertDoesNotThrow(() -> PillageApplyService.apply(null));
      for (String id : Arrays.asList(null, "", "removed_town")) {
        rig.war.setTargetSettlementId(id);
        PillageApplyService.apply(rig.war);
      }
      assertEquals(balance, rig.attacker.getBank().getWealth());
      assertTrue(rig.defender.getOrCreateMainGuild().getPillageHits().isEmpty());
    }
  }

  @Test
  void staleWarPayloadsAndRemovedConfigurationLeaveExistingStateUntouched() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "openMarketApplyDefenderLaw");
      Cache.openMarketApplyDefenderLaw = "removed_market_law";
      var before =
          rig.defender.getGovernment().getStabilityModifiers().stream()
              .map(modifier -> modifier.getName() + ":" + modifier.getModifier())
              .toList();
      double wealth = rig.attacker.getBank().getWealth();
      WarOutcomeService.apply(null, WarEndReason.ATTACKER_VICTORY);
      WarOutcomeService.apply(rig.war, null);
      for (WarGoalType goal :
          List.of(
              WarGoalType.SUBJUGATE,
              WarGoalType.TRANSFER_SUBJECT,
              WarGoalType.DE_JURE_ANNEX,
              WarGoalType.OPEN_MARKET,
              WarGoalType.CHANGE_GOVERNMENT)) {
        rig.war.setGoal(goal);
        rig.war.setRelationTypeId("removed_subject_type");
        rig.war.setSubjectFactionId("removed_subject");
        rig.war.setTargetTitleId("removed_title");
        rig.war.setGovernmentLawId("removed_government_law");
        rig.war.setLeadershipLawId("removed_leadership_law");
        WarOutcomeService.apply(rig.war, WarEndReason.ATTACKER_VICTORY);
      }
      rig.war.setGoal(WarGoalType.DE_JURE_ANNEX);
      rig.war.setTargetTitleId(null);
      WarOutcomeService.apply(rig.war, WarEndReason.ATTACKER_VICTORY);
      assertNull(RelationManager.getOverlord(rig.attacker));
      assertNull(RelationManager.getOverlord(rig.defender));
      assertEquals(wealth, rig.attacker.getBank().getWealth());
      assertEquals(
          before,
          rig.defender.getGovernment().getStabilityModifiers().stream()
              .map(modifier -> modifier.getName() + ":" + modifier.getModifier())
              .toList());
    }
  }

  @Test
  void annexVictoryLeavesACapitalMovedIntoTheClaimedTitleDuringTheWarWithItsOwner()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.defender.addProvince(22);
      rig.defender.addProvince(23);
      Province first = new Province(22, "plains", 0);
      Province second = new Province(23, "plains", 0);
      first.addNeighbour(23);
      second.addNeighbour(22);
      rig.domain.provinceData.put(22, first);
      rig.domain.provinceData.put(23, second);
      rig.defender.setCapital(22, false, false);
      var title = rig.domain.title("contested_county", "county", 22, 23);
      rig.attacker.addTitle(title);
      rig.war.setGoal(WarGoalType.DE_JURE_ANNEX);
      rig.war.setTargetTitleId(title.getId());
      WarOutcomeService.apply(rig.war, WarEndReason.ATTACKER_VICTORY);
      assertTrue(rig.defender.ownsProvince(22));
      assertFalse(rig.attacker.ownsProvince(22));
      assertEquals(22, rig.defender.getCapital());
      assertFalse(rig.defender.ownsProvince(23));
      assertTrue(rig.attacker.ownsProvince(23));
    }
  }

  @Test
  void reparationsQueriesAndDailySettlementIgnoreInactiveRowsAndRemoveExpiredObligations()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      assertFalse(WarReparationsService.apply(null, rig.defender, 10, 2));
      assertFalse(WarReparationsService.apply(rig.attacker, null, 10, 2));
      assertFalse(WarReparationsService.apply(rig.attacker, rig.attacker, 10, 2));
      assertNull(WarReparationsService.findObligation(null, rig.defender));
      assertNull(WarReparationsService.findObligation(rig.attacker, null));
      assertTrue(WarReparationsService.activeObligations(null).isEmpty());
      WarReparationsService.tickAfterDailySettlement(null);
      WarReparationsService.tickAfterDailySettlement(rig.attacker);
      WarReparationsService.applyFromWar(null);
      assertTrue(WarReparationsService.apply(rig.attacker, rig.defender, 12.5, 2));
      var live = WarReparationsService.findObligation(rig.attacker, rig.defender);
      rig.attacker.getWarReparationsObligations().add(null);
      rig.attacker.getWarReparationsObligations().add(new WarReparationsObligation("", 10, 1));
      assertEquals(List.of(live), WarReparationsService.activeObligations(rig.attacker));
      assertNull(
          WarReparationsService.findObligation(
              rig.attacker, rig.domain.saved("other_payee", "Other")));
      WarReparationsService.tickAfterDailySettlement(rig.attacker);
      assertEquals(List.of(live), rig.attacker.getWarReparationsObligations());
      assertEquals(1, live.getDaysRemaining());
      WarReparationsService.tickAfterDailySettlement(rig.attacker);
      assertTrue(WarReparationsService.activeObligations(rig.attacker).isEmpty());
      assertNull(WarReparationsService.findObligation(rig.attacker, rig.defender));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing_from",
        "missing_to",
        "unknown_from",
        "unknown_to",
        "bad_percent",
        "bad_days",
        "nonpositive",
        "same"
      })
  void administrativeReparationsRejectInvalidArgumentsWithoutSavingOrMutating(String bad)
      throws Exception {
    try (Fixture rig = new Fixture()) {
      String from = rig.attacker.getId(), to = rig.defender.getId(), percent = "15", days = "3";
      switch (bad) {
        case "missing_from" -> from = "";
        case "missing_to" -> to = null;
        case "unknown_from" -> from = "unknown";
        case "unknown_to" -> to = "unknown";
        case "bad_percent" -> percent = "abc";
        case "bad_days" -> days = "1.5";
        case "nonpositive" -> days = "0";
        case "same" -> to = from;
        default -> fail("Unexpected case");
      }
      rig.writes.clear();
      var result = WarReparationsAdminService.apply(from, to, percent, days);
      assertFalse(result.ok());
      assertTrue(result.message().startsWith("§c"));
      assertTrue(rig.attacker.getWarReparationsObligations().isEmpty());
      assertTrue(rig.defender.getWarReparationsObligations().isEmpty());
      assertTrue(rig.writes.isEmpty());
    }
  }

  @Test
  void pillageVictoryTargetsTheDefendersTownWhenTheAttackersTownHasTheSameId() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "pillageLootDays");
      rig.remember(Cache.class, "pillageTradeHitPercent");
      Cache.pillageLootDays = 2;
      Cache.pillageTradeHitPercent = -80;
      rig.attacker.addProvince(11);
      rig.defender.addProvince(22);
      assertTrue(rig.attacker.getSettlementHandler().found("Town", 11, 0, 0).isSuccess());
      assertTrue(rig.defender.getSettlementHandler().found("Town", 22, 0, 0).isSuccess());
      var ownGuild = rig.domain.guild(rig.attacker, "own_merchants", "OwnTrader");
      var enemyGuild = rig.domain.guild(rig.defender, "enemy_merchants", "EnemyTrader");
      ownGuild.setCapital(11, false);
      enemyGuild.setCapital(22, false);
      when(rig.domain.provinces.getIncome(ownGuild, false)).thenReturn(7.0);
      when(rig.domain.provinces.getIncome(enemyGuild, false)).thenReturn(30.0);
      double before = rig.attacker.getBank().getWealth();
      rig.war.setGoal(WarGoalType.PILLAGE);
      rig.war.setTargetSettlementId("Town");

      WarOutcomeService.apply(rig.war, WarEndReason.ATTACKER_VICTORY);

      assertAll(
          () -> assertEquals(before + 60, rig.attacker.getBank().getWealth()),
          () -> assertEquals(-80, PillageTradeHit.percent(enemyGuild)),
          () -> assertEquals(0, PillageTradeHit.percent(ownGuild)));
    }
  }

  @Test
  void administrativeReparationsPersistEveryAffectedFactionInTheVassalTree() throws Exception {
    try (Fixture rig = new Fixture()) {
      Faction subject = rig.domain.saved("resolution_subject", "SubjectLeader");
      Faction subsubject = rig.domain.saved("resolution_subsubject", "NestedLeader");
      rig.domain.subject(rig.attacker, subject);
      rig.domain.subject(subject, subsubject);
      rig.writes.clear();

      var result =
          WarReparationsAdminService.apply(rig.attacker.getId(), rig.defender.getId(), "12.5", "3");

      assertTrue(result.ok(), result.message());
      for (Faction payer : Set.of(rig.attacker, subject, subsubject)) {
        var obligation = WarReparationsService.findObligation(payer, rig.defender);
        assertNotNull(obligation);
        assertEquals(12.5, obligation.getIncomePercent());
        assertEquals(3, obligation.getDaysRemaining());
      }
      Set<String> persisted =
          rig.writes.stream()
              .map(Fixture.Write::value)
              .filter(FactionData.class::isInstance)
              .map(FactionData.class::cast)
              .filter(data -> !data.warReparationsObligations.isEmpty())
              .map(data -> data.id)
              .collect(Collectors.toSet());
      assertEquals(Set.of(rig.attacker.getId(), subject.getId(), subsubject.getId()), persisted);
    }
  }
}
