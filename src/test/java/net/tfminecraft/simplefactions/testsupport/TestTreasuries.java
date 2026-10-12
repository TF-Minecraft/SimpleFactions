package net.tfminecraft.simplefactions.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.denareconomy.ledger.Ledger;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.objects.Bank;

/** Real isolated SQLite ledger for domain tests; production has no in-memory treasury fallback. */
public final class TestTreasuries implements AutoCloseable {
    private final Ledger previous = DenarEconomy.getLedger();
    private final Path directory;
    private final Ledger ledger;
    public TestTreasuries() {
        try {
            directory = Files.createTempDirectory("sf-test-money-");
        } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        org.slf4j.jul.JDK14LoggerFactory.LOGGER = Logger.getAnonymousLogger();
        ledger = Ledger.open(directory.resolve("ledger.db").toFile(), Logger.getAnonymousLogger(), account -> false);
        DenarEconomy.setLedger(ledger);
    }
    public static void set(Bank bank, double amount) {
        set(bank.accountId(), Money.cents(amount));
        for (var guild : net.tfminecraft.simplefactions.managers.FactionManager.getAllGuilds()) {
            if (bank.accountId().equals("guild:" + guild.getIncarnationId())) guild.updateWealth();
        }
    }
    public static void add(Bank bank, double amount) { set(bank, bank.getWealth() + amount); }
    public static void set(String account, long cents) {
        long change = Math.subtractExact(cents, Denars.balance(account));
        Result result = Denars.post("test:" + UUID.randomUUID(), "test:opening-balance",
                List.of(new Leg(Denars.MINT, -change), new Leg(account, change)));
        if (result != Result.APPLIED) throw new AssertionError("Test funding failed: " + result);
    }
    @Override public void close() {
        ledger.close();
        DenarEconomy.setLedger(previous);
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
