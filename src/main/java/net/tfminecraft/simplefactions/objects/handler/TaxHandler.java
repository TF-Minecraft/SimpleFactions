package net.tfminecraft.simplefactions.objects.handler;

import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.enums.Rules;

public class TaxHandler {
    public static final Bracket DEFAULT_HUB_TAX_BRACKET = new Bracket(0, 10);
    private Faction f;
    private TaxSnapshot savedSnapshot;

    private HashMap<TaxTarget, Bracket> taxBrackets = new HashMap<>();

    private double citizenTax;
    private double guildTax;
    private double vassalTax;
    private double dividendTax;
    private double tariffs;
    private double hubTax;

    private HashMap<TaxTarget, HashMap<String, Double>> specificTaxes = new HashMap<>();

    public TaxHandler(Faction f, double citizenTax, double guildTax, double vassalTax, double dividendTax, double tariffs) {
        this.f = f;
        this.citizenTax = citizenTax;
        this.guildTax = guildTax;
        this.vassalTax = vassalTax;
        this.dividendTax = dividendTax;
        this.tariffs = tariffs;
    }

    public void setTariffs(double tariffs) {
        this.tariffs = tariffs;
    }

    public void setHubTax(double rate) {
        hubTax = clampHubTax(rate);
    }

    public double getHubTax() {
        return clampHubTax(hubTax);
    }

    private double clampHubTax(double rate) {
        return canCollectTax(TaxTarget.HUB_TAX)
                ? clampHubTax(rate, getBracket(TaxTarget.HUB_TAX)) : 0;
    }

    private double clampHubTax(double rate, Bracket bracket) {
        if (!Double.isFinite(rate)) {
            return 0;
        }
        double max = Math.min(Cache.supplyHubMaxTax, bracket == null ? DEFAULT_HUB_TAX_BRACKET.getMax() : bracket.getMax());
        double min = Math.min(max, bracket == null ? 0.0 : bracket.getMin());
        return Math.max(min, Math.min(max, rate));
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

            case HUB_TAX:
                setHubTax(rate);
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
            case HUB_TAX -> hubTax;
            case TARIFFS -> (id != null && hasSpecificTax(target, id))
                ? getSpecificTax(target, id) : tariffs;

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
        IncomePreviewContext context = IncomePreviewContext.current();
        if (context != null) {
            rate = context.adjustTax(f, this, target, id, rate);
        }
        if (target == TaxTarget.HUB_TAX) {
            if (context != null && context.previewsLaw(f)) {
                rate = clampHubTax(rate, new Bracket(0, Cache.supplyHubMaxTax));
            } else {
                rate = clampHubTax(rate);
            }
        }
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

            case HUB_TAX:
                setHubTax(hubTax);
                break;

            case TARIFFS:
                tariffs = applyBracket(tariffs, bracket);
                applySpecificBracket(target, bracket);
                break;

            default:
                break;
        }
    }

    public Bracket getBracket(TaxTarget target) {
        return target == TaxTarget.HUB_TAX
                ? taxBrackets.getOrDefault(target, DEFAULT_HUB_TAX_BRACKET) : taxBrackets.get(target);
    }

    public double getMin(TaxTarget target) {
        if(!canCollectTax(target)) return 0.0;
        Bracket bracket = getBracket(target);
        if (bracket == null) return 0.0;
        if (target == TaxTarget.HUB_TAX) {
            return Math.min(bracket.getMin(), getMax(target));
        }
        return bracket.getMin();
    }

    public double getMax(TaxTarget target) {
        if(!canCollectTax(target)) return 0.0;
        Bracket bracket = getBracket(target);
        if (target == TaxTarget.HUB_TAX) {
            return Math.min(Cache.supplyHubMaxTax, bracket == null ? DEFAULT_HUB_TAX_BRACKET.getMax() : bracket.getMax());
        }
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
            case HUB_TAX:
                return f.hasFactionRule(Rules.HUB_TAX);
            default:
                return false;
        }
    }

    public void setSpecificTax(TaxTarget target, String id, double rate) {
        if (target == TaxTarget.HUB_TAX) {
            return;
        }
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
            hubTax,
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
        setHubTax(savedSnapshot.hubTax);

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
            case HUB_TAX -> getHubTax();
            case TARIFFS, TARIFF_ID -> tariffs;
            default -> 0.0;
        };
    }

    public Map<Guild, Double> getTaxChangeEffects(TaxTarget target, String id, double newRate) {
        return EconomicPreview.tax(f, target, id, newRate);
    }
}
