package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.REST.RestServer;
import me.Plugins.SimpleFactions.Utils.HomeSettlementNames;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * Who is for hire and who may hire them.
 *
 * <p>Signing is deliberately local. A company keeps a hall in its home settlement
 * and business is done there, which means a besieged or blockaded company is
 * genuinely harder to reach - a war-relevant consequence rather than a menu click.
 */
public final class MercenaryMarket {
    private MercenaryMarket() {
    }

    /* =====================================================
     * Listing
     * ===================================================== */

    /** Formed companies, best track record first, so reputation is worth having. */
    public static List<MercenaryCompany> listing() {
        List<MercenaryCompany> companies = new ArrayList<>();
        for (Guild g : FactionManager.getAllGuilds()) {
            if (g == null) continue;
            MercenaryCompany company = g.getCompany();
            if (company == null || !company.isFormed()) continue;
            companies.add(company);
        }
        return sorted(companies);
    }

    /** Split out so the ordering can be tested without a populated world. */
    public static List<MercenaryCompany> sorted(List<MercenaryCompany> companies) {
        List<MercenaryCompany> sorted = new ArrayList<>(companies);
        sorted.sort(Comparator
                .comparingInt(MercenaryCompany::getReputation).reversed()
                .thenComparing(c -> c.getName() == null ? "" : c.getName(),
                        String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /** What the market screen may honestly advertise for the coming day. */
    public static int availableToday(MercenaryCompany company) {
        long now = System.currentTimeMillis();
        return SlotReservations.remaining(company, now, now + 86400000L);
    }

    public static String homeSettlement(MercenaryCompany company) {
        return company == null ? "None" : HomeSettlementNames.of(company.getGuild());
    }

    /* =====================================================
     * Signing gates
     * ===================================================== */

    /**
     * Both gates in one answer: the signer must be standing in the company's home
     * province, and must be in their own faction's government.
     */
    public static MercenaryResult canSign(MercenaryCompany company, Player signer) {
        if (company == null || !company.isFormed()) {
            return MercenaryResult.deny("That company is not open for hire.");
        }
        if (signer == null) {
            return MercenaryResult.deny("Only a player may sign a contract.");
        }
        MercenaryResult local = isLocal(company, RestServer.getProvince(signer));
        if (!local.ok()) return local;
        return hasAuthority(company, signer.getName());
    }

    /**
     * Province equality is the whole range check: a settlement occupies exactly one
     * province ({@code SettlementHandler.rebuildIndex} normalises every settlement to
     * its {@code centerProvince}), so being in the province is being in the town.
     *
     * @param provinceAtSigner as {@link RestServer#getProvince}: -2 unresolved, <= 0 none
     */
    public static MercenaryResult isLocal(MercenaryCompany company, int provinceAtSigner) {
        Guild host = company.getGuild();
        if (host == null || !host.hasCapital() || host.getCapital() <= 0) {
            return MercenaryResult.deny(
                    company.getName() + " has no hall to sign a contract in.");
        }
        if (provinceAtSigner == -2) {
            return MercenaryResult.deny("Could not resolve your province.");
        }
        if (provinceAtSigner <= 0) {
            return MercenaryResult.deny("This location has no province.");
        }
        if (provinceAtSigner != host.getCapital()) {
            return MercenaryResult.deny("You must be in " + homeSettlement(company)
                    + " to sign with " + company.getName() + ".");
        }
        return MercenaryResult.ok("You are at the company hall.");
    }

    /**
     * Any government member may sign, not the leader alone, so a hemmed-in ruler is
     * not a single point of failure for a realm that needs swords.
     */
    public static MercenaryResult hasAuthority(MercenaryCompany company, String signer) {
        Faction faction = factionOf(signer);
        if (faction == null) {
            return MercenaryResult.deny("You are not in a faction.");
        }
        if (faction.getGovernment() == null
                || !faction.getGovernment().isCouncilMember(signer)) {
            return MercenaryResult.deny(
                    "Only a member of your government may hire a mercenary company.");
        }
        MercenaryResult loyal = MercenaryLoyalty.canServe(company, faction);
        if (!loyal.ok()) return loyal;
        return MercenaryResult.ok("You may hire " + company.getName() + ".");
    }

    public static Faction factionOf(String player) {
        Faction byLeader = FactionManager.getByLeader(player);
        if (byLeader != null) return byLeader;
        return FactionManager.getByMember(player);
    }

    public static MercenaryCompany byName(String name) {
        if (name == null) return null;
        for (MercenaryCompany company : listing()) {
            if (company.getName() != null
                    && Formatter.formatId(company.getName()).equalsIgnoreCase(Formatter.formatId(name))) {
                return company;
            }
        }
        return null;
    }
}
