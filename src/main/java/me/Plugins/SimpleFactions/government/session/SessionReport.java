package me.Plugins.SimpleFactions.government.session;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.proposal.TaxLawChange;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;

public class SessionReport {
    private static class ProposalResult {
        Proposal proposal;
        VoteResult result;
        int yay, nay, abstain;
        
        ProposalResult(Proposal proposal, VoteResult result, int yay, int nay, int abstain) {
            this.proposal = proposal;
            this.result = result;
            this.yay = yay;
            this.nay = nay;
            this.abstain = abstain;
        }
    }
    
    private List<ProposalResult> results = new ArrayList<>();
    private String leaderName;
    private Faction faction;
    
    public SessionReport(String leaderName, Faction faction) {
        this.leaderName = leaderName;
        this.faction = faction;
    }
    
    public void addResult(Proposal proposal, VoteResult result, int yay, int nay, int abstain) {
        results.add(new ProposalResult(proposal, result, yay, nay, abstain));
    }
    
    public ItemStack generateReportBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        
        // Format date: dd/mm/yyyy
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1;
        String dateStr = String.format("%02d/%02d/"+Cache.baseYear, day, month);
        meta.setDisplayName(StringFormatter.formatHex("#68ab6fCouncil Session #819483" + dateStr));
        meta.setTitle(StringFormatter.formatHex("#68ab6fCouncil Session #819483" + dateStr));
        meta.setAuthor(faction.getRulerTitle() + " " + leaderName);
        
        List<String> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();
        
        // Title page
        currentPage.append("§6§lCouncil Session§r§0\n");
        currentPage.append(dateStr + "§r\n\n");
        currentPage.append("Total Proposals\n");
        currentPage.append("" + results.size() + "\n\n");
        
        int passed = 0, failed = 0, tied = 0;
        for (ProposalResult pr : results) {
            if (pr.result == VoteResult.PASSED) passed++;
            else if (pr.result == VoteResult.FAILED) failed++;
            else if (pr.result == VoteResult.TIE) tied++;
        }
        
        currentPage.append("§aPassed§0: " + passed + "\n");
        currentPage.append("§cFailed§0: " + failed + "\n");
        currentPage.append("§7Tied§0: " + tied);
        
        pages.add(currentPage.toString());
        
        // Proposal detail pages
        int proposalNum = 1;
        for (ProposalResult pr : results) {
            currentPage = new StringBuilder();
            
            String resultText = pr.result == VoteResult.PASSED ? "§aPASSED" : 
                               pr.result == VoteResult.FAILED ? "§cFAILED" : "§7TIED";
            
            currentPage.append("§6§lProposal " + proposalNum + " - " + resultText + "§r\n\n");
            
            // Get proposal details
            if (pr.proposal.isLawProposal()) {
                Law law = pr.proposal.getLaw();
                if (law != null) {
                    LawGroup group = faction.getLawHandler().getGroup(law.getGroup());
                    String groupName = group != null ? group.getName() : law.getGroup();
                    String oldLaw = group != null && group.getCurrent() != null ? group.getCurrent().getName() : "Unknown";
                    String newLaw = law.getName();
                    
                    currentPage.append("Type: Law\n");
                    currentPage.append("Group: ").append(groupName).append("§r\n\n");
                    currentPage.append(oldLaw).append(" §0→ ").append(newLaw);
                }
            } else if (pr.proposal.isTaxProposal()) {
                TaxLawChange taxChange = pr.proposal.getTaxChange();
                if (taxChange != null) {
                    TaxTarget target = taxChange.getTarget();
                    String name = target != null ? target.getDisplayName() : "Unknown";
                    String type = "";
                    
                    if (target == TaxTarget.GUILD_ID) {
                        Guild guild = FactionManager.getGuildByString(taxChange.getId());
                        if (guild != null) name = guild.getName();
                    } else if (target == TaxTarget.VASSAL_ID) {
                        name = FactionManager.getByString(taxChange.getId()).getName();
                    } else if (target == TaxTarget.TARIFF_ID) {
                        name = FactionManager.getByString(taxChange.getId()).getName();
                    }
                    
                    double oldRate = faction.getTaxRate(target, taxChange.getId(), false);
                    if (oldRate == -1.0) {
                        oldRate = faction.getTaxRate(target, null, false);
                    }
                    
                    currentPage.append("Type: Tax\n");
                    currentPage.append("Target: ").append(name).append("§r\n\n");
                    currentPage.append(String.format("%.0f", oldRate)).append("% §0→ ").append(taxChange.getNewTax()).append("%");
                }
            }
            
            currentPage.append("\n\n§6Votes\n");
            currentPage.append("§a").append(pr.yay).append(" §aYay\n");
            currentPage.append("§c").append(pr.nay).append(" §cNay\n");
            currentPage.append("§7").append(pr.abstain).append(" §7Abstain");
            
            pages.add(currentPage.toString());
            proposalNum++;
        }
        
        meta.setPages(pages);
        item.setItemMeta(meta);
        return item;
    }
}
