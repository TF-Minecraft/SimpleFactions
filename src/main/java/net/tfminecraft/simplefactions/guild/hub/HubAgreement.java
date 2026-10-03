package net.tfminecraft.simplefactions.guild.hub;

/**
 * Terms for one guild hub at another realm's installation.
 * The rate is a whole percent. The fee is whole cents of a denar.
 */
public record HubAgreement(
        String hostFactionId,
        String installationId,
        int taxRatePercent,
        long feeCents,
        int daysRemaining,
        boolean guildRenews,
        boolean hostRenews,
        HubTerms nextTerms,
        boolean dormantNoticeSent) {

    public HubAgreement withDays(int days) {
        return new HubAgreement(hostFactionId, installationId, taxRatePercent, feeCents, days,
                guildRenews, hostRenews, nextTerms, dormantNoticeSent);
    }

    public HubAgreement withRenewal(boolean guildRenews, boolean hostRenews) {
        return new HubAgreement(hostFactionId, installationId, taxRatePercent, feeCents, daysRemaining,
                guildRenews, hostRenews, nextTerms, dormantNoticeSent);
    }

    public HubAgreement withNextTerms(HubTerms nextTerms) {
        return new HubAgreement(hostFactionId, installationId, taxRatePercent, feeCents, daysRemaining,
                guildRenews, hostRenews, nextTerms, dormantNoticeSent);
    }

    public HubAgreement withDormantNotice(boolean sent) {
        return new HubAgreement(hostFactionId, installationId, taxRatePercent, feeCents, daysRemaining,
                guildRenews, hostRenews, nextTerms, sent);
    }

    public boolean willRenew() {
        return guildRenews && hostRenews;
    }
}
