package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.government.stability.StabilityFacts.Body;

class StabilityMathTest {

	@Test
	void autocracyNeedsSupportToStayLegitimate() {
		StabilityFacts neutral = nation("autocracy", false, "mercantilism", "closed_borders", 21);
		guild(neutral, "Chisels", false, 8, 34, 37.5, "NEUTRAL", false);
		guild(neutral, "Scrolls", false, 5, 5, 7.5, "NEUTRAL", false);
		guild(neutral, "Realm", true, 12, 13, 4.5, "SUPPORT", false);
		StabilityReport boycott = StabilityMath.assess(neutral);
		assertEquals(41.5, boycott.legitimacy);
		assertTrue(boycott.illegitimate);
		assertEquals(38.1, boycott.weakStateMalus);
		assertEquals(StabilityStatus.FAILING, boycott.status);

		for (Body body : neutral.guilds) {
			if (!body.realm) body.stance = "SUPPORT";
		}
		StabilityReport endorsed = StabilityMath.assess(neutral);
		assertEquals(38.1, endorsed.weakStateMalus);
		assertEquals(34, endorsed.requiredLevels);
		assertEquals(100, endorsed.legitimacy);
		assertFalse(endorsed.illegitimate);
		assertEquals(100, endorsed.economic);
		assertEquals(61.9, endorsed.stability);
		assertEquals(StabilityStatus.STRAINED, endorsed.status);
		assertEquals(0.382, GovernmentIncompatibility.factor(34, 13, 1), 0.001);
	}

	@Test
	void aStateAtLeastAsLargeAsTheBiggestGuildIsNotWeak() {
		StabilityFacts facts = nation("autocracy", false, "mercantilism", "closed_borders", 21);
		guild(facts, "Chisels", false, 8, 13, 37.5, "SUPPORT", false);
		guild(facts, "Realm", true, 12, 34, 4.5, "SUPPORT", false);
		StabilityReport report = StabilityMath.assess(facts);
		assertEquals(0, report.weakStateMalus);
		assertEquals(100, report.economic);
		assertEquals(100, report.stability);
		assertEquals(1, GovernmentIncompatibility.factor(13, 34, 1));
	}

	@Test
	void oligarchyOnlyNeedsHalfTheLargestGuild() {
		StabilityFacts facts = nation("oligarchy", false, "protectionism", "closed_borders", 22);
		guild(facts, "Betriebsrat", false, 5, 18, 20, "SUPPORT", false);
		guild(facts, "Realm", true, 4, 11, 6, "SUPPORT", false);
		StabilityReport report = StabilityMath.assess(facts);
		assertEquals(9, report.requiredLevels);
		assertEquals(0, report.weakStateMalus);
		assertEquals(100, report.stability);
		assertEquals(1, GovernmentIncompatibility.factor(18, 11, 0.5));
		assertEquals(0.733, GovernmentIncompatibility.factor(30, 11, 0.5), 0.001);
	}

	@Test
	void singleGuildIsAStableHivemind() {
		StabilityFacts facts = nation("autocracy", false, "mercantilism", "closed_borders", 18);
		guild(facts, "Realm", true, 4, 18, 5.5, "SUPPORT", false);
		StabilityReport report = StabilityMath.assess(facts);
		assertEquals(100, report.legitimacy);
		assertEquals(100, report.economic);
		assertEquals(StabilityStatus.STABLE, report.status);
	}

	@Test
	void communityStaysHighOnNeutralAndPaysForSize() {
		StabilityFacts facts = nation("community", false, "protectionism", "open_borders", 23);
		guild(facts, "Vardyn", false, 7, 16, 15, "NEUTRAL", false);
		guild(facts, "Oyfthyr", false, 4, 16, 15, "NEUTRAL", false);
		guild(facts, "Noyn", false, 4, 10, 10, "NEUTRAL", false);
		guild(facts, "Realm", true, 5, 13, 5.5, "SUPPORT", false);
		StabilityReport report = StabilityMath.assess(facts);
		assertEquals(87, report.legitimacy);
		assertEquals(0, report.weakStateMalus);
		assertEquals(100, report.economic);
		assertEquals(26, report.overextension);
		assertEquals(72.3, report.stability);
		assertEquals(StabilityStatus.STRAINED, report.status);
	}

