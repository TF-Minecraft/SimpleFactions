package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 * guild (paid from that guild's bank). A guild is named by its incarnation ID, which a later
 * guild with the same name never shares. Everything is read live, so a renamed realm, a new
 * leader or new members count at once, and a guild that has become a realm's main guild
 * (rebellion, elevation, eviction) counts as that realm.
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

    /** A player guild by incarnation; a realm's main guild stands for the realm itself. */
    public static UpkeepHolder of(Guild guild) {
        if (guild == null) return null;
        return guild.isBase() ? of(guild.getFaction()) : new UpkeepHolder(Kind.GUILD, guild.getIncarnation());
    }

    /** The holder of an upkeep-only row, as it stands now; null for any other row. */
    public static UpkeepHolder of(PlayerVehicleRecord record) {
        if (record == null || record.getMode() != OwnershipMode.UPKEEP) return null;
        if (record.getGuildId() != null) {
            Guild guild = findGuild(record.getGuildId());
            // The guild became a realm's main guild: its vehicles are that realm's now.
            if (guild != null && guild.isBase()) return of(guild.getFaction());
            return new UpkeepHolder(Kind.GUILD, record.getGuildId());
        }
        return record.getFactionId() == null ? null : new UpkeepHolder(Kind.FACTION, record.getFactionId());
    }

    /** The guild with this incarnation; rows saved before incarnations were used name the guild ID. */
    static Guild findGuild(String key) {
        if (key == null) return null;
        for (Guild guild : FactionManager.getAllGuilds()) {
            if (guild != null && key.equalsIgnoreCase(guild.getIncarnation())) return guild;
        }
        return Members.characterId(key) == null ? FactionManager.getGuildByString(key) : null;
    }

    /** The player guild (never a realm's main guild) this character leads, else null. */
    static Guild ledGuild(String key) {
        for (Guild guild : FactionManager.getAllGuilds()) {
            if (guild != null && !guild.isBase() && guild.isLeader(key)) return guild;
        }
        return null;
    }

    /** The faction or player guild this character leads; null when they lead none. */
    public static UpkeepHolder ledBy(String key, Kind kind) {
        if (key == null) return null;
        if (kind == Kind.FACTION) return of(FactionManager.getByLeader(key));
        return of(ledGuild(key));
    }

    /** The faction this character belongs to, or the player guild they are in or lead; null otherwise. */
    public static UpkeepHolder memberOf(String key, Kind kind) {
        if (key == null) return null;
        if (kind == Kind.FACTION) return of(FactionManager.getByMember(key));
        Guild led = ledGuild(key);
        if (led != null) return of(led);
        for (Guild guild : FactionManager.getAllGuilds()) {
            if (guild != null && !guild.isBase() && guild.isMember(key)) return of(guild);
        }
        return null;
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
        Guild guild = findGuild(id);
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

    /**
     * Waiting for a leader: a held realm (dead leader, heir pending), a guild with no leader, or a
     * guild in a held realm. No upkeep is charged and no unpaid day is added meanwhile.
     */
    public boolean held() {
        if (kind == Kind.FACTION) return HeldRealms.isHeld(faction());
        Guild guild = guild();
        return guild != null && (guild.isLeaderless() || HeldRealms.isHeld(guild.getFaction()));
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

    /** The holder's rows, including those of a guild that has since become this realm's main guild. */
    public List<PlayerVehicleRecord> vehicles(PlayerVehicleRegistry registry) {
        if (kind == Kind.GUILD) return registry.getUpkeepVehicles(null, id);
        List<PlayerVehicleRecord> out = new ArrayList<>(registry.getUpkeepVehicles(id, null));
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (record.getMode() == OwnershipMode.UPKEEP && record.getGuildId() != null && holds(record)) {
                out.add(record);
            }
        }
        return out;
    }
}
