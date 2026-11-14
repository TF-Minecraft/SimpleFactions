package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.io.IOException;
import java.util.List;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
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
 * rows follow its main guild, which lives on as a player guild; if that cannot be saved, the
 * realm is kept.</li>
 * <li>A guild is deleted (by staff) or closed without a leader: its rows stay with nobody stamped
 * or paying, like a deleted faction's pool, for staff to reassign. A leader cannot delete a guild
 * that still holds upkeep-only vehicles; giving them out checks each member's vehicle limits.</li>
 * </ul>
 */
public final class UpkeepGuildChanges {
    private UpkeepGuildChanges() {}

    /**
     * After {@code guild} left {@code oldHost} and became {@code realm}'s main guild. Both realms
     * are saved first, so the rows never name a realm the saved data lacks; until then (or if
     * either save fails) the guild-keyed rows still count for the realm through its main guild.
     */
    public static void becameRealm(Guild guild, Faction oldHost, Faction realm) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        PlayerVehicleRegistry registry = plugin == null ? null : SimpleFactions.getVehicleRegistry();
        if (registry == null || guild == null || realm == null) return;
        List<PlayerVehicleRecord> rows = registry.getUpkeepVehicles(null, guild.getIncarnation());
        if (rows.isEmpty()) return;
        if (!saveRealms(oldHost, realm)) {
            plugin.getLogger().warning("Could not save " + realm.getId() + "; its " + rows.size()
                    + " upkeep-only vehicles keep counting for it through its main guild");
            return;
        }
        for (PlayerVehicleRecord row : rows) {
            registry.register(PlayerVehicleRecord.upkeep(row.getPlayerUuid(), row.getVehicleUuid(), row.getVehicleTypeId(),
                    realm.getId(), null));
        }
        if (!plugin.saveVehicleRegistry()) {
            rows.forEach(registry::register);
            plugin.getLogger().warning("Could not save " + rows.size() + " upkeep-only vehicles of " + realm.getId()
                    + " as faction rows; they still count for it through its main guild");
        }
    }

    private static boolean saveRealms(Faction oldHost, Faction realm) {
        Database database = new Database();
        DurableJson.Batch batch = new DurableJson.Batch();
        // The batch renames in this order: the new realm first, so a crash in between leaves the
        // guild in both realm files (a duplicate staff can clear) rather than in neither.
        if (!database.saveFactionDurably(realm, batch)) return false;
        if (oldHost != null && !database.saveFactionDurably(oldHost, batch)) return false;
        try {
            batch.commit();
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    /**
     * Before {@code realm} goes while its main guild {@code base} lives on as a player guild: its
     * faction rows become rows of that guild, which count for the realm as long as the guild is
     * its main guild and for the guild afterwards. False when they could not be saved: the realm
     * must then stay. The rows are kept in memory either way, for the next save to write.
     */
    public static boolean realmBecameGuild(Faction realm, Guild base) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        PlayerVehicleRegistry registry = plugin == null ? null : SimpleFactions.getVehicleRegistry();
        if (registry == null || realm == null || base == null) return true;
        List<PlayerVehicleRecord> rows = registry.getUpkeepVehicles(realm.getId(), null);
        if (rows.isEmpty()) return true;
        for (PlayerVehicleRecord row : rows) {
            registry.register(PlayerVehicleRecord.upkeep(row.getPlayerUuid(), row.getVehicleUuid(), row.getVehicleTypeId(),
                    null, base.getIncarnation()));
        }
        if (plugin.saveVehicleRegistry()) return true;
        plugin.getLogger().warning("Could not save " + rows.size() + " upkeep-only vehicles of " + realm.getId()
                + " as rows of its main guild; the realm is kept");
        return false;
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
