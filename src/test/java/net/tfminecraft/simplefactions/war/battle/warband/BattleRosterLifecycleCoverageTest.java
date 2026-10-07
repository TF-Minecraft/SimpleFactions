package net.tfminecraft.simplefactions.war.battle.warband;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.BattleWarbandRetreatService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.BattleWarbandRetreatService.RetreatResult;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.engine.win.SiegeContestService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.ContestArea;
import net.tfminecraft.simplefactions.war.campaign.raid.*;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidLaunchService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class BattleRosterLifecycleCoverageTest {
  @TempDir Path savedFiles;
  private Fixture rig;
  private WarbandMembershipService membership;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    rig.domain.inventory.confirming = new HashMap<>();
    YamlConfiguration soldiers = new YamlConfiguration();
    soldiers.set("item.material", "PAPER");
    soldiers.set("default-slots", 1);
    soldiers.set("offense", true);
    rig.attacker.getMilitary().getRegiments().add(new Regiment("guard", soldiers));
    rig.defender.getMilitary().getRegiments().add(new Regiment("guard", soldiers));
    rig.remember(WarbandMembershipService.class, "instance");
    rig.remember(Cache.class, "battleRetreatMinElapsedSeconds");
    Cache.battleRetreatMinElapsedSeconds = 120;
    WarbandMembershipService.resetForTests();
    membership = WarbandMembershipService.getInstance();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void quitDetachesThePlayerAndJoiningRestoresTheSameOpenRosterAndBossBar() {
    Warband band = openBand("roster", rig.alice);
    Battle battle = battle("roster_battle", band, false);
    assertNull(battle.start());
    battle.getSideById("attacker").addBossBarPlayer(rig.alice);
    BattleManager.currentBattle.put(rig.alice, battle);
    BossBar life = rig.bars.getFirst();
    assertTrue(life.getPlayers().contains(rig.alice));

    membership.handleQuit(rig.alice.getUniqueId());

    assertFalse(band.hasMember(rig.alice));
    assertFalse(BattleManager.currentBattle.containsKey(rig.alice));
    assertFalse(life.getPlayers().contains(rig.alice));
    assertEquals(band.getId(), membership.getPendingRejoin(rig.alice.getUniqueId()).getWarbandId());
    assertFalse(membership.getPendingRejoin(rig.alice.getUniqueId()).hasFaction());
    assertTrue(membership.handleJoin(rig.alice));
    assertTrue(band.hasMember(rig.alice));
    assertTrue(life.getPlayers().contains(rig.alice));
    assertNull(membership.getPendingRejoin(rig.alice.getUniqueId()));
    assertFalse(membership.handleJoin(rig.alice));
  }

  @Test
  void aDeletedWarbandCannotTransferItsPendingMemberIntoAnUnrelatedReplacement() {
    Warband original = openBand("reused_name", rig.alice);
    membership.handleQuit(rig.alice.getUniqueId());
    WarbandManager.deleteWarband(original);
    Warband replacement = openBand("reused_name", rig.bob);

    assertFalse(membership.handleJoin(rig.alice));
    assertEquals(Set.of(rig.bob.getUniqueId()), new HashSet<>(replacement.getMemberIds()));
    assertNull(membership.getPendingRejoin(rig.alice.getUniqueId()));
  }

  @Test
  void lockedRejoinRequiresAStillValidInvitation() {
    Warband band = openBand("invited", rig.bob);
    band.addPlayer(rig.alice);
    band.invite(rig.alice);
    band.setLocked(true);
    membership.handleQuit(rig.alice.getUniqueId());
    assertTrue(membership.handleJoin(rig.alice));
    assertTrue(band.hasMember(rig.alice));

    membership.handleQuit(rig.alice.getUniqueId());
    band.uninvite(rig.alice);
    assertFalse(membership.handleJoin(rig.alice));
    assertFalse(band.hasMember(rig.alice));
    assertTrue(band.hasMember(rig.bob));
  }

  @Test
  void deletedOrExplicitlyClearedRejoinStateDoesNotCreateMembership() {
    assertDoesNotThrow(() -> membership.handleQuit(UUID.randomUUID()));
    Warband band = openBand("deleted", rig.alice);
    membership.handleQuit(rig.alice.getUniqueId());
    WarbandManager.deleteWarband(band);
    assertFalse(membership.handleJoin(rig.alice));
    Warband another = openBand("cleared", rig.alice);
    membership.handleQuit(rig.alice.getUniqueId());
    membership.clearPendingRejoin(rig.alice.getUniqueId());
    assertFalse(membership.handleJoin(rig.alice));
    assertFalse(another.hasMember(rig.alice));
  }

  @Test
  void membershipSingletonStartsEmptyAndResetDiscardsOnlyItsPendingSessions() throws Exception {
    var instance = WarbandMembershipService.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(null, null);
    WarbandMembershipService fresh = WarbandMembershipService.getInstance();
    assertSame(fresh, WarbandMembershipService.getInstance());
    Warband band = openBand("cold_membership", rig.alice);
    fresh.handleQuit(rig.alice.getUniqueId());
    assertNotNull(fresh.getPendingRejoin(rig.alice.getUniqueId()));
    WarbandMembershipService.resetForTests();
    assertNotSame(fresh, WarbandMembershipService.getInstance());
    assertNull(WarbandMembershipService.getInstance().getPendingRejoin(rig.alice.getUniqueId()));
    assertFalse(band.hasMember(rig.alice));
    assertTrue(fresh.handleJoin(rig.alice));
    assertTrue(band.hasMember(rig.alice));
  }

  @Test
  void quittingAnUnassignedWarbandLeavesOtherBattleRostersAndBarsUntouched() {
    Warband other = openBand("other_attacker", rig.player("OtherAttacker"));
    Battle otherBattle = battle("other_roster_battle", other, false);
    assertNull(otherBattle.start());
    Warband unassigned = openBand("not_in_battle", rig.alice);
    rig.bars.forEach(bar -> clearInvocations(bar));

    membership.handleQuit(rig.alice.getUniqueId());

    assertFalse(unassigned.hasMember(rig.alice));
    assertNotNull(membership.getPendingRejoin(rig.alice.getUniqueId()));
    assertTrue(otherBattle.hasStarted());
    assertTrue(other.hasMember(other.getLeaderId()));
    for (BossBar bar : rig.bars) verify(bar, never()).removePlayer(rig.alice);
    assertTrue(membership.handleJoin(rig.alice));
    assertNull(BattleManager.getBattleByMemberId(rig.alice.getUniqueId()));
  }

  @Test
  void retreatCooldownAndConfirmationRecheckLiveEligibility() {
    Warband band = openBand("retreat", rig.alice);
    Battle battle = battle("retreat_battle", band, true);
    assertNull(battle.start());
    Instant started = battle.getStartedAt();
    assertEquals(
        RetreatResult.REJECTED_TOO_EARLY,
        BattleWarbandRetreatService.retreat(rig.alice, started.plusSeconds(1)));
    assertTrue(battle.hasStarted());
    assertEquals(
        "§cYou cannot retreat for another 2 minutes.",
        BattleWarbandRetreatService.Messages.messageForResult(
            RetreatResult.REJECTED_TOO_EARLY, rig.alice, started.plusSeconds(1)));
    assertEquals(
        "§cYou cannot retreat for another minute.",
        BattleWarbandRetreatService.Messages.messageForResult(
            RetreatResult.REJECTED_TOO_EARLY, rig.alice, started.plusSeconds(60)));
    assertTrue(BattleWarbandRetreatService.canRetreat(rig.alice, started.plusSeconds(120)));
    BattleWarbandRetreatService.ConfirmHandler.handleConfirm(rig.alice, false);
    verify(rig.alice).closeInventory();
    assertTrue(battle.hasStarted());
    battle.setStartedAt(Instant.now().minusSeconds(121));
    BattleWarbandRetreatService.ConfirmHandler.handleConfirm(rig.alice, true);
    assertFalse(battle.hasStarted());
    verify(rig.alice).sendMessage(BattleWarbandRetreatService.Messages.SUCCESS);
    assertEquals(1, band.getRealMemberCount());
    ArgumentCaptor<BattleEndedEvent> ended = ArgumentCaptor.forClass(BattleEndedEvent.class);
    verify(Bukkit.getPluginManager()).callEvent(ended.capture());
    assertEquals(BattleEndReason.RETREAT, ended.getValue().getEndReason());
    assertEquals("defender", ended.getValue().getWinningSideId());
    assertEquals(
        Set.of(rig.alice.getUniqueId(), rig.bob.getUniqueId()),
        ended.getValue().getParticipantIds());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void campaignMembersRejoinTheSameFactionBeforeAndDuringBattle(boolean started) {
    Warband band = factionBand("campaign_members");
    Battle battle = battle("campaign_members_battle", band, true);
    if (started) assertNull(battle.start());
    membership.handleQuit(rig.alice.getUniqueId());
    assertEquals(
        rig.attacker.getId(), membership.getPendingRejoin(rig.alice.getUniqueId()).getFactionId());
    assertTrue(membership.handleJoin(rig.alice));
    assertTrue(band.hasMember(rig.alice));
  }

  @Test
  void pendingFactionRosterRejectsAFullSideAndADeletedFaction() {
    Warband band = factionBand("limited");
    Battle battle = battle("limited_battle", band, true);
    membership.handleQuit(rig.alice.getUniqueId());
    for (int i = 0; i < 4; i++) band.addMember(UUID.randomUUID());
    assertFalse(membership.handleJoin(rig.alice));
    assertFalse(band.hasMember(rig.alice));
    band.addPlayer(rig.alice);
    membership.handleQuit(rig.alice.getUniqueId());
    FactionManager.factions.remove(rig.attacker);
    assertFalse(membership.handleJoin(rig.alice));
    assertFalse(band.hasMember(rig.alice));
    assertFalse(battle.hasStarted());
  }

  @Test
  void factionRejoinUsesSavedOfflineNameWhenTheOnlineLookupHasGoneAway() {
    Warband band = factionBand("offline_name");
    Battle battle = battle("offline_name_battle", band, true);
    assertNull(battle.start());
    membership.handleQuit(rig.alice.getUniqueId());
    OfflinePlayer profile = mock(OfflinePlayer.class);
    when(profile.getName()).thenReturn("Alice");
    when(Bukkit.getOfflinePlayer(rig.alice.getUniqueId())).thenReturn(profile);
    when(Bukkit.getPlayer(rig.alice.getUniqueId())).thenReturn(null);
    assertTrue(membership.attemptRejoin(rig.alice.getUniqueId(), "Alice", null));
    assertTrue(band.hasMember(rig.alice));
    band.removePlayer(rig.alice);
    assertFalse(
        membership.evaluateRejoin(
            band,
            rig.alice.getUniqueId(),
            rig.defender,
            new WarbandRejoinState(band.getId(), rig.attacker)));
  }

  @Test
  void factionMembershipChangesAndAnUnavailableOfflineNameRefuseRejoin() {
    Warband band = factionBand("changed_membership");
    Player member = rig.player("Member");
    rig.attacker.addMember("Member");
    band.addPlayer(member);
    membership.handleQuit(member.getUniqueId());
    rig.attacker.forceRemoveMember("Member");
    assertFalse(membership.handleJoin(member));
    assertFalse(band.hasMember(member));
    assertTrue(band.hasMember(rig.alice));
    membership.handleQuit(rig.alice.getUniqueId());
    assertTrue(
        membership.handleJoin(rig.alice), "An unchanged faction can rejoin a standalone band");
    Battle battle = battle("unknown_profile_battle", band, true);
    assertNull(battle.start());
    membership.handleQuit(rig.alice.getUniqueId());
    when(Bukkit.getPlayer(rig.alice.getUniqueId())).thenReturn(null);
    when(Bukkit.getOfflinePlayer(rig.alice.getUniqueId())).thenReturn(mock(OfflinePlayer.class));
    assertFalse(membership.attemptRejoin(rig.alice.getUniqueId(), "Alice", null));
    assertFalse(band.hasMember(rig.alice));
  }

  @Test
  void quittingWithoutAnOnlineProfileDoesNotInventARejoinFaction() {
    Warband band = factionBand("offline_quit");
    rig.domain.online.remove("Alice");
    membership.handleQuit(rig.alice.getUniqueId());
    assertFalse(membership.getPendingRejoin(rig.alice.getUniqueId()).hasFaction());
    rig.domain.online.put("Alice", rig.alice);
    assertFalse(membership.handleJoin(rig.alice));
    assertFalse(band.hasMember(rig.alice));
  }

  @Test
  void regularCampaignVehicleRestrictionTracksTheActualBattleStart() {
    Warband band = factionBand("vehicle_campaign");
    assertFalse(WarbandVehicleRules.blocksVehicleEntryForWarband(band));
    Battle battle = battle("vehicle_campaign_battle", band, true);
    assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(band));
    assertNull(battle.start());
    assertFalse(WarbandVehicleRules.blocksVehicleEntryForWarband(band));
    assertFalse(
        WarbandVehicleRules.blocksVehicleEntryForWarband(
            openBand("ordinary_band", rig.player("Other"))));
  }

  @Test
  void optionalVehicleFrameworkFailureFallsBackToTheBukkitMountState() {
    assertFalse(WarbandVehicleRules.isMountedOnVehicle(null));
    assertFalse(WarbandVehicleRules.blocksVehicleEntry(null));
    assertFalse(WarbandVehicleRules.isCampaignAutoWarband(null));
    when(Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")).thenReturn(true);
    try (MockedStatic<VehicleFramework> framework = mockStatic(VehicleFramework.class)) {
      VehicleManager vehicles = mock(VehicleManager.class);
      framework.when(VehicleFramework::getVehicleManager).thenReturn(vehicles);
      when(vehicles.getByPassenger(rig.alice)).thenReturn(mock(ActiveVehicle.class));
      assertEquals(
          WarbandVehicleRules.JOIN_BLOCKED_MOUNTED,
          WarbandVehicleRules.joinBlockedReason(rig.alice));
      when(vehicles.getByPassenger(rig.alice)).thenReturn(null);
      assertFalse(WarbandVehicleRules.isMountedOnVehicle(rig.alice));
      when(vehicles.getByPassenger(rig.alice))
          .thenThrow(new IllegalStateException("provider unloading"));
      when(rig.alice.isInsideVehicle()).thenReturn(true);
      assertEquals(
          WarbandVehicleRules.JOIN_BLOCKED_MOUNTED,
          WarbandVehicleRules.joinBlockedReason(rig.alice));
      when(rig.alice.isInsideVehicle()).thenReturn(false);
      assertNull(WarbandVehicleRules.joinBlockedReason(rig.alice));
    }
  }

  @Test
  void raidMusterBlocksVehicleEntryAndTheStartedFightReleasesIt() {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    Warband band = CampaignRaidWarbandService.getAttackerWarband(raid);
    assertEquals(
        CampaignRaidResults.JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war, rig.alice.getUniqueId(), "Alice", rig.attacker, raid.getId(), rig.now()));
    assertTrue(WarbandVehicleRules.isCampaignAutoWarband(band));
    assertTrue(WarbandVehicleRules.blocksVehicleEntry(rig.alice));
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    assertEquals(CampaignRaidState.FIGHTING, raid.getState());
    assertFalse(WarbandVehicleRules.blocksVehicleEntryForWarband(band));
    Battle fighting = BattleManager.getByString(raid.getBattleId());
    Warband defenders = CampaignRaidWarbandService.getDefenderWarband(raid);
    assertTrue(defenders.hasMember(rig.bob));
    membership.handleQuit(rig.bob.getUniqueId());
    rig.bars.forEach(bar -> clearInvocations(bar));
    assertTrue(membership.handleJoin(rig.bob));
    assertTrue(defenders.hasMember(rig.bob));
    for (BossBar bar : rig.bars) verify(bar, never()).addPlayer(rig.bob);
    fighting.end();
    assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(band));
    BattleManager.get().remove(fighting);
    assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(band));
  }

  @Test
  void aManualBattleReusingTheRaidIdCannotUnlockVehiclesForTheRaidRoster() {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    Warband band = CampaignRaidWarbandService.getAttackerWarband(raid);
    band.addPlayer(rig.alice);
    CampaignRaidLaunchService.startFight(rig.war, raid.getMusterEndsAt());
    Battle original = BattleManager.getByString(raid.getBattleId());
    original.end();
    BattleManager.get().remove(original);
    Battle replacement = BattleFactory.createBlank(BattleType.FIELD, original.getId());
    replacement
        .getSideById("attacker")
        .addBand(openBand("manual_attackers", rig.player("ManualA")));
    replacement
        .getSideById("defender")
        .addBand(openBand("manual_defenders", rig.player("ManualD")));
    BattleManager.addBattle(replacement);
    assertNull(replacement.start());
    assertTrue(replacement.hasStarted());

    assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(band));

    assertTrue(replacement.hasStarted());
    assertNull(replacement.getWarId());
    assertSame(raid, rig.war.getActiveCampaignRaid());
  }

  @Test
  void aFightTransitionWithoutItsBattleYetKeepsVehiclesBlocked() {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    Warband band = CampaignRaidWarbandService.getAttackerWarband(raid);
    assertEquals(
        CampaignRaidResults.TransitionResult.OK,
        CampaignRaidService.transitionToFighting(rig.war, raid.getMusterEndsAt()));
    assertNull(raid.getBattleId());
    assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(band));
    assertTrue(BattleManager.get().isEmpty());
  }

  @Test
  void vehicleRestrictionsFindTheRightRaidAfterOtherActiveWars() {
    Faction neutralAttackers = rig.domain.saved("neutral_attackers", "NeutralAttacker");
    Faction neutralDefenders = rig.domain.saved("neutral_defenders", "NeutralDefender");
    War noRaid = new War(rig.war.getId() + 1, neutralAttackers, neutralDefenders);
    WarManager.addWar(noRaid);
    Collections.swap(WarManager.get(), 0, 1);
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid first = CampaignRaidService.getActive(rig.war);
    Warband firstBand = CampaignRaidWarbandService.getAttackerWarband(first);
    Faction secondAttackers = rig.domain.saved("second_attackers", "SecondAttacker");
    Faction secondDefenders = rig.domain.saved("second_defenders", "SecondDefender");
    War second = new War(rig.war.getId() + 2, secondAttackers, secondDefenders);
    second.setGoal(WarGoalType.SUBJUGATE);
    second.setWarType(WarType.SUBJUGATE);
    second.setBattleDay(Fixture.DAY);
    second.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    second.setOccupiedByAttacker(new ArrayList<>(List.of(30)));
    second.setOccupiedByDefender(new ArrayList<>(List.of(40)));
    Installation source = rig.install(secondAttackers, "second_source", InstallationKind.PORT, 30);
    Installation target = rig.install(secondDefenders, "second_target", InstallationKind.PORT, 40);
    WarManager.addWar(second);
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            second, secondAttackers, source.getId(), target.getId(), rig.now()));
    CampaignRaid secondRaid = CampaignRaidService.getActive(second);
    Warband secondBand = CampaignRaidWarbandService.getAttackerWarband(secondRaid);

    assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(secondBand));
    assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(firstBand));
    assertNull(CampaignRaidService.getActive(noRaid));
    assertSame(first, CampaignRaidService.getActive(rig.war));
    assertSame(secondRaid, CampaignRaidService.getActive(second));
    assertEquals(CampaignRaidState.MUSTER, first.getState());
    assertEquals(CampaignRaidState.MUSTER, secondRaid.getState());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ended", "unknown-from-save"})
  void inactiveOrUnknownRaidStateInASavedWarCannotPermanentlyLockRosterVehicles(String state)
      throws Exception {
    assertEquals(
        CampaignRaidResults.LaunchResult.STARTED,
        CampaignRaidService.beginMuster(
            rig.war, rig.attacker, rig.source.getId(), rig.target.getId(), rig.now()));
    CampaignRaid raid = CampaignRaidService.getActive(rig.war);
    assertEquals(
        CampaignRaidResults.JoinResult.OK,
        CampaignRaidJoinService.join(
            rig.war, rig.alice.getUniqueId(), "Alice", rig.attacker, raid.getId(), rig.now()));
    Warband band = CampaignRaidWarbandService.getAttackerWarband(raid);
    Set<UUID> before = Set.copyOf(band.getMemberIds());
    WarData saved = WarMapper.toData(rig.war);
    saved.activeCampaignRaid.state = state;
    Path file = savedFiles.resolve("invalid-raid-state.json");
    Files.writeString(file, JsonUtil.GSON.toJson(saved));
    War loaded = WarMapper.fromData(JsonUtil.readJson(file.toFile(), WarData.class));
    assertNotNull(loaded);
    WarManager.get().set(WarManager.get().indexOf(rig.war), loaded);

    assertFalse(WarbandVehicleRules.blocksVehicleEntryForWarband(band));

    assertEquals(before, band.getMemberIds());
    assertSame(band, WarbandManager.getByString(band.getId()));
    assertTrue(loaded.isActive());
    assertTrue(BattleManager.get().isEmpty());
  }

  @Test
  void aLoadedUnknownBattleTypeCannotTurnRetreatIntoAWinForEitherSide() throws Exception {
    Warband attackers = factionBand("saved_unknown_type");
    Battle original = battle("saved_unknown_battle", attackers, true);
    assertNull(original.start());
    BattleData saved = BattleMapper.toData(original);
    saved.battleType = "type-removed-from-configuration";
    Path file = savedFiles.resolve("unknown-battle-type.json");
    Files.writeString(file, JsonUtil.GSON.toJson(saved));
    original.end();
    BattleManager.get().remove(original);
    Battle loaded = BattleMapper.fromData(JsonUtil.readJson(file.toFile(), BattleData.class));
    assertNotNull(loaded);
    for (var side : saved.sides) {
      for (String id : side.warbandIds) {
        loaded.getSideById(side.id).addBand(WarbandManager.getByString(id));
      }
    }
    BattleManager.addBattle(loaded);
    List<UUID> before = List.copyOf(attackers.getMemberIds());

    assertEquals(
        RetreatResult.REJECTED_WRONG_BATTLE_TYPE,
        BattleWarbandRetreatService.retreat(rig.alice, Instant.now().plusSeconds(1000)));

    assertTrue(loaded.hasStarted());
    assertEquals(before, List.copyOf(attackers.getMemberIds()));
    assertSame(loaded, BattleManager.getByString(loaded.getId()));
  }

  @Test
  void opponentResolutionLeavesMissingContextUnresolvedAndUsesManualSideIds() {
    assertNull(BattleWarbandRetreatService.opponentSideId(null, "attacker"));
    Battle manual = BattleFactory.createBlank(BattleType.FIELD, "manual_opponents");
    assertNull(BattleWarbandRetreatService.opponentSideId(manual, null));
    assertNull(BattleWarbandRetreatService.opponentSideId(manual, "spectator"));
    assertEquals("defender", BattleWarbandRetreatService.opponentSideId(manual, "attacker"));
    assertEquals("attacker", BattleWarbandRetreatService.opponentSideId(manual, "defender"));
    assertFalse(manual.hasStarted());
  }

  @Test
  void siegePresenceCountsOnlyOnFootPlayersInsideTheAreaAndClearsItsTimerOnEnd() {
    Warband attackers = openBand("siege_attackers", rig.alice);
    Player second = rig.player("Second"), third = rig.player("Third");
    attackers.addPlayer(second);
    attackers.addPlayer(third);
    Battle battle = battle("siege_runtime", attackers, false);
    battle.setBattleType(BattleType.SIEGE);
    battle.setContestDurationSeconds(30);
    battle.setContestArea(
        new ContestArea(
            new BattleLocation("world", -5, 60, -5, 0, 0),
            new BattleLocation("world", 5, 70, 5, 0, 0)));
    for (Player player : List.of(rig.alice, second, third, rig.bob))
      when(player.getLocation()).thenReturn(new Location(rig.domain.ui.world, 0, 64, 0));
    try (MockedStatic<VehicleFramework> framework = mockStatic(VehicleFramework.class)) {
      VehicleManager vehicles = mock(VehicleManager.class);
      framework.when(VehicleFramework::getVehicleManager).thenReturn(vehicles);
      assertNull(battle.start());
      for (int i = 0; i < 4; i++) SiegeContestService.tick(battle);
      assertEquals(30, battle.getContestHoldRemainingSeconds());
      SiegeContestService.tick(battle);
      assertEquals(29, battle.getContestHoldRemainingSeconds());
      verify(rig.alice, atLeastOnce())
          .sendTitle(" ", "§6Siege: §f30s §7- §aATTACKERS HOLDING", 0, 10, 0);
      when(vehicles.get(third)).thenReturn(mock(ActiveVehicle.class));
      for (int i = 0; i < 5; i++) SiegeContestService.tick(battle);
      assertEquals(29, battle.getContestHoldRemainingSeconds());
      Player fourth = rig.player("Fourth"), fifth = rig.player("Fifth");
      for (Player defender : List.of(fourth, fifth)) {
        battle.getSideById("defender").getBands().getFirst().addPlayer(defender);
        when(defender.getLocation()).thenReturn(new Location(rig.domain.ui.world, 0, 64, 0));
      }
      for (int i = 0; i < 5; i++) SiegeContestService.tick(battle);
      assertEquals(30, battle.getContestHoldRemainingSeconds());
      verify(rig.bob, atLeastOnce())
          .sendTitle(" ", "§6Siege: §f29s §7- §cDEFENDERS HOLDING", 0, 10, 0);
      battle.getSideById("defender").getBands().getFirst().removePlayer(fourth);
      battle.getSideById("defender").getBands().getFirst().removePlayer(fifth);
      battle.setContestHoldRemainingSeconds(29);
      when(vehicles.get(third)).thenReturn(null);
      when(third.getLocation()).thenReturn(new Location(rig.domain.ui.world, 20, 64, 0));
      for (int i = 0; i < 5; i++) SiegeContestService.tick(battle);
      assertEquals(29, battle.getContestHoldRemainingSeconds());
      when(third.getLocation()).thenReturn(new Location(rig.domain.ui.world, 0, 64, 0));
      for (int i = 0; i < 4; i++) SiegeContestService.tick(battle);
      battle.end();
      assertNull(battle.start());
      SiegeContestService.tick(battle);
      assertEquals(30, battle.getContestHoldRemainingSeconds());
      when(Bukkit.getWorld("world")).thenReturn(null);
      for (int i = 0; i < 5; i++) SiegeContestService.tick(battle);
      assertEquals(
          30,
          battle.getContestHoldRemainingSeconds(),
          "An unloaded contest world pauses its clock");
    }
    assertDoesNotThrow(() -> SiegeContestService.tick(null));
    assertDoesNotThrow(
        () -> SiegeContestService.tick(BattleFactory.createBlank(BattleType.FIELD, "not_started")));
  }

  @ParameterizedTest
  @CsvSource({
    "REJECTED_NOT_IN_WARBAND, You need to lead a warband to retreat.",
    "REJECTED_NOT_LEADER, Only the warband leader can retreat.",
    "REJECTED_PENDING_LEADER, Your warband has no leader yet.",
    "REJECTED_NOT_IN_BATTLE, You are not in an active campaign battle.",
    "REJECTED_BATTLE_NOT_STARTED, The battle has not started yet.",
    "REJECTED_NOT_CAMPAIGN_BATTLE, You can only retreat from campaign battles.",
    "REJECTED_RAID, You cannot retreat from a raid.",
    "REJECTED_WRONG_BATTLE_TYPE, You cannot retreat from this battle type.",
    "REJECTED_WAR_INACTIVE, War not found.",
    "REJECTED_NO_OPPONENT, Could not resolve the opposing battle side."
  })
  void rejectionMessagesExplainTheRequiredAction(RetreatResult result, String explanation) {
    assertEquals("§c" + explanation, BattleWarbandRetreatService.Messages.messageForResult(result));
  }

  @Test
  void staleOrAbsentConfirmationDoesNotEndAnUnrelatedBattle() {
    BattleWarbandRetreatService.ConfirmHandler.handleConfirm(null, true);
    assertNull(BattleWarbandRetreatService.Messages.messageForResult(null));
    assertEquals(
        "§cYou cannot retreat yet.",
        BattleWarbandRetreatService.Messages.messageForResult(RetreatResult.REJECTED_TOO_EARLY));
    assertEquals(
        "§cYou cannot retreat yet.",
        BattleWarbandRetreatService.Messages.messageForResult(
            RetreatResult.REJECTED_TOO_EARLY, rig.alice, Instant.now()));
    Warband band = openBand("confirm_stale", rig.alice);
    assertEquals(
        "§cYou cannot retreat yet.",
        BattleWarbandRetreatService.Messages.messageForResult(
            RetreatResult.REJECTED_TOO_EARLY, rig.alice, Instant.now()));
    Battle battle = battle("confirm_stale_battle", band, true);
    assertNull(battle.start());
    band.setLeader(rig.bob);
    rig.domain.inventory.confirming.put(rig.alice, rig.attacker);
    BattleWarbandRetreatService.ConfirmHandler.handleConfirm(rig.alice, true);
    assertTrue(battle.hasStarted());
    assertFalse(rig.domain.inventory.confirming.containsKey(rig.alice));
    verify(rig.alice).sendMessage(BattleWarbandRetreatService.Messages.NOT_LEADER);
  }

  private Warband factionBand(String id) {
    Warband band = Warband.createCampaignSideShell(id, rig.war, rig.war.getAttackers(), "attacker");
    band.setLeader(rig.alice);
    band.addPlayer(rig.alice);
    WarbandManager.addWarband(band);
    return band;
  }

  private Warband openBand(String id, Player leader) {
    Warband band = new Warband(id, leader);
    band.setLocked(false);
    WarbandManager.addWarband(band);
    return band;
  }

  private Battle battle(String id, Warband attacker, boolean campaign) {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, id);
    if (campaign) {
      battle.setWarId(rig.war.getId());
      battle.setProvinceId(20);
    }
    battle.getSideById("attacker").addBand(attacker);
    battle.getSideById("defender").addBand(openBand(id + "_defenders", rig.bob));
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(rig.domain.ui.world, 0, 64, 0));
      side.setJail(new Location(rig.domain.ui.world, 10, 64, 0));
    }
    BattleManager.addBattle(battle);
    return battle;
  }
}
