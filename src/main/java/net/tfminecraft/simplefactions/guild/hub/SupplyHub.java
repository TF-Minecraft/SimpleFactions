package net.tfminecraft.simplefactions.guild.hub;

/**
 * One guild's supply hub at one installation. Active or dormant is not stored;
 * {@link SupplyHubService} computes it when asked.
 *
 * @param ownerFactionId faction that owns the installation
 * @param installationId installation id within that faction
 * @param createdAt epoch millis, oldest first for upkeep and slot order
 */
public record SupplyHub(String ownerFactionId, String installationId, long createdAt) {
}
