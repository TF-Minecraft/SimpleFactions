package me.Plugins.SimpleFactions.Objects.Handler;

import java.util.HashMap;
import java.util.Map;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Bracket;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.enums.Rules;

public class TaxHandler {
    private Faction f;
    private TaxSnapshot savedSnapshot;

    private HashMap<TaxTarget, Bracket> taxBrackets = new HashMap<>();

    private double citizenTax;
    private double guildTax;
    private double vassalTax;
    private double dividendTax;
    private double tariffs;

    private HashMap<TaxTarget, HashMap<String, Double>> specificTaxes = new HashMap<>();

    public TaxHandler(Faction f, double citizenTax, double guildTax, double vassalTax, double dividendTax, double tariffs) {
        this.f = f;
        this.citizenTax = citizenTax;
        this.guildTax = guildTax;
        this.vassalTax = vassalTax;
        this.dividendTax = dividendTax;
        this.tariffs = tariffs;
    }

    public boolean hasTariffs() {
        return tariffs > 0;
    }

    public void setTariffs(double tariffs) {
        this.tariffs = tariffs;
    }

    public void setCitizenTax(double citizenTax) {
        this.citizenTax = citizenTax;
    }

    public void setGuildTax(double guildTax) {
        this.guildTax = guildTax;
    }

    public void setVassalTax(double vassalTax) {
        this.vassalTax = vassalTax;
    }

    public void setDividendTax(double dividendTax) {
        this.dividendTax = dividendTax;
    }

    public double getTariffs() {
        return tariffs;
    }

    public double getCitizenTax() {
        return citizenTax;
    }
    public double getGuildTax() {
        return guildTax;
    }
    public double getVassalTax() {
        return vassalTax;
    }
    public double getDividendTax() {
        return dividendTax;
    }

    public HashMap<TaxTarget, HashMap<String, Double>> getSpecificTaxes() {
        return specificTaxes;
    }

    public void setTaxRate(TaxTarget target, String id, double rate) {
        switch (target) {
            case CITIZENS:
                citizenTax = rate;
                break;

            case GUILDS:
                guildTax = rate;
                break;

            case VASSALS:
                vassalTax = rate;
                break;

            case DIVIDENDS:
                dividendTax = rate;
                break;

            case TARIFFS:
                tariffs = rate;
                break;

            case GUILD_ID:
                setSpecificTax(TaxTarget.GUILDS, id, rate);
                break;

            case VASSAL_ID:
                setSpecificTax(TaxTarget.VASSALS, id, rate);
                break;

            case TARIFF_ID:
                setSpecificTax(TaxTarget.TARIFFS, id, rate);
                break;

            default:
                break;
        }
    }

    public double getTaxRate(TaxTarget target, String id, boolean effective) {
        double rate =  switch (target) {
            case CITIZENS -> citizenTax;
            case DIVIDENDS -> dividendTax;
            case TARIFFS -> tariffs;

            case GUILDS -> (id != null && hasSpecificTax(target, id))
                ? getSpecificTax(target, id) : guildTax;

            case VASSALS -> (id != null && hasSpecificTax(target, id))
                ? getSpecificTax(target, id) : vassalTax;

            case GUILD_ID -> (id != null && hasSpecificTax(TaxTarget.GUILDS, id))
                ? getSpecificTax(TaxTarget.GUILDS, id) : guildTax;

            case VASSAL_ID -> (id != null && hasSpecificTax(TaxTarget.VASSALS, id))
                ? getSpecificTax(TaxTarget.VASSALS, id) : vassalTax;

            case TARIFF_ID -> (id != null && hasSpecificTax(TaxTarget.TARIFFS, id))
                ? getSpecificTax(TaxTarget.TARIFFS, id) : tariffs;

            default -> 0.0;
        };
        return effective ? Formatter.formatDouble(rate * f.getGovernment().getTaxEfficiency()) : rate;
    }


    public boolean hasSpecificTax(TaxTarget target, String id) {
        return specificTaxes.containsKey(target) && specificTaxes.get(target).containsKey(id);
    }

    public double getSpecificTax(TaxTarget target, String id) {
        if(!hasSpecificTax(target, id)) return -1.0;
        return specificTaxes.get(target).getOrDefault(id, -1.0);
    }

    //Brackets
    private double applyBracket(double value, Bracket bracket) {
        if (value < bracket.getMin()) return bracket.getMin();
        if (value > bracket.getMax()) return bracket.getMax();
        return value;
    }

    public void applyBracket(TaxTarget target, Bracket bracket) {
        taxBrackets.put(target, bracket);

        switch (target) {

            case CITIZENS:
                citizenTax = applyBracket(citizenTax, bracket);
                break;

            case GUILDS:
                guildTax = applyBracket(guildTax, bracket);
                applySpecificBracket(target, bracket);
                break;

            case VASSALS:
                vassalTax = applyBracket(vassalTax, bracket);
                applySpecificBracket(target, bracket);
                break;

            case DIVIDENDS:
                dividendTax = applyBracket(dividendTax, bracket);
                break;

            case TARIFFS:
                tariffs = applyBracket(tariffs, bracket);
                break;

            default:
                break;
        }
    }

