package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.HubAgreementData;
import net.tfminecraft.simplefactions.database.HubOfferData;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.BuildFailure;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

/**
 * Hub agreement rules. Decision methods do not call Bukkit. Menus call the public
 * actions; each one returns a player-facing message. The daily tick is separate.
 */
public final class HubAgreementService {
    static final long DAY_MILLIS = 86_400_000L;

    private HubAgreementService() {
    }

    public record AgreementResult(boolean succeeded, String message, List<HubNotice> notices, boolean builtHub) {
        public static AgreementResult ok(String message, List<HubNotice> notices, boolean builtHub) {
            return new AgreementResult(true, message, notices == null ? List.of() : List.copyOf(notices), builtHub);
        }

        public static AgreementResult fail(String message) {
            return new AgreementResult(false, message, List.of(), false);
        }
    }

    public record HubNotice(String playerName, String message) {
    }

    public record RateRange(int minPercent, int maxPercent, boolean canHost) {
    }

    public record PendingMatter(Kind kind, String guildId, String hostFactionId, String installationId, String message) {
        public enum Kind {
            OFFER,
            EXPIRING
        }
    }

    public static AgreementResult propose(
            Guild guild, String actorName, String hostFactionId, String installationId, int ratePercent, double feeDenars) {
        AgreementResult result = propose(
                guild, actorName, hostFactionId, installationId, ratePercent, feeDenars,
                HubAgreementFacts.LIVE, System.currentTimeMillis());
        HubAgreementMessenger.deliver(result.notices());
        return result;
    }

    public static AgreementResult counter(
            Guild guild, String actorName, String hostFactionId, String installationId, int ratePercent, double feeDenars) {
        AgreementResult result = counter(
                guild, actorName, hostFactionId, installationId, ratePercent, feeDenars,
                HubAgreementFacts.LIVE, System.currentTimeMillis());
        HubAgreementMessenger.deliver(result.notices());
        return result;
    }

    public static AgreementResult accept(
            Guild guild, String actorName, String hostFactionId, String installationId) {
        AgreementResult result = accept(
                guild, actorName, hostFactionId, installationId, HubAgreementFacts.LIVE, System.currentTimeMillis());
        if (result.builtHub()) {
            SupplyHubCommands.recalculateTrade();
        }
        HubAgreementMessenger.deliver(result.notices());
        return result;
    }

    public static AgreementResult decline(
            Guild guild, String actorName, String hostFactionId, String installationId) {
        AgreementResult result = decline(
                guild, actorName, hostFactionId, installationId, HubAgreementFacts.LIVE, System.currentTimeMillis());
        HubAgreementMessenger.deliver(result.notices());
        return result;
    }

    public static AgreementResult withdraw(
            Guild guild, String actorName, String hostFactionId, String installationId) {
        AgreementResult result = withdraw(
                guild, actorName, hostFactionId, installationId, HubAgreementFacts.LIVE, System.currentTimeMillis());
        HubAgreementMessenger.deliver(result.notices());
        return result;
    }

    public static AgreementResult setAutoRenewal(
            Guild guild, String actorName, String hostFactionId, String installationId, boolean enabled) {
        AgreementResult result = setAutoRenewal(
                guild, actorName, hostFactionId, installationId, enabled, HubAgreementFacts.LIVE);
        HubAgreementMessenger.deliver(result.notices());
        return result;
    }

    public static RateRange allowedRateRange(Faction host) {
        if (host == null || host.getTaxHandler() == null || !host.hasFactionRule(Rules.HUB_TAX)) {
            return new RateRange(0, 0, false);
        }
        int min = (int) Math.round(host.getTaxHandler().getMin(TaxTarget.HUB_TAX));
        int max = (int) Math.round(host.getTaxHandler().getMax(TaxTarget.HUB_TAX));
        if (max < min) {
            return new RateRange(0, 0, false);
        }
        return new RateRange(min, max, true);
    }

    public static List<PendingMatter> pendingFor(String playerName) {
        return pendingFor(playerName, SupplyHubService.allGuilds(), HubAgreementFacts.LIVE);
    }

    public static String joinSummary(String playerName) {
        return summarize(pendingFor(playerName));
    }

    public static List<HubNotice> tick(Iterable<Guild> guilds, long nowMillis) {
        boolean[] hubsRemoved = {false};
        List<HubNotice> notices = tick(guilds, nowMillis, HubAgreementFacts.LIVE, hubsRemoved);
        if (hubsRemoved[0]) {
            SupplyHubCommands.recalculateTrade();
        }
        HubAgreementMessenger.deliver(notices);
        return notices;
    }

    public static boolean hasAgreement(Guild guild, String hostFactionId, String installationId) {
        return findAgreement(guild, hostFactionId, installationId) != null;
    }

    public static HubOffer offer(Guild guild, String hostFactionId, String installationId) {
        return findOffer(guild, hostFactionId, installationId);
    }

    public static HubAgreement findAgreement(Guild guild, String hostFactionId, String installationId) {
        if (guild == null || guild.getHubAgreements() == null) {
            return null;
        }
        for (HubAgreement agreement : guild.getHubAgreements()) {
            if (samePlace(agreement == null ? null : agreement.hostFactionId(),
                    agreement == null ? null : agreement.installationId(), hostFactionId, installationId)) {
                return agreement;
            }
        }
        return null;
    }

    /** The guild removed its hub. The agreement and any offer end with it. */
    public static void onHubRemoved(Guild guild, String hostFactionId, String installationId) {
        dropAgreement(guild, hostFactionId, installationId);
        dropOffer(guild, hostFactionId, installationId);
    }

