package net.tfminecraft.simplefactions.utils;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class EconomicImpact {
    static final String CALCULATING = "Calculating economic impact...";

    public static void applyEconomicChange(List<String> lore, Player p, Faction f, LawGroup group, Law law) {
        applyEconomicChange(lore, p, f, group, law, false, null, false);
    }

    public static void applyEconomicChange(List<String> lore, Player p, Faction f, LawGroup group, Law law, boolean shortForm) {
        applyEconomicChange(lore, p, f, group, law, shortForm, null, false);
    }

    public static void applyEconomicChange(
            List<String> lore, Player p, Faction f, LawGroup group, Law law,
            boolean shortForm, ItemMeta meta, boolean book) {
        Guild us = viewer(p);
        if (us == null) {
            return;
        }
        if (defer(lore, p, us, shortForm, meta, book, prepared -> EconomicPreview.law(prepared, f, group, law))) {
            return;
        }
        write(lore, SimpleFactions.getInstance().getProvinceManager().previewLawIncomeExact(f, group, law), us, shortForm);
    }

    public static void applyTaxImpact(List<String> lore, Player p, Faction f, TaxTarget target, String id, double rate) {
        applyTaxImpact(lore, p, f, target, id, rate, false, null, false);
    }

    public static void applyTaxImpact(List<String> lore, Player p, Faction f, TaxTarget target, String id, double rate, boolean shortForm) {
        applyTaxImpact(lore, p, f, target, id, rate, shortForm, null, false);
    }

    public static void applyTaxImpact(
            List<String> lore, Player p, Faction f, TaxTarget target, String id, double rate,
            boolean shortForm, ItemMeta meta, boolean book) {
        Guild us = viewer(p);
        if (us == null) {
            return;
        }
        if (defer(lore, p, us, shortForm, meta, book, prepared -> EconomicPreview.tax(f, target, id, rate))) {
            return;
        }
        write(lore, f.getTaxHandler().getTaxChangeEffects(target, id, rate), us, shortForm);
    }

    public static void applyTariffImpact(List<String> lore, Player p, Faction f, double newTariffRate) {
        applyTariffImpact(lore, p, f, newTariffRate, false, null, false);
    }

    public static void applyTariffImpact(List<String> lore, Player p, Faction f, double newTariffRate, boolean shortForm) {
        applyTariffImpact(lore, p, f, newTariffRate, shortForm, null, false);
    }

    public static void applyTariffImpact(
            List<String> lore, Player p, Faction f, double newTariffRate,
            boolean shortForm, ItemMeta meta, boolean book) {
        Guild us = viewer(p);
        if (us == null) {
            return;
        }
        if (defer(lore, p, us, shortForm, meta, book,
                prepared -> EconomicPreview.copyOf(prepared).previewTariffRateChange(f, newTariffRate))) {
            return;
        }
        write(lore, SimpleFactions.getInstance().getProvinceManager().previewTariffRateChange(f, newTariffRate), us, shortForm);
    }

    public static void applyFavourRepressChange(List<String> lore, Player p, Faction f, Guild g, boolean favour) {
        applyFavourRepressChange(lore, p, f, g, favour, false, null, false);
    }

    public static void applyFavourRepressChange(List<String> lore, Player p, Faction f, Guild g, boolean favour, boolean shortForm) {
        applyFavourRepressChange(lore, p, f, g, favour, shortForm, null, false);
    }

    public static void applyFavourRepressChange(
            List<String> lore, Player p, Faction f, Guild g, boolean favour,
            boolean shortForm, ItemMeta meta, boolean book) {
        Guild us = viewer(p);
        if (us == null) {
            return;
        }
        if (defer(lore, p, us, shortForm, meta, book, prepared -> EconomicPreview.favour(prepared, g, favour))) {
            return;
        }
        write(lore, SimpleFactions.getInstance().getProvinceManager().previewFavourRepressIncomeExact(f, g, favour), us, shortForm);
    }

    public static void applyTradeAgreementChange(List<String> lore, Player p, Faction origin, Faction target, RelationType agreement) {
        applyTradeAgreementChange(lore, p, origin, target, agreement, false, null, false);
    }

    public static void applyTradeAgreementChange(
            List<String> lore, Player p, Faction origin, Faction target, RelationType agreement, boolean shortForm) {
        applyTradeAgreementChange(lore, p, origin, target, agreement, shortForm, null, false);
    }

    public static void applyTradeAgreementChange(
            List<String> lore, Player p, Faction origin, Faction target, RelationType agreement,
            boolean shortForm, ItemMeta meta, boolean book) {
        Guild us = viewer(p);
        if (us == null) {
            return;
        }
        if (defer(lore, p, us, shortForm, meta, book,
                prepared -> EconomicPreview.trade(prepared, origin, target, agreement))) {
            return;
        }
        write(lore, SimpleFactions.getInstance().getProvinceManager()
                .previewTradeAgreementIncomeExact(origin, target, agreement), us, shortForm);
    }

    private static Guild viewer(Player player) {
        if (player == null) {
            return null;
        }
        return FactionManager.getGuildByMember(player.getName());
    }

    private static boolean defer(
            List<String> lore,
            Player player,
            Guild viewer,
            boolean shortForm,
            ItemMeta meta,
            boolean book,
            EconomicImpactService.Calculator calculator) {
        if (meta == null || SimpleFactions.plugin == null || !SimpleFactions.plugin.isEnabled()) {
            return false;
        }
        lore.add(calculatingLine());
        EconomicImpactService.enqueue(player, meta, viewer, shortForm, book, calculator);
        return true;
    }

    static String calculatingLine() {
        return StringFormatter.formatHex("#78856d" + CALCULATING);
    }

    static String unavailableLine() {
        return StringFormatter.formatHex("#cf493aIncome estimate unavailable");
    }

    static void write(List<String> lore, Map<Guild, Double> deltas, Guild us, boolean shortForm) {
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
