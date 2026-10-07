package net.tfminecraft.simplefactions.war.battle.engine.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.enums.DefenderRespawnMode;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidWinService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.campaign.raid.intruder.CampaignRaidIntruderService;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandVehicleRules;
import org.bukkit.entity.Entity;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.junit.jupiter.params.provider.CsvSource;
import net.tfminecraft.simplefactions.loaders.BattleTemplateLoader;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.ContestArea;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.capture.BattleCapturePoints;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.ui.BattleInventoryManager;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.vehicleframework.events.VFExplosionEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class BattleEngineListenersCoverageTest {
  private GuiTestFixture ui;
  private Player alice;
  private BattleManager listener;
  private WarbandManager warbandListener;
  private BattleInventoryManager menus;
  private List<Battle> previousBattles;
  private List<Warband> previousBands;
  private Map<Player, Battle> previousEditors;
  private Map<Player, String> previousSideEditors;
  private final Map<String, Player> online = new LinkedHashMap<>();
  private final List<BossBar> bars = new ArrayList<>();
  private final List<String> consoleCommands = new ArrayList<>();
  private MockedStatic<TLibs> tlibs;
  private MockedStatic<BattlePersistenceService> persistence;

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    previousBattles = new ArrayList<>(BattleManager.get());
    previousBands = new ArrayList<>(WarbandManager.get());
    previousEditors = new LinkedHashMap<>(BattleManager.currentBattle);
    previousSideEditors = new LinkedHashMap<>(BattleManager.currentSideEdit);
    BattleManager.get().clear();
    WarbandManager.get().clear();
    BattleManager.currentBattle.clear();
    BattleManager.currentSideEdit.clear();
    when(ui.world.getName()).thenReturn("world");
    when(Bukkit.getWorld("world")).thenReturn(ui.world);
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> online.values());
    when(Bukkit.getPlayerExact(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
    when(Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.values().stream()
        .filter(player -> player.getUniqueId().equals(call.getArgument(0))).findFirst().orElse(null));
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
        .thenAnswer(call -> { BossBar bar = mock(BossBar.class); bars.add(bar); return bar; });
    when(Bukkit.getPluginManager()).thenReturn(mock(PluginManager.class));
    when(Bukkit.getConsoleSender()).thenReturn(mock(ConsoleCommandSender.class));
    when(Bukkit.dispatchCommand(any(CommandSender.class), anyString())).thenAnswer(call -> {
      consoleCommands.add(call.getArgument(1)); return true;
    });
    ScoreboardManager scoreboards = mock(ScoreboardManager.class);
    when(scoreboards.getMainScoreboard()).thenReturn(mock(Scoreboard.class));
    when(Bukkit.getScoreboardManager()).thenReturn(scoreboards);
    tlibs = mockStatic(TLibs.class);
    ItemAPI items = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    tlibs.when(TLibs::getItemAPI).thenReturn(items);
    when(items.getCreator().getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    persistence = mockStatic(BattlePersistenceService.class);
    persistence.when(() -> BattlePersistenceService.deleteManualBattle(any()))
        .thenAnswer(call -> { BattleManager.deleteBattle(call.getArgument(0)); return null; });
    alice = player("Alice");
    when(alice.hasPermission("warbands.admin")).thenReturn(true);
    listener = new BattleManager();
    warbandListener = new WarbandManager();
    menus = new BattleInventoryManager();
  }

  @AfterEach
  void cleanup() {
    for (Player player : online.values()) BattleRespawnRouting.clear(player.getUniqueId());
    for (Battle battle : BattleManager.get()) {
      BattleCasualtyLedger.clear(battle);
      RaidAttackerEliminationService.clearBattleState(battle);
    }
    persistence.close();
    tlibs.close();
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
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getScoreboard()).thenReturn(mock(Scoreboard.class));
    org.bukkit.OfflinePlayer offline = mock(org.bukkit.OfflinePlayer.class);
    when(offline.getName()).thenReturn(name);
    UUID playerId = player.getUniqueId();
    when(Bukkit.getOfflinePlayer(playerId)).thenReturn(offline);
    online.put(name, player);
    return player;
  }

  private Battle battle(String id, BattleType type) {
    Battle battle = BattleFactory.createBlank(type, id + "-" + UUID.randomUUID());
    BattleManager.addBattle(battle);
    return battle;
  }

  private Warband band(Battle battle, String sideId, Player leader) {
    Warband band = new Warband("band-" + leader.getName(), leader);
    WarbandManager.addWarband(band);
    if (battle != null) battle.getSideById(sideId).addBand(band);
    return band;
  }

  private Inventory top() { return alice.getOpenInventory().getTopInventory(); }

  private void edit(Battle battle) {
    BattleManager.currentBattle.put(alice, battle);
    menus.battleView(alice, battle);
  }

  private CapturePoint runtimePoint(Battle battle) {
    CapturePoint point = new CapturePoint("A", new Location(ui.world, 10, 65, 10), battle.getSideById("attacker"), 100);
    battle.addPoint(point);
    battle.getPointManager().setPoints(battle.getPoints());
    return battle.getPointManager().getPoints().getFirst();
  }

  @ParameterizedTest
  @ValueSource(strings = {"capture", "spawn", "jail"})
  void explosionsInAnotherWorldDoNotCompareIncompatibleLocations(String protectedLocation) {
    Battle battle = battle("front", BattleType.FIELD);
    Location protectedPosition = new Location(ui.world, 0, 64, 0);
    switch (protectedLocation) {
      case "capture" -> runtimePoint(battle);
      case "spawn" -> battle.getSides().getFirst().setSpawn(protectedPosition);
      case "jail" -> battle.getSides().getFirst().setJail(protectedPosition);
      default -> fail();
    }
    VFExplosionEvent event = mock(VFExplosionEvent.class);
    when(event.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
    assertDoesNotThrow(() -> listener.explode(event));
    verify(event, never()).setBlockDamage(false);
  }

  @Test
  void displayedBattleNamesDoNotReplaceTheIdentityUsedToSelectABattle() {
    Battle battle = battle("front-id", BattleType.FIELD);
    battle.setDisplayName("The Northern Front");
    menus.battleList(alice);
    listener.invenClick(ui.click(alice, 0));
    assertEquals("§7Side Selection", alice.getOpenInventory().getTitle());
    assertSame(battle, BattleManager.currentBattle.get(alice));
  }

  @Test
  void bottomInventoryClicksCannotToggleBattleRulesOrOverwritePlayerItems() {
    Battle battle = battle("front", BattleType.FIELD);
    edit(battle);
    ItemStack personal = new ItemStack(Material.DIAMOND, 3);
    alice.getInventory().setItem(1, personal);
    ItemStack[] before = alice.getInventory().getContents().clone();
    InventoryClickEvent event = ui.click(alice, 28);
    listener.invenClick(event);
    assertTrue(battle.isLocked());
    assertArrayEquals(before, alice.getInventory().getContents());
    persistence.verifyNoInteractions();
  }

  @Test
  void losingAdminPermissionWhileTheEditorIsOpenPreventsFurtherChanges() {
    Battle battle = battle("front", BattleType.FIELD);
    edit(battle);
    when(alice.hasPermission("warbands.admin")).thenReturn(false);
    listener.invenClick(ui.click(alice, 1));
    assertTrue(battle.isLocked());
    persistence.verifyNoInteractions();
  }

  @Test
  void contestDurationChangesArePersistedLikeOtherBattleSettings() {
    Battle battle = battle("siege", BattleType.SIEGE);
    battle.setContestDurationSeconds(60);
    BattleManager.currentBattle.put(alice, battle);
    menus.contestView(alice, battle);
    listener.invenClick(ui.click(alice, 2));
    assertEquals(120, battle.getContestDurationSeconds());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
  }

  @Test
  void delayedRespawnRechecksMembershipBeforeRestoringResourcesOrTeleporting() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setStarted(true);
    Warband band = band(battle, "attacker", player("Bob"));
    band.addPlayer(alice);
    battle.getSideById("attacker").setSpawn(new Location(ui.world, 3, 64, 3));
    runtimePoint(battle);
    PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
    when(event.getPlayer()).thenReturn(alice);
    listener.playerRespawn(event);
    assertEquals(1, ui.tasks.size());
    band.removePlayer(alice);
    assertDoesNotThrow(ui::runTasks);
    verify(alice, never()).teleport(any(Location.class));
    verify(alice, never()).setFoodLevel(anyInt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"removed", "enemy", "contested", "left", "stopped", "offline"})
  void countdownRevalidatesTheRespawnChoiceBeforeTeleporting(String invalidation) {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setStarted(true);
    Warband band = band(battle, "attacker", alice);
    CapturePoint point = runtimePoint(battle);
    listener.spawnTeleport(alice, point);
    assertEquals(1, ui.repeatingTasks.size());
    Runnable timer = ui.repeatingTasks.getFirst();
    for (int tick = 0; tick < 15; tick++) timer.run();
    verify(alice, never()).teleport(any(Location.class));
    switch (invalidation) {
      case "removed" -> battle.getPointManager().getPoints().clear();
      case "enemy" -> point.setController(battle.getSideById("defender"));
      case "contested" -> point.setCaptureProgress(49);
      case "left" -> band.removePlayer(alice);
      case "stopped" -> battle.setStarted(false);
      case "offline" -> when(alice.isOnline()).thenReturn(false);
      default -> fail();
    }
    assertDoesNotThrow(timer::run);
    verify(alice, never()).teleport(any(Location.class));
  }
  private PlayerDeathEvent death(Player player, List<ItemStack> drops) {
    PlayerDeathEvent event = mock(PlayerDeathEvent.class);
    when(event.getEntity()).thenReturn(player);
    when(event.getDrops()).thenReturn(drops);
    return event;
  }

  @Test
  void registryQueriesResolveCampaignsRaidsMembersAndEditorSessionsByIdentity() {
    assertNull(BattleManager.getManualBattle());
    assertFalse(BattleManager.hasManualBattle());
    Battle campaign = battle("campaign", BattleType.FIELD);
    campaign.setWarId(10);
    Battle raid = battle("raid", BattleType.RAID);
    raid.setWarId(10);
    raid.setCampaignRaid(true);
    Battle other = battle("other", BattleType.FIELD);
    other.setWarId(11);
    Battle manual = battle("manual", BattleType.FIELD);
    assertSame(manual, BattleManager.getManualBattle());
    assertTrue(BattleManager.hasManualBattle());
    assertSame(campaign, BattleManager.getByWarId(10));
    assertNull(BattleManager.getByWarId(99));
    assertEquals(List.of(campaign, raid), BattleManager.getAllByWarId(10));
    assertTrue(BattleManager.getAllByWarId(99).isEmpty());
    assertSame(manual, BattleManager.getByString(manual.getId().toUpperCase(java.util.Locale.ROOT)));
    assertNull(BattleManager.getByString("missing"));
    Warband band = band(manual, "attacker", alice);
    assertSame(manual, BattleManager.getBattleByMemberId(alice.getUniqueId()));
    assertSame(manual, BattleManager.getBattleByPlayer(alice));
    assertNull(BattleManager.getBattleByMemberId(UUID.randomUUID()));
    assertNull(BattleManager.getBattleByPlayer(player("Outside")));
    assertSame(band, WarbandManager.getByMemberId(alice.getUniqueId()));
    assertSame(band, WarbandManager.getByString(band.getId().toUpperCase(java.util.Locale.ROOT)));
    assertNull(WarbandManager.getByString("missing"));
    assertNull(WarbandManager.getByMemberId(UUID.randomUUID()));
    assertSame(band, WarbandManager.getByLeader(alice));
    assertNull(WarbandManager.getByLeader(player("OtherLeader")));
    BattleManager.currentBattle.put(alice, manual);
    BattleManager.currentSideEdit.put(alice, "attacker");
    Player bob = player("Bob");
    BattleManager.currentBattle.put(bob, campaign);
    BattleManager.clearEditorSessions(null);
    BattleManager.clearEditorSessions(manual);
    assertFalse(BattleManager.currentBattle.containsKey(alice));
    assertFalse(BattleManager.currentSideEdit.containsKey(alice));
    assertSame(campaign, BattleManager.currentBattle.get(bob));
    WarbandManager.deleteWarband(band);
    assertNull(WarbandManager.getByPlayer(alice));
    BattleManager.deleteBattle(manual);
    assertNull(BattleManager.getManualBattle());
  }

  @Test
  void scheduledBattleTicksSkipInactiveBattlesAndShutdownClearsTheirRuntimeUi() {
    Battle idle = battle("idle", BattleType.RAID);
    Battle running = battle("running", BattleType.RAID);
    running.setStarted(true);
    try (MockedStatic<RaidWinService> wins = mockStatic(RaidWinService.class)) {
      listener.start();
      assertEquals(1, ui.repeatingTasks.size());
      ui.repeatingTasks.getFirst().run();
      wins.verify(() -> RaidWinService.checkRaidWin(running));
      wins.verify(() -> RaidWinService.checkRaidWin(idle), never());
      verify(bars.get(2)).setVisible(true);
      verify(bars.get(0), never()).setVisible(anyBoolean());
    }
    Battle raid = battle("campaign-raid", BattleType.RAID);
    raid.setStarted(true);
    raid.setCampaignRaid(true);
    BattleManager.shutdown();
    verify(bars.get(2)).removeAll();
    verify(bars.get(4)).removeAll();
    verify(bars.get(0), never()).removeAll();
    assertTrue(running.hasStarted(), "shutdown cleans displays without declaring a combat result");
    listener.end();
    assertFalse(running.hasStarted());
    assertFalse(raid.hasStarted());
    assertFalse(idle.hasStarted());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void campaignDeathsHonorKeepInventoryAndConsumeExactlyOneAvailableLife(boolean keepInventory) {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setWarId(12);
    battle.setStarted(true);
    battle.setKeepInventory(keepInventory);
    band(battle, "attacker", alice);
    BattleSide side = battle.getSideById("attacker");
    side.setLives(2);
    List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 3)));
    PlayerDeathEvent event = death(alice, drops);
    listener.playerDeath(event);
    verify(event).setKeepInventory(keepInventory);
    assertEquals(keepInventory ? 0 : 1, drops.size());
    assertEquals(1, side.getLives());
    assertFalse(BattleRespawnRouting.consumeJailRespawn(alice.getUniqueId()));
    assertEquals(Map.of("attacker", 1), BattleCasualtyLedger.getSideCasualties(battle));
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
    verify(alice).setMetadata(eq(BattleManager.KEEP_POUCH_METADATA), argThat((MetadataValue value) -> value.asBoolean()));
    assertEquals(1, ui.tasks.size());
    ui.runTasks();
    verify(alice).removeMetadata(BattleManager.KEEP_POUCH_METADATA, ui.plugin);
    side.setLives(0);
    listener.playerDeath(death(alice, new ArrayList<>()));
    assertEquals(0, side.getLives());
    assertTrue(BattleRespawnRouting.consumeJailRespawn(alice.getUniqueId()));
    assertFalse(BattleRespawnRouting.consumeJailRespawn(alice.getUniqueId()));
  }

  @Test
  void deathConsumesOnlyThePlayersOwnSideAfterEarlierUnrelatedBattlesAndBands() {
    Battle earlier = battle("earlier", BattleType.FIELD);
    earlier.setStarted(true);
    band(earlier, "attacker", player("EarlierAttacker"));
    band(earlier, "defender", player("EarlierDefender"));
    Battle battle = battle("current", BattleType.FIELD);
    battle.setStarted(true);
    band(battle, "attacker", player("Opponent"));
    band(battle, "defender", player("OtherLeader"));
    band(battle, "defender", alice);
    BattleSide ownSide = battle.getSideById("defender");
    ownSide.setLives(3);
    int earlierLives = earlier.getSideById("attacker").getLives();
    listener.playerDeath(death(alice, new ArrayList<>()));
    assertEquals(2, ownSide.getLives());
    assertEquals(earlierLives, earlier.getSideById("attacker").getLives());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
    persistence.verify(() -> BattlePersistenceService.persistBattle(earlier), never());
  }

  @Test
  void unrelatedInactiveAndAlreadyHandledIntruderDeathsDoNotConsumeBattleResources() {
    PlayerDeathEvent event = death(alice, new ArrayList<>());
    listener.playerDeath(event);
    verify(event, never()).setKeepInventory(anyBoolean());
    Battle battle = battle("front", BattleType.FIELD);
    band(battle, "attacker", alice);
    listener.playerDeath(event);
    assertEquals(25, battle.getSideById("attacker").getLives());
    battle.setStarted(true);
    try (MockedStatic<CampaignRaidIntruderService> intruders = mockStatic(CampaignRaidIntruderService.class)) {
      intruders.when(() -> CampaignRaidIntruderService.consumeIntruderDeath(alice.getUniqueId())).thenReturn(true);
      listener.playerDeath(event);
      assertEquals(25, battle.getSideById("attacker").getLives());
    }
    persistence.verifyNoInteractions();
    SimpleFactions.plugin = null;
    try {
      listener.playerDeath(event);
      assertEquals(24, battle.getSideById("attacker").getLives());
      verify(alice, never()).setMetadata(anyString(), any());
    } finally { SimpleFactions.plugin = ui.plugin; }
  }

  @ParameterizedTest
  @CsvSource({"attacker,INFINITE,2,true", "defender,INFINITE,2,false", "defender,LIVES,1,false"})
  void raidDeathsSeparateAttackerEliminationFromLimitedAndUnlimitedDefenderLives(String sideId, DefenderRespawnMode mode, int expectedLives, boolean eliminated) {
    Battle battle = battle("raid", BattleType.RAID);
    battle.setStarted(true);
    battle.setDefenderRespawnMode(mode);
    band(battle, sideId, alice);
    BattleSide side = battle.getSideById(sideId);
    side.setLives(2);
    listener.playerDeath(death(alice, new ArrayList<>()));
    assertEquals(expectedLives, side.getLives());
    assertEquals(eliminated, RaidAttackerEliminationService.isMarkedOut(battle, alice.getUniqueId()));
    persistence.verifyNoInteractions();
  }

  @Test
  void quittingRecordsCampaignCasualtyAndClearsPendingRespawnWithoutSpendingASecondLife() {
    PlayerQuitEvent event = mock(PlayerQuitEvent.class);
    when(event.getPlayer()).thenReturn(alice);
    listener.playerQuit(event);
    Battle battle = battle("front", BattleType.FIELD);
    battle.setWarId(12);
    band(battle, "attacker", alice);
    listener.playerQuit(event);
    assertTrue(BattleCasualtyLedger.getSideCasualties(battle).isEmpty());
    battle.setStarted(true);
    BattleRespawnRouting.scheduleJailRespawn(alice.getUniqueId(), true);
    listener.playerQuit(event);
    assertEquals(25, battle.getSideById("attacker").getLives());
    assertEquals(Map.of("attacker", 1), BattleCasualtyLedger.getSideCasualties(battle));
    assertFalse(BattleRespawnRouting.consumeJailRespawn(alice.getUniqueId()));
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
  }

  @ParameterizedTest
  @ValueSource(strings = {"attacker", "defender"})
  void raidQuitOnlyEliminatesAttackersAndChecksTheRaidOutcome(String side) {
    Battle battle = battle("raid", BattleType.RAID);
    battle.setStarted(true);
    band(battle, side, alice);
    PlayerQuitEvent event = mock(PlayerQuitEvent.class);
    when(event.getPlayer()).thenReturn(alice);
    try (MockedStatic<RaidWinService> wins = mockStatic(RaidWinService.class)) {
      listener.playerQuit(event);
      assertEquals(side.equals("attacker"), RaidAttackerEliminationService.isMarkedOut(battle, alice.getUniqueId()));
      if (side.equals("attacker")) wins.verify(() -> RaidWinService.checkRaidWin(battle));
      else wins.verifyNoInteractions();
    }
    persistence.verifyNoInteractions();
  }

  @ParameterizedTest
  @CsvSource({"FIELD,attacker,false,spawn", "FIELD,attacker,true,jail", "RAID,attacker,false,jail", "RAID,defender,false,spawn"})
  void delayedRespawnRestoresResourcesAndUsesTheCurrentSideRouting(BattleType type, String sideId, boolean jail, String destination) {
    PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
    when(event.getPlayer()).thenReturn(alice);
    listener.playerRespawn(event);
    assertTrue(ui.tasks.isEmpty());
    Battle battle = battle("front", type);
    band(battle, sideId, alice);
    listener.playerRespawn(event);
    assertTrue(ui.tasks.isEmpty());
    battle.setStarted(true);
    battle.setDefenderRespawnMode(DefenderRespawnMode.INFINITE);
    BattleSide side = battle.getSideById(sideId);
    Location spawn = new Location(ui.world, 2, 64, 2);
    Location cell = new Location(ui.world, 8, 64, 8);
    side.setSpawn(spawn);
    side.setJail(cell);
    BattleRespawnRouting.scheduleJailRespawn(alice.getUniqueId(), jail);
    if (type == BattleType.FIELD && !jail) runtimePoint(battle);
    listener.playerRespawn(event);
    assertEquals(1, ui.tasks.size());
    ui.runTasks();
    verify(alice).setFoodLevel(20);
    assertEquals(List.of("mmocore admin resource-health give Alice 100", "mmocore admin resource-mana give Alice 100"), consoleCommands);
    verify(alice).teleport(destination.equals("jail") ? cell : spawn);
    if (type == BattleType.FIELD && !jail) assertEquals("§7Respawn Points", alice.getOpenInventory().getTitle());
  }

  @Test
  void friendlyFireBlocksOnlyCurrentAlliesInARunningBattleWithTheRuleDisabled() {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    Entity nonPlayer = mock(Entity.class);
    when(event.getDamager()).thenReturn(nonPlayer);
    when(event.getEntity()).thenReturn(nonPlayer);
    listener.friendlyFire(event);
    when(event.getDamager()).thenReturn(alice);
    listener.friendlyFire(event);
    Player bob = player("Bob");
    when(event.getEntity()).thenReturn(bob);
    listener.friendlyFire(event);
    Battle battle = battle("front", BattleType.FIELD);
    Warband band = band(battle, "attacker", alice);
    band.addPlayer(bob);
    listener.friendlyFire(event);
    battle.setStarted(true);
    listener.friendlyFire(event);
    verify(event, never()).setCancelled(true);
    battle.setFriendlyFire(false);
    listener.friendlyFire(event);
    verify(event).setCancelled(true);
    Player enemy = player("Enemy");
    band(battle, "defender", enemy);
    when(event.getEntity()).thenReturn(enemy);
    listener.friendlyFire(event);
    verify(event).setCancelled(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"capture", "spawn", "jail"})
  void explosionsProtectNearbyBattleLocationsButLeaveDistantBlocksAlone(String protectedLocation) {
    Battle battle = battle("front", BattleType.FIELD);
    Location target = new Location(ui.world, 10, 65, 10);
    switch (protectedLocation) {
      case "capture" -> runtimePoint(battle);
      case "spawn" -> battle.getSides().getFirst().setSpawn(target);
      case "jail" -> battle.getSides().getFirst().setJail(target);
      default -> fail();
    }
    VFExplosionEvent event = mock(VFExplosionEvent.class);
    when(event.getLocation()).thenReturn(new Location(ui.world, 100, 65, 100));
    listener.explode(event);
    verify(event, never()).setBlockDamage(false);
    when(event.getLocation()).thenReturn(new Location(ui.world, 15, 65, 10));
    listener.explode(event);
    verify(event).setBlockDamage(false);
  }

  @Test
  void aValidCapturePointCountdownTeleportsOnceAtZeroAndCancelsItsTask() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setStarted(true);
    band(battle, "attacker", alice);
    CapturePoint point = runtimePoint(battle);
    listener.spawnTeleport(alice, point);
    Runnable timer = ui.repeatingTasks.getFirst();
    for (int tick = 0; tick < 15; tick++) timer.run();
    verify(alice, never()).teleport(any(Location.class));
    timer.run();
    verify(alice).teleport(point.getLoc());
    verify(ui.scheduler).cancelTask(1);
    verify(alice).sendTitle(" ", "§eTeleporting... §a15s", 0, 30, 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void manualWarbandJoiningRetainsInvitationsWhenMountedAndConsumesThemOnSuccess(boolean invited) {
    Warband band = band(null, null, player("Bob"));
    band.setLocked(invited);
    if (invited) band.invite(alice);
    menus.warbandList(alice);
    when(alice.isInsideVehicle()).thenReturn(true);
    warbandListener.invenClick(ui.click(alice, 0));
    assertFalse(band.hasMember(alice));
    if (invited) assertTrue(band.isInvited(alice));
    verify(alice).sendMessage("§c" + WarbandVehicleRules.JOIN_BLOCKED_MOUNTED);
    persistence.verifyNoInteractions();
    when(alice.isInsideVehicle()).thenReturn(false);
    warbandListener.invenClick(ui.click(alice, 0));
    assertTrue(band.hasMember(alice));
    assertFalse(band.isInvited(alice));
    persistence.verify(() -> BattlePersistenceService.persistWarband(band));
    verify(alice).sendMessage("§aJoined §e" + band.getId());
  }

  @Test
  void warbandListRejectsMissingInvitationsOtherMembershipAndMissingItemIds() {
    Warband target = band(null, null, player("Bob"));
    menus.warbandList(alice);
    warbandListener.invenClick(ui.click(alice, 0));
    verify(alice).sendMessage("§cWarband is invite only");
    assertFalse(target.hasMember(alice));
    band(null, null, alice);
    warbandListener.invenClick(ui.click(alice, 0));
    verify(alice).sendMessage("§cAlready in a warband, leave your current warband first!");
    top().setItem(0, new ItemStack(Material.STONE));
    warbandListener.invenClick(ui.click(alice, 0));
    top().setItem(0, null);
    warbandListener.invenClick(ui.click(alice, 0));
    alice.closeInventory();
    warbandListener.invenClick(ui.click(alice, 0));
  }

  @Test
  void factionWarbandSignupUsesFactionEligibilityAndPreservesStateOnRejectedSignup() {
    Side side = mock(Side.class);
    Faction home = mock(Faction.class);
    when(side.getLeader()).thenReturn(home);
    when(home.getName()).thenReturn("Home");
    Warband band = Warband.createRaidShell("campaign-band", side, "attacker");
    WarbandManager.addWarband(band);
    try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
         MockedStatic<CampaignWarbandSignupService> signup = mockStatic(CampaignWarbandSignupService.class)) {
      menus.warbandList(alice);
      warbandListener.invenClick(ui.click(alice, 0));
      verify(alice).sendMessage("§cThis is a faction warband, you need a faction to join");
      assertFalse(band.hasMember(alice));
      factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(home);
      signup.when(() -> CampaignWarbandSignupService.signup(alice, band, home)).thenReturn("Roster full");
      warbandListener.invenClick(ui.click(alice, 0));
      verify(alice).sendMessage("§cRoster full");
      assertFalse(band.hasMember(alice));
      signup.when(() -> CampaignWarbandSignupService.signup(alice, band, home))
          .thenAnswer(call -> { band.addPlayer(alice); return null; });
      warbandListener.invenClick(ui.click(alice, 0));
      assertTrue(band.hasMember(alice));
      persistence.verify(() -> BattlePersistenceService.persistWarband(band));
    }
  }

  @Test
  void warbandTickerShowsFriendsAndOtherAlliedBandsAndRefreshesOnlyOpenWarbandLists() {
    Battle battle = battle("front", BattleType.FIELD);
    Warband first = band(battle, "attacker", alice);
    Player bob = player("Bob");
    first.addPlayer(bob);
    Player carol = player("Carol");
    band(battle, "attacker", carol);
    Player enemy = player("Enemy");
    band(battle, "defender", enemy);
    band(null, null, player("Outside"));
    Player offline = player("Offline");
    when(offline.isOnline()).thenReturn(false);
    band(null, null, offline);
    online.remove("Offline");
    menus.warbandList(alice);
    Inventory inventory = top();
    warbandListener.start();
    assertEquals(1, ui.repeatingTasks.size());
    ui.repeatingTasks.getFirst().run();
    verify(alice).spawnParticle(eq(Particle.DUST), eq(0d), eq(66d), eq(0d), eq(1),
        argThat((Particle.DustOptions dust) -> dust.getColor().equals(Color.LIME)));
    verify(alice).spawnParticle(eq(Particle.DUST), eq(0d), eq(66d), eq(0d), eq(1),
        argThat((Particle.DustOptions dust) -> dust.getColor().equals(Color.BLUE)));
    assertSame(inventory, top());
    assertNotNull(inventory.getItem(0));
    verify(enemy, never()).spawnParticle(eq(Particle.DUST), anyDouble(), anyDouble(), anyDouble(), anyInt(),
        any(Particle.DustOptions.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deletingFromAStalePointMenuCannotTargetAnotherPointWhoseLetterWasReused(boolean removedSelection) {
    Battle battle = battle("front", BattleType.FIELD);
    CapturePoint first = new CapturePoint("A", new Location(ui.world, 10, 64, 10), battle.getSideById("attacker"), 100);
    CapturePoint second = new CapturePoint("B", new Location(ui.world, 20, 64, 20), battle.getSideById("attacker"), 100);
    first.setSequenceIndex(0);
    second.setSequenceIndex(1);
    battle.addPoint(first);
    battle.addPoint(second);
    BattleManager.currentBattle.put(alice, battle);
    menus.pointView(alice, battle);
    assertTrue(BattleCapturePoints.removePoint(battle, first));
    assertEquals("A", second.getId());
    listener.invenClick(ui.click(alice, removedSelection ? 0 : 1));
    assertEquals(removedSelection ? List.of(second) : List.of(), battle.getPoints());
    if (removedSelection) persistence.verifyNoInteractions();
    else persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
  }

  private void click(int slot) { listener.invenClick(ui.click(alice, slot)); }

  private ItemStack labelled(Material material, String name) {
    ItemStack item = new ItemStack(material);
    var meta = item.getItemMeta();
    meta.setDisplayName(name);
    item.setItemMeta(meta);
    return item;
  }

  @Test
  void editorRulesToggleAndPersistWithoutChangingTheFixedLifeMode() {
    Battle battle = battle("front", BattleType.FIELD);
    edit(battle);
    click(1); assertFalse(battle.isLocked());
    click(4); assertFalse(battle.hasFriendlyFire());
    click(5); assertFalse(battle.hasKeepInventory());
    click(6); assertTrue(battle.hasTeleport());
    click(14); assertFalse(battle.hasLootEnabled());
    var lifeMode = battle.getLifeType();
    click(2); assertSame(lifeMode, battle.getLifeType());
    click(3); assertEquals(25, battle.getLives());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle), times(5));
    battle.setStarted(true);
    click(1);
    assertFalse(battle.isLocked());
    verify(alice).sendMessage("§cCannot edit a battle while it has started");
  }

  @Test
  void editorCyclesSiegeAndRaidSettingsAndSequentialCaptureRules() {
    Battle siege = battle("siege", BattleType.SIEGE);
    siege.setContestDurationSeconds(60);
    edit(siege);
    click(3);
    assertEquals(120, siege.getContestDurationSeconds());
    click(8);
    assertFalse(siege.isSequentialCapture());
    Battle raid = battle("raid", BattleType.RAID);
    raid.setDefenderRespawnMode(DefenderRespawnMode.INFINITE);
    edit(raid);
    click(3);
    assertEquals(DefenderRespawnMode.LIVES, raid.getDefenderRespawnMode());
    raid.setDefenderLives(5);
    click(8);
    assertEquals(10, raid.getDefenderLives());
    click(3);
    assertEquals(DefenderRespawnMode.INFINITE, raid.getDefenderRespawnMode());
    click(8);
    assertEquals(10, raid.getDefenderLives());
    Battle field = battle("field", BattleType.FIELD);
    runtimePoint(field);
    field.getSideById("defender").setSpawn(new Location(ui.world, 0, 64, 0));
    field.getSideById("attacker").setSpawn(new Location(ui.world, 20, 64, 20));
    edit(field);
    click(8);
    assertTrue(field.isSequentialCapture());
    assertEquals(0, field.getPoints().getFirst().getSequenceIndex());
    click(8);
    assertFalse(field.isSequentialCapture());
    assertEquals("A", field.getPoints().getFirst().getId());
    persistence.verify(() -> BattlePersistenceService.persistBattle(siege));
    persistence.verify(() -> BattlePersistenceService.persistBattle(raid), times(3));
    persistence.verify(() -> BattlePersistenceService.persistBattle(field), times(2));
  }

  @ParameterizedTest
  @CsvSource({"FIELD,23,§7Point View", "SIEGE,23,§7Contest View", "RAID,23,§7Raid Target View", "FIELD,0,§7Side View", "FIELD,7,§7Template Selection"})
  void editorNavigationOpensTheRelevantSetupScreenAndBackReturnsToTheSameBattle(BattleType type, int slot, String title) {
    Battle battle = battle("front", type);
    edit(battle);
    click(slot);
    assertEquals(title, alice.getOpenInventory().getTitle());
    click(26);
    assertEquals("§7Battle View", alice.getOpenInventory().getTitle());
    assertSame(battle, BattleManager.currentBattle.get(alice));
  }

  @Test
  void editorStartRejectsIncompleteSetupAndStartsAndStopsAManualBattle() {
    Battle battle = battle("siege", BattleType.SIEGE);
    edit(battle);
    click(18);
    assertFalse(battle.hasStarted());
    verify(alice).sendMessage(contains("Siege contest area is not configured"));
    battle.setBattleType(BattleType.FIELD);
    band(battle, "attacker", alice);
    band(battle, "defender", player("Bob"));
    click(18);
    assertTrue(battle.hasStarted());
    assertEquals("Crafting", alice.getOpenInventory().getTitle());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
    edit(battle);
    click(18);
    assertFalse(battle.hasStarted());
    assertEquals("§7Battle View", alice.getOpenInventory().getTitle());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle), times(2));
  }

  @Test
  void startingACampaignMarksItsWarAndEndingItClosesTheEditor() {
    Battle battle = battle("campaign", BattleType.FIELD);
    battle.setWarId(12);
    band(battle, "attacker", alice);
    band(battle, "defender", player("Bob"));
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(ui.world, 0, 64, 0));
      side.setJail(new Location(ui.world, 100, 64, 100));
    }
    War war = mock(War.class);
    try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
         MockedStatic<BattleEndSupport> endings = mockStatic(BattleEndSupport.class)) {
      wars.when(() -> WarManager.getById(12)).thenReturn(war);
      endings.when(() -> BattleEndSupport.endBattle(battle, null, BattleEndReason.TIMER))
          .thenAnswer(call -> { battle.end(); return null; });
      edit(battle);
      click(18);
      assertTrue(battle.hasStarted());
      verify(war).setFirstBattleStarted(true);
      wars.verify(() -> WarManager.persist(war));
      edit(battle);
      click(18);
      assertFalse(battle.hasStarted());
      assertFalse(BattleManager.currentBattle.containsKey(alice));
      assertEquals("Crafting", alice.getOpenInventory().getTitle());
      verify(alice).sendMessage("§aBattle ended.");
    }
  }

  @Test
  void deletingAManualBattleClearsAllEditorsWhileCampaignDeletionIsDenied() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setWarId(12);
    edit(battle);
    click(22);
    assertTrue(BattleManager.get().contains(battle));
    verify(alice).sendMessage(contains("Campaign battles cannot be deleted"));
    persistence.verifyNoInteractions();
    battle.setWarId(null);
    battle.setStarted(true);
    click(22);
    assertTrue(BattleManager.get().contains(battle));
    verify(alice).sendMessage("§cCannot edit a battle while it has started");
    persistence.verifyNoInteractions();
    battle.setStarted(false);
    Player bob = player("Bob");
    BattleManager.currentBattle.put(bob, battle);
    BattleManager.currentSideEdit.put(bob, "defender");
    BattleManager.currentSideEdit.put(alice, "attacker");
    click(22);
    assertFalse(BattleManager.get().contains(battle));
    assertTrue(BattleManager.currentBattle.isEmpty());
    assertTrue(BattleManager.currentSideEdit.isEmpty());
    assertEquals("Crafting", alice.getOpenInventory().getTitle());
    persistence.verify(() -> BattlePersistenceService.deleteManualBattle(battle));
  }

  @Test
  void sideSelectionUsesCurrentIdentifiersAndLegacyDisplayNamesWithoutRetargetingRemovedSides() {
    Battle battle = battle("front", BattleType.FIELD);
    edit(battle);
    click(0);
    top().setItem(0, labelled(Material.EMERALD, "§eattacker: §f0"));
    click(0);
    assertEquals("§7Side Edit", alice.getOpenInventory().getTitle());
    assertEquals("attacker", BattleManager.currentSideEdit.get(alice));
    click(26);
    assertEquals("§7Side View", alice.getOpenInventory().getTitle());
    assertFalse(BattleManager.currentSideEdit.containsKey(alice));
    top().setItem(0, labelled(Material.EMERALD, "unknown"));
    click(0);
    assertEquals("§7Side View", alice.getOpenInventory().getTitle());
    top().setItem(0, new ItemStack(Material.STONE));
    assertFalse(top().getItem(0).hasItemMeta());
    click(0);
    assertEquals("§7Side View", alice.getOpenInventory().getTitle());
    assertFalse(BattleManager.currentSideEdit.containsKey(alice));
    top().setItem(0, null);
    click(0);
    menus.sideView(alice, battle);
    battle.removeSide("attacker");
    click(0);
    assertFalse(BattleManager.currentSideEdit.containsKey(alice));
  }

  @Test
  void sideEditorSavesLocationsCapturePointsAndManualLifePoolsAndRejectsStaleState() {
    Battle battle = battle("front", BattleType.FIELD);
    BattleSide side = battle.getSideById("attacker");
    BattleManager.currentBattle.put(alice, battle);
    BattleManager.currentSideEdit.put(alice, "attacker");
    menus.sideEditView(alice, battle, side);
    Location position = alice.getLocation();
    click(10); assertEquals(position, side.getSpawn());
    click(12); assertEquals(position, side.getJail());
    click(14); assertEquals(1, battle.getPoints().size());
    assertSame(side, battle.getPoints().getFirst().getController());
    click(16); assertEquals(50, side.getLives());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle), times(4));
    battle.setWarId(12);
    click(16);
    assertEquals(50, side.getLives());
    verify(alice).sendMessage("§cCampaign battle lives are computed from war commitment");
    battle.setStarted(true);
    click(10);
    verify(alice).sendMessage("§cCannot edit a battle while it has started");
    battle.removeSide("attacker");
    click(10);
    assertFalse(BattleManager.currentSideEdit.containsKey(alice));
    assertEquals("§7Side View", alice.getOpenInventory().getTitle());
  }

  @Test
  void contestEditorStoresBothCornersAndStopsEditingAfterTheBattleStarts() {
    Battle battle = battle("siege", BattleType.SIEGE);
    BattleManager.currentBattle.put(alice, battle);
    menus.contestView(alice, battle);
    Location first = alice.getLocation();
    click(0);
    assertEquals(first, battle.getContestArea().getMin().toBukkitLocation());
    Location second = new Location(ui.world, 8, 70, 9);
    when(alice.getLocation()).thenReturn(second);
    click(1);
    assertEquals(second, battle.getContestArea().getMax().toBukkitLocation());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle), times(2));
    battle.setStarted(true);
    click(0);
    assertEquals(first, battle.getContestArea().getMin().toBukkitLocation());
  }

  @Test
  void pointEditorRemovesOnlyTheSelectedCurrentPointAndRejectsStartedOrUnidentifiedActions() {
    Battle battle = battle("front", BattleType.FIELD);
    runtimePoint(battle);
    BattleManager.currentBattle.put(alice, battle);
    menus.pointView(alice, battle);
    battle.setStarted(true);
    click(0);
    assertEquals(1, battle.getPoints().size());
    verify(alice).sendMessage("§cCannot edit capture points while the battle has started");
    battle.setStarted(false);
    click(0);
    assertTrue(battle.getPoints().isEmpty());
    persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
    click(0);
    assertTrue(battle.getPoints().isEmpty());
  }

  @Test
  void templateSelectionAppliesMatchingRulesResetsBaseAndRejectsRemovedTemplates() {
    Map<String, BattleTemplate> previous = BattleTemplateLoader.getAll();
    BattleTemplateLoader.resetForTests();
    try {
      YamlConfiguration config = new YamlConfiguration();
      config.set("type", "field");
      config.set("lives", 9);
      BattleTemplateLoader.putForTests(new BattleTemplate("plain", config));
      Battle battle = battle("front", BattleType.FIELD);
      runtimePoint(battle);
      BattleManager.currentBattle.put(alice, battle);
      menus.templateView(alice, battle);
      click(1);
      assertEquals("plain", battle.getTemplateName());
      assertEquals(9, battle.getLives());
      assertTrue(battle.getPoints().isEmpty());
      assertEquals("§7Battle View", alice.getOpenInventory().getTitle());
      menus.templateView(alice, battle);
      click(0);
      assertNull(battle.getTemplateName());
      assertEquals(25, battle.getLives());
      menus.templateView(alice, battle);
      BattleTemplateLoader.resetForTests();
      click(1);
      verify(alice).sendMessage("§cUnknown battle template: plain");
      assertNull(battle.getTemplateName());
      top().setItem(1, new ItemStack(Material.STONE));
      click(1);
      top().setItem(1, null);
      click(1);
      battle.setStarted(true);
      click(0);
      verify(alice).sendMessage("§cCannot edit a battle while it has started");
      persistence.verify(() -> BattlePersistenceService.persistBattle(battle), times(2));
    } finally {
      BattleTemplateLoader.resetForTests();
      previous.values().forEach(BattleTemplateLoader::putForTests);
    }
  }

  @Test
  void battleListAcceptsLegacyItemsAndIgnoresUnidentifiedOrRemovedBattles() {
    Battle battle = battle("front", BattleType.FIELD);
    menus.battleList(alice);
    top().setItem(0, labelled(Material.IRON_SWORD, "§e" + battle.getId()));
    click(0);
    assertSame(battle, BattleManager.currentBattle.get(alice));
    menus.battleList(alice);
    top().setItem(0, labelled(Material.IRON_SWORD, "missing"));
    click(0);
    assertEquals("§7Battle List", alice.getOpenInventory().getTitle());
    top().setItem(0, null);
    click(0);
    menus.battleList(alice);
    BattleManager.deleteBattle(battle);
    click(0);
    assertEquals("§7Battle List", alice.getOpenInventory().getTitle());
  }

  @Test
  void gameplaySideSelectionHonorsStartedAndLockedStateAndUsesTheJoinService() {
    Battle battle = battle("front", BattleType.FIELD);
    BattleManager.currentBattle.put(alice, battle);
    menus.sideSelection(alice, battle);
    battle.setStarted(true);
    click(0);
    verify(alice).sendMessage("§cBattle has started");
    battle.setStarted(false);
    click(0);
    verify(alice).sendMessage("§cBattle is locked");
    battle.setLocked(false);
    click(0);
    verify(alice).sendMessage(contains("warband"));
    Warband band = band(null, null, alice);
    click(0);
    assertTrue(battle.getSideById("attacker").getBands().contains(band));
    assertEquals("Crafting", alice.getOpenInventory().getTitle());
    menus.sideSelection(alice, battle);
    click(0);
    assertFalse(battle.getSideById("attacker").getBands().contains(band));
    verify(alice).sendMessage("§cLeft attacker in the battle " + battle.getId());
    menus.sideSelection(alice, battle);
    top().setItem(0, labelled(Material.EMERALD, "unknown"));
    click(0);
    top().setItem(0, null);
    click(0);
  }

  @Test
  void respawnSelectionUsesTheLivePointAndRejectsUncontrolledOrRemovedChoices() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setStarted(true);
    Warband band = band(battle, "attacker", alice);
    CapturePoint point = runtimePoint(battle);
    point.setCaptureProgress(49);
    menus.spawnList(alice, battle);
    click(0);
    verify(alice).sendMessage("§cPoint not controlled");
    assertTrue(ui.repeatingTasks.isEmpty());
    point.setCaptureProgress(100);
    menus.spawnList(alice, battle);
    click(0);
    assertEquals(1, ui.repeatingTasks.size());
    assertEquals("Crafting", alice.getOpenInventory().getTitle());
    menus.spawnList(alice, battle);
    band.removePlayer(alice);
    click(0);
    assertEquals(1, ui.repeatingTasks.size());
    band.addPlayer(alice);
    menus.spawnList(alice, battle);
    battle.getPointManager().getPoints().clear();
    click(0);
    assertEquals(1, ui.repeatingTasks.size());
  }

  @Test
  void choosingTheSecondRespawnPointTeleportsToThatPointAfterTheCountdown() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setStarted(true);
    band(battle, "attacker", alice);
    BattleSide side = battle.getSideById("attacker");
    Location firstPosition = new Location(ui.world, 10, 64, 10);
    Location secondPosition = new Location(ui.world, 30, 64, 30);
    battle.addPoint(new CapturePoint("A", firstPosition, side, 100));
    battle.addPoint(new CapturePoint("B", secondPosition, side, 100));
    battle.getPointManager().setPoints(battle.getPoints());
    menus.spawnList(alice, battle);
    click(1);
    assertEquals(1, ui.repeatingTasks.size());
    for (int tick = 0; tick < 16; tick++) ui.repeatingTasks.getFirst().run();
    verify(alice).teleport(secondPosition);
    verify(alice, never()).teleport(firstPosition);
  }

  @Test
  void respawnMenuOpenedBeforeRestartCannotSelectAReplacementPointWithTheSameLetter() {
    Battle battle = battle("front", BattleType.FIELD);
    band(battle, "attacker", alice);
    band(battle, "defender", player("Bob"));
    CapturePoint original = runtimePoint(battle);
    battle.setStarted(true);
    menus.spawnList(alice, battle);
    battle.end();
    battle.getPoints().clear();
    battle.addPoint(new CapturePoint("A", new Location(ui.world, 80, 64, 80),
        battle.getSideById("attacker"), 100));
    assertNull(battle.start());
    assertTrue(battle.hasStarted());
    assertFalse(battle.getPointManager().getPoints().contains(original));
    click(0);
    assertTrue(ui.repeatingTasks.isEmpty(), "the old menu must not select a newly started round's point");
    verify(alice, never()).teleport(any(Location.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"§7Battle View", "§7Side View", "§7Side Edit", "§7Point View", "§7Contest View", "§7Raid Target View", "§7Template Selection", "§7Side Selection"})
  void staleEditorSessionsCannotChangeBattleState(String title) {
    Battle battle = battle("front", BattleType.FIELD);
    Inventory inventory = ui.inventory(null, 27, title);
    inventory.setItem(0, new ItemStack(Material.STONE));
    alice.openInventory(inventory);
    click(0);
    assertEquals(2, battle.getSides().size());
    assertFalse(battle.hasStarted());
    assertTrue(BattleManager.currentBattle.isEmpty());
    persistence.verifyNoInteractions();
  }

  @Test
  void unrecognizedInventoriesAndUnsafeClickTypesDoNotRunBattleActions() {
    Inventory other = ui.inventory(null, 9, "Ordinary chest");
    alice.openInventory(other);
    InventoryClickEvent untouched = ui.click(alice, 0);
    listener.invenClick(untouched);
    assertFalse(untouched.isCancelled());
    Battle battle = battle("front", BattleType.FIELD);
    edit(battle);
    InventoryClickEvent shift = new InventoryClickEvent(alice.getOpenInventory(), InventoryType.SlotType.CONTAINER,
        1, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
    listener.invenClick(shift);
    assertTrue(shift.isCancelled());
    assertTrue(battle.isLocked());
    InventoryClickEvent outside = ui.click(alice, -999);
    listener.invenClick(outside);
    assertTrue(outside.isCancelled());
    persistence.verifyNoInteractions();
  }

}
