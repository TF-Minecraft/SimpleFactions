package net.tfminecraft.simplefactions.tiers.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.tiers.admin.TitleAdminService.MapKey;
import net.tfminecraft.simplefactions.tiers.admin.TitleAdminService.Result;

class TitleAdminServiceTest {
	private static final IntPredicate ANY_PROVINCE = p -> p > 0 && p < 1000;

	private Title county1;
	private Title county2;
	private Title county3;
	private Title duchy1;
	private Title duchy2;

	@BeforeEach
	void setUp() {
		Tier county = tier("county", 2);
		Tier duchy = tier("duchy", 3);
		county1 = title(county, "COUNTY_1", "{\"name\":\"Urseilos\",\"rgb\":\"1,1,1\",\"provinces\":[1,2,3]}");
		county2 = title(county, "COUNTY_2", "{\"name\":\"Ardentos\",\"rgb\":\"2,2,2\",\"provinces\":[4]}");
		county3 = title(county, "COUNTY_3", "{\"name\":\"Vedrasos\",\"rgb\":\"3,3,3\",\"provinces\":[5,6]}");
		duchy1 = title(duchy, "DUCHY_1", "{\"name\":\"Atrarcha\",\"rgb\":\"1,1,1\",\"titles\":[\"COUNTY_1\",\"COUNTY_2\"]}");
		duchy2 = title(duchy, "DUCHY_2", "{\"name\":\"Heliovera\",\"rgb\":\"9,9,9\",\"titles\":[\"COUNTY_3\"]}");
		TitleLoader.getTitles().clear();
		TitleLoader.getTitles().addAll(List.of(county1, county2, county3, duchy1, duchy2));
	}

	@AfterEach
	void tearDown() {
		TitleLoader.getTitles().clear();
	}

	@Test
	void renameCollapsesWhitespaceAndQueuesItsRegion() {
		Result result = TitleAdminService.rename("county_1", "  New   Urseilos ");

		assertTrue(result.ok());
		assertEquals("New Urseilos", county1.getName());
		assertEquals(Set.of(county1), result.changed());
		assertTrue(result.regenerate().contains(new MapKey("county", "1,1,1")));
		assertTrue(result.regenerate().contains(new MapKey("duchy", "1,1,1")));
	}

	@Test
	void renameRejectsColourCodesAndBlankNames() {
		assertFalse(TitleAdminService.rename("COUNTY_1", "§cRed").ok());
		assertFalse(TitleAdminService.rename("COUNTY_1", "   ").ok());
		assertFalse(TitleAdminService.rename("MISSING", "Name").ok());
		assertEquals("Urseilos", county1.getName());
	}

	@Test
	void setColourNormalisesAndClearsTheOldRegion() {
		Result result = TitleAdminService.setColour("COUNTY_1", "10, 20 ,30");

		assertTrue(result.ok());
		assertEquals("10,20,30", county1.getRgb());
		assertTrue(result.regenerate().contains(new MapKey("county", "1,1,1")));
		assertTrue(result.regenerate().contains(new MapKey("county", "10,20,30")));
	}

	@Test
	void setColourMustBeUniqueWithinATierOnly() {
		assertFalse(TitleAdminService.setColour("COUNTY_1", "2,2,2").ok());
		assertEquals("1,1,1", county1.getRgb());
		// A duchy may reuse a county's colour: the map keeps each tier separate.
		assertTrue(TitleAdminService.setColour("DUCHY_2", "3,3,3").ok());
		assertFalse(TitleAdminService.setColour("COUNTY_1", "256,0,0").ok());
		assertFalse(TitleAdminService.setColour("COUNTY_1", "1,2").ok());
	}

	@Test
	void addProvinceMovesItFromItsOldTitle() {
		Result result = TitleAdminService.addProvinces("COUNTY_3", List.of("1"), ANY_PROVINCE);

		assertTrue(result.ok());
		assertEquals(List.of(2, 3), county1.getProvinces());
		assertEquals(List.of(5, 6, 1), county3.getProvinces());
		assertEquals(Set.of(county1, county3), result.changed());
		assertTrue(result.regenerate().containsAll(Set.of(
				new MapKey("county", "1,1,1"), new MapKey("county", "3,3,3"),
				new MapKey("duchy", "1,1,1"), new MapKey("duchy", "9,9,9"))));
	}

