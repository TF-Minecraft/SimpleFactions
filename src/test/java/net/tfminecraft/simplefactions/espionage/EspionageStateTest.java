package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.JsonUtil;

class EspionageStateTest {
    private SpecialPositionAssignment holder() {
        var holder = new SpecialPositionAssignment();
        holder.playerId = UUID.fromString("e8601e44-34d9-44f8-a62a-2628f81d338e");
        holder.playerName = "Spy";
        holder.characterId = "character-a";
        return holder;
    }

    private IntelligenceReport report() {
        return EspionageService.createReport(Map.of("Professional army", 5.0, "Daily net income", -10.0),
                60, new Random(5));
    }

    @Test
    void noSpymasterHasZeroAptitudeAndIndependentOffenseDefenseLuck() {
        var state = new EspionageState();
        var actual = state.rolls(10, new Random(12));
        var random = new Random(12);
        assertEquals(EspionageMath.dailyRoll(0, 0, random), actual.offense());
        assertEquals(EspionageMath.dailyRoll(0, 0, random), actual.defense());
    }

    @Test
    void dailyRollsCannotBeRefreshedByAppointmentRemovalOrSabotage() {
        var state = new EspionageState();
        var first = state.rolls(10, new Random(1));
        var holder = holder();
        state.appoint(holder, () -> 100);
        holder.offenseReduction = 100;
        holder.defenseReduction = 50;
        assertSame(first, state.rolls(10, new Random(2)));
        var next = state.rolls(11, new Random(3));
        var random = new Random(3);
        assertEquals(EspionageMath.dailyRoll(100, 100, random), next.offense());
        assertEquals(EspionageMath.dailyRoll(100, 50, random), next.defense());
        state.removeSpymaster();
        assertSame(next, state.rolls(11, new Random(4)));
    }

    @Test
    void reappointingSameCharacterKeepsAptitudeAndResetsSabotage() {
        var state = new EspionageState();
        var first = holder();
        state.appoint(first, () -> 12);
        first.offenseReduction = 100;
        state.removeSpymaster();
        var second = holder();
        state.appoint(second, () -> { fail("An office cannot be used to reroll aptitude"); return 100; });
        assertEquals(12, second.aptitude);
        assertEquals(0, second.offenseReduction);
        second = holder();
        second.characterId = "character-b";
        state.appoint(second, () -> 80);
        assertEquals(80, second.aptitude);
    }

    @Test
    void reportIsSharedSnapshotAndRefreshesOnlyNextDayOrNewFactionIncarnation() {
        var state = new EspionageState();
        AtomicInteger created = new AtomicInteger();
        var supplier = (java.util.function.Supplier<IntelligenceReport>) () -> { created.incrementAndGet(); return report(); };
        var first = state.report("target", 100, 10, supplier);
        assertSame(first, state.report("target", 100, 10, supplier));
        assertEquals(1, created.get());
        assertNotSame(first, state.report("target", 100, 11, supplier));
        state.report("other", 200, 11, supplier);
        assertNotSame(first, state.report("target", 300, 11, supplier));
        assertEquals(4, created.get());
    }

    @Test
    void factionJsonRoundTripKeepsOfficeSabotageAptitudeAndBothDailyCaches() {
        FactionData data = new FactionData();
        data.espionage = new EspionageState();
        var holder = holder();
        data.espionage.appoint(holder, () -> 82);
        holder.defenseReduction = 75;
        var rolls = data.espionage.rolls(42, new Random(1));
        var report = data.espionage.report("target", 123, 42, this::report);
        FactionData restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(data), FactionData.class);
        assertEquals(holder.playerId, restored.espionage.getSpymaster().playerId);
        assertEquals(82, restored.espionage.getSpymaster().aptitude);
        assertEquals(75, restored.espionage.getSpymaster().defenseReduction);
        assertEquals(rolls, restored.espionage.rolls(42, new Random(99)));
        var cached = restored.espionage.report("target", 123, 42, () -> { fail("Restart cannot reroll"); return null; });
        assertEquals(report.estimates, cached.estimates);
        restored.espionage.removeSpymaster();
        var replacement = holder();
        restored.espionage.appoint(replacement, () -> { fail("Restart cannot reroll appointment"); return 0; });
        assertEquals(82, replacement.aptitude);
        assertNull(JsonUtil.GSON.fromJson("{\"id\":\"legacy\"}", FactionData.class).espionage);
    }
}
