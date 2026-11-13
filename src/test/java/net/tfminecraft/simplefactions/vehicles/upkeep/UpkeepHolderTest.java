package net.tfminecraft.simplefactions.vehicles.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;

class UpkeepHolderTest {
    private final String leader = UUID.randomUUID().toString();
    private MockedStatic<FactionManager> factions;
    private Faction realm;
    private Guild main;
    private Guild smiths;

    @BeforeEach
    void setUp() {
        realm = mock(Faction.class);
        when(realm.getId()).thenReturn("Realm");
        when(realm.getName()).thenReturn("The Realm");
        when(realm.getLeader()).thenReturn(leader);
        when(realm.getMembers()).thenReturn(List.of("Alice", "Bob"));
        when(realm.isMember("Alice")).thenReturn(true);
        main = mock(Guild.class);
        when(main.isBase()).thenReturn(true);
        when(main.getFaction()).thenReturn(realm);
        GuildHandler handler = mock(GuildHandler.class);
        when(handler.getGuild("Realm")).thenReturn(main);
        when(realm.getGuildHandler()).thenReturn(handler);
        smiths = mock(Guild.class);
        when(smiths.getId()).thenReturn("smiths");
        when(smiths.getName()).thenReturn("Smiths");
        when(smiths.getFaction()).thenReturn(realm);
        when(smiths.getLeader()).thenReturn("Carl");
        when(smiths.getMembers()).thenReturn(List.of("Carl", "Dana"));
        when(smiths.isMember("Dana")).thenReturn(true);
        factions = mockStatic(FactionManager.class);
        factions.when(() -> FactionManager.getByString("Realm")).thenReturn(realm);
        factions.when(() -> FactionManager.getGuildByString("smiths")).thenReturn(smiths);
        factions.when(() -> FactionManager.getGuildByString("Realm")).thenReturn(main);
    }

    @AfterEach
    void tearDown() {
        factions.close();
    }

    @Test
    void holdersComeFromFactionsGuildsAndUpkeepRowsOnly() {
        assertEquals(new UpkeepHolder(Kind.FACTION, "Realm"), UpkeepHolder.of(realm));
        assertEquals(new UpkeepHolder(Kind.FACTION, "Realm"), UpkeepHolder.of(main));
        assertEquals(new UpkeepHolder(Kind.GUILD, "smiths"), UpkeepHolder.of(smiths));
        assertNull(UpkeepHolder.of((Faction) null));
        assertNull(UpkeepHolder.of((Guild) null));
        assertNull(UpkeepHolder.of((PlayerVehicleRecord) null));
        assertNull(UpkeepHolder.of(new PlayerVehicleRecord(null, "v", "t", OwnershipMode.POOL, null, "Realm")));
        assertEquals(new UpkeepHolder(Kind.FACTION, "Realm"),
                UpkeepHolder.of(PlayerVehicleRecord.upkeep(null, "v", "t", "Realm", null)));
        assertEquals(new UpkeepHolder(Kind.GUILD, "smiths"),
                UpkeepHolder.of(PlayerVehicleRecord.upkeep(null, "v", "t", "Realm", "smiths")));
        assertNull(UpkeepHolder.of(PlayerVehicleRecord.upkeep(null, "v", "t", null, null)));
        assertEquals("faction", Kind.FACTION.command());
        assertEquals("guild", Kind.GUILD.command());
    }

    @Test
    void leadersAndMembersFindTheirOwnHolder() {
        factions.when(() -> FactionManager.getByLeader(leader)).thenReturn(realm);
        factions.when(() -> FactionManager.getGuildByLeader("Carl")).thenReturn(smiths);
        factions.when(() -> FactionManager.getGuildByLeader(leader)).thenReturn(main);
        factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(realm);
        factions.when(() -> FactionManager.getGuildByMember("Dana")).thenReturn(smiths);
        factions.when(() -> FactionManager.getGuildByMember("Alice")).thenReturn(main);
        assertEquals(UpkeepHolder.of(realm), UpkeepHolder.ledBy(leader, Kind.FACTION));
        assertEquals(UpkeepHolder.of(smiths), UpkeepHolder.ledBy("Carl", Kind.GUILD));
        assertNull(UpkeepHolder.ledBy(leader, Kind.GUILD), "a realm's main guild is not a player guild");
        assertNull(UpkeepHolder.ledBy("Nobody", Kind.GUILD));
        assertNull(UpkeepHolder.ledBy(null, Kind.FACTION));
        assertEquals(UpkeepHolder.of(realm), UpkeepHolder.memberOf("Alice", Kind.FACTION));
        assertEquals(UpkeepHolder.of(smiths), UpkeepHolder.memberOf("Dana", Kind.GUILD));
        assertEquals(UpkeepHolder.of(smiths), UpkeepHolder.memberOf("Carl", Kind.GUILD), "a leader counts");
        assertNull(UpkeepHolder.memberOf("Alice", Kind.GUILD));
        assertNull(UpkeepHolder.memberOf(null, Kind.GUILD));
    }