    static AgreementResult propose(
            Guild guild, String actorName, String hostFactionId, String installationId,
            int ratePercent, double feeDenars, HubAgreementFacts facts, long nowMillis) {
        if (guild == null || facts == null) {
            return AgreementResult.fail("§cThat guild does not exist");
        }
        if (!facts.hostExists(hostFactionId)) {
            return AgreementResult.fail("§cThat faction does not exist");
        }
        if (facts.sameRealm(guild, hostFactionId)) {
            return AgreementResult.fail("§cA hub in your own realm does not need an agreement");
        }
        if (!facts.hostHasHubTax(hostFactionId)) {
            return AgreementResult.fail("§cThat faction's economy cannot host a supply hub");
        }
        String termsProblem = termsProblem(ratePercent, feeDenars, facts, hostFactionId);
        if (termsProblem != null) {
            return AgreementResult.fail(termsProblem);
        }
        if (findOffer(guild, hostFactionId, installationId) != null) {
            return AgreementResult.fail("§cThere is already an offer for this installation");
        }
        long cents = wholeCents(feeDenars);
        HubAgreement existing = findAgreement(guild, hostFactionId, installationId);
        if (existing != null) {
            if (!facts.isGuildLeader(guild, actorName) && !facts.isHostCouncil(hostFactionId, actorName)) {
                return AgreementResult.fail("§cOnly the guild leader or the host's council can offer new terms");
            }
            if (!hasHub(guild, hostFactionId, installationId)) {
                return AgreementResult.fail("§cThat hub is no longer there");
            }
            OfferSide awaiting = facts.isGuildLeader(guild, actorName) ? OfferSide.HOST : OfferSide.GUILD;
            HubOffer offer = new HubOffer(
                    hostFactionId, installationId, ratePercent, cents, awaiting, actorName, nowMillis, OfferKind.NEXT_TERM);
            if (!addOffer(guild, offer)) {
                return AgreementResult.fail("§cThat offer could not be saved");
            }
            return AgreementResult.ok(
                    "§aTerms offered for the next term: §e" + ratePercent + "%§a and §e" + money(cents) + "§a a day",
                    tellOtherSide(guild, hostFactionId, awaiting, facts,
                            "§eNew terms were offered for the hub at §f" + facts.installationLabel(hostFactionId, installationId)
                                    + "§e: §f" + ratePercent + "%§e and §f" + money(cents) + "§e a day"),
                    false);
        }
        if (!facts.isGuildLeader(guild, actorName)) {
            return AgreementResult.fail("§cOnly the guild leader can propose a hub");
        }
        String buildProblem = buildProblem(guild, hostFactionId, installationId, facts, true);
        if (buildProblem != null) {
            return AgreementResult.fail(buildProblem);
        }
        HubOffer offer = new HubOffer(
                hostFactionId, installationId, ratePercent, cents, OfferSide.HOST, actorName, nowMillis, OfferKind.NEW_HUB);
        if (!addOffer(guild, offer)) {
            return AgreementResult.fail("§cThat offer could not be saved");
        }
        return AgreementResult.ok(
                "§aOffer sent: §e" + ratePercent + "%§a and §e" + money(cents) + "§a a day",
                tell(facts.council(hostFactionId),
                        "§e" + guildName(guild) + " §eoffered a hub at §f"
                                + facts.installationLabel(hostFactionId, installationId)
                                + "§e: §f" + ratePercent + "%§e and §f" + money(cents) + "§e a day"),
                false);
    }

    static AgreementResult counter(
            Guild guild, String actorName, String hostFactionId, String installationId,
            int ratePercent, double feeDenars, HubAgreementFacts facts, long nowMillis) {
        HubOffer offer = readyOffer(guild, hostFactionId, installationId, facts, nowMillis);
        if (offer == null) {
            return missingOrLapsed(guild, hostFactionId, installationId, facts, nowMillis);
        }
        if (!actsFor(offer.awaiting(), guild, actorName, hostFactionId, facts)) {
            return AgreementResult.fail(wrongSide(offer.awaiting()));
        }
        String termsProblem = termsProblem(ratePercent, feeDenars, facts, hostFactionId);
        if (termsProblem != null) {
            return AgreementResult.fail(termsProblem);
        }
        long cents = wholeCents(feeDenars);
        OfferSide next = offer.awaiting() == OfferSide.HOST ? OfferSide.GUILD : OfferSide.HOST;
        HubOffer updated = new HubOffer(
                offer.hostFactionId(), offer.installationId(), ratePercent, cents, next, actorName, nowMillis, offer.kind());
        replaceOffer(guild, offer, updated);
        return AgreementResult.ok(
                "§aTerms sent back: §e" + ratePercent + "%§a and §e" + money(cents) + "§a a day",
                tellOtherSide(guild, hostFactionId, next, facts,
                        "§eNew hub terms for §f" + facts.installationLabel(hostFactionId, installationId)
                                + "§e: §f" + ratePercent + "%§e and §f" + money(cents) + "§e a day"),
                false);
    }

