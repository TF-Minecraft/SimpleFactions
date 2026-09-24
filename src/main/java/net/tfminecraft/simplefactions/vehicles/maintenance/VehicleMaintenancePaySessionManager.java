package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;

public final class VehicleMaintenancePaySessionManager {
    private final Map<UUID, VehicleMaintenancePaySession> byLeaderUuid = new HashMap<>();

    public void put(UUID leaderUuid, VehicleMaintenancePaySession session) {
        if (leaderUuid == null || session == null) {
            return;
        }
        byLeaderUuid.put(leaderUuid, session);
    }

    public VehicleMaintenancePaySession get(UUID leaderUuid) {
        if (leaderUuid == null) {
            return null;
        }
        VehicleMaintenancePaySession session = byLeaderUuid.get(leaderUuid);
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

    public static final class VehicleMaintenancePaySession {
        private final long expiresAtMillis;
        private final PaymentSource paymentSource;

        public VehicleMaintenancePaySession(long expiresAtMillis) {
            this(expiresAtMillis, PaymentSource.POUCH);
        }

        public VehicleMaintenancePaySession(long expiresAtMillis, PaymentSource paymentSource) {
            this.expiresAtMillis = expiresAtMillis;
            this.paymentSource = paymentSource;
        }

        public PaymentSource getPaymentSource() {
            return paymentSource;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }

        public boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
