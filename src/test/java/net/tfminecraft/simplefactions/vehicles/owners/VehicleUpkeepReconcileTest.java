package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

class VehicleUpkeepReconcileTest {
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final Map<UUID, UUID> accounts = new HashMap<>();
    private final List<String> written = new ArrayList<>();
    private final Logger logger = mock(Logger.class);
    private UUID leader;
    private boolean rowsSave = true;
    private FakeOwnerApi api;
    private List<Faction> previousFactions;

    @BeforeEach
    void setUp() {
        api = FakeOwnerApi.install();
        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();
        leader = UUID.randomUUID();
        Faction red = mock(Faction.class);
        when(red.getId()).thenReturn("red");
        when(red.getLeader()).thenReturn(leader.toString());
        FactionManager.factions.add(red);
    }

    @AfterEach
    void tearDown() {
        FactionManager.factions = previousFactions;
    }

    private List<VehicleQuarantine.Entry> run() {
        return new VehicleUpkeepReconcile(api, registry, (vehicle, type, character, account) -> {
            written.add(vehicle + " " + type + " " + character + " " + account);
            return rowsSave;
        }, leader::equals, character -> Optional.ofNullable(accounts.get(character)), logger).run();
    }

    private static Set<String> reasons(List<VehicleQuarantine.Entry> findings) {
        Set<String> out = new java.util.TreeSet<>();
        for (VehicleQuarantine.Entry entry : findings) out.add(entry.vehicle() + ": " + entry.reason());
        return out;
    }

    @Test
    void aCharacterOwnedVehicleWithoutARowGetsAPayerRowForItsOwner() {
        UUID kira = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        accounts.put(kira, account);
        api.stored("cart", "cart", "char_" + kira);

        assertEquals(List.of(), run());

        assertEquals(List.of("cart cart " + kira + " " + account), written);
        verify(logger).info(contains("now pays its upkeep"));
    }

    @Test
    void whatCannotBeRepairedSafelyIsListedForStaff() {
        UUID kira = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        accounts.put(kira, UUID.randomUUID());
        api.stored("leaders", "cart", "char_" + leader)
                .stored("unknown", "cart", "char_" + unknown)
                .stored("mismatch", "cart", "char_" + UUID.randomUUID())
                .stored("stale", "gun", "player_Former")
                .stored("fine", "gun", "char_" + leader)
                .stored("legacy", "cart", "player_Old")
                .stored("unowned", "cart", "none");
        registry.register(new PlayerVehicleRecord(kira, "mismatch", "cart", OwnershipMode.PERSONAL, null, null,
                UUID.randomUUID()));
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "stale", "gun", OwnershipMode.POOL, null, "red"));
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "fine", "gun", OwnershipMode.POOL, null, "red"));
        registry.register(new PlayerVehicleRecord(null, "destroyed", "cart", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(null, "legacy", "cart", OwnershipMode.PERSONAL, null));

        assertEquals(Set.of(
                "destroyed: reconcile: registry row has no vehicle",
                "leaders: reconcile: owner is a faction leader but the vehicle has no registry row",
                "legacy: reconcile: personal registry row names character null, the vehicle names another owner",
                "mismatch: reconcile: personal registry row names character " + kira
                        + ", the vehicle names another owner",
                "stale: reconcile: faction vehicle should carry char_" + leader,
                "unknown: reconcile: owner character is unknown and the vehicle has no registry row"),
                reasons(run()).stream().filter(line -> !line.startsWith("legacy: reconcile: personal row's"))
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(), written);
    }

    @Test
    void aPersonalRowWithNoKnowableAccountIsListedBecauseItCountsAgainstEveryone() {
        api.stored("legacy", "cart", "player_Old");
        registry.register(new PlayerVehicleRecord(null, "legacy", "cart", OwnershipMode.PERSONAL, null));

        assertEquals(Set.of("legacy: reconcile: personal registry row names character null, the vehicle names another owner",
                "legacy: reconcile: personal row's account is unknown, so it counts against every claimer"),
                reasons(run()));
    }

    @Test
    void aRowThatCannotBeSavedIsListed() {
        UUID kira = UUID.randomUUID();
        accounts.put(kira, UUID.randomUUID());
        api.stored("cart", "cart", "char_" + kira);
        rowsSave = false;

        assertEquals(Set.of("cart: reconcile: could not save a payer row for its owner"), reasons(run()));
    }

    @Test
    void anUnreadableVehicleFrameworkFailsTheReconcile() {
        api.failure = new IllegalStateException("SQLite closed");
        assertThrows(IllegalStateException.class, this::run);
    }
}
