package net.tfminecraft.simplefactions.utils;

import java.util.UUID;

/** Character lookup seam used by ledger wage previews. Payouts belong to the realm settlement. */
public final class PostSettlementPayouts {
    @FunctionalInterface
    public interface PlayerUuidLookup { UUID uuidOf(String member); }
    private PostSettlementPayouts() {}
}
