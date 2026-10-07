package net.tfminecraft.simplefactions.government;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.government.election.Election;
import net.tfminecraft.simplefactions.government.proposal.FeeChange;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.government.session.Session;
import net.tfminecraft.simplefactions.government.session.SessionReport;
import net.tfminecraft.simplefactions.government.session.Vote;
import net.tfminecraft.simplefactions.government.session.VoteResult;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GovernmentProcessesCoverageTest {
  private FactionDomainFixture fixture;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    when(Bukkit.getPlayer(anyString())).thenAnswer(call -> fixture.online.get(call.getArgument(0)));
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> new ArrayList<>(fixture.online.values()));
    when(fixture.ui.plugin.getSessionManager()).thenReturn(new SessionManager());
  }

  @AfterEach
  void cleanup() {
    if (fixture != null) fixture.close();
  }

  @Test
  void validatingCompletedBallotsPreservesVotesFromStillEligibleMembers() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    Election election = faction.getGovernment().getElection();
    election.addCandidate(Candidate.LEADER, "Bob");
    election.start();
    assertTrue(election.canVote("Alice"));
    election.addVote(Candidate.LEADER, "Alice", "Bob");
    assertFalse(election.canVote("Alice"));
    assertEquals(1, election.getVotes(Candidate.LEADER, "Bob"));

    election.validateVotes();

    assertEquals(1, election.getVotes(Candidate.LEADER, "Bob"));
    assertEquals("Bob", election.getVote(Candidate.LEADER, "Alice"));
    election.end();
    assertEquals("Bob", faction.getLeader());
    assertEquals(Map.of("Bob", 1), election.getPreviousVotes().get(Candidate.LEADER));
    assertFalse(election.isActive());
    assertTrue(election.getCandidates(Candidate.LEADER).isEmpty());
    assertNull(election.getVote(Candidate.LEADER, "Alice"));
  }

  @Test
  void voteValidationStillRemovesDepartedVotersMissingCandidatesAndUnregisteredVoters() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    Election election = faction.getGovernment().getElection();
    election.addCandidate(Candidate.LEADER, "Bob");
    election.start();
    election.addVote(Candidate.LEADER, "Alice", "Missing");
    election.addVote(Candidate.LEADER, "Cara", "Bob");
    election.addVote(Candidate.LEADER, "Outsider", "Bob");
    faction.forceRemoveMember("Cara");
    election.validateVotes();
    assertTrue(election.serializeVotes().get("LEADER").isEmpty());
    assertFalse(election.canVote("Cara"));
    assertFalse(election.canVote("Outsider"));
    election.addVote(Candidate.LEADER, "Alice", "Bob");
    election.removeCandidate(Candidate.LEADER, "Bob");
    assertNull(election.getVote(Candidate.LEADER, "Alice"));
    assertFalse(election.hasAnyCandidates());
  }

  @Test
  void electionEligibilityIncludesEntitledSubjectsAndEachEnabledBallotMustBeCompleted() {
    fixture.lawGroup(
        "democracy",
        Map.of(
            "effects.faction.rules",
            List.of(
                "LEADER_ELECTIONS true",
                "HAS_COUNCIL true",
                "ELECTED_COUNCIL true",
                "VASSAL_VOTING_RIGHTS true"),
            "effects.faction.council-size",
            2));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    Faction vassal = fixture.saved("vassal", "Drew");
    fixture.subject(faction, vassal);
    Election election = faction.getGovernment().getElection();
    election.addCandidate(Candidate.LEADER, "Bob");
    election.addCandidate(Candidate.COUNCIL, "Cara");
    assertTrue(election.hasAnyCandidates());
    election.start();
    assertTrue(
        election.getStoredEligibleVoters().containsAll(List.of("Alice", "Bob", "Cara", "Drew")));
    assertTrue(election.canVote("Drew"));
    election.addVote(Candidate.LEADER, "Drew", "Bob");
    assertTrue(election.hasVoted(Candidate.LEADER, "Drew"));
    assertFalse(election.hasVoted("Drew"));
    assertTrue(election.canVote("Drew"));
    election.addVote(Candidate.COUNCIL, "Drew", "Cara");
    assertTrue(election.hasVoted("Drew"));
    assertFalse(election.canVote("Drew"));
    election.validateVotes();
    assertEquals(1, election.getVotes(Candidate.LEADER, "Bob"));
    assertEquals(1, election.getVotes(Candidate.COUNCIL, "Cara"));
    election.end();
    assertEquals("Bob", faction.getLeader());
    assertEquals("Cara", faction.getGovernment().getCouncilMembers().getFirst());
  }

  @Test
  void electionRanksLiveAndArchivedResultsWithStableAlphabeticalTiesAndIndependentSnapshots() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    for (String name : List.of("Bob", "Cara", "Drew")) faction.addMember(name);
    Election election = faction.getGovernment().getElection();
    Map<String, List<String>> candidates = new LinkedHashMap<>();
    candidates.put("LEADER", new ArrayList<>(List.of("Drew", "Cara", "Bob")));
    candidates.put("REMOVED_TYPE", List.of("Invalid"));
    Map<String, Map<String, String>> votes =
        Map.of("LEADER", Map.of("Alice", "Cara"), "BAD", Map.of());
    Map<String, Map<String, Integer>> previous =
        Map.of("LEADER", Map.of("Bob", 3, "Cara", 3, "Drew", 1), "BAD", Map.of());
    election.restoreFromData(candidates, votes, previous, List.of("Alice", "Bob"));
    candidates.get("LEADER").clear();
    assertEquals(List.of("Bob", "Cara", "Drew"), election.getWinners(Candidate.LEADER));
    assertEquals(List.of("Drew", "Cara", "Bob"), election.getCandidates(Candidate.LEADER));
    assertEquals("Cara", election.getVote(Candidate.LEADER, "Alice"));
    election.serializeCandidates().get("LEADER").clear();
    election.serializeVotes().get("LEADER").clear();
    election.serializePreviousVotes().get("LEADER").clear();
    assertEquals(3, election.getCandidates(Candidate.LEADER).size());
    assertEquals(1, election.getVotes(Candidate.LEADER, "cARA"));
    election.start();
    assertNull(election.getVote(Candidate.LEADER, "Alice"));
    election.addVote(Candidate.LEADER, "Alice", "Drew");
    assertEquals(List.of("Drew", "Bob", "Cara"), election.getWinners(Candidate.LEADER));
    election.cancel(Candidate.LEADER);
    assertTrue(election.isActive());
    assertTrue(election.serializePreviousVotes().get("LEADER").isEmpty());
    election.cancelAll();
    assertFalse(election.isActive());
    assertTrue(election.serializeCandidates().values().stream().allMatch(List::isEmpty));
  }

  @Test
  void
      candidateRulesExcludeGuildLeadersAndDuplicateGuildTicketsWhilePreservingTheExistingCandidate() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.addMember("Finn");
    guild.addMember("Grace");
    Election election = faction.getGovernment().getElection();
    assertFalse(election.canBeCandidate(Candidate.LEADER, "Stranger"));
    assertFalse(election.canBeCandidate(Candidate.LEADER, "Eve"));
    assertTrue(election.canBeCandidate(Candidate.LEADER, "Finn"));
    election.addCandidate(Candidate.LEADER, "Finn");
    assertTrue(election.isCandiate(Candidate.LEADER, "Finn"));
    assertFalse(election.canBeCandidate(Candidate.LEADER, "Finn"));
    assertTrue(election.canBeCandidate(Candidate.LEADER, "Finn", false));
    assertFalse(election.canBeCandidate(Candidate.LEADER, "Grace"));
    assertFalse(election.otherCandidateExists(Candidate.LEADER, "Bob"));
    assertFalse(election.otherCandidateExists(Candidate.LEADER, "Stranger"));
    election.addCandidate(Candidate.LEADER, "Bob");
    election.addCandidate(Candidate.LEADER, "Missing");
    election.validateCandidates();
    assertEquals(List.of("Finn", "Bob"), election.getCandidates(Candidate.LEADER));
    List<String> lore = new ArrayList<>();
    election.applyReasons(lore, Candidate.LEADER, fixture.player("Finn"));
    assertTrue(lore.isEmpty());
    election.applyReasons(lore, Candidate.LEADER, fixture.player("Eve"));
    assertTrue(lore.stream().anyMatch(line -> line.contains("Guild leaders")));
    lore.clear();
    election.applyReasons(lore, Candidate.LEADER, fixture.player("Stranger"));
    assertTrue(lore.stream().anyMatch(line -> line.contains("must be a member")));
    lore.clear();
    election.applyReasons(lore, Candidate.LEADER, fixture.player("Grace"));
    assertTrue(lore.stream().anyMatch(line -> line.contains("already has a Leader candidate")));
    election.addCandidate(Candidate.COUNCIL, "Eve");
    assertFalse(election.canBeCandidate(Candidate.COUNCIL, "Eve"));
    assertFalse(election.canBeCandidate(Candidate.COUNCIL, "Finn"));
    lore.clear();
    election.applyReasons(lore, Candidate.COUNCIL, fixture.player("Finn"));
    assertTrue(lore.stream().anyMatch(line -> line.contains("already has a Council candidate")));
    election.start();
    lore.clear();
    election.applyReasons(lore, Candidate.LEADER, fixture.player("Alice"));
    assertTrue(lore.stream().anyMatch(line -> line.contains("voting phase")));
  }

  @Test
  void activeElectionNotifiesOnlyUnfinishedEligibleVotersAtConfiguredBooths() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    Player alice = fixture.player("Alice");
    Player bob = fixture.player("Bob");
    Player stranger = fixture.player("Stranger");
    Election election = faction.getGovernment().getElection();
    election.addCandidate(Candidate.LEADER, "Bob");
    election.start();
    election.addVote(Candidate.LEADER, "Bob", "Bob");
    for (int i = 0; i < 301; i++) election.tick();
    verify(alice, never()).sendMessage(anyString());
    faction.getGovernment().addVotingBooth(new Location(fixture.ui.world, 4, 65, -3));
    for (int i = 0; i < 301; i++) election.tick();
    verify(alice).sendMessage(contains("Active Election!"));
    verify(alice).sendMessage(contains("4§fx §e65§fy §e-3§fz"));
    verify(bob, never()).sendMessage(anyString());
    verify(stranger, never()).sendMessage(anyString());
  }

  private Faction sessionFaction() {
    fixture.lawGroup(
        "constitution",
        Map.of(
            "effects.faction.rules",
            List.of("HAS_COUNCIL true", "APPOINTED_COUNCIL true"),
            "effects.faction.council-size",
            3));
    Faction faction = fixture.saved("home", "Alice");
    fixture.player("Alice");
    for (String member : List.of("Bob", "Cara", "Drew")) {
      faction.addMember(member);
      faction.getGovernment().getCouncil().addMemberForce(member);
    }
    fixture.player("Bob");
    fixture.player("Cara");
    return faction;
  }

  private Proposal tax(Faction faction, TaxTarget target, String id, double rate) {
    Proposal proposal = new Proposal("Alice", faction.getGovernment());
    proposal.setTaxProposal(new TaxLawChange(target, id, rate));
    return proposal;
  }

  private Session session(Faction faction) {
    SessionManager manager = fixture.ui.plugin.getSessionManager();
    manager.newSession(fixture.online.get("Alice"), faction);
    return manager.getSession(faction.getGovernment().getCouncil());
  }

  private Block lantern() {
    Block block = mock(Block.class);
    when(block.getWorld()).thenReturn(fixture.ui.world);
    when(block.getLocation()).thenAnswer(call -> new Location(fixture.ui.world, 1, 64, 2));
    when(fixture.ui.world.spawnEntity(any(Location.class), eq(EntityType.TEXT_DISPLAY)))
        .thenAnswer(call -> mock(TextDisplay.class));
    return block;
  }

  private BookMeta report(Player player) {
    ItemStack report =
        Arrays.stream(player.getInventory().getContents())
            .filter(item -> item != null && item.getType() == Material.WRITTEN_BOOK)
            .findFirst()
            .orElseThrow();
    return (BookMeta) report.getItemMeta();
  }

  @Test
  void multiProposalSessionKeepsEachRecordedVoteCountInItsFinalReport() {
    Faction faction = sessionFaction();
    Proposal first = tax(faction, TaxTarget.CITIZENS, null, 12);
    Proposal second = tax(faction, TaxTarget.GUILDS, null, 30);
    faction.getGovernment().propose(first);
    faction.getGovernment().propose(second);
    Session session = session(faction);
    Block lantern = lantern();
    session.onLanternClick(lantern);
    assertTrue(session.isStarted());
    assertSame(first, session.getCurrentProposal());
    assertSame(faction, session.getFaction());
    assertSame(lantern, session.getLantern());
    assertSame(fixture.online.get("Alice"), session.getLeader());
    assertFalse(session.recordVote("Stranger", Vote.YAY));
    assertTrue(session.recordVote("Alice", Vote.YAY));
    assertTrue(session.recordVote("Bob", Vote.YAY));
    assertTrue(session.recordVote("Cara", Vote.NAY));
    assertSame(first, session.getCurrentProposal());
    fixture.ui.runTasks();
    assertNull(session.getCurrentProposal());
    fixture.ui.runTasks();
    assertSame(second, session.getCurrentProposal());
    session.recordVote("Alice", Vote.NAY);
    session.recordVote("Bob", Vote.NAY);
    session.recordVote("Cara", Vote.ABSTAIN);
    fixture.ui.runTasks();
    fixture.ui.runTasks();
    assertFalse(faction.getGovernment().getCouncil().hasSession());
    assertEquals(12, faction.getTaxRate());
    assertEquals(10, faction.getTaxRate(TaxTarget.GUILDS, null, false));
    BookMeta report = report(fixture.online.get("Alice"));
    assertEquals(3, report.getPages().size());
    assertTrue(report.getPages().get(1).contains("§a2 §aYay"));
    assertTrue(report.getPages().get(1).contains("§c1 §cNay"));
    assertTrue(report.getPages().get(1).contains("§71 §7Abstain"));
    assertTrue(report.getPages().get(2).contains("§a0 §aYay"));
    assertTrue(report.getPages().get(2).contains("§c2 §cNay"));
    assertTrue(report.getPages().get(2).contains("§72 §7Abstain"));
  }

  @Test
  void sessionTimesOutBeforeSelectionAndRejectsInsufficientQuorumWithoutConsumingProposals() {
    Faction faction = sessionFaction();
    Proposal proposal = tax(faction, TaxTarget.CITIZENS, null, 12);
    faction.getGovernment().propose(proposal);
    Session timeout = session(faction);
    assertFalse(timeout.isStarted());
    for (int i = 0; i < 59; i++) timeout.tick();
    assertTrue(faction.getGovernment().getCouncil().hasSession());
    timeout.tick();
    assertFalse(faction.getGovernment().getCouncil().hasSession());
    assertEquals(
        List.of(proposal),
        faction.getGovernment().getCouncil().getProposalHandler().getProposals());
    when(fixture.online.get("Bob").isOnline()).thenReturn(false);
    Session denied = session(faction);
    denied.onLanternClick(lantern());
    assertFalse(denied.isStarted());
    verify(fixture.online.get("Alice")).sendMessage(contains("Not enough council members online"));
    assertEquals(
        List.of(proposal),
        faction.getGovernment().getCouncil().getProposalHandler().getProposals());
  }

  @Test
  void tieLeavesTheLawUnchangedAndStartedSessionIgnoresAnotherLantern() {
    Faction faction = sessionFaction();
    faction.getGovernment().propose(tax(faction, TaxTarget.CITIZENS, null, 12));
    Session session = session(faction);
    Block lantern = lantern();
    session.onLanternClick(lantern);
    session.onLanternClick(mock(Block.class));
    assertSame(lantern, session.getLantern());
    session.recordVote("Alice", Vote.YAY);
    session.recordVote("Bob", Vote.NAY);
    session.recordVote("Cara", Vote.ABSTAIN);
    fixture.ui.runTasks();
    session.countVotes();
    fixture.ui.runTasks();
    assertEquals(5, faction.getTaxRate());
    assertTrue(report(fixture.online.get("Alice")).getPages().get(1).contains("TIED"));
    verify(fixture.ui.world, times(20))
        .spawnParticle(eq(Particle.NOTE), any(Location.class), eq(1), eq(.5), eq(.5), eq(.5));
    session.kill();
    session.tick();
  }

  @Test
  void offlineSessionLeaderStillAppliesDecidedProposalsWithoutCreatingABook() {
    Faction faction = sessionFaction();
    faction.getGovernment().propose(tax(faction, TaxTarget.CITIZENS, null, 12));
    Session session = session(faction);
    session.start();
    assertNull(session.getLantern());
    session.recordVote("Alice", Vote.YAY);
    session.recordVote("Bob", Vote.YAY);
    session.recordVote("Cara", Vote.ABSTAIN);
    fixture.ui.runTasks();
    when(fixture.online.get("Alice").isOnline()).thenReturn(false);
    fixture.ui.runTasks();
    assertEquals(12, faction.getTaxRate());
    assertTrue(
        Arrays.stream(fixture.online.get("Alice").getInventory().getContents())
            .allMatch(item -> item == null));
    assertFalse(faction.getGovernment().getCouncil().hasSession());
  }

  @ParameterizedTest
  @EnumSource(
      value = TaxTarget.class,
      names = {"GUILD_ID", "VASSAL_ID", "TARIFF_ID"})
  void reportPreservesDeletedTaxTargetIdentityInsteadOfFailingTheCompletedSession(
      TaxTarget target) {
    Faction faction = fixture.saved("home", "Alice");
    SessionReport report = new SessionReport("Alice", faction);
    report.addResult(tax(faction, target, "deleted_faction", 15), VoteResult.PASSED, 3, 0, 1);
    BookMeta book = (BookMeta) assertDoesNotThrow(report::generateReportBook).getItemMeta();
    assertTrue(book.getPages().get(1).contains("deleted_faction"));
  }

  @Test
  void reportDescribesLawTaxAndVehicleFeeOutcomesWithTotalsAndVoteBreakdowns() {
    fixture.lawGroup("finance", Map.of("name", "Existing law"));
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = fixture.guild(faction, "merchants", "Bob");
    Faction vassal = fixture.saved("vassal", "Cara");
    SessionReport report = new SessionReport("Alice", faction);
    Proposal law = new Proposal("Alice", faction.getGovernment());
    law.setLawProposal(fixture.law("finance", "new", Map.of("name", "New law")));
    report.addResult(law, VoteResult.PASSED, 3, 0, 1);
    report.addResult(
        tax(faction, TaxTarget.GUILD_ID, guild.getId(), 15), VoteResult.FAILED, 1, 2, 1);
    report.addResult(
        tax(faction, TaxTarget.VASSAL_ID, vassal.getId(), 16), VoteResult.TIE, 2, 2, 0);
    report.addResult(
        tax(faction, TaxTarget.TARIFF_ID, vassal.getId(), 17), VoteResult.PASSED, 3, 0, 1);
    Proposal fee = new Proposal("Alice", faction.getGovernment());
    fee.setFeeProposal(new FeeChange(FeeKind.REGISTRATION_FEE, null, 1.5));
    report.addResult(fee, VoteResult.PASSED, 4, 0, 0);
    BookMeta book = (BookMeta) report.generateReportBook().getItemMeta();
    assertEquals("Governor Alice", book.getAuthor());
    assertTrue(book.getTitle().contains("Council Session"));
    assertEquals(6, book.getPages().size());
    assertTrue(book.getPages().getFirst().contains("Total Proposals\n5"));
    assertTrue(book.getPages().getFirst().contains("§aPassed§0: 3"));
    assertTrue(book.getPages().get(1).contains("New law"));
    assertTrue(book.getPages().get(2).contains(guild.getName()));
    assertTrue(book.getPages().get(3).contains(vassal.getName()));
    assertTrue(book.getPages().get(4).contains("17.0%"));
    assertTrue(book.getPages().get(5).contains("1.5x upkeep"));
  }

  @Test
  void aFullInventoryDoesNotDiscardTheCompletedSessionReport() {
    Faction faction = sessionFaction();
    faction.getGovernment().propose(tax(faction, TaxTarget.CITIZENS, null, 12));
    Player alice = fixture.online.get("Alice");
    when(alice.getWorld()).thenReturn(fixture.ui.world);
    for (int slot = 0; slot < alice.getInventory().getSize(); slot++)
      alice.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
    Session session = session(faction);
    session.start();
    session.recordVote("Alice", Vote.YAY);
    session.recordVote("Bob", Vote.YAY);
    session.recordVote("Cara", Vote.ABSTAIN);
    fixture.ui.runTasks();
    fixture.ui.runTasks();
    assertEquals(12, faction.getTaxRate());
    assertTrue(
        Arrays.stream(alice.getInventory().getContents())
            .allMatch(item -> item.getType() == Material.STONE && item.getAmount() == 64));
    verify(fixture.ui.world)
        .dropItemNaturally(
            eq(alice.getLocation()),
            argThat(
                (ItemStack item) ->
                    item.getType() == Material.WRITTEN_BOOK
                        && ((BookMeta) item.getItemMeta()).getPages().size() == 2));
    assertFalse(faction.getGovernment().getCouncil().hasSession());
  }

  @Test
  void councilCandidateRegistrationRejectsOutsidersAndOrdinaryGuildMembers() {
    fixture.lawGroup(
        "democracy",
        Map.of("effects.faction.rules", List.of("HAS_COUNCIL true", "ELECTED_COUNCIL true")));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    Guild guild = fixture.guild(faction, "merchants", "Cara");
    guild.addMember("Drew");
    Election election = faction.getGovernment().getElection();
    assertTrue(election.canBeCandidate(Candidate.COUNCIL, "Bob"));
    assertTrue(election.canBeCandidate(Candidate.COUNCIL, "Cara"));
    assertFalse(election.canBeCandidate(Candidate.COUNCIL, "Outsider"));
    assertFalse(election.canBeCandidate(Candidate.COUNCIL, "Drew"));
    assertFalse(election.canBeCandidate(Candidate.COUNCIL, "Alice"));
  }

  @Test
  void cancellingDisabledBallotsClearsTicketsAndDifferentGuildsCanPresentIndependentCandidates() {
    Faction faction = fixture.saved("home", "Alice");
    Guild first = fixture.guild(faction, "merchants", "Bob");
    Guild second = fixture.guild(faction, "artisans", "Cara");
    second.addMember("Drew");
    Election election = faction.getGovernment().getElection();
    election.addCandidate(Candidate.COUNCIL, "Missing");
    election.addCandidate(Candidate.COUNCIL, "Alice");
    election.addCandidate(Candidate.COUNCIL, first.getLeader());
    assertFalse(election.otherCandidateExists(Candidate.COUNCIL, second.getLeader()));
    election.cancel(Candidate.COUNCIL);
    assertFalse(election.isActive());
    assertTrue(election.getCandidates(Candidate.COUNCIL).isEmpty());
  }

  @Test
  void finishingANewElectionReplacesPreviousResultsInsteadOfKeepingWithdrawnCandidates() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    Election election = faction.getGovernment().getElection();
    election.restoreFromData(null, null, Map.of("LEADER", Map.of("Bob", 50)), null);
    election.addCandidate(Candidate.LEADER, "Cara");
    election.start();
    election.addVote(Candidate.LEADER, "Alice", "Cara");
    election.end();
    assertEquals("Cara", faction.getLeader());
    assertEquals(Map.of("Cara", 1), election.getPreviousVotes().get(Candidate.LEADER));
    assertEquals(List.of("Cara"), election.getWinners(Candidate.LEADER));
  }
}
