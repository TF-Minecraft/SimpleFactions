package net.tfminecraft.simplefactions.map.export;

import com.google.gson.JsonParser;
import net.tfminecraft.simplefactions.identity.Members;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicFactionProjectionTest {
    @Test
    void projectsPublicFieldsWithoutPrivateKeysOrNestedAccounting() {
        String member = java.util.UUID.randomUUID().toString();
        var source = JsonParser.parseString("""
                {"id":"realm","name":"Realm","leader":"%s","banner":["blue",{"private":"value"}],
                "governmentData":{"votes":{"secret":"secret"}},"espionage":{"secret":"secret"},
                "guilds":[null,{"id":"guild","type":"trade","leader":"%s","incarnation":"secret",
                "members":["%s"],"balance":100,"loans":["secret"],"ledger last day":{"secret":5},
                "branches":[null,{"id":"market","level":2,"private":"secret"}]}]}
                """.formatted(member, member, member)).getAsJsonObject();
        var result = PublicFactionProjection.faction(source);
        assertEquals("Realm", result.get("name").getAsString());
        assertEquals(1, result.getAsJsonArray("banner").size());
        assertEquals(1, result.getAsJsonArray("members").size());
        assertEquals("Unknown", result.get("leader character").getAsString());
        assertEquals(result.get("leader"), result.get("leader character of"));
        assertEquals(1, result.getAsJsonArray("guilds").size());
        assertEquals("market", result.getAsJsonArray("guilds").get(0).getAsJsonObject()
                .getAsJsonArray("branches").get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(result.toString().contains("secret"));
        assertFalse(result.toString().contains(member));
        assertFalse(result.toString().contains("balance"));
        assertTrue(source.toString().contains("secret"));
    }

    @Test
    void missingOrMalformedPublicFieldsCannotCopyPrivateObjects() {
        var result = PublicFactionProjection.faction(JsonParser.parseString("""
                {"name":{"private":"secret"},"leader":null,"guilds":[{"members":{},"branches":{}}]}
                """).getAsJsonObject());
        assertFalse(result.has("name"));
        assertEquals("Unknown", result.get("leader").getAsString());
        assertEquals(0, result.getAsJsonArray("members").size());
        assertEquals(0, PublicFactionProjection.faction(new com.google.gson.JsonObject())
                .getAsJsonArray("guilds").size());
    }

    @Test
    void heldRealmShowsItsDeadLeaderMarkedDead() {
        var characters = net.tfminecraft.simplefactions.guild.TestCharacters.install();
        String dead = characters.character(java.util.UUID.randomUUID(), "Grim Hollow");
        var held = PublicFactionProjection.faction(JsonParser.parseString("""
                {"id":"realm","leader":"","guilds":[{"id":"other"},{"id":"realm","leader":"","dead leader":"%s"}]}
                """.formatted(dead)).getAsJsonObject());
        assertEquals("Grim Hollow (dead)", held.get("leader character").getAsString());
        var open = PublicFactionProjection.faction(JsonParser.parseString("""
                {"id":"realm","leader":"","guilds":[{"id":"other","dead leader":"%s"}]}
                """.formatted(dead)).getAsJsonObject());
        assertFalse(open.get("leader character").getAsString().contains("(dead)"));
    }

    @Test
    void emptySeatShowsTheDeadLeaderMarkedDead() {
        var characters = net.tfminecraft.simplefactions.guild.TestCharacters.install();
        String dead = characters.character(java.util.UUID.randomUUID(), "Grim Hollow");
        String living = characters.character(java.util.UUID.randomUUID(), "Kira Vale");
        var result = PublicFactionProjection.faction(JsonParser.parseString("""
                {"guilds":[{"id":"a","leader":"","dead leader":"%s"},{"id":"b","leader":"%s","dead leader":"%s"}]}
                """.formatted(dead, living, dead)).getAsJsonObject());
        var guilds = result.getAsJsonArray("guilds");
        assertEquals("Grim Hollow (dead)", guilds.get(0).getAsJsonObject().get("leader character").getAsString());
        assertEquals("Kira Vale", guilds.get(1).getAsJsonObject().get("leader character").getAsString());
        assertFalse(result.toString().contains(dead));
    }
}
