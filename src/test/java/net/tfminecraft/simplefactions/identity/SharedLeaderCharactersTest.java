package net.tfminecraft.simplefactions.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.SimpleServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

class SharedLeaderCharactersTest {
    @Test
    void readyLeaderUsesCanonicalNameRatherThanMaskOrAlias() {
        try (var fixture = new Fixture()) {
            Player leader = fixture.player("rushork");
            fixture.activate(leader, UUID.randomUUID(), "Grunk the Bold");
            when(fixture.provider.displayName(leader)).thenReturn("A hooded traveller");

            assertEquals("A hooded traveller", Characters.displayName(leader));
            clearInvocations(fixture.provider);
            assertEquals(new LeaderCharacters.Remembered("Grunk the Bold", "rushork"),
                    LeaderCharacters.resolve("rushork", null, null));
            verify(fixture.provider, never()).displayName(any());
            verify(fixture.plugin, never()).getDataFolder();
            verify(fixture.plugins, never()).getPlugin(anyString());
        }
    }

    @Test
    void pendingOnlineLeaderKeepsOnlyTheirRememberedNameWithoutOfflineFallback() {
        try (var fixture = new Fixture()) {
            Player leader = fixture.player("rushork");
            fixture.lastPlayed.put(leader.getUniqueId(),
                    info(leader.getUniqueId(), UUID.randomUUID(), "Old directory name"));

            assertEquals(LeaderCharacters.Remembered.NONE,
                    LeaderCharacters.resolve("rushork", null, null));
            assertEquals(new LeaderCharacters.Remembered("Remembered", "rushork"),
                    LeaderCharacters.resolve("rushork", "Remembered", "RUSHORK"));
            assertEquals(LeaderCharacters.Remembered.NONE,
                    LeaderCharacters.resolve("rushork", "Another ruler", "someoneelse"));
            verify(fixture.provider, never()).lastPlayed(any());
        }
    }

    @Test
    void sharedLookupSeesProviderArrivalAndLossWithoutReinstallingTheProbe() {
        try (var fixture = new Fixture()) {
            Player leader = fixture.player("rushork");
            fixture.activate(leader, UUID.randomUUID(), "Grunk");
            fixture.unregister();
            assertEquals(LeaderCharacters.Remembered.NONE,
                    LeaderCharacters.resolve("rushork", "Remembered", "rushork"));

            fixture.register();
            assertEquals("Grunk", LeaderCharacters.resolve("rushork", null, null).name());
            fixture.unregister();
            assertEquals(LeaderCharacters.Remembered.NONE,
                    LeaderCharacters.resolve("rushork", "Grunk", "rushork"));
        }
    }

    @Test
    void onlineLookupRejectsForeignSessionsAndMismatchedOrMissingSnapshots() {
        try (var fixture = new Fixture()) {
            Player leader = fixture.player("rushork");
            UUID owner = leader.getUniqueId();
            UUID characterId = UUID.randomUUID();
            CharacterSession session = fixture.activate(leader, characterId, "Grunk");
            fixture.sessions.put(owner,
                    new CharacterSession(new CharacterRef(UUID.randomUUID(), characterId), 100));
            assertNull(LeaderCharacters.resolve("rushork", null, null).name());

            fixture.sessions.put(owner, session);
            fixture.infos.put(characterId, info(UUID.randomUUID(), characterId, "Someone else's name"));
            assertNull(LeaderCharacters.resolve("rushork", null, null).name());
            fixture.infos.put(characterId, info(owner, UUID.randomUUID(), "Wrong character"));
            assertNull(LeaderCharacters.resolve("rushork", null, null).name());
            fixture.infos.remove(characterId);
            assertNull(LeaderCharacters.resolve("rushork", null, null).name());
        }
    }

    @Test
    void snapshotCallbackCannotPublishAnOldGenerationAfterAToBToA() {
        try (var fixture = new Fixture()) {
            Player leader = fixture.player("rushork");
            UUID characterId = UUID.randomUUID();
            CharacterSession original = fixture.activate(leader, characterId, "Original A");
            CharacterInfo originalInfo = fixture.infos.get(characterId);
            when(fixture.provider.info(characterId)).thenAnswer(call -> {
                fixture.activate(leader, UUID.randomUUID(), "B");
                fixture.activate(leader, characterId, "Fresh A");
                return Optional.of(originalInfo);
            });

            assertEquals("Remembered", LeaderCharacters.resolve("rushork", "Remembered", "rushork").name());
            verify(fixture.provider).isCurrent(original);
            assertFalse(Characters.isCurrent(original));
        }
    }

    @Test
    void snapshotCallbackCannotPublishAfterDisconnectOrConnectionReplacement() {
        for (boolean disconnect : new boolean[] {true, false}) {
            try (var fixture = new Fixture()) {
                Player leader = fixture.player("rushork");
                UUID owner = leader.getUniqueId();
                UUID characterId = UUID.randomUUID();
                CharacterSession original = fixture.activate(leader, characterId, "Grunk");
                CharacterInfo snapshot = fixture.infos.get(characterId);
                when(fixture.provider.info(characterId)).thenAnswer(call -> {
                    if (disconnect) when(leader.isOnline()).thenReturn(false);
                    else fixture.connections.put(owner, mock(Player.class));
                    return Optional.of(snapshot);
                });

                assertNull(LeaderCharacters.resolve("rushork", null, null).name());
                assertTrue(Characters.isCurrent(original), "Provider token alone cannot prove the connection");
                when(leader.isOnline()).thenReturn(true);
                fixture.connections.put(owner, leader);
                doReturn(Optional.of(snapshot)).when(fixture.provider).info(characterId);
                assertEquals("Grunk", LeaderCharacters.resolve("rushork", null, null).name());
            }
        }
    }

