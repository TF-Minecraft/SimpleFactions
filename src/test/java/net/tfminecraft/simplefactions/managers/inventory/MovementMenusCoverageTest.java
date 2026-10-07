package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.tfminecraft.simplefactions.enums.Member;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.MovementCrackdownQueries;
import net.tfminecraft.simplefactions.government.movement.MovementOutcomeService;
import net.tfminecraft.simplefactions.government.movement.MovementOutcomeSource;
import net.tfminecraft.simplefactions.government.movement.Phase;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.Pool;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.utils.LoreWriter;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarStartService;
import net.tfminecraft.simplefactions.war.resolution.CouncilPeaceQueries;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Real menus, proposals, causes and support pools; faction/domain services are scoped boundaries. */
class MovementMenusCoverageTest {
  private GuiTestFixture ui;
  private Player player;
  private Player ruler;
  private Faction faction;
  private Government government;
  private Movement movement;
  private Cause cause;
  private final List<Cause> causes = new ArrayList<>();
  private final List<Movement> movements = new ArrayList<>();
  private final List<Faction> backers = new ArrayList<>();
  private final Map<String, Player> online = new HashMap<>();
  private final Pool supporters = new Pool();
  private final AtomicReference<String> leader = new AtomicReference<>("Alice");
  private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.REBELLIOUS);
  private InventoryManager manager;
  private MovementCreator creator;
  private MovementView view;
  private final List<MockedStatic<?>> mocks = new ArrayList<>();
  private MockedStatic<FactionManager> lookup;
  private MockedStatic<MovementCrackdownQueries> crackdown;
  private MockedStatic<MovementOutcomeService> outcomes;
  private MockedStatic<CivilWarStartService> civilWars;
  private MockedStatic<LoreWriter> lore;
  private ItemCreator itemCreator;
  private List<Faction> previousFactions;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> result = mockStatic(type);
    mocks.add(result);
    return result;
  }

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    previousFactions = FactionManager.factions;
    FactionManager.factions = new ArrayList<>();
    player = ui.player("Alice");
    ruler = ui.player("Bob");
    online.put("Alice", player);
    online.put("Bob", ruler);
    when(Bukkit.getPlayer(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
    faction = mock(Faction.class);
    when(faction.getId()).thenReturn("home");
    when(faction.getName()).thenReturn("Homeland");
    when(faction.getLeader()).thenReturn("Bob");
    when(faction.getMembers()).thenAnswer(call -> new ArrayList<>(List.of("Alice", "Bob", "Cara")));
    when(faction.getVassalMembers()).thenReturn(List.of("Dan"));
    when(faction.canBecomeLeader(anyString())).thenReturn(true);
    when(faction.getRelationToFaction(anyString())).thenReturn(Member.MEMBER);
    government = mock(Government.class);
    when(government.getFaction()).thenReturn(faction);
    when(faction.getGovernment()).thenReturn(government);
    when(government.getMovements()).thenReturn(movements);
    FactionManager.factions.add(faction);
    lookup = scoped(FactionManager.class);
    lookup.when(() -> FactionManager.getByString("home")).thenReturn(faction);
    lookup.when(() -> FactionManager.getByMember(anyString())).thenReturn(faction);
    movement = mock(Movement.class);
    when(movement.getId()).thenReturn("movement-1");
    when(movement.getFaction()).thenReturn(faction);
    when(movement.getLeader()).thenAnswer(call -> leader.get());
    when(movement.hasLeader()).thenAnswer(call -> leader.get() != null);
    when(movement.isLeader(nullable(String.class))).thenAnswer(call -> leader.get() != null && leader.get().equalsIgnoreCase(call.getArgument(0)));
    doAnswer(call -> { leader.set(call.getArgument(0)); return null; }).when(movement).setLeader(nullable(String.class));
    when(movement.canBeLeader("Alice")).thenReturn(true);
    when(movement.getPhase()).thenAnswer(call -> phase.get());
    doAnswer(call -> { phase.set(call.getArgument(0)); return null; }).when(movement).setPhase(any());
    when(movement.getOrganization()).thenReturn(100.0);
    when(movement.getMaxOrganization()).thenReturn(100.0);
    when(movement.getOrganizationGain()).thenReturn(5.5);
    when(movement.getPower()).thenReturn(60.0);
    when(movement.getStabilityEffect()).thenReturn(12.5);
    when(movement.getCauses()).thenReturn(causes);
    when(movement.getSupporters()).thenReturn(supporters);
    when(movement.getForeignBackers()).thenReturn(backers);
    when(movement.getAllMembers()).thenAnswer(call -> {
      List<String> names = new ArrayList<>(supporters.getAllMembers());
      for (Cause entry : causes) if (entry != null) names.addAll(entry.getFullMemberList());
      return names;
    });
    when(movement.quickJoinCheck(any())).thenReturn(true);
    when(movement.getJoiningAs(any())).thenAnswer(call -> ((Player) call.getArgument(0)).getName());
    doAnswer(call -> {
      Object joining = call.getArgument(0);
      Cause selected = call.getArgument(1);
      if (selected != null) selected.leave(joining);
      else if (joining instanceof String citizen) supporters.removeCitizen(citizen);
      else if (joining instanceof Guild guild) supporters.removeGuild(guild);
      else if (joining instanceof Faction supporter) supporters.removeFaction(supporter);
      return null;
    }).when(movement).leave(any(), nullable(Cause.class));
    doAnswer(call -> { backers.remove(call.getArgument(0)); return null; }).when(movement).leaveAsForeignBacker(any());
    lookup.when(() -> FactionManager.getMovementById("movement-1")).thenReturn(movement);
    when(government.getMovementById("movement-1")).thenReturn(movement);
    movements.add(movement);
    cause = cause(Action.CHANGE_LEADER);
    cause.getProposal().setTarget("Cara");
    crackdown = scoped(MovementCrackdownQueries.class);
    crackdown.when(() -> MovementCrackdownQueries.canCrush(faction, movement)).thenReturn(true);
    crackdown.when(() -> MovementCrackdownQueries.denyReason(faction, movement)).thenReturn("Movement is too strong");
    outcomes = scoped(MovementOutcomeService.class);
    civilWars = scoped(CivilWarStartService.class);
    scoped(LogManager.class);
    lore = scoped(LoreWriter.class);
    lore.when(() -> LoreWriter.applyProposalLore(any(), anyList(), any(), any(), any())).thenAnswer(call -> {
      List<String> lines = call.getArgument(1);
      lines.add("Proposal effects preview");
      return null;
    });
    // The real query is pure for this action classification; all other war work stays at its service boundary.
    scoped(CouncilPeaceQueries.class).when(() -> CouncilPeaceQueries.isWarEndAction(any())).thenAnswer(call ->
        call.getArgument(0) == Action.WHITE_PEACE || call.getArgument(0) == Action.SURRENDER);
    ItemAPI api = mock(ItemAPI.class);
    itemCreator = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(itemCreator);
    when(itemCreator.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    when(itemCreator.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(api);
    manager = mock(InventoryManager.class);
    manager.confirming = new HashMap<>();
    manager.governmentView = mock(GovernmentView.class);
    when(manager.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.BARRIER));
    creator = new MovementCreator();
    view = new MovementView(manager);
  }

  @AfterEach
  void cleanup() {
    for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
    FactionManager.factions = previousFactions;
    ui.close();
  }

  private Cause cause(Action action) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("name", action.name());
    config.set("description", List.of("Apply the requested political change"));
    config.set("pools", List.of("citizens", "guilds", "factions"));
    PoliticalAction political = new PoliticalAction(action.name(), config);
    Proposal proposal = new Proposal("Alice", government);
    proposal.setPoliticalActionProposal(political);
    Cause created = new Cause(movement, proposal, "Alice");
    causes.add(created);
    return created;
  }

  private Inventory top(Player viewer) { return viewer.getOpenInventory().getTopInventory(); }
  private Inventory top() { return top(player); }
  private SFGUI menu(Player viewer) { return ((SFInventoryHolder) top(viewer).getHolder()).getType(); }
  private void click(Player viewer, int slot) {
    Inventory inventory = top(viewer);
    InventoryClickEvent event = ui.click(viewer, slot);
    view.click(event, inventory, viewer);
    assertTrue(event.isCancelled());
  }
  private void click(int slot) { click(player, slot); }
  private static String text(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    return ChatColor.stripColor(meta.getDisplayName() + "\n" + String.join("\n", meta.getLore() == null ? List.of() : meta.getLore()));
  }
  private String id(ItemStack item) { return item.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING); }

  @Test
  void leaderlessMovementStillOpensAndAllowsEligiblePlayerToTakeLeadership() {
    leader.set(null);
    assertDoesNotThrow(() -> view.movementView(player, faction, movement, null));
    assertEquals(Material.GREEN_CONCRETE, top().getItem(10).getType());
    assertTrue(text(top().getItem(28)).contains("Only the movement leader"));
    click(10);
    assertEquals("Alice", leader.get());
    assertEquals(Material.PLAYER_HEAD, top().getItem(10).getType());
  }

  @Test
  void removedCauseDoesNotCrashAnAlreadyOpenDetailsMenu() {
    view.causeView(player, faction, movement, cause, null);
    causes.clear();
    assertDoesNotThrow(() -> click(14));
    verify(movement, never()).leave(any(), any());
    lookup.verify(() -> FactionManager.requestMovementJoin(any(), any(), any(), any()), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"details", "list", "target"})
  void aRemovedDisplayedCauseCannotBeReplacedByTheCauseThatMovesIntoItsIndex(String screen) {
    Cause replacement = cause(Action.SNAP_ELECTIONS);
    if (screen.equals("details")) view.causeView(player, faction, movement, cause, null);
    else if (screen.equals("list")) view.causesView(player, faction, movement, null);
    else view.targetSelectionView(player, faction, movement, cause, null);
    Inventory original = top();
    causes.remove(cause);
    click(screen.equals("details") ? 14 : 10);
    verify(movement, never()).leave(any(), eq(replacement));
    lookup.verify(() -> FactionManager.requestMovementLeaderTarget(any(), any(), eq(replacement), anyString()), never());
    assertSame(original, top());
    assertTrue(replacement.getFullMemberList().contains("Alice"));
  }

  @Test
  void removingAnEarlierCauseDoesNotRedirectTheSurvivingCauseDetailsAction() {
    Cause displayed = cause(Action.SNAP_ELECTIONS);
    Cause later = cause(Action.NATIONHOOD);
    view.causeView(player, faction, movement, displayed, null);
    causes.remove(cause);
    click(14);
    verify(movement).leave("Alice", displayed);
    verify(movement, never()).leave("Alice", later);
    assertFalse(displayed.getFullMemberList().contains("Alice"));
    assertTrue(later.getFullMemberList().contains("Alice"));
  }

  @Test
  void joiningCauseRequestsTheSelectedCauseRatherThanGeneralSupport() {
    cause.getPool().getCitizens().clear();
    view.causeView(player, faction, movement, cause, null);
    click(14);
    lookup.verify(() -> FactionManager.requestMovementJoin(player, movement, "member", cause));
  }

  @Test
  void overviewRendersStateAndNavigatesFromFactionListThroughCauseDetails() {
    supporters.addCitizen("Cara");
    view.movementListView(player, faction, null);
    assertTrue(text(top().getItem(10)).contains("Organization: 100"));
    assertEquals("movement-1", id(top().getItem(10)));
    click(10);
    assertEquals(SFGUI.MOVEMENT_VIEW, menu(player));
    assertTrue(text(top().getItem(11)).contains("+5.5"));
    assertTrue(text(top().getItem(15)).contains("Cara"));
    assertNotNull(top().getItem(19));
    assertNotNull(top().getItem(34));
    assertNull(top().getItem(25));
    click(13);
    assertEquals(SFGUI.CAUSES_VIEW, menu(player));
    assertTrue(text(top().getItem(10)).contains("Proposal effects preview"));
    click(10);
    assertEquals(SFGUI.CAUSE_VIEW, menu(player));
    assertEquals(0, ((SFInventoryHolder) top().getHolder()).getPage());
    assertTrue(text(top().getItem(12)).contains("Proposal effects preview"));
    assertTrue(text(top().getItem(14)).contains("Leave Cause"));
    assertTrue(text(top().getItem(28)).contains("Cara"));
    lore.verify(() -> LoreWriter.applyProposalLore(eq(cause.getProposal()), anyList(), eq(player), eq(faction), any()), atLeastOnce());
  }

  @Test
  void allPublicViewsRedrawInPlaceAndKeepTheRightActionMetadata() {
    view.movementView(ruler, faction, movement, null);
    Inventory overview = top(ruler);
    view.movementView(ruler, faction, movement, overview);
    assertSame(overview, top(ruler));
    assertNotNull(overview.getItem(25));
    assertNull(overview.getItem(19));
    view.causesView(player, faction, movement, null);
    Inventory causesInventory = top();
    view.causesView(player, faction, movement, causesInventory);
    assertSame(causesInventory, top());
    assertEquals("movement-1", id(top().getItem(10)));
    view.causeView(player, faction, movement, cause, null);
    Inventory causeInventory = top();
    view.causeView(player, faction, movement, cause, causeInventory);
    assertSame(causeInventory, top());
    view.targetSelectionView(player, faction, movement, cause, null);
    Inventory targets = top();
    view.targetSelectionView(player, faction, movement, cause, targets);
    assertSame(targets, top());
    assertEquals("Alice", id(targets.getItem(10)));
    view.movementListView(player, faction, null);
    Inventory list = top();
    view.movementListView(player, faction, list);
    assertSame(list, top());
    view.demandsView(ruler, faction, movement, null);
    Inventory demands = top(ruler);
    view.demandsView(ruler, faction, movement, demands);
    assertSame(demands, top(ruler));
    assertTrue(text(demands.getItem(29)).contains("12.5"));
    view.crackdownView(player, faction, movement, null);
    Inventory disband = top();
    view.crackdownView(player, faction, movement, disband);
    assertSame(disband, top());
    assertTrue(text(disband.getItem(22)).contains("Civil War"));
    assertEquals(Material.RED_CONCRETE, disband.getItem(33).getType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"citizen", "guild", "vassal"})
  void leavingGeneralSupportUsesTheRepresentedEntityAndDropsMovementLeadership(String kind) {
    Object represented = player.getName();
    if (kind.equals("citizen")) supporters.addCitizen("Alice");
    if (kind.equals("guild")) {
      Guild guild = mock(Guild.class);
      when(guild.getMembers()).thenReturn(List.of("Alice"));
      when(guild.getName()).thenReturn("Artisans");
      when(faction.getRelationToFaction("Alice")).thenReturn(Member.GUILD_MEMBER);
      lookup.when(() -> FactionManager.getGuildByMember("Alice")).thenReturn(guild);
      supporters.addGuild(guild);
      represented = guild;
    }
    if (kind.equals("vassal")) {
      when(faction.getRelationToFaction("Alice")).thenReturn(Member.VASSAL_LEADER);
      supporters.addFaction(faction);
      represented = faction;
    }
    view.movementView(player, faction, movement, null);
    click(15);
    verify(movement).leave(represented, null);
    assertTrue(supporters.getAllMembers().isEmpty());
    assertNull(leader.get());
    assertTrue(text(top().getItem(15)).contains("Join as Supporter"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"quick", "identity", "blocked", "allowed"})
  void generalSupportRequestsRecheckEligibilityAndExplainDenials(String scenario) {
    if (scenario.equals("quick")) when(movement.quickJoinCheck(player)).thenReturn(false);
    if (scenario.equals("identity")) when(movement.getJoiningAs(player)).thenReturn(null);
    if (scenario.equals("blocked")) when(movement.joinBlockReason("Alice", null, false)).thenReturn("Already committed elsewhere");
    view.movementView(player, faction, movement, null);
    click(15);
    if (scenario.equals("allowed")) lookup.verify(() -> FactionManager.requestMovementJoin(player, movement, "supporter", null));
    else lookup.verify(() -> FactionManager.requestMovementJoin(any(), any(), anyString(), nullable(Cause.class)), never());
    if (scenario.equals("blocked")) verify(player).sendMessage("Already committed elsewhere");
  }

  @ParameterizedTest
  @ValueSource(strings = {"join", "blocked", "leave", "supporter", "own", "factionless"})
  void foreignBackersCannotLeaveWhileSupportingAndNewRequestsRespectRules(String scenario) {
    Faction foreign = mock(Faction.class);
    when(foreign.getId()).thenReturn("foreign");
    when(foreign.getName()).thenReturn("Overseas");
    lookup.when(() -> FactionManager.getByMember("Alice")).thenReturn(scenario.equals("own") ? faction : scenario.equals("factionless") ? null : foreign);
    if (scenario.equals("leave") || scenario.equals("supporter")) backers.add(foreign);
    if (scenario.equals("supporter")) supporters.addCitizen("Alice");
    if (scenario.equals("blocked")) when(movement.foreignBackerBlockReason(foreign, false)).thenReturn("Backer prohibited");
    view.movementView(player, faction, movement, null);
    click(16);
    if (scenario.equals("join")) lookup.verify(() -> FactionManager.requestMovementJoin(player, movement, "foreign_backer", null));
    else lookup.verify(() -> FactionManager.requestMovementJoin(any(), any(), anyString(), nullable(Cause.class)), never());
    if (scenario.equals("leave")) { verify(movement).leaveAsForeignBacker(foreign); assertTrue(backers.isEmpty()); }
    if (scenario.equals("supporter")) { assertEquals(List.of(foreign), backers); verify(player).sendMessage(contains("while you are a supporter")); }
    if (scenario.equals("blocked")) verify(player).sendMessage("Backer prohibited");
  }

  @ParameterizedTest
  @ValueSource(strings = {"online", "missingTarget", "offline", "lowOrganization", "notLeader"})
  void sendingDemandsRequiresLeadershipOrganizationTargetsAndAnOnlineRuler(String scenario) {
    view.movementView(player, faction, movement, null);
    if (scenario.equals("missingTarget")) cause.getProposal().setTarget(null);
    if (scenario.equals("offline")) when(ruler.isOnline()).thenReturn(false);
    if (scenario.equals("lowOrganization")) when(movement.getOrganization()).thenReturn(99.0);
    if (scenario.equals("notLeader")) leader.set("Cara");
    click(19);
    if (scenario.equals("online")) {
      assertEquals(SFGUI.MOVEMENT_DEMANDS, menu(ruler));
      verify(player).sendMessage(contains("Demands sent to Bob"));
    } else {
      verify(ruler, never()).openInventory(any(Inventory.class));
      if (scenario.equals("missingTarget")) verify(player).sendMessage(contains("lack a target"));
      if (scenario.equals("offline")) verify(player).sendMessage(contains("must be online"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"allowed", "blocked", "leaderless", "offline", "notRuler"})
  void disbandDemandsRecheckCrackdownRulesAndCurrentLeaders(String scenario) {
    view.movementView(ruler, faction, movement, null);
    if (scenario.equals("blocked")) crackdown.when(() -> MovementCrackdownQueries.canCrush(faction, movement)).thenReturn(false);
    if (scenario.equals("leaderless")) leader.set(null);
    if (scenario.equals("offline")) online.remove("Alice");
    if (scenario.equals("notRuler")) when(faction.getLeader()).thenReturn("Cara");
    click(ruler, 25);
    if (scenario.equals("allowed")) {
      assertEquals(SFGUI.MOVEMENT_CRACKDOWN, menu(player));
      verify(ruler).sendMessage(contains("Disband demand sent"));
    } else {
      verify(player, never()).openInventory(any(Inventory.class));
      if (!scenario.equals("notRuler")) verify(ruler).sendMessage(anyString());
    }
  }

  @Test
  void phaseCardsShowCurrentPreviousNextAndUnavailableStatesAndOnlyAllowedClickChangesPhase() {
    phase.set(Phase.PRESSURING);
    when(movement.getOrganization()).thenReturn(40.0);
    when(movement.canChangeToPhase(Phase.AGITATED)).thenReturn(true);
    view.movementView(player, faction, movement, null);
    assertEquals(Material.YELLOW_CONCRETE, top().getItem(28).getType());
    assertEquals(Material.GREEN_CONCRETE, top().getItem(29).getType());
    assertEquals(Material.YELLOW_CONCRETE, top().getItem(30).getType());
    assertEquals(Material.RED_CONCRETE, top().getItem(31).getType());
    click(30);
    assertEquals(Phase.AGITATED, phase.get());
    verify(player).sendMessage(contains("Phase changed"));
    when(movement.getOrganization()).thenReturn(20.0);
    assertTrue(text(creator.createPhaseItem(player, Phase.REBELLIOUS, movement)).contains("Organization 20.0/75"));
    assertTrue(text(creator.createPhaseItem(ruler, Phase.REBELLIOUS, movement)).contains("Only the movement leader"));
    view.movementView(player, faction, movement, null);
    click(29);
    verify(movement, times(1)).setPhase(any());
  }

  @Test
  void endMovementUsesConfirmationAndRechecksLeadershipAtConfirmTime() {
    view.movementView(player, faction, movement, null);
    click(34);
    assertSame(faction, manager.confirming.get(player));
    verify(manager).confirmEndMovementView(player, movement);
    assertTrue(text(creator.createEndMovementConfirmItem(movement)).contains("remove all supporters and causes"));
    view.handleEndConfirm(player, "movement-1", false);
    assertEquals(SFGUI.MOVEMENT_VIEW, menu(player));
    verify(government, never()).endMovement(any());
    leader.set("Cara");
    view.handleEndConfirm(player, "movement-1", true);
    verify(government, never()).endMovement(any());
    leader.set("Alice");
    view.handleEndConfirm(player, "movement-1", true);
    verify(government).endMovement(movement);
    assertEquals(SFGUI.MOVEMENT_LIST, menu(player));
    verify(player).sendMessage(contains("has been disbanded"));
  }

  @Test
  void removedMovementConfirmationReturnsToFactionListOrClosesWithoutMutation() {
    manager.confirming.put(player, faction);
    view.handleEndConfirm(player, "removed", true);
    assertEquals(SFGUI.MOVEMENT_LIST, menu(player));
    assertFalse(manager.confirming.containsKey(player));
    view.handleEndConfirm(player, "removed", true);
    verify(player).closeInventory();
    verify(government, never()).endMovement(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"supporter", "notSupporter", "frozen", "full"})
  void newCauseSlotsAppearOnlyForEligibleSupportersWithRoom(String scenario) {
    if (!scenario.equals("notSupporter")) supporters.addCitizen("Alice");
    if (scenario.equals("frozen")) when(movement.isFrozen()).thenReturn(true);
    if (scenario.equals("full")) { cause(Action.NATIONHOOD); cause(Action.INDEPENDENCE); }
    view.causesView(player, faction, movement, null);
    if (scenario.equals("supporter")) {
      assertEquals(Material.YELLOW_CONCRETE, top().getItem(11).getType());
      click(11);
      verify(manager).proposalView(player, faction, null);
    } else if (!scenario.equals("full")) {
      assertEquals(Material.GRAY_CONCRETE, top().getItem(11).getType());
      click(11);
      verify(manager, never()).proposalView(any(), any(), any());
    } else assertEquals(Material.WRITABLE_BOOK, top().getItem(12).getType());
  }

  @Test
  void causeLeadershipJoinAndLeaveControlsShowAndApplyTheCurrentMembership() {
    cause.setLeader(null);
    assertEquals(Material.GREEN_CONCRETE, creator.createCauseLeaderItem(player, cause).getType());
    view.causeView(player, faction, movement, cause, null);
    click(10);
    assertEquals("Alice", cause.getLeader());
    click(14);
    verify(movement).leave("Alice", cause);
    assertTrue(cause.getFullMemberList().isEmpty());
    assertNull(cause.getLeader());
    assertNull(leader.get());
    assertEquals(Material.RED_CONCRETE, top().getItem(10).getType());
    assertTrue(text(creator.createCauseItem(cause, player, faction)).contains("No Leader"));
    assertTrue(text(creator.createDemandItem(cause, movement)).contains("Leaderless"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"identity", "quick", "blocked"})
  void causeJoinStopsAtEachEligibilityBoundary(String scenario) {
    cause.getPool().getCitizens().clear();
    if (scenario.equals("identity")) when(movement.getJoiningAs(player)).thenReturn(null);
    if (scenario.equals("quick")) when(movement.quickJoinCheck(player)).thenReturn(false);
    if (scenario.equals("blocked")) when(movement.joinBlockReason("Alice", cause, false)).thenReturn("Cause membership denied");
    view.causeView(player, faction, movement, cause, null);
    click(14);
    lookup.verify(() -> FactionManager.requestMovementJoin(any(), any(), anyString(), nullable(Cause.class)), never());
    if (scenario.equals("blocked")) verify(player).sendMessage("Cause membership denied");
  }

  @Test
  void leaderTargetPickerFiltersCandidatesAndRequestsTheSelectedEligibleMember() {
    when(faction.canBecomeLeader("Bob")).thenReturn(false);
    view.causeView(player, faction, movement, cause, null);
    click(28);
    assertEquals(SFGUI.TARGET_SELECT, menu(player));
    assertEquals("Alice", id(top().getItem(10)));
    assertEquals("Cara", id(top().getItem(11)));
    assertEquals("Dan", id(top().getItem(12)));
    click(11);
    lookup.verify(() -> FactionManager.requestMovementLeaderTarget(player, movement, cause, "Cara"));
    assertEquals(SFGUI.CAUSE_VIEW, menu(player));
    assertEquals(List.of("Alice", "Bob", "Cara"), faction.getMembers());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ineligible", "lostLeadership", "removedCause"})
  void targetPickerRevalidatesTheCauseLeaderAndTarget(String scenario) {
    view.targetSelectionView(player, faction, movement, cause, null);
    if (scenario.equals("ineligible")) when(faction.canBecomeLeader("Alice")).thenReturn(false);
    if (scenario.equals("lostLeadership")) cause.setLeader("Bob");
    if (scenario.equals("removedCause")) causes.clear();
    click(10);
    lookup.verify(() -> FactionManager.requestMovementLeaderTarget(any(), any(), any(), anyString()), never());
    if (scenario.equals("ineligible")) verify(player).sendMessage(contains("cannot become"));
  }

  @Test
  void warEndCausesUseTheWarPickerAndExplainMissingTargets() {
    Cause peace = cause(Action.WHITE_PEACE);
    view.causeView(player, faction, movement, peace, null);
    assertTrue(text(top().getItem(28)).contains("Choose War"));
    assertTrue(text(top().getItem(28)).contains("No Target Selected"));
    click(28);
    verify(manager.governmentView).warPeaceSelectView(player, faction, Action.WHITE_PEACE, true, 1, null);
    assertTrue(text(creator.createTargetButton(ruler, peace)).contains("Only the cause leader"));
    Cause elections = cause(Action.SNAP_ELECTIONS);
    view.causeView(player, faction, movement, elections, null);
    assertNull(top().getItem(28));
  }

  @ParameterizedTest
  @CsvSource({"demands,accept", "demands,reject", "demands,error", "demands,unauthorized", "crackdown,accept", "crackdown,reject", "crackdown,error", "crackdown,unauthorized"})
  void demandAndCrackdownDecisionsApplyTheRightOutcomeAndNotifyTheOtherLeader(String kind, String decision) {
    boolean demands = kind.equals("demands");
    Player decider = demands ? ruler : player;
    Player notified = demands ? player : ruler;
    if (demands) view.demandsView(decider, faction, movement, null);
    else view.crackdownView(decider, faction, movement, null);
    Inventory original = top(decider);
    if (decision.equals("error")) civilWars.when(() -> CivilWarStartService.start(movement)).thenReturn("War cannot begin now");
    if (decision.equals("unauthorized")) {
      if (demands) when(faction.getLeader()).thenReturn("Cara");
      else leader.set("Cara");
    }
    click(decider, decision.equals("accept") ? 29 : 33);
    if (decision.equals("unauthorized")) {
      outcomes.verifyNoInteractions();
      civilWars.verifyNoInteractions();
      verify(government, never()).endMovement(any());
      assertSame(original, top(decider));
    } else if (decision.equals("error")) {
      verify(decider).sendMessage("War cannot begin now");
      assertSame(original, top(decider));
      verify(notified, never()).sendMessage(anyString());
    } else {
      verify(decider).closeInventory();
      verify(notified, atLeastOnce()).sendMessage(anyString());
      if (decision.equals("accept")) {
        if (demands) outcomes.verify(() -> MovementOutcomeService.apply(movement, MovementOutcomeSource.ACCEPTED));
        else verify(government).endMovement(movement);
        civilWars.verifyNoInteractions();
      } else {
        civilWars.verify(() -> CivilWarStartService.start(movement));
        verify(decider).sendMessage(contains("civil war has begun"));
      }
    }
  }

  @Test
  void listAndTargetMenusLimitContentsToFourteenCards() {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < 20; i++) { names.add("Person" + i); movements.add(movement); }
    when(faction.getMembers()).thenAnswer(call -> new ArrayList<>(names));
    view.targetSelectionView(player, faction, movement, cause, null);
    assertEquals("Person13", id(top().getItem(25)));
    assertNull(top().getItem(26));
    view.movementListView(player, faction, null);
    assertNotNull(top().getItem(25));
    assertNull(top().getItem(26));
    assertTrue(text(creator.createSendDemandsItem(movement)).contains("Click to Send"));
    when(movement.getOrganization()).thenReturn(50.0);
    when(movement.getOrganizationGain()).thenReturn(-5.0);
    assertTrue(text(creator.createSendDemandsItem(movement)).contains("Unavailable"));
    assertTrue(text(creator.createOrganizationItem(movement)).contains("-5.0"));
    crackdown.when(() -> MovementCrackdownQueries.canCrush(faction, movement)).thenReturn(false);
    assertTrue(text(creator.createDemandDisbandItem(faction, movement)).contains("Movement is too strong"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unavailablePoliticalActionIconsUseAUsableFallback(boolean missing) {
    ItemStack unavailable = missing ? null : new ItemStack(Material.AIR);
    if (unavailable != null) when(unavailable.getItemMeta()).thenReturn(null);
    when(itemCreator.getItemFromPath("v.paper")).thenReturn(unavailable);
    ItemStack item = assertDoesNotThrow(() -> creator.createCauseProposalItem(cause, player, faction));
    assertEquals(Material.PAPER, item.getType());
    assertTrue(text(item).contains("Proposal effects preview"));
    assertEquals("movement-1", id(item));
  }

  @Test
  void providerItemsWithoutMetadataUseAnIndependentUsableProposalCard() {
    // The item provider is external. A nonempty custom stack may still fail to supply metadata.
    ItemStack providerItem = mock(ItemStack.class);
    ItemStack providerCopy = mock(ItemStack.class);
    when(providerItem.getType()).thenReturn(Material.PAPER);
    when(providerCopy.getType()).thenReturn(Material.PAPER);
    when(providerItem.clone()).thenReturn(providerCopy);
    when(itemCreator.getItemFromPath("v.paper")).thenReturn(providerItem);
    ItemStack result = creator.createCauseProposalItem(cause, player, faction);
    assertNotSame(providerItem, result);
    assertNotSame(providerCopy, result);
    assertEquals(Material.PAPER, result.getType());
    assertTrue(text(result).contains("Proposal effects preview"));
    assertEquals("movement-1", id(result));
    verify(providerItem, never()).setItemMeta(any());
    verify(providerCopy, never()).setItemMeta(any());
  }

  @Test
  void leaderlessIneligiblePlayersSeeAnExplanationAndCannotClaimLeadership() {
    leader.set(null);
    when(movement.canBeLeader("Alice")).thenReturn(false);
    view.movementView(player, faction, movement, null);
    assertEquals(Material.RED_CONCRETE, top().getItem(10).getType());
    assertTrue(text(top().getItem(10)).contains("lead a cause"));
    click(10);
    assertNull(leader.get());
  }

  @Test
  void stalePhaseAndDisbandControlsRecheckLeadershipAndUnknownPhaseMetadataIsHarmless() {
    phase.set(Phase.PRESSURING);
    when(movement.getOrganization()).thenReturn(40.0);
    view.movementView(player, faction, movement, null);
    leader.set("Cara");
    click(34);
    assertFalse(manager.confirming.containsKey(player));
    verify(manager, never()).confirmEndMovementView(any(), any());
    leader.set(null);
    assertDoesNotThrow(() -> click(30));
    verify(movement, never()).setPhase(any());
    leader.set("Alice");
    ItemStack phaseItem = top().getItem(30);
    ItemMeta meta = phaseItem.getItemMeta();
    meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, "REMOVED_PHASE");
    phaseItem.setItemMeta(meta);
    assertDoesNotThrow(() -> click(30));
    assertEquals(Phase.PRESSURING, phase.get());
  }

  @Test
  void obsoleteMovementListCardsAndUnrelatedCauseItemsDoNotDispatchActions() {
    view.movementListView(player, faction, null);
    when(government.getMovementById("movement-1")).thenReturn(null);
    Inventory original = top();
    click(10);
    assertSame(original, top());
    view.causeView(player, faction, movement, cause, null);
    click(53);
    assertEquals(SFGUI.CAUSE_VIEW, menu(player));
    lookup.verify(() -> FactionManager.requestMovementJoin(any(), any(), anyString(), nullable(Cause.class)), never());
  }

  @Test
  void emptyAndUnusableClicksHaveNoDomainEffects() {
    view.movementView(player, faction, movement, null);
    Inventory inventory = top();
    InventoryClickEvent empty = ui.click(player, 0);
    view.click(empty, inventory, player);
    assertFalse(empty.isCancelled());
    ItemStack air = new ItemStack(Material.AIR);
    when(air.getItemMeta()).thenReturn(null);
    inventory.setItem(0, air);
    InventoryClickEvent invalid = ui.click(player, 0);
    view.click(invalid, inventory, player);
    assertTrue(invalid.isCancelled());
    Inventory unowned = ui.inventory(null, 9, "Unowned inventory");
    unowned.setItem(0, new ItemStack(Material.PAPER));
    player.openInventory(unowned);
    InventoryClickEvent unownedClick = ui.click(player, 0);
    view.click(unownedClick, unowned, player);
    assertFalse(unownedClick.isCancelled());
    verify(movement, never()).setPhase(any());
    verify(movement, never()).leave(any(), nullable(Cause.class));
  }

  @Test
  void periodicTargetRefreshKeepsTheDisplayedCauseAfterEarlierCauseRemoval() {
    Cause displayed = cause(Action.CHANGE_LEADER);
    Cause later = cause(Action.CHANGE_LEADER);
    InventoryManager router = new InventoryManager();
    router.movementView = view;
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(player));
    view.targetSelectionView(player, faction, movement, displayed, null);
    causes.remove(cause);
    new InventoryUpdater(router).updateInventory();
    click(10);
    lookup.verify(() -> FactionManager.requestMovementLeaderTarget(player, movement, displayed, "Alice"));
    lookup.verify(() -> FactionManager.requestMovementLeaderTarget(player, movement, later, "Alice"), never());
  }

  @Test
  void periodicRefreshOfARemovedTargetCauseOpensTheCauseListWithItsOwnHolder() {
    cause(Action.CHANGE_LEADER);
    InventoryManager router = new InventoryManager();
    router.movementView = view;
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(player));
    view.targetSelectionView(player, faction, movement, cause, null);
    causes.remove(cause);
    new InventoryUpdater(router).updateInventory();
    assertEquals(SFGUI.CAUSES_VIEW, menu(player));
    assertEquals("movement-1", ((SFInventoryHolder) top().getHolder()).getId());
    lookup.verify(() -> FactionManager.requestMovementLeaderTarget(any(), any(), any(), anyString()), never());
  }

  @Test
  void targetPickerBackButtonReturnsToTheSameSurvivingCauseWithACauseHolder() {
    Cause displayed = cause(Action.CHANGE_LEADER);
    Cause later = cause(Action.NATIONHOOD);
    displayed.getProposal().setTarget("Cara");
    InventoryManager router = new InventoryManager();
    router.movementView = view;
    view.targetSelectionView(player, faction, movement, displayed, null);
    causes.remove(cause);
    InventoryClickEvent back = ui.click(player, 53);
    router.clickButton(back);
    assertTrue(back.isCancelled());
    assertEquals(SFGUI.CAUSE_VIEW, menu(player));
    assertTrue(text(top().getItem(12)).contains("Change Leader"));
    click(14);
    verify(movement).leave("Alice", displayed);
    verify(movement, never()).leave("Alice", later);
  }
}
