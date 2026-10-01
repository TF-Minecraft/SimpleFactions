package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Rates;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class HubTransportTest {

    @AfterEach
    void restore() {
        HubTransport.resetConfig();
    }

    @Test
    void defaults_railCarriesMostAndAirLeast() {
        Rates rail = HubTransport.rates(Mode.RAIL);
        Rates sea = HubTransport.rates(Mode.SEA);
        Rates air = HubTransport.rates(Mode.AIR);

        assertEquals(0.70, rail.trade());
        assertEquals(0.25, rail.production());
        assertEquals(0.45, sea.trade());
        assertEquals(0.30, sea.production());
        assertEquals(0.20, air.trade());
        assertEquals(0.0, air.production());
        assertTrue(rail.trade() > sea.trade() && sea.trade() > air.trade());
    }

    @Test
    void onlyInstallationsOfTheSameKindConnect() {
        assertEquals(Mode.RAIL, HubTransport.modeBetween(InstallationKind.TRAIN_STATION, InstallationKind.TRAIN_STATION));
        assertEquals(Mode.SEA, HubTransport.modeBetween(InstallationKind.PORT, InstallationKind.PORT));
        assertEquals(Mode.AIR, HubTransport.modeBetween(InstallationKind.AIRPORT, InstallationKind.AIRPORT));
        assertNull(HubTransport.modeBetween(InstallationKind.TRAIN_STATION, InstallationKind.PORT));
        assertNull(HubTransport.modeBetween(InstallationKind.FORT, InstallationKind.FORT));
        assertNull(HubTransport.modeBetween(null, null));
    }

    @Test
    void distanceLosesAShareEveryThousandBlocks() {
        assertEquals(0.70, HubTransport.delivered(0.70, 0.90, 0), 1e-9);
        assertEquals(0.63, HubTransport.delivered(0.70, 0.90, 1000), 1e-9);
        assertEquals(0.70 * 0.81, HubTransport.delivered(0.70, 0.90, 2000), 1e-9);
        assertEquals(0, HubTransport.delivered(0, 0.90, 500));
    }

    @Test
    void linkCarriesBothFactors() {
        Link link = HubTransport.link(3, 9, Mode.SEA, 1000);

        assertEquals(3, link.fromProvince());
        assertEquals(9, link.toProvince());
        assertEquals(0.45 * 0.85, link.tradeFactor(), 1e-9);
        assertEquals(0.30 * 0.85, link.productionFactor(), 1e-9);
    }

    @Test
    void modifierBoostIsAppliedBeforeTheShareCapAndDistanceLoss() {
        Link link = HubTransport.link(3, 9, Mode.RAIL, 1000);

        assertEquals(0.70 * 1.3 * 0.90, link.boostedTradeFactor(0.30), 1e-9);
        assertEquals(0.25 * 1.3 * 0.90, link.boostedProductionFactor(0.30), 1e-9);

        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.90);
        HubTransport.loadConfig(config);
        Link capped = HubTransport.link(3, 9, Mode.RAIL, 0);
        assertEquals(HubTransport.MAX_SHARE, capped.boostedTradeFactor(0.5), 1e-9);
    }

    @Test
    void maxRangeZeroMeansNoLimit() {
        assertTrue(HubTransport.inRange(HubTransport.rates(Mode.RAIL), 50_000));
        assertTrue(HubTransport.inRange(HubTransport.rates(Mode.AIR), 2500));
        assertFalse(HubTransport.inRange(HubTransport.rates(Mode.AIR), 2501));
    }

    @Test
    void configOverridesAndSharesAreCappedBelowOne() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 1.4);
        config.set("supply-hubs.transport.rail.production", -2);
        config.set("supply-hubs.transport.air.max-range", 900);

        HubTransport.loadConfig(config);

        assertEquals(HubTransport.MAX_SHARE, HubTransport.rates(Mode.RAIL).trade());
        assertEquals(0, HubTransport.rates(Mode.RAIL).production());
        assertEquals(0.90, HubTransport.rates(Mode.RAIL).keptPer1000());
        assertEquals(900, HubTransport.rates(Mode.AIR).maxRange());
        assertEquals(0.45, HubTransport.rates(Mode.SEA).trade());
    }

    @Test
    void missingConfigKeepsDefaults() {
        HubTransport.loadConfig(new YamlConfiguration());

        assertEquals(0.70, HubTransport.rates(Mode.RAIL).trade());
        assertEquals(4000, HubTransport.rates(Mode.SEA).maxRange());
    }
}
