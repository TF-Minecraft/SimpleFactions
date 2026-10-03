package net.tfminecraft.simplefactions.map.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EffectiveTerrainTest {
    @Test
    void zeroInfrastructureLeavesRawTerrainAlone() {
        for (double terrain : new double[] {0.80, 0.75, 0.40, 0.30}) {
            assertEquals(terrain, EffectiveTerrain.calculate(terrain, 0, 20, 0.75, 1));
        }
    }

    @Test
    void fullInfrastructureFillsTheGapAndStopsAtFull() {
        assertEquals(0.75, EffectiveTerrain.calculate(0.40, 20, 20, 0.75, 1), 1e-9);
        assertEquals(0.75, EffectiveTerrain.calculate(0.40, 40, 20, 0.75, 1), 1e-9);
    }

    @Test
    void halfInfrastructureFillsHalfTheGap() {
        assertEquals(0.575, EffectiveTerrain.calculate(0.40, 10, 20, 0.75, 1), 1e-9);
    }

    @Test
    void accessScalesTheGap() {
        assertEquals(0.575, EffectiveTerrain.calculate(0.40, 20, 20, 0.75, 0.5), 1e-9);
        assertEquals(0.40, EffectiveTerrain.calculate(0.40, 20, 20, 0.75, 0), 1e-9);
    }

    @Test
    void terrainAtOrAboveTheTargetIsUnchangedAtAnyFill() {
        for (double infrastructure : new double[] {0, 10, 20, 40}) {
            assertEquals(0.80, EffectiveTerrain.calculate(0.80, infrastructure, 20, 0.75, 1));
            assertEquals(0.75, EffectiveTerrain.calculate(0.75, infrastructure, 20, 0.75, 1));
        }
    }
}
