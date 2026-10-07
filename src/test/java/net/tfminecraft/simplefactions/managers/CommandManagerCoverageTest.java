package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.data.Account;
import net.tfminecraft.denareconomy.data.PlayerData;
import net.tfminecraft.denareconomy.managers.MoneyManager;
import net.tfminecraft.denareconomy.managers.PlayerManager;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.ExpandResult;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.espionage.CharacterNames;
import net.tfminecraft.simplefactions.espionage.EspionageCommands;
import net.tfminecraft.simplefactions.events.FactionCreateEvent;
import net.tfminecraft.simplefactions.events.FactionDeleteEvent;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.election.Election;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.GuildBankGrant;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.network.TradeGraphCommand;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationConstruction;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.ConstructResult;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.loaders.LawLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.inventory.InstallationView;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.BankPlacementValidator;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.settlement.handler.CapitalResult;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.tiers.admin.TitleAdminCommand;
import net.tfminecraft.simplefactions.utils.DisplayNameGate;
import net.tfminecraft.simplefactions.utils.DisplayNameGate.NameOperation;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.utils.RandomRGB;
import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleFindMessages;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class CommandManagerCoverageTest {
  private GuiTestFixture gui;
  private MockedStatic<FactionManager> factions;
  private CommandManager commands;
  private InventoryManager registered;
  private Player player;
  private Faction faction;
  private InstallationHandler installations;
  private boolean previousProvinces;
  private List<Faction> previousFactions;
  private List<Bank> previousBanks;
  private Government government;
  private Guild guild;
  private GuildHandler guilds;
  private MapSystem map;

  @BeforeEach
  void setUp() {
    previousProvinces = Cache.provincesEnabled;
    previousFactions = FactionManager.factions;
    previousBanks = BankManager.banks;
    FactionManager.factions = new ArrayList<>();
    BankManager.banks = new ArrayList<>();
    Cache.provincesEnabled = true;
    gui = new GuiTestFixture();
    player = gui.player("Leader");
    faction = mock(Faction.class);
    installations = mock(InstallationHandler.class);
    when(faction.getId()).thenReturn("realm");
    when(faction.getLeader()).thenReturn("Leader");
    when(faction.getName()).thenReturn("Realm");
    when(faction.getMembers()).thenReturn(new ArrayList<>(List.of("Leader", "Alice")));
    government = mock(Government.class);
    when(faction.getGovernment()).thenReturn(government);
    guild = mock(Guild.class, RETURNS_DEEP_STUBS);
    when(guild.getId()).thenReturn("merchants");
    when(guild.getName()).thenReturn("Merchants");
    when(guild.getLeader()).thenReturn("Leader");
    when(guild.getFaction()).thenReturn(faction);
    when(guild.getMembers()).thenReturn(new ArrayList<>(List.of("Leader", "Alice")));
    when(guild.isLeader(player)).thenReturn(true);
    when(guild.getBank()).thenReturn(null);
    when(faction.getOrCreateMainGuild()).thenReturn(guild);
    guilds = mock(GuildHandler.class);
    when(faction.getGuildHandler()).thenReturn(guilds);
    map = mock(MapSystem.class);
    when(faction.getInstallationHandler()).thenReturn(installations);
    when(installations.getById("fort")).thenReturn(mock(Installation.class));
    when(installations.deconstruct("fort")).thenReturn(ConstructResult.ok("Removed fort"));
    when(installations.upgrade("fort")).thenReturn(ConstructResult.ok("Upgrade queued"));
    registered = new InventoryManager();
    registered.installationView = mock(InstallationView.class);
    factions = mockStatic(FactionManager.class);
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(faction);
    // SimpleFactions.onEnable publishes the InventoryManager registered as its event listener here.
    factions.when(FactionManager::getInv).thenReturn(registered);
    factions.when(FactionManager::getMap).thenReturn(map);
    factions.when(() -> FactionManager.getByString("realm")).thenReturn(faction);
    factions.when(() -> FactionManager.getGuildByString("merchants")).thenReturn(guild);
    commands = new CommandManager();
  }

  @AfterEach
  void close() {
    try {
      if (factions != null) factions.close();
    } finally {
      try {
        if (gui != null) gui.close();
      } finally {
        Cache.provincesEnabled = previousProvinces;
        FactionManager.factions = previousFactions;
        BankManager.banks = previousBanks;
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"deconstruct", "upgrade"})
  void installationCommandConfirmationIsHandledByTheRegisteredListener(String action) {
    assertTrue(run("faction", action, "fort"));
    assertEquals("§7Confirm Action", player.getOpenInventory().getTitle());
    registered.clickButton(gui.click(player, 11));
    if (action.equals("deconstruct")) verify(installations).deconstruct("fort");
    else verify(installations).upgrade("fort");
    verify(registered.installationView).installationsView(null, player, faction, true);
  }

  @ParameterizedTest
  @CsvSource({
    "addprestigemodifier,bad",
    "addprestigemodifier,NaN",
    "addprestigemodifier,Infinity",
    "addprestigemodifier,-Infinity",
    "setpower,bad",
    "setpower,NaN",
    "setpower,Infinity",
    "setpower,-Infinity"
  })
  void administrativeNumericInputCannotCrashOrStoreNonFiniteValues(String action, String amount) {
    when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
    factions.when(() -> FactionManager.getByString("realm")).thenReturn(faction);
    Government government = mock(Government.class);
    when(faction.getGovernment()).thenReturn(government);
    if (action.equals("setpower")) run("faction", action, "realm", amount);
    else run("faction", action, "realm", "festival", amount);
    verify(faction, never()).addPersistentPrestigeModifier(any());
    verify(government, never()).setPower(anyDouble());
    verify(player).sendMessage(contains("number"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void missingSubcommandHasUsageAndConsoleCannotRunPlayerCommands(String name) {
    assertTrue(run(name));
    verify(player).sendMessage(contains("command format"));
    CommandSender console = mock(CommandSender.class);
    assertFalse(run(console, name, "create", "Realm"));
    verifyNoInteractions(console);
  }

  @Test
  void unknownCommandExplainsTheFormat() {
    assertFalse(run("unrelated", "unknown"));
    verify(player).sendMessage(contains("command format"));
  }

  static Stream<String> administrativeCommands() {
    return Stream.of(
        "addprestigemodifier realm type 1",
        "forcedelete realm",
        "forceupgrade realm fort",
        "forceconstruct realm fort Citadel",
        "forceregiment realm give guards 1",
        "forceleader realm Alice",
        "forcejoin realm Bob",
        "forcewithdraw realm 1",
        "refresh",
        "delbank realm",
        "startelection realm",
        "endelection realm",
        "getglobalwealth",
        "queueallnations",
        "fullregen i_love_tfmc",
        "reloadtitles",
        "reloadconfigs",
        "destroytitle crown",
        "granttitle realm crown",
        "transfersubject realm other",
        "setrelation realm other alliance",
        "settreaty realm other trade",
        "setpower realm 1",
        "setlaw realm levy low",
        "setstance merchants support",
        "usurp realm other",
        "provincecap");
  }

  @ParameterizedTest
  @MethodSource("administrativeCommands")
  void staffCommandsRefusePlayersWithoutPermission(String command) {
    assertTrue(run("faction", command.split(" ")));
    verify(player).sendMessage(contains("do not have access"));
    verify(faction, never()).setLeader(anyString());
    verify(government, never()).setPower(anyDouble());
    verifyNoInteractions(map);
  }

  @ParameterizedTest
  @ValueSource(strings = {"dummyLeader", "dummify"})
  void guildStaffCommandsRequirePermissionAndAnExistingMembership(String action) {
    assertTrue(run("guild", action));
    verify(player).sendMessage(contains("do not have access"));
    admin();
    assertTrue(run("guild", action));
    verify(player).sendMessage("§cYou are not in a guild");
    guildLeader();
    assertTrue(run("guild", action));
    if (action.equals("dummify")) verify(guild).dummify(player);
    else verify(guild).dummyLeader(player);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "forceupgrade realm fort",
        "forceconstruct realm fort Citadel",
        "queueallnations",
        "fullregen i_love_tfmc",
        "reloadtitles",
        "destroytitle crown",
        "granttitle realm crown",
        "usurp realm other",
        "provincecap",
        "claim",
        "unclaim",
        "construct fort Citadel",
        "deconstruct fort",
        "upgrade fort",
        "setcapital"
      })
  void provinceCommandsRefuseDisabledProvinceIntegration(String command) {
    admin();
    Cache.provincesEnabled = false;
    assertTrue(run("faction", command.split(" ")));
    verify(player).sendMessage(contains("disabled"));
    verifyNoInteractions(map);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "forceupgrade missing fort",
        "forceconstruct missing fort Citadel",
        "forceregiment missing give guards",
        "forceleader missing Alice",
        "forcejoin missing Alice",
        "forcewithdraw missing 1",
        "forcedelete missing",
        "delbank missing",
        "startelection missing",
        "endelection missing",
        "addprestigemodifier missing type 1"
      })
  void administrativeActionsReportMissingFaction(String command) {
    admin();
    assertTrue(run("faction", command.split(" ")));
    verify(player)
        .sendMessage(
            argThat(
                (String text) ->
                    text.contains("does not exist") || text.contains("Faction not found")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "forceupgrade",
        "forceupgrade realm",
        "forceconstruct",
        "forceconstruct realm",
        "forceregiment",
        "forceregiment realm give",
        "forceregiment realm dance guards"
      })
  void incompleteStaffConstructionAndRegimentCommandsDescribeUsage(String command) {
    admin();
    assertTrue(run("faction", command.split(" ")));
    verify(player).sendMessage(contains("Usage:"));
    verify(installations, never()).upgradeInstant(anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"construct", "forceconstruct"})
  void constructionChecksKindNameLocationAndPreservesANameWithSpaces(String action) {
    admin();
    boolean forced = action.startsWith("force");
    assertTrue(
        run(
            "faction",
            forced ? new String[] {action, "realm", "unknown"} : new String[] {action, "unknown"}));
    verify(player).sendMessage(contains("Unknown installation type"));
    assertTrue(
        run(
            "faction",
            forced ? new String[] {action, "realm", "fort"} : new String[] {action, "fort"}));
    verify(player).sendMessage(contains("Name required"));
    String[] valid =
        forced
            ? new String[] {action, "realm", "fort", "North", "Gate"}
            : new String[] {action, "fort", "North", "Gate"};
    try (MockedStatic<RestServer> rest = mockStatic(RestServer.class)) {
      rest.when(() -> RestServer.getProvince(player)).thenReturn(-2);
      assertTrue(run("faction", valid));
      verify(player).sendMessage(contains("could not resolve province"));
      rest.when(() -> RestServer.getProvince(player)).thenReturn(0);
      assertTrue(run("faction", valid));
      verify(player).sendMessage("§cThis location has no province!");
      rest.when(() -> RestServer.getProvince(player)).thenReturn(7);
      ConstructResult result = ConstructResult.ok("Construction complete");
      when(installations.constructInstant(
              eq(InstallationKind.FORT), eq("North Gate"), eq(7), anyInt(), anyInt()))
          .thenReturn(result);
      when(installations.construct(
              eq(InstallationKind.FORT), eq("North Gate"), eq(7), anyInt(), anyInt()))
          .thenReturn(result);
      assertTrue(run("faction", valid));
      if (forced)
        verify(installations)
            .constructInstant(
                InstallationKind.FORT,
                "North Gate",
                7,
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ());
      else
        verify(installations)
            .construct(
                InstallationKind.FORT,
                "North Gate",
                7,
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ());
      verify(player).sendMessage("Construction complete");
      verify(player).playSound(player, Sound.BLOCK_ANVIL_USE, 1f, 1f);
    }
  }

  @Test
  void forcedUpgradeReturnsTheDomainFailureWithoutClaimingSuccess() {
    admin();
    when(installations.upgradeInstant("fort")).thenReturn(ConstructResult.fail("Maximum level"));
    assertTrue(run("faction", "forceupgrade", "realm", "fort"));
    verify(installations).upgradeInstant("fort");
    verify(player).sendMessage("Maximum level");
  }

  @ParameterizedTest
  @CsvSource({"give,1,1,Added", "give,3,3,Added", "take,2,-2,Removed"})
  void regimentAdjustmentsUseSignedAmountsAndHumanReadableNames(
      String action, int amount, int delta, String verb) {
    admin();
    Military military = mock(Military.class);
    when(faction.getMilitary()).thenReturn(military);
    when(military.adminAdjustSlots("guards", delta)).thenReturn(new ExpandResult(true, ""));
    Regiment regiment = mock(Regiment.class);
    when(regiment.getName()).thenReturn("Royal Guards");
    when(military.getRegiment("guards")).thenReturn(regiment);
    assertTrue(
        run("faction", "forceregiment", "realm", action, "guards", Integer.toString(amount)));
    verify(military).adminAdjustSlots("guards", delta);
    verify(player)
        .sendMessage(contains(verb + " " + amount + " " + (amount == 1 ? "slot" : "slots")));
    verify(player).sendMessage(contains("Royal Guards"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad", "0", "-1", "1.5", "2147483648"})
  void regimentAmountsMustBePositiveIntegers(String amount) {
    admin();
    assertTrue(run("faction", "forceregiment", "realm", "give", "guards", amount));
    verify(player).sendMessage(contains("positive whole number"));
    verify(faction, never()).getMilitary();
  }

  @Test
  void regimentDefaultIsOneAndDomainDenialsArePreserved() {
    admin();
    Military military = mock(Military.class);
    when(faction.getMilitary()).thenReturn(military);
    when(military.adminAdjustSlots("guards", 1))
        .thenReturn(new ExpandResult(false, "Cannot grow levies"));
    assertTrue(run("faction", "forceregiment", "realm", "give", "guards"));
    verify(player).sendMessage("§cCannot grow levies");
    when(military.adminAdjustSlots("guards", 1)).thenReturn(new ExpandResult(true, ""));
    assertTrue(run("faction", "forceregiment", "realm", "give", "guards"));
    verify(player).sendMessage("§aAdded 1 slot to guards");
  }

  @Test
  void finitePrestigeAndPowerValuesUpdateTheRequestedDomainObjects() {
    admin();
    assertTrue(run("faction", "addprestigemodifier", "realm", "festival", "2.5"));
    ArgumentCaptor<Modifier> modifier = ArgumentCaptor.forClass(Modifier.class);
    verify(faction).addPersistentPrestigeModifier(modifier.capture());
    assertEquals("festival", modifier.getValue().getType());
    assertEquals(2.5, modifier.getValue().getAmount());
    assertTrue(modifier.getValue().isPersistent());
    verify(faction).updatePrestige();
    assertTrue(run("faction", "setpower", "realm", "4.25"));
    verify(government).setPower(4.25);
  }

  @ParameterizedTest
  @ValueSource(strings = {"accept", "decline"})
  void pendingRequestsAreRequiredBeforeTheyCanBeResolved(String action) {
    try (MockedStatic<RequestManager> requests = mockStatic(RequestManager.class)) {
      assertTrue(run("faction", action));
      verify(player).sendMessage(contains("no requests"));
      requests.when(() -> RequestManager.hasRequest(player)).thenReturn(true);
      assertTrue(run("faction", action));
      if (action.equals("accept")) requests.verify(() -> RequestManager.accept(player));
      else requests.verify(() -> RequestManager.decline(player));
    }
  }

  @Test
  void missingFactionJoinReportsTheMissingIdWithoutConsumingAnInvite() {
    assertTrue(run("faction", "join", "missing"));
    verify(player).sendMessage(contains("No faction"));
    verify(faction, never()).consumeInvite(anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"setrulertitle", "setrulingsystem", "setculture", "setreligion"})
  void leaderIdentityCommandsConvertUnderscoresAndRefuseOutsiders(String action) {
    assertTrue(run("faction", action, "High_King"));
    switch (action) {
      case "setrulertitle" -> verify(faction).setRulerTitle("High King");
      case "setrulingsystem" -> verify(faction).setGovernment("High King");
      case "setculture" -> verify(faction).setCulture("High King");
      case "setreligion" -> verify(faction).setReligion("High King");
    }
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(null);
    assertTrue(run("faction", action, "Other"));
    verify(player).sendMessage(contains("must be the leader"));
  }

  private void admin() {
    when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "0.005"})
  void forcedWithdrawalRejectsInvalidMonetaryAmounts(String amount) {
    admin();
    Bank bank = mock(Bank.class);
    when(faction.getBank()).thenReturn(bank);
    when(bank.getWealth()).thenReturn(100.0);
    try (MockedStatic<DenarEconomy> economy = mockStatic(DenarEconomy.class)) {
      var money = mock(MoneyManager.class);
      economy.when(DenarEconomy::getMoneyManager).thenReturn(money);
      when(money.amountToItems(anyDouble())).thenReturn(List.of());
      run("faction", "forcewithdraw", "realm", amount);
      verify(bank, never()).withdraw(anyDouble());
      verify(money, never()).amountToItems(anyDouble());
      verify(player)
          .sendMessage(
              argThat(
                  (String message) -> message.contains("amount") || message.contains("Amount")));
    }
  }

  @Test
  void forcedWithdrawalReportsAMissingBank() {
    admin();
    assertTrue(run("faction", "forcewithdraw", "realm", "10"));
    verify(player).sendMessage(contains("bank"));
  }

  @Test
  void forcedWithdrawalDropsPhysicalCoinsThatCannotFitInTheInventoryExactlyOnce() {
    admin();
    Bank bank = mock(Bank.class);
    when(faction.getBank()).thenReturn(bank);
    when(bank.getWealth()).thenReturn(100.0);
    var coin = new ItemStack(Material.GOLD_NUGGET, 10);
    when(player.getWorld()).thenReturn(gui.world);
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
    }
    try (MockedStatic<DenarEconomy> economy = mockStatic(DenarEconomy.class)) {
      var money = mock(MoneyManager.class);
      economy.when(DenarEconomy::getMoneyManager).thenReturn(money);
      when(money.amountToItems(10.0)).thenReturn(List.of(coin));
      assertTrue(run("faction", "forcewithdraw", "realm", "10"));
      verify(bank).withdraw(10.0);
      verify(player.getInventory()).addItem(coin);
      ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
      verify(gui.world).dropItemNaturally(eq(player.getLocation()), dropped.capture());
      assertEquals(Material.GOLD_NUGGET, dropped.getValue().getType());
      assertEquals(10, dropped.getValue().getAmount());
      for (ItemStack held : player.getInventory().getContents()) {
        assertEquals(Material.STONE, held.getType());
        assertEquals(64, held.getAmount());
      }
    }
  }

  @ParameterizedTest
  @CsvSource({"faction,deposit", "faction,withdraw", "guild,deposit", "guild,withdraw"})
  void bankTransfersValidateMembershipLocationAmountAndFunds(String name, String action) {
    boolean deposit = action.equals("deposit");
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(null);
    assertTrue(run(name, action, "10"));
    verify(player).sendMessage(contains("need to be"));
    member();
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(faction);
    guildLeader();
    assertFalse(run(name, action, "10"));
    verify(player).sendMessage(contains("not established a bank"));
    Bank bank = mock(Bank.class);
    when(faction.getBank()).thenReturn(bank);
    when(guild.getBank()).thenReturn(bank);
    Chunk here = mock(Chunk.class);
    Location location = mock(Location.class);
    when(location.getChunk()).thenReturn(here);
    when(player.getLocation()).thenReturn(location);
    assertFalse(run(name, action, "10"));
    verify(player).sendMessage(contains("must be at your"));
    when(bank.getChunk()).thenReturn(here);
    assertFalse(run(name, action, "bad"));
    verify(player).sendMessage(contains("whole cents"));
    try (MockedStatic<DenarEconomy> economy = mockStatic(DenarEconomy.class)) {
      var players = mock(PlayerManager.class);
      var data = mock(PlayerData.class);
      var pouch = mock(Account.class);
      economy.when(DenarEconomy::getPlayerManager).thenReturn(players);
      when(players.get(player)).thenReturn(data);
      when(data.getPouch()).thenReturn(pouch);
      assertFalse(run(name, action, "10"));
      verify(player).sendMessage(contains("Not enough funds"));
      when(bank.getWealth()).thenReturn(100.0);
      when(pouch.getBal()).thenReturn(100.0);
      assertTrue(run(name, action, "10.25"));
      verify(pouch).change(deposit ? -10.25 : 10.25);
      if (deposit) {
        verify(bank).deposit(10.25);
        verify(guild.getLedger().getHistory()).addDeposit("Leader", 10.25);
      } else verify(bank).withdraw(10.25);
      verify(player).sendMessage(contains("Bank Report"));
      verify(player).playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void bankPlacementHonoursDomainPlacementRulesAndMovesOrCreatesTheBank(String name) {
    member();
    guildLeader();
    Chunk chunk = mock(Chunk.class);
    Location location = mock(Location.class);
    when(location.getChunk()).thenReturn(chunk);
    when(player.getLocation()).thenReturn(location);
    try (MockedStatic<BankPlacementValidator> placement =
        mockStatic(BankPlacementValidator.class)) {
      placement
          .when(() -> BankPlacementValidator.failureReasonForFaction(faction, location))
          .thenReturn("Outside your capital");
      placement
          .when(() -> BankPlacementValidator.failureReasonForGuild(guild, location))
          .thenReturn("Outside your capital");
      assertTrue(run(name, "setbank"));
      verify(player).sendMessage("Outside your capital");
      placement.reset();
      try (MockedConstruction<Bank> banks = mockConstruction(Bank.class)) {
        assertTrue(run(name, "setbank"));
        assertEquals(1, banks.constructed().size());
        if (name.equals("faction")) verify(faction).setBank(banks.constructed().getFirst());
        else verify(guild).setBank(banks.constructed().getFirst());
      }
      Bank existing = mock(Bank.class);
      when(faction.getBank()).thenReturn(existing);
      when(guild.getBank()).thenReturn(existing);
      assertTrue(run(name, "setbank"));
      verify(existing).setChunk(chunk);
      verify(player).sendMessage("§aBank moved");
    }
  }

  @Test
  void validForcedWithdrawalPaysPhysicalCoinsAndNotifiesOnlineMembers() {
    admin();
    Bank bank = mock(Bank.class);
    when(faction.getBank()).thenReturn(bank);
    when(bank.getWealth()).thenReturn(100.0);
    Player alice = gui.player("Alice");
    when(alice.isOnline()).thenReturn(true);
    when(Bukkit.getPlayer("Alice")).thenReturn(alice);
    try (MockedStatic<DenarEconomy> economy = mockStatic(DenarEconomy.class)) {
      var money = mock(MoneyManager.class);
      economy.when(DenarEconomy::getMoneyManager).thenReturn(money);
      var coin = new ItemStack(Material.GOLD_NUGGET, 10);
      when(money.amountToItems(10.0)).thenReturn(List.of(coin));
      assertTrue(run("faction", "forcewithdraw", "realm", "10"));
      verify(bank).withdraw(10.0);
      verify(player.getInventory()).addItem(coin);
      verify(alice).sendMessage(contains("withdrawn from the faction by admins"));
      assertTrue(run("faction", "forcewithdraw", "realm", "101"));
      verify(player).sendMessage(contains("enough wealth"));
      assertFalse(run("faction", "forcewithdraw", "realm", "-1"));
      verify(bank, times(1)).withdraw(anyDouble());
    }
  }

  @Test
  void administrativeMaintenanceRepairsMembershipAndDeletesBanksWithoutOtherChanges() {
    admin();
    List<String> members = new ArrayList<>(List.of("Alice"));
    when(faction.getMembers()).thenReturn(members);
    FactionManager.factions.add(faction);
    assertTrue(run("faction", "refresh"));
    assertEquals(List.of("Alice", "Leader"), members);
    assertTrue(run("faction", "refresh"));
    assertEquals(2, members.size());
    Bank bank = mock(Bank.class);
    when(faction.getBank()).thenReturn(bank);
    BankManager.banks.add(bank);
    assertTrue(run("faction", "delbank", "realm"));
    assertTrue(BankManager.banks.isEmpty());
    verify(faction).setBank(null);
    assertTrue(run("faction", "provincecap"));
    verify(faction).provinceCap();
  }

  @Test
  void mapRegenerationAndReloadCommandsUseTheirDedicatedServices() {
    admin();
    assertTrue(run("faction", "queueallnations"));
    verify(map).queueAllNations();
    assertTrue(run("faction", "fullregen", "wrong"));
    verify(map, never()).fullRegen();
    verify(player).sendMessage(contains("incorrect passcode"));
    assertTrue(run("faction", "fullregen", "i_love_tfmc"));
    verify(map).fullRegen();
    try (MockedStatic<SimpleFactions> plugin = mockStatic(SimpleFactions.class)) {
      assertTrue(run("faction", "reloadtitles"));
      plugin.verify(SimpleFactions::reloadTitles);
      assertTrue(run("faction", "reloadconfigs"));
      plugin.verify(SimpleFactions::reloadConfigs);
    }
  }

  @Test
  void globalWealthReportCombinesAndLabelsTheSeparateBalances() {
    admin();
    factions.when(FactionManager::getGlobalWealth).thenReturn(100.0);
    factions.when(FactionManager::getPouchWealth).thenReturn(20.0);
    factions.when(FactionManager::getBankWealth).thenReturn(5.0);
    factions.when(FactionManager::getGlobalNodeWealth).thenReturn(25.0);
    assertTrue(run("faction", "getglobalwealth"));
    verify(player).sendMessage("§eGlobal Wealth: §6125.0d");
    verify(player).sendMessage("§aNode Percentage: §f20% §aof global wealth");
  }

  @ParameterizedTest
  @ValueSource(strings = {"startelection", "endelection"})
  void electionCommandsRespectStateAndNotifyOnlyFactionMembers(String action) {
    admin();
    Election election = mock(Election.class);
    when(government.getElection()).thenReturn(election);
    Player alice = gui.player("Alice"), outsider = gui.player("Visitor");
    online(alice, outsider);
    if (action.equals("startelection")) {
      assertTrue(run("faction", action, "realm"));
      verify(player).sendMessage(contains("does not have elections"));
      when(government.hasElections()).thenReturn(true);
      when(election.isActive()).thenReturn(true);
      assertTrue(run("faction", action, "realm"));
      verify(player).sendMessage(contains("already active"));
      when(election.isActive()).thenReturn(false);
      assertTrue(run("faction", action, "realm"));
      verify(election).start();
      verify(alice).sendMessage(contains("Election Started"));
    } else {
      assertTrue(run("faction", action, "realm"));
      verify(player).sendMessage(contains("No active election"));
      when(election.isActive()).thenReturn(true);
      assertTrue(run("faction", action, "realm"));
      verify(election).end();
      verify(alice).sendMessage(contains("Election Ended"));
    }
    verify(outsider, never()).sendMessage(anyString());
  }

  private void member() {
    factions.when(() -> FactionManager.getByMember("Leader")).thenReturn(faction);
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void renamingRequiresLeadershipAndAcceptsOnlyConfirmedDisplayNames(String name) {
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(null);
    assertTrue(run(name, "rename", "New_Realm"));
    verify(player).sendMessage(contains("must be the leader"));
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(faction);
    guildLeader();
    NameOperation operation =
        name.equals("faction") ? NameOperation.FACTION_RENAME : NameOperation.GUILD_RENAME;
    try (MockedStatic<DisplayNameGate> names = mockStatic(DisplayNameGate.class)) {
      names
          .when(() -> DisplayNameGate.check(player, operation, "New_Realm"))
          .thenReturn(DisplayNameGate.Result.NEEDS_CONFIRM);
      assertTrue(run(name, "rename", "New_Realm"));
      verify(faction, never()).setName(anyString());
      verify(guild, never()).setName(anyString());
      names
          .when(() -> DisplayNameGate.check(player, operation, "New_Realm"))
          .thenReturn(DisplayNameGate.Result.OK);
      assertTrue(run(name, "rename", "New_Realm"));
      if (name.equals("faction")) {
        verify(faction)
            .setName(argThat((String value) -> "New Realm".equals(ChatColor.stripColor(value))));
        verify(map).enqueue(eq("nation"), nullable(String.class));
      } else
        verify(guild)
            .setName(argThat((String value) -> "New Realm".equals(ChatColor.stripColor(value))));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void bannerCommandsCopyOneBannerWithoutChangingTheHeldStack(String name) {
    guildLeader();
    var stone = new ItemStack(Material.STONE);
    when(player.getInventory().getItemInMainHand()).thenReturn(stone);
    assertTrue(run(name, "setbanner"));
    verify(player).sendMessage(contains("holding a banner"));
    var held = new ItemStack(Material.WHITE_BANNER, 12);
    when(player.getInventory().getItemInMainHand()).thenReturn(held);
    assertTrue(run(name, "setbanner"));
    ArgumentCaptor<ItemStack> banner = ArgumentCaptor.forClass(ItemStack.class);
    if (name.equals("faction")) verify(faction).setBanner(banner.capture());
    else verify(guild).setBanner(banner.capture());
    assertNotSame(held, banner.getValue());
    assertEquals(1, banner.getValue().getAmount());
    assertEquals(12, held.getAmount());
  }

  @ParameterizedTest
  @CsvSource({"setbanner,unused", "setcolour,'1,2,3'"})
  void baseGuildAppearanceIsChangedThroughTheFactionCommands(String action, String value) {
    guildLeader();
    when(guild.isBase()).thenReturn(true);
    assertTrue(
        run(
            "guild",
            action.equals("setbanner") ? new String[] {action} : new String[] {action, value}));
    verify(player).sendMessage(contains("base guild"));
    verify(guild, never()).setRGB(anyString());
    verify(guild, never()).setBanner(any());
  }

  @ParameterizedTest
  @CsvSource({
    "faction,1,Invalid format",
    "guild,1,Invalid format",
    "faction,2,must be numbers",
    "guild,2,must be numbers",
    "faction,3,between 0 and 255",
    "guild,3,between 0 and 255"
  })
  void invalidColoursHaveSpecificValidationMessages(String name, int code, String message) {
    guildLeader();
    factions.when(() -> FactionManager.validateRGB("invalid")).thenReturn(code);
    assertTrue(run(name, "setcolour", "invalid"));
    verify(player).sendMessage(contains(message));
    verify(guild, never()).setRGB(anyString());
    verify(faction, never()).setRGB(anyString());
  }

  @Test
  void validColoursQueueBothFactionMapStatesAndRespectGuildColourUniqueness() {
    guildLeader();
    when(faction.getRGB()).thenReturn("1,1,1", "2,2,2");
    assertTrue(run("faction", "setcolour", "2,2,2"));
    verify(faction).setRGB("2,2,2");
    verify(map).enqueue("nation", "1,1,1");
    verify(map).enqueue("nation", "2,2,2");
    try (MockedStatic<RandomRGB> colours = mockStatic(RandomRGB.class)) {
      assertTrue(run("guild", "setcolour", "2,2,2"));
      verify(player).sendMessage(contains("already used"));
      colours.when(() -> RandomRGB.isFree("2,2,2")).thenReturn(true);
      assertTrue(run("guild", "setcolour", "2,2,2"));
      verify(guild).setRGB("2,2,2");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void leavingRequiresMembershipAndNonLeadership(String name) {
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(null);
    assertTrue(run(name, "leave"));
    verify(player).sendMessage(contains("not in a"));
    member();
    guildLeader();
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(faction);
    assertTrue(run(name, "leave"));
    verify(player).sendMessage(contains("cannot abandon"));
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(null);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(null);
    if (name.equals("faction")) {
      when(faction.isInGuild("Leader")).thenReturn(true);
      assertTrue(run(name, "leave"));
      verify(player).sendMessage(contains("use /guild leave"));
      when(faction.isInGuild("Leader")).thenReturn(false);
    }
    assertTrue(run(name, "leave"));
    if (name.equals("faction")) {
      verify(faction).forceRemoveMember("Leader");
      verify(faction).updatePrestige();
    } else verify(guild).kick("Leader");
    verify(player).sendMessage(contains("Left "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void joiningConsumesAnInvitationBeforeAddingAndNotifyingMembers(String name) {
    String id = name.equals("faction") ? "realm" : "merchants";
    factions.when(() -> FactionManager.canJoinGuild(player)).thenReturn(true);
    assertTrue(run(name, "join", id));
    verify(player).sendMessage(contains("need to be invited"));
    verify(faction, never()).addMember(anyString());
    verify(guild, never()).addMember(anyString());
    when(faction.consumeInvite("Leader")).thenReturn(true);
    when(guild.consumeInvite("Leader")).thenReturn(true);
    Guild previous = mock(Guild.class);
    factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(previous);
    Player alice = gui.player("Alice"), outsider = gui.player("Visitor");
    online(alice, outsider);
    assertTrue(run(name, "join", id));
    if (name.equals("faction")) verify(faction).addMember("Leader");
    else {
      verify(previous).kick("Leader");
      verify(guild).addMember("Leader");
    }
    verify(faction).updatePrestige();
    verify(alice).sendMessage(contains("joined the faction"));
    verify(outsider, never()).sendMessage(anyString());
  }

  @Test
  void joiningRefusesExistingMembershipOrGuildLeadership() {
    member();
    assertTrue(run("faction", "join", "realm"));
    verify(player).sendMessage(contains("Already in a faction"));
    guildLeader();
    assertTrue(run("guild", "join", "merchants"));
    verify(player).sendMessage(contains("leader of a guild"));
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(null);
    assertTrue(run("guild", "join", "merchants"));
    verify(player).sendMessage(contains("already in a guild"));
    verify(faction, never()).consumeInvite(anyString());
    verify(guild, never()).consumeInvite(anyString());
  }

  @Test
  void factionKickProtectsLeadersGuildMembersAndNonMembers() {
    assertTrue(run("faction", "kick", "Alice"));
    verify(player).sendMessage(contains("need to have a faction"));
    member();
    when(faction.getLeader()).thenReturn("Ruler");
    assertTrue(run("faction", "kick", "Alice"));
    verify(player).sendMessage(contains("Only the leader"));
    when(faction.getLeader()).thenReturn("Leader");
    assertTrue(run("faction", "kick", "Leader"));
    verify(player).sendMessage(contains("cannot dismiss the leader"));
    assertTrue(run("faction", "kick", "Visitor"));
    verify(player).sendMessage(contains("not a member"));
    when(faction.isInGuild("Alice")).thenReturn(true);
    assertTrue(run("faction", "kick", "Alice"));
    verify(player).sendMessage(contains("member of a guild"));
    verify(faction, never()).forceRemoveMember(anyString());
    when(faction.isInGuild("Alice")).thenReturn(false);
    Player alice = gui.player("Alice"), visitor = gui.player("Visitor");
    online(alice, visitor);
    assertTrue(run("faction", "kick", "Alice"));
    verify(faction).forceRemoveMember("Alice");
    verify(alice).sendMessage(contains("kicked you"));
    verify(visitor, never()).sendMessage(anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"setleader", "forceleader"})
  void factionLeaderChangesCheckEligibilityAndNotifyFactionMembers(String action) {
    if (action.equals("forceleader")) admin();
    else {
      assertTrue(run("faction", action, "Alice"));
      verify(player).sendMessage(contains("need to have a faction"));
      member();
      when(faction.getLeader()).thenReturn("Ruler");
      assertTrue(run("faction", action, "Alice"));
      verify(player).sendMessage(contains("Only the leader"));
      when(faction.getLeader()).thenReturn("Leader");
      when(government.hasLeaderElections()).thenReturn(true);
      assertTrue(run("faction", action, "Alice"));
      verify(player).sendMessage(contains("democracy"));
      when(government.hasLeaderElections()).thenReturn(false);
    }
    String[] leader =
        action.equals("forceleader")
            ? new String[] {action, "realm", "Leader"}
            : new String[] {action, "Leader"};
    assertTrue(run("faction", leader));
    verify(player).sendMessage(contains("already the leader"));
    String[] missing =
        action.equals("forceleader")
            ? new String[] {action, "realm", "Visitor"}
            : new String[] {action, "Visitor"};
    assertTrue(run("faction", missing));
    verify(player).sendMessage(contains("not in the faction"));
    String[] eligible =
        action.equals("forceleader")
            ? new String[] {action, "realm", "Alice"}
            : new String[] {action, "Alice"};
    assertTrue(run("faction", eligible));
    verify(player).sendMessage(contains("not eligible"));
    when(faction.canBecomeLeader("Alice")).thenReturn(true);
    Player alice = gui.player("Alice"), visitor = gui.player("Visitor");
    online(alice, visitor);
    assertTrue(run("faction", eligible));
    verify(faction).setLeader("Alice");
    verify(alice).sendMessage(contains("Alice is the new faction leader"));
    verify(visitor, never()).sendMessage(anyString());
  }

  @Test
  void guildLeaderChangesCheckMembershipAndNotifyOnlyGuildMembers() {
    assertTrue(run("guild", "setleader", "Alice"));
    verify(player).sendMessage(contains("need to have a guild"));
    guildLeader();
    when(guild.getLeader()).thenReturn("Other");
    assertTrue(run("guild", "setleader", "Alice"));
    verify(player).sendMessage(contains("Only the leader"));
    when(guild.getLeader()).thenReturn("Leader");
    assertTrue(run("guild", "setleader", "Visitor"));
    verify(player).sendMessage(contains("not in the guild"));
    when(guild.isMember("Leader")).thenReturn(true);
    assertTrue(run("guild", "setleader", "Leader"));
    verify(player).sendMessage(contains("already the leader"));
    when(guild.isMember("Alice")).thenReturn(true);
    Player alice = gui.player("Alice"), visitor = gui.player("Visitor");
    online(alice, visitor);
    assertTrue(run("guild", "setleader", "Alice"));
    verify(guild).setLeader("Alice");
    verify(alice).sendMessage(contains("new guild leader"));
    verify(visitor, never()).sendMessage(anyString());
  }

  @Test
  void administrativeJoinRefusesExistingMembershipAndNotifiesAfterAdding() {
    admin();
    assertTrue(run("faction", "forcejoin", "realm", "Alice"));
    verify(player).sendMessage(contains("already in the faction"));
    Faction other = mock(Faction.class);
    factions.when(() -> FactionManager.getByMember("Bob")).thenReturn(other);
    assertTrue(run("faction", "forcejoin", "realm", "Bob"));
    verify(player).sendMessage(contains("already in a faction"));
    factions.when(() -> FactionManager.getByMember("Bob")).thenReturn(null);
    Player alice = gui.player("Alice"), visitor = gui.player("Visitor");
    online(alice, visitor);
    assertTrue(run("faction", "forcejoin", "realm", "Bob"));
    verify(faction).addMember("Bob");
    verify(alice).sendMessage(contains("Bob joined"));
    verify(visitor, never()).sendMessage(anyString());
  }

  private void guildLeader() {
    factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(guild);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
  }

  @Test
  void titleGrantsAndDestructionValidateExistenceAndOwnership() {
    admin();
    Title title = mock(Title.class);
    when(title.getId()).thenReturn("crown");
    when(title.getName()).thenReturn("The Crown");
    try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
        MockedStatic<TitleManager> ownership = mockStatic(TitleManager.class)) {
      assertFalse(run("faction", "granttitle", "missing", "crown"));
      verify(player).sendMessage("§cNo faction by that id");
      assertFalse(run("faction", "granttitle", "realm", "crown"));
      assertFalse(run("faction", "destroytitle", "crown"));
      verify(player, times(2)).sendMessage("§cNo title by that id");
      titles.when(() -> TitleLoader.getById("crown")).thenReturn(title);
      assertFalse(run("faction", "destroytitle", "crown"));
      verify(player).sendMessage("§cNo faction owns that title");
      assertTrue(run("faction", "granttitle", "realm", "crown"));
      verify(faction).addTitle(title);
      ownership.when(() -> TitleManager.getOwner(title)).thenReturn(faction);
      assertFalse(run("faction", "granttitle", "realm", "crown"));
      verify(player).sendMessage(contains("already owns that title"));
      assertTrue(run("faction", "destroytitle", "crown"));
      verify(faction).removeTitle(title);
    }
  }

  @Test
  void subjectTransferRejectsMissingFactionsLoopsAndInvalidVassalOwnership() {
    admin();
    Faction other = mock(Faction.class);
    when(other.getId()).thenReturn("other");
    when(other.getName()).thenReturn("Other");
    try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
      assertFalse(run("faction", "transfersubject", "missing", "other"));
      verify(player).sendMessage("§cNo faction by the id missing");
      assertFalse(run("faction", "transfersubject", "realm", "other"));
      verify(player).sendMessage("§cNo faction by the id other");
      factions.when(() -> FactionManager.getByString("other")).thenReturn(other);
      assertFalse(run("faction", "transfersubject", "realm", "other"));
      verify(player).sendMessage(contains("not a subject"));
      relations.when(() -> RelationManager.getOverlord(faction)).thenReturn("other");
      assertFalse(run("faction", "transfersubject", "realm", "other"));
      verify(player).sendMessage(contains("already a subject"));
      relations.when(() -> RelationManager.getOverlord(faction)).thenReturn("former");
      relations.when(() -> RelationManager.isOnOverlordPath(other, faction)).thenReturn(true);
      assertFalse(run("faction", "transfersubject", "realm", "other"));
      verify(player).sendMessage(contains("cause a loop"));
      relations.when(() -> RelationManager.isOnOverlordPath(other, faction)).thenReturn(false);
      assertFalse(run("faction", "transfersubject", "realm", "other"));
      verify(player).sendMessage(contains("cannot have vassals"));
      when(other.canHaveVassals()).thenReturn(true);
      assertTrue(run("faction", "transfersubject", "realm", "other"));
      relations.verify(() -> RelationManager.transferSubject(faction, other));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"setrelation", "settreaty"})
  void diplomaticCommandsDistinguishRelationsTreatiesAndTrade(String action) {
    admin();
    Faction other = mock(Faction.class);
    RelationType type = mock(RelationType.class);
    when(type.getName()).thenReturn("Agreement");
    try (MockedStatic<RelationLoader> types = mockStatic(RelationLoader.class);
        MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
      assertFalse(run("faction", action, "missing", "other", "agreement"));
      verify(player).sendMessage("§cNo faction by the id missing");
      assertFalse(run("faction", action, "realm", "other", "agreement"));
      verify(player).sendMessage("§cNo faction by the id other");
      factions.when(() -> FactionManager.getByString("other")).thenReturn(other);
      assertFalse(run("faction", action, "realm", "other", "agreement"));
      verify(player).sendMessage(contains("with the id agreement"));
      types.when(() -> RelationLoader.getType("agreement")).thenReturn(type);
      if (action.equals("setrelation")) {
        when(type.isTradeAgreement()).thenReturn(true);
        assertFalse(run("faction", action, "realm", "other", "agreement"));
        verify(player).sendMessage(contains("Use /faction settreaty"));
        when(type.isTradeAgreement()).thenReturn(false);
        assertTrue(run("faction", action, "realm", "other", "agreement"));
        relations.verify(() -> RelationManager.setRelation(player, type, other, faction, false));
      } else {
        assertFalse(run("faction", action, "realm", "other", "agreement"));
        when(type.isTreaty()).thenReturn(true);
        assertTrue(run("faction", action, "realm", "other", "agreement"));
        relations.verify(() -> RelationManager.setTreatyRelationForced(type, other, faction));
        when(type.isTreaty()).thenReturn(false);
        when(type.isTradeAgreement()).thenReturn(true);
        assertTrue(run("faction", action, "realm", "other", "agreement"));
        relations.verify(() -> RelationManager.setTradeRelationForced(type, other, faction));
      }
    }
  }

  @Test
  void lawChangeValidatesTheTemplateAndFactionGroupBeforeApplying() {
    admin();
    var handler = mock(LawHandler.class);
    when(faction.getLawHandler()).thenReturn(handler);
    var template = mock(LawGroup.class);
    when(template.getId()).thenReturn("levy");
    var chosen = mock(Law.class);
    when(chosen.getId()).thenReturn("low");
    var owned = mock(LawGroup.class);
    try (MockedStatic<LawLoader> laws = mockStatic(LawLoader.class)) {
      assertFalse(run("faction", "setlaw", "missing", "levy", "low"));
      verify(player).sendMessage(contains("No faction"));
      assertFalse(run("faction", "setlaw", "realm", "levy", "low"));
      verify(player).sendMessage(contains("No law group"));
      laws.when(() -> LawLoader.getByString("levy")).thenReturn(template);
      assertFalse(run("faction", "setlaw", "realm", "levy", "low"));
      verify(player).sendMessage(contains("No law low"));
      when(template.getLaw("low")).thenReturn(chosen);
      assertFalse(run("faction", "setlaw", "realm", "levy", "low"));
      verify(player).sendMessage(contains("has no group"));
      when(handler.getGroup("levy")).thenReturn(owned);
      assertTrue(run("faction", "setlaw", "realm", "levy", "low"));
      verify(faction).applyLaw(chosen, owned);
    }
  }

  @Test
  void staffStanceUpdatesResolveFactionMainGuildsAndPersistOnlyValidStances() {
    admin();
    assertFalse(run("faction", "setstance", "missing", "support"));
    verify(player).sendMessage(contains("No guild or faction"));
    assertFalse(run("faction", "setstance", "merchants", "unknown"));
    verify(player).sendMessage(contains("oppose, neutral, or support"));
    try (MockedConstruction<Database> databases = mockConstruction(Database.class)) {
      assertTrue(run("faction", "setstance", "realm", "SUPPORT"));
      verify(guild).setStance(Stance.SUPPORT);
      assertEquals(1, databases.constructed().size());
      verify(databases.constructed().getFirst()).saveFaction(faction);
    }
  }

  @Test
  void usurpChecksBothFactionsAndReportsSuccessfulTransfers() {
    admin();
    assertFalse(run("faction", "usurp", "missing", "realm"));
    verify(player).sendMessage(contains("No faction by the id missing"));
    assertFalse(run("faction", "usurp", "realm", "other"));
    verify(player).sendMessage(contains("No faction by the id other"));
    Faction other = mock(Faction.class);
    factions.when(() -> FactionManager.getByString("other")).thenReturn(other);
    assertTrue(run("faction", "usurp", "realm", "other"));
    Title title = mock(Title.class);
    when(title.getName()).thenReturn("Crown");
    factions.when(() -> FactionManager.usurp(player, faction, other)).thenReturn(title);
    assertTrue(run("faction", "usurp", "realm", "other"));
    verify(player).sendMessage(contains("usurped Crown"));
  }

  @Test
  void commandDispatchDelegatesConsoleCapableAndEspionageCommandsWithTheirArguments() {
    CommandSender console = mock(CommandSender.class);
    try (MockedStatic<TradeGraphCommand> trade = mockStatic(TradeGraphCommand.class);
        MockedStatic<EspionageCommands> espionage = mockStatic(EspionageCommands.class);
        MockedStatic<TitleAdminCommand> titles = mockStatic(TitleAdminCommand.class)) {
      String[] graph = {"tradegraph", "status"};
      trade.when(() -> TradeGraphCommand.handle(console, graph)).thenReturn(true);
      assertTrue(run(console, "faction", graph));
      trade.verify(() -> TradeGraphCommand.handle(console, graph));
      String[] reload = {"reloadespionage"};
      espionage.when(() -> EspionageCommands.reload(console, reload)).thenReturn(true);
      assertTrue(run(console, "faction", reload));
      espionage.verify(() -> EspionageCommands.reload(console, reload));
      String[] edit = {TitleAdminCommand.SUBCOMMAND, "list"};
      titles.when(() -> TitleAdminCommand.handle(console, edit)).thenReturn(true);
      assertTrue(run(console, "faction", edit));
      titles.verify(() -> TitleAdminCommand.handle(console, edit));
      String[] positions = {"positions"};
      espionage.when(() -> EspionageCommands.matches("positions")).thenReturn(true);
      espionage.when(() -> EspionageCommands.handle(player, positions)).thenReturn(true);
      assertTrue(run("faction", positions));
      espionage.verify(() -> EspionageCommands.handle(player, positions));
    }
  }

  private void online(Player... players) {
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(players));
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void menuCommandsRouteToThePlayersOwnDomainAndListToTheOverview(String name) {
    assertTrue(run(name, "menu"));
    verify(player).sendMessage(contains("not in a"));
    member();
    guildLeader();
    try (MockedConstruction<InventoryManager> menus = mockConstruction(InventoryManager.class)) {
      assertTrue(run(name, "menu"));
      assertTrue(run(name, "list"));
      assertEquals(2, menus.constructed().size());
      if (name.equals("faction")) {
        verify(menus.constructed().get(0)).factionView(player, faction);
        verify(menus.constructed().get(1)).factionList(player);
      } else {
        verify(menus.constructed().get(0)).guildView(player, guild);
        verify(menus.constructed().get(1)).guildList(player);
      }
    }
  }

  @Test
  void guildCreationRejectsConflictingMembershipOrAnUnconfirmedName() {
    assertTrue(run("guild", "create", "New_Guild"));
    verify(player).sendMessage(contains("need to be in a faction"));
    member();
    when(faction.isInGuild("Leader")).thenReturn(true);
    assertTrue(run("guild", "create", "New_Guild"));
    verify(player).sendMessage(contains("already in a guild"));
    when(faction.isInGuild("Leader")).thenReturn(false);
    assertTrue(run("guild", "create", "New_Guild"));
    verify(player).sendMessage(contains("leader of the faction"));
    when(faction.getLeader()).thenReturn("Other");
    factions.when(() -> FactionManager.guildExists("New_Guild")).thenReturn(true);
    assertTrue(run("guild", "create", "New_Guild"));
    verify(player).sendMessage(contains("guild already exists"));
    factions.when(() -> FactionManager.guildExists("New_Guild")).thenReturn(false);
    try (MockedStatic<DisplayNameGate> names = mockStatic(DisplayNameGate.class);
        MockedConstruction<Guild> created = mockConstruction(Guild.class)) {
      names
          .when(() -> DisplayNameGate.check(player, NameOperation.GUILD_CREATE, "New_Guild"))
          .thenReturn(DisplayNameGate.Result.NEEDS_CONFIRM);
      assertTrue(run("guild", "create", "New_Guild"));
      assertTrue(created.constructed().isEmpty());
      names
          .when(() -> DisplayNameGate.check(player, NameOperation.GUILD_CREATE, "New_Guild"))
          .thenReturn(DisplayNameGate.Result.OK);
      assertTrue(run("guild", "create", "New_Guild"));
      assertEquals(1, created.constructed().size());
      verify(guilds).addGuild(created.constructed().getFirst());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void factionCreationHonoursNameConfirmationAndTheCreateEvent(boolean cancelled) {
    member();
    assertTrue(run("faction", "create", "New_Realm"));
    verify(player).sendMessage(contains("already have a faction"));
    factions.when(() -> FactionManager.getByMember("Leader")).thenReturn(null);
    PluginManager plugins = mock(PluginManager.class);
    when(Bukkit.getPluginManager()).thenReturn(plugins);
    doAnswer(
            call -> {
              ((FactionCreateEvent) call.getArgument(0)).setCancelled(cancelled);
              return null;
            })
        .when(plugins)
        .callEvent(any());
    try (MockedStatic<DisplayNameGate> names = mockStatic(DisplayNameGate.class);
        MockedConstruction<Faction> created = mockConstruction(Faction.class)) {
      names
          .when(() -> DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "New_Realm"))
          .thenReturn(DisplayNameGate.Result.NEEDS_CONFIRM);
      assertTrue(run("faction", "create", "New_Realm"));
      assertTrue(created.constructed().isEmpty());
      names
          .when(() -> DisplayNameGate.check(player, NameOperation.FACTION_CREATE, "New_Realm"))
          .thenReturn(DisplayNameGate.Result.OK);
      assertTrue(run("faction", "create", "New_Realm"));
      assertEquals(1, created.constructed().size());
      Faction result = created.constructed().getFirst();
      factions.verify(() -> FactionManager.addFaction(result), cancelled ? never() : times(1));
      ArgumentCaptor<Event> event = ArgumentCaptor.forClass(Event.class);
      verify(plugins).callEvent(event.capture());
      assertSame(result, ((FactionCreateEvent) event.getValue()).getFaction());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"delete", "forcedelete"})
  void factionDeletionHonoursCancellationAndRemovesTheBankOnlyAfterApproval(String action) {
    admin();
    Bank bank = mock(Bank.class);
    when(faction.getBank()).thenReturn(bank);
    BankManager.banks.add(bank);
    Faction other = mock(Faction.class);
    HashMap<String, Relation> relations = new HashMap<>();
    relations.put("realm", mock(Relation.class));
    when(other.getRelations()).thenReturn(relations);
    FactionManager.factions.add(other);
    PluginManager plugins = mock(PluginManager.class);
    when(Bukkit.getPluginManager()).thenReturn(plugins);
    doAnswer(
            call -> {
              ((FactionDeleteEvent) call.getArgument(0)).setCancelled(true);
              return null;
            })
        .when(plugins)
        .callEvent(any());
    assertTrue(run("faction", action, "realm"));
    factions.verify(() -> FactionManager.deleteFaction(faction), never());
    assertEquals(List.of(bank), BankManager.banks);
    assertTrue(relations.containsKey("realm"));
    doNothing().when(plugins).callEvent(any());
    assertTrue(run("faction", action, "realm"));
    factions.verify(() -> FactionManager.deleteFaction(faction));
    assertTrue(BankManager.banks.isEmpty());
    if (action.equals("delete")) assertFalse(relations.containsKey("realm"));
    verify(player).sendMessage(contains("deleted!"));
  }

  @Test
  void factionDeletionRequiresMembershipLeadershipAndSettledFinances() {
    assertTrue(run("faction", "delete", "missing"));
    verify(player).sendMessage(contains("does not exist"));
    when(faction.getMembers()).thenReturn(List.of());
    assertTrue(run("faction", "delete", "realm"));
    verify(player).sendMessage(contains("not part of"));
    when(faction.getMembers()).thenReturn(List.of("Leader"));
    when(guild.isBankrupt()).thenReturn(true);
    assertTrue(run("faction", "delete", "realm"));
    verify(player).sendMessage(contains("bankruptcy"));
    when(guild.isBankrupt()).thenReturn(false);
    var loan = mock(Loan.class);
    when(guild.getLoanHandler().getLoansTaken()).thenReturn(List.of(loan));
    assertTrue(run("faction", "delete", "realm"));
    verify(player).sendMessage(contains("active loans"));
    when(guild.getLoanHandler().getLoansTaken()).thenReturn(List.of());
    when(faction.getLeader()).thenReturn("Other");
    assertTrue(run("faction", "delete", "realm"));
    verify(player).sendMessage(contains("Only the faction leader"));
    when(faction.getLeader()).thenReturn("Leader");
    Bank bank = mock(Bank.class);
    when(faction.getBank()).thenReturn(bank);
    when(bank.getWealth()).thenReturn(1.0);
    assertTrue(run("faction", "delete", "realm"));
    verify(player).sendMessage(contains("bank balance is above 0"));
    factions.verify(() -> FactionManager.deleteFaction(any()), never());
  }

  @Test
  void claimsRequireAnExistingCapitalAndUnclaimsResolveAnAuthorizedFaction() {
    member();
    assertTrue(run("faction", "claim"));
    verify(player).sendMessage(contains("claim your first province"));
    when(faction.getProvinces()).thenReturn(List.of(1));
    try (MockedStatic<RestServer> rest = mockStatic(RestServer.class)) {
      rest.when(() -> RestServer.getProvince(player)).thenReturn(-2);
      assertTrue(run("faction", "claim"));
      assertTrue(run("faction", "unclaim"));
      verify(player, times(2)).sendMessage(contains("could not resolve province"));
      rest.when(() -> RestServer.getProvince(player)).thenReturn(7);
      assertTrue(run("faction", "claim"));
      verify(map).claim(player, faction, 7, false);
      assertTrue(run("faction", "unclaim"));
      verify(map).unclaim(player, faction, 7);
      assertTrue(run("faction", "unclaim", "realm"));
      verify(player).sendMessage(contains("do not have access"));
      admin();
      assertTrue(run("faction", "unclaim", "missing"));
      verify(player).sendMessage(contains("No faction by the id missing"));
      assertTrue(run("faction", "unclaim", "realm"));
      verify(map, times(2)).unclaim(player, faction, 7);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "guild"})
  void capitalsValidateLocationNameOwnershipAndDomainRules(String name) {
    guildLeader();
    var settlements = mock(SettlementHandler.class);
    when(faction.getSettlementHandler()).thenReturn(settlements);
    try (MockedStatic<RestServer> rest = mockStatic(RestServer.class);
        MockedStatic<DisplayNameGate> names = mockStatic(DisplayNameGate.class)) {
      rest.when(() -> RestServer.getProvince(player)).thenReturn(-2);
      assertTrue(run(name, "setcapital"));
      verify(player).sendMessage(contains("could not resolve province"));
      rest.when(() -> RestServer.getProvince(player)).thenReturn(0);
      assertTrue(run(name, "setcapital"));
      verify(player).sendMessage(contains("no province"));
      rest.when(() -> RestServer.getProvince(player)).thenReturn(7);
      when(settlements.requiresFoundingName(7)).thenReturn(true);
      if (name.equals("faction")) {
        assertTrue(run(name, "setcapital"));
        verify(player).sendMessage(contains("Name required"));
      }
      names
          .when(() -> DisplayNameGate.check(player, NameOperation.SETTLEMENT_FOUND, "Capital"))
          .thenReturn(DisplayNameGate.Result.NEEDS_CONFIRM);
      assertTrue(run(name, "setcapital", "Capital"));
      verify(map, never()).claimForCapital(any(), any(), anyInt(), anyBoolean());
      names
          .when(() -> DisplayNameGate.check(player, NameOperation.SETTLEMENT_FOUND, "Capital"))
          .thenReturn(DisplayNameGate.Result.OK);
      Faction foreign = mock(Faction.class);
      when(foreign.getId()).thenReturn("foreign");
      factions.when(() -> FactionManager.getByProvince(7)).thenReturn(foreign);
      assertTrue(run(name, "setcapital", "Capital"));
      verify(player).sendMessage(contains("owned by another faction"));
      factions.when(() -> FactionManager.getByProvince(7)).thenReturn(null);
      when(faction.getProvinces()).thenReturn(List.of(1));
      assertTrue(run(name, "setcapital", "Capital"));
      verify(player).sendMessage(contains("doesn't own this province"));
      when(faction.getProvinces()).thenReturn(List.of());
      assertTrue(run(name, "setcapital", "Capital"));
      verify(map).claimForCapital(player, faction, 7, true);
      when(faction.ownsProvince(7)).thenReturn(true);
      var denied = CapitalResult.fail("Settlement unavailable");
      when(settlements.validateFactionCapital(player, 7, "Capital")).thenReturn(denied);
      when(settlements.resolveGuildCapital(player, guild, 7, "Capital")).thenReturn(denied);
      assertTrue(run(name, "setcapital", "Capital"));
      verify(player).sendMessage("Settlement unavailable");
      var success = CapitalResult.ok("Capital founded");
      when(settlements.validateFactionCapital(player, 7, "Capital")).thenReturn(success);
      when(settlements.resolveGuildCapital(player, guild, 7, "Capital")).thenReturn(success);
      try (MockedStatic<CapitalMovePrompt> prompt = mockStatic(CapitalMovePrompt.class)) {
        assertTrue(run(name, "setcapital", "Capital"));
        if (name.equals("faction"))
          prompt.verify(
              () -> CapitalMovePrompt.applyFactionCapitalMove(player, faction, 7, "Capital"));
        else {
          verify(guild).setCapital(7);
          verify(player).playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
      }
    }
  }

  @Test
  void anExistingCapitalIsRenamedOrMovedThroughTheConfirmationPrompt() {
    var settlements = mock(SettlementHandler.class);
    when(faction.getSettlementHandler()).thenReturn(settlements);
    when(faction.ownsProvince(7)).thenReturn(true);
    when(faction.hasCapital()).thenReturn(true);
    when(faction.getCapital()).thenReturn(7);
    when(settlements.validateFactionCapital(eq(player), eq(7), nullable(String.class)))
        .thenReturn(CapitalResult.ok("valid"));
    try (MockedStatic<RestServer> rest = mockStatic(RestServer.class);
        MockedStatic<DisplayNameGate> names = mockStatic(DisplayNameGate.class);
        MockedStatic<CapitalMovePrompt> prompt = mockStatic(CapitalMovePrompt.class)) {
      rest.when(() -> RestServer.getProvince(player)).thenReturn(7);
      assertTrue(run("faction", "setcapital"));
      verify(player).sendMessage(contains("already set here"));
      when(settlements.rename(7, "Capital", true)).thenReturn(CapitalResult.fail("Duplicate name"));
      assertTrue(run("faction", "setcapital", "Capital"));
      verify(player).sendMessage("Duplicate name");
      when(settlements.rename(7, "Capital", true)).thenReturn(CapitalResult.ok("Renamed"));
      assertTrue(run("faction", "setcapital", "Capital"));
      prompt.verify(() -> CapitalMovePrompt.begin(player, faction, 7, "Capital", true));
      when(faction.getCapital()).thenReturn(1);
      assertTrue(run("faction", "setcapital", "Capital"));
      prompt.verify(() -> CapitalMovePrompt.begin(player, faction, 7, "Capital", false));
    }
  }

  private boolean run(CommandSender sender, String commandName, String... args) {
    Command command = mock(Command.class);
    when(command.getName()).thenReturn(commandName);
    return commands.onCommand(sender, command, commandName, args);
  }

  @Test
  void administratorGuildBankGrantsValidateTheGuildAmountAndLedger() {
    CommandSender console = mock(CommandSender.class);
    assertTrue(run(console, "faction", "addguildbank", "merchants", "10"));
    verify(console).sendMessage(contains("do not have access"));
    when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
    assertTrue(run(console, "faction", "addguildbank"));
    verify(console).sendMessage(contains("Usage:"));
    assertTrue(run(console, "faction", "addguildbank", "missing", "10"));
    verify(console).sendMessage(contains("guild does not exist"));
    assertTrue(run(console, "faction", "addguildbank", "merchants", "NaN"));
    verify(console, times(2)).sendMessage(contains("Usage:"));
    assertTrue(run(console, "faction", "addguildbank", "merchants", "10"));
    verify(console).sendMessage(contains("does not have a bank"));
    Bank bank = mock(Bank.class);
    when(guild.getBank()).thenReturn(bank);
    when(bank.getWealth()).thenReturn(10.0);
    try (MockedStatic<GuildBankGrant> grant = mockStatic(GuildBankGrant.class)) {
      assertTrue(run(console, "faction", "addguildbank", "merchants", "10"));
      verify(console, times(2)).sendMessage(contains("does not have a bank"));
    }
    assertTrue(run(console, "faction", "addguildbank", "merchants", "10"));
    verify(bank).deposit(10.0);
    verify(guild.getLedger().getHistory()).addDeposit("Compensation", 10.0);
    verify(console).sendMessage(contains("New balance:"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "vehicle transfer fort",
        "vehicle transfer",
        "transfervehicle fort",
        "transfervehicle",
        "vehicle take",
        "vehicle give Alice",
        "vehicle give",
        "vehicle handover Alice",
        "vehicle handover",
        "vehicle maintenance pay",
        "vehicle maintenance pay bank"
      })
  void vehicleCommandsRouteEachActionAndOptionalTarget(String command) {
    String[] args = command.split(" ");
    try (MockedStatic<VehicleFactionCommands> vehicles = mockStatic(VehicleFactionCommands.class)) {
      assertTrue(run("faction", args));
      if (command.contains("transfer"))
        vehicles.verify(
            () ->
                VehicleFactionCommands.armTransfer(
                    player, command.endsWith("fort") ? "fort" : null));
      else if (command.endsWith("take"))
        vehicles.verify(() -> VehicleFactionCommands.armTake(player));
      else if (command.contains("give"))
        vehicles.verify(
            () ->
                VehicleFactionCommands.armGive(player, command.endsWith("Alice") ? "Alice" : null));
      else if (command.contains("handover"))
        vehicles.verify(
            () ->
                VehicleFactionCommands.armHandover(
                    player, command.endsWith("Alice") ? "Alice" : null));
      else
        vehicles.verify(
            () ->
                VehicleFactionCommands.armMaintenancePay(
                    player, command.endsWith("bank") ? PaymentSource.BANK : PaymentSource.POUCH));
    }
  }

  @Test
  void vehicleUsageAndFindingValidateTheLeaderAndInstallation() {
    assertTrue(run("faction", "vehicle"));
    verify(player)
        .sendMessage(
            net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceMessages
                .vehicleUsage());
    assertTrue(run("faction", "vehicle", "maintenance"));
    verify(player)
        .sendMessage(
            net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceMessages
                .payUsage());
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(null);
    assertTrue(run("faction", "findvehicles", "fort"));
    verify(player).sendMessage(VehicleFindMessages.notLeader());
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(faction);
    assertTrue(run("faction", "findvehicles"));
    verify(player).sendMessage(VehicleFindMessages.usage());
    try (MockedStatic<VehicleFindMessages> finds = mockStatic(VehicleFindMessages.class)) {
      finds.when(VehicleFindMessages::unknownInstallation).thenCallRealMethod();
      finds
          .when(() -> VehicleFindMessages.resolveInstallation(installations, "missing"))
          .thenCallRealMethod();
      finds
          .when(() -> VehicleFindMessages.resolveInstallation(installations, "fort"))
          .thenCallRealMethod();
      assertTrue(run("faction", "findvehicles", "missing"));
      verify(player).sendMessage(VehicleFindMessages.unknownInstallation());
      Installation fort = installations.getById("fort");
      assertTrue(run("faction", "findvehicles", "fort"));
      finds.verify(() -> VehicleFindMessages.sendInstallationVehicles(player, fort));
    }
  }

  @Test
  void guildCapitalIsUnavailableWhenProvincesAreDisabled() {
    Cache.provincesEnabled = false;
    assertTrue(run("guild", "setcapital"));
    verify(player).sendMessage(Cache.PROVINCES_DISABLED_MESSAGE);
    verify(guild, never()).setCapital(anyInt());
  }

  @Test
  void installationMenusAndUnknownIdsPreserveThePendingConfirmation() {
    assertTrue(run("faction", "construct"));
    verify(player).sendMessage(contains("Usage:"));
    assertTrue(run("faction", "deconstruct"));
    verify(registered.installationView).installationsView(null, player, faction, true);
    assertTrue(run("faction", "deconstruct", "missing"));
    verify(player).sendMessage(contains("No installation with id"));
    assertTrue(run("faction", "upgrade"));
    verify(player).sendMessage(contains("upgrade <installation id>"));
    assertTrue(run("faction", "upgrade", "missing"));
    verify(player, times(2)).sendMessage(contains("No installation with id"));
    var construction = mock(InstallationConstruction.class);
    when(construction.getId()).thenReturn("pending");
    when(installations.getPendingConstruction()).thenReturn(construction);
    assertTrue(run("faction", "deconstruct", "pending"));
    assertSame(faction, registered.confirming.get(player));
    assertEquals("§7Confirm Action", player.getOpenInventory().getTitle());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "claim",
        "unclaim",
        "construct fort Capital",
        "deconstruct fort",
        "upgrade fort",
        "setbank",
        "setbanner",
        "setcapital",
        "setcolour 1,2,3"
      })
  void factionLeadershipIsRequiredForTerritoryAndAppearanceActions(String command) {
    factions.when(() -> FactionManager.getByLeader("Leader")).thenReturn(null);
    assertTrue(run("faction", command.split(" ")));
    verify(player).sendMessage(contains("leader"));
    verifyNoInteractions(map);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "setbank",
        "setbanner",
        "setcapital",
        "setcolour 1,2,3",
        "invite Alice",
        "kick Alice"
      })
  void guildLeadershipIsRequiredForGuildManagement(String command) {
    assertTrue(run("guild", command.split(" ")));
    verify(player).sendMessage(contains("leader"));
    verify(guild, never()).setBank(any());
    verify(guild, never()).setBanner(any());
  }

  @Test
  void unclaimRejectsExtraArgumentsWithoutPassingANullFactionToTheMap() {
    try (MockedStatic<RestServer> rest = mockStatic(RestServer.class)) {
      rest.when(() -> RestServer.getProvince(player)).thenReturn(7);
      assertFalse(run("faction", "unclaim", "realm", "extra"));
      verifyNoInteractions(map);
      verify(player).sendMessage(contains("command format"));
    }
  }

  @Test
  void settingPowerForAMissingFactionReportsTheId() {
    admin();
    assertFalse(run("faction", "setpower", "missing", "2.5"));
    verify(player).sendMessage("§cNo faction by the id missing");
  }

  private boolean run(String commandName, String... args) {
    Command command = mock(Command.class);
    when(command.getName()).thenReturn(commandName);
    return commands.onCommand(player, command, commandName, args);
  }

  @Test
  void factionInvitationsRequireLeadershipCapacityAndAnUnalignedInvitee() {
    assertTrue(run("faction", "invite", "Lady", "Alice"));
    verify(player).sendMessage(contains("need to have a faction"));
    member();
    when(faction.getLeader()).thenReturn("Other");
    assertTrue(run("faction", "invite", "Lady", "Alice"));
    verify(player).sendMessage(contains("Only the leader"));
    when(faction.getLeader()).thenReturn("Leader");
    int oldMaximum = Cache.maxMembers;
    try (MockedStatic<CharacterNames> characters = mockStatic(CharacterNames.class)) {
      assertTrue(run("faction", "invite", "Lady", "Alice"));
      verify(faction, never()).invite(anyString());
      Player alice = gui.player("Alice");
      characters.when(() -> CharacterNames.resolveOnline(player, "Lady Alice")).thenReturn(alice);
      when(faction.isMemberIgnoreCase("Alice")).thenReturn(true);
      assertTrue(run("faction", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("already a member"));
      when(faction.isMemberIgnoreCase("Alice")).thenReturn(false);
      Faction other = mock(Faction.class);
      factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(other);
      assertTrue(run("faction", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("member of another faction"));
      factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(null);
      Cache.maxMembers = 2;
      assertTrue(run("faction", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("maximum amount"));
      Cache.maxMembers = 3;
      assertTrue(run("faction", "invite", "Lady", "Alice"));
      verify(faction).invite("Alice");
      verify(alice).sendMessage(contains("invited you"));
      verify(alice).sendMessage(contains("/faction join realm"));
    } finally {
      Cache.maxMembers = oldMaximum;
    }
  }

  @Test
  void guildInvitationsRejectSelfDuplicatesAndProtectedMemberships() {
    guildLeader();
    when(guild.isLeader(player)).thenReturn(false);
    assertTrue(run("guild", "invite", "Lady", "Alice"));
    verify(player).sendMessage(contains("Only the guild leader"));
    when(guild.isLeader(player)).thenReturn(true);
    when(guild.isBase()).thenReturn(true);
    assertTrue(run("guild", "invite", "Lady", "Alice"));
    verify(player).sendMessage(contains("use /faction invite"));
    when(guild.isBase()).thenReturn(false);
    try (MockedStatic<CharacterNames> characters = mockStatic(CharacterNames.class)) {
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(guild, never()).invite(anyString());
      characters.when(() -> CharacterNames.resolveOnline(player, "Lady Alice")).thenReturn(player);
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("Cannot invite yourself"));
      Player alice = gui.player("Alice");
      characters.when(() -> CharacterNames.resolveOnline(player, "Lady Alice")).thenReturn(alice);
      when(guild.isInvited("Alice")).thenReturn(true);
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("already invited"));
      when(guild.isInvited("Alice")).thenReturn(false);
      when(guild.isMember("Alice")).thenReturn(true);
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("already a member"));
      when(guild.isMember("Alice")).thenReturn(false);
      Guild other = mock(Guild.class);
      Faction foreign = mock(Faction.class);
      when(other.getFaction()).thenReturn(foreign);
      factions.when(() -> FactionManager.getGuildByMember("Alice")).thenReturn(other);
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("in another faction"));
      when(other.getFaction()).thenReturn(faction);
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("in the same faction"));
      when(other.isBase()).thenReturn(true);
      when(other.isLeader("Alice")).thenReturn(true);
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(player).sendMessage(contains("leader of the faction, cannot invite"));
      when(other.isLeader("Alice")).thenReturn(false);
      assertTrue(run("guild", "invite", "Lady", "Alice"));
      verify(guild).invite("Alice");
      verify(alice).sendMessage(contains("invited to the guild"));
    }
  }

  @Test
  void closedBordersBlockOutsideGuildInvitesButAllowTheFactionLeader() {
    guildLeader();
    when(faction.hasFactionRule(Rules.CLOSED_BORDERS)).thenReturn(true);
    Player alice = gui.player("Alice");
    try (MockedStatic<CharacterNames> characters = mockStatic(CharacterNames.class)) {
      characters.when(() -> CharacterNames.resolveOnline(player, "Alice")).thenReturn(alice);
      assertTrue(run("guild", "invite", "Alice"));
      verify(player).sendMessage(contains("closed borders"));
      verify(guild, never()).invite("Alice");
      when(faction.isLeader("Leader")).thenReturn(true);
      assertTrue(run("guild", "invite", "Alice"));
      verify(guild).invite("Alice");
    }
  }
}
