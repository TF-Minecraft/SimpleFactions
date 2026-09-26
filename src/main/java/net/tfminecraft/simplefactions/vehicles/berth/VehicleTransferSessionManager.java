package net.tfminecraft.simplefactions.vehicles.berth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VehicleTransferSessionManager {
    private final Map<UUID, VehicleTransferSession> byLeaderUuid = new HashMap<>();

    public void put(UUID leaderUuid, VehicleTransferSession session) {
        if (leaderUuid == null || session == null) {
            return;
        }
        byLeaderUuid.put(leaderUuid, session);
    }

    public VehicleTransferSession get(UUID leaderUuid) {
        if (leaderUuid == null) {
            return null;
        }
        VehicleTransferSession session = byLeaderUuid.get(leaderUuid);
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

    public static final class VehicleTransferSession {
        private final String installationId;
        private final long expiresAtMillis;
        private final boolean pool;

        public VehicleTransferSession(String installationId, long expiresAtMillis) {
            this(installationId, expiresAtMillis, false);
        }

        public VehicleTransferSession(String installationId, long expiresAtMillis, boolean pool) {
            this.installationId = installationId;
            this.expiresAtMillis = expiresAtMillis;
            this.pool = pool;
        }

        public String getInstallationId() {
            return installationId;
        }

        public boolean isPool() {
            return pool;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }

        public boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
