package net.tfminecraft.simplefactions.inactivity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An inactive member stays in the realm but counts for nothing and holds no office but the realm's leadership. */
class InactiveMembersTest {
  private PersistenceFilesFixture disk;
  private FactionDomainFixture fixture;
  private Field historyField;
  private Object originalHistory;

  @BeforeEach
  void setup() throws Exception {
    disk = new PersistenceFilesFixture();
    Files.createDirectories(disk.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    when(fixture.ui.plugin.getLogger()).thenReturn(mock(Logger.class));
    historyField = FactionCleanup.class.getDeclaredField("offlineDays");
    historyField.setAccessible(true);
    originalHistory = historyField.get(null);
    history(Map.of());
  }

  @AfterEach
  void cleanup() throws Exception {
    try {
      if (historyField != null) historyField.set(null, originalHistory);
      if (fixture != null) fixture.close();
    } finally {
      if (disk != null) disk.close();
    }
  }

  private void history(Map<String, Integer> days) throws Exception {
    disk.write("Cache/logins.json", JsonUtil.GSON.toJson(days));
    historyField.set(null, null);
  }

  private void away(String... members) throws Exception {
    Map<String, Integer> days = new HashMap<>();
    for (String member : members) days.put(member, InactivityRules.DAYS_UNTIL_INACTIVE);
    history(days);
  }

  private Faction realm(String leader, String... members) {
    Faction faction = fixture.saved("home", leader);
    for (String member : members) faction.addMember(member);
    return faction;
  }

  @Test
  void onlyActiveMembersAreKept() throws Exception {
    away("Bob");
    assertEquals(List.of(), InactivityService.activeOnly(null));
    assertEquals(
        List.of("Alice", "dummy_bot"),
        InactivityService.activeOnly(Arrays.asList("Alice", "Bob", "dummy_bot")));
  }

  @Test
  void anInactiveMemberAddsNoPrestigeUntilTheyReturn() throws Exception {
    Faction faction = realm("Alice", "Bob", "Carl");
    faction.updatePrestige();
    double active = faction.getPrestige();

    away("Bob", "Carl");
    faction.updatePrestige();
    assertEquals(List.of("Alice"), faction.getActiveMembers());
    assertTrue(faction.getPrestige() < active);

    history(Map.of());
    faction.updatePrestige();
    assertEquals(active, faction.getPrestige());
  }

  @Test
  void anInactiveMemberCannotSitOnTheCouncilVoteStandOrSucceed() throws Exception {
    Faction faction = realm("Alice", "Bob", "Carl");
    var council = faction.getGovernment().getCouncil();
    council.addMemberForce("Bob");
    away("Bob");

    council.toggleRefuse("Carl");
    history(Map.of("Bob", InactivityRules.DAYS_UNTIL_INACTIVE, "Carl", InactivityRules.DAYS_UNTIL_INACTIVE));
    council.replace();
    assertFalse(council.isMember("Bob"));
    assertTrue(council.refuses("Carl"));
    away("Bob");
    council.toggleRefuse("Carl");
    assertFalse(council.canRemainMember("Bob"));
    assertTrue(council.canRemainMember("Carl"));
    assertFalse(council.canBeMember("Bob", true, true));
    assertTrue(council.canBeMember("Carl", true, true));
    assertFalse(faction.canVote("Bob"));
    assertTrue(faction.canVote("Carl"));
    assertFalse(faction.canBecomeLeader("Bob"));
    assertTrue(faction.canBecomeLeader("Carl"));
    var election = faction.getGovernment().getElection();
    assertFalse(election.canBeCandidate(Candidate.LEADER, "Bob", false));
    assertTrue(election.canBeCandidate(Candidate.LEADER, "Carl", false));
  }

  @Test
  void anInactiveLeaderStaysLeader() throws Exception {
    Faction faction = realm("Alice", "Bob");
    away("Alice");
    faction.getGovernment().replace();
    assertEquals("Alice", faction.getLeader());
  }

  @Test
  void anInactiveMovementLeaderLosesTheLeadAndInactiveSupportersAddNoPower() throws Exception {
    Faction faction = realm("Alice", "Bob", "Carl", "Dana");
    Proposal proposal = new Proposal("Bob", faction.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
    Movement movement = new Movement(faction, "Bob", proposal);
    faction.getGovernment().getMovements().add(movement);
    var cause = movement.getCauses().get(0);
    cause.getPool().addCitizen("Carl");
    double power = movement.getPower();
    assertTrue(cause.canBeLeader("Bob"));

    away("Bob");
    assertFalse(cause.canBeLeader("Bob"));
    assertTrue(movement.getPower() < power);
    movement.tick();
    assertNull(movement.getLeader());
    assertNull(cause.getLeader());
    assertTrue(cause.getPool().getCitizens().contains("Bob"));
  }

  @Test
  void aBranchCountsOnlyItsActiveMembersTowardItsShareOfTheRealm() throws Exception {
    Faction faction = realm("Alice", "Dana");
    Guild branch = fixture.guild(faction, "smiths", "Bob");
    branch.addMember("Carl");
    assertEquals(0.5, branch.getMemberPercentage());

    away("Carl");
    assertEquals(List.of("Bob"), branch.getActiveMembers());
    assertEquals(1 / 3.0, branch.getMemberPercentage(), 1e-9);
    away("Alice", "Bob", "Carl", "Dana");
    assertEquals(0.0, branch.getMemberPercentage());
  }

  @Test
  void newCountiesNeedAnActiveMemberEach() throws Exception {
    Tier county = TierLoader.getByString("county");
    Faction faction = mock(Faction.class);
    List<Title> held = new ArrayList<>(List.of(mock(Title.class)));
    when(faction.getTitles(county)).thenReturn(held);
    when(faction.getMembers()).thenReturn(List.of("Alice", "Bob"));
    when(faction.getActiveMembers()).thenReturn(List.of("Alice", "Bob"));
    assertFalse(Title.countyLimitReached(faction, county));

    when(faction.getActiveMembers()).thenReturn(List.of("Alice"));
    assertTrue(Title.countyLimitReached(faction, county));
  }
}
