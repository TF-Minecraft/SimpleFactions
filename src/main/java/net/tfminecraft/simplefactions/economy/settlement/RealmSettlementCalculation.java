package net.tfminecraft.simplefactions.economy.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.LoanData;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.guild.income.LedgerHistory;
import net.tfminecraft.simplefactions.guild.income.SettlementLedgerView;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryReputationSeam;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.mercenary.contract.TerminationReason;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation;

/** Owns detached financial objects. Calculation changes no live faction, bank, loan or obligation. */
public final class RealmSettlementCalculation {
    public record Claim(String id, UUID guildIncarnation, UUID characterId, long cents, net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind kind) {}

    /** Vehicle costs run last against the balances projected by the whole realm posting. */
    @FunctionalInterface
    public interface Vehicles {
        void calculate(Map<String, Long> balances, Map<String, Long> legs, RealmSettlementChanges changes);
    }

    private final long day;
    private final long now;
    private final List<Guild> guilds;
    private final List<Claim> claims;
    private List<FactionUpkeepPlan> preparedUpkeep;
    private final Map<Guild, Ledger> ledgers = new LinkedHashMap<>();
    private final Map<Guild, SettlementLedgerView> views = new LinkedHashMap<>();
    private final Map<Guild, MercenaryCompany> companies = new LinkedHashMap<>();
    private final Map<Loan, Loan> loans = new IdentityHashMap<>();
    private final Map<Guild, Integer> credits = new LinkedHashMap<>();
    private final Map<Guild, Double> initial = new LinkedHashMap<>();
    private final Map<String, Long> balances = new LinkedHashMap<>();
    private final Map<String, Long> legs = new LinkedHashMap<>();
    private final RealmSettlementChanges changes = new RealmSettlementChanges();

    public RealmSettlementCalculation(long day, long now, Collection<Guild> guilds, List<Claim> claims) {
        this.day = day;
        this.now = now;
        this.guilds = guilds.stream().sorted(Comparator.comparing(Guild::getId)).toList();
        this.claims = List.copyOf(claims);
    }

    public RealmSettlementCalculation withPreparedUpkeep(List<FactionUpkeepPlan> upkeep) {
        this.preparedUpkeep = List.copyOf(upkeep);
        return this;
    }

    public SettlementJournal.Plan calculate(Vehicles vehicles) {
        capture();
        accrue();
        var history = Ledger.collectHistoryDay(guilds, ledgers::get);
        DailyGuildTransfers buffer = new DailyGuildTransfers();
        for (Guild guild : guilds) ledgers.get(guild).populateDailyTransfers(buffer);
        for (var from : buffer.getTransfers().entrySet()) {
            for (var to : from.getValue().entrySet()) transfer(account(from.getKey()), account(to.getKey()), Money.cents(to.getValue()));
        }
        for (var entry : buffer.getExternalDeltas().entrySet()) transfer(Denars.MINT, account(entry.getKey()), Money.cents(entry.getValue()));
        for (Guild guild : guilds) {
            if (canMove(guild) && guild.isBase()) {
                // CITIZENS in the transfer buffer is backed by held tax, never newly minted.
                long held = Money.cents(ledgers.get(guild).getIncome(Cashflow.CITIZENS));
                transfer(Denars.guildTax(guild.getIncarnationId()), Denars.MINT, held);
            }
        }
        for (Guild guild : guilds) liquidate(guild);
        for (Guild guild : guilds) payouts(guild, buffer);
        payOrphanClaims();
        for (Guild guild : guilds) {
            for (Loan loan : guild.getLoanHandler().getLoansGiven()) loans.get(loan).tickDay();
            ageContracts(guild);
        }
        for (Guild guild : guilds) {
            afterValues(guild, history.get(guild));
        }
        for (Faction faction : guilds.stream().map(Guild::getFaction).distinct().toList()) {
            List<WarReparationsObligation> next = new ArrayList<>();
            for (WarReparationsObligation obligation : faction.getWarReparationsObligations()) {
                if (obligation != null && obligation.getDaysRemaining() > 1) next.add(new WarReparationsObligation(
                        obligation.getPayeeFactionId(), obligation.getIncomePercent(), obligation.getDaysRemaining() - 1));
            }
            changes.reparations.put(faction.getId(), next);
        }
        vehicles.calculate(balances, legs, changes);
        List<Leg> posting = legs.entrySet().stream().filter(e -> e.getValue() != 0)
                .sorted(Map.Entry.comparingByKey()).map(e -> new Leg(e.getKey(), e.getValue())).toList();
        return new SettlementJournal.Plan(day, posting, JsonUtil.GSON.toJsonTree(changes).getAsJsonObject());
    }

