package net.tfminecraft.simplefactions.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.tfminecraft.rpcharacters.chat.ChatChannel;
import net.tfminecraft.rpcharacters.chat.ChatChannelPreferenceManager;
import net.tfminecraft.rpcharacters.chat.ChatRecipientFilters;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.identity.LeaderCharacters;
import net.tfminecraft.simplefactions.integration.rpcharacters.chat.FactionChatRecipientResolver;
import net.tfminecraft.simplefactions.integration.rpcharacters.chat.GuildChatRecipientResolver;
import net.tfminecraft.simplefactions.rest.BannerFetcher;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.utils.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AdapterBoundaryCoverageTest {
  @TempDir Path directory;

  @Test
  void bannerFetchReleasesItsKeyWhenThePluginDisablesBeforeMainThreadDelivery() throws Exception {
    String key = "adapter-disable-" + UUID.randomUUID();
    try (var ui = new GuiTestFixture();
        var gateway = mockStatic(GatewayClient.class)) {
      AtomicBoolean enabled = new AtomicBoolean(true);
      AtomicInteger delivered = new AtomicInteger();
      when(ui.plugin.isEnabled()).thenAnswer(call -> enabled.get());
      gateway
          .when(() -> GatewayClient.request("GET", "/generator/banner", null))
          .thenReturn(GatewayClient.Result.success("[\"WHITE.BASE\"]"));
      try {
        assertTrue(BannerFetcher.fetch(key, patterns -> delivered.incrementAndGet()));
        enabled.set(false);
        runAsync(ui);
        assertTrue(ui.tasks.isEmpty());
        assertEquals(0, delivered.get());
        enabled.set(true);
        assertTrue(
            BannerFetcher.fetch(key, patterns -> delivered.incrementAndGet()),
            "A dropped callback must not block every future request for the same banner");
        runAsync(ui);
        ui.runTasks();
        assertEquals(1, delivered.get());
      } finally {
        forgetTestRequest(key);
      }
    }
  }

  @Test
  void publicBannerFetchUsesAsyncGatewayAndHoldsDeduplicationUntilMainDelivery() throws Exception {
    String key = "adapter-banner-" + UUID.randomUUID();
    try (var ui = new GuiTestFixture();
        var gateway = mockStatic(GatewayClient.class)) {
      when(ui.plugin.isEnabled()).thenReturn(true);
      gateway
          .when(() -> GatewayClient.request("GET", "/generator/banner", null))
          .thenReturn(GatewayClient.Result.success("[\"RED.BASE\",\"WHITE.CROSS\"]"));
      AtomicReference<List<String>> delivered = new AtomicReference<>();
      try {
        assertTrue(BannerFetcher.fetch(key, delivered::set));
        assertFalse(BannerFetcher.fetch(key, delivered::set));
        gateway.verifyNoInteractions();
        runAsync(ui);
        assertNull(delivered.get());
        assertEquals(1, ui.tasks.size());
        assertFalse(BannerFetcher.fetch(key, delivered::set));
        ui.runTasks();
        assertEquals(List.of("RED.BASE", "WHITE.CROSS"), delivered.get());
        assertTrue(BannerFetcher.fetch(key, delivered::set));
        runAsync(ui);
        ui.runTasks();
        gateway.verify(() -> GatewayClient.request("GET", "/generator/banner", null), times(2));
      } finally {
        forgetTestRequest(key);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unavailablePluginRejectsBannerWorkBeforeSchedulingOrNetworkAccess(boolean absent) {
    try (var ui = new GuiTestFixture();
        var gateway = mockStatic(GatewayClient.class)) {
      when(ui.plugin.isEnabled()).thenReturn(false);
      if (absent) SimpleFactions.plugin = null;
      assertFalse(
          BannerFetcher.fetch("adapter-unavailable", patterns -> fail("Unexpected callback")));
      assertTrue(ui.asyncTasks.isEmpty());
      assertTrue(ui.tasks.isEmpty());
      gateway.verifyNoInteractions();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void organizationChatSkipsInvalidOfflineDuplicateAndUnauthorizedMembers(boolean guild) {
    try (var fixture = new FactionDomainFixture();
        var preferences = mockStatic(ChatChannelPreferenceManager.class)) {
      var faction = fixture.saved("chat", "Alice");
      Player alice = fixture.player("Alice"),
          bob = fixture.player("Bob"),
          offline = fixture.player("Offline"),
          hidden = fixture.player("Hidden"),
          denied = fixture.player("Denied");
      faction
          .getOrCreateMainGuild()
          .getMembers()
          .addAll(Arrays.asList(" ", "Bob", "BOB", "Offline", "Hidden", "Denied"));
      when(Bukkit.getPlayerExact(anyString()))
          .thenAnswer(
              call ->
                  fixture.online.values().stream()
                      .filter(player -> player.getName().equalsIgnoreCase(call.getArgument(0)))
                      .findFirst()
                      .orElse(null));
      when(offline.isOnline()).thenReturn(false);
      for (Player player : List.of(alice, bob, offline, hidden))
        when(player.hasPermission("org.read")).thenReturn(true);
      when(denied.hasPermission("org.read")).thenReturn(false);
      var visibility = mock(ChatChannelPreferenceManager.class);
      when(visibility.isChannelVisible(any(Player.class), eq("organization")))
          .thenAnswer(call -> !call.getArgument(0).equals(hidden));
      preferences.when(ChatChannelPreferenceManager::get).thenReturn(visibility);
      YamlConfiguration config = new YamlConfiguration();
      config.set("read-perm", "org.read");
      ChatChannel channel = new ChatChannel("organization", config);
      Set<Player> result =
          guild
              ? new GuildChatRecipientResolver().resolve(alice, channel)
              : new FactionChatRecipientResolver().resolve(alice, channel);
      assertEquals(Set.of(alice, bob), result);
      verify(visibility, never()).isChannelVisible(eq(offline), anyString());
      verify(visibility, never()).isChannelVisible(eq(denied), anyString());
    }
  }

  @Test
  void organizationResolverSuppliesNoCandidatesBeforeBukkitIsAvailable() {
    try (var fixture = new FactionDomainFixture();
        var filters = mockStatic(ChatRecipientFilters.class)) {
      var faction = fixture.saved("chat", "Alice");
      faction.addMember("Bob");
      var alice = fixture.player("Alice");
      fixture.player("Bob");
      var channel = mock(ChatChannel.class);
      when(Bukkit.getServer()).thenReturn(null);
      filters
          .when(
              () ->
                  ChatRecipientFilters.filterCandidates(
                      same(alice), same(channel), anyCollection()))
          .thenAnswer(call -> Set.copyOf((java.util.Collection<Player>) call.getArgument(2)));
      assertTrue(new FactionChatRecipientResolver().resolve(alice, channel).isEmpty());
      filters.verify(() -> ChatRecipientFilters.filterCandidates(alice, channel, List.of()));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void jsonAtomicFallbackKeepsTheOldSaveOnFailureAndRemovesItsTemporaryFile(boolean fallbackFails)
      throws Exception {
    Path file = directory.resolve("state.json");
    JsonUtil.writeJson(file.toFile(), Map.of("owner", "old"));
    String original = Files.readString(file);
    AtomicInteger atomicAttempts = new AtomicInteger();
    AtomicInteger fallbackAttempts = new AtomicInteger();
    try (var files =
        mockStatic(
            Files.class,
            call -> {
              if (call.getMethod().getName().equals("move")) {
                Object[] args = call.getRawArguments();
                if (file.toAbsolutePath().equals(args[1])) {
                  CopyOption[] options = (CopyOption[]) args[2];
                  if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                    atomicAttempts.incrementAndGet();
                    throw new AtomicMoveNotSupportedException(
                        args[0].toString(), args[1].toString(), "filesystem boundary");
                  }
                  fallbackAttempts.incrementAndGet();
                  if (fallbackFails) throw new IOException("replacement failed");
                }
              }
              return call.callRealMethod();
            })) {
      if (fallbackFails)
        assertEquals(
            "replacement failed",
            assertThrows(
                    IOException.class,
                    () -> JsonUtil.writeJsonAtomic(file.toFile(), Map.of("owner", "new")))
                .getMessage());
      else JsonUtil.writeJsonAtomic(file.toFile(), Map.of("owner", "new"));
    }
    assertEquals(1, atomicAttempts.get());
    assertEquals(1, fallbackAttempts.get());
    if (fallbackFails) assertEquals(original, Files.readString(file));
    assertEquals(
        fallbackFails ? "old" : "new", JsonUtil.readJson(file.toFile(), Map.class).get("owner"));
    try (var files = Files.list(directory)) {
      assertEquals(List.of(file), files.toList());
    }
  }

  @Test
  void disablingCharacterIntegrationClearsRememberedNamesWithoutChangingOwnership() {
    try (var fixture = new FactionDomainFixture()) {
      var faction = fixture.saved("realm", "Alice");
      LeaderCharacters.setProbe(player -> "§aLady Rowan");
      assertTrue(LeaderCharacters.refresh(faction));
      assertEquals("Lady Rowan", faction.getLeaderCharacter());
      LeaderCharacters.reset();
      assertTrue(LeaderCharacters.refresh(faction));
      assertNull(faction.getLeaderCharacter());
      assertNull(faction.getLeaderCharacterOf());
      assertEquals("Alice", faction.getLeader());
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
  void unavailableOrNonFiniteAmountsCannotEscapeIntoMoneyDisplays(Double amount) {
    assertEquals(0.0, Formatter.formatDouble(amount));
    if (amount != null) assertEquals("0.00", Formatter.formatMoney(amount));
  }

  private void runAsync(GuiTestFixture ui) {
    var tasks = new ArrayList<>(ui.asyncTasks);
    ui.asyncTasks.clear();
    tasks.forEach(Runnable::run);
  }

  @SuppressWarnings("unchecked")
  private void forgetTestRequest(String key) throws Exception {
    // Restore only the test's own global request after a failing regression; never invoke private
    // code.
    var field = BannerFetcher.class.getDeclaredField("inFlight");
    field.setAccessible(true);
    ((Set<String>) field.get(null)).remove(key);
  }
}
