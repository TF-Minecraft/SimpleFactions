package net.tfminecraft.simplefactions.vehicles.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.kyori.adventure.text.Component;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Mode;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Session;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Awaiting;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

class UpkeepVehicleListenerTest {
    @TempDir Path folder;
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final UpkeepHolder holder = new UpkeepHolder(Kind.FACTION, "realm");
    private final World world = mock(World.class);
    private boolean saves = true;
    private MockedStatic<FactionManager> factions;
    private MockedStatic<Members> members;
    private FakeOwnerApi api;
    private UpkeepVehicleListener listener;
    private Player lea;
    private Player alice;

    @BeforeEach
    void setUp() throws Exception {
        Path config = folder.resolve("vehicles.yml");
        Files.writeString(config, "personal-slot-limit: 3\ncategories:\n  ships:\n    sloop:\n      upkeep: 10\n      size: 1\n");
        VehiclesConfigLoader.load(config.toFile());
        api = FakeOwnerApi.install();
        UpkeepRequests.resetForTests();
        UpkeepClickSessions.resetForTests();
        Faction realm = mock(Faction.class);
        when(realm.getId()).thenReturn("realm");
        when(realm.getName()).thenReturn("Realm");
        when(realm.getLeader()).thenReturn("Lea");
        when(realm.getMembers()).thenReturn(List.of("Lea", "Alice"));
        when(realm.isMember("Alice")).thenReturn(true);
        factions = mockStatic(FactionManager.class);
        factions.when(() -> FactionManager.getByString("realm")).thenReturn(realm);
        members = mockStatic(Members.class, CALLS_REAL_METHODS);
        members.when(() -> Members.online(any())).thenReturn(Optional.empty());
        listener = new UpkeepVehicleListener(new UpkeepVehicleService(registry, () -> saves, (vehicle, character) -> true));
        lea = player("Lea", 0);
        alice = player("Alice", 5);
    }

    @AfterEach
    void tearDown() {
        members.close();
        factions.close();
        UpkeepRequests.resetForTests();
        UpkeepClickSessions.resetForTests();
    }

