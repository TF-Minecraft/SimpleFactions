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
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleAdminResult;
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleAdminService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.buildup.BuildupService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleTickService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsService;
import net.tfminecraft.simplefactions.war.truce.TruceService;
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
import net.tfminecraft.simplefactions.managers.InventoryManager;

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

  /** Runs a command as already confirmed, so tests of its effect skip the confirm prompt. */
  private boolean run(String... args) {
    boolean risky = WarAdminRisks.describe(args) != null
        && !net.tfminecraft.simplefactions.utils.StaffConfirm.isConfirmed(args);
    String[] confirmed = risky ? java.util.Arrays.copyOf(args, args.length + 1) : args;
    if (risky) confirmed[args.length] = "confirm";
    return runRaw(confirmed);
  }

  private boolean runRaw(String... args) {
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
  void theArmyToolsAnswerAndComplete() {
    War war = war(78);
    org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
    yaml.set("item.material", "PAPER");
    net.tfminecraft.simplefactions.army.Regiment guard = new net.tfminecraft.simplefactions.army.Regiment("guard", yaml);
    guard.setCurrentSlots(4);
    attacker.getMilitary().getRegiments().add(guard);
    org.bukkit.configuration.file.YamlConfiguration levyYaml = new org.bukkit.configuration.file.YamlConfiguration();
    levyYaml.set("item.material", "PAPER");
    levyYaml.set("levy", true);
    attacker.getMilitary().getRegiments().add(new net.tfminecraft.simplefactions.army.Regiment("levy", levyYaml));
    war.getLedger().addBattle("b", "fought", "t", 10,
        new net.tfminecraft.simplefactions.war.wounds.WarLedger.SideRecord(10,
            net.tfminecraft.simplefactions.war.wounds.TestForces.regiments("iron", 4)),
        new net.tfminecraft.simplefactions.war.wounds.WarLedger.SideRecord(0, List.of()));
    net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany company =
        mock(net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany.class);
    net.tfminecraft.simplefactions.guild.Guild guild = attacker.getOrCreateMainGuild();
    when(company.getGuild()).thenReturn(guild);
    when(company.getName()).thenReturn("Blades");
    when(company.getWounds()).thenReturn(new net.tfminecraft.simplefactions.war.wounds.CompanyWounds());
    guild.setCompany(company);

    assertTrue(run("admin", "losses", "78"));
    verify(alice).sendMessage("§6War 78: 1 recorded battles");
    assertTrue(run("admin", "army", "iron"));
    verify(alice).sendMessage(contains("§eguard§7: 4 slots"));
    assertTrue(run("admin", "wounds", "78", "iron", "guard", "add", "5"));
    verify(alice).sendMessage("§aWar 78: added 5 wounded lives on iron guard.");

    assertTrue(complete("admin", "lo").contains("losses"));
    assertTrue(complete("admin", "ar").contains("army"));
    assertTrue(complete("admin", "wou").contains("wounds"));
    assertEquals(List.of("iron"), complete("admin", "army", "ir"));
    assertEquals(List.of(), complete("admin", "army", "iron", ""));
    assertTrue(complete("admin", "losses", "").contains("78"));
    assertFalse(complete("admin", "losses", "").contains("company"));
    assertTrue(complete("admin", "wounds", "").containsAll(List.of("78", "company")));
    assertEquals(List.of("1"), complete("admin", "losses", "78", ""));
    assertEquals(List.of(), complete("admin", "losses", "999", ""));
    assertEquals(List.of("attacker", "defender"), complete("admin", "losses", "78", "1", ""));
    assertEquals(List.of("set", "add", "remove"), complete("admin", "losses", "78", "1", "attacker", ""));
    assertEquals(List.of(), complete("admin", "losses", "78", "1", "attacker", "set", ""));
    assertEquals(List.of("iron"), complete("admin", "wounds", "78", "ir"));
    assertTrue(complete("admin", "wounds", "78", "iron", "").contains("guard"));
    assertFalse(complete("admin", "wounds", "78", "iron", "").contains("levy"));
    assertEquals(List.of(), complete("admin", "wounds", "78", "ghost", ""));
    assertEquals(List.of("add", "remove"), complete("admin", "wounds", "78", "iron", "guard", ""));
    assertEquals(List.of(guild.getId()), complete("admin", "wounds", "company", guild.getId().substring(0, 1)).stream()
        .filter(guild.getId()::equals).toList());
    assertEquals(List.of("set"), complete("admin", "wounds", "company", guild.getId(), ""));
    assertEquals(List.of(), complete("admin", "wounds", "company", guild.getId(), "set", ""));
    guild.setCompany(null);
  }

  @Test
  void irreversibleAdminCommandsWaitForConfirm() {
    War war = war(31);
    assertTrue(runRaw("admin", "end", "31"));
    verify(alice).sendMessage("§6This cannot be undone: §eends the war at once, with no winner.");
    assertTrue(war.isActive());
    assertTrue(runRaw("admin", "time", "add", "1h"));
    assertEquals(Duration.ZERO, CampaignClock.getOffset());
  }

  @Test
  void timeRemoveMovesTheClockBack() {
    assertTrue(run("admin", "time", "remove"));
    verify(alice).sendMessage("§cUsage: /war admin time remove <duration...>");
    assertTrue(run("admin", "time", "add", "2h", "confirm"));
    assertTrue(run("admin", "time", "remove", "30m"));
    assertEquals(Duration.ofMinutes(90), CampaignClock.getOffset());
    assertTrue(complete("admin", "time", "re").containsAll(List.of("reset", "remove")));
    assertTrue(complete("admin", "time", "remove", "").contains("1h"));
  }

  @Test
  void scheduleBattleStaffToolsReachTheWarsBattle() {
    War war = war(32);
    assertTrue(run("admin", "schedule", "32", "battlehold", "on"));
    verify(alice).sendMessage("§cNo campaign battle for this war.");
    Battle battle = new Battle("campaign_w32_p20");
    battle.setWarId(32);
    BattleManager.addBattle(battle);

    assertTrue(run("admin", "schedule", "32", "battlerestart", "confirm"));
    assertTrue(run("admin", "schedule", "32", "battleabort", "confirm"));
    assertTrue(run("admin", "schedule", "32", "battleend", "confirm"));
    assertTrue(run("admin", "schedule", "32", "battleend", "nowinner", "confirm"));
    assertTrue(run("admin", "schedule", "32", "battleend", "attacker", "confirm"));
    verify(alice, org.mockito.Mockito.times(5)).sendMessage("§cThat battle is not running.");
    assertTrue(run("admin", "schedule", "32", "battlehold", "maybe"));
    verify(alice).sendMessage("§cUsage: /war admin schedule <id> battlehold on|off");
    assertTrue(run("admin", "schedule", "32", "battlehold", "on"));
    assertTrue(battle.isStaffHold());
    assertTrue(run("admin", "schedule", "32", "battlehold", "off"));
    assertFalse(battle.isStaffHold());
    assertTrue(war.isActive());

    assertTrue(complete("admin", "schedule", "32", "battle").containsAll(
        List.of("battlerestart", "battleabort", "battleend", "battlehold")));
    assertEquals(List.of("nowinner", "attacker", "defender"), complete("admin", "schedule", "32", "battleend", ""));
    assertEquals(List.of("on", "off"), complete("admin", "schedule", "32", "battlehold", ""));
  }

  @Test
  void forceInstallationCommitsAWarFactionsInstallationPastThePickRules() {
    War war = war(41);
    net.tfminecraft.simplefactions.installation.Installation fort =
        new net.tfminecraft.simplefactions.installation.Installation(
            "fort-1", "Iron Fort", net.tfminecraft.simplefactions.installation.InstallationKind.FORT, 10, 0, 0, 0L);
    attacker.getInstallationHandler().acceptTransferred(fort);
    Faction neutral = fixture.saved("plain", "Carl");
    Battle battle = new Battle("campaign_w41");
    battle.setWarId(41);
    BattleManager.addBattle(battle);
    BattleManager.addBattle(new Battle("manual"));
    Battle raid = new Battle("raid_w41");
    raid.setWarId(41);
    raid.setCampaignRaid(true);
    raid.setDisplayName("Harbour Raid");
    BattleManager.addBattle(raid);
    assertTrue(run("admin", "forceinstallation", "raid_w41", "iron", "fort-1"));
    verify(alice).sendMessage("§cHarbour Raid is a raid; only its source and target installations take part.");

    assertTrue(run("admin", "forceinstallation", "campaign_w41"));
    verify(alice).sendMessage("§cUsage: /war admin forceinstallation <battle> <faction> <installation>");
    assertTrue(run("admin", "forceinstallation", "nope", "iron", "fort-1"));
    verify(alice).sendMessage("§cThere is no battle with the id nope");
    assertTrue(run("admin", "forceinstallation", "manual", "iron", "fort-1"));
    verify(alice).sendMessage("§cmanual is not a battle of an active war.");
    assertTrue(run("admin", "forceinstallation", "campaign_w41", "ghost", "fort-1"));
    verify(alice).sendMessage("§cUnknown faction: ghost");
    assertTrue(run("admin", "forceinstallation", "campaign_w41", "plain", "fort-1"));
    verify(alice).sendMessage("§c" + neutral.getName() + " is not on a side of war #41.");
    assertTrue(run("admin", "forceinstallation", "campaign_w41", "iron", "nope"));
    verify(alice).sendMessage("§c" + attacker.getName() + " has no installation with the id nope.");

    assertTrue(runRaw("admin", "forceinstallation", "campaign_w41", "iron", "fort-1"));
    verify(alice).sendMessage("§6This cannot be undone: §ecommits Iron Fort to this battle: vehicles berthed there"
        + " can be used, it can't berth or unberth, and it's open to attack until the battle day ends.");
    assertTrue(war.getForcedBattleInstallationPicks().isEmpty());
    assertTrue(runRaw("admin", "forceinstallation", "campaign_w41", "iron", "fort-1", "confirm"));
    verify(alice).sendMessage("§aCommitted §eIron Fort §a(" + attacker.getName() + ") to campaign_w41 until the battle day ends.");
    assertEquals(java.util.Set.of("fort-1"),
        net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService.getPicks(war, "iron"));
    assertTrue(writes(war) > 0);

    assertTrue(complete("admin", "force").contains("forceinstallation"));
    assertEquals(List.of("campaign_w41"), complete("admin", "forceinstallation", ""));
    assertEquals(List.of("iron", "river"), complete("admin", "forceinstallation", "campaign_w41", ""));
    assertEquals(List.of("fort-1"), complete("admin", "forceinstallation", "campaign_w41", "iron", ""));
    assertEquals(List.of(), complete("admin", "forceinstallation", "campaign_w41", "plain", ""));
    assertEquals(List.of(), complete("admin", "forceinstallation", "campaign_w41", "ghost", ""));
    assertEquals(List.of(), complete("admin", "forceinstallation", "campaign_w41", "iron", "fort-1", ""));
    assertEquals(List.of(), complete("admin", "forceinstallation", "manual", ""));
    assertEquals(List.of(), complete("admin", "forceinstallation", "nope", ""));
    assertEquals(List.of(), complete("admin", "forceinstallation", "raid_w41", ""));
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
    verify(alice).sendMessage("§eUsage: §a/war list§e, §a/war vote <war id>§e, §a/war calls§e, §a/war call accept|decline <war id>");
    verify(alice).sendMessage(contains("Staff:"));
    when(alice.hasPermission("simplefactions.admin")).thenReturn(false);
    assertTrue(run("admin", "devmode", "on"));
    assertFalse(WarDevMode.isEnabled());
    verify(alice).sendMessage(contains("You do not have access"));
    assertTrue(run("list"));
    assertNotEquals("Crafting", alice.getOpenInventory().getTitle());
    assertNotNull(alice.getOpenInventory().getTopInventory().getHolder());
  }

  @Test
  void voteOpensThatWarsCampaignView() {
    War war = war(41);
    try (MockedConstruction<InventoryManager> views = mockConstruction(InventoryManager.class)) {
      assertTrue(run("vote", "41"));
      InventoryManager opened = views.constructed().get(views.constructed().size() - 1);
      verify(opened).openCampaignView(alice, war);
    }
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
        Arguments.of("admin truce", "Usage: /war admin truce"),
        Arguments.of("admin truce wrong", "Usage: /war admin truce"),
        Arguments.of("admin truce list iron extra", "Usage: /war admin truce"),
        Arguments.of("admin truce clear iron", "Usage: /war admin truce"),
        Arguments.of("admin truce clear iron river extra", "Usage: /war admin truce"),
        Arguments.of("admin truce clear iron river", "§cNo truce between"),
        Arguments.of("admin truce cooldowns extra", "Usage: /war admin truce"),
        Arguments.of("admin truce clearcooldown", "Usage: /war admin truce"),
        Arguments.of("admin truce clearcooldown iron", "§ciron §chas no civil war cooldown."),
        Arguments.of("admin schedule", "Usage:"),
        Arguments.of("admin schedule bad forcequorum", "War id must be a number"),
        Arguments.of("admin schedule 999 forcequorum", "No war by that id"),
        Arguments.of("admin schedule 1 castvote", "Usage:"),
        Arguments.of("admin schedule 1 castvote bad", "Hour must be a number"),
        Arguments.of("admin schedule 1 castvote 2147483648", "Hour must be a number"),
        Arguments.of("admin schedule 1 castvote 15", "Hour must be within the battle window"),
        Arguments.of(
            "admin schedule 1 castvote 21 wrong", "Side must be attacker, defender, or both"),
        Arguments.of("admin schedule 1 setday", "Usage: /war admin schedule <id> setday"),
        Arguments.of("admin schedule 1 sethour", "Usage: /war admin schedule <id> sethour"),
        Arguments.of("admin schedule 1 sethour bad", "Hour must be a number"),
        Arguments.of("admin declare iron", "Usage: /war admin declare"),
        Arguments.of("admin declare iron nowhere", "Unknown faction: nowhere"),
        Arguments.of("admin declare nowhere river", "Unknown faction: nowhere"),
        Arguments.of("admin declare iron river conquest", "Unknown war goal: conquest"),
        Arguments.of("admin declare iron river overthrow", "wars start from movements"),
        Arguments.of(
            "admin declare iron river transfer_subject nowhere", "Unknown subject faction: nowhere"),
        Arguments.of("admin schedule 1 setscheduled", "Usage:"),
        Arguments.of("admin schedule 1 setscheduled invalid", "Could not parse instant"),
        Arguments.of("admin schedule 1 winbattle", "Usage:"),
        Arguments.of("admin schedule 1 winbattle neither", "Winner must be attacker or defender"),
        Arguments.of("admin schedule 1 choice", "Usage:"),
        Arguments.of("admin schedule 1 choice invalid", "Post-battle choice not available"),
        Arguments.of("admin schedule 1 wrong", "Unknown subcommand"),
        Arguments.of("calls extra", "Unknown subcommand"),
        Arguments.of("vote", "Unknown subcommand"),
        Arguments.of("vote bad", "No active war with id"),
        Arguments.of("vote 999", "No active war with id"),
        Arguments.of("call", "Usage: §a/war call accept|decline"),
        Arguments.of("call accept", "Usage: §a/war call accept|decline"),
        Arguments.of("call maybe 1", "Usage: §a/war call accept|decline"),
        Arguments.of("call accept bad", "No active war with id"),
        Arguments.of("call decline 999", "No active war with id"),
        Arguments.of("call accept 19", "No active war with id"),
        Arguments.of("admin buildup", "Usage: /war admin buildup"),
        Arguments.of("admin buildup 1", "Usage: /war admin buildup"),
        Arguments.of("admin buildup bad status", "No active war by that id"),
        Arguments.of("admin buildup 999 status", "No active war by that id"),
        Arguments.of("admin buildup 1 wrong", "Usage: /war admin buildup"),
        Arguments.of("admin buildup 1 closevote", "is not voting on its first battle day"),
        Arguments.of("admin buildup 1 setday", "Usage: /war admin buildup <warId> setday"),
        Arguments.of("admin buildup 1 setday never", "Usage: /war admin buildup <warId> setday"),
        Arguments.of("admin buildup 1 setday 2026-11-20", "is not in a buildup"));
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "admin declare iron hill war extra",
        "admin declare iron hill subjugate vassal extra",
        "admin declare iron hill change_government a b extra"
      })
  void adminDeclareRejectsArgumentsTheGoalDoesNotTake(String input) {
    fixture.saved("hill", "Carol");
    int before = WarManager.get().size();
    assertTrue(run(input.split(" ")));
    verify(alice).sendMessage(startsWith("§cUsage: /war admin declare"));
    assertEquals(before, WarManager.get().size());
  }

  static Stream<Arguments> adminDeclareGoalArguments() {
    return Stream.of(
        Arguments.of(
            "admin declare iron river", WarGoalType.WAR, Arrays.asList(null, null, null, null, null, null)),
        Arguments.of(
            "admin declare iron river de_jure_annex t1",
            WarGoalType.DE_JURE_ANNEX,
            Arrays.asList("t1", null, null, null, null, null)),
        Arguments.of(
            "admin declare iron river transfer_subject river",
            WarGoalType.TRANSFER_SUBJECT,
            Arrays.asList(null, "river", null, null, null, null)),
        Arguments.of(
            "admin declare iron river transfer_subject",
            WarGoalType.TRANSFER_SUBJECT,
            Arrays.asList(null, null, null, null, null, null)),
        Arguments.of(
            "admin declare iron river subjugate vassal",
            WarGoalType.SUBJUGATE,
            Arrays.asList(null, null, "vassal", null, null, null)),
        Arguments.of(
            "admin declare iron river change_government gov lead",
            WarGoalType.CHANGE_GOVERNMENT,
            Arrays.asList(null, null, null, "gov", "lead", null)),
        Arguments.of(
            "admin declare iron river pillage s1",
            WarGoalType.PILLAGE,
            Arrays.asList(null, null, null, null, null, "s1")));
  }

  @ParameterizedTest
  @MethodSource("adminDeclareGoalArguments")
  void adminDeclarePassesEachGoalItsOwnArguments(
      String input, WarGoalType goal, List<String> goalArgs) {
    War declared = war(7);
    try (MockedStatic<WarManager> manager = mockStatic(WarManager.class)) {
      manager
          .when(
              () ->
                  WarManager.declareWar(
                      any(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(declared);
      assertTrue(run(input.split(" ")));
      manager.verify(
          () ->
              WarManager.declareWar(
                  attacker,
                  defender,
                  goal,
                  goalArgs.get(0),
                  goalArgs.get(1),
                  goalArgs.get(2),
                  goalArgs.get(3),
                  goalArgs.get(4),
                  goalArgs.get(5)));
    }
    verify(alice).sendMessage(contains("§aDeclared war §e#7"));
    verify(alice).sendMessage(contains("Battle day: §e2026-10-10"));
  }

  @Test
  void adminDeclareReportsWhyTheDeclarationFailed() {
    try (MockedStatic<WarManager> manager = mockStatic(WarManager.class)) {
      manager
          .when(
              () ->
                  WarManager.declareWar(
                      any(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(null);
      manager.when(WarManager::getLastDeclareError).thenReturn("§cAlready at war.", (String) null);
      assertTrue(run("admin", "declare", "iron", "river"));
      verify(alice).sendMessage("§cAlready at war.");
      assertTrue(run("admin", "declare", "iron", "river"));
      verify(alice).sendMessage("§cCould not declare war.");
    }
  }

  @Test
  void adminSetDayAndSetHourPassTheirArgumentsToTheScheduleService() {
    War war = war(1);
    try (MockedStatic<WarScheduleAdminService> admin = mockStatic(WarScheduleAdminService.class)) {
      admin
          .when(() -> WarScheduleAdminService.setDay(war, "2026-12-01"))
          .thenReturn(WarScheduleAdminResult.ok("day moved"));
      admin
          .when(() -> WarScheduleAdminService.setHour(war, 1))
          .thenReturn(WarScheduleAdminResult.ok("hour moved"));
      assertTrue(run("admin", "schedule", "1", "setday", "2026-12-01"));
      assertTrue(run("admin", "schedule", "1", "sethour", "1"));
      admin.verify(() -> WarScheduleAdminService.setDay(war, "2026-12-01"));
      admin.verify(() -> WarScheduleAdminService.setHour(war, 1));
    }
    verify(alice).sendMessage(contains("day moved"));
    verify(alice).sendMessage(contains("hour moved"));
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

  @ParameterizedTest
  @ValueSource(strings = {"end", "attacker", "defender", "white_peace"})
  void onlyWarsThatWereFoughtOutLeaveATruce(String ending) {
    cache("warTruceDays", 10);
    War war = war(1);
    switch (ending) {
      case "end" -> assertTrue(run("admin", "end", "1"));
      case "white_peace" -> WarManager.endWar(war, WarEndReason.WHITE_PEACE);
      default -> assertTrue(run("admin", "win", "1", ending));
    }
    assertEquals(!ending.equals("end"), TruceService.hasTruce(attacker, defender));
    assertEquals(!ending.equals("end"), TruceService.hasTruce(defender, attacker));
  }

  @Test
  void staffReachTheWarHistoryCommands() {
    assertTrue(run("admin", "history"));
    verify(alice).sendMessage(net.tfminecraft.simplefactions.war.history.WarHistoryAdmin.USAGE);
    assertTrue(run("admin", "history", "status"));
    verify(alice).sendMessage("§7Not started.");
  }

  @Test
  void staffListAndClearTrucesByFaction() {
    Faction other = fixture.saved("hill", "Carol");
    java.time.Instant until = java.time.Instant.now().plus(Duration.ofDays(3)).plusSeconds(60);
    TruceService.record("iron", "river", until, 4);
    TruceService.record("hill", "river", until.plus(Duration.ofDays(1)), 5);
    assertTrue(run("admin", "truce", "list"));
    verify(alice).sendMessage("§7Truces (2):");
    verify(alice).sendMessage("§firon §7- §friver §7(3d 0h left, war 4)");
    verify(alice).sendMessage("§fhill §7- §friver §7(4d 0h left, war 5)");
    assertTrue(run("admin", "truce", "list", "hill"));
    verify(alice).sendMessage("§7Truces (1):");
    assertTrue(run("admin", "truce", "clear", "RIVER", "iron"));
    verify(alice).sendMessage("§aCleared the truce between §friver §aand §firon§a.");
    assertFalse(TruceService.hasTruce(attacker, defender));
    assertTrue(TruceService.hasTruce(other, defender));
    assertTrue(run("admin", "truce", "list", "iron"));
    verify(alice).sendMessage("§7No active truces for §firon§7.");
    TruceService.clear("hill", "river");
    assertTrue(run("admin", "truce", "list"));
    verify(alice).sendMessage("§7No active truces.");
  }

  @Test
  void staffListAndClearCivilWarCooldowns() {
    assertTrue(run("admin", "truce", "cooldowns"));
    verify(alice).sendMessage("§7No active civil war cooldowns.");
    java.time.Instant until = java.time.Instant.now().plus(Duration.ofDays(2)).plusSeconds(60);
    TruceService.recordCivilWarCooldown("river", until, 6);
    TruceService.recordCivilWarCooldown("gone", until.plus(Duration.ofDays(1)), 7);
    assertTrue(run("admin", "truce", "cooldowns"));
    verify(alice).sendMessage("§7Civil war cooldowns (2):");
    verify(alice).sendMessage("§friver §7(2d 0h left, war 6)");
    verify(alice).sendMessage("§fgone §7(3d 0h left, war 7)");
    assertTrue(run("admin", "truce", "clearcooldown", "RIVER"));
    verify(alice).sendMessage("§aCleared the civil war cooldown of §friver§a.");
    assertNull(TruceService.civilWarCooldownUntil(defender));
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
    for (CampaignSideRole coalition : CampaignSideRole.values())
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
        Arguments.of("", List.of("list", "vote", "calls", "call", "admin")),
        Arguments.of("ca", List.of("calls", "call")),
        Arguments.of("vote ", List.of("1", "2")),
        Arguments.of("vote 1 ", List.of()),
        Arguments.of("call ", List.of("accept", "decline")),
        Arguments.of("call d", List.of("decline")),
        Arguments.of("call accept ", List.of()),
        Arguments.of("call accept 1 ", List.of()),
        Arguments.of("admin buildup ", List.of("1", "2")),
        Arguments.of("admin buildup 1 ", List.of("status", "end", "closevote", "setday")),
        Arguments.of("admin buildup 1 c", List.of("closevote")),
        Arguments.of(
            "admin ",
            List.of(
                "declare",
                "end",
                "win",
                "status",
                "path",
                "time",
                "schedule",
                "buildup",
                "devmode",
                "raid",
                "reparations",
                "truce",
                "history",
                "factions",
                "forceinstallation",
                "losses",
                "army",
                "wounds")),
        Arguments.of("admin history ", List.of("status", "upload-battles")),
        Arguments.of("admin history u", List.of("upload-battles")),
        Arguments.of("admin history status ", List.of()),
        Arguments.of("admin truce ", List.of("list", "clear", "cooldowns", "clearcooldown")),
        Arguments.of("admin truce clearc", List.of("clearcooldown")),
        Arguments.of("admin truce clearcooldown r", List.of("river")),
        Arguments.of("admin truce cooldowns ", List.of()),
        Arguments.of("admin truce list r", List.of("river")),
        Arguments.of("admin truce clear i", List.of("iron")),
        Arguments.of("admin truce clear iron r", List.of("river")),
        Arguments.of("admin truce list iron ", List.of()),
        Arguments.of("admin reparations i", List.of("iron")),
        Arguments.of("admin reparations iron r", List.of("river")),
        Arguments.of("admin factions r", List.of("river")),
        Arguments.of("admin devmode o", List.of("on", "off")),
        Arguments.of("admin raid ", List.of("resetquota")),
        Arguments.of("admin raid resetquota 1", List.of("1")),
        Arguments.of("admin raid resetquota 1 ", List.of("aggressor", "defender", "both")),
        Arguments.of("admin time ", List.of("status", "reset", "add", "remove", "skip-to-battle-day")),
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
                "setday",
                "sethour",
                "castvote",
                "forcequorum",
                "setscheduled",
                "battlecreate",
                "battledelete",
                "battlestart",
                "battlerestart",
                "battleabort",
                "battleend",
                "battlehold",
                "winbattle",
                "choice",
                "battlechoice",
                "defenderchoice")),
        Arguments.of("admin schedule 1 winbattle ", List.of("attacker", "defender")),
        Arguments.of("admin schedule 1 choice ", List.of("push", "hold", "attack", "accept")),
        Arguments.of("admin schedule 1 castvote 2", List.of("20", "21", "22", "23")),
        Arguments.of("admin schedule 1 castvote 21 ", List.of("attacker", "defender", "both")),
        Arguments.of("admin schedule 1 sethour 2", List.of("20", "21", "22", "23")),
        Arguments.of("admin declare r", List.of("river")),
        Arguments.of("admin declare river i", List.of("iron")),
        Arguments.of("admin declare river iron su", List.of("subjugate")),
        Arguments.of("admin declare river iron transfer_subject r", List.of("river")),
        Arguments.of("admin declare river iron war ", List.of()),
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
  void setDayCompletesTodayAndTheNextTwoBattleDays() {
    war(1);
    java.time.Instant noon = java.time.Instant.parse("2026-11-06T11:00:00Z");
    try (MockedStatic<CampaignClock> clock = mockStatic(CampaignClock.class, CALLS_REAL_METHODS)) {
      clock.when(CampaignClock::now).thenReturn(noon);
      assertEquals(
          List.of("2026-11-06", "2026-11-07", "2026-11-08"),
          complete("admin", "schedule", "1", "setday", "2026-11"));
      assertEquals(List.of("2026-11-07"), complete("admin", "schedule", "1", "setday", "2026-11-07"));
    }
  }

  @Test
  void completionsRespectCommandAndStaffPermissions() {
    when(command.getName()).thenReturn("other");
    assertTrue(complete("").isEmpty());
    when(command.getName()).thenReturn("war");
    when(alice.hasPermission("simplefactions.admin")).thenReturn(false);
    assertEquals(List.of("list", "vote", "calls", "call"), complete(""));
    assertTrue(complete("admin", "").isEmpty());
  }

  private War inBuildup(int id) {
    cache("warBuildupEnabled", true);
    War war = war(id);
    war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
    assertTrue(BuildupService.start(war, CampaignClock.now()));
    return war;
  }

  @Test
  void adminDeclareOfABuildupWarSaysWhenItsDayVoteCloses() {
    War declared = inBuildup(8);
    try (MockedStatic<WarManager> manager = mockStatic(WarManager.class)) {
      manager
          .when(
              () ->
                  WarManager.declareWar(
                      any(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(declared);
      assertTrue(run("admin", "declare", "iron", "river"));
    }
    verify(alice).sendMessage(contains("In its buildup; the day vote closes §e"));
  }

  @Test
  void buildupStaffToolsReportCloseSetAndEndTheBuildup() {
    War war = inBuildup(51);
    assertTrue(run("admin", "buildup", "51", "status"));
    verify(alice).sendMessage("§eWar 51 buildup: §cfrozen until the first battle");
    verify(alice).sendMessage(org.mockito.AdditionalMatchers.and(startsWith("§7Day vote closes "), endsWith(", 0 voters.")));

    assertTrue(run("admin", "schedule", "51", "setday", "2026-11-20"));
    verify(alice).sendMessage(contains("is still voting on its first battle day"));
    assertEquals(BattleSchedulePhase.BUILDUP, war.getBattleSchedulePhase());

    long before = writes(war);
    assertTrue(run("admin", "buildup", "51", "closevote"));
    verify(alice).sendMessage(startsWith("§aClosed the day vote §7(no quorum)§a. First battle day: "));
    assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
    assertNotNull(war.getBattleDay());
    assertTrue(war.isBuildupFrozen());
    assertEquals(before + 1, writes(war));

    assertTrue(run("admin", "buildup", "51", "status"));
    verify(alice).sendMessage(startsWith("§7First battle day: "));

    LocalDate later =
        net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService.scheduleDate(
                CampaignClock.now())
            .plusDays(20);
    assertTrue(run("admin", "buildup", "51", "setday", later.toString()));
    verify(alice).sendMessage("§aFirst battle day of war 51: " + later);
    assertEquals(later, war.getBattleDay());

    assertTrue(run("admin", "buildup", "51", "end"));
    verify(alice).sendMessage("§aEnded the buildup of war 51. Its factions can build again.");
    assertFalse(war.isBuildupFrozen());
    assertEquals(later, war.getBattleDay());
    assertTrue(run("admin", "buildup", "51", "status"));
    verify(alice).sendMessage("§eWar 51 buildup: §anot frozen by a buildup");
  }

  @Test
  void endingABuildupStillVotingOnItsDayGivesItTheNextVotableBattleDay() {
    War war = inBuildup(52);
    java.time.Instant now = CampaignClock.now();

    assertTrue(run("admin", "buildup", "52", "end"));

    assertFalse(war.isBuildupFrozen());
    assertNull(war.getDayVoteFirstDay());
    assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
    assertEquals(
        BattleScheduleService.firstVotableBattleDay(
            net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService.scheduleDate(now),
            now),
        war.getBattleDay());
  }

  @Test
  void buildupActionsThatChangeTheWarAskForConfirmFirst() {
    War war = inBuildup(53);
    assertTrue(runRaw("admin", "buildup", "53", "end"));
    verify(alice).sendMessage(contains("lifts the war's buildup freeze now"));
    assertTrue(runRaw("admin", "buildup", "53", "closevote"));
    verify(alice).sendMessage(contains("closes the first battle day vote now"));
    assertTrue(runRaw("admin", "buildup", "53", "setday", "2026-12-01"));
    verify(alice).sendMessage(contains("sets the first battle day and closes the day vote"));
    assertTrue(war.isBuildupFrozen());
    assertEquals(BattleSchedulePhase.BUILDUP, war.getBattleSchedulePhase());
    assertNull(WarAdminRisks.describe(new String[] {"admin", "buildup", "53", "status"}));
    assertNull(WarAdminRisks.describe(new String[] {"admin", "buildup", "999", "end"}));
    assertNull(WarAdminRisks.describe(new String[] {"admin", "buildup", "53"}));
  }

  @Test
  void callsAreListedCompletedAndAnsweredByWarId() {
    War war = war(54);
    War other = war(55);
    Faction carl = fixture.saved("plain", "Carl");
    War called = WarManager.addWar(new War(56, defender, carl));
    called.getCallsToArms().put("iron", new CallToArms("iron", "river", null, null));
    other.end(WarEndReason.ADMIN_END);
    other.getCallsToArms().put("iron", new CallToArms("iron", "river", null, null));

    assertTrue(run("calls"));
    verify(alice).sendMessage("§6Open calls to arms:");
    assertEquals(List.of("56"), complete("call", "accept", ""));
    assertEquals(List.of(), complete("call", "accept", "4"));

    assertTrue(run("call", "accept", "55"));
    verify(alice).sendMessage("§cNo active war with id §f55");
    assertTrue(run("call", "accept", "56"));
    verify(alice).sendMessage("§cCould not join the war.");
    assertFalse(called.isParticipating(attacker));

    called.getCallsToArms().put("iron", new CallToArms("iron", "river", null, null));
    assertTrue(run("call", "decline", "56"));
    verify(alice).sendMessage("§7You declined the call to arms.");
    assertTrue(called.getCallsToArms().isEmpty());
    assertTrue(run("calls"));
    verify(alice).sendMessage("§7Your faction has no open calls to arms.");
    assertTrue(war.isActive());
  }
}
