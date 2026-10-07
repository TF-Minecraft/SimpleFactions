package net.tfminecraft.simplefactions.war.battle.warband;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidRespawnService;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidWinService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.DefenderRespawnMode;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.CapturePointDefinition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class BattleRosterBoundaryCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.remember(WarbandMembershipService.class, "instance");
    WarbandMembershipService.resetForTests();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @ParameterizedTest
  @CsvSource({
    "true,false,true,attacker",
    "false,true,true,draw",
    "true,true,false,draw",
    "true,false,false,attacker",
    "false,false,true,attacker",
    "false,true,false,defender"
  })
  void raidResultDistinguishesSimultaneousOpposingWinsFromTwoAttackerWinConditions(
      boolean targetCaptured, boolean attackersOut, boolean defendersOut, String winner) {
    Battle battle = startedManualRaid();
    BattleSide attacker = battle.getSideById("attacker");
    BattleSide defender = battle.getSideById("defender");
    if (targetCaptured) {
      var target = battle.getPointManager().getPoints().getFirst();
      target.setController(attacker);
      target.setCaptureProgress(100);
    }
    if (attackersOut) RaidAttackerEliminationService.markOut(battle, rig.alice.getUniqueId());
    if (defendersOut) {
      defender.setLives(0);
      when(rig.bob.getLocation()).thenReturn(defender.getJail());
    }
    clearInvocations(Bukkit.getPluginManager());

    RaidWinService.checkRaidWin(battle);

    assertFalse(battle.hasStarted());
    ArgumentCaptor<Event> events = ArgumentCaptor.forClass(Event.class);
    verify(Bukkit.getPluginManager()).callEvent(events.capture());
    BattleEndedEvent result = assertInstanceOf(BattleEndedEvent.class, events.getValue());
    assertEquals(winner.equals("draw") ? null : winner, result.getWinningSideId());
    assertEquals(battle.getId(), result.getBattleId());
    assertEquals(
        Set.of(rig.alice.getUniqueId(), rig.bob.getUniqueId()), result.getParticipantIds());
    assertFalse(RaidAttackerEliminationService.isMarkedOut(battle, rig.alice.getUniqueId()));
  }

  @Test
  void unfinishedAndOrdinaryFieldBattlesCannotBeEndedByTheRaidRule() {
    Battle battle = BattleFactory.createBlank(BattleType.RAID, "not_started");
    BattleManager.addBattle(battle);
    RaidWinService.checkRaidWin(null);
    RaidWinService.checkRaidWin(battle);
    assertFalse(battle.hasStarted());
    assertFalse(RaidWinService.isTargetCaptured(battle));
    battle.setBattleType(BattleType.FIELD);
    RaidWinService.checkRaidWin(battle);
    assertEquals(BattleType.FIELD, battle.getBattleType());
    assertTrue(rig.war.isActive());
  }

  @Test
  void quitAndJoinEventsRestoreAnOpenManualRosterWithoutDuplicatingMembers() {
    Warband band = new Warband("event_rejoin", rig.alice);
    band.setLocked(false);
    WarbandManager.addWarband(band);
    WarbandMembershipListener listener = new WarbandMembershipListener();
    listener.onQuit(new PlayerQuitEvent(rig.alice, "left"));
    assertFalse(band.hasMember(rig.alice));
    assertNotNull(WarbandMembershipService.getInstance().getPendingRejoin(rig.alice.getUniqueId()));
    listener.onJoin(new PlayerJoinEvent(rig.alice, "joined"));
    assertTrue(band.hasMember(rig.alice));
    assertEquals(1, band.getMemberCount());
    assertNull(WarbandMembershipService.getInstance().getPendingRejoin(rig.alice.getUniqueId()));
    listener.onJoin(new PlayerJoinEvent(rig.alice, "joined"));
    assertEquals(1, band.getMemberCount());
  }

  @Test
  void invitationsIncludeOnlyOnlineProfilesAndAreConsumedByJoiningOrExplicitRevocation() {
    Warband band = new Warband("invitations", rig.alice);
    var offline = rig.player("Offline");
    when(offline.isOnline()).thenReturn(false);
    var disappeared = rig.player("Disappeared");
    band.invite(rig.bob);
    band.invite(offline);
    band.invite(disappeared);
    when(Bukkit.getPlayer(disappeared.getUniqueId())).thenReturn(null);
    assertEquals(List.of(rig.bob), band.getInvited());
    band.addPlayer(rig.bob);
    assertFalse(band.isInvited(rig.bob));
    band.uninvite(offline);
    band.uninvite(null);
    assertEquals(Set.of(disappeared.getUniqueId()), band.getInvitedIds());
    band.removePlayer(null);
    assertEquals(2, band.getRealMemberCount());
  }

  @Test
  void dummyCleanupRestoresTheOldestRealLeaderAndDoesNotEraseRealMembership() {
    Warband band =
        Warband.createCampaignSideShell(
            "dummy_cleanup", rig.war, rig.war.getAttackers(), "attacker");
    UUID dummy =
        UUID.nameUUIDFromBytes("dummy-one".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    band.addMember(rig.alice.getUniqueId());
    band.addMember(rig.bob.getUniqueId());
    band.addDummyMembers(null, null);
    band.addDummyMembers(List.of(), Map.of());
    band.addDummyMembers(
        java.util.Arrays.asList(null, band.getLeaderId(), dummy, rig.alice.getUniqueId()),
        Map.of(dummy, "Phantom"));
    assertEquals(1, band.getDummyMemberCount());
    assertEquals(2, band.getRealMemberCount());
    assertEquals(rig.bob.getUniqueId(), band.getOldestRealMemberId(rig.alice.getUniqueId()));
    assertEquals("Phantom", band.getMemberDisplayName(dummy));
    band.setLeaderId(dummy);
    band.clearDummyMembers();
    assertEquals(rig.alice.getUniqueId(), band.getLeaderId());
    assertEquals(
        Set.of(rig.alice.getUniqueId(), rig.bob.getUniqueId()), Set.copyOf(band.getMemberIds()));
    band.clearDummyMembers();
    assertEquals(2, band.getMemberCount());
  }

  @Test
  void clearingTheLastDummyRestoresAnEmptyCampaignShellToPendingSignup() {
    Warband band =
        Warband.createCampaignSideShell(
            "pending_shell", rig.war, rig.war.getAttackers(), "attacker");
    UUID phantom = UUID.randomUUID();
    band.addDummyMembers(List.of(phantom), Map.of(phantom, " "));
    band.setLeaderId(phantom);
    assertEquals("Unknown", band.getLeaderDisplayName());
    assertNull(band.getOldestRealMemberId(null));
    band.clearDummyMembers();
    assertTrue(band.isPendingLeader());
    assertEquals("Pending signup", band.getLeaderDisplayName());
    assertEquals(0, band.getMemberCount());
    assertNull(band.getOldestRealMemberId(null));
    assertEquals(List.of(), band.getMemberDisplayNamesForLore(5));
  }

  @Test
  void memberDisplayNamesUseOnlineThenOfflineProfilesAndRespectTheLoreLimit() {
    UUID archived = UUID.randomUUID(), unknown = UUID.randomUUID();
    OfflinePlayer profile = mock(OfflinePlayer.class), blank = mock(OfflinePlayer.class);
    when(profile.getName()).thenReturn("Archived");
    when(blank.getName()).thenReturn(" ");
    when(Bukkit.getOfflinePlayer(archived)).thenReturn(profile);
    when(Bukkit.getOfflinePlayer(unknown)).thenReturn(blank);
    Warband band = new Warband("names", rig.alice);
    band.addMember(archived);
    band.addMember(unknown);
    assertEquals("Unknown", band.getMemberDisplayName(null));
    assertEquals("Archived", band.getMemberDisplayName(archived));
    assertEquals("Unknown", band.getMemberDisplayName(unknown));
    assertEquals(List.of("Alice (leader)", "Archived"), band.getMemberDisplayNamesForLore(2));
    assertEquals(List.of(), band.getMemberDisplayNamesForLore(0));
    assertEquals(List.of(rig.alice), band.getOnlineMembers());
    assertEquals(1, band.getOnlineMemberCount());
    assertSame(rig.alice, band.getLeader());
  }

  private Battle startedManualRaid() {
    Battle battle = BattleFactory.createBlank(BattleType.RAID, "boundary_manual_raid");
    battle.setDefenderRespawnMode(DefenderRespawnMode.LIVES);
    battle.setDefenderLives(2);
    battle.setRaidTarget(
        new CapturePointDefinition("keep", new BattleLocation("world", 50, 64, 50, 0, 0)));
    battle.getSideById("attacker").addBand(new Warband("raiders", rig.alice));
    battle.getSideById("defender").addBand(new Warband("guards", rig.bob));
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 30, 64, 30));
      side.setJail(new Location(rig.domain.ui.world, 200, 64, 200));
    }
    when(rig.alice.getWorld()).thenReturn(rig.domain.ui.world);
    when(rig.bob.getWorld()).thenReturn(rig.domain.ui.world);
    when(rig.alice.getLocation()).thenReturn(new Location(rig.domain.ui.world, 30, 64, 30));
    when(rig.bob.getLocation()).thenReturn(new Location(rig.domain.ui.world, 30, 64, 30));
    BattleManager.addBattle(battle);
    assertNull(battle.start());
    assertTrue(battle.hasStarted());
    return battle;
  }

  @Test
  void raidRespawnsMarkAttackersOutButKeepFiniteDefenderDeathsForTheCollectiveRouter() {
    Battle battle = startedManualRaid();
    BattleSide attacker = battle.getSideById("attacker");
    BattleSide defender = battle.getSideById("defender");
    clearInvocations(rig.alice, rig.bob);
    assertFalse(RaidRespawnService.applyRespawn(null, rig.alice, attacker));
    assertFalse(RaidRespawnService.applyRespawn(battle, null, attacker));
    assertFalse(RaidRespawnService.applyRespawn(battle, rig.alice, null));
    assertFalse(RaidRespawnService.applyRespawn(battle, rig.bob, defender));
    verify(rig.bob, never()).teleport(any(Location.class));
    assertEquals(2, defender.getLives());
    battle.setDefenderRespawnMode(DefenderRespawnMode.INFINITE);
    assertTrue(RaidRespawnService.applyRespawn(battle, rig.bob, defender));
    verify(rig.bob).teleport(defender.getSpawn());
    assertFalse(RaidAttackerEliminationService.isMarkedOut(battle, rig.bob.getUniqueId()));
    attacker.setJail(null);
    assertTrue(RaidRespawnService.applyRespawn(battle, rig.alice, attacker));
    assertTrue(RaidAttackerEliminationService.isMarkedOut(battle, rig.alice.getUniqueId()));
    verify(rig.alice).teleport(attacker.getSpawn());
    assertTrue(battle.hasStarted());
    Battle field = BattleFactory.createBlank(BattleType.FIELD, "ordinary_respawn");
    assertFalse(RaidRespawnService.applyRespawn(field, rig.alice, field.getSideById("attacker")));
  }

  @Test
  void campaignLifePreviewsUseRealRegimentsAndUniqueRosterMembersThenApplyTheSamePool() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "life_preview");
    BattleManager.addBattle(battle);
    battle.setWarId(rig.war.getId());
    battle.setProvinceId(11);
    regiment(rig.attacker, "assault");
    regiment(rig.defender, "defence");
    Warband first = new Warband("first_life_roster", rig.alice);
    first.addPlayer(rig.bob);
    Warband second = new Warband("second_life_roster", rig.bob);
    BattleSide side = battle.getSideById("attacker");
    side.addBand(first);
    side.addBand(second);
    var preview =
        net.tfminecraft.simplefactions.war.battle.military.BattleLivesService
            .previewCampaignSideLives(rig.war, battle, "attacker");
    assertEquals(2, preview.committedRegiments());
    assertEquals(8, preview.poolLives());
    assertEquals(2, preview.rosterFighters());
    assertEquals(6, preview.sideLives());
    assertEquals(0, preview.mercenarySlots());
    net.tfminecraft.simplefactions.war.battle.military.BattleLivesService.applyCampaignLives(
        battle);
    assertEquals(preview.sideLives(), side.getLives());
    assertEquals(8, battle.getSideById("defender").getLives());
    side.getBands().add(null);
    assertEquals(
        2,
        net.tfminecraft.simplefactions.war.battle.military.BattleLivesService.countRosterFighters(
            side));
    side.getBands().remove(null);
    assertEquals(3, first.getMemberCount() + second.getMemberCount());
  }

  @Test
  void incompleteCampaignLifeContextsLeaveExistingPoolsAloneAndEmptyArmiesReceiveNoLives() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "incomplete_lives");
    BattleManager.addBattle(battle);
    battle.setWarId(rig.war.getId());
    BattleSide attacker = battle.getSideById("attacker");
    attacker.setLives(9);
    net.tfminecraft.simplefactions.war.battle.military.BattleLivesService.applyCampaignLives(
        battle);
    assertEquals(9, attacker.getLives());
    var empty =
        new net.tfminecraft.simplefactions.war.battle.military.BattleLivesService.SideLivesPreview(
            0, 0, 0, 0, 0);
    assertEquals(
        empty,
        net.tfminecraft.simplefactions.war.battle.military.BattleLivesService
            .previewCampaignSideLives(null, battle, "attacker"));
    assertEquals(
        empty,
        net.tfminecraft.simplefactions.war.battle.military.BattleLivesService
            .previewCampaignSideLives(rig.war, null, "attacker"));
    assertEquals(
        empty,
        net.tfminecraft.simplefactions.war.battle.military.BattleLivesService
            .previewCampaignSideLives(rig.war, battle, null));
    battle.setProvinceId(11);
    assertEquals(
        empty,
        net.tfminecraft.simplefactions.war.battle.military.BattleLivesService
            .previewCampaignSideLives(rig.war, battle, "missing_side"));
    battle.getSides().remove(attacker);
    net.tfminecraft.simplefactions.war.battle.military.BattleLivesService.applyCampaignLives(
        battle);
    assertEquals(0, battle.getSideById("defender").getLives());
    assertEquals(9, attacker.getLives());
    assertTrue(rig.war.isActive());
  }

  @Test
  void restoredSideLivesCorrectAnOldLowerMaximumAndKeepTheBarWithinBounds() {
    BattleSide side =
        new BattleSide(
            "restored", net.tfminecraft.simplefactions.war.battle.enums.LifeType.COLLECTIVE, 1);
    side.setLt(net.tfminecraft.simplefactions.war.battle.enums.LifeType.fromJson("PER_PLAYER"));
    assertEquals(net.tfminecraft.simplefactions.war.battle.enums.LifeType.COLLECTIVE, side.getLt());
    side.restoreLives(8, 3);
    assertEquals(8, side.getLives());
    assertEquals(8, side.getMaxLives());
    org.bukkit.boss.BossBar bar = rig.bars.getLast();
    assertEquals(1.0, bar.getProgress());
    assertEquals("§frestored: §e8", bar.getTitle());
    side.restoreLives(-5, 0);
    assertEquals(0, side.getLives());
    assertEquals(1, side.getMaxLives());
    assertEquals(0.0, bar.getProgress());
  }

  @Test
  void persistedLifeAndLootNamesRetainTheirLegacyCompatibilityAndRejectUnknownValues() {
    var collective = net.tfminecraft.simplefactions.war.battle.enums.LifeType.COLLECTIVE;
    assertEquals(
        collective,
        net.tfminecraft.simplefactions.war.battle.enums.LifeType.fromJson(collective.toJson()));
    assertEquals(
        collective,
        net.tfminecraft.simplefactions.war.battle.enums.LifeType.fromJson("per_player"));
    assertNull(net.tfminecraft.simplefactions.war.battle.enums.LifeType.fromJson(null));
    assertNull(net.tfminecraft.simplefactions.war.battle.enums.LifeType.fromJson(" "));
    assertNull(net.tfminecraft.simplefactions.war.battle.enums.LifeType.fromJson("unrecognised"));
    for (var mode : net.tfminecraft.simplefactions.war.battle.enums.BattleLootMode.values())
      assertEquals(
          mode,
          net.tfminecraft.simplefactions.war.battle.enums.BattleLootMode.fromJson(mode.toJson()));
    assertNull(net.tfminecraft.simplefactions.war.battle.enums.BattleLootMode.fromJson(null));
    assertNull(net.tfminecraft.simplefactions.war.battle.enums.BattleLootMode.fromJson(" "));
    assertNull(
        net.tfminecraft.simplefactions.war.battle.enums.BattleLootMode.fromJson("unrecognised"));
  }

  private void regiment(net.tfminecraft.simplefactions.objects.Faction faction, String id) {
    org.bukkit.configuration.file.YamlConfiguration yaml =
        new org.bukkit.configuration.file.YamlConfiguration();
    yaml.set("item.material", "PAPER");
    yaml.set("offense", true);
    var regiment = new net.tfminecraft.simplefactions.army.Regiment(id, yaml);
    faction.getMilitary().getRegiments().add(regiment);
    assertTrue(faction.getMilitary().adminAdjustSlots(id, 2).allowed());
  }
}
