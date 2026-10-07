package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.denareconomy.managers.MoneyManager;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.LoanData;
import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.Member;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.guild.income.LedgerHistory;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.mercenary.contract.ContractAccrualService;
import net.tfminecraft.simplefactions.mercenary.contract.ContractTerminationService;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.objects.PrestigeRank;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.objects.request.ElevateRequest;
import net.tfminecraft.simplefactions.objects.request.MovementJoinRequest;
import net.tfminecraft.simplefactions.objects.request.MovementLeaderTargetRequest;
import net.tfminecraft.simplefactions.objects.request.RelocateRequest;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import net.tfminecraft.simplefactions.utils.PostSettlementPayouts;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleUpkeepService;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsService;
import net.tfminecraft.tlibs.TLibs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class FactionManagerCoverageTest {
  private FactionDomainFixture fixture;
  private FactionManager manager;
  private MockedConstruction<Database> databases;
  private final Map<Field, Object> globals = new LinkedHashMap<>();

  @BeforeEach
  void setUp() throws Exception {
    fixture = new FactionDomainFixture();
    replace(FactionManager.class, "timer", 0);
    replace(FactionManager.class, "day", 0);
    replace(FactionManager.class, "loaded", false);
    replace(FactionManager.class, "dbRelations", new HashMap<>());
    replace(FactionManager.class, "dbTradeRelations", new HashMap<>());
    replace(FactionManager.class, "dbTreatyRelations", new HashMap<>());
    replace(FactionManager.class, "loans", new ArrayList<>());
    replace(RequestManager.class, "requests", new HashMap<>());
    replace(Cache.class, "votingBlock", "v.LECTERN");
    databases = mockConstruction(Database.class);
    manager = new FactionManager();
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> fixture.online.values());
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      databases.close();
      for (Map.Entry<Field, Object> entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    } finally {
      fixture.close();
    }
  }

  private void replace(Class<?> owner, String name, Object value) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    globals.putIfAbsent(field, field.get(null));
    field.set(null, value);
  }

  private Faction faction(String id, String leader) {
    Faction faction = mock(Faction.class, RETURNS_DEEP_STUBS);
    when(faction.getId()).thenReturn(id);
    when(faction.getName()).thenReturn(id);
    when(faction.getLeader()).thenReturn(leader);
    when(faction.getRGB()).thenReturn(id + "-rgb");
    List<String> members = new ArrayList<>(List.of(leader));
    when(faction.getMembers()).thenReturn(members);
    when(faction.isMemberIgnoreCase(anyString()))
        .thenAnswer(call -> members.stream().anyMatch(name -> name.equalsIgnoreCase(call.getArgument(0))));
    doAnswer(call -> members.add(call.getArgument(0))).when(faction).addMember(anyString());
    when(faction.getProvinces()).thenReturn(new ArrayList<>());
    when(faction.getTitles()).thenReturn(new ArrayList<>());
    when(faction.getGuildHandler().getGuilds()).thenReturn(new ArrayList<>());
    when(faction.getGovernment().getFaction()).thenReturn(faction);
    when(faction.getGovernment().getMovementById(anyString())).thenReturn(null);
    when(faction.getGovernment().getMovementByLeader(anyString())).thenReturn(null);
    AtomicReference<PrestigeRank> rank = new AtomicReference<>(RankLoader.getLowest());
    when(faction.getRank()).thenAnswer(call -> rank.get());
    doAnswer(call -> { rank.set(call.getArgument(0)); return null; }).when(faction).setRank(any());
    DiplomacyHandler diplomacy = new DiplomacyHandler(faction);
    when(faction.getDiplomacyHandler()).thenReturn(diplomacy);
    when(faction.getRelations()).thenReturn(diplomacy.getRelations());
    when(faction.getRelation(anyString())).thenAnswer(call -> diplomacy.getRelation(call.getArgument(0)));
    doAnswer(call -> { diplomacy.setRelation(call.getArgument(0), call.getArgument(1)); return null; })
        .when(faction).setRelation(any(Faction.class), any(Relation.class));
    FactionManager.factions.add(faction);
    return faction;
  }

  private Guild guild(Faction faction, String id, String leader) {
    Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
    when(guild.getId()).thenReturn(id);
    when(guild.getName()).thenReturn(id);
    when(guild.getFaction()).thenReturn(faction);
    when(guild.getLeader()).thenReturn(leader);
    when(guild.isLeader(anyString())).thenAnswer(call -> leader.equalsIgnoreCase(call.getArgument(0)));
    when(guild.getMembers()).thenReturn(new ArrayList<>(List.of(leader)));
    when(guild.getType()).thenReturn(GuildLoader.map.get("guild"));
    faction.getGuildHandler().getGuilds().add(guild);
    return guild;
  }

  private Movement movement(Faction faction, String leader) {
    Movement movement = mock(Movement.class);
    when(movement.getId()).thenReturn("movement");
    when(movement.getFaction()).thenReturn(faction);
    when(movement.getLeader()).thenReturn(leader);
    when(movement.getCauses()).thenReturn(new ArrayList<>());
    when(faction.getGovernment().getMovementById("movement")).thenReturn(movement);
    when(faction.getGovernment().getMovementByLeader(leader)).thenReturn(movement);
    return movement;
  }

  private Cause leadershipCause(Movement movement) {
    when(movement.getFaction().getRelationToFaction("Alice")).thenReturn(Member.MEMBER);
    PoliticalAction action = mock(PoliticalAction.class);
    when(action.getAction()).thenReturn(Action.CHANGE_LEADER);
    Proposal proposal = new Proposal("Alice", movement.getFaction().getGovernment());
    proposal.setPoliticalActionProposal(action);
    Cause cause = new Cause(movement, proposal, "Alice");
    movement.getCauses().add(cause);
    return cause;
  }

  @Test
  void deletedMovementJoinCauseCannotSelectTheCauseNowAtItsOldIndex() {
    Player sender = fixture.player("Alice");
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Bob");
    Movement movement = movement(home, "Bob");
    Cause removed = leadershipCause(movement);
    Cause survivor = leadershipCause(movement);
    when(movement.getJoiningAs(sender)).thenReturn("Alice");
    when(movement.canJoin(eq("Alice"), any())).thenReturn(true);
    FactionManager.requestMovementJoin(sender, movement, "supporter", removed);
    movement.getCauses().remove(removed);

    assertDoesNotThrow(() -> FactionManager.acceptMovementJoinRequest(leader));

    verify(movement, never()).join(any(), any());
    verify(leader).sendMessage("§cSpecified cause no longer exists");
    assertEquals(List.of(survivor), movement.getCauses());
  }

  @Test
  void survivingMovementJoinCauseKeepsItsIdentityAfterEarlierCauseRemoval() {
    Player sender = fixture.player("Alice");
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Bob");
    Movement movement = movement(home, "Bob");
    Cause earlier = leadershipCause(movement);
    Cause selected = leadershipCause(movement);
    leadershipCause(movement);
    when(movement.getJoiningAs(sender)).thenReturn("Alice");
    when(movement.canJoin(eq("Alice"), any())).thenReturn(true);
    FactionManager.requestMovementJoin(sender, movement, "supporter", selected);
    movement.getCauses().remove(earlier);

    FactionManager.acceptMovementJoinRequest(leader);

    verify(movement).join("Alice", selected);
  }

  @Test
  void deletedWantedLeaderCauseCannotRetargetTheNextProposal() {
    Player sender = fixture.player("Alice");
    Player target = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    Movement movement = movement(home, "Alice");
    Cause removed = leadershipCause(movement);
    Cause survivor = leadershipCause(movement);
    FactionManager.requestMovementLeaderTarget(sender, movement, removed, "Bob");
    movement.getCauses().remove(removed);

    FactionManager.acceptMovementLeaderTargetRequest(target);

    assertNull(survivor.getProposal().getTarget());
    verify(target).sendMessage("§cSpecified cause no longer exists");
    assertTrue(databases.constructed().isEmpty());
  }

  @Test
  void managerLookupsPreserveCaseInsensitivityAndRepairLegacyLeaderMembership() {
    Faction home = faction("Home", "Alice");
    Faction other = faction("Other", "Bob");
    Guild traders = guild(home, "Traders", "Carol");
    traders.getMembers().add("Dave");
    Title title = fixture.title("County", "county", 4);
    home.getProvinces().add(4);
    when(home.hasTitle(title)).thenReturn(true);
    assertSame(home, FactionManager.getByString("HOME"));
    assertSame(home, FactionManager.getByLeader("aLiCe"));
    assertSame(home, FactionManager.getByMember("ALICE"));
    home.getMembers().clear();
    assertSame(home, FactionManager.getByMember("aLiCe"));
    assertEquals(List.of("aLiCe"), home.getMembers());
    assertSame(home, FactionManager.getByRGB("home-RGB"));
    assertSame(home, FactionManager.getByProvince(4));
    assertSame(home, FactionManager.getTitleOwner(title));
    assertSame(traders, FactionManager.getGuildByString("TRADERS"));
    assertSame(traders, FactionManager.getGuildByLeader("cARoL"));
    assertSame(traders, FactionManager.getGuildByMember("dAVE"));
    assertTrue(FactionManager.guildExists("traders"));
    assertFalse(FactionManager.guildExists("missing"));
    assertNull(FactionManager.getByString("missing"));
    assertNull(FactionManager.getByLeader("missing"));
    assertNull(FactionManager.getByMember("missing"));
    assertNull(FactionManager.getByMember(null));
    assertNull(FactionManager.getByRGB("missing"));
    assertNull(FactionManager.getByProvince(9));
    assertNull(FactionManager.getTitleOwner(fixture.title("Elsewhere", "county", 9)));
    assertNull(FactionManager.getGuildByString("missing"));
    assertNull(FactionManager.getGuildByLeader("missing"));
    assertNull(FactionManager.getGuildByMember("missing"));
    assertNull(FactionManager.getGuildByMember(null));
    assertNull(FactionManager.getMovementById("missing"));
    assertNull(FactionManager.getMovementById(null));
    List<Faction> copy = FactionManager.getCopy();
    copy.clear();
    assertEquals(List.of(home, other), FactionManager.factions);
    assertSame(fixture.map, FactionManager.getMap());
    assertSame(fixture.inventory, FactionManager.getInv());
    assertEquals(List.of(traders), FactionManager.getAllGuilds());
  }

  @Test
  void joiningGuildIsAllowedForUnaffiliatedAndRealmMembersButNotGuildLeaders() {
    Faction home = faction("home", "Alice");
    Guild guild = guild(home, "guild", "Bob");
    guild.getMembers().add("Carol");
    assertTrue(FactionManager.canJoinGuild(fixture.player("Dave")));
    assertFalse(FactionManager.canJoinGuild(fixture.player("BOB")));
    assertFalse(FactionManager.canJoinGuild(fixture.player("Carol")));
    when(guild.getType()).thenReturn(GuildLoader.map.get("realm"));
    assertTrue(FactionManager.canJoinGuild(fixture.player("Carol")));
  }

  @ParameterizedTest
  @CsvSource(value = {"0,0,0|0", "255, 12, 3|0", "1,2|1", "1,2,3,4|1", "x,2,3|2", "-1,2,3|3", "256,2,3|3"}, delimiter = '|')
  void rgbValidationProvidesSpecificErrors(String input, int expected) {
    assertEquals(expected, FactionManager.validateRGB(input));
    assertEquals(1, FactionManager.validateRGB(null));
  }

  @Test
  void competitiveRankThresholdUsesTheHighestHolderOrTheConfiguredFloor() {
    PrestigeRank rank = RankLoader.getLowest();
    rank.setMin(100.0);
    rank.setPercentage(50.0);
    assertEquals(100.0, FactionManager.getRankUpAmount(rank));
    Faction weak = faction("weak", "Alice");
    Faction strong = faction("strong", "Bob");
    when(weak.getPrestige()).thenReturn(30.0);
    when(strong.getPrestige()).thenReturn(50.0);
    assertEquals(100.0, FactionManager.getRankUpAmount(rank));
    when(strong.getPrestige()).thenReturn(300.0);
    assertEquals(150.0, FactionManager.getRankUpAmount(rank));
    strong.setRank(RankLoader.getByString("renowned"));
    assertEquals(100.0, FactionManager.getRankUpAmount(rank));
  }

  @Test
  void wealthSummariesIncludeOnlyTheRequestedSourcesAndExcludeBaseGuildLiquidWealth() {
    Faction home = faction("home", "Alice");
    Faction other = faction("other", "Bob");
    when(home.getWealth()).thenReturn(120.123);
    when(other.getWealth()).thenReturn(25.5);
    when(home.getWealthModifiers()).thenReturn(List.of(new Modifier("BANK", 45.0, false), new Modifier("nodes", 20.0, false), new Modifier("other", 30.0, false)));
    Guild guild = guild(home, "guild", "Carol");
    Guild realm = guild(other, "realm", "Bob");
    when(realm.isBase()).thenReturn(true);
    when(guild.getWealthModifiers()).thenReturn(List.of(new Modifier("Bank", 12.345, false), new Modifier("nodes", 30.0, false)));
    when(realm.getWealthModifiers()).thenReturn(List.of(new Modifier("bank", 500.0, false)));
    when(guild.getLedger().getInflationDelta()).thenReturn(3.25);
    when(realm.getLedger().getInflationDelta()).thenReturn(-1.0);
    when(guild.getTotalExpansionSpent()).thenReturn(7.5);
    when(realm.getTotalExpansionSpent()).thenReturn(2.0);
    MoneyManager money = mock(MoneyManager.class);
    when(money.getServerBal(Accounts.POUCH)).thenReturn(90.126);
    when(money.getServerBal(Accounts.BANK)).thenReturn(200.124);
    try (MockedStatic<DenarEconomy> economy = mockStatic(DenarEconomy.class)) {
      economy.when(DenarEconomy::getMoneyManager).thenReturn(money);
      assertEquals(90.13, FactionManager.getPouchWealth());
      assertEquals(200.12, FactionManager.getBankWealth());
    }
    assertEquals(145.62, FactionManager.getGlobalWealth());
    assertEquals(45.0, FactionManager.getGlobalLiquidWealth());
    assertEquals(20.0, FactionManager.getGlobalNodeWealth());
    assertEquals(12.35, FactionManager.getGuildLiquidWealth());
    assertEquals(2.25, FactionManager.getTotalGuildIncome());
    assertEquals(9.5, FactionManager.getGlobalGuildExpansions());
  }

  @Test
  void queuedDiplomacyRestoresRelationsAndOverlaysAndDrainsEachQueue() {
    Faction home = faction("home", "Alice");
    Faction subject = faction("subject", "Bob");
    Faction partner = faction("partner", "Carol");
    RelationType trade = fixture.relationType("trade", Map.of("trade-agreement", true));
    RelationType treaty = fixture.relationType("peace", Map.of("treaty", true));
    for (String invalid : Arrays.asList(null, "broken", "missing(neutral.neutral.1)", "home(neutral.neutral.1)", "partner(neutral)", "partner(missing.neutral.1)", "partner(neutral.missing.1)", "partner(neutral.neutral.invalid)", "partner(")) {
      FactionManager.addDBRelation(home, invalid);
    }
    FactionManager.addDBRelation(home, "subject(vassal.neutral.12)");
    FactionManager.addDBRelation(home, "partner(neutral.neutral.42)");
    for (String invalid : Arrays.asList(null, "broken", "missing(trade)", "home(trade)", "partner(missing)", "partner(")) {
      FactionManager.addDBTradeRelation(home, invalid);
    }
    FactionManager.addDBTradeRelation(home, "partner(trade)");
    fixture.relationType("clear", Map.of("treaty", true, "clear", true));
    for (String invalid : Arrays.asList(null, "broken", "missing(peace)", "home(peace)", "partner(missing)", "partner(neutral)", "partner(clear)", "partner(")) {
      FactionManager.addDBTreatyRelation(home, invalid);
    }
    FactionManager.addDBTreatyRelation(home, "partner(peace)");

    FactionManager.loadRelations();

    assertEquals("vassal", home.getRelation("subject").getType().getId());
    assertEquals("overlord", subject.getRelation("home").getType().getId());
    assertEquals(12, home.getRelation("subject").getOpinion());
    assertEquals(42, home.getRelation("partner").getOpinion());
    assertEquals(2, home.getRelations().size());
    assertSame(trade, home.getDiplomacyHandler().getTradeRelation("partner"));
    assertSame(treaty, home.getDiplomacyHandler().getTreatyRelation("partner"));
    Relation preserved = subject.getRelation("home");
    FactionManager.addDBRelation(home, "subject(vassal.neutral.8)");
    FactionManager.loadRelations();
    assertSame(preserved, subject.getRelation("home"), "an explicit reverse must not be overwritten");
    home.getRelations().clear();
    home.getDiplomacyHandler().getTradeRelations().clear();
    home.getDiplomacyHandler().getTreatyRelations().clear();
    FactionManager.loadRelations();
    assertTrue(home.getRelations().isEmpty());
    assertTrue(home.getDiplomacyHandler().getTradeRelations().isEmpty());
    assertTrue(home.getDiplomacyHandler().getTreatyRelations().isEmpty());
  }

  @Test
  void aStoredTradeWhoseTypeBecameDiplomaticDoesNotEnterTheTradeMap() {
    Faction home = faction("home", "Alice");
    faction("other", "Bob");
    FactionManager.addDBTradeRelation(home, "other(vassal)");

    FactionManager.loadRelations();

    assertTrue(home.getDiplomacyHandler().getTradeRelations().isEmpty());
  }

  @Test
  void invalidLegacyTradeTargetsStayUnmigratedAndMutualFallbackRetainsAgreementType() {
    Faction home = faction("home", "Alice");
    Faction other = faction("other", "Bob");
    RelationType trade = fixture.relationType("trade", Map.of("trade-agreement", true, "mutual", true, "link", "neutral"));
    Relation stale = new Relation(trade, null, 17);
    home.getRelations().put("removed", stale);
    home.getRelations().put("home", new Relation(trade, null, 0));
    home.setRelation(other, new Relation(trade, null, 25));

    FactionManager.loadRelations();

    assertSame(trade, stale.getType());
    assertSame(trade, home.getRelation("home").getType());
    assertSame(RelationLoader.getDefaultType(), home.getRelation("other").getType());
    assertSame(trade, other.getDiplomacyHandler().getTradeRelation("home"));
    assertEquals(25, home.getRelation("other").getOpinion());
    assertEquals("trade.?.17", FactionManager.describeRelation(stale));
    assertEquals("null", FactionManager.describeRelation(null));
    assertEquals("null", FactionManager.describeRelation(new Relation(null, null)));
    assertEquals("neutral.neutral.0(default)", FactionManager.describeRelation(new Relation()));
  }

  @Test
  void reloadingBindsTitlesRanksDiplomacyAndOutstandingRequestsToCurrentDefinitions() {
    Faction home = faction("home", "Alice");
    Faction other = faction("other", "Bob");
    Title kept = fixture.title("kept", "county", 2);
    Title removed = fixture.title("removed", "county", 3);
    home.getTitles().addAll(List.of(kept, removed));
    TitleLoader.getTitles().clear();
    Title replacement = fixture.title("kept", "county", 5);
    FactionManager.reloadTitles();
    verify(home).resetTitles(List.of(replacement));
    PrestigeRank oldRank = home.getRank();
    RankLoader.ranks.clear();
    PrestigeRank replacementRank = fixture.rank(oldRank.getId(), 1, 30);
    other.setRank(null);
    FactionManager.rebindRanks();
    assertSame(replacementRank, home.getRank());
    assertSame(replacementRank, other.getRank());
    Relation oldRelation = new Relation(RelationLoader.getType("vassal"), RelationLoader.getDefaultAttitude(), 19);
    home.setRelation(other, oldRelation);
    home.getRelations().put("unknown", new Relation(null, null));
    home.getRelations().put("invalid", null);
    RelationType oldTrade = fixture.relationType("trade", Map.of("trade-agreement", true));
    home.getDiplomacyHandler().getTradeRelations().put("other", oldTrade);
    home.getDiplomacyHandler().getTradeRelations().put("null", null);
    home.getDiplomacyHandler().getTradeRelations().put("removed", RelationLoader.getType("vassal"));
    home.getDiplomacyHandler().getTreatyRelations().put("removed", RelationLoader.getType("vassal"));
    RelationLoader.types.clear();
    RelationType neutral = fixture.relationType("neutral", Map.of("default", true));
    RelationType replacementTrade = fixture.relationType("trade", Map.of("trade-agreement", true));
    RelationLoader.attitudes.clear();
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("neutral.default", true);
    Attitude neutralAttitude = new Attitude("neutral", yaml.getConfigurationSection("neutral"));
    RelationLoader.attitudes.add(neutralAttitude);

    FactionManager.rebindDiplomacy();

    assertSame(neutral, oldRelation.getType());
    assertSame(neutralAttitude, oldRelation.getAttitude());
    assertEquals(19, oldRelation.getOpinion());
    assertSame(neutral, home.getRelation("unknown").getType());
    assertEquals(Map.of("other", replacementTrade), home.getDiplomacyHandler().getTradeRelations());
    assertTrue(home.getDiplomacyHandler().getTreatyRelations().isEmpty());
    RankLoader.ranks.clear();
    FactionManager.rebindRanks();
    assertNull(home.getRank());
    RelationLoader.types.clear();
    RelationLoader.attitudes.clear();
    FactionManager.rebindDiplomacy();
    assertNull(oldRelation.getType());
    assertNull(oldRelation.getAttitude());
  }

  @Test
  void queuedLoansResolveLiveGuildsAndAreNotIssuedTwice() {
    Faction home = faction("home", "Alice");
    Guild lender = guild(home, "lender", "Alice");
    Guild borrower = guild(home, "borrower", "Bob");
    Loan source = new Loan("loan", 120, lender, borrower, 1000L, 3, 2, 5, true);
    FactionManager.addDBLoan(new LoanData(source));

    FactionManager.loadDBLoans();
    FactionManager.loadDBLoans();

    org.mockito.ArgumentCaptor<Loan> issued = org.mockito.ArgumentCaptor.forClass(Loan.class);
    verify(lender.getLoanHandler()).issueLoan(issued.capture());
    assertEquals("loan", issued.getValue().getId());
    assertSame(lender, issued.getValue().getIssuer());
    assertSame(borrower, issued.getValue().getBorrower());
    assertEquals(120, issued.getValue().getAmount());
  }

  @Test
  void startupListReplacementAndDeletionUpdateRegistryDiplomacyMapAndPersistence() {
    Faction home = faction("home", "Alice");
    Faction other = faction("other", "Bob");
    home.setRelation(other, new Relation());
    other.setRelation(home, new Relation());
    manager.start(new ArrayList<>(List.of(home, other)));

    FactionManager.deleteFaction(other);

    assertEquals(List.of(home), FactionManager.factions);
    assertFalse(home.getRelations().containsKey("other"));
    verify(fixture.map).enqueue("nation", "other-rgb");
    verify(databases.constructed().getLast()).deleteFaction(other);
  }

  @Test
  void prestigePassesWaitForLoadingAndStopWhenRanksConverge() {
    Faction home = faction("home", "Alice");
    Faction other = faction("other", "Bob");
    FactionManager.loading = true;
    FactionManager.updateAllPrestige();
    verify(home, never()).updatePrestige();
    FactionManager.loading = false;
    doAnswer(call -> { home.setRank(RankLoader.getByString("renowned")); return null; }).when(home).updatePrestige();

    FactionManager.updateAllPrestigeConverged();

    verify(home, times(2)).updatePrestige();
    verify(other, times(2)).updatePrestige();
    assertEquals("renowned", home.getRank().getId());
  }

  @Test
  void prestigePassesAreBoundedEvenIfCollaboratorNeverSettles() {
    Faction home = faction("home", "Alice");
    PrestigeRank low = RankLoader.getLowest();
    PrestigeRank high = RankLoader.getByString("renowned");
    doAnswer(call -> { home.setRank(home.getRank() == low ? high : low); return null; }).when(home).updatePrestige();
    FactionManager.updateAllPrestigeConverged();
    verify(home, times(RankLoader.getRanks().size())).updatePrestige();
  }

  @Test
  void usurpingTransfersHighestTitleSubjectsAndTheFormerOverlordLink() {
    Player actor = fixture.player("Alice");
    Faction winner = faction("winner", "Alice");
    Faction loser = faction("loser", "Bob");
    Faction subject = faction("subject", "Carol");
    Faction overlord = faction("overlord", "Dave");
    Title title = fixture.title("duchy", "duchy", 2);
    when(loser.getHighestTitle()).thenReturn(title);
    RelationType subjectType = fixture.relationType("subject", Map.of("overlord", true));
    try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
      relations.when(() -> RelationManager.getSubjects(loser)).thenReturn(List.of(winner, subject));
      relations.when(() -> RelationManager.getOverlord(loser)).thenReturn("overlord");
      assertSame(title, FactionManager.usurp(actor, winner, loser));
      verify(winner).addTitle(title);
      verify(loser).removeTitle(title);
      relations.verify(() -> RelationManager.transferSubject(subject, winner));
      relations.verify(() -> RelationManager.transferSubject(winner, winner), never());
      relations.verify(() -> RelationManager.endVassalage(overlord, loser, false));
      relations.verify(() -> RelationManager.transferSubject(winner, overlord));
      relations.verify(() -> RelationManager.endVassalage(winner, loser, true));
      relations.verify(() -> RelationManager.setRelationForced(subjectType, loser, winner));
    }
    when(loser.getHighestTitle()).thenReturn(null);
    assertNull(FactionManager.usurp(actor, winner, loser));
    assertNull(FactionManager.usurp(null, winner, loser));
    verify(actor).sendMessage("§ctarget has no titles");
  }

  @Test
  void relocationRequestRequiresOnlineLeaderAndCarriesTheNamedDestination() {
    Player sender = fixture.player("Alice");
    Faction home = faction("home", "Alice");
    Faction target = faction("target", "Bob");
    Guild guild = guild(home, "Artisans", "Alice");
    FactionManager.requestRelocation(sender, guild, target, 7, "Riverside");
    verify(sender).sendMessage("§cTarget faction leader is not online");
    Player leader = fixture.player("Bob");
    FactionManager.requestRelocation(sender, guild, target, 7, "Riverside");
    RelocateRequest request = assertInstanceOf(RelocateRequest.class, RequestManager.getRequest(leader));
    assertSame(guild, request.getSender());
    assertEquals(7, request.getNewCapital());
    assertEquals("Riverside", request.getSettlementName());
    verify(leader).sendMessage("Artisans §7is requesting to relocate to your faction");
  }

  @Test
  void relocationAcceptanceRechecksFactionDestinationNameAndAvailableFunds() {
    Player sender = fixture.player("Alice");
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    Faction target = faction("target", "Bob");
    Guild guild = guild(home, "Artisans", "Alice");
    RequestManager.addRequest(sender, leader, new RelocateRequest(guild, 7, null));
    FactionManager.factions.remove(target);
    FactionManager.acceptRelocateRequest(leader);
    verify(leader).sendMessage("§cYou do not have a faction");
    FactionManager.factions.add(target);
    when(target.getSettlementHandler().requiresFoundingName(7)).thenReturn(true);
    FactionManager.acceptRelocateRequest(leader);
    verify(leader).sendMessage("§cA city name is required to relocate here");
    verify(sender).sendMessage("§cRelocation failed - a city name is required at the destination");
    RequestManager.remove(leader);
    RequestManager.addRequest(sender, leader, new RelocateRequest(guild, 7, "Riverside"));
    when(guild.getRelocationCost(7)).thenReturn(25.5);
    when(guild.getBank().getWealth()).thenReturn(25.0);
    FactionManager.acceptRelocateRequest(leader);
    verify(guild.getBank(), never()).withdraw(anyDouble());
    verify(guild, never()).relocate(any(), anyInt(), any(), any());
    verify(sender).sendMessage(contains("does not have enough funds"));
    when(guild.getBank().getWealth()).thenReturn(25.5);
    FactionManager.acceptRelocateRequest(leader);
    verify(guild.getBank()).withdraw(25.5);
    verify(guild).relocate(target, 7, sender, "Riverside");
    verify(leader).sendMessage("Artisans§a has been relocated to your faction");
  }

  @Test
  void relocationCanCompleteWithTheReceivingLeaderWhenTheGuildLeaderGoesOffline() {
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    Faction target = faction("target", "Bob");
    Guild guild = guild(home, "Artisans", "Alice");
    RequestManager.addRequest(leader, leader, new RelocateRequest(guild, 7, null));
    when(guild.getRelocationCost(7)).thenReturn(10.0);
    when(guild.getBank().getWealth()).thenReturn(20.0);
    FactionManager.acceptRelocateRequest(leader);
    verify(guild).relocate(target, 7, leader, null);
    verify(guild.getBank()).withdraw(10.0);
  }

  @Test
  void elevationRequestRequiresOnlineLeaderAndNamesTheSponsoringFaction() {
    Player sender = fixture.player("Alice");
    Faction home = faction("home", "Alice");
    Guild guild = guild(home, "Artisans", "Bob");
    FactionManager.requestElevation(sender, guild);
    verify(sender).sendMessage("§cTarget guild leader is not online");
    Player leader = fixture.player("Bob");
    FactionManager.requestElevation(sender, guild);
    assertSame(guild, assertInstanceOf(ElevateRequest.class, RequestManager.getRequest(leader)).getSender());
    verify(leader).sendMessage("home §7wants to elevate your guild to a faction");
  }

  @Test
  void elevationRechecksLeadershipAndPowerAndOnlyChargesSuccessfulElevation() {
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    Guild guild = guild(home, "Artisans", "Bob");
    RequestManager.addRequest(leader, leader, new ElevateRequest(guild));
    home.getGuildHandler().getGuilds().clear();
    FactionManager.acceptElevationRequest(leader);
    verify(leader).sendMessage("§cYou are not the leader of a guild");
    home.getGuildHandler().getGuilds().add(guild);
    when(guild.getElevationCost()).thenReturn(12.0);
    when(home.getGovernment().getPower()).thenReturn(11.0);
    FactionManager.acceptElevationRequest(leader);
    verify(leader).sendMessage("§cCannot afford to elevate");
    verify(guild, never()).elevate(anyBoolean());
    when(home.getGovernment().getPower()).thenReturn(12.0);
    when(guild.elevate(true)).thenReturn(null);
    FactionManager.acceptElevationRequest(leader);
    verify(home.getGovernment(), never()).spendPower(anyDouble());
    Faction elevated = faction("elevated", "Bob");
    when(guild.elevate(true)).thenAnswer(call -> {
      when(guild.getFaction()).thenReturn(elevated);
      return elevated;
    });
    FactionManager.acceptElevationRequest(leader);
    verify(home.getGovernment()).spendPower(12.0);
    verify(elevated.getGovernment(), never()).spendPower(anyDouble());
    verify(leader).sendMessage("§aGuild elevated to Faction!");
    verify(leader).playSound(leader, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
  }

  @Test
  void elevationConsentCannotBeTransferredToAnotherGuildTheRecipientNowLeads() {
    Player sponsor = fixture.player("Alice");
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    Guild requested = guild(home, "requested", "Bob");
    when(home.getGovernment().getPower()).thenReturn(100.0);
    FactionManager.requestElevation(sponsor, requested);
    when(requested.isLeader("Bob")).thenReturn(false);
    when(requested.getLeader()).thenReturn("Carol");
    Guild replacement = guild(home, "replacement", "Bob");

    FactionManager.acceptElevationRequest(leader);

    verify(requested, never()).elevate(anyBoolean());
    verify(replacement, never()).elevate(anyBoolean());
    verify(home.getGovernment(), never()).spendPower(anyDouble());
  }

  @Test
  void elevationConsentExpiresWhenTheRequestedGuildMovesToAnotherSponsor() {
    Player sponsor = fixture.player("Alice");
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    Faction other = faction("other", "Carol");
    Guild requested = guild(home, "requested", "Bob");
    when(home.getGovernment().getPower()).thenReturn(100.0);
    when(other.getGovernment().getPower()).thenReturn(100.0);
    FactionManager.requestElevation(sponsor, requested);
    home.getGuildHandler().getGuilds().remove(requested);
    other.getGuildHandler().getGuilds().add(requested);
    when(requested.getFaction()).thenReturn(other);

    FactionManager.acceptElevationRequest(leader);

    verify(requested, never()).elevate(anyBoolean());
    verify(home.getGovernment(), never()).spendPower(anyDouble());
    verify(other.getGovernment(), never()).spendPower(anyDouble());
  }

  @Test
  void movementJoinRechecksOnlineSenderFactionMovementAndCurrentEligibility() {
    Player sender = fixture.player("Alice");
    Faction home = faction("home", "Bob");
    Movement movement = movement(home, "Bob");
    FactionManager.requestMovementJoin(sender, movement, "supporter", null);
    verify(sender).sendMessage("§cTarget movement leader is not online");
    Player leader = fixture.player("Bob");
    FactionManager.requestMovementJoin(sender, movement, "supporter", null);
    assertInstanceOf(MovementJoinRequest.class, RequestManager.getRequest(leader));
    fixture.online.remove("Alice");
    FactionManager.acceptMovementJoinRequest(leader);
    verify(leader).sendMessage("§cRequest sender is not online");
    fixture.online.put("Alice", sender);
    FactionManager.factions.remove(home);
    FactionManager.acceptMovementJoinRequest(leader);
    verify(leader).sendMessage("§cTarget faction no longer exists");
    FactionManager.factions.add(home);
    when(home.getGovernment().getMovementByLeader("Bob")).thenReturn(null);
    FactionManager.acceptMovementJoinRequest(leader);
    verify(leader).sendMessage("§cYou are not the leader of a movement");
    when(home.getGovernment().getMovementByLeader("Bob")).thenReturn(movement);
    FactionManager.acceptMovementJoinRequest(leader);
    verify(leader).sendMessage("§cSender cannot join movement as a supporter");
    when(movement.getJoiningAs(sender)).thenReturn("Alice");
    when(movement.joinBlockReason("Alice", null, true)).thenReturn("§cStaff block");
    when(movement.joinBlockReason("Alice", null, false)).thenReturn("§cPlayer block");
    FactionManager.acceptMovementJoinRequest(leader);
    verify(leader).sendMessage("§cStaff block");
    verify(sender).sendMessage("§cPlayer block");
    when(movement.canJoin("Alice", null)).thenReturn(true);
    FactionManager.acceptMovementJoinRequest(leader);
    verify(movement).join("Alice", null);
    verify(leader).sendMessage("§aAlice has joined your movement as a supporter!");
  }

  @Test
  void foreignBackingRequiresALiveFactionAndUsesSeparateStaffAndPlayerDenialText() {
    Player sender = fixture.player("Alice");
    Player leader = fixture.player("Bob");
    Faction home = faction("home", "Bob");
    Movement movement = movement(home, "Bob");
    FactionManager.requestMovementJoin(sender, movement, "foreign_backer", null);
    FactionManager.acceptMovementJoinRequest(leader);
    verify(leader).sendMessage("§cSender has no faction.");
    Faction foreign = faction("foreign", "Alice");
    when(movement.foreignBackerBlockReason(foreign, true)).thenReturn("§cStaff denial");
    when(movement.foreignBackerBlockReason(foreign, false)).thenReturn("§cPlayer denial");
    FactionManager.acceptMovementJoinRequest(leader);
    verify(leader).sendMessage("§cStaff denial");
    verify(sender).sendMessage("§cPlayer denial");
    verify(movement, never()).joinAsForeignBacker(any());
    when(movement.foreignBackerBlockReason(foreign, true)).thenReturn(null);
    FactionManager.acceptMovementJoinRequest(leader);
    verify(movement).joinAsForeignBacker(foreign);
  }

  @Test
  void wantedLeaderRequestValidatesTheCandidateBeforeOfferingConsent() {
    Player sender = fixture.player("Alice");
    Faction home = faction("home", "Alice");
    Movement movement = movement(home, "Alice");
    Cause cause = leadershipCause(movement);
    FactionManager.requestMovementLeaderTarget(null, movement, cause, "Bob");
    FactionManager.requestMovementLeaderTarget(sender, null, cause, "Bob");
    FactionManager.requestMovementLeaderTarget(sender, movement, null, "Bob");
    FactionManager.requestMovementLeaderTarget(sender, movement, cause, null);
    FactionManager.requestMovementLeaderTarget(sender, movement, cause, "Bob");
    verify(sender).sendMessage("§cThat player must be online to become the wanted leader.");
    Player target = fixture.player("Bob");
    FactionManager.requestMovementLeaderTarget(sender, movement, cause, "Bob");
    assertNull(RequestManager.getRequest(target));
    verify(sender).sendMessage("§cBob cannot be the wanted leader.");
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    FactionManager.requestMovementLeaderTarget(sender, movement, cause, "Bob");
    MovementLeaderTargetRequest request = assertInstanceOf(MovementLeaderTargetRequest.class, RequestManager.getRequest(target));
    assertEquals("Alice", request.getRequester());
    assertEquals("movement", request.getMovementId());
    assertEquals("Bob", request.getProposedName());
  }

  @Test
  void wantedLeaderAcceptanceRechecksMovementAndCandidateThenPersistsTheExactProposal() {
    Player sender = fixture.player("Alice");
    Player target = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    Movement movement = movement(home, "Alice");
    Cause cause = leadershipCause(movement);
    FactionManager.acceptMovementLeaderTargetRequest(target);
    FactionManager.requestMovementLeaderTarget(sender, movement, cause, "Bob");
    when(movement.isFrozen()).thenReturn(true);
    FactionManager.acceptMovementLeaderTargetRequest(target);
    verify(target).sendMessage("§cThat movement is no longer available.");
    when(movement.isFrozen()).thenReturn(false);
    when(home.canBecomeLeader("Bob")).thenReturn(false);
    FactionManager.acceptMovementLeaderTargetRequest(target);
    verify(target).sendMessage("§cYou can no longer become the wanted leader.");
    assertNull(cause.getProposal().getTarget());
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    FactionManager.acceptMovementLeaderTargetRequest(target);
    assertEquals("Bob", cause.getProposal().getTarget());
    verify(databases.constructed().getLast()).saveFaction(home);
    verify(sender).sendMessage("§aBob accepted becoming the wanted leader.");
    verify(target).sendMessage("§aYou are now the wanted leader of this movement.");
  }

  @Test
  void wantedLeaderConsentExpiresWhenTheRequesterNoLongerLeadsThatCause() {
    Player sender = fixture.player("Alice");
    Player target = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    Movement movement = movement(home, "Alice");
    Cause cause = leadershipCause(movement);
    FactionManager.requestMovementLeaderTarget(sender, movement, cause, "Bob");
    cause.setLeader("Carol");

    FactionManager.acceptMovementLeaderTargetRequest(target);

    assertNull(cause.getProposal().getTarget());
    assertTrue(databases.constructed().isEmpty());
  }

  @Test
  void wantedLeaderConsentFollowsTheOriginalSurvivingCauseAfterReindexing() {
    Player sender = fixture.player("Alice");
    Player target = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    Movement movement = movement(home, "Alice");
    Cause first = leadershipCause(movement);
    Cause selected = leadershipCause(movement);
    Cause later = leadershipCause(movement);
    FactionManager.requestMovementLeaderTarget(sender, movement, selected, "Bob");
    movement.getCauses().remove(first);
    fixture.online.remove("Alice");

    FactionManager.acceptMovementLeaderTargetRequest(target);

    assertEquals("Bob", selected.getProposal().getTarget());
    assertNull(later.getProposal().getTarget());
    verify(databases.constructed().getLast()).saveFaction(home);
  }

  @Test
  void wantedLeaderConsentCannotApplyToAReplacementMovementWithTheSameId() {
    Player sender = fixture.player("Alice");
    Player target = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    Movement original = movement(home, "Alice");
    Cause originalCause = leadershipCause(original);
    FactionManager.requestMovementLeaderTarget(sender, original, originalCause, "Bob");
    Movement replacement = movement(home, "Alice");
    Cause replacementCause = leadershipCause(replacement);
    FactionManager.acceptMovementLeaderTargetRequest(target);
    assertNull(originalCause.getProposal().getTarget());
    assertNull(replacementCause.getProposal().getTarget());
    assertTrue(databases.constructed().isEmpty());
  }

  @Test
  void aWantedLeaderRequestForAnotherPlayerIsRejectedWithoutChangingTheProposal() {
    Player sender = fixture.player("Alice");
    Player target = fixture.player("Bob");
    Faction home = faction("home", "Alice");
    when(home.canBecomeLeader("Carol")).thenReturn(true);
    Movement movement = movement(home, "Alice");
    Cause cause = leadershipCause(movement);
    MovementLeaderTargetRequest request = new MovementLeaderTargetRequest(null, "Alice", movement, cause, "Carol");
    RequestManager.addRequest(sender, target, request);
    FactionManager.acceptMovementLeaderTargetRequest(target);
    verify(target).sendMessage("§cThat request is not for you.");
    assertNull(cause.getProposal().getTarget());
  }

  @Test
  void legacyOrdinalOnlyRequestsRetainTheirDataButCannotRetargetCurrentCauses() {
    Player sender = fixture.player("Alice");
    Player target = fixture.player("Bob");
    Faction home = faction("home", "Bob");
    Movement movement = movement(home, "Bob");
    Cause cause = leadershipCause(movement);
    when(movement.getJoiningAs(sender)).thenReturn("Alice");
    when(movement.canJoin(eq("Alice"), any())).thenReturn(true);
    MovementJoinRequest join = new MovementJoinRequest(null, "Alice", "supporter", "home", 0);
    assertEquals("Alice", join.getPlayer());
    assertEquals("supporter", join.getType());
    assertEquals("home", join.getTargetFactionId());
    assertEquals(0, join.getCauseIndex());
    RequestManager.addRequest(sender, target, join);
    assertDoesNotThrow(() -> FactionManager.acceptMovementJoinRequest(target));
    verify(movement, never()).join(any(), any());
    RequestManager.remove(target);
    MovementLeaderTargetRequest leader = new MovementLeaderTargetRequest(null, "Alice", "movement", 0, "Bob");
    assertEquals(0, leader.getCauseIndex());
    RequestManager.addRequest(sender, target, leader);
    when(home.canBecomeLeader("Bob")).thenReturn(true);
    FactionManager.acceptMovementLeaderTargetRequest(target);
    assertNull(cause.getProposal().getTarget());
  }

  /** External daily services stay bounded; the manager and transfer buffer remain real. */
  private final class DailyServices implements AutoCloseable {
    final MockedStatic<InactivityService> inactivity = mockStatic(InactivityService.class);
    final MockedStatic<ContractAccrualService> accrual = mockStatic(ContractAccrualService.class);
    final MockedStatic<ContractTerminationService> termination = mockStatic(ContractTerminationService.class);
    final MockedStatic<PostSettlementPayouts> payouts = mockStatic(PostSettlementPayouts.class);
    final MockedStatic<WarReparationsService> reparations = mockStatic(WarReparationsService.class);
    final MockedStatic<TradeGraph> routes = mockStatic(TradeGraph.class);
    final MockedStatic<FactionCleanup> cleanup = mockStatic(FactionCleanup.class);
    final MockedStatic<PlayerEconomyManager> players = mockStatic(PlayerEconomyManager.class);
    final MockedStatic<Ledger> histories = mockStatic(Ledger.class);
    final PlayerEconomyManager economy = mock(PlayerEconomyManager.class);
    final VehicleUpkeepService upkeep = mock(VehicleUpkeepService.class);

    DailyServices() {
      players.when(PlayerEconomyManager::get).thenReturn(economy);
      histories.when(() -> Ledger.collectHistoryDay(any())).thenReturn(Map.of());
      when(fixture.ui.plugin.getVehicleUpkeepService()).thenReturn(upkeep);
    }

    @Override
    public void close() {
      histories.close();
      players.close();
      cleanup.close();
      routes.close();
      reparations.close();
      payouts.close();
      termination.close();
      accrual.close();
      inactivity.close();
    }
  }

  @Test
  void dailySettlementNetsTransfersRoundsBalancesAndPublishesHistoryBeforeClearingBuffer() {
    Faction home = faction("home", "Alice");
    Guild payer = guild(home, "payer", "Alice");
    Guild recipient = guild(home, "recipient", "Bob");
    Guild zero = guild(home, "zero", "Carol");
    Guild noBank = guild(home, "no-bank", "Dave");
    when(noBank.getBank()).thenReturn(null);
    Loan loan = mock(Loan.class);
    when(loan.getId()).thenReturn("loan");
    when(payer.getLoanHandler().getLoansGiven()).thenReturn(Arrays.asList(null, loan));
    Ledger payerLedger = payer.getLedger();
    AtomicReference<DailyGuildTransfers> collected = new AtomicReference<>();
    doAnswer(call -> {
      DailyGuildTransfers buffer = call.getArgument(0);
      collected.set(buffer);
      buffer.add(payer, recipient, 7.12);
      buffer.addExternalDelta(payer, 10.004);
      buffer.addExternalDelta(recipient, -1.004);
      buffer.addExternalDelta(zero, 3.0);
      buffer.addExternalDelta(zero, -3.0);
      buffer.addExternalDelta(noBank, 2.0);
      return null;
    }).when(payerLedger).populateDailyTransfers(any());
    try (DailyServices daily = new DailyServices()) {
      Map<LedgerHistory.Source, Map<String, Double>> before = new HashMap<>();
      daily.histories.when(() -> Ledger.collectHistoryDay(any())).thenReturn(Map.of(payer, before));
      daily.payouts.when(() -> PostSettlementPayouts.apply(any(), any(), any(), any()))
          .thenAnswer(call -> {
            DailyGuildTransfers buffer = call.getArgument(0);
            assertEquals(7.12, buffer.getTransfers().get(payer).get(recipient));
            verify(payer.getBank()).deposit(2.88);
            verify(recipient.getBank()).deposit(6.12);
            return null;
          });

      manager.settleIncome();

      verify(payer.getLedger().getHistory()).closeDay(same(before));
      verify(recipient.getLedger().getHistory()).closeDay(null);
      verify(zero.getBank(), never()).deposit(anyDouble());
      verify(loan).tickDay();
      verify(payer).refreshDividendEligibility();
      verify(recipient).refreshDividendEligibility();
      daily.accrual.verify(ContractAccrualService::accrueDailyAndPush);
      daily.termination.verify(ContractTerminationService::tickDay);
      daily.reparations.verify(() -> WarReparationsService.tickAfterDailySettlement(home));
      verify(daily.upkeep).processDailyUpkeep();
      verify(fixture.ui.plugin).recordVehicleOwners();
      assertTrue(collected.get().getTransfers().isEmpty());
      assertTrue(collected.get().getExternalDeltas().isEmpty());
    }
  }

  @Test
  void settlementLiquidatesWhileAssetsShrinkAndStopsAStalledLiquidator() {
    Faction home = faction("home", "Alice");
    Guild shrinking = guild(home, "shrinking", "Alice");
    Guild stalled = guild(home, "stalled", "Bob");
    AtomicInteger remaining = new AtomicInteger(5);
    when(shrinking.isBankrupt()).thenAnswer(call -> remaining.get() > 3);
    when(shrinking.canLiquidate()).thenReturn(true);
    when(shrinking.getSize()).thenAnswer(call -> remaining.get());
    doAnswer(call -> { remaining.decrementAndGet(); return null; }).when(shrinking).liquidateRandom();
    when(stalled.isBankrupt()).thenReturn(true);
    when(stalled.canLiquidate()).thenReturn(true);
    when(stalled.getSize()).thenReturn(3);
    Ledger shrinkingLedger = shrinking.getLedger();
    doAnswer(call -> {
      DailyGuildTransfers buffer = call.getArgument(0);
      buffer.addExternalDelta(shrinking, -20);
      buffer.addExternalDelta(stalled, -20);
      return null;
    }).when(shrinkingLedger).populateDailyTransfers(any());
    try (DailyServices daily = new DailyServices()) {
      manager.settleIncome();
      verify(shrinking, times(2)).liquidateRandom();
      assertEquals(3, remaining.get());
      verify(stalled).liquidateRandom();
      verify(daily.upkeep).processDailyUpkeep();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dailyFailureIsLoggedAndDoesNotPreventOtherGuildsOrFinalMaintenance(boolean availableLogger) {
    Faction home = faction("home", "Alice");
    Guild broken = guild(home, "broken", "Alice");
    Guild healthy = guild(home, "healthy", "Bob");
    Logger logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(availableLogger ? logger : null);
    IllegalStateException failure = new IllegalStateException("broken ledger");
    Ledger brokenLedger = broken.getLedger();
    Ledger healthyLedger = healthy.getLedger();
    doThrow(failure).when(brokenLedger).populateDailyTransfers(any());
    doAnswer(call -> { ((DailyGuildTransfers) call.getArgument(0)).addExternalDelta(healthy, 10); return null; })
        .when(healthyLedger).populateDailyTransfers(any());
    try (DailyServices daily = new DailyServices()) {
      assertDoesNotThrow(manager::settleIncome);
      verify(healthy.getBank()).deposit(10.0);
      verify(healthy.getLedger().getHistory()).closeDay(null);
      verify(daily.upkeep).processDailyUpkeep();
      if (availableLogger) verify(logger).log(Level.SEVERE, "Daily tick failed during ledger broken", failure);
    }
  }

  @Test
  void hourlyAndFiveMinuteTicksAdvancePowerAndPillageAndWarnOnlyOnlineOverCapLeaders() {
    Player leader = fixture.player("Alice");
    Faction home = faction("home", "Alice");
    Faction offline = faction("offline", "Bob");
    Faction landless = faction("landless", "Carol");
    home.getProvinces().add(1);
    offline.getProvinces().add(2);
    Guild guild = guild(home, "guild", "Alice");
    try (DailyServices daily = new DailyServices(); MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
      titles.when(() -> TitleManager.overProvinceCap(home)).thenReturn(true);
      FactionManager.timer = 3599;
      manager.time();
      assertEquals(3600, FactionManager.getTimer());
      assertEquals(82800, FactionManager.getSecondsUntilNewDay());
      verify(home.getGovernment()).powerTick();
      verify(guild).tickPillageHits();
      verify(leader).sendMessage("§cYou are over your province cap! Other nations can steal your provinces from you!");
      verify(leader).sendMessage("§cGet more prestige or unclaim provinces to counteract this!");
      titles.verify(() -> TitleManager.overProvinceCap(offline), never());
      titles.verify(() -> TitleManager.overProvinceCap(landless), never());
      verify(daily.upkeep).warnBankShortfalls(fixture.online.values(), 82800);
      daily.inactivity.verify(() -> InactivityService.tickIfDue(FactionManager.factions));
    }
    FactionManager.timer = -4;
    assertEquals(86400, FactionManager.getSecondsUntilNewDay());
    FactionManager.timer = 90000;
    assertEquals(0, FactionManager.getSecondsUntilNewDay());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dayRolloverContinuesAfterOneFactionFailureAndDoesNotPayAgainNextSecond(boolean tracksChanged) {
    Faction broken = faction("broken", "Alice");
    Faction healthy = faction("healthy", "Bob");
    IllegalStateException failure = new IllegalStateException("one faction failed");
    doThrow(failure).when(broken).newDay();
    Logger logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);
    when(fixture.ui.plugin.refreshTrackProvinces()).thenReturn(tracksChanged);
    try (DailyServices daily = new DailyServices()) {
      FactionManager.timer = 86399;
      FactionManager.day = 5;
      manager.time();
      assertEquals(0, FactionManager.getTimer());
      assertEquals(6, FactionManager.getDay());
      verify(healthy).newDay();
      verify(daily.economy).clearAllDaily();
      daily.cleanup.verify(() -> FactionCleanup.advanceOfflineDays(FactionManager.factions));
      daily.routes.verify(TradeGraph::forgetRoutes);
      verify(fixture.provinces, tracksChanged ? never() : times(1)).recalculate();
      verify(logger).log(Level.SEVERE, "Daily tick failed during faction broken", failure);
      verify(daily.upkeep).processDailyUpkeep();
      manager.time();
      assertEquals(1, FactionManager.getTimer());
      assertEquals(6, FactionManager.getDay());
      verify(daily.upkeep, times(1)).processDailyUpkeep();
      verify(healthy, times(1)).newDay();
    }
  }

  @Test
  void runRestoresClockInitializesFactionsRepairsAlliesAndSchedulesTheRealTick() {
    Faction home = faction("home", "Alice");
    Faction ally = faction("ally", "Bob");
    RelationType allyType = fixture.relationType("ally", Map.of("mutual", true));
    Relation reverse = new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude(), 17);
    ally.setRelation(home, reverse);
    databases.close();
    databases = mockConstruction(Database.class, (database, context) -> {
      when(database.getTimer()).thenReturn(42);
      when(database.getDay()).thenReturn(9);
    });
    try (DailyServices daily = new DailyServices(); MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
      relations.when(() -> RelationManager.getAllies(home)).thenReturn(List.of(ally));
      assertFalse(FactionManager.isLoaded());
      manager.run();
      assertTrue(FactionManager.isLoaded());
      assertEquals(42, FactionManager.getTimer());
      assertEquals(9, FactionManager.getDay());
      verify(home.getLawHandler()).apply();
      verify(home).countyCheck();
      verify(home.getGovernment()).loadMovements();
      verify(home).ping();
      assertSame(allyType, reverse.getType());
      assertEquals(17, reverse.getOpinion());
      assertEquals(1, fixture.ui.repeatingTasks.size());
      fixture.ui.repeatingTasks.getFirst().run();
      verify(home).tick();
      verify(ally).tick();
      verify(fixture.map).tick();
      relations.verify(RelationManager::tick);
      verify(fixture.inventory.getUpdater()).updateInventory();
      assertEquals(43, FactionManager.getTimer());
      manager.fixRelations();
      assertSame(allyType, reverse.getType());
    }
  }

  private Block booth() {
    Block block = mock(Block.class);
    when(block.getLocation()).thenReturn(new Location(fixture.ui.world, 4, 64, 7));
    return block;
  }

  private PlayerInteractEvent interact(Player player, Block block, org.bukkit.event.block.Action action) {
    return new PlayerInteractEvent(player, action, null, block, BlockFace.UP);
  }

  @Test
  void ordinaryInteractionsAndUnknownBlocksDoNotOpenOrCancelVoting() {
    Player player = fixture.player("Alice");
    faction("home", "Alice");
    Block block = booth();
    PlayerInteractEvent left = interact(player, block, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK);
    PlayerInteractEvent other = interact(player, block, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
    manager.openBooth(left);
    manager.openBooth(other);
    assertFalse(left.isCancelled());
    assertFalse(other.isCancelled());
    assertNull(manager.getByVotingBooth(null));
    assertNull(manager.getByVotingBooth(block));
  }

  @Test
  void aBoothWithoutElectionsGivesFeedbackAndSuppressesDuplicateHandEvents() {
    Player player = fixture.player("Alice");
    Location playerLocation = player.getLocation();
    Faction home = faction("home", "Alice");
    Block block = booth();
    when(home.getGovernment().isVotingBooth(block.getLocation())).thenReturn(true);
    PlayerInteractEvent first = interact(player, block, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
    PlayerInteractEvent second = interact(player, block, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
    manager.openBooth(first);
    manager.openBooth(second);
    assertTrue(first.isCancelled());
    assertTrue(second.isCancelled());
    assertSame(home, manager.getByVotingBooth(block));
    verify(player, times(1)).sendMessage("§cThis faction has no elections");
    verify(player, times(1)).playSound(playerLocation, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
  }

  @Test
  void votingRequiresCurrentRightsBeforeOpeningElectionMenu() {
    Faction home = faction("home", "Alice");
    Player denied = fixture.player("Alice");
    Player allowed = fixture.player("Bob");
    Location deniedLocation = denied.getLocation();
    Block block = booth();
    when(home.getGovernment().isVotingBooth(block.getLocation())).thenReturn(true);
    when(home.getGovernment().hasElections()).thenReturn(true);
    when(home.canVote("Bob")).thenReturn(true);
    try (MockedConstruction<InventoryManager> menus = mockConstruction(InventoryManager.class)) {
      PlayerInteractEvent refusal = interact(denied, block, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
      manager.openBooth(refusal);
      assertTrue(refusal.isCancelled());
      assertTrue(menus.constructed().isEmpty());
      verify(denied).sendMessage("§cYou have no voting rights in this faction");
      verify(denied).playSound(deniedLocation, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      PlayerInteractEvent acceptance = interact(allowed, block, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
      manager.openBooth(acceptance);
      assertTrue(acceptance.isCancelled());
      verify(menus.constructed().getFirst()).electionView(allowed, home);
    }
  }

  @Test
  void nativeBlockEventsRegisterAndRemoveOnlyMatchingBoothsAndAvoidDuplicates() {
    Player player = fixture.player("Alice");
    Location playerLocation = player.getLocation();
    Faction home = faction("home", "Alice");
    Block block = booth();
    BlockPlaceEvent place = mock(BlockPlaceEvent.class);
    when(place.getPlayer()).thenReturn(player);
    when(place.getBlock()).thenReturn(block);
    BlockBreakEvent broken = new BlockBreakEvent(block, player);
    try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class, RETURNS_DEEP_STUBS)) {
      manager.placeVotingBooth(place);
      manager.breakVotingBooth(broken);
      verify(home.getGovernment(), never()).addVotingBooth(any());
      verify(home.getGovernment(), never()).removeVotingBooth(any());
      when(TLibs.getBlockAPI().getChecker().checkBlock(block, Cache.votingBlock)).thenReturn(true);
      FactionManager.factions.clear();
      manager.placeVotingBooth(place);
      FactionManager.factions.add(home);
      manager.placeVotingBooth(place);
      verify(home.getGovernment()).addVotingBooth(block.getLocation());
      verify(player).sendMessage("§aVoting booth added!");
      verify(player).playSound(playerLocation, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
      when(home.getGovernment().isVotingBooth(block.getLocation())).thenReturn(true);
      manager.placeVotingBooth(place);
      verify(home.getGovernment(), times(1)).addVotingBooth(any());
      manager.breakVotingBooth(broken);
      verify(home.getGovernment()).removeVotingBooth(block.getLocation());
      verify(player).sendMessage("§cVoting booth removed!");
      verify(player).playSound(playerLocation, Sound.BLOCK_ANVIL_LAND, 1f, 1f);
      when(home.getGovernment().isVotingBooth(block.getLocation())).thenReturn(false);
      manager.breakVotingBooth(broken);
      verify(home.getGovernment(), times(1)).removeVotingBooth(any());
    }
  }

  @Test
  void furnitureConfiguredBoothsAreLeftToTheFurnitureListener() {
    Player player = fixture.player("Alice");
    Block block = booth();
    Cache.votingBlock = "iaf.tfmc:voting_booth";
    BlockPlaceEvent place = mock(BlockPlaceEvent.class);
    BlockBreakEvent broken = new BlockBreakEvent(block, player);
    try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class)) {
      manager.placeVotingBooth(place);
      manager.breakVotingBooth(broken);
      tlibs.verifyNoInteractions();
      verifyNoInteractions(place);
    }
  }

  @Test
  void aPartiallyLoadedSnapshotCanBeInspectedWithoutInventingRelationsOrGuilds() {
    Faction complete = faction("complete", "Alice");
    Guild guild = guild(complete, "guild", "Alice");
    manager.start(new ArrayList<>(Arrays.asList(null, complete, null)));
    assertDoesNotThrow(FactionManager::loadRelations);
    assertEquals(List.of(guild), FactionManager.getAllGuilds());
    assertTrue(complete.getRelations().isEmpty());
    Faction partial = mock(Faction.class);
    when(partial.getProvinces()).thenReturn(List.of());
    manager.start(new ArrayList<>(List.of(partial)));
    assertTrue(FactionManager.getAllGuilds().isEmpty());
    try (DailyServices daily = new DailyServices()) {
      FactionManager.timer = 3599;
      assertDoesNotThrow(manager::time);
      assertEquals(3600, FactionManager.getTimer());
      verify(daily.upkeep).warnBankShortfalls(fixture.online.values(), 82800);
    }
  }

  @Test
  void addingFactionWithAnUnplacedMainGuildDoesNotInventACapital() {
    Faction home = faction("home", "Alice");
    home.getProvinces().add(4);
    when(home.getCapital()).thenReturn(-1);
    when(home.getOrCreateMainGuild().hasCapital()).thenReturn(false);
    FactionManager.factions.clear();
    try (MockedStatic<net.tfminecraft.simplefactions.espionage.EspionageService> espionage = mockStatic(net.tfminecraft.simplefactions.espionage.EspionageService.class)) {
      FactionManager.addFaction(home);
      assertEquals(List.of(home), FactionManager.factions);
      espionage.verify(() -> net.tfminecraft.simplefactions.espionage.EspionageService.initializeFounder(home));
      verify(home, never()).setCapital(anyInt(), anyBoolean());
    }
  }
}
