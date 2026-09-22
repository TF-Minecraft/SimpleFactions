package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.Guild.Guild;

public class MovementLeaderTargetRequest extends Request {
	private final String requester;
	private final String movementId;
	private final int causeIndex;
	private final String proposedName;

	public MovementLeaderTargetRequest(
			Guild sender,
			String requester,
			String movementId,
			int causeIndex,
			String proposedName) {
		super(sender);
		this.requester = requester;
		this.movementId = movementId;
		this.causeIndex = causeIndex;
		this.proposedName = proposedName;
	}

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
