package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.List;
import java.util.function.Function;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

/**
 * What a faction's or guild's upkeep-only vehicles become when the faction or guild itself changes.
 * <ul>
 * <li>A guild becomes a realm (eviction to landless, elevation): its rows become the new realm's
 * faction rows. Until that is saved, and for a rebellion that may be undone, the rows still count
 * for whichever realm the guild leads ({@link UpkeepHolder#of(PlayerVehicleRecord)}).</li>
 * <li>A realm becomes a guild (annexed by its overlord, a rebel realm absorbed back): its faction
 * rows follow its main guild, which lives on as a player guild.</li>
 * <li>A guild is deleted (by staff) or closed without a leader: its rows stay with nobody stamped
 * or paying, like a deleted faction's pool, for staff to reassign. A leader cannot delete a guild
 * that still holds upkeep-only vehicles; giving them out checks each member's vehicle limits.</li>
 * </ul>
 */
public final class UpkeepGuildChanges {
    private UpkeepGuildChanges() {}

    /** After {@code guild} became {@code realm}'s main guild. */
    public static void becameRealm(Guild guild, Faction realm) {
        if (guild == null || realm == null) return;
        rewrite(registry -> registry.getUpkeepVehicles(null, guild.getIncarnation()),
                row -> PlayerVehicleRecord.upkeep(row.getPlayerUuid(), row.getVehicleUuid(), row.getVehicleTypeId(),
                        realm.getId(), null),
                realm.getId() + " as faction rows; they still count for it through its main guild");
    }

    /** Before {@code realm} is deleted while its main guild {@code base} lives on as a player guild. */
    public static void realmBecameGuild(Faction realm, Guild base) {
        if (realm == null || base == null) return;
        rewrite(registry -> registry.getUpkeepVehicles(realm.getId(), null),
                row -> PlayerVehicleRecord.upkeep(row.getPlayerUuid(), row.getVehicleUuid(), row.getVehicleTypeId(),
                        null, base.getIncarnation()),
                realm.getId() + " as rows of its main guild; they lose their holder with the realm");
    }

    private static void rewrite(Function<PlayerVehicleRegistry, List<PlayerVehicleRecord>> select,
            Function<PlayerVehicleRecord, PlayerVehicleRecord> change, String failure) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        PlayerVehicleRegistry registry = plugin == null ? null : SimpleFactions.getVehicleRegistry();
        if (registry == null) return;
        List<PlayerVehicleRecord> rows = select.apply(registry);
        if (rows.isEmpty()) return;
        for (PlayerVehicleRecord row : rows) registry.register(change.apply(row));
        if (!plugin.saveVehicleRegistry()) {
            rows.forEach(registry::register);
            plugin.getLogger().warning("Could not save " + rows.size() + " upkeep-only vehicles of " + failure);
        }
    }

    /** Whether the guild still holds upkeep-only vehicles; a leader must give them out before deleting it. */
    public static boolean holdsVehicles(Guild guild) {
        PlayerVehicleRegistry registry = SimpleFactions.getInstance() == null ? null : SimpleFactions.getVehicleRegistry();
        return registry != null && guild != null && !registry.getUpkeepVehicles(null, guild.getIncarnation()).isEmpty();
    }

    /** Before {@code guild} is removed for good: its rows stay, stamped for nobody, for staff. */
    public static void disbanded(Guild guild) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        PlayerVehicleRegistry registry = plugin == null ? null : SimpleFactions.getVehicleRegistry();
        if (registry == null || guild == null || guild.isBase()) return;
        List<PlayerVehicleRecord> rows = registry.getUpkeepVehicles(null, guild.getIncarnation());
        if (rows.isEmpty()) return;
        for (PlayerVehicleRecord row : rows) {
            String vehicle = row.getVehicleUuid();
            if (OwnerTags.NO_CHARACTER.equals(VehicleOwnerWrites.currentOwner(vehicle))) continue;
            Result result = VehicleOwnerWrites.write(vehicle, OwnerTags.NO_CHARACTER, Reason.RESTAMP);
            if (result != Result.OK) {
                plugin.getLogger().warning("Could not clear the owner of vehicle " + vehicle + " (" + result
                        + "); the upkeep reconcile lists it");
            }
        }
        plugin.getLogger().warning(guild.getName() + " (" + guild.getIncarnation() + ") was removed with " + rows.size()
                + " upkeep-only vehicles; nobody pays for them until staff reassign them");
    }
}
