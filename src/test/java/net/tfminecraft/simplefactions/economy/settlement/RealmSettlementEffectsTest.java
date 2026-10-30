package net.tfminecraft.simplefactions.economy.settlement;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.TimerData;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.TestTreasuries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealmSettlementEffectsTest {
    @TempDir Path folder;

    @Test void partialFactionSaveKeepsClaimsAndClockPendingAndReplaysExactAfterValues() throws Exception {
        try (var fixture = new FactionDomainFixture()) {
            FactionManager.day = 0;
            UUID character = UUID.randomUUID();
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            var capital = faction.getOrCreateMainGuild();
            var guild = fixture.guild(faction, "guild", character.toString());
            TestTreasuries.set(guild.getBank(), 10);
            var company = new MercenaryCompany(guild, "Company", null, 0);
            company.accrueWage(character.toString(), 30);
            guild.setCompany(company);
            var loan = new Loan("loan", 100, capital, guild, 1000, 1, 0, 0, true);
            capital.getLoanHandler().issueLoan(loan);
            var plan = new RealmSettlementCalculation(2, 100000000L, FactionManager.getAllGuilds(), List.of())
                    .calculate((balances, legs, changes) -> changes.playerIncome.computeIfAbsent(character, key -> new java.util.LinkedHashMap<>()).put(PlayerCashflow.WAGES.name(), 10.0));
            var database = mock(Database.class);
            when(database.saveFactionDurably(eq(faction), any())).thenReturn(false, true);
            var deaths = mock(RealmSettlementEffects.Payments.class);
            var vehicles = mock(RealmSettlementEffects.Vehicles.class);
            var economy = new PlayerEconomyManager();
            var effects = new RealmSettlementEffects(folder, database, economy, deaths, vehicles);
            assertThrows(IOException.class, () -> effects.apply(plan));
            verifyNoInteractions(deaths, vehicles);
            effects.apply(plan);
            double owed = loan.getTotalOwed();
            effects.apply(plan);
            assertEquals(owed, loan.getTotalOwed());
            assertEquals(10, economy.getLedger(character).getNetDaily());
            assertEquals(3, JsonUtil.GSON.fromJson(java.nio.file.Files.readString(folder.resolve("Cache/data.json")), TimerData.class).day);
            assertEquals(3, FactionManager.day);
            verify(deaths, times(2)).apply("sf:settle:2", Map.of());
            assertEquals(10, guild.getBank().getWealth(), "Effects never perform money movement");
        }
    }

    @Test void aCrashBeforeTheFactionBatchIsOnDiskHoldsBackEveryLaterEffect() throws Exception {
        try (var fixture = new FactionDomainFixture(); var channels = mockStatic(FileChannel.class, CALLS_REAL_METHODS)) {
            FactionManager.day = 0;
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            var plan = new RealmSettlementCalculation(4, 100000000L, FactionManager.getAllGuilds(), List.of()).calculate((b, l, c) -> {});
            Path data = folder.toAbsolutePath().resolve("Data");
            boolean[] failing = {true};
            channels.when(() -> FileChannel.open(any(Path.class), any(OpenOption[].class))).thenAnswer(call -> {
                if (!Arrays.asList(call.getArguments()).contains(StandardOpenOption.READ)) return call.callRealMethod();
                if (failing[0] && call.getArgument(0).equals(data)) throw new IOException("simulated crash");
                return mock(FileChannel.class);
            });
            var database = mock(Database.class);
            when(database.saveFactionDurably(eq(faction), any())).thenAnswer(call -> {
                call.<DurableJson.Batch>getArgument(1).write(data.resolve("realm.json"), Map.of("id", faction.getId()));
                return true;
            });
            var deaths = mock(RealmSettlementEffects.Payments.class);
            var vehicles = mock(RealmSettlementEffects.Vehicles.class);
            var effects = new RealmSettlementEffects(folder, database, new PlayerEconomyManager(), deaths, vehicles);
            assertThrows(IOException.class, () -> effects.apply(plan));
            verifyNoInteractions(deaths, vehicles);
            assertFalse(Files.exists(folder.resolve("Cache/data.json")));
            assertEquals(0, FactionManager.day);
            failing[0] = false;
            effects.apply(plan);
            verify(vehicles).apply(any());
            verify(deaths).apply("sf:settle:4", Map.of());
            assertEquals(5, JsonUtil.GSON.fromJson(Files.readString(folder.resolve("Cache/data.json")), TimerData.class).day);
            assertEquals(5, FactionManager.day);
        }
    }

    @Test void replacementIncarnationFailsBeforeAnySave() throws Exception {
        try (var fixture = new FactionDomainFixture()) {
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            var plan = new RealmSettlementCalculation(3, 1000, FactionManager.getAllGuilds(), List.of()).calculate((b,l,c) -> {});
            var changes = JsonUtil.GSON.fromJson(plan.changes(), RealmSettlementChanges.class);
            changes.guilds.getFirst().incarnation = UUID.randomUUID();
            var altered = new SettlementJournal.Plan(3, plan.legs(), JsonUtil.GSON.toJsonTree(changes).getAsJsonObject());
            var database = mock(Database.class);
            var effects = new RealmSettlementEffects(folder, database, new PlayerEconomyManager(), (id, paid) -> fail(), c -> fail());
            assertThrows(IOException.class, () -> effects.apply(altered));
            verifyNoInteractions(database);
        }
    }
}
