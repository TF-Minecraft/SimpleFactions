package net.tfminecraft.simplefactions;


import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleHealthDecayApi.Vf;
import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.presence.ProvincePresenceListener;
import net.tfminecraft.simplefactions.map.presence.ProvincePresenceService;
import net.tfminecraft.simplefactions.map.presence.ProvincePresenceTickService;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.loaders.BattleTemplateLoader;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.CompanyUpgradeLoader;
import net.tfminecraft.simplefactions.loaders.ConfigLoader;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.LawLoader;
import net.tfminecraft.simplefactions.loaders.PoliticalActionLoader;
import net.tfminecraft.simplefactions.loaders.ProvinceLoader;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.loaders.RegionLoader;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.installation.InstallationProtectionListener;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.BankManager;
import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.LedgerCommandManager;
import net.tfminecraft.simplefactions.managers.MercenaryCommandManager;
import net.tfminecraft.simplefactions.mercenary.stat.MercenaryStatService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.PlayerManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelocationPrompt;
import net.tfminecraft.simplefactions.managers.CapitalMovePrompt;
import net.tfminecraft.simplefactions.utils.DisplayNameGate;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.TabCompletion;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleTickService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignViewRefreshService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommandManager;
import net.tfminecraft.simplefactions.war.declare.DeclareCodePrompt;
import net.tfminecraft.simplefactions.government.movement.admin.MovementCommandManager;
import net.tfminecraft.simplefactions.government.movement.admin.MovementTabCompletion;
import net.tfminecraft.simplefactions.war.core.WarTabCompletion;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleOutcomeService;
import net.tfminecraft.simplefactions.war.battle.engine.rules.BattleItemDurability;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.rules.BattleProvinceBlockProtectionService;
import net.tfminecraft.simplefactions.war.battle.ui.BattleCommandManager;
import net.tfminecraft.simplefactions.war.battle.ui.BattleTabCompletion;
import net.tfminecraft.simplefactions.war.campaign.raid.RaidCommandManager;
import net.tfminecraft.simplefactions.war.campaign.raid.RaidTabCompletion;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipListener;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplateService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidBattleEndService;
import net.tfminecraft.simplefactions.war.campaign.raid.intruder.CampaignRaidIntruderService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.vehicles.battle.BattleVehicleEligibilityService;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleUnberthService;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.VehicleIntegrationListener;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimListener;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimService;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryPersistence;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceDecayTask;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayListener;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePaySessionManager;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePersistence;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceRepairListener;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.simplefactions.vehicles.VehicleSpawnListener;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferConsentService;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferListener;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleUpkeepService;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;

public class SimpleFactions extends JavaPlugin{
	public static FileConfiguration config;
	public static SimpleFactions plugin;
	//Loaders
	private final ConfigLoader configLoader = new ConfigLoader();
	//private final CoinLoader coinLoader = new CoinLoader(); No longer in use
	private final RankLoader rankLoader = new RankLoader();
	private final RegimentLoader regimentLoader = new RegimentLoader();
	private final RelationLoader relationLoader = new RelationLoader();
	private final TierLoader tierLoader = new TierLoader();
	private static final TitleLoader titleLoader = new TitleLoader();
	private final BranchLoader branchLoader = new BranchLoader();
	private final UpgradeLoader upgradeLoader = new UpgradeLoader();
	private final CompanyUpgradeLoader companyUpgradeLoader = new CompanyUpgradeLoader();
	private final GuildLoader guildLoader = new GuildLoader();
	private final LawLoader lawLoader = new LawLoader();
	private final ProvinceLoader provinceLoader = new ProvinceLoader();
	private final PoliticalActionLoader politicalActionLoader = new PoliticalActionLoader();
	private final BattleTemplateLoader battleTemplateLoader = new BattleTemplateLoader();
	
