package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.AgreementResult;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.HubNotice;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.PendingMatter;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService.RateRange;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.guild.income.LedgerHistory;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;

class HubAgreementServiceTest {
    @AfterEach
    void restoreCap() {
        Cache.supplyHubMaxTax = 50;
    }

    @Test
    void offerFlowBuildsTheHubAtTheLastTermsAndRefusesTheWrongActor() {
        Facts facts = new Facts();
        Guild guild = guild();
        long start = 1_000L;

        AgreementResult stranger = HubAgreementService.propose(
                guild, "Stranger", "host", "port", 10, 1.0, facts, start);
        assertFalse(stranger.succeeded());
        assertTrue(guild.getHubOffers().isEmpty());

        AgreementResult proposed = HubAgreementService.propose(
                guild, "Leader", "host", "port", 10, 1.0, facts, start);
        assertTrue(proposed.succeeded());
        assertEquals(OfferSide.HOST, guild.getHubOffers().get(0).awaiting());
        assertEquals(10, guild.getHubOffers().get(0).taxRatePercent());

        AgreementResult second = HubAgreementService.propose(
                guild, "Leader", "host", "port", 11, 1.0, facts, start);
        assertFalse(second.succeeded());
        assertEquals(1, guild.getHubOffers().size());

        AgreementResult outsider = HubAgreementService.counter(
                guild, "Stranger", "host", "port", 12, 2.0, facts, start + 1);
        assertFalse(outsider.succeeded());
        assertEquals(10, guild.getHubOffers().get(0).taxRatePercent());

        AgreementResult hostCounter = HubAgreementService.counter(
                guild, "Council", "host", "port", 12, 2.0, facts, start + 2);
        assertTrue(hostCounter.succeeded());
        assertEquals(OfferSide.GUILD, guild.getHubOffers().get(0).awaiting());

        AgreementResult guildCounter = HubAgreementService.counter(
                guild, "Leader", "host", "port", 15, 3.5, facts, start + 3);
        assertTrue(guildCounter.succeeded());
        assertEquals(OfferSide.HOST, guild.getHubOffers().get(0).awaiting());
        assertEquals(15, guild.getHubOffers().get(0).taxRatePercent());

        AgreementResult own = HubAgreementService.accept(guild, "Leader", "host", "port", facts, start + 4);
        assertFalse(own.succeeded());
        assertTrue(own.message().contains("other side"));
        assertTrue(guild.getSupplyHubs().isEmpty());
        assertEquals(1, guild.getHubOffers().size());

        AgreementResult accepted = HubAgreementService.accept(guild, "Council", "host", "port", facts, start + 5);
        assertTrue(accepted.succeeded());
        assertTrue(accepted.builtHub());
        assertTrue(guild.getHubOffers().isEmpty());
        assertEquals(1, guild.getSupplyHubs().size());
        assertEquals("host", guild.getSupplyHubs().get(0).ownerFactionId());
        assertEquals("port", guild.getSupplyHubs().get(0).installationId());
        HubAgreement agreement = HubAgreementService.findAgreement(guild, "host", "port");
        assertNotNull(agreement);
        assertEquals(15, agreement.taxRatePercent());
        assertEquals(350, agreement.feeCents());
        assertEquals(14, agreement.daysRemaining());
        assertTrue(agreement.guildRenews());
        assertTrue(agreement.hostRenews());
    }

    @Test
    void lapsedOfferIsRefusedAndTheTickDropsIt() {
        Facts facts = new Facts();
        Guild guild = guild();
        HubAgreementService.propose(guild, "Leader", "host", "port", 10, 1.0, facts, 0L);
        long lapsedAt = HubAgreementService.DAY_MILLIS * 7;

        AgreementResult refused = HubAgreementService.accept(guild, "Council", "host", "port", facts, lapsedAt);
        assertFalse(refused.succeeded());
        assertTrue(refused.message().toLowerCase(Locale.ROOT).contains("lapsed"));
        assertTrue(guild.getHubOffers().isEmpty());

        HubAgreementService.propose(guild, "Leader", "host", "port", 10, 1.0, facts, 0L);
        assertEquals(1, guild.getHubOffers().size());
        HubAgreementService.tick(List.of(guild), lapsedAt, facts);
        assertTrue(guild.getHubOffers().isEmpty());
    }

