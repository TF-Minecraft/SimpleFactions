package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.government.election.*;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

class ElectionMenusCoverageTest {
  private org.mockito.MockedStatic<net.tfminecraft.tlibs.TLibs> items;
  private FactionDomainFixture fixture;
  private InventoryManager inventory;
  private Faction home;
  private Player alice;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    fixture.lawGroup(
        "constitution",
        Map.of(
            "effects.faction.rules",
            List.of("LEADER_ELECTIONS true", "HAS_COUNCIL true", "ELECTED_COUNCIL true"),
            "effects.faction.council-size",
            2));
    home = fixture.saved("home", "Alice");
    alice = fixture.player("Alice");
    inventory = new InventoryManager();
    items = mockStatic(net.tfminecraft.tlibs.TLibs.class);
    var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class);
    var creator = mock(net.tfminecraft.tlibs.objects.api.subapi.ItemCreator.class);
    when(api.getCreator()).thenReturn(creator);
    items.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
    when(creator.getItemFromPath(anyString()))
        .thenAnswer(call -> new ItemStack(org.bukkit.Material.PAPER));
    when(creator.getItemsAdderItem(anyString()))
        .thenAnswer(call -> new ItemStack(org.bukkit.Material.PAPER));
    when(fixture.ui.plugin.getProvinceManager())
        .thenReturn(new net.tfminecraft.simplefactions.managers.ProvinceManager());
  }

  @AfterEach
  void close() {
    if (items != null) items.close();
    fixture.close();
  }

  private Inventory top() {
    return alice.getOpenInventory().getTopInventory();
  }

  private String key(ItemStack item) {
    return item == null
        ? null
        : item.getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.STRING_KEY, PersistentDataType.STRING);
  }

  private int named(Inventory menu, String text) {
    for (int slot = 0; slot < menu.getSize(); slot++) {
      ItemStack item = menu.getItem(slot);
      if (item != null
          && item.getItemMeta() != null
          && item.getItemMeta().getDisplayName().contains(text)) return slot;
    }
    return -1;
  }

  @Test
  void everyCandidateCanBeReachedAndVotedForBeyondFiftyFourEntries() {
    Election election = home.getGovernment().getElection();
    for (int n = 0; n < 57; n++) {
      String candidate = "Candidate" + n;
      home.addMember(candidate);
      election.addCandidate(Candidate.LEADER, candidate);
    }
    election.start();
    inventory.electionView.votingView(alice, home, Candidate.LEADER);
    Set<String> reached = new HashSet<>();
    for (int page = 0; page < 8; page++) {
      Inventory menu = top();
      for (ItemStack item : menu.getContents()) {
        String id = key(item);
        if (id != null && id.startsWith("Candidate")) reached.add(id);
      }
      int next = named(menu, "Next");
      if (next < 0) break;
      inventory.electionView.click(fixture.ui.click(alice, next), menu, alice);
    }
    assertEquals(new HashSet<>(election.getCandidates(Candidate.LEADER)), reached);
    int last = named(top(), "Candidate56");
    assertTrue(last >= 0);
    inventory.electionView.click(fixture.ui.click(alice, last), top(), alice);
    assertEquals("Candidate56", election.getVote(Candidate.LEADER, "Alice"));
  }

  @Test
  void signupWithdrawalAndOneBallotUseActualElectionState() {
    Election election = home.getGovernment().getElection();
    inventory.electionView.electionView(alice, home);
    assertEquals("LEADER", key(top().getItem(0)));
    inventory.electionView.click(fixture.ui.click(alice, 0), top(), alice);
    assertEquals(List.of("Alice"), election.getCandidates(Candidate.LEADER));
    assertTrue(String.join(" ", top().getItem(0).getItemMeta().getLore()).contains("withdraw"));
    inventory.electionView.click(fixture.ui.click(alice, 0), top(), alice);
    assertTrue(election.getCandidates(Candidate.LEADER).isEmpty());
    home.addMember("Bob");
    election.addCandidate(Candidate.LEADER, "Bob");
    election.addCandidate(Candidate.COUNCIL, "Bob");
    election.start();
    inventory.electionView.electionView(alice, home);
    inventory.electionView.click(fixture.ui.click(alice, 0), top(), alice);
    Inventory ballot = top();
    assertEquals("Bob", key(ballot.getItem(0)));
    inventory.electionView.click(fixture.ui.click(alice, 0), ballot, alice);
    assertEquals("Bob", election.getVote(Candidate.LEADER, "Alice"));
    assertEquals(1, election.getVotes(Candidate.LEADER, "Bob"));
    inventory.electionView.electionView(alice, home);
    assertTrue(election.canVote("Alice"));
    assertTrue(
        String.join(" ", top().getItem(0).getItemMeta().getLore()).contains("already voted"));
  }

  @Test
  void staleCandidateMetadataDoesNotCastOrOverwriteABallot() {
    Election election = home.getGovernment().getElection();
    home.addMember("Bob");
    election.addCandidate(Candidate.LEADER, "Bob");
    inventory.electionView.electionView(alice, home);
    ItemStack item = top().getItem(0);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(Keys.STRING_KEY, PersistentDataType.STRING, "removed-office");
    item.setItemMeta(meta);
    inventory.electionView.click(fixture.ui.click(alice, 0), top(), alice);
    assertEquals(List.of("Bob"), election.getCandidates(Candidate.LEADER));
    election.start();
    inventory.electionView.votingView(alice, home, Candidate.LEADER);
    item = top().getItem(0);
    meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, "removed-office");
    item.setItemMeta(meta);
    java.io.PrintStream stderr = System.err;
    java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
    try {
      System.setErr(new java.io.PrintStream(captured));
      inventory.electionView.click(fixture.ui.click(alice, 0), top(), alice);
    } finally {
      System.setErr(stderr);
    }
    assertTrue(captured.toString().contains("removed-office"));
    assertNull(election.getVote(Candidate.LEADER, "Alice"));
    assertEquals(0, election.getVotes(Candidate.LEADER, "Bob"));
    meta.getPersistentDataContainer()
        .set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, "LEADER");
    item.setItemMeta(meta);
    election.removeCandidate(Candidate.LEADER, "Bob");
    inventory.electionView.click(fixture.ui.click(alice, 0), top(), alice);
    assertNull(election.getVote(Candidate.LEADER, "Alice"));
  }

  @Test
  void previousPageAndRefreshAfterWithdrawalsKeepCandidatesAccessible() {
    Election election = home.getGovernment().getElection();
    for (int n = 0; n < 50; n++) {
      home.addMember("Candidate" + n);
      election.addCandidate(Candidate.LEADER, "Candidate" + n);
    }
    election.start();
    inventory.electionView.votingView(alice, home, Candidate.LEADER);
    Inventory menu = top();
    SFInventoryHolder holder = (SFInventoryHolder) menu.getHolder();
    inventory.electionView.click(fixture.ui.click(alice, 53), menu, alice);
    assertEquals(1, holder.getPage());
    inventory.electionView.click(fixture.ui.click(alice, 45), menu, alice);
    assertEquals(0, holder.getPage());
    inventory.electionView.click(fixture.ui.click(alice, 53), menu, alice);
    for (int n = 1; n < 50; n++) election.removeCandidate(Candidate.LEADER, "Candidate" + n);
    inventory.electionView.votingView(alice, home, Candidate.LEADER, menu);
    assertEquals(0, holder.getPage());
    assertEquals("Candidate0", key(menu.getItem(0)));
    assertNull(menu.getItem(53));
    assertNull(election.getVote(Candidate.LEADER, "Alice"));
  }
}
