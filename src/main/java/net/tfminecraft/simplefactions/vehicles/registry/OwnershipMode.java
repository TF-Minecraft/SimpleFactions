package net.tfminecraft.simplefactions.vehicles.registry;

public enum OwnershipMode {
    PERSONAL,
    INSTALLATION,
    POOL,
    /**
     * Kept by a faction or a guild only for its upkeep: its bank pays, its leader decides, and
     * the vehicle never takes part in battles or other war rules.
     */
    UPKEEP
}
