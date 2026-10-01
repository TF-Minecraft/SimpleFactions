package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.SupplyHub;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.DormantReason;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubStanding;

class SupplyHubViewTest {
    @Test
    void guildHubLoreShowsActiveAndDormantState() {
        List<String> active = SupplyHubCreator.guildHubLore(
                "North Port", "Port", "Northland", 15, new HubStanding(true, null),
                List.of("§7South Port: §esea §71000 blocks, trade 45%, production 30%"), 12, 4);
        assertTrue(active.contains("§aActive"));
        assertTrue(active.stream().anyMatch(line -> line.contains("South Port") && line.contains("trade 45%")));

        List<String> dormant = SupplyHubCreator.guildHubLore(
                "North Port", "Port", "Northland", 15,
                new HubStanding(false, DormantReason.NO_PERMIT), List.of(), 12, 4);
        assertTrue(dormant.contains("§cDormant §7(the owning faction has not granted a hub permit)"));
    }

    @Test
    void removalOnlyRemovesTheRequestedGuildsHub() {
        SupplyHub target = new SupplyHub("Northland", "North_Port", 10);
        SupplyHub otherAtSite = new SupplyHub("Northland", "North_Port", 11);
        SupplyHub otherSite = new SupplyHub("Southland", "South_Port", 12);
        List<SupplyHub> hubs = new ArrayList<>(List.of(target, otherAtSite, otherSite));

        assertTrue(SupplyHubView.removeGuildHub(hubs, "Northland", "North_Port"));
        assertEquals(List.of(otherAtSite, otherSite), hubs);
        assertFalse(SupplyHubView.removeGuildHub(hubs, "Missing", "North_Port"));
    }
}
