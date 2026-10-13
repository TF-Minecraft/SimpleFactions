package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeConfirmations;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleRegistrationFeeListener;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vfbuilders.events.VehicleConstructEvent;

class ConstructionStampTest {
    @TempDir Path folder;
    private final VehicleFeeStore store = new VehicleFeeStore();
    private final UUID account = UUID.randomUUID();
    private final UUID payer = NameKeyedMembers.characterOf("Builder");
    private final Logger logger = mock(Logger.class);
    private FakeOwnerApi api;
    private VehiclePayments payments;
    private MockedStatic<VehiclePayments> paymentsStatic;
    private MockedStatic<SimpleFactions> plugin;
    private MockedStatic<org.bukkit.Bukkit> bukkit;
    private ActiveStation station;
    private ActiveVehicle vehicle;
    private VehicleRegistrationFeeListener listener;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(org.bukkit.Bukkit.class);
        store.bind(folder.toFile());
        api = FakeOwnerApi.install().live("ship-1", "ship", "none");
        payments = mock(VehiclePayments.class);
        when(payments.personal(anyString(), anyString(), any(), any())).thenReturn(true);
        paymentsStatic = mockStatic(VehiclePayments.class);
        paymentsStatic.when(VehiclePayments::current).thenReturn(payments);
        SimpleFactions instance = mock(SimpleFactions.class);
        when(instance.getLogger()).thenReturn(logger);
        plugin = mockStatic(SimpleFactions.class);
        plugin.when(SimpleFactions::getInstance).thenReturn(instance);
        station = mock(ActiveStation.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(station.getLocation()).thenReturn(new Location(world, 6, 64, 7));
        vehicle = mock(ActiveVehicle.class);
        when(vehicle.getUUID()).thenReturn("ship-1");
        when(vehicle.getId()).thenReturn("ship");
        store.beginBuild("world:6:64:7", payer.toString());
        listener = new VehicleRegistrationFeeListener(store, new VehicleFeeConfirmations(), () -> {});
        VehicleQuarantine.use(new VehicleQuarantine(folder));
    }

    @AfterEach
    void tearDown() {
        plugin.close();
        paymentsStatic.close();
        bukkit.close();
        ConstructionStamp.resetProbe();
    }

    private VehicleConstructEvent built(UUID constructorCharacter) {
        return new VehicleConstructEvent(account, constructorCharacter, vehicle, mock(Blueprint.class),
                station.getLocation(), station);
    }

    @Test
    void theBuilderWhoPaidOwnsTheirVehicleWithAnEmptyCrew() {
        listener.onVehicleConstruct(built(payer));

        assertEquals("char_" + payer, api.owner("ship-1"));
        assertEquals(List.of("ship-1 char_" + payer + " CONSTRUCT"), api.writes);
        verify(payments).personal("ship-1", "ship", payer, null);
        verify(payments).complete("build:world:6:64:7", null);
        assertEquals("Builder", store.getLastOwner("ship-1"));
        assertEquals(List.of(), VehicleQuarantine.current().list());
    }

    @Test
    void aPaidReceiptNamesThePayerAndItsAccount() {
        UUID payerAccount = UUID.randomUUID();
        when(payments.receipt("build:world:6:64:7")).thenReturn(new VehiclePayments.Receipt("r",
                new CharacterRef(payerAccount, payer), "red", UUID.randomUUID(), FeeKind.REGISTRATION_FEE, 100));

        listener.onVehicleConstruct(built(payer));

        verify(payments).personal("ship-1", "ship", payer, payerAccount);
    }

    @Test
    void aPayerWhoIsNotTheConstructorLeavesTheVehicleToNobodyAndListsItForStaff() {
        UUID constructor = UUID.randomUUID();

        listener.onVehicleConstruct(built(constructor));

        assertEquals("char_none", api.owner("ship-1"));
        VehicleQuarantine.Entry entry = VehicleQuarantine.current().list().getFirst();
        assertEquals("ship-1", entry.vehicle());
        assertEquals("char_none", entry.owner());
        assertTrue(entry.reason().contains(payer + " is not the constructor " + constructor));
        verify(payments).complete("build:world:6:64:7", null);
        verify(logger).warning(contains("held for staff"));
    }

