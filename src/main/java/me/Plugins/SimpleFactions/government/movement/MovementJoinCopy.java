package me.Plugins.SimpleFactions.government.movement;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;

public final class MovementJoinCopy {
	private MovementJoinCopy() {}

	public static String pick(boolean staff, String player, String staffLine) {
		return staff ? staffLine : player;
	}

	public static String factionId(Faction faction) {
		if (faction == null || faction.getId() == null) {
			return "?";
		}
		return faction.getId();
	}

	public static String guildId(Guild guild) {
		if (guild == null || guild.getId() == null) {
			return "?";
		}
		return guild.getId();
	}

	public static String citizenMustBeMember(boolean staff, String playerName, Faction host) {
		return pick(
				staff,
				"§cYou must be a member of the movement's faction to support it.",
				"§c" + playerName + " is not a citizen of " + factionId(host) + ".");
	}

	public static String citizenAlreadySupportingOther(boolean staff, String playerName, Faction host) {
		return pick(
				staff,
				"§cYou are already supporting another movement in this faction.",
				"§c" + playerName + " is already supporting another movement in " + factionId(host) + ".");
	}

	public static String oneProvinceHostGuild(boolean staff, Guild guild) {
		return pick(
				staff,
				CivilWarCopy.ONE_PROVINCE_HOST_GUILD,
				"§c" + guildId(guild) + " cannot join: host guilds cannot start or join a movement in a one-province faction.");
	}

	public static String guildMustBelong(boolean staff, Guild guild, Faction host) {
		return pick(
				staff,
				"§cYour guild must belong to the movement's faction to support it.",
				"§c" + guildId(guild) + " does not belong to " + factionId(host) + ".");
	}

	public static String guildCannotBeBase(boolean staff, Guild guild) {
		return pick(
				staff,
				"§cYour guild cannot be the base guild to support the movement.",
				"§c" + guildId(guild) + " is the base guild and cannot support the movement.");
	}

	public static String guildSupportStance(boolean staff, Guild guild, Faction host) {
		return pick(
				staff,
				"§cYour guild cannot have SUPPORT stance towards the movement's faction.",
				"§c" + guildId(guild) + " has SUPPORT stance towards " + factionId(host) + ".");
	}

	public static String guildMemberInOtherMovement(boolean staff, Guild guild, Faction host) {
		return pick(
				staff,
				"§cA member of your guild is already supporting another movement in this faction.",
				"§cA member of " + guildId(guild) + " is already supporting another movement in " + factionId(host) + ".");
	}

	public static String notVassalSupporter(boolean staff, Faction joining, Faction host) {
		return pick(
				staff,
				"§cYour faction must be a vassal of the movement's faction to support it.",
				"§c" + factionId(joining) + " is not a vassal of " + factionId(host) + ".");
	}

	public static String factionSupportStance(boolean staff, Faction joining, Faction host) {
		return pick(
				staff,
				"§cYour faction cannot have SUPPORT stance towards the movement's faction.",
				"§c" + factionId(joining) + " has SUPPORT stance towards " + factionId(host) + ".");
	}

	public static String factionMemberInOtherMovement(boolean staff, Faction joining, Faction host) {
		return pick(
				staff,
				"§cA member of your faction is already supporting another movement in this faction.",
				"§cA member of " + factionId(joining) + " is already supporting another movement in " + factionId(host) + ".");
	}

	public static String backerSameRealm(boolean staff, Faction joining, Faction host) {
		return pick(
				staff,
				"§cYou cannot join as a foreign backer from the same realm.",
				"§c" + factionId(joining) + " is in the same realm as " + factionId(host) + " and cannot be a foreign backer.");
	}

	public static String backerOwnFaction(boolean staff, Faction host) {
		return pick(
				staff,
				"§cYour own faction cannot be a foreign backer.",
				"§c" + factionId(host) + " cannot be a foreign backer of its own movement.");
	}

	public static String backerAlreadyBackingOther(boolean staff, Faction joining, Faction host) {
		return pick(
				staff,
				"§cYour faction is already backing another movement in this faction.",
				"§c" + factionId(joining) + " is already backing another movement in " + factionId(host) + ".");
	}

	public static String causeCitizenMustBeMember(boolean staff, String playerName, Faction host) {
		return pick(
				staff,
				"§cYou must be a member of the movement's faction to join it.",
				"§c" + playerName + " is not a citizen of " + factionId(host) + ".");
	}

	public static String causeCitizensNotAllowed(boolean staff, String playerName) {
		return pick(
				staff,
				"§cCitizens are not allowed to join this movement as members.",
				"§cCitizens cannot join this cause (blocked for " + playerName + ").");
	}

	public static String alreadyCauseMember(boolean staff, String playerName) {
		return pick(
				staff,
				"§cYou are already a member of this movement.",
				"§c" + playerName + " is already a member of this movement.");
	}

	public static String causeGuildsNotAllowed(boolean staff, Guild guild) {
		return pick(
				staff,
				"§cGuilds are not allowed to join this movement as members.",
				"§cGuilds cannot join this cause (blocked for " + guildId(guild) + ").");
	}

	public static String causeGuildBase(boolean staff, Guild guild) {
		return pick(
				staff,
				"§cGuild cannot be the base guild of the faction.",
				"§c" + guildId(guild) + " is the base guild and cannot join this cause.");
	}

	public static String causeGuildWrongFaction(boolean staff, Guild guild, Faction host) {
		return pick(
				staff,
				"§cGuild must belong to the same faction as the movement.",
				"§c" + guildId(guild) + " does not belong to " + factionId(host) + ".");
	}

	public static String causeGuildSupportStance(boolean staff, Guild guild, Faction host) {
		return pick(
				staff,
				"§cGuilds that support the movement's faction cannot join.",
				"§c" + guildId(guild) + " has SUPPORT stance towards " + factionId(host) + ".");
	}

	public static String causeGuildLeaderAlreadyMember(boolean staff, Guild guild) {
		return pick(
				staff,
				"§cGuild leader is already a member of this movement.",
				"§cLeader of " + guildId(guild) + " is already a member of this movement.");
	}

	public static String causeFactionsNotAllowed(boolean staff, Faction joining) {
		return pick(
				staff,
				"§cFactions are not allowed to join this movement as members.",
				"§cVassals cannot join this cause (blocked for " + factionId(joining) + ").");
	}

	public static String causeOwnFaction(boolean staff, Faction host) {
		return pick(
				staff,
				"§cFaction cannot join its own movement.",
				"§c" + factionId(host) + " cannot join its own movement as a vassal.");
	}

	public static String causeNotVassal(boolean staff, Faction joining, Faction host) {
		return pick(
				staff,
				"§cYour faction must be a vassal of the movement's faction to join it.",
				"§c" + factionId(joining) + " is not a vassal of " + factionId(host) + ".");
	}

	public static String causeFactionSupportStance(boolean staff, Faction joining, Faction host) {
		return pick(
				staff,
				"§cFactions that support the movement's faction cannot join.",
				"§c" + factionId(joining) + " has SUPPORT stance towards " + factionId(host) + ".");
	}

	public static String causeFactionLeaderAlreadyMember(boolean staff, Faction joining) {
		return pick(
				staff,
				"§cFaction leader is already a member of this movement.",
				"§cLeader of " + factionId(joining) + " is already a member of this movement.");
	}

	public static String unknownJoinType() {
		return "§cCannot join as that member type.";
	}
}
