package net.tfminecraft.simplefactions.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.rpcharacters.chat.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.identity.LeaderCharacters;
import net.tfminecraft.simplefactions.integration.rpcharacters.chat.*;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.prestige.MemberPlaytime;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
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
import org.bukkit.plugin.SimpleServicesManager;
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
  void leaderNamesUseSharedReadyCharactersWithoutRpcEnablementOrDiskLookup() {
    SimpleServicesManager services = sharedServices();
    CharacterIdentity provider = mock(CharacterIdentity.class);
    LeaderCharacters.useSharedIdentity();
    assertNull(LeaderCharacters.resolve("Online", null, null).name());
    Player player = rig.player("Online");
    UUID owner = player.getUniqueId();
    UUID characterId = UUID.randomUUID();
    CharacterRef ref = new CharacterRef(owner, characterId);
    CharacterSession session = new CharacterSession(ref, 1);
    CharacterInfo info = new CharacterInfo(ref, "Aelin of the Reach", CharacterStatus.ALIVE,
        null, Set.of(), 7200, Instant.EPOCH);
    when(Bukkit.getPlayer(owner)).thenReturn(player);
    when(provider.currentSession(player)).thenReturn(Optional.empty());
    when(provider.info(characterId)).thenReturn(Optional.of(info));
    when(provider.isCurrent(session)).thenReturn(true);
    when(Bukkit.getOfflinePlayer(anyString()))
        .thenThrow(new AssertionError("Remote lookup is forbidden"));
    services.register(CharacterIdentity.class, provider, rig.ui.plugin, ServicePriority.Normal);
    try {
      assertNull(LeaderCharacters.resolve("Online", null, null).name());
      when(provider.currentSession(player)).thenReturn(Optional.of(session));
      assertEquals("Aelin of the Reach", LeaderCharacters.resolve("Online", null, null).name());
      assertNull(LeaderCharacters.resolve("Uncached", null, null).name());
      verify(Bukkit.getPluginManager(), never()).isPluginEnabled("RPCharacters");
      services.unregister(CharacterIdentity.class, provider);
      assertNull(LeaderCharacters.resolve("Online", "Aelin of the Reach", "Online").name());
    } finally {
      services.unregister(CharacterIdentity.class, provider);
      LeaderCharacters.reset();
    }
  }

  @Test
  void playtimeIntegrationUsesSharedOfflineSnapshotAndBecomesUnknownOnProviderLoss() {
    SimpleServicesManager services = sharedServices();
    CharacterIdentity provider = mock(CharacterIdentity.class);
    MemberPlaytime.useSharedIdentity();
    assertNull(MemberPlaytime.secondsFor("Offline"));
    UUID owner = UUID.randomUUID();
    OfflinePlayer offline = mock(OfflinePlayer.class);
    when(offline.getUniqueId()).thenReturn(owner);
    when(Bukkit.getOfflinePlayerIfCached("Offline")).thenReturn(offline);
    CharacterInfo info = new CharacterInfo(new CharacterRef(owner, UUID.randomUUID()),
        "Last character", CharacterStatus.DEAD, null, Set.of(), 7200, Instant.EPOCH);
    when(provider.lastPlayed(owner)).thenReturn(Optional.of(info));
    services.register(CharacterIdentity.class, provider, rig.ui.plugin, ServicePriority.Normal);
    try {
      assertEquals(7200, MemberPlaytime.secondsFor("Offline"));
      verify(provider).lastPlayed(owner);
      verify(Bukkit.getPluginManager(), never()).isPluginEnabled("RPCharacters");
      services.unregister(CharacterIdentity.class, provider);
      assertNull(MemberPlaytime.secondsFor("Offline"));
    } finally {
      services.unregister(CharacterIdentity.class, provider);
      MemberPlaytime.reset();
    }
  }

  private SimpleServicesManager sharedServices() {
    SimpleServicesManager services = new SimpleServicesManager();
    var plugins = Bukkit.getPluginManager();
    when(Bukkit.isPrimaryThread()).thenReturn(true);
    when(Bukkit.getServicesManager()).thenReturn(services);
    when(rig.ui.server.getServicesManager()).thenReturn(services);
    when(rig.ui.server.getPluginManager()).thenReturn(plugins);
    return services;
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
