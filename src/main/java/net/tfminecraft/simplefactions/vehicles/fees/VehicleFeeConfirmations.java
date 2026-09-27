package net.tfminecraft.simplefactions.vehicles.fees;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers the charge a player was last shown, so repeating the same action within the
 * window confirms it. Anything else in between asks again.
 */
public final class VehicleFeeConfirmations {
    public static final long WINDOW_MILLIS = 30_000L;

    private record Pending(String target, double amount, long expiresAtMillis) {}

    private final Map<UUID, Pending> pending = new HashMap<>();

    /** True, and cleared, when this player was shown this charge and has not let it lapse. */
    public boolean confirm(UUID playerUuid, String target, double amount, long nowMillis) {
        Pending found = playerUuid == null ? null : pending.get(playerUuid);
        if (found == null || nowMillis >= found.expiresAtMillis()
                || !found.target().equals(target) || Double.compare(found.amount(), amount) != 0) {
            return false;
        }
        pending.remove(playerUuid);
        return true;
    }

    public void ask(UUID playerUuid, String target, double amount, long nowMillis) {
        if (playerUuid != null && target != null) {
            pending.put(playerUuid, new Pending(target, amount, nowMillis + WINDOW_MILLIS));
        }
    }

    public void clear(UUID playerUuid) {
        if (playerUuid != null) {
            pending.remove(playerUuid);
        }
    }
}
