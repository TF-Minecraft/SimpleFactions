package net.tfminecraft.simplefactions.war.campaign.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.campaign.progression.AttackerNavalContestService;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleTickService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleSiegeFortService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignScheduleCountdown;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignScheduleLogger;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleScheduleCloseResult;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortZocIndex;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortZocIndex.OperationalFort;
import net.tfminecraft.simplefactions.war.campaign.zoc.PortSeaZocIndex;
import net.tfminecraft.simplefactions.war.campaign.zoc.PortSeaZocIndex.OperationalPort;
import net.tfminecraft.simplefactions.war.core.WarDevMode;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignScheduleLifecycleCoverageTest {
  @TempDir Path temporary;
  private Fixture rig;
  private final ProvinceManager provinces = new ProvinceManager();
  private final Map<Integer, Province> graph = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    for (String field :
        List.of(
            "warFirstBattleAtBorder",
            "warProvincesBetweenBattles",
            "warBattleWindowStartHour",
            "warBattleWindowEndHour")) rig.remember(Cache.class, field);
    Cache.warFirstBattleAtBorder = true;
    Cache.warProvincesBetweenBattles = 1;
    Cache.warBattleWindowStartHour = 21;
    Cache.warBattleWindowEndHour = 24;
    for (int id : List.of(10, 11, 12, 20, 21))
      graph.put(id, new Province(id, "PLAINS", 50, id * 10, 0));
    graph.put(30, new Province(30, "SEA", 0, 300, 0));
    link(10, 11);
    link(11, 12);
    link(12, 20);
    link(12, 21);
    link(20, 21);
    link(20, 30);
    provinces.start(graph);
    when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(provinces);
    for (int id : List.of(10, 11)) rig.attacker.addProvince(id);
    for (int id : List.of(12, 20, 21)) rig.defender.addProvince(id);
    rig.attacker.setCapital(10, true, false);
    rig.defender.setCapital(20, true, false);
    rig.war.setWarType(WarType.WAR);
    rig.war.setGoal(WarGoalType.WAR);
    rig.war.setCampaignProvinces(List.of(10, 11, 12, 20));
    rig.war.setObjectiveProvinceId(20);
    rig.war.setCursorIndex(1);
    rig.war.setInitiativeAttacker(5);
    rig.war.setInitiativeDefender(5);
    rig.war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
    rig.war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
  }

  @AfterEach
  void close() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void persistedSpoofVoteIdentityDoesNotChangeWithTheServersDefaultLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.ROOT);
      UUID stored = BattleScheduleLookups.spoofMemberUuid("INDIGO");
      rig.attacker.getOrCreateMainGuild().addMember("INDIGO");
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      assertEquals(stored, BattleScheduleLookups.spoofMemberUuid("INDIGO"));
      assertEquals(stored, BattleScheduleLookups.spoofMemberNameToUuid().apply("indigo"));
      assertSame(rig.attacker, BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(stored));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void publicInvasionBuilderHandlesAMissingAxisLikeTheCombinedBuilder() {
    rig.attacker.setCapital(10, true, false);
    FortZocIndex forts = FortZocIndex.fromForts(List.of());

    assertEquals(
        List.of(),
        assertDoesNotThrow(() -> CampaignScheduleBuilder.build(rig.war, null, 0, 0, forts)));
  }

  @Test
  void publicCounterBuilderHandlesAMissingAxisLikeTheCombinedBuilder() {
    rig.war.setObjectiveProvinceId(20);
    FortZocIndex forts = FortZocIndex.fromForts(List.of());

    assertEquals(
        List.of(),
        assertDoesNotThrow(
            () -> CampaignScheduleBuilder.buildCounter(rig.war, null, 0, 0, forts, null)));
  }

  @Test
  void publicBuildersRejectAnObjectiveBeforeTheBorder() {
    var axis = List.of(10, 11, 12, 20);
    var forts = FortZocIndex.fromForts(List.of());
    assertAll(
        () ->
            assertTrue(
                assertDoesNotThrow(() -> CampaignScheduleBuilder.build(rig.war, axis, 2, 1, forts))
                    .isEmpty()),
        () -> {
          var built =
              assertDoesNotThrow(
                  () -> CampaignScheduleBuilder.buildAll(rig.war, axis, 2, 1, 0, forts, null));
          assertTrue(built.invasion().isEmpty());
          assertTrue(built.counter().isEmpty());
        });
  }

  @Test
  void reSiegingAnOffAxisFortKeepsItsHomeProvinceAndChronologyAnchor() {
    var fort = rig.install(rig.defender, "off-axis-fort", InstallationKind.FORT, 21);
    rig.war.putFortController(fort.getStableKey(), CampaignCoalition.DEFENDER);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null)));

    ScheduledCampaignBattle siege = CampaignScheduleService.currentSlot(rig.war).orElseThrow();

    assertEquals(
        21,
        siege.provinceId(),
        "The siege must occur at the actual fort, not its neighboring ZOC tile");
    assertEquals(12, siege.sortProvinceId());
    assertEquals(CampaignBattleKind.SIEGE, siege.kind());
    assertSame(rig.defender, BattleSiegeFortService.currentSiegeFortOwner(rig.war).orElseThrow());
    assertEquals(siege, CampaignScheduleService.currentSlot(rig.war).orElseThrow());
    assertEquals(
        2,
        rig.war.getCampaignBattleSchedule().size(),
        "Reading again must not duplicate the re-siege");
  }

  @Test
  void scheduleQueriesHandleAbsentWarsAndRespectIndependentLegsAndIndices() {
    assertFalse(CampaignScheduleService.hasSchedule(null));
    assertFalse(CampaignScheduleService.hasActiveSchedule(null));
    assertEquals(List.of(), CampaignScheduleService.scheduleListForLeg(null, ScheduleLeg.INVASION));
    assertEquals(0, CampaignScheduleService.scheduleIndexForLeg(null, ScheduleLeg.COUNTER));
    assertTrue(CampaignScheduleService.slotAt(null, 0).isEmpty());
    assertTrue(CampaignScheduleService.slotAt(rig.war, 0, null).isEmpty());
    assertTrue(CampaignScheduleService.slotForProvince(null, 20).isEmpty());
    assertTrue(CampaignScheduleService.currentSlot(null).isEmpty());
    assertTrue(CampaignScheduleService.slotAtActiveIndex(null).isEmpty());
    assertTrue(CampaignScheduleService.firstOnAxisScheduleIndex(null).isEmpty());
    CampaignScheduleService.advanceIndex(null);
    CampaignScheduleService.insertSiegeAtCurrentIndex(null, null, 0);
    CampaignScheduleService.insertSiegeAtCurrentIndex(rig.war, null, 0);
    assertTrue(rig.war.getCampaignBattleSchedule().isEmpty());

    ScheduledCampaignBattle border = field(12, false),
        objective = field(20, true),
        capital = field(10, true);
    rig.war.setCampaignBattleSchedule(List.of(border, objective));
    rig.war.setCampaignCounterSchedule(List.of(capital));
    assertTrue(CampaignScheduleService.hasSchedule(rig.war));
    assertTrue(CampaignScheduleService.hasScheduleForLeg(rig.war, ScheduleLeg.COUNTER));
    assertEquals(0, CampaignScheduleService.firstOnAxisScheduleIndex(rig.war).orElseThrow());
    assertEquals(12, CampaignScheduleService.firstOnAxisScheduleProvince(rig.war).orElseThrow());
    assertEquals(border, CampaignScheduleService.slotForProvince(rig.war, 12).orElseThrow());
    assertEquals(objective, CampaignScheduleService.slotForProvince(rig.war, 20).orElseThrow());
    assertTrue(CampaignScheduleService.slotForProvince(rig.war, 999).isEmpty());
    assertTrue(CampaignScheduleService.slotAt(rig.war, -1).isEmpty());
    assertTrue(CampaignScheduleService.slotAt(rig.war, 2).isEmpty());
    CampaignScheduleService.advanceIndex(rig.war);
    assertTrue(CampaignScheduleService.slotForProvince(rig.war, 12).isEmpty());
    rig.war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
    rig.war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
    assertEquals(ScheduleLeg.COUNTER, CampaignScheduleService.activeLeg(rig.war));
    assertEquals(capital, CampaignScheduleService.slotAtActiveIndex(rig.war).orElseThrow());
    CampaignScheduleService.advanceIndex(rig.war);
    assertEquals(1, CampaignScheduleService.getActiveScheduleIndex(rig.war));
    assertEquals(1, rig.war.getCampaignScheduleIndex());
    assertTrue(CampaignScheduleService.slotAtActiveIndex(rig.war).isEmpty());
    rig.war.setCampaignBattleSchedule(List.of(field(999, true)));
    assertTrue(CampaignScheduleService.firstOnAxisScheduleIndex(rig.war).isEmpty());
    rig.war.setCampaignProvinces(null);
    assertTrue(CampaignScheduleService.firstOnAxisScheduleIndex(rig.war).isEmpty());
  }

  @Test
  void installationNamesResolveLiveOwnersAndPreserveUnknownIdentifiers() {
    FactionManager.factions.add(0, null);
    assertNull(CampaignScheduleService.resolveInstallationName(null));
    assertNull(CampaignScheduleService.resolveInstallationName(" "));
    assertEquals(
        rig.target.getName(), CampaignScheduleService.resolveInstallationName(rig.target.getId()));
    assertEquals("removed-port", CampaignScheduleService.resolveInstallationName("removed-port"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"terminal", "retake", "no-objective", "already-later", "friendly", "counter"})
  void reSiegeChecksRespectTheLivePushAndDoNotDuplicateOrAttackFriendlyForts(String state) {
    var fort = rig.install(rig.defender, "defender-fort", InstallationKind.FORT, 20);
    rig.war.putFortController(fort.getStableKey(), CampaignCoalition.DEFENDER);
    rig.war.setCampaignBattleSchedule(List.of(field(20, true)));
    rig.war.setCampaignCounterSchedule(List.of(field(10, true)));
    if (state.equals("terminal")) rig.war.setCursorIndex(3);
    if (state.equals("retake")) rig.war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
    if (state.equals("no-objective")) rig.war.setObjectiveProvinceId(999);
    if (state.equals("friendly"))
      rig.war.putFortController(fort.getStableKey(), CampaignCoalition.AGGRESSOR);
    if (state.equals("already-later"))
      rig.war.setCampaignBattleSchedule(
          List.of(
              field(12, false),
              new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, fort.getId())));
    if (state.equals("counter")) {
      rig.war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
      rig.war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
      rig.war.setCursorIndex(3);
    }
    List<ScheduledCampaignBattle> before = List.copyOf(rig.war.getCampaignBattleSchedule());
    CampaignScheduleService.ensureReSiegeInsert(rig.war);
    if (state.equals("terminal") || state.equals("retake")) {
      assertEquals(
          CampaignBattleKind.SIEGE,
          CampaignScheduleService.slotAtActiveIndex(rig.war).orElseThrow().kind());
      assertEquals(2, rig.war.getCampaignBattleSchedule().size());
    } else {
      assertEquals(before, rig.war.getCampaignBattleSchedule());
      assertEquals(List.of(field(10, true)), rig.war.getCampaignCounterSchedule());
    }
  }

  @Test
  void battlePlacementRejectsUnsupportedInputsAndDeduplicatesPortsAndObjectives() {
    CampaignScheduleBuildContext ctx = context(FortZocIndex.fromForts(List.of()));
    CampaignBattlePlacer.placeBattle(
        null,
        rig.war,
        ScheduleLeg.INVASION,
        12,
        BattleTrigger.BORDER,
        CampaignCoalition.AGGRESSOR,
        null,
        null);
    CampaignBattlePlacer.placeBattle(
        ctx,
        null,
        ScheduleLeg.INVASION,
        12,
        BattleTrigger.BORDER,
        CampaignCoalition.AGGRESSOR,
        null,
        null);
    CampaignBattlePlacer.placeBattle(
        ctx, rig.war, null, 12, BattleTrigger.BORDER, CampaignCoalition.AGGRESSOR, null, null);
    CampaignBattlePlacer.placeBattle(
        ctx,
        rig.war,
        ScheduleLeg.INVASION,
        0,
        BattleTrigger.BORDER,
        CampaignCoalition.AGGRESSOR,
        null,
        null);
    CampaignBattlePlacer.placeBattle(
        ctx, rig.war, ScheduleLeg.INVASION, 12, null, CampaignCoalition.AGGRESSOR, null, null);
    place(ctx, 30, BattleTrigger.NAVAL, null, " ");
    place(ctx, 30, BattleTrigger.OBJECTIVE, null, null);
    assertTrue(ctx.invasion().isEmpty());
    place(ctx, 12, BattleTrigger.BORDER, null, null);
    place(ctx, 12, BattleTrigger.CADENCE, null, null);
    place(ctx, 20, BattleTrigger.OBJECTIVE, null, null);
    place(ctx, 20, BattleTrigger.OBJECTIVE, null, null);
    place(ctx, 30, BattleTrigger.NAVAL, null, "port");
    place(ctx, 30, BattleTrigger.NAVAL, null, "port");
    assertEquals(
        List.of(CampaignBattleKind.NAVAL, CampaignBattleKind.FIELD, CampaignBattleKind.FIELD),
        ctx.invasion().stream().map(ScheduledCampaignBattle::kind).toList());
    assertEquals(Set.of("port"), ctx.portIdsFor(ScheduleLeg.INVASION));
    assertTrue(ctx.portIdsFor(ScheduleLeg.COUNTER).isEmpty());
    assertTrue(ctx.invasion().getLast().required());
    assertEquals(12, ctx.borderProvinceId());
  }

  @Test
  void fortPlacementChecksIdentityAndReplacesOnlyOptionalFieldsAtItsChronologyTile() {
    var fort = rig.install(rig.defender, "off-axis", InstallationKind.FORT, 21);
    rig.war.putFortController(fort.getStableKey(), CampaignCoalition.DEFENDER);
    CampaignScheduleBuildContext ctx = context(FortZocIndex.fromGameState());
    place(ctx, 10, BattleTrigger.FORT_ZOC, "absent", null);
    place(ctx, 12, BattleTrigger.FORT_ZOC, "different-fort", null);
    assertTrue(ctx.invasion().isEmpty());
    place(ctx, 12, BattleTrigger.BORDER, null, null);
    place(ctx, 20, BattleTrigger.OBJECTIVE, null, null);
    place(ctx, 12, BattleTrigger.FORT_ZOC, fort.getId(), null);
    place(ctx, 12, BattleTrigger.CADENCE, null, null);
    assertEquals(2, ctx.invasion().size());
    assertEquals(21, ctx.invasion().getFirst().provinceId());
    assertEquals(12, ctx.invasion().getFirst().sortProvinceId());
    assertEquals(field(20, true), ctx.invasion().getLast());
    place(ctx, 20, BattleTrigger.FORT_ZOC, fort.getId(), null);
    assertEquals(2, ctx.invasion().size());
  }

  @Test
  void combinedBuilderReturnsImmutableOrderedLegsAndValidatesTheObjectiveContract() {
    FortZocIndex forts = FortZocIndex.fromForts(List.of());
    List<Integer> axis = rig.war.getCampaignProvinces();
    var result = CampaignScheduleBuilder.buildAll(rig.war, axis, 2, 3, 0, forts, null);
    assertEquals(List.of(field(12, false), field(20, true)), result.invasion());
    assertEquals(List.of(field(11, false), field(10, true)), result.counter());
    assertThrows(UnsupportedOperationException.class, () -> result.invasion().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.counter().clear());
    assertTrue(CampaignScheduleValidator.isValidInvasionSchedule(rig.war, axis, result.invasion()));
    assertFalse(CampaignScheduleValidator.isValidInvasionSchedule(null, axis, result.invasion()));
    assertFalse(
        CampaignScheduleValidator.isValidInvasionSchedule(rig.war, List.of(), result.invasion()));
    assertFalse(
        CampaignScheduleValidator.isValidInvasionSchedule(rig.war, axis, List.of(field(12, true))));
    assertFalse(
        CampaignScheduleValidator.isValidInvasionSchedule(
            rig.war, axis, List.of(field(21, false), field(20, true))));
    rig.war.setObjectiveProvinceId(null);
    assertFalse(
        CampaignScheduleValidator.isValidInvasionSchedule(rig.war, axis, result.invasion()));
    rig.war.setObjectiveProvinceId(999);
    assertFalse(
        CampaignScheduleValidator.isValidInvasionSchedule(rig.war, axis, result.invasion()));
    assertTrue(
        CampaignScheduleBuilder.buildAll(rig.war, List.of(), 0, 0, 0, forts, null)
            .invasion()
            .isEmpty());
    assertTrue(
        CampaignScheduleBuilder.buildAll(rig.war, axis, -1, 3, 0, forts, null)
            .invasion()
            .isEmpty());
    assertTrue(
        CampaignScheduleBuilder.buildAll(rig.war, axis, 99, 3, 0, forts, null).counter().isEmpty());
    assertTrue(CampaignScheduleBuilder.buildCounter(rig.war, axis, 0, 0, forts, null).isEmpty());
  }

  @Test
  void trimmerPreservesRequiredFightsAndBorderWhileRemovingOptionalKindsFromEachLeg() {
    ScheduledCampaignBattle capital = field(10, true), objective = field(20, true);
    var navy = new ScheduledCampaignBattle(30, CampaignBattleKind.NAVAL, false, null, "port");
    var legacyLanding =
        new ScheduledCampaignBattle(12, CampaignBattleKind.NAVAL_INVASION, false, null);
    var siege = new ScheduledCampaignBattle(21, CampaignBattleKind.SIEGE, false, "fort");
    assertEquals(List.of(), CampaignScheduleTrimmer.trimInvasion(null, 2));
    assertEquals(List.of(), CampaignScheduleTrimmer.trimCounter(List.of(capital), 0));
    assertEquals(List.of(capital), CampaignScheduleTrimmer.trimCounter(List.of(capital), 2));
    assertEquals(
        List.of(capital),
        CampaignScheduleTrimmer.trim(List.of(legacyLanding, navy, siege, capital), 1));
    assertEquals(
        List.of(field(12, false), objective),
        CampaignScheduleTrimmer.trimInvasion(
            List.of(field(12, false), legacyLanding, navy, siege, objective), 2));
    assertEquals(
        List.of(capital, objective),
        CampaignScheduleTrimmer.trimCounter(List.of(capital, objective), 1));
    assertEquals(
        CampaignScheduleTrimmer.maxBattlesPerLegForGoal(null),
        CampaignScheduleTrimmer.maxBattlesForGoal(null));
  }

  @Test
  void rosterLookupsResolveOnlineOfflineAndFallbackIdentitiesWithoutInventingMembership() {
    var live = BattleScheduleLookups.uuidToFaction();
    assertNull(live.apply(null));
    assertNull(live.apply(UUID.randomUUID()));
    assertSame(rig.attacker, live.apply(rig.alice.getUniqueId()));
    assertNull(BattleScheduleLookups.uuidToFactionForWar(null).apply(rig.alice.getUniqueId()));
    assertNull(BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(null));
    assertNull(BattleScheduleLookups.memberNameToUuid().apply(" "));
    assertNull(BattleScheduleLookups.spoofMemberUuid(null));
    assertSame(
        rig.defender,
        BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(rig.bob.getUniqueId()));
    var outsider = rig.domain.saved("outsider", "Carol");
    var carol = rig.player("Carol");
    assertSame(
        outsider, BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(carol.getUniqueId()));
    rig.attacker.getOrCreateMainGuild().addMember("Offline");
    UUID offline = BattleScheduleLookups.memberNameToUuid().apply("Offline");
    assertSame(rig.attacker, BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(offline));
    when(Bukkit.getServer()).thenReturn(null);
    assertEquals(
        BattleScheduleLookups.spoofMemberUuid("Offline"),
        BattleScheduleLookups.memberNameToUuid().apply("Offline"));
    assertNull(BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(UUID.randomUUID()));
    assertSame(
        rig.attacker,
        BattleScheduleLookups.uuidToFactionForWar(rig.war)
            .apply(BattleScheduleLookups.spoofMemberUuid("Alice")));
  }

  @Test
  void lifecycleTransitionsClearPriorTargetsAndPreserveVoteMembershipAndCalendarRules() {
    Instant at = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setScheduledBattleAt(at);
    rig.war.setScheduledBattleHour(21);
    rig.war.setScheduledBattleProvinceId(12);
    rig.war.setSignupRemindersSent(Set.of(1));
    rig.war.setDefenderChoiceResolved(true);
    BattleScheduleService.openVote(rig.war);
    assertEquals(BattleSchedulePhase.VOTING, rig.war.getBattleSchedulePhase());
    assertNull(rig.war.getScheduledBattleAt());
    assertNull(rig.war.getScheduledBattleProvinceId());
    assertEquals(0, rig.war.getScheduledBattleHour());
    assertTrue(rig.war.getSignupRemindersSent().isEmpty());
    rig.war.setAutoresolveProposedByAttacker(true);
    rig.war.setAutoresolveProposedByDefender(true);
    BattleScheduleService.enterAutoresolvePending(rig.war);
    assertEquals(BattleSchedulePhase.AUTORESOLVE_PENDING, rig.war.getBattleSchedulePhase());
    assertFalse(rig.war.isAutoresolveProposedByAttacker());
    assertFalse(rig.war.isAutoresolveProposedByDefender());
    BattleScheduleService.openVote(rig.war);
    int added =
        BattleScheduleService.castSpoofVotes(
            rig.war,
            21,
            BattleScheduleLookups.memberNameToUuid(),
            BelligerentRole.ATTACKER,
            BelligerentRole.DEFENDER);
    assertEquals(2, added);
    assertEquals(
        0,
        BattleScheduleService.castSpoofVotes(
            rig.war,
            21,
            BattleScheduleLookups.memberNameToUuid(),
            BelligerentRole.ATTACKER,
            BelligerentRole.DEFENDER));
    assertEquals(
        0,
        BattleScheduleService.castSpoofVotes(rig.war, 22, name -> null, BelligerentRole.ATTACKER));
    assertEquals(
        0,
        BattleScheduleService.castSpoofVotes(
            rig.war, 24, BattleScheduleLookups.memberNameToUuid(), BelligerentRole.ATTACKER));
    rig.war.setBattleInstallationPicks(
        Map.of(rig.attacker.getId(), new LinkedHashSet<>(List.of(rig.source.getId()))));
    int before = rig.war.getPostponementsThisCycle();
    BattleScheduleService.skipBattleDay(rig.war, at);
    assertEquals(Fixture.DAY.plusDays(1), rig.war.getBattleDay());
    assertTrue(rig.war.getBattleInstallationPicks().isEmpty());
    assertEquals(before, rig.war.getPostponementsThisCycle());
    BattleScheduleService.postpone(rig.war, at);
    assertEquals(Fixture.DAY.plusDays(2), rig.war.getBattleDay());
    assertEquals(before + 1, rig.war.getPostponementsThisCycle());
    BattleScheduleService.skipBattleDay(rig.war);
    BattleScheduleService.postpone(rig.war);
    assertEquals(Fixture.DAY.plusDays(4), rig.war.getBattleDay());
  }

  @Test
  void publicSchedulingGuardsLeaveAbsentOrIncompleteWarsUnscheduled() {
    BattleScheduleService.openVote(null);
    BattleScheduleService.enterAutoresolvePending(null);
    BattleScheduleService.skipBattleDay(null);
    BattleScheduleService.postpone(null);
    assertFalse(BattleScheduleService.scheduleFromVotes(null, uuid -> rig.attacker));
    assertFalse(BattleScheduleService.scheduleFromVotes(rig.war, null));
    assertFalse(BattleScheduleService.applyScheduledInstant(null, rig.now()));
    assertFalse(BattleScheduleService.applyScheduledInstant(rig.war, null));
    assertFalse(BattleScheduleService.scheduleBattleAtProvince(null, 12, rig.now()));
    assertFalse(BattleScheduleService.markScheduledAtProvince(null, 12));
    assertNull(BattleScheduleService.resolveBattleProvinceId(null));
    assertEquals(
        0,
        BattleScheduleService.castSpoofVotes(
            null, 21, BattleScheduleLookups.memberNameToUuid(), BelligerentRole.ATTACKER));
    assertEquals(
        BattleScheduleCloseResult.SKIPPED,
        BattleScheduleService.closeVote(
            null,
            rig.now(),
            BattleScheduleLookups.uuidToFaction(),
            BattleScheduleLookups.memberNameToUuid()));
    assertEquals(
        BattleScheduleCloseResult.SKIPPED,
        BattleScheduleService.closeVote(
            rig.war, rig.now(), null, BattleScheduleLookups.memberNameToUuid()));
    assertFalse(
        BattleScheduleService.scheduleFromVotes(rig.war, BattleScheduleLookups.uuidToFaction()));
    rig.war.setBattleDay(null);
    BattleScheduleService.skipBattleDay(rig.war, rig.now());
    BattleScheduleService.postpone(rig.war, rig.now());
    assertNull(rig.war.getBattleDay());
    assertFalse(
        BattleScheduleService.scheduleFromVotes(rig.war, BattleScheduleLookups.uuidToFaction()));
    rig.war.setScheduledBattleProvinceId(20);
    assertEquals(20, BattleScheduleService.resolveBattleProvinceId(rig.war));
  }

  @Test
  void developmentRaidWindowStillRequiresAnActiveWarAndNormalCalendarWindowsStayBounded() {
    Instant tooEarly = BattleWindowService.atScheduleHour(Fixture.DAY, 18);
    assertFalse(BattleScheduleService.isRaidWindowOpen(rig.war, tooEarly));
    assertFalse(BattleScheduleService.isBattleWindowOpen(rig.war, tooEarly));
    assertFalse(BattleScheduleService.isOnBattleDay(rig.war, null));
    assertFalse(BattleScheduleService.isOnBattleDay(null, rig.now()));
    WarDevMode.setEnabled(true);
    assertTrue(BattleScheduleService.isRaidWindowOpen(rig.war, tooEarly));
    assertFalse(BattleScheduleService.isRaidWindowOpen(null, tooEarly));
  }

  @Test
  void navalCapabilityRequiresAConfiguredShipAtTheOffensiveSidesInPlayPort() throws Exception {
    var registry = navalRegistry();
    rig.install(rig.attacker, "airport", InstallationKind.AIRPORT, 11);
    assertFalse(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(null));
    assertFalse(AttackerNavalContestService.isNavalSlotActive(null));
    registry.register(
        new PlayerVehicleRecord(
            rig.alice.getUniqueId(),
            "unknown",
            "obsolete",
            OwnershipMode.INSTALLATION,
            rig.source.getId(),
            rig.attacker.getId()));
    registry.register(
        new PlayerVehicleRecord(
            rig.alice.getUniqueId(),
            "plane",
            "glider",
            OwnershipMode.INSTALLATION,
            rig.source.getId(),
            rig.attacker.getId()));
    assertFalse(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(rig.war));
    pickAttackerPort();
    assertFalse(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(rig.war));
    registry.register(
        new PlayerVehicleRecord(
            rig.alice.getUniqueId(),
            "ship",
            "ironclad",
            OwnershipMode.INSTALLATION,
            rig.source.getId(),
            rig.attacker.getId()));
    assertTrue(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(rig.war));
    assertFalse(AttackerNavalContestService.wouldAttackerAutoLoseNaval(rig.war));
    assertFalse(AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(rig.war, 30));
    var plugin = SimpleFactions.plugin;
    SimpleFactions.plugin = null;
    try {
      assertFalse(AttackerNavalContestService.hasBerthedNavalAtInPlayPort(rig.war));
    } finally {
      SimpleFactions.plugin = plugin;
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void uncontestedNavalFightPurgesTheUnstartedBattleAndNotifiesBothCoalitions(boolean online)
      throws Exception {
    navalRegistry();
    giveArmy(rig.attacker);
    giveArmy(rig.defender);
    pickAttackerPort();
    var pending = BattleFactory.createBlank(BattleType.FIELD, "pending_naval");
    pending.setWarId(rig.war.getId());
    pending.setProvinceId(30);
    BattleManager.addBattle(pending);
    when(rig.alice.isOnline()).thenReturn(online);
    when(rig.bob.isOnline()).thenReturn(online);
    clearInvocations(rig.alice, rig.bob);

    assertTrue(AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(rig.war, 30));

    assertFalse(BattleManager.get().contains(pending));
    assertEquals(4, rig.war.getInitiativeAttacker());
    assertEquals(1, rig.war.getCampaignBattlesFought());
    assertEquals(CampaignCoalition.DEFENDER, rig.war.getPostBattleWinnerCoalition());
    verify(rig.alice, times(online ? 1 : 0)).sendMessage(CampaignUiCopy.navalAutoLossBroadcast());
    verify(rig.bob, times(online ? 1 : 0)).sendMessage(CampaignUiCopy.navalAutoLossBroadcast());
    assertFalse(
        AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(rig.war, 30),
        "A pending post-battle decision must prevent a second outcome");
  }

  @Test
  void navalAutoLossCannotReplaceAnAlreadyStartedBattleOrAnInvalidTarget() throws Exception {
    navalRegistry();
    var battle = BattleFactory.createBlank(BattleType.FIELD, "live_naval");
    battle.setWarId(rig.war.getId());
    battle.setStarted(true);
    BattleManager.addBattle(battle);
    assertFalse(AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(null, 30));
    assertFalse(AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(rig.war, 0));
    assertFalse(AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(rig.war, 30));
    assertTrue(BattleManager.get().contains(battle));
    assertEquals(0, rig.war.getCampaignBattlesFought());
  }

  @Test
  void aRejectedReschedulePreservesAlreadySentSignupReminders() {
    Instant at = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setScheduledBattleAt(at);
    rig.war.setScheduledBattleHour(21);
    rig.war.setScheduledBattleProvinceId(12);
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    rig.war.setSignupRemindersSent(Set.of(1, 2));

    assertFalse(
        BattleScheduleService.scheduleBattleAtProvince(
            rig.war, 20, BattleWindowService.atScheduleHour(Fixture.DAY, 14)));

    assertEquals(Set.of(1, 2), rig.war.getSignupRemindersSent());
    assertEquals(at, rig.war.getScheduledBattleAt());
    assertEquals(12, rig.war.getScheduledBattleProvinceId());
    assertEquals(BattleSchedulePhase.SCHEDULED, rig.war.getBattleSchedulePhase());
  }

  @Test
  void explicitSchedulingPreparesOneRealBattleAndBlocksMissingOrUndecidedTargets() {
    Instant at = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setCampaignBattleSchedule(List.of(field(12, false), field(20, true)));
    assertEquals(12, BattleScheduleService.resolveBattleProvinceId(rig.war));
    assertTrue(BattleScheduleService.applyScheduledInstant(rig.war, at));
    var battle = BattleManager.getByWarId(rig.war.getId());
    assertNotNull(battle);
    assertEquals(12, battle.getProvinceId());
    assertEquals(at, rig.war.getScheduledBattleAt());
    assertTrue(BattleScheduleService.markScheduledAtProvince(rig.war, 12));
    assertSame(battle, BattleManager.getByWarId(rig.war.getId()));
    rig.war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
    rig.war.setPostBattleWinnerCoalition(CampaignCoalition.AGGRESSOR);
    rig.war.setPostBattleChoiceResolved(false);
    assertNull(BattleScheduleService.resolveScheduledProvinceId(rig.war));
    assertFalse(BattleScheduleService.applyScheduledInstant(rig.war, at));
    assertSame(battle, BattleManager.getByWarId(rig.war.getId()));
    rig.war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
    rig.war.setPostBattleChoiceResolved(true);
    rig.war.setCampaignProvinces(null);
    assertFalse(BattleScheduleService.applyScheduledInstant(rig.war, at));
    rig.war.getBattleVotes().put(rig.alice.getUniqueId(), Set.of(21));
    rig.war.getBattleVotes().put(rig.bob.getUniqueId(), Set.of(21));
    assertFalse(
        BattleScheduleService.scheduleFromVotes(
            rig.war, BattleScheduleLookups.uuidToFactionForWar(rig.war)));
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    assertEquals(
        BattleScheduleCloseResult.SKIPPED,
        BattleScheduleService.closeVote(
            rig.war,
            BattleWindowService.atScheduleHour(Fixture.DAY, 15),
            BattleScheduleLookups.uuidToFaction(),
            BattleScheduleLookups.memberNameToUuid(),
            null));
  }

  @Test
  void blankImportedRosterEntriesAreIgnoredWhileValidSiblingsStillResolve() {
    rig.attacker.getOrCreateMainGuild().getMembers().add("");
    rig.attacker.getOrCreateMainGuild().getMembers().add(" ");
    assertSame(
        rig.defender,
        BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(rig.bob.getUniqueId()));
    assertNull(BattleScheduleLookups.uuidToFactionForWar(rig.war).apply(UUID.randomUUID()));
  }

  @Test
  void resiegePublicEntryGuardsAndMissingAxisKeepUnrelatedSchedulesIntact() {
    assertDoesNotThrow(() -> CampaignScheduleService.ensureReSiegeInsert(null));
    assertDoesNotThrow(() -> CampaignScheduleService.ensureReSiegeInsert(rig.war));
    var fort = new OperationalFort("known", rig.defender, 21, 1);
    rig.war.setCampaignProvinces(null);
    CampaignScheduleService.insertSiegeAtCurrentIndex(rig.war, fort, 12);
    var slot = rig.war.getCampaignBattleSchedule().getFirst();
    assertEquals(21, slot.provinceId());
    assertEquals(12, slot.sortProvinceId());
  }

  @Test
  void placementKeepsAnObjectiveUntilItIsUpgradedAndOrdersLegacyNavalKinds() {
    var fort = rig.install(rig.defender, "objective-fort", InstallationKind.FORT, 20);
    rig.war.putFortController(fort.getStableKey(), CampaignCoalition.DEFENDER);
    var ctx = context(FortZocIndex.fromGameState());
    place(ctx, 20, BattleTrigger.CADENCE, null, null);
    place(ctx, 20, BattleTrigger.FORT_ZOC, fort.getId(), null);
    assertEquals(2, ctx.invasion().size());
    place(ctx, 20, BattleTrigger.OBJECTIVE, null, null);
    assertEquals(field(20, true), ctx.invasion().getLast());

    var legacy = context(FortZocIndex.fromForts(List.of()));
    legacy
        .counter()
        .add(new ScheduledCampaignBattle(12, CampaignBattleKind.NAVAL_INVASION, false, null));
    CampaignBattlePlacer.placeBattle(
        legacy,
        rig.war,
        ScheduleLeg.COUNTER,
        12,
        BattleTrigger.NAVAL,
        CampaignCoalition.DEFENDER,
        null,
        "port");
    CampaignBattlePlacer.placeBattle(
        legacy,
        rig.war,
        ScheduleLeg.COUNTER,
        12,
        BattleTrigger.OBJECTIVE,
        CampaignCoalition.DEFENDER,
        null,
        null);
    assertEquals(
        List.of(
            CampaignBattleKind.FIELD, CampaignBattleKind.NAVAL, CampaignBattleKind.NAVAL_INVASION),
        legacy.counter().stream().map(ScheduledCampaignBattle::kind).toList());
    ScheduledCampaignBattle imported = new ScheduledCampaignBattle(12, null, false, " ", " ", 0);
    assertEquals(CampaignBattleKind.FIELD, imported.kind());
    assertNull(imported.fortInstallationId());
    assertNull(imported.portInstallationId());
    assertNull(imported.chronologyProvinceId());
    assertEquals(12, imported.sortProvinceId());
  }

  @Test
  void seaOnlyRouteEndpointProducesANavalSlotAndIgnoresNeutralPorts() {
    var ports =
        PortSeaZocIndex.fromPorts(
            List.of(new OperationalPort(rig.target.getId(), rig.defender, 20, 1)));
    var result =
        CampaignScheduleBuilder.buildAll(
            rig.war, List.of(30), 0, 0, -1, FortZocIndex.fromForts(List.of()), ports);
    assertEquals(
        List.of(
            new ScheduledCampaignBattle(
                30, CampaignBattleKind.NAVAL, false, null, rig.target.getId())),
        result.invasion());
    var neutral = rig.domain.saved("neutral_port_owner", "Carol");
    var neutralPort =
        PortSeaZocIndex.fromPorts(List.of(new OperationalPort("neutral", neutral, 20, 1)));
    assertTrue(
        CampaignScheduleBuilder.buildAll(
                rig.war, List.of(30), 0, 0, -1, FortZocIndex.fromForts(List.of()), neutralPort)
            .invasion()
            .isEmpty());
    var abandoned =
        PortSeaZocIndex.fromPorts(List.of(new OperationalPort("abandoned", null, 20, 1)));
    assertTrue(
        CampaignScheduleBuilder.buildAll(
                rig.war, List.of(30), 0, 0, -1, FortZocIndex.fromForts(List.of()), abandoned)
            .invasion()
            .isEmpty());
  }

  @Test
  void scheduleLoggingWritesTheActualFortAndChronologyAndSupportsEmptySchedules() throws Exception {
    for (String field :
        List.of("logFile", "relationsLogFile", "movementLogFile", "civilwarLogFile", "warLogFile"))
      rig.remember(LogManager.class, field);
    Field field = LogManager.class.getDeclaredField("sessionLines");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<String> pending = (List<String>) field.get(null);
    var previous = new ArrayList<>(pending);
    try {
      var slot = new ScheduledCampaignBattle(21, CampaignBattleKind.SIEGE, false, "fort", null, 12);
      LogManager.configure(false, false, temporary.toFile());
      CampaignScheduleLogger.logSchedule(
          "Disabled", rig.war, rig.war.getCampaignProvinces(), List.of(slot));
      assertFalse(Files.exists(temporary.resolve("logs")));
      assertEquals(previous, pending, "Disabled logging must not alter the pending session");
      LogManager.configure(true, false, temporary.toFile());
      LogManager.beginSession("Campaign schedule proof");
      CampaignScheduleLogger.logSchedule(
          "Invasion", rig.war, rig.war.getCampaignProvinces(), List.of(slot, field(20, true)));
      CampaignScheduleLogger.logSchedule(
          "Empty", rig.war, ScheduleLeg.COUNTER, List.of(), List.of());
      LogManager.flush();
      String written = Files.readString(temporary.resolve("logs/log.txt"));
      assertTrue(written.contains("province=21"));
      assertTrue(written.contains("sortProvince=12"));
      assertTrue(written.contains("(empty)"));
      assertTrue(Files.readString(temporary.resolve("logs/war.log")).contains("fort=fort"));
      assertEquals("null", CampaignScheduleLogger.formatSlot(null, null));
      assertTrue(CampaignScheduleLogger.formatSlot(slot, null).contains("homeAxis=off-axis"));
      assertEquals(
          "n/a", CampaignScheduleLogger.fightOrderSummary(null, ScheduleLeg.INVASION, slot));
      assertTrue(
          CampaignScheduleLogger.fightOrderSummary(
                  context(FortZocIndex.fromForts(List.of())),
                  ScheduleLeg.COUNTER,
                  field(999, false))
              .contains("off-axis"));
    } finally {
      pending.clear();
      pending.addAll(previous);
    }
  }

  @Test
  void countdownAndRepeatingSchedulerExposeOnlyRelevantCurrentMilestones() throws Exception {
    assertTrue(CampaignScheduleCountdown.formatNextMilestone(null, rig.now()).isEmpty());
    assertTrue(CampaignScheduleCountdown.formatNextMilestone(rig.war, null).isEmpty());
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    assertTrue(CampaignScheduleCountdown.formatNextMilestone(rig.war, rig.now()).isEmpty());
    Instant at = BattleWindowService.atScheduleHour(Fixture.DAY, 21);
    rig.war.setScheduledBattleAt(at);
    assertEquals(
        "Starting now", CampaignScheduleCountdown.formatNextMilestone(rig.war, at).orElseThrow());
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
    assertTrue(CampaignScheduleCountdown.formatNextMilestone(rig.war, at).isEmpty());
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    rig.war.setBattleDay(null);
    assertTrue(CampaignScheduleCountdown.formatNextMilestone(rig.war, at).isEmpty());
    var started = BattleFactory.createBlank(BattleType.FIELD, "countdown_live");
    started.setWarId(rig.war.getId());
    started.setStarted(true);
    BattleManager.addBattle(started);
    assertTrue(CampaignScheduleCountdown.formatNextMilestone(rig.war, at).isEmpty());
    BattleManager.deleteBattle(started);
    rig.remember(BattleScheduleTickService.class, "lastProcessedUtcHour");
    rig.remember(BattleScheduleTickService.class, "lastProcessedUtcDate");
    WarManager.get().clear();
    assertEquals(0, BattleScheduleTickService.tick(null));
    int previous = rig.domain.ui.repeatingTasks.size();
    BattleScheduleTickService.start();
    assertEquals(previous + 1, rig.domain.ui.repeatingTasks.size());
    rig.domain.ui.repeatingTasks.getLast().run();
    assertEquals(
        0, BattleScheduleTickService.tick(rig.now()), "A repeated hour must not reprocess wars");
  }

  private PlayerVehicleRegistry navalRegistry() throws Exception {
    for (String name :
        List.of(
            "personalSlotLimit",
            "defaultPerPerson",
            "maintenanceHourlyDamagePercent",
            "maintenanceMinHealthPercent",
            "maintenanceIntervalTicks",
            "categoryIds",
            "typesByCategory",
            "categoryByVehicleTypeId",
            "categoryDisplayNames",
            "feeExcludedCategories")) rig.remember(VehiclesConfigLoader.class, name);
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("categories.ships.ironclad.upkeep", 1);
    yaml.set("categories.ships.ironclad.size", 1);
    yaml.set("categories.aircraft.glider.upkeep", 1);
    yaml.set("categories.aircraft.glider.size", 1);
    Path file = temporary.resolve("vehicles.yml");
    yaml.save(file.toFile());
    VehiclesConfigLoader.load(file.toFile());
    PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    Field field = SimpleFactions.class.getDeclaredField("vehicleRegistry");
    field.setAccessible(true);
    field.set(rig.domain.ui.plugin, registry);
    link(10, 30);
    link(30, 12);
    rig.war.setCampaignProvinces(List.of(10, 30, 12, 20));
    rig.war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(
                30, CampaignBattleKind.NAVAL, false, null, rig.target.getId()),
            field(20, true)));
    return registry;
  }

  private void pickAttackerPort() {
    rig.war.setBattleInstallationPicks(
        Map.of(rig.attacker.getId(), new LinkedHashSet<>(List.of(rig.source.getId()))));
    rig.war.setBattleInstallationPicksBattleDay(rig.war.getBattleDay());
  }

  private void giveArmy(Faction faction) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("offense", true);
    Regiment regiment = new Regiment("naval_guard", yaml);
    faction.getMilitary().getRegiments().add(regiment);
    assertTrue(faction.getMilitary().adminAdjustSlots(regiment.getId(), 2).allowed());
  }

  private ScheduledCampaignBattle field(int id, boolean required) {
    return new ScheduledCampaignBattle(id, CampaignBattleKind.FIELD, required, null);
  }

  private CampaignScheduleBuildContext context(FortZocIndex forts) {
    return new CampaignScheduleBuildContext(rig.war.getCampaignProvinces(), 12, 2, 3, forts);
  }

  private void place(
      CampaignScheduleBuildContext ctx, int id, BattleTrigger trigger, String fort, String port) {
    CampaignBattlePlacer.placeBattle(
        ctx, rig.war, ScheduleLeg.INVASION, id, trigger, CampaignCoalition.AGGRESSOR, fort, port);
  }

  private void link(int a, int b) {
    graph.get(a).addNeighbour(b);
    graph.get(b).addNeighbour(a);
  }
}
