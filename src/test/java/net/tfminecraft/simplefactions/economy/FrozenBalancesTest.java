package net.tfminecraft.simplefactions.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.BranchIncomePreview;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;

class FrozenBalancesTest {
    private final List<Faction> savedFactions = new ArrayList<>();
    private Guild guild;

    @BeforeEach
    void setUp() {
        savedFactions.addAll(FactionManager.factions);
        FactionManager.factions.clear();
        guild = mock(Guild.class);
        when(guild.getIncarnationId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000042"));
        when(guild.getBank()).thenAnswer(invocation -> new Bank(guild, null));
        GuildHandler handler = mock(GuildHandler.class);
        when(handler.getGuilds()).thenReturn(new ArrayList<>(List.of(guild)));
        Faction faction = mock(Faction.class);
        when(faction.getGuildHandler()).thenReturn(handler);
        FactionManager.factions.add(faction);
    }

    @AfterEach
    void tearDown() {
        FactionManager.factions.clear();
        FactionManager.factions.addAll(savedFactions);
    }

    @Test
    void asyncReadsUseBalancesCapturedOnTheMainThread() throws Exception {
        FrozenBalances frozen;
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(1234L);
            frozen = FrozenBalances.capture();
        }

        // Static mocks stay on the test thread, so the worker reaches the real ledger guard.
        // Without a server, the guard fails inside Bukkit.isPrimaryThread rather than with its own message.
        CompletableFuture<Double> live = CompletableFuture.supplyAsync(() -> guild.getBank().getWealth());
        ExecutionException refused = assertThrows(ExecutionException.class, live::get);
        assertTrue(Arrays.stream(refused.getCause().getStackTrace())
                .anyMatch(frame -> frame.getMethodName().equals("requireMainThread")));

        assertEquals(12.34, CompletableFuture.supplyAsync(() -> frozen.read(() -> guild.getBank().getWealth())).get());
        assertEquals(0L, frozen.read(() -> FrozenBalances.balance("unknown")));
    }

    @Test
    void readRestoresTheLiveLedgerAfterwards() {
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(500L);
            FrozenBalances frozen = FrozenBalances.capture();
            denars.when(() -> Denars.balance(anyString())).thenReturn(700L);

            assertEquals(5.0, frozen.read(() -> guild.getBank().getWealth()));
            assertEquals(7.0, guild.getBank().getWealth());
        }
    }

    @Test
    void nestedReadsRestoreTheOuterBalances() {
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(100L);
            FrozenBalances outer = FrozenBalances.capture();
            denars.when(() -> Denars.balance(anyString())).thenReturn(200L);
            FrozenBalances inner = FrozenBalances.capture();

            assertEquals(1.0, outer.read(() -> {
                assertEquals(2.0, inner.read(() -> guild.getBank().getWealth()));
                return guild.getBank().getWealth();
            }));
        }
    }

    @Test
    void previewCopiesCarryTheBalancesFromWhenTheyWereMade() {
        ProvinceManager live = new ProvinceManager();
        live.start(Map.of());
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(-300L);
            EconomicPreview.Prepared economic = EconomicPreview.prepare(live);
            BranchIncomePreview.Prepared branch = BranchIncomePreview.prepare(live);
            denars.when(() -> Denars.balance(anyString())).thenReturn(0L);

            assertEquals(-3.0, economic.balances().read(() -> guild.getBank().getWealth()));
            assertEquals(-3.0, branch.balances().read(() -> guild.getBank().getWealth()));
        }
    }
}
