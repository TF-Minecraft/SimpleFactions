package net.tfminecraft.simplefactions.vehicles.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.AddResult;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.GiveStatus;
import net.tfminecraft.tlibs.character.CharacterRef;

class UpkeepVehicleServiceTest {
    @TempDir Path folder;
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final UUID leaderCharacter = UUID.randomUUID();
    private final String leader = leaderCharacter.toString();
    private final UUID alice = NameKeyedMembers.characterOf("Alice");
    private final UUID outsider = NameKeyedMembers.characterOf("Outsider");
    private final List<Boolean> saves = new ArrayList<>();
    private final List<String> assigned = new ArrayList<>();
    private boolean assignWorks = true;
    private MockedStatic<FactionManager> factions;
    private FakeOwnerApi api;
    private Faction realm;
    private UpkeepHolder holder;
    private UpkeepVehicleService service;

    @BeforeEach
    void setUp() throws Exception {
        Path config = folder.resolve("vehicles.yml");
        Files.writeString(config, """
                personal-slot-limit: 1
                categories:
                  ships:
                    sloop:
                      upkeep: 10
                      size: 1
                    karve:
                      upkeep: 2.5
                      size: 1
                """);
        VehiclesConfigLoader.load(config.toFile());
        api = FakeOwnerApi.install();
        realm = mock(Faction.class);
        when(realm.getId()).thenReturn("realm");
        when(realm.getName()).thenReturn("Realm");
        when(realm.getLeader()).thenReturn(leader);
        when(realm.getMembers()).thenReturn(List.of("Alice"));
        when(realm.isMember("Alice")).thenReturn(true);
        factions = mockStatic(FactionManager.class);
        factions.when(() -> FactionManager.getByString("realm")).thenReturn(realm);
        holder = UpkeepHolder.of(realm);
        service = new UpkeepVehicleService(registry, () -> saves.isEmpty() || saves.remove(0),
                (vehicle, character) -> {
                    assigned.add(vehicle + " " + character);
                    return assignWorks;
                });
    }

    @AfterEach
    void tearDown() {
        factions.close();
    }

    private UpkeepTarget target(String vehicle, String type, UUID owner) {
        api.stored(vehicle, type, owner == null ? "none" : "char_" + owner);
        return new UpkeepTarget(vehicle, type, owner == null ? "none" : "char_" + owner);
    }

    @Test
    void onlyAMembersOwnVehicleOfAKnownTypeJoinsBelowTheCap() {
        assertSame(registry, service.registry());
        assertEquals(AddResult.NO_HOLDER, service.canAdd(null, target("v", "sloop", alice)));
        assertEquals(AddResult.NO_HOLDER, service.canAdd(new UpkeepHolder(Kind.GUILD, "gone"), target("v", "sloop", alice)));
        assertEquals(AddResult.NOT_FOUND, service.canAdd(holder, null));
        assertEquals(AddResult.NOT_FOUND, service.canAdd(holder, new UpkeepTarget(null, "sloop", null)));
        registry.register(PlayerVehicleRecord.upkeep(alice, "kept", "sloop", "other", null));
        registry.register(new PlayerVehicleRecord(alice, "pooled", "sloop", OwnershipMode.POOL, null, "realm"));
        registry.register(new PlayerVehicleRecord(alice, "berthed", "sloop", OwnershipMode.INSTALLATION, "port", "realm"));
        assertEquals(AddResult.ALREADY_UPKEEP, service.canAdd(holder, target("kept", "sloop", alice)));
        assertEquals(AddResult.ALREADY_IN_POOL, service.canAdd(holder, target("pooled", "sloop", alice)));
        assertEquals(AddResult.ALREADY_BERTHED, service.canAdd(holder, target("berthed", "sloop", alice)));
        assertEquals(AddResult.NOT_OWNED, service.canAdd(holder, target("v", "sloop", null)));
        assertEquals(AddResult.NOT_MEMBER, service.canAdd(holder, target("v", "sloop", outsider)));
        assertEquals(AddResult.UNKNOWN_TYPE, service.canAdd(holder, target("v", "dragon", alice)));
        assertEquals(AddResult.OK, service.canAdd(holder, target("v", "sloop", alice)));
        registry.register(new PlayerVehicleRecord(alice, "personal", "sloop", OwnershipMode.PERSONAL, null));
        assertEquals(AddResult.OK, service.canAdd(holder, target("personal", "sloop", alice)));
        for (int i = 0; i < 6; i++) registry.register(holder.row(alice, "own" + i, "karve"));
        assertEquals(AddResult.AT_CAP, service.canAdd(holder, target("v", "sloop", alice)), "two members, six vehicles");
    }

