package me.Plugins.SimpleFactions.Managers.Inventory;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;

public class LoanPayment {
    private Guild guild;
    private Loan loan;
    private int time;

    public LoanPayment(Guild guild, Loan loan) {
        this.guild = guild;
        this.loan = loan;
        time = 0;
    }

    public boolean tick() {
        time++;
        return time == 30;
    }

    public Loan getLoan() {
        return loan;
    }

    public Guild getGuild() {
        return guild;
    }
}
