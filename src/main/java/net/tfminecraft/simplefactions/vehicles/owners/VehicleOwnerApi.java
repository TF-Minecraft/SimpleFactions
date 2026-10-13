package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.List;
import java.util.Optional;

/** VehicleFramework's ownership API in SimpleFactions' own types. Main thread only. */
public interface VehicleOwnerApi {
    /** System write reasons, matching VehicleFramework's. */
    enum Reason { MIGRATE, RESTAMP, RELEASE, HANDOVER, CONSTRUCT, TRANSFER }

    /** {@code UNAVAILABLE} means SimpleFactions could not reach a usable VehicleFramework. */
    enum Result { OK, NOT_FOUND, SAVE_FAILED, REFUSED_MODE, UNAVAILABLE }

    /** Whether VehicleFramework runs in characters mode. */
    boolean characters();

    /** Every stored row except tombstones, plus loaded vehicles. Throws when SQLite cannot be read. */
    List<OwnerMeta> list();

    Optional<OwnerMeta> read(String vehicle);

    /** {@code whitelist} is only accepted with {@link Reason#MIGRATE}; null keeps the entries. */
    Result write(String vehicle, String owner, List<String> whitelist, Reason reason);
}
