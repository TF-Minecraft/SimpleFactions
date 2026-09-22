package me.Plugins.SimpleFactions.Army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Rules;

class MilitaryRecruitmentRuleTest {

	@Test
	void levyAllowedWhenProfessionalRuleOff() {
		Military military = military(false);
		Regiment levy = regiment(false, true);

		assertTrue(military.canExpand(levy).allowed());
		assertTrue(military.enqueue(levy));
		assertEquals(1, military.getQueue().size());
	}

	@Test
	void militiaAllowedWhenProfessionalRuleOff() {
		Military military = military(false);
		Regiment militia = regiment(false, false);

		assertTrue(military.canExpand(militia).allowed());
		assertTrue(military.enqueue(militia));
		assertEquals(1, military.getQueue().size());
	}

	@Test
	void professionalBlockedWhenRuleOff() {
		Military military = military(false);
		Regiment professional = regiment(true, false);

		ExpandResult result = military.canExpand(professional);
		assertFalse(result.allowed());
		assertEquals("Your laws do not allow recruiting a professional army.", result.reason());
		assertFalse(military.enqueue(professional));
		assertTrue(military.getQueue().isEmpty());
	}

	@Test
	void professionalAllowedWhenRuleOn() {
		Military military = military(true);
		Regiment professional = regiment(true, false);

		assertTrue(military.canExpand(professional).allowed());
		assertTrue(military.enqueue(professional));
		assertEquals(1, military.getQueue().size());
	}

	@Test
	void bothAllowedWhenRuleOn() {
		Military military = military(true);
		assertTrue(military.canExpand(regiment(false, true)).allowed());
		assertTrue(military.canExpand(regiment(true, false)).allowed());
		assertTrue(military.canExpand(regiment(false, false)).allowed());
	}

	private static Military military(boolean canRecruitProfessional) {
		Faction faction = mock(Faction.class);
		when(faction.hasFactionRule(Rules.CAN_RECRUIT_PROFESSIONAL_ARMY)).thenReturn(canRecruitProfessional);
		return new Military(faction);
	}

	private static Regiment regiment(boolean professional, boolean levy) {
		Regiment regiment = mock(Regiment.class);
		when(regiment.isProfessional()).thenReturn(professional);
		when(regiment.isLevy()).thenReturn(levy);
		when(regiment.getExpansionTime()).thenReturn(1);
		return regiment;
	}
}
