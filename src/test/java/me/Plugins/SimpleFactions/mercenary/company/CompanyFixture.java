package me.Plugins.SimpleFactions.mercenary.company;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Loaders.CompanyUpgradeLoader;
import me.Plugins.SimpleFactions.Loaders.RegimentLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;

/**
 * Stand-ins for the Bukkit-backed objects a company hangs off, so company rules
 * can be tested without a server.
 */
public final class CompanyFixture {
    public final Guild guild = mock(Guild.class);
    public final Bank bank = mock(Bank.class);
    private final AtomicReference<MercenaryCompany> company = new AtomicReference<>();
    private final AtomicReference<String> leader = new AtomicReference<>("Ivar");
    private double balance;

    public CompanyFixture(double balance) {
        this.balance = balance;
        when(guild.getId()).thenReturn("hired_blades");
        when(guild.getLeader()).thenAnswer(i -> leader.get());
        when(guild.getBannerPatterns()).thenReturn(new ArrayList<>(List.of("white.base")));
        when(guild.getBank()).thenReturn(bank);
        when(guild.getCompany()).thenAnswer(i -> company.get());
        when(guild.hasCompany()).thenAnswer(i -> company.get() != null && company.get().isFormed());
        when(guild.isFoundingCompany())
                .thenAnswer(i -> company.get() != null && company.get().isForming());
        doAnswer(i -> {
            company.set(i.getArgument(0));
            return null;
        }).when(guild).setCompany(any());

        when(bank.getWealth()).thenAnswer(i -> balance());
        doAnswer(i -> {
            this.balance -= (double) (Double) i.getArgument(0);
            return null;
        }).when(bank).withdraw(anyDouble());
        doAnswer(i -> {
            this.balance += (double) (Double) i.getArgument(0);
            return null;
        }).when(bank).deposit(anyDouble());
    }

    public double balance() {
        return balance;
    }

    public String leader() {
        return leader.get();
    }

    public void setLeader(String name) {
        leader.set(name);
    }

    public MercenaryCompany company() {
        return company.get();
    }

    /** Puts guilds behind FactionManager, for the server-wide company lookups. */
    public static void registerGlobally(Guild... guilds) {
        GuildHandler handler = mock(GuildHandler.class);
        when(handler.getGuilds()).thenReturn(new ArrayList<>(List.of(guilds)));
        Faction faction = mock(Faction.class);
        when(faction.getGuildHandler()).thenReturn(handler);
        FactionManager.factions.clear();
        FactionManager.factions.add(faction);
    }

    public static void clearGlobalGuilds() {
        FactionManager.factions.clear();
    }

    /** Registers a mercenary prototype so the loader lookup finds one. */
    public static void installMercenaryPrototype() {
        RegimentLoader.oList.clear();
        RegimentLoader.oList.add(prototype());
    }

    public static void clearRegiments() {
        RegimentLoader.oList.clear();
    }

    /** A plain faction regiment prototype, for checking the mercenary type is skipped. */
    public static Regiment regularPrototype(String id, int slots, double upkeep) {
        ItemStack icon = mock(ItemStack.class);
        when(icon.clone()).thenReturn(icon);
        Regiment r = mock(Regiment.class);
        when(r.getId()).thenReturn(id);
        when(r.getName()).thenReturn(id);
        when(r.getIcon()).thenReturn(icon);
        when(r.getDescription()).thenReturn(new ArrayList<>());
        when(r.getCurrentSlots()).thenReturn(slots);
        when(r.getUpkeep()).thenReturn(upkeep);
        when(r.getExpansionTime()).thenReturn(10);
        when(r.isOffensive()).thenReturn(true);
        return r;
    }

    public static Regiment prototype() {
        ItemStack icon = mock(ItemStack.class);
        when(icon.clone()).thenReturn(icon);
        Regiment r = mock(Regiment.class);
        when(r.getId()).thenReturn("mercenary");
        when(r.getName()).thenReturn("Mercenary Company");
        when(r.getIcon()).thenReturn(icon);
        when(r.getDescription()).thenReturn(new ArrayList<>());
        when(r.getUpkeep()).thenReturn(8.0);
        when(r.getExpansionTime()).thenReturn(86400);
        when(r.isMercenary()).thenReturn(true);
        when(r.isOffensive()).thenReturn(true);
        return r;
    }

    /** A real regiment cloned from the prototype, as a company holds. */
    public static Regiment companyRegiment() {
        return new Regiment(prototype());
    }

    /** The three shipped company upgrades, without touching guild upgrades. */
    public static void installCompanyUpgrades() {
        CompanyUpgradeLoader.get().clear();
        CompanyUpgradeLoader.get().put("company_health",
                upgrade("company_health", "max_health 0 0.5", 10));
        CompanyUpgradeLoader.get().put("company_mana",
                upgrade("company_mana", "max_mana 0 1", 10));
        CompanyUpgradeLoader.get().put("company_mana_regen",
                upgrade("company_mana_regen", "mana_regen 0 0.1", 10));
    }

    public static void clearCompanyUpgrades() {
        CompanyUpgradeLoader.get().clear();
    }

    public static Upgrade upgrade(String id, String modifier, int maxLevel) {
        MemoryConfiguration root = new MemoryConfiguration();
        ConfigurationSection section = root.createSection(id);
        section.set("name", "#b7aae3" + id);
        section.set("icon", "writable_book.0");
        section.set("upkeep", 10.0);
        section.set("expansion-time", 4);
        if (maxLevel > 0) section.set("max-level", maxLevel);
        section.set("modifiers", List.of(modifier));
        return new Upgrade(id, section);
    }
}