    static AgreementResult accept(
            Guild guild, String actorName, String hostFactionId, String installationId,
            HubAgreementFacts facts, long nowMillis) {
        HubOffer offer = readyOffer(guild, hostFactionId, installationId, facts, nowMillis);
        if (offer == null) {
            return missingOrLapsed(guild, hostFactionId, installationId, facts, nowMillis);
        }
        if (offer.lastActor() != null && offer.lastActor().equalsIgnoreCase(actorName)) {
            return AgreementResult.fail("§cYou can only accept terms the other side sent");
        }
        if (!actsFor(offer.awaiting(), guild, actorName, hostFactionId, facts)) {
            OfferSide sender = offer.awaiting() == OfferSide.HOST ? OfferSide.GUILD : OfferSide.HOST;
            if (actsFor(sender, guild, actorName, hostFactionId, facts)) {
                return AgreementResult.fail("§cYou can only accept terms the other side sent");
            }
            return AgreementResult.fail(wrongSide(offer.awaiting()));
        }
        if (!facts.hostHasHubTax(hostFactionId)) {
            return AgreementResult.fail("§cThat faction's economy cannot host a supply hub");
        }
        String termsProblem = termsProblem(offer.taxRatePercent(), offer.feeCents() / 100.0, facts, hostFactionId);
        if (termsProblem != null) {
            return AgreementResult.fail(termsProblem);
        }
        String label = facts.installationLabel(hostFactionId, installationId);
        if (offer.kind() == OfferKind.NEXT_TERM) {
            HubAgreement agreement = findAgreement(guild, hostFactionId, installationId);
            if (agreement == null || !hasHub(guild, hostFactionId, installationId)) {
                dropOffer(guild, hostFactionId, installationId);
                return AgreementResult.fail("§cThat hub is no longer there");
            }
            HubAgreement updated = agreement
                    .withNextTerms(new HubTerms(offer.taxRatePercent(), offer.feeCents()))
                    .withRenewal(true, true);
            replaceAgreement(guild, agreement, updated);
            dropOffer(guild, hostFactionId, installationId);
            List<HubNotice> notices = new ArrayList<>();
            notices.addAll(tell(List.of(facts.guildLeader(guild)),
                    "§aThe next term at §f" + label + " §awill be §f" + offer.taxRatePercent()
                            + "%§a and §f" + money(offer.feeCents()) + "§a a day"));
            notices.addAll(tell(facts.council(hostFactionId),
                    "§a" + guildName(guild) + " §aagreed the next term at §f" + label
                            + "§a: §f" + offer.taxRatePercent() + "%§a and §f" + money(offer.feeCents()) + "§a a day"));
            return AgreementResult.ok(
                    "§aThose terms will apply on renewal, and auto-renewal is on for both sides",
                    notices, false);
        }
        String buildProblem = buildProblem(guild, hostFactionId, installationId, facts, false);
        if (buildProblem != null) {
            return AgreementResult.fail(buildProblem);
        }
        List<SupplyHub> hubs = guild.getSupplyHubs();
        if (hubs == null) {
            return AgreementResult.fail("§cThat hub could not be built");
        }
        hubs.add(new SupplyHub(hostFactionId, installationId, nowMillis));
        int days = Math.max(1, facts.agreementDays());
        HubAgreement agreement = new HubAgreement(
                hostFactionId, installationId, offer.taxRatePercent(), offer.feeCents(),
                days, true, true, null, false);
        if (!addAgreement(guild, agreement)) {
            hubs.remove(hubs.size() - 1);
            return AgreementResult.fail("§cThat agreement could not be saved");
        }
        dropOffer(guild, hostFactionId, installationId);
        List<HubNotice> notices = new ArrayList<>();
        notices.addAll(tell(List.of(facts.guildLeader(guild)),
                "§aHub agreement started at §f" + label + " §afor §f" + days + "§a days"));
        notices.addAll(tell(facts.council(hostFactionId),
                "§a" + guildName(guild) + " §abuilt a hub at §f" + label
                        + "§a for §f" + days + "§a days at §f" + offer.taxRatePercent()
                        + "%§a and §f" + money(offer.feeCents()) + "§a a day"));
        return AgreementResult.ok(
                "§aAgreement started for §e" + days + "§a days at §e" + offer.taxRatePercent()
                        + "%§a and §e" + money(offer.feeCents()) + "§a a day",
                notices, true);
    }

    static AgreementResult decline(
            Guild guild, String actorName, String hostFactionId, String installationId,
            HubAgreementFacts facts, long nowMillis) {
        HubOffer offer = readyOffer(guild, hostFactionId, installationId, facts, nowMillis);
        if (offer == null) {
            return missingOrLapsed(guild, hostFactionId, installationId, facts, nowMillis);
        }
        if (!actsFor(offer.awaiting(), guild, actorName, hostFactionId, facts)) {
            return AgreementResult.fail(wrongSide(offer.awaiting()));
        }
        dropOffer(guild, hostFactionId, installationId);
        OfferSide other = offer.awaiting() == OfferSide.HOST ? OfferSide.GUILD : OfferSide.HOST;
        return AgreementResult.ok("§cOffer declined",
                tellOtherSide(guild, hostFactionId, other, facts,
                        "§cThe hub offer for §f" + facts.installationLabel(hostFactionId, installationId) + " §cwas declined"),
                false);
    }

    static AgreementResult withdraw(
            Guild guild, String actorName, String hostFactionId, String installationId,
            HubAgreementFacts facts, long nowMillis) {
        HubOffer offer = readyOffer(guild, hostFactionId, installationId, facts, nowMillis);
        if (offer == null) {
            return missingOrLapsed(guild, hostFactionId, installationId, facts, nowMillis);
        }
        OfferSide sender = offer.awaiting() == OfferSide.HOST ? OfferSide.GUILD : OfferSide.HOST;
        if (!actsFor(sender, guild, actorName, hostFactionId, facts)) {
            return AgreementResult.fail("§cOnly the side that sent the current terms can withdraw them");
        }
        dropOffer(guild, hostFactionId, installationId);
        return AgreementResult.ok("§aOffer withdrawn",
                tellOtherSide(guild, hostFactionId, offer.awaiting(), facts,
                        "§7The hub offer for §f" + facts.installationLabel(hostFactionId, installationId) + " §7was withdrawn"),
                false);
    }

