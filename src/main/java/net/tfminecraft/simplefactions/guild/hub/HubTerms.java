package net.tfminecraft.simplefactions.guild.hub;

/** Rate and fee agreed for the term that follows the current one. */
public record HubTerms(int taxRatePercent, long feeCents) {
}
