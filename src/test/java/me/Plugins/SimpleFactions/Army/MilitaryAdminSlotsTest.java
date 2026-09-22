package me.Plugins.SimpleFactions.Army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Loaders.RegimentLoader;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.mercenary.company.CompanyFixture;

class MilitaryAdminSlotsTest {

	@BeforeEach
	void setUp() {
		RegimentLoader.oList.clear();
		RegimentLoader.oList.add(CompanyFixture.regularPrototype("militia", 6, 2.0));
		RegimentLoader.oList.add(CompanyFixture.regularPrototype("professional", 0, 6.0));
		RegimentLoader.oList.add(levyPrototype());
		RegimentLoader.oList.add(CompanyFixture.prototype());
	}

	@AfterEach
	void tearDown() {
		CompanyFixture.clearRegiments();
	}

	@Test
	void giveMilitia_increasesSlots() {
		Military military = new Military(mock(Faction.class));

		ExpandResult result = military.adminAdjustSlots("militia", 2);

		assertTrue(result.allowed());
		assertEquals(8, military.getRegiment("militia").getCurrentSlots());
	}

	@Test
	void takeMilitia_decreasesSlots() {
		Military military = new Military(mock(Faction.class));

		ExpandResult result = military.adminAdjustSlots("militia", -1);

		assertTrue(result.allowed());
		assertEquals(5, military.getRegiment("militia").getCurrentSlots());
	}

	@Test
	void takeBelowZero_deniesAndLeavesSlots() {
		Military military = new Military(mock(Faction.class));
		Regiment militia = military.getRegiment("militia");
		militia.setCurrentSlots(1);

		ExpandResult result = military.adminAdjustSlots("militia", -2);

		assertFalse(result.allowed());
		assertEquals("Not enough slots to remove.", result.reason());
		assertEquals(1, militia.getCurrentSlots());
	}

	@Test
	void levy_denies() {
		Military military = new Military(mock(Faction.class));

		ExpandResult result = military.adminAdjustSlots("levy", 1);

		assertFalse(result.allowed());
		assertEquals("Levies cannot be adjusted by admins.", result.reason());
	}

	@Test
	void mercenary_deniesAsUnknown() {
		Military military = new Military(mock(Faction.class));

		ExpandResult result = military.adminAdjustSlots("mercenary", 1);

		assertFalse(result.allowed());
		assertEquals("Unknown regiment.", result.reason());
	}

	@Test
	void professionalGiveAndTake() {
		Military military = new Military(mock(Faction.class));

		assertTrue(military.adminAdjustSlots("professional", 3).allowed());
		assertEquals(3, military.getRegiment("professional").getCurrentSlots());

		assertTrue(military.adminAdjustSlots("professional", -1).allowed());
		assertEquals(2, military.getRegiment("professional").getCurrentSlots());
	}

	@Test
	void zeroDelta_denies() {
		Military military = new Military(mock(Faction.class));

		ExpandResult result = military.adminAdjustSlots("militia", 0);

		assertFalse(result.allowed());
		assertEquals("Amount must be non-zero.", result.reason());
	}

	private static Regiment levyPrototype() {
		Regiment levy = CompanyFixture.regularPrototype("levy", 0, 0.0);
		when(levy.isLevy()).thenReturn(true);
		return levy;
	}
}
