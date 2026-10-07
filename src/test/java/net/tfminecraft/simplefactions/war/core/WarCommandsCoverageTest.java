package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleTickService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsService;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class WarCommandsCoverageTest {
  private FactionDomainFixture fixture;
  private MockedConstruction<Database> databases;
  private Player alice;
  private Command command;
  private WarCommandManager commands;
  private WarTabCompletion tabs;
  private Faction attacker;
  private Faction defender;
  private List<Battle> previousBattles;
  private List<Warband> previousBands;
  private Duration previousClock;
  private boolean previousDevmode;
  private final Map<Field, Object> previousCache = new LinkedHashMap<>();
  private MockedStatic<TLibs> tlibs;
  private MockedStatic<BattlePersistenceService> battlePersistence;
  private Map<Integer, List<WarCommitment>> commitments;
  private Map<Integer, List<WarCommitment>> previousCommitments;

  @BeforeEach
  void setUp() {
    // Initialize the persistence singleton before construction mocking so its real database
    // remains available to other test classes after this scope closes.
    battlePersistence = mockStatic(BattlePersistenceService.class);
    databases = mockConstruction(Database.class);
    fixture = new FactionDomainFixture();
    alice = fixture.player("Alice");
    when(alice.hasPermission("simplefactions.admin")).thenReturn(true);
    command = mock(Command.class);
    when(command.getName()).thenReturn("war");
    commands = new WarCommandManager();
    tabs = new WarTabCompletion();
    attacker = fixture.saved("iron", "Alice");
    defender = fixture.saved("river", "Bob");
    previousBattles = new ArrayList<>(BattleManager.get());
    previousBands = new ArrayList<>(WarbandManager.get());
    BattleManager.get().clear();
    WarbandManager.get().clear();
    previousClock = CampaignClock.getOffset();
    CampaignClock.reset();
    previousDevmode = WarDevMode.isEnabled();
    WarDevMode.resetForTests();
    try {
      Field field = WarCommitmentService.class.getDeclaredField("commitmentsByWar");
      field.setAccessible(true);
      commitments = (Map<Integer, List<WarCommitment>>) field.get(null);
      previousCommitments = new LinkedHashMap<>(commitments);
      commitments.clear();
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
    cache("warBattleWindowStartHour", 20);
    cache("warBattleWindowEndHour", 24);
    cache("warVoteCloseHour", 16);
    cache("warDevmodePhantomCount", 2);
    cache("warReparationsIncomePercent", 5d);
    cache("warReparationsDays", 7);
    cache("battleCampaignTemplateField", "");
    when(Bukkit.createBossBar(anyString(), any(), any(), any()))
        .thenAnswer(call -> mock(BossBar.class));
    when(Bukkit.getOfflinePlayer(anyString()))
        .thenAnswer(
            call -> {
              OfflinePlayer offline = mock(OfflinePlayer.class);
              when(offline.getUniqueId())
                  .thenReturn(
                      java.util.UUID.nameUUIDFromBytes(
                          call.<String>getArgument(0)
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
              return offline;
            });
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemsAdderItem(anyString()))
        .thenAnswer(call -> new ItemStack(Material.PAPER));
    tlibs = mockStatic(TLibs.class);
    tlibs.when(TLibs::getItemAPI).thenReturn(api);
  }

  @AfterEach
  void close() {
    WarDevMode.resetForTests();
    if (previousDevmode) WarDevMode.setEnabled(true);
    CampaignClock.reset();
    CampaignClock.add(previousClock);
    BattleManager.get().clear();
    BattleManager.get().addAll(previousBattles);
    WarbandManager.get().clear();
    WarbandManager.get().addAll(previousBands);
    commitments.clear();
    commitments.putAll(previousCommitments);
    previousCache.forEach(
        (field, value) -> {
          try {
            field.set(null, value);
          } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
          }
        });
    tlibs.close();
    battlePersistence.close();
    fixture.close();
    databases.close();
  }

  private boolean run(String... args) {
    return commands.onCommand(alice, command, "war", args);
  }

  private List<String> complete(String... args) {
    return tabs.onTabComplete(alice, command, "war", args);
  }

  private void cache(String name, Object value) {
    try {
      Field field = Cache.class.getField(name);
      previousCache.putIfAbsent(field, field.get(null));
      field.set(null, value);
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
  }

  private long writes(War war) {
    return databases.constructed().stream()
        .flatMap(db -> mockingDetails(db).getInvocations().stream())
        .filter(call -> call.getMethod().getName().equals("saveWar") && call.getArgument(0) == war)
        .count();
  }

  private War war(int id) {
    War war = new War(id, attacker, defender);
    war.setGoal(WarGoalType.SUBJUGATE);
    war.setWarType(WarType.SUBJUGATE);
    war.setBattleDay(LocalDate.of(2026, 10, 10));
    war.setCampaignProvinces(new ArrayList<>(List.of(10, 20)));
    war.setCampaignStartProvinceId(10);
    war.setObjectiveProvinceId(20);
    war.setCursorIndex(1);
    war.setInitiativeAttacker(3);
    war.setInitiativeDefender(2);
    war.setOccupiedByAttacker(new ArrayList<>());
    war.setOccupiedByDefender(new ArrayList<>());
    WarManager.addWar(war);
    return war;
  }

  @Test
  void uppercaseAdminCommandsRemainUsableInTurkishLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertTrue(run("admin", "TIME", "status"));
      verify(alice).sendMessage("§7Offset: §ereal time");
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void uppercaseCompletionsAndFactionFiltersAreIndependentOfServerLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals(List.of("list"), complete("LI"));
      assertEquals(List.of("win"), complete("admin", "WI"));
      assertEquals(List.of("iron"), complete("admin", "factions", "I"));
      assertTrue(run("admin", "factions", "IRON"));
      verify(alice).sendMessage("§firon §7- iron");
    } finally {
      Locale.setDefault(previous);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
  void nonFiniteReparationPercentagesCannotCreateOrPersistObligations(String percentage) {
    assertTrue(run("admin", "reparations", "iron", "river", percentage, "3"));
    assertTrue(attacker.getWarReparationsObligations().isEmpty());
    assertFalse(WarReparationsService.apply(attacker, defender, Double.parseDouble(percentage), 3));
    assertTrue(attacker.getWarReparationsObligations().isEmpty());
    for (Database database : databases.constructed()) verify(database, never()).saveFaction(any());
  }

  @Test
  void consoleUsagePermissionsAndPlayerListRouteThroughPublicCommandEntry() {
    ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    assertTrue(commands.onCommand(console, command, "war", new String[] {"admin", "end", "1"}));
    verifyNoInteractions(console);
    assertTrue(run());
    verify(alice).sendMessage("§eUsage: §a/war list");
    verify(alice).sendMessage(contains("Staff:"));
    when(alice.hasPermission("simplefactions.admin")).thenReturn(false);
    assertTrue(run("admin", "devmode", "on"));
    assertFalse(WarDevMode.isEnabled());
    verify(alice).sendMessage(contains("You do not have access"));
    assertTrue(run("list"));
    assertNotEquals("Crafting", alice.getOpenInventory().getTitle());
    assertNotNull(alice.getOpenInventory().getTopInventory().getHolder());
  }

  static Stream<Arguments> rejectedCommands() {
    return Stream.of(
        Arguments.of("wrong", "Unknown subcommand"),
        Arguments.of("admin", "Usage:"),
        Arguments.of("admin wrong", "Unknown admin subcommand"),
        Arguments.of("admin end", "Usage:"),
        Arguments.of("admin end bad", "War id must be a number"),
        Arguments.of("admin end 999", "No war by that id"),
        Arguments.of("admin win 1", "Usage:"),
        Arguments.of("admin win bad attacker", "War id must be a number"),
        Arguments.of("admin win 1 neither", "Winner must be attacker or defender"),
        Arguments.of("admin win 999 attacker", "No war by that id"),
        Arguments.of("admin status", "Usage:"),
        Arguments.of("admin status bad", "War id must be a number"),
        Arguments.of("admin status 999", "No war by that id"),
        Arguments.of("admin path", "Usage:"),
        Arguments.of("admin path bad", "War id must be a number"),
        Arguments.of("admin path 999", "No war by that id"),
        Arguments.of("admin time", "Usage:"),
        Arguments.of("admin time add", "Usage:"),
        Arguments.of("admin time add nonsense", "Invalid duration"),
        Arguments.of("admin time skip-to-battle-day", "Usage:"),
        Arguments.of("admin time skip-to-battle-day bad", "War id must be a number"),
        Arguments.of("admin time skip-to-battle-day 999", "Unknown war id"),
        Arguments.of("admin time wrong", "Unknown subcommand"),
        Arguments.of("admin devmode", "Usage:"),
        Arguments.of("admin devmode wrong", "Usage:"),
        Arguments.of("admin raid", "Usage:"),
        Arguments.of("admin raid wrong 1", "Usage:"),
        Arguments.of("admin raid resetquota bad", "War id must be a number"),
        Arguments.of("admin raid resetquota 999", "No war by that id"),
        Arguments.of("admin raid resetquota 1 wrong", "Side must be aggressor, defender, or both"),
        Arguments.of("admin reparations iron", "Usage:"),
        Arguments.of("admin reparations iron river 5 7 extra", "Usage:"),
        Arguments.of("admin schedule", "Usage:"),
        Arguments.of("admin schedule bad forcequorum", "War id must be a number"),
        Arguments.of("admin schedule 999 forcequorum", "No war by that id"),
        Arguments.of("admin schedule 1 castvote", "Usage:"),
        Arguments.of("admin schedule 1 castvote bad", "Hour must be a number"),
        Arguments.of("admin schedule 1 castvote 2147483648", "Hour must be a number"),
        Arguments.of("admin schedule 1 castvote 15", "Hour must be within the battle window"),
        Arguments.of(
            "admin schedule 1 castvote 21 wrong", "Side must be attacker, defender, or both"),
        Arguments.of("admin schedule 1 setscheduled", "Usage:"),
        Arguments.of("admin schedule 1 setscheduled invalid", "Could not parse instant"),
        Arguments.of("admin schedule 1 winbattle", "Usage:"),
        Arguments.of("admin schedule 1 winbattle neither", "Winner must be attacker or defender"),
        Arguments.of("admin schedule 1 choice", "Usage:"),
        Arguments.of("admin schedule 1 choice invalid", "Post-battle choice not available"),
        Arguments.of("admin schedule 1 wrong", "Unknown subcommand"));
  }

  @ParameterizedTest
  @MethodSource("rejectedCommands")
  void rejectedArgumentsLeaveCampaignStateAndPersistenceUnchanged(
      String input, String expectedMessage) {
    War war = war(1);
    long before = writes(war);
    assertTrue(run(input.split(" ")));
    verify(alice).sendMessage(contains(expectedMessage));
    assertTrue(war.isActive());
    assertEquals(LocalDate.of(2026, 10, 10), war.getBattleDay());
    assertTrue(war.getBattleVotes().isEmpty());
    assertTrue(war.getCampaignRaidsUsed().isEmpty());
    assertEquals(before, writes(war));
    assertEquals(Duration.ZERO, CampaignClock.getOffset());
  }

  @Test
  void inactiveWarsCannotBeWonOrHaveTheirCampaignRegenerated() {
    War war = war(1);
    war.end(WarEndReason.ADMIN_END);
    long before = writes(war);
    assertTrue(run("admin", "win", "1", "attacker"));
    assertTrue(run("admin", "path", "1"));
    verify(alice, times(2)).sendMessage("§cWar is not active");
    assertFalse(war.isActive());
    assertEquals(before, writes(war));
  }

  @ParameterizedTest
  @ValueSource(strings = {"end", "attacker", "defender"})
  void endingAWarUpdatesItsRealStatusAndRemovesItFromTheActiveRegistry(String winner) {
    War war = war(1);
    if (winner.equals("end")) assertTrue(run("admin", "end", "1"));
    else assertTrue(run("admin", "win", "1", winner));
    assertFalse(war.isActive());
    assertNull(WarManager.getById(1));
    assertTrue(
        databases.constructed().stream()
            .anyMatch(
                db ->
                    mockingDetails(db).getInvocations().stream()
                        .anyMatch(
                            call ->
                                call.getMethod().getName().equals("deleteWar")
                                    && call.getArgument(0) == war)));
    verify(alice).sendMessage(contains("Ended war"));
  }

  @Test
  void statusPrintsTheCurrentWarAndUnavailablePathsDoNotAlterIt() {
    War war = war(1);
    assertTrue(run("admin", "status", "1"));
    verify(alice).sendMessage(contains("\"id\":1"));
    long before = writes(war);
    assertTrue(run("admin", "path", "1"));
    verify(alice).sendMessage("§cCould not regenerate campaign route");
    assertEquals(List.of(10, 20), war.getCampaignProvinces());
    assertEquals(before, writes(war));
  }

  @Test
  void campaignRouteRegenerationReportsAndPersistsTheActualPath() {
    cache("tradeCarry", new EnumMap<Terrain, Double>(Map.of(Terrain.PLAINS, 0.85)));
    cache("warPathfinderNeutralPenalty", 8d);
    cache("warPathfinderSeaPassEnabled", true);
    cache("warPathfinderWaterCost", 0d);
    cache("warInitiativeFactor", 1.5d);
    EnumMap<WarGoalType, Integer> limits = new EnumMap<>(WarGoalType.class);
    for (WarGoalType goal : WarGoalType.values()) limits.put(goal, 4);
    cache("warGoalMaxBattles", limits);
    for (int id : List.of(5, 10, 20, 30))
      fixture.provinceData.put(id, new Province(id, "PLAINS", 50, id * 10, id * 10));
    List<Integer> route = List.of(5, 10, 20, 30);
    for (int index = 1; index < route.size(); index++) {
      fixture.provinceData.get(route.get(index - 1)).addNeighbour(route.get(index));
      fixture.provinceData.get(route.get(index)).addNeighbour(route.get(index - 1));
    }
    attacker.setCapital(5, true, false);
    defender.setCapital(30, true, false);
    War war = war(1);
    try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
      titles.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 30));
      titles.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
      titles.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
      titles.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
      titles.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);
      long before = writes(war);
      assertTrue(run("admin", "path", "1"));
      assertEquals(route, war.getCampaignProvinces());
      assertEquals(30, war.getObjectiveProvinceId());
      assertTrue(writes(war) > before);
      verify(alice).sendMessage(contains("Regenerated campaign for war 1"));
      verify(alice).sendMessage(contains("(province 20)"));
    }
  }

  @Test
  void factionLookupReportsNoMatchesAndCapsLargeResultsWithAnExplicitRemainder() {
    assertTrue(run("admin", "factions", "none"));
    verify(alice).sendMessage("§cNo faction matches §fnone");
    assertTrue(run("admin", "factions", "one", "extra"));
    verify(alice).sendMessage(contains("Usage: /war admin factions"));
    for (int index = 0; index < 41; index++) fixture.saved("group" + index, "Leader" + index);
    assertTrue(run("admin", "factions", "group"));
    verify(alice).sendMessage("§7Factions (41):");
    verify(alice).sendMessage(contains("1 more. Narrow the filter"));
    verify(alice, never()).sendMessage("§fgroup40 §7- group40");
    FactionManager.factions.clear();
    assertTrue(run("admin", "factions"));
    verify(alice).sendMessage("§cNo faction matches §f-");
  }

  @Test
  void staffLookupsTolerateIncompletePublicRegistryEntriesWithoutLosingValidSiblings() {
    var missingName = fixture.data("unnamed", "MissingNameLeader");
    missingName.name = null;
    fixture.saved(missingName);
    // The registry and id setter are public plugin integration APIs. Staff lookups must
    // remain usable if another plugin publishes an incomplete entry.
    Faction incomplete = fixture.saved("incomplete", "IncompleteLeader");
    incomplete.setId(null);
    FactionManager.factions.add(null);
    assertEquals(List.of("iron", "river", "unnamed"), complete("admin", "factions", ""));
    assertTrue(run("admin", "factions", "iron"));
    verify(alice).sendMessage("§7Factions (1):");
    verify(alice).sendMessage("§firon §7- iron");
    assertNull(incomplete.getId());
  }

  @Test
  void clockCommandsPreserveExactOffsetAndReportBattleDayAlignment() {
    try (MockedStatic<BattleScheduleTickService> ticks =
        mockStatic(BattleScheduleTickService.class)) {
      assertTrue(run("admin", "time", "add", "1h", "31m"));
      assertEquals(Duration.ofMinutes(91), CampaignClock.getOffset());
      assertTrue(run("admin", "time", "status"));
      verify(alice).sendMessage("§7Spoofed: §eyes");
      assertTrue(run("admin", "time", "reset"));
      assertEquals(Duration.ZERO, CampaignClock.getOffset());
      War war = war(1);
      assertTrue(run("admin", "time", "skip-to-battle-day", "1"));
      assertEquals(war.getBattleDay(), BattleScheduleService.battleDayDate(CampaignClock.now()));
      assertEquals(0, BattleScheduleService.battleDayHour(CampaignClock.now()));
      ticks.verify(BattleScheduleTickService::onClockOffsetChanged, times(3));
    }
  }

  @Test
  void devmodeTogglesAndClearsRealDummyRosterEntries() {
    assertTrue(run("admin", "devmode", "on"));
    assertTrue(WarDevMode.isEnabled());
    verify(alice).sendMessage("§aWar devmode enabled.");
    assertTrue(run("admin", "devmode", "status"));
    verify(alice).sendMessage(contains("§aenabled"));
    Warband band = new Warband("dummies", alice);
    WarbandManager.addWarband(band);
    WarDevMode.seedDummyMembers(band, 2);
    assertEquals(2, band.getDummyMemberCount());
    assertTrue(run("admin", "devmode", "off"));
    assertFalse(WarDevMode.isEnabled());
    assertEquals(0, band.getDummyMemberCount());
    verify(alice).sendMessage("§aWar devmode disabled. Cleared dummies from 1 warbands.");
    assertTrue(run("admin", "devmode", "off"));
    verify(alice).sendMessage("§aWar devmode disabled.");
  }

  @Test
  void enablingDevmodeFillsBothRealCampaignSideRostersAndReportsTheCount() {
    War war = war(1);
    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign-front");
    battle.setWarId(war.getId());
    BattleManager.addBattle(battle);
    assertTrue(run("admin", "devmode", "on"));
    assertTrue(WarDevMode.isEnabled());
    assertEquals(2, WarbandManager.get().size());
    for (var side : battle.getSides()) {
      assertEquals(1, side.getBands().size());
      assertEquals(2, side.getBands().getFirst().getDummyMemberCount());
    }
    verify(alice).sendMessage("§aWar devmode enabled. Filled 2 campaign side warbands.");
    assertTrue(run("admin", "devmode", "off"));
    assertTrue(WarbandManager.get().stream().allMatch(band -> band.getDummyMemberCount() == 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"default", "aggressor", "defender", "both"})
  void quotaResetChangesOnlyTheRequestedCoalitionAndPersistsIt(String scope) {
    War war = war(1);
    for (CampaignCoalition coalition : CampaignCoalition.values())
      war.getCampaignRaidsUsed().put(coalition.toJson(), war.getBattleDay().toString());
    long before = writes(war);
    if (scope.equals("default")) assertTrue(run("admin", "raid", "resetquota", "1"));
    else assertTrue(run("admin", "raid", "resetquota", "1", scope));
    boolean both = scope.equals("both") || scope.equals("default");
    assertEquals(both ? 0 : 1, war.getCampaignRaidsUsed().size());
    if (!both) assertFalse(war.getCampaignRaidsUsed().containsKey(scope));
    assertEquals(before + 1, writes(war));
    verify(alice).sendMessage(contains(both ? "cleared 2 entries" : "cleared 1 entry"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reparationsApplyExplicitOrConfiguredTermsToTheRealPayer(boolean explicit) {
    if (explicit) assertTrue(run("admin", "reparations", "iron", "river", "12.5", "3"));
    else assertTrue(run("admin", "reparations", "iron", "river"));
    var obligation = attacker.getWarReparationsObligations().getFirst();
    assertEquals("river", obligation.getPayeeFactionId());
    assertEquals(explicit ? 12.5 : 5, obligation.getIncomePercent());
    assertEquals(explicit ? 3 : 7, obligation.getDaysRemaining());
    assertTrue(defender.getWarReparationsObligations().isEmpty());
    verify(alice).sendMessage(contains("§aAdded reparations: iron pays river"));
  }

  @Test
  void scheduleCommandsChangeVotesQuorumAndDayAndPersistOnlySuccessfulTransitions() {
    attacker.addMember("Alice");
    defender.addMember("Bob");
    War war = war(1);
    long before = writes(war);
    assertTrue(run("admin", "schedule", "1", "opencvote"));
    assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
    assertTrue(run("admin", "schedule", "1", "castvote", "21"));
    assertEquals(2, BattleQuorumService.countDistinctVoters(war));
    assertTrue(war.getBattleVotes().values().stream().allMatch(hours -> hours.equals(Set.of(21))));
    assertTrue(run("admin", "schedule", "1", "castvote", "22", "attacker"));
    assertTrue(war.getBattleVotes().values().stream().anyMatch(hours -> hours.contains(22)));
    assertTrue(run("admin", "schedule", "1", "forcequorum"));
    assertTrue(war.isForceQuorumNextClose());
    LocalDate day = war.getBattleDay();
    assertTrue(run("admin", "schedule", "1", "skipday"));
    assertEquals(day.plusDays(1), war.getBattleDay());
    assertEquals(before + 5, writes(war));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "closevote",
        "battlecreate",
        "battledelete",
        "battlestart",
        "winbattle attacker",
        "choice push",
        "battlechoice hold",
        "defenderchoice attack",
        "pushchoice push",
        "holdchoice hold"
      })
  void scheduleServicesRejectTransitionsForEndedWarsWithoutPersisting(String subcommand) {
    War war = war(1);
    war.end(WarEndReason.ADMIN_END);
    long before = writes(war);
    List<String> args = new ArrayList<>(List.of("admin", "schedule", "1"));
    args.addAll(Arrays.asList(subcommand.split(" ")));
    assertTrue(run(args.toArray(String[]::new)));
    verify(alice).sendMessage("§cWar is not active.");
    assertEquals(before, writes(war));
    assertTrue(BattleManager.get().isEmpty());
  }

  static Stream<Arguments> completionCases() {
    return Stream.of(
        Arguments.of("", List.of("list", "admin")),
        Arguments.of(
            "admin ",
            List.of(
                "end",
                "win",
                "status",
                "path",
                "time",
                "schedule",
                "devmode",
                "raid",
                "reparations",
                "factions")),
        Arguments.of("admin reparations i", List.of("iron")),
        Arguments.of("admin reparations iron r", List.of("river")),
        Arguments.of("admin factions r", List.of("river")),
        Arguments.of("admin devmode o", List.of("on", "off")),
        Arguments.of("admin raid ", List.of("resetquota")),
        Arguments.of("admin raid resetquota 1", List.of("1")),
        Arguments.of("admin raid resetquota 1 ", List.of("aggressor", "defender", "both")),
        Arguments.of("admin time ", List.of("status", "reset", "add", "skip-to-battle-day")),
        Arguments.of("admin end 1", List.of("1")),
        Arguments.of("admin win 1", List.of("1")),
        Arguments.of("admin status 1", List.of("1")),
        Arguments.of("admin path 1", List.of("1")),
        Arguments.of("admin schedule 1", List.of("1")),
        Arguments.of("admin win 1 ", List.of("attacker", "defender")),
        Arguments.of("admin time skip-to-battle-day 1", List.of("1")),
        Arguments.of("admin time add 1", List.of("1h", "1d", "1h31m")),
        Arguments.of(
            "admin schedule 1 ",
            List.of(
                "opencvote",
                "closevote",
                "skipday",
                "castvote",
                "forcequorum",
                "setscheduled",
                "battlecreate",
                "battledelete",
                "battlestart",
                "winbattle",
                "choice",
                "battlechoice",
                "defenderchoice")),
        Arguments.of("admin schedule 1 winbattle ", List.of("attacker", "defender")),
        Arguments.of("admin schedule 1 choice ", List.of("push", "hold", "attack", "accept")),
        Arguments.of("admin schedule 1 castvote 2", List.of("20", "21", "22", "23")),
        Arguments.of("admin schedule 1 castvote 21 ", List.of("attacker", "defender", "both")),
        Arguments.of("admin unknown arguments", List.of()),
        Arguments.of("list arguments", List.of()));
  }

  @ParameterizedTest
  @MethodSource("completionCases")
  void completionsExposeOnlyApplicableArgumentsAndActiveWarIds(
      String input, List<String> expected) {
    war(1);
    war(19).end(WarEndReason.ADMIN_END);
    war(2);
    assertEquals(expected, complete(input.split(" ", -1)));
  }

  @Test
  void completionsRespectCommandAndStaffPermissions() {
    when(command.getName()).thenReturn("other");
    assertTrue(complete("").isEmpty());
    when(command.getName()).thenReturn("war");
    when(alice.hasPermission("simplefactions.admin")).thenReturn(false);
    assertEquals(List.of("list"), complete(""));
    assertTrue(complete("admin", "").isEmpty());
  }
}
