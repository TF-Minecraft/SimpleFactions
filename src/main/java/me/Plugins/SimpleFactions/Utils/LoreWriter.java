package me.Plugins.SimpleFactions.Utils;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Bracket;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.enums.Brackets;
import me.Plugins.SimpleFactions.enums.Region;
import me.Plugins.SimpleFactions.enums.Rules;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.proposal.TaxLawChange;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawEffect;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class LoreWriter {

    private static final String CHECK = "✔";
	private static final String CROSS = "✖";

	private static final String GREEN = "#87d65c";
	private static final String RED   = "#d65c5c";
	private static final String GRAY  = "#6f776a";
	private static final String LIGHT_GRAY  = "#9cb68c";

    public static void applyProposalLore(Proposal proposal, List<String> lore, Player p, Faction f) {
        if(proposal.isLawProposal()) {
            Law law = proposal.getLaw();
            LawGroup group = f.getLawHandler().getGroup(law.getGroup());
            lore.add(StringFormatter.formatHex("#b8ae61Group: #c2bea7"+group.getName()));
            lore.add(StringFormatter.formatHex(group.getCurrent().getName()+" §7-> "+law.getName()));
            // ---- Economic preview ----
            if (law.affectsEconomy()) {
                EconomicImpact.applyEconomicChange(lore, p, f, group, law);
            }
        } else if(proposal.isTaxProposal()) {
            TaxLawChange taxChange = proposal.getTaxChange();
            TaxTarget target = taxChange.getTarget();
            String name = "";
            String type = "";
            if(target == TaxTarget.GUILD_ID) {
                Guild guild = FactionManager.getGuildByString(taxChange.getId());
                name = guild.getName();
                type = guild.getType().getName();
            } else if(target == TaxTarget.VASSAL_ID) {
                name = FactionManager.getByString(taxChange.getId()).getName();
                type = "#4269a8Vassal";
            } else if(target == TaxTarget.TARIFF_ID) {
                name = FactionManager.getByString(taxChange.getId()).getName();
                type = "#79bf6dTariff";
            } else{
                name = target.getDisplayName();
            }
            String oldRate = "";
            if(target == TaxTarget.GUILD_ID || target == TaxTarget.VASSAL_ID || target == TaxTarget.TARIFF_ID) {
                double rate = f.getTaxRate(target, taxChange.getId(), false);
                if(rate == -1.0) {
                    oldRate = String.valueOf(f.getTaxRate(target, null, false));
                } else {
                    oldRate = String.valueOf(rate);
                }
            } else {
                oldRate = String.valueOf(f.getTaxRate(target, null, false));
            }
            double baseRate = f.getTaxRate(target, null, false);
            lore.add(StringFormatter.formatHex("#b8ae61Target: #c2bea7"+name+
                (type.isEmpty() ? "" : " §7("+type+"§7)")));
            lore.add(StringFormatter.formatHex("#b8ae61Change: #c2bea7"+oldRate+"% §7-> #c2bea7"+taxChange.getNewTax()+"%"));
            if(target == TaxTarget.GUILD_ID || target == TaxTarget.VASSAL_ID || target == TaxTarget.TARIFF_ID) {
                lore.add(StringFormatter.formatHex("#3f4040(#767a77Base Rate: #928d7a"+baseRate+"%#3f4040)"));
            }
            if(target == TaxTarget.TARIFFS || target == TaxTarget.TARIFF_ID) {
                EconomicImpact.applyTariffImpact(lore, p, f, taxChange.getNewTax());
            } else {
                EconomicImpact.applyTaxImpact(lore, p, f, target, taxChange.getId(), taxChange.getNewTax());
            }
        } else if(proposal.isPoliticalActionProposal()) {
            Action action = proposal.getPoliticalAction().getAction();
            lore.add(StringFormatter.formatHex("#b8ae61Action: #c2bea7"+action.getDisplay()));
            switch(action) {
                case CHANGE_LEADER:
                    lore.add(StringFormatter.formatHex("#d4c9aeTarget: "+ (proposal.hasTarget() ? "#51e0a2"+ proposal.getTarget() : "#a3462cNo target")));
                    break;
                case WHITE_PEACE:
                    lore.add(StringFormatter.formatHex("#d4c9aeForce a white peace offer in a chosen war"));
                    break;
                case SURRENDER:
                    lore.add(StringFormatter.formatHex("#d4c9aeSurrender a chosen war immediately"));
                    break;
                case DISSOLVE:
                    lore.add(StringFormatter.formatHex("#d4c9aeDissolves the faction as if the #c9655e'Dissolve Faction' #d4c9aebutton was clicked"));
                    break;
                case INDEPENDENCE:
                    lore.add(StringFormatter.formatHex("#d4c9aeMembers of the cause are granted independence"));
                    break;
                case NATIONHOOD:
                    lore.add(StringFormatter.formatHex("#d4c9aeMembers of the cause are elevated to nationhood"));
                    break;
                case SNAP_ELECTIONS:
                    lore.add(StringFormatter.formatHex("#d4c9aeElections will immediately begin, voting lasts for a week"));
                    break;
                //handled outside or none
                case TAX_CHANGE:
                case LAW_CHANGE:
                case NONE:
                default:
                    break;
            }
        }
    }

    public static void writeEffect(Scope scope, LawEffect effect, List<String> lore) {
        boolean factionScope = scope == Scope.FACTION;
        // Scope header
        if (!factionScope) {
            lore.add(StringFormatter.formatHex("  "+GRAY + scope.getDisplay() + ":"));
        }

        String indent = factionScope ? "  " : "    ";

        // ---- Rules ----
        if (effect.hasRules()) {
            for (Map.Entry<Rules, Boolean> ruleEntry : effect.getRules().entrySet()) {

                Rules rule = ruleEntry.getKey();
                boolean value = ruleEntry.getValue();

                String symbol = value ? GREEN + CHECK : RED + CROSS;

                lore.add(StringFormatter.formatHex(
                        indent + symbol + " #d4c9ae" + rule.getDisplay()
                ));
            }
        }

        // ---- Brackets ----
        if (effect.hasBrackets()) {
            for (Map.Entry<Brackets, Bracket> bracketEntry
                    : effect.getBrackets().entrySet()) {

                Brackets type = bracketEntry.getKey();
                Bracket bracket = bracketEntry.getValue();

                lore.add(StringFormatter.formatHex(
                        indent + LIGHT_GRAY + type.getDisplay() + " §7Range: "
                ) + bracket.getString());
            }
        }

        // ---- Regiments ----
        if (effect.hasRegiments()) {
            for (Map.Entry<Regiment, Integer> regimentEntry
                    : effect.getRegiments().entrySet()) {

                Regiment reg = regimentEntry.getKey();
                int amount = regimentEntry.getValue();

                lore.add(StringFormatter.formatHex(
                        indent + LIGHT_GRAY + "Free " + reg.getName() + LIGHT_GRAY +" Regiments§7: " + GREEN
                ) + amount);
            }
        }

        // ---- Global modifiers ----
        if (effect.hasGlobalModifiers()) {
            for (FactionModifier mod : effect.getGlobalModifiers()) {
                lore.add(indent + mod.getString());
            }
        }

        // ---- Region modifiers ----
        if (effect.hasRegionModifiers()) {
            for (Map.Entry<Region, List<FactionModifier>> regionEntry
                    : effect.getRegionModifiers().entrySet()) {

                Region region = regionEntry.getKey();

                lore.add(StringFormatter.formatHex(
                        indent + LIGHT_GRAY + region.getDisplay() + ":"
                ));

                for (FactionModifier mod : regionEntry.getValue()) {
                    lore.add(indent + "  " + mod.getString());
                }
            }
        }
    }
}
