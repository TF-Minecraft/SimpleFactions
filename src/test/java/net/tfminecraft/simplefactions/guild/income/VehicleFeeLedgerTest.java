package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

class VehicleFeeLedgerTest {
    private static Ledger ledger(boolean base) {
        Guild guild = mock(Guild.class);
        when(guild.isBase()).thenReturn(base);
        when(guild.getBank()).thenReturn(mock(Bank.class));
        when(guild.getFaction()).thenReturn(mock(Faction.class));
        Ledger ledger = new Ledger(guild);
        when(guild.getLedger()).thenReturn(ledger);
        return ledger;
    }

    @Test
    void collectedFeesShowOnTheCapitalLedgerOnly() {
        Ledger capital = ledger(true);
        capital.addVehicleFeeEntry(30.0);
        capital.addVehicleFeeEntry(-5.0);
        assertEquals(25.0, capital.getIncome(Cashflow.VEHICLE_FEES));

        Ledger guild = ledger(false);
        guild.addVehicleFeeEntry(30.0);
        assertEquals(0.0, guild.getIncome(Cashflow.VEHICLE_FEES));
    }
}
