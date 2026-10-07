package net.tfminecraft.simplefactions.war.campaign.raid.fight;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.ui.BattleCommandManager;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.LaunchResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RaidReviewRegressionTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
  }

  @AfterEach
  void close() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void beginningMusterReservesBothSideIdsBeforeAnyoneSignsUp() {
    CampaignRaid raid = muster();

    assertReservedShells(raid);
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
    assertNull(raid.getBattleId());
    assertTrue(BattleManager.get().isEmpty());
    assertFalse(CampaignRaidService.isSideQuotaUsed(rig.war, CampaignCoalition.AGGRESSOR));
    assertTrue(
        CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(
            CampaignRaidWarbandService.getDefenderWarband(raid), rig.bob));
    assertFalse(
        CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(
            CampaignRaidWarbandService.getAttackerWarband(raid), rig.alice));
  }

  @Test
  void resumingASavedMusterRebuildsBothReservationsIdempotently() {
    muster();
    War restored = restoreWithoutRuntimeWarbands(WarMapper.toData(rig.war));

    CampaignRaidResumeService.resumeAll();

    CampaignRaid raid = CampaignRaidService.getActive(restored);
    assertReservedShells(raid);
    List<Warband> originalShells = List.copyOf(WarbandManager.get());
    CampaignRaidResumeService.resumeAll();
    assertEquals(originalShells, WarbandManager.get());
    assertTrue(BattleManager.get().isEmpty());
    assertEquals(CampaignRaidState.MUSTER, raid.getState());
  }

  @ParameterizedTest
  @CsvSource({"attacker,false", "attacker,true", "defender,false", "defender,true"})
  void playerCreateCannotTakeAnActiveRaidsReservedIdBeforeRuntimeRestoration(
      String side, boolean upperCase) {
    muster();
    War restored = restoreWithoutRuntimeWarbands(WarMapper.toData(rig.war));
    CampaignRaid raid = CampaignRaidService.getActive(restored);
    String id = raid.getId() + "_" + side;
    if (upperCase) id = id.toUpperCase(Locale.ROOT);
    Player outsider = rig.player("PrivateOwner");
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("warband");
    int writes = rig.writes.size();

    assertTrue(
        new BattleCommandManager()
            .onCommand(outsider, command, "warband", new String[] {"create", id}));

    assertNull(
        WarbandManager.getByString(id), "The active raid reserves this ID before its shells load");
    assertNull(WarbandManager.getByPlayer(outsider));
    assertEquals(writes, rig.writes.size());
    assertSame(raid, CampaignRaidService.getActive(restored));
    verify(outsider, atLeastOnce()).sendMessage(contains("reserved"));
  }

  @Test
  void ordinaryPlayerWarbandIdsRemainAvailableBesideAnActiveMuster() {
    CampaignRaid raid = muster();
    Player outsider = rig.player("PrivateOwner");
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("warband");

    assertTrue(
        new BattleCommandManager()
            .onCommand(outsider, command, "warband", new String[] {"create", "private_patrol"}));

    Warband manual = WarbandManager.getByString("private_patrol");
    assertNotNull(manual);
    assertFalse(manual.isFaction());
    assertEquals(outsider.getUniqueId(), manual.getLeaderId());
    assertTrue(manual.hasMember(outsider.getUniqueId()));
    assertSame(raid, CampaignRaidService.getActive(rig.war));
  }

  @ParameterizedTest
  @ValueSource(strings = {"attacker", "defender"})
  void naturalBattleEndPersistsTheClearedRaidSoReloadCannotResumeIt(String winner) {
    CampaignRaid raid = muster();
    CampaignRaidWarbandService.signupAttacker(rig.war, raid, rig.alice.getUniqueId(), "Alice");
    rig.time(raid.getMusterEndsAt());
    CampaignRaidLaunchService.startFight(rig.war, rig.now());
    Battle battle = BattleManager.getByString(raid.getBattleId());
    assertNotNull(battle);
    assertTrue(battle.hasStarted());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
    assertNotNull(lastSavedWar().activeCampaignRaid);
    var usedQuota = new java.util.LinkedHashMap<>(rig.war.getCampaignRaidsUsed());
    var repairLocks = new java.util.LinkedHashMap<>(rig.war.getRaidRepairLockUntil());
    long previousWrites = rig.warWrites();
    CampaignRaidBattleEndService listener = new CampaignRaidBattleEndService();
    var callbacks = Bukkit.getPluginManager();
    doAnswer(
            call -> {
              if (call.getArgument(0) instanceof BattleEndedEvent event)
                listener.onBattleEnded(event);
              return null;
            })
        .when(callbacks)
        .callEvent(any(Event.class));

    BattleEndSupport.endBattle(battle, winner, BattleEndReason.SIDE_WIN);

    assertNull(CampaignRaidService.getActive(rig.war));
    assertFalse(battle.hasStarted());
    assertNull(BattleManager.getByString(battle.getId()));
    assertTrue(WarbandManager.get().isEmpty());
    assertEquals(
        previousWrites + 1,
        rig.warWrites(),
        "The cleanup must replace the persisted fighting raid");
    WarData saved = lastSavedWar();
    assertNull(saved.activeCampaignRaid);
    War restored = WarMapper.fromData(saved);
    assertNotNull(restored);
    assertTrue(restored.isActive());
    assertNull(CampaignRaidService.getActive(restored));
    assertEquals(usedQuota, restored.getCampaignRaidsUsed());
    assertEquals(repairLocks, restored.getRaidRepairLockUntil());
    WarManager.get().clear();
    WarManager.get().add(restored);
    CampaignRaidResumeService.resumeAll();
    assertTrue(BattleManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
    assertNull(CampaignRaidService.getActive(restored));
  }

  private CampaignRaid muster() {
    assertEquals(
        LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    return CampaignRaidService.getActive(rig.war);
  }

  private War restoreWithoutRuntimeWarbands(WarData data) {
    War restored = WarMapper.fromData(data);
    assertNotNull(restored);
    WarbandManager.get().clear();
    WarManager.get().clear();
    WarManager.get().add(restored);
    return restored;
  }

  private void assertReservedShells(CampaignRaid raid) {
    Warband attackers = CampaignRaidWarbandService.getAttackerWarband(raid);
    Warband defenders = CampaignRaidWarbandService.getDefenderWarband(raid);
    assertNotNull(attackers);
    assertNotNull(defenders, "The defender ID must be reserved throughout the muster");
    assertEquals(List.of(attackers, defenders), WarbandManager.get());
    for (Warband shell : WarbandManager.get()) {
      assertTrue(shell.isFaction());
      assertTrue(shell.isPendingLeader());
      assertTrue(shell.getMemberIds().isEmpty());
    }
    assertEquals("attacker", attackers.getCampaignSideId());
    assertEquals("defender", defenders.getCampaignSideId());
  }

  private WarData lastSavedWar() {
    return rig.writes.stream()
        .map(Fixture.Write::value)
        .filter(WarData.class::isInstance)
        .map(WarData.class::cast)
        .reduce((first, last) -> last)
        .orElseThrow();
  }
}
