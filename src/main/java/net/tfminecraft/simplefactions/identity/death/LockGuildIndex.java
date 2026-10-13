package net.tfminecraft.simplefactions.identity.death;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Character ID to the guild incarnation a dead owner's GUILD and FACTION locks follow. */
final class LockGuildIndex {
    private final Map<UUID, UUID> entries = new HashMap<>();

    /** Only each character's most recent journal counts; two journals at the same moment deny. */
    void rebuild(List<DeathJournal> journals) {
        Map<UUID, List<DeathJournal>> latest = new HashMap<>();
        for (DeathJournal journal : journals) {
            List<DeathJournal> winners = latest.get(journal.characterId);
            if (winners == null || journal.at > winners.getFirst().at) {
                latest.put(journal.characterId, new ArrayList<>(List.of(journal)));
            } else if (journal.at == winners.getFirst().at) {
                winners.add(journal);
            }
        }
        entries.clear();
        latest.forEach((character, winners) -> {
            UUID incarnation = winners.size() == 1 ? incarnation(winners.getFirst()) : null;
            if (incarnation != null) entries.put(character, incarnation);
        });
    }

    /** A guildless death removes the entry an earlier death left. */
    void capture(DeathJournal journal) {
        if (journal.lockGuildIncarnation == null) entries.remove(journal.characterId);
        else entries.put(journal.characterId, journal.lockGuildIncarnation);
    }

    Optional<UUID> get(UUID characterId) {
        return Optional.ofNullable(entries.get(characterId));
    }

    /**
     * Journals written before the field existed use the first guild listing the
     * character in the rights snapshots, in their order, without civil-war mapping.
     */
    static UUID incarnation(DeathJournal journal) {
        if (journal.lockGuildIncarnation != null) return journal.lockGuildIncarnation;
        JsonPrimitive key = new JsonPrimitive(journal.characterId.toString());
        for (DeathJournal.Step step : journal.steps) {
            if (!step.kind.equals("rights") || !step.plan.has("guilds")) continue;
            for (JsonElement guild : step.plan.getAsJsonArray("guilds")) {
                if (guild.getAsJsonObject().getAsJsonArray("members").contains(key)) {
                    return UUID.fromString(guild.getAsJsonObject().get("incarnation").getAsString());
                }
            }
        }
        return null;
    }
}