	@Test
	void addProvinceAcceptsUntitledAndCommaSeparatedIds() {
		Result result = TitleAdminService.addProvinces("COUNTY_2", List.of("7,8", "9"), ANY_PROVINCE);

		assertTrue(result.ok());
		assertEquals(List.of(4, 7, 8, 9), county2.getProvinces());
	}

	@Test
	void addProvinceRejectsTheWholeBatchWhenOneWouldEmptyATitle() {
		Result result = TitleAdminService.addProvinces("COUNTY_1", List.of("5", "4"), ANY_PROVINCE);

		assertFalse(result.ok());
		assertEquals(List.of(1, 2, 3), county1.getProvinces());
		assertEquals(List.of(4), county2.getProvinces());
		assertEquals(List.of(5, 6), county3.getProvinces());
	}

	@Test
	void addProvinceRejectsUnknownProvincesAndCompositeTitles() {
		assertFalse(TitleAdminService.addProvinces("COUNTY_1", List.of("5000"), ANY_PROVINCE).ok());
		assertFalse(TitleAdminService.addProvinces("COUNTY_1", List.of("abc"), ANY_PROVINCE).ok());
		assertFalse(TitleAdminService.addProvinces("DUCHY_1", List.of("7"), ANY_PROVINCE).ok());
		assertFalse(TitleAdminService.addProvinces("COUNTY_1", List.of("1"), ANY_PROVINCE).ok());
	}

	@Test
	void removeProvinceKeepsAtLeastOne() {
		assertFalse(TitleAdminService.removeProvinces("COUNTY_2", List.of("4")).ok());
		assertFalse(TitleAdminService.removeProvinces("COUNTY_1", List.of("9")).ok());

		Result result = TitleAdminService.removeProvinces("COUNTY_1", List.of("1", "3"));
		assertTrue(result.ok());
		assertEquals(List.of(2), county1.getProvinces());
	}

	@Test
	void addTitleMovesALowerTitleBetweenParents() {
		Result result = TitleAdminService.addTitle("DUCHY_2", "county_2");

		assertTrue(result.ok());
		assertEquals(List.of("COUNTY_1"), duchy1.getTitles());
		assertEquals(List.of("COUNTY_3", "COUNTY_2"), duchy2.getTitles());
		assertEquals(Set.of(duchy1, duchy2), result.changed());
	}

	@Test
	void addTitleFindsTheOldParentWhateverTheIdCase() {
		duchy1.getTitles().set(1, "county_2");

		assertTrue(TitleAdminService.addTitle("DUCHY_2", "COUNTY_2").ok());
		assertEquals(List.of("COUNTY_1"), duchy1.getTitles());
		assertEquals(List.of("COUNTY_3", "COUNTY_2"), duchy2.getTitles());
	}

	@Test
	void addTitleChecksTiersAndEmptyParents() {
		assertFalse(TitleAdminService.addTitle("DUCHY_1", "DUCHY_2").ok());
		assertFalse(TitleAdminService.addTitle("COUNTY_1", "COUNTY_2").ok());
		assertFalse(TitleAdminService.addTitle("DUCHY_1", "COUNTY_3").ok());
		assertFalse(TitleAdminService.addTitle("DUCHY_1", "COUNTY_1").ok());
	}

	@Test
	void removeTitleKeepsAtLeastOne() {
		assertFalse(TitleAdminService.removeTitle("DUCHY_2", "COUNTY_3").ok());
		assertFalse(TitleAdminService.removeTitle("DUCHY_1", "COUNTY_3").ok());

		Result result = TitleAdminService.removeTitle("DUCHY_1", "county_2");
		assertTrue(result.ok());
		assertEquals(List.of("COUNTY_1"), duchy1.getTitles());
	}

	@Test
	void setCompleteNeedsABoolean() {
		assertFalse(TitleAdminService.setComplete("COUNTY_1", "yes").ok());
		Result result = TitleAdminService.setComplete("COUNTY_1", "TRUE");
		assertTrue(result.ok());
		assertTrue(county1.isTitleComplete());
		assertTrue(result.regenerate().isEmpty());
		assertFalse(TitleAdminService.setComplete("COUNTY_1", "true").ok());
	}

	private static Tier tier(String id, int level) {
		YamlConfiguration config = new YamlConfiguration();
		config.set("name", id);
		config.set("tier", level);
		return new Tier(id, config);
	}

	private static Title title(Tier tier, String id, String json) {
		return new Title(tier, id, JsonParser.parseString(json).getAsJsonObject());
	}
}
