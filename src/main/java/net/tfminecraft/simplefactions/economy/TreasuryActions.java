package net.tfminecraft.simplefactions.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.LoanData;
import net.tfminecraft.simplefactions.database.MercenaryCompanyData;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.character.CharacterSession;
import org.bukkit.entity.Player;

/** Durable treasury postings with captured ownership and exact, replayable domain changes. */
public final class TreasuryActions {
    private static OperationJournal journal = new OperationJournal(Path.of("plugins/SimpleFactions/economy/callers"));
    private TreasuryActions() {}

    public static void recoverTopologyFiles(Path folder) throws IOException {
        new OperationJournal(folder).recoverAppliedEffects(intent -> {
            for (JsonElement element : intent.changes().getAsJsonArray("effects")) {
                JsonObject effect = element.getAsJsonObject();
                if ("topology".equals(effect.get("kind").getAsString())) TreasuryTopology.restoreFiles(effect);
            }
        });
    }
    public static void recover(Path folder) throws IOException {
        journal = new OperationJournal(folder);
        journal.recover(TreasuryActions::apply);
    }

    public static final class Plan {
        private final JsonObject changes = new JsonObject();
        private final JsonArray effects = new JsonArray();
        private final JsonArray guilds = new JsonArray();
        private final List<Leg> legs = new ArrayList<>();
        private final CharacterSession session;
        private final String id;
        private final String reason;
        private OperationJournal.Effects physical;
        public Plan(String id, String reason, Player actor, Guild... participants) {
            this.id = id;
            this.reason = reason;
            session = actor == null ? null : Members.session(actor).orElseThrow(() -> new IllegalStateException("Character unavailable"));
            changes.addProperty("schema", 1);
            changes.add("actor", JsonUtil.GSON.toJsonTree(session));
            changes.add("guilds", guilds);
            changes.add("effects", effects);
            for (Guild guild : participants) capture(guild);
        }
        public Plan capture(Guild guild) {
            JsonObject ref = new JsonObject();
            ref.addProperty("id", guild.getId());
            ref.addProperty("incarnation", guild.getIncarnationId().toString());
            if (!guilds.contains(ref)) guilds.add(ref);
            return this;
        }
        public Plan transfer(String from, String to, long cents) {
            if (cents < 0) throw new IllegalArgumentException("Negative transfer");
            legs.add(new Leg(from, -cents));
            legs.add(new Leg(to, cents));
            return this;
        }
        public Plan effect(String kind, Guild guild, String key, Object before, Object after) {
            JsonObject effect = new JsonObject();
            effect.addProperty("kind", kind);
            effect.addProperty("guild", guild.getId());
            effect.addProperty("key", key);
            effect.add("before", JsonUtil.GSON.toJsonTree(before));
            effect.add("after", JsonUtil.GSON.toJsonTree(after));
            effects.add(effect);
            return this;
        }
        public Plan physical(JsonObject snapshot, OperationJournal.Effects delivery) {
            changes.add("physical", snapshot.deepCopy());
            physical = delivery;
            return this;
        }
        public boolean commit(Player actor) {
            if (!EconomyRuntime.ready() || session != null && !Members.isCurrent(session)) return unavailable(actor);
            try {
                Result result = journal.execute(new OperationJournal.Intent(id, reason, legs, changes), intent -> {
                    apply(intent, physical);
                });
                if (result == Result.APPLIED) return true;
                if (result == Result.REFUSED) {
                    if (actor != null) actor.sendMessage("§cThe treasury could not fund this action.");
                    return false;
                }
                throw new IOException("Treasury intent " + id + " remains " + result);
            } catch (IOException | RuntimeException error) {
                EconomyRuntime.failClosed(error);
                if (actor != null) actor.sendMessage("§cTreasury action pending recovery: " + id);
                return false;
            }
        }
    }

    public static Boolean resume(String id, Player actor) {
        if (!EconomyRuntime.ready()) return unavailable(actor);
        try {
            var result = journal.resume(id, TreasuryActions::apply);
            if (result.isEmpty()) return null;
            if (result.get() == Result.APPLIED) return true;
            if (result.get() == Result.REFUSED) return false;
            throw new IOException("Treasury intent " + id + " remains " + result.get());
        } catch (IOException | RuntimeException error) {
            EconomyRuntime.failClosed(error);
            if (actor != null) actor.sendMessage("§cTreasury action pending recovery: " + id);
            return false;
        }
    }