    static AgreementResult setAutoRenewal(
            Guild guild, String actorName, String hostFactionId, String installationId,
            boolean enabled, HubAgreementFacts facts) {
        HubAgreement agreement = findAgreement(guild, hostFactionId, installationId);
        if (agreement == null) {
            return AgreementResult.fail("§cThere is no hub agreement there");
        }
        boolean leader = facts.isGuildLeader(guild, actorName);
        boolean council = facts.isHostCouncil(hostFactionId, actorName);
        if (!leader && !council) {
            return AgreementResult.fail("§cOnly the guild leader or the host's council can change renewal");
        }
        boolean guildRenews = leader ? enabled : agreement.guildRenews();
        boolean hostRenews = (!leader && council) ? enabled : agreement.hostRenews();
        replaceAgreement(guild, agreement, agreement.withRenewal(guildRenews, hostRenews));
        String whose = leader ? "Your guild's auto-renewal" : "The host's auto-renewal";
        return AgreementResult.ok("§a" + whose + " is now " + (enabled ? "on" : "off"), List.of(), false);
    }

    static List<PendingMatter> pendingFor(String playerName, Iterable<Guild> guilds, HubAgreementFacts facts) {
        List<PendingMatter> pending = new ArrayList<>();
        if (playerName == null || guilds == null || facts == null) {
            return pending;
        }
        for (Guild guild : guilds) {
            if (guild == null) {
                continue;
            }
            boolean leader = facts.isGuildLeader(guild, playerName);
            if (guild.getHubOffers() != null) {
                for (HubOffer offer : guild.getHubOffers()) {
                    if (offer == null || !actsFor(offer.awaiting(), guild, playerName, offer.hostFactionId(), facts)) {
                        continue;
                    }
                    pending.add(new PendingMatter(
                            PendingMatter.Kind.OFFER, guild.getId(), offer.hostFactionId(), offer.installationId(),
                            "§eHub offer waiting at §f" + facts.installationLabel(offer.hostFactionId(), offer.installationId())
                                    + "§e: §f" + offer.taxRatePercent() + "%§e and §f" + money(offer.feeCents()) + "§e a day"));
                }
            }
            if (guild.getHubAgreements() == null) {
                continue;
            }
            for (HubAgreement agreement : guild.getHubAgreements()) {
                if (agreement == null || agreement.willRenew() || agreement.daysRemaining() <= 0
                        || agreement.daysRemaining() > 3) {
                    continue;
                }
                boolean council = facts.isHostCouncil(agreement.hostFactionId(), playerName);
                if (!leader && !council) {
                    continue;
                }
                pending.add(new PendingMatter(
                        PendingMatter.Kind.EXPIRING, guild.getId(), agreement.hostFactionId(), agreement.installationId(),
                        "§eHub agreement at §f"
                                + facts.installationLabel(agreement.hostFactionId(), agreement.installationId())
                                + " §eends in §f" + agreement.daysRemaining() + "§e "
                                + (agreement.daysRemaining() == 1 ? "day" : "days") + " and will not renew"));
            }
        }
        return pending;
    }

    static String summarize(List<PendingMatter> pending) {
        int offers = 0;
        int expiring = 0;
        if (pending != null) {
            for (PendingMatter matter : pending) {
                if (matter == null) {
                    continue;
                }
                if (matter.kind() == PendingMatter.Kind.OFFER) {
                    offers++;
                } else {
                    expiring++;
                }
            }
        }
        if (offers == 0 && expiring == 0) {
            return null;
        }
        StringBuilder text = new StringBuilder("§e");
        if (offers > 0) {
            text.append(offers).append(offers == 1 ? " hub offer is waiting on you" : " hub offers are waiting on you");
        }
        if (offers > 0 && expiring > 0) {
            text.append(". §e");
        }
        if (expiring > 0) {
            text.append(expiring).append(expiring == 1
                    ? " hub agreement ends within 3 days and will not renew"
                    : " hub agreements end within 3 days and will not renew");
        }
        text.append(".");
        return text.toString();
    }

    static List<HubNotice> tick(Iterable<Guild> guilds, long nowMillis, HubAgreementFacts facts) {
        return tick(guilds, nowMillis, facts, null);
    }

    static List<HubNotice> tick(
            Iterable<Guild> guilds, long nowMillis, HubAgreementFacts facts, boolean[] hubsRemoved) {
        List<HubNotice> notices = new ArrayList<>();
        if (guilds == null || facts == null) {
            return notices;
        }
        for (Guild guild : guilds) {
            if (guild == null) {
                continue;
            }
            if (tickAgreements(guild, facts, notices) && hubsRemoved != null && hubsRemoved.length > 0) {
                hubsRemoved[0] = true;
            }
            tickOffers(guild, nowMillis, facts);
        }
        return notices;
    }

    static List<HubNotice> onTransferred(
            Iterable<Guild> guilds, String fromFactionId, String toFactionId, String installationId, HubAgreementFacts facts) {
        List<HubNotice> notices = new ArrayList<>();
        if (guilds == null || toFactionId == null || installationId == null || facts == null) {
            return notices;
        }
        for (Guild guild : guilds) {
            if (guild == null || guild.getSupplyHubs() == null) {
                continue;
            }
            SupplyHub hub = SupplyHubService.findHub(guild.getSupplyHubs(), fromFactionId, installationId);
            if (hub == null) {
                continue;
            }
            if (facts.sameRealm(guild, toFactionId)) {
                List<SupplyHub> hubs = guild.getSupplyHubs();
                int index = hubs.indexOf(hub);
                if (index >= 0) {
                    hubs.set(index, new SupplyHub(toFactionId, hub.installationId(), hub.createdAt()));
                }
                dropAgreement(guild, fromFactionId, installationId);
                dropOffer(guild, fromFactionId, installationId);
                continue;
            }
            guild.getSupplyHubs().remove(hub);
            dropAgreement(guild, fromFactionId, installationId);
            dropOffer(guild, fromFactionId, installationId);
            String label = facts.installationLabel(toFactionId, installationId);
            if (label.equals(installationId)) {
                label = facts.installationLabel(fromFactionId, installationId);
            }
            String message = "§cThe supply hub of §f" + guildName(guild) + " §cat §f" + label
                    + " §cwas removed because the installation moved to another realm";
            notices.addAll(tell(List.of(facts.guildLeader(guild)), message));
            notices.addAll(tell(facts.council(fromFactionId), message));
            notices.addAll(tell(facts.council(toFactionId), message));
        }
        return dedupe(notices);
    }

