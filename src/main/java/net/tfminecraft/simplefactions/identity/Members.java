package net.tfminecraft.simplefactions.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Member keys: every stored member string is the lower-case ID of a character.
 * A player acts for the character of their current session; with no session
 * they are a stranger to every guild.
 */
public final class Members {

    /** Prefix of a leader seat whose original holder is waiting in quarantine. */
    public static final String QUARANTINED = "quarantine:";

    /** Answers the identity questions; production uses the shared character provider. */
    public interface Resolver {
        Optional<CharacterSession> session(Player player);

        boolean isCurrent(CharacterSession session);

        String key(Player player);

        Optional<CharacterInfo> info(String key);

        Optional<Player> online(String key);
    }

    private static final Resolver SHARED = new SharedResolver();
    private static volatile Resolver resolver = SHARED;

    private Members() {}

    public static void setResolver(Resolver next) {
        resolver = next == null ? SHARED : next;
    }

    public static void useSharedIdentity() {
        resolver = SHARED;
    }

    /** The current character's key, or null for a player without a ready session. */
    public static String key(Player player) {
        return player == null ? null : resolver.key(player);
    }

    /** Key form of a character ID. */
    public static String key(UUID characterId) {
        return characterId == null ? null : characterId.toString().toLowerCase(Locale.ROOT);
    }

    public static String key(CharacterRef ref) {
        return ref == null ? null : key(ref.characterId());
    }

    public static Optional<CharacterSession> session(Player player) {
        return player == null ? Optional.empty() : resolver.session(player);
    }

    public static boolean isCurrent(CharacterSession session) {
        return session != null && resolver.isCurrent(session);
    }

    /** The current character key of the online account with that Minecraft name, or null. */
    public static String ofAccountName(String accountName) {
        return accountName == null ? null : key(onlineByName(accountName));
    }