    private void capture() {
        Map<Faction, FactionUpkeepPlan> upkeep = new LinkedHashMap<>();
        for (Guild guild : guilds) {
            balances.put(account(guild), guild.getBank() == null ? 0 : Money.cents(guild.getBank().getWealth()));
            initial.put(guild, guild.getBank() == null ? 0 : guild.getBank().getWealth());
            credits.put(guild, guild.getLoanHandler().getCreditScore());
            MercenaryCompany company = guild.getCompany();
            if (company != null) companies.put(guild, new MercenaryCompany(guild, company.serialize(),
                    company.getRegiment() == null ? null : new Regiment(company.getRegiment())));
        }
        for (Faction faction : guilds.stream().map(Guild::getFaction).distinct().toList()) {
            FactionUpkeepPlan plan = preparedUpkeep == null ? FactionUpkeepPlan.calculate(faction)
                    : preparedUpkeep.stream().filter(p -> p.faction().equals(faction.getId())).findFirst().orElseThrow();
            changes.upkeep.add(plan);
            upkeep.put(faction, plan);
            Guild capital = SettlementGuilds.capital(faction);
            transfer(account(capital), Denars.MINT, plan.charge());
            initial.put(capital, Money.denars(balances.get(account(capital))));
        }
        for (Guild guild : guilds) {
            for (Loan original : guild.getLoanHandler().getLoansGiven()) {
                if (!credits.containsKey(original.getBorrower()) || original.getIssuer() != guild)
                    throw new IllegalStateException("Loan parties missing from settlement: " + original.getId());
                loans.put(original, original.copyForSettlement(now, delta -> credits.compute(original.getBorrower(),
                        (key, value) -> Math.max(0, Math.min(100, value + delta)))));
            }
        }
        for (Guild guild : guilds) {
            Map<Cashflow, Double> income = new EnumMap<>(Cashflow.class);
            if (guild.isBase()) {
                FactionUpkeepPlan costs = upkeep.get(guild.getFaction());
                MercenaryCompany company = companies.get(guild);
                income.put(Cashflow.MILITARY_UPKEEP, -costs.armyLedgerCost() - (company == null || !company.isFormed() ? 0 : company.getSlotUpkeep()));
                income.put(Cashflow.INSTALLATIONS, -costs.installationLedgerCost());
                income.put(Cashflow.CITIZENS, Money.denars(Denars.balance(Denars.guildTax(guild.getIncarnationId()))));
            }
            SettlementLedgerView view = new SettlementLedgerView(ledgers,
                    guild.getLoanHandler().getLoansGiven().stream().map(loans::get).toList(),
                    guild.getLoanHandler().getLoansTaken().stream().map(loans::get).toList(), companies.get(guild),
                    income, initial.get(guild), guild.getDonationAmount());
            views.put(guild, view);
            ledgers.put(guild, guild.getLedger().copyForSettlement(view));
        }
    }

    private void accrue() {
        for (Guild guild : guilds) {
            MercenaryCompany company = companies.get(guild);
            if (!canMove(guild) || company == null || !company.isFormed()) continue;
            for (String member : company.getEnlisted()) company.accrueWage(member, company.getWageSettings().peacetimeFor(member));
            for (MercenaryContract contract : company.getContractHandler().getActive()) {
                Guild capital = capital(contract);
                if (!canMove(capital)) continue;
                contract.accrueDayPrice();
                List<String> enlisted = company.getEnlisted();
                for (int i = 0; i < Math.min(contract.getSlots(), enlisted.size()); i++) {
                    String member = enlisted.get(i);
                    company.accrueWage(member, company.getWageSettings().activeShareOf(contract.getPricePerSlotPerDay(), member));
                }
            }
            for (MercenaryContract contract : company.getContractHandler().getAll()) {
                Guild capital = capital(contract);
                if (capital == null) continue;
                ledgers.get(capital).addMercenaryPaymentEntry(guild.getId(), contract.getAccruedToCompany());
                ledgers.get(capital).addRefundEntry(guild.getId(), contract.getAccruedToHirer());
            }
        }
    }

