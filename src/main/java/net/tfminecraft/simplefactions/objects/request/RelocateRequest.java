package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.managers.FactionManager;

public class RelocateRequest extends Request{
    private final int newCapital;
    private final String settlementName;
    private final Faction originalHost;
    private final String originalLeader;
    private final Faction destination;
    private final net.tfminecraft.tlibs.character.CharacterSession sendingSession;
    private final net.tfminecraft.tlibs.character.CharacterSession receivingSession;
    private final String operationId = net.tfminecraft.simplefactions.economy.TreasuryActions.newId();

    public RelocateRequest(Guild sender, int newCapital, String settlementName) {
        this(sender, newCapital, settlementName, null);
    }

    public RelocateRequest(Guild sender, int newCapital, String settlementName, Faction destination) {
        this(sender, newCapital, settlementName, destination, null, null);
    }

    public RelocateRequest(Guild sender, int newCapital, String settlementName, Faction destination,
            net.tfminecraft.tlibs.character.CharacterSession sendingSession,
            net.tfminecraft.tlibs.character.CharacterSession receivingSession) {
        super(sender);
        this.sendingSession = sendingSession;
        this.receivingSession = receivingSession;
        this.originalHost = sender.getFaction();
        this.originalLeader = sender.getLeader();
        this.destination = destination;
        this.newCapital = newCapital;
        this.settlementName = settlementName;
    }

    public boolean isCurrentFor(Faction receiver) {
        return (sendingSession == null || net.tfminecraft.simplefactions.identity.Members.isCurrent(sendingSession))
                && (receivingSession == null || net.tfminecraft.simplefactions.identity.Members.isCurrent(receivingSession))
                && originalHost != null
                && FactionManager.getByString(originalHost.getId()) == originalHost
                && FactionManager.getGuildByString(sender.getId()) == sender
                && sender.getFaction() == originalHost
                && java.util.Objects.equals(originalLeader, sender.getLeader())
                && !sender.isBase()
                && receiver != originalHost
                && (destination == null || destination == receiver);
    }

    public int getNewCapital() {
        return newCapital;
    }

    public String getOperationId() { return operationId; }

    public String getSettlementName() {
        return settlementName;
    }
}
