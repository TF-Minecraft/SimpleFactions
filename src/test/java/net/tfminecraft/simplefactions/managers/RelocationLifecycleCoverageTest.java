package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.RelocateRequest;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.utils.DisplayNameGate;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation")
class RelocationLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Faction host, other;
  private Guild guild;
  private Player player, receiver;
  private RelocationPrompt listener;
  private final List<Runnable> deadlines = new ArrayList<>();
  private final Map<Map<Object, Object>, Map<Object, Object>> globals = new IdentityHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    fixture.provincesEnabled(true);
    for (var entry :
        List.of(
            Map.entry(RelocationPrompt.class, "pending"),
            Map.entry(DisplayNameGate.class, "pending"),
            Map.entry(RequestManager.class, "requests"))) {
      Field field = entry.getKey().getDeclaredField(entry.getValue());
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<Object, Object> map = (Map<Object, Object>) field.get(null);
      globals.put(map, new LinkedHashMap<>(map));
      map.clear();
    }
    var data = fixture.data("host", "Ruler");
    data.provinces.addAll(List.of(1, 2, 3));
    host = fixture.saved(data);
    other = fixture.saved("other", "Bob");
    for (int id : List.of(1, 2, 3, 4, 5))
      fixture.provinceData.put(id, new Province(id, "PLAINS", 50));
    guild = fixture.guild(host, "traders", "Alice");
    guild.setCapital(1);
    guild.getBank().setWealth(100.0);
    host.getSettlementHandler().found("Old_City", 1, 0, 0);
    player = fixture.player("Alice");
    receiver = fixture.player("Bob");
    listener = new RelocationPrompt();
    when(fixture.ui.scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
        .thenAnswer(
            call -> {
              deadlines.add(call.getArgument(1));
              BukkitTask task = mock(BukkitTask.class);
              when(task.getTaskId()).thenReturn(deadlines.size());
              return task;
            });
    fixture.ui.tasks.clear();
  }

  @AfterEach
  void close() {
    try {
      globals.forEach(
          (map, old) -> {
            map.clear();
            map.putAll(old);
          });
    } finally {
      fixture.close();
    }
  }

  private AsyncPlayerChatEvent chat(String text) {
    var event = new AsyncPlayerChatEvent(true, player, text, Set.of());
    listener.onPlayerChat(event);
    return event;
  }

  private void begin(int province) {
    assertTrue(RelocationPrompt.begin(player, guild, host, province, false, 20));
  }

  @Test
  void expiredOldPromptCannotRemoveItsReplacement() {
    begin(2);
    Runnable old = deadlines.getFirst();
    begin(3);
    clearInvocations(player);
    old.run();
    assertTrue(chat("New_City").isCancelled());
    fixture.ui.runTasks();
    assertEquals(3, guild.getCapital());
    assertEquals(80.0, guild.getBank().getWealth());
    verify(player, never()).sendMessage("§cRelocation timed out");
  }

  @Test
  void anOldQueuedAnswerCannotApplyAfterAReplacementPrompt() {
    begin(2);
    assertTrue(chat("First_City").isCancelled());
    begin(3);
    assertTrue(chat("Second_City").isCancelled());
    fixture.ui.runTasks();
    assertEquals(3, guild.getCapital());
    assertEquals(80.0, guild.getBank().getWealth());
    assertNull(host.getSettlementHandler().getByProvince(2));
  }

  @Test
  void leadershipMustStillBelongToThePersonAnswering() {
    begin(2);
    guild.setLeader("Cara");
    assertTrue(chat("New_City").isCancelled());
    fixture.ui.runTasks();
    assertEquals(1, guild.getCapital());
    assertEquals(100.0, guild.getBank().getWealth());
    assertNull(host.getSettlementHandler().getByProvince(2));
  }

  @Test
  void moneySpentWhilePromptWasOpenCannotBeSpentAgain() {
    begin(2);
    guild.getBank().setWealth(5.0);
    assertTrue(chat("New_City").isCancelled());
    fixture.ui.runTasks();
    assertEquals(1, guild.getCapital());
    assertEquals(5.0, guild.getBank().getWealth());
    assertNull(host.getSettlementHandler().getByProvince(2));
  }

  @Test
  void aRejectedNameMustPreserveTheExistingCapitalAndCity() {
    var old = host.getSettlementHandler().getByProvince(1);
    host.getSettlementHandler().found("Taken", 3, 0, 0);
    assertFalse(RelocationPrompt.completeIntraFactionRelocate(player, guild, host, 2, "Taken", 20));
    assertEquals(1, guild.getCapital());
    assertSame(old, host.getSettlementHandler().getByProvince(1));
    assertNull(host.getSettlementHandler().getByProvince(2));
    assertEquals(100.0, guild.getBank().getWealth());
  }

  @Test
  void ordinaryChatRemainsPublicAndAnExistingCityNeedsNoPrompt() {
    assertFalse(RelocationPrompt.begin(player, guild, host, 1, false, 20));
    assertFalse(chat("ordinary conversation").isCancelled());
    assertTrue(deadlines.isEmpty());
  }

  @Test
  void validRelocationUsesTheMainThreadAndChargesOnlyOnce() {
    begin(2);
    assertTrue(chat("  New_City  ").isCancelled());
    assertEquals(1, guild.getCapital());
    assertEquals(100.0, guild.getBank().getWealth());
    fixture.ui.runTasks();
    assertEquals(2, guild.getCapital());
    assertEquals(80.0, guild.getBank().getWealth());
    assertNotNull(host.getSettlementHandler().getByProvince(2));
    assertNull(host.getSettlementHandler().getByProvince(1));
    verify(fixture.inventory).guildView(player, guild);
    assertFalse(chat("ordinary conversation").isCancelled());
  }

  @Test
  void blankInputKeepsThePromptAvailable() {
    begin(2);
    assertTrue(chat("  ").isCancelled());
    fixture.ui.runTasks();
    verify(player).sendMessage("§cA city name is required to relocate here");
    assertTrue(chat("New_City").isCancelled());
    fixture.ui.runTasks();
    assertEquals(2, guild.getCapital());
  }

  @Test
  void softNameWarningRequiresTheSameNameTwice() {
    begin(2);
    assertTrue(chat("new_city").isCancelled());
    fixture.ui.runTasks();
    assertEquals(1, guild.getCapital());
    assertTrue(chat("new_city").isCancelled());
    fixture.ui.runTasks();
    assertEquals(2, guild.getCapital());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void timeoutNotifiesOnlyAnOnlinePlayer(boolean online) {
    begin(2);
    when(player.isOnline()).thenReturn(online);
    deadlines.getFirst().run();
    assertFalse(chat("New_City").isCancelled());
    verify(player, times(online ? 1 : 0)).sendMessage("§cRelocation timed out");
    assertEquals(1, guild.getCapital());
  }

  @Test
  void quitCancelsThePromptAndAlreadyQueuedWork() {
    begin(2);
    chat("New_City");
    listener.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
    fixture.ui.runTasks();
    assertEquals(1, guild.getCapital());
    assertEquals(100.0, guild.getBank().getWealth());
    assertFalse(chat("late").isCancelled());
  }

  @Test
  void disconnectBeforeApplicationDoesNotMoveTheGuild() {
    begin(2);
    chat("New_City");
    when(player.isOnline()).thenReturn(false);
    fixture.ui.runTasks();
    assertEquals(1, guild.getCapital());
    assertEquals(100.0, guild.getBank().getWealth());
  }

  @Test
  void crossFactionPromptSendsTheNamedRequestWithoutChargingOrMoving() {
    assertTrue(RelocationPrompt.begin(player, guild, other, 4, true, 20));
    chat("Far_City");
    fixture.ui.runTasks();
    var request = assertInstanceOf(RelocateRequest.class, RequestManager.getRequest(receiver));
    assertSame(guild, request.getSender());
    assertEquals(4, request.getNewCapital());
    assertEquals("Far_City", request.getSettlementName());
    assertSame(host, guild.getFaction());
    assertEquals(1, guild.getCapital());
    assertEquals(100.0, guild.getBank().getWealth());
  }

  @Test
  void failedClaimRestoresTheOldCapitalWithoutCharging() {
    var old = host.getSettlementHandler().getByProvince(1);
    assertFalse(
        RelocationPrompt.completeIntraFactionRelocate(player, guild, host, 4, "New_City", 20));
    assertEquals(1, guild.getCapital());
    assertSame(old, host.getSettlementHandler().getByProvince(1));
    assertEquals(100.0, guild.getBank().getWealth());
    verify(fixture.map).claim(player, host, 4, true);
  }

  @Test
  void successfulClaimMovesAndChargesAfterFounding() {
    doAnswer(
            call -> {
              host.addProvince(4);
              return null;
            })
        .when(fixture.map)
        .claim(player, host, 4, true);
    assertTrue(
        RelocationPrompt.completeIntraFactionRelocate(player, guild, host, 4, "New_City", 20));
    assertEquals(4, guild.getCapital());
    assertNotNull(host.getSettlementHandler().getByProvince(4));
    assertEquals(80.0, guild.getBank().getWealth());
  }

  @Test
  void directRelocationRequiresTheCurrentGuildLeader() {
    Player outsider = fixture.player("Outsider");
    assertFalse(
        RelocationPrompt.completeIntraFactionRelocate(outsider, guild, host, 2, "New_City", 20));
    assertEquals(1, guild.getCapital());
    assertEquals(100.0, guild.getBank().getWealth());
    assertNull(host.getSettlementHandler().getByProvince(2));
  }

  @Test
  void successfulNewClaimAlsoRemovesTheVacatedCity() {
    doAnswer(
            call -> {
              host.addProvince(4);
              return null;
            })
        .when(fixture.map)
        .claim(player, host, 4, true);
    assertTrue(
        RelocationPrompt.completeIntraFactionRelocate(player, guild, host, 4, "New_City", 20));
    assertNull(host.getSettlementHandler().getByProvince(1));
    assertNotNull(host.getSettlementHandler().getByProvince(4));
  }

  @Test
  void anInvalidNameCannotClaimLandBeforeRelocationIsRejected() {
    host.getSettlementHandler().found("Taken", 3, 0, 0);
    doAnswer(
            call -> {
              host.addProvince(4);
              return null;
            })
        .when(fixture.map)
        .claim(player, host, 4, true);
    assertFalse(RelocationPrompt.completeIntraFactionRelocate(player, guild, host, 4, "Taken", 20));
    assertFalse(host.hasProvince(4));
    assertEquals(1, guild.getCapital());
    assertEquals(100.0, guild.getBank().getWealth());
    verify(fixture.map, never()).claim(player, host, 4, true);
  }
}