    private Player player(String name, int x) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(("account:" + name).getBytes()));
        when(player.getLocation()).thenReturn(new Location(world, x, 64, 0));
        return player;
    }

    private static String owner(String name) {
        return "char_" + NameKeyedMembers.characterOf(name);
    }

    private VehiclePreInteractEvent click(Player player, String vehicle, String owner) {
        api.stored(vehicle, "sloop", owner);
        ActiveVehicle active = mock(ActiveVehicle.class);
        when(active.getUUID()).thenReturn(vehicle);
        when(active.getId()).thenReturn("sloop");
        OwnerData data = mock(OwnerData.class);
        when(data.getOwner()).thenReturn(owner);
        when(active.getOwnerData()).thenReturn(data);
        when(active.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        VehiclePreInteractEvent event = mock(VehiclePreInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getVehicle()).thenReturn(active);
        listener.onVehiclePreInteract(event);
        return event;
    }

    private void arm(Player player, Mode mode, String recipient) {
        UpkeepClickSessions.arm(player.getUniqueId(), new Session(mode, holder, recipient, Long.MAX_VALUE));
    }

    @Test
    void clicksWithoutAnArmedModeOrVehicleAreLeftAlone() {
        VehiclePreInteractEvent noPlayer = mock(VehiclePreInteractEvent.class);
        listener.onVehiclePreInteract(noPlayer);
        VehiclePreInteractEvent event = click(lea, "boat", owner("Lea"));
        verify(event, never()).setCancelled(true);
        arm(lea, Mode.ADD, null);
        VehiclePreInteractEvent unknown = mock(VehiclePreInteractEvent.class);
        when(unknown.getPlayer()).thenReturn(lea);
        listener.onVehiclePreInteract(unknown);
        verify(unknown, never()).setCancelled(true);
        assertNotNull(UpkeepClickSessions.get(lea.getUniqueId()));
    }

    @Test
    void theLeadersOwnVehicleMovesInAtOnce() {
        arm(lea, Mode.ADD, null);
        VehiclePreInteractEvent event = click(lea, "boat", owner("Lea"));
        verify(event).setCancelled(true);
        assertEquals(OwnershipMode.UPKEEP, registry.getByVehicleUuid("boat").orElseThrow().getMode());
        verify(lea).sendMessage(contains("is now an upkeep-only vehicle of Realm"));
        assertNull(UpkeepClickSessions.get(lea.getUniqueId()));
    }

    @Test
    void aRefusedOrUnsavedMoveKeepsTheClickModeArmed() {
        arm(lea, Mode.ADD, null);
        click(lea, "unowned", "none");
        verify(lea).sendMessage(contains("must be owned by a player"));
        saves = false;
        click(lea, "boat", owner("Lea"));
        verify(lea).sendMessage(UpkeepVehicleMessages.saveFailed());
        assertNotNull(UpkeepClickSessions.get(lea.getUniqueId()));
    }

    @Test
    void someoneWhoNoLongerLeadsLosesTheClickMode() {
        arm(alice, Mode.ADD, null);
        click(alice, "boat", owner("Alice"));
        verify(alice).sendMessage(UpkeepVehicleMessages.notLeader(Kind.FACTION));
        assertNull(UpkeepClickSessions.get(alice.getUniqueId()));
        arm(alice, Mode.GIVE, "Alice");
        click(alice, "boat", owner("Alice"));
        assertNull(UpkeepClickSessions.get(alice.getUniqueId()));
    }

    @Test
    void aMembersVehicleWaitsForItsOwnerWhoMustBeOnlineAndNear() {
        arm(lea, Mode.ADD, null);
        click(lea, "boat", owner("Alice"));
        verify(lea).sendMessage(contains("owner must be online"));
        members.when(() -> Members.online("Alice")).thenReturn(Optional.of(alice));
        click(lea, "boat", owner("Alice"));
        verify(lea, org.mockito.Mockito.times(2)).sendMessage(contains("owner must be online"));
        CharacterRef ref = new CharacterRef(alice.getUniqueId(), NameKeyedMembers.characterOf("Alice"));
        members.when(() -> Members.ref("Alice")).thenReturn(Optional.of(ref));
        when(alice.getLocation()).thenReturn(new Location(world, 500, 64, 0));
        click(lea, "boat", owner("Alice"));
        verify(lea).sendMessage(contains("within 20 blocks"));
        when(alice.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
        click(lea, "boat", owner("Alice"));
        verify(lea, org.mockito.Mockito.times(2)).sendMessage(contains("within 20 blocks"));
        when(alice.getLocation()).thenReturn(new Location(world, 5, 64, 0));
        click(lea, "boat", owner("Alice"));
        verify(alice).sendMessage(any(Component.class));
        verify(lea).sendMessage(contains("Asked Alice"));
        var pending = UpkeepRequests.find("boat");
        assertEquals(Awaiting.OWNER, pending.awaiting());
        assertEquals("Lea", pending.leaderKey());
        assertTrue(registry.getByVehicleUuid("boat").isEmpty(), "nothing moves before the owner agrees");
        arm(lea, Mode.ADD, null);
        click(lea, "boat", owner("Alice"));
        verify(lea).sendMessage(UpkeepVehicleMessages.alreadyPending());
    }

    @Test
    void aVehicleWithoutLocationCannotBeCheckedForDistance() {
        members.when(() -> Members.online("Alice")).thenReturn(Optional.of(alice));
        CharacterRef aliceRef = new CharacterRef(alice.getUniqueId(), NameKeyedMembers.characterOf("Alice"));
        members.when(() -> Members.ref("Alice")).thenReturn(Optional.of(aliceRef));
        arm(lea, Mode.ADD, null);
        api.stored("boat", "sloop", owner("Alice"));
        ActiveVehicle active = mock(ActiveVehicle.class);
        when(active.getUUID()).thenReturn("boat");
        when(active.getId()).thenReturn("sloop");
        OwnerData data = mock(OwnerData.class);
        when(data.getOwner()).thenReturn(owner("Alice"));
        when(active.getOwnerData()).thenReturn(data);
        VehiclePreInteractEvent event = mock(VehiclePreInteractEvent.class);
        when(event.getPlayer()).thenReturn(lea);
        when(event.getVehicle()).thenReturn(active);
        listener.onVehiclePreInteract(event);
        verify(lea).sendMessage(contains("within 20 blocks"));
    }

    @Test
    void anOwnersRequestWaitsForTheLeader() {
        arm(alice, Mode.REQUEST, null);
        click(alice, "boat", owner("Lea"));
        verify(alice).sendMessage(UpkeepVehicleMessages.notYourVehicle());
        registry.register(new PlayerVehicleRecord(null, "pooled", "sloop", OwnershipMode.POOL, null, "realm"));
        click(alice, "pooled", owner("Alice"));
        verify(alice).sendMessage(contains("faction vehicle pool"));
        click(alice, "boat", owner("Alice"));
        verify(alice).sendMessage(contains("the leader is offline"));
        assertNull(UpkeepClickSessions.get(alice.getUniqueId()));
        assertEquals(Awaiting.LEADER, UpkeepRequests.find("boat").awaiting());
        members.when(() -> Members.online("Lea")).thenReturn(Optional.of(lea));
        arm(alice, Mode.REQUEST, null);
        click(alice, "boat", owner("Alice"));
        verify(alice).sendMessage(UpkeepVehicleMessages.alreadyPending());
        click(alice, "other", owner("Alice"));
        verify(lea).sendMessage(any(Component.class));
        verify(alice).sendMessage(contains("lapses in 10 minutes."));
    }

    @Test
    void aPlayerWithoutACharacterCannotRequest() {
        Player stranger = mock(Player.class);
        when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
        arm(stranger, Mode.REQUEST, null);
        click(stranger, "boat", owner("Alice"));
        verify(stranger).sendMessage(UpkeepVehicleMessages.notYourVehicle());
    }

    @Test
    void theLeaderHandsAVehicleToAMember() {
        SimpleFactions plugin = mock(SimpleFactions.class);
        registry.register(holder.row(null, "boat", "sloop"));
        members.when(() -> Members.online("Alice")).thenReturn(Optional.of(alice));
        CharacterRef aliceRef = new CharacterRef(alice.getUniqueId(), NameKeyedMembers.characterOf("Alice"));
        members.when(() -> Members.ref("Alice")).thenReturn(Optional.of(aliceRef));
        arm(lea, Mode.GIVE, "Alice");
        click(lea, "elsewhere", owner("Lea"));
        verify(lea).sendMessage(contains("not an upkeep-only vehicle of Realm"));
        assertNotNull(UpkeepClickSessions.get(lea.getUniqueId()));
        try (var sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            click(lea, "boat", "char_none");
        }
        verify(lea).sendMessage(contains("Gave the sloop to Alice"));
        verify(alice).sendMessage(contains("Lea gave you the sloop from Realm"));
        verify(plugin).recordVehicleOwner("boat", "Alice");
        assertEquals(OwnershipMode.PERSONAL, registry.getByVehicleUuid("boat").orElseThrow().getMode());
        assertNull(UpkeepClickSessions.get(lea.getUniqueId()));
    }
}
