package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.utils.DisplayNameGate.NameOperation;
import net.tfminecraft.simplefactions.utils.DisplayNameGate.Result;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DisplayNameLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Player player;
  private Map<Object, Object> pending;
  private Map<Object, Object> original;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    player = fixture.player("Alice");
    var field = DisplayNameGate.class.getDeclaredField("pending");
    field.setAccessible(true);
    pending = (Map<Object, Object>) field.get(null);
    original = new LinkedHashMap<>(pending);
    pending.clear();
  }

  @AfterEach
  void close() {
    try {
      pending.clear();
      pending.putAll(original);
    } finally {
      fixture.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"lowerCamel", "UpperCamel", "lower_name"})
  void repeatingTheSameNameConfirmsAllOfItsSoftWarnings(String name) {
    assertEquals(
        Result.NEEDS_CONFIRM, DisplayNameGate.check(player, NameOperation.FACTION_CREATE, name));
    assertEquals(Result.OK, DisplayNameGate.check(player, NameOperation.FACTION_CREATE, name));
    assertTrue(pending.isEmpty());
  }

  @Test
  void aStaleTimeoutDoesNotClearTheNewerNameConfirmation() {
    assertEquals(
        Result.NEEDS_CONFIRM,
        DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "UpperCamel"));
    Runnable old = fixture.ui.tasks.removeLast();
    assertEquals(
        Result.NEEDS_CONFIRM,
        DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "OtherCamel"));
    old.run();
    assertEquals(
        Result.OK, DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "OtherCamel"));
  }

  @Test
  void theCurrentTimeoutExpiresTheNameConfirmation() {
    DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "UpperCamel");
    fixture.ui.tasks.removeLast().run();
    assertEquals(
        Result.NEEDS_CONFIRM,
        DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "UpperCamel"));
  }

  @Test
  void changingOperationsOrQuittingRequiresFreshConsent() {
    DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "lower_name", true);
    verify(player).sendMessage("§7Type the same name again in chat to keep it anyway.");
    assertEquals(
        Result.NEEDS_CONFIRM,
        DisplayNameGate.check(player, NameOperation.GUILD_CREATE, "lower_name", false));
    new DisplayNameGate().onPlayerQuit(new PlayerQuitEvent(player, "quit"));
    assertEquals(
        Result.NEEDS_CONFIRM,
        DisplayNameGate.check(player, NameOperation.GUILD_CREATE, "lower_name"));
  }

  @Test
  void wellFormedNamesClearPreviousWarningsAndEmptyFormattingIsSafe() {
    DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "lower_name");
    assertEquals(
        Result.OK, DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "Upper_Name"));
    assertTrue(pending.isEmpty());
    assertEquals(Result.OK, DisplayNameGate.check(null, NameOperation.FACTION_CREATE, "lower"));
    assertEquals(Result.OK, DisplayNameGate.check(player, null, "lower"));
    assertFalse(DisplayNameGate.looksLikeMissingSpaces(null));
    assertTrue(DisplayNameGate.findUncapitalizedWord(null).isEmpty());
    assertTrue(DisplayNameGate.findUncapitalizedWord("   ").isEmpty());
    assertTrue(DisplayNameGate.findUncapitalizedWord("§a").isEmpty());
    assertTrue(DisplayNameGate.findUncapitalizedWord("1_Upper_Name").isEmpty());
    assertEquals("", DisplayNameGate.suggestUnderscores(null));
  }
}
