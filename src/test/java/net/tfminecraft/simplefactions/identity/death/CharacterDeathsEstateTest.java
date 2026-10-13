package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** The replay guard's reading of the character, and the death guild answered from journals (E10, E12). */
class CharacterDeathsEstateTest {
    @TempDir Path folder;
    final CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();
    final UUID guild = UUID.randomUUID();
    final UUID realm = UUID.randomUUID();
    final Set<String> referenced = new HashSet<>();
    MockedStatic<Characters> characters;
    MockedConstruction<FactionDeaths> effects;

    @BeforeEach void open() throws Exception {
        characters = mockStatic(Characters.class);
        effects = mockConstruction(FactionDeaths.class, (adapter, context) -> {
            when(adapter.referencedCharacters()).thenAnswer(call -> referenced);
            when(adapter.apply(any(), any())).thenReturn(true);
        });
        CharacterDeaths.load(folder);
    }

    @AfterEach void close() {
        CharacterDeaths.stop();
        effects.close();
        characters.close();
    }

    void status(CharacterStatus status, UUID currentDeath) {
        characters.when(() -> Characters.info(ref.characterId())).thenReturn(Optional.of(
                new CharacterInfo(ref, "Name", status, null, Set.of(), 0, Instant.EPOCH)));
        characters.when(() -> Characters.deathId(ref.characterId())).thenReturn(Optional.ofNullable(currentDeath));
    }

    DeathJournal journal() {
        DeathJournal journal = new DeathJournal();
        journal.deathId = death;
        journal.ownerId = ref.ownerId();
        journal.characterId = ref.characterId();
        journal.at = 1000;
        return journal;
    }

    void write(DeathJournal journal) throws Exception {
        DurableJson.write(folder.resolve("deaths/" + journal.deathId + ".json"), journal);
    }

    static DeathJournal.Step rights(String realmValue, String... guilds) {
        JsonObject plan = new JsonObject();
        if (realmValue != null) plan.addProperty("realm", realmValue);
        JsonArray entries = new JsonArray();
        for (String incarnation : guilds) {
            JsonObject entry = new JsonObject();
            entry.addProperty("incarnation", incarnation);
            entries.add(entry);
        }
        plan.add("guilds", entries);
        return new DeathJournal.Step("rights:" + UUID.randomUUID(), "rights", plan);
    }

    @Test void guardReadsTheCharactersCurrentLife() {
        DeathJournal journal = journal();
        characters.when(() -> Characters.info(ref.characterId())).thenReturn(Optional.empty());
        assertEquals(DeathJournalStore.Replay.SKIP, CharacterDeaths.replayGuard(journal), "unreadable: wait");
        status(CharacterStatus.ALIVE, death);
        assertEquals(DeathJournalStore.Replay.CLOSE_REVIVE, CharacterDeaths.replayGuard(journal));
        status(CharacterStatus.DEAD, death);
        assertEquals(DeathJournalStore.Replay.REPLAY, CharacterDeaths.replayGuard(journal));
        status(CharacterStatus.DEAD, UUID.randomUUID());
        assertEquals(DeathJournalStore.Replay.CLOSE_REDEATH, CharacterDeaths.replayGuard(journal));
        status(CharacterStatus.DEAD, null);
        assertEquals(DeathJournalStore.Replay.SKIP, CharacterDeaths.replayGuard(journal), "no death identity: wait");
        status(CharacterStatus.MISSING, null);
        assertEquals(DeathJournalStore.Replay.SKIP, CharacterDeaths.replayGuard(journal), "staff decide MISSING characters");
    }

    @Test void reconcileClosesARevivedCharactersJournalWithoutTouchingItsRights() throws Exception {
        DeathJournal journal = journal();
        journal.steps.add(new DeathJournal.Step("rights:realm", "rights", new JsonObject()));
        journal.claims.add(new DeathJournal.Claim("rights:realm", new OwedPayment("wages", guild,
                ref.characterId(), 500, OwedPayment.Kind.WAGES)));
        write(journal);
        status(CharacterStatus.ALIVE, death);
        CharacterDeaths.reconcile();
        verify(effects.constructed().getFirst(), never()).apply(any(), any());
        DeathJournal closed = DurableJson.read(folder.resolve("deaths/" + death + ".json"), DeathJournal.class);
        assertEquals("revive", closed.closedBy);
        assertTrue(CharacterDeaths.pendingPayments().isEmpty());
    }

