package net.tfminecraft.simplefactions.managers;

import java.util.function.Consumer;
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

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.loans.LoanBook;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.ContractBook;
import net.tfminecraft.simplefactions.mercenary.contract.ContractTerms;
import net.tfminecraft.simplefactions.mercenary.contract.ContractValidator;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.denareconomy.item.Coin;
import net.tfminecraft.denareconomy.event.PlayerBankPulseEvent;
import net.tfminecraft.denareconomy.event.PlayerDepositMaterialsEvent;
import net.tfminecraft.denareconomy.event.PlayerEarnMoneyEvent;

public class PlayerManager implements Listener{
    @EventHandler
    public void joinEvent(PlayerJoinEvent e) {
        net.tfminecraft.simplefactions.inactivity.InactivityService.onLogin(e.getPlayer());
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
        Consumer<ItemStack> replacement = bookReplacement(e);
        Integer contractStage = ContractBook.stage(meta);
        if(contractStage != null) {
            signContractBook(e, p, issuer, contractStage, newMeta, replacement);
            return;
        }
        Integer stage = meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER);
        if(stage == null) return;
        if((stage == 1 || stage == 2) && !issuer.isLeader(p)) {
            // Drafting and reviewing commit the lending guild's bank, so only its leader may sign.
            e.setCancelled(true);
            p.sendMessage("§cOnly the leader of "+issuer.getName()+" can draft its loans.");
            return;
        }
        if(stage == 1) {
            e.setCancelled(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    Long time = newMeta.getPersistentDataContainer().get(Keys.LONG, PersistentDataType.LONG);
                    if(time != null && time < System.currentTimeMillis()) {
                        p.sendMessage("§cThis loan contract has expired! Unable to process.");
                        replacement.accept(new ItemStack(Material.WRITABLE_BOOK));
                        return;
                    }
                    Loan draft = LoanBook.createLoanFromBook(newMeta, null);
                    if(draft == null) {
                        p.sendMessage(LoanBook.INVALID_TERMS_MESSAGE);
                        return;
                    }
                    replacement.accept(LoanBook.getEstimatedBook(draft));
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
                        replacement.accept(new ItemStack(Material.WRITABLE_BOOK));
                        return;
                    }
                    Loan draft = LoanBook.createLoanFromBook(newMeta, null);
                    if(draft == null) {
                        p.sendMessage(LoanBook.INVALID_TERMS_MESSAGE);
                        return;
                    }
                    replacement.accept(LoanBook.getLoanBook(draft));
                }
            }.runTaskLater(SimpleFactions.plugin, 1L);
        } else if(stage == 3) {
            e.setCancelled(true);
            Guild borrowerGuild = FactionManager.getGuildByLeader(p.getName());
            String offerId = LoanBook.offerId(newMeta);
            Loan loan = offerId == null ? null : LoanBook.createLoanFromBook(newMeta, borrowerGuild, offerId);
            String validate = newMeta.getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
            Long time = newMeta.getPersistentDataContainer().get(Keys.LONG, PersistentDataType.LONG);
            if(time != null && time < System.currentTimeMillis()) {
                p.sendMessage("§cThis loan contract has expired! Unable to process.");
                replacement.accept(new ItemStack(Material.WRITABLE_BOOK));
                return;
            }
            if(offerId == null) {
                p.sendMessage("§cThis loan agreement is out of date. Ask the lender for a new one.");
                return;
            }
            if(validate == null) {
                p.sendMessage("§cThis loan contract has been tampered with! Unable to process.");
                new BukkitRunnable() {
                @Override
                    public void run() {
                        replacement.accept(new ItemStack(Material.WRITABLE_BOOK));
                    }
                }.runTaskLater(SimpleFactions.plugin, 1L);
                return;
            }
            Loan validation = LoanBook.createLoanFromString(validate, issuer, borrowerGuild);
            if(loan == null || validation == null || !loan.validate(validation)) {
                p.sendMessage("§cThis loan contract has been tampered with! Unable to process.");
                new BukkitRunnable() {
                @Override
                    public void run() {
                        replacement.accept(new ItemStack(Material.WRITABLE_BOOK));
                    }
                }.runTaskLater(SimpleFactions.plugin, 1L);
                return;
            }
            if(borrowerGuild == null) {
                p.sendMessage("§cYou must be the leader of a guild to sign a loan contract!");
                return;
            }
            if(issuer.getBank() == null || borrowerGuild.getBank() == null) {
                p.sendMessage("§cBoth guilds need a bank to take out a loan!");
                return;
            }
            if(issuer.getBank().getWealth() < loan.getAmount()) {
                p.sendMessage("§cThe issuer cannot afford this loan!");
                return;
            }
            // Each agreement pays out once. The book is only swapped a few ticks later, so a
            // second signature before then, or after a restart, must not take the loan again.
            long bookExpiresAt = time != null ? time : System.currentTimeMillis() + 86400000L;
            if(!issuer.getLoanHandler().useOffer(offerId, bookExpiresAt)) {
                p.sendMessage("§cThis loan agreement has already been signed.");
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
                // Keep the existing legacy text representation, formatting, and exact-string comparisons.
                @SuppressWarnings("deprecation")
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
                    replacement.accept(i);
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
            PlayerEditBookEvent e, Player p, Guild host, int stage, BookMeta newMeta, Consumer<ItemStack> replacement) {
        e.setCancelled(true);
        MercenaryCompany company = host.getCompany();
        if(company == null || !company.isFormed()) {
            p.sendMessage("§cThat company is not open for hire.");
            return;
        }
        Long expiry = ContractBook.expiry(newMeta);
        if(expiry != null && expiry < System.currentTimeMillis()) {
            p.sendMessage("§cThis contract has expired! Unable to process.");
            replaceHeldBook(replacement, new ItemStack(Material.WRITABLE_BOOK));
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
            replaceHeldBook(replacement, ContractBook.reviewBook(company, terms));
            return;
        }
        if(stage == ContractBook.STAGE_REVIEW) {
            if(!company.isLeader(p.getName())) {
                p.sendMessage("§cOnly the company leader may publish an offer.");
                return;
            }
            if(!ContractBook.matchesSnapshot(newMeta)) {
                p.sendMessage("§cThis contract has been tampered with! Unable to process.");
                replaceHeldBook(replacement, new ItemStack(Material.WRITABLE_BOOK));
                return;
            }
            p.sendMessage("§7Choose who to offer this to with §e/company offer <faction>");
            replaceHeldBook(replacement, ContractBook.reviewBook(company, terms));
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
                replaceHeldBook(replacement, new ItemStack(Material.WRITABLE_BOOK));
                return;
            }
            MercenaryResult result = company.getContractHandler()
                    .acceptAtHall(contractId, contract.getHirer(), p);
            p.sendMessage((result.ok() ? "§a" : "§c")+result.message());
            if(!result.ok()) return;
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            Player leader = Bukkit.getPlayer(company.getLeader());
            if(leader != null && leader.isOnline()) {
                leader.sendMessage("§a"+result.message());
                leader.playSound(leader, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            replaceHeldBook(replacement, signedAgreement(company, contract, p.getName()));
        }
    }

    /** Keep delayed updates bound to the original inventory slot and its unchanged book. */
    private Consumer<ItemStack> bookReplacement(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        int slot = event.getSlot() == -1 ? 40 : event.getSlot();
        ItemStack original = player.getInventory().getItem(slot);
        ItemStack expected = original == null ? null : original.clone();
        return replacement -> {
            if (!player.isOnline() || expected == null || expected.getType() != Material.WRITABLE_BOOK) return;
            ItemStack current = player.getInventory().getItem(slot);
            if (current != null && current.getAmount() == expected.getAmount() && current.isSimilar(expected)) {
                player.getInventory().setItem(slot, replacement);
            }
        };
    }

    /** A signed book cannot be replaced in the same tick, so this waits one. */
    private void replaceHeldBook(Consumer<ItemStack> replacement, ItemStack book) {
        new BukkitRunnable() {
            @Override
            public void run() {
                replacement.accept(book);
            }
        }.runTaskLater(SimpleFactions.plugin, 1L);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
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
        PlayerLedger ledger = PlayerEconomyManager.get().getLedger(playerName);
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
            p.sendMessage("§cYou must belong to a guild or faction to bank here.");
            return false;
        }

        if (!inFactionOrGuildBankChunk(p)) {
            p.sendMessage("§cYou must be at your bank to deposit or withdraw.");
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
