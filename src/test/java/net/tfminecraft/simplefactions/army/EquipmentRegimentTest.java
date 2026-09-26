package net.tfminecraft.simplefactions.army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.mercenary.company.CompanyFixture;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignDeclareValidator;

class EquipmentRegimentTest {

	@BeforeEach
	void setUp() {
		RegimentLoader.oList.clear();
	}

	@AfterEach
	void tearDown() {
		CompanyFixture.clearRegiments();
	}

	@Test
	void shippedArtilleryRegimentMatchesProfessionalUpkeep() throws URISyntaxException {
		YamlConfiguration config = YamlConfiguration.loadConfiguration(resource("regiments.yml"));

		assertTrue(config.getBoolean("artillery.equipment"));
		assertEquals(0, config.getInt("artillery.default-slots"));
		assertEquals(6.0, config.getDouble("artillery.upkeep"));
		assertEquals(config.getDouble("professional.upkeep"), config.getDouble("artillery.upkeep"));
		assertEquals(21600, config.getInt("artillery.expansion-time"));
		assertEquals(config.getInt("professional.expansion-time"), config.getInt("artillery.expansion-time"));
		assertTrue(config.getString("artillery.name", "").contains("Field Artillery"));
		String description = String.join(" ", config.getStringList("artillery.description"));
		assertTrue(description.contains("Each slot lets the faction keep one"));
		assertTrue(description.contains("field artillery piece in its vehicle pool"));
	}

	@Test
	void centralizedLawGrantsOneArtillerySlot() throws URISyntaxException {
		YamlConfiguration config = YamlConfiguration.loadConfiguration(resource("laws.yml"));

		List<String> grants = config.getStringList("military.laws.centralized.effects.faction.regiments");

		assertTrue(grants.contains("professional 2"));
		assertTrue(grants.contains("artillery 1"));
	}

	@Test
	void equipmentSlotsAreNotManpowerButStillCostUpkeep() {
		Regiment artillery = CompanyFixture.regularPrototype("artillery", 3, 6.0);
		when(artillery.isEquipment()).thenReturn(true);
		when(artillery.isOffensive()).thenReturn(true);
		RegimentLoader.oList.add(artillery);

		Faction faction = mock(Faction.class);
		Military military = new Military(faction);
		when(faction.getMilitary()).thenReturn(military);

		assertTrue(military.getRegiment("artillery").isEquipment());
		assertTrue(military.canExpand(military.getRegiment("artillery")).allowed());
		assertEquals(0, military.getManpower(true));
		assertEquals(0, military.getManpower(false));
		assertEquals(0, military.getManpowerNoLevy(true));
		assertEquals(0, military.getManpowerNoLevy(false));
		assertEquals(18.0, military.getRawTotalUpkeep());
		assertFalse(CampaignDeclareValidator.validateAttackerCanDeclare(faction).isValid());
	}

	@Test
	void leviesIgnoreEquipmentSlots() {
		Faction overlord = mock(Faction.class);
		Faction subject = mock(Faction.class);
		when(subject.getMembers()).thenReturn(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
		FactionModifier levy = mock(FactionModifier.class);
		when(levy.getAmount()).thenReturn(100.0);
		when(subject.getModifier(FactionModifiers.LEVY)).thenReturn(levy);

		Regiment professional = mock(Regiment.class);
		when(professional.isEquipment()).thenReturn(false);
		when(professional.getCurrentSlots()).thenReturn(4);
		Regiment artillery = mock(Regiment.class);
		when(artillery.isEquipment()).thenReturn(true);
		when(artillery.getCurrentSlots()).thenReturn(6);
		Military subjectMilitary = mock(Military.class);
		when(subjectMilitary.getRegiments()).thenReturn(List.of(professional, artillery));
		when(subject.getMilitary()).thenReturn(subjectMilitary);

		Military military = new Military(overlord);
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			relations.when(() -> RelationManager.getSubjects(overlord)).thenReturn(List.of(subject));
			relations.when(() -> RelationManager.getSubjects(subject)).thenReturn(List.of());

			List<LevyEntry> levies = military.getLevies();

			assertEquals(1, levies.size());
			assertEquals(4, levies.get(0).getAmount());
			verify(artillery, never()).setSentToOverlord(anyInt());
		}
	}

	private static File resource(String name) throws URISyntaxException {
		return new File(EquipmentRegimentTest.class.getClassLoader().getResource(name).toURI());
	}
}
