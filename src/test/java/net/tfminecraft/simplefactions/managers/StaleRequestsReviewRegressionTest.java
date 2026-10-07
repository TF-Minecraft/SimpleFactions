package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.RelationRequest;
import net.tfminecraft.simplefactions.objects.request.RelocateRequest;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StaleRequestsReviewRegressionTest {
  private FactionDomainFixture domain;
  private PersistenceFilesFixture files;
  private Field requests;
  private Object originalRequests;
  private Faction origin;
  private Faction target;
  private Guild guild;
  private Player alice;
  private Player bob;

  @BeforeEach
  void setUp() throws Exception {
    files = new PersistenceFilesFixture();
    domain = new FactionDomainFixture();
    requests = RequestManager.class.getDeclaredField("requests");
    requests.setAccessible(true);
    originalRequests = requests.get(null);
    requests.set(null, new HashMap<>());
    domain.provincesEnabled(true);
    for (int id : List.of(1, 2, 3)) domain.provinceData.put(id, new Province(id, "PLAINS", 50));
    var originData = domain.data("origin", "Alice");
    originData.provinces.add(1);
    origin = domain.saved(originData);
    var targetData = domain.data("target", "Bob");
    targetData.provinces.add(2);
    target = domain.saved(targetData);
    alice = domain.player("Alice");
    bob = domain.player("Bob");
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      if (requests != null) requests.set(null, originalRequests);
    } finally {
      try {
        if (domain != null) domain.close();
      } finally {
        if (files != null) files.close();
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "relation,false",
    "trade,false",
    "treaty,false",
    "relation,true",
    "trade,true",
    "treaty,true"
  })
  void pendingDiplomacyCannotCreateRelationsToAnUnregisteredOrReplacedFaction(
      String kind, boolean replacement) {
    RelationType pact = requestPact(kind);
    FactionManager.deleteFaction(origin);
    Faction current = replacement ? domain.saved(origin.getId(), "Cara") : null;
    assertSame(current, FactionManager.getByString(origin.getId()));

    assertDoesNotThrow(() -> RequestManager.accept(bob));

    assertAll(
        () -> assertNotSame(pact, selected(kind, target, origin)),
        () -> assertNotSame(pact, selected(kind, origin, target)),
        () -> {
          if (current != null) assertNotSame(pact, selected(kind, current, target));
        },
        () -> assertFalse(RequestManager.hasRequest(bob)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"relation", "trade", "treaty"})
  void currentFactionRequestsStillCreateTheMutuallyAcceptedAgreement(String kind) {
    RelationType pact = requestPact(kind);
    RequestManager.accept(bob);
    assertSame(pact, selected(kind, origin, target));
    assertSame(pact, selected(kind, target, origin));
    assertFalse(RequestManager.hasRequest(bob));
  }

  private RelationType requestPact(String kind) {
    RelationType pact =
        domain.relationType(
            "pact",
            Map.of(
                "name",
                "Pact",
                "mutual",
                true,
                "trade-agreement",
                kind.equals("trade"),
                "treaty",
                kind.equals("treaty")));
    switch (kind) {
      case "trade" -> RelationManager.setTradeRelation(alice, pact, target, origin, true);
      case "treaty" -> RelationManager.setTreatyRelation(alice, pact, target, origin, true);
      default -> RelationManager.setRelation(alice, pact, target, origin, true);
    }
    RelationRequest pending =
        assertInstanceOf(RelationRequest.class, RequestManager.getRequest(bob));
    assertSame(origin, pending.getFaction());
    assertSame(pact, pending.getType());
    return pact;
  }

  private RelationType selected(String kind, Faction from, Faction to) {
    return switch (kind) {
      case "trade" -> from.getDiplomacyHandler().getTradeRelation(to.getId());
      case "treaty" -> from.getDiplomacyHandler().getTreatyRelation(to.getId());
      default -> from.getRelation(to.getId()).getType();
    };
  }

  private void requestRelocation() {
    guild = domain.guild(origin, "traders", "GuildLeader");
    Player sender = domain.player("GuildLeader");
    guild.setCapital(1);
    guild.getBank().setWealth(500.0);
    FactionManager.requestRelocation(sender, guild, target, 2, "Destination");
    assertSame(
        guild, assertInstanceOf(RelocateRequest.class, RequestManager.getRequest(bob)).getSender());
    assertEquals(100.0, guild.getRelocationCost(2));
  }

  @ParameterizedTest
  @ValueSource(strings = {"removed", "replaced", "different-host", "already-arrived", "new-leader"})
  void relocationConsentDoesNotFollowAChangedOrRemovedGuild(String change) {
    requestRelocation();
    switch (change) {
      case "removed" -> origin.getGuildHandler().removeGuild(guild.getId(), false, false);
      case "replaced" -> {
        origin.getGuildHandler().removeGuild(guild.getId(), false, false);
        domain.guild(origin, guild.getId(), "ReplacementLeader");
      }
      case "different-host" -> guild.relocate(domain.saved("third", "Cara"), -1);
      case "already-arrived" -> guild.relocate(target, -1);
      case "new-leader" -> guild.setLeader("Cara");
      default -> throw new AssertionError(change);
    }
    Faction currentHost = guild.getFaction();
    Guild registered = FactionManager.getGuildByString(guild.getId());
    int capital = guild.getCapital();
    Bank account = guild.getBank();
    double balance = account.getWealth();

    assertDoesNotThrow(() -> RequestManager.accept(bob));

    assertAll(
        () ->
            assertEquals(
                balance, account.getWealth(), "Rejected consent must not charge the old guild"),
        () -> assertSame(currentHost, guild.getFaction()),
        () -> assertEquals(capital, guild.getCapital()),
        () -> assertSame(registered, FactionManager.getGuildByString(guild.getId())),
        () -> assertNull(target.getSettlementHandler().getByProvince(2)));
  }

  @Test
  void aBaseGuildCannotBeChargedForARelocationThatDoesNothing() {
    guild = origin.getOrCreateMainGuild();
    guild.getBank().setWealth(500.0);
    FactionManager.requestRelocation(alice, guild, target, 2, "Destination");
    assertDoesNotThrow(() -> RequestManager.accept(bob));
    assertEquals(500.0, guild.getBank().getWealth());
    assertSame(origin, guild.getFaction());
    assertNull(target.getGuildHandler().getGuild(guild.getId()));
  }

  @Test
  void removingTheBankWhileConsentIsPendingDoesNotMoveTheGuildOrThrow() {
    requestRelocation();
    Bank originalAccount = guild.getBank();
    guild.setBank(null);
    assertAll(
        () -> assertDoesNotThrow(() -> RequestManager.accept(bob)),
        () -> assertSame(origin, guild.getFaction()),
        () -> assertSame(guild, origin.getGuildHandler().getGuild(guild.getId())),
        () -> assertNull(target.getGuildHandler().getGuild(guild.getId())),
        () -> assertEquals(1, guild.getCapital()),
        () -> assertEquals(500.0, originalAccount.getWealth()),
        () -> assertNull(target.getSettlementHandler().getByProvince(2)));
  }

  @Test
  void relocationConsentIsBoundToTheOriginalDestinationWhenItsLeaderChangesFaction() {
    requestRelocation();
    target.setLeader("Cara");
    Faction newRealm = domain.saved("new-realm", "Bob");
    assertSame(newRealm, FactionManager.getByLeader(bob.getName()));

    assertDoesNotThrow(() -> RequestManager.accept(bob));

    assertAll(
        () -> assertSame(origin, guild.getFaction()),
        () -> assertSame(guild, origin.getGuildHandler().getGuild(guild.getId())),
        () -> assertNull(newRealm.getGuildHandler().getGuild(guild.getId())),
        () -> assertNull(target.getGuildHandler().getGuild(guild.getId())),
        () -> assertEquals(500.0, guild.getBank().getWealth()),
        () -> assertEquals(1, guild.getCapital()),
        () -> assertFalse(RequestManager.hasRequest(bob)));
  }

  @Test
  void aCityNameTakenWhileRelocationIsPendingCannotChargeOrMoveTheGuild() {
    requestRelocation();
    target.addProvince(3);
    assertTrue(target.getSettlementHandler().found("Destination", 3, 0, 0).isSuccess());
    var existingCity = target.getSettlementHandler().getByProvince(3);

    assertDoesNotThrow(() -> RequestManager.accept(bob));

    assertAll(
        () -> assertSame(origin, guild.getFaction()),
        () -> assertSame(guild, origin.getGuildHandler().getGuild(guild.getId())),
        () -> assertNull(target.getGuildHandler().getGuild(guild.getId())),
        () -> assertEquals(500.0, guild.getBank().getWealth()),
        () -> assertEquals(1, guild.getCapital()),
        () -> assertSame(existingCity, target.getSettlementHandler().getByProvince(3)),
        () -> assertNull(target.getSettlementHandler().getByProvince(2)));
  }

  @Test
  void losingTheDestinationProvinceWhileConsentIsPendingCannotChargeOrMoveTheGuild() {
    requestRelocation();
    target.removeProvince(2, false);
    assertFalse(target.hasProvince(2));

    assertDoesNotThrow(() -> RequestManager.accept(bob));

    assertAll(
        () -> assertSame(origin, guild.getFaction()),
        () -> assertSame(guild, origin.getGuildHandler().getGuild(guild.getId())),
        () -> assertNull(target.getGuildHandler().getGuild(guild.getId())),
        () -> assertEquals(500.0, guild.getBank().getWealth()),
        () -> assertEquals(1, guild.getCapital()),
        () -> assertNull(target.getSettlementHandler().getByProvince(2)));
  }

  @Test
  void unchangedConsentMovesExactlyTheRequestedGuildAndChargesOnce() {
    requestRelocation();
    RequestManager.accept(bob);
    assertAll(
        () -> assertSame(target, guild.getFaction()),
        () -> assertSame(guild, target.getGuildHandler().getGuild(guild.getId())),
        () -> assertNull(origin.getGuildHandler().getGuild(guild.getId())),
        () -> assertEquals(400.0, guild.getBank().getWealth()),
        () -> assertEquals(2, guild.getCapital()),
        () -> assertNotNull(target.getSettlementHandler().getByProvince(2)),
        () -> assertFalse(RequestManager.hasRequest(bob)));
    RequestManager.accept(bob);
    assertEquals(400.0, guild.getBank().getWealth());
  }
}
