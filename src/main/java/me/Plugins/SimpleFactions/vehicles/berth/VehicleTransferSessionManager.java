package me.Plugins.SimpleFactions.vehicles.berth;

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

        public VehicleTransferSession(String installationId, long expiresAtMillis) {
            this.installationId = installationId;
            this.expiresAtMillis = expiresAtMillis;
        }

        public String getInstallationId() {
            return installationId;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }

        public boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
