package net.tfminecraft.simplefactions.events;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent;
import org.junit.jupiter.api.Test;

class PluginEventBoundaryCoverageTest {
  @Test
  void factionLifecycleEventsCarryTheirActorAndSupportCancellationWithoutMutatingTheFaction() {
    try (FactionDomainFixture fixture = new FactionDomainFixture()) {
      var faction = fixture.saved("realm", "Alice");
      var player = fixture.player("Alice");
      FactionCreateEvent created = new FactionCreateEvent(player, faction);
      FactionDeleteEvent deleted = new FactionDeleteEvent(player, faction);
      assertSame(faction, created.getFaction());
      assertSame(faction, deleted.getFaction());
      assertSame(player, created.getCreator());
      assertSame(player, deleted.getCreator());
      assertSame(FactionCreateEvent.getHandlerList(), created.getHandlers());
      assertSame(FactionDeleteEvent.getHandlerList(), deleted.getHandlers());
      assertNotSame(created.getHandlers(), deleted.getHandlers());
      assertFalse(created.isCancelled());
      assertFalse(deleted.isCancelled());
      created.setCancelled(true);
      deleted.setCancelled(true);
      assertTrue(created.isCancelled());
      assertTrue(deleted.isCancelled());
      assertEquals("Alice", faction.getLeader());
    }
  }

  @Test
  void battleEventsSnapshotTheirPayloadAndKeepIndependentHandlerLists() {
    UUID player = UUID.randomUUID();
    Set<UUID> participants = new HashSet<>(Set.of(player));
    Map<String, Integer> casualties = new HashMap<>(Map.of("attacker", 3));
    BattleStartedEvent started = new BattleStartedEvent("field", BattleType.FIELD, 7, participants);
    BattleEndedEvent ended =
        new BattleEndedEvent(
            "field",
            BattleType.FIELD,
            7,
            "defender",
            casualties,
            participants,
            BattleEndReason.TIMER);
    participants.clear();
    casualties.clear();
    assertEquals(Set.of(player), started.getParticipantIds());
    assertEquals(Set.of(player), ended.getParticipantIds());
    assertEquals(Map.of("attacker", 3), ended.getSideCasualties());
    assertThrows(UnsupportedOperationException.class, () -> ended.getSideCasualties().clear());
    assertThrows(UnsupportedOperationException.class, () -> started.getParticipantIds().clear());
    assertEquals(BattleType.FIELD, started.getBattleType());
    assertEquals(BattleType.FIELD, ended.getBattleType());
    assertEquals("field", started.getBattleId());
    assertEquals("field", ended.getBattleId());
    assertEquals(7, started.getWarId());
    assertEquals(7, ended.getWarId());
    assertTrue(ended.hasWinner());
    assertEquals(BattleEndReason.TIMER, ended.getEndReason());
    assertFalse(ended.isCampaignRaid());
    assertTrue(ended.isLootEnabled());
    assertSame(BattleStartedEvent.getHandlerList(), started.getHandlers());
    assertSame(BattleEndedEvent.getHandlerList(), ended.getHandlers());
    assertNotSame(started.getHandlers(), ended.getHandlers());
    BattleEndedEvent draw =
        new BattleEndedEvent("draw", BattleType.SIEGE, null, " ", null, null, null);
    assertFalse(draw.hasWinner());
    assertEquals(BattleEndReason.TIMER, draw.getEndReason());
    assertTrue(draw.getParticipantIds().isEmpty());
  }
}
