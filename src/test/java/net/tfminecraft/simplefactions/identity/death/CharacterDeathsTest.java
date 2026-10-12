package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CharacterDeathsTest {
    @TempDir Path folder;
    final CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();
    final Instant at = Instant.ofEpochMilli(1000);

    @AfterEach void close() { CharacterDeaths.stop(); }

    CharacterInfo info(CharacterRef who, CharacterStatus status) {
        return new CharacterInfo(who, "Name", status, null, Set.of(), 0, Instant.EPOCH);
    }

    DeathJournal journal() {
        DeathJournal journal = new DeathJournal();
        journal.deathId = death;
        journal.ownerId = ref.ownerId();
        journal.characterId = ref.characterId();
        journal.at = at.toEpochMilli();
        journal.steps.add(new DeathJournal.Step("rights", "rights", new JsonObject()));
        journal.claims.add(new DeathJournal.Claim("rights", new OwedPayment("wages", UUID.randomUUID(),
                ref.characterId(), 1500, OwedPayment.Kind.WAGES)));
        return journal;
    }

    @Test void unloadedConsumerFailsClosed() {
        CharacterDeaths.stop();
        assertThrows(IOException.class, CharacterDeaths::reconcile);
        assertThrows(UncheckedIOException.class, CharacterDeaths::pendingPayments);
        assertThrows(IOException.class, () -> CharacterDeaths.applyPayments("day", Map.of()));
        assertThrows(IOException.class, () -> CharacterDeaths.onDeath(ref, death, at));
    }

    @Test void startupFindsOfflineDeadRightsAndUsesProviderDeathIdentityAcrossReopen() throws Exception {
        try (var characters = mockStatic(Characters.class);
             var members = mockStatic(Members.class);
             var effects = mockConstruction(FactionDeaths.class, (adapter, context) -> {
                 when(adapter.referencedCharacters()).thenReturn(Set.of(ref.characterId().toString(), "living", "unknown"));
                 when(adapter.capture(eq(ref), eq(death), anyLong())).thenReturn(journal());
                 when(adapter.apply(any(), any())).thenReturn(true);
             })) {
            members.when(() -> Members.info(ref.characterId().toString())).thenReturn(Optional.of(info(ref, CharacterStatus.DEAD)));
            members.when(() -> Members.info("living")).thenReturn(Optional.of(info(ref, CharacterStatus.ALIVE)));
            characters.when(() -> Characters.deathId(ref.characterId())).thenReturn(Optional.of(death));
            CharacterDeaths.startup(folder);
            assertEquals(1500, CharacterDeaths.pendingPayments().getFirst().cents());
            CharacterDeaths.applyPayments("day:1", Map.of("wages", 500L));
            CharacterDeaths.stop();
            CharacterDeaths.startup(folder);
            CharacterDeaths.applyPayments("day:1", Map.of("wages", 500L));
            assertEquals(1000, CharacterDeaths.pendingPayments().getFirst().cents());
            verify(effects.constructed().getFirst()).capture(eq(ref), eq(death), anyLong());
            verify(effects.constructed().getLast(), never()).capture(any(), any(), anyLong());
            Files.writeString(folder.resolve("deaths/" + death + ".json"), "broken");
            assertThrows(UncheckedIOException.class, CharacterDeaths::pendingPayments);
            assertThrows(IOException.class, () -> CharacterDeaths.load(folder));
        }
    }

    @Test void missingAuthoritativeDeathIdentityStopsReconciliationBeforeCleanup() throws Exception {
        try (var characters = mockStatic(Characters.class);
             var members = mockStatic(Members.class);
             var effects = mockConstruction(FactionDeaths.class, (adapter, context) ->
                     when(adapter.referencedCharacters()).thenReturn(Set.of(ref.characterId().toString())))) {
            members.when(() -> Members.info(ref.characterId().toString())).thenReturn(Optional.of(info(ref, CharacterStatus.DEAD)));
            assertThrows(IOException.class, () -> CharacterDeaths.startup(folder));
            verify(effects.constructed().getFirst(), never()).capture(any(), any(), anyLong());
        }
    }

    @Test void committedEventsReplayOnceAndIgnoreForeignRevivedOrSupersededDeaths() throws Exception {
        try (var characters = mockStatic(Characters.class);
             var effects = mockConstruction(FactionDeaths.class, (adapter, context) -> {
                 when(adapter.capture(ref, death, at.toEpochMilli())).thenReturn(journal());
                 when(adapter.apply(any(), any())).thenReturn(true);
             })) {
            CharacterDeaths.load(folder);
            characters.when(() -> Characters.info(ref.characterId())).thenReturn(Optional.of(info(
                    new CharacterRef(UUID.randomUUID(), ref.characterId()), CharacterStatus.DEAD)));
            CharacterDeaths.onDeath(ref, death, at);
            characters.when(() -> Characters.info(ref.characterId())).thenReturn(Optional.of(info(ref, CharacterStatus.ALIVE)));
            CharacterDeaths.onDeath(ref, death, at);
            characters.when(() -> Characters.info(ref.characterId())).thenReturn(Optional.of(info(ref, CharacterStatus.DEAD)));
            characters.when(() -> Characters.deathId(ref.characterId())).thenReturn(Optional.of(UUID.randomUUID()));
            CharacterDeaths.onDeath(ref, death, at);
            verify(effects.constructed().getFirst(), never()).capture(any(), any(), anyLong());
            characters.when(() -> Characters.deathId(ref.characterId())).thenReturn(Optional.of(death));
            CharacterDeaths.onDeath(ref, death, at);
            characters.when(() -> Characters.info(ref.characterId())).thenReturn(Optional.empty());
            CharacterDeaths.onDeath(ref, death, at);
            verify(effects.constructed().getFirst()).capture(ref, death, at.toEpochMilli());
            assertEquals(1, CharacterDeaths.pendingPayments().size());
        }
    }
}
