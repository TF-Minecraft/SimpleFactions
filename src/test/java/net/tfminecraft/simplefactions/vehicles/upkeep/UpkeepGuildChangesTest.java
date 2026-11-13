package net.tfminecraft.simplefactions.vehicles.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterRef;

class UpkeepGuildChangesTest {
    private final String incarnation = UUID.randomUUID().toString();
    private final UUID previous = UUID.randomUUID();
    private final CharacterRef carl = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final SimpleFactions plugin = mock(SimpleFactions.class);
    private final Logger logger = mock(Logger.class);
    private MockedStatic<SimpleFactions> sf;
    private MockedStatic<Members> members;
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
        members = mockStatic(Members.class, CALLS_REAL_METHODS);
        members.when(() -> Members.online(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        members.close();
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
    void anUnsavedConversionKeepsTheGuildRowsWhichStillCountForTheRealm() {
        kept("boat", "char_x");
        PlayerVehicleRecord before = registry.getByVehicleUuid("boat").orElseThrow();
        when(plugin.saveVehicleRegistry()).thenReturn(false);
        UpkeepGuildChanges.becameRealm(smiths, landless);
        assertSame(before, registry.getByVehicleUuid("boat").orElseThrow());
        verify(logger).warning(contains("still count for it through its main guild"));
    }

    @Test
    void aDisbandedGuildsVehiclesGoToItsLeaderAsPersonalVehicles() {
        Player online = mock(Player.class);
        members.when(() -> Members.ref("Carl")).thenReturn(Optional.of(carl));
        members.when(() -> Members.online("Carl")).thenReturn(Optional.of(online));
        kept("stamped", "char_" + carl.characterId());
        kept("stale", "char_x");
        UpkeepGuildChanges.disbanded(smiths);
        for (String vehicle : List.of("stamped", "stale")) {
            PlayerVehicleRecord row = registry.getByVehicleUuid(vehicle).orElseThrow();
            assertEquals(OwnershipMode.PERSONAL, row.getMode());
            assertEquals(carl.characterId(), row.getPlayerUuid());
            assertEquals(carl.ownerId(), row.getAccountUuid());
            assertEquals("char_" + carl.characterId(), api.owner(vehicle));
        }
        assertEquals(List.of("stale char_" + carl.characterId() + " RELEASE"), api.writes);
        verify(online).sendMessage(contains("2 upkeep-only vehicles are now your personal vehicles"));
    }

    @Test
    void anUnsavedHandoverLeavesTheVehiclesWithTheGuildForStaff() {
        members.when(() -> Members.ref("Carl")).thenReturn(Optional.of(carl));
        kept("boat", "char_x");
        when(plugin.saveVehicleRegistry()).thenReturn(false);
        UpkeepGuildChanges.disbanded(smiths);
        assertEquals(OwnershipMode.UPKEEP, registry.getByVehicleUuid("boat").orElseThrow().getMode());
        assertTrue(api.writes.isEmpty());
        verify(logger).severe(contains("stay with the closed guild for staff"));
    }

    @Test
    void aGuildClosedWithoutALeaderKeepsItsRowsWithNobodyStamped() {
        when(smiths.getLeader()).thenReturn("");
        kept("boat", "char_x");
        kept("done", "char_none");
        api.forced = null;
        UpkeepGuildChanges.disbanded(smiths);
        assertEquals(OwnershipMode.UPKEEP, registry.getByVehicleUuid("boat").orElseThrow().getMode());
        assertEquals("char_none", api.owner("boat"));
        assertEquals(List.of("boat char_none RESTAMP"), api.writes);
        verify(logger).warning(contains("closed without a leader"));
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
        UpkeepGuildChanges.disbanded(main);
        sf.when(SimpleFactions::getVehicleRegistry).thenReturn(null);
        UpkeepGuildChanges.disbanded(smiths);
        UpkeepGuildChanges.becameRealm(smiths, landless);
        sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
        sf.when(SimpleFactions::getInstance).thenReturn(null);
        kept("boat", "char_x");
        UpkeepGuildChanges.disbanded(smiths);
        UpkeepGuildChanges.becameRealm(smiths, landless);
        assertEquals(incarnation, registry.getByVehicleUuid("boat").orElseThrow().getGuildId());
        verify(plugin, never()).saveVehicleRegistry();
    }
}
