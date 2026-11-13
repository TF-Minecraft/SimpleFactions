package net.tfminecraft.simplefactions.vehicles.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Mode;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Session;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Awaiting;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Pending;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

class UpkeepStoresTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final UpkeepHolder realm = new UpkeepHolder(Kind.FACTION, "realm");
    private final CharacterRef owner = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        UpkeepRequests.resetForTests();
        UpkeepClickSessions.resetForTests();
        UpkeepRequests.clock = now::get;
        UpkeepClickSessions.clock = now::get;
    }

    @AfterEach
    void tearDown() {
        UpkeepRequests.resetForTests();
        UpkeepClickSessions.resetForTests();
    }

    private Pending pending(String vehicle, UpkeepHolder holder, CharacterRef by) {
        return new Pending(holder, vehicle, "sloop", by, null, Awaiting.LEADER, UpkeepRequests.expiry());
    }

    @Test
    void oneRequestPerVehicleFoundByIdOrUniquePrefixUntilItLapses() {
        assertEquals(1_000 + UpkeepRequests.TIMEOUT_MILLIS, UpkeepRequests.expiry());
        Pending first = pending("abcdef12-0000", realm, owner);
        assertTrue(UpkeepRequests.add(first));
        assertFalse(UpkeepRequests.add(pending("abcdef12-0000", realm, owner)));
        Pending second = pending("abcxyz99-0000", new UpkeepHolder(Kind.GUILD, "smiths"),
                new CharacterRef(UUID.randomUUID(), UUID.randomUUID()));
        assertTrue(UpkeepRequests.add(second));
        assertEquals("abcdef", first.shortId());
        assertEquals("ab", Pending.shortId("ab"));
        assertSame(first, UpkeepRequests.find("abcdef12-0000"));
        assertSame(first, UpkeepRequests.find("ABCDEF"));
        assertNull(UpkeepRequests.find("abc"), "two vehicles start with abc");
        assertNull(UpkeepRequests.find("zzz"));
        assertNull(UpkeepRequests.find(null));
        assertNull(UpkeepRequests.find(" "));
        assertEquals(List.of(first), UpkeepRequests.forHolder(new UpkeepHolder(Kind.FACTION, "REALM")));
        assertEquals(List.of(first), UpkeepRequests.forOwner(owner.characterId()));
        UpkeepRequests.remove("abcxyz99-0000");
        assertTrue(UpkeepRequests.forHolder(second.holder()).isEmpty());
        now.addAndGet(UpkeepRequests.TIMEOUT_MILLIS);
        assertNull(UpkeepRequests.find("abcdef"));
        assertTrue(UpkeepRequests.add(pending("abcdef12-0000", realm, owner)), "a lapsed request frees the vehicle");
    }

    @Test
    void clickModesLastUntilDisarmedOrExpired() {
        UUID account = UUID.randomUUID();
        assertNull(UpkeepClickSessions.get(account));
        assertFalse(UpkeepClickSessions.disarm(account));
        Session session = new Session(Mode.GIVE, realm, "Alice", 2_000);
        UpkeepClickSessions.arm(account, session);
        assertSame(session, UpkeepClickSessions.get(account));
        assertEquals(1_000, UpkeepClickSessions.now());
        assertTrue(UpkeepClickSessions.disarm(account));
        UpkeepClickSessions.arm(account, session);
        now.set(2_000);
        assertNull(UpkeepClickSessions.get(account));
        assertFalse(UpkeepClickSessions.disarm(account));
    }

    @Test
    void targetsComeFromLoadedOrStoredVehicles() {
        assertNull(UpkeepTarget.of(null));
        ActiveVehicle vehicle = mock(ActiveVehicle.class);
        assertNull(UpkeepTarget.of(vehicle));
        when(vehicle.getUUID()).thenReturn("v1");
        when(vehicle.getId()).thenReturn("sloop");
        assertEquals(new UpkeepTarget("v1", "sloop", null), UpkeepTarget.of(vehicle));
        OwnerData data = mock(OwnerData.class);
        when(data.getOwner()).thenReturn("char_x");
        when(vehicle.getOwnerData()).thenReturn(data);
        assertEquals(new UpkeepTarget("v1", "sloop", "char_x"), UpkeepTarget.of(vehicle));
        FakeOwnerApi api = FakeOwnerApi.install();
        api.stored("v2", "karve", "char_y");
        assertEquals(new UpkeepTarget("v2", "karve", "char_y"), UpkeepTarget.read("v2"));
        assertNull(UpkeepTarget.read("gone"));
    }
}
