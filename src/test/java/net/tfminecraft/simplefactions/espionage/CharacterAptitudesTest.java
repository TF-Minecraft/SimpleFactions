package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.database.JsonUtil;

class CharacterAptitudesTest {
    @TempDir Path directory;

    @Test
    void characterKeepsAptitudeAcrossFactionsDeletionAndServerRestart() throws Exception {
        Path file = directory.resolve("character-aptitudes.json");
        var registry = new CharacterAptitudes(file);
        assertEquals(88, registry.aptitude("character-a", () -> 88));
        Faction first = mock(Faction.class), second = mock(Faction.class);
        when(first.getEspionage()).thenReturn(new EspionageState());
        when(second.getEspionage()).thenReturn(new EspionageState());
        var assignment = new SpecialPositionAssignment();
        assignment.characterId = "character-a";
        first.getEspionage().appoint(assignment, registry.aptitude("character-a", () -> fail("Reroll")));
        first.getEspionage().removeSpymaster();
        var transferred = new SpecialPositionAssignment();
        transferred.characterId = "character-a";
        second.getEspionage().appoint(transferred, registry.aptitude("character-a", () -> fail("Faction swap reroll")));
        assertEquals(88, transferred.aptitude);
        var restarted = new CharacterAptitudes(file);
        restarted.load();
        assertEquals(88, restarted.aptitude("character-a", () -> fail("Restart reroll")));
        assertEquals(7, restarted.aptitude("character-b", () -> 7));
    }