    @Test
    void registeringStampsTheLeaderAndDropsTheOldOwnersEntries() {
        UpkeepTarget boat = target("boat", "sloop", alice);
        assertTrue(service.register(holder, boat, alice));
        PlayerVehicleRecord row = registry.getByVehicleUuid("boat").orElseThrow();
        assertEquals(OwnershipMode.UPKEEP, row.getMode());
        assertEquals("realm", row.getFactionId());
        assertEquals(alice, row.getPlayerUuid());
        assertEquals("char_" + leader, api.owner("boat"));
        assertEquals(List.of("boat char_" + leader + " TRANSFER"), api.writes);
    }

    @Test
    void theLeadersOwnVehicleKeepsItsOwnerAndWhitelist() {
        UpkeepTarget boat = target("boat", "sloop", leaderCharacter);
        assertTrue(service.register(holder, boat, leaderCharacter));
        assertTrue(api.writes.isEmpty());
        assertEquals(OwnershipMode.UPKEEP, registry.getByVehicleUuid("boat").orElseThrow().getMode());
    }

    @Test
    void aFailedSaveOrStampPutsThePreviousRowBack() {
        PlayerVehicleRecord personal = new PlayerVehicleRecord(alice, "boat", "sloop", OwnershipMode.PERSONAL, null);
        registry.register(personal);
        UpkeepTarget boat = target("boat", "sloop", alice);
        saves.add(false);
        assertFalse(service.register(holder, boat, alice));
        assertSame(personal, registry.getByVehicleUuid("boat").orElseThrow());
        assertTrue(api.writes.isEmpty());

        api.forced = Result.SAVE_FAILED;
        assertFalse(service.register(holder, boat, alice));
        assertSame(personal, registry.getByVehicleUuid("boat").orElseThrow());

        SimpleFactions plugin = mock(SimpleFactions.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        try (var sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            saves.add(true);
            saves.add(false);
            assertFalse(service.register(holder, boat, alice));
        }
        verify(logger).severe(contains("upkeep-only stamp failed"));
        registry.unregister("boat");
        api.forced = null;
        UpkeepTarget fresh = target("fresh", "sloop", alice);
        api.forced = Result.NOT_FOUND;
        assertFalse(service.register(holder, fresh, alice));
        assertEquals(Optional.empty(), registry.getByVehicleUuid("fresh"));
    }

    @Test
    void theLeaderGivesAVehicleToAMemberWithRoom() {
        CharacterRef ref = new CharacterRef(UUID.randomUUID(), alice);
        registry.register(holder.row(alice, "boat", "sloop"));
        assertEquals(GiveStatus.NOT_LEADER, service.give(null, leader, ref, "boat").status());
        assertEquals(GiveStatus.NOT_LEADER, service.give(holder, "Alice", ref, "boat").status());
        assertEquals(GiveStatus.NOT_UPKEEP_VEHICLE, service.give(holder, leader, ref, null).status());
        assertEquals(GiveStatus.NOT_UPKEEP_VEHICLE, service.give(holder, leader, ref, "missing").status());
        var noRecipient = service.give(holder, leader, null, "boat");
        assertEquals(GiveStatus.OWNERSHIP_UNAVAILABLE, noRecipient.status());
        assertEquals("sloop", noRecipient.vehicleTypeId());
        assertEquals(GiveStatus.NOT_MEMBER,
                service.give(holder, leader, new CharacterRef(UUID.randomUUID(), outsider), "boat").status());
        registry.register(new PlayerVehicleRecord(alice, "mine", "karve", OwnershipMode.PERSONAL, null, null,
                ref.ownerId()));
        var full = service.give(holder, leader, ref, "boat");
        assertEquals(GiveStatus.NO_PERSONAL_ROOM, full.status());
        assertEquals(CanBuildResult.TOTAL_LIMIT, full.slotFailure());
        registry.unregister("mine");

        var given = service.give(holder, leader, ref, "boat");
        assertEquals(GiveStatus.OK, given.status());
        PlayerVehicleRecord row = registry.getByVehicleUuid("boat").orElseThrow();
        assertEquals(OwnershipMode.PERSONAL, row.getMode());
        assertEquals(alice, row.getPlayerUuid());
        assertEquals(ref.ownerId(), row.getAccountUuid());
        assertEquals(List.of("boat " + alice), assigned);
        assertNull(given.slotFailure());
    }

    @Test
    void theLeaderMayGiveAVehicleToThemselves() {
        CharacterRef self = new CharacterRef(UUID.randomUUID(), leaderCharacter);
        registry.register(holder.row(alice, "boat", "sloop"));
        assertEquals(GiveStatus.OK, service.give(holder, leader, self, "boat").status());
    }

    @Test
    void anUnknownTypeOrAFailedWriteLeavesTheVehicleWithTheHolder() {
        CharacterRef ref = new CharacterRef(UUID.randomUUID(), alice);
        PlayerVehicleRecord row = holder.row(alice, "boat", "dragon");
        registry.register(row);
        assertEquals(GiveStatus.UNKNOWN_TYPE, service.give(holder, leader, ref, "boat").status());

        row = holder.row(alice, "boat", "sloop");
        registry.register(row);
        saves.add(false);
        assertEquals(GiveStatus.SAVE_FAILED, service.give(holder, leader, ref, "boat").status());
        assertSame(row, registry.getByVehicleUuid("boat").orElseThrow());
        assertTrue(assigned.isEmpty());

        assignWorks = false;
        assertEquals(GiveStatus.OWNERSHIP_UNAVAILABLE, service.give(holder, leader, ref, "boat").status());
        assertSame(row, registry.getByVehicleUuid("boat").orElseThrow());

        SimpleFactions plugin = mock(SimpleFactions.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        try (var sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            saves.add(true);
            saves.add(false);
            assertEquals(GiveStatus.SAVE_FAILED, service.give(holder, leader, ref, "boat").status());
        }
        verify(logger).severe(contains("restored to Realm"));
        assertSame(row, registry.getByVehicleUuid("boat").orElseThrow());
    }

    @Test
    void dailyUpkeepAddsTheHoldersVehiclesOnly() {
        registry.register(holder.row(alice, "a", "sloop"));
        registry.register(holder.row(alice, "b", "karve"));
        registry.register(PlayerVehicleRecord.upkeep(alice, "c", "sloop", null, "smiths"));
        assertEquals(12.5, UpkeepVehicleService.dailyUpkeep(registry, holder));
        assertEquals(0.0, UpkeepVehicleService.dailyUpkeep(null, holder));
        assertEquals(0.0, UpkeepVehicleService.dailyUpkeep(registry, null));
        assertEquals(0.0, UpkeepVehicleService.dailyUpkeepOfGuild("smiths"), "no plugin running");
        SimpleFactions previous = SimpleFactions.plugin;
        SimpleFactions.plugin = mock(SimpleFactions.class);
        try (var sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
            assertEquals(10.0, UpkeepVehicleService.dailyUpkeepOfGuild("smiths"));
            assertEquals(0.0, UpkeepVehicleService.dailyUpkeepOfGuild(null));
        } finally {
            SimpleFactions.plugin = previous;
        }
    }

    @Test
    void theDefaultServiceSavesThroughThePlugin() {
        UpkeepVehicleService live = new UpkeepVehicleService(registry);
        assertTrue(live.register(holder, target("boat", "sloop", alice), alice), "no plugin: nothing to save");
        SimpleFactions plugin = mock(SimpleFactions.class);
        try (var sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            when(plugin.saveVehicleRegistry()).thenReturn(false);
            assertFalse(live.register(holder, target("other", "sloop", alice), alice));
        }
        verify(plugin, never()).getLogger();
    }
}
