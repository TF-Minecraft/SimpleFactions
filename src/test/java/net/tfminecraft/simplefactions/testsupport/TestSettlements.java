package net.tfminecraft.simplefactions.testsupport;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.economy.settlement.*;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real upkeep planner and durable after-value applier against the test SQLite ledger. */
public final class TestSettlements {
    private TestSettlements() {}
    public static void upkeep(Faction faction) {
        try {
            var planned = FactionUpkeepPlan.calculate(faction);
            var changes = new RealmSettlementChanges();
            changes.upkeep.add(planned);
            var plan = new SettlementJournal.Plan(0,
                    List.of(new Leg(Denars.guild(SettlementGuilds.capital(faction).getIncarnationId()), -planned.charge()),
                            new Leg(Denars.MINT, planned.charge())), JsonUtil.GSON.toJsonTree(changes).getAsJsonObject());
            var directory = Files.createTempDirectory("sf-upkeep-effects-");
            var database = mock(Database.class);
            when(database.saveFactionDurably(any())).thenReturn(true);
            try {
                assertEquals(Result.APPLIED, Denars.post("test:upkeep:" + UUID.randomUUID(), "test:upkeep", plan.legs()));
                new RealmSettlementEffects(directory, database, new PlayerEconomyManager(), (id, paid) -> {}, values -> {}).apply(plan);
            } finally {
                try (var files = Files.walk(directory)) {
                    for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
                }
            }
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
