package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

public class ElevateRequest extends Request {
    private final Faction sponsoringFaction;
    public ElevateRequest(Guild sender) {
        super(sender);
        sponsoringFaction = sender.getFaction();
    }

    public Faction getSponsoringFaction() {
        return sponsoringFaction;
    }
    
}
