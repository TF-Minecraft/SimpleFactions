package net.tfminecraft.simplefactions.war.declare;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;

class ProvinceAnnexGraphTest {
	@Test
	void landRoutesCrossOneWaterOrSeaTileButNotInvalidProvinces() {
		Map<Integer, Province> map = new HashMap<>();
		Province home = put(map, new Province(1, "plains", 0, 100, -200));
		Province neighbour = put(map, new Province(2, "forest", 0));
		Province river = put(map, new Province(3, "water", 0));
		Province coast = put(map, new Province(4, "hills", 0));
		Province sea = put(map, new Province(5, "sea", 0));
		Province island = put(map, new Province(6, "plains", 0));
		link(home, neighbour);
		link(home, river);
		link(river, coast);
		link(home, sea);
		link(sea, island);
		home.addNeighbour(7);
		ProvinceManager manager = mock(ProvinceManager.class);
		when(manager.get(anyInt())).thenAnswer(call -> map.getOrDefault((int) call.getArgument(0), new Province()));
		ProvinceAnnexGraph graph = new ProvinceAnnexGraph(manager);

		assertEquals(List.copyOf(Set.of(2)), List.copyOf(graph.landNeighbours(1)));
		assertEquals(Set.of(2, 4, 6), Set.copyOf(graph.routeNeighbours(1)));
		assertTrue(graph.routeNeighbours(2).contains(1));
		assertArrayEquals(new int[] {100, -200}, graph.centre(1));
		assertNull(graph.centre(2));
		assertTrue(graph.landNeighbours(7).isEmpty());
	}

	@Test
	void withoutAProvinceMapThereIsNothingToRoute() {
		ProvinceAnnexGraph graph = new ProvinceAnnexGraph(null);
		assertTrue(graph.landNeighbours(1).isEmpty());
		assertTrue(graph.routeNeighbours(1).isEmpty());
		assertNull(graph.centre(1));
	}

	private static Province put(Map<Integer, Province> map, Province province) {
		map.put(province.getId(), province);
		return province;
	}

	private static void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}
}
