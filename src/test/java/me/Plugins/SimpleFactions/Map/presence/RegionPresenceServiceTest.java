package me.Plugins.SimpleFactions.Map.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Map.MapRegion;

class RegionPresenceServiceTest {
	private static final int PROV_A = 10;
	private static final int PROV_B = 20;
	private static final int PROV_C = 30;

	private static final MapRegion HIGHLANDS =
			new MapRegion("REGION_1", "Highlands", Set.of(PROV_A, PROV_B));
	private static final MapRegion LOWLANDS =
			new MapRegion("REGION_2", "Lowlands", Set.of(PROV_C));

	private UUID playerId;
	private List<String> transitions;
	private RegionPresenceService service;

	@BeforeEach
	void setUp() {
		RegionPresenceService.resetForTests();
		playerId = UUID.randomUUID();
		transitions = new ArrayList<>();
		service = new RegionPresenceService(
				this::regionFor,
				new RegionPresenceCallbacks() {
					@Override
					public void onEnter(
							UUID id, String regionId, String regionName, String previousRegionId) {
						transitions.add("enter:" + regionId + ":" + regionName + ":" + previousRegionId);
					}

					@Override
					public void onLeave(UUID id, String regionId, String nextRegionId) {
						transitions.add("leave:" + regionId + ":" + nextRegionId);
					}
				});
	}

	@Test
	void firstSighting_firesEnterOnly() {
		service.applyProvince(playerId, PROV_A);

		assertEquals(List.of("enter:REGION_1:Highlands:null"), transitions);
	}

	@Test
	void sameRegionDifferentProvince_noEvent() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.applyProvince(playerId, PROV_B);

		assertTrue(transitions.isEmpty());
	}

	@Test
	void crossingRegions_firesLeaveThenEnter() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.applyProvince(playerId, PROV_C);

		assertEquals(
				List.of("leave:REGION_1:REGION_2", "enter:REGION_2:Lowlands:REGION_1"),
				transitions);
	}

	@Test
	void unknownProvince_leavesRegion() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.applyProvince(playerId, ProvincePresenceService.UNKNOWN_PROVINCE);

		assertEquals(List.of("leave:REGION_1:null"), transitions);
	}

	@Test
	void quit_firesLeaveAndClears() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.handleQuit(playerId);

		assertEquals(List.of("leave:REGION_1:null"), transitions);

		transitions.clear();
		service.applyProvince(playerId, PROV_A);
		assertEquals(List.of("enter:REGION_1:Highlands:null"), transitions);
	}

	private MapRegion regionFor(int provinceId) {
		if (HIGHLANDS.containsProvince(provinceId)) {
			return HIGHLANDS;
		}
		if (LOWLANDS.containsProvince(provinceId)) {
			return LOWLANDS;
		}
		return null;
	}
}
