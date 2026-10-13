package net.tfminecraft.simplefactions.vehicles.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerMeta;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;

public final class VehicleOwnershipQueries {
    /** A personal vehicle: owned by one character, or still by a legacy account name, and not a faction's. */
    public record PersonalVehicle(String vehicle, String typeId) {}

    private VehicleOwnershipQueries() {}

    /**
     * Every personal vehicle, from VehicleFramework's owner values. Without a readable
     * VehicleFramework the personal registry rows stand in, so upkeep is still charged.
     */
    public static List<PersonalVehicle> allPersonalVehicles(PlayerVehicleRegistry registry) {
        VehicleOwnerApi api = VehicleOwnerWrites.api();
        if (api != null) {
            try {
                List<PersonalVehicle> out = new ArrayList<>();
                for (OwnerMeta meta : api.list()) {
                    String owner = meta.currentOwner();
                    if (!OwnerTags.isCharacter(owner) && OwnerTags.legacyName(owner) == null) continue;
                    if (registry.isFactionOwned(meta.vehicle())) continue;
                    out.add(new PersonalVehicle(meta.vehicle(), meta.typeId()));
                }
                return out;
            } catch (RuntimeException | LinkageError error) {
                logFallback(error);
            }
        }
        List<PersonalVehicle> rows = new ArrayList<>();
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (record.getMode() == OwnershipMode.PERSONAL) {
                rows.add(new PersonalVehicle(record.getVehicleUuid(), record.getVehicleTypeId()));
            }
        }
        return rows;
    }

    /** The account a legacy owner name belongs to, from DenarEconomy's remembered names. */
    public static UUID resolvePlayerUuid(String playerName) {
        return OfflineModifier.playerId(playerName);
    }

    /** The character a new registry row or fee is recorded for; null without a current session. */
    public static UUID characterId(Player player) {
        return Members.session(player).map(session -> session.ref().characterId()).orElse(null);
    }

    private static void logFallback(Throwable error) {
        var plugin = net.tfminecraft.simplefactions.SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning("Could not list vehicle owners; using personal registry rows instead: " + error);
        }
    }
}
