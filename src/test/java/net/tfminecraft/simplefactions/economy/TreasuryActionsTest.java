package net.tfminecraft.simplefactions.economy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.denareconomy.api.TaxLeg;
import net.tfminecraft.denareconomy.api.TaxPolicy;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.GuildBankGrant;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.mercenary.company.CompanyFixture;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.TestTreasuries;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

class TreasuryActionsTest {
    @TempDir Path folder;
    FactionDomainFixture fixture;
    Guild guild;
    Player actor;
    MockedConstruction<Database> saves;
    final AtomicBoolean save = new AtomicBoolean(true);
    @BeforeEach void setup() throws IOException {
        fixture = new FactionDomainFixture();
        guild = fixture.saved("realm", "Leader").getOrCreateMainGuild();
        actor = fixture.player("Leader");
        TestTreasuries.set(guild.getBank(), 100);
        TestTreasuries.set(Denars.pouch(Members.session(actor).orElseThrow().ref()), 5000);
        TreasuryActions.recover(folder);
        saves = mockConstruction(Database.class, (database, context) ->
                when(database.saveFactionDurably(any())).thenAnswer(call -> save.get()));
    }
    @AfterEach void close() { if (saves != null) saves.close(); if (fixture != null) fixture.close(); }

    @Test void depositAndWithdrawalUseCapturedCharacterAndGuildAccounts() {
        assertTrue(TreasuryActions.bank(actor, guild, 12.34, true));
        assertEquals(112.34, guild.getBank().getWealth());
        assertEquals(37.66, TreasuryActions.pouch(actor));
        assertEquals(12.34, guild.getLedger().getHistory().getDepositsToday().get("Leader"));
        assertTrue(TreasuryActions.bank(actor, guild, 2.34, false));
        assertEquals(110, guild.getBank().getWealth());
        assertEquals(40, TreasuryActions.pouch(actor));
        assertEquals(12.34, guild.getLedger().getHistory().getDepositsToday().get("Leader"));
    }
    @Test void otherCharacterOnAccountCannotWithdrawLeadersTreasury() {
        Player other = fixture.player("Other");
        var account = actor.getUniqueId();
        when(other.getUniqueId()).thenReturn(account);
        assertFalse(TreasuryActions.bank(other, guild, 10, false));
        assertFalse(TreasuryActions.bank(other, guild, 10, true));
        assertEquals(100, guild.getBank().getWealth());
    }
    @Test void grantRecordsActualAmountDurably() {
        assertTrue(GuildBankGrant.grant(guild, 2.75));
        assertEquals(102.75, guild.getBank().getWealth());
        assertEquals(2.75, guild.getLedger().getHistory().getDepositsToday().get("Compensation"));
        verify(saves.constructed().getFirst()).saveFactionDurably(guild.getFaction());
    }
    @Test void noOverdraftOrHistoryOnRefusedDeposit() {
        assertFalse(TreasuryActions.bank(actor, guild, 60, true));
        assertEquals(100, guild.getBank().getWealth());
        assertEquals(50, TreasuryActions.pouch(actor));
        assertTrue(guild.getLedger().getHistory().getDepositsToday().isEmpty());
        assertTrue(EconomyRuntime.ready());
    }
    @Test void pendingSaveDisablesNewActionsAndRecoversExactHistoryOnce() throws IOException {
        save.set(false);
        assertFalse(TreasuryActions.bank(actor, guild, 10, true));
        assertEquals(110, guild.getBank().getWealth());
        assertEquals(40, TreasuryActions.pouch(actor));
        assertFalse(TreasuryActions.bank(actor, guild, 10, true));
        assertEquals(110, guild.getBank().getWealth());
        save.set(true);
        TreasuryActions.recover(folder);
        EconomyRuntime.recovered();
        assertEquals(10, guild.getLedger().getHistory().getDepositsToday().get("Leader"));
        TreasuryActions.recover(folder);
        assertEquals(110, guild.getBank().getWealth());
    }
    @Test void completedIntentDoesNotOverwriteLaterDomainChanges() throws IOException {
        String id = TreasuryActions.newId();
        var plan = new TreasuryActions.Plan(id, "grant", actor, guild).transfer(Denars.MINT, TreasuryActions.account(guild), 100);
        TreasuryActions.history(plan, guild, "Leader", 1);
        assertTrue(plan.commit(actor));
        guild.getLedger().getHistory().getDepositsToday().put("Leader", 25d);
        assertEquals(Boolean.TRUE, TreasuryActions.resume(id, actor));
        TreasuryActions.recover(folder);
        assertEquals(25, guild.getLedger().getHistory().getDepositsToday().get("Leader"));
        assertEquals(101, guild.getBank().getWealth());
    }
    @Test void driftCannotOverwriteHistoryOnRecovery() {
        save.set(false);
        assertFalse(TreasuryActions.bank(actor, guild, 10, true));
        guild.getLedger().getHistory().getDepositsToday().put("Leader", 22d);
        save.set(true);
        assertThrows(IOException.class, () -> TreasuryActions.recover(folder));
        assertEquals(22, guild.getLedger().getHistory().getDepositsToday().get("Leader"));
        assertEquals(110, guild.getBank().getWealth());
    }
    @Test void reusedGuildIdCannotReceivePendingDomainEffects() {
        save.set(false);
        assertFalse(TreasuryActions.bank(actor, guild, 10, true));
        Guild replacement = mock(Guild.class);
        when(replacement.getIncarnationId()).thenReturn(UUID.randomUUID());
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getGuildByString(guild.getId())).thenReturn(replacement);
            assertThrows(IOException.class, () -> TreasuryActions.recover(folder));
        }
        verify(replacement, never()).getLedger();
    }
    @Test void unavailableCreatesNoIntentOrEffect() throws IOException {
        when(fixture.ui.plugin.isStarted()).thenReturn(false);
        assertFalse(TreasuryActions.bank(actor, guild, 10, true));
        assertFalse(GuildBankGrant.grant(guild, 10));
        assertFalse(new TreasuryActions.Plan("wait", "wait", actor, guild)
                .transfer(Denars.MINT, TreasuryActions.account(guild), 1).commit(actor));
        assertEquals(Boolean.FALSE, TreasuryActions.resume("wait", actor));
        try (var files = Files.list(folder)) { assertEquals(0, files.count()); }
        assertEquals(100, guild.getBank().getWealth());
    }
    @Test void inabilityToWriteIntentPostsNothing() throws IOException {
        Path file = folder.resolve("not-directory"); Files.writeString(file, "blocked");
        assertThrows(IOException.class, () -> TreasuryActions.recover(file));
        assertFalse(TreasuryActions.bank(actor, guild, 10, true));
        assertEquals(100, guild.getBank().getWealth());
        assertEquals(50, TreasuryActions.pouch(actor));
    }
    @Test void alteredOperationPayloadFailsClosedWithoutSecondPayment() {
        String id = TreasuryActions.newId();
        assertTrue(new TreasuryActions.Plan(id, "same", actor, guild).transfer(Denars.MINT, TreasuryActions.account(guild), 100).commit(actor));
        assertFalse(new TreasuryActions.Plan(id, "same", actor, guild).transfer(Denars.MINT, TreasuryActions.account(guild), 200).commit(actor));
        assertEquals(101, guild.getBank().getWealth());
        assertFalse(EconomyRuntime.ready());
    }
    @Test void loanAcceptanceAndClampedRepaymentMoveActualFundsOnce() {
        Guild borrower = fixture.guild(guild.getFaction(), "borrower", "Borrower");
        Player debtor = fixture.player("Borrower");
        Loan loan = new Loan("offer", 25, guild, borrower, System.currentTimeMillis(), 10, 0, 0, false);
        assertTrue(TreasuryActions.acceptLoan(debtor, loan, "offer", System.currentTimeMillis() + 60000));
        assertFalse(TreasuryActions.acceptLoan(debtor, loan, "offer", System.currentTimeMillis() + 60000));
        assertEquals(75, guild.getBank().getWealth()); assertEquals(25, borrower.getBank().getWealth());
        Loan accepted = guild.getLoanHandler().getLoanById("offer");
        assertEquals(25, TreasuryActions.repay(debtor, accepted, 100, "repay"));
        assertTrue(accepted.isPaidOff());
        assertEquals(100, guild.getBank().getWealth()); assertEquals(0, borrower.getBank().getWealth());
        assertEquals(-1, TreasuryActions.repay(debtor, accepted, 100, "repay"));
        assertEquals(0, guild.getLedger().getHistory().getDepositsToday().size());
    }
    @Test void companyFormationSnapshotsRosterWithoutEarlyMutation() {
        CompanyFixture.installMercenaryPrototype();
        try {
            MercenaryCompany company = new MercenaryCompany(guild, "Company", CompanyFixture.companyRegiment(), 0);
            company.enlistLeader();
            assertTrue(TreasuryActions.formation(guild, "Leader", company, 10));
            assertEquals(90, guild.getBank().getWealth());
            assertTrue(guild.getCompany().isEnlisted("Leader"));
        } finally { CompanyFixture.clearRegiments(); }
    }
    @ParameterizedTest @ValueSource(longs = {0, 125, 875})
    void materialWithholdingAndActualDestroyedItemsCommitTogether(long tax) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT, 7);
        actor.getInventory().setItem(0, item);
        var ref = Members.session(actor).orElseThrow().ref();
        String holding = Denars.guildTax(guild.getIncarnationId());
        TaxPolicy policy = (earner, gross) -> tax == 0 ? Optional.empty() : Optional.of(new TaxLeg(holding, tax));
        Bukkit.getServicesManager().register(TaxPolicy.class, policy, fixture.ui.plugin, ServicePriority.Normal);
        assertTrue(TreasuryPhysical.material(actor, ref, item, 8.75, guild));
        assertEquals(0, item.getAmount());
        assertEquals(875 - tax, Denars.balance(Denars.bank(ref)));
        assertEquals(tax, Denars.balance(holding));
    }
    @Test void physicalFailureAfterPostingIsRetainedAndNeverDeliveredOnRestart() throws IOException {
        String id = TreasuryActions.newId();
        JsonObject snapshot = new JsonObject(); snapshot.addProperty("item", "captured");
        assertFalse(new TreasuryActions.Plan(id, "physical", actor, guild)
                .transfer(TreasuryActions.account(guild), Denars.MINT, 100)
                .physical(snapshot, intent -> { throw new IOException("ambiguous delivery"); }).commit(actor));
        assertEquals(Result.APPLIED, Denars.status(id).orElseThrow());
        assertEquals(99, guild.getBank().getWealth());
        assertThrows(IOException.class, () -> TreasuryActions.recover(folder));
        assertEquals(99, guild.getBank().getWealth());
        try (var files = Files.list(folder)) { assertEquals(1, files.count()); }
    }
    @Test void refusedResumeIsTerminalAndUnavailableResumeFailsClosed() {
        var ref = Members.session(actor).orElseThrow().ref();
        assertFalse(new TreasuryActions.Plan("refused", "too much", actor, guild)
                .transfer(Denars.pouch(ref), TreasuryActions.account(guild), 6000).commit(actor));
        assertEquals(Boolean.FALSE, TreasuryActions.resume("refused", actor));
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.UNAVAILABLE);
            assertFalse(new TreasuryActions.Plan("pending", "wait", actor, guild)
                    .transfer(Denars.MINT, TreasuryActions.account(guild), 100).commit(actor));
            EconomyRuntime.recovered();
            assertEquals(Boolean.FALSE, TreasuryActions.resume("pending", actor));
            assertFalse(EconomyRuntime.ready());
        }
        assertEquals(100, guild.getBank().getWealth());
    }
    @Test void unrecognizedEffectRetainsPaymentForReviewAndRawPrepassDoesNotApplyOtherEffects() throws IOException {
        assertFalse(new TreasuryActions.Plan("unknown", "unsupported", actor, guild)
                .transfer(Denars.MINT, TreasuryActions.account(guild), 100)
                .effect("unsupported", guild, "", null, "future").commit(actor));
        assertEquals(101, guild.getBank().getWealth());
        TreasuryActions.recoverTopologyFiles(folder);
        assertThrows(IOException.class, () -> TreasuryActions.recover(folder));
        assertEquals(101, guild.getBank().getWealth());
    }
    @Test void capturedSessionCannotBeUsedAfterCharacterSwitch() throws IOException {
        var identity = net.tfminecraft.simplefactions.managers.inventory.CharacterKeyFixture.install();
        identity.play(actor, "Original");
        var plan = new TreasuryActions.Plan("captured", "captured", actor, guild)
                .transfer(Denars.MINT, TreasuryActions.account(guild), 100);
        identity.play(actor, "Replacement");
        assertFalse(plan.commit(actor));
        assertEquals(100, guild.getBank().getWealth());
        try (var files = Files.list(folder)) { assertEquals(0, files.count()); }
    }
    @Test void branchChangesAndRefundsUpdateLevelMoneyAndCachedWealthTogether() {
        var branch = guild.getBranch(0);
        int old = branch.getLevel();
        assertTrue(TreasuryActions.branch(actor, guild, branch, true, "upgrade"));
        assertEquals(old + 1, branch.getLevel());
        assertEquals(0, guild.getBank().getWealth());
        assertTrue(TreasuryActions.branch(actor, guild, branch, false, "downgrade"));
        assertEquals(old, branch.getLevel());
        assertEquals(80, guild.getBank().getWealth());
        assertEquals(80, guild.getWealth());
    }
    @Test void materialChangingDuringPostingIsQuarantinedWithoutDestroyingReplacement() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT, 7);
        actor.getInventory().setItem(0, item);
        var ref = Members.session(actor).orElseThrow().ref();
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenAnswer(call -> {
                Result posted = (Result) call.callRealMethod();
                item.setAmount(8);
                return posted;
            });
            assertFalse(TreasuryPhysical.material(actor, ref, item, 8.75, guild));
        }
        assertEquals(8, item.getAmount());
        assertEquals(875, Denars.balance(Denars.bank(ref)));
        assertThrows(IOException.class, () -> TreasuryActions.recover(folder));
    }
    @Test void withdrawalInventoryChangingDuringPostingNeverDeliversTwice() {
        when(actor.getWorld()).thenReturn(fixture.ui.world);
        when(fixture.ui.world.getUID()).thenReturn(UUID.randomUUID());
        ItemStack delivered = new ItemStack(Material.GOLD_NUGGET, 1);
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenAnswer(call -> {
                Result posted = (Result) call.callRealMethod();
                actor.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 1));
                return posted;
            });
            assertFalse(TreasuryPhysical.withdraw(actor, guild, 1, List.of(delivered)));
        }
        assertEquals(99, guild.getBank().getWealth());
        assertEquals(Material.DIAMOND, actor.getInventory().getItem(0).getType());
        verify(actor.getInventory(), never()).addItem(any(ItemStack.class));
        assertThrows(IOException.class, () -> TreasuryActions.recover(folder));
    }

}
