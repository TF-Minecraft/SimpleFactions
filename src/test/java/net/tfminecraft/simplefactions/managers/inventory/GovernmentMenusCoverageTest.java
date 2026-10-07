package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Member;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.espionage.CharacterNames;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.government.Council;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.government.election.Election;
import net.tfminecraft.simplefactions.government.handler.ProposalHandler;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.government.stability.StabilityFacts;
import net.tfminecraft.simplefactions.government.stability.StabilityReport;
import net.tfminecraft.simplefactions.government.stability.StabilityStatus;
import net.tfminecraft.simplefactions.government.stability.StabilityTuning;
import net.tfminecraft.simplefactions.government.stability.StateStability;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.laws.CanHaveLaw;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawEffect;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.loaders.PoliticalActionLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.utils.EconomicImpact;
import net.tfminecraft.simplefactions.utils.LoreWriter;
import net.tfminecraft.simplefactions.utils.Represents;
import net.tfminecraft.simplefactions.utils.Wealth;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarHostMovementRules;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.resolution.CouncilPeaceQueries;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class GovernmentMenusCoverageTest {
  private GuiTestFixture ui;
  private Player player;
  private Faction faction;
  private Government government;
  private Council council;
  private Election election;
  private ProposalHandler proposals;
  private LawHandler laws;
  private GuildHandler guildHandler;
  private TaxHandler taxes;
  private InventoryManager manager;
  private SessionManager sessions;
  private GovernmentCreator creator;
  private GovernmentView view;
  private StabilityReport report;
  private StabilityFacts facts;
  private final List<MockedStatic<?>> mocks = new ArrayList<>();
  private final Map<String, Faction> factions = new LinkedHashMap<>();
  private final Map<String, Guild> guilds = new LinkedHashMap<>();
  private List<Faction> previousFactions;
  private Map<String, String> previousIcons;
  private Map<Scope, LawEffect> previousEffects;
  private MockedStatic<FactionManager> factionLookup;
  private MockedStatic<RelationManager> relations;
  private MockedStatic<EspionageService> intelligence;
  private MockedStatic<CouncilPeaceQueries> peace;
  private MockedStatic<CanHaveLaw> canHaveLaw;
  private MockedStatic<CivilWarHostMovementRules> hostRules;
  private MockedStatic<LoreWriter> loreWriter;
  private MockedStatic<VehicleFeeView> fees;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> mocked = mockStatic(type);
    mocks.add(mocked);
    return mocked;
  }

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    player = ui.player("Alice");
    previousFactions = new ArrayList<>(FactionManager.factions);
    previousIcons = new HashMap<>(Cache.icons);
    previousEffects = new HashMap<>(Cache.baseEffects);
    FactionManager.factions.clear();
    Cache.icons.clear();
    Cache.baseEffects.clear();
    factionLookup = scoped(FactionManager.class);
    factionLookup
        .when(() -> FactionManager.getByString(anyString()))
        .thenAnswer(call -> factions.get(call.getArgument(0)));
    factionLookup
        .when(() -> FactionManager.getGuildByString(anyString()))
        .thenAnswer(call -> guilds.get(call.getArgument(0)));
    relations = scoped(RelationManager.class);
    intelligence = scoped(EspionageService.class);
    intelligence.when(() -> EspionageService.canViewExact(any(), any())).thenReturn(true);
    peace = scoped(CouncilPeaceQueries.class);
    peace
        .when(() -> CouncilPeaceQueries.isWarEndAction(any()))
        .thenAnswer(
            call -> Set.of(Action.WHITE_PEACE, Action.SURRENDER).contains(call.getArgument(0)));
    canHaveLaw = scoped(CanHaveLaw.class);
    hostRules = scoped(CivilWarHostMovementRules.class);
    loreWriter = scoped(LoreWriter.class);
    scoped(EconomicImpact.class);
    fees = scoped(VehicleFeeView.class);
    scoped(Represents.class)
        .when(() -> Represents.represents(any(), anyString()))
        .thenReturn("Artisans");
    MockedStatic<Wealth> wealth = scoped(Wealth.class);
    wealth.when(() -> Wealth.wealth(anyString())).thenReturn(125.5);
    wealth
        .when(() -> Wealth.topWealth(any(), eq(true)))
        .thenReturn(List.of("Alice", "Bob", "Cara"));
    scoped(CharacterNames.class)
        .when(() -> CharacterNames.display(any(), anyString(), any()))
        .thenAnswer(call -> call.getArgument(1));
    scoped(PoliticalActionLoader.class)
        .when(() -> PoliticalActionLoader.getByAction(any()))
        .thenAnswer(call -> new PoliticalAction(call.getArgument(0)));
    ItemAPI itemApi = mock(ItemAPI.class);
    ItemCreator items = mock(ItemCreator.class);
    when(itemApi.getCreator()).thenReturn(items);
    when(items.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    when(items.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(itemApi);
    faction = faction("home", "Home");
    government = mock(Government.class);
    council = mock(Council.class);
    election = mock(Election.class);
    proposals = mock(ProposalHandler.class);
    laws = mock(LawHandler.class);
    guildHandler = mock(GuildHandler.class);
    taxes = new TaxHandler(faction, 5, 10, 20, 15, 30);
    when(faction.getGovernment()).thenReturn(government);
    when(faction.getLawHandler()).thenReturn(laws);
    when(faction.getGuildHandler()).thenReturn(guildHandler);
    when(faction.getTaxHandler()).thenReturn(taxes);
    when(faction.getTaxRate(any(), nullable(String.class), anyBoolean()))
        .thenAnswer(
            call ->
                taxes.getTaxRate(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
    when(faction.getLeader()).thenReturn("Alice");
    when(faction.isLeader(anyString()))
        .thenAnswer(call -> faction.getLeader().equalsIgnoreCase(call.getArgument(0)));
    when(faction.getRulerTitle()).thenReturn("Governor");
    when(faction.getGovernmentString()).thenReturn("Republic");
    when(faction.getMembers()).thenAnswer(call -> new ArrayList<>(List.of("Alice", "Bob", "Cara")));
    when(government.getFaction()).thenReturn(faction);
    when(government.getCouncil()).thenReturn(council);
    when(government.getElection()).thenReturn(election);
    when(government.getTaxEfficiency()).thenReturn(0.8);
    when(government.getPower()).thenReturn(100.0);
    when(government.getMaxPower()).thenReturn(200.0);
    when(government.getPowerGain()).thenReturn(3.5);
    when(government.hasCouncil()).thenReturn(true);
    when(government.canProposeOrStartMovement(player)).thenReturn(true);
    when(government.canBeProposed(any())).thenReturn(true);
    when(council.getType()).thenReturn(Rules.APPOINTED_COUNCIL);
    when(council.getMaxSize()).thenReturn(3);
    when(council.getProposalHandler()).thenReturn(proposals);
    when(election.getPreviousVotes())
        .thenReturn(Map.of(Candidate.LEADER, Map.of(), Candidate.COUNCIL, Map.of()));
    report = new StabilityReport();
    report.stability = 100;
    report.legitimacy = 80;
    when(government.stateReport()).thenReturn(report);
    facts = new StabilityFacts();
    scoped(StateStability.class).when(() -> StateStability.facts(faction)).thenReturn(facts);
    scoped(StabilityTuning.class).when(StabilityTuning::get).thenReturn(StabilityTuning.DEFAULTS);
    sessions = mock(SessionManager.class);
    when(ui.plugin.getSessionManager()).thenReturn(sessions);
    manager = mock(InventoryManager.class);
    when(manager.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.BARRIER));
    manager.movementView = mock(MovementView.class);
    manager.vehicleFeeView = mock(VehicleFeeView.class);
    creator = new GovernmentCreator();
    view = new GovernmentView(manager);
  }

  @AfterEach
  void cleanup() {
    for (int n = mocks.size() - 1; n >= 0; n--) mocks.get(n).close();
    FactionManager.factions.clear();
    FactionManager.factions.addAll(previousFactions);
    Cache.icons.clear();
    Cache.icons.putAll(previousIcons);
    Cache.baseEffects.clear();
    Cache.baseEffects.putAll(previousEffects);
    ui.close();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaxTarget.class,
      names = {"GUILD_ID", "VASSAL_ID", "TARIFF_ID"})
  void specificTaxCardsShowTheStoredOverrideAndTheDifferentBaseRate(TaxTarget target) {
    Guild guild = guild("target", "Artisans");
    Faction other = faction("target", "Neighbour");
    taxes.setTaxRate(target, "target", 40);

    ItemStack item = creator.createSpecificTaxItem(player, faction, "target", target);

    String lore = lore(item);
    assertTrue(lore.contains("Current Rate: 40.0%"), lore);
    double base = target == TaxTarget.GUILD_ID ? 10 : target == TaxTarget.VASSAL_ID ? 20 : 30;
    assertTrue(lore.contains("Base Rate: " + base + "%"), lore);
    assertEquals("target", data(item, Keys.STRING_KEY));
    assertEquals(target.name(), data(item, Keys.SECONDARY_STRING_KEY));
  }

  @Test
  void councilMeetingClickRevalidatesLeadershipAfterTheMenuWasOpened() {
    when(council.canHostSession()).thenReturn(true);
    when(council.hasEnoughValidVoters()).thenReturn(true);
    view.governmentView(player, faction, null);
    Inventory menu = top();
    assertTrue(name(menu.getItem(23)).contains("Start Council Meeting"));
    when(faction.getLeader()).thenReturn("Bob");

    InventoryClickEvent click = ui.click(player, 23);
    view.click(click, menu, player);

    assertTrue(click.isCancelled());
    verify(sessions, never()).newSession(any(), any());
  }

  @Test
  void councilMeetingClickRevalidatesWhetherTheCouncilCanStillHost() {
    when(council.canHostSession()).thenReturn(true);
    when(council.hasEnoughValidVoters()).thenReturn(true);
    view.governmentView(player, faction, null);
    when(council.canHostSession()).thenReturn(false);
    click(23);
    verifyNoInteractions(sessions);
    verify(player, never()).closeInventory();
  }

  @Test
  void governmentSummaryDisplaysRulerElectionRulesAndSignedAdministrativePower() {
    ItemStack summary = creator.createGovernmentItem(faction);
    assertEquals("Government:", name(summary));
    assertTrue(lore(summary).contains("Governor: Alice"));
    assertTrue(lore(summary).contains("100.0/200.0"));
    assertTrue(lore(summary).contains("+3.5"));
    when(government.getPower()).thenReturn(-5.0);
    when(government.getMaxPower()).thenReturn(-1.0);
    when(government.getPowerGain()).thenReturn(-2.0);
    when(government.hasLeaderElections()).thenReturn(true);
    String negative = lore(creator.createGovernmentItem(player, faction));
    assertTrue(negative.contains("-5.0/-1.0"));
    assertTrue(negative.contains("-2.0"));
    assertTrue(negative.contains("Leader Elections: ✔"));
  }

  @Test
  void councilSummaryReportsSeatsAndRepresentedMembers() {
    assertTrue(lore(creator.createCouncilItem(faction)).contains("Council Size: 0/3"));
    when(council.getCurrentSize()).thenReturn(2);
    when(government.getCouncilMembers()).thenReturn(List.of("Bob", "Cara"));
    when(government.hasCouncilElections()).thenReturn(true);
    String summary = lore(creator.createCouncilItem(faction));
    assertTrue(summary.contains("Council Size: 2/3"));
    assertTrue(summary.contains("Council Elections: ✔"));
    assertTrue(summary.contains("Bob (Artisans)"));
    assertTrue(summary.contains("Cara (Artisans)"));
  }

  @Test
  void stabilitySummaryKeepsValidModifiersWhenThePublicModifierListContainsANullEntry() {
    when(government.getStabilityModifiers())
        .thenReturn(
            java.util.Arrays.asList(
                new StabilityModifier("Victory", 2, 1),
                null,
                new StabilityModifier("Unrest", -3, 1)));
    String summary = lore(creator.createStabilityItem(faction));
    assertTrue(summary.contains("Victory: +2%"));
    assertTrue(summary.contains("Unrest: -3%"));
    assertFalse(summary.contains("null"));
  }

  @Test
  void electionCardsShowActiveTimingAndPreviousResultsWithEligibilityAndVoteShares() {
    when(government.hasElection()).thenReturn(true);
    when(government.getTimeUntilElectionEnds()).thenReturn("2 days");
    assertTrue(lore(creator.createElectionItem(player, faction)).contains("Ends in: 2 days"));
    when(government.hasElection()).thenReturn(false);
    when(government.getTimeUntilNextElection()).thenReturn("3 days");
    when(government.getLastElectionString()).thenReturn("Yesterday");
    when(government.hasLeaderElections()).thenReturn(true);
    when(government.hasCouncilElections()).thenReturn(true);
    when(council.getType()).thenReturn(Rules.ELECTED_COUNCIL);
    when(faction.canBecomeLeader("Bob")).thenReturn(true);
    when(council.isMember("Alice")).thenReturn(true);
    when(council.canBeMember("Bob", true, false)).thenReturn(true);
    when(election.getPreviousVotes())
        .thenReturn(
            Map.of(
                Candidate.LEADER, Map.of("Alice", 6, "Bob", 3, "Cara", 1),
                Candidate.COUNCIL, Map.of("Alice", 0, "Bob", 0, "Cara", 0)));
    when(election.getWinners(any())).thenReturn(List.of("Alice", "Bob", "Cara"));
    String summary = lore(creator.createElectionItem(player, faction));
    assertTrue(summary.contains("Next Election: 3 days"));
    assertTrue(summary.contains("Last Election: Yesterday"));
    assertTrue(summary.contains("Leader Results"));
    assertTrue(summary.contains("Council Results"));
    assertTrue(summary.contains("[1] Alice (60%)"));
    assertTrue(summary.contains("[2] Bob (30%)"));
    assertTrue(summary.contains("[3] Cara (10%)"));
    assertTrue(summary.contains("[1] Alice (0%)"));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 40.0, 60.0, 80.0, 100.0})
  void stabilityCardsDescribeActualScoresAndTheirConsequences(double score) {
    report.stability = score;
    report.legitimacy = score;
    report.status = StabilityStatus.fromStability(score);
    report.illegitimate = score < 50;
    report.weakStateMalus = score == 0 ? 5.25 : 0;
    report.requiredLevels = 12.5;
    report.overextension = score == 0 ? 3 : 0;
    facts.bankrupt = score == 0;
    when(government.getStabilityModifiers())
        .thenReturn(
            List.of(
                new StabilityModifier("Victory", 2, 1), new StabilityModifier("Unrest", -3, 1)));
    String stability = lore(creator.createStabilityItem(faction));
    assertTrue(name(creator.createStabilityItem(faction)).contains(report.status.getLabel()));
    assertTrue(stability.contains("Victory: +2%"));
    assertTrue(stability.contains("Unrest: -3%"));
    if (score == 0) {
      assertTrue(stability.contains("State needs to be size 12.5"));
      assertTrue(stability.contains("Provinces: -3"));
      assertTrue(stability.contains("State is bankrupt"));
      assertTrue(stability.contains("Offensive War: Locked"));
      assertTrue(stability.contains("Title Formation: Locked"));
      assertTrue(stability.contains("Movement Organization: 2x"));
    }
    String legitimacy = lore(creator.createLegitimacyItem(faction));
    assertTrue(
        legitimacy.contains(score < 50 ? "Illegitimate" : score < 65 ? "Contested" : "Legitimate"));
    assertTrue(legitimacy.contains("Stability:"));
  }

  @Test
  void legitimacyListsGuildAndVassalStancesWithoutColorCodesInNames() {
    facts.guilds.add(body("Realm", 10, "SUPPORT", true));
    facts.guilds.add(body("§aArtisans", 5, "OPPOSE", false));
    facts.guilds.add(body("Farmers", 5, "NEUTRAL", false));
    facts.guilds.add(body("", 2, "SUPPORT", false));
    facts.vassals.add(body("Neighbour", 8, "SUPPORT", false));
    String text = lore(creator.createLegitimacyItem(faction));
    assertTrue(text.contains("Artisans: Oppose"));
    assertTrue(text.contains("Farmers: Neutral"));
    assertTrue(text.contains("A guild: Support"));
    assertTrue(text.contains("Neighbour (Vassal): Support"));
    assertFalse(text.contains("Realm:"));
  }

  @ParameterizedTest
  @EnumSource(Stance.class)
  void stanceCardsEncodeTheGuildAndUseAColorMatchingItsStance(Stance stance) {
    Guild guild = guild("artisans", "Artisans");
    when(guild.getStance(faction)).thenReturn(stance);
    ItemStack item = creator.createStanceItem(faction, guild);
    Material expected =
        stance == Stance.OPPOSE
            ? Material.RED_CONCRETE
            : stance == Stance.SUPPORT ? Material.GREEN_CONCRETE : Material.YELLOW_CONCRETE;
    assertEquals(expected, item.getType());
    assertEquals("artisans", data(item, Keys.STRING_KEY));
    assertTrue(lore(item).contains("Click to change"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void councilRefusalCardsExplainTheCurrentChoice(boolean refuses) {
    when(council.refuses("Alice")).thenReturn(refuses);
    ItemStack item = creator.createToggleRefuseButton(player, faction);
    assertEquals("home", data(item, Keys.STRING_KEY));
    assertTrue(lore(item).contains(refuses ? "refusing to join" : "open to joining"));
  }

  @ParameterizedTest
  @EnumSource(TaxTarget.class)
  void taxCategoryCardsEncodeTheTargetAndOnlyOfferAvailableProposals(TaxTarget target) {
    ItemStack available = creator.createTaxTypeItem(player, faction, target, true);
    assertEquals(target.getDisplayName(), name(available));
    assertEquals(target.name(), data(available, Keys.STRING_KEY));
    boolean specific =
        Set.of(TaxTarget.GUILD_ID, TaxTarget.VASSAL_ID, TaxTarget.TARIFF_ID).contains(target);
    assertTrue(lore(available).contains(specific ? "Click to view options" : "Current Rate:"));
    ItemStack readOnly = creator.createTaxTypeItem(player, faction, target, false);
    assertTrue(lore(readOnly).contains(specific ? "specific rates" : "Current Rate:"));
    when(government.canBeProposed(any())).thenReturn(false);
    ItemStack unavailable = creator.createTaxTypeItem(player, faction, target, true);
    if (!specific) assertTrue(lore(unavailable).contains("Another proposal is active"));
  }

  @ParameterizedTest
  @EnumSource(
      value = TaxTarget.class,
      names = {"GUILD_ID", "VASSAL_ID", "TARIFF_ID"})
  void specificTaxCardsHandleAbsentTargetsAndInheritedRates(TaxTarget target) {
    assertNull(creator.createSpecificTaxItem(player, faction, "missing", target));
    guild("target", "Artisans");
    faction("target", "Neighbour");
    when(government.canProposeOrStartMovement(player)).thenReturn(false);
    ItemStack card = creator.createSpecificTaxItem(player, faction, "target", target);
    assertTrue(lore(card).contains("No specific"));
    assertTrue(lore(card).contains("Another proposal is active"));
  }

  @Test
  void proposalEntryDistinguishesCouncilProposalsFromMovementsAndExplainsUnavailableActions() {
    when(government.isCouncilMember(player)).thenReturn(true);
    when(council.getCurrentProposals("Alice")).thenReturn(1);
    ItemStack proposal = creator.createProposalItem(player, faction);
    assertEquals("New Proposal", name(proposal));
    assertTrue(lore(proposal).contains("1/2"));
    assertTrue(lore(proposal).contains("Click to start"));
    when(government.isCouncilMember(player)).thenReturn(false);
    when(government.getMovementByMember("Alice")).thenReturn(mock(Movement.class));
    ItemStack movement = creator.createProposalItem(player, faction);
    assertEquals("New Movement", name(movement));
    assertTrue(lore(movement).contains("Potential Civil War"));
    assertTrue(lore(movement).contains("cannot start"));
    when(proposals.getProposals()).thenReturn(List.of(mock(Proposal.class)));
    when(government.getMovements()).thenReturn(List.of(mock(Movement.class), mock(Movement.class)));
    assertTrue(
        lore(creator.createProposalListItem(player, faction)).contains("Active Proposals: 1"));
    assertTrue(
        lore(creator.createMovementListItem(player, faction)).contains("Active Movements: 2"));
  }

  @ParameterizedTest
  @EnumSource(
      value = Rules.class,
      names = {"APPOINTED_COUNCIL", "WEALTH_BASED_COUNCIL", "ELECTED_COUNCIL"})
  void councilSeatsShowTheirMemberAndEnforceSequentialAppointmentInTheirPresentation(Rules type) {
    when(council.getType()).thenReturn(type);
    when(council.getMembers()).thenReturn(List.of("Bob"));
    ItemStack occupied = creator.createCouncilMemberItem(player, faction, 0);
    assertEquals(Material.PLAYER_HEAD, occupied.getType());
    assertEquals("Bob", name(occupied));
    assertEquals("Bob", data(occupied, Keys.STRING_KEY));
    assertTrue(lore(occupied).contains("125.5d"));
    assertTrue(lore(occupied).contains("Ranking: 2/3"));
    assertTrue(lore(occupied).contains("Click to replace"));
    assertEquals(
        Material.GREEN_CONCRETE, creator.createCouncilMemberItem(player, faction, 1).getType());
    ItemStack tooFar = creator.createCouncilMemberItem(player, faction, 2);
    assertEquals(Material.RED_CONCRETE, tooFar.getType());
    assertTrue(lore(tooFar).contains("Must fill seats in order"));
    ItemStack outsider = creator.createCouncilMemberItem(ui.player("Visitor"), faction, 0);
    assertFalse(lore(outsider).contains("Click to replace"));
    ItemStack candidate = creator.createPotentialMemberItem(player, faction, "Cara", 1);
    assertEquals("Cara", data(candidate, Keys.STRING_KEY));
    assertEquals(
        1,
        candidate
            .getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.INT, PersistentDataType.INTEGER));
    assertTrue(lore(candidate).contains("Ranking: 3/3"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"law", "tax", "fee", "political"})
  void proposalTypeCardsClearlyDescribeTheirAction(String type) {
    ItemStack item = creator.createProposalTypeItem(type);
    assertTrue(name(item).toLowerCase().contains(type.equals("fee") ? "vehicle fee" : type));
    assertTrue(lore(item).contains("Create a proposal"));
    Proposal proposal = mock(Proposal.class);
    when(proposal.isLawProposal()).thenReturn(type.equals("law"));
    when(proposal.isFeeProposal()).thenReturn(type.equals("fee"));
    when(proposal.getProposer()).thenReturn("Bob");
    ItemStack current = creator.createCurrentProposalItem(player, faction, proposal);
    assertTrue(lore(current).contains("Proposed by: Bob"));
    loreWriter.verify(
        () ->
            LoreWriter.applyProposalLore(eq(proposal), anyList(), eq(player), eq(faction), any()));
  }

  @Test
  void politicalAndWarCardsPreserveTheActionAndSpecificWarId() {
    ItemStack action = creator.createPoliticalProposalTypeItem(player, faction, Action.WHITE_PEACE);
    assertEquals("WHITE_PEACE", data(action, Keys.STRING_KEY));
    assertTrue(lore(action).contains("White Peace"));
    War war = mock(War.class);
    when(war.getId()).thenReturn(42);
    when(war.getName()).thenReturn("Border War");
    ItemStack target = creator.createWarPeaceSelectItem(war, Action.SURRENDER);
    assertEquals("Border War", name(target));
    assertEquals("42", data(target, Keys.STRING_KEY));
    assertTrue(lore(target).contains("Surrender"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void favourAndRepressMenusExplainGuildAndVassalEligibilityAndCurrentState(boolean favour) {
    Guild guild = guild("artisans", "Artisans");
    Faction vassal = faction("vassal", "Neighbour");
    when(vassal.getOrCreateMainGuild()).thenReturn(guild);
    when(guild.getRepressFavourCost()).thenReturn(4.0);
    assertEquals("Favour & Repress", name(creator.createFavourRepressEntryButton()));
    assertEquals("Favour", name(creator.createFavourButton()));
    assertEquals("Repress", name(creator.createRepressButton()));
    Scope guildScope = favour ? Scope.FAVOURED_GUILDS : Scope.REPRESSED_GUILDS;
    Scope vassalScope = favour ? Scope.FAVOURED_VASSALS : Scope.REPRESSED_VASSALS;
    Cache.baseEffects.put(guildScope, mock(LawEffect.class));
    Cache.baseEffects.put(vassalScope, mock(LawEffect.class));
    assertTrue(lore(creator.createGuildsTypeButton(favour)).contains("Click to view guilds"));
    assertTrue(lore(creator.createVassalsTypeButton(favour)).contains("Click to view vassals"));
    loreWriter.verify(() -> LoreWriter.writeEffect(eq(guildScope), any(), anyList()));
    loreWriter.verify(() -> LoreWriter.writeEffect(eq(vassalScope), any(), anyList()));
    when(government.canFavour(guild)).thenReturn(favour);
    when(government.canRepress(guild)).thenReturn(!favour);
    for (boolean active : new boolean[] {false, true}) {
      when(guild.isFavoured()).thenReturn(favour && active);
      when(guild.isRepressed()).thenReturn(!favour && active);
      ItemStack guildCard = creator.createFavourRepressGuildItem(player, faction, guild, favour);
      ItemStack vassalCard = creator.createFavourRepressVassalItem(player, faction, vassal, favour);
      assertEquals("artisans", data(guildCard, Keys.STRING_KEY));
      assertEquals("vassal", data(vassalCard, Keys.STRING_KEY));
      for (ItemStack card : List.of(guildCard, vassalCard)) {
        assertTrue(lore(card).contains("Click to toggle"));
        assertEquals(!active, lore(card).contains("Upkeep: 4.0 Administrative Power"));
        assertTrue(
            lore(card).contains(active ? "Currently" : favour ? "Not Favoured" : "Not Repressed"));
      }
    }
    when(government.canFavour(guild)).thenReturn(false);
    when(government.canRepress(guild)).thenReturn(false);
    for (boolean opposing : new boolean[] {false, true}) {
      when(guild.isFavoured()).thenReturn(!favour && opposing);
      when(guild.isRepressed()).thenReturn(favour && opposing);
      String blockedGuild =
          lore(creator.createFavourRepressGuildItem(player, faction, guild, favour));
      String blockedVassal =
          lore(creator.createFavourRepressVassalItem(player, faction, vassal, favour));
      assertTrue(blockedGuild.contains(favour ? "Cannot favour" : "Cannot repress"));
      assertTrue(blockedVassal.contains(favour ? "Cannot favour" : "Cannot repress"));
      if (opposing) {
        assertTrue(blockedGuild.contains(favour ? "Guild is repressed" : "Guild is favoured"));
        assertTrue(blockedVassal.contains(favour ? "Vassal is repressed" : "Vassal is favoured"));
      }
    }
  }

  @Test
  void governmentMenuRefreshesOnlyAvailableControlsWithoutOpeningAnotherInventory() {
    Guild guild = guild("artisans", "Artisans");
    factionLookup.when(() -> FactionManager.getGuildByMember("Alice")).thenReturn(guild);
    when(government.canAffectStability(guild)).thenReturn(true);
    when(council.canBeMember("Alice", true, true)).thenReturn(true);
    when(council.canHostSession()).thenReturn(true);
    when(government.hasElections()).thenReturn(true);
    when(faction.hasFactionRule(Rules.CAN_FAVOUR)).thenReturn(true);
    view.governmentView(player, faction, null);
    Inventory menu = top();
    assertMenu(SFGUI.GOVERNMENT_VIEW);
    for (int slot : new int[] {10, 11, 12, 13, 14, 15, 16, 21, 23, 24, 28, 33, 53}) {
      assertNotNull(menu.getItem(slot), "slot " + slot);
    }
    assertEquals("artisans", data(menu.getItem(28), Keys.STRING_KEY));
    when(government.canAffectStability(guild)).thenReturn(false);
    when(council.canHostSession()).thenReturn(false);
    when(government.hasElections()).thenReturn(false);
    view.governmentView(player, faction, menu);
    assertNull(menu.getItem(23));
    assertNull(menu.getItem(14));
    assertNull(menu.getItem(28));
    verify(player, times(1)).openInventory(any(Inventory.class));
  }

  @Test
  void reportedGovernmentViewDelegatesBothItsInitialOpenAndRefresh() {
    intelligence.when(() -> EspionageService.canViewExact(player, faction)).thenReturn(false);
    Inventory reported =
        ui.inventory(
            new SFInventoryHolder("home", SFGUI.GOVERNMENT_VIEW), 54, "Reported Government");
    MockedStatic<ReportedMenus> reports = scoped(ReportedMenus.class);
    reports
        .when(
            () -> ReportedMenus.open(player, "home", SFGUI.GOVERNMENT_VIEW, 54, "Government View"))
        .thenReturn(reported);
    view.governmentView(player, faction, null);
    view.governmentView(player, faction, reported);
    assertSame(reported, top());
    reports.verify(() -> ReportedMenus.government(reported, player, faction, manager), times(2));
    verify(player, times(1)).openInventory(reported);
  }

  @Test
  void councilSelectionListsOnlyEligibleCandidatesAndPreservesTheFactionRoster() {
    when(council.getMembers()).thenReturn(List.of("Bob"));
    when(faction.getVassalMembers()).thenReturn(List.of("Dana"));
    when(council.canBeMember("Alice", true, false)).thenReturn(true);
    when(council.canBeMember("Dana", true, false)).thenReturn(true);
    view.councilView(player, faction, null);
    Inventory seats = top();
    assertEquals("Bob", data(seats.getItem(10), Keys.STRING_KEY));
    assertNotNull(seats.getItem(12));
    assertNull(seats.getItem(13));
    click(10);
    assertMenu(SFGUI.COUNCIL_SELECT);
    Inventory candidates = top();
    assertEquals("Alice", data(candidates.getItem(0), Keys.STRING_KEY));
    assertEquals("Dana", data(candidates.getItem(1), Keys.STRING_KEY));
    assertNull(candidates.getItem(2));
    view.councilSelect(player, faction, candidates, 0);
    assertEquals(List.of("Alice", "Bob", "Cara"), faction.getMembers());
    click(1);
    verify(council).replaceMember(0, "Dana");
    assertMenu(SFGUI.COUNCIL_VIEW);
    view.councilView(player, faction, top());
  }

  @Test
  void nextCouncilSeatAppointsWhileGapsAndLostLeadershipDoNot() {
    when(council.canBeMember(anyString(), eq(true), eq(false))).thenReturn(true);
    view.councilView(player, faction, null);
    Inventory seats = top();
    click(11);
    assertSame(seats, top());
    verify(player).sendMessage("§cYou must fill council seats in order!");
    click(10);
    click(1);
    verify(council).addMember("Bob");
    view.councilSelect(player, faction, null, 2);
    click(0);
    verify(council, times(1)).addMember(anyString());
    view.councilSelect(player, faction, null, 0);
    when(faction.getLeader()).thenReturn("Bob");
    click(0);
    verify(council, times(1)).addMember(anyString());
    view.councilView(player, faction, null);
    Inventory denied = top();
    click(10);
    assertSame(denied, top());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void candidateWhoOptsOutAfterSelectionOpensCannotBeAppointedOrReplaceAMember(boolean replacing) {
    when(council.getMembers()).thenReturn(replacing ? List.of("Bob") : List.of());
    when(council.canBeMember("Cara", true, false)).thenReturn(true);
    view.councilSelect(player, faction, null, 0);
    assertEquals("Cara", data(top().getItem(0), Keys.STRING_KEY));
    when(council.canBeMember("Cara", true, false)).thenReturn(false);
    click(0);
    verify(council, never()).addMember(anyString());
    verify(council, never()).replaceMember(anyInt(), anyString());
  }

  @Test
  void governmentEntryButtonsRespectCurrentProposalAndCouncilState() {
    when(council.canBeMember("Alice", true, true)).thenReturn(true);
    when(council.canHostSession()).thenReturn(true);
    view.governmentView(player, faction, null);
    Inventory menu = top();
    when(government.canProposeOrStartMovement(player)).thenReturn(false);
    click(15);
    assertSame(menu, top());
    when(government.canProposeOrStartMovement(player)).thenReturn(true);
    when(government.getMovementByMember("Alice")).thenReturn(mock(Movement.class));
    click(15);
    assertSame(menu, top());
    when(government.getMovementByMember("Alice")).thenReturn(null);
    click(15);
    assertMenu(SFGUI.PROPOSAL_VIEW);
    view.governmentView(player, faction, null);
    click(21);
    verify(council).toggleRefuse("Alice");
    click(33);
    verify(manager).movementListView(player, faction, null);
    click(24);
    assertMenu(SFGUI.PROPOSALS);
    assertTrue(click(0).isCancelled());
    view.governmentView(player, faction, null);
    when(government.hasCouncil()).thenReturn(false);
    menu = top();
    click(13);
    assertSame(menu, top());
    click(23);
    verifyNoInteractions(sessions);
    when(government.hasCouncil()).thenReturn(true);
    when(council.canHostSession()).thenReturn(true);
    click(23);
    verifyNoInteractions(sessions);
    verify(player).sendMessage(contains("At least 75%"));
    when(council.hasEnoughValidVoters()).thenReturn(true);
    click(23);
    verify(sessions).newSession(player, faction);
    verify(player).closeInventory();
  }

  @Test
  void councilEntryNavigatesToTheCurrentCouncilRoster() {
    view.governmentView(player, faction, null);
    click(13);
    assertMenu(SFGUI.COUNCIL_VIEW);
    assertEquals(Material.GREEN_CONCRETE, top().getItem(10).getType());
    assertEquals(Material.BARRIER, top().getItem(53).getType());
  }

  @Test
  void stanceToggleUsesItsEncodedGuildAndRefreshesTheCurrentMenu() {
    Guild guild = guild("artisans", "Artisans");
    factionLookup.when(() -> FactionManager.getGuildByMember("Alice")).thenReturn(guild);
    when(government.canAffectStability(guild)).thenReturn(true);
    view.governmentView(player, faction, null);
    Inventory menu = top();
    click(28);
    verify(guild).switchStance();
    assertSame(menu, top());
    guilds.remove("artisans");
    click(28);
    verify(guild, times(1)).switchStance();
  }

  @Test
  void proposalCategoriesAndListsShowActualAvailableChoicesAndRefreshInPlace() {
    fees.when(() -> VehicleFeeView.anyChargeable(faction)).thenReturn(true);
    view.proposalView(player, faction, null);
    Inventory choices = top();
    assertNotNull(choices.getItem(2));
    assertNotNull(choices.getItem(3));
    click(3);
    verify(manager.vehicleFeeView).feeProposalView(player, faction, null);
    click(2);
    assertMenu(SFGUI.POLITICAL_PROPOSAL_VIEW);
    when(government.canPropose(player)).thenReturn(true);
    when(government.canProposeOrStartMovement(player)).thenReturn(false);
    peace.when(() -> CouncilPeaceQueries.isParticipatingInAny(faction)).thenReturn(true);
    view.proposalView(player, faction, choices);
    assertNotNull(choices.getItem(2));
    peace.when(() -> CouncilPeaceQueries.isParticipatingInAny(faction)).thenReturn(false);
    view.proposalView(player, faction, choices);
    assertNull(choices.getItem(2));
    Proposal pending = new Proposal("Bob", government);
    pending.setPoliticalActionProposal(new PoliticalAction(Action.INDEPENDENCE));
    when(proposals.getProposals()).thenReturn(List.of(pending));
    view.proposalList(player, faction, null);
    Inventory list = top();
    assertTrue(lore(list.getItem(0)).contains("Proposed by: Bob"));
    assertEquals(Material.BARRIER, list.getItem(26).getType());
    view.proposalList(player, faction, list);
    assertNull(list.getItem(1));
  }

  @ParameterizedTest
  @EnumSource(TaxTarget.class)
  void taxClicksOpenSpecificTargetsOrBeginTheCorrectChatEdit(TaxTarget target) {
    Inventory menu = menu(SFGUI.TAX_PROPOSAL_VIEW, 18);
    menu.setItem(0, creator.createTaxTypeItem(player, faction, target, true));
    click(0);
    if (Set.of(TaxTarget.GUILD_ID, TaxTarget.VASSAL_ID, TaxTarget.TARIFF_ID).contains(target)) {
      assertMenu(SFGUI.SPECIFIC_TAX_PROPOSAL_VIEW);
      assertEquals(target.name(), holder().getSecondaryId());
      verify(manager, never()).setChanging(any(), any(), any(), any());
    } else {
      verify(manager).setChanging(faction, player, target, "all");
      verify(player)
          .sendTitle(eq("§aTax Change"), contains(target.getDisplayName()), eq(20), eq(40), eq(20));
      verify(player).closeInventory();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = TaxTarget.class,
      names = {"GUILD_ID", "VASSAL_ID", "TARIFF_ID"})
  void specificTaxMenusFilterTheRosterAndSelectTheTargetIdentity(TaxTarget target) {
    Guild base = guild("base", "Home");
    when(base.isBase()).thenReturn(true);
    Guild member = guild("artisans", "Artisans");
    Faction subject = faction("subject", "Neighbour");
    Faction sameRealm = faction("realm", "Realm Ally");
    relations.when(() -> RelationManager.sameRealm(sameRealm, faction)).thenReturn(true);
    relations.when(() -> RelationManager.getSubjects(faction)).thenReturn(List.of(subject));
    when(guildHandler.getGuilds()).thenReturn(List.of(base, member));
    view.specificTaxProposalView(player, faction, null, target);
    Inventory menu = top();
    String expected = target == TaxTarget.GUILD_ID ? "artisans" : "subject";
    assertEquals(expected, data(menu.getItem(0), Keys.STRING_KEY));
    assertNull(menu.getItem(1));
    assertEquals(Material.BARRIER, menu.getItem(53).getType());
    view.specificTaxProposalView(player, faction, menu, target);
    click(0);
    verify(manager).setChanging(faction, player, target, expected);
    verify(player).closeInventory();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaxTarget.class,
      names = {"GUILD_ID", "VASSAL_ID", "TARIFF_ID"})
  void aSpecificTaxTargetRemovedWhileTheMenuIsOpenDoesNotStartAnEditOrThrow(TaxTarget target) {
    Guild guild = guild("artisans", "Artisans");
    Faction neighbour = faction("neighbour", "Neighbour");
    when(guildHandler.getGuilds()).thenReturn(List.of(guild));
    relations.when(() -> RelationManager.getSubjects(faction)).thenReturn(List.of(neighbour));
    view.specificTaxProposalView(player, faction, null, target);
    guilds.remove("artisans");
    factions.remove("neighbour");
    assertDoesNotThrow(() -> click(0));
    verify(manager, never()).setChanging(any(), any(), any(), any());
  }

  @Test
  void taxViewOnlyShowsEnabledTaxesAndStaleTaxMetadataDoesNotBeginAnEdit() {
    when(faction.hasFactionRule(any())).thenReturn(true);
    view.taxProposalView(player, faction, null);
    Inventory taxMenu = top();
    assertEquals(
        TaxTarget.values().length,
        java.util.Arrays.stream(taxMenu.getContents()).filter(java.util.Objects::nonNull).count()
            - 1);
    view.taxProposalView(player, faction, taxMenu);
    taxMenu.setItem(0, encoded("removed-tax", null));
    click(0);
    verify(manager, never()).setChanging(any(), any(), any(), any());
    taxMenu.setItem(0, new ItemStack(Material.PAPER));
    click(0);
    factions.remove("home");
    click(0);
    verify(manager, never()).setChanging(any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void favourAndRepressNavigationPreservesModeAndChangesOnlyAnEligibleSelectedGuild(
      boolean favour) {
    Guild guild = guild("artisans", "Artisans");
    Faction subject = faction("subject", "Neighbour");
    when(subject.getOrCreateMainGuild()).thenReturn(guild);
    Guild base = guild("base", "Home");
    when(base.isBase()).thenReturn(true);
    when(guildHandler.getGuilds()).thenReturn(List.of(base, guild));
    when(faction.getSubjects()).thenReturn(List.of(subject));
    when(faction.hasFactionRule(Rules.CAN_FAVOUR)).thenReturn(true);
    when(faction.hasFactionRule(Rules.CAN_REPRESS)).thenReturn(true);
    view.governmentView(player, faction, null);
    click(16);
    assertMenu(SFGUI.FAVOUR_REPRESS_MAIN);
    view.favourRepressMainView(player, faction, top());
    click(favour ? 3 : 5);
    assertMenu(SFGUI.FAVOUR_REPRESS_TYPE);
    assertEquals(favour, holder().getFlag());
    view.favourRepressTypeView(player, faction, favour, top());
    for (boolean selectGuild : new boolean[] {true, false}) {
      view.favourRepressTypeView(player, faction, favour, null);
      click(selectGuild ? 3 : 5);
      assertMenu(SFGUI.FAVOUR_REPRESS_SELECT);
      assertEquals(selectGuild ? 1 : 0, holder().getPage());
      assertEquals(selectGuild ? "artisans" : "subject", data(top().getItem(0), Keys.STRING_KEY));
      assertNull(top().getItem(1));
      when(government.canFavour(guild)).thenReturn(false);
      when(government.canRepress(guild)).thenReturn(false);
      click(0);
      verify(government, never()).toggleFavour(guild);
      verify(government, never()).toggleRepress(guild);
      when(government.canFavour(guild)).thenReturn(favour);
      when(government.canRepress(guild)).thenReturn(!favour);
      Inventory menu = top();
      click(0);
      assertSame(menu, top());
      if (favour) verify(government).toggleFavour(guild);
      else verify(government).toggleRepress(guild);
      clearInvocations(government);
      when(faction.getLeader()).thenReturn("Bob");
      click(0);
      verify(government, never()).toggleFavour(any());
      verify(government, never()).toggleRepress(any());
      when(faction.getLeader()).thenReturn("Alice");
    }
  }

  @Test
  void lawMenusListGroupsAndTheirLawsAndUseBothIdsToNavigate() {
    Law law = law();
    LawGroup group = laws.getGroup("charter");
    view.lawCreator = mock(LawCreator.class);
    when(view.lawCreator.createLawGroupItem(player, faction, group))
        .thenAnswer(call -> encoded("charter", null));
    when(view.lawCreator.createLawItem(player, faction, group, law, true))
        .thenAnswer(call -> encoded("free", "charter"));
    view.proposalView(player, faction, null);
    click(0);
    assertMenu(SFGUI.LAW_PROPOSAL_VIEW);
    Inventory groups = top();
    assertEquals("charter", data(groups.getItem(10), Keys.STRING_KEY));
    view.lawProposalView(player, faction, groups);
    click(10);
    assertMenu(SFGUI.LAW_PROPOSAL_SELECT);
    assertEquals("charter", holder().getSecondaryId());
    Inventory selection = top();
    assertEquals("free", data(selection.getItem(0), Keys.STRING_KEY));
    assertEquals("charter", data(selection.getItem(0), Keys.SECONDARY_STRING_KEY));
    view.lawProposalSelect(player, faction, group, selection);
    assertNull(selection.getItem(1));
    view.proposalView(player, faction, null);
    click(1);
    assertMenu(SFGUI.TAX_PROPOSAL_VIEW);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "solo",
        "council",
        "cause",
        "movement",
        "poor-solo",
        "poor-council",
        "active-council",
        "active-cause",
        "blocked-law",
        "blocked-host",
        "denied"
      })
  void lawSelectionRevalidatesTheRulesAndChargesOnlySuccessfulGovernmentChanges(String scenario) {
    Law law = law();
    Movement movement = mock(Movement.class);
    when(government.hasCouncil()).thenReturn(!scenario.endsWith("solo"));
    when(government.canPropose(player)).thenReturn(scenario.endsWith("council"));
    when(government.canProposeOrStartMovement(player)).thenReturn(!scenario.equals("denied"));
    when(government.canBeProposed(any())).thenReturn(!scenario.startsWith("active"));
    if (scenario.endsWith("cause"))
      when(government.getMovementByMember("Alice")).thenReturn(movement);
    if (scenario.startsWith("poor")) when(government.getPower()).thenReturn(39.0);
    if (scenario.equals("blocked-law"))
      canHaveLaw.when(() -> CanHaveLaw.blockReason(faction, law)).thenReturn("§cLaw is locked");
    if (scenario.equals("blocked-host"))
      hostRules
          .when(() -> CivilWarHostMovementRules.blocksHostGuildStart(faction, "Alice"))
          .thenReturn(true);
    Inventory menu = menu(SFGUI.LAW_PROPOSAL_SELECT, 27);
    menu.setItem(0, encoded("free", "charter"));
    click(0);
    ArgumentCaptor<Proposal> proposal = ArgumentCaptor.forClass(Proposal.class);
    switch (scenario) {
      case "solo" -> {
        verify(faction).applyLaw(law, laws.getGroup("charter"));
        verify(government).spendPower(40);
        assertMenu(SFGUI.GOVERNMENT_VIEW);
      }
      case "council" -> {
        verify(government).propose(proposal.capture());
        assertSame(law, proposal.getValue().getLaw());
        verify(government).spendPower(40);
        assertMenu(SFGUI.GOVERNMENT_VIEW);
      }
      case "cause" -> {
        verify(movement).createCause(eq("Alice"), proposal.capture());
        assertSame(law, proposal.getValue().getLaw());
        verify(government, never()).spendPower(anyDouble());
      }
      case "movement" -> {
        verify(government).startMovement(eq("Alice"), proposal.capture());
        assertSame(law, proposal.getValue().getLaw());
        verify(government, never()).spendPower(anyDouble());
      }
      default -> {
        verify(faction, never()).applyLaw(any(), any());
        verify(government, never()).spendPower(anyDouble());
        verify(government, never()).propose(any());
        verify(government, never()).startMovement(anyString(), any());
        verify(movement, never()).createCause(anyString(), any());
        assertSame(menu, top());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "start",
        "cause",
        "active",
        "host-blocked",
        "no-movement",
        "no-political",
        "invalid"
      })
  void politicalSelectionUsesTheActionAndRechecksMovementPermissions(String scenario) {
    when(government.canProposePolitical(player, Action.INDEPENDENCE))
        .thenReturn(!scenario.equals("no-political"));
    when(government.canProposeOrStartMovement(player)).thenReturn(!scenario.equals("no-movement"));
    when(government.canBeProposed(any())).thenReturn(!scenario.equals("active"));
    Movement movement = mock(Movement.class);
    if (Set.of("cause", "active").contains(scenario))
      when(government.getMovementByMember("Alice")).thenReturn(movement);
    hostRules
        .when(() -> CivilWarHostMovementRules.blocksHostGuildStart(faction, "Alice"))
        .thenReturn(scenario.equals("host-blocked"));
    Inventory menu = menu(SFGUI.POLITICAL_PROPOSAL_VIEW, 27);
    menu.setItem(0, encoded(scenario.equals("invalid") ? "obsolete-action" : "INDEPENDENCE", null));
    click(0);
    ArgumentCaptor<Proposal> proposal = ArgumentCaptor.forClass(Proposal.class);
    if (scenario.equals("start")) verify(government).startMovement(eq("Alice"), proposal.capture());
    else if (scenario.equals("cause"))
      verify(movement).createCause(eq("Alice"), proposal.capture());
    else {
      verify(government, never()).startMovement(anyString(), any());
      verify(movement, never()).createCause(anyString(), any());
      assertSame(menu, top());
      return;
    }
    assertEquals(Action.INDEPENDENCE, proposal.getValue().getPoliticalAction().getAction());
    assertEquals("Alice", proposal.getValue().getProposer());
    assertMenu(SFGUI.GOVERNMENT_VIEW);
  }

  @Test
  void politicalWarActionOpensAListOfActualWarsAndCapsItAtTheBackButton() {
    when(government.canProposePolitical(player, Action.WHITE_PEACE)).thenReturn(true);
    List<War> wars = new ArrayList<>();
    for (int n = 0; n < 56; n++) {
      War war = mock(War.class);
      when(war.getId()).thenReturn(n + 1);
      when(war.getName()).thenReturn("War " + (n + 1));
      wars.add(war);
    }
    peace.when(() -> CouncilPeaceQueries.warsFor(faction)).thenReturn(wars);
    view.politicalProposalView(player, faction, null);
    Inventory political = top();
    assertEquals("WHITE_PEACE", data(political.getItem(0), Keys.STRING_KEY));
    assertNull(political.getItem(1));
    view.politicalProposalView(player, faction, political);
    click(0);
    assertMenu(SFGUI.WAR_PEACE_SELECT);
    assertEquals("WHITE_PEACE", holder().getSecondaryId());
    assertEquals("1", data(top().getItem(0), Keys.STRING_KEY));
    assertEquals("53", data(top().getItem(52), Keys.STRING_KEY));
    assertEquals(Material.BARRIER, top().getItem(53).getType());
    view.warPeaceSelectView(player, faction, Action.WHITE_PEACE, false, 0, top());
    assertEquals("53", data(top().getItem(52), Keys.STRING_KEY));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "solo",
        "council",
        "movement",
        "existing",
        "active-council",
        "active-existing",
        "host-blocked",
        "denied",
        "political-denied",
        "war-ended"
      })
  void selectedWarIsRevalidatedBeforeApplyingProposingOrStartingAMovement(String scenario) {
    when(government.canProposePolitical(player, Action.SURRENDER))
        .thenReturn(!scenario.equals("political-denied"));
    when(government.hasCouncil()).thenReturn(!scenario.equals("solo"));
    when(government.canPropose(player)).thenReturn(scenario.endsWith("council"));
    when(government.canProposeOrStartMovement(player)).thenReturn(!scenario.equals("denied"));
    when(government.canBeProposed(any())).thenReturn(!scenario.startsWith("active"));
    peace
        .when(() -> CouncilPeaceQueries.isValidTarget(faction, "42"))
        .thenReturn(!scenario.equals("war-ended"));
    hostRules
        .when(() -> CivilWarHostMovementRules.blocksHostGuildStart(faction, "Alice"))
        .thenReturn(scenario.equals("host-blocked"));
    Movement movement = mock(Movement.class);
    if (scenario.endsWith("existing"))
      when(government.getMovementByMember("Alice")).thenReturn(movement);
    Inventory menu = warMenu(false, 0);
    click(0);
    ArgumentCaptor<Proposal> proposal = ArgumentCaptor.forClass(Proposal.class);
    switch (scenario) {
      case "solo" -> verify(faction).applyPoliticalAction(isNull(), proposal.capture());
      case "council" -> verify(government).propose(proposal.capture());
      case "movement" -> verify(government).startMovement(eq("Alice"), proposal.capture());
      case "existing" -> verify(movement).createCause(eq("Alice"), proposal.capture());
      default -> {
        verify(faction, never()).applyPoliticalAction(any(), any());
        verify(government, never()).propose(any());
        verify(government, never()).startMovement(anyString(), any());
        verify(movement, never()).createCause(anyString(), any());
        assertSame(menu, top());
        return;
      }
    }
    assertEquals(Action.SURRENDER, proposal.getValue().getPoliticalAction().getAction());
    assertEquals("42", proposal.getValue().getTarget());
    assertMenu(SFGUI.GOVERNMENT_VIEW);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"leader", "lost-leader", "no-leader", "removed-cause", "removed-movement"})
  void warTargetForAnExistingCauseCanOnlyBeChangedByItsCurrentLeader(String scenario) {
    peace.when(() -> CouncilPeaceQueries.isValidTarget(faction, "42")).thenReturn(true);
    Movement movement = mock(Movement.class);
    Cause cause = mock(Cause.class);
    Proposal proposal = new Proposal("Alice", government);
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.SURRENDER));
    when(cause.getProposal()).thenReturn(proposal);
    when(cause.getMovement()).thenReturn(movement);
    when(cause.hasLeader()).thenReturn(!scenario.equals("no-leader"));
    when(cause.getLeader()).thenReturn(scenario.equals("lost-leader") ? "Bob" : "Alice");
    when(movement.getCauses())
        .thenReturn(scenario.equals("removed-cause") ? List.of() : List.of(cause));
    when(government.getMovementByMember("Alice"))
        .thenReturn(scenario.equals("removed-movement") ? null : movement);
    warMenu(true, 0);
    click(0);
    if (scenario.equals("leader")) {
      assertEquals("42", proposal.getTarget());
      verify(manager.movementView).causeView(player, faction, movement, cause, null);
    } else {
      assertNull(proposal.getTarget());
      verifyNoInteractions(manager.movementView);
    }
    verify(government, never()).propose(any());
    verify(government, never()).startMovement(anyString(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"empty-slot", "air", "missing-war-id", "missing-faction", "obsolete-action"})
  void invalidWarSelectionDataIsCancelledWithoutAnyGovernmentMutation(String state) {
    Inventory menu = warMenu(false, 0);
    switch (state) {
      case "empty-slot" -> menu.setItem(0, null);
      case "air" -> {
        ItemStack air = new ItemStack(Material.AIR);
        // Paper supplies no ItemMeta for AIR; this scoped override models that API boundary.
        when(air.getItemMeta()).thenReturn(null);
        menu.setItem(0, air);
      }
      case "missing-war-id" -> menu.setItem(0, new ItemStack(Material.PAPER));
      case "missing-faction" -> factions.remove("home");
      case "obsolete-action" -> {
        menu =
            ui.inventory(
                new SFInventoryHolder("home", SFGUI.WAR_PEACE_SELECT, 0, false, "obsolete-action"),
                54,
                "Select War");
        menu.setItem(0, encoded("42", null));
        player.openInventory(menu);
      }
      default -> fail("Unexpected fixture state");
    }
    click(0);
    assertSame(menu, top());
    verify(government, never()).propose(any());
    verify(government, never()).startMovement(anyString(), any());
    verify(faction, never()).applyPoliticalAction(any(), any());
    verifyNoInteractions(manager.movementView);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "removed",
        "shifted",
        "refreshed-removed",
        "refreshed-shifted",
        "switched-movement"
      })
  void warTargetSelectionKeepsTheCauseThatOpenedTheMenu(String state) {
    peace.when(() -> CouncilPeaceQueries.isValidTarget(faction, "42")).thenReturn(true);
    Movement movement = mock(Movement.class);
    when(movement.getFaction()).thenReturn(faction);
    when(faction.getRelationToFaction("Alice")).thenReturn(Member.MEMBER);
    List<Cause> causes = new ArrayList<>();
    for (int n = 0; n < 3; n++) {
      Proposal proposal = new Proposal("Alice", government);
      proposal.setPoliticalActionProposal(new PoliticalAction(Action.SURRENDER));
      causes.add(new Cause(movement, proposal, "Alice"));
    }
    List<Cause> original = List.copyOf(causes);
    when(movement.getCauses()).thenReturn(causes);
    when(government.getMovementByMember("Alice")).thenReturn(movement);
    int selected = state.contains("shifted") ? 1 : 0;
    view.warPeaceSelectView(player, faction, Action.SURRENDER, true, selected, null);
    Inventory menu = top();
    if (state.equals("switched-movement")) {
      Movement replacement = mock(Movement.class);
      when(replacement.getCauses()).thenReturn(causes);
      when(government.getMovementByMember("Alice")).thenReturn(replacement);
    } else causes.removeFirst();
    if (state.startsWith("refreshed"))
      view.warPeaceSelectView(player, faction, Action.SURRENDER, true, selected, menu);
    menu.setItem(0, encoded("42", null));
    click(0);
    for (int n = 0; n < original.size(); n++) {
      assertEquals(
          state.contains("shifted") && n == selected ? "42" : null,
          original.get(n).getProposal().getTarget(),
          "Only the originally displayed live cause may change");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void warPickerBackUsesTheCapturedCauseAndFallsBackIfItWasRemoved(boolean surviving) {
    Movement movement = mock(Movement.class);
    when(movement.getFaction()).thenReturn(faction);
    when(faction.getRelationToFaction("Alice")).thenReturn(Member.MEMBER);
    List<Cause> causes = new ArrayList<>();
    for (int n = 0; n < 2; n++) {
      Proposal proposal = new Proposal("Alice", government);
      proposal.setPoliticalActionProposal(new PoliticalAction(Action.SURRENDER));
      causes.add(new Cause(movement, proposal, "Alice"));
    }
    when(movement.getCauses()).thenReturn(causes);
    when(government.getMovementByMember("Alice")).thenReturn(movement);
    Cause selected = causes.get(surviving ? 1 : 0);
    view.warPeaceSelectView(player, faction, Action.SURRENDER, true, selected.getIndex(), null);
    causes.removeFirst();
    InventoryManager router = new InventoryManager();
    router.governmentView = view;
    router.movementView = manager.movementView;
    top().setItem(53, router.createBackButton(SFGUI.WAR_PEACE_SELECT));
    router.clickButton(ui.click(player, 53));
    if (surviving)
      verify(manager.movementView).causeView(player, faction, movement, selected, null);
    else {
      verifyNoInteractions(manager.movementView);
      assertMenu(SFGUI.GOVERNMENT_VIEW);
    }
  }

  private Inventory warMenu(boolean cause, int index) {
    view.warPeaceSelectView(player, faction, Action.SURRENDER, cause, index, null);
    Inventory menu = top();
    menu.setItem(0, encoded("42", null));
    return menu;
  }

  @Test
  void largeCouncilCandidateRosterKeepsEveryCandidateSelectableAcrossPages() {
    List<String> members =
        java.util.stream.IntStream.range(0, 70).mapToObj(n -> "Member" + n).toList();
    when(faction.getMembers()).thenAnswer(call -> new ArrayList<>(members));
    when(council.getMembers()).thenReturn(List.of("Bob"));
    when(council.canBeMember(anyString(), eq(true), eq(false))).thenReturn(true);
    assertDoesNotThrow(() -> view.councilSelect(player, faction, null, 1));
    assertEquals("Member0", data(top().getItem(0), Keys.STRING_KEY));
    assertEquals("Member44", data(top().getItem(44), Keys.STRING_KEY));
    assertTrue(name(top().getItem(52)).contains("Next"));
    assertEquals(Material.BARRIER, top().getItem(53).getType());
    click(52);
    assertEquals(1, holder().getPage());
    assertEquals("Member45", data(top().getItem(0), Keys.STRING_KEY));
    assertEquals("Member69", data(top().getItem(24), Keys.STRING_KEY));
    assertEquals(
        1,
        top()
            .getItem(24)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.INT, PersistentDataType.INTEGER));
    assertNull(top().getItem(25));
    assertNull(top().getItem(52));
    assertTrue(name(top().getItem(45)).contains("Previous"));
    view.councilSelect(player, faction, top(), 1);
    assertEquals(1, holder().getPage());
    assertEquals("Member45", data(top().getItem(0), Keys.STRING_KEY));
    click(45);
    assertEquals(0, holder().getPage());
    assertEquals("Member0", data(top().getItem(0), Keys.STRING_KEY));
    click(52);
    click(24);
    verify(council).addMember("Member69");
    assertMenu(SFGUI.COUNCIL_VIEW);
    assertEquals(70, faction.getMembers().size());
  }

  @Test
  void councilPagesClampAfterRosterChangesAndRejectNavigationAfterLeadershipIsLost() {
    List<String> members =
        java.util.stream.IntStream.range(0, 70).mapToObj(n -> "Member" + n).toList();
    when(faction.getMembers()).thenAnswer(call -> new ArrayList<>(members));
    when(council.canBeMember(anyString(), eq(true), eq(false))).thenReturn(true);
    view.councilSelect(player, faction, null, 0);
    Inventory original = top();
    when(faction.getLeader()).thenReturn("Bob");
    click(52);
    assertSame(original, top());
    assertEquals(0, holder().getPage());
    when(faction.getLeader()).thenReturn("Alice");
    click(52);
    assertEquals(1, holder().getPage());
    when(faction.getMembers()).thenAnswer(call -> new ArrayList<>(List.of("Member0", "Member1")));
    view.councilSelect(player, faction, top(), 0);
    assertEquals(0, holder().getPage());
    assertEquals("Member0", data(top().getItem(0), Keys.STRING_KEY));
    assertNull(top().getItem(2));
    assertNull(top().getItem(45));
    assertNull(top().getItem(52));
    verify(council, never()).addMember(anyString());
  }

  @Test
  void largeTaxAndFavourTargetListsKeepTheirFirstFiftyThreeEntriesAndBackControl() {
    List<Guild> guildList = new ArrayList<>();
    List<Faction> subjects = new ArrayList<>();
    for (int n = 0; n < 60; n++) {
      Guild guild = guild("guild-" + n, "Guild " + n);
      Faction subject = faction("subject-" + n, "Subject " + n);
      when(subject.getOrCreateMainGuild()).thenReturn(guild);
      guildList.add(guild);
      subjects.add(subject);
    }
    when(guildHandler.getGuilds()).thenReturn(guildList);
    when(faction.getSubjects()).thenReturn(subjects);
    relations.when(() -> RelationManager.getSubjects(faction)).thenReturn(subjects);
    for (TaxTarget target : List.of(TaxTarget.GUILD_ID, TaxTarget.VASSAL_ID, TaxTarget.TARIFF_ID)) {
      view.specificTaxProposalView(player, faction, null, target);
      String prefix = target == TaxTarget.GUILD_ID ? "guild-" : "subject-";
      assertEquals(prefix + "0", data(top().getItem(0), Keys.STRING_KEY));
      assertEquals(prefix + "52", data(top().getItem(52), Keys.STRING_KEY));
      assertEquals(Material.BARRIER, top().getItem(53).getType());
    }
    for (boolean guildMode : new boolean[] {true, false}) {
      view.favourRepressSelectView(player, faction, true, guildMode, null);
      assertEquals(
          (guildMode ? "guild-" : "subject-") + "52", data(top().getItem(52), Keys.STRING_KEY));
      assertEquals(Material.BARRIER, top().getItem(53).getType());
    }
  }

  private Law law() {
    Law law = mock(Law.class);
    Law old = mock(Law.class);
    LawGroup group = mock(LawGroup.class);
    when(law.getId()).thenReturn("free");
    when(law.getGroup()).thenReturn("charter");
    when(law.getCost()).thenReturn(20.0);
    when(law.getCompatibility("old")).thenReturn(2);
    when(old.getId()).thenReturn("old");
    when(group.getId()).thenReturn("charter");
    when(group.getCurrent()).thenReturn(old);
    when(group.getLaws()).thenReturn(Map.of("free", law));
    when(laws.getGroupList()).thenReturn(List.of(group));
    when(laws.getGroup("charter")).thenReturn(group);
    when(laws.getLaw("charter", "free")).thenReturn(law);
    return law;
  }

  private Inventory menu(SFGUI type, int size) {
    Inventory menu = ui.inventory(new SFInventoryHolder("home", type), size, type.name());
    player.openInventory(menu);
    return menu;
  }

  private SFInventoryHolder holder() {
    return (SFInventoryHolder) top().getHolder();
  }

  private void assertMenu(SFGUI expected) {
    assertEquals(expected, holder().getType());
  }

  private InventoryClickEvent click(int slot) {
    Inventory inventory = top();
    InventoryClickEvent event = ui.click(player, slot);
    view.click(event, inventory, player);
    assertTrue(event.isCancelled());
    return event;
  }

  private ItemStack encoded(String primary, String secondary) {
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    if (primary != null)
      meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, primary);
    if (secondary != null)
      meta.getPersistentDataContainer()
          .set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, secondary);
    item.setItemMeta(meta);
    return item;
  }

  private StabilityFacts.Body body(String name, int members, String stance, boolean realm) {
    StabilityFacts.Body body = new StabilityFacts.Body();
    body.name = name;
    body.members = members;
    body.stance = stance;
    body.realm = realm;
    return body;
  }

  private Faction faction(String id, String name) {
    Faction value = mock(Faction.class);
    when(value.getId()).thenReturn(id);
    when(value.getName()).thenReturn(name);
    ItemStack banner = new ItemStack(Material.BLUE_BANNER);
    when(value.getBanner()).thenReturn(banner);
    factions.put(id, value);
    FactionManager.factions.add(value);
    return value;
  }

  private Guild guild(String id, String name) {
    Guild value = mock(Guild.class);
    when(value.getId()).thenReturn(id);
    when(value.getName()).thenReturn(name);
    ItemStack banner = new ItemStack(Material.WHITE_BANNER);
    when(value.getBanner()).thenReturn(banner);
    when(value.getFaction()).thenReturn(faction);
    when(value.getStance(any())).thenReturn(Stance.NEUTRAL);
    guilds.put(id, value);
    return value;
  }

  private Inventory top() {
    return player.getOpenInventory().getTopInventory();
  }

  private String name(ItemStack item) {
    return ChatColor.stripColor(item.getItemMeta().getDisplayName());
  }

  private String lore(ItemStack item) {
    List<String> lines = item.getItemMeta().getLore();
    return lines == null ? "" : ChatColor.stripColor(String.join("\n", lines));
  }

  private String data(ItemStack item, org.bukkit.NamespacedKey key) {
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }
}