    @Test
    void boundsRefuseARateOrFeeOutsideTheHostRange() {
        Facts facts = new Facts();
        facts.minRate = 5;
        facts.maxRate = 20;
        Guild guild = guild();

        assertFalse(HubAgreementService.propose(guild, "Leader", "host", "port", 4, 1.0, facts, 0L).succeeded());
        assertFalse(HubAgreementService.propose(guild, "Leader", "host", "port", 21, 1.0, facts, 0L).succeeded());
        facts.maxRate = 50;
        assertFalse(HubAgreementService.propose(guild, "Leader", "host", "port", 51, 1.0, facts, 0L).succeeded());
        assertFalse(HubAgreementService.propose(guild, "Leader", "host", "port", 10, -0.01, facts, 0L).succeeded());
        assertFalse(HubAgreementService.propose(guild, "Leader", "host", "port", 10, 1.234, facts, 0L).succeeded());
        assertFalse(HubAgreementService.propose(guild, "Leader", "host", "port", 10, 500.01, facts, 0L).succeeded());
        assertTrue(guild.getHubOffers().isEmpty());

        facts.hubTax = false;
        assertFalse(HubAgreementService.propose(guild, "Leader", "host", "port", 10, 1.0, facts, 0L).succeeded());
        assertTrue(guild.getHubOffers().isEmpty());

        Faction host = mock(Faction.class);
        when(host.hasFactionRule(Rules.HUB_TAX)).thenReturn(true);
        TaxHandler taxes = new TaxHandler(host, 0, 0, 0, 0, 0);
        taxes.applyBracket(TaxTarget.HUB_TAX, new Bracket(0, 80));
        when(host.getTaxHandler()).thenReturn(taxes);
        Cache.supplyHubMaxTax = 50;
        RateRange range = HubAgreementService.allowedRateRange(host);
        assertTrue(range.canHost());
        assertEquals(0, range.minPercent());
        assertEquals(50, range.maxPercent());
        when(host.hasFactionRule(Rules.HUB_TAX)).thenReturn(false);
        assertFalse(HubAgreementService.allowedRateRange(host).canHost());
    }

    @Test
    void acceptanceBuildsNothingWhenTheLimitOrSlotIsGone() {
        Facts facts = new Facts();
        Guild limited = guild();
        limited.getHubOffers().add(offer(10, 100));
        limited.getSupplyHubs().add(new SupplyHub("other", "yard", 1));
        facts.limit = 1;
        AgreementResult atLimit = HubAgreementService.accept(limited, "Council", "host", "port", facts, 5L);
        assertFalse(atLimit.succeeded());
        assertEquals(1, limited.getHubOffers().size());
        assertEquals(1, limited.getSupplyHubs().size());
        assertNull(HubAgreementService.findAgreement(limited, "host", "port"));

        Facts full = new Facts();
        full.slots = 1;
        full.occupied = 1;
        Guild taken = guild();
        taken.getHubOffers().add(offer(10, 100));
        AgreementResult noSlot = HubAgreementService.accept(taken, "Council", "host", "port", full, 5L);
        assertFalse(noSlot.succeeded());
        assertEquals(1, taken.getHubOffers().size());
        assertTrue(taken.getSupplyHubs().isEmpty());
    }

    @Test
    void termRenewsRemovesAndAppliesNextTerms() {
        Facts facts = new Facts();
        Guild renewing = agreed(facts, 14, true, true, null);
        tick(renewing, facts, 13);
        assertNotNull(HubAgreementService.findAgreement(renewing, "host", "port"));
        assertEquals(1, HubAgreementService.findAgreement(renewing, "host", "port").daysRemaining());
        tick(renewing, facts, 1);
        HubAgreement renewed = HubAgreementService.findAgreement(renewing, "host", "port");
        assertNotNull(renewed);
        assertEquals(14, renewed.daysRemaining());
        assertEquals(10, renewed.taxRatePercent());
        assertEquals(1, renewing.getSupplyHubs().size());

        Guild ending = agreed(facts, 14, true, false, null);
        tick(ending, facts, 13);
        assertEquals(1, ending.getSupplyHubs().size());
        tick(ending, facts, 1);
        assertTrue(ending.getSupplyHubs().isEmpty());
        assertNull(HubAgreementService.findAgreement(ending, "host", "port"));

        Guild next = agreed(facts, 14, true, true, new HubTerms(8, 50));
        tick(next, facts, 14);
        HubAgreement applied = HubAgreementService.findAgreement(next, "host", "port");
        assertNotNull(applied);
        assertEquals(8, applied.taxRatePercent());
        assertEquals(50, applied.feeCents());
        assertNull(applied.nextTerms());
        assertEquals(14, applied.daysRemaining());

        Facts tight = new Facts();
        Guild outside = agreed(tight, 14, true, true, null);
        tick(outside, tight, 13);
        tight.maxRate = 9;
        tick(outside, tight, 1);
        assertTrue(outside.getSupplyHubs().isEmpty());
        assertNull(HubAgreementService.findAgreement(outside, "host", "port"));
    }

