package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.HubEstimates.InstallationPreview;

class InfrastructureMenuCopyTest {
    @Test
    void headlineUsesTheCachedDailyFigure() {
        assertEquals(
                "§7Infrastructure is worth about +45 a day to your realm",
                InfrastructureMenuCopy.headline(45));
        assertEquals(
                "§7Infrastructure is worth about -3 a day to your realm",
                InfrastructureMenuCopy.headline(-3));
        assertEquals(
                "§7Infrastructure worth is worked out once a day.",
                InfrastructureMenuCopy.headlineUnknown());
    }

    @Test
    void installationPreviewNamesInfrastructureIncomeAndUpkeep() {
        assertEquals(
                List.of(
                        "§a+10 infrastructure here",
                        "§7about +6 a day for your realm",
                        "§7upkeep 5"),
                InfrastructureMenuCopy.installationPreview(new InstallationPreview(10, 6, 5)));
        assertEquals(
                List.of(
                        "§7This adds no infrastructure here",
                        "§7about +0 a day for your realm",
                        "§7upkeep 30"),
                InfrastructureMenuCopy.installationPreview(new InstallationPreview(0, 0, 30)));
        assertTrue(InfrastructureMenuCopy.installationPreview(null).get(0).contains("Working out"));
    }
}
