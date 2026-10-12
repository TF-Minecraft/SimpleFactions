package net.tfminecraft.simplefactions.identity.death;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/** Main-thread death reconciliation and the settlement's remaining estate-payment claims. */
public final class CharacterDeaths {
    private static DeathJournalStore store;
    private static FactionDeaths factions;

    private CharacterDeaths() {}

    /** After faction loading and settlement recovery, before enabling gameplay or timers. */
    public static void startup(Path dataFolder) throws IOException {
        load(dataFolder);
        reconcile();
    }

    /** Load obligations before settlement recovery; defer cleanup until that recovery is complete. */
    public static void load(Path dataFolder) throws IOException {
        factions = new FactionDeaths(dataFolder);
        store = new DeathJournalStore(dataFolder.resolve("deaths"), factions::apply);
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
                store.record(factions.capture(info.get().ref(), deathId, Instant.now().toEpochMilli()));
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
        else store.record(factions.capture(ref, deathId, at.toEpochMilli()));
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
    }

    private static void requireStarted() throws IOException {
        if (store == null) throw new IOException("Character death journals are not loaded");
    }
}