	//Managers
	private final ProvinceManager provinceManager = new ProvinceManager();
	private final CommandManager commands = new CommandManager();
	private final InventoryManager inventoryManager = new InventoryManager();
	private final LedgerCommandManager ledgerCommandManager = new LedgerCommandManager(inventoryManager);
	private final MercenaryCommandManager mercenaryCommandManager = new MercenaryCommandManager();
	private final BankManager bankManager = new BankManager();
	private final Database db = new Database();
	private final FactionManager factionManager = new FactionManager();
	private final TitleManager titleManager = new TitleManager();
	private final PlayerManager playerManager = new PlayerManager();
	private final SessionManager sessionManager = new SessionManager();
	private final RelocationPrompt relocationPrompt = new RelocationPrompt();
	private final CapitalMovePrompt capitalMovePrompt = new CapitalMovePrompt();
	private final DisplayNameGate displayNameGate = new DisplayNameGate();
	private final DeclareCodePrompt declareCodePrompt = new DeclareCodePrompt();
	private final ProvincePresenceListener provincePresenceListener = new ProvincePresenceListener();
	private final BattleManager battleManager = new BattleManager();
	private final BattleItemDurability.Listener battleItemDurabilityListener =
			new BattleItemDurability.Listener();
	private final BattleProvinceBlockProtectionService.Listener battleProvinceBlockProtectionListener =
			new BattleProvinceBlockProtectionService.Listener();
	private final WarbandManager warbandManager = new WarbandManager();
	private final BattleCommandManager battleCommandManager = new BattleCommandManager();
	private final RaidCommandManager raidCommandManager = new RaidCommandManager();
	private final WarCommandManager warCommandManager = new WarCommandManager();
	private final MovementCommandManager movementCommandManager = new MovementCommandManager();
	private final WarbandMembershipListener warbandMembershipListener = new WarbandMembershipListener();
	private final CampaignRaidWarbandService.Listener campaignRaidWarbandListener = new CampaignRaidWarbandService.Listener();
	private final CampaignRaidIntruderService.Listener campaignRaidIntruderListener = new CampaignRaidIntruderService.Listener();
	private final CampaignRaidBattleEndService campaignRaidBattleEndService = new CampaignRaidBattleEndService();
	private final CampaignBattleOutcomeService campaignBattleOutcomeService = new CampaignBattleOutcomeService();
	private final net.tfminecraft.simplefactions.war.battle.loot.BattleLootService battleLootService =
			new net.tfminecraft.simplefactions.war.battle.loot.BattleLootService();
	private final InstallationProtectionListener installationProtectionListener =
			new InstallationProtectionListener();
	private final MercenaryStatService.Listener mercenaryStatListener =
			new MercenaryStatService.Listener();
	private final net.tfminecraft.simplefactions.mercenary.contract.AttendanceService.Hook attendanceHook =
			new net.tfminecraft.simplefactions.mercenary.contract.AttendanceService.Hook();
	private ProvinceManager provinceSnapshot = new ProvinceManager();
	private ProvinceGrid provinceGrid;
	private final PlayerVehicleRegistry vehicleRegistry = new PlayerVehicleRegistry();
	private VehicleRegistryPersistence vehicleRegistryPersistence;
	private final InstallationVehicleOwnerSync installationVehicleOwnerSync =
			new InstallationVehicleOwnerSync(vehicleRegistry);
	private final InstallationVehicleService installationVehicleService =
			new InstallationVehicleService(vehicleRegistry, installationVehicleOwnerSync);
	private final InstallationVehicleUnberthService installationVehicleUnberthService =
			new InstallationVehicleUnberthService(vehicleRegistry);
	private final VehicleTransferSessionManager vehicleTransferSessionManager =
			new VehicleTransferSessionManager();
	private final VehicleMaintenancePaySessionManager vehicleMaintenancePaySessionManager =
			new VehicleMaintenancePaySessionManager();
	private final VehicleMaintenanceStore vehicleMaintenanceStore = new VehicleMaintenanceStore();
	private VehicleMaintenancePersistence vehicleMaintenancePersistence;
	private final VehicleTransferConsentService vehicleTransferConsentService =
			new VehicleTransferConsentService(
					installationVehicleService,
					vehicleRegistry,
					vehicleTransferSessionManager);
	private final VehicleRegistryClaimService vehicleRegistryClaimService =
			new VehicleRegistryClaimService(vehicleRegistry);
	private final VehicleRegistryClaimListener vehicleRegistryClaimListener =
			new VehicleRegistryClaimListener(vehicleRegistryClaimService);
	private final VehicleIntegrationListener vehicleIntegrationListener = new VehicleIntegrationListener();
	private final VehicleTransferListener vehicleTransferListener = new VehicleTransferListener(
			vehicleTransferSessionManager,
			vehicleRegistry,
			installationVehicleService,
			vehicleTransferConsentService);
	private final VehicleSpawnListener vehicleSpawnListener =
			new VehicleSpawnListener(installationVehicleOwnerSync);
	private final BattleVehicleEligibilityService.Listener battleVehicleEligibilityListener =
			new BattleVehicleEligibilityService.Listener(vehicleRegistry);
	private final VehicleMaintenancePayService vehicleMaintenancePayService =
			new VehicleMaintenancePayService(vehicleMaintenanceStore, DenarEconomyPlayerBank.INSTANCE);
	private final VehicleMaintenanceRepairListener vehicleMaintenanceRepairListener =
			new VehicleMaintenanceRepairListener(vehicleMaintenanceStore);
	private final VehicleMaintenancePayListener vehicleMaintenancePayListener =
			new VehicleMaintenancePayListener(
					vehicleMaintenancePaySessionManager,
					vehicleMaintenancePayService);
	private boolean vehicleIntegrationRegistered = false;
	private final PlayerEconomyManager playerEconomyManager = new PlayerEconomyManager();
	private final VehicleUpkeepService vehicleUpkeepService = new VehicleUpkeepService(
		vehicleRegistry,
		playerEconomyManager,
		DenarEconomyPlayerBank.INSTANCE,
		vehicleMaintenanceStore,
		net.tfminecraft.simplefactions.vehicles.maintenance.VehicleHealthDecayApi.Vf.INSTANCE);
	private final VehicleMaintenanceDecayTask vehicleMaintenanceDecayTask =
			new VehicleMaintenanceDecayTask();
	