    static List<HubNotice> onInstallationRemoved(
            Iterable<Guild> guilds, String ownerFactionId, String installationId, HubAgreementFacts facts) {
        List<HubNotice> notices = new ArrayList<>();
        if (guilds == null) {
            return notices;
        }
        for (Guild guild : guilds) {
            if (guild == null) {
                continue;
            }
            boolean hadHub = guild.getSupplyHubs() != null
                    && SupplyHubService.findHub(guild.getSupplyHubs(), ownerFactionId, installationId) != null;
            boolean hadAgreement = findAgreement(guild, ownerFactionId, installationId) != null;
            if (guild.getSupplyHubs() != null) {
                SupplyHubService.removeInstallation(List.of(guild), ownerFactionId, installationId);
            }
            dropAgreement(guild, ownerFactionId, installationId);
            dropOffer(guild, ownerFactionId, installationId);
            if ((hadHub || hadAgreement) && facts != null) {
                notices.addAll(tell(List.of(facts.guildLeader(guild)),
                        "§cYour supply hub at §f" + facts.installationLabel(ownerFactionId, installationId)
                                + " §cwas removed because the installation is gone"));
            }
        }
        return notices;
    }

    /** Foreign hubs saved without an agreement cannot stay. Returns one log line per hub removed. */
    static List<String> removeUnagreedForeignHubs(Iterable<Guild> guilds, HubAgreementFacts facts) {
        List<String> lines = new ArrayList<>();
        if (guilds == null || facts == null) {
            return lines;
        }
        for (Guild guild : guilds) {
            if (guild == null || guild.getSupplyHubs() == null) {
                continue;
            }
            List<SupplyHub> hubs = guild.getSupplyHubs();
            for (int i = hubs.size() - 1; i >= 0; i--) {
                SupplyHub hub = hubs.get(i);
                if (hub == null) {
                    continue;
                }
                if (facts.sameRealm(guild, hub.ownerFactionId())
                        || hasAgreement(guild, hub.ownerFactionId(), hub.installationId())) {
                    continue;
                }
                hubs.remove(i);
                dropOffer(guild, hub.ownerFactionId(), hub.installationId());
                lines.add("Guild " + guildName(guild) + " (" + guild.getId()
                        + ") lost its supply hub at " + hub.installationId()
                        + ": a foreign hub needs an agreement");
            }
        }
        return lines;
    }

    static void logRemovedForeignHubs(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        try {
            SimpleFactions plugin = SimpleFactions.getInstance();
            if (plugin == null) {
                return;
            }
            for (String line : lines) {
                plugin.getLogger().info(line);
            }
        } catch (Throwable ignored) {
        }
    }

    static double feeDenars(Guild guild, SupplyHub hub) {
        if (hub == null) {
            return 0;
        }
        HubAgreement agreement = findAgreement(guild, hub.ownerFactionId(), hub.installationId());
        if (agreement == null || agreement.feeCents() <= 0) {
            return 0;
        }
        return agreement.feeCents() / 100.0;
    }

    static void endFor(Guild guild, List<SupplyHub> removed) {
        if (guild == null || removed == null) {
            return;
        }
        for (SupplyHub hub : removed) {
            if (hub == null) {
                continue;
            }
            dropAgreement(guild, hub.ownerFactionId(), hub.installationId());
            dropOffer(guild, hub.ownerFactionId(), hub.installationId());
        }
    }

    static int taxRatePercent(Guild guild, SupplyHub hub) {
        HubAgreement agreement = hub == null ? null : findAgreement(guild, hub.ownerFactionId(), hub.installationId());
        return agreement == null ? 0 : agreement.taxRatePercent();
    }

    public static List<HubAgreement> fromAgreementData(List<HubAgreementData> data) {
        List<HubAgreement> agreements = new ArrayList<>();
        if (data == null) {
            return agreements;
        }
        for (HubAgreementData entry : data) {
            HubAgreement agreement = fromAgreementData(entry);
            if (agreement != null) {
                agreements.add(agreement);
            }
        }
        return agreements;
    }

    static HubAgreement fromAgreementData(HubAgreementData data) {
        if (data == null || blank(data.faction) || blank(data.installation)
                || data.rate == null || data.dailyFee == null || data.daysRemaining == null) {
            return null;
        }
        Long cents = wholeCents(data.dailyFee);
        if (cents == null) {
            cents = Math.round(data.dailyFee * 100.0);
            if (cents < 0) {
                return null;
            }
        }
        HubTerms next = null;
        if (data.nextRate != null && data.nextFee != null) {
            long nextCents = Math.max(0, Math.round(data.nextFee * 100.0));
            next = new HubTerms(data.nextRate, nextCents);
        }
        return new HubAgreement(
                data.faction, data.installation, data.rate, cents, data.daysRemaining,
                data.guildRenews == null || data.guildRenews,
                data.hostRenews == null || data.hostRenews,
                next, Boolean.TRUE.equals(data.dormantNotice));
    }

