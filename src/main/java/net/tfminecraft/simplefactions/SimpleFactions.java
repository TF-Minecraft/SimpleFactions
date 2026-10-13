package net.tfminecraft.simplefactions;


import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleHealthDecayApi.Vf;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.migration.CharacterMigration;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceCache;
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
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleGiveService;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseListener;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleUnberthService;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.VehicleIntegrationListener;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimListener;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimService;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryPersistence;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeConfirmations;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleReclaimFeeListener;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverListener;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverSessionManager;
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
import net.tfminecraft.simplefactions.vehicles.berth.VehicleReleaseSessionManager;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleUpkeepService;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.vehicles.owners.LeaderStamps;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleClaimsOff;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerMigration;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnershipGate;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnershipServices;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleQuarantine;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleUpkeepReconcile;

public class SimpleFactions extends JavaPlugin{
	public static FileConfiguration config;
	public static SimpleFactions plugin;
	//Loaders
	private final ConfigLoader configLoader = new ConfigLoader();
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
    private net.tfminecraft.simplefactions.economy.settlement.RealmSettlementService realmSettlementService;
    public net.tfminecraft.simplefactions.economy.settlement.RealmSettlementService getRealmSettlementService() { return realmSettlementService; }
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
	private static final long TRACK_REFRESH_TICKS = 300L * 20L;
	private BukkitTask trackRefreshTask;
	private final PlayerVehicleRegistry vehicleRegistry = new PlayerVehicleRegistry();
	private VehicleRegistryPersistence vehicleRegistryPersistence;
	private final InstallationVehicleOwnerSync installationVehicleOwnerSync =
			new InstallationVehicleOwnerSync(vehicleRegistry);
	private final InstallationVehicleService installationVehicleService =
			new InstallationVehicleService(vehicleRegistry, installationVehicleOwnerSync);
	private final net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService factionVehiclePoolService =
			new net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService(
					vehicleRegistry, installationVehicleOwnerSync);
	private final FactionVehicleReleaseService factionVehicleReleaseService =
			new FactionVehicleReleaseService(vehicleRegistry);
	private final InstallationVehicleUnberthService installationVehicleUnberthService =
			new InstallationVehicleUnberthService(factionVehicleReleaseService);
	private final VehicleTransferSessionManager vehicleTransferSessionManager =
			new VehicleTransferSessionManager();
	private final VehicleReleaseSessionManager vehicleReleaseSessionManager =
			new VehicleReleaseSessionManager();
	private final VehicleMaintenancePaySessionManager vehicleMaintenancePaySessionManager =
			new VehicleMaintenancePaySessionManager();
	private final VehicleMaintenanceStore vehicleMaintenanceStore = new VehicleMaintenanceStore();
	private VehicleMaintenancePersistence vehicleMaintenancePersistence;
	private final VehicleTransferConsentService vehicleTransferConsentService =
			new VehicleTransferConsentService(
					installationVehicleService,
					vehicleTransferSessionManager,
					factionVehiclePoolService);
	private final FactionVehicleGiveService factionVehicleGiveService =
			new FactionVehicleGiveService(factionVehicleReleaseService);
	private final VehicleRegistryClaimService vehicleRegistryClaimService =
			new VehicleRegistryClaimService(vehicleRegistry);
	private final VehicleRegistryClaimListener vehicleRegistryClaimListener =
			new VehicleRegistryClaimListener(vehicleRegistryClaimService);
	private final VehicleIntegrationListener vehicleIntegrationListener = new VehicleIntegrationListener();
	private final VehicleTransferListener vehicleTransferListener = new VehicleTransferListener(
			vehicleTransferSessionManager,
			vehicleRegistry,
			installationVehicleService,
			vehicleTransferConsentService,
			factionVehiclePoolService);
	private final FactionVehicleReleaseListener factionVehicleReleaseListener =
			new FactionVehicleReleaseListener(
					vehicleReleaseSessionManager,
					factionVehicleReleaseService,
					factionVehicleGiveService);
	private final VehicleSpawnListener vehicleSpawnListener =
			new VehicleSpawnListener(installationVehicleOwnerSync);
	private final BattleVehicleEligibilityService.Listener battleVehicleEligibilityListener =
			new BattleVehicleEligibilityService.Listener(vehicleRegistry);
	private final VehicleMaintenancePayService vehicleMaintenancePayService =
			new VehicleMaintenancePayService(vehicleMaintenanceStore);
	private final VehicleMaintenanceRepairListener vehicleMaintenanceRepairListener =
			new VehicleMaintenanceRepairListener(vehicleMaintenanceStore);
	private final VehicleMaintenancePayListener vehicleMaintenancePayListener =
			new VehicleMaintenancePayListener(
					vehicleMaintenancePaySessionManager,
					vehicleMaintenancePayService);
	private final VehicleFeeStore vehicleFeeStore = new VehicleFeeStore();
	private final VehicleFeeConfirmations vehicleFeeConfirmations = new VehicleFeeConfirmations();
	private final VehicleHandoverSessionManager vehicleHandoverSessionManager = new VehicleHandoverSessionManager();
	private final VehicleHandoverService vehicleHandoverService =
			new VehicleHandoverService(vehicleRegistry, vehicleFeeStore, this::saveVehicleFees);
	private final VehicleHandoverListener vehicleHandoverListener = new VehicleHandoverListener(
			vehicleHandoverSessionManager, vehicleHandoverService, vehicleFeeConfirmations);
	private final VehicleReclaimFeeListener vehicleReclaimFeeListener =
			new VehicleReclaimFeeListener(vehicleFeeStore, this::saveVehicleFees);
	private boolean registrationFeeRegistered;
	private boolean incompatibleCharacterApi;
	/** Once-guard for {@link #startVehicles()}, which both start() and VehicleFramework's enable can trigger. */
	private boolean vehiclesStarted;
	private static final long VEHICLE_RECONCILE_TICKS = 24L * 60L * 60L * 20L;
	// Seams for tests; production probes VehicleFramework and copies its SQLite database.
	java.util.function.Supplier<VehicleOwnerApi> vehicleApiProbe = VehicleOwnerWrites::probe;
	java.util.function.Function<java.nio.file.Path, VehicleOwnerMigration.Backups> vehicleBackups =
			VehicleOwnerMigration.SqliteBackups::new;
	VehicleOwnerMigration.Identity vehicleIdentity = new VehicleOwnerMigration.LiveIdentity();
	private boolean constructionFreezeRegistered;
	private final PlayerEconomyManager playerEconomyManager = new PlayerEconomyManager();
	private final VehicleUpkeepService vehicleUpkeepService = new VehicleUpkeepService(
		vehicleRegistry,
		playerEconomyManager,
		DenarEconomyPlayerBank.INSTANCE,
		vehicleMaintenanceStore,
		net.tfminecraft.simplefactions.vehicles.maintenance.VehicleHealthDecayApi.Vf.INSTANCE);
	private final VehicleMaintenanceDecayTask vehicleMaintenanceDecayTask =
			new VehicleMaintenanceDecayTask();
	
