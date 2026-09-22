package me.Plugins.SimpleFactions.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisplayNameGateTest {

	@Test
	void flagsCamelCaseWithoutSpaces() {
		assertTrue(DisplayNameGate.looksLikeMissingSpaces("GreenWrathTribe"));
	}

	@Test
	void allowsUnderscoresSpacesAndSingleWords() {
		assertFalse(DisplayNameGate.looksLikeMissingSpaces("Green_Wrath_Tribe"));
		assertFalse(DisplayNameGate.looksLikeMissingSpaces("Green Wrath Tribe"));
		assertFalse(DisplayNameGate.looksLikeMissingSpaces("Green"));
		assertFalse(DisplayNameGate.looksLikeMissingSpaces("USA"));
	}

	@Test
	void suggestUnderscoresInsertsAtCamelBoundaries() {
		assertEquals("Green_Wrath_Tribe", DisplayNameGate.suggestUnderscores("GreenWrathTribe"));
	}

	@Test
	void flagsLowercaseFirstWord() {
		assertEquals("green", DisplayNameGate.findUncapitalizedWord("green_Wrath").orElseThrow());
		assertTrue(DisplayNameGate.hasCapitalizationIssue("green_Wrath"));
	}

	@Test
	void flagsLowercaseLaterWord() {
		assertEquals("wrath", DisplayNameGate.findUncapitalizedWord("Green_wrath").orElseThrow());
		assertTrue(DisplayNameGate.hasCapitalizationIssue("Green_wrath"));
	}

	@Test
	void allowsWhitelistedLowercaseAfterFirstWord() {
		assertTrue(DisplayNameGate.findUncapitalizedWord("Kingdom_of_Wrath").isEmpty());
		assertFalse(DisplayNameGate.hasCapitalizationIssue("Kingdom_of_Wrath"));
	}

	@Test
	void doesNotWhitelistFirstWord() {
		assertEquals("the", DisplayNameGate.findUncapitalizedWord("the_Kingdom").orElseThrow());
		assertTrue(DisplayNameGate.hasCapitalizationIssue("the_Kingdom"));
	}

	@Test
	void allowsProperlyCapitalizedNames() {
		assertTrue(DisplayNameGate.findUncapitalizedWord("Green").isEmpty());
		assertTrue(DisplayNameGate.findUncapitalizedWord("USA").isEmpty());
		assertTrue(DisplayNameGate.findUncapitalizedWord("Green_Wrath_Tribe").isEmpty());
		assertFalse(DisplayNameGate.hasCapitalizationIssue("Green_Wrath_Tribe"));
	}
}
