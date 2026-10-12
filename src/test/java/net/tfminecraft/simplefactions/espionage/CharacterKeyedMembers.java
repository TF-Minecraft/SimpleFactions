package net.tfminecraft.simplefactions.espionage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;

/**
 * Keys each test player by the character {@link EspionageTestFixtures#characterOf(String)}
 * gives their name, so office holders and faction members share one key. Registered
 * players are online and their character carries their name.
 */
final class CharacterKeyedMembers implements Members.Resolver {
    private final Map<String, Player> players = new HashMap<>();

    static CharacterKeyedMembers install() {
        CharacterKeyedMembers resolver = new CharacterKeyedMembers();
        Members.setResolver(resolver);
        return resolver;
    }

    Player add(Player player) {
        players.put(EspionageTestFixtures.key(player.getName()), player);
        return player;
    }

    void remove(Player player) {
        players.remove(EspionageTestFixtures.key(player.getName()));
    }

    @Override
    public Optional<CharacterSession> session(Player player) {
        if (player.getName() == null) return Optional.empty();
        return Optional.of(new CharacterSession(
                new CharacterRef(player.getUniqueId(), EspionageTestFixtures.characterOf(player.getName())), 1));
    }

    @Override
    public boolean isCurrent(CharacterSession session) {
        return true;
    }

    @Override
    public String key(Player player) {
        return player.getName() == null ? null : EspionageTestFixtures.key(player.getName());
    }

    @Override
    public Optional<CharacterInfo> info(String key) {
        Player player = players.get(key);
        if (player == null) return Optional.empty();
        return Optional.of(new CharacterInfo(new CharacterRef(player.getUniqueId(),
                EspionageTestFixtures.characterOf(player.getName())), player.getName(),
                CharacterStatus.ALIVE, null, Set.of(), 0, null));
    }

    @Override
    public Optional<Player> online(String key) {
        return Optional.ofNullable(players.get(key));
    }
}
