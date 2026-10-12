package net.tfminecraft.simplefactions.espionage;

import org.bukkit.Bukkit;
import net.tfminecraft.tlibs.character.Characters;

/** Visible online names and cached-account directory names, without disk or network reads. */
public final class CharacterNames {
    private CharacterNames() {}

    public static String display(org.bukkit.entity.Player viewer, String account) {
        String character = of(account);
        return EspionageService.bypasses(viewer) ? character + " \u00a77(" + account + ")" : character;
    }

    public static String display(org.bukkit.entity.Player viewer, String account,
                                 net.tfminecraft.simplefactions.objects.Faction faction) {
        if (!Characters.available() || EspionageService.canViewExact(viewer, faction))
            return display(viewer, account);
        return forForeign(account);
    }

    /** A missing active character must not expose account identity in foreign reports. */
    public static String forForeign(String account) {
        String name = activeName(account);
        return name == null ? "Unknown" : name;
    }

    /** Exact account names take precedence; ambiguous character names never pick a player arbitrarily. */
    public static org.bukkit.entity.Player resolveOnline(org.bukkit.entity.Player sender, String input) {
        String query = input.strip();
        if (query.length() >= 2 && query.startsWith("\"") && query.endsWith("\""))
            query = query.substring(1, query.length() - 1).strip();
        var result = resolve(query, Bukkit.getOnlinePlayers(), player -> of(player.getName()));
        if (result.ambiguous()) sender.sendMessage("\u00a7cSeveral characters share that name. Use their Minecraft name.");
        else if (result.player() == null) sender.sendMessage("\u00a7cNo online player found by that character or Minecraft name.");
        return result.player();
    }

    static Resolution resolve(String query, java.util.Collection<? extends org.bukkit.entity.Player> players,
                              java.util.function.Function<org.bukkit.entity.Player, String> names) {
        for (var player : players) if (player.getName().equalsIgnoreCase(query)) return new Resolution(player, false);
        var matches = players.stream().filter(player -> org.bukkit.ChatColor.stripColor(names.apply(player))
                .equalsIgnoreCase(query)).toList();
        return new Resolution(matches.size() == 1 ? matches.getFirst() : null, matches.size() > 1);
    }

    record Resolution(org.bukkit.entity.Player player, boolean ambiguous) {}

    /** Bukkit replaces only the current argument, so return the untyped suffix of a full name. */
    public static java.util.List<String> inviteCompletions(java.util.Collection<String> names, String[] args) {
        if (args.length < 2) return java.util.List.of();
        int skip = args.length - 2;
        String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        return names.stream().filter(java.util.Objects::nonNull).map(org.bukkit.ChatColor::stripColor).distinct()
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length()))
                .map(name -> name.split(" "))
                .filter(words -> words.length > skip)
                .map(words -> String.join(" ", java.util.Arrays.copyOfRange(words, skip, words.length)))
                .toList();
    }

    public static String of(String playerName) {
        String name = activeName(playerName);
        return name == null ? playerName : name;
    }

    private static String activeName(String playerName) {
        if (playerName == null) return "Unknown";
        if (!Characters.available()) return playerName;
        var player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            var session = Characters.currentSession(player).orElse(null);
            if (session == null || !session.ref().ownerId().equals(player.getUniqueId())) return null;
            String name = Characters.displayName(player);
            return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                    && Characters.isCurrent(session) ? name : null;
        }
        var cached = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cached == null) return null;
        return Characters.lastPlayed(cached.getUniqueId())
                .filter(info -> info.ref().ownerId().equals(cached.getUniqueId()))
                .map(net.tfminecraft.tlibs.character.CharacterInfo::name).orElse(null);
    }
}
