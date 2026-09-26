package net.tfminecraft.simplefactions.war.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.ContractFixture;
import net.tfminecraft.simplefactions.mercenary.contract.ContractHandler;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;

class PreparationFreezeMercenaryTest {
	private final List<War> wars = new ArrayList<>();
	private ContractFixture fixture;
	private Faction enemy;

	@BeforeEach
	void setUp() {
		Cache.warPostponeFreezeHours = 24;
		PreparationFreeze.setActiveWars(() -> wars);
		fixture = ContractFixture.formed(2);
		enemy = ContractFixture.faction("foe");
	}

	@AfterEach
	void tearDown() {
		PreparationFreeze.setActiveWars(null);
		ContractFixture.tearDown();
	}

	@Test
	void frozenHirer_cannotBeOfferedAContract() {
		freeze(fixture.hirer);

		ContractHandler.Offer offer = fixture.company.getContractHandler()
				.offer(fixture.hirer, ContractFixture.validTerms(1));

		assertFalse(offer.ok());
		assertTrue(offer.message().contains("cannot hire mercenaries"));
	}

	@Test
	void frozenHirer_cannotSignAnOpenOffer() {
		Government government = mock(Government.class);
		when(government.isCouncilMember("signer")).thenReturn(true);
		when(fixture.hirer.getGovernment()).thenReturn(government);
		MercenaryContract contract = fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());
		freeze(fixture.hirer);

		MercenaryResult result = fixture.company.getContractHandler()
				.accept(contract.getId(), fixture.hirer, "signer");

		assertFalse(result.ok());
		assertTrue(contract.isOffered());
	}

	@Test
	void unfrozenHirer_canStillBeOffered() {
		freeze(enemy);

		assertTrue(fixture.company.getContractHandler()
				.offer(fixture.hirer, ContractFixture.validTerms(1)).ok());
	}

	@Test
	void companyOfFrozenHost_holdsItsSlotExpansion() {
		MercenaryCompany company = fixture.company;
		int before = startSlotExpansion(company);
		freeze(fixture.host.guild.getFaction());

		company.tick();

		assertEquals(before, company.getSlotQueue().get(0).getTimeLeft());
	}

	@Test
	void companyServingAFrozenHirer_holdsItsSlotExpansion() {
		MercenaryCompany company = fixture.company;
		MercenaryContract contract = fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());
		assertTrue(contract.activate());
		int before = startSlotExpansion(company);
		freeze(fixture.hirer);

		company.tick();

		assertEquals(before, company.getSlotQueue().get(0).getTimeLeft());
	}

	@Test
	void companyOutsideTheWar_keepsExpanding() {
		MercenaryCompany company = fixture.company;
		int before = startSlotExpansion(company);
		freeze(enemy);

		company.tick();

		assertNotEquals(before, company.getSlotQueue().get(0).getTimeLeft());
	}

	private int startSlotExpansion(MercenaryCompany company) {
		for (int i = 0; i < company.getSlots(); i++) {
			company.enlist("Recruit" + i);
		}
		assertTrue(company.enqueueExpansion().ok());
		return company.getSlotQueue().get(0).getTimeLeft();
	}

	private void freeze(Faction side) {
		Faction other = side == enemy ? ContractFixture.faction("bystander") : enemy;
		War war = new War(wars.size() + 1, side, other);
		wars.add(war);
		PreparationFreeze.applyPostponement(war, Instant.now());
	}
}
