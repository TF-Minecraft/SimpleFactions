package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuildCreatorIncomeLineTest {
    @Test
    void aRealmBranchNamesTheWholeRealm() {
        GuildCreator creator = new GuildCreator();
        String realm = creator.incomeChangeLine(12.5, true);
        String guild = creator.incomeChangeLine(12.5, false);
        String waiting = creator.incomeChangeLine(null, true);

        assertTrue(realm.contains("Estimated Realm Income Change"), realm);
        assertTrue(realm.contains("+12.50"), realm);
        assertTrue(guild.contains("Estimated Income Change"), guild);
        assertTrue(!guild.contains("Realm"), guild);
        assertTrue(waiting.contains("Estimated Realm Income Change"), waiting);
        assertTrue(waiting.contains("Calculating"), waiting);
    }
}
