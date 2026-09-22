package me.Plugins.SimpleFactions.settlement.handler;

import me.Plugins.SimpleFactions.settlement.Settlement;

public class CapitalResult {
    private final boolean success;
    private final String message;
    private final Settlement settlement;

    public CapitalResult(boolean success, String message) {
        this(success, message, null);
    }

    public CapitalResult(boolean success, String message, Settlement settlement) {
        this.success = success;
        this.message = message;
        this.settlement = settlement;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public static CapitalResult fail(String message) {
        return new CapitalResult(false, message);
    }

    public static CapitalResult ok(String message) {
        return new CapitalResult(true, message);
    }

    public static CapitalResult ok(String message, Settlement settlement) {
        return new CapitalResult(true, message, settlement);
    }
}
