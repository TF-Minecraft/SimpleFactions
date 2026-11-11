package net.tfminecraft.simplefactions.war.wounds;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * Mock factions whose regiments really change size, registered with a mocked FactionManager,
 * plus a WarManager that keeps wars in memory and does not save them.
 */
public final class WoundFixture implements AutoCloseable {
	public final Map<String, Faction> factions = new HashMap<>();
	public final Map<String, Guild> guilds = new HashMap<>();
	private final MockedStatic<FactionManager> factionManager;
	private final MockedStatic<WarManager> warManager;
	private final List<War> savedWars;
	private final int savedLives;
	private final int savedDeaths;

	public WoundFixture() {
		savedLives = Cache.warBattleLivesPerRegiment;
		savedDeaths = Cache.warBattleDeathsPerRegimentLoss;
		Cache.warBattleLivesPerRegiment = 5;
		Cache.warBattleDeathsPerRegimentLoss = 5;
		savedWars = new ArrayList<>(WarManager.get());
		WarManager.get().clear();
		factionManager = mockStatic(FactionManager.class);
		factionManager.when(() -> FactionManager.getByString(anyString()))
				.thenAnswer(call -> factions.get(call.getArgument(0, String.class).toLowerCase(Locale.ROOT)));
		factionManager.when(() -> FactionManager.getGuildByString(anyString()))
				.thenAnswer(call -> guilds.get(call.getArgument(0, String.class)));
		// Wars stay in memory: persist does nothing, everything else is real.
		warManager = mockStatic(WarManager.class, call -> call.getMethod().getName().equals("persist")
				? null : call.callRealMethod());
	}

	public Faction faction(String id, Regiment... regiments) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id.toUpperCase(Locale.ROOT));
		Military military = mock(Military.class);
		List<Regiment> list = new ArrayList<>(List.of(regiments));
		when(military.getRegiments()).thenReturn(list);
		when(military.getRegiment(anyString())).thenAnswer(call -> list.stream()
				.filter(regiment -> regiment.getId().equalsIgnoreCase(call.getArgument(0, String.class)))
				.findFirst().orElse(null));
		when(faction.getMilitary()).thenReturn(military);
		factions.put(id.toLowerCase(Locale.ROOT), faction);
		return faction;
	}

	/** A regiment whose slots change when the code sets them; {@code free} law slots stay fixed. */
	public static Regiment regiment(String id, int current, int free) {
		AtomicInteger slots = new AtomicInteger(current);
		Regiment regiment = mock(Regiment.class);
		when(regiment.getId()).thenReturn(id);
		when(regiment.getName()).thenReturn("Name " + id);
		when(regiment.getFreeSlots()).thenReturn(free);
		when(regiment.getCurrentSlots()).thenAnswer(call -> slots.get());
		when(regiment.getPaidSlots()).thenAnswer(call -> Math.max(0, slots.get() - free));
		doAnswer(call -> {
			slots.set(call.getArgument(0, Integer.class));
			return null;
		}).when(regiment).setCurrentSlots(anyInt());
		return regiment;
	}

	public War war(int id, Faction attacker, Faction defender) {
		War war = new War(id, attacker, defender);
		WarManager.get().add(war);
		return war;
	}

	@Override
	public void close() {
		warManager.close();
		factionManager.close();
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
		Cache.warBattleLivesPerRegiment = savedLives;
		Cache.warBattleDeathsPerRegimentLoss = savedDeaths;
	}
}