    @Test void journalRecordsTheGuildAndRealmAtDeath() throws Exception {
        DeathJournal journal = journal();
        journal.lockGuildIncarnation = guild;
        journal.lockGuildRealm = realm;
        write(journal);
        assertEquals(new CharacterDeaths.DeathGuild(CharacterDeaths.DeathGuild.Kind.GUILD, guild, realm),
                CharacterDeaths.deathGuild(ref.characterId(), death));
    }

    @Test void olderJournalsTakeTheRealmFromTheOneStoredPlanListingTheGuild() throws Exception {
        DeathJournal journal = journal();
        journal.lockGuildIncarnation = guild;
        journal.steps.add(new DeathJournal.Step("online", "online", new JsonObject()));
        journal.steps.add(rights(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        journal.steps.add(rights(realm.toString(), guild.toString()));
        JsonObject noGuilds = new JsonObject();
        noGuilds.addProperty("realm", UUID.randomUUID().toString());
        journal.steps.add(new DeathJournal.Step("rights:bare", "rights", noGuilds));
        write(journal);
        assertEquals(realm, CharacterDeaths.deathGuild(ref.characterId(), death).realm());

        journal.steps.add(rights(UUID.randomUUID().toString(), guild.toString()));
        assertNull(CharacterDeaths.storedRealm(journal, guild), "two realms list it: unknown");
        assertNull(CharacterDeaths.storedRealm(journal, UUID.randomUUID()), "no realm lists it: unknown");
        DeathJournal malformed = journal();
        malformed.steps.add(rights("not-a-uuid", guild.toString()));
        malformed.steps.add(rights(null, guild.toString()));
        assertNull(CharacterDeaths.storedRealm(malformed, guild));
        DeathJournal odd = journal();
        DeathJournal.Step step = rights(realm.toString(), guild.toString());
        step.plan.getAsJsonArray("guilds").add("not an object");
        odd.steps.add(step);
        assertEquals(realm, CharacterDeaths.storedRealm(odd, guild));
    }

    @Test void journalsBeforeTheGuildFieldDeriveTheGuildFromTheirRightsSnapshot() throws Exception {
        DeathJournal journal = journal();
        DeathJournal.Step step = rights(realm.toString(), guild.toString());
        step.plan.getAsJsonArray("guilds").get(0).getAsJsonObject().add("members", new JsonArray());
        step.plan.getAsJsonArray("guilds").get(0).getAsJsonObject().getAsJsonArray("members").add(ref.characterId().toString());
        journal.steps.add(step);
        write(journal);
        assertEquals(new CharacterDeaths.DeathGuild(CharacterDeaths.DeathGuild.Kind.GUILD, guild, realm),
                CharacterDeaths.deathGuild(ref.characterId(), death));
    }

    @Test void guildlessJournalAnswersNone() throws Exception {
        write(journal());
        assertEquals(CharacterDeaths.DeathGuild.Kind.NONE, CharacterDeaths.deathGuild(ref.characterId(), death).kind());
    }

    @Test void deathWithoutAJournalWaitsWhileTheCharacterIsStillReferenced() {
        referenced.add(Members.key(ref.characterId()));
        assertEquals(CharacterDeaths.DeathGuild.Kind.UNAVAILABLE, CharacterDeaths.deathGuild(ref.characterId(), death).kind());
        referenced.clear();
        assertEquals(CharacterDeaths.DeathGuild.Kind.NONE, CharacterDeaths.deathGuild(ref.characterId(), death).kind());
    }

    @Test void foreignUnreadableOrUnloadedJournalsAreUnavailable() throws Exception {
        DeathJournal journal = journal();
        journal.lockGuildIncarnation = guild;
        write(journal);
        assertEquals(CharacterDeaths.DeathGuild.Kind.UNAVAILABLE, CharacterDeaths.deathGuild(UUID.randomUUID(), death).kind());
        Files.writeString(folder.resolve("deaths/" + death + ".json"), "{}");
        assertEquals(CharacterDeaths.DeathGuild.Kind.UNAVAILABLE, CharacterDeaths.deathGuild(ref.characterId(), death).kind());
        CharacterDeaths.stop();
        assertEquals(CharacterDeaths.DeathGuild.Kind.UNAVAILABLE, CharacterDeaths.deathGuild(ref.characterId(), death).kind());
    }
}
