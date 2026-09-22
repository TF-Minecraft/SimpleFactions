package me.Plugins.SimpleFactions.Map.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProvincePresenceServiceTest {
	private static final int PROV_A = 10;
	private static final int PROV_B = 20;

	private UUID playerId;
	private List<Transition> transitions;
	private ProvincePresenceService service;

	@BeforeEach
	void setUp() {
		ProvincePresenceService.resetForTests();
		playerId = UUID.randomUUID();
		transitions = new ArrayList<>();
		service = newService();
	}

	@Test
	void firstSighting_firesEnterOnly() {
		service.updatePlayer(playerId, PROV_A);

		assertEquals(1, transitions.size());
		assertEquals("enter", transitions.get(0).type());
		assertEquals(PROV_A, transitions.get(0).provinceId());
		assertEquals(null, transitions.get(0).previousProvinceId());
		assertEquals(PROV_A, service.getCurrentProvince(playerId));
	}

	@Test
	void sameProvinceTwice_firesNoEvents() {
		service.updatePlayer(playerId, PROV_A);
		transitions.clear();

		service.updatePlayer(playerId, PROV_A);

		assertTrue(transitions.isEmpty());
		assertEquals(PROV_A, service.getCurrentProvince(playerId));
	}

	@Test
	void provinceChange_firesLeaveThenEnter() {
		service.updatePlayer(playerId, PROV_A);
		transitions.clear();

		service.updatePlayer(playerId, PROV_B);

		assertEquals(2, transitions.size());
		assertEquals("leave", transitions.get(0).type());
		assertEquals(PROV_A, transitions.get(0).provinceId());
		assertEquals(PROV_B, transitions.get(0).nextProvinceId());
		assertEquals("enter", transitions.get(1).type());
		assertEquals(PROV_B, transitions.get(1).provinceId());
		assertEquals(PROV_A, transitions.get(1).previousProvinceId());
		assertEquals(PROV_B, service.getCurrentProvince(playerId));
	}

	@Test
	void unknownToKnown_firesLeaveThenEnter() {
		service.updatePlayer(playerId, ProvincePresenceService.UNKNOWN_PROVINCE);
		transitions.clear();

		service.updatePlayer(playerId, PROV_A);

		assertEquals(2, transitions.size());
		assertEquals("leave", transitions.get(0).type());
		assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, transitions.get(0).provinceId());
		assertEquals(PROV_A, transitions.get(0).nextProvinceId());
		assertEquals("enter", transitions.get(1).type());
		assertEquals(PROV_A, transitions.get(1).provinceId());
		assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, transitions.get(1).previousProvinceId());
	}

	@Test
	void quit_firesLeaveAndClearsState() {
		service.updatePlayer(playerId, PROV_A);
		transitions.clear();

		service.handleQuit(playerId);

		assertEquals(1, transitions.size());
		assertEquals("leave", transitions.get(0).type());
		assertEquals(PROV_A, transitions.get(0).provinceId());
		assertEquals(null, transitions.get(0).nextProvinceId());
		assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, service.getCurrentProvince(playerId));
	}

	@Test
	void getCurrentProvince_unknownWhenUntracked() {
		assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, service.getCurrentProvince(playerId));
	}

	@Test
	void isInProvince_falseWhenUntracked() {
		assertFalse(service.getCurrentProvince(playerId) == PROV_A);
		assertTrue(service.getCurrentProvince(playerId) == ProvincePresenceService.UNKNOWN_PROVINCE);
	}

	private ProvincePresenceService newService() {
		return new ProvincePresenceService(
				p -> PROV_A,
				new ProvincePresenceCallbacks() {
					@Override
					public void onEnter(UUID id, int provinceId, Integer previousProvinceId) {
						transitions.add(new Transition("enter", provinceId, previousProvinceId, null));
					}

					@Override
					public void onLeave(UUID id, int provinceId, Integer nextProvinceId) {
						transitions.add(new Transition("leave", provinceId, null, nextProvinceId));
					}
				});
	}

	private record Transition(
			String type,
			int provinceId,
			Integer previousProvinceId,
			Integer nextProvinceId) {
	}
}
