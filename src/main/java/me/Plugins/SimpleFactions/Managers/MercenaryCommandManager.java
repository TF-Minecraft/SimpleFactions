package me.Plugins.SimpleFactions.Managers;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.DisplayNameGate;
import me.Plugins.SimpleFactions.Utils.DisplayNameGate.NameOperation;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanies;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanyService;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryInvites;
import me.Plugins.SimpleFactions.mercenary.contract.ContractBook;
import me.Plugins.SimpleFactions.mercenary.contract.ContractHandler;
import me.Plugins.SimpleFactions.mercenary.contract.ContractTerms;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryMarket;

public final class MercenaryCommandManager implements CommandExecutor {
    public static final String CMD = "company";
    /** The hiring side of the same feature, so both live in one executor. */
    public static final String MARKET_CMD = "mercenaries";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean market = command.getName().equalsIgnoreCase(MARKET_CMD);
        if (!market && !command.getName().equalsIgnoreCase(CMD)) {
            return false;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        if (market) {
            return market(p, args);
        }
        if (args.length == 0) {
            usage(p);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "found" -> found(p, args);
            case "invite" -> {
                if (args.length != 2) {
                    p.sendMessage("§cUsage: /company invite <player>");
                    return true;
                }
                MercenaryInvites.invite(p, args[1]);
            }
            case "accept" -> MercenaryInvites.accept(p);
            case "decline" -> MercenaryInvites.decline(p);
            case "kick" -> {
                if (args.length != 2) {
                    p.sendMessage("§cUsage: /company kick <player>");
                    return true;
                }
                report(p, MercenaryCompanyService.kick(leaderGuild(p), p.getName(), args[1]));
            }
            case "expand" -> report(p, MercenaryCompanyService.expand(leaderGuild(p), p.getName()));
            case "draft" -> draft(p);
            case "offer" -> {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /company offer <faction>");
                    return true;
                }
                offer(p, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
            case "contracts" -> contracts(p);
            case "admin" -> admin(p, args);
            default -> usage(p);
        }
        return true;
    }

    private void found(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /company found <name>");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (DisplayNameGate.check(p, NameOperation.COMPANY_FOUND, name)
                == DisplayNameGate.Result.NEEDS_CONFIRM) {
            return;
        }
        report(p, MercenaryCompanyService.requestFormation(leaderGuild(p), p.getName(), name));
    }

