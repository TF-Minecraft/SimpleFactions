package net.tfminecraft.simplefactions.vehicles.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries.PersonalVehicle;

class VehicleOwnershipQueriesTest {
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();

    @Test
    void personalVehiclesAreCharacterAndLegacyOwnersExceptFactionVehicles() {
        UUID character = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(character, "berthed", "ironclad", OwnershipMode.INSTALLATION, "port"));
        registry.register(new PlayerVehicleRecord(character, "pooled", "coal_car", OwnershipMode.POOL, null, "red"));
        FakeOwnerApi.install()
                .stored("personal", "ironclad", "char_" + character)
                .live("legacy", "cart", "player_Alice")
                .stored("berthed", "ironclad", "char_" + character)
                .stored("pooled", "coal_car", "char_none")
                .stored("leaderless", "cart", "char_none")
                .stored("unowned", "cart", "none")
                .stored("nobody", "cart", "player_none");

        assertEquals(List.of(new PersonalVehicle("personal", "ironclad"), new PersonalVehicle("legacy", "cart")),
                VehicleOwnershipQueries.allPersonalVehicles(registry));
    }

    @Test
    void personalRegistryRowsStandInWithoutAReadableVehicleFramework() {
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "personal", "cart", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "pooled", "cart", OwnershipMode.POOL, null, "red"));
        List<PersonalVehicle> rows = List.of(new PersonalVehicle("personal", "cart"));

        assertEquals(rows, VehicleOwnershipQueries.allPersonalVehicles(registry));
        FakeOwnerApi api = FakeOwnerApi.install().stored("other", "cart", "char_" + UUID.randomUUID());
        api.failure = new IllegalStateException("SQLite closed");
        assertEquals(rows, VehicleOwnershipQueries.allPersonalVehicles(registry));
    }

    @Test
    void recordsAreWrittenForThePlayersCurrentCharacter() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        assertEquals(NameKeyedMembers.characterOf("Alice"), VehicleOwnershipQueries.characterId(player));
        assertNull(VehicleOwnershipQueries.characterId(null));
    }

    @Test
    void theFallbackToRegistryRowsIsLogged() {
        FakeOwnerApi.install().failure = new IllegalStateException("SQLite closed");
        net.tfminecraft.simplefactions.SimpleFactions plugin = mock(net.tfminecraft.simplefactions.SimpleFactions.class);
        java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        try (var sf = org.mockito.Mockito.mockStatic(net.tfminecraft.simplefactions.SimpleFactions.class)) {
            sf.when(net.tfminecraft.simplefactions.SimpleFactions::getInstance).thenReturn(plugin);
            assertEquals(List.of(), VehicleOwnershipQueries.allPersonalVehicles(registry));
        }
        org.mockito.Mockito.verify(logger).warning(org.mockito.ArgumentMatchers.contains("using personal registry rows"));
    }
}
