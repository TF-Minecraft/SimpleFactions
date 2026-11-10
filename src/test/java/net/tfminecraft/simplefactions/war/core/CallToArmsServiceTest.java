package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Stored calls to arms on real factions and real war saves; only Paper is simulated. */
class CallToArmsServiceTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private Map<Integer, List<WarCommitment>> previousCommitments;
  private RelationType alliance;
  private int previousAllyPenalty;
  private Faction attacker;
  private Faction defender;
  private Faction target;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    Files.createDirectories(files.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    previousCommitments = new LinkedHashMap<>(commitments());
    commitments().clear();
    previousAllyPenalty = Cache.warDeclinedAllyStabilityPenalty;
    Cache.warDeclinedAllyStabilityPenalty = -30;
    alliance = fixture.relationType("ally", Map.of("link", "ally"));
    fixture.regiment("guard", false, 3, 0);
    attacker = fixture.saved("attacker", "Alice");
    defender = fixture.saved("defender", "Bob");
    target = fixture.saved("target", "Cara");
    ally(attacker, target);
  }

  @AfterEach
  void close() throws Exception {
    try {
      commitments().clear();
      commitments().putAll(previousCommitments);
      Cache.warDeclinedAllyStabilityPenalty = previousAllyPenalty;
      fixture.close();
    } finally {
      files.close();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<Integer, List<WarCommitment>> commitments() throws Exception {
    Field field = WarCommitmentService.class.getDeclaredField("commitmentsByWar");
    field.setAccessible(true);
    return (Map<Integer, List<WarCommitment>>) field.get(null);
  }

  private void ally(Faction first, Faction second) {
    first.setRelation(second, new Relation(alliance, RelationLoader.getDefaultAttitude()));
    second.setRelation(first, new Relation(alliance, RelationLoader.getDefaultAttitude()));
  }

  private War war(int id) {
    return WarManager.addWar(new War(id, attacker, defender));
  }

  private War saved(int id) {
    return new Database().loadWars().stream().filter(w -> w.getId() == id).findFirst().orElseThrow();
  }

  @Test
  void theDeclarationRemembersEveryAllyAsCallable() {
    War war = war(30);

    assertEquals(Set.of("target"), war.getCallableFactionIds());
    assertNull(CallToArmsService.notCallableReason(war, target));
    assertNull(CallToArmsService.notCallableReason(war, null));
    Faction late = fixture.saved("late", "Lena");
    assertEquals(CallToArmsService.NOT_ALLIED_AT_DECLARATION, CallToArmsService.notCallableReason(war, late));
    war.setCallableFactionIds(null);
    assertNull(CallToArmsService.notCallableReason(war, late), "Older wars call anyone");
    assertDoesNotThrow(() -> CallToArmsService.snapshotCallable(null));
  }

  @Test
  void aCallToAnOfflineLeaderIsStoredUntilAnsweredAndCannotBeSentTwice() {
    Player sender = fixture.player("Alice");
    War war = war(31);

    CallToArmsService.send(sender, attacker, target, war);

    verify(sender)
        .sendMessage("§aSent a call to arms to target§a. It stays open until their leader answers.");
    CallToArms call = war.getCallsToArms().get("target");
    assertEquals("target", call.targetFactionId());
    assertEquals("attacker", call.callerFactionId());
    assertFalse(call.isCoalitionCall());
    assertNotNull(call.sentAt());
    assertEquals(call, saved(31).getCallsToArms().get("target"));

    CallToArmsService.send(sender, attacker, target, war);

    verify(sender).sendMessage("§ctarget §calready has a call to arms in this war.");
    assertEquals(1, war.getCallsToArms().size());
  }

  @Test
  void anIneligibleTargetIsRefusedWithItsReason() {
    Player sender = fixture.player("Alice");
    War war = war(32);

    CallToArmsService.send(sender, attacker, defender, war);

    verify(sender).sendMessage(CallToArmsEligibility.ALREADY_IN_WAR);
    assertTrue(war.getCallsToArms().isEmpty());
  }

  @Test
  void theCalledLeaderIsToldHowToAnswer() {
    Player called = fixture.player("Cara");
    War war = war(33);

    CallToArmsService.send(fixture.player("Alice"), attacker, target, war);

    verify(called).sendMessage(startsWith("attacker §7calls on you to join their war against defender"));
    verify(called)
        .sendMessage(
            "§7Answer with §a/war call accept 33 §7or §c/war call decline 33§7."
                + " §e/war calls §7lists every open call.");
  }

  @Test
  void acceptingACurrentCallCommitsItsFactionAndPersistsItsParticipation() {
    Player sender = fixture.player("Alice");
    Player called = fixture.player("Cara");
    War war = war(36);
    CallToArmsService.send(sender, attacker, target, war);

    CallToArmsService.accept(called, war);

    assertTrue(war.isParticipating(target));
    assertTrue(war.getCallsToArms().isEmpty());
    assertFalse(WarManager.getCommitmentsForWar(36).isEmpty());
    verify(sender).sendMessage("target §aaccepted your call to arms: " + war.getName() + "§a.");
    verify(called).sendMessage("§aYour faction has joined the war: " + war.getName());
    War stored = saved(36);
    assertTrue(stored.isParticipating(target));
    assertTrue(stored.getCallsToArms().isEmpty());
  }

  @Test
  void anAllyCannotJoinWhileABattleOfTheWarIsBeingFought() {
    Player called = fixture.player("Cara");
    War war = war(39);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
    var battle = mock(net.tfminecraft.simplefactions.war.battle.engine.core.Battle.class);
    when(battle.hasStarted()).thenReturn(true);
    try (var battles = mockStatic(net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.class)) {
      battles.when(() -> net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByWarId(39))
          .thenReturn(battle);

      CallToArmsService.accept(called, war);
    }

    verify(called).sendMessage("§cA battle of this war is being fought. Answer the call once it ends.");
    assertFalse(war.isParticipating(target));
    assertTrue(war.getCallsToArms().containsKey("target"));
    // Between battles the same call can be accepted.
    CallToArmsService.accept(called, war);
    assertTrue(war.isParticipating(target));
  }

  @Test
  void acceptingAfterTheWarEndsDoesNotRecreateItsSaveOrCommitTroops() {
    Player called = fixture.player("Cara");
    War war = war(37);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
    WarManager.endWar(war);
    assertFalse(Files.exists(files.root.resolve("Wars/war_37.json")));

    CallToArmsService.accept(called, war);

    assertFalse(war.isParticipating(target));
    assertTrue(WarManager.getCommitmentsForWar(37).isEmpty());
    assertFalse(Files.exists(files.root.resolve("Wars/war_37.json")));
    verify(called).sendMessage("§cCould not join the war.");
  }

  @Test
  void aCallCannotRestoreAnOldWarObjectAfterTheManagerReloads() {
    Player called = fixture.player("Cara");
    War original = war(38);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, original);
    WarManager.start();
    War current = WarManager.getById(38);
    assertNotSame(original, current);

    CallToArmsService.accept(called, original);

    assertFalse(original.isParticipating(target));
    assertFalse(current.isParticipating(target));
    assertTrue(WarManager.getCommitmentsForWar(38).isEmpty());
    assertTrue(saved(38).getCallsToArms().containsKey("target"), "The live war's save is untouched");
    CallToArmsService.accept(called, current);
    assertTrue(current.isParticipating(target));
  }

  @Test
  void theCallBelongsToTheFactionSoItsNextLeaderAnswersIt() {
    Faction other = fixture.saved("other", "Elena");
    ally(attacker, other);
    Player called = fixture.player("Cara");
    War war = war(39);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
    target.getOrCreateMainGuild().addMember("Dave");
    target.setLeader("Dave");
    target.getOrCreateMainGuild().kick("Cara");
    other.getOrCreateMainGuild().addMember("Cara");
    other.setLeader("Cara");

    CallToArmsService.accept(called, war);

    verify(called).sendMessage("§cYour faction has no call to arms in that war.");
    assertFalse(war.isParticipating(other));
    assertFalse(war.isParticipating(target));

    CallToArmsService.accept(fixture.player("Dave"), war);

    assertTrue(war.isParticipating(target));
  }

  @Test
  void aLeaderWithoutAFactionCannotAnswer() {
    Player called = fixture.player("Cara");
    War war = war(40);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
    target.getOrCreateMainGuild().addMember("Dave");
    target.setLeader("Dave");

    CallToArmsService.accept(called, war);
    CallToArmsService.decline(called, war);

    verify(called, times(2)).sendMessage("§cOnly a faction leader can answer a call to arms.");
    assertTrue(war.getCallsToArms().containsKey("target"));
    Player dave = fixture.player("Dave");
    CallToArmsService.accept(dave, null);
    verify(dave).sendMessage("§cYour faction has no call to arms in that war.");
  }

  @Test
  void aCallIsRevalidatedWhenTheAllyBecomesBoundToTheEnemy() {
    Player called = fixture.player("Cara");
    War war = war(41);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
    fixture.subject(defender, target);

    CallToArmsService.accept(called, war);

    assertFalse(war.isParticipating(target));
    assertTrue(WarManager.getCommitmentsForWar(41).isEmpty());
    verify(called).sendMessage("§cCould not join the war.");
    assertTrue(saved(41).getCallsToArms().isEmpty(), "The spent call is saved as gone");
  }

  @Test
  void breakingTheAllianceOrLosingTheCallerInvalidatesTheCall() {
    Player called = fixture.player("Cara");
    War war = war(42);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, war);
    attacker.getDiplomacyHandler().removeRelation(target.getId());
    target.getDiplomacyHandler().removeRelation(attacker.getId());
    assertFalse(RelationManager.getAllies(attacker).contains(target));

    CallToArmsService.accept(called, war);

    assertFalse(war.isParticipating(target));
    war.getCallsToArms().put("target", new CallToArms("target", "disbanded", null, null));
    CallToArmsService.accept(called, war);
    verify(called, times(2)).sendMessage("§cCould not join the war.");
  }

  @Test
  void decliningCostsStabilityAndTellsTheCaller() {
    Player sender = fixture.player("Alice");
    Player called = fixture.player("Cara");
    War war = war(43);
    CallToArmsService.send(sender, attacker, target, war);

    CallToArmsService.decline(called, war);

    verify(called).sendMessage("§7You declined the call to arms.");
    verify(sender).sendMessage("target §cdeclined your call to arms: " + war.getName() + "§c.");
    StabilityModifier modifier = target.getGovernment().getByName("Declined Call to Arms");
    assertEquals(-30, modifier.getModifier());
    assertEquals(1, modifier.getDecay());
    assertTrue(war.getCallsToArms().isEmpty());
    assertTrue(saved(43).getCallsToArms().isEmpty());
    assertFalse(war.isParticipating(target));
  }

  @Test
  void decliningACallFromADisbandedCallerTellsNobody() {
    Player called = fixture.player("Cara");
    War war = war(44);
    war.getCallsToArms().put("target", new CallToArms("target", "disbanded", null, null));

    CallToArmsService.decline(called, war);

    verify(called).sendMessage("§7You declined the call to arms.");
    assertTrue(war.getCallsToArms().isEmpty());
  }

  @Test
  void openCallsAreListedAndASingleOneIsAnsweredByFactionAcceptOrDecline() {
    Player called = fixture.player("Cara");
    assertTrue(CallToArmsService.pendingFor(null).isEmpty());
    assertTrue(CallToArmsService.pendingFor(fixture.player("Nobody")).isEmpty());
    assertFalse(CallToArmsService.answerOnly(called, true));
    CallToArmsService.list(called);
    verify(called).sendMessage("§7Your faction has no open calls to arms.");

    War first = war(45);
    War second = war(46);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, first);
    second.getCallsToArms().put("target", new CallToArms("target", "disbanded", null, Instant.now()));
    assertEquals(
        List.of(first, second),
        CallToArmsService.pendingFor(called).stream().map(CallToArmsService.Pending::war).toList());

    assertTrue(CallToArmsService.answerOnly(called, true));

    verify(called).sendMessage("§6Open calls to arms:");
    verify(called)
        .sendMessage(
            "§e#45 §f" + first.getName() + "§7: called by attacker§7. §a/war call accept 45"
                + " §7or §c/war call decline 45");
    verify(called)
        .sendMessage(
            "§e#46 §f" + second.getName() + "§7: called by disbanded§7. §a/war call accept 46"
                + " §7or §c/war call decline 46");
    assertFalse(first.isParticipating(target));

    second.getCallsToArms().clear();
    assertTrue(CallToArmsService.answerOnly(called, false));
    verify(called).sendMessage("§7You declined the call to arms.");
    assertTrue(first.getCallsToArms().isEmpty());

    War third = war(47);
    CallToArmsService.send(fixture.player("Alice"), attacker, target, third);
    assertTrue(CallToArmsService.answerOnly(called, true));
    assertTrue(third.isParticipating(target));
    assertFalse(CallToArmsService.answerOnly(called, true));
    assertEquals(1, FactionManager.factions.stream().filter(f -> f == target).count());
  }
}