    public Bracket getBracket(TaxTarget target) {
        return taxBrackets.get(target);
    }

    public double getMin(TaxTarget target) {
        if(!canCollectTax(target)) return 0.0;
        Bracket bracket = taxBrackets.get(target);
        if (bracket == null) return 0.0;
        return bracket.getMin();
    }

    public double getMax(TaxTarget target) {
        if(!canCollectTax(target)) return 0.0;
        Bracket bracket = taxBrackets.get(target);
        if (bracket == null) return 100.0;
        return bracket.getMax();
    }

    public boolean canCollectTax(TaxTarget target) {
        switch(target) {
            case CITIZENS:
                return f.hasFactionRule(Rules.CITIZEN_TAX);
            case GUILDS:
            case GUILD_ID:
                return f.hasFactionRule(Rules.GUILD_TAX);
            case VASSALS:
            case VASSAL_ID:
                return f.hasFactionRule(Rules.VASSAL_TAX);
            case DIVIDENDS:
                return f.hasFactionRule(Rules.DIVIDEND_TAX);
            case TARIFFS:
            case TARIFF_ID:
                return f.hasFactionRule(Rules.TARIFFS);
            default:
                return false;
        }
    }

    public void setSpecificTax(TaxTarget target, String id, double rate) {
        double defaultRate = getDefaultRate(target);

        if (Double.compare(rate, defaultRate) == 0) {
            // Not specific anymore
            HashMap<String, Double> map = specificTaxes.get(target);
            if (map != null) {
                map.remove(id);
                if (map.isEmpty()) specificTaxes.remove(target);
            }
            return;
        }

        specificTaxes
            .computeIfAbsent(target, k -> new HashMap<>())
            .put(id, rate);
    }

    private void applySpecificBracket(TaxTarget target, Bracket bracket) {
        if (!specificTaxes.containsKey(target)) return;

        HashMap<String, Double> map = specificTaxes.get(target);
        double defaultRate = getDefaultRate(target);

        map.entrySet().removeIf(entry -> {
            double clamped = applyBracket(entry.getValue(), bracket);

            // If equal to default, remove the override
            if (Double.compare(clamped, defaultRate) == 0) {
                return true;
            }

            // Otherwise update value
            entry.setValue(clamped);
            return false;
        });

        // Clean up empty maps
        if (map.isEmpty()) {
            specificTaxes.remove(target);
        }
    }

    //State
    public void saveState() {
        HashMap<TaxTarget, HashMap<String, Double>> copiedSpecificTaxes = new HashMap<>();

        for (var entry : specificTaxes.entrySet()) {
            copiedSpecificTaxes.put(
                entry.getKey(),
                new HashMap<>(entry.getValue())
            );
        }

        savedSnapshot = new TaxSnapshot(
            citizenTax,
            guildTax,
            vassalTax,
            dividendTax,
            tariffs,
            copiedSpecificTaxes
        );
    }

    public void restoreState() {
        if (savedSnapshot == null) return;

        this.citizenTax = savedSnapshot.citizenTax;
        this.guildTax = savedSnapshot.guildTax;
        this.vassalTax = savedSnapshot.vassalTax;
        this.dividendTax = savedSnapshot.dividendTax;
        this.tariffs = savedSnapshot.tariffs;

        this.specificTaxes.clear();

        for (var entry : savedSnapshot.specificTaxes.entrySet()) {
            this.specificTaxes.put(
                entry.getKey(),
                new HashMap<>(entry.getValue())
            );
        }

        savedSnapshot = null; // optional: prevent double restore
    }

    private double getDefaultRate(TaxTarget target) {
        return switch (target) {
            case CITIZENS -> citizenTax;
            case GUILDS, GUILD_ID -> guildTax;
            case VASSALS, VASSAL_ID -> vassalTax;
            case DIVIDENDS -> dividendTax;
            case TARIFFS, TARIFF_ID -> tariffs;
            default -> 0.0;
        };
    }

    public Map<Guild, Double> getTaxChangeEffects(TaxTarget target, String id, double newRate) {
        Map<Guild, Double> effects = new HashMap<>();
        for(Guild g : FactionManager.getAllGuilds()) {
            effects.put(g, g.getLedger().getNetIncome());
        }
        saveState();
        setTaxRate(target, id, newRate);
        for(Guild g : FactionManager.getAllGuilds()) {
            double newIncome = g.getLedger().getNetIncome();
            effects.put(g, newIncome - effects.get(g));
        }
        restoreState();
        return effects;
    }
}
