package net.tfminecraft.simplefactions.vehicles.handover;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owners who ran /faction vehicle handover and have not yet clicked a vehicle. */
public final class VehicleHandoverSessionManager {
    public record Session(String recipientName, UUID recipientUuid, long expiresAtMillis) {}

    private final Map<UUID, Session> byOwnerUuid = new HashMap<>();

    public void put(UUID ownerUuid, Session session) {
        if (ownerUuid != null && session != null) {
            byOwnerUuid.put(ownerUuid, session);
        }
    }

    public Session get(UUID ownerUuid) {
        Session session = ownerUuid == null ? null : byOwnerUuid.get(ownerUuid);
        if (session != null && System.currentTimeMillis() >= session.expiresAtMillis()) {
            byOwnerUuid.remove(ownerUuid);
            return null;
        }
        return session;
    }

    public void clear(UUID ownerUuid) {
        if (ownerUuid != null) {
            byOwnerUuid.remove(ownerUuid);
        }
    }
}
