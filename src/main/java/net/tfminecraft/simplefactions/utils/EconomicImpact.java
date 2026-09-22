package net.tfminecraft.simplefactions.utils;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class EconomicImpact {

    public static void applyEconomicChange(List<String> lore, Player p, Faction f, LawGroup group, Law law) {
        applyEconomicChange(lore, p, f, group, law, false);
    }

    public static void applyEconomicChange(List<String> lore, Player p, Faction f, LawGroup group, Law law, boolean shortForm) {
        Guild us = FactionManager.getGuildByMember(p.getName());
        if (us != null) {

            Map<Guild, Double> deltas =
                SimpleFactions.getInstance()
                    .getProvinceManager()
                    .previewLawIncomeExact(f, group, law);

            write(lore, deltas, us, shortForm);
        }
    }

    public static void applyTaxImpact(List<String> lore, Player p, Faction f, TaxTarget target, String id, double rate) {
        applyTaxImpact(lore, p, f, target, id, rate, false);
    }

    public static void applyTaxImpact(List<String> lore, Player p, Faction f, TaxTarget target, String id, double rate, boolean shortForm) {
        Guild us = FactionManager.getGuildByMember(p.getName());
        if (us != null) {

            Map<Guild, Double> deltas =
                f.getTaxHandler().getTaxChangeEffects(target, id, rate);

            write(lore, deltas, us, shortForm);
        }
    }

    public static void applyTariffImpact(List<String> lore, Player p, Faction f, double newTariffRate) {
        applyTariffImpact(lore, p, f, newTariffRate, false);
    }

    public static void applyTariffImpact(List<String> lore, Player p, Faction f, double newTariffRate, boolean shortForm) {
        Guild us = FactionManager.getGuildByMember(p.getName());
        if (us != null) {

            Map<Guild, Double> deltas =
                SimpleFactions.getInstance()
                    .getProvinceManager()
                    .previewTariffRateChange(f, newTariffRate);

            write(lore, deltas, us, shortForm);
        }
    }

    public static void applyFavourRepressChange(List<String> lore, Player p, Faction f, Guild g, boolean favour) {
        applyFavourRepressChange(lore, p, f, g, favour, false);
    }

    public static void applyFavourRepressChange(List<String> lore, Player p, Faction f, Guild g, boolean favour, boolean shortForm) {
        Guild us = FactionManager.getGuildByMember(p.getName());
        if (us != null) {

            Map<Guild, Double> deltas =
                SimpleFactions.getInstance()
                    .getProvinceManager()
                    .previewFavourRepressIncomeExact(f, g, favour);

            write(lore, deltas, us, shortForm);
        }
    }

    public static void applyTradeAgreementChange(List<String> lore, Player p, Faction origin, Faction target, RelationType agreement) {
        applyTradeAgreementChange(lore, p, origin, target, agreement, false);
    }

    public static void applyTradeAgreementChange(List<String> lore, Player p, Faction origin, Faction target, RelationType agreement, boolean shortForm) {
        Guild us = FactionManager.getGuildByMember(p.getName());
        if (us != null) {

            Map<Guild, Double> deltas =
                SimpleFactions.getInstance()
                    .getProvinceManager()
                    .previewTradeAgreementIncomeExact(origin, target, agreement);

            write(lore, deltas, us, shortForm);
        }
    }

    private static void write(List<String> lore, Map<Guild, Double> deltas, Guild us, boolean shortForm) {
        lore.add(StringFormatter.formatHex(shortForm ? "#78856dImpacts:" : "#78856dEstimated Economic Impacts:"));

        boolean shownAny = false;

        String indent = shortForm ? "" : "  ";
        String denominator = shortForm ? "d" : "d/day";
        // ---- Our guild first ----
        Double ourDelta = deltas.get(us);
        if (ourDelta != null && Math.abs(ourDelta) > 0) {
            lore.add(StringFormatter.formatHex(
                indent + us.getName() + "§7: " +
                (ourDelta > 0 ? "#87d65c+" : "#d65c5c") +
                String.format("%.2f", ourDelta) +
                denominator
            ));
            shownAny = true;
        }
        lore.add("");
        lore.add(StringFormatter.formatHex(shortForm ? "#78856dOther impacts:" : "#78856dOther Notable Impacts:"));
        // ---- Other most impacted guilds ----
        deltas.entrySet().stream()
            .filter(e -> !e.getKey().equals(us))
            .filter(e -> Math.abs(e.getValue()) > 0)
            .sorted((a, b) ->
                Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue()))
            )
            .limit(5)
            .forEach(e -> {
                String represents = shortForm ? "" : " §7("+e.getKey().getFaction().getName()+"§7)";
                lore.add(StringFormatter.formatHex(
                    indent + e.getKey().getName() + represents + "§7: " +
                    (e.getValue() > 0 ? "#87d65c+" : "#d65c5c") +
                    String.format("%.2f", e.getValue()) +
                    denominator
                ));
            });

        if (!shownAny && deltas.values().stream().allMatch(v -> Math.abs(v) == 0)) {
            lore.add(StringFormatter.formatHex(shortForm ? indent + "#93c9a7No Change" : indent + "#93c9a7No Economic Change"));
        }
    }
}
