package net.tfminecraft.simplefactions.api;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import org.bukkit.entity.Player;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for other plugins. Until {@link #ready()} is true, SimpleFactions
 * is waiting for character identity or migrating, and its lookups
 * ({@code FactionManager.getByMember} and friends) return null or empty: treat
 * that as "unavailable", never as "no faction", and do not persist decisions
 * based on it. {@code SimpleFactionsReadyEvent} fires when it becomes ready.
 */
public final class SimpleFactionsApi {
    private SimpleFactionsApi() {}

    public static boolean ready() {
        SimpleFactions plugin = SimpleFactions.getInstance();
        return plugin != null && plugin.isStarted()
                && net.tfminecraft.tlibs.character.Characters.available()
                && net.tfminecraft.denareconomy.api.Denars.ready();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Guild> getGuildByMember(Player player) {
        return ready() ? Optional.ofNullable(FactionManager.getGuildByMember(player)) : Optional.empty();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Guild> getGuildByMember(UUID characterId) {
        return ready() ? Optional.ofNullable(FactionManager.getGuildByMember(characterId)) : Optional.empty();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Guild> getGuildByLeader(Player player) {
        return ready() ? Optional.ofNullable(FactionManager.getGuildByLeader(player)) : Optional.empty();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Guild> getGuildByLeader(UUID characterId) {
        return ready() ? Optional.ofNullable(FactionManager.getGuildByLeader(characterId)) : Optional.empty();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Faction> getByMember(Player player) {
        return ready() ? Optional.ofNullable(FactionManager.getByMember(player)) : Optional.empty();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Faction> getByMember(UUID characterId) {
        return ready() ? Optional.ofNullable(FactionManager.getByMember(characterId)) : Optional.empty();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Faction> getByLeader(Player player) {
        return ready() ? Optional.ofNullable(FactionManager.getByLeader(player)) : Optional.empty();
    }

    /** Empty while unavailable; check ready() before making persistence decisions. */
    public static Optional<Faction> getByLeader(UUID characterId) {
        return ready() ? Optional.ofNullable(FactionManager.getByLeader(characterId)) : Optional.empty();
    }
}
