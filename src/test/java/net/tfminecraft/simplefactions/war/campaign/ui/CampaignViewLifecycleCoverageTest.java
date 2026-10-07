package net.tfminecraft.simplefactions.war.campaign.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.CampaignInventoryHolder;
import net.tfminecraft.simplefactions.managers.inventory.CampaignView;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleFeedbackFormatter;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleBuildContext;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CampaignViewLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Player alice;
  private Faction attacker, defender;
  private War war;
  private CampaignView view;
  private CampaignViewRefreshService listener;
  private List<Battle> previousBattles;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    CampaignViewRefreshService.stop();
    previousBattles = new ArrayList<>(BattleManager.get());
    BattleManager.get().clear();
    alice = fixture.player("Alice");
    attacker = fixture.saved("attacker", "Alice");
    defender = fixture.saved("defender", "Bob");
    war = new War(751, attacker, defender);
    WarManager.get().add(war);
    view = mock(CampaignView.class);
    fixture.inventory.campaignView = view;
    when(fixture.ui.server.getPlayer(any(UUID.class)))
        .thenAnswer(
            call ->
                fixture.online.values().stream()
                    .filter(p -> p.getUniqueId().equals(call.getArgument(0)))
                    .findFirst()
                    .orElse(null));
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
        .thenAnswer(call -> mock(BossBar.class));
  }

  @AfterEach
  void close() {
    CampaignViewRefreshService.stop();
    BattleManager.get().clear();
    BattleManager.get().addAll(previousBattles);
    fixture.close();
  }

  private Runnable start() {
    PluginManager plugins = mock(PluginManager.class);
    when(fixture.ui.server.getPluginManager()).thenReturn(plugins);
    CampaignViewRefreshService.start();
    ArgumentCaptor<org.bukkit.event.Listener> capture =
        ArgumentCaptor.forClass(org.bukkit.event.Listener.class);
    verify(plugins).registerEvents(capture.capture(), same(fixture.ui.plugin));
    listener = (CampaignViewRefreshService) capture.getValue();
    return fixture.ui.repeatingTasks.getLast();
  }

  private void open(int warId, SFGUI type) {
    alice.openInventory(
        fixture.ui.inventory(new CampaignInventoryHolder(warId, type), 54, "Campaign"));
  }

  @Test
  void repeatingRefreshUsesTheRegisteredWarWithoutReopeningTheInventory() {
    Runnable task = start();
    open(war.getId(), SFGUI.CAMPAIGN_VIEW);
    var inventory = alice.getOpenInventory().getTopInventory();
    CampaignViewRefreshService.register(alice, war.getId());
    task.run();
    task.run();
    verify(view, times(2)).campaignView(alice, war, false);
    assertSame(inventory, alice.getOpenInventory().getTopInventory());
    verify(fixture.ui.scheduler)
        .runTaskTimer(same(fixture.ui.plugin), any(Runnable.class), eq(20L), eq(20L));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "offline",
        "absent",
        "different_war",
        "closed",
        "missing_war",
        "quit",
        "unregister"
      })
  void staleViewersStayUnregisteredUntilTheyOpenAgain(String cause) {
    Runnable task = start();
    open(war.getId(), SFGUI.CAMPAIGN_VIEW);
    CampaignViewRefreshService.register(alice, war.getId());
    task.run();
    clearInvocations(view);
    switch (cause) {
      case "offline" -> when(alice.isOnline()).thenReturn(false);
      case "absent" -> fixture.online.remove("Alice");
      case "different_war" -> open(war.getId() + 1, SFGUI.CAMPAIGN_VIEW);
      case "closed" -> {
        listener.onInventoryClose(new InventoryCloseEvent(alice.getOpenInventory()));
        alice.closeInventory();
      }
      case "missing_war" -> WarManager.get().clear();
      case "quit" -> listener.onPlayerQuit(new PlayerQuitEvent(alice, ""));
      case "unregister" -> CampaignViewRefreshService.unregister(alice);
      default -> throw new AssertionError(cause);
    }
    task.run();
    when(alice.isOnline()).thenReturn(true);
    fixture.online.put("Alice", alice);
    if (WarManager.get().isEmpty()) WarManager.get().add(war);
    open(war.getId(), SFGUI.CAMPAIGN_VIEW);
    task.run();
    verifyNoInteractions(view);
    CampaignViewRefreshService.register(alice, war.getId());
    task.run();
    verify(view).campaignView(alice, war, false);
  }

  @Test
  void startupWaitsForInventoryServiceAndShutdownCancelsAndForgetsViewers() {
    Runnable first = start();
    open(war.getId(), SFGUI.CAMPAIGN_VIEW);
    CampaignViewRefreshService.register(alice, war.getId());
    FactionManager.inv = null;
    first.run();
    verifyNoInteractions(view);
    FactionManager.inv = fixture.inventory;
    first.run();
    verify(view).campaignView(alice, war, false);
    clearInvocations(view);
    Runnable second = start();
    verify(fixture.ui.scheduler).cancelTask(1);
    CampaignViewRefreshService.stop();
    verify(fixture.ui.scheduler).cancelTask(2);
    second.run();
    verifyNoInteractions(view);
    CampaignViewRefreshService.stop();
    CampaignViewRefreshService.register(null, war.getId());
    CampaignViewRefreshService.unregister(null);
  }

  @Test
  void unrelatedInventoryCloseAndNonPlayerEventsDoNotUnregisterACampaignViewer() {
    Runnable task = start();
    open(war.getId(), SFGUI.CAMPAIGN_VIEW);
    CampaignViewRefreshService.register(alice, war.getId());
    InventoryView other = mock(InventoryView.class);
    when(other.getPlayer()).thenReturn(alice);
    when(other.getTopInventory()).thenReturn(fixture.ui.inventory(null, 9, "Other"));
    listener.onInventoryClose(new InventoryCloseEvent(other));
    when(other.getPlayer()).thenReturn(mock(HumanEntity.class));
    listener.onInventoryClose(new InventoryCloseEvent(other));
    task.run();
    verify(view).campaignView(alice, war, false);
  }

  @Test
  void feedbackCommandNamesDoNotDependOnTheServerLocale() {
    Locale before = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertFalse(WarScheduleFeedbackFormatter.format("WINBATTLE", war).isEmpty());
    } finally {
      Locale.setDefault(before);
    }
  }

  @Test
  void administrativeFeedbackDescribesAbsentTimesVotesAndBattles() {
    assertTrue(WarScheduleFeedbackFormatter.format(null, war).isEmpty());
    assertTrue(WarScheduleFeedbackFormatter.format("closevote", null).isEmpty());
    assertTrue(WarScheduleFeedbackFormatter.format("unknown", war).isEmpty());
    assertTrue(text("castvote").contains("Selections: §e0"));
    assertTrue(text("skipday").contains("Battle day: §e-"));
    assertTrue(text("forcequorum").contains("bypasses quorum"));
    assertTrue(text("setscheduled").contains("Scheduled: §e-"));
    assertTrue(text("battlecreate").contains("Province: §e-"));
    assertTrue(text("battledelete").contains("No campaign battle"));
    assertTrue(text("battlestart").contains("No campaign battle"));
    assertTrue(text("closevote").contains("idle"));
    war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
    assertTrue(text("closevote").contains("autoresolve_pending"));
    war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    assertTrue(text("closevote").contains("Autoresolved"));
    war.setPostponementsThisCycle(1);
    assertTrue(text("closevote").contains("Postponed"));
    for (String command : List.of("battlechoice", "defenderchoice", "pushchoice", "holdchoice")) {
      assertTrue(text(command).contains("toward_objective"));
    }
  }

  @Test
  void administrativeFeedbackReportsTheActualCampaignBattleAndBothSchedules() {
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_751");
    battle.setWarId(war.getId());
    BattleManager.addBattle(battle);
    assertTrue(text("battledelete").contains("campaign_751"));
    assertTrue(text("battlestart").contains("Started: §efalse"));
    assertTrue(text("battlecreate").contains("Battle: §ecampaign_751"));
    war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(10, CampaignBattleKind.SIEGE, true, "fort")));
    war.setCampaignCounterSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null, "port")));
    String output = text("opencvote");
    assertTrue(output.contains("Invasion schedule"));
    assertTrue(output.contains("Counter schedule"));
    assertTrue(output.contains("fort §efort"));
    assertTrue(output.contains("port §eport"));
    assertTrue(output.contains("Campaign battle: §ecampaign_751"));
  }

  private String text(String command) {
    return String.join("\n", WarScheduleFeedbackFormatter.format(command, war));
  }

  @Test
  void legacyDefenderInitiativeDoesNotDisplayAsAggressorWhenCoalitionIsAbsent() {
    war.setInitiativeHolder(
        net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole.DEFENDER);
    war.setInitiativeHolderCoalition(null);
    assertTrue(text("battlechoice").contains("Initiative: §edefender"));
  }

  @Test
  void routeActionsAndCapitalLoreReflectTheSavedCampaignState() {
    var data = fixture.data("capital_owner", "CapitalLeader");
    data.capital = 10;
    data.provinces = new ArrayList<>(List.of(10));
    Faction capitalOwner = fixture.saved(data);
    War campaign = new War(752, capitalOwner, defender);
    campaign.setCampaignProvinces(List.of(10, 20));
    campaign.setCursorIndex(0);
    campaign.setInitiativeAttacker(2);
    var invasion = new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL_INVASION, true, null);
    campaign.setCampaignBattleSchedule(List.of(invasion));
    assertEquals(List.of(20), CampaignRouteRenderer.actionProvinceIds(campaign));
    assertTrue(CampaignRouteRenderer.isCursorIndex(campaign, 0));
    assertFalse(CampaignRouteRenderer.isCursorIndex(campaign, 1));
    assertFalse(CampaignRouteRenderer.isCursorIndex(null, 0));
    assertEquals(
        Material.IRON_SWORD,
        CampaignRouteRenderer.resolveRouteEntryMaterial(
            campaign,
            capitalOwner,
            new CampaignRouteEntry(20, 1, 0),
            invasion,
            id -> "capital_owner"));
    assertTrue(
        String.join(" ", CampaignRouteRenderer.buildRouteLore(campaign, 10, id -> "capital_owner"))
            .contains("Attacker Capital"));
  }

  @Test
  void borderSlotSkipsNavalBattlesAndRequiresAnInvasionLandSlot() {
    assertTrue(CampaignRouteRenderer.buildRouteEntries(null).isEmpty());
    assertFalse(CampaignRouteRenderer.isBorderFirstBattleSlot(null, null));
    var land = new CampaignRouteEntry(30, 2, 2);
    assertFalse(CampaignRouteRenderer.isBorderFirstBattleSlot(war, land));
    war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(10, CampaignBattleKind.NAVAL, false, null),
            new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL_INVASION, false, null)));
    assertFalse(CampaignRouteRenderer.isBorderFirstBattleSlot(war, land));
    var slots = new ArrayList<>(war.getCampaignBattleSchedule());
    slots.add(new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, true, null));
    war.setCampaignBattleSchedule(slots);
    assertTrue(CampaignRouteRenderer.isBorderFirstBattleSlot(war, land));
    assertFalse(
        CampaignRouteRenderer.isBorderFirstBattleSlot(
            war, new CampaignRouteEntry(30, 2, 2, ScheduleLeg.COUNTER)));
    assertFalse(
        CampaignRouteRenderer.isBorderFirstBattleSlot(war, new CampaignRouteEntry(30, 2, -1)));
    war.setCampaignProvinces(null);
    assertEquals(-1, CampaignRouteRenderer.buildRouteEntries(war).getFirst().axisIndex());
  }

  @Test
  void routeOrderingKeepsOffAxisSlotsLastAndInvasionBeforeCounterAtSharedProvince() {
    war.setCampaignProvinces(List.of(10, 20, 30));
    war.setCampaignBattleSchedule(
        List.of(
            new ScheduledCampaignBattle(99, CampaignBattleKind.FIELD, false, null),
            new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
    war.setCampaignCounterSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
    List<CampaignRouteEntry> entries = CampaignRouteRenderer.buildRouteEntries(war);
    assertEquals(
        List.of(20, 20, 99), entries.stream().map(CampaignRouteEntry::provinceId).toList());
    assertEquals(ScheduleLeg.INVASION, entries.getFirst().scheduleLeg());
    assertEquals(ScheduleLeg.COUNTER, entries.get(1).scheduleLeg());
    war.setCampaignScheduleIndex(1);
    assertEquals(
        Material.GREEN_CONCRETE,
        CampaignRouteRenderer.resolveRouteEntryMaterial(
            war,
            attacker,
            entries.getFirst(),
            war.getCampaignBattleSchedule().get(1),
            id -> "attacker"));
    assertEquals(
        Material.GRAY_CONCRETE,
        CampaignRouteRenderer.resolveRouteEntryMaterial(
            war,
            attacker,
            entries.getLast(),
            war.getCampaignBattleSchedule().getFirst(),
            id -> "attacker"));
    assertEquals(
        Material.BLUE_CONCRETE,
        CampaignRouteRenderer.resolveMaterial(war, attacker, 10, id -> "attacker"));
    assertEquals(
        Material.RED_CONCRETE,
        CampaignRouteRenderer.resolveMaterial(war, defender, 10, id -> "attacker"));
    assertEquals(
        Material.GRAY_CONCRETE,
        CampaignRouteRenderer.resolveMaterial(war, attacker, 10, id -> null));
    assertEquals(
        Material.RED_CONCRETE,
        CampaignRouteRenderer.resolveMaterial(
            war, fixture.saved("outsider", "Other"), 10, id -> "attacker"));
  }

  @Test
  void routeLoreSafelyHandlesUnscheduledAndMissingSlots() {
    assertTrue(CampaignRouteRenderer.buildRouteLore(null, 10, id -> null).isEmpty());
    assertTrue(
        CampaignRouteRenderer.buildRouteLore(war, (CampaignRouteEntry) null, id -> null).isEmpty());
    var missing = new CampaignRouteEntry(10, 0, 8);
    assertDoesNotThrow(() -> CampaignRouteRenderer.buildRouteLore(war, missing, id -> null));
    war.setObjectiveProvinceId(70);
    war.setObjectiveHeldBy(ObjectiveHolder.ATTACKER);
    assertTrue(
        String.join(" ", CampaignRouteRenderer.buildRouteLore(war, 70, id -> null))
            .contains("Attacker Target Region"));
    war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
    assertTrue(
        String.join(" ", CampaignRouteRenderer.buildRouteLore(war, 70, id -> null))
            .contains("Defender Target Region"));
    assertTrue(CampaignRouteRenderer.buildRouteLore(war, 71, id -> null).isEmpty());
  }

  @Test
  void scheduleDiagnosticsLabelOffAxisSlotsWithoutChangingTheirSortPriority() {
    var slot = new ScheduledCampaignBattle(99, CampaignBattleKind.FIELD, false, null);
    var ctx = new CampaignScheduleBuildContext(List.of(10, 20), 10, 0, 1, null);
    assertEquals("null", CampaignScheduleLogger.formatSlot(null, null));
    assertTrue(CampaignScheduleLogger.formatSlot(slot, null).contains("homeAxis=off-axis"));
    assertTrue(
        CampaignScheduleLogger.formatSlot(slot, List.of(10, 20)).contains("sortAxis=off-axis"));
    assertEquals("n/a", CampaignScheduleLogger.fightOrderSummary(null, ScheduleLeg.INVASION, slot));
    assertEquals("n/a", CampaignScheduleLogger.fightOrderSummary(ctx, ScheduleLeg.INVASION, null));
    String offAxis = CampaignScheduleLogger.fightOrderSummary(ctx, ScheduleLeg.INVASION, slot);
    assertTrue(offAxis.contains("axisIndex=off-axis"), offAxis);
    assertTrue(offAxis.contains("fightKey=" + Integer.MAX_VALUE));
    var onAxis = new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, true, "fort");
    assertTrue(
        CampaignScheduleLogger.fightOrderSummary(ctx, ScheduleLeg.COUNTER, onAxis)
            .contains("fightKey=-1"));
  }

  @Test
  void scheduleLoggingEmitsNamesAndSlotDetailsOnlyWhenEnabled() {
    try (var log = mockStatic(LogManager.class)) {
      CampaignScheduleLogger.logSchedule("Disabled", war, List.of(), null);
      log.verify(() -> LogManager.section(anyString()), never());
      log.when(LogManager::isEnabled).thenReturn(true);
      CampaignScheduleLogger.logSchedule("Empty", war, List.of(), null);
      log.verify(() -> LogManager.section("Empty"));
      log.verify(() -> LogManager.line("(empty)"));
      var slot = new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null);
      CampaignScheduleLogger.logSchedule("Ready", war, List.of(10, 20), List.of(slot));
      log.verify(() -> LogManager.section("Ready"));
      log.verify(
          () ->
              LogManager.war(
                  eq("[%d] warId=%s %s | %s"),
                  eq(0),
                  eq(war.getId()),
                  anyString(),
                  contains("province=20")));
      log.verify(
          () -> LogManager.line(eq("[%d] %s | %s"), eq(0), anyString(), contains("sortAxis=1")));
    }
  }
}
