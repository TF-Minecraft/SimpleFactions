package net.tfminecraft.simplefactions.objects.request;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;

public class MovementLeaderTargetRequest extends Request {
	private final String requester;
	private final String movementId;
	private final int causeIndex;
	private final String proposedName;
    private final Movement movement;
    private final Cause cause;

    /** Legacy request data; an ordinal alone cannot authorize editing a live cause. */
	public MovementLeaderTargetRequest(
			Guild sender,
			String requester,
			String movementId,
			int causeIndex,
			String proposedName) {
        this(sender, requester, movementId, causeIndex, proposedName, null, null);
    }

    public MovementLeaderTargetRequest(Guild sender, String requester, Movement movement, Cause cause, String proposedName) {
        this(sender, requester, movement.getId(), cause.getIndex(), proposedName, movement, cause);
    }

    private MovementLeaderTargetRequest(Guild sender, String requester, String movementId, int causeIndex, String proposedName, Movement movement, Cause cause) {
		super(sender);
        this.movement = movement;
        this.cause = cause;
		this.requester = requester;
		this.movementId = movementId;
		this.causeIndex = causeIndex;
		this.proposedName = proposedName;
	}

    public Movement getMovement() { return movement; }

    public Cause getCause() { return cause; }

	public String getRequester() {
		return requester;
	}

	public String getMovementId() {
		return movementId;
	}

	public int getCauseIndex() {
		return causeIndex;
	}

	public String getProposedName() {
		return proposedName;
	}
}