    private void liquidate(Guild guild) {
        var change = new RealmSettlementChanges.GuildChange();
        change.id = guild.getId();
        change.incarnation = guild.getIncarnationId();
        guild.getBranches().forEach((key, branch) -> change.branches.put(key, branch.getLevel()));
        changes.guilds.add(change);
        if (guild.getBank() == null || net.tfminecraft.simplefactions.inactivity.InactivityService.isFullyInactive(guild.getMembers())) return;
        int size = change.branches.values().stream().mapToInt(Integer::intValue).sum();
        Random random = new Random(day ^ guild.getIncarnationId().getMostSignificantBits() ^ guild.getIncarnationId().getLeastSignificantBits());
        for (int guard = 0; balances.get(account(guild)) < 0 && size > 0 && guard < 1000; guard++) {
            List<Integer> expanded = change.branches.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).sorted().toList();
            int selected = expanded.get(random.nextInt(expanded.size()));
            double refund = Math.round(Cache.branchUpgradeCost * Math.pow(Cache.branchUpgradeExponent, --size) * 0.8 * 100) / 100.0;
            change.branches.compute(selected, (key, value) -> value - 1);
            transfer(Denars.MINT, account(guild), Money.cents(refund));
        }
    }

    private void payouts(Guild guild, DailyGuildTransfers buffer) {
        if (guild.getBank() == null || balances.get(account(guild)) < 0) return;
        long pool = Money.cents(buffer.getPendingDividendPools().getOrDefault(guild, 0.0));
        long newDividendPool = pool;
        Map<UUID, Double> wages = buffer.getPlayerPayouts().getOrDefault(guild, Map.of());
        long wageTotal = wages.values().stream().mapToLong(Money::cents).sum();
        List<Claim> ownClaims = claims.stream().filter(c -> c.guildIncarnation().equals(guild.getIncarnationId())).toList();
        pool = Math.max(pool, ownClaims.stream().filter(c -> c.kind() == net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.DIVIDENDS).mapToLong(Claim::cents).sum());
        long deathWages = ownClaims.stream().filter(c -> c.kind() == net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.WAGES).mapToLong(Claim::cents).sum();
        long needed = Math.addExact(pool, Math.addExact(wageTotal, deathWages));
        if (needed <= 0) return;
        long available = Math.max(0, balances.get(account(guild)));
        double scale = Math.min(1, (double) available / needed);
        long actualPool = Math.min(available, Math.round(pool * scale));
        if (actualPool > 0 && !guild.isBase()) {
            long taxablePool = Math.min(actualPool, Math.round(newDividendPool * scale));
            long tax = Math.min(taxablePool, Math.max(0, Math.round(taxablePool * guild.getFaction().getTaxRate(TaxTarget.DIVIDENDS, guild.getId(), true) / 100)));
            Guild capital = SettlementGuilds.capital(guild.getFaction());
            if (capital.getBank() != null && capital != guild) transfer(account(guild), account(capital), tax);
            long left = actualPool - tax;
            for (Claim claim : ownClaims) {
                if (claim.kind() != net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.DIVIDENDS) continue;
                long paid = Math.min(left, claim.cents());
                payClaim(guild, claim, paid);
                left -= paid;
            }
            List<String> eligible = guild.getDividendEligibleMembers().stream().filter(key -> Members.characterId(key) != null).sorted().toList();
            if (!eligible.isEmpty()) {
                long each = left / eligible.size();
                long remainder = left % eligible.size();
                for (String key : eligible) payCharacter(guild, Members.characterId(key), each + (remainder-- > 0 ? 1 : 0), PlayerCashflow.DIVIDEND_PAYOUT);
            }
        }
        MercenaryCompany company = companies.get(guild);
        Map<String, Double> unpaid = company == null ? new LinkedHashMap<>() : new LinkedHashMap<>(company.getPendingWages());
        for (var wage : wages.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            long paid = Math.min(Math.max(0, balances.get(account(guild))), Math.round(Money.cents(wage.getValue()) * scale));
            payCharacter(guild, wage.getKey(), paid, PlayerCashflow.WAGES);
            unpaid.computeIfPresent(Members.key(wage.getKey()), (key, value) -> Math.max(0, Formatter.formatDouble(value - Money.denars(paid))));
        }
        if (company != null) {
            company.clearPendingWages();
            unpaid.forEach(company::accrueWage);
        }
        for (Claim claim : ownClaims) {
            if (claim.kind() != net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.WAGES) continue;
            long paid = Math.min(Math.max(0, balances.get(account(guild))), Math.round(claim.cents() * scale));
            payClaim(guild, claim, paid);
        }
    }

    private void payClaim(Guild guild, Claim claim, long cents) {
        if (cents <= 0) return;
        payCharacter(guild, claim.characterId(), cents, claim.kind() == net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.WAGES ? PlayerCashflow.WAGES : PlayerCashflow.DIVIDEND_PAYOUT);
        changes.deathPayments.put(claim.id(), cents);
    }

    private void payOrphanClaims() {
        Map<UUID, List<Claim>> orphaned = new LinkedHashMap<>();
        for (Claim claim : claims) {
            if (guilds.stream().noneMatch(g -> g.getIncarnationId().equals(claim.guildIncarnation())))
                orphaned.computeIfAbsent(claim.guildIncarnation(), key -> new ArrayList<>()).add(claim);
        }
        for (var entry : orphaned.entrySet()) {
            String account = Denars.guild(entry.getKey());
            long available = Math.max(0, Denars.balance(account));
            long owed = entry.getValue().stream().mapToLong(Claim::cents).sum();
            double scale = owed == 0 ? 0 : Math.min(1, (double) available / owed);
            for (Claim claim : entry.getValue().stream().sorted(Comparator.comparing(Claim::id)).toList()) {
                long paid = Math.min(available, Math.round(claim.cents() * scale));
                if (paid <= 0) continue;
                transfer(account, "char:" + claim.characterId() + ":bank", paid);
                changes.deathPayments.put(claim.id(), paid);
                available -= paid;
            }
        }
    }

    private void payCharacter(Guild guild, UUID character, long cents, PlayerCashflow source) {
        if (cents <= 0) return;
        transfer(account(guild), "char:" + character + ":bank", cents);
        changes.playerIncome.computeIfAbsent(character, key -> new LinkedHashMap<>()).merge(source.name(), Money.denars(cents), Double::sum);
    }

    private void ageContracts(Guild guild) {
        MercenaryCompany company = companies.get(guild);
        if (company == null || !company.isFormed()) return;
        for (MercenaryContract contract : company.getContractHandler().getActive()) contract.addDayServed();
        for (MercenaryContract contract : company.getContractHandler().getActive()) {
            TerminationReason reason = now >= contract.getDueDate() ? TerminationReason.DURATION_ELAPSED
                    : balances.get(account(guild)) < 0 ? TerminationReason.HOST_BANKRUPT : null;
            if (reason != null && contract.finish(reason.getOutcome())) new MercenaryReputationSeam().onTermination(contract, reason);
        }
    }

    private void afterValues(Guild guild, Map<LedgerHistory.Source, Map<String, Double>> historyDay) {
        var change = changes.guilds.stream().filter(c -> c.incarnation.equals(guild.getIncarnationId())).findFirst().orElseThrow();
        change.donation = views.get(guild).donation();
        change.citizenTaxes = canMove(guild) ? Map.of() : guild.getLedger().getCitizenTaxesCopy();
        change.casinoProfit = canMove(guild) ? 0 : guild.getLedger().getCasinoProfit();
        change.vehicleFeeIncome = canMove(guild) ? 0 : guild.getLedger().getVehicleFeeIncome();
        change.creditScore = credits.get(guild);
        for (Loan original : guild.getLoanHandler().getLoansGiven()) change.loans.add(new LoanData(loans.get(original)));
        MercenaryCompany company = companies.get(guild);
        if (company != null) {
            change.company = company.serialize();
            for (var contract : change.company.contracts) {
                Faction hirer = net.tfminecraft.simplefactions.managers.FactionManager.getByString(contract.hirer);
                if (canMove(guild) && hirer != null && canMove(SettlementGuilds.capital(hirer))) contract.accruedToCompany = 0.0;
                if (canMove(guild) && hirer != null) contract.accruedToHirer = 0.0;
            }
        }
        change.dividendEligible = new ArrayList<>(guild.getMembers());
        LedgerHistory history = new LedgerHistory();
        history.load(guild.getLedger().getHistory().getLastDayCopy(), guild.getLedger().getHistory().getLifetimeCopy(), guild.getLedger().getHistory().getDepositsTodayCopy());
        history.closeDay(historyDay);
        change.ledgerLastDay = history.getLastDayCopy();
        change.ledgerLifetime = history.getLifetimeCopy();
    }

    private boolean canMove(Guild guild) { return guild != null && guild.getBank() != null && initial.containsKey(guild) && initial.get(guild) >= 0; }
    private static Guild capital(MercenaryContract contract) { return contract.getHirer() == null ? null : SettlementGuilds.capital(contract.getHirer()); }
    private static String account(Guild guild) { return Denars.guild(guild.getIncarnationId()); }

    private void transfer(String from, String to, long cents) {
        if (cents == 0) return;
        legs.merge(from, -cents, Math::addExact);
        legs.merge(to, cents, Math::addExact);
        balances.computeIfPresent(from, (key, value) -> Math.subtractExact(value, cents));
        balances.computeIfPresent(to, (key, value) -> Math.addExact(value, cents));
    }
}
