package net.tfminecraft.simplefactions.war.campaign.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleAutoresolveCoverageTest {
  private static final LocalDate DAY = LocalDate.of(2026, 10, 10);
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private Faction attacker;
  private Faction defender;
  private Player alice;
  private Player bob;
  private Duration previousOffset;
  private final Map<Field, Object> settings = new LinkedHashMap<>();
  private Map<Player, Request> oldRequests;
  private Map<Integer, List<WarCommitment>> oldCommitments;
  private List<Battle> oldBattles;
  private Random previousRandom;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    Files.createDirectories(files.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    oldRequests = new HashMap<>(requests());
    requests().clear();
    oldCommitments = new LinkedHashMap<>(commitments());
    commitments().clear();
    oldBattles = new ArrayList<>(BattleManager.get());
    BattleManager.get().clear();
    previousRandom = (Random) field(BattleAutoresolveService.class, "randomOverride").get(null);
    BattleAutoresolveService.setRandomForTests(null);
    previousOffset = CampaignClock.getOffset();
    time(BattleWindowService.atScheduleHour(DAY, 10));
    setting("warVoteCloseHour", 16);
    setting("warBattleLivesPerRegiment", 5);
    setting("warBattleDeathsPerRegimentLoss", 5);
    setting("warAutoresolveLuck", 0.0);
    setting("warAutoresolveLoserLossFraction", 0.5);
    setting("warFirstBattleAtBorder", true);
    setting("warBattleVotingMaxPostponements", 1);
    fixture.provincesEnabled(true);
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("guard.item.material", "PAPER");
    yaml.set("guard.default-slots", 0);
    yaml.set("guard.offense", true);
    RegimentLoader.oList.add(new Regiment("guard", yaml.getConfigurationSection("guard")));
    attacker = fixture.saved("attacker", "Alice");
    defender = fixture.saved("defender", "Bob");
    attacker.getMilitary().getRegiment("guard").setCurrentSlots(10);
    defender.getMilitary().getRegiment("guard").setCurrentSlots(6);
    Province one = new Province(1, "PLAINS", 50, 10, 0);
    Province two = new Province(2, "PLAINS", 50, 20, 0);
    Province three = new Province(3, "PLAINS", 50, 30, 0);
    one.addNeighbour(2);
    two.addNeighbour(1);
    two.addNeighbour(3);
    three.addNeighbour(2);
    ProvinceManager provinces = new ProvinceManager();
    provinces.start(Map.of(1, one, 2, two, 3, three));
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(provinces);
    attacker.addProvince(1);
    attacker.setCapital(1, true, false);
    defender.addProvince(2);
    defender.addProvince(3);
    defender.setCapital(3, true, false);
    alice = fixture.player("Alice");
    bob = fixture.player("Bob");
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (oldRequests != null) {
        requests().clear();
        requests().putAll(oldRequests);
      }
      if (oldCommitments != null) {
        commitments().clear();
        commitments().putAll(oldCommitments);
      }
      if (oldBattles != null) {
        BattleManager.get().clear();
        BattleManager.get().addAll(oldBattles);
      }
      BattleAutoresolveService.setRandomForTests(previousRandom);
      CampaignClock.reset();
      if (previousOffset != null) CampaignClock.add(previousOffset);
      for (var entry : settings.entrySet()) entry.getKey().set(null, entry.getValue());
      if (fixture != null) fixture.close();
    } finally {
      if (files != null) files.close();
    }
  }

  private static Field field(Class<?> owner, String name) throws Exception {
    Field f = owner.getDeclaredField(name);
    f.setAccessible(true);
    return f;
  }

  @SuppressWarnings("unchecked")
  private static Map<Player, Request> requests() throws Exception {
    return (Map<Player, Request>) field(RequestManager.class, "requests").get(null);
  }

  @SuppressWarnings("unchecked")
  private static Map<Integer, List<WarCommitment>> commitments() throws Exception {
    return (Map<Integer, List<WarCommitment>>)
        field(WarCommitmentService.class, "commitmentsByWar").get(null);
  }

  private void setting(String name, Object value) throws Exception {
    Field f = field(Cache.class, name);
    settings.putIfAbsent(f, f.get(null));
    f.set(null, value);
  }

  private void time(Instant instant) {
    CampaignClock.reset();
    CampaignClock.add(Duration.between(Instant.now(), instant));
  }

  private War votingWar(int id) {
    War war = new War(id, attacker, defender);
    war.setGoal(WarGoalType.WAR);
    war.setWarType(WarType.WAR);
    war.setObjectiveProvinceId(3);
    war.setCampaignStartProvinceId(2);
    war.setCampaignProvinces(List.of(1, 2, 3));
    war.setCursorIndex(1);
    war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(2, CampaignBattleKind.FIELD, true, null),
            new ScheduledCampaignBattle(3, CampaignBattleKind.FIELD, true, null)));
    war.setInitiativeAttacker(4);
    war.setInitiativeDefender(4);
    war.setInitiativeHolder(BelligerentRole.ATTACKER);
    war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
    war.setCampaignPhase(CampaignPhase.INVASION);
    war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
    war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
    war.setPostBattleChoiceResolved(true);
    war.setBattleDay(DAY);
    war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    war.setAutoresolveProposedByAttacker(true);
    war.setAutoresolveProposedByDefender(true);
    WarManager.addWar(war);
    WarCommitmentService.commitAllParticipants(war);
    return war;
  }

  @Test
  void anAcceptedCurrentRequestAppliesRealCasualtiesAndPersistsTheBattleResult() {
    War war = votingWar(0);
    assertEquals(
        BattleAutoresolveService.SendResult.SENT,
        BattleAutoresolveService.sendProposeRequest(alice, war, BelligerentRole.ATTACKER));
    assertTrue(RequestManager.hasRequest(bob));

    RequestManager.accept(bob);

    assertFalse(RequestManager.hasRequest(bob));
    assertTrue(war.hasFirstBattleStarted());
    assertEquals(1, war.getCampaignBattlesFought());
    assertEquals(8, attacker.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(3, defender.getMilitary().getRegiment("guard").getCurrentSlots());
    assertFalse(war.isAutoresolveProposedByAttacker());
    assertFalse(war.isAutoresolveProposedByDefender());
    verify(bob).sendMessage("§aBattle vote autoresolve accepted.");
    verify(alice).sendMessage(defender.getName() + " §aaccepted autoresolve");
    assertEquals(1, new Database().loadWars().getFirst().getCampaignBattlesFought());
  }

  @Test
  void acceptingAnOldRequestCannotAutoresolveANewWarThatReusesTheSameId() {
    War original = votingWar(0);
    assertEquals(
        BattleAutoresolveService.SendResult.SENT,
        BattleAutoresolveService.sendProposeRequest(alice, original, BelligerentRole.ATTACKER));
    WarManager.endWar(original);
    War replacement = votingWar(0);
    clearInvocations(alice, bob);

    RequestManager.accept(bob);

    assertAll(
        () -> assertFalse(replacement.hasFirstBattleStarted()),
        () -> assertEquals(0, replacement.getCampaignBattlesFought()),
        () -> assertEquals(10, attacker.getMilitary().getRegiment("guard").getCurrentSlots()),
        () -> assertEquals(6, defender.getMilitary().getRegiment("guard").getCurrentSlots()),
        () -> verify(bob, never()).sendMessage("§aBattle vote autoresolve accepted."));
    assertSame(replacement, WarManager.getById(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "ended", "not_leader", "expired", "no_battle"})
  void staleOrUnavailableRequestsDoNotSpendTroops(String reason) {
    War war = votingWar(0);
    assertEquals(
        BattleAutoresolveService.SendResult.SENT,
        BattleAutoresolveService.sendProposeRequest(alice, war, BelligerentRole.ATTACKER));
    if (reason.equals("missing")) WarManager.get().clear();
    if (reason.equals("ended")) war.end(WarEndReason.ADMIN_END);
    if (reason.equals("not_leader")) {
      defender.getOrCreateMainGuild().addMember("Cara");
      defender.setLeader("Cara");
    }
    if (reason.equals("expired")) time(BattleWindowService.atScheduleHour(DAY, 16));
    if (reason.equals("no_battle")) {
      war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
      war.setPostBattleChoiceResolved(false);
    }

    RequestManager.accept(bob);

    assertFalse(war.hasFirstBattleStarted());
    assertEquals(0, war.getCampaignBattlesFought());
    assertEquals(10, attacker.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(6, defender.getMilitary().getRegiment("guard").getCurrentSlots());
    verify(bob, never()).sendMessage("§aBattle vote autoresolve accepted.");
    assertFalse(RequestManager.hasRequest(bob));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no_war",
        "ended",
        "phase",
        "side",
        "outsider",
        "wrong_side",
        "offline",
        "logged_out"
      })
  void proposalsRequireAValidVotingWarItsLeaderAndAnOnlineOpponent(String reason) {
    War war = votingWar(0);
    if (reason.equals("ended")) war.end(WarEndReason.ADMIN_END);
    if (reason.equals("phase")) war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    Player proposer = reason.equals("outsider") ? fixture.player("Cara") : alice;
    if (reason.equals("offline")) fixture.online.remove("Bob");
    if (reason.equals("logged_out")) when(bob.isOnline()).thenReturn(false);
    var side =
        reason.equals("side")
            ? null
            : reason.equals("wrong_side") ? BelligerentRole.DEFENDER : BelligerentRole.ATTACKER;

    var result =
        BattleAutoresolveService.sendProposeRequest(
            proposer, reason.equals("no_war") ? null : war, side);

    assertEquals(
        reason.equals("offline") || reason.equals("logged_out")
            ? BattleAutoresolveService.SendResult.OPPOSING_LEADER_OFFLINE
            : BattleAutoresolveService.SendResult.NOT_ALLOWED,
        result);
    assertFalse(RequestManager.hasRequest(bob));
    assertEquals(0, war.getCampaignBattlesFought());
    assertEquals(10, attacker.getMilitary().getRegiment("guard").getCurrentSlots());
  }

  @Test
  void theDefenderCanProposeAndTheOriginalSenderMayGoOfflineBeforeAcceptance() {
    War war = votingWar(0);
    assertEquals(
        BattleAutoresolveService.SendResult.SENT,
        BattleAutoresolveService.sendProposeRequest(bob, war, BelligerentRole.DEFENDER));
    when(bob.isOnline()).thenReturn(false);
    clearInvocations(bob);

    RequestManager.accept(alice);

    assertEquals(1, war.getCampaignBattlesFought());
    verify(alice).sendMessage("§aBattle vote autoresolve accepted.");
    verify(bob, never()).sendMessage(anyString());
    assertFalse(RequestManager.hasRequest(alice));
  }

  @Test
  void eligibilityUsesTheActualBattleDayAndTheVoteCloseBoundary() {
    War war = votingWar(0);
    assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(null, CampaignClock.now()));
    assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(war, null));
    assertTrue(
        BattleAutoresolveService.canProposeAutoresolveNow(
            war, BattleWindowService.atScheduleHour(DAY, 15)));
    assertFalse(
        BattleAutoresolveService.canProposeAutoresolveNow(
            war, BattleWindowService.atScheduleHour(DAY, 16)));
    assertFalse(
        BattleAutoresolveService.canProposeAutoresolveNow(
            war, BattleWindowService.atScheduleHour(DAY.plusDays(1), 15)));
    assertFalse(BattleAutoresolveService.resolve(null));
    BattleAutoresolveService.acceptRequest(bob);
    assertEquals(0, war.getCampaignBattlesFought());
    war.end(WarEndReason.ADMIN_END);
    assertFalse(BattleAutoresolveService.resolve(war));
    assertEquals(10, attacker.getMilitary().getRegiment("guard").getCurrentSlots());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aPreparedBattleIsOnlyRemovedWhenAutoresolveActuallyResolvesIt(boolean started) {
    War war = votingWar(0);
    Battle prepared = new Battle("prepared_battle");
    prepared.setWarId(0);
    prepared.setProvinceId(2);
    prepared.setStarted(started);
    BattleManager.addBattle(prepared);
    new Database().saveBattle(prepared);

    assertEquals(!started, BattleAutoresolveService.resolve(war, new Random(7)));

    if (started) {
      assertSame(prepared, BattleManager.getByWarId(0));
      assertTrue(prepared.hasStarted());
      assertEquals(0, war.getCampaignBattlesFought());
      assertEquals(10, attacker.getMilitary().getRegiment("guard").getCurrentSlots());
    } else {
      assertNull(BattleManager.getByWarId(0));
      assertEquals(1, war.getCampaignBattlesFought());
      assertTrue(
          new Database().loadBattles().stream().noneMatch(b -> b.getId().equals(prepared.getId())));
    }
  }

  @Test
  void aMissingDisplayNameUsesTheWinningSideLabelWithoutChangingTheOutcome() {
    War war = votingWar(0);
    attacker.setName(" ");
    BattleAutoresolveService.setRandomForTests(new Random(3));

    assertTrue(BattleAutoresolveService.resolve(war));

    verify(alice).sendMessage(contains("§eAttacker §7won"));
    assertEquals(8, attacker.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(3, defender.getMilitary().getRegiment("guard").getCurrentSlots());
  }

  @Test
  void excessiveConfiguredLossFractionsStillCapCasualtiesAtTheActualTroopCount() {
    var prediction = BattleAutoresolveService.predict(50, 30, 0, Double.MAX_VALUE, new Random(1));
    assertTrue(prediction.offensiveWins());
    assertEquals(10, prediction.offensiveDeaths());
    assertEquals(30, prediction.defensiveDeaths());
    assertEquals(6, prediction.defensiveRegimentLosses());
  }

  @Test
  void negativeLuckAndLossFractionsDoNotProduceNegativeOrRandomCasualties() {
    Random neverUsed =
        new Random() {
          @Override
          public double nextDouble() {
            throw new AssertionError("clamped zero luck must be deterministic");
          }
        };
    var prediction = BattleAutoresolveService.predict(50, 30, -4, -2, neverUsed);
    assertTrue(prediction.offensiveWins());
    assertEquals(10, prediction.offensiveDeaths());
    assertEquals(0, prediction.defensiveDeaths());
    assertEquals(0, prediction.defensiveRegimentLosses());
  }

  @Test
  void luckAboveOneIsClampedAndANullGeneratorUsesTheMidpoint() {
    var clamped = BattleAutoresolveService.predict(50, 30, 5, 0.5, new Random(9));
    var maximum = BattleAutoresolveService.predict(50, 30, 1, 0.5, new Random(9));
    assertEquals(maximum, clamped);
    assertEquals(
        BattleAutoresolveService.predict(50, 30, 0, 0.5, new Random(9)),
        BattleAutoresolveService.predict(50, 30, 0.5, 0.5, null));
    var zero = BattleAutoresolveService.predict(-50, 30, 0.5, 0.5, new Random(9));
    assertFalse(zero.offensiveWins());
    assertEquals(0, zero.offensiveDeaths());
    assertEquals(0, zero.defensiveDeaths());
  }

  @Test
  void nearlyTiedMaximumArmiesCannotOverflowWinnerCasualties() {
    Random upperBound =
        new Random() {
          @Override
          public double nextDouble() {
            return Math.nextDown(1.0);
          }
        };
    var result =
        BattleAutoresolveService.predict(
            Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 1, 1, upperBound);
    assertTrue(result.offensiveWins());
    assertEquals(Integer.MAX_VALUE, result.offensiveDeaths());
    assertEquals(Integer.MAX_VALUE - 1, result.defensiveDeaths());
    assertEquals(429496729, result.offensiveRegimentLosses());
    assertEquals(429496729, result.defensiveRegimentLosses());
    var invalidFraction = BattleAutoresolveService.predict(50, 30, 0, Double.NaN, null);
    assertEquals(0, invalidFraction.defensiveDeaths());
  }

  @Test
  void largeConfiguredLivesCannotTurnCommittedArmiesIntoZeroCasualtyForces() throws Exception {
    setting("warBattleLivesPerRegiment", Integer.MAX_VALUE);
    War war = votingWar(0);

    assertTrue(BattleAutoresolveService.resolve(war, new Random(1)));

    verify(alice).sendMessage(contains("§e" + attacker.getName() + " §7won"));
    assertEquals(0, attacker.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(0, defender.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(1, war.getCampaignBattlesFought());
    assertFalse(war.isAutoresolveProposedByAttacker());
    assertFalse(war.isAutoresolveProposedByDefender());
  }
}
