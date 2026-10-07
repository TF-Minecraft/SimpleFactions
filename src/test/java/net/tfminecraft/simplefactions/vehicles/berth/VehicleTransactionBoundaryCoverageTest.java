package net.tfminecraft.simplefactions.vehicles.berth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.VehicleHandoverRequest;
import net.tfminecraft.simplefactions.objects.request.VehicleTransferConsentRequest;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Outcome;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Status;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService.CanRegisterResult;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleReleaseSessionManager.Kind;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleReleaseSessionManager.VehicleReleaseSession;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager.VehicleTransferSession;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeConfirmations;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverMessages;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverSessionManager;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolLore;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService.CanAddResult;
import net.tfminecraft.simplefactions.vehicles.registry.FakeOwnedInventory;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VehicleTransactionBoundaryCoverageTest {
  @TempDir Path directory;

  @Test
  void sessionExpiryCancellationAndReplacementAreScopedToTheCorrectPlayer() {
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    VehicleReleaseSessionManager releases = new VehicleReleaseSessionManager();
    VehicleReleaseSession give = new VehicleReleaseSession(Kind.GIVE, "Bob", bob, Long.MAX_VALUE);
    releases.put(null, give);
    releases.put(alice, null);
    releases.clear(null);
    assertNull(releases.get(null));
    assertNull(releases.get(alice));
    releases.put(alice, new VehicleReleaseSession(Kind.TAKE, 0));
    releases.put(bob, give);
    assertNull(releases.get(alice));
    assertNull(releases.get(alice), "Expired entries are removed, not returned on a second click");
    assertSame(give, releases.get(bob));
    releases.put(alice, give);
    assertEquals("Bob", releases.get(alice).getTargetName());
    assertEquals(bob, releases.get(alice).getTargetUuid());
    assertEquals(Kind.GIVE, releases.get(alice).getKind());
    releases.clear(alice);
    assertNull(releases.get(alice));
    assertSame(give, releases.get(bob));

    VehicleTransferSessionManager transfers = new VehicleTransferSessionManager();
    VehicleTransferSession transfer = new VehicleTransferSession("harbor", Long.MAX_VALUE);
    transfers.put(null, transfer);
    transfers.put(alice, null);
    transfers.clear(null);
    assertNull(transfers.get(null));
    assertNull(transfers.get(alice));
    transfers.put(alice, new VehicleTransferSession(null, 0, true));
    assertNull(transfers.get(alice));
    transfers.put(alice, transfer);
    assertEquals("harbor", transfers.get(alice).getInstallationId());
    assertFalse(transfers.get(alice).isPool());
    assertEquals(Long.MAX_VALUE, transfers.get(alice).getExpiresAtMillis());
    transfers.clear(alice);
    assertNull(transfers.get(alice));

    VehicleHandoverSessionManager handovers = new VehicleHandoverSessionManager();
    var live = new VehicleHandoverSessionManager.Session("Bob", bob, Long.MAX_VALUE);
    handovers.put(null, live);
    handovers.put(alice, null);
    handovers.clear(null);
    assertNull(handovers.get(null));
    handovers.put(alice, new VehicleHandoverSessionManager.Session("Bob", bob, 0));
    handovers.put(bob, live);
    assertNull(handovers.get(alice));
    assertSame(live, handovers.get(bob));
    handovers.put(alice, live);
    handovers.clear(alice);
    assertNull(handovers.get(alice));
    assertSame(live, handovers.get(bob));
  }

  @Test
  void cancellingAFeeConfirmationCannotChargeThePlayerOnTheNextClick() {
    VehicleFeeConfirmations confirmations = new VehicleFeeConfirmations();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    confirmations.ask(alice, "ironclad", 40, 100);
    confirmations.ask(bob, "sloop", 8, 100);
    confirmations.clear(null);
    confirmations.clear(alice);
    assertFalse(confirmations.confirm(alice, "ironclad", 40, 101));
    assertTrue(confirmations.confirm(bob, "sloop", 8, 101));
    assertFalse(confirmations.confirm(bob, "sloop", 8, 101));
    confirmations.ask(alice, "ironclad", 40, 200);
    assertFalse(
        confirmations.confirm(alice, "ironclad", 40, 200 + VehicleFeeConfirmations.WINDOW_MILLIS));
    confirmations.ask(alice, "ironclad", 40, 300);
    assertTrue(confirmations.confirm(alice, "ironclad", 40, 301));
  }

  @Test
  void quotedFeesAndRefundsConserveMoneyAndRecordOnlyCompletedTransfers() throws Exception {
    try (Rig rig = new Rig()) {
      rig.faction.addMember("Owner");
      rig.faction.getVehicleFeeHandler().applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(0, 5));
      rig.faction.getVehicleFeeHandler().setRate(FeeKind.REGISTRATION_FEE, null, 2);
      UUID ownerId = rig.owner.getUniqueId();
      rig.bank.balances.put(ownerId, 100.0);
      rig.faction.getBank().setWealth(0.0);
      var quote = VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, "Owner", "ironclad");
      assertNotNull(quote);
      assertEquals(40, quote.amount());
      assertTrue(VehicleFeeService.collect(ownerId, quote));
      assertEquals(60, VehicleFeeService.bankBalance(ownerId));
      assertEquals(40, rig.faction.getBank().getWealth());
      assertEquals(40, rig.faction.getOrCreateMainGuild().getLedger().getVehicleFeeIncome());
      assertEquals(-40, rig.economy.getLedger(ownerId).getAmount(PlayerCashflow.VEHICLE_FEES));
      assertEquals(ownerId, VehicleFeeService.resolve("Owner"));

      rig.bank.rejectDeposits = true;
      assertEquals(0, VehicleFeeService.refund(ownerId, "home", FeeKind.REGISTRATION_FEE, 40));
      assertEquals(40, rig.faction.getBank().getWealth());
      assertEquals(60, rig.bank.getBankBalance(ownerId));
      assertEquals(-40, rig.economy.getLedger(ownerId).getNetDaily());
      rig.bank.rejectDeposits = false;
      assertEquals(40, VehicleFeeService.refund(ownerId, "home", FeeKind.REGISTRATION_FEE, 60));
      assertEquals(0, rig.faction.getBank().getWealth());
      assertEquals(100, rig.bank.getBankBalance(ownerId));
      assertEquals(0, rig.faction.getOrCreateMainGuild().getLedger().getVehicleFeeIncome());
      assertEquals(0, rig.economy.getLedger(ownerId).getNetDaily());
      assertEquals(0, VehicleFeeService.refund(ownerId, "home", FeeKind.REGISTRATION_FEE, 20));
      assertEquals(0, VehicleFeeService.refund(ownerId, "missing", FeeKind.REGISTRATION_FEE, 20));
      assertEquals(0, VehicleFeeService.refund(ownerId, null, FeeKind.REGISTRATION_FEE, 20));
      assertEquals(0, VehicleFeeService.refund(null, "home", FeeKind.REGISTRATION_FEE, 20));
      assertEquals(0, VehicleFeeService.refund(ownerId, "home", FeeKind.REGISTRATION_FEE, 0));
      assertEquals(0, VehicleFeeService.bankBalance(null));
      assertFalse(VehicleFeeService.collect(null, quote));
      assertFalse(VehicleFeeService.collect(ownerId, null));
      assertNull(VehicleFeeService.quote(null, "Owner", "ironclad"));
      assertNull(VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, null, "ironclad"));
      assertNull(VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, " ", "ironclad"));
      assertNull(VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, "Owner", null));
    }
  }

  @Test
  void factionFeeCreditCanCompleteWithoutTheOptionalPlayerLedgerPlugin() throws Exception {
    try (Rig rig = new Rig()) {
      UUID owner = rig.owner.getUniqueId();
      rig.bank.balances.put(owner, 20.0);
      double initial = rig.faction.getBank().getWealth();
      SimpleFactions previous = SimpleFactions.plugin;
      try {
        SimpleFactions.plugin = null;
        assertTrue(
            VehicleFeeService.collect(
                owner,
                new VehicleFeeService.Quote(FeeKind.TRANSFER_FEE, "ironclad", rig.faction, 1, 20)));
      } finally {
        SimpleFactions.plugin = previous;
      }
      assertEquals(initial + 20, rig.faction.getBank().getWealth());
      assertEquals(0, rig.bank.getBankBalance(owner));
      assertEquals(0, rig.economy.getLedger(owner).getNetDaily());
    }
  }

  @Test
  void poolMessagesUseActualRosterCapacityAndDistinguishStorageConflicts() throws Exception {
    try (Rig rig = new Rig()) {
      var pools =
          new FactionVehiclePoolService(
              rig.registry, new InstallationVehicleOwnerSync(rig.registry));
      ActiveVehicle artillery = rig.vehicle("third", "field_artillery");
      rig.registry.register(rig.pool("first", "field_artillery"));
      rig.registry.register(rig.pool("second", "field_artillery"));
      var result = pools.canAdd(rig.faction, artillery);
      assertEquals(CanAddResult.NO_ARTILLERY_CAPACITY, result);
      assertEquals(
          "§cThe faction pool has no free artillery slot (2/2 used).",
          VehicleTransferMessages.forPoolResult(result, "field_artillery", rig.faction));
      assertEquals(
          "§cThe faction pool has no free artillery slot (0/0 used).",
          VehicleTransferMessages.forPoolResult(result, "field_artillery", null));
      assertEquals(
          "§cThis vehicle is already in a faction vehicle pool.",
          VehicleTransferMessages.forPoolResult(
              pools.canAdd(rig.faction, rig.vehicle("first", "field_artillery")),
              "field_artillery",
              rig.faction));
      rig.registry.register(
          new PlayerVehicleRecord(
              rig.owner.getUniqueId(),
              "berthed",
              "ironclad",
              OwnershipMode.INSTALLATION,
              "harbor",
              "home"));
      assertEquals(
          "§cThis vehicle is already berthed at an installation.",
          VehicleTransferMessages.forPoolResult(
              pools.canAdd(rig.faction, rig.vehicle("berthed", "ironclad")),
              "ironclad",
              rig.faction));
      assertEquals(
          "§cThis vehicle belongs at an installation, not in the faction pool.",
          VehicleTransferMessages.forPoolResult(
              pools.canAdd(rig.faction, rig.vehicle("at-sea", "ironclad")),
              "ironclad",
              rig.faction));
      assertEquals(
          "§cThis vehicle is not registered for faction upkeep.",
          VehicleTransferMessages.forPoolResult(
              pools.canAdd(rig.faction, rig.vehicle("unknown", "unknown")),
              "unknown",
              rig.faction));
      assertTrue(rig.registry.unregister("first"));
      assertEquals(
          "§aVehicle added to the faction vehicle pool.",
          VehicleTransferMessages.forPoolResult(
              pools.canAdd(rig.faction, artillery), "field_artillery", rig.faction));
      assertNull(VehicleTransferMessages.forPoolResult(null, "ironclad", rig.faction));
      assertNull(VehicleTransferMessages.forResult(null, rig.port, null, "ironclad"));
      assertNull(VehicleTransferMessages.forResult(CanRegisterResult.OK, null, null, "ironclad"));
      assertEquals(
          "§aVehicle berthed at Harbor.",
          VehicleTransferMessages.forResult(CanRegisterResult.OK, rig.port, null, "ironclad"));
      assertEquals(
          "§cThis vehicle is already in a faction vehicle pool.",
          VehicleTransferMessages.forResult(
              CanRegisterResult.ALREADY_IN_POOL, rig.port, null, "ironclad"));
      assertEquals(
          "§cThis vehicle is not registered for faction upkeep.",
          VehicleTransferMessages.forResult(
              CanRegisterResult.UNKNOWN_TYPE, rig.port, null, "unknown"));
    }
  }

  @Test
  void releaseErrorsGiveUsefulRecipientsAndConfiguredLimitsWhenOptionalMetadataIsAbsent()
      throws Exception {
    try (Rig rig = new Rig()) {
      assertEquals(
          "§cThat vehicle is not in your faction pool or berthed at your installation.",
          FactionVehicleReleaseMessages.forTake(null));
      assertEquals(
          "§cYou need to be a faction leader to take or give faction vehicles.",
          FactionVehicleReleaseMessages.forGive(new Outcome(Status.NOT_LEADER, null, null), "Bob"));
      assertEquals(
          "§cBob has reached their personal vehicle limit (3).",
          FactionVehicleReleaseMessages.forGive(
              new Outcome(Status.NO_PERSONAL_ROOM, null, "ironclad"), "Bob"));
      assertEquals(
          "§cThis vehicle type is not registered for faction upkeep.",
          FactionVehicleReleaseMessages.forGive(
              new Outcome(Status.UNKNOWN_TYPE, null, null), "Bob"));
      assertEquals(
          "§cBob already has the maximum number of that vehicles (1).",
          FactionVehicleReleaseMessages.forGive(
              new Outcome(Status.NO_PERSONAL_ROOM, CanBuildResult.PER_TYPE_LIMIT, null), "Bob"));
      assertEquals(
          "§cYou have reached your personal vehicle limit (3).",
          FactionVehicleReleaseMessages.forTake(
              new Outcome(Status.NO_PERSONAL_ROOM, null, "ironclad")));
      assertEquals(
          CanBuildResult.UNKNOWN_TYPE,
          VehicleSlotGuard.checkCanBuild((String) null, "ironclad", rig.registry));
      assertEquals(
          CanBuildResult.UNKNOWN_TYPE,
          VehicleSlotGuard.checkCanBuild(" ", "ironclad", rig.registry));
      assertEquals(
          CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild("Owner", "ironclad", null));
      CanBuildResult free = VehicleSlotGuard.checkCanBuild("Recipient", "ironclad", rig.registry);
      assertEquals(CanBuildResult.OK, free);
      assertNull(
          FactionVehicleReleaseMessages.personalRoom(free, "ironclad", "Recipient"),
          "A successful slot check needs no error message");
      assertEquals(
          "§cRecipient has no room for another personal vehicle.",
          FactionVehicleReleaseMessages.personalRoom(null, "ironclad", "Recipient"),
          "An unspecified external slot failure should still give a useful explanation");
      assertNull(VehicleConstructionMessages.forResult(null, "ironclad"));
      assertEquals(
          CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild("Owner", "", rig.registry));
      assertEquals(
          CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild("Owner", null, rig.registry));
    }
  }

  @Test
  void failedExternalOwnerAssignmentRestoresTheFactionRecordAndReportsTheRetry() throws Exception {
    try (Rig rig = new Rig()) {
      PlayerVehicleRecord original = rig.pool("gift", "field_artillery");
      rig.registry.register(original);
      AtomicInteger writes = new AtomicInteger();
      AtomicInteger assignments = new AtomicInteger();
      FactionVehicleReleaseService service =
          new FactionVehicleReleaseService(
              rig.registry,
              (id, name) -> {
                assertEquals("gift", id);
                assertEquals("Recipient", name);
                assignments.incrementAndGet();
                return false;
              },
              () -> {
                writes.incrementAndGet();
                return true;
              });
      Outcome outcome = service.give(rig.faction, "Leader", "Recipient", "gift");
      assertEquals(Status.OWNERSHIP_UNAVAILABLE, outcome.status());
      assertSame(original, rig.registry.getByVehicleUuid("gift").orElseThrow());
      assertEquals(2, writes.get());
      assertEquals(1, assignments.get());
      assertEquals(
          "§cCould not assign that vehicle. Try again while it is spawned.",
          FactionVehicleReleaseMessages.forGive(outcome, "Recipient"));
    }
  }

  @Test
  void poolLoreCapsTheVisibleRosterButReportsHiddenAndUnpaidVehicles() throws Exception {
    try (Rig rig = new Rig()) {
      List<PlayerVehicleRecord> roster = new ArrayList<>();
      for (int i = 0; i < 12; i++) roster.add(rig.pool("vehicle-" + i, "field_artillery"));
      List<String> lore =
          FactionVehiclePoolLore.lines(roster, 12, Set.of("vehicle-0", "vehicle-11"));
      assertEquals("§7Artillery: §e12/12", lore.getFirst());
      assertTrue(lore.contains("§cUnpaid: 2 of 12"));
      assertEquals(10, lore.stream().filter(line -> line.startsWith("§7- §f")).count());
      assertTrue(
          lore.stream().anyMatch(line -> line.contains("#vehicl") && line.endsWith("§cunpaid")));
      assertEquals("§7And 2 more...", lore.getLast());
      PlayerVehicleRecord unidentified =
          new PlayerVehicleRecord(
              rig.owner.getUniqueId(), null, "field_artillery", OwnershipMode.POOL, null, "home");
      List<String> unidentifiedLore = FactionVehiclePoolLore.lines(List.of(unidentified), 2, null);
      assertEquals("§7Artillery: §e1/2", unidentifiedLore.getFirst());
      assertTrue(
          unidentifiedLore.getLast().contains("field_artillery §8#? §8"),
          "The public formatter can explain externally supplied records with no identifier");
      assertNull(unidentified.getVehicleUuid(), "Rendering must not invent an ownership identity");

      assertEquals(
          List.of(
              "§7Artillery: §e0/0",
              "§7Leader: §e/faction vehicle take §7or §egive <player>",
              "§7No vehicles in the pool."),
          FactionVehiclePoolLore.lines(null, -1, null));
    }
  }

  @Test
  void onlyTheFactionOwnStartedCampaignBattleBlocksItsVehicleTransactions() throws Exception {
    try (Rig rig = new Rig()) {
      Faction north = rig.domain.saved("north", "North");
      Faction south = rig.domain.saved("south", "South");
      War elsewhere = new War(8101, north, south);
      WarManager.get().add(elsewhere);
      Battle foreignBattle = new Battle("foreign-battle");
      foreignBattle.setWarId(elsewhere.getId());
      foreignBattle.setStarted(true);
      BattleManager.addBattle(foreignBattle);
      assertTrue(FactionCampaignBattleLock.blocks(north));
      assertFalse(
          FactionCampaignBattleLock.blocks(rig.faction),
          "Unrelated wars must not lock this faction");

      War ownWar = new War(8102, rig.faction, south);
      WarManager.get().add(ownWar);
      Battle ownBattle = new Battle("own-battle");
      ownBattle.setWarId(ownWar.getId());
      BattleManager.addBattle(ownBattle);
      assertFalse(
          FactionCampaignBattleLock.blocks(rig.faction),
          "Preparation must still permit vehicle transactions");
      ownBattle.setStarted(true);
      assertTrue(
          FactionCampaignBattleLock.blocks(rig.faction),
          "A later matching war must still be checked");
      String savedLeaderId = north.getId();
      north.setId(null);
      try {
        assertTrue(
            FactionCampaignBattleLock.blocks(rig.faction),
            "An invalid saved leader identity in an earlier war cannot hide a real campaign lock");
        ownBattle.setStarted(false);
        assertFalse(
            FactionCampaignBattleLock.blocks(rig.faction),
            "An unrelated malformed identity is tolerated without imposing another faction's lock");
      } finally {
        north.setId(savedLeaderId);
      }
      assertFalse(FactionCampaignBattleLock.blocks(rig.faction));
    }
  }

  @Test
  void expiredConsentAndHandoverRequestsCannotMoveOwnershipOrChargeMoney() throws Exception {
    try (Rig rig = new Rig()) {
      Path config = directory.resolve("installations.yml");
      Files.writeString(
          config,
          Files.readString(Path.of("src/main/resources/installations.yml"))
              .replace(
                  "transfer-request-timeout-seconds: 60", "transfer-request-timeout-seconds: 1"));
      InstallationConfigLoader.load(config.toFile());
      var transfer =
          new VehicleTransferConsentRequest(
              rig.faction.getOrCreateMainGuild(),
              "harbor",
              "Harbor",
              "v1",
              "ironclad",
              rig.owner.getUniqueId(),
              rig.leader.getUniqueId());
      var handover =
          new VehicleHandoverRequest(
              rig.faction.getOrCreateMainGuild(),
              "v2",
              "ironclad",
              rig.leader.getUniqueId(),
              "Leader",
              rig.recipient.getUniqueId());
      RequestManager.addRequest(rig.leader, rig.owner, transfer);
      RequestManager.addRequest(rig.leader, rig.recipient, handover);
      PlayerVehicleRecord original =
          new PlayerVehicleRecord(
              rig.owner.getUniqueId(), "v1", "ironclad", OwnershipMode.PERSONAL, null);
      rig.registry.register(original);
      var sessions = new VehicleTransferSessionManager();
      var berths =
          new InstallationVehicleService(
              rig.registry, new InstallationVehicleOwnerSync(rig.registry));
      var consent = new VehicleTransferConsentService(berths, rig.registry, sessions);
      var store = new VehicleFeeStore();
      AtomicInteger saves = new AtomicInteger();
      var service = new VehicleHandoverService(rig.registry, store, saves::incrementAndGet);
      double before = rig.faction.getBank().getWealth();
      Thread.sleep(1100);
      assertTrue(transfer.timedOut());
      assertTrue(handover.timedOut());
      consent.acceptRequest(rig.owner);
      service.acceptRequest(rig.recipient);
      assertSame(original, rig.registry.getByVehicleUuid("v1").orElseThrow());
      assertEquals(before, rig.faction.getBank().getWealth());
      assertNull(store.getLastOwner("v2"));
      assertEquals(0, saves.get());
      verify(rig.owner).sendMessage(VehicleTransferMessages.consentExpired());
      verify(rig.recipient).sendMessage(VehicleHandoverMessages.expired());
      verify(rig.domain.ui.plugin, never()).saveVehicleRegistry();
    }
  }

  private static final class Rig implements AutoCloseable {
    final FactionDomainFixture domain = new FactionDomainFixture();
    final Map<Field, Object> globals = new LinkedHashMap<>();
    final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    final PlayerEconomyManager economy = new PlayerEconomyManager();
    final MemoryBank bank = new MemoryBank();
    final Faction faction;
    final Player leader;
    final Player owner;
    final Player recipient;
    final Installation port;

    Rig() throws Exception {
      try {
        for (String name :
            List.of(
                "personalSlotLimit",
                "defaultPerPerson",
                "maintenanceHourlyDamagePercent",
                "maintenanceMinHealthPercent",
                "maintenanceIntervalTicks",
                "categoryIds",
                "typesByCategory",
                "categoryByVehicleTypeId",
                "categoryDisplayNames",
                "feeExcludedCategories")) snapshot(VehiclesConfigLoader.class, name);
        for (String name :
            List.of("byKind", "consentProximityBlocks", "transferRequestTimeoutSeconds"))
          snapshot(InstallationConfigLoader.class, name);
        snapshot(RequestManager.class, "requests").set(null, new HashMap<>());
        snapshot(BattleManager.class, "battles").set(null, new ArrayList<>());
        snapshot(VehicleOwnershipQueries.class, "source");
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory());
        snapshot(VehicleFeeService.class, "factionLookup");
        snapshot(VehicleFeeService.class, "playerBank");
        VehicleFeeService.setForTests(null, bank);
        VehiclesConfigLoader.load(Path.of("src/main/resources/vehicles.yml").toFile());
        InstallationConfigLoader.load(Path.of("src/main/resources/installations.yml").toFile());
        domain.regiment("artillery", false, 2, 4);
        faction = domain.saved("home", "Leader");
        leader = domain.player("Leader");
        owner = domain.player("Owner");
        recipient = domain.player("Recipient");
        bank.ids.put("Owner", owner.getUniqueId());
        when(Bukkit.getPlayer(any(UUID.class)))
            .thenAnswer(
                call ->
                    domain.online.values().stream()
                        .filter(p -> p.getUniqueId().equals(call.getArgument(0)))
                        .findFirst()
                        .orElse(null));
        port = new Installation("harbor", "Harbor", InstallationKind.PORT, 42, 0, 0, 0);
        faction.getInstallationHandler().acceptTransferred(port);
        Field registryField = SimpleFactions.class.getDeclaredField("vehicleRegistry");
        registryField.setAccessible(true);
        registryField.set(domain.ui.plugin, registry);
        Field economyField = SimpleFactions.class.getDeclaredField("playerEconomyManager");
        economyField.setAccessible(true);
        economyField.set(domain.ui.plugin, economy);
      } catch (Throwable failure) {
        try {
          close();
        } catch (Throwable cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    }

    PlayerVehicleRecord pool(String id, String type) {
      return new PlayerVehicleRecord(
          owner.getUniqueId(), id, type, OwnershipMode.POOL, null, faction.getId());
    }

    ActiveVehicle vehicle(String id, String type) {
      ActiveVehicle vehicle = mock(ActiveVehicle.class);
      when(vehicle.getUUID()).thenReturn(id);
      when(vehicle.getId()).thenReturn(type);
      OwnerData data = new OwnerData();
      data.setOwner("player_Owner");
      when(vehicle.getOwnerData()).thenReturn(data);
      return vehicle;
    }

    Field snapshot(Class<?> type, String name) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      Object value = field.get(null);
      globals.put(
          field,
          Modifier.isFinal(field.getModifiers()) ? new LinkedHashMap<>((Map<?, ?>) value) : value);
      return field;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void close() throws Exception {
      try {
        for (var entry : globals.entrySet()) {
          Field field = entry.getKey();
          if (Modifier.isFinal(field.getModifiers())) {
            Map current = (Map) field.get(null);
            current.clear();
            current.putAll((Map) entry.getValue());
          } else field.set(null, entry.getValue());
        }
      } finally {
        domain.close();
      }
    }
  }

  private static final class MemoryBank implements PlayerBank {
    final Map<UUID, Double> balances = new HashMap<>();
    final Map<String, UUID> ids = new HashMap<>();
    boolean rejectDeposits;

    @Override
    public double getBankBalance(UUID id) {
      return balances.getOrDefault(id, 0.0);
    }

    @Override
    public boolean withdrawFromBank(UUID id, double amount) {
      if (getBankBalance(id) < amount) return false;
      balances.put(id, getBankBalance(id) - amount);
      return true;
    }

    @Override
    public boolean depositToBank(UUID id, double amount) {
      if (rejectDeposits) return false;
      balances.put(id, getBankBalance(id) + amount);
      return true;
    }

    @Override
    public UUID resolve(String name) {
      return ids.get(name);
    }
  }
}
