package net.tfminecraft.simplefactions.war.campaign.raid.fight;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.campaign.raid.*;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.*;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignRaidFightBoundaryCoverageTest {
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

  private CampaignRaid fight() {
    CampaignRaid raid = muster();
    assertEquals(
        JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war, rig.alice.getUniqueId(), "Alice", rig.attacker, raid.getId(), rig.now()));
    rig.time(raid.getMusterEndsAt());
    CampaignRaidLaunchService.startFight(rig.war, rig.now());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
    assertTrue(BattleManager.getByString(raid.getBattleId()).hasStarted());
    return raid;
  }

  @Test
  void incompleteRaidIdentityCannotThrowOrCreateAPartialBattle() {
    CampaignRaid raid = muster();
    raid.setId(null);
    assertNull(CampaignRaidBattleService.createAndStart(rig.war, raid, rig.now()));
    assertTrue(BattleManager.get().isEmpty());
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
  }

  @Test
  void missingInputsAndAnUnavailableWorldCannotLaunchABattle() {
    CampaignRaid raid = muster();
    assertNull(CampaignRaidBattleService.createAndStart(null, raid, rig.now()));
    assertNull(CampaignRaidBattleService.createAndStart(rig.war, null, rig.now()));
    assertNull(CampaignRaidBattleService.createAndStart(rig.war, raid, null));
    when(Bukkit.getWorld(anyString())).thenReturn(null);
    assertNull(CampaignRaidBattleService.createAndStart(rig.war, raid, rig.now()));
    assertTrue(BattleManager.get().isEmpty());
  }

  @Test
  void repeatingFightCreationKeepsTheRunningBattleAndItsStartTime() {
    CampaignRaid raid = fight();
    Battle battle = BattleManager.getByString(raid.getBattleId());
    var startedAt = battle.getStartedAt();
    assertSame(
        battle, CampaignRaidBattleService.createAndStart(rig.war, raid, rig.now().plusSeconds(1)));
    assertEquals(startedAt, battle.getStartedAt());
    assertEquals(List.of(battle), BattleManager.get());
    assertTrue(battle.getAllParticipants().contains(rig.alice));
  }

  @Test
  void savedRaidRecognitionRequiresTheCorrectWarAndRaidBattleType() {
    CampaignRaid raid = muster();
    Battle legacy = BattleFactory.createBlank(BattleType.RAID, raid.getId());
    legacy.setWarId(rig.war.getId());
    BattleManager.addBattle(legacy);
    assertTrue(CampaignRaidBattleService.isCampaignRaidBattle(rig.war, legacy));
    assertFalse(legacy.isCampaignRaid());
    CampaignRaidResumeService.applyLoadedBattle(legacy);
    assertTrue(legacy.isCampaignRaid());
    assertEquals(legacy.getId(), raid.getBattleId());
    assertTrue(CampaignRaidBattleService.isCampaignRaidBattle(null, legacy));
    legacy.setWarId(rig.war.getId() + 100);
    assertFalse(CampaignRaidBattleService.isCampaignRaidBattle(rig.war, legacy));
    CampaignRaidBattleService.markAsCampaignRaidIfActive(rig.war, null);
    assertFalse(CampaignRaidBattleService.isCampaignRaidEvent(null, null));
    assertFalse(
        CampaignRaidBattleService.isCampaignRaidEvent(
            rig.war, event("unrelated", rig.war.getId(), false)));
    assertSame(raid, CampaignRaidService.getActive(rig.war));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unrelatedSavedBattleCannotClaimAnActiveRaidEvenWithLegacyFlag(boolean raidType) {
    CampaignRaid raid = muster();
    raid.setFightEndsAt(rig.now().plusSeconds(60));
    Battle unrelated =
        BattleFactory.createBlank(
            raidType ? BattleType.RAID : BattleType.FIELD, "other_saved_battle");
    unrelated.setWarId(rig.war.getId());
    unrelated.setCampaignRaid(true);
    BattleManager.addBattle(unrelated);
    CampaignRaidBattleService.markAsCampaignRaidIfActive(rig.war, unrelated);
    CampaignRaidResumeService.applyLoadedBattle(unrelated);
    assertNull(raid.getBattleId());
    assertNull(unrelated.getStartedAt());
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
  }

  @Test
  void missingSavedCoalitionCannotRecordAnUnsentReminder() {
    CampaignRaid raid = muster();
    raid.setAttackerCoalition(null);
    raid.setMusterEndsAt(rig.now().plusSeconds(30));
    assertFalse(CampaignRaidMusterReminderService.tryFireReminder(rig.war, 30, rig.now()));
    assertTrue(raid.getMusterRemindersSent().isEmpty());
  }

  @Test
  void enrollmentCanRestoreBothRosterReferencesAndRemainsIdempotent() {
    CampaignRaid raid = fight();
    Battle battle = BattleManager.getByString(raid.getBattleId());
    var attackers = CampaignRaidWarbandService.getAttackerWarband(raid);
    var defenders = CampaignRaidWarbandService.getDefenderWarband(raid);
    battle.getSideById("attacker").getBands().clear();
    battle.getSideById("defender").getBands().clear();
    CampaignRaidBattleService.enrollRaidWarbands(null, battle, null, null);
    CampaignRaidBattleService.enrollRaidWarbands(raid, null, null, null);
    CampaignRaidBattleService.enrollRaidWarbands(raid, battle, null, null);
    CampaignRaidBattleService.enrollRaidWarbands(raid, battle, attackers, defenders);
    assertEquals(List.of(attackers), battle.getSideById("attacker").getBands());
    assertEquals(List.of(defenders), battle.getSideById("defender").getBands());
    assertTrue(battle.getAllParticipants().contains(rig.alice));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void orphanedRaidBossBarsAreRemovedWhenTheWarLinkDisappears(boolean missingId) {
    CampaignRaid raid = fight();
    Battle battle = BattleManager.getByString(raid.getBattleId());
    var bars =
        rig.bars.stream().filter(b -> b.isVisible() && b.getPlayers().contains(rig.alice)).toList();
    assertEquals(2, bars.size());
    battle.setWarId(missingId ? null : -999);
    CampaignRaidBossBarService.tickCampaignRaid(battle);
    assertTrue(bars.stream().noneMatch(org.bukkit.boss.BossBar::isVisible));
    assertTrue(bars.stream().allMatch(b -> b.getPlayers().isEmpty()));
    assertTrue(battle.hasStarted());
  }

  @Test
  void bossBarFallbackUsesTheBattleIdAndRebuildsAfterAClear() {
    CampaignRaid raid = fight();
    Battle battle = BattleManager.getByString(raid.getBattleId());
    CampaignRaidBossBarService.clear(null);
    CampaignRaidBossBarService.onFightStarted(null, raid);
    CampaignRaidBossBarService.update(battle, null);
    CampaignRaidBossBarService.tickCampaignRaid(null);
    CampaignRaidBossBarService.clear(battle);
    battle.setDisplayName(" ");
    CampaignRaidBossBarService.update(battle, raid);
    assertTrue(
        rig.bars.stream().anyMatch(b -> b.isVisible() && b.getTitle().startsWith(battle.getId())));
    var bars = rig.bars.stream().filter(org.bukkit.boss.BossBar::isVisible).toList();
    raid.setState(CampaignRaidState.MUSTER);
    CampaignRaidBossBarService.tickCampaignRaid(battle);
    assertTrue(bars.stream().noneMatch(org.bukkit.boss.BossBar::isVisible));
  }

  @Test
  void unrelatedEndEventsCannotCancelTheCurrentRaid() {
    CampaignRaid raid = muster();
    CampaignRaidBattleEndService listener = new CampaignRaidBattleEndService();
    listener.onBattleEnded(null);
    listener.onBattleEnded(event("missing", null, true));
    listener.onBattleEnded(event("missing", -999, true));
    listener.onBattleEnded(event("missing", rig.war.getId(), false));
    listener.onBattleEnded(event(raid.getId(), rig.war.getId(), true));
    assertSame(raid, CampaignRaidService.getActive(rig.war));
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
  }

  @Test
  void malformedRestoredFightWithoutIdsIsCleanedWithoutTouchingOtherBattles() {
    CampaignRaid raid = muster();
    raid.setState(CampaignRaidState.FIGHTING);
    raid.setId(null);
    raid.setBattleId(null);
    raid.setFightEndsAt(rig.now().plusSeconds(60));
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "unrelated_manual");
    BattleManager.addBattle(manual);
    CampaignRaidResumeService.resumeAll();
    assertNull(CampaignRaidService.getActive(rig.war));
    assertEquals(List.of(manual), BattleManager.get());
  }

  @Test
  void absentTimerContextDoesNotEndAnUnrelatedBattle() {
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "manual");
    BattleManager.addBattle(manual);
    CampaignRaidFightScheduler.onFightEnd(null, rig.now());
    CampaignRaidFightScheduler.onFightEnd(rig.war, rig.now());
    CampaignRaid raid = muster();
    raid.setState(CampaignRaidState.FIGHTING);
    CampaignRaidFightScheduler.onFightEnd(rig.war, rig.now());
    assertEquals(List.of(manual), BattleManager.get());
    assertSame(raid, CampaignRaidService.getActive(rig.war));
  }

  @Test
  void incompleteReminderContextCannotSendOrRecordAReminder() {
    assertEquals(-1, CampaignRaidMusterReminderService.findNextDueReminderOffset(null, rig.now()));
    assertFalse(CampaignRaidMusterReminderService.tryFireReminder(null, 30, rig.now()));
    CampaignRaidMusterReminderService.schedule(null, rig.now());
    CampaignRaidMusterReminderService.schedule(rig.war, null);
    CampaignRaidMusterReminderService.schedule(rig.war, rig.now());
    CampaignRaidMusterScheduler.onMusterEnd(null, rig.now());
    CampaignRaidMusterScheduler.onMusterEnd(rig.war, null);
    CampaignRaid raid = muster();
    assertFalse(CampaignRaidFightScheduler.processOverdue(rig.war, rig.now()));
    raid.setMusterEndsAt(null);
    assertEquals(
        -1, CampaignRaidMusterReminderService.findNextDueReminderOffset(rig.war, rig.now()));
    assertFalse(CampaignRaidMusterReminderService.tryFireReminder(rig.war, 30, rig.now()));
    assertTrue(raid.getMusterRemindersSent().isEmpty());
  }

  @Test
  void absentOrEmptyRaidRostersDoNotDeclareAnAttackerDefeat() {
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "manual_roster");
    Battle empty = BattleFactory.createBlank(BattleType.RAID, "empty_raid_roster");
    Battle incomplete = BattleFactory.createBlank(BattleType.RAID, "incomplete_raid_roster");
    BattleFactory.resetLayout(incomplete);
    for (Battle battle : java.util.Arrays.asList(null, manual, empty, incomplete)) {
      assertFalse(RaidAttackerEliminationService.isAttackerSideEliminated(battle));
      assertEquals(0, RaidAttackerEliminationService.countActiveAttackers(battle));
      assertEquals(0, RaidAttackerEliminationService.countAttackerRoster(battle));
      RaidAttackerEliminationService.markOut(battle, null);
      assertFalse(RaidAttackerEliminationService.isMarkedOut(battle, null));
      RaidAttackerEliminationService.clearBattleState(battle);
    }
    RaidAttackerEliminationService.markOut(null, rig.alice.getUniqueId());
    assertFalse(RaidAttackerEliminationService.isMarkedOut(null, rig.alice.getUniqueId()));
  }

  @Test
  void storedBattleAliasesAreRecognizedAndMissingDisplayNamesFallBackToTheTarget() {
    CampaignRaid raid = muster();
    raid.setBattleId("legacy_battle_alias");
    assertTrue(CampaignRaidBattleService.matchesRaidBattle(raid, "legacy_battle_alias"));
    raid.setDisplayName(null);
    Battle battle = CampaignRaidBattleService.createAndStart(rig.war, raid, rig.now());
    assertNotNull(battle);
    assertEquals("Campaign raid at " + rig.target.getName(), battle.getDisplayName());
    assertEquals(raid.getId(), raid.getBattleId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"all", "attacker", "defender"})
  void aSavedRaidWithMissingSidesFailsToStartAndDoesNotConsumeAQuota(String missing) {
    CampaignRaid raid = muster();
    Battle incomplete = BattleFactory.createBlank(BattleType.RAID, raid.getId());
    incomplete.setCampaignRaid(true);
    incomplete.setWarId(rig.war.getId());
    if (missing.equals("all")) incomplete.clearSides();
    else incomplete.removeSide(missing);
    BattleManager.addBattle(incomplete);
    assertNull(CampaignRaidBattleService.createAndStart(rig.war, raid, rig.now()));
    assertFalse(incomplete.hasStarted());
    assertTrue(rig.war.getCampaignRaidsUsed().isEmpty());
    CampaignRaidLaunchService.startFight(rig.war, rig.now());
    assertNull(CampaignRaidService.getActive(rig.war));
    assertNull(BattleManager.getByString(incomplete.getId()));
  }

  private BattleEndedEvent event(String id, Integer warId, boolean raid) {
    return new BattleEndedEvent(
        id, BattleType.RAID, warId, null, Map.of(), Set.of(), BattleEndReason.TIMER, raid, false);
  }
}
