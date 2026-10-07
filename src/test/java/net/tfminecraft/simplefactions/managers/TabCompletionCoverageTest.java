package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.espionage.CharacterNames;
import net.tfminecraft.simplefactions.espionage.EspionageCommands;
import net.tfminecraft.simplefactions.espionage.EspionageConfig;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationConstruction;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.loaders.LawLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryMarket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.request.MercenaryInviteRequest;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.tiers.admin.TitleAdminCommand;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.utils.TabCompletion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class TabCompletionCoverageTest {
  private GuiTestFixture gui;
  private MockedStatic<FactionManager> factions;
  private TabCompletion completion;
  private Player player;
  private Faction realm;
  private Faction other;
  private Guild guild;
  private List<Faction> previousFactions;
  private boolean previousProvinces;

  @BeforeEach
  void setUp() {
    previousFactions = FactionManager.factions;
    previousProvinces = Cache.provincesEnabled;
    Cache.provincesEnabled = true;
    gui = new GuiTestFixture();
    player = gui.player("Leader");
    realm = realm("realm", "Realm");
    other = realm("other", "Other Realm");
    guild = mock(Guild.class);
    when(guild.getId()).thenReturn("merchants");
    when(guild.getFaction()).thenReturn(realm);
    when(guild.getLeader()).thenReturn("Leader");
    when(guild.getMembers()).thenReturn(new ArrayList<>(List.of("Leader", "Alice", "Bob")));
    FactionManager.factions = new ArrayList<>(List.of(realm, other));
    factions = mockStatic(FactionManager.class);
    factions.when(() -> FactionManager.getByString("realm")).thenReturn(realm);
    factions.when(() -> FactionManager.getByString("other")).thenReturn(other);
    completion = new TabCompletion();
  }

  @AfterEach
  void close() {
    try {
      if (factions != null) factions.close();
    } finally {
      try {
        if (gui != null) gui.close();
      } finally {
        FactionManager.factions = previousFactions;
        Cache.provincesEnabled = previousProvinces;
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"guild", "unrelated"})
  void factionForceJoinSuggestionsAreNotReturnedForOtherCommands(String command) {
    admin();
    assertTrue(complete(command, "forcejoin", "").isEmpty());
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(gui.player("Alice")));
    assertTrue(complete(command, "forcejoin", "realm", "").isEmpty());
  }

  @Test
  void removedWealthModifierCommandHasNoMisleadingArgumentSuggestions() {
    admin();
    assertTrue(complete("faction", "addwealthmodifier", "").isEmpty());
    assertTrue(complete("faction", "addwealthmodifier", "type", "").isEmpty());
  }

  @Test
  void rootSuggestionsFollowMembershipLeadershipAndProvinceAvailability() {
    List<String> outsider = complete("faction", "");
    assertTrue(outsider.containsAll(List.of("list", "create", "join", "vehicle")));
    assertFalse(outsider.contains("menu"));
    assertFalse(outsider.contains("claim"));
    assertFalse(outsider.contains("forceleader"));
    factions.when(() -> FactionManager.getByMember("Leader")).thenReturn(realm);
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
    List<String> leader = complete("faction");
    assertTrue(
        leader.containsAll(
            List.of(
                "menu",
                "positions",
                "spymaster",
                "espionage",
                "claim",
                "construct",
                "deconstruct",
                "upgrade",
                "unclaim",
                "setcapital",
                "setbank",
                "withdraw",
                "invite",
                "kick",
                "setleader",
                "setculture",
                "setreligion",
                "setrulingsystem",
                "setrulertitle",
                "setbanner",
                "setcolour")));
    Cache.provincesEnabled = false;
    List<String> noProvinces = complete("faction", "");
    assertFalse(noProvinces.contains("construct"));
    assertFalse(noProvinces.contains("setcapital"));
    assertTrue(noProvinces.contains("transfervehicle"));
    assertTrue(noProvinces.contains("setbank"));
  }

  @Test
  void administratorRootSuggestionsIncludeOnlyEnabledProvinceTools() {
    admin();
    List<String> enabled = complete("faction", "");
    assertTrue(
        enabled.containsAll(
            List.of(
                "addguildbank",
                "forceleader",
                "forcejoin",
                "forcewithdraw",
                "forceregiment",
                "addprestigemodifier",
                "getglobalwealth",
                "forceconstruct",
                "forceupgrade",
                "queueallnations",
                "fullregen",
                "reloadtitles",
                "destroytitle",
                "granttitle",
                "usurp",
                TitleAdminCommand.SUBCOMMAND,
                "tradegraph",
                "reloadconfigs",
                "transfersubject",
                "setrelation",
                "settreaty",
                "setpower",
                "setlaw",
                "setstance",
                "startelection",
                "endelection")));
    Cache.provincesEnabled = false;
    List<String> disabled = complete("faction", "");
    assertFalse(disabled.contains("forceconstruct"));
    assertFalse(disabled.contains("tradegraph"));
    assertFalse(disabled.contains(TitleAdminCommand.SUBCOMMAND));
    assertTrue(disabled.contains("setpower"));
  }

  @Test
  void guildRootSuggestionsAddLeaderAndStaffActionsWithoutProvinceOnlyActionsWhenDisabled() {
    List<String> outsider = complete("guild");
    assertTrue(
        outsider.containsAll(
            List.of("create", "join", "menu", "setbank", "deposit", "withdraw", "setcapital")));
    assertFalse(outsider.contains("invite"));
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    admin();
    Cache.provincesEnabled = false;
    List<String> leader = complete("guild", "");
    assertTrue(
        leader.containsAll(
            List.of(
                "invite",
                "kick",
                "setleader",
                "rename",
                "setbanner",
                "setcolour",
                "dummify",
                "dummyleader")));
    assertFalse(leader.contains("setcapital"));
  }

  @Test
  void espionageReloadShortcutRequiresItsPermissionAndMatchesCaseInsensitivePrefix() {
    when(player.hasPermission(EspionageConfig.reloadPermission())).thenReturn(true);
    assertEquals(List.of("reloadespionage"), complete("faction", "RELOADESP"));
    assertTrue(complete("faction", "").contains("reloadespionage"));
    CommandSender console = mock(CommandSender.class);
    when(console.hasPermission(EspionageConfig.reloadPermission())).thenReturn(true);
    assertEquals(List.of("reloadespionage"), complete(console, "faction", "reloadesp"));
    assertTrue(complete(console, "faction", "spymaster", "").isEmpty());
    try (MockedStatic<EspionageCommands> espionage = mockStatic(EspionageCommands.class)) {
      String[] args = {"spymaster", "Al"};
      espionage.when(() -> EspionageCommands.matches("spymaster")).thenReturn(true);
      espionage.when(() -> EspionageCommands.complete(player, args)).thenReturn(List.of("Alice"));
      assertEquals(List.of("Alice"), complete("faction", args));
      espionage.verify(() -> EspionageCommands.complete(player, args));
    }
  }

  @Test
  void addGuildBankCompletionWorksForStaffConsoleAndSkipsMissingGuilds() {
    CommandSender console = mock(CommandSender.class);
    when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
    Guild otherGuild = mock(Guild.class);
    when(otherGuild.getId()).thenReturn("miners");
    factions.when(FactionManager::getAllGuilds).thenReturn(Arrays.asList(guild, null, otherGuild));
    assertEquals(List.of("merchants"), complete(console, "faction", "addguildbank", "MER"));
    assertTrue(complete("faction", "addguildbank", "").isEmpty());
  }

  @Test
  void titleEditingCompletionRequiresStaffAndEnabledProvinces() {
    String[] args = {TitleAdminCommand.SUBCOMMAND, ""};
    assertTrue(complete("faction", args).isEmpty());
    admin();
    Cache.provincesEnabled = false;
    assertTrue(complete("faction", args).isEmpty());
    Cache.provincesEnabled = true;
    try (MockedStatic<TitleAdminCommand> titles = mockStatic(TitleAdminCommand.class)) {
      titles.when(() -> TitleAdminCommand.complete(args)).thenReturn(List.of("create", "edit"));
      assertEquals(List.of("create", "edit"), complete("faction", args));
    }
  }

  @Test
  void companyRootSuggestionsFollowCompanyOwnershipInvitesAndStaffPermission() {
    try (MockedStatic<RequestManager> requests = mockStatic(RequestManager.class)) {
      assertTrue(complete("company", "").isEmpty());
      factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
      assertEquals(List.of("found"), complete("company"));
      MercenaryCompany company = mock(MercenaryCompany.class);
      when(guild.getCompany()).thenReturn(company);
      when(guild.hasCompany()).thenReturn(true);
      when(company.getGuild()).thenReturn(guild);
      MercenaryInviteRequest invitation = new MercenaryInviteRequest(company);
      requests.when(() -> RequestManager.getRequest(player)).thenReturn(invitation);
      admin();
      assertEquals(
          List.of(
              "invite",
              "kick",
              "expand",
              "draft",
              "offer",
              "contracts",
              "accept",
              "decline",
              "admin"),
          complete("company", ""));
      assertEquals(List.of("accept", "admin"), complete("company", "A"));
      assertTrue(complete(mock(CommandSender.class), "company", "").isEmpty());
    }
  }

  @Test
  void companyArgumentsSuggestMatchingEnlistedPlayersOnlinePlayersAndFactionNames() {
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(company.getEnlisted()).thenReturn(List.of("Alice", "Bob"));
    when(guild.getCompany()).thenReturn(company);
    when(Bukkit.getOnlinePlayers())
        .thenAnswer(call -> List.of(gui.player("Alice"), gui.player("Bob")));
    assertEquals(List.of("Alice"), complete("company", "kick", "al"));
    assertEquals(List.of("Bob"), complete("company", "invite", "B"));
    assertEquals(List.of("Realm"), complete("company", "offer", "R"));
    when(other.getName()).thenReturn(null);
    assertEquals(List.of("Realm"), complete("company", "offer", ""));
    assertTrue(complete("company", "unknown", "").isEmpty());
    when(guild.getCompany()).thenReturn(null);
    assertTrue(complete("company", "kick", "").isEmpty());
  }

  @Test
  void companyAdminCompletesOnlyExistingCompaniesAndRecognizedOperations() {
    admin();
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(company.getName()).thenReturn("Silver Shields");
    when(guild.getCompany()).thenReturn(company);
    Guild unnamed = mock(Guild.class);
    when(unnamed.getId()).thenReturn("unnamed");
    when(unnamed.getCompany()).thenReturn(mock(MercenaryCompany.class));
    factions
        .when(FactionManager::getAllGuilds)
        .thenReturn(Arrays.asList(null, mock(Guild.class), guild, unnamed));
    assertEquals(List.of("give", "take"), complete("company", "admin", ""));
    assertEquals(
        List.of("Silver Shields", "merchants", "unnamed"),
        complete("company", "admin", "give", ""));
    assertEquals(List.of("merchants"), complete("company", "admin", "take", "mer"));
    assertEquals(List.of("1"), complete("company", "admin", "give", "merchants", ""));
    when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(false);
    assertTrue(complete("company", "admin", "").isEmpty());
  }

  @Test
  void mercenaryMarketSuggestionsOnlyContainNamedMatchingListings() {
    assertEquals(List.of("list", "hire"), complete("mercenaries"));
    assertEquals(List.of("hire"), complete("mercenaries", "H"));
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(company.getName()).thenReturn("Silver Shields");
    try (MockedStatic<MercenaryMarket> market = mockStatic(MercenaryMarket.class)) {
      market
          .when(MercenaryMarket::listing)
          .thenReturn(List.of(company, mock(MercenaryCompany.class)));
      assertEquals(List.of("Silver Shields"), complete("mercenaries", "hire", "sil"));
      assertTrue(complete("mercenaries", "hire", "absent").isEmpty());
      assertTrue(complete("mercenaries", "unknown", "").isEmpty());
    }
  }

  @Test
  void constructionKindsAreFilteredAndRequireAnEligibleLeader() {
    assertTrue(complete("faction", "construct", "").isEmpty());
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
    assertEquals(
        List.of("fort", "port", "airport", "train_station"), complete("faction", "construct"));
    assertEquals(List.of("train_station"), complete("faction", "construct", "TR"));
    assertEquals(List.of("<name>"), complete("faction", "construct", "fort", ""));
    Cache.provincesEnabled = false;
    assertTrue(complete("faction", "construct", "").isEmpty());
    assertTrue(complete(mock(CommandSender.class), "faction", "construct", "").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"upgrade", "deconstruct", "transfervehicle", "findvehicles"})
  void installationCompletionsUseTheLeadersCurrentInstallations(String action) {
    assertTrue(complete("faction", action, "").isEmpty());
    installationChoices();
    List<String> all = complete("faction", action);
    assertTrue(all.containsAll(List.of("fort", "port")));
    assertEquals(action.equals("deconstruct"), all.contains("fort_building"));
    assertEquals(List.of("port"), complete("faction", action, "P"));
    if (action.equals("upgrade") || action.equals("deconstruct")) {
      Cache.provincesEnabled = false;
      assertTrue(complete("faction", action, "").isEmpty());
    }
  }

  @Test
  void vehicleArgumentsSuggestPlayersPoolInstallationsAndBankPaymentOnlyAtTheirPositions() {
    installationChoices();
    when(Bukkit.getOnlinePlayers())
        .thenAnswer(call -> List.of(gui.player("Alice"), gui.player("Bob")));
    assertTrue(
        complete("faction", "vehicle")
            .containsAll(List.of("give", "handover", "transfer", "maintenance")));
    assertEquals(List.of("transfer"), complete("faction", "vehicle", "tr"));
    assertEquals(List.of("Alice"), complete("faction", "vehicle", "give", "A"));
    assertEquals(List.of("Bob"), complete("faction", "vehicle", "handover", "B"));
    assertEquals(List.of("pool", "port"), complete("faction", "vehicle", "transfer", "p"));
    assertEquals(List.of("fort"), complete("faction", "vehicle", "transfer", "f"));
    assertEquals(List.of("pay"), complete("faction", "vehicle", "maintenance", "p"));
    assertEquals(List.of("bank"), complete("faction", "vehicle", "maintenance", "pay", "b"));
    assertTrue(complete("faction", "vehicle", "unknown", "").isEmpty());
    assertTrue(complete(mock(CommandSender.class), "faction", "vehicle", "").isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "faction,create,<id>",
    "guild,create,<id>",
    "faction,rename,<id>",
    "faction,setculture,<culture>",
    "faction,setreligion,<religion>",
    "faction,setrulingsystem,<ruling system>",
    "faction,setrulertitle,<ruler title>",
    "faction,setcolour,'R,G,B'",
    "guild,setcolour,'R,G,B'"
  })
  void scalarArgumentsHaveSpecificPlaceholders(String command, String action, String placeholder) {
    assertEquals(List.of(placeholder), complete(command, action, ""));
  }

  @ParameterizedTest
  @CsvSource({
    "faction,setbanner",
    "guild,setbanner",
    "faction,claim",
    "faction,unclaim",
    "faction,accept",
    "guild,accept"
  })
  void CommandsWithoutExtraArgumentsOfferNoPlaceholder(String command, String action) {
    assertTrue(complete(command, action, "").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void capitalNamesComeFromTheLeadersSettlements(String command) {
    assertTrue(complete(command, "setcapital", "").isEmpty());
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    SettlementHandler settlements = mock(SettlementHandler.class);
    Settlement settlement = mock(Settlement.class);
    when(settlement.getId()).thenReturn("capital");
    when(settlements.getAll()).thenReturn(List.of(settlement));
    when(realm.getSettlementHandler()).thenReturn(settlements);
    assertEquals(List.of("capital"), complete(command, "setcapital", ""));
    Cache.provincesEnabled = false;
    assertTrue(complete(command, "setcapital", "").isEmpty());
  }

  @Test
  void deletionSuggestionsNeverIncludeTheBaseGuildAndDoNotRepeatThePlayersGuild() {
    assertTrue(complete("faction", "delete", "").isEmpty());
    assertTrue(complete("guild", "delete", "").isEmpty());
    factions.when(() -> FactionManager.getByMember("Leader")).thenReturn(realm);
    factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(guild);
    assertEquals(List.of("realm"), complete("faction", "delete", ""));
    assertEquals(List.of("merchants"), complete("guild", "delete", ""));
    Guild base = mock(Guild.class);
    when(base.getId()).thenReturn("base");
    when(base.isBase()).thenReturn(true);
    Guild second = mock(Guild.class);
    when(second.getId()).thenReturn("miners");
    GuildHandler handler = mock(GuildHandler.class);
    when(handler.getGuilds()).thenReturn(List.of(base, guild, second));
    when(realm.getGuildHandler()).thenReturn(handler);
    admin();
    assertEquals(List.of("merchants", "miners"), complete("guild", "delete", ""));
    when(guild.isBase()).thenReturn(true);
    assertEquals(List.of("miners"), complete("guild", "delete", ""));
  }

  @Test
  void memberAndLeadershipSuggestionsExcludeTheCurrentLeaderAndIneligibleSuccessors() {
    assertTrue(complete("faction", "kick", "").isEmpty());
    assertTrue(complete("guild", "kick", "").isEmpty());
    assertTrue(complete("faction", "setleader", "").isEmpty());
    factions.when(() -> FactionManager.getByMember("Leader")).thenReturn(realm);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    when(realm.canBecomeLeader("Alice")).thenReturn(true);
    assertEquals(List.of("Alice", "Bob"), complete("guild", "kick", ""));
    assertEquals(List.of("Alice", "Bob"), complete("faction", "kick", ""));
    assertEquals(List.of("Alice"), complete("faction", "setleader", ""));
    when(guild.isBase()).thenReturn(true);
    assertTrue(complete("guild", "kick", "").isEmpty());
  }

  @Test
  void invitationCompletionsKeepOnlyTheUnfinishedSuffixOfACharacterName() {
    Player alice = gui.player("Alice");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(alice));
    try (MockedStatic<CharacterNames> characters =
        mockStatic(CharacterNames.class, CALLS_REAL_METHODS)) {
      characters.when(() -> CharacterNames.of("Alice")).thenReturn("Lady Alice");
      assertEquals(List.of("Alice"), complete("faction", "invite", "Lady", "A"));
      assertEquals(List.of("Alice"), complete("guild", "invite", "Al"));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "forceleader",
        "forceupgrade",
        "forceconstruct",
        "forceregiment",
        "forcewithdraw",
        "addprestigemodifier",
        "forcedelete",
        "granttitle",
        "setrelation",
        "settreaty",
        "setpower",
        "setlaw",
        "usurp"
      })
  void administrativeFactionArgumentsRequirePermissionAndListKnownIds(String action) {
    assertTrue(complete("faction", action, "").isEmpty());
    admin();
    assertEquals(List.of("realm", "other"), complete("faction", action, ""));
  }

  @Test
  void administrativeLeaderSuggestionsRespectTheSelectedFactionsEligibility() {
    admin();
    when(realm.canBecomeLeader("Alice")).thenReturn(true);
    assertEquals(List.of("Alice"), complete("faction", "forceleader", "realm", ""));
    assertTrue(complete("faction", "forceleader", "missing", "").isEmpty());
  }

  @Test
  void administrativeInstallationSuggestionsUseSelectedFactionKindAndPrefix() {
    admin();
    installationChoices();
    assertEquals(List.of("fort"), complete("faction", "forceupgrade", "realm", "F"));
    assertTrue(complete("faction", "forceupgrade", "missing", "").isEmpty());
    assertEquals(List.of("airport"), complete("faction", "forceconstruct", "realm", "air"));
    assertEquals(List.of("<name>"), complete("faction", "forceconstruct", "realm", "fort", ""));
    Cache.provincesEnabled = false;
    assertTrue(complete("faction", "forceconstruct", "realm", "").isEmpty());
    assertTrue(complete("faction", "forceupgrade", "realm", "").isEmpty());
  }

  @Test
  void administrativeRegimentChoicesExcludeLeviesAndOfferValidActionsAndAmount() {
    admin();
    Military military = mock(Military.class);
    Regiment professional = mock(Regiment.class);
    when(professional.getId()).thenReturn("archers");
    Regiment levy = mock(Regiment.class);
    when(levy.getId()).thenReturn("levy");
    when(levy.isLevy()).thenReturn(true);
    when(military.getRegiments()).thenReturn(List.of(professional, levy));
    when(realm.getMilitary()).thenReturn(military);
    assertEquals(List.of("give", "take"), complete("faction", "forceregiment", "realm", ""));
    assertEquals(List.of("archers"), complete("faction", "forceregiment", "realm", "give", ""));
    assertTrue(complete("faction", "forceregiment", "missing", "give", "").isEmpty());
    assertEquals(
        List.of("1"), complete("faction", "forceregiment", "realm", "give", "archers", ""));
  }

  @Test
  void forceJoinSuggestsOnlyMatchingFactionIdsAndPlayersWithoutMembership() {
    admin();
    Player free = gui.player("Free");
    Player member = gui.player("Alice");
    Player elsewhere = gui.player("Joined");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(free, member, elsewhere));
    factions.when(() -> FactionManager.getByMember("Joined")).thenReturn(other);
    assertEquals(List.of("realm"), complete("faction", "forcejoin", "RE"));
    assertEquals(List.of("Free"), complete("faction", "forcejoin", "realm", ""));
    assertEquals(List.of("Free"), complete("faction", "forcejoin", "realm", "f"));
    assertTrue(complete("faction", "forcejoin", "missing", "").isEmpty());
  }

  @Test
  void administrativeNumbersAndModifiersHaveClearArgumentPlaceholders() {
    admin();
    assertEquals(List.of("1.0"), complete("faction", "forcewithdraw", "realm", ""));
    assertEquals(List.of("<type>"), complete("faction", "addprestigemodifier", "realm", ""));
    assertEquals(
        List.of("1.0"), complete("faction", "addprestigemodifier", "realm", "festival", ""));
  }

  @Test
  void titleSuggestionsDistinguishOwnedAndUnownedTitles() {
    admin();
    Title owned = mock(Title.class);
    Title unowned = mock(Title.class);
    when(owned.getId()).thenReturn("owned_title");
    when(unowned.getId()).thenReturn("unowned_title");
    try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
      titles.when(TitleManager::getAllOwnedTitles).thenReturn(List.of(owned));
      titles.when(TitleManager::getAllUnownedTitles).thenReturn(List.of(unowned));
      assertEquals(List.of("owned_title"), complete("faction", "destroytitle", ""));
      assertEquals(List.of("unowned_title"), complete("faction", "granttitle", "realm", ""));
    }
    when(other.getTitles()).thenReturn(List.of(owned));
    assertEquals(List.of("other"), complete("faction", "usurp", "realm", ""));
    when(other.getTitles()).thenReturn(List.of());
    assertTrue(complete("faction", "usurp", "realm", "").isEmpty());
  }

  @Test
  void diplomacySuggestionsFilterSubjectsExcludeTheSourceAndSeparateRelationTypes() {
    admin();
    try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
        MockedStatic<RelationLoader> types = mockStatic(RelationLoader.class)) {
      relations.when(() -> RelationManager.getOverlord(realm)).thenReturn("other");
      assertEquals(List.of("realm"), complete("faction", "transfersubject", ""));
      assertEquals(List.of("other"), complete("faction", "transfersubject", "realm", ""));
      assertEquals(List.of("other"), complete("faction", "setrelation", "realm", ""));
      assertEquals(List.of("other"), complete("faction", "settreaty", "realm", ""));
      RelationType diplomatic = mock(RelationType.class);
      RelationType trade = mock(RelationType.class);
      RelationType political = mock(RelationType.class);
      when(diplomatic.getId()).thenReturn("alliance");
      when(trade.getId()).thenReturn("trade");
      when(political.getId()).thenReturn("non_aggression");
      types.when(RelationLoader::getDiplomaticTypes).thenReturn(List.of(diplomatic));
      types.when(RelationLoader::getTreatyTypes).thenReturn(List.of(trade));
      types.when(RelationLoader::getPoliticalTreatyTypes).thenReturn(List.of(political));
      assertEquals(List.of("alliance"), complete("faction", "setrelation", "realm", "other", ""));
      assertEquals(
          List.of("trade", "non_aggression"),
          complete("faction", "settreaty", "realm", "other", ""));
    }
  }

  @Test
  void lawSuggestionsUseTheSelectedGroupAndStanceOwnersAreDeduplicated() {
    admin();
    LawGroup group = mock(LawGroup.class);
    when(group.getId()).thenReturn("tax");
    when(group.getLaws()).thenReturn(Map.of("low", mock(Law.class)));
    try (MockedStatic<LawLoader> laws = mockStatic(LawLoader.class)) {
      laws.when(LawLoader::getList).thenReturn(List.of(group));
      laws.when(() -> LawLoader.getByString("tax")).thenReturn(group);
      assertEquals(List.of("tax"), complete("faction", "setlaw", "realm", ""));
      assertEquals(List.of("low"), complete("faction", "setlaw", "realm", "tax", ""));
      assertTrue(complete("faction", "setlaw", "realm", "missing", "").isEmpty());
    }
    Guild base = mock(Guild.class);
    when(base.getId()).thenReturn("realm");
    factions.when(FactionManager::getAllGuilds).thenReturn(List.of(base, guild));
    assertEquals(List.of("realm", "merchants", "other"), complete("faction", "setstance", ""));
    assertEquals(
        List.of("oppose", "neutral", "support"), complete("faction", "setstance", "realm", ""));
  }

  @Test
  void playerOnlyAndUnknownCommandsLeaveConsoleCompletionEmpty() {
    CommandSender console = mock(CommandSender.class);
    for (String command : List.of("guild", "faction", "company", "unrelated"))
      assertTrue(complete(console, command, "").isEmpty());
    assertTrue(complete("unrelated", "unknown").isEmpty());
  }

  private void installationChoices() {
    InstallationHandler installations = mock(InstallationHandler.class);
    Installation fort = mock(Installation.class);
    Installation port = mock(Installation.class);
    when(fort.getId()).thenReturn("fort");
    when(port.getId()).thenReturn("port");
    when(installations.getAll()).thenReturn(List.of(fort, port));
    InstallationConstruction pending = mock(InstallationConstruction.class);
    when(pending.getId()).thenReturn("fort_building");
    when(installations.getPendingConstruction()).thenReturn(pending);
    when(realm.getInstallationHandler()).thenReturn(installations);
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
  }

  private void admin() {
    when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
  }

  private Faction realm(String id, String name) {
    Faction faction = mock(Faction.class);
    when(faction.getId()).thenReturn(id);
    when(faction.getName()).thenReturn(name);
    when(faction.getLeader()).thenReturn("Leader");
    when(faction.getMembers()).thenReturn(new ArrayList<>(List.of("Leader", "Alice", "Bob")));
    when(faction.getGuildHandler()).thenReturn(new GuildHandler(faction));
    return faction;
  }

  private List<String> complete(String commandName, String... args) {
    return complete(player, commandName, args);
  }

  private List<String> complete(CommandSender sender, String commandName, String... args) {
    Command command = mock(Command.class);
    when(command.getName()).thenReturn(commandName);
    List<String> result = completion.onTabComplete(sender, command, commandName, args);
    return result == null ? List.of() : result;
  }
}
