package me.Plugins.SimpleFactions.mercenary.contract;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.mercenary.company.CompanyFixture;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanyService;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryEligibility;

/** A formed company and a faction willing to hire it, without a server. */
public final class ContractFixture {
    public static final long DAY = 24L * 60L * 60L * 1000L;

    public final CompanyFixture host;
    public final MercenaryCompany company;
    public final Faction hirer;

    private ContractFixture(CompanyFixture host, MercenaryCompany company, Faction hirer) {
        this.host = host;
        this.company = company;
        this.hirer = hirer;
    }

    /** Installs config, prototypes, a formed company with the requested slots, and a hirer. */
    public static ContractFixture formed(int slots) {
        installConfig();
        CompanyFixture.installMercenaryPrototype();
        CompanyFixture.installCompanyUpgrades();

        CompanyFixture host = new CompanyFixture(10000);
        MercenaryCompanyService.requestFormation(host.guild, host.leader(), "Hired Blades");
        MercenaryCompany company = host.company();
        for (int i = 0; i < Cache.mercenaryFormationSeconds; i++) {
            company.tick();
        }
        // Formation grants one slot; the rest are filled and grown so expansion is legal.
        while (company.getSlots() < slots) {
            for (int i = 0; i < company.getSlots(); i++) {
                company.enlist("Soldier" + i);
            }
            company.enqueueExpansion();
            for (int i = 0; i < Cache.mercenaryFormationSeconds; i++) {
                company.tick();
            }
        }

        Faction hirer = faction("brume");
        register(host.guild, hirer);
        return new ContractFixture(host, company, hirer);
    }

    public static void installConfig() {
        Cache.mercenaryFormationCost = 100.0;
        Cache.mercenaryFormationSeconds = 86400;
        Cache.mercenarySlotUpkeep = 8.0;
        Cache.mercenaryMinPricePerBattle = 50.0;
        Cache.mercenaryMinPricePerDay = 10.0;
        Cache.mercenaryMaxContractDays = 14;
        Cache.mercenaryDefaultBreachRefund = 500.0;
    }

    public static Faction faction(String id) {
        Faction f = mock(Faction.class);
        when(f.getId()).thenReturn(id);
        when(f.getName()).thenReturn(id);
        // FactionManager.getByLeader walks every faction, so a null leader is a landmine.
        when(f.getLeader()).thenReturn(id + "_ruler");
        return f;
    }

    /** Puts the host guild and any extra factions behind FactionManager for lookups. */
    public static void register(Guild hostGuild, Faction... extras) {
        GuildHandler handler = mock(GuildHandler.class);
        when(handler.getGuilds()).thenReturn(new ArrayList<>(List.of(hostGuild)));
        Faction hostFaction = faction("hired_blades_realm");
        when(hostFaction.getName()).thenReturn("Hired Blades Realm");
        when(hostFaction.getGuildHandler()).thenReturn(handler);
        when(hostGuild.getFaction()).thenReturn(hostFaction);

        GuildHandler empty = mock(GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new ArrayList<>());

        FactionManager.factions.clear();
        FactionManager.factions.add(hostFaction);
        for (Faction extra : extras) {
            when(extra.getGuildHandler()).thenReturn(empty);
            FactionManager.factions.add(extra);
        }
    }

    public static void tearDown() {
        FactionManager.factions.clear();
        CompanyFixture.clearCompanyUpgrades();
        CompanyFixture.clearRegiments();
        MercenaryEligibility.reset();
    }

    /** Terms that pass every rule, for tests that want to vary one figure at a time. */
    public static ContractTerms validTerms(int slots) {
        return new ContractTerms(slots, 50.0, 10.0, 7, 50.0, 500.0);
    }

    public MercenaryContract offer(ContractTerms terms, long from) {
        MercenaryContract contract =
                new MercenaryContract(company, hirer, ContractKind.MERCENARY, terms, from);
        company.getContractHandler().add(contract);
        return contract;
    }
}
