package net.tfminecraft.simplefactions.vehicles.maintenance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

class UpkeepPauseUpkeepOnlyTest {
    @Test
    void aHeldGuildsUpkeepOnlyVehiclesTakeNoUnpaidDamage() {
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        String incarnation = java.util.UUID.randomUUID().toString();
        registry.register(PlayerVehicleRecord.upkeep(null, "kept", "ship", null, incarnation));
        registry.register(PlayerVehicleRecord.upkeep(null, "orphan", "ship", null, null));
        Guild smiths = mock(Guild.class);
        when(smiths.getIncarnation()).thenReturn(incarnation);
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(FactionManager::getAllGuilds).thenReturn(java.util.List.of(smiths));
            assertFalse(UpkeepPause.paused(registry, "kept"));
            when(smiths.isLeaderless()).thenReturn(true);
            assertTrue(UpkeepPause.paused(registry, "kept"));
            assertFalse(UpkeepPause.paused(registry, "orphan"));
            assertFalse(UpkeepPause.paused(registry, "missing"));
        }
    }
}
