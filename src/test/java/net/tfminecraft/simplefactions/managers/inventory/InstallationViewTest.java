package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class InstallationViewTest {
	@Test
	void berthedVehicleSlotsSkipControlSlots() {
		assertEquals(10, InstallationView.berthedVehicleSlot(10));
		assertEquals(12, InstallationView.berthedVehicleSlot(11));
		assertEquals(14, InstallationView.berthedVehicleSlot(12));
		assertEquals(44, InstallationView.berthedVehicleSlot(42));
		assertEquals(-1, InstallationView.berthedVehicleSlot(43));
		assertNotEquals(11, InstallationView.berthedVehicleSlot(11));
		assertNotEquals(13, InstallationView.berthedVehicleSlot(12));
	}
}