    public static List<HubAgreementData> toAgreementData(List<HubAgreement> agreements) {
        List<HubAgreementData> data = new ArrayList<>();
        if (agreements == null) {
            return data;
        }
        for (HubAgreement agreement : agreements) {
            if (agreement == null || blank(agreement.hostFactionId()) || blank(agreement.installationId())) {
                continue;
            }
            HubAgreementData entry = new HubAgreementData();
            entry.faction = agreement.hostFactionId();
            entry.installation = agreement.installationId();
            entry.rate = agreement.taxRatePercent();
            entry.dailyFee = agreement.feeCents() / 100.0;
            entry.daysRemaining = agreement.daysRemaining();
            entry.guildRenews = agreement.guildRenews();
            entry.hostRenews = agreement.hostRenews();
            if (agreement.nextTerms() != null) {
                entry.nextRate = agreement.nextTerms().taxRatePercent();
                entry.nextFee = agreement.nextTerms().feeCents() / 100.0;
            }
            entry.dormantNotice = agreement.dormantNoticeSent();
            data.add(entry);
        }
        return data;
    }

    public static List<HubOffer> fromOfferData(List<HubOfferData> data) {
        List<HubOffer> offers = new ArrayList<>();
        if (data == null) {
            return offers;
        }
        for (HubOfferData entry : data) {
            HubOffer offer = fromOfferData(entry);
            if (offer != null) {
                offers.add(offer);
            }
        }
        return offers;
    }

    static HubOffer fromOfferData(HubOfferData data) {
        if (data == null || blank(data.faction) || blank(data.installation)
                || data.rate == null || data.dailyFee == null) {
            return null;
        }
        OfferSide awaiting = "guild".equalsIgnoreCase(data.awaiting) ? OfferSide.GUILD : OfferSide.HOST;
        OfferKind kind = "next".equalsIgnoreCase(data.kind) ? OfferKind.NEXT_TERM : OfferKind.NEW_HUB;
        long cents = Math.max(0, Math.round(data.dailyFee * 100.0));
        long createdAt = data.createdAt == null ? 0L : data.createdAt;
        return new HubOffer(
                data.faction, data.installation, data.rate, cents, awaiting, data.lastActor, createdAt, kind);
    }

    public static List<HubOfferData> toOfferData(List<HubOffer> offers) {
        List<HubOfferData> data = new ArrayList<>();
        if (offers == null) {
            return data;
        }
        for (HubOffer offer : offers) {
            if (offer == null || blank(offer.hostFactionId()) || blank(offer.installationId())) {
                continue;
            }
            HubOfferData entry = new HubOfferData();
            entry.faction = offer.hostFactionId();
            entry.installation = offer.installationId();
            entry.rate = offer.taxRatePercent();
            entry.dailyFee = offer.feeCents() / 100.0;
            entry.awaiting = offer.awaiting() == OfferSide.GUILD ? "guild" : "host";
            entry.lastActor = offer.lastActor();
            entry.createdAt = offer.createdAt();
            entry.kind = offer.kind() == OfferKind.NEXT_TERM ? "next" : "new";
            data.add(entry);
        }
        return data;
    }

    private static boolean tickAgreements(Guild guild, HubAgreementFacts facts, List<HubNotice> notices) {
        if (guild.getHubAgreements() == null) {
            return false;
        }
        boolean removed = false;
        List<HubAgreement> kept = new ArrayList<>();
        for (HubAgreement agreement : new ArrayList<>(guild.getHubAgreements())) {
            if (agreement == null) {
                continue;
            }
            String hostId = agreement.hostFactionId();
            String installationId = agreement.installationId();
            String label = facts.installationLabel(hostId, installationId);
            if (!facts.hostExists(hostId) || !facts.installationExists(hostId, installationId)) {
                removed = removeHub(guild, hostId, installationId) || removed;
                notices.addAll(tell(List.of(facts.guildLeader(guild)),
                        "§cYour supply hub at §f" + label + " §cwas removed because the installation is gone"));
                continue;
            }
            if (facts.sameRealm(guild, hostId)) {
                continue;
            }
            int days = agreement.daysRemaining() - 1;
            if (days > 0) {
                HubAgreement updated = applyDormant(agreement.withDays(days), guild, facts, notices);
                kept.add(updated);
                if (days <= 3 && !updated.willRenew()) {
                    String message = "§eThe hub agreement at §f" + label + " §eends in §f" + days + "§e "
                            + (days == 1 ? "day" : "days") + " and will not renew";
                    notices.addAll(tell(List.of(facts.guildLeader(guild)), message));
                    notices.addAll(tell(facts.council(hostId), message));
                }
                continue;
            }
            int rate = agreement.nextTerms() == null
                    ? agreement.taxRatePercent() : agreement.nextTerms().taxRatePercent();
            long fee = agreement.nextTerms() == null
                    ? agreement.feeCents() : agreement.nextTerms().feeCents();
            boolean inBracket = facts.hostHasHubTax(hostId)
                    && rate >= facts.minRate(hostId) && rate <= facts.maxRate(hostId);
            if (agreement.willRenew() && inBracket) {
                HubAgreement renewed = new HubAgreement(
                        hostId, installationId, rate, fee, Math.max(1, facts.agreementDays()),
                        true, true, null, agreement.dormantNoticeSent());
                kept.add(applyDormant(renewed, guild, facts, notices));
                String message = "§aThe hub agreement at §f" + label + " §arenewed for §f"
                        + Math.max(1, facts.agreementDays()) + "§a days";
                notices.addAll(tell(List.of(facts.guildLeader(guild)), message));
                notices.addAll(tell(facts.council(hostId), message));
            } else {
                removed = removeHub(guild, hostId, installationId) || removed;
                String message = "§cThe hub agreement at §f" + label + " §cended and the hub was removed";
                notices.addAll(tell(List.of(facts.guildLeader(guild)), message));
                notices.addAll(tell(facts.council(hostId), message));
            }
        }
        replaceAllAgreements(guild, kept);
        return removed;
    }

