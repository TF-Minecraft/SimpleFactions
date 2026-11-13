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

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

class UpkeepGuildChangesTest {
    private final String incarnation = UUID.randomUUID().toString();
    private final UUID previous = UUID.randomUUID();
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final SimpleFactions plugin = mock(SimpleFactions.class);
    private final Logger logger = mock(Logger.class);
    private MockedStatic<SimpleFactions> sf;
    private FakeOwnerApi api;
    private Guild smiths;
    private Faction landless;

    @BeforeEach
    void setUp() {
        api = FakeOwnerApi.install();
        smiths = mock(Guild.class);
        when(smiths.getIncarnation()).thenReturn(incarnation);
        when(smiths.getName()).thenReturn("Smiths");
        when(smiths.getLeader()).thenReturn("Carl");
        landless = mock(Faction.class);
        when(landless.getId()).thenReturn("smiths_realm");
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.saveVehicleRegistry()).thenReturn(true);
        sf = mockStatic(SimpleFactions.class);
        sf.when(SimpleFactions::getInstance).thenReturn(plugin);
        sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
    }

    @AfterEach
    void tearDown() {
        sf.close();
    }

    private void kept(String vehicle, String owner) {
        registry.register(PlayerVehicleRecord.upkeep(previous, vehicle, "sloop", null, incarnation));
        api.stored(vehicle, "sloop", owner);
    }

    @Test
    void aGuildThatBecomesARealmHandsItsVehiclesToThatRealm() {
        UpkeepGuildChanges.becameRealm(smiths, landless);
        verify(plugin, never()).saveVehicleRegistry();
        kept("boat", "char_x");
        registry.register(PlayerVehicleRecord.upkeep(previous, "other", "sloop", null, UUID.randomUUID().toString()));
        UpkeepGuildChanges.becameRealm(smiths, landless);
        PlayerVehicleRecord row = registry.getByVehicleUuid("boat").orElseThrow();
        assertEquals(OwnershipMode.UPKEEP, row.getMode());
        assertEquals("smiths_realm", row.getFactionId());
        assertNull(row.getGuildId());
        assertEquals(previous, row.getPlayerUuid());
        assertTrue(registry.getByVehicleUuid("other").orElseThrow().getGuildId() != null);
        UpkeepGuildChanges.becameRealm(null, landless);
        UpkeepGuildChanges.becameRealm(smiths, null);
    }

    @Test
    void anAbsorbedRealmsVehiclesFollowItsMainGuild() {
        registry.register(PlayerVehicleRecord.upkeep(previous, "boat", "sloop", "smiths_realm", null));
        registry.register(PlayerVehicleRecord.upkeep(previous, "theirs", "sloop", "other_realm", null));
        UpkeepGuildChanges.realmBecameGuild(landless, smiths);
        PlayerVehicleRecord row = registry.getByVehicleUuid("boat").orElseThrow();
        assertNull(row.getFactionId());
        assertEquals(incarnation, row.getGuildId());
        assertEquals(previous, row.getPlayerUuid());
        assertEquals("other_realm", registry.getByVehicleUuid("theirs").orElseThrow().getFactionId());
        UpkeepGuildChanges.realmBecameGuild(null, smiths);
        UpkeepGuildChanges.realmBecameGuild(landless, null);
    }

    @Test
    void anUnsavedConversionKeepsTheOldRows() {
        kept("boat", "char_x");
        PlayerVehicleRecord before = registry.getByVehicleUuid("boat").orElseThrow();
        when(plugin.saveVehicleRegistry()).thenReturn(false);
        UpkeepGuildChanges.becameRealm(smiths, landless);
        assertSame(before, registry.getByVehicleUuid("boat").orElseThrow());
        verify(logger).warning(contains("still count for it through its main guild"));
        registry.register(PlayerVehicleRecord.upkeep(previous, "realm", "sloop", "smiths_realm", null));
        UpkeepGuildChanges.realmBecameGuild(landless, smiths);
        assertEquals("smiths_realm", registry.getByVehicleUuid("realm").orElseThrow().getFactionId());
        verify(logger).warning(contains("lose their holder with the realm"));
    }

    @Test
    void aRemovedGuildsVehiclesStayForStaffWithNobodyStampedNeverPastAnyonesLimits() {
        assertFalse(UpkeepGuildChanges.holdsVehicles(smiths));
        kept("boat", "char_x");
        kept("done", "char_none");
        assertTrue(UpkeepGuildChanges.holdsVehicles(smiths));
        assertFalse(UpkeepGuildChanges.holdsVehicles(null));
        UpkeepGuildChanges.disbanded(smiths);
        assertEquals(OwnershipMode.UPKEEP, registry.getByVehicleUuid("boat").orElseThrow().getMode());
        assertEquals("char_none", api.owner("boat"));
        assertEquals(List.of("boat char_none RESTAMP"), api.writes);
        verify(logger).warning(contains("nobody pays for them until staff reassign them"));
        api.forced = Result.SAVE_FAILED;
        api.stored("boat", "sloop", "char_x");
        UpkeepGuildChanges.disbanded(smiths);
        verify(logger).warning(contains("the upkeep reconcile lists it"));
    }

    @Test
    void nothingHappensWithoutThePluginForMainGuildsOrForGuildsWithoutVehicles() {
        UpkeepGuildChanges.disbanded(smiths);
        UpkeepGuildChanges.disbanded(null);
        Guild main = mock(Guild.class);
        when(main.isBase()).thenReturn(true);
        kept("boat", "char_x");
        UpkeepGuildChanges.disbanded(main);
        sf.when(SimpleFactions::getVehicleRegistry).thenReturn(null);
        UpkeepGuildChanges.disbanded(smiths);
        UpkeepGuildChanges.becameRealm(smiths, landless);
        assertFalse(UpkeepGuildChanges.holdsVehicles(smiths));
        sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
        sf.when(SimpleFactions::getInstance).thenReturn(null);
        UpkeepGuildChanges.disbanded(smiths);
        UpkeepGuildChanges.becameRealm(smiths, landless);
        assertFalse(UpkeepGuildChanges.holdsVehicles(smiths));
        assertEquals(incarnation, registry.getByVehicleUuid("boat").orElseThrow().getGuildId());
        assertTrue(api.writes.isEmpty());
        verify(plugin, never()).saveVehicleRegistry();
    }
}
