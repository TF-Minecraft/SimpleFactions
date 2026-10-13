package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LockGuildIndexTest {
    final UUID character = UUID.randomUUID();
    final String key = character.toString();
    final LockGuildIndex index = new LockGuildIndex();

    DeathJournal journal(long at, JsonObject... rights) {
        DeathJournal journal = new DeathJournal();
        journal.deathId = UUID.randomUUID();
        journal.ownerId = UUID.randomUUID();
        journal.characterId = character;
        journal.at = at;
        for (JsonObject plan : rights) journal.steps.add(new DeathJournal.Step("rights:" + UUID.randomUUID(), "rights", plan));
        journal.steps.add(new DeathJournal.Step("online", "online", new JsonObject()));
        return journal;
    }

    static JsonObject guild(UUID incarnation, String... members) {
        JsonObject guild = new JsonObject();
        guild.addProperty("incarnation", incarnation.toString());
        JsonArray list = new JsonArray();
        for (String member : members) list.add(member);
        guild.add("members", list);
        guild.add("invites", new JsonArray());
        return guild;
    }

    static JsonObject realm(JsonObject... guilds) {
        JsonObject realm = new JsonObject();
        realm.addProperty("realm", UUID.randomUUID().toString());
        JsonArray list = new JsonArray();
        for (JsonObject guild : guilds) list.add(guild);
        realm.add("guilds", list);
        return realm;
    }

    @Test void olderJournalsUseTheFirstGuildListingTheCharacterInSnapshotOrder() {
        UUID invitedOnly = UUID.randomUUID(), first = UUID.randomUUID(), later = UUID.randomUUID();
        JsonObject invited = guild(invitedOnly, "someone");
        invited.getAsJsonArray("invites").add(key);
        DeathJournal journal = journal(1, new JsonObject(),
                realm(invited, guild(first, "someone", key)), realm(guild(later, key)));
        index.rebuild(List.of(journal));
        assertEquals(Optional.of(first), index.get(character));
    }

    @Test void recordedIncarnationWinsOverTheSnapshot() {
        UUID recorded = UUID.randomUUID();
        DeathJournal journal = journal(1, realm(guild(UUID.randomUUID(), key)));
        journal.lockGuildIncarnation = recorded;
        index.rebuild(List.of(journal));
        assertEquals(Optional.of(recorded), index.get(character));
    }

    @Test void onlyTheMostRecentJournalPerCharacterCounts() {
        UUID guild = UUID.randomUUID();
        DeathJournal inGuild = journal(1, realm(guild(guild, key)));
        DeathJournal guildless = journal(2, realm(guild(UUID.randomUUID(), "someone")));
        index.rebuild(List.of(guildless, inGuild));
        assertTrue(index.get(character).isEmpty(), "a later guildless death leaves no entry");
        index.rebuild(List.of(journal(3, realm(guild(guild, key))), guildless));
        assertEquals(Optional.of(guild), index.get(character));
    }

    @Test void journalsAtTheSameMomentDeny() {
        UUID guild = UUID.randomUUID();
        index.rebuild(List.of(journal(1, realm(guild(UUID.randomUUID(), key))),
                journal(5, realm(guild(guild, key))), journal(5, realm(guild(guild, key)))));
        assertTrue(index.get(character).isEmpty());
    }

    @Test void rebuildReplacesEarlierEntriesAndCaptureKeepsThemCurrent() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        index.rebuild(List.of(journal(1, realm(guild(first, key)))));
        index.rebuild(List.of());
        assertTrue(index.get(character).isEmpty());
        DeathJournal captured = journal(2);
        captured.lockGuildIncarnation = second;
        index.capture(captured);
        assertEquals(Optional.of(second), index.get(character));
        index.capture(journal(3));
        assertTrue(index.get(character).isEmpty(), "a guildless capture deletes the entry");
    }

    @Test void malformedOldSnapshotsGiveNoEntryWhileWellFormedSiblingsStillIndex() {
        List<JsonObject> malformed = new ArrayList<>();
        JsonObject nullGuilds = realm();
        nullGuilds.add("guilds", JsonNull.INSTANCE);
        malformed.add(nullGuilds);
        JsonObject objectGuilds = realm();
        objectGuilds.add("guilds", new JsonObject());
        malformed.add(objectGuilds);
        JsonObject stringGuild = realm(guild(UUID.randomUUID(), key));
        stringGuild.getAsJsonArray("guilds").set(0, new JsonPrimitive("guild"));
        malformed.add(stringGuild);
        JsonObject nullMembers = guild(UUID.randomUUID(), key);
        nullMembers.add("members", JsonNull.INSTANCE);
        malformed.add(realm(nullMembers, guild(UUID.randomUUID(), key)));
        JsonObject missingMembers = guild(UUID.randomUUID(), key);
        missingMembers.remove("members");
        malformed.add(realm(missingMembers));
        JsonObject missingIncarnation = guild(UUID.randomUUID(), key);
        missingIncarnation.remove("incarnation");
        malformed.add(realm(missingIncarnation, guild(UUID.randomUUID(), key)));
        for (Object bad : new Object[] {JsonNull.INSTANCE, "", " ", "not-a-uuid", 7}) {
            JsonObject guild = guild(UUID.randomUUID(), key);
            if (bad instanceof String text) guild.addProperty("incarnation", text);
            else if (bad instanceof Integer number) guild.addProperty("incarnation", number);
            else guild.add("incarnation", JsonNull.INSTANCE);
            malformed.add(realm(guild));
        }
        UUID sibling = UUID.randomUUID(), siblingGuild = UUID.randomUUID();
        for (JsonObject snapshot : malformed) {
            DeathJournal broken = journal(1, snapshot);
            DeathJournal wellFormed = journal(1, realm(guild(siblingGuild, sibling.toString())));
            wellFormed.characterId = sibling;
            assertDoesNotThrow(() -> index.rebuild(List.of(broken, wellFormed)), snapshot::toString);
            assertTrue(index.get(character).isEmpty(), snapshot::toString);
            assertEquals(Optional.of(siblingGuild), index.get(sibling), snapshot::toString);
        }
    }
}
