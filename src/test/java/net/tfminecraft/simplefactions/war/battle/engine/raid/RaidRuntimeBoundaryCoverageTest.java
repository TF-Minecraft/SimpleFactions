package net.tfminecraft.simplefactions.war.battle.engine.raid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignNavalAutoLossReminderService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent;
import net.tfminecraft.simplefactions.war.battle.warband.*;
import net.tfminecraft.simplefactions.war.campaign.admin.CampaignTimeCommandService;
import net.tfminecraft.simplefactions.war.campaign.raid.*;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidLaunchService;
import net.tfminecraft.simplefactions.war.campaign.raid.intruder.CampaignRaidIntruderService;
import net.tfminecraft.simplefactions.war.campaign.runtime.*;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignScheduleCountdown;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDevMode;
import net.tfminecraft.simplefactions.war.enums.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class RaidRuntimeBoundaryCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(BattleScheduleTickService.class, "lastProcessedUtcHour");
    rig.remember(BattleScheduleTickService.class, "lastProcessedUtcDate");
    rig.remember(Cache.class, "battleEmptySideGraceSeconds");
    Cache.battleEmptySideGraceSeconds = 300;
  }

  @AfterEach
  void close() throws Exception {
    if (rig != null) rig.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"9223372036854775807d", "9223372036854775807s", "31556889864403200s"})
  void anUnrepresentableClockAdvanceReturnsAnErrorWithoutPoisoningCurrentTime(String token) {
    Duration before = CampaignClock.getOffset();
    long writes = rig.warWrites();
    var result = assertDoesNotThrow(() -> CampaignTimeCommandService.add(token));
    assertFalse(result.success());
    assertTrue(result.message().contains("Invalid duration"));
    assertEquals(before, CampaignClock.getOffset());
    assertDoesNotThrow(CampaignClock::now);
    assertEquals(writes, rig.warWrites());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1h?2m", "?1h", "0s"})
  void malformedCompoundAndZeroDurationsAreRejectedBeforeChangingTheClock(String token) {
    var before = CampaignClock.getOffset();
    assertThrows(IllegalArgumentException.class, () -> CampaignDurationParser.parse(token));
    var result = CampaignTimeCommandService.add(token);
    assertFalse(result.success());
    assertEquals(before, CampaignClock.getOffset());
  }

  @Test
  void raidMessagesDistinguishMissingContextAndMissingMembership() {
    assertNull(CampaignRaidMessages.messageForLaunchResult(null));
    assertNull(CampaignRaidMessages.messageForJoinResult(null));
    assertEquals(
        CampaignRaidMessages.NOT_PARTICIPANT,
        CampaignRaidMessages.messageForValidateResult(
            CampaignRaidResults.ValidateLaunchResult.REJECTED_NOT_PARTICIPANT));
    assertEquals(
        CampaignRaidMessages.RAID_NOT_FOUND,
        CampaignRaidMessages.messageForJoinResult(
            CampaignRaidResults.JoinResult.REJECTED_RAID_NOT_FOUND));
  }

  @Test
  void advancingAcrossVoteCloseTicksTheRealWarAndReportsItsPostponement() throws Exception {
    rig.remember(Cache.class, "warBattleVotingMaxPostponements");
    Cache.warBattleVotingMaxPostponements = 2;
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 14));
    long writes = rig.warWrites();
    var result = CampaignTimeCommandService.add("3h");
    assertTrue(result.success());
    assertTrue(result.message().contains("1 war(s) updated"));
    assertEquals(Fixture.DAY.plusDays(1), rig.war.getBattleDay());
    assertEquals(1, rig.war.getPostponementsThisCycle());
    assertTrue(rig.warWrites() > writes);
    assertEquals(17, BattleWindowService.scheduleHour(rig.now()));
  }

  @Test
  void missingBattleDayCannotResetAnExistingClockOffset() {
    rig.war.setBattleDay(null);
    Duration before = CampaignClock.getOffset();
    var result = CampaignTimeCommandService.skipToBattleDay(rig.war);
    assertFalse(result.success());
    assertEquals("§cWar has no battle day set.", result.message());
    assertEquals(before, CampaignClock.getOffset());
  }

  @Test
  void anUnrelatedWarOrPlayerCannotRevealOrJoinTheHiddenRaidBand() {
    War unrelated =
        new War(
            rig.war.getId() + 1,
            rig.domain.saved("outsider_one", "OutsideOne"),
            rig.domain.saved("outsider_two", "OutsideTwo"));
    WarManager.get().addFirst(unrelated);
    var raid = muster();
    assertEquals(List.of(unrelated, rig.war), WarManager.getActive());
    Warband band = CampaignRaidWarbandService.getAttackerWarband(raid);
    Player visitor = rig.player("Visitor");
    assertTrue(CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(band, visitor));
    assertFalse(CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(band, rig.alice));
    assertTrue(CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(band, rig.bob));
    new CampaignRaidWarbandService.Listener()
        .onQuitLow(new PlayerQuitEvent(visitor, net.kyori.adventure.text.Component.empty()));
    assertTrue(band.isPendingLeader());
    assertTrue(band.getMemberIds().isEmpty());
    CampaignRaidWarbandService.signupAttacker(
        rig.war, raid, rig.alice.getUniqueId(), rig.alice.getName());
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
    assertFalse(CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(band, visitor));
  }

  @Test
  void anUnrelatedFactionsLoginDoesNotEnrollInAnotherWarRaid() {
    var raid = fight();
    Player visitor = rig.player("OutsideOne");
    var outsider = rig.domain.saved("outside_one", "OutsideOne");
    var other = rig.domain.saved("outside_two", "OutsideTwo");
    WarManager.addWar(new War(rig.war.getId() + 1, outsider, other));
    var band = CampaignRaidWarbandService.getDefenderWarband(raid);
    Set<UUID> before = Set.copyOf(band.getMemberIds());
    CampaignRaidWarbandService.tryEnrollDefenderOnLogin(visitor);
    assertEquals(before, Set.copyOf(band.getMemberIds()));
    assertNull(WarbandManager.getByMemberId(visitor.getUniqueId()));
  }

  @Test
  void noRaidOrMissingSavedBattleIdentityCannotWarnAnUnregisteredAttacker() {
    Player rogue = rig.player("Rogue");
    rig.attacker.addMember("Rogue");
    CampaignRaidIntruderService.onProvinceEnter(rogue, rig.target.getProvince());
    verify(rogue, never()).sendMessage(CampaignRaidMessages.INTRUDER);
    CampaignRaid raid = muster();
    var data = raid.toData();
    data.state = "fighting";
    data.battleId = null;
    CampaignRaid restored = CampaignRaid.fromData(data);
    rig.war.setActiveCampaignRaid(restored);
    CampaignRaidIntruderService.onProvinceEnter(rogue, rig.target.getProvince());
    verify(rogue, never()).sendMessage(CampaignRaidMessages.INTRUDER);
    assertSame(restored, CampaignRaidService.getActive(rig.war));
  }

  @Test
  void aPubliclyIncompleteRaidIdCannotEmitAnUnclearEnterWarning() {
    Player rogue = rig.player("Rogue");
    rig.attacker.addMember("Rogue");
    CampaignRaid raid = fight();
    raid.setId(null);
    CampaignRaidIntruderService.onProvinceEnter(rogue, rig.target.getProvince());
    verify(rogue, never()).sendMessage(CampaignRaidMessages.INTRUDER);
    assertFalse(CampaignRaidIntruderService.consumeIntruderDeath(rogue.getUniqueId()));
  }

  @Test
  void developmentListingsStillRequireRealOwnershipAndSupportedKinds() {
    WarDevMode.setEnabled(true);
    rig.war.setBattleDay(null);
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 2));
    assertEquals(
        List.of(rig.source),
        CampaignRaidEligibilityService.listValidSources(rig.war, rig.attacker.getId(), rig.now()));
    assertEquals(
        1,
        CampaignRaidEligibilityService.listValidTargets(
                rig.war, rig.attacker.getId(), rig.source.getId(), rig.now())
            .size());
    var station =
        rig.install(rig.defender, "unsupported_target", InstallationKind.TRAIN_STATION, 21);
    assertFalse(
        CampaignRaidEligibilityService.isValidTarget(
            rig.war, rig.attacker.getId(), rig.source.getId(), station.getId(), rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            null,
            rig.attacker.getId(),
            rig.target.getId(),
            RaidTargetService.RaidKind.NAVAL,
            rig.now()));
    assertFalse(
        CampaignRaidEligibilityService.isValidTargetForRaidKind(
            rig.war, rig.attacker.getId(), rig.target.getId(), null, rig.now()));
    assertNull(CampaignRaidService.getActive(rig.war));
  }

  @Test
  void activityCopyUsesTheActualRunningBattleBeforeSavedScheduleState() {
    assertEquals("Between Battles", CampaignUiCopy.resolveActivityStatus(null));
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
    assertEquals("Autoresolve Pending", CampaignUiCopy.resolveActivityStatus(rig.war));
    rig.war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    Battle battle = mainBattle();
    assertSame(battle, BattleManager.getByWarId(rig.war.getId()));
    assertEquals("In Battle", CampaignUiCopy.resolveActivityStatus(rig.war));
  }

  @Test
  void rejectedSignupInputsAndMountedPlayersLeaveTheRosterIntact() {
    Warband band = new Warband("signup_boundary", rig.bob);
    Set<UUID> before = Set.copyOf(band.getMemberIds());
    assertFalse(CampaignWarbandSignupService.isSignupOpen(null, rig.now()));
    assertFalse(CampaignWarbandSignupService.isSignupOpen(rig.war, null));
    assertEquals(
        "Invalid warband signup", CampaignWarbandSignupService.signup(null, band, rig.attacker));
    assertEquals(
        "Invalid warband signup",
        CampaignWarbandSignupService.signupMember(null, "Alice", band, rig.attacker));
    when(rig.alice.isInsideVehicle()).thenReturn(true);
    assertEquals(
        WarbandVehicleRules.JOIN_BLOCKED_MOUNTED,
        CampaignWarbandSignupService.signup(rig.alice, band, rig.attacker));
    assertEquals(before, Set.copyOf(band.getMemberIds()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing_war",
        "missing_time",
        "other_day",
        "live_battle",
        "no_leader",
        "offline_leader"
      })
  void navalWarningsRequireAnUpcomingBattleAndAnAvailableLeader(String missing) {
    rig.war.setCampaignProvinces(List.of(10, 15, 20));
    rig.war.setCampaignStartProvinceId(15);
    rig.war.setObjectiveProvinceId(20);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(15, CampaignBattleKind.NAVAL, false, null)));
    rig.war.setCampaignScheduleIndex(0);
    if (missing.equals("live_battle")) mainBattle();
    if (missing.equals("no_leader")) rig.attacker.setLeader(null);
    if (missing.equals("offline_leader")) when(rig.alice.isOnline()).thenReturn(false);
    Instant now = missing.equals("other_day") ? rig.now().plus(Duration.ofDays(1)) : rig.now();
    clearInvocations(rig.alice, rig.bob);
    CampaignNavalAutoLossReminderService.processReminders(
        missing.equals("missing_war") ? null : rig.war,
        missing.equals("missing_time") ? null : now);
    verify(rig.alice, never()).sendMessage(CampaignUiCopy.navalAutoLossLeaderPing());
    verify(rig.bob, never()).sendMessage(CampaignUiCopy.navalAutoLossLeaderPing());
  }

  private CampaignRaid muster() {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    return CampaignRaidService.getActive(rig.war);
  }

  private CampaignRaid fight() {
    CampaignRaid raid = muster();
    CampaignRaidWarbandService.signupAttacker(
        rig.war, raid, rig.alice.getUniqueId(), rig.alice.getName());
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
    assertTrue(BattleManager.getByString(raid.getBattleId()).hasStarted());
    return raid;
  }

  @Test
  void directClockAdvancesValidateBeforeMutationAndKeepSignedOffsets() {
    CampaignClock.reset();
    CampaignClock.add(Duration.ofHours(3));
    assertEquals(Duration.ofHours(3), CampaignClock.getOffset());
    CampaignClock.add(Duration.ofHours(-5));
    assertEquals(Duration.ofHours(-2), CampaignClock.getOffset());
    Instant lower = Instant.now().minus(Duration.ofHours(2)).minusSeconds(1);
    Instant upper = Instant.now().minus(Duration.ofHours(2)).plusSeconds(1);
    assertTrue(CampaignClock.now().isAfter(lower));
    assertTrue(CampaignClock.now().isBefore(upper));
    for (Duration invalid :
        List.of(Duration.ofSeconds(Long.MAX_VALUE), Duration.ofDays(365250000000L))) {
      assertThrows(IllegalArgumentException.class, () -> CampaignClock.add(invalid));
      assertEquals(Duration.ofHours(-2), CampaignClock.getOffset());
    }
  }

  @Test
  void aValidOffsetBeyondIntegerSecondsKeepsItsCorrectReadableDuration() {
    CampaignClock.reset();
    var result = CampaignTimeCommandService.add("2147483648s");
    assertTrue(result.success());
    assertEquals(Duration.ofSeconds(2147483648L), CampaignClock.getOffset());
    assertTrue(result.message().contains("+24855d 3h 14m 8s"), result.message());
    assertTrue(CampaignTimeCommandService.statusLines().getFirst().contains("+24855d 3h 14m 8s"));
    CampaignClock.reset();
    CampaignClock.add(Duration.ofSeconds(-2147483648L));
    assertTrue(CampaignTimeCommandService.statusLines().getFirst().contains("-24855d 3h 14m 8s"));
  }

  private Battle mainBattle() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "live_main_campaign");
    battle.setWarId(rig.war.getId());
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 0, 64, 0));
      side.setJail(new Location(rig.domain.ui.world, 10, 64, 10));
    }
    BattleManager.addBattle(battle);
    assertNull(battle.start());
    assertTrue(battle.hasStarted());
    return battle;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void longDatedCampaignCountdownsKeepTheActualNumberOfDays(boolean scheduled) {
    int hour = scheduled ? 22 : 0;
    Instant now = BattleWindowService.atScheduleHour(Fixture.DAY, hour);
    LocalDate future = Fixture.DAY.plusYears(70);
    rig.war.setBattleDay(future);
    rig.war.setBattleSchedulePhase(
        scheduled ? BattleSchedulePhase.SCHEDULED : BattleSchedulePhase.VOTING);
    if (scheduled) rig.war.setScheduledBattleAt(BattleWindowService.atScheduleHour(future, hour));
    String expected = scheduled ? "Starts in 25568d" : "Battle day in 25568d";
    assertEquals(
        Optional.of(expected), CampaignScheduleCountdown.formatNextMilestone(rig.war, now));
    assertEquals(future, rig.war.getBattleDay());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aRaidEndedByItsStartEventCannotResumeOrMutateAReplacement(boolean replace) {
    CampaignRaid original = muster();
    CampaignRaidWarbandService.signupAttacker(
        rig.war, original, rig.alice.getUniqueId(), rig.alice.getName());
    var replacementTarget =
        rig.install(rig.defender, "replacement_target", InstallationKind.PORT, 21);
    CampaignRaid[] replacement = {null};
    int[] callbackWrites = {-1};
    int[] startedCallbacks = {0};
    var plugins = Bukkit.getPluginManager();
    doAnswer(
            call -> {
              if (call.getArgument(0) instanceof BattleStartedEvent event
                  && original.getId().equals(event.getBattleId())) {
                startedCallbacks[0]++;
                CampaignRaidService.endRaid(rig.war, original.getMusterEndsAt());
                if (replace) {
                  assertEquals(
                      CampaignRaidResults.LaunchResult.STARTED,
                      CampaignRaidService.beginMuster(
                          rig.war,
                          rig.attacker,
                          rig.source.getId(),
                          replacementTarget.getId(),
                          original.getMusterEndsAt()));
                  replacement[0] = CampaignRaidService.getActive(rig.war);
                  assertNotEquals(original.getId(), replacement[0].getId());
                }
                callbackWrites[0] = rig.writes.size();
              }
              return null;
            })
        .when(plugins)
        .callEvent(any(org.bukkit.event.Event.class));

    CampaignRaidLaunchService.startFight(rig.war, original.getMusterEndsAt());

    assertEquals(1, startedCallbacks[0]);
    assertAll(
        () -> assertEquals(CampaignRaidState.ENDED, original.getState()),
        () -> assertNull(BattleManager.getByString(original.getId())),
        () -> assertNull(CampaignRaidWarbandService.getAttackerWarband(original)),
        () ->
            assertTrue(
                rig.writes.subList(callbackWrites[0], rig.writes.size()).stream()
                    .noneMatch(
                        write ->
                            write.value() instanceof BattleData data
                                && original.getId().equals(data.id)),
                "An ended raid battle must not be persisted again after its deletion"),
        () ->
            assertFalse(
                CampaignRaidService.isInstallationRepairLocked(
                    rig.war, rig.target, original.getMusterEndsAt()),
                "The ended start must not add a repair lock"),
        () -> {
          if (replace) {
            assertSame(replacement[0], CampaignRaidService.getActive(rig.war));
            assertEquals(CampaignRaidState.MUSTER, replacement[0].getState());
            assertNotNull(CampaignRaidWarbandService.getAttackerWarband(replacement[0]));
            assertNull(replacement[0].getBattleId());
          } else {
            assertNull(CampaignRaidService.getActive(rig.war));
          }
        });
  }

  @Test
  void zeroOffsetStatusExplicitlyReportsRealTime() {
    CampaignClock.reset();
    assertTrue(CampaignTimeCommandService.statusLines().getFirst().contains("real time"));
    assertEquals(Duration.ZERO, CampaignClock.getOffset());
  }

  @Test
  void skippingToBattleDayProcessesAndReportsAnotherWarsPendingAutoresolve() throws Exception {
    War other = otherCampaignForClockEvents();
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY.minusDays(1), 12));

    var result = CampaignTimeCommandService.skipToBattleDay(rig.war);

    assertTrue(result.success(), result.message());
    assertTrue(result.message().contains("1 war(s) updated"), result.message());
    assertEquals(Fixture.DAY, rig.war.getBattleDay());
    assertEquals(Fixture.DAY, BattleScheduleService.battleDayDate(CampaignClock.now()));
    assertEquals(0, BattleScheduleService.battleDayHour(CampaignClock.now()));
    assertEquals(1, other.getCampaignBattlesFought());
    assertEquals(BattleSchedulePhase.VOTING, other.getBattleSchedulePhase());
    assertFalse(other.isPostBattleChoiceResolved());
  }

  @ParameterizedTest
  @CsvSource({
    "REJECTED_NOT_PARTICIPANT, §cYou are not a belligerent in this war.",
    "REJECTED_NOT_ATTACKER_COALITION, §cOnly the attacking coalition can join this raid.",
    "REJECTED_ALREADY_JOINED, §7You are already in this campaign raid muster.",
    "REJECTED_MOUNTED_ON_VEHICLE, §cYou cannot join a raid while mounted on a vehicle."
  })
  void raidJoinMessagesExplainTheSpecificRejectedAction(
      CampaignRaidResults.JoinResult result, String expected) {
    assertEquals(expected, CampaignRaidMessages.messageForJoinResult(result));
  }

  @Test
  void timeJumpWarnsWhenAnOverdueBattlesStartListenerChangesTheClock() throws Exception {
    War overdue = otherCampaignForClockEvents();
    overdue.setBattleDay(Fixture.DAY.minusDays(1));
    overdue.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    overdue.setScheduledBattleAt(BattleWindowService.atScheduleHour(Fixture.DAY.minusDays(1), 21));
    overdue.setScheduledBattleProvinceId(32);
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "overdue_clock_battle");
    battle.setWarId(overdue.getId());
    battle.setProvinceId(32);
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 320, 64, 0));
      side.setJail(new Location(rig.domain.ui.world, 325, 64, 0));
    }
    BattleManager.addBattle(battle);
    int[] starts = {0};
    Duration[] beforeListener = {null};
    var plugins = Bukkit.getPluginManager();
    doAnswer(
            call -> {
              if (call.getArgument(0) instanceof BattleStartedEvent event
                  && battle.getId().equals(event.getBattleId())) {
                starts[0]++;
                beforeListener[0] = CampaignClock.getOffset();
                CampaignClock.add(Duration.ofDays(1));
              }
              return null;
            })
        .when(plugins)
        .callEvent(any(org.bukkit.event.Event.class));

    var result = CampaignTimeCommandService.skipToBattleDay(rig.war);

    assertEquals(1, starts[0], "The overdue battle must actually fire its public start event");
    assertFalse(result.success());
    assertEquals("§cCould not align campaign clock to war battle day.", result.message());
    assertEquals(Fixture.DAY, rig.war.getBattleDay());
    assertEquals(Fixture.DAY.plusDays(1), BattleScheduleService.battleDayDate(CampaignClock.now()));
    assertEquals(beforeListener[0].plusDays(1), CampaignClock.getOffset());
    assertSame(battle, BattleManager.getByWarId(overdue.getId()));
    assertTrue(battle.hasStarted());
  }

  private War otherCampaignForClockEvents() throws Exception {
    rig.remember(Cache.class, "warAutoresolveLuck");
    Cache.warAutoresolveLuck = 0;
    rig.domain.provincesEnabled(true);
    Province home = new Province(31, "PLAINS", 50, 31, 0);
    Province border = new Province(32, "PLAINS", 50, 32, 0);
    Province capital = new Province(33, "PLAINS", 50, 33, 0);
    home.addNeighbour(32);
    border.addNeighbour(31);
    border.addNeighbour(33);
    capital.addNeighbour(32);
    ProvinceManager provinces = new ProvinceManager();
    provinces.start(Map.of(31, home, 32, border, 33, capital));
    when(rig.domain.ui.plugin.getProvinceManager()).thenReturn(provinces);
    YamlConfiguration config = new YamlConfiguration();
    config.set("guard.item.material", "PAPER");
    config.set("guard.offense", true);
    RegimentLoader.oList.add(new Regiment("guard", config.getConfigurationSection("guard")));
    var attacker = rig.domain.saved("clock_other_a", "OtherA");
    var defender = rig.domain.saved("clock_other_b", "OtherB");
    attacker.addProvince(31);
    attacker.setCapital(31, true, false);
    defender.addProvince(32);
    defender.addProvince(33);
    defender.setCapital(33, true, false);
    attacker.getMilitary().getRegiment("guard").setCurrentSlots(10);
    defender.getMilitary().getRegiment("guard").setCurrentSlots(6);
    War other = new War(rig.war.getId() + 1, attacker, defender);
    other.setGoal(WarGoalType.WAR);
    other.setWarType(WarType.WAR);
    other.setCampaignProvinces(List.of(31, 32, 33));
    other.setCampaignStartProvinceId(32);
    other.setObjectiveProvinceId(33);
    other.setCursorIndex(1);
    other.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(32, CampaignBattleKind.FIELD, true, null),
            new ScheduledCampaignBattle(33, CampaignBattleKind.FIELD, true, null)));
    other.setInitiativeAttacker(4);
    other.setInitiativeDefender(4);
    other.setBattleDay(Fixture.DAY);
    other.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
    WarManager.addWar(other);
    return other;
  }
}
