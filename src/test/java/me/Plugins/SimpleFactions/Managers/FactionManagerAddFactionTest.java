package me.Plugins.SimpleFactions.Managers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;

class FactionManagerAddFactionTest {

	private List<Faction> previous;

	@BeforeEach
	void saveList() {
		previous = FactionManager.factions;
		FactionManager.factions = new ArrayList<>();
	}

	@AfterEach
	void restoreList() {
		FactionManager.factions = previous;
	}

	@Test
	void addFaction_emptyProvinces_doesNotSetCapital() {
		Faction f = mock(Faction.class);
		when(f.getProvinces()).thenReturn(List.of());
		when(f.getCapital()).thenReturn(-1);

		FactionManager.addFaction(f);

		verify(f, never()).setCapital(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(true));
	}

	@Test
	void addFaction_hasProvincesAndNoCapital_setsCapitalFromMainGuild() {
		Faction f = mock(Faction.class);
		when(f.getProvinces()).thenReturn(List.of(12));
		when(f.getCapital()).thenReturn(-1);
		Guild main = mock(Guild.class);
		when(main.hasCapital()).thenReturn(true);
		when(main.getCapital()).thenReturn(12);
		when(f.getOrCreateMainGuild()).thenReturn(main);

		FactionManager.addFaction(f);

		verify(f).setCapital(12, true);
	}

	@Test
	void addFaction_alreadyHasCapital_doesNotSetCapital() {
		Faction f = mock(Faction.class);
		when(f.getProvinces()).thenReturn(List.of(12));
		when(f.getCapital()).thenReturn(12);

		FactionManager.addFaction(f);

		verify(f, never()).setCapital(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(true));
	}
}
