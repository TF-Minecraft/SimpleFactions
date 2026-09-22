package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * The two loyalty rules, as services rather than GUI checks.
 *
 * <p>A company will not fight its own realm, and a ruler will not fight their own
 * realm even as a hireling. Both are checked here so the command layer, the GUI
 * and the Phase 4 battle roster all answer the same way.
 */
public final class MercenaryLoyalty {
    private MercenaryLoyalty() {
    }

    /* =====================================================
     * Company level
     * ===================================================== */

    /**
     * Whether the company may serve this faction. There is no war on a contract,
     * so the question is asked against every war the hirer is currently in: if any
     * of them puts the company's host realm on the other side, the company is out.
     */
    public static MercenaryResult canServe(MercenaryCompany company, Faction hirer) {
        return canServe(company, hirer, WarManager.getActive());
    }

    public static MercenaryResult canServe(
            MercenaryCompany company, Faction hirer, Collection<War> wars) {
        if (company == null) {
            return MercenaryResult.deny("That company no longer exists.");
        }
        if (hirer == null) {
            return MercenaryResult.deny("That faction no longer exists.");
        }
        Faction host = hostFaction(company);
        if (host == null) {
            return MercenaryResult.ok("No host realm to betray.");
        }
        if (wars == null) {
            return MercenaryResult.ok("No conflict of loyalty.");
        }
        for (War war : wars) {
            if (war == null || !war.isParticipating(hirer)) continue;
            Side opposing = war.getOppositeSide(hirer);
            if (opposing == null) continue;
            MercenaryResult conflict = conflictWith(host, opposing);
            if (!conflict.ok()) return conflict;
        }
        return MercenaryResult.ok("No conflict of loyalty.");
    }

    /**
     * Whether the company may take this hirer without already serving the other
     * side of one of their wars. Kept separate from {@link #canServe} because that
     * check is symmetric and would fail both contracts when the watcher re-runs it.
     */
    public static MercenaryResult canServeAlongside(MercenaryCompany company, Faction hirer) {
        return canServeAlongside(company, hirer, WarManager.getActive());
    }

    public static MercenaryResult canServeAlongside(
            MercenaryCompany company, Faction hirer, Collection<War> wars) {
        if (company == null) {
            return MercenaryResult.deny("That company no longer exists.");
        }
        if (hirer == null) {
            return MercenaryResult.deny("That faction no longer exists.");
        }
        if (wars == null) {
            return MercenaryResult.ok("No opposing contract.");
        }
        for (War war : wars) {
            if (war == null || !war.isParticipating(hirer)) continue;
            Side opposing = war.getOppositeSide(hirer);
            if (opposing == null) continue;
            for (MercenaryContract contract : company.getContractHandler().getActive()) {
                Faction other = contract.getHirer();
                if (other == null || sameFaction(other, hirer)) continue;
                if (opposing.isParticipating(other)) {
                    return MercenaryResult.deny(
                            "Your company is already serving the other side of this war.");
                }
            }
        }
        return MercenaryResult.ok("No opposing contract.");
    }

    /**
     * {@code Side.isParticipating} already walks leader, subjects and joined
     * secondaries, so the direct and nested-vassal cases are one call. Alliances
     * are checked separately because an ally who has not answered the call to arms
     * is not yet a participant, but hiring against them is still treachery.
     */
    private static MercenaryResult conflictWith(Faction host, Side opposing) {
        if (opposing.isParticipating(host)) {
            return MercenaryResult.deny(
                    "Your company will not take arms against " + name(host) + ".");
        }
        for (Faction enemy : factionsOn(opposing)) {
            if (enemy == null || sameFaction(enemy, host)) continue;
            if (isBoundTo(host, enemy)) {
                return MercenaryResult.deny("Your company will not take arms against "
                        + name(enemy) + ", who " + name(host) + " is bound to.");
            }
        }
        return MercenaryResult.ok("No conflict of loyalty.");
    }

    /** Allies, overlords and subjects all make a target off limits. */
    private static boolean isBoundTo(Faction host, Faction other) {
        for (Faction ally : RelationManager.getAllies(host)) {
            if (sameFaction(ally, other)) return true;
        }
        String overlord = RelationManager.getOverlord(host);
        if (overlord != null && other.getId() != null && overlord.equalsIgnoreCase(other.getId())) {
            return true;
        }
        for (Faction subject : RelationManager.getSubjects(host)) {
            if (sameFaction(subject, other)) return true;
        }
        return false;
    }

    private static List<Faction> factionsOn(Side side) {
        List<Faction> list = new ArrayList<>();
        if (side.getLeader() != null) list.add(side.getLeader());
        for (Participant p : side.getMainParticipants()) {
            if (p == null) continue;
            list.addAll(p.getAllParticipatingFactions());
            list.addAll(p.getAllies().keySet());
        }
        return list;
    }

    /* =====================================================
     * Player level
     * ===================================================== */

    /**
     * Whether an enlisted player may be deployed against a given faction. A
     * turncoat citizen is allowed; a turncoat ruler is not. Phase 4 calls this when
     * building a battle roster, deliberately not at enlistment: a ruler may belong
     * to a company, they simply cannot march on their own realm.
     */
    public static boolean canDeploy(String player, Faction enemy) {
        if (player == null || enemy == null) return true;
        Faction own = factionOf(player);
        if (own == null || !sameFaction(own, enemy)) return true;
        return enemy.getGovernment() == null || !enemy.getGovernment().isCouncilMember(player);
    }

    /**
     * Whether the player may be rostered against this whole side. A turncoat
     * citizen may; a faction leader or council member may not.
     */
    public static boolean canDeployAgainst(String player, Side opposing) {
        if (player == null || opposing == null) return true;
        for (Faction enemy : factionsOn(opposing)) {
            if (!canDeploy(player, enemy)) return false;
        }
        return true;
    }

    /** Enlisted players of a company who may not be sent against this faction. */
    public static List<String> blockedAgainst(MercenaryCompany company, Faction enemy) {
        List<String> blocked = new ArrayList<>();
        if (company == null) return blocked;
        for (String member : company.getEnlisted()) {
            if (!canDeploy(member, enemy)) blocked.add(member);
        }
        return blocked;
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    public static Faction hostFaction(MercenaryCompany company) {
        Guild guild = company == null ? null : company.getGuild();
        return guild == null ? null : guild.getFaction();
    }

    private static Faction factionOf(String player) {
        Guild guild = FactionManager.getGuildByMember(player);
        return guild == null ? null : guild.getFaction();
    }

    private static boolean sameFaction(Faction a, Faction b) {
        return a != null && b != null && a.getId() != null
                && a.getId().equalsIgnoreCase(b.getId());
    }

    private static String name(Faction f) {
        if (f == null) return "that realm";
        return f.getName() != null ? f.getName() : f.getId();
    }
}