    /**
     * The member key standing for an account known only by name: its current
     * character if online, else its reviewed legacy heir. Used where another
     * plugin still records ownership by account (stage 6 moves those).
     */
    public static String ofAccountOrHeir(String accountName) {
        String online = ofAccountName(accountName);
        if (online != null || accountName == null) return online;
        try {
            if (Bukkit.getServer() == null) return null;
            org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(accountName);
            if (cached == null || cached.getUniqueId() == null || !Characters.available()) return null;
            return Characters.legacyHeir(cached.getUniqueId()).map(Members::key).orElse(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** The character name for a key; offline characters are read from the provider. */
    public static String display(String key) {
        if (key == null || key.isBlank()) return "Unknown";
        if (isQuarantined(key)) return "Unresolved member";
        if (characterId(key) == null) return key;
        return resolver.info(key).map(CharacterInfo::name).map(Members::plain).orElse("Unknown");
    }

    /** The name of the character the player is playing, for messages to others. */
    public static String name(Player player) {
        if (player == null) return "Unknown";
        String key = key(player);
        return key == null ? player.getName() : display(key);
    }

    /**
     * The character name, with the account name after it for staff who
     * bypass espionage, so they can tell who plays it.
     */
    public static String display(Player viewer, String key) {
        String name = display(key);
        if (!net.tfminecraft.simplefactions.espionage.EspionageService.bypasses(viewer)) return name;
        String account = accountName(key);
        return account == null ? name : name + " \u00a77(" + account + ")";
    }

    /** The account that plays the character, or empty if unknown. */
    public static Optional<UUID> accountId(String key) {
        return ref(key).map(CharacterRef::ownerId);
    }

    /** The Minecraft name of the account that plays the character, or null. */
    public static String accountName(String key) {
        UUID owner = accountId(key).orElse(null);
        if (owner == null) return null;
        try {
            return Bukkit.getServer() == null ? null : Bukkit.getOfflinePlayer(owner).getName();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Display names for several keys, in order. */
    public static List<String> display(Collection<String> keys) {
        List<String> names = new ArrayList<>();
        if (keys != null) for (String key : keys) names.add(display(key));
        return names;
    }

    /** The online player currently playing that character. */
    public static Optional<Player> online(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return resolver.online(key);
    }

    /** Nullable form of {@link #online(String)} for older call sites. */
    public static Player player(String key) {
        return online(key).orElse(null);
    }

    public static boolean isOnline(String key) {
        return online(key).isPresent();
    }

    public static Optional<CharacterRef> ref(String key) {
        if (key == null || characterId(key) == null) return Optional.empty();
        return resolver.info(key).map(CharacterInfo::ref);
    }

    public static Optional<CharacterInfo> info(String key) {
        if (key == null || characterId(key) == null) return Optional.empty();
        return resolver.info(key);
    }

    /** Whether the player is currently playing the character with that key. */
    public static boolean is(Player player, String key) {
        String own = key(player);
        return own != null && own.equals(key);
    }

    public static boolean isQuarantined(String key) {
        return key != null && key.startsWith(QUARANTINED);
    }

    /**
     * Finds a member among {@code keys} by what a player typed: an online
     * player's Minecraft name or a character name. Null when nothing or more
     * than one member matches.
     */
    public static String find(Collection<String> keys, String input) {
        if (keys == null || input == null) return null;
        String query = unquote(input);
        String typedKey = key(characterId(query));
        if (typedKey != null && keys.contains(typedKey)) return typedKey;
        Player online = onlineByName(query);
        String onlineKey = key(online);
        if (onlineKey != null && keys.contains(onlineKey)) return onlineKey;
        String match = null;
        for (String key : keys) {
            if (key == null || !display(key).equalsIgnoreCase(query)) continue;
            if (match != null && !match.equals(key)) return null;
            match = key;
        }
        return match;
    }

    /**
     * The key of an online player named by Minecraft or character name, or
     * null. A typed character ID is taken as it is, for staff.
     */
    public static String findOnline(String input) {
        if (input == null) return null;
        String query = unquote(input);
        String typedKey = key(characterId(query));
        if (typedKey != null) return typedKey;
        String direct = key(onlineByName(query));
        if (direct != null) return direct;
        String match = null;
        for (Player player : onlinePlayers()) {
            String key = key(player);
            if (key == null || !display(key).equalsIgnoreCase(query)) continue;
            if (match != null) return null;
            match = key;
        }
        return match;
    }

    /**
     * An online player named by Minecraft name, else by character name. Ambiguous
     * character names never pick a player; the sender is told why nothing matched.
     */
    public static Player resolveOnline(Player sender, String input) {
        String query = unquote(input);
        Player exact = onlineByName(query);
        if (exact != null) return exact;
        List<Player> matches = new ArrayList<>();
        for (Player player : onlinePlayers()) {
            if (player != null && name(player).equalsIgnoreCase(query)) matches.add(player);
        }
        if (matches.size() == 1) return matches.getFirst();
        if (sender != null) {
            sender.sendMessage(matches.isEmpty()
                    ? "\u00a7cNo online player found by that character or Minecraft name."
                    : "\u00a7cSeveral characters share that name. Use their Minecraft name.");
        }
        return null;
    }

    /** Bukkit replaces only the current argument, so return the untyped suffix of a full name. */
    public static List<String> completions(Collection<String> names, String[] args) {
        if (args.length < 2) return List.of();
        int skip = args.length - 2;
        String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        return names.stream().filter(java.util.Objects::nonNull).map(ChatColor::stripColor).distinct()
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length()))
                .map(name -> name.split(" "))
                .filter(words -> words.length > skip)
                .map(words -> String.join(" ", java.util.Arrays.copyOfRange(words, skip, words.length)))
                .toList();
    }

    /** The parsed character ID of a key, or null if it is not one. */
    public static UUID characterId(String key) {
        if (key == null) return null;
        try {
            UUID id = UUID.fromString(key);
            return id.toString().equalsIgnoreCase(key) ? id : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Player onlineByName(String name) {
        try {
            return Bukkit.getServer() == null ? null : Bukkit.getPlayerExact(name);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Collection<? extends Player> onlinePlayers() {
        try {
            return Bukkit.getServer() == null ? List.of() : Bukkit.getOnlinePlayers();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static String unquote(String input) {
        String query = input.strip();
        if (query.length() >= 2 && query.startsWith("\"") && query.endsWith("\""))
            query = query.substring(1, query.length() - 1).strip();
        return query;
    }

    static String plain(String name) {
        String stripped = ChatColor.stripColor(name);
        return stripped == null || stripped.isBlank() ? "Unknown" : stripped.strip();
    }

    /** Reads everything from the shared character provider on every call. */
    static final class SharedResolver implements Resolver {
        @Override
        public Optional<CharacterSession> session(Player player) {
            if (!Characters.available()) return Optional.empty();
            return Characters.currentSession(player)
                    .filter(session -> session.ref().ownerId().equals(player.getUniqueId()))
                    .filter(Characters::isCurrent);
        }

        @Override
        public boolean isCurrent(CharacterSession session) {
            return Characters.available() && Characters.isCurrent(session);
        }

        @Override
        public String key(Player player) {
            return session(player).map(session -> Members.key(session.ref())).orElse(null);
        }

        @Override
        public Optional<CharacterInfo> info(String key) {
            UUID id = characterId(key);
            if (id == null || !Characters.available()) return Optional.empty();
            return Characters.info(id).filter(info -> id.equals(info.ref().characterId()));
        }

        @Override
        public Optional<Player> online(String key) {
            return info(key).map(info -> Bukkit.getPlayer(info.ref().ownerId()))
                    .filter(player -> key.equals(key(player)));
        }
    }
}
