package net.tfminecraft.simplefactions.identity.death;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.DurableJson;

/** Reads on every attempt so a failed write never advances the in-memory recovery cursor. */
final class DeathJournalStore {
    @FunctionalInterface
    interface ApplyStep {
        boolean apply(DeathJournal journal, DeathJournal.Step step) throws IOException;
    }

    private final Path folder;
    private final ApplyStep effects;

    DeathJournalStore(Path folder, ApplyStep effects) {
        this.folder = folder;
        this.effects = effects;
    }

    boolean contains(UUID deathId) {
        return Files.exists(path(deathId));
    }

    void record(DeathJournal journal) throws IOException {
        if (!contains(journal.deathId)) DurableJson.write(path(journal.deathId), journal);
        else {
            DeathJournal existing = read(path(journal.deathId));
            if (!existing.ownerId.equals(journal.ownerId) || !existing.characterId.equals(journal.characterId)) {
                throw new IOException("Death ID reused for a different character: " + journal.deathId);
            }
        }
        replay(journal.deathId);
    }

    void replayAll() throws IOException {
        for (DeathJournal journal : all()) replay(journal.deathId);
    }

    private void replay(UUID deathId) throws IOException {
        DeathJournal journal = read(path(deathId));
        if (journal.complete) return;
        for (DeathJournal.Step step : journal.steps) {
            if (step.done) continue;
            if (!effects.apply(journal, step)) continue;
            step.done = true;
            DurableJson.write(path(deathId), journal);
        }
        journal.complete = journal.steps.stream().allMatch(step -> step.done);
        DurableJson.write(path(deathId), journal);
    }

    List<OwedPayment> pendingPayments() throws IOException {
        List<OwedPayment> pending = new ArrayList<>();
        for (DeathJournal journal : all()) {
            for (DeathJournal.Claim claim : journal.claims) {
                long left = claim.payment.cents() - claim.paid;
                if (left <= 0 || !cleaned(journal, claim.cleanupStep)) continue;
                OwedPayment original = claim.payment;
                pending.add(new OwedPayment(original.id(), original.guildIncarnation(),
                        original.characterId(), left, original.kind()));
            }
        }
        return List.copyOf(pending);
    }

    void applyPayments(String settlementId, Map<String, Long> paidCents) throws IOException {
        if (settlementId == null || settlementId.isBlank()) throw new IOException("Missing settlement ID");
        Path receiptFile = folder.resolve("payments").resolve(UUID.nameUUIDFromBytes(
                settlementId.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".json");
        if (Files.exists(receiptFile)) {
            Receipt receipt = DurableJson.read(receiptFile, Receipt.class);
            if (!settlementId.equals(receipt.settlementId) || !paidCents.equals(receipt.paidCents)) {
                throw new IOException("Conflicting death payment acknowledgment: " + settlementId);
            }
        }
        List<DeathJournal> journals = all();
        Map<String, DeathJournal.Claim> known = new HashMap<>();
        Map<String, DeathJournal> owners = new HashMap<>();
        for (DeathJournal journal : journals) {
            for (DeathJournal.Claim claim : journal.claims) {
                known.put(claim.payment.id(), claim);
                owners.put(claim.payment.id(), journal);
            }
        }
        // Validate the entire acknowledgment before the first journal is changed.
        for (var entry : paidCents.entrySet()) {
            DeathJournal.Claim claim = known.get(entry.getKey());
            if (claim == null || entry.getValue() == null || entry.getValue() < 0) {
                throw new IOException("Invalid death payment: " + entry.getKey());
            }
            DeathJournal owner = owners.get(entry.getKey());
            if (!owner.payments.containsKey(settlementId)
                    && (!cleaned(owner, claim.cleanupStep) || entry.getValue() > claim.payment.cents() - claim.paid)) {
                throw new IOException("Death payment exceeds available obligation: " + entry.getKey());
            }
        }
        for (DeathJournal journal : journals) {
            Map<String, Long> portion = new LinkedHashMap<>();
            for (DeathJournal.Claim claim : journal.claims) {
                if (paidCents.containsKey(claim.payment.id())) portion.put(claim.payment.id(), paidCents.get(claim.payment.id()));
            }
            Map<String, Long> previous = journal.payments.get(settlementId);
            if (previous != null && !previous.equals(portion)) throw new IOException("Conflicting death payment acknowledgment: " + settlementId);
        }
        if (!Files.exists(receiptFile)) DurableJson.write(receiptFile, new Receipt(settlementId, Map.copyOf(paidCents)));
        for (DeathJournal journal : journals) {
            Map<String, Long> portion = new LinkedHashMap<>();
            for (DeathJournal.Claim claim : journal.claims) {
                Long cents = paidCents.get(claim.payment.id());
                if (cents != null) portion.put(claim.payment.id(), cents);
            }
            if (portion.isEmpty() || journal.payments.containsKey(settlementId)) continue;
            for (DeathJournal.Claim claim : journal.claims) claim.paid += portion.getOrDefault(claim.payment.id(), 0L);
            journal.payments.put(settlementId, portion);
            DurableJson.write(path(journal.deathId), journal);
        }
    }

    private record Receipt(String settlementId, Map<String, Long> paidCents) {}

    private static boolean cleaned(DeathJournal journal, String id) {
        return journal.steps.stream().anyMatch(step -> step.id.equals(id) && step.done);
    }

    private List<DeathJournal> all() throws IOException {
        if (!Files.exists(folder)) return List.of();
        List<DeathJournal> journals = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                journals.add(read(file));
            }
        }
        return journals;
    }

    private DeathJournal read(Path file) throws IOException {
        DeathJournal journal = DurableJson.read(file, DeathJournal.class);
        if (journal.deathId == null || journal.characterId == null || journal.ownerId == null
                || journal.steps == null || journal.claims == null || journal.payments == null
                || !file.getFileName().toString().equals(journal.deathId + ".json")) {
            throw new IOException("Invalid death journal: " + file);
        }
        var stepIds = new java.util.HashSet<String>();
        for (DeathJournal.Step step : journal.steps) {
            if (step == null || step.id == null || step.kind == null || step.plan == null || !stepIds.add(step.id)) {
                throw new IOException("Invalid death step in " + file);
            }
        }
        var claimIds = new java.util.HashSet<String>();
        for (DeathJournal.Claim claim : journal.claims) {
            if (claim == null || claim.payment == null || claim.payment.id() == null
                    || claim.payment.guildIncarnation() == null || claim.payment.kind() == null
                    || !journal.characterId.equals(claim.payment.characterId()) || claim.payment.cents() <= 0
                    || claim.paid < 0 || claim.paid > claim.payment.cents() || !stepIds.contains(claim.cleanupStep)
                    || !claimIds.add(claim.payment.id())) throw new IOException("Invalid death obligation in " + file);
        }
        return journal;
    }

    private Path path(UUID deathId) {
        return folder.resolve(deathId + ".json");
    }
}
