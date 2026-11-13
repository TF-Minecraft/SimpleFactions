package net.tfminecraft.simplefactions.vehicles.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
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

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Mode;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Awaiting;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Pending;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.managers.VehicleManager;

class UpkeepVehicleCommandsTest {
    private static final String SMITHS = UUID.randomUUID().toString();
    @TempDir Path folder;
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final VehicleMaintenanceStore maintenance = new VehicleMaintenanceStore();
    private boolean saves = true;
    private MockedStatic<SimpleFactions> sf;
    private MockedStatic<FactionManager> factions;
    private MockedStatic<Members> members;
    private MockedStatic<VehicleFactionCommands> clicks;
    private MockedStatic<VehicleFramework> vf;
    private FakeOwnerApi api;
    private Faction realm;
    private Guild smiths;
    private Player lea;
    private Player alice;
    private Player carl;
    private Player dana;

    @BeforeEach
    void setUp() throws Exception {
        Path config = folder.resolve("vehicles.yml");
        Files.writeString(config, "categories:\n  ships:\n    sloop:\n      upkeep: 10\n      size: 1\n");
        VehiclesConfigLoader.load(config.toFile());
        api = FakeOwnerApi.install();
        UpkeepRequests.resetForTests();
        UpkeepClickSessions.resetForTests();
        SimpleFactions plugin = mock(SimpleFactions.class);
        when(plugin.getUpkeepVehicleService()).thenReturn(
                new UpkeepVehicleService(registry, () -> saves, (vehicle, character) -> true));
        when(plugin.getVehicleMaintenanceStore()).thenReturn(maintenance);
        sf = mockStatic(SimpleFactions.class);
        sf.when(SimpleFactions::getInstance).thenReturn(plugin);
        realm = mock(Faction.class);
        when(realm.getId()).thenReturn("realm");
        when(realm.getName()).thenReturn("Realm");
        when(realm.getLeader()).thenReturn("Lea");
        when(realm.getMembers()).thenReturn(List.of("Lea", "Alice"));
        when(realm.isMember("Alice")).thenReturn(true);
        smiths = mock(Guild.class);
        when(smiths.getId()).thenReturn("smiths");
        when(smiths.getName()).thenReturn("Smiths");
        when(smiths.getLeader()).thenReturn("Carl");
        when(smiths.getMembers()).thenReturn(List.of("Carl", "Dana"));
        when(smiths.isMember("Dana")).thenReturn(true);
        when(smiths.isLeader("Carl")).thenReturn(true);
        when(smiths.getIncarnation()).thenReturn(SMITHS);
        factions = mockStatic(FactionManager.class);
        factions.when(() -> FactionManager.getByString("realm")).thenReturn(realm);
        factions.when(() -> FactionManager.getByLeader("Lea")).thenReturn(realm);
        factions.when(() -> FactionManager.getByMember("Lea")).thenReturn(realm);
        factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(realm);
        factions.when(FactionManager::getAllGuilds).thenReturn(List.of(smiths));
        members = mockStatic(Members.class, CALLS_REAL_METHODS);
        members.when(() -> Members.online(any())).thenReturn(Optional.empty());
        clicks = mockStatic(VehicleFactionCommands.class);
        vf = mockStatic(VehicleFramework.class);
        VehicleManager manager = mock(VehicleManager.class);
        vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
        when(manager.getOfflineLocation(anyString())).thenReturn(Optional.empty());
        lea = player("Lea");
        alice = player("Alice");
        carl = player("Carl");
        dana = player("Dana");
    }

