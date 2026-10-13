package net.tfminecraft.simplefactions.vehicles.owners;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

/**
 * Faction vehicles carry their faction leader's character as owner, which gives the
 * leader the ownership menu and seats and nothing else. Without a leader character
 * (no leader, a staff dummy, a deleted faction) the stamp is {@code char_none}.
 */
public final class LeaderStamps {
    private static volatile boolean enabled;
    private static final Set<String> scheduled = new HashSet<>();

    private LeaderStamps() {}

    /** Turned on once the vehicle stage has finished; before that the boot reconcile covers changes. */
    public static void enable(boolean on) {
        enabled = on;
        if (!on) scheduled.clear();
    }

    public static boolean enabled() {
        return enabled;
    }

    public static String expectedStamp(Faction faction) {
        UUID leader = faction == null ? null : Members.characterId(faction.getLeader());
        return OwnerTags.character(leader);
    }

    /** The stamp a faction row should carry: its paying faction's leader, else {@code char_none}. */
    public static String expected(PlayerVehicleRecord record) {
        return expectedStamp(FactionVehiclePoolService.payingFaction(record));
    }

    public static boolean isFactionRow(PlayerVehicleRecord record) {
        return record != null
                && (record.getMode() == OwnershipMode.INSTALLATION || record.getMode() == OwnershipMode.POOL);
    }

    /** Writes the expected stamp when the vehicle carries anything else. */
    public static Result restamp(PlayerVehicleRecord record) {
        return stamp(record.getVehicleUuid(), expected(record));
    }

    static Result stamp(String vehicle, String expected) {
        // A vehicle held for staff (for example taken over while the migration was interrupted) is theirs.
        if (VehicleQuarantine.held(vehicle)) return Result.OK;
        String current = VehicleOwnerWrites.currentOwner(vehicle);
        if (current == null) return Result.NOT_FOUND;
        if (current.equals(expected)) return Result.OK;
        return VehicleOwnerWrites.write(vehicle, expected, Reason.RESTAMP);
    }

    /** {@link net.tfminecraft.simplefactions.events.FactionLeaderChangedEvent}: restamps the faction's vehicles. */
    public static void onLeaderChanged(Faction faction, PlayerVehicleRegistry registry) {
        if (!enabled || faction == null || registry == null) return;
        for (PlayerVehicleRecord record : rowsOf(faction, registry)) report(record.getVehicleUuid(), restamp(record));
    }

    /** Called once from {@code FactionManager.deleteFaction}: the faction's remaining rows lose their leader. */
    public static void onFactionDeleted(Faction faction) {
        if (!enabled || faction == null || SimpleFactions.getInstance() == null) return;
        for (PlayerVehicleRecord record : rowsOf(faction, SimpleFactions.getVehicleRegistry())) {
            report(record.getVehicleUuid(), stamp(record.getVehicleUuid(), OwnerTags.NO_CHARACTER));
        }
    }

    /** The registry rows a faction pays for, plus rows that still name it. */
    static List<PlayerVehicleRecord> rowsOf(Faction faction, PlayerVehicleRegistry registry) {
        List<PlayerVehicleRecord> rows = new ArrayList<>();
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (!isFactionRow(record)) continue;
            if (faction.getId().equalsIgnoreCase(record.getFactionId())
                    || (record.getFactionId() == null && FactionVehiclePoolService.payingFaction(record) == faction)) {
                rows.add(record);
            }
        }
        return rows;
    }

    /**
     * Startup step 3: every faction row gets its expected stamp. A failed write aborts the
     * vehicle stage; rows whose vehicle is gone are left to the upkeep reconcile's list.
     */
    public static void reconcile(PlayerVehicleRegistry registry) throws IOException {
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (!isFactionRow(record)) continue;
            Result result = restamp(record);
            if (result != Result.OK && result != Result.NOT_FOUND) {
                throw new IOException("Could not restamp faction vehicle " + record.getVehicleUuid() + ": " + result);
            }
        }
    }

    /** Backstop: whether a faction vehicle carries a stale stamp. Personal and unregistered vehicles never do. */
    public static boolean isStale(PlayerVehicleRecord record, String currentOwner) {
        return isFactionRow(record) && !expected(record).equals(currentOwner)
                && !VehicleQuarantine.held(record.getVehicleUuid());
    }

    /** Restamps on the next tick, once per vehicle however often it is clicked. */
    public static void scheduleRestamp(PlayerVehicleRecord record) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (!enabled || plugin == null || !scheduled.add(record.getVehicleUuid())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            scheduled.remove(record.getVehicleUuid());
            if (!enabled) return;
            PlayerVehicleRecord now = SimpleFactions.getVehicleRegistry()
                    .getByVehicleUuid(record.getVehicleUuid()).orElse(null);
            if (isFactionRow(now)) report(now.getVehicleUuid(), restamp(now));
        });
    }

    private static void report(String vehicle, Result result) {
        if (result == Result.OK) return;
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning("Could not restamp faction vehicle " + vehicle + " (" + result
                    + "); the next boot or click tries again");
        }
    }
}
