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
        store = new DeathJournalStore(dataFolder.resolve("deaths"), factions::apply);
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
