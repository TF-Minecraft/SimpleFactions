package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubTaxBreakdown;
import net.tfminecraft.simplefactions.guild.hub.SupplyHub;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;

class LedgerHubTaxTest {
    private MockedStatic<FactionManager> factions;
    private MockedStatic<RelationManager> relations;
    private Guild payer;
    private Guild receiver;
    private Faction host;
    private TaxHandler taxes;
    private HubTaxBreakdown assessment;

    @BeforeEach
    void setUp() {
        factions = mockStatic(FactionManager.class);
        relations = mockStatic(RelationManager.class);
        Faction home = mock(Faction.class);
        when(home.getId()).thenReturn("home");
        when(home.getName()).thenReturn("Home");
        host = mock(Faction.class);
        when(host.getId()).thenReturn("host");
        when(host.getName()).thenReturn("Host");
        Government government = mock(Government.class);
        when(host.getGovernment()).thenReturn(government);
        when(government.getTaxEfficiency()).thenReturn(1.0);
        taxes = new TaxHandler(host, 0, 0, 0, 0, 0);
        taxes.setHubTax(20);
        when(host.getTaxHandler()).thenReturn(taxes);
        when(host.getTaxRate(any(TaxTarget.class), nullable(String.class), anyBoolean()))
                .thenAnswer(call -> taxes.getTaxRate(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        payer = guild(home, false);
        receiver = guild(host, true);
        when(payer.getId()).thenReturn("payer");
        when(payer.getName()).thenReturn("Payer");
        when(host.getOrCreateMainGuild()).thenReturn(receiver);
        SupplyHub hub = new SupplyHub("host", "station", 1);
        assessment = new HubTaxBreakdown(
                Map.of(hub, new HubTaxBreakdown.Assessment(50, 10)), Map.of(host, 50.0), Map.of(host, 10.0), java.util.Set.of());
        when(payer.getHubTaxBreakdown()).thenReturn(assessment);
        TradeBreakdown trade = new TradeBreakdown();
        trade.setIncome(200);
        when(payer.getTradeBreakdown()).thenReturn(trade);
        factions.when(FactionManager::getAllGuilds).thenReturn(List.of(payer, receiver));
    }

    @AfterEach
    void tearDown() {
        IncomePreviewContext.clear();
        factions.close();
        relations.close();
    }

    @Test
    void bothLedgerLinesNetAndDividendBaseMatchTheSingleTransfer() {
        assertEquals(-10, payer.getLedger().getIncome(Cashflow.HUB_TAX_PAYMENTS));
        assertEquals(10, receiver.getLedger().getIncome(Cashflow.HUB_TAX));
        assertEquals(0, payer.getLedger().getIncome(Cashflow.HUB_TAX));
        assertEquals(190, payer.getLedger().getNetIncome());
        assertEquals(190, payer.getLedger().getDividendBase());
        assertEquals(10, receiver.getLedger().getNetIncome());
        assertEquals(200, payer.getLedger().getGrossTaxableIncome());
        assertEquals(0, payer.getLedger().getInflationDelta() - 200);
        DailyGuildTransfers transfers = new DailyGuildTransfers();
        payer.getLedger().populateDailyTransfers(transfers);
        receiver.getLedger().populateDailyTransfers(transfers);
        assertEquals(Map.of(receiver, 10.0), transfers.getTransfers().get(payer));
        assertEquals(Map.of(payer, 200.0), transfers.getExternalDeltas());
        assertEquals(1, transfers.getTransfers().size());
    }

    @Test
    void bothSidesHistoryIsCollectedAndSaved() {
        var day = Ledger.collectHistoryDay(List.of(payer, receiver));
        assertEquals(Map.of("Home", 10.0), day.get(receiver).get(LedgerHistory.Source.HUB_TAX));
        assertEquals(Map.of("Host", 10.0), day.get(payer).get(LedgerHistory.Source.HUB_TAX_PAYMENTS));
        for (Guild guild : List.of(payer, receiver)) {
            guild.getLedger().getHistory().closeDay(day.get(guild));
            GuildData data = new GuildData();
            data.ledgerLastDay = guild.getLedger().getHistory().getLastDayCopy();
            data.ledgerLifetime = guild.getLedger().getHistory().getLifetimeCopy();
            GuildData restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(data), GuildData.class);
            LedgerHistory loaded = new LedgerHistory();
            loaded.load(restored.ledgerLastDay, restored.ledgerLifetime, null);
            LedgerHistory.Source source = guild == payer ? LedgerHistory.Source.HUB_TAX_PAYMENTS : LedgerHistory.Source.HUB_TAX;
            assertEquals(10, LedgerHistory.total(loaded.getLastDay(source)));
            assertEquals(10, LedgerHistory.total(loaded.getLifetime(source)));
        }
    }

    @Test
    void bankruptPayersDoNotPayOrCreateIncomeForHosts() {
        when(payer.isBankrupt()).thenReturn(true);
        assertEquals(0, payer.getLedger().getIncome(Cashflow.HUB_TAX_PAYMENTS));
        assertEquals(0, receiver.getLedger().getIncome(Cashflow.HUB_TAX));
        assertTrue(Ledger.collectHistoryDay(List.of(payer, receiver)).isEmpty());
        DailyGuildTransfers transfers = new DailyGuildTransfers();
        payer.getLedger().populateDailyTransfers(transfers);
        assertTrue(transfers.getTransfers().isEmpty());
    }

    @Test
    void bankruptHostsReceiveNothingAndPayersKeepTheirIncome() {
        when(receiver.isBankrupt()).thenReturn(true);
        assertEquals(0, receiver.getLedger().getTotalHubTaxEarned());
        assertEquals(0, receiver.getLedger().getIncome(Cashflow.HUB_TAX));
        assertEquals(0, payer.getLedger().getIncome(Cashflow.HUB_TAX_PAYMENTS));
        assertEquals(200, payer.getLedger().getNetIncome());
        assertEquals(200, payer.getLedger().getDividendBase());
        assertTrue(Ledger.collectHistoryDay(List.of(payer, receiver)).isEmpty());
        DailyGuildTransfers transfers = new DailyGuildTransfers();
        payer.getLedger().populateDailyTransfers(transfers);
        receiver.getLedger().populateDailyTransfers(transfers);
        assertTrue(transfers.getTransfers().isEmpty());
        assertEquals(Map.of(payer, 200.0), transfers.getExternalDeltas());
    }

    @Test
    void missingBankPayersAreFrozenAndBaseGuildsAlsoPay() {
        when(payer.getBank()).thenReturn(null);
        assertEquals(0, receiver.getLedger().getIncome(Cashflow.HUB_TAX));
        when(payer.getBank()).thenReturn(mock(Bank.class));
        when(payer.isBase()).thenReturn(true);
        assertEquals(-10, payer.getLedger().getIncome(Cashflow.HUB_TAX_PAYMENTS));
    }

    @Test
    void ratePreviewUsesStoredBasesAndLeavesTheAssessmentAndRateAlone() {
        Map<Guild, Double> impact = EconomicPreview.tax(host, TaxTarget.HUB_TAX, null, 40);
        assertEquals(-10, impact.get(payer));
        assertEquals(10, impact.get(receiver));
        assertEquals(20, taxes.getHubTax());
        assertEquals(10, assessment.getTotalTax());
        assertEquals(50, assessment.getTaxableIncome(host));
    }

    private static Guild guild(Faction faction, boolean base) {
        Guild guild = mock(Guild.class);
        Bank bank = mock(Bank.class);
        when(bank.getWealth()).thenReturn(1000.0);
        when(guild.getBank()).thenReturn(bank);
        when(guild.getFaction()).thenReturn(faction);
        when(guild.isBase()).thenReturn(base);
        when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
        Ledger ledger = new Ledger(guild);
        when(guild.getLedger()).thenReturn(ledger);
        return guild;
    }
}
