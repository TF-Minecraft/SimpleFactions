package me.Plugins.SimpleFactions.mercenary.company;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Army.MilitaryExpansion;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Database.GuildBranchData;
import me.Plugins.SimpleFactions.Database.MercenaryCompanyData;
import me.Plugins.SimpleFactions.Database.MercenaryContractData;
import me.Plugins.SimpleFactions.Database.UpgradeExpansionData;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Guild.upgrade.UpgradeExpansion;
import me.Plugins.SimpleFactions.Loaders.CompanyUpgradeLoader;
import me.Plugins.SimpleFactions.Loaders.RegimentLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.contract.ContractHandler;
import me.Plugins.SimpleFactions.mercenary.contract.ContractStatus;
import me.Plugins.SimpleFactions.mercenary.contract.ContractTerminationService;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;
import me.Plugins.SimpleFactions.mercenary.contract.TerminationReason;

/**
 * A mercenary company owned by a guild. The company holds its own regiment, so
 * its manpower never shows up in a faction military, and its own upgrades, so
 * its buffs never reach {@link Guild#getModifier}.
 */
public class MercenaryCompany {
    public static final int MAX_QUEUED_SLOTS = 3;
    public static final int MAX_QUEUED_UPGRADES = 3;

    private final Guild guild;
    private String name;
    private List<String> bannerPatterns = new ArrayList<>();
    private final Regiment regiment;
    private int formationRemaining;
    private int reputation = 50;

    private final List<String> enlisted = new ArrayList<>();
    private final Map<String, Upgrade> upgrades = new LinkedHashMap<>();
    private final List<UpgradeExpansion> upgradeQueue = new ArrayList<>();
    private final List<MilitaryExpansion> slotQueue = new ArrayList<>();
    private final ContractHandler contractHandler = new ContractHandler(this);
    private final WageSettings wages = new WageSettings();

    /**
     * Wages owed but not yet settled, keyed by player name. Persisted because a
     * battle share accrues the moment a battle resolves, which can be many hours
     * before the daily tick that pays it out.
     */
    private final Map<String, Double> pendingWages = new LinkedHashMap<>();

    public MercenaryCompany(Guild guild, String name, Regiment regiment, int formationSeconds) {
        this.guild = guild;
        this.name = name;
        this.regiment = regiment;
        this.formationRemaining = Math.max(0, formationSeconds);
        this.bannerPatterns = guild != null ? new ArrayList<>(guild.getBannerPatterns()) : new ArrayList<>();
        if (regiment != null) {
            regiment.setCurrentSlots(formationRemaining > 0 ? 0 : 1);
        }
        loadUpgradePrototypes();
    }

    public MercenaryCompany(Guild guild, MercenaryCompanyData data, Regiment regiment) {
        this.guild = guild;
        this.regiment = regiment;
        this.name = data.name;
        this.bannerPatterns = data.banner != null ? new ArrayList<>(data.banner) : new ArrayList<>();
        this.formationRemaining = data.formationRemaining != null ? data.formationRemaining : 0;
        if (data.reputation != null) this.reputation = data.reputation;
        if (data.enlisted != null) this.enlisted.addAll(data.enlisted);
        if (regiment != null) {
            regiment.setCurrentSlots(parseSlots(data.slots));
        }
        loadUpgradePrototypes();
        if (data.upgrades != null) {
            for (GuildBranchData bd : data.upgrades) {
                if (bd == null || bd.id == null) continue;
                Upgrade base = CompanyUpgradeLoader.getByString(bd.id);
                if (base == null) continue;
                upgrades.put(base.getId(), new Upgrade(base, bd.level == null ? 0 : bd.level.intValue()));
            }
        }
        if (data.upgradeQueue != null) {
            for (UpgradeExpansionData ued : data.upgradeQueue) {
                if (ued == null || ued.upgrade == null) continue;
                Upgrade queued = getUpgrade(ued.upgrade);
                if (queued == null) continue;
                upgradeQueue.add(new UpgradeExpansion(queued, ued.timeLeft));
            }
        }
        if (data.contracts != null) {
            for (MercenaryContractData cd : data.contracts) {
                if (cd == null || cd.id == null) continue;
                contractHandler.add(new MercenaryContract(this, cd));
            }
        }
        if (data.activeWagePercent != null) wages.setActivePercent(data.activeWagePercent);
        if (data.peacetimeWagePerDay != null) wages.setPeacetimePerDay(data.peacetimeWagePerDay);
        if (data.activeWageOverrides != null) {
            for (Map.Entry<String, Double> e : data.activeWageOverrides.entrySet()) {
                wages.setActiveOverride(e.getKey(), e.getValue());
            }
        }
        if (data.peacetimeWageOverrides != null) {
            for (Map.Entry<String, Double> e : data.peacetimeWageOverrides.entrySet()) {
                wages.setPeacetimeOverride(e.getKey(), e.getValue());
            }
        }
        if (data.pendingWages != null) {
            for (Map.Entry<String, Double> e : data.pendingWages.entrySet()) {
                accrueWage(e.getKey(), e.getValue() == null ? 0 : e.getValue());
            }
        }
        if (data.slotQueue != null && regiment != null) {
            for (String entry : data.slotQueue) {
                if (entry == null) continue;
                String[] split = entry.split("\\.");
                if (split.length != 2) continue;
                try {
                    slotQueue.add(new MilitaryExpansion(regiment, Integer.parseInt(split[1])));
                } catch (NumberFormatException ignored) {
                    // A corrupt queue entry should not cost the company its whole save.
                }
            }
        }
    }

