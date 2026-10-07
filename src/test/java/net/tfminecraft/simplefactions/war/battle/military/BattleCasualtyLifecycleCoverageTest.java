package net.tfminecraft.simplefactions.war.battle.military;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleCasualtyLifecycleCoverageTest {
  private Fixture rig;
  private List<WarCommitment> previousCommitments;
  private Battle battle;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(Cache.class, "warBattleDeathsPerRegimentLoss");
    Cache.warBattleDeathsPerRegimentLoss = 5;
    previousCommitments =
        new ArrayList<>(WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    WarCommitmentService.clearCommitments(rig.war.getId());
    rig.war.setCampaignPhase(CampaignPhase.INVASION);
    rig.war.setScheduledBattleProvinceId(20);
    battle = BattleFactory.createBlank(BattleType.FIELD, "casualty_lifecycle");
    battle.setWarId(rig.war.getId());
    battle.setProvinceId(20);
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) {
      if (previousCommitments != null)
        WarCommitmentService.restoreCommitments(rig.war.getId(), previousCommitments);
      rig.close();
    }
  }

  @Test
  void battleDeathsDebitRealRegimentsAndCommitmentsAndPersistTheResult() {
    Regiment infantry = regiment(rig.attacker, "infantry", 8, true, false);
    Regiment artillery = regiment(rig.attacker, "artillery", 9, true, true);
    Regiment defenders = regiment(rig.defender, "guard", 6, false, false);
    WarCommitmentService.commitAllParticipants(rig.war);
    long beforeWrites = rig.warWrites();

    BattleCasualtyService.applyBattleCasualties(
        rig.war, battle, Map.of("attacker", 12, "defender", 5));

    assertEquals(6, infantry.getCurrentSlots());
    assertEquals(9, artillery.getCurrentSlots());
    assertEquals(5, defenders.getCurrentSlots());
    assertEquals(6, committed(rig.attacker, "infantry"));
    assertEquals(5, committed(rig.defender, "guard"));
    assertEquals(beforeWrites + 1, rig.warWrites());
    verify(rig.alice).sendMessage("§7Campaign battle losses: attacker 2, defender 1 regiments.");
    verify(rig.bob).sendMessage("§7Campaign battle losses: attacker 2, defender 1 regiments.");
  }

  @Test
  void reportedLossesMustNotExceedTheAvailableRegiments() {
    Regiment only = regiment(rig.attacker, "infantry", 1, true, false);
    WarCommitmentService.commitFaction(rig.war, rig.attacker);

    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 25));

    assertEquals(0, only.getCurrentSlots());
    assertTrue(WarCommitmentService.getCommitmentsForWar(rig.war.getId()).isEmpty());
    verify(rig.alice).sendMessage("§7Campaign battle losses: attacker 1, defender 0 regiments.");
  }

  @Test
  void levyCasualtiesReduceTheSourceArmyWhenTheSubjectDoesNotFightDirectly() {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction subject = rig.domain.saved("levy_source", "SubjectLeader");
    subject.addMember("SubjectSecond");
    rig.domain.subject(rig.attacker, subject);
    Regiment source = regiment(subject, "guard", 6, false, false);
    source.setSentToOverlord(2);
    WarCommitmentService.commitAllParticipants(rig.war);
    List<WarCommitment> rows = WarCommitmentService.getCommitmentsForWar(rig.war.getId());
    assertEquals(1, rows.size());
    assertTrue(rows.getFirst().isLevyRow());
    assertEquals(2, rows.getFirst().count());
    assertEquals(subject.getId(), rows.getFirst().sourceFactionId());

    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 5));

    assertEquals(5, source.getCurrentSlots());
    assertEquals(1, source.sentToOverlord());
    assertEquals(1, WarCommitmentService.getCommitmentsForWar(rig.war.getId()).getFirst().count());
    assertTrue(
        rig.writes.stream()
            .anyMatch(
                write ->
                    write.value() instanceof FactionData saved
                        && saved.id.equals(subject.getId())));
  }

  @Test
  void casualtyAllocationMustNotOverflowWhenConfiguredArmyWeightsExceedIntegerMaximum() {
    Regiment first = regiment(rig.attacker, "first", 1_500_000_000, true, false);
    Regiment second = regiment(rig.attacker, "second", 1_500_000_000, true, false);
    WarCommitmentService.commitFaction(rig.war, rig.attacker);

    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 5));

    assertEquals(2_999_999_999L, (long) first.getCurrentSlots() + second.getCurrentSlots());
    assertEquals(
        2_999_999_999L,
        WarCommitmentService.getCommitmentsForWar(rig.war.getId()).stream()
            .mapToLong(WarCommitment::count)
            .sum());
  }

  @Test
  void rawCommitmentQueriesAreCaseInsensitiveUnderTurkishLocale() {
    regiment(rig.attacker, "infantry", 3, true, false);
    WarCommitmentService.commitFaction(rig.war, rig.attacker);
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals(
          3,
          WarCommitmentService.totalCommittedRegiments(
              rig.war.getId(), Set.of(rig.attacker.getId().toUpperCase(Locale.ROOT)), null));
    } finally {
      Locale.setDefault(original);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void endingOneVassalageRemovesOnlyItsLevyRowsAndKeepsOtherCommitments(boolean subjectFirst) {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction released = rig.domain.saved("released_subject", "ReleasedLeader");
    Faction sibling = rig.domain.saved("remaining_subject", "RemainingLeader");
    rig.domain.subject(rig.attacker, released);
    rig.domain.subject(rig.attacker, sibling);
    regiment(released, "guard", 6, false, false);
    regiment(sibling, "guard", 6, false, false);
    regiment(rig.attacker, "infantry", 3, true, false);
    WarCommitmentService.commitAllParticipants(rig.war);
    assertEquals(3, WarCommitmentService.getCommitmentsForWar(rig.war.getId()).size());

    assertTrue(
        RelationManager.endVassalage(
            subjectFirst ? released : rig.attacker, subjectFirst ? rig.attacker : released, false));

    List<WarCommitment> remaining = WarCommitmentService.getCommitmentsForWar(rig.war.getId());
    assertEquals(2, remaining.size());
    assertTrue(remaining.stream().noneMatch(row -> released.getId().equals(row.sourceFactionId())));
    assertEquals(
        1,
        remaining.stream()
            .filter(row -> sibling.getId().equals(row.sourceFactionId()))
            .mapToInt(WarCommitment::count)
            .sum());
    assertEquals(3, committed(rig.attacker, "infantry"));
    assertNull(RelationManager.getOverlord(released));
    assertEquals(rig.attacker.getId(), RelationManager.getOverlord(sibling));
  }

  @Test
  void commitmentSnapshotsAreStableAndDebitsRejectInvalidRequestsWithoutLosingOtherRows() {
    regiment(rig.attacker, "infantry", 3, true, false);
    regiment(rig.attacker, "reserve", 0, true, false);
    List<WarCommitment> first = WarCommitmentService.commitFaction(rig.war, rig.attacker);
    assertEquals(first, WarCommitmentService.commitFaction(rig.war, rig.attacker));
    assertThrows(UnsupportedOperationException.class, () -> first.add(first.getFirst()));
    assertEquals(
        0,
        WarCommitmentService.debitCount(rig.war.getId(), rig.attacker.getId(), null, "reserve", 1));
    assertEquals(
        0,
        WarCommitmentService.debitCount(rig.war.getId(), rig.attacker.getId(), null, "missing", 1));
    assertEquals(0, WarCommitmentService.debitCount(rig.war.getId(), null, null, "infantry", 1));
    assertEquals(
        0,
        WarCommitmentService.debitCount(
            rig.war.getId(), rig.attacker.getId(), null, "infantry", 0));
    assertEquals(
        0, WarCommitmentService.debitCount(-123, rig.attacker.getId(), null, "infantry", 1));
    assertEquals(first, WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertEquals(
        3,
        WarCommitmentService.debitCount(
            rig.war.getId(), rig.attacker.getId(), null, "infantry", 10));
    assertEquals(1, WarCommitmentService.getCommitmentsForWar(rig.war.getId()).size());
    assertEquals(
        0,
        WarCommitmentService.totalCommittedRegiments(rig.war.getId(), Set.of("unrelated"), null));
    assertEquals(0, WarCommitmentService.totalCommittedRegiments(rig.war.getId(), Set.of(), null));
    assertEquals(
        0,
        WarCommitmentService.totalCommittedRegiments(
            rig.war.getId(), Set.of(rig.attacker.getId()), row -> false));
  }

  @Test
  void optionalCommitmentInputsLeaveAnExistingSnapshotUnchanged() {
    regiment(rig.attacker, "infantry", 3, true, false);
    List<WarCommitment> rows = WarCommitmentService.commitFaction(rig.war, rig.attacker);
    assertTrue(WarCommitmentService.commitFaction(null, rig.attacker).isEmpty());
    assertTrue(WarCommitmentService.snapshotLevyForSide(null, rig.war.getAttackers()).isEmpty());
    assertTrue(WarCommitmentService.snapshotLevyForFighter(null, rig.attacker).isEmpty());
    assertTrue(
        WarCommitmentService.snapshotLevyForFighter(rig.war, rig.domain.saved("neutral", "Neutral"))
            .isEmpty());
    WarCommitmentService.commitAllParticipants(null);
    WarCommitmentService.onVassalageEnded(null, rig.attacker);
    WarCommitmentService.removeLevySubtree(null);
    WarCommitmentService.restoreCommitments(-1, rows);
    assertEquals(rows, WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertEquals(
        3,
        WarCommitmentService.totalCommittedRegiments(
            rig.war.getId(),
            new HashSet<>(Arrays.asList(null, rig.attacker.getId())),
            row -> true));
    WarCommitmentService.restoreCommitments(rig.war.getId(), null);
    assertTrue(WarCommitmentService.getCommitmentsForWar(rig.war.getId()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {5, 15, 40})
  void ownProvinceMilitiaAbsorbsLossesBeforeProfessionalSoldiers(int deaths) {
    rig.defender.addProvince(20);
    Regiment militia = regiment(rig.defender, "militia", 2, false, false);
    Regiment guard = regiment(rig.defender, "guard", 3, true, false);
    regiment(rig.defender, "equipment", 8, false, true);
    WarCommitmentService.commitFaction(rig.war, rig.defender);

    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("defender", deaths));

    int actualLoss = Math.min(5, deaths / 5);
    assertEquals(Math.max(0, 2 - actualLoss), militia.getCurrentSlots());
    assertEquals(3 - Math.max(0, actualLoss - 2), guard.getCurrentSlots());
    assertEquals(
        5 - actualLoss, committed(rig.defender, "militia") + committed(rig.defender, "guard"));
    verify(rig.bob)
        .sendMessage(
            "§7Campaign battle losses: attacker 0, defender " + actualLoss + " regiments.");
  }

  @Test
  void inapplicableAndSubRegimentDeathsLeaveTheRealArmyAndSnapshotUnchanged() {
    Regiment guard = regiment(rig.attacker, "guard", 6, true, false);
    List<WarCommitment> rows = WarCommitmentService.commitFaction(rig.war, rig.attacker);
    long writes = rig.warWrites();
    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 4));
    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 0));
    BattleCasualtyService.applyBattleCasualties(rig.war, battle, null);
    BattleCasualtyService.applyBattleCasualties(null, battle, Map.of("attacker", 10));
    battle.setBattleType(BattleType.RAID);
    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 10));
    battle.setBattleType(BattleType.FIELD);
    battle.setProvinceId(null);
    rig.war.setScheduledBattleProvinceId(null);
    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 10));
    assertEquals(6, guard.getCurrentSlots());
    assertEquals(rows, WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertEquals(writes, rig.warWrites());
    Cache.warBattleDeathsPerRegimentLoss = 0;
    assertEquals(1, BattleCasualtyService.regimentLossesForDeaths(9));
    assertEquals(0, BattleCasualtyService.regimentLossesForDeaths(-1));
  }

  @Test
  void repeatedLevySnapshotsDoNotDuplicateRowsAndSeveringTheLastSourceClearsThem() {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction subject = rig.domain.saved("last_levy_source", "SubjectLeader");
    rig.domain.subject(rig.attacker, subject);
    regiment(subject, "guard", 0, false, false);
    assertTrue(subject.getMilitary().adminAdjustSlots("guard", 2).allowed());
    assertEquals(2, subject.getMilitary().getRegiment("guard").getPaidSlots());
    List<WarCommitment> rows = WarCommitmentService.snapshotLevyForFighter(rig.war, rig.attacker);
    assertEquals(1, rows.size());
    assertTrue(WarCommitmentService.snapshotLevyForFighter(rig.war, rig.attacker).isEmpty());
    assertEquals(rows, WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertTrue(RelationManager.endVassalage(subject, rig.attacker, false));
    assertTrue(WarCommitmentService.getCommitmentsForWar(rig.war.getId()).isEmpty());
    assertEquals(2, subject.getMilitary().getRegiment("guard").getCurrentSlots());
    WarCommitmentService.removeLevySubtree(subject);
    assertTrue(WarCommitmentService.getCommitmentsForWar(rig.war.getId()).isEmpty());
  }

  @Test
  void persistedCommitmentSnapshotsRestoreTheirValuesAndDoNotAliasTheInputList() {
    paidRegiment(rig.attacker, "infantry", 3, true);
    paidRegiment(rig.defender, "guard", 2, false);
    WarCommitmentService.commitAllParticipants(rig.war);
    List<WarCommitment> original =
        List.copyOf(WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    WarData saved = WarMapper.toData(rig.war);
    WarCommitmentService.clearCommitments(rig.war.getId());

    War restored =
        WarMapper.fromData(JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(saved), WarData.class));

    assertNotNull(restored);
    assertEquals(original, WarCommitmentService.getCommitmentsForWar(restored.getId()));
    ArrayList<WarCommitment> replacement = new ArrayList<>(original);
    WarCommitmentService.restoreCommitments(restored.getId(), replacement);
    replacement.clear();
    assertEquals(original, WarCommitmentService.getCommitmentsForWar(restored.getId()));
    assertThrows(
        UnsupportedOperationException.class,
        () -> WarCommitmentService.getCommitmentsForWar(restored.getId()).clear());
    BattleCasualtyService.applyBattleCasualties(restored, battle, Map.of("attacker", 5));
    assertEquals(2, committed(rig.attacker, "infantry"));
    assertEquals(2, rig.attacker.getMilitary().getRegiment("infantry").getCurrentSlots());
    assertEquals(2, committed(rig.defender, "guard"));
  }

  @Test
  void releasingANestedFighterRemovesItsHeldLeviesButPreservesItsOwnArmyAndOtherSources() {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction middle = rig.domain.saved("middle_fighter", "MiddleLeader");
    Faction grandchild = rig.domain.saved("grandchild_source", "GrandchildLeader");
    Faction sibling = rig.domain.saved("sibling_source", "SiblingLeader");
    rig.domain.subject(rig.attacker, middle);
    rig.domain.subject(middle, grandchild);
    rig.domain.subject(rig.attacker, sibling);
    assertTrue(rig.war.getAttackers().getMainParticipants().getFirst().addBacker(middle));
    paidRegiment(rig.attacker, "infantry", 3, true);
    paidRegiment(middle, "guard", 2, false);
    paidRegiment(grandchild, "guard", 4, false);
    paidRegiment(sibling, "guard", 4, false);
    WarCommitmentService.commitAllParticipants(rig.war);
    List<WarCommitment> before =
        List.copyOf(WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertEquals(4, before.size());
    assertTrue(
        before.stream()
            .anyMatch(
                row ->
                    row.isLevyRow()
                        && middle.getId().equals(row.factionId())
                        && grandchild.getId().equals(row.sourceFactionId())));

    assertTrue(RelationManager.endVassalage(rig.attacker, middle, false));

    List<WarCommitment> after = WarCommitmentService.getCommitmentsForWar(rig.war.getId());
    assertEquals(3, after.size());
    assertTrue(
        after.stream()
            .filter(WarCommitment::isLevyRow)
            .allMatch(row -> sibling.getId().equals(row.sourceFactionId())));
    assertEquals(3, committed(rig.attacker, "infantry"));
    assertEquals(2, committed(middle, "guard"));
    assertEquals(2, middle.getMilitary().getRegiment("guard").getPaidSlots());
    assertEquals(4, grandchild.getMilitary().getRegiment("guard").getPaidSlots());
    assertEquals(middle.getId(), RelationManager.getOverlord(grandchild));
    assertNull(RelationManager.getOverlord(middle));
  }

  @Test
  void casualtiesWithoutAnEligibleArmyDoNotAnnounceInventedLosses() {
    paidRegiment(rig.attacker, "defensive_only", 3, false);
    WarCommitmentService.commitAllParticipants(rig.war);
    List<WarCommitment> before =
        List.copyOf(WarCommitmentService.getCommitmentsForWar(rig.war.getId()));

    BattleCasualtyService.applyBattleCasualties(
        rig.war, battle, Map.of("attacker", 15, "defender", 10));

    assertEquals(before, WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertEquals(3, rig.attacker.getMilitary().getRegiment("defensive_only").getCurrentSlots());
    verify(rig.alice, never()).sendMessage(startsWith("§7Campaign battle losses:"));
    verify(rig.bob, never()).sendMessage(startsWith("§7Campaign battle losses:"));
  }

  @Test
  void militiaOnlyDefendersReportTheirActualLossWhenDeathsExceedTheirWholeArmy() {
    rig.defender.addProvince(20);
    Regiment militia = paidRegiment(rig.defender, "militia", 2, false);
    WarCommitmentService.commitAllParticipants(rig.war);
    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("defender", 30));
    assertEquals(0, militia.getCurrentSlots());
    assertEquals(0, committed(rig.defender, "militia"));
    verify(rig.bob).sendMessage("§7Campaign battle losses: attacker 0, defender 2 regiments.");
  }

  @Test
  void anUnconfiguredPublicBattleCannotDebitCampaignRegiments() {
    Regiment infantry = paidRegiment(rig.attacker, "infantry", 3, true);
    WarCommitmentService.commitAllParticipants(rig.war);
    Battle unconfigured = new Battle("not_configured");
    unconfigured.setWarId(rig.war.getId());
    unconfigured.setProvinceId(20);
    assertNull(unconfigured.getBattleType());
    assertFalse(BattleCasualtyService.shouldApply(rig.war, unconfigured, Map.of("attacker", 10)));
    BattleCasualtyService.applyBattleCasualties(rig.war, unconfigured, Map.of("attacker", 10));
    assertEquals(3, infantry.getCurrentSlots());
    assertEquals(3, committed(rig.attacker, "infantry"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"present", "missing_regiment", "missing_source"})
  void legacySourceSnapshotsRemainSafeAfterArmyConfigurationOrFactionRemoval(String sourceState) {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction source = rig.domain.saved("legacy_source", "SourceLeader");
    source.addMember("SourceSecond");
    rig.domain.subject(rig.attacker, source);
    Regiment guard = paidRegiment(source, "guard", 2, false);
    Regiment reserve = paidRegiment(source, "reserve", 0, false);
    assertTrue(rig.war.getAttackers().getMainParticipants().getFirst().addBacker(source));
    WarCommitmentService.commitAllParticipants(rig.war);
    rig.war.getAttackers().getMainParticipants().getFirst().clean(source);
    assertEquals(1, WarCommitmentService.snapshotLevyForFighter(rig.war, rig.attacker).size());
    guard.setSentToOverlord(2);
    WarData saved = WarMapper.toData(rig.war);
    if (sourceState.equals("missing_regiment")) source.getMilitary().getRegiments().remove(guard);
    if (sourceState.equals("missing_source")) FactionManager.deleteFaction(source);
    if (!sourceState.equals("present")) {
      WarCommitmentService.clearCommitments(rig.war.getId());
      assertNotNull(
          WarMapper.fromData(JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(saved), WarData.class)));
    }
    int defenderSlots = rig.defender.getMilitary().getManpower(false);

    assertDoesNotThrow(
        () -> BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 5)));

    assertEquals(
        1,
        WarCommitmentService.getCommitmentsForWar(rig.war.getId()).stream()
            .filter(WarCommitment::isLevyRow)
            .mapToInt(WarCommitment::count)
            .sum());
    assertEquals(1, committed(source, "guard"));
    assertEquals(0, reserve.getCurrentSlots());
    assertEquals(defenderSlots, rig.defender.getMilitary().getManpower(false));
    if (sourceState.equals("present")) {
      assertEquals(1, guard.getCurrentSlots());
      assertEquals(1, guard.sentToOverlord());
    } else if (sourceState.equals("missing_regiment")) {
      assertNull(source.getMilitary().getRegiment("guard"));
      assertEquals(2, guard.getCurrentSlots());
    } else {
      assertNull(FactionManager.getByString(source.getId()));
      assertEquals(2, guard.getCurrentSlots());
    }
  }

  @Test
  void proportionalLevyLossesChooseTheLargerSourcesWithoutDebitingZeroAllocations() {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction small = rig.domain.saved("small_levy", "SmallLeader");
    Faction large = rig.domain.saved("large_levy", "LargeLeader");
    Faction enemySource = rig.domain.saved("enemy_levy", "EnemyLeader");
    for (int i = 0; i < 3; i++) large.addMember("LargeMember" + i);
    enemySource.addMember("EnemyMember1");
    enemySource.addMember("EnemyMember2");
    rig.domain.subject(rig.attacker, small);
    rig.domain.subject(rig.attacker, large);
    rig.domain.subject(rig.defender, enemySource);
    Regiment smallGuard = paidRegiment(small, "guard", 1, false);
    Regiment largeLight = paidRegiment(large, "light", 1, false);
    Regiment largeHeavy = paidRegiment(large, "heavy", 9, false);
    Regiment enemyGuard = paidRegiment(enemySource, "guard", 3, false);
    assertTrue(rig.war.getAttackers().getMainParticipants().getFirst().addBacker(large));
    WarCommitmentService.commitAllParticipants(rig.war);
    rig.war.getAttackers().getMainParticipants().getFirst().clean(large);
    assertEquals(1, WarCommitmentService.snapshotLevyForFighter(rig.war, rig.attacker).size());
    rig.attacker.getMilitary().getLevies();
    rig.defender.getMilitary().getLevies();
    List<WarCommitment> before =
        List.copyOf(WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertEquals(
        4,
        before.stream()
            .filter(row -> large.getId().equals(row.sourceFactionId()))
            .mapToInt(WarCommitment::count)
            .sum());

    BattleCasualtyService.applyBattleCasualties(
        rig.war, battle, Map.of("attacker", 5, "defender", 5));

    assertEquals(1, smallGuard.getCurrentSlots());
    assertEquals(1, largeLight.getCurrentSlots());
    assertEquals(8, largeHeavy.getCurrentSlots());
    assertEquals(2, enemyGuard.getCurrentSlots());
    assertEquals(1, committed(large, "light"));
    assertEquals(8, committed(large, "heavy"));
    List<WarCommitment> after = WarCommitmentService.getCommitmentsForWar(rig.war.getId());
    assertEquals(
        1,
        after.stream()
            .filter(row -> small.getId().equals(row.sourceFactionId()))
            .mapToInt(WarCommitment::count)
            .sum());
    assertEquals(
        3,
        after.stream()
            .filter(row -> large.getId().equals(row.sourceFactionId()))
            .mapToInt(WarCommitment::count)
            .sum());
    assertEquals(
        2,
        after.stream()
            .filter(row -> enemySource.getId().equals(row.sourceFactionId()))
            .mapToInt(WarCommitment::count)
            .sum());
    verify(rig.alice).sendMessage("§7Campaign battle losses: attacker 1, defender 1 regiments.");
  }

  @Test
  void aSmallerCurrentLevyLedgerDoesNotGoNegativeWhenAnOlderSnapshotTakesLosses() {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction source = rig.domain.saved("shrinking_source", "SourceLeader");
    source.addMember("DepartingOne");
    source.addMember("DepartingTwo");
    rig.domain.subject(rig.attacker, source);
    Regiment guard = paidRegiment(source, "guard", 4, false);
    assertTrue(rig.war.getAttackers().getMainParticipants().getFirst().addBacker(source));
    WarCommitmentService.commitAllParticipants(rig.war);
    rig.war.getAttackers().getMainParticipants().getFirst().clean(source);
    assertEquals(
        3, WarCommitmentService.snapshotLevyForFighter(rig.war, rig.attacker).getFirst().count());
    rig.attacker.getMilitary().getLevies();
    assertEquals(3, guard.sentToOverlord());
    source.forceRemoveMember("DepartingOne");
    source.forceRemoveMember("DepartingTwo");
    rig.attacker.getMilitary().getLevies();
    assertEquals(1, guard.sentToOverlord());

    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 10));

    assertEquals(2, guard.getCurrentSlots());
    assertEquals(2, committed(source, "guard"));
    assertEquals(0, guard.sentToOverlord());
    assertEquals(
        1,
        WarCommitmentService.getCommitmentsForWar(rig.war.getId()).stream()
            .filter(WarCommitment::isLevyRow)
            .mapToInt(WarCommitment::count)
            .sum());
    assertEquals(List.of("SourceLeader"), source.getMembers());
  }

  @Test
  void publicSnapshotRestoreToleratesAnEmptyLevyRowAlongsideAValidSource() {
    rig.domain.lawGroup("conscription", Map.of("effects.faction.modifiers", List.of("LEVY(100)")));
    Faction funded = rig.domain.saved("funded_source", "FundedLeader");
    Faction empty = rig.domain.saved("empty_source", "EmptyLeader");
    rig.domain.subject(rig.attacker, funded);
    rig.domain.subject(rig.attacker, empty);
    Regiment guard = paidRegiment(funded, "guard", 2, false);
    Regiment emptyGuard = paidRegiment(empty, "guard", 0, false);
    WarCommitmentService.commitAllParticipants(rig.war);
    List<WarCommitment> restored =
        new ArrayList<>(WarCommitmentService.getCommitmentsForWar(rig.war.getId()));
    assertEquals(1, restored.size());
    restored.add(
        new WarCommitment(
            rig.war.getId(),
            rig.attacker.getId(),
            empty.getId(),
            WarCommitment.LEVY_REGIMENT_ID,
            0,
            restored.getFirst().committedAt()));
    WarCommitmentService.restoreCommitments(rig.war.getId(), restored);
    rig.attacker.getMilitary().getLevies();

    BattleCasualtyService.applyBattleCasualties(rig.war, battle, Map.of("attacker", 5));

    assertEquals(1, guard.getCurrentSlots());
    assertEquals(0, emptyGuard.getCurrentSlots());
    assertTrue(
        WarCommitmentService.getCommitmentsForWar(rig.war.getId()).stream()
            .allMatch(row -> row.count() == 0));
    verify(rig.alice).sendMessage("§7Campaign battle losses: attacker 1, defender 0 regiments.");
  }

  private Regiment paidRegiment(Faction faction, String id, int slots, boolean offense) {
    Regiment regiment = regiment(faction, id, 0, offense, false);
    if (slots > 0) assertTrue(faction.getMilitary().adminAdjustSlots(id, slots).allowed());
    return regiment;
  }

  private Regiment regiment(
      Faction faction, String id, int slots, boolean offense, boolean equipment) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("default-slots", slots);
    yaml.set("offense", offense);
    yaml.set("equipment", equipment);
    Regiment regiment = new Regiment(id, yaml);
    faction.getMilitary().getRegiments().add(regiment);
    return regiment;
  }

  private int committed(Faction faction, String regiment) {
    return WarCommitmentService.getCommitmentsForWar(rig.war.getId()).stream()
        .filter(row -> row.factionId().equals(faction.getId()) && row.regimentId().equals(regiment))
        .mapToInt(WarCommitment::count)
        .sum();
  }
}