    @Test
    void anOlderStationWithoutACharacterIsCheckedAgainstTheAccountsHeir() {
        try (MockedStatic<Characters> characters = mockStatic(Characters.class)) {
            characters.when(() -> Characters.legacyHeir(account)).thenReturn(Optional.of(new CharacterRef(account, payer)));
            listener.onVehicleConstruct(built(null));
            assertEquals("char_" + payer, api.owner("ship-1"));

            characters.when(() -> Characters.legacyHeir(account))
                    .thenReturn(Optional.of(new CharacterRef(UUID.randomUUID(), payer)));
            assertNull(ConstructionStamp.constructor(built(null)), "A foreign heir is not the constructor");
        }
        assertNull(ConstructionStamp.constructor(new VehicleConstructEvent(null, null, vehicle, mock(Blueprint.class),
                station.getLocation(), station)));
    }

    @Test
    void aFailedStampTakesThePayerRowBackAndKeepsTheBuildReceiptOpen() {
        api.forced = Result.SAVE_FAILED;

        listener.onVehicleConstruct(built(payer));

        verify(payments).replaceRow("ship-1", null);
        verify(payments, never()).complete(anyString(), any());
        verify(logger).warning(contains("build receipt build:world:6:64:7 stays open"));
        assertNull(store.getLastOwner("ship-1"));
        // Unowned but held, so nobody claims it while the paid build is unresolved.
        assertTrue(VehicleQuarantine.held("ship-1"));
        assertEquals("none", VehicleQuarantine.current().list().getFirst().owner());
    }

    @Test
    void aPayerRowThatCannotBeWrittenStopsBeforeAnyStamp() {
        when(payments.personal(anyString(), anyString(), any(), any())).thenReturn(false);
        listener.onVehicleConstruct(built(payer));
        store.endBuild("world:6:64:7");
        listener.onVehicleConstruct(built(payer));
        assertEquals(List.of(), api.writes);
    }

    @Test
    void aQuarantineThatCannotBeSavedIsLoggedAndTheVehicleStaysHeld() throws Exception {
        java.nio.file.Files.writeString(folder.resolve("vehicles"), "a file where the folder should be");
        listener.onVehicleConstruct(built(UUID.randomUUID()));
        assertEquals("char_none", api.owner("ship-1"));
        verify(logger).warning(contains("Could not list quarantined vehicle ship-1"));

        VehicleQuarantine.use(null);
        api.live("ship-1", "ship", "none");
        store.beginBuild("world:6:64:7", payer.toString());
        listener.onVehicleConstruct(built(UUID.randomUUID()));
        assertEquals("char_none", api.owner("ship-1"));
        plugin.when(SimpleFactions::getInstance).thenReturn(null);
        api.live("ship-1", "ship", "none");
        store.beginBuild("world:6:64:7", payer.toString());
        listener.onVehicleConstruct(built(UUID.randomUUID()));
        verify(logger, org.mockito.Mockito.times(2)).warning(contains("held for staff"));
    }

    @Test
    void anOlderVFBuildersWithoutCharactersIsDetected() {
        assertTrue(ConstructionStamp.characterApi());
        assertTrue(ConstructionStamp.hasCharacter(VehicleConstructEvent.class));
        assertEquals(false, ConstructionStamp.hasCharacter(Object.class));
    }

    @Test
    void noBuildStartsUntilTheVehicleStageRunsInCharactersMode() {
        org.bukkit.entity.Player builder = mock(org.bukkit.entity.Player.class);
        when(builder.getName()).thenReturn("Builder");
        Blueprint blueprint = mock(Blueprint.class);
        when(blueprint.getId()).thenReturn("ship");
        var begin = new net.tfminecraft.vfbuilders.events.BeginVehicleConstructionEvent(builder, blueprint, station,
                station.getLocation());

        listener.onBeginVehicleConstruction(begin);

        assertTrue(begin.isCancelled());
        verify(builder).sendMessage(VehicleRegistrationFeeListener.OWNERSHIP_STARTING);
        verify(payments, never()).charge(anyString(), anyString(), any(), any());
    }
}