    @Test
    void nextTermAcceptanceStoresTheTermsAndTurnsRenewalOn() {
        Facts facts = new Facts();
        Guild guild = agreed(facts, 10, false, false, null);
        AgreementResult offered = HubAgreementService.propose(
                guild, "Leader", "host", "port", 18, 4.25, facts, 20L);
        assertTrue(offered.succeeded());
        AgreementResult accepted = HubAgreementService.accept(guild, "Council", "host", "port", facts, 21L);
        assertTrue(accepted.succeeded());
        assertFalse(accepted.builtHub());
        HubAgreement agreement = HubAgreementService.findAgreement(guild, "host", "port");
        assertNotNull(agreement.nextTerms());
        assertEquals(18, agreement.nextTerms().taxRatePercent());
        assertEquals(425, agreement.nextTerms().feeCents());
        assertTrue(agreement.guildRenews());
        assertTrue(agreement.hostRenews());
        assertTrue(guild.getHubOffers().isEmpty());
    }

    @Test
    void pendingListIsOnlyTheOfferAndTheAgreementThatWillNotRenew() {
        Facts facts = new Facts();
        Guild guild = guild();
        guild.getHubOffers().add(new HubOffer(
                "host", "port", 10, 100, OfferSide.GUILD, "Council", 1L, OfferKind.NEW_HUB));
        guild.getHubOffers().add(new HubOffer(
                "host", "yard", 10, 100, OfferSide.HOST, "Leader", 1L, OfferKind.NEW_HUB));
        guild.getHubAgreements().add(new HubAgreement("host", "old", 10, 100, 3, false, true, null, false));
        guild.getHubAgreements().add(new HubAgreement("host", "later", 10, 100, 4, false, true, null, false));
        guild.getHubAgreements().add(new HubAgreement("host", "renews", 10, 100, 3, true, true, null, false));

        List<PendingMatter> pending = HubAgreementService.pendingFor("Leader", List.of(guild), facts);
        assertEquals(2, pending.size());
        assertEquals(PendingMatter.Kind.OFFER, pending.get(0).kind());
        assertEquals("port", pending.get(0).installationId());
        assertEquals(PendingMatter.Kind.EXPIRING, pending.get(1).kind());
        assertEquals("old", pending.get(1).installationId());
        String summary = HubAgreementService.summarize(pending);
        assertNotNull(summary);
        assertTrue(summary.contains("1 hub offer"));
        assertTrue(summary.contains("1 hub agreement"));

        assertTrue(HubAgreementService.pendingFor("Stranger", List.of(guild), facts).isEmpty());
    }

