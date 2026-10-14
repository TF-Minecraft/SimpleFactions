package net.tfminecraft.simplefactions.objects;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.economy.FrozenBalances;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.guild.Guild;

/** A location and a read-only view of the immutable guild incarnation's DenarEconomy treasury. */
public class Bank {
    private final Guild guild;
    private final double legacyBalance;
    private Chunk chunk;

    public Bank(Guild guild) {
        this(guild, 0, Bukkit.getWorld(Cache.worldName).getChunkAt(0, 0));
    }
    public Bank(Guild guild, Chunk chunk) { this(guild, 0, chunk); }
    public Bank(Guild guild, double amount, Chunk chunk) {
        this.guild = guild;
        this.legacyBalance = amount;
        this.chunk = chunk;
    }
    public String accountId() { return Denars.guild(guild.getIncarnationId()); }
    public Double getWealth() { return Money.denars(FrozenBalances.balance(accountId())); }
    /** Read only by the one-time, ledger-keyed legacy importer during startup. */
    public double legacyBalance() { return legacyBalance; }
    public Chunk getChunk() { return chunk; }
    public void setChunk(Chunk chunk) { this.chunk = chunk; }

    /** Removed in 4.0: every mutation needs balanced legs, a durable intent and a stable operation ID. */
    @Deprecated public void setWealth(Double amount) { throw new UnsupportedOperationException("Use a durable Denars operation"); }
    @Deprecated public void deposit(Double amount) { throw new UnsupportedOperationException("Use a durable Denars operation"); }
    @Deprecated public void withdraw(Double amount) { throw new UnsupportedOperationException("Use a durable Denars operation"); }
}
