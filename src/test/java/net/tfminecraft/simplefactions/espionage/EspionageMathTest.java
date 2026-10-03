package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class EspionageMathTest {
    private Map<String, Integer> attributes(int value) {
        return Map.of("intelligence", value, "wisdom", value, "charisma", value,
                "dexterity", value, "constitution", value, "strength", value);
    }

    @Test
    void fullAptitudeScaleIsReachableAndBounded() {
        RandomGenerator random = new Random(11);
        var weak = new java.util.HashMap<>(attributes(0));
        weak.put("strength", 16);
        weak.put("constitution", 16);
        var strong = new java.util.HashMap<>(attributes(16));
        strong.put("strength", 0);
        strong.put("constitution", 0);
        assertEquals(0, EspionageMath.aptitude(weak, EspionageConfig.DEFAULT_WEIGHTS, random));
        assertEquals(100, EspionageMath.aptitude(strong, EspionageConfig.DEFAULT_WEIGHTS, random));
        for (int score = -5; score < 100; score++) {
            int result = EspionageMath.aptitude(attributes(score), EspionageConfig.DEFAULT_WEIGHTS, random);
            assertTrue(result >= 0 && result <= 100);
        }
    }

    @Test
    void mentalAndSocialAttributesMatterMoreThanBruteStrength() {
        int baseline = EspionageMath.aptitude(attributes(6), EspionageConfig.DEFAULT_WEIGHTS, new Random(3));
        var intelligence = new java.util.HashMap<>(attributes(6));
        intelligence.put("intelligence", 10);
        var strength = new java.util.HashMap<>(attributes(6));
        strength.put("strength", 10);
        var poorJudgment = new java.util.HashMap<>(attributes(6));
        poorJudgment.put("wisdom", 0);
        assertTrue(EspionageMath.aptitude(intelligence, EspionageConfig.DEFAULT_WEIGHTS, new Random(3)) > baseline + 10);
        assertTrue(EspionageMath.aptitude(strength, EspionageConfig.DEFAULT_WEIGHTS, new Random(3)) < baseline - 7);
        assertTrue(EspionageMath.aptitude(poorJudgment, EspionageConfig.DEFAULT_WEIGHTS, new Random(3)) < baseline - 10);
    }

    @Test
    void physicalSpecialistsStayLowWhileMentalSpecialistsCanReachMaximum() {
        var physical = new java.util.HashMap<>(attributes(6));
        physical.put("strength", 16);
        physical.put("constitution", 16);
        var mental = new java.util.HashMap<>(attributes(6));
        mental.put("intelligence", 16);
        mental.put("wisdom", 16);
        for (int seed = 0; seed < 100; seed++) {
            assertTrue(EspionageMath.aptitude(physical, EspionageConfig.DEFAULT_WEIGHTS, new Random(seed)) <= 30);
            assertTrue(EspionageMath.aptitude(mental, EspionageConfig.DEFAULT_WEIGHTS, new Random(seed)) >= 85);
        }
    }

    @Test
    void attributeWeightsAreConfigurableAndNonFiniteValuesUseDefaults() {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("espionage.aptitude.attribute-weights.intelligence", 7.0);
        config.set("espionage.aptitude.attribute-weights.strength", -5.0);
        config.set("espionage.aptitude.attribute-weights.wisdom", Double.NaN);
        try {
            EspionageConfig.load(config);
            assertEquals(7.0, EspionageConfig.weights().get("intelligence"));
            assertEquals(-5.0, EspionageConfig.weights().get("strength"));
            assertEquals(2.5, EspionageConfig.weights().get("wisdom"));
            var stats = new java.util.HashMap<>(attributes(6));
            stats.put("strength", 10);
            assertTrue(EspionageMath.aptitude(stats, new Random(1))
                    < EspionageMath.aptitude(stats, EspionageConfig.DEFAULT_WEIGHTS, new Random(1)));
        } finally { EspionageConfig.load(new org.bukkit.configuration.file.YamlConfiguration()); }
    }

    @Test
    void weakestCanBeatStrongestButStrongestUsuallyWins() {
        RandomGenerator random = new Random(111);
        int upset = 0;
        for (int day = 0; day < 1_000_000; day++) {
            if (EspionageMath.dailyRoll(0, 0, random) > EspionageMath.dailyRoll(100, 0, random)) upset++;
        }
        assertTrue(upset > 0 && upset < 1000, "An exceptionally rare upset must remain possible");
    }

    @Test
    void sabotageLowersBothRollTypesByTheChosenAmount() {
        assertEquals(EspionageMath.dailyRoll(60, 0, new Random(1)) - 100,
                EspionageMath.dailyRoll(60, 100, new Random(1)));
    }

    @Test
    void hiddenAndFiniteChecksFailClosed() {
        assertNotNull(EspionageMath.estimate(100, 0, false, new Random(1)));
        assertNotNull(EspionageMath.estimate(100, -10, false, new Random(1)));
        assertNull(EspionageMath.estimate(Double.NaN, 100, false, new Random(1)));
    }

    @Test
    void rangesContainTheTruthWithoutRevealingExactZeroOrNegativeIncome() {
        RandomGenerator random = new Random(21);
        for (double value : new double[]{0, 0.25, 5, 103.75, 10_000, -125.5}) {
            for (int margin : new int[]{1, 29, 30, 64, 65, 99, 100, 250}) {
                for (int sample = 0; sample < 100; sample++) {
                    var range = EspionageMath.estimate(value, margin, value < 0, random);
                    assertTrue(range.lower() <= value && range.upper() >= value, range.toString());
                    assertTrue(range.lower() < range.upper());
                    if (value >= 0) assertTrue(range.lower() >= 0);
                }
            }
        }
    }

    @Test
    void strongerIntelligenceProducesNarrowerRanges() {
        var rumours = EspionageMath.estimate(1000, 1, false, new Random(5));
        var detailed = EspionageMath.estimate(1000, 100, false, new Random(5));
        assertTrue(detailed.upper() - detailed.lower() < rumours.upper() - rumours.lower());
    }
}
