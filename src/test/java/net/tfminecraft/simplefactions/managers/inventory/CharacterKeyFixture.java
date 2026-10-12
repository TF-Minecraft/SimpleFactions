package net.tfminecraft.simplefactions.managers.inventory;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.bukkit.entity.Player;

/**
 * Real character keys for menu tests: each mocked player plays a named character whose key is a
 * character UUID, and can switch to another character. The global extension restores the
 * name-keyed resolver after each test.
 */
public final class CharacterKeyFixture implements Members.Resolver {
  private final Map<UUID, CharacterSession> sessions = new HashMap<>();
  private final Map<String, CharacterInfo> infos = new HashMap<>();
  private final Map<String, Player> online = new HashMap<>();
  private final Set<CharacterSession> ended = new HashSet<>();
  private long nextSession = 1;

  public static CharacterKeyFixture install() {
    CharacterKeyFixture fixture = new CharacterKeyFixture();
    Members.setResolver(fixture);
    return fixture;
  }

  /** Starts a new character for the player, ending any current one, and returns its key. */
  public String play(Player player, String name) {
    end(player);
    CharacterRef ref = new CharacterRef(player.getUniqueId(), UUID.randomUUID());
    sessions.put(player.getUniqueId(), new CharacterSession(ref, nextSession++));
    String key = Members.key(ref);
    infos.put(key, info(ref, name));
    online.put(key, player);
    return key;
  }

  /** A character that exists but is not being played. */
  public String offline(UUID owner, String name) {
    CharacterRef ref = new CharacterRef(owner, UUID.randomUUID());
    String key = Members.key(ref);
    infos.put(key, info(ref, name));
    return key;
  }

  public void end(Player player) {
    CharacterSession previous = sessions.remove(player.getUniqueId());
    if (previous == null) return;
    ended.add(previous);
    online.remove(Members.key(previous.ref()));
  }

  @Override
  public Optional<CharacterSession> session(Player player) {
    return Optional.ofNullable(sessions.get(player.getUniqueId()));
  }

  @Override
  public boolean isCurrent(CharacterSession session) {
    return !ended.contains(session);
  }

  @Override
  public String key(Player player) {
    return session(player).map(session -> Members.key(session.ref())).orElse(null);
  }

  @Override
  public Optional<CharacterInfo> info(String key) {
    return Optional.ofNullable(infos.get(key));
  }

  @Override
  public Optional<Player> online(String key) {
    return Optional.ofNullable(online.get(key));
  }

  private static CharacterInfo info(CharacterRef ref, String name) {
    return new CharacterInfo(ref, name, CharacterStatus.ALIVE, "human", Set.of(), 0L, Instant.EPOCH);
  }
}
