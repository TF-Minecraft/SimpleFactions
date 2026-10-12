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
}