	@Override
	public void onEnable() {
		config = getConfig();
		plugin = this;
		FactionManager.inv = inventoryManager;
		createFolders();
		createConfigs();
		registerListeners();
		loadConfigs();
		registerRpCharactersIntegrationHooks();
		vehicleRegistryPersistence = new VehicleRegistryPersistence(
			new File(getDataFolder(), "Cache"),
			vehicleRegistry);
		vehicleRegistryPersistence.load();
		vehicleMaintenancePersistence = new VehicleMaintenancePersistence(
			new File(getDataFolder(), "Cache"),
			vehicleMaintenanceStore);
		vehicleMaintenancePersistence.load();
		registerVehicleIntegrationHooks();
		if (Cache.mapEnabled && !getServer().getPluginManager().isPluginEnabled("TFMCWeb")) {
			getLogger().severe(
				"[SimpleFactions] enable-map is true but TFMCWeb is not loaded. "
				+ "Map upload, province lookup, and regen require TFMCWeb + api.base-url / api.plugin-key."
			);
		}
		db.loadFactions();
		getCommand(commands.cmd1).setExecutor(commands);
		getCommand(commands.cmd2).setExecutor(commands);
		getCommand(commands.cmd1).setTabCompleter(new TabCompletion());
		getCommand(commands.cmd2).setTabCompleter(new TabCompletion());
		getCommand(ledgerCommandManager.cmd).setExecutor(ledgerCommandManager);
		getCommand(MercenaryCommandManager.CMD).setExecutor(mercenaryCommandManager);
		getCommand(MercenaryCommandManager.CMD).setTabCompleter(new TabCompletion());
		getCommand(MercenaryCommandManager.MARKET_CMD).setExecutor(mercenaryCommandManager);
		getCommand(MercenaryCommandManager.MARKET_CMD).setTabCompleter(new TabCompletion());
		if (!Cache.provincesEnabled) {
			getLogger().info(
					"[SimpleFactions] enable-provinces is false; skipping province Input load.");
		} else {
			try {
				provinceManager.start(
					provinceLoader.loadProvinces(
						new File(getDataFolder(), "Input/provinces.txt"),
						new File(getDataFolder(), "Input/province_neighbors.json")
					)
				);
			} catch (Exception e) {
				getLogger().severe("Failed to load provinces! Plugin disabled.");
				e.printStackTrace();
				getServer().getPluginManager().disablePlugin(this);
				return;
			}
			try {
				provinceGrid = ProvinceGrid.load(new File(getDataFolder(), "Input/province_id_grid.bin.gz"));
				getLogger().info(
						"Loaded province_id_grid "
								+ provinceGrid.getWidth()
								+ "x"
								+ provinceGrid.getHeight());
			} catch (Exception e) {
				getLogger().severe("Failed to load province_id_grid.bin.gz! Plugin disabled.");
				e.printStackTrace();
				getServer().getPluginManager().disablePlugin(this);
				return;
			}
		}
		factionManager.run();
		if (Cache.provincesEnabled) {
			ProvincePresenceTickService.start();
		}
		CampaignRaidIntruderService.Tick.start();
		warbandManager.start();
		battleManager.start();
		getCommand(battleCommandManager.cmd1).setExecutor(battleCommandManager);
		getCommand(battleCommandManager.cmd2).setExecutor(battleCommandManager);
		BattleTabCompletion battleTabCompletion = new BattleTabCompletion();
		getCommand(battleCommandManager.cmd1).setTabCompleter(battleTabCompletion);
		getCommand(battleCommandManager.cmd2).setTabCompleter(battleTabCompletion);
		RaidTabCompletion raidTabCompletion = new RaidTabCompletion();
		getCommand(raidCommandManager.cmd).setExecutor(raidCommandManager);
		getCommand(raidCommandManager.cmd).setTabCompleter(raidTabCompletion);
		WarTabCompletion warTabCompletion = new WarTabCompletion();
		getCommand(WarCommandManager.CMD).setExecutor(warCommandManager);
		getCommand(WarCommandManager.CMD).setTabCompleter(warTabCompletion);
		getCommand(MovementCommandManager.CMD).setExecutor(movementCommandManager);
		getCommand(MovementCommandManager.CMD).setTabCompleter(new MovementTabCompletion());
		RequestManager.start();
		WarManager.start();
		net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.loadAll();
		net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.startAutosave();
		BattleScheduleTickService.start();
		CampaignViewRefreshService.start();
		sessionManager.start();
		if (Cache.provincesEnabled) {
			provinceSnapshot = provinceManager.createSnapshotShell();
			provinceManager.recalculate();
		}
		inventoryManager.start();
		vehicleMaintenanceDecayTask.start();
	}
	@Override
	public void onDisable() {
		net.tfminecraft.simplefactions.integration.rpcharacters.chat.RpCharactersChatIntegration.unregister();
		MercenaryStatService.clearAll();
		net.tfminecraft.simplefactions.mercenary.company.MercenaryEligibility.reset();
		vehicleMaintenanceDecayTask.stop();
		CampaignViewRefreshService.stop();
		BattleManager.shutdown();
		net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.stopAutosave();
		net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.saveAll();
		sessionManager.end();
		db.saveTimer(FactionManager.getTimer(), FactionManager.getDay());
		for(Faction f : FactionManager.factions) {
			db.saveFaction(f);
		}
		for(War w : WarManager.get()){
			db.saveWar(w);
		}
		if (vehicleRegistryPersistence != null) {
			vehicleRegistryPersistence.save();
		}
		if (vehicleMaintenancePersistence != null) {
			vehicleMaintenancePersistence.save();
		}
	}
	public void loadConfigs() {
		configLoader.loadConfig(new File(getDataFolder(), "config.yml"));
		configLoader.loadWar(new File(getDataFolder(), "war.yml"));
		VehiclesConfigLoader.load(new File(getDataFolder(), "vehicles.yml"));
		InstallationConfigLoader.load(new File(getDataFolder(), "installations.yml"));
		net.tfminecraft.simplefactions.managers.LogManager.configure(
				Cache.loggingEnabled,
				Cache.wipeLog,
				getDataFolder());
		//coinLoader.loadCoins(new File(getDataFolder(), "coins.yml"));
		rankLoader.loadRanks(new File(getDataFolder(), "ranks.yml"));
		regimentLoader.loadRegiments(new File(getDataFolder(), "regiments.yml"));
		relationLoader.loadRelationTypes(new File(getDataFolder(), "diplomacy.yml"));
		relationLoader.loadAttitudes(new File(getDataFolder(), "diplomacy.yml"));
		politicalActionLoader.load(new File(getDataFolder(), "political-actions.yml"));
		lawLoader.load(new File(getDataFolder(), "laws.yml"));
		tierLoader.load(new File(getDataFolder(), "tiers.yml"));
		battleTemplateLoader.load(new File(getDataFolder(), "battle-templates.yml"));
		guildLoader.load(new File(getDataFolder(), "Guilds/guild-types.yml"));
		branchLoader.load(new File(getDataFolder(), "Guilds/branches.yml"));
		upgradeLoader.load(new File(getDataFolder(), "Guilds/upgrades.yml"));
		companyUpgradeLoader.load(new File(getDataFolder(), "Guilds/company-upgrades.yml"));
		titleLoader.loadAll();
		RegionLoader.loadAll();
	}
	public void registerListeners() {
		getServer().getPluginManager().registerEvents(commands, this);
		getServer().getPluginManager().registerEvents(inventoryManager, this);
		getServer().getPluginManager().registerEvents(bankManager, this);
		getServer().getPluginManager().registerEvents(titleManager, this);
		getServer().getPluginManager().registerEvents(playerManager, this);
		getServer().getPluginManager().registerEvents(sessionManager, this);
		getServer().getPluginManager().registerEvents(relocationPrompt, this);
		getServer().getPluginManager().registerEvents(capitalMovePrompt, this);
		getServer().getPluginManager().registerEvents(displayNameGate, this);
		getServer().getPluginManager().registerEvents(declareCodePrompt, this);
		getServer().getPluginManager().registerEvents(factionManager, this);
		getServer().getPluginManager().registerEvents(provincePresenceListener, this);
		getServer().getPluginManager().registerEvents(battleManager, this);
		getServer().getPluginManager().registerEvents(battleItemDurabilityListener, this);
		getServer().getPluginManager().registerEvents(battleProvinceBlockProtectionListener, this);
		getServer().getPluginManager().registerEvents(warbandManager, this);
		getServer().getPluginManager().registerEvents(warbandMembershipListener, this);
		getServer().getPluginManager().registerEvents(campaignRaidWarbandListener, this);
		getServer().getPluginManager().registerEvents(campaignRaidIntruderListener, this);
		getServer().getPluginManager().registerEvents(campaignRaidBattleEndService, this);
		getServer().getPluginManager().registerEvents(campaignBattleOutcomeService, this);
		getServer().getPluginManager().registerEvents(battleLootService, this);
		getServer().getPluginManager().registerEvents(installationProtectionListener, this);
		getServer().getPluginManager().registerEvents(mercenaryStatListener, this);
		getServer().getPluginManager().registerEvents(attendanceHook, this);
		MercenaryStatService.setGate(new net.tfminecraft.simplefactions.mercenary.stat.HiredMercenaryGate());
		net.tfminecraft.simplefactions.mercenary.contract.ContractTerminationService.setReputationSeam(
				new net.tfminecraft.simplefactions.mercenary.company.MercenaryReputationSeam());
		if (getServer().getPluginManager().isPluginEnabled("RPCharacters")) {
			registerRpCharactersIntegration();
		}
	}
	public void createFolders() {
		File dataFolder = getDataFolder();
		if (!dataFolder.exists()) dataFolder.mkdir();

		String[] subFolders = {
			"Data",
			"PlayerData",
			"Wars",
			"Cache",
			"MapAPI",
			"Input",
			"Guilds",
			"logs"
		};

		for (String name : subFolders) {
			File folder = new File(dataFolder, name);
			if (!folder.exists()) folder.mkdir();
		}
	}