	@Test
	void favourAddsStrainAndHolyOrderLandsFailing() {
		StabilityFacts facts = nation("autocracy", false, "protectionism", "closed_borders", 44);
		guild(facts, "Keepers", false, 6, 36, 42.5, "SUPPORT", true);
		guild(facts, "Kinswomen", false, 6, 9, 7.5, "SUPPORT", false);
		guild(facts, "Gravekeepers", false, 4, 2, 5, "SUPPORT", false);
		guild(facts, "Realm", true, 7, 22, 6, "SUPPORT", false);
		StabilityReport report = StabilityMath.assess(facts);
		assertEquals(100, report.economic);
		assertEquals(36, report.requiredLevels);
		assertEquals(15.1, report.weakStateMalus);
		assertEquals(100, report.legitimacy);
		assertEquals(84.9, report.stability);
		assertEquals(StabilityStatus.STABLE, report.status);
		assertEquals(1.0, report.status.getTaxFactor());
		assertEquals(0.611, GovernmentIncompatibility.factor(36, 22, 1), 0.001);
	}

	@Test
	void plutocracyMatchesNothingAndDemocracyMatchesAFifth() {
		StabilityFacts corporate = nation("plutocracy", false, "free_trade", "open_borders", 4);
		guild(corporate, "House", false, 4, 20, 10, "SUPPORT", false);
		guild(corporate, "Realm", true, 2, 1, 1, "SUPPORT", false);
		assertEquals(0, StabilityMath.assess(corporate).weakStateMalus);

		StabilityFacts democracy = nation("democracy", false, "free_trade", "open_borders", 4);
		guild(democracy, "Assembly", false, 4, 20, 5, "SUPPORT", false);
		guild(democracy, "Realm", true, 2, 4, 1, "SUPPORT", false);
		StabilityReport report = StabilityMath.assess(democracy);
		assertEquals(4.0, report.requiredLevels);
		assertEquals(0, report.weakStateMalus);
	}

	@Test
	void slowDebuffsWaitAndForeignTradeDoesNot() {
		StabilityTuning tuning = StabilityTuning.get();
		assertEquals(0, StabilityDebuffs.prestigeMalus(100, tuning));
		assertEquals(0, StabilityDebuffs.foreignTradeBonus(100, tuning));
		assertEquals(1, StabilityDebuffs.realmOutputFactor(100));
		assertEquals(0.539, StabilityDebuffs.realmOutputFactor(53.9), 0.0001);
		assertEquals(0, StabilityDebuffs.realmOutputFactor(0));
		assertEquals(7.5, StabilityDebuffs.realmSeed(null, 7.5));
		assertEquals(0.5, StabilityDebuffs.foreignTradeBonus(0, tuning));
		assertEquals(12.5, StabilityDebuffs.prestigeMalus(50, tuning));
		assertEquals(0.5, StabilityDebuffs.adminFactor(20, tuning));
		assertEquals(2.0, StabilityDebuffs.upkeepFactor(20, tuning));
		assertEquals(25, StabilityDebuffs.prestigeMalus(0, tuning));
		assertEquals(1.0, StabilityStatus.STRAINED.getTaxFactor());
		assertEquals(0.75, StabilityStatus.STRUGGLING.getTaxFactor());
		assertEquals(0.5, StabilityStatus.FAILING.getTaxFactor());
		assertTrue(StabilityDebuffs.adminFactor(54, tuning) > 0.8);
	}

	@Test
	void seventyFiveIsStableAndSeventyFourIsStrained() {
		assertEquals(StabilityStatus.STABLE, StabilityStatus.fromStability(75));
		assertEquals(StabilityStatus.STRAINED, StabilityStatus.fromStability(74));
		assertFalse(StabilityStatus.COLLAPSING.canWageWar());
		assertFalse(StabilityStatus.FAILED.canFormTitles());
	}

	private static StabilityFacts nation(String government, boolean elected, String economy, String borders, int provinces) {
		StabilityFacts facts = new StabilityFacts();
		facts.government = government;
		facts.electedLeadership = elected;
		facts.economy = economy;
		facts.borders = borders;
		facts.provinces = provinces;
		return facts;
	}

	private static void guild(StabilityFacts facts, String name, boolean realm, int members, int levels, double trade, String stance, boolean favoured) {
		Body body = new Body();
		body.name = name;
		body.realm = realm;
		body.members = members;
		body.branchLevels = levels;
		body.tradePower = trade;
		body.stance = stance;
		body.favoured = favoured;
		facts.guilds.add(body);
	}
}
