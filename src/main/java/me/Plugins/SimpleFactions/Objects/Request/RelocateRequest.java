package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.Guild.Guild;

public class RelocateRequest extends Request{
    private final int newCapital;
    private final String settlementName;

    public RelocateRequest(Guild sender, int newCapital, String settlementName) {
        super(sender);
        this.newCapital = newCapital;
        this.settlementName = settlementName;
    }

    public int getNewCapital() {
        return newCapital;
    }

    public String getSettlementName() {
        return settlementName;
    }
}
