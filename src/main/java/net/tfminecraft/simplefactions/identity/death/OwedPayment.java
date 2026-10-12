package net.tfminecraft.simplefactions.identity.death;

import java.util.UUID;

/** An unpaid claim preserved when death removes a character from its guild. */
public record OwedPayment(String id, UUID guildIncarnation, UUID characterId, long cents, Kind kind) {
    public enum Kind { WAGES, DIVIDENDS }
}
