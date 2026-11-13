package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.List;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * What a guild's upkeep-only vehicles become when the guild itself changes.
 * <ul>
 * <li>The guild becomes a realm (eviction to landless, elevation): its rows become the new
 * realm's faction rows. Until that is saved, and for a rebellion that may be undone, the rows
 * still count for whichever realm the guild leads ({@link UpkeepHolder#of(PlayerVehicleRecord)}).</li>
 * <li>The guild is disbanded: its leader gets the vehicles as personal vehicles, past the
 * personal-slot limit if need be. A guild closed without a leader keeps its rows with nobody to
 * pay or lead them, like a deleted faction's pool, for staff to reassign.</li>
 * </ul>
 */
public final class UpkeepGuildChanges {
    private UpkeepGuildChanges() {}

    /** After {@code guild} became {@code realm}'s main guild. */
    public static void becameRealm(Guild guild, Faction realm) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || guild == null || realm == null) return;
        PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
        if (registry == null) return;
        List<PlayerVehicleRecord> rows = registry.getUpkeepVehicles(null, guild.getIncarnation());
        if (rows.isEmpty()) return;
        for (PlayerVehicleRecord row : rows) {
            registry.register(PlayerVehicleRecord.upkeep(row.getPlayerUuid(), row.getVehicleUuid(),
                    row.getVehicleTypeId(), realm.getId(), null));
        }
        if (!plugin.saveVehicleRegistry()) {
            rows.forEach(registry::register);
            plugin.getLogger().warning("Could not save " + rows.size() + " upkeep-only vehicles of " + realm.getId()
                    + " as faction rows; they still count for it through its main guild");
        }
    }

    /** Before {@code guild} is removed for good (deleted, or closed without a leader). */
    public static void disbanded(Guild guild) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || guild == null || guild.isBase()) return;
        PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
        if (registry == null) return;
        List<PlayerVehicleRecord> rows = registry.getUpkeepVehicles(null, guild.getIncarnation());
        if (rows.isEmpty()) return;
        CharacterRef leader = Members.ref(guild.getLeader()).orElse(null);
        if (leader == null) {
            for (PlayerVehicleRecord row : rows) stamp(plugin, row.getVehicleUuid(), OwnerTags.NO_CHARACTER, Reason.RESTAMP);
            plugin.getLogger().warning(guild.getName() + " closed without a leader; its " + rows.size()
                    + " upkeep-only vehicles have nobody to pay for them until staff reassign them");
            return;
        }
        for (PlayerVehicleRecord row : rows) {
            registry.register(new PlayerVehicleRecord(leader.characterId(), row.getVehicleUuid(), row.getVehicleTypeId(),
                    OwnershipMode.PERSONAL, null, null, leader.ownerId()));
        }
        if (!plugin.saveVehicleRegistry()) {
            rows.forEach(registry::register);
            plugin.getLogger().severe("Could not give " + guild.getName() + "'s " + rows.size()
                    + " upkeep-only vehicles to its leader; they stay with the closed guild for staff");
            return;
        }
        for (PlayerVehicleRecord row : rows) {
            stamp(plugin, row.getVehicleUuid(), OwnerTags.character(leader.characterId()), Reason.RELEASE);
        }
        Members.online(guild.getLeader()).ifPresent(player -> player.sendMessage("§e" + guild.getName()
                + "§e's " + rows.size() + " upkeep-only vehicles are now your personal vehicles, and you pay their upkeep."));
    }

    private static void stamp(SimpleFactions plugin, String vehicle, String owner, Reason reason) {
        if (owner.equals(VehicleOwnerWrites.currentOwner(vehicle))) return;
        Result result = VehicleOwnerWrites.write(vehicle, owner, reason);
        if (result != Result.OK) {
            plugin.getLogger().warning("Could not set vehicle " + vehicle + " to " + owner + " (" + result
                    + "); the upkeep reconcile lists it");
        }
    }
}