	public static int getMaxExtraNodeCapacity() {
		return Cache.maxExtraNodeCapacity;
	}
	public void createConfigs() {
		String[] files = {
				"regiments.yml",
				"diplomacy.yml",
				"ranks.yml",
				"config.yml",
				"war.yml",
				"tiers.yml",
				"laws.yml",
				"political-actions.yml",
				"Guilds/guild-types.yml",
				"Guilds/branches.yml",
				"Guilds/upgrades.yml",
				"Guilds/company-upgrades.yml",
				"battle-templates.yml",
				"vehicles.yml",
				"installations.yml",
				};
		for(String s : files) {
			File newConfigFile = new File(getDataFolder(), s);
	        if (!newConfigFile.exists()) {
	        	newConfigFile.getParentFile().mkdirs();
	            saveResource(s, false);
	        }
		}
	}

	public static void reloadTitles() {
		titleLoader.reload();
		RegionLoader.loadAll();
	}

	public static void reloadConfigs() {
		plugin.loadConfigs();
		FactionManager.rebindRanks();
		FactionManager.rebindDiplomacy();
		FactionManager.updateAllPrestigeConverged();
	}

	public static SimpleFactions getInstance() {
		return plugin;
	}

	public ProvinceManager getProvinceManager() {
		return provinceManager;
	}

