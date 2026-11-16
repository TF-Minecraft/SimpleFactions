package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.LongSupplier;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreatyNoticeTest {
  private static final long DAY = 86_400_000L;

  private FactionDomainFixture fixture;
  private LongSupplier originalClock;
  private Field tickField;
  private int originalTick;
  private long now = 1_000_000L;
  private Faction origin;
  private Faction target;
  private Player alice;
  private Player bob;
  private RelationType pact;
  private RelationType clear;

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    originalClock = RelationManager.clock;
    RelationManager.clock = () -> now;
    tickField = RelationManager.class.getDeclaredField("tick");
    tickField.setAccessible(true);
    originalTick = tickField.getInt(null);
    tickField.setInt(null, 0);
    origin = fixture.saved("origin", "Alice");
    target = fixture.saved("target", "Bob");
    alice = fixture.player("Alice");
    bob = fixture.player("Bob");
    pact = fixture.relationType("pact", Map.of("treaty", true, "mutual", true, "blocks-war", true));
    clear = fixture.relationType("none", Map.of("treaty", true, "clear", true));
    RelationManager.setTreatyRelationForced(pact, target, origin);
  }

  @AfterEach
  void close() throws Exception {
    try {
      RelationManager.clock = originalClock;
      if (tickField != null) tickField.setInt(null, originalTick);
    } finally {
      if (fixture != null) fixture.close();
    }
  }

  @Test
  void noticeLengthDefaultsToTwoWeeksForPactsThatBlockWar() {
    assertEquals(14 * DAY, pact.getCancelNoticeMillis());
    assertEquals(0, clear.getCancelNoticeMillis());
    assertEquals(
        DAY / 2,
        fixture.relationType("short", Map.of("treaty", true, "cancel-notice-days", 0.5))
            .getCancelNoticeMillis());
    assertEquals(
        0,
        fixture.relationType("bad", Map.of("blocks-war", true, "cancel-notice-days", -3))
            .getCancelNoticeMillis());
  }

  @Test
  void givingNoticeKeepsThePactUntilItRunsOut() {
    RelationManager.setTreatyRelation(alice, clear, target, origin, true);

    assertSame(pact, origin.getDiplomacyHandler().getTreatyRelation(target.getId()));
    assertTrue(RelationManager.hasNonAggressionPact(origin, target));
    assertEquals(now + 14 * DAY, RelationManager.treatyNoticeEnd(origin, target));
    assertEquals(now + 14 * DAY, RelationManager.treatyNoticeEnd(target, origin));
    assertEquals("14d 0h", RelationManager.treatyNoticeRemaining(target, origin));
    assertTrue(RelationManager.gaveTreatyNotice(origin, target));
    assertFalse(RelationManager.gaveTreatyNotice(target, origin));
    verify(alice).sendMessage(contains("We gave notice"));
    verify(bob).sendMessage(contains("gave notice to end"));

    // Neither side can restart the clock while it runs.
    RelationManager.setTreatyRelation(alice, clear, target, origin, true);
    RelationManager.setTreatyRelation(bob, clear, origin, target, true);
    RelationManager.setTreatyRelation(null, clear, target, origin, true);
    verify(alice).sendMessage(contains("already ends in"));
    verify(bob).sendMessage(contains("already ends in"));
    assertEquals(now + 14 * DAY, RelationManager.treatyNoticeEnd(origin, target));

    now += 14 * DAY - 1;
    RelationManager.expireTreatyNotices(now);
    assertTrue(RelationManager.hasNonAggressionPact(origin, target));

    now += 1;
    for (int i = 0; i < 60; i++) RelationManager.tick();
    assertNull(origin.getDiplomacyHandler().getTreatyRelation(target.getId()));
    assertNull(target.getDiplomacyHandler().getTreatyRelation(origin.getId()));
    assertNull(RelationManager.treatyNoticeEnd(origin, target));
    assertFalse(RelationManager.hasNonAggressionPact(origin, target));
    verify(alice).sendMessage(contains("has ended"));
    verify(bob).sendMessage(contains("has ended"));
  }

  @Test
  void pickingThePactAgainWithdrawsOurNotice() {
    RelationManager.setTreatyRelation(alice, clear, target, origin, true);
    RelationManager.setTreatyRelation(alice, pact, target, origin, true);

    assertNull(RelationManager.treatyNoticeEnd(origin, target));
    assertSame(pact, target.getDiplomacyHandler().getTreatyRelation(origin.getId()));
    verify(alice).sendMessage(contains("We withdrew our notice"));
    verify(bob).sendMessage(contains("withdrew its notice"));
  }

  @Test
  void reSigningThePactClearsTheOtherSidesNotice() {
    RelationManager.setTreatyRelation(bob, clear, origin, target, true);
    assertTrue(RelationManager.gaveTreatyNotice(target, origin));

    RelationManager.setTreatyRelation(alice, pact, target, origin, false);
    assertNull(RelationManager.treatyNoticeEnd(origin, target));
    assertSame(pact, origin.getDiplomacyHandler().getTreatyRelation(target.getId()));

    RelationManager.setTreatyRelation(bob, clear, origin, target, true);
    RelationManager.setTreatyRelationForced(pact, target, origin);
    assertNull(RelationManager.treatyNoticeEnd(origin, target));
  }

  @Test
  void staffClearEndsThePactAtOnce() {
    RelationManager.setTreatyRelation(alice, clear, target, origin, true);
    RelationManager.setTreatyRelationForced(clear, target, origin);
    assertNull(origin.getDiplomacyHandler().getTreatyRelation(target.getId()));
    assertNull(RelationManager.treatyNoticeEnd(origin, target));
  }

  @Test
  void expiryToleratesMissingPartnersAndTreaties() {
    origin.getDiplomacyHandler().setTreatyNoticeEnd("ghost", now);
    target.getDiplomacyHandler().removeTreatyRelation(origin.getId());
    origin.getDiplomacyHandler().removeTreatyRelation(target.getId());
    origin.getDiplomacyHandler().setTreatyNoticeEnd(target.getId(), now);

    RelationManager.expireTreatyNotices(now);

    assertTrue(origin.getDiplomacyHandler().getTreatyNotices().isEmpty());
    verify(alice, never()).sendMessage(contains("has ended"));
    assertNull(RelationManager.treatyNoticeEnd(null, target));
    assertNull(RelationManager.treatyNoticeEnd(origin, null));
  }

  @Test
  void offlineMembersAreSkipped() {
    when(bob.isOnline()).thenReturn(false);
    RelationManager.setTreatyRelation(alice, clear, target, origin, true);
    verify(bob, never()).sendMessage(anyString());
  }

  @Test
  void clearingWithoutAPactStillWorksAtOnce() {
    RelationManager.setTreatyRelationForced(clear, target, origin);
    RelationManager.setTreatyRelation(alice, clear, target, origin, true);
    verify(alice).sendMessage(contains("Cleared treaty"));
  }
}
