package net.tfminecraft.simplefactions.plots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("deprecation")
class PlotMenuTest {
    @TempDir Path directory;
    private PlotTestWorld world;
    private Player master, assassin, helper, target;
    private CommandSender staff;

    @BeforeEach
    void setup() throws Exception {
        world = new PlotTestWorld(directory);
        master = world.player("Master", "c-master", 50);
        assassin = world.player("Assassin", "c-assassin", 60);
        helper = world.player("Helper", "c-helper", 80);
        target = world.player("Target", "c-target", 40);
        staff = mock(CommandSender.class);
        List<Player> everyone = new ArrayList<>(List.of(master, assassin, helper, target));
        when(world.gui.world.getPlayers()).thenReturn(everyone);
    }

    @AfterEach
    void close() { world.close(); }

    private PlotMenu.Holder holder(Player player) {
        return (PlotMenu.Holder) player.getOpenInventory().getTopInventory().getHolder();
    }

    private ItemStack item(Player player, int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }

    private String name(Player player, int slot) { return item(player, slot).getItemMeta().getDisplayName(); }

    private List<String> lore(Player player, int slot) { return item(player, slot).getItemMeta().getLore(); }

    private void click(Player player, int slot, ClickType type) { holder(player).actions.get(slot).accept(type); }

    private void click(Player player, int slot) { click(player, slot, ClickType.LEFT); }

    private Plot building() {
        Plot plot = PlotService.request(master);
        PlotService.approve(staff, plot.code, "Target");
        PlotService.invite(master, assassin);
        PlotService.accept(assassin, plot.code);
        PlotService.assign(master, "c-assassin", PlotRole.ASSASSIN, null);
        return plot;
    }

    @Test
    void theMainMenuShowsIntrigueAndStartsAPlot() {
        PlotMenu.open(world.player("Nobody", null, null));
        PlotMenu.open(master);
        assertEquals("§5Intrigue: §e50/100", name(master, 4));
        assertTrue(lore(master, 4).contains("§7When plotting: §e58"));
        assertEquals("§5Approach: §eNeutral", name(master, 2));
        assertTrue(lore(master, 6).contains("§7Protection now: §e29"));
        assertEquals("§5Begin a plot", name(master, 22));
        click(master, 2);
        assertEquals("§5Approach: §eDefensive", name(master, 2));
        click(master, 22);
        assertEquals("§5Assassination", name(master, 13));
        click(master, 22);
        assertEquals("§5Begin a plot", name(master, 22));
        click(master, 22);
        click(master, 13);
        assertNotNull(world.store().plotOf("c-master"));
        PlotMenu.open(master);
        assertEquals("§5Plot request §eP-ABCDE", name(master, 22));
        click(master, 31);
        assertNotNull(world.store().plotOf("c-master"));
        click(master, 31, ClickType.SHIFT_LEFT);
        assertNull(world.store().plotOf("c-master"));
        click(master, 49);
    }

    @Test
    void theDefenderMenuOffersSelfSpymasterAndSomeoneNearby() {
        PlotMenu.open(target);
        click(target, 6);
        click(target, 13);
        assertTrue(world.heard(target, "no Spymaster"));
        PlotMenu.defender(target);
        click(target, 15);
        assertTrue(name(target, 0).contains("Char "));
        when(helper.getLocation()).thenReturn(new org.bukkit.Location(world.gui.world, 100, 64, 0));
        when(assassin.getWorld()).thenReturn(world.otherWorld);
        PlotMenu.nearby(target, PlotMenu.Purpose.DEFENDER);
        assertEquals(2, holder(target).actions.size());
        click(target, 0);
        assertEquals(DefenderChoice.CHARACTER, world.store().peek("c-target").defender());
        assertEquals("§5Defended by: §eChar Master", name(target, 6));
        world.pass(PlotConfig.defenderCooldown());
        PlotMenu.defender(target);
        click(target, 11);
        assertEquals(DefenderChoice.SELF, world.store().peek("c-target").defender());
        PlotMenu.defender(target);
        click(target, 22);
        when(world.gui.world.getPlayers()).thenReturn(List.of(target));
        PlotMenu.nearby(target, PlotMenu.Purpose.INVITE);
        assertEquals("§7Nobody is near you", name(target, 22));
        click(target, 49);
    }

    @Test
    void theMasterRecruitsGivesRolesAndRunsThePlot() {
        Plot plot = PlotService.request(master);
        PlotService.approve(staff, plot.code, "Target");
        PlotMenu.open(master);
        assertTrue(lore(master, 22).stream().anyMatch(line -> line.contains("Start it within")));
        click(master, 29);
        int assassinSlot = -1;
        for (int slot = 0; slot < 45; slot++)
            if (item(master, slot) != null && name(master, slot).equals("§eChar Assassin")) assassinSlot = slot;
        click(master, assassinSlot);
        assertTrue(plot.invitations.containsKey("c-assassin"));
        PlotMenu.open(assassin);
        assertEquals("§5Proposal from §eChar Master", name(assassin, 45));
        click(assassin, 45);
        assertNotNull(plot.member("c-assassin"));
        PlotService.invite(master, helper);
        PlotMenu.open(helper);
        click(helper, 45, ClickType.RIGHT);
        assertTrue(plot.invitations.isEmpty());

        PlotMenu.open(master);
        click(master, 20);
        assertEquals("§eChar Assassin", name(master, 10));
        click(master, 10);
        click(master, 10);
        assertEquals(PlotRole.ASSASSIN, plot.member("c-assassin").role);
        click(master, 10);
        click(master, 12);
        assertEquals(CollaboratorTask.PROGRESS, plot.member("c-assassin").task);
        click(master, 10);
        click(master, 14);
        assertEquals(PlotRole.UNASSIGNED, plot.member("c-assassin").role);
        click(master, 10);
        click(master, 22);
        click(master, 10);
        click(master, 16);
        assertNotNull(plot.member("c-assassin"));
        click(master, 10);
        click(master, 10);
        click(master, 22);
        click(master, 24);
        assertEquals(PlotPhase.BUILDING, plot.phase);
        assertTrue(lore(master, 22).stream().anyMatch(line -> line.startsWith("§7Secrecy: ")));
        click(master, 33);
        assertEquals("§7Click to choose.", lore(master, 0).getFirst());
        click(master, 49);
        click(master, 20);
        click(master, 10);
        click(master, 16, ClickType.SHIFT_LEFT);
        assertNull(plot.member("c-assassin"));
    }

