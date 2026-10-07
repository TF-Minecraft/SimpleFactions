package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.holder.*;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults.InstallationPickToggleResult;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class InventoryBoundaryCoverageTest {
  private Fixture rig;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
  }

  @AfterEach
  void close() throws Exception {
    if (rig != null) rig.close();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "military:owner",
        ":owner:0",
        "unknown:owner:0",
        "military::0",
        "military:owner:",
        "military:owner:nope",
        "military:owner:2147483648"
      })
  void invalidCancelPayloadsCannotAddressAQueueEntry(String payload) {
    assertTrue(QueueCancelPayload.parse(payload).isEmpty());
  }

  @Test
  void cancellationPayloadRetainsExactOwnerAndInstallationIdentity() {
    for (QueueCancelPayload.Type type : QueueCancelPayload.Type.values()) {
      String detail = type == QueueCancelPayload.Type.INSTALLATION ? "port:east" : "17";
      var parsed =
          QueueCancelPayload.parse(QueueCancelPayload.encode(type, "owner-a", detail))
              .orElseThrow();
      assertEquals(type, parsed.type());
      assertEquals("owner-a", parsed.ownerId());
      assertEquals(detail, parsed.detail());
      assertEquals(type == QueueCancelPayload.Type.INSTALLATION ? 0 : 17, parsed.index());
    }
  }

  @Test
  void routingHoldersStayAttachedToServerOwnedInventories() {
    CampaignInventoryHolder campaign =
        new CampaignInventoryHolder(rig.war.getId(), SFGUI.CAMPAIGN_VIEW);
    CampaignRaidLaunchHolder raid =
        new CampaignRaidLaunchHolder(rig.war.getId(), rig.source.getId());
    DeclareWarHolder declare =
        new DeclareWarHolder(
            rig.attacker.getId(),
            rig.defender.getId(),
            SFGUI.WAR_DECLARE_GOAL,
            "government",
            "leadership",
            "group");
    SFCombinedInventoryHolder combined =
        new SFCombinedInventoryHolder(rig.war.getId(), rig.attacker.getId(), SFGUI.WAR_VIEW);
    SFInventoryHolder faction =
        new SFInventoryHolder(rig.attacker.getId(), SFGUI.FACTION_VIEW, "subject");
    WarInventoryHolder war = new WarInventoryHolder(rig.war.getId(), SFGUI.WAR_VIEW);
    for (InventoryHolder holder : List.of(campaign, raid, declare, combined, faction, war)) {
      Inventory inventory = rig.domain.ui.plugin.getServer().createInventory(holder, 9, "Routing");
      assertSame(holder, inventory.getHolder());
      assertNull(
          holder.getInventory(), "Holders identify the menu; Bukkit owns its inventory instance");
      rig.alice.openInventory(inventory);
      assertSame(inventory, rig.top(rig.alice));
    }
    campaign.setPage(-1);
    raid.setPage(2);
    faction.setPage(3);
    assertEquals(0, campaign.getPage());
    assertEquals(2, raid.getPage());
    assertEquals(3, faction.getPage());
    assertEquals(rig.war.getId(), campaign.getWarId());
    assertEquals(rig.source.getId(), raid.getSourceInstallationId());
    assertFalse(raid.isSourcePage());
    assertEquals("group", declare.getPickingGroupId());
    assertEquals(rig.attacker.getId(), combined.getFactionId());
    assertEquals("subject", faction.getSecondaryId());
    assertEquals(rig.war.getId(), war.getId());
  }

  @Test
  void anOpenInstallationMenuRechecksLeadershipBeforeChangingThePick() {
    rig.time(
        net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService.atScheduleHour(
            Fixture.DAY, 10));
    var view = new CampaignInstallationPickView(rig.navigation);
    view.open(rig.alice, rig.war, rig.attacker);
    Inventory inventory = rig.top(rig.alice);
    rig.attacker.addMember("Successor");
    rig.attacker.setLeader("Successor");
    long writes = rig.warWrites();
    var click = rig.domain.ui.click(rig.alice, 12);
    view.click(click, inventory, rig.alice);
    assertTrue(click.isCancelled());
    assertTrue(BattleInstallationPickService.getPicks(rig.war, rig.attacker.getId()).isEmpty());
    assertEquals(writes, rig.warWrites());
    verify(rig.alice)
        .sendMessage("§cOnly your faction leader can select installations for this battle.");
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "ADDED|§aCommitted Harbor for this battle.",
        "REMOVED|§7Uncommitted Harbor.",
        "REJECTED_LOCKED|§cInstallation choices are locked until the next battle day.",
        "REJECTED_ZOC_PORT|§cThe ZOC port is required for this naval battle.",
        "REJECTED_NOT_LEADER|§cOnly your faction leader can select installations for this battle.",
        "REJECTED_NOT_PARTICIPANT|§cYou are not a belligerent in this war.",
        "REJECTED_INVALID_INSTALLATION|§cThat installation is not available.",
        "REJECTED_WAR_INACTIVE|§cWar not found."
      },
      delimiter = '|')
  void installationResultsKeepTheirDistinctPlayerFacingMessages(
      InstallationPickToggleResult result, String expected) {
    assertEquals(expected, CampaignInstallationPickView.toggleMessage(result, "Harbor"));
  }
}
