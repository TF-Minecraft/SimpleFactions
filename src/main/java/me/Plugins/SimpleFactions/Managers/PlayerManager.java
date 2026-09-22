package me.Plugins.SimpleFactions.Managers;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Guild.loans.LoanBook;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.FactionCleanup;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.contract.ContractBook;
import me.Plugins.SimpleFactions.mercenary.contract.ContractTerms;
import me.Plugins.SimpleFactions.mercenary.contract.ContractValidator;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;
import me.Plugins.SimpleFactions.player.PlayerEconomyManager;
import me.Plugins.SimpleFactions.player.income.PlayerCashflow;
import me.Plugins.SimpleFactions.player.income.PlayerLedger;
import net.tfminecraft.DenarEconomy.DenarEconomy;
import net.tfminecraft.DenarEconomy.Enum.Accounts;
import net.tfminecraft.DenarEconomy.Item.Coin;
import net.tfminecraft.DenarEconomy.event.PlayerBankPulseEvent;
import net.tfminecraft.DenarEconomy.event.PlayerDepositMaterialsEvent;
import net.tfminecraft.DenarEconomy.event.PlayerEarnMoneyEvent;

public class PlayerManager implements Listener{
    @EventHandler
    public void joinEvent(PlayerJoinEvent e) {
        FactionCleanup.ping(e.getPlayer().getName());
    }

