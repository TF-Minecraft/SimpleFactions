package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.PoliticalActionLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.utils.EconomicImpact;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.LoreWriter;
import net.tfminecraft.simplefactions.utils.Represents;
import net.tfminecraft.simplefactions.utils.Wealth;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.Council;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.government.stability.StabilityDebuffs;
import net.tfminecraft.simplefactions.government.stability.StabilityFacts;
import net.tfminecraft.simplefactions.government.stability.StabilityFacts.Body;
import net.tfminecraft.simplefactions.government.stability.StabilityMath;
import net.tfminecraft.simplefactions.government.stability.StabilityReport;
import net.tfminecraft.simplefactions.government.stability.StabilityTuning;
import net.tfminecraft.simplefactions.government.stability.StateStability;
import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class GovernmentCreator {
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createGovernmentItem(Faction f) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7Government:"));
        List<String> lore = new ArrayList<String>();
        Government gov = f.getGovernment();
        lore.add(StringFormatter.formatHex("#9c9775§l"+f.getRulerTitle()+": #c2bea7"+f.getLeader()));
        double power = Formatter.formatDouble(gov.getPower());
        double maxPower = Formatter.formatDouble(gov.getMaxPower());
        String powerString = ((power < 0) ? "§c" : "") + power+"/"+((maxPower < 0) ? "§c" : "") + maxPower;
        lore.add(StringFormatter.formatHex("#85c265Administrative Power§7: §e"+powerString+" §7("+(gov.getPowerGain() >= 0 ? "§e+" : "§c") 
                +Formatter.formatDouble(gov.getPowerGain())+"§7/hour)"));
        lore.add(" ");
        lore.add(StringFormatter.formatHex("#b8ae61Ruling System: #d4c9ae"+f.getGovernmentString()));
        lore.add(StringFormatter.formatHex("#b8ae61Leader Elections: "+(gov.hasLeaderElections() ? "#45afc4✔" : "#c74d32✖")));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createCouncilItem(Faction f) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7Council:"));
        List<String> lore = new ArrayList<String>();
        Government gov = f.getGovernment();
        lore.add(StringFormatter.formatHex("#85c265Council Type§7: #45c46f"+gov.getCouncil().getType().getDisplay()));
        lore.add(StringFormatter.formatHex("#85c265Council Size§7: §e"+gov.getCouncil().getCurrentSize()+"/"+gov.getCouncil().getMaxSize()));
        lore.add(" ");
        lore.add(StringFormatter.formatHex("#b8ae61Council Elections: "+(gov.hasCouncilElections() ? "#45afc4✔" : "#c74d32✖")));
        if(gov.getCouncil().getCurrentSize() > 0) {
            lore.add(StringFormatter.formatHex("#93c9a7Members:"));
            for(String member : gov.getCouncilMembers()) {
                lore.add(StringFormatter.formatHex("#d4bb98- "+member + " §7("+Represents.represents(f, member)+")"));
            }
        }
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createElectionItem(Player p, Faction f) {
        ItemStack item = TLibs.getItemAPI().getCreator().getItemFromPath("ia.iasurvival:letter");
        ItemMeta m = item.getItemMeta();
        Government gov = f.getGovernment();

        m.setDisplayName(StringFormatter.formatHex("#51d6e8Election"));
        List<String> lore = new ArrayList<>();

        if (gov.hasElection()) {
            lore.add(StringFormatter.formatHex("#85c265Election in progress"));
            lore.add(StringFormatter.formatHex("#ad9072Ends in: #e3d5a1" + gov.getTimeUntilElectionEnds()));
        } else {
            lore.add(StringFormatter.formatHex("#ad9072Next Election: #e3d5a1" + gov.getTimeUntilNextElection()));
            lore.add(StringFormatter.formatHex("#ad9072Last Election: #e3d5a1" + gov.getLastElectionString()));

            Map<Candidate, Map<String, Integer>> prev = gov.getElection().getPreviousVotes();

            // Leader results
            if (gov.hasLeaderElections() && !prev.get(Candidate.LEADER).isEmpty()) {
                lore.add("");
                lore.add(StringFormatter.formatHex("#93c9a7Leader Results"));

                int totalVotes = prev.get(Candidate.LEADER).values().stream().mapToInt(i -> i).sum();
                List<String> winners = gov.getElection().getWinners(Candidate.LEADER);

                int i = 1;
                for (String name : winners) {
                    int votes = prev.get(Candidate.LEADER).getOrDefault(name, 0);

                    String color;
                    if (f.isLeader(name)) {
                        color = "#45c46f"; // green – current leader
                    } else if (f.canBecomeLeader(name)) {
                        color = "#9bb6c9"; // eligible
                    } else {
                        color = "#c74d32"; // ineligible
                    }

                    lore.add(formatElectionLine(i++, name, votes, totalVotes, color));
                }

            }

            // Council results
            if (gov.hasCouncilElections()
                    && gov.getCouncil().getType() == Rules.ELECTED_COUNCIL
                    && !prev.get(Candidate.COUNCIL).isEmpty()) {

                lore.add("");
                lore.add(StringFormatter.formatHex("#93c9a7Council Results"));

                int totalVotes = prev.get(Candidate.COUNCIL).values().stream().mapToInt(i -> i).sum();
                List<String> winners = gov.getElection().getWinners(Candidate.COUNCIL);

                int i = 1;
                for (String name : winners) {
                    int votes = prev.get(Candidate.COUNCIL).getOrDefault(name, 0);

                    String color;
                    if (gov.getCouncil().isMember(name)) {
                        color = "#45c46f"; // green – current council member
                    } else if (gov.getCouncil().canBeMember(name, true, false)) {
                        color = "#9bb6c9"; // eligible
                    } else {
                        color = "#c74d32"; // ineligible
                    }

                    lore.add(formatElectionLine(i++, name, votes, totalVotes, color));
                }
            }
        }

        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    private String formatElectionLine(int index, String name, int votes, int totalVotes, String nameColor) {
        int percent = totalVotes > 0 ? (votes * 100) / totalVotes : 0;

        return StringFormatter.formatHex(
            "#ffffff[" + index + "] " +
            nameColor + name +
            " #c0c0c0(" + percent + "%)"
        );
    }


    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createStabilityItem(Faction f) {
        ItemStack item = IconGetter.getIconOrDefault("stability", Material.BLACK_DYE);
        ItemMeta m = item.getItemMeta();
        StabilityReport report = f.getGovernment().stateReport();
        String color = report.status.getListColor();
        paint(m, color + report.status.getLabel() + "§7: " + color + num(report.stability) + "%", stabilityLore(f, report));
        item.setItemMeta(m);
        return item;
    }

    public ItemStack createLegitimacyItem(Faction f) {
        ItemStack item = IconGetter.getIconOrDefault("overlord", Material.BLACK_DYE);
        ItemMeta m = item.getItemMeta();
        StabilityReport report = f.getGovernment().stateReport();
        String color = scoreColor(report.legitimacy);
        paint(m, "#d4c9aeLegitimacy§7: " + color + num(report.legitimacy) + "%", legitimacyLore(f, report));
        item.setItemMeta(m);
        return item;
    }

    private List<String> stabilityLore(Faction faction, StabilityReport report) {
        StabilityFacts facts = StateStability.facts(faction);
        List<String> lore = new ArrayList<>();
        double legitimacyCost = StabilityMath.curve(report.legitimacy);
        lore.add("#b8ae61Legitimacy: " + (legitimacyCost > 0.05 ? "#d13530-" : "#45c46f-") + num(legitimacyCost));
        lore.add("#b8ae61Weak State: " + (report.weakStateMalus > 0 ? "#d13530-" : "#45c46f-") + num(report.weakStateMalus));
        if (report.weakStateMalus > 0) {
            lore.add("#8f8a7aState needs to be size " + num(report.requiredLevels));
        }
        if (report.overextension > 0) {
            lore.add("#b8ae61Provinces: #d13530-" + num(report.overextension));
        }
        Government government = faction.getGovernment();
        if (government != null && government.getStabilityModifiers() != null) {
            for (StabilityModifier modifier : government.getStabilityModifiers()) {
                if (modifier == null) {
                    continue;
                }
                lore.add("#b8ae61" + modifier.getName() + ": " + (modifier.getModifier() >= 0 ? "#45c46f+" : "#d13530")
                        + num(modifier.getModifier()) + "%");
            }
        }
        if (facts.bankrupt) {
            lore.add("#b8ae61State is bankrupt: #d13530-100%");
        }
        List<String> effects = effectLines(report, StabilityTuning.get());
        if (!effects.isEmpty()) {
            lore.add(" ");
            lore.add("#93c9a7Effects:");
            lore.addAll(effects);
        }
        return lore;
    }

    private List<String> legitimacyLore(Faction faction, StabilityReport report) {
        StabilityTuning tuning = StabilityTuning.get();
        StabilityFacts facts = StateStability.facts(faction);
        List<String> lore = new ArrayList<>();
        lore.add(legitimacyStanding(report.legitimacy));
        List<String> stances = stanceLines(facts, tuning);
        if (!stances.isEmpty()) {
            lore.add(" ");
            lore.add("#93c9a7Stances:");
            lore.addAll(stances);
        }
        lore.add(" ");
        lore.add("#93c9a7Effects:");
        double legitimacyCost = StabilityMath.curve(report.legitimacy);
        lore.add("#b8ae61Stability: " + (legitimacyCost > 0.05 ? "#d13530-" : "#45c46f-") + num(legitimacyCost));
        return lore;
    }

    private static List<String> stanceLines(StabilityFacts facts, StabilityTuning tuning) {
        double base = StabilityMath.baseLegitimacy(facts, tuning);
        int population = 0;
        for (Body body : facts.guilds) {
            if (body != null && !body.realm) {
                population += Math.max(0, body.members);
            }
        }
        for (Body vassal : facts.vassals) {
            if (vassal != null) {
                population += Math.max(0, vassal.members);
            }
        }
        List<String> lines = new ArrayList<>();
        if (population <= 0) {
            return lines;
        }
        double room = Math.max(0, 100 - base);
        for (Body body : facts.guilds) {
            if (body == null || body.realm) {
                continue;
            }
            lines.add(stanceLine(body.name, body.stance, body.members, false, room, population, tuning));
        }
        for (Body vassal : facts.vassals) {
            if (vassal == null) {
                continue;
            }
            lines.add(stanceLine(vassal.name, vassal.stance, vassal.members, true, room, population, tuning));
        }
        return lines;
    }

    private static String stanceLine(String name, String stance, int members, boolean vassal, double room, int population, StabilityTuning tuning) {
        String word = switch (stance == null ? "" : stance.toUpperCase()) {
            case "OPPOSE" -> "#d13530Oppose";
            case "NEUTRAL" -> "#decc68Neutral";
            default -> "#4bc957Support";
        };
        double effect = population <= 0 ? 0 : room * Math.max(0, members) * StabilityMath.weight(stance, tuning) / population;
        String who = strip(name);
        if (vassal) {
            who += " §7(Vassal)";
        }
        return " #d4bb98- #d4c9ae" + who + ": " + word + " §7(+" + num(effect) + "%)";
    }

    private static List<String> effectLines(StabilityReport report, StabilityTuning tuning) {
        List<String> lines = new ArrayList<>();
        double taxPenalty = (1 - report.status.getTaxFactor()) * 100;
        double foreign = StabilityDebuffs.foreignTradeBonus(report.stability, tuning) * 100;
        double adminPenalty = (1 - StabilityDebuffs.adminFactor(report.stability, tuning)) * 100;
        double upkeepIncrease = (StabilityDebuffs.upkeepFactor(report.stability, tuning) - 1) * 100;
        double deJure = StabilityDebuffs.deJureBonus(report.stability, tuning);
        double prestige = StabilityDebuffs.prestigeMalus(report.stability, tuning);
        double outputPenalty = Math.max(0, 100 - report.stability);
        penalty(lines, "Tax Efficiency", taxPenalty);
        penalty(lines, "Foreign Trade Power", foreign);
        penalty(lines, "Admin Power Gain", adminPenalty);
        penalty(lines, "Max Admin Power", adminPenalty);
        penalty(lines, "Max Diplomatic Capacity", adminPenalty);
        penalty(lines, "Law Upkeep", upkeepIncrease);
        penalty(lines, "De Jure Requirement", deJure);
        penalty(lines, "Prestige Malus", prestige);
        penalty(lines, "State Output", outputPenalty);
        if (!report.status.canWageWar()) {
            lines.add("#b8ae61Offensive War: #d13530Locked");
        }
        if (!report.status.canFormTitles()) {
            lines.add("#b8ae61Title Formation: #d13530Locked");
        }
        if (report.illegitimate) {
            lines.add("#b8ae61Movement Organization: #d13530" + num(tuning.movementGainMultiplier) + "x");
        }
        return lines;
    }

    private static void penalty(List<String> lines, String label, double amount) {
        if (amount <= 0.05) {
            return;
        }
        String sign = label.equals("Law Upkeep") || label.equals("Foreign Trade Power") || label.equals("De Jure Requirement") ? "+" : "-";
        lines.add("#b8ae61" + label + ": #d13530" + sign + num(amount) + (label.equals("De Jure Requirement") ? "" : "%"));
    }

    private static String strip(String name) {
        if (name == null || name.isBlank()) {
            return "A guild";
        }
        return name.replaceAll("§x(?:§[0-9a-fA-F]){6}", "").replaceAll("§.", "");
    }

    static String legitimacyStanding(double legitimacy) {
        if (legitimacy < 50) {
            return "#d13530Illegitimate Government";
        }
        if (legitimacy < 65) {
            return "#d1b43fContested Government";
        }
        return "#45c46fLegitimate Government";
    }

    static void paint(ItemMeta meta, String name, List<String> lore) {
        meta.displayName(plain(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(plain(line));
        }
        meta.lore(lines);
    }

    private static Component plain(String legacy) {
        String text = legacy == null ? "" : legacy;
        if (!text.startsWith("#") && !text.startsWith("§")) {
            text = "#d4c9ae" + text;
        }
        return LegacyComponentSerializer.legacySection()
                .deserialize(StringFormatter.formatHex(text))
                .decoration(TextDecoration.ITALIC, false);
    }

    static String scoreColor(double score) {
        if (score >= 75) return "#45c46f";
        if (score >= 50) return "#d1b43f";
        return "#d13530";
    }

    static String num(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == (long) rounded) {
            return Long.toString((long) rounded);
        }
        return Double.toString(rounded);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createStanceItem(Faction f, Guild guild) {
        Stance stance = guild.getStance(f);
        ItemStack item = new ItemStack(Material.YELLOW_CONCRETE);
        if(stance == Stance.OPPOSE) item = new ItemStack(Material.RED_CONCRETE);
        else if(stance == Stance.SUPPORT) item = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta m = item.getItemMeta();
        List<String> lore = new ArrayList<String>();
        lore.add("#28ed70Click to change");
        paint(m, stance.getDisplay(), lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, guild.getId());
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createToggleRefuseButton(Player p, Faction f) {
        boolean refuse = f.getGovernment().getCouncil().refuses(p.getName());
        ItemStack item = TLibs.getItemAPI().getCreator().getItemsAdderItem(refuse ? "mcicons:icon_cancel" : "mcicons:icon_confirm");
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#a27cbfToggle Council Stance"));
        List<String> lore = new ArrayList<String>();
        if(refuse) {
            lore.add(StringFormatter.formatHex("#c74d32You are currently refusing to join the council."));
        } else {
            lore.add(StringFormatter.formatHex("#45c46fYou are currently open to joining the council."));
        }
        lore.add("");
        lore.add(StringFormatter.formatHex("#28ed70Click to toggle"));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, f.getId());
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createTaxTypeItem(Player p, Faction f, TaxTarget target, boolean proposalView) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7"+target.getDisplayName()));
        List<String> lore = new ArrayList<String>();
        if(target == TaxTarget.GUILD_ID || target == TaxTarget.VASSAL_ID || target == TaxTarget.TARIFF_ID) {
            if(proposalView) lore.add(StringFormatter.formatHex("#28ed70Click to view options"));
            else lore.add(StringFormatter.formatHex("#28ed70Click to specific rates"));
        } else {
            Government gov = f.getGovernment();
            Proposal proposal = new Proposal(p.getName(), gov);
            proposal.setTaxProposal(new TaxLawChange(target, "all", 50));
            if(gov.canProposeOrStartMovement(p) && gov.canBeProposed(proposal)) {
                lore.add(StringFormatter.formatHex("#525d5dCurrent Rate: #e3d5a1"+f.getTaxRate(target, null, false)+"%"+ " §8(§7"+f.getTaxRate(target, null, true)+"% effective§8)"));
                if(proposalView) {
                    lore.add(StringFormatter.formatHex("#b8ae61Create a proposal to change"));
                    lore.add(StringFormatter.formatHex("#b8ae61the rate for #62ca43"+target.getDisplayName()+"."));
                }
            }
            else if(proposalView) lore.add(StringFormatter.formatHex("#89504eAnother proposal is active for this target."));
        }
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, target.name());
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createSpecificTaxItem(Player p, Faction f, String id, TaxTarget target) {
        String name = "";
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        if(target == TaxTarget.GUILD_ID) {
            Guild g = FactionManager.getGuildByString(id);
            if(g == null) return null;
            name = g.getName();
            item = g.getBanner().clone();
        } else if(target == TaxTarget.VASSAL_ID) {
            Faction vassal = FactionManager.getByString(id);
            if(vassal == null) return null;
            name = vassal.getName();
            item = vassal.getBanner().clone();
        } else if(target == TaxTarget.TARIFF_ID) {
            Faction faction = FactionManager.getByString(id);
            if(faction == null) return null;
            name = faction.getName();
            item = faction.getBanner().clone();
        }
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex(name));
        List<String> lore = new ArrayList<String>();
        TaxHandler taxHandler = f.getTaxHandler();
        double taxRate = taxHandler.getTaxRate(target, id, false);
        if(taxHandler.hasSpecificTax(target, null)) {
            lore.add(StringFormatter.formatHex("#525d5dCurrent Rate: #e3d5a1"+taxRate+"%"+ " §8(§7"+f.getTaxRate(target, id, true)+"% effective§8)"));
            lore.add(StringFormatter.formatHex("#3f4040(#767a77Base Rate: #928d7a"+taxRate+"%#3f4040)"));
        } else {
            lore.add(StringFormatter.formatHex("#812222No specific "+(target == TaxTarget.TARIFF_ID ? "tariff" : "tax")+" set."));
            lore.add(StringFormatter.formatHex("#3f4040(#767a77Base Rate: #928d7a"+taxRate+"%#3f4040)"));
        }
        Government gov = f.getGovernment();
        Proposal proposal = new Proposal(p.getName(), gov);
        proposal.setTaxProposal(new TaxLawChange(target, id, 50));
        if(gov.canProposeOrStartMovement(p) && gov.canBeProposed(proposal)) lore.add(StringFormatter.formatHex("#28ed70Click to propose a change"));
        else lore.add(StringFormatter.formatHex("#89504eAnother proposal is active for this target."));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, id);
        m.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, target.name());
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createProposalItem(Player p, Faction f) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta m = item.getItemMeta();
        Government gov = f.getGovernment();
        m.setDisplayName(StringFormatter.formatHex(gov.isCouncilMember(p) ? "#28ed70New Proposal" : "#89504eNew Movement"));
        List<String> lore = new ArrayList<String>();
        if(gov.isCouncilMember(p)) {
            lore.add(StringFormatter.formatHex("#b8ae61Create a new proposal to change"));
            lore.add(StringFormatter.formatHex("#b8ae61your faction's laws or taxes."));
            lore.add(StringFormatter.formatHex("#525d5dCurrently Active Proposals: #e3d5a1"+gov.getCouncil().getCurrentProposals(p.getName())+"/2"));
        } else {
            lore.add(StringFormatter.formatHex("#b8ae61Create a new movement to demand"));
            lore.add(StringFormatter.formatHex("#b8ae61changes to your faction's laws or taxes."));
            lore.add(StringFormatter.formatHex("#b8ae61With enough support you can send an"));
            lore.add(StringFormatter.formatHex("#b8ae61ultimatum to the council."));
            lore.add(StringFormatter.formatHex("§7(#812222Potential Civil War§7)"));
        }
        if(gov.canProposeOrStartMovement(p) && gov.getMovementByMember(p.getName()) == null) lore.add(StringFormatter.formatHex("#28ed70Click to start"));
        else lore.add(StringFormatter.formatHex("#89504eYou cannot start a new proposal/movement at this time."));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createProposalListItem(Player p, Faction f) {
        ItemStack item = new ItemStack(Material.BOOKSHELF);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7Current Proposals"));
        List<String> lore = new ArrayList<String>();
        Government gov = f.getGovernment();
        int count = gov.getCouncil().getProposalHandler().getProposals().size();
        lore.add(StringFormatter.formatHex("#85c265Active Proposals§7: §e"+count));
        lore.add("");
        lore.add(StringFormatter.formatHex("#28ed70Click to view"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createMovementListItem(Player p, Faction f) {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#d1743bActive Movements"));
        List<String> lore = new ArrayList<String>();
        Government gov = f.getGovernment();
        int count = gov.getMovements().size();
        lore.add(StringFormatter.formatHex("#85c265Active Movements§7: §e"+count));
        lore.add("");
        lore.add(StringFormatter.formatHex("#7a7a7aMovements represent organized"));
        lore.add(StringFormatter.formatHex("#7a7a7aefforts to enact political change."));
        lore.add("");
        lore.add(StringFormatter.formatHex("#28ed70Click to view"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Existing configuration identifies offline profiles by player name, not UUID.
    @SuppressWarnings("deprecation")
    public ItemStack createCouncilMemberItem(Player player, Faction f, int slot) {
        Council council = f.getGovernment().getCouncil();
        List<String> members = council.getMembers();
        
        String memberName = slot < members.size() ? members.get(slot) : null;
        boolean isEmpty = memberName == null;
        boolean isLeader = player.getName().equalsIgnoreCase(f.getLeader());
        boolean canModify = isLeader && (
            council.getType().equals(Rules.APPOINTED_COUNCIL) ||
            council.getType().equals(Rules.WEALTH_BASED_COUNCIL) ||
            council.getType().equals(Rules.ELECTED_COUNCIL)
        );
        
        // Check if this slot can be appointed to (only next empty slot)
        boolean isNextEmpty = slot == members.size();
        boolean isOccupied = !isEmpty;
        boolean canAppoint = canModify && (isNextEmpty || isOccupied);
        
        ItemStack item;
        if(isEmpty) {
            if(canAppoint) {
                item = new ItemStack(Material.GREEN_CONCRETE);
            } else {
                // Can't appoint yet - not next in order
                item = new ItemStack(Material.RED_CONCRETE);
            }
        } else {
            item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta skullMeta = item.getItemMeta();
            if(skullMeta instanceof org.bukkit.inventory.meta.SkullMeta) {
                ((org.bukkit.inventory.meta.SkullMeta) skullMeta).setOwner(memberName);
            }
            item.setItemMeta(skullMeta);
        }
        
        ItemMeta m = item.getItemMeta();
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        
        if(isEmpty) {
            m.setDisplayName(StringFormatter.formatHex("#89504eEmpty Seat"));
            List<String> lore = new ArrayList<>();
            if(canAppoint) {
                lore.add(StringFormatter.formatHex("#28ed70Click to appoint a member"));
            } else {
                lore.add(StringFormatter.formatHex("#c74d32Must fill seats in order"));
            }
            m.setLore(lore);
        } else {
            m.setDisplayName(StringFormatter.formatHex("#93c9a7" + memberName));
            List<String> lore = new ArrayList<>();
            
            // Display wealth and ranking
            double wealth = Wealth.wealth(memberName);
            List<String> topByWealth = Wealth.topWealth(f, true);
            int ranking = topByWealth.indexOf(memberName) + 1;
            lore.add(StringFormatter.formatHex("#499eccRepresents§7: "+Represents.represents(f, memberName)));
            lore.add(StringFormatter.formatHex("#85c265Wealth§7: #ccbb76" + Formatter.formatDouble(wealth)+"d"));
            lore.add(StringFormatter.formatHex("#85c265Ranking§7: #7a706a" + ranking + "/" + f.getMembers().size()));
            lore.add("");
            
            // Display why they have their seat
            Rules councilType = council.getType();
            if(councilType.equals(Rules.APPOINTED_COUNCIL)) {
                lore.add(StringFormatter.formatHex("#b8ae61Appointed Member"));
            } else if(councilType.equals(Rules.WEALTH_BASED_COUNCIL)) {
                lore.add(StringFormatter.formatHex("#b8ae61Wealth-Based Selection"));
            } else if(councilType.equals(Rules.ELECTED_COUNCIL)) {
                lore.add(StringFormatter.formatHex("#b8ae61Elected Member"));
            }
            
            lore.add("");
            
            // Add modify option if leader
            if(canModify) {
                lore.add(StringFormatter.formatHex("#28ed70Click to replace"));
            }
            
            m.setLore(lore);
        }
        
        // Store member name and slot in persistent data
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, 
            memberName != null ? memberName : "");
        m.getPersistentDataContainer().set(Keys.INT, PersistentDataType.INTEGER, slot);
        
        item.setItemMeta(m);
        return item;
    }

    // Existing configuration identifies offline profiles by player name, not UUID.
    @SuppressWarnings("deprecation")
    public ItemStack createPotentialMemberItem(Player player, Faction f, String member, int slot) {
        
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta skullMeta = item.getItemMeta();
        if(skullMeta instanceof org.bukkit.inventory.meta.SkullMeta) {
            ((org.bukkit.inventory.meta.SkullMeta) skullMeta).setOwner(member);
        }
        item.setItemMeta(skullMeta);
        
        
        ItemMeta m = item.getItemMeta();
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        m.setDisplayName(StringFormatter.formatHex("#93c9a7" + member));
        List<String> lore = new ArrayList<>();
        
        // Display wealth and ranking
        double wealth = Wealth.wealth(member);
        List<String> topByWealth = Wealth.topWealth(f, true);
        int ranking = topByWealth.indexOf(member) + 1;
        lore.add(StringFormatter.formatHex("#499eccRepresents§7: "+Represents.represents(f, member)));
        lore.add(StringFormatter.formatHex("#85c265Wealth§7: #ccbb76" + Formatter.formatDouble(wealth)+"d"));
        lore.add(StringFormatter.formatHex("#85c265Ranking§7: #7a706a" + ranking + "/" + f.getMembers().size()));
        lore.add("");

        lore.add(StringFormatter.formatHex("#28ed70Click to replace"));
        
        m.setLore(lore);
        
        // Store member name and slot in persistent data
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, member);
        m.getPersistentDataContainer().set(Keys.INT, PersistentDataType.INTEGER, slot);
        
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createStartCouncilButton(Player p, Faction f) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#85c265Start Council Meeting"));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#49c96bClick to start"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createCurrentProposalItem(Player p, Faction f, Proposal proposal) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex(proposal.isLawProposal() ? "#93c9a7Law Proposal"
                : proposal.isFeeProposal() ? "#93c9a7Fee Proposal" : "#93c9a7Tax Proposal"));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#85c265Proposed by: #c2bea7"+proposal.getProposer()));
        LoreWriter.applyProposalLore(proposal, lore, p, f, m);
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createProposalTypeItem(String type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta m = item.getItemMeta();
        if(type.equalsIgnoreCase("law")) {
            m.setDisplayName(StringFormatter.formatHex("#93c9a7Law Proposal"));
            List<String> lore = new ArrayList<String>();
            lore.add(StringFormatter.formatHex("#b8ae61Create a proposal to change"));
            lore.add(StringFormatter.formatHex("#b8ae61a law in your faction."));
            m.setLore(lore);
        } else if(type.equalsIgnoreCase("tax")) {
            m.setDisplayName(StringFormatter.formatHex("#93c9a7Tax Proposal"));
            List<String> lore = new ArrayList<String>();
            lore.add(StringFormatter.formatHex("#b8ae61Create a proposal to change"));
            lore.add(StringFormatter.formatHex("#b8ae61the tax rate in your faction."));
            m.setLore(lore);
        } else if(type.equalsIgnoreCase("fee")) {
            m.setDisplayName(StringFormatter.formatHex("#93c9a7Vehicle Fee Proposal"));
            List<String> lore = new ArrayList<String>();
            lore.add(StringFormatter.formatHex("#b8ae61Create a proposal to change the"));
            lore.add(StringFormatter.formatHex("#b8ae61vehicle tax or fees, for every"));
            lore.add(StringFormatter.formatHex("#b8ae61vehicle or just one."));
            m.setLore(lore);
        } else if(type.equalsIgnoreCase("political")) {
            m.setDisplayName(StringFormatter.formatHex("#93c9a7Political Proposal"));
            List<String> lore = new ArrayList<String>();
            lore.add(StringFormatter.formatHex("#b8ae61Create a proposal to change"));
            lore.add(StringFormatter.formatHex("#b8ae61the political landscape in your faction."));
            m.setLore(lore);
        }
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createPoliticalProposalTypeItem(Player p, Faction f, Action action) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta m = item.getItemMeta();
        String actionDisplay = action.getDisplay();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7"+actionDisplay));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#b8ae61Create a proposal to "+actionDisplay));
        PoliticalAction politicalAction = PoliticalActionLoader.getByAction(action);
        lore.addAll(politicalAction.getDescription());
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, action.name());
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createWarPeaceSelectItem(War war, Action action) {
        ItemStack item = new ItemStack(Material.IRON_SWORD, 1);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(war.getName());
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#b8ae61" + action.getDisplay()));
        lore.add(StringFormatter.formatHex("#50e846Click to select this war"));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, String.valueOf(war.getId()));
        item.setItemMeta(m);
        return item;
    }

    // Preserve the existing additional-tooltip component selection and legacy item text; hiding the whole tooltip is different.
    @SuppressWarnings({"deprecation"})
    public ItemStack createFavourRepressEntryButton() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta m = item.getItemMeta();
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        m.setDisplayName(StringFormatter.formatHex("#93c9a7Favour & Repress"));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#b8ae61Manage which guilds and vassals"));
        lore.add(StringFormatter.formatHex("#b8ae61you favour or repress."));
        lore.add("");
        lore.add(StringFormatter.formatHex("#28ed70Click to open"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createFavourButton() {
        ItemStack item = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_confirm");
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#45c46fFavour"));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#b8ae61Favour guilds or vassals"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#28ed70Click to manage"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createRepressButton() {
        ItemStack item = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_cancel");
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#c74d32Repress"));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#b8ae61Repress guilds or vassals"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#28ed70Click to manage"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createGuildsTypeButton(boolean favour) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7Guilds"));
        List<String> lore = new ArrayList<String>();
        Scope scope = favour ? Scope.FAVOURED_GUILDS : Scope.REPRESSED_GUILDS;
        if(Cache.baseEffects.containsKey(scope)) {
            LoreWriter.writeEffect(scope, Cache.baseEffects.get(scope), lore);
            lore.add("");
        }
        lore.add(StringFormatter.formatHex("#28ed70Click to view guilds"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createVassalsTypeButton(boolean favour) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7Vassals"));
        List<String> lore = new ArrayList<String>();
        Scope scope = favour ? Scope.FAVOURED_VASSALS : Scope.REPRESSED_VASSALS;
        if(Cache.baseEffects.containsKey(scope)) {
            LoreWriter.writeEffect(scope, Cache.baseEffects.get(scope), lore);
            lore.add("");
        }
        lore.add(StringFormatter.formatHex("#28ed70Click to view vassals"));
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createFavourRepressGuildItem(Player p, Faction f, Guild guild, boolean isFavourMode) {
        ItemStack item = guild.getBanner().clone();
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7"+guild.getName()));
        List<String> lore = new ArrayList<String>();
        
        Government gov = f.getGovernment();
        
        if(isFavourMode) {
            if(guild.isFavoured()) {
                lore.add(StringFormatter.formatHex("#45c46f✔ Currently Favoured"));
            } else {
                lore.add(StringFormatter.formatHex("#c74d32✖ Not Favoured"));
            }
            
            if(gov.canFavour(guild)) {
                lore.add(StringFormatter.formatHex("#eddda8If Toggled:"));
                EconomicImpact.applyFavourRepressChange(lore, p, f, guild, isFavourMode, false, m, false);
                if(f.isLeader(p.getName())) {
                    if(!guild.isFavoured()) {
                        lore.add(StringFormatter.formatHex("#eddda8Upkeep: §e" + guild.getRepressFavourCost()+" Administrative Power"));
                    }
                    lore.add("");
                    lore.add(StringFormatter.formatHex("#28ed70Click to toggle"));
                }
            } else if(f.isLeader(p.getName())) {
                lore.add("");
                if(guild.isRepressed()) {
                    lore.add(StringFormatter.formatHex("#89504eCannot favour: Guild is repressed"));
                } else {
                    lore.add(StringFormatter.formatHex("#89504eCannot favour this guild"));
                }
            }
        } else {
            if(guild.isRepressed()) {
                lore.add(StringFormatter.formatHex("#c74d32✔ Currently Repressed"));
            } else {
                lore.add(StringFormatter.formatHex("#45c46f✖ Not Repressed"));
            }
            
            if(gov.canRepress(guild)) {
                lore.add(StringFormatter.formatHex("#eddda8If Toggled:"));
                EconomicImpact.applyFavourRepressChange(lore, p, f, guild, isFavourMode, false, m, false);
                if(f.isLeader(p.getName())) {
                    if(!guild.isRepressed()) {
                        lore.add(StringFormatter.formatHex("#eddda8Upkeep: §e" + guild.getRepressFavourCost()+" Administrative Power"));
                    }
                    lore.add("");
                    lore.add(StringFormatter.formatHex("#28ed70Click to toggle"));
                }
            } else if(f.isLeader(p.getName())) {
                lore.add("");
                if(guild.isFavoured()) {
                    lore.add(StringFormatter.formatHex("#89504eCannot repress: Guild is favoured"));
                } else {
                    lore.add(StringFormatter.formatHex("#89504eCannot repress this guild"));
                }
            }
        }
        
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, guild.getId());
        item.setItemMeta(m);
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createFavourRepressVassalItem(Player p, Faction f, Faction vassal, boolean isFavourMode) {
        Guild mainGuild = vassal.getOrCreateMainGuild();
        ItemStack item = vassal.getBanner().clone();
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex("#93c9a7"+vassal.getName()));
        List<String> lore = new ArrayList<String>();
        
        Government gov = f.getGovernment();
        
        if(isFavourMode) {
            if(mainGuild.isFavoured()) {
                lore.add(StringFormatter.formatHex("#45c46f✔ Currently Favoured"));
            } else {
                lore.add(StringFormatter.formatHex("#c74d32✖ Not Favoured"));
            }
            
            if(gov.canFavour(mainGuild)) {
                lore.add(StringFormatter.formatHex("#eddda8If Toggled:"));
                EconomicImpact.applyFavourRepressChange(lore, p, f, mainGuild, isFavourMode, false, m, false);
                if(f.isLeader(p.getName())) {
                    if(!mainGuild.isFavoured()) {
                        lore.add(StringFormatter.formatHex("#eddda8Upkeep: §e" + mainGuild.getRepressFavourCost()+" Administrative Power"));
                    }
                    lore.add("");
                    lore.add(StringFormatter.formatHex("#28ed70Click to toggle"));
                }
            } else if(f.isLeader(p.getName())) {
                lore.add("");
                if(mainGuild.isRepressed()) {
                    lore.add(StringFormatter.formatHex("#89504eCannot favour: Vassal is repressed"));
                } else {
                    lore.add(StringFormatter.formatHex("#89504eCannot favour this vassal"));
                }
            }
        } else {
            if(mainGuild.isRepressed()) {
                lore.add(StringFormatter.formatHex("#c74d32✔ Currently Repressed"));
            } else {
                lore.add(StringFormatter.formatHex("#45c46f✖ Not Repressed"));
            }
            
            if(gov.canRepress(mainGuild)) {
                lore.add(StringFormatter.formatHex("#eddda8If Toggled:"));
                EconomicImpact.applyFavourRepressChange(lore, p, f, mainGuild, isFavourMode, false, m, false);
                if(f.isLeader(p.getName())) {
                    lore.add("");
                    if(!mainGuild.isRepressed()) {
                        lore.add(StringFormatter.formatHex("#eddda8Upkeep: §e" + mainGuild.getRepressFavourCost()+" Administrative Power"));
                    }
                    lore.add(StringFormatter.formatHex("#28ed70Click to toggle"));
                }
            } else if(f.isLeader(p.getName())) {
                lore.add("");
                if(mainGuild.isFavoured()) {
                    lore.add(StringFormatter.formatHex("#89504eCannot repress: Vassal is favoured"));
                } else {
                    lore.add(StringFormatter.formatHex("#89504eCannot repress this vassal"));
                }
            }
        }
        
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, vassal.getId());
        item.setItemMeta(m);
        return item;
    }
}
