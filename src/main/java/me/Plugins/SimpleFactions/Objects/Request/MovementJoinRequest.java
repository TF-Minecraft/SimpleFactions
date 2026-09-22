package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.Guild.Guild;

public class MovementJoinRequest extends Request{
    private String player;
    private String type;
    private String faction;
    private int causeIndex;
	public MovementJoinRequest(Guild sender, String player, String type, String faction, int causeIndex) {
		super(sender);
        this.player = player;
        this.type = type;
        this.faction = faction;
        this.causeIndex = causeIndex;
	}

    public String getPlayer() {
        return player;
    }

    public String getType() {
        return type;
    }

    public String getTargetFactionId() {
        return faction;
    }

    public int getCauseIndex() {
        return causeIndex;
    }
}
