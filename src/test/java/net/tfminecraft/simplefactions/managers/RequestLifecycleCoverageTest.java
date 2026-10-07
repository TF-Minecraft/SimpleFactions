package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.objects.request.*;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.berth.*;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

class RequestLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Player alice, bob;
  private Guild guild;
  private Map<Player, Request> requests, original;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    alice = fixture.player("Alice");
    bob = fixture.player("Bob");
    guild = fixture.saved("realm", "Alice").getOrCreateMainGuild();
    var field = RequestManager.class.getDeclaredField("requests");
    field.setAccessible(true);
    requests = (Map<Player, Request>) field.get(null);
    original = new HashMap<>(requests);
    requests.clear();
  }

  @AfterEach
  void close() {
    try {
      requests.clear();
      requests.putAll(original);
    } finally {
      fixture.close();
    }
  }

  private static void expire(Request request) throws Exception {
    var field = Request.class.getDeclaredField("time");
    field.setAccessible(true);
    field.setLong(request, 0);
  }

  @Test
  void acceptingAnExpiredVehicleOfferNeverInvokesTheTransfer() throws Exception {
    VehicleHandoverService service = mock(VehicleHandoverService.class);
    when(fixture.ui.plugin.getVehicleHandoverService()).thenReturn(service);
    var request =
        new VehicleHandoverRequest(
            guild,
            UUID.randomUUID().toString(),
            "wagon",
            alice.getUniqueId(),
            "Alice",
            bob.getUniqueId());
    expire(request);
    RequestManager.addRequest(alice, bob, request);
    RequestManager.accept(bob);
    verify(service, never()).acceptRequest(bob);
    verify(service).notifyExpired(request, bob);
    assertFalse(RequestManager.hasRequest(bob));
  }

  @Test
  void scheduledExpiryNotifiesTheCorrectVehicleServicesAndKeepsLiveRequests() throws Exception {
    VehicleHandoverService handovers = mock(VehicleHandoverService.class);
    FactionVehicleGiveService gifts = mock(FactionVehicleGiveService.class);
    when(fixture.ui.plugin.getVehicleHandoverService()).thenReturn(handovers);
    when(fixture.ui.plugin.getFactionVehicleGiveService()).thenReturn(gifts);
    var handover =
        new VehicleHandoverRequest(
            guild,
            UUID.randomUUID().toString(),
            "wagon",
            alice.getUniqueId(),
            "Alice",
            bob.getUniqueId());
    var gift =
        new VehicleGiveConsentRequest(
            guild,
            "realm",
            UUID.randomUUID().toString(),
            "wagon",
            alice.getUniqueId(),
            bob.getUniqueId(),
            "Bob");
    expire(handover);
    expire(gift);
    RequestManager.addRequest(alice, bob, handover);
    RequestManager.addRequest(bob, alice, gift);
    Player live = fixture.player("Carol");
    Request active = new Request(guild);
    RequestManager.addRequest(alice, live, active);
    RequestManager.start();
    fixture.ui.repeatingTasks.removeLast().run();
    verify(handovers).notifyExpired(handover, bob);
    verify(gifts).notifyExpired(gift, alice);
    assertFalse(RequestManager.hasRequest(alice));
    assertFalse(RequestManager.hasRequest(bob));
    assertSame(active, RequestManager.getRequest(live));
  }

  @Test
  void relationReloadRebindsCurrentTypesCancelsRemovedTypesAndKeepsOtherRequests() {
    RelationType old =
        new RelationType("ally", new org.bukkit.configuration.file.YamlConfiguration());
    RelationType current =
        new RelationType("ally", new org.bukkit.configuration.file.YamlConfiguration());
    RelationType removed =
        new RelationType("removed", new org.bukkit.configuration.file.YamlConfiguration());
    RelationLoader.types.clear();
    RelationLoader.types.add(current);
    RelationRequest first = new RelationRequest(guild, old, false);
    RequestManager.addRequest(alice, bob, first);
    RequestManager.addRequest(bob, alice, new RelationRequest(guild, removed, false));
    Player carol = fixture.player("Carol");
    RequestManager.addRequest(alice, carol, new RelationRequest(guild, null, false));
    Player dave = fixture.player("Dave");
    Request ordinary = new Request(guild);
    RequestManager.addRequest(alice, dave, ordinary);
    RequestManager.rebindRelationRequests();
    assertSame(current, first.getType());
    assertSame(first, RequestManager.getRequest(bob));
    assertFalse(RequestManager.hasRequest(alice));
    assertFalse(RequestManager.hasRequest(carol));
    assertSame(ordinary, RequestManager.getRequest(dave));
  }

  @Test
  void requestDispatchUsesTheCurrentReceiverAndClearsConsumedRequests() {
    List<Request> pending =
        List.of(
            new RelocateRequest(guild, 1, "City"),
            new ElevateRequest(guild),
            new MovementJoinRequest(guild, "Offline", "player", "realm", 0),
            new MovementLeaderTargetRequest(guild, "Alice", "missing", 0, "Bob"));
    for (Request request : pending) {
      RequestManager.addRequest(alice, bob, request);
      RequestManager.accept(bob);
      assertFalse(RequestManager.hasRequest(bob));
    }
    verify(bob).sendMessage("§cYou do not have a faction");
    verify(bob).sendMessage("§cYou are not the leader of a guild");
    verify(bob).sendMessage("§cRequest sender is not online");
    verify(bob).sendMessage("§cThat movement is no longer available.");
  }

  @Test
  void anInviteToADissolvedCompanyIsConsumedWithoutJoining() {
    var company =
        new net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany(
            guild, "Company", fixture.regiment("company_troops", false, 2, 0), 0);
    guild.setCompany(company);
    RequestManager.addRequest(alice, bob, new MercenaryInviteRequest(company));
    guild.setCompany(null);
    RequestManager.accept(bob);
    verify(bob).sendMessage("§cThat company invite has expired.");
    assertFalse(RequestManager.hasRequest(bob));
    assertNull(guild.getCompany());
  }
}