	public ProvinceManager getProvinceSnapshot() {
		return provinceSnapshot;
	}

	public ProvinceGrid getProvinceGrid() {
		return provinceGrid;
	}

	public SessionManager getSessionManager() {
		return sessionManager;
	}

	public ProvincePresenceService getProvincePresenceService() {
		return ProvincePresenceService.getInstance();
	}

	public static PlayerVehicleRegistry getVehicleRegistry() {
		return plugin.vehicleRegistry;
	}

	public VehicleTransferSessionManager getVehicleTransferSessionManager() {
		return vehicleTransferSessionManager;
	}

	public VehicleMaintenancePaySessionManager getVehicleMaintenancePaySessionManager() {
		return vehicleMaintenancePaySessionManager;
	}

	public VehicleMaintenanceStore getVehicleMaintenanceStore() {
		return vehicleMaintenanceStore;
	}

	public VehicleTransferConsentService getVehicleTransferConsentService() {
		return vehicleTransferConsentService;
	}

	public InstallationVehicleUnberthService getInstallationVehicleUnberthService() {
		return installationVehicleUnberthService;
	}

	public static PlayerEconomyManager getPlayerEconomyManager() {
		return plugin.playerEconomyManager;
	}

	public VehicleUpkeepService getVehicleUpkeepService() {
		return vehicleUpkeepService;
	}

