package net.tfminecraft.simplefactions.vehicles.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepHolder.Kind;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Awaiting;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Pending;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.AddResult;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.GiveOutcome;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.GiveStatus;
import net.tfminecraft.tlibs.character.CharacterRef;

class UpkeepVehicleMessagesTest {
    @TempDir Path folder;
    private MockedStatic<FactionManager> factions;
    private UpkeepHolder realm;
    private UpkeepHolder smiths;

    @BeforeEach
    void setUp() throws Exception {
        Path config = folder.resolve("vehicles.yml");
        Files.writeString(config, "categories:\n  ships:\n    sloop:\n      upkeep: 10\n      size: 1\n");
        VehiclesConfigLoader.load(config.toFile());
        Faction faction = mock(Faction.class);
        when(faction.getName()).thenReturn("Realm");
        when(faction.getMembers()).thenReturn(List.of("A", "B"));
        Guild guild = mock(Guild.class);
        when(guild.getName()).thenReturn("Smiths");
        factions = mockStatic(FactionManager.class);
        factions.when(() -> FactionManager.getByString("realm")).thenReturn(faction);
        factions.when(() -> FactionManager.getGuildByString("smiths")).thenReturn(guild);
        realm = new UpkeepHolder(Kind.FACTION, "realm");
        smiths = new UpkeepHolder(Kind.GUILD, "smiths");
    }

    @AfterEach
    void tearDown() {
        factions.close();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void commandHintsNameTheRightRoot() {
        assertTrue(UpkeepVehicleMessages.usage(Kind.GUILD).contains("/guild vehicle transfer upkeep"));
        assertTrue(UpkeepVehicleMessages.usage(Kind.FACTION).contains("/faction vehicle upkeep give <member>"));
        assertTrue(UpkeepVehicleMessages.notLeader(Kind.FACTION).contains("faction leader"));
        assertTrue(UpkeepVehicleMessages.notLeader(Kind.GUILD).contains("lead a guild"));
        assertTrue(UpkeepVehicleMessages.notMember(Kind.FACTION).contains("in a faction"));
        assertTrue(UpkeepVehicleMessages.notMember(Kind.GUILD).contains("in a guild"));
        assertTrue(UpkeepVehicleMessages.addArmed(smiths).contains("/guild vehicle disable"));
        assertTrue(UpkeepVehicleMessages.requestArmed(realm).contains("leader of Realm"));
        assertTrue(UpkeepVehicleMessages.giveArmed(realm, "Alice").contains("give it to Alice"));
        assertTrue(UpkeepVehicleMessages.notAMemberNamed(realm, "Zed").contains("Zed"));
        assertTrue(UpkeepVehicleMessages.notYourVehicle().contains("own vehicle"));
        assertTrue(UpkeepVehicleMessages.leaderHelp(Kind.GUILD).contains("/guild vehicle upkeep give"));
    }

    @ParameterizedTest
    @EnumSource(AddResult.class)
    void everyAddRefusalHasAMessage(AddResult result) {
        String message = UpkeepVehicleMessages.forAdd(result, realm, 4);
        if (result == AddResult.OK) assertNull(message);
        else assertTrue(message.startsWith("§c"), message);
    }

    @Test
    void theCapRefusalShowsTheCount() {
        assertTrue(UpkeepVehicleMessages.forAdd(AddResult.AT_CAP, realm, 6).contains("6/6"));
        assertTrue(UpkeepVehicleMessages.forAdd(AddResult.NOT_MEMBER, realm, 0).contains("members of Realm"));
    }

    @Test
    void movesAndRequestsNameThePayerAndUpkeep() {
        assertTrue(UpkeepVehicleMessages.addedForLeader(realm, "sloop").contains("The faction's main guild bank pays 10.00d"));
        assertTrue(UpkeepVehicleMessages.addedForLeader(smiths, "sloop").contains("The guild bank pays"));
        assertTrue(UpkeepVehicleMessages.addedForOwner(smiths, "sloop").contains("kept by Smiths"));
        assertTrue(UpkeepVehicleMessages.alreadyPending().contains("pending"));
        assertTrue(UpkeepVehicleMessages.requestSent(realm, "sloop", true).endsWith("10 minutes."));
        assertTrue(UpkeepVehicleMessages.requestSent(smiths, "sloop", false).contains("/guild vehicle upkeep"));
        assertTrue(UpkeepVehicleMessages.ownerAsked("Bob").contains("Asked Bob"));
        Pending pending = new Pending(smiths, "abcdef123", "sloop", new CharacterRef(UUID.randomUUID(), UUID.randomUUID()),
                null, Awaiting.LEADER, 0);
        String leader = plain(UpkeepVehicleMessages.leaderPrompt(pending, "Bob"));
        assertTrue(leader.contains("Bob asks Smiths to keep their sloop #abcdef"), leader);
        assertTrue(leader.contains("[Approve]") && leader.contains("[Deny]"));
        String owner = plain(UpkeepVehicleMessages.ownerPrompt(pending, "Carl"));
        assertTrue(owner.contains("Carl wants Smiths"), owner);
        assertTrue(UpkeepVehicleMessages.noPending().contains("No pending"));
        assertTrue(UpkeepVehicleMessages.notWaitingForYou().contains("not waiting"));
        assertTrue(UpkeepVehicleMessages.declined("Bob", "sloop").contains("Bob declined"));
        assertTrue(UpkeepVehicleMessages.youDeclined().contains("declined"));
        assertTrue(UpkeepVehicleMessages.ownerChanged().contains("changed owner"));
        assertTrue(UpkeepVehicleMessages.saveFailed().contains("Could not save"));
    }

    @ParameterizedTest
    @EnumSource(GiveStatus.class)
    void everyGiveOutcomeHasAMessage(GiveStatus status) {
        String message = UpkeepVehicleMessages.forGive(
                new GiveOutcome(status, CanBuildResult.TOTAL_LIMIT, "sloop"), realm, "Alice");
        assertNotNull(message);
        assertEquals(status == GiveStatus.OK, message.startsWith("§a"), message);
    }

    @Test
    void theListShowsCountsUpkeepAndPendingRequests() {
        assertTrue(UpkeepVehicleMessages.received(realm, "Lea", "sloop").contains("Lea gave you the sloop from Realm"));
        assertTrue(UpkeepVehicleMessages.header(realm, 2).contains("2/6"));
        assertTrue(UpkeepVehicleMessages.overCap().contains("Over the cap"));
        assertTrue(UpkeepVehicleMessages.daily(smiths, 12.5).contains("12.50d"));
        assertTrue(UpkeepVehicleMessages.none().contains("None"));
        assertTrue(UpkeepVehicleMessages.line("sloop", "abcdef", "world 1, 2, 3", true).contains("unpaid"));
        assertFalse(UpkeepVehicleMessages.line("sloop", "abcdef", "world 1, 2, 3", false).contains("unpaid"));
        assertTrue(UpkeepVehicleMessages.pendingHeader().contains("Pending"));
        CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        assertTrue(UpkeepVehicleMessages.pendingLine(new Pending(realm, "abcdef1", "sloop", ref, null, Awaiting.LEADER, 0),
                "Bob").contains("waiting for the leader"));
        assertTrue(UpkeepVehicleMessages.pendingLine(new Pending(realm, "abcdef1", "sloop", ref, "Lea", Awaiting.OWNER, 0),
                "Bob").contains("waiting for the owner"));
    }
}