    /** {@code /mercenaries} opens the hiring hall; {@code list} prints it in chat. */
    private boolean market(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            List<MercenaryCompany> listing = MercenaryMarket.listing();
            if (listing.isEmpty()) {
                p.sendMessage("§7No mercenary companies are for hire.");
                return true;
            }
            p.sendMessage("§7Companies for hire, best reputation first:");
            for (MercenaryCompany company : listing) {
                p.sendMessage("§e" + company.getName()
                        + " §7- reputation " + company.getReputationString()
                        + " §7- §e" + MercenaryMarket.availableToday(company)
                        + "§7/§e" + company.getSlots() + " §7free"
                        + " §7- §e" + MercenaryMarket.homeSettlement(company));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("hire")) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            MercenaryCompany company = MercenaryMarket.byName(name);
            if (company == null) {
                p.sendMessage("§cNo company by that name is for hire.");
                return true;
            }
            report(p, MercenaryMarket.canSign(company, p));
            return true;
        }
        FactionManager.getInv().mercenaryMarketList(p);
        return true;
    }

    /** Hands out stage 1 of the negotiation book. */
    private void draft(Player p) {
        MercenaryCompany company = company(p);
        if (company == null || !company.isLeader(p.getName())) {
            p.sendMessage("§cOnly a company leader may draft a contract.");
            return;
        }
        p.getInventory().addItem(ContractBook.draftBook(company));
        p.sendMessage("§aDraft written. Fill in the terms page and sign it.");
    }

    /**
     * Turns the reviewed book in hand into a live offer for a named faction. The
     * faction is chosen here rather than in the book because the terms are the
     * company's price list, and the same price list may be offered to anyone.
     */
    private void offer(Player p, String factionName) {
        MercenaryCompany company = company(p);
        if (company == null || !company.isLeader(p.getName())) {
            p.sendMessage("§cOnly a company leader may offer a contract.");
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || !(held.getItemMeta() instanceof BookMeta meta)
                || ContractBook.stage(meta) == null) {
            p.sendMessage("§cHold a reviewed contract book to offer it.");
            return;
        }
        ContractTerms terms = ContractBook.parseTerms(meta);
        if (terms == null) {
            p.sendMessage("§cThe terms page could not be read.");
            return;
        }
        Faction hirer = resolveFaction(factionName);
        if (hirer == null) {
            p.sendMessage("§cNo faction by that name.");
            return;
        }
        ContractHandler.Offer offer = company.getContractHandler().offer(hirer, terms);
        report(p, offer.result());
        if (!offer.ok()) return;
        p.getInventory().setItemInMainHand(ContractBook.agreementBook(offer.contract()));
        p.sendMessage("§7Give this book to a member of "
                + hirer.getName() + "§7's government to sign.");
    }

    private void contracts(Player p) {
        Guild guild = leaderGuild(p);
        if (guild == null || guild.getCompany() == null) {
            p.sendMessage("§cYou are not in a company.");
            return;
        }
        FactionManager.getInv().contractListView(p, guild);
    }

    private void admin(Player p, String[] args) {
        if (!Permissions.isAdmin(p)) {
            p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
            return;
        }
        if (args.length < 3) {
            p.sendMessage("§cUsage: §e/company admin give|take <company> [amount]");
            return;
        }
        boolean give;
        if (args[1].equalsIgnoreCase("give")) {
            give = true;
        } else if (args[1].equalsIgnoreCase("take")) {
            give = false;
        } else {
            p.sendMessage("§cUsage: §e/company admin give|take <company> [amount]");
            return;
        }
        MercenaryCompany company = resolveCompany(args[2]);
        if (company == null) {
            p.sendMessage("§cNo company by that name.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                p.sendMessage("§cAmount must be a positive whole number.");
                return;
            }
            if (amount <= 0) {
                p.sendMessage("§cAmount must be a positive whole number.");
                return;
            }
        }
        report(p, company.adminAdjustSlots(give ? amount : -amount));
    }

    /** Players type the display name; ids are only ever seen by the database. */
    static Faction resolveFaction(String nameOrId) {
        Faction byId = FactionManager.getByString(nameOrId);
        if (byId != null) return byId;
        for (Faction f : FactionManager.factions) {
            if (f.getName() != null && f.getName().equalsIgnoreCase(nameOrId)) return f;
        }
        return null;
    }

    private static MercenaryCompany resolveCompany(String nameOrId) {
        MercenaryCompany company = MercenaryCompanies.findByName(nameOrId);
        if (company != null) {
            return company;
        }
        Guild guild = FactionManager.getGuildByString(nameOrId);
        return guild == null ? null : guild.getCompany();
    }

    private static MercenaryCompany company(Player p) {
        Guild guild = leaderGuild(p);
        return guild == null ? null : guild.getCompany();
    }

    private static Guild leaderGuild(Player p) {
        Guild guild = FactionManager.getGuildByLeader(p.getName());
        return guild != null ? guild : FactionManager.getGuildByMember(p.getName());
    }

    private static void report(Player p, MercenaryResult result) {
        p.sendMessage((result.ok() ? "§a" : "§c") + result.message());
    }

    private static void usage(Player p) {
        p.sendMessage("§7/company found <name> §8- §7hire a company charter");
        p.sendMessage("§7/company invite <player> §8- §7offer a slot");
        p.sendMessage("§7/company accept §8- §7sign on");
        p.sendMessage("§7/company decline §8- §7turn down an offer");
        p.sendMessage("§7/company kick <player> §8- §7dismiss a mercenary");
        p.sendMessage("§7/company expand §8- §7queue another slot");
        p.sendMessage("§7/company draft §8- §7write a contract book");
        p.sendMessage("§7/company offer <faction> §8- §7send the reviewed book as an offer");
        p.sendMessage("§7/company contracts §8- §7open the contract ledger");
        p.sendMessage("§7/mercenaries §8- §7browse the companies for hire");
        p.sendMessage("§7/mercenaries list §8- §7the same list in chat");
        p.sendMessage("§7/mercenaries hire <company> §8- §7check whether you may sign here");
    }
}
