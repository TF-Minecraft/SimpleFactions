package net.tfminecraft.simplefactions.identity.death;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/** Main-thread death reconciliation and the settlement's remaining estate-payment claims. */
public final class CharacterDeaths {
    private static DeathJournalStore store;
    private static FactionDeaths factions;
    private static LockGuildIndex lockGuilds;

    private CharacterDeaths() {}

    /** After faction loading and settlement recovery, before enabling gameplay or timers. */
    public static void startup(Path dataFolder) throws IOException {
        load(dataFolder);
        reconcile();
        indexLockGuilds();
    }

    /** Load obligations before settlement recovery; defer cleanup until that recovery is complete. */
    public static void load(Path dataFolder) throws IOException {
        factions = new FactionDeaths(dataFolder);
        store = new DeathJournalStore(dataFolder.resolve("deaths"), factions::apply, CharacterDeaths::replayGuard);
        lockGuilds = new LockGuildIndex();
        store.pendingPayments();
    }

    /** Called at startup and hourly; incomplete journals replay even if membership is already gone. */
    public static void reconcile() throws IOException {
        requireStarted();
        store.replayAll();
        for (String key : factions.referencedCharacters()) {
            var info = Members.info(key);
            if (info.isEmpty() || info.get().status() != CharacterStatus.DEAD) continue;
            UUID deathId = Characters.deathId(info.get().ref().characterId())
                    .orElseThrow(() -> new IOException("Dead character has no authoritative death ID: " + key));
            if (!store.contains(deathId)) {
                record(factions.capture(info.get().ref(), deathId, Instant.now().toEpochMilli()));
            }
        }
    }

    public static void onDeath(CharacterRef ref, UUID deathId, Instant at) throws IOException {
        requireStarted();
        var info = Characters.info(ref.characterId());
        if (info.isPresent() && (!info.get().ref().equals(ref) || info.get().status() != CharacterStatus.DEAD)) return;
        var currentDeath = Characters.deathId(ref.characterId());
        if (currentDeath.isPresent() && !currentDeath.get().equals(deathId)) return;
        if (store.contains(deathId)) store.replayAll();
        else record(factions.capture(ref, deathId, at.toEpochMilli()));
    }

    /** After reconcile and before SimpleFactions is ready; lock lookups never read journals. */
    public static void indexLockGuilds() throws IOException {
        requireStarted();
        lockGuilds.rebuild(store.all());
    }

    /** The guild incarnation a dead character's locks follow; empty when unknown, guildless, tied or not loaded. */
    public static Optional<UUID> lockGuildIncarnation(UUID characterId) {
        LockGuildIndex index = lockGuilds;
        return index == null ? Optional.empty() : index.get(characterId);
    }

    /**
     * Replay acts only on the life the journal belongs to. A revived or re-dead character's
     * journal is closed; an unreadable one waits, and so does a MISSING one, which staff decide (E13).
     */
    static DeathJournalStore.Replay replayGuard(DeathJournal journal) {
        var info = Characters.info(journal.characterId);
        if (info.isEmpty()) return DeathJournalStore.Replay.SKIP;
        return switch (info.get().status()) {
            case ALIVE -> DeathJournalStore.Replay.CLOSE_REVIVE;
            case MISSING -> DeathJournalStore.Replay.SKIP;
            case DEAD -> Characters.deathId(journal.characterId)
                    .map(current -> current.equals(journal.deathId)
                            ? DeathJournalStore.Replay.REPLAY : DeathJournalStore.Replay.CLOSE_REDEATH)
                    .orElse(DeathJournalStore.Replay.SKIP);
        };
    }

    /** The guild a death journal records; {@code realm} is its ruling guild's incarnation, null when unknown. */
    public record DeathGuild(Kind kind, UUID incarnation, UUID realm) {
        public enum Kind { UNAVAILABLE, NONE, GUILD }
        static final DeathGuild UNAVAILABLE = new DeathGuild(Kind.UNAVAILABLE, null, null);
        static final DeathGuild NONE = new DeathGuild(Kind.NONE, null, null);
    }

    /**
     * The guild this death left, read only from its journal: never re-snapshotted, never created.
     * UNAVAILABLE while not loaded, when the journal cannot be read or names another character,
     * and while a character with no journal is still referenced by a realm or warband (the
     * reconcile has yet to capture it).
     */
    public static DeathGuild deathGuild(UUID characterId, UUID deathId) {
        DeathJournalStore current = store;
        FactionDeaths adapter = factions;
        if (current == null || adapter == null) return DeathGuild.UNAVAILABLE;
        try {
            Optional<DeathJournal> found = current.find(deathId);
            if (found.isEmpty()) {
                return adapter.referencedCharacters().contains(Members.key(characterId))
                        ? DeathGuild.UNAVAILABLE : DeathGuild.NONE;
            }
            DeathJournal journal = found.get();
            if (!journal.characterId.equals(characterId)) return DeathGuild.UNAVAILABLE;
            UUID incarnation = LockGuildIndex.incarnation(journal);
            if (incarnation == null) return DeathGuild.NONE;
            UUID realm = journal.lockGuildRealm != null ? journal.lockGuildRealm : storedRealm(journal, incarnation);
            return new DeathGuild(DeathGuild.Kind.GUILD, incarnation, realm);
        } catch (IOException error) {
            return DeathGuild.UNAVAILABLE;
        }
    }

    /**
     * For journals written before {@code lockGuildRealm}: the one realm whose stored rights plan
     * lists the guild. None or several leave it unknown.
     */
    static UUID storedRealm(DeathJournal journal, UUID incarnation) {
        java.util.Set<UUID> realms = new java.util.HashSet<>();
        for (DeathJournal.Step step : journal.steps) {
            if (!step.kind.equals("rights") || !(step.plan.get("guilds") instanceof com.google.gson.JsonArray guilds)) continue;
            for (var guild : guilds) {
                if (guild instanceof com.google.gson.JsonObject entry
                        && incarnation.equals(LockGuildIndex.uuid(entry.get("incarnation")))) {
                    realms.add(LockGuildIndex.uuid(step.plan.get("realm")));
                }
            }
        }
        return realms.size() == 1 ? realms.iterator().next() : null;
    }

    /** A written journal updates the index even when its cleanup has to be retried. */
    private static void record(DeathJournal journal) throws IOException {
        try {
            store.record(journal);
        } finally {
            if (store.contains(journal.deathId)) lockGuilds.capture(journal);
        }
    }

    public static List<OwedPayment> pendingPayments() {
        try {
            requireStarted();
            return store.pendingPayments();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Replay-safe partial payments: the same settlement must acknowledge the same amounts. */
    public static void applyPayments(String settlementId, Map<String, Long> paidCents) throws IOException {
        requireStarted();
        store.applyPayments(settlementId, paidCents);
    }

    public static void stop() {
        store = null;
        factions = null;
        lockGuilds = null;
    }

    private static void requireStarted() throws IOException {
        if (store == null) throw new IOException("Character death journals are not loaded");
    }
}
