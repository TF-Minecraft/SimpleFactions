package net.tfminecraft.simplefactions.vehicles.berth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VehicleReleaseSessionManager {
    private final Map<UUID, VehicleReleaseSession> byLeaderUuid = new HashMap<>();

    public void put(UUID leaderUuid, VehicleReleaseSession session) {
        if (leaderUuid == null || session == null) {
            return;
        }
        byLeaderUuid.put(leaderUuid, session);
    }

    public VehicleReleaseSession get(UUID leaderUuid) {
        if (leaderUuid == null) {
            return null;
        }
        VehicleReleaseSession session = byLeaderUuid.get(leaderUuid);
        if (session == null) {
            return null;
        }
        if (session.isExpired(System.currentTimeMillis())) {
            byLeaderUuid.remove(leaderUuid);
            return null;
        }
        return session;
    }

    public void clear(UUID leaderUuid) {
        if (leaderUuid == null) {
            return;
        }
        byLeaderUuid.remove(leaderUuid);
    }

    public enum Kind {
        TAKE,
        GIVE
    }

    public static final class VehicleReleaseSession {
        private final Kind kind;
        private final String targetName;
        private final UUID targetUuid;
        private final long expiresAtMillis;

        public VehicleReleaseSession(Kind kind, long expiresAtMillis) {
            this(kind, null, null, expiresAtMillis);
        }

        public VehicleReleaseSession(Kind kind, String targetName, UUID targetUuid, long expiresAtMillis) {
            this.kind = kind;
            this.targetName = targetName;
            this.targetUuid = targetUuid;
            this.expiresAtMillis = expiresAtMillis;
        }

        public Kind getKind() {
            return kind;
        }

        public String getTargetName() {
            return targetName;
        }

        public UUID getTargetUuid() {
            return targetUuid;
        }

        public boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