    @Test
    void feeMovesToTheHostMainGuildAndSkipsSameRealmOrABankruptHost() {
        Faction home = mock(Faction.class);
        Faction host = mock(Faction.class);
        when(home.getId()).thenReturn("home");
        when(home.getName()).thenReturn("Home");
        when(host.getId()).thenReturn("host");
        when(host.getName()).thenReturn("Host");
        when(host.hasFactionRule(Rules.HUB_TAX)).thenReturn(true);
        Guild payer = ledgerGuild(home, false);
        Guild receiver = ledgerGuild(host, true);
        when(payer.getName()).thenReturn("Payer");
        when(payer.getHubAgreements()).thenReturn(new ArrayList<>(List.of(
                new HubAgreement("host", "port", 10, 250, 14, true, true, null, false))));
        when(payer.getSupplyHubs()).thenReturn(new ArrayList<>());
        GuildHandler hosts = mock(GuildHandler.class);
        when(host.getGuildHandler()).thenReturn(hosts);
        when(hosts.getGuild("host")).thenReturn(receiver);
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
            factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
            factions.when(() -> FactionManager.getByString("home")).thenReturn(home);
            factions.when(FactionManager::getAllGuilds).thenReturn(List.of(payer, receiver));
            relations.when(() -> RelationManager.sameRealm(any(), any())).thenReturn(false);

            assertEquals(-2.5, payer.getLedger().getIncome(Cashflow.HUB_FEE_PAYMENTS), 1e-9);
            assertEquals(2.5, receiver.getLedger().getIncome(Cashflow.HUB_FEE), 1e-9);
            DailyGuildTransfers transfers = new DailyGuildTransfers();
            payer.getLedger().populateDailyTransfers(transfers);
            assertEquals(2.5, transfers.getTransfers().get(payer).get(receiver), 1e-9);
            var day = Ledger.collectHistoryDay(List.of(payer, receiver));
            assertEquals(2.5, day.get(receiver).get(LedgerHistory.Source.HUB_FEE).get("Home"), 1e-9);
            assertEquals(2.5, day.get(payer).get(LedgerHistory.Source.HUB_FEE_PAYMENTS).get("Host"), 1e-9);

            relations.when(() -> RelationManager.sameRealm(any(), any())).thenReturn(true);
            assertEquals(0, payer.getLedger().getIncome(Cashflow.HUB_FEE_PAYMENTS), 1e-9);
            assertEquals(0, receiver.getLedger().getIncome(Cashflow.HUB_FEE), 1e-9);

            relations.when(() -> RelationManager.sameRealm(any(), any())).thenReturn(false);
            when(receiver.isBankrupt()).thenReturn(true);
            assertEquals(0, payer.getLedger().getIncome(Cashflow.HUB_FEE_PAYMENTS), 1e-9);
            assertEquals(0, receiver.getLedger().getIncome(Cashflow.HUB_FEE), 1e-9);

            when(receiver.isBankrupt()).thenReturn(false);
            when(host.hasFactionRule(Rules.HUB_TAX)).thenReturn(false);
            assertEquals(-2.5, payer.getLedger().getIncome(Cashflow.HUB_FEE_PAYMENTS), 1e-9);
            assertEquals(2.5, receiver.getLedger().getIncome(Cashflow.HUB_FEE), 1e-9);
        }
    }

    @Test
    void twoHostsAreTaxedAtTheirOwnAgreementRates() {
        Guild guild = guild();
        guild.getHubAgreements().add(new HubAgreement("east", "port", 10, 0, 14, true, true, null, false));
        guild.getHubAgreements().add(new HubAgreement("west", "yard", 40, 0, 14, true, true, null, false));
        SupplyHub east = new SupplyHub("east", "port", 1);
        SupplyHub west = new SupplyHub("west", "yard", 2);
        assertEquals(10, HubAgreementService.taxRatePercent(guild, east));
        assertEquals(40, HubAgreementService.taxRatePercent(guild, west));
        assertEquals(0, HubAgreementService.taxRatePercent(guild, new SupplyHub("east", "missing", 3)));
    }

    @Test
    void sheddingDropsTheNewestHubAndItsAgreementWhenFeesCannotBePaid() {
        Guild guild = guild();
        SupplyHub older = new SupplyHub("host", "old", 1);
        SupplyHub newest = new SupplyHub("host", "new", 2);
        guild.getSupplyHubs().add(older);
        guild.getSupplyHubs().add(newest);
        guild.getHubAgreements().add(new HubAgreement("host", "old", 10, 0, 14, true, true, null, false));
        guild.getHubAgreements().add(new HubAgreement("host", "new", 10, 1000, 14, true, true, null, false));
        when(guild.getModifier(GuildModifier.HUB_UPKEEP)).thenReturn(15.0);
        Bank bank = mock(Bank.class);
        when(bank.getWealth()).thenReturn(30.0);
        when(guild.getBank()).thenReturn(bank);

        List<SupplyHub> removed = SupplyHubService.shedUnpaid(guild);
        assertEquals(List.of(newest), removed);
        assertEquals(List.of(older), guild.getSupplyHubs());
        assertNull(HubAgreementService.findAgreement(guild, "host", "new"));
        assertNotNull(HubAgreementService.findAgreement(guild, "host", "old"));
    }

    @Test
    void transferToAnotherRealmRemovesTheHubAndTransferHomeKeepsIt() {
        Facts facts = new Facts();
        facts.councils.put("rome", List.of("OldCouncil"));
        facts.councils.put("venice", List.of("NewCouncil"));
        facts.councils.put("home", List.of("HomeCouncil"));
        facts.same.add("home");

        Guild abroad = guild();
        abroad.getSupplyHubs().add(new SupplyHub("rome", "port", 5));
        abroad.getHubAgreements().add(new HubAgreement("rome", "port", 10, 100, 8, true, true, null, false));
        List<HubNotice> removed = HubAgreementService.onTransferred(List.of(abroad), "rome", "venice", "port", facts);
        assertTrue(abroad.getSupplyHubs().isEmpty());
        assertNull(HubAgreementService.findAgreement(abroad, "rome", "port"));
        Set<String> told = new HashSet<>();
        for (HubNotice notice : removed) {
            told.add(notice.playerName());
            assertTrue(notice.message().contains("another realm"));
        }
        assertEquals(Set.of("Leader", "OldCouncil", "NewCouncil"), told);

        Guild home = guild();
        home.getSupplyHubs().add(new SupplyHub("rome", "port", 5));
        home.getHubAgreements().add(new HubAgreement("rome", "port", 10, 100, 8, true, true, null, false));
        List<HubNotice> kept = HubAgreementService.onTransferred(List.of(home), "rome", "home", "port", facts);
        assertTrue(kept.isEmpty());
        assertNull(HubAgreementService.findAgreement(home, "rome", "port"));
        assertNull(HubAgreementService.findAgreement(home, "home", "port"));
        assertEquals(1, home.getSupplyHubs().size());
        assertEquals("home", home.getSupplyHubs().get(0).ownerFactionId());
        assertEquals("port", home.getSupplyHubs().get(0).installationId());
        assertEquals(5L, home.getSupplyHubs().get(0).createdAt());
    }

    @Test
    void dormantHubKeepsItsFeeAndTheLeaderIsToldOnce() {
        Facts facts = new Facts();
        facts.dormant = true;
        Guild guild = agreed(facts, 14, true, true, null);
        List<HubNotice> first = HubAgreementService.tick(List.of(guild), 1L, facts);
        assertEquals(1, first.size());
        assertEquals("Leader", first.get(0).playerName());
        assertTrue(first.get(0).message().contains("dormant"));
        assertTrue(first.get(0).message().contains("Remove the hub"));
        HubAgreement agreement = HubAgreementService.findAgreement(guild, "host", "port");
        assertTrue(agreement.dormantNoticeSent());
        assertEquals(5.0, HubAgreementService.feeDenars(guild, guild.getSupplyHubs().get(0)), 1e-9);
        List<HubNotice> second = HubAgreementService.tick(List.of(guild), 2L, facts);
        assertTrue(second.stream().noneMatch(notice -> notice.message().contains("dormant")));
        assertEquals(5.0, HubAgreementService.feeDenars(guild, guild.getSupplyHubs().get(0)), 1e-9);
        assertNotNull(HubAgreementService.findAgreement(guild, "host", "port"));
    }

    @Test
    void loadDropsAForeignHubWithNoAgreementAndIgnoresOldPermitData() {
        Facts facts = new Facts();
        facts.same.add("home");
        Guild guild = guild();
        guild.getSupplyHubs().add(new SupplyHub("host", "port", 1));
        guild.getSupplyHubs().add(new SupplyHub("home", "yard", 2));
        List<String> lines = HubAgreementService.removeUnagreedForeignHubs(List.of(guild), facts);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("Merchants"));
        assertTrue(lines.get(0).contains("port"));
        assertNull(SupplyHubService.findHub(guild.getSupplyHubs(), "host", "port"));
        assertNotNull(SupplyHubService.findHub(guild.getSupplyHubs(), "home", "yard"));

        FactionData loaded = JsonUtil.GSON.fromJson(
                "{\"hub permits\":[\"merchants\"],\"hub tax\":12.0,\"id\":\"rome\"}", FactionData.class);
        assertEquals("rome", loaded.id);
        assertEquals(List.of("merchants"), loaded.hubPermits);
        assertEquals(12.0, loaded.hubTax);
        String fresh = JsonUtil.GSON.toJson(new FactionData());
        assertFalse(fresh.contains("hub permits"));
        assertFalse(fresh.contains("hub tax"));
    }

    private static void tick(Guild guild, Facts facts, int days) {
        for (int day = 0; day < days; day++) {
            HubAgreementService.tick(List.of(guild), day, facts);
        }
    }

    private static Guild agreed(Facts facts, int days, boolean guildRenews, boolean hostRenews, HubTerms next) {
        Guild guild = guild();
        guild.getSupplyHubs().add(new SupplyHub("host", "port", 1));
        guild.getHubAgreements().add(new HubAgreement(
                "host", "port", 10, 500, days, guildRenews, hostRenews, next, false));
        return guild;
    }

    private static HubOffer offer(int rate, long cents) {
        return new HubOffer("host", "port", rate, cents, OfferSide.HOST, "Leader", 1L, OfferKind.NEW_HUB);
    }

    private static Guild guild() {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("merchants");
        when(guild.getName()).thenReturn("Merchants");
        when(guild.getSupplyHubs()).thenReturn(new ArrayList<>());
        when(guild.getHubAgreements()).thenReturn(new ArrayList<>());
        when(guild.getHubOffers()).thenReturn(new ArrayList<>());
        return guild;
    }

    private static Guild ledgerGuild(Faction faction, boolean base) {
        Guild guild = mock(Guild.class);
        Bank bank = mock(Bank.class);
        when(bank.getWealth()).thenReturn(1000.0);
        when(guild.getBank()).thenReturn(bank);
        when(guild.getFaction()).thenReturn(faction);
        when(guild.isBase()).thenReturn(base);
        when(guild.isBankrupt()).thenReturn(false);
        when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
        when(guild.getLedger()).thenReturn(new Ledger(guild));
        when(guild.getId()).thenReturn(base ? "host-guild" : "payer");
        return guild;
    }

    private static final class Facts implements HubAgreementFacts {
        final Set<String> same = new HashSet<>();
        final Map<String, List<String>> councils = new HashMap<>();
        boolean hostExists = true;
        boolean hubTax = true;
        int minRate;
        int maxRate = 50;
        double maxFee = 500;
        int offerDays = 7;
        int agreementDays = 14;
        boolean installation = true;
        boolean allows = true;
        int slots = 2;
        int limit = 4;
        int occupied;
        double trade = 12;
        boolean dormant;
        String leader = "Leader";

        Facts() {
            councils.put("host", List.of("Council"));
        }

        @Override
        public boolean sameRealm(Guild guild, String factionId) {
            return factionId != null && same.contains(factionId.toLowerCase(Locale.ROOT));
        }

        @Override
        public boolean hostExists(String factionId) {
            return hostExists;
        }

        @Override
        public boolean hostHasHubTax(String factionId) {
            return hubTax;
        }

        @Override
        public int minRate(String factionId) {
            return minRate;
        }

        @Override
        public int maxRate(String factionId) {
            return maxRate;
        }

        @Override
        public double maxFee() {
            return maxFee;
        }

        @Override
        public int offerDays() {
            return offerDays;
        }

        @Override
        public int agreementDays() {
            return agreementDays;
        }

        @Override
        public boolean installationExists(String factionId, String installationId) {
            return installation;
        }

        @Override
        public boolean allowsSupplyHubs(Guild guild) {
            return allows;
        }

        @Override
        public int hubSlots(String factionId, String installationId) {
            return slots;
        }

        @Override
        public int hubLimit(Guild guild) {
            return limit;
        }

        @Override
        public int hubsAtInstallation(String factionId, String installationId) {
            return occupied;
        }

        @Override
        public double tradePower(Guild guild, String factionId, String installationId) {
            return trade;
        }

        @Override
        public boolean isGuildLeader(Guild guild, String actorName) {
            return actorName != null && actorName.equalsIgnoreCase(leader);
        }

        @Override
        public boolean isHostCouncil(String factionId, String actorName) {
            if (actorName == null) {
                return false;
            }
            List<String> names = councils.getOrDefault(factionId, List.of());
            for (String name : names) {
                if (actorName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<String> council(String factionId) {
            return councils.getOrDefault(factionId, List.of());
        }

        @Override
        public String guildLeader(Guild guild) {
            return leader;
        }

        @Override
        public boolean hubDormant(Guild guild, SupplyHub hub) {
            return dormant;
        }

        @Override
        public String installationLabel(String factionId, String installationId) {
            return installationId == null ? "installation" : installationId;
        }
    }
}
