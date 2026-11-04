package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.economy.TaxExemptions;
import net.tfminecraft.simplefactions.identity.Members;

class TaxExemptCommandTest {
    @TempDir Path folder;
    private MockedStatic<Members> members;
    private MockedStatic<SimpleFactions> plugin;
    private Logger logger;
    private Player staff;
    private CommandSender console;
    private final Command command = mock(Command.class);
    private final TaxExemptCommand executor = new TaxExemptCommand();
    private final String npc = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        TaxExemptions.bind(folder);
        TaxExemptions.load();
        members = mockStatic(Members.class, CALLS_REAL_METHODS);
        members.when(() -> Members.display(anyString())).thenReturn("Captain Avalon");
        members.when(() -> Members.accountName(anyString())).thenReturn("Lucy");
        plugin = mockStatic(SimpleFactions.class);
        SimpleFactions instance = mock(SimpleFactions.class);
        logger = mock(Logger.class);
        when(instance.getLogger()).thenReturn(logger);
        plugin.when(SimpleFactions::getInstance).thenReturn(instance);
        staff = mock(Player.class);
        when(staff.getName()).thenReturn("Lucy");
        when(staff.hasPermission(TaxExemptions.PERMISSION)).thenReturn(true);
        members.when(() -> Members.key(staff)).thenReturn(npc);
        console = mock(CommandSender.class);
        when(console.hasPermission(TaxExemptions.PERMISSION)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        plugin.close();
        members.close();
        // Clears the bound folder and the in-memory set for other tests.
        TaxExemptions.bind(null);
        TaxExemptions.load();
    }

    private void run(CommandSender sender, String... args) {
        assertTrue(executor.onCommand(sender, command, "taxexempt", args));
    }

    @Test
    void toggleTurnsTheCurrentCharacterOffAndOnAndLogsIt() {
        run(staff);
        assertTrue(TaxExemptions.isSet(npc));
        verify(staff).sendMessage(contains("Captain Avalon now pays no citizen tax"));
        verify(logger).info("[TaxExempt] Lucy turned citizen tax OFF for Captain Avalon (" + npc + ")");
        assertTrue(Files.exists(folder.resolve("tax-exempt-characters.json")));

        run(staff);
        assertFalse(TaxExemptions.isSet(npc));
        verify(staff).sendMessage("§aCaptain Avalon pays citizen tax again.");
        verify(logger).info("[TaxExempt] Lucy turned citizen tax ON for Captain Avalon (" + npc + ")");
    }

    @Test
    void onAndOffAreExplicit() {
        run(staff, "off");
        verify(staff).sendMessage("§eCaptain Avalon already pays citizen tax.");
        run(staff, "ON");
        assertTrue(TaxExemptions.isSet(npc));
        run(staff, "on");
        verify(staff).sendMessage("§eCaptain Avalon already pays no citizen tax.");
        assertTrue(TaxExemptions.isSet(npc));
        run(staff, "off");
        assertFalse(TaxExemptions.isSet(npc));
    }

    @Test
    void withoutPermissionNothingChanges() {
        Player player = mock(Player.class);
        members.when(() -> Members.key(player)).thenReturn(npc);
        run(player, "on");
        verify(player).sendMessage("§cYou do not have permission to use this command.");
        assertFalse(TaxExemptions.isSet(npc));
        assertEquals(List.of(), executor.onTabComplete(player, command, "taxexempt", new String[] {""}));
    }

    @Test
    void badArgumentsShowUsage() {
        run(staff, "maybe");
        run(staff, "on", "extra");
        verify(staff, org.mockito.Mockito.times(2)).sendMessage("§cUsage: /taxexempt [on|off|list]");
        assertFalse(TaxExemptions.isSet(npc));
    }

    @Test
    void aPlayerWithoutACharacterIsRefused() {
        members.when(() -> Members.key(staff)).thenReturn(null);
        run(staff, "on");
        verify(staff).sendMessage("§cYou need an active character first.");
        assertTrue(TaxExemptions.keys().isEmpty());
    }

    @Test
    void theConsoleCanListButNotToggle() throws Exception {
        run(console, "on");
        verify(console).sendMessage(contains("Only players"));
        run(console, "list");
        verify(console).sendMessage("§eNo character is exempt from citizen tax.");
        TaxExemptions.set(npc, true);
        run(console, "list");
        verify(console).sendMessage("§eCharacters exempt from citizen tax (1):");
        verify(console).sendMessage("§7- §fCaptain Avalon §7(Lucy)");
        members.when(() -> Members.accountName(anyString())).thenReturn(null);
        run(console, "list");
        verify(console).sendMessage("§7- §fCaptain Avalon");
    }

    @Test
    void aFailedSaveIsReportedAndChangesNothing() throws Exception {
        Path blocker = folder.resolve("blocker");
        Files.writeString(blocker, "not a folder");
        TaxExemptions.bind(blocker);
        TaxExemptions.load();
        run(staff, "on");
        verify(staff).sendMessage("§cCould not save the change; nothing was changed. Tell a developer.");
        verify(logger).log(eq(java.util.logging.Level.SEVERE), contains("[TaxExempt] Could not save"), any(Throwable.class));
        verify(logger, never()).info(anyString());
        assertFalse(TaxExemptions.isSet(npc));
    }

    @Test
    void tabCompletesTheSubcommands() {
        assertEquals(List.of("on", "off", "list"), executor.onTabComplete(staff, command, "taxexempt", new String[] {""}));
        assertEquals(List.of("on", "off"), executor.onTabComplete(staff, command, "taxexempt", new String[] {"o"}));
        assertEquals(List.of(), executor.onTabComplete(staff, command, "taxexempt", new String[] {"on", ""}));
    }
}
