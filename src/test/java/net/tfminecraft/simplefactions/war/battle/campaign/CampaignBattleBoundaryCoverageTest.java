package net.tfminecraft.simplefactions.war.battle.campaign;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleJoinService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignBattleBoundaryCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void unrelatedCampaignsWithTheSameDisplayNameReceiveIndependentRosters() {
    Battle first = campaignBattle("first_front", rig.war);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, first);
    Warband firstAttackers = first.getSideById("attacker").getBands().getFirst();
    firstAttackers.addPlayer(rig.alice);

    War secondWar =
        new War(
            918732,
            rig.domain.saved("other_iron", "Cedar"),
            rig.domain.saved("other_river", "Delta"));
    WarManager.addWar(secondWar);
    Battle second = campaignBattle("second_front", secondWar);
    CampaignBattleRosterService.ensureEnrolledForced(secondWar, second);
    Warband secondAttackers = second.getSideById("attacker").getBands().getFirst();

    assertNotSame(firstAttackers, secondAttackers);
    assertTrue(firstAttackers.hasMember(rig.alice));
    assertFalse(secondAttackers.hasMember(rig.alice));
    assertNotEquals(firstAttackers.getId(), secondAttackers.getId());
    CampaignBattleRosterService.ensureEnrolledForced(secondWar, second);
    assertEquals(1, second.getSideById("attacker").getBands().size());
    assertEquals(1, second.getSideById("defender").getBands().size());
    BattlePersistenceService.deleteCampaignBattle(first);
    assertSame(secondAttackers, WarbandManager.getByString(secondAttackers.getId()));
    assertSame(second, BattleManager.getByString(second.getId()));
  }

  @Test
  void enrollingACampaignCannotReuseAPreexistingManualWarbandWithTheDisplaySlug() {
    Battle battle = campaignBattle("manual_collision", rig.war);
    String collisionId = BattleNamingService.campaignWarbandId(battle.getDisplayName(), "attacker");
    Warband manual = new Warband(collisionId, rig.alice);
    WarbandManager.addWarband(manual);

    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);

    Warband campaign = battle.getSideById("attacker").getBands().getFirst();
    assertNotSame(manual, campaign);
    assertTrue(campaign.isFaction());
    assertEquals(0, campaign.getMemberCount());
    assertSame(manual, WarbandManager.getByString(collisionId));
    assertEquals(rig.alice.getUniqueId(), manual.getLeaderId());
    assertTrue(manual.hasMember(rig.alice));
    assertFalse(manual.isFaction());
    BattlePersistenceService.deleteCampaignBattle(battle);
    assertSame(manual, WarbandManager.getByString(collisionId));
    assertTrue(manual.hasMember(rig.alice));
  }

  private Battle campaignBattle(String id, War war) {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, id);
    battle.setWarId(war.getId());
    battle.setProvinceId(11);
    battle.setDisplayName("Battle of Wilderness");
    battle.setLocked(false);
    BattleManager.addBattle(battle);
    return battle;
  }

  @Test
  void renamingABattlePreservesItsAttachedCampaignRosterAndMembers() {
    Battle battle = campaignBattle("renamed_front", rig.war);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    Warband attackers = battle.getSideById("attacker").getBands().getFirst();
    attackers.addPlayer(rig.alice);
    battle.setDisplayName("Battle of Emerald Crossing");
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    assertEquals(1, battle.getSideById("attacker").getBands().size());
    assertEquals(1, battle.getSideById("defender").getBands().size());
    assertSame(attackers, battle.getSideById("attacker").getBands().getFirst());
    assertTrue(attackers.hasMember(rig.alice));
  }

  @Test
  void finalizingAMissingWarDoesNotAffectAnyRegisteredManualBattle() {
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "manual_survivor");
    BattleManager.addBattle(manual);
    long writes = rig.warWrites();
    CampaignBattleOutcomeService.finalizeCampaignBattleAfterOutcome(null);
    assertSame(manual, BattleManager.getByString("manual_survivor"));
    assertEquals(writes, rig.warWrites());
    assertTrue(rig.war.isActive());
  }

  @Test
  void stableCampaignIdsSkipReservedManualNamesWithoutTakingTheirMembers() {
    Battle battle = campaignBattle("stable_collision", rig.war);
    String base = "campaign_stable_collision_attacker";
    Warband firstManual = new Warband(base, rig.alice);
    Warband secondManual = new Warband(base + "_2", rig.bob);
    WarbandManager.addWarband(firstManual);
    WarbandManager.addWarband(secondManual);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    Warband shell = battle.getSideById("attacker").getBands().getFirst();
    assertNotEquals(base, shell.getId());
    assertNotEquals(base + "_2", shell.getId());
    assertTrue(shell.isFaction());
    assertEquals(0, shell.getMemberCount());
    assertSame(firstManual, WarbandManager.getByString(base));
    assertSame(secondManual, WarbandManager.getByString(base + "_2"));
    assertTrue(firstManual.hasMember(rig.alice));
    assertTrue(secondManual.hasMember(rig.bob));
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    assertSame(shell, battle.getSideById("attacker").getBands().getFirst());
    assertEquals(1, battle.getSideById("attacker").getBands().size());
  }

  @Test
  void enrollmentRetainsAnAttachedLegacyShellAlongsideAManualBand() {
    Battle battle = campaignBattle("loaded_legacy", rig.war);
    Warband legacy =
        Warband.createCampaignSideShell(
            "old_display_based_attackers", rig.war, rig.war.getAttackers(), "attacker");
    legacy.addPlayer(rig.alice);
    Warband manual = new Warband("manual_ally", rig.player("Charlie"));
    WarbandManager.addWarband(legacy);
    WarbandManager.addWarband(manual);
    battle.getSideById("attacker").addBand(manual);
    battle.getSideById("attacker").addBand(legacy);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    battle.setDisplayName("Updated Battlefield Name");
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    assertEquals(java.util.List.of(manual, legacy), battle.getSideById("attacker").getBands());
    assertTrue(legacy.hasMember(rig.alice));
    assertEquals(1, manual.getMemberCount());
    assertEquals(1, battle.getSideById("defender").getBands().size());
  }

  @Test
  void deletingOneCampaignCannotPurgeAnotherCampaignsLegacyDisplayNamedShell() {
    Battle first = campaignBattle("deleting_new_front", rig.war);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, first);
    War otherWar =
        new War(
            918732,
            rig.domain.saved("legacy_iron", "Cedar"),
            rig.domain.saved("legacy_river", "Delta"));
    WarManager.addWar(otherWar);
    Battle other = campaignBattle("preserved_legacy_front", otherWar);
    String oldId = BattleNamingService.campaignWarbandId(other.getDisplayName(), "attacker");
    Warband legacy =
        Warband.createCampaignSideShell(oldId, otherWar, otherWar.getAttackers(), "attacker");
    org.bukkit.entity.Player cedar = rig.player("Cedar");
    legacy.addPlayer(cedar);
    WarbandManager.addWarband(legacy);
    other.getSideById("attacker").addBand(legacy);
    BattlePersistenceService.deleteCampaignBattle(first);
    assertSame(legacy, WarbandManager.getByString(oldId));
    assertTrue(legacy.hasMember(cedar));
    assertSame(other, BattleManager.getByString(other.getId()));
    assertSame(legacy, other.getSideById("attacker").getBands().getFirst());
    assertNull(BattleManager.getByString(first.getId()));
  }

  @Test
  void legacyWarCleanupRemovesOnlyUnattachedCampaignShellsAndPreservesManualNames() {
    String attackerId = Warband.campaignSideWarbandId(rig.war.getId(), "attacker");
    String defenderId = Warband.campaignSideWarbandId(rig.war.getId(), "defender");
    Warband abandoned =
        Warband.createCampaignSideShell(attackerId, rig.war, rig.war.getAttackers(), "attacker");
    Warband manual = new Warband(defenderId, rig.bob);
    WarbandManager.addWarband(abandoned);
    WarbandManager.addWarband(manual);

    BattlePersistenceService.purgeCampaignWarbandsForWar(rig.war.getId());

    assertNull(WarbandManager.getByString(attackerId));
    assertSame(manual, WarbandManager.getByString(defenderId));
    assertFalse(manual.isFaction());
    assertTrue(manual.hasMember(rig.bob));
  }

  @Test
  void legacySharedShellSurvivesUntilItsLastAttachedBattleIsDeleted() {
    Battle first = campaignBattle("legacy_shared_first", rig.war);
    Battle second = campaignBattle("legacy_shared_second", rig.war);
    String id = Warband.campaignSideWarbandId(rig.war.getId(), "attacker");
    Warband shared =
        Warband.createCampaignSideShell(id, rig.war, rig.war.getAttackers(), "attacker");
    shared.addPlayer(rig.alice);
    WarbandManager.addWarband(shared);
    first.getSideById("attacker").addBand(shared);
    second.getSideById("attacker").addBand(shared);

    assertNull(CampaignBattleRosterService.getCampaignWarband(null, "attacker"));
    assertNull(CampaignBattleRosterService.getCampaignWarband(first, "spectator"));
    assertSame(shared, CampaignBattleRosterService.getCampaignWarband(first, "attacker"));
    BattlePersistenceService.deleteCampaignBattle(first);
    assertTrue(first.getSideById("attacker").getBands().isEmpty());
    assertSame(shared, WarbandManager.getByString(id));
    assertSame(shared, CampaignBattleRosterService.getCampaignWarband(second, "attacker"));
    assertTrue(shared.hasMember(rig.alice));

    BattlePersistenceService.deleteCampaignBattle(second);
    assertNull(WarbandManager.getByString(id));
    assertTrue(second.getSideById("attacker").getBands().isEmpty());
  }

  @Test
  void persistedBattleNameSlugDoesNotDependOnServerLocale() {
    Locale before = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals("iron_field", BattleNamingService.slugifyDisplayName("IRON FIELD"));
    } finally {
      Locale.setDefault(before);
    }
  }

  @Test
  void siegeNameResolvesTheScheduledProvinceWhenFortIdsAreOnlyLocallyUnique() {
    fortsWithSameLocalId();
    ScheduledCampaignBattle target =
        new ScheduledCampaignBattle(40, CampaignBattleKind.SIEGE, false, "shared");
    Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "second_fort");
    BattleNamingService.applyCampaignName(battle, rig.war, 40, BattleType.SIEGE, target);
    assertEquals("Siege of Beta Fort", battle.getDisplayName());
    assertEquals(
        "Siege of Beta Fort",
        BattleNamingService.resolveScheduledDisplayName(
            rig.war, ScheduleLeg.INVASION, 0, target, 40));
  }

  @Test
  void differentFortsWithTheSameLocalIdHaveIndependentSiegeOrdinals() {
    fortsWithSameLocalId();
    ScheduledCampaignBattle first =
        new ScheduledCampaignBattle(30, CampaignBattleKind.SIEGE, false, "shared");
    ScheduledCampaignBattle second =
        new ScheduledCampaignBattle(40, CampaignBattleKind.SIEGE, false, "shared");
    rig.war.setCampaignBattleSchedule(List.of(first, second));
    assertEquals(
        1, BattleNamingService.resolveScheduledOrdinal(rig.war, ScheduleLeg.INVASION, 1, second));
    BattleNamingService.recordLocationBattle(rig.war, 30, first);
    assertEquals(
        1, BattleNamingService.resolveScheduledOrdinal(rig.war, ScheduleLeg.INVASION, 0, second));
  }

  private void fortsWithSameLocalId() {
    rig.attacker
        .getInstallationHandler()
        .acceptTransferred(
            new Installation(
                "shared",
                "Alpha Fort",
                InstallationKind.FORT,
                30,
                0,
                0,
                Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()));
    rig.defender
        .getInstallationHandler()
        .acceptTransferred(
            new Installation(
                "shared",
                "Beta Fort",
                InstallationKind.FORT,
                40,
                0,
                0,
                Instant.parse("2026-10-02T00:00:00Z").toEpochMilli()));
  }

  @Test
  void legacyUniqueFortCounterContinuesAfterTheFirstNewBattleRecord() {
    rig.install(rig.defender, "sole_fort", InstallationKind.FORT, 42);
    ScheduledCampaignBattle slot =
        new ScheduledCampaignBattle(42, CampaignBattleKind.SIEGE, false, "sole_fort");
    rig.war.recordLocationBattle("fort:sole_fort");
    rig.war.recordLocationBattle("fort:sole_fort");
    assertEquals(
        3, BattleNamingService.resolveScheduledOrdinal(rig.war, ScheduleLeg.INVASION, 0, slot));
    BattleNamingService.recordLocationBattle(rig.war, 42, slot);
    assertEquals(
        4, BattleNamingService.resolveScheduledOrdinal(rig.war, ScheduleLeg.INVASION, 0, slot));
  }

  @Test
  void ambiguousLegacyFortCountsAreNotAssignedToAnUnrelatedFort() {
    fortsWithSameLocalId();
    rig.war.recordLocationBattle("fort:shared");
    ScheduledCampaignBattle slot =
        new ScheduledCampaignBattle(40, CampaignBattleKind.SIEGE, false, "shared");
    assertEquals(
        1, BattleNamingService.resolveScheduledOrdinal(rig.war, ScheduleLeg.INVASION, 0, slot));
    BattleNamingService.recordLocationBattle(rig.war, 40, slot);
    assertEquals(
        2, BattleNamingService.resolveScheduledOrdinal(rig.war, ScheduleLeg.INVASION, 0, slot));
    rig.defender.getInstallationHandler().detachOnProvince(40);
    assertEquals(
        "Siege of shared",
        BattleNamingService.resolveScheduledDisplayName(
            rig.war, ScheduleLeg.INVASION, 0, slot, 40));
  }

  @Test
  void raidsOnDifferentPortsWithTheSameLocalIdHaveIndependentBattleCounts() {
    Installation first = rig.install(rig.attacker, "same_port", InstallationKind.PORT, 60);
    Installation second = rig.install(rig.defender, "same_port", InstallationKind.PORT, 70);
    rig.war.recordLocationBattle(BattleNamingService.raidLocationKey(first));
    assertEquals("same_port Raid", BattleNamingService.buildRaidDisplayName(rig.war, second));
  }

  @Test
  void manualNamesPreferAnExactCityThenFortThenCountyWithoutChangingBattleIdentity() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "stable_manual_id");
    fortsWithSameLocalId();
    BattleNamingService.applyManualName(battle, 40, BattleType.SIEGE);
    assertEquals("Siege of Beta Fort", battle.getDisplayName());
    rig.defender
        .getSettlementHandler()
        .acceptTransferred(new Settlement("beta_city", "Beta City", 40, 0, 0));
    BattleNamingService.applyManualName(battle, 40, BattleType.FIELD);
    assertEquals("Battle of Beta City", battle.getDisplayName());
    assertEquals("settlement:Beta City", BattleNamingService.resolveLocationKey(40));
    rig.domain.title("Green County", "county", 50);
    BattleNamingService.applyManualName(battle, 50, BattleType.FIELD);
    assertEquals("Battle of Green County", battle.getDisplayName());
    assertEquals("stable_manual_id", battle.getId());
    BattleNamingService.applyManualName(battle, null, BattleType.FIELD);
    BattleNamingService.applyManualName(battle, 50, null);
    BattleNamingService.applyCampaignName(battle, rig.war, 40, null);
    assertEquals("Battle of Green County", battle.getDisplayName());
    assertEquals(
        "Battle of Wilderness",
        BattleNamingService.resolveScheduledDisplayName(null, null, 0, null, 999));
    assertEquals(1, BattleNamingService.resolveScheduledOrdinal(null, null, 0, null));
    BattleNamingService.recordLocationBattle(null, 999);
    assertEquals("raid:unknown", BattleNamingService.raidLocationKey(null));
    assertEquals("wilderness", BattleNamingService.slugifyDisplayName("§a"));
    assertEquals("wilderness", BattleNamingService.slugifyDisplayName("___"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing_war", "ended", "null_time", "before", "voting", "unscheduled"})
  void scheduledLaunchGuardsLeaveRostersAndWarStateUntouched(String reason) {
    Instant due = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    rig.war.setScheduledBattleAt(due);
    if (reason.equals("ended")) rig.war.end(WarEndReason.ADMIN_END);
    if (reason.equals("voting")) rig.war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    if (reason.equals("unscheduled")) rig.war.setScheduledBattleAt(null);
    long writes = rig.warWrites();
    assertFalse(
        CampaignBattleLaunchService.tryStartScheduledBattle(
            reason.equals("missing_war") ? null : rig.war,
            reason.equals("null_time")
                ? null
                : reason.equals("before") ? due.minusSeconds(1) : due));
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertFalse(rig.war.hasFirstBattleStarted());
    assertEquals(writes, rig.warWrites());
    if (reason.equals("missing_war") || reason.equals("ended")) {
      War absent = reason.equals("missing_war") ? null : rig.war;
      assertNull(CampaignBattleLaunchService.prepareScheduledBattle(absent));
      assertNull(CampaignBattleLaunchService.launchAutoresolveBattle(absent));
    }
  }

  @Test
  void actualCampaignStartsOnceAndTimeCapEndsWithATimerResult() throws Exception {
    prepareArmedCampaign();
    Instant due = rig.war.getScheduledBattleAt();
    rig.time(due);
    Battle battle = CampaignBattleLaunchService.prepareScheduledBattle(rig.war);
    assertNotNull(battle);
    assertSame(battle, CampaignBattleLaunchService.prepareScheduledBattle(rig.war));
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 100, 64, 100));
      side.setJail(new Location(rig.domain.ui.world, 110, 64, 110));
    }
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    CampaignBattleRosterService.getCampaignWarband(battle, "attacker").addPlayer(rig.alice);
    CampaignBattleRosterService.getCampaignWarband(battle, "defender").addPlayer(rig.bob);
    assertTrue(CampaignBattleLaunchService.tryStartScheduledBattle(rig.war, due));
    assertTrue(battle.hasStarted());
    assertTrue(rig.war.hasFirstBattleStarted());
    CampaignBattleRosterService.ensureEnrolled(rig.war, battle);
    assertNull(
        CampaignBattleJoinService.validateRosterHasRoom(
            rig.war,
            battle,
            "attacker",
            CampaignBattleRosterService.getCampaignWarband(battle, "attacker"),
            1));
    assertEquals(
        "Battle already started.",
        CampaignBattleLaunchService.startPreparedBattle(rig.war, battle));
    assertNotNull(BattleJoinService.join(rig.alice, battle, "attacker"));
    assertEquals(2, battle.getAllParticipants().size());
    rig.remember(Cache.class, "battleTimeCapEnabled");
    rig.remember(Cache.class, "battleTimeCapMinutes");
    Cache.battleTimeCapEnabled = true;
    Cache.battleTimeCapMinutes = 1;
    battle.setStartedAt(Instant.now().minusSeconds(61));
    clearInvocations(Bukkit.getPluginManager());
    battle.tick();
    assertFalse(battle.hasStarted());
    var events = org.mockito.ArgumentCaptor.forClass(org.bukkit.event.Event.class);
    verify(Bukkit.getPluginManager()).callEvent(events.capture());
    BattleEndedEvent ended = assertInstanceOf(BattleEndedEvent.class, events.getValue());
    assertEquals(BattleEndReason.TIMER, ended.getEndReason());
    assertEquals(battle.getId(), ended.getBattleId());
    assertEquals(
        java.util.Set.of(rig.alice.getUniqueId(), rig.bob.getUniqueId()),
        ended.getParticipantIds());
  }

  private void prepareArmedCampaign() throws Exception {
    rig.remember(Cache.class, "battleCampaignTemplateField");
    Cache.battleCampaignTemplateField = "";
    rig.war.setGoal(WarGoalType.WAR);
    rig.war.setWarType(WarType.WAR);
    rig.war.setCampaignProvinces(List.of(10, 11, 12, 20));
    rig.war.setObjectiveProvinceId(20);
    rig.war.setCursorIndex(1);
    rig.war.setInitiativeAttacker(5);
    rig.war.setInitiativeDefender(5);
    rig.war.setInitiativeHolder(BelligerentRole.ATTACKER);
    rig.war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, false, null)));
    rig.war.setScheduledBattleProvinceId(11);
    rig.war.setScheduledBattleAt(BattleWindowService.atScheduleHour(Fixture.DAY, 21));
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    army(rig.attacker, "assault");
    army(rig.defender, "guard");
  }

  private void army(Faction faction, String id) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("offense", true);
    Regiment regiment = new Regiment(id, yaml);
    faction.getMilitary().getRegiments().add(regiment);
    assertTrue(faction.getMilitary().adminAdjustSlots(id, 2).allowed());
  }

  @Test
  void raidCountersRetainUniqueLegacyHistoryWithoutSharingAmbiguousLocalIds() {
    Installation target = rig.install(rig.defender, "legacy_port", InstallationKind.PORT, 71);
    rig.war.recordLocationBattle("raid:legacy_port");
    rig.war.recordLocationBattle("raid:legacy_port");
    assertEquals(
        "Third legacy_port Raid", BattleNamingService.buildRaidDisplayName(rig.war, target));
    rig.war.recordLocationBattle(BattleNamingService.raidLocationKey(target));
    assertEquals("4th legacy_port Raid", BattleNamingService.buildRaidDisplayName(rig.war, target));
    Installation unrelated = rig.install(rig.attacker, "legacy_port", InstallationKind.PORT, 81);
    assertEquals("legacy_port Raid", BattleNamingService.buildRaidDisplayName(rig.war, unrelated));
    assertEquals(
        "Second legacy_port Raid", BattleNamingService.buildRaidDisplayName(rig.war, target));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing_war",
        "null_time",
        "voting",
        "unscheduled",
        "no_battle",
        "null_offsets",
        "empty_offsets"
      })
  void reminderGuardsDoNotSendOrConsumeAnOffset(String reason) throws Exception {
    rig.remember(Cache.class, "battleSignupReminderSecondsBefore");
    Cache.battleSignupReminderSecondsBefore = List.of(60);
    Instant due = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    rig.war.setScheduledBattleAt(due);
    if (!reason.equals("no_battle")) campaignBattle("reminder_guard", rig.war);
    if (reason.equals("voting")) rig.war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    if (reason.equals("unscheduled")) rig.war.setScheduledBattleAt(null);
    if (reason.equals("null_offsets")) Cache.battleSignupReminderSecondsBefore = null;
    if (reason.equals("empty_offsets")) Cache.battleSignupReminderSecondsBefore = List.of();
    long writes = rig.warWrites();
    clearInvocations(rig.alice, rig.bob);
    CampaignBattleSignupReminderService.processReminders(
        reason.equals("missing_war") ? null : rig.war,
        reason.equals("null_time") ? null : due.minusSeconds(60));
    assertTrue(rig.war.getSignupRemindersSent().isEmpty());
    assertEquals(writes, rig.warWrites());
    verify(rig.alice, never()).sendMessage(anyString());
    verify(rig.bob, never()).sendMessage(anyString());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void remindersReachOnlyOnlineUnassignedBelligerentsAndPersistOnce(boolean bobOnline)
      throws Exception {
    rig.remember(Cache.class, "battleSignupReminderSecondsBefore");
    Cache.battleSignupReminderSecondsBefore = List.of(60);
    Instant due = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    rig.war.setScheduledBattleAt(due);
    campaignBattle("reminder_roster", rig.war);
    WarbandManager.addWarband(new Warband("alice_already_assigned", rig.alice));
    when(rig.bob.isOnline()).thenReturn(bobOnline);
    clearInvocations(rig.alice, rig.bob);
    long writes = rig.warWrites();
    CampaignBattleSignupReminderService.processReminders(rig.war, due.minusSeconds(60));
    CampaignBattleSignupReminderService.processReminders(rig.war, due.minusSeconds(59));
    assertEquals(java.util.Set.of(60), rig.war.getSignupRemindersSent());
    assertEquals(writes + 1, rig.warWrites());
    verify(rig.alice, never()).sendMessage(anyString());
    verify(rig.bob, times(bobOnline ? 1 : 0)).sendMessage(contains("/warband list"));
  }

  @Test
  void signupCreatesAndAnnouncesEachCanonicalShellOnlyOnceWhenItsWindowOpens() {
    Battle battle = campaignBattle("window_signup", rig.war);
    CampaignBattleRosterService.tryEnrollWhenSignupOpens(null, Instant.now());
    CampaignBattleRosterService.tryEnrollWhenSignupOpens(rig.war, null);
    CampaignBattleRosterService.ensureEnrolledForced(null, battle);
    CampaignBattleRosterService.enrollWarbands(rig.war, null);
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 10));
    CampaignBattleRosterService.ensureEnrolled(rig.war, battle);
    assertTrue(WarbandManager.get().isEmpty());
    clearInvocations(rig.alice, rig.bob);
    Instant signup = BattleWindowService.atScheduleHour(Fixture.DAY, 20);
    CampaignBattleRosterService.tryEnrollWhenSignupOpens(rig.war, signup);
    CampaignBattleRosterService.tryEnrollWhenSignupOpens(rig.war, signup.plusSeconds(1));
    assertEquals(2, WarbandManager.get().size());
    assertEquals(
        0, CampaignBattleRosterService.getCampaignWarband(battle, "attacker").getMemberCount());
    verify(rig.alice).sendMessage(contains("ready. Sign up"));
    verify(rig.bob).sendMessage(contains("ready. Sign up"));
  }

  @Test
  void scheduledEnrollmentLeavesUnregisteredBattlesAndCampaignRaidRostersAlone() {
    CampaignBattleRosterService.tryEnrollWhenSignupOpens(rig.war, Instant.now());
    assertTrue(WarbandManager.get().isEmpty());
    Battle raid = BattleFactory.createBlank(BattleType.RAID, "special_raid");
    raid.setWarId(rig.war.getId());
    raid.setCampaignRaid(true);
    BattleManager.addBattle(raid);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, raid);
    assertTrue(raid.getSides().stream().allMatch(side -> side.getBands().isEmpty()));
    assertTrue(WarbandManager.get().isEmpty());
  }

  @Test
  void automaticEnrollmentPreservesAttachedManualBandsEvenWhenTheBattleIsLocked() {
    Battle battle = campaignBattle("locked_enrollment", rig.war);
    Warband manual = new Warband("manual_locked_ally", rig.alice);
    WarbandManager.addWarband(manual);
    battle.getSideById("attacker").addBand(manual);
    battle.setLocked(true);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    assertTrue(battle.isLocked());
    assertEquals(2, battle.getSideById("attacker").getBands().size());
    assertSame(manual, battle.getSideById("attacker").getBands().getFirst());
    assertTrue(manual.hasMember(rig.alice));
    assertNotSame(manual, CampaignBattleRosterService.getCampaignWarband(battle, "attacker"));
    assertEquals(1, battle.getSideById("defender").getBands().size());
  }

  @Test
  void campaignJoinValidationUsesLiveMembershipAndNeverMutatesARejectedRoster() throws Exception {
    prepareArmedCampaign();
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "noncampaign_context");
    BattleManager.addBattle(manual);
    Battle unknownWar = campaignBattle("orphaned_campaign_context", rig.war);
    unknownWar.setWarId(999999);
    Battle battle = campaignBattle("join_context", rig.war);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    Warband attackers = CampaignBattleRosterService.getCampaignWarband(battle, "attacker");
    Warband defenders = CampaignBattleRosterService.getCampaignWarband(battle, "defender");
    long writes = rig.warWrites();
    assertSame(battle, CampaignBattleJoinService.findCampaignBattleForWarband(attackers).battle());
    assertNull(CampaignBattleJoinService.findCampaignBattleForWarband(null));
    assertEquals(
        "Invalid campaign battle join",
        CampaignBattleJoinService.validateJoin(null, battle, attackers, "attacker"));
    assertEquals(
        "Invalid campaign warband join",
        CampaignBattleJoinService.validateWarbandMemberJoin(
            rig.war, battle, "attacker", attackers, (org.bukkit.entity.Player) null));
    assertEquals(
        "Invalid campaign warband join",
        CampaignBattleJoinService.validateWarbandMemberJoin(
            null, battle, "attacker", attackers, "Alice"));
    assertEquals(
        "You must be in a faction to join this campaign battle",
        CampaignBattleJoinService.validateWarbandMemberJoin(
            rig.war, battle, "attacker", attackers, "Outsider"));
    assertEquals(
        "Your faction is not on this battle side",
        CampaignBattleJoinService.validateWarbandMemberJoin(
            rig.war, battle, "unknown", attackers, rig.alice));
    assertEquals(
        "Your faction is not on this battle side",
        CampaignBattleJoinService.validateWarbandMemberJoin(
            rig.war, battle, "attacker", attackers, rig.bob));
    assertEquals(
        "Warband is not on this battle side",
        CampaignBattleJoinService.validateWarbandMemberJoin(
            rig.war, battle, "attacker", defenders, rig.alice));
    assertEquals(
        "Warband is not on this battle side",
        BattleJoinService.join(defenders, battle, "attacker"));
    assertNull(
        CampaignBattleJoinService.validateWarbandMemberJoin(
            rig.war, battle, "attacker", attackers, "Alice"));
    assertNull(
        CampaignBattleJoinService.validateRosterHasRoom(rig.war, battle, "attacker", attackers, 0));
    assertNull(CampaignBattleJoinService.rosterSideFor(null, "Alice", rig.attacker));
    assertNull(CampaignBattleJoinService.rosterSideFor(rig.war, "Outsider", null));
    assertEquals(0, CampaignBattleJoinService.countSideRoster(null, "attacker"));
    assertFalse(CampaignBattleJoinService.warbandSideMatches(null, "attacker"));
    assertFalse(CampaignBattleJoinService.warbandSideMatches(attackers, null));
    assertNull(
        CampaignBattleJoinService.validateJoin(
            rig.war, battle, new Warband("compatible_manual", rig.alice), "attacker"));
    assertTrue(attackers.getMemberIds().isEmpty());
    assertTrue(defenders.getMemberIds().isEmpty());
    assertEquals(1, battle.getSideById("attacker").getBands().size());
    assertEquals(writes, rig.warWrites());
  }

  @Test
  void endedEventsWithoutARegisteredBattleCanReopenVotingWithoutCreatingAnOccupation()
      throws Exception {
    prepareArmedCampaign();
    rig.time(rig.war.getScheduledBattleAt());
    int attackerFuel = rig.war.getInitiativeAttacker();
    int battles = rig.war.getCampaignBattlesFought();
    var occupied = new java.util.ArrayList<>(rig.war.getOccupiedByAttacker());
    clearInvocations(rig.alice, rig.bob);
    CampaignBattleOutcomeService listener = new CampaignBattleOutcomeService();
    listener.onBattleEnded(
        new BattleEndedEvent(
            "already_removed",
            BattleType.FIELD,
            rig.war.getId(),
            null,
            java.util.Map.of(),
            java.util.Set.of()));
    assertEquals(BattleSchedulePhase.VOTING, rig.war.getBattleSchedulePhase());
    assertEquals(attackerFuel, rig.war.getInitiativeAttacker());
    assertEquals(battles, rig.war.getCampaignBattlesFought());
    assertEquals(occupied, rig.war.getOccupiedByAttacker());
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(rig.war.isActive());
    verify(rig.alice).sendMessage("§7Campaign battle ended with no winner. Voting reopened.");
    verify(rig.bob).sendMessage("§7Campaign battle ended with no winner. Voting reopened.");
  }

  @Test
  void obsoleteOutcomeEventsDoNotAlterAnyActiveWarOrManualBattle() {
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "manual_event_guard");
    BattleManager.addBattle(manual);
    CampaignBattleOutcomeService listener = new CampaignBattleOutcomeService();
    long writes = rig.warWrites();
    listener.onBattleEnded(null);
    listener.onBattleEnded(
        new BattleEndedEvent(
            manual.getId(),
            BattleType.FIELD,
            null,
            "attacker",
            java.util.Map.of(),
            java.util.Set.of()));
    listener.onBattleEnded(
        new BattleEndedEvent(
            "deleted_war",
            BattleType.FIELD,
            999999,
            "attacker",
            java.util.Map.of(),
            java.util.Set.of()));
    var result = CampaignBattleOutcomeService.applyCampaignBattleOutcome(null, null, null);
    assertFalse(result.progressionApplied());
    assertFalse(result.postBattleChoicePending());
    assertTrue(result.autoEndReason().isEmpty());
    assertSame(manual, BattleManager.getByString(manual.getId()));
    assertEquals(writes, rig.warWrites());
    assertTrue(rig.war.isActive());
  }

  @Test
  void aDueUnpreparedBattleIsCreatedButCannotStartWithoutStaffPlacements() throws Exception {
    prepareArmedCampaign();
    rig.time(rig.war.getScheduledBattleAt());
    rig.war.setScheduledBattleProvinceId(null);
    clearInvocations(rig.alice, rig.bob);
    assertFalse(CampaignBattleLaunchService.tryStartScheduledBattle(rig.war, rig.now()));
    Battle battle = BattleManager.getByWarId(rig.war.getId());
    assertNotNull(battle);
    assertEquals(11, battle.getProvinceId());
    assertFalse(battle.hasStarted());
    assertFalse(rig.war.hasFirstBattleStarted());
    assertEquals(5, rig.war.getInitiativeAttacker());
    assertFalse(CampaignBattleLaunchService.tryStartScheduledBattle(rig.war, rig.now()));
    assertSame(battle, BattleManager.getByWarId(rig.war.getId()));
    verify(rig.alice).sendMessage(contains("could not start:"));
    verify(rig.bob).sendMessage(contains("could not start:"));
  }

  @Test
  void aDueBattleWithNoOffensiveArmyResolvesForfeitWithoutStartingAnEmptyFight() throws Exception {
    prepareArmedCampaign();
    rig.attacker.addProvince(11);
    rig.domain.provinceData.put(
        11, new net.tfminecraft.simplefactions.map.provinces.Province(11, "PLAINS", 40, 0, 0));
    assertTrue(rig.attacker.getMilitary().adminAdjustSlots("assault", -2).allowed());
    rig.time(rig.war.getScheduledBattleAt());
    assertTrue(CampaignBattleLaunchService.tryStartScheduledBattle(rig.war, rig.now()));
    assertTrue(BattleManager.get().isEmpty());
    assertFalse(rig.war.hasFirstBattleStarted());
    assertEquals(4, rig.war.getInitiativeAttacker());
    assertTrue(rig.war.getOccupiedByDefender().contains(11));
  }

  @Test
  void pendingChoiceWithoutARecordedWinnerCannotNotifyAnUnrelatedSide() {
    rig.war.setPostBattleChoicePhase(
        net.tfminecraft.simplefactions.war.campaign.progression.postbattle
            .CampaignPostBattleChoiceService.PostBattleChoicePhase.WINNER_PUSH_HOLD);
    rig.war.setPostBattleChoiceResolved(false);
    rig.war.setPostBattleWinnerCoalition(null);
    clearInvocations(rig.alice, rig.bob);
    CampaignBattleOutcomeService.notifyPostBattleChoicePending(rig.war);
    verify(rig.alice, never()).sendMessage(anyString());
    verify(rig.bob, never()).sendMessage(anyString());
    assertNull(rig.war.getPostBattleWinnerCoalition());
    assertFalse(rig.war.isPostBattleChoiceResolved());
  }

  @Test
  void pendingChoiceNotificationDoesNotRequireAnAvailableServer() {
    rig.war.setPostBattleChoicePhase(
        net.tfminecraft.simplefactions.war.campaign.progression.postbattle
            .CampaignPostBattleChoiceService.PostBattleChoicePhase.WINNER_PUSH_HOLD);
    rig.war.setPostBattleChoiceResolved(false);
    rig.war.setPostBattleWinnerCoalition(
        net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService
            .CampaignCoalition.AGGRESSOR);
    org.bukkit.Server before = Bukkit.getServer();
    clearInvocations(rig.alice, rig.bob);
    try {
      when(Bukkit.getServer()).thenReturn(null);
      CampaignBattleOutcomeService.notifyPostBattleChoicePending(rig.war);
      verify(rig.alice, never()).sendMessage(anyString());
      verify(rig.bob, never()).sendMessage(anyString());
      assertFalse(rig.war.isPostBattleChoiceResolved());
    } finally {
      when(Bukkit.getServer()).thenReturn(before);
    }
  }

  @Test
  void developmentEnrollmentBeforeSignupKeepsStableShellsAndOnlyRemovesDummyMembers()
      throws Exception {
    prepareArmedCampaign();
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 10));
    Battle battle = campaignBattle("development_signup", rig.war);
    rig.remember(Cache.class, "warDevmodePhantomCount");
    Cache.warDevmodePhantomCount = 3;
    net.tfminecraft.simplefactions.war.core.WarDevMode.setEnabled(true);
    CampaignBattleRosterService.ensureEnrolled(rig.war, battle);
    Warband attackers = CampaignBattleRosterService.getCampaignWarband(battle, "attacker");
    assertNotNull(attackers);
    assertEquals(0, attackers.getRealMemberCount());
    assertEquals(3, attackers.getDummyMemberCount());
    assertNull(
        net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService
            .signupMember(rig.alice.getUniqueId(), rig.alice.getName(), attackers, rig.attacker));
    assertEquals(1, attackers.getRealMemberCount());
    assertEquals(3, attackers.getDummyMemberCount());
    net.tfminecraft.simplefactions.war.core.WarDevMode.setEnabled(false);
    assertEquals(0, attackers.getDummyMemberCount());
    assertTrue(attackers.hasMember(rig.alice));
    assertSame(attackers, CampaignBattleRosterService.getCampaignWarband(battle, "attacker"));
    assertFalse(battle.hasStarted());
  }

  @Test
  void anEndedWarCannotSupplyACampaignContextForItsRetainedRoster() {
    Battle battle = campaignBattle("ended_context", rig.war);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    Warband attackers = CampaignBattleRosterService.getCampaignWarband(battle, "attacker");
    attackers.addPlayer(rig.alice);
    rig.war.end(WarEndReason.ADMIN_END);
    assertNull(CampaignBattleJoinService.findCampaignBattleForWarband(attackers));
    assertTrue(attackers.hasMember(rig.alice));
    assertSame(battle, BattleManager.getByString(battle.getId()));
  }

  @Test
  void hiredRosterHonorsThePromisedSlotAndRetainsAnAlreadySignedMember() throws Exception {
    prepareArmedCampaign();
    var home = rig.domain.saved("hired_home", "Cedar");
    home.addMember("Hireling");
    home.addMember("Extra");
    org.bukkit.entity.Player hireling = rig.player("Hireling");
    org.bukkit.entity.Player extra = rig.player("Extra");
    var company = savedHiredCompany(home, List.of("Hireling", "Extra"));
    Battle battle = campaignBattle("hired_roster", rig.war);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    Warband attackers = CampaignBattleRosterService.getCampaignWarband(battle, "attacker");
    var previous =
        net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements.uuidLookup();
    net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements.setUuidLookup(
        name -> {
          org.bukkit.entity.Player player = Bukkit.getPlayerExact(name);
          return player == null ? null : player.getUniqueId();
        });
    try {
      assertSame(
          rig.war.getAttackers(),
          CampaignBattleJoinService.rosterSideFor(rig.war, "Hireling", home));
      assertNull(
          CampaignBattleJoinService.validateWarbandMemberJoin(
              rig.war, battle, "attacker", attackers, hireling));
      attackers.addPlayer(hireling);
      assertNull(
          CampaignBattleJoinService.validateWarbandMemberJoin(
              rig.war, battle, "attacker", attackers, hireling));
      assertEquals(
          "Every hired slot is already covered",
          CampaignBattleJoinService.validateWarbandMemberJoin(
              rig.war, battle, "attacker", attackers, extra));
      assertEquals(
          "You are under contract to the other host",
          CampaignBattleJoinService.validateWarbandMemberJoin(
              rig.war, battle, "defender", attackers, hireling));
      assertEquals(
          java.util.Set.of(hireling.getUniqueId()), java.util.Set.copyOf(attackers.getMemberIds()));
      assertFalse(attackers.hasMember(extra));
      assertEquals(1, company.getContractHandler().getActive().size());
    } finally {
      net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements.setUuidLookup(
          previous);
    }
  }

  @Test
  void anEnlistedPlayerWhoNowLeadsTheOpposingRealmCannotDeployAgainstIt() throws Exception {
    prepareArmedCampaign();
    Faction home = rig.domain.saved("loyalty_home", "Cedar");
    var company = savedHiredCompany(home, List.of("Bob"));
    Battle battle = campaignBattle("hired_loyalty", rig.war);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    Warband attackers = CampaignBattleRosterService.getCampaignWarband(battle, "attacker");
    assertEquals(
        "You cannot march on your own realm",
        CampaignBattleJoinService.validateWarbandMemberJoin(
            rig.war, battle, "attacker", attackers, rig.bob));
    assertTrue(attackers.getMemberIds().isEmpty());
    assertEquals(1, company.getContractHandler().getActive().size());
    assertEquals("Bob", rig.defender.getLeader());
  }

  private net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany savedHiredCompany(
      Faction home, List<String> enlisted) {
    var guild = home.getOrCreateMainGuild();
    var data = new net.tfminecraft.simplefactions.database.MercenaryCompanyData();
    data.name = "Boundary Spears";
    data.formationRemaining = 0;
    data.enlisted = enlisted;
    data.slots = "hired_unit.2";
    var contract = new net.tfminecraft.simplefactions.database.MercenaryContractData();
    contract.id = "boundary_hiring";
    contract.hirer = rig.attacker.getId();
    contract.status = "ACTIVE";
    contract.kind = "MERCENARY";
    contract.slots = 1;
    contract.durationDays = 7;
    contract.issueDate = System.currentTimeMillis();
    contract.dueDate = contract.issueDate + java.time.Duration.ofDays(7).toMillis();
    data.contracts.add(contract);
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("default-slots", 2);
    var company =
        new net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany(
            guild, data, new Regiment("hired_unit", yaml));
    guild.setCompany(company);
    assertTrue(company.isFormed());
    assertEquals(1, company.getContractHandler().getActive().size());
    return company;
  }

  @Test
  void namingToleratesMissingEntriesInThePublicFactionRegistry() {
    net.tfminecraft.simplefactions.managers.FactionManager.factions.add(0, null);
    try {
      assertEquals("Wilderness", BattleNamingService.resolveLocationDisplayName(999));
      assertEquals("wilderness:999", BattleNamingService.resolveLocationKey(999));
    } finally {
      net.tfminecraft.simplefactions.managers.FactionManager.factions.remove(null);
    }
    assertTrue(
        net.tfminecraft.simplefactions.managers.FactionManager.factions.contains(rig.attacker));
  }

  @Test
  void capturingTheCapitalEndsTheWarAndCleansOnlyItsCampaignWithoutOfferingAnotherChoice()
      throws Exception {
    prepareArmedCampaign();
    rig.defender.addProvince(20);
    rig.defender.setCapital(20, true);
    rig.domain.provinceData.put(
        20, new net.tfminecraft.simplefactions.map.provinces.Province(20, "PLAINS", 40, 0, 0));
    rig.war.setCursorIndex(3);
    rig.war.setScheduledBattleProvinceId(20);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null)));
    Battle battle = campaignBattle("capital_decider", rig.war);
    battle.setProvinceId(20);
    CampaignBattleRosterService.ensureEnrolledForced(rig.war, battle);
    Warband campaign = CampaignBattleRosterService.getCampaignWarband(battle, "attacker");
    campaign.addPlayer(rig.alice);
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "unrelated_manual_battle");
    BattleManager.addBattle(manual);
    Warband manualBand = new Warband("unrelated_manual_band", rig.player("Charlie"));
    WarbandManager.addWarband(manualBand);
    manual.getSideById("attacker").addBand(manualBand);
    clearInvocations(rig.alice, rig.bob);

    var result =
        CampaignBattleOutcomeService.applyCampaignBattleOutcome(
            rig.war, BelligerentRole.ATTACKER, 20, battle, java.util.Map.of());

    assertTrue(result.progressionApplied());
    assertFalse(result.postBattleChoicePending());
    assertEquals(WarEndReason.ATTACKER_VICTORY, result.autoEndReason().orElseThrow());
    assertFalse(rig.war.isActive());
    assertEquals(WarEndReason.ATTACKER_VICTORY, rig.war.getEndReason());
    assertNull(WarManager.getById(rig.war.getId()));
    assertEquals(1, rig.war.getCampaignBattlesFought());
    assertNull(BattleManager.getByString(battle.getId()));
    assertNull(WarbandManager.getByString(campaign.getId()));
    assertSame(manual, BattleManager.getByString(manual.getId()));
    assertSame(manualBand, WarbandManager.getByString(manualBand.getId()));
    assertEquals(1, manualBand.getRealMemberCount());
    verify(rig.alice).sendMessage("§7The war has ended. The attacker coalition wins.");
    verify(rig.bob).sendMessage("§7The war has ended. The attacker coalition wins.");
    verify(rig.alice, never()).sendMessage(contains("Choose"));
    verify(rig.bob, never()).sendMessage(contains("Choose"));
  }

  @Test
  void noPendingChoiceDoesNotNotifyPlayersOrWriteTheWar() {
    long writes = rig.warWrites();
    clearInvocations(rig.alice, rig.bob);
    CampaignBattleOutcomeService.notifyPostBattleChoicePending(null);
    CampaignBattleOutcomeService.notifyPostBattleChoicePending(rig.war);
    verify(rig.alice, never()).sendMessage(anyString());
    verify(rig.bob, never()).sendMessage(anyString());
    assertEquals(writes, rig.warWrites());
    assertTrue(rig.war.isActive());
  }
}
