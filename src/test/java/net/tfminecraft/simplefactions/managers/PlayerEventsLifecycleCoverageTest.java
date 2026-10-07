package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.Events.*;
import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.*;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerEventsLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private String oldVoting;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    oldVoting = Cache.votingBlock;
    Cache.votingBlock = "iaf(test:booth)";
  }

  @AfterEach
  void close() {
    try {
      Cache.votingBlock = oldVoting;
    } finally {
      fixture.close();
    }
  }

  @Test
  void joiningRefreshesOnlyTheMatchingRealmAndGuildLeaderAfterTheDelay() {
    var realm = fixture.saved("realm", "Alice");
    realm.getOrCreateMainGuild();
    var guild = fixture.guild(realm, "traders", "Bob");
    var names = new HashMap<>(Map.of("Alice", "Lady Alice", "Bob", "Master Bob"));
    LeaderCharacters.setProbe(names::get);
    var listener = new LeaderCharacterListener(fixture.ui.plugin);
    listener.onJoin(new PlayerJoinEvent(fixture.player("Alice"), "join"));
    assertNull(realm.getLeaderCharacter());
    fixture.ui.tasks.removeLast().run();
    assertEquals("Lady Alice", realm.getLeaderCharacter());
    assertNull(guild.getLeaderCharacter());
    verify(fixture.map, times(1)).markLeaderNamesChanged();
    listener.onJoin(new PlayerJoinEvent(fixture.player("Bob"), "join"));
    fixture.ui.tasks.removeLast().run();
    assertEquals("Master Bob", guild.getLeaderCharacter());
    verify(fixture.map, times(2)).markLeaderNamesChanged();
    LeaderCharacterListener.refresh("alice");
    verify(fixture.map, times(2)).markLeaderNamesChanged();
    names.put("Alice", "Queen Alice");
    names.put("Bob", "Guildmaster Bob");
    LeaderCharacterListener.refresh(null);
    assertEquals("Queen Alice", realm.getLeaderCharacter());
    assertEquals("Guildmaster Bob", guild.getLeaderCharacter());
    verify(fixture.map, times(3)).markLeaderNamesChanged();
  }

  @ParameterizedTest
  @EnumSource(
      value = Action.class,
      names = {"RIGHT_CLICK_AIR", "RIGHT_CLICK_BLOCK"})
  void matchingVotingFurnitureCancelsOnlyWhenTheBoothWasPresented(Action action) {
    FactionManager manager = mock(FactionManager.class);
    var listener = new VotingBoothListener(manager);
    Player player = fixture.player("Alice");
    Entity entity = mock(Entity.class);
    Location location = mock(Location.class);
    Block block = mock(Block.class);
    when(entity.getLocation()).thenReturn(location);
    when(location.getBlock()).thenReturn(block);
    FurnitureInteractEvent event = mock(FurnitureInteractEvent.class);
    when(event.getAction()).thenReturn(action);
    when(event.getNamespacedID()).thenReturn("test:booth");
    when(event.getPlayer()).thenReturn(player);
    when(event.getBukkitEntity()).thenReturn(entity);
    when(manager.presentBooth(player, block)).thenReturn(true, false);
    listener.openVotingFurniture(event);
    listener.openVotingFurniture(event);
    verify(manager, times(2)).presentBooth(player, block);
    verify(event, times(1)).setCancelled(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"left", "different", "missing_entity"})
  void unrelatedFurnitureInteractionsDoNotOpenVoting(String reason) {
    FactionManager manager = mock(FactionManager.class);
    var event = mock(FurnitureInteractEvent.class);
    when(event.getAction())
        .thenReturn(reason.equals("left") ? Action.LEFT_CLICK_BLOCK : Action.RIGHT_CLICK_BLOCK);
    when(event.getNamespacedID())
        .thenReturn(reason.equals("different") ? "test:chair" : "test:booth");
    new VotingBoothListener(manager).openVotingFurniture(event);
    verifyNoInteractions(manager);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void placingAndBreakingFurnitureMaintainTheSamePhysicalVotingBooth() {
    FactionManager manager = mock(FactionManager.class);
    var listener = new VotingBoothListener(manager);
    Player player = fixture.player("Alice");
    Entity entity = mock(Entity.class);
    Location location = mock(Location.class);
    Block block = mock(Block.class);
    when(entity.getLocation()).thenReturn(location);
    when(location.getBlock()).thenReturn(block);
    var placed = mock(FurniturePlaceSuccessEvent.class);
    var broken = mock(FurnitureBreakEvent.class);
    when(placed.getNamespacedID()).thenReturn("test:booth");
    when(broken.getNamespacedID()).thenReturn("test:booth");
    when(placed.getBukkitEntity()).thenReturn(entity);
    when(broken.getBukkitEntity()).thenReturn(entity);
    when(placed.getPlayer()).thenReturn(player);
    when(broken.getPlayer()).thenReturn(player);
    listener.placeVotingFurniture(placed);
    listener.breakVotingFurniture(broken);
    verify(manager).registerVotingBooth(player, block);
    verify(manager).unregisterVotingBooth(player, block);
    when(placed.getBukkitEntity()).thenReturn(null);
    when(broken.getBukkitEntity()).thenReturn(null);
    listener.placeVotingFurniture(placed);
    listener.breakVotingFurniture(broken);
    when(placed.getNamespacedID()).thenReturn("test:chair");
    when(broken.getNamespacedID()).thenReturn("test:chair");
    listener.placeVotingFurniture(placed);
    listener.breakVotingFurniture(broken);
    verifyNoMoreInteractions(manager);
  }
}