    @Test
    void aFactionHolderReadsTheRealm() {
        UpkeepHolder holder = UpkeepHolder.of(realm);
        assertSame(realm, holder.faction());
        assertNull(holder.guild());
        assertTrue(holder.exists());
        assertEquals(leader, holder.leader());
        assertTrue(holder.isLeader(leader));
        assertFalse(holder.isLeader(null));
        assertFalse(holder.isLeader(""));
        assertEquals("char_" + leader, holder.leaderStamp());
        assertEquals(Set.of("Alice", "Bob", leader), holder.members());
        assertEquals(9, holder.cap());
        assertTrue(holder.isMember("Alice"));
        assertTrue(holder.isMember(leader));
        assertFalse(holder.isMember("Dana"));
        assertFalse(holder.isMember(null));
        assertSame(main, holder.bank());
        assertFalse(holder.held());
        when(main.isHeld()).thenReturn(true);
        assertTrue(holder.held());
        assertEquals("The Realm", holder.name());
        PlayerVehicleRecord row = holder.row(null, "v", "ship");
        assertEquals("Realm", row.getFactionId());
        assertNull(row.getGuildId());
        assertTrue(holder.holds(row));
        assertTrue(holder.same(new UpkeepHolder(Kind.FACTION, "realm")));
        assertFalse(holder.same(new UpkeepHolder(Kind.GUILD, "Realm")));
        assertFalse(holder.same(null));
    }

    @Test
    void aGuildHolderReadsTheGuildAndItsHost() {
        UpkeepHolder holder = UpkeepHolder.of(smiths);
        assertSame(smiths, holder.guild());
        assertSame(realm, holder.faction());
        assertTrue(holder.exists());
        assertEquals("Carl", holder.leader());
        assertEquals("char_none", holder.leaderStamp());
        assertEquals(Set.of("Carl", "Dana"), holder.members());
        assertEquals(6, holder.cap());
        assertTrue(holder.isMember("Dana"));
        assertFalse(holder.isMember("Alice"));
        assertSame(smiths, holder.bank());
        assertFalse(holder.held());
        when(smiths.isHeld()).thenReturn(true);
        assertTrue(holder.held());
        assertEquals("Smiths", holder.name());
        PlayerVehicleRecord row = holder.row(null, "v", "ship");
        assertNull(row.getFactionId());
        assertEquals("smiths", row.getGuildId());
        assertTrue(holder.holds(row));
        assertFalse(UpkeepHolder.of(realm).holds(row));
        when(smiths.getLeader()).thenReturn("");
        assertEquals(Set.of("Carl", "Dana"), holder.members(), "a leaderless guild still counts its members");
    }

    @Test
    void aHolderThatIsGoneHasNothing() {
        for (UpkeepHolder gone : List.of(new UpkeepHolder(Kind.FACTION, "gone"), new UpkeepHolder(Kind.GUILD, "gone"),
                new UpkeepHolder(Kind.GUILD, "Realm"))) {
            assertNull(gone.faction());
            assertNull(gone.guild());
            assertFalse(gone.exists());
            assertNull(gone.leader());
            assertEquals("char_none", gone.leaderStamp());
            assertTrue(gone.members().isEmpty());
            assertEquals(0, gone.cap());
            assertFalse(gone.isMember("Alice"));
            assertNull(gone.bank());
            assertFalse(gone.held());
            assertEquals(gone.id(), gone.name());
        }
    }

    @Test
    void vehiclesAreTheHoldersUpkeepRows() {
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        registry.register(PlayerVehicleRecord.upkeep(null, "a", "ship", "Realm", null));
        registry.register(PlayerVehicleRecord.upkeep(null, "b", "ship", "Realm", "smiths"));
        registry.register(new PlayerVehicleRecord(null, "c", "ship", OwnershipMode.POOL, null, "Realm"));
        assertEquals(List.of("a"), UpkeepHolder.of(realm).vehicles(registry).stream()
                .map(PlayerVehicleRecord::getVehicleUuid).toList());
        assertEquals(List.of("b"), UpkeepHolder.of(smiths).vehicles(registry).stream()
                .map(PlayerVehicleRecord::getVehicleUuid).toList());
    }
}
