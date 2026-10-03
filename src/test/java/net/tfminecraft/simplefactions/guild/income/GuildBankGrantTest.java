package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;

class GuildBankGrantTest {

    private final Guild guild = mock(Guild.class);
    private final Bank bank = new Bank(guild, 100.0, null);
    private final Ledger ledger = new Ledger(guild);

    @BeforeEach
    void setUp() {
        when(guild.getBank()).thenReturn(bank);
        when(guild.getLedger()).thenReturn(ledger);
    }

    @Test
    void positiveWholeCentAmountIsAddedAndRecordedAsCompensation() {
        assertTrue(GuildBankGrant.grant(guild, 25.75));

        assertEquals(125.75, bank.getWealth(), 1e-9);
        assertEquals(25.75, ledger.getHistory().getDepositsToday().get("Compensation"), 1e-9);
    }

    @Test
    void missingBankAndInvalidAmountsDoNotChangeTheBalance() {
        when(guild.getBank()).thenReturn(null);
        assertFalse(GuildBankGrant.grant(guild, 25.0));
        when(guild.getBank()).thenReturn(bank);

        assertFalse(GuildBankGrant.grant(guild, 0));
        assertFalse(GuildBankGrant.grant(guild, -1));
        assertFalse(GuildBankGrant.grant(guild, 0.005));

        assertEquals(100.0, bank.getWealth(), 1e-9);
        assertTrue(ledger.getHistory().getDepositsToday().isEmpty());
    }

    @Test
    void commandReportsUnknownGuildWithoutChangingTheBank() {
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        when(sender.hasPermission("simplefactions.admin")).thenReturn(true);
        when(command.getName()).thenReturn("faction");

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getGuildByString("missing")).thenReturn(null);

            assertTrue(new CommandManager().onCommand(sender, command, "faction",
                    new String[] {"addguildbank", "missing", "25"}));
        }

        verify(sender).sendMessage("§a[SimpleFactions]§c Error! guild does not exist!");
        assertEquals(100.0, bank.getWealth(), 1e-9);
    }

    @Test
    void commandRejectsNonAdminBeforeLookingUpGuild() {
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        when(sender.hasPermission("simplefactions.admin")).thenReturn(false);
        when(command.getName()).thenReturn("faction");

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            assertTrue(new CommandManager().onCommand(sender, command, "faction",
                    new String[] {"addguildbank", "guild", "25"}));
            factions.verify(() -> FactionManager.getGuildByString("guild"), never());
        }

        verify(sender).sendMessage("§a[SimpleFactions]§c You do not have access to this command");
        assertEquals(100.0, bank.getWealth(), 1e-9);
    }
}
