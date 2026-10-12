package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;

public class LoanPayment {
    private Guild guild;
    private Loan loan;
    private int time;
    private final String operationId = net.tfminecraft.simplefactions.economy.TreasuryActions.newId();
    private net.tfminecraft.tlibs.character.CharacterSession actor;

    public LoanPayment(Guild guild, Loan loan, org.bukkit.entity.Player player) {
        this(guild, loan);
        actor = net.tfminecraft.simplefactions.identity.Members.session(player).orElse(null);
    }

    public boolean isCurrent() { return net.tfminecraft.simplefactions.identity.Members.isCurrent(actor); }
    public String operationId() { return operationId; }


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