    /** Clone of the configured mercenary prototype, or null when the YAML is missing it. */
    public static Regiment cloneMercenaryRegiment() {
        Regiment prototype = RegimentLoader.getMercenaryRegiment();
        return prototype == null ? null : new Regiment(prototype);
    }

    private void loadUpgradePrototypes() {
        for (Upgrade u : CompanyUpgradeLoader.getList()) {
            if (!upgrades.containsKey(u.getId())) upgrades.put(u.getId(), new Upgrade(u, 0));
        }
    }

    private static int parseSlots(String slots) {
        if (slots == null) return 0;
        String[] split = slots.split("\\.");
        if (split.length != 2) return 0;
        try {
            return Math.max(0, Integer.parseInt(split[1]));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /* =====================================================
     * Identity
     * ===================================================== */

    public Guild getGuild() {
        return guild;
    }

    /** Leadership always follows the owning guild; it is never stored. */
    public String getLeader() {
        return guild == null ? null : guild.getLeader();
    }

    public boolean isLeader(String player) {
        String leader = getLeader();
        return leader != null && player != null && leader.equalsIgnoreCase(player);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getBannerPatterns() {
        return bannerPatterns;
    }

    public void setBannerPatterns(List<String> patterns) {
        this.bannerPatterns = patterns == null ? new ArrayList<>() : new ArrayList<>(patterns);
    }

    public int getReputation() {
        return reputation;
    }

    public void setReputation(int reputation) {
        this.reputation = clampReputation(reputation);
    }

    /** The one place reputation is clamped, mirroring {@code LoanHandler.changeCreditScore}. */
    public void changeReputation(int amount) {
        reputation = clampReputation(reputation + amount);
    }

    public String getReputationString() {
        return MercenaryReputationCalculator.display(reputation);
    }

    private static int clampReputation(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public Regiment getRegiment() {
        return regiment;
    }

    public ContractHandler getContractHandler() {
        return contractHandler;
    }

    /* =====================================================
     * Formation
     * ===================================================== */

    public boolean isForming() {
        return formationRemaining > 0;
    }

    public boolean isFormed() {
        return formationRemaining <= 0;
    }

    public int getFormationRemaining() {
        return formationRemaining;
    }

    /* =====================================================
     * Slots
     * ===================================================== */

    public int getSlots() {
        return regiment == null ? 0 : regiment.getCurrentSlots();
    }

    public int getFilledSlots() {
        return Math.min(enlisted.size(), getSlots());
    }

    public boolean hasFreeSlot() {
        return enlisted.size() < getSlots();
    }

    public List<MilitaryExpansion> getSlotQueue() {
        return slotQueue;
    }

    /**
     * Slots may only grow once every existing and queued slot has someone in it,
     * so a company cannot outrun its own recruiting.
     */
    public MercenaryResult canExpand() {
        if (regiment == null) {
            return MercenaryResult.deny("Mercenary companies are not configured on this server.");
        }
        if (isForming()) {
            return MercenaryResult.deny("Your company is still being founded.");
        }
        if (slotQueue.size() >= MAX_QUEUED_SLOTS) {
            return MercenaryResult.deny("The slot queue is full.");
        }
        if (enlisted.size() < getSlots() + slotQueue.size()) {
            return MercenaryResult.deny("Fill every slot before adding another.");
        }
        return MercenaryResult.ok("Slot queued.");
    }

    /** Null when expansion is allowed, otherwise the shared refusal text. */
    public String getExpansionBlockedReason() {
        MercenaryResult result = canExpand();
        return result.ok() ? null : result.message();
    }

    public MercenaryResult enqueueExpansion() {
        MercenaryResult result = canExpand();
        if (!result.ok()) return result;
        slotQueue.add(new MilitaryExpansion(regiment));
        return result;
    }

    public void addQueuedExpansion(int timeLeft) {
        if (regiment == null || slotQueue.size() >= MAX_QUEUED_SLOTS) return;
        slotQueue.add(new MilitaryExpansion(regiment, timeLeft));
    }

    public MercenaryResult adminAdjustSlots(int delta) {
        if (delta == 0) {
            return MercenaryResult.deny("Amount must be non-zero.");
        }
        if (regiment == null) {
            return MercenaryResult.deny("Mercenary companies are not configured on this server.");
        }
        if (isForming()) {
            return MercenaryResult.deny("Your company is still being founded.");
        }
        if (delta > 0) {
            for (int i = 0; i < delta; i++) {
                regiment.sizeIncrease();
            }
            return MercenaryResult.ok("Added " + delta + " slot" + (delta == 1 ? "" : "s") + ".");
        }
        int remove = -delta;
        if (getSlots() < remove) {
            return MercenaryResult.deny("Not enough slots to remove.");
        }
        for (int i = 0; i < remove; i++) {
            if (!dropSlot()) {
                return MercenaryResult.deny("Not enough slots to remove.");
            }
        }
        return MercenaryResult.ok("Removed " + remove + " slot" + (remove == 1 ? "" : "s") + ".");
    }

    /**
     * The only way a company loses a slot. Phase 3 hangs contract breach off
     * this, so every caller must come through here.
     */
    public boolean dropSlot() {
        if (regiment == null || getSlots() == 0) return false;
        regiment.sizeDecrease();
        while (enlisted.size() > getSlots()) {
            enlisted.remove(enlisted.size() - 1);
        }
        ContractTerminationService.checkSlotCommitments(this);
        return true;
    }

    /* =====================================================
     * Roster
     * ===================================================== */

    public List<String> getEnlisted() {
        return Collections.unmodifiableList(enlisted);
    }

    public boolean isEnlisted(String player) {
        for (String s : enlisted) {
            if (s.equalsIgnoreCase(player)) return true;
        }
        return false;
    }

    public boolean enlist(String player) {
        if (player == null || isEnlisted(player) || !hasFreeSlot()) return false;
        enlisted.add(player);
        return true;
    }

    /** Frees the slot, which in turn freezes expansion until it is filled again. */
    public boolean kick(String player) {
        for (int i = 0; i < enlisted.size(); i++) {
            if (enlisted.get(i).equalsIgnoreCase(player)) {
                enlisted.remove(i);
                // Every roster or slot change funnels through the one guarded check, so no
                // caller has to know which of the two can put the company under its promise.
                ContractTerminationService.checkSlotCommitments(this);
                return true;
            }
        }
        return false;
    }

    /* =====================================================
     * Upgrades
     * ===================================================== */

    public Upgrade getUpgrade(String id) {
        for (Upgrade u : upgrades.values()) {
            if (u.getId().equalsIgnoreCase(id)) return u;
        }
        return null;
    }

    public List<Upgrade> getUpgrades() {
        return new ArrayList<>(upgrades.values());
    }

    public List<UpgradeExpansion> getUpgradeQueue() {
        return upgradeQueue;
    }

    public boolean enqueueUpgrade(Upgrade u) {
        if (u == null || upgradeQueue.size() >= MAX_QUEUED_UPGRADES || u.isMaxed()) return false;
        upgradeQueue.add(new UpgradeExpansion(u));
        return true;
    }

    public void addQueuedUpgrade(Upgrade u, int time) {
        if (u == null || upgradeQueue.size() >= MAX_QUEUED_UPGRADES) return;
        upgradeQueue.add(new UpgradeExpansion(u, time));
    }

    public boolean cancelSlotQueue(int index) {
        if (index < 0 || index >= slotQueue.size()) {
            return false;
        }
        slotQueue.remove(index);
        return true;
    }

    public boolean cancelUpgradeQueue(int index) {
        if (index < 0 || index >= upgradeQueue.size()) {
            return false;
        }
        upgradeQueue.remove(index);
        return true;
    }

    /** Total of a company modifier across its upgrades; guild modifiers are untouched. */
    public double getModifier(GuildModifier modifier) {
        double amount = 0;
        for (Upgrade u : upgrades.values()) {
            if (u.getLevel() == 0) continue;
            amount += u.getAmount(modifier);
        }
        return amount;
    }

    /* =====================================================
     * Money
     * ===================================================== */

    public double getSlotUpkeep() {
        return getSlots() * Cache.mercenarySlotUpkeep;
    }

    public double getUpgradeUpkeep() {
        double total = 0;
        for (Upgrade u : upgrades.values()) {
            total += u.getTotalUpkeep();
        }
        return total;
    }

    public WageSettings getWageSettings() {
        return wages;
    }

    /**
     * What a day of payroll is expected to cost: a peacetime wage for every
     * enlisted player, plus the active day share for each slot actually under
     * contract. Projected rather than accrued, because this drives the burn
     * display before the money moves.
     */
    public double getWageUpkeep() {
        double total = 0;
        for (String player : enlisted) {
            total += wages.peacetimeFor(player);
        }
        for (MercenaryContract c : contractHandler.getActive()) {
            int covered = Math.min(c.getSlots(), enlisted.size());
            for (int i = 0; i < covered; i++) {
                total += wages.activeShareOf(c.getPricePerSlotPerDay(), enlisted.get(i));
            }
        }
        return total;
    }

    /* =====================================================
     * Payroll
     * ===================================================== */

    public void accrueWage(String player, double amount) {
        if (player == null || amount <= 0) return;
        pendingWages.merge(player, amount, Double::sum);
    }

    public Map<String, Double> getPendingWages() {
        return Collections.unmodifiableMap(pendingWages);
    }

    public void clearPendingWages() {
        pendingWages.clear();
    }

    public double getDailyBurn() {
        return getSlotUpkeep() + getUpgradeUpkeep() + getWageUpkeep();
    }

    /**
     * What a day of the current contracts is worth, so a leader can read burn
     * against income rather than in isolation. The day price only, because a battle
     * price depends on whether anyone declares war.
     */
    public double getContractIncome() {
        double total = 0;
        for (MercenaryContract c : contractHandler.getActive()) {
            total += c.getDailyPrice();
        }
        return total;
    }

    /** Income less burn. Negative means the company is eating its host guild. */
    public double getNetPosition() {
        return getContractIncome() - getDailyBurn();
    }

    /* =====================================================
     * Tick
     * ===================================================== */

    public void tick() {
        if (enforceCharacterGate()) return;
        if (formationRemaining > 0) {
            formationRemaining--;
            if (formationRemaining == 0 && regiment != null) {
                regiment.setCurrentSlots(1);
                chime(SFGUI.COMPANY_VIEW);
            }
            return;
        }
        tickSlotQueue();
        tickUpgradeQueue();
        if (!contractHandler.tickExpiry().isEmpty()) {
            chime(SFGUI.CONTRACT_LIST_VIEW);
        }
    }

    /**
     * Leader without the mercenary trait dissolves the company. Enlisted fighters
     * without it are kicked. Offline players and a missing character plugin are
     * left alone until they can be checked.
     *
     * @return true when this company was disbanded and the rest of the tick must stop
     */
    boolean enforceCharacterGate() {
        if (!MercenaryEligibility.isEnforced()) return false;
        if (MercenaryEligibility.check(getLeader()) == MercenaryEligibility.Status.INELIGIBLE) {
            String companyName = name;
            String leader = getLeader();
            List<String> roster = new ArrayList<>(enlisted);
            disband();
            tell(leader, "§c" + companyName
                    + " disbanded because your character has no mercenary trait.");
            for (String member : roster) {
                if (member.equalsIgnoreCase(leader)) continue;
                tell(member, "§c" + companyName + " has disbanded.");
            }
            return true;
        }
        for (String member : new ArrayList<>(enlisted)) {
            if (MercenaryEligibility.check(member) != MercenaryEligibility.Status.INELIGIBLE) {
                continue;
            }
            kick(member);
            tell(member, "§cYou were dismissed from " + name
                    + ". Your character has no mercenary trait.");
            tell(getLeader(), "§c" + member
                    + " was dismissed from the company. Their character has no mercenary trait.");
        }
        return false;
    }

    /**
     * Closes every live contract as a company-side breach, drops open offers, and
     * detaches from the host guild. Pending wages die with the company.
     */
    public void disband() {
        for (MercenaryContract contract : contractHandler.getActive()) {
            ContractTerminationService.terminate(contract, TerminationReason.COMPANY_DISBANDED);
        }
        for (MercenaryContract contract : contractHandler.getOffered()) {
            contract.finish(ContractStatus.TERMINATED);
        }
        enlisted.clear();
        if (guild != null && guild.getCompany() == this) {
            guild.setCompany(null);
        }
    }

    private static void tell(String player, String message) {
        if (player == null || message == null || SimpleFactions.plugin == null) return;
        Player online = Bukkit.getPlayerExact(player);
        if (online != null) online.sendMessage(message);
    }

    private void tickSlotQueue() {
        if (slotQueue.isEmpty()) return;
        MilitaryExpansion e = slotQueue.get(0);
        e.tick();
        if (e.getTimeLeft() != 0) return;
        slotQueue.remove(0);
        if (regiment != null) regiment.sizeIncrease();
        chime(SFGUI.COMPANY_SLOTS_VIEW);
    }

    private void tickUpgradeQueue() {
        if (upgradeQueue.isEmpty()) return;
        UpgradeExpansion e = upgradeQueue.get(0);
        e.tick();
        if (e.getTimeLeft() != 0) return;
        upgradeQueue.remove(0);
        e.getUpgrade().levelUp();
        chime(SFGUI.COMPANY_UPGRADE_VIEW);
    }

    private void chime(SFGUI screen) {
        if (SimpleFactions.plugin == null) return;
        InventoryManager inv = FactionManager.getInv();
        if (inv == null) return;
        inv.getUpdater().inventorySound("minecraft:block.note_block.chime", screen);
    }

    /* =====================================================
     * Persistence
     * ===================================================== */

    public MercenaryCompanyData serialize() {
        MercenaryCompanyData data = new MercenaryCompanyData();
        data.name = name;
        data.banner = new ArrayList<>(bannerPatterns);
        data.formationRemaining = formationRemaining;
        data.reputation = reputation;
        data.enlisted = new ArrayList<>(enlisted);
        if (regiment != null) {
            data.slots = regiment.getId() + "." + regiment.getCurrentSlots();
            for (MilitaryExpansion e : slotQueue) {
                data.slotQueue.add(regiment.getId() + "." + e.getTimeLeft());
            }
        }
        for (Upgrade u : upgrades.values()) {
            GuildBranchData bd = new GuildBranchData();
            bd.id = u.getId();
            bd.level = u.getLevel();
            data.upgrades.add(bd);
        }
        for (UpgradeExpansion e : upgradeQueue) {
            UpgradeExpansionData ued = new UpgradeExpansionData();
            ued.upgrade = e.getUpgrade().getId();
            ued.timeLeft = e.getTimeLeft();
            data.upgradeQueue.add(ued);
        }
        for (MercenaryContract c : contractHandler.getAll()) {
            data.contracts.add(c.serialize());
        }
        data.activeWagePercent = wages.getActivePercent();
        data.peacetimeWagePerDay = wages.getPeacetimePerDay();
        data.activeWageOverrides = new LinkedHashMap<>(wages.getActiveOverrides());
        data.peacetimeWageOverrides = new LinkedHashMap<>(wages.getPeacetimeOverrides());
        data.pendingWages = new LinkedHashMap<>(pendingWages);
        return data;
    }
}
