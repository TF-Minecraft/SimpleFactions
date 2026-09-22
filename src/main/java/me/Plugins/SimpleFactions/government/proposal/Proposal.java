package me.Plugins.SimpleFactions.government.proposal;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.EconomicImpact;
import me.Plugins.SimpleFactions.laws.LawGroup;

import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.resolution.CouncilPeaceQueries;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.PoliticalAction;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.laws.Law;

public class Proposal {
    private Government gov;
    private String proposer;

    private Law law;
    private TaxLawChange tax;
    private PoliticalAction action;
    private String target;

    public Proposal(String proposer, Government gov) {
        this.gov = gov;
        this.proposer = proposer;
    }

    public String getProposer() {
        return proposer;
    }

    public void apply(Cause cause) {
        if (isLawProposal()) {
            LawGroup group = gov.getFaction().getLawHandler().getGroup(law.getGroup());
            gov.getFaction().applyLaw(law, group);
        } else if (isTaxProposal()) {
            TaxTarget target = tax.getTarget();
            gov.getFaction().getTaxHandler().setTaxRate(target, tax.getId(), tax.getNewTax());
        } else if (isPoliticalActionProposal()) {
            gov.getFaction().applyPoliticalAction(cause, this);
        }
    }

    public void tick() {
        //checking if target is still valid
        if(hasTarget()) {
            if (!checkTarget()) {
                target = null;
            }
        }
    }

