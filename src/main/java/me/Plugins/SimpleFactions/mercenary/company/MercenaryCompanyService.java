package me.Plugins.SimpleFactions.mercenary.company;

import java.util.Collection;
import java.util.Locale;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

/**
 * Every entry point into a mercenary company, so the command layer and the GUI
 * share one set of rules and one set of refusal messages.
 */
public final class MercenaryCompanyService {
    private MercenaryCompanyService() {
    }

    /* =====================================================
     * Formation
     * ===================================================== */

    public static MercenaryResult requestFormation(Guild guild, String actor, String name) {
        if (guild == null) {
            return MercenaryResult.deny("You are not in a guild.");
        }
        if (!isGuildLeader(guild, actor)) {
            return MercenaryResult.deny("Only the guild leader can found a mercenary company.");
        }
        if (guild.hasCompany()) {
            return MercenaryResult.deny("Your guild already has a mercenary company.");
        }
        if (guild.isFoundingCompany()) {
            return MercenaryResult.deny("Your guild is already founding a mercenary company.");
        }
        if (!MercenaryEligibility.canCreate(actor)) {
            return MercenaryResult.deny("You are not the sort to run a mercenary company.");
        }
        if (name == null || name.isBlank()) {
            return MercenaryResult.deny("Give the company a name.");
        }
        Regiment regiment = MercenaryCompany.cloneMercenaryRegiment();
        if (regiment == null) {
            return MercenaryResult.deny("Mercenary companies are not configured on this server.");
        }
        double cost = Cache.mercenaryFormationCost;
        Bank bank = guild.getBank();
        if (bank == null) {
            return MercenaryResult.deny("Your guild needs a bank before it can found a company.");
        }
        if (bank.getWealth() == null || bank.getWealth() < cost) {
            return MercenaryResult.deny("Your guild bank needs " + money(cost) + " to found a company.");
        }
        bank.withdraw(cost);
        guild.setCompany(new MercenaryCompany(
                guild,
                StringFormatter.formatHex(Formatter.formatName(name.trim())),
                regiment,
                Cache.mercenaryFormationSeconds));
        return MercenaryResult.ok("Your company will be ready in "
                + hours(Cache.mercenaryFormationSeconds) + ".");
    }

    /* =====================================================
     * Enlistment
     * ===================================================== */

    public static MercenaryResult canInvite(Guild guild, String actor, String target) {
        if (guild == null || guild.getCompany() == null) {
            return MercenaryResult.deny("Your guild has no mercenary company.");
        }
        MercenaryCompany company = guild.getCompany();
        if (!company.isLeader(actor)) {
            return MercenaryResult.deny("Only the guild leader can enlist mercenaries.");
        }
        if (company.isForming()) {
            return MercenaryResult.deny("Your company is still being founded.");
        }
        if (company.isEnlisted(target)) {
            return MercenaryResult.deny(target + " already serves in your company.");
        }
        if (!company.hasFreeSlot()) {
            return MercenaryResult.deny("Every slot is already filled.");
        }
        return MercenaryResult.ok("Invite sent to " + target + ".");
    }

    public static MercenaryResult canJoin(MercenaryCompany company, String player) {
        return canJoin(company, player, FactionManager.getAllGuilds());
    }

    public static MercenaryResult canJoin(
            MercenaryCompany company, String player, Collection<Guild> guilds) {
        if (company == null) {
            return MercenaryResult.deny("That company no longer exists.");
        }
        if (company.isForming()) {
            return MercenaryResult.deny("That company is still being founded.");
        }
        if (!MercenaryEligibility.canJoin(player)) {
            return MercenaryResult.deny("You are not the sort to take mercenary work.");
        }
        MercenaryCompany existing = MercenaryCompanies.findByMember(player, guilds);
        if (existing == company) {
            return MercenaryResult.deny("You already serve in that company.");
        }
        if (existing != null) {
            return MercenaryResult.deny("You already serve in " + existing.getName() + ".");
        }
        if (!company.hasFreeSlot()) {
            return MercenaryResult.deny("That company has no free slot.");
        }
        return MercenaryResult.ok("You joined " + company.getName() + ".");
    }

    public static MercenaryResult join(MercenaryCompany company, String player) {
        return join(company, player, FactionManager.getAllGuilds());
    }

    public static MercenaryResult join(
            MercenaryCompany company, String player, Collection<Guild> guilds) {
        MercenaryResult result = canJoin(company, player, guilds);
        if (!result.ok()) return result;
        company.enlist(player);
        return result;
    }

    public static MercenaryResult kick(Guild guild, String actor, String player) {
        if (guild == null || guild.getCompany() == null) {
            return MercenaryResult.deny("Your guild has no mercenary company.");
        }
        MercenaryCompany company = guild.getCompany();
        if (!company.isLeader(actor)) {
            return MercenaryResult.deny("Only the guild leader can dismiss mercenaries.");
        }
        if (!company.kick(player)) {
            return MercenaryResult.deny(player + " does not serve in your company.");
        }
        return MercenaryResult.ok(player + " was dismissed from the company.");
    }

    /* =====================================================
     * Slots and upgrades
     * ===================================================== */

    public static MercenaryResult expand(Guild guild, String actor) {
        if (guild == null || guild.getCompany() == null) {
            return MercenaryResult.deny("Your guild has no mercenary company.");
        }
        MercenaryCompany company = guild.getCompany();
        if (!company.isLeader(actor)) {
            return MercenaryResult.deny("Only the guild leader can expand the company.");
        }
        return company.enqueueExpansion();
    }

    public static MercenaryResult upgrade(Guild guild, String actor, String upgradeId) {
        if (guild == null || guild.getCompany() == null) {
            return MercenaryResult.deny("Your guild has no mercenary company.");
        }
        MercenaryCompany company = guild.getCompany();
        if (!company.isLeader(actor)) {
            return MercenaryResult.deny("Only the guild leader can order company upgrades.");
        }
        Upgrade upgrade = company.getUpgrade(upgradeId);
        if (upgrade == null) {
            return MercenaryResult.deny("Unknown company upgrade.");
        }
        if (upgrade.isMaxed()) {
            return MercenaryResult.deny(upgrade.getName() + " is already at its maximum level.");
        }
        if (!company.enqueueUpgrade(upgrade)) {
            return MercenaryResult.deny("The upgrade queue is full.");
        }
        return MercenaryResult.ok(upgrade.getName() + " queued.");
    }

    private static boolean isGuildLeader(Guild guild, String player) {
        String leader = guild.getLeader();
        return leader != null && player != null && leader.equalsIgnoreCase(player);
    }

    private static String money(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount) + "d";
    }

    private static String hours(int seconds) {
        int hours = Math.max(1, seconds / 3600);
        return hours + (hours == 1 ? " hour" : " hours");
    }
}
