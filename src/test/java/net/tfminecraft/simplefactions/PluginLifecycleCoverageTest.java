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
import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.WarbandData;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.espionage.SpecialPositionsConfigFile;
import net.tfminecraft.simplefactions.guild.hub.VehicleFrameworkTrackProvinces;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.identity.LeaderCharacterListener;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.integration.rpcharacters.chat.RpCharactersChatIntegration;
import net.tfminecraft.simplefactions.loaders.BattleTemplateLoader;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.CompanyUpgradeLoader;
import net.tfminecraft.simplefactions.loaders.ConfigLoader;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.ProvinceLoader;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.loaders.RegionLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
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
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplateService;
import net.tfminecraft.simplefactions.war.battle.ui.BattleCommandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
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
    scoped(BattlePersistenceService.class).verify(BattlePersistenceService::stopAutosave);
    scoped(BattleManager.class).verify(BattleManager::shutdown);
    scoped(CampaignViewRefreshService.class).verify(CampaignViewRefreshService::stop);
  }

  private Map<Path, String> unloadedSavedState() throws Exception {
    var faction = domain.data("prior", "PriorLeader");
    faction.titles = List.of("prior_county");
    faction.military = List.of("guard.12.3");
    BattleData battle = new BattleData();
    battle.id = "prior_battle";
    battle.battleType = "field";
    WarbandData warband = new WarbandData();
    warband.id = "prior_warband";
    warband.leaderId = UUID.randomUUID().toString();
    warband.memberIds.add(warband.leaderId);
    Map<Path, String> saved = new LinkedHashMap<>();
    for (var entry :
        Map.of(
                "Data/prior.json", JsonUtil.GSON.toJson(faction),
                "Battles/battle_prior_battle.json", JsonUtil.GSON.toJson(battle),
                "Warbands/warband_prior_warband.json", JsonUtil.GSON.toJson(warband),
                "Cache/data.json", "{\"time\":450,\"day\":12}")
            .entrySet()) {
      saved.put(disk.write(entry.getKey(), entry.getValue()), entry.getValue());
    }
    return saved;
  }

  private void assertSavedStateUnchanged(Map<Path, String> saved) {
    assertAll(
        saved.entrySet().stream()
            .map(
                entry ->
                    () -> {
                      assertTrue(
                          Files.isRegularFile(entry.getKey()),
                          "Startup failure removed " + entry.getKey());
                      assertEquals(
                          entry.getValue(),
                          Files.readString(entry.getKey()),
                          "Startup failure rewrote " + entry.getKey());
                    }));
  }

  @ParameterizedTest
  @ValueSource(strings = {"titles", "regiments"})
  void malformedInitialDefinitionsAbortBeforeGameplayAndPreserveSavedState(String catalog)
      throws Exception {
    Map<Path, String> saved = unloadedSavedState();
    if (catalog.equals("titles")) {
      disk.write("Input/county.json", "{ malformed");
    } else {
      field("regimentLoader", new RegimentLoader());
      disk.write("regiments.yml", "guard: [unterminated\n");
    }
    scoped(BattlePersistenceService.class)
        .when(BattlePersistenceService::saveAll)
        .thenCallRealMethod();
    try (var bands = mockStatic(WarbandManager.class)) {
      assertDoesNotThrow(plugin::onEnable);
      assertDoesNotThrow(plugin::onDisable);
      assertAll(
          () -> verify(plugins).disablePlugin(plugin),
          () ->
              assertTrue(
                  listeners.isEmpty(), "Gameplay listeners must wait for valid configuration"),
          () -> verify(dependency("db", Database.class), never()).loadFactions(),
          () -> verify(dependency("factionManager", FactionManager.class), never()).run(),
          () -> verify(dependency("inventoryManager", InventoryManager.class), never()).start(),
          () ->
              scoped(BattlePersistenceService.class)
                  .verify(BattlePersistenceService::saveAll, never()),
          () -> scoped(InactivityService.class).verify(InactivityService::save, never()),
          () -> assertSavedStateUnchanged(saved));
    }
    assertTrue(TitleLoader.getTitles().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"aptitudes", "provinces", "grid"})
  void failedStartupDisablesThePluginBeforeStartingRuntimeLoops(String step) throws Exception {
    Cache.provincesEnabled = true;
    Map<Path, String> saved = unloadedSavedState();
    scoped(BattlePersistenceService.class)
        .when(BattlePersistenceService::saveAll)
        .thenCallRealMethod();
    try (var grids = mockStatic(ProvinceGrid.class);
        var bands = mockStatic(WarbandManager.class)) {
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
      assertSavedStateUnchanged(saved);
      verify(dependency("db", Database.class), never()).saveTimer(anyInt(), anyInt());
      verify(dependency("db", Database.class), never()).saveFaction(any());
      verify(dependency("db", Database.class), never()).saveWar(any());
      scoped(BattlePersistenceService.class).verify(BattlePersistenceService::saveAll, never());
      scoped(InactivityService.class).verify(InactivityService::save, never());
      scoped(BattlePersistenceService.class).verify(BattlePersistenceService::stopAutosave);
      verify(dependency("sessionManager", SessionManager.class)).end();
    }
  }

  @Test
  void earlyStartupFailureCannotPruneFilesThatHaveNotBeenLoaded() throws Exception {
    Map<Path, String> saved = unloadedSavedState();
    statics.remove(BattlePersistenceService.class).close();
    scoped(EspionageService.class)
        .when(() -> EspionageService.loadAptitudes(any()))
        .thenThrow(new IllegalStateException("bad aptitude file"));
    try (var bands = mockStatic(WarbandManager.class)) {
      plugin.onEnable();
      verify(plugins).disablePlugin(plugin);
      plugin.onDisable();
      assertSavedStateUnchanged(saved);
    }
  }

  @Test
  void failedTitleReloadRetainsTheLiveDefinitionAndKeepsTheRunningPluginEnabled() throws Exception {
    Path title =
        disk.write(
            "Input/county.json",
            "{\"prior_county\":{\"name\":\"Prior County\",\"provinces\":[3]}}");
    plugin.onEnable();
    var original = TitleLoader.getById("prior_county");
    assertNotNull(original);
    Files.writeString(title, "{ malformed");
    var admin = domain.player("Admin");
    when(admin.hasPermission("simplefactions.admin")).thenReturn(true);
    PluginCommand command = commands.get("faction");
    when(command.getName()).thenReturn("faction");
    Cache.provincesEnabled = true;

    assertTrue(
        assertDoesNotThrow(
            () ->
                dependency("commands", CommandManager.class)
                    .onCommand(admin, command, "faction", new String[] {"reloadtitles"})));

    assertSame(original, TitleLoader.getById("prior_county"));
    assertEquals("{ malformed", Files.readString(title));
    verify(admin).sendMessage(contains("Could not reload titles"));
    verify(admin, never()).sendMessage("§eReloaded titles!");
    verify(plugins, never()).disablePlugin(plugin);
    plugin.onDisable();
    scoped(BattlePersistenceService.class).verify(BattlePersistenceService::saveAll);
  }

  @Test
  void failedConfigurationReloadPreservesRegimentsWithoutDisablingNormalShutdownSaving()
      throws Exception {
    field("regimentLoader", new RegimentLoader());
    Path regiments =
        disk.write("regiments.yml", "guard:\n  default-slots: 3\n  item:\n    material: PAPER\n");
    plugin.onEnable();
    var original = RegimentLoader.getByString("guard");
    assertNotNull(original);
    Files.writeString(regiments, "guard: [unterminated\n");
    var admin = domain.player("Admin");
    when(admin.hasPermission("simplefactions.admin")).thenReturn(true);
    PluginCommand command = commands.get("faction");
    when(command.getName()).thenReturn("faction");

    assertTrue(
        assertDoesNotThrow(
            () ->
                dependency("commands", CommandManager.class)
                    .onCommand(admin, command, "faction", new String[] {"reloadconfigs"})));

    assertSame(original, RegimentLoader.getByString("guard"));
    assertEquals(3, original.getCurrentSlots());
    verify(admin).sendMessage(contains("Could not reload configs"));
    verify(admin, never()).sendMessage("§eReloaded configs!");
    verify(plugins, never()).disablePlugin(plugin);
    plugin.onDisable();
    scoped(BattlePersistenceService.class).verify(BattlePersistenceService::saveAll);
  }

  @ParameterizedTest
  @ValueSource(strings = {"battle-templates.yml", "Guilds/guild-types.yml", "Input/county.json"})
  void laterCatalogFailureRestoresCanonicalRegistriesAndSuccessfulRetryRebinds(String rejectedFile)
      throws Exception {
    Map<String, BattleTemplate> originalTemplates = BattleTemplateLoader.getAll();
    try {
      field("rankLoader", new RankLoader());
      field("relationLoader", new RelationLoader());
      field("tierLoader", new TierLoader());
      field("battleTemplateLoader", new BattleTemplateLoader());
      field("guildLoader", new GuildLoader());
      statics.remove(FactionManager.class).close();
      statics.put(FactionManager.class, mockStatic(FactionManager.class, CALLS_REAL_METHODS));
      writeIdentityCatalogs(false);
      plugin.loadConfigs();
      var factionData = domain.data("realm", "Alice");
      factionData.titles = List.of("county_owned");
      Faction faction = domain.saved(factionData);
      Faction neighbor = domain.saved("neighbor", "Bob");
      Relation relation =
          new Relation(RelationLoader.getType("ally"), RelationLoader.getAttitude("friendly"), 9);
      faction.getRelations().put(neighbor.getId(), relation);
      faction
          .getDiplomacyHandler()
          .getTradeRelations()
          .put(neighbor.getId(), RelationLoader.getType("trade"));
      faction
          .getDiplomacyHandler()
          .getTreatyRelations()
          .put(neighbor.getId(), RelationLoader.getType("treaty"));
      List<List<?>> registries = identityRegistries();
      List<List<?>> entries = new ArrayList<>();
      for (List<?> registry : registries) entries.add(new ArrayList<>(registry));
      var originalRank = faction.getRank();
      var originalType = relation.getType();
      var originalAttitude = relation.getAttitude();
      var originalTitle = faction.getTitles().getFirst();
      var originalTier = originalTitle.getTier();
      var originalTierTitles = new ArrayList<>(TitleLoader.getByTier(originalTier));
      assertEquals(2, originalTierTitles.size());
      writeIdentityCatalogs(true);
      Path broken =
          disk.write(
              rejectedFile, rejectedFile.endsWith("json") ? "{ malformed" : "bad: [unterminated\n");
      var emitted = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
      doAnswer(
              call -> {
                try {
                  return call.callRealMethod();
                } catch (RuntimeException failure) {
                  emitted.set(failure);
                  throw failure;
                }
              })
          .when(plugin)
          .loadConfigs();

      IllegalStateException failure =
          assertThrows(IllegalStateException.class, SimpleFactions::reloadConfigs);

      assertSame(emitted.get(), failure, "The original loader exception must reach the caller");
      assertNotNull(failure.getCause());
      assertTrue(failure.getMessage().contains(broken.getFileName().toString()));
      assertAll(
          () -> assertRegistryIdentities(registries, entries),
          () -> assertSame(originalRank, RankLoader.getByString("common")),
          () -> assertSame(RankLoader.getByString("common"), faction.getRank()),
          () -> assertSame(originalType, RelationLoader.getType("ally")),
          () -> assertSame(RelationLoader.getType("ally"), relation.getType()),
          () -> assertSame(originalAttitude, RelationLoader.getAttitude("friendly")),
          () -> assertSame(RelationLoader.getAttitude("friendly"), relation.getAttitude()),
          () -> assertSame(originalTitle, TitleLoader.getById("county_owned")),
          () -> assertSame(TitleLoader.getById("county_owned"), faction.getTitles().getFirst()),
          () -> assertSame(originalTier, TierLoader.getByString("county")),
          () -> assertSame(TierLoader.getByString("county"), originalTitle.getTier()),
          () ->
              assertEquals(
                  originalTierTitles, TitleLoader.getByTier(TierLoader.getByString("county"))),
          () ->
              assertSame(
                  RelationLoader.getType("trade"),
                  faction.getDiplomacyHandler().getTradeRelation(neighbor.getId())),
          () ->
              assertSame(
                  RelationLoader.getType("treaty"),
                  faction.getDiplomacyHandler().getTreatyRelations().get(neighbor.getId())),
          () -> assertEquals(9, relation.getOpinion()));
      scoped(FactionManager.class).verify(FactionManager::rebindRanks, never());
      scoped(FactionManager.class).verify(FactionManager::rebindDiplomacy, never());
      scoped(FactionManager.class).verify(FactionManager::reloadTitles, never());
      scoped(FactionManager.class).verify(FactionManager::updateAllPrestigeConverged, never());

      writeIdentityCatalogs(true);
      assertDoesNotThrow(SimpleFactions::reloadConfigs);

      for (int i = 0; i < registries.size(); i++)
        assertSame(registries.get(i), identityRegistries().get(i));
      assertNotSame(originalRank, faction.getRank());
      assertSame(RankLoader.getByString("common"), faction.getRank());
      assertEquals("Updated Common", faction.getRank().getName());
      assertNotSame(originalType, relation.getType());
      assertSame(RelationLoader.getType("ally"), relation.getType());
      assertEquals(25, relation.getType().getTarget());
      assertNotSame(originalAttitude, relation.getAttitude());
      assertSame(RelationLoader.getAttitude("friendly"), relation.getAttitude());
      assertEquals(35, relation.getAttitude().getTarget());
      assertNotSame(originalTitle, faction.getTitles().getFirst());
      assertSame(TitleLoader.getById("county_owned"), faction.getTitles().getFirst());
      assertNotSame(originalTier, faction.getTitles().getFirst().getTier());
      assertSame(TierLoader.getByString("county"), faction.getTitles().getFirst().getTier());
      assertEquals("Updated County", TierLoader.getByString("county").getName());
      assertEquals(
          List.of("county_other", "county_owned"),
          TitleLoader.getByTier(TierLoader.getByString("county")).stream()
              .map(t -> t.getId())
              .toList());
      assertSame(
          RelationLoader.getType("trade"),
          faction.getDiplomacyHandler().getTradeRelation(neighbor.getId()));
      assertSame(
          RelationLoader.getType("treaty"),
          faction.getDiplomacyHandler().getTreatyRelations().get(neighbor.getId()));
      assertEquals(9, relation.getOpinion());
      scoped(FactionManager.class).verify(FactionManager::rebindRanks);
      scoped(FactionManager.class).verify(FactionManager::rebindDiplomacy);
      scoped(FactionManager.class).verify(FactionManager::reloadTitles);
      scoped(FactionManager.class).verify(FactionManager::updateAllPrestigeConverged);
    } finally {
      BattleTemplateLoader.resetForTests();
      originalTemplates.values().forEach(BattleTemplateLoader::putForTests);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void guildConversionRetainsCapitalAfterConfigurationReload(boolean reloadSucceeds)
      throws Exception {
    field("guildLoader", new GuildLoader());
    statics.remove(FactionManager.class).close();
    statics.put(FactionManager.class, mockStatic(FactionManager.class, CALLS_REAL_METHODS));
    Path guildTypes =
        disk.write("Guilds/guild-types.yml", "realm:\n  base: true\nguild:\n  default: true\n");
    plugin.loadConfigs();
    Faction ruler = domain.saved("ruler", "Alice");
    Faction source = domain.saved("subject", "Bob");
    for (int id : List.of(7, 8))
      domain.provinceData.put(
          id, new net.tfminecraft.simplefactions.map.provinces.Province(id, "PLAINS", 1));
    ruler.addProvince(7);
    ruler.setCapital(7);
    source.addProvince(8);
    source.setCapital(8);
    domain.subject(ruler, source);
    var mainGuild = source.getOrCreateMainGuild();
    mainGuild.addMember("Cara");
    source.getBank().deposit(20.0);
    var originalBank = source.getBank();
    var originalGuildTypes = GuildLoader.get();
    var originalEntries = new LinkedHashMap<>(originalGuildTypes);
    Files.writeString(
        guildTypes,
        "realm:\n  base: true\n  name: Updated Realm\nguild:\n  default: true\nnew_type: {}\n");
    if (reloadSucceeds) {
      assertDoesNotThrow(SimpleFactions::reloadConfigs);
      assertNotSame(originalEntries.get("realm"), GuildLoader.getBaseType());
      assertEquals("Updated Realm", GuildLoader.getBaseType().getName());
      assertNotNull(GuildLoader.getByString("new_type"));
    } else {
      Path title = disk.write("Input/county.json", "{ malformed");
      IllegalStateException failure =
          assertThrows(IllegalStateException.class, SimpleFactions::reloadConfigs);
      assertTrue(failure.getMessage().contains("county.json"));
      assertEquals("{ malformed", Files.readString(title));
    }

    try (var databases = mockConstruction(Database.class)) {
      assertSame(ruler, source.dissolve(source.getVassals(), source.getGuildHandler().getGuilds()));
      verify(databases.constructed().getFirst()).deleteFaction(source);
    }

    assertAll(
        () -> {
          if (!reloadSucceeds) {
            assertSame(originalGuildTypes, GuildLoader.get());
            assertEquals(
                new ArrayList<>(originalEntries.keySet()),
                new ArrayList<>(GuildLoader.get().keySet()));
            originalEntries.forEach((id, type) -> assertSame(type, GuildLoader.getByString(id)));
          }
        },
        () -> assertSame(ruler, mainGuild.getFaction()),
        () -> assertFalse(mainGuild.isBase()),
        () ->
            assertEquals(8, mainGuild.getCapital(), "Dissolution must retain the subject capital"),
        () -> assertSame(originalBank, mainGuild.getBank()),
        () -> assertEquals(20.0, mainGuild.getBank().getWealth()),
        () -> assertEquals("Bob", mainGuild.getLeader()),
        () -> assertTrue(mainGuild.getMembers().containsAll(List.of("Bob", "Cara"))),
        () -> assertEquals(Set.of(7, 8), Set.copyOf(ruler.getProvinces())),
        () -> assertFalse(FactionManager.factions.contains(source)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Guilds/branches.yml",
        "Guilds/upgrades.yml",
        "Guilds/company-upgrades.yml",
        "Input/county.json"
      })
  void rejectedReloadRetainsDependentGuildDefinitions(String rejectedFile) throws Exception {
    var priorCompanyUpgrades = new LinkedHashMap<>(CompanyUpgradeLoader.get());
    try {
      field("guildLoader", new GuildLoader());
      field("branchLoader", new BranchLoader());
      field("upgradeLoader", new UpgradeLoader());
      field("companyUpgradeLoader", new CompanyUpgradeLoader());
      statics.remove(FactionManager.class).close();
      statics.put(FactionManager.class, mockStatic(FactionManager.class, CALLS_REAL_METHODS));
      disk.write("Guilds/guild-types.yml", "realm:\n  base: true\nguild:\n  default: true\n");
      disk.write(
          "Guilds/branches.yml",
          """
          bureaucracy:
            group: 0
            allowed-types: [realm]
          guild_halls:
            group: 0
            allowed-types: [guild]
          workshops:
            group: 1
            allowed-types: [realm, guild]
          """);
      disk.write("Guilds/upgrades.yml", "storehouse:\n  allowed-types: [realm, guild]\n");
      disk.write("Guilds/company-upgrades.yml", "company_storehouse:\n  allowed-types: [guild]\n");
      plugin.loadConfigs();
      List<Map<String, ?>> registries =
          List.of(
              GuildLoader.get(),
              BranchLoader.get(),
              UpgradeLoader.get(),
              CompanyUpgradeLoader.get());
      List<Map<String, ?>> entries = new ArrayList<>();
      for (var registry : registries) entries.add(new LinkedHashMap<>(registry));
      Faction ruler = domain.saved("ruler", "Alice");
      Faction source = domain.saved("subject", "Bob");
      for (int id : List.of(7, 8))
        domain.provinceData.put(
            id, new net.tfminecraft.simplefactions.map.provinces.Province(id, "PLAINS", 1));
      ruler.addProvince(7);
      ruler.setCapital(7);
      source.addProvince(8);
      source.setCapital(8);
      domain.subject(ruler, source);
      var mainGuild = source.getOrCreateMainGuild();
      mainGuild.getBranches().get(0).levelUp();
      mainGuild.getBranches().get(0).levelUp();
      mainGuild.getUpgrades().getFirst().setLevel(3);
      mainGuild.addMember("Cara");
      source.getBank().deposit(20.0);
      var originalBank = source.getBank();
      Path broken =
          disk.write(
              rejectedFile, rejectedFile.endsWith("json") ? "{ malformed" : "bad: [unterminated\n");

      IllegalStateException failure =
          assertThrows(IllegalStateException.class, SimpleFactions::reloadConfigs);
      assertTrue(failure.getMessage().contains(broken.getFileName().toString()));
      var freshBase = new net.tfminecraft.simplefactions.guild.Guild(ruler);
      var freshGuild = domain.guild(ruler, "fresh_guild", "Dana");
      assertAll(
          () -> {
            List<Map<String, ?>> current =
                List.of(
                    GuildLoader.get(),
                    BranchLoader.get(),
                    UpgradeLoader.get(),
                    CompanyUpgradeLoader.get());
            for (int i = 0; i < registries.size(); i++) {
              assertSame(registries.get(i), current.get(i));
              assertEquals(
                  new ArrayList<>(entries.get(i).keySet()),
                  new ArrayList<>(current.get(i).keySet()));
              for (var entry : entries.get(i).entrySet())
                assertSame(entry.getValue(), current.get(i).get(entry.getKey()));
            }
          },
          () -> assertEquals(Set.of(0, 1), freshBase.getBranches().keySet()),
          () -> assertEquals(Set.of(0, 1), freshGuild.getBranches().keySet()),
          () -> assertNotNull(BranchLoader.getByGroup(GuildLoader.getBaseType(), 0)),
          () -> assertNotNull(BranchLoader.getByGroup(GuildLoader.getDefaultType(), 0)),
          () -> assertTrue(UpgradeLoader.getByString("storehouse").isAllowed(freshGuild.getType())),
          () ->
              assertTrue(
                  CompanyUpgradeLoader.getByString("company_storehouse")
                      .isAllowed(freshGuild.getType())),
          () -> {
            try (var databases = mockConstruction(Database.class)) {
              assertSame(
                  ruler,
                  assertDoesNotThrow(
                      () ->
                          source.dissolve(
                              source.getVassals(), source.getGuildHandler().getGuilds())));
              verify(databases.constructed().getFirst()).deleteFaction(source);
            }
            assertSame(ruler, mainGuild.getFaction());
            assertFalse(mainGuild.isBase());
            assertEquals(8, mainGuild.getCapital());
            assertSame(originalBank, mainGuild.getBank());
            assertEquals(20.0, mainGuild.getBank().getWealth());
            assertEquals("Bob", mainGuild.getLeader());
            assertTrue(mainGuild.getMembers().containsAll(List.of("Bob", "Cara")));
            assertEquals("guild_halls", mainGuild.getBranches().get(0).getId());
            assertEquals(2, mainGuild.getBranches().get(0).getLevel());
            assertEquals("workshops", mainGuild.getBranches().get(1).getId());
            assertEquals(3, mainGuild.getUpgrades().getFirst().getLevel());
            assertEquals(Set.of(7, 8), Set.copyOf(ruler.getProvinces()));
            assertFalse(FactionManager.factions.contains(source));
          });
    } finally {
      CompanyUpgradeLoader.get().clear();
      CompanyUpgradeLoader.get().putAll(priorCompanyUpgrades);
    }
  }

  private static List<List<?>> identityRegistries() {
    return List.of(
        RankLoader.getRanks(),
        RelationLoader.getTypes(),
        RelationLoader.getAttitudes(),
        TierLoader.get(),
        TitleLoader.getTitles());
  }

  private static void assertRegistryIdentities(List<List<?>> references, List<List<?>> entries) {
    List<List<?>> current = identityRegistries();
    for (int i = 0; i < references.size(); i++) {
      assertSame(references.get(i), current.get(i), "Registry collection " + i);
      assertEquals(entries.get(i).size(), current.get(i).size(), "Registry size " + i);
      for (int j = 0; j < entries.get(i).size(); j++)
        assertSame(entries.get(i).get(j), current.get(i).get(j), "Registry " + i + " entry " + j);
    }
  }

  private void writeIdentityCatalogs(boolean updated) throws Exception {
    String common =
        "common:\n  name: "
            + (updated ? "Updated Common" : "Common")
            + "\n  level: 1\n  minimum-prestige: 0\n";
    String renowned = "renowned:\n  name: Renowned\n  level: 2\n  minimum-prestige: 100000\n";
    disk.write("ranks.yml", updated ? renowned + common : common + renowned);
    disk.write(
        "diplomacy.yml",
        """
        types:
          neutral:
            default: true
          ally:
            target: %d
          trade:
            trade-agreement: true
          treaty:
            treaty: true
        attitudes:
          neutral:
            default: true
          friendly:
            target: %d
        """
            .formatted(updated ? 25 : 5, updated ? 35 : 10));
    disk.write(
        "tiers.yml",
        """
        landless:
          name: Landless
          tier: 0
        province:
          name: Province
          tier: 1
          prestige: 10
        county:
          name: %s
          tier: 2
          prestige: 50
        duchy:
          name: Duchy
          tier: 3
          prestige: 100
        """
            .formatted(updated ? "Updated County" : "County"));
    disk.write("battle-templates.yml", "field_default:\n  type: field\n");
    disk.write("Guilds/guild-types.yml", "realm:\n  base: true\nguild:\n  default: true\n");
    String owned =
        "\"county_owned\":{\"name\":\""
            + (updated ? "Updated Title" : "Old Title")
            + "\",\"provinces\":[12]}";
    String other = "\"county_other\":{\"name\":\"Other Title\",\"provinces\":[13]}";
    disk.write(
        "Input/county.json", "{" + (updated ? other + "," + owned : owned + "," + other) + "}");
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
    plugin
        .recordVehicleOwners(); // A disabled VehicleFramework leaves existing owner history intact.
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
