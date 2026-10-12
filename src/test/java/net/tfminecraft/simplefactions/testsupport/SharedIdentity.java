package net.tfminecraft.simplefactions.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;

/**
 * The real TLibs character facade over a mocked provider, with Bukkit's
 * server boundary stubbed, and {@link Members} reading from it. Accounts and
 * characters are created by the test.
 */
public final class SharedIdentity implements AutoCloseable {
    public final MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
    public final Server server = mock(Server.class);
    public final SimpleServicesManager services = new SimpleServicesManager();
    public final Plugin plugin = mock(Plugin.class);
    public final PluginManager plugins = mock(PluginManager.class);
    public final CharacterIdentity provider = mock(CharacterIdentity.class);
    public final Map<UUID, CharacterSession> sessions = new HashMap<>();
    public final Map<UUID, CharacterInfo> infos = new HashMap<>();
    public final Map<UUID, CharacterRef> heirs = new HashMap<>();
    public final Map<String, Player> online = new HashMap<>();
    public final Map<UUID, Player> connections = new HashMap<>();
    public final Map<String, OfflinePlayer> cached = new HashMap<>();
    public boolean heirsReady = true;
    private long generation;

    public SharedIdentity() {
        when(server.getPluginManager()).thenReturn(plugins);
        when(server.getServicesManager()).thenReturn(services);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> new ArrayList<>(connections.values()));
        bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> connections.get(call.getArgument(0)));
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(anyString())).thenAnswer(call -> cached.get(call.getArgument(0)));
        bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenAnswer(call -> {
            OfflinePlayer account = mock(OfflinePlayer.class);
            UUID id = call.getArgument(0);
            for (Map.Entry<String, OfflinePlayer> entry : cached.entrySet()) {
                if (id.equals(entry.getValue().getUniqueId())) when(account.getName()).thenReturn(entry.getKey());
            }
            return account;
        });
        when(provider.currentSession(any())).thenAnswer(call -> {
            Player player = call.getArgument(0);
            return Optional.ofNullable(sessions.get(player.getUniqueId()));
        });
        when(provider.isCurrent(any())).thenAnswer(call -> {
            CharacterSession session = call.getArgument(0);
            return session != null && session.equals(sessions.get(session.ref().ownerId()));
        });
        when(provider.info(any())).thenAnswer(call -> Optional.ofNullable(infos.get(call.getArgument(0))));
        when(provider.characters(any())).thenAnswer(call -> {
            UUID owner = call.getArgument(0);
            List<CharacterInfo> list = new ArrayList<>();
            for (CharacterInfo info : infos.values()) if (info.ref().ownerId().equals(owner)) list.add(info);
            return Optional.of(list);
        });
        when(provider.legacyHeir(any())).thenAnswer(call -> Optional.ofNullable(heirs.get(call.getArgument(0))));
        when(provider.legacyHeirsReady()).thenAnswer(call -> heirsReady);
        register();
        Members.useSharedIdentity();
    }

    /** A known account: cached by name, offline. */
    public UUID account(String name) {
        UUID owner = UUID.nameUUIDFromBytes(("account:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getName()).thenReturn(name);
        cached.put(name, player);
        return owner;
    }

    /** A living character of {@code owner}. */
    public CharacterRef character(UUID owner, String name, String... traits) {
        return character(owner, name, CharacterStatus.ALIVE, traits);
    }

    public CharacterRef character(UUID owner, String name, CharacterStatus status, String... traits) {
        CharacterRef ref = new CharacterRef(owner, UUID.randomUUID());
        infos.put(ref.characterId(), new CharacterInfo(ref, name, status, null, Set.of(traits), 0, Instant.EPOCH));
        return ref;
    }

    /** An online connection for an account, playing nothing yet. */
    public Player connect(String accountName) {
        UUID owner = account(accountName);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getName()).thenReturn(accountName);
        when(player.isOnline()).thenReturn(true);
        online.put(accountName, player);
        connections.put(owner, player);
        return player;
    }

    /** Starts a new session of {@code ref} for its online account. */
    public CharacterSession play(Player player, CharacterRef ref) {
        CharacterSession session = new CharacterSession(ref, ++generation);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public void stopPlaying(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public static String key(CharacterRef ref) {
        return Members.key(ref);
    }

    public void register() {
        services.register(CharacterIdentity.class, provider, plugin, ServicePriority.Normal);
    }

    public void unregister() {
        services.unregister(CharacterIdentity.class, provider);
    }

    public Collection<Player> players() {
        return connections.values();
    }

    @Override
    public void close() {
        try {
            unregister();
            NameKeyedMembers.install();
        } finally {
            bukkit.close();
        }
    }
}
