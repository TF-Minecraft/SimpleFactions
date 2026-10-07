package net.tfminecraft.simplefactions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.espionage.SpecialPositionsConfigFile;
import net.tfminecraft.simplefactions.guild.hub.VehicleFrameworkTrackProvinces;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.identity.LeaderCharacterListener;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.integration.rpcharacters.chat.RpCharactersChatIntegration;
import net.tfminecraft.simplefactions.loaders.ConfigLoader;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.ProvinceLoader;
import net.tfminecraft.simplefactions.loaders.RegionLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.LedgerCommandManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceCache;
import net.tfminecraft.simplefactions.map.presence.ProvincePresenceService;
import net.tfminecraft.simplefactions.map.presence.ProvincePresenceTickService;
import net.tfminecraft.simplefactions.mercenary.stat.MercenaryStatService;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.vehicles.VehicleIntegrationListener;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePersistence;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryPersistence;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplateService;
import net.tfminecraft.simplefactions.war.battle.ui.BattleCommandManager;
import net.tfminecraft.simplefactions.war.campaign.raid.RaidCommandManager;
import net.tfminecraft.simplefactions.war.campaign.raid.intruder.CampaignRaidIntruderService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleTickService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignViewRefreshService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.freeze.VfBuildersConstructionFreeze;
import net.tfminecraft.vehicleframework.data.OwnedVehicleSummary;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class PluginLifecycleCoverageTest {
  private FactionDomainFixture domain;
  private PersistenceFilesFixture disk;
  private SimpleFactions plugin;
  private PluginManager plugins;
  private Logger logger;
  private FileConfiguration oldConfig;
  private boolean oldMapEnabled;
  private final Map<Class<?>, MockedStatic<?>> statics = new LinkedHashMap<>();
  private final Map<String, Object> fields = new LinkedHashMap<>();
  private final Map<String, PluginCommand> commands = new LinkedHashMap<>();
  private final List<Listener> listeners = new ArrayList<>();

  @SuppressWarnings("unchecked")
  private <T> MockedStatic<T> scoped(Class<T> type) {
    return (MockedStatic<T>) statics.computeIfAbsent(type, key -> mockStatic(type));
  }

  private void field(String name, Object value) throws Exception {
    Field field = SimpleFactions.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(plugin, value);
    fields.put(name, value);
  }

  private <T> T dependency(String name, Class<T> type) {
    return type.cast(fields.get(name));
  }

  @BeforeEach
  void setup() throws Exception {
    disk = new PersistenceFilesFixture();
    domain = new FactionDomainFixture();
    oldConfig = SimpleFactions.config;
    oldMapEnabled = Cache.mapEnabled;
    Cache.mapEnabled = false;
    plugin = mock(SimpleFactions.class, CALLS_REAL_METHODS);
    plugins = mock(PluginManager.class);
    logger = mock(Logger.class);
    doReturn(disk.root.toFile()).when(plugin).getDataFolder();
    doReturn(domain.ui.server).when(plugin).getServer();
    doReturn(logger).when(plugin).getLogger();
    doReturn("SimpleFactions").when(plugin).getName();
    doReturn("simplefactions").when(plugin).namespace();
    doReturn(new YamlConfiguration()).when(plugin).getConfig();
    doAnswer(
            call ->
                commands.computeIfAbsent(call.getArgument(0), name -> mock(PluginCommand.class)))
        .when(plugin)
        .getCommand(anyString());
    when(domain.ui.server.getPluginManager()).thenReturn(plugins);
    doAnswer(
            call -> {
              listeners.add(call.getArgument(0));
              return null;
            })
        .when(plugins)
        .registerEvents(any(), eq(plugin));
    doAnswer(
            call -> {
              String name = call.getArgument(0);
              Path destination = disk.root.resolve(name);
              try (var input = SimpleFactions.class.getClassLoader().getResourceAsStream(name)) {
                assertNotNull(input, "Bundled config " + name);
                Files.copy(input, destination);
              }
              return null;
            })
        .when(plugin)
        .saveResource(anyString(), eq(false));
    // Substitute lifecycle boundaries, keeping the plugin's orchestration and callbacks real.
    for (Field field : SimpleFactions.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
      if (Set.of(
              "provinceGrid",
              "trackRefreshTask",
              "vehicleRegistryPersistence",
              "vehicleMaintenancePersistence")
          .contains(field.getName())) continue;
      field(field.getName(), mock(field.getType()));
    }
    field("commands", new CommandManager());
    field(
        "ledgerCommandManager",
        new LedgerCommandManager(dependency("inventoryManager", InventoryManager.class)));
    field("battleCommandManager", new BattleCommandManager());
    field("raidCommandManager", new RaidCommandManager());
    field("vehicleRegistry", new PlayerVehicleRegistry());
    field("vehicleFeeStore", new VehicleFeeStore());
    field("vehicleMaintenanceStore", new VehicleMaintenanceStore());
    for (Class<?> type :
        List.of(
            InactivityService.class,
            EspionageService.class,
            SpecialPositionsConfigFile.class,
            VehiclesConfigLoader.class,
            InstallationConfigLoader.class,
            RegionLoader.class,
            FactionManager.class,
            WarManager.class,
            RequestManager.class,
            ProvincePresenceTickService.class,
            CampaignRaidIntruderService.Tick.class,
            BattlePersistenceService.class,
            BattleScheduleTickService.class,
            CampaignViewRefreshService.class,
            BattleManager.class,
            MercenaryStatService.class,
            TradeGraph.class,
            RpCharactersChatIntegration.class,
            VfBuildersConstructionFreeze.class,
            LeaderCharacterListener.class)) scoped(type);
    SimpleFactions.plugin = plugin;
  }

  @AfterEach
  void close() throws Exception {
    try {
      List<MockedStatic<?>> values = new ArrayList<>(statics.values());
      for (int i = values.size() - 1; i >= 0; i--) values.get(i).close();
      SimpleFactions.config = oldConfig;
      Cache.mapEnabled = oldMapEnabled;
      domain.close();
    } finally {
      disk.close();
    }
  }

  @Test
  void startupRegistersGameplayAndShutdownSavesLoadedState() throws Exception {
    plugin.onEnable();
    assertSame(plugin, SimpleFactions.getInstance());
    assertSame(plugin.getConfig(), SimpleFactions.config);
    for (String name :
        List.of(
            "faction",
            "guild",
            "ledger",
            "company",
            "mercenaries",
            "warband",
            "battle",
            "raid",
            "war",
            "movement")) {
      assertTrue(
          commands.containsKey(name),
          "Registered command " + name + "; actual " + commands.keySet());
    }
    verify(commands.get("battle"))
        .setExecutor(dependency("battleCommandManager", BattleCommandManager.class));
    verify(dependency("factionManager", FactionManager.class)).run();
    verify(dependency("inventoryManager", InventoryManager.class)).start();
    verify(dependency("sessionManager", SessionManager.class)).start();
    verify(dependency("battleManager", BattleManager.class)).start();
    assertTrue(listeners.contains(dependency("inventoryManager", InventoryManager.class)));
    assertTrue(Files.isRegularFile(disk.root.resolve("config.yml")));
    assertTrue(Files.isRegularFile(disk.root.resolve("Guilds/guild-types.yml")));
    plugin.createFolders();
    plugin.createConfigs();
    scoped(FactionManager.class).when(FactionManager::isLoaded).thenReturn(true);
    scoped(FactionManager.class).when(FactionManager::getTimer).thenReturn(100);
    scoped(FactionManager.class).when(FactionManager::getDay).thenReturn(4);
    Faction faction = mock(Faction.class);
    FactionManager.factions.add(faction);
    War war = mock(War.class);
    scoped(WarManager.class).when(WarManager::get).thenReturn(List.of(war));
    SimpleFactions.getVehicleRegistry()
        .register(
            new PlayerVehicleRecord(
                UUID.randomUUID(), "cart-1", "cart", OwnershipMode.PERSONAL, "dock"));
    plugin.onDisable();
    Database db = dependency("db", Database.class);
    verify(db).saveTimer(100, 4);
    verify(db).saveFaction(faction);
    verify(db).saveWar(war);
    verify(dependency("sessionManager", SessionManager.class)).end();
    assertTrue(Files.isRegularFile(disk.root.resolve("Cache/vehicles_registry.json")));
    scoped(BattlePersistenceService.class).verify(BattlePersistenceService::saveAll);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aptitudes", "provinces", "grid"})
  void failedStartupDisablesThePluginBeforeStartingRuntimeLoops(String step) throws Exception {
    Cache.provincesEnabled = true;
    try (var grids = mockStatic(ProvinceGrid.class)) {
      if (step.equals("aptitudes"))
        scoped(EspionageService.class)
            .when(() -> EspionageService.loadAptitudes(any()))
            .thenThrow(new IllegalStateException("bad aptitude file"));
      if (step.equals("provinces"))
        when(dependency("provinceLoader", ProvinceLoader.class).loadProvinces(any(), any()))
            .thenThrow(new IllegalStateException("bad province input"));
      if (step.equals("grid"))
        grids.when(() -> ProvinceGrid.load(any())).thenThrow(new IllegalStateException("bad grid"));
      plugin.onEnable();
      verify(plugins).disablePlugin(plugin);
      verify(dependency("factionManager", FactionManager.class), never()).run();
      verify(dependency("inventoryManager", InventoryManager.class), never()).start();
      plugin.onDisable();
      verify(dependency("db", Database.class), never()).saveTimer(anyInt(), anyInt());
      verify(dependency("db", Database.class), never()).saveFaction(any());
    }
  }

  @Test
  void enabledProvinceGridSchedulesRefreshAndCancelsItAtShutdown() throws Exception {
    Cache.provincesEnabled = true;
    Cache.mapEnabled = true;
    ProvinceGrid grid = mock(ProvinceGrid.class);
    when(grid.getWidth()).thenReturn(20);
    when(grid.getHeight()).thenReturn(10);
    TrackProvinceCache cache = new TrackProvinceCache();
    try (var grids = mockStatic(ProvinceGrid.class);
        var tracks = mockStatic(TrackProvinceCache.class)) {
      grids.when(() -> ProvinceGrid.load(any())).thenReturn(grid);
      tracks.when(TrackProvinceCache::live).thenReturn(cache);
      plugin.onEnable();
      assertSame(grid, plugin.getProvinceGrid());
      verify(logger).info("Loaded province_id_grid 20x10");
      verify(logger).severe(contains("TFMCWeb is not loaded"));
      verify(logger).info(contains("VehicleFramework is not enabled"));
      scoped(ProvincePresenceTickService.class).verify(ProvincePresenceTickService::start);
      assertEquals(1, domain.ui.repeatingTasks.size());
      domain.ui.repeatingTasks.getFirst().run();
      plugin.onDisable();
      var taskField = SimpleFactions.class.getDeclaredField("trackRefreshTask");
      taskField.setAccessible(true);
      verify((BukkitTask) taskField.get(plugin)).cancel();
    }
  }

  @Test
  void loadAndReloadPreserveConfigurationOrderAndRefreshDerivedFactionState() throws Exception {
    plugin.createFolders();
    plugin.createConfigs();
    plugin.loadConfigs();
    verify(dependency("configLoader", ConfigLoader.class))
        .loadConfig(disk.root.resolve("config.yml").toFile());
    verify(dependency("configLoader", ConfigLoader.class))
        .loadWar(disk.root.resolve("war.yml").toFile());
    verify(dependency("guildLoader", GuildLoader.class))
        .load(disk.root.resolve("Guilds/guild-types.yml").toFile());
    SimpleFactions.reloadConfigs();
    scoped(FactionManager.class).verify(FactionManager::rebindRanks);
    scoped(FactionManager.class).verify(FactionManager::rebindDiplomacy);
    scoped(FactionManager.class).verify(FactionManager::reloadTitles);
    scoped(FactionManager.class).verify(FactionManager::updateAllPrestigeConverged);
    SimpleFactions.reloadTitles();
    scoped(RegionLoader.class).verify(RegionLoader::loadAll, times(3));
  }

  @Test
  void paperClassLoaderConstructsTheRealPluginAndItsServiceGraph() throws Exception {
    PluginDescriptionFile description =
        new PluginDescriptionFile("SimpleFactions", "test", SimpleFactions.class.getName());
    try (var services = mockStatic(net.kyori.adventure.util.Services.class, CALLS_REAL_METHODS)) {
      when(Bukkit.getUnsafe()).thenReturn(mock(org.bukkit.UnsafeValues.class, RETURNS_DEEP_STUBS));
      services
          .when(() -> net.kyori.adventure.util.Services.service(PluginLoader.class))
          .thenReturn(Optional.of(mock(PluginLoader.class)));
      TestPluginLoader loader =
          new TestPluginLoader(description, domain.ui.server, logger, disk.root);
      JavaPlugin constructed =
          (JavaPlugin)
              loader.loadClass(SimpleFactions.class.getName()).getConstructor().newInstance();
      assertSame(constructed, loader.getPlugin());
      assertEquals("SimpleFactions", constructed.getName());
      assertSame(description, constructed.getPluginMeta());
      assertEquals(disk.root.resolve("data").toFile(), constructed.getDataFolder());
      SessionManager sessions =
          (SessionManager)
              constructed.getClass().getMethod("getSessionManager").invoke(constructed);
      assertNotNull(sessions);
      assertSame(
          sessions, constructed.getClass().getMethod("getSessionManager").invoke(constructed));
      constructed.onDisable();
      scoped(BattleManager.class).verify(BattleManager::shutdown);
    }
  }

  @Test
  void enabledIntegrationsRegisterOnceAndHandleLateDependencyEvents() throws Exception {
    Plugin items = mock(Plugin.class);
    when(plugins.getPlugin("ItemsAdder")).thenReturn(items);
    when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
    when(plugins.isPluginEnabled("VehicleFramework")).thenReturn(true);
    when(plugins.isPluginEnabled("VFBuilders")).thenReturn(true);
    plugin.onEnable();
    verify(plugins)
        .registerEvents(
            dependency("vehicleIntegrationListener", VehicleIntegrationListener.class), plugin);
    long office =
        listeners.stream()
            .filter(
                net.tfminecraft.simplefactions.espionage.OfficeCharacterDeathListener.class
                    ::isInstance)
            .count();
    assertEquals(1, office);
    assertTrue(
        listeners.stream()
            .anyMatch(
                net.tfminecraft.simplefactions.managers.VotingBoothListener.class::isInstance));
    assertTrue(
        listeners.stream()
            .anyMatch(
                net.tfminecraft.simplefactions.vehicles.fees.VehicleRegistrationFeeListener.class
                    ::isInstance));
    scoped(VfBuildersConstructionFreeze.class)
        .verify(() -> VfBuildersConstructionFreeze.register(plugin));
    for (String dependency :
        List.of("Unrelated", "RPCharacters", "VFBuilders", "VehicleFramework")) {
      Plugin enabled = mock(Plugin.class);
      when(enabled.getName()).thenReturn(dependency);
      for (Listener listener : new ArrayList<>(listeners)) {
        if (listener.getClass().getEnclosingClass() != SimpleFactions.class) continue;
        var method = listener.getClass().getMethod("onPluginEnable", PluginEnableEvent.class);
        method.setAccessible(true);
        method.invoke(listener, new PluginEnableEvent(enabled));
      }
    }
    verify(plugins, times(1))
        .registerEvents(
            dependency("vehicleIntegrationListener", VehicleIntegrationListener.class), plugin);
    assertEquals(
        1,
        listeners.stream()
            .filter(
                net.tfminecraft.simplefactions.espionage.OfficeCharacterDeathListener.class
                    ::isInstance)
            .count());
    domain.ui.runTasks();
    scoped(LeaderCharacterListener.class)
        .verify(() -> LeaderCharacterListener.refresh(null), atLeastOnce());
    scoped(RpCharactersChatIntegration.class)
        .verify(RpCharactersChatIntegration::register, atLeastOnce());
  }

  @Test
  void ownerAndMaintenancePersistenceUsesTheExposedLiveStores() throws Exception {
    assertTrue(plugin.saveVehicleRegistry());
    plugin.onEnable();
    plugin.getVehicleMaintenanceStore().markUnpaid("cart-1", 1234L);
    plugin.recordVehicleOwner("cart-1", "Alice");
    plugin.recordVehicleOwners(); // A disabled VehicleFramework leaves existing owner history intact.
    plugin.saveVehicleFees();
    assertTrue(plugin.saveVehicleRegistry());
    VehicleFeeStore fees = new VehicleFeeStore();
    fees.bind(disk.root.resolve("Cache").toFile());
    fees.load();
    assertEquals("Alice", fees.getLastOwner("cart-1"));
    VehicleMaintenanceStore restored = new VehicleMaintenanceStore();
    new VehicleMaintenancePersistence(disk.root.resolve("Cache").toFile(), restored).load();
    assertTrue(restored.isUnpaid("cart-1"));
    PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
    registry.register(
        new PlayerVehicleRecord(
            UUID.randomUUID(), "berthed", "cart", OwnershipMode.PERSONAL, "dock"));
    plugin.recordVehicleOwner("berthed", "PreviousOwner");
    when(plugins.isPluginEnabled("VehicleFramework")).thenReturn(true);
    try (var queries = mockStatic(VehicleOwnershipQueries.class, CALLS_REAL_METHODS)) {
      OwnedVehicleSummary personal = mock(OwnedVehicleSummary.class),
          unowned = mock(OwnedVehicleSummary.class);
      when(personal.getUuid()).thenReturn("personal");
      when(personal.getOwner()).thenReturn("player_Bob");
      when(unowned.getUuid()).thenReturn("unowned");
      when(unowned.getOwner()).thenReturn("player_none");
      queries
          .when(() -> VehicleOwnershipQueries.allPersonalVehicles(registry))
          .thenReturn(List.of(personal, unowned));
      plugin.recordVehicleOwners();
      fees.load();
      assertEquals("Bob", fees.getLastOwner("personal"));
      assertNull(fees.getLastOwner("berthed"));
      queries
          .when(() -> VehicleOwnershipQueries.allPersonalVehicles(registry))
          .thenThrow(new LinkageError("old VehicleFramework"));
      assertDoesNotThrow(plugin::recordVehicleOwners);
      verify(logger).warning(contains("Could not record vehicle owners"));
    }
    var persistence = mock(VehicleRegistryPersistence.class);
    when(persistence.save()).thenReturn(false);
    field("vehicleRegistryPersistence", persistence);
    assertFalse(plugin.saveVehicleRegistry());
  }

  @Test
  void publicServicesExposeTheSameInstancesUsedByStartupListeners() {
    assertSame(fields.get("provinceManager"), plugin.getProvinceManager());
    assertSame(fields.get("provinceSnapshot"), plugin.getProvinceSnapshot());
    assertSame(fields.get("sessionManager"), plugin.getSessionManager());
    assertSame(
        fields.get("vehicleTransferSessionManager"), plugin.getVehicleTransferSessionManager());
    assertSame(
        fields.get("vehicleReleaseSessionManager"), plugin.getVehicleReleaseSessionManager());
    assertSame(
        fields.get("vehicleMaintenancePaySessionManager"),
        plugin.getVehicleMaintenancePaySessionManager());
    assertSame(
        fields.get("vehicleTransferConsentService"), plugin.getVehicleTransferConsentService());
    assertSame(fields.get("factionVehicleGiveService"), plugin.getFactionVehicleGiveService());
    assertSame(
        fields.get("vehicleHandoverSessionManager"), plugin.getVehicleHandoverSessionManager());
    assertSame(fields.get("vehicleHandoverService"), plugin.getVehicleHandoverService());
    assertSame(
        fields.get("installationVehicleUnberthService"),
        plugin.getInstallationVehicleUnberthService());
    assertSame(fields.get("playerEconomyManager"), SimpleFactions.getPlayerEconomyManager());
    assertSame(fields.get("vehicleUpkeepService"), plugin.getVehicleUpkeepService());
    assertSame(ProvincePresenceService.getInstance(), plugin.getProvincePresenceService());
    assertSame(BattleTemplateService.getInstance(), plugin.getBattleTemplateService());
    assertEquals(Cache.maxExtraNodeCapacity, SimpleFactions.getMaxExtraNodeCapacity());
  }

  @Test
  void trackChangesInvalidateRoutesBeforeRecalculationAndAReadFailureClearsTheSnapshot()
      throws Exception {
    TrackProvinceCache cache = new TrackProvinceCache();
    ProvinceGrid grid = mock(ProvinceGrid.class);
    var province = mock(net.tfminecraft.simplefactions.map.provinces.Province.class);
    when(province.getId()).thenReturn(17);
    ProvinceManager manager = plugin.getProvinceManager();
    when(manager.getProvinces()).thenReturn(List.of(province));
    when(plugins.isPluginEnabled("VehicleFramework")).thenReturn(true);
    try (var tracks = mockStatic(TrackProvinceCache.class);
        var source = mockStatic(VehicleFrameworkTrackProvinces.class)) {
      tracks.when(TrackProvinceCache::live).thenReturn(cache);
      assertFalse(plugin.refreshTrackProvinces());
      field("provinceGrid", grid);
      source
          .when(
              () ->
                  VehicleFrameworkTrackProvinces.sample(
                      eq(Cache.worldName), same(grid), eq(Map.of(17, province))))
          .thenReturn(Set.of(17));
      doAnswer(
              call -> {
                scoped(TradeGraph.class).verify(TradeGraph::forgetRoutes, atLeastOnce());
                scoped(TradeGraph.class).verify(() -> TradeGraph.refresh(manager), atLeastOnce());
                return null;
              })
          .when(manager)
          .recalculate();
      assertTrue(plugin.refreshTrackProvinces());
      assertEquals(Set.of(17), cache.provinces());
      assertFalse(plugin.refreshTrackProvinces());
      verify(manager, times(1)).recalculate();
      source
          .when(() -> VehicleFrameworkTrackProvinces.sample(any(), same(grid), any()))
          .thenThrow(new LinkageError("old tracks API"));
      assertTrue(plugin.refreshTrackProvinces());
      assertTrue(cache.provinces().isEmpty());
      assertFalse(plugin.refreshTrackProvinces());
      verify(manager, times(2)).recalculate();
      verify(logger, times(1)).warning(contains("Could not read VehicleFramework tracks"));
    }
  }

  @Test
  void lateVehicleAndCharacterEnableEventsRegisterTheIntegrationsAndRefreshTheMap()
      throws Exception {
    plugin.onEnable();
    verify(plugins, never())
        .registerEvents(
            dependency("vehicleIntegrationListener", VehicleIntegrationListener.class), plugin);
    scoped(FactionManager.class).when(FactionManager::getMap).thenReturn(domain.map);
    when(plugins.isPluginEnabled("VehicleFramework")).thenReturn(true);
    when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
    for (String name : List.of("VehicleFramework", "RPCharacters")) {
      Plugin enabled = mock(Plugin.class);
      when(enabled.getName()).thenReturn(name);
      for (Listener listener : new ArrayList<>(listeners)) {
        if (listener.getClass().getEnclosingClass() != SimpleFactions.class) continue;
        var method = listener.getClass().getMethod("onPluginEnable", PluginEnableEvent.class);
        method.setAccessible(true);
        method.invoke(listener, new PluginEnableEvent(enabled));
      }
    }
    verify(plugins)
        .registerEvents(
            dependency("vehicleIntegrationListener", VehicleIntegrationListener.class), plugin);
    verify(logger).info("[SimpleFactions] VehicleFramework vehicle integration enabled");
    domain.ui.runTasks();
    verify(domain.map).markLeaderNamesChanged();
  }

  @Test
  void olderVfBuildersWithoutOptionalApisStillAllowsThePluginToStart() throws Exception {
    PluginDescriptionFile description =
        new PluginDescriptionFile("SimpleFactions", "test", SimpleFactions.class.getName());
    try (var services = mockStatic(net.kyori.adventure.util.Services.class, CALLS_REAL_METHODS)) {
      when(Bukkit.getUnsafe()).thenReturn(mock(org.bukkit.UnsafeValues.class, RETURNS_DEEP_STUBS));
      services
          .when(() -> net.kyori.adventure.util.Services.service(PluginLoader.class))
          .thenReturn(Optional.of(mock(PluginLoader.class)));
      TestPluginLoader loader =
          new TestPluginLoader(description, domain.ui.server, logger, disk.root);
      loader.unavailable =
          Set.of(
              "net.tfminecraft.vfbuilders.api.ConstructionFreeze",
              "net.tfminecraft.vfbuilders.events.VehicleConstructionCancelEvent");
      Class<?> type = loader.loadClass(SimpleFactions.class.getName());
      JavaPlugin legacy = spy((JavaPlugin) type.getConstructor().newInstance());
      doReturn(disk.root.toFile()).when(legacy).getDataFolder();
      doReturn(new YamlConfiguration()).when(legacy).getConfig();
      doAnswer(
              call ->
                  commands.computeIfAbsent(call.getArgument(0), name -> mock(PluginCommand.class)))
          .when(legacy)
          .getCommand(anyString());
      doAnswer(
              call -> {
                plugin.saveResource(call.getArgument(0), false);
                return null;
              })
          .when(legacy)
          .saveResource(anyString(), eq(false));
      for (var entry : fields.entrySet()) {
        Field target = type.getDeclaredField(entry.getKey());
        target.setAccessible(true);
        target.set(legacy, entry.getValue());
      }
      when(plugins.isPluginEnabled("VFBuilders")).thenReturn(true);
      when(plugins.isPluginEnabled("VehicleFramework")).thenReturn(true);
      legacy.onEnable();
      verify(dependency("factionManager", FactionManager.class)).run();
      verify(plugins, never()).disablePlugin(legacy);
      verify(logger).warning(contains("VFBuilders is older than 2.1.0"));
      verify(logger).warning(contains("VFBuilders has no ConstructionFreeze API"));
      scoped(VfBuildersConstructionFreeze.class).verifyNoInteractions();
      legacy.onDisable();
    }
  }

  /** Loads unchanged plugin bytes through Paper's real JavaPlugin initialization contract. */
  private static final class TestPluginLoader extends ClassLoader
      implements ConfiguredPluginClassLoader {
    private final PluginDescriptionFile description;
    private final Server server;
    private final Logger logger;
    private final Path directory;
    private JavaPlugin plugin;
    private Set<String> unavailable = Set.of();

    TestPluginLoader(
        PluginDescriptionFile description, Server server, Logger logger, Path directory) {
      super(SimpleFactions.class.getClassLoader());
      this.description = description;
      this.server = server;
      this.logger = logger;
      this.directory = directory;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (unavailable.contains(name)) throw new ClassNotFoundException(name);
      if (!name.equals(SimpleFactions.class.getName())
          && !name.startsWith(SimpleFactions.class.getName() + "$"))
        return super.loadClass(name, resolve);
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
          if (input == null) throw new ClassNotFoundException(name);
          byte[] bytes = input.readAllBytes();
          loaded =
              defineClass(name, bytes, 0, bytes.length, SimpleFactions.class.getProtectionDomain());
        } catch (IOException error) {
          throw new ClassNotFoundException(name, error);
        }
      }
      if (resolve) resolveClass(loaded);
      return loaded;
    }

    @Override
    public PluginMeta getConfiguration() {
      return description;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve, boolean global, boolean libraries)
        throws ClassNotFoundException {
      return loadClass(name, resolve);
    }

    @Override
    public void init(JavaPlugin value) {
      plugin = value;
      value.init(
          server,
          description,
          directory.resolve("data").toFile(),
          directory.resolve("plugin.jar").toFile(),
          this,
          description,
          logger);
    }

    @Override
    public JavaPlugin getPlugin() {
      return plugin;
    }

    @Override
    public PluginClassLoaderGroup getGroup() {
      return null;
    }

    @Override
    public void close() {}
  }
}
