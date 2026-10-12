package net.tfminecraft.simplefactions.government;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.government.election.Election;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.MovementJoinCopy;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.admin.MovementAdminService;
import net.tfminecraft.simplefactions.government.movement.admin.MovementTabCompletion;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.guild.TestCharacters;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;

/** Government, elections and movements store character keys and show character names. */
class CharacterKeyedPoliticsTest {
    private PersistenceFilesFixture disk;
    private FactionDomainFixture fixture;
    private TestCharacters characters;
    private String ruler;
    private String ash;
    private String zed;
    private Faction realm;

    @BeforeEach
    void setUp() throws Exception {
        disk = new PersistenceFilesFixture();
        java.nio.file.Files.createDirectories(disk.root.resolve("Data"));
        fixture = new FactionDomainFixture();
        characters = TestCharacters.install();
        ruler = characters.character(UUID.randomUUID(), "Queen Ilsa");
        ash = characters.character(UUID.randomUUID(), "Ash Vale");
        zed = characters.character(UUID.randomUUID(), "Zed Moor");
        realm = fixture.saved("realm", ruler);
        realm.addMember(ash);
        realm.addMember(zed);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            fixture.close();
        } finally {
            disk.close();
        }
    }

    private Player playing(String account, String key) {
        Player player = fixture.player(account);
        characters.play(player, key);
        return player;
    }

    @Test
    void theRulerActsThroughTheirCurrentCharacterOnly() {
        UUID account = UUID.randomUUID();
        String alt = characters.character(account, "Other Self");
        Player player = playing("RulerAccount", ruler);
        Government government = realm.getGovernment();
        assertTrue(realm.isLeader(player));
        assertTrue(government.canPropose(player));
        assertTrue(government.isCouncilMember(player));
        assertFalse(government.canProposePolitical(player, Action.CHANGE_LEADER));

        characters.play(player, alt);
        assertFalse(realm.isLeader(player));
        assertFalse(government.canPropose(player));
        assertFalse(government.isCouncilMember(player));
        assertFalse(government.canProposeOrStartMovement(player));

        characters.play(player, null);
        assertFalse(government.canPropose(player));
        assertFalse(government.canProposeOrStartMovement(player));
        assertFalse(government.canProposePolitical(player, Action.CHANGE_LEADER));
    }

    @Test
    void councilQuorumCountsCharactersBeingPlayed() {
        Council council = realm.getGovernment().getCouncil();
        council.addMemberForce(ash);
        council.addMemberForce(zed);
        Player account = playing("Account", ruler);
        assertFalse(council.hasEnoughValidVoters());
        playing("AshAccount", ash);
        assertFalse(council.hasEnoughValidVoters());
        playing("ZedAccount", zed);
        assertTrue(council.hasEnoughValidVoters());
        characters.play(account, null);
        assertFalse(council.hasEnoughValidVoters());
        assertTrue(council.canRemainMember(ash));
        assertFalse(council.canBeMember(ruler, true, true));
    }

    @Test
    void electionTiesAreOrderedByCharacterName() {
        Election election = realm.getGovernment().getElection();
        election.restoreFromData(
                Map.of("LEADER", new ArrayList<>(List.of(zed, ash))),
                Map.of("LEADER", Map.of(ruler, zed)),
                Map.of("LEADER", Map.of(zed, 2, ash, 2)),
                List.of(ruler));
        assertEquals(List.of(ash, zed), election.getWinners(Candidate.LEADER));
        assertEquals(1, election.getVotes(Candidate.LEADER, zed));
        election.removeCandidate(Candidate.LEADER, zed);
        assertEquals(0, election.getVotes(Candidate.LEADER, zed));

        List<String> lore = new ArrayList<>();
        election.applyReasons(lore, Candidate.LEADER, playing("Outsider", null));
        assertFalse(lore.isEmpty());
        lore.clear();
        election.applyReasons(lore, Candidate.LEADER, playing("ZedAccount", zed));
        assertTrue(lore.isEmpty());
    }

    @Test
    void movementsStoreCitizensByKeyAndShowTheirNames() {
        Proposal proposal = new Proposal(ash, realm.getGovernment());
        proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
        realm.getGovernment().startMovement(ash, proposal);
        Movement movement = realm.getGovernment().getMovementByLeader(ash);
        assertTrue(movement.isLeader(ash));
        assertTrue(movement.canBeLeader(ash));
        assertEquals(movement.getCauses().getFirst(), movement.getCauseByLeader(ash));

        Player zedPlayer = playing("ZedAccount", zed);
        assertTrue(movement.quickJoinCheck(zedPlayer));
        assertEquals(zed, movement.getJoiningAs(zedPlayer));
        assertNull(movement.getJoiningAs(playing("Stranger", null)));
        movement.join(movement.getJoiningAs(zedPlayer), null);
        assertTrue(movement.isMember(zed));
        assertFalse(movement.quickJoinCheck(zedPlayer));
        assertTrue(String.join("\n", movement.getSupporters().getFormattedList()).contains("Zed Moor"));
        assertTrue(MovementJoinCopy.alreadyCauseMember(true, zed).contains("Zed Moor"));

        proposal.setTarget(zed);
        String book = pages(proposal.getAsBook());
        assertTrue(book.contains("Proposed by: Ash Vale"));
        assertTrue(book.contains("Target: Zed Moor"));
    }

    @Test
    void staffPickCitizensAndWantedLeadersByCharacterName() {
        Proposal proposal = new Proposal(ash, realm.getGovernment());
        proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
        realm.getGovernment().startMovement(ash, proposal);
        Movement movement = realm.getGovernment().getMovementByLeader(ash);

        assertTrue(MovementAdminService.listLines().getFirst().contains("leader: §fAsh Vale"));
        assertTrue(MovementAdminService.hostCitizenNames(movement).contains("Zed Moor"));
        assertTrue(MovementAdminService.wantedLeaderNames(movement).contains("Zed Moor"));

        MovementAdminService.Result missing = MovementAdminService.target(movement.getId(), "0", "Nobody Here");
        assertFalse(missing.ok());
        MovementAdminService.Result set = MovementAdminService.target(movement.getId(), "0", "zed moor");
        assertTrue(set.ok(), set.message());
        assertTrue(set.message().endsWith("to Zed Moor."));
        assertEquals(zed, proposal.getTarget());

        MovementAdminService.Result joined = MovementAdminService.joinSupporter(movement.getId(), "citizen", zed);
        assertTrue(joined.ok(), joined.message());
        realm.forceRemoveMember(zed);
        MovementAdminService.Result left = MovementAdminService.leaveSupporter(movement.getId(), "citizen", "Zed Moor");
        assertTrue(left.ok(), left.message());
        assertFalse(movement.getSupporters().getCitizens().contains(zed));
    }

    @Test
    void staffCommandsAcceptAndCompleteCharacterNamesWithSpaces() {
        Proposal proposal = new Proposal(ash, realm.getGovernment());
        proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
        realm.getGovernment().startMovement(ash, proposal);
        Movement movement = realm.getGovernment().getMovementByLeader(ash);
        Player staff = playing("Staff", null);
        when(staff.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(staff.isOp()).thenReturn(true);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("movement");
        MovementTabCompletion tabs = new MovementTabCompletion();
        String id = movement.getId();

        assertEquals(List.of("Moor"), tabs.onTabComplete(staff, command, "movement",
                new String[] {"admin", "target", id, "0", "Zed", ""}));
        assertEquals(List.of("Zed Moor"), tabs.onTabComplete(staff, command, "movement",
                new String[] {"admin", "join", id, "supporter", "citizen", "Z"}));
        assertEquals(List.of("Moor"), tabs.onTabComplete(staff, command, "movement",
                new String[] {"admin", "join", id, "cause", "0", "citizen", "Zed", "M"}));
        assertEquals(List.of(), tabs.onTabComplete(staff, command, "movement",
                new String[] {"admin", "join", id, "supporter", "guild", "x", "y"}));
        assertEquals(List.of(), tabs.onTabComplete(staff, command, "movement",
                new String[] {"admin", "join", id, "supporter", "other", "x"}));

        new net.tfminecraft.simplefactions.government.movement.admin.MovementCommandManager()
                .onCommand(staff, command, "movement", new String[] {"admin", "target", id, "0", "Zed", "Moor"});
        assertEquals(zed, proposal.getTarget());
    }

    private static String pages(ItemStack item) {
        assertEquals(Material.WRITTEN_BOOK, item.getType());
        return org.bukkit.ChatColor.stripColor(String.join("\n", ((BookMeta) item.getItemMeta()).getPages()));
    }
}