	private boolean startupComplete;
	/** Set when loading began, so a failed start still releases what it took. */
	private boolean servicesBegun;
	/** True once the data is migrated and every service has started. */
	private volatile boolean started;
	private boolean startFailed;
	private BukkitTask identityPoll;
	private CharacterMigration.Heirs migrationHeirs = CharacterMigration.SHARED;
	/** Every command in plugin.yml; each answers "starting up" until the plugin has started. */
	static final java.util.List<String> COMMANDS = java.util.List.of(
			"faction", "guild", "warband", "battle", "raid", "war", "movement", "ledger", "company", "mercenaries");
	private static final long IDENTITY_POLL_TICKS = 5L * 20L;

	@Override
	public void onEnable() {
		try {
			net.tfminecraft.tlibs.character.Characters.legacyHeirsReady();
			net.tfminecraft.tlibs.character.Characters.deathId(new java.util.UUID(0L, 0L));
		} catch (LinkageError error) {
			incompatibleCharacterApi = true;
			getLogger().severe("SimpleFactions requires TLibs 2.4.1 or newer; disabling.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		startupComplete = false;
		started = false;
		startFailed = false;
		servicesBegun = false;
		vehiclesStarted = false;
		config = getConfig();
		plugin = this;
		FactionManager.inv = inventoryManager;
		createFolders();
		createConfigs();
		try {
			loadConfigs();
		} catch (RuntimeException error) {
			getLogger().log(java.util.logging.Level.SEVERE, "Cannot load SimpleFactions configuration; disabling before restoring saved state", error);
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		// VehicleFramework may enable inside this enable window or after start(); startVehicles() runs once.
		getServer().getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onPluginEnable(PluginEnableEvent event) {
				if ("VehicleFramework".equalsIgnoreCase(event.getPlugin().getName())) startVehicles();
			}
		}, this);
		awaitIdentity();
	}

	/**
	 * Faction data names characters, so nothing loads, ticks, listens or saves
	 * until the character provider can name every legacy account's heir.
	 * Commands answer that factions are starting up meanwhile.
	 */
	private void awaitIdentity() {
		for (String name : COMMANDS) {
			org.bukkit.command.PluginCommand command = getCommand(name);
			if (command == null) continue;
			command.setExecutor((sender, cmd, label, args) -> {
				sender.sendMessage("§eFactions are starting up; try again in a moment.");
				return true;
			});
			command.setTabCompleter((sender, cmd, label, args) -> java.util.List.of());
		}
		if (identityReady()) {
			start();
			return;
		}
		getLogger().severe("Waiting for character identity (RPCharacters) before loading any faction data; "
				+ "factions stay offline until it is ready");
		getServer().getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onPluginEnable(PluginEnableEvent event) {
				if (("RPCharacters".equalsIgnoreCase(event.getPlugin().getName()) || "DenarEconomy".equalsIgnoreCase(event.getPlugin().getName())) && identityReady()) start();
			}
		}, this);
		identityPoll = getServer().getScheduler().runTaskTimer(this, () -> {
			if (identityReady()) start();
		}, IDENTITY_POLL_TICKS, IDENTITY_POLL_TICKS);
	}

	static boolean identityReady() {
		return net.tfminecraft.denareconomy.api.Denars.ready()
                && net.tfminecraft.tlibs.character.Characters.available()
				&& net.tfminecraft.tlibs.character.Characters.legacyHeirsReady();
	}

    /** A persisted operation must finish before another character or money mutation is allowed. */
    public void failFinancialState(Throwable error) {
        started = false;
        LeaderStamps.enable(false);
        startupComplete = false;
        startFailed = true;
        getServer().getServicesManager().unregisterAll(this);
        getLogger().log(java.util.logging.Level.SEVERE,
                "Faction money recovery is incomplete; disabling until the saved operation can be recovered", error);
        getServer().getPluginManager().disablePlugin(this);
    }

	/** Whether the data is migrated and everything has started; see {@code SimpleFactionsApi.ready()}. */
	public boolean isStarted() {
		return started;
	}

	void useMigrationHeirs(CharacterMigration.Heirs heirs) {
		migrationHeirs = heirs;
	}

	/** Migrates the stores if needed, then loads and starts everything. */
	void start() {
		if (started || startFailed) return;
		if (identityPoll != null) {
			identityPoll.cancel();
			identityPoll = null;
		}
		try {
			CharacterMigration.Result result =
					new CharacterMigration(getDataFolder().toPath(), migrationHeirs, getLogger()).run();
			if (result.migrated() > 0 || result.assignmentsApplied() > 0) {
				getLogger().info("Character migration: " + result.migrated() + " stores migrated, "
						+ result.assignmentsApplied() + " quarantine assignments applied");
			}
			if (result.quarantined() > 0) {
				getLogger().warning(result.quarantined() + " quarantined faction records need staff; see /f admin quarantine list");
			}
		} catch (java.io.IOException | RuntimeException error) {
			startFailed = true;
			getLogger().log(java.util.logging.Level.SEVERE,
					"Cannot migrate faction data to characters; factions stay offline until this is fixed", error);
			return;
		}
		try { startServices(); }
        catch (Exception error) { failFinancialState(error); }
		if (!started) return;
		// Factions are up; vehicles follow once VehicleFramework is enabled too. The ready event says nothing about vehicles.
		startVehicles();
		if (!started) return;
		if (!vehiclesStarted) {
			getLogger().info("Vehicle ownership waits for VehicleFramework to enable; factions run without vehicles until then");
		}
		getServer().getPluginManager().callEvent(new net.tfminecraft.simplefactions.events.SimpleFactionsReadyEvent());
	}

	private void startServices() {
		servicesBegun = true;
		net.tfminecraft.simplefactions.identity.LeaderCharacters.useSharedIdentity();
		net.tfminecraft.simplefactions.prestige.MemberPlaytime.useSharedIdentity();
		net.tfminecraft.simplefactions.mercenary.company.MercenaryEligibility.useSharedIdentity();

		vehicleRegistryPersistence = new VehicleRegistryPersistence(
			new File(getDataFolder(), "Cache"),
			vehicleRegistry);
		vehicleRegistryPersistence.load();
		vehicleMaintenancePersistence = new VehicleMaintenancePersistence(
			new File(getDataFolder(), "Cache"),
			vehicleMaintenanceStore);
		vehicleMaintenancePersistence.load();
		vehicleFeeStore.bind(new File(getDataFolder(), "Cache"));
		vehicleFeeStore.load();
		registerVehicleIntegrationHooks();
		if (Cache.mapEnabled && !getServer().getPluginManager().isPluginEnabled("TFMCWeb")) {
			getLogger().severe(
				"[SimpleFactions] enable-map is true but TFMCWeb is not loaded. "
				+ "Map upload, province lookup, and regen require TFMCWeb + api.base-url / api.plugin-key."
			);
		}
		net.tfminecraft.simplefactions.inactivity.InactivityService.load();
        try {
            net.tfminecraft.simplefactions.economy.TreasuryActions.recoverTopologyFiles(getDataFolder().toPath().resolve("economy/callers"));
        } catch (Exception error) { failFinancialState(error); return; }
        db.loadFactions();
		try {
			net.tfminecraft.simplefactions.espionage.EspionageService.loadAptitudes(
					getDataFolder().toPath().resolve("Cache/character-aptitudes.json"));
		} catch (Exception error) {
			getLogger().log(java.util.logging.Level.SEVERE, "Cannot load permanent character aptitudes; disabling SimpleFactions", error);
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
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
        factionManager.prepare();
        WarManager.start();
        net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.loadAll();
        try {
            for (var guild : FactionManager.getAllGuilds()) {
                net.tfminecraft.simplefactions.economy.GuildTreasury.importLegacy(guild.getIncarnationId(),
                        guild.getBank() == null ? 0 : guild.getBank().legacyBalance(), guild.getLedger().getCitizenTaxesCopy());
                guild.updateWealth();
            }
            FactionManager.updateAllPrestigeConverged();
            net.tfminecraft.simplefactions.identity.death.CharacterDeaths.load(getDataFolder().toPath());
            net.tfminecraft.simplefactions.economy.TreasuryActions.recover(getDataFolder().toPath().resolve("economy/callers"));
            net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.startup(getDataFolder().toPath(), vehicleFeeStore, vehicleMaintenanceStore);
            net.tfminecraft.simplefactions.economy.ExternalIncome.recover(getDataFolder().toPath());
            realmSettlementService = new net.tfminecraft.simplefactions.economy.settlement.RealmSettlementService(getDataFolder().toPath(), db);
            realmSettlementService.recover();
            net.tfminecraft.simplefactions.identity.death.CharacterDeaths.reconcile();
            net.tfminecraft.simplefactions.identity.death.CharacterDeaths.indexLockGuilds();
            net.tfminecraft.simplefactions.economy.EconomyRuntime.recovered();
        } catch (Exception error) {
            failFinancialState(error);
            return;
        }
        factionManager.startTicks();
        registerListeners();
        registerRpCharactersChatHooks();
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
		net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.startAutosave();
		BattleScheduleTickService.start();
		CampaignViewRefreshService.start();
		sessionManager.start();
		if (Cache.provincesEnabled) {
			provinceSnapshot = provinceManager.createSnapshotShell();
			refreshTrackProvinces(() -> {});
			provinceManager.recalculate();
			trackRefreshTask = getServer().getScheduler().runTaskTimer(
					this, () -> { refreshTrackProvinces(); }, TRACK_REFRESH_TICKS, TRACK_REFRESH_TICKS);
		}
		inventoryManager.start();
		vehicleMaintenanceDecayTask.start();
		startupComplete = true;
        started = true;
        getServer().getServicesManager().register(net.tfminecraft.denareconomy.api.TaxPolicy.class,
                new net.tfminecraft.simplefactions.economy.GuildTaxPolicy(), this, org.bukkit.plugin.ServicePriority.Normal);
        net.tfminecraft.simplefactions.estate.EstateBridge.registerFallback(this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            try { net.tfminecraft.simplefactions.identity.death.CharacterDeaths.reconcile(); }
            catch (Exception error) { net.tfminecraft.simplefactions.economy.EconomyRuntime.failClosed(error); }
        }, 72000L, 72000L);
        net.tfminecraft.simplefactions.identity.LeaderCharacterListener.refresh(null);
	}
	@Override
	public void onDisable() {
		if (incompatibleCharacterApi) return;
		if (identityPoll != null) identityPoll.cancel();
		identityPoll = null;
		started = false;
		LeaderStamps.enable(false);
		VehicleOwnerWrites.use(null);
		VehicleQuarantine.use(null);
        getServer().getServicesManager().unregisterAll(this);
        net.tfminecraft.simplefactions.identity.death.CharacterDeaths.stop();
        net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.stop();
		if (!servicesBegun) {
			// Still waiting for identity: nothing was loaded, so nothing may be saved.
			return;
		}
		if (trackRefreshTask != null) trackRefreshTask.cancel();
		net.tfminecraft.simplefactions.integration.rpcharacters.chat.RpCharactersChatIntegration.unregister();
		MercenaryStatService.clearAll();
		net.tfminecraft.simplefactions.mercenary.company.MercenaryEligibility.reset();
		vehicleMaintenanceDecayTask.stop();
		CampaignViewRefreshService.stop();
		BattleManager.shutdown();
		net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.stopAutosave();
		sessionManager.end();
		if (!startupComplete) {
			net.tfminecraft.simplefactions.identity.LeaderCharacters.reset();
			return;
		}
		net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.saveAll();
		net.tfminecraft.simplefactions.inactivity.InactivityService.save();
		saveFactionsForShutdown();
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
    void saveFactionsForShutdown() {
        // The save resolves leader characters, so RPCharacters stays asked until it is done.
        saveLoadedFactions();
        net.tfminecraft.simplefactions.identity.LeaderCharacters.reset();
    }

    void saveLoadedFactions() {
        // An early enable failure must not overwrite partially restored faction state.
        if (!FactionManager.isLoaded()) return;
        db.saveTimer(FactionManager.getTimer(), FactionManager.getDay());
        for (Faction faction : FactionManager.factions) db.saveFaction(faction);
    }

	public void loadConfigs() {
		net.tfminecraft.simplefactions.espionage.SpecialPositionsConfigFile.load(this);
		configLoader.loadConfig(new File(getDataFolder(), "config.yml"));
		configLoader.loadWar(new File(getDataFolder(), "war.yml"));
		VehiclesConfigLoader.load(new File(getDataFolder(), "vehicles.yml"));
		InstallationConfigLoader.load(new File(getDataFolder(), "installations.yml"));
		net.tfminecraft.simplefactions.managers.LogManager.configure(
				Cache.loggingEnabled,
				Cache.wipeLog,
				getDataFolder());
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
		getServer().getPluginManager().registerEvents(
				new net.tfminecraft.simplefactions.identity.LeaderCharacterListener(), this);
		getServer().getPluginManager().registerEvents(sessionManager, this);
		getServer().getPluginManager().registerEvents(relocationPrompt, this);
		getServer().getPluginManager().registerEvents(capitalMovePrompt, this);
		getServer().getPluginManager().registerEvents(displayNameGate, this);
		getServer().getPluginManager().registerEvents(declareCodePrompt, this);
		getServer().getPluginManager().registerEvents(factionManager, this);
		if (getServer().getPluginManager().getPlugin("ItemsAdder") != null) {
			getServer().getPluginManager().registerEvents(
					new net.tfminecraft.simplefactions.managers.VotingBoothListener(factionManager), this);
		}
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
		getServer().getPluginManager().registerEvents(
				new net.tfminecraft.simplefactions.espionage.OfficeCharacterDeathListener(), this);
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
		var previousRanks = new ArrayList<>(RankLoader.getRanks());
		var previousTypes = new ArrayList<>(RelationLoader.getTypes());
		var previousAttitudes = new ArrayList<>(RelationLoader.getAttitudes());
		var previousTiers = new ArrayList<>(TierLoader.get());
		var previousTitles = new ArrayList<>(TitleLoader.getTitles());
		var previousGuildTypes = new LinkedHashMap<>(GuildLoader.get());
		var previousBranches = new LinkedHashMap<>(BranchLoader.get());
		var previousUpgrades = new LinkedHashMap<>(UpgradeLoader.get());
		var previousCompanyUpgrades = new LinkedHashMap<>(CompanyUpgradeLoader.get());
		try {
			plugin.loadConfigs();
		} catch (RuntimeException failure) {
			// Live factions still reference these definitions until every loader succeeds.
			RankLoader.getRanks().clear();
			RankLoader.getRanks().addAll(previousRanks);
			RelationLoader.getTypes().clear();
			RelationLoader.getTypes().addAll(previousTypes);
			RelationLoader.getAttitudes().clear();
			RelationLoader.getAttitudes().addAll(previousAttitudes);
			TierLoader.get().clear();
			TierLoader.get().addAll(previousTiers);
			TitleLoader.getTitles().clear();
			TitleLoader.getTitles().addAll(previousTitles);
			GuildLoader.get().clear();
			GuildLoader.get().putAll(previousGuildTypes);
			BranchLoader.get().clear();
			BranchLoader.get().putAll(previousBranches);
			UpgradeLoader.get().clear();
			UpgradeLoader.get().putAll(previousUpgrades);
			CompanyUpgradeLoader.get().clear();
			CompanyUpgradeLoader.get().putAll(previousCompanyUpgrades);
			throw failure;
		}
		FactionManager.rebindRanks();
		FactionManager.rebindDiplomacy();
		// loadConfigs rebuilds every Title, so point factions at the new copies or hasTitle stops matching.
		FactionManager.reloadTitles();
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

	public boolean refreshTrackProvinces() {
		return refreshTrackProvinces(provinceManager::recalculate);
	}

	private boolean refreshTrackProvinces(Runnable recalculate) {
		return TrackProvinceCache.live().refresh(() -> {
			if (provinceGrid == null) {
				return java.util.Set.of();
			}
			if (!getServer().getPluginManager().isPluginEnabled("VehicleFramework")) {
				TrackProvinceCache.live().vehicleFrameworkUnavailable(
						message -> getLogger().info("[SimpleFactions] " + message));
				return java.util.Set.of();
			}
			java.util.Map<Integer, net.tfminecraft.simplefactions.map.provinces.Province> provinces =
					new java.util.HashMap<>();
			for (net.tfminecraft.simplefactions.map.provinces.Province province : provinceManager.getProvinces()) {
				provinces.put(province.getId(), province);
			}
			return net.tfminecraft.simplefactions.guild.hub.VehicleFrameworkTrackProvinces.sample(
					Cache.worldName, provinceGrid, provinces);
		}, () -> {
			// Forget cached rail routes first; that also drops the graph's routes.
			net.tfminecraft.simplefactions.guild.network.TradeGraph.forgetRoutes();
			net.tfminecraft.simplefactions.guild.network.TradeGraph.refresh(provinceManager);
			recalculate.run();
		},
				message -> getLogger().warning("[SimpleFactions] " + message));
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

	public VehicleReleaseSessionManager getVehicleReleaseSessionManager() {
		return vehicleReleaseSessionManager;
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

	public FactionVehicleGiveService getFactionVehicleGiveService() {
		return factionVehicleGiveService;
	}

	public VehicleHandoverSessionManager getVehicleHandoverSessionManager() {
		return vehicleHandoverSessionManager;
	}

	public VehicleHandoverService getVehicleHandoverService() {
		return vehicleHandoverService;
	}

	public void saveVehicleFees() {
		vehicleFeeStore.save();
	}

	/** Remembers the member key of the character a vehicle was just handed to, for a later transfer fee. */
	public void recordVehicleOwner(String vehicleUuid, String memberKey) {
		vehicleFeeStore.setLastOwner(vehicleUuid, memberKey);
		vehicleFeeStore.save();
	}

    /** Retains captured character payers; a current account session cannot replace a registry identity. */
    public void recordVehicleOwners() {
        for (var record : vehicleRegistry.getAll()) {
            if (record.getMode() != net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode.PERSONAL) {
                vehicleFeeStore.forgetVehicle(record.getVehicleUuid());
            } else if (record.getPlayerUuid() != null && vehicleFeeStore.getLastOwner(record.getVehicleUuid()) == null) {
                vehicleFeeStore.setLastOwner(record.getVehicleUuid(), record.getPlayerUuid().toString());
            }
        }
        vehicleFeeStore.save();
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

	/** Returns false when the vehicle registry could not be written. */
	public boolean saveVehicleRegistry() {
		boolean saved = true;
		if (vehicleRegistryPersistence != null) {
			saved = vehicleRegistryPersistence.save();
		}
		if (vehicleMaintenancePersistence != null) {
			vehicleMaintenancePersistence.save();
		}
		vehicleFeeStore.save();
		return saved;
	}

	private void registerRpCharactersChatHooks() {
		getServer().getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onPluginEnable(PluginEnableEvent event) {
				if ("RPCharacters".equalsIgnoreCase(event.getPlugin().getName())) {
					net.tfminecraft.simplefactions.integration.rpcharacters.chat.RpCharactersChatIntegration.register();
				}
			}
		}, this);
		net.tfminecraft.simplefactions.integration.rpcharacters.chat.RpCharactersChatIntegration.register();
	}

	/** The VFBuilders construction freeze and registration fee, which need no vehicle stage. */
	private void registerVehicleIntegrationHooks() {
		getServer().getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onPluginEnable(PluginEnableEvent event) {
				if ("VFBuilders".equalsIgnoreCase(event.getPlugin().getName())
						|| "VehicleFramework".equalsIgnoreCase(event.getPlugin().getName())) {
					registerConstructionFreeze();
					registerRegistrationFee();
				}
			}
		}, this);
		registerConstructionFreeze();
		registerRegistrationFee();
	}

	/**
	 * The vehicle stage, once factions have started and VehicleFramework is enabled:
	 * payer migration, the owner migration, the leader and upkeep reconciles, then the
	 * vehicle listeners, and last the ownership authority VehicleFramework waits for.
	 * Without VehicleFramework 2.13 in characters mode only the payer migration and the
	 * listeners run, with claims and restamps off.
	 */
	void startVehicles() {
		if (vehiclesStarted || !started || !getServer().getPluginManager().isPluginEnabled("VehicleFramework")) {
			return;
		}
		vehiclesStarted = true;
		VehicleOwnerApi api = vehicleApiProbe.get();
		VehicleOwnerWrites.use(api);
		boolean full = VehicleOwnerWrites.ready();
		java.nio.file.Path folder = getDataFolder().toPath();
		VehicleQuarantine quarantine = new VehicleQuarantine(folder);
		VehicleQuarantine.use(quarantine);
		if (api != null) VehicleOwnershipServices.registerQuarantine(getServer().getServicesManager(), this, quarantine);
		try {
			net.tfminecraft.simplefactions.vehicles.registry.VehiclePayerMigration.startup(folder, vehicleRegistry);
			if (full) {
				VehicleOwnerMigration.Outcome outcome = new VehicleOwnerMigration(folder, api, vehicleRegistry,
						vehicleIdentity, vehicleBackups.apply(vehicleDatabase())).run();
				if (outcome.ran()) {
					getLogger().info("Vehicle owner migration: " + outcome.migrated() + " migrated, " + outcome.unchanged()
							+ " unchanged, " + outcome.quarantined() + " quarantined, " + outcome.gone() + " gone");
				}
			}
			quarantine.load(VehicleOwnerMigration.quarantine(folder));
		} catch (Exception error) {
			failFinancialState(error);
			return;
		}
		if (!full) {
			getLogger().severe("VehicleFramework 2.13 or newer in characters mode is needed for vehicle ownership; "
					+ "vehicle claims and leader stamps are off, and upkeep is charged from registry rows");
		} else {
			try {
				LeaderStamps.reconcile(vehicleRegistry);
				int filled = net.tfminecraft.simplefactions.vehicles.registry.RegistryAccounts.fill(folder, vehicleRegistry,
						character -> net.tfminecraft.simplefactions.identity.Members.accountId(
								net.tfminecraft.simplefactions.identity.Members.key(character)),
						java.util.List.of(VehicleOwnerMigration.accounts(folder),
								net.tfminecraft.simplefactions.vehicles.registry.VehiclePayerMigration.accounts(folder)));
				if (filled > 0) getLogger().info("Cached the account on " + filled + " personal vehicle rows");
				quarantine.setFindings(upkeepReconcile().run());
			} catch (Exception error) {
				getLogger().log(java.util.logging.Level.SEVERE, "Vehicle reconcile failed, so vehicle listeners and the "
						+ "ownership authority stay off until a restart; VehicleFramework refuses player ownership changes", error);
				return;
			}
		}
		registerVehicleIntegration(full);
		if (full) {
			VehicleOwnershipServices.registerAuthority(getServer().getServicesManager(), this,
					() -> started && LeaderStamps.enabled());
			LeaderStamps.enable(true);
			getServer().getScheduler().runTaskTimer(this, this::reconcileVehicleUpkeep,
					VEHICLE_RECONCILE_TICKS, VEHICLE_RECONCILE_TICKS);
			net.tfminecraft.simplefactions.estate.EstateBridge.registerVehicles(this, folder, vehicleRegistry, vehicleFeeStore);
		}
	}

	private java.nio.file.Path vehicleDatabase() {
		return getServer().getPluginManager().getPlugin("VehicleFramework").getDataFolder().toPath()
				.resolve("data/vehicles.db");
	}

	/** The daily upkeep reconcile; its findings replace the previous ones in the staff list. */
	void reconcileVehicleUpkeep() {
		try {
			VehicleQuarantine.current().setFindings(upkeepReconcile().run());
		} catch (RuntimeException error) {
			getLogger().log(java.util.logging.Level.WARNING, "Daily vehicle upkeep reconcile failed", error);
		}
	}

	private VehicleUpkeepReconcile upkeepReconcile() {
		return new VehicleUpkeepReconcile(VehicleOwnerWrites.api(), vehicleRegistry,
				(vehicle, type, character, account) -> {
					var payments = net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.current();
					return payments != null && payments.personal(vehicle, type, character, account);
				},
				SimpleFactions::leadsAFaction,
				character -> net.tfminecraft.simplefactions.identity.Members.accountId(
						net.tfminecraft.simplefactions.identity.Members.key(character)),
				getLogger());
	}

	static boolean leadsAFaction(java.util.UUID character) {
		String key = net.tfminecraft.simplefactions.identity.Members.key(character);
		for (Faction faction : FactionManager.factions) {
			if (key.equals(faction.getLeader())) return true;
		}
		return false;
	}

	private void registerRegistrationFee() {
		if (registrationFeeRegistered || !getServer().getPluginManager().isPluginEnabled("VFBuilders")
				|| !getServer().getPluginManager().isPluginEnabled("VehicleFramework")) {
			return;
		}
		registrationFeeRegistered = true;
		try {
			Class.forName("net.tfminecraft.vfbuilders.events.VehicleConstructionCancelEvent");
		} catch (ClassNotFoundException | LinkageError e) {
			// The listener needs the confirm click and cancel event from VFBuilders 2.1.0.
			getLogger().warning("VFBuilders is older than 2.1.0, so vehicle registration fees are off; "
					+ "update it to charge them");
			return;
		}
		getServer().getPluginManager().registerEvents(new net.tfminecraft.simplefactions.vehicles.fees
				.VehicleRegistrationFeeListener(vehicleFeeStore, vehicleFeeConfirmations, this::saveVehicleFees), this);
	}

	private void registerConstructionFreeze() {
		if (constructionFreezeRegistered || !getServer().getPluginManager().isPluginEnabled("VFBuilders")) {
			return;
		}
		constructionFreezeRegistered = true;
		try {
			Class.forName("net.tfminecraft.vfbuilders.api.ConstructionFreeze");
		} catch (ClassNotFoundException | LinkageError e) {
			// Loading VfBuildersConstructionFreeze would fail on a VFBuilders without this API.
			getLogger().warning("VFBuilders has no ConstructionFreeze API; update it so vehicle "
					+ "projects freeze with postponed battles");
			return;
		}
		net.tfminecraft.simplefactions.war.freeze.VfBuildersConstructionFreeze.register(this);
	}

	/** Step 5 of the vehicle stage, which runs once per start. */
	private void registerVehicleIntegration(boolean full) {
		getServer().getPluginManager().registerEvents(vehicleIntegrationListener, this);
		getServer().getPluginManager().registerEvents(vehicleRegistryClaimListener, this);
		getServer().getPluginManager().registerEvents(vehicleTransferListener, this);
		getServer().getPluginManager().registerEvents(factionVehicleReleaseListener, this);
		getServer().getPluginManager().registerEvents(vehicleMaintenancePayListener, this);
		getServer().getPluginManager().registerEvents(vehicleMaintenanceRepairListener, this);
		getServer().getPluginManager().registerEvents(battleVehicleEligibilityListener, this);
		getServer().getPluginManager().registerEvents(vehicleHandoverListener, this);
		getServer().getPluginManager().registerEvents(vehicleReclaimFeeListener, this);
		if (full) {
			getServer().getPluginManager().registerEvents(vehicleSpawnListener, this);
			getServer().getPluginManager().registerEvents(new VehicleOwnershipGate(vehicleRegistry,
					vehicleRegistryClaimService, vehicleFeeStore, vehicleFeeConfirmations, this::saveVehicleFees,
					getLogger()), this);
		} else {
			getServer().getPluginManager().registerEvents(new VehicleClaimsOff(), this);
		}
		// VehicleFramework loads its vehicles as it enables, so wait before reading owners.
		getServer().getScheduler().runTaskLater(this, this::recordVehicleOwners, 200L);
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
