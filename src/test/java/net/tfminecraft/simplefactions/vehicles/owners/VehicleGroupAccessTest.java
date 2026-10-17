package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.api.AccessGroup;

class VehicleGroupAccessTest {
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final AtomicBoolean ready = new AtomicBoolean(true);
    private final VehicleGroupAccess access = new VehicleGroupAccess(registry, ready::get);
    private final UUID vehicle = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID rider = UUID.randomUUID();
    private final String stamp = OwnerTags.character(owner);
    private final Faction bog = faction("The_Bog", "The Bog");
    private final Faction marsh = faction("Marsh", "Marsh");
    private MockedStatic<FactionManager> factions;
    private MockedStatic<RelationManager> relations;
    private MockedStatic<FactionVehiclePoolService> pool;

    @BeforeEach
    void setUp() {
        factions = mockStatic(FactionManager.class);
        relations = mockStatic(RelationManager.class);
        pool = mockStatic(FactionVehiclePoolService.class);
        factions.when(() -> FactionManager.getByMember(owner)).thenReturn(bog);
    }

    @AfterEach
    void tearDown() {
        pool.close();
        relations.close();
        factions.close();
    }

    private static Faction faction(String id, String name) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        when(faction.getName()).thenReturn(name);
        return faction;
    }

    private static Guild guild(String name, boolean base) {
        Guild guild = mock(Guild.class);
        when(guild.getName()).thenReturn(name);
        when(guild.isBase()).thenReturn(base);
        return guild;
    }

    private void factionVehicle(Faction payer) {
        PlayerVehicleRecord row = new PlayerVehicleRecord(owner, vehicle.toString(), "gunboat", OwnershipMode.POOL, null,
                "The_Bog");
        registry.register(row);
        pool.when(() -> FactionVehiclePoolService.payingFaction(row)).thenReturn(payer);
    }

    @Test
    void readinessFollowsSimpleFactionsOnEveryCall() {
        assertTrue(access.ready());
        ready.set(false);
        assertFalse(access.ready());
    }

    @Test
    void factionAccessCountsFromTheOwnersCurrentFaction() {
        assertFalse(access.admits(AccessGroup.FACTION, vehicle, stamp, rider), "a rider without a faction");
        factions.when(() -> FactionManager.getByMember(rider)).thenReturn(marsh);
        assertFalse(access.admits(AccessGroup.FACTION, vehicle, stamp, rider));
        Faction lowerCase = faction("the_bog", "The Bog");
        factions.when(() -> FactionManager.getByMember(rider)).thenReturn(lowerCase);
        assertTrue(access.admits(AccessGroup.FACTION, vehicle, stamp, rider), "faction ids match without case");
        assertFalse(access.admits(AccessGroup.FACTION, vehicle, "char_none", rider), "no owner character, no faction");
        assertFalse(access.admits(AccessGroup.FACTION, vehicle, stamp, null));
        assertFalse(access.admits(null, vehicle, stamp, rider));
        assertEquals(Optional.of("The Bog"), access.describe(AccessGroup.FACTION, vehicle, stamp));
        assertEquals(Set.of(), access.alwaysOpen(vehicle, stamp));
        assertEquals(Set.of(), access.alwaysOpen(null, stamp));
    }

    @Test
    void aFactionVehicleAlwaysAdmitsItsPayingFactionWhateverTheStamp() {
        factionVehicle(bog);
        factions.when(() -> FactionManager.getByMember(rider)).thenReturn(bog);
        assertEquals(Set.of(AccessGroup.FACTION), access.alwaysOpen(vehicle, "char_none"));
        assertTrue(access.admits(AccessGroup.FACTION, vehicle, "char_none", rider));
        assertEquals(Optional.of("The Bog"), access.describe(AccessGroup.ALLY, vehicle, "char_none"));
        factionVehicle(null);
        assertFalse(access.admits(AccessGroup.FACTION, vehicle, stamp, rider), "a row whose faction is gone admits nobody");
    }

    @Test
    void personalRowsCountFromTheOwner() {
        registry.register(new PlayerVehicleRecord(owner, vehicle.toString(), "cart", OwnershipMode.PERSONAL, null));
        factions.when(() -> FactionManager.getByMember(rider)).thenReturn(bog);
        assertEquals(Set.of(), access.alwaysOpen(vehicle, stamp));
        assertTrue(access.admits(AccessGroup.FACTION, vehicle, stamp, rider));
    }

    @Test
    void guildAccessMeansTheSamePlayerGuildNeverTheMainGuild() {
        Guild smiths = guild("Smiths", false);
        factions.when(() -> FactionManager.getGuildByMember(owner)).thenReturn(smiths);
        assertFalse(access.admits(AccessGroup.GUILD, vehicle, stamp, rider));
        Guild masons = guild("Masons", false);
        factions.when(() -> FactionManager.getGuildByMember(rider)).thenReturn(masons);
        assertFalse(access.admits(AccessGroup.GUILD, vehicle, stamp, rider));
        factions.when(() -> FactionManager.getGuildByMember(rider)).thenReturn(null);
        factions.when(() -> FactionManager.getGuildByLeader(rider)).thenReturn(smiths);
        assertTrue(access.admits(AccessGroup.GUILD, vehicle, stamp, rider), "a guild's leader counts as in it");
        assertEquals(Optional.of("Smiths"), access.describe(AccessGroup.GUILD, vehicle, stamp));
        Guild main = guild("The Bog", true);
        factions.when(() -> FactionManager.getGuildByMember(owner)).thenReturn(main);
        factions.when(() -> FactionManager.getGuildByLeader(rider)).thenReturn(main);
        assertFalse(access.admits(AccessGroup.GUILD, vehicle, stamp, rider), "the main guild is the whole faction");
        assertEquals(Optional.empty(), access.describe(AccessGroup.GUILD, vehicle, stamp));
        assertFalse(access.admits(AccessGroup.GUILD, vehicle, "none", rider));
        factions.when(() -> FactionManager.getGuildByMember(rider)).thenReturn(main);
        factions.when(() -> FactionManager.getGuildByLeader(rider)).thenReturn(smiths);
        factions.when(() -> FactionManager.getGuildByMember(owner)).thenReturn(smiths);
        assertTrue(access.admits(AccessGroup.GUILD, vehicle, stamp, rider), "a leader still listed in the main guild");
    }

    @Test
    void factionVehiclesHaveNoGuild() {
        Guild smiths = guild("Smiths", false);
        factions.when(() -> FactionManager.getGuildByMember(any(UUID.class))).thenReturn(smiths);
        factionVehicle(bog);
        assertFalse(access.admits(AccessGroup.GUILD, vehicle, stamp, rider));
        assertEquals(Optional.empty(), access.describe(AccessGroup.GUILD, vehicle, stamp));
    }

    @Test
    void allyAccessNeedsTheOwnersFactionToCallTheRidersFactionAnAlly() {
        assertFalse(access.admits(AccessGroup.ALLY, vehicle, stamp, rider), "a rider without a faction");
        factions.when(() -> FactionManager.getByMember(rider)).thenReturn(marsh);
        assertFalse(access.admits(AccessGroup.ALLY, vehicle, stamp, rider));
        relations.when(() -> RelationManager.isAlly(bog, marsh)).thenReturn(true);
        assertTrue(access.admits(AccessGroup.ALLY, vehicle, stamp, rider));
        relations.when(() -> RelationManager.isAlly(bog, bog)).thenReturn(true);
        factions.when(() -> FactionManager.getByMember(rider)).thenReturn(bog);
        assertFalse(access.admits(AccessGroup.ALLY, vehicle, stamp, rider), "the owner's own faction is not an ally");
        assertFalse(access.admits(AccessGroup.ALLY, vehicle, "none", rider));
    }

    @Test
    void vassalAccessIsForDirectVassalsOfTheOwnersFaction() {
        assertFalse(access.admits(AccessGroup.VASSAL, vehicle, stamp, rider), "a rider without a faction");
        factions.when(() -> FactionManager.getByMember(rider)).thenReturn(marsh);
        assertFalse(access.admits(AccessGroup.VASSAL, vehicle, stamp, rider));
        relations.when(() -> RelationManager.isOverlord(marsh, bog)).thenReturn(true);
        assertTrue(access.admits(AccessGroup.VASSAL, vehicle, stamp, rider));
        relations.when(() -> RelationManager.isOverlord(bog, marsh)).thenReturn(true);
        relations.when(() -> RelationManager.isOverlord(marsh, bog)).thenReturn(false);
        assertFalse(access.admits(AccessGroup.VASSAL, vehicle, stamp, rider), "the overlord is not the vassal");
        assertFalse(access.admits(AccessGroup.VASSAL, vehicle, "none", rider));
        assertEquals(Optional.empty(), access.describe(AccessGroup.VASSAL, vehicle, "none"));
    }
}