    public static String newId() { return "sf:treasury:" + UUID.randomUUID(); }
    public static String account(Guild guild) { return Denars.guild(guild.getIncarnationId()); }
    private static boolean unavailable(Player actor) {
        if (actor != null) actor.sendMessage("§cThe treasury is not ready. Please try again later.");
        return false;
    }
    public static double pouch(Player actor) {
        return Members.session(actor).map(s -> Money.denars(Denars.balance(Denars.pouch(s.ref())))).orElse(0d);
    }
    public static boolean bank(Player actor, Guild guild, double amount, boolean deposit) {
        if (!EconomyRuntime.ready()) return unavailable(actor);
        CharacterSession session = Members.session(actor).orElse(null);
        if (session == null) return unavailable(actor);
        if (deposit ? !(guild.isBase() ? guild.getFaction().isMember(actor) : guild.isMember(actor)) : !guild.isLeader(actor)) return false;
        String pouch = Denars.pouch(session.ref());
        Plan plan = new Plan(newId(), deposit ? "Guild deposit" : "Guild withdrawal", actor, guild);
        plan.transfer(deposit ? pouch : account(guild), deposit ? account(guild) : pouch, Money.cents(amount));
        if (deposit) history(plan, guild, Members.key(session.ref()), amount);
        return plan.commit(actor);
    }
    public static void history(Plan plan, Guild guild, String key, double amount) {
        Double before = guild.getLedger().getHistory().getDepositsToday().get(key);
        plan.effect("history", guild, key, before, (before == null ? 0 : before) + amount);
    }
    public static boolean grant(Guild guild, double amount) {
        if (!EconomyRuntime.ready()) return false;
        Plan plan = new Plan(newId(), "Staff guild grant", null, guild).transfer(Denars.MINT, account(guild), Money.cents(amount));
        history(plan, guild, "Compensation", amount);
        return plan.commit(null);
    }
    public static boolean branch(Player actor, Guild guild, Branch branch, boolean upgrade, String id) {
        if (!EconomyRuntime.ready() || !guild.isLeader(actor)) return unavailable(actor);
        if (!upgrade && branch.getLevel() == 0) return false;
        double amount = upgrade ? guild.getExpansionCost() : guild.getRefund();
        if (!Double.isFinite(amount) || amount < 0 || upgrade && guild.getBank().getWealth() < amount) {
            actor.sendMessage("§cCannot afford to upgrade");
            return false;
        }
        return new Plan(id, "Guild branch level", actor, guild)
                .transfer(upgrade ? account(guild) : Denars.MINT, upgrade ? Denars.MINT : account(guild), Money.cents(amount))
                .effect("branch", guild, branch.getId(), branch.getLevel(), branch.getLevel() + (upgrade ? 1 : -1)).commit(actor);
    }
    public static boolean acceptLoan(Player actor, Loan loan, String offer, long expires) {
        Guild lender = loan.getIssuer(), borrower = loan.getBorrower();
        if (!EconomyRuntime.ready() || !borrower.isLeader(actor)) return unavailable(actor);
        if (lender.getLoanHandler().getUsedOffers().containsKey(offer) || lender.getLoanHandler().getLoanById(offer) != null) return false;
        return new Plan("sf:loan-offer:" + lender.getIncarnationId() + ":" + offer, "Accept guild loan", actor, lender, borrower)
                .transfer(account(lender), account(borrower), Money.cents(loan.getAmount()))
                .effect("loan", lender, loan.getId(), null, new LoanData(loan))
                .effect("offer", lender, offer, null, expires).commit(actor);
    }
    public static double repay(Player actor, Loan loan, double amount, String operationId) {
        Guild borrower = loan.getBorrower(), lender = loan.getIssuer();
        if (!EconomyRuntime.ready() || !borrower.isLeader(actor)) { unavailable(actor); return -1; }
        int beforeCredit = borrower.getLoanHandler().getCreditScore();
        int[] afterCredit = { beforeCredit };
        Loan planned = loan.copyForSettlement(System.currentTimeMillis(), delta -> afterCredit[0] = Math.max(0, Math.min(100, afterCredit[0] + delta)));
        double effective = planned.makePayment(Math.min(amount, loan.getTotalOwed()), false);
        if (effective <= 0 || borrower.getBank().getWealth() < effective) return -1;
        Plan plan = new Plan(operationId, "Guild loan repayment", actor, borrower, lender)
                .transfer(account(borrower), account(lender), Money.cents(effective))
                .effect("loan", lender, loan.getId(), new LoanData(loan), new LoanData(planned))
                .effect("credit", borrower, "", beforeCredit, afterCredit[0]);
        return plan.commit(actor) ? effective : -1;
    }
    public static boolean formation(Guild guild, String member, MercenaryCompany company, double amount) {
        Player actor = Members.player(member);
        if (actor == null || !guild.isLeader(actor) || !EconomyRuntime.ready()) return false;
        Plan plan = new Plan(newId(), "Mercenary company formation", actor, guild)
                .transfer(account(guild), Denars.MINT, Money.cents(amount));
        plan.effect("company", guild, "", null, company.serialize());
        return plan.commit(actor);
    }

