package me.Plugins.SimpleFactions.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Database.InstallationConstructionData;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class InstallationTransferServiceTest {

	@Test
	void transfer_movesCompletedInstallToNewOwner() {
		Fixture from = faction("from", "Alice");
		Fixture to = faction("to", "Bob");
		Installation port = new Installation("port-1", "Harbour", InstallationKind.PORT, 42, 0, 0, 1L);
		from.handler.acceptTransferred(port);

		InstallationTransferService.transfer(from.faction, to.faction, 42);

		assertNull(from.handler.getById("port-1"));
		assertNotNull(to.handler.getById("port-1"));
		assertEquals(42, to.handler.getById("port-1").getProvince());
		assertTrue(from.handler.getAll().isEmpty());
	}

	@Test
	void transfer_sameFaction_isNoOp() {
		Fixture from = faction("from", "Alice");
		Installation port = new Installation("port-1", "Harbour", InstallationKind.PORT, 42, 0, 0, 1L);
		from.handler.acceptTransferred(port);

		InstallationTransferService.transfer(from.faction, from.faction, 42);

		assertNotNull(from.handler.getById("port-1"));
	}

	@Test
	void transfer_clearsPendingConstructionOnThatProvince() {
		Fixture from = faction("from", "Alice");
		Fixture to = faction("to", "Bob");
		from.handler.loadConstruction(constructionData(42));
		assertNotNull(from.handler.getPendingConstruction());

		InstallationTransferService.transfer(from.faction, to.faction, 42);

		assertNull(from.handler.getPendingConstruction());
		assertTrue(to.handler.getAll().isEmpty());
		assertNull(to.handler.getPendingConstruction());
	}

	@Test
	void transfer_thenOldOwnerProvinceLost_keepsInstallOnNewOwner() {
		Fixture from = faction("from", "Alice");
		Fixture to = faction("to", "Bob");
		Installation port = new Installation("port-1", "Harbour", InstallationKind.PORT, 42, 0, 0, 1L);
		from.handler.acceptTransferred(port);

		InstallationTransferService.transfer(from.faction, to.faction, 42);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			from.handler.onProvinceLost(42);
		}

		assertNull(from.handler.getById("port-1"));
		assertNotNull(to.handler.getById("port-1"));
		assertEquals(42, to.handler.getById("port-1").getProvince());
	}

	@Test
	void onProvinceLost_withoutTransfer_dissolvesInstall() {
		Fixture from = faction("from", "Alice");
		Installation port = new Installation("port-1", "Harbour", InstallationKind.PORT, 42, 0, 0, 1L);
		from.handler.acceptTransferred(port);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			from.handler.onProvinceLost(42);
		}

		assertNull(from.handler.getById("port-1"));
		assertTrue(from.handler.getAll().isEmpty());
	}

	@Test
	void onProvinceLost_clearsPendingConstruction() {
		Fixture from = faction("from", "Alice");
		from.handler.loadConstruction(constructionData(42));

		from.handler.onProvinceLost(42);

		assertNull(from.handler.getPendingConstruction());
	}

	private static InstallationConstructionData constructionData(int province) {
		InstallationConstructionData data = new InstallationConstructionData();
		data.id = "port-build";
		data.name = "Harbour";
		data.kind = "port";
		data.province = province;
		data.centerX = 0;
		data.centerZ = 0;
		data.timeLeft = 60;
		data.startedAt = 1L;
		return data;
	}

	private static Fixture faction(String id, String leader) {
		Fixture fx = new Fixture();
		fx.faction = mock(Faction.class);
		fx.handler = new InstallationHandler(fx.faction);
		when(fx.faction.getId()).thenReturn(id);
		when(fx.faction.getLeader()).thenReturn(leader);
		when(fx.faction.getRGB()).thenReturn("#ffffff");
		when(fx.faction.getInstallationHandler()).thenReturn(fx.handler);
		return fx;
	}

	private static final class Fixture {
		Faction faction;
		InstallationHandler handler;
	}
}
