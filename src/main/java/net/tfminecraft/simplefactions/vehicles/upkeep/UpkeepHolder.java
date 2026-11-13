package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.LinkedHashSet;
import java.util.Set;

import net.tfminecraft.simplefactions.economy.settlement.SettlementGuilds;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.HeldRealms;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

/**
 * Who keeps an upkeep-only vehicle: a faction (paid from its main guild's bank) or a player
 * guild (paid from that guild's bank). Everything is read live, so a renamed realm, a new
 * leader or new members count at once.
 */
public record UpkeepHolder(Kind kind, String id) {
    /** Each member lets the holder keep this many upkeep-only vehicles. */
    public static final int VEHICLES_PER_MEMBER = 3;

    public enum Kind {
        FACTION("faction"),
        GUILD("guild");

        private final String command;

        Kind(String command) {
            this.command = command;
        }

        /** The command root and the word players see: {@code faction} or {@code guild}. */
        public String command() {
            return command;
        }
    }

    public static UpkeepHolder of(Faction faction) {
        return faction == null ? null : new UpkeepHolder(Kind.FACTION, faction.getId());
    }

    /** A player guild; a faction's main guild stands for the faction itself. */
    public static UpkeepHolder of(Guild guild) {
        if (guild == null) return null;
        return guild.isBase() ? of(guild.getFaction()) : new UpkeepHolder(Kind.GUILD, guild.getId());
    }

    /** The holder of an upkeep-only row; null for any other row. */
    public static UpkeepHolder of(PlayerVehicleRecord record) {
        if (record == null || record.getMode() != OwnershipMode.UPKEEP) return null;
        if (record.getGuildId() != null) return new UpkeepHolder(Kind.GUILD, record.getGuildId());
        return record.getFactionId() == null ? null : new UpkeepHolder(Kind.FACTION, record.getFactionId());
    }

    /** The faction or player guild this character leads; null when they lead none. */
    public static UpkeepHolder ledBy(String key, Kind kind) {
        if (key == null) return null;
        if (kind == Kind.FACTION) return of(FactionManager.getByLeader(key));
        Guild guild = FactionManager.getGuildByLeader(key);
        return guild == null || guild.isBase() ? null : of(guild);
    }

    /** The faction this character belongs to, or the player guild they are in or lead; null otherwise. */
    public static UpkeepHolder memberOf(String key, Kind kind) {
        if (key == null) return null;
        if (kind == Kind.FACTION) return of(FactionManager.getByMember(key));
        Guild guild = FactionManager.getGuildByMember(key);
        if (guild == null || guild.isBase()) guild = FactionManager.getGuildByLeader(key);
        return guild == null || guild.isBase() ? null : of(guild);
    }

    /** The faction itself, or the guild's host faction. */
    public Faction faction() {
        if (kind == Kind.FACTION) return FactionManager.getByString(id);
        Guild guild = guild();
        return guild == null ? null : guild.getFaction();
    }

    /** The player guild; null for a faction or a guild that is gone. */
    public Guild guild() {
        if (kind != Kind.GUILD) return null;
        Guild guild = FactionManager.getGuildByString(id);
        return guild == null || guild.isBase() ? null : guild;
    }

    public boolean exists() {
        return kind == Kind.FACTION ? faction() != null : guild() != null;
    }

    /** The leader's member key; null when the holder is gone, blank while a guild has no leader. */
    public String leader() {
        if (kind == Kind.FACTION) {
            Faction faction = faction();
            return faction == null ? null : faction.getLeader();
        }
        Guild guild = guild();
        return guild == null ? null : guild.getLeader();
    }

    public boolean isLeader(String key) {
        return key != null && !key.isBlank() && key.equals(leader());
    }

    /** The VehicleFramework owner its vehicles carry: the leader's character, else {@code char_none}. */
    public String leaderStamp() {
        return OwnerTags.character(Members.characterId(leader()));
    }

    /** Every member, the leader included. */
    public Set<String> members() {
        Set<String> members = new LinkedHashSet<>();
        if (kind == Kind.FACTION) {
            Faction faction = faction();
            if (faction == null) return members;
            members.addAll(faction.getMembers());
        } else {
            Guild guild = guild();
            if (guild == null) return members;
            members.addAll(guild.getMembers());
        }
        String leader = leader();
        if (leader != null && !leader.isBlank()) members.add(leader);
        return members;
    }

    public boolean isMember(String key) {
        if (key == null) return false;
        if (isLeader(key)) return true;
        if (kind == Kind.FACTION) {
            Faction faction = faction();
            return faction != null && faction.isMember(key);
        }
        Guild guild = guild();
        return guild != null && guild.isMember(key);
    }

    /** How many upkeep-only vehicles may be added: three per member. Existing ones are never removed. */
    public int cap() {
        return members().size() * VEHICLES_PER_MEMBER;
    }

    /** The guild whose bank pays: the faction's main guild, or the guild itself. */
    public Guild bank() {
        return kind == Kind.FACTION ? SettlementGuilds.ruling(faction()) : guild();
    }

    /** Waiting for a dead leader's heir: no upkeep is charged and no unpaid day is added. */
    public boolean held() {
        if (kind == Kind.FACTION) return HeldRealms.isHeld(faction());
        Guild guild = guild();
        return guild != null && guild.isHeld();
    }

    public String name() {
        if (kind == Kind.FACTION) {
            Faction faction = faction();
            return faction == null ? id : faction.getName();
        }
        Guild guild = guild();
        return guild == null ? id : guild.getName();
    }

    public boolean holds(PlayerVehicleRecord record) {
        return same(of(record));
    }

    /** The same faction or guild; ids are matched without case, as everywhere else. */
    public boolean same(UpkeepHolder other) {
        return other != null && other.kind == kind && other.id.equalsIgnoreCase(id);
    }

    public PlayerVehicleRecord row(java.util.UUID previousOwner, String vehicle, String type) {
        return PlayerVehicleRecord.upkeep(previousOwner, vehicle, type,
                kind == Kind.FACTION ? id : null, kind == Kind.GUILD ? id : null);
    }

    public java.util.List<PlayerVehicleRecord> vehicles(PlayerVehicleRegistry registry) {
        return registry.getUpkeepVehicles(kind == Kind.FACTION ? id : null, kind == Kind.GUILD ? id : null);
    }
}