    private static HubAgreement applyDormant(
            HubAgreement agreement, Guild guild, HubAgreementFacts facts, List<HubNotice> notices) {
        SupplyHub hub = SupplyHubService.findHub(guild.getSupplyHubs(), agreement.hostFactionId(), agreement.installationId());
        boolean dormant = hub != null && facts.hubDormant(guild, hub);
        if (!dormant) {
            return agreement.dormantNoticeSent() ? agreement.withDormantNotice(false) : agreement;
        }
        if (agreement.dormantNoticeSent()) {
            return agreement;
        }
        notices.addAll(tell(List.of(facts.guildLeader(guild)),
                "§eYour hub at §f" + facts.installationLabel(agreement.hostFactionId(), agreement.installationId())
                        + " §eis dormant. The daily fee is still charged. Remove the hub to stop it"));
        return agreement.withDormantNotice(true);
    }

    private static void tickOffers(Guild guild, long nowMillis, HubAgreementFacts facts) {
        if (guild.getHubOffers() == null) {
            return;
        }
        List<HubOffer> kept = new ArrayList<>();
        for (HubOffer offer : guild.getHubOffers()) {
            if (offer == null) {
                continue;
            }
            if (lapsed(offer, nowMillis, facts.offerDays())
                    || !facts.hostExists(offer.hostFactionId())
                    || !facts.installationExists(offer.hostFactionId(), offer.installationId())
                    || facts.sameRealm(guild, offer.hostFactionId())) {
                continue;
            }
            boolean hubThere = hasHub(guild, offer.hostFactionId(), offer.installationId());
            if (offer.kind() == OfferKind.NEXT_TERM
                    && (!hubThere || findAgreement(guild, offer.hostFactionId(), offer.installationId()) == null)) {
                continue;
            }
            if (offer.kind() == OfferKind.NEW_HUB && hubThere) {
                continue;
            }
            kept.add(offer);
        }
        replaceAllOffers(guild, kept);
    }

    private static String buildProblem(
            Guild guild, String hostFactionId, String installationId, HubAgreementFacts facts, boolean proposing) {
        if (!facts.installationExists(hostFactionId, installationId)) {
            return "§cThat installation does not exist";
        }
        if (!facts.allowsSupplyHubs(guild)) {
            return SupplyHubService.buildFailureMessage(BuildFailure.ECONOMY_DISALLOWS, null, facts.hubLimit(guild));
        }
        int hubs = guild.getSupplyHubs() == null ? 0 : guild.getSupplyHubs().size();
        BuildFailure failure = SupplyHubService.checkBuild(
                facts.hubSlots(hostFactionId, installationId),
                hasHub(guild, hostFactionId, installationId),
                hubs,
                facts.hubLimit(guild),
                facts.hubsAtInstallation(hostFactionId, installationId),
                facts.tradePower(guild, hostFactionId, installationId),
                true);
        if (failure == null) {
            return null;
        }
        if (proposing && failure == BuildFailure.NO_AGREEMENT) {
            return null;
        }
        return SupplyHubService.buildFailureMessage(failure, "installation", facts.hubLimit(guild));
    }

    private static String termsProblem(int ratePercent, double feeDenars, HubAgreementFacts facts, String hostFactionId) {
        int min = facts.minRate(hostFactionId);
        int max = facts.maxRate(hostFactionId);
        if (ratePercent < min || ratePercent > max) {
            return "§cThat rate is outside the host's range (§e" + min + "-" + max + "%§c)";
        }
        Long cents = wholeCents(feeDenars);
        if (cents == null) {
            return "§cThe daily fee must be at least zero, in whole cents, and at most §e"
                    + Formatter.formatMoney(facts.maxFee()) + "§c";
        }
        long maxCents = Math.round(Math.max(0, facts.maxFee()) * 100.0);
        if (cents > maxCents) {
            return "§cThe daily fee must be at least zero, in whole cents, and at most §e"
                    + Formatter.formatMoney(facts.maxFee()) + "§c";
        }
        return null;
    }

    private static AgreementResult missingOrLapsed(
            Guild guild, String hostFactionId, String installationId, HubAgreementFacts facts, long nowMillis) {
        HubOffer offer = findOffer(guild, hostFactionId, installationId);
        if (offer != null && lapsed(offer, nowMillis, facts.offerDays())) {
            dropOffer(guild, hostFactionId, installationId);
            return AgreementResult.fail("§cThat offer has lapsed");
        }
        return AgreementResult.fail("§cThere is no offer for that installation");
    }

    private static HubOffer readyOffer(
            Guild guild, String hostFactionId, String installationId, HubAgreementFacts facts, long nowMillis) {
        HubOffer offer = findOffer(guild, hostFactionId, installationId);
        if (offer == null) {
            return null;
        }
        if (lapsed(offer, nowMillis, facts.offerDays())) {
            return null;
        }
        return offer;
    }

    static boolean lapsed(HubOffer offer, long nowMillis, int offerDays) {
        if (offer == null) {
            return true;
        }
        long limit = Math.max(1, offerDays) * DAY_MILLIS;
        return nowMillis - offer.createdAt() >= limit;
    }

