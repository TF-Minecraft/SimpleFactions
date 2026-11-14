package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Armed upkeep-only click modes, by account: the next vehicle the player right-clicks is
 * moved in, requested, or handed to a member.
 */
public final class UpkeepClickSessions {
    public enum Mode { ADD, REQUEST, GIVE }

    /** @param recipient the member key a {@link Mode#GIVE} hands the vehicle to; null otherwise */
    public record Session(Mode mode, UpkeepHolder holder, String recipient, long expiresAt) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    static LongSupplier clock = System::currentTimeMillis;

    private UpkeepClickSessions() {}

    public static void arm(UUID account, Session session) {
        SESSIONS.put(account, session);
    }

    public static Session get(UUID account) {
        Session session = SESSIONS.get(account);
        if (session != null && session.expiresAt() <= clock.getAsLong()) {
            SESSIONS.remove(account);
            return null;
        }
        return session;
    }

    /** Ends the click mode; false when none was armed. */
    public static boolean disarm(UUID account) {
        return SESSIONS.remove(account) != null;
    }

    static long now() {
        return clock.getAsLong();
    }

    static void resetForTests() {
        SESSIONS.clear();
        clock = System::currentTimeMillis;
    }
}
