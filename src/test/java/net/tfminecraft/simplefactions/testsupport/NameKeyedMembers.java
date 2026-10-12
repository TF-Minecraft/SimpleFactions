package net.tfminecraft.simplefactions.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;

/**
 * Member keys are opaque strings, so most tests key each mocked player by its
 * Minecraft name. Tests about real character keys install
 * {@link Members#useSharedIdentity()} themselves.
 */
public final class NameKeyedMembers implements Members.Resolver, BeforeEachCallback, AfterEachCallback {
    public static final NameKeyedMembers INSTANCE = new NameKeyedMembers();
    /** Character IDs handed out in sessions, back to the name that keys them. */
    private static final java.util.Map<UUID, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void install() {
        Members.setResolver(INSTANCE);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        install();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        install();
    }

    @Override
    public Optional<CharacterSession> session(Player player) {
        String name = player.getName();
        if (name == null) return Optional.empty();
        UUID owner = player.getUniqueId() != null ? player.getUniqueId() : id("owner:" + name);
        return Optional.of(new CharacterSession(new CharacterRef(owner, characterOf(name)), 1L));
    }

    /** The character ID standing for a test name; its key is the name again. */
    public static UUID characterOf(String name) {
        UUID character = id(name);
        NAMES.put(character, name);
        return character;
    }

    /** A session's character keys back to the player's name, as {@link #key(Player)} does. */
    @Override
    public String key(UUID characterId) {
        String name = NAMES.get(characterId);
        return name != null ? name : Members.Resolver.super.key(characterId);
    }

    @Override
    public boolean isCurrent(CharacterSession session) {
        return true;
    }

    @Override
    public String key(Player player) {
        return player.getName();
    }

    @Override
    public Optional<CharacterInfo> info(String key) {
        return Optional.empty();
    }

    @Override
    public Optional<Player> online(String key) {
        Player player = null;
        try {
            player = Bukkit.getPlayerExact(key);
        } catch (RuntimeException ignored) {
            // No server in this test.
        }
        if (player == null) {
            try {
                player = Bukkit.getPlayer(key);
            } catch (RuntimeException ignored) {
                // No server in this test.
            }
        }
        if (player == null) {
            try {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online != null && key.equals(online.getName())) return Optional.of(online);
                }
            } catch (RuntimeException ignored) {
                // No server in this test.
            }
        }
        return Optional.ofNullable(player);
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
