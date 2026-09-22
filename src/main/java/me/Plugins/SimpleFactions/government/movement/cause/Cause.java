package me.Plugins.SimpleFactions.government.movement.cause;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.MovementJoinCopy;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Member;
import me.Plugins.SimpleFactions.enums.Stance;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.Pool;
import me.Plugins.SimpleFactions.government.proposal.Proposal;

public class Cause {
    private Movement movement;
    private Action action;
    private Proposal proposal;

    private String leader;
    
    private Pool pool = new Pool();

    public Cause(Movement movement, Proposal proposal, String leader) {
        this.movement = movement;
        this.proposal = proposal;
        action = proposal.getPoliticalAction().getAction();
        this.leader = leader;
        Member relation = movement.getFaction().getRelationToFaction(leader);
        if (relation == Member.GUILD_LEADER || relation == Member.GUILD_MEMBER) {
            pool.addGuild(FactionManager.getGuildByMember(leader));
        } else if (relation == Member.VASSAL_LEADER || relation == Member.VASSAL_MEMBER) {
            pool.addFaction(FactionManager.getByMember(leader));
        } else if (relation == Member.MEMBER) {
            pool.addCitizen(leader);
        } else {
            this.leader = null;
        }
    }

    public void tick() {
        checkMembers(pool);
        if(hasLeader() && !canBeLeader(leader)) {
            leader = null;
        }
        if(isEmpty()) {
            remove();
        }
    }

    public boolean isEmpty() {
        return pool.getAllMembers().isEmpty();
    }

    public void remove() {
        movement.removeCause(this);
    }

    public void join(Object o) {
        if(!canJoin(o, false)) return;
        if (o instanceof Guild guild) {
            pool.addGuild(guild);
        } else if (o instanceof Faction faction) {
            pool.addFaction(faction);
        } else if (o instanceof String citizen) {
            pool.addCitizen(citizen);
        }
    }

    public void leave(Object o) {
        if (o instanceof Guild guild) {
            pool.removeGuild(guild);
        } else if (o instanceof Faction faction) {
            pool.removeFaction(faction);
        } else if (o instanceof String citizen) {
            pool.removeCitizen(citizen);
        }
    }

    public boolean canBeLeader(String p) {
        if(p == null || !pool.getAllMembers().contains(p)) return false;
        Member relation = movement.getFaction().getRelationToFaction(p);
        if (relation == null) return false;
        return switch (relation) {
            case GUILD_LEADER, GUILD_MEMBER -> proposal.getPoliticalAction().allowGuilds();
            case VASSAL_LEADER -> proposal.getPoliticalAction().allowFactions();
            case MEMBER -> proposal.getPoliticalAction().allowCitizens();
            default -> false;
        };
    }

    public boolean canJoin(Object obj, boolean feedback) {
        return joinBlockReason(obj, false) == null;
    }

    public String joinBlockReason(Object obj, boolean staff) {
        if (obj instanceof String citizen) {
            return memberBlockReason(citizen, true, staff);
        }
        if (obj instanceof Guild guild) {
            return guildBlockReason(guild, true, staff);
        }
        if (obj instanceof Faction faction) {
            return factionBlockReason(faction, true, staff);
        }
        return MovementJoinCopy.unknownJoinType();
    }

    public void checkMembers(Pool pool) {

        for (String citizen : new ArrayList<>(pool.getCitizens())) {
            if (!canMemberJoin(citizen, false, false)) {
                pool.remove("citizens", citizen);
            }
        }

        for (Guild guild : new ArrayList<>(pool.getGuilds())) {
            if (!canGuildJoin(guild, false, false)) {
                pool.remove("guild", guild.getName());
            }
        }

        for (Faction faction : new ArrayList<>(pool.getFactions())) {
            if (!canFactionJoin(faction, false, false)) {
                pool.remove("faction", faction.getName());
            }
        }
    }

    public boolean canMemberJoin(String playerName, boolean checkExisting, boolean feedback) {
        return memberBlockReason(playerName, checkExisting, false) == null;
    }

    public String memberBlockReason(String playerName, boolean checkExisting, boolean staff) {
        Member relation = movement.getFaction().getRelationToFaction(playerName);
        if (relation != Member.MEMBER) {
            return MovementJoinCopy.causeCitizenMustBeMember(staff, playerName, movement.getFaction());
        }
        if (!proposal.getPoliticalAction().allowCitizens()) {
            return MovementJoinCopy.causeCitizensNotAllowed(staff, playerName);
        }
        if (checkExisting && movement.isMember(playerName)) {
            return MovementJoinCopy.alreadyCauseMember(staff, playerName);
        }
        return null;
    }

    public boolean canGuildJoin(Guild guild, boolean checkExisting, boolean feedback) {
        return guildBlockReason(guild, checkExisting, false) == null;
    }

    public String guildBlockReason(Guild guild, boolean checkExisting, boolean staff) {
        if (!proposal.getPoliticalAction().allowGuilds()) {
            return MovementJoinCopy.causeGuildsNotAllowed(staff, guild);
        }
        if (guild.isBase()) {
            return MovementJoinCopy.causeGuildBase(staff, guild);
        }
        if (me.Plugins.SimpleFactions.War.civilwar.CivilWarHostMovementRules.blocksHostGuildJoin(movement.getFaction())) {
            return MovementJoinCopy.oneProvinceHostGuild(staff, guild);
        }
        if (!guild.getFaction().getId().equalsIgnoreCase(movement.getFaction().getId())) {
            return MovementJoinCopy.causeGuildWrongFaction(staff, guild, movement.getFaction());
        }
        if (guild.getStance(movement.getFaction()) == Stance.SUPPORT) {
            return MovementJoinCopy.causeGuildSupportStance(staff, guild, movement.getFaction());
        }
        if (checkExisting && movement.isMember(guild.getLeader())) {
            return MovementJoinCopy.causeGuildLeaderAlreadyMember(staff, guild);
        }
        return null;
    }

    public boolean canFactionJoin(Faction faction, boolean checkExisting, boolean feedback) {
        return factionBlockReason(faction, checkExisting, false) == null;
    }

    public String factionBlockReason(Faction faction, boolean checkExisting, boolean staff) {
        if (!proposal.getPoliticalAction().allowFactions()) {
            return MovementJoinCopy.causeFactionsNotAllowed(staff, faction);
        }
        if (faction.getId().equalsIgnoreCase(movement.getFaction().getId())) {
            return MovementJoinCopy.causeOwnFaction(staff, movement.getFaction());
        }
        if (faction.getOverlord() == null
                || !faction.getOverlord().getId().equalsIgnoreCase(movement.getFaction().getId())) {
            return MovementJoinCopy.causeNotVassal(staff, faction, movement.getFaction());
        }
        if (faction.getOrCreateMainGuild().getStance(movement.getFaction()) == Stance.SUPPORT) {
            return MovementJoinCopy.causeFactionSupportStance(staff, faction, movement.getFaction());
        }
        if (checkExisting && movement.isMember(faction.getLeader())) {
            return MovementJoinCopy.causeFactionLeaderAlreadyMember(staff, faction);
        }
        return null;
    }

    public int getIndex() {
        return movement.getCauses().indexOf(this);
    }

    public Movement getMovement() {
        return movement;
    }

    public Action getAction() {
        return action;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public String getLeader() {
        return leader;
    }

    public boolean hasLeader() {
        return leader != null;
    }

    public Pool getPool() {
        return pool;
    }

    public List<String> getFullMemberList() {
        return pool.getAllMembers();
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public List<String> getCitizenList() {
        return pool.getCitizens();
    }
}