    private static void apply(OperationJournal.Intent intent) throws IOException { apply(intent, null); }
    private static void apply(OperationJournal.Intent intent, OperationJournal.Effects physical) throws IOException {
        JsonObject changes = intent.changes();
        if (changes.get("schema").getAsInt() != 1) throw new IOException("Unsupported treasury schema");
        List<Guild> participants = new ArrayList<>();
        for (JsonElement element : changes.getAsJsonArray("guilds")) {
            JsonObject ref = element.getAsJsonObject();
            Guild guild = FactionManager.getGuildByString(ref.get("id").getAsString());
            if (guild == null || !guild.getIncarnationId().toString().equals(ref.get("incarnation").getAsString()))
                throw new IOException("Guild incarnation unavailable: " + ref);
            participants.add(guild);
        }
        if (changes.has("physical") && physical == null)
            throw new IOException("Physical treasury delivery requires staff reconciliation: " + intent.id());
        LinkedHashSet<Faction> dirty = new LinkedHashSet<>();
        LinkedHashSet<Guild> changedBranches = new LinkedHashSet<>();
        for (JsonElement element : changes.getAsJsonArray("effects")) {
            JsonObject e = element.getAsJsonObject();
            Guild guild = FactionManager.getGuildByString(e.get("guild").getAsString());
            String kind = e.get("kind").getAsString(), key = e.get("key").getAsString();
            JsonElement before = e.get("before"), after = e.get("after");
            switch (kind) {
                case "history" -> {
                    var deposits = guild.getLedger().getHistory().getDepositsToday();
                    check(deposits.get(key), before, after);
                    deposits.put(key, after.getAsDouble());
                }
                case "branch" -> {
                    Branch branch = guild.getBranch(key);
                    if (branch == null) throw new IOException("Missing branch " + key);
                    check(branch.getLevel(), before, after);
                    while (branch.getLevel() < after.getAsInt()) branch.levelUp();
                    while (branch.getLevel() > after.getAsInt()) branch.levelDown();
                    changedBranches.add(guild);
                }
                case "loan" -> {
                    Loan existing = guild.getLoanHandler().getLoanById(key);
                    check(existing == null ? null : new LoanData(existing), before, after);
                    LoanData data = JsonUtil.GSON.fromJson(after, LoanData.class);
                    if (existing == null) guild.getLoanHandler().issueLoan(new Loan(data));
                    else existing.restoreSettlement(data);
                }
                case "offer" -> {
                    // Expired markers may have been pruned; the captured loan still prevents a second acceptance.
                    Long current = guild.getLoanHandler().getUsedOffers().get(key);
                    if (current != null) check(current, before, after);
                    guild.getLoanHandler().restoreUsedOffers(java.util.Map.of(key, after.getAsLong()));
                }
                case "credit" -> {
                    check(guild.getLoanHandler().getCreditScore(), before, after);
                    guild.getLoanHandler().restoreCreditScore(after.getAsInt());
                }
                case "company" -> {
                    check(guild.getCompany() == null ? null : guild.getCompany().serialize(), before, after);
                    if (guild.getCompany() == null) guild.setCompany(new MercenaryCompany(guild,
                            JsonUtil.GSON.fromJson(after, MercenaryCompanyData.class), MercenaryCompany.cloneMercenaryRegiment()));
                }
                case "pillage" -> {
                    check(guild.getPillageHits(), before, after);
                    guild.getPillageHits().clear();
                    for (JsonElement hit : after.getAsJsonArray()) guild.getPillageHits().add(JsonUtil.GSON.fromJson(hit, StabilityModifier.class));
                }
                case "topology" -> TreasuryTopology.apply(e);
                default -> throw new IOException("Unknown treasury effect " + kind);
            }
            dirty.add(guild.getFaction());
        }
        if (physical != null) physical.apply(intent);
        for (Guild guild : participants) if (changedBranches.contains(guild)
                || intent.legs().stream().anyMatch(leg -> leg.cents() != 0 && account(guild).equals(leg.account()))) guild.updateWealth();
        for (Faction faction : dirty) if (!new Database().saveFactionDurably(faction))
            throw new IOException("Cannot save treasury effects for " + faction.getId());
    }
    public static void check(Object current, JsonElement before, JsonElement after) throws IOException {
        JsonElement value = JsonUtil.GSON.toJsonTree(current);
        if (!value.equals(before) && !value.equals(after)) throw new IOException("Treasury effect state changed: " + value);
    }
}
