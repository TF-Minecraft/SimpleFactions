package me.Plugins.SimpleFactions.government.movement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Database.CauseData;
import me.Plugins.SimpleFactions.Database.MovementData;
import me.Plugins.SimpleFactions.Database.PoolData;
import me.Plugins.SimpleFactions.Database.ProposalData;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarHostMovementRules;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.enums.Member;
import me.Plugins.SimpleFactions.enums.Stance;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public class Movement {
    private Faction f;
    private String id;

    private String leader;

    private double organization;

    private List<Cause> causes = new ArrayList<>();

    private Pool supporters = new Pool();
    
    private List<Faction> foreignBackers = new ArrayList<>();

    private Phase phase;

    private boolean frozen;

    public Movement(Faction f, String leader, Proposal cause) {
        this.f = f;
        this.leader = leader;
        this.phase = Phase.GATHERING;
        this.id = MovementIds.allocate(leader);
        addCause(new Cause(this, cause, leader));
    }

    public Movement(Faction f, MovementData data) {
        this.f = f;
        if(data.id != null) this.id = data.id;
        else this.id = UUID.randomUUID().toString();
        if(data.leader != null) this.leader = data.leader;
        this.organization = data.organization != null ? data.organization : 0;
        this.causes = new ArrayList<>();
        this.supporters = new Pool();
        this.foreignBackers = new ArrayList<>();

        if(data.phase != null) {
            try {
                this.phase = Phase.valueOf(data.phase);
            } catch (Exception e) {
                e.printStackTrace();
                this.phase = Phase.GATHERING;
            }
        } else {
            this.phase = Phase.GATHERING;
        }
        this.frozen = data.frozen;

        // Deserialize all causes
        if (data.causes != null) {
            for (CauseData causeData : data.causes) {
                try {
                    Proposal proposal = deserializeProposal(f, causeData.proposal);
                    if (proposal == null) continue;
                    
                    Cause cause = new Cause(this, proposal, causeData.leader);
                    
                    // Restore members pool if available
                    if (causeData.members != null) {
                        cause.setPool(deserializePool(f, causeData.members));
                    }
                    
                    causes.add(cause);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        // Deserialize supporters pool
        if (data.supporters != null) {
            supporters = deserializePool(f, data.supporters);
        }
        
        // Deserialize foreign backers
        if (data.foreignBackers != null) {
            for (String backerId : data.foreignBackers) {
                Faction backer = FactionManager.getByString(backerId);
                if (backer != null) {
                    foreignBackers.add(backer);
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public void removeCause(Cause cause) {
        causes.remove(cause);
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void setPhase(Phase newPhase) {
        this.phase = newPhase;
        // Reset organization when changing phase
        if (organization > phase.getMaxOrganization()) {
            organization = phase.getMaxOrganization();
        }
    }

    public boolean canChangeToPhase(Phase targetPhase) {
        // Can't change to current phase
        if (targetPhase == phase) return false;
        
        // Can go back one phase
        if (targetPhase.getIndex() == phase.getIndex() - 1) {
            return true;
        }
        
        // Can advance one phase if organization is at max
        if (targetPhase.getIndex() < 4 && targetPhase.getIndex() == phase.getIndex() + 1 
            && organization >= phase.getMaxOrganization()) {
            return true;
        }
        
        return false;
    }

    public Faction getFaction() {
        return f;
    }

    public double getOrganizationGain() {
        double base = 30;
        base *= getPower()/100.0;
        base /= (double) causes.size();
        for(Cause cause : causes) {
            if(!cause.hasLeader()) {
                base *= 0.6;
            }
            // Apply penalty if CHANGE_LEADER action has no target
            if(cause.getProposal().needsTarget() && !cause.getProposal().hasTarget()) {
                base -= 5;
            }
        }
        if(!hasLeader()) {
            base = -10;
        }
        return Formatter.formatDouble(base);
    }

    public double getMaxOrganization() {
        return phase.getMaxOrganization();
    }

    public void tick() {
        if (frozen) {
            return;
        }
        for (Cause cause : new ArrayList<>(causes)) {
            cause.tick();
        }
        checkSupporters();
        checkForeignBackers();
        if(causes.size() == 0) {
            f.getGovernment().endMovement(this);
        }
    }

    public boolean canForeignBackerJoin(Faction faction, boolean feedback) {
        return foreignBackerBlockReason(faction, false) == null;
    }

    public String foreignBackerBlockReason(Faction faction, boolean staff) {
        if (RelationManager.sameRealm(faction, f)) {
            return MovementJoinCopy.backerSameRealm(staff, faction, f);
        }
        if (faction.getId().equalsIgnoreCase(f.getId())) {
            return MovementJoinCopy.backerOwnFaction(staff, f);
        }
        for (Movement otherMovement : f.getGovernment().getMovements()) {
            if (otherMovement.getId().equals(this.id)) continue;
            if (otherMovement.getForeignBackers().contains(faction)) {
                return MovementJoinCopy.backerAlreadyBackingOther(staff, faction, f);
            }
        }
        return null;
    }

    public boolean isMember(String p) {
        return supporters.getAllMembers().contains(p) || getAllMembers().contains(p);
    }

    public void checkForeignBackers() {
        foreignBackers.removeIf(faction -> !canForeignBackerJoin(faction, false));
    }

    public void checkSupporters() {
        supporters.getCitizens().removeIf(citizen -> !canCitizenBeSupporter(citizen, false));
        supporters.getGuilds().removeIf(guild -> !canGuildBeSupporter(guild, false));
        supporters.getFactions().removeIf(faction -> !canFactionBeSupporter(faction, false));
    }

    public boolean canCitizenBeSupporter(String playerName, boolean feedback) {
        return citizenSupporterBlockReason(playerName, false) == null;
    }

    public String citizenSupporterBlockReason(String playerName, boolean staff) {
        Member relation = f.getRelationToFaction(playerName);
        if (relation != Member.MEMBER) {
            return MovementJoinCopy.citizenMustBeMember(staff, playerName, f);
        }
        for (Movement otherMovement : f.getGovernment().getMovements()) {
            if (otherMovement.getId().equals(this.id)) continue;
            if (otherMovement.isMember(playerName)) {
                return MovementJoinCopy.citizenAlreadySupportingOther(staff, playerName, f);
            }
        }
        return null;
    }

    public boolean canGuildBeSupporter(Guild guild, boolean feedback) {
        return guildSupporterBlockReason(guild, false) == null;
    }

    public String guildSupporterBlockReason(Guild guild, boolean staff) {
        if (CivilWarHostMovementRules.blocksHostGuildJoin(f)) {
            return MovementJoinCopy.oneProvinceHostGuild(staff, guild);
        }
        if (!guild.getFaction().getId().equalsIgnoreCase(f.getId())) {
            return MovementJoinCopy.guildMustBelong(staff, guild, f);
        }
        if (guild.isBase()) {
            return MovementJoinCopy.guildCannotBeBase(staff, guild);
        }
        if (guild.getStance(f) == Stance.SUPPORT) {
            return MovementJoinCopy.guildSupportStance(staff, guild, f);
        }
        for (Movement otherMovement : f.getGovernment().getMovements()) {
            if (otherMovement.getId().equals(this.id)) continue;
            for (String member : guild.getMembers()) {
                if (otherMovement.isMember(member)) {
                    return MovementJoinCopy.guildMemberInOtherMovement(staff, guild, f);
                }
            }
        }
        return null;
    }

    public boolean canFactionBeSupporter(Faction faction, boolean feedback) {
        return factionSupporterBlockReason(faction, false) == null;
    }

    public String factionSupporterBlockReason(Faction faction, boolean staff) {
        if (faction.getOverlord() == null
                || !faction.getOverlord().getId().equalsIgnoreCase(f.getId())) {
            return MovementJoinCopy.notVassalSupporter(staff, faction, f);
        }
        if (faction.getOrCreateMainGuild().getStance(f) == Stance.SUPPORT) {
            return MovementJoinCopy.factionSupportStance(staff, faction, f);
        }
        for (Movement otherMovement : f.getGovernment().getMovements()) {
            if (otherMovement.getId().equals(this.id)) continue;
            for (String member : faction.getMembers()) {
                if (otherMovement.isMember(member)) {
                    return MovementJoinCopy.factionMemberInOtherMovement(staff, faction, f);
                }
            }
        }
        return null;
    }

    public String joinBlockReason(Object obj, Cause cause, boolean staff) {
        if (cause == null) {
            if (obj instanceof String citizen) {
                return citizenSupporterBlockReason(citizen, staff);
            }
            if (obj instanceof Guild guild) {
                return guildSupporterBlockReason(guild, staff);
            }
            if (obj instanceof Faction faction) {
                return factionSupporterBlockReason(faction, staff);
            }
            return MovementJoinCopy.unknownJoinType();
        }
        return cause.joinBlockReason(obj, staff);
    }

    public boolean canJoin(Object obj, Cause cause, boolean feedback) {
        return joinBlockReason(obj, cause, false) == null;
    }

    public boolean canJoin(Object obj, Cause cause) {
        return canJoin(obj, cause, false);
    }

    public void join(Object obj, Cause cause) {
        if (frozen) return;
        double power = getPower();
        if(cause == null) {
            // Joining as general supporter
            if (obj instanceof String) {
                if (!canCitizenBeSupporter((String) obj, false)) return;
                supporters.addCitizen((String) obj);
            } else if (obj instanceof Guild) {
                if (!canGuildBeSupporter((Guild) obj, false)) return;
                supporters.addGuild((Guild) obj);
            } else if (obj instanceof Faction) {
                if (!canFactionBeSupporter((Faction) obj, false)) return;
                supporters.addFaction((Faction) obj);
            }
        } else {
            // Joining a specific cause
            if (!cause.canJoin(obj, false)) return;
            cause.join(obj);
        }
        double powerDiff = getPower() - power;
        changeOrganization(-powerDiff);
    }

    public void leave(Object obj, Cause cause) {
        double power = getPower();
        if(cause == null) {
            // Leaving as general supporter
            if (obj instanceof String) {
                supporters.removeCitizen((String) obj);
            } else if (obj instanceof Guild) {
                supporters.removeGuild((Guild) obj);
            } else if (obj instanceof Faction) {
                supporters.removeFaction((Faction) obj);
            }
        } else {
            // Leaving a specific cause
            cause.leave(obj);
        }
        double powerDiff = getPower() - power;
        changeOrganization(-powerDiff);
    }

    public void joinAsForeignBacker(Faction backer) {
        if (!canForeignBackerJoin(backer, false)) return;
        
        // Check if already backing another movement in this faction
        for (Movement otherMovement : f.getGovernment().getMovements()) {
            if (otherMovement.getId().equals(this.id)) continue;
            if (otherMovement.getForeignBackers().contains(backer)) return;
        }
        
        foreignBackers.add(backer);
    }

    public void leaveAsForeignBacker(Faction backer) {
        foreignBackers.remove(backer);
    }

    public void changeOrganization(double amount) {
        organization += amount;
        if (organization < 0) organization = 0;
        if (organization > getMaxOrganization()) organization = getMaxOrganization();
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public boolean hasLeader() {
        return leader != null;
    }

    public boolean isLeader(String player) {
        return leader != null && leader.equalsIgnoreCase(player);
    }

    public boolean canBeLeader(String player) {
        for(Cause cause : causes) {
            if(cause.getLeader() != null && cause.getLeader().equalsIgnoreCase(player)) {
                return true;
            }
        }
        return false;
    }

    public double getOrganization() {
        return Formatter.formatDouble(organization);
    }

    public List<Cause> getCauses() {
        return causes;
    }

    public Cause getCauseByLeader(String leaderName) {
        for (Cause cause : causes) {
            if (cause.getLeader().equalsIgnoreCase(leaderName)) {
                return cause;
            }
        }
        return null;
    }

    public Pool getSupporters() {
        return supporters;
    }

    public List<String> getAllMembers() {
        List<String> members = new ArrayList<>();
        for (Cause cause : causes) {
            members.addAll(cause.getPool().getAllMembers());
        }
        members.addAll(supporters.getAllMembers());
        return members;
    }

    public List<Faction> getForeignBackers() {
        return foreignBackers;
    }

    public void addCause(Cause cause) {
        causes.add(cause);
    }

    public void createCause(String leader, Proposal proposal) {
        if (frozen) return;
        if (causes.size() >= 3) return; // Max 3 causes
        Cause newCause = new Cause(this, proposal, leader);
        addCause(newCause);
        Member relation = f.getRelationToFaction(leader);
        if (relation == Member.GUILD_LEADER || relation == Member.GUILD_MEMBER) {
            supporters.removeGuild(FactionManager.getGuildByMember(leader));
        } else if (relation == Member.VASSAL_LEADER || relation == Member.VASSAL_MEMBER) {
            supporters.removeFaction(FactionManager.getByMember(leader));
        } else if (relation == Member.MEMBER) {
            supporters.removeCitizen(leader);
        }
    }

    private Proposal deserializeProposal(Faction faction, ProposalData proposalData) {
        if (proposalData == null) return null;
        
        Proposal p = new Proposal(proposalData.proposer, faction.getGovernment());
        
        try {
            if ("law".equals(proposalData.type)) {
                LawGroup group = faction.getLawHandler().getGroup(proposalData.groupId);
                if (group != null) {
                    Law law = group.getLaw(proposalData.lawId);
                    if (law != null) {
                        p.setLawProposal(law);
                        return p;
                    }
                }
            } else if ("tax".equals(proposalData.type)) {
                me.Plugins.SimpleFactions.government.proposal.TaxTarget target = 
                    me.Plugins.SimpleFactions.government.proposal.TaxTarget.valueOf(proposalData.taxTarget);
                me.Plugins.SimpleFactions.government.proposal.TaxLawChange tax = 
                    new me.Plugins.SimpleFactions.government.proposal.TaxLawChange(target, proposalData.taxId, proposalData.newTax);
                p.setTaxProposal(tax);
                return p;
            } else if ("political".equals(proposalData.type)) {
                Action action = Action.valueOf(proposalData.actionKey);
                PoliticalAction politicalAction = new PoliticalAction(action);
                p.setPoliticalActionProposal(politicalAction);
                p.setTarget(proposalData.target);
                return p;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }

    private Pool deserializePool(Faction faction, PoolData poolData) {
        Pool pool = new Pool();
        
        if (poolData.citizens != null) {
            for (String citizen : poolData.citizens) {
                pool.addCitizen(citizen);
            }
        }
        
        if (poolData.guilds != null) {
            for (String guildId : poolData.guilds) {
                Guild guild = faction.getGuildHandler().getGuild(guildId);
                if (guild != null) {
                    pool.addGuild(guild);
                }
            }
        }
        
        if (poolData.factions != null) {
            for (String factionId : poolData.factions) {
                Faction f = FactionManager.getByString(factionId);
                if (f != null) {
                    pool.addFaction(f);
                }
            }
        }
        
        return pool;
    }

    public List<Guild> getAllSupportingGuilds() {
        List<Guild> allGuilds = new ArrayList<>(supporters.getGuilds());
        for (Cause cause : causes) {
            allGuilds.addAll(cause.getPool().getGuilds());
        }
        return allGuilds;
    }

    public List<Faction> getAllSupportingFactions() {
        List<Faction> allFactions = new ArrayList<>(supporters.getFactions());
        for (Cause cause : causes) {
            allFactions.addAll(cause.getPool().getFactions());
        }
        return allFactions;
    }

    public double getPower() {
        double base = 0;
        double memberCount = supporters.getAllMembers().size();
        for(Cause cause : causes) {
            memberCount += cause.getPool().getAllMembers().size();
        }
        for(Faction backer : foreignBackers) {
            memberCount += (backer.getMembers().size() + backer.getVassalMembers().size())/2.0; //backers count half
        }
        if((f.getMembers().size()+f.getVassalMembers().size()) > 0) {
            base += (100.0* (memberCount/(f.getMembers().size()+f.getVassalMembers().size())));
        }
        if(f.getTotalTradePower() > 0) {
            base += (30.0* (getTotalTradePower()/f.getTotalTradePower()));
        }
        if(base > 100) base = 100;
        if(base < 0) base = 0;
        return Formatter.formatDouble(base);
    }

    private double getTotalTradePower() {
        double total = 0;
        for(Guild guild : getAllSupportingGuilds()) {
            total += guild.getTradeBreakdown().getTradePower();
        }
        for(Faction faction : getAllSupportingFactions()) {
            total += faction.getTotalTradePower();
        }
        for (Faction backer : foreignBackers) {
            total += backer.getTotalTradePower()/4.0; //backers contribute a quarter of their trade power
        }
        return Formatter.formatDouble(total);
    }

    public Object getJoiningAs(Player p) {
        Member relation = f.getRelationToFaction(p.getName());
        if (relation == Member.GUILD_LEADER || relation == Member.GUILD_MEMBER) {
            return FactionManager.getGuildByMember(p.getName());
        } else if (relation == Member.VASSAL_LEADER) {
            return FactionManager.getByMember(p.getName());
        } else if (relation == Member.MEMBER) {
            return p.getName();
        }
        return null;
    }

    public boolean quickJoinCheck(Player p) {
        if(isMember(p.getName())) return false;
        Member relation = f.getRelationToFaction(p.getName());
        switch(relation) {
            case FOREIGNER:
            case GUILD_MEMBER:
            case LEADER:
            case VASSAL_MEMBER:
            default:
                return false;
            case MEMBER:
            case VASSAL_LEADER:
            case GUILD_LEADER:
                return true;
            
        }
    }

    public double getStabilityEffect() {
        double effect = 100-getPower();
        if(effect < 0) effect = 0;
        effect /=3;
        effect += 50;
        return Formatter.formatDouble(effect);
    }
}
