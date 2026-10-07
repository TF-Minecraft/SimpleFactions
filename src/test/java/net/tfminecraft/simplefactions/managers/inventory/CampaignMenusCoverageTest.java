package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.CampaignInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignChoiceService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignNavyGate;
import net.tfminecraft.simplefactions.war.campaign.progression.WhitePeaceService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignRetreatService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignRetreatService.ConcedeResult;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignRetreatService.RetreatResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLaunchAvailability;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLaunchAvailability.LaunchAvailability;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.LaunchResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleAutoresolveService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleAutoresolveService.SendResult;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignRouteEntry;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignRouteRenderer;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignViewRefreshService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleHourTally;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleVoteToggleResult;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.resolution.WarResolutionService;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class CampaignMenusCoverageTest {
  private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
  private FactionDomainFixture fixture;
  private Faction attacker, defender;
  private Player player, defending;
  private War war;
  private InventoryManager navigation;
  private CampaignView view;
  private CampaignCreator creator;
  private MockedStatic<WarManager> wars;
  private MockedStatic<CampaignClock> clock;
  private MockedStatic<CampaignRouteRenderer> routes;
  private MockedStatic<BattleInstallationPickService> picks;
  private MockedStatic<CampaignNavyGate> navy;
  private MockedStatic<CampaignRaidLaunchAvailability> availability;
  private MockedStatic<CampaignRetreatService> retreat;
  private MockedStatic<WhitePeaceService> peace;
  private MockedStatic<BattleAutoresolveService> autoresolve;
  private MockedStatic<CampaignChoiceService> choices;
  private MockedStatic<CampaignCapabilityService> capability;
  private MockedStatic<WarResolutionService> resolution;
  private MockedStatic<CampaignViewRefreshService> refresh;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();
  private int oldStart, oldEnd, oldClose;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> result = mockStatic(type);
    scopes.add(result);
    return result;
  }

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    oldStart = Cache.warBattleWindowStartHour;
    oldEnd = Cache.warBattleWindowEndHour;
    oldClose = Cache.warVoteCloseHour;
    Cache.warBattleWindowStartHour = 20;
    Cache.warBattleWindowEndHour = 22;
    Cache.warVoteCloseHour = 16;
    attacker = fixture.saved("attacker", "Leader");
    attacker.getOrCreateMainGuild();
    defender = fixture.saved("defender", "Defender");
    defender.getOrCreateMainGuild();
    player = fixture.player("Leader");
    defending = fixture.player("Defender");
    war = new War(7, attacker, defender);
    war.setGoal(WarGoalType.SUBJUGATE);
    war.setWarType(WarType.SUBJUGATE);
    war.setBattleDay(LocalDate.of(2026, 10, 7));
    war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    war.setCampaignProvinces(List.of(10, 11, 12));
    war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(11, CampaignBattleKind.FIELD, true, null)));
    wars = scoped(WarManager.class);
    wars.when(() -> WarManager.getById(7)).thenReturn(war);
    clock = scoped(CampaignClock.class);
    clock.when(CampaignClock::now).thenReturn(NOW);
    scoped(BattleManager.class);
    scoped(InstallationConfigLoader.class);
    refresh = scoped(CampaignViewRefreshService.class);
    Function<UUID, Faction> lookup =
        uuid -> uuid.equals(player.getUniqueId()) ? attacker : defender;
    scoped(BattleScheduleLookups.class)
        .when(() -> BattleScheduleLookups.uuidToFactionForWar(war))
        .thenReturn(lookup);
    routes =
        mockStatic(
            CampaignRouteRenderer.class,
            call ->
                switch (call.getMethod().getName()) {
                  case "resolveRouteEntryMaterial" -> Material.PAPER;
                  case "buildRouteLore" -> List.of("§7Defender front");
                  default -> call.callRealMethod();
                });
    scopes.add(routes);
    scoped(BattleNamingService.class)
        .when(
            () ->
                BattleNamingService.resolveScheduledDisplayName(
                    eq(war), any(), anyInt(), any(), anyInt()))
        .thenAnswer(c -> "Battle of " + c.getArgument(4));
    picks = scoped(BattleInstallationPickService.class);
    navy = scoped(CampaignNavyGate.class);
    navy.when(() -> CampaignNavyGate.winnerCanContestNextNaval(eq(war), any())).thenReturn(true);
    availability = scoped(CampaignRaidLaunchAvailability.class);
    availability
        .when(() -> CampaignRaidLaunchAvailability.describe(eq(war), any(), any()))
        .thenReturn(new LaunchAvailability(true, List.of("§7Launch from a port")));
    retreat = scoped(CampaignRetreatService.class);
    peace = scoped(WhitePeaceService.class);
    autoresolve = scoped(BattleAutoresolveService.class);
    autoresolve
        .when(() -> BattleAutoresolveService.canProposeAutoresolveNow(war, NOW))
        .thenReturn(true);
    choices = scoped(CampaignChoiceService.class);
    capability = scoped(CampaignCapabilityService.class);
    capability
        .when(() -> CampaignCapabilityService.canMountOffensiveAfterPush(eq(war), any()))
        .thenReturn(true);
    resolution = scoped(WarResolutionService.class);
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator items = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(items);
    when(items.getItemsAdderItem(anyString())).thenAnswer(c -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(api);
    scoped(IconGetter.class)
        .when(() -> IconGetter.getIconOrDefault(anyString(), any()))
        .thenAnswer(c -> new ItemStack(c.<Material>getArgument(1)));
    navigation = mock(InventoryManager.class);
    navigation.confirming = new HashMap<>();
    navigation.campaignConfirmWar = new HashMap<>();
    navigation.campaignInstallationPickView = mock(CampaignInstallationPickView.class);
    navigation.campaignRaidLaunchView = mock(CampaignRaidLaunchView.class);
    when(navigation.getFiller(any())).thenAnswer(c -> new ItemStack(c.<Material>getArgument(0)));
    when(navigation.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.ARROW));
    view = new CampaignView(navigation);
    creator = view.creator;
  }

  @AfterEach
  void close() {
    try {
      for (int n = scopes.size() - 1; n >= 0; n--) scopes.get(n).close();
    } finally {
      Cache.warBattleWindowStartHour = oldStart;
      Cache.warBattleWindowEndHour = oldEnd;
      Cache.warVoteCloseHour = oldClose;
      fixture.close();
    }
  }

  @Test
  void routeItemsContainBattleLocationAndScheduleIdentityOrAnUnavailableMarker() {
    CampaignRouteEntry entry = new CampaignRouteEntry(11, 1, 0);
    ItemStack item = creator.createRouteEntryItem(war, attacker, entry);
    assertEquals("Battle of 11", name(item));
    assertEquals(11, number(item, "campaign_province"));
    assertEquals(0, number(item, "campaign_schedule_index"));
    assertEquals("INVASION", string(item, "campaign_schedule_leg"));
    assertLore(item, "Defender front");
    ItemStack missing =
        creator.createRouteEntryItem(war, attacker, new CampaignRouteEntry(99, 5, -1));
    assertEquals(Material.BARRIER, missing.getType());
    assertEquals("Province 99", name(missing));
    assertNull(number(missing, "campaign_province"));
    ItemStack marker = creator.createFirstBattleMarkerItem();
    assertEquals("First Battle", name(marker));
    assertNull(marker.getItemMeta().getLore());
  }

  @Test
  void campaignInformationShowsInitiativeVotesPeaceAndTheScheduledCountdown() {
    war.setInitiativeAttacker(4);
    war.setInitiativeDefender(2);
    war.setWhitePeaceProposedByAttacker(true);
    war.setWhitePeaceProposedByDefender(true);
    war.getBattleVotes().put(player.getUniqueId(), Set.of(20, 21));
    ItemStack voting = creator.createInfoItem(war, attacker, player.getUniqueId());
    assertLore(voting, "Attacker Initiative: 4");
    assertLore(voting, "Defender Initiative: 2");
    assertLore(voting, "Attacker proposed white peace");
    assertLore(voting, "Defender proposed white peace");
    assertLore(voting, "Hour Votes:");
    assertLore(voting, "Total voters: 1");
    assertLore(voting, "Your Hours:");
    war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    war.setScheduledBattleAt(NOW.plusSeconds(3600));
    war.setScheduledBattleProvinceId(11);
    ItemStack scheduled = creator.createInfoItem(war, attacker, null, uuid -> attacker);
    assertLore(scheduled, "Fight At:");
    assertLore(scheduled, "Starts in");
    assertLore(scheduled, "Battle province: 11");
    assertLore(scheduled, "Your Hours: -");
    war.setScheduledBattleAt(null);
    war.setScheduledBattleProvinceId(null);
    assertFalse(lore(creator.createInfoItem(war, attacker, null)).contains("Fight At:"));
    assertTrue(CampaignCreator.buildScheduleInfoLines(null, null, null).isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"true,true", "false,true", "true,false", "false,false"})
  void hourButtonsDescribeSelectionEligibilityAndOnlyTagAllowedActions(
      boolean selected, boolean allowed) {
    ItemStack item =
        creator.createHourToggleItem(war, 20, selected, allowed, new BattleHourTally(2, 3));
    assertLore(item, "2/3");
    assertLore(item, "attacker/defender");
    assertLore(
        item, allowed ? (selected ? "Click to remove" : "Click to select") : "Voting closed");
    assertEquals(allowed ? 7 : null, number(item, "campaign_vote_war"));
    assertEquals(allowed ? 20 : null, number(item, "campaign_vote_hour"));
    ItemStack unavailable = creator.createHourToggleItem(null, 20, selected, allowed, null);
    assertLore(unavailable, "0/0");
    assertNull(number(unavailable, "campaign_vote_war"));
  }

  @Test
  void enemyCommitmentsRemainHiddenUntilLockAndResolveKnownInstallationNames() {
    assertTrue(CampaignCreator.buildEnemyInstallationIntelLines(null, attacker, NOW).isEmpty());
    assertTrue(CampaignCreator.buildEnemyInstallationIntelLines(war, null, NOW).isEmpty());
    Faction outsider = fixture.saved("outsider", "Outsider");
    assertTrue(CampaignCreator.buildEnemyInstallationIntelLines(war, outsider, NOW).isEmpty());
    assertLore(creator.createEnemyInstallationIntelItem(war, attacker), "hidden until vote close");
    Installation fort = new Installation("fort", "Castle", InstallationKind.FORT, 11, 0, 0, 0);
    defender.getInstallationHandler().load(List.of(fort.toData()));
    Map<String, Set<String>> enemy = new LinkedHashMap<>();
    enemy.put("defender", Set.of("fort", "missing"));
    enemy.put("departed", Set.of("old-port"));
    enemy.put("empty", Set.of());
    enemy.put("unknown", null);
    picks.when(() -> BattleInstallationPickService.isLocked(war, NOW)).thenReturn(true);
    picks
        .when(() -> BattleInstallationPickService.getVisibleEnemyPicks(war, "attacker", NOW))
        .thenReturn(enemy);
    ItemStack intel = creator.createEnemyInstallationIntelItem(war, attacker);
    assertLore(intel, "Castle");
    assertLore(intel, "missing");
    assertLore(intel, "departed: old-port");
    assertLore(intel, "empty: (none)");
    assertLore(intel, "unknown: (none)");
  }

  @Test
  void campaignActionButtonsCarryOnlyTheirMatchingWarAction() {
    Map<String, ItemStack> buttons = new LinkedHashMap<>();
    buttons.put("campaign_retreat", creator.createRetreatButton(war));
    buttons.put("campaign_surrender", creator.createSurrenderButton(war));
    buttons.put("campaign_accept_peace", creator.createAcceptPeaceButton(war));
    buttons.put("campaign_push", creator.createPushButton(war));
    buttons.put("campaign_hold", creator.createHoldButton(war));
    buttons.put("campaign_attack", creator.createLoserAttackButton(war));
    buttons.put("campaign_loser_peace", creator.createLoserAcceptPeaceButton(war));
    for (var entry : buttons.entrySet()) {
      assertEquals(7, number(entry.getValue(), entry.getKey()));
      assertFalse(lore(entry.getValue()).isBlank());
    }
    navy.when(() -> CampaignNavyGate.winnerCanContestNextNaval(eq(war), any())).thenReturn(false);
    ItemStack blocked = creator.createPushButton(war);
    assertEquals(Material.GRAY_CONCRETE, blocked.getType());
    assertNull(number(blocked, "campaign_push"));
    assertFalse(lore(blocked).isBlank());
    for (BelligerentRole side : BelligerentRole.values()) {
      ItemStack item = creator.createAutoresolveProposeButton(war, side);
      assertEquals(7, number(item, "campaign_autoresolve_war"));
      assertEquals(side.name(), string(item, "campaign_autoresolve_side"));
      assertLore(item, "Opposing war leader must accept");
    }
    assertLore(creator.createVotingHelpItem(), "One vote per eligible player");
    assertEquals(Material.BLACK_STAINED_GLASS_PANE, creator.createUnusedHourSlotItem().getType());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void installationEntryAndSummaryExplainWhetherCommitmentsAreLocked(boolean locked) {
    picks.when(() -> BattleInstallationPickService.isLocked(war, NOW)).thenReturn(locked);
    picks
        .when(() -> BattleInstallationPickService.getPicks(war, "attacker"))
        .thenReturn(Set.of("fort", "port"));
    ItemStack entry = creator.createInstallationsEntryButton(war, attacker);
    assertEquals(7, number(entry, "campaign_installations_entry_war"));
    assertLore(entry, "Committed: 2");
    assertLore(entry, locked ? "Locked at vote close" : "Leader only until vote close");
    assertLore(creator.createInstallationsEntryButton(war, null), "Committed: 0");
    assertLore(
        creator.createInstallationPickSummaryItem(war, attacker, locked),
        locked ? "Locked at vote close" : "Click installations below");
    assertLore(creator.createInstallationPickSummaryItem(war, null, locked), "Committed: 0");
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,false",
    "true,false,false",
    "false,true,false",
    "true,true,false",
    "true,true,true"
  })
  void installationSelectionPreservesItsIdentityAndEnforcesPickLocks(
      boolean selected, boolean locked, boolean zocLocked) {
    Installation fort = new Installation("fort", "Castle", InstallationKind.FORT, 11, 0, 0, 0);
    ItemStack item =
        creator.createInstallationPickToggleItem(war, fort, selected, locked, zocLocked);
    assertEquals("Castle", name(item));
    assertLore(item, "Province: 11");
    if (selected) assertLore(item, "Committed for this battle");
    assertLore(
        item,
        zocLocked
            ? "Required"
            : locked ? "Locked at vote close" : selected ? "Click to uncommit" : "Click to commit");
    assertEquals(!locked || zocLocked ? 7 : null, number(item, "campaign_installation_pick_war"));
    assertEquals(
        !locked || zocLocked ? "fort" : null, string(item, "campaign_installation_pick_id"));
    assertNull(
        number(
            creator.createInstallationPickToggleItem(null, fort, selected, locked, zocLocked),
            "campaign_installation_pick_war"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void raidEntryTracksLiveAvailabilityAndSelectionPrompts(boolean enabled) {
    availability
        .when(() -> CampaignRaidLaunchAvailability.describe(war, attacker, NOW))
        .thenReturn(new LaunchAvailability(enabled, List.of(enabled ? "Ready" : "Quota spent")));
    ItemStack entry = creator.createStartRaidEntryButton(war, attacker, NOW);
    assertEquals(enabled ? Material.CROSSBOW : Material.GRAY_STAINED_GLASS_PANE, entry.getType());
    assertEquals(enabled ? 7 : null, number(entry, "campaign_raid_entry_war"));
    assertLore(entry, enabled ? "Ready" : "Quota spent");
    Installation port = new Installation("port", "Harbour", InstallationKind.PORT, 10, 0, 0, 0);
    assertLore(
        creator.createRaidLaunchSummaryItem(war, attacker, null, enabled, NOW),
        enabled ? "No valid sources" : "Choose your port or airport");
    assertLore(
        creator.createRaidLaunchSummaryItem(war, attacker, port, enabled, NOW),
        enabled ? "No valid targets" : "Click an enemy installation");
    ItemStack source = creator.createRaidLaunchInstallationItem(war, port, "attacker", true);
    assertLore(source, "Owner: attacker");
    assertLore(source, "Click to pick targets");
    assertEquals(7, number(source, "campaign_raid_launch_war"));
    assertEquals("port", string(source, "campaign_raid_launch_installation_id"));
    ItemStack target =
        creator.createRaidLaunchInstallationItem(war, port, enabled ? null : "", false);
    assertLore(target, "Click to launch raid");
    assertFalse(lore(target).contains("Owner:"));
  }

  @Test
  void campaignOpensCompleteCurrentStateAndRefreshesTheSameInventory() {
    retreat.when(() -> CampaignRetreatService.canRetreat(war, attacker, NOW)).thenReturn(true);
    peace.when(() -> WhitePeaceService.acceptWhitePeace(war, attacker)).thenReturn(true);
    view.campaignView(player, war, true);
    Inventory first = top();
    assertTrue(CampaignView.isViewingCampaign(player, 7));
    assertEquals("Campaign status", name(first.getItem(4)));
    assertEquals(11, number(first.getItem(10), "campaign_province"));
    assertEquals(Material.AIR, first.getItem(11).getType());
    assertEquals("First Battle", name(first.getItem(19)));
    assertNotNull(first.getItem(27));
    assertEquals(20, number(first.getItem(28), "campaign_vote_hour"));
    assertEquals(Material.BLACK_STAINED_GLASS_PANE, first.getItem(32).getType());
    assertEquals(7, number(first.getItem(46), "campaign_retreat"));
    assertEquals(7, number(first.getItem(47), "campaign_surrender"));
    assertEquals(7, number(first.getItem(48), "campaign_accept_peace"));
    assertEquals("ATTACKER", string(first.getItem(49), "campaign_autoresolve_side"));
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, first.getItem(50).getType());
    view.campaignView(player, war, false);
    assertSame(first, top());
    refresh.verify(() -> CampaignViewRefreshService.register(player, 7));
    view.campaignView(defending, war, true);
    assertEquals(
        "DEFENDER",
        string(
            defending.getOpenInventory().getTopInventory().getItem(50),
            "campaign_autoresolve_side"));
  }

  @Test
  void refreshingAChangedRouteRemovesTheFormerFirstBattleMarker() {
    view.campaignView(player, war, true);
    Inventory inventory = top();
    assertEquals("First Battle", name(inventory.getItem(19)));
    war.setCampaignCounterSchedule(
        List.of(new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, true, null)));
    view.campaignView(player, war, false);
    assertSame(inventory, top());
    assertEquals("First Battle", name(inventory.getItem(20)));
    assertTrue(
        inventory.getItem(19) == null || inventory.getItem(19).getType() == Material.AIR,
        "Previous marker must be removed when sorted route entries change");
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "ended", "raid", "route", "membership"})
  void invalidCampaignsExplainTheFailureOnlyForAnExplicitOpen(String reason) {
    War requested = war;
    if (reason.equals("missing")) requested = null;
    if (reason.equals("ended")) war.end(WarEndReason.ADMIN_END);
    if (reason.equals("raid")) war.setWarType(WarType.RAID);
    if (reason.equals("route")) war.setCampaignProvinces(List.of());
    if (reason.equals("membership")) removePlayerFromFaction();
    Inventory before = top();
    view.campaignView(player, requested, false);
    verify(player, never()).sendMessage(anyString());
    view.campaignView(player, requested, true);
    verify(player)
        .sendMessage(
            contains(
                reason.equals("route")
                    ? "no campaign route"
                    : reason.equals("membership") ? "not in a faction" : "no campaign view"));
    assertSame(before, top());
    refresh.verifyNoInteractions();
  }

  @Test
  void nonleaderMembersAndOutsideFactionsReceiveOnlyTheirOwnAvailableActions() {
    Player member = fixture.player("Member");
    attacker.addMember("Member");
    view.campaignView(member, war, true);
    Inventory memberPage = member.getOpenInventory().getTopInventory();
    assertEquals(7, number(memberPage.getItem(33), "campaign_installations_entry_war"));
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, memberPage.getItem(47).getType());
    Faction other = fixture.saved("other", "Other");
    other.getOrCreateMainGuild();
    Player visitor = fixture.player("Other");
    view.campaignView(visitor, war, true);
    Inventory foreign = visitor.getOpenInventory().getTopInventory();
    for (int slot : List.of(33, 34, 35, 46, 47, 48, 49, 50))
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, foreign.getItem(slot).getType());
    assertFalse(view.canRetreat(member, war));
    assertFalse(view.canAcceptWhitePeace(member, war));
    assertFalse(view.canMakePostBattleChoice(member, war));
    assertFalse(view.isAttackerLeader(member, war));
    assertFalse(view.isDefenderLeader(member, war));
    assertFalse(view.canSurrender(player, null));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void postBattleMenusExposeOnlyTheRequiredChoiceForTheCoalitionLeader(boolean winner) {
    war.setPostBattleChoicePhase(
        winner ? PostBattleChoicePhase.WINNER_PUSH_HOLD : PostBattleChoicePhase.LOSER_ATTACK_PEACE);
    war.setPostBattleWinnerCoalition(
        winner ? CampaignCoalition.AGGRESSOR : CampaignCoalition.DEFENDER);
    war.setPostBattleChoiceResolved(false);
    view.campaignView(player, war, true);
    Inventory inventory = top();
    assertEquals(
        7,
        number(inventory.getItem(winner ? 40 : 42), winner ? "campaign_push" : "campaign_attack"));
    assertEquals(
        7,
        number(
            inventory.getItem(winner ? 41 : 43),
            winner ? "campaign_hold" : "campaign_loser_peace"));
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(47).getType());
    view.campaignView(defending, war, true);
    assertEquals(
        Material.GRAY_STAINED_GLASS_PANE,
        defending.getOpenInventory().getTopInventory().getItem(winner ? 40 : 42).getType());
    assertFalse(view.canSurrender(player, war));
    war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    view.campaignView(player, war, false);
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, top().getItem(49).getType());
  }

  @Test
  void campaignHolderIdentityPreventsRefreshAndClickingUnrelatedInventories() {
    assertFalse(CampaignView.isViewingCampaign(null, 7));
    assertFalse(CampaignView.isViewingCampaign(player, 7));
    Inventory before = top();
    view.campaignView(player, war, false);
    assertSame(before, top());
    Inventory unrelated = fixture.ui.inventory(null, 9, "Other");
    player.openInventory(unrelated);
    InventoryClickEvent other = fixture.ui.click(player, 0);
    view.click(other, unrelated, player);
    assertFalse(other.isCancelled());
    Inventory wrong =
        fixture.ui.inventory(new CampaignInventoryHolder(7, SFGUI.WAR_VIEW), 54, "War");
    player.openInventory(wrong);
    InventoryClickEvent wrongEvent = fixture.ui.click(player, 0);
    view.click(wrongEvent, wrong, player);
    assertFalse(wrongEvent.isCancelled());
    assertFalse(CampaignView.isViewingCampaign(player, 7));
    open();
    assertFalse(CampaignView.isViewingCampaign(player, 8));
  }

  @Test
  void backButtonUnregistersRefreshAndMissingWarIsReported() {
    open();
    click(53);
    refresh.verify(() -> CampaignViewRefreshService.unregister(player));
    verify(navigation).warView(null, player, war, true);
    wars.when(() -> WarManager.getById(7)).thenReturn(null);
    click(0);
    verify(player).sendMessage("§cWar not found.");
    wars.when(() -> WarManager.getById(7)).thenReturn(war);
    war.end(WarEndReason.ADMIN_END);
    click(0);
    verify(player, times(2)).sendMessage("§cWar not found.");
  }

  @Test
  void emptyAndForeignTaggedItemsCannotDispatchAnAction() {
    Inventory inventory = open();
    click(0);
    inventory.setItem(0, new ItemStack(Material.AIR));
    click(0);
    inventory.setItem(0, tag("campaign_retreat", 8));
    click(0);
    inventory.setItem(0, tag("campaign_province", 11));
    click(0);
    assertTrue(navigation.confirming.isEmpty());
    assertTrue(navigation.campaignConfirmWar.isEmpty());
    verify(navigation, never()).confirmView(any(), any(), anyString(), anyString());
  }

  @Test
  void realHourTogglesAddRemovePersistAndRefreshAvailability() {
    view.campaignView(player, war, true);
    click(28);
    assertEquals(Set.of(20), war.getBattleVotes().get(player.getUniqueId()));
    verify(player).sendMessage("§aAdded 20:00 UTC to your availability.");
    assertLore(top().getItem(28), "Click to remove");
    click(28);
    assertFalse(war.getBattleVotes().containsKey(player.getUniqueId()));
    verify(player).sendMessage("§aRemoved 20:00 UTC from your availability.");
    wars.verify(() -> WarManager.persist(war), times(2));
  }

  @ParameterizedTest
  @EnumSource(
      value = BattleVoteToggleResult.class,
      names = {
        "REJECTED_INVALID_HOUR",
        "REJECTED_NOT_PARTICIPANT",
        "REJECTED_OFFLINE",
        "REJECTED_VOTE_CLOSED"
      })
  void voteServiceRejectionsExplainTheProblemAndDoNotPersist(BattleVoteToggleResult result) {
    Inventory inventory = open();
    inventory.setItem(0, creator.createHourToggleItem(war, 20, false, true, null));
    try (MockedStatic<BattleVoteService> votes = mockStatic(BattleVoteService.class)) {
      votes
          .when(() -> BattleVoteService.toggleVote(war, player.getUniqueId(), 20, attacker, true))
          .thenReturn(result);
      click(0);
      votes.verify(
          () -> BattleVoteService.toggleVote(war, player.getUniqueId(), 20, attacker, true));
    }
    verify(player)
        .sendMessage(
            switch (result) {
              case REJECTED_INVALID_HOUR -> "§cThat hour is not in the battle window.";
              case REJECTED_NOT_PARTICIPANT -> "§cYou are not eligible to vote in this war.";
              case REJECTED_OFFLINE -> "§cYou must be online to vote.";
              case REJECTED_VOTE_CLOSED -> "§cVoting is closed for this battle day.";
              default -> throw new IllegalArgumentException(result.name());
            });
    wars.verify(() -> WarManager.persist(any()), never());
    assertTrue(war.getBattleVotes().isEmpty());
  }

  @Test
  void expiredVotingAndFormerMembersCannotUseAnAlreadyOpenHourButton() {
    Inventory inventory = open();
    inventory.setItem(0, creator.createHourToggleItem(war, 20, false, true, null));
    clock.when(CampaignClock::now).thenReturn(NOW.plusSeconds(36000));
    click(0);
    verify(player).sendMessage(contains("cannot vote"));
    assertTrue(war.getBattleVotes().isEmpty());
    clock.when(CampaignClock::now).thenReturn(NOW);
    removePlayerFromFaction();
    click(0);
    verify(player, times(2)).sendMessage(contains("cannot vote"));
    assertTrue(war.getBattleVotes().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(SendResult.class)
  void autoresolveRequestsSurfaceTheServiceOutcomeAndRefreshOnlyWhenSent(SendResult result) {
    Inventory inventory = open();
    inventory.setItem(0, creator.createAutoresolveProposeButton(war, BelligerentRole.ATTACKER));
    autoresolve
        .when(
            () ->
                BattleAutoresolveService.sendProposeRequest(player, war, BelligerentRole.ATTACKER))
        .thenReturn(result);
    click(0);
    autoresolve.verify(
        () -> BattleAutoresolveService.sendProposeRequest(player, war, BelligerentRole.ATTACKER));
    if (result == SendResult.SENT) {
      assertEquals("Campaign status", name(top().getItem(4)));
      verify(player).playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    } else
      verify(player)
          .sendMessage(
              contains(
                  result == SendResult.OPPOSING_LEADER_OFFLINE ? "not online" : "cannot propose"));
  }

  @Test
  void autoresolveRequiresTheMatchingCurrentLeaderAndOpenWindow() {
    Inventory inventory = open();
    inventory.setItem(0, creator.createAutoresolveProposeButton(war, BelligerentRole.DEFENDER));
    click(0);
    verify(player).sendMessage(contains("cannot propose"));
    inventory.setItem(0, creator.createAutoresolveProposeButton(war, BelligerentRole.ATTACKER));
    autoresolve
        .when(() -> BattleAutoresolveService.canProposeAutoresolveNow(war, NOW))
        .thenReturn(false);
    click(0);
    verify(player, times(2)).sendMessage(contains("cannot propose"));
    autoresolve.verify(
        () -> BattleAutoresolveService.sendProposeRequest(any(), any(), any()), never());
  }

  @Test
  void installationAndRaidEntriesRevalidateParticipationAndRaidLeadership() {
    Inventory inventory = open();
    inventory.setItem(0, creator.createInstallationsEntryButton(war, attacker));
    click(0);
    verify(navigation.campaignInstallationPickView).open(player, war, attacker);
    Player member = fixture.player("Member");
    attacker.addMember("Member");
    member.openInventory(inventory);
    InventoryClickEvent event = fixture.ui.click(member, 0);
    view.click(event, inventory, member);
    verify(navigation.campaignInstallationPickView).open(member, war, attacker);
    inventory.setItem(0, creator.createStartRaidEntryButton(war, attacker, NOW));
    event = fixture.ui.click(member, 0);
    view.click(event, inventory, member);
    verify(member).sendMessage(contains("leader"));
    verifyNoInteractions(navigation.campaignRaidLaunchView);
    attacker.getOrCreateMainGuild().kick("Member");
    event = fixture.ui.click(member, 0);
    view.click(event, inventory, member);
    verify(member).sendMessage(contains("belligerent"));
    inventory.setItem(0, creator.createInstallationsEntryButton(war, attacker));
    event = fixture.ui.click(member, 0);
    view.click(event, inventory, member);
    verify(member, times(2)).sendMessage(contains("belligerent"));
  }

  @ParameterizedTest
  @EnumSource(LaunchResult.class)
  void raidLaunchChecksTheCurrentServiceAvailability(LaunchResult result) {
    Inventory inventory = open();
    inventory.setItem(0, creator.createStartRaidEntryButton(war, attacker, NOW));
    try (MockedStatic<CampaignRaidService> raid = mockStatic(CampaignRaidService.class)) {
      raid.when(() -> CampaignRaidService.canLaunch(war, attacker, NOW)).thenReturn(result);
      click(0);
      raid.verify(() -> CampaignRaidService.canLaunch(war, attacker, NOW));
    }
    if (result == LaunchResult.STARTED)
      verify(navigation.campaignRaidLaunchView).openSourcePage(player, war, attacker);
    else {
      verifyNoInteractions(navigation.campaignRaidLaunchView);
      verify(player).sendMessage(anyString());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"retreat", "surrender", "accept_peace", "push", "hold", "attack", "loser_peace"})
  void allowedActionsCreateConfirmationForTheCurrentWarAndFaction(String action) {
    prepareAction(action);
    Inventory inventory = open();
    inventory.setItem(0, action(action));
    click(0);
    assertSame(attacker, navigation.confirming.get(player));
    assertEquals(7, navigation.campaignConfirmWar.get(player));
    verify(navigation).confirmView(player, attacker, "campaign_" + action, "7");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"retreat", "surrender", "accept_peace", "push", "hold", "attack", "loser_peace"})
  void staleActionButtonsCannotAuthorizeAFormerLeader(String action) {
    prepareAction(action);
    Inventory inventory = open();
    inventory.setItem(0, action(action));
    attacker.addMember("Successor");
    attacker.setLeader("Successor");
    click(0);
    assertTrue(navigation.confirming.isEmpty());
    assertTrue(navigation.campaignConfirmWar.isEmpty());
    verify(navigation, never()).confirmView(any(), any(), anyString(), anyString());
    verify(player).sendMessage(contains("cannot"));
  }

  @Test
  void cancellationAndStaleConfirmationsClearPendingStateWithoutApplyingAnything() {
    view.handleConfirm(player, "campaign_push", "7", true);
    choices.verifyNoInteractions();
    pending();
    view.handleConfirm(player, "campaign_push", "7", false);
    assertTrue(navigation.confirming.isEmpty());
    assertTrue(navigation.campaignConfirmWar.isEmpty());
    assertTrue(CampaignView.isViewingCampaign(player, 7));
    choices.verifyNoInteractions();
    pending();
    wars.when(() -> WarManager.getById(7)).thenReturn(null);
    view.handleConfirm(player, "campaign_push", "7", true);
    verify(player).sendMessage("§cWar not found.");
    assertTrue(navigation.campaignConfirmWar.isEmpty());
    choices.verifyNoInteractions();
    wars.when(() -> WarManager.getById(7)).thenReturn(war);
    pending();
    attacker.addMember("Successor");
    attacker.setLeader("Successor");
    view.handleConfirm(player, "campaign_push", "7", true);
    choices.verifyNoInteractions();
    assertTrue(navigation.confirming.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"push", "hold", "attack", "loser_peace", "surrender", "accept_peace"})
  void rejectedConfirmedActionsReportFailureWithoutNavigating(String action) {
    prepareAction(action);
    pending();
    view.handleConfirm(player, "campaign_" + action, "7", true);
    verify(player)
        .sendMessage(
            switch (action) {
              case "push" -> "§cCould not push.";
              case "hold" -> "§cCould not hold the front.";
              case "attack" -> "§cCould not schedule attack.";
              case "surrender" -> "§cCould not surrender.";
              case "loser_peace", "accept_peace" -> "§cCould not accept white peace.";
              default -> throw new IllegalArgumentException(action);
            });
    verify(navigation, never()).warList(any());
    assertTrue(navigation.confirming.isEmpty());
    assertTrue(navigation.campaignConfirmWar.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"push", "hold", "attack", "loser_peace", "surrender", "accept_peace"})
  void acceptedConfirmedActionsNavigateAccordingToWhetherTheWarContinues(String action) {
    prepareAction(action);
    success(action, false);
    pending();
    view.handleConfirm(player, "campaign_" + action, "7", true);
    verify(player)
        .sendMessage(
            switch (action) {
              case "push" -> "§aOffensive continues.";
              case "hold" -> "§aFront held. White peace proposed.";
              case "attack" -> "§aAttack scheduled at the held front.";
              case "surrender" -> "§aYou have surrendered.";
              case "loser_peace", "accept_peace" -> "§aWhite peace accepted.";
              default -> throw new IllegalArgumentException(action);
            });
    assertTrue(navigation.confirming.isEmpty());
    assertTrue(navigation.campaignConfirmWar.isEmpty());
    if (List.of("push", "hold", "attack").contains(action)) {
      assertTrue(CampaignView.isViewingCampaign(player, 7));
      verify(navigation, never()).warList(player);
    } else verify(navigation).warList(player);
  }

  @ParameterizedTest
  @ValueSource(strings = {"push", "hold", "attack"})
  void choicesThatEndTheWarReturnToTheWarList(String action) {
    prepareAction(action);
    success(action, true);
    pending();
    view.handleConfirm(player, "campaign_" + action, "7", true);
    verify(navigation).warList(player);
    verify(player).playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    assertTrue(navigation.campaignConfirmWar.isEmpty());
  }

  @ParameterizedTest
  @EnumSource(value = RetreatResult.class, names = "SUCCESS", mode = EnumSource.Mode.EXCLUDE)
  void retreatFailuresGiveTheSpecificReasonAndKeepTheCampaignOpen(RetreatResult result) {
    prepareAction("retreat");
    pending();
    retreat
        .when(() -> CampaignRetreatService.concedeActiveSlot(war, attacker, NOW))
        .thenReturn(ConcedeResult.rejected(result));
    view.handleConfirm(player, "campaign_retreat", "7", true);
    verify(player)
        .sendMessage(
            switch (result) {
              case REJECTED_VOTE_CLOSED -> "§cRetreat is closed for this battle day.";
              case REJECTED_NOT_LEADER -> "§cOnly the pushed coalition war leader can retreat.";
              case REJECTED_POST_BATTLE_CHOICE -> "§cResolve the post-battle choice first.";
              case REJECTED_NO_ACTIVE_SLOT -> "§cNo active battle slot to concede.";
              case REJECTED_NOT_ELIGIBLE -> "§cYou cannot retreat right now.";
              default -> throw new IllegalArgumentException(result.name());
            });
    verify(navigation, never()).warList(player);
    assertTrue(navigation.campaignConfirmWar.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successfulRetreatRefreshesTheCampaignOrShowsTheWarEnded(boolean ended) {
    prepareAction("retreat");
    pending();
    retreat
        .when(() -> CampaignRetreatService.concedeActiveSlot(war, attacker, NOW))
        .thenReturn(
            new ConcedeResult(
                RetreatResult.SUCCESS,
                ended ? Optional.of(WarEndReason.DEFENDER_VICTORY) : Optional.empty()));
    view.handleConfirm(player, "campaign_retreat", "7", true);
    verify(player).sendMessage(ended ? "§aSlot conceded. War ended." : "§aActive slot retreated.");
    if (ended) verify(navigation).warList(player);
    else assertTrue(CampaignView.isViewingCampaign(player, 7));
  }

  @Test
  void confirmationRechecksRetreatAndPushCapabilityAndIgnoresUnknownActions() {
    pending();
    view.handleConfirm(player, "campaign_retreat", "7", true);
    verify(player).sendMessage(contains("cannot retreat"));
    retreat.verify(() -> CampaignRetreatService.concedeActiveSlot(any(), any(), any()), never());
    prepareAction("push");
    capability
        .when(() -> CampaignCapabilityService.canMountOffensiveAfterPush(eq(war), any()))
        .thenReturn(false);
    pending();
    view.handleConfirm(player, "campaign_push", "7", true);
    choices.verify(() -> CampaignChoiceService.applyPush(any()), never());
    verify(player).sendMessage("§cCould not push.");
    pending();
    view.handleConfirm(player, "unknown", "7", true);
    assertTrue(navigation.confirming.isEmpty());
    verify(navigation, never()).warList(player);
  }

  private void removePlayerFromFaction() {
    attacker.addMember("Successor");
    attacker.setLeader("Successor");
    attacker.getOrCreateMainGuild().kick("Leader");
  }

  private void prepareAction(String action) {
    war.setPostBattleChoiceResolved(false);
    if (List.of("push", "hold").contains(action)) {
      war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
      war.setPostBattleWinnerCoalition(CampaignCoalition.AGGRESSOR);
    }
    if (List.of("attack", "loser_peace").contains(action)) {
      war.setPostBattleChoicePhase(PostBattleChoicePhase.LOSER_ATTACK_PEACE);
      war.setPostBattleWinnerCoalition(CampaignCoalition.DEFENDER);
    }
    if (action.equals("retreat"))
      retreat.when(() -> CampaignRetreatService.canRetreat(war, attacker, NOW)).thenReturn(true);
    if (action.equals("accept_peace"))
      peace.when(() -> WhitePeaceService.acceptWhitePeace(war, attacker)).thenReturn(true);
  }

  private ItemStack action(String action) {
    return switch (action) {
      case "retreat" -> creator.createRetreatButton(war);
      case "surrender" -> creator.createSurrenderButton(war);
      case "accept_peace" -> creator.createAcceptPeaceButton(war);
      case "push" -> creator.createPushButton(war);
      case "hold" -> creator.createHoldButton(war);
      case "attack" -> creator.createLoserAttackButton(war);
      case "loser_peace" -> creator.createLoserAcceptPeaceButton(war);
      default -> throw new IllegalArgumentException(action);
    };
  }

  private void success(String action, boolean ends) {
    org.mockito.stubbing.Answer<Boolean> apply =
        call -> {
          if (ends) wars.when(() -> WarManager.getById(7)).thenReturn(null);
          return true;
        };
    switch (action) {
      case "push" -> choices.when(() -> CampaignChoiceService.applyPush(war)).thenAnswer(apply);
      case "hold" -> choices.when(() -> CampaignChoiceService.applyHold(war)).thenAnswer(apply);
      case "attack" ->
          choices.when(() -> CampaignChoiceService.applyLoserAttack(war)).thenAnswer(apply);
      case "loser_peace" ->
          choices.when(() -> CampaignChoiceService.applyLoserAcceptPeace(war)).thenAnswer(apply);
      case "surrender" ->
          resolution.when(() -> WarResolutionService.surrender(war, attacker)).thenAnswer(apply);
      case "accept_peace" ->
          choices
              .when(() -> CampaignChoiceService.acceptWhitePeaceAndEnd(war, attacker))
              .thenAnswer(apply);
      default -> throw new IllegalArgumentException(action);
    }
  }

  private void pending() {
    navigation.confirming.put(player, attacker);
    navigation.campaignConfirmWar.put(player, 7);
  }

  private Inventory open() {
    Inventory inventory =
        fixture.ui.inventory(new CampaignInventoryHolder(7, SFGUI.CAMPAIGN_VIEW), 54, "Campaign");
    player.openInventory(inventory);
    return inventory;
  }

  private Inventory top() {
    return player.getOpenInventory().getTopInventory();
  }

  private void click(int slot) {
    Inventory inventory = top();
    InventoryClickEvent event = fixture.ui.click(player, slot);
    view.click(event, inventory, player);
    assertTrue(event.isCancelled());
  }

  private ItemStack tag(String key, int value) {
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.INTEGER, value);
    item.setItemMeta(meta);
    return item;
  }

  private Integer number(ItemStack item, String key) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.INTEGER);
  }

  private String string(ItemStack item, String key) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.STRING);
  }

  private static String name(ItemStack item) {
    return ChatColor.stripColor(item.getItemMeta().getDisplayName());
  }

  private static String lore(ItemStack item) {
    return item.getItemMeta().getLore() == null
        ? ""
        : String.join(
            "\n", item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList());
  }

  private static void assertLore(ItemStack item, String text) {
    assertTrue(lore(item).contains(text), () -> "Expected " + text + " in " + lore(item));
  }
}
