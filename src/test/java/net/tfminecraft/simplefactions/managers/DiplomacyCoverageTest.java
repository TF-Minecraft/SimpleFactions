package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.RelationRequest;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiplomacyCoverageTest {
  private FactionDomainFixture fixture;
  private Field requestsField;
  private Object originalRequests;
  private Field tickField;
  private int originalTick;

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    fixture.lawGroup(
        "government", Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS true")));
    requestsField = RequestManager.class.getDeclaredField("requests");
    requestsField.setAccessible(true);
    originalRequests = requestsField.get(null);
    requestsField.set(null, new HashMap<>());
    tickField = RelationManager.class.getDeclaredField("tick");
    tickField.setAccessible(true);
    originalTick = tickField.getInt(null);
    tickField.setInt(null, 0);
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (requestsField != null) requestsField.set(null, originalRequests);
      if (tickField != null) tickField.setInt(null, originalTick);
    } finally {
      if (fixture != null) fixture.close();
    }
  }

  @Test
  void failedSubjectTransferPreservesTheOriginalOverlordAndBothRelationshipDirections() {
    Faction old = fixture.saved("old", "Alice");
    Faction subject = fixture.saved("subject", "Bob");
    Faction recipient = fixture.saved("recipient", "Cara");
    RelationType limited =
        fixture.relationType("limited", Map.of("vassal", true, "link", "overlord", "limit", 0));
    old.setRelation(subject, new Relation(limited, RelationLoader.getDefaultAttitude()));
    subject.setRelation(
        old, new Relation(RelationLoader.getType("overlord"), RelationLoader.getDefaultAttitude()));
    assertTrue(recipient.canHaveVassals());
    assertTrue(RelationManager.atLimit(recipient, limited));
    RelationManager.transferSubject(subject, recipient);
    assertEquals("old", RelationManager.getOverlord(subject));
    assertSame(limited, old.getRelation(subject.getId()).getType());
    assertFalse(recipient.getRelation(subject.getId()).getType().isVassalage());
  }

  private Attitude attitude(String id, int target, double cost) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(id + ".name", id);
    yaml.set(id + ".target", target);
    yaml.set(id + ".cost", cost);
    Attitude attitude = new Attitude(id, yaml.getConfigurationSection(id));
    RelationLoader.attitudes.add(attitude);
    return attitude;
  }

  private void opinion(Faction from, Faction to, int opinion) {
    from.setRelation(
        to,
        new Relation(
            from.getRelation(to.getId()).getType(), RelationLoader.getDefaultAttitude(), opinion));
  }

  private RelationType selected(String kind, Faction from, Faction to) {
    return switch (kind) {
      case "trade" -> from.getDiplomacyHandler().getTradeRelation(to.getId());
      case "treaty" -> from.getDiplomacyHandler().getTreatyRelation(to.getId());
      default -> from.getRelation(to.getId()).getType();
    };
  }

  private void set(
      String kind,
      Player player,
      RelationType type,
      Faction target,
      Faction origin,
      boolean check) {
    switch (kind) {
      case "trade" -> RelationManager.setTradeRelation(player, type, target, origin, check);
      case "treaty" -> RelationManager.setTreatyRelation(player, type, target, origin, check);
      default -> RelationManager.setRelation(player, type, target, origin, check);
    }
  }

  @Test
  void successfulSubjectTransferKeepsTheRelationKindAndRejectsCyclesSelfAndSameHost() {
    Faction old = fixture.saved("old", "Alice");
    Faction subject = fixture.saved("subject", "Bob");
    Faction recipient = fixture.saved("recipient", "Cara");
    Faction child = fixture.saved("child", "Drew");
    fixture.subject(old, subject);
    fixture.subject(subject, child);
    RelationType kind = old.getRelation(subject.getId()).getType();
    for (Faction invalid : List.of(old, subject, child)) {
      RelationManager.transferSubject(subject, invalid);
      assertEquals("old", RelationManager.getOverlord(subject));
      assertSame(kind, old.getRelation(subject.getId()).getType());
      assertEquals("subject", RelationManager.getOverlord(child));
    }
    RelationManager.transferSubject(subject, recipient);
    assertEquals("recipient", RelationManager.getOverlord(subject));
    assertSame(kind, recipient.getRelation(subject.getId()).getType());
    assertSame(RelationLoader.getDefaultType(), old.getRelation(subject.getId()).getType());
    assertSame(RelationLoader.getDefaultType(), subject.getRelation(old.getId()).getType());
    assertEquals("subject", RelationManager.getOverlord(child));
  }

  @Test
  void subjectTransfersDoNothingForAbsentOrIndependentPartiesAndDisallowedRecipients() {
    Faction old = fixture.saved("old", "Alice");
    Faction subject = fixture.saved("subject", "Bob");
    Faction recipient = fixture.saved("recipient", "Cara");
    RelationManager.transferSubject(null, recipient);
    RelationManager.transferSubject(subject, null);
    RelationManager.transferSubject(subject, recipient);
    assertNull(RelationManager.getOverlord(subject));
    fixture.subject(old, subject);
    var law =
        fixture.law(
            "government",
            "independent",
            Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS false")));
    recipient.getLawHandler().getGroup("government").setCurrent(law);
    assertFalse(recipient.canHaveVassals());
    RelationManager.transferSubject(subject, recipient);
    assertEquals(old.getId(), RelationManager.getOverlord(subject));
    recipient
        .getLawHandler()
        .getGroup("government")
        .setCurrent(recipient.getLawHandler().getLaw("government", "current"));
    FactionManager.factions.remove(old);
    RelationManager.transferSubject(subject, recipient);
    assertEquals(old.getId(), RelationManager.getOverlord(subject));
    assertFalse(recipient.getRelation(subject.getId()).getType().isVassalage());
    assertEquals(old.getId(), RelationManager.getTopLiege(subject));
    assertFalse(RelationManager.isOnOverlordPath(subject, recipient));
  }

  @Test
  void hourlyTicksMoveOpinionOnceAndBreakingVassalagePreservesOpinions() {
    Faction host = fixture.saved("host", "Alice");
    Faction subject = fixture.saved("subject", "Bob");
    fixture.subject(host, subject);
    Attitude hostile = attitude("hostile", -50, 0);
    subject.setRelation(host, new Relation(RelationLoader.getType("overlord"), hostile, 12));
    opinion(host, subject, 6);
    for (int i = 0; i < 3599; i++) RelationManager.tick();
    assertEquals(12, subject.getRelation(host.getId()).getOpinion());
    RelationManager.tick();
    assertEquals(11, subject.getRelation(host.getId()).getOpinion());
    assertEquals(5, host.getRelation(subject.getId()).getOpinion());
    assertTrue(RelationManager.endVassalage(subject, host, true));
    for (Relation relation :
        List.of(subject.getRelation(host.getId()), host.getRelation(subject.getId()))) {
      assertSame(RelationLoader.getDefaultType(), relation.getType());
      assertSame(hostile, relation.getAttitude());
    }
    assertEquals(11, subject.getRelation(host.getId()).getOpinion());
    assertFalse(RelationManager.endVassalage(subject, host, false));
    RelationManager.reset(host, subject, false);
    assertSame(
        RelationLoader.getDefaultAttitude(), host.getRelation(subject.getId()).getAttitude());
    assertEquals(5, host.getRelation(subject.getId()).getOpinion());
  }

  @Test
  void subjectAndAllyQueriesRespectNestedGraphsAndPruneDeletedSubjects() {
    Faction root = fixture.saved("root", "Alice");
    Faction middle = fixture.saved("middle", "Bob");
    Faction leaf = fixture.saved("leaf", "Cara");
    Faction foreign = fixture.saved("foreign", "Drew");
    fixture.subject(root, middle);
    fixture.subject(middle, leaf);
    RelationType ally = fixture.relationType("ally", Map.of("mutual", true));
    root.setRelation(foreign, new Relation(ally, RelationLoader.getDefaultAttitude()));
    assertEquals(List.of(foreign), RelationManager.getAllies(root));
    assertEquals(List.of(middle), RelationManager.getSubjects(root));
    assertTrue(RelationManager.getSubjects(null).isEmpty());
    assertEquals(root.getId(), RelationManager.getTopLiege(leaf));
    assertNull(RelationManager.getTopLiege(root));
    assertTrue(RelationManager.isOnOverlordPath(leaf, middle));
    assertTrue(RelationManager.isOnOverlordPath(leaf, root));
    assertFalse(RelationManager.isOnOverlordPath(leaf, foreign));
    assertTrue(RelationManager.sameRealm(root, root));
    assertTrue(RelationManager.sameRealm(root, leaf));
    assertTrue(RelationManager.sameRealm(leaf, root));
    assertFalse(RelationManager.sameRealm(root, foreign));
    assertFalse(RelationManager.sameRealm(null, root));
    assertTrue(RelationManager.vassalCheck(middle, root));
    assertFalse(RelationManager.vassalCheck(middle, foreign));
    assertTrue(RelationManager.vassalCheck(foreign, root));
    FactionManager.factions.remove(middle);
    assertTrue(RelationManager.getSubjects(root).isEmpty());
    assertFalse(root.getRelations().containsKey(middle.getId()));
    assertEquals(List.of(foreign), RelationManager.getAllies(root));
  }

  @ParameterizedTest
  @ValueSource(strings = {"relation", "trade", "treaty"})
  void mutualRequestsWaitForConsentThenUpdateBothParties(String kind) {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    Player alice = fixture.player("Alice");
    Player bob = fixture.player("Bob");
    RelationType pact =
        fixture.relationType(
            kind,
            Map.of(
                "name",
                "Pact",
                "mutual",
                true,
                "trade-agreement",
                kind.equals("trade"),
                "treaty",
                kind.equals("treaty")));
    set(kind, alice, pact, target, origin, true);
    RelationRequest request =
        assertInstanceOf(RelationRequest.class, RequestManager.getRequest(bob));
    assertSame(pact, request.getType());
    assertSame(origin, request.getFaction());
    assertNotSame(pact, selected(kind, origin, target));
    verify(bob).sendMessage("§7Type §a/faction accept §7to accept");
    RequestManager.accept(bob);
    assertFalse(RequestManager.hasRequest(bob));
    assertSame(pact, selected(kind, origin, target));
    assertSame(pact, selected(kind, target, origin));
    verify(alice).sendMessage(contains("accepted your request"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"relation", "trade", "treaty"})
  void requestsRequireAnOnlineTargetAndCurrentLeadershipWhenAccepted(String kind) {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    Player alice = fixture.player("Alice");
    RelationType pact = fixture.relationType(kind, Map.of("mutual", true));
    set(kind, alice, pact, target, origin, true);
    verify(alice).sendMessage(contains("leader is not online"));
    Player bob = fixture.player("Bob");
    when(bob.isOnline()).thenReturn(false);
    clearInvocations(alice);
    set(kind, alice, pact, target, origin, true);
    verify(alice).sendMessage(contains("leader is not online"));
    assertFalse(RequestManager.hasRequest(bob));
    when(bob.isOnline()).thenReturn(true);
    set(kind, alice, pact, target, origin, true);
    assertTrue(RequestManager.hasRequest(bob));
    target.addMember("Cara");
    target.setLeader("Cara");
    RequestManager.accept(bob);
    assertFalse(RequestManager.hasRequest(bob));
    verify(bob).sendMessage("§cYou do not have a faction");
    assertNotSame(pact, selected(kind, origin, target));
    assertNotSame(pact, selected(kind, target, origin));
  }

  @ParameterizedTest
  @ValueSource(strings = {"relation", "trade", "treaty"})
  void opinionThresholdsMustBeMetOnBothSidesBeforeTheRelationshipChanges(String kind) {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    Player alice = fixture.player("Alice");
    RelationType pact =
        fixture.relationType(
            kind, Map.of("mutual", true, "threshold.amount", 20, "threshold.mutual", true));
    set(kind, alice, pact, target, origin, false);
    assertNotSame(pact, selected(kind, origin, target));
    verify(alice).sendMessage(contains("You need an opinion"));
    verify(alice).sendMessage(contains("They need an opinion"));
    opinion(origin, target, 20);
    set(kind, null, pact, target, origin, false);
    assertNotSame(pact, selected(kind, origin, target));
    opinion(target, origin, 20);
    set(kind, alice, pact, target, origin, false);
    assertSame(pact, selected(kind, origin, target));
    assertSame(pact, selected(kind, target, origin));
    assertEquals(20, origin.getRelation(target.getId()).getOpinion());
  }

  @Test
  void forcedOverlaysPreserveBaseRelationsAndClearingTreatiesDoesNotRemoveTrade() {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    opinion(origin, target, 7);
    RelationType treaty =
        fixture.relationType("peace", Map.of("mutual", true, "treaty", true, "blocks-war", true));
    RelationType trade =
        fixture.relationType(
            "embargo", Map.of("mutual", true, "trade-agreement", true, "blocks-shops", true));
    RelationType clear = fixture.relationType("clear", Map.of("treaty", true, "clear", true));
    RelationManager.setTreatyRelationForced(null, target, origin);
    RelationManager.setTradeRelationForced(null, target, origin);
    assertNull(origin.getDiplomacyHandler().getTreatyRelation(target.getId()));
    RelationManager.setTreatyRelationForced(treaty, target, origin);
    RelationManager.setTradeRelationForced(trade, target, origin);
    assertSame(treaty, target.getDiplomacyHandler().getTreatyRelation(origin.getId()));
    assertSame(trade, target.getDiplomacyHandler().getTradeRelation(origin.getId()));
    assertTrue(RelationManager.hasNonAggressionPact(origin, target));
    assertTrue(RelationManager.hasTradeEmbargo(origin, target));
    assertFalse(RelationManager.hasTradeEmbargo(null, target));
    assertFalse(RelationManager.hasNonAggressionPact(origin, null));
    assertEquals(7, origin.getRelation(target.getId()).getOpinion());
    RelationManager.setTreatyRelationForced(clear, target, origin);
    assertFalse(RelationManager.hasNonAggressionPact(origin, target));
    assertTrue(RelationManager.hasTradeEmbargo(origin, target));
    RelationManager.setTreatyRelationForced(treaty, target, origin);
    Player alice = fixture.player("Alice");
    RelationManager.setTreatyRelation(alice, clear, target, origin, false);
    verify(alice).sendMessage(contains("Cleared treaty"));
    assertNull(target.getDiplomacyHandler().getTreatyRelation(origin.getId()));
  }

  @Test
  void forcedRelationsStillRejectLawsExistingOverlordsLimitsAndAncestryLoops() {
    Faction root = fixture.saved("root", "Alice");
    Faction middle = fixture.saved("middle", "Bob");
    Faction leaf = fixture.saved("leaf", "Cara");
    Faction foreign = fixture.saved("foreign", "Drew");
    Player player = fixture.player("Alice");
    RelationType vassal = RelationLoader.getType("vassal");
    fixture.subject(root, middle);
    fixture.subject(middle, leaf);
    assertFalse(RelationManager.setRelation(player, vassal, root, leaf, false, false));
    assertFalse(RelationManager.setRelation(player, vassal, middle, leaf, false, false));
    assertFalse(RelationManager.setRelation(player, vassal, leaf, foreign, false, false));
    verify(player).sendMessage("§cThis faction is your top overlord");
    verify(player, times(2)).sendMessage("§cThis faction is already a subject of someone else");
    var law =
        fixture.law(
            "government",
            "independent",
            Map.of("effects.faction.rules", List.of("CAN_HAVE_VASSALS false")));
    foreign.getLawHandler().getGroup("government").setCurrent(law);
    assertFalse(RelationManager.setRelation(player, vassal, root, foreign, false, false));
    verify(player).sendMessage("§cYour faction cannot have vassals!");
    RelationType limited = fixture.relationType("limited", Map.of("limit", 0));
    assertFalse(RelationManager.setRelation(player, limited, foreign, root, false, false));
    verify(player).sendMessage("§cYou have reached the limit for this relation type");
    assertEquals(1, RelationManager.getRelationCount(root, vassal));
    assertFalse(RelationManager.atLimit(root, vassal));
    assertFalse(RelationManager.setRelationForced(null, root, foreign));
    assertEquals(root.getId(), RelationManager.getOverlord(middle));
    assertEquals(middle.getId(), RelationManager.getOverlord(leaf));
  }

  @Test
  void settingAndReplacingVassalageUpdatesLinkedRelationsAndTheMapWithoutLosingOpinion() {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    Player alice = fixture.player("Alice");
    Player bob = fixture.player("Bob");
    opinion(origin, target, 17);
    opinion(target, origin, -3);
    RelationType vassal = RelationLoader.getType("vassal");
    assertTrue(RelationManager.setRelation(alice, vassal, target, origin, false, false));
    assertEquals(origin.getId(), RelationManager.getOverlord(target));
    verify(fixture.map, times(2)).enqueue(eq("nation"), anyString());
    assertSame(
        RelationLoader.getType("overlord"),
        RelationManager.partnerRelationAfter(target, origin, vassal));
    assertFalse(RelationManager.reverseChange(target, origin, vassal));
    RelationType ally = fixture.relationType("ally", Map.of("mutual", true));
    assertTrue(RelationManager.reverseChange(target, origin, ally));
    assertTrue(RelationManager.setRelation(alice, ally, target, origin, false, false));
    verify(bob).sendMessage(contains("has been changed"));
    assertSame(ally, target.getRelation(origin.getId()).getType());
    assertEquals(17, origin.getRelation(target.getId()).getOpinion());
    assertEquals(-3, target.getRelation(origin.getId()).getOpinion());
    RelationType unilateral = fixture.relationType("friendly", Map.of("name", "Friendly"));
    RelationManager.reset(origin, target, false);
    assertSame(
        RelationLoader.getDefaultType(),
        RelationManager.partnerRelationAfter(target, origin, unilateral));
    assertTrue(RelationManager.setRelation(alice, unilateral, target, origin, false, false));
    assertSame(RelationLoader.getDefaultType(), target.getRelation(origin.getId()).getType());
  }

  @Test
  void acceptingAnAllianceRequestRechecksAWarThatStartedAfterItWasSent() {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    Faction enemy = fixture.saved("enemy", "Cara");
    Player alice = fixture.player("Alice");
    Player bob = fixture.player("Bob");
    RelationType ally = fixture.relationType("ally", Map.of("mutual", true));
    assertFalse(RelationManager.setRelation(alice, ally, target, origin, true, false));
    WarManager.get().add(new War(21, origin, enemy));
    RequestManager.accept(bob);
    assertFalse(RequestManager.hasRequest(bob));
    assertNotSame(ally, origin.getRelation(target.getId()).getType());
    verify(bob).sendMessage(contains("cannot make new allies while at war"));
    verify(alice).sendMessage(contains("cannot make new allies while at war"));
    verify(alice, never()).sendMessage(contains("accepted your request"));
  }

  @Test
  void attitudesRespectCapacityAndAllowKeepingOrCheaperChangesWhileOverCapacity() {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    target.setPrestige(100.0);
    Player player = fixture.player("Alice");
    Attitude costly = attitude("costly", 20, 2);
    Attitude free = attitude("free", 10, 0);
    assertFalse(RelationManager.setAttitude(null, costly, target, origin));
    assertFalse(RelationManager.setAttitude(player, costly, target, origin));
    verify(player).sendMessage("§cYou lack diplomatic capacity for this attitude!");
    origin.setRelation(target, new Relation(RelationLoader.getDefaultType(), costly));
    assertTrue(RelationManager.setAttitude(player, costly, target, origin));
    assertSame(costly, origin.getRelation(target.getId()).getAttitude());
    assertTrue(RelationManager.setAttitude(player, free, target, origin));
    assertSame(free, origin.getRelation(target.getId()).getAttitude());
    assertEquals(0, RelationManager.getDiplomaticCost(origin, target, (Attitude) null));
    assertEquals(20, RelationManager.getDiplomaticCost(origin, target, costly));
  }

  @Test
  void capacityCalculationsIncludePartnerLinksBlocShareAndPrestigeDiminishingReturns() {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    Faction third = fixture.saved("third", "Cara");
    origin.setPrestige(100.0);
    target.setPrestige(400.0);
    third.setPrestige(500.0);
    RelationType scaled =
        fixture.relationType("scaled", Map.of("cost", 2.0, "bloc-scaling", 1.0, "mutual", true));
    assertEquals(60, RelationManager.getDiplomaticCost(origin, target, scaled));
    assertTrue(
        RelationManager.actorLacksCapacity(
            origin, target, scaled, RelationLoader.getDefaultType()));
    assertTrue(RelationManager.partnerLacksRelationCapacity(origin, target, scaled));
    assertTrue(RelationManager.partnerLacksOverlayCapacity(origin, target, scaled, null));
    assertFalse(RelationManager.actorLacksCapacity(origin, target, scaled, scaled));
    assertFalse(RelationManager.actorLacksCapacity(null, target, scaled, null));
    assertFalse(RelationManager.partnerLacksRelationCapacity(null, target, scaled));
    assertFalse(RelationManager.partnerLacksOverlayCapacity(origin, target, null, null));
    assertNull(RelationManager.partnerRelationAfter(target, origin, null));
    RelationType unilateral = fixture.relationType("unilateral", Map.of("cost", 2.0));
    assertFalse(RelationManager.partnerLacksOverlayCapacity(origin, target, unilateral, null));
    RelationType nonSettable =
        fixture.relationType("fixed", Map.of("cost", 9.0, "settable", false));
    assertEquals(0, RelationManager.getDiplomaticCost(origin, target, nonSettable));
    RelationType vassal = fixture.relationType("paid_subject", Map.of("cost", 3.0, "vassal", true));
    assertEquals(20, RelationManager.getDiplomaticCost(origin, target, vassal));
    assertEquals(0, RelationManager.getDiplomaticCost(origin, target, (RelationType) null));
    target.setPrestige(-10.0);
    assertEquals(0, RelationManager.getDiplomaticCost(origin, target, scaled));
    assertFalse(RelationManager.lacksCapacityForChange(-10, 5, 6));
    assertFalse(RelationManager.lacksCapacityForChange(5, 10, 5));
    assertTrue(RelationManager.lacksCapacityForChange(4, 10, 5));
  }

  @ParameterizedTest
  @ValueSource(strings = {"relation", "trade", "treaty"})
  void rejectedStaleRequestsNeverAnnounceSuccessfulAcceptance(String kind) {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    Player alice = fixture.player("Alice");
    Player bob = fixture.player("Bob");
    RelationType pact =
        fixture.relationType(
            kind,
            Map.of(
                "name", "Pact", "mutual", true, "threshold.amount", 20, "threshold.mutual", true));
    opinion(origin, target, 20);
    opinion(target, origin, 20);
    set(kind, alice, pact, target, origin, true);
    assertTrue(RequestManager.hasRequest(bob));
    opinion(origin, target, 0);
    clearInvocations(alice, bob);
    RequestManager.accept(bob);
    assertFalse(RequestManager.hasRequest(bob));
    assertNotSame(pact, selected(kind, origin, target));
    assertNotSame(pact, selected(kind, target, origin));
    verify(alice, never()).sendMessage(contains("accepted your request"));
  }

  @Test
  void nullableDiplomacyQueriesAndForcedUpdatesDoNotChangeExistingRelationships() {
    Faction origin = fixture.saved("origin", "Alice");
    Faction target = fixture.saved("target", "Bob");
    fixture.subject(origin, target);
    RelationType type = RelationLoader.getType("vassal");
    assertNull(RelationManager.wartimeBlock(null, target, origin));
    assertNull(RelationManager.wartimeBlock(type, null, origin));
    assertNull(RelationManager.wartimeBlock(type, target, null));
    assertFalse(RelationManager.isTributaryOf(null, target));
    assertFalse(RelationManager.isTributaryOf(origin, null));
    assertFalse(RelationManager.hasTradeEmbargo(origin, null));
    assertFalse(RelationManager.hasNonAggressionPact(null, target));
    RelationManager.setTradeRelationForced(type, null, origin);
    RelationManager.setTreatyRelationForced(type, target, null);
    assertEquals("origin", RelationManager.getOverlord(target));
    assertSame(type, origin.getRelation(target.getId()).getType());
    assertNull(origin.getDiplomacyHandler().getTradeRelation(target.getId()));
    assertNull(origin.getDiplomacyHandler().getTreatyRelation(target.getId()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aFactionCannotBecomeItsOwnVassal(boolean forced) {
    Faction faction = fixture.saved("home", "Alice");
    Player player = fixture.player("Alice");
    RelationType vassal = RelationLoader.getType("vassal");
    try {
      assertFalse(RelationManager.setRelation(player, vassal, faction, faction, false, forced));
      assertFalse(faction.getRelations().containsKey(faction.getId()));
      assertNull(RelationManager.getOverlord(faction));
    } finally {
      // Restore this public map even on the broken implementation, which writes a self-overlord
      // edge.
      faction.getRelations().remove(faction.getId());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void partialPublicRelationEntriesDoNotBecomeTributariesOrLoseExistingSubjectOwnership(
      boolean missingType) {
    Faction host = fixture.saved("host", "Alice");
    Faction subject = fixture.saved("subject", "Bob");
    Faction sibling = fixture.saved("sibling", "Cara");
    Faction recipient = fixture.saved("recipient", "Drew");
    fixture.subject(host, subject);
    fixture.subject(host, sibling);
    Relation incomplete =
        missingType ? new Relation(null, RelationLoader.getDefaultAttitude()) : null;
    host.getRelations().put(subject.getId(), incomplete);
    assertFalse(RelationManager.isTributaryOf(host, subject));
    assertEquals(List.of(sibling), RelationManager.getSubjects(host));
    RelationManager.transferSubject(subject, recipient);
    assertEquals(host.getId(), RelationManager.getOverlord(subject));
    assertSame(incomplete, host.getRelations().get(subject.getId()));
    assertEquals(host.getId(), RelationManager.getOverlord(sibling));
    assertFalse(recipient.getRelations().containsKey(subject.getId()));
  }
}
