package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.SupplyHubTutorial.Decision;

class SupplyHubTutorialTest {
    @Test
    void tutorialDecisionShowsNotesOrDoesNothing() {
        assertEquals(Decision.SHOW, SupplyHubTutorial.decision(true, false, false, false));
        assertEquals(Decision.NOTE, SupplyHubTutorial.decision(true, false, false, true));
        assertEquals(Decision.NONE, SupplyHubTutorial.decision(false, false, false, false));
        assertEquals(Decision.NONE, SupplyHubTutorial.decision(true, true, false, false));
        assertEquals(Decision.NONE, SupplyHubTutorial.decision(true, false, true, false));
    }
}
