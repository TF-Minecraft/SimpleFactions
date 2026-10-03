package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.HubOffer;
import net.tfminecraft.simplefactions.guild.hub.OfferKind;
import net.tfminecraft.simplefactions.guild.hub.OfferSide;
import net.tfminecraft.simplefactions.managers.inventory.HubProposalMenu.Draft;

class HubProposalMenuTest {
    @Test
    void refreshKeepsAnUnsentRateWhenTheOfferMoves() {
        Draft draft = new Draft("host", "port", 15, 200, 10, 100);
        Draft next = HubProposalMenu.refreshed(draft, offer(12, 0));
        assertEquals(15, next.rate());
        assertEquals(200, next.feeCents());
        assertEquals(12, next.baseRate());
        assertEquals(0, next.baseFeeCents());
    }

    @Test
    void refreshFollowsTheOfferWhenNothingWasEdited() {
        Draft draft = new Draft("host", "port", 10, 100, 10, 100);
        Draft next = HubProposalMenu.refreshed(draft, offer(12, 0));
        assertEquals(12, next.rate());
        assertEquals(0, next.feeCents());
        assertEquals(12, next.baseRate());
    }

    @Test
    void offerPagesReachPastTheFirstChest() {
        List<String> matters = new ArrayList<>();
        for (int index = 0; index < 46; index++) {
            matters.add("m" + index);
        }
        assertEquals(45, HubProposalMenu.page(matters, 0, 45).size());
        assertEquals(List.of("m45"), HubProposalMenu.page(matters, 1, 45));
    }

    private static HubOffer offer(int rate, long feeCents) {
        return new HubOffer("host", "port", rate, feeCents, OfferSide.GUILD, "Council", 1L, OfferKind.NEW_HUB);
    }
}
