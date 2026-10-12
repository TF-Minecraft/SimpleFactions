package net.tfminecraft.simplefactions.guild;

import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;

/** Real character keys for tests: each player plays at most one named character at a time. */
public final class TestCharacters implements Members.Resolver {
    private final Map<String, CharacterInfo> characters = new HashMap<>();
    private final Map<Player, String> playing = new IdentityHashMap<>();

    /** Installs a fresh resolver; the global test extension restores the default after each test. */
    public static TestCharacters install() {
        TestCharacters resolver = new TestCharacters();
        Members.setResolver(resolver);
        return resolver;
    }

    /** Registers a character owned by {@code owner} and returns its member key. */
    public String character(UUID owner, String name) {
        CharacterRef ref = new CharacterRef(owner, UUID.randomUUID());
        String key = Members.key(ref);
        characters.put(key, new CharacterInfo(ref, name, CharacterStatus.ALIVE, "human", Set.of(), 0L, Instant.EPOCH));
        return key;
    }

    /** Starts a session for {@code key} on that player; null ends it. */
    public void play(Player player, String key) {
        if (key == null) playing.remove(player);
        else playing.put(player, key);
    }

    @Override
    public Optional<CharacterSession> session(Player player) {
        return Optional.ofNullable(playing.get(player))
                .map(key -> new CharacterSession(characters.get(key).ref(), 1L));
    }

    @Override
    public boolean isCurrent(CharacterSession session) {
        return true;
    }

    @Override
    public String key(Player player) {
        return playing.get(player);
    }

    @Override
    public Optional<CharacterInfo> info(String key) {
        return Optional.ofNullable(characters.get(key));
    }

    @Override
    public Optional<Player> online(String key) {
        return playing.entrySet().stream().filter(entry -> entry.getValue().equals(key))
                .map(Map.Entry::getKey).findFirst();
    }
}
