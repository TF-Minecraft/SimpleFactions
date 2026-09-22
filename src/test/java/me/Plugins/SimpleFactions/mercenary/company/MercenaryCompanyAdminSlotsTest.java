package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

class MercenaryCompanyAdminSlotsTest {
	private double oldSlotUpkeep;

	@BeforeEach
	void setUp() {
		oldSlotUpkeep = Cache.mercenarySlotUpkeep;
		Cache.mercenarySlotUpkeep = 8.0;
		CompanyFixture.installCompanyUpgrades();
	}

	@AfterEach
	void tearDown() {
		Cache.mercenarySlotUpkeep = oldSlotUpkeep;
		CompanyFixture.clearCompanyUpgrades();
	}

	@Test
	void giveWhileEmpty_skipsCanExpand() {
		MercenaryCompany company = formedCompany();
		assertTrue(company.getEnlisted().isEmpty());

		MercenaryResult result = company.adminAdjustSlots(2);

		assertTrue(result.ok());
		assertEquals(3, company.getSlots());
	}

	@Test
	void takeViaDropSlot_trimsExcessEnlisted() {
		MercenaryCompany company = formedCompany();
		company.enlist("Sigrun");
		company.adminAdjustSlots(1);
		company.enlist("Erik");
		assertEquals(2, company.getSlots());
		assertEquals(2, company.getEnlisted().size());

		MercenaryResult result = company.adminAdjustSlots(-2);

		assertTrue(result.ok());
		assertEquals(0, company.getSlots());
		assertTrue(company.getEnlisted().isEmpty());
	}

	@Test
	void formingCompany_deniesGive() {
		MercenaryCompany company = new MercenaryCompany(
				new CompanyFixture(0).guild, "Hired Blades", CompanyFixture.companyRegiment(), 120);

		MercenaryResult result = company.adminAdjustSlots(1);

		assertFalse(result.ok());
		assertEquals("Your company is still being founded.", result.message());
	}

	@Test
	void takeBelowZero_denies() {
		MercenaryCompany company = formedCompany();

		MercenaryResult result = company.adminAdjustSlots(-2);

		assertFalse(result.ok());
		assertEquals("Not enough slots to remove.", result.message());
		assertEquals(1, company.getSlots());
	}

	@Test
	void zeroDelta_denies() {
		MercenaryCompany company = formedCompany();

		MercenaryResult result = company.adminAdjustSlots(0);

		assertFalse(result.ok());
		assertEquals("Amount must be non-zero.", result.message());
	}

	private static MercenaryCompany formedCompany() {
		return new MercenaryCompany(
				new CompanyFixture(0).guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
	}
}
