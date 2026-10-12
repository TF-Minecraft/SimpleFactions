package net.tfminecraft.simplefactions.api;

import net.tfminecraft.simplefactions.SimpleFactions;

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
        return plugin != null && plugin.isStarted();
    }
}