    //Loans and stuff
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void signBook(PlayerEditBookEvent e) {
        if(!e.isSigning()) return;
        BookMeta meta = e.getPreviousBookMeta();
        BookMeta newMeta = e.getNewBookMeta();
        Player p = e.getPlayer();
        String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
        if(id == null) return;
        Guild issuer = FactionManager.getGuildByString(id);
        if(issuer == null) return;
        Integer contractStage = ContractBook.stage(meta);
        if(contractStage != null) {
            signContractBook(e, p, issuer, contractStage, newMeta);
            return;
        }
        Integer stage = meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER);
        if(stage == null) return;
        if(stage == 1) {
            e.setCancelled(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    Long time = newMeta.getPersistentDataContainer().get(Keys.LONG, PersistentDataType.LONG);
                    if(time != null && time < System.currentTimeMillis()) {
                        p.sendMessage("§cThis loan contract has expired! Unable to process.");
                        p.getInventory().setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK));
                        return;
                    }
                    p.getInventory().setItemInMainHand(LoanBook.getEstimatedBook(LoanBook.createLoanFromBook(newMeta, null)));
                }
            }.runTaskLater(SimpleFactions.plugin, 1L);
        }
        else if(stage == 2) {
            e.setCancelled(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    Long time = newMeta.getPersistentDataContainer().get(Keys.LONG, PersistentDataType.LONG);
                    if(time != null && time < System.currentTimeMillis()) {
                        p.sendMessage("§cThis loan contract has expired! Unable to process.");
                        p.getInventory().setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK));
                        return;
                    }
                    p.getInventory().setItemInMainHand(LoanBook.getLoanBook(LoanBook.createLoanFromBook(newMeta, null)));
                }
            }.runTaskLater(SimpleFactions.plugin, 1L);
        } else if(stage == 3) {
            e.setCancelled(true);
            Guild borrowerGuild = FactionManager.getGuildByLeader(p.getName());
            Loan loan = LoanBook.createLoanFromBook(newMeta, borrowerGuild);
            String validate = newMeta.getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
            Long time = newMeta.getPersistentDataContainer().get(Keys.LONG, PersistentDataType.LONG);
            if(time != null && time < System.currentTimeMillis()) {
                p.sendMessage("§cThis loan contract has expired! Unable to process.");
                p.getInventory().setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK));
                return;
            }
            if(validate == null) {
                p.sendMessage("§cThis loan contract has been tampered with! Unable to process.");
                new BukkitRunnable() {
                @Override
                    public void run() {
                        p.getInventory().setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK));
                    }
                }.runTaskLater(SimpleFactions.plugin, 1L);
                return;
            }
            Loan validation = LoanBook.createLoanFromString(validate, issuer, borrowerGuild);
            if(validation == null || !loan.validate(validation)) {
                p.sendMessage("§cThis loan contract has been tampered with! Unable to process.");
                new BukkitRunnable() {
                @Override
                    public void run() {
                        p.getInventory().setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK));
                    }
                }.runTaskLater(SimpleFactions.plugin, 1L);
                return;
            }
            if(borrowerGuild == null) {
                p.sendMessage("§cYou must be the leader of a guild to sign a loan contract!");
                return;
            }
            if(issuer.getBank().getWealth() < loan.getAmount()) {
                p.sendMessage("§cThe issuer cannot afford this loan!");
                return;
            }
            borrowerGuild.getBank().deposit(loan.getAmount());
            issuer.getBank().withdraw(loan.getAmount());
            issuer.getLoanHandler().issueLoan(loan);
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            p.sendMessage("§aLoan taken!");
            Player lender = Bukkit.getPlayer(issuer.getLeader());
            if(lender != null && lender.isOnline()) {
                lender.sendMessage("§aYour loan has been accepted by "+borrowerGuild.getName()+"!");
                lender.playSound(lender, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    ItemStack i = new ItemStack(Material.WRITTEN_BOOK);
                    BookMeta m = (BookMeta) i.getItemMeta();
                    ItemStack complete = LoanBook.getLoanBook(loan);
                    BookMeta completeMeta = (BookMeta) complete.getItemMeta();
                    m.setPages(completeMeta.getPages());
                    m.setDisplayName("§6Loan Agreement §7"+Cache.getFantasyDate(System.currentTimeMillis()));
                    m.setTitle("§6Loan Agreement §7"+Cache.getFantasyDate(System.currentTimeMillis()));
                    m.setAuthor(issuer.getLeader()+" and "+p.getName());
                    i.setItemMeta(m);
                    p.getInventory().setItemInMainHand(i);
                }
            }.runTaskLater(SimpleFactions.plugin, 5L);
        }
    }

    /**
     * The mercenary contract negotiation, same three stages as a loan: the company
     * leader edits and signs a draft, reviews and signs again to publish an offer,
     * and a government member of the hiring faction signs the agreement to accept.
     */
    private void signContractBook(
            PlayerEditBookEvent e, Player p, Guild host, int stage, BookMeta newMeta) {
        e.setCancelled(true);
        MercenaryCompany company = host.getCompany();
        if(company == null || !company.isFormed()) {
            p.sendMessage("§cThat company is not open for hire.");
            return;
        }
        Long expiry = ContractBook.expiry(newMeta);
        if(expiry != null && expiry < System.currentTimeMillis()) {
            p.sendMessage("§cThis contract has expired! Unable to process.");
            replaceHeldBook(p, new ItemStack(Material.WRITABLE_BOOK));
            return;
        }
        ContractTerms terms = ContractBook.parseTerms(newMeta);
        if(terms == null) {
            p.sendMessage("§cThe terms page could not be read.");
            return;
        }
        if(stage == ContractBook.STAGE_DRAFT) {
            if(!company.isLeader(p.getName())) {
                p.sendMessage("§cOnly the company leader may draft a contract.");
                return;
            }
            MercenaryResult valid = ContractValidator.validate(
                    terms, company, System.currentTimeMillis());
            if(!valid.ok()) {
                p.sendMessage("§c"+valid.message());
                return;
            }
            replaceHeldBook(p, ContractBook.reviewBook(company, terms));
            return;
        }
        if(stage == ContractBook.STAGE_REVIEW) {
            if(!company.isLeader(p.getName())) {
                p.sendMessage("§cOnly the company leader may publish an offer.");
                return;
            }
            if(!ContractBook.matchesSnapshot(newMeta)) {
                p.sendMessage("§cThis contract has been tampered with! Unable to process.");
                replaceHeldBook(p, new ItemStack(Material.WRITABLE_BOOK));
                return;
            }
            p.sendMessage("§7Choose who to offer this to with §e/company offer <faction>");
            replaceHeldBook(p, ContractBook.reviewBook(company, terms));
            return;
        }
        if(stage == ContractBook.STAGE_AGREEMENT) {
            String contractId = ContractBook.contractId(newMeta);
            MercenaryContract contract = company.getContractHandler().getById(contractId);
            if(contract == null) {
                p.sendMessage("§cThat offer no longer exists.");
                return;
            }
            if(!ContractBook.matchesSnapshot(newMeta)) {
                p.sendMessage("§cThis contract has been tampered with! Unable to process.");
                replaceHeldBook(p, new ItemStack(Material.WRITABLE_BOOK));
                return;
            }
            MercenaryResult result = company.getContractHandler()
                    .accept(contractId, contract.getHirer(), p.getName());
            p.sendMessage((result.ok() ? "§a" : "§c")+result.message());
            if(!result.ok()) return;
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            Player leader = Bukkit.getPlayer(company.getLeader());
            if(leader != null && leader.isOnline()) {
                leader.sendMessage("§a"+result.message());
                leader.playSound(leader, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            replaceHeldBook(p, signedAgreement(company, contract, p.getName()));
        }
    }

    /** A signed book cannot be replaced in the same tick, so this waits one. */
    private void replaceHeldBook(Player p, ItemStack book) {
        new BukkitRunnable() {
            @Override
            public void run() {
                p.getInventory().setItemInMainHand(book);
            }
        }.runTaskLater(SimpleFactions.plugin, 1L);
    }

    private ItemStack signedAgreement(
            MercenaryCompany company, MercenaryContract contract, String signer) {
        ItemStack i = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta m = (BookMeta) i.getItemMeta();
        ItemStack agreement = ContractBook.agreementBook(contract);
        m.setPages(((BookMeta) agreement.getItemMeta()).getPages());
        String title = "§6Mercenary Contract §7"+Cache.getFantasyDate(System.currentTimeMillis());
        m.setDisplayName(title);
        m.setTitle(title);
        m.setAuthor(company.getLeader()+" and "+signer);
        i.setItemMeta(m);
        return i;
    }

    @EventHandler
    public void earnMoney(PlayerEarnMoneyEvent e) {
        String playerName = e.getPlayer();
        double paidTax = 0;
        double gross = e.getAmount();
        if(FactionManager.getByMember(playerName) != null) {
			Faction f = FactionManager.getByMember(playerName);
			if(f.getTaxRate(TaxTarget.CITIZENS, null, true) > 0) {
				if(f.getBank() != null) {
					paidTax = f.getTaxRate(TaxTarget.CITIZENS, null, true)/100.0*gross;
					f.giveTax(playerName, paidTax);
				}
			}
		}
        Player online = Bukkit.getPlayerExact(playerName);
        UUID playerUuid = online != null
            ? online.getUniqueId()
            : Bukkit.getOfflinePlayer(playerName).getUniqueId();
        PlayerLedger ledger = PlayerEconomyManager.get().getLedger(playerUuid);
        if (gross > 0) {
            ledger.add(PlayerCashflow.EARNINGS, gross);
        }
        if (paidTax > 0) {
            ledger.add(PlayerCashflow.CITIZEN_TAX, -paidTax);
        }
        e.setAmount(paidTax);
    }

    @EventHandler
    public void depositMaterials(PlayerDepositMaterialsEvent e) {
        ItemStack i = e.getItem();
        Player p = e.getPlayer();
        Coin c = DenarEconomy.getMoneyManager().getCoin(i);
		if(c == null) return;
		if(c.canWithdraw()) return;
		if(!inFactionOrGuildBankChunk(p)) return;
		DenarEconomy.getMoneyManager().addMoneyToAccount(p.getUniqueId().toString(), c.getValue()*i.getAmount(), false, true, Accounts.BANK);
		p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
		i.setAmount(0);
    }

    @EventHandler
    public void bankPulse(PlayerBankPulseEvent e) {
        if(!isInBankChunk(e.getPlayer())) {
            e.setCancelled(true);
        }
    } 

    private boolean isInBankChunk(Player p) {
        Faction f = FactionManager.getByMember(p.getName());
        Guild g = FactionManager.getGuildByMember(p.getName());

        if (f == null && g == null) {
            p.sendMessage("§a[DenarEconomy] §cYou must be in a faction");
            return false;
        }

        if (!inFactionOrGuildBankChunk(p)) {
            p.sendMessage("§a[DenarEconomy] §cYou must be inside the bank chunk to deposit/withdraw");
            return false;
        }

        return true;
    }

    private boolean inFactionOrGuildBankChunk(Player p) {
        Faction f = FactionManager.getByMember(p.getName());
        Guild g = FactionManager.getGuildByMember(p.getName());
        Chunk playerChunk = p.getLocation().getChunk();

        if (f != null && f.getBank() != null && f.getBank().getChunk() != null
                && f.getBank().getChunk().equals(playerChunk)) {
            return true;
        }
        if (g != null && g.getBank() != null && g.getBank().getChunk() != null
                && g.getBank().getChunk().equals(playerChunk)) {
            return true;
        }
        return false;
    }
}