    @Test
    void offlineLeaderUsesCachedOwnerAndLastPlayedEvenWhenTheCharacterIsDead() {
        try (var fixture = new Fixture()) {
            UUID owner = fixture.cached("rushork");
            fixture.lastPlayed.put(owner, new CharacterInfo(new CharacterRef(owner, UUID.randomUUID()),
                    "Grunk remembered", CharacterStatus.DEAD, null, Set.of(), 60, Instant.EPOCH));

            assertEquals("Grunk remembered", LeaderCharacters.resolve("rushork", null, null).name());
            fixture.bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
            verify(fixture.plugin, never()).getDataFolder();
            verify(fixture.provider, never()).currentSession(any());
        }
    }

    @Test
    void uncachedUnknownAndForeignOfflineResultsCannotInventAName() {
        try (var fixture = new Fixture()) {
            assertNull(LeaderCharacters.resolve("uncached", null, null).name());
            verify(fixture.provider, never()).lastPlayed(any());
            UUID owner = fixture.cached("rushork");
            assertEquals("Remembered", LeaderCharacters.resolve("rushork", "Remembered", "rushork").name());
            fixture.lastPlayed.put(owner, info(UUID.randomUUID(), UUID.randomUUID(), "Wrong owner"));
            assertNull(LeaderCharacters.resolve("rushork", null, null).name());
            fixture.bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
        }
    }

    static CharacterInfo info(UUID owner, UUID characterId, String name) {
        return new CharacterInfo(new CharacterRef(owner, characterId), name,
                CharacterStatus.ALIVE, null, Set.of(), 0, Instant.EPOCH);
    }

    /** Real facade and registry; only Bukkit's server boundary and provider data are controlled. */
    static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final SimpleServicesManager services = new SimpleServicesManager();
        final Plugin plugin = mock(Plugin.class);
        final PluginManager plugins = mock(PluginManager.class);
        final CharacterIdentity provider = mock(CharacterIdentity.class);
        final Map<UUID, CharacterSession> sessions = new HashMap<>();
        final Map<UUID, CharacterInfo> infos = new HashMap<>();
        final Map<UUID, CharacterInfo> lastPlayed = new HashMap<>();
        final Map<String, Player> online = new HashMap<>();
        final Map<UUID, Player> connections = new HashMap<>();
        final Map<String, OfflinePlayer> cached = new HashMap<>();
        private long generation;

        Fixture() {
            Server server = mock(Server.class);
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getServicesManager()).thenReturn(services);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayerExact(anyString()))
                    .thenAnswer(call -> online.get(call.getArgument(0)));
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class)))
                    .thenAnswer(call -> connections.get(call.getArgument(0)));
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(anyString()))
                    .thenAnswer(call -> cached.get(call.getArgument(0)));
            when(provider.currentSession(any())).thenAnswer(call -> {
                Player player = call.getArgument(0);
                return Optional.ofNullable(sessions.get(player.getUniqueId()));
            });
            when(provider.isCurrent(any())).thenAnswer(call -> {
                CharacterSession session = call.getArgument(0);
                return session != null && session.equals(sessions.get(session.ref().ownerId()));
            });
            when(provider.info(any())).thenAnswer(call -> Optional.ofNullable(infos.get(call.getArgument(0))));
            when(provider.lastPlayed(any())).thenAnswer(call -> Optional.ofNullable(lastPlayed.get(call.getArgument(0))));
            register();
            LeaderCharacters.useSharedIdentity();
        }

        Player player(String name) {
            Player player = mock(Player.class);
            UUID owner = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(owner);
            when(player.getName()).thenReturn(name);
            when(player.isOnline()).thenReturn(true);
            online.put(name, player);
            connections.put(owner, player);
            return player;
        }

        UUID cached(String name) {
            UUID owner = UUID.randomUUID();
            OfflinePlayer player = mock(OfflinePlayer.class);
            when(player.getUniqueId()).thenReturn(owner);
            cached.put(name, player);
            return owner;
        }

        CharacterSession activate(Player player, UUID characterId, String name) {
            UUID owner = player.getUniqueId();
            CharacterSession session = new CharacterSession(new CharacterRef(owner, characterId), ++generation);
            sessions.put(owner, session);
            infos.put(characterId, info(owner, characterId, name));
            return session;
        }

        void register() {
            services.register(CharacterIdentity.class, provider, plugin, ServicePriority.Normal);
        }

        void unregister() {
            services.unregister(CharacterIdentity.class, provider);
        }

        @Override public void close() {
            try {
                unregister();
                LeaderCharacters.reset();
            } finally {
                bukkit.close();
            }
        }
    }
}