    public boolean checkTarget() {
        if (!needsTarget()) return true;
        if (target == null) return false;
        // For CHANGE_LEADER, target must be a current member of the faction
        if (isPoliticalActionProposal() && action.getAction() == Action.CHANGE_LEADER) {
            Faction faction = gov.getFaction();
            return faction != null && faction.canBecomeLeader(target);
        }
        if (isPoliticalActionProposal() && CouncilPeaceQueries.isWarEndAction(action.getAction())) {
            return CouncilPeaceQueries.isValidTarget(gov.getFaction(), target);
        }
        return false;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean needsTarget() {
        if (!isPoliticalActionProposal()) {
            return false;
        }
        Action kind = action.getAction();
        return kind == Action.CHANGE_LEADER || CouncilPeaceQueries.isWarEndAction(kind);
    }

    public boolean isLawProposal() {
        return law != null;
    }
    public Law getLaw() {
        return law;
    }
    public void setLawProposal(Law law) {
        this.law = law;
    }
    public boolean isTaxProposal() {
        return tax != null;
    }
    public TaxLawChange getTaxChange() {
        return tax;
    }
    public void setTaxProposal(TaxLawChange tax) {
        this.tax = tax;
    }
    public boolean isPoliticalActionProposal() {
        return action != null;
    }
    public PoliticalAction getPoliticalAction() {
        if(action == null) {
            if(isLawProposal()) {
                return new PoliticalAction(Action.LAW_CHANGE);
            } else if(isTaxProposal()) {
                return new PoliticalAction(Action.TAX_CHANGE);
            }
        }
        return action;
    }
    public void setPoliticalActionProposal(PoliticalAction action) {
        this.action = action;
    }

    public boolean affectsEconomy() {
        if (isLawProposal()) {
            return law.affectsEconomy();
        } else if (isTaxProposal()) {
            return true;
        } else if (isPoliticalActionProposal()) {
            return false;
        }
        return false;
    }

    public ItemStack getAsBook() {
        return getAsBook(null);
    }

    public ItemStack getAsBook(Player p) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();

        String title = isLawProposal() ? "Law Proposal" : isTaxProposal() ? "Tax Proposal" : "Political Action Proposal";
        if (title.length() > 32) title = title.substring(0, 32);
        meta.setTitle(title);
        meta.setAuthor(proposer != null ? proposer : "Unknown");

        Faction f = gov != null ? gov.getFaction() : null;

        // First page: basic info
        List<String> first = new ArrayList<>();
        first.add(StringFormatter.formatHex(isLawProposal() ? "#93c9a7Law Proposal" : "#93c9a7Tax Proposal"));
        first.add("");
        first.add(StringFormatter.formatHex("#85c265Proposed by: #c2bea7" + proposer));

        if (isLawProposal() && law != null) {
            LawGroup group = f != null ? f.getLawHandler().getGroup(law.getGroup()) : null;
            String groupName = group != null ? group.getName() : (law.getGroup() != null ? law.getGroup() : "unknown");
            first.add(StringFormatter.formatHex("#b8ae61Group: #c2bea7" + groupName));
            if (group != null && group.getCurrent() != null) {
                first.add(StringFormatter.formatHex(group.getCurrent().getName() + " §7-> " + law.getName()));
            } else {
                first.add(StringFormatter.formatHex(law.getName()));
            }
        } else if (isTaxProposal() && tax != null) {
            TaxTarget target = tax.getTarget();
            String name = target != null ? target.getDisplayName() : "Unknown";
            String type = "";
            if (target == TaxTarget.GUILD_ID) {
                name = tax.getId() != null ? tax.getId() : name;
            } else if (target == TaxTarget.VASSAL_ID) {
                name = tax.getId() != null ? tax.getId() : name;
                type = " #4269a8Vassal";
            } else if (target == TaxTarget.TARIFF_ID) {
                name = tax.getId() != null ? tax.getId() : name;
                type = " #79bf6dTariff";
            }

            String oldRate = "";
            if (f != null) {
                if (target == TaxTarget.GUILD_ID || target == TaxTarget.VASSAL_ID || target == TaxTarget.TARIFF_ID) {
                    double rate = f.getTaxRate(target, tax.getId(), false);
                    if (rate == -1.0) oldRate = String.valueOf(f.getTaxRate(target, null, false));
                    else oldRate = String.valueOf(rate);
                } else {
                    oldRate = String.valueOf(f.getTaxRate(target, null, false));
                }
            }

            first.add(StringFormatter.formatHex("#b8ae61Target: #c2bea7" + name + (type.isEmpty() ? "" : " §7(" + type + "§7)")));
            first.add(StringFormatter.formatHex("#b8ae61Change: #c2bea7" + oldRate + "% §7-> #c2bea7" + tax.getNewTax() + "%"));
            if (f != null && (target == TaxTarget.GUILD_ID || target == TaxTarget.VASSAL_ID || target == TaxTarget.TARIFF_ID)) {
                double baseRate = f.getTaxRate(target, null, false);
                first.add(StringFormatter.formatHex("#3f4040(#767a77Base Rate: #928d7a" + baseRate + "%#3f4040)"));
            }
        } else if(isPoliticalActionProposal()) {
            Action action = getPoliticalAction().getAction();
            first.add(StringFormatter.formatHex("#b8ae61Action: #c2bea7"+action.getDisplay()));
            switch(action) {
                case CHANGE_LEADER:
                    first.add(StringFormatter.formatHex("#d4c9aeTarget: "+ (hasTarget() ? "#51e0a2"+ target : "#a3462cNo target")));
                    break;
                case WHITE_PEACE:
                case SURRENDER:
                    first.add(StringFormatter.formatHex("#d4c9aeWar: "+ warTargetLabel()));
                    break;
                case DISSOLVE:
                    first.add(StringFormatter.formatHex("#d4c9aeDissolves the faction as if the #c9655e'Dissolve Faction' #d4c9aebutton was clicked"));
                    break;
                case INDEPENDENCE:
                    first.add(StringFormatter.formatHex("#d4c9aeMembers of the cause are granted independence"));
                    break;
                case NATIONHOOD:
                    first.add(StringFormatter.formatHex("#d4c9aeMembers of the cause are elevated to nationhood"));
                    break;
                case SNAP_ELECTIONS:
                    first.add(StringFormatter.formatHex("#d4c9aeElections will immediately begin, voting lasts for a week"));
                    break;
                //handled outside or none
                case TAX_CHANGE:
                case LAW_CHANGE:
                case NONE:
                default:
                    break;
            }
        }

        // Second page: economic impact (if available)
        List<String> econ = new ArrayList<>();
        if (affectsEconomy() && p != null && f != null) {
            if (isLawProposal() && law != null) {
                LawGroup group = f.getLawHandler().getGroup(law.getGroup());
                EconomicImpact.applyEconomicChange(econ, p, f, group, law, true);
            } else if (isTaxProposal() && tax != null) {
                TaxTarget target = tax.getTarget();
                if (target == TaxTarget.TARIFFS || target == TaxTarget.TARIFF_ID) {
                    EconomicImpact.applyTariffImpact(econ, p, f, tax.getNewTax(), true);
                } else {
                    EconomicImpact.applyTaxImpact(econ, p, f, target, tax.getId(), tax.getNewTax(), true);
                }
            }
        } else if (affectsEconomy()) {
            econ.add(StringFormatter.formatHex("#89504eEconomic preview unavailable"));
            econ.add(StringFormatter.formatHex("#b8ae61Open this book while online to view impact"));
        }

        List<String> pages = new ArrayList<>();
        pages.add(String.join("\n", first));
        if (!econ.isEmpty()) pages.add(String.join("\n", econ));

        meta.setPages(pages);
        item.setItemMeta(meta);
        return item;
    }

    private String warTargetLabel() {
        if (!hasTarget()) {
            return "#a3462cNo target";
        }
        War war = CouncilPeaceQueries.warFromTarget(target);
        if (war == null) {
            return "#a3462cInvalid war";
        }
        return "#51e0a2" + war.getName();
    }
}
