package net.tfminecraft.simplefactions.war.battle.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandBattleService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.BattleWarbandRetreatService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.BattleWarbandRetreatService.RetreatResult;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.BattleWarbandRetreatService.Messages;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleJoinService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.core.WarDevMode;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class BattleCommandsCoverageTest {
  private GuiTestFixture ui;
  private Player alice;
  private BattleCommandManager commands;
  private BattleTabCompletion tabs;
  private final Map<String, Player> online = new LinkedHashMap<>();
  private List<Battle> previousBattles;
  private List<Warband> previousBands;
  private Map<Player, Battle> previousEditors;
  private Map<Player, String> previousSideEditors;
  private InventoryManager previousInventory;
  private MockedStatic<BattlePersistenceService> persistence;
  private MockedStatic<WarDevMode> devmode;
  private MockedStatic<FactionManager> factions;
  private MockedConstruction<BattleInventoryManager> menus;

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    previousBattles = new ArrayList<>(BattleManager.get());
    previousBands = new ArrayList<>(WarbandManager.get());
    previousEditors = new HashMap<>(BattleManager.currentBattle);
    previousSideEditors = new HashMap<>(BattleManager.currentSideEdit);
    BattleManager.get().clear();
    WarbandManager.get().clear();
    BattleManager.currentBattle.clear();
    BattleManager.currentSideEdit.clear();
    previousInventory = FactionManager.inv;
    FactionManager.inv = mock(InventoryManager.class);
    FactionManager.inv.confirming = new HashMap<>();
    factions = mockStatic(FactionManager.class);
    when(ui.world.getName()).thenReturn("world");
    when(Bukkit.getWorld("world")).thenReturn(ui.world);
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> online.values());
    when(Bukkit.getPlayerExact(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
    when(Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.values().stream()
        .filter(player -> player.getUniqueId().equals(call.getArgument(0))).findFirst().orElse(null));
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
        .thenAnswer(call -> mock(BossBar.class));
    persistence = mockStatic(BattlePersistenceService.class);
    persistence.when(() -> BattlePersistenceService.deleteWarband(any()))
        .thenAnswer(call -> { WarbandManager.deleteWarband(call.getArgument(0)); return null; });
    persistence.when(() -> BattlePersistenceService.deleteManualBattle(any()))
        .thenAnswer(call -> { BattleManager.deleteBattle(call.getArgument(0)); return null; });
    devmode = mockStatic(WarDevMode.class);
    menus = mockConstruction(BattleInventoryManager.class);
    alice = player("Alice");
    commands = new BattleCommandManager();
    tabs = new BattleTabCompletion();
  }

  @AfterEach
  void cleanup() {
    menus.close();
    devmode.close();
    persistence.close();
    factions.close();
    FactionManager.inv = previousInventory;
    BattleManager.get().clear();
    BattleManager.get().addAll(previousBattles);
    WarbandManager.get().clear();
    WarbandManager.get().addAll(previousBands);
    BattleManager.currentBattle.clear();
    BattleManager.currentBattle.putAll(previousEditors);
    BattleManager.currentSideEdit.clear();
    BattleManager.currentSideEdit.putAll(previousSideEditors);
    ui.close();
  }

  private Player player(String name) {
    Player player = ui.player(name);
    online.put(name, player);
    return player;
  }

  private Command command(String name) {
    Command command = mock(Command.class);
    when(command.getName()).thenReturn(name);
    return command;
  }

  private boolean run(String name, String... args) {
    return commands.onCommand(alice, command(name), name, args);
  }

  private List<String> complete(CommandSender sender, String name, String... args) {
    return tabs.onTabComplete(sender, command(name), name, args);
  }

  private Battle battle(String id, BattleType type) {
    Battle battle = BattleFactory.createBlank(type, id);
    BattleManager.addBattle(battle);
    return battle;
  }

  private Warband band(String id, Player leader) {
    Warband band = new Warband(id, leader);
    WarbandManager.addWarband(band);
    return band;
  }

  private void admin() {
    when(alice.hasPermission("warbands.admin")).thenReturn(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"warband", "battle"})
  void emptyCommandsGiveUsageWithoutReadingAMissingArgument(String name) {
    assertTrue(assertDoesNotThrow(() -> run(name)));
    verify(alice).sendMessage(contains("Error with command format"));
    persistence.verifyNoInteractions();
  }

  @ParameterizedTest
  @CsvSource({"setlives,invalid", "setlives,2147483648", "setcontestduration,invalid", "setdefenderlives,invalid"})
  void invalidNumbersDoNotThrowOrMutateBattleSettings(String operation, String input) {
    admin();
    Battle battle = battle("battle", BattleType.FIELD);
    assertTrue(assertDoesNotThrow(() -> run("battle", operation, "battle", input)));
    assertEquals(25, battle.getLives());
    assertEquals(0, battle.getContestDurationSeconds());
    assertNull(battle.getDefenderLives());
    persistence.verifyNoInteractions();
    verify(alice, atLeastOnce()).sendMessage(startsWith("§c"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void manualLivesRejectNonPositiveValues(String input) {
    admin();
    Battle battle = battle("battle", BattleType.FIELD);
    assertTrue(run("battle", "setlives", "battle", input));
    assertEquals(25, battle.getLives());
    persistence.verifyNoInteractions();
  }

  @Test
  void invitingAnOfflinePlayerGivesFeedbackWithoutPassingNullIntoTheWarband() {
    Warband band = band("scouts", alice);
    assertTrue(assertDoesNotThrow(() -> run("warband", "invite", "Offline")));
    assertEquals(1, band.getMemberCount());
    persistence.verifyNoInteractions();
    verify(alice).sendMessage(contains("online"));
  }

  @ParameterizedTest
  @CsvSource({"setlives,5|10|20|50", "setcontestduration,60|120|180|240|300", "setdefenderlives,5|10|20|25|50"})
  void numericSettingsCompleteNumbersInsteadOfUnrelatedSideIds(String operation, String values) {
    admin();
    battle("battle", BattleType.FIELD);
    assertEquals(List.of(values.split("\\|")), complete(alice, "battle", operation, "battle", ""));
  }

  @Test
  void battleIdsAreOfferedForEveryAdministrationCommandThatTakesOne() {
    admin();
    battle("front", BattleType.FIELD);
    for (String operation : List.of("edit", "delete", "addside", "addpoint", "setlives", "setspawn", "setjail", "setcontestmin", "setcontestmax", "setcontestduration", "setraidtarget", "setdefenderlives")) {
      assertEquals(List.of("front"), complete(alice, "battle", operation, ""), operation);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"delete", "toggleopen"})
  void successfulWarbandCommandsDoNotFallThroughIntoUsageErrors(String operation) {
    Warband band = band("scouts", alice);
    boolean handled = operation.equals("delete")
        ? run("warband", operation, "scouts") : run("warband", operation);
    assertTrue(handled);
    verify(alice, never()).sendMessage(contains("Error with command format"));
    if (operation.equals("delete")) assertNull(WarbandManager.getByString("scouts"));
    else assertFalse(band.isLocked());
  }

  @Test
  void duplicateWarbandIdsCannotReplaceOrShadowAnotherPlayersWarband() {
    Player bob = player("Bob");
    Warband original = band("scouts", bob);
    assertTrue(run("warband", "create", "SCOUTS"));
    assertEquals(List.of(original), WarbandManager.get());
    persistence.verifyNoInteractions();
    verify(alice).sendMessage(startsWith("§c"));
  }

  @Test
  void aRejectedSignupDoesNotConsumeTheWarbandInvitation() {
    Player bob = player("Bob");
    Warband band = band("scouts", bob);
    band.invite(alice);
    try (MockedStatic<CampaignWarbandSignupService> signups = mockStatic(CampaignWarbandSignupService.class)) {
      signups.when(() -> CampaignWarbandSignupService.signup(alice, band, null)).thenReturn("Leave your vehicle first");
      assertTrue(run("warband", "join", "scouts"));
      assertTrue(band.isInvited(alice));
      assertFalse(band.hasMember(alice));
      verify(alice).sendMessage("§cLeave your vehicle first");
    }
  }

  @Test
  void onlyPlayersExecuteGameplayCommandsAndBattleEditingRequiresPermission() {
    CommandSender console = mock(CommandSender.class);
    assertFalse(commands.onCommand(console, command("battle"), "battle", new String[] {"edit", "front"}));
    verifyNoInteractions(console);
    assertTrue(run("battle", "edit", "front"));
    verify(alice).sendMessage("§a[Battle]§4You do not have access to this command");
    persistence.verifyNoInteractions();
    assertFalse(run("unknown", "anything"));
    verify(alice).sendMessage(contains("Error with command format"));
  }

  @Test
  void creationRegistersAndPersistsARealWarbandAndRefusesPlayersAlreadyInOne() {
    assertTrue(run("warband", "create", "scouts"));
    Warband band = WarbandManager.getByString("scouts");
    assertNotNull(band);
    assertEquals(alice.getUniqueId(), band.getLeaderId());
    assertTrue(band.hasMember(alice));
    persistence.verify(() -> BattlePersistenceService.persistWarband(band));
    devmode.verify(() -> WarDevMode.seedDummyMembersIfEnabled(band));
    assertTrue(run("warband", "create", "second"));
    assertNull(WarbandManager.getByString("second"));
    verify(alice).sendMessage("§cYou already have a warband!");
  }

  private Warband campaignBand(Player leader) {
    Side side = mock(Side.class);
    Faction faction = mock(Faction.class);
    when(faction.getName()).thenReturn("Home");
    when(side.getLeader()).thenReturn(faction);
    Warband band = Warband.createRaidShell("campaign", side, "attacker");
    band.addPlayer(leader);
    band.setLeader(leader);
    WarbandManager.addWarband(band);
    return band;
  }

  @Test
  void warbandDeletionEnforcesMembershipLeadershipAndCampaignProtection() {
    assertTrue(run("warband", "delete", "missing"));
    verify(alice).sendMessage("§a[Battle]§c Error! Warband does not exist!");
    Player bob = player("Bob");
    Warband band = band("scouts", bob);
    assertTrue(run("warband", "delete", "scouts"));
    verify(alice).sendMessage("§cCannot delete a warband you are not part of!");
    band.addPlayer(alice);
    assertTrue(run("warband", "delete", "scouts"));
    verify(alice).sendMessage("§cOnly the warband leader can delete the warband!");
    Warband campaign = campaignBand(alice);
    assertTrue(run("warband", "delete", "campaign"));
    verify(alice).sendMessage("§cCampaign warbands cannot be deleted");
    assertEquals(2, WarbandManager.get().size());
    admin();
    assertTrue(run("warband", "delete", "campaign"));
    assertFalse(WarbandManager.get().contains(campaign));
    assertTrue(WarbandManager.get().contains(band));
  }

  @Test
  void leaderCanToggleBothOpenAndClosedAndListWarbands() {
    assertTrue(run("warband", "toggleopen"));
    verify(alice).sendMessage("§cYou need to lead a warband to open/close it");
    Warband band = band("scouts", alice);
    assertTrue(run("warband", "toggleopen"));
    assertFalse(band.isLocked());
    verify(alice).sendMessage("§aWarband is now open");
    assertTrue(run("warband", "toggleopen"));
    assertTrue(band.isLocked());
    verify(alice).sendMessage("§cWarband is now invite only");
    persistence.verify(() -> BattlePersistenceService.persistWarband(band), times(2));
    assertTrue(run("warband", "list"));
    verify(menus.constructed().getLast()).warbandList(alice);
  }

  @ParameterizedTest
  @CsvSource({"kick,You need to have a warband to kick someone,Only the leader can dismiss members!", "setleader,You need to have a warband to change leader,Only the leader can set a new leader!", "invite,You need to have a warband to invite someone,Only the leader can invite new members!"})
  void membershipChangesRequireAnExistingWarbandLedByTheSender(String operation, String noBand, String noLeader) {
    assertTrue(run("warband", operation, "Carol"));
    verify(alice).sendMessage("§c" + noBand);
    Warband band = band("scouts", player("Bob"));
    band.addPlayer(alice);
    assertTrue(run("warband", operation, "Carol"));
    verify(alice).sendMessage("§c" + noLeader);
    persistence.verifyNoInteractions();
  }

  @Test
  void kickingValidatesTheTargetAndRemovesOnlyTheNamedMember() {
    Warband band = band("scouts", alice);
    Player bob = player("Bob");
    player("Carol");
    assertTrue(run("warband", "kick", "Alice"));
    verify(alice).sendMessage("§cYou cannot dismiss the leader!");
    assertTrue(run("warband", "kick", "Offline"));
    assertTrue(run("warband", "kick", "Bob"));
    verify(alice, times(2)).sendMessage("§cPlayer is not a member");
    band.addPlayer(bob);
    assertTrue(run("warband", "kick", "Bob"));
    assertFalse(band.hasMember(bob));
    assertTrue(band.hasMember(alice));
    persistence.verify(() -> BattlePersistenceService.persistWarband(band));
    verify(bob).sendMessage("§aAlice kicked you from scouts");
  }

  @Test
  void leadershipChangesOnlyToCurrentMembersAndNotifiesTheRoster() {
    Warband band = band("scouts", alice);
    Player bob = player("Bob");
    Player outsider = player("Carol");
    assertTrue(run("warband", "setleader", "Offline"));
    assertTrue(run("warband", "setleader", "Bob"));
    verify(alice, times(2)).sendMessage("§cPlayer is not in the warband");
    assertTrue(run("warband", "setleader", "Alice"));
    verify(alice).sendMessage("§cPlayer is already the leader");
    band.addPlayer(bob);
    assertTrue(run("warband", "setleader", "Bob"));
    assertEquals(bob.getUniqueId(), band.getLeaderId());
    verify(alice).sendMessage("§aBob is the new warband leader!");
    verify(bob).sendMessage("§aBob is the new warband leader!");
    verify(outsider, never()).sendMessage(anyString());
    persistence.verify(() -> BattlePersistenceService.persistWarband(band));
  }

  @Test
  void invitationsAreStoredForOnlineNonMembersAndExplainHowToJoin() {
    Warband band = band("scouts", alice);
    Player bob = player("Bob");
    player("Carol");
    assertTrue(run("warband", "invite", "Alice"));
    verify(alice).sendMessage("§cPlayer is already a member");
    assertTrue(run("warband", "invite", "Bob"));
    assertTrue(band.isInvited(bob));
    assertFalse(band.hasMember(bob));
    verify(bob).sendMessage("§aAlice invited you to scouts");
    verify(bob).sendMessage("§aType /warband join scouts§a to join");
    persistence.verify(() -> BattlePersistenceService.persistWarband(band));
  }

  @Test
  void joiningChecksExistingMembershipInvitationsAndUsesSignupServiceResult() {
    Warband own = band("own", alice);
    assertTrue(run("warband", "join", "scouts"));
    verify(alice).sendMessage("§cAlready in a warband, leave your current warband first!");
    WarbandManager.deleteWarband(own);
    assertTrue(run("warband", "join", "missing"));
    verify(alice).sendMessage("§cWarband not found");
    Warband band = band("scouts", player("Bob"));
    assertTrue(run("warband", "join", "scouts"));
    verify(alice).sendMessage("§cYou need to be invited to this warband by the leader first!");
    band.invite(alice);
    try (MockedStatic<CampaignWarbandSignupService> signups = mockStatic(CampaignWarbandSignupService.class)) {
      signups.when(() -> CampaignWarbandSignupService.signup(alice, band, null))
          .thenAnswer(call -> { band.addPlayer(alice); return null; });
      assertTrue(run("warband", "join", "scouts"));
      assertTrue(band.hasMember(alice));
      assertFalse(band.isInvited(alice));
      verify(alice).sendMessage("§aJoined scouts");
    }
  }

  @Test
  void campaignSignupRequiresAFactionButDoesNotRequireAManualInvitation() {
    Warband band = campaignBand(player("Bob"));
    assertTrue(run("warband", "join", "campaign"));
    verify(alice).sendMessage("§cThis is a faction warband, you need a faction to join");
    Faction faction = mock(Faction.class);
    factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(faction);
    try (MockedStatic<CampaignWarbandSignupService> signups = mockStatic(CampaignWarbandSignupService.class)) {
      assertTrue(run("warband", "join", "campaign"));
      signups.verify(() -> CampaignWarbandSignupService.signup(alice, band, faction));
      verify(alice).sendMessage("§aJoined campaign");
    }
  }

  @Test
  void leavingRefusesManualLeadersAndRoutesMembersThroughBattleCleanup() {
    assertTrue(run("warband", "leave"));
    verify(alice).sendMessage("§cYou are not in a warband");
    Warband band = band("scouts", alice);
    assertTrue(run("warband", "leave"));
    verify(alice).sendMessage("§cA leader cannot abandon their warband.");
    verify(alice).sendMessage("§cUse /warband delete first");
    band.setLeader(player("Bob"));
    try (MockedStatic<CampaignWarbandBattleService> cleanup = mockStatic(CampaignWarbandBattleService.class)) {
      assertTrue(run("warband", "leave"));
      cleanup.verify(() -> CampaignWarbandBattleService.processLeave(alice, band, true));
      verify(alice).sendMessage("§aLeft scouts");
    }
  }

  @Test
  void listingBattlesRequiresAWarbandLeaderButJoiningReportsServiceErrors() {
    assertTrue(run("battle", "list"));
    verify(alice).sendMessage("§cYou need to lead a warband to view battles");
    Warband band = band("scouts", player("Bob"));
    band.addPlayer(alice);
    assertTrue(run("battle", "list"));
    verify(alice).sendMessage("§cOnly the leader can view battles!");
    band.setLeader(alice);
    assertTrue(run("battle", "list"));
    verify(menus.constructed().getLast()).battleList(alice);
    assertTrue(run("battle", "join", "missing", "attacker"));
    verify(alice).sendMessage("§cThere is no battle with this id");
    Battle battle = battle("front", BattleType.FIELD);
    try (MockedStatic<BattleJoinService> joins = mockStatic(BattleJoinService.class)) {
      joins.when(() -> BattleJoinService.join(alice, battle, "attacker")).thenReturn("Locked");
      assertTrue(run("battle", "join", "front", "attacker"));
      verify(alice).sendMessage("§cLocked");
      joins.when(() -> BattleJoinService.join(alice, battle, "attacker")).thenReturn(null);
      assertTrue(run("battle", "join", "front", "attacker"));
      verify(alice).sendMessage("§aJoined attacker in the battle front");
    }
  }

  @Test
  void tabCompletionReflectsPermissionsCurrentWarbandsAndSideNames() {
    assertEquals(List.of("list", "join"), complete(alice, "battle"));
    assertTrue(complete(alice, "warband").containsAll(List.of("create", "kick", "retreat")));
    assertEquals(List.of("<id>"), complete(alice, "warband", "create", ""));
    assertEquals(List.of(), complete(alice, "warband", "delete", ""));
    assertEquals(List.of(), complete(alice, "warband", "kick", ""));
    assertEquals(List.of(), complete(alice, "warband", "setleader", ""));
    Player bob = player("Bob");
    Warband band = band("scouts", alice);
    band.addPlayer(bob);
    assertEquals(List.of("scouts"), complete(alice, "warband", "delete", ""));
    assertEquals(List.of("Alice", "Bob"), complete(alice, "warband", "invite", ""));
    assertEquals(List.of("Bob"), complete(alice, "warband", "kick", ""));
    assertEquals(List.of("Bob"), complete(alice, "warband", "setleader", ""));
    battle("front", BattleType.FIELD);
    assertEquals(List.of("front"), complete(alice, "battle", "join", ""));
    assertEquals(List.of("attacker", "defender"), complete(alice, "battle", "join", "front", ""));
    assertNull(complete(alice, "battle", "join", "missing", ""));
    assertNull(complete(alice, "battle", "create", ""));
    admin();
    assertTrue(complete(alice, "battle", "").containsAll(List.of("create", "edit", "delete", "setlives")));
    assertEquals(List.of("field", "siege", "raid"), complete(alice, "battle", "create", ""));
    assertEquals(List.of("<battleId>"), complete(alice, "battle", "create", "field", ""));
    assertEquals(List.of("<sideId>"), complete(alice, "battle", "addside", "front", ""));
    for (String operation : List.of("addpoint", "setspawn", "setjail")) {
      assertEquals(List.of("attacker", "defender"), complete(alice, "battle", operation, "front", ""));
    }
    assertEquals(List.of(), complete(alice, "battle", "addpoint", "missing", ""));
    assertNull(complete(alice, "battle", "setspawn", "missing", ""));
    assertNull(complete(alice, "unknown", ""));
    assertNull(complete(mock(CommandSender.class), "warband", ""));
  }
  @Test
  void retreatReportsRejectionsAndRequiresConfirmationForAnEligibleCampaignLeader() {
    try (MockedStatic<BattleWarbandRetreatService> retreat = mockStatic(BattleWarbandRetreatService.class);
         MockedStatic<CampaignBattleJoinService> campaigns = mockStatic(CampaignBattleJoinService.class)) {
      retreat.when(() -> BattleWarbandRetreatService.retreatRejection(eq(alice), any(Instant.class)))
          .thenReturn(RetreatResult.REJECTED_NOT_IN_WARBAND);
      assertTrue(run("warband", "retreat"));
      verify(alice).sendMessage(Messages.messageForResult(RetreatResult.REJECTED_NOT_IN_WARBAND));
      assertTrue(FactionManager.inv.confirming.isEmpty());
      Warband band = band("scouts", alice);
      Battle battle = battle("campaign", BattleType.FIELD);
      battle.setWarId(10);
      Faction faction = mock(Faction.class);
      factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(faction);
      retreat.when(() -> BattleWarbandRetreatService.retreatRejection(eq(alice), any(Instant.class))).thenReturn(null);
      campaigns.when(() -> CampaignBattleJoinService.findCampaignBattleForWarband(band))
          .thenReturn(new CampaignBattleContext(battle, "attacker", mock(War.class)));
      assertTrue(run("warband", "retreat"));
      assertSame(faction, FactionManager.inv.confirming.get(alice));
      verify(FactionManager.inv).confirmBattleRetreatView(alice, "campaign");
      verify(alice).playSound(alice, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }
  }

  @Test
  void battleCreationValidatesTypeIdentityAndTheSingleManualBattleLimitBeforePersisting() {
    admin();
    assertTrue(run("battle", "create", "unknown", "front"));
    verify(alice).sendMessage("§cInvalid battle type. Use field, siege, or raid.");
    assertTrue(run("battle", "create", "field", " "));
    verify(alice).sendMessage("§cBattle id is required");
    Battle campaign = battle("existing", BattleType.FIELD);
    campaign.setWarId(10);
    assertTrue(run("battle", "create", "field", "existing"));
    verify(alice).sendMessage("§cThere already exists a battle with this id");
    assertTrue(run("battle", "create", "siege", "front"));
    Battle created = BattleManager.getByString("front");
    assertNotNull(created);
    assertEquals(BattleType.SIEGE, created.getBattleType());
    assertEquals(2, created.getSides().size());
    assertSame(created, BattleManager.currentBattle.get(alice));
    persistence.verify(() -> BattlePersistenceService.persistBattle(created));
    verify(menus.constructed().getLast()).battleView(alice, created);
    assertTrue(run("battle", "create", "raid", "second"));
    assertNull(BattleManager.getByString("second"));
    verify(alice).sendMessage("§cOnly one manual battle allowed. Delete the existing one first.");
  }

  @Test
  void editingOpensTheRequestedBattleAndDeletionClearsEveryEditorOnlyAfterSafetyChecks() {
    admin();
    assertTrue(run("battle", "edit", "missing"));
    verify(alice).sendMessage("§cThere is no battle with this id");
    assertTrue(run("battle", "delete", "missing"));
    verify(alice).sendMessage("§cNo battle with id §emissing");
    Battle battle = battle("front", BattleType.FIELD);
    assertTrue(run("battle", "edit", "front"));
    assertSame(battle, BattleManager.currentBattle.get(alice));
    verify(menus.constructed().getLast()).battleView(alice, battle);
    battle.setWarId(17);
    assertTrue(run("battle", "delete", "front"));
    verify(alice).sendMessage(contains("Campaign battles cannot be deleted"));
    verify(alice).sendMessage(contains("/war admin schedule 17 battledelete"));
    battle.setWarId(null);
    battle.setStarted(true);
    assertTrue(run("battle", "delete", "front"));
    verify(alice).sendMessage("§cCannot delete a battle while it is running.");
    verify(alice).sendMessage(contains("Stop it first"));
    persistence.verifyNoInteractions();
    Player bob = player("Bob");
    Battle other = battle("other", BattleType.FIELD);
    Player carol = player("Carol");
    BattleManager.currentBattle.put(bob, battle);
    BattleManager.currentBattle.put(carol, other);
    BattleManager.currentSideEdit.put(alice, "attacker");
    BattleManager.currentSideEdit.put(bob, "defender");
    BattleManager.currentSideEdit.put(carol, "attacker");
    battle.setStarted(false);
    assertTrue(run("battle", "delete", "front"));
    assertNull(BattleManager.getByString("front"));
    assertEquals(Map.of(carol, other), BattleManager.currentBattle);
    assertEquals(Map.of(carol, "attacker"), BattleManager.currentSideEdit);
    persistence.verify(() -> BattlePersistenceService.deleteManualBattle(battle));
    verify(alice).sendMessage("§aManual battle §efront §adeleted.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"addside", "addpoint", "setlives", "setspawn", "setjail", "setcontestduration", "setdefenderlives"})
  void threeArgumentSetupCommandsRejectMissingBattles(String operation) {
    admin();
    assertTrue(run("battle", operation, "missing", "5"));
    verify(alice).sendMessage("§cThere is no battle with this id");
    persistence.verifyNoInteractions();
  }

  @ParameterizedTest
  @ValueSource(strings = {"setcontestmin", "setcontestmax", "setraidtarget"})
  void twoArgumentSetupCommandsRejectMissingBattles(String operation) {
    admin();
    assertTrue(run("battle", operation, "missing"));
    verify(alice).sendMessage("§cThere is no battle with this id");
    persistence.verifyNoInteractions();
  }

  @Test
  void addingSidesRejectsDuplicatesAndPersistsTheNewSideWithBattleDefaults() {
    admin();
    Battle battle = battle("front", BattleType.FIELD);
    assertTrue(run("battle", "addside", "front", "attacker"));
    verify(alice).sendMessage("§cAlready a side with this id");
    assertTrue(run("battle", "addside", "front", "reinforcements"));
    BattleSide side = battle.getSideById("reinforcements");
    assertNotNull(side);
    assertEquals(battle.getLives(), side.getLives());
    assertEquals(battle.getLifeType(), side.getLt());
    assertEquals(3, battle.getSides().size());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
    verify(alice).sendMessage("§aSide §ereinforcements §acreated!");
  }

  @ParameterizedTest
  @ValueSource(strings = {"addpoint", "setspawn", "setjail"})
  void sideSetupRejectsMissingSidesWithoutPersisting(String operation) {
    admin();
    Battle battle = battle("front", BattleType.FIELD);
    assertTrue(run("battle", operation, "front", "missing"));
    verify(alice).sendMessage("§cNo side with this id");
    assertNull(battle.getSides().getFirst().getSpawn());
    assertNull(battle.getSides().getFirst().getJail());
    assertTrue(battle.getPoints().isEmpty());
    persistence.verifyNoInteractions();
  }

  @Test
  void sideSetupStoresThePlayersPositionAndGeneratedCapturePointIdentity() {
    admin();
    Battle battle = battle("front", BattleType.FIELD);
    Location location = alice.getLocation();
    assertTrue(run("battle", "setspawn", "front", "attacker"));
    assertEquals(location, battle.getSideById("attacker").getSpawn());
    assertTrue(run("battle", "setjail", "front", "attacker"));
    assertEquals(location, battle.getSideById("attacker").getJail());
    assertTrue(run("battle", "addpoint", "front", "attacker"));
    assertEquals(1, battle.getPoints().size());
    var point = battle.getPoints().getFirst();
    assertEquals(location, point.getLoc());
    assertSame(battle.getSideById("attacker"), point.getController());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle), times(3));
    verify(alice).sendMessage("§aPoint §e" + point.getId() + " §acreated!");
    assertTrue(run("battle", "addpoint", "front", "attacker", "custom"));
    verify(alice).sendMessage(contains("Custom point ids are no longer supported"));
    battle.setCapturePointsEnabled(false);
    assertTrue(run("battle", "addpoint", "front", "attacker"));
    verify(alice).sendMessage("§cCapture points are not enabled for this battle");
    assertEquals(1, battle.getPoints().size());
  }

  @Test
  void manualLivesPersistButCampaignLivesStayComputed() {
    admin();
    Battle battle = battle("front", BattleType.FIELD);
    assertTrue(run("battle", "setlives", "front", "37"));
    assertEquals(37, battle.getLives());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
    verify(alice).sendMessage("§aLives set to 37");
    battle.setWarId(12);
    assertTrue(run("battle", "setlives", "front", "50"));
    assertEquals(37, battle.getLives());
    verify(alice).sendMessage(contains("computed from war commitment"));
    verify(alice).sendMessage(contains("Adjust regiments"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"setcontestmin", "setcontestmax"})
  void contestCornersPersistTheSelectedPlayerPosition(String operation) {
    admin();
    Battle battle = battle("front", BattleType.FIELD);
    Location position = alice.getLocation();
    assertTrue(run("battle", operation, "front"));
    var corner = operation.endsWith("min") ? battle.getContestArea().getMin() : battle.getContestArea().getMax();
    assertEquals(position, corner.toBukkitLocation());
    verify(alice).sendMessage("§aContest area " + (operation.endsWith("min") ? "min" : "max") + " set!");
    verify(alice).sendMessage("§eWarning: battle type is not siege");
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
  }

  @ParameterizedTest
  @CsvSource({"setcontestduration,0", "setcontestduration,-1", "setdefenderlives,0", "setdefenderlives,-1"})
  void durationsAndDefenderLivesRemainUnchangedForNonPositiveInput(String operation, String value) {
    admin();
    Battle battle = battle("front", BattleType.FIELD);
    assertTrue(run("battle", operation, "front", value));
    assertEquals(0, battle.getContestDurationSeconds());
    assertNull(battle.getDefenderLives());
    verify(alice).sendMessage(contains("must be at least 1"));
    persistence.verifyNoInteractions();
  }

  @Test
  void validSiegeAndRaidSetupPersistsTheirOwnSettings() {
    admin();
    Battle siege = battle("siege", BattleType.SIEGE);
    assertTrue(run("battle", "setcontestduration", "siege", "175"));
    assertEquals(175, siege.getContestDurationSeconds());
    verify(alice).sendMessage("§aContest duration set to 175s");
    Battle raid = battle("raid", BattleType.RAID);
    assertTrue(run("battle", "setdefenderlives", "raid", "12"));
    assertEquals(12, raid.getDefenderLives());
    verify(alice).sendMessage("§aDefender lives set to 12");
    assertTrue(run("battle", "setraidtarget", "raid"));
    assertEquals(alice.getLocation(), raid.getRaidTarget().getLocation().toBukkitLocation());
    verify(alice).sendMessage("§aRaid target set!");
    persistence.verify(() -> BattlePersistenceService.persistBattle(siege));
    persistence.verify(() -> BattlePersistenceService.persistBattle(raid), times(2));
    verify(alice, never()).sendMessage(startsWith("§eWarning:"));
    assertTrue(run("battle", "setraidtarget", "siege"));
    verify(alice).sendMessage("§eWarning: battle type is not raid");
    assertFalse(run("battle", "edit"));
    verify(alice).sendMessage(contains("Error with command format"));
  }

}
