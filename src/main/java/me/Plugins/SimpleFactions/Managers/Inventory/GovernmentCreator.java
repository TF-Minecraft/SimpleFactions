package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.PoliticalActionLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.TaxHandler;
import me.Plugins.SimpleFactions.Utils.EconomicImpact;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.LoreWriter;
import me.Plugins.SimpleFactions.Utils.Represents;
import me.Plugins.SimpleFactions.Utils.Wealth;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.enums.Rules;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.enums.Stance;
import me.Plugins.SimpleFactions.government.Council;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.government.election.Candidate;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.PoliticalAction;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.proposal.TaxLawChange;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class GovernmentCreator {
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


    public ItemStack createStabilityItem(Faction f) {
        ItemStack item = IconGetter.getIconOrDefault("stability", Material.BLACK_DYE);
        ItemMeta m = item.getItemMeta();
        Government gov = f.getGovernment();
        m.setDisplayName(StringFormatter.formatHex("#85c265Stability§7: §e"+gov.getStabilityString()+"%"));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#b8ae61Base: #45c46f+"+Formatter.formatDouble(gov.getBaseStability())+"%"));
        double effect = f.getOrCreateMainGuild().getStabilityModifier(f);
        lore.add(StringFormatter.formatHex("#b8ae61From State: " + ( effect >= 0 ? "#45c46f+" : "#d13530")+Formatter.formatDouble(effect)+"%"));
        if(gov.getStabilityMalusFromCouncil() > 0) {
            lore.add(StringFormatter.formatHex("#b8ae61Council too small: #d13530-"+Formatter.formatDouble(gov.getStabilityMalusFromCouncil())+"%"));
        }
        for(StabilityModifier modifier : gov.getStabilityModifiers()) {
            lore.add(StringFormatter.formatHex("#b8ae61"+modifier.getName()+": " + ( modifier.getModifier() >= 0 ? "#45c46f+" : "#d13530")+Formatter.formatDouble(modifier.getModifier())+"%" + " §7("+(modifier.getDecay() >= 0 ? "+#45c46f+" : "#d13530")+Formatter.formatDouble(modifier.getDecay())+"%/hour§7)"));
        }
        if(f.getOrCreateMainGuild().isBankrupt()) {
            lore.add(StringFormatter.formatHex("#b8ae61State is bankrupt! #d13530-100%"));
        }
        if(gov.hasElections() && gov.getVotingBooths().isEmpty()) {
            lore.add(StringFormatter.formatHex("#b8ae61No voting booths! #d13530-75%"));
        }
        lore.add(StringFormatter.formatHex("#93c9a7Stances:"));
        for(Guild guild : f.getGuildHandler().getGuilds()) {
            if(guild.isBase()) continue;
            effect = guild.getStabilityModifier(f);
            lore.add(StringFormatter.formatHex(" #d4bb98- "+guild.getName()+" §7("+guild.getType().getName()+"§7): "+guild.getStance(f).getDisplay()+" §7("+( effect >= 0 ? "#45c46f+" : "#d13530")+Formatter.formatDouble(effect)+"%"+"§7)"));
        }
        for(Faction v : RelationManager.getSubjects(f)) {
            Guild guild = v.getOrCreateMainGuild();
            effect = guild.getStabilityModifier(f);
            lore.add(StringFormatter.formatHex(" #d4bb98- "+guild.getName()+" §7(#4269a8Vassal§7): "+guild.getStance(f).getDisplay()+" §7("+( effect >= 0 ? "#45c46f+" : "#d13530")+Formatter.formatDouble(effect)+"%"+"§7)"));
        }
        
        // Add stability effects section
        if(gov.getStability() < 100) {
            lore.add(" ");
            lore.add(StringFormatter.formatHex("#93c9a7Effects:"));
            
            // Tax Efficiency penalty (1 - taxEfficiency)
            double taxEfficiencyPenalty = (1.0 - gov.getTaxEfficiency()) * 100.0;
            lore.add(StringFormatter.formatHex("#b8ae61Tax Efficiency: #d13530-"+Formatter.formatDouble(taxEfficiencyPenalty)+"%"));
            
            // Admin Power Gain (stability/100 is the multiplier, so penalty from 100% stability)
            double stabilityMultiplier = gov.getStability() / 100.0;
            double powerGainPenalty = (1.0 - stabilityMultiplier) * 100.0;
            lore.add(StringFormatter.formatHex("#b8ae61Admin Power Gain: #d13530-"+Formatter.formatDouble(powerGainPenalty)+"%"));
            
            // Max Admin Power (also uses stability/100 multiplier)
            double maxPowerPenalty = (1.0 - stabilityMultiplier) * 100.0;
            lore.add(StringFormatter.formatHex("#b8ae61Max Admin Power: #d13530-"+Formatter.formatDouble(maxPowerPenalty)+"%"));

            // Max Diplomatic Capacity (also uses stability/100 multiplier)
            double maxDiplomaticPenalty = (1.0 - stabilityMultiplier) * 100.0;
            lore.add(StringFormatter.formatHex("#b8ae61Max Diplomatic Capacity: #d13530-"+Formatter.formatDouble(maxDiplomaticPenalty)+"%"));
            
            // Law Upkeep (uses 3 - stability/50 multiplier, so upkeep increases as stability decreases)
            double upkeepMultiplier = 3.0 - gov.getStability() / 50.0;
            double upkeepIncrease = (upkeepMultiplier - 1.0) * 100.0;
            if(upkeepIncrease > 0) {
                lore.add(StringFormatter.formatHex("#b8ae61Law Upkeep: #d13530+"+Formatter.formatDouble(upkeepIncrease)+"%"));
            } else {
                lore.add(StringFormatter.formatHex("#b8ae61Law Upkeep: #45c46f"+Formatter.formatDouble(upkeepIncrease)+"%"));
            }
        }
        
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    public ItemStack createStanceItem(Faction f, Guild guild) {
        Stance stance = guild.getStance(f);
        ItemStack item = new ItemStack(Material.YELLOW_CONCRETE);
        if(stance == Stance.OPPOSE) item = new ItemStack(Material.RED_CONCRETE);
        else if(stance == Stance.SUPPORT) item = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex(stance.getDisplay()));
        List<String> lore = new ArrayList<String>();
        double effect = guild.getStabilityModifier(f);
        lore.add(StringFormatter.formatHex("#b8ae61Stability Effect: "+( effect >= 0 ? "#45c46f+" : "#d13530")+Formatter.formatDouble(effect)+"%"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#28ed70Click to change"));
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, guild.getId());
        item.setItemMeta(m);
        return item;
    }

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

    public ItemStack createCurrentProposalItem(Player p, Faction f, Proposal proposal) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex(proposal.isLawProposal() ? "#93c9a7Law Proposal" : "#93c9a7Tax Proposal"));
        List<String> lore = new ArrayList<String>();
        lore.add(StringFormatter.formatHex("#85c265Proposed by: #c2bea7"+proposal.getProposer()));
        LoreWriter.applyProposalLore(proposal, lore, p, f);
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

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
                EconomicImpact.applyFavourRepressChange(lore, p, f, guild, isFavourMode);
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
                EconomicImpact.applyFavourRepressChange(lore, p, f, guild, isFavourMode);
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
                EconomicImpact.applyFavourRepressChange(lore, p, f, mainGuild, isFavourMode);
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
                EconomicImpact.applyFavourRepressChange(lore, p, f, mainGuild, isFavourMode);
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
