package net.tfminecraft.simplefactions.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.rpcharacters.chat.*;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.playtime.PlaytimeService;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.identity.RpCharactersLeaderCharacterProbe;
import net.tfminecraft.simplefactions.integration.rpcharacters.chat.*;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.prestige.RpCharactersPlaytimeProbe;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.freeze.VfBuildersConstructionFreeze;
import net.tfminecraft.vfbuilders.api.ConstructionFreeze;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class IntegrationBoundaryCoverageTest {
  private FactionDomainFixture rig;
  @TempDir Path folder;

  @BeforeEach
  void setup() {
    rig = new FactionDomainFixture();
  }

  @AfterEach
  void close() {
    if (rig != null) rig.close();
  }

  @Test
  void constructionProviderHoldsOnlyProjectsWhoseFactionBattleIsPostponed() {
    var owner = rig.saved("builder_owner", "Builder");
    var enemy = rig.saved("builder_enemy", "Enemy");
    Player builder = rig.player("Builder");
    when(Bukkit.getOfflinePlayer(builder.getUniqueId())).thenReturn(builder);
    ServicesManager services = mock(ServicesManager.class);
    when(Bukkit.getServicesManager()).thenReturn(services);
    VfBuildersConstructionFreeze.register(rig.ui.plugin);
    ArgumentCaptor<ConstructionFreeze> provider = ArgumentCaptor.forClass(ConstructionFreeze.class);
    verify(services)
        .register(
            eq(ConstructionFreeze.class),
            provider.capture(),
            same(rig.ui.plugin),
            eq(ServicePriority.Normal));
    assertNull(provider.getValue().freezeReason(builder.getUniqueId()));
    War war = new War(813477, owner, enemy);
    WarManager.addWar(war);
    war.setPreparationFrozenUntil(Instant.now().plusSeconds(3600));
    assertTrue(
        provider.getValue().freezeReason(builder.getUniqueId()).startsWith("battle postponed ("));
    war.setPreparationFrozenUntil(Instant.now().minusSeconds(1));
    assertNull(provider.getValue().freezeReason(builder.getUniqueId()));
  }

  @Test
  void chatHooksRegisterTheCorrectResolversOnlyWhenTheProviderIsEnabled() {
    Map<String, ChatRecipientResolver> registered = new LinkedHashMap<>();
    try (var registry = mockStatic(ChatRecipientResolverRegistry.class);
        var filters = mockStatic(ChatRecipientFilters.class)) {
      registry
          .when(
              () ->
                  ChatRecipientResolverRegistry.register(
                      anyString(), any(ChatRecipientResolver.class)))
          .thenAnswer(
              call -> {
                registered.put(call.getArgument(0), call.getArgument(1));
                return null;
              });
      registry
          .when(() -> ChatRecipientResolverRegistry.unregister(anyString()))
          .thenAnswer(
              call -> {
                registered.remove(call.getArgument(0));
                return null;
              });
      var plugins = Bukkit.getPluginManager();
      when(Bukkit.getServer()).thenReturn(null);
      RpCharactersChatIntegration.register();
      when(Bukkit.getServer()).thenReturn(rig.ui.server);
      when(Bukkit.getPluginManager()).thenReturn(null);
      RpCharactersChatIntegration.register();
      when(Bukkit.getPluginManager()).thenReturn(plugins);
      RpCharactersChatIntegration.register();
      assertTrue(registered.isEmpty());
      when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
      RpCharactersChatIntegration.register();
      assertEquals(Set.of("simplefactions:guild", "simplefactions:faction"), registered.keySet());
      assertInstanceOf(GuildChatRecipientResolver.class, registered.get("simplefactions:guild"));
      assertInstanceOf(
          FactionChatRecipientResolver.class, registered.get("simplefactions:faction"));
      var faction = rig.saved("chat_owner", "Speaker");
      faction.getOrCreateMainGuild().addMember("Listener");
      Player speaker = rig.player("Speaker"), listener = rig.player("Listener");
      ChatChannel channel = mock(ChatChannel.class);
      filters
          .when(() -> ChatRecipientFilters.filterCandidates(any(), any(), any()))
          .thenAnswer(call -> Set.copyOf((java.util.Collection<Player>) call.getArgument(2)));
      for (var resolver : registered.values()) {
        assertEquals(Set.of(speaker, listener), resolver.resolve(speaker, channel));
        assertTrue(resolver.resolve(null, channel).isEmpty());
        assertTrue(resolver.resolve(rig.player("Outsider"), channel).isEmpty());
      }
      RpCharactersChatIntegration.unregister();
      assertTrue(registered.isEmpty());
    }
  }

  @Test
  void leaderNamesUseLiveCharactersOrCachedLocalFilesWithoutRemoteLookup() throws Exception {
    var plugins = Bukkit.getPluginManager();
    var probe = new RpCharactersLeaderCharacterProbe();
    assertNull(probe.activeCharacterName("Online"));
    when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
    Player player = rig.player("Online");
    try (var players = mockStatic(PlayerManager.class)) {
      assertNull(probe.activeCharacterName("Online"));
      PlayerData data = mock(PlayerData.class);
      players.when(() -> PlayerManager.get(player)).thenReturn(data);
      assertNull(probe.activeCharacterName("Online"));
      when(data.hasActiveCharacter()).thenReturn(true);
      assertNull(probe.activeCharacterName("Online"));
      RPCharacter character = mock(RPCharacter.class);
      when(character.getName()).thenReturn("Aelin of the Reach");
      when(data.getActiveCharacter()).thenReturn(character);
      assertEquals("Aelin of the Reach", probe.activeCharacterName("Online"));
    }
    when(Bukkit.getOfflinePlayer(anyString()))
        .thenThrow(new AssertionError("Remote lookup is forbidden"));
    assertNull(probe.activeCharacterName("Uncached"));
    OfflinePlayer offline = mock(OfflinePlayer.class);
    UUID uuid = UUID.randomUUID();
    when(offline.getUniqueId()).thenReturn(uuid);
    when(Bukkit.getOfflinePlayerIfCached("Cached")).thenReturn(offline);
    Path directory = Path.of("plugins/RPCharacters/data/characterdata", uuid.toString());
    Path saved = directory.resolve("active.json");
    byte[] previous =
        Files.exists(saved, LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(saved) : null;
    List<Path> createdDirectories = new ArrayList<>();
    for (Path ancestor = directory;
        ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS);
        ancestor = ancestor.getParent()) {
      createdDirectories.add(ancestor);
    }
    try {
      Files.createDirectories(directory);
      Files.writeString(saved, "{\"active\":true,\"name\":\"Brann\"}");
      assertEquals("Brann", probe.activeCharacterName("Cached"));
    } finally {
      if (previous == null) Files.deleteIfExists(saved);
      else Files.write(saved, previous);
      for (Path created : createdDirectories) {
        if (!Files.isDirectory(created, LinkOption.NOFOLLOW_LINKS)) continue;
        try {
          Files.deleteIfExists(created);
        } catch (DirectoryNotEmptyException retained) {
          // Preserve contents added by another fixture; ancestors are visited deepest first.
        }
      }
    }
  }

  @Test
  void playtimeIntegrationReturnsUnknownWhenDisabledAndTheProvidersValueWhenEnabled() {
    var probe = new RpCharactersPlaytimeProbe();
    assertNull(probe.secondsFor("Offline"));
    when(Bukkit.getPluginManager().isPluginEnabled("RPCharacters")).thenReturn(true);
    try (var playtime = mockStatic(PlaytimeService.class)) {
      playtime.when(() -> PlaytimeService.getSeconds("Offline")).thenReturn(7200);
      assertEquals(7200, probe.secondsFor("Offline"));
      playtime.verify(() -> PlaytimeService.getSeconds("Offline"));
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "[]",
        "{\"settlements\":[],\"installations\":{}}",
        "{\"settlements\":[],\"forts\":{}}"
      })
  void malformedMapMarkersCannotReachTheGateway(String invalid) throws Exception {
    boolean previous = Cache.mapEnabled;
    Cache.mapEnabled = true;
    var logger = mock(java.util.logging.Logger.class);
    when(rig.ui.plugin.getLogger()).thenReturn(logger);
    Path file = Files.writeString(folder.resolve("markers.json"), invalid);
    try (var gateway = mockStatic(GatewayClient.class)) {
      RestServer.upload("map_markers", file.toFile());
      gateway.verifyNoInteractions();
      ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
      verify(logger)
          .log(
              eq(java.util.logging.Level.WARNING),
              eq("Upload failed for map_markers"),
              failure.capture());
      assertInstanceOf(IllegalStateException.class, failure.getValue());
    } finally {
      Cache.mapEnabled = previous;
    }
  }

  @Test
  void mapGatewayCarriesValidatedPayloadsAndHonorsDisabledUploads() throws Exception {
    boolean oldMap = Cache.mapEnabled, oldChronicle = Cache.chronicleEnabled;
    String oldRef = Cache.mapRef;
    Path upload = Files.writeString(folder.resolve("regions.json"), "{}");
    try (var gateway = mockStatic(GatewayClient.class)) {
      Cache.mapEnabled = false;
      RestServer.upload("regions", upload.toFile());
      RestServer.commenceRegen("political");
      gateway.verifyNoInteractions();
      Cache.mapEnabled = true;
      Cache.chronicleEnabled = false;
      RestServer.upload("chronicle", upload.toFile());
      gateway.verifyNoInteractions();
      Cache.mapRef = "boundary_map";
      gateway
          .when(() -> GatewayClient.request("GET", "/generator/banner", null))
          .thenReturn(GatewayClient.Result.success("[\"banner_one\",\"banner_two\"]"));
      assertEquals(List.of("banner_one", "banner_two"), RestServer.fetchBannerList());
      gateway
          .when(() -> GatewayClient.request(eq("POST"), anyString(), anyString()))
          .thenReturn(GatewayClient.Result.success("ok"));
      RestServer.upload("regions", upload.toFile());
      gateway.verify(
          () -> GatewayClient.request("POST", "/boundary_map/data/upload/regions", "{}"));
      gateway
          .when(
              () ->
                  GatewayClient.request(eq("GET"), contains("/api/regenerate/political"), isNull()))
          .thenReturn(GatewayClient.Result.success("ok"));
      RestServer.commenceRegen("political");
      gateway.verify(
          () -> GatewayClient.request(eq("GET"), contains("/api/regenerate/political"), isNull()));
    } finally {
      Cache.mapEnabled = oldMap;
      Cache.chronicleEnabled = oldChronicle;
      Cache.mapRef = oldRef;
    }
  }
}
