package me.Plugins.SimpleFactions.mercenary;

/**
 * Outcome of a mercenary company action, carrying the message the command and
 * the GUI both show so refusals read the same in either place.
 */
public record MercenaryResult(boolean ok, String message) {
    public static MercenaryResult ok(String message) {
        return new MercenaryResult(true, message);
    }

    public static MercenaryResult deny(String reason) {
        return new MercenaryResult(false, reason);
    }
}
