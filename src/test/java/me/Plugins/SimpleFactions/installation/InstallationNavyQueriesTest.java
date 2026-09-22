package me.Plugins.SimpleFactions.installation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class InstallationNavyQueriesTest {

	@Test
	void hasOperationalPort_trueWhenFactionOwnsCompletedPort() {
		Faction faction = factionWithInstallations(List.of(
				new Installation("port-1", "Harbour", InstallationKind.PORT, 10, 0, 0, 1L)));
		assertTrue(InstallationNavyQueries.hasOperationalPort(faction));
	}

	@Test
	void hasOperationalPort_falseWhenOnlyFort() {
		Faction faction = factionWithInstallations(List.of(
				new Installation("fort-1", "Fort", InstallationKind.FORT, 10, 0, 0, 1L)));
		assertFalse(InstallationNavyQueries.hasOperationalPort(faction));
	}

	@Test
	void hasOperationalPort_falseWhenEmpty() {
		Faction faction = factionWithInstallations(List.of());
		assertFalse(InstallationNavyQueries.hasOperationalPort(faction));
	}

	@Test
	void hasOperationalPort_falseWhenOnlyPendingConstruction() {
		Faction faction = mock(Faction.class);
		InstallationHandler handler = mock(InstallationHandler.class);
		when(faction.getInstallationHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(List.of());
		InstallationConstruction pending = mock(InstallationConstruction.class);
		when(pending.getKind()).thenReturn(InstallationKind.PORT);
		when(handler.getPendingConstruction()).thenReturn(pending);
		assertFalse(InstallationNavyQueries.hasOperationalPort(faction));
	}

	@Test
	void hasOperationalPort_vassalPortDoesNotCountForOverlord() {
		Faction overlord = factionWithInstallations(List.of());
		Faction vassal = factionWithInstallations(List.of(
				new Installation("port-v", "Vassal Port", InstallationKind.PORT, 20, 0, 0, 1L)));
		assertTrue(InstallationNavyQueries.hasOperationalPort(vassal));
		assertFalse(InstallationNavyQueries.hasOperationalPort(overlord));
	}

	@Test
	void hasOperationalPort_falseWhenFactionNull() {
		assertFalse(InstallationNavyQueries.hasOperationalPort(null));
	}

	private static Faction factionWithInstallations(List<Installation> installations) {
		Faction faction = mock(Faction.class);
		InstallationHandler handler = mock(InstallationHandler.class);
		when(faction.getInstallationHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(installations);
		return faction;
	}
}
