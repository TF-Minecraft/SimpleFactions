package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.vehicleframework.api.OwnerChange;
import net.tfminecraft.vehicleframework.api.OwnershipIdentity;
import net.tfminecraft.vehicleframework.api.OwnershipMeta;
import net.tfminecraft.vehicleframework.api.OwnershipValues;
import net.tfminecraft.vehicleframework.api.SystemReason;
import net.tfminecraft.vehicleframework.api.VehicleFrameworkApi;

class VehicleOwnerWritesTest {
    private final UUID id = UUID.randomUUID();

    @Test
    void theProbeFindsVehicleFrameworksOwnershipApi() {
        assertInstanceOf(VfOwnerApi.class, VehicleOwnerWrites.probe());
        assertNull(VehicleOwnerWrites.probe("net.tfminecraft.vehicleframework.api.Missing"),
                "An older VehicleFramework has no ownership API");
        assertNull(VehicleOwnerWrites.probe("java.lang.Object"));
    }

    @Test
    void withoutVehicleFrameworkEveryWriteIsUnavailableAndNothingCanBeRead() {
        assertNull(VehicleOwnerWrites.api());
        assertFalse(VehicleOwnerWrites.ready());
        assertEquals(Result.UNAVAILABLE, VehicleOwnerWrites.write("v", "none", Reason.RESTAMP));
        assertEquals(Optional.empty(), VehicleOwnerWrites.read("v"));
        assertNull(VehicleOwnerWrites.currentOwner("v"));
    }

    @Test
    void writesAndReadsGoThroughTheInstalledApiAndFailuresAreContained() {
        FakeOwnerApi api = FakeOwnerApi.install().stored("v", "cart", "player_Old");
        assertSame(api, VehicleOwnerWrites.api());
        assertTrue(VehicleOwnerWrites.ready());
        assertEquals(Result.OK, VehicleOwnerWrites.write("v", "char_none", Reason.RESTAMP));
        assertEquals("char_none", VehicleOwnerWrites.currentOwner("v"));
        assertEquals(Optional.empty(), VehicleOwnerWrites.read(null));
        api.characters = false;
        assertFalse(VehicleOwnerWrites.ready());

        SimpleFactions plugin = mock(SimpleFactions.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        api.failure = new IllegalStateException("closed");
        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            assertEquals(Result.SAVE_FAILED, VehicleOwnerWrites.write("v", "none", Reason.RELEASE));
            assertEquals(Optional.empty(), VehicleOwnerWrites.read("v"));
        }
        verify(logger).log(eq(Level.WARNING), contains("Could not set the owner of vehicle v to none (RELEASE)"),
                any(IllegalStateException.class));
        verify(logger).log(eq(Level.WARNING), contains("Could not read the owner of vehicle v"),
                any(IllegalStateException.class));
        assertEquals(Result.SAVE_FAILED, VehicleOwnerWrites.write("v", "none", Reason.RELEASE));

        VehicleOwnerWrites.use(brokenMode());
        assertFalse(VehicleOwnerWrites.ready(), "A VehicleFramework without the mode accessor is not ready");
    }

    private static VehicleOwnerApi brokenMode() {
        VehicleOwnerApi api = mock(VehicleOwnerApi.class);
        when(api.characters()).thenThrow(new NoSuchMethodError("ownershipIdentity"));
        return api;
    }

    @Test
    void theBridgeTranslatesVehicleFrameworksTypes() {
        VfOwnerApi bridge = new VfOwnerApi();
        OwnershipValues stored = new OwnershipValues("player_Old", List.of("Bob"), true, true, "t1");
        OwnershipValues live = new OwnershipValues("char_none", List.of(), false, false, null);
        OwnershipMeta meta = new OwnershipMeta(id, "cart", "Cart", "player_Old", Optional.of(stored), Optional.of(live));
        try (MockedStatic<VehicleFrameworkApi> vf = mockStatic(VehicleFrameworkApi.class)) {
            vf.when(VehicleFrameworkApi::ownershipIdentity).thenReturn(OwnershipIdentity.CHARACTERS);
            assertTrue(bridge.characters());
            vf.when(VehicleFrameworkApi::ownershipIdentity).thenReturn(OwnershipIdentity.NAMES);
            assertFalse(bridge.characters());

            vf.when(VehicleFrameworkApi::listOwnershipMeta).thenReturn(List.of(meta));
            OwnerMeta converted = bridge.list().getFirst();
            assertEquals(id.toString(), converted.vehicle());
            assertEquals("cart", converted.typeId());
            assertEquals("player_Old", converted.storedColumn());
            assertEquals(new OwnerFields("player_Old", List.of("Bob"), true, true, "t1"), converted.stored().orElseThrow());
            assertEquals(new OwnerFields("char_none", List.of(), false, false, null), converted.live().orElseThrow());

            vf.when(() -> VehicleFrameworkApi.readOwnershipMeta(id)).thenReturn(Optional.of(meta));
            assertEquals(converted, bridge.read(id.toString()).orElseThrow());
            assertEquals(Optional.empty(), bridge.read("not-a-uuid"));
            assertEquals(Optional.empty(), bridge.read(null));

            UUID kira = UUID.randomUUID();
            vf.when(() -> VehicleFrameworkApi.setOwnership(id, OwnerChange.character(kira), SystemReason.RESTAMP))
                    .thenReturn(VehicleFrameworkApi.Result.OK);
            assertEquals(Result.OK, bridge.write(id.toString(), "char_" + kira, null, Reason.RESTAMP));
            vf.when(() -> VehicleFrameworkApi.setOwnership(id, OwnerChange.seeded("char_" + kira, List.of("e")),
                    SystemReason.MIGRATE)).thenReturn(VehicleFrameworkApi.Result.SAVE_FAILED);
            assertEquals(Result.SAVE_FAILED, bridge.write(id.toString(), "char_" + kira, List.of("e"), Reason.MIGRATE));
            assertEquals(Result.NOT_FOUND, bridge.write("not-a-uuid", "none", null, Reason.RELEASE));
            assertEquals(Result.NOT_FOUND, bridge.write(null, "none", null, Reason.RELEASE));
            assertThrows(IllegalArgumentException.class,
                    () -> bridge.write(id.toString(), "player_Steve", null, Reason.RESTAMP));
        }
    }
}
