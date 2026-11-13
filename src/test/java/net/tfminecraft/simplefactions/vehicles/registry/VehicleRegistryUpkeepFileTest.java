package net.tfminecraft.simplefactions.vehicles.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VehicleRegistryUpkeepFileTest {
    @TempDir Path folder;

    private Path cache() throws Exception {
        return Files.createDirectories(folder.resolve("Cache"));
    }

    @Test
    void upkeepRowsAreSavedApartSoTheRegistryStaysReadableForOlderPlugins() throws Exception {
        Path cache = cache();
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        UUID owner = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(owner, "personal", "sloop", OwnershipMode.PERSONAL, null));
        registry.register(PlayerVehicleRecord.upkeep(owner, "kept", "sloop", "realm", null));
        registry.register(PlayerVehicleRecord.upkeep(owner, "guilds", "sloop", "ignored", "smiths"));
        VehicleRegistryPersistence persistence = new VehicleRegistryPersistence(cache.toFile(), registry);
        assertTrue(persistence.save());
        String main = Files.readString(cache.resolve("vehicles_registry.json"));
        assertTrue(main.contains("personal"));
        assertFalse(main.contains("UPKEEP"));
        assertFalse(main.contains("guildId"), "rows without a guild keep the old format");
        String upkeep = Files.readString(cache.resolve(VehicleRegistryPersistence.UPKEEP_FILE));
        assertTrue(upkeep.contains("\"smiths\""));

        PlayerVehicleRegistry loaded = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(cache.toFile(), loaded).load();
        assertEquals(3, loaded.getAll().size());
        PlayerVehicleRecord guild = loaded.getByVehicleUuid("guilds").orElseThrow();
        assertEquals(OwnershipMode.UPKEEP, guild.getMode());
        assertEquals("smiths", guild.getGuildId());
        assertNull(guild.getFactionId());
        assertEquals("realm", loaded.getByVehicleUuid("kept").orElseThrow().getFactionId());
        assertEquals(owner, guild.getPlayerUuid());

        registry.unregister("kept");
        registry.unregister("guilds");
        assertTrue(persistence.save());
        assertEquals("[]", Files.readString(cache.resolve(VehicleRegistryPersistence.UPKEEP_FILE)).replaceAll("\\s", ""),
                "the last upkeep row leaving empties the file");
    }

    @Test
    void noUpkeepFileIsWrittenUntilTheFirstUpkeepRow() throws Exception {
        Path cache = cache();
        VehicleRegistryPersistence.write(cache.resolve("vehicles_registry.json"),
                List.of(new PlayerVehicleRecord(null, "v", "sloop", OwnershipMode.PERSONAL, null)));
        assertFalse(Files.exists(cache.resolve(VehicleRegistryPersistence.UPKEEP_FILE)));
    }

    @Test
    void theRegistrysOwnRowWinsOverAnUpkeepRowLeftByAnInterruptedSave() throws Exception {
        Path cache = cache();
        Files.writeString(cache.resolve("vehicles_registry.json"),
                "[{\"vehicleUuid\":\"v\",\"vehicleTypeId\":\"sloop\",\"mode\":\"POOL\",\"factionId\":\"realm\"}]");
        Files.writeString(cache.resolve(VehicleRegistryPersistence.UPKEEP_FILE),
                "[{\"vehicleUuid\":\"v\",\"vehicleTypeId\":\"sloop\",\"mode\":\"UPKEEP\",\"factionId\":\"realm\"},"
                + "{\"vehicleUuid\":\"w\",\"vehicleTypeId\":\"sloop\",\"mode\":\"UPKEEP\",\"guildId\":\"smiths\"}]");
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(cache.toFile(), registry).load();
        assertEquals(OwnershipMode.POOL, registry.getByVehicleUuid("v").orElseThrow().getMode());
        assertEquals(OwnershipMode.UPKEEP, registry.getByVehicleUuid("w").orElseThrow().getMode());
    }

    @Test
    void anUpkeepFileAloneStillLoads() throws Exception {
        Path cache = cache();
        Files.writeString(cache.resolve(VehicleRegistryPersistence.UPKEEP_FILE),
                "[{\"vehicleUuid\":\"w\",\"vehicleTypeId\":\"sloop\",\"mode\":\"UPKEEP\",\"guildId\":\"smiths\"}]");
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(cache.toFile(), registry).load();
        assertEquals("smiths", registry.getByVehicleUuid("w").orElseThrow().getGuildId());
    }

    @Test
    void anythingButAnUpkeepRowInTheUpkeepFileBlocksSaving() throws Exception {
        Path cache = cache();
        Files.writeString(cache.resolve(VehicleRegistryPersistence.UPKEEP_FILE),
                "[{\"vehicleUuid\":\"w\",\"vehicleTypeId\":\"sloop\",\"mode\":\"POOL\"}]");
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        VehicleRegistryPersistence persistence = new VehicleRegistryPersistence(cache.toFile(), registry);
        assertThrows(IllegalStateException.class, persistence::load);
        assertFalse(persistence.save());
    }

    @Test
    void upkeepRowsAreNeitherPersonalNorWarRows() {
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        registry.register(PlayerVehicleRecord.upkeep(null, "kept", "sloop", "realm", null));
        registry.register(PlayerVehicleRecord.upkeep(null, "guilds", "sloop", null, "smiths"));
        registry.register(new PlayerVehicleRecord(null, "pooled", "sloop", OwnershipMode.POOL, null, "realm"));
        assertTrue(registry.isFactionOwned("kept"));
        assertFalse(registry.isBerthed("kept"));
        assertEquals(List.of("pooled"), registry.getPoolVehicles("realm").stream().map(PlayerVehicleRecord::getVehicleUuid).toList());
        assertTrue(registry.getByInstallationId(null).isEmpty());
        assertEquals(List.of("kept"), registry.getUpkeepVehicles("REALM", null).stream()
                .map(PlayerVehicleRecord::getVehicleUuid).toList());
        assertEquals(List.of("guilds"), registry.getUpkeepVehicles(null, "Smiths").stream()
                .map(PlayerVehicleRecord::getVehicleUuid).toList());
        assertTrue(registry.getUpkeepVehicles(null, null).isEmpty());
        PlayerVehicleRecord withAccount = registry.getByVehicleUuid("guilds").orElseThrow().withAccount(UUID.randomUUID());
        assertEquals("smiths", withAccount.getGuildId());
    }
}
