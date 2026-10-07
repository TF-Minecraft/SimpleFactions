package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;

public class MovementJoinRequest extends Request{
    private String player;
    private String type;
    private String faction;
    private int causeIndex;
    private final Movement movement;
    private final Cause cause;

    /** Legacy request data; an ordinal alone cannot authorize joining a live movement. */
	public MovementJoinRequest(Guild sender, String player, String type, String faction, int causeIndex) {
        this(sender, player, type, faction, causeIndex, null, null);
    }

    public MovementJoinRequest(Guild sender, String player, String type, Movement movement, Cause cause) {
        this(sender, player, type, movement.getFaction().getId(), cause == null ? -1 : cause.getIndex(), movement, cause);
    }

    private MovementJoinRequest(Guild sender, String player, String type, String faction, int causeIndex, Movement movement, Cause cause) {
		super(sender);
        this.movement = movement;
        this.cause = cause;
        this.player = player;
        this.type = type;
        this.faction = faction;
        this.causeIndex = causeIndex;
	}

    public Movement getMovement() { return movement; }

    public Cause getCause() { return cause; }

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
