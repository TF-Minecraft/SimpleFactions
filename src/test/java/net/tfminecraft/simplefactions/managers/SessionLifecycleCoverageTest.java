package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.government.session.Session;
import net.tfminecraft.simplefactions.government.session.Vote;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("deprecation")
class SessionLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private SessionManager manager;
  private final List<TextDisplay> holograms = new ArrayList<>();

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    manager = new SessionManager();
    when(fixture.ui.plugin.getSessionManager()).thenReturn(manager);
    when(Bukkit.getPlayer(anyString())).thenAnswer(call -> fixture.online.get(call.getArgument(0)));
    when(fixture.ui.world.spawnEntity(any(Location.class), eq(EntityType.TEXT_DISPLAY)))
        .thenAnswer(
            call -> {
              TextDisplay display = mock(TextDisplay.class);
              holograms.add(display);
              return display;
            });
    fixture.lawGroup(
        "constitution",
        Map.of(
            "effects.faction.rules",
            List.of("HAS_COUNCIL true", "APPOINTED_COUNCIL true"),
            "effects.faction.council-size",
            1));
  }

  @AfterEach
  void cleanup() {
    if (fixture != null) fixture.close();
  }

  private Player player(String name) {
    Player player = fixture.player(name);
    when(player.getWorld()).thenReturn(fixture.ui.world);
    return player;
  }

  private Faction faction(String id, String leader, String councillor) {
    Faction faction = fixture.saved(id, leader);
    player(leader);
    player(councillor);
    faction.addMember(councillor);
    faction.getGovernment().getCouncil().addMemberForce(councillor);
    Proposal proposal = new Proposal(leader, faction.getGovernment());
    proposal.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 12));
    faction.getGovernment().propose(proposal);
    return faction;
  }

  private Block block(Material type, int x) {
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(type);
    when(block.getWorld()).thenReturn(fixture.ui.world);
    when(block.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, x, 64, 0));
    return block;
  }

  private PlayerInteractEvent click(Player player, Action action, Block block) {
    return new PlayerInteractEvent(player, action, null, block, BlockFace.UP, EquipmentSlot.HAND);
  }

  private Session started(Faction faction, Block lantern) {
    Player leader = fixture.online.get(faction.getLeader());
    manager.newSession(leader, faction);
    PlayerInteractEvent click = click(leader, Action.RIGHT_CLICK_BLOCK, lantern);
    manager.onPlayerInteract(click);
    assertTrue(click.isCancelled());
    Session session = manager.getSession(faction.getGovernment().getCouncil());
    assertTrue(session.isStarted());
    return session;
  }

  private AsyncPlayerChatEvent chat(Player player, String message) {
    return new AsyncPlayerChatEvent(true, player, message, Set.of());
  }

  @Test
  void asyncChatDefersWorldHologramAndVoteChangesToTheCapturedMainThreadCallback() {
    Faction faction = faction("home", "Alice", "Bob");
    started(faction, block(Material.LANTERN, 0));
    Player bob = fixture.online.get("Bob");
    clearInvocations(bob, fixture.ui.world);
    holograms.forEach(display -> clearInvocations(display));
    AsyncPlayerChatEvent event = chat(bob, " yay ");

    manager.onPlayerChat(event);

    verify(bob, never()).getLocation();
    verify(bob, never()).sendMessage(anyString());
    holograms.forEach(display -> verifyNoInteractions(display));
    assertEquals(1, fixture.ui.tasks.size());
    assertFalse(event.isCancelled());
    fixture.ui.runTasks();
    verify(bob).sendMessage(contains("Yay"));
    verify(holograms.get(2)).setText(contains("1 Yay"));
  }

  @Test
  void quittingTheHostCancelsTheSessionAndItsAlreadyQueuedVoteCount() {
    Faction faction = faction("home", "Alice", "Bob");
    Session session = started(faction, block(Material.LANTERN, 0));
    session.recordVote("Alice", Vote.YAY);
    session.recordVote("Bob", Vote.YAY);
    assertFalse(fixture.ui.tasks.isEmpty());

    manager.onPlayerQuit(new PlayerQuitEvent(fixture.online.get("Alice"), "quit"));

    assertFalse(manager.hasSession(faction.getGovernment().getCouncil()));
    assertFalse(session.isStarted());
    assertFalse(session.recordVote("Bob", Vote.YAY));
    fixture.ui.runTasks();
    fixture.ui.runTasks();
    assertEquals(5, faction.getTaxRate());
    for (TextDisplay display : holograms) verify(display, times(1)).remove();
    verify(fixture.online.get("Alice"), never()).sendMessage(contains("report"));
  }

  @Test
  void breakingTheLanternCancelsDecidedButUnappliedProposalsAndDoesNotReviveTheSession() {
    Faction faction = faction("home", "Alice", "Bob");
    Block lantern = block(Material.LANTERN, 0);
    Session session = started(faction, lantern);
    session.recordVote("Alice", Vote.YAY);
    session.recordVote("Bob", Vote.YAY);
    fixture.ui.runTasks();
    assertNull(session.getCurrentProposal());

    manager.onBlockBreak(new BlockBreakEvent(lantern, fixture.online.get("Bob")));

    assertFalse(manager.hasSession(faction.getGovernment().getCouncil()));
    fixture.ui.runTasks();
    assertEquals(5, faction.getTaxRate());
    assertFalse(session.isStarted());
    for (TextDisplay display : holograms) verify(display, times(1)).remove();
  }

  @Test
  void playerInAnotherWorldCannotVoteAndDoesNotCauseADistanceException() {
    Faction faction = faction("home", "Alice", "Bob");
    Session session = started(faction, block(Material.LANTERN, 0));
    Player bob = fixture.online.get("Bob");
    when(bob.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
    assertDoesNotThrow(() -> manager.onPlayerChat(chat(bob, "nay")));
    assertDoesNotThrow(fixture.ui::runTasks);
    assertNotNull(session.getCurrentProposal());
    verify(bob, never()).sendMessage(contains("recorded"));
    verify(bob).sendMessage(contains("within 10 blocks"));
  }

  @Test
  void timingOutTwoPendingSessionsDoesNotModifyTheMapBeingIterated() {
    Faction first = faction("first", "Alice", "Bob");
    Faction second = faction("second", "Cara", "Drew");
    manager.newSession(fixture.online.get("Alice"), first);
    manager.newSession(fixture.online.get("Cara"), second);
    manager.start();
    assertEquals(1, fixture.ui.repeatingTasks.size());
    for (int tick = 0; tick < 60; tick++)
      assertDoesNotThrow(fixture.ui.repeatingTasks.getFirst()::run);
    assertFalse(manager.hasSession(first.getGovernment().getCouncil()));
    assertFalse(manager.hasSession(second.getGovernment().getCouncil()));
    assertEquals(1, first.getGovernment().getCouncil().getProposalHandler().getProposals().size());
    assertEquals(1, second.getGovernment().getCouncil().getProposalHandler().getProposals().size());
  }

  @Test
  void replacingASessionTerminatesItsCallbacksWithoutUnregisteringTheReplacement() {
    Faction faction = faction("home", "Alice", "Bob");
    Session old = started(faction, block(Material.LANTERN, 0));
    old.recordVote("Alice", Vote.YAY);
    old.recordVote("Bob", Vote.YAY);
    manager.newSession(fixture.online.get("Alice"), faction);
    Session replacement = manager.getSession(faction.getGovernment().getCouncil());
    assertNotSame(old, replacement);
    assertFalse(old.isStarted());
    old.kill();
    old.end();
    old.start();
    old.onLanternClick(block(Material.LANTERN, 9));
    old.tick();
    fixture.ui.runTasks();
    fixture.ui.runTasks();
    assertSame(replacement, manager.getSession(faction.getGovernment().getCouncil()));
    assertEquals(5, faction.getTaxRate());
    assertFalse(old.recordVote("Alice", Vote.YAY));
    assertFalse(old.isStarted());
    for (TextDisplay display : holograms) verify(display, times(1)).remove();
  }

  @Test
  void shutdownKillsEverySessionAndQueuedVotesCannotChangeTheEndedSessions() {
    Faction first = faction("first", "Alice", "Bob");
    Faction second = faction("second", "Cara", "Drew");
    Session one = started(first, block(Material.LANTERN, 0));
    Session two = started(second, block(Material.LANTERN, 3));
    manager.onPlayerChat(chat(fixture.online.get("Bob"), "yay"));
    assertDoesNotThrow(manager::end);
    assertDoesNotThrow(manager::end);
    fixture.ui.runTasks();
    assertFalse(manager.hasSession(first.getGovernment().getCouncil()));
    assertFalse(manager.hasSession(second.getGovernment().getCouncil()));
    assertFalse(one.isStarted());
    assertFalse(two.isStarted());
    assertEquals(5, first.getTaxRate());
    assertEquals(5, second.getTaxRate());
    for (TextDisplay display : holograms) verify(display, times(1)).remove();
  }

  @Test
  void completedSessionCreatesOneReportEvenWhenEndIsCalledAgain() {
    Faction faction = faction("home", "Alice", "Bob");
    Session session = started(faction, block(Material.LANTERN, 0));
    session.recordVote("Alice", Vote.YAY);
    session.recordVote("Bob", Vote.YAY);
    fixture.ui.runTasks();
    fixture.ui.runTasks();
    session.end();
    session.kill();
    assertEquals(12, faction.getTaxRate());
    assertFalse(manager.hasSession(faction.getGovernment().getCouncil()));
    long reports =
        java.util.Arrays.stream(fixture.online.get("Alice").getInventory().getContents())
            .filter(item -> item != null && item.getType() == Material.WRITTEN_BOOK)
            .count();
    assertEquals(1, reports);
    verify(fixture.online.get("Alice"), times(1)).sendMessage(contains("Session report added"));
  }

  @Test
  void emptySessionEndsImmediatelyAndCannotBeRestartedBySelectingAnotherLantern() {
    Faction faction = faction("home", "Alice", "Bob");
    faction.getGovernment().getCouncil().getProposalHandler().clearProposals();
    manager.newSession(fixture.online.get("Alice"), faction);
    Session session = manager.getSession(faction.getGovernment().getCouncil());
    session.onLanternClick(block(Material.LANTERN, 0));
    assertFalse(session.isStarted());
    assertFalse(manager.hasSession(faction.getGovernment().getCouncil()));
    session.start();
    assertFalse(session.isStarted());
  }

  @ParameterizedTest
  @EnumSource(Vote.class)
  void validChatVotesAcknowledgeTheCorrectVoteAndPlayItsSoundOnlyAfterScheduling(Vote vote) {
    Faction faction = faction("home", "Alice", "Bob");
    started(faction, block(Material.LANTERN, 0));
    Player bob = fixture.online.get("Bob");
    when(bob.getLocation()).thenReturn(new Location(fixture.ui.world, 10, 64, 0));
    manager.onPlayerChat(chat(bob, vote.name().toLowerCase(java.util.Locale.ROOT)));
    verify(bob, never()).sendMessage(contains("recorded"));
    fixture.ui.runTasks();
    verify(bob).sendMessage(contains(vote.getDisplay()));
    Sound expected =
        switch (vote) {
          case YAY -> Sound.BLOCK_NOTE_BLOCK_CHIME;
          case NAY -> Sound.BLOCK_NOTE_BLOCK_BASS;
          case ABSTAIN -> Sound.ITEM_BOOK_PAGE_TURN;
        };
    verify(fixture.ui.world).playSound(bob.getLocation(), expected, 1f, 1f);
    verify(bob).swingMainHand();
  }

  @Test
  void chatRevalidatesOnlineMembershipSessionAndDistanceBeforeRecording() {
    Faction faction = faction("home", "Alice", "Bob");
    Player bob = fixture.online.get("Bob");
    manager.onPlayerChat(chat(bob, "ordinary conversation"));
    assertTrue(fixture.ui.tasks.isEmpty());
    Player outsider = player("Stranger");
    manager.onPlayerChat(chat(outsider, "yay"));
    fixture.ui.runTasks();
    verify(outsider, never()).sendMessage(anyString());
    manager.onPlayerChat(chat(bob, "yay"));
    fixture.ui.runTasks();
    verify(bob, never()).sendMessage(anyString());
    manager.newSession(fixture.online.get("Alice"), faction);
    manager.onPlayerChat(chat(bob, "yay"));
    fixture.ui.runTasks();
    verify(bob, never()).sendMessage(anyString());
    Session session = manager.getSession(faction.getGovernment().getCouncil());
    session.onLanternClick(block(Material.LANTERN, 0));
    manager.onPlayerChat(chat(bob, "yay"));
    when(bob.isOnline()).thenReturn(false);
    fixture.ui.runTasks();
    verify(bob, never()).sendMessage(contains("recorded"));
    when(bob.isOnline()).thenReturn(true);
    when(bob.getLocation()).thenReturn(new Location(fixture.ui.world, 10.01, 64, 0));
    manager.onPlayerChat(chat(bob, "yay"));
    fixture.ui.runTasks();
    verify(bob).sendMessage(contains("within 10 blocks"));
    faction.addMember("Cara");
    Player cara = player("Cara");
    manager.onPlayerChat(chat(cara, "yay"));
    fixture.ui.runTasks();
    verify(cara, never()).sendMessage(contains("recorded"));
    assertNotNull(session.getCurrentProposal());
  }

  @Test
  void unrelatedClicksCannotStartASessionAndActiveLanternShowsTheCurrentProposal() {
    Faction faction = faction("home", "Alice", "Bob");
    Player alice = fixture.online.get("Alice");
    Player bob = fixture.online.get("Bob");
    Block lantern = block(Material.LANTERN, 0);
    manager.onPlayerInteract(click(alice, Action.LEFT_CLICK_BLOCK, lantern));
    manager.onPlayerInteract(click(alice, Action.RIGHT_CLICK_BLOCK, null));
    manager.onPlayerInteract(click(alice, Action.RIGHT_CLICK_BLOCK, block(Material.STONE, 0)));
    manager.onPlayerInteract(click(player("Stranger"), Action.RIGHT_CLICK_BLOCK, lantern));
    manager.onPlayerInteract(click(alice, Action.RIGHT_CLICK_BLOCK, lantern));
    assertFalse(manager.hasSession(faction.getGovernment().getCouncil()));
    manager.newSession(alice, faction);
    faction.addMember("Cara");
    Player cara = player("Cara");
    PlayerInteractEvent unauthorized = click(cara, Action.RIGHT_CLICK_BLOCK, lantern);
    manager.onPlayerInteract(unauthorized);
    assertFalse(unauthorized.isCancelled());
    assertFalse(manager.getSession(faction.getGovernment().getCouncil()).isStarted());
    manager.onPlayerInteract(click(bob, Action.RIGHT_CLICK_BLOCK, lantern));
    Session session = manager.getSession(faction.getGovernment().getCouncil());
    assertTrue(session.isStarted());
    PlayerInteractEvent other = click(alice, Action.RIGHT_CLICK_BLOCK, block(Material.LANTERN, 1));
    manager.onPlayerInteract(other);
    assertFalse(other.isCancelled());
    PlayerInteractEvent view = click(bob, Action.RIGHT_CLICK_BLOCK, lantern);
    manager.onPlayerInteract(view);
    assertTrue(view.isCancelled());
    verify(bob)
        .openBook(
            argThat(
                (ItemStack item) ->
                    item.getType() == Material.WRITTEN_BOOK
                        && ((BookMeta) item.getItemMeta())
                            .getPages().stream().anyMatch(page -> page.contains("12.0"))));
    verify(bob).swingMainHand();
    session.recordVote("Alice", Vote.YAY);
    session.recordVote("Bob", Vote.YAY);
    fixture.ui.runTasks();
    PlayerInteractEvent betweenProposals = click(bob, Action.RIGHT_CLICK_BLOCK, lantern);
    manager.onPlayerInteract(betweenProposals);
    assertTrue(betweenProposals.isCancelled());
    verify(bob, times(1)).openBook(any(ItemStack.class));
  }

  @Test
  void unrelatedBlockBreaksAndOtherPlayersQuittingLeaveTheSessionRunning() {
    Faction faction = faction("home", "Alice", "Bob");
    Player bob = fixture.online.get("Bob");
    manager.newSession(fixture.online.get("Alice"), faction);
    manager.onBlockBreak(new BlockBreakEvent(block(Material.STONE, 0), bob));
    manager.onBlockBreak(new BlockBreakEvent(block(Material.LANTERN, 1), bob));
    manager.onPlayerQuit(new PlayerQuitEvent(bob, "quit"));
    assertTrue(manager.hasSession(faction.getGovernment().getCouncil()));
    Session session = manager.getSession(faction.getGovernment().getCouncil());
    session.onLanternClick(block(Material.LANTERN, 0));
    manager.onBlockBreak(new BlockBreakEvent(block(Material.LANTERN, 1), bob));
    assertTrue(session.isStarted());
  }
}
