package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.api.AccessGroup;
import net.tfminecraft.vehicleframework.api.VehicleAccessGroups;

/**
 * VehicleFramework's group switches, answered from current membership. A pool or installation
 * vehicle counts from its paying faction and always admits it; any other vehicle counts from its
 * owner's character. Guilds mean player guilds, never a faction's main guild, and faction
 * vehicles have none. Vassals are the direct vassals only.
 */
public final class VehicleGroupAccess implements VehicleAccessGroups {
    private final PlayerVehicleRegistry registry;
    private final BooleanSupplier ready;

    public VehicleGroupAccess(PlayerVehicleRegistry registry, BooleanSupplier ready) {
        this.registry = registry;
        this.ready = ready;
    }

    @Override
    public boolean ready() {
        return ready.getAsBoolean();
    }

    @Override
    public boolean admits(AccessGroup group, UUID vehicleId, String owner, UUID rider) {
        if (group == null || rider == null) return false;
        return switch (group) {
            case FACTION -> same(faction(vehicleId, owner), FactionManager.getByMember(rider));
            case GUILD -> {
                Guild guild = guild(vehicleId, owner);
                yield guild != null && guild == playerGuild(rider);
            }
            case ALLY -> {
                Faction faction = faction(vehicleId, owner);
                Faction other = FactionManager.getByMember(rider);
                yield faction != null && other != null && !same(faction, other) && RelationManager.isAlly(faction, other);
            }
            case VASSAL -> {
                Faction faction = faction(vehicleId, owner);
                Faction other = FactionManager.getByMember(rider);
                yield faction != null && other != null && RelationManager.isOverlord(other, faction);
            }
        };
    }

    @Override
    public Set<AccessGroup> alwaysOpen(UUID vehicleId, String owner) {
        return factionRow(vehicleId) != null ? Set.of(AccessGroup.FACTION) : Set.of();
    }

    @Override
    public Optional<String> describe(AccessGroup group, UUID vehicleId, String owner) {
        if (group == AccessGroup.GUILD) {
            return Optional.ofNullable(guild(vehicleId, owner)).map(Guild::getName);
        }
        return Optional.ofNullable(faction(vehicleId, owner)).map(Faction::getName);
    }

    /** The faction a vehicle's groups count from: its paying faction, else its owner's faction. */
    private Faction faction(UUID vehicleId, String owner) {
        PlayerVehicleRecord row = factionRow(vehicleId);
        if (row != null) return FactionVehiclePoolService.payingFaction(row);
        UUID character = OwnerTags.characterId(owner).orElse(null);
        return character == null ? null : FactionManager.getByMember(character);
    }

    private Guild guild(UUID vehicleId, String owner) {
        if (factionRow(vehicleId) != null) return null;
        return OwnerTags.characterId(owner).map(VehicleGroupAccess::playerGuild).orElse(null);
    }

    private PlayerVehicleRecord factionRow(UUID vehicleId) {
        if (vehicleId == null) return null;
        PlayerVehicleRecord row = registry.getByVehicleUuid(vehicleId.toString()).orElse(null);
        return LeaderStamps.isFactionRow(row) ? row : null;
    }

    /** The character's player guild, as member or leader; a faction's main guild does not count. */
    static Guild playerGuild(UUID character) {
        Guild guild = FactionManager.getGuildByMember(character);
        if (guild == null || guild.isBase()) guild = FactionManager.getGuildByLeader(character);
        return guild == null || guild.isBase() ? null : guild;
    }

    private static boolean same(Faction a, Faction b) {
        return a != null && b != null && a.getId().equalsIgnoreCase(b.getId());
    }
}
