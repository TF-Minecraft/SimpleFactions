package net.tfminecraft.simplefactions.vehicles.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.database.VehicleRepository;
import net.tfminecraft.vehicleframework.data.StoredVehicleMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VehiclePayerMigrationTest {
    @TempDir Path folder;
    final CharacterRef heir = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    StoredVehicleMeta vehicle(String id, String owner) { return new StoredVehicleMeta(id, id, "ship", owner); }

    @Test void persistedOwnersMapOnceAndPersonalRowsSurviveReloadIncludingUnresolvedQuarantine() throws Exception {
        var registry = new PlayerVehicleRegistry();
        UUID existing = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(existing, "existing", "ship", OwnershipMode.PERSONAL, null));
        VehiclePayerMigration.migrate(folder, registry, List.of(vehicle("mapped", "player_OldName"),
                vehicle("unknown", "player_Unknown"), vehicle("existing", "player_Wrong")),
                name -> name.equals("OldName") ? heir.ownerId() : null, id -> heir);
        assertEquals(heir.characterId(), registry.getByVehicleUuid("mapped").orElseThrow().getPlayerUuid());
        assertNull(registry.getByVehicleUuid("unknown").orElseThrow().getPlayerUuid());
        assertEquals(existing, registry.getByVehicleUuid("existing").orElseThrow().getPlayerUuid());
        String original = Files.readString(folder.resolve("identity/vehicle-payers.json"));
        assertTrue(original.contains("player_Unknown"));
        var reopened = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(folder.resolve("Cache").toFile(), reopened).load();
        VehiclePayerMigration.migrate(folder, reopened, List.of(vehicle("unknown", "player_NewOwner"), vehicle("new", "player_NewOwner")),
                name -> { fail("Completed migration must not infer ownership again"); return null; }, id -> heir);
        assertEquals(3, reopened.getAll().size());
        assertNull(reopened.getByVehicleUuid("unknown").orElseThrow().getPlayerUuid());
        assertEquals(original, Files.readString(folder.resolve("identity/vehicle-payers.json")));
    }

    @Test void savedPlanReplaysWithoutProviderAfterInterruptedRegistryWrite() throws Exception {
        Files.writeString(folder.resolve("Cache"), "blocked");
        var registry = new PlayerVehicleRegistry();
        assertThrows(IOException.class, () -> VehiclePayerMigration.migrate(folder, registry,
                List.of(vehicle("v", "player_Owner")), ignored -> heir.ownerId(), ignored -> heir));
        assertTrue(registry.getAll().isEmpty());
        Files.delete(folder.resolve("Cache"));
        VehiclePayerMigration.startup(folder, registry);
        assertEquals(heir.characterId(), registry.getByVehicleUuid("v").orElseThrow().getPlayerUuid());
        assertTrue(Files.readString(folder.resolve("identity/vehicle-payers.json")).contains("\"completed\": true"));
    }

    @Test void wrongAccountHeirAndUnknownAccountRemainUnmapped() throws Exception {
        var registry = new PlayerVehicleRegistry();
        VehiclePayerMigration.migrate(folder, registry, List.of(vehicle("v", "player_Wrong")),
                ignored -> UUID.randomUUID(), ignored -> heir);
        assertNull(registry.getByVehicleUuid("v").orElseThrow().getPlayerUuid());
    }

    @Test void runtimeRequiresReadyHeirsAndAnAllVehicleRepositoryAndPropagatesReadFailure() throws Exception {
        var registry = new PlayerVehicleRegistry();
        try (var chars = mockStatic(Characters.class); var vf = mockStatic(VehicleFramework.class)) {
            assertThrows(IOException.class, () -> VehiclePayerMigration.startup(folder, registry));
            chars.when(Characters::legacyHeirsReady).thenReturn(true);
            assertThrows(IOException.class, () -> VehiclePayerMigration.startup(folder, registry));
            var repository = mock(VehicleRepository.class);
            vf.when(VehicleFramework::getVehicleRepository).thenReturn(repository);
            when(repository.listPlayerOwnedMeta()).thenThrow(new IllegalStateException("broken SQLite"));
            assertThrows(IOException.class, () -> VehiclePayerMigration.startup(folder, registry));
            assertFalse(Files.exists(folder.resolve("identity/vehicle-payers.json")));
            doReturn(List.of(vehicle("v", "player_" + heir.ownerId()))).when(repository).listPlayerOwnedMeta();
            chars.when(() -> Characters.legacyHeir(heir.ownerId())).thenReturn(Optional.of(heir));
            VehiclePayerMigration.startup(folder, registry);
            assertEquals(heir.characterId(), registry.getByVehicleUuid("v").orElseThrow().getPlayerUuid());
        }
    }

    @Test void invalidSavedPlanIsNeverReplaced() throws Exception {
        Files.createDirectories(folder.resolve("identity"));
        Files.writeString(folder.resolve("identity/vehicle-payers.json"), "{\"rows\":null}");
        assertThrows(IOException.class, () -> VehiclePayerMigration.startup(folder, new PlayerVehicleRegistry()));
        String invalid = "{\"rows\":[{\"vehicle\":\"v\",\"type\":\"ship\"}]}";
        Files.writeString(folder.resolve("identity/vehicle-payers.json"), invalid);
        assertThrows(IOException.class, () -> VehiclePayerMigration.startup(folder, new PlayerVehicleRegistry()));
        assertEquals(invalid, Files.readString(folder.resolve("identity/vehicle-payers.json")));
    }

    @Test void persistedPlayerNameResolvesAnAccountBeforeLookingUpItsHeir() throws Exception {
        var registry = new PlayerVehicleRegistry();
        try (var chars = mockStatic(Characters.class); var vf = mockStatic(VehicleFramework.class);
                var ownership = mockStatic(VehicleOwnershipQueries.class, CALLS_REAL_METHODS)) {
            chars.when(Characters::legacyHeirsReady).thenReturn(true);
            var repository = mock(VehicleRepository.class);
            vf.when(VehicleFramework::getVehicleRepository).thenReturn(repository);
            when(repository.listPlayerOwnedMeta()).thenReturn(List.of(vehicle("v", "player_OldName")));
            ownership.when(() -> VehicleOwnershipQueries.resolvePlayerUuid("OldName")).thenReturn(heir.ownerId());
            chars.when(() -> Characters.legacyHeir(heir.ownerId())).thenReturn(Optional.of(heir));
            VehiclePayerMigration.startup(folder, registry);
            assertEquals(heir.characterId(), registry.getByVehicleUuid("v").orElseThrow().getPlayerUuid());
            assertTrue(Files.readString(folder.resolve("identity/vehicle-payers.json")).contains("player_OldName"));
        }
    }
}
