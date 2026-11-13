package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Pending moves into upkeep-only, one per vehicle. An owner's request waits for the leader;
 * a leader's move of someone else's vehicle waits for its owner. Requests live in memory and
 * lapse after {@link #TIMEOUT_MILLIS}.
 */
public final class UpkeepRequests {
    public static final long TIMEOUT_MILLIS = 10L * 60L * 1000L;
    static final int SHORT_ID = 6;

    public enum Awaiting { LEADER, OWNER }

    /**
     * @param leaderKey the leader who moved the vehicle, for a request awaiting its owner; null otherwise
     */
    public record Pending(UpkeepHolder holder, String vehicle, String typeId, CharacterRef owner, String leaderKey,
            Awaiting awaiting, long expiresAt) {
        public String shortId() {
            return shortId(vehicle);
        }

        public static String shortId(String vehicle) {
            return vehicle.length() <= SHORT_ID ? vehicle : vehicle.substring(0, SHORT_ID);
        }
    }

    private static final Map<String, Pending> BY_VEHICLE = new LinkedHashMap<>();
    static LongSupplier clock = System::currentTimeMillis;

    private UpkeepRequests() {}

    public static long expiry() {
        return clock.getAsLong() + TIMEOUT_MILLIS;
    }

    /** Adds the request; false when the vehicle already has one pending. */
    public static boolean add(Pending pending) {
        prune();
        if (BY_VEHICLE.containsKey(pending.vehicle())) return false;
        BY_VEHICLE.put(pending.vehicle(), pending);
        return true;
    }

    /** The pending request for a vehicle ID, or the only one whose ID starts with {@code query}. */
    public static Pending find(String query) {
        prune();
        if (query == null || query.isBlank()) return null;
        Pending exact = BY_VEHICLE.get(query);
        if (exact != null) return exact;
        String prefix = query.toLowerCase(Locale.ROOT);
        Pending match = null;
        for (Pending pending : BY_VEHICLE.values()) {
            if (!pending.vehicle().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            if (match != null) return null;
            match = pending;
        }
        return match;
    }

    /** Whether several pending requests start with {@code query}. */
    public static boolean ambiguous(String query) {
        if (query == null || query.isBlank() || BY_VEHICLE.containsKey(query)) return false;
        String prefix = query.toLowerCase(Locale.ROOT);
        return BY_VEHICLE.keySet().stream().filter(vehicle -> vehicle.toLowerCase(Locale.ROOT).startsWith(prefix))
                .count() > 1;
    }

    public static List<Pending> forHolder(UpkeepHolder holder) {
        prune();
        List<Pending> out = new ArrayList<>();
        for (Pending pending : BY_VEHICLE.values()) {
            if (pending.holder().same(holder)) out.add(pending);
        }
        return out;
    }

    public static List<Pending> forOwner(UUID character) {
        prune();
        List<Pending> out = new ArrayList<>();
        for (Pending pending : BY_VEHICLE.values()) {
            if (pending.owner().characterId().equals(character)) out.add(pending);
        }
        return out;
    }

    public static void remove(String vehicle) {
        BY_VEHICLE.remove(vehicle);
    }

    private static void prune() {
        long now = clock.getAsLong();
        Iterator<Pending> iterator = BY_VEHICLE.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt() <= now) iterator.remove();
        }
    }

    static void resetForTests() {
        BY_VEHICLE.clear();
        clock = System::currentTimeMillis;
    }
}
