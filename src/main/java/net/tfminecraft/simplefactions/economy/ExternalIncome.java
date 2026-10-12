package net.tfminecraft.simplefactions.economy;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;

/** Replayable bookkeeping for money already posted by another plugin. */
public final class ExternalIncome {
    private ExternalIncome() {}

    public static void recover(Path folder) throws IOException {
        journal(folder).recover(ExternalIncome::apply);
    }

    public static boolean casino(Guild guild, String operation, double amount) {
        if (!EconomyRuntime.ready()) return false;
        try {
            Path folder = SimpleFactions.getInstance().getDataFolder().toPath();
            var journal = journal(folder);
            String id = "sf:casino:" + guild.getIncarnationId() + ":" + operation;
            var resumed = journal.resume(id, saved -> {
                if (Double.compare(saved.changes().get("amount").getAsDouble(), amount) != 0)
                    throw new IOException("Casino operation amount changed: " + operation);
                apply(saved);
            });
            Result result;
            if (resumed.isPresent()) result = resumed.get();
            else {
                var values = new JsonObject();
                values.addProperty("guild", guild.getId());
                values.addProperty("incarnation", guild.getIncarnation());
                values.addProperty("operation", operation);
                values.addProperty("amount", amount);
                values.addProperty("before", guild.getLedger().getCasinoProfit());
                values.addProperty("after", guild.getLedger().getCasinoProfit() + amount);
                result = journal.execute(new OperationJournal.Intent(id, "sf:casino-income", List.of(new Leg(Denars.MINT, 0)), values), ExternalIncome::apply);
            }
            if (result != Result.APPLIED || !Double.valueOf(amount).equals(guild.getLedger().getCasinoProfitOperations().get(operation)))
                throw new IOException("Casino bookkeeping remains incomplete: " + operation);
            return true;
        } catch (IOException | RuntimeException error) {
            EconomyRuntime.failClosed(error);
            return false;
        }
    }

    private static OperationJournal journal(Path folder) { return new OperationJournal(folder.resolve("external-income")); }

    private static void apply(OperationJournal.Intent intent) throws IOException {
        JsonObject values = intent.changes();
        Guild guild = FactionManager.getGuildByString(values.get("guild").getAsString());
        if (guild == null || !guild.getIncarnation().equals(values.get("incarnation").getAsString()))
            throw new IOException("Casino treasury disappeared or was replaced");
        String operation = values.get("operation").getAsString();
        double amount = values.get("amount").getAsDouble();
        var ledger = guild.getLedger();
        double originalProfit = ledger.getCasinoProfit();
        Map<String, Double> originalOperations = ledger.getCasinoProfitOperations();
        Map<String, Double> operations = new java.util.HashMap<>(originalOperations);
        Double known = operations.get(operation);
        double before = values.get("before").getAsDouble(), after = values.get("after").getAsDouble();
        if (known != null) {
            if (Double.compare(known, amount) != 0) throw new IOException("Casino operation disagrees with saved amount");
        } else {
            if (Double.compare(ledger.getCasinoProfit(), before) != 0 && Double.compare(ledger.getCasinoProfit(), after) != 0)
                throw new IOException("Casino income changed before unfinished bookkeeping recovered");
            operations.put(operation, amount);
            ledger.setCasinoProfit(after);
            ledger.restoreCasinoProfitOperations(operations);
        }
        if (!new Database().saveFactionDurably(guild.getFaction())) {
            ledger.setCasinoProfit(originalProfit);
            ledger.restoreCasinoProfitOperations(originalOperations);
            throw new IOException("Cannot save casino income");
        }
    }
}
