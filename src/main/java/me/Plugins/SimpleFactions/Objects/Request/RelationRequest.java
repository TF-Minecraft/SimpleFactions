package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Guild.Guild;

public class RelationRequest extends Request{
	private RelationType type;
	private boolean trade;
	private boolean treaty;
	
	public RelationRequest(Guild sender, RelationType type, boolean trade) {
		this(sender, type, trade, false);
	}

	public RelationRequest(Guild sender, RelationType type, boolean trade, boolean treaty) {
		super(sender);
		this.type = type;
		this.trade = trade;
		this.treaty = treaty;
	}

	public RelationType getType() {
		return type;
	}

	public void setType(RelationType type) {
		this.type = type;
	}

	public boolean isTrade() {
		return trade;
	}

	public boolean isTreaty() {
		return treaty;
	}
}
