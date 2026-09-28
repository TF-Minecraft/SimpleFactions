package net.tfminecraft.simplefactions.government;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VotingBlockTest {

	@Test
	void furnitureId_readsItemsAdderFurniturePath() {
		assertEquals("tfmc:voting_booth", VotingBlock.furnitureId("iaf(tfmc:voting_booth)"));
		assertEquals("tfmc:voting_booth", VotingBlock.furnitureId("iaf.tfmc:voting_booth"));
		assertNull(VotingBlock.furnitureId("v(chiseled_bookshelf)"));
		assertNull(VotingBlock.furnitureId(null));
	}

	@Test
	void matches_comparesNamespacedId() {
		assertTrue(VotingBlock.matches("iaf(tfmc:voting_booth)", "tfmc:voting_booth"));
		assertTrue(VotingBlock.matches("iaf(tfmc:voting_booth)", "TFMC:Voting_Booth"));
		assertFalse(VotingBlock.matches("iaf(tfmc:voting_booth)", "tfmc:bank"));
		assertFalse(VotingBlock.matches("v(chiseled_bookshelf)", "tfmc:voting_booth"));
	}
}
