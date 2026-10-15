package net.tfminecraft.simplefactions.estate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.economy.settlement.SettlementGuilds;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.HeldRealms;
import net.tfminecraft.simplefactions.guild.LeaderlessGuilds;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.identity.death.CharacterDeaths;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.tlibs.character.estate.DeathGuild;
import net.tfminecraft.tlibs.character.estate.EstateFallback;
import net.tfminecraft.tlibs.character.estate.GuildLookup;
import net.tfminecraft.tlibs.character.estate.GuildRef;

/**
 * Guild answers for estates (design E5, E10). It reads death journals and the loaded guilds
 * only: it never re-snapshots a realm and never creates a guild. A realm is named by its
 * ruling guild's incarnation, whose treasury is the realm's.
 */
final class GuildEstateFallback implements EstateFallback {
    @Override
    public DeathGuild deathGuild(UUID characterId, UUID deathId) {
        if (characterId == null || deathId == null || !SimpleFactionsApi.ready()) return DeathGuild.UNAVAILABLE;
        CharacterDeaths.DeathGuild recorded = CharacterDeaths.deathGuild(characterId, deathId);
        return switch (recorded.kind()) {
            case UNAVAILABLE -> DeathGuild.UNAVAILABLE;
            case NONE -> DeathGuild.NONE;
            case GUILD -> {
                if (closing(recorded.incarnation())) yield DeathGuild.UNAVAILABLE;
                Guild live = byIncarnation(recorded.incarnation());
                yield DeathGuild.guild(recorded.incarnation(), live != null,
                        live == null ? Optional.empty() : leader(live), Optional.ofNullable(recorded.realm()));
            }
        };
    }

    @Override
    public GuildLookup guild(UUID incarnation) {
        if (incarnation == null || !SimpleFactionsApi.ready() || closing(incarnation)) {
            return GuildLookup.UNAVAILABLE;
        }
        Guild guild = byIncarnation(incarnation);
        return guild == null ? GuildLookup.GONE : GuildLookup.live(ref(guild));
    }

    /** An incarnation is accepted in place of a name; a name two guilds share cannot be told apart now. */
    @Override
    public GuildLookup guildByName(String name) {
        if (name == null || name.isBlank() || !SimpleFactionsApi.ready()) return GuildLookup.UNAVAILABLE;
        UUID incarnation = Members.characterId(name.trim());
        if (incarnation != null) return guild(incarnation);
        List<Guild> matches = FactionManager.getAllGuilds().stream()
                .filter(guild -> name.trim().equalsIgnoreCase(guild.getName()))
                .toList();
        if (matches.size() > 1) return GuildLookup.UNAVAILABLE;
        return matches.isEmpty() ? GuildLookup.GONE : GuildLookup.live(ref(matches.getFirst()));
    }

    /** A guild being closed, or a held realm, is neither live nor gone yet. */
    private static boolean closing(UUID incarnation) {
        return LeaderlessGuilds.closing(incarnation) || HeldRealms.unsettled(incarnation);
    }

    private static Guild byIncarnation(UUID incarnation) {
        return FactionManager.getAllGuilds().stream()
                .filter(guild -> incarnation.toString().equalsIgnoreCase(guild.getIncarnation()))
                .findFirst().orElse(null);
    }

    static GuildRef ref(Guild guild) {
        Guild ruling = SettlementGuilds.ruling(guild.getFaction());
        String name = guild.getName() == null || guild.getName().isBlank() ? guild.getId() : guild.getName();
        return new GuildRef(guild.getIncarnationId(), name,
                Optional.ofNullable(ruling).map(Guild::getIncarnationId), leader(guild),
                Denars.guild(guild.getIncarnationId()));
    }

    /** {@code Guild.getLeader()}: a ruling guild's leader is its realm's. Empty while the seat is blank. */
    private static Optional<UUID> leader(Guild guild) {
        return Optional.ofNullable(Members.characterId(guild.getLeader()));
    }
}
