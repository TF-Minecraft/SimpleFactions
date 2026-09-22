package me.Plugins.SimpleFactions.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FormatterTest {

	@Test
	void formatMoneyAlwaysShowsTwoDecimals() {
		assertEquals("6.10", Formatter.formatMoney(6.1));
		assertEquals("12.35", Formatter.formatMoney(12.345));
		assertEquals("0.00", Formatter.formatMoney(0.0));
	}

	@Test
	void formatIdKeepsPlainUnderscoredName() {
		assertEquals("The_Bog", Formatter.formatId("The_Bog"));
	}

	@Test
	void formatIdTurnsSpacesIntoUnderscores() {
		assertEquals("The_Bog", Formatter.formatId("The Bog"));
		assertEquals("Rebel_Camp", Formatter.formatId("Rebel Camp"));
	}

	@Test
	void formatIdStripsMiniMessageHexGradient() {
		assertEquals("The_Bog", Formatter.formatId(
				"&#396E47T&#397055h&#397264e&#3A7472_&#3A7580B&#3A778Fo&#3A799Dg"));
	}

	@Test
	void formatIdStripsHashHexWithoutAmpersand() {
		assertEquals("The_Bog", Formatter.formatId(
				"#396E47T#397055h#397264e#3A7472_#3A7580B#3A778Fo#3A799Dg"));
	}

	@Test
	void formatIdStripsLegacyAndHexAndLeftoverJunk() {
		assertEquals("TheBog", Formatter.formatId("&aThe&#ff00aaBog&"));
		assertEquals("TheBog", Formatter.formatId("§aThe#ff00aaBog§"));
		assertEquals("TheBog", Formatter.formatId("The#Bog"));
	}

	@Test
	void formatIdNullOrEmptyIsEmpty() {
		assertEquals("", Formatter.formatId(null));
		assertEquals("", Formatter.formatId(""));
	}
}
