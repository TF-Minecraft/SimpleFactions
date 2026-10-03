package net.tfminecraft.simplefactions.guild.hub;

/**
 * An open proposal for a new hub or for the next term.
 * {@code createdAt} is the epoch millis of the last move. The offer lapses from that moment.
 */
public record HubOffer(
        String hostFactionId,
        String installationId,
        int taxRatePercent,
        long feeCents,
        OfferSide awaiting,
        String lastActor,
        long createdAt,
        OfferKind kind) {
}
