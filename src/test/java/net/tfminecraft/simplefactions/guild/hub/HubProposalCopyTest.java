package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Destination;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Group;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Terms;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class HubProposalCopyTest {
    @Test
    void destinationLoreNamesBothSidesAndAnAssumedRailway() {
        Destination ready = destination(Group.READY, "station", false, true, 1900, 88, -19);
        List<String> lore = HubProposalCopy.destinationLore(ready);
        assertTrue(lore.get(0).contains("Ready now"));
        assertTrue(lore.stream().anyMatch(line -> line.contains("§a+88.00") && line.contains("You")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("§c-19.00") && line.contains("Host")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("No railway yet") && line.contains("1900")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Click to negotiate")));

        Destination own = destination(Group.READY, "station", true, false, 0, 40, 0);
        assertTrue(HubProposalCopy.destinationLore(own).stream().anyMatch(line -> line.contains("Click to build")));

        Destination imagined = destination(Group.WORTH_BUILDING, null, false, false, 0, 12, 3);
        assertTrue(HubProposalCopy.destinationLore(imagined).stream()
                .anyMatch(line -> line.contains("station has to be built")));
    }

    @Test
    void negotiationLinesUseTheCachedDestinationAndTheFee() {
        Destination destination = destination(Group.READY, "port", false, false, 0, 100, -10);
        Terms terms = HubEstimates.applyTerms(destination, 10, 500);
        List<String> operator = HubProposalCopy.operatorLines(destination, terms);
        assertTrue(operator.get(0).contains("before tax"));
        assertTrue(operator.get(1).contains("10.00") && operator.get(1).contains("5.00"));
        assertTrue(operator.get(2).contains("§a+85.00"));

        List<String> host = HubProposalCopy.hostLines(destination, terms, Map.of("north", "North Guild"));
        assertTrue(host.stream().anyMatch(line -> line.contains("Net") && line.contains("§a+5.00")));
        assertTrue(host.stream().anyMatch(line -> line.contains("North Guild") && line.contains("§c-4.00")));
    }

    @Test
    void breakEvenIsTheRateWhereTheHostNetsZero() {
        Destination destination = destination(Group.READY, "port", false, false, 0, 100, -10);
        assertEquals("§7Break-even is about §e10%", HubProposalCopy.breakEvenLine(destination, 0, 0, 20));
        assertEquals("§7Break-even is about §e5%§7 at this fee",
                HubProposalCopy.breakEvenLine(destination, 500, 0, 20));
        assertEquals("§7The host gains even at 0%",
                HubProposalCopy.breakEvenLine(
                        destination(Group.READY, "port", false, false, 0, 100, 5), 0, 0, 20));
        assertEquals("§7The host does not break even inside 0-5%",
                HubProposalCopy.breakEvenLine(destination, 0, 0, 5));
        assertEquals("§7A hub in your own realm has no tax or fee",
                HubProposalCopy.breakEvenLine(destination(Group.READY, "port", true, false, 0, 10, 0), 0, 0, 20));
    }

    @Test
    void rateAndFeeStayInsideTheHostBracket() {
        assertEquals(10, HubProposalCopy.startingRate(0, 20));
        assertEquals(0, HubProposalCopy.clampRate(-5, 0, 20));
        assertEquals(20, HubProposalCopy.clampRate(25, 0, 20));
        assertEquals(50000L, HubProposalCopy.clampFeeCents(60000, 500));
        assertEquals(0L, HubProposalCopy.clampFeeCents(-100, 500));
    }

    private static Destination destination(
            Group group, String installationId, boolean ownRealm, boolean railway, double track,
            double operator, double host) {
        return new Destination(
                group, "host", installationId, "North Port", 2, InstallationKind.PORT, ownRealm,
                railway, track, operator, host, Map.of("north", -4.0), 100);
    }
}