    private static Long wholeCents(double fee) {
        if (Double.isNaN(fee) || Double.isInfinite(fee) || fee < 0) {
            return null;
        }
        double scaled = fee * 100.0;
        long cents = Math.round(scaled);
        if (Math.abs(scaled - cents) > 0.0001d) {
            return null;
        }
        return cents;
    }

    private static boolean actsFor(
            OfferSide side, Guild guild, String actorName, String hostFactionId, HubAgreementFacts facts) {
        if (side == OfferSide.GUILD) {
            return facts.isGuildLeader(guild, actorName);
        }
        return facts.isHostCouncil(hostFactionId, actorName);
    }

    private static String wrongSide(OfferSide awaiting) {
        if (awaiting == OfferSide.GUILD) {
            return "§cOnly the guild leader can answer this offer";
        }
        return "§cOnly the host's council can answer this offer";
    }

    private static List<HubNotice> tellOtherSide(
            Guild guild, String hostFactionId, OfferSide side, HubAgreementFacts facts, String message) {
        if (side == OfferSide.GUILD) {
            return tell(List.of(facts.guildLeader(guild)), message);
        }
        return tell(facts.council(hostFactionId), message);
    }

    private static List<HubNotice> tell(List<String> players, String message) {
        List<HubNotice> notices = new ArrayList<>();
        if (players == null || message == null) {
            return notices;
        }
        for (String player : players) {
            if (player == null || player.isBlank()) {
                continue;
            }
            notices.add(new HubNotice(player, message));
        }
        return notices;
    }

    private static List<HubNotice> dedupe(List<HubNotice> notices) {
        List<HubNotice> unique = new ArrayList<>();
        for (HubNotice notice : notices) {
            if (notice == null || notice.playerName() == null) {
                continue;
            }
            boolean seen = false;
            for (HubNotice kept : unique) {
                if (kept.playerName().equalsIgnoreCase(notice.playerName())
                        && kept.message().equals(notice.message())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique.add(notice);
            }
        }
        return unique;
    }

    private static boolean hasHub(Guild guild, String hostFactionId, String installationId) {
        return guild != null && SupplyHubService.hasHub(guild.getSupplyHubs(), hostFactionId, installationId);
    }

    private static boolean removeHub(Guild guild, String hostFactionId, String installationId) {
        if (guild == null || guild.getSupplyHubs() == null) {
            return false;
        }
        SupplyHub hub = SupplyHubService.findHub(guild.getSupplyHubs(), hostFactionId, installationId);
        if (hub == null) {
            return false;
        }
        guild.getSupplyHubs().remove(hub);
        return true;
    }

    private static HubOffer findOffer(Guild guild, String hostFactionId, String installationId) {
        if (guild == null || guild.getHubOffers() == null) {
            return null;
        }
        for (HubOffer offer : guild.getHubOffers()) {
            if (offer != null && samePlace(offer.hostFactionId(), offer.installationId(), hostFactionId, installationId)) {
                return offer;
            }
        }
        return null;
    }

    private static boolean addOffer(Guild guild, HubOffer offer) {
        if (guild.getHubOffers() == null) {
            return false;
        }
        guild.getHubOffers().add(offer);
        return true;
    }

    private static boolean addAgreement(Guild guild, HubAgreement agreement) {
        if (guild.getHubAgreements() == null) {
            return false;
        }
        guild.getHubAgreements().add(agreement);
        return true;
    }

    private static void replaceOffer(Guild guild, HubOffer previous, HubOffer updated) {
        List<HubOffer> offers = guild.getHubOffers();
        if (offers == null) {
            return;
        }
        int index = offers.indexOf(previous);
        if (index >= 0) {
            offers.set(index, updated);
        }
    }

    private static void replaceAgreement(Guild guild, HubAgreement previous, HubAgreement updated) {
        List<HubAgreement> agreements = guild.getHubAgreements();
        if (agreements == null) {
            return;
        }
        int index = agreements.indexOf(previous);
        if (index >= 0) {
            agreements.set(index, updated);
        }
    }

    private static void replaceAllAgreements(Guild guild, List<HubAgreement> agreements) {
        List<HubAgreement> live = guild.getHubAgreements();
        if (live == null) {
            return;
        }
        live.clear();
        live.addAll(agreements);
    }

    private static void replaceAllOffers(Guild guild, List<HubOffer> offers) {
        List<HubOffer> live = guild.getHubOffers();
        if (live == null) {
            return;
        }
        live.clear();
        live.addAll(offers);
    }

    private static void dropAgreement(Guild guild, String hostFactionId, String installationId) {
        if (guild == null || guild.getHubAgreements() == null) {
            return;
        }
        guild.getHubAgreements().removeIf(agreement -> agreement != null
                && samePlace(agreement.hostFactionId(), agreement.installationId(), hostFactionId, installationId));
    }

    private static void dropOffer(Guild guild, String hostFactionId, String installationId) {
        if (guild == null || guild.getHubOffers() == null) {
            return;
        }
        guild.getHubOffers().removeIf(offer -> offer != null
                && samePlace(offer.hostFactionId(), offer.installationId(), hostFactionId, installationId));
    }

    private static boolean samePlace(String hostId, String installationId, String otherHost, String otherInstallation) {
        return hostId != null && installationId != null && otherHost != null && otherInstallation != null
                && hostId.equalsIgnoreCase(otherHost) && installationId.equalsIgnoreCase(otherInstallation);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String guildName(Guild guild) {
        if (guild == null || guild.getName() == null || guild.getName().isBlank()) {
            return guild == null || guild.getId() == null ? "A guild" : guild.getId();
        }
        return guild.getName();
    }

    private static String money(long cents) {
        return Formatter.formatMoney(cents / 100.0);
    }
}
