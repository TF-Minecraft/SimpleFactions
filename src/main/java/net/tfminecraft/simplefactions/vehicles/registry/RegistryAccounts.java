package net.tfminecraft.simplefactions.vehicles.registry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Personal rows from before the account was cached get it filled in, so slot limits
 * keep counting them per account: from the character's account, else from the account
 * the owner migrations matched the vehicle's old owner name to. Rows nothing resolves
 * keep no account and count against every claimer.
 */
public final class RegistryAccounts {
    private RegistryAccounts() {}

    /** Fills missing accounts and saves the registry when anything changed; returns how many were filled. */
    public static int fill(Path folder, PlayerVehicleRegistry registry, Function<UUID, Optional<UUID>> accountOf,
            List<Map<String, UUID>> matched) throws IOException {
        List<PlayerVehicleRecord> records = new ArrayList<>();
        int filled = 0;
        for (PlayerVehicleRecord record : registry.getAll()) {
            UUID account = record.getMode() == OwnershipMode.PERSONAL && record.getAccountUuid() == null
                    ? account(record, accountOf, matched) : null;
            if (account != null) filled++;
            records.add(account == null ? record : record.withAccount(account));
        }
        if (filled > 0) {
            VehicleRegistryPersistence.write(folder.resolve("Cache/vehicles_registry.json"), records);
            records.forEach(registry::register);
        }
        return filled;
    }

    private static UUID account(PlayerVehicleRecord record, Function<UUID, Optional<UUID>> accountOf,
            List<Map<String, UUID>> matched) {
        if (record.getPlayerUuid() != null) {
            Optional<UUID> owner = accountOf.apply(record.getPlayerUuid());
            if (owner.isPresent()) return owner.get();
        }
        for (Map<String, UUID> source : matched) {
            UUID account = source.get(record.getVehicleUuid());
            if (account != null) return account;
        }
        return null;
    }
}
