package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;

class GuildDonationTest {

    @AfterEach
    void tearDown() {
        Ledger.setNodeUpkeepLookup(null);
    }

    @Test
    void feeIsTenPercentOnTopOfTheGift() {
        assertEquals(10.0, GuildDonation.fee(100));
        assertEquals(110.0, GuildDonation.cost(100));
        assertEquals(0.0, GuildDonation.fee(0));
        assertEquals(0.0, GuildDonation.cost(0));
        assertEquals(3.33, GuildDonation.fee(33.33));
        assertEquals(36.66, GuildDonation.cost(33.33));
    }

    @Test
    void donationAmountRoundTripsInGuildData() {
        GuildData data = new GuildData();
        data.donation = 100.0;
        GuildData restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(data), GuildData.class);
        assertEquals(100.0, restored.donation);
        GuildData empty = JsonUtil.GSON.fromJson("{}", GuildData.class);
        assertNull(empty.donation);
    }

    @Test
    void settlementSendsTheGiftAndSinksTheFee() {
        Guilds guilds = guilds(1000, 100);
        assertEquals(-100.0, guilds.payer.getLedger().getIncome(Cashflow.DONATION_PAYMENTS));
        assertEquals(-10.0, guilds.payer.getLedger().getIncome(Cashflow.DONATION_FEE));
        assertEquals(-110.0, guilds.payer.getLedger().getNetIncome());
        assertEquals(-110.0, guilds.payer.getLedger().getDividendBase());
        assertEquals(100.0, guilds.capital.getLedger().getIncome(Cashflow.DONATIONS));
        assertEquals(-10.0, guilds.payer.getLedger().getInflationDelta());
        assertEquals(0.0, guilds.payer.getLedger().getGrossTaxableIncome());

        DailyGuildTransfers transfers = new DailyGuildTransfers();
        guilds.payer.getLedger().populateDailyTransfers(transfers);

        assertEquals(100.0, guilds.donation[0]);
        assertEquals(100.0, transfers.getTransfers().get(guilds.payer).get(guilds.capital));
        assertEquals(-10.0, transfers.getExternalDeltas().get(guilds.payer));
    }

    @Test
    void settlementCancelsTheGiftWhenTheDayWouldFinishBelowZero() {
        Ledger.setNodeUpkeepLookup(guild -> 50);
        Guilds guilds = guilds(100, 100);

        DailyGuildTransfers transfers = new DailyGuildTransfers();
        guilds.payer.getLedger().populateDailyTransfers(transfers);

        assertEquals(0.0, guilds.donation[0]);
        assertNull(transfers.getTransfers().get(guilds.payer));
        assertEquals(-50.0, transfers.getExternalDeltas().get(guilds.payer));
    }

    @Test
    void settlementKeepsTheGiftWhenTheDayFinishesAtZero() {
        Ledger.setNodeUpkeepLookup(guild -> 50);
        Guilds guilds = guilds(160, 100);

        DailyGuildTransfers transfers = new DailyGuildTransfers();
        guilds.payer.getLedger().populateDailyTransfers(transfers);

        assertEquals(100.0, guilds.donation[0]);
        assertEquals(100.0, transfers.getTransfers().get(guilds.payer).get(guilds.capital));
        assertEquals(-60.0, transfers.getExternalDeltas().get(guilds.payer));
    }

    @Test
    void tradeIncomeArrivingTheSameDayCanCoverTheGift() {
        Guilds guilds = guilds(0, 100);
        guilds.payer.getTradeBreakdown().setIncome(200);

        DailyGuildTransfers transfers = new DailyGuildTransfers();
        guilds.payer.getLedger().populateDailyTransfers(transfers);

        assertEquals(100.0, guilds.donation[0]);
        assertEquals(100.0, transfers.getTransfers().get(guilds.payer).get(guilds.capital));
        assertEquals(190.0, transfers.getExternalDeltas().get(guilds.payer));
    }

    @Test
    void bankruptGuildClearsThePledgeInsteadOfCarryingIt() {
        Guilds guilds = guilds(1000, 80);
        when(guilds.payer.isBankrupt()).thenReturn(true);

        guilds.payer.getLedger().populateDailyTransfers(new DailyGuildTransfers());

        assertEquals(0.0, guilds.donation[0]);
    }

    @Test
    void realmGuildDoesNotSendADonation() {
        Guilds guilds = guilds(1000, 0);
        when(guilds.capital.getDonationAmount()).thenReturn(500.0);

        DailyGuildTransfers transfers = new DailyGuildTransfers();
        guilds.capital.getLedger().populateDailyTransfers(transfers);

        assertNull(transfers.getTransfers().get(guilds.capital));
        assertEquals(0.0, guilds.capital.getLedger().getIncome(Cashflow.DONATION_PAYMENTS));
        assertEquals(0.0, guilds.capital.getLedger().getIncome(Cashflow.DONATION_FEE));
    }

    @Test
    void realmReceiptSkipsBankruptGuilds() {
        Guilds guilds = guilds(1000, 25);
        net.tfminecraft.simplefactions.guild.Guild broke = mock(net.tfminecraft.simplefactions.guild.Guild.class);
        when(broke.isBase()).thenReturn(false);
        when(broke.isBankrupt()).thenReturn(true);
        when(broke.getDonationAmount()).thenReturn(40.0);
        Bank bank = mock(Bank.class);
        when(bank.getWealth()).thenReturn(-5.0);
        when(broke.getBank()).thenReturn(bank);
        when(broke.getLedger()).thenReturn(new Ledger(broke));
        when(guilds.handler.getGuilds()).thenReturn(List.of(guilds.capital, guilds.payer, broke));

        assertEquals(25.0, guilds.capital.getLedger().getIncome(Cashflow.DONATIONS));
    }

    private static Guilds guilds(double wealth, double donation) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn("realm");
        when(faction.getName()).thenReturn("Realm");
        GuildHandler handler = mock(GuildHandler.class);
        when(faction.getGuildHandler()).thenReturn(handler);

        net.tfminecraft.simplefactions.guild.Guild payer = guild(faction, false, wealth);
        net.tfminecraft.simplefactions.guild.Guild capital = guild(faction, true, wealth);
        when(faction.getOrCreateMainGuild()).thenReturn(capital);
        when(handler.getGuilds()).thenReturn(List.of(capital, payer));

        double[] stored = {donation};
        when(payer.getDonationAmount()).thenAnswer(invocation -> stored[0]);
        when(payer.setDonationAmount(anyDouble())).thenAnswer(invocation -> {
            stored[0] = invocation.getArgument(0);
            return stored[0];
        });
        return new Guilds(payer, capital, handler, stored);
    }

    private static net.tfminecraft.simplefactions.guild.Guild guild(Faction faction, boolean base, double wealth) {
        net.tfminecraft.simplefactions.guild.Guild guild = mock(net.tfminecraft.simplefactions.guild.Guild.class);
        Bank bank = mock(Bank.class);
        when(bank.getWealth()).thenReturn(wealth);
        when(guild.getBank()).thenReturn(bank);
        when(guild.getFaction()).thenReturn(faction);
        when(guild.isBase()).thenReturn(base);
        when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
        when(guild.getLedger()).thenReturn(new Ledger(guild));
        return guild;
    }

    private record Guilds(
            net.tfminecraft.simplefactions.guild.Guild payer,
            net.tfminecraft.simplefactions.guild.Guild capital,
            GuildHandler handler,
            double[] donation) {}
}
