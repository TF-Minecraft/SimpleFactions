package net.tfminecraft.simplefactions.vehicles.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.database.DurableJson;

class VehicleRegistryUpkeepFileTest {
    @TempDir Path folder;
    private final UUID owner = UUID.randomUUID();

    private Path cache() throws Exception {
        return Files.createDirectories(folder.resolve("Cache"));
    }

    private Path main() throws Exception {
        return cache().resolve("vehicles_registry.json");
    }

    private Path upkeep() throws Exception {
        return cache().resolve(VehicleRegistryPersistence.UPKEEP_FILE);
    }

    private PlayerVehicleRegistry load() throws Exception {
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(cache().toFile(), registry).load();
        return registry;
    }

    private OwnershipMode mode(PlayerVehicleRegistry registry, String vehicle) {
        return registry.getByVehicleUuid(vehicle).map(PlayerVehicleRecord::getMode).orElse(null);
    }

    private PlayerVehicleRecord personal(String vehicle) {
        return new PlayerVehicleRecord(owner, vehicle, "sloop", OwnershipMode.PERSONAL, null);
    }

    private PlayerVehicleRecord kept(String vehicle) {
        return PlayerVehicleRecord.upkeep(owner, vehicle, "sloop", "realm", null);
    }

    @Test
    void upkeepRowsAreSavedApartSoTheRegistryStaysReadableForOlderPlugins() throws Exception {
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        registry.register(personal("personal"));
        registry.register(kept("kept"));
        registry.register(PlayerVehicleRecord.upkeep(owner, "guilds", "sloop", "ignored", "smiths"));
        VehicleRegistryPersistence persistence = new VehicleRegistryPersistence(cache().toFile(), registry);
        assertTrue(persistence.save());
        String saved = Files.readString(main());
        assertTrue(saved.contains("personal"));
        assertFalse(saved.contains("UPKEEP"));
        assertFalse(saved.contains("guildId"), "rows without a guild keep the old format");
        JsonObject state = JsonParser.parseString(Files.readString(upkeep())).getAsJsonObject();
        assertEquals(2, state.getAsJsonArray("rows").size());
        assertTrue(state.has("registry"));

        PlayerVehicleRegistry loaded = load();
        assertEquals(3, loaded.getAll().size());
        PlayerVehicleRecord guild = loaded.getByVehicleUuid("guilds").orElseThrow();
        assertEquals("smiths", guild.getGuildId());
        assertNull(guild.getFactionId());
        assertEquals("realm", loaded.getByVehicleUuid("kept").orElseThrow().getFactionId());

        registry.unregister("kept");
        registry.unregister("guilds");
        assertTrue(persistence.save());
        assertEquals(0, JsonParser.parseString(Files.readString(upkeep())).getAsJsonObject().getAsJsonArray("rows").size(),
                "the last upkeep row leaving empties the file");
        assertEquals(1, load().getAll().size());
    }

    @Test
    void noUpkeepFileIsWrittenUntilTheFirstUpkeepRow() throws Exception {
        VehicleRegistryPersistence.write(main(), List.of(personal("v")));
        assertFalse(Files.exists(upkeep()));
    }

    @Test
    void anUpkeepFileWrittenBeforeItNamedItsRegistryStillLoadsAndTheRegistryWins() throws Exception {
        Files.writeString(main(), "[{\"vehicleUuid\":\"v\",\"vehicleTypeId\":\"sloop\",\"mode\":\"POOL\",\"factionId\":\"realm\"}]");
        Files.writeString(upkeep(), "[{\"vehicleUuid\":\"v\",\"vehicleTypeId\":\"sloop\",\"mode\":\"UPKEEP\",\"factionId\":\"realm\"},"
                + "{\"vehicleUuid\":\"w\",\"vehicleTypeId\":\"sloop\",\"mode\":\"UPKEEP\",\"guildId\":\"smiths\"}]");
        PlayerVehicleRegistry registry = load();
        assertEquals(OwnershipMode.POOL, mode(registry, "v"));
        assertEquals(OwnershipMode.UPKEEP, mode(registry, "w"));
    }

    @Test
    void anUpkeepFileAloneStillLoads() throws Exception {
        Files.writeString(upkeep(), "[{\"vehicleUuid\":\"w\",\"vehicleTypeId\":\"sloop\",\"mode\":\"UPKEEP\",\"guildId\":\"smiths\"}]");
        assertEquals("smiths", load().getByVehicleUuid("w").orElseThrow().getGuildId());
        Files.delete(upkeep());
        VehicleRegistryPersistence.write(main(), List.of(kept("k")));
        Files.delete(main());
        assertEquals(OwnershipMode.UPKEEP, mode(load(), "k"), "rows written for an empty registry load without it");
    }

