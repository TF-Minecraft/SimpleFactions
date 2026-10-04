package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Site;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class HubProposalCopyTest {
    @Test
    void laterHubLoreShowsWhatArrives() {
        Site foreign = site(false, 12, 3.6);
        List<String> lore = HubProposalCopy.destinationLore(foreign, true);
        assertTrue(lore.get(0).contains("Ready now"));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Trade power here") && line.contains("12")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Arrives here") && line.contains("3.6")));
        assertTrue(lore.stream().noneMatch(line -> line.contains("railway") || line.contains("track")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Click to negotiate")));

        Site own = site(true, 40, 8);
        assertTrue(HubProposalCopy.destinationLore(own, true).stream()
                .anyMatch(line -> line.contains("Click to build")));
    }

    @Test
    void aFirstHubShowsTradePowerWithoutAnArrival() {
        Site own = site(true, 18, 0);
        List<String> lore = HubProposalCopy.destinationLore(own, false);
        assertTrue(lore.stream().anyMatch(line -> line.contains("Trade power here") && line.contains("18")));
        assertTrue(lore.stream().noneMatch(line -> line.contains("Arrives here")));
    }

    @Test
    void negotiationShowsTradePowerHereAndWhatArrives() {
        Site site = site(false, 10, 3.6);
        List<String> power = HubProposalCopy.powerLines(site);
        assertTrue(power.get(0).contains("Trade power here") && power.get(0).contains("10"));
        assertTrue(power.get(1).contains("Arrives here") && power.get(1).contains("3.6"));
        assertEquals(
                "§7The rate and the daily fee are what this realm charges.",
                HubProposalCopy.hostLines(site).get(0));
        assertEquals(
                "§7A hub in your own realm has no tax or fee",
                HubProposalCopy.hostLines(site(true, 10, 0)).get(0));
    }

    @Test
    void rateAndFeeStayInsideTheHostBracket() {
        assertEquals(10, HubProposalCopy.startingRate(0, 20));
        assertEquals(0, HubProposalCopy.clampRate(-5, 0, 20));
        assertEquals(20, HubProposalCopy.clampRate(25, 0, 20));
        assertEquals(50000L, HubProposalCopy.clampFeeCents(60000, 500));
        assertEquals(0L, HubProposalCopy.clampFeeCents(-100, 500));
    }

    private static Site site(boolean ownRealm, double tradeHere, double arrivesHere) {
        return new Site(
                "host", "port", "North Port", 2, InstallationKind.PORT, ownRealm,
                tradeHere, arrivesHere, true);
    }
}
