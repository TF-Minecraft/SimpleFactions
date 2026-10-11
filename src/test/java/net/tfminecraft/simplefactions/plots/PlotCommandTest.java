package net.tfminecraft.simplefactions.plots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.tfminecraft.simplefactions.espionage.CharacterNames;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class PlotCommandTest {
    @TempDir Path directory;
    private PlotTestWorld world;
    private MockedStatic<CharacterNames> names;
    private Player master, assassin, helper, target;
    private CommandSender console;
    private final List<String> consoleSaid = new ArrayList<>();
    private final PlotCommand command = new PlotCommand();
    private final Command bukkitCommand = mock(Command.class);

    @BeforeEach
    void setup() throws Exception {
        world = new PlotTestWorld(directory);
        master = world.player("Master", "c-master", 50);
        assassin = world.player("Assassin", "c-assassin", 60);
        helper = world.player("Helper", "c-helper", 80);
        target = world.player("Target", "c-target", 40);
        console = mock(CommandSender.class);
        doAnswer(call -> consoleSaid.add(call.getArgument(0))).when(console).sendMessage(anyString());
        when(console.hasPermission("simplefactions.plots.staff")).thenReturn(true);
        names = mockStatic(CharacterNames.class);
        names.when(() -> CharacterNames.resolveOnline(any(), anyString())).thenAnswer(call -> world.players.values().stream()
                .filter(player -> player.getName().equalsIgnoreCase(call.getArgument(1))).findFirst().orElse(null));
    }

    @AfterEach
    void close() {
        names.close();
        world.close();
    }

    private void run(CommandSender sender, String line) {
        assertTrue(command.onCommand(sender, bukkitCommand, "plot", line.isEmpty() ? new String[0] : line.split(" ")));
    }

    private String last() { return consoleSaid.getLast(); }

    @Test
    void playersRunTheWholePlotByCommand() {
        run(console, "request");
        assertTrue(last().contains("Only players"));
        run(master, "");
        assertTrue(master.getOpenInventory().getTopInventory().getHolder() instanceof PlotMenu.Holder);
        run(master, "request");
        Plot plot = world.store().plotOf("c-master");
        run(console, "approve " + plot.code + " Target");
        run(master, "invite");
        assertTrue(world.last(master).contains("/plot invite <player or character>"));
        run(master, "invite Nobody");
        run(master, "invite Assassin");
        run(assassin, "accept");
        run(assassin, "accept " + plot.code);
        run(master, "invite Helper");
        run(helper, "decline " + plot.code);
        run(master, "role Assassin");
        run(master, "role Assassin juggle");
        run(master, "role Nobody assassin");
        assertTrue(world.last(master).contains("Nobody by that name"));
        run(master, "role Char Assassin assassin");
        assertEquals(PlotRole.ASSASSIN, plot.member("c-assassin").role);
        run(master, "role assassin strength");
        assertEquals(CollaboratorTask.STRENGTH, plot.member("c-assassin").task);
        run(master, "role Assassin none");
        run(master, "role Assassin assassin");
        run(helper, "role Assassin assassin");
        run(master, "inform Helper");
        run(master, "start");
        assertEquals(PlotPhase.BUILDING, plot.phase);
        run(master, "launch");
        run(assassin, "hunt");
        assertTrue(world.last(assassin).contains("Only an assassin can hunt"));
        run(master, "status");
        assertTrue(world.last(master).contains("Plot against"));
        plot.knowledge.put("threat", "Someone");
        run(target, "status");
        assertTrue(world.last(target).contains("Someone"));
        run(world.player("Nobody2", null, null), "status");
        plot.discovered = true;
        run(target, "investigator Helper");
        run(master, "approach");
        run(master, "approach offensive");
        run(target, "defender");
        run(target, "defender self");
        run(target, "defender spymaster");
        run(target, "defender Helper");
        run(target, "defender Nobody");
        run(assassin, "leave");
        run(master, "invite Assassin");
        run(assassin, "accept " + plot.code);
        run(master, "dismiss Assassin");
        assertNull(plot.member("c-assassin"));
        run(master, "abandon");
        assertTrue(world.last(master).contains("/plot abandon confirm"));
        run(master, "abandon confirm");
        assertEquals(PlotPhase.DISSOLVED, plot.phase);
        run(master, "dance");
        assertTrue(world.last(master).contains("request|accept"));
        run(helper, "request");
        run(helper, "status");
        assertTrue(world.last(helper).contains("awaits approval"));
    }

    @Test
    void staffApproveInspectAndEndPlots() {
        CommandSender player = mock(CommandSender.class);
        run(player, "list");
        org.mockito.Mockito.verify(player).sendMessage(eq("§cYou do not have permission to manage plots."));
        run(console, "list");
        assertEquals("§7No plots are running.", last());
        run(master, "request");
        Plot plot = world.store().plotOf("c-master");
        run(console, "list");
        assertTrue(last().contains("-> ?"));
        run(console, "info " + plot.code);
        assertTrue(consoleSaid.stream().anyMatch(line -> line.contains("none yet")));
        run(console, "approve " + plot.code);
        assertTrue(last().contains("/plot approve <code> <target player>"));
        run(console, "deny");
        run(console, "approve " + plot.code + " Target");
        run(console, "list");
        assertTrue(last().contains("Char Target (Target)"));
        PlotService.invite(master, assassin);
        PlotService.accept(assassin, plot.code);
        plot.informed.add(world.character(helper));
        run(console, "info " + plot.code);
        assertTrue(consoleSaid.stream().anyMatch(line -> line.contains("Protection 24, betrayal NONE")));
        run(console, "info P-NONE");
        assertEquals("§cNo plot has that code.", last());
        run(console, "day " + plot.code);
        run(console, "cancel " + plot.code);
        run(console, "info " + plot.code);
        assertTrue(consoleSaid.stream().anyMatch(line -> line.contains("(Ended by staff)")));
        run(helper, "request");
        run(console, "deny " + world.store().plotOf("c-helper").code);
    }

    @Test
    void tabCompletionOffersStaffCommandsOnlyToStaff() {
        assertEquals(List.of(), command.onTabComplete(master, bukkitCommand, "plot", new String[]{"a", "b"}));
        assertEquals(List.of("accept", "abandon", "approach"), command.onTabComplete(master, bukkitCommand, "plot", new String[]{"A"}));
        assertEquals(List.of("accept", "abandon", "approach", "approve"), command.onTabComplete(console, bukkitCommand, "plot", new String[]{"a"}));
    }
}
