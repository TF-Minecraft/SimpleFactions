package net.tfminecraft.simplefactions.vehicles.berth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.LeaderStamps;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

class InstallationVehicleOwnerSyncTest {
    private List<Faction> previousFactions;
    private PlayerVehicleRegistry registry;
    private InstallationVehicleOwnerSync ownerSync;
    private FakeOwnerApi api;
    private final UUID carol = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();
        registry = new PlayerVehicleRegistry();
        ownerSync = new InstallationVehicleOwnerSync(registry);
        api = FakeOwnerApi.install();
        LeaderStamps.enable(true);
    }

    @AfterEach
    void tearDown() {
        FactionManager.factions = previousFactions;
    }

    private Faction faction(String id, String leader) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        when(faction.getLeader()).thenReturn(leader);
        FactionManager.factions.add(faction);
        return faction;
    }

    private static ActiveVehicle loaded(String uuid, String owner) {
        ActiveVehicle vehicle = mock(ActiveVehicle.class);
        OwnerData data = new OwnerData();
        data.setOwner(owner);
        when(vehicle.getUUID()).thenReturn(uuid);
        when(vehicle.getOwnerData()).thenReturn(data);
        return vehicle;
    }

    @Test
    void aVehicleJoiningAFactionIsStampedForItsLeaderAndLosesItsCrew() {
        api.live("ship", "ironclad", "char_" + UUID.randomUUID(), "char_" + UUID.randomUUID() + ";x;Crew");

        assertEquals(Result.OK, InstallationVehicleOwnerSync.stampForFaction("ship", faction("red", carol.toString())));

        assertEquals("char_" + carol, api.owner("ship"));
        assertEquals(List.of(), api.vehicles.get("ship").current().orElseThrow().whitelist());
        assertEquals(List.of("ship char_" + carol + " TRANSFER"), api.writes);
    }

    @Test
    void factionsWithoutALeaderCharacterStampNobody() {
        api.live("a", "cart", "none").live("b", "cart", "none").live("c", "cart", "none");

        InstallationVehicleOwnerSync.stampForFaction("a", faction("red", "dummy_2"));
        InstallationVehicleOwnerSync.stampForFaction("b", faction("blue", ""));
        InstallationVehicleOwnerSync.stampForFaction("c", null);

        assertEquals(List.of("char_none", "char_none", "char_none"), List.of(api.owner("a"), api.owner("b"),
                api.owner("c")));
    }

    @Test
    void aSpawnedInstallationVehicleWithAStaleStampIsRestamped() {
        Installation port = new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L);
        Faction faction = faction("red", carol.toString());
        InstallationHandler handler = mock(InstallationHandler.class);
        when(handler.getById("port-1")).thenReturn(port);
        when(faction.getInstallationHandler()).thenReturn(handler);
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "ship", "ironclad", OwnershipMode.INSTALLATION,
                "port-1", "red"));
        api.live("ship", "ironclad", "player_Carol");

        ownerSync.syncIfBerthed(loaded("ship", "player_Carol"));

        assertEquals(List.of("ship char_" + carol + " RESTAMP"), api.writes);
    }

    @Test
    void aSpawnedPoolVehicleAlreadyStampedOrAPersonalOneIsLeftAlone() {
        faction("red", carol.toString());
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "gun", "cannon", OwnershipMode.POOL, null, "red"));
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "cart", "cart", OwnershipMode.PERSONAL, null));

        ownerSync.syncIfBerthed(loaded("gun", "char_" + carol));
        ownerSync.syncIfBerthed(loaded("cart", "char_" + UUID.randomUUID()));
        ownerSync.syncIfBerthed(loaded("unregistered", "none"));
        ownerSync.syncIfBerthed(null);
        ownerSync.syncIfBerthed(loaded(null, "none"));

        assertEquals(List.of(), api.writes);
    }

    @Test
    void spawnsBeforeTheVehicleStageFinishesAreLeftToTheBootReconcile() {
        faction("red", carol.toString());
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "gun", "cannon", OwnershipMode.POOL, null, "red"));
        LeaderStamps.enable(false);

        ownerSync.syncIfBerthed(loaded("gun", "player_Carol"));

        assertEquals(List.of(), api.writes);
    }

    @Test
    void aFailedSpawnRestampIsLogged() {
        faction("red", carol.toString());
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "gun", "cannon", OwnershipMode.POOL, null, "red"));
        api.live("gun", "cannon", "player_Carol");
        api.forced = Result.SAVE_FAILED;
        SimpleFactions plugin = mock(SimpleFactions.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);

        ownerSync.syncIfBerthed(loaded("gun", "player_Carol"));
        try (var sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            ownerSync.syncIfBerthed(loaded("gun", "player_Carol"));
        }

        verify(logger).warning(contains("SAVE_FAILED"));
    }
}
