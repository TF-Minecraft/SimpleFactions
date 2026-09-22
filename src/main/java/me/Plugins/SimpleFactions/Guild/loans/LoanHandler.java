package me.Plugins.SimpleFactions.Guild.loans;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class LoanHandler {
    private Guild guild;
    private int creditScore = 50;
    private Map<String, Loan> issuedLoans = new LinkedHashMap<>();

    public LoanHandler(Guild guild) {
        this.guild = guild;
    }

    public LoanHandler(Guild guild, int creditScore) {
        this.guild = guild;
        this.creditScore = creditScore;
    }

    public void issueLoan(Loan loan) {
        issuedLoans.put(loan.getId(), loan);
    }

    public void removeLoan(String id) {
        issuedLoans.remove(id);
    }

    public Loan getLoanById(String id) {
        return issuedLoans.get(id);
    }

    public void changeCreditScore(int amount) {
        creditScore += amount;
        creditScore = Math.max(0, Math.min(100, creditScore));
    }

    public List<Loan> getLoansByGuild(Guild g) {
        List<Loan> loans = new ArrayList<>();
        for(Loan l : issuedLoans.values()) {
            if(l.getBorrower().getId().equalsIgnoreCase(g.getId())) {
                loans.add(l);
            }
        }
        return loans;
    }

    public List<Loan> getLoansGiven() {
        return new ArrayList<>(issuedLoans.values());
    }

    public List<Loan> getLoansTaken() {
        List<Loan> loansTaken = new ArrayList<>();
        for(Guild g : FactionManager.getAllGuilds()) {
            if(g.getId().equalsIgnoreCase(guild.getId())) continue;
            loansTaken.addAll(g.getLoanHandler().getLoansByGuild(guild));
        }
        return loansTaken;
    }

    public double getTotalOwed() {
        double total = 0.0;
        for(Loan l : getLoansTaken()) {
            total += l.getTotalOwed();
        }
        return total;
    }

    public double getDailyInterestChange() {
        double total = 0.0;
        for(Loan l : getLoansTaken()) {
            total += l.getDailyInterestChange();
        }
        return total;
    }   

    public double getTotalLent() {
        double total = 0.0;
        for(Loan l : issuedLoans.values()) {
            total += l.getTotalOwed();
        }
        return total;
    }

    public int getCreditScore() {
        return creditScore;
    }

    public String getCreditScoreString() {
        // Clamp stability just in case
        double s = Math.max(0, Math.min(100, getCreditScore()));
        double t = s / 100.0;

        // Dark red → bright green
        int startR = 139, startG = 0,   startB = 0;
        int endR   = 0,   endG   = 255, endB   = 0;

        int r = (int) Math.round(startR + (endR - startR) * t);
        int g = (int) Math.round(startG + (endG - startG) * t);
        int b = (int) Math.round(startB + (endB - startB) * t);

        return StringFormatter.formatHex(String.format("#%02X%02X%02X"+getCreditScore()+"/100", r, g, b));
    }
}