	public void saveVehicleRegistry() {
		if (vehicleRegistryPersistence != null) {
			vehicleRegistryPersistence.save();
		}
		if (vehicleMaintenancePersistence != null) {
			vehicleMaintenancePersistence.save();
		}
	}

	private void registerRpCharactersIntegrationHooks() {
		getServer().getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onPluginEnable(PluginEnableEvent event) {
				if ("RPCharacters".equalsIgnoreCase(event.getPlugin().getName())) {
					registerRpCharactersIntegration();
				}
			}
		}, this);
		registerRpCharactersIntegration();
	}

	private void registerRpCharactersIntegration() {
		if (getServer().getPluginManager() == null
				|| !getServer().getPluginManager().isPluginEnabled("RPCharacters")) {
			return;
		}
		net.tfminecraft.simplefactions.mercenary.company.MercenaryEligibility.setProbe(
				new net.tfminecraft.simplefactions.mercenary.company.RpCharactersMercenaryTraitProbe());
		net.tfminecraft.simplefactions.prestige.MemberPlaytime.setProbe(
				new net.tfminecraft.simplefactions.prestige.RpCharactersPlaytimeProbe());
		net.tfminecraft.simplefactions.integration.rpcharacters.chat.RpCharactersChatIntegration.register();
	}

	private void registerVehicleIntegrationHooks() {
		getServer().getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onPluginEnable(PluginEnableEvent event) {
				if ("VFBuilders".equalsIgnoreCase(event.getPlugin().getName())
						|| "VehicleFramework".equalsIgnoreCase(event.getPlugin().getName())) {
					registerVehicleIntegration();
				}
			}
		}, this);
		registerVehicleIntegration();
	}

	private void registerVehicleIntegration() {
		if (vehicleIntegrationRegistered) {
			return;
		}
		if (!getServer().getPluginManager().isPluginEnabled("VehicleFramework")) {
			return;
		}
		getServer().getPluginManager().registerEvents(vehicleIntegrationListener, this);
		getServer().getPluginManager().registerEvents(vehicleRegistryClaimListener, this);
		getServer().getPluginManager().registerEvents(vehicleTransferListener, this);
		getServer().getPluginManager().registerEvents(vehicleMaintenancePayListener, this);
		getServer().getPluginManager().registerEvents(vehicleMaintenanceRepairListener, this);
		getServer().getPluginManager().registerEvents(vehicleSpawnListener, this);
		getServer().getPluginManager().registerEvents(battleVehicleEligibilityListener, this);
		vehicleIntegrationRegistered = true;
		if (getServer().getPluginManager().isPluginEnabled("VFBuilders")) {
			getLogger().info("[SimpleFactions] VFBuilders vehicle integration enabled");
		} else {
			getLogger().info("[SimpleFactions] VehicleFramework vehicle integration enabled");
		}
	}

	public BattleTemplateService getBattleTemplateService() {
		return BattleTemplateService.getInstance();
	}
}