    @Test
    void anythingButAnUpkeepRowInTheUpkeepFileBlocksSaving() throws Exception {
        Files.writeString(upkeep(), "[{\"vehicleUuid\":\"w\",\"vehicleTypeId\":\"sloop\",\"mode\":\"POOL\"}]");
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        VehicleRegistryPersistence persistence = new VehicleRegistryPersistence(cache().toFile(), registry);
        assertThrows(IllegalStateException.class, persistence::load);
        assertFalse(persistence.save());
        Files.writeString(upkeep(), "{\"registry\":\"x\",\"rows\":null}");
        assertThrows(IllegalStateException.class, persistence::load);
        Files.writeString(upkeep(), "null");
        assertThrows(IllegalStateException.class, persistence::load);
    }

    @Test
    void aFailedRegistryWriteLeavesAnUpkeepRowLeavingWhereItWas() throws Exception {
        VehicleRegistryPersistence.write(main(), List.of(kept("boat"), personal("other")));
        try (var durable = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
            Path registryFile = main();
            durable.when(() -> DurableJson.write(eq(registryFile), any())).thenThrow(new IOException("disk"));
            assertThrows(IOException.class,
                    () -> VehicleRegistryPersistence.write(registryFile, List.of(personal("boat"), personal("other"))));
        }
        assertEquals(OwnershipMode.UPKEEP, mode(load(), "boat"), "a save that reported failure changed nothing");
        VehicleRegistryPersistence.write(main(), List.of(personal("boat"), personal("other")));
        assertEquals(OwnershipMode.PERSONAL, mode(load(), "boat"));
    }

    @Test
    void aFailedRegistryWriteLeavesAPoolVehicleOutOfUpkeep() throws Exception {
        PlayerVehicleRecord pooled = new PlayerVehicleRecord(owner, "boat", "sloop", OwnershipMode.POOL, null, "realm");
        VehicleRegistryPersistence.write(main(), List.of(pooled, kept("older")));
        try (var durable = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
            Path registryFile = main();
            durable.when(() -> DurableJson.write(eq(registryFile), any())).thenThrow(new IOException("disk"));
            assertThrows(IOException.class, () -> VehicleRegistryPersistence.write(registryFile, List.of(kept("boat"), kept("older"))));
        }
        PlayerVehicleRegistry registry = load();
        assertEquals(OwnershipMode.POOL, mode(registry, "boat"));
        assertEquals(OwnershipMode.UPKEEP, mode(registry, "older"));
    }

    @Test
    void whenOnlyUpkeepRowsChangeTheUpkeepWriteAloneCommits() throws Exception {
        VehicleRegistryPersistence.write(main(), List.of(personal("other"), kept("leaving")));
        try (var durable = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
            Path registryFile = main();
            Path upkeepFile = upkeep();
            durable.when(() -> DurableJson.write(eq(upkeepFile), any())).thenThrow(new IOException("disk"));
            assertThrows(IOException.class, () -> VehicleRegistryPersistence.write(registryFile, List.of(personal("other"), kept("joining"))));
            PlayerVehicleRegistry unchanged = load();
            assertEquals(OwnershipMode.UPKEEP, mode(unchanged, "leaving"));
            assertNull(mode(unchanged, "joining"));
            durable.when(() -> DurableJson.write(eq(upkeepFile), any())).thenCallRealMethod();
            VehicleRegistryPersistence.write(registryFile, List.of(personal("other"), kept("joining")));
            durable.verify(() -> DurableJson.write(eq(registryFile), any()), never());
        }
        PlayerVehicleRegistry registry = load();
        assertNull(mode(registry, "leaving"));
        assertEquals(OwnershipMode.UPKEEP, mode(registry, "joining"));
    }

    @Test
    void aRegistryChangedByAnOlderPluginKeepsTheNewestUpkeepRowsAndWinsOnConflicts() throws Exception {
        VehicleRegistryPersistence.write(main(), List.of(personal("other"), kept("boat"), kept("cart")));
        Files.writeString(main(), "[{\"playerUuid\":\"" + owner + "\",\"vehicleUuid\":\"boat\",\"vehicleTypeId\":\"sloop\",\"mode\":\"PERSONAL\"}]");
        PlayerVehicleRegistry registry = load();
        assertEquals(OwnershipMode.PERSONAL, mode(registry, "boat"));
        assertEquals(OwnershipMode.UPKEEP, mode(registry, "cart"));
        assertNull(mode(registry, "other"));
    }

    @Test
    void anUnreadableSavedFileIsReplacedOnTheNextWrite() throws Exception {
        Files.writeString(upkeep(), "{broken");
        VehicleRegistryPersistence.write(main(), List.of(kept("kept"), personal("other")));
        assertEquals(OwnershipMode.UPKEEP, mode(load(), "kept"));
        Files.writeString(main(), "{broken");
        VehicleRegistryPersistence.write(main(), List.of(kept("kept"), personal("other")));
        PlayerVehicleRegistry registry = load();
        assertEquals(OwnershipMode.UPKEEP, mode(registry, "kept"));
        assertEquals(OwnershipMode.PERSONAL, mode(registry, "other"));
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
