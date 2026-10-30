package net.tfminecraft.simplefactions.economy.settlement;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyPreparationJournalTest {
    @Test void crashDuringPreparationReplaysTheChosenProvinceAndExactDayValues() throws Exception {
        try (var disk = new PersistenceFilesFixture(); var fixture = new FactionDomainFixture(); var titles = mockStatic(TitleManager.class, CALLS_REAL_METHODS)) {
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            faction.addProvince(1); faction.addProvince(2); faction.addProvince(3);
            faction.setCapital(1, true);
            var movement = new Movement(faction, new MovementData());
            movement.restoreOrganization(20);
            faction.getGovernment().getMovements().add(movement);
            titles.when(() -> TitleManager.overProvinceCap(faction)).thenReturn(true);
            Field offline = FactionCleanup.class.getDeclaredField("offlineDays");
            offline.setAccessible(true);
            Object previous = offline.get(null);
            offline.set(null, new HashMap<>(Map.of(faction.getLeader(), 3)));
            try {
                var database = mock(Database.class);
                when(database.saveFactionDurably(eq(faction), any())).thenReturn(false, true);
                var journal = new DailyPreparationJournal(disk.root.resolve("day-preparation"), database);
                assertThrows(IOException.class, () -> journal.prepare(2, 1000));
                assertEquals(List.of(1, 2), faction.getProvinces());
                // A later retry cannot choose the next province, increment organization twice or change the timestamp.
                var restored = new DailyPreparationJournal(disk.root.resolve("day-preparation"), database);
                var prepared = restored.prepare(2, 9000);
                assertEquals(1000, prepared.timestamp());
                assertEquals(List.of(1, 2), faction.getProvinces());
                assertEquals(10, movement.getOrganization());
                assertEquals(4, FactionCleanup.daysOffline(faction.getLeader()));
                restored.prepare(2, 10000);
                assertEquals(10, movement.getOrganization());
                assertEquals(4, FactionCleanup.daysOffline(faction.getLeader()));
                assertEquals(List.of(2L), restored.unfinishedDays());
                restored.complete(2);
                assertTrue(restored.unfinishedDays().isEmpty());
            } finally { offline.set(null, previous); }
        }
    }

    @Test void aCrashBeforeEitherFactionBatchIsOnDiskLeavesAStepThatReplaysExactly() throws Exception {
        crashAtFactionDirectorySync(1);
        crashAtFactionDirectorySync(2);
    }

    /** Fails the given sync of the faction directory, as if the server died before that batch was durable. */
    private static void crashAtFactionDirectorySync(int failing) throws Exception {
        try (var disk = new PersistenceFilesFixture(); var fixture = new FactionDomainFixture(); var titles = mockStatic(TitleManager.class, CALLS_REAL_METHODS);
                var channels = mockStatic(FileChannel.class, CALLS_REAL_METHODS)) {
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            faction.addProvince(1); faction.addProvince(2); faction.addProvince(3);
            faction.setCapital(1, true);
            var movement = new Movement(faction, new MovementData());
            movement.restoreOrganization(20);
            faction.getGovernment().getMovements().add(movement);
            titles.when(() -> TitleManager.overProvinceCap(faction)).thenReturn(true);
            Path data = disk.root.resolve("Data");
            int[] syncs = {0};
            channels.when(() -> FileChannel.open(any(Path.class), any(OpenOption[].class))).thenAnswer(call -> {
                if (!Arrays.asList(call.getArguments()).contains(StandardOpenOption.READ)) return call.callRealMethod();
                if (call.getArgument(0).equals(data) && ++syncs[0] == failing) throw new IOException("simulated crash");
                return mock(FileChannel.class);
            });
            Field offline = FactionCleanup.class.getDeclaredField("offlineDays");
            offline.setAccessible(true);
            Object previous = offline.get(null);
            offline.set(null, new HashMap<>(Map.of(faction.getLeader(), 3)));
            try {
                var database = mock(Database.class);
                when(database.saveFactionDurably(eq(faction), any())).thenAnswer(call -> {
                    call.<DurableJson.Batch>getArgument(1).write(data.resolve("realm.json"), Map.of("provinces", faction.getProvinces()));
                    return true;
                });
                var folder = disk.root.resolve("day-preparation");
                assertThrows(IOException.class, () -> new DailyPreparationJournal(folder, database).prepare(2, 1000));
                Map<?, ?> journal = DurableJson.read(folder.resolve("2.json"), Map.class);
                // Removals are journaled as done only after their batch; the day is never marked prepared.
                assertEquals(failing == 2, journal.get("organization") != null);
                assertEquals(false, journal.get("prepared"));
                assertEquals(3, FactionCleanup.daysOffline(faction.getLeader()));
                var prepared = new DailyPreparationJournal(folder, database).prepare(2, 9000);
                assertEquals(1000, prepared.timestamp());
                assertEquals(List.of(1, 2), faction.getProvinces());
                assertEquals(10, movement.getOrganization());
                assertEquals(4, FactionCleanup.daysOffline(faction.getLeader()));
                assertEquals(true, DurableJson.read(folder.resolve("2.json"), Map.class).get("prepared"));
            } finally { offline.set(null, previous); }
        }
    }

    @Test void aHeldRealmKeepsItsProvincesAndArmyAndPaysNoUpkeep() throws Exception {
        try (var disk = new PersistenceFilesFixture(); var fixture = new FactionDomainFixture(); var titles = mockStatic(TitleManager.class, CALLS_REAL_METHODS)) {
            String leader = UUID.randomUUID().toString();
            var faction = fixture.saved("realm", leader);
            faction.addProvince(1); faction.addProvince(2);
            faction.setCapital(1, true);
            titles.when(() -> TitleManager.overProvinceCap(faction)).thenReturn(true);
            var guard = new net.tfminecraft.simplefactions.army.Regiment(fixture.regiment("guard", false, 2, 5));
            guard.setCurrentSlots(6);
            faction.getMilitary().getRegiments().add(guard);
            faction.succeedDeadLeader(leader, "");
            net.tfminecraft.simplefactions.guild.HeldRealms.hold(faction, leader, 1);
            var database = mock(Database.class);
            when(database.saveFactionDurably(eq(faction), any())).thenReturn(true);
            new DailyPreparationJournal(disk.root.resolve("day-preparation"), database).prepare(2, 1000);
            assertEquals(List.of(1, 2), faction.getProvinces());
            var upkeep = FactionUpkeepPlan.calculate(faction, 0);
            assertEquals(0, upkeep.charge());
            assertEquals(Map.of("guard", 6), upkeep.regimentSlots());
        }
    }

    @Test void malformedOrReplacedPreparationCannotStartAnotherDay() throws Exception {
        try (var disk = new PersistenceFilesFixture(); var fixture = new FactionDomainFixture()) {
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            var database = mock(Database.class);
            var folder = disk.root.resolve("day-preparation");
            var journal = new DailyPreparationJournal(folder, database);
            assertTrue(journal.unfinishedDays().isEmpty());
            assertThrows(IOException.class, () -> journal.prepare(4, 1000));
            FactionManager.factions.clear();
            assertThrows(IOException.class, () -> journal.prepare(4, 9000));
            DurableJson.write(folder.resolve("7.json"), Map.of("day", 2));
            assertThrows(IOException.class, journal::unfinishedDays);
        }
    }
}