    @Test
    void firstDevRollMigratesAndSurvivesAfterItsFactionDisappears() throws Exception {
        Faction old = mock(Faction.class);
        when(old.getId()).thenReturn("old");
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.characterId = "legacy-character";
        holder.playerId = UUID.randomUUID();
        state.appoint(holder, () -> 41);
        state.removeSpymaster();
        when(old.getEspionage()).thenReturn(state);
        Path file = directory.resolve("migrated.json");
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(FactionManager::getCopy).thenReturn(List.of(old));
            EspionageService.loadAptitudes(file);
            factions.when(FactionManager::getCopy).thenReturn(List.of());
            EspionageService.loadAptitudes(file);
        }
        var registry = new CharacterAptitudes(file);
        registry.load();
        assertEquals(41, registry.aptitude("legacy-character", () -> fail("Migration reroll")));
    }

    @Test
    void canonicalLookupKeepsUppercaseLegacyKeyAndRollAcrossSaveAndRestart() throws Exception {
        String canonical = "abcdefab-cdef-abcd-efab-cdefabcdefab";
        String legacy = canonical.toUpperCase(Locale.ROOT);
        Path file = directory.resolve("uppercase.json");
        Map<String, Integer> original = Map.of(legacy, 73, "legacy-character", 29);
        Files.writeString(file, JsonUtil.GSON.toJson(original));
        String originalJson = Files.readString(file);
        var registry = new CharacterAptitudes(file);
        registry.load();
        assertEquals(73, registry.aptitude(canonical, () -> fail("Canonical UUID rerolled")));
        registry.remember(canonical, 99);
        assertEquals(originalJson, Files.readString(file), "Lookup must not rename a persisted key");
        registry.save();
        assertEquals(original, saved(file));
        var restarted = new CharacterAptitudes(file);
        restarted.load();
        assertEquals(73, restarted.aptitude(canonical, () -> fail("Restart rerolled")));
        assertEquals(29, restarted.aptitude("legacy-character", () -> fail("Legacy key lost")));
    }

    @Test
    void rememberedRollReusesEitherUuidCasingWithoutAddingAnAlias() throws Exception {
        String lower = "abcdefab-cdef-abcd-efab-cdefabcdefab";
        String upper = lower.toUpperCase(Locale.ROOT);
        for (String first : List.of(lower, upper)) {
            String alias = first.equals(lower) ? upper : lower;
            Path file = directory.resolve(first + ".json");
            var registry = new CharacterAptitudes(file);
            registry.remember(first, 67);
            registry.remember(alias, 98);
            assertEquals(67, registry.aptitude(alias, () -> fail("Remembered UUID rerolled")));
            registry.save();
            assertEquals(Map.of(first, 67), saved(file));
        }
    }

    @Test
    void conflictingAliasesKeepExactValuesAndUseInsertionOrderForUnseenCasing() throws Exception {
        String lower = "abcdefab-cdef-abcd-efab-cdefabcdefab";
        String upper = lower.toUpperCase(Locale.ROOT);
        String mixed = "Abcdefab-cdef-abcd-efab-cdefabcdefab";
        Map<String, Integer> original = new LinkedHashMap<>();
        original.put(upper, 23);
        original.put(lower, 91);
        Path file = directory.resolve("conflict.json");
        Files.writeString(file, JsonUtil.GSON.toJson(original));
        var registry = new CharacterAptitudes(file);
        registry.load();
        assertEquals(91, registry.aptitude(lower, () -> fail("Exact key rerolled")));
        assertEquals(23, registry.aptitude(upper, () -> fail("Exact key rerolled")));
        assertEquals(23, registry.aptitude(mixed, () -> fail("Case alias rerolled")));
        registry.remember(mixed, 77);
        registry.save();
        assertEquals(original, saved(file));
        assertEquals(List.of(upper, lower), List.copyOf(saved(file).keySet()));
    }

    @Test
    void shorthandAndMalformedLegacyKeysStayDistinctFromCanonicalUuid() throws Exception {
        String shorthand = "1-1-1-1-1";
        String canonical = "00000001-0001-0001-0001-000000000001";
        assertEquals(UUID.fromString(canonical), UUID.fromString(shorthand));
        String malformed = "not-a-valid-character-uuid";
        for (boolean legacyFirst : List.of(true, false)) {
            Path file = directory.resolve("malformed-" + legacyFirst + ".json");
            var registry = new CharacterAptitudes(file);
            registry.remember(malformed, 15);
            registry.remember(legacyFirst ? shorthand : canonical, 31);
            assertEquals(76, registry.aptitude(legacyFirst ? canonical : shorthand, () -> 76));
            assertEquals(Map.of(malformed, 15, shorthand, legacyFirst ? 31 : 76,
                    canonical, legacyFirst ? 76 : 31), saved(file));
            var restarted = new CharacterAptitudes(file);
            restarted.load();
            assertEquals(15, restarted.aptitude(malformed, () -> fail("Malformed legacy key lost")));
            assertEquals(legacyFirst ? 31 : 76, restarted.aptitude(shorthand, () -> fail("Shorthand key lost")));
        }
    }

    @Test
    void throwingRollCreatesNeitherEntryNorFilesAndAllowsLaterFirstRoll() throws Exception {
        Path file = directory.resolve("new-registry/aptitudes.json");
        var registry = new CharacterAptitudes(file);
        String characterId = "abcdefab-cdef-abcd-efab-cdefabcdefab";
        var failure = new UncheckedIOException(new IOException("Character session changed"));
        assertSame(failure, assertThrows(UncheckedIOException.class,
                () -> registry.aptitude(characterId, () -> { throw failure; })));
        assertFalse(Files.exists(file.getParent()));
        String upper = characterId.toUpperCase(Locale.ROOT);
        assertEquals(46, registry.aptitude(upper, () -> 46));
        assertEquals(46, registry.aptitude(characterId, () -> fail("Retry created a case alias")));
        assertEquals(Map.of(upper, 46), saved(file));
    }

    @Test
    void throwingRollLeavesEarlierAptitudesAndTheirFileUntouched() throws Exception {
        Path file = directory.resolve("existing.json");
        var registry = new CharacterAptitudes(file);
        assertEquals(32, registry.aptitude("legacy-character", () -> 32));
        String before = Files.readString(file);
        assertThrows(IllegalStateException.class, () -> registry.aptitude(UUID.randomUUID().toString(),
                () -> { throw new IllegalStateException("Session ended during attributes lookup"); }));
        assertEquals(before, Files.readString(file));
        assertFalse(Files.exists(file.resolveSibling("existing.json.tmp")));
        registry.save();
        assertEquals(Map.of("legacy-character", 32), saved(file));
    }

    private Map<String, Integer> saved(Path file) throws IOException {
        return JsonUtil.GSON.fromJson(Files.readString(file), new TypeToken<Map<String, Integer>>() {}.getType());
    }
}
