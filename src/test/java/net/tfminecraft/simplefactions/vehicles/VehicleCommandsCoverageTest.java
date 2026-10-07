package net.tfminecraft.simplefactions.vehicles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import net.tfminecraft.simplefactions.installation.*;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.*;
import net.tfminecraft.simplefactions.vehicles.berth.*;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverSessionManager;
import net.tfminecraft.simplefactions.vehicles.maintenance.*;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Command transitions use the real session stores and current faction/installation state. */
class VehicleCommandsCoverageTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private Faction faction;
  private Player leader, other;
  private Installation port;
  private final VehicleTransferSessionManager transfer = new VehicleTransferSessionManager();
  private final VehicleReleaseSessionManager release = new VehicleReleaseSessionManager();
  private final VehicleHandoverSessionManager handover = new VehicleHandoverSessionManager();
  private final VehicleMaintenancePaySessionManager maintenance =
      new VehicleMaintenancePaySessionManager();

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    fixture = new FactionDomainFixture();
    faction = fixture.saved("home", "Leader");
    faction.getOrCreateMainGuild();
    leader = fixture.player("Leader");
    other = fixture.player("Other");
    port = new Installation("harbor", "Harbor", InstallationKind.PORT, 1, 0, 0, 0);
    faction.getInstallationHandler().acceptTransferred(port);
    when(fixture.ui.plugin.getVehicleTransferSessionManager()).thenReturn(transfer);
    when(fixture.ui.plugin.getVehicleReleaseSessionManager()).thenReturn(release);
    when(fixture.ui.plugin.getVehicleHandoverSessionManager()).thenReturn(handover);
    when(fixture.ui.plugin.getVehicleMaintenancePaySessionManager()).thenReturn(maintenance);
  }

  @AfterEach
  void close() throws Exception {
    try {
      fixture.close();
    } finally {
      files.close();
    }
  }

  private void seed(Player player) {
    UUID id = player.getUniqueId();
    long expiry = System.currentTimeMillis() + 60_000;
    transfer.put(id, new VehicleTransferSessionManager.VehicleTransferSession("old", expiry));
    release.put(
        id,
        new VehicleReleaseSessionManager.VehicleReleaseSession(
            VehicleReleaseSessionManager.Kind.TAKE, expiry));
    handover.put(id, new VehicleHandoverSessionManager.Session("Old", UUID.randomUUID(), expiry));
    maintenance.put(
        id, new VehicleMaintenancePaySessionManager.VehicleMaintenancePaySession(expiry));
  }

  private void only(Player player, String kind) {
    UUID id = player.getUniqueId();
    assertEquals(kind.equals("transfer"), transfer.get(id) != null);
    assertEquals(kind.equals("release"), release.get(id) != null);
    assertEquals(kind.equals("handover"), handover.get(id) != null);
    assertEquals(kind.equals("maintenance"), maintenance.get(id) != null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"transfer", "take", "give", "pouch"})
  void factionActionsRequireTheCurrentLeaderAndPreserveDeniedSessions(String action) {
    seed(other);
    var pending = transfer.get(other.getUniqueId());
    switch (action) {
      case "transfer" -> VehicleFactionCommands.armTransfer(other, "harbor");
      case "take" -> VehicleFactionCommands.armTake(other);
      case "give" -> VehicleFactionCommands.armGive(other, "Leader");
      case "pouch" -> VehicleFactionCommands.armMaintenancePay(other);
    }
    assertSame(pending, transfer.get(other.getUniqueId()));
    assertNotNull(handover.get(other.getUniqueId()));
    verify(other).sendMessage(anyString());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "missing"})
  void invalidInstallationDoesNotReplaceAnExistingAction(String installation) {
    seed(leader);
    var pending = transfer.get(leader.getUniqueId());
    VehicleFactionCommands.armTransfer(leader, installation);
    assertSame(pending, transfer.get(leader.getUniqueId()));
    assertNotNull(release.get(leader.getUniqueId()));
    verify(leader).sendMessage(anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"harbor", "PoOl"})
  void armingTransferClearsOtherActionsOnlyForThatPlayer(String destination) {
    seed(leader);
    seed(other);
    long before = System.currentTimeMillis();
    VehicleFactionCommands.armTransfer(leader, destination);
    only(leader, "transfer");
    var state = transfer.get(leader.getUniqueId());
    assertTrue(state.getExpiresAtMillis() > before);
    assertEquals(destination.equals("PoOl"), state.isPool());
    assertEquals(destination.equals("PoOl") ? null : "harbor", state.getInstallationId());
    assertNotNull(handover.get(other.getUniqueId()));
  }

  @Test
  void raidEmbargoRejectsOrdinaryTransferAndAllowsStaffOverride() {
    Faction enemy = fixture.saved("enemy", "Enemy");
    War war = WarManager.addWar(new War(11, faction, enemy));
    CampaignRaidService.setRepairLockUntil(war, port.getId(), Instant.now().plusSeconds(3600));
    seed(leader);
    var pending = transfer.get(leader.getUniqueId());
    VehicleFactionCommands.armTransfer(leader, "harbor");
    assertSame(pending, transfer.get(leader.getUniqueId()));
    verify(leader).sendMessage(VehicleInstallationLockService.BERTH_BLOCKED);
    when(leader.hasPermission("simplefactions.admin")).thenReturn(true);
    VehicleFactionCommands.armTransfer(leader, "harbor");
    only(leader, "transfer");
    assertEquals("harbor", transfer.get(leader.getUniqueId()).getInstallationId());
  }

  @Test
  void takeReplacesOtherActionsAndRecordsTheReleaseKind() {
    seed(leader);
    VehicleFactionCommands.armTake(leader);
    only(leader, "release");
    assertEquals(
        VehicleReleaseSessionManager.Kind.TAKE, release.get(leader.getUniqueId()).getKind());
    assertNull(release.get(leader.getUniqueId()).getTargetUuid());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givingAndPersonalHandoverBindTheOnlineRecipientIdentity(boolean personal) {
    seed(leader);
    if (personal) VehicleFactionCommands.armHandover(leader, "Other");
    else VehicleFactionCommands.armGive(leader, "Other");
    only(leader, personal ? "handover" : "release");
    if (personal) {
      assertEquals(other.getUniqueId(), handover.get(leader.getUniqueId()).recipientUuid());
      assertEquals("Other", handover.get(leader.getUniqueId()).recipientName());
    } else {
      var state = release.get(leader.getUniqueId());
      assertEquals(VehicleReleaseSessionManager.Kind.GIVE, state.getKind());
      assertEquals(other.getUniqueId(), state.getTargetUuid());
      assertEquals("Other", state.getTargetName());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "empty", "blank", "self", "missing", "offline"})
  void invalidRecipientNeverReplacesAnExistingAction(String state) {
    seed(leader);
    var pending = transfer.get(leader.getUniqueId());
    String target =
        switch (state) {
          case "null" -> null;
          case "empty" -> "";
          case "blank" -> " ";
          case "self" -> "LEADER";
          case "missing" -> "Missing";
          default -> "Other";
        };
    if (state.equals("offline")) when(other.isOnline()).thenReturn(false);
    VehicleFactionCommands.armGive(leader, target);
    assertSame(pending, transfer.get(leader.getUniqueId()));
    VehicleFactionCommands.armHandover(leader, target);
    assertSame(pending, transfer.get(leader.getUniqueId()));
    verify(leader, atLeastOnce()).sendMessage(anyString());
  }

  @Test
  void personalHandoverAndBankPaymentRemainAvailableWithoutFactionLeadership() {
    VehicleFactionCommands.armHandover(other, "Leader");
    only(other, "handover");
    VehicleFactionCommands.armMaintenancePay(other, PaymentSource.BANK);
    only(other, "maintenance");
    assertEquals(PaymentSource.BANK, maintenance.get(other.getUniqueId()).getPaymentSource());
    seed(leader);
    VehicleFactionCommands.armMaintenancePay(leader);
    only(leader, "maintenance");
    assertEquals(PaymentSource.POUCH, maintenance.get(leader.getUniqueId()).getPaymentSource());
  }

  @Test
  void commandRootsAndMissingTransferArgumentsAreDistinguishedFromOtherRoutes() {
    assertFalse(VehicleFactionCommands.VehicleCommandRoute.isVehicleRoot(null));
    assertFalse(VehicleFactionCommands.VehicleCommandRoute.isVehicleRoot(new String[0]));
    assertFalse(VehicleFactionCommands.VehicleCommandRoute.isVehicleRoot(new String[] {"guild"}));
    assertTrue(VehicleFactionCommands.VehicleCommandRoute.isVehicleRoot(new String[] {"VeHiClE"}));
    assertNull(VehicleFactionCommands.VehicleCommandRoute.handoverTarget(null));
    assertNull(
        VehicleFactionCommands.VehicleCommandRoute.handoverTarget(
            new String[] {"vehicle", "give", "Other"}));
    assertEquals(
        "",
        VehicleFactionCommands.VehicleCommandRoute.handoverTarget(
            new String[] {"vehicle", "handover"}));
    assertEquals(
        "Other",
        VehicleFactionCommands.VehicleCommandRoute.handoverTarget(
            new String[] {"vehicle", "handover", "Other"}));
    assertNull(VehicleFactionCommands.VehicleCommandRoute.transferInstallationId(null));
    assertNull(VehicleFactionCommands.VehicleCommandRoute.transferInstallationId(new String[0]));
    assertNull(
        VehicleFactionCommands.VehicleCommandRoute.transferInstallationId(
            new String[] {"vehicle", "give"}));
    assertEquals(
        "",
        VehicleFactionCommands.VehicleCommandRoute.transferInstallationId(
            new String[] {"vehicle", "transfer"}));
    assertEquals(
        "pool",
        VehicleFactionCommands.VehicleCommandRoute.transferInstallationId(
            new String[] {"vehicle", "transfer", "pool"}));
  }
}
