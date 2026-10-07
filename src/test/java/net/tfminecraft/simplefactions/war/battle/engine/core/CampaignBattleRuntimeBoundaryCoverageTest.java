package net.tfminecraft.simplefactions.war.battle.engine.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.database.WarbandData;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandLeaveBlock;
import net.tfminecraft.simplefactions.war.battle.engine.win.SiegeWinService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.military.BattleLivesService;
import net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandMapper;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.ContestArea;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignBattleRuntimeBoundaryCoverageTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void retainedBattlePoolsStayUnchangedWhenTheirWarHasEndedOrBeenRemoved(boolean removed)
      throws Exception {
    try (Fixture rig = new Fixture()) {
      Battle battle = BattleFactory.createBlank(BattleType.FIELD, "boundary_retained_lives");
      battle.setWarId(rig.war.getId());
      battle.setProvinceId(20);
      battle.getSideById("attacker").setLives(17);
      battle.getSideById("defender").setLives(23);
      if (removed) net.tfminecraft.simplefactions.managers.WarManager.get().remove(rig.war);
      else rig.war.end(net.tfminecraft.simplefactions.war.enums.WarEndReason.ADMIN_END);

      BattleLivesService.applyCampaignLives(battle);

      assertEquals(17, battle.getSideById("attacker").getLives());
      assertEquals(23, battle.getSideById("defender").getLives());
      assertFalse(battle.hasStarted());
      assertTrue(battle.getSides().stream().allMatch(side -> side.getBands().isEmpty()));
    }
  }

  @Test
  void incompleteLegacyWarbandRosterRowsKeepValidMembersAndInvitationsThroughJsonReload() {
    UUID member = UUID.randomUUID(), invited = UUID.randomUUID();
    WarbandData legacy = new WarbandData();
    legacy.id = "legacy_boundary_roster";
    legacy.name = null;
    legacy.leaderId = "invalid-player-id";
    legacy.memberIds = new ArrayList<>(Arrays.asList(null, " ", "broken", member.toString()));
    legacy.invitedIds = new ArrayList<>(Arrays.asList("invalid", invited.toString()));
    legacy.locked = true;
    legacy.faction = true;
    legacy.campaignSideId = "attacker";
    Gson gson = new com.google.gson.GsonBuilder().serializeNulls().create();

    Warband restored =
        WarbandMapper.fromData(gson.fromJson(gson.toJson(legacy), WarbandData.class));

    assertNotNull(restored);
    assertEquals("legacy_boundary_roster", restored.getName());
    assertEquals(Warband.pendingLeaderUuid(legacy.id), restored.getLeaderId());
    assertTrue(restored.isPendingLeader());
    assertEquals(List.of(member), List.copyOf(restored.getMemberIds()));
    assertEquals(Set.of(invited), restored.getInvitedIds());
    assertTrue(restored.isLocked());
    assertTrue(restored.isFaction());
    assertEquals("attacker", restored.getCampaignSideId());
    WarbandData canonical = WarbandMapper.toData(restored);
    assertEquals(Warband.pendingLeaderUuid(legacy.id).toString(), canonical.leaderId);
    assertEquals(List.of(member.toString()), canonical.memberIds);
    assertEquals(List.of(invited.toString()), canonical.invitedIds);
    legacy.memberIds = null;
    legacy.invitedIds = null;
    Warband empty = WarbandMapper.fromData(gson.fromJson(gson.toJson(legacy), WarbandData.class));
    assertNotNull(empty);
    assertEquals(0, empty.getRealMemberCount());
    assertTrue(empty.getInvitedIds().isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void absentWarbandIdentityCannotCreateAReplacementRoster(String id) {
    WarbandData data = new WarbandData();
    data.id = id;
    assertNull(WarbandMapper.fromData(data));
    assertNull(WarbandMapper.fromData(null));
    assertNull(WarbandMapper.toData(null));
  }

  @Test
  void missingRespawnIdentifiersCannotConsumeAnotherPlayersPendingJailRoute() {
    UUID player = UUID.randomUUID(), other = UUID.randomUUID();
    try {
      BattleRespawnRouting.scheduleJailRespawn(player, true);
      BattleRespawnRouting.scheduleJailRespawn(null, true);
      BattleRespawnRouting.scheduleJailRespawn(null, false);
      BattleRespawnRouting.clear(null);
      assertFalse(BattleRespawnRouting.consumeJailRespawn(null));
      assertFalse(BattleRespawnRouting.consumeJailRespawn(other));
      assertTrue(BattleRespawnRouting.consumeJailRespawn(player));
      assertFalse(BattleRespawnRouting.consumeJailRespawn(player));
      BattleRespawnRouting.scheduleJailRespawn(player, true);
      BattleRespawnRouting.scheduleJailRespawn(player, false);
      assertFalse(BattleRespawnRouting.consumeJailRespawn(player));
    } finally {
      BattleRespawnRouting.clear(player);
      BattleRespawnRouting.clear(other);
    }
  }

  @Test
  void invalidContestEditsPreserveExistingBoundsAndCrossWorldAreasNeverContainPlayers()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "boundary_contest");
      BattleContestSetup.setContestMin(battle, new Location(rig.domain.ui.world, 3, 60, 5));
      BattleContestSetup.setContestMax(battle, new Location(rig.domain.ui.world, -3, 70, -5));
      ContestArea area = battle.getContestArea();
      BattleLocation originalMin = area.getMin(), originalMax = area.getMax();
      assertTrue(area.contains(new Location(rig.domain.ui.world, 0, 65, 0)));
      assertThrows(
          IllegalArgumentException.class, () -> BattleContestSetup.setContestMin(battle, null));
      assertThrows(
          IllegalArgumentException.class, () -> BattleContestSetup.setContestMax(battle, null));
      assertSame(originalMin, area.getMin());
      assertSame(originalMax, area.getMax());
      assertFalse(area.contains(new Location(null, 0, 65, 0)));
      World other = mock(World.class);
      when(other.getName()).thenReturn("other_world");
      when(Bukkit.getWorld("other_world")).thenReturn(other);
      assertFalse(area.contains(new Location(other, 0, 65, 0)));
      assertFalse(area.contains(null));
      ContestArea unset = new ContestArea();
      assertFalse(unset.isConfigured());
      assertFalse(unset.contains(new Location(rig.domain.ui.world, 0, 65, 0)));
      when(Bukkit.getWorld("unloaded_world")).thenReturn(null);
      ContestArea unavailable =
          new ContestArea(new BattleLocation("unloaded_world", 0, 60, 0, 0, 0), originalMax);
      assertFalse(unavailable.isConfigured());
      assertFalse(unavailable.contains(new Location(rig.domain.ui.world, 0, 65, 0)));
      BattleContestSetup.setContestMax(battle, new Location(other, 3, 70, 5));
      assertFalse(area.contains(new Location(rig.domain.ui.world, 0, 65, 0)));
      assertFalse(area.contains(new Location(other, 0, 65, 0)));
      rig.remember(Cache.class, "battleSiegeContestDurationSeconds");
      Cache.battleSiegeContestDurationSeconds = 90;
      battle.setContestDurationSeconds(0);
      assertEquals(90, BattleContestSetup.getEffectiveDurationSeconds(battle));
      battle.setContestDurationSeconds(45);
      assertEquals(45, BattleContestSetup.getEffectiveDurationSeconds(battle));
    }
  }

  @Test
  void missingParticipantAndLeaveIdentifiersCannotAlterARealRosterOrItsExistingBlock()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      Battle battle = BattleFactory.createBlank(BattleType.FIELD, "boundary_participants");
      Warband band =
          Warband.createWithMemberIds(
              "boundary_roster", rig.alice.getUniqueId(), true, rig.bob.getUniqueId());
      UUID dummy = UUID.randomUUID();
      band.addDummyMembers(List.of(dummy), Map.of(dummy, "Practice Soldier"));
      battle.getSideById("attacker").addBand(band);
      Set<UUID> participants = BattleParticipantCollector.collect(battle);
      assertEquals(Set.of(rig.alice.getUniqueId(), rig.bob.getUniqueId()), participants);
      assertThrows(UnsupportedOperationException.class, () -> participants.add(dummy));
      assertTrue(BattleParticipantCollector.collect(null).isEmpty());

      CampaignWarbandLeaveBlock.block(battle.getId(), band.getId(), rig.alice.getUniqueId());
      CampaignWarbandLeaveBlock.block(null, band.getId(), rig.alice.getUniqueId());
      CampaignWarbandLeaveBlock.block(battle.getId(), null, rig.alice.getUniqueId());
      CampaignWarbandLeaveBlock.block(battle.getId(), band.getId(), null);
      assertFalse(CampaignWarbandLeaveBlock.isBlocked(null, band.getId(), rig.alice.getUniqueId()));
      assertFalse(
          CampaignWarbandLeaveBlock.isBlocked(battle.getId(), null, rig.alice.getUniqueId()));
      assertFalse(CampaignWarbandLeaveBlock.isBlocked(battle.getId(), band.getId(), null));
      assertTrue(
          CampaignWarbandLeaveBlock.isBlocked(
              battle.getId(), band.getId(), rig.alice.getUniqueId()));
      assertFalse(
          CampaignWarbandLeaveBlock.isBlocked(battle.getId(), band.getId(), rig.bob.getUniqueId()));
      assertEquals(participants, BattleParticipantCollector.collect(battle));
    }
  }

  @Test
  void siegeChecksIgnoreInvalidInputsAndEndTwoEliminatedSidesWithoutAwardingAWinner()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "battleEmptySideGraceSeconds");
      Cache.battleEmptySideGraceSeconds = 0;
      Battle siege = BattleFactory.createBlank(BattleType.SIEGE, "boundary_empty_siege");
      Battle field = BattleFactory.createBlank(BattleType.FIELD, "boundary_field");
      field.setStarted(true);
      SiegeWinService.checkSiegeWin(null);
      SiegeWinService.checkSiegeWin(siege);
      SiegeWinService.checkSiegeWin(field);
      assertFalse(siege.hasStarted());
      assertTrue(field.hasStarted());
      verify(Bukkit.getPluginManager(), never()).callEvent(any(BattleEndedEvent.class));

      siege.setStarted(true);
      siege.setContestHoldRemainingSeconds(60);
      siege.getSideById("attacker").setLives(0);
      siege.getSideById("defender").setLives(0);
      SiegeWinService.checkSiegeWin(siege);

      assertFalse(siege.hasStarted());
      verify(Bukkit.getPluginManager())
          .callEvent(
              argThat(
                  event ->
                      event instanceof BattleEndedEvent ended
                          && ended.getBattleId().equals(siege.getId())
                          && !ended.hasWinner()
                          && ended.getParticipantIds().isEmpty()
                          && ended.getEndReason() == BattleEndReason.TIMER));
      assertTrue(field.hasStarted(), "Siege cleanup must not end another running battle");
    }
  }

  @Test
  void campaignSideMappingUsesTheSavedOffensiveCoalitionAndRejectsForeignSides() throws Exception {
    try (Fixture rig = new Fixture()) {
      Battle battle = BattleFactory.createBlank(BattleType.FIELD, "boundary_counterattack");
      battle.setWarId(rig.war.getId());
      battle.setOffensiveCoalition(CampaignCoalition.DEFENDER);
      Faction outsider = rig.domain.saved("runtime_outsider", "OutsiderLeader");
      War other = new War(998013, outsider, rig.attacker);
      assertSame(
          rig.war.getDefenders(), CampaignBattleSides.warSideFor(rig.war, battle, "attacker"));
      assertSame(
          rig.war.getAttackers(), CampaignBattleSides.warSideFor(rig.war, battle, "defender"));
      assertEquals(
          "attacker", CampaignBattleSides.battleSideFor(rig.war, battle, rig.war.getDefenders()));
      assertEquals(
          "defender", CampaignBattleSides.battleSideFor(rig.war, battle, rig.war.getAttackers()));
      assertEquals(
          BelligerentRole.DEFENDER, CampaignBattleSides.roleFor(rig.war, battle, "attacker"));
      assertEquals(
          BelligerentRole.ATTACKER, CampaignBattleSides.roleFor(rig.war, battle, "defender"));
      assertNull(CampaignBattleSides.battleSideFor(null, battle, rig.war.getDefenders()));
      assertNull(CampaignBattleSides.battleSideFor(rig.war, battle, null));
      assertNull(CampaignBattleSides.battleSideFor(rig.war, battle, other.getAttackers()));
      assertNull(CampaignBattleSides.warSideFor(rig.war, battle, "unrelated"));
      assertNull(CampaignBattleSides.roleFor(rig.war, battle, "unrelated"));
      assertSame(rig.war.getAttackers(), CampaignBattleSides.warSideFor(rig.war, null, "attacker"));
      assertTrue(battle.getSides().stream().allMatch(side -> side.getBands().isEmpty()));
    }
  }

  @Test
  void savedCasualtiesResumeFromValidatedRowsAndSnapshotsCannotChangeTheLedger() throws Exception {
    try (Fixture rig = new Fixture()) {
      Battle source =
          BattleFactory.createBlank(BattleType.FIELD, "boundary_ledger_" + UUID.randomUUID());
      source.setWarId(rig.war.getId());
      source.setStarted(true);
      BattleData data = BattleMapper.toData(source);
      data.sideCasualties = new HashMap<>();
      data.sideCasualties.put("attacker", 2);
      data.sideCasualties.put(null, 8);
      data.sideCasualties.put("invalid_null", null);
      data.sideCasualties.put("invalid_negative", -7);
      data.sideCasualties.put("invalid_zero", 0);
      Battle restored = BattleMapper.fromData(data);
      try {
        assertEquals(Map.of("attacker", 2), BattleCasualtyLedger.getSideCasualties(restored));
        assertTrue(BattleCasualtyLedger.getSideCasualties(null).isEmpty());
        BattleCasualtyLedger.clear(null);
        BattleCasualtyLedger.recordSideCasualty(restored, restored.getSideById("attacker"));
        Map<String, Integer> snapshot = BattleCasualtyLedger.getSideCasualties(restored);
        assertEquals(Map.of("attacker", 3), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("attacker", 99));
        assertEquals(Map.of("attacker", 3), BattleMapper.toData(restored).sideCasualties);
      } finally {
        BattleCasualtyLedger.clear(restored);
      }
      assertTrue(BattleCasualtyLedger.getSideCasualties(restored).isEmpty());
    }
  }

  @Test
  void anotherCasualtyCannotOverflowOrEraseAFullPersistedCounter() throws Exception {
    try (Fixture rig = new Fixture()) {
      Battle battle =
          BattleFactory.createBlank(BattleType.FIELD, "boundary_full_ledger_" + UUID.randomUUID());
      battle.setWarId(rig.war.getId());
      battle.setStarted(true);
      battle.setRecordedSideCasualty("attacker", Integer.MAX_VALUE);
      try {
        BattleCasualtyLedger.recordSideCasualty(battle, battle.getSideById("attacker"));

        assertEquals(
            Map.of("attacker", Integer.MAX_VALUE), BattleCasualtyLedger.getSideCasualties(battle));
        assertEquals(
            Map.of("attacker", Integer.MAX_VALUE), BattleMapper.toData(battle).sideCasualties);
      } finally {
        BattleCasualtyLedger.clear(battle);
      }
    }
  }

  @Test
  void aLargePositiveRegimentCountMustNotCollapseToTheMinimumBattleLives() throws Exception {
    try (Fixture rig = new Fixture()) {
      assertEquals(4, Cache.warBattleLivesPerRegiment);
      int lives = BattleLivesService.computeSideLives(1_000_000_000, 1);
      assertTrue(
          lives >= 1_000_000_000,
          "Positive pool multiplication must not wrap before the roster debit: " + lives);
    }
  }
}
