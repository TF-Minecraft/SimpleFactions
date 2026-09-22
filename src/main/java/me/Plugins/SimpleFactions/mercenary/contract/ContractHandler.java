package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * The contracts a company holds, in the shape of
 * {@link me.Plugins.SimpleFactions.Guild.loans.LoanHandler}: the company owns
 * the map, and a hiring faction finds its contracts by scanning companies.
 */
public class ContractHandler {
    private final MercenaryCompany company;
    private final Map<String, MercenaryContract> contracts = new LinkedHashMap<>();

    public ContractHandler(MercenaryCompany company) {
        this.company = company;
    }

    public MercenaryCompany getCompany() {
        return company;
    }

    public void add(MercenaryContract contract) {
        if (contract == null) return;
        contracts.put(contract.getId(), contract);
    }

    public void remove(String id) {
        contracts.remove(id);
    }

    public MercenaryContract getById(String id) {
        if (id == null) return null;
        for (MercenaryContract c : contracts.values()) {
            if (c.getId().equalsIgnoreCase(id)) return c;
        }
        return null;
    }

    public List<MercenaryContract> getAll() {
        return new ArrayList<>(contracts.values());
    }

    public List<MercenaryContract> getActive() {
        List<MercenaryContract> list = new ArrayList<>();
        for (MercenaryContract c : contracts.values()) {
            if (c.isActive()) list.add(c);
        }
        return list;
    }

    public List<MercenaryContract> getOffered() {
        List<MercenaryContract> list = new ArrayList<>();
        for (MercenaryContract c : contracts.values()) {
            if (c.isOffered()) list.add(c);
        }
        return list;
    }

    /** Offered and active both hold slots, so both matter to the reservation calendar. */
    public List<MercenaryContract> getReserving() {
        List<MercenaryContract> list = new ArrayList<>();
        for (MercenaryContract c : contracts.values()) {
            if (c.reservesSlots()) list.add(c);
        }
        return list;
    }

    public List<MercenaryContract> getForFaction(Faction faction) {
        List<MercenaryContract> list = new ArrayList<>();
        for (MercenaryContract c : contracts.values()) {
            if (c.isHirer(faction)) list.add(c);
        }
        return list;
    }

    public boolean hasActiveFor(Faction faction) {
        for (MercenaryContract c : contracts.values()) {
            if (c.isActive() && c.isHirer(faction)) return true;
        }
        return false;
    }

    /* =====================================================
     * Transitions
     * ===================================================== */

    /** An offer and why it was or was not written. */
    public record Offer(MercenaryResult result, MercenaryContract contract) {
        public boolean ok() {
            return result.ok();
        }

        public String message() {
            return result.message();
        }
    }

    public Offer offer(Faction hirer, ContractTerms terms) {
        return offer(hirer, ContractKind.MERCENARY, terms, System.currentTimeMillis());
    }

    /**
     * Writes an offer, which immediately holds its slots. The terms are validated
     * and loyalty is checked here rather than at acceptance, so a company cannot
     * dangle an offer it was never allowed to make.
     */
    public Offer offer(Faction hirer, ContractKind kind, ContractTerms terms, long now) {
        if (hirer == null) {
            return new Offer(MercenaryResult.deny("That faction no longer exists."), null);
        }
        MercenaryResult valid = ContractValidator.validate(terms, company, now);
        if (!valid.ok()) return new Offer(valid, null);

        MercenaryResult loyal = MercenaryLoyalty.canServe(company, hirer);
        if (!loyal.ok()) return new Offer(loyal, null);
        MercenaryResult alongside = MercenaryLoyalty.canServeAlongside(company, hirer);
        if (!alongside.ok()) return new Offer(alongside, null);

        MercenaryContract contract = new MercenaryContract(company, hirer, kind, terms, now);
        add(contract);
        return new Offer(MercenaryResult.ok("Offer sent to " + hirer.getName() + "."), contract);
    }

    /**
     * The hiring faction accepts. Capacity is not re-checked because the offer has
     * been holding those slots all along; loyalty is, because the world moves.
     */
    public MercenaryResult accept(String contractId, Faction hirer, String signer) {
        MercenaryContract contract = getById(contractId);
        if (contract == null) {
            return MercenaryResult.deny("That contract no longer exists.");
        }
        if (!contract.isOffered()) {
            return MercenaryResult.deny("That offer is no longer open.");
        }
        if (!contract.isHirer(hirer)) {
            return MercenaryResult.deny("That offer was not made to your faction.");
        }
        if (contract.isOfferExpired()) {
            expire(contract);
            return MercenaryResult.deny("That offer has lapsed.");
        }
        if (hirer.getGovernment() == null || !hirer.getGovernment().isCouncilMember(signer)) {
            return MercenaryResult.deny("Only a member of your government may sign a contract.");
        }
        MercenaryResult loyal = MercenaryLoyalty.canServe(company, hirer);
        if (!loyal.ok()) {
            expire(contract);
            return loyal;
        }
        MercenaryResult alongside = MercenaryLoyalty.canServeAlongside(company, hirer);
        if (!alongside.ok()) {
            expire(contract);
            return alongside;
        }
        if (!contract.activate()) {
            return MercenaryResult.deny("That offer is no longer open.");
        }
        return MercenaryResult.ok(company.getName() + " has entered your service.");
    }

    /** The hirer refuses, or the company withdraws. Either way the hold is released. */
    public MercenaryResult decline(String contractId) {
        MercenaryContract contract = getById(contractId);
        if (contract == null) {
            return MercenaryResult.deny("That contract no longer exists.");
        }
        if (!contract.isOffered()) {
            return MercenaryResult.deny("That offer is no longer open.");
        }
        expire(contract);
        return MercenaryResult.ok("Offer declined.");
    }

    private void expire(MercenaryContract contract) {
        contract.finish(ContractStatus.TERMINATED);
    }

    /** Driven from {@code MercenaryCompany.tick()}; a lapsed offer frees its slots. */
    public List<MercenaryContract> tickExpiry() {
        List<MercenaryContract> lapsed = new ArrayList<>();
        for (MercenaryContract c : new ArrayList<>(contracts.values())) {
            if (!c.isOfferExpired()) continue;
            expire(c);
            lapsed.add(c);
        }
        return lapsed;
    }
}
