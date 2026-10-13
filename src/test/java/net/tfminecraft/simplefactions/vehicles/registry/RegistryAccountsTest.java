package net.tfminecraft.simplefactions.vehicles.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistryAccountsTest {
    @TempDir Path folder;
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();

    @Test
    void olderPersonalRowsGetTheirCharactersAccountElseTheAccountTheMigrationsMatched() throws IOException {
        UUID cached = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        UUID characterAccount = UUID.randomUUID();
        UUID sixA = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "cached", "cart", OwnershipMode.PERSONAL, null,
                null, cached));
        registry.register(new PlayerVehicleRecord(character, "character", "cart", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "deleted", "cart", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(null, "payer", "cart", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(null, "unknown", "cart", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(null, "pool", "gun", OwnershipMode.POOL, null, "red"));

        int filled = RegistryAccounts.fill(folder, registry,
                id -> id.equals(character) ? Optional.of(characterAccount) : Optional.empty(),
                List.of(Map.of("deleted", sixA, "pool", UUID.randomUUID()), Map.of("payer", payer, "deleted", payer)));

        assertEquals(3, filled);
        assertEquals(cached, account("cached"));
        assertEquals(characterAccount, account("character"));
        assertEquals(sixA, account("deleted"));
        assertEquals(payer, account("payer"));
        assertNull(account("unknown"));
        assertNull(account("pool"));
        PlayerVehicleRegistry reloaded = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(folder.resolve("Cache").toFile(), reloaded).load();
        assertEquals(payer, reloaded.getByVehicleUuid("payer").orElseThrow().getAccountUuid());
        assertEquals(cached, reloaded.getByVehicleUuid("cached").orElseThrow().getAccountUuid());
    }

    @Test
    void nothingToFillWritesNothing() throws IOException {
        registry.register(new PlayerVehicleRecord(null, "unknown", "cart", OwnershipMode.PERSONAL, null));
        assertEquals(0, RegistryAccounts.fill(folder, registry, id -> Optional.empty(), List.of()));
        assertFalse(Files.exists(folder.resolve("Cache/vehicles_registry.json")));
    }

    @Test
    void rowsKeepTheirAccountAcrossTheRegistryFileAndReplacementsAreDurable() throws IOException {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        VehicleRegistryPersistence.personal(folder, registry, "cart", "cart", character, account);
        PlayerVehicleRecord row = registry.getByVehicleUuid("cart").orElseThrow();
        assertEquals(account, row.getAccountUuid());
        assertEquals(account, row.withAccount(account).getAccountUuid());

        VehicleRegistryPersistence.replace(folder, registry, "cart", null);
        assertTrue(registry.getAll().isEmpty());
        assertEquals("[]", Files.readString(folder.resolve("Cache/vehicles_registry.json")).strip());
        VehicleRegistryPersistence.replace(folder, registry, "cart", row);
        PlayerVehicleRegistry reloaded = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(new File(folder.toFile(), "Cache"), reloaded).load();
        assertEquals(account, reloaded.getByVehicleUuid("cart").orElseThrow().getAccountUuid());
        assertEquals(character, reloaded.getByVehicleUuid("cart").orElseThrow().getPlayerUuid());
    }

    private UUID account(String vehicle) {
        return registry.getByVehicleUuid(vehicle).orElseThrow().getAccountUuid();
    }
}