    @AfterEach
    void tearDown() {
        vf.close();
        clicks.close();
        members.close();
        factions.close();
        sf.close();
        UpkeepRequests.resetForTests();
        UpkeepClickSessions.resetForTests();
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(("account:" + name).getBytes()));
        return player;
    }

    private void online(Player... players) {
        for (Player player : players) members.when(() -> Members.online(player.getName())).thenReturn(Optional.of(player));
    }

    private static CharacterRef ref(Player player) {
        return new CharacterRef(player.getUniqueId(), NameKeyedMembers.characterOf(player.getName()));
    }

    private static UpkeepHolder realmHolder() {
        return new UpkeepHolder(Kind.FACTION, "realm");
    }

    private Pending request(String vehicle, Player owner, Awaiting awaiting, String leaderKey) {
        api.stored(vehicle, "sloop", "char_" + NameKeyedMembers.characterOf(owner.getName()));
        Pending pending = new Pending(realmHolder(), vehicle, "sloop", ref(owner), leaderKey, awaiting,
                UpkeepRequests.expiry());
        UpkeepRequests.add(pending);
        return pending;
    }

    private boolean run(Player player, String command, String... args) {
        return UpkeepVehicleCommands.handle(player, command, args);
    }

    @Test
    void factionsHandOnlyUpkeepCommandsOverAndGuildsEveryVehicleCommand() {
        assertTrue(UpkeepVehicleCommands.handles("faction", new String[] {"vehicle", "upkeep"}));
        assertTrue(UpkeepVehicleCommands.handles("FACTION", new String[] {"vehicle", "transfer", "UPKEEP"}));
        assertFalse(UpkeepVehicleCommands.handles("faction", new String[] {"vehicle", "transfer", "pool"}));
        assertFalse(UpkeepVehicleCommands.handles("faction", new String[] {"vehicle", "transfer"}));
        assertFalse(UpkeepVehicleCommands.handles("faction", new String[] {"vehicle"}));
        assertTrue(UpkeepVehicleCommands.handles("guild", new String[] {"vehicle"}));
        assertFalse(UpkeepVehicleCommands.handles("guild", new String[] {"kick"}));
        assertFalse(UpkeepVehicleCommands.handles("guild", new String[0]));
        assertFalse(UpkeepVehicleCommands.handles("guild", null));
        assertFalse(UpkeepVehicleCommands.handles(null, new String[] {"vehicle"}));
        assertFalse(UpkeepVehicleCommands.handles("war", new String[] {"vehicle", "upkeep"}));
    }

    @Test
    void transferUpkeepArmsTheLeadersClickModeAndOthersAreTurnedAway() {
        assertTrue(run(lea, "faction", "vehicle", "transfer", "upkeep"));
        var session = UpkeepClickSessions.get(lea.getUniqueId());
        assertEquals(Mode.ADD, session.mode());
        assertEquals(realmHolder(), session.holder());
        verify(lea).sendMessage(contains("upkeep-only vehicle of Realm"));
        clicks.verify(() -> VehicleFactionCommands.clearFactionClickModes(lea));
        assertTrue(run(alice, "faction", "vehicle", "transfer", "upkeep"));
        verify(alice).sendMessage(UpkeepVehicleMessages.notLeader(Kind.FACTION));
        assertTrue(run(carl, "guild", "vehicle", "transfer", "upkeep"));
        assertEquals(new UpkeepHolder(Kind.GUILD, SMITHS), UpkeepClickSessions.get(carl.getUniqueId()).holder());
    }

    @Test
    void unknownWordsShowTheUsageAndDisableEndsClickModes() {
        assertTrue(run(carl, "guild", "vehicle", "disable"));
        clicks.verify(() -> VehicleFactionCommands.disarm(carl));
        assertTrue(run(carl, "guild", "vehicle", "cancel"));
        assertTrue(run(carl, "guild", "vehicle"));
        assertTrue(run(carl, "guild", "vehicle", "fly"));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "bogus"));
        verify(carl, org.mockito.Mockito.times(2)).sendMessage(UpkeepVehicleMessages.usage(Kind.GUILD));
        verify(lea).sendMessage(UpkeepVehicleMessages.usage(Kind.FACTION));
    }

    @Test
    void membersRequestLeadersAddAndOutsidersAreRefused() {
        assertTrue(run(alice, "guild", "vehicle", "upkeep", "request"));
        verify(alice).sendMessage(UpkeepVehicleMessages.notMember(Kind.GUILD));
        assertTrue(run(alice, "faction", "vehicle", "upkeep", "request"));
        assertEquals(Mode.REQUEST, UpkeepClickSessions.get(alice.getUniqueId()).mode());
        verify(alice).sendMessage(contains("ask the leader of Realm"));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "request"));
        assertEquals(Mode.ADD, UpkeepClickSessions.get(lea.getUniqueId()).mode());
    }

    @Test
    void giveNeedsTheLeaderAndAMember() {
        assertTrue(run(alice, "faction", "vehicle", "upkeep", "give", "Lea"));
        verify(alice).sendMessage(UpkeepVehicleMessages.notLeader(Kind.FACTION));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "give"));
        verify(lea).sendMessage(UpkeepVehicleMessages.usage(Kind.FACTION));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "give", " "));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "give", "Zed"));
        verify(lea).sendMessage(contains("matches Zed"));
        assertNull(UpkeepClickSessions.get(lea.getUniqueId()));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "give", "Alice"));
        var session = UpkeepClickSessions.get(lea.getUniqueId());
        assertEquals(Mode.GIVE, session.mode());
        assertEquals("Alice", session.recipient());
        verify(lea).sendMessage(contains("give it to Alice"));
    }

    @Test
    void theListShowsVehiclesTheCapUpkeepAndRequests() {
        assertTrue(run(dana, "faction", "vehicle", "upkeep"));
        verify(dana).sendMessage(UpkeepVehicleMessages.notMember(Kind.FACTION));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "list"));
        verify(lea).sendMessage(contains("0/6"));
        verify(lea).sendMessage(UpkeepVehicleMessages.none());
        for (int i = 0; i < 7; i++) registry.register(realmHolder().row(null, "boat-" + i, "sloop"));
        maintenance.markUnpaid("boat-0", 1);
        request("aliceboat", alice, Awaiting.LEADER, null);
        request("leasboat", lea, Awaiting.OWNER, "Lea");
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "info"));
        verify(lea).sendMessage(contains("7/6"));
        verify(lea).sendMessage(UpkeepVehicleMessages.overCap());
        verify(lea).sendMessage(contains("70.00d"));
        verify(lea).sendMessage(contains("unpaid"));
        verify(lea, org.mockito.Mockito.atLeastOnce()).sendMessage(contains("location unknown"));
        verify(lea).sendMessage(UpkeepVehicleMessages.pendingHeader());
        verify(lea).sendMessage(contains("from Alice"));
        verify(lea, org.mockito.Mockito.times(2)).sendMessage(UpkeepVehicleMessages.leaderHelp(Kind.FACTION));
        assertTrue(run(alice, "faction", "vehicle", "upkeep"));
        verify(alice).sendMessage(contains("from Alice"));
        verify(alice, never()).sendMessage(contains("from Lea"));
        verify(alice, never()).sendMessage(UpkeepVehicleMessages.leaderHelp(Kind.FACTION));
        verify(alice, never()).sendMessage(contains("location"));
    }

    @Test
    void theLeaderApprovesAnOwnersRequest() {
        online(lea, alice);
        Pending pending = request("aliceboat", alice, Awaiting.LEADER, null);
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve"));
        verify(lea).sendMessage(UpkeepVehicleMessages.noPending());
        assertTrue(run(dana, "faction", "vehicle", "upkeep", "approve", pending.shortId()));
        assertTrue(run(alice, "faction", "vehicle", "upkeep", "approve", pending.shortId()));
        verify(dana).sendMessage(UpkeepVehicleMessages.notWaitingForYou());
        verify(alice).sendMessage(UpkeepVehicleMessages.notWaitingForYou());
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", pending.shortId()));
        PlayerVehicleRecord row = registry.getByVehicleUuid("aliceboat").orElseThrow();
        assertEquals(OwnershipMode.UPKEEP, row.getMode());
        assertEquals(NameKeyedMembers.characterOf("Alice"), row.getPlayerUuid());
        verify(alice).sendMessage(contains("Your sloop is now kept by Realm"));
        verify(lea).sendMessage(contains("sloop is now an upkeep-only vehicle of Realm"));
        assertNull(UpkeepRequests.find("aliceboat"));
    }

    @Test
    void theOwnerApprovesALeadersMoveWhileTheLeaderStillLeads() {
        Pending pending = request("aliceboat", alice, Awaiting.OWNER, "Lea");
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", pending.shortId()));
        verify(lea).sendMessage(UpkeepVehicleMessages.notWaitingForYou());
        assertTrue(run(alice, "faction", "vehicle", "upkeep", "approve", "aliceboat"));
        assertEquals(OwnershipMode.UPKEEP, registry.getByVehicleUuid("aliceboat").orElseThrow().getMode());

        request("second", alice, Awaiting.OWNER, "Former");
        assertTrue(run(alice, "faction", "vehicle", "upkeep", "approve", "second"));
        verify(alice).sendMessage(contains("expired or was cancelled"));
        assertTrue(registry.getByVehicleUuid("second").isEmpty());
    }

    @Test
    void eitherSideMayDenyAndTheOtherIsTold() {
        online(lea, alice);
        request("one", alice, Awaiting.LEADER, null);
        assertTrue(run(dana, "faction", "vehicle", "upkeep", "deny", "one"));
        verify(dana).sendMessage(UpkeepVehicleMessages.notWaitingForYou());
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "deny", "one"));
        verify(lea).sendMessage(UpkeepVehicleMessages.youDeclined());
        verify(alice).sendMessage(contains("Lea declined"));
        request("two", alice, Awaiting.LEADER, null);
        assertTrue(run(alice, "faction", "vehicle", "upkeep", "deny", "two"));
        verify(lea).sendMessage(contains("Alice declined"));
        assertNull(UpkeepRequests.find("two"));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void anApprovalRechecksOwnerRulesAndSaving() {
        online(lea, alice);
        request("sold", alice, Awaiting.LEADER, null);
        api.stored("sold", "sloop", "char_" + UUID.randomUUID());
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", "sold"));
        verify(alice).sendMessage(UpkeepVehicleMessages.ownerChanged());
        verify(lea).sendMessage(UpkeepVehicleMessages.ownerChanged());

        request("pooled", alice, Awaiting.LEADER, null);
        registry.register(new PlayerVehicleRecord(null, "pooled", "sloop", OwnershipMode.POOL, null, "realm"));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", "pooled"));
        verify(lea).sendMessage(contains("faction vehicle pool"));

        request("gone", alice, Awaiting.LEADER, null);
        api.vehicles.remove("gone");
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", "gone"));
        verify(lea).sendMessage(contains("could not be found"));

        assertNull(UpkeepRequests.find("pooled"), "a vehicle in the pool will not come back by itself");
        assertNull(UpkeepRequests.find("sold"));

        request("unsaved", alice, Awaiting.LEADER, null);
        saves = false;
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", "unsaved"));
        verify(alice).sendMessage(UpkeepVehicleMessages.saveFailed());
        assertTrue(registry.getByVehicleUuid("unsaved").isEmpty());
        assertTrue(UpkeepRequests.find("unsaved") != null, "a failed save keeps the request to try again");
        saves = true;
        for (int i = 0; i < 6; i++) registry.register(realmHolder().row(null, "full-" + i, "sloop"));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", "unsaved"));
        verify(lea).sendMessage(contains("already keeps 6/6"));
        assertTrue(UpkeepRequests.find("unsaved") != null, "a full cap may free up, so the request stays");
    }

    @Test
    void completionsOfferSubcommandsIdsAndMembers() {
        assertEquals(List.of("transfer", "upkeep"), UpkeepVehicleCommands.complete(carl, Kind.GUILD, new String[] {"vehicle", ""})
                .subList(0, 2));
        assertEquals(List.of("upkeep"), UpkeepVehicleCommands.complete(carl, Kind.GUILD, new String[] {"vehicle", "transfer", "u"}));
        assertTrue(UpkeepVehicleCommands.complete(carl, Kind.GUILD, new String[] {"vehicle", "fly", ""}).isEmpty());
        assertEquals(List.of("approve"), UpkeepVehicleCommands.complete(lea, Kind.FACTION, new String[] {"vehicle", "upkeep", "ap"}));
        request("abcdef-1", alice, Awaiting.LEADER, null);
        assertEquals(List.of("abcdef"), UpkeepVehicleCommands.complete(lea, Kind.FACTION,
                new String[] {"vehicle", "upkeep", "approve", ""}));
        assertEquals(List.of("abcdef"), UpkeepVehicleCommands.complete(alice, Kind.FACTION,
                new String[] {"vehicle", "upkeep", "deny", "a"}));
        assertTrue(UpkeepVehicleCommands.complete(dana, Kind.FACTION,
                new String[] {"vehicle", "upkeep", "deny", ""}).isEmpty());
        assertTrue(UpkeepVehicleCommands.complete(dana, Kind.GUILD,
                new String[] {"vehicle", "upkeep", "deny", ""}).isEmpty());
        assertEquals(List.of("Alice"), UpkeepVehicleCommands.complete(lea, Kind.FACTION,
                new String[] {"vehicle", "upkeep", "give", "Al"}));
        assertTrue(UpkeepVehicleCommands.complete(alice, Kind.FACTION,
                new String[] {"vehicle", "upkeep", "give", ""}).isEmpty());
        assertTrue(UpkeepVehicleCommands.complete(lea, Kind.FACTION,
                new String[] {"vehicle", "upkeep", "list", ""}).isEmpty());
        verify(lea, never()).sendMessage(startsWith("§c"));
    }

    @Test
    void anAmbiguousShortIdAsksForMore() {
        request("abc111-1", alice, Awaiting.LEADER, null);
        request("abc222-2", alice, Awaiting.LEADER, null);
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "approve", "abc"));
        verify(lea).sendMessage(contains("More than one pending request starts with abc"));
        assertTrue(run(lea, "faction", "vehicle", "upkeep", "deny", "abc111"));
        verify(lea).sendMessage(UpkeepVehicleMessages.youDeclined());
        assertFalse(UpkeepRequests.ambiguous("abc222-2"));
        assertFalse(UpkeepRequests.ambiguous(null));
        assertFalse(UpkeepRequests.ambiguous(" "));
    }
}
