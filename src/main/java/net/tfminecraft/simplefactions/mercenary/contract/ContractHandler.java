package net.tfminecraft.simplefactions.mercenary.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;

/**
 * The contracts a company holds, in the shape of
 * {@link net.tfminecraft.simplefactions.guild.loans.LoanHandler}: the company owns
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

    /**
     * The company leader offers a new slot count on an active contract. Nothing
     * about the signed terms changes until the hiring government accepts. An
     * increase holds the extra slots immediately; a decrease holds nothing.
     */
    public MercenaryResult proposeSlots(String contractId, String actor, int slots, long now) {
        MercenaryContract contract = getById(contractId);
        if (contract == null) {
            return MercenaryResult.deny("That contract no longer exists.");
        }
        if (!company.isLeader(actor)) {
            return MercenaryResult.deny("Only the guild leader can change a contract's slots.");
        }
        if (!contract.isActive()) {
            return MercenaryResult.deny("Only an active contract can change size.");
        }
        contract.takeExpiredSlotChange(now);
        if (contract.hasPendingSlots(now)) {
            return MercenaryResult.deny("A slot change is already waiting on the hirer.");
        }
        if (now >= contract.getDueDate()) {
            return MercenaryResult.deny("That contract has already ended.");
        }
        if (slots < 1) {
            return MercenaryResult.deny("A contract must keep at least one slot.");
        }
        if (slots == contract.getSlots()) {
            return MercenaryResult.deny("That contract already hires that many slots.");
        }
        int room = SlotReservations.maxForAmendment(company, contract, now);
        if (slots > room) {
            int more = Math.max(0, room - contract.getSlots());
            if (more == 0) {
                return MercenaryResult.deny("No more slots are free for the rest of this contract.");
            }
            return MercenaryResult.deny("Only " + more + " more slot"
                    + (more == 1 ? " is" : "s are") + " free for the rest of this contract.");
        }
        int from = contract.getSlots();
        contract.offerSlotChange(slots, now + MercenaryContract.OFFER_WINDOW_MS);
        Faction hirer = contract.getHirer();
        tellGovernment(hirer, "§e" + company.getName()
                + " offered to change your contract from " + from + " slots to " + slots
                + ". A day would cost " + Formatter.formatMoney(contract.getPricePerSlotPerDay() * slots)
                + "d, a battle " + Formatter.formatMoney(contract.getPricePerSlotPerBattle() * slots)
                + "d. Open the company contracts to accept or decline.");
        String hirerName = hirer == null ? "the hiring faction" : hirer.getName();
        return MercenaryResult.ok("Slot change offered to " + hirerName + ".");
    }

    /** The company leader takes the offer back. The signed slot count stays. */
    public MercenaryResult withdrawSlots(String contractId, String actor, long now) {
        MercenaryContract contract = getById(contractId);
        if (contract == null) {
            return MercenaryResult.deny("That contract no longer exists.");
        }
        if (!company.isLeader(actor)) {
            return MercenaryResult.deny("Only the guild leader can withdraw a slot change.");
        }
        if (!contract.hasPendingSlots(now)) {
            contract.takeExpiredSlotChange(now);
            return MercenaryResult.deny("There is no slot change waiting.");
        }
        int slots = contract.getPendingSlots(now);
        contract.clearPendingSlots();
        tellGovernment(contract.getHirer(), "§e" + company.getName()
                + " withdrew the offer to change your contract to " + slots + " slots.");
        return MercenaryResult.ok("Slot change withdrawn.");
    }

    /**
     * The hiring government accepts. Refused while one of their battles has
     * started, because that battle is already using the old slot count.
     */
    public MercenaryResult acceptSlots(String contractId, Faction hirer, String signer, long now) {
        MercenaryContract contract = getById(contractId);
        if (contract == null) {
            return MercenaryResult.deny("That contract no longer exists.");
        }
        if (!contract.isActive()) {
            return MercenaryResult.deny("That contract is no longer running.");
        }
        if (!contract.isHirer(hirer)) {
            return MercenaryResult.deny("That slot change was not offered to your faction.");
        }
        if (!contract.hasPendingSlots(now)) {
            contract.takeExpiredSlotChange(now);
            return MercenaryResult.deny("That slot change has lapsed.");
        }
        if (hirer.getGovernment() == null || !hirer.getGovernment().isCouncilMember(signer)) {
            return MercenaryResult.deny("Only a member of your government may accept a slot change.");
        }
        if (ContractBattleGate.hirerIsFighting(hirer)) {
            return MercenaryResult.deny("A battle is underway. Accept the slot change when it ends.");
        }
        int slots = contract.getPendingSlots(now);
        if (slots > SlotReservations.maxForAmendment(company, contract, now)) {
            contract.clearPendingSlots();
            tell(company.getLeader(), "§cThe slot change on your contract with "
                    + hirer.getName() + " was dropped. The company no longer has room for it.");
            return MercenaryResult.deny("The company no longer has room for that many slots.");
        }
        if (!contract.applyPendingSlots(now)) {
            return MercenaryResult.deny("That slot change has lapsed.");
        }
        tell(company.getLeader(), "§a" + hirer.getName()
                + " accepted the slot change. The contract now hires " + slots + " slots.");
        return MercenaryResult.ok("The contract now hires " + slots + " slots.");
    }

    /** The hiring government refuses. The signed slot count stays. */
    public MercenaryResult declineSlots(String contractId, Faction hirer, String signer, long now) {
        MercenaryContract contract = getById(contractId);
        if (contract == null) {
            return MercenaryResult.deny("That contract no longer exists.");
        }
        if (!contract.isHirer(hirer)) {
            return MercenaryResult.deny("That slot change was not offered to your faction.");
        }
        if (!contract.hasPendingSlots(now)) {
            contract.takeExpiredSlotChange(now);
            return MercenaryResult.deny("There is no slot change waiting.");
        }
        if (hirer.getGovernment() == null || !hirer.getGovernment().isCouncilMember(signer)) {
            return MercenaryResult.deny("Only a member of your government may decline a slot change.");
        }
        int slots = contract.getPendingSlots(now);
        contract.clearPendingSlots();
        tell(company.getLeader(), "§c" + hirer.getName()
                + " declined the change to " + slots + " slots.");
        return MercenaryResult.ok("Slot change declined.");
    }

    /**
     * A company that shrank can no longer cover a pending increase. The signed
     * contract stays; only the unaccepted extra is released.
     */
    public List<MercenaryContract> lapseAmendmentsThatNoLongerFit(long now) {
        List<MercenaryContract> lapsed = new ArrayList<>();
        for (MercenaryContract c : getActive()) {
            Integer pending = c.getPendingSlots(now);
            if (pending == null || pending <= c.getSlots()) continue;
            if (pending <= SlotReservations.maxForAmendment(company, c, now)) continue;
            c.clearPendingSlots();
            lapsed.add(c);
            Faction hirer = c.getHirer();
            String hirerName = hirer == null ? "the hiring faction" : hirer.getName();
            tell(company.getLeader(), "§cThe slot change on your contract with "
                    + hirerName + " was dropped. The company no longer has room for it.");
            tellGovernment(hirer, "§c" + company.getName()
                    + " can no longer cover the extra slots it offered. The change was dropped.");
        }
        return lapsed;
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

    /** A slot change that was not accepted within a day releases its hold. */
    public List<MercenaryContract> tickAmendments(long now) {
        List<MercenaryContract> lapsed = new ArrayList<>();
        for (MercenaryContract c : new ArrayList<>(contracts.values())) {
            if (!c.takeExpiredSlotChange(now)) continue;
            lapsed.add(c);
            Faction hirer = c.getHirer();
            String hirerName = hirer == null ? "the hiring faction" : hirer.getName();
            tell(company.getLeader(), "§cThe slot change on your contract with "
                    + hirerName + " lapsed.");
            tellGovernment(hirer, "§cThe slot change from " + company.getName() + " lapsed.");
        }
        return lapsed;
    }

    private static void tell(String player, String message) {
        if (player == null || message == null || SimpleFactions.plugin == null) return;
        Player online = Bukkit.getPlayerExact(player);
        if (online != null) online.sendMessage(message);
    }

    private static void tellGovernment(Faction hirer, String message) {
        if (hirer == null || hirer.getGovernment() == null || SimpleFactions.plugin == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && hirer.getGovernment().isCouncilMember(online.getName())) {
                online.sendMessage(message);
            }
        }
    }
}
