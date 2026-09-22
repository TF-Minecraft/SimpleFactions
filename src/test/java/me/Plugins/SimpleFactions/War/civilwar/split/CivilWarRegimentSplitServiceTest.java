package me.Plugins.SimpleFactions.War.civilwar.split;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarRegimentSplitService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Objects.Faction;

class CivilWarRegimentSplitServiceTest {

	@Test
	void power50_splitsEachNonLevyType_leavesVassalUntouched() {
		Regiment hostProfessional = regiment("professional", 10, false);
		Regiment hostMilitia = regiment("militia", 4, false);
		Regiment hostLevy = regiment("levy", 20, true);
		Regiment rebelProfessional = regiment("professional", 0, false);
		Regiment rebelMilitia = regiment("militia", 0, false);
		Regiment vassalProfessional = regiment("professional", 8, false);

		Faction host = faction("host", List.of(hostProfessional, hostMilitia, hostLevy));
		Faction rebels = faction("rebels", List.of(rebelProfessional, rebelMilitia));
		Faction vassal = faction("vassal", List.of(vassalProfessional));

		Map<String, Integer> moved = CivilWarRegimentSplitService.split(host, rebels, 50);

		assertEquals(5, moved.get("professional"));
		assertEquals(2, moved.get("militia"));
		assertEquals(5, hostProfessional.getCurrentSlots());
		assertEquals(2, hostMilitia.getCurrentSlots());
		assertEquals(20, hostLevy.getCurrentSlots());
		assertEquals(5, rebelProfessional.getCurrentSlots());
		assertEquals(2, rebelMilitia.getCurrentSlots());
		assertEquals(8, vassalProfessional.getCurrentSlots());
	}

	@Test
	void power50_transfersPaidOnly_ignoresFreeSlots() {
		Regiment hostMilitia = regiment("militia", 10, 6, false);
		Regiment rebelMilitia = regiment("militia", 6, 6, false);
		Faction host = faction("host", List.of(hostMilitia));
		Faction rebels = faction("rebels", List.of(rebelMilitia));

		Map<String, Integer> moved = CivilWarRegimentSplitService.split(host, rebels, 50);

		assertEquals(2, moved.get("militia"));
		assertEquals(8, hostMilitia.getCurrentSlots());
		assertEquals(6, hostMilitia.getFreeSlots());
		assertEquals(2, rebelMilitia.getCurrentSlots());
		assertEquals(0, rebelMilitia.getFreeSlots());
	}

	@Test
	void allFreeMilitia_transfersNothing_zerosRebelGrant() {
		Regiment hostMilitia = regiment("militia", 6, 6, false);
		Regiment rebelMilitia = regiment("militia", 6, 6, false);
		Faction host = faction("host", List.of(hostMilitia));
		Faction rebels = faction("rebels", List.of(rebelMilitia));

		Map<String, Integer> moved = CivilWarRegimentSplitService.split(host, rebels, 50);

		assertTrue(moved.isEmpty());
		assertEquals(6, hostMilitia.getCurrentSlots());
		assertEquals(6, hostMilitia.getFreeSlots());
		assertEquals(0, rebelMilitia.getCurrentSlots());
		assertEquals(0, rebelMilitia.getFreeSlots());
	}

	@Test
	void power0_movesNothing() {
		Regiment hostProfessional = regiment("professional", 10, false);
		Regiment rebelProfessional = regiment("professional", 0, false);
		Faction host = faction("host", List.of(hostProfessional));
		Faction rebels = faction("rebels", List.of(rebelProfessional));

		Map<String, Integer> moved = CivilWarRegimentSplitService.split(host, rebels, 0);

		assertTrue(moved.isEmpty());
		assertEquals(10, hostProfessional.getCurrentSlots());
		assertEquals(0, rebelProfessional.getCurrentSlots());
	}

	@Test
	void nullTempRebels_doesNotTouchHost() {
		Regiment hostProfessional = regiment("professional", 10, false);
		Faction host = faction("host", List.of(hostProfessional));

		Map<String, Integer> moved = CivilWarRegimentSplitService.split(host, null, 50);

		assertTrue(moved.isEmpty());
		assertEquals(10, hostProfessional.getCurrentSlots());
	}

	@Test
	void rollback_restoresHostAndRebelSlots() {
		Regiment hostProfessional = regiment("professional", 5, false);
		Regiment rebelProfessional = regiment("professional", 5, false);
		Faction host = faction("host", List.of(hostProfessional));
		Faction rebels = faction("rebels", List.of(rebelProfessional));

		CivilWarRegimentSplitService.rollback(host, rebels, Map.of("professional", 5));

		assertEquals(10, hostProfessional.getCurrentSlots());
		assertEquals(0, rebelProfessional.getCurrentSlots());
	}

	@Test
	void mergeRemaining_addsRebelSlotsOntoHost() {
		Regiment hostProfessional = regiment("professional", 5, false);
		Regiment rebelProfessional = regiment("professional", 5, false);
		Faction host = faction("host", List.of(hostProfessional));
		Faction rebels = faction("rebels", List.of(rebelProfessional));

		CivilWarRegimentSplitService.mergeRemaining(rebels, host);

		assertEquals(10, hostProfessional.getCurrentSlots());
		assertEquals(0, rebelProfessional.getCurrentSlots());
	}

	private static Faction faction(String id, List<Regiment> regiments) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		Military military = mock(Military.class);
		when(military.getRegiments()).thenReturn(regiments);
		when(military.getRegiment(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
			String wanted = invocation.getArgument(0);
			for (Regiment regiment : regiments) {
				if (regiment.getId().equalsIgnoreCase(wanted)) {
					return regiment;
				}
			}
			return null;
		});
		when(faction.getMilitary()).thenReturn(military);
		return faction;
	}

	private static Regiment regiment(String id, int slots, boolean levy) {
		return regiment(id, slots, 0, levy);
	}

	private static Regiment regiment(String id, int slots, int free, boolean levy) {
		Regiment regiment = mock(Regiment.class);
		when(regiment.getId()).thenReturn(id);
		when(regiment.isLevy()).thenReturn(levy);
		AtomicInteger current = new AtomicInteger(slots);
		AtomicInteger freeSlots = new AtomicInteger(free);
		when(regiment.getCurrentSlots()).thenAnswer(invocation -> current.get());
		when(regiment.getFreeSlots()).thenAnswer(invocation -> freeSlots.get());
		doAnswer(invocation -> {
			current.set(invocation.getArgument(0));
			return null;
		}).when(regiment).setCurrentSlots(anyInt());
		doAnswer(invocation -> {
			freeSlots.set(invocation.getArgument(0));
			return null;
		}).when(regiment).setFreeSlots(anyInt());
		return regiment;
	}
}
