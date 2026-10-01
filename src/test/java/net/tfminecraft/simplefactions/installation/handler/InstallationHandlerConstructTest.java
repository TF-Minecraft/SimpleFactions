package net.tfminecraft.simplefactions.installation.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.simplefactions.map.ProvinceSpatial;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.handler.ProvinceHandler;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class InstallationHandlerConstructTest {
	private static final int PROVINCE = 42;
	private static final int X = 10;
	private static final int Z = 20;
	private static final String DISPLAY_NAME = "green_fort";

	@Test
	void upgradeUsesSharedQueueAndFinishesAtNextLevel() {
		Fixture fx = fixture();
		Installation station = new Installation("central_station", "Central", InstallationKind.TRAIN_STATION, PROVINCE, X, Z, 1L);
		fx.handler.acceptTransferred(station);
		try (TestMocks mocks = testMocks(fx, false)) {
			ConstructResult result = fx.handler.upgrade(station.getId());
			assertTrue(result.isSuccess());
			assertTrue(fx.handler.getPendingConstruction().isUpgrade());
			assertEquals(1, station.getLevel());
			assertFalse(fx.handler.construct(InstallationKind.FORT, "new", PROVINCE, X, Z).isSuccess());
			for (int i = 0; i < 100; i++) fx.handler.tick();
			assertEquals(2, station.getLevel());
			assertNull(fx.handler.getPendingConstruction());
		}
	}

	@Test
	void pendingUpgradePersistsAndCanBeCancelled() {
		Fixture fx = fixture();
		fx.handler.acceptTransferred(new Installation("central_station", "Central", InstallationKind.TRAIN_STATION, PROVINCE, X, Z, 1L));
		try (TestMocks mocks = testMocks(fx, false)) {
			assertTrue(fx.handler.upgrade("central_station").isSuccess());
			var data = fx.handler.serializeConstruction();
			assertTrue(data.upgrade);
			InstallationHandler restored = new InstallationHandler(fx.faction);
			restored.acceptTransferred(fx.handler.getById("central_station"));
			restored.loadConstruction(data);
			assertTrue(restored.getPendingConstruction().isUpgrade());
			assertEquals(data.timeLeft, restored.serializeConstruction().timeLeft);
			assertTrue(restored.cancelPending("central_station"));
			assertNull(restored.getPendingConstruction());
		}
	}

	@Test
	void deconstructingInstallationCancelsItsPendingUpgrade() {
		Fixture fx = fixture();
		Installation station = new Installation(
				"central_station", "Central", InstallationKind.TRAIN_STATION,
				PROVINCE, X, Z, 1L);
		fx.handler.acceptTransferred(station);
		try (TestMocks mocks = testMocks(fx, false)) {
			assertTrue(fx.handler.upgrade(station.getId()).isSuccess());

			ConstructResult result = fx.handler.deconstruct(station.getId());

			assertTrue(result.isSuccess());
			assertNull(fx.handler.getById(station.getId()));
			assertNull(fx.handler.getPendingConstruction());
		}
	}

	@Test
	void missedUpkeepDropsLevelAndCancelsUpgradeButLevelOneDissolves() {
		Fixture fx = fixture();
		Bank bank = mock(Bank.class);
		when(bank.getWealth()).thenReturn(0.0);
		when(fx.faction.getBank()).thenReturn(bank);
		Installation station = new Installation("central_station", "Central", InstallationKind.TRAIN_STATION, PROVINCE, X, Z, 1L, 2);
		fx.handler.acceptTransferred(station);
		try (TestMocks mocks = testMocks(fx, false)) {
			assertTrue(fx.handler.upgrade("central_station").isSuccess());
			fx.handler.payDailyUpkeep();
			assertEquals(1, station.getLevel());
			assertNull(fx.handler.getPendingConstruction());
			verify(bank, never()).withdraw(any());
			assertTrue(fx.handler.upgrade("central_station").isSuccess());
			fx.handler.payDailyUpkeep();
			assertNull(fx.handler.getById("central_station"));
			assertNull(fx.handler.getPendingConstruction());
		}
	}

	@Test
	void constructInstant_fort_success() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false)) {
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertTrue(result.isSuccess());
			assertNotNull(result.getInstallation());
			assertNotNull(fx.handler.getById("green_fort"));
			assertNull(fx.handler.getPendingConstruction());
			assertNotNull(fx.handler.getByProvince(InstallationKind.FORT, PROVINCE));
			assertTrue(result.getMessage().contains("Constructed"));
			mocks.verifyMapEnqueue();
		}
	}

	@Test
	void construct_queuesPendingWithoutRegistering() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false)) {
			ConstructResult result = fx.handler.construct(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertTrue(result.isSuccess());
			assertNotNull(fx.handler.getPendingConstruction());
			assertNull(fx.handler.getById("green_fort"));
			assertTrue(result.getMessage().contains("remaining"));
		}
	}

	@Test
	void constructInstant_unknownKind_fails() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false)) {
			ConstructResult result = fx.handler.constructInstant(null, DISPLAY_NAME, PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("Unknown installation type"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_blankName_fails() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false)) {
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, "   ", PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("Name required"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_alreadyPending_failsAndLeavesPending() {
		Fixture fx = fixture();
		fx.handler.loadConstruction(pendingData());
		try (TestMocks mocks = testMocks(fx, false)) {
			org.mockito.Mockito.clearInvocations(mocks.map);
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("already building"));
			assertNotNull(fx.handler.getPendingConstruction());
			assertNull(fx.handler.getById("green_fort"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_doesNotOwnProvince_fails() {
		Fixture fx = fixture();
		when(fx.provinceHandler.hasProvince(PROVINCE)).thenReturn(false);
		try (TestMocks mocks = testMocks(fx, false)) {
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("doesn't own this province"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_duplicateKindInProvince_fails() {
		Fixture fx = fixture();
		fx.handler.acceptTransferred(
				new Installation("existing-fort", "Existing", InstallationKind.FORT, PROVINCE, 0, 0, 1L));
		try (TestMocks mocks = testMocks(fx, false)) {
			org.mockito.Mockito.clearInvocations(mocks.map);
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("already has a fort"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_invalidProvince_fails() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, true)) {
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("no province"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_seaProvince_fails() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false, true)) {
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("cannot construct on water"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_duplicateId_fails() {
		Fixture fx = fixture();
		fx.handler.acceptTransferred(
				new Installation("green_fort", "Other", InstallationKind.PORT, 99, 0, 0, 1L));
		try (TestMocks mocks = testMocks(fx, false)) {
			org.mockito.Mockito.clearInvocations(mocks.map);
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.FORT, DISPLAY_NAME, PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("already exists"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_portTooFarFromSea_fails() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false)) {
			mocks.portProximity(false);

			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.PORT, "harbour", PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("blocks of sea or river"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_portNearSea_succeeds() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false)) {
			mocks.portProximity(true);

			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.PORT, "harbour", PROVINCE, X, Z);

			assertTrue(result.isSuccess());
			assertNotNull(fx.handler.getById("harbour"));
			assertNotNull(fx.handler.getByProvince(InstallationKind.PORT, PROVINCE));
			mocks.verifyMapEnqueue();
		}
	}

	@Test
	void constructInstant_trainStation_succeedsWithoutPortPlacementRule() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false)) {
			mocks.portProximity(false);

			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.TRAIN_STATION, "central_station", PROVINCE, X, Z);

			assertTrue(result.isSuccess());
			assertNotNull(fx.handler.getByProvince(InstallationKind.TRAIN_STATION, PROVINCE));
			mocks.verifyMapEnqueue();
		}
	}

	@Test
	void constructInstant_duplicateTrainStationInProvince_fails() {
		Fixture fx = fixture();
		fx.handler.acceptTransferred(new Installation(
				"existing_station", "Existing Station", InstallationKind.TRAIN_STATION, PROVINCE, 0, 0, 1L));
		try (TestMocks mocks = testMocks(fx, false)) {
			org.mockito.Mockito.clearInvocations(mocks.map);
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.TRAIN_STATION, "central_station", PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("already has a train station"));
			mocks.verifyNoMapEnqueue();
		}
	}

	@Test
	void constructInstant_trainStationOnSea_fails() {
		Fixture fx = fixture();
		try (TestMocks mocks = testMocks(fx, false, true)) {
			ConstructResult result = fx.handler.constructInstant(
					InstallationKind.TRAIN_STATION, "central_station", PROVINCE, X, Z);

			assertFalse(result.isSuccess());
			assertTrue(result.getMessage().contains("cannot construct on water"));
			mocks.verifyNoMapEnqueue();
		}
	}

	private static net.tfminecraft.simplefactions.database.InstallationConstructionData pendingData() {
		net.tfminecraft.simplefactions.database.InstallationConstructionData data =
				new net.tfminecraft.simplefactions.database.InstallationConstructionData();
		data.id = "pending-fort";
		data.name = "Pending";
		data.kind = "fort";
		data.province = PROVINCE;
		data.centerX = 0;
		data.centerZ = 0;
		data.timeLeft = 60;
		data.startedAt = 1L;
		return data;
	}

	private static Fixture fixture() {
		Fixture fx = new Fixture();
		fx.faction = mock(Faction.class);
		fx.provinceHandler = mock(ProvinceHandler.class);
		fx.handler = new InstallationHandler(fx.faction);
		when(fx.faction.getId()).thenReturn("faction_a");
		when(fx.faction.getLeader()).thenReturn("Alice");
		when(fx.faction.getRGB()).thenReturn("#ffffff");
		when(fx.faction.getProvinceHandler()).thenReturn(fx.provinceHandler);
		when(fx.faction.getInstallationHandler()).thenReturn(fx.handler);
		when(fx.provinceHandler.hasProvince(PROVINCE)).thenReturn(true);
		return fx;
	}

	private static TestMocks testMocks(Fixture fx, boolean invalidProvince) {
		return testMocks(fx, invalidProvince, false);
	}

	private static TestMocks testMocks(Fixture fx, boolean invalidProvince, boolean sea) {
		ProvinceManager provinceManager = mock(ProvinceManager.class);
		Province province = invalidProvince ? new Province() : mock(Province.class);
		if (!invalidProvince) {
			when(province.isValid()).thenReturn(true);
			when(province.isSea()).thenReturn(sea);
		}
		when(provinceManager.get(PROVINCE)).thenReturn(province);

		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(provinceManager);

		MapSystem map = mock(MapSystem.class);

		MockedStatic<SimpleFactions> simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);

		MockedStatic<FactionManager> factionManager = mockStatic(FactionManager.class);
		factionManager.when(FactionManager::getMap).thenReturn(map);

		MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);

		MockedStatic<InstallationConfigLoader> config = mockStatic(InstallationConfigLoader.class);
		config.when(() -> InstallationConfigLoader.getConstructionTimeSeconds(any()))
				.thenReturn(60);
		config.when(() -> InstallationConfigLoader.getConstructionTimeSeconds(any(InstallationKind.class), anyInt()))
				.thenReturn(100);
		config.when(() -> InstallationConfigLoader.getMaximumLevel(any(InstallationKind.class))).thenReturn(3);
		config.when(() -> InstallationConfigLoader.getDailyUpkeep(any(InstallationKind.class), anyInt())).thenReturn(35.0);

		MockedStatic<ProvinceSpatial> spatial = mockStatic(ProvinceSpatial.class);
		spatial.when(() -> ProvinceSpatial.withinConfiguredPortSeaProximity(anyInt(), anyInt()))
				.thenReturn(true);

		return new TestMocks(map, factionManager, simpleFactions, bukkit, config, spatial);
	}

	private static final class Fixture {
		Faction faction;
		ProvinceHandler provinceHandler;
		InstallationHandler handler;
	}

	private static final class TestMocks implements AutoCloseable {
		final MapSystem map;
		private final MockedStatic<FactionManager> factionManager;
		private final MockedStatic<SimpleFactions> simpleFactions;
		private final MockedStatic<Bukkit> bukkit;
		private final MockedStatic<InstallationConfigLoader> config;
		private final MockedStatic<ProvinceSpatial> spatial;

		private TestMocks(
				MapSystem map,
				MockedStatic<FactionManager> factionManager,
				MockedStatic<SimpleFactions> simpleFactions,
				MockedStatic<Bukkit> bukkit,
				MockedStatic<InstallationConfigLoader> config,
				MockedStatic<ProvinceSpatial> spatial) {
			this.map = map;
			this.factionManager = factionManager;
			this.simpleFactions = simpleFactions;
			this.bukkit = bukkit;
			this.config = config;
			this.spatial = spatial;
		}

		void portProximity(boolean nearSea) {
			spatial.when(() -> ProvinceSpatial.withinConfiguredPortSeaProximity(anyInt(), anyInt()))
					.thenReturn(nearSea);
		}

		void verifyMapEnqueue() {
			org.mockito.Mockito.verify(map).enqueue(eq("nation"), eq("#ffffff"));
		}

		void verifyNoMapEnqueue() {
			org.mockito.Mockito.verify(map, org.mockito.Mockito.never())
					.enqueue(anyString(), anyString());
		}

		@Override
		public void close() {
			spatial.close();
			config.close();
			bukkit.close();
			factionManager.close();
			simpleFactions.close();
		}
	}
}
