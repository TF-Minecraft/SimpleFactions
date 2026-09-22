package me.Plugins.SimpleFactions.Map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChapterIdentityTest {

	@Test
	void normalizeId_keepsAlphanumericLowercase() {
		assertEquals("vardera", ChapterIdentity.normalizeId("Vardera"));
		assertEquals("vardera", ChapterIdentity.normalizeId("vardera"));
	}

	@Test
	void normalizeId_rejectsNonAlphanumericAndBlank() {
		assertEquals(ChapterIdentity.UNKNOWN_ID, ChapterIdentity.normalizeId("var_dera"));
		assertEquals(ChapterIdentity.UNKNOWN_ID, ChapterIdentity.normalizeId("var-dera"));
		assertEquals(ChapterIdentity.UNKNOWN_ID, ChapterIdentity.normalizeId(""));
		assertEquals(ChapterIdentity.UNKNOWN_ID, ChapterIdentity.normalizeId("   "));
		assertEquals(ChapterIdentity.UNKNOWN_ID, ChapterIdentity.normalizeId(null));
	}

	@Test
	void normalizeName_trimsAndDefaults() {
		assertEquals("Vardera", ChapterIdentity.normalizeName("  Vardera  "));
		assertEquals(ChapterIdentity.UNKNOWN_NAME, ChapterIdentity.normalizeName(""));
		assertEquals(ChapterIdentity.UNKNOWN_NAME, ChapterIdentity.normalizeName("   "));
		assertEquals(ChapterIdentity.UNKNOWN_NAME, ChapterIdentity.normalizeName(null));
	}

	@Test
	void normalizeName_doesNotInventFromId() {
		assertEquals("Calavorn", ChapterIdentity.normalizeName("Calavorn"));
	}
}
