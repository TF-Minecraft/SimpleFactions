package net.tfminecraft.simplefactions.war.declare;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation")
class DeclareCodePromptCoverageTest {
  private record Timed(Runnable callback, long ticks) {}

  private FactionDomainFixture fixture;
  private InventoryManager inventory;
  private DeclareCodePrompt listener;
  private Faction attacker;
  private Faction defender;
  private Faction alternative;
  private Player player;
  private final List<Timed> timed = new ArrayList<>();
  private final List<JsonObject> requests = new ArrayList<>();
  private GatewayClient.Result response;
  private WarDeclareCodeService.Gateway previousGateway;
  private Map<Object, Object> previousPending;
  private Map<Object, Object> previousSessions;
  private int previousTimeout;
  private Map<String, String> previousIcons;

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    fixture.provincesEnabled(true);
    previousTimeout = Cache.warDeclareCodeTimeoutSeconds;
    Cache.warDeclareCodeTimeoutSeconds = 5;
    previousIcons = new LinkedHashMap<>(Cache.icons);
    Cache.icons.clear();
    Cache.icons.put("war", "PAPER.1");
    previousGateway =
        (WarDeclareCodeService.Gateway) field(WarDeclareCodeService.class, "gateway").get(null);
    previousPending = new LinkedHashMap<>(map(DeclareCodePrompt.class, "pending"));
    previousSessions = new LinkedHashMap<>(map(WarDeclareCodeService.class, "sessions"));
    map(DeclareCodePrompt.class, "pending").clear();
    map(WarDeclareCodeService.class, "sessions").clear();
    response = GatewayClient.Result.success("{\"goal\":\"war\"}");
    WarDeclareCodeService.setGateway(
        (path, body) -> {
          assertEquals("/wars/declare-codes/validate", path);
          requests.add(JsonParser.parseString(body).getAsJsonObject());
          return response;
        });
    when(fixture.ui.scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
        .thenAnswer(
            call -> {
              timed.add(new Timed(call.getArgument(1), call.getArgument(2)));
              BukkitTask task = mock(BukkitTask.class);
              when(task.getTaskId()).thenReturn(timed.size());
              return task;
            });
    inventory = new InventoryManager();
    field(FactionManager.class, "inv").set(null, inventory);
    listener = new DeclareCodePrompt();
    attacker = fixture.saved("attacker", "Alice");
    defender = fixture.saved("defender", "Bob");
    alternative = fixture.saved("alternative", "Cara");
    player = fixture.player("Alice");
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (previousPending != null) {
        map(DeclareCodePrompt.class, "pending").clear();
        map(DeclareCodePrompt.class, "pending").putAll(previousPending);
      }
      if (previousSessions != null) {
        map(WarDeclareCodeService.class, "sessions").clear();
        map(WarDeclareCodeService.class, "sessions").putAll(previousSessions);
      }
      if (previousGateway != null) WarDeclareCodeService.setGateway(previousGateway);
      Cache.warDeclareCodeTimeoutSeconds = previousTimeout;
      if (previousIcons != null) {
        Cache.icons.clear();
        Cache.icons.putAll(previousIcons);
      }
    } finally {
      if (fixture != null) fixture.close();
    }
  }

  private static Field field(Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> map(Class<?> owner, String name) throws Exception {
    return (Map<Object, Object>) field(owner, name).get(null);
  }

  private AsyncPlayerChatEvent submit(String code) {
    AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, player, code, Set.of());
    listener.onPlayerChat(event);
    fixture.ui.runTasks();
    return event;
  }

  @Test
  void aValidCodeIsCheckedOffThreadAndOnlyOpensItsPinnedConfirmationOnTheMainCallback() {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("  chosen-code  ").isCancelled());
    assertTrue(requests.isEmpty());
    assertNull(WarDeclareCodeService.session(player));
    fixture.ui.asyncTasks.removeFirst().run();
    assertEquals(1, requests.size());
    assertEquals("chosen-code", requests.getFirst().get("code").getAsString());
    assertEquals(attacker.getId(), requests.getFirst().get("attacker_faction_id").getAsString());
    assertEquals(defender.getId(), requests.getFirst().get("defender_faction_id").getAsString());
    assertNull(WarDeclareCodeService.session(player));
    assertFalse(inventory.pendingWarDeclares.containsKey(player));

    fixture.ui.runTasks();

    assertEquals(WarGoalType.WAR, WarDeclareCodeService.session(player).goal);
    assertEquals("chosen-code", WarDeclareCodeService.session(player).code);
    assertSame(defender, inventory.pendingWarDeclares.get(player).getDefender());
    assertEquals(WarGoalType.WAR, inventory.pendingWarDeclares.get(player).getGoal());
    assertEquals(
        "defender",
        fixture.ui.data(player.getOpenInventory().getTopInventory().getItem(11), "war_declare"));
  }

  @Test
  void anEarlierPromptTimeoutCannotCancelItsReplacement() {
    DeclareCodePrompt.begin(player, attacker, defender);
    Timed oldTimeout = timed.getLast();
    DeclareCodePrompt.begin(player, attacker, alternative);
    clearInvocations(player);

    oldTimeout.callback().run();

    AsyncPlayerChatEvent next = submit("replacement-code");
    assertAll(
        () -> assertTrue(next.isCancelled()),
        () -> assertEquals(1, fixture.ui.asyncTasks.size()),
        () -> verify(player, never()).sendMessage("§cWar declaration timed out"));
  }

  @Test
  void anOlderGatewayReplyCannotReplaceTheAcceptedNewerPairing() {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("old-code").isCancelled());
    Runnable oldRequest = fixture.ui.asyncTasks.removeFirst();
    DeclareCodePrompt.begin(player, attacker, alternative);
    assertTrue(submit("new-code").isCancelled());
    fixture.ui.asyncTasks.removeFirst().run();
    fixture.ui.runTasks();
    assertEquals("new-code", WarDeclareCodeService.session(player).code);
    assertSame(alternative, inventory.pendingWarDeclares.get(player).getDefender());

    oldRequest.run();
    fixture.ui.runTasks();

    assertEquals("new-code", WarDeclareCodeService.session(player).code);
    assertSame(alternative, inventory.pendingWarDeclares.get(player).getDefender());
  }

  @Test
  void cancellingAReplacementAlsoInvalidatesTheOlderInFlightCode() {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("old-code").isCancelled());
    Runnable oldRequest = fixture.ui.asyncTasks.removeFirst();
    DeclareCodePrompt.begin(player, attacker, alternative);
    assertTrue(submit("cancel").isCancelled());
    verify(player).sendMessage("§7War declaration cancelled");

    oldRequest.run();
    fixture.ui.runTasks();

    assertNull(WarDeclareCodeService.session(player));
    assertFalse(inventory.pendingWarDeclares.containsKey(player));
  }

  @Test
  void absentInputsDoNotPromptAndOrdinaryChatRemainsPublic() {
    DeclareCodePrompt.begin(null, attacker, defender);
    DeclareCodePrompt.begin(player, null, defender);
    DeclareCodePrompt.begin(player, attacker, null);

    assertTrue(timed.isEmpty());
    assertFalse(submit("ordinary chat").isCancelled());
    assertTrue(fixture.ui.asyncTasks.isEmpty());
    verify(player, never()).closeInventory();
  }

  @ParameterizedTest
  @ValueSource(strings = {"   ", " CaNcEl "})
  void blankAndCancelConsumeOnlyThePromptWithoutContactingTheGateway(String input) {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit(input).isCancelled());
    verify(player)
        .sendMessage(
            input.isBlank()
                ? "§cA war code is required to declare here"
                : "§7War declaration cancelled");
    assertFalse(submit("ordinary chat").isCancelled());
    timed.getFirst().callback().run();
    assertTrue(requests.isEmpty());
    assertTrue(fixture.ui.asyncTasks.isEmpty());
    assertNull(WarDeclareCodeService.session(player));
    verify(player, never()).sendMessage("§cWar declaration timed out");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void anUnsubmittedPromptExpiresAndOnlyNotifiesAnOnlinePlayer(boolean online) {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertEquals(1200L, timed.getFirst().ticks());
    when(player.isOnline()).thenReturn(online);

    timed.getFirst().callback().run();

    assertFalse(submit("too late").isCancelled());
    verify(player, times(online ? 1 : 0)).sendMessage("§cWar declaration timed out");
    assertTrue(fixture.ui.asyncTasks.isEmpty());
  }

  @Test
  void aSubmittedCodeSurvivesTheInputDeadlineAndDoesNotCaptureUnrelatedChat() {
    DeclareCodePrompt.begin(player, attacker, defender);
    Timed inputDeadline = timed.getFirst();
    assertTrue(submit("valid-code").isCancelled());
    assertFalse(submit("still chatting").isCancelled());
    inputDeadline.callback().run();

    fixture.ui.asyncTasks.removeFirst().run();
    fixture.ui.runTasks();
    timed.getLast().callback().run();

    assertEquals("valid-code", WarDeclareCodeService.session(player).code);
    assertSame(defender, inventory.pendingWarDeclares.get(player).getDefender());
    assertEquals(1, requests.size());
    verify(player, never()).sendMessage("§cWar declaration timed out");
    verify(player, never()).sendMessage("§cThe war code service did not answer in time.");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aGatewayTimeoutConsumesTheAttemptAndRejectsItsLateReply(boolean online) {
    Cache.warDeclareCodeTimeoutSeconds = 0;
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("slow-code").isCancelled());
    Timed serviceDeadline = timed.getLast();
    assertEquals(20L, serviceDeadline.ticks());
    when(player.isOnline()).thenReturn(online);

    serviceDeadline.callback().run();
    fixture.ui.asyncTasks.removeFirst().run();
    fixture.ui.runTasks();

    assertNull(WarDeclareCodeService.session(player));
    assertFalse(inventory.pendingWarDeclares.containsKey(player));
    verify(player, times(online ? 1 : 0))
        .sendMessage("§cThe war code service did not answer in time.");
    assertFalse(submit("ordinary chat").isCancelled());
  }

  @Test
  void anOfflinePlayerDoesNotReceiveOrOpenAGatewayResult() {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("valid-code").isCancelled());
    fixture.ui.asyncTasks.removeFirst().run();
    when(player.isOnline()).thenReturn(false);
    clearInvocations(player);

    fixture.ui.runTasks();

    assertNull(WarDeclareCodeService.session(player));
    assertFalse(inventory.pendingWarDeclares.containsKey(player));
    verify(player, never()).sendMessage(anyString());
  }

  @Test
  void rejectedCodesReportTheBackendReasonWithoutGrantingADeclarationSession() {
    response = GatewayClient.Result.fail("This code has expired");
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("expired-code").isCancelled());
    fixture.ui.asyncTasks.removeFirst().run();
    fixture.ui.runTasks();

    verify(player).sendMessage("§cThis code has expired");
    verify(player).playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    assertNull(WarDeclareCodeService.session(player));
    assertFalse(inventory.pendingWarDeclares.containsKey(player));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deletingEitherFactionDuringValidationDoesNotOpenAStaleConfirmation(boolean deleteAttacker) {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("valid-code").isCancelled());
    FactionManager.factions.remove(deleteAttacker ? attacker : defender);
    fixture.ui.asyncTasks.removeFirst().run();
    fixture.ui.runTasks();

    verify(player).sendMessage("§cThat faction no longer exists.");
    assertNull(WarDeclareCodeService.session(player));
    assertFalse(inventory.pendingWarDeclares.containsKey(player));
  }

  @Test
  void leavingInvalidatesInFlightValidationEvenBeforeTheOnlineFlagChanges() {
    DeclareCodePrompt.begin(player, attacker, defender);
    assertTrue(submit("valid-code").isCancelled());
    WarDeclareCodeService.openSession(
        player,
        new WarDeclareCodeService.Session(
            "previous", attacker.getId(), defender.getId(), WarGoalType.WAR));

    listener.onPlayerQuit(new PlayerQuitEvent(player, "left"));
    fixture.ui.asyncTasks.removeFirst().run();
    fixture.ui.runTasks();

    assertNull(WarDeclareCodeService.session(player));
    assertFalse(inventory.pendingWarDeclares.containsKey(player));
    assertFalse(submit("ordinary chat").isCancelled());
  }

  @Test
  void startingANewPromptClearsPreviouslyAcceptedCodes() {
    WarDeclareCodeService.openSession(
        player,
        new WarDeclareCodeService.Session(
            "previous", attacker.getId(), defender.getId(), WarGoalType.WAR));

    DeclareCodePrompt.begin(player, attacker, alternative);

    assertNull(WarDeclareCodeService.session(player));
    assertTrue(submit("cancel").isCancelled());
  }
}