    @Test
    void conspiratorsSeeTheirPlotAndTheTargetSeesThreats() {
        Plot plot = building();
        PlotService.begin(master);
        plot.informed.add(world.character(helper));
        PlotMenu.open(assassin);
        assertTrue(lore(assassin, 20).contains("§8Char Helper - Informed"));
        assertNull(holder(assassin).actions.get(20));
        click(assassin, 33);
        PlotMenu.nearby(assassin, PlotMenu.Purpose.INFORM);
        assertSame(assassin.getOpenInventory().getTopInventory(), holder(assassin).getInventory());
        click(assassin, 0);
        assertTrue(world.heard(assassin, "cannot be marked"));
        PlotMenu.open(assassin);
        click(assassin, 24);
        assertNotNull(plot.member("c-assassin"));
        click(assassin, 24, ClickType.SHIFT_LEFT);
        assertNull(plot.member("c-assassin"));
        PlotService.invite(master, assassin);
        PlotService.accept(assassin, plot.code);
        PlotService.assign(master, "c-assassin", PlotRole.ASSASSIN, null);

        plot.phase = PlotPhase.READY;
        PlotMenu.open(master);
        assertEquals("§cBegin the final phase", name(master, 24));
        click(master, 24);
        assertEquals(PlotPhase.INVESTIGATION, plot.phase);
        assertTrue(lore(master, 22).stream().anyMatch(line -> line.contains("Assassins may strike in")));
        plot.phase = PlotPhase.EXECUTION;
        PlotMenu.open(master);
        assertTrue(lore(master, 22).stream().anyMatch(line -> line.contains("Time left to strike")));
        PlotMenu.open(assassin);
        assertFalse(holder(assassin).actions.containsKey(24));

        PlotMenu.open(target);
        assertNull(item(target, 40));
        plot.discovered = true;
        plot.knowledge.put("threat", "Someone is plotting against your life.");
        world.store().settings("c-target").investigators.add(world.character(helper));
        PlotMenu.open(target);
        assertEquals(List.of("§7Someone is plotting against your life."), lore(target, 40));
        assertEquals("§7Char Helper", lore(target, 42).getFirst());
        click(target, 42);
        PlotMenu.nearby(target, PlotMenu.Purpose.INVESTIGATOR);
        click(target, 0);

        click(master, 31);
        PlotMenu.open(master);
        click(master, 31, ClickType.SHIFT_LEFT);
        assertEquals(PlotPhase.DISSOLVED, plot.phase);
        PlotMenu.members(master);
    }

    @Test
    void onlyFourProposalsShowAndIntrigueMayBeUnknown() {
        for (int index = 0; index < 5; index++) {
            Plot plot = new Plot();
            plot.code = "P-" + index;
            plot.master = new PlotCharacter("m" + index, null, "M", "M" + index);
            plot.target = new PlotCharacter("t", null, "T", "T");
            plot.phase = index == 4 ? PlotPhase.READY : PlotPhase.RECRUITING;
            plot.invitations.put("c-helper", world.clock.get() + 1000);
            world.store().plots.add(plot);
        }
        Plot other = new Plot();
        other.code = "P-X";
        other.master = new PlotCharacter("mx", null, "M", "MX");
        other.target = new PlotCharacter("t", null, "T", "T");
        other.phase = PlotPhase.RECRUITING;
        other.invitations.put("c-helper", world.clock.get() + 1000);
        world.store().plots.add(other);
        PlotMenu.open(helper);
        assertNotNull(item(helper, 48));
        assertNotNull(holder(helper).actions.get(49));
        var settings = world.store().settings("c-x");
        settings.defender = DefenderChoice.CHARACTER;
        settings.helper = world.character(master);
        ItemStack unknown = PlotMenu.intrigueItem(new PlotCharacter("c-x", null, "X", "X"), null);
        assertEquals("§5Intrigue: §e?", unknown.getItemMeta().getDisplayName());
        assertTrue(unknown.getItemMeta().getLore().contains("§7Defended by: §eChar Master"));
        assertEquals("§a|||||§8||||| §750%", PlotMenu.bar(50));
        assertEquals("§a§8|||||||||| §7-5%", PlotMenu.bar(-5));
    }

    @Test
    void theMembersMenuNeedsAPlotMaster() {
        PlotMenu.members(helper);
        assertTrue(world.heard(helper, "not leading a plot"));
    }
}
