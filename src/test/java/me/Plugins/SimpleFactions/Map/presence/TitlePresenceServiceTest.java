package me.Plugins.SimpleFactions.Map.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TitlePresenceServiceTest {
	private static final int PROV_A = 10;
	private static final int PROV_B = 20;
	private static final int PROV_C = 30;

	private UUID playerId;
	private List<String> transitions;
	private TitlePresenceService service;

	@BeforeEach
	void setUp() {
		TitlePresenceService.resetForTests();
		playerId = UUID.randomUUID();
		transitions = new ArrayList<>();
		service = new TitlePresenceService(
				this::titlesFor,
				new TitlePresenceCallbacks() {
					@Override
					public void onEnter(
							UUID id,
							String tierId,
							String titleId,
							String titleName,
							String previousTitleId) {
						transitions.add("enter:" + tierId + ":" + titleId + ":" + previousTitleId);
					}

					@Override
					public void onLeave(UUID id, String tierId, String titleId, String nextTitleId) {
						transitions.add("leave:" + tierId + ":" + titleId + ":" + nextTitleId);
					}
				});
	}

	@Test
	void firstSighting_firesEnterOnly() {
		service.applyProvince(playerId, PROV_A);

		assertEquals(List.of(
				"enter:county:county_a:null",
				"enter:duchy:duchy_west:null"), transitions);
	}

	@Test
	void sameCountyDifferentProvince_noCountyEvent() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.applyProvince(playerId, PROV_B);

		assertTrue(transitions.isEmpty());
	}

	@Test
	void crossingCounties_firesLeaveThenEnter() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.applyProvince(playerId, PROV_C);

		assertEquals(List.of(
				"leave:county:county_a:county_c",
				"enter:county:county_c:county_a"), transitions);
	}

	@Test
	void unknownProvince_leavesTitles() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.applyProvince(playerId, ProvincePresenceService.UNKNOWN_PROVINCE);

		assertEquals(List.of(
				"leave:county:county_a:null",
				"leave:duchy:duchy_west:null"), transitions);
	}

	@Test
	void quit_firesLeaveAndClears() {
		service.applyProvince(playerId, PROV_A);
		transitions.clear();

		service.handleQuit(playerId);

		assertEquals(List.of(
				"leave:county:county_a:null",
				"leave:duchy:duchy_west:null"), transitions);

		transitions.clear();
		service.applyProvince(playerId, PROV_A);
		assertEquals(List.of(
				"enter:county:county_a:null",
				"enter:duchy:duchy_west:null"), transitions);
	}

	private Map<String, TitlePresenceResolver.ResolvedTitle> titlesFor(int provinceId) {
		if (provinceId == PROV_A || provinceId == PROV_B) {
			return Map.of(
					"county", new TitlePresenceResolver.ResolvedTitle("county_a", "County A"),
					"duchy", new TitlePresenceResolver.ResolvedTitle("duchy_west", "West"));
		}
		if (provinceId == PROV_C) {
			return Map.of(
					"county", new TitlePresenceResolver.ResolvedTitle("county_c", "County C"),
					"duchy", new TitlePresenceResolver.ResolvedTitle("duchy_west", "West"));
		}
		return Map.of();
	}
}
