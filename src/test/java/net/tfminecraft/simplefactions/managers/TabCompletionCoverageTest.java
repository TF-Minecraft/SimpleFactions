package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.managers.inventory.CharacterKeyFixture;
import net.tfminecraft.simplefactions.espionage.EspionageCommands;
import net.tfminecraft.simplefactions.espionage.EspionageConfig;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
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
  @ValueSource(strings = {"unrelated"})
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
                "admin",
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
  void forceRemoveCapitalSuggestsSeatedNonBaseGuilds() {
    admin();
    Guild base = mock(Guild.class);
    when(base.getId()).thenReturn("mainguild");
    when(base.isBase()).thenReturn(true);
    when(base.hasCapital()).thenReturn(true);
    Guild unseated = mock(Guild.class);
    when(unseated.getId()).thenReturn("miners");
    when(guild.hasCapital()).thenReturn(true);
    factions.when(FactionManager::getAllGuilds).thenReturn(Arrays.asList(guild, null, base, unseated));
    assertEquals(List.of("merchants"), complete("guild", "forceremovecapital", "M"));
    assertEquals(List.of("realm", "other"), complete("faction", "forceclaim", ""));
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
    when(guild.hasCompany()).thenReturn(true);
    when(company.isLeader("Leader")).thenReturn(true);
    when(company.hasFreeSlot()).thenReturn(true);
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
    assertEquals(List.of("give", "take", "add"), complete("company", "admin", ""));
    assertEquals(
        List.of("merchants", "unnamed"),
        complete("company", "admin", "give", ""));
    assertEquals(List.of("merchants"), complete("company", "admin", "take", "mer"));
    assertEquals(List.of(), complete("company", "admin", "give", "merchants", ""));
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
        List.of("fort", "port", "airport", "train_station"), complete("faction", "construct", ""));
    assertEquals(List.of("train_station"), complete("faction", "construct", "TR"));
    assertEquals(List.of(), complete("faction", "construct", "fort", ""));
    Cache.provincesEnabled = false;
    assertTrue(complete("faction", "construct", "").isEmpty());
    assertTrue(complete(mock(CommandSender.class), "faction", "construct", "").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"upgrade", "deconstruct", "transfervehicle", "findvehicles"})
  void installationCompletionsUseTheLeadersCurrentInstallations(String action) {
    assertEquals(action.equals("findvehicles") ? List.of("personal") : List.of(), complete("faction", action, ""));
    installationChoices();
    assertEquals(List.of(action), complete("faction", action));
    List<String> all = complete("faction", action, "");
    assertTrue(all.containsAll(List.of("fort", "port")));
    assertEquals(action.equals("deconstruct"), all.contains("fort_building"));
    assertEquals(action.equals("findvehicles") ? List.of("pool", "personal", "port") : List.of("port"), complete("faction", action, "P"));
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
        complete("faction", "vehicle", "")
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
  void freeTextArgumentsDoNotInsertUsageHints(String command, String action, String placeholder) {
    assertEquals(List.of(), complete(command, action, ""));
    assertEquals(List.of(), complete(command, action, "MIXED"));
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
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
    factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(guild);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
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
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
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
    CharacterKeyFixture.install().play(alice, "Lady Alice");
    assertEquals(List.of("Alice"), complete("faction", "invite", "Lady", "A"));
    assertEquals(List.of("Alice"), complete("guild", "invite", "Al"));
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
    assertEquals(List.of(), complete("faction", "forceconstruct", "realm", "fort", ""));
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
    Regiment mercenary = mock(Regiment.class);
    when(mercenary.isMercenary()).thenReturn(true);
    when(mercenary.getId()).thenReturn("company_owned");
    when(military.getRegiments()).thenReturn(List.of(professional, levy, mercenary));
    when(realm.getMilitary()).thenReturn(military);
    assertEquals(List.of("give", "take"), complete("faction", "forceregiment", "realm", ""));
    assertEquals(List.of("archers"), complete("faction", "forceregiment", "realm", "give", ""));
    assertEquals(List.of("archers"), complete("faction", "forceregiment", "realm", "take", "AR"));
    assertTrue(complete("faction", "forceregiment", "missing", "give", "").isEmpty());
    assertEquals(
        List.of(), complete("faction", "forceregiment", "realm", "give", "archers", ""));
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
  void administrativeFreeTextAndNumbersHaveNoInventedValues() {
    admin();
    assertEquals(List.of(), complete("faction", "forcewithdraw", "realm", ""));
    assertEquals(List.of(), complete("faction", "addprestigemodifier", "realm", ""));
    assertEquals(
        List.of(), complete("faction", "addprestigemodifier", "realm", "festival", ""));
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
      assertEquals(List.of("alliance"), complete("faction", "setrelation", "realm", "other", "ALL"));
      assertTrue(complete("faction", "setrelation", "realm", "other", "tr").isEmpty());
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

  @Test
  void missingRootActionsFollowMemberLeaderAdminAndProvinceContext() {
    assertTrue(complete("guild", "li").contains("list"));
    factions.when(() -> FactionManager.getByMember("Leader")).thenReturn(realm);
    when(realm.getBank()).thenReturn(mock(net.tfminecraft.simplefactions.objects.Bank.class));
    assertTrue(complete("faction", "").containsAll(List.of("deposit", "leave")));
    when(realm.isInGuild("Leader")).thenReturn(true);
    assertFalse(complete("faction", "").contains("leave"));
    factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(guild);
    assertEquals(List.of("leave"), complete("guild", "lea"));
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    assertFalse(complete("guild", "").contains("leave"));
    assertEquals(List.of("delete"), complete("guild", "del"));
    admin();
    assertTrue(complete("faction", "").containsAll(List.of("admin", "forcedelete", "refresh", "delbank", "provincecap")));
    assertEquals(List.of("quarantine"), complete("faction", "admin", "QU"));
    assertEquals(List.of("assign"), complete("faction", "admin", "quarantine", "AS"));
    Cache.provincesEnabled = false;
    assertFalse(complete("faction", "").contains("provincecap"));
  }

  @Test
  void rootPrefixFilteringKeepsEveryAuthorizedMatchAndExactCommandToken() {
    admin();
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    when(player.hasPermission(EspionageConfig.reloadPermission())).thenReturn(true);
    assertEquals(List.of("setleader"), complete("guild", "SeTL"));
    assertEquals(List.of("invite"), complete("faction", "iNV"));
    assertTrue(complete("faction", "re").containsAll(List.of("rename", "reloadconfigs", "reloadtitles", "reloadespionage", "refresh")));
    assertTrue(complete("faction", "re").stream().allMatch(value -> value.startsWith("re")));
    for (String action : List.of("construct", "upgrade", "deconstruct", "transfervehicle", "findvehicles", "vehicle"))
      assertEquals(List.of(action), complete("faction", action));
    assertEquals(List.of("fort", "port", "airport", "train_station"), complete("faction", "construct", ""));
  }

  @Test
  void adminFactionIdsAndDeletionIncludeTargetsOutsideMembership() {
    admin();
    for (String action : List.of("delete", "delbank", "startelection", "endelection", "unclaim")) {
      assertEquals(List.of("realm", "other"), complete("faction", action, ""));
      assertEquals(List.of("other"), complete("faction", action, "Ot"));
      assertEquals(List.of(), complete("faction", action, "other", ""));
    }
    when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(false);
    assertTrue(complete("faction", "delete", "").isEmpty());
    assertTrue(complete("faction", "delbank", "").isEmpty());
  }

  @Test
  void companyContractsRemainDiscoverableToMembersAndNamesCompleteAfterSpaces() {
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(guild.getCompany()).thenReturn(company);
    when(guild.hasCompany()).thenReturn(true);
    factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(guild);
    assertEquals(List.of("contracts"), complete("company", "CON"));
    assertFalse(complete("company", "").contains("invite"));
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    assertEquals(List.of("Realm"), complete("company", "offer", "Other", "rE"));
    assertTrue(complete("company", "offer", "Other", "Realm", "").isEmpty());
    when(company.getName()).thenReturn("Silver Shields");
    try (MockedStatic<MercenaryMarket> market = mockStatic(MercenaryMarket.class)) {
      market.when(MercenaryMarket::listing).thenReturn(List.of(company));
      assertEquals(List.of("Shields"), complete("mercenaries", "hire", "Silver", "sH"));
      assertTrue(complete("mercenaries", "hire", "Silver", "Shields", "").isEmpty());
    }
  }

  @Test
  void companyAdminTargetsFitTheExecutorsSingleTokenGrammar() {
    admin();
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(company.getName()).thenReturn("Silver Shields");
    when(guild.getCompany()).thenReturn(company);
    factions.when(FactionManager::getAllGuilds).thenReturn(List.of(guild));
    assertEquals(List.of("merchants"), complete("company", "admin", "give", ""));
    for (String target : complete("company", "admin", "take", "")) {
      assertEquals(1, target.split(" ").length);
      assertEquals(guild.getId(), target);
    }
    when(company.getName()).thenReturn("Silver");
    assertEquals(List.of("Silver", "merchants"), complete("company", "admin", "take", ""));
  }

  @Test
  void guildSetLeaderAndLeaderlessAppointmentsCompleteEligibleOfflineCharacterSuffixes() {
    CharacterKeyFixture characters = CharacterKeyFixture.install();
    String leader = characters.play(player, "Lord Aldric");
    String alice = characters.offline(UUID.randomUUID(), "Alice Fairweather");
    String bob = characters.offline(UUID.randomUUID(), "Bob Stone");
    when(realm.getMembers()).thenReturn(List.of(leader, alice, bob));
    when(guild.getMembers()).thenReturn(List.of(leader, alice));
    when(guild.getLeader()).thenReturn(leader);
    when(guild.isLeader(leader)).thenReturn(true);
    factions.when(() -> FactionManager.getGuildByLeader(leader)).thenReturn(guild);
    factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    assertEquals(List.of("Alice Fairweather"), complete("guild", "setleader", "aL"));
    assertEquals(List.of("Fairweather"), complete("guild", "setleader", "Alice", "f"));
    assertTrue(complete("guild", "setleader", "Bob", "").isEmpty());
    when(guild.isLeaderless()).thenReturn(true);
    when(guild.getMembers()).thenReturn(List.of());
    factions.when(() -> FactionManager.getGuildByLeader(bob)).thenReturn(mock(Guild.class));
    try (MockedStatic<net.tfminecraft.simplefactions.guild.LeaderlessGuilds> leaderless = mockStatic(net.tfminecraft.simplefactions.guild.LeaderlessGuilds.class)) {
      leaderless.when(() -> net.tfminecraft.simplefactions.guild.LeaderlessGuilds.appointable(leader)).thenReturn(List.of(guild));
      assertEquals(List.of("Alice Fairweather"), complete("guild", "appoint", "merchants", ""));
      assertEquals(List.of("Fairweather"), complete("guild", "appoint", "merchants", "Alice", ""));
      assertTrue(complete("guild", "appoint", "missing", "").isEmpty());
      leaderless.when(() -> net.tfminecraft.simplefactions.guild.LeaderlessGuilds.appointable(leader)).thenReturn(List.of());
      assertTrue(complete("guild", "appoint", "merchants", "").isEmpty());
    }
    admin();
    assertEquals(List.of("Alice Fairweather"), complete("guild", "forceleader", "merchants", ""));
    assertEquals(List.of("Fairweather"), complete("guild", "forceleader", "merchants", "Alice", "F"));
    assertTrue(complete("guild", "forceleader", "merchants", "Bob", "").isEmpty());
    var livingBob = net.tfminecraft.simplefactions.identity.Members.info(bob).orElseThrow();
    factions.when(() -> FactionManager.getGuildByLeader(bob)).thenReturn(null);
    var deadBob = new net.tfminecraft.tlibs.character.CharacterInfo(livingBob.ref(), livingBob.name(),
        net.tfminecraft.tlibs.character.CharacterStatus.DEAD, null, java.util.Set.of(), 0L, java.time.Instant.EPOCH);
    try (MockedStatic<net.tfminecraft.simplefactions.identity.Members> identities = mockStatic(net.tfminecraft.simplefactions.identity.Members.class, CALLS_REAL_METHODS)) {
      identities.when(() -> net.tfminecraft.simplefactions.identity.Members.info(bob)).thenReturn(java.util.Optional.of(deadBob));
      assertEquals(List.of("Alice Fairweather"), complete("guild", "forceleader", "merchants", ""));
    }

  }

  @Test
  void companyAndVehicleRecipientsIncludeOnlineCharacterNamesAndSuffixes() {
    CharacterKeyFixture characters = CharacterKeyFixture.install();
    String leader = characters.play(player, "Lord Aldric");
    Player account = gui.player("BlockCrafter");
    String alice = characters.play(account, "Alice Fairweather");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(player, account));
    factions.when(() -> FactionManager.getByLeader(leader)).thenReturn(realm);
    factions.when(() -> FactionManager.getGuildByLeader(leader)).thenReturn(guild);
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(guild.hasCompany()).thenReturn(true);
    when(guild.getCompany()).thenReturn(company);
    when(company.isLeader(leader)).thenReturn(true);
    when(company.hasFreeSlot()).thenReturn(true);
    assertEquals(List.of("Alice Fairweather"), complete("company", "invite", "Al"));
    assertEquals(List.of("Fairweather"), complete("company", "invite", "Alice", "f"));
    for (String action : List.of("give", "handover")) {
      assertEquals(List.of("Alice Fairweather"), complete("faction", "vehicle", action, "Al"));
      assertEquals(List.of("Fairweather"), complete("faction", "vehicle", action, "Alice", ""));
      assertEquals(List.of("BlockCrafter"), complete("faction", "vehicle", action, "Bl"));
    }
    when(company.isEnlisted(alice)).thenReturn(true);
    assertTrue(complete("company", "invite", "Al").isEmpty());
  }

  @Test
  void factionKickExcludesMembersWhoMustLeaveThroughTheirSubguild() {
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(realm);
    when(realm.isInGuild("Bob")).thenReturn(true);
    assertEquals(List.of("Alice"), complete("faction", "kick", ""));
    assertTrue(complete("faction", "kick", "B").isEmpty());
  }

  @Test
  void offeredLeaderlessGuildCanBeDeclinedWithoutAnOrdinaryInvite() {
    try (MockedStatic<net.tfminecraft.simplefactions.guild.LeaderlessGuilds> leaderless = mockStatic(net.tfminecraft.simplefactions.guild.LeaderlessGuilds.class)) {
      leaderless.when(() -> net.tfminecraft.simplefactions.guild.LeaderlessGuilds.offeredTo("Leader")).thenReturn(guild);
      assertEquals(List.of("merchants"), complete("guild", "decline", "MER"));
      assertTrue(complete("guild", "join", "MER").isEmpty());
      assertTrue(complete("guild", "decline", "x").isEmpty());
    }
  }

  @Test
  void staffConsoleDiscoversOnlySupportedFactionRoots() {
    CommandSender console = mock(CommandSender.class);
    when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
    assertEquals(List.of("title"), complete(console, "faction", "tiT"));
    assertEquals(List.of("addguildbank"), complete(console, "faction", "addg"));
    assertEquals(List.of("tradegraph"), complete(console, "faction", "trad"));
    assertTrue(complete(console, "faction", "").contains("admin"));
    assertFalse(complete(console, "faction", "").contains("create"));
    // /guild admin also works from the console, so it is offered there; nothing else is.
    assertEquals(List.of("admin"), complete(console, "guild", ""));
    assertEquals(List.of("admin"), complete(console, "guild", "AD"));
    assertTrue(complete(console, "guild", "cr").isEmpty());
    factions.when(FactionManager::getAllGuilds).thenReturn(Arrays.asList(guild, null));
    assertEquals(List.of("merchants"), complete(console, "guild", "admin", "info", "mer"));
    CommandSender visitor = mock(CommandSender.class);
    assertTrue(complete(visitor, "guild", "").isEmpty());
    Cache.provincesEnabled = false;
    assertTrue(complete(console, "faction", "tit").isEmpty());
  }

  @Test
  void pluginCommandDoesNotFallBackToPlayerNamesAfterTerminalOrUnknownArguments() throws Exception {
    Player fallback = gui.player("UnrelatedAccount");
    when(player.getServer()).thenReturn(gui.server);
    when(gui.server.suggestPlayerNamesWhenNullTabCompletions()).thenReturn(true);
    when(gui.server.getOnlinePlayers()).thenAnswer(call -> List.of(fallback));
    when(player.canSee(fallback)).thenReturn(true);
    var constructor = org.bukkit.command.PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
    constructor.setAccessible(true);
    for (String root : List.of("faction", "guild")) {
      var command = constructor.newInstance(root, gui.plugin);
      command.setExecutor((sender, cmd, label, args) -> true);
      command.setTabCompleter((sender, cmd, label, args) -> null);
      assertEquals(List.of("UnrelatedAccount"), command.tabComplete(player, root, new String[] {"list", ""}));
      command.setTabCompleter(completion);
      for (String action : List.of("list", "reloadconfigs", "menu", "leave", "unknown"))
        assertEquals(List.of(), command.tabComplete(player, root, new String[] {action, ""}));
    }
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

  @Test
  void memberSuggestionsOfferCharacterNamesAndCompleteTheirLastWords() {
    CharacterKeyFixture characters = CharacterKeyFixture.install();
    String leader = characters.play(player, "Lord Aldric");
    String alice = characters.play(gui.player("Alice"), "Alice Fairweather");
    String bob = characters.offline(UUID.randomUUID(), "Bob Stone");
    List<String> members = List.of(leader, alice, bob);
    when(realm.getLeader()).thenReturn(leader);
    when(realm.getMembers()).thenAnswer(call -> new ArrayList<>(members));
    when(realm.canBecomeLeader(alice)).thenReturn(true);
    when(guild.getLeader()).thenReturn(leader);
    when(guild.getMembers()).thenAnswer(call -> new ArrayList<>(members));
    when(guild.isLeader(leader)).thenReturn(true);
    factions.when(() -> FactionManager.getByMember(leader)).thenReturn(realm);
    factions.when(() -> FactionManager.getByLeader(leader)).thenReturn(realm);
    factions.when(() -> FactionManager.getGuildByLeader(leader)).thenReturn(guild);
    factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);

    assertEquals(List.of("Alice Fairweather", "Bob Stone"), complete("faction", "kick", ""));
    assertEquals(List.of("Fairweather"), complete("guild", "kick", "Alice", "F"));
    assertEquals(List.of("Alice Fairweather"), complete("faction", "setleader", "a"));
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(company.getEnlisted()).thenReturn(List.of(bob));
    when(guild.getCompany()).thenReturn(company);
    assertEquals(List.of("Stone"), complete("company", "kick", "Bob", ""));

    admin();
    assertEquals(List.of("Fairweather"), complete("faction", "forceleader", "realm", "Alice", ""));
    assertEquals(
        List.of("Alice Fairweather", "Bob Stone"),
        complete("guild", "forceleader", "merchants", ""));
    Player stranger = gui.player("Stranger");
    Player free = gui.player("Free");
    characters.play(free, "Free Folk");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(stranger, free));
    assertEquals(List.of("Free"), complete("faction", "forcejoin", "realm", ""));
  }

  private void admin() {
    when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"join", "decline"})
  void joinAndDeclineSuggestOnlyPendingInvites(String action) {
    when(realm.isInvited("Leader")).thenReturn(true);
    Guild smiths = mock(Guild.class);
    when(smiths.getId()).thenReturn("Smiths");
    when(smiths.isInvited("Leader")).thenReturn(true);
    GuildHandler handler = mock(GuildHandler.class);
    when(handler.getGuilds()).thenReturn(List.of(smiths));
    when(other.getGuildHandler()).thenReturn(handler);
    assertEquals(List.of("realm"), complete("faction", action, "r"));
    assertEquals(List.of(), complete("faction", action, "o"));
    assertEquals(List.of("Smiths"), complete("guild", action, "sm"));
  }

  @Test
  void guildForceLeaderSuggestsSubGuildsThenTheirOtherMembersForAdmins() {
    Guild base = mock(Guild.class);
    when(base.getId()).thenReturn("realm");
    when(base.isBase()).thenReturn(true);
    GuildHandler handler = mock(GuildHandler.class);
    when(handler.getGuilds()).thenReturn(List.of(base, guild));
    when(realm.getGuildHandler()).thenReturn(handler);
    when(guild.isLeader("Leader")).thenReturn(true);
    factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    assertFalse(complete("guild").contains("forceleader"));
    assertEquals(List.of(), complete("guild", "forceleader", ""));
    admin();
    assertTrue(complete("guild").contains("forceleader"));
    assertEquals(List.of("merchants"), complete("guild", "forceleader", ""));
    assertEquals(List.of("Alice"), complete("guild", "forceleader", "merchants", "a"));
    assertEquals(List.of(), complete("guild", "forceleader", "missing", ""));
  }

  @Test
  void guildForceJoinSuggestsNonBaseGuildsThenOnlineCharactersOutsideIt() {
    Guild base = mock(Guild.class);
    when(base.getId()).thenReturn("mainguild");
    when(base.isBase()).thenReturn(true);
    factions.when(FactionManager::getAllGuilds).thenReturn(Arrays.asList(guild, null, base));
    factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    Player alice = gui.player("Alice"), bob = gui.player("Bob"), nobody = gui.player("Nobody");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(alice, bob, nobody));
    CharacterKeyFixture keys = CharacterKeyFixture.install();
    keys.play(alice, "Alice Smith");
    keys.play(bob, "Bob Smith");
    when(guild.isMember(Members.key(alice))).thenReturn(true);
    assertFalse(complete("guild").contains("forcejoin"));
    admin();
    assertTrue(complete("guild").contains("forcejoin"));
    assertEquals(List.of("merchants"), complete("guild", "forcejoin", "M"));
    assertEquals(List.of("Bob Smith"), complete("guild", "forcejoin", "merchants", ""));
    assertEquals(List.of("Smith"), complete("guild", "forcejoin", "merchants", "Bob", ""));
    assertEquals(List.of(), complete("guild", "forcejoin", "missing", ""));
  }

  @Test
  void companyAdminAddSuggestsCompaniesThenOnlineCharacters() {
    admin();
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(company.getName()).thenReturn("Lances");
    when(guild.getCompany()).thenReturn(company);
    factions.when(FactionManager::getAllGuilds).thenReturn(Arrays.asList(guild));
    Player alice = gui.player("Alice"), nobody = gui.player("Nobody");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(alice, nobody));
    CharacterKeyFixture.install().play(alice, "Alice Smith");
    assertEquals(List.of("add"), complete("company", "admin", "a"));
    assertEquals(List.of("Lances", "merchants"), complete("company", "admin", "add", ""));
    assertEquals(List.of("Alice Smith"), complete("company", "admin", "add", "Lances", "al"));
    assertEquals(List.of("Smith"), complete("company", "admin", "add", "Lances", "Alice", "S"));
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
    assertNotNull(result);
    return result;
  }

  @Test
  void leaderlessGuildCommandsCompleteForTheRealmLeaderAndTheOfferedCharacter() {
    Guild smiths = mock(Guild.class);
    when(smiths.getId()).thenReturn("smiths");
    try (MockedStatic<net.tfminecraft.simplefactions.guild.LeaderlessGuilds> leaderless =
        mockStatic(net.tfminecraft.simplefactions.guild.LeaderlessGuilds.class)) {
      leaderless.when(() -> net.tfminecraft.simplefactions.guild.LeaderlessGuilds.appointable("Leader"))
          .thenReturn(List.of(smiths));
      leaderless.when(() -> net.tfminecraft.simplefactions.guild.LeaderlessGuilds.offeredTo("Leader"))
          .thenReturn(smiths);
      assertTrue(complete("guild", "").containsAll(List.of("appoint", "lead")));
      assertEquals(List.of("smiths"), complete("guild", "appoint", "Sm"));
      assertTrue(complete("guild", "appoint", "x").isEmpty());
      assertEquals(List.of("smiths"), complete("guild", "lead", ""));
      leaderless.when(() -> net.tfminecraft.simplefactions.guild.LeaderlessGuilds.offeredTo("Leader"))
          .thenReturn(null);
      assertTrue(complete("guild", "lead", "").isEmpty());
      leaderless.when(() -> net.tfminecraft.simplefactions.guild.LeaderlessGuilds.appointable("Leader"))
          .thenReturn(List.of());
      assertFalse(complete("guild", "").contains("appoint"));
      assertFalse(complete("guild", "").contains("lead"));
    }
  }
}
